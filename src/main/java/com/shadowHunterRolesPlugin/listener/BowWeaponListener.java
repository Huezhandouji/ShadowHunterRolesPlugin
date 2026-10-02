package com.shadowHunterRolesPlugin.listener;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.RolesContext;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon.ProjectileHitSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon.ShootSignal;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 弓弩主武器（{@link BowWeapon}）的输入管道。
 *
 * <h2>本类与另外两条管道的关系（读这条就够）</h2>
 * 本类是**第三条**输入管道，与既有两条的关系各有一条硬边界：
 * <ul>
 *   <li><b>对 {@code SkillListener}</b>：判据是 **{@link BowWeapon.Utils#isBowWeapon}**，而弓弩物品写的是
 *       自己的键（不带 {@code SKILL_KEY}）⇒ 技能管道永远不会认领弓弩物品（否则它会取消右键、
 *       弓就拉不开）。两条管道各自按键判物，互不认领；</li>
 *   <li><b>对 {@code MainWeaponListener}</b>：同上（弓弩不带 {@code MAIN_WEAPON_KEY}）；差异在语义 ——
 *       近战主武器的**右键被那条管道接管**，而弓弩的**右键归原版**（拉弓 · 装填 · 击发），
 *       本类只在<b>箭矢离弦那一刻</b>派发 {@link BowWeapon#onShoot(ShootSignal)}。</li>
 * </ul>
 * 一句话：<b>右键归原版、左键与 Q 走 {@code onCast}、离弦走 {@code onShoot}</b>；三条管道按各自的识别键
 * 判物，因此永远不会互相抢物品。
 * <p>左键的"不许挖方块"这半与近战管道**同规**（两条管道都取消 {@code PlayerInteractEvent} 的左键），
 * 差别只在近战管道顺手派发 {@code onCast(LEFT_CLICK)}，而本类**也**派发同一入口（弓弩与技能家族同形）。
 *
 * <h2>事件 → 入口（逐条）</h2>
 * <table border="1">
 *   <caption>本类接下的七类事件</caption>
 *   <tr><th>事件</th><th>处理姿态</th><th>派发的入口</th></tr>
 *   <tr><td>{@link EntityShootBowEvent}</td><td>只读（不取消；顺手给箭矢打归属）</td>
 *       <td>{@code onShoot(ShootSignal)} + 请求重绘</td></tr>
 *   <tr><td>{@link ProjectileHitEvent}</td><td>只读（不取消原版伤害与插地）</td>
 *       <td>{@code onProjectileHit(ProjectileHitSignal)} + 请求重绘</td></tr>
 *   <tr><td>{@link PlayerInteractEvent}（右键）</td>
 *       <td><b>默认不取消</b>；仅"冷却中"取消（闸门）</td><td>无（本族不声明右键分支）</td></tr>
 *   <tr><td>{@link PlayerInteractEvent}（左键）</td><td>取消（保护：不许挖方块）</td>
 *       <td>{@code onCast(LEFT_CLICK)} + 请求重绘</td></tr>
 *   <tr><td>{@link EntityDamageByEntityEvent}（左键打人 / 近战命中）</td>
 *       <td><b>直接拦截</b>：取消原版伤害</td><td>无（{@code BowWeapon#onAttack} 已封死）</td></tr>
 *   <tr><td>{@link PlayerDropItemEvent}（Q）</td><td>取消（保护）</td>
 *       <td>{@code onCast(DROP)} + 请求重绘</td></tr>
 *   <tr><td>{@link InventoryClickEvent}</td><td>取消（保护）</td><td>无</td></tr>
 * </table>
 *
 * <h2>冷却闸门放在哪（与既有两条管道同源）</h2>
 * {@code SkillListener#cast} 与 {@code MainWeaponListener#cast} 的闸门在**入口**："冷却中不派发"。
 * 弓弩有两条派发入口，各有一道闸门，口径都是这一条：
 * <ul>
 *   <li><b>拉弓起点</b>（右键）：冷却中取消这次右键 ⇒ 弓拉不开、弩装填不上 ⇒ 自然射不出箭。
 *       **为什么不在箭矢离弦时拦**：那时拉弓已经白拉、箭已经离弦，拦它要么让玩家白等一次蓄力，
 *       要么得回答"取消射击事件时这支箭算不算被消耗"（本仓无该口径的运行级读数）——
 *       放在入口就不必回答它，且与热键栏图标（冷却中已变灰）给出的信息一致；</li>
 *   <li><b>左键 / Q</b>：都走 {@link #cast}，冷却中不派发（原版行为照旧被取消），与
 *       {@code MainWeaponListener#cast} 逐字同规。</li>
 * </ul>
 * <p><b>边界（如实申报）</b>：右键闸门只挡"新的拉弓"，因此"先拉开弓 · 途中该武器才进入冷却"这一瞬间仍能
 * 射出这一箭（冷却通常由本组件在射击成功处启动 ⇒ 这条窗口在正常玩法下不可达）。
 * <p>背包点击不设闸门：它不派发任何入口，只做取消（取消不是"用了一次武器"）。
 *
 * <h2>本类为什么按识别键判物</h2>
 * 判据 = {@link BowWeapon.Utils#isBowWeapon(ItemStack)}（与 {@code MainWeaponListener} 用
 * {@code MainWeapon.Utils#isMainWeapon} 同规）：**不按材质判**（`Material.BOW` 谁都可能有），
 * 也不按"角色 + 栏位"判 —— 键由组件的 {@code buildItem()} 最后一步写进 PDC，
 * 因此"是不是本系统的弓弩物品"这个问题只有一个答案来源。
 */
public class BowWeaponListener implements Listener {

    protected final RoleManager roleManager;
    private final RolesContext context;

    public BowWeaponListener(RoleManager roleManager, RolesContext context) {
        this.roleManager = roleManager;
        this.context = context;
    }

    // ───────── 主入口：箭矢离弦 → onShoot ─────────

    /**
     * **射击管道（本类的唯一入口）**：按 id 取通用面 → 判类型 → 受保护调用 → 请求重绘。
     * <p>箭矢**已经离弦**（原版事件未被取消），因此本处理器只读不取消 —— 与 {@code DamageHookListener}
     * 的"事件已发生、我只通知"同一姿态。
     * <p>{@code ignoreCancelled = true}：别的插件取消这次射击时，箭**不会**飞出去 ⇒ 不该被当成一次使用
     * （与 {@code SkillListener#onPlayerQDropSkillItem} 的取态同源）。
     * <p>冷却判定**不在此处**：与 {@code MainWeaponListener#onAttackPlayer} 同规 —— 入口管道只判"是不是本族
     * 物品"，"这次能不能用"由闸门（右键入口）与组件自己把住。
     */
    @EventHandler(ignoreCancelled = true)
    public void onShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player shooter)) return;

        //射击用的那一把弓 / 弩（原版给出；null 时下面的判定自带判空）
        ItemStack weapon = event.getBow();
        if (!BowWeapon.Utils.isBowWeapon(weapon)) return;

        //射出的箭矢本体：**这就是要交给组件的引用**。
        //防御性前置（弓 / 弩路径恒为 Projectile，此分支不可达）：非 Projectile 的射出物不派发，
        //  因为信号面按"箭矢"声明（不把 Entity 这个过宽的通用面漏给组件）。
        if (!(event.getProjectile() instanceof Projectile projectile)) return;

        RoleInstance instance = roleManager.getRoleInstance(shooter);
        if (instance == null) return;

        String weaponId = BowWeapon.Utils.getWeaponId(weapon);
        if (weaponId == null) return;

        RoleComponent component = instance.componentRegistry().getById(weaponId);
        if (!(component instanceof BowWeapon bow)) return;

        //给这支箭打归属（"它归哪个弓组件"）：命中管道靠它把 {@link ProjectileHitEvent}
        //  交回同一个组件。打在**离弦**这一刻，因为这是"射手 + 用的是哪把弓"都还在手上的最后时刻。
        BowWeapon.Utils.tagArrow(projectile, weaponId);

        instance.invokeComponentHook(component, "onShoot",
                () -> bow.onShoot(new ShootSignal(projectile, weapon, event.getForce())));
        requestRepaint(instance);
    }

    // ───────── 命中：箭矢命中实体 / 方块 → onProjectileHit ─────────

    /**
     * **命中管道**：箭矢打中实体或方块之后，按箭矢的归属把它交回**射它的那个弓组件**。
     *
     * <h2>归属怎么定（唯一口径）</h2>
     * 读箭矢身上的 {@link BowWeapon.Utils#ARROW_OWNER_KEY}（由 {@link #onShootBow} 在离弦时写入）。
     * 因此判据是"这支箭是哪把弓射的"，而**不是**"射手现在手上拿着什么、现在是什么角色"——
     * 中途换手 / 换角色 / 丢下弓，都不会把这支箭错记到别人头上；射手已无角色实例则整次跳过。
     *
     * <h2>不做的事（逐条申报）</h2>
     * <ul>
     *   <li><b>不取消</b>：原版箭矢伤害与"插在方块上"的原版行为都照常 —— 本族只管把"命中发生了"
     *       告诉组件；"击中方块要不要把箭清掉"是**组件**的产品口径（组件在钩子里
     *       {@code projectile.remove()} 即可），不是本管道的默认行为；</li>
     *   <li><b>不判敌人 / 不判玩家</b>：信号原样交回（命中实体可为 {@code null} = 打在方块上），
     *       判敌口径归组件（它才持有该角色的阵营视角）；</li>
     *   <li><b>不重复派发</b>：{@code ignoreCancelled = true} —— 别的插件取消这次命中时，
     *       这一次命中没有真的发生，不该被当成一次使用。</li>
     * </ul>
     *
     * <h2>为什么这里可以读箭矢身上的 PDC</h2>
     * 与物品侧读 {@code BOW_WEAPON_KEY} 是同一件事：身份的写入点唯一（离弦那一刻），
     * 读取点唯一（这里），中间的飞行过程不参与判定。
     */
    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        if (projectile == null) return;

        String ownerId = BowWeapon.Utils.arrowOwnerOf(projectile);
        if (ownerId == null) return;   //不是本系统射出的箭（没打过归属标签）

        //射手：原版给出；非玩家（发射器 / 别的实体）则没有角色实例可投递
        if (!(projectile.getShooter() instanceof Player shooter)) return;

        RoleInstance instance = roleManager.getRoleInstance(shooter);
        if (instance == null) return;

        RoleComponent component = instance.componentRegistry().getById(ownerId);
        if (!(component instanceof BowWeapon bow)) return;

        instance.invokeComponentHook(component, "onProjectileHit",
                () -> bow.onProjectileHit(new ProjectileHitSignal(
                        projectile, event.getHitEntity(), event.getHitBlock())));
        requestRepaint(instance);
    }

    // ───────── 左键：取消原版交互 + 派发 onCast(LEFT_CLICK) ─────────

    /**
     * 左键入口：**取消原版交互**（保护）后派发 {@link BowWeapon#onCast(CastSignal)}（trigger =
     * {@link CastTrigger#LEFT_CLICK}）—— 与 {@code MainWeaponListener#onLeftClick} 逐条同构。
     * <p><b>为什么必须拦</b>：不拦则手持本系统的弓弩物品就是"拿了一把能挖土方的镐"——
     * 左键会开始破坏方块、与方块交互，玩家可以把地形挖穿、也能用弓去推动拉杆 / 打开箱子一类。
     * 取消 {@code PlayerInteractEvent} 的 {@code LEFT_CLICK_AIR / LEFT_CLICK_BLOCK} = 原版挖矿动作的起点
     * （与近战主武器**完全同一条**既有做法）。
     * <p>闸门：冷却中不派发（{@link #cast} 里判），与 {@code MainWeaponListener#cast} 同源；
     * 取消原版交互这半**无条件生效**（哪怕冷却中也不许挖方块）。
     * <p>{@code isDropping()} 检查服务的是"Q 会顺带触发一次左键交互"这件事（见
     * {@link #onQDrop(PlayerDropItemEvent)}）：那段窗口里不再把这次左键当成一次施放，避免按一次 Q
     * 打出两次入口。
     */
    @EventHandler
    public void onLeftClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;

        if (!BowWeapon.Utils.isBowWeapon(item)) return;

        RoleInstance instance = roleManager.getRoleInstance(player);
        if (instance == null) return;

        if (instance.isDropping()) return;

        String weaponId = BowWeapon.Utils.getWeaponId(item);
        if (weaponId == null) return;

        event.setCancelled(true);

        cast(instance, weaponId, CastTrigger.LEFT_CLICK);
    }

    // ───────── 闸门：冷却中拉不开弓（其余时候右键归原版）─────────

    /**
     * 右键入口：<b>不取消</b>（拉弓 / 装填 / 击发全归原版）—— **唯一的例外是冷却中**，
     * 那时取消这次右键（= 拉弓起不来），闸门口径与 {@code SkillListener#cast} /
     * {@code MainWeaponListener#cast} 的"冷却中不派发"同源。
     * <p><b>为什么"取消"能挡住拉弓</b>：原版拉弓由这次交互触发 ⇒ 取消它就没有拉弓这回事
     * （这正是本族独立于 {@code MainWeapon} 的根因 —— 近战主武器的管道**无条件**取消右键，
     * 于是弓永远拉不开）。这里只在冷却中取消，因此"能用的弓"不受任何影响。
     * <p>副作用（如实申报）：冷却中右键点方块也开不了容器（取消的是整次交互）；这与近战主武器
     * "冷却中不响应右键"的既有体验一致，且窗口极短（冷却声明值）。
     */
    @EventHandler
    public void onRightClick(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (!BowWeapon.Utils.isBowWeapon(item)) return;

        RoleInstance instance = roleManager.getRoleInstance(event.getPlayer());
        if (instance == null) return;

        String weaponId = BowWeapon.Utils.getWeaponId(item);
        if (weaponId == null) return;

        RoleComponent component = instance.componentRegistry().getById(weaponId);
        if (component instanceof ActiveComponent active && active.isCoolingDown()) {
            event.setCancelled(true);
        }
    }

    // ───────── 近战命中：直接拦截（不派发任何入口）─────────

    /**
     * 近战命中（左键打人）：**直接拦截** —— 取消原版伤害，**不派发任何入口**。
     * <p>判据 = 攻击者主手拿着本系统的弓弩物品 ⇒ 这次近战整个不成立
     * （{@code BowWeapon#onAttack} 已被封成 {@code final} 空实现，本族没有"近战伤害"这个扩展点 ——
     * 伤害该由射击那边给）。
     * <p><b>为什么不做"受害者必须是玩家"的过滤</b>：这里拦的不是"不许打人"，而是"本系统的物品不造成原版
     * 近战伤害"——那是**物品层面**的规则，与目标是谁无关。（近战主武器侧有那道过滤，是因为它的
     * {@code onAttack} 需要一个玩家受害者；本族不派发任何入口，故不需要。）
     * 要把口径收窄成"只拦对玩家的命中"，在下面加一行
     * {@code if (!(event.getEntity() instanceof Player)) return;} 即可 —— 那正是
     * {@code MainWeaponListener#onAttackPlayer} 的形态。
     * <p>与射击路径不冲突：箭矢命中时 {@code getDamager()} 是**箭矢实体**（不是玩家）⇒ 不进本处理器。
     */
    @EventHandler
    public void onMeleeHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        ItemStack item = attacker.getInventory().getItemInMainHand();
        if (!BowWeapon.Utils.isBowWeapon(item)) return;

        RoleInstance instance = roleManager.getRoleInstance(attacker);
        if (instance == null) return;

        //取消原版事件；不派发任何入口（本族没有近战入口）
        event.setCancelled(true);
    }

    // ───────── 保护：本系统的弓弩物品不可丢弃、不可从背包里搬走 ─────────

    /**
     * Q 丢弃入口：取消（保护）后派发 {@link BowWeapon#onCast(CastSignal)}（trigger =
     * {@link CastTrigger#DROP}）—— 与 {@code MainWeaponListener#onQDrop} 逐条同规（含那个
     * {@code isDropping} 标记）。
     * <p>Q 在本族 = **第二条主动入口**（第一条是左键，第三条是箭矢离弦）。三件事逐条对齐近战管道：
     * ① 原版丢弃一律取消（本系统的物品不许离手）；② 置 / 清 {@code isDropping} 标记 1 tick
     * （压掉"按 Q 顺带触发的那次左键交互"，否则一次 Q 会被左键路径当成第二次施放）；③ 冷却中不派发
     * （{@link #cast} 里判），随后派发 + 请求重绘。
     * <p>{@code ignoreCancelled = true}：已取消的丢弃不重复取消、也不重复派发。
     */
    @EventHandler(ignoreCancelled = true)
    public void onQDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();

        if (!BowWeapon.Utils.isBowWeapon(item)) return;

        //取消原版丢弃
        event.setCancelled(true);

        RoleInstance instance = roleManager.getRoleInstance(player);
        if (instance == null) return;

        String weaponId = BowWeapon.Utils.getWeaponId(item);
        if (weaponId == null) return;

        //设置标记（1 tick 后清除）：压掉 Q 顺带触发的那次左键交互
        instance.setDroppingState(true);
        context.scheduler().runLater(() -> {
            instance.setDroppingState(false);
        }, 1L);

        cast(instance, weaponId, CastTrigger.DROP);
    }

    /**
     * 施放管道（归本 listener）：按 id 取通用面 → 判「声明了主动入口」→ 判冷却 → 受保护调用 → 请求重绘。
     * <p>与 {@code MainWeaponListener#cast} / {@code SkillListener#cast} 逐字同规，差异只有一处：
     * 本族的 {@link CastTrigger#RIGHT_CLICK} **不经过本方法**（右键归原版），因此本方法只被左键与 Q 调到。
     *
     * @return 是否真的施放了（未命中 / 未声明主动入口 / 冷却中则 {@code false}）
     */
    private boolean cast(RoleInstance instance, String weaponId, CastTrigger trigger) {
        RoleComponent component = instance.componentRegistry().getById(weaponId);
        if (!(component instanceof ActiveComponent active)) return false;
        if (active.isCoolingDown()) return false;
        instance.invokeComponentHook(component, "onCast", () -> active.onCast(new CastSignal(trigger)));
        requestRepaint(instance);
        return true;
    }

    /**
     * 背包点击（背包界面里点 / 光标上拿着）：取消，避免把弓弩物品从热键栏拿出来
     * （与 {@code MainWeaponListener#onInventoryClick} 同规；拖拽 / F 键 / 漏斗 / 合成格四类由
     * {@code listener/hook/HotbarItemProtectionListener} 按同一套识别键覆盖）。
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;

        ItemStack clicked = event.getCurrentItem();
        if (BowWeapon.Utils.isBowWeapon(clicked)) {
            event.setCancelled(true);
            return;
        }

        ItemStack cursor = event.getCursor();
        if (BowWeapon.Utils.isBowWeapon(cursor)) {
            event.setCancelled(true);
        }
    }

    /** 请求热键栏重绘（按 id 取渲染组件后调它的通用面；容器不代劳 —— 与另外两条管道同规）。 */
    private void requestRepaint(RoleInstance instance) {
        RoleComponent render = instance.componentRegistry().getById(HotbarRenderComponent.ID);
        if (render instanceof HotbarRenderComponent hotbar) hotbar.markDirty();
    }
}
