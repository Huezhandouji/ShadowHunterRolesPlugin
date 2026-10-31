package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * BOSS 条**增强视觉**的离线契约测试（2026-10-30 冷识首发；2026-10-31 全量推广）。
 *
 * <h2>为什么这套测试存在</h2>
 * 增强画法的几件事都是"玩家看得见、但服务端不报错"的：箭头有没有在流、TE 越低是不是越灰、
 * 标题属性色对不对、惩罚态有没有换成金色乱码、猎影人的金色箭头有没有随 SAN 变少。
 * 它们一旦写错，编译期与运行期都不会抛异常，只会"看起来不对"。因此这里把渲染抽成的纯函数逐帧钉住：
 * <ol>
 *   <li>影都标题逐字正确（只有箭头 + TE + 数值，没有分段格条）；</li>
 *   <li>影都箭头底色：满 TE = 亮紫（#ff55ff）→ TE 归零 = 深灰（#555555），中间线性单调；</li>
 *   <li>标题属性色：两个阵营都是亮黄（2026-10-31 起 SAN 也改亮黄）；</li>
 *   <li>影都箭头每帧流动；惩罚态 = 金黄乱码 + 移动的「TE」；</li>
 *   <li>猎影人：金色 {@code >>>>>} / {@code <<<<<} + 白色涌动、数量随 SAN 递减、SAN 后仍显示 ENERGY、
 *       SAN 归零不进入乱码态；</li>
 *   <li>BOSS 条进度 = TE / SAN 比例（含越界夹取）。</li>
 * </ol>
 */
