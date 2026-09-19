package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.hotbar.CooldownAware;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;

/**
 * {@link CooldownPort} 的独立适配器：**构造期已绑定本组件 id 与 kind**（组件不再传 id）。
 * 技能与主武器今天两张冷却表，按 {@link ItemKind} 分派到对应方法（合并到单一冷却命名空间属阶段 5）。
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
    private final ItemKind kind;

    CooldownPortImpl(RoleInstance owner, String componentId, ItemKind kind) {
        this.owner = owner;
        this.componentId = componentId;
        this.kind = kind;
    }

    @Override
    public boolean isReady() {
        //PASSIVE（阶段 6）：显式无声语义 —— 被动没有冷却，恒就绪（不落进技能表/主武器表）
        if (kind == ItemKind.PASSIVE) return true;
        return kind == ItemKind.SKILL ? owner.isSkillReady(componentId) : owner.isMainWeaponReady(componentId);
    }

    @Override
    public int remainingTicks() {
        if (kind == ItemKind.PASSIVE) return 0;
        return kind == ItemKind.SKILL
                ? owner.getRemainingSkillCooldownTicks(componentId)
                : owner.getRemainingMainWeaponCooldownTicks(componentId);
    }

    /** S2：以本次调用时刻重算到期（覆盖旧值）；旧段未到期 ⇒ 先清条目并回调 {@code RESTARTED}。 */
    @Override
    public void start(int ticks) {
        //PASSIVE（阶段 6）：不写任何表、不派发、不置脏（被动没有冷却；若将来需要，须先立项给它一张表）
        if (kind == ItemKind.PASSIVE) return;
        if (owner.clearCooldownForRestart(componentId, kind)) {
            owner.dispatchCooldownEnd(componentId, CooldownAware.CooldownEndReason.RESTARTED);
        }
        if (kind == ItemKind.SKILL) {
            owner.startSkillCooldown(componentId, ticks);
        } else {
            owner.startMainWeaponCooldown(componentId, ticks);
        }
        //触点②（冷却启动）：端口适配器内置脏 —— 一个落点覆盖技能与主武器两 kind（阶段 5 判据 C-01/C-02）。
        owner.hotbarRenderer().markDirty();
    }

    /** S3：仅在冷却中生效（清条目 + 回调 + 刷新 + true）；否则 false 且无副作用。 */
    @Override
    public boolean end() {
        if (kind == ItemKind.PASSIVE) return false;
        return owner.endCooldown(componentId, kind);
    }
}