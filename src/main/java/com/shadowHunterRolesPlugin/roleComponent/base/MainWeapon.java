package com.shadowHunterRolesPlugin.roleComponent.base;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.hotbar.IconState;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;

import java.util.ArrayList;
import java.util.List;


/**
 * 主武器组件基类（继承 {@link ActiveComponent}；`energyCost` 恒传 0）。
 * <p>
 * 本类**给出主武器侧默认画法** {@link #buildItem()}。与技能侧的两条冻结差异：
 * <ul>
 *   <li>冷却名**不带** {@code " x.xs"} 秒数后缀（技能带）；</li>
 *   <li>{@code energyCost ≡ 0} 由类型封死 ⇒ {@link IconState#ENERGY_LACK} 对主武器**不可达**。</li>
 * </ul>
 * 识别键 = {@link Utils#MAIN_WEAPON_KEY}。
 * <p>原 `CombatHook`（唯一方法 {@link #onAttack(AttackSignal)}）**已被本类吸收**
 * —— 本类本来就声明 `onAttack`，那个接口只是重复声明 ⇒ 整体删除 ✗（派发按**本类**判，
 * 接受集逐字不变）；{@code AttackSignal} 随之成为 {@code ActiveComponent} 的嵌套类型 ✓。
 */
public abstract class MainWeapon extends ActiveComponent {

    // ───────── 基类**不持** buff / energy、**不查容器**、**不做该项判断** ─────────
    //用户原话：「基类不需要存 buff 和 energy 字段。这些应该由子类判断。」
    //⇒ 原先本类的两个按需取用入口已**整体删除** ✗（同 `Skill` 的那一段）。
    //★ 能量维度：主武器的声明耗能由类型**封死 ≡ 0**（{@link Specification} 没有 `setEnergyCost`）
    //  ⇒ 「能量不足」态对它**不可达**（冻结面口径）⇒ 本类**不设**能量钩子，判定直接传声明值 ✓。

    /**
     * **闸门是否放行？**（**下放给子类**）—— 基类不查任何组件 ✗。
     * <p>子类用**自己的 buff 字段**回答（主武器侧 = `canUseMainWeapon()`：非 STUN）。
     * <p><b>为什么是抽象</b>：「禁用」态完全由本值决定 ⇒ 若给默认值，漏写者会**静默**丢掉灰显 ✗。
     */
    protected abstract boolean gateOpen();

    /**
     * 状态行与描述之间的分隔线（冻结字面量，值一字不变）。本类与 {@link Skill} 各持一份
     * ⇒ 两份都落在**组件基类的默认实现**里，渲染器内 0 处。
     */
    private static final String LORE_SEPARATOR = "====================";

