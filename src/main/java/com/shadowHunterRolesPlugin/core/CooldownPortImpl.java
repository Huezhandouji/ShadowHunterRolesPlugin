package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.hotbar.CooldownAware;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

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
 *
 * <p><b>阶段 11 · t84（F-1/F-2 修复：状态面一律按实例）</b>：本适配器由
 * {@code RoleInstance#createServices(componentId)} 在**组件构造之前**造出 ⇒ 构造期物理上拿不到实例引用，
 * 于是它曾经**只持 {@link #componentId}**，能力判定与回调投递都只能按 id 二次解析（{@code getById} =
 * **添加顺序第一个**同 id 者）⇒ 同 id 两份实例时会判错/投错 ✗。修法 = **创建后绑定**：
 * {@link #bind(RoleComponent)} 由两个创建点（装配期 {@code initComponents}、运行期动态增
 * {@code ComponentLookupImpl#insertAt}）在构造返回后**立刻**调用；此后能力判定与回调投递一律用
 * **绑定实例**，未绑定时才按 id 回落（{@link RoleInstance#preferBound(RoleComponent, RoleComponent)}）
 * —— **回落不得静默丢弃** ✗。
 * <p><b>表仍按 id</b>（单一冷却命名空间本就"一个 id 一份冷却"）⇒ {@code isReady()}/{@code remainingTicks()}
 * 与写表键都保持 id 口径，**只有"这是谁的冷却"与"回调投给谁"按实例** ✓。
 */
final class CooldownPortImpl implements CooldownPort {

    private final RoleInstance owner;
    private final String componentId;

    /**
     * **创建后绑定**的目标（阶段 11 · t84）：{@code null} ⇒ 按 id 回落
     * （见 {@link RoleInstance#preferBound(RoleComponent, RoleComponent)}）。
     */
    private RoleComponent bound;

    CooldownPortImpl(RoleInstance owner, String componentId) {
        this.owner = owner;
        this.componentId = componentId;
    }

    /** 绑定"持有本端口的那一个组件实例"（由两个创建点在构造返回后**立刻**调用；本身幂等，后绑定覆盖）。 */
    void bind(RoleComponent component) {
        this.bound = component;
    }

    /** 本端口的**目标实例**：已绑定 ⇒ 绑定实例；未绑定 ⇒ 按 id 回落（**不得静默丢弃** ✗）。 */
    private RoleComponent target() {
        return RoleInstance.preferBound(bound, owner.resolveComponent(componentId));
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
        //阶段 11 · t84（F-1/F-2）：目标实例**只解析一次**，能力判定与 RESTARTED 回调用的是**同一个**实例 ✓
        RoleComponent target = target();
        if (owner.clearCooldownForRestart(componentId)) {
            owner.dispatchCooldownEnd(target, CooldownAware.CooldownEndReason.RESTARTED);
        }
        //无声语义（如被动）：没有冷却这回事 ⇒ 不写表、也不置脏（判据 = **这一个实例**的能力接口，见 t84）
        if (!owner.startCooldown(target, componentId, ticks)) return;
        //触点②（冷却启动）：端口适配器内置脏 —— 一个落点覆盖全部 kind（阶段 5 判据 C-01/C-02）。
        owner.hotbarRenderer().markDirty();
    }

    /** S3：仅在冷却中生效（清条目 + 回调 + 刷新 + true）；否则 false 且无副作用。 */
    @Override
    public boolean end() {
        //阶段 11 · t84（F-2）：回调目标 = **绑定实例**（未绑定才按 id 回落）⇒ 同 id 两份实例下不投错 ✓
        return owner.endCooldown(target(), componentId);
    }
}
