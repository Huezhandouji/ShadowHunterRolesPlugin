package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 「抗争」的**控制状态判定小工具**（纯静态、无状态、无副作用）。
 *
 * <p>用来回答两个问题：
 * <ol>
 *   <li><b>我现在被控了吗？</b> ⇒ {@link #maskOf(BuffComponent, Player)} 采一个位掩码；</li>
 *   <li><b>这次解控算不算"成功解控"？</b> ⇒ {@link #isSuccessfullyCleared(int, int)}：
 *       解控前有控制位、解控后一位不剩，才算成功（本来就没被控 ⇒ 不算，不会误喊台词）。</li>
 * </ol>
 *
 * <p>★ 为什么要位掩码而不是 boolean：一技能引导期间会给<b>自己</b>施加缓慢 IV，
 * 如果只用一个布尔"当前是否被控"，引导刚结束的采样必然为 true（自己施加的缓慢），
 * 分不清"解掉了敌人的控制"还是"解掉了自己的引导减速"。用掩码 + 在引导开始前采样即可区分。
 *
 * <p>★ 纯函数（{@link #hasAnyControl}、{@link #isSuccessfullyCleared}、{@link #remaining}）
 * 不碰 Bukkit，可离线单测；只有 {@link #maskOf} 需要在线对象。
 */
public final class ResistControl {

    /** 插件侧：眩晕（技能闸门 + 主武器闸门都关）。 */
    public static final int BIT_STUN = 1;

    /** 插件侧：沉默（技能闸门关闭）。 */
    public static final int BIT_SILENCE = 1 << 1;

    /** 原版：缓慢（任意等级都算）。 */
    public static final int BIT_SLOWNESS = 1 << 2;

    /** 原版：负等级跳跃（被用来做"禁跳/定身"的既有手法）。 */
    public static final int BIT_JUMP_LOCK = 1 << 3;

    /** 原版：失明。 */
    public static final int BIT_BLINDNESS = 1 << 4;

    /** 原版：黑暗。 */
    public static final int BIT_DARKNESS = 1 << 5;

    /** 原版：虚弱。 */
    public static final int BIT_WEAKNESS = 1 << 6;

    /** 原版：挖掘疲劳。 */
    public static final int BIT_FATIGUE = 1 << 7;

    /** 原版：反胃。 */
    public static final int BIT_NAUSEA = 1 << 8;

    private ResistControl() {
    }

    /**
     * 采集当前控制位（{@code 0} = 没有任何控制）。
     *
     * @param buff   插件侧 buff 记账（可传 null ⇒ 只看原版药水）
     * @param player 目标玩家（null ⇒ 返回 0）
     */
    public static int maskOf(BuffComponent buff, Player player) {
        if (player == null) {
            return 0;
        }
        int mask = 0;
        if (buff != null) {
            if (buff.has(BuffType.STUN)) {
                mask |= BIT_STUN;
            }
            if (buff.has(BuffType.SILENCE)) {
                mask |= BIT_SILENCE;
            }
        }
        if (player.getPotionEffect(PotionEffectType.SLOWNESS) != null) {
            mask |= BIT_SLOWNESS;
        }
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jump != null && jump.getAmplifier() < 0) {
            mask |= BIT_JUMP_LOCK;      //只有"负等级"才是禁跳，正等级是正常增益
        }
        if (player.getPotionEffect(PotionEffectType.BLINDNESS) != null) {
            mask |= BIT_BLINDNESS;
        }
        if (player.getPotionEffect(PotionEffectType.DARKNESS) != null) {
            mask |= BIT_DARKNESS;
        }
        if (player.getPotionEffect(PotionEffectType.WEAKNESS) != null) {
            mask |= BIT_WEAKNESS;
        }
        if (player.getPotionEffect(PotionEffectType.MINING_FATIGUE) != null) {
            mask |= BIT_FATIGUE;
        }
        if (player.getPotionEffect(PotionEffectType.NAUSEA) != null) {
            mask |= BIT_NAUSEA;
        }
        return mask;
    }

    /** 纯函数：是否有任意控制。 */
    public static boolean hasAnyControl(int mask) {
        return mask != 0;
    }

    /**
     * 纯函数：是否**成功解控** —— 解控前有控制位、解控后一位不剩。
     *
     * <p>★ 本来就没被控（{@code before == 0}）⇒ 返回 false：这不是"解控成功"，只是"没控可解"，
     * 不该喊挣脱台词。
     */
    public static boolean isSuccessfullyCleared(int before, int after) {
        return before != 0 && after == 0;
    }

    /** 纯函数：解控后仍然残留的控制位（诊断用；{@code 0} = 干净）。 */
    public static int remaining(int before, int after) {
        return before & after;
    }

    /** 纯函数：把控制位翻译成可读名字（日志 / 探针用，形如 {@code STUN+SLOWNESS}）。 */
    public static String describe(int mask) {
        if (mask == 0) {
            return "NONE";
        }
        StringBuilder sb = new StringBuilder();
        append(sb, mask, BIT_STUN, "STUN");
        append(sb, mask, BIT_SILENCE, "SILENCE");
        append(sb, mask, BIT_SLOWNESS, "SLOWNESS");
        append(sb, mask, BIT_JUMP_LOCK, "JUMP_LOCK");
        append(sb, mask, BIT_BLINDNESS, "BLINDNESS");
        append(sb, mask, BIT_DARKNESS, "DARKNESS");
        append(sb, mask, BIT_WEAKNESS, "WEAKNESS");
        append(sb, mask, BIT_FATIGUE, "FATIGUE");
        append(sb, mask, BIT_NAUSEA, "NAUSEA");
        return sb.toString();
    }

    private static void append(StringBuilder sb, int mask, int bit, String name) {
        if ((mask & bit) == 0) {
            return;
        }
        if (sb.length() > 0) {
            sb.append('+');
        }
        sb.append(name);
    }
}
