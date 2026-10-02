package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.registry.RoleLoader;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 「猎手」的离线判据：**纯函数 + 进化档位换算 + 装配注册**。
 *
 * <h2>为什么这些能离线测</h2>
 * 本工程的口径是"需要真实 {@code Player} 的分支必须如实申报、不硬测"。猎手的战斗逻辑
 * （粒子 / 索敌 / 位移 / 拉回 / 隐身）全部需要活玩家，因此本测试**只钉住三块纯逻辑**：
 * <ol>
 *   <li><b>闭区间随机整数</b>（{@link HunterGrudgeMainWeapon#rollInclusive}）—— 需求写的是
 *       "6 到 10"，两端都含，与 {@code Random#nextInt(bound)} 的半开区间必须区分开；</li>
 *   <li><b>两个几何 / 阈值判据</b>（{@link HunterPullSkill#insideThrustCorridor}、
 *       {@link HunterPullSkill#pullEscaped}、{@link HunterPreyPassive#withinMarkRange}）
 *       —— 这类"写错了也照跑、只是永远打不中 / 永远不脱控"的判据最值得钉；</li>
 *   <li><b>进化档位 ⇒ 生效值</b>（{@link HunterEvolutionPassive}）—— 档位换算不碰 Bukkit，
 *       用一个"服务集为空的实例"即可穷举（{@code getCurrentEvolutionLevel()} 只读字段）。</li>
 * </ol>
 *
 * <h2>KeyFactory 桩（必须）</h2>
 * {@link HunterGrudgeMainWeapon} 有 {@code static final NamespacedKey ATTACK_SPEED_KEY =
 * KeyFactory.Registry.of(...)} —— **类加载时求值**，而离线 JVM 里没人 install 过 ⇒
 * 会抛 {@code ExceptionInInitializerError}，且 ★ JVM 会**缓存这次失败**，
 * 之后任何触碰该类的代码一律 {@code NoClassDefFoundError}（"谁先碰到谁毒化整个 JVM"）。
 * 因此本类自带一个幂等的最小桩（照 {@code registry/RoleAssemblyTest} 的既有写法）。
 */
public class HunterMechanicsTest {

    /**
     * 装 {@code KeyFactory} 最小桩：幂等，谁先跑都一样（写法幂等 ⇒ 与测试执行顺序无关）。
     * <p>这是**全局静态副作用**（如实申报），但它是本工程离线测任何"带静态键的组件类"的前提。
     */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    // ───────── ① 闭区间随机整数 ─────────

    /**
     * 遗愤的两条随机伤害都必须**两端都含**：物理 {@value HunterGrudgeMainWeapon#PHYSICAL_DAMAGE_MIN}~
     * {@value HunterGrudgeMainWeapon#PHYSICAL_DAMAGE_MAX}、SanTE
     * {@value HunterGrudgeMainWeapon#SANTE_DAMAGE_MIN}~{@value HunterGrudgeMainWeapon#SANTE_DAMAGE_MAX}。
     * <p>穷举 20000 次抽样的极值：**最小必须恰好等于下界、最大必须恰好等于上界**
     * （只判"落在区间内"是弱断言 —— 半开区间实现也会通过，但它永远抽不到上界）。
     */
    @Test
    public void physicalAndSanteRollsIncludeBothBounds() {
        Random random = new Random(20261002L);

        int physicalMin = Integer.MAX_VALUE;
        int physicalMax = Integer.MIN_VALUE;
        int santeMin = Integer.MAX_VALUE;
        int santeMax = Integer.MIN_VALUE;
        for (int i = 0; i < 20000; i++) {
            int physical = HunterGrudgeMainWeapon.rollInclusive(random,
                    HunterGrudgeMainWeapon.PHYSICAL_DAMAGE_MIN, HunterGrudgeMainWeapon.PHYSICAL_DAMAGE_MAX);
            int sante = HunterGrudgeMainWeapon.rollInclusive(random,
                    HunterGrudgeMainWeapon.SANTE_DAMAGE_MIN, HunterGrudgeMainWeapon.SANTE_DAMAGE_MAX);
            physicalMin = Math.min(physicalMin, physical);
            physicalMax = Math.max(physicalMax, physical);
            santeMin = Math.min(santeMin, sante);
            santeMax = Math.max(santeMax, sante);
        }

        assertEquals("物理伤害下界必须抽得到（闭区间）",
                HunterGrudgeMainWeapon.PHYSICAL_DAMAGE_MIN, physicalMin);
        assertEquals("物理伤害上界必须抽得到（闭区间）",
                HunterGrudgeMainWeapon.PHYSICAL_DAMAGE_MAX, physicalMax);
        assertEquals("SanTE 伤害下界必须抽得到（闭区间）",
                HunterGrudgeMainWeapon.SANTE_DAMAGE_MIN, santeMin);
        assertEquals("SanTE 伤害上界必须抽得到（闭区间）",
                HunterGrudgeMainWeapon.SANTE_DAMAGE_MAX, santeMax);
    }

    /** 退化与非法入参：{@code max == min} 恒定回该值；{@code max < min} 与 {@code null} 回下界（不抛）。 */
    @Test
    public void rollInclusiveDegeneratesSafely() {
        Random random = new Random(7L);
        assertEquals("闭区间退化成一个点时必须回该点", 5, HunterGrudgeMainWeapon.rollInclusive(random, 5, 5));
        assertEquals("上界小于下界时回下界（不抛、不打断战斗）",
                9, HunterGrudgeMainWeapon.rollInclusive(random, 9, 3));
        assertEquals("随机源为 null 时回下界", 9, HunterGrudgeMainWeapon.rollInclusive(null, 9, 3));
    }

    // ───────── ② 标记范围 ─────────

    /** "30 格内"含 30、含 0；超出与负数一律不算。 */
    @Test
    public void markRangeBoundariesAreInclusive() {
        assertTrue("贴身（0 格）也算在范围内", HunterPreyPassive.withinMarkRange(0d));
        assertTrue("29.99 在范围内", HunterPreyPassive.withinMarkRange(29.99d));
        assertTrue("恰好 30 格计入（\"30格内\"含 30）", HunterPreyPassive.withinMarkRange(30d));
        assertFalse("30.01 超出范围", HunterPreyPassive.withinMarkRange(30.01d));
        assertFalse("负距离不合法", HunterPreyPassive.withinMarkRange(-1d));
    }

    // ───────── ③ 前方长廊（穿刺命中）─────────

    /** 正前方、同高度、5 格 ⇒ 命中。 */
    @Test
    public void thrustCorridorAcceptsStraightAhead() {
        assertTrue("正前方 5 格、同高度必须命中",
                HunterPullSkill.insideThrustCorridor(5d, 0d, 0d, 1d, 0d));
        assertTrue("近距离（0.5 格）也算命中",
                HunterPullSkill.insideThrustCorridor(0.5d, 0d, 0d, 1d, 0d));
    }

    /** 射程：前向必须 ≥ 0 且 ≤ 12 —— 背后与超程都要刷掉。 */
    @Test
    public void thrustCorridorRejectsBehindAndBeyondRange() {
        assertFalse("背后（前向为负）不该命中",
                HunterPullSkill.insideThrustCorridor(-1d, 0d, 0d, 1d, 0d));
        assertTrue("恰好 12 格命中",
                HunterPullSkill.insideThrustCorridor(12d, 0d, 0d, 1d, 0d));
        assertFalse("12.01 格超出射程",
                HunterPullSkill.insideThrustCorridor(12.01d, 0d, 0d, 1d, 0d));
    }

    /** 侧向容差 1.6：1.59 命中、1.61 刷掉。 */
    @Test
    public void thrustCorridorRejectsSidewaysTargets() {
        assertTrue("侧向 1.59 在容差内",
                HunterPullSkill.insideThrustCorridor(5d, 1.59d, 0d, 1d, 0d));
        assertFalse("侧向 1.61 超出容差",
                HunterPullSkill.insideThrustCorridor(5d, 1.61d, 0d, 1d, 0d));
    }

    /** 高差容差 2.5：2.49 命中、2.51 刷掉。 */
    @Test
    public void thrustCorridorRejectsVerticalMismatch() {
        assertTrue("高差 2.49 在容差内",
                HunterPullSkill.insideThrustCorridor(5d, 0d, 2.49d, 1d, 0d));
        assertFalse("高差 2.51 超出容差",
                HunterPullSkill.insideThrustCorridor(5d, 0d, 2.51d, 1d, 0d));
    }

    /**
     * ★ **回归判据：竖直分量不得污染侧向**（把工程踩过的那类错误写法钉死）。
     *
     * <p>工程教训 §20：旧实现把"眼睛 − 脚底"的三维向量差当侧向偏离用，
     * 而眼睛比脚底高约 **1.62 格** ⇒ 阈值 1.3 / 1.6 一律被这 1.62 吃掉，
     * **站在平地上的敌人永远打不中**（且无任何报错）。
     *
     * <p>本测试同时断言两件事：
     * <ol>
     *   <li>长廊判据对 {@code dy = 1.62} 的**同列正前方**目标仍然命中
     *       （侧向 = 水平叉积 = 0，竖直分量进不去）；</li>
     *   <li>那个"错误写法"在此输入下确实会拒绝（{@code 1.62 > 1.6}）——
     *       因此若有人把长廊改回"三维向量差"，本测试红。</li>
     * </ol>
     */
    @Test
    public void verticalComponentMustNotLeakIntoLateral() {
        double dx = 5d;
        double dz = 0d;
        double eyeMinusFeet = 1.62d;
        double axisX = 1d;
        double axisZ = 0d;

        assertTrue("同列正前方、高差 1.62 格（眼睛 vs 脚底）必须仍然命中 —— "
                        + "侧向用水平叉积，竖直分量天然不参与",
                HunterPullSkill.insideThrustCorridor(dx, dz, eyeMinusFeet, axisX, axisZ));

        //错误写法（回归对照）：把三维向量差减去沿轴投影后的模长当"侧向距离"
        double along = dx * axisX + dz * axisZ;
        double lateralWrong = Math.sqrt(
                Math.pow(dx - along * axisX, 2)
                        + Math.pow(eyeMinusFeet, 2)
                        + Math.pow(dz - along * axisZ, 2));
        assertTrue("回归对照：那种错误写法算出的\"侧向距离\"确实等于竖直差 1.62 > 容差 1.6 ⇒ 必然误判",
                lateralWrong > HunterPullSkill.THRUST_LATERAL);
    }

    // ───────── ④ 拉回脱离判据 ─────────

    /** 阈值内不算脱离；恰好等于阈值不算；超过才算 —— 且阈值非法时一律不放走。 */
    @Test
    public void pullEscapeUsesStrictlyGreaterThanThreshold() {
        double t = HunterPullSkill.ESCAPE_DISTANCE;

        assertFalse("原地不动不脱离",
                HunterPullSkill.pullEscaped(10d, 64d, 10d, 10d, 64d, 10d, t));
        assertFalse("自己走了 0.215 格（一秒走路 / 20）不脱离",
                HunterPullSkill.pullEscaped(10d, 64d, 10d, 10.215d, 64d, 10d, t));
        assertFalse("恰好等于阈值不算脱离（判据是严格大于）",
                HunterPullSkill.pullEscaped(10d, 64d, 10d, 10d + t, 64d, 10d, t));
        assertTrue("超过阈值 ⇒ 判定为目标自己位移（技能位移）⇒ 放走",
                HunterPullSkill.pullEscaped(10d, 64d, 10d, 10d + t + 0.001d, 64d, 10d, t));
        assertFalse("阈值为 0 时视为参数非法（宁可不放走）",
                HunterPullSkill.pullEscaped(0d, 0d, 0d, 100d, 0d, 0d, 0d));
        assertFalse("阈值为负时同样视为非法",
                HunterPullSkill.pullEscaped(0d, 0d, 0d, 100d, 0d, 0d, -3d));
    }

    /** 拉回速度必须**恰好**等于每秒 10 格（需求："常规下为10格每秒"）。 */
    @Test
    public void pullSpeedMatchesTenBlocksPerSecond() {
        assertEquals("每秒 10 格 ⇒ 每刻 0.5 格", 0.5d, HunterPullSkill.PULL_SPEED_PER_TICK, 1.0E-9d);
        assertEquals("20 刻的位移必须恰好 10 格",
                10d, HunterPullSkill.PULL_SPEED_PER_TICK * 20d, 1.0E-9d);
    }

    /**
     * **加速拖拽**：不按 = 常规 10 格/秒；**按一次即封顶 20 格/秒**
     * （需求："常规下为10格每秒，再次释放为20格每秒" —— 只有"再次释放"这一档）。
     */
    @Test
    public void acceleratedPullSpeedScalesByStepAndCaps() {
        assertEquals("不按 = 常规速度（每秒 10 格）",
                10d, HunterPullSkill.acceleratedPullSpeed(0) * 20d, 1.0E-9d);
        assertEquals("按 1 次 = 每秒 20 格（需求：再次释放为 20 格/秒）",
                20d, HunterPullSkill.acceleratedPullSpeed(1) * 20d, 1.0E-9d);
        assertEquals("按 2 次仍封顶在每秒 20 格",
                20d, HunterPullSkill.acceleratedPullSpeed(2) * 20d, 1.0E-9d);
        assertEquals("按 8 次仍封顶",
                20d, HunterPullSkill.acceleratedPullSpeed(8) * 20d, 1.0E-9d);
        assertEquals("负数按 0 次处理（不抛、不回退速度）",
                10d, HunterPullSkill.acceleratedPullSpeed(-5) * 20d, 1.0E-9d);
    }

    /** 拉回时长上限必须**恰好 2.5 秒**（需求："最多只能拉着敌人2.5S"）。 */
    @Test
    public void pullLastsAtMostTwoAndAHalfSeconds() {
        assertEquals("2.5 秒 = 50 刻", 50, HunterPullSkill.PULL_MAX_TICKS);
        assertEquals("换算成秒必须恰好 2.5",
                2.5d, HunterPullSkill.PULL_MAX_TICKS / 20d, 1.0E-9d);
    }

    // ───────── ⑧ 死亡后不再被继续拖拽（需求："死亡后不再会被继续控制拖拽"）─────────

    /**
     * 判据 = 在线 && 未死 && 血量 > 0；三条缺一条就必须**不再**拖。
     * <p>这个判据在 `resolveThrust` / `update` / `beginPull` / `stepPull`（写坐标前）四处共用 ——
     * 任一处漏了都会出现"拖着尸体走"。
     */
    @Test
    public void draggableRequiresOnlineAliveAndPositiveHealth() {
        assertTrue("活着且在线有血 ⇒ 可拖",
                HunterPullSkill.isDraggable(true, false, 20d));

        assertFalse("已死亡（血量归零）⇒ 不可拖",
                HunterPullSkill.isDraggable(true, true, 0d));
        assertFalse("血量归零但 isDead 还没翻 ⇒ 也不可拖（两侧都判，不赌引擎时点）",
                HunterPullSkill.isDraggable(true, false, 0d));
        assertFalse("血量是负数（某些来源会写成负）⇒ 不可拖",
                HunterPullSkill.isDraggable(true, false, -3d));
        assertFalse("掉线 ⇒ 不可拖",
                HunterPullSkill.isDraggable(false, false, 20d));
        assertFalse("掉线 + 死亡 ⇒ 不可拖",
                HunterPullSkill.isDraggable(false, true, 0d));
    }

    /** 死亡判定必须是"血量为 0"就成立 —— 哪怕 `isDead()` 还没翻（不赌引擎的更新时点）。 */
    @Test
    public void draggableTreatsZeroHealthAsDeadEvenIfFlagLags() {
        assertFalse("血量为 0 ⇒ 直接视为不可拖",
                HunterPullSkill.isDraggable(true, false, 0d));
        assertTrue("血量 0.5（还没死）⇒ 仍可拖",
                HunterPullSkill.isDraggable(true, false, 0.5d));
    }

    // ───────── ⑦ 击杀去重（需求："敌人死亡后只加一层，现在会加两层"）─────────

    @Test
    public void duplicateKillSameVictimSameTickIsDeduped() {
        UUID victim = UUID.randomUUID();
        assertTrue("同一刻、同一敌人 ⇒ 判为重复投递（只算一层）",
                HunterEvolutionPassive.isDuplicateKill(victim, 1234, victim, 1234));
    }

    @Test
    public void killDedupeDistinguishesVictimAndTick() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        assertFalse("同一刻、不同敌人 ⇒ 两次真实击杀，都要算",
                HunterEvolutionPassive.isDuplicateKill(a, 1234, b, 1234));
        assertFalse("不同刻、同一敌人（复活后再死）⇒ 新的击杀，要算",
                HunterEvolutionPassive.isDuplicateKill(a, 1234, a, 1235));
        assertFalse("还没算过（last = null）⇒ 不是重复",
                HunterEvolutionPassive.isDuplicateKill(null, Integer.MIN_VALUE, a, 7));
    }

    @Test
    public void killDedupeDoesNotSwallowWhenVictimUnknown() {
        UUID a = UUID.randomUUID();
        assertFalse("载荷没带敌人 ⇒ 不去重（宁可按一次击杀算，也不吞掉玩家的层数）",
                HunterEvolutionPassive.isDuplicateKill(a, 5, null, 5));
    }

    // ───────── ⑤ 标记集合（多标记 + 消费语义）─────────

    /**
     * 标记集合的**空集语义**与**空安全**：还没标记过任何敌人时
     * 计数为 0、查询与消费都回 {@code false}，且传 {@code null} 不抛。
     *
     * <p>★ 边界如实申报：真实"标记 → 消费一次 → 再消费拿不到"这条链路需要
     * {@code Bukkit.getPlayer(...)} 与真实世界（{@link HunterPreyPassive#update()} 里索敌），
     * **离线不可测** —— 这里只钉住不依赖 Bukkit 的那一半。
     */
    @Test
    public void markSetStartsEmptyAndIsNullSafe() {
        HunterPreyPassive prey = new HunterPreyPassive("t_hunter_prey",
                new ComponentServicesPort(null, null, null), new HunterPreyPassive.Specification());

        assertEquals("尚未标记任何敌人时计数为 0", 0, prey.markedCount());
        assertFalse("没人被标记", prey.isMarked(java.util.UUID.randomUUID()));
        assertFalse("消费一个不存在的标记必须回 false（否则主武器会白给额外伤害）",
                prey.consumeMark(java.util.UUID.randomUUID()));
        assertFalse("null 不是被标记的对象", prey.isMarked(null));
        assertFalse("消费 null 必须回 false（空安全）", prey.consumeMark(null));
        assertEquals("以上调用都不该改动计数", 0, prey.markedCount());
    }

    // ───────── ⑥ 进化档位 ─────────

    /** 造一个"服务集为空"的进化被动实例（构造器只存字段，不碰 Bukkit）。 */
    private static HunterEvolutionPassive evolution() {
        return new HunterEvolutionPassive("t_hunter_evolution", new ComponentServicesPort(null, null, null),
                new HunterEvolutionPassive.Specification());
    }

    /** 五档各有文案；0 与 6 没有（"没有这一档"可判定，而不是回空串让人猜）。 */
    @Test
    public void tierTextsCoverExactlyFiveTiers() {
        for (int level = 1; level <= HunterEvolutionPassive.MAX_EVOLUTION_LEVEL; level++) {
            assertNotNull("第 " + level + " 档必须有文案", HunterEvolutionPassive.tierTextOf(level));
            assertTrue("第 " + level + " 档文案必须以档位号开头",
                    HunterEvolutionPassive.tierTextOf(level).startsWith(level + "-"));
        }
        assertNull("第 0 档不存在", HunterEvolutionPassive.tierTextOf(0));
        assertNull("第 6 档不存在（封顶 5）", HunterEvolutionPassive.tierTextOf(6));
        assertEquals("封顶必须是 5", 5, HunterEvolutionPassive.MAX_EVOLUTION_LEVEL);
    }

    /** 升级消息 = {@code "[进化]  "} 前缀 + 档位文案（前缀两个空格，与工程既有口径一致）。 */
    @Test
    public void evolutionMessageCarriesTagAndTierText() {
        String message = HunterEvolutionPassive.evolutionMessageOf(3);
        assertNotNull("第 3 档消息必须存在", message);
        assertTrue("消息必须以 [进化] + 两个空格开头，实际 = " + message,
                message.startsWith("[进化]  "));
        assertTrue("消息必须含该档文案", message.endsWith(HunterEvolutionPassive.tierTextOf(3)));
        assertNull("超出档位的消息回 null", HunterEvolutionPassive.evolutionMessageOf(9));
    }

    /**
     * 五档对三个消费组件的**生效值**逐档推进（每档只翻自己那一项，别的档不动）。
     * <p>这是需求与代码之间最容易被改漂的一处，因此穷举 0~5 级逐档断言。
     */
    @Test
    public void tierEffectsSwitchOnAtTheirOwnLevel() {
        HunterEvolutionPassive evolution = evolution();

        //0 级：什么都没有
        assertEquals("0 级不该有技能 CD 减免", 60, evolution.adjustedSkillCooldownTicks(60));
        assertEquals("0 级不该回复 TE", 0, evolution.grudgeSanTERestore());
        assertEquals("0 级遁形时长 = 声明值 15 秒", 300, evolution.stealthDurationTicks(300));
        assertFalse("0 级没有灵魂脉冲", evolution.stealthSoulPulse());

        evolution.increaseEvolutionLevel();                 // 1 级
        assertEquals("1 级只给生命恢复，CD 不动", 60, evolution.adjustedSkillCooldownTicks(60));

        evolution.increaseEvolutionLevel();                 // 2 级
        assertEquals("2 级只给抗性，CD 不动", 60, evolution.adjustedSkillCooldownTicks(60));

        evolution.increaseEvolutionLevel();                 // 3 级
        assertEquals("3 级起所有技能 CD 减少 1 秒（60 - 20 = 40）",
                40, evolution.adjustedSkillCooldownTicks(60));
        assertEquals("缩短后不得低于 1 刻（否则短 CD 技能会变成无冷却）",
                1, evolution.adjustedSkillCooldownTicks(10));
        assertEquals("声明值 ≤ 0 时原样返回（无冷却这回事）",
                0, evolution.adjustedSkillCooldownTicks(0));
        assertEquals("3 级还没到 TE 回复档", 0, evolution.grudgeSanTERestore());

        evolution.increaseEvolutionLevel();                 // 4 级
        assertEquals("4 级起 [遗愤] 攻击回复 5 点 TE", 5, evolution.grudgeSanTERestore());
        assertEquals("4 级遁形时长不变", 300, evolution.stealthDurationTicks(300));

        evolution.increaseEvolutionLevel();                 // 5 级
        assertEquals("5 级遁形时长缩短为 6 秒", 120, evolution.stealthDurationTicks(300));
        assertTrue("5 级起开启灵魂脉冲", evolution.stealthSoulPulse());
        assertEquals("5 级 CD 减免仍在", 40, evolution.adjustedSkillCooldownTicks(60));
    }

    // ───────── ⑦ 装配注册 ─────────

    /**
     * **猎手必须真的在注册表里，且六个组件齐全**。
     * <p>单独钉住以防"忘了在 {@code defaultDefinitions()} 加一行"—— 那种漏写在其它测试里没有任何表现
     * （症状是"编译过、测试过、进游戏选不到这个角色"）。
     */
    @Test
    public void hunterIsRegisteredWithSixComponents() {
        RoleLoader.Definition hunter = null;
        for (RoleLoader.Definition definition : new RoleLoader(Logger.getLogger("HunterMechanicsTest"))
                .defaultDefinitions()) {
            if ("hunter".equals(definition.id())) {
                hunter = definition;
                break;
            }
        }
        assertNotNull("defaultDefinitions() 里必须有 id = \"hunter\" 的角色（漏了 ⇒ 进游戏选不到猎手）", hunter);

        Role role = hunter.builder().get().build();
        role.verifyDependencies();
        assertTrue("猎手缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());

        Set<String> present = role.getComponents().keySet();
        for (String id : List.of(
                "hunter_mainWeapon_grudge",
                "hunter_skill_pounce",
                "hunter_skill_pullback",
                "hunter_skill_stealth",
                "hunter_passive_prey",
                "hunter_evolution_passive")) {
            assertTrue("猎手缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        //★ 内建条数**从注册表推导**，不写死：上游 2026-10-02 把内建从 7 个加到 9 个，
        //  写死的总数让本用例误报过一次 —— "框架有几个内建"不是本用例要管的事。
        //  基准取 selfUpdateExample：它 = 内建 + 自己的 1 个示例组件。
        int builtIns = 0;
        for (RoleLoader.Definition definition : new RoleLoader(Logger.getLogger("HunterMechanicsTest"))
                .defaultDefinitions()) {
            if ("selfUpdateExample".equals(definition.id())) {
                builtIns = definition.builder().get().build().getComponents().size() - 1;
            }
        }
        assertTrue("注册表里必须有 selfUpdateExample（内建基准）", builtIns > 0);
        assertEquals("猎手应有的组件条数（内建 " + builtIns + " + 6 角色）；实际 = " + present,
                builtIns + 6, role.getComponents().size());
    }
}
