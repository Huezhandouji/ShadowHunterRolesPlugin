package com.shadowHunterRolesPlugin.roleComponent.base;

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

import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.IconState;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;

import java.util.ArrayList;
import java.util.List;


/**
 * 技能组件基类（继承 {@link ActiveComponent}）。
 * <p>
 * 本类**给出技能侧默认画法** {@link #buildItem()} —— 快捷栏物品完全由组件控制，
 * 框架只负责"什么时候写"和"写到哪一格"。默认画法的合成（冻结面，值一字不变，只是搬家到这里）：
 * 三态材质 → 名称着色/加粗 → 后缀（技能冷却名带 {@code " x.xs"}，这是与主武器的**冻结差异**）
 * → 状态行 lore → 分隔线 + 描述 → **最后一步**写识别键 {@link Utils#SKILL_KEY}。
 */
public abstract class Skill extends ActiveComponent {

    // ───────── 基类**不持** buff / energy、**不查容器**、**不做该项判断** ─────────
    //用户原话：「基类不需要存 buff 和 energy 字段。这些应该由子类判断。」
    //⇒ 原先本类的两个**按需取用入口**（那两个私有方法）已**整体删除** ✗；
    //  可用性判定的两项输入改由**各子类用自己的字段**给出 ✓（下面两个抽象钩子）。
    //★ 与 `ActiveComponent` 的分工不变：冷却那半仍由父类自持（`isCoolingDown()`）；本类只把
    //  「闸门 / 当前能量」这两项**下放**，而判定的**顺序与语义**仍唯一落在状态枚举的静态工厂里 ✓。

    /**
     * **现在允许使用吗？**（**下放给子类**）—— 基类不查任何组件 ✗。
     * <p>子类用**自己的 buff 字段**回答（技能侧 = `canCastSkill()`：非 STUN 且非 SILENCE）。
     * <p>★ 语义 = 三态判定里的「**禁用**」那一维：返回 `false` ⇒ 图标变红屏障（DISABLED）。
     * <p><b>为什么是抽象</b>：三态里的「禁用」完全由本值决定 ⇒ 若给默认值，漏写者会
     * **静默**丢掉"被沉默 / 眩晕时灰显"的可见行为 ✗（本仓口径：漏写要成为**编译错误**，不是运行期惊喜）。
     */
    protected abstract boolean canUse();

    /**
     * **当前能量**（**下放给子类**）—— 基类不查任何组件 ✗。
     * <p>子类用**自己的 energy 字段**回答；**声明耗能 ≤ 0 的组件不参与能量维度** ⇒ 直接返回
     * {@link #getEnergyCost()}（"恰好够"）：与既有读法**逐字等价** —— 它读的是组件的 `current()`，
     * 而该值在组件内被 clamp 到 `[0, max]`（构造期 = max）⇒ `current() < 0` 恒假 ✓。
     * <p>同理**抽象**：声明耗能 &gt; 0 的组件若漏写，会静默丢掉「能量不足」态 ✗。
     */
    protected abstract int currentEnergy();

    /**
     * 状态行与描述之间的分隔线（冻结字面量，值一字不变）。本类与 {@link MainWeapon} 各持一份
     * ⇒ 两份都落在**组件基类的默认实现**里，渲染器内 0 处。
     */
    private static final String LORE_SEPARATOR = "====================";

    /**
     * 首位两参 `(id, ComponentServices)` 为**构造期注入**（id 由注册处声明、服务集由容器注入）。
     * <p>
     * **描述符口径的构造**：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事。
     */
    public Skill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 表现参数内联的构造口径：**保留为兼容别名** —— 与既有口径逐字同序同义。
     *
     * @deprecated 改用 `(id, services, Specification)`：表现值写进组件自己的嵌套 `Specification`。
     */
    @Deprecated
    public Skill(String id, ComponentServices services, Component displayName, Component description, int cooldown, int energyCost, Material icon){
        super(id, services, HotbarSpecification.of("Skill", id, displayName, description, icon, cooldown, energyCost));
    }

