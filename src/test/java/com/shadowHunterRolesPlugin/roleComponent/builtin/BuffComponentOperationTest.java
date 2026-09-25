package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.core.ports.Self;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * `BuffComponent` 的**组件操作面**离线单测（≥6 例）。
 *
 * <h2>为什么能离线跑：最小测试替身（两件）</h2>
 * ① 生产里 {@code BuffManager} 由容器以 {@code new BuffManager(player, this)} 构造 ⇒ **需要活 Player** ✗；
 * 但它的**构造器只把两个实参存进字段**（零解引用 ✓）⇒ 用 {@code super(null, null)} 构造的替身**不需要 Player** ✓。
 * 本类的 {@link StubBuffManager} 在此之上把**组件实际调用的那 5 个方法**换成内存实现 ✓ ⇒ 组件可离线构造 ✓。
 * <p>② ★ 真正的硬阻断其实在**类初始化** ✗：{@code BuffManager} 的静态常量
 * {@code BUFF_MOVEMENT_SPEED_MODIFIER_KEY} 走 {@code KeyFactory.Registry.of(...)}，而 {@code KeyFactory}
 * 的实现在插件 {@code onEnable} 才 install ⇒ 不装桩时**连类都加载不了** ✗（实测：{@code NoClassDefFoundError} ✗）
 * ⇒ 本类在 {@link BeforeClass} 里 install 一个**最小 KeyFactory 桩**（{@code new NamespacedKey("shadowhunterroles", key)} ✓，
 * 离线可构造 ✓）。
 * <p><b>★ 该桩的副作用（如实申报）</b> ✗：{@code KeyFactory.Registry} 是**全局静态** ⇒ 本类的 install 对本 JVM
 * 内**其它测试**同样生效 ✓（现算：`src/test` 里**零**处引用 {@code KeyFactory} ⇒ 无人依赖"未安装即抛" ✓）。
 * <p><b>替身的语义边界（如实申报）</b> ✗：替身**不**模拟真实 {@code BuffManager} 的取最大时长 / 到期判定 /
 * STUN 属性修饰符 / 药水施加 —— 那些是**生产行为**，归窗口级读数（见交付说明的未覆盖段 ✓）。
 *
 * <h2>哪些动词能离线驱动</h2>
 * 全部 7 个动词都经替身 ✓；★ 例外：{@code clear} 只覆盖**空账本**路径 ✓ —— 账本非空时它会逐个
 * {@code player.removePotionEffect(...)} ⇒ 需要活 Player ✗（本卡如实申报，归窗口卡 ✓）。
 */
public class BuffComponentOperationTest {

    /** **离线测试基设**：装一个最小 {@code KeyFactory} 桩，让带静态 {@code NamespacedKey} 常量的类可被加载 ✓。 */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    /** **最小测试替身**：buff 记账表换成内存表（不碰 Player、不碰实例）。 */
    private static final class StubBuffManager extends BuffManager {

        private final Map<BuffType, Integer> ticks = new LinkedHashMap<>();

        StubBuffManager() {
            super(null, null, null); // 基类构造只赋三个字段 ⇒ 零解引用 ✓
        }

        @Override
        public void addBuff(BuffType type, int durationTicks) {
            ticks.put(type, durationTicks);
        }

        @Override
        public boolean hasBuff(BuffType type) {
            return ticks.containsKey(type);
        }

        @Override
        public long getRemainingTicks(BuffType type) {
            return ticks.getOrDefault(type, 0);
        }

        @Override
        public boolean canCastSkill() {
            return !hasBuff(BuffType.STUN) && !hasBuff(BuffType.SILENCE);
        }

        @Override
        public boolean canUseMainWeapon() {
            return !hasBuff(BuffType.STUN);
        }
    }

    /** 玩家面替身：只提供 {@code player()} 的**空值**（空账本下不会被解引用 ✓）。 */
    private static final class StubSelf implements Self {

        @Override
        public Player player() {
            return null;
        }

        @Override
        public UUID id() {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
    }

    private static BuffComponent component() {
        return new BuffComponent("buffs",
                new ComponentServices(new StubSelf(), null, null),
                new StubBuffManager());
    }

    // ───────── ① 合法读（闸门两个） ─────────

    /** 技能闸门：无 buff ⇒ `true`；`STUN` 后 ⇒ `false`（读的是既有强类型方法 ✓）。 */
    @Test
    public void canCastReadsTheExistingGate() {
        BuffComponent buffs = component();
        assertEquals("true", buffs.onOperationCommand("can_cast"));
        assertEquals("50", buffs.onOperationCommand("add STUN 50"));
        assertEquals("false", buffs.onOperationCommand("can_cast"));
    }

