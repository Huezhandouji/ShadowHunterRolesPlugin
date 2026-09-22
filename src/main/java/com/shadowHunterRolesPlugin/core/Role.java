package com.shadowHunterRolesPlugin.core;


import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
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

    private final Faction faction;

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
     * **装配期依赖检查**（唯一实现点）：① **必需依赖必须齐**；② **依赖图不得有环**。
     * <p><b>时机</b>（A2）：由调用方在 {@code Role.build()} **之后**、**任何 {@code awake()} 之前**调用 ——
     * 框架里有两处：{@code registry/RoleLoader#loadInto}（**注册之前** ⇒ 坏模板根本不进注册表）与
     * {@link #createInstance(Player, RolesContext)}（**实例化之前** ⇒ 任何路径都绕不过去）。
     * <p><b>失败形态</b>（硬失败，不降级）：抛 {@link ComponentDependencyException}；调用方
     * {@code RoleLoader} 记一条 {@code SEVERE} 并**跳过该角色**（其余角色继续装配）。
     * <p><b>匹配规则（写死）</b>：组件 A 的必需类型 R 被满足 ⟺ 存在**另一个**组件 B（B 的 id ≠ A 的 id）
     * 使 {@code R.isAssignableFrom(B.providedType())}。**A 自己不算提供者** —— 用户原话是"检查自己需要的
     * 依赖（**其他组件**）"，因此"只有自己提供该类型"按**缺依赖**处理（这也是冻结件 §4.3 的"自环"用例：
     * 它在本实现里落成一条可读的缺依赖错误，而不是一个能被自己满足的假通过）。
     * <p><b>为什么不用反射去"扫"组件实例</b>：装配期**还没有任何实例**（实例化发生在
     * {@code RoleInstance} 的构造期）⇒ 检查只能基于描述符声明的类型，这也是它能在"注册之前"完成的原因。
     * <p><b>不检查什么（如实申报）</b>：可选依赖缺失不报错；提供类型是**族级**的组件（三个家族描述符的
     * 泛型实参是家族基类）无法满足"按具体类"的依赖声明，除非该描述符覆写
     * {@code providedType()}（见 {@code RoleComponent.Specification#providedType()}）。
     *
     * @throws ComponentDependencyException 缺必需依赖或依赖图有环（消息里点名角色 / 组件 id / 缺的类型）
     */
    public void verifyDependencies(){
        List<String> problems = new ArrayList<>(missingRequiredDependencies());
        problems.addAll(dependencyCycles());
        if(!problems.isEmpty()){
            throw new ComponentDependencyException(
                    "Role '" + id + "' failed the assembly-time dependency check: " + String.join(" | ", problems));
        }
    }

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

    /**
     * **依赖环检测**（冻结件 §4.3 / 裁定②：禁止依赖循环）：把"必需依赖"连成有向图
     * （A → B ⟺ A 的某个必需类型由 B 提供，B ≠ A），返回可读的环清单（空 = 无环）。
     * <p>自环（A→A）在**连边阶段就被排除**（自己不算提供者）⇒ 它的可观测形态 = 缺依赖错误；
     * 互环（A→B→A）与更长的环都在这里被抓到，消息给出**完整路径**（例：{@code a -> b -> a}）。
     */
    public List<String> dependencyCycles(){
        List<String> cycles = new ArrayList<>();
        Set<String> finished = new LinkedHashSet<>();
        for(String start : components.keySet()){
            if(finished.contains(start)) continue;
            Set<String> onPath = new LinkedHashSet<>();
            Deque<String> path = new ArrayDeque<>();
            walkForCycles(start, path, onPath, finished, cycles);
        }
        return cycles;
    }

    private void walkForCycles(String current, Deque<String> path, Set<String> onPath, Set<String> finished,
                               List<String> cycles){
        if(onPath.contains(current)){
            List<String> cycle = new ArrayList<>();
            boolean collecting = false;
            for(String node : path){
                if(node.equals(current)) collecting = true;
                if(collecting) cycle.add(node);
            }
            cycle.add(current);
            String rendered = "dependency cycle: " + String.join(" -> ", cycle);
            if(!cycles.contains(rendered)) cycles.add(rendered);
            return;
        }
        if(finished.contains(current)) return;
        onPath.add(current);
        path.addLast(current);
        ComponentEntry entry = components.get(current);
        if(entry != null){
            for(Class<? extends RoleComponent> required : entry.getRequiredTypes()){
                for(Map.Entry<String, ComponentEntry> other : components.entrySet()){
                    if(other.getKey().equals(current)) continue;
                    if(required.isAssignableFrom(other.getValue().getProvidedType())){
                        walkForCycles(other.getKey(), path, onPath, finished, cycles);
                    }
                }
            }
        }
        path.removeLast();
        onPath.remove(current);
        finished.add(current);
    }

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
     * 它的两个消费者改为按**能力接口**判定：{@code CooldownBearing} / 占栏位组件）。
     * 未注册 ⇒ {@code null}。
     */
    public Class<?> descriptorTypeOf(String id){
        ComponentEntry entry = components.get(id);
        return entry != null ? entry.getDescriptorType() : null;
    }

    public Faction getFaction() { return faction; }

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

