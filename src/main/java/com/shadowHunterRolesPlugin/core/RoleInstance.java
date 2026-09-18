package com.shadowHunterRolesPlugin.core;

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

    //T-2 ①③：迁移标记已删 —— 所有组件**无条件**走新管道（单一入口 = handleCast/handleAttack）。

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
     * 组件与其**一对一**的服务集（含按本组件 id/kind 构造的冷却端口、按本组件 id 定位资源表的定时器端口）。
     * 收尾批⑤：改按 {@code (id, kind)} 构造 —— 服务集必须先于组件实例存在（构造期注入）。
     */
    private ComponentServices createServices(String componentId, ItemKind kind){
        return new ComponentServices(
                new SelfImpl(this),
                new EnergyPortImpl(this),
                new SanTEPortImpl(this),
                new VitalsPortImpl(this),
                new CooldownPortImpl(this, componentId, kind),
                new BuffPortImpl(this),
                new FactionPortImpl(this),
                new DamagePortImpl(),
                new TimerPortImpl(this, componentRegistry, componentId),
                new ComponentLookupImpl(componentRegistry)
        );
    }

    /**
     * 组件创建之后的**紧邻登记**（五条件①④）：服务集与组件一对一进表，组件同时进注册表。
     * 服务集是在**构造期**交给组件的（{@code factory.create(id, services)}）⇒ 不存在"创建后尚未注入"的窗口。
     */
    private void registerCreated(RoleComponent component, ComponentServices services){
        componentServices.put(component, services);
        componentRegistry.register(component);
    }

    // ───────── 阶段 4：施放 / 攻击管道（新旧路径并存；开关默认旧路径 ⇒ 行为不变） ─────────

    /**
     * 新路径施放入口。返回 {@code true} = 本次已由管道处理（旧路径不再插手）；
     * 开关关闭、或该 id 尚未迁移到 {@link RoleComponent} 时返回 {@code false}，交回旧路径。
     */
    public boolean handleCast(CastTrigger trigger, Player caster){
        if(caster == null) return false;

        ItemStack item = caster.getInventory().getItemInMainHand();
        String id = Skill.Utils.getSkillId(item);
        if(id == null) id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
        if(!(component instanceof ActiveComponent active)) return false;

        //冷却自管理（D1）：框架**不再**代启动冷却 —— 组件在施放成功处自行 svc().cooldowns().start(getCooldownTicks())；
        //声明值仍是唯一真值来源（4.7/O-13），启动点与启动值都与旧框架代启动逐字一致 ⇒ 可观察行为不变。
        active.onCast(new CastSignal(trigger));
        hotbarRenderer.markDirty();
        return true;
    }

    /** 新路径攻击入口（主武器）。语义同 {@link #handleCast}。 */
    public boolean handleAttack(Player victim, Player attacker){
        if(victim == null || attacker == null) return false;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        String id = MainWeapon.Utils.getWeaponId(item);
        if(id == null) return false;

        RoleComponent component = componentRegistry.getById(id);
        if(!(component instanceof CombatHook hook)) return false;

        //冷却自管理（D1）：框架不再代启动冷却（同 handleCast）
        hook.onAttack(new AttackSignal(victim));
        hotbarRenderer.markDirty();
        return true;
    }

    private void initComponents(){
        for(String skillId : role.getSkillIds()){
            ComponentServices services = createServices(skillId, ItemKind.SKILL);
            Skill skill = role.createSkill(skillId, services);
            if(skill != null){
                skillMap.put(skillId, skill);
                registerCreated(skill, services);
            }
        }

        for(String passiveId : role.getPassiveSkillIds()){
            ComponentServices services = createServices(passiveId, ItemKind.SKILL);
            PassiveSkill passive = role.createPassive(passiveId, services);
            if(passive != null){
                passiveMap.put(passiveId, passive);
                registerCreated(passive, services);
            }
        }

        for(String weaponId : role.getMainWeaponIds()){
            ComponentServices services = createServices(weaponId, ItemKind.MAIN_WEAPON);
            MainWeapon mainWeapon = role.createMainWeapon(weaponId, services);
            if(mainWeapon != null){
                mainWeaponMap.put(weaponId, mainWeapon);
                registerCreated(mainWeapon, services);
            }
        }
    }


    public BuffManager getBuffManager() { return buffManager; }





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
        if(runComponentPipeline(CastTrigger.LEFT_CLICK, caster)) return true;
        //T-1 (4)：旧派发入口（onLeftClick）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(skillMap.get(skillId) == null){
            caster.sendMessage(Component.text("unknown skill!"));
        }
        return false;
    }

    public boolean castSkillRightClick(String skillId, Player caster){
        if(runComponentPipeline(CastTrigger.RIGHT_CLICK, caster)) return true;
        Skill skill = skillMap.get(skillId);
        if(skill == null){
            caster.sendMessage(Component.text("unknown skill!"));
        }
        //T-2c：T-1 的未迁移回退已删（组件侧一律走新管道）；可见刷新由 handleCast 的 markDirty() + updateHotbar 每 tick 保证。
        return false;
    }

    public boolean castSkillQDrop(String skillId, Player caster){
        if(runComponentPipeline(CastTrigger.DROP, caster)) return true;
        //T-1 (4)：旧派发入口（onDrop）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(skillMap.get(skillId) == null){
            caster.sendMessage(Component.text("unknown skill!"));
        }
        return false;
    }

    /** T-2 (3)：**纯委派**（迁移标记已删）—— 组件一律走新管道，单一入口 = {@link #handleCast}。 */
    private boolean runComponentPipeline(CastTrigger trigger, Player caster){
        return handleCast(trigger, caster);
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

    // ───────── 冷却自管理（阶段 4 追补 D1/D2/D3/D4）─────────

    private Map<String, Integer> cooldownTable(ItemKind kind){
        return kind == ItemKind.SKILL ? skillCooldowns : mainWeaponCooldowns;
    }

    /** 冷却是否**正在进行**（条目存在且未到期）。与 {@code isReady()} 互补：后者对"无条目/已到期"都返回 true。 */
    boolean isCooling(String componentId, ItemKind kind){
        Integer endTick = cooldownTable(kind).get(componentId);
        return endTick != null && Bukkit.getCurrentTick() < endTick;
    }

    /** 重启顶替（S2）：旧段**未到期** ⇒ 清掉旧条目（调用方随后回调 {@code RESTARTED} 并起新冷却）；返回是否确实顶替了一段冷却。 */
    boolean clearCooldownForRestart(String componentId, ItemKind kind){
        if(!isCooling(componentId, kind)) return false;
        cooldownTable(kind).remove(componentId);
        return true;
    }

    /**
     * 显式结束冷却（S3）：**仅在冷却中生效** ⇒ 移除条目 + 回调 {@code ENDED_BY_COMPONENT} + 一次可见刷新；
     * 不在冷却中 ⇒ 无副作用（幂等）。
     * @return 是否确实结束了一段冷却
     */
    boolean endCooldown(String componentId, ItemKind kind){
        if(!isCooling(componentId, kind)) return false;
        cooldownTable(kind).remove(componentId);
        dispatchCooldownEnd(componentId, ActiveComponent.CooldownEndReason.ENDED_BY_COMPONENT);
        updateHotbar();
        return true;
    }

    /**
     * 每 tick 扫描两张冷却表：**到期 ⇒ 移除条目**（关闭 O-21：条目不再永驻）＋ 回调 {@code EXPIRED} ＋ **一次**可见刷新。
     * 复用既有每 tick 路径（{@link #triggerUpdate()}），**不新建 ticker**（与 t17 划界）。
     */
    private void scanCooldowns(){
        boolean removed = false;
        for(String skillId : expiredIds(skillCooldowns)){
            skillCooldowns.remove(skillId);
            dispatchCooldownEnd(skillId, ActiveComponent.CooldownEndReason.EXPIRED);
            removed = true;
        }
        for(String weaponId : expiredIds(mainWeaponCooldowns)){
            mainWeaponCooldowns.remove(weaponId);
            dispatchCooldownEnd(weaponId, ActiveComponent.CooldownEndReason.EXPIRED);
            removed = true;
        }
        if(removed){
            //D3 第一步：可见刷新仍走 updateHotbar（runLater(updateHotbar) 机制保留）；换 markDirty 留到 4.4。
            updateHotbar();
        }
    }

    /** 先收集到期 id 再移除（避免边遍历边改表）；只遍历尚未到期的条目，到期即移除 ⇒ 扫描开销有界。 */
    private List<String> expiredIds(Map<String, Integer> table){
        List<String> ids = new ArrayList<>();
        for(Map.Entry<String, Integer> entry : table.entrySet()){
            if(Bukkit.getCurrentTick() >= entry.getValue()){
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    /**
     * 冷却结束回调的唯一派发点（D4）：**先移除条目、再回调** ⇒ 回调内再 {@code end()} 只会得到 {@code false}（不递归重入）；
     * 异常隔离沿用 {@link #runComponentUpdate}（与 update()/onSanTEChange 同键）。
     */
    void dispatchCooldownEnd(String componentId, ActiveComponent.CooldownEndReason reason){
        RoleComponent component = componentRegistry.getById(componentId);
        if(component instanceof ActiveComponent active){
            runComponentUpdate("registered", active.getId(), () -> active.onCooldownEnd(reason));
        }
    }

    //释放主武器技能
    public boolean castMainWeaponLeftClick(String weaponId, Player caster){
        if(runComponentPipeline(CastTrigger.LEFT_CLICK, caster)) return true;
        //T-1 (4)：旧派发入口（onLeftClick）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(mainWeaponMap.get(weaponId) == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
        }
        return false;
    }

    public boolean castMainWeaponRightClick(String weaponId, Player caster){
        if(runComponentPipeline(CastTrigger.RIGHT_CLICK, caster)) return true;
        //T-1 (4)：旧派发入口（onRightClick）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(mainWeaponMap.get(weaponId) == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
        }
        return false;
    }

    public boolean castMainWeaponQDrop(String weaponId, Player caster){
        if(runComponentPipeline(CastTrigger.DROP, caster)) return true;
        //T-1 (4)：旧派发入口（onDrop）与组件侧 legacy 回调已删 —— 组件侧一律走新管道；
        //未迁移组件（T-1 后已无）不再有特殊落点。
        if(mainWeaponMap.get(weaponId) == null){
            caster.sendMessage(Component.text("unknown mainWeapon!"));
        }
        return false;
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

        //I-15：容器**直派**（不再经 RoleEventListener 转发；上一行的事件发布保持不变）
        dispatchSanTEChange(preSanTE, currentSanTE);
    }

    public void increaseSanTE(int amount){
        setCurrentSanTE(currentSanTE + amount);
    }

    public void decreaseSanTE(int amount){
        setCurrentSanTE(currentSanTE - amount);
    }

    public int getMaxSanTE() { return role.getMaxSanTE(); }

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

        //阶段 4（B②-c）：为**注册表内组件**广播新基类钩子 awake()。
        //广播给"全部注册组件"：所有组件的新钩子由各组件自行实现（基类提供默认空实现）；
        //按迁移状态分支会引入第二套判据（与硬约束 §20 删总闸的教训同类）。legacy 生命周期扇出已在 T-1 ④ 删除。
        for(RoleComponent component : componentRegistry.all()){
            component.awake();
        }
    }

    //start阶段：开始生效，顺序与awake一致（技能/被动/武器）
    public void triggerLifecycleStart(){
        if(player == null ) return;

        //阶段 4（B②-c）：为注册表内组件广播新基类钩子 start()（顺序 = 注册表顺序；理由同 awake 处注释）
        for(RoleComponent component : componentRegistry.all()){
            component.start();
        }
    }

    //stop阶段：停止生效（T-1 ④ 后仅剩新钩子广播，按注册表顺序）
    public void triggerLifecycleStop(){
        if(player == null ) return;

        //阶段 4（B②-c）：为注册表内组件广播新基类钩子 stop()。
        //**顺序说明**：新钩子按**注册表顺序**停止（legacy 逆序扇出已在 T-1 ④ 删除）。
        //两者不会对同一组件双触发同一逻辑 —— 迁移后的组件**不再实现 legacy 生命周期接口**，
        //未迁移组件则对基类 stop() 是**默认空实现** ⇒ 任一组件在任一时刻只被"真实逻辑"处理一次。
        //**幂等说明**：若组件在 stop() 里自行取消任务，随后 clear() 的 cancelAllAndClear() 仍会取消其
        //资源表内的同一句柄 ⇒ 重复 cancel 幂等（Task.cancel() 对已取消句柄是 no-op）。
        for(RoleComponent component : componentRegistry.all()){
            component.stop();
        }
    }

    //I-14：SanTE 派发的重入护栏状态。哨兵 Integer.MIN_VALUE = 无待发值；
    //派发期间的组件重入写入只记最新值（禁止嵌套），返回后合并补发一次。
    private boolean sanTEDispatching = false;
    private int sanTEPendingValue = Integer.MIN_VALUE;

    /**
     * SanTE 变更的**唯一派发点**（阶段 4 追补 I-15 容器直派 + I-14 重入护栏）。
     * <ul>
     *   <li><b>真变化才派发</b>（{@code pre == now} 直接返回）—— B⑨ 口径不变：SanTE 已为 0 时再扣不再通知组件；</li>
     *   <li><b>禁止嵌套派发</b>：派发期间组件再次改写 SanTE ⇒ 只把最新值记为待发并立即返回；</li>
     *   <li><b>合并成末次一次</b>：本次派发返回后，若期间有重入写入，则对"末次待发值"补发**一次**（中间态被合并掉）；</li>
     *   <li><b>`notified` 机制（**为什么不能拿 `currentSanTE` 比**）</b>：{@code setCurrentSanTE} 是**先写字段、后派发**，
     *       所以派发期间字段值已经等于重入写入的目标值 —— 若把补偿条件写成 {@code currentSanTE != target}，该条件**恒假**，
     *       补偿分支会退化成**不可达死代码**（且给人"已实现合并"的假象）。故这里改用局部 {@code notified}
     *       （初值 = 本次 {@code newSanTE}；每补发一次更新为 {@code target}）与 {@code target} 比较：
     *       **无重入 ⇒ 不补发（与旧行为逐字一致）**；**有重入 ⇒ 恰好补发末次一次**；
     *       循环退出条件 = {@code sanTEPendingValue == Integer.MIN_VALUE}（哨兵 = 无待发值）；</li>
     *   <li>异常隔离沿用 {@link #runComponentUpdate}（单个组件抛异常不影响其余组件）。</li>
     * </ul>
     * 现存两个实现者（{@code DefaultSanTEZeroPunishment} / {@code RedDeeplySorrowSkill} 的
     * {@code onSanTEChange}）都**不在钩子内同步写 SanTE**（前者只调度任务、后者只起冷却）
     * ⇒ 护栏在当前组件集下**不可达**，属防御性设施。
     */
    private void dispatchSanTEChange(int preSanTE, int newSanTE){
        if(player == null ) return;
        if(preSanTE == newSanTE) return;

        if(sanTEDispatching){
            sanTEPendingValue = newSanTE;
            return;
        }

        sanTEDispatching = true;
        try{
            //notified = "上一次已广播的 now"。**不要**改成与 currentSanTE 比较：
            //setCurrentSanTE 先写字段、后派发 ⇒ 重入时 currentSanTE 已等于 target，比较恒假 ⇒ 补偿永不发生（死代码）。
            int notified = newSanTE;
            broadcastSanTEChange(preSanTE, newSanTE);

            while(sanTEPendingValue != Integer.MIN_VALUE){
                int target = sanTEPendingValue;
                sanTEPendingValue = Integer.MIN_VALUE;
                if(notified != target){
                    broadcastSanTEChange(notified, target);
                    notified = target;
                }
            }
        }
        finally{
            sanTEDispatching = false;
        }
    }

    /** 按注册表顺序广播组件侧 {@code onSanTEChange(pre, now)}（顺序与 update()/start()/stop() 同源）。 */
    private void broadcastSanTEChange(int preSanTE, int newSanTE){
        for(RoleComponent component : componentRegistry.all()){
            runComponentUpdate("registered", component.getId(), () -> component.onSanTEChange(preSanTE, newSanTE));
        }
    }

    public void triggerUpdate(){
        if(player == null ) return;

        //阶段 4（B⑤）：为**注册表内组件**广播新基类钩子 update()。
        //**顺序说明**：按**注册表顺序**遍历（legacy 三段扇出已在 T-1 ④ 删除，无先后关系）；
        //所有组件都对基类 update() 自行实现（基类默认空实现）；本批组件均已迁移（T-2 ① 后无迁移标记）
        //**不再实现 legacy 更新接口** ⇒ 只被这一条路径调用，不会双触发；异常隔离复用 runComponentUpdate。
        for(RoleComponent component : componentRegistry.all()){
            runComponentUpdate("registered", component.getId(), component::update);
        }

        //阶段 4 追补（冷却自管理 · D2）：到期条目 ⇒ 移除 + 回调 + 一次刷新（关闭 O-21）
        scanCooldowns();

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
    }

}
