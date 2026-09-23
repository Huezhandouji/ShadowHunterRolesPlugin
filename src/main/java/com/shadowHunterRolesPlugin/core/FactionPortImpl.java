package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.FactionPort;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.FactionComponent;
import org.bukkit.entity.Player;

/**
 * {@link FactionPort} 的独立适配器（阶段 10 · t63 · A2）：**纯转发**到 {@link FactionComponent} ——
 * 当前阵营的真值（原 {@code RoleInstance.faction} 字段）与两个判定入口都已搬到组件，本类不持有任何状态 ✗。
 * <p>关系表仍留平台（{@code platform.factions()}）⇒ 组件依赖它是"外部单例"许可，见组件 javadoc。
 * <p>第 3 步之前的**临时兼容层**；既有调用点一字未动。
 */
final class FactionPortImpl implements FactionPort {

    private final RoleInstance owner;

    FactionPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    private FactionComponent component() {
        return owner.factionComponent();
    }

    @Override
    public boolean isHostile(Player victim) {
        return component().isHostile(victim);
    }

    @Override
    public Faction faction() {
        return component().faction();
    }

    @Override
    public boolean hasEnemyInRange(double radius) {
        return component().hasEnemyInRange(radius);
    }
}
