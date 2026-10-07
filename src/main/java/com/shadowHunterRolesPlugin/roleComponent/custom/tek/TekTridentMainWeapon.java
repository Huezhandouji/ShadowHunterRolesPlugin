package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 「特克」主武器：**逆命天理**（三叉戟）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>每次攻击</b>造成 <b>{@value #PHYSICAL_DAMAGE} 点物理伤害 + {@value #TRUE_DAMAGE} 点真实伤害</b>，
 *       并给敌人附加 <b>一层「真理」</b>；</li>
 *   <li><b>主武器 CD {@value #ATTACK_COOLDOWN_SECONDS} 秒</b>（= {@value #ATTACK_COOLDOWN_TICKS} 刻）；</li>
 *   <li><b>按住右键 {@value #CHARGE_SECONDS} 秒</b>（= {@value #CHARGE_TICKS} 刻）蓄力，
 *       蓄力时<b>沿用三叉戟的蓄力投出动作</b>，<b>松开右键</b>（= 三叉戟投出）才向前<b>刺出长枪</b>，
 *       {@value #DASH_FLIGHT_SECONDS} 秒内<b>突击 {@value #DASH_DISTANCE} 格</b>
 *       （突进 CD {@value #DASH_COOLDOWN_SECONDS} 秒）；<b>没蓄满就松手 ⇒ 不突刺</b>；</li>
 *   <li><b>突进沿途命中敌人 ⇒ 造成伤害并触发被动</b>（叠真理 + 减技能 CD + 得能量）；
 *       <b>一次突进对同一名目标只造成一次伤害</b>；</li>
 *   <li><b>蓄力过程中</b>：有<b>蓄力音效与粒子</b>，且<b>不可攻击</b>；</li>
 *   <li><b>突进 CD 完备后，给武器附魔提示</b>（物品常亮光效）；</li>
 *   <li><b>★ 突刺冷却中（含正在飞行）完全不响应右键</b>：不能蓄力、<b>不能举起武器</b>
 *       （不调 {@code startUsingItem} ⇒ 无举枪姿势）、<b>不出现蓄力粒子</b>。
 *       挡在 {@link #onCast} 最前面（{@code !isDashReady()} ⇒ 直接 return），
 *       {@link #update()} 里另有一条防御性兜底。</li>
 * </ol>
 *
 * <h2>攻击间隔 = 0.2 秒怎么做到（三件缺一不可）</h2>
 * <ol>
 *   <li>声明冷却 = {@value #ATTACK_COOLDOWN_TICKS} 刻（{@link Specification} 第 4 参）；</li>
 *   <li>{@link #onAttack} <b>开头</b>自判 {@code isCoolingDown()} ——
 *       <b>派发侧不替主武器挡冷却</b>（{@code MainWeaponListener} 只在技能路径判冷却），
 *       不自己判就会"冷却期内再攻击仍出伤"；</li>
 *   <li>物品攻速 = {@value #ATTACK_SPEED_VALUE}（三叉戟原版攻速 1.1 ⇒ 原版冷却 ≈ 18 刻 ≈ 0.9 秒，
 *       会把 4 刻的间隔压长成 0.9 秒）。</li>
 * </ol>
 *
 * <h2>★「按住 0.8 秒 → 松手才突刺」+ 三叉戟蓄力动作（口径申报，改前必读）</h2>
 * <b>Bukkit 没有"右键松开"事件</b>（{@code Action} 枚举只有
 * {@code LEFT_CLICK_AIR/BLOCK}、{@code RIGHT_CLICK_AIR/BLOCK}、{@code PHYSICAL}，**没有 RELEASE**），
 * 而且本工程对右键一律 {@code setCancelled(true)} ⇒ 原版那条"开始使用物品"的路走不通。
 *
 * <p>解法 = <b>我们主动把"使用物品"状态起起来</b>（{@code player.startUsingItem(HAND)}）。
 * 这一步一次解决两件事：
 * <ol>
 *   <li><b>三叉戟的蓄力投出动作</b>（需求）：举枪姿势由"是否正在使用物品"驱动，
 *       原版被取消 ⇒ 必须我们自己起；</li>
 *   <li><b>"手还举着吗"变成可靠判据</b>：在使用中时 {@code isHandRaised()} 为真，
 *       松手 ⇒ 服务端处理 RELEASE_USE_ITEM ⇒ 使用状态结束 ⇒ 它立刻转假
 *       ⇒ <b>松手可被即时、直接观测</b>（不需要靠间隙猜）。</li>
 * </ol>
 *
 * <p><b>同一刻探针 + 降级</b>：{@code startUsingItem} 是同步生效的，因此调完**立刻**读一次
 * {@code isHandRaised()}：
 * <table border="1">
 *   <tr><th>探针结果</th><th>{@link #holdReliable}</th><th>松手判据</th></tr>
 *   <tr><td>{@code true}（正常）</td><td>{@code true}</td>
 *       <td>{@code !player.isHandRaised()} —— 即时且可靠</td></tr>
 *   <tr><td>{@code false}（起状态失败）</td><td>{@code false}</td>
 *       <td>降级 = click-chatter + 间隙（{@link #RELEASE_GAP_TICKS}）：按住时
 *           {@code PlayerInteractEvent} 约每 4 刻重复派发，它停了就是松手</td></tr>
 * </table>
 * 于是<b>两种情形技能都可用</b>：正常时手感与需求完全一致；起状态失败时只是没有举枪姿势、
 * 松手判定变成"多等 8 刻"。
 *
 * <p>★ <b>必须用"刷新"封住原版投掷</b>（这是最容易漏、后果最难看的一处）：
 * 三叉戟的 {@code releaseUsing} 有"蓄力不足 10 刻则直接返回"的判据，
 * 一旦放任不管，玩家松手时已使用刻数 ≥ 10 ⇒ <b>原版会把我们的武器当三叉戟投掷出去</b>。
 * 因此蓄力期间每 {@value #HOLD_REFRESH_AT_TICKS} 刻
 * {@code clearActiveItem() → startUsingItem(HAND)} 把已使用刻数重置回 0
 * ⇒ 松手时恒 < 10 ⇒ 投掷分支永不执行；而举枪姿势只与"是否正在使用"有关 ⇒ 不中断。
 * 详见 {@link #HOLD_REFRESH_AT_TICKS} 的 javadoc。
 *
 * <p>另：{@link #onCast} 里"已在蓄力"的右键 = 按住中的心跳，<b>只累加计数、不重置蓄力进度</b>
 * （重置会让"按住"永远凑不满 0.8 秒）。
 *
 * <h2>不动框架</h2>
 * 全程只用基类公开面（{@code isCoolingDown()} / {@code startCooldown(int)} / {@code update()} /
 * {@code buildItem()} / {@code svc()}）与 Bukkit 的 {@code LivingEntity} 公开方法
 * （{@code startUsingItem} / {@code clearActiveItem} / {@code isHandRaised} /
 * {@code getActiveItemUsedTime}），没有新增框架能力、没改任何框架文件。
 */
public class TekTridentMainWeapon extends MainWeapon implements OperationProvider {

    /** 本组件的登记 id。 */
    public static final String ID = "tekTridentMainWeapon";

    // ───────── 数值口径（唯一修改点）─────────

    /** 每次攻击的物理伤害。 */
    private static final double PHYSICAL_DAMAGE = 4d;

    /** 每次攻击的真实伤害。 */
    private static final double TRUE_DAMAGE = 4d;

    /** 主武器攻击冷却（刻）：0.2 秒 = 4 刻。 */
    private static final int ATTACK_COOLDOWN_TICKS = 4;

    /** 主武器攻击冷却（秒；仅用于文案与 javadoc）。 */
    private static final String ATTACK_COOLDOWN_SECONDS = "0.2";

    /** 物品攻速目标值（统一 100，见流程文档 §6.3）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速属性修饰符的命名键（先移除后添加 ⇒ 必须固定）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("tek_trident_main_weapon_attack_speed");

    /** 蓄力时长（刻）：0.8 秒 = 16 刻。 */
    private static final int CHARGE_TICKS = 16;

    /** 蓄力时长（秒；文案用）。 */
    private static final String CHARGE_SECONDS = "0.8";

    /**
     * **主动维持"正在使用物品"状态时的刷新节拍（刻）**。
     *
     * <p>★ 这是本组件最关键的一条保护：我们为了让玩家看到**三叉戟的蓄力投出动作**，
     * 会主动调 {@code player.startUsingItem(HAND)}（见 {@link #onCast}）。但三叉戟的
     * {@code releaseUsing} 有一条判据 —— <b>"蓄力不足 10 刻则什么都不做"</b>：
     * <pre>
     * int i = getUseDuration(stack) - timeLeft;   // = 已经"使用"了多少刻
     * if (i &lt; 10) return;                          // ← 不足 10 刻 ⇒ 直接返回，什么都不做
     * if (riptide == 0) { 投掷三叉戟 }              // ← 否则会把武器当三叉戟扔出去！
     * </pre>
     * 若放着不管，玩家松手时 {@code i} 早已 ≥ 10 ⇒ **原版会把我们的武器投掷出去**
     * （生成投射物 + 物品 -1）。本工程每刻重绘热键栏虽能把物品补回来，但那个飞出去的投射物是错的。
     *
     * <p>因此蓄力期间每 {@value #HOLD_REFRESH_AT_TICKS} 刻做一次
     * {@code clearActiveItem() → startUsingItem(HAND)}：把"已使用刻数"重置回 0
     * ⇒ 松手时 {@code i} 恒 &lt; 10 ⇒ **原版的投掷分支永不执行**。
     * 而"举枪姿势"只取决于"是否正在使用"、与已使用刻数无关 ⇒ 姿势**不会中断**。
     *
     * <p>取 {@value #HOLD_REFRESH_AT_TICKS}（&lt; 10）是留出 4 刻余量，保证不踩到 10 刻那条线。
     */
    private static final int HOLD_REFRESH_AT_TICKS = 6;

    /**
     * **降级路径**用的"还在按住"判据：相邻两次右键事件的最大允许间隙（刻）。
     *
     * <p>只在 {@link #holdReliable} 为 {@code false}（{@code startUsingItem} 没生效、
     * 拿不到 {@code isHandRaised()}）时才用到 —— 那时改用"按住时 {@code PlayerInteractEvent}
     * 约每 4 刻重复派发 ⇒ 它停了就是松手"来间接推断。
     *
     * <p>★ 取 {@value #RELEASE_GAP_TICKS} 而不是 5 的理由：主武器右键要过
     * `MainWeaponListener#cast` 的 `isCoolingDown()` 闸门，而 `onAttack` 会启动 4 刻攻击冷却
     * ⇒ 冷却期内到达的右键事件被**吞掉**。容忍"漏掉一次刷新"（4 + 4 = 8 刻）才不会把
     * "还按着"误判成"松手"。
     *
     * <p>代价 = 松手后最多延迟这么多刻才真正突刺（0.4 秒）。若确认客户端刷新稳定，
     * 把它调到 5~6 会让手感更即时。
     */
    private static final int RELEASE_GAP_TICKS = 8;

    /**
     * 蓄力总时长上限（刻）：即使"蓄满后一直不松手"也强制出手（防呆，避免无限蓄力）。
     * <p>只在**蓄满之后**才计数（见 {@link #update()}），因此不影响"0.8 秒"这条需求。
     */
    private static final int CHARGE_MAX_TICKS = CHARGE_TICKS + 40;

    /** 蓄力音效的节拍（刻）：每 2 刻一个音符 ⇒ 16 刻共 8 个音（音高逐级升高）。 */
    private static final int CHARGE_NOTE_INTERVAL_TICKS = 2;

    /** 蓄力音效总音符数（= {@link #CHARGE_TICKS} / {@link #CHARGE_NOTE_INTERVAL_TICKS}）。 */
    private static final int CHARGE_NOTE_COUNT = CHARGE_TICKS / CHARGE_NOTE_INTERVAL_TICKS;

    /** 蓄力粒子环的起手半径（格）。 */
    private static final double CHARGE_RING_OUTER_RADIUS = 1.7d;

    /** 突进距离（格）：6 格。 */
    private static final double DASH_DISTANCE = 6d;

    /** 突进飞行时长（刻）：0.2 秒 = 4 刻。 */
    private static final int DASH_FLIGHT_TICKS = 4;

    /** 突进飞行时长（秒；文案用）。 */
    private static final String DASH_FLIGHT_SECONDS = "0.2";

    /** 突进单独冷却（刻）：3 秒 = 60 刻。 */
    private static final int DASH_COOLDOWN_TICKS = 60;

    /** 突进冷却（秒；文案用）。 */
    private static final String DASH_COOLDOWN_SECONDS = "3";

    /** 突进命中判定的半径（格）：枪身横扫的宽度。 */
    private static final double DASH_HIT_RADIUS = 1.4d;

    /** 突进每格推进时，沿途取样的间距（格）。 */
    private static final double DASH_SAMPLE_STEP = 1d;

    /**
     * **突进沿途命中造成的物理伤害**（需求：对沿途敌人造成伤害）。
     * <p>与普攻同值 —— 它是同一把「逆命天理」刺出的同一击。
     */
    private static final double DASH_PHYSICAL_DAMAGE = PHYSICAL_DAMAGE;

    /** **突进沿途命中造成的真实伤害**（同上）。 */
    private static final double DASH_TRUE_DAMAGE = TRUE_DAMAGE;

    // ───────── 依赖（start() 里一次查好）─────────

    private VitalsComponent vitals;
    private BuffComponent buff;
    private TekDestinyPassive destiny;

    // ───────── 运行期状态 ─────────

    /**
     * 是否正在蓄力（右键已按下、还没松手）。
     * <p>★ 蓄力期间**禁止攻击**（需求：蓄力过程中不可攻击）——见 {@link #onAttack}。
     */
    private boolean charging;

    /** 本次蓄力已累计的刻数。 */
    private int chargeTicks;

    /**
     * **蓄力是否已满**（累计 ≥ {@value #CHARGE_TICKS} 刻）。
     * <p>满了不等于出手：需求是"按住 0.8 秒后**再松开**才突刺" ⇒ 由松手触发，见 {@link #update()}。
     */
    private boolean chargeArmed;

    /**
     * **最近一次收到右键事件的刻**（`Bukkit.getCurrentTick()` 口径）。
     * <p>这是"还按着吗"的唯一判据：原版按住右键会约每 4 刻重复派发一次事件 ⇒
     * 用"距上次事件多久"推断松手（见 {@value #RELEASE_GAP_TICKS}）。
     * <p>★ **不能**用 {@code player.isHandRaised()}：本工程 listener 对右键一律
     * `setCancelled(true)` ⇒ 原版"开始使用物品"从未发生 ⇒ 它恒为 false。
     */
    private int lastRightClickTick;

    /**
     * **"还在按住"的判据是否可信**（= 我们主动 `startUsingItem` 是否真的生效了）。
     * <p>{@code true} ⇒ 直接用 {@code player.isHandRaised()} 判松手（**即时、可靠**）；
     * {@code false} ⇒ {@code startUsingItem} 没生效 ⇒ 降级到"click-chatter + 间隙"（见
     * {@link #RELEASE_GAP_TICKS}）。
     * <p>判定时点 = {@link #onCast} 调完 {@code startUsingItem} 的**同一刻**立刻读一次
     * {@code isHandRaised()} —— 它是同步生效的，不需要等下一 tick。
     */
    private boolean holdReliable;

    /**
     * 本次蓄力期间收到的右键事件**次数**（含按下的那一次）。
     * <p>只在降级路径（{@link #holdReliable} 为 {@code false}）里用：
     * 只有 {@code >= 2} 时"间隙判据"才可信（见 {@link #releaseDetectable()}）。
     */
    private int rightClickCount;

    /** 突进冷却的到期刻；{@code 0} = 就绪。 */
    private int dashCooldownUntilTick;

    /** 突进飞行剩余刻数（{@code > 0} 表示正在飞）。 */
    private int dashFlightRemaining;

    /** 突进方向（飞行期间每刻推进用）。 */
    private final Vector dashDirection = new Vector(0d, 0d, 1d);

    /** 突进每刻应推进的格数（= 距离 / 飞行刻数）。 */
    private double dashPerTick;

    /** 本次突进已经命中过的敌人（防止同一段突进反复打同一人）。 */
    private final List<Player> dashHit = new ArrayList<>();

    /**
     * 本轮冷却是否已经报过"就绪"。
     * <p>保证"冷却走完"只响一次（否则站着不动会每秒响一声）。
     */
    private boolean dashReadyAnnounced;

    public TekTridentMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends MainWeapon.Specification<TekTridentMainWeapon> {

        public Specification() {
            super(Component.text("逆命天理"),
                    List.of(Component.text("每次攻击造成 " + (int) PHYSICAL_DAMAGE + " 点物理伤害与 "
                                    + (int) TRUE_DAMAGE + " 点真实伤害，并附加一层「真理」"),
                            Component.text("攻击间隔 " + ATTACK_COOLDOWN_SECONDS + " 秒"),
                            Component.text("按住右键蓄力 " + CHARGE_SECONDS + " 秒，松开右键向前刺出："
                                    + DASH_FLIGHT_SECONDS + " 秒内突击 " + (int) DASH_DISTANCE + " 格"),
                            Component.text("突进沿途命中敌人：造成伤害、叠加真理、减少所有技能冷却、获得能量"),
                            Component.text("一次突进对同一名目标只造成一次伤害"),
                            Component.text("蓄力未满即松手则不出手；蓄力过程中无法攻击"),
                            Component.text("突进冷却 " + DASH_COOLDOWN_SECONDS + " 秒（冷却期间无法蓄力）")),
                    Material.TRIDENT,
                    ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(TekDestinyPassive.class).requires(FactionComponent.class);
        }

        @Override
        public TekTridentMainWeapon create(String id, ComponentServicesPort services) {
            return new TekTridentMainWeapon(id, services, this);
        }
    }

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        destiny = svc().components().get(TekDestinyPassive.class);
    }

    @Override
    public void stop() {
        //角色被清 ⇒ 顺手结束可能还挂着的"使用物品"状态（否则举枪姿势会残留在玩家身上）
        endUseState(svc().self().player());
        charging = false;
        chargeTicks = 0;
        chargeArmed = false;
        holdReliable = false;
        rightClickCount = 0;
        lastRightClickTick = 0;
        dashFlightRemaining = 0;
        dashCooldownUntilTick = 0;
        dashReadyAnnounced = false;
        dashHit.clear();
    }

    // ───────── 物品画法：先 super，再叠攻速 + 突进就绪光效 ─────────

    /**
     * 物品实例：先 {@code super.buildItem()}（识别键 / 名称 / lore / 三态材质全在基类那一步），
     * 再叠加①攻速修饰符 ②突进就绪的常亮光效。
     * <p>★ 覆写口径：先调 {@code super.buildItem()}，再在其上后处理 —— 识别键必须保留，
     * 否则点击无反应、角色清除时物品残留。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        //① 攻速统一 100（先移除再添加，不去重会叠加）
        applyAttackSpeed(meta);
        //② 突进就绪 ⇒ 常亮附魔光效（需求：CD 完备后给武器附魔提示）
        if (isDashReady() && canUse()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** 攻速统一 100：先移除再添加（不去重会叠加成 100/200/300）。 */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    // ───────── 攻击：4 物理 + 4 真实 + 一层真理 ─────────

    @Override
    public void onAttack(AttackSignal signal) {
        //★ 蓄力中不可攻击（需求）—— 放在最前，连冷却都不消耗：蓄力期间这一次攻击完全不成立
        if (charging) {
            return;
        }
        //★ 主武器必须自判冷却（派发侧不替主武器挡），否则 0.2 秒只是纸面数字
        if (isCoolingDown()) {
            return;
        }
        if (!canUse()) {
            return;
        }
        Player owner = svc().self().player();
        Player victim = signal == null ? null : signal.victim();
        if (owner == null || victim == null || vitals == null) {
            return;
        }

        //① 4 点物理伤害（带来源 ⇒ 走物理结算）
        vitals.physicalDamage(victim, owner, PHYSICAL_DAMAGE);
        //② 4 点真实伤害（需求：真实伤害）
        vitals.trueDamage(victim, owner, TRUE_DAMAGE);
        //★ 连打必需（组件侧，不动框架）：扣血之后再清一次受击无敌帧。
        //  原因见 CangluTraumaMainWeapon#clearHitInterval 的说明 ——
        //  DamageUtil 只在扣血【前】清一次，而 damage() 结算后会把 noDamageTicks 设回 20 刻，
        //  那 20 刻内若目标先被"不经本插件伤害入口"的来源打中，我们下一击仍会被吞。
        victim.setNoDamageTicks(0);
        //③ 一层真理 + 被动结算（减 CD / 得能量 / 阈值护盾）
        if (destiny != null) {
            destiny.onHit(victim);
        } else {
            TekTruth.addOne(victim.getUniqueId());
        }

        World world = owner.getWorld();
        if (world != null) {
            TekSound.tridentHitSound(world, victim.getLocation());
            TekSound.tridentTrueHitSound(world, victim.getLocation());
        }
        //④ 结算成功 ⇒ 启动主武器冷却
        startCooldown();
    }

    // ───────── 右键：按下（开始蓄力）/ 按住（刷新"还在按"的心跳）─────────

    /**
     * 右键 = **按下**（开始蓄力 / 举起三叉戟）或 **按住中的一次心跳**。
     *
     * <p>★ 关键动作：按下时**主动调 {@code player.startUsingItem(HAND)}**
     * —— 这一步同时解决两件事：
     * <ol>
     *   <li><b>播放三叉戟的蓄力投出动作</b>（需求）：玩家的"举枪蓄力"姿势由
     *       "是否正在使用物品"驱动，原版右键又被本工程 listener 取消（原版那条路走不通），
     *       因此必须我们自己把使用状态起起来；</li>
     *   <li><b>把"手还举着吗"变成可靠判据</b>：一旦在"使用中"，
     *       {@code player.isHandRaised()} 就为 {@code true}，松手时服务端处理
     *       RELEASE_USE_ITEM ⇒ 使用状态结束 ⇒ 它立刻变 {@code false}
     *       ⇒ **松手可以被即时、直接观测**（不再需要靠间隙猜）。</li>
     * </ol>
     *
     * <p>同一刻立刻读一次 {@code isHandRaised()} 作为**探针**：为 {@code true} 则
     * {@link #holdReliable} 置真；为 {@code false} 说明 {@code startUsingItem} 没生效
     * ⇒ 自动降级到"click-chatter + 间隙"（技能仍然可用，只是没有举枪姿势）。
     *
     * <p>已在蓄力时到达的右键 = 按住中的心跳：<b>只累加计数，不重置蓄力进度</b>
     * （重置会让"按住"永远凑不满 0.8 秒）。降级路径要靠它判断"客户端还在重复派发"。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal == null || signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !canUse()) {
            return;
        }
        //★ 突刺冷却中（含正在飞行）⇒ **完全不响应右键**（需求）：
        //  不能蓄力、不能举起武器（不 startUsingItem ⇒ 没有举枪姿势，也不会有蓄力粒子）。
        //  刻意放在最前面：只要冷却没走完，连"举起"这一步都不发生。
        if (!isDashReady()) {
            return;
        }

        lastRightClickTick = org.bukkit.Bukkit.getCurrentTick();

        if (charging) {
            //★ 按住中的心跳：只累加计数（时间戳上面那行已刷），**不重置 chargeTicks**
            rightClickCount++;
            return;
        }

        startCharge(owner);
    }

    /**
     * **起手蓄力**：起使用状态（举枪姿势 + 松手可观测）、初始化计数、播起手音。
     */
    private void startCharge(Player owner) {
        charging = true;
        chargeArmed = false;
        chargeTicks = 0;
        rightClickCount = 1;

        //★ 主动起"使用物品"状态 ⇒ 三叉戟举枪姿势 + isHandRaised() 生效
        owner.startUsingItem(org.bukkit.inventory.EquipmentSlot.HAND);
        //同一刻探针：同步生效则 isHandRaised() 立刻为 true
        holdReliable = owner.isHandRaised();

        World world = owner.getWorld();
        if (world != null) {
            TekSound.chargeStartSound(world, owner.getLocation());
        }
    }

    /**
     * **结束"使用物品"状态**（松手 / 出手 / 作废时调）。
     * <p>必要动作：不清会让举枪姿势留在身上，且使用状态会被原版当成"还在蓄力"。
     */
    private void endUseState(Player owner) {
        if (owner != null && owner.isHandRaised()) {
            owner.clearActiveItem();
        }
    }

    // ───────── 每 tick：蓄力计时 + 突进飞行 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            charging = false;
            dashFlightRemaining = 0;
            return;
        }

        //① 突进飞行优先（飞行期间不蓄力）
        if (dashFlightRemaining > 0) {
            advanceDash(owner);
            return;
        }

        //② 蓄力计时 + 松手判定 + 反馈（音效 / 粒子）
        if (!charging) {
            //就绪提示音（节流）：只在自己身上、且冷却刚走完时轻轻响一下
            maybePlayReadySound(owner);
            return;
        }

        //②-0 ★ 突刺冷却中 ⇒ 不允许处于蓄力状态（需求：CD 中不能举高、不出粒子、不能蓄力）。
        //     正常路径由 onCast 的 `!isDashReady()` 挡住，这里是**防御性**兜底：
        //     万一冷却在蓄力期间才开始（当前实现不会），也要立刻收掉举枪姿势与粒子。
        if (!isDashReady()) {
            abortCharge(owner);
            return;
        }

        //②-a 中途换手 / 丢下武器 ⇒ 立即作废（便宜且无歧义的判据，先判）
        if (!isHoldingWeapon(owner)) {
            abortCharge(owner);
            return;
        }

        chargeTicks++;
        int now = org.bukkit.Bukkit.getCurrentTick();
        long gap = now - lastRightClickTick;

        //②-b **保持举枪姿势 + 封住原版投掷**：见 HOLD_REFRESH_AT_TICKS 的说明
        if (holdReliable) {
            if (owner.getActiveItemUsedTime() >= HOLD_REFRESH_AT_TICKS) {
                owner.clearActiveItem();
                owner.startUsingItem(org.bukkit.inventory.EquipmentSlot.HAND);
            }
        }

        //②-c 满 0.8 秒 ⇒ 标记"已蓄满（等待松手）"（排在反馈之前，避免同帧两个音重叠）
        if (!chargeArmed && chargeTicks >= CHARGE_TICKS) {
            chargeArmed = true;
            World world = owner.getWorld();
            if (world != null) {
                TekSound.chargeArmedSound(world, owner.getLocation());
            }
        }

        //②-d 蓄力反馈：粒子（每刻）+ 音效（每 CHARGE_NOTE_INTERVAL_TICKS 刻一个更高音）
        chargeFeedback(owner, gap);

        //②-e **松手判定**
        //     主判据（holdReliable）= isHandRaised()：我们主动起了使用状态，松手即刻可观测；
        //     降级判据 = click-chatter + 间隙（startUsingItem 没生效时兜底）
        boolean released;
        if (holdReliable) {
            released = !owner.isHandRaised();
        } else {
            released = releaseDetectable() && gap > RELEASE_GAP_TICKS;
        }

        //②-f 交给**纯函数**做判定（可离线单测；规则见 {@link #decideCharge}）
        ChargeDecision decision = decideCharge(chargeTicks, chargeArmed, released);
        if (decision == ChargeDecision.FIRE) {
            charging = false;
            chargeArmed = false;
            chargeTicks = 0;
            endUseState(owner);
            launchDash(owner);
        } else if (decision == ChargeDecision.ABORT) {
            abortCharge(owner);
        }
    }

    /** 一帧蓄力的判定结果（**纯函数**，见 {@link #decideCharge}）。 */
    enum ChargeDecision {
        /** 继续蓄力 / 继续等待松手。 */
        CONTINUE,
        /** 出手：向前突刺。 */
        FIRE,
        /** 作废：本次蓄力不成立（没蓄满就松手 / 换手等）。 */
        ABORT
    }

    /**
     * **蓄力状态机的判定核心**（纯函数 ⇒ 可离线单测，见 {@code TekChargeDecisionTest}）。
     *
     * <p>规则只有两条：
     * <ol>
     *   <li><b>还在按着</b>（{@code !released}）：
     *       已蓄满且超过 {@link #CHARGE_MAX_TICKS} ⇒ 强制出手（防呆，避免无限蓄力）；否则继续等；</li>
     *   <li><b>松手了</b>（{@code released}）：蓄满 ⇒ <b>出手</b>（需求要的就是这条）；
     *       没蓄满 ⇒ <b>作废</b>（提前松手不成立）。</li>
     * </ol>
     *
     * @param chargeTicks 已累计刻数
     * @param armed       是否已蓄满（调用方在"跨过 CHARGE_TICKS"那一帧置位）
     * @param released    是否已松手（判据由调用方给出：优先 {@code isHandRaised()}，降级 chatter+间隙）
     */
    static ChargeDecision decideCharge(int chargeTicks, boolean armed, boolean released) {
        if (released) {
            return armed ? ChargeDecision.FIRE : ChargeDecision.ABORT;
        }
        if (armed && chargeTicks >= CHARGE_MAX_TICKS) {
            return ChargeDecision.FIRE;
        }
        return ChargeDecision.CONTINUE;
    }

    /** 暴露给单测的阈值（避免测试硬编码魔数而与实现漂移）。 */
    static int chargeTicksForTest() {
        return CHARGE_TICKS;
    }

    /** 暴露给单测的阈值（同上）。 */
    static int releaseGapTicksForTest() {
        return RELEASE_GAP_TICKS;
    }

    /** 暴露给单测的阈值（同上）。 */
    static int chargeMaxTicksForTest() {
        return CHARGE_MAX_TICKS;
    }

    /** 暴露给单测的阈值（同上；必须 < 10，见 {@link #HOLD_REFRESH_AT_TICKS}）。 */
    static int holdRefreshAtTicksForTest() {
        return HOLD_REFRESH_AT_TICKS;
    }

    /**
     * **降级路径的"间隙判据是否可信"**：本次蓄力期间是否**至少收到过两次**右键事件。
     *
     * <p>只在 {@link #holdReliable} 为 {@code false} 时才会被问到。
     * 只收到一次 ⇒ 客户端不重复派发（或刷新被冷却吞光）⇒ 无法区分"按住"与"已松手"
     * ⇒ 这时**不作**松手判定（宁可让它继续蓄下去，也不要误判成提前松手而作废）。
     */
    private boolean releaseDetectable() {
        return rightClickCount >= 2;
    }

    /**
     * **蓄力反馈**（需求：蓄力过程中会有蓄力音效以及粒子）。
     *
     * @param gap 距上次右键事件的刻数（用来判断"还按着"；间隙超过阈值就不再续音，避免空响）
     */
    private void chargeFeedback(Player owner, long gap) {
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        double progress = Math.min(1d, chargeTicks / (double) CHARGE_TICKS);
        Location center = owner.getLocation().clone();

        //① 粒子：每刻画一圈由外向内收敛的能量（越满越密越亮）
        double phase = chargeTicks * 0.45d;
        TekVfx.chargeConverge(world, center, progress, phase, CHARGE_RING_OUTER_RADIUS);

        //② 音效：每 CHARGE_NOTE_INTERVAL_TICKS 刻一个音，音高逐级升高（未满期间才续音）
        if (!chargeArmed
                && chargeTicks % CHARGE_NOTE_INTERVAL_TICKS == 0
                && gap <= RELEASE_GAP_TICKS) {
            int index = chargeTicks / CHARGE_NOTE_INTERVAL_TICKS;
            TekSound.chargeUpNote(world, center, index, CHARGE_NOTE_COUNT);
        }
    }

    /** **作废本次蓄力**：结束使用状态 + 清状态 + 一声落空音（不进冷却）。 */
    private void abortCharge(Player owner) {
        boolean wasArmed = chargeArmed;
        charging = false;
        chargeArmed = false;
        chargeTicks = 0;
        endUseState(owner);
        if (!wasArmed && owner != null) {
            World world = owner.getWorld();
            if (world != null) {
                TekSound.chargeAbortSound(world, owner.getLocation());
            }
        }
    }

    /** 主手是否仍拿着本武器（蓄力期间换手 / 丢下 ⇒ 本次蓄力作废）。 */
    private static boolean isHoldingWeapon(Player owner) {
        ItemStack held = owner.getInventory().getItemInMainHand();
        return held != null && !held.getType().isAir() && Utils.isMainWeapon(held)
                && ID.equals(Utils.getWeaponId(held));
    }

    // ───────── 突进 ─────────

    /**
     * **发起突进**（需求：向前刺出长枪，0.2 秒内突击 6 格）。
     *
     * <p>突进方向 = 当前**视线水平方向**（抬头不该往天上窜）。
     */
    private void launchDash(Player owner) {
        if (!isDashReady()) {
            return;
        }
        Vector look = owner.getEyeLocation().getDirection();
        Vector flat = new Vector(look.getX(), 0d, look.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            Vector body = owner.getLocation().getDirection();
            flat = new Vector(body.getX(), 0d, body.getZ());
        }
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        dashDirection.copy(flat.normalize());
        dashHit.clear();
        dashPerTick = DASH_DISTANCE / Math.max(1, DASH_FLIGHT_TICKS);
        dashFlightRemaining = DASH_FLIGHT_TICKS;
        //突进冷却从此刻起算
        dashCooldownUntilTick = org.bukkit.Bukkit.getCurrentTick() + DASH_COOLDOWN_TICKS;

        World world = owner.getWorld();
        if (world != null) {
            TekSound.dashLaunchSound(world, owner.getLocation());
        }
    }

    /**
     * **推进一帧突进**：按 {@link #dashPerTick} 向前平移，沿途取样命中敌人，飞完即停。
     *
     * <p>用 {@code teleport}（而非 {@code setVelocity}）保证"0.2 秒整 6 格"的确定性 ——
     * 速度推进会被地形摩擦吃掉距离。
     **/
    private void advanceDash(Player owner) {
        World world = owner.getWorld();
        if (world == null) {
            dashFlightRemaining = 0;
            return;
        }
        Location from = owner.getLocation().clone();
        Location to = from.clone().add(dashDirection.clone().multiply(dashPerTick));

        //沿途取样：每 DASH_SAMPLE_STEP 格取一个点，命中该点附近的敌人
        double travelled = dashPerTick;
        for (double d = 0d; d <= travelled; d += DASH_SAMPLE_STEP) {
            Location sample = from.clone().add(dashDirection.clone().multiply(d));
            hitAround(owner, sample);
        }

        owner.teleport(to);
        TekVfx.dashTrail(world, from, 0.35d);
        dashFlightRemaining--;
        if (dashFlightRemaining <= 0) {
            dashFlightRemaining = 0;
            dashHit.clear();
        }
    }

    /**
     * **突进命中判定**：半径 {@value #DASH_HIT_RADIUS} 内的敌人 ⇒
     * <b>①造成伤害 ②触发被动</b>（叠真理 + 减技能 CD + 得能量）。
     *
     * <h2>★「一次突进仅仅对同一名目标造成一次伤害」怎么保证</h2>
     * 突进飞行 {@value #DASH_FLIGHT_TICKS} 刻、每刻推进 {@code DASH_DISTANCE / DASH_FLIGHT_TICKS} 格，
     * 且每刻还按 {@value #DASH_SAMPLE_STEP} 格的间距取多个样点 ⇒ 同一个敌人会被**多次**落到判定里。
     * 用 {@link #dashHit} 记录"本次突进已经打过的目标"来去重：
     * <ul>
     *   <li>在 {@link #launchDash} 里 {@code clear()} ⇒ 每次突进是干净的一份；</li>
     *   <li>飞行结束再 {@code clear()}（防御性，避免跨突进残留）；</li>
     *   <li>命中前先查 {@code contains} ⇒ 已在名单里就直接跳过。</li>
     * </ul>
     *
     * <p>顺序也重要：<b>先去重、再造成伤害</b>。反过来的话，同一目标会被结算两次伤害。
     */
    private void hitAround(Player owner, Location at) {
        if (vitals == null) {
            return;
        }
        for (Player candidate : at.getNearbyPlayers(DASH_HIT_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            //★ 去重必须排在伤害之前（否则同一目标会被打两次）
            if (dashHit.contains(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            dashHit.add(candidate);

            //① **沿途造成伤害**（需求：对沿途敌人造成伤害）
            vitals.physicalDamage(candidate, owner, DASH_PHYSICAL_DAMAGE);
            vitals.trueDamage(candidate, owner, DASH_TRUE_DAMAGE);

            //② 触发被动（叠真理 + 减技能 CD + 得能量）
            if (destiny != null) {
                destiny.onHit(candidate);
            } else {
                TekTruth.addOne(candidate.getUniqueId());
            }

            World world = at.getWorld();
            if (world != null) {
                TekSound.dashImpactSound(world, candidate.getLocation());
                TekVfx.truthStripped(world, candidate.getLocation().clone().add(0d, 1d, 0d),
                        TekTruth.layersOf(candidate.getUniqueId()));
            }
        }
    }

    // ───────── 就绪与提示 ─────────

    /** 突进是否就绪（冷却已走完且不在飞行中）。 */
    public boolean isDashReady() {
        return dashFlightRemaining <= 0 && org.bukkit.Bukkit.getCurrentTick() >= dashCooldownUntilTick;
    }

    /** 突进剩余冷却刻数（0 = 就绪）。 */
    public int dashRemainingTicks() {
        return Math.max(0, dashCooldownUntilTick - org.bukkit.Bukkit.getCurrentTick());
    }

    /** 是否正在蓄力（按下且未松手）。 */
    public boolean isCharging() {
        return charging;
    }

    /** 蓄力是否已满（≥ 0.8 秒，等松手）。 */
    public boolean isChargeArmed() {
        return chargeArmed;
    }

    /** 本次蓄力已累计刻数。 */
    public int chargeTicks() {
        return chargeTicks;
    }

    /**
     * **降级路径的间隙判据是否可信**（探测读口）：本次蓄力是否收到过 ≥2 次右键事件。
     * <p>只在 {@link #holdReliable} 为 {@code false}（即走降级路径）时才有意义。
     * 排障时先看 {@code holdReliable}：它为真 ⇒ 这个读数与松手判定无关。
     */
    public boolean isReleaseDetectable() {
        return charging && releaseDetectable();
    }

    /** 是否正在突进飞行。 */
    public boolean isDashing() {
        return dashFlightRemaining > 0;
    }

    /**
     * 就绪提示音：**每次冷却是只响一次**（冷却进行中不响；走完的那一瞬响一下）。
     *
     * <p>★ 不能用"每 N 刻节流重播"——那会让玩家站着不动时**每秒响一声**，噪音。
     * 用 {@link #dashReadyAnnounced} 记住"这一轮已经报过就绪了"。
     */
    private void maybePlayReadySound(Player owner) {
        if (!isDashReady() || !canUse()) {
            //冷却进行中 / 被禁用 ⇒ 重置播报位，让下一轮走完时还能报一次
            dashReadyAnnounced = false;
            return;
        }
        if (dashReadyAnnounced) {
            return;
        }
        dashReadyAnnounced = true;
        World world = owner.getWorld();
        if (world != null) {
            TekSound.dashReadySound(world, owner.getLocation());
        }
    }

    // ───────── 操作面（排障 / 探针）─────────

    /**
     * 组件操作面（照仓库既有先例：把字符串指令薄适配到既有强类型方法）。
     *
     * <pre>
     * state   读：charging=false charge=0/16 armed=false holdReliable=true held=false clicks=0
     *              dashReady=true dashCd=0 flying=false
     * ready   写：把突进冷却直接清零（调试用）；回写后的 state
     * </pre>
     *
     * <p>{@code holdReliable} / {@code held} 专门用于排障"举枪姿势与松手判定是否生效"：
     * 若 {@code holdReliable=false}，说明 {@code startUsingItem} 没生效 ⇒
     * 既没有举枪姿势，松手判定也走了 click-chatter 降级路径（{@code clicks} 会说明客户端有没有重复派发）。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) {
            return null;
        }
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return null;
        }
        switch (tokens[0]) {
            case "state" -> {
                return tokens.length == 1 ? stateText() : null;
            }
            case "ready" -> {
                if (tokens.length != 1) {
                    return null;
                }
                dashCooldownUntilTick = 0;
                return stateText();
            }
            default -> {
                return null;
            }
        }
    }

    /** 一行状态文本（排障 / 探针读口）。 */
    private String stateText() {
        Player owner = svc().self().player();
        return "charging=" + charging
                + " charge=" + chargeTicks + "/" + CHARGE_TICKS
                + " armed=" + chargeArmed
                + " holdReliable=" + holdReliable
                + " held=" + (owner != null && owner.isHandRaised())
                + " clicks=" + rightClickCount
                + " gapThresh=" + RELEASE_GAP_TICKS
                + " dashReady=" + isDashReady()
                + " dashCd=" + dashRemainingTicks()
                + " flying=" + isDashing();
    }

    /** 存活判定（死亡 / 死亡界面 / 已下线一律排除）。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    @Override
    protected boolean canUse() {
        //主武器闸门 = 非眩晕（与其余主武器同口径）
        return buff != null && buff.canUseMainWeapon();
    }

    /** 供探针确认依赖已挂上（{@code null} 表示 start() 尚未跑）。 */
    VitalsComponent dependencyProbe() {
        return vitals;
    }
}
