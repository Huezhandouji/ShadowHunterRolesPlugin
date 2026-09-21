package com.shadowHunterRolesPlugin.core.hotbar;

import org.bukkit.inventory.ItemStack;

/**
 * 「这个组件自己产出热键栏物品」的能力接口（阶段 8）：**唯一实现点是 {@link #buildItem()}**。
 * <p>
 * <b>分工（阶段 8 冻结）</b>：组件侧**自己判定状态**并产出**完整已装饰**的物品；框架侧只保留
 * <b>管道职责</b> —— <b>什么时候写</b>（脏标记 / 帧末 flush）与 <b>写到哪一格</b>
 * （装配条目里的栏位，见 {@code Role.ComponentEntry#getSlot()}）。
 * <p>
 * <b>为什么默认实现在组件基类而不是描述符</b>：画物品要读<b>运行期状态</b>（冷却剩余刻数 / 闸门 / 当前能量），
 * 而描述符（{@link HotbarSpecification}）是**装配期对象**、拿不到 {@code svc()} ⇒ 默认画法写在
 * {@code core/Skill} 与 {@code core/MainWeapon} 这两个**组件基类**里（合成指标：三态材质 · 六条状态文案 ·
 * 技能带 {@code x.xs} 而主武器不带 · 两个 PDC 键 · 声明数据取自描述符）。描述符只保留**声明数据**
 * （图标 / 显示名 / 描述 / 冷却 / 耗能）。
 * <p>
 * <b>能力簇（用户裁定 C-14：强依赖的能力应合并）</b>：本接口被 {@link HotbarPresentable} 收进热键栏能力簇
 * （{@code HotbarPresentable extends HotbarItem, CooldownBearing, EnergyCosting, HotbarItemProviding}）
 * ⇒ <b>实现者集合按构造相同</b>（凡"能出现在热键栏"的组件都必须给出 buildItem），且 {@code buildItem()}
 * 的语义**必须读**簇内状态（{@link CooldownBearing#isCooling()} / {@link EnergyCosting#getEnergyCost()} /
 * {@link HotbarItem} 的声明面）⇒ 两条合并判据同时成立。不占热键栏的组件（被动）**不在**本簇内，
 * 因此不会被强制实现一个永远不会被调用的方法。
 * <p>
 * <b>覆写者须知（用户裁定：PDC 键与六条文案**均允许组件覆写**，覆写者自负其责）</b>：
 * <ul>
 *   <li><b>键写错</b>（写入的键与框架读取的键不一致）⇒ 该物品在监听器前置闸门
 *       （{@code listener/SkillListener} 的 {@code isSkillItem} / {@code listener/MainWeaponListener} 的
 *       {@code isMainWeapon}）处不被识别 ⇒ <b>点击该物品无任何反应</b>（玩家感知为"技能坏了"）；</li>
 *   <li><b>键缺失</b>（完全不写键）⇒ 角色清除时 {@code RoleInstance.clearHotbar()} 扫不到它
 *       ⇒ <b>物品残留在背包/热键栏</b>（可继续拿在手上）。</li>
 * </ul>
 * 框架**不再**为此提供保障（不再"写完回读校验"）—— 这两条是**已申报的代价**，不是缺陷。
 */
public interface HotbarItemProviding {

    /**
     * 产出本组件在当前时刻的热键栏物品（**完整形态**：材质 / 名称 / 后缀 / lore / 识别键都已就位）。
     * <p>
     * 由框架在**帧末 flush** 的写物品段调用（每帧至多一次/组件），并与**注册序**同序遍历；
     * 组件**不得**在此方法里写玩家背包（写物品的唯一落点仍是 {@code HotbarRenderer#render()}）。
     */
    ItemStack buildItem();
}
