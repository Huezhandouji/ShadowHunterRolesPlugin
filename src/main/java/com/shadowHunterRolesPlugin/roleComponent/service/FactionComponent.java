package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.platform.Task;

/**
 * 阵营组件（阶段 10 · t55 · A1）：系统级能力「阵营读写 + 敌对判定」的**组件形态**
 * （每角色实例一个，裁定③ / ⑧）。
 * <p><b>薄封装</b>：内部**转调既有端口** {@code FactionPort}；**关系表仍留平台**
 * （{@code FactionLookup} 是静态数据，不随实例复制），本组件只提供"判定入口 + 本实例的当前阵营"。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * private FactionComponent factions;
 * @Override public void awake() { factions = getComponent(FactionComponent.class); }
 * @Override public void update() { if (factions.hasEnemyInRange(8.0d)) { ... } }
 * }</pre>
 * <p><b>装配示例</b>：{@code builder.addComponent("factions", new FactionComponent.Specification());}（不占栏位）。
 * <p><b>本卡不改任何调用点</b>：既有组件仍走 {@code svc().factions()}；
 * 容器的 {@code getFaction/setFaction/resetFaction}（`RoleAPI` 的四组对外方法）**一字不动**。
 */
public class FactionComponent extends RoleComponent {

    public FactionComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 该玩家是否与**本实例**敌对（未选角色的玩家也算敌人）。 */
    public boolean isHostile(org.bukkit.entity.Player victim) {
        return svc().factions().isHostile(victim);
    }

    /** 本实例的当前阵营。 */
    public com.shadowHunterRolesPlugin.core.Faction faction() {
        return svc().factions().faction();
    }

    /** 半径内是否有敌人（几何 + 阵营语义逐字沿用既有实现）。 */
    public boolean hasEnemyInRange(double radius) {
        return svc().factions().hasEnemyInRange(radius);
    }

    /** 装配描述符：**不占栏位**；提供类型 = {@code FactionComponent.class}。 */
    public static final class Specification extends RoleComponent.Specification<FactionComponent> {

        public Specification() {
            super("FactionComponent");
        }

        @Override
        public FactionComponent create(String id, ComponentServices services) {
            return new FactionComponent(id, services);
        }
    }
}
