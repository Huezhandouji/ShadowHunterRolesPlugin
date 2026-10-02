package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * **「真理」层数的全局账本**（特克体系的唯一真值持有者）。
 *
 * <h2>为什么是静态账本，而不是某组件的字段</h2>
 * 「真理」挂在**被击中的敌人**身上，而三条需求都要**跨玩家实例**读它：
 * <ul>
 *   <li>「真理之刺」的解锁条件 = <b>场上</b>有任何角色真理 ≥ {@link #UNLOCK_THRESHOLD}；</li>
 *   <li>「真理之刺」结算后要<b>清空场上所有角色的真理</b>；</li>
 *   <li>「逆转天意」的护盾与「落岳」的叠加都要按目标读写。</li>
 * </ul>
 * 而框架的组件查找面（{@code ComponentLookupPort}）<b>只覆盖本角色实例内</b>的组件，
 * 拿不到"别的玩家身上的另一个特克实例" ⇒ 层数必须住在一处**玩家无关**的地方。
 *
 * <h2>为什么不上永久化 / 记分板</h2>
 * 真理只是战斗中的临时层数（一次遭遇战内有效）；永久化会污染存档。
 * 本类因此是**内存账本 + 惰性过期**：超过 {@link #STALE_MILLIS} 没有被任何写入触碰的条目，
 * 在下一次读写时被顺手清掉，不注册任何任务、不依赖任何框架能力。
 *
 * <h2>口径申报（如实）</h2>
 * <ul>
 *   <li>键 = 被叠层的玩家 UUID；值 = 层数（0 层即时移除，不留空条目）；</li>
 *   <li>本类**不做阵营判定**：调用方（组件）自己用 {@code svc().roleInfo()} 判敌，再决定是否叠层；</li>
 *   <li>本类**不做任何副作用**（不播声音、不画粒子、不发伤害）—— 那些全部归组件；</li>
 *   <li>线程安全：底层 {@link ConcurrentHashMap}；所有复合操作在 {@link Entry} 的同步块内完成
 *       （Bukkit 主线程单线，但账本可能被离线单测 / 探针读，防御性处理）。</li>
 * </ul>
 *
 * <p><b>为什么放这里而不是 {@code core/}</b>：本类是特克角色的领域知识（"真理"只属于特克），
 * 按工程纪律"角色自己的东西放自己的包"（同 {@code platform/FactionRelation} 那种静态真值持有者形态），
 * 且 {@code core/} 属禁改区。放在 {@code roleComponent/custom/tek/} 既满足"跨实例读"，
 * 又不动框架一根头发。
 */
public final class TekTruth {

    private TekTruth() {
    }

    // ───────── 数值口径 ─────────

    /** **解锁阈值**：场上有人真理 ≥ 该值时，「真理之刺」可用（需求：大于等于 10）。 */
    public static final int UNLOCK_THRESHOLD = 10;

    /** 单次叠加的层数上限（防止无上限堆积；需求未给上限，取一个宽松值）。 */
    public static final int MAX_LAYERS = 99;

    /** 条目过期时长：这么久没有被触碰 ⇒ 视为战斗已结束，顺手清掉（毫秒）。 */
    private static final long STALE_MILLIS = 3L * 60L * 1000L;

    /** 真理账本：键 = 被叠层玩家 UUID，值 = 层数 + 最后一次触碰时刻。 */
    private static final Map<UUID, Entry> LEDGER = new ConcurrentHashMap<>();

    /** 一条账目（层数 + 时间戳），所有复合操作在自身锁内完成。 */
    private static final class Entry {
        private int layers;
        private long touchedAt;

        private Entry(int layers, long touchedAt) {
            this.layers = layers;
            this.touchedAt = touchedAt;
        }
    }

    // ───────── 写面 ─────────

    /**
     * **叠加真理**（需求：普攻命中 +1、突击命中 +1、刺霄命中 +1、落岳命中 +1）。
     *
     * @return 叠加后的层数（0 层不会留下条目；{@code target == null} ⇒ 返回 0 且无副作用）
     */
    public static int add(UUID target, int amount) {
        if (target == null || amount == 0) {
            return layersOf(target);
        }
        long now = System.currentTimeMillis();
        Entry entry = LEDGER.computeIfAbsent(target, key -> new Entry(0, now));
        synchronized (entry) {
            entry.touchedAt = now;
            entry.layers = clamp(entry.layers + amount);
            if (entry.layers <= 0) {
                LEDGER.remove(target);
                return 0;
            }
            return entry.layers;
        }
    }

    /** 叠加一层（常见路径的语义糖）。 */
    public static int addOne(UUID target) {
        return add(target, 1);
    }

    /** **清空某一个目标的真理**（返回被清掉的层数）。 */
    public static int clearOne(UUID target) {
        if (target == null) {
            return 0;
        }
        Entry removed = LEDGER.remove(target);
        if (removed == null) {
            return 0;
        }
        synchronized (removed) {
            return removed.layers;
        }
    }

    /**
     * **清空全场真理**（「真理之刺」结算后调用）。
     *
     * @return 被清掉的**总层数**（需求：去除场上所有"真理"层数）
     */
    public static int clearAll() {
        int total = 0;
        for (UUID key : new ArrayList<>(LEDGER.keySet())) {
            total += clearOne(key);
        }
        return total;
    }

    // ───────── 读面 ─────────

    /** 某一目标当前的真理层数（无条目 ⇒ 0）。 */
    public static int layersOf(UUID target) {
        if (target == null) {
            return 0;
        }
        Entry entry = LEDGER.get(target);
        if (entry == null) {
            return 0;
        }
        synchronized (entry) {
            return entry.layers;
        }
    }

    /**
     * **场上是否有角色真理 ≥ {@value #UNLOCK_THRESHOLD}**
     * （「真理之刺」的解锁闸门；需求：真理之刺当场上有真理 ≥ 10 的角色时解锁）。
     *
     * <p>本方法顺带做一次惰性过期清理（太久没被触碰的条目视为战斗已结束）。
     */
    public static boolean anyAtLeast(int threshold) {
        expireStale();
        for (Entry entry : LEDGER.values()) {
            synchronized (entry) {
                if (entry.layers >= threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 「真理之刺」是否解锁（等价于 {@code anyAtLeast(UNLOCK_THRESHOLD)}）。 */
    public static boolean isUnlocked() {
        return anyAtLeast(UNLOCK_THRESHOLD);
    }

    /**
     * **场上层数最高的目标**（「真理之刺」的选取源：需求是"最近的一名真理 ≥10 的角色"，
     * 由组件在候选集上再按距离挑最近；本方法只给"当前有真理的目标清单"）。
     *
     * @return 目标 UUID 与层数的不可变快照列表（无条目 ⇒ 空列表）
     */
    public static List<Target> snapshot() {
        expireStale();
        List<Target> result = new ArrayList<>();
        for (Map.Entry<UUID, Entry> e : LEDGER.entrySet()) {
            synchronized (e.getValue()) {
                if (e.getValue().layers > 0) {
                    result.add(new Target(e.getKey(), e.getValue().layers));
                }
            }
        }
        return List.copyOf(result);
    }

    /** 账本里的目标数（探针 / 单测读口）。 */
    public static int size() {
        return LEDGER.size();
    }

    /** **仅测试用**：清空整张账本（离线单测之间互不串扰）。 */
    public static void resetForTest() {
        LEDGER.clear();
    }

    /** 一条真理账目（目标 + 层数）。 */
    public record Target(UUID playerId, int layers) {
    }

    // ───────── 内部 ─────────

    private static int clamp(int value) {
        if (value < 0) {
            return 0;
        }
        return Math.min(value, MAX_LAYERS);
    }

    /** 惰性过期：触碰时刻早于 {@link #STALE_MILLIS} 的条目一律清掉。 */
    private static void expireStale() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Entry> e : LEDGER.entrySet()) {
            synchronized (e.getValue()) {
                if (now - e.getValue().touchedAt > STALE_MILLIS) {
                    LEDGER.remove(e.getKey());
                }
            }
        }
    }
}
