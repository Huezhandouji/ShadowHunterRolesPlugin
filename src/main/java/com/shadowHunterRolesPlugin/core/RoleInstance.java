package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.RoleComponentAware.EnergyChangeAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.SanTEChangeAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.UpdateAware;
import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastTrigger;
import com.shadowHunterRolesPlugin.core.dispatch.CombatHook;
import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarRenderer;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.logging.Level;

public class RoleInstance {


    private final Player player;
    private final Role role;
    private int currentEnergy;
    private int currentSanTE;

    private boolean isInSanTEPunishment = false;

    //实例是否仍然有效：clear() 之后置为 false，组件里的延时任务用它做"实例已失效"守卫
    private boolean valid = true;

    //药水记账（D6 / O-7）：只记录本系统施加到本实例玩家身上的效果类型，clear() 只回收这些
    private final Set<PotionEffectType> appliedPotionTypes = new LinkedHashSet<>();

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

    //上下文（阶段 2：平台层剥离后，领域层唯一的平台入口）
    private final Map<String, Object> context = new HashMap<>();

    //平台上下文（调度/日志/键/阵营查询）
    private final RolesContext platform;

    //buff管理器
    private final BuffManager buffManager;

    //已经上报过update异常的组件，避免每tick刷屏
    private final Set<String> reportedUpdateErrors = new HashSet<>();

    private Task updateTask;

    private final NamespacedKey roleHealthModifierKey;

    //阶段 4：组件注册表（组件集合 + 每组件资源表 + getComponent 查找）与统一渲染器（骨架）
    private final ComponentRegistry componentRegistry = new ComponentRegistry();
    private final HotbarRenderer hotbarRenderer = new HotbarRenderer();
    private final Map<RoleComponent, ComponentServices> componentServices = new HashMap<>();

    /**
     * 新旧路径开关（阶段 4 的 4.2）：批次迁移期默认走**旧路径**（listener + 组件自己启冷却），
     * 行为逐字不变；批次 ①–⑨ 全部迁完后翻成 {@code true} 并删除旧路径。
     */
    private static final boolean USE_COMPONENT_PIPELINE = false;

    public RoleInstance(Player player, Role role, RolesContext platform){
        this.player = player;
        this.role = role;
        this.platform = platform;
        this.faction = role.getFaction();
        this.currentEnergy = role.getMaxEnergy();
        this.currentSanTE = role.getMaxSanTE();

        this.roleHealthModifierKey = platform.keys().of("role_health_modifier");

        initComponents();

        //装配完成 → 冻结注册表（此后 getComponent 才合法）
        componentRegistry.freeze();

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

        //生命周期时序：全部组件创建完成 -> awake全部 -> start全部 -> 启动ticker -> 渲染热键栏
        triggerLifecycleAwake();
        triggerLifecycleStart();

        updateTask = platform.scheduler().runRepeating(
                this::triggerUpdate,
                1L,
                1L
        );

        updateHotbar();
    }

    //平台上下文：阶段 2 的组件取用入口（阶段 4 起逐批收窄为 ComponentServices 端口白名单）
    public RolesContext rolesContext() { return platform; }

    //统一渲染器（阶段 4.1 骨架；接管渲染属 4.4）
    public HotbarRenderer hotbarRenderer() { return hotbarRenderer; }

    //组件注册表（框架内部：装配、资源兜底、getComponent 查找）
    public ComponentRegistry componentRegistry() { return componentRegistry; }

    /**
     * 唯一创建点之后的**紧邻两步**（五条件①④）：{@code new → bind → register}。
     * 只对已迁移到 {@link RoleComponent} 的组件生效；未迁移组件继续走旧路径（本轮为零迁移 ⇒ 不触发）。
     */
    private void bindAndRegister(Object component, ItemKind kind){
        if(!(component instanceof RoleComponent roleComponent)) return;
        ComponentServices services = createServices(roleComponent, kind);
        roleComponent.bind(services);          // ① 注册/钩子之前；② bind 只允许一次
        componentServices.put(roleComponent, services);
        componentRegistry.register(roleComponent);
    }

    /** 组件与它**一对一**的服务集（含构造期绑定本组件 id/kind 的冷却端口、指向本组件资源表的定时器端口）。 */
    private ComponentServices createServices(RoleComponent component, ItemKind kind){
        return new ComponentServices(
                new SelfImpl(this),
                new EnergyPortImpl(this),
                new SanTEPortImpl(this),
                new VitalsPortImpl(this),
                new CooldownPortImpl(this, component.getId(), kind),
                new BuffPortImpl(this),
                new FactionPortImpl(this),
                new DamagePortImpl(),
                new TimerPortImpl(this, componentRegistry, component),
                new ComponentLookupImpl(componentRegistry)
        );
    }

