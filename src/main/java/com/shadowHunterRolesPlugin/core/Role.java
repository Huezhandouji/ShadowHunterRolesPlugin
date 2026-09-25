package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;


import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.roleComponent.ComponentDependencyException;
import com.shadowHunterRolesPlugin.roleComponent.ComponentFactory;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
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

 /**
 * **唯一有序组件表**：`LinkedHashMap` ⇒ **声明顺序 = 装配调用顺序**，
 * 它就是生命周期/事件广播的**派发序载体**（纯注册序；不再是"技能 → 被动 → 主武器"三段序）。
 * <p>条目里的"种类"由**描述符类型**表达（`kind 枚举` 已删）——
 * 行为分支不再读任何"种类"值。
 */
    private final Map<String, ComponentEntry> components;
 /**
 * 三个按**描述符类型**过滤的**有序** id 视图（组内保持声明序；公共访问器语义不变）：
 * 技能 = {@link Skill.Specification} 一支 · 被动 = **无栏位的那一支** · 主武器 =
 * {@link MainWeapon.Specification}。
 */
    private final Set<String> skillIds;
    private final Set<String> passiveIds;
    private final Set<String> mainWeaponIds;

 //★ **栏位视图已整体删除**（`slotMap` / `deriveSlotMap` / `getSlotMap()` / `componentIdAtSlot()`）——
 // 「物品栏位置」的持有者是**渲染组件**（它自己读描述符的 `slot()` 做落位），聚合根不再持有派生视图。
 // 条目仍携带栏位值（`ComponentEntry.slot`）—— 那是**数据**（描述符快照的一部分），不是本类的视图。

    private Faction faction;

 /**
 * **角色模板声明的阵营**：构造期由描述符给出、**此后只读** ⇒ 它就是
 * {@link #resetFaction()} 的回落目标。
 * <p>与 {@link #faction}（可变、{@code setFaction} 的写入点）分开持有是**必需**的：既有口径下
 * 回落目标住在**每实例**的阵营组件（原阵营组件，已整体删除）
 * 的默认阵营字段里（构造期取 {@code role.getFaction()}）
 * ⇒ 若只保留一个可变字段，"复位"会变成"把当前值写回自己"的**空操作**，与旧行为不等价。
 */
    private final Faction defaultFaction;

    private final Material icon;

 //私有构造方法，需要通过内部构建器创建实例
    private Role(Builder builder){

        this.id = builder.id;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.faction = builder.faction;
 //回落目标与可变值同源起步（builder.faction 由 Builder#faction 保证非 null
 //⇒ 与旧 FactionComponent 的 `faction = defaultFaction` 逐字一致）
        this.defaultFaction = builder.faction;

        this.components = Collections.unmodifiableMap(new LinkedHashMap<>(builder.components));
        this.skillIds = Collections.unmodifiableSet(filterIds(this.components, Skill.Specification.class));
        this.passiveIds = Collections.unmodifiableSet(filterIds(this.components, PassiveSkill.Specification.class));
        this.mainWeaponIds = Collections.unmodifiableSet(filterIds(this.components, MainWeapon.Specification.class));
 //★ 栏位视图已删除 ⇒ 构造期不再派生 slotMap

        this.icon = builder.icon;

 //★ 「已被提供的类型」注入机制已删除（6 件内建组件已进模板 ⇒ 供给面判定只看模板组件表）

    }

 /**
 * **装配期一次性派生栏位视图**：遍历组件表，把**占栏位**的条目收成 `栏位 → id`。
 * <p>遍历顺序 = 注册序 ⇒ 同一栏位不可能出现两次（装配期已校验），派生结果与既有实现写入的那张表逐项相同。
 */
 /**
 * 按**描述符类型**过滤出**保持声明序**的 id 视图（`LinkedHashSet`）。
 * <p>早先口径是"按权威 kind 过滤"（`kind 枚举` 已删）；现口径 = **描述符类型**——
 * 技能/主武器来自带栏位描述符的两个家族支，被动来自**无栏位的被动描述符支**
 * （装配入口 = 统一的 {@code addComponent}；归类只按描述符类型的**可赋值性**）。仓内读数逐条相同。
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
 //（A2 · 检查时机）：依赖检查必须发生在 **任何实例化/awake 之前** ——
 // 这里是"造实例"的唯一入口（{@code manager/RoleManager#selectRole} 与测试探针都走它）⇒
 // 在这里再查一次，任何路径都不可能绕过检查进到 {@code awake()}。
 // 幂等：{@code registry/RoleLoader#loadInto} 在注册前已经查过一次（不注册的模板根本到不了这里）。
        verifyDependencies();
        return new RoleInstance(player, this, context);
    }

 // ─────────────：装配期依赖检查（用户计划第三条） ─────────────

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
 * <p><b>取代指向（不静默改写）</b>：本方法**曾**同时检查"依赖图不得有环"
 *。**明确改为"允许组件环形
 * 依赖"** ⇒ 环检测的**硬失败已删除**（{@code dependencyCycles()} 与其 DFS 一并移除）。
 * **只放开环，不动另一半**：「缺必需依赖 ⇒ 抛异常 + 阻止该角色加载注册」**原样保留**。
 * 因此本方法的失败面**只有一种**：缺必需依赖。
 * <p><b>运行期初始化顺序（A3）</b>：环存在时，组件 {@code awake()} 的调用顺序 = **容器按插入序**，
 * 与依赖图**无关** ⇒ 环内"谁先醒"**未定义**（本工程**不承诺**任何依赖驱动或拓扑序）。
 * 这是**显式申报**，不是遗漏：任何依赖"环内某组件先于另一个 awake"的写法都是**靠巧合**，不得依赖。
 * @throws ComponentDependencyException 缺必需依赖（消息里点名角色 / 组件 id / 缺的类型）
 */
    public void verifyDependencies(){
        List<String> problems = new ArrayList<>(missingRequiredDependencies());
        if(!problems.isEmpty()){
            throw new ComponentDependencyException(
                    "Role '" + id + "' failed the assembly-time dependency check: " + String.join(" | ", problems));
        }
    }

 //★ **「已被提供的类型」豁免机制已整体删除** —— 它原本是「内建组件按实例装配、模板里看不见」
 // 那个缺口的补丁；现在 6 件内建组件**已注册进模板**（`registry/RoleLoader#withBuiltIns`）
 // ⇒ `requires(...)` 的供给面判定只看**模板组件表**即可，不再需要任何外部清单 ✓。

 /**
 * **缺必需依赖的清单**（诊断用；空 = 齐）。每条都点名：组件 id · 该组件**提供**的类型 · **缺**的类型。
 * <p>{@link #verifyDependencies()} 的异常消息直接由它拼出 ⇒ 消息与清单**同源**，不会各说一套。
 */
    public List<String> missingRequiredDependencies(){
        List<String> problems = new ArrayList<>();
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            String componentId = entry.getKey();
            ComponentEntry component = entry.getValue();
            for(Class<? extends RoleComponent> required : component.getRequiredTypes()){
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

 //
 // 删除后**不变**的东西（边界，防止误读）：
 // * 自环（A 的某个必需类型由 A 自己提供）：A **不算自己的提供者** ⇒ 仍落成**缺依赖**硬失败。
 // 这是匹配规则的一部分，**不是**环检测的残留 —— 删环检测**没有**放松它。
 // * 互环（A↔B）与更长的环：装配**通过**（这正是预期）。
 // * 运行期 awake() 顺序 = 容器按插入序，与依赖图无关 ⇒ 环内"谁先醒"**未定义**（见
 // {@link #verifyDependencies()} 的 A3 段）。

 /**
 * **唯一组件创建点**：全仓**创建路径**只有这里调用
 * {@link ComponentFactory#create(String, ComponentServices)}；装配表按 id 查条目的工厂。
 * <p>服务集**在构造期**交给组件：组件返回时即已持有它，容器随后立刻登记。
 * **没有任何"种类"值**需要传给组件（权威 kind 已随 kind 枚举删除，
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
 * 装配条目：`(工厂, 栏位?, 描述符类型, 提供类型, 必需依赖, 可选依赖)`（**不含 kind**；
 * ** 增加依赖声明**）。
 * <p><b>不占栏位 = 栏位的缺失</b>：栏位由**描述符**表达（带栏位的描述符必须 {@code setSlot}；
 * 无栏位的描述符天然不占）⇒ 渲染组件取计划时天然看不到无栏位者。
 * 想表达"不占栏位"只有一条路：装配一个**没有栏位**的描述符（如**无栏位的被动描述符支**）。
 * <p>★ **本条目不再持有栏位值** —— 栏位只住在描述符快照里（`Specification.Snapshot#getSlot()`），
 * 渲染组件读那一份做落位 ✓。
 * <p>本条目是装配期从描述符取到的**不可变快照**：只持有几个值，**不持有描述符对象** ⇒
 * 同一份描述符实例被两个角色共享时，后手改动影响不到先手。
 * <p>`descriptorType` = 描述符的**类型**（原 `kind` 的唯一职责改由它承担：三个 id 视图按
 * 类型归类）；它**不参与任何行为分支**。
 * <p>：`providedType` / `requiredTypes` / `optionalTypes` 是**依赖检查的三元组** ——
 * 提供类型是"我能被谁依赖"，必需/可选是"我依赖谁"。它们只被
 * {@link Role#verifyDependencies()} 读取（**不参与任何运行期行为分支**）。
 */
    public static final class ComponentEntry{

        private final ComponentFactory<? extends RoleComponent> factory;
        private final Class<?> descriptorType;
 /** 本组件**提供**的类型（依赖检查的供给面；无描述符的装配入口按工厂形参类型给族级值）。 */
        private final Class<? extends RoleComponent> providedType;
 /** **必需**依赖类型（缺任一 ⇒ {@link Role#verifyDependencies()} 抛异常）。 */
        private final List<Class<? extends RoleComponent>> requiredTypes;
 /** **可选**依赖类型（缺失不报错）。 */
        private final List<Class<? extends RoleComponent>> optionalTypes;

        ComponentEntry(ComponentFactory<? extends RoleComponent> factory, Class<?> descriptorType,
                       Class<? extends RoleComponent> providedType,
                       List<Class<? extends RoleComponent>> requiredTypes,
                       List<Class<? extends RoleComponent>> optionalTypes){
            this.factory = factory;
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

 //★ `getSlot()` / `hasSlot()` 已删除 —— 栏位值**只**住在描述符快照里
 // （`Specification.Snapshot#getSlot()`），渲染组件读那一份做落位 ⇒ 条目不再重复持有它 ✓
    }

    public static class Builder{

        private final String id;
        private Component displayName;
 // 默认空表，addLineOfDescription 在 description(...) 之前调用时不再 NPE
        private List<Component> description = new ArrayList<>();
        private Faction faction = Faction.UNKNOWN;

 /** 唯一有序组件表（声明序 = 装配调用序）—— 派发序载体，也是**栏位的唯一来源**。 */
        private final Map<String, ComponentEntry> components = new LinkedHashMap<>();

        /** 已占用的栏位（★ 只在**装配期**用于冲突判定；不进条目、不进 Role 实例）。 */
        private final Set<Integer> occupiedSlots = new LinkedHashSet<>();


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

        public Builder faction(Faction faction) {
            this.faction = faction != null ? faction : Faction.UNKNOWN;
            return this;
        }

 //★ `Builder#providedTypes(...)` 已删除 —— 见类内「豁免机制已整体删除」的说明。

 /**
 * **统一装配入口（描述符口径，不含 kind）**：吃一个**装配期描述符**
 * （{@link com.shadowHunterRolesPlugin.roleComponent.RoleComponent.Specification}），
 * 栏位从描述符读，**不再由调用点传值**；也不再有任何"种类"形参。
 * <p>占不占栏位由**描述符的类型**决定：带栏位的描述符（`HotbarSpecification` 一支）用
 * {@code setSlot} 指定位置，装配期未设栏位 ⇒ 此处抛异常；不带栏位的描述符
 * （**无栏位的被动描述符**）**没有** {@code setSlot} ⇒ 天然不占栏位。
 * <p>本方法对传入描述符取**不可变快照**（{@code Specification#freeze()}）：条目只留
 * `(栏位, 工厂, 描述符类型, 提供类型, 必需依赖, 可选依赖)`，**不持有描述符对象**。
 * <p>：描述符上的依赖声明（{@code requires(...)} / {@code requiresOptional(...)}）与
 * **提供类型**（{@code providedType()}）随快照进入条目，供 {@link Role#verifyDependencies()} 在装配期检查。
 */
        public Builder addComponent(String id, RoleComponent.Specification<?> specification){
            Objects.requireNonNull(specification);
 //**把注册 id 绑进描述符**再冻结 —— 组件自带的描述符用
 //"不带 id 的构造"声明，不绑定的话描述符里的 id 字段会恒为 null。
            specification.bindId(id);
            RoleComponent.Specification.Snapshot snapshot = specification.freeze();
 //★ 栏位值**不进条目** —— 它住在描述符快照里（渲染组件读那一份做落位）；
 // 但**装配期仍做冲突判定**（fail-fast 语义与文案逐字不变）：扫的是快照值，不是条目的字段。
            if (snapshot.hasSlot()) {
                int slot = snapshot.getSlot();
                if (slot < 0 || slot > 8) {
                    throw new IllegalArgumentException("Slot must be between 0 and 8, got: " + slot);
                }
                for (Map.Entry<String, ComponentEntry> registered : components.entrySet()) {
                    if (occupiedSlots.contains(slot)) {
                        throw new IllegalArgumentException(
                                "Slot " + slot + " is already occupied by '" + registered.getKey() + "'.");
                    }
                }
                occupiedSlots.add(slot);
            }
            return addComponentInternal(id, specification.getClass(),
                    snapshot.getFactory(), snapshot.getDescriptorLabel(),
                    snapshot.getProvidedType(), snapshot.getRequiredTypes(), snapshot.getOptionalTypes());
        }

 /**
 * **唯一内部装配路径**（栏位可有可无、"栏位随组件走"、
 * **不含 kind**、**增加依赖三元组**）：描述符入口与无栏位入口都只调用这里
 * ⇒ 校验、id 去重、入表各只有一处实现。
 * <p>本类**不持有任何栏位数据**：栏位冲突判定在 `addComponent(...)` 里用**描述符快照的值**完成，
 * 落位由渲染组件读描述符完成 ✓。
 * @param descriptorType 描述符**类型**（旧 kind 的唯一职责承担者：三个 id 视图按它归类）
 * @param descriptorLabel 诊断标签（只用于重复 id 的异常文案，逐字相同）
 * @param providedType 本组件**提供**的类型（依赖检查的供给面）
 * @param requiredTypes **必需**依赖类型（缺任一 ⇒ {@link Role#verifyDependencies()} 抛异常）
 * @param optionalTypes **可选**依赖类型（缺失不报错）
 */
        private Builder addComponentInternal(String id, Class<?> descriptorType,
                                             ComponentFactory<? extends RoleComponent> factory,
                                             String descriptorLabel,
                                             Class<? extends RoleComponent> providedType,
                                             List<Class<? extends RoleComponent>> requiredTypes,
                                             List<Class<? extends RoleComponent>> optionalTypes){
 // 与临时实例取 id 的 fail-fast 等价：null 工厂在**装配期**立刻 NPE，而不是拖到实例创建
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

            components.put(id, new ComponentEntry(factory, descriptorType, providedType,
                    requiredTypes, optionalTypes));

            return this;
        }

 //设置这个角色的图标, 便于游戏逻辑插件自动化读取
        public Builder icon(Material icon){
            this.icon = icon;
            return this;
        }

 // id 去重必须是跨类型的 —— 技能/被动/主武器共用同一个 id 命名空间，任一重复都抛异常
        private void ensureIdNotRegistered(String descriptorLabel, String id){
            if (components.containsKey(id)) {
                throw new IllegalArgumentException(descriptorLabel + " already registered: " + id);
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
 * **唯一有序组件表**：遍历顺序 = 装配调用顺序 ⇒ 生命周期/事件广播的派发序。
 * 容器的组件初始化只遍历本表**一次**（纯注册序）。
 */
    public Map<String, ComponentEntry> getComponents(){
        return components;
    }

 /**
 * 按 id 取**描述符类型**：仅供"三个 id 视图的归类"等装配期用途；
 * 组件的**行为分支不再读任何种类**（{@code componentKindOf} 已随 kind 枚举删除，
 * 它的两个消费者改为按**组件类型**判定：{@code roleComponent.ActiveComponent} / 占栏位组件）。
 * 未注册 ⇒ {@code null}。
 */
    public Class<?> descriptorTypeOf(String id){
        ComponentEntry entry = components.get(id);
        return entry != null ? entry.getDescriptorType() : null;
    }

    public Faction getFaction() { return faction; }

 /**
 * **设置本角色的阵营**（faction 迁移收尾的前一半，重建 /-2）。
 * <p>① **管理级 / 模板级**语义：这是**角色模板**上的声明值，不是每玩家状态；
 * ② 影响**该角色的所有实例**（已实例化的玩家实例下一次经 `RoleInfo#faction()` 读取时即生效）；
 * ③ 阵营的**读取唯一入口仍是 `roleInfo` 服务面** ⇒ 外部不直改、组件不直读。
 * <p><b>（欠账 A 后半）</b>：本方法即旧
 * 旧阵营组件的 `setFaction` 的**唯一接替落点** —— 该组件已整体删除，
 * 写侧经 `RoleInstance#setFaction` 转调到本方法；读侧一律走 `roleInfo` 服务面（不读本字段的裸值）。
 */
    public void setFaction(Faction faction){ this.faction = faction; }

 /**
 * **复位为角色模板声明的阵营**（`RoleAPI#resetFaction` 的落点）。
 * <p>语义 = 旧阵营组件的 `reset()` **逐字等价**（当时写作
 * {@code this.faction = defaultFaction;}） —— 回落目标就是构造期由描述符给出的
 * {@link #defaultFaction}（只读），因此连续复位是幂等的。
 * <p>差异只有一处：回落目标从**每实例组件字段**搬到**角色模板字段** ⇒ 同一角色的实例
 * 共享同一回落目标 （阵营本就"一个角色一份、全局静态"）。
 * `FactionComponent#reset()`、真值在组件里。
 * <p><b>后续</b>：`api/RoleAPI` 把 `setFaction` / `resetFaction` 两条
 * 改为空实现后，`RoleInstance` 上的两个**写视图**可一并删除（读侧已删除）。
 */
    public void resetFaction(){ this.faction = defaultFaction; }

 //★ **栏位视图与按槽位反查已整体删除**（`getSlotMap()` / `componentIdAtSlot(int)`）——
 // 「物品栏位置」的持有者是**渲染组件**：它自己读描述符的 `slot()` 做落位，
 // 数字目标解析也由它提供（`HotbarRenderComponent.identityOf(int)`）。
}

