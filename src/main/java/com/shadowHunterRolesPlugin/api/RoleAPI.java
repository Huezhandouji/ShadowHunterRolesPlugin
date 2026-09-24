package com.shadowHunterRolesPlugin.api;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;


public interface RoleAPI {

    //不建议使用所有以Player类型作参数的api

    //通过特殊设置的pdc查询最后攻击者
    UUID getLastDamagerUuid(LivingEntity player);

    //查询一个角色id是否存在，即这个角色是否被实现
    boolean isValidRoleId(String id);

    //设置角色
    @Deprecated
    boolean setPlayerRole(Player player, String roleId);
    boolean setPlayerRole(UUID uuid, String roleId);

    @Deprecated
    boolean clearPlayerRole(Player player);
    boolean clearPlayerRole(UUID uuid);

    //角色查询
    @Deprecated
    String getPlayerRoleId(Player player);
    String getPlayerRoleId(UUID uuid);

    @Deprecated
    Component getPlayerRoleDisplayName(Player player);
    Component getPlayerRoleDisplayName(UUID uuid);

    @Deprecated
    boolean hasRole(Player player);
    boolean hasRole(UUID uuid);

    //角色描述查询
    Component getRoleDisplayName(String roleId);
    List<Component> getRoleDescription(String roleId);
    Material getRoleIcon(String roleId);

    //角色清单（阶段 3.3：**只增**，既有方法签名一律未动）
    //下游用这两个方法自行发现"有哪些角色"，而不是 import 内部类去读注册表
    Set<String> getAllRoleIds();
    List<RoleInfo> getRoles();

    //能量系统
    @Deprecated
    int getPlayerEnergy(Player player);
    int getPlayerEnergy(UUID uuid);

    @Deprecated
    int getPlayerMaxEnergy(Player player);
    int getPlayerMaxEnergy(UUID uuid);

    @Deprecated
    void setPlayerEnergy(Player player, int amount);
    void setPlayerEnergy(UUID uuid, int amount);

    @Deprecated
    void increaseEnergy(Player player, int amount);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = EnergyComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void increaseEnergy(UUID uuid, int amount);

    @Deprecated
    void decreaseEnergy(Player player, int amount);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = EnergyComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void decreaseEnergy(UUID uuid, int amount);

    //sanTE
    /**
     * {@link #getPlayerSanTE(Player)} / {@link #getPlayerSanTE(UUID)} 在**玩家没有角色**时返回的哨兵值。
     * <p>阶段 7 · 清理批**只增**：把那个"魔法数"变成有名字、有文档的常量（**值与原实现逐字相同 = -78**）。
     * 新代码请改用 {@link #getPlayerSanTEOptional(UUID)} —— 它把"没有角色"表达成**空 Optional**，
     * 调用方不必先 {@link #hasRole(UUID)} 再读、也不必认哨兵。
     */
    int NO_ROLE_SAN_TE_SENTINEL = -78;

    /**
     * 当前 SanTE 值；**玩家没有角色时返回哨兵 {@value #NO_ROLE_SAN_TE_SENTINEL}**
     * （语义与迁移前**逐字不变**，本批未动它）。
     * <p>新代码建议改用 {@link #getPlayerSanTEOptional(Player)}。
     */
    @Deprecated
    int getPlayerSanTE(Player player);
    /** 同 {@link #getPlayerSanTE(Player)}（UUID 口径）。 */
    int getPlayerSanTE(UUID uuid);

    /**
     * **只增入口**（阶段 7 · 清理批）：当前 SanTE 值；**玩家没有角色时返回空 {@link OptionalInt}**。
     * <p>与 {@link #getPlayerSanTE(UUID)} 的哨兵语义**互补而非取代**：旧方法与旧返回值一字未动，
     * 本方法只是给"没有角色"提供一个**不需要认哨兵**的读法。
     */
    OptionalInt getPlayerSanTEOptional(Player player);
    /** 同 {@link #getPlayerSanTEOptional(Player)}（UUID 口径）。 */
    OptionalInt getPlayerSanTEOptional(UUID uuid);

    @Deprecated
    int getPlayerMaxSanTE(Player player);
    int getPlayerMaxSanTE(UUID uuid);

    @Deprecated
    void setPlayerSanTE(Player player, int amount);
    void setPlayerSanTE(UUID uuid, int amount);

    @Deprecated
    void increaseSanTE(Player player, int amount);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = SanTEComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void increaseSanTE(UUID uuid, int amount);

    @Deprecated
    void decreaseSanTE(Player player, int amount);
    void decreaseSanTE(UUID uuid, int amount);


    //生命值
    @Deprecated
    double getPlayerHealth(Player player);
    double getPlayerHealth(UUID uuid);

    @Deprecated
    double getPlayerMaxHealth(Player player);
    double getPlayerMaxHealth(UUID uuid);

    @Deprecated
    void healPlayer(Player player, double amount);
    void healPlayer(UUID uuid, double amount);

    //技能相关
    @Deprecated
    boolean isSkillReady(Player player, String skillId);
    boolean isSkillReady(UUID uuid, String skillId);

    @Deprecated
    int getSkillCooldownTick(Player player, String skillId);
    int getSkillCooldownTick(UUID uuid, String skillId);

    //阵营信息
    @Deprecated
    Faction getFaction(Player player);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo#faction()（组件经角色信息服务取用，t90 唯一入口） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    Faction getFaction(UUID uuid);

    @Deprecated
    void setFaction(Player player, Faction faction);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo/角色服务面（勿由外部直改阵营；组件侧经角色信息服务） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void setFaction(UUID uuid, Faction faction);

    @Deprecated
    void resetFaction(Player player);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo/角色服务面（勿由外部直改阵营；组件侧经角色信息服务） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void resetFaction(UUID uuid);

    @Deprecated
    boolean areHostile(Player p1, Player p2);
    boolean areHostile(UUID p1, UUID p2);
}
