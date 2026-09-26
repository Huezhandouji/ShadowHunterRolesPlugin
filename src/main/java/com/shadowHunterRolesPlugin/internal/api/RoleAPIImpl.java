package com.shadowHunterRolesPlugin.internal.api;
import com.shadowHunterRolesPlugin.core.component.ComponentRegistry;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.api.RoleInfo;
import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    @Override
    public boolean setPlayerRole(UUID uuid, String roleId) {
        return roleManager.selectRole(uuid, roleId);
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


    @Override
    public String getPlayerRoleId(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getRole().getId() : null;
    }


    @Override
    public Component getPlayerRoleDisplayName(UUID uuid) {
        RoleInstance instance = getRoleInstance(uuid);
        return instance != null ? instance.getRole().getDisplayName() : null;
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






    // ───────── 阵营（★ 真值在聚合根 `Role`）─────────

    /**
     * **两个玩家是否敌对**：转调 {@link RoleManager#areHostile(UUID, UUID)}（**唯一实现点**）。
     *
     * <p>★ 它**不是**"直接操作组件" —— 判定链是
     * `RoleManager` → 双方实例的 {@code roleInfo()} 服务面 → {@code platform.FactionLookup} 关系表
     * ⇒ 阵营真值仍只从**聚合根**读 ✓。
     *
     * <p>语义：任一方无角色 / 实例缺失 ⇒ {@code true}（与既有口径一致）。
     */
    @Override
    public boolean areHostile(UUID uuid1, UUID uuid2) {
        return roleManager.areHostile(uuid1, uuid2);
    }

    //★ `getFaction(UUID)` / `setFaction(UUID,Faction)` / `resetFaction(UUID)` 三条**已删除** ——
    // 它们曾是"直接操作组件"时代的空壳（恒回 `UNKNOWN` / 空操作）：
    //   读 ⇒ 走 {@link RoleInfo#faction()}（角色只读快照，**唯一读入口**）
    //   写 ⇒ 只在聚合根上（{@code Role#setFaction} / {@code Role#resetFaction}），**不由外部 API 直改**


    //枚举已装配的角色 id 与只读快照
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

    // ───────── 组件操作面（**唯一**操作入口） ─────────

    /**
     * **唯一**的"操作角色"入口（读与写都走它 ✓）：解析实例 → 按 id 定位组件 → 转发给组件的操作面 ✓。
     * <p><b>不做的事</b> ✗：不解析 payload 的 grammar（那是组件的事 ✓）、不做权限/审计（归指令面那片 ✓）、
     * 不缓存、不持有任何状态 ✓。
     * <p><b>失败一律回 {@code null}</b> ✓（逐条见 {@link RoleAPI#executeComponentOperation} 的 javadoc）；
     * 组件内部异常在此**被捕获** ⇒ 不逃到调用方 ✓。
     */
    @Override
    public String executeComponentOperation(UUID uuid, String componentId, String payload) {
        if (uuid == null || componentId == null) return null;
        RoleInstance instance = getRoleInstance(uuid);
        if (instance == null) return null;                       // 无角色实例 ⇒ 拒绝（"仅在线" ✓）
        return dispatchOperation(instance.componentRegistry().all(), componentId, payload);
    }

    /**
     * **定位 + 派发（纯函数 ⇒ 离线可测 ✓）**：`componentId` 可带 `#index` 消歧（**0 基** ✓）。
     * <p>规则（逐条可测）：id 为空 / `#` 后非数字 / 下标为负 ⇒ {@code null} ✗；命中 **0 份** ⇒ {@code null} ✗；
     * 同 id **多份且未给下标** ⇒ {@code null} ✗（**绝不静默取第一份** ✓）；下标**越界** ⇒ {@code null} ✗；
     * 目标组件**未实现** {@link OperationProvider} ⇒ {@code null} ✗；组件抛异常 ⇒ **捕获**后回 {@code null} ✓。
     * <p>★ 复用既有只读入口（`componentRegistry().all()` 线性过滤）⇒ **未新增任何查取入口/接口** ✓。
     *
     * @param components  容器内的组件快照（调用方给 `all()` ✓；测试可直接给桩件列表 ✓）
     * @param componentId 组件 id，可带 `#index`（**最后一个** `#` 为分隔符 ✓）
     * @param payload     整段操作文本（原样转发 ✓）
     * @return 组件操作面的返回值原样 ✓；任一条不满足 ⇒ {@code null}
     */
    static String dispatchOperation(List<RoleComponent> components, String componentId, String payload) {
        if (components == null || componentId == null) return null;

        int separator = componentId.lastIndexOf('#');
        String id = separator < 0 ? componentId : componentId.substring(0, separator);
        String indexToken = separator < 0 ? null : componentId.substring(separator + 1);
        if (id.isEmpty()) return null;                            // "#2" 之类 ⇒ 语法不合法

        Integer index = null;
        if (indexToken != null) {
            try {
                index = Integer.parseInt(indexToken);
            } catch (NumberFormatException notAnIndex) {
                return null;                                      // "energy#abc" ⇒ 语法不合法
            }
            if (index < 0) return null;
        }

        List<RoleComponent> matches = new ArrayList<>();
        for (RoleComponent component : components) {
            if (component != null && id.equals(component.getId())) {
                matches.add(component);
            }
        }
        if (matches.isEmpty()) return null;                       // 命中 0 份

        RoleComponent target;
        if (index == null) {
            if (matches.size() > 1) return null;                  // 多份且未给下标 ⇒ 拒绝（不静默取第一份 ✗）
            target = matches.get(0);
        } else {
            if (index >= matches.size()) return null;             // 下标越界
            target = matches.get(index);
        }

        if (!(target instanceof OperationProvider provider)) return null;   // 未实现操作面 ⇒ 不支持
        try {
            return provider.onOperationCommand(payload);          // 原样返回（null/""/非空串三种约定 ✓）
        } catch (RuntimeException componentFailure) {
            return null;                                          // 组件内部异常不得逃到调用方 ✓
        }
    }

}
