package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.registry.RoleLoader;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;
import java.util.logging.Logger;

import static org.junit.Assert.*;

/**
 * 「艾尔伯特」的**离线单测**：装配闸门 + 全部纯函数。
 *
 * <h2>★ 为什么必须先装 {@code KeyFactory} 桩</h2>
 * 本测试会加载 {@link AlbertMainWeapon}，它带**静态**常量
 * {@code static final NamespacedKey ATTACK_SPEED_KEY = KeyFactory.Registry.of(...)}。
 * 没人 {@code install} 时 {@code KeyFactory.Registry.of} 抛 {@code IllegalStateException}
 * ⇒ 类初始化失败，而且 <b>JVM 会把这次失败缓存住</b>：之后任何触碰该类的代码都
 * {@code NoClassDefFoundError}，**事后补装桩也救不回来**，且症状随执行顺序变化。
 * ⇒ 与 {@code RoleAssemblyTest} 同做法，本类自己先装桩（install 幂等）。
 *
 * <h2>覆盖什么</h2>
 * <ul>
 *   <li><b>装配</b>：{@code albert} 在注册表里、能装起来（缺依赖 / 依赖环 / 槽位冲突都会红）；</li>
 *   <li><b>锁定的纯函数</b>：标记过期判据、零件回绕；</li>
 *   <li><b>主武器的纯函数</b>：闭区间随机、弹药回复条件、能否开火；</li>
 *   <li><b>无人机系统的纯函数</b>：集火加速后的间隔、编队级 0.7 秒闸门。</li>
 * </ul>
 *
 * <p><b>不覆盖</b>（如实申报）：运行期行为（无人机飞行 / 索敌 / 命中结算 / 光环 / HUD），
 * 那些需要真服务集与真世界，归实测。
 */
public class AlbertRoleTest {

    /** 离线测试基设：装最小 {@code KeyFactory} 桩（★ 见类注释，漏了会被 JVM 缓存失败）。 */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    // ───────── 装配闸门 ─────────

    /** 角色 id 必须出现在 {@link RoleLoader#defaultDefinitions()} 里。 */
    @Test
    public void albertIsRegistered() {
        boolean found = new RoleLoader(Logger.getLogger("AlbertRoleTest"))
                .defaultDefinitions().stream()
                .anyMatch(definition -> "albert".equals(definition.id()));
        assertTrue("albert 必须被注册进默认角色表（否则进游戏选不到）", found);
    }

    /** 角色必须能装配且依赖自检通过（缺依赖 / 依赖环 / 槽位冲突在这里变红）。 */
    @Test
    public void albertAssemblesAndVerifies() {
        Role.Builder builder = new RoleLoader(Logger.getLogger("AlbertRoleTest"))
                .defaultDefinitions().stream()
                .filter(definition -> "albert".equals(definition.id()))
                .findFirst()
                .orElseThrow()
                .builder()
                .get();
        Role role = builder.build();
        assertNotNull("albert 必须能 build 出角色", role);
        role.verifyDependencies();
    }

    // ───────── 锁定被动 ─────────

    /** 到期刻等于当前刻 ⇒ 已过期（{@code >=} 而不是 {@code >}）。 */
    @Test
    public void markExpiresAtDueTick() {
        assertFalse(AlbertLockOnPassive.isMarkExpired(99, 100));
        assertTrue(AlbertLockOnPassive.isMarkExpired(100, 100));
        assertTrue(AlbertLockOnPassive.isMarkExpired(101, 100));
    }

    /** 零件满 {@code PARTS_REQUIRED} 层回绕到 0。 */
    @Test
    public void partsWrapAroundAtRequired() {
        int parts = 0;
        for (int hit = 1; hit < AlbertLockOnPassive.PARTS_REQUIRED; hit++) {
            parts = AlbertLockOnPassive.partsAfterHit(parts);
            assertEquals(hit, parts);
        }
        assertEquals("满 4 层必须回绕到 0", 0,
                AlbertLockOnPassive.partsAfterHit(AlbertLockOnPassive.PARTS_REQUIRED - 1));
    }

