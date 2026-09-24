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

    //角色清单（既有方法签名一律未动）
    //下游用这两个方法自行发现"有哪些角色"，而不是 import 内部类去读注册表
    Set<String> getAllRoleIds();
    List<RoleInfo> getRoles();

    //能量系统
    @Deprecated
    int getPlayerEnergy(Player player);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    int getPlayerEnergy(UUID uuid);

    @Deprecated
    int getPlayerMaxEnergy(Player player);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    int getPlayerMaxEnergy(UUID uuid);

    @Deprecated
    void setPlayerEnergy(Player player, int amount);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    void setPlayerEnergy(UUID uuid, int amount);

    @Deprecated
    void increaseEnergy(Player player, int amount);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = EnergyComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void increaseEnergy(UUID uuid, int amount);

    @Deprecated
    void decreaseEnergy(Player player, int amount);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = EnergyComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void decreaseEnergy(UUID uuid, int amount);

    //sanTE
    /**
     * {@link #getPlayerSanTE(Player)} / {@link #getPlayerSanTE(UUID)} 在**玩家没有角色**时返回的哨兵值。
     * <p>把那个"魔法数"变成有名字、有文档的常量（**值与既有实现逐字相同 = -78**）。
     * 新代码请改用 {@link #getPlayerSanTEOptional(UUID)} —— 它把"没有角色"表达成**空 Optional**，
     * 调用方不必先 {@link #hasRole(UUID)} 再读、也不必认哨兵。
     */
    int NO_ROLE_SAN_TE_SENTINEL = -78;

    /**
     * 当前 SanTE 值；**玩家没有角色时返回哨兵 {@value #NO_ROLE_SAN_TE_SENTINEL}**
     * （语义**逐字不变**，本方法未动它）。
     * <p>新代码建议改用 {@link #getPlayerSanTEOptional(Player)}。
     */
    @Deprecated
    int getPlayerSanTE(Player player);
    /**
     * 同 {@link #getPlayerSanTE(Player)}（UUID 口径）。
     *
     * @deprecated **已做空（仍在但不再生效）** ✗ —— 现恒回 {@code 0} ✓；
     *             替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。
     */
    @Deprecated
    int getPlayerSanTE(UUID uuid);

    /**
     * 当前 SanTE 值；**玩家没有角色时返回空 {@link OptionalInt}**。
     * <p>与 {@link #getPlayerSanTE(UUID)} 的哨兵语义**互补而非取代**：旧方法与旧返回值一字未动，
     * 本方法只是给"没有角色"提供一个**不需要认哨兵**的读法。
     *
     * @deprecated **已做空（仍在但不再生效）** ✗ —— 现恒回**空 {@link OptionalInt}** ✓；
     *             替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。
     */
    @Deprecated
    OptionalInt getPlayerSanTEOptional(Player player);
    /**
     * 同 {@link #getPlayerSanTEOptional(Player)}（UUID 口径）。
     *
     * @deprecated **已做空（仍在但不再生效）** ✗ —— 现恒回**空 {@link OptionalInt}** ✓；
     *             替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。
     */
    @Deprecated
    OptionalInt getPlayerSanTEOptional(UUID uuid);

    @Deprecated
    int getPlayerMaxSanTE(Player player);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    int getPlayerMaxSanTE(UUID uuid);

    @Deprecated
    void setPlayerSanTE(Player player, int amount);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    void setPlayerSanTE(UUID uuid, int amount);

    @Deprecated
    void increaseSanTE(Player player, int amount);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = SanTEComponent（经 svc().components() 或 getAllByType 直接取组件） ✓（只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void increaseSanTE(UUID uuid, int amount);

    @Deprecated
    void decreaseSanTE(Player player, int amount);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    void decreaseSanTE(UUID uuid, int amount);


    //生命值
    @Deprecated
    double getPlayerHealth(Player player);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    double getPlayerHealth(UUID uuid);

    @Deprecated
    double getPlayerMaxHealth(Player player);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    double getPlayerMaxHealth(UUID uuid);

    @Deprecated
    void healPlayer(Player player, double amount);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    void healPlayer(UUID uuid, double amount);

    //技能相关
    @Deprecated
    boolean isSkillReady(Player player, String skillId);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    boolean isSkillReady(UUID uuid, String skillId);

    @Deprecated
    int getSkillCooldownTick(Player player, String skillId);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    int getSkillCooldownTick(UUID uuid, String skillId);

    //阵营信息
    @Deprecated
    Faction getFaction(Player player);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo#faction()（组件经角色信息服务取用，唯一入口） ✓（只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    Faction getFaction(UUID uuid);

    @Deprecated
    void setFaction(Player player, Faction faction);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo/角色服务面（勿由外部直改阵营；组件侧经角色信息服务） ✓（只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void setFaction(UUID uuid, Faction faction);

    @Deprecated
    void resetFaction(Player player);
    /** @deprecated 本方法**直接操作组件** ✗；替代路径 = RoleInfo/角色服务面（勿由外部直改阵营；组件侧经角色信息服务） ✓（只许 ComponentLookup/RoleInfo/Self 三端口 + 直接取组件）✓。 */     @Deprecated
    void resetFaction(UUID uuid);

    @Deprecated
    boolean areHostile(Player p1, Player p2);
    /** @deprecated **已做空（仍在但不再生效）** ✗；替代路径 = {@link #executeComponentOperation(UUID, String, String)} ✓。 */
    @Deprecated
    boolean areHostile(UUID p1, UUID p2);

    //组件操作面 —— **唯一**的操作角色入口 ✓
    /**
     * **执行一条组件操作**：**唯一**的"操作角色"入口 ✓ ——
     * 读与写都走它 ✓（写操作回"写后状态"、读操作回值本身 ✓）。
     * <p><b>grammar 由组件自己规定</b> ✓：payload 的**首 token 必为操作动词** ✓（如 {@code add 5} / {@code current}），
     * 其余部分由目标组件自解析 ✓ ⇒ 具体动词表见**实现它的组件的 javadoc**（如能量组件 ✓）。
     * <p><b>与 {@code OperationProvider} 的关系</b>：本方法只做「解析实例 → 按 id 定位组件 → 转发」✓ ——
     * 目标组件**未实现** {@code OperationProvider} ⇒ 回 {@code null} ✗（不支持操作指令）。
     *
     * @param uuid        目标玩家。**参数类型用 UUID** ✓（此后新增 API 一律以 UUID 为玩家参数 ✗
     *                    不用 {@code Player}）—— 这与"仅在线"不冲突 ✓：**解析不到角色实例即回 {@code null}** ✓
     * @param componentId 组件在实例容器里的登记 id（如 {@code energy}）；**多实例消歧写在 id 字符串里** ✓ ——
     *                    形如 {@code energy#2}（{@code #} 后是 **0 基**下标 ✓）；同 id 命中**多份**而**未给**下标
     *                    ⇒ **拒绝并回 {@code null}** ✓（**绝不静默取第一份** ✗）
     * @param payload     **整段**操作文本（**可含空格** ✓）；{@code null} / 空串的语义由组件自行定义 ✓
     * @return {@code null} = 未识别 / 被拒绝 / **无角色实例** / 组件**命中 0 份**或（同 id）**多份而未给下标** /
     *         {@code componentId} 语法不合法 / 目标组件**未实现** {@code OperationProvider} / **组件内部异常** ✓；
     *         **非空串** = 规范化值（读操作回值 · 写操作回"写后状态" ✓）；
     *         {@code ""} = 已识别但**没有回值**（纯写操作 ✓）
     */
    String executeComponentOperation(UUID uuid, String componentId, String payload);
}
