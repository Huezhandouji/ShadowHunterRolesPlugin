package com.shadowHunterRolesPlugin.core;


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

    private final Map<String, ComponentFactory<Skill>> skillFactories;
    private final Map<String, ComponentFactory<PassiveSkill>> passiveFactories;
    private final Map<String, ComponentFactory<MainWeapon>> mainWeaponFactories;
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

        this.skillFactories = Collections.unmodifiableMap(builder.skillFactories);
        this.passiveFactories = Collections.unmodifiableMap(builder.passiveFactories);
        this.mainWeaponFactories = Collections.unmodifiableMap(builder.mainWeaponFactories);
        this.slotMap = Collections.unmodifiableMap(builder.slotMap);

        this.icon = builder.icon;

    }

    public RoleInstance createInstance(Player player, RolesContext context){
        return new RoleInstance(player, this, context);
    }

    public Skill createSkill(String skillId, ComponentServices services){
        return createComponent(skillId, skillFactories.get(skillId), services);
    }

    public PassiveSkill createPassive(String passiveId, ComponentServices services){
        return createComponent(passiveId, passiveFactories.get(passiveId), services);
    }

    public MainWeapon createMainWeapon(String weaponId, ComponentServices services){
        return createComponent(weaponId, mainWeaponFactories.get(weaponId), services);
    }

    /**
     * **唯一组件创建点**（阶段 4 的 4.1）：全仓**创建路径**只有这里调用 {@code ComponentFactory.create}；
     * {@code Builder} 过去的三处**校验用**实例化（"造了再丢"）已在收尾批⑤随 id 上移（注册处声明）而删除，
     * 全仓不再有"为取 id 而临时造一个组件"的代码 —— 判据是「创建路径唯一（本方法）」。
     * 服务集**在构造期**交给组件（见 {@code roleComponent.ComponentFactory}）：组件返回时即已持有它，
     * 容器随后立刻登记（五条件①④，见 {@code debug-logs/阶段4-交付小结.md} §5.7），注入不可能被遗漏。
     */
    private <T extends RoleComponent> T createComponent(String id, ComponentFactory<T> factory, ComponentServices services){
        return factory != null ? factory.create(id, services) : null;
    }

    public Material getIcon(){
        return icon;
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

        private final Map<String, ComponentFactory<Skill>> skillFactories = new LinkedHashMap<>();
        private final Map<String, ComponentFactory<PassiveSkill>> passiveFactories = new LinkedHashMap<>();
        private final Map<String, ComponentFactory<MainWeapon>> mainWeaponFactories = new LinkedHashMap<>();
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
         * 装配一条技能：**id 由调用方（注册处）声明** —— 不再"造一个临时实例取 id"。
         * 收尾批⑤：装配条目 = {@code (id, ComponentFactory)}；容器在**构造期**把服务集交给组件。
         */
        public Builder addSkill(String skillId, ComponentFactory<Skill> factory, int slot){
            // 与迁移前临时实例取 id 的 fail-fast 等价：null 工厂在**装配期**立刻 NPE，而不是拖到实例创建
            Objects.requireNonNull(factory);

            if(skillId == null || skillId.trim().isEmpty()){
                throw new IllegalArgumentException("Skill ID cannot be null or empty.");
            }
            ensureIdNotRegistered("Skill", skillId);

            validateSlot(slot);

            skillFactories.put(skillId, factory);
            slotMap.put(slot, skillId);

            return this;
        }

        /**
         * 装配一条被动：**id 由注册处声明**（无槽位）；装配条目 = {@code (id, ComponentFactory)}。
         */
        public Builder addPassive(String passiveId, ComponentFactory<PassiveSkill> factory){
            Objects.requireNonNull(factory);

            if (passiveId == null || passiveId.trim().isEmpty()) {
                throw new IllegalArgumentException("Passive skill ID cannot be null or empty");
            }
            ensureIdNotRegistered("Passive", passiveId);

            passiveFactories.put(passiveId, factory);

            return this;
        }

        /**
         * 装配一条主武器：**id 由注册处声明**；装配条目 = {@code (id, ComponentFactory)}。
         */
        public Builder addMainWeapon(String mainWeaponId, ComponentFactory<MainWeapon> factory, int slot){
            Objects.requireNonNull(factory);

            if(mainWeaponId == null || mainWeaponId.trim().isEmpty()){
                throw new IllegalArgumentException("MainWeapon ID cannot be null or empty.");
            }
            ensureIdNotRegistered("MainWeapon", mainWeaponId);

            validateSlot(slot);

            mainWeaponFactories.put(mainWeaponId, factory);
            slotMap.put(slot, mainWeaponId);

            return this;
        }

        //设置这个角色的图标, 便于游戏逻辑插件自动化读取
        public Builder icon(Material icon){
            this.icon = icon;
            return this;
        }

        //O-10：id 去重必须是跨类型的 —— 技能/被动/主武器共用同一个 id 命名空间，任一重复都抛异常
        private void ensureIdNotRegistered(String type, String id){
            if (skillFactories.containsKey(id) || passiveFactories.containsKey(id) || mainWeaponFactories.containsKey(id)) {
                throw new IllegalArgumentException(type + " already registered: " + id);
            }
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
        return skillFactories.keySet();
    }
    public Set<String> getPassiveSkillIds() {
        return passiveFactories.keySet();
    }
    public Set<String> getMainWeaponIds(){
        return mainWeaponFactories.keySet();
    }

    public Faction getFaction() { return faction; }
    public Map<Integer, String> getSlotMap() { return slotMap; }
}

