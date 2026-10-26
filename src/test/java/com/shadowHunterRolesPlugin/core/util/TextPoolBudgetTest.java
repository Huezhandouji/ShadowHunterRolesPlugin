package com.shadowHunterRolesPlugin.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 悬浮文本池的**节流 / 并发预算**契约（纯常量 ⇒ 不碰 Bukkit）。
 *
 * <p>为什么值得单测：这三个阈值全是**无报错**的调参 ——
 * 改错了只会"字出得太少 / 太多 / 糊屏"，运行期看不出来。
 * 本类把当前口径钉死，将来谁再动就会立刻红。
 *
 * <p>需求来源："缩短发送文本间隔，并且文本可以同时存在最多 5 条。"
 */
public class TextPoolBudgetTest {

    /** 并发上限：需求要求"最多同时 5 条"。 */
    @Test
    public void concurrentSegmentCapIsFive() {
        assertEquals("同时最多 5 段", 5, TextUtil.Pool.MAX_SEGMENTS);
        assertEquals("对外读口应与池常量一致",
                TextUtil.Pool.MAX_SEGMENTS, TextUtil.maxConcurrentSegments());
    }

    /** 全局最小间隔：应比旧的 12 刻更短（更密）。 */
    @Test
    public void globalGapIsShorterThanLegacyTwelve() {
        assertTrue("全局最小间隔应小于旧的 12 刻",
                TextUtil.Pool.MIN_GAP_TICKS < 12);
        assertTrue("但必须 > 0（否则退化为每刻无限刷）",
                TextUtil.Pool.MIN_GAP_TICKS > 0);
    }

    /** 连排句间隔：应比旧的 6 刻更短（更密）。 */
    @Test
    public void sequenceGapIsShorterThanLegacySix() {
        assertTrue("连排句间隔应小于旧的 6 刻",
                TextUtil.SEQUENCE_GAP_TICKS < 6);
        assertTrue("但必须 > 0", TextUtil.SEQUENCE_GAP_TICKS > 0);
    }

    /** 三层闸门必须真存在（段数上限、字数上限、间隔上限）。 */
    @Test
    public void allThreeGatesRemainInPlace() {
        assertTrue("段数闸门 > 0", TextUtil.Pool.MAX_SEGMENTS > 0);
        assertTrue("单段字数闸门 > 0", TextUtil.Pool.MAX_CHARS_PER_SEGMENT > 0);
        assertEquals("单段字数应与生成器口径一致",
                TextUtil.MAX_CHARS, TextUtil.Pool.MAX_CHARS_PER_SEGMENT);
        assertTrue("全局间隔闸门 > 0", TextUtil.Pool.MIN_GAP_TICKS > 0);
    }

    /** 并发上限必须容得下一次连排（否则排了 N 句只能看到 1~2 句）。 */
    @Test
    public void capAllowsASequenceToBeVisible() {
        assertTrue("段数上限应 ≥ 3（一次连排至少能看到几句）",
                TextUtil.Pool.MAX_SEGMENTS >= 3);
    }
}
