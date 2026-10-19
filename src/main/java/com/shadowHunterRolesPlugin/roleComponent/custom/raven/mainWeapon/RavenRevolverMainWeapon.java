package com.shadowHunterRolesPlugin.roleComponent.custom.raven.mainWeapon;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenAirMobilityPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenFloatingTextComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 「绯红渡鸦 · 林晨霜」主武器：**散华『改』**（模型弩）。
 *
 * <h2>需求逐条（近战）</h2>
 * <ol>
 *   <li>造成 <b>12 点物理伤害</b>与 <b>3~6 点特殊值伤害</b>；</li>
 *   <li>左键攻击 CD <b>0.4 秒</b>（2026-10-11 由 0.6 秒下调）；</li>
 *   <li><b>击退等级 2</b>。</li>
 * </ol>
 *
 * <h2>需求逐条（地面右键射击）</h2>
 * <ol>
 *   <li>发射一枚<b>全威力弹</b>，弹匣共 {@value #MAGAZINE_SIZE} 枚；</li>
 *   <li>每 <b>2 秒</b>自动消耗 <b>2 能量</b>回复 1 枚（换弹期间暂停自动回复）；</li>
 *   <li>击发间隔 <b>0.7 秒</b>；命中 <b>10 点物理 + 3~6 点特殊值</b>；</li>
 *   <li>子弹<b>20 格每秒</b>飞行，<b>撞墙消失</b>；</li>
 *   <li>★ 2026-10-11：<b>物品耐久条额外表达弹药量</b>（见 {@link #ammoDamage(int, int, int)}）。</li>
 * </ol>
 *
 * <h2>需求逐条（地面丢出 = 换弹）</h2>
 * <ol>
 *   <li>每发 <b>0.5 秒</b>，每装一发消耗 <b>3 能量</b>；</li>
 *   <li>换弹期间<b>不能右键射击</b>，但<b>能左键近战</b>；<b>左键即打断换弹</b>。</li>
 * </ol>
 *
 * <h2>需求逐条（浮空机动中的锁定射击）</h2>
 * <ol>
 *   <li>武器切换至<b>锁定状态</b>；右键在 <b>0.8 秒</b>内蓄力后立即锁定<b>视野内 20 格</b>中的敌人，
 *       轮流射出共 <b>3 枚</b>（每发间隔 <b>0.1 秒</b>）；</li>
 *   <li>锁定顺序 = <b>离准心最近 → 第二近 → 第三近</b>；不足 3 人则<b>从头再轮</b>；</li>
 *   <li>★ 2026-10-11：锁定标记改为 <b>0.25 秒闪烁一次</b>（{@link #isLockBlinkOn(int)}），
 *       并在<b>锁定成功时给提示音</b>；</li>
 *   <li>蓄力判定<b>周围无敌人</b> ⇒ 直接以准心射出<b>一发</b>；</li>
 *   <li>空中<b>左键</b> = 切换瞄准模式（锁定 / 单发），提示音 = <b>经验球的"叮"</b>；</li>
 *   <li>空中<b>丢出</b> = 快装 2 发 + <b>0.2 秒内前冲 8 格</b> + 刷新浮空机动
 *       （耗 1 层机动，间隔 <b>1 秒</b>）。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：三个计时器互不共用</h2>
 * <ul>
 *   <li><b>近战</b>用组件自带的冷却（{@code startCooldown()}，声明值 {@value #MELEE_COOLDOWN_TICKS} 刻）；</li>
 *   <li><b>射击</b>用本类自己的 {@link #nextShotTick}（{@value #SHOT_COOLDOWN_TICKS} 刻）；</li>
 *   <li><b>空中机动</b>用 {@link #nextAirDashTick}（{@value #AIR_DASH_INTERVAL_TICKS} 刻 = 1 秒）。</li>
 * </ul>
 * ★ <b>代价（如实申报）</b>：施放管道会检查 {@code isCoolingDown()} ⇒ 近战命中后的 0.4 秒内
 * 右键 / 左键 / Q 都打不出（含换弹与空中机动）。要彻底解耦必须改框架（禁改区）。
 * ★ 2026-10-11 把近战 CD 从 0.6 秒下调到 0.4 秒后，这条残留代价**比射击间隔（0.7 秒）更小**
 * ⇒ 实际上不再构成可感的损失。
 *
 * <h2>★★ 口径申报二：子弹是**逐刻行进的弹体**，不是命中扫描</h2>
 * 需求给了"飞行速度 20 格每秒 + 撞墙消失" ⇒ 本类在 {@code update()} 里维护一张**活子弹表**，
 * 每刻推进 {@value #BULLET_SPEED_BLOCKS_PER_TICK} 格并逐刻判"撞墙 / 命中 / 超程"。
 * ★ 逐刻行进**不需要**任何调度器 / 投射物实体 ⇒ 组件不必自挂 Listener（工程铁律）。
 *
 * <h2>★★ 口径申报三：空中"前冲 8 格"是 0.2 秒的**逐刻位移**</h2>
 * 需求 2026-10-11："加入在 0.2 秒内突进 8 格" ⇒ 拆成 {@value #AIR_DASH_STEPS} 刻、
 * 每刻 {@value #AIR_DASH_STEP_PER_TICK} 格（= 8 / 4），逐刻 {@code teleport}。
 * 期间由被动侧的 {@code beginAirDash()} / {@code endAirDash()} 把悬停物理挂起
 * （否则"每刻写速度"与"每刻改位置"会互相打架），结束时顺手刷新浮空持续时间。
 *
 * <h2>★★ 口径申报四：名称与状态色的取舍</h2>
 * 需求 2026-10-11 指定主武器名为<b>加粗斜体 · 暗红「散华」+ 金黄「『改』」</b>，
 * 而基类会把整个名字按状态染成绿 / 灰 / 红。两者冲突 ⇒ 本类**整体接管名称**（保留基类的三态材质
 * 与 lore），状态改用**后缀文字**表达（{@code " DISABLED"} / {@code " x.xs"} / {@code " 换弹中"} / {@code " 空仓"}）。
 *
 * <h2>★★ 口径申报五："用主武器附魔与否来表示机动是否触发"</h2>
 * 需求括注的落点 = 物品的**附魔光效**：{@link #buildItem()} 里
 * {@code setEnchantmentGlintOverride(机动有可用层数)}。
 */
public class RavenRevolverMainWeapon extends MainWeapon {

    /** 本组件的登记 id。 */
    public static final String ID = "raven_revolver_main_weapon";

    // ───────── 近战 ─────────

    /** 近战冷却（刻）—— 需求 2026-10-11："近战攻击冷却缩减至 0.4 秒" ⇒ 8 刻。 */
    public static final int MELEE_COOLDOWN_TICKS = 8;

    /** 攻击速度统一值（压掉原版攻击冷却）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速修饰符的键（先摘后加 ⇒ 反复重绘不叠加）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("raven_revolver_attack_speed");

    /** 近战物理伤害（需求：12 点）。 */
    public static final double MELEE_PHYSICAL_DAMAGE = 12d;

    /** 近战特殊值伤害区间（需求："3~6 点"，两端都含）。 */
    public static final int MELEE_SANTE_MIN = 3;
    public static final int MELEE_SANTE_MAX = 6;

    /** 近战击退强度（需求："击退等级 2"）。 */
    public static final double MELEE_KNOCKBACK_STRENGTH = 2d;

    // ───────── 射击 ─────────

    /** 弹匣容量（需求："六枚弹药"）。 */
    public static final int MAGAZINE_SIZE = 6;

    /** 击发间隔（刻）—— 需求"每次击发时间 cd 为 0.7s"。 */
    public static final int SHOT_COOLDOWN_TICKS = 14;

    /** 弹药自动回复周期（刻）—— 需求"每 2 秒自动回复 1 枚"（耗能见 {@link #AMMO_REGEN_ENERGY_COST}）。 */
    public static final int AMMO_REGEN_TICKS = 40;

    /**
     * 每次**自动回复**弹药的能量消耗——需求 2026-10-11 改为 **3 能量**（原先 2）。
     * <p>★ 只管"每 2 秒自动回 1 枚"这一条；**地面换弹**（丢出武器）仍是每发
     * {@link #RELOAD_ENERGY_PER_BULLET} 能量 —— 需求这次只提了"子弹回复一颗的耗能"。
     */
    public static final int AMMO_REGEN_ENERGY_COST = 3;

    /** 子弹速度（格/刻）—— 需求"20 格每秒" ⇒ 20/20 = 1。 */
    public static final double BULLET_SPEED_BLOCKS_PER_TICK = 1.0d;

    /** 子弹最大射程（格）—— 需求未给，取一个不夸张的远射上限（口径申报）。 */
    public static final double BULLET_MAX_RANGE = 40.0d;

    /** 子弹命中半径（格）—— 与玩家碰撞箱宽度同量级。 */
    public static final double BULLET_HIT_RADIUS = 0.75d;

    /** 射击物理伤害（需求 2026-10-11 改为：**10 点**；原先 12 点）。 */
    public static final double BULLET_PHYSICAL_DAMAGE = 10d;

    /** 射击特殊值伤害区间（需求："3~6 点"）。 */
    public static final int BULLET_SANTE_MIN = 3;
    public static final int BULLET_SANTE_MAX = 6;

    // ───────── 换弹（地面丢出）─────────

    /** 每装一发的耗时（刻）—— 需求"换弹时间为 0.5S 每发"（耗能见 {@link #RELOAD_ENERGY_PER_BULLET}）。 */
    public static final int RELOAD_TICKS_PER_BULLET = 10;

    /** 每装一发的能量消耗——需求 2026-10-11 改为 **3 能量**（原先 2）。 */
    public static final int RELOAD_ENERGY_PER_BULLET = 3;

    // ───────── 空中机动（空中丢出）─────────

    /** 前冲距离（格）—— 需求"向前位移 8 格"。 */
    public static final double AIR_DASH_DISTANCE = 8.0d;

    /** 前冲的采样步长（格）。 */
    public static final double AIR_DASH_STEP = 0.5d;

    /** 前冲耗时（刻）—— 需求 2026-10-11："在 0.2 秒内突进 8 格" ⇒ 4 刻。 */
    public static final int AIR_DASH_STEPS = 4;

    /** 每刻位移（格）＝ 8 / 4。 */
    public static final double AIR_DASH_STEP_PER_TICK = AIR_DASH_DISTANCE / AIR_DASH_STEPS;

    /** 快装弹药数（需求："快速装填 2 枚子弹"）。 */
    public static final int AIR_QUICK_LOAD_AMOUNT = 2;

    // ───────── 锁定射击 ─────────

    /** 蓄力时长（刻）—— 需求"在 0.8S 内蓄力"。 */
    public static final int LOCK_CHARGE_TICKS = 16;

    /** 一轮锁定的射出枚数（需求："共 3 枚"）。 */
    public static final int LOCK_SHOTS = 3;

    /** 锁定射击的每发间隔（刻）—— 需求"每发间隔 0.1S"。 */
    public static final int LOCK_SHOT_INTERVAL_TICKS = 2;

    /** 锁定索敌半径（格）—— 需求"20 格范围内"。 */
    public static final double LOCK_RANGE = 20.0d;

    /** 锁定视野半角（度）—— 需求"在视野的"；取 50° 半角（合计 100° 视锥）。 */
    public static final double LOCK_FOV_HALF_DEGREES = 50.0d;

    /** 锁定标记的闪烁周期（刻）—— 需求 2026-10-11："0.25 秒闪烁一次" ⇒ 5 刻。 */
    public static final int LOCK_BLINK_PERIOD_TICKS = 5;

    /** 每个周期里"亮"的刻数（亮 2 灭 3 ⇒ 看得见又明显在闪）。 */
    public static final int LOCK_BLINK_ON_TICKS = 2;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private EnergyComponent energy;
    private FactionComponent faction;
    private RavenAirMobilityPassive flight;

    /** 语音组件（命中 / 主动机动 / 无人可锁 四栏台词）。 */
    private RavenFloatingTextComponent voice;

    /** 渲染组件（**只用来请求重绘**）：弹药 / 换弹 / 机动层数 / 耐久都在非冷却期变化。 */
    private HotbarRenderComponent render;

    private final Random random = new Random();

    // ───────── 运行期状态 ─────────

    /** 当前弹药。 */
    private int ammo = MAGAZINE_SIZE;

    /** 下一发的可用刻（射击间隔）。 */
    private int nextShotTick;

    /** 弹药自动回复节拍。 */
    private int ammoRegenTick;

    /** 换弹中？ */
    private boolean reloading;

    /** 换弹节拍（每 {@value #RELOAD_TICKS_PER_BULLET} 刻装一发）。 */
    private int reloadTick;

    /** 蓄力中？（锁定模式） */
    private boolean charging;

    /** 蓄力到期刻。 */
    private int chargeUntilTick;

    /** 待射连发：剩余发数。 */
    private int volleyRemaining;

    /** 待射连发：下一发时刻。 */
    private int volleyNextShotTick;

    /** 待射连发：已射出第几发（用来按顺序轮转目标）。 */
    private int volleyShotIndex;

    /** 待射连发：目标名单（可空 = 以准心直射）。 */
    private List<Player> volleyTargets = List.of();

    /** 待射连发：命中是否给射手回血（技能二"每发弹药击中敌人将额外回复自己 2 点生命"）。 */
    private boolean volleyHealOnHit;

    /** 活子弹表（逐刻推进）。 */
    private final List<Bullet> bullets = new ArrayList<>();

    /** 空中主动机动的到期刻（1 秒闸门）。 */
    private int nextAirDashTick;

    // ── 空中前冲（0.2 秒、逐刻位移）──

    /** 剩余刻数（{@code 0} = 没有进行中的前冲）。 */
    private int airDashRemaining;

    /** 前冲的起点（每刻据它 + 步长算当前落点）。 */
    private Location airDashFrom;

    /** 每刻位移向量（终点 − 起点，再除以步数）。 */
    private Vector airDashStep = new Vector();

    /** 上一帧的"外观签名"——变了才请求重绘。 */
    private String lastRenderSignature = "";

    /** 一发在飞的子弹。 */
    private static final class Bullet {
        private Location at;
        private final Vector direction;
        private double travelled;
        private final boolean healShooterOnHit;

        private Bullet(Location at, Vector direction, boolean healShooterOnHit) {
            this.at = at;
            this.direction = direction;
            this.healShooterOnHit = healShooterOnHit;
        }
    }

    public RavenRevolverMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 主武器名（需求 2026-10-11）：**加粗斜体 · 暗红「散华」+ 金黄「『改』」**，拼接成一个词。
     */
    public static Component weaponName() {
        return Component.text("散华")
                .color(NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD, TextDecoration.ITALIC)
                .append(Component.text("『改』")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD, TextDecoration.ITALIC));
    }

    /** 本组件的描述符：弩（"模型弩"）+ 逐条对应需求的说明。 */
    public static final class Specification extends MainWeapon.Specification<RavenRevolverMainWeapon> {

        public Specification() {
            super(weaponName(),
                    List.of(
                            Component.text("近战：12 点物理伤害 + 3~6 点特殊值伤害，击退等级 2（冷却 0.4 秒）"),
                            Component.text("地面右键：发射全威力弹（10 点物理 + 3~6 点特殊值），弹匣 6 枚；每 2 秒消耗 3 能量回复 1 枚"),
                            Component.text("命中造成 10 点物理 + 3~6 点特殊值；子弹 20 格每秒，撞墙消失（击发间隔 0.7 秒）"),
                            Component.text("物品耐久条额外表达弹药量"),
                            Component.text("地面丢出武器：换弹，每发 0.5 秒 / 3 能量；换弹中不能射击，左键可打断"),
                            Component.text("浮空机动中：右键蓄力 0.8 秒锁定视野内 20 格的敌人，轮流射出 3 枚（间隔 0.1 秒）"),
                            Component.text("浮空机动中丢出：快装 2 发 + 0.2 秒内前冲 8 格并刷新浮空（耗 1 层机动，间隔 1 秒）"),
                            Component.text("空中左键：切换「锁定 / 单发」瞄准模式（经验球的叮声）"),
                            Component.text("击杀敌人立刻为主动机动充能一层；有层数时武器发光")
                    ),
                    Material.CROSSBOW,
                    MELEE_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(EnergyComponent.class)
                    .requires(FactionComponent.class).requires(RavenAirMobilityPassive.class)
                    .requires(RavenFloatingTextComponent.class);
        }

        @Override
        public RavenRevolverMainWeapon create(String id, ComponentServicesPort services) {
            return new RavenRevolverMainWeapon(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        faction = svc().components().get(FactionComponent.class);
        flight = svc().components().get(RavenAirMobilityPassive.class);
        voice = svc().components().get(RavenFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        ammo = MAGAZINE_SIZE;
        resetTransientState();
    }

    @Override
    public void stop() {
        cancelAirDash(false);
        resetTransientState();
        vitals = null;
        sante = null;
        buff = null;
        energy = null;
        faction = null;
        flight = null;
        voice = null;
        render = null;
    }

    /** 清掉全部"进行中"的临时状态（换弹 / 蓄力 / 连发 / 在飞的子弹 / 前冲）。 */
    private void resetTransientState() {
        nextShotTick = 0;
        ammoRegenTick = 0;
        reloading = false;
        reloadTick = 0;
        charging = false;
        chargeUntilTick = 0;
        volleyRemaining = 0;
        volleyNextShotTick = 0;
        volleyShotIndex = 0;
        volleyTargets = List.of();
        volleyHealOnHit = false;
        bullets.clear();
        airDashRemaining = 0;
        airDashFrom = null;
        airDashStep = new Vector();
        lastRenderSignature = "";
    }

    // ───────── 每刻：弹药回复 / 换弹 / 蓄力 / 连发 / 子弹 / 前冲 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            reloading = false;
            charging = false;
            volleyRemaining = 0;
            bullets.clear();
            cancelAirDash(false);
            return;
        }
        //★ 空中前冲优先推进（这 4 刻里位置由本类接管）
        advanceAirDash(owner);
        advanceAmmoRegen(owner);
        advanceReload(owner);
        advanceCharge(owner);
        advanceVolley(owner);
        advanceBullets(owner);
        //★ 弹药读数推给 HUD 宿主（actionbar 必须单一发布者）
        if (flight != null) {
            flight.publishAmmo(ammo, MAGAZINE_SIZE);
        }
        requestRenderIfChanged();
    }

    /**
     * **每 2 秒自动回复一枚**（需求："每 2 秒自动回复 1 枚"；每次耗 {@value #AMMO_REGEN_ENERGY_COST} 能量）。
     * <p>换弹期间**暂停**（需求："换弹期间将不会自动回复弹药"）。
     */
    private void advanceAmmoRegen(Player owner) {
        if (reloading) {
            ammoRegenTick = 0;
            return;
        }
        if (++ammoRegenTick < AMMO_REGEN_TICKS) {
            return;
        }
        ammoRegenTick = 0;
        int available = energy != null ? energy.current() : 0;
        if (!shouldRegenAmmo(ammo, MAGAZINE_SIZE, available)) {
            return;     // 满仓 / 能量不足 ⇒ 这一拍不回（不欠账、不排队）
        }
        if (energy.tryConsume(AMMO_REGEN_ENERGY_COST)) {
            ammo++;
            RavenSound.ravenReloadClipSound(owner.getWorld(), owner.getLocation());
        }
    }

    /**
     * **换弹节拍**（地面丢出后）：每 {@value #RELOAD_TICKS_PER_BULLET} 刻装一发、
     * 每发耗 {@value #RELOAD_ENERGY_PER_BULLET} 能量；装到满仓即收工。
     * <p>能量不足 ⇒ 中止换弹（不欠账），并给一声落空提示。
     */
    private void advanceReload(Player owner) {
        if (!reloading) {
            return;
        }
        if (ammo >= MAGAZINE_SIZE) {
            finishReload(owner);
            return;
        }
        if (++reloadTick < RELOAD_TICKS_PER_BULLET) {
            return;
        }
        reloadTick = 0;
        int available = energy != null ? energy.current() : 0;
        if (available < RELOAD_ENERGY_PER_BULLET) {
            reloading = false;
            RavenSound.ravenReloadInterruptSound(owner.getWorld(), owner.getLocation());
            RavenVfx.ravenReloadInterrupt(owner.getWorld(), owner.getLocation().clone().add(0d, 1d, 0d));
            return;
        }
        if (energy.tryConsume(RELOAD_ENERGY_PER_BULLET)) {
            ammo++;
            Location mid = owner.getLocation().clone().add(0d, 1d, 0d);
            RavenSound.ravenReloadClipSound(owner.getWorld(), mid);
            RavenVfx.ravenReloadClip(owner.getWorld(), mid);
        }
        if (ammo >= MAGAZINE_SIZE) {
            finishReload(owner);
        }
    }

    /** 换弹收尾。 */
    private void finishReload(Player owner) {
        reloading = false;
        reloadTick = 0;
        RavenSound.ravenReloadFinishSound(owner.getWorld(), owner.getLocation());
    }

    /** **蓄力推进**（锁定模式）：边蓄力边把锁定标记画在候选敌人身上；到期即结算。 */
    private void advanceCharge(Player owner) {
        if (!charging) {
            return;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        int total = Math.max(1, LOCK_CHARGE_TICKS);
        double progress = 1d - Math.max(0, chargeUntilTick - now) / (double) total;
        //蓄力进度环（连续 —— 它是"还有多久"的读数，不参与闪烁）
        RavenVfx.ravenLockCharge(owner.getWorld(), owner.getLocation(), progress);

        //★ 锁定标记闪烁（需求 2026-10-11："锁定的红色锁定粒子改为 0.25 秒闪烁一次"）
        if (isLockBlinkOn(now)) {
            for (Player target : collectLockTargets(owner)) {
                RavenVfx.ravenLockMarker(owner.getWorld(), owner.getLocation(), target.getLocation(),
                        progress * 6d);
            }
        }
        if (now >= chargeUntilTick) {
            resolveLockCharge(owner, collectLockTargets(owner));
        }
    }

    /**
     * 蓄力结算：有敌人 ⇒ 锁定并排队 3 连发；**无敌人 ⇒ 什么也不射**。
     *
     * <p>★ 2026-10-11 按需求改掉了旧行为："无论有无锁定目标都必须经过蓄力，
     * 把「若蓄力判定周围无敌人则直接以准心射出一发子弹」这一段改掉"。
     * 现在无目标 = 蓄力白费（只给一声低音提示，不扣弹药）——
     * 与"锁定模式不提供单发"这条保持一致：想单发就先用左键切到"自主"。
     */
    private void resolveLockCharge(Player owner, List<Player> candidates) {
        charging = false;
        chargeUntilTick = 0;
        World world = owner.getWorld();
        if (candidates.isEmpty()) {
            RavenSound.ravenLockNoTargetSound(world, owner.getLocation());
            //需求"无人可锁 / 周围无敌人"那一栏语音
            if (voice != null) {
                voice.saySpecial(owner, RavenFloatingTextComponent.MOMENT_NO_TARGET);
            }
            return;             //★ 不再"直接以准心射出一发"
        }
        //★ 需求："锁定成功后给一个提示音效"
        RavenSound.ravenLockSuccessSound(world, owner.getLocation());
        for (Player target : candidates) {
            RavenVfx.ravenLockConfirm(world, target.getLocation());
        }
        startVolley(candidates, LOCK_SHOTS, false);
    }

    /** **连发推进**：每 {@value #LOCK_SHOT_INTERVAL_TICKS} 刻射出一发，按名单轮转。 */
    private void advanceVolley(Player owner) {
        if (volleyRemaining <= 0) {
            return;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now < volleyNextShotTick) {
            return;
        }
        volleyNextShotTick = now + LOCK_SHOT_INTERVAL_TICKS;
        if (ammo <= 0) {
            //中途空仓 ⇒ 剩余发数作废（口径申报三）
            volleyRemaining = 0;
            RavenSound.ravenRevolverDryFireSound(owner.getWorld(), owner.getLocation());
            return;
        }
        int index = lockShotIndex(volleyShotIndex, volleyTargets.size());
        volleyShotIndex++;
        volleyRemaining--;
        if (index < 0) {
            fireSingleShot(owner);
            return;
        }
        Player target = volleyTargets.get(index);
        if (target == null || !isAlive(target) || !isHostile(target)) {
            //目标中途失效 ⇒ 这一发改打准心（不浪费弹药）
            fireSingleShot(owner);
            return;
        }
        Location eye = owner.getEyeLocation();
        Vector direction = target.getEyeLocation().toVector().subtract(eye.toVector());
        if (direction.lengthSquared() < 1.0E-6d) {
            direction = eye.getDirection();
        }
        ammo--;
        spawnBullet(eye, direction.normalize(), volleyHealOnHit);
        RavenSound.ravenLockVolleySound(owner.getWorld(), eye);
        Location chest = target.getLocation().clone().add(0d, 1d, 0d);
        RavenVfx.ravenLockBeam(owner.getWorld(), eye, chest);
        if (volleyRemaining <= 0) {
            volleyTargets = List.of();
            volleyHealOnHit = false;
        }
    }

    /** **子弹逐刻推进**：撞墙消失 / 命中结算 / 超程回收（需求："撞墙消失"）。 */
    private void advanceBullets(Player owner) {
        if (bullets.isEmpty()) {
            return;
        }
        World ownerWorld = owner.getWorld();
        Iterator<Bullet> iterator = bullets.iterator();
        while (iterator.hasNext()) {
            Bullet bullet = iterator.next();
            Location next = bullet.at.clone()
                    .add(bullet.direction.clone().multiply(BULLET_SPEED_BLOCKS_PER_TICK));
            bullet.at = next;
            bullet.travelled += BULLET_SPEED_BLOCKS_PER_TICK;

            World world = next.getWorld();
            if (world == null || world != ownerWorld) {
                iterator.remove();      //跨世界 / 世界卸载 ⇒ 直接消失
                continue;
            }
            Block block = next.getBlock();
            if (block.getBoundingBox().getVolume() > 0d) {
                RavenVfx.ravenBulletWall(world, next);
                RavenSound.ravenBulletWallSound(world, next);
                iterator.remove();
                continue;
            }
            Player victim = bulletVictim(owner, next);
            if (victim != null) {
                resolveBulletHit(owner, victim, bullet, next);
                iterator.remove();
                continue;
            }
            if (bullet.travelled >= BULLET_MAX_RANGE) {
                iterator.remove();
                continue;
            }
            RavenVfx.ravenBulletTrail(world, next);
        }
    }

    // ───────── 空中前冲（0.2 秒逐刻位移）─────────

    /**
     * **推进空中前冲**：每刻位移 {@value #AIR_DASH_STEP_PER_TICK} 格，共
     * {@value #AIR_DASH_STEPS} 刻（= 0.2 秒、总计 8 格）。
     *
     * <p>收尾时调 {@code flight.endAirDash()}（恢复悬停物理 + 刷新浮空持续时间）。
     */
    private void advanceAirDash(Player owner) {
        if (airDashRemaining <= 0) {
            return;
        }
        if (airDashFrom == null || airDashFrom.getWorld() != owner.getWorld()) {
            cancelAirDash(false);
            return;
        }
        Location before = owner.getLocation();
        Location next = airDashFrom.clone().add(airDashStep.clone().multiply(
                AIR_DASH_STEPS - airDashRemaining + 1));
        owner.teleport(next);
        owner.setFallDistance(0f);
        RavenVfx.ravenAirDashTrail(owner.getWorld(), before, next);
        airDashRemaining--;
        if (airDashRemaining <= 0) {
            cancelAirDash(true);
        }
    }

    /**
     * 结束 / 取消空中前冲。
     *
     * <p>★★ <b>判据必须问真值持有者（被动的 {@code isDashSuspended()}），
     * 而不是本类自己的 {@code airDashRemaining > 0}</b>（2026-10-11 实测的真 BUG）：
     * 正常收尾的调用点发生在"最后一刻刚把它减到 0"之后 ⇒ 用 `remaining > 0` 判会得出
     * "从未开始过" ⇒ **{@code flight.endAirDash()} 永远不执行** ⇒ 被动那侧的
     * {@code dashSuspended} 永远为真 ⇒ 悬停的**物理与超时判定被永久挂起**
     * （表现：用一次主动机动之后，浮空状态再也掉不下来、也不会超时，一直飘着）。
     * <p>改成问被动之后，"谁持有那个标志、谁说了算" ⇒ 这一类错误在结构上不可能再发生。
     *
     * @param refreshHover 是否顺手刷新浮空（正常结束为真；死亡 / 停用为假）
     */
    private void cancelAirDash(boolean refreshHover) {
        airDashRemaining = 0;
        airDashFrom = null;
        airDashStep = new Vector();
        if (flight != null && flight.isDashSuspended()) {
            flight.endAirDash(refreshHover);
        }
    }

    // ───────── 攻击（近战）─────────

    /**
     * 近战命中（{@code listener/MainWeaponListener#onAttackPlayer} 是唯一投递点）。
     *
     * <p>顺序（不可交换）：冷却自判 → 闸门 → 打断换弹 → 12 物理（击退 2）→ 3~6 特殊值
     * → 清无敌帧 → 观感 → 起冷却。
     */
    @Override
    public void onAttack(AttackSignal signal) {
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
        //① 左键即打断换弹（需求："点击左键视为打断换弹操作"）
        if (reloading) {
            reloading = false;
            reloadTick = 0;
            RavenSound.ravenReloadInterruptSound(owner.getWorld(), owner.getLocation());
            RavenVfx.ravenReloadInterrupt(owner.getWorld(), owner.getLocation().clone().add(0d, 1d, 0d));
        }
        //② 12 点物理 + 击退等级 2
        vitals.physicalDamage(victim, owner, MELEE_PHYSICAL_DAMAGE, MELEE_KNOCKBACK_STRENGTH);
        //③ 3~6 点特殊值（扣的是【对方】的 SanTE ⇒ 走跨实例入口）
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(),
                    rollInclusive(random, MELEE_SANTE_MIN, MELEE_SANTE_MAX));
        }
        //④ 连打必需：扣血后再清一次受击无敌帧（工程既有口径）
        victim.setNoDamageTicks(0);

        World world = owner.getWorld();
        if (world != null) {
            Location chest = victim.getLocation().clone().add(0d, 1d, 0d);
            world.spawnParticle(org.bukkit.Particle.SWEEP_ATTACK, chest, 1, 0d, 0d, 0d, 0d);
            world.spawnParticle(org.bukkit.Particle.CRIT, chest, 10, 0.3d, 0.3d, 0.3d, 0.1d);
            RavenVfx.ravenBulletHit(world, chest);
            RavenSound.ravenBulletHitSound(world, chest);
        }
        startCooldown();
        //需求"主武器命中 · 近战命中"那一栏语音
        if (voice != null) {
            voice.saySpecial(owner, RavenFloatingTextComponent.MOMENT_MELEE_HIT);
        }
    }

    // ───────── 施放入口（右键射击 / 左键切换 / 丢出换弹或机动）─────────

    @Override
    public void onCast(CastSignal signal) {
        if (signal == null) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !canUse()) {
            return;
        }
        switch (signal.trigger()) {
            case RIGHT_CLICK -> onRightClick(owner);
            case LEFT_CLICK -> onLeftClick(owner);
            case DROP -> onDrop(owner);
        }
    }

    /**
     * 右键：
     * <ul>
     *   <li><b>锁定模式</b>（浮空 + 锁定）⇒ <b>永远走蓄力</b>，不会立即射出子弹；
     *       ★ 蓄力中再点右键 ⇒ <b>直接忽略</b>（2026-10-11 修的 BUG：旧版会落到单发分支
     *       ⇒ "蓄力期间随手一点就打出一发"，观感就是"右键有时候立即出弹、没有蓄力"）；</li>
     *   <li><b>单发模式 / 地面</b> ⇒ 立即射一发（受 0.7 秒击发间隔限制）。</li>
     * </ul>
     */
    private void onRightClick(Player owner) {
        if (reloading) {
            return;     //需求："更换弹药时间无法右键射击"
        }
        boolean lockMode = flight != null && flight.isHovering() && flight.isAimingLock();
        if (lockMode) {
            if (charging) {
                return;     //★ 蓄力中再点右键：忽略（绝不立刻出弹）
            }
            if (ammo <= 0) {
                RavenSound.ravenRevolverDryFireSound(owner.getWorld(), owner.getLocation());
                return;
            }
            charging = true;
            chargeUntilTick = org.bukkit.Bukkit.getCurrentTick() + LOCK_CHARGE_TICKS;
            RavenSound.ravenLockChargeSound(owner.getWorld(), owner.getLocation());
            return;
        }
        //单发路径（地面 / 浮空的"自主"模式）
        if (org.bukkit.Bukkit.getCurrentTick() < nextShotTick) {
            return;
        }
        if (ammo <= 0) {
            RavenSound.ravenRevolverDryFireSound(owner.getWorld(), owner.getLocation());
            nextShotTick = org.bukkit.Bukkit.getCurrentTick() + SHOT_COOLDOWN_TICKS;
            return;
        }
        fireSingleShot(owner);
    }

    /** 左键：打断换弹；浮空中 ⇒ 切换瞄准模式（经验球的叮声）；地面上的"挥空"不做事。 */
    private void onLeftClick(Player owner) {
        if (reloading) {
            reloading = false;
            reloadTick = 0;
            RavenSound.ravenReloadInterruptSound(owner.getWorld(), owner.getLocation());
            RavenVfx.ravenReloadInterrupt(owner.getWorld(), owner.getLocation().clone().add(0d, 1d, 0d));
        }
        if (flight != null && flight.isHovering()) {
            flight.toggleAimMode();
        }
    }

    /**
     * 丢出主武器：
     * <ul>
     *   <li><b>浮空中</b> ⇒ 主动机动（快装 2 发 + 0.2 秒前冲 8 格 + 刷新浮空，耗 1 层，1 秒间隔）；</li>
     *   <li><b>地面上</b> ⇒ 换弹。</li>
     * </ul>
     */
    private void onDrop(Player owner) {
        if (flight != null && flight.isHovering()) {
            tryAirDash(owner);
            return;
        }
        startReload(owner);
    }

    /** 开始地面换弹（满仓 ⇒ 只响一声收尾音，不进状态）。 */
    private void startReload(Player owner) {
        if (charging) {
            return;     //蓄力中不打断（避免"丢出"把锁定吞掉）
        }
        if (ammo >= MAGAZINE_SIZE) {
            RavenSound.ravenReloadFinishSound(owner.getWorld(), owner.getLocation());
            return;
        }
        reloading = true;
        reloadTick = 0;
        RavenSound.ravenReloadStartSound(owner.getWorld(), owner.getLocation());
    }

    /**
     * **空中主动机动**（需求："在空中时，丢出武器不再进行换弹，而是改为快速装填 2 枚子弹，
     * 并向前位移 8 格，并再次触发浮空机动状态（刷新持续时间）。每次使用间隔为 1.5S…
     * 使用成功后刷新浮空机动 cd。"；2026-10-11：间隔缩到 1 秒、位移改为 0.2 秒内完成）。
     *
     * <p>两道闸门：① <b>机动层数</b>（消耗 1 层）；② <b>1 秒释放间隔</b>。
     */
    private void tryAirDash(Player owner) {
        if (flight == null || airDashRemaining > 0) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        if (org.bukkit.Bukkit.getCurrentTick() < nextAirDashTick) {
            return;     //1 秒间隔未到
        }
        if (!flight.consumeCharge()) {
            //没层数：一声落空的扣扳机响（与空仓同一听感 —— 都是"这一步做不了"）
            RavenSound.ravenRevolverDryFireSound(world, owner.getLocation());
            return;
        }
        //需求"一技能：升空突袭 / 主动机动"那一栏语音（两者共用同一组台词）
        if (voice != null) {
            voice.saySpecial(owner, RavenFloatingTextComponent.MOMENT_SKILL_MOBILITY);
        }
        nextAirDashTick = org.bukkit.Bukkit.getCurrentTick() + RavenAirMobilityPassive.AIR_DASH_INTERVAL_TICKS;

        //① 快装 2 发
        ammo = Math.min(MAGAZINE_SIZE, ammo + AIR_QUICK_LOAD_AMOUNT);
        Location mid = owner.getLocation().clone().add(0d, 1d, 0d);
        RavenSound.ravenAirQuickLoadSound(world, mid);
        RavenVfx.ravenQuickLoad(world, mid);

        //② 前冲（方向 = 水平视线 + 只保留向上的竖直分量；见工程既有"走廊不能扎地"口径）
        Location from = owner.getLocation();
        Vector direction = dashDirection(owner.getEyeLocation().getDirection());
        Location destination = dashDestination(from, direction, AIR_DASH_DISTANCE);
        Location target = destination != null ? destination : from;
        airDashFrom = from.clone();
        airDashStep = target.toVector().subtract(from.toVector()).multiply(1d / AIR_DASH_STEPS);
        airDashRemaining = AIR_DASH_STEPS;

        //③ 挂起悬停物理（这 4 刻的位置由本类接管），结束后由 cancelAirDash 刷新浮空
        flight.beginAirDash();
        RavenSound.ravenAirDashSound(world, from);
        advanceAirDash(owner);      //本刻立刻走第一步（响应更跟手）
    }

    /** 以准心射出一发全威力弹（地面 / 单发模式 / 无目标时的锁定结算）。 */
    private void fireSingleShot(Player owner) {
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        if (ammo <= 0) {
            RavenSound.ravenRevolverDryFireSound(world, owner.getLocation());
            return;
        }
        Location eye = owner.getEyeLocation();
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < 1.0E-6d) {
            return;
        }
        ammo--;
        nextShotTick = org.bukkit.Bukkit.getCurrentTick() + SHOT_COOLDOWN_TICKS;
        spawnBullet(eye, direction.normalize(), false);
        RavenSound.ravenRevolverShotSound(world, eye);
        RavenVfx.ravenMuzzleFlash(world, eye, direction);
    }

    // ───────── 对外只读 / 供技能调用的面 ─────────

    /** 当前弹药。 */
    public int ammo() {
        return ammo;
    }

    /** 弹匣容量。 */
    public int magazineSize() {
        return MAGAZINE_SIZE;
    }

    /** 是否在换弹。 */
    public boolean reloading() {
        return reloading;
    }

    /** 是否在蓄力锁定。 */
    public boolean chargingLock() {
        return charging;
    }

    /**
     * **瞬间补齐弹药**（技能二需求："同时进行一次子弹装填【瞬间补齐子弹，每装填一发发子弹 2 能量】"）。
     *
     * @param perBulletEnergyCost 每发耗能（技能二传 2）
     * @return 实际装进去的发数
     */
    public int instantReload(int perBulletEnergyCost) {
        int loaded = 0;
        while (ammo < MAGAZINE_SIZE) {
            if (energy == null || !energy.tryConsume(Math.max(0, perBulletEnergyCost))) {
                break;
            }
            ammo++;
            loaded++;
        }
        return loaded;
    }

    /** **按"离准心最近"排序的锁定候选**（技能二复用同一套索敌口径）。 */
    public List<Player> lockTargetsInRange(Player owner, double range) {
        if (owner == null) {
            return List.of();
        }
        return collectLockTargets(owner, range);
    }

    /**
     * **开始一轮连发**（本类的锁定结算与技能二的散射共用这一条通道）。
     *
     * @param targets   目标名单（空 ⇒ 每发都以准心直射）
     * @param shots     射出枚数（会被当前弹药夹住）
     * @param healOnHit 命中是否给射手回血（技能二的散射为真）
     */
    public void startVolley(List<Player> targets, int shots, boolean healOnHit) {
        if (shots <= 0) {
            return;
        }
        volleyTargets = targets == null ? List.of() : List.copyOf(targets);
        volleyRemaining = shots;
        volleyShotIndex = 0;
        volleyHealOnHit = healOnHit;
        //第一发立即射出，其后每 LOCK_SHOT_INTERVAL_TICKS 一发（需求："每发间隔 0.1S"）
        volleyNextShotTick = org.bukkit.Bukkit.getCurrentTick();
    }

    /**
     * **刷新空中机动的释放间隔**（技能二需求："刷新机动 CD 间隔"）——
     * 清掉那道 1 秒闸门，使下一次空中丢出立即可用。幂等。
     */
    public void resetAirDashInterval() {
        nextAirDashTick = 0;
    }

    // ───────── 内部：射击 / 命中 / 索敌 ─────────

    /** 生成一发在飞的子弹（此刻已经在枪口）。 */
    private void spawnBullet(Location eye, Vector direction, boolean healOnHit) {
        Location muzzle = eye.clone().add(direction.clone().multiply(1.0d));
        bullets.add(new Bullet(muzzle, direction.clone(), healOnHit));
    }

    /** 该位置是否有可命中的敌人（最近的优先）。 */
    private Player bulletVictim(Player owner, Location at) {
        World world = at.getWorld();
        if (world == null) {
            return null;
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : world.getNearbyPlayers(at, BULLET_HIT_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate) || !isHostile(candidate)) {
                continue;
            }
            double distance = candidate.getLocation().clone().add(0d, 1d, 0d).distanceSquared(at);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 子弹命中结算：10 物理 + 3~6 特殊值（+ 可选的射手回血）。 */
    private void resolveBulletHit(Player owner, Player victim, Bullet bullet, Location at) {
        World world = at.getWorld();
        if (vitals != null) {
            vitals.physicalDamage(victim, owner, BULLET_PHYSICAL_DAMAGE);
        }
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(),
                    rollInclusive(random, BULLET_SANTE_MIN, BULLET_SANTE_MAX));
        }
        victim.setNoDamageTicks(0);
        if (world != null) {
            RavenVfx.ravenBulletHit(world, at);
            RavenSound.ravenBulletHitSound(world, at);
        }
        //需求"主武器命中 · 左轮命中"那一栏语音
        if (voice != null) {
            voice.saySpecial(owner, RavenFloatingTextComponent.MOMENT_SHOT_HIT);
        }
        if (bullet.healShooterOnHit && vitals != null) {
            vitals.heal(owner, DEATH_EYE_LIFE_STEAL_HEAL);
            if (world != null) {
                RavenVfx.ravenLifeSteal(world, victim.getLocation(), owner.getLocation());
                RavenSound.ravenDeathEyeLifeStealSound(world, owner.getLocation());
            }
        }
    }

    /** 技能二"每发弹药击中敌人将额外回复自己 2 点生命"。 */
    public static final double DEATH_EYE_LIFE_STEAL_HEAL = 2d;

    /** 本类自己的锁定索敌（默认半径）。 */
    private List<Player> collectLockTargets(Player owner) {
        return collectLockTargets(owner, LOCK_RANGE);
    }

    /**
     * **锁定候选**：视野内、半径内、敌对的存活玩家，按"离准心最近"升序。
     *
     * <p>判据（沿用工程既有的"用角度而不是用距离"口径）：
     * {@code angle = acos( normalize(目标胸口 − 眼睛) · 视线 )}，角度越小越"靠近准心"。
     */
    private List<Player> collectLockTargets(Player owner, double range) {
        List<Player> result = new ArrayList<>();
        Location eye = owner.getEyeLocation();
        Vector look = eye.getDirection();
        if (look.lengthSquared() < 1.0E-6d) {
            return result;
        }
        Vector unitLook = look.clone().normalize();
        World world = owner.getWorld();
        if (world == null) {
            return result;
        }
        for (Player candidate : world.getNearbyPlayers(eye, range)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate) || !isHostile(candidate)) {
                continue;
            }
            Location chest = candidate.getLocation().clone().add(0d, 1d, 0d);
            Vector delta = chest.toVector().subtract(eye.toVector());
            if (delta.lengthSquared() < 1.0E-6d) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.max(-1d, Math.min(1d,
                    delta.clone().normalize().dot(unitLook)))));
            if (withinLockFov(angle)) {
                result.add(candidate);
            }
        }
        result.sort(Comparator.comparingDouble(candidate -> {
            Location chest = candidate.getLocation().clone().add(0d, 1d, 0d);
            Vector delta = chest.toVector().subtract(eye.toVector());
            return Math.acos(Math.max(-1d, Math.min(1d, delta.clone().normalize().dot(unitLook))));
        }));
        return result;
    }

    /** 是否还活着且在场。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** 是否敌对（判敌口径归阵营组件，本类不重写）。 */
    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }

    // ───────── 闸门 / 外观 ─────────

    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    /**
     * 主武器物品：
     * <ol>
     *   <li>攻速统一 {@value #ATTACK_SPEED_VALUE}（先摘后加 ⇒ 反复重绘不叠加）；</li>
     *   <li>★ <b>需求："用主武器附魔与否来表示机动是否触发"</b> ⇒ 有可用机动层数就发光；</li>
     *   <li>★ <b>需求："用弩的耐久度来额外表达弹药量多少"</b> ⇒ {@link #ammoDamage(int, int, int)}；</li>
     *   <li>★ <b>名称 = 加粗斜体暗红「散华」+ 金黄「『改』」</b>，状态改用后缀表达（见口径申报四）。</li>
     * </ol>
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        applyAttackSpeed(meta);
        applyAmmoDurability(meta, stack.getType());
        meta.setEnchantmentGlintOverride(flight != null && flight.hasCharge());
        meta.displayName(weaponName().append(stateSuffix()));
        stack.setItemMeta(meta);
        return stack;
    }

    /** 状态后缀（基类的三态名称被本类的固定配色名接管，状态改由后缀表达）。 */
    private Component stateSuffix() {
        if (!canUse()) {
            return Component.text(" DISABLED").color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }
        if (isCoolingDown()) {
            return Component.text(" " + String.format("%.1f", remainingCooldownTicks() / 20f) + "s")
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
        }
        if (reloading) {
            return Component.text(" 换弹中").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD);
        }
        if (ammo <= 0) {
            return Component.text(" 空仓").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
        }
        return Component.empty();
    }

    /** 攻速统一 100：先摘后加（{@code buildItem()} 会被反复调用）。 */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    /** 按当前材质施加"弹药 → 耐久"的换算（材质没有耐久条就跳过）。 */
    private void applyAmmoDurability(ItemMeta meta, Material material) {
        int maxDurability = material == null ? 0 : material.getMaxDurability();
        if (maxDurability <= 1) {
            return;     //没有耐久条（如结构空位）⇒ 不表达
        }
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage(ammoDamage(ammo, MAGAZINE_SIZE, maxDurability));
    }

    /**
     * **外观变了才请求重绘**。
     *
     * <p>★ 为什么必须自己驱动：框架只在"组件冷却中"才每刻自动刷，而弹药 / 换弹 / 机动层数 / 耐久
     * 全都在**非冷却期**变化 ⇒ 不请求的话玩家看到的永远是第一次画出来的那个值。
     * 工程既有先例：{@code AlbertThrustSkill} / {@code TekTruthThrustSkill}。
     */
    private void requestRenderIfChanged() {
        if (render == null) {
            return;
        }
        String signature = ammo + "/" + reloading + "/" + charging
                + "/" + (flight != null ? flight.charges() : -1)
                + "/" + (flight != null && flight.hasCharge());
        if (!signature.equals(lastRenderSignature)) {
            lastRenderSignature = signature;
            render.requestRepaint();
        }
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **弹药 → 物品耐久伤害值**（纯函数）——需求 2026-10-11："用弩的耐久度来额外表达弹药量多少"。
     *
     * <p>映射：<b>满弹 ⇒ 0（耐久条满）</b>；<b>空仓 ⇒ {@code maxDurability − 1}（耐久条空）</b>；
     * 中间线性。★ 上界取 {@code maxDurability − 1} 而不是 {@code maxDurability}
     * —— 原版把"伤害值 = 上限"视作**已损坏**，物品会被当成坏掉的那一件。
     */
    public static int ammoDamage(int ammo, int magazineSize, int maxDurability) {
        if (maxDurability <= 1 || magazineSize <= 0) {
            return 0;
        }
        int clampedAmmo = Math.max(0, Math.min(ammo, magazineSize));
        int span = maxDurability - 1;
        double ratio = 1d - clampedAmmo / (double) magazineSize;
        return Math.max(0, Math.min(span, (int) Math.round(span * ratio)));
    }

    /**
     * **一轮连发的第 {@code shotIndex} 发打谁**（纯函数）——
     * 需求："优先锁定离准心最近敌人随后依次锁定第二近第三近的敌人，若人数不够，则从头再轮一次"。
     *
     * @return 候选下标；无候选 ⇒ {@code -1}
     */
    public static int lockShotIndex(int shotIndex, int candidateCount) {
        if (candidateCount <= 0) {
            return -1;
        }
        int index = shotIndex % candidateCount;
        return index < 0 ? index + candidateCount : index;
    }

    /** 角度是否在锁定视锥内（纯函数）。 */
    public static boolean withinLockFov(double angleDegrees) {
        return angleDegrees <= LOCK_FOV_HALF_DEGREES;
    }

    /**
     * **锁定标记此刻是否"亮"**（纯函数）——需求 2026-10-11："锁定的红色锁定粒子改为 0.25 秒闪烁一次"。
     *
     * <p>周期 {@value #LOCK_BLINK_PERIOD_TICKS} 刻（0.25 秒），其中前
     * {@value #LOCK_BLINK_ON_TICKS} 刻亮。用 {@code floorMod} ⇒ 负数刻也不炸。
     */
    public static boolean isLockBlinkOn(int tick) {
        return Math.floorMod(tick, LOCK_BLINK_PERIOD_TICKS) < LOCK_BLINK_ON_TICKS;
    }

    /**
     * **是否应当自动回复弹药**（纯函数）：弹匣未满 且 能量足够。
     * <p>★ 换弹期间**不**自动回复（需求原话），那一条由调用方在 {@code advanceAmmoRegen} 里先行拦下。
     */
    public static boolean shouldRegenAmmo(int ammo, int capacity, int energy) {
        return ammo < capacity && energy >= AMMO_REGEN_ENERGY_COST;
    }

    /** **一发是否能打出**（纯函数）：有弹药 且 过了击发闸门。 */
    public static boolean canFire(int ammo, int nowTick, int nextShotAt) {
        return ammo > 0 && nowTick >= nextShotAt;
    }

    /**
     * **一次连发实际能射出几发**（纯函数）= 请求枚数与当前弹药取小。
     * <p>单独抽出来是为了让"锁定 3 发但弹匣只剩 1 发"这种边界可被单测钉住。
     */
    public static int shotsAffordable(int requested, int ammo) {
        return Math.max(0, Math.min(requested, ammo));
    }

    /**
     * **前冲方向**（纯函数）＝ 视线的水平分量 + **只保留向上的竖直分量**。
     *
     * <p>与 {@code AlbertThrustSkill#dashDirection} 同口径：走廊若沿带俯仰的视线采样，
     * 玩家稍微低头就会扎进地面 ⇒ 终点退化成原地（"平地也冲不动"）。把向下分量夹成 0
     * ⇒ 无论怎么瞄都稳定前进；**向上保留**（抬头前冲是合法用法）。
     */
    public static Vector dashDirection(Vector look) {
        if (look == null) {
            return new Vector(0d, 0d, 1d);
        }
        Vector flat = new Vector(look.getX(), Math.max(0d, look.getY()), look.getZ());
        if (flat.lengthSquared() < 1.0E-9d) {
            return new Vector(0d, 0d, 1d);
        }
        return flat.normalize();
    }

    /** **闭区间随机整数** {@code [min, max]}（需求写"3~6 点"，两端都含）。 */
    public static int rollInclusive(Random random, int min, int max) {
        if (random == null || max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    // ───────── 内部几何 ─────────

    /** 在空中前冲：沿方向采样，取最远的"人能站住"的点（一个合格点都没有 ⇒ 原地）。 */
    private Location dashDestination(Location origin, Vector direction, double distance) {
        if (origin == null || origin.getWorld() == null || direction == null) {
            return origin;
        }
        Vector unit = direction.clone();
        if (unit.lengthSquared() < 1.0E-9d) {
            return origin;
        }
        unit.normalize();
        Location best = origin.clone();
        for (double t = AIR_DASH_STEP; t <= distance + 1.0E-6d; t += AIR_DASH_STEP) {
            Location candidate = origin.clone().add(unit.clone().multiply(t));
            if (isStandable(candidate)) {
                best = candidate;
            }
        }
        return best;
    }

    /** 该位置能否站人：脚那格与头那格都得是可通过的。 */
    private static boolean isStandable(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block head = at.clone().add(0d, 1d, 0d).getBlock();
        return feet.isPassable() && head.isPassable();
    }
}
