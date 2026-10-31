package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 冷识（{@code lethe}）的主动技能「**重塑**」（槽位 3）：**引导 → 立方体坠落 → 浮空**。
 *
 * <h2>玩法（四段）</h2>
 * <ol>
 *   <li><b>引导</b> {@value #CHANNEL_TICKS} 刻（1.25 秒）：自身缓慢 III；**其它两个技能禁用**
 *       （本类暴露 {@link #isChanneling()}，遗忘 / 召圣在 {@code onCast} 开头问一次）；
 *       并 {@code resonance.suspendRingDamage(true)} 暂停带标记箭的被动伤害 ——
 *       否则引导期间共鸣圈会先把目标打死，"浮空 + 吸血"这一段就无从发生；</li>
 *   <li><b>坠落</b> {@value #FALL_TICKS} 刻：一颗巨大的蓝色立方体在冷识**引导结束时的位置**快速落下；</li>
 *   <li><b>爆开</b>：对落点 r={@value #MAIN_RADIUS} 的敌人造成 {@value #SOUL_DAMAGE} 灵魂(TRUE)
 *       + {@value #SANTE_DAMAGE} 特殊值；并在**每个带标记的箭的位置**生成小立方体爆炸
 *       （r={@value #MARK_RADIUS}，同样伤害）；两处命中去重后再统一结算；</li>
 *   <li><b>浮空</b>：所有受到伤害的敌人立刻向上浮空 {@value #FLOAT_HEIGHT} 格、持续
 *       {@value #FLOAT_TICKS} 刻（3 秒）；浮空结束给缓降保护（见「浮空的实现口径」）。</li>
 * </ol>
 * 冷却：基础 {@value #BASE_COOLDOWN_SECONDS} 秒；进化[4]「救赎」{@link LetheEvolutionPassive#hasReducedReshapeCooldown()}
 * 为 {@code true} 时取一半。起算点是**落点爆开那一刻**（技能真正产生结算的时刻）：引导被打断 /
 * 玩家不可用 ⇒ 不启动冷却（"冷却只在成功路径"）。
 *
 * <h2>★ 浮空的实现口径（确定性优先，选"逐刻抬升"而不是漂浮药水）</h2>
 * 需求允许"漂浮/缓降药水"或"逐刻抬升"两种。本类选**逐刻抬升**：
 * <ul>
 *   <li>漂浮药水的上升速度由服务端内部决定（约 {@code 0.05 × 增幅} 每刻，还受玩家输入、碰撞影响），
 *       无法保证"3 秒恰好 10 格"——同一次施放换台机器可能就少半格；</li>
 *   <li>逐刻抬升的公式是固定闭式：第 {@code n} 刻的 Y = 起始 Y + {@value #FLOAT_HEIGHT} ×
 *       min(1, n/{@value #FLOAT_TICKS})，第 {@value #FLOAT_TICKS} 刻**恰好** +{@value #FLOAT_HEIGHT} 格，
 *       与帧率 / 输入 / 碰撞全部无关 ⇒ 可复现、可对拍；</li>
 *   <li>每刻清零 {@code fallDistance}，并把"浮空结束"这一拍用于缓降保护（走 {@code BuffComponent}
 *       的跨玩家账本入口），因此抬升本身不会变成一次额外的摔落伤害。</li>
 * </ul>
 *
 * <h2>★ 进化[5]「新生」的两条吸血，以及本类对第二条的实现选择</h2>
 * <ol>
 *   <li><b>浮空目标</b>：生效窗口内每 {@value #DRAIN_INTERVAL_TICKS} 刻（0.25 秒），对每个仍在浮空的
 *       命中目标各回 1 生命 + 1 特殊值（持续到浮空结束）——本类自己持有浮空名单，最直接；</li>
 *   <li><b>共鸣圈中的敌人</b>：每 {@value #RING_FEEDBACK_INTERVAL_TICKS} 刻（0.5 秒）为冷识回 1 生命
 *       + 1 特殊值。<b>契约偏差（如实申报）</b>：§3.1 冻结的共鸣组件接口里**没有**"圈内伤害"的回调面
 *       （只有 {@code suspendRingDamage} / {@code markedArrowPoints} 等），而本工程不允许组件自行挂
 *       Bukkit 事件监听。因此本类采用"**由本技能在生效窗口内轮询**"这一变体：把
 *       {@link LetheResonanceComponent#markedArrowPoints()}（含已停的箭 = 共鸣圈心）当作圈心，
 *       已被本技能命中且落在某个圈心 {@value #RING_RADIUS} 格内的敌人，每 0.5 秒反哺一次。
 *       若将来共鸣组件暴露圈伤害回调，这一段可以整体替换为订阅（接口面不变）。</li>
 * </ol>
 *
 * <h2>★ 表现归属（任务书 vs 契约 + D 道实现，如实申报）</h2>
 * <ul>
 *   <li><b>{@code reshapeChannel} 这个 Vfx 函数不存在</b>：任务书要求"reshapeChannel / reshapeCubeFall / …
 *       + 对应音效"，但契约 §3.4 的冻结清单里没有 {@code reshapeChannel(World, Location)}，D 道的
 *       {@code LetheVfx} 也没有实现它（只有 {@code LetheSound.reshapeChannel} 音效）。
 *       按"契约不满足需求就照契约写并申报"的纪律，本类**不发明**该函数：引导期的画面改用
 *       {@code LetheVfx.reshapeCubeFall(world, at, tick)} 的 tick 轴表达（D 道把"引导期方块悬停旋转 +
 *       落体"合成了一个函数，tick 从引导开始计，见 {@link #CUBE_ANIMATION_TICKS}），
 *       音效仍用现成的 {@code LetheSound.reshapeChannel}。若 Lead 要严格按任务书呈现"引导"与"落体"
 *       两套独立粒子，需要 D 道补一个 {@code reshapeChannel} 变体。</li>
 *   <li>其余 {@code reshapeCubeFall / reshapeImpact / reshapeShard / reshapeFloat / reshapeMarkExplosion}
 *       与音效 {@code reshapeChannel / reshapeImpact / reshapeFloat} 的调用点都在本类（D 道的共鸣/召唤
 *       动作不涉及重塑，不存在双份播放）。</li>
 * </ul>
 *
 * <h2>闸门与禁用</h2>
 * 施放闸门（冷却 / 沉默眩晕）在 {@link #onCast(CastSignal)} 里自判（listener 只查冷却）；
 * 另外本类在引导 / 坠落 / 生效期间忽略重复施放。{@link #isChanneling()} **只**在 1.25 秒引导期为
 * {@code true}（契约口径 = "引导期间其它技能禁用"），坠落与浮空窗口不冻结其它技能。
 */
public class LetheReshapeSkill extends Skill {

    /** 本组件的登记 id（与契约 §1 逐字一致）。 */
    public static final String ID = "lethe_reshape_skill";

    // ───────── 数值（唯一修改点）─────────

    /** 基础冷却（1300 刻 = 65 秒）。 */
    private static final int BASE_COOLDOWN_TICKS = 1300;

    /** 基础冷却秒数（只用于描述文案；与 {@link #BASE_COOLDOWN_TICKS} 同源换算）。 */
    private static final int BASE_COOLDOWN_SECONDS = BASE_COOLDOWN_TICKS / 20;

    /** 引导时长（25 刻 = 1.25 秒）。 */
    private static final int CHANNEL_TICKS = 25;

    /** 引导期缓慢药水的安全余量（刻）：结束时显式摘掉，余量只保证引导期间不过期。 */
    private static final int SLOWNESS_SAFETY_TICKS = 10;

    /** 引导期缓慢等级 III ⇒ amplifier 2。 */
    private static final int CHANNEL_SLOWNESS_AMPLIFIER = 2;

    /** 立方体下落动画时长（5 刻 = 0.25 秒，"快速落下"）。 */
    private static final int FALL_TICKS = 5;

    /**
     * 立方体坠落的**总动画刻数**（40 刻 = 2 秒）。
     * <p>这个数来自 D 道 {@code LetheVfx.reshapeCubeFall} 的契约：它的 {@code tick} 参数是"引导开始起的刻数"，
     * 40 刻时正好落到地面（函数注释：「0..40 tick 线性从 11.5 格落到地面」）。
     * <p>因此本类把这一条 40 刻的动画铺在"引导 25 刻 + 下落 5 刻"上：引导期到 tick 24（方块缓缓降，
     * 玩家看见它在头顶成型），下落期每刻 +{@value #CUBE_FALL_STEP} 刻（从 28 到 40，方块快速砸下），
     * 第 5 个下落帧恰好 tick 40 ⇒ 立方体落地的那一帧就是爆开帧。这样"缓慢旋转地下压 → 快速落下 → 爆开"
     * 三个语义都由**同一个函数**的 tick 轴表达，不额外发明第二套时间。
     */
    private static final int CUBE_ANIMATION_TICKS = 40;

    /** 下落期每刻推进的立方体动画刻数：(40 − 25) / 5 = 3（保证第 5 个下落帧正好是 tick 40）。 */
    private static final int CUBE_FALL_STEP = (CUBE_ANIMATION_TICKS - CHANNEL_TICKS) / FALL_TICKS;

    /** 主爆点半径（格）。 */
    private static final double MAIN_RADIUS = 15d;

    /** 每个标记箭位置的二次爆炸半径（格）。 */
    private static final double MARK_RADIUS = 10d;

    /** 每次命中的灵魂伤害（{@code DamageKind.TRUE} 口径）。 */
    private static final double SOUL_DAMAGE = 5d;

    /** 每次命中的特殊值伤害。 */
    private static final int SANTE_DAMAGE = 15;

    /** 浮空高度（格）。 */
    private static final double FLOAT_HEIGHT = 10d;

    /** 浮空持续时间（60 刻 = 3 秒）。 */
    private static final int FLOAT_TICKS = 60;

    /** 浮空结束后的缓降保护时长（100 刻 = 5 秒）。 */
    private static final int FALL_PROTECTION_TICKS = 100;

    /** 进化[5]：对每个浮空目标的吸血节拍（5 刻 = 0.25 秒）。 */
    private static final int DRAIN_INTERVAL_TICKS = 5;

    /** 进化[5]：圈内敌人反哺的节拍（10 刻 = 0.5 秒）。 */
    private static final int RING_FEEDBACK_INTERVAL_TICKS = 10;

    /** 进化[5]：每一滴吸血给冷识回的生命。 */
    private static final double DRAIN_HEAL = 1d;

    /** 进化[5]：每一滴吸血给冷识回的特殊值。 */
    private static final int DRAIN_SANTE = 1;

    /**
     * 共鸣圈半径（格）—— 进化[5] 第二条"圈内敌人反哺"的判定半径。
     * <p>这个值不在契约 §3.1 的冻结面里（共鸣组件自己持有它），而"圈半径 = 3 格"是 A 道实现的既有口径；
     * 这里按同一数值对齐，否则玩家会看到"目标明明站在圈里却不回血"。
     * 若日后契约补一个 {@code ringRadius()} 读口，这一行改成读组件即可。
     */
    private static final double RING_RADIUS = 3.0d;

    // ───────── 状态 ─────────

    /** 技能当前处于哪一段（同一时刻只有一个状态；用枚举避免多个布尔拼出非法组合）。 */
    private enum Phase {
        /** 空闲（常态）。 */
        IDLE,
        /** 1.25 秒引导：缓慢 III、其它技能禁用、共鸣圈伤害暂停。 */
        CHANNEL,
        /** 立方体下落动画（0.25 秒）。 */
        FALL,
        /** 生效窗口：浮空 3 秒（进化[5] 的吸血也挂在这里）。 */
        EFFECT
    }

    /** 一条浮空记录：受害者 + 起始世界 + 起始 Y（逐刻抬升的闭式基准）。 */
    private record Levitated(Player victim, World world, double baseY) {
    }

    private Phase phase = Phase.IDLE;

    /** 当前这一段已进行的刻数（引导 / 下落 / 浮空各自从头计）。 */
    private int phaseTicks;

    /**
     * 立方体坠落动画的刻数轴（0 = 引导开始；{@value #CUBE_ANIMATION_TICKS} = 落地）。
     * <p>唯一消费者 = {@code LetheVfx.reshapeCubeFall(world, at, tick)}；见 {@link #CUBE_ANIMATION_TICKS}。
     */
    private int cubeAnimationTick;

    /**
     * 落点（= 引导结束时的玩家位置；立方体就落在这里、主爆点也在这里）。
     * <p>存克隆体：{@link Location} 可变，存引用会被后续原地修改连带改掉。
     */
    private Location impactCenter;

    /** 正在浮空的受害者（顺序 = 命中顺序；浮空结束统一收尾）。 */
    private final List<Levitated> levitated = new ArrayList<>();

    /**
     * 引导期缓慢是否由**本组件自己**记过账。
     * <p>为什么需要这个标记：{@link BuffComponent#removePotionEffect(PotionEffectType)} 会无条件摘掉
     * 该类型药水（不只是本组件施加的那一次）。若在"没引导"的清理路径上也盲摘 SLOWNESS，
     * 就会把**别人打给冷识的减速**一起解掉。只有确实自己施加过才摘。
     */
    private boolean channelSlownessApplied;

    // ───────── 协作组件（只在 start() 取；stop() 置空）─────────

    private LetheResonanceComponent resonance;
    private BuffComponent buff;
    private VitalsComponent vitals;
    private SanTEComponent sante;
    private FactionComponent faction;
    private LetheEvolutionPassive evolution;
    private HotbarRenderComponent render;
    private LetheFloatingTextComponent voice;

    public LetheReshapeSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot(3)} 指定）。 */
    public static final class Specification extends Skill.Specification<LetheReshapeSkill> {

        public Specification() {
            super(Component.text("重塑"),
                    List.of(
                            Component.text("引导1.25秒（缓慢III、其它技能禁用、共鸣圈被动伤害暂停）"),
                            Component.text("巨大的蓝色立方体在冷识位置落下爆开：r=" + (int) MAIN_RADIUS
                                    + " 敌人受" + (int) SOUL_DAMAGE + "灵魂(TRUE)+" + SANTE_DAMAGE + "特殊值"),
                            Component.text("每个标记箭位置再爆一次：r=" + (int) MARK_RADIUS + "，同样伤害"),
                            Component.text("受击者立刻向上浮空" + (int) FLOAT_HEIGHT + "格，持续"
                                    + (FLOAT_TICKS / 20) + "秒"),
                            Component.text("进化[5]「新生」：浮空目标每0.25秒为冷识回1生命+1特殊值"),
                            Component.text("CD " + BASE_COOLDOWN_SECONDS + "s（进化[4]「救赎」后减半）")
                    ),
                    BASE_COOLDOWN_TICKS,
                    0,
                    Material.BLUE_ICE);
            requires(LetheResonanceComponent.class)
                    .requires(BuffComponent.class)
                    .requires(VitalsComponent.class)
                    .requires(SanTEComponent.class)
                    .requires(FactionComponent.class)
                    .requires(LetheEvolutionPassive.class)
                    .requires(HotbarRenderComponent.class)
                    .requires(LetheFloatingTextComponent.class);
        }

        @Override
        public LetheReshapeSkill create(String id, ComponentServicesPort services) {
            return new LetheReshapeSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        resonance = svc().components().get(LetheResonanceComponent.class);
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        faction = svc().components().get(FactionComponent.class);
        evolution = getComponent(LetheEvolutionPassive.class);
        render = svc().components().get(HotbarRenderComponent.class);
        voice = svc().components().get(LetheFloatingTextComponent.class);
    }

    /**
     * 停止生效：把引导痕迹与浮空名单收干净，**不启动冷却**（实例已销毁）。
     * <p>必须恢复 {@code suspendRingDamage(false)}：组件死了而共鸣圈仍被挂上"暂停伤害"，
     * 会让整个角色的核心被动永久失效 —— 这是本类最危险的一条清理。
     */
    @Override
    public void stop() {
        releaseChannelResources();
        levitated.clear();
        phase = Phase.IDLE;
        phaseTicks = 0;
        impactCenter = null;
        resonance = null;
        buff = null;
        vitals = null;
        sante = null;
        faction = null;
        evolution = null;
        render = null;
        voice = null;
    }

    // ───────── 施放（唯一入口：右键）─────────

    /**
     * 右键：开始引导。
     * <p>闸门（冷却 / 沉默眩晕）必须自判：listener 只查冷却，不查 {@code canUse()}。
     * <p>引导 / 坠落 / 浮空期间重复施放一律忽略（不重置引导、不叠第二次立方体）。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        if (isCoolingDown()) {
            return;
        }
        if (!canUse()) {
            return;
        }
        if (phase != Phase.IDLE) {
            return;
        }
        Player self = selfPlayer();
        if (self == null) {
            return;
        }
        beginChannel(self);
    }

    // ───────── 每刻：四段状态机 ─────────

    @Override
    public void update() {
        if (phase == Phase.IDLE) {
            return;   //常态零开销
        }
        Player self = selfPlayer();
        if (self == null || !self.isOnline() || self.isDead()) {
            abort();
            return;
        }
        switch (phase) {
            case CHANNEL -> tickChannel(self);
            case FALL -> tickFall();
            case EFFECT -> tickEffect(self);
            default -> {
            }
        }
    }

    /** 引导开始：缓慢 III + 暂停共鸣圈被动伤害 + 其它技能禁用（{@link #isChanneling()} 由 phase 推出）。 */
    private void beginChannel(Player self) {
        phase = Phase.CHANNEL;
        phaseTicks = 0;
        cubeAnimationTick = 0;
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SLOWNESS,
                    CHANNEL_TICKS + SLOWNESS_SAFETY_TICKS, CHANNEL_SLOWNESS_AMPLIFIER);
            channelSlownessApplied = true;   //只有确实走账本施加过，收尾时才允许摘（见字段注释）
        }
        if (resonance != null) {
            resonance.suspendRingDamage(true);
        }
        LetheSound.reshapeChannel(self.getWorld(), self.getLocation());
        say(self, LetheFloatingTextComponent.MOMENT_RESHAPE_CHANNEL);
        repaint();
    }

    /** 引导每刻：闸门关闭则打断；每刻推立方体动画（它跟着冷识的当前位置成型）；到点进入下落。 */
    private void tickChannel(Player self) {
        //立方体在头顶缓缓下降、缓慢自转 —— 这一段的画面由同一个 reshapeCubeFall 的 tick 轴表达
        //  （D 道把"引导期悬停旋转"与"落体"合成了一个函数，见 CUBE_ANIMATION_TICKS）
        LetheVfx.reshapeCubeFall(self.getWorld(), self.getLocation(), phaseTicks);
        //大招引导期：紫色粒子绕角色盘旋（需求 2026-10-30）
        LetheVfx.reshapeAura(self.getWorld(), self.getLocation(), phaseTicks);
        phaseTicks++;
        if (!canUse()) {
            interruptChannel();
            return;
        }
        if (phaseTicks >= CHANNEL_TICKS) {
            beginFall(self);
        }
    }

    /** 引导结束：定下落点、恢复共鸣圈伤害、摘缓慢，立方体开始快速砸下。 */
    private void beginFall(Player self) {
        phase = Phase.FALL;
        phaseTicks = 0;
        cubeAnimationTick = CHANNEL_TICKS;   //引导已经走完动画的前 25 刻
        impactCenter = self.getLocation().clone();
        releaseChannelResources();
        repaint();
    }

    /** 下落每刻：把立方体动画快进到落点（第 5 帧正好 tick 40 = 落地）；到点爆开。 */
    private void tickFall() {
        if (impactCenter == null || impactCenter.getWorld() == null) {
            abort();
            return;
        }
        cubeAnimationTick = Math.min(CUBE_ANIMATION_TICKS, cubeAnimationTick + CUBE_FALL_STEP);
        LetheVfx.reshapeCubeFall(impactCenter.getWorld(), impactCenter, cubeAnimationTick);
        phaseTicks++;
        if (phaseTicks >= FALL_TICKS) {
            impact();
        }
    }

    /**
     * 落点爆开：两处范围结算 → 浮空名单 → 起冷却 → 进入生效窗口。
     * <p>顺序不可交换：先结算伤害再登记浮空（被这一下打死的目标不该被抬起来）。
     */
    private void impact() {
        Player self = selfPlayer();
        if (self == null || impactCenter == null || impactCenter.getWorld() == null) {
            abort();
            return;
        }
        World world = impactCenter.getWorld();
        LetheVfx.reshapeImpact(world, impactCenter);
        LetheSound.reshapeImpact(world, impactCenter);
        say(self, LetheFloatingTextComponent.MOMENT_RESHAPE_IMPACT);

        //① 主爆点 r=15；② 每个标记箭位置 r=10（去重：同一个敌人只结算一次）
        LinkedHashSet<Player> hit = new LinkedHashSet<>(hostilesNear(impactCenter, MAIN_RADIUS));
        for (Location point : markedPoints()) {
            LetheVfx.reshapeMarkExplosion(point.getWorld(), point);
            hit.addAll(hostilesNear(point, MARK_RADIUS));
        }

        //③ 结算：5 灵魂(TRUE) + 15 特殊值；碎片表现按命中序取 index
        int shardIndex = 0;
        for (Player victim : hit) {
            if (victim.isDead() || !victim.isOnline()) {
                continue;
            }
            if (vitals != null) {
                vitals.trueDamage(victim, self, SOUL_DAMAGE);
            }
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(), SANTE_DAMAGE);
            }
            LetheVfx.reshapeShard(victim.getWorld(), victim.getLocation(), shardIndex++);
            if (victim.isDead() || !victim.isOnline()) {
                continue;   //被这一下打死 ⇒ 没有可浮空的活人
            }
            levitated.add(new Levitated(victim, victim.getWorld(), victim.getLocation().getY()));
            LetheVfx.reshapeFloat(victim.getWorld(), victim.getLocation());
        }

        //④ 冷却：落点真的爆开了才算成功（引导被打断 / 玩家不可用都不会到这里）
        startCooldown(cooldownTicks());
        phase = Phase.EFFECT;
        phaseTicks = 0;
        repaint();
    }

    /** 生效窗口每刻：确定性抬升 + 进化[5] 吸血；满 3 秒收尾。 */
    private void tickEffect(Player self) {
        phaseTicks++;
        //① 确定性抬升：第 n 刻 Y = 起始 Y + 10 × min(1, n/60) ⇒ 第 60 刻恰好 +10 格（见类注释）
        double progress = Math.min(1d, phaseTicks / (double) FLOAT_TICKS);
        for (int i = levitated.size() - 1; i >= 0; i--) {
            Levitated entry = levitated.get(i);
            Player victim = entry.victim();
            if (victim == null || !victim.isOnline() || victim.isDead()
                    || victim.getWorld() == null || !victim.getWorld().equals(entry.world())) {
                levitated.remove(i);   //死亡 / 掉线 / 换世界：这条浮空失效，移出名单
                continue;
            }
            Location current = victim.getLocation();
            double targetY = entry.baseY() + FLOAT_HEIGHT * progress;
            victim.teleport(new Location(current.getWorld(), current.getX(), targetY, current.getZ(),
                    current.getYaw(), current.getPitch()));
            victim.setFallDistance(0f);   //每刻清零：抬升途中不攒摔落伤害
        }

        //② 进化[5]「新生」：生效期间持续吸血（两条见类注释）
        if (evolution != null && evolution.hasNewLifeDrain()) {
            boolean selfAlive = !self.isDead() && self.isOnline();
            if (selfAlive && phaseTicks % DRAIN_INTERVAL_TICKS == 0) {
                drainLevitated();
            }
            if (selfAlive && phaseTicks % RING_FEEDBACK_INTERVAL_TICKS == 0) {
                drainRingVictims();
            }
        }

        if (phaseTicks >= FLOAT_TICKS) {
            finishEffect();
        }
    }

    /** 收尾：浮空结束表现 + 缓降保护 + 清名单回空闲。 */
    private void finishEffect() {
        for (Levitated entry : levitated) {
            Player victim = entry.victim();
            if (victim == null || !victim.isOnline() || victim.isDead() || victim.getWorld() == null) {
                continue;
            }
            LetheSound.reshapeFloat(victim.getWorld(), victim.getLocation());
            //浮空结束的缓降保护：走 buff 组件的跨玩家账本入口 ⇒ 目标角色清除时会被他自己的账本回收；
            //目标没有角色实例时该入口不施加任何东西（本工程"不写孤儿效果"的统一口径）
            if (buff != null) {
                buff.applyPotionEffectTo(victim, PotionEffectType.SLOW_FALLING, FALL_PROTECTION_TICKS, 0);
            }
        }
        levitated.clear();
        phase = Phase.IDLE;
        phaseTicks = 0;
        impactCenter = null;
        repaint();
    }

    /** 进化[5] 第一条：对每个仍在浮空的命中目标回 1 生命 + 1 特殊值。 */
    private void drainLevitated() {
        for (Levitated entry : levitated) {
            Player victim = entry.victim();
            if (victim == null || !victim.isOnline() || victim.isDead()) {
                continue;
            }
            if (vitals != null) {
                vitals.heal(DRAIN_HEAL);
            }
            if (sante != null) {
                sante.increase(DRAIN_SANTE);
            }
            LetheVfx.damageSoul(victim.getWorld(), victim.getLocation());
        }
    }

    /**
     * 进化[5] 第二条：共鸣圈中被本技能命中的敌人每 0.5 秒反哺 1 生命 + 1 特殊值。
     * <p>实现选择与契约偏差见类注释「进化[5]」第 2 点：圈心 = 标记箭（含已停点）的位置，
     * 判定半径 = 共鸣圈的真实半径（{@value #RING_RADIUS} 格），本技能在生效窗口内轮询，不新增事件通道。
     */
    private void drainRingVictims() {
        List<Location> points = markedPoints();
        if (points.isEmpty()) {
            return;
        }
        for (Levitated entry : levitated) {
            Player victim = entry.victim();
            if (victim == null || !victim.isOnline() || victim.isDead()) {
                continue;
            }
            if (!withinAnyPoint(victim.getLocation(), points, RING_RADIUS)) {
                continue;
            }
            if (vitals != null) {
                vitals.heal(DRAIN_HEAL);
            }
            if (sante != null) {
                sante.increase(DRAIN_SANTE);
            }
            LetheVfx.damageSante(victim.getWorld(), victim.getLocation());
        }
    }

    /**
     * 引导被打断（眩晕 / 沉默 / 玩家不可用）：恢复共鸣圈伤害、摘缓慢、回空闲，**不启动冷却**。
     * <p>引导没走完就没有"落点爆开"这个结果 —— 不进冷却是"冷却只在成功路径"的直接推论。
     */
    private void interruptChannel() {
        releaseChannelResources();
        phase = Phase.IDLE;
        phaseTicks = 0;
        impactCenter = null;
        repaint();
    }

    /** 非引导段的异常中止（玩家死亡 / 掉线 / 世界丢失）：清理但不动冷却。 */
    private void abort() {
        releaseChannelResources();
        levitated.clear();
        phase = Phase.IDLE;
        phaseTicks = 0;
        impactCenter = null;
    }

    /**
     * 释放引导期的两个副作用（幂等）：摘掉缓慢、恢复共鸣圈伤害。
     * <p>这两项必须在**每一条**离开引导的路径上被调到（正常结束 / 打断 / stop），
     * 否则要么玩家带着缓慢跑，要么共鸣被动被永久静音。
     */
    private void releaseChannelResources() {
        if (channelSlownessApplied) {
            channelSlownessApplied = false;
            if (buff != null) {
                buff.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
        if (resonance != null) {
            resonance.suspendRingDamage(false);
        }
    }

    // ───────── 最低冷却：按进化[4]「救赎」定档 ─────────

    /** 本轮冷却刻数：进化[4] 起减半（在爆开那一刻定档，整轮不变）。 */
    private int cooldownTicks() {
        if (evolution != null && evolution.hasReducedReshapeCooldown()) {
            return BASE_COOLDOWN_TICKS / 2;
        }
        return BASE_COOLDOWN_TICKS;
    }

    // ───────── 索敌 / 点位 ─────────

    /**
     * 半径内的敌对玩家（立方体按真实直线距离过滤 —— {@code getNearbyEntities} 给的是立方体范围）。
     * <p>判敌口径 = {@link FactionComponent#isHostileTo(java.util.UUID)}（自己与同阵营排除）。
     */
    private List<Player> hostilesNear(Location center, double radius) {
        List<Player> result = new ArrayList<>();
        World world = center == null ? null : center.getWorld();
        if (world == null || faction == null) {
            return result;
        }
        Player self = selfPlayer();
        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player candidate)) {
                continue;
            }
            if (candidate.isDead() || !candidate.isOnline() || candidate.equals(self)) {
                continue;
            }
            if (!world.equals(candidate.getWorld())) {
                continue;
            }
            if (candidate.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            if (!faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    /** 带标记的箭位置（含已停的停点 = 共鸣圈心）；世界已失效的点被过滤掉。 */
    private List<Location> markedPoints() {
        if (resonance == null) {
            return List.of();
        }
        List<Location> points = resonance.markedArrowPoints();
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        return points.stream()
                .filter(point -> point != null && point.getWorld() != null)
                .toList();
    }

    /** 某点是否在 {@code points} 中任一点的 {@code radius} 内（同世界）。 */
    private boolean withinAnyPoint(Location at, List<Location> points, double radius) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        double radiusSquared = radius * radius;
        for (Location point : points) {
            if (!at.getWorld().equals(point.getWorld())) {
                continue;
            }
            if (at.distanceSquared(point) <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕 / 沉默时不许施放（listener 不查它，必须这里判）。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    /** 本组件不参与能量维度（声明耗能 0），故回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    // ───────── 私有工具 ─────────

    /** 请求热键栏重绘（拿不到渲染组件就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    /** 喊一句语音（台词走语音组件的词库）。 */
    private void say(Player self, String moment) {
        if (voice != null && self != null) {
            voice.say(self, moment, null);
        }
    }

    // ───────── 对外读口（契约冻结：遗忘 / 召圣据此禁用自己）─────────

    /**
     * 是否正在 1.25 秒引导中。
     * <p>消费方 = {@link LetheForgetSkill} / {@link LetheSummonSkill}（它们在 {@code onCast} 开头
     * 查本读数，为 {@code true} 时直接返回）。只读、无副作用。
     */
    public boolean isChanneling() {
        return phase == Phase.CHANNEL;
    }

    /** 是否在浮空生效窗口内（诊断用；不参与行为决策）。 */
    public boolean isEffectActive() {
        return phase == Phase.EFFECT;
    }

    /** 当前浮空名单人数（诊断用；不参与行为决策）。 */
    public int levitatedCount() {
        return levitated.size();
    }
}
