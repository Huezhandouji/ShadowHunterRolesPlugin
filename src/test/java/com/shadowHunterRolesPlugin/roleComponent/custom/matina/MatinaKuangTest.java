package com.shadowHunterRolesPlugin.roleComponent.custom.matina;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaKuangPassive;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 「狂躁牧师·马提娜」被动 {@link MatinaKuangPassive}（狂暴值 KUANG）的**离线**单测。
 *
 * <h2>判据来源</h2>
 * 需求「狂暴指数」那张表（1 回血 8 / 4 回能 1 / 7 回 san 5 / 10 免疫缓慢 / 15 真伤 6【不与 20 层叠加】/
 * 20 真伤 8 / 30 抗性 2 / 每秒 −1 层 / &gt;60 每秒死亡判定 / 每层一个环绕粒子）。
 *
 * <h2>为什么能离线跑</h2>
 * 与 {@code CapabilityDispatchTest} 同一做法：组件的构造器**只把 id / 服务集 / 描述符存起来**、
 * 不做任何副作用 ⇒ 用一个**空的 {@code ComponentServicesPort} 桩**即可构造。
 * 本类只覆盖**不需要玩家实例**的那部分：层数读写、阈值判定、真伤取值、操作面、lore 文案、
 * actionbar HUD 的**画法**（纯函数）。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li><b>不</b>覆盖：第 1 层的"攻击回复自己 8 点生命"实际结算（{@code healSelf} 要真实 {@code Player}
 *       ⇒ 属运行级）、每秒衰减节拍与暴走**掷骰**结果（{@code update()} 要有玩家）、环绕粒子、
 *       actionbar 的**发送**本身（{@code Player#sendActionBar}）—— 这些都在组件里但需要 Bukkit 运行环境。</li>
 *   <li><b>不</b>覆盖：与其它组件的装配依赖是否满足（归 {@code Role#verifyDependencies()} 那一层）。</li>
 * </ul>
 */
public class MatinaKuangTest {

    /** 空服务集桩（构造期只存字段 ⇒ 不需要真服务）。 */
    private static ComponentServicesPort inertServices() {
        return new ComponentServicesPort(null, null, null);
    }

    /** 一个全新的被动实例（层数 0）。 */
    private static MatinaKuangPassive fresh() {
        return new MatinaKuangPassive(MatinaKuangPassive.ID, inertServices(),
                new MatinaKuangPassive.Specification());
    }

    /** 把层数直接摆到某个值（走公开写口）。 */
    private static MatinaKuangPassive at(int layers) {
        MatinaKuangPassive passive = fresh();
        passive.setKuang(layers);
        return passive;
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    // ───────── ① 层数读写 ─────────

    @Test
    public void startsAtZero() {
        assertEquals(0, fresh().kuang());
    }

    @Test
    public void addAccumulatesAndReportsAccepted() {
        MatinaKuangPassive passive = fresh();
        assertTrue(passive.addKuang());
        assertEquals(1, passive.kuang());
        assertTrue(passive.addKuang(6));
        assertEquals(7, passive.kuang());
    }

    @Test
    public void addIsRejectedOnceAtCapAndNeverOverflows() {
        MatinaKuangPassive passive = at(MatinaKuangPassive.KUANG_MAX);
        assertFalse("满层必须拒绝再增加", passive.addKuang());
        assertEquals(MatinaKuangPassive.KUANG_MAX, passive.kuang());
    }

    @Test
    public void addRejectsNonPositiveAmounts() {
        MatinaKuangPassive passive = at(5);
        assertFalse(passive.addKuang(0));
        assertFalse(passive.addKuang(-3));
        assertEquals(5, passive.kuang());
    }

    @Test
    public void setClampsBothEnds() {
        MatinaKuangPassive passive = fresh();
        passive.setKuang(-10);
        assertEquals(0, passive.kuang());
        passive.setKuang(MatinaKuangPassive.KUANG_MAX + 500);
        assertEquals(MatinaKuangPassive.KUANG_MAX, passive.kuang());
    }

    @Test
    public void clearResetsToZero() {
        MatinaKuangPassive passive = at(77);
        passive.clearKuang();
        assertEquals(0, passive.kuang());
    }

    // ───────── ② 真伤：15 层与 20 层**不叠加**（需求明写） ─────────

    @Test
    public void trueDamageIsOffBelowFifteen() {
        assertEquals(0d, at(14).attackTrueDamage(), 0d);
        assertEquals(0d, at(0).attackTrueDamage(), 0d);
    }

    @Test
    public void trueDamageIsSixFromFifteenToNineteen() {
        assertEquals(6d, at(15).attackTrueDamage(), 0d);
        assertEquals(6d, at(19).attackTrueDamage(), 0d);
    }

    @Test
    public void trueDamageIsEightFromTwentyUpAndDoesNotStack() {
        assertEquals(8d, at(20).attackTrueDamage(), 0d);
        assertEquals("20 层取代 15 层，而不是 6+8", 8d, at(99).attackTrueDamage(), 0d);
    }

    // ───────── ③ 阈值判定（1 / 10 / 30 / >60） ─────────

    @Test
    public void selfHealThresholdIsOne() {
        assertFalse(at(0).hasSelfHeal());
        assertTrue(at(MatinaKuangPassive.LAYER_SELF_HEAL).hasSelfHeal());
    }

    @Test
    public void slowImmunityThresholdIsTen() {
        assertFalse(at(9).hasSlowImmunity());
        assertTrue(at(MatinaKuangPassive.LAYER_SLOW_IMMUNE).hasSlowImmunity());
    }

    @Test
    public void resistanceThresholdIsThirty() {
        assertFalse(at(29).hasResistance());
        assertTrue(at(MatinaKuangPassive.LAYER_RESISTANCE).hasResistance());
    }

    @Test
    public void overloadFlagFlipsAboveSixty() {
        assertFalse("60 层本身不进入暴走判定", at(MatinaKuangPassive.OVERLOAD_THRESHOLD).overloaded());
        assertTrue(at(MatinaKuangPassive.OVERLOAD_THRESHOLD + 1).overloaded());
    }

    // ───────── ④ 操作面（字符串指令薄适配） ─────────

    @Test
    public void operationReadsValue() {
        assertEquals("37", at(37).onOperationCommand("value"));
        assertNull("value 不接受参数", at(37).onOperationCommand("value 1"));
    }

    @Test
    public void operationTagsListTheActiveThresholdsInOrder() {
        assertEquals("none", at(0).onOperationCommand("tags"));
        assertEquals("heal8", at(1).onOperationCommand("tags"));
        assertEquals("heal8,energy1", at(4).onOperationCommand("tags"));
        assertEquals("heal8,energy1,sante5", at(7).onOperationCommand("tags"));
        assertEquals("heal8,energy1,sante5,slowImmune", at(10).onOperationCommand("tags"));
        assertEquals("heal8,energy1,sante5,slowImmune,trueDamage6", at(15).onOperationCommand("tags"));
        assertEquals("heal8,energy1,sante5,slowImmune,trueDamage8", at(20).onOperationCommand("tags"));
        assertEquals("heal8,energy1,sante5,slowImmune,trueDamage8,resistance2", at(30).onOperationCommand("tags"));
    }

    @Test
    public void operationWritesReturnTheValueAfterWriting() {
        MatinaKuangPassive passive = fresh();
        assertEquals("5", passive.onOperationCommand("add 5"));
        assertEquals("5", passive.onOperationCommand("value"));
        assertEquals("100", passive.onOperationCommand("set 1000"));
        assertEquals("0", passive.onOperationCommand("clear"));
        assertEquals("0", passive.onOperationCommand("value"));
    }

    @Test
    public void operationRejectsUnknownVerbsBadArityAndBadNumbers() {
        MatinaKuangPassive passive = at(3);
        assertNull(passive.onOperationCommand(null));
        assertNull(passive.onOperationCommand(""));
        assertNull(passive.onOperationCommand("nope"));
        assertNull("add 缺参数", passive.onOperationCommand("add"));
        assertNull("add 多参数", passive.onOperationCommand("add 1 2"));
        assertNull("负数被拒", passive.onOperationCommand("add -3"));
        assertNull("非数字被拒", passive.onOperationCommand("add abc"));
        assertNull(passive.onOperationCommand("clear 1"));
        assertEquals("拒绝的指令不得改动层数", 3, passive.kuang());
    }

    // ───────── ⑤ 玩家可见文案 ─────────

    @Test
    public void loreLineCarriesLayerCount() {
        assertEquals("狂暴：15 层", plain(MatinaKuangPassive.loreLineFor(15)));
    }

    @Test
    public void loreLineWarnsWhileRollingOverloadDeath() {
        String line = plain(MatinaKuangPassive.loreLineFor(61));
        assertNotNull(line);
        assertTrue("高于 60 层要出现暴走提示，实际 = " + line, line.contains("暴走判定中"));
        assertFalse("60 层本身不提示", plain(MatinaKuangPassive.loreLineFor(60)).contains("暴走判定中"));
    }

    // ───────── ⑥ actionbar HUD（2026-10-31 需求）─────────

    /** 60 层前每 5 层加一格 ⇒ 12 格 = 60 层满格。 */
    @Test
    public void barFilledGrowsEveryFiveLayers() {
        assertEquals(0, MatinaKuangPassive.barFilled(0));
        assertEquals(0, MatinaKuangPassive.barFilled(4));
        assertEquals(1, MatinaKuangPassive.barFilled(5));
        assertEquals(11, MatinaKuangPassive.barFilled(55));
        assertEquals(11, MatinaKuangPassive.barFilled(59));
        assertEquals("60 层 = 满格", 12, MatinaKuangPassive.barFilled(60));
        assertEquals("超过 60 层仍是满格，但显示进乱码区", 12, MatinaKuangPassive.barFilled(61));
        assertEquals(12, MatinaKuangPassive.barFilled(100));
        assertEquals("负值按 0 层算", 0, MatinaKuangPassive.barFilled(-5));
        assertEquals("12 格常量", 12, MatinaKuangPassive.BAR_SEGMENTS);
    }

    /** 正常区：{@code [############]}（12 格固定），红色填充数 = 已填充格数。 */
    @Test
    public void normalBarHasTwelveSlotsAndRedFilledSegments() {
        assertEquals(14, plain(MatinaKuangPassive.barComponent(0, 0)).length());
        assertEquals("[############]", plain(MatinaKuangPassive.barComponent(0, 0)));
        assertEquals("[############]", plain(MatinaKuangPassive.barComponent(60, 0)));
        assertEquals("[############]", plain(MatinaKuangPassive.barComponent(37, 3)));
        assertEquals("0 层：无红色格", 0, countSlots(MatinaKuangPassive.barComponent(0, 0), NamedTextColor.RED));
        assertEquals("5 层：1 格红", 1, countSlots(MatinaKuangPassive.barComponent(5, 0), NamedTextColor.RED));
        assertEquals("55 层：11 格红", 11, countSlots(MatinaKuangPassive.barComponent(55, 0), NamedTextColor.RED));
        assertEquals("60 层：12 格全红", 12, countSlots(MatinaKuangPassive.barComponent(60, 0), NamedTextColor.RED));
    }

    /** 60 层后：进度条变成红色乱码，且随帧变化（快速闪动）；60 层本身不进入乱码。 */
    @Test
    public void overloadAfterSixtyTurnsBarIntoFlashingRedGarble() {
        assertFalse("60 层本身不进乱码区", MatinaKuangPassive.isOverloadDisplay(60));
        assertTrue("61 层进乱码区", MatinaKuangPassive.isOverloadDisplay(61));

        Component garble = MatinaKuangPassive.barComponent(61, 0);
        String first = plain(garble);
        assertEquals("条框长度不变（12 格 + 两个括号）", 14, first.length());
        assertTrue(first.startsWith("["));
        assertTrue(first.endsWith("]"));
        assertFalse("乱码里不应再出现进度用的 '#'", first.contains("#"));
        assertEquals("12 格全是红色系", 12,
                countSlots(garble, NamedTextColor.RED) + countSlots(garble, NamedTextColor.DARK_RED));
        assertEquals("亮红 / 暗红各半", 6, countSlots(garble, NamedTextColor.RED));
        assertEquals(6, countSlots(garble, NamedTextColor.DARK_RED));

        String second = plain(MatinaKuangPassive.barComponent(61, 1));
        assertEquals("同一帧必须可复现", first, plain(MatinaKuangPassive.barComponent(61, 0)));
        assertFalse("相邻帧必须不同（快速闪动）", first.equals(second));
    }

    /** actionbar 整行：前缀 + 当前/上限值 + 进度条；60 层后追加「暴走」。 */
    @Test
    public void hudLineCarriesValueAndOverloadMarker() {
        String normal = plain(MatinaKuangPassive.hudComponent(37, 0));
        assertTrue("要带狂暴值，实际 = " + normal, normal.contains("狂暴 37/100"));
        assertTrue("要带进度条，实际 = " + normal, normal.contains("[############]"));
        assertFalse(normal.contains("暴走"));

        String overload = plain(MatinaKuangPassive.hudComponent(61, 0));
        assertTrue("要带狂暴值，实际 = " + overload, overload.contains("狂暴 61/100"));
        assertTrue("60 层后要带暴走标记，实际 = " + overload, overload.contains("暴走"));
    }

    /** 按叶子组件数颜色出现次数（每个槽 / 每个括号都是一个显式着色的叶子）。 */
    private static int countSlots(Component component, net.kyori.adventure.text.format.TextColor color) {
        if (component.children().isEmpty()) {
            return color.equals(component.color()) ? 1 : 0;
        }
        int count = 0;
        for (Component child : component.children()) {
            count += countSlots(child, color);
        }
        return count;
    }
}
