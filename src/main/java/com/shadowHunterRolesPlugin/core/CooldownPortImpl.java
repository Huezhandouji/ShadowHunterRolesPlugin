package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;

/**
 * {@link CooldownPort} 的独立适配器：**构造期已绑定本组件 id 与 kind**（组件不再传 id）。
 * 技能与主武器今天两张冷却表，按 {@link ItemKind} 分派到对应方法（合并到单一冷却命名空间属阶段 5）。
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
        return kind == ItemKind.SKILL ? owner.isSkillReady(componentId) : owner.isMainWeaponReady(componentId);
    }

    @Override
    public int remainingTicks() {
        return kind == ItemKind.SKILL
                ? owner.getRemainingSkillCooldownTicks(componentId)
                : owner.getRemainingMainWeaponCooldownTicks(componentId);
    }

    @Override
    public void start(int ticks) {
        if (kind == ItemKind.SKILL) {
            owner.startSkillCooldown(componentId, ticks);
        } else {
            owner.startMainWeaponCooldown(componentId, ticks);
        }
    }

    /**
     * 保留位：今天的冷却条目**永不清除**（O-21：唯一的 remove 在死方法里），
     * 故此处无对应实现；阶段 5 的冷却注册表接管后再补真实语义。
     */
    @Override
    public void end() {
        // no-op by design (见 javadoc)
    }
}
