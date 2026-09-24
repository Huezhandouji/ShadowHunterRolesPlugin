package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;


import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.roleComponent.ComponentDependencyException;
import com.shadowHunterRolesPlugin.roleComponent.ComponentFactory;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.EnergyComponent;
//阶段 13 · t123：`frameworkLevel.FactionComponent` 的 **未使用 import 已删除** ✗ —— 该组件本体
//已随 faction 迁移收尾整体删除（阵营的真值 = 本类的 `faction` 字段 ⇒ 见下方 getFaction/setFaction/resetFaction）。
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.TimerComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;


public class Role {

    private final String id;
    private final Component displayName;
    private final List<Component> description;

    private final double maxHP;
    private final double baseATK;
    private final int maxEnergy;
    private final int maxSanTE;

    /**
     * **唯一有序组件表**（阶段 6）：`LinkedHashMap` ⇒ **声明顺序 = 装配调用顺序**，
     * 它就是生命周期/事件广播的**派发序载体**（纯注册序；不再是"技能 → 被动 → 主武器"三段序）。
     * <p>阶段 8：条目里的"种类"由**描述符类型**表达（旧的 `kind 枚举` 已删）——
     * 行为分支不再读任何"种类"值。
     */
    private final Map<String, ComponentEntry> components;
    /**
     * 三个按**描述符类型**过滤的**有序** id 视图（组内保持声明序；旧的公共访问器语义不变）：
     * 技能 = {@link Skill.Specification} 一支 · 被动 = {@link PassiveSkill.Specification} · 主武器 =
     * {@link MainWeapon.Specification}。
     */
    private final Set<String> skillIds;
    private final Set<String> passiveIds;
    private final Set<String> mainWeaponIds;
    /**
     * **栏位视图（派生）**（阶段 7 · B 步）：栏位归属**不再由本类维护** —— 它随组件自己的描述符走
     * （装配点只写 {@code setSlot}）。本表是构造期**一次性从组件表派生**出来的只读视图，
     * 只为公开 API {@link #getSlotMap()} 保留（签名与语义不变）。
     */
    private final Map<Integer, String> slotMap;

    private Faction faction;

    /**
     * **角色模板声明的阵营**（阶段 13 · t123）：构造期由描述符给出、**此后只读** ⇒ 它就是
     * {@link #resetFaction()} 的回落目标 ✓。
     * <p>与 {@link #faction}（可变、{@code setFaction} 的写入点）分开持有是**必需**的：旧口径下
     * 回落目标住在**每实例**的阵营组件（原 `frameworkLevel/FactionComponent`，阶段 13 · t123 起已删除 ✗）
     * 的默认阵营字段里（构造期取 {@code role.getFaction()}）
     * ⇒ 若只保留一个可变字段，"复位"会变成"把当前值写回自己"的**空操作**，与旧行为不等价 ✗。
     */
    private final Faction defaultFaction;

    private final Material icon;

    //私有构造方法，需要通过内部构建器创建实例
    private Role(Builder builder){

        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.maxHP = builder.maxHP;
        this.baseATK = builder.baseATK;
        this.maxEnergy = builder.maxEnergy;
        this.maxSanTE = builder.maxSanTE;
        this.faction = builder.faction;
        //回落目标与可变值同源起步（builder.faction 由 Builder#faction 保证非 null
        //⇒ 与旧 FactionComponent 的 `faction = defaultFaction` 逐字一致 ✓）
        this.defaultFaction = builder.faction;

        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(builder.components));
        this.skillIds = Collections.unmodifiableSet(filterIds(this.components, Skill.Specification.class));
        this.passiveIds = Collections.unmodifiableSet(filterIds(this.components, PassiveSkill.Specification.class));
        this.mainWeaponIds = Collections.unmodifiableSet(filterIds(this.components, MainWeapon.Specification.class));
        this.slotMap = Collections.unmodifiableMap(deriveSlotMap(this.components));

