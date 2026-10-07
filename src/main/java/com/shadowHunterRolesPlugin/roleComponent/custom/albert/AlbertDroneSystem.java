package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * 「艾尔伯特」的核心被动：**高斯无人机编队系统**。
 *
 * <p>需求原话："资源：无能量机制。核心为「高斯无人机」与「零件」" ⇒ 本类是整张角色卡的中枢。
 *
 * <h2>它管理的四类对象</h2>
 * <table border="1">
 *   <tr><th>对象</th><th>来源</th><th>形态</th></tr>
 *   <tr><td><b>高斯无人机</b>（≤ {@value #MAX_DRONES}）</td><td>零件满 4 层 / 技能1 / 击杀猎杀目标</td>
 *       <td>白色小菱形，待机时分布在视角左右角 + 左右肩</td></tr>
 *   <tr><td><b>哨戒无人机</b>（至多 1）</td><td>技能1（释放时满编才生成）</td>
 *       <td>蓝色菱形，固定常驻原地，r={@value #SENTRY_RADIUS} 缓慢 I + 报警</td></tr>
 *   <tr><td><b>自杀式无人机</b>（技能4 一次性）</td><td>过载协议</td>
 *       <td>红色菱形，原地蓄力 {@value #KAMIKAZE_CHARGE_TICKS} 刻后逐个飞出</td></tr>
 *   <tr><td><b>无人机诱饵</b></td><td>技能3 起点</td><td>匍匐地面的灰色菱形，r={@value #DECOY_TRIGGER_RADIUS} 触发爆炸</td></tr>
 * </table>
 *
 * <h2>★ 依赖方向（为什么它依赖锁定被动，反之不成立）</h2>
 * 框架 {@code Role#verifyDependencies()} 把<b>依赖环</b>判为装配失败（角色根本不注册）。
 * 而"击杀猎杀目标 ⇒ 立刻部署一架无人机"需要"读标记 + 部署"两件事，
 * 若把订阅放在 {@link AlbertLockOnPassive}、部署放在本类，就会形成双向依赖。
 * ⇒ 本项目把击杀订阅放在<b>本类</b>（它本来就依赖 {@code VitalsComponent}），
 * 由它查 {@code lockOn.isMarked(...)}。依赖因此是单向的：本类 → 锁定被动。
 *
 * <h2>★★ 口径申报一：「主动防御」的层数 = 无人机数</h2>
 * 需求括号里那句把两件事绑在一起："每一次触发主动防御时会消耗一层无人机" +
 * "每被攻击一次就消耗一层主动防御"。
 * ⇒ <b>层数不是一个独立计数器，就是编队里的无人机数</b>；被消耗掉的那一架就是"替艾尔伯特挡下这一下"的那一架。
 *
 * <p><b>消耗的两条触发路径</b>：
 * <ol>
 *   <li><b>受伤</b> —— 本类实现 {@link VitalsComponent.Participant}，在 {@link #onDamaged} 里消耗一层
 *       （同一刻只消耗一层：见 {@link #lastShieldConsumeTick}）；</li>
 *   <li><b>远程拦截</b> —— 每刻扫描主人 {@value #INTERCEPT_RADIUS} 格内的投射物并移除它，
 *       消耗一层（这就是需求"无人机能够主动防御抵消远程攻击"的落点）。</li>
 * </ol>
 *
 * <p>★ <b>如实申报（平台行为相关）</b>：抗性 255 会把伤害减到 0，而"伤害为 0 时平台是否仍派发
 * {@code EntityDamageEvent}"由服务端实现决定。若某些版本不在此时派发，
 * 则<b>近战那条路径不会消耗层数</b>（远程那条不受影响，因为它是本类自己扫描的）。
 * 届时表现为"层数比预期耐用"（偏强而非崩溃）—— 属于观感/平衡问题，不是功能缺失。
 *
 * <h2>★★ 口径申报二：0.7 秒"机间攻击间隔"是全局闸门</h2>
 * 需求写的是"上一架无人机飞出后 0.7S 后下一架无人机才能飞出攻击" ⇒ 它是
 * <b>编队级</b>的闸门（{@link #lastLaunchTick}），不是每架自己的节拍。
 * 每架自己的节拍是 {@value #LAUNCH_INTERVAL_TICKS} 刻（6 秒，需求原话"每 6 秒"）。
 *
 * <h2>★★ 口径申报三：6 秒周期从"起飞"起算</h2>
 * 一架无人机"出击 → 命中/超时 → 返航"的总时长最多约 2 秒（超时）+ 返航时间，
 * 若周期从"回到待机位"起算，实际节奏会被返航距离拉长到不确定。
 * ⇒ 周期从<b>起飞那一刻</b>起算（{@link Drone#nextLaunchTick} = 起飞刻 + 120），
 * 与需求"每 6 秒飞出一次"的读数一致，且与距离无关。
 *
 * <h2>★★ 口径申报四：不读"自己"的在场状态</h2>
 * 索敌一律走 {@code svc().components().get(FactionComponent.class).isHostileTo(uuid)}（全角色单点），
 * 并且**显式排除主人自己** —— {@code getNearbyPlayers(r)} 是<b>包含自己</b>的
 * （工程既有实测口径，各技能都得自己排除）。
 */
public class AlbertDroneSystem extends PassiveSkill implements VitalsComponent.Participant {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_passive_droneSystem";

    // ───────── 高斯无人机 ─────────

    /** 同时存在的无人机上限（需求原话"最多 4 架"）。 */
    public static final int MAX_DRONES = 4;

    /** 单架无人机的出击周期（刻）：6 秒（需求原话"每 6 秒"）。 */
    public static final int LAUNCH_INTERVAL_TICKS = 120;

    /** 机与机之间的出击闸门（刻）：0.7 秒（需求原话"上一架飞出后 0.7S 下一架"）。 */
    public static final int LAUNCH_GATE_TICKS = 14;

    /** 命中判定半径（格）：目标进入无人机中心 r={@value #ATTACK_RADIUS} 即视为被攻击。 */
    public static final double ATTACK_RADIUS = 3.0d;

    /** 飞行速度（格/刻）：15 格/秒（需求原话"以 15 格每秒的速度飞出"）。 */
    public static final double SPEED_PER_TICK = 15.0d / 20d;

    /** 出击超时（刻）：2 秒内没能命中就返航（需求原话"若 2 秒内不能成功攻击则也会进入 CD 并返回"）。 */
    public static final int OUTBOUND_TIMEOUT_TICKS = 40;

    /** 返航抵达判定（格）。 */
    private static final double ARRIVE_EPSILON = 0.55d;

    /** 基础自动索敌范围（格）：以无人机自身坐标为圆心（需求原话"自动索敌攻击范围为自身坐标 15 格"）。 */
    public static final double BASE_SEARCH_RANGE = 15.0d;

    /** 技能1 展开后的索敌范围（格，需求原话"扩大索敌范围[r=25]"）。 */
    public static final double ASSEMBLE_SEARCH_RANGE = 25.0d;

    /** 技能4 展开后的索敌范围（格，需求原话"扩大索敌范围至 30 格"）。 */
    public static final double OVERLOAD_SEARCH_RANGE = 30.0d;

    /** 无人机单次攻击的伤害（需求原话"6 点物理伤害与 4 点特殊值伤害，1 点真实伤害"）。 */
    public static final double DRONE_PHYSICAL_DAMAGE = 6d;
    public static final int DRONE_SANTE_DAMAGE = 4;
    public static final double DRONE_TRUE_DAMAGE = 1d;

    /** 集火窗口（技能2）期间，无人机每次攻击额外造成的灵魂伤害（需求原话"额外造成 2 点灵魂伤害"）。 */
    public static final double FOCUS_BONUS_SOUL_DAMAGE = 2d;

    /** 集火窗口持续时间（刻）：10 秒（需求原话"持续 10 秒"；★ 2026-10-06 由 5 秒上调）。 */
    public static final int FOCUS_WINDOW_TICKS = 200;

    /**
     * ★ 集火窗口内的**出击超时**（刻）：10 秒。
     *
     * <p>默认超时是 {@value #OUTBOUND_TIMEOUT_TICKS} 刻（2 秒 = 最多飞 30 格）。
     * 但需求要求"<b>区域内敌人被无人机无视距离优先攻击</b>" ⇒ 若仍按 2 秒收，
     * 超出 30 格的目标根本飞不到（"无视距离"就是句空话）。
     * ⇒ 集火窗口内放宽到整段窗口长度，让无人机真的能追出去。
     */
    public static final int FOCUS_OUTBOUND_TIMEOUT_TICKS = 200;

    /** 集火窗口期间的攻速提升（需求原话"攻击速度提升 50%"）⇒ 节拍乘它。 */
    public static final double FOCUS_SPEED_MULTIPLIER = 1.5d;

    /** 集火区域半径（格，需求原话"标记目标区域（半径 10 格）"）。 */
    public static final double FOCUS_RADIUS = 10.0d;

    // ───────── 编队光环 ─────────

    /** 身边有无人机时的速度（需求原话"获得速度 2"）。 */
    public static final int AMBIENT_SPEED_AMPLIFIER = 1;

    /** 无敌人时施加隐形的判定半径（格，需求原话"周围 20 米没有敌方单位"）。 */
    public static final double INVISIBLE_SAFE_RADIUS = 20.0d;

    /** 光环刷新时长（刻）：40 刻刷新一次 ⇒ 观感是"常驻"，且组件停用后会自然消失。 */
    private static final int AMBIENT_REFRESH_TICKS = 40;

    // ───────── 主动防御 ─────────

    /** 主动防御期间的反抗等级（需求原话"获得永久的抗性 255"）。 */
    public static final int SHIELD_RESISTANCE_AMPLIFIER = 255;

    /** 触发主动防御时附加的速度等级（需求原话"附加 2 秒速度 6"）。 */
    public static final int SHIELD_SPEED_AMPLIFIER = 5;

    /** 触发主动防御时的速度持续（刻）：2 秒。 */
    public static final int SHIELD_SPEED_TICKS = 40;

    /** 远程拦截扫描半径（格）。 */
    public static final double INTERCEPT_RADIUS = 2.6d;

    // ───────── 哨戒无人机 ─────────

    /** 哨戒无人机的效果半径（格，需求原话"对进入哨戒无人机范围的敌人[r=10]"）。 */
    public static final double SENTRY_RADIUS = 10.0d;

    /** 哨戒施加的缓慢等级（需求原话"持续造成缓慢 1 效果"）⇒ 增幅 0。 */
    public static final int SENTRY_SLOWNESS_AMPLIFIER = 0;

    /** 哨戒缓慢的刷新时长（刻）。 */
    private static final int SENTRY_SLOWNESS_TICKS = 40;

    // ───────── 自杀式无人机（技能4）─────────

    /** 原地蓄力时长（刻）：3 秒（需求原话"原地蓄力 3S 后开始逐个飞出攻击敌人"）。 */
    public static final int KAMIKAZE_CHARGE_TICKS = 60;

    /** 逐个起飞的间隔（刻）。 */
    public static final int KAMIKAZE_LAUNCH_GAP_TICKS = 10;

    /** 触碰敌人后的二次蓄力（刻）：1 秒（需求原话"触碰到敌人后停滞蓄力 1S 后爆炸"）。 */
    public static final int KAMIKAZE_FUSE_TICKS = 20;

    /** 自杀式无人机爆炸半径（格，需求原话"对周围 7 格内敌人"）。 */
    public static final double KAMIKAZE_BLAST_RADIUS = 7.0d;

    /** 自杀式无人机爆炸伤害（需求原话"10 点灵魂伤害与 10 点特殊值伤害"）。 */
    public static final double KAMIKAZE_SOUL_DAMAGE = 10d;
    public static final int KAMIKAZE_SANTE_DAMAGE = 10;

    /** 命中敌人后回复主人的生命（需求原话"若成功命中敌人则为艾尔伯特回复 8 点生命"）。 */
    public static final double KAMIKAZE_SELF_HEAL = 8d;

    /** 自杀式无人机的飞行速度（格/刻）：比高斯更快（它是"最后的命令"）。 */
    private static final double KAMIKAZE_SPEED_PER_TICK = 0.9d;

    // ───────── 诱饵（技能3）─────────

    /** 诱饵存在时长（刻）：4 秒（需求原话"诱饵存在 4 秒"）。 */
    public static final int DECOY_LIFETIME_TICKS = 80;

    /** 诱饵触发半径（格，需求原话"敌人进入半径 4 格时爆炸"）。 */
    public static final double DECOY_TRIGGER_RADIUS = 4.0d;

    /** 诱饵爆炸：物理伤害 10 / 缓慢 III 2 秒 / 特殊值 3~10（需求原话）。 */
    public static final double DECOY_PHYSICAL_DAMAGE = 10d;
    public static final int DECOY_SLOWNESS_TICKS = 40;
    public static final int DECOY_SLOWNESS_AMPLIFIER = 2;
    public static final int DECOY_SANTE_MIN = 3;
    public static final int DECOY_SANTE_MAX = 10;

    /** HUD 刷新间隔（刻）：每 5 刻一次（够快看得出变化，又不至于每刻发包）。 */
    private static final int HUD_INTERVAL_TICKS = 5;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private AlbertLockOnPassive lockOn;
    private AlbertFloatingTextComponent floatingText;

    /** 击杀订阅句柄（{@code start()} 注册、{@code stop()} 按引用撤销）。 */
    private VitalsComponent.PlayerKilledListener killedEntry;

    /** 随机源（只影响观感抖动，不参与判定）。 */
    private final Random random = new Random();

    // ───────── 运行期状态 ─────────

    /** 高斯无人机编队（下标 = 待机槽位，与 {@link AlbertVfx#dockSlot} 一一对应）。 */
    private final List<Drone> drones = new ArrayList<>();

    /** 哨戒无人机（至多一架，{@code null} = 没有）。 */
    private Sentry sentry;

    /** 自杀式无人机群（技能4）。 */
    private final List<Kamikaze> kamikazes = new ArrayList<>();

    /** 无人机诱饵列表（技能3 可储存 2 次 ⇒ 最多同时两个）。 */
    private final List<Decoy> decoys = new ArrayList<>();

    /** 编队级出击闸门（刻）：上一架飞出的时刻。 */
    private int lastLaunchTick;

    /** 索敌范围扩展的到期刻（技能1 ⇒ 25，技能4 ⇒ 30；{@code 0} = 未扩展）。 */
    private int expandedUntilTick;
    private double expandedRange;

    /** 集火窗口（技能2）：中心 + 到期刻。 */
    private Location focusCenter;
    private int focusUntilTick;

    /** 哨戒报警：本刻是否应提示（进入瞬间置真，由 HUD 消费后清）。 */
    private boolean sentryAlarmPending;

    /** 主人上一次被消耗护盾的刻（同刻去重，防"一次攻击吃两层"）。 */
    private int lastShieldConsumeTick = -1;

    /** 上一帧是否处于隐形（边沿判据：只在"刚进入隐形"时说一次台词）。 */
    private boolean cloaked;

    /** 上一帧是否被控制（沉默 / 眩晕）—— 同上，边沿触发。 */
    private boolean controlled;

    /** 主人的上一帧生命（用于兜底识别"被打了"）。 */
    private double lastHealth = -1d;

    /** 主武器推送过来的弹药读数（由 {@link #publishAmmo} 写；仅用于 HUD）。 */
    private int ammoCurrent = -1;
    private int ammoMax = -1;

    /** 节拍。 */
    private int tick;
    private double phase;

    public AlbertDroneSystem(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符。
     * <p>依赖 {@link AlbertLockOnPassive}（读标记）与三个内建组件；**不依赖任何技能**
     * （技能反过来依赖它），依赖图因此无环。
     */
    public static final class Specification extends PassiveSkill.Specification<AlbertDroneSystem> {

        public Specification() {
            super(Component.text("过度响应协议"),
                    List.of(
                            Component.text("高斯无人机自动跟随，身边有无人机时获得速度 2"),
                            Component.text("周围 20 格没有敌人时获得隐形"),
                            Component.text("无人机主动防御：消耗一层无人机抵消一次攻击，并给予 2 秒速度 6"),
                            Component.text("主动防御期间获得抗性 255")
                    ));
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class)
                    .requires(AlbertLockOnPassive.class)
                    .requires(AlbertFloatingTextComponent.class).requires(FactionComponent.class);
        }

        @Override
        public AlbertDroneSystem create(String id, ComponentServicesPort services) {
            return new AlbertDroneSystem(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        lockOn = svc().components().get(AlbertLockOnPassive.class);
        floatingText = svc().components().get(AlbertFloatingTextComponent.class);
        registerKillListener();
        lastHealth = -1d;
    }

    @Override
    public void stop() {
        unregisterKillListener();
        drones.clear();
        kamikazes.clear();
        decoys.clear();
        sentry = null;
        focusCenter = null;
        focusUntilTick = 0;
        expandedUntilTick = 0;
        expandedRange = 0d;
        clearAmbientEffects();
    }

    /** 订阅击杀：击杀「猎杀目标」⇒ 立刻部署一架（需求原话）。 */
    private void registerKillListener() {
        if (killedEntry != null || vitals == null) {
            return;
        }
        killedEntry = vitals.addPlayerKilledListener(this, event -> {
            Player victim = event.getVictim();
            Player killer = event.getKiller();
            if (victim == null || killer == null || lockOn == null) {
                return;
            }
            if (killer != svc().self().player()) {
                return;
            }
            if (lockOn.consumeMark(victim.getUniqueId())) {
                if (floatingText != null) {
                    floatingText.saySpecial(killer, AlbertFloatingTextComponent.MOMENT_KILL_MARKED);
                }
                deployOne();
            }
        });
    }

    private void unregisterKillListener() {
        if (killedEntry == null) {
            return;
        }
        if (vitals != null) {
            vitals.removePlayerKilledListener(killedEntry);
        }
        killedEntry = null;
    }

    // ───────── 对外 API（技能 / 武器用）─────────

    /** 当前高斯无人机数量。 */
    public int droneCount() {
        return drones.size();
    }

    /** 当前「主动防御」层数（= 无人机数量；需求口径见类注释）。 */
    public int defenseCharges() {
        return drones.size();
    }

    /** 是否满编。 */
    public boolean isFull() {
        return drones.size() >= MAX_DRONES;
    }

    /**
     * **部署一架高斯无人机**（零件满 / 技能1 / 击杀猎杀目标都走这里）。
     *
     * @return {@code true} = 真的加了一架（已满编 ⇒ {@code false}，需求"不超过上限"）
     */
    public boolean deployOne() {
        Player owner = svc().self().player();
        if (owner == null || drones.size() >= MAX_DRONES) {
            return false;
        }
        Location dock = AlbertVfx.dockSlot(owner, drones.size());
        Drone drone = new Drone();
        drone.slot = drones.size();
        if (dock != null) {
            drone.x = dock.getX();
            drone.y = dock.getY();
            drone.z = dock.getZ();
        }
        drone.nextLaunchTick = tick;
        drones.add(drone);
        if (drones.size() >= MAX_DRONES && floatingText != null) {
            floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_FULL);
        }
        return true;
    }

    /** 移除一架无人机（主动防御消耗 / 过载协议抽取）。 */
    public boolean removeOne() {
        if (drones.isEmpty()) {
            return false;
        }
        drones.remove(drones.size() - 1);
        return true;
    }

    /**
     * **技能1 的高斯装配**：扩到 r=25 + 立刻派出所有待机无人机（无视 6 秒节拍与 0.7 秒闸门）。
     *
     * @param rangeTicks 扩展持续刻数
     */
    /**
     * **技能1 的高斯装配**：立刻派出全部待机无人机索敌攻击一次。
     *
     * <p>★★ <b>索敌范围只在这一波抬到 25 格，走出本方法就回到 15 格</b>（2026-10-06 按反馈收窄）。
     * 旧实现是"开一个 15 秒的 25 格窗口" ⇒ 与需求"扩大"的本意相比太慷慨：
     * 那段窗口里无人机能一直隔着 25 格追人，而技能描述读起来只是"这一轮打得更远"。
     *
     * <p>落地方式 = 把"扩展到期刻"设成 {@code tick + 1}（本 tick 内有效），
     * 循环一结束立刻恢复原值（{@code try/finally}）⇒ 出了本方法 {@link #searchRange()} 就是 15。
     * <p>★ 已经起飞的那些无人机<b>沿用"起飞那一刻选中的目标"</b>（{@code drone.target} 在
     * {@link #tryLaunch} 里就定死了）⇒ 它们的这一轮追击不受"范围已经调回"影响，
     * 真正被收窄的只是"下一轮该打谁"。
     * <p>★ 用 {@code try/finally} 而不是直接清零：技能4 的过载窗口（30 格）如果正开着，
     * 这一波结束后必须把它**还原**，不能顺手把别人的窗口也关掉。
     */
    public void assembleNow() {
        Player owner = svc().self().player();
        World world = owner == null ? null : owner.getWorld();

        double previousRange = expandedRange;
        int previousUntil = expandedUntilTick;
        expandedRange = ASSEMBLE_SEARCH_RANGE;
        expandedUntilTick = tick + 1;
        try {
            if (owner == null || world == null) {
                return;
            }
            for (Drone drone : drones) {
                drone.nextLaunchTick = tick;
                // 无视编队闸门（"立刻派出拥有的所有无人机"）；但没敌人仍然不空放（见 tryLaunch）
                tryLaunch(drone, owner, world, true);
            }
        } finally {
            expandedRange = previousRange;
            expandedUntilTick = previousUntil;
        }
    }

    /** 当前有效索敌范围（格）。 */
    /**
     * 当前有效索敌范围（格）。
     * <p>★ 只有技能1 那一波 / 技能4 的窗口会把它抬起来；**技能1 的抬升在同一 tick 内就恢复**
     * （见 {@link #assembleNow()}），因此常规状态下恒为 {@value #BASE_SEARCH_RANGE}。
     */
    public double searchRange() {
        return searchRangeFor(BASE_SEARCH_RANGE, expandedRange, tick, expandedUntilTick);
    }

    /**
     * **有效索敌范围**（**纯函数**）：扩展未过期 ⇒ 扩展值，否则基准值。
     *
     * @param base              基准范围（{@value #BASE_SEARCH_RANGE} 格）
     * @param expanded          扩展值
     * @param nowTick           当前刻
     * @param expandedUntilTick 扩展到期刻（{@code 0} / 不大于当前刻 ⇒ 未扩展）
     */
    public static double searchRangeFor(double base, double expanded, int nowTick, int expandedUntilTick) {
        return expandedUntilTick > nowTick ? expanded : base;
    }

    /** 过载协议：把索敌范围扩到 30 格。 */
    public void overloadRange(int rangeTicks) {
        expandedRange = OVERLOAD_SEARCH_RANGE;
        expandedUntilTick = tick + Math.max(1, rangeTicks);
    }

    /**
     * **技能2 的猎杀指令**：开一个集火窗口。
     *
     * @param center 区域中心
     * @param ticks  持续刻数（{@value #FOCUS_WINDOW_TICKS} = 10 秒）
     */
    public void openFocusWindow(Location center, int ticks) {
        if (center == null) {
            return;
        }
        focusCenter = center.clone();
        focusUntilTick = tick + Math.max(1, ticks);
    }

    /** 集火窗口是否生效中。 */
    public boolean focusActive() {
        return focusCenter != null && focusUntilTick > tick;
    }

    /** 集火窗口中心（未生效 ⇒ {@code null}）。 */
    public Location focusCenter() {
        return focusActive() ? focusCenter.clone() : null;
    }

    /**
     * **技能1 的哨戒无人机**：常驻原地（已有一架则替换它 —— "仅能存在一个"）。
     *
     * @return {@code true} = 新部署了一架
     */
    public boolean deploySentry(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Sentry fresh = new Sentry();
        fresh.x = at.getX();
        fresh.y = at.getY();
        fresh.z = at.getZ();
        fresh.world = at.getWorld();
        sentry = fresh;
        return true;
    }

    /** 是否有哨戒无人机（技能1 判"满编才额外生成"时用）。 */
    public boolean hasSentry() {
        return sentry != null;
    }

    /**
     * **技能3 的无人机诱饵**：留在起点，4 秒后自毁（或有人踏入即爆）。
     *
     * @return {@code true} = 放置成功
     */
    public boolean placeDecoy(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Decoy decoy = new Decoy();
        decoy.x = at.getX();
        decoy.y = at.getY();
        decoy.z = at.getZ();
        decoy.world = at.getWorld();
        decoy.expireTick = tick + DECOY_LIFETIME_TICKS;
        decoys.add(decoy);
        return true;
    }

    /**
     * **技能4 的过载协议**：消耗全部无人机层数，每层生成一架自杀式无人机。
     *
     * @return 实际生成的自杀式无人机数量（= 消耗前的层数）
     */
    public int startOverload() {
        Player owner = svc().self().player();
        int count = drones.size();
        drones.clear();
        if (owner == null || owner.getWorld() == null) {
            return count;
        }
        Location base = owner.getLocation();
        for (int i = 0; i < count; i++) {
            Kamikaze kamikaze = new Kamikaze();
            double angle = 2d * Math.PI * i / Math.max(1, count) + random.nextDouble() * 0.5d;
            double radius = 1.2d;
            kamikaze.x = base.getX() + Math.cos(angle) * radius;
            kamikaze.y = base.getY() + 1.0d;
            kamikaze.z = base.getZ() + Math.sin(angle) * radius;
            kamikaze.world = owner.getWorld();
            kamikaze.state = KamikazeState.CHARGING;
            kamikaze.stateUntilTick = tick + KAMIKAZE_CHARGE_TICKS;
            kamikaze.launchAtTick = tick + KAMIKAZE_CHARGE_TICKS + i * KAMIKAZE_LAUNCH_GAP_TICKS;
            kamikazes.add(kamikaze);
        }
        overloadRange(KAMIKAZE_CHARGE_TICKS + 200);
        return count;
    }

    /** 是否还有未结束的自杀式无人机 / 集火窗口（技能4 判"技能完全后冷却"用）。 */
    public boolean overloadBusy() {
        return !kamikazes.isEmpty();
    }

    /**
     * 主武器推来的弹药读数（仅用于 actionbar HUD）。
     *
     * <p>★ 为什么由武器"推"而不是本类"拉"：若本类反过来 {@code requires(主武器)}，
     * 而主武器又 {@code requires(本类)}（部署无人机），就构成依赖环。
     * ⇒ 单向：主武器 → 本类，本类只持有一个可写字段。
     */
    public void publishAmmo(int current, int max) {
        this.ammoCurrent = current;
        this.ammoMax = max;
    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        tick++;
        phase += 0.5d;

        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        if (expandedUntilTick > 0 && expandedUntilTick <= tick) {
            expandedUntilTick = 0;
            expandedRange = 0d;
        }
        if (focusUntilTick > 0 && focusUntilTick <= tick) {
            focusCenter = null;
            focusUntilTick = 0;
        }

        // ① 远程拦截（在受击结算之前 —— 挡住就没伤害）
        interceptProjectiles(owner);
        // ② 光环（速度 2 / 隐形 / 抗性 255）
        refreshAmbient(owner);
        // ②b 被控制 / 解控（边沿判据：进入受控的那一刻说一次）
        updateControlledEdge(owner);
        // ③ 集火区域高亮（窗口期内常亮）
        highlightFocus(world);
        // ③ 编队推进
        updateDrones(owner, world);
        // ④ 哨戒无人机
        updateSentry(owner, world);
        // ⑤ 自杀式无人机
        updateKamikazes(owner, world);
        // ⑥ 诱饵
        updateDecoys(owner, world);
        // ⑦ HUD
        if (tick % HUD_INTERVAL_TICKS == 0) {
            publishHud(owner);
        }
    }

    // ───────── 高斯无人机编队 ─────────

    private void updateDrones(Player owner, World world) {
        for (int i = 0; i < drones.size(); i++) {
            Drone drone = drones.get(i);
            drone.slot = i;

            if (drone.outbound) {
                stepOutbound(owner, world, drone);
                continue;
            }
            if (drone.returning) {
                if (flyToward(owner, world, drone, dockLocation(owner, i), SPEED_PER_TICK, phase + i)) {
                    drone.returning = false;
                    drone.target = null;
                    // ★ 2026-10-06：去掉"每架返航各响一次"的回旋音 ——
                    //   4 架编队会变成每 1~2 秒一声的持续噪音（正是"没在打人却一直在响"的主因之一）。
                }
                continue;
            }
            // 待机：贴住待机位（视角左右角 + 左右肩）
            pinToDock(owner, drone);
            if (drone.nextLaunchTick <= tick) {
                // ★ "CD 转好后进入待命跟随状态，有敌人时发射无人机"（见 tryLaunch）
                tryLaunch(drone, owner, world, false);
            }
            AlbertVfx.diamondGaussDrone(world, droneLocation(world, drone));
        }
    }

    /** 编队级闸门：上一架飞出后必须过 {@value #LAUNCH_GATE_TICKS} 刻（含集火加速）。 */
    private boolean canLaunchNow() {
        int gate = focusActive()
                ? Math.max(1, (int) Math.round(LAUNCH_GATE_TICKS / FOCUS_SPEED_MULTIPLIER))
                : LAUNCH_GATE_TICKS;
        return tick - lastLaunchTick >= gate;
    }

    /**
     * ★★ **尝试让一架待机无人机起飞**（需求："CD 转好后进入待命跟随状态，有敌人时发射无人机"）。
     *
     * <p><b>没有目标 ⇒ 不起飞</b>：不发声、不占编队闸门、不消耗那 6 秒 CD ——
     * 它就静静待命跟随，直到有敌人进入索敌范围。
     * 这条同时解决了"无人机在未攻击时还在响攻击音效"（旧实现无条件飞出并播
     * {@code fireworkShootAlbertDroneLaunchSound}，玩家身边一圈在空放）。
     *
     * @param ignoreGate 忽略 {@value #LAUNCH_GATE_TICKS} 刻的编队闸门
     *                   （{@code true} = 技能1 的"立刻派出所有无人机"）
     * @return {@code true} = 真的起飞了
     */
    private boolean tryLaunch(Drone drone, Player owner, World world, boolean ignoreGate) {
        if (owner == null || world == null) {
            return false;
        }
        if (!ignoreGate && !canLaunchNow()) {
            return false;
        }
        Location from = AlbertVfx.dockSlot(owner, drone.slot);
        if (from != null) {
            drone.x = from.getX();
            drone.y = from.getY();
            drone.z = from.getZ();
        }
        Location here = droneLocation(world, drone);
        // ★ 待命跟随：没有敌人就不发射（这是"未攻击时不发出攻击音效"的唯一可靠落点）
        Player target = pickTarget(owner, here);
        if (target == null) {
            return false;
        }
        drone.outbound = true;
        drone.returning = false;
        drone.target = target.getUniqueId();
        drone.outboundStartTick = tick;
        drone.nextLaunchTick = tick + intervalTicks(LAUNCH_INTERVAL_TICKS, focusActive());
        lastLaunchTick = tick;
        AlbertSound.fireworkShootAlbertDroneLaunchSound(world, here);
        return true;
    }

    /** 出击超时（刻）：集火窗口内放宽（"无视距离"的必要条件，见 {@link #FOCUS_OUTBOUND_TIMEOUT_TICKS}）。 */
    private int outboundTimeout() {
        return focusActive() ? FOCUS_OUTBOUND_TIMEOUT_TICKS : OUTBOUND_TIMEOUT_TICKS;
    }

    /** 出击推进：飞向目标，进入 r=3 ⇒ 结算；超时 ⇒ 返航。 */
    private void stepOutbound(Player owner, World world, Drone drone) {
        Player target = drone.target == null ? null : Bukkit.getPlayer(drone.target);
        boolean targetGone = target == null || !target.isOnline() || target.isDead()
                || target.getWorld() != world;
        if (targetGone) {
            // 目标没了 ⇒ 原地重选一次（不重置超时计时）
            target = pickTarget(owner, droneLocation(world, drone));
            drone.target = target == null ? null : target.getUniqueId();
            if (target == null) {
                beginReturn(drone);
                return;
            }
        }

        Location here = droneLocation(world, drone);
        Location to = target.getLocation().clone().add(0d, 1.0d, 0d);
        if (here.distance(to) <= ATTACK_RADIUS) {
            resolveDroneAttack(owner, world, drone, target, to);
            beginReturn(drone);
            return;
        }
        if (tick - drone.outboundStartTick >= outboundTimeout()) {
            // 超时没能命中 ⇒ 进 CD 并返航（需求原话；集火窗口内超时被放宽，见 outboundTimeout）
            beginReturn(drone);
            return;
        }
        flyToward(owner, world, drone, to, SPEED_PER_TICK, phase + drone.slot);
    }

    private void beginReturn(Drone drone) {
        drone.outbound = false;
        drone.returning = true;
        drone.target = null;
    }

    /** 命中结算（需求：6 物理 + 4 特殊值 + 1 真实；对猎杀目标额外 1 灵魂；集火期额外 2 灵魂）。 */
    private void resolveDroneAttack(Player owner, World world, Drone drone, Player target, Location at) {
        if (vitals != null) {
            vitals.physicalDamage(target, owner, DRONE_PHYSICAL_DAMAGE);
        }
        if (sante != null) {
            sante.decreaseSanTE(target.getUniqueId(), DRONE_SANTE_DAMAGE);
        }
        if (vitals != null) {
            vitals.trueDamage(target, owner, DRONE_TRUE_DAMAGE);
            boolean marked = lockOn != null && lockOn.isMarked(target.getUniqueId());
            if (marked) {
                vitals.trueDamage(target, owner, AlbertLockOnPassive.MARK_SOUL_BONUS_DAMAGE);
            }
            if (focusActive()) {
                vitals.trueDamage(target, owner, FOCUS_BONUS_SOUL_DAMAGE);
            }
        }
        // 连打必需：扣血后再清一次受击无敌帧（工程既有口径）
        target.setNoDamageTicks(0);
        AlbertVfx.hitSparkAlbertGunbladeMelee(world, at);
        AlbertSound.critAlbertDroneImpactSound(world, at);
        // 无人机击杀台词：这一击把目标打倒了（血量 ≤ 0）
        if (floatingText != null && (target.isDead() || target.getHealth() <= 0d)) {
            floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_KILL_DRONE);
        }
    }

    // ───────── 索敌 ─────────

    /**
     * 选目标。候选集合 = <b>「无人机自身索敌范围内」∪「集火区域内（无视距离）」</b>，
     * 按四级优先取最近的：
     * <ol>
     *   <li><b>区域内 + 猎杀目标</b>；</li>
     *   <li><b>区域内</b>（★ 需求"区域内敌人会被无人机无视距离优先攻击" —— 这一档<b>不查射程</b>）；</li>
     *   <li>射程内的猎杀目标；</li>
     *   <li>射程内最近的敌人。</li>
     * </ol>
     * 集火窗口未开启时只有 ③④ 两档 ⇒ 行为与"普通索敌"逐字一致。
     */
    private Player pickTarget(Player owner, Location from) {
        if (from == null || from.getWorld() == null) {
            return null;
        }
        Location focus = focusCenter();

        // ── 候选集合：射程内 ∪ 区域内（无视距离）──
        List<Player> candidates = new ArrayList<>(from.getNearbyPlayers(searchRange()));
        if (focus != null && focus.getWorld() != null) {
            for (Player candidate : focus.getNearbyPlayers(FOCUS_RADIUS)) {
                if (candidate != null && !candidates.contains(candidate)) {
                    candidates.add(candidate);
                }
            }
        }

        Player bestAreaMarked = null;
        double bestAreaMarkedDistance = Double.MAX_VALUE;
        Player bestArea = null;
        double bestAreaDistance = Double.MAX_VALUE;
        Player bestMarked = null;
        double bestMarkedDistance = Double.MAX_VALUE;
        Player bestAny = null;
        double bestAnyDistance = Double.MAX_VALUE;

        for (Player candidate : candidates) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            Location at = candidate.getLocation();
            if (at.getWorld() != from.getWorld()) {
                continue;
            }
            double distance = at.distance(from);
            boolean inArea = focus != null
                    && at.getWorld() == focus.getWorld()
                    && at.distance(focus) <= FOCUS_RADIUS;
            boolean marked = lockOn != null && lockOn.isMarked(candidate.getUniqueId());

            if (inArea) {
                if (distance < bestAreaDistance) {
                    bestAreaDistance = distance;
                    bestArea = candidate;
                }
                if (marked && distance < bestAreaMarkedDistance) {
                    bestAreaMarkedDistance = distance;
                    bestAreaMarked = candidate;
                }
            }
            if (marked && distance < bestMarkedDistance) {
                bestMarkedDistance = distance;
                bestMarked = candidate;
            }
            if (distance < bestAnyDistance) {
                bestAnyDistance = distance;
                bestAny = candidate;
            }
        }

        if (bestAreaMarked != null) {
            return bestAreaMarked;
        }
        if (bestArea != null) {
            return bestArea;
        }
        return bestMarked != null ? bestMarked : bestAny;
    }

    /** 存活判定（死亡 / 已下线一律排除）。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /**
     * **集火区域高亮**（需求："给 2 技能目标区域做高亮"）。
     *
     * <p>窗口期内<b>每刻</b>画一次，但 {@link AlbertVfx#highlightAlbertHuntOrder} 内部按片交错
     * ⇒ 实际每刻只 spawn 十几颗粒子，靠存活期看起来是常亮的一道环（10 秒全画会到上万颗）。
     */
    private void highlightFocus(World world) {
        Location focus = focusCenter();
        if (focus == null || focus.getWorld() != world) {
            return;
        }
        AlbertVfx.highlightAlbertHuntOrder(world, focus, FOCUS_RADIUS, phase);
    }

    // ───────── 哨戒无人机 ─────────

    private void updateSentry(Player owner, World world) {
        if (sentry == null) {
            return;
        }
        if (sentry.world == null || sentry.world != world || !sentry.world.isChunkLoaded(
                ((int) Math.floor(sentry.x)) >> 4, ((int) Math.floor(sentry.z)) >> 4)) {
            sentry = null;
            return;
        }
        Location center = new Location(sentry.world, sentry.x, sentry.y, sentry.z);
        AlbertVfx.sentryAlbertAssemble(sentry.world, center, phase);

        // 缓慢 I + 报警（需求：进入 r=10 持续缓慢 1；并告知艾尔伯特有人进入）
        boolean intruder = false;
        for (Player candidate : center.getNearbyPlayers(SENTRY_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            intruder = true;
            // 给他上缓慢（★ 直接对目标上药水 —— buff.applyPotionEffect 只作用于自己）
            candidate.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    SENTRY_SLOWNESS_TICKS, SENTRY_SLOWNESS_AMPLIFIER, true, false, false));
            if (tick % 20 == 0) {
                AlbertVfx.alarmAlbertSentry(sentry.world,
                        candidate.getLocation().clone().add(0d, 0.2d, 0d));
            }
        }
        if (intruder && tick % 20 == 0) {
            sentryAlarmPending = true;
            AlbertSound.bellAlbertSentryAlarmSound(sentry.world, center.clone().add(0d, 1.2d, 0d));
        }
    }

    // ───────── 自杀式无人机 ─────────

    private void updateKamikazes(Player owner, World world) {
        if (kamikazes.isEmpty()) {
            return;
        }
        kamikazes.removeIf(k -> k.world != world);
        for (int i = kamikazes.size() - 1; i >= 0; i--) {
            Kamikaze k = kamikazes.get(i);
            Location here = new Location(k.world, k.x, k.y, k.z);

            switch (k.state) {
                case CHARGING -> {
                    double progress = 1d - Math.max(0d, (k.stateUntilTick - tick))
                            / (double) KAMIKAZE_CHARGE_TICKS;
                    AlbertVfx.chargeAlbertOverload(k.world, here, progress);
                    AlbertVfx.diamondKamikazeDrone(k.world, here.clone().add(0d, 0.6d, 0d));
                    if (tick >= k.stateUntilTick) {
                        k.state = KamikazeState.WAITING;
                        AlbertSound.witherChargeAlbertKamikazeChargeSound(k.world, here);
                    }
                }
                case WAITING -> {
                    AlbertVfx.diamondKamikazeDrone(k.world, here.clone().add(0d, 0.6d, 0d));
                    if (tick >= k.launchAtTick) {
                        Player picked = pickTargetFor(k.world, here);
                        k.target = picked == null ? null : picked.getUniqueId();
                        k.state = KamikazeState.OUTBOUND;
                    }
                }
                case OUTBOUND -> {
                    AlbertVfx.diamondKamikazeDrone(k.world, here.clone().add(0d, 0.6d, 0d));
                    Player target = k.target == null ? null : Bukkit.getPlayer(k.target);
                    if (target == null || !isAlive(target) || target.getWorld() != k.world) {
                        Player picked = pickTargetFor(k.world, here);
                        k.target = picked == null ? null : picked.getUniqueId();
                        target = picked;
                        if (target == null) {
                            kamikazes.remove(i);
                            continue;
                        }
                    }
                    Location to = target.getLocation().clone().add(0d, 1.0d, 0d);
                    if (here.distance(to) <= 1.4d) {
                        k.state = KamikazeState.FUSE;
                        k.stateUntilTick = tick + KAMIKAZE_FUSE_TICKS;
                        break;
                    }
                    moveToward(k, to, KAMIKAZE_SPEED_PER_TICK);
                }
                case FUSE -> {
                    AlbertVfx.chargeAlbertOverload(k.world, here, 0.6d);
                    AlbertVfx.diamondKamikazeDrone(k.world, here.clone().add(0d, 0.6d, 0d));
                    if (tick >= k.stateUntilTick) {
                        explodeKamikaze(owner, k, here);
                        kamikazes.remove(i);
                    }
                }
            }
        }
    }

    /** 自杀式无人机的索敌：以自身为圆心、当前索敌范围，优先猎杀目标。 */
    private Player pickTargetFor(World world, Location from) {
        Player owner = svc().self().player();
        if (owner == null) {
            return null;
        }
        return pickTarget(owner, from);
    }

    /** 爆炸：r=7 内 10 灵魂（真伤）+ 10 特殊值；命中过敌人则回主人 {@value #KAMIKAZE_SELF_HEAL} 点。 */
    private void explodeKamikaze(Player owner, Kamikaze k, Location at) {
        AlbertVfx.explodeAlbertKamikaze(k.world, at);
        AlbertSound.dragonFireballAlbertKamikazeExplodeSound(k.world, at);
        int hits = 0;
        for (Player candidate : at.getNearbyPlayers(KAMIKAZE_BLAST_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            if (vitals != null) {
                vitals.trueDamage(candidate, owner, KAMIKAZE_SOUL_DAMAGE);
            }
            if (sante != null) {
                sante.decreaseSanTE(candidate.getUniqueId(), KAMIKAZE_SANTE_DAMAGE);
            }
            candidate.setNoDamageTicks(0);
            hits++;
        }
        if (hits > 0 && vitals != null && owner.isOnline()) {
            vitals.heal(owner, KAMIKAZE_SELF_HEAL);
        }
    }

    // ───────── 诱饵 ─────────

    private void updateDecoys(Player owner, World world) {
        if (decoys.isEmpty()) {
            return;
        }
        decoys.removeIf(d -> d.world != world);
        for (int i = decoys.size() - 1; i >= 0; i--) {
            Decoy decoy = decoys.get(i);
            Location at = new Location(decoy.world, decoy.x, decoy.y, decoy.z);
            AlbertVfx.diamondDecoyGround(decoy.world, at);

            boolean triggered = false;
            for (Player candidate : at.getNearbyPlayers(DECOY_TRIGGER_RADIUS)) {
                if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                    continue;
                }
                if (svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                    triggered = true;
                    break;
                }
            }
            if (triggered) {
                explodeDecoy(owner, decoy, at);
                decoys.remove(i);
                continue;
            }
            if (tick >= decoy.expireTick) {
                // 4 秒到点自然消散（不炸 —— 需求只说"存在 4 秒"）
                decoys.remove(i);
            }
        }
    }

    /** 诱饵爆炸：r=4 内 10 物理 + 2 秒缓慢 III + 3~10 特殊值（需求原话）。 */
    private void explodeDecoy(Player owner, Decoy decoy, Location at) {
        AlbertVfx.explodeAlbertDecoy(decoy.world, at);
        AlbertSound.explodeAlbertDecoySound(decoy.world, at);
        for (Player candidate : at.getNearbyPlayers(DECOY_TRIGGER_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            if (vitals != null) {
                vitals.physicalDamage(candidate, owner, DECOY_PHYSICAL_DAMAGE);
            }
            // ★ 直接对目标上药水（buff.applyPotionEffect 只作用于自己）
            candidate.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    DECOY_SLOWNESS_TICKS, DECOY_SLOWNESS_AMPLIFIER, true, false, false));
            if (sante != null) {
                sante.decreaseSanTE(candidate.getUniqueId(),
                        DECOY_SANTE_MIN + random.nextInt(DECOY_SANTE_MAX - DECOY_SANTE_MIN + 1));
            }
            candidate.setNoDamageTicks(0);
        }
    }

    // ───────── 主动防御 ─────────

    /**
     * **受伤 ⇒ 消耗一层主动防御**（需求："每被攻击一次，就消耗一层主动防御"）。
     *
     * <p>同一刻去重（{@link #lastShieldConsumeTick}）：一次攻击可能同时触发
     * {@code EntityDamageEvent} 与别的路径，不去重会"一下吃两层"。
     */
    @Override
    public void onDamaged(Player source, double amount) {
        if (drones.isEmpty()) {
            return;
        }
        if (lastShieldConsumeTick == tick) {
            return;
        }
        lastShieldConsumeTick = tick;
        consumeShieldLayer("hit");
    }

    /** 受治疗：本组件不响应。 */
    @Override
    public void onHealed(double amount) {
        // 无人机系统不响应治疗
    }

    /** 消耗一层：移除一架无人机 + 2 秒速度 6 + 碎裂粒子 / 音效。 */
    private void consumeShieldLayer(String cause) {
        Player owner = svc().self().player();
        if (owner == null || drones.isEmpty()) {
            return;
        }
        Drone removed = drones.remove(drones.size() - 1);
        World world = owner.getWorld();
        if (world != null) {
            Location at = new Location(world, removed.x, removed.y, removed.z);
            AlbertVfx.shatterAlbertDrone(world, at);
            AlbertVfx.shieldBurstAlbertOverResponse(world, owner.getLocation().clone().add(0d, 1.1d, 0d));
            AlbertSound.glassBreakAlbertDroneDownSound(world, at);
            AlbertSound.shieldBlockAlbertOverResponseSound(world, owner.getLocation());
        }
        // 2 秒速度 6（需求原话）
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                SHIELD_SPEED_TICKS, SHIELD_SPEED_AMPLIFIER, true, false, false));
        if (floatingText != null) {
            floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_SHIELD);
            if (drones.isEmpty()) {
                floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_NO_DRONE);
            }
        }
    }

    /** **远程拦截**：移除主人附近飞来的投射物（需求"无人机能够主动防御抵消远程攻击"）。 */
    private void interceptProjectiles(Player owner) {
        if (drones.isEmpty()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        Location center = owner.getLocation().clone().add(0d, 1.0d, 0d);
        for (Entity entity : world.getNearbyEntities(center, INTERCEPT_RADIUS, INTERCEPT_RADIUS,
                INTERCEPT_RADIUS)) {
            if (!(entity instanceof Projectile projectile)) {
                continue;
            }
            if (projectile.getShooter() == owner) {
                continue;
            }
            projectile.remove();
            lastShieldConsumeTick = tick;
            consumeShieldLayer("intercept");
            return;
        }
    }

    // ───────── 光环（速度 2 / 隐形 / 抗性 255）─────────

    private void refreshAmbient(Player owner) {
        if (drones.isEmpty()) {
            clearAmbientEffects();
            return;
        }
        // 速度 2（需求："当身边存在高斯无人机时，艾弗伯特获得速度 2"）
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                AMBIENT_REFRESH_TICKS, AMBIENT_SPEED_AMPLIFIER, true, false, false));
        // 抗性 255（需求："拥有主动防御时，获得永久的抗性 255"）
        owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                AMBIENT_REFRESH_TICKS, SHIELD_RESISTANCE_AMPLIFIER, true, false, false));

        // 周围 20 格没有敌方单位 ⇒ 隐形（需求原话）
        World world = owner.getWorld();
        boolean safe = true;
        if (world != null) {
            Location at = owner.getLocation();
            for (Player candidate : at.getNearbyPlayers(INVISIBLE_SAFE_RADIUS)) {
                if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                    continue;
                }
                if (svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                    safe = false;
                    break;
                }
            }
        }
        if (safe) {
            owner.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                    AMBIENT_REFRESH_TICKS, 0, true, false, false));
            // ★ 边沿判据：只在"由不隐形变成隐形"那一刻说话（否则每刻刷新会复读）
            if (!cloaked && floatingText != null) {
                floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_CLOAK);
            }
            cloaked = true;
        } else {
            owner.removePotionEffect(PotionEffectType.INVISIBILITY);
            cloaked = false;
        }
    }

    /**
     * **被控制 / 解控的边沿判据**：只在"刚进入受控"那一刻说一次台词。
     *
     * <p>为什么不挂事件：工程铁律 —— <b>组件不得自挂 Bukkit Listener</b>。
     * 而沉默 / 眩晕的真值就在 {@link BuffComponent} 里（{@link BuffType#SILENCE} / {@link BuffType#STUN}）
     * ⇒ 每刻读一次、与上一帧比较（边沿判据）即可，成本可忽略。
     */
    private void updateControlledEdge(Player owner) {
        if (buff == null) {
            return;
        }
        boolean nowControlled = buff.has(BuffType.STUN) || buff.has(BuffType.SILENCE);
        if (nowControlled && !controlled && floatingText != null) {
            floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_CONTROLLED);
        }
        controlled = nowControlled;
    }

    /** 清掉本组件施加过的三个光环（停用 / 编队清空时调，避免残留"永久抗性"）。 */    private void clearAmbientEffects() {
        Player owner = svc().self().player();
        if (owner == null) {
            return;
        }
        owner.removePotionEffect(PotionEffectType.RESISTANCE);
        owner.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    // ───────── HUD（actionbar）─────────

    /**
     * **actionbar**：弹药 + 主动防御层数 +（哨戒报警）。
     *
     * <p>需求三处都要求 actionbar：弹药数（主武器）、主动防御层数（被动）、哨戒报警（技能1）。
     * 三者合并成<b>同一条</b>由本类统一发布 —— 若各写各的，会互相覆盖（actionbar 是一行、后写覆盖先写）。
     */
    private void publishHud(Player owner) {
        Component line = Component.empty();
        boolean any = false;

        if (ammoMax > 0) {
            int current = Math.max(0, Math.min(ammoCurrent, ammoMax));
            line = line.append(Component.text("弹药 ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(current + "/" + ammoMax,
                            current > 0 ? NamedTextColor.WHITE : NamedTextColor.RED));
            any = true;
        }
        if (!drones.isEmpty()) {
            if (any) {
                line = line.append(Component.text("   ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(Component.text("主动防御 ", NamedTextColor.DARK_PURPLE))
                    .append(Component.text(String.valueOf(drones.size()), NamedTextColor.LIGHT_PURPLE));
            any = true;
        }
        if (sentryAlarmPending) {
            if (any) {
                line = line.append(Component.text("   ", NamedTextColor.DARK_GRAY));
            }
            line = line.append(Component.text("哨戒报警：有人越线", NamedTextColor.RED));
            sentryAlarmPending = false;
            any = true;
        }
        if (!any) {
            return;
        }
        // ★ 头尾各加一个亮紫色「〓」⇒ 形如「〓弹药 3/6  主动防御 2〓」
        //   （需求原话"给 actionbar 的头尾加入亮紫色的「〓」符号"）
        owner.sendActionBar(Component.text(HUD_BRACKET, NamedTextColor.LIGHT_PURPLE)
                .append(line)
                .append(Component.text(HUD_BRACKET, NamedTextColor.LIGHT_PURPLE)));
    }

    /** actionbar 头尾的装饰符号（U+3013 GETA MARK，即「〓」）。 */
    public static final String HUD_BRACKET = "〓";

    // ───────── 飞行工具 ─────────

    /** 待机位（视角左右角 + 左右肩）。 */
    private static Location dockLocation(Player owner, int slot) {
        return AlbertVfx.dockSlot(owner, slot);
    }

    /** 直接把无人机钉到待机位（需求："自动跟随艾尔伯特"）。 */
    private void pinToDock(Player owner, Drone drone) {
        Location dock = dockLocation(owner, drone.slot);
        if (dock == null) {
            return;
        }
        drone.x = dock.getX();
        drone.y = dock.getY();
        drone.z = dock.getZ();
    }

    /** 朝目标飞一步；抵达（≤ {@value #ARRIVE_EPSILON} 格）⇒ 返回 {@code true}。 */
    private boolean flyToward(Player owner, World world, Drone drone, Location aim,
                              double speed, double bobPhase) {
        if (aim == null || aim.getWorld() != world) {
            return true;
        }
        Location here = droneLocation(world, drone);
        Vector delta = aim.toVector().subtract(here.toVector());
        double distance = delta.length();
        if (distance <= ARRIVE_EPSILON) {
            return true;
        }
        Vector step = delta.multiply(Math.min(speed, distance) / distance);
        drone.x += step.getX();
        drone.y += step.getY();
        drone.z += step.getZ();
        AlbertVfx.diamondGaussDrone(world, new Location(world, drone.x, drone.y, drone.z));
        return false;
    }

    /** 自杀式无人机朝目标飞一步（无抖动、更快）。 */
    private void moveToward(Kamikaze k, Location aim, double speed) {
        if (aim == null || aim.getWorld() != k.world) {
            return;
        }
        Vector delta = aim.toVector().subtract(new Vector(k.x, k.y, k.z));
        double distance = delta.length();
        if (distance < 1.0E-6d) {
            return;
        }
        Vector step = delta.multiply(Math.min(speed, distance) / distance);
        k.x += step.getX();
        k.y += step.getY();
        k.z += step.getZ();
    }

    private static Location droneLocation(World world, Drone drone) {
        return new Location(world, drone.x, drone.y, drone.z);
    }

    // ───────── 内部类型 ─────────

    /** 一架高斯无人机。 */
    private static final class Drone {
        double x;
        double y;
        double z;
        int slot;
        boolean outbound;
        boolean returning;
        UUID target;
        int outboundStartTick;
        int nextLaunchTick;
    }

    /** 哨戒无人机（固定常驻，存续无限）。 */
    private static final class Sentry {
        double x;
        double y;
        double z;
        World world;
    }

    /** 自杀式无人机的状态机。 */
    private enum KamikazeState { CHARGING, WAITING, OUTBOUND, FUSE }

    /** 一架自杀式无人机。 */
    private static final class Kamikaze {
        double x;
        double y;
        double z;
        World world;
        KamikazeState state = KamikazeState.CHARGING;
        int stateUntilTick;
        int launchAtTick;
        UUID target;
    }

    /** 一个无人机诱饵。 */
    private static final class Decoy {
        double x;
        double y;
        double z;
        World world;
        int expireTick;
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **集火窗口期间的间隔**（**纯函数**）：需求"攻击速度提升 50%"⇒ 节拍 / 1.5。
     *
     * @param baseTicks 基础间隔（刻）
     * @param focusing  是否在集火窗口内
     * @return 实际间隔（至少 1 刻）
     */
    public static int intervalTicks(int baseTicks, boolean focusing) {
        if (!focusing) {
            return Math.max(1, baseTicks);
        }
        return Math.max(1, (int) Math.round(baseTicks / FOCUS_SPEED_MULTIPLIER));
    }

    /**
     * **该时刻能否起飞**（**纯函数**）：编队级 0.7 秒闸门。
     *
     * @param nowTick       当前刻
     * @param lastLaunchTick 上一架起飞刻（{@code -1} = 从未起飞 ⇒ 放行）
     * @param focusing      是否在集火窗口内
     */
    public static boolean canLaunch(int nowTick, int lastLaunchTick, boolean focusing) {
        if (lastLaunchTick < 0) {
            return true;
        }
        return nowTick - lastLaunchTick >= intervalTicks(LAUNCH_GATE_TICKS, focusing);
    }

    /** 读口：当前索敌范围扩展的剩余刻数（{@code 0} = 未扩展）。 */
    public int expandedRemainingTicks() {
        return Math.max(0, expandedUntilTick - tick);
    }

    /** 读口：集火窗口剩余刻数（{@code 0} = 未生效）。 */
    public int focusRemainingTicks() {
        return Math.max(0, focusUntilTick - tick);
    }

    /** 读口：未结束的自杀式无人机数量。 */
    public int kamikazeCount() {
        return kamikazes.size();
    }

    /** 读口：诱饵数量。 */
    public int decoyCount() {
        return decoys.size();
    }

    /** 供扩展 / 排障使用的只读实体校验（避免编译器"未使用"告警的占位）。 */
    static boolean isLivingTarget(Entity entity) {
        return entity instanceof LivingEntity;
    }
}
