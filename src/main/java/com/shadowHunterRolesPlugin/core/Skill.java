package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.shadowHunterRolesPlugin.core.hotbar.HotbarItemProviding;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.hotbar.IconState;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;

import java.util.ArrayList;
import java.util.List;


/**
 * 技能组件基类（阶段 4 B0b-2 起改基到 {@link ActiveComponent}）。
 * <p>
 * <b>阶段 8</b>：本类**给出技能侧默认画法** {@link #buildItem()} —— 快捷栏物品完全由组件控制，
 * 框架只负责"什么时候写"和"写到哪一格"。默认画法的合成（冻结面，值一字不变，只是搬家到这里）：
 * 三态材质 → 名称着色/加粗 → 后缀（技能冷却名带 {@code " x.xs"}，这是与主武器的**冻结差异**）
 * → 状态行 lore → 分隔线 + 描述 → **最后一步**写识别键 {@link Utils#SKILL_KEY}。
 */
public abstract class Skill extends ActiveComponent implements HotbarItemProviding {

    /**
     * 状态行与描述之间的分隔线（冻结字面量，值一字不变）。本类与 {@link MainWeapon} 各持一份
     * ⇒ 两份都落在**组件基类的默认实现**里，渲染器内 0 处（归属判据 C-13）。
     */
    private static final String LORE_SEPARATOR = "====================";

    /**
     * 阶段 4 收尾批⑤：首位两参 `(id, ComponentServices)` 为**构造期注入**（id 由注册处声明、服务集由容器注入）。
     * <p>
     * **描述符口径的构造**（阶段 7 · B 步）：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事。
     */
    public Skill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 旧构造口径（表现参数内联）：**保留为兼容别名** —— 与迁移前逐字同序同义。
     *
     * @deprecated 改用 `(id, services, Specification)`：表现值写进组件自己的嵌套 `Specification`。
     */
    @Deprecated
    public Skill(String id, ComponentServices services, Component displayName, Component description, int cooldown, int energyCost, Material icon){
        super(id, services, HotbarSpecification.of("Skill", id, displayName, description, icon, cooldown, energyCost));
    }

    /**
     * **技能描述符**（阶段 7 · A 步骨架、**阶段 8 收敛为纯声明**）：带栏位
     * （继承 {@link HotbarSpecification} ⇒ 有 {@code setSlot}），kind 已删（不再自述种类）。
     * <p>参数顺序 = 本类构造器去掉前两位（`id` / `services`）后的**原样顺序** ⇒ 阶段 7 · B 步的迁移是机械可对拍的：
     * 把构造实参从子类构造器**原样粘贴**进它自己的嵌套 `Specification` 即可。
     * <p>与主武器的规则差异（阶段 7 拍板"规则进类型"）：
     * <ul>
     *   <li>技能**可以**有非零能量消耗（`energyCost` 是本类型的构造参数）；</li>
     *   <li>{@code setSlot} 由本类型提供（技能占热键栏）。</li>
     * </ul>
     * 本类型**不实现** {@link #create(String, ComponentServices)} ⇒ 具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制，不会漏）。
     */
    public abstract static class Specification extends HotbarSpecification<Skill> {

        /** 声明式构造（推荐）：id 属于注册处，不写进组件描述符。 */
        protected Specification(Component displayName, Component description, int cooldownTicks,
                                int energyCost, Material icon){
            this(null, displayName, description, cooldownTicks, energyCost, icon);
        }

        /** 带 id 的构造（表现面需要 id 时用；{@code null} = 由注册处给出）。 */
        protected Specification(String id, Component displayName, Component description, int cooldownTicks,
                                int energyCost, Material icon){
            super("Skill", id, displayName, description, icon, cooldownTicks, energyCost);
        }

        /** 具体组件必须给出创建逻辑（保留抽象 ⇒ 漏写是**编译错误**，不是运行期惊喜）。 */
        @Override
        public abstract Skill create(String id, ComponentServices services);
    }

