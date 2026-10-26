package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「影稳态失衡」全局账本的离线单测（{@link SayakaImbalance}，纯静态、不碰 Bukkit）。
 *
 * <h2>钉住的是什么</h2>
 * <ul>
 *   <li>叠层 / 上限 30 / 积满触发；</li>
 *   <li>触发时层数归零、爆发窗口 400 刻、触发者记录；</li>
 *   <li>★ <b>爆发期间不再积累</b>（需求原话）——{@code blockedDuringBurst}；</li>
 *   <li>★ 到期后可重新积累（从 0 起）；</li>
 *   <li>每秒一拍的目标拍数（{@code drainTargetFor}）——跳刻也能补上、不超结；</li>
 *   <li>清扫三条（离场 / 空条目 / 过期）——其中"空条目"= 爆发收尾后不再留存；</li>
 *   <li>★ <b>闲置消退</b>（2026-10-18）：10 秒没被叠层 ⇒ 每 0.3 秒退 1 层，新叠层重置计时，
 *       退到 0 后由 sweep 清掉。</li>
 * </ul>
 *
 * <p>★ 账本是静态的 ⇒ 每个用例用**独立 UUID**，避免互相污染；用例尾部顺手清理。
 */
public class SayakaImbalanceTest {

    private static UUID id() {
        return UUID.randomUUID();
    }

    /** 叠层：累加、不封顶前不触发。 */
    @Test
    public void addAccumulatesUntilFull() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.AddResult first = SayakaImbalance.add(target, 10, 0, owner);
            assertFalse(first.triggered());
            assertFalse(first.blockedDuringBurst());
            assertEquals(10, first.after());
            assertEquals(10, SayakaImbalance.amountOf(target));

