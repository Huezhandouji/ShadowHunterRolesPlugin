package com.shadowHunterRolesPlugin.core.util;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link TextUtil.Style} 的离线穷举单测（纯数据声明 ⇒ 不碰 Bukkit）。
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li>编号表 {@code 0..35} 完整（36 项，逐一对得上数据包 V26.2 的样式）；</li>
 *   <li>{@link TextUtil.Style#byDatapackNumber(int)} 的越界回退（负 / 超界 ⇒ 默认样式）；</li>
 *   <li>每个枚举常量的 in/out 维度都非 null（穷举，防止漏填）。</li>
 * </ol>
 */
public class TextStyleTest {

    @Test
    public void numberedTableHas36Entries() {
        assertEquals(36, TextUtil.Style.numberedCount());
    }

    @Test
    public void datapackNumberRoundTrips() {
        // 0..35 都必须能查到"自己"，且回退到同一常数
        for (int n = 0; n < 36; n++) {
            TextUtil.Style style = TextUtil.Style.byDatapackNumber(n);
            assertNotNull("style " + n + " 不应为 null", style);
        }
    }

    @Test
    public void datapackNumberOutOfRangeFallsBackToDefault() {
        assertEquals(TextUtil.Style.POP_SCALE, TextUtil.Style.byDatapackNumber(-1));
        assertEquals(TextUtil.Style.POP_SCALE, TextUtil.Style.byDatapackNumber(36));
        assertEquals(TextUtil.Style.POP_SCALE, TextUtil.Style.byDatapackNumber(Integer.MAX_VALUE));
        assertEquals(TextUtil.Style.POP_SCALE, TextUtil.Style.byDatapackNumber(Integer.MIN_VALUE));
    }

    @Test
    public void everyStyleHasNonNullInAndOut() {
        for (TextUtil.Style style : TextUtil.Style.values()) {
            assertNotNull(style.name() + " 缺 in", style.in());
            assertNotNull(style.name() + " 缺 out", style.out());
        }
    }

    @Test
    public void defaultStyleIsPopScale() {
        // 数据包对非法 style 停在"默认值"，本实现把默认值定为 POP_SCALE（对应 style=0）
        assertEquals(TextUtil.Style.POP_SCALE, TextUtil.Style.byDatapackNumber(0));
    }

    @Test
    public void centerStaggeredFamilyIsCoherent() {
        // 中心扩散一族（28..35 里的 center 字样）必须 centerStaggered
        assertTrue(TextUtil.Style.CENTER_IN_CENTER_OUT.centerStaggered());
        assertTrue(TextUtil.Style.CENTER_FADE_IN_CENTER_OUT.centerStaggered());
        assertTrue(TextUtil.Style.CENTER_FADE_IN_CENTER_FADE_OUT.centerStaggered());
        assertFalse(TextUtil.Style.POP_SCALE.centerStaggered());
        assertFalse(TextUtil.Style.DROP_DOWN_IN.centerStaggered());
    }

    @Test
    public void randomStaggeredFamilyIsCoherent() {
        assertTrue(TextUtil.Style.RANDOM_SCALE_IN.randomStaggered());
        assertTrue(TextUtil.Style.RANDOM_FADE_IN_RANDOM_OUT.randomStaggered());
        assertFalse(TextUtil.Style.POP_SCALE.randomStaggered());
        assertFalse(TextUtil.Style.CENTER_IN_CENTER_OUT.randomStaggered());
    }
}
