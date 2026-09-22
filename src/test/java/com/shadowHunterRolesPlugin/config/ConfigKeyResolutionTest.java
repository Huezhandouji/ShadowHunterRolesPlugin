package com.shadowHunterRolesPlugin.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * T-6（t50 §4）：锁住**配置解析与回落**（t50 §1 第 ⑫ 条的纯半边）= `ConfigKey.resolve(Object)` 的安全口径：
 * **永不抛异常**；缺失 / 类型不符 / 不可解析 ⇒ 默认值；数值越界 ⇒ 夹取。
 * <p>为什么能单测：`resolve` 只吃一个 `Object`（读盘原始值）、只吐一个不可变 `Resolution` ⇒
 * 不读文件、不碰 `Plugin`/`YamlConfiguration` ⇒ 离线可跑。运行侧只剩"改盘上文件 ≤5 s 生效"（t31 已做同轮正对照）。
 * <p>本测试**不读盘**（冻结件 §3.2：第一批不新增 `src/test/resources`）。
 */
public class ConfigKeyResolutionTest {

    /** 与真实配置键同形：`command-permission-level` = 整数 3、范围 0..4。 */
    private static ConfigKey<Integer> levelKey() {
        return ConfigKey.integer("command-permission-level", 3, 0, 4, "命令权限等级");
    }

    /** 正常值：原值透传（既不回落也不夹取）。 */
    @Test
    public void valueInsideRangePassesThrough() {
        ConfigKey.Resolution<Integer> r = levelKey().resolve(2);
        assertEquals(Integer.valueOf(2), r.getValue());
        assertFalse("范围内不得标成回落", r.isDefaultValueUsed());
        assertFalse("范围内不得标成夹取", r.isClamped());
        assertSame("raw 必须是读盘原值本身", Integer.valueOf(2), r.getRaw());
    }

    /** 越界**夹取**（上界 / 下界各一次），且**不被标成回落**（两者是不同语义）。 */
    @Test
    public void outOfRangeIsClampedNotFallenBack() {
        ConfigKey.Resolution<Integer> high = levelKey().resolve(9);
        assertEquals(Integer.valueOf(4), high.getValue());
        assertTrue(high.isClamped());
        assertFalse(high.isDefaultValueUsed());
        ConfigKey.Resolution<Integer> low = levelKey().resolve(-5);
        assertEquals(Integer.valueOf(0), low.getValue());
        assertTrue(low.isClamped());
        assertFalse(low.isDefaultValueUsed());
    }

    /** 类型不符 / 缺失 / 不可解析 ⇒ 默认值（**永不抛**）。 */
    @Test
    public void badOrMissingValuesFallBackToDefault() {
        for (Object raw : new Object[]{null, "3", "abc", Boolean.TRUE, java.util.List.of(1), 3.5d}) {
            ConfigKey.Resolution<Integer> r = levelKey().resolve(raw);
            if (raw instanceof Number) {
                // 只有 Number 才算整数（3.5d 会被 Number 分支接住 ⇒ 夹取到 4）
                continue;
            }
            assertEquals("raw=" + raw + " 必须回落默认值", Integer.valueOf(3), r.getValue());
            assertTrue("raw=" + raw + " 必须标成回落", r.isDefaultValueUsed());
            assertFalse("回落不是夹取", r.isClamped());
        }
        assertEquals("带引号的数字不算整数（与既有 getInt 语义不同，属冻结口径）",
                Integer.valueOf(3), levelKey().resolve("3").getValue());
    }

    /** 非整数 Number（double）走 Number 分支 ⇒ 截断后夹取（如实记录这条口径）。 */
    @Test
    public void nonIntegralNumberIsTruncatedThenClamped() {
        ConfigKey.Resolution<Integer> r = levelKey().resolve(3.5d);
        assertEquals(Integer.valueOf(3), r.getValue());
        assertTrue("3.5 → 3 后仍在范围内 ⇒ 夹取标志应为 false（intValue 截断发生在夹取之前）",
                !r.isClamped());
    }

    /** 文本键：String 透传；其它类型（含缺失）回落默认值。 */
    @Test
    public void textKeyPassesStringsAndFallsBackOtherwise() {
        ConfigKey<String> key = ConfigKey.text("mode", "auto", "模式");
        assertEquals("manual", key.resolve("manual").getValue());
        assertFalse(key.resolve("manual").isDefaultValueUsed());
        assertEquals("auto", key.resolve(7).getValue());
        assertTrue(key.resolve(7).isDefaultValueUsed());
        assertEquals("auto", key.resolve(null).getValue());
        assertTrue(key.resolve(null).isDefaultValueUsed());
    }

    /** 布尔键：Boolean 透传（含 false！不得把 false 当"缺失"处理）；其它回落默认值。 */
    @Test
    public void booleanKeyPassesBooleansAndFallsBackOtherwise() {
        ConfigKey<Boolean> key = ConfigKey.bool("feature", true, "开关");
        assertEquals(Boolean.FALSE, key.resolve(false).getValue());
        assertFalse("false 是合法值，不是缺失", key.resolve(false).isDefaultValueUsed());
        assertEquals(Boolean.TRUE, key.resolve("true").getValue());
        assertTrue("字符串不得当成布尔（与 YAML 口径一致）", key.resolve("true").isDefaultValueUsed());
        assertEquals(Boolean.TRUE, key.resolve(null).getValue());
        assertTrue(key.resolve(null).isDefaultValueUsed());
    }

    /** 声明期校验（fail-fast）：路径空 / 默认值越界 / min>max 都必须在**建键时**就抛。 */
    @Test
    public void declarationTimeValidationIsFailFast() {
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.integer("", 1, 0, 4, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.integer("k", 9, 0, 4, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.integer("k", 1, 5, 4, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.text("k", null, "d"));
        assertThrows(IllegalArgumentException.class, () -> ConfigKey.bool(" ", true, "d"));
    }
}
