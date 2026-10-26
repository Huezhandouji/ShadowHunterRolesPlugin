package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * **「影稳态失衡」的全局账本**（芹泽沙耶香体系的唯一真值持有者）。
 *
 * <h2>为什么是静态账本，而不是某组件的字段</h2>
 * 「失衡」挂在**被攻击的敌人**身上，而所有相关读写都**跨玩家实例**：
 * <ul>
 *   <li>叠层方 = 芹泽沙耶香自己的主武器 / 技能（攻击瞬时）；</li>
 *   <li>结算方 = 该玩家的**被动组件**（每刻扫描爆发中的条目：扣 SAN、回复猎影人）；</li>
 *   <li>读方 = 主武器（「对处于失衡爆发的敌人攻击时额外回血」）。</li>
 * </ul>
 * 而框架的组件查找面（{@code ComponentLookupPort}）**只覆盖本角色实例内**的组件，
 * 拿不到"别的玩家身上的层数"。⇒ 层数与爆发状态必须住在一处**玩家无关**的地方
 * （与 {@code TekTruth} 同一形态：静态真值持有者，放角色自己的包里，不碰 {@code core/}）。
 *
 * <h2>需求映射（本类只做"真值怎么变"，不做任何副作用）</h2>
 * <ul>
 *   <li>失衡上限 {@value #MAX} 点；积满 ⇒ 触发失衡爆发；</li>
 *   <li>爆发持续 {@value #BURST_TICKS} 刻（20 秒）；</li>
 *   <li><b>爆发期间不再积累</b>（{@link #add} 直接忽略），到期后从 0 重新积累；</li>
 *   <li>爆发期间每秒结算一次（{@value #BURST_DRAIN_INTERVAL} 刻一拍，共
 *       {@value #BURST_DRAIN_COUNT} 拍）——拍数由 {@link #markDrained} 记账，
 *       即使某刻被跳过也能在下一刻补上，不会少结。</li>
 *   <li><b>闲置消退</b>（2026-10-18 需求）：有层数的目标若 <b>{@value #DECAY_IDLE_TICKS} 刻
 *       （10 秒）</b>没再被叠层，则此后<b>每 {@value #DECAY_INTERVAL_TICKS} 刻（0.3 秒）消退 1 层</b>；
 *       期间任何一次新叠层都会把计时重新拉满。</li>
 * </ul>
 *
 * <h2>线程安全 / 生命周期</h2>
 * 底层 {@link ConcurrentHashMap}，所有复合操作在 {@link Entry} 的同步块内完成
 * （Bukkit 主线程单线，但账本会被离线单测直接读写，防御性处理）。
 * <b>清扫</b>由被动侧每刻调用 {@link #sweep}（主体死亡 / 离线 / 条目过期），
 * 见工程口径「全局账本的清扫必须在早退守卫之前」。
 *
 * <p><b>为什么放这里而不是 {@code core/}</b>：本类是芹泽沙耶香的领域知识（「影稳态失衡」只属于她），
 * 按工程纪律"角色自己的东西放自己的包"；{@code core/} 属禁改区。
 */
public final class SayakaImbalance {

    private SayakaImbalance() {
    }

    // ───────── 数值口径 ─────────

    /** **失衡上限**：需求「上限 30 点」 ⇒ 积满即触发爆发。 */
    public static final int MAX = 30;

    /** 爆发持续（刻）—— 需求「持续 20 秒」。 */
    public static final int BURST_TICKS = 400;

    /** 爆发期间每秒一拍（刻）。 */
    public static final int BURST_DRAIN_INTERVAL = 20;

    /** 爆发期间共结算几拍（= 20 秒 / 每秒一拍）。 */
    public static final int BURST_DRAIN_COUNT = BURST_TICKS / BURST_DRAIN_INTERVAL;

    /**
     * **闲置消退的起算时长（刻）**——需求：「10 秒未受到新层数叠加」⇒ 开始消退。
     *
     * <p>注意是**从最后一次成功叠层**起算，不是从层数变化起算：消退本身不重置计时，
     * 否则会变成"永远退不掉第 2 层"。
     */
    public static final int DECAY_IDLE_TICKS = 200;

    /** **消退节拍（刻）**——需求：「0.3 秒消退 1 层」⇒ 6 刻。 */
    public static final int DECAY_INTERVAL_TICKS = 6;

    /** 条目过期（毫秒）：这么久没被任何写入触碰、且不在爆发中 ⇒ 视为战斗结束，顺手清掉。 */
    private static final long STALE_MILLIS = 3L * 60L * 1000L;

    /** 失衡账本：键 = 被叠层玩家 UUID。 */
    private static final Map<UUID, Entry> LEDGER = new ConcurrentHashMap<>();

    /** 一条账目（失衡层数 + 爆发状态），所有复合操作在自身锁内完成。 */
    private static final class Entry {
        /** 当前失衡层数（爆发触发时归零）。 */
        private int amount;
        /** 爆发结束刻（{@code 0} = 从未爆发过）。 */
        private int burstUntilTick;
        /** 本次爆发开始刻。 */
        private int burstStartTick;
        /** 本次爆发的"开场效果"（8 真伤 / 虚弱 / 奖励）是否已被被动执行过。 */
        private boolean startProcessed;
        /** 本次爆发已结算的"每秒拍"数（0..{@value #BURST_DRAIN_COUNT}）。 */
        private int drainedCount;
        /** 触发本次爆发的芹泽沙耶香 UUID（只有她的被动实例负责执行爆发效果）。 */
        private UUID burstOwner;
        /** **最后一次成功叠层的刻**（闲置消退计时起点）。 */
        private int lastStackTick;
        /** 最后一次触碰时刻（毫秒，用于过期清扫）。 */
        private long touchedAt;

        private Entry(long nowMillis) {
            this.touchedAt = nowMillis;
        }
    }

    // ───────── 载荷 ─────────

    /**
     * 一次叠层的结算结果。
     *
     * @param before 叠层前的层数
     * @param after  叠层后的层数（触发爆发时为 0 —— 需求「积满后重新积累从 0 起」）
     * @param triggered 是否**本次**把条积满并触发了爆发
     * @param blockedDuringBurst 是否因「爆发期间不积累」而被忽略
     */
    public record AddResult(int before, int after, boolean triggered, boolean blockedDuringBurst) {
    }

    /**
     * 一条只读快照（被动每刻迭代用；不可变）。
     */
    public record View(UUID target, int amount, int burstUntilTick, int burstStartTick,
                       boolean startProcessed, int drainedCount, UUID burstOwner) {
    }

    /**
     * **一次闲置消退事件**（被动据此播放"影尘消散"的观感）。
     *
     * @param target 被消退的目标
     * @param before 消退前的层数
     * @param after  消退后的层数（{@code 0} = 条已退干净）
     */
    public record DecayEvent(UUID target, int before, int after) {
    }

    // ───────── 写面 ─────────

    /**
     * **叠加失衡**（主武器 / 技能 / 榴弹场命中时调用）。
     *
     * <p>规则（逐条对齐需求）：
     * <ol>
     *   <li>层数 = {@code min(MAX, 当前 + amount)}；</li>
     *   <li>积满 ⇒ 触发爆发：层数归零、记录爆发窗口与触发者（{@code owner}），返回
     *       {@code triggered = true}；</li>
     *   <li>**爆发期间叠层被忽略**（{@code blockedDuringBurst = true}），到期后可重新积累。</li>
     * </ol>
     *
     * @param target  被叠层的玩家（{@code null} ⇒ 无副作用）
     * @param amount  叠层数（{@code <= 0} ⇒ 无副作用）
     * @param nowTick 当前刻（由调用方传入 ⇒ 本类可离线单测，不碰 Bukkit）
     * @param owner   触发者（芹泽沙耶香）UUID；可为 {@code null}（层数照记，但无人执行爆发效果）
     */
    public static AddResult add(UUID target, int amount, int nowTick, UUID owner) {
        if (target == null || amount <= 0) {
            return new AddResult(0, 0, false, false);
        }
        Entry entry = LEDGER.computeIfAbsent(target, key -> new Entry(System.currentTimeMillis()));
        synchronized (entry) {
            entry.touchedAt = System.currentTimeMillis();
            if (isBursting(entry, nowTick)) {
                return new AddResult(entry.amount, entry.amount, false, true);
            }
            //只在"真的叠上了"这一路径重置消退计时（爆发中被忽略的叠层不算）
            entry.lastStackTick = nowTick;
            int before = entry.amount;
            int after = Math.min(MAX, before + amount);
            if (after >= MAX) {
                entry.amount = 0;
                entry.burstStartTick = nowTick;
                entry.burstUntilTick = nowTick + BURST_TICKS;
                entry.startProcessed = false;
                entry.drainedCount = 0;
                entry.burstOwner = owner;
                return new AddResult(before, 0, true, false);
            }
            entry.amount = after;
            return new AddResult(before, after, false, false);
        }
    }

    /**
     * 标记「本次爆发的开场效果已被执行」（被动侧在结算 8 真伤 / 虚弱 / 奖励**之前**调用，
     * 保证幂等：即使同一条目被两处看到，效果也只会打一次）。
     */
    public static void markStartProcessed(UUID target) {
        Entry entry = target == null ? null : LEDGER.get(target);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.startProcessed = true;
            entry.touchedAt = System.currentTimeMillis();
        }
    }

    /** 记一拍「每秒结算」（被动侧在扣 SAN / 回复猎影人**之后**调用）。 */
    public static void markDrained(UUID target) {
        Entry entry = target == null ? null : LEDGER.get(target);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            if (entry.drainedCount < BURST_DRAIN_COUNT) {
                entry.drainedCount++;
            }
            entry.touchedAt = System.currentTimeMillis();
        }
    }

    /**
     * **结束失衡并把"爆发 CD"清零**（2026-10-18 需求 3：敌人死亡时）。
     *
     * <p>清零的四个量合起来就是这条账目的"CD"：
     * <ul>
     *   <li>{@code burstUntilTick} —— 爆发窗口（也就是"期间不再积累"的锁定）；</li>
     *   <li>{@code burstStartTick} / {@code drainedCount} —— 每秒结算的进度（不清零会在复活后继续补拍）；</li>
     *   <li>{@code startProcessed} / {@code burstOwner} —— 开场效果与触发者。</li>
     * </ul>
     * 层数一并归零。之后同一个 UUID 再被叠层时，等同全新目标。
     */
    public static void endBurst(UUID target) {
        Entry entry = target == null ? null : LEDGER.get(target);
        if (entry == null) {
            return;
        }
        synchronized (entry) {
            entry.amount = 0;
            entry.burstUntilTick = 0;
            entry.burstStartTick = 0;
            entry.startProcessed = false;
            entry.drainedCount = 0;
            entry.burstOwner = null;
            entry.lastStackTick = 0;
            entry.touchedAt = System.currentTimeMillis();
        }
    }

    /** 清掉一条账目（死亡 / 离线 / 过期）—— **先结束失衡并刷新 CD，再移除**。 */
    public static void clear(UUID target) {
        if (target != null) {
            endBurst(target);
            LEDGER.remove(target);
        }
    }

    /**
     * **周期性清扫**（被动每刻调用，必须放在早退守卫**之前** —— 工程口径）。
     *
     * <p>三条清理条件（取或，宁可多清不可漏清）：
     * <ol>
     *   <li>{@code isGone} 为真（目标离线 / 死亡）；</li>
     *   <li>不在爆发中且层数归零（爆发已收尾的空条目）；</li>
     *   <li>久未触碰（{@value #STALE_MILLIS} 毫秒）且不在爆发中。</li>
     * </ol>
     *
     * @param nowTick 当前刻
     * @param isGone  目标是否已离场（{@code null} ⇒ 跳过该条检查，便于离线单测）
     */
    public static void sweep(int nowTick, Predicate<UUID> isGone) {
        long nowMillis = System.currentTimeMillis();
        List<UUID> removal = new ArrayList<>();
        for (Map.Entry<UUID, Entry> pair : LEDGER.entrySet()) {
            UUID target = pair.getKey();
            Entry entry = pair.getValue();
            boolean gone;
            boolean idleEmpty;
            boolean stale;
            synchronized (entry) {
                gone = isGone != null && isGone.test(target);
                idleEmpty = !isBursting(entry, nowTick) && entry.amount <= 0;
                stale = !isBursting(entry, nowTick) && nowMillis - entry.touchedAt > STALE_MILLIS;
            }
            if (gone || idleEmpty || stale) {
                removal.add(target);
            }
        }
        for (UUID target : removal) {
            LEDGER.remove(target);
        }
    }

    /**
     * **推进"闲置消退"**（被动每刻调用一次）。
     *
     * <p>规则：有层数、且<b>距最后一次成功叠层 ≥ {@value #DECAY_IDLE_TICKS} 刻（10 秒）</b>时，
     * 此后每 {@value #DECAY_INTERVAL_TICKS} 刻（0.3 秒）消退 1 层，直至 0。
     * <ul>
     *   <li>爆发中的条目不消退（那时层数本来就是 0，且"爆发期间不再积累"）；</li>
     *   <li>层数归零后由 {@link #sweep} 的 {@code idleEmpty} 分支顺手清掉条目；</li>
     *   <li>实现按"距上次叠层的总闲置时长"**一次算够应退几层**，因此即使某刻 update
     *       被跳过（卡顿 / 掉刻）也不会漏退 —— 与 {@link #drainTargetFor} 同一思路。</li>
     * </ul>
     *
     * @param nowTick 当前刻
     * @return 本刻发生了消退的条目（被动据此播放影尘消散的观感；无消退 ⇒ 空表）
     */
    public static List<DecayEvent> decay(int nowTick) {
        List<DecayEvent> events = new ArrayList<>();
        for (Map.Entry<UUID, Entry> pair : LEDGER.entrySet()) {
            Entry entry = pair.getValue();
            synchronized (entry) {
                if (entry.amount <= 0 || isBursting(entry, nowTick)) {
                    continue;
                }
                int after = decayedAmount(entry.amount, nowTick, entry.lastStackTick);
                if (after < entry.amount) {
                    int before = entry.amount;
                    entry.amount = after;
                    events.add(new DecayEvent(pair.getKey(), before, after));
                }
            }
        }
        return events;
    }

    // ───────── 读面 ─────────

    /** 该玩家此刻是否处于失衡爆发（{@code false} = 未爆发 / 已到期）。 */
    public static boolean isBursting(UUID target, int nowTick) {
        Entry entry = target == null ? null : LEDGER.get(target);
        if (entry == null) {
            return false;
        }
        synchronized (entry) {
            return isBursting(entry, nowTick);
        }
    }

    /** 当前层数（未入账 ⇒ 0）。 */
    public static int amountOf(UUID target) {
        Entry entry = target == null ? null : LEDGER.get(target);
        if (entry == null) {
            return 0;
        }
        synchronized (entry) {
            return entry.amount;
        }
    }

    /** 全部条目的只读快照（被动每刻迭代用）。 */
    public static List<View> snapshot() {
        List<View> views = new ArrayList<>();
        for (Map.Entry<UUID, Entry> pair : LEDGER.entrySet()) {
            Entry entry = pair.getValue();
            synchronized (entry) {
                views.add(new View(pair.getKey(), entry.amount, entry.burstUntilTick, entry.burstStartTick,
                        entry.startProcessed, entry.drainedCount, entry.burstOwner));
            }
        }
        return views;
    }

    /** 条目数（诊断读口 / 单测用）。 */
    public static int size() {
        return LEDGER.size();
    }

    // ───────── 纯函数 ─────────

    /** 爆发中判定（纯函数，调用方须持有该条目的锁或接受读瞬时值）。 */
    private static boolean isBursting(Entry entry, int nowTick) {
        return entry.burstUntilTick > nowTick;
    }

    /**
     * **某一层数叠加后是否会触发爆发**（纯函数 ⇒ 离线单测）。
     *
     * @return 叠满的档位：{@code 0} = 未叠满；{@code 1} = 本次叠满
     */
    public static int triggersBurst(int current, int amount) {
        return Math.min(MAX, Math.max(0, current) + Math.max(0, amount)) >= MAX ? 1 : 0;
    }

    /**
     * **爆发中每分钟该结第几拍**（纯函数 ⇒ 离线单测）。
     *
     * @param elapsedTicks 爆发已过刻数
     * @param drained      已结算拍数
     * @return 此刻应当到达的拍数（{@code 0..}{@value #BURST_DRAIN_COUNT}）；
     *         调用方在 {@code 应结拍数 > 已结拍数} 时补结
     */
    public static int drainTargetFor(int elapsedTicks, int drained) {
        int target = Math.max(0, elapsedTicks) / BURST_DRAIN_INTERVAL;
        return Math.max(Math.max(0, drained), Math.min(BURST_DRAIN_COUNT, target));
    }

    /**
     * **闲置消退后的层数**（纯函数 ⇒ 离线单测）。
     *
     * <p>时间线（{@code lastStackTick = 0}）：
     * <pre>
     *   199 刻（&lt; 10 秒）      ⇒ 不退
     *   200 刻（= 10 秒）       ⇒ 退 1 层
     *   205 刻                  ⇒ 仍只退 1 层（下一拍在 206）
     *   206 刻（10.3 秒）       ⇒ 退 2 层
     *   …                       ⇒ 每 6 刻退 1 层
     * </pre>
     * 一次传入很大的 {@code nowTick}（掉刻）会一次退够，不会漏。
     *
     * @param amount       当前层数（{@code <= 0} ⇒ 0）
     * @param nowTick      当前刻
     * @param lastStackTick 最后一次**成功叠层**的刻
     * @return 消退后的层数（{@code 0..amount}）
     */
    public static int decayedAmount(int amount, int nowTick, int lastStackTick) {
        int current = Math.max(0, amount);
        if (current <= 0) {
            return 0;
        }
        int idle = nowTick - lastStackTick;
        if (idle < DECAY_IDLE_TICKS) {
            return current;     //还在 10 秒保护期内（idle < 0 也走这里：刻被重置时按"刚叠过"处理）
        }
        int steps = 1 + (idle - DECAY_IDLE_TICKS) / DECAY_INTERVAL_TICKS;
        return Math.max(0, current - steps);
    }
}
