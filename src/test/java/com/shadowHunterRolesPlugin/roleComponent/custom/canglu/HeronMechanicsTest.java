package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「苍鹭 Heron」的**离线纯函数闸门**。
 *
 * <h2>为什么只打纯函数</h2>
 * 本角色绝大多数逻辑要 Bukkit 运行环境（活玩家、活世界、真粒子）。真正会**静默出错**的是
 * 「旋律选取」的**存量回复闸门**：写错时不报错、也不崩，只是"满仓还白扣能量"或
 * "缺能也硬充"（资源被悄悄吃掉，手感还看不出来）。口径与
 * {@code AlbertThrustSkill} / {@code RavenAirMobilityPassive} 的同名函数一致。
 *
 * <h2>★ 离线测试必须先装 KeyFactory 桩</h2>
 * 本包组件里的 {@code static final NamespacedKey} 走 {@code KeyFactory.Registry.of(...)}；
 * 没人 install 时抛 {@code IllegalStateException}，而 **JVM 会缓存类初始化失败**
 * ⇒ 之后碰它们的代码全变 {@code NoClassDefFoundError}（症状极隐蔽）。口径同 {@code RavenMechanicsTest}。
 */
public class HeronMechanicsTest {

    /** 离线测试基设：先装一个最小 {@code KeyFactory} 桩（幂等）。 */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    // ───────── 旋律选取：存量回复的闸门 ─────────

    @Test
    public void 存量_满仓时不回复也不扣能量() {
        assertFalse("满 " + HeronHookSkill.MAX_CHARGES + " 个 ⇒ 这一拍不回复（因此也不会扣能量）",
                HeronHookSkill.shouldRegenCharge(HeronHookSkill.MAX_CHARGES, HeronHookSkill.MAX_CHARGES, 100));
    }

    @Test
    public void 存量_能量不足这一拍不充() {
        int cost = HeronHookSkill.CHARGE_ENERGY;
        assertFalse("未满仓 + 能量差 1 点 ⇒ 不充（不欠账、不排队）",
                HeronHookSkill.shouldRegenCharge(0, HeronHookSkill.MAX_CHARGES, cost - 1));
        assertFalse("未满仓 + 能量 0 ⇒ 不充",
                HeronHookSkill.shouldRegenCharge(0, HeronHookSkill.MAX_CHARGES, 0));
    }

    @Test
    public void 存量_能量刚好够才充() {
        int cost = HeronHookSkill.CHARGE_ENERGY;
        assertTrue("未满仓 + 能量刚好 " + cost + " ⇒ 充",
                HeronHookSkill.shouldRegenCharge(0, HeronHookSkill.MAX_CHARGES, cost));
        assertTrue("只剩 1 个空位 + 能量充足 ⇒ 充",
                HeronHookSkill.shouldRegenCharge(HeronHookSkill.MAX_CHARGES - 1, HeronHookSkill.MAX_CHARGES, 100));
    }

    // ───────── 旋律选取：拉拽时限 + 双击蹲取消（2026-10-30 反馈修复）─────────

    /** 拉拽硬上限：正好 3 秒（60 刻）必须脱钩，不许被移动目标/卡墙拖成无限拉拽。 */
    @Test
    public void 拉拽_最长不超过3秒() {
        assertEquals("拉拽上限必须正好 3 秒", 60, HeronHookSkill.PULL_MAX_TICKS);
        assertEquals("3 秒 = 60 刻", 3 * 20, HeronHookSkill.PULL_MAX_TICKS);
        assertFalse("第 59 刻还没到 3 秒 ⇒ 继续拉", HeronHookSkill.shouldAbortPull(59));
        assertTrue("第 60 刻 = 3 秒 ⇒ 必须脱钩", HeronHookSkill.shouldAbortPull(60));
        assertTrue("超过 3 秒也一律脱钩（不累积、不续拉）", HeronHookSkill.shouldAbortPull(120));
    }

    /** 双击蹲窗口：正好 0.15 秒（3 刻）内连按两次才取消；差 4 刻（0.2 秒）不算。 */
    @Test
    public void 双击蹲_窗口正好0点15秒() {
        assertEquals("窗口必须正好 3 刻 = 0.15 秒", 3, HeronHookSkill.DOUBLE_SNEAK_WINDOW_TICKS);
        assertTrue("相邻刻按下算双击", HeronHookSkill.withinDoubleSneakWindow(10, 11));
        assertTrue("正好 0.15 秒（3 刻）仍算双击", HeronHookSkill.withinDoubleSneakWindow(10, 13));
        assertFalse("差 4 刻（0.2 秒）不算双击", HeronHookSkill.withinDoubleSneakWindow(10, 14));
        assertFalse("同一刻不可能按两次", HeronHookSkill.withinDoubleSneakWindow(10, 10));
        assertFalse("时间倒流不算双击", HeronHookSkill.withinDoubleSneakWindow(13, 10));
    }

    // ───────── 主武器备注：特殊值伤害口径必须与实际一致（2026-10-30 修）─────────

    /**
     * 主武器 lore 是玩家唯一能读到的"数值说明书"，它写错不会报错、只会让玩家按错的数字打。
     * 这里把**特殊值伤害**与实际上限钉住：旧文案（12 特殊值 / 结算 4 灵魂伤害 / 护盾 / 12 SAN / 24 层）
     * 全是过时口径，真值见 {@code HeronMainWeapon} 的 onAttack 与 {@code HeronTraumaPassive} 的常量。
     */
    @Test
    public void 主武器备注_特殊值伤害与实际一致() {
        StringBuilder lore = new StringBuilder();
        for (net.kyori.adventure.text.Component line : new HeronMainWeapon.Specification().getDescription()) {
            lore.append(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                    .plainText().serialize(line)).append('\n');
        }
        String text = lore.toString();
        assertTrue("每次攻击必须写 3~7 特殊值伤害: " + text, text.contains("3~7特殊值伤害"));
        assertFalse("旧错误文案「12特殊值伤害」不许再出现: " + text, text.contains("12特殊值伤害"));
        assertTrue("4 层结算的额外 7 点特殊值伤害必须写明: " + text, text.contains("7点特殊值伤害"));
        assertTrue("夺取能量应为 5（实际 ENERGY_THEFT_AMOUNT）: " + text, text.contains("夺取5能量"));
        assertTrue("创伤上限必须是 16 层（实际 MAX_STACK_COUNT）: " + text, text.contains("16层"));
        assertFalse("旧的「24层」是错误上限: " + text, text.contains("24层"));
        assertFalse("旧的「12点SAN值」回复不存在: " + text, text.contains("12点SAN值"));
    }
}
