package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.platform.FactionLookup;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * 阵营组件（阶段 10 · t63 · A1 改正）：系统级能力「阵营读写 + 敌对判定」的**组件形态**
 * （每角色实例一个，裁定③ / ⑧）。
 * <p><b>★ 本组件持有状态与行为</b>：玩家**当前阵营**的真值 {@link #faction}（原 {@code RoleInstance.faction}
 * 字段搬进本组件）+ 敌对判定的两个入口（{@link #isHostile(Player)} / {@link #hasEnemyInRange(double)}）✓
 * —— **不再转调任何旧端口** ✗。
 * <p><b>关系表仍留平台</b>（冻结件 §8）：{@link FactionLookup} 是**静态数据**（未选角色 ⇒ UNKNOWN ⇒ 敌对），
 * 不随实例复制 —— 属"真正外部的东西"（单例）⇒ 允许依赖 ✓，且它**不进依赖图**。
 * <p><b>语义逐字保留</b>：{@code faction()} 的"未设 ⇒ 回落角色模板阵营"、{@code hasEnemyInRange} 的
 * 几何 + "未选角色的玩家也算敌人"、{@code reset()} 的回落语义，全部与既有端口实现逐字一致。
 */
public class FactionComponent extends RoleComponent {

    private final FactionLookup lookup;

    /** 角色模板声明的阵营（构造期由容器给出）—— 回落目标。 */
    private final Faction defaultFaction;

    /** ★ 真值：玩家当前阵营（唯一持有处；{@code null} ⇒ 回落 {@link #defaultFaction}）。 */
    private Faction faction;

    public FactionComponent(String id, ComponentServices services, FactionLookup lookup, Faction defaultFaction) {
        super(id, services);
        this.lookup = lookup;
        this.defaultFaction = defaultFaction;
        //与既有 RoleInstance 构造期逐字一致：this.faction = role.getFaction()
        this.faction = defaultFaction;
    }

    /** 本实例的当前阵营（未设 ⇒ 角色模板阵营）。 */
    public Faction faction() {
        return faction != null ? faction : defaultFaction;
    }

    /** 写入当前阵营（{@code RoleAPI#setFaction} 的落点）。 */
    public void setFaction(Faction faction) {
        this.faction = faction;
    }

    /** 复位为角色模板阵营（{@code RoleAPI#resetFaction} 的落点）。 */
    public void reset() {
        this.faction = defaultFaction;
    }

    /** 该玩家是否与**本实例**敌对（未选角色的玩家也算敌人）。 */
    public boolean isHostile(Player victim) {
        return lookup.isHostile(faction(), victim);
    }

    /**
     * 半径内是否有敌人（几何 + 阵营语义**逐字**沿用原 {@code SkillUtil.hasEnemyInRange}）：
     * 以本实例玩家位置为圆心、**未选角色的玩家也算敌人**。
     */
    public boolean hasEnemyInRange(double radius) {
        Location loc = svc().self().player().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        Faction selfFaction = faction();

        for (Player p : loc.getNearbyPlayers(radius)) {
            if (p == null) {
                continue;
            }
            //没有选角色的玩家也要算进来（FactionLookup 对未选角色返回敌对）
            if (lookup.isHostile(selfFaction, p)) {
                return true;
            }
        }
        return false;
    }
}
