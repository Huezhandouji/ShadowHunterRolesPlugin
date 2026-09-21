package com.shadowHunterRolesPlugin.core;


import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import com.shadowHunterRolesPlugin.platform.RolesContext;
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
     * <p>每个条目的 {@link ComponentEntry#getKind()} 是**权威 kind**（行为分支的唯一来源）。
     */
    private final Map<String, ComponentEntry> components;
    /** 三个按 kind 过滤的**有序** id 视图（组内保持声明序；旧的公共访问器语义不变）。 */
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
        this.skillIds = Collections.unmodifiableSet(filterIds(this.components, ItemKind.SKILL));
        this.passiveIds = Collections.unmodifiableSet(filterIds(this.components, ItemKind.PASSIVE));
        this.mainWeaponIds = Collections.unmodifiableSet(filterIds(this.components, ItemKind.MAIN_WEAPON));
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

    /** 按 kind 过滤出**保持声明序**的 id 视图（`LinkedHashSet`）。 */
    private static Set<String> filterIds(Map<String, ComponentEntry> components, ItemKind kind){
        Set<String> ids = new LinkedHashSet<>();
        for(Map.Entry<String, ComponentEntry> entry : components.entrySet()){
            if(entry.getValue().getKind() == kind){
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    public RoleInstance createInstance(Player player, RolesContext context){
        return new RoleInstance(player, this, context);
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
     * 权威 kind **不**经本方法传递给组件（组件无从得知，见 `RoleInstance.createServices(id, kind)`），
     * 它由 {@link #componentKindOf(String)} 供框架侧行为分支读取。
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
     * 装配条目：`(工厂, 权威 kind, 栏位?)`（阶段 7 · A 步）。
     * <p><b>不占栏位 = 栏位的缺失</b>：栏位用**可空的 {@link Integer}** 表达（`null` = 不占热键栏，
     * 不进 `slotMap` ⇒ 渲染器遍历 `slotMap` 时天然看不到它）。
     * 旧版的 `-1` 哨兵已删除 —— {@link #getSlot()} 在无栏位时**抛异常**，而不是返回一个能参与算术的值；
     * 想表达"不占栏位"只剩一条路：装配一个**没有栏位**的描述符（如 `PassiveSkill.Specification`）。
     * <p>本条目是装配期从描述符取到的**不可变快照**：只持有三个值，**不持有描述符对象** ⇒
     * 同一份描述符实例被两个角色共享时，后手改动影响不到先手。
     */
    public static final class ComponentEntry{

        private final ComponentFactory<? extends RoleComponent> factory;
        private final ItemKind kind;
        private final Integer slot;

        ComponentEntry(ComponentFactory<? extends RoleComponent> factory, ItemKind kind, Integer slot){
            this.factory = factory;
            this.kind = kind;
            this.slot = slot;
        }

        public ComponentFactory<? extends RoleComponent> getFactory() { return factory; }

        /** **权威 kind**（行为分支的唯一来源）。 */
        public ItemKind getKind() { return kind; }

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
         * **统一装配入口（描述符口径，阶段 7 · A 步新增）**：吃一个**装配期描述符**
         * （{@link com.shadowHunterRolesPlugin.roleComponent.RoleComponent.Specification}），
         * 权威 kind 与栏位都从描述符读，**不再由调用点传值**。
         * <p>占不占栏位由**描述符的类型**决定：带栏位的描述符（`HotbarSpecification` 一支）用
         * {@code setSlot} 指定位置，装配期未设栏位 ⇒ 此处抛异常；不带栏位的描述符
         * （`PassiveSkill.Specification`）**没有** {@code setSlot} ⇒ 天然不占栏位。
         * <p>本方法对传入描述符取**不可变快照**（{@code Specification#freeze()}）：条目只留
         * `(kind, 栏位, 工厂)` 三个值，**不持有描述符对象**。
         */
        public Builder addComponent(String id, RoleComponent.Specification<?> specification){
            Objects.requireNonNull(specification);
            RoleComponent.Specification.Snapshot snapshot = specification.freeze();
            return addComponentInternal(id, snapshot.getKind(),
                    snapshot.hasSlot() ? Integer.valueOf(snapshot.getSlot()) : null,
                    snapshot.getFactory());
        }

        /**
         * **统一装配入口（占热键栏）**：权威 kind 由注册处给出（技能 / 主武器 / 被动都走这里）。
         * <p>工厂形参放宽到 {@code ComponentFactory<? extends RoleComponent>} ⇒ 组件**不必**继承
         * `Skill` / `MainWeapon` / `PassiveSkill`，只需 `extends RoleComponent` 并按需实现能力接口。
         * <p>槽位写入与 id 去重都发生在**唯一内部路径** {@code addComponentInternal} 内。
         */
        public Builder addComponent(String id, ComponentFactory<? extends RoleComponent> factory, int slot, ItemKind kind){
            return addComponentInternal(id, kind, slot, factory);
        }

        /**
         * **统一装配入口（不占热键栏）**：无栏位 ⇒ 不进 `slotMap` ⇒ 渲染器遍历 `slotMap` 时天然看不到它。
         * <p>其余语义与四参重载完全一致（同一条内部路径）。
         */
        public Builder addComponent(String id, ComponentFactory<? extends RoleComponent> factory, ItemKind kind){
            return addComponentInternal(id, kind, null, factory);
        }

        /**
         * 装配一条技能（**弃用别名**）：语义等价于统一入口的四参重载（权威 kind = `SKILL`）。
         *
         * @deprecated 改用统一入口的四参重载 `addComponent` —— 两者走**同一条内部路径**
         *             （本方法只负责补上权威 kind，不另开实现）。
         */
        @Deprecated
        public Builder addSkill(String skillId, ComponentFactory<Skill> factory, int slot){
            return addComponentInternal(skillId, ItemKind.SKILL, slot, factory);
        }

        /**
         * 装配一条被动（**弃用别名**）：语义等价于统一入口的无栏位重载（权威 kind = `PASSIVE`；
         * 无栏位 ⇒ 不占热键栏）。
         *
         * @deprecated 改用统一入口的无栏位重载 `addComponent` —— 同一条内部路径。
         */
        @Deprecated
        public Builder addPassive(String passiveId, ComponentFactory<PassiveSkill> factory){
            return addComponentInternal(passiveId, ItemKind.PASSIVE, null, factory);
        }

        /**
         * 装配一条主武器（**弃用别名**）：语义等价于统一入口的四参重载（权威 kind = `MAIN_WEAPON`）。
         *
         * @deprecated 改用统一入口的四参重载 `addComponent` —— 同一条内部路径。
         */
        @Deprecated
        public Builder addMainWeapon(String mainWeaponId, ComponentFactory<MainWeapon> factory, int slot){
            return addComponentInternal(mainWeaponId, ItemKind.MAIN_WEAPON, slot, factory);
        }

        /**
         * **唯一内部装配路径**（阶段 6 立、阶段 7 · A 步改为"栏位可有可无"、B 步改为"栏位随组件走"）：
         * 新描述符入口、两个旧重载与三个弃用别名都只调用这里 ⇒ 校验、id 去重、入表各只有一处实现。
         * <p>本类**不再维护栏位表**：栏位只作为条目的一个值存在（{@code ComponentEntry.slot}），
         * 角色构造期再一次性派生出 {@code slotMap} 视图。
         *
         * @param slot 栏位；{@code null} = **不占栏位**（不占热键栏）——旧版用 {@code -1} 哨兵表达同一件事
         */
        private Builder addComponentInternal(String id, ItemKind kind, Integer slot,
                                             ComponentFactory<? extends RoleComponent> factory){
            // 与迁移前临时实例取 id 的 fail-fast 等价：null 工厂在**装配期**立刻 NPE，而不是拖到实例创建
            Objects.requireNonNull(factory);

            if(id == null || id.trim().isEmpty()){
                throw new IllegalArgumentException("Component ID cannot be null or empty.");
            }
            if(kind == null){
                throw new IllegalArgumentException("Component kind cannot be null.");
            }
            ensureIdNotRegistered(kind, id);

            if(slot != null){
                validateSlot(slot);
            }

            components.put(id, new ComponentEntry(factory, kind, slot));

            return this;
        }

        //设置这个角色的图标, 便于游戏逻辑插件自动化读取
        public Builder icon(Material icon){
            this.icon = icon;
            return this;
        }

        //O-10：id 去重必须是跨类型的 —— 技能/被动/主武器共用同一个 id 命名空间，任一重复都抛异常
        private void ensureIdNotRegistered(ItemKind kind, String id){
            if (components.containsKey(id)) {
                throw new IllegalArgumentException(kindLabel(kind) + " already registered: " + id);
            }
        }

        /** 旧错误文案里的类型词（保持可读性；异常类型与触发条件不变）。 */
        private static String kindLabel(ItemKind kind){
            return switch (kind) {
                case SKILL -> "Skill";
                case PASSIVE -> "Passive";
                case MAIN_WEAPON -> "MainWeapon";
            };
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

    /** 按 id 取**权威 kind**（框架侧行为分支的唯一来源）：未注册 ⇒ {@code null}。 */
    public ItemKind componentKindOf(String id){
        ComponentEntry entry = components.get(id);
        return entry != null ? entry.getKind() : null;
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