    /** 主武器闸门：无 buff ⇒ `true`；`STUN` 后 ⇒ `false`。 */
    @Test
    public void canWeaponReadsTheExistingGate() {
        BuffComponent buffs = component();
        assertEquals("true", buffs.onOperationCommand("can_weapon"));
        assertEquals("50", buffs.onOperationCommand("add STUN 50"));
        assertEquals("false", buffs.onOperationCommand("can_weapon"));
    }

    // ───────── ② 合法写 + 合法读（has / remaining） ─────────

    /** `add` 回**写后状态**；`has` / `remaining` 读同一份表（含"无该 buff"的 0 ✓）。 */
    @Test
    public void addHasAndRemainingShareOneTable() {
        BuffComponent buffs = component();
        assertEquals("写后状态 = 剩余刻", "120", buffs.onOperationCommand("add SILENCE 120"));
        assertEquals("true", buffs.onOperationCommand("has SILENCE"));
        assertEquals("120", buffs.onOperationCommand("remaining SILENCE"));
        assertEquals("false", buffs.onOperationCommand("has STUN"));
        assertEquals("0", buffs.onOperationCommand("remaining STUN"));
    }

    /** `0` 是合法的写入值（与既有 add 语义一致 ✓）。 */
    @Test
    public void zeroTicksIsAValidWrite() {
        BuffComponent buffs = component();
        assertEquals("0", buffs.onOperationCommand("add IMMUNE 0"));
        assertEquals("true", buffs.onOperationCommand("has IMMUNE"));
    }

    // ───────── ③ 账本读口（count / clear） ─────────

    /** `count` / `clear` 读同一份账本；空账本 ⇒ `0`（★ 非空路径需活 Player ⇒ 见类 javadoc ✗）。 */
    @Test
    public void countAndClearReportTheEmptyLedger() {
        BuffComponent buffs = component();
        assertEquals("0", buffs.onOperationCommand("count"));
        assertEquals("写后状态 = 账本数", "0", buffs.onOperationCommand("clear"));
        assertEquals("0", buffs.onOperationCommand("count"));
    }

    // ───────── ④ 未识别 / 拒绝（三态） ─────────

    /** 未知动词（含**大小写不符**与多带参数的无参动词）⇒ 未识别 ⇒ `null`，且不改状态 ✓。 */
    @Test
    public void unknownVerbAndNoArgVerbsWithArgumentsAreRejected() {
        BuffComponent buffs = component();
        assertNull("未知动词", buffs.onOperationCommand("reset"));
        assertNull("大写不符（动词大小写敏感）", buffs.onOperationCommand("CAN_CAST"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("can_cast 1"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("count 1"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("clear 1"));
        assertEquals("拒绝不得改状态", "true", buffs.onOperationCommand("can_cast"));
    }

    /** 空 / 空白 / null payload ⇒ 未识别 ⇒ `null`（javadoc 写明 ✓）。 */
    @Test
    public void emptyPayloadIsRejected() {
        BuffComponent buffs = component();
        assertNull("null", buffs.onOperationCommand(null));
        assertNull("空串", buffs.onOperationCommand(""));
        assertNull("纯空白", buffs.onOperationCommand("   "));
    }

    /** 参数非法：未知 buff id（含**大小写不符**）· 缺参 · 多参 · 非数字 · 负数 · 溢出 ⇒ `null` ✓。 */
    @Test
    public void malformedArgumentsAreRejected() {
        BuffComponent buffs = component();
        assertNull("未知 buff id", buffs.onOperationCommand("has NOPE"));
        assertNull("buff id 大小写不符（严格 valueOf）", buffs.onOperationCommand("has silence"));
        assertNull("缺参", buffs.onOperationCommand("has"));
        assertNull("缺参", buffs.onOperationCommand("add SILENCE"));
        assertNull("多参", buffs.onOperationCommand("add SILENCE 1 2"));
        assertNull("非数字", buffs.onOperationCommand("add SILENCE abc"));
        assertNull("负数", buffs.onOperationCommand("add SILENCE -1"));
        assertNull("溢出", buffs.onOperationCommand("add SILENCE 999999999999"));
        assertEquals("全部被拒 ⇒ 表仍为空", "false", buffs.onOperationCommand("has SILENCE"));
    }
}
