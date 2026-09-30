package com.shadowHunterRolesPlugin.api;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;


public interface RoleAPI {

    //不建议使用所有以Player类型作参数的api

    //通过特殊设置的pdc查询最后攻击者
    UUID getLastDamagerUuid(LivingEntity player);

    //查询一个角色id是否存在，即这个角色是否被实现
    boolean isValidRoleId(String id);

    //设置角色
    boolean setPlayerRole(UUID uuid, String roleId);

    boolean clearPlayerRole(UUID uuid);

    //角色查询
    String getPlayerRoleId(UUID uuid);

    Component getPlayerRoleDisplayName(UUID uuid);

    boolean hasRole(UUID uuid);

    //角色描述查询
    Component getRoleDisplayName(String roleId);
    List<Component> getRoleDescription(String roleId);
    Material getRoleIcon(String roleId);

    //角色清单（既有方法签名一律未动）
    //下游用这两个方法自行发现"有哪些角色"，而不是 import 内部类去读注册表
    Set<String> getAllRoleIds();
    List<RoleInfo> getRoles();

    //阵营信息
    //阵营真值在聚合根（core/Role），因此本接口不暴露阵营的读/写方法：
    //   读 = {@link RoleInfo#faction()}（角色只读快照，唯一读入口；{@code getRoles()} 回的就是它）
    //   写 = 只在聚合根上（{@code Role#setFaction} / {@code Role#resetFaction}），不由外部 API 直改

    /**
     * 两个玩家是否敌对（阵营关系查询）。
     *
     * <p>判定链 = {@code RoleManager} → 平台关系表（{@code FactionLookup}），
     * 关系表同时看"在场"与"阵营"两个维度，因此阵营真值只从聚合根读。
     * <p>语义：任一方**没有角色**（含实例缺失）⇒ {@code true}；
     * 任一方**不在场**（创造 / 旁观模式）⇒ {@code false}；
     * 双方都有角色时，阵营不同 ⇒ {@code true}、同阵营 ⇒ {@code false}。
     *
     * @return 两个 uuid 为 {@code null} 则 {@code false}
     */
    boolean areHostile(UUID p1, UUID p2);

    //组件操作面 —— 唯一的操作角色入口
    /**
     * 执行一条组件操作：唯一的"操作角色"入口 —— 读与写都走它（写操作回"写后状态"、读操作回值本身）。
     * <p><b>grammar 由组件自己规定</b>：payload 的首 token 必为操作动词（如 {@code add 5} / {@code current}），
     * 其余部分由目标组件自解析，具体动词表见实现它的组件的 javadoc（如能量组件）。
     * <p><b>与 {@code OperationProvider} 的关系</b>：本方法只做「解析实例 → 按 id 定位组件 → 转发」；
     * 目标组件未实现 {@code OperationProvider} 则回 {@code null}（不支持操作指令）。
     *
     * @param uuid        目标玩家。参数类型用 UUID（此后新增 API 一律以 UUID 为玩家参数，
     *                    不用 {@code Player}）—— 这与"仅在线"不冲突：解析不到角色实例即回 {@code null}
     * @param componentId 组件在实例容器里的登记 id（如 {@code energy}）；多实例消歧写在 id 字符串里 ——
     *                    形如 {@code energy#2}（{@code #} 后是 0 基下标）；同 id 命中多份而未给下标
     *                    则拒绝并回 {@code null}（绝不静默取第一份）
     * @param payload     整段操作文本（可含空格）；{@code null} / 空串的语义由组件自行定义
     * @return {@code null} = 未识别 / 被拒绝 / 无角色实例 / 组件命中 0 份或（同 id）多份而未给下标 /
     *         {@code componentId} 语法不合法 / 目标组件未实现 {@code OperationProvider} / 组件内部异常；
     *         非空串 = 规范化值（读操作回值 · 写操作回"写后状态"）；
     *         {@code ""} = 已识别但没有回值（纯写操作）
     */
    String executeComponentOperation(UUID uuid, String componentId, String payload);
}
