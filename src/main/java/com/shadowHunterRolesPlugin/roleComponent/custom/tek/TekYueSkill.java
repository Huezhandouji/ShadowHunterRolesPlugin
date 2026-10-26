package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 「特克」技能二：**落岳 · 凝灰岩**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>★ 两段式释放</b>：
 *       <ul>
 *         <li><b>第一次释放</b>：向上<b>跃起 {@value #LEAP_HEIGHT} 格</b>
 *             （利用 {@value #LEVITATION_SECONDS} 秒漂浮 buff 推上去），随后
 *             <b>正常原版下落</b>（交给重力，期间获得<b>抗性提升 II</b>，
 *             且由技能吃掉摔落伤害）；</li>
 *         <li><b>在空中再次释放</b>：切换为<b>加速下落</b>
 *             （{@value #FALL_FAST_PER_TICK} 格/刻，约 {@value #FALL_FAST_SECONDS} 秒落完）；</li>
 *         <li><b>★ 两种情况都是"下落完毕后"技能才进入冷却</b>（不是释放那一刻）；</li>
 *       </ul></li>
 *   <li><b>★ 必须完全落地后</b>才释放落地攻击：对落地范围（r = {@value #IMPACT_RADIUS}）内所有敌人造成
 *       <b>{@value #IMPACT_DAMAGE} 点物理伤害</b>与 <b>{@value #STUN_SECONDS} 秒眩晕</b>；</li>
 *   <li>落地位置产生<b>爆炸粒子</b>，同时叠加<b>一层「真理」</b>；</li>
 *   <li>冷却 {@value #COOLDOWN_SECONDS} 秒（= {@value #COOLDOWN_TICKS} 刻）。</li>
 * </ol>
 *
 * <h2>「跃起 15 格」与两档下落怎么实现</h2>
 * 先施 {@code LEVITATION}（漂浮）{@value #LEVITATION_SECONDS} 秒把玩家推上去 ——
 * 漂浮的原版效果是"持续上升"，配合 {@code TaskComponent#addScheduleLater} 在
 * {@value #RISE_TICKS} 刻后取消漂浮并开始下落。整段（跃起 + 下落）由 {@code update()} 每刻驱动：
 * <ul>
 *   <li><b>上升段</b>：每刻把 Y 抬 {@link #RISE_PER_TICK} 格，直到到达目标高度或刻数用尽；</li>
 *   <li><b>下落段</b>：每刻把 Y 压 <b>当前档位的步长</b>（慢速 § 或快速 §），
 *       <b>落到地面方块顶面</b>即结算。两档共用同一段代码，只有步长不同。</li>
 * </ul>
 * 直接写坐标（{@code teleport}）而不是靠原版物理，保证"整 15 格"与两档下落速度的确定性。
 * <p>★ <b>为什么必须 teleport 驱动、还要清 {@code fallDistance}</b>：
 * 若把下落完全交给原版重力，玩家会积攒约 15 格的摔落距离 ⇒ <b>落地时要吃十几点摔落伤害</b>。
 * teleport 会重置摔落距离，这里再每刻显式 {@code setFallDistance(0)} 做双保险
 * （这是"跃起类技能"的通用要求：位移由技能给，摔落伤害不该由玩家承担）。
 *
 * <h2>★「完全落地」的判据（需求：完全落地后才能释放落地攻击）</h2>
 * 落地判据是**两条同时成立**（真值表见 {@link #isFullyLanded(boolean, boolean)}）：
 * <ol>
 *   <li><b>脚所在那格不是实心</b>（人没被嵌进方块里）；</li>
 *   <li><b>紧邻下方那格是实心</b>（踩在东西上）。</li>
 * </ol>
 * <p>★ <b>只看第 ② 条是错的</b>（本技能的旧实现就是这样）：玩家离地面约 1 格时，下方那格
 * **已经是实心**了 ⇒ 会在<b>空中</b>提前放出落地攻击（"还没落地就爆了"）。
 * <p>下落过程改用 {@link #groundLevelY} <b>先把落点算出来</b>再精确落上去（不做"下一帧再说"，
 * 避免快速下落时穿过薄地面），落到之后再用上面两条复核；若落点站不住（虚空 / 被方块占住）⇒
 * {@link #endWithoutImpact}：<b>结束技能但不放出落地攻击</b>（不给出凭空收益），冷却照常启动。
 *
 * <h2>「眩晕」怎么实现（口径申报）</h2>
 * 眩晕在框架里是三件事：技能闸门 + 主武器闸门（都在目标的 {@code BuffComponent}，**只作用自己**）
 * + 原版失明/黑暗 + 移速归零。组件不能给"别人"上 {@code BuffType.STUN}（那是只作用自己的口），
 * 因此这里按<b>等价原版形态</b>施加：{@code SLOWNESS 255}（近似定身）+ {@code BLINDNESS} +
 * {@code DARKNESS}，时长 = 需求给的 1 秒。这是本工程对"给他人眩晕"的既有做法
 * （见 {@code SinThornEntangleSkill} 的"缓慢 255 + 失明"）。
 */
public class TekYueSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "tekYueSkill";

    // ───────── 数值口径（唯一修改点）─────────

    /** 跃起高度（格）。 */
    private static final double LEAP_HEIGHT = 15d;

    /** 漂浮 buff 时长（刻）：0.2 秒 = 4 刻。 */
    private static final int LEVITATION_TICKS = 4;

    /** 漂浮时长（秒；文案用）。 */
    private static final String LEVITATION_SECONDS = "0.2";

    /** 上升段总刻数（与漂浮时长一致）。 */
    private static final int RISE_TICKS = LEVITATION_TICKS;

    /** 每刻上升格数（= 高度 / 上升刻数）。 */
    private static final double RISE_PER_TICK = LEAP_HEIGHT / RISE_TICKS;

    /**
     * **档位一：不加速下落 = 完全交给原版重力**（需求："正常原版下落"）。
     *
     * <p>★ 与上一版的差别（改前必读）：上一版是"用 {@code teleport} 以 0.6 格/刻 匀速下落"，
     * 因为担心"交给重力会积攒摔落距离 ⇒ 落地吃十几点摔落伤害"。
     * <p>现在按需求放开给重力，但**摔落伤害由 {@code update()} 里每刻的
     * {@code owner.setFallDistance(0f)} 吃掉** ⇒ 拿到原版手感的同时不掉血。
     * <p>副作用（如实申报）：下落轨迹完全由原版物理 + 玩家操作决定 ⇒
     * 水平位移、落在平台上、被水流推开都由原版处理（"正常原版下落"本就该如此）。
     * <p>因此这一档**没有步长常量**（不驱动 Y），只保留一个"落地判据每刻都要查"的约定。
     */

    /**
     * **下落期间给的抗性提升增幅**（需求："下落时给个抗性2"）。
     * <p>原版增幅从 0 计 ⇒ {@code 1} = 抗性提升 II（每级减伤 20%）。
     */
    private static final int RESISTANCE_AMPLIFIER = 1;

    /**
     * **抗性的刷新时长（刻）**：下落期间每刻用这么长的时长重刷一次。
     *
     * <p>★ 为什么用"短时长 + 每刻刷新"而不是"给一次长时长"：
     * 需求是"<b>下落时</b>给抗性" ⇒ 落地后不该还留着。
     * 给一次 5 秒会在落地后继续生效好几秒；短时长刷新则落地后最多残留
     * {@value #RESISTANCE_REFRESH_DURATION_TICKS} 刻（0.2 秒），几乎不可感知。
     */
    private static final int RESISTANCE_REFRESH_DURATION_TICKS = 4;

    /**
     * **档位二：加速下落的步长（格/刻）** —— 在空中**再次释放**后切到这一档（需求："加速下落"）。
     *
     * <p>3.5 格/刻 ⇒ 下落 {@value #LEAP_HEIGHT} 格约 {@value #FALL_FAST_SECONDS} 秒
     * （{@value #FALL_FAST_TICKS} 刻），是"砸下来"的手感。
     */
    private static final double FALL_FAST_PER_TICK = 3.5d;

    /** 档位二的下落时长（秒；文案与推导用）。 */
    private static final String FALL_FAST_SECONDS = "0.2";

    /** 档位二的预期刻数（= 高度 / 步长）。 */
    private static final int FALL_FAST_TICKS = (int) Math.ceil(LEAP_HEIGHT / FALL_FAST_PER_TICK);

    /** 落地判定半径（格）。 */
    private static final double IMPACT_RADIUS = 5d;

    /** 落地伤害（物理）。 */
    private static final double IMPACT_DAMAGE = 15d;

    /** 眩晕时长（刻）：1 秒 = 20 刻。 */
    private static final int STUN_TICKS = 20;

    /** 眩晕时长（秒；文案用）。 */
    private static final String STUN_SECONDS = "1";

    /** 眩晕的缓慢增幅（255 ⇒ 近似无法移动）。 */
    private static final int STUN_SLOWNESS_AMPLIFIER = 255;

    /** 冷却（刻）：12 秒 = 240 刻。 */
    private static final int COOLDOWN_TICKS = 240;

    /** 冷却（秒；文案用）。 */
    private static final String COOLDOWN_SECONDS = "12";

    /** 能量消耗：0。 */
    private static final int ENERGY_COST = 0;

    /**
     * 下落段的**兜底上限**（刻）。
     *
     * <p>★ 它**不是**"到点就结算伤害"的开关：需求要求"完全落地后才能释放落地攻击"，
     * 因此超时只会走 {@link #endWithoutImpact}（收工但**不放**落地攻击）。
     * 只在"落点永远站不住"（掉进虚空 / 落点被方块占埋）时才会命中。
     * <p>正常下落远快于本值：原版重力从 {@value #LEAP_HEIGHT} 格落下约 20 刻、
     * 快档约 {@value #FALL_FAST_TICKS} 刻，都远小于本值。
     */
    private static final int FALL_GUARD_TICKS = 100;

    private VitalsComponent vitals;
    private BuffComponent buff;
    private TaskComponent timer;
    private HotbarRenderComponent render;
    private TekDestinyPassive destiny;

    // ───────── 运行期状态 ─────────

    /**
     * **阶段**（两段式释放的状态机）。
     *
     * <table border="1">
     *   <tr><th>阶段</th><th>含义</th><th>下落步长</th></tr>
     *   <tr><td>{@link #IDLE}</td><td>不在役（可释放 / 冷却中）</td><td>—</td></tr>
     *   <tr><td>{@link #RISING}</td><td>跃起中（漂浮推升）</td><td>—</td></tr>
     *   <tr><td>{@link #FALLING_NORMAL}</td><td><b>正常原版下落</b>（第一次释放后的默认档：交给重力，
     *       不吃摔落伤害，期间给抗性 II）</td>
     *       <td>原版物理（不驱动 Y）</td></tr>
     *   <tr><td>{@link #FALLING_FAST}</td><td><b>加速下落</b>（空中再次释放后切换）</td>
     *       <td>{@value #FALL_FAST_PER_TICK} 格/刻</td></tr>
     * </table>
     */
    enum Phase { IDLE, RISING, FALLING_NORMAL, FALLING_FAST }

    private Phase phase = Phase.IDLE;

    /**
     * **释放一次技能后的阶段**（纯函数 ⇒ 可离线单测见 {@code TekYuePhaseTransitionTest}）。
     *
     * <p>这就是"两段式释放"的全部规则（需求逐条）：
     * <table border="1">
     *   <tr><th>当前阶段</th><th>再释放一次的结果</th></tr>
     *   <tr><td>{@link Phase#IDLE}（在地面）</td><td>{@link Phase#RISING} —— 向上跃起，
     *       之后自动进入"正常原版下落"</td></tr>
     *   <tr><td>{@link Phase#RISING}（跃起中）</td><td>{@link Phase#FALLING_FAST} —— 加速下落
     *       （顺带撤掉漂浮）</td></tr>
     *   <tr><td>{@link Phase#FALLING_NORMAL}（慢速下落中）</td><td>{@link Phase#FALLING_FAST}
     *       —— 加速下落</td></tr>
     *   <tr><td>{@link Phase#FALLING_FAST}（已在加速）</td><td><b>不变</b>（幂等）</td></tr>
     * </table>
     *
     * @param current 当前阶段
     * @return 释放后的阶段（与 {@code current} 相同 = 本次释放无效果）
     */
    static Phase phaseAfterCast(Phase current) {
        if (current == Phase.IDLE) {
            return Phase.RISING;
        }
        //跃起中 / 慢速下落中 ⇒ 切加速；已在加速 ⇒ 保持（幂等）
        return Phase.FALLING_FAST;
    }

    /** 上升 / 下落已累计刻数。 */
    private int phaseTicks;

    /** 跃起起点（结算落地粒子与"是否还在空中"判据）。 */
    private Location leapOrigin;

    /** 取消漂浮的任务句柄（角色被清时兜底取消）。 */
    private ScheduledHandle levitationCancelTask;

    /** 粒子相位。 */
    private double phase05;

    public TekYueSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<TekYueSkill> {

        public Specification() {
            super(Component.text("落岳"),
                    List.of(Component.text("释放：跃起 " + (int) LEAP_HEIGHT + " 格（利用 "
                                    + LEVITATION_SECONDS + " 秒漂浮）后正常原版下落"),
                            Component.text("下落期间获得抗性提升 II"),
                            Component.text("在空中再次释放：改为加速下落"),
                            Component.text("下落完毕后技能进入冷却，对落地范围内（r = "
                                    + (int) IMPACT_RADIUS + "）所有敌人造成 "
                                    + (int) IMPACT_DAMAGE + " 点物理伤害与 " + STUN_SECONDS + " 秒眩晕"),
                            Component.text("落地位置产生爆炸粒子，同时叠加一层「真理」"),
                            Component.text("冷却 " + COOLDOWN_SECONDS + " 秒")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.TUFF);
            requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(TaskComponent.class).requires(HotbarRenderComponent.class)
                    .requires(TekDestinyPassive.class).requires(FactionComponent.class);
        }

        @Override
        public TekYueSkill create(String id, ComponentServicesPort services) {
            return new TekYueSkill(id, services, this);
        }
    }

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        timer = svc().components().get(TaskComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
        destiny = svc().components().get(TekDestinyPassive.class);
    }

    @Override
    public void stop() {
        if (levitationCancelTask != null) {
            levitationCancelTask.cancel();
            levitationCancelTask = null;
        }
        phase = Phase.IDLE;
        phaseTicks = 0;
        leapOrigin = null;
    }

    /**
     * **两段式释放**（需求）：
     * <ol>
     *   <li><b>地面 / 不在役时释放</b> ⇒ 起手向上跃起，随后进入
     *       {@link Phase#FALLING_NORMAL}（<b>不加速下落</b>）；</li>
     *   <li><b>在空中（跃起中或慢速下落中）再次释放</b> ⇒ 切到
     *       {@link Phase#FALLING_FAST}（<b>加速下落</b>）；
     *       若还在跃起段，顺带把漂浮撤掉，立刻转下落；</li>
     *   <li>已经是加速档 ⇒ 重复按无效（幂等）。</li>
     * </ol>
     * <p>★ <b>冷却不在本方法里启动</b>：需求是"下落完毕后技能进入冷却" ⇒ 由
     * {@link #impact} / {@link #endWithoutImpact} 在收尾时启动。
     * <p>冷却中（含落地后的 12 秒）不能再起手；但"空中再次释放"不受冷却影响
     * （那一段本来就没有冷却，冷却要等落地才起算）。
     */
    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || vitals == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        //★ 迁移规则只写在 phaseAfterCast 里（唯一真值来源，可离线单测）
        Phase next = phaseAfterCast(phase);

        //① 已在加速下落 ⇒ 本次释放无效果（幂等）
        if (next == phase) {
            return;
        }

        //② 起手跃起（受冷却限制：冷却中不能再起手）
        if (next == Phase.RISING) {
            if (isCoolingDown()) {
                return;
            }
            startLeap(owner, world);
            return;
        }

        //③ 还在空中（跃起中 / 慢速下落中）⇒ 加速下落
        accelerateFall(owner);
    }

    /**
     * **起手跃起**（第一次释放）：上漂浮、记账起点、转入 {@link Phase#RISING}。
     * <p>冷却**不**在这里启动（需求："下落完毕后技能进入冷却"，见 {@link #onCast} 的口径）。
     */
    private void startLeap(Player owner, World world) {
        //漂浮起跳：先给自己上 LEVITATION（对自己 ⇒ 走 buff 的记账口是对的）
        buff.applyPotionEffect(PotionEffectType.LEVITATION.createEffect(LEVITATION_TICKS, 4));
        //到点取消漂浮（否则上升段结束后还会持续飘）
        levitationCancelTask = timer.addScheduleLater(this, LEVITATION_TICKS, () -> {
            Player p = svc().self().player();
            if (p != null) {
                p.removePotionEffect(PotionEffectType.LEVITATION);
            }
        });

        leapOrigin = owner.getLocation().clone();
        phase = Phase.RISING;
        phaseTicks = 0;
        phase05 = 0d;

        TekSound.yueLeapSound(world, owner.getLocation());
        repaint();
    }

    /**
     * **切换为加速下落**（需求：在空中再次释放 ⇒ 加速下落）。
     *
     * <p>若此刻还在 {@link Phase#RISING}：把漂浮撤掉（否则漂浮会持续把玩家往上推，
     * 与"加速下落"互相打架），并把下落起点重记为当前位置。
     */
    private void accelerateFall(Player owner) {
        boolean wasRising = phase == Phase.RISING;

        //撤掉漂浮（跃起段中途加速时必须做）
        owner.removePotionEffect(PotionEffectType.LEVITATION);
        if (levitationCancelTask != null) {
            levitationCancelTask.cancel();
            levitationCancelTask = null;
        }

        phase = Phase.FALLING_FAST;
        phaseTicks = 0;
        if (wasRising) {
            //刚从跃起段切过来 ⇒ 下落起点按当前位置重算（不沿用旧 leapOrigin）
            leapOrigin = owner.getLocation().clone();
        }

        World world = owner.getWorld();
        if (world != null) {
            //加速那一刻给一声提示音（与跃起音、落地音都区分开）
            TekSound.yueAccelerateSound(world, owner.getLocation());
        }
        repaint();
    }

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || phase == Phase.IDLE) {
            return;
        }
        //★ 在空中死亡（跃起或下落途中）⇒ 直接收工：
        //  不 teleport 尸体、不放落地攻击、也不起冷却（人都死了，冷却无意义）。
        if (owner.isDead() || owner.getHealth() <= 0d) {
            phase = Phase.IDLE;
            phaseTicks = 0;
            leapOrigin = null;
            repaint();
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            phase = Phase.IDLE;
            return;
        }
        phase05 += 0.5d;
        phaseTicks++;

        //★ 位移是技能给的（跃起 15 格）⇒ 摔落伤害不该由玩家承担。
        //  第一档已交给原版重力 ⇒ 不这么做的话落地要吃十几点摔落伤害。
        //  每刻清零 + teleport 自身的重置，两层叠加（见 class javadoc）。
        owner.setFallDistance(0f);

        if (phase == Phase.RISING) {
            Location current = owner.getLocation();
            double nextY = current.getY() + RISE_PER_TICK;
            double ceilingY = (leapOrigin != null ? leapOrigin.getY() : current.getY()) + LEAP_HEIGHT;
            if (phaseTicks >= RISE_TICKS || nextY >= ceilingY) {
                //到达顶点 ⇒ 转入**正常原版下落**（需求：第一次释放后正常原版下落）
                if (nextY > ceilingY) {
                    nextY = ceilingY;
                }
                Location at = current.clone();
                at.setY(nextY);
                owner.teleport(at);
                phase = Phase.FALLING_NORMAL;
                phaseTicks = 0;
                return;
            }
            Location at = current.clone();
            at.setY(nextY);
            owner.teleport(at);
            TekVfx.leapColumn(world, owner.getLocation());
            return;
        }

        //★ 下落期间给抗性提升 II（需求："下落时给个抗性2"）。
        //  短时长 + 每刻刷新 ⇒ 落地后最多残留 RESISTANCE_REFRESH_DURATION_TICKS 刻。
        //  对自己 ⇒ 走 buff 的记账口是正确的（那个口只作用自己，见流程文档 §3）。
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.RESISTANCE.createEffect(
                    RESISTANCE_REFRESH_DURATION_TICKS, RESISTANCE_AMPLIFIER));
        }

        //──────────────── 以下为下落段 ────────────────
        //  · FALLING_NORMAL = 正常原版下落（**不驱动 Y**，交给重力；需求"正常原版下落"）
        //  · FALLING_FAST   = 加速下落（teleport 驱动，精确落到地面顶面）
        //★ 两档都必须"完全落地"才结算（需求）⇒ 不允许在空中提前结算
        Location current = owner.getLocation();

        if (phase == Phase.FALLING_NORMAL) {
            //正常原版下落：**不碰 Y**（重力与玩家操作决定轨迹），只判"是否已完全落地"。
            if (isFullyLanded(current)) {
                impact(owner, current);
                return;
            }
            //兜底：落点久久站不住（掉进虚空 / 卡在方块里）⇒ 收工但不放落地攻击
            if (phaseTicks >= FALL_GUARD_TICKS) {
                endWithoutImpact(owner);
            }
            return;
        }

        double groundY = groundLevelY(current);
        double nextY = current.getY() - FALL_FAST_PER_TICK;

        if (nextY <= groundY) {
            //本刻会触地 ⇒ **精确**落到"地面方块顶面"（不做"下一帧再说"，避免隧穿）
            Location landing = current.clone();
            landing.setY(groundY);
            owner.teleport(landing);

            //★ 必须再确认"确实站稳了"才放出落地攻击：
            //  旧实现只要"下方一格是实心"就结算 ⇒ 离地约 1 格时就提前放出了（已修）。
            if (isFullyLanded(landing)) {
                impact(owner, landing);
                return;
            }
            //落点站不住（被方块占住 / 整列无地面）⇒ 不结算；只有兜底超时才收工
            if (phaseTicks >= FALL_GUARD_TICKS) {
                endWithoutImpact(owner);
            }
            return;
        }

        //加速档仍在空中 ⇒ 继续快速下落（未落地前绝不放落地攻击）
        Location at = current.clone();
        at.setY(nextY);
        owner.teleport(at);
        TekVfx.dashTrail(world, owner.getLocation(), 0.3d);
    }

    /**
     * **从该位置向下找"地面高度"**：同列第一个有碰撞箱的方块的**顶面 Y**。
     *
     * <p>从"脚所在方块的下方一格"开始扫（不是脚那格），因为"站在地面上"意味着
     * <b>脚那格是空的、它下面那格是实心的</b> ⇒ 从脚那格开始扫会在玩家被方块嵌住时得出错误高度。
     *
     * <p><b>精度口径（如实申报）</b>：判据按**整格**算（`Block#getBoundingBox()` 是整个方块立方体，
     * 不是碰撞形状）⇒ 半砖 / 楼梯 / 雪层这类**不满一格**的地面，落点会取到<b>方块顶面</b>，
     * 即人比实际表面高 0~0.5 格。这是有意的取舍：判据用整格是确定性的、不会漏判，
     * 而"人比半砖高半格"只影响观感、不影响"是否已落地"的结论（下方仍是实心方块）。
     *
     * @return 地面顶面 Y；整列没有实心方块（虚空）⇒ 世界的 {@code minHeight}
     *         （"能站的最低处"，配合 {@link #isFullyLanded} 会判为站不住 ⇒ 不结算伤害）
     */
    private static double groundLevelY(Location from) {
        World world = from.getWorld();
        if (world == null) {
            return from.getY();
        }
        int bx = from.getBlockX();
        int bz = from.getBlockZ();
        return groundLevelY(from.getBlockY() - 1, world.getMinHeight(),
                by -> world.getBlockAt(bx, by, bz).getBoundingBox().getVolume() > 0d);
    }

    /**
     * **地面高度扫描**（纯函数 ⇒ 可离线单测见 {@code TekYueLandingTest}）。
     *
     * <p>从 {@code startBlockY} 向下逐格找第一个实心方块，返回它<b>顶面</b>的 Y
     * （方块占 {@code [by, by+1)} ⇒ 顶面 = {@code by + 1}）；一路到 {@code minY} 都没有实心方块
     * （虚空）⇒ 返回 {@code minY}。
     *
     * @param startBlockY 起始方块 Y（调用方传"脚所在方块 − 1"）
     * @param minY        世界最低高度（扫描下界）
     * @param solidAt     "该 Y 的方块是否有碰撞箱"的判据（把 Bukkit 访问挡在纯函数之外）
     * @return 地面顶面 Y
     */
    static double groundLevelY(int startBlockY, int minY, java.util.function.IntPredicate solidAt) {
        for (int by = startBlockY; by >= minY; by--) {
            if (solidAt.test(by)) {
                return by + 1d;
            }
        }
        return minY;
    }

    /**
     * **是否"完全落地"**（需求判据，纯函数 ⇒ 可离线单测见 {@code TekYueLandingTest}）。
     *
     * <p>两条同时成立才算落地：
     * <ol>
     *   <li><b>脚所在那格不是实心</b>（人没被嵌进方块里）；</li>
     *   <li><b>紧邻下方那格是实心</b>（踩在东西上）。</li>
     * </ol>
     * <p>★ 只看第 ② 条是**错的**：玩家离地面约 1 格时，下方那格就已经是实心了 ⇒
     * 会在空中提前放出落地攻击。必须同时要求第 ① 条。
     *
     * @param feetBlockSolid  脚所在方块是否有碰撞箱
     * @param belowBlockSolid 脚下方紧邻方块是否有碰撞箱
     */
    static boolean isFullyLanded(boolean feetBlockSolid, boolean belowBlockSolid) {
        return !feetBlockSolid && belowBlockSolid;
    }

    /** 几何取值 + 委托 {@link #isFullyLanded(boolean, boolean)}（本方法要 Bukkit，故只算不判）。 */
    private static boolean isFullyLanded(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        boolean feetSolid = at.getBlock().getBoundingBox().getVolume() > 0d;
        boolean belowSolid = at.clone().subtract(0d, 1d, 0d).getBlock()
                .getBoundingBox().getVolume() > 0d;
        return isFullyLanded(feetSolid, belowSolid);
    }

    /**
     * **无法落地时收工**（虚空 / 落点站不住）：结束技能但**不放出落地攻击**。
     *
     * <p>为什么宁可空放也不在空中结算：需求要求"完全落地后才能释放落地攻击" ⇒
     * 在虚空里给出 15 点物理 + 眩晕属于凭空收益。冷却照常启动，避免玩家反复刷这个状态。
     */
    private void endWithoutImpact(Player owner) {
        phase = Phase.IDLE;
        phaseTicks = 0;
        World world = owner != null ? owner.getWorld() : null;
        if (world != null) {
            TekSound.yueAbortSound(world, owner.getLocation());
        }
        startCooldown();
        repaint();
    }

    /** **落地结算**：r = {@link #IMPACT_RADIUS} 内所有敌人 ⇒ 15 点物理 + 1 秒眩晕 + 一层真理。 */
    private void impact(Player owner, Location landing) {
        World world = landing.getWorld();
        phase = Phase.IDLE;
        phaseTicks = 0;

        if (world != null) {
            TekVfx.impactBurst(world, landing.clone().add(0d, 0.05d, 0d), IMPACT_RADIUS, phase05);
            TekSound.yueImpactSound(world, landing);
        }

        for (Player victim : landing.getNearbyPlayers(IMPACT_RADIUS)) {
            if (victim == null || victim.equals(owner) || !isAlive(victim)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) {
                continue;
            }
            vitals.physicalDamage(victim, owner, IMPACT_DAMAGE);
            applyStun(victim);
            if (destiny != null) {
                destiny.onHit(victim);
            } else {
                TekTruth.addOne(victim.getUniqueId());
            }
        }

        startCooldown();
        repaint();
    }

    /**
     * **给他人上"眩晕"**（等价原版形态）：缓慢 255 + 失明 + 黑暗，时长 {@link #STUN_TICKS} 刻。
     * <p>★ 直接作用于目标（{@code victim.addPotionEffect}）—— 不能用 {@code buff.applyPotionEffect}，
     * 那个口只作用自己（见流程文档 §3 高频踩坑）。
     */
    private void applyStun(Player victim) {
        victim.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(
                STUN_TICKS, STUN_SLOWNESS_AMPLIFIER));
        victim.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(STUN_TICKS, 1));
        victim.addPotionEffect(PotionEffectType.DARKNESS.createEffect(STUN_TICKS, 1));
    }

    /** 请求重绘热键栏（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.markDirty();
        }
    }

    /** 是否正在跃起 / 下落（排障读口）。 */
    public boolean isAirborne() {
        return phase != Phase.IDLE;
    }

    /** 当前阶段名（排障读口）。 */
    public String phaseName() {
        return phase.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** 是否处于"加速下落"档（排障读口）。 */
    public boolean isAccelerating() {
        return phase == Phase.FALLING_FAST;
    }

    /** 暴露给单测的下落步长（加速档）。 */
    static double fallFastPerTickForTest() {
        return FALL_FAST_PER_TICK;
    }

    /** 暴露给单测的抗性增幅（需求："下落时给个抗性2" ⇒ 必须 = 1）。 */
    static int resistanceAmplifierForTest() {
        return RESISTANCE_AMPLIFIER;
    }

    /** 存活判定。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    // ───────── 技能物品画法：在役（跃起 / 下落中）⇒ 常亮光效 ─────────

    /**
     * 基类三态画法 + 本技能的两条状态表达：
     * <ul>
     *   <li><b>跃起 / 下落中（不在役以外）⇒ 常亮光效</b>（需求：正在释放时看得见；
     *       同时提示"再按一次可以加速下落"）；
     *       ★ 被眩晕/沉默（DISABLED，红屏障）时不换材质、只叠光效；</li>
     *   <li>冷却走完只是能量不足（本技能耗能 0 ⇒ 不可达，但口径照抄规范，保持一致）。</li>
     * </ul>
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
        if (phase != Phase.IDLE) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
            return stack;
        }
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
