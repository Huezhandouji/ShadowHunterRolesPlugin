package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

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
}