public class BossbarEnhancedVisualTest {

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static int count(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    /** 组件树里是否存在指定颜色（Adventure 的样式可能挂在任意层）。 */
    private static boolean hasColor(Component component, TextColor expected) {
        if (expected.equals(component.color())) {
            return true;
        }
        for (Component child : component.children()) {
            if (hasColor(child, expected)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 增强画法**按阵营全量推广**（2026-10-31 用户需求）：影都与猎影人都吃增强画法，
     * 未识别阵营仍走旧画法（等于一次显式的推广决定）。
     */
    @Test
    public void enhancedVisualsCoverBothFactions() {
        assertTrue("影都必须启用增强视觉", BossbarRoleAttributesDisplayComponent.enhancedFor(Faction.SHADOW));
        assertTrue("猎影人必须启用增强视觉", BossbarRoleAttributesDisplayComponent.enhancedFor(Faction.HUNTER));
        assertFalse("未识别阵营仍走旧画法", BossbarRoleAttributesDisplayComponent.enhancedFor(Faction.UNKNOWN));
        assertFalse("null 阵营不得启用", BossbarRoleAttributesDisplayComponent.enhancedFor(null));
    }

    /** 正常态标题 = 两侧箭头 + 属性名 + 数值（没有分段格条）；SHADOW 的 TE 标题是亮黄。 */
    @Test
    public void normalTitleShowsArrowsAndTeValueOnly() {
        Component title = BossbarRoleAttributesDisplayComponent.enhancedTitle(74, 100, 0, Faction.SHADOW);
        String text = plain(title);

        //精确串：证明箭头与数值的**顺序/空格**也是对的（不是只有字符合集对）
        assertEquals("第 0 帧的完整标题必须逐字正确", ">>>>>> TE 74/100 <<<<<<", text);
        assertFalse("没有分段格条，不能出现方括号: " + text, text.contains("["));
        assertFalse("没有分段格条，不能出现实心格: " + text, text.contains("█"));
        assertFalse("没有分段格条，不能出现灰色空格: " + text, text.contains("░"));
        assertEquals("左右箭头各 6 个", 6, count(text, ">"));
        assertEquals("左右箭头各 6 个", 6, count(text, "<"));
        assertTrue("SHADOW 的 TE 标题必须是亮黄色", hasColor(title, NamedTextColor.YELLOW));
    }

    /** 核心口径：箭头底色满 TE 亮紫 → TE 归零深灰，中间线性单调（只影响标题，不影响条颜色）。 */
    @Test
    public void teArrowsFadeFromPurpleToGray() {
        assertEquals("满 TE 箭头 = 亮紫", "#FF55FF",
                BossbarRoleAttributesDisplayComponent.teArrowColor(1f).asHexString());
        assertEquals("TE 归零箭头 = 深灰", "#555555",
                BossbarRoleAttributesDisplayComponent.teArrowColor(0f).asHexString());

        int previousRed = -1;
        for (float teRatio : new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}) {
            int rgb = Integer.parseInt(
                    BossbarRoleAttributesDisplayComponent.teArrowColor(teRatio).asHexString().substring(1), 16);
            int red = (rgb >> 16) & 0xFF;
            int green = (rgb >> 8) & 0xFF;
            int blue = rgb & 0xFF;
            assertEquals("绿通道固定 85（只抬红/蓝）", 85, green);
            assertEquals("红蓝通道同步", red, blue);
            assertTrue("红通道必须随 TE 单调增（ratio=" + teRatio + "）", red > previousRed);
            previousRed = red;
        }
        //满 TE 的标题里能看到亮紫箭头；74/100 时箭头已偏灰、不再是纯亮紫
        assertTrue("满 TE 标题必须含亮紫箭头",
                hasColor(BossbarRoleAttributesDisplayComponent.enhancedTitle(100, 100, 0, Faction.SHADOW),
                        NamedTextColor.LIGHT_PURPLE));
        assertFalse("TE 降到 74/100 时箭头不该还是纯亮紫（应已开始变灰）",
                hasColor(BossbarRoleAttributesDisplayComponent.enhancedTitle(74, 100, 0, Faction.SHADOW),
                        NamedTextColor.LIGHT_PURPLE));
    }

    /** 标题属性色方案：两个阵营的属性标题色都是亮黄（2026-10-31 起 SAN 由亮紫改亮黄）。 */
    @Test
    public void titleAttributeColorsFollowFactionScheme() {
        assertEquals("SHADOW 的 TE 标题色 = 亮黄", "#FFFF55",
                BossbarRoleAttributesDisplayComponent.attributeTitleColor(Faction.SHADOW).asHexString());
        assertEquals("HUNTER 的 SAN 标题色 = 亮黄", "#FFFF55",
                BossbarRoleAttributesDisplayComponent.attributeTitleColor(Faction.HUNTER).asHexString());
        assertEquals("SHADOW 显示 TE", "TE", BossbarRoleAttributesDisplayComponent.attributeLabel(Faction.SHADOW));
        assertEquals("HUNTER 显示 SAN", "SAN", BossbarRoleAttributesDisplayComponent.attributeLabel(Faction.HUNTER));

        String shadow = plain(BossbarRoleAttributesDisplayComponent.enhancedTitle(50, 100, 0, Faction.SHADOW));
        assertTrue("SHADOW 标题写 TE: " + shadow, shadow.contains("TE 50/100"));
        assertTrue("SHADOW 的 TE 是亮黄",
                hasColor(BossbarRoleAttributesDisplayComponent.enhancedTitle(50, 100, 0, Faction.SHADOW),
                        NamedTextColor.YELLOW));

        Component hunterTitle = BossbarRoleAttributesDisplayComponent.hunterEnhancedTitle(50, 100, 40, 100, 0);
        assertTrue("HUNTER 标题写 SAN: " + plain(hunterTitle), plain(hunterTitle).contains("SAN 50/100"));
        assertTrue("HUNTER 的 SAN 是亮黄", hasColor(hunterTitle, NamedTextColor.YELLOW));
        assertTrue("HUNTER 标题保留能量值", plain(hunterTitle).contains("ENERGY 40/100"));
    }

    /** 猎影人箭头数量随 SAN 递减：满 = 5、归零 = 0，且单调不增（"SAN 越少金色 > 也越少"）。 */
    @Test
    public void hunterArrowCountShrinksWithSan() {
        assertEquals("满 SAN = 5 个箭头", 5, BossbarRoleAttributesDisplayComponent.hunterArrowCount(1f));
        assertEquals("约 74% = 4 个", 4, BossbarRoleAttributesDisplayComponent.hunterArrowCount(0.74f));
        assertEquals("50% = 3 个（四舍五入）", 3, BossbarRoleAttributesDisplayComponent.hunterArrowCount(0.5f));
        assertEquals("33% = 2 个", 2, BossbarRoleAttributesDisplayComponent.hunterArrowCount(0.33f));
        assertEquals("SAN 归零 = 0 个", 0, BossbarRoleAttributesDisplayComponent.hunterArrowCount(0f));
        int previous = Integer.MAX_VALUE;
        for (float ratio : new float[]{1f, 0.8f, 0.6f, 0.4f, 0.2f, 0f}) {
            int count = BossbarRoleAttributesDisplayComponent.hunterArrowCount(ratio);
            assertTrue("箭头数量必须随 SAN 单调不增（ratio=" + ratio + "）", count <= previous);
            previous = count;
        }
    }

    /** 猎影人标题：金色 >>>>> / <<<<< + 白色涌动 + 亮黄 SAN + 保留 ENERGY。 */
    @Test
    public void hunterTitleShowsGoldenArrowsSanAndEnergy() {
        Component title = BossbarRoleAttributesDisplayComponent.hunterEnhancedTitle(100, 100, 40, 100, 0);
        String text = plain(title);
        assertEquals("满 SAN 的完整标题必须逐字正确", ">>>>> SAN 100/100 || ENERGY 40/100 <<<<<", text);
        assertEquals("左侧金色箭头 5 个", 5, count(text, ">"));
        assertEquals("右侧金色箭头 5 个", 5, count(text, "<"));
        assertTrue("箭头底色必须是金色", hasColor(title, NamedTextColor.GOLD));
        assertTrue("必须有一格白色涌动高亮", hasColor(title, NamedTextColor.WHITE));
        assertTrue("SAN 标题必须是亮黄色", hasColor(title, NamedTextColor.YELLOW));
        assertTrue("SAN 之后必须保留能量值", text.contains("ENERGY 40/100"));
        assertTrue("能量值沿用绿色", hasColor(title, NamedTextColor.GREEN));

        String half = plain(BossbarRoleAttributesDisplayComponent.hunterEnhancedTitle(50, 100, 40, 100, 0));
        assertEquals("SAN 降到 50% 时箭头减到 3 个", 3, count(half, ">"));
        assertEquals("右侧同步减到 3 个", 3, count(half, "<"));
    }

    /** 猎影人：SAN 归零时仍显示数值与能量（不进入乱码惩罚态），且没有箭头。 */
    @Test
    public void hunterSanZeroKeepsNumbersAndHasNoArrows() {
        Component title = BossbarRoleAttributesDisplayComponent.hunterEnhancedTitle(0, 100, 40, 100, 0);
        String text = plain(title);
        assertEquals("SAN 归零 = 没有箭头", 0, count(text, ">"));
        assertEquals("SAN 归零 = 没有箭头", 0, count(text, "<"));
        assertTrue("仍显示 SAN 数值: " + text, text.contains("SAN 0/100"));
        assertTrue("仍显示能量值: " + text, text.contains("ENERGY 40/100"));
        assertFalse("猎影人不进入乱码态", containsGarble(text));
        assertFalse("不出现 TE 乱码单词", text.contains("TE "));
    }

    /** 猎影人箭头也在流动：纯文本不变、只有高亮颜色在动；箭头数量不随帧变化。 */
    @Test
    public void hunterArrowsFlowButKeepText() {
        Component frame0 = BossbarRoleAttributesDisplayComponent.hunterEnhancedTitle(74, 100, 40, 100, 0);
        Component frame1 = BossbarRoleAttributesDisplayComponent.hunterEnhancedTitle(74, 100, 40, 100, 1);
        assertNotEquals("相邻两帧必须不同（白色高亮在动）", frame0.toString(), frame1.toString());
        assertEquals("纯文本不变、只有颜色在动", plain(frame0), plain(frame1));
        assertEquals("箭头数量不随动画变化", 4, count(plain(frame0), ">"));
    }

    /** 流动箭头：每 0.2 秒换一帧 ⇒ 组件（颜色）变，但箭头本体与纯文本不变。 */
    @Test
    public void flowingArrowsAnimateEveryFrame() {
        Component frame0 = BossbarRoleAttributesDisplayComponent.enhancedTitle(74, 100, 0, Faction.SHADOW);
        Component frame1 = BossbarRoleAttributesDisplayComponent.enhancedTitle(74, 100, 1, Faction.SHADOW);
        assertNotEquals("相邻两帧的标题必须不同（高亮位置在动）", frame0.toString(), frame1.toString());
        assertEquals("箭头字符数不随动画变化", 6, count(plain(frame0), ">"));
        assertEquals("箭头字符数不随动画变化", 6, count(plain(frame0), "<"));
        assertEquals("纯文本不变、只有颜色在动", plain(frame0), plain(frame1));
    }

    /** 惩罚态：金黄乱码 + 会移动的「TE」，且不再显示 TE 数值。 */
    @Test
    public void punishmentTitleIsGoldenGarbleWithMovingTe() {
        Component frame0 = BossbarRoleAttributesDisplayComponent.enhancedTitle(0, 100, 0, Faction.SHADOW);
        Component frame1 = BossbarRoleAttributesDisplayComponent.enhancedTitle(0, 100, 1, Faction.SHADOW);
        String text0 = plain(frame0);

        assertTrue("惩罚态仍要有箭头", text0.contains(">>>>>>") && text0.contains("<<<<<<"));
        assertTrue("惩罚态必须出现 TE 单词: " + text0, text0.contains("TE"));
        assertTrue("惩罚态必须是金黄色", hasColor(frame0, NamedTextColor.GOLD));
        assertFalse("惩罚态不再显示 TE 数值: " + text0, text0.contains("0/100"));
        assertTrue("惩罚态必须有乱码字符", containsGarble(text0));
        assertNotEquals("乱码每帧都在变", frame0.toString(), frame1.toString());
        assertEquals("同一帧渲染必须稳定（确定性伪随机）",
                frame0.toString(),
                BossbarRoleAttributesDisplayComponent.enhancedTitle(0, 100, 0, Faction.SHADOW).toString());
    }

    private static boolean containsGarble(String text) {
        for (char glyph : BossbarRoleAttributesDisplayComponent.GARBLE_POOL.toCharArray()) {
            if (text.indexOf(glyph) >= 0) {
                return true;
            }
        }
        return false;
    }

    /** BOSS 条进度 = TE 比例（越少越短），并夹在 0~1。 */
    @Test
    public void bossbarProgressFollowsTeRatio() {
        assertEquals(1.0f, BossbarRoleAttributesDisplayComponent.ratio(100, 100), 0.0001f);
        assertEquals(0.5f, BossbarRoleAttributesDisplayComponent.ratio(50, 100), 0.0001f);
        assertEquals(0.0f, BossbarRoleAttributesDisplayComponent.ratio(0, 100), 0.0001f);
        assertEquals("负值夹到 0", 0.0f, BossbarRoleAttributesDisplayComponent.ratio(-5, 100), 0.0001f);
        assertEquals("超过上限夹到 1", 1.0f, BossbarRoleAttributesDisplayComponent.ratio(150, 100), 0.0001f);
        assertEquals("上限非法时回 0", 0.0f, BossbarRoleAttributesDisplayComponent.ratio(50, 0), 0.0001f);
    }
}
