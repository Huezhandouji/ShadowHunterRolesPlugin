package com.shadowHunterRolesPlugin.core;


import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.apache.maven.model.Build;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.eclipse.sisu.launch.Main;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class Role {

    private final String id;
    private final Component displayName;
    private final Component description;

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

    public static class Builder{

        private final String id;
        private Component displayName;
        private Component description;
        private double maxHP = 20d;
        private double baseATK = 10d;
        private int maxEnergy = 100;
        private int maxSanTE = 100;
        private Faction faction = Faction.UNKNOWN;

        private final Map<String, Supplier<Skill>> skillSuppliers = new LinkedHashMap<>();
        private final Map<String, Supplier<PassiveSkill>> passiveSuppliers = new LinkedHashMap<>();
        private final Map<String, Supplier<MainWeapon>> mainWeaponSuppliers = new LinkedHashMap<>();
        private final Map<Integer, String> slotMap = new HashMap<>();

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

        public Builder description(Component description) {
            this.description = description;
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
            if (skillSuppliers.containsKey(skillId)) {
                throw new IllegalArgumentException("Skill already registered: " + skillId);
            }

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
            if (skillSuppliers.containsKey(passiveId)) {
                throw new IllegalArgumentException("Passive already registered: " + passiveId);
            }

            passiveSuppliers.put(passiveId, supplier);

            return this;
        }

        public Builder addMainWeapon(Supplier<MainWeapon> supplier, int slot){
            MainWeapon temp = supplier.get();
            String mainWeaponId = temp.getId();

            if(mainWeaponId == null || mainWeaponId.trim().isEmpty()){
                throw new IllegalArgumentException("MainWeapon ID cannot be null or empty.");
            }
            if (skillSuppliers.containsKey(mainWeaponId)) {
                throw new IllegalArgumentException("MainWeapon already registered: " + mainWeaponId);
            }

            validateSlot(slot);

            mainWeaponSuppliers.put(mainWeaponId, supplier);
            slotMap.put(slot, mainWeaponId);

            return this;
        }

        private void validateSlot(int slot){
            if(slot < 0 || slot > 8){
                throw new IllegalArgumentException("Slot must be between 0 and 8, got: " + slot);
            }
            if(slotMap.containsKey(slot)){
                ShadowHunterRolesPlugin.getInstance().getLogger().warning("Slot " + slot + " is already occupied, but you overrode it with a new skill or mainWeapon!");
            }
        }

        public Role build(){
            if(displayName == null) displayName = Component.text(id);
            if(description == null) description = Component.text("No description yet.");

            return new Role(this);
        }

    }




    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
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

    public Supplier<MainWeapon> getMainWeaponSupplier(String weaponId){
        return mainWeaponSuppliers.get(weaponId);
    }

    public Supplier<Skill> getSkillSupplier(String skillId){
        return skillSuppliers.get(skillId);
    }

    public Supplier<PassiveSkill> getPassiveSupplier(String passiveId){
        return passiveSuppliers.get(passiveId);
    }

    public Faction getFaction() { return faction; }
    public Map<Integer, String> getSlotMap() { return slotMap; }
}