            SayakaImbalance.AddResult second = SayakaImbalance.add(target, 10, 1, owner);
            assertFalse(second.triggered());
            assertEquals(20, second.after());
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** 积满：触发爆发、层数归零、记录爆发窗口与触发者。 */
    @Test
    public void reachingMaxTriggersBurstAndResetsLayers() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, 25, 100, owner);
            SayakaImbalance.AddResult burst = SayakaImbalance.add(target, 5, 101, owner);
            assertTrue(burst.triggered());
            assertEquals(0, burst.after());
            assertEquals(0, SayakaImbalance.amountOf(target));
            assertTrue(SayakaImbalance.isBursting(target, 101));
            assertTrue(SayakaImbalance.isBursting(target, 101 + SayakaImbalance.BURST_TICKS - 1));
            assertFalse(SayakaImbalance.isBursting(target, 101 + SayakaImbalance.BURST_TICKS));

            SayakaImbalance.View view = SayakaImbalance.snapshot().stream()
                    .filter(v -> v.target().equals(target)).findFirst().orElseThrow();
            assertEquals(owner, view.burstOwner());
            assertFalse(view.startProcessed());
            assertEquals(101, view.burstStartTick());
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** 一次超量叠层也会正确封顶触发（10 + 10 + 30 的第三下；前两下不足 30 不得触发）。 */
    @Test
    public void overfillStillTriggersOnce() {
        UUID target = id();
        UUID owner = id();
        try {
            assertFalse(SayakaImbalance.add(target, 10, 0, owner).triggered());
            assertFalse(SayakaImbalance.add(target, 10, 1, owner).triggered());
            SayakaImbalance.AddResult burst = SayakaImbalance.add(target, 30, 2, owner);
            assertTrue(burst.triggered());
            assertFalse(SayakaImbalance.isBursting(target, 2 + SayakaImbalance.BURST_TICKS));
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** ★ 爆发期间叠层被忽略（不再积累）。 */
    @Test
    public void accumulationIsBlockedDuringBurst() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, 30, 0, owner);      //触发
            SayakaImbalance.AddResult blocked = SayakaImbalance.add(target, 10, 50, owner);
            assertTrue("爆发期间必须被拦下", blocked.blockedDuringBurst());
            assertFalse(blocked.triggered());
            assertEquals("爆发期间不得积累", 0, SayakaImbalance.amountOf(target));
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** ★ 爆发到期后从 0 重新积累，并能再次触发。 */
    @Test
    public void accumulationResumesAfterBurstEnds() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, 30, 0, owner);
            int afterBurst = SayakaImbalance.BURST_TICKS + 10;
            SayakaImbalance.AddResult again = SayakaImbalance.add(target, 12, afterBurst, owner);
            assertFalse(again.blockedDuringBurst());
            assertEquals(12, SayakaImbalance.amountOf(target));
            SayakaImbalance.AddResult secondBurst = SayakaImbalance.add(target, 18, afterBurst + 1, owner);
            assertTrue(secondBurst.triggered());
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** 开场已处理标记 / 已结拍数：可写可读（幂等由被动侧先标记实现）。 */
    @Test
    public void startProcessedAndDrainMarksPersist() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, 30, 0, owner);
            SayakaImbalance.markStartProcessed(target);
            SayakaImbalance.markDrained(target);
            SayakaImbalance.markDrained(target);
            SayakaImbalance.View view = SayakaImbalance.snapshot().stream()
                    .filter(v -> v.target().equals(target)).findFirst().orElseThrow();
            assertTrue(view.startProcessed());
            assertEquals(2, view.drainedCount());
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** 纯函数：{@code triggersBurst}。 */
    @Test
    public void triggersBurstThreshold() {
        assertEquals(0, SayakaImbalance.triggersBurst(24, 5));
        assertEquals(1, SayakaImbalance.triggersBurst(25, 5));
        assertEquals(1, SayakaImbalance.triggersBurst(30, 1));
        assertEquals(0, SayakaImbalance.triggersBurst(0, 0));
    }

    /** 纯函数：每秒拍数（含跳刻补结、封顶 20 拍）。 */
    @Test
    public void drainTargetPacing() {
        assertEquals(0, SayakaImbalance.drainTargetFor(0, 0));
        assertEquals(0, SayakaImbalance.drainTargetFor(19, 0));
        assertEquals(1, SayakaImbalance.drainTargetFor(20, 0));
        assertEquals(1, SayakaImbalance.drainTargetFor(39, 0));
        assertEquals(2, SayakaImbalance.drainTargetFor(40, 0));
        assertEquals("跳刻也能一次补到位（目标数 > 已结数时调用方补结）",
                5, SayakaImbalance.drainTargetFor(100, 0));
        assertEquals("已结数更大时不得回退", 5, SayakaImbalance.drainTargetFor(80, 5));
        assertEquals(20, SayakaImbalance.drainTargetFor(SayakaImbalance.BURST_TICKS, 0));
        assertEquals("不得超结", 20, SayakaImbalance.drainTargetFor(100000, 0));
    }

    /** 清扫：离场目标被清、爆发中的条目不被清、收尾后的空条目被清。 */
    @Test
    public void sweepRules() {
        UUID goneTarget = id();
        UUID burstingTarget = id();
        UUID finishedTarget = id();
        UUID owner = id();
        try {
            //① 离场目标
            SayakaImbalance.add(goneTarget, 10, 0, owner);
            SayakaImbalance.sweep(1, target -> target.equals(goneTarget));
            assertFalse("离场目标必须被清", SayakaImbalance.snapshot().stream()
                    .anyMatch(v -> v.target().equals(goneTarget)));

            //② 爆发中的条目：既不能因"空层数"被清，也不因离场检查缺席被清
            SayakaImbalance.add(burstingTarget, 30, 0, owner);
            SayakaImbalance.sweep(1, null);
            assertTrue("爆发中的条目必须留存", SayakaImbalance.snapshot().stream()
                    .anyMatch(v -> v.target().equals(burstingTarget)));

            //③ 收尾（层数 0、不在爆发中）后：被清
            SayakaImbalance.sweep(SayakaImbalance.BURST_TICKS + 1, null);
            assertFalse("收尾后的空条目必须被清", SayakaImbalance.snapshot().stream()
                    .anyMatch(v -> v.target().equals(burstingTarget)));

            //④ 还有层数的条目：不得被清
            SayakaImbalance.add(finishedTarget, 5, 0, owner);
            SayakaImbalance.sweep(1, null);
            assertTrue("仍有层数的条目必须留存", SayakaImbalance.snapshot().stream()
                    .anyMatch(v -> v.target().equals(finishedTarget)));
        } finally {
            SayakaImbalance.clear(goneTarget);
            SayakaImbalance.clear(burstingTarget);
            SayakaImbalance.clear(finishedTarget);
        }
    }

    // ───────── 2026-10-18 需求 3：敌人死亡 ⇒ 失衡结束 + CD 刷新 ─────────

    /**
     * **死亡即结束**：{@link SayakaImbalance#endBurst} 把层数、爆发窗口、拍数计时、触发者全部清零。
     *
     * <p>"CD"在这条账本里就是 {@code burstUntilTick}（爆发期间不再积累的锁定）+
     * {@code drainedCount}（每秒结算进度）。清零后同一个 UUID 再被叠层，等同全新目标。
     */
    @Test
    public void endBurstResetsLayersAndCooldown() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, SayakaImbalance.MAX, 0, owner);   //积满触发
            assertTrue("触发后处于爆发中", SayakaImbalance.isBursting(target, 1));
            assertEquals("爆发时层数归零", 0, SayakaImbalance.amountOf(target));

            SayakaImbalance.endBurst(target);
            assertFalse("爆发窗口被清零（CD 刷新）", SayakaImbalance.isBursting(target, 1));
            assertEquals("层数仍为 0", 0, SayakaImbalance.amountOf(target));

            SayakaImbalance.AddResult again = SayakaImbalance.add(target, 6, 2, owner);
            assertFalse("复活 / 结束后不再被'爆发期间不积累'挡住", again.blockedDuringBurst());
            assertEquals(6, again.after());
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** {@code clear}（死亡 / 离线的实际调用点）也必须先把 CD 清零再移除。 */
    @Test
    public void clearAlsoEndsBurstFirst() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, SayakaImbalance.MAX, 10, owner);
            assertTrue(SayakaImbalance.isBursting(target, 11));
            SayakaImbalance.clear(target);
            assertFalse("清掉后不再爆发", SayakaImbalance.isBursting(target, 11));
            assertEquals("清掉后层数为 0", 0, SayakaImbalance.amountOf(target));
            assertFalse("条目已移除", SayakaImbalance.snapshot().stream()
                    .anyMatch(v -> v.target().equals(target)));
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    // ───────── 2026-10-18 需求 4：闲置 10 秒后每 0.3 秒消退 1 层 ─────────

    /**
     * **消退时间线**（纯函数）：10 秒保护期 → 每 6 刻退 1 层 → 退不穿地板。
     *
     * <p>{@code lastStackTick = 0} 时：199 刻不退、200 刻退第 1 层、205 刻仍 1 层、
     * 206 刻（10.3 秒）退第 2 层。
     */
    @Test
    public void decayTimelineIsTenSecondsThenEverySixTicks() {
        assertEquals("9.95 秒：一层都不退", 10, SayakaImbalance.decayedAmount(10, 199, 0));
        assertEquals("整 10 秒：退第 1 层", 9, SayakaImbalance.decayedAmount(10, 200, 0));
        assertEquals("10.25 秒：还没到下一拍", 9, SayakaImbalance.decayedAmount(10, 205, 0));
        assertEquals("10.3 秒：退第 2 层", 8, SayakaImbalance.decayedAmount(10, 206, 0));
        assertEquals("20 层刚好在 10 秒 + 19×0.3 秒时归零", 0,
                SayakaImbalance.decayedAmount(20, 200 + 19 * 6, 0));
        assertEquals("退不穿地板", 0, SayakaImbalance.decayedAmount(3, 99999, 0));
        assertEquals("0 层恒 0", 0, SayakaImbalance.decayedAmount(0, 99999, 0));
        assertEquals("刻被重置（闲置为负）按\"刚叠过\"处理", 5, SayakaImbalance.decayedAmount(5, 3, 100));
        assertEquals("每层 0.3 秒的常量口径", 6, SayakaImbalance.DECAY_INTERVAL_TICKS);
        assertEquals("10 秒的常量口径", 200, SayakaImbalance.DECAY_IDLE_TICKS);
    }

    /** **账本级消退**：闲置到点才退；任何一次新叠层把 10 秒计时重新拉满。 */
    @Test
    public void decayStartsAfterIdleAndResetsOnNewStack() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, 10, 100, owner);

            assertEquals("距最后叠层 199 刻：无消退", 0, SayakaImbalance.decay(299).size());
            assertEquals(10, SayakaImbalance.amountOf(target));

            List<SayakaImbalance.DecayEvent> first = SayakaImbalance.decay(300);   //idle = 200
            assertEquals("整 10 秒：恰好退 1 层", 1, first.size());
            assertEquals(target, first.get(0).target());
            assertEquals(10, first.get(0).before());
            assertEquals(9, first.get(0).after());
            assertEquals(9, SayakaImbalance.amountOf(target));

            SayakaImbalance.add(target, 5, 301, owner);    //新叠层 ⇒ 计时重置（9 → 14）
            assertEquals(14, SayakaImbalance.amountOf(target));
            assertEquals("新叠层后 10 秒内不得消退", 0, SayakaImbalance.decay(500).size());
            assertEquals(14, SayakaImbalance.amountOf(target));

            assertEquals("再过 10 秒（501 刻起）继续消退", 1, SayakaImbalance.decay(501).size());
            assertEquals(13, SayakaImbalance.amountOf(target));
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** 退到 0 后交给 {@code sweep} 的"空条目"分支清掉（不留垃圾账目）。 */
    @Test
    public void decayedToZeroIsSweptAway() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, 2, 0, owner);
            List<SayakaImbalance.DecayEvent> events = SayakaImbalance.decay(200 + 2 * 6);
            assertEquals(1, events.size());
            assertEquals("两层一次退净", 0, SayakaImbalance.amountOf(target));

            SayakaImbalance.sweep(200 + 2 * 6, null);
            assertFalse("层数归零的条目必须被清掉", SayakaImbalance.snapshot().stream()
                    .anyMatch(v -> v.target().equals(target)));
        } finally {
            SayakaImbalance.clear(target);
        }
    }

    /** 爆发中的条目不参与消退（层数此时本就为 0，且"爆发期间不再积累"）。 */
    @Test
    public void burstingEntriesDoNotDecay() {
        UUID target = id();
        UUID owner = id();
        try {
            SayakaImbalance.add(target, SayakaImbalance.MAX, 0, owner);   //触发爆发
            assertTrue(SayakaImbalance.isBursting(target, 200));
            assertEquals("爆发中没有层可退，也不产生消退事件", 0, SayakaImbalance.decay(200).size());
            assertEquals(0, SayakaImbalance.amountOf(target));
        } finally {
            SayakaImbalance.clear(target);
        }
    }
}