    // ───────── 阶段 4：施放 / 攻击管道（新旧路径并存；开关默认旧路径 ⇒ 行为不变） ─────────

    /**
     * 新路径施放入口。返回 {@code true} = 本次已由管道处理（旧路径不再插手）；
     * 开关关闭、或该 id 尚未迁移到 {@link RoleComponent} 时返回 {@code false}，交回旧路径。
     */
    public boolean handleCast(CastTrigger trigger, Player caster){
        if(!USE_COMPONENT_PIPELINE || caster == null) return false;

        ItemStack item = caster.getInventory().getItemInMainHand();
        String id = Skill.Utils.getSkillId(item);
        if(id == null) id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
        if(!(component instanceof ActiveComponent active)) return false;

        CastResult result = active.onCast(new CastSignal(trigger));
        if(result == CastResult.CAST){
            //声明值是唯一真值来源（4.7/O-13）：框架按 getCooldownTicks() 启动冷却
            componentServices.get(component).cooldowns().start(active.getCooldownTicks());
        }
        hotbarRenderer.markDirty();
        return true;
    }

    /** 新路径攻击入口（主武器）。语义同 {@link #handleCast}。 */
    public boolean handleAttack(Player victim, Player attacker){
        if(!USE_COMPONENT_PIPELINE || victim == null || attacker == null) return false;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        String id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
        if(!(component instanceof CombatHook hook)) return false;

        CastResult result = hook.onAttack(new AttackSignal(victim));
        if(result == CastResult.CAST){
            componentServices.get(component).cooldowns().start(((ActiveComponent) component).getCooldownTicks());
        }
        hotbarRenderer.markDirty();
        return true;
    }

    private void initComponents(){
        for(String skillId : role.getSkillIds()){
            Skill skill = role.createSkill(skillId);
            if(skill != null){
                skillMap.put(skillId, skill);
                bindAndRegister(skill, ItemKind.SKILL);
            }
        }

        for(String passiveId : role.getPassiveSkillIds()){
            PassiveSkill passive = role.createPassive(passiveId);
            if(passive != null){
                passiveMap.put(passiveId, passive);
                bindAndRegister(passive, ItemKind.SKILL);
            }
        }

        for(String weaponId : role.getMainWeaponIds()){
            MainWeapon mainWeapon = role.createMainWeapon(weaponId);
            if(mainWeapon != null){
                 mainWeaponMap.put(weaponId, mainWeapon);
                 bindAndRegister(mainWeapon, ItemKind.MAIN_WEAPON);
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
    public boolean isSkillReady(String skillId){
        int endTick = skillCooldowns.getOrDefault(skillId, 0);
        return Bukkit.getCurrentTick() >= endTick;
    }

    public void startSkillCooldown(String skillId, int ticks){
        int endTick = Bukkit.getCurrentTick() + ticks;
        skillCooldowns.put(skillId, endTick);

        updateHotbar();
        //冷却结束后刷新物品
        platform.scheduler().runLater(this::updateHotbar, getRemainingSkillCooldownTicks(skillId));

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
        platform.scheduler().runLater(this::updateHotbar, 1L);

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
        platform.scheduler().runLater(this::updateHotbar, 1L);

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
        platform.scheduler().runLater(this::updateHotbar, 1L);

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

        platform.scheduler().runLater(this::updateHotbar, getRemainingMainWeaponCooldownTicks(weaponId));
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
        platform.scheduler().runLater(this::updateHotbar, 1L);
        return true;
    }

    public boolean castMainWeaponRightClick(String weaponId, Player caster){
        MainWeapon mainWeapon = mainWeaponMap.get(weaponId);
        if(mainWeapon == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
            return false;
        }
        mainWeapon.onRightClick(caster, this);
        platform.scheduler().runLater(this::updateHotbar, 1L);
        return true;
    }

    public boolean castMainWeaponQDrop(String weaponId, Player caster){
        MainWeapon mainWeapon = mainWeaponMap.get(weaponId);
        if(mainWeapon == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
            return false;
        }
        mainWeapon.onDrop(caster, this);
        platform.scheduler().runLater(this::updateHotbar, 1L);
        return true;
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
                inv.setItem(slot, weapon.createIconItem(this));
                continue;
            }

            //如果是技能
            if(skillMap.containsKey(id)){
                Skill skill = skillMap.get(id);
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

    public void setIsInSanTEPunishmentState(boolean state){
        this.isInSanTEPunishment = state;
    }

    //实例是否有效：clear() 之后为 false，供组件里的延时任务做失效守卫
    public boolean isValid(){
        return valid;
    }

    //药水施加入口（记账）：施加到本实例玩家身上的效果记入账本，clear() 时只回收账本里的类型（O-7）
    public void applyPotionEffect(PotionEffect effect){
        if(effect == null) return;
        player.addPotionEffect(effect);
        appliedPotionTypes.add(effect.getType());
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

        Faction otherFaction = other.getFaction();

        return isHostileTo(otherFaction);
    }

    //阶段 2：不再查 RoleManager 单例，改走注入进来的 FactionLookup（未选角色 → UNKNOWN → 敌对）
    public boolean isHostileTo(Player other){
        return platform.factions().isHostile(getFaction(), other);
    }

    public boolean isHostileTo(Faction otherFaction){
        Faction thisFaction = getFaction();
        return thisFaction != otherFaction || thisFaction == Faction.UNKNOWN;
    }

    //生命周期触发
    //awake阶段：只解析跨组件依赖并缓存引用，必须幂等且不改动玩家可见状态
    public void triggerLifecycleAwake(){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof LifecycleAware){
                ((LifecycleAware) skill).awake(player, this);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof LifecycleAware){
                ((LifecycleAware) passive).awake(player, this);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof LifecycleAware){
                ((LifecycleAware) weapon).awake(player, this);
            }
        }
    }

    //start阶段：开始生效，顺序与awake一致（技能/被动/武器）
    public void triggerLifecycleStart(){
        if(player == null ) return;

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof LifecycleAware){
                ((LifecycleAware) skill).start(player, this);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof LifecycleAware){
                ((LifecycleAware) passive).start(player, this);
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof LifecycleAware){
                ((LifecycleAware) weapon).start(player, this);
            }
        }
    }