    /**
     * **描述符口径的构造**：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事（主武器的能量消耗由类型恒为 0）。
     */
    public MainWeapon(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 表现参数内联的构造口径：**保留为兼容别名** —— 与既有口径逐字同序同义（`energyCost` 仍恒传 0）。
     *
     * @deprecated 改用 `(id, services, Specification)`：表现值写进组件自己的嵌套 `Specification`。
     */
    @Deprecated
    public MainWeapon(String id, ComponentServices services, Component displayName, Component description, Material icon, int cooldown){
        super(id, services, HotbarSpecification.of("MainWeapon", id, displayName, description, icon, cooldown, 0));
    }

    /**
     * **主武器描述符**（收敛为纯声明）：带栏位
     * （继承 {@link HotbarSpecification} ⇒ 有 {@code setSlot}），kind 已删（不再自述种类）。
     * <p>参数顺序 = 本类构造器去掉前两位（`id` / `services`）后的**原样顺序**。
     * <p><b>规则进类型</b>：本类型**没有** `setEnergyCost` —— 能量消耗**根本不是参数**，
     * 在构造期以字面量 {@code 0} 交给父类 ⇒ **"主武器 `energyCost ≡ 0`"由类型封死**，
     * 不再是"装配点记得传 0"的自觉；`ENERGY LACK` 态因此对主武器**不可达**（冻结面口径不变）。
     * <p>本类型**不实现** {@link #create(String, ComponentServices)} ⇒ 具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制）。
     */
    // ───────── 冷却：**由本组件实例自持** ────────────────────────────
    //主武器 / 技能这两个组件**自己持有冷却和其判断**，并**写开启冷却 / 停止冷却方法供子类使用**；
    //**框架不参与**（本类不向框架登记任何冷却状态、不新增组件、不新增端口）。
    //冷却状态与 API 已**上提到 `ActiveComponent`**（两家族基类合一 ✓）——
    //  本类不再自带副本；`startCooldown()` / `startCooldown(int ticks)` / `stopCooldown()` /
    //  `isCoolingDown()` / `remainingCooldownTicks()` 均由父类提供 ✓。

    public abstract static class Specification extends HotbarSpecification<MainWeapon> {

        /** 声明式构造（推荐）：id 属于注册处，不写进组件描述符。 */
        protected Specification(Component displayName, Component description, Material icon, int cooldownTicks){
            this(null, displayName, description, icon, cooldownTicks);
        }

        /** 带 id 的构造（表现面需要 id 时用；{@code null} = 由注册处给出）。 */
        protected Specification(String id, Component displayName, Component description, Material icon,
                                int cooldownTicks){
            super("MainWeapon", id, displayName, description, icon, cooldownTicks, 0);
        }

        /** 具体组件必须给出创建逻辑（保留抽象 ⇒ 漏写是**编译错误**，不是运行期惊喜）。 */
        @Override
        public abstract MainWeapon create(String id, ComponentServices services);
    }

    /**
     * **物品使用入口（攻击）的契约**（返回 {@code void}）：listener 在攻击后**无条件**启动武器冷却。
     * <p>本方法从"覆写能力接口"变成**本类的声明**（原 `CombatHook` 被吸收 ✗）
     * ⇒ `@Override` 已删（它已无超类型方法可覆写）；签名与默认体**逐字未变** ✓。
     */
    public void onAttack(AttackSignal signal){
    }

    /**
     * **主武器侧默认画法**：组件侧自判状态、产出**完整已装饰**的热键栏物品。
     * <p>序列与技能侧同构（冻结，顺序不可交换）：声明数据 → 状态判定 → 三态材质 → 名称着色/加粗
     * → 后缀（**只有 ` DISABLED` / ` ENERGY LACK`，冷却态无秒数**）→ 状态行 lore + 分隔线 + 描述
     * → **最后一步**写识别键 {@link Utils#MAIN_WEAPON_KEY}（值 = 本组件的注册 id）。
     * <p><b>覆写者须知（键与文案均允许覆写，覆写者自负其责）</b>：本方法整体可覆写。
     * 覆写后若**键写错**（与 {@code MainWeaponListener} 闸门读的键不一致）⇒ 点击该物品**无任何反应**；
     * 若**键缺失** ⇒ 角色清除时 {@code HotbarItems.clearFrom()} 扫不到它 ⇒ **物品残留**在背包里。
     * 详见渲染组件 {@link HotbarRenderComponent#buildItemOf} 的读侧契约 javadoc。
     */
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

        //② 状态判定（读运行期状态）；主武器 energyCost ≡ 0 ⇒ ENERGY_LACK 不可达
        //闸门由**子类**给出（基类不查容器 ✗）；能量维不参与 ⇒ 传声明值（恒 0）
        IconState state = IconState.of(
                !isCoolingDown(),
                gateOpen(),
                getEnergyCost(),
                getEnergyCost());

        //③ 三态材质：就绪 = 基础物品材质；禁用 = BARRIER；冷却 = STRUCTURE_VOID
        ItemStack stack = new ItemStack(state.material(baseMaterial));
        ItemMeta meta = stack.getItemMeta();

        List<Component> lore = new ArrayList<>();
        //④⑤ 名称（着色 + 加粗 + 后缀）与状态行：**冷却名不带秒数**（与技能侧的冻结差异，不得"顺手统一"）
        switch (state) {
            case COOLDOWN -> {
                meta.displayName(baseName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
                lore.add(Component.text("MainWeapon is on cooldown."));
            }
            case DISABLED -> {
                meta.displayName(baseName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                        .append(Component.text(" DISABLED")).color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
                lore.add(Component.text("MainWeapon has been disabled."));
            }
            case READY -> {
                meta.displayName(baseName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
                lore.add(Component.text("MainWeapon is ready."));
            }
            case ENERGY_LACK -> {
                //不可达：主武器 energyCost ≡ 0 且能量被 clamp 到 ≥ 0 ⇒ 不为其造新外观
            }
        }

        //⑤ 分隔线 + 描述：对**所有**状态都追加
        lore.add(Component.text(LORE_SEPARATOR));
        lore.addAll(baseLore);
        meta.lore(lore);

        //⑥ 最后一步：写识别键（写入点唯一；键名与值语义是冻结面）
        meta.getPersistentDataContainer().set(Utils.MAIN_WEAPON_KEY, PersistentDataType.STRING, id);

        stack.setItemMeta(meta);
        return stack;
    }

    //主武器物品识别工具（键名 / 读取面，冻结面；渲染器不再持有它们）
    public static class Utils{

        public static final NamespacedKey MAIN_WEAPON_KEY = KeyFactory.Registry.of(
                "main_weapon_id"
        );

        public static boolean isMainWeapon(ItemStack item){
            if(item == null || item.getType().isAir()) return false;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return false;
            return meta.getPersistentDataContainer().has(MAIN_WEAPON_KEY, PersistentDataType.STRING);
        }

        public static String getWeaponId(ItemStack item){
            if(item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return null;
        return meta.getPersistentDataContainer().get(MAIN_WEAPON_KEY, PersistentDataType.STRING);
        }

    }

    //getters 已上移到 ActiveComponent（getId/getDisplayName/getDescription/getIcon/getCooldownTicks/getEnergyCost）
    //getKind() 已随 kind 枚举一并删除（表现面不再自述种类）。

}
