package com.shadowHunterRolesPlugin.core;


import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
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
import java.util.function.Supplier;


public class Role {

    private final String id;
    private final Component displayName;
    private final List<Component> description;

    private final double maxHP;
    private final double baseATK;
    private final int maxEnergy;
    private final int maxSanTE;

    private final Map<String, Supplier<Skill>> skillSuppliers;
    private final Map<String, Supplier<PassiveSkill>> passiveSuppliers;
    private final Map<String, Supplier<MainWeapon>> mainWeaponSuppliers;
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

        this.skillSuppliers = Collections.unmodifiableMap(builder.skillSuppliers);
        this.passiveSuppliers = Collections.unmodifiableMap(builder.passiveSuppliers);
        this.mainWeaponSuppliers = Collections.unmodifiableMap(builder.mainWeaponSuppliers);
        this.slotMap = Collections.unmodifiableMap(builder.slotMap);

        this.icon = builder.icon;

    }

    public RoleInstance createInstance(Player player){
        return new RoleInstance(player, this);
    }

    public Skill createSkill(String skillId){
        Supplier<Skill> supplier = skillSuppliers.get(skillId);
        return supplier != null ? supplier.get() : null;
    }

    public PassiveSkill createPassive(String passiveId){
        Supplier<PassiveSkill> supplier = passiveSuppliers.get(passiveId);
        return supplier != null ? supplier.get() : null;
    }

    public MainWeapon createMainWeapon(String weaponId){
        Supplier<MainWeapon> supplier = mainWeaponSuppliers.get(weaponId);
        return supplier != null ? supplier.get() : null;
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

        private final Map<String, Supplier<Skill>> skillSuppliers = new LinkedHashMap<>();
        private final Map<String, Supplier<PassiveSkill>> passiveSuppliers = new LinkedHashMap<>();
        private final Map<String, Supplier<MainWeapon>> mainWeaponSuppliers = new LinkedHashMap<>();
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

        public Builder addSkill(Supplier<Skill> supplier, int slot){
            Skill temp = supplier.get();
            String skillId = temp.getId();

            if(skillId == null || skillId.trim().isEmpty()){
                throw new IllegalArgumentException("Skill ID cannot be null or empty.");
            }
            ensureIdNotRegistered("Skill", skillId);

            validateSlot(slot);

            skillSuppliers.put(skillId, supplier);
            slotMap.put(slot, skillId);

            return this;
        }

        public Builder addPassive(Supplier<PassiveSkill> supplier){
            PassiveSkill temp = supplier.get();
            String passiveId = temp.getId();

            if (passiveId == null || passiveId.trim().isEmpty()) {
                throw new IllegalArgumentException("Passive skill ID cannot be null or empty");
            }
            ensureIdNotRegistered("Passive", passiveId);

            passiveSuppliers.put(passiveId, supplier);

            return this;
        }

        public Builder addMainWeapon(Supplier<MainWeapon> supplier, int slot){
            MainWeapon temp = supplier.get();
            String mainWeaponId = temp.getId();

            if(mainWeaponId == null || mainWeaponId.trim().isEmpty()){
                throw new IllegalArgumentException("MainWeapon ID cannot be null or empty.");
            }
            ensureIdNotRegistered("MainWeapon", mainWeaponId);

            validateSlot(slot);

            mainWeaponSuppliers.put(mainWeaponId, supplier);
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
            if (skillSuppliers.containsKey(id) || passiveSuppliers.containsKey(id) || mainWeaponSuppliers.containsKey(id)) {
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
        return skillSuppliers.keySet();
    }
    public Set<String> getPassiveSkillIds() {
        return passiveSuppliers.keySet();
    }
    public Set<String> getMainWeaponIds(){
        return mainWeaponSuppliers.keySet();
    }

    public Faction getFaction() { return faction; }
    public Map<Integer, String> getSlotMap() { return slotMap; }
}

