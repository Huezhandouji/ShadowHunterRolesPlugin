package com.shadowHunterRolesPlugin.manager;

import com.shadowHunterRolesPlugin.platform.RolesContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * 故障隔离的**提醒**器（阶段 10 · t66 · 用户新设计第 ④ 步：给所有人发消息提醒）。
 *
 * <p><b>两档信息（用户裁定 ②）</b>：
 * <ul>
 *   <li><b>全服简报</b>：所有在线玩家只看到"某角色已停用"类信息（**不含**组件名 / 堆栈）✓；</li>
 *   <li><b>OP 详情</b>：OP 额外收到含**组件名 / 阶段 / 异常类型与消息 / 栈摘要**的详情 ✓。</li>
 * </ul>
 *
 * <p><b>★ 限流/去重（用户裁定 ② 的硬要求，不是可选项）</b>：同一 {@code (角色, 组件)} 在
 * {@link #DEDUP_WINDOW_MS} 毫秒内**只播一次** ⇒ 一个反复抛异常的组件不会刷屏 ✗。
 * 被限流的那一次**仍然记一条 INFO**（{@code [quarantine] announcement suppressed …}）⇒
 * "被限流"本身是可观测的，不是静默丢弃 ✓。
 *
 * <p><b>可测性</b>：限流判据 {@link #shouldAnnounce(String, long)} 是**纯函数**（时间由形参注入）
 * ⇒ 可脱离服务器单测（仓外探针即如此取证）。
 */
public final class QuarantineNotifier {

    /** 同一 {@code (roleId, componentId)} 的播报窗口（毫秒）。 */
    public static final long DEDUP_WINDOW_MS = 30_000L;

    private final RolesContext context;

    /** 限流表：{@code roleId:componentId → 上次播报时刻(ms)}。只被主线程访问。 */
    private final Map<String, Long> lastAnnouncement = new HashMap<>();

    private long announcedCount = 0;
    private long suppressedCount = 0;

    public QuarantineNotifier(RolesContext context) {
        this.context = context;
    }

    /**
     * **限流判据（纯函数）**：本 key 现在该不该播？
     * <p>窗口内已有一次播报 ⇒ {@code false}（并计入 {@link #suppressedCount()}）；否则记下本次时刻并返回
     * {@code true}（计入 {@link #announcedCount()}）。
     *
     * @param key 去重键（= {@code roleId + ":" + componentId}）
     * @param now 当前时刻（毫秒；**由调用方注入** ⇒ 可单测）
     */
    public boolean shouldAnnounce(String key, long now) {
        Long last = lastAnnouncement.get(key);
        if (last != null && now - last < DEDUP_WINDOW_MS) {
            suppressedCount++;
            return false;
        }
        lastAnnouncement.put(key, now);
        announcedCount++;
        return true;
    }

    /** 已播报次数（诊断/取证读数）。 */
    public long announcedCount() {
        return announcedCount;
    }

    /** 被限流次数（诊断/取证读数）。 */
    public long suppressedCount() {
        return suppressedCount;
    }

    /** 限流表大小（诊断/取证读数）。 */
    public int trackedKeys() {
        return lastAnnouncement.size();
    }

    /**
     * 播报一次隔离：**先过限流**，通过则全服简报 + OP 详情。
     *
     * @return 本次是否真的播报了（{@code false} = 被限流）
     */
    public boolean announce(String roleId, String playerName, String componentId, String phase, Throwable failure) {
        String key = roleId + ":" + componentId;
        if (!shouldAnnounce(key, System.currentTimeMillis())) {
            context.logger().info("[quarantine] announcement suppressed by the rate limiter (key=" + key
                    + ", window=" + DEDUP_WINDOW_MS + "ms, announced=" + announcedCount
                    + ", suppressed=" + suppressedCount + ")");
            return false;
        }

        //① 全服简报：所有在线玩家 —— 只说明"某角色已停用"，**不含**组件名/堆栈
        Component brief = Component.text("[ShadowHunter] Role '" + roleId
                + "' has been disabled after a component failure.", NamedTextColor.RED);

        //② OP 详情：组件名 + 阶段 + 异常类型/消息 + 栈摘要
        Component detail = Component.text("[ShadowHunter/OP] role=" + roleId
                + " | player=" + playerName
                + " | component=" + componentId
                + " | phase=" + phase
                + " | exception=" + (failure == null ? "null" : failure.getClass().getName())
                + (failure == null || failure.getMessage() == null ? "" : ": " + failure.getMessage())
                + stackSummary(failure), NamedTextColor.GOLD);

        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(brief);
            if (online.isOp()) {
                online.sendMessage(detail);
            }
        }
        context.logger().info("[quarantine] announced role=" + roleId + " component=" + componentId
                + " to " + Bukkit.getOnlinePlayers().size() + " online player(s) (rate-limit key=" + key + ").");
        return true;
    }

    /** 栈摘要（前 3 帧；**不**把整条栈塞进聊天框，避免刷屏）。 */
    private static String stackSummary(Throwable failure) {
        if (failure == null) {
            return "";
        }
        StackTraceElement[] trace = failure.getStackTrace();
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < Math.min(3, trace.length); i++) {
            summary.append(" <- ").append(trace[i]);
        }
        return summary.toString();
    }
}