    /**
     * **技能描述符**（收敛为纯声明）：带栏位
     * （继承 {@link HotbarSpecification} ⇒ 有 {@code setSlot}），kind 已删（不再自述种类）。
     * <p>参数顺序 = 本类构造器去掉前两位（`id` / `services`）后的**原样顺序** ⇒ 描述符化是机械可对拍的：
     * 把构造实参从子类构造器**原样粘贴**进它自己的嵌套 `Specification` 即可。
     * <p>与主武器的规则差异（"规则进类型"）：
     * <ul>
     *   <li>技能**可以**有非零能量消耗（`energyCost` 是本类型的构造参数）；</li>
     *   <li>{@code setSlot} 由本类型提供（技能占热键栏）。</li>
     * </ul>
     * 本类型**不实现** {@link #create(String, ComponentServices)} ⇒ 具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制，不会漏）。
     */
    // ───────── 冷却：**由本组件实例自持** ────────────────────────────
    //主武器 / 技能这两个组件**自己持有冷却和其判断**，并**写开启冷却 / 停止冷却方法供子类使用**；
    //**框架不参与**（本类不向框架登记任何冷却状态、不新增组件、不新增端口）。
    //冷却状态与 API 已**上提到 `ActiveComponent`**（两家族基类合一 ✓）——
    //  本类不再自带副本；`startCooldown()` / `startCooldown(int ticks)` / `stopCooldown()` /
    //  `isCoolingDown()` / `remainingCooldownTicks()` 均由父类提供 ✓。

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
     * **技能侧默认画法**：组件侧自判状态、产出**完整已装饰**的热键栏物品。
     * <p>序列（冻结，顺序不可交换）：
     * <ol>
     *   <li>读**声明数据**（取自描述符）：基础物品的材质 / 显示名 / 描述、{@code energyCost}；</li>
     *   <li>判状态（{@link IconState#of}，顺序 = 冷却 → 禁用 → 能量不足 → 就绪）；</li>
     *   <li>施加**三态材质**（{@link IconState#material(Material)}；就绪态沿用基础物品材质）；</li>
     *   <li>名称着色 / 加粗 + 后缀（冷却态 = {@code " x.xs"} 秒数，**全仓唯一一处秒数格式串**）；</li>
     *   <li>状态行 lore + 分隔线 + 描述（能量不足态**没有**状态行 —— 既有形态）；</li>
     *   <li><b>最后一步</b>写识别键 {@link Utils#SKILL_KEY}（值 = 本组件的注册 id）。</li>
     * </ol>
     * <p><b>覆写者须知（键与文案均允许覆写，覆写者自负其责）</b>：本方法整体可覆写。
     * 覆写后若**键写错**（与 {@code SkillListener} 闸门读的键不一致）⇒ 点击该物品**无任何反应**；
     * 若**键缺失** ⇒ 角色清除时 {@code HotbarItems.clearFrom()} 扫不到它 ⇒ **物品残留**在背包里。
     * 详见渲染组件 {@link HotbarRenderComponent#buildItemOf} 的读侧契约 javadoc。
     */
    /**
     * **技能家族的默认画法带 {@code x.xs} 秒数 ⇒ 外观依赖活状态**：
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

        //② 状态判定（读运行期状态：冷却 / 闸门 / 当前能量 —— 描述符拿不到这些）
        //后两项由**子类**给出（基类不查容器 ✗）；判定顺序与语义仍唯一在状态枚举的静态工厂里 ✓
        IconState state = IconState.of(
                !isCoolingDown(),
                canUse(),
                currentEnergy(),
                getEnergyCost());

        //③ 三态材质：就绪 = 基础物品材质；禁用 = BARRIER；冷却 / 能量不足 = STRUCTURE_VOID
        ItemStack stack = new ItemStack(state.material(baseMaterial));
        ItemMeta meta = stack.getItemMeta();

        List<Component> lore = new ArrayList<>();
        //④⑤ 名称（着色 + 加粗 + 后缀）与状态行
        switch (state) {
            case COOLDOWN -> {
                meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                        .append(Component.text(" " + String.format("%.1f", remainingCooldownTicks() / 20f) + "s")
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
