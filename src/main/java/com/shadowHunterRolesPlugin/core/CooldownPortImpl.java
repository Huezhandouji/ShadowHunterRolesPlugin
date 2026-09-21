package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.hotbar.CooldownAware;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;

/**
 * {@link CooldownPort} 的独立适配器：**构造期只绑定本组件 id**（组件不再传 id，也不再传 kind）。
 * <p>
 * <b>阶段 8 前置（本批）</b>：技能与主武器的两张冷却表已合并为**单一冷却命名空间**
 * （`RoleInstance` 内唯一那张表）⇒ 本适配器**不再需要在构造期绑定 kind（那个枚举已删）** ——
 * 那正是"删 kind 枚举"的硬阻塞（只要还是两张表，建服务集时就必须知道 kind）。
 * 于是"被动没有冷却"这条**无声语义**从端口挪到了表那一层（{@code RoleInstance.startCooldown} 返回
 * 是否真的写了表）⇒ 可见行为逐字不变：不写表、不派发、**不置脏**。
 * <p>
 * 阶段 4 追补（冷却自管理）：{@code start} 支持"重复开启按新的来"（先清未到期的旧条目并回调
 * {@code RESTARTED}，再起新冷却）；{@code end} 从保留位改为**真实语义**（在冷却中 ⇒ 清条目 + 回调
 * {@code ENDED_BY_COMPONENT} + 返回 {@code true}）。
 * <p>
 * 顺序说明：两者都**先移除条目、再回调** ⇒ 回调内再 {@code end()} 只会得到 {@code false}（不会同步递归重入）。
 */
final class CooldownPortImpl implements CooldownPort {

    private final RoleInstance owner;
    private final String componentId;

    CooldownPortImpl(RoleInstance owner, String componentId) {
        this.owner = owner;
        this.componentId = componentId;
    }

    @Override
    public boolean isReady() {
        //单一冷却表：无条目/已到期 ⇒ 就绪（"没有冷却这回事"的组件天然没有条目 ⇒ 恒就绪，语义与合并前一致）
        return owner.isCooldownReady(componentId);
    }

    @Override
    public int remainingTicks() {
        return owner.remainingCooldownTicks(componentId);
    }

    /** S2：以本次调用时刻重算到期（覆盖旧值）；旧段未到期 ⇒ 先清条目并回调 {@code RESTARTED}。 */
    @Override
    public void start(int ticks) {
        if (owner.clearCooldownForRestart(componentId)) {
            owner.dispatchCooldownEnd(componentId, CooldownAware.CooldownEndReason.RESTARTED);
        }
        //无声语义（如被动）：没有冷却这回事 ⇒ 不写表、也不置脏（合并前由端口按 kind 挡下，现收在表那一层）
        if (!owner.startCooldown(componentId, ticks)) return;
        //触点②（冷却启动）：端口适配器内置脏 —— 一个落点覆盖全部 kind（阶段 5 判据 C-01/C-02）。
        owner.hotbarRenderer().markDirty();
    }

    /** S3：仅在冷却中生效（清条目 + 回调 + 刷新 + true）；否则 false 且无副作用。 */
    @Override
    public boolean end() {
        return owner.endCooldown(componentId);
    }
}
