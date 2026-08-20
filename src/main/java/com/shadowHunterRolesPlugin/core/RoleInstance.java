package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.EnergyChangeAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.SanTEChangeAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.UpdateAware;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class RoleInstance {


    private final Player player;
    private final Role role;
    private int currentEnergy;
    private int currentSanTE;

    private boolean isInSanTEPunishment = false;

    //冷却游戏刻时间戳
    private Map<String, Integer> skillCooldowns = new HashMap<>();
    private Map<String, Integer> mainWeaponCooldowns = new HashMap<>();

    //丢弃物品时，mc服务端会发送挥手数据包，这回导致触发左键交互事件，使用这个标记变量阻止按q时触发左键逻辑
    private boolean isDropping = false;
    public boolean isDropping() { return isDropping; }
    public void setDroppingState(boolean dropping) { isDropping = dropping; }

    private final Map<String, MainWeapon> mainWeaponMap = new HashMap<>();
    private final Map<String, Skill> skillMap = new HashMap<>();
    private final Map<String, PassiveSkill> passiveMap = new HashMap<>();

    //阵营信息，构造时将从Role里面复制，方便以后插件可以通过设置这个信息来实现无差别pvp
    private Faction faction;

    //上下文
    private final Map<String, Object> context = new HashMap<>();

    //buff管理器
    private final BuffManager buffManager;

    private int updateTaskId = -1;

    private final NamespacedKey roleHealthModifierKey = new NamespacedKey(
            ShadowHunterRolesPlugin.getInstance(),
            "role_health_modifier"
    );

    public RoleInstance(Player player, Role role){
        this.player = player;
        this.role = role;
        this.faction = role.getFaction();
        this.currentEnergy = role.getMaxEnergy();
        this.currentSanTE = role.getMaxSanTE();

        initComponents();

        this.buffManager = new BuffManager(player, this);

        //设置生命
        AttributeModifier am = new AttributeModifier(
                roleHealthModifierKey,
                role.getMaxHP() - 20,
                AttributeModifier.Operation.ADD_NUMBER

        );
        player.getAttribute(Attribute.MAX_HEALTH).removeModifier(am);
        player.getAttribute(Attribute.MAX_HEALTH).addModifier(am);
        player.setHealth(getMaxHealth());

        updateHotbar();


        updateTaskId = Bukkit.getScheduler().runTaskTimer(
                ShadowHunterRolesPlugin.getInstance(),
                this::triggerUpdate,
                1L,
                1L
        ).getTaskId();

        triggerLifecycleOnSet();
    }

    private void initComponents(){
        for(String skillId : role.getSkillIds()){
            Skill skill = role.createSkill(skillId);
            if(skill != null){
                skillMap.put(skillId, skill);
            }
        }

        for(String passiveId : role.getPassiveSkillIds()){
            PassiveSkill passive = role.createPassive(passiveId);
            if(passive != null){
                passiveMap.put(passiveId, passive);
            }
        }

        for(String weaponId : role.getMainWeaponIds()){
            MainWeapon mainWeapon = role.createMainWeapon(weaponId);
            if(mainWeapon != null){
                 mainWeaponMap.put(weaponId, mainWeapon);
            }
        }
    }


    public BuffManager getBuffManager() { return buffManager; }





    //武器，各个技能直接通信，用于角色组件之间关联
    public void setContext(String key, Object value){
        context.put(key, value);
    }

    public <T> T getContext(String key, Class<T> type){
        Object value = context.get(key);
        if(value == null) return null;
        return type.cast(value);
    }

    public void removeContext(String key){
        context.remove(key);
    }

    public boolean hasContext(String key){
        return context.containsKey(key);
    }

    //技能相关
    public Skill getSkillById(String skillId){
        return skillMap.getOrDefault(skillId, null);
    }

    public boolean isSkillReady(String skillId){
        int endTick = skillCooldowns.getOrDefault(skillId, 0);
        return Bukkit.getCurrentTick() >= endTick;
    }

    public void startSkillCooldown(String skillId, int ticks){
        int endTick = Bukkit.getCurrentTick() + ticks;
        skillCooldowns.put(skillId, endTick);

        updateHotbar();
        //冷却结束后刷新物品
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                getRemainingSkillCooldownTicks(skillId)
        );

    }

    public void endSkillCooldown(String skillId){
        skillCooldowns.remove(skillId);
        updateHotbar();
    }

    public int getRemainingSkillCooldownTicks(String skillId){
        int endTick = skillCooldowns.getOrDefault(skillId, 0);
        int remaining = endTick - Bukkit.getCurrentTick();
        return Math.max(0, remaining);
    }

    public float getRemainingSkillCooldownSeconds(String skillId){
        return getRemainingSkillCooldownTicks(skillId) / 20f;
    }


    //技能释放
    public boolean castSkillLeftClick(String skillId, Player caster){
        Skill skill = skillMap.get(skillId);
        if(skill == null){
            caster.sendMessage(Component.text("unknown skill!"));
            return false;
        }
        skill.onLeftClick(caster, this);

        //在1t后更新技能物品
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                1L
        );

        return true;
    }

    public boolean castSkillRightClick(String skillId, Player caster){
        Skill skill = skillMap.get(skillId);
        if(skill == null){
            caster.sendMessage(Component.text("unknown skill!"));
            return false;
        }
        skill.onRightClick(caster, this);

        //在1t后更新技能物品
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                1L
        );

        return true;
    }

    public boolean castSkillQDrop(String skillId, Player caster){
        Skill skill = skillMap.get(skillId);
        if(skill == null){
            caster.sendMessage(Component.text("unknown skill!"));
            return false;
        }
        skill.onDrop(caster, this);

        //在1t后更新技能物品
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                1L
        );

        return true;
    }


    //主武器相关
    public MainWeapon getMainWeaponById(String weaponId){
        return mainWeaponMap.getOrDefault(weaponId, null);
    }

    public boolean isMainWeaponReady(String weaponId){
        int endTick = mainWeaponCooldowns.getOrDefault(weaponId, 0);
        return Bukkit.getCurrentTick() >= endTick;
    }

    public void startMainWeaponCooldown(String weaponId, int ticks){
        int endTick = Bukkit.getCurrentTick() + ticks;
        mainWeaponCooldowns.put(weaponId, endTick);
        updateHotbar();

        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                getRemainingMainWeaponCooldownTicks(weaponId)
        );
    }

    public void endMainWeaponCooldown(String weaponId){
        mainWeaponCooldowns.remove(weaponId);
        updateHotbar();
    }

    public int getRemainingMainWeaponCooldownTicks(String weaponId){
        int endTick = mainWeaponCooldowns.getOrDefault(weaponId, 0);
        return Math.max(0, endTick - Bukkit.getCurrentTick());
    }

    public float getRemainingMainWeaponCooldownSeconds(String weaponId){
        return getRemainingMainWeaponCooldownTicks(weaponId) / 20f;
    }

    //释放主武器技能
    public boolean castMainWeaponLeftClick(String weaponId, Player caster){
        MainWeapon mainWeapon = mainWeaponMap.get(weaponId);
        if(mainWeapon == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
            return false;
        }
        mainWeapon.onLeftClick(caster, this);
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                1L
        );
        return true;
    }

    public boolean castMainWeaponRightClick(String weaponId, Player caster){
        MainWeapon mainWeapon = mainWeaponMap.get(weaponId);
        if(mainWeapon == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
            return false;
        }
        mainWeapon.onRightClick(caster, this);
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                1L
        );
        return true;
    }

    public boolean castMainWeaponQDrop(String weaponId, Player caster){
        MainWeapon mainWeapon = mainWeaponMap.get(weaponId);
        if(mainWeapon == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
            return false;
        }
        mainWeapon.onDrop(caster, this);
        Bukkit.getScheduler().runTaskLater(
                ShadowHunterRolesPlugin.getInstance(),
                this::updateHotbar,
                1L
        );
        return true;
    }



    public Collection<Skill> getSkills(){
        return skillMap.values();
    }

    public Collection<PassiveSkill> getPassives(){
        return passiveMap.values();
    }

    public Collection<MainWeapon> getMainWeapons(){
        return mainWeaponMap.values();
    }

    public Skill getSkill(String skillId){
        return skillMap.get(skillId);
    }

    public PassiveSkill getPassive(String passiveId){
        return passiveMap.get(passiveId);
    }

    public MainWeapon getMainWeapon(String weaponId){
        return mainWeaponMap.get(weaponId);
    }

    //更新技能物品栏，设置物品或者替换物品


    public void updateHotbar(){
        Player p = player;
        Inventory inv = p.getInventory();

        //获取角色武器技能栏位配置
        Map<Integer, String> slotMap = role.getSlotMap();
        if(slotMap == null || slotMap.isEmpty()) return;

        //填充武器技能
        for(Map.Entry<Integer, String> entry : slotMap.entrySet()){
            int slot = entry.getKey();
            String id = entry.getValue();

            //如果是武器
            if(mainWeaponMap.containsKey(id)){
                MainWeapon weapon = mainWeaponMap.get(id);
                boolean isReady = isMainWeaponReady(id);
                inv.setItem(slot, weapon.createIconItem(this));
                continue;
            }

            //如果是技能
            if(skillMap.containsKey(id)){
                Skill skill = skillMap.get(id);
                boolean isReady = isSkillReady(id);
                inv.setItem(slot, skill.createIconItem(this));
            }
        }
    }

    //更新元数据，主要是冷却
    public void updateSkillItemMeta(String skillId){
        Skill skill = skillMap.getOrDefault(skillId, null);
        if(skill == null) return;

        Map<Integer, String> slotMap = role.getSlotMap();
        if(!slotMap.containsValue(skillId)) return;

        int slot = 0;
        for(int key : slotMap.keySet()){
            if(slotMap.getOrDefault(key, null).equals(skillId)){
                slot = key;
                break;
            }
        }

        Inventory inv = player.getInventory();
        ItemStack item = inv.getItem(slot);
        if(item == null) return;

        ItemMeta meta = item.getItemMeta();

        meta.displayName(skill.getDisplayName(this));

        //元数据设回物品
        item.setItemMeta(meta);
    }

    public void updateAllSkillItemMeta(){
        for(String skillId : role.getSkillIds()){
            updateSkillItemMeta(skillId);
        }
    }

    //清除主武器，技能占用的快捷栏
    public void clearHotbar(){
        Inventory inv = player.getInventory();
        for(int i = 0; i < 9; i++){
            ItemStack item = inv.getItem(i);
            if(Skill.Utils.isSkillItem(item) ||
                    MainWeapon.Utils.isMainWeapon(item)){
                inv.setItem(i, null);
            }
        }
    }

    public static void clearHotbar(Player player){
        Inventory inv = player.getInventory();
        for(int i = 0; i < 9; i++){
            ItemStack item = inv.getItem(i);
            if(Skill.Utils.isSkillItem(item) ||
                    MainWeapon.Utils.isMainWeapon(item)){
                inv.setItem(i, null);
            }
        }
    }

    public Player getPlayer() { return player; }
    public Role getRole() { return role; }



    //生命
    public double getCurrentHealth(){
        return player.getHealth();
    }

    public void setCurrentHealth(double health){
        double clamped = Math.max(0d, Math.min(health, player.getAttribute(Attribute.MAX_HEALTH).getValue()));
        player.setHealth(clamped);
    }

    public double getMaxHealth(){
        return player.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    public void heal(double amount){
        double newHealth = Math.min(player.getHealth() + amount, player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setHealth(newHealth);
    }

    public void damage(double amount){
        player.damage(amount);
    }
    public void damage(double amount, Entity source){
        player.damage(amount, source);
    }

    public int getMaxEnergy(){
        return role.getMaxEnergy();
    }

    //能量
    public int getCurrentEnergy() { return currentEnergy; }

    public void setCurrentEnergy(int amount){
        int preEnergy = currentEnergy;

        this.currentEnergy = Math.clamp(amount, 0, role.getMaxEnergy());
        updateHotbar();

        EnergyChangeEvent event = new EnergyChangeEvent(player, this, preEnergy, currentEnergy, role.getMaxEnergy());
        Bukkit.getPluginManager().callEvent(event);
    }

    public boolean hasEnoughEnergy(int cost){
        return currentEnergy >= cost;
    }

    public void decreaseEnergy(int amount){
        setCurrentEnergy(currentEnergy - amount);
    }

    public void increaseEnergy(int amount){
        setCurrentEnergy(currentEnergy + amount);
    }

    //SanTE
    public int getCurrentSanTE() { return currentSanTE; }

    public void setCurrentSanTE(int amount){
        int preSanTE = currentSanTE;
        currentSanTE = Math.clamp(amount, 0, role.getMaxSanTE());

        SanTEChangeEvent event = new SanTEChangeEvent(player, this, preSanTE, currentSanTE, role.getMaxSanTE());
        Bukkit.getPluginManager().callEvent(event);
    }

    public void increaseSanTE(int amount){
        setCurrentSanTE(currentSanTE + amount);
    }

    public void decreaseSanTE(int amount){
        setCurrentSanTE(currentSanTE - amount);
    }

    public int getMaxSanTE() { return role.getMaxSanTE(); }

    public boolean isInSanTEPunishment() { return isInSanTEPunishment; }

    public void setIsInSanTEPunishmentState(boolean state){
        this.isInSanTEPunishment = state;
    }

    //faction相关
    public Faction getFaction(){
        return faction != null ? faction : role.getFaction();
    }

    //重设faction，一般用不到
    public void setFaction(Faction faction){
        this.faction = faction;
    }

    public void resetFaction(){
        this.faction = role.getFaction();
    }

    public boolean isHostileTo(RoleInstance other){
        if(other == null){
            return true;
        }

        Faction thisFaction = getFaction();
        Faction otherFaction = other.getFaction();

        if(thisFaction == otherFaction && thisFaction != Faction.UNKNOWN){
            return false;
        }
        return true;
    }

    public boolean isHostileTo(Player other){
        RoleInstance otherInstance =  RoleManager.getInstance().getRoleInstance(other);
        return isHostileTo(otherInstance);
    }

    public static boolean areHostile(Player p1, Player p2){
        if(p1 == null || p2 == null) return false;
        RoleInstance ins1 = RoleManager.getInstance().getRoleInstance(p1);
        RoleInstance ins2 = RoleManager.getInstance().getRoleInstance(p2);

        if(ins1 == null || ins2 == null) return true;
        return ins1.isHostileTo(ins2);

    }
    public static boolean areHostile(UUID p1, UUID p2){
        if(p1 == null || p2 == null) return false;
        RoleInstance ins1 = RoleManager.getInstance().getRoleInstance(p1);
        RoleInstance ins2 = RoleManager.getInstance().getRoleInstance(p2);

        if(ins1 == null || ins2 == null) return true;
        return ins1.isHostileTo(ins2);
    }

    //生命周期触发
    public void triggerLifecycleOnSet(){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof LifecycleAware){
                ((LifecycleAware) skill).onSet(player, this);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof LifecycleAware){
                ((LifecycleAware) passive).onSet(player, this);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof LifecycleAware){
                ((LifecycleAware) weapon).onSet(player, this);
            }
        }
    }

    public void triggerLifecycleOnClear(){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof LifecycleAware){
                ((LifecycleAware) skill).onClear(player, this);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof LifecycleAware){
                ((LifecycleAware) passive).onClear(player, this);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof LifecycleAware){
                ((LifecycleAware) weapon).onClear(player, this);
            }
        }
    }

    public void triggerEnergyChange(int preEnergy, int newEnergy){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof EnergyChangeAware){
                ((EnergyChangeAware) skill).onEnergyChange(player, this, preEnergy, newEnergy);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof EnergyChangeAware){
                ((EnergyChangeAware) passive).onEnergyChange(player, this, preEnergy, newEnergy);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof EnergyChangeAware){
                ((EnergyChangeAware) weapon).onEnergyChange(player, this, preEnergy, newEnergy);
            }
        }
    }

    public void triggerSanTEChange(int preSanTE, int newSanTE){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof SanTEChangeAware){
                ((SanTEChangeAware) skill).onSanTEChange(player, this, preSanTE, newSanTE);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof SanTEChangeAware){
                ((SanTEChangeAware) passive).onSanTEChange(player, this, preSanTE, newSanTE);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof SanTEChangeAware){
                ((SanTEChangeAware) weapon).onSanTEChange(player, this, preSanTE, newSanTE);
            }
        }
    }

    public void triggerUpdate(){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof UpdateAware){
                ((UpdateAware) skill).update(player, this);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof UpdateAware){
                ((UpdateAware) passive).update(player, this);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof UpdateAware){
                ((UpdateAware) weapon).update(player, this);
            }
        }

        if(shouldUpdateHotbar()){
            updateHotbar();
        }

        if(shouldUpdateItemMeta()){
            updateAllSkillItemMeta();
        }

    }

    //检测是否应该更新物品
    private boolean shouldUpdateHotbar(){
        for(String skillId : skillCooldowns.keySet()){
            if(isSkillReady(skillId)) return true;
        }
        for(String weaponId : mainWeaponCooldowns.keySet()){
            if(isMainWeaponReady(weaponId)) return true;
        }
        return false;
    }

    private boolean shouldUpdateItemMeta(){
        for(String skillId : skillCooldowns.keySet()){
            if(!isSkillReady(skillId)){
                return true;
            }
        }
        return false;
    }

    //清除这个实例时使用，重置玩家状态
    public void clear(){
        triggerLifecycleOnClear();

        Bukkit.getScheduler().cancelTask(updateTaskId);

        clearHotbar();

        player.getAttribute(Attribute.MAX_HEALTH).removeModifier(roleHealthModifierKey);

        player.getActivePotionEffects().forEach(potionEffect -> {
            player.removePotionEffect(potionEffect.getType());
        });

        buffManager.clearAll();


        skillCooldowns.clear();
        mainWeaponCooldowns.clear();
        context.clear();
    }

}
