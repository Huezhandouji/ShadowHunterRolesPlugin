package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * 「特克」的**彩虹跳字工具**（纯静态、无状态、零组件依赖）。
 *
 * <h2>做什么</h2>
 * 把一段文本做成"逐字彩虹 + 随时间流动"的 {@link Component}：
 * 每个字按它在字符串里的位置取一个色相（整段铺满一整圈彩虹），
 * 再叠加一个随调用次数推进的相位 ⇒ 颜色在字之间**流动**，看起来就是"跳字"。
 *
 * <h2>为什么要"逐字"而不是"整段一个颜色"</h2>
 * 需求要的是"**彩虹**跳字" —— 单个颜色轮换只是"闪色"，不是彩虹。
 * 逐字取色才能在一行字里同时看到红橙黄绿青蓝紫。
 *
 * <h2>怎么让它动起来</h2>
 * 本类只负责"按相位产出颜色"。**必须由调用方每刻重绘**（组件的
 * {@code update()} 里请求重绘 + {@code buildItem()} 里重新生成名字），
 * 相位才会推进 —— 见 {@code TekTruthThrustSkill}。
 *
 * <h2>边界</h2>
 * 不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 * 色相 → RGB 自带实现（**不引 AWT**：`java.awt.Color` 会把 AWT 拖进服务端插件）。
 */
public final class TekRainbow {

    private TekRainbow() {
    }

    /**
     * 相位一圈的长度（刻）。
     * <p>取 64 ⇒ 20 Hz 下约每 3.2 秒整个彩虹平移一遍（够快能看出"动"，又不至于晃眼）。
     */
    public static final int PHASE_CYCLE = 64;

    /**
     * **彩虹跳字**：把 {@code base} 的**纯文本**逐字染色，并叠加 {@code phase} 的流动相位。
     *
     * <p>★ 只用纯文本、不保留原样式：调用方传进来的名字可能是"绿色 + 加粗"或
     * "灰色 + 秒数后缀"，这里统一改成彩虹 —— 状态信息不会因此丢失，
     * 因为三态**材质**（结构空位 / 红屏障）与名字后面的后缀文字都还在。
     *
     * @param base  底稿（通常 = 基类刚画好的显示名；{@code null} ⇒ 返回空组件）
     * @param phase 相位（同一段文本、不同相位 ⇒ 颜色不同；每刻推进即"跳字"）
     */
    public static Component animated(Component base, int phase) {
        if (base == null) {
            return Component.empty();
        }
        String text = PlainTextComponentSerializer.plainText().serialize(base);
        if (text.isEmpty()) {
            return Component.empty();
        }
        Component out = Component.empty();
        int total = text.length();
        for (int i = 0; i < total; i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                //空格不着色，保持字间距干净（也不影响整段的色相推进）
                out = out.append(Component.text(" "));
                continue;
            }
            out = out.append(Component.text(String.valueOf(c))
                    .color(TextColor.color(rgbOf(hueFor(i, total, phase)))));
        }
        return out.decorate(TextDecoration.BOLD);
    }

    /**
     * **纯函数：第 {@code index} 个字（共 {@code total} 字）在给定相位下的色相**（0~1）。
     *
     * <p>规则：整段字铺满**一整圈**彩虹（{@code index / total}），
     * 再叠加 {@code phase} 的平移量（{@code phase / PHASE_CYCLE}）⇒ 相位推进时颜色在字间流动。
     *
     * @param index 字符下标（从 0 起；越界会自动环绕）
     * @param total 字符总数（{@code ≤ 0} 时按 1 处理）
     * @param phase 相位（可负；内部取模）
     * @return 色相 ∈ {@code [0, 1)}
     */
    static float hueFor(int index, int total, int phase) {
        int span = Math.max(1, total);
        int idx = Math.floorMod(index, span);
        float spanPart = idx / (float) span;
        float phasePart = Math.floorMod(phase, PHASE_CYCLE) / (float) PHASE_CYCLE;
        float hue = spanPart + phasePart;
        return hue - (float) Math.floor(hue);
    }

    /**
     * **纯函数：色相 → 24 位 RGB**（HSV 取 S = V = 1，即最鲜艳的彩虹）。
     *
     * @param hue 色相（任意实数；内部取小数部分）
     * @return {@code 0xRRGGBB}
     */
    static int rgbOf(float hue) {
        float h = hue - (float) Math.floor(hue);
        float sector = h * 6f;
        int i = (int) sector;
        float f = sector - i;
        float q = 1f - f;

        float r;
        float g;
        float b;
        switch (i % 6) {
            case 0 -> { r = 1f; g = f; b = 0f; }
            case 1 -> { r = q; g = 1f; b = 0f; }
            case 2 -> { r = 0f; g = 1f; b = f; }
            case 3 -> { r = 0f; g = q; b = 1f; }
            case 4 -> { r = f; g = 0f; b = 1f; }
            default -> { r = 1f; g = 0f; b = q; }
        }
        return (channel(r) << 16) | (channel(g) << 8) | channel(b);
    }

    /** 0~1 的单通道值 → 0~255（四舍五入并夹住）。 */
    private static int channel(float value) {
        int v = Math.round(value * 255f);
        return Math.max(0, Math.min(255, v));
    }
}
