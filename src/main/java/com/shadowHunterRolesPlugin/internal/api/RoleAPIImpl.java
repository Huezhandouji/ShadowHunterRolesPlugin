package com.shadowHunterRolesPlugin.internal.api;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.api.RoleInfo;
import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

public class RoleAPIImpl implements RoleAPI {

    private final RoleManager roleManager;
    private final RoleRegistry registry;

    public RoleAPIImpl(RoleManager roleManager, RoleRegistry registry){
        this.roleManager = roleManager;
        this.registry = registry;
    }

    @Override
    public UUID getLastDamagerUuid(LivingEntity player) {
        return DamageUtil.getLastDamagerUUID(player);
    }


    @Override
    public boolean isValidRoleId(String id) {
        return registry.contains(id);
    }

    //设置和取消角色
    @Deprecated
    @Override
    public boolean setPlayerRole(Player player, String roleId) {
        return roleManager.selectRole(player, roleId);
    }
    @Override
    public boolean setPlayerRole(UUID uuid, String roleId) {
        return roleManager.selectRole(uuid, roleId);
    }

    @Deprecated
    @Override
    public boolean clearPlayerRole(Player player) {
        return roleManager.clearRole(player);
    }
    @Override
    public boolean clearPlayerRole(UUID uuid) {
        return roleManager.clearRole(uuid);
    }


    //角色查询
    private RoleInstance getRoleInstance(Player player){
        return roleManager.getRoleInstance(player);
    }
    private RoleInstance getRoleInstance(UUID uuid){
        return roleManager.getRoleInstance(uuid);
    }

    @Deprecated
    @Override
    public String getPlayerRoleId(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRole().getId() : null;
    }
    @Override
    public String getPlayerRoleId(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getRole().getId() : null;
    }

    @Deprecated
    @Override
    public Component getPlayerRoleDisplayName(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRole().getDisplayName() : null;
    }
    @Override
    public Component getPlayerRoleDisplayName(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getRole().getDisplayName() : null;
    }

    @Deprecated
    @Override
    public boolean hasRole(Player player) {
        return roleManager.hasRole(player);
    }
    @Override
    public boolean hasRole(UUID uuid) {
        return roleManager.hasRole(uuid);
    }

    //角色描述查询
    @Override
    public Component getRoleDisplayName(String roleID){
        Role role = registry.get(roleID);
        if(role == null){
            return Component.text("");
        }
        else{
            return role.getDisplayName();
        }
    }

    @Override
    public List<Component> getRoleDescription(String roleID){
        Role role = registry.get(roleID);
        if(role == null){
            return List.of(Component.text(""));
        }
        else{
            return role.getDescription();
        }
    }

    @Override
    public Material getRoleIcon(String roleID){
        Role role = registry.get(roleID);
        if(role == null){
            return Material.AIR;
        }
        else {
            return role.getIcon();
        }
    }

    //能量系统
    @Deprecated
    @Override
    public int getPlayerEnergy(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getCurrentEnergy() : -78; //-78代表没查到
    }
    @Override
    public int getPlayerEnergy(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getCurrentEnergy() : -78;
    }

    @Deprecated
    @Override
    public int getPlayerMaxEnergy(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getMaxEnergy() : -78;
    }
    @Override
    public int getPlayerMaxEnergy(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getMaxEnergy() : -78;
    }

    @Deprecated
    @Override
    public void setPlayerEnergy(Player player, int amount){
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.setCurrentEnergy(amount);
    }
    @Override
    public void setPlayerEnergy(UUID uuid, int amount){
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.setCurrentEnergy(amount);
    }