        this.icon = builder.icon;

    }

    /**
     * **装配期一次性派生栏位视图**（阶段 7 · B 步）：遍历组件表，把**占栏位**的条目收成 `栏位 → id`。
     * <p>遍历顺序 = 注册序 ⇒ 同一栏位不可能出现两次（装配期已校验），派生结果与旧实现写入的那张表逐项相同。
     */
    private static Map<Integer, String> deriveSlotMap(Map<String, ComponentEntry> components){
        Map<Integer, String> derived = new HashMap<>();
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            ComponentEntry component = entry.getValue();
            if(component.hasSlot()){
                derived.put(component.getSlot(), entry.getKey());
            }
        }
        return derived;
    }

    /**
     * 按**描述符类型**过滤出**保持声明序**的 id 视图（`LinkedHashSet`）。
     * <p>阶段 8：旧口径是"按权威 kind 过滤"（`kind 枚举` 已删）；新口径 = **描述符类型**——
     * 技能/主武器来自带栏位描述符的两个家族支，被动来自 {@code PassiveSkill.Specification}
     * （装配入口 {@code addPassive} 按工厂类型归到它）。仓内读数逐条相同。
     */
    private static Set<String> filterIds(Map<String, ComponentEntry> components, Class<?> descriptorType){
        Set<String> ids = new LinkedHashSet<>();
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            if(descriptorType.isAssignableFrom(entry.getValue().getDescriptorType())){
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    public RoleInstance createInstance(Player player, RolesContext context){
        //阶段 10 · t54（A2 · 检查时机）：依赖检查必须发生在 **任何实例化/awake 之前** ——
        // 这里是"造实例"的唯一入口（{@code manager/RoleManager#selectRole} 与测试探针都走它）⇒
        // 在这里再查一次，任何路径都不可能绕过检查进到 {@code awake()}。
        // 幂等：{@code registry/RoleLoader#loadInto} 在注册前已经查过一次（不注册的模板根本到不了这里）。
        verifyDependencies();
        return new RoleInstance(player, this, context);
    }

    // ───────────── 阶段 10 · t54：装配期依赖检查（用户计划第三条） ─────────────

    /**
     * **装配期依赖检查**（唯一实现点）：**必需依赖必须齐**。
     * <p><b>时机</b>（A2）：由调用方在 {@code Role.build()} **之后**、**任何 {@code awake()} 之前**调用 ——
     * 框架里有两处：{@code registry/RoleLoader#loadInto}（**注册之前** ⇒ 坏模板根本不进注册表）与
     * {@link #createInstance(Player, RolesContext)}（**实例化之前** ⇒ 任何路径都绕不过去）。
     * <p><b>失败形态</b>（硬失败，不降级）：抛 {@link ComponentDependencyException}；调用方
     * {@code RoleLoader} 记一条 {@code SEVERE} 并**跳过该角色**（其余角色继续装配）。
     * <p><b>匹配规则（写死）</b>：组件 A 的必需类型 R 被满足 ⟺ 存在**另一个**组件 B（B 的 id ≠ A 的 id）
     * 使 {@code R.isAssignableFrom(B.providedType())}。**A 自己不算提供者** —— 用户原话是"检查自己需要的
     * 依赖（**其他组件**）"，因此"只有自己提供该类型"按**缺依赖**处理。
     * <p><b>为什么不用反射去"扫"组件实例</b>：装配期**还没有任何实例**（实例化发生在
     * {@code RoleInstance} 的构造期）⇒ 检查只能基于描述符声明的类型，这也是它能在"注册之前"完成的原因。
     * <p><b>不检查什么（如实申报）</b>：可选依赖缺失不报错；提供类型是**族级**的组件（三个家族描述符的
     * 泛型实参是家族基类）无法满足"按具体类"的依赖声明，除非该描述符覆写
     * {@code providedType()}（见 {@code RoleComponent.Specification#providedType()}）。
     * <p><b>阶段 10 · t68 取代指向（不静默改写）</b>：本方法**曾**同时检查"依赖图不得有环"
     * （{@code t54} 实现，依据当时的裁定 Q2「禁止依赖循环」）。用户新路线图第三条**明确改为"允许组件环形
     * 依赖"** ⇒ 环检测的**硬失败已删除**（{@code dependencyCycles()} 与其 DFS 一并移除）。
     * **只放开环，不动另一半**：「缺必需依赖 ⇒ 抛异常 + 阻止该角色加载注册」**原样保留**（新路线图没有
     * 推翻它）。因此本方法的失败面**只有一种**：缺必需依赖。
     * <p><b>运行期初始化顺序（A3）</b>：环存在时，组件 {@code awake()} 的调用顺序 = **容器按插入序**，
     * 与依赖图**无关** ⇒ 环内"谁先醒"**未定义**（本工程**不承诺**任何依赖驱动或拓扑序）。
     * 这是**显式申报**，不是遗漏：任何依赖"环内某组件先于另一个 awake"的写法都是**靠巧合**，不得依赖。
     *
     * @throws ComponentDependencyException 缺必需依赖（消息里点名角色 / 组件 id / 缺的类型）
     */
    public void verifyDependencies(){
        List<String> problems = new ArrayList<>(missingRequiredDependencies());
        if(!problems.isEmpty()){
            throw new ComponentDependencyException(
                    "Role '" + id + "' failed the assembly-time dependency check: " + String.join(" | ", problems));
        }
    }

    // ───────────── 阶段 10 · t69：框架必然提供的服务类型（白名单） ─────────────

    /**
     * <b>「框架必然提供的服务类型」白名单</b>（阶段 10 · t69 · 用户新路线图第 12 / 13 行）。
     *
     * <p><b>它解决什么问题</b>（{@code t63} 已申报的缺口）：{@link #verifyDependencies()} 只看
     * **模板的组件表**；而**框架级服务组件**是**按角色实例**装配的（每实例一个，见
     * {@code core/RoleInstance} 的 `registerServiceComponents()`）⇒ 模板侧永远看不见它们。
     * 于是任何组件写 {@code requires(EnergyComponent.class)} 这类声明都会被误报成"缺必需依赖"
     * ⇒ 该角色**被错误地拒绝注册** ✗。
     *
     * <p><b>语义（写死）</b>：列在本集合里的类型 = **框架保证在角色实例上必然提供**的类型
     * ⇒ 声明它们的组件**不算缺依赖**（检查放行）。它**只影响"供给面"的判定**，不影响任何运行期行为：
     * 组件仍按 {@code RoleComponent#getComponent(Class)} 自己去取（取不到是另一回事，属运行期）。
     *
     * <p><b>清单 = 7 个框架级服务组件</b>（与 {@code ComponentServices} 的 8 个端口成员同族；
     * 它们是**每角色实例一份**的框架服务，不是角色内容）：能量 / SanTE / 生命 / buff / 计时 /
     * 阵营 / 伤害。它们**不进 `Role` 模板**（模板组件表逐格不变），因此只能由本白名单在模板侧豁免。
     *
     * <p><b>两侧分工（本卡的验收口径）</b>：
     * <ul>
     *   <li><b>模板侧</b> = 本白名单：让 {@code requires(这些类型)} **能通过**装配期检查（否则误拒注册）；</li>
     *   <li><b>实例侧</b> = 框架真的提供了它们：组件在 {@code awake()} 里
     *       {@code getComponent(EnergyComponent.class)} 等**取得到**（同一份实例）。</li>
     * </ul>
     * 两条都给了运行级读数（见交付说明 §4），缺一条就可能是"白名单放行了但实例上根本没有"的假绿 ✗。
     *
     * <p><b>边界（如实申报，不静默放宽）</b>：白名单**只**列这 **5** 个框架级服务组件（阶段 13 · t121：`FactionComponent` 条目已移除 ⇒ **6 → 5** ✓，与 faction 迁移收尾同趟 ✓）；
     * （技能 / 被动 / 主武器）一律不在此列 ⇒ 它们之间的依赖声明照旧按模板组件表判定。
     */
    public static final Set<Class<? extends RoleComponent>> FRAMEWORK_PROVIDED_TYPES = Set.of(
            EnergyComponent.class,
            SanTEComponent.class,
            VitalsComponent.class,
            BuffComponent.class,
            TimerComponent.class);

    /**
     * **缺必需依赖的清单**（诊断用；空 = 齐）。每条都点名：组件 id · 该组件**提供**的类型 · **缺**的类型。
     * <p>{@link #verifyDependencies()} 的异常消息直接由它拼出 ⇒ 消息与清单**同源**，不会各说一套。
     * <p><b>阶段 10 · t69（A3 白名单）</b>：列在 {@link #FRAMEWORK_PROVIDED_TYPES} 里的类型
     * （= 框架必然按实例提供的 **5** 个服务组件 —— 阶段 13 · t121 起 faction 不在其中 ✓）**不算缺** ⇒ 跳过。这是"框架级服务组件在模板里看不见"
     * 这个缺口的唯一修法。
     */
    public List<String> missingRequiredDependencies(){
        List<String> problems = new ArrayList<>();
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            String componentId = entry.getKey();
            ComponentEntry component = entry.getValue();
            for(Class<? extends RoleComponent> required : component.getRequiredTypes()){
                //框架必然提供的服务类型（按实例装配、不进模板）⇒ 声明它不算缺依赖（见 FRAMEWORK_PROVIDED_TYPES）
                if(FRAMEWORK_PROVIDED_TYPES.contains(required)) continue;
                if(!hasProviderOtherThan(componentId, required)){
                    problems.add("component '" + componentId + "' (provides " + component.getProvidedType().getName()
                            + ") requires missing component type '" + required.getName() + "'");
                }
            }
        }
        return problems;
    }

    /** 是否存在**另一个**组件提供该类型（自己不算；见 {@link #verifyDependencies()} 的匹配规则）。 */
    private boolean hasProviderOtherThan(String requesterId, Class<? extends RoleComponent> required){
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            if(entry.getKey().equals(requesterId)) continue;
            if(required.isAssignableFrom(entry.getValue().getProvidedType())) return true;
        }
        return false;
    }

    // ───────────── 阶段 10 · t68：环检测已按用户新路线图第三条删除 ─────────────
    //
    // 这里**曾**有 `dependencyCycles()` 与它的 DFS 辅助 `walkForCycles()`（t54 实现，依据当时的
    // 裁定 Q2「禁止依赖循环」）。用户新路线图第三条**改为「允许组件环形依赖」** ⇒ 两者**整段删除**，
    // 不留死代码（保留一个不再被调用的环检测只会让下一个读者以为环仍被禁止）。
    //
    // 删除后**不变**的东西（边界，防止误读）：
    //   * 自环（A 的某个必需类型由 A 自己提供）：A **不算自己的提供者** ⇒ 仍落成**缺依赖**硬失败。
    //     这是匹配规则的一部分，**不是**环检测的残留 —— 删环检测**没有**放松它。
    //   * 互环（A↔B）与更长的环：装配**通过**（这正是本卡要的）。
    //   * 运行期 awake() 顺序 = 容器按插入序，与依赖图无关 ⇒ 环内"谁先醒"**未定义**（见
    //     {@link #verifyDependencies()} 的 A3 段）。

    /** 旧窄类型入口（保留兼容）：kind 不符时返回 {@code null}（与"该类型未装配"同义）。 */
    public Skill createSkill(String skillId, ComponentServices services){
        RoleComponent component = createComponent(skillId, services);
        return component instanceof Skill skill ? skill : null;
    }

    /** 旧窄类型入口（保留兼容）：见 {@link #createSkill(String, ComponentServices)}。 */
    public PassiveSkill createPassive(String passiveId, ComponentServices services){
        RoleComponent component = createComponent(passiveId, services);
        return component instanceof PassiveSkill passive ? passive : null;
    }

    /** 旧窄类型入口（保留兼容）：见 {@link #createSkill(String, ComponentServices)}。 */
    public MainWeapon createMainWeapon(String weaponId, ComponentServices services){
        RoleComponent component = createComponent(weaponId, services);
        return component instanceof MainWeapon weapon ? weapon : null;
    }

    /**
     * **唯一组件创建点**（阶段 6 起对全部 kind 生效）：全仓**创建路径**只有这里调用
     * {@link ComponentFactory#create(String, ComponentServices)}；装配表按 id 查条目的工厂。
     * <p>服务集**在构造期**交给组件：组件返回时即已持有它，容器随后立刻登记。
     * 阶段 8：**没有任何"种类"值**需要传给组件（旧的权威 kind 已随 kind 枚举删除，
     * 服务集构造也不再需要它）。
     */
    public RoleComponent createComponent(String id, ComponentServices services){
        ComponentEntry entry = components.get(id);
        if(entry == null) return null;
        return entry.getFactory().create(id, services);
    }

    public Material getIcon(){
        return icon;
    }

    /**
     * 装配条目：`(工厂, 栏位?, 描述符类型, 提供类型, 必需依赖, 可选依赖)`（阶段 7 · A 步；**阶段 8 去掉 kind**；
     * **阶段 10 · t54 增加依赖声明**）。
     * <p><b>不占栏位 = 栏位的缺失</b>：栏位用**可空的 {@link Integer}** 表达（`null` = 不占热键栏，
     * 不进 `slotMap` ⇒ 渲染器遍历 `slotMap` 时天然看不到它）。
     * 旧版的 `-1` 哨兵已删除 —— {@link #getSlot()} 在无栏位时**抛异常**，而不是返回一个能参与算术的值；
     * 想表达"不占栏位"只剩一条路：装配一个**没有栏位**的描述符（如 `PassiveSkill.Specification`）。
     * <p>本条目是装配期从描述符取到的**不可变快照**：只持有几个值，**不持有描述符对象** ⇒
     * 同一份描述符实例被两个角色共享时，后手改动影响不到先手。
     * <p>阶段 8：`descriptorType` = 描述符的**类型**（旧 `kind` 的唯一职责改由它承担：三个 id 视图按
     * 类型归类）；它**不参与任何行为分支**。
     * <p>阶段 10 · t54：`providedType` / `requiredTypes` / `optionalTypes` 是**依赖检查的三元组** ——
     * 提供类型是"我能被谁依赖"，必需/可选是"我依赖谁"。它们只被
     * {@link Role#verifyDependencies()} 读取（**不参与任何运行期行为分支**）。
     */
    public static final class ComponentEntry{

        private final ComponentFactory<? extends RoleComponent> factory;
        private final Integer slot;
        private final Class<?> descriptorType;
        /** 本组件**提供**的类型（依赖检查的供给面；无描述符的装配入口按工厂形参类型给族级值）。 */
        private final Class<? extends RoleComponent> providedType;
        /** **必需**依赖类型（缺任一 ⇒ {@link Role#verifyDependencies()} 抛异常）。 */
        private final List<Class<? extends RoleComponent>> requiredTypes;
        /** **可选**依赖类型（缺失不报错）。 */
        private final List<Class<? extends RoleComponent>> optionalTypes;

        ComponentEntry(ComponentFactory<? extends RoleComponent> factory, Integer slot, Class<?> descriptorType,
                       Class<? extends RoleComponent> providedType,
                       List<Class<? extends RoleComponent>> requiredTypes,
                       List<Class<? extends RoleComponent>> optionalTypes){
            this.factory = factory;
            this.slot = slot;
            this.descriptorType = descriptorType;
            this.providedType = providedType;
            this.requiredTypes = List.copyOf(requiredTypes);
            this.optionalTypes = List.copyOf(optionalTypes);
        }

        public ComponentFactory<? extends RoleComponent> getFactory() { return factory; }

        /** **描述符类型**（旧 kind 的唯一职责承担者：仅供三个 id 视图归类，不参与行为分支）。 */
        public Class<?> getDescriptorType() { return descriptorType; }

        /** 本组件**提供**的类型（依赖检查按它匹配：`required.isAssignableFrom(provided)`）。 */
        public Class<? extends RoleComponent> getProvidedType() { return providedType; }

        /** 本组件声明的**必需**依赖类型（不可变副本）。 */
        public List<Class<? extends RoleComponent>> getRequiredTypes() { return requiredTypes; }

        /** 本组件声明的**可选**依赖类型（不可变副本）。 */
        public List<Class<? extends RoleComponent>> getOptionalTypes() { return optionalTypes; }

        /** 栏位（0..8）；**不占栏位 ⇒ 抛异常**（本版不再有 `-1` 哨兵）。 */
        public int getSlot() {
            if (slot == null) {
                throw new IllegalStateException("Component does not occupy a hotbar slot.");
            }
            return slot;
        }

        /** 占不占热键栏（`false` ⇒ 不进 `slotMap`）。 */
        public boolean hasSlot() { return slot != null; }
    }

    public static class Builder{

        private final String id;
        private Component displayName;
        //O-11：默认空表，addLineOfDescription 在 description(...) 之前调用时不再 NPE
        private List<Component> description = new ArrayList<>();
        private double maxHP = 20d;
        private double baseATK = 10d;
        private int maxEnergy = 100;
        private int maxSanTE = 100;
        private Faction faction = Faction.UNKNOWN;

        /** 唯一有序组件表（声明序 = 装配调用序）—— 阶段 6 的派发序载体，也是**栏位的唯一来源**（阶段 7 · B 步）。 */
        private final Map<String, ComponentEntry> components = new LinkedHashMap<>();

        private Material icon;

        public Builder(String id){
            if(id == null || id.trim().isEmpty()){
                throw new IllegalArgumentException("Role ID cannot be null or empty.");
            }
            this.id = id;
        }

        public Builder displayName(Component displayName){
            this.displayName = displayName;
            return this;
        }

        public Builder description(List<Component> description) {
            //归一为可变列表，避免传入不可变列表后 addLineOfDescription 抛 UnsupportedOperationException
            this.description = description != null ? new ArrayList<>(description) : new ArrayList<>();
            return this;
        }

        public Builder addLineOfDescription(Component description){
            this.description.add(description);
            return this;
        }

        public Builder maxHP(double maxHP) {
            if (maxHP <= 0) {
                throw new IllegalArgumentException("Role maxHP cannot be negative.");
            }
            this.maxHP = maxHP;
            return this;
        }

        public Builder baseATK(double baseATK) {
            if (baseATK < 0) {
                throw new IllegalArgumentException("Role baseATK cannot be negative.");
            }
            this.baseATK = baseATK;
            return this;
        }

        public Builder maxEnergy(int maxEnergy) {
            if (maxEnergy < 0) {
                throw new IllegalArgumentException("Role maxEnergy cannot be negative.");
            }
            this.maxEnergy = maxEnergy;
            return this;
        }

        public Builder maxSanTE(int maxSanTE) {
            if (maxSanTE < 0) {
                throw new IllegalArgumentException("Role maxSanTE cannot be negative.");
            }
            this.maxSanTE = maxSanTE;
            return this;
        }

        public Builder faction(Faction faction) {
            this.faction = faction != null ? faction : Faction.UNKNOWN;
            return this;
        }

        /**
         * **统一装配入口（描述符口径，阶段 7 · A 步新增；阶段 8 去掉 kind）**：吃一个**装配期描述符**
         * （{@link com.shadowHunterRolesPlugin.roleComponent.RoleComponent.Specification}），
         * 栏位从描述符读，**不再由调用点传值**；也不再有任何"种类"形参。
         * <p>占不占栏位由**描述符的类型**决定：带栏位的描述符（`HotbarSpecification` 一支）用
         * {@code setSlot} 指定位置，装配期未设栏位 ⇒ 此处抛异常；不带栏位的描述符
         * （`PassiveSkill.Specification`）**没有** {@code setSlot} ⇒ 天然不占栏位。
         * <p>本方法对传入描述符取**不可变快照**（{@code Specification#freeze()}）：条目只留
         * `(栏位, 工厂, 描述符类型, 提供类型, 必需依赖, 可选依赖)`，**不持有描述符对象**。
         * <p>阶段 10 · t54：描述符上的依赖声明（{@code requires(...)} / {@code requiresOptional(...)}）与
         * **提供类型**（{@code providedType()}）随快照进入条目，供 {@link Role#verifyDependencies()} 在装配期检查。
         */
        public Builder addComponent(String id, RoleComponent.Specification<?> specification){
            Objects.requireNonNull(specification);
            //阶段 7 · C 步（A7 选 (a)）：**把注册 id 绑进描述符**再冻结 —— 组件自带的描述符用
            //"不带 id 的构造"声明，不绑定的话描述符里的 id 字段会恒为 null（t34 第一轮的真实回归根因）。
            specification.bindId(id);
            RoleComponent.Specification.Snapshot snapshot = specification.freeze();
            return addComponentInternal(id, specification.getClass(),
                    snapshot.hasSlot() ? Integer.valueOf(snapshot.getSlot()) : null,
                    snapshot.getFactory(), snapshot.getDescriptorLabel(),
                    snapshot.getProvidedType(), snapshot.getRequiredTypes(), snapshot.getOptionalTypes());
        }

        /**
         * **无栏位装配入口**（阶段 8 起为被动唯一的入口；旧的 kind 形参已随 `kind 枚举` 删除）：
         * 不占热键栏 ⇒ 不进 `slotMap` ⇒ 渲染器遍历时天然看不到它，也不会被要求画物品。
         * <p>"是被动"由**工厂形参的类型**表达（{@code ComponentFactory<PassiveSkill>}）⇒
         * 三个 id 视图按 `PassiveSkill.Specification` 归类；其余语义与描述符入口一致（同一条内部路径）。
         * <p><b>阶段 10 · t54 的依赖面（如实申报）</b>：本入口**没有描述符** ⇒
         * ① 提供类型只能是**族级** {@code PassiveSkill.class}（依赖检查按它匹配）；
         * ② **无法**声明依赖（{@code requires} 只在描述符上）。要按具体类被依赖或要声明依赖的被动，
         * 应改走描述符入口（给该被动加一个嵌套 {@code PassiveSkill.Specification}）—— 属逐组件迁移卡的范围。
         */
        public Builder addPassive(String passiveId, ComponentFactory<PassiveSkill> factory){
            Objects.requireNonNull(factory);
            return addComponentInternal(passiveId, PassiveSkill.Specification.class, null, factory, "Passive",
                    PassiveSkill.class, List.of(), List.of());
        }

        /**
         * **唯一内部装配路径**（阶段 6 立、阶段 7 · A 步改为"栏位可有可无"、B 步改为"栏位随组件走"、
         * **阶段 8 去掉 kind**、**阶段 10 · t54 增加依赖三元组**）：描述符入口与无栏位入口都只调用这里
         * ⇒ 校验、id 去重、入表各只有一处实现。
         * <p>本类**不再维护栏位表**：栏位只作为条目的一个值存在（{@code ComponentEntry.slot}），
         * 角色构造期再一次性派生出 {@code slotMap} 视图。
         *
         * @param slot           栏位；{@code null} = **不占栏位**（不占热键栏）——旧版用 {@code -1} 哨兵表达同一件事
         * @param descriptorType 描述符**类型**（旧 kind 的唯一职责承担者：三个 id 视图按它归类）
         * @param descriptorLabel 诊断标签（只用于重复 id 的异常文案，与迁移前逐字相同）
         * @param providedType   本组件**提供**的类型（依赖检查的供给面）
         * @param requiredTypes  **必需**依赖类型（缺任一 ⇒ {@link Role#verifyDependencies()} 抛异常）
         * @param optionalTypes  **可选**依赖类型（缺失不报错）
         */
        private Builder addComponentInternal(String id, Class<?> descriptorType, Integer slot,
                                             ComponentFactory<? extends RoleComponent> factory,
                                             String descriptorLabel,
                                             Class<? extends RoleComponent> providedType,
                                             List<Class<? extends RoleComponent>> requiredTypes,
                                             List<Class<? extends RoleComponent>> optionalTypes){
            // 与迁移前临时实例取 id 的 fail-fast 等价：null 工厂在**装配期**立刻 NPE，而不是拖到实例创建
            Objects.requireNonNull(factory);

            if(id == null || id.trim().isEmpty()){
                throw new IllegalArgumentException("Component ID cannot be null or empty.");
            }
            if(descriptorType == null){
                throw new IllegalArgumentException("Component descriptor type cannot be null.");
            }
            if(providedType == null){
                throw new IllegalArgumentException("Component provided type cannot be null.");
            }
            ensureIdNotRegistered(descriptorLabel, id);

            if(slot != null){
                validateSlot(slot);
            }

            components.put(id, new ComponentEntry(factory, slot, descriptorType, providedType,
                    requiredTypes, optionalTypes));

            return this;
        }

        //设置这个角色的图标, 便于游戏逻辑插件自动化读取
        public Builder icon(Material icon){
            this.icon = icon;
            return this;
        }

        //O-10：id 去重必须是跨类型的 —— 技能/被动/主武器共用同一个 id 命名空间，任一重复都抛异常
        private void ensureIdNotRegistered(String descriptorLabel, String id){
            if (components.containsKey(id)) {
                throw new IllegalArgumentException(descriptorLabel + " already registered: " + id);
            }
        }

        /**
         * O-12：槽位冲突 fail-fast（§10 裁决 1）—— 抛异常、该角色不注册，不再"告警 + 覆盖"。
         * <p>阶段 7 · B 步：冲突判定改为**扫组件表里已占栏位的条目**（本类不再另存栏位表），
         * 异常类型与文案**逐字不变**。
         */
        private void validateSlot(int slot){
            if(slot < 0 || slot > 8){
                throw new IllegalArgumentException("Slot must be between 0 and 8, got: " + slot);
            }
            for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
                ComponentEntry registered = entry.getValue();
                if(registered.hasSlot() && registered.getSlot() == slot){
                    throw new IllegalArgumentException("Slot " + slot + " is already occupied by '" + entry.getKey() + "'.");
                }
            }
        }

        public Role build(){
            if(displayName == null) displayName = Component.text(id);
            //空表仍给占位文案，保持与原 build() 兜底一致的可见输出
            if(description.isEmpty()) description = new ArrayList<>(List.of(Component.text("No description yet.")));

            return new Role(this);
        }

    }




    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public List<Component> getDescription() { return description; }
    public double getMaxHP() { return maxHP; }
    public double getBaseATK() { return baseATK; }
    public int getMaxEnergy() { return maxEnergy; }
    public int getMaxSanTE() { return maxSanTE; }
    public Set<String> getSkillIds(){
        return skillIds;
    }
    public Set<String> getPassiveSkillIds() {
        return passiveIds;
    }
    public Set<String> getMainWeaponIds(){
        return mainWeaponIds;
    }

    /**
     * **唯一有序组件表**（阶段 6）：遍历顺序 = 装配调用顺序 ⇒ 生命周期/事件广播的派发序。
     * 容器的组件初始化只遍历本表**一次**（纯注册序）。
     */
    public Map<String, ComponentEntry> getComponents(){
        return components;
    }

    /**
     * 按 id 取**描述符类型**（阶段 8）：仅供"三个 id 视图的归类"等装配期用途；
     * 组件的**行为分支不再读任何种类**（旧的 {@code componentKindOf} 已随 kind 枚举删除，
     * 它的两个消费者改为按**能力接口**判定：{@code roleComponent.RoleComponent.CooldownBearing} / 占栏位组件）。
     * 未注册 ⇒ {@code null}。
     */
    public Class<?> descriptorTypeOf(String id){
        ComponentEntry entry = components.get(id);
        return entry != null ? entry.getDescriptorType() : null;
    }

    public Faction getFaction() { return faction; }

    /**
     * **设置本角色的阵营**（阶段 13 · t121：faction 迁移收尾的前一半，重建 t96/t90-2 ✓）。
     * <p>① **管理级 / 模板级**语义：这是**角色模板**上的声明值，不是每玩家状态 ✗；
     * ② 影响**该角色的所有实例**（已实例化的玩家实例下一次经 `RoleInfo#faction()` 读取时即生效 ✓）；
     * ③ 阵营的**读取唯一入口仍是 `roleInfo` 服务面** ✓ ⇒ 外部不直改、组件不直读（R-6 ✓）。
     * <p><b>【已作废】旧口径原文</b>（阶段 10 · t90 原文，逐字保留）：「faction 为 final ⇒ 仅构造期由描述符写入」✗。
     * <p><b>阶段 13 · t123（欠账 A 后半）</b>：本方法即旧
     * `frameworkLevel/FactionComponent#setFaction` 的**唯一接替落点** —— 该组件已整体删除 ✗，
     * 写侧经 `RoleInstance#setFaction` 转调到本方法 ✓；读侧一律走 `roleInfo` 服务面（不读本字段的裸值）✓。
     */
    public void setFaction(Faction faction){ this.faction = faction; }

    /**
     * **复位为角色模板声明的阵营**（阶段 13 · t123：`RoleAPI#resetFaction` 的落点 ✓）。
     * <p>语义 = 旧 `frameworkLevel/FactionComponent#reset()` **逐字等价**（当时写作
     * {@code this.faction = defaultFaction;}）✓ —— 回落目标就是构造期由描述符给出的
     * {@link #defaultFaction}（只读），因此连续复位是幂等的 ✓。
     * <p>旧口径的差异只有一处：回落目标从**每实例组件字段**搬到**角色模板字段** ⇒ 同一角色的实例
     * 共享同一回落目标 ✓（阵营本就"一个角色一份、全局静态"）。
     * <p><b>【已作废】旧口径原文</b>（阶段 10 · t90 原文，逐字保留）：复位落点在
     * `FactionComponent#reset()`、真值在组件里 ✗。
     * <p><b>后续（本卡不改）</b>：`api/RoleAPI` 的第三片把 `setFaction` / `resetFaction` 两条
     * 改为空实现后，`RoleInstance` 上的两个**写视图**届时可一并删除 ✓（读侧已于本卡删除 ✗）。
     */
    public void resetFaction(){ this.faction = defaultFaction; }

    /**
     * 栏位视图（**派生**，阶段 7 · B 步）：`栏位 → 组件 id`，由构造期一次性从组件表派生。
     * 签名与语义与迁移前**完全一致**（公开 API，只增不改）；栏位归属本身随组件自己的描述符走。
     */
    public Map<Integer, String> getSlotMap() { return slotMap; }

    /**
     * 按栏位取组件 id（**新增**，阶段 7 · B 步）：走与渲染器**同一趟**组件表遍历
     * （`栏位 → id` 的唯一来源是条目里的栏位值），未占用 ⇒ {@code null}。
     * <p>给 {@code DebugCooldownCommand} 的"纯数字 = 热键栏槽位"解析用，避免它去读第二套栏位表。
     */
    public String componentIdAtSlot(int slot){
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            ComponentEntry component = entry.getValue();
            if(component.hasSlot() && component.getSlot() == slot){
                return entry.getKey();
            }
        }
        return null;
    }
}

