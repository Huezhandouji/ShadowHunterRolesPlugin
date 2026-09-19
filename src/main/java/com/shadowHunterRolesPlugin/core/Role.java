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
    //武器和技能所在的栏位
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
        this.slotMap = Collections.unmodifiableMap(builder.slotMap);

        this.icon = builder.icon;

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
     * 装配条目：`(工厂, 权威 kind, 槽位?)`。
     * <p>槽位 = {@code -1} 表示**不占热键栏**（不进 `slotMap` ⇒ 渲染器遍历 `slotMap` 时天然看不到它）。
     */
    public static final class ComponentEntry{

        private final ComponentFactory<? extends RoleComponent> factory;
        private final ItemKind kind;
        private final int slot;

        ComponentEntry(ComponentFactory<? extends RoleComponent> factory, ItemKind kind, int slot){
            this.factory = factory;
            this.kind = kind;
            this.slot = slot;
        }

        public ComponentFactory<? extends RoleComponent> getFactory() { return factory; }

        /** **权威 kind**（行为分支的唯一来源）。 */
        public ItemKind getKind() { return kind; }

        /** 槽位；{@code -1} = 无槽位（不占热键栏）。 */
        public int getSlot() { return slot; }

        public boolean hasSlot() { return slot >= 0; }
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

        /** 唯一有序组件表（声明序 = 装配调用序）—— 阶段 6 的派发序载体。 */
        private final Map<String, ComponentEntry> components = new LinkedHashMap<>();
        private final Map<Integer, String> slotMap = new HashMap<>();

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
         * **统一装配入口（占热键栏）**：权威 kind 由注册处给出（技能 / 主武器 / 被动都走这里）。
         * <p>工厂形参放宽到 {@code ComponentFactory<? extends RoleComponent>} ⇒ 组件**不必**继承
         * `Skill` / `MainWeapon` / `PassiveSkill`，只需 `extends RoleComponent` 并按需实现能力接口。
         * <p>槽位写入与 id 去重都发生在**唯一内部路径** {@code addComponentInternal} 内。
         */
        public Builder addComponent(String id, ComponentFactory<? extends RoleComponent> factory, int slot, ItemKind kind){
            return addComponentInternal(id, factory, kind, slot);
        }

        /**
         * **统一装配入口（不占热键栏）**：无槽位 ⇒ 不进 `slotMap` ⇒ 渲染器遍历 `slotMap` 时天然看不到它。
         * <p>其余语义与四参重载完全一致（同一条内部路径）。
         */
        public Builder addComponent(String id, ComponentFactory<? extends RoleComponent> factory, ItemKind kind){
            return addComponentInternal(id, factory, kind, -1);
        }

        /**
         * 装配一条技能（**弃用别名**）：语义等价于统一入口的四参重载（权威 kind = `SKILL`）。
         *
         * @deprecated 改用统一入口的四参重载 `addComponent` —— 两者走**同一条内部路径**
         *             （本方法只负责补上权威 kind，不另开实现）。
         */
        @Deprecated
        public Builder addSkill(String skillId, ComponentFactory<Skill> factory, int slot){
            return addComponentInternal(skillId, factory, ItemKind.SKILL, slot);
        }

        /**
         * 装配一条被动（**弃用别名**）：语义等价于统一入口的无槽位重载（权威 kind = `PASSIVE`；
         * 无槽位 ⇒ 不占热键栏）。
         *
         * @deprecated 改用统一入口的无槽位重载 `addComponent` —— 同一条内部路径。
         */
        @Deprecated
        public Builder addPassive(String passiveId, ComponentFactory<PassiveSkill> factory){
            return addComponentInternal(passiveId, factory, ItemKind.PASSIVE, -1);
        }

        /**
         * 装配一条主武器（**弃用别名**）：语义等价于统一入口的四参重载（权威 kind = `MAIN_WEAPON`）。
         *
         * @deprecated 改用统一入口的四参重载 `addComponent` —— 同一条内部路径。
         */
        @Deprecated
        public Builder addMainWeapon(String mainWeaponId, ComponentFactory<MainWeapon> factory, int slot){
            return addComponentInternal(mainWeaponId, factory, ItemKind.MAIN_WEAPON, slot);
        }

        /**
         * **唯一内部装配路径**（阶段 6）：两个新重载与三个弃用别名都只调用这里 ⇒ 校验、id 去重、
         * 槽位写入、入表各只有一处实现。
         *
         * @param slot 槽位；{@code < 0} = 无槽位（不占热键栏，不写 `slotMap`）
         */
        private Builder addComponentInternal(String id, ComponentFactory<? extends RoleComponent> factory,
                                             ItemKind kind, int slot){
            // 与迁移前临时实例取 id 的 fail-fast 等价：null 工厂在**装配期**立刻 NPE，而不是拖到实例创建
            Objects.requireNonNull(factory);

            if(id == null || id.trim().isEmpty()){
                throw new IllegalArgumentException("Component ID cannot be null or empty.");
            }
            if(kind == null){
                throw new IllegalArgumentException("Component kind cannot be null.");
            }
            ensureIdNotRegistered(kind, id);

            boolean hasSlot = slot >= 0;
            if(hasSlot){
                validateSlot(slot);
            }

            components.put(id, new ComponentEntry(factory, kind, hasSlot ? slot : -1));
            if(hasSlot){
                slotMap.put(slot, id);
            }

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

        //O-12：槽位冲突 fail-fast（§10 裁决 1）—— 抛异常、该角色不注册，不再"告警 + 覆盖"
        private void validateSlot(int slot){
            if(slot < 0 || slot > 8){
                throw new IllegalArgumentException("Slot must be between 0 and 8, got: " + slot);
            }
            if(slotMap.containsKey(slot)){
                throw new IllegalArgumentException("Slot " + slot + " is already occupied by '" + slotMap.get(slot) + "'.");
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
    public Map<Integer, String> getSlotMap() { return slotMap; }
}

