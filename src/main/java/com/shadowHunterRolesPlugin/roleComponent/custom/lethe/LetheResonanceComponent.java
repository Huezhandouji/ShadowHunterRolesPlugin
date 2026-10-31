package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 冷识「共鸣」组件：**箭矢标记 / 共鸣圈 / 召圣（抬升 · 召回 · 引爆）/ 重塑静默的唯一状态持有者**。
 *
 * <h2>为什么这些状态必须集中在一个组件里</h2>
 * 主武器（{@code LetheMainWeapon}）是"标记"的产生点，三个主动技能与进化被动是"标记的消费者"：
 * 抬升、召回、引爆要读同一份标记表，重塑引导要暂停同一批圈的伤害。若让每个技能各自记一份
 * "我标记过的箭"，召回时就出现两本账（主武器记的箭别的技能看不见），上面任何一条规则都会在
 * "谁的表"上分叉。因此本组件是这些状态的**唯一持有处**：别人只调用本类的公开方法，不碰内部表。
 *
 * <h2>一条箭的生命周期</h2>
 * <ol>
 *   <li><b>离弦</b>：主武器调 {@link #markArrow(Projectile, boolean)}，这支箭被记进标记表；</li>
 *   <li><b>命中敌对玩家</b>：主武器调 {@link #onMarkedArrowHit(Projectile, Player)} ——
 *       扣对方 10 点特殊值并造成 4 点真实伤害，标记到此**兑现并被消耗**（不再留下共鸣圈）；</li>
 *   <li><b>落地 / 失效</b>：{@link #onMarkedArrowStopped(Projectile)}（主武器在命中时调）或
 *       {@link #update()} 自己发现"箭没了 / 插在方块上"时，在这支箭的**最后位置**生成共鸣圈，
 *       同时把箭矢实体移除（不让它插在地上被拾取）。两条入口走同一段逻辑，按 UUID 幂等；</li>
 *   <li><b>共鸣圈</b>：半径 {@value #RING_RADIUS} 格，每 {@value #DEFAULT_RING_INTERVAL_TICKS} 刻
 *       （0.25 秒）对圈内敌对玩家结算 1 点真实伤害 + 2 点特殊值伤害；</li>
 *   <li><b>召圣 · 抬升</b>：{@link #liftMarkedArrows()} 把全部圈原地抬 {@value #LIFT_HEIGHT} 格（目标位置必须站得下人），
 *       抬起的圈改为 1 秒一次、且**不再过期**（它们是"召圣"阶段的持续威胁）；</li>
 *   <li><b>召圣 · 召回</b>：{@link #recallLiftedArrows()} 把抬起的圈化成"箭"飞回施法者，
 *       途中经过的敌人被每支箭独立结算 5 真实 + 10 特殊值；</li>
 *   <li><b>召圣 · 引爆</b>：{@link #detonateLiftedArrows()} 让仍悬停的圈自爆，半径 10 内敌人
 *       吃 10 点特殊值伤害 + 2 点灵魂伤害（真伤口径）。</li>
 * </ol>
 *
 * <h2>口径申报（逐条都是"为什么这么判"，不是实现细节）</h2>
 * <ol>
 *   <li><b>"一次触发"= 一次脉冲真的打伤了至少一个敌人</b>：需求原文是"每次伤害算一次触发"。
 *       本实现按**脉冲**计数而不是按"被打中的头像数"计数 —— 同一个脉冲同时命中三个人，也只是
 *       圈的一次呼吸，不该让圈三倍速消散。反过来，圈里没人时脉冲**不消耗**次数：
 *       否则一个用来守点的圈会在无人经过时自己"计时消失"，玩家就失去了"先布场再引敌"的玩法。
 *       于是非抬升的圈只会在"伤害真的兑现过 {@code triggerCap} 次"之后消散。</li>
 *   <li><b>抬升的"能站人"检查</b>：只对**目标点**（原位置 + {@value #LIFT_HEIGHT} 格）判脚格与头格是否可通过；
 *       不通过就保持原高度（并计入未抬升数）。为什么不判原位置：圈落在"箭插进去的方块表面"，
 *       原位置本来就可能不可站立（例如箭钉在天花板下方），该检查会把它永远判死。</li>
 *   <li><b>召回的是"逻辑箭"，不是 Bukkit 箭矢实体</b>：每刻沿施法者方向前进 2 格（40 格/秒）、
 *       由 {@code LetheVfx.summonRecallTrail} 画出轨迹。不生成实体的理由：原版箭矢命中会自己
 *       结算一次伤害、并再触发一次 {@code ProjectileHitEvent} —— 那会让"每支箭 5 真实 + 10 特殊值"
 *       之外再多出一份原版伤害，且伤害归属会绕开本组件的来源口径。逻辑箭让每一次伤害都只在
 *       本类的结算点出现一次（来源 = 施法者）。</li>
 *   <li><b>召回的去重上限</b>：每支飞行箭对同一个敌人**至多结算一次**（逐箭的命中名册）；
 *       同一个敌人在**一次召回**里至多被 {@value #RECALL_HIT_ARROW_CAP} 支箭打中
 *       （全局计数表）。两者叠加 ⇒ 单次召回对单个敌人最多 3 份伤害，不会被"圈多"糊成一张秒杀网。</li>
 *   <li><b>{@link #suspendRingDamage(boolean)} 只暂停"伤害与计数"</b>，脉冲粒子照常：
 *       重塑引导期间玩家仍看得见圈在原地呼吸，只是这一刻它不咬人。这样暂停状态本身就是可读的
 *       （圈没有消失、也没有假装在结算）。</li>
 *   <li><b>脉冲用绝对刻排期</b>（{@link Bukkit#getCurrentTick()}），不做"每 tick 自增计数"：
 *       服务器卡顿/跳刻之后不会出现"欠了三次脉冲、下一 tick 连打三下"的补发。</li>
 * </ol>
 *
 * <h2>依赖（四项都只在 {@link #start()} 取，{@link #stop()} 成对置空）</h2>
 * <ul>
 *   <li>{@link VitalsComponent}：真实伤害 / 灵魂伤害的唯一出口；</li>
 *   <li>{@link SanTEComponent}：特殊值伤害（跨实例按 UUID 扣）；</li>
 *   <li>{@link FactionComponent}：圈内 / 弹道上"是不是敌人"的唯一判定；</li>
 *   <li>{@link BuffComponent}：本组件当前**不读它** —— 依赖清单里保留它，是为了让"共鸣组件要装在
 *       一套完整的角色组件之上"这件事在装配期成立（缺它则本角色不注册），而不是运行期才发现
 *       某个消费者取不到闸门。共鸣的伤害属于"环境结算"，不归自身施法闸门管。</li>
 * </ul>
 *
 * <p>本组件不占热键栏（描述符直接继承 {@code RoleComponent.Specification}，没有栏位口径）。
 */
public class LetheResonanceComponent extends RoleComponent {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "lethe_resonance_component";

    // ───────── 数值（唯一修改点）─────────

    /** 共鸣圈半径（格）。 */
    private static final double RING_RADIUS = 3.0d;

    /** 共鸣圈默认触发上限（次）：打满这么多次伤害脉冲后消散。 */
    private static final int DEFAULT_RING_TRIGGER_CAP = 5;

    /** 共鸣圈默认脉冲间隔（刻）= 0.25 秒。 */
    private static final int DEFAULT_RING_INTERVAL_TICKS = 5;

    /**
     * 共鸣圈的最长存活（刻）= 20 秒，只对**未被召圣抬起**的圈生效。
     * <p>为什么需要它：触发次数只在"圈里有敌人"的脉冲上才消耗（见类注释口径申报一），
     * 因此空场落下的圈永远不会自己走完 5 次 ⇒ 长时间拉扯会越积越多（每 0.25 秒/圈一次粒子脉冲）。
     * 加一条最长存活期把它收掉：既不改"触发 5 次消散"的正向规则，也保证空场不会无限堆积。
     */
    private static final int RING_LIFETIME_TICKS = 400;

    /** 同时存在的标记上限（飞行中的箭 + 已经成圈的停点）：超了就回收最老的一条，防极端刷箭。 */
    private static final int MAX_MARKED_ARROWS = 16;

    /** 钟声的待机节奏（刻）= 2.5 秒：圈里没打中人时，主教的心跳就是这个间隔。 */
    private static final int BELL_IDLE_INTERVAL_TICKS = 50;

    /** 钟声的激斗节奏（刻）= 1 秒：圈正在打中人时加快（2026-10-30 需求）。 */
    private static final int BELL_HOT_INTERVAL_TICKS = 20;

    /** "正在打中人"的记忆窗口（刻）= 1 秒：最近 1 秒内有过命中 ⇒ 钟声按激斗节奏。 */
    private static final int BELL_HOT_WINDOW_TICKS = 20;

    /** 圈生成后的"全特效"时长（刻）= 1 秒：之后没命中就退化成简化画法（2026-10-30 需求）。 */
    private static final int RING_RICH_HEAD_TICKS = 20;


    /** 抬起后的共鸣圈脉冲间隔（刻）= 1 秒：召圣阶段是"慢而久"的威胁。 */
    private static final int LIFTED_RING_INTERVAL_TICKS = 20;

    /** 抬升高度（格）。 */
    private static final int LIFT_HEIGHT = 1;

    /** 圈脉冲对每个敌人造成的真实伤害。 */
    private static final double RING_TRUE_DAMAGE = 1.0d;

    /** 圈脉冲对每个敌人造成的特殊值伤害。 */
    private static final int RING_SANTE_DAMAGE = 2;

    /** 抬起的圈不再过期；视觉上按"满读数"呈现（{@code LetheVfx.ringPulse} 的读数上限是 8）。 */
    private static final int LIFTED_RING_TRIGGER_READING = 8;

    /** 「流连」标记命中时扣除的特殊值。 */
    private static final int MARK_HIT_SANTE_DAMAGE = 10;

    /** 「流连」标记命中时造成的真实伤害。 */
    private static final double MARK_HIT_TRUE_DAMAGE = 4.0d;

    /** 飞行箭拖尾的刷新间隔（刻）：每 2 刻一段，既连成线也不至于每 tick 铺满粒子。 */
    private static final int TRAIL_INTERVAL_TICKS = 2;

    /** 召回箭每刻前进的距离（格）= 2 格/tick = 40 格/秒。 */
    private static final double RECALL_STEP = 2.0d;

    /** 召回箭"经过敌人"的判定半径（点到线段的距离，格）。 */
    private static final double RECALL_HIT_RADIUS = 1.9d;

    /** 召回箭命中伤害：真实伤害。 */
    private static final double RECALL_TRUE_DAMAGE = 5.0d;

    /** 召回箭命中伤害：特殊值伤害。 */
    private static final int RECALL_SANTE_DAMAGE = 10;

    /** 同一敌人一次召回里至多被几支飞行箭命中。 */
    private static final int RECALL_HIT_ARROW_CAP = 3;

    /** 引爆半径（格）。 */
    private static final double DETONATE_RADIUS = 10.0d;

    /** 引爆对每个敌人造成的特殊值伤害。 */
    private static final int DETONATE_SANTE_DAMAGE = 10;

    /** 引爆对每个敌人造成的灵魂伤害（真伤口径）。 */
    private static final double DETONATE_SOUL_DAMAGE = 2.0d;

    /** 浮点比较容差（"走到了施法者身上"的判定）。 */
    private static final double ARRIVE_EPSILON = 1.0E-6d;

    // ───────── 协作组件（start() 里一次取好，stop() 成对置空）─────────

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private FactionComponent faction;

    /**
     * 装配依赖清单里的一项（见类注释）；本组件当前的结算路径**不读它**。
     * <p>保留字段而不是"声明了却不取"：依赖声明与实取清单必须是同一个集合，
     * 装配期检查与运行期行为才不会分叉。
     */
    private BuffComponent buff;

    // ───────── 运行期状态 ─────────

    /**
     * 标记表：键 = 箭矢实体 UUID，值 = 这支箭的完整状态（飞行中 / 已停成圈 / 被抬升）。
     * <p>顺序 = 标记先后（{@link LinkedHashMap}），因此 {@link #markedArrowPoints()} 的返回序可复现。
     * <p>为什么箭停下后仍留在这张表里：共鸣圈需要同一份状态承载"停点 / 已触发次数 / 是否被抬起"，
     * 另开一张"圈表"就会让召回 / 引爆要在两张表之间对账（两本账）。
     */
    private final Map<UUID, MarkedArrow> markedArrows = new LinkedHashMap<>();

    /** 正在飞回施法者的"逻辑箭"（召回阶段的临时状态；到点即从表中消失）。 */
    private final List<RecallArrow> recallArrows = new ArrayList<>();

    /** 本次召回里每个敌人已被几支飞行箭命中（去重上限见 {@link #RECALL_HIT_ARROW_CAP}）。 */
    private final Map<UUID, Integer> recallHitCounts = new HashMap<>();

    /** 圈触发上限（次）。进化「共鸣」档会把它改成 8。 */
    private int ringTriggerCap = DEFAULT_RING_TRIGGER_CAP;

    /** 圈脉冲间隔（刻）。默认 5；外部可改（例如技能侧想让圈更慢）。 */
    private int ringIntervalTicks = DEFAULT_RING_INTERVAL_TICKS;

    /** 圈伤害是否被暂停（重塑引导期间为 true）。 */
    private boolean ringDamageSuspended;

    /** 当前是否有召回箭在飞。 */
    private boolean recalling;

    /** 下一次钟声的绝对刻（0 = 目前没有圈、未启动节拍器）。 */
    private int nextBellTick;

    /** 最近一次"圈打中人"的绝对刻（决定钟声按 2.5 秒还是 1 秒）。 */
    private int lastAnyRingHitTick = Integer.MIN_VALUE / 2;


    public LetheResonanceComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services);
    }

    /**
     * 本组件的描述符：不带栏位（不占热键栏），依赖 = 实取清单的四个组件。
     */
    public static final class Specification extends RoleComponent.Specification<LetheResonanceComponent> {

        public Specification() {
            super("Resonance");
            requires(VitalsComponent.class)
                    .requires(SanTEComponent.class)
                    .requires(FactionComponent.class)
                    .requires(BuffComponent.class);
        }

        @Override
        public LetheResonanceComponent create(String id, ComponentServicesPort services) {
            return new LetheResonanceComponent(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        faction = svc().components().get(FactionComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * 停止生效：清空全部状态并回到默认配置。
     * <p><b>不主动移除仍在飞的箭</b>：那些是原版实体（玩家已经付出一支箭、也看到了它飞出去），
     * 角色被清除时"凭空消失"是玩家可见的怪事。本组件只是不再观察它们（与主武器侧同一口径）。
     * 已经生成的共鸣圈则必须随角色一起消失（它们是本插件的效果，没有理由留在场上）。
     */
    @Override
    public void stop() {
        clearAll();
        ringTriggerCap = DEFAULT_RING_TRIGGER_CAP;
        ringIntervalTicks = DEFAULT_RING_INTERVAL_TICKS;
        vitals = null;
        sante = null;
        faction = null;
        buff = null;
    }

    // ═════════════════════════ 主武器调用面 ═════════════════════════

    /**
     * 给一支刚离弦的箭打上「流连」标记。
     *
     * @param arrow     射出的箭矢实体（同一个实例在命中时会被交回来）
     * @param bloodTear 这支箭是否由「血泪」射出（当前只作为标记状态保留：血泪与普通箭走同一套
     *                  标记 / 圈规则；把来源记下来，是为了以后按来源分叉时不必再改一遍表结构）
     */
    public void markArrow(Projectile arrow, boolean bloodTear) {
        if (arrow == null) {
            return;
        }
        markedArrows.put(arrow.getUniqueId(), new MarkedArrow(arrow, bloodTear));
        trimMarkedArrows();
    }

    /**
     * 标记表上限保护：超过 {@link #MAX_MARKED_ARROWS} 时优先回收"已经成圈"的最老一条
     * （它的圈已经兑现过随机性，收掉最不明显），全是飞行箭时回收最老的一条记录。
     * <p>只在本角色自己的表内做，不碰平台实体（实体由主武器 / 命中路径负责）。
     */
    private void trimMarkedArrows() {
        if (markedArrows.size() <= MAX_MARKED_ARROWS) {
            return;
        }
        Iterator<Map.Entry<UUID, MarkedArrow>> iterator = markedArrows.entrySet().iterator();
        Map.Entry<UUID, MarkedArrow> fallback = null;
        while (iterator.hasNext()) {
            Map.Entry<UUID, MarkedArrow> entry = iterator.next();
            if (fallback == null) {
                fallback = entry;
            }
            if (entry.getValue().stopped) {
                expireRing(entry.getValue());
                iterator.remove();
                return;
            }
        }
        if (fallback != null) {
            markedArrows.remove(fallback.getKey());
        }
    }

    /**
     * 「流连」标记在敌对玩家身上兑现：扣 10 点特殊值 + 4 点真实伤害，并**消耗**这枚标记。
     * <p>消耗 = 从标记表移除，因此随后主武器调 {@link #onMarkedArrowStopped(Projectile)} 时
     * 找不到它（幂等 ⇒ 不会在目标脚下再长出一个圈）。命中兑现的视觉归主武器（那里才知道
     * 命中的是哪一发），本方法只做结算。
     *
     * @param arrow  命中的箭（未标记过也允许：只结算伤害，不凭空留下状态）
     * @param victim 被命中的敌对玩家（敌我判定由调用方在主武器侧完成）
     */
    public void onMarkedArrowHit(Projectile arrow, Player victim) {
        if (arrow == null || victim == null) {
            return;
        }
        markedArrows.remove(arrow.getUniqueId());
        if (!victim.isOnline() || victim.isDead()) {
            return;
        }
        Player owner = selfPlayer();
        if (owner == null) {
            return;
        }
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(), MARK_HIT_SANTE_DAMAGE);
        }
        if (vitals != null) {
            vitals.trueDamage(victim, owner, MARK_HIT_TRUE_DAMAGE);
        }
    }

    /**
     * 标记箭停下（命中方块 / 命中后不再飞 / 实体已失效）：在停点生成共鸣圈，并把箭矢实体移除。
     *
     * <p><b>幂等</b>：同一支箭被重复通知（主武器在命中时通知一次、{@link #update()} 也可能看到
     * 同一个"已停止"状态）只会有一个圈；找不到这条标记记录时什么都不做。
     *
     * @param arrow 停下的箭矢（{@code null} 静默返回）
     */
    public void onMarkedArrowStopped(Projectile arrow) {
        if (arrow == null) {
            return;
        }
        MarkedArrow mark = markedArrows.get(arrow.getUniqueId());
        if (mark == null) {
            return;
        }
        spawnRing(mark, arrow);
        if (mark.expired) {
            markedArrows.remove(arrow.getUniqueId());
        }
    }

    // ═════════════════════════ 状态查询面 ═════════════════════════

    /**
     * 当前仍在场的标记数量（飞行中的箭 + 已经成圈的停点；召回中的逻辑箭不再算"标记"）。
     * <p>技能侧用它回答"现在值不值得开召圣"。
     */
    public int markedArrowCount() {
        return markedArrows.size();
    }

    /** 当前存活的共鸣圈数量（含被召圣抬起的圈；召回中的逻辑箭不算圈）。 */
    public int activeRingCount() {
        int count = 0;
        for (MarkedArrow mark : markedArrows.values()) {
            if (mark.stopped) {
                count++;
            }
        }
        return count;
    }

    /**
     * 全部标记点的当前位置：飞行中的箭取"此刻的实体位置"，已停的取"停点（含被抬起后的新高度）"。
     * <p>返回的是克隆体（{@code Location} 可变，交出去之后调用方原地改它不会改到内部状态）。
     */
    public List<Location> markedArrowPoints() {
        List<Location> points = new ArrayList<>();
        for (MarkedArrow mark : markedArrows.values()) {
            Location point;
            if (mark.stopped) {
                point = mark.stopPoint;
            } else if (mark.projectile != null && mark.projectile.isValid()) {
                point = mark.projectile.getLocation();
            } else {
                point = mark.lastPosition;
            }
            if (point != null && point.getWorld() != null) {
                points.add(point.clone());
            }
        }
        return points;
    }

    // ═════════════════════════ 召圣：抬升 / 召回 / 引爆 ═════════════════════════

    /**
     * 召圣 · 抬升：全部共鸣圈**原地上升 {@value #LIFT_HEIGHT} 格**。
     *
     * <p>抬升条件 = 目标位置（原圈心 + {@value #LIFT_HEIGHT} 格）站得下人（脚格与头格都可通过）。抬不起来的圈保持原位。
     * 抬起的圈被标记为 {@code lifted}：此后它的脉冲间隔固定为 1 秒、且**不再因为触发上限而消散**
     * —— 它们要一直悬停到被召回（{@link #recallLiftedArrows()}）或引爆
     * （{@link #detonateLiftedArrows()}），这是"召圣"阶段的持续压制。
     *
     * @return 真正被抬起的圈数量（0 = 没有任何圈满足抬升条件）
     */
    public int liftMarkedArrows() {
        int liftedCount = 0;
        int now = Bukkit.getCurrentTick();
        for (MarkedArrow mark : markedArrows.values()) {
            if (!mark.stopped || mark.lifted || mark.stopPoint == null) {
                continue;
            }
            Location raised = mark.stopPoint.clone().add(0d, LIFT_HEIGHT, 0d);
            if (!canStandAt(raised)) {
                continue;
            }
            mark.stopPoint = raised;
            mark.lifted = true;
            mark.nextPulseTick = now + LIFTED_RING_INTERVAL_TICKS;
            World world = raised.getWorld();
            LetheVfx.summonLift(world, raised);
            LetheVfx.summonHover(world, raised);
            LetheSound.summonLift(world, raised);
            liftedCount++;
        }
        return liftedCount;
    }

    /**
     * 召圣 · 召回：所有**被抬起的圈**化成逻辑箭，以 40 格/秒（2 格/tick）飞向施法者。
     *
     * <p>途中经过的敌人被每支箭独立结算 5 点真实伤害 + 10 点特殊值伤害；
     * 同一支箭对同一敌人只结算一次，且同一敌人整次召回至多被
     * {@value #RECALL_HIT_ARROW_CAP} 支箭命中（见类注释口径申报四）。
     * 箭到达施法者处即消失；全部箭落地后 {@link #isRecalling()} 回到 {@code false}。
     *
     * <p>没有抬起的圈时是 no-op（不播召回音、不进入召回状态）。
     */
    public void recallLiftedArrows() {
        Player owner = selfPlayer();
        if (owner == null) {
            return;
        }
        List<UUID> liftedIds = new ArrayList<>();
        for (Map.Entry<UUID, MarkedArrow> entry : markedArrows.entrySet()) {
            if (entry.getValue().lifted) {
                liftedIds.add(entry.getKey());
            }
        }
        for (UUID id : liftedIds) {
            MarkedArrow mark = markedArrows.remove(id);
            if (mark == null || mark.stopPoint == null || mark.stopPoint.getWorld() == null) {
                continue;
            }
            recallArrows.add(new RecallArrow(mark.stopPoint.clone()));
        }
        if (recallArrows.isEmpty()) {
            return;
        }
        if (!recalling) {
            //只有"开始一趟新的召回"才清空去重计数：上一趟还在飞的箭不该因为又开一趟而被重置上限
            recallHitCounts.clear();
        }
        recalling = true;
        if (owner.getWorld() != null) {
            LetheSound.summonRecall(owner.getWorld(), owner.getLocation());
        }
    }

    /**
     * 召圣 · 引爆：所有**仍被抬起的圈**自爆，半径 {@value #DETONATE_RADIUS} 格内敌人吃
     * 10 点特殊值伤害 + 2 点灵魂伤害（真伤口径）。
     * <p>自爆的圈随即从场上消失。已经进入召回阶段的逻辑箭不受影响（它们已经不再是圈）。
     */
    public void detonateLiftedArrows() {
        List<UUID> liftedIds = new ArrayList<>();
        for (Map.Entry<UUID, MarkedArrow> entry : markedArrows.entrySet()) {
            if (entry.getValue().lifted) {
                liftedIds.add(entry.getKey());
            }
        }
        Player owner = selfPlayer();
        for (UUID id : liftedIds) {
            MarkedArrow mark = markedArrows.remove(id);
            if (mark == null || mark.stopPoint == null || mark.stopPoint.getWorld() == null) {
                continue;
            }
            Location center = mark.stopPoint;
            World world = center.getWorld();
            LetheVfx.summonDetonate(world, center);
            LetheSound.summonDetonate(world, center);
            if (owner == null) {
                continue;
            }
            for (Player victim : hostilePlayersNear(center, DETONATE_RADIUS)) {
                if (sante != null) {
                    sante.decreaseSanTE(victim.getUniqueId(), DETONATE_SANTE_DAMAGE);
                }
                if (vitals != null) {
                    vitals.trueDamage(victim, owner, DETONATE_SOUL_DAMAGE);
                }
                LetheVfx.damageSoul(world, victim.getLocation());
                LetheVfx.damageSante(world, victim.getLocation());
            }
        }
    }

    /** 当前是否有被召圣抬起、尚未召回 / 引爆的圈。 */
    public boolean hasLiftedArrows() {
        for (MarkedArrow mark : markedArrows.values()) {
            if (mark.lifted) {
                return true;
            }
        }
        return false;
    }

    /** 是否仍处于召回过程中（还有逻辑箭在飞）。 */
    public boolean isRecalling() {
        return recalling || !recallArrows.isEmpty();
    }

    // ═════════════════════════ 重塑：暂停圈伤害 ═════════════════════════

    /**
     * 暂停 / 恢复共鸣圈的伤害结算（引导期间由重塑使用）。
     * <p>暂停只停"伤害与触发计数"，脉冲粒子照常出现（见类注释口径申报五）。
     *
     * @param suspended {@code true} = 暂停结算；{@code false} = 恢复
     */
    public void suspendRingDamage(boolean suspended) {
        this.ringDamageSuspended = suspended;
    }

    // ═════════════════════════ 进化 / 配置 ═════════════════════════

    /**
     * 改圈触发上限（默认 {@value #DEFAULT_RING_TRIGGER_CAP}；进化「共鸣」档 = 8）。
     * <p>只影响**未抬起**的圈：抬起的圈已经"不再过期"。已在场上的圈也按新上限判定（读的是本字段）。
     *
     * @param cap 新的上限；{@code < 1} 归一为 1（上限为 1 意味着打一次就散，是合法配置）
     */
    public void setRingTriggerCap(int cap) {
        this.ringTriggerCap = Math.max(1, cap);
    }

    /**
     * 改圈脉冲间隔（默认 {@value #DEFAULT_RING_INTERVAL_TICKS} 刻）。
     * <p>只影响**未抬起**的圈：抬起的圈按召圣口径固定为 1 秒一次（见 {@link #liftMarkedArrows()}）。
     * 改动从"下次脉冲排期"起生效。
     *
     * @param ticks 新的间隔（刻）；{@code < 1} 归一为 1
     */
    public void setRingIntervalTicks(int ticks) {
        this.ringIntervalTicks = Math.max(1, ticks);
    }

    /** 清空全部运行期状态（标记 / 圈 / 召回箭 / 暂停位 / 召回计数）。它是 {@link #stop()} 的第一步。 */
    public void clearAll() {
        markedArrows.clear();
        recallArrows.clear();
        recallHitCounts.clear();
        ringDamageSuspended = false;
        recalling = false;
        nextBellTick = 0;
        lastAnyRingHitTick = Integer.MIN_VALUE / 2;

    }

    // ═════════════════════════ 每 tick ═════════════════════════

    @Override
    public void update() {
        Player owner = selfPlayer();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        updateMarkedArrows(now);
        updateRecallArrows(owner);
    }

    /** 推进标记箭（飞行拖尾 / 落地成圈）与共鸣圈（脉冲结算 / 到期消散）。 */
    private void updateMarkedArrows(int now) {
        if (markedArrows.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, MarkedArrow>> iterator = markedArrows.entrySet().iterator();
        while (iterator.hasNext()) {
            MarkedArrow mark = iterator.next().getValue();
            if (mark.expired) {
                iterator.remove();
                continue;
            }
            if (mark.stopped) {
                pulseRingIfDue(mark, now);
                if (mark.expired) {
                    iterator.remove();
                }
                continue;
            }
                        advanceFlyingArrow(mark, now);
            if (mark.expired) {
                iterator.remove();
            }
        }
        if (activeRingCount() > 0) {
            tickResonanceBell(now);
        } else {
            nextBellTick = 0;
        }
    }

    /**
     * 共鸣圈的**钟声节拍器**：一条全角色共用的钟，而不是每圈各敲各的。
     *
     * <p>为什么共用一条：钟是"主教的心跳"，多个圈各自敲会变成噪音；位置取离主人最近的活圈心。
     * <p>节奏（2026-10-30 需求）：默认 2.5 秒一记；最近 {@value #BELL_HOT_WINDOW_TICKS} 刻内
     * 有圈打中过人 ⇒ 加快到 1 秒一记，窗口过期后自动落回 2.5 秒。
     */
    private void tickResonanceBell(int now) {
        boolean hot = now - lastAnyRingHitTick <= BELL_HOT_WINDOW_TICKS;
        int interval = hot ? BELL_HOT_INTERVAL_TICKS : BELL_IDLE_INTERVAL_TICKS;
        if (nextBellTick == 0) {
            nextBellTick = now + interval;
            return;
        }
        if (now < nextBellTick) {
            return;
        }
        nextBellTick = now + interval;
        Location anchor = bellAnchor();
        if (anchor != null && anchor.getWorld() != null) {
            LetheSound.ringBell(anchor.getWorld(), anchor);
        }
    }

    /** 钟声落点：离主人最近的活圈心；一个圈都没有时退回主人脚下。 */
    private Location bellAnchor() {
        Player owner = selfPlayer();
        Location fallback = owner != null ? owner.getLocation() : null;
        Location best = null;
        double bestDistance = Double.MAX_VALUE;
        for (MarkedArrow mark : markedArrows.values()) {
            if (!mark.stopped || mark.stopPoint == null || mark.stopPoint.getWorld() == null) {
                continue;
            }
            double distance = fallback == null ? 0d : mark.stopPoint.distanceSquared(fallback);
            if (best == null || distance < bestDistance) {
                best = mark.stopPoint;
                bestDistance = distance;
            }
        }
        return best != null ? best : fallback;
    }

    /** 推进一支飞行中的标记箭：刷新拖尾；插进方块 / 实体已消失时按最后位置落圈。 */
    private void advanceFlyingArrow(MarkedArrow mark, int now) {
        Projectile projectile = mark.projectile;
        if (projectile == null || !projectile.isValid() || projectile.isDead()) {
            // 箭被别的东西移除 / 世界卸载：这份标记仍然要兑现成一个圈（否则玩家白射一箭）
            spawnRing(mark, null);
            return;
        }
        Location current = projectile.getLocation();
        if (current == null || current.getWorld() == null) {
            mark.expired = true;
            return;
        }
        if (now >= mark.nextTrailTick) {
            mark.nextTrailTick = now + TRAIL_INTERVAL_TICKS;
            Location previous = mark.lastPosition;
            if (previous != null && previous.getWorld() == current.getWorld()) {
                LetheVfx.arrowMarkTrail(current.getWorld(), previous, current.clone());
            }
        }
        mark.lastPosition = current.clone();
        if (projectile instanceof AbstractArrow arrow && arrow.isInBlock()) {
            spawnRing(mark, projectile);
        }
    }

    /**
     * 把这支标记兑现成共鸣圈：记停点、移除箭矢实体、播生成表现。
     * <p><b>幂等</b>：已经成圈的直接返回（只顺手确认实体已移除）。
     *
     * @param projectile 停止的箭矢（可为 {@code null} = 箭已经不存在，只能用最后位置）
     */
    private void spawnRing(MarkedArrow mark, Projectile projectile) {
        if (mark.stopped) {
            if (projectile != null && projectile.isValid()) {
                projectile.remove();
            }
            return;
        }
        Location center = mark.lastPosition != null ? mark.lastPosition.clone() : null;
        //只在实体仍然有效时读它的位置 / 移除它：已失效实体的位置读口是否可用属平台细节，
        //  而"最后位置"本来就在逐刻刷新，用它足够落圈。
        if (projectile != null && projectile.isValid()) {
            Location current = projectile.getLocation();
            if (current != null) {
                center = current.clone();
                mark.lastPosition = center.clone();
            }
            projectile.remove();
        }
        mark.projectile = null;
        if (center == null || center.getWorld() == null) {
            // 位置信息已经不可用：这条标记没有可落地的点，安静丢弃（不抛）。
            mark.expired = true;
            return;
        }
        mark.stopped = true;
        mark.stopPoint = center;
        mark.triggers = 0;
        mark.ringSpawnTick = Bukkit.getCurrentTick();
        mark.nextPulseTick = mark.ringSpawnTick + effectiveRingInterval(mark);
        LetheVfx.ringSpawn(center.getWorld(), center);
        LetheSound.ringSpawn(center.getWorld(), center);
        //第一个圈出现时启动钟声节拍器：默认 2.5 秒一记（命中后由 tickResonanceBell 加速到 1 秒）
        if (activeRingCount() == 1) {
            nextBellTick = mark.ringSpawnTick + BELL_IDLE_INTERVAL_TICKS;
        }

    }

    /** 圈到点就结算一次脉冲：表现 → 伤害 → 触发计数 → 到期判定。 */
    private void pulseRingIfDue(MarkedArrow mark, int now) {
        if (now < mark.nextPulseTick) {
            return;
        }
        Location center = mark.stopPoint;
        if (center == null || center.getWorld() == null) {
            mark.expired = true;
            return;
        }
        mark.nextPulseTick = now + effectiveRingInterval(mark);
        if (!mark.lifted && now - mark.ringSpawnTick >= RING_LIFETIME_TICKS) {
            //空场超时：触发次数不会耗尽，用最长存活期收掉（见 RING_LIFETIME_TICKS 的理由）
            expireRing(mark);
            return;
        }
        int triggersLeft = mark.lifted
                ? LIFTED_RING_TRIGGER_READING
                : Math.max(0, ringTriggerCap - mark.triggers);
        // ── 先结算：这一拍是否打中了敌人（同时决定"全特效还是简化画法"与钟声加速）──
        boolean hit = false;
        if (!ringDamageSuspended) {
            Player owner = selfPlayer();
            if (owner != null) {
                List<Player> victims = hostilePlayersNear(center, RING_RADIUS);
                if (!victims.isEmpty()) {
                    hit = true;
                    lastAnyRingHitTick = now;
                    for (Player victim : victims) {
                        if (vitals != null) {
                            vitals.trueDamage(victim, owner, RING_TRUE_DAMAGE);
                        }
                        if (sante != null) {
                            sante.decreaseSanTE(victim.getUniqueId(), RING_SANTE_DAMAGE);
                        }
                    }
                    mark.triggers++;
                    if (!mark.lifted && mark.triggers >= ringTriggerCap) {
                        expireRing(mark);
                        return;   //打满触发的散场表现由 expireRing 自己播，不再叠一帧脉冲
                    }
                }
            }
        }
        // ── 表现（2026-10-30 口径）──
        //  命中 或 生成后 1 秒内 ⇒ 完整画法；其余 ⇒ 简化画法（蓝圈 + 白点 + 圈内少量散点）
        if (hit || now - mark.ringSpawnTick < RING_RICH_HEAD_TICKS) {
            LetheVfx.ringPulse(center.getWorld(), center, triggersLeft);
        } else {
            LetheVfx.ringPulseSimple(center.getWorld(), center);
        }
        LetheSound.ringPulse(center.getWorld(), center);
    }

    /** 圈到期：播散场表现并标记为待移除。 */
    private void expireRing(MarkedArrow mark) {
        Location center = mark.stopPoint;
        if (center != null && center.getWorld() != null) {
            LetheVfx.ringExpire(center.getWorld(), center);
            LetheSound.ringExpire(center.getWorld(), center);
        }
        mark.expired = true;
    }

    /**
     * 推进召回箭：每刻朝施法者当前位置前进 {@value #RECALL_STEP} 格；
     * 终点那一段沿途扫一次命中，到点即消失。
     */
    private void updateRecallArrows(Player owner) {
        if (recallArrows.isEmpty()) {
            if (recalling) {
                recalling = false;
        nextBellTick = 0;
        lastAnyRingHitTick = Integer.MIN_VALUE / 2;

                recallHitCounts.clear();
            }
            return;
        }
        Location home = owner.getLocation().clone().add(0d, 1.0d, 0d);
        if (home.getWorld() == null) {
            recallArrows.clear();
            recallHitCounts.clear();
            recalling = false;
        nextBellTick = 0;
        lastAnyRingHitTick = Integer.MIN_VALUE / 2;

            return;
        }
        Iterator<RecallArrow> iterator = recallArrows.iterator();
        while (iterator.hasNext()) {
            RecallArrow recallArrow = iterator.next();
            Location start = recallArrow.position;
            if (start == null || start.getWorld() == null || !start.getWorld().equals(home.getWorld())) {
                // 换了世界（或位置已失效）的箭没法继续飞：直接消散
                iterator.remove();
                continue;
            }
            Vector delta = home.toVector().subtract(start.toVector());
            double distance = delta.length();
            if (distance <= ARRIVE_EPSILON) {
                iterator.remove();
                continue;
            }
            double step = Math.min(RECALL_STEP, distance);
            Location next = start.clone().add(delta.multiply(step / distance));
            //召回拖尾隔刻画（2026-10-30 按反馈降频）：箭每刻走 2 格，隔刻已连成线，粒子量减半
            if ((recallArrow.trailTick++ & 1) == 0 && next.getWorld() != null) {
                LetheVfx.summonRecallTrail(next.getWorld(), next);
            }
            sweepRecallHits(owner, start, next, recallArrow);
            if (step >= distance - ARRIVE_EPSILON) {
                iterator.remove();
            } else {
                recallArrow.position = next;
            }
        }
        if (recallArrows.isEmpty()) {
            recalling = false;
        nextBellTick = 0;
        lastAnyRingHitTick = Integer.MIN_VALUE / 2;

            recallHitCounts.clear();
        }
    }

    /**
     * 对这**一段**位移做命中扫描：与线段距离 ≤ {@value #RECALL_HIT_RADIUS} 的敌人被这支箭结算一次。
     *
     * <p>为什么按"线段"而不是"当前点"判定：箭每刻走 2 格，而命中半径是 {@value #RECALL_HIT_RADIUS} 格 ——
     * 只看当前点会从两个敌人中间"穿过去"而不结算。线段判定把这一段的覆盖面补齐，与箭的连续性一致。
     */
    private void sweepRecallHits(Player owner, Location from, Location to, RecallArrow recallArrow) {
        if (faction == null) {
            return;
        }
        Location middle = from.clone().add(to.toVector().subtract(from.toVector()).multiply(0.5d));
        double sweepRadius = RECALL_HIT_RADIUS + RECALL_STEP * 0.5d + 0.1d;
        for (Player victim : middle.getNearbyPlayers(sweepRadius)) {
            if (victim == null || victim.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            if (!victim.isOnline() || victim.isDead() || victim.getHealth() <= 0d) {
                continue;
            }
            if (!faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            UUID victimId = victim.getUniqueId();
            //① 同一支箭对同一敌人只结算一次
            if (recallArrow.hitVictims.contains(victimId)) {
                continue;
            }
            //② 同一敌人整次召回最多吃 RECALL_HIT_ARROW_CAP 支箭
            if (recallHitCounts.getOrDefault(victimId, 0) >= RECALL_HIT_ARROW_CAP) {
                continue;
            }
            Location body = victim.getLocation().clone().add(0d, 1.0d, 0d);
            if (distanceToSegment(body, from, to) > RECALL_HIT_RADIUS) {
                continue;
            }
            recallArrow.hitVictims.add(victimId);
            recallHitCounts.merge(victimId, 1, Integer::sum);
            if (vitals != null) {
                vitals.trueDamage(victim, owner, RECALL_TRUE_DAMAGE);
            }
            if (sante != null) {
                sante.decreaseSanTE(victimId, RECALL_SANTE_DAMAGE);
            }
            World world = victim.getWorld();
            LetheVfx.damageSoul(world, victim.getLocation());
            LetheVfx.damageSante(world, victim.getLocation());
        }
    }

    // ═════════════════════════ 内部工具 ═════════════════════════

    /** 本实例的玩家（离线下回 {@code null}；四个入口都容忍它）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    /** 圈脉冲 / 抬升的实际间隔：抬起的圈固定 1 秒一次，其余按当前配置。 */
    private int effectiveRingInterval(MarkedArrow mark) {
        return mark.lifted ? LIFTED_RING_INTERVAL_TICKS : ringIntervalTicks;
    }

    /**
     * 半径内的敌对玩家（排除自己；敌我判定只走阵营组件，不在这里重写"无阵营 = 敌人"等规则）。
     * <p>返回列表（而不是直接结算）：调用方需要先知道"有没有人"再决定是否消耗触发次数。
     */
    private List<Player> hostilePlayersNear(Location center, double radius) {
        List<Player> victims = new ArrayList<>();
        if (faction == null || center == null || center.getWorld() == null) {
            return victims;
        }
        Player owner = selfPlayer();
        for (Player candidate : center.getNearbyPlayers(radius)) {
            if (candidate == null || !candidate.isOnline() || candidate.isDead()
                    || candidate.getHealth() <= 0d) {
                continue;
            }
            if (owner != null && owner.getUniqueId().equals(candidate.getUniqueId())) {
                continue;
            }
            if (!faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            victims.add(candidate);
        }
        return victims;
    }

    /**
     * 该位置能否站人：脚格与头格都必须可通过。
     * <p>抬升判定只验目标点，理由见类注释口径申报二。
     * <p>未加载的区块按"不满足"处理，而不是在这里同步加载它：抬升只是表现 + 节奏调整，
     * 为了一次抬升去拉一个区块不值得（且会在 tick 里制造卡顿）。
     */
    private static boolean canStandAt(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        if (!at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) {
            return false;
        }
        Block feet = at.getBlock();
        if (!feet.isPassable()) {
            return false;
        }
        return feet.getRelative(0, 1, 0).isPassable();
    }

    /** 点到线段的距离（召回命中判定的几何内核）。 */
    private static double distanceToSegment(Location point, Location from, Location to) {
        Vector segment = to.toVector().subtract(from.toVector());
        Vector offset = point.toVector().subtract(from.toVector());
        double lengthSquared = segment.lengthSquared();
        double t = lengthSquared <= 1.0E-9d ? 0d : Math.clamp(offset.dot(segment) / lengthSquared, 0d, 1d);
        Vector closest = from.toVector().add(segment.multiply(t));
        return point.toVector().distance(closest);
    }

    // ═════════════════════════ 内部状态类型 ═════════════════════════

    /**
     * 一支被标记的箭的完整状态（纯数据）：飞行中是"观察记录"，停下后是"共鸣圈"。
     * <p>两类状态共用同一个对象，是为了让"停点 / 触发次数 / 是否抬起"只有一份 —— 见类注释。
     */
    private static final class MarkedArrow {

        /** 是否由「血泪」射出（当前只做状态保留，口径见 {@link #markArrow(Projectile, boolean)}）。 */
        private final boolean bloodTear;

        /** 飞行中的箭矢实体；停下后置 {@code null}（圈不再持有实体引用，避免误操作已移除实体）。 */
        private Projectile projectile;

        /** 最后一刻看到的箭矢位置（实体消失时用它落圈）。 */
        private Location lastPosition;

        /** 是否已经停下（{@code true} = 已经是共鸣圈）。 */
        private boolean stopped;

        /** 停点（共鸣圈中心；被召圣抬起后就是抬起后的位置）。 */
        private Location stopPoint;

        /** 是否被召圣抬起（抬起后 1 秒一次、不过期）。 */
        private boolean lifted;

        /** 是否应当从标记表移除（落地失败 / 已到期）。 */
        private boolean expired;

        /** 已经兑现过的伤害脉冲次数（只对未抬起的圈有意义）。 */
        private int triggers;

        /** 下一次拖尾粒子的绝对刻。 */
        private int nextTrailTick;

        /** 下一次脉冲的绝对刻。 */
        private int nextPulseTick;

        /** 成圈的绝对刻（用于最长存活判定；未成圈时为 0）。 */
        private int ringSpawnTick;

        private MarkedArrow(Projectile projectile, boolean bloodTear) {
            this.bloodTear = bloodTear;
            this.projectile = projectile;
            this.lastPosition = projectile.getLocation().clone();
        }
    }

    /** 召回阶段的一支"逻辑箭"：一个会动的点 + 它已经打中过谁。 */
    private static final class RecallArrow {

        /** 此刻位置（每 tick 朝施法者推进）。 */
        private Location position;

        /** 这支箭已经结算过的敌人（同一支箭不重复打同一个人）。 */
        private final Set<UUID> hitVictims = new HashSet<>();

        /** 拖尾降频计数（召回拖尾隔刻画一次）。 */
        private int trailTick;

        private RecallArrow(Location position) {
            this.position = position;
        }
    }
}