    @Deprecated
    @Override
    public void increaseEnergy(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.increaseEnergy(amount);
    }
    @Override
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = EnergyComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    public void increaseEnergy(UUID uuid, int amount) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.increaseEnergy(amount);
    }

    @Deprecated
    @Override
    public void decreaseEnergy(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.decreaseEnergy(amount);
    }
    @Override
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = EnergyComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    public void decreaseEnergy(UUID uuid, int amount) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.decreaseEnergy(amount);
    }

    //sanTE相关
    @Deprecated
    @Override
    public int getPlayerSanTE(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getCurrentSanTE() : RoleAPI.NO_ROLE_SAN_TE_SENTINEL;
    }
    @Override
    public int getPlayerSanTE(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getCurrentSanTE() : RoleAPI.NO_ROLE_SAN_TE_SENTINEL;
    }

    /** 只增入口（阶段 7 · 清理批）：无角色 ⇒ 空 Optional（不再需要调用方认哨兵）。 */
    @Override
    public OptionalInt getPlayerSanTEOptional(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? OptionalInt.of(instance.getCurrentSanTE()) : OptionalInt.empty();
    }

    @Override
    public OptionalInt getPlayerSanTEOptional(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? OptionalInt.of(instance.getCurrentSanTE()) : OptionalInt.empty();
    }

    @Deprecated
    @Override
    public int getPlayerMaxSanTE(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getMaxSanTE() : -78;
    }
    @Override
    public int getPlayerMaxSanTE(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getMaxSanTE() : -78;
    }

    @Deprecated
    @Override
    public void setPlayerSanTE(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.setCurrentSanTE(amount);
    }
    @Override
    public void setPlayerSanTE(UUID uuid, int amount) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.setCurrentSanTE(amount);
    }

    @Deprecated
    @Override
    public void increaseSanTE(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.increaseSanTE(amount);
    }
    @Override
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = SanTEComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    public void increaseSanTE(UUID uuid, int amount) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.increaseSanTE(amount);
    }

    @Deprecated
    @Override
    public void decreaseSanTE(Player player, int amount) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.decreaseSanTE(amount);
    }
    @Override
    public void decreaseSanTE(UUID uuid, int amount) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.decreaseSanTE(amount);
    }

    //生命值相关
    @Deprecated
    @Override
    public double getPlayerHealth(Player player) {
        return player.getHealth();
    }
    @Override
    public double getPlayerHealth(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return 0;
        Player player = instance.getPlayer();
        if(player == null) return 0;
        return player.getHealth();
    }

    @Deprecated
    @Override
    public double getPlayerMaxHealth(Player player) {
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }
    @Override
    public double getPlayerMaxHealth(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return 0;
        Player player = instance.getPlayer();
        if(player == null) return 0;
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    @Deprecated
    @Override
    public void healPlayer(Player player, double amount) {
        double newHealth = Math.min(player.getHealth() + amount, getPlayerMaxHealth(player));
        player.setHealth(newHealth);
    }
    @Override
    public void healPlayer(UUID uuid, double amount) {
        Player player = Bukkit.getPlayer(uuid);
        if(player == null) return;
        double newHealth = Math.min(player.getHealth() + amount, getPlayerMaxHealth(player));
        player.setHealth(newHealth);
    }

    //技能相关
    @Deprecated
    @Override
    public boolean isSkillReady(Player player, String skillId) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null && instance.isSkillReady(skillId);
    }
    @Override
    public boolean isSkillReady(UUID uuid, String skillId) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null && instance.isSkillReady(skillId);
    }

    @Deprecated
    @Override
    public int getSkillCooldownTick(Player player, String skillId) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.getRemainingSkillCooldownTicks(skillId) : 0;
    }
    @Override
    public int getSkillCooldownTick(UUID uuid, String skillId) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getRemainingSkillCooldownTicks(skillId) : 0;
    }

    //阵营相关
    //阶段 13 · t123（欠账 A 后半）：阵营**读取唯一入口 = `RoleInfo` 服务面** ✓ —— 旧写法走
    //`RoleInstance#getFaction()` 的**组件直读视图**（已随 FactionComponent 一并删除 ✗）。
    @Deprecated
    @Override
    public Faction getFaction(Player player) {
        RoleInstance instance = getRoleInstance(player);
        return instance != null ? instance.roleInfo().faction() : Faction.UNKNOWN;
    }
    @Override
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo#faction()（组件经角色信息服务取用，t90 唯一入口） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    public Faction getFaction(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.roleInfo().faction() : Faction.UNKNOWN;
    }

    //阶段 13 · t123：写侧改接**聚合根**（`RoleInstance#setFaction` 转调 `Role#setFaction`）✓
    //—— 旧落点 `FactionComponent#setFaction` 已随组件删除 ✗；`roleInfo` 服务面**不带写面**（R-1）✗。
    @Deprecated
    @Override
    public void setFaction(Player player, Faction faction) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.setFaction(faction);
    }
    @Override
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo/角色服务面（勿由外部直改阵营；组件侧经角色信息服务） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    public void setFaction(UUID uuid, Faction faction) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.setFaction(faction);
    }

    //阶段 13 · t123：复位同样改接**聚合根**（`RoleInstance#resetFaction` 转调 `Role#resetFaction`
    //⇒ 回落目标 = 角色模板声明的阵营，与旧 `FactionComponent#reset()` 逐字等价 ✓）。
    @Deprecated
    @Override
    public void resetFaction(Player player) {
        RoleInstance instance = getRoleInstance(player);
        if(instance == null) return;
        instance.resetFaction();
    }
    @Override
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo/角色服务面（勿由外部直改阵营；组件侧经角色信息服务） ✓（R-6：只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    public void resetFaction(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        if(instance == null) return;
        instance.resetFaction();
    }

    @Deprecated
    @Override
    public boolean areHostile(Player p1, Player p2) {
        return roleManager.areHostile(p1, p2);
    }
    @Override
    public boolean areHostile(UUID uuid1, UUID uuid2) {
        return roleManager.areHostile(uuid1, uuid2);
    }
    //阶段 3.3（RoleAPI 只增）：枚举已装配的角色 id 与只读快照
    @Override
    public Set<String> getAllRoleIds() {
        return registry.ids();
    }

    @Override
    public List<RoleInfo> getRoles() {
        List<RoleInfo> result = new ArrayList<>();
        for (Role role : registry.all()) {
            result.add(new RoleInfo(role.getId(), role.getDisplayName(), role.getDescription(), role.getIcon(), role.getFaction()));
        }
        return result;
    }

}
