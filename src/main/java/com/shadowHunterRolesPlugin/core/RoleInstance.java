package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastTrigger;
import com.shadowHunterRolesPlugin.core.dispatch.CombatHook;
import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.CooldownAware;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItem;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarRenderer;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.event.EnergyChangeEvent;
import com.shadowHunterRolesPlugin.event.SanTEChangeEvent;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.platform.Task;
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

    /**
     * **单一冷却命名空间**（阶段 8 前置 · 合并两张表）：`组件 id → 到期游戏刻`。
     * <p>合并前是 `skillCooldowns` / `mainWeaponCooldowns` 两张表，端口必须在**构造期**绑定 kind 才能选表
     * ⇒ 那是"删 `ItemKind`"的硬阻塞。现在**只有这一张表**：组件 id 在全仓本就是**跨类型唯一**的命名空间
     * （`Role.Builder` 的 id 去重是跨类型的）⇒ 一张表足以表达全部冷却，端口构造不再需要 kind。
     */
    private final Map<String, Integer> cooldowns = new HashMap<>();

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

    //组件注册表（组件集合 + 每组件资源表 + getComponent 查找）与统一渲染器（**唯一渲染者**）
    private final ComponentRegistry componentRegistry = new ComponentRegistry();
    private final HotbarRenderer hotbarRenderer = new HotbarRenderer(this);
    /**
     * **唯一的方法引用持有者**（阶段 5 判据 C-03）：供三条"程序化刷新"路径共用 ——
     * 冷却到点（启动时预约）、每 tick 到期扫描、显式结束冷却（S3）。
     * 它们都**不**额外产生裸直呼点（阶段 5 判据 C-02 的计数守恒：5 处直呼 + 1 处方法引用）。
     */
    private final Runnable markHotbarDirty = hotbarRenderer::markDirty;
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

        //阶段 5 · 4.4：构造期**同步首刷一次**（与迁移前的可见时机逐字一致 = 选角色瞬间热键栏即就绪、零延迟）；
        //首个 tick 因置脏初值为 true 还会再写一次同内容（不可见、且此后空闲 tick 不再写）。
        hotbarRenderer.render();
    }

    //平台上下文：阶段 2 的组件取用入口（阶段 4 起逐批收窄为 ComponentServices 端口白名单）
    public RolesContext rolesContext() { return platform; }

    //统一渲染器：阶段 5 · 4.4 起为**唯一渲染者**（写物品只发生在 core/hotbar 内）
    public HotbarRenderer hotbarRenderer() { return hotbarRenderer; }

    //组件注册表（框架内部：装配、资源兜底、getComponent 查找）
    public ComponentRegistry componentRegistry() { return componentRegistry; }

    /**
     * **临时调试用**（冷却自管理冒烟入口）：取某组件一对一的服务集，使调试命令能调用**同一个**端口实例
     * （如 {@code cooldowns().end()} / {@code cooldowns().start(ticks)}）。
     * 冒烟结束后随调试入口一并删除（见交付报告的删除清单）。
     */
    public ComponentServices servicesOf(String componentId){
        RoleComponent component = componentRegistry.getById(componentId);
        return component != null ? componentServices.get(component) : null;
    }

    /**
     * 组件与其**一对一**的服务集（按本组件 id 构造的冷却端口、按本组件 id 定位资源表的定时器端口）。
     * <p><b>阶段 8 前置</b>：冷却表已合并为**单一命名空间** ⇒ 本方法**不再需要 kind**
     * （合并前冷却端口必须在构造期绑定 kind 才能选表，那是"删 `ItemKind`"的硬阻塞）。
     * 权威 kind 仍由注册处承载（`Role.componentKindOf`），供框架侧行为分支按需读取。
     */
    private ComponentServices createServices(String componentId){
        return new ComponentServices(
                new SelfImpl(this),
                new EnergyPortImpl(this),
                new SanTEPortImpl(this),
                new VitalsPortImpl(this),
                new CooldownPortImpl(this, componentId),
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
        //阶段 6 · 派发面能力化：判据由「继承关系」改为「能力接口」——本处只用到 onCast（HotbarActionable 的唯一方法）
        if(!(component instanceof HotbarActionable active)) return false;

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

    /**
     * 组件初始化（阶段 6 · 统一装配）：**只遍历 {@code role.getComponents()} 一次** ——
     * 遍历顺序 = `Builder.add*` 的调用顺序 = **纯注册序**（旧的三段遍历
     * 「技能 → 被动 → 主武器」已删除，见交付说明的派发序申报）。
     * <p>权威 kind 由注册处随条目给出（`Role.componentKindOf`，供框架侧行为分支读取）；
     * **服务集构造不再需要 kind**（阶段 8 前置：冷却表已合并为单一命名空间）。
     */
    private void initComponents(){
        for(Map.Entry<String, Role.ComponentEntry> entry : role.getComponents().entrySet()){
            String componentId = entry.getKey();

            ComponentServices services = createServices(componentId);
            RoleComponent component = role.createComponent(componentId, services);
            if(component == null) continue;

            //旧窄类型视图（供既有公共访问器使用）：按**具体类型**归位，不按 kind 猜测
            if(component instanceof Skill skill) skillMap.put(componentId, skill);
            if(component instanceof MainWeapon weapon) mainWeaponMap.put(componentId, weapon);
            if(component instanceof PassiveSkill passive) passiveMap.put(componentId, passive);

            registerCreated(component, services);
        }
    }


    public BuffManager getBuffManager() { return buffManager; }





    //技能相关
    /** 就绪判定（**单一冷却命名空间**的视图）：无条目/已到期 ⇒ {@code true}。 */
    public boolean isSkillReady(String skillId){
        return isCooldownReady(skillId);
    }

    public int getRemainingSkillCooldownTicks(String skillId){
        return remainingCooldownTicks(skillId);
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
        //T-2c：T-1 的未迁移回退已删（组件侧一律走新管道）；可见刷新由 handleCast 的置脏 + 帧末 flush 保证。
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
        return isCooldownReady(weaponId);
    }

    public int getRemainingMainWeaponCooldownTicks(String weaponId){
        return remainingCooldownTicks(weaponId);
    }

    public float getRemainingMainWeaponCooldownSeconds(String weaponId){
        return getRemainingMainWeaponCooldownTicks(weaponId) / 20f;
    }

    // ───────── 冷却自管理（阶段 4 追补 D1/D2/D3/D4）· 阶段 8 前置：**单一命名空间** ─────────

    /** 就绪判定（单一表的规范读法）：无条目/已到期 ⇒ {@code true}。 */
    public boolean isCooldownReady(String componentId){
        return Bukkit.getCurrentTick() >= cooldowns.getOrDefault(componentId, 0);
    }

    /** 剩余刻（单一表的规范读法）：无条目/已到期 ⇒ {@code 0}。 */
    public int remainingCooldownTicks(String componentId){
        return Math.max(0, cooldowns.getOrDefault(componentId, 0) - Bukkit.getCurrentTick());
    }

    /**
     * **起冷却**（单一表的规范写法；合并前是 `startSkillCooldown` / `startMainWeaponCooldown` 两支）。
     *
     * @return 是否**真的写了表**。`false` = 该组件"没有冷却这回事"（今天的定义 = 被动，见
     *         {@link #hasCooldownNamespace(String)}）⇒ 调用方**不得**因此置脏（无声语义逐字保留）。
     */
    public boolean startCooldown(String componentId, int ticks){
        if(!hasCooldownNamespace(componentId)) return false;
        cooldowns.put(componentId, Bukkit.getCurrentTick() + ticks);

        //冷却到点置脏（方法引用形态，避免新增直呼点）：到点那一 tick 的帧末 flush 完成图标恢复。
        platform.scheduler().runLater(markHotbarDirty, remainingCooldownTicks(componentId));
        return true;
    }

    /**
     * 该组件**有没有冷却这回事**：合并前由冷却端口在**构造期**按 kind 挡下被动（"被动没有冷却 ⇒
     * 恒就绪 / 剩余 0 / 不写表 / `end()` 恒 false"）；现在端口不再持有 kind，这一句收在**表这一层**。
     * <p>今天 = 非 {@code PASSIVE}（权威 kind 取自注册处）。
     */
    private boolean hasCooldownNamespace(String componentId){
        return role.componentKindOf(componentId) != ItemKind.PASSIVE;
    }

    /** 冷却是否**正在进行**（条目存在且未到期）。与 {@code isCooldownReady()} 互补：后者对"无条目/已到期"都返回 true。 */
    boolean isCooling(String componentId){
        Integer endTick = cooldowns.get(componentId);
        return endTick != null && Bukkit.getCurrentTick() < endTick;
    }

    /** 重启顶替（S2）：旧段**未到期** ⇒ 清掉旧条目（调用方随后回调 {@code RESTARTED} 并起新冷却）；返回是否确实顶替了一段冷却。 */
    boolean clearCooldownForRestart(String componentId){
        if(!isCooling(componentId)) return false;
        cooldowns.remove(componentId);
        return true;
    }

    /**
     * 显式结束冷却（S3）：**仅在冷却中生效** ⇒ 移除条目 + 回调 {@code ENDED_BY_COMPONENT} + 一次可见刷新；
     * 不在冷却中 ⇒ 无副作用（幂等）。
     * @return 是否确实结束了一段冷却
     */
    boolean endCooldown(String componentId){
        if(!isCooling(componentId)) return false;
        cooldowns.remove(componentId);
        dispatchCooldownEnd(componentId, CooldownAware.CooldownEndReason.ENDED_BY_COMPONENT);
        markHotbarDirty.run();
        return true;
    }

    /**
     * 每 tick 扫描**唯一那张**冷却表：**到期 ⇒ 移除条目**（关闭 O-21：条目不再永驻）＋ 回调 {@code EXPIRED} ＋ **一次**可见刷新。
     * 复用既有每 tick 路径（{@link #triggerUpdate()}），**不新建 ticker**（与 t17 划界）。
     */
    private void scanCooldowns(){
        boolean removed = false;
        for(String componentId : expiredIds(cooldowns)){
            cooldowns.remove(componentId);
            dispatchCooldownEnd(componentId, CooldownAware.CooldownEndReason.EXPIRED);
            removed = true;
        }
        if(removed){
            //到期移除后置脏（方法引用形态）：同 tick 的帧末 flush 即完成图标恢复（冷却结束不再有可见延迟）
            markHotbarDirty.run();
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
     * <p>阶段 6 · 派发面能力化：判据由 {@code ActiveComponent} 改为能力接口 {@link CooldownAware}
     * （{@code ActiveComponent implements CooldownAware} ⇒ 既有组件的接受集逐字不变）。
     */
    void dispatchCooldownEnd(String componentId, CooldownAware.CooldownEndReason reason){
        RoleComponent component = componentRegistry.getById(componentId);
        if(component instanceof CooldownAware aware){
            runComponentUpdate("registered", component.getId(), () -> aware.onCooldownEnd(reason));
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



    //热键栏渲染：阶段 5 · 4.4 起**唯一渲染者 = HotbarRenderer**（写物品只发生在 core/hotbar 内）；
    //本容器只提供查表与状态输入，旧的"更新物品栏 / 更新元数据"方法（连同其两条调用路径）已随本批删除。

    /**
     * 统一渲染器的查表入口：按槽位表里的 id 取可渲染组件（未注册 id ⇒ {@code null}，渲染器跳过该槽位）。
     * <p>**适配点（阶段 6）**：优先取 {@link HotbarPresentable#asHotbarItem()} 的规格视图 ——
     * 这样「只 `extends RoleComponent` + 实现 `HotbarPresentable`」的新式组件同样可被渲染；
     * 旧式实现（自身即 `HotbarItem`）原样返回。
     * <p>行为分支（技能/主武器）由**注册 kind** 决定，不在此处区分。
     */
    public HotbarItem hotbarItemOf(String id){
        RoleComponent component = componentRegistry.getById(id);
        if(component == null) return null;
        if(component instanceof HotbarPresentable presentable) return presentable.asHotbarItem();
        return component instanceof HotbarItem item ? item : null;
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
        //触点④（能量单一入口）：**无条件**置脏（不做"跨阈值才置脏"的优化 —— 那属阶段 5 性能项）
        hotbarRenderer.markDirty();

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

        //阶段 4 追补（冷却自管理 · D2）：到期条目 ⇒ 移除 + 回调（关闭 O-21）；可见刷新改由同 tick 的帧末 flush 承担
        scanCooldowns();

        //阶段 5 · 4.4 帧末 flush（落点 = tick 末尾，紧接组件更新与到期扫描之后）：
        //① 判脏 → ② 写物品（唯一写点 = HotbarRenderer.render）→ ③ 清脏（此顺序不可交换）
        //入口条件并入 B-2：**有技能冷却中** ⇒ 每 tick 至少刷一次（否则技能名里的 " x.xs" 不再逐 tick 递减 = 可见行为变化）。
        if(hotbarRenderer.isDirty() || hasCoolingSkill()){
            hotbarRenderer.render();
            hotbarRenderer.clearDirty();
        }

    }

    /**
     * B-2 谓词：是否存在**冷却中**（条目存在且未到期）的技能。
     * 只数技能 —— 主武器冷却名没有秒数文本，外观恒定 ⇒ 不需要每 tick 刷新（与迁移前一致）。
     * <p>阶段 8 前置（单一冷却表）：表里现在混放着各 kind 的条目 ⇒ 这里用**权威 kind**
     * （{@code Role.componentKindOf}）把主武器排除掉，口径与合并前**逐字相同**。
     */
    private boolean hasCoolingSkill(){
        for(String componentId : cooldowns.keySet()){
            if(role.componentKindOf(componentId) != ItemKind.SKILL) continue;
            if(!isSkillReady(componentId)) return true;
        }
        return false;
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

    //阶段 5 · 4.4：旧的两条每 tick 轮询判定（"检测是否应该更新物品"）已删 ——
    //其中一条是恒假死路径，另一条的语义（B-2）并入 triggerUpdate 末尾的帧末 flush 入口条件。

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


        cooldowns.clear();
    }

}
