package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 统一热键栏渲染器（**骨架**，阶段 4.1/4.2 只落接口与状态判定；接管渲染属 4.4）。
 * <p>
 * 契约（指南 §3.5 / §3.5.1）：
 * <ul>
 *   <li><b>状态判定顺序固定</b>：冷却 → 禁用 → 能量不足 → 就绪（{@link #stateOf}）；</li>
 *   <li><b>空闲 tick 零 setItem（4.4 生效）</b>：只有 {@link #markDirty()} 被调用后，帧末 flush 才会写物品；</li>
 *   <li><b>刷新触发</b>：施放 / 冷却启动 / 冷却到点 / 能量变化 / buff 变化五类事件都必须置脏
 *       （由各自的端口适配器在内部调用 {@code markDirty()}）；</li>
 *   <li>组件**不参与**渲染：没有 HotbarPort，也没有组件可调用的 markDirty。</li>
 * </ul>
 */
public final class HotbarRenderer {

    private boolean dirty = true;

    /**
     * 置脏。由框架内部（施放管道；端口的自动置脏属 4.4）调用；组件侧没有这条通道。
     * <p><b>⚠️ 当前为骨架：{@link #markDirty()} 仅翻标志，尚无帧末 flush；可见刷新仍由
     * {@code RoleInstance.updateHotbar()} 承担；端口的 markDirty 接线与 flush 属阶段 4.4</b>
     * —— 不要在 4.4 落地前据本类判断"置脏已覆盖刷新"而删掉 legacy 刷新路径（会断可见刷新）。
     */
    public void markDirty() {
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** 帧末 flush 完成后清脏（**4.4 生效**）。真正的写物品逻辑在 4.4 接管时接上（本骨架不写任何物品，且当前**无消费者**调用本方法）。 */
    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * 状态判定（顺序与今天的渲染代码逐字一致）。
     * 主武器的 {@code energyCost} 恒为 {@code 0} ⇒ 永不进入 {@link IconState#ENERGY_LACK}
     * （该分支今天只存在于两个技能覆写里，见设计 §4.3 陷阱①）。
     */
    public static IconState stateOf(boolean ready, boolean canCast, int energy, int energyCost) {
        if (!ready) {
            return IconState.COOLDOWN;
        }
        if (!canCast) {
            return IconState.DISABLED;
        }
        if (energy < energyCost) {
            return IconState.ENERGY_LACK;
        }
        return IconState.READY;
    }
}