    /**
     * **技能侧默认画法**（阶段 8）：组件侧自判状态、产出**完整已装饰**的热键栏物品。
     * <p>序列（冻结，顺序不可交换）：
     * <ol>
     *   <li>读**声明数据**（取自描述符）：基础物品的材质 / 显示名 / 描述、{@code energyCost}；</li>
     *   <li>判状态（{@link IconState#of}，顺序 = 冷却 → 禁用 → 能量不足 → 就绪）；</li>
     *   <li>施加**三态材质**（{@link IconState#material(Material)}；就绪态沿用基础物品材质）；</li>
     *   <li>名称着色 / 加粗 + 后缀（冷却态 = {@code " x.xs"} 秒数，**全仓唯一一处秒数格式串**）；</li>
     *   <li>状态行 lore + 分隔线 + 描述（能量不足态**没有**状态行 —— 既有形态）；</li>
     *   <li><b>最后一步</b>写识别键 {@link Utils#SKILL_KEY}（值 = 本组件的注册 id）。</li>
     * </ol>
     * <p><b>覆写者须知（用户裁定：键与文案均允许覆写，覆写者自负其责）</b>：本方法整体可覆写。
     * 覆写后若**键写错**（与 {@code SkillListener} 闸门读的键不一致）⇒ 点击该物品**无任何反应**；
     * 若**键缺失** ⇒ 角色清除时 {@code RoleInstance.clearHotbar()} 扫不到它 ⇒ **物品残留**在背包里。
     * 详见 {@link HotbarItemProviding} 的接口 javadoc。
     */
    /**
     * **技能家族的默认画法带 {@code x.xs} 秒数 ⇒ 外观依赖活状态**（阶段 8 · t46 / A8）：
     * 覆写为 {@code true} ⇒ 只要本组件在冷却中，框架就每 tick 至少刷一次，秒数才会逐刻递减。
     * <p>这是**能力自报**、不是框架点名具体类：覆写掉秒数外观的子类（见
     * {@code roleComponent/ExampleSelfRefreshingSkill}）可以把它覆写回 {@code false} ⇒ **不再每 tick 重绘**。
     */
    @Override
    public boolean dependsOnLiveState() {
        return true;
    }

    @Override
    public ItemStack buildItem() {
        //① 声明数据（全部取自描述符；基础物品可由组件覆写 baseItem 自行给出）
        String id = getId();
        ItemStack base = baseItem(id);
        ItemMeta baseMeta = base.getItemMeta();
        Material baseMaterial = base.getType();
        Component baseName = baseMeta != null && baseMeta.displayName() != null
                ? baseMeta.displayName() : getDisplayName();
        List<Component> baseLore = baseMeta != null && baseMeta.lore() != null && !baseMeta.lore().isEmpty()
                ? baseMeta.lore() : List.of(getDescription());

        //② 状态判定（读运行期状态：冷却表 / 闸门 / 当前能量 —— 描述符拿不到这些）
        IconState state = IconState.of(
                svc().cooldowns().isReady(),
                svc().buffs().canCastSkill(),
                svc().energy().current(),
                getEnergyCost());

        //③ 三态材质：就绪 = 基础物品材质；禁用 = BARRIER；冷却 / 能量不足 = STRUCTURE_VOID
        ItemStack stack = new ItemStack(state.material(baseMaterial));
        ItemMeta meta = stack.getItemMeta();

        List<Component> lore = new ArrayList<>();
        //④⑤ 名称（着色 + 加粗 + 后缀）与状态行
        switch (state) {
            case COOLDOWN -> {
                meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                        .append(Component.text(" " + String.format("%.1f", svc().cooldowns().remainingTicks() / 20f) + "s")
                                .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)));
                lore.add(Component.text("Skill is on cooldown."));
            }
            case DISABLED -> {
                meta.displayName(baseName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .append(Component.text(" DISABLED")).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
                lore.add(Component.text("Skill has been disabled."));
            }
            case ENERGY_LACK -> meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                    .append(Component.text(" ENERGY LACK")).color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
            case READY -> {
                meta.displayName(baseName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                lore.add(Component.text("Skill is ready."));
            }
        }

        //⑤ 分隔线 + 描述：对**所有**状态都追加（含"能量不足态只有这两行"这条非对称）
        lore.add(Component.text(LORE_SEPARATOR));
        lore.addAll(baseLore);
        meta.lore(lore);

        //⑥ 最后一步：写识别键（写入点唯一；键名与值语义是冻结面）
        meta.getPersistentDataContainer().set(Utils.SKILL_KEY, PersistentDataType.STRING, id);

        stack.setItemMeta(meta);
        return stack;
    }

    //技能物品识别工具（键名 / 读取面，冻结面；渲染器不再持有它们）
    public static class Utils{

        public static final NamespacedKey SKILL_KEY = KeyFactory.Registry.of(
                "skill_id"
        );

        public static boolean isSkillItem(ItemStack item){
            if(item == null || item.getType().isAir()) return false;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return false;

            return meta.getPersistentDataContainer().has(SKILL_KEY, PersistentDataType.STRING);
        }

        public static String getSkillId(ItemStack item){
            if(item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return null;

            PersistentDataContainer container = meta.getPersistentDataContainer();
            if(!container.has(SKILL_KEY, PersistentDataType.STRING)) return null;
            return container.get(SKILL_KEY, PersistentDataType.STRING);
        }

    }

}