    //stop阶段：停止生效，遍历顺序与start相反（武器/被动/技能），逆序拆卸
    public void triggerLifecycleStop(){
        if(player == null ) return;

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof LifecycleAware){
                ((LifecycleAware) weapon).stop(player, this);
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof LifecycleAware){
                ((LifecycleAware) passive).stop(player, this);
            }
        }

        //遍历所有技能
        for(Skill skill : skillMap.values()){
            if(skill instanceof LifecycleAware){
                ((LifecycleAware) skill).stop(player, this);
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
                runComponentUpdate("skill", skill.getId(), () -> ((UpdateAware) skill).update(player, this));
            }
        }

        for(PassiveSkill passive : passiveMap.values()){
            if(passive instanceof UpdateAware){
                runComponentUpdate("passive", passive.getId(), () -> ((UpdateAware) passive).update(player, this));
            }
        }

        for(MainWeapon weapon : mainWeaponMap.values()){
            if(weapon instanceof UpdateAware){
                runComponentUpdate("mainWeapon", weapon.getId(), () -> ((UpdateAware) weapon).update(player, this));
            }
        }

        if(shouldUpdateHotbar()){
            updateHotbar();
        }

        if(shouldUpdateItemMeta()){
            updateAllSkillItemMeta();
        }

    }

    //单个组件抛异常时不能中断这一tick其他组件的更新；同一个组件的异常只上报一次，恢复正常后再提示一次
    private void runComponentUpdate(String componentType, String componentId, Runnable action){
        String key = componentType + ":" + componentId;
        try{
            action.run();
            if(reportedUpdateErrors.remove(key)){
                platform.logger().info(
                        "Role '" + role.getId() + "' component [" + key + "] recovered from a previous update error.");
            }
        }
        catch(Throwable throwable){
            if(reportedUpdateErrors.add(key)){
                platform.logger().log(Level.SEVERE,
                        "Role '" + role.getId() + "' component [" + key + "] threw an exception in update(), only this component is skipped. "
                                + "Repeated errors of this component are suppressed until it recovers.", throwable);
            }
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
        valid = false;

        triggerLifecycleStop();

        //阶段 4：框架兜底回收组件登记的全部资源（定时器等）——组件忘了取消也不会泄漏
        componentRegistry.cancelAllAndClear();

        if(updateTask != null){
            updateTask.cancel();
            updateTask = null;
        }

        clearHotbar();

        player.getAttribute(Attribute.MAX_HEALTH).removeModifier(roleHealthModifierKey);

        //药水记账（O-7 / D6）：只移除本系统记账过的效果，不再无条件清空玩家身上的所有药水效果
        for(PotionEffectType type : appliedPotionTypes){
            player.removePotionEffect(type);
        }
        appliedPotionTypes.clear();

        buffManager.clearAll();


        skillCooldowns.clear();
        mainWeaponCooldowns.clear();
        context.clear();
    }

}
