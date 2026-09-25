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

    //能量系统（**全部做空** ✗ —— 仍在但不再生效 ✓；替代路径 = executeComponentOperation ✓）
    @Deprecated
    @Override
    public int getPlayerEnergy(Player player) {
        stubbed("getPlayerEnergy(Player)");
        return 0;
    }
    @Deprecated
    @Override
    public int getPlayerEnergy(UUID uuid) {
        stubbed("getPlayerEnergy(UUID)");
        return 0;
    }

    @Deprecated
    @Override
    public int getPlayerMaxEnergy(Player player) {
        stubbed("getPlayerMaxEnergy(Player)");
        return 0;
    }
    @Deprecated
    @Override
    public int getPlayerMaxEnergy(UUID uuid) {
        stubbed("getPlayerMaxEnergy(UUID)");
        return 0;
    }

    @Deprecated
    @Override
    public void setPlayerEnergy(Player player, int amount){
        stubbed("setPlayerEnergy(Player,int)");
    }
    @Deprecated
    @Override
    public void setPlayerEnergy(UUID uuid, int amount){
        stubbed("setPlayerEnergy(UUID,int)");
    }

    @Deprecated
    @Override
    public void increaseEnergy(Player player, int amount) {
        stubbed("increaseEnergy(Player,int)");
    }
    @Deprecated
    @Override
    public void increaseEnergy(UUID uuid, int amount) {
        stubbed("increaseEnergy(UUID,int)");
    }

    @Deprecated
    @Override
    public void decreaseEnergy(Player player, int amount) {
        stubbed("decreaseEnergy(Player,int)");
    }
    @Deprecated
    @Override
    public void decreaseEnergy(UUID uuid, int amount) {
        stubbed("decreaseEnergy(UUID,int)");
    }

    //sanTE相关（**全部做空** ✗ —— 含 `getPlayerSanTEOptional` ×2 ✓）
    @Deprecated
    @Override
    public int getPlayerSanTE(Player player) {
        stubbed("getPlayerSanTE(Player)");
        return 0;
    }
    @Deprecated
    @Override
    public int getPlayerSanTE(UUID uuid) {
        stubbed("getPlayerSanTE(UUID)");
        return 0;
    }

    /** **已做空** ✗（仍在但不再生效 ✓）⇒ 恒空 Optional ✓。 */
    @Deprecated
    @Override
    public OptionalInt getPlayerSanTEOptional(Player player) {
        stubbed("getPlayerSanTEOptional(Player)");
        return OptionalInt.empty();
    }
    @Deprecated
    @Override
    public OptionalInt getPlayerSanTEOptional(UUID uuid) {
        stubbed("getPlayerSanTEOptional(UUID)");
        return OptionalInt.empty();
    }

    @Deprecated
    @Override
    public int getPlayerMaxSanTE(Player player) {
        stubbed("getPlayerMaxSanTE(Player)");
        return 0;
    }
    @Deprecated
    @Override
    public int getPlayerMaxSanTE(UUID uuid) {
        stubbed("getPlayerMaxSanTE(UUID)");
        return 0;
    }

    @Deprecated
    @Override
    public void setPlayerSanTE(Player player, int amount) {
        stubbed("setPlayerSanTE(Player,int)");
    }
    @Deprecated
    @Override
    public void setPlayerSanTE(UUID uuid, int amount) {
        stubbed("setPlayerSanTE(UUID,int)");
    }

    @Deprecated
    @Override
    public void increaseSanTE(Player player, int amount) {
        stubbed("increaseSanTE(Player,int)");
    }
    @Deprecated
    @Override
    public void increaseSanTE(UUID uuid, int amount) {
        stubbed("increaseSanTE(UUID,int)");
    }

    @Deprecated
    @Override
    public void decreaseSanTE(Player player, int amount) {
        stubbed("decreaseSanTE(Player,int)");
    }
    @Deprecated
    @Override
    public void decreaseSanTE(UUID uuid, int amount) {
        stubbed("decreaseSanTE(UUID,int)");
    }

    //生命值相关（**全部做空** ✗ —— 仍在但不再生效 ✓；替代路径 = executeComponentOperation ✓）
    @Deprecated
    @Override
    public double getPlayerHealth(Player player) {
        stubbed("getPlayerHealth(Player)");
        return 0;
    }
    @Deprecated
    @Override
    public double getPlayerHealth(UUID uuid) {
        stubbed("getPlayerHealth(UUID)");
        return 0;
    }

    @Deprecated
    @Override
    public double getPlayerMaxHealth(Player player) {
        stubbed("getPlayerMaxHealth(Player)");
        return 0;
    }
    @Deprecated
    @Override
    public double getPlayerMaxHealth(UUID uuid) {
        stubbed("getPlayerMaxHealth(UUID)");
        return 0;
    }

    @Deprecated
    @Override
    public void healPlayer(Player player, double amount) {
        stubbed("healPlayer(Player,double)");
    }
    @Deprecated
    @Override
    public void healPlayer(UUID uuid, double amount) {
        stubbed("healPlayer(UUID,double)");
    }

    //技能相关（**全部做空** ✗ —— 仍在但不再生效 ✓）
    @Deprecated
    @Override
    public boolean isSkillReady(Player player, String skillId) {
        stubbed("isSkillReady(Player,String)");
        return false;
    }
    @Deprecated
    @Override
    public boolean isSkillReady(UUID uuid, String skillId) {
        stubbed("isSkillReady(UUID,String)");
        return false;
    }

    @Deprecated
    @Override
    public int getSkillCooldownTick(Player player, String skillId) {
        stubbed("getSkillCooldownTick(Player,String)");
        return 0;
    }
    @Deprecated
    @Override
    public int getSkillCooldownTick(UUID uuid, String skillId) {
        stubbed("getSkillCooldownTick(UUID,String)");
        return 0;
    }

    //阵营相关（**全部做空** ✗ —— 仍在但不再生效 ✓；阵营读取唯一入口仍是 `RoleInfo` 服务面 ✓）
    //阵营**读取唯一入口 = `RoleInfo` 服务面** ✓ —— 既有写法走
    //`RoleInstance#getFaction()` 的**组件直读视图**（已随 FactionComponent 一并删除 ✗）。
    @Deprecated
    @Override
    public Faction getFaction(Player player) {
        stubbed("getFaction(Player)");
        return Faction.UNKNOWN;
    }
    @Deprecated
    @Override
    public Faction getFaction(UUID uuid) {
        stubbed("getFaction(UUID)");
        return Faction.UNKNOWN;
    }

    //写侧**做空** ✗ —— 那条"转调聚合根"的写视图（`RoleInstance#setFaction/resetFaction`）
    //已一并删除 ✗（做空后它再无消费者 ✓）；组件侧要改阵营请走角色服务面，**不要**由外部直改 ✗。
    @Deprecated
    @Override
    public void setFaction(Player player, Faction faction) {
        stubbed("setFaction(Player,Faction)");
    }
    @Deprecated
    @Override
    public void setFaction(UUID uuid, Faction faction) {
        stubbed("setFaction(UUID,Faction)");
    }

    @Deprecated
    @Override
    public void resetFaction(Player player) {
        stubbed("resetFaction(Player)");
    }
    @Deprecated
    @Override
    public void resetFaction(UUID uuid) {
        stubbed("resetFaction(UUID)");
    }

    @Deprecated
    @Override
    public boolean areHostile(Player p1, Player p2) {
        stubbed("areHostile(Player,Player)");
        return false;
    }
    @Deprecated
    @Override
    public boolean areHostile(UUID uuid1, UUID uuid2) {
        stubbed("areHostile(UUID,UUID)");
        return false;
    }

    // ───────── 老 API 的**做空实现** ─────────

    /**
     * **老 API 的做空实现**（老的"直接操作组件"的 API 一律做空，改用组件操作面 ✓）。
     * <p><b>方法仍在、签名与注解一律保留</b> ✓（第三方仍能编译 ✓），但**不再生效** ✗ ——
     * 每次调用记一条 WARNING（内容含**方法名** + **替代路径** ✓）。
     * <p><b>返回中性哨兵值</b> ✓（{@code false} / {@code 0} / {@code OptionalInt.empty()} /
     * {@link Faction#UNKNOWN}）—— ★ **不用"随便一个数字"** ✗✗：假的 9999 会被调用方当真能量用 ✗。
     * <p>替代路径 = {@link RoleAPI#executeComponentOperation(UUID, String, String)} ✓（读写都走它 ✓
     * —— 一条实现、两个门面 ✓）。
     */
    private void stubbed(String method) {
        Bukkit.getLogger().warning("[RoleAPI] " + method + " 已做空、不再生效 ✗（本方法直接操作组件）："
                + "替代路径 = executeComponentOperation(uuid, componentId, payload) ✓");
    }

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
