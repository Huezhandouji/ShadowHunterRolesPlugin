package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * **名下血量 + SAN/TE 显示的纯逻辑闸门**（2026-10-31 需求：增加名下血量显示和 SAN/TE 显示；
 * 猎影人紫色 SAN、影都亮黄色 TE）。
 *
 * <p>离线单测覆盖"文字格式 / 阵营标签与颜色 / 血量取整夹取 / 高度偏移"；
 * 实体生成与跟随需要游戏内环境，由装配接线测试（{@code registry/NameplateDisplayWiringTest}）
 * 与游戏内实测覆盖。
 */
public class NameplateVitalsDisplayComponentTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** 属性名与颜色：猎影人 SAN 紫、影都 TE 亮黄。 */
    @Test
    public void attributeLabelAndColorFollowFaction() {
        assertEquals("猎影人的特殊值叫 SAN", "SAN", NameplateVitalsDisplayComponent.attributeLabel(Faction.HUNTER));
        assertEquals("影都的特殊值叫 TE", "TE", NameplateVitalsDisplayComponent.attributeLabel(Faction.SHADOW));
        assertEquals("未知阵营按 TE", "TE", NameplateVitalsDisplayComponent.attributeLabel(Faction.UNKNOWN));
        assertEquals("未知阵营按影都色", "TE",
                NameplateVitalsDisplayComponent.attributeLabel(null));

        assertEquals("猎影人 SAN = 紫色",
                NameplateVitalsDisplayComponent.HUNTER_ATTRIBUTE_COLOR,
                NameplateVitalsDisplayComponent.attributeColor(Faction.HUNTER));
        assertEquals("影都 TE = 亮黄色",
                NamedTextColor.YELLOW,
                NameplateVitalsDisplayComponent.attributeColor(Faction.SHADOW));
        assertNotEquals("两个阵营不能同色",
                NameplateVitalsDisplayComponent.attributeColor(Faction.HUNTER),
                NameplateVitalsDisplayComponent.attributeColor(Faction.SHADOW));
    }

    /** 文字形态：❤ 血量/上限 + 空格 + SAN/TE 当前/上限。 */
    @Test
    public void textShowsHealthAndFactionAttribute() {
        String hunter = plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.HUNTER, 24.0d, 40, 72, 100));
        assertTrue("缺血量段：" + hunter, hunter.contains("❤ 24/40"));
        assertTrue("猎影人缺 SAN 段：" + hunter, hunter.contains("SAN 72/100"));
        assertFalse("猎影人不应出现 TE：" + hunter, hunter.contains("TE "));

        String shadow = plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.SHADOW, 24.0d, 40, 72, 100));
        assertTrue("缺血量段：" + shadow, shadow.contains("❤ 24/40"));
        assertTrue("影都缺 TE 段：" + shadow, shadow.contains("TE 72/100"));
        assertFalse("影都不应出现 SAN：" + shadow, shadow.contains("SAN "));

        String unknown = plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.UNKNOWN, 10.0d, 20, 5, 100));
        assertTrue("未知阵营按 TE 显示：" + unknown, unknown.contains("TE 5/100"));
    }

    /** 血量取整（向上）与夹取（不显示负数 / 不超过上限 / 上限非法兜底 20）。 */
    @Test
    public void healthIsRoundedUpAndClamped() {
        assertTrue("0.5 血向上取整为 1", plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.HUNTER, 0.5d, 40, 10, 100)).contains("❤ 1/40"));
        assertTrue("超上限夹到上限", plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.HUNTER, 41.9d, 40, 10, 100)).contains("❤ 40/40"));
        assertTrue("负数血夹到 0", plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.HUNTER, -5d, 40, 10, 100)).contains("❤ 0/40"));
        assertTrue("上限非法时兜底 20", plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.SHADOW, 10d, 0, 10, 100)).contains("❤ 10/20"));
    }

    /** 没有特殊值（上限 ≤0）时只显示血量，不出现 SAN/TE 段。 */
    @Test
    public void missingSanteHidesAttributeSegment() {
        String text = plain(NameplateVitalsDisplayComponent.nameplateText(
                Faction.HUNTER, 18d, 40, 0, 0));
        assertTrue("血量段仍要显示：" + text, text.contains("❤ 18/40"));
        assertFalse("没有特殊值就不显示 SAN/TE：" + text, text.contains("SAN"));
        assertFalse("没有特殊值就不显示 SAN/TE：" + text, text.contains("TE"));
    }

    /** 高度偏移：文字在"身高 + NAME_GAP（0.5）"处 ⇒ 抬高到 0.5 格，避免与原版名字重叠。 */
    @Test
    public void displayHeightSitsAboveHead() {
        assertEquals("站立 1.8 高 ⇒ 2.3（头顶之上 0.5 格）", 2.3d,
                NameplateVitalsDisplayComponent.displayY(1.8d), 1e-9);
        assertEquals(1.5d + NameplateVitalsDisplayComponent.NAME_GAP,
                NameplateVitalsDisplayComponent.displayY(1.5d), 1e-9);
        assertEquals("需求口径：头顶之上 0.5 格", 0.5d,
                NameplateVitalsDisplayComponent.NAME_GAP, 1e-9);
        assertTrue("可见范围倍率必须为正", NameplateVitalsDisplayComponent.VIEW_RANGE > 0f);
    }
}
