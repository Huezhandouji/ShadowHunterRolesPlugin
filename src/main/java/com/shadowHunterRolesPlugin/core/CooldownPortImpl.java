package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.CooldownPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * {@link CooldownPort} 的**兼容薄壳**（阶段 13 · t105 · N6）：**端口不再是冷却真值来源** ✗ ——
 * 冷却的**状态与判断**整体归**组件实例**（`ActiveComponent` 每实例一份 `cooldownUntilTick`）；
 * 本类只把旧的端口签名**转问组件**，让第④批删除前既有引用面仍能编译 ✓。
 * <p><b>语义对照（逐条）</b>：
 * <ul>
 *   <li>{@code isReady()} = "没有冷却这回事，或已到期" ⇒ {@code !isCoolingDown()}；
 *       目标实例**不是主动组件**（无冷却能力）⇒ **恒就绪** ✓（与迁移前"没有条目 ⇒ 恒就绪"逐字等价 ✓）；</li>
 *   <li>{@code remainingTicks()} ⇒ 组件的剩余刻读数（不在冷却中 ⇒ {@code 0} ✓）；</li>
 *   <li>{@code start(int)} ⇒ 组件的**按显式刻数**启动重载（纯委托 ✓）；启动成功仍触发一次**刷新置脏**
 *       （触点②的落点保留 ✓，与迁移前同一动作）；</li>
 *   <li>{@code end()} ⇒ 仅在**冷却中**生效（清状态 + 置脏 + {@code true}），否则 {@code false} 且**无副作用** ✓；</li>
 *   <li>迁移前的"冷却结束回调"（`RESTARTED` / `ENDED_BY_COMPONENT`）随该能力接口一并删除 ✓ ——
 *       组件自持冷却后框架不再派发（全库零覆写点 ⇒ 删除零行为变化 ✓）。</li>
 * </ul>
 * <p><b>阶段 11 · t84 的"创建后绑定"仍然保留</b>（调用方与第④批删除卡都要用到 ✓）：构造期物理上拿不到
 * 实例引用 ⇒ {@link #bind(RoleComponent)} 由两个创建点（装配期 {@code initComponents}、运行期动态增
 * {@code ComponentLookupImpl#insertAt}）在构造返回后**立刻**调用；此后一律用**绑定实例**，
 * 未绑定时才按 id 回落（{@link RoleInstance#preferBound(RoleComponent, RoleComponent)}）—— **回落不得静默丢弃** ✗。
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

    /** 目标实例的**冷却视图**；不是主动组件（无冷却能力）⇒ {@code null}（读口恒就绪、写口 no-op ✓）。 */
    private ActiveComponent cooling() {
        return target() instanceof ActiveComponent active ? active : null;
    }

    @Override
    public boolean isReady() {
        ActiveComponent active = cooling();
        return active == null || !active.isCoolingDown();
    }

    @Override
    public int remainingTicks() {
        ActiveComponent active = cooling();
        return active == null ? 0 : active.remainingCooldownTicks();
    }

    /** 以**显式刻数**启动（覆盖式重启）—— 纯委托到组件的同形重载 ✓。 */
    @Override
    public void start(int ticks) {
        ActiveComponent active = cooling();
        if (active == null) return;
        //无声语义（如被动）：没有冷却这回事 ⇒ 组件不写状态、本壳也不置脏 ✓
        if (!active.startCooldown(ticks)) return;
        owner.hotbarRenderer().markDirty();
    }

    /** 仅在冷却中生效（清状态 + 置脏 + true）；否则 false 且无副作用 ✓。 */
    @Override
    public boolean end() {
        ActiveComponent active = cooling();
        if (active == null || !active.isCoolingDown()) return false;
        active.stopCooldown();
        owner.hotbarRenderer().markDirty();
        return true;
    }
}