    /** 零件层数任何输入都落在合法区间（不越界）。 */
    @Test
    public void partsAlwaysWithinRange() {
        for (int input = -5; input < 20; input++) {
            int next = AlbertLockOnPassive.partsAfterHit(input);
            assertTrue("input=" + input + " 结果越界: " + next,
                    next >= 0 && next < AlbertLockOnPassive.PARTS_REQUIRED);
        }
    }

    /** 标记时长常量就是需求写的 20 秒。 */
    @Test
    public void markDurationIsTwentySeconds() {
        assertEquals(400, AlbertLockOnPassive.MARK_DURATION_TICKS);
        assertEquals(4, AlbertLockOnPassive.PARTS_REQUIRED);
    }

    // ───────── 主武器 ─────────

    /** 闭区间随机：两端都能取到，且永不越界。 */
    @Test
    public void rollInclusiveCoversBothEnds() {
        Random random = new Random(20261006L);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 2000; i++) {
            int value = AlbertMainWeapon.rollInclusive(random, 3, 6);
            assertTrue("越界: " + value, value >= 3 && value <= 6);
            if (value == 3) {
                sawMin = true;
            }
            if (value == 6) {
                sawMax = true;
            }
        }
        assertTrue("必须能取到下限 3", sawMin);
        assertTrue("必须能取到上限 6", sawMax);
    }

    /** 非法入参优雅退化（max <= min ⇒ 返回 min，不抛）。 */
    @Test
    public void rollInclusiveDegradesGracefully() {
        assertEquals(5, AlbertMainWeapon.rollInclusive(null, 5, 9));
        assertEquals(5, AlbertMainWeapon.rollInclusive(new Random(1L), 5, 5));
        assertEquals(5, AlbertMainWeapon.rollInclusive(new Random(1L), 5, 2));
    }

    /** 弹匣满 ⇒ 不回复；能量不足 ⇒ 不回复；都满足才回。 */
    @Test
    public void ammoRegenNeedsRoomAndEnergy() {
        assertFalse("弹匣满不回复", AlbertMainWeapon.shouldRegenAmmo(6, 6, 100));
        assertFalse("能量不足不回复", AlbertMainWeapon.shouldRegenAmmo(3, 6, 1));
        assertTrue("有位置且能量够 ⇒ 回复", AlbertMainWeapon.shouldRegenAmmo(3, 6, 2));
    }

    /** 开火条件：有弹药 且 过了射速闸门。 */
    @Test
    public void firingNeedsAmmoAndInterval() {
        assertFalse("没弹药打不出", AlbertMainWeapon.canFire(0, 100, 0));
        assertFalse("射速闸门未到", AlbertMainWeapon.canFire(3, 100, 101));
        assertTrue("有弹药且闸门已到", AlbertMainWeapon.canFire(3, 100, 100));
    }

    /** 主武器常量逐条对齐需求。 */
    @Test
    public void weaponConstantsMatchSpec() {
        assertEquals(6, AlbertMainWeapon.MAGAZINE_SIZE);
        assertEquals(2, AlbertMainWeapon.SHOT_INTERVAL_TICKS);   // 0.1 秒
        assertEquals(100, AlbertMainWeapon.AMMO_REGEN_TICKS);    // 5 秒
        assertEquals(2, AlbertMainWeapon.AMMO_REGEN_ENERGY_COST);
        assertEquals(3, AlbertMainWeapon.MELEE_SANTE_MIN);
        assertEquals(6, AlbertMainWeapon.MELEE_SANTE_MAX);
        assertEquals(3, AlbertMainWeapon.SHOT_SANTE_MIN);
        assertEquals(6, AlbertMainWeapon.SHOT_SANTE_MAX);
    }

    // ───────── 无人机系统 ─────────

    /** 集火窗口 ⇒ 间隔按 1.5 倍缩短；非集火 ⇒ 原值。 */
    @Test
    public void focusShortensIntervals() {
        assertEquals(120, AlbertDroneSystem.intervalTicks(120, false));
        assertEquals(80, AlbertDroneSystem.intervalTicks(120, true));
        assertEquals(14, AlbertDroneSystem.intervalTicks(14, false));
        assertEquals(9, AlbertDroneSystem.intervalTicks(14, true));
    }

    /** 间隔永不为 0（否则每刻都能起飞）。 */
    @Test
    public void intervalNeverZero() {
        for (int base = 0; base <= 20; base++) {
            assertTrue(AlbertDroneSystem.intervalTicks(base, false) >= 1);
            assertTrue(AlbertDroneSystem.intervalTicks(base, true) >= 1);
        }
    }

    /** 编队级闸门：从未起飞 ⇒ 放行；未到间隔 ⇒ 挡；到了 ⇒ 放行。 */
    @Test
    public void launchGateBehaviour() {
        assertTrue("从未起飞过必须放行", AlbertDroneSystem.canLaunch(100, -1, false));
        assertFalse("闸门未到", AlbertDroneSystem.canLaunch(100, 90, false));
        assertTrue("刚好到闸门", AlbertDroneSystem.canLaunch(104, 90, false));
        // 集火窗口里闸门被缩短到 9 刻
        assertFalse("集火闸门未到", AlbertDroneSystem.canLaunch(98, 90, true));
        assertTrue("集火闸门已到", AlbertDroneSystem.canLaunch(99, 90, true));
    }

    /** 无人机常量逐条对齐需求。 */
    @Test
    public void droneConstantsMatchSpec() {
        assertEquals(4, AlbertDroneSystem.MAX_DRONES);
        assertEquals(120, AlbertDroneSystem.LAUNCH_INTERVAL_TICKS);   // 6 秒
        assertEquals(14, AlbertDroneSystem.LAUNCH_GATE_TICKS);        // 0.7 秒
        assertEquals(40, AlbertDroneSystem.OUTBOUND_TIMEOUT_TICKS);   // 2 秒
        assertEquals(3.0d, AlbertDroneSystem.ATTACK_RADIUS, 1e-9);
        assertEquals(0.75d, AlbertDroneSystem.SPEED_PER_TICK, 1e-9);  // 15 格/秒
        assertEquals("集火窗口 10 秒", 200, AlbertDroneSystem.FOCUS_WINDOW_TICKS);
        assertEquals("集火期内出击超时放宽到 10 秒", 200, AlbertDroneSystem.FOCUS_OUTBOUND_TIMEOUT_TICKS);
        assertEquals(255, AlbertDroneSystem.SHIELD_RESISTANCE_AMPLIFIER);
        assertEquals(5, AlbertDroneSystem.SHIELD_SPEED_AMPLIFIER);    // 速度 6 = 增幅 5
        assertEquals(40, AlbertDroneSystem.SHIELD_SPEED_TICKS);       // 2 秒
    }

    /** 自杀式无人机常量逐条对齐需求。 */
    @Test
    public void kamikazeConstantsMatchSpec() {
        assertEquals(60, AlbertDroneSystem.KAMIKAZE_CHARGE_TICKS);    // 3 秒
        assertEquals(20, AlbertDroneSystem.KAMIKAZE_FUSE_TICKS);      // 1 秒
        assertEquals(7.0d, AlbertDroneSystem.KAMIKAZE_BLAST_RADIUS, 1e-9);
        assertEquals(10d, AlbertDroneSystem.KAMIKAZE_SOUL_DAMAGE, 1e-9);
        assertEquals(10, AlbertDroneSystem.KAMIKAZE_SANTE_DAMAGE);
        assertEquals(8d, AlbertDroneSystem.KAMIKAZE_SELF_HEAL, 1e-9);
    }

    /** 哨戒 / 诱饵常量逐条对齐需求。 */
    @Test
    public void sentryAndDecoyConstantsMatchSpec() {
        assertEquals(10.0d, AlbertDroneSystem.SENTRY_RADIUS, 1e-9);
        assertEquals(0, AlbertDroneSystem.SENTRY_SLOWNESS_AMPLIFIER);  // 缓慢 I = 增幅 0
        assertEquals(80, AlbertDroneSystem.DECOY_LIFETIME_TICKS);      // 4 秒
        assertEquals(4.0d, AlbertDroneSystem.DECOY_TRIGGER_RADIUS, 1e-9);
        assertEquals(10d, AlbertDroneSystem.DECOY_PHYSICAL_DAMAGE, 1e-9);
        assertEquals(2, AlbertDroneSystem.DECOY_SLOWNESS_AMPLIFIER);   // 缓慢 III = 增幅 2
        assertEquals(3, AlbertDroneSystem.DECOY_SANTE_MIN);
        assertEquals(10, AlbertDroneSystem.DECOY_SANTE_MAX);
    }

    /** 技能3 的储存次数与回复周期逐条对齐需求。 */
    @Test
    public void thrustConstantsMatchSpec() {
        assertEquals(2, AlbertThrustSkill.MAX_CHARGES);
        assertEquals(160, AlbertThrustSkill.CHARGE_REGEN_TICKS);       // 8 秒
        assertEquals(6, AlbertThrustSkill.CHARGE_REGEN_ENERGY_COST);
        assertEquals(8.0d, AlbertThrustSkill.DASH_DISTANCE, 1e-9);
        assertEquals(3, AlbertThrustSkill.DASH_RESISTANCE_AMPLIFIER);  // 抗性 4 = 增幅 3
        assertEquals("两次释放之间 0.5 秒", 10, AlbertThrustSkill.RELEASE_INTERVAL_TICKS);
        assertEquals("跃起高度 0.5 格", 0.5d, AlbertThrustSkill.DASH_LIFT, 1e-9);
        assertEquals("跃起阶段 3 刻", 3, AlbertThrustSkill.HOP_TICKS);
    }

    /**
     * ★★ **突进方向必须夹掉向下的分量**（这是"平地突进不动"那个缺陷的修复核心）。
     *
     * <p>根因：走廊沿视线采样，瞄得略低就让走廊扎进地面 ⇒ 采样点全不合格 ⇒ 位移退化为原地。
     * 把"向下"夹成水平后，无论怎么瞄，平地都能稳定前进 8 格；而"向上"必须保留
     * （抬头向上突进是本技能的合法用法）。
     */
    @Test
    public void dashDirectionClampsDownwardButKeepsUpward() {
        Vector level = AlbertThrustSkill.dashDirection(new Vector(0d, 0d, 1d));
        assertEquals("平视不该有竖直分量", 0d, level.getY(), 1e-9);
        assertEquals("必须是单位向量", 1d, level.length(), 1e-9);

        Vector down = AlbertThrustSkill.dashDirection(new Vector(0d, -0.7d, 0.7d));
        assertEquals("向下的分量必须被夹成 0", 0d, down.getY(), 1e-9);
        assertEquals("夹掉后仍要归一化", 1d, down.length(), 1e-9);

        Vector up = AlbertThrustSkill.dashDirection(new Vector(0d, 0.6d, 0.8d));
        assertTrue("向上的分量必须保留", up.getY() > 0d);
        assertEquals("仍要归一化", 1d, up.length(), 1e-9);

        // 视线垂直向下（退化）⇒ 退回 +Z，绝不能是零向量（否则原地不动）
        Vector straightDown = AlbertThrustSkill.dashDirection(new Vector(0d, -1d, 0d));
        assertEquals("退化时必须是单位向量", 1d, straightDown.length(), 1e-9);
        assertEquals("退化时不能有竖直分量", 0d, straightDown.getY(), 1e-9);

        // null / 零向量都不得抛异常
        assertEquals(1d, AlbertThrustSkill.dashDirection(null).length(), 1e-9);
        assertEquals(1d, AlbertThrustSkill.dashDirection(new Vector()).length(), 1e-9);
    }

    /** ★ 满仓时**连能量都不该扣**（需求："存储到 2 后不再继续消耗能量存储"）。 */
    @Test
    public void fullChargesNeverConsumeEnergy() {
        assertFalse("满仓不回复（也不扣能量）",
                AlbertThrustSkill.shouldRegenCharge(2, 2, 100));
        assertFalse("满仓且能量为 0 也不回复",
                AlbertThrustSkill.shouldRegenCharge(2, 2, 0));
        assertFalse("未满但能量不足不回复",
                AlbertThrustSkill.shouldRegenCharge(1, 2, 5));
        assertTrue("未满且能量够 ⇒ 回复",
                AlbertThrustSkill.shouldRegenCharge(1, 2, 6));
        assertTrue("空仓且能量够 ⇒ 回复",
                AlbertThrustSkill.shouldRegenCharge(0, 2, 100));
    }

    /** 二技能信号弹射程 = 25 格（到达即自动爆开）。 */
    @Test
    public void huntOrderRangeIsTwentyFive() {
        assertEquals(25.0d, AlbertHuntOrderSkill.MAX_AIM_RANGE, 1e-9);
    }

    /**
     * ★ 索敌范围的有效值（纯函数）：扩展未过期 ⇒ 扩展值；否则基准 15 格。
     *
     * <p>技能1 的"25 格"只活在同一 tick 内（{@code expandedUntilTick = tick + 1}）
     * ⇒ 这里把那条边界钉死：{@code nowTick} 等于到期刻时就<b>已经回到基准</b>。
     */
    @Test
    public void searchRangeFallsBackToBaseAfterExpiry() {
        double base = AlbertDroneSystem.BASE_SEARCH_RANGE;
        double expanded = AlbertDroneSystem.ASSEMBLE_SEARCH_RANGE;
        assertEquals(15.0d, base, 1e-9);
        assertEquals(25.0d, expanded, 1e-9);
        assertEquals("未扩展（到期刻 0）⇒ 基准", base,
                AlbertDroneSystem.searchRangeFor(base, expanded, 500, 0), 1e-9);
        assertEquals("扩展期内 ⇒ 扩展值", expanded,
                AlbertDroneSystem.searchRangeFor(base, expanded, 500, 501), 1e-9);
        assertEquals("到期刻等于当前刻 ⇒ 已经回到基准", base,
                AlbertDroneSystem.searchRangeFor(base, expanded, 500, 500), 1e-9);
        assertEquals("已过期 ⇒ 基准", base,
                AlbertDroneSystem.searchRangeFor(base, expanded, 500, 499), 1e-9);
    }

    /** actionbar 头尾装饰符 = 「〓」（U+3013）。 */
    @Test
    public void hudBracketIsGetaMark() {
        assertEquals("〓", AlbertDroneSystem.HUD_BRACKET);
        assertEquals(1, AlbertDroneSystem.HUD_BRACKET.codePointCount(0,
                AlbertDroneSystem.HUD_BRACKET.length()));
    }

    // ───────── 台词库 ─────────

    /** 扩展时刻标识必须两两不同（撞名会让两套台词混在一起）。 */
    @Test
    public void expansionMomentsAreDistinct() {
        String[] moments = {
                AlbertFloatingTextComponent.MOMENT_MELEE_HIT,
                AlbertFloatingTextComponent.MOMENT_SHOT_HIT,
                AlbertFloatingTextComponent.MOMENT_LOCK,
                AlbertFloatingTextComponent.MOMENT_SKILL_1,
                AlbertFloatingTextComponent.MOMENT_SKILL_2,
                AlbertFloatingTextComponent.MOMENT_SKILL_3,
                AlbertFloatingTextComponent.MOMENT_SKILL_4,
                AlbertFloatingTextComponent.MOMENT_KILL_MARKED,
                AlbertFloatingTextComponent.MOMENT_KILL_DRONE,
                AlbertFloatingTextComponent.MOMENT_FULL,
                AlbertFloatingTextComponent.MOMENT_NO_DRONE,
                AlbertFloatingTextComponent.MOMENT_CLOAK,
                AlbertFloatingTextComponent.MOMENT_SENTRY,
                AlbertFloatingTextComponent.MOMENT_SHIELD,
                AlbertFloatingTextComponent.MOMENT_CONTROLLED,
                AlbertFloatingTextComponent.MOMENT_OVERLOAD
        };
        for (int i = 0; i < moments.length; i++) {
            for (int j = i + 1; j < moments.length; j++) {
                assertNotEquals("时刻标识撞名: " + moments[i], moments[i], moments[j]);
            }
        }
    }
}
