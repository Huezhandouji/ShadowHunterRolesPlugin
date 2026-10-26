package com.shadowHunterRolesPlugin.roleComponent.custom.raven;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.mainWeapon.RavenRevolverMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenAirMobilityPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill.RavenDeathEyeSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill.RavenSkyStrikeSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill.RavenSomersaultSkill;
import org.bukkit.NamespacedKey;
import org.bukkit.util.Vector;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「绯红渡鸦 · 林晨霜」的**离线纯函数闸门**。
 *
 * <h2>为什么单测只打纯函数</h2>
 * 本角色的绝大多数逻辑要 Bukkit 运行环境（活玩家、活世界、真粒子）。真正会**静默出错**、
 * 且**只能在离线被抓住**的，是那批"被抽出来的纯函数"：悬停的水平 / 垂直速度、
 * 双击空格的边沿判据、按蹲下落的加速曲线、跃起的放松判据、落地冲击的高度边界、
 * 机动充能的闸门、锁定轮转的取模、锁定闪烁的节拍、阶段迁移表、俯冲落点的外推、
 * 弹药→耐久的换算、装备属性的换算。它们写错时**都不报错**，只是"手感不对 / 数值差一点"。
 *
 * <h2>★ 离线测试必须先装 KeyFactory 桩</h2>
 * 本测试类会加载 {@link RavenEquipmentsPassive} / {@link RavenRevolverMainWeapon}，
 * 两者的 {@code static final NamespacedKey} 走 {@code KeyFactory.Registry.of(...)}；
 * 没人 install 时抛 {@code IllegalStateException}，而 **JVM 会缓存类初始化失败**
 * ⇒ 之后碰它们的代码全变 {@code NoClassDefFoundError}（症状极隐蔽）。
 */
public class RavenMechanicsTest {

    /** 离线测试基设：先装一个最小 {@code KeyFactory} 桩（幂等）。 */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    // ───────── 浮空机动：垂直速度 ─────────

    @Test
    public void 悬停_什么都不按是真悬停() {
        assertEquals("不按跳也不按蹲 ⇒ 垂直速度 0（真悬停；重力已关，不写速度就不动）",
                0d, RavenAirMobilityPassive.hoverVerticalVelocity(false, false, 0), 1.0E-9d);
    }

    @Test
    public void 悬停_按跳键缓慢上升() {
        double rise = RavenAirMobilityPassive.hoverVerticalVelocity(true, false, 0);
        assertTrue("按跳键必须向上，实际 = " + rise, rise > 0d);
        assertTrue("上升必须缓慢（不超过 0.3 格/刻 = 6 格/秒），实际 = " + rise, rise <= 0.3d);
    }

    @Test
    public void 悬停_按蹲键是加速下落而不是解除悬停() {
        //★ 2026-10-11 第二轮口径：按蹲**不结束悬停**（否则战斗中随手按 Shift 就会掉出浮空），
        //  它只让垂直速度向下、且越久越快；降到地面由"触地"那条路径结束悬停
        double first = RavenAirMobilityPassive.hoverVerticalVelocity(false, true, 0);
        double later = RavenAirMobilityPassive.hoverVerticalVelocity(false, true, 12);
        assertTrue("按蹲必须向下，实际 = " + first, first < 0d);
        assertTrue("按得越久下得越快（" + first + " → " + later + "）", later < first);
        assertEquals("封顶 = -最大下沉速度",
                -RavenAirMobilityPassive.FAST_FALL_MAX_SINK,
                RavenAirMobilityPassive.hoverVerticalVelocity(false, true, 10000), 1.0E-9d);
    }

    @Test
    public void 悬停_蹲与跳同时按取蹲() {
        assertEquals("同时按下时取「想下去」",
                RavenAirMobilityPassive.hoverVerticalVelocity(false, true, 0),
                RavenAirMobilityPassive.hoverVerticalVelocity(true, true, 0), 1.0E-9d);
    }

    @Test
    public void 悬停_垂直速度三个分支穷举() {
        //跳 → 升；蹲 → 降；都不按 → 停。三态互斥、且"停"是精确 0
        assertTrue(RavenAirMobilityPassive.hoverVerticalVelocity(true, false, 0) > 0d);
        assertTrue(RavenAirMobilityPassive.hoverVerticalVelocity(false, true, 0) < 0d);
        assertEquals(0d, RavenAirMobilityPassive.hoverVerticalVelocity(false, false, 5), 1.0E-9d);
    }

    // ───────── 悬停水平速度（2026-10-11 提速 3 倍 + 无状态算法）─────────

    @Test
    public void 悬停水平_不按方向键就停住() {
        Vector v = RavenAirMobilityPassive.hoverHorizontalVelocity(0, 0, 0d,
                RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED);
        assertEquals("不按键 ⇒ 零向量（悬停不该有惯性）", 0d, v.length(), 1.0E-9d);
    }

    @Test
    public void 悬停水平_朝向0度时前进朝正Z() {
        //yaw 0 = 南（+Z）：前进键 ⇒ +Z
        Vector v = RavenAirMobilityPassive.hoverHorizontalVelocity(1, 0, 0d,
                RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED);
        assertEquals("前进必须走 +Z", 1d,
                v.getZ() / RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED, 1.0E-6d);
        assertEquals("不该有 X 分量", 0d, v.getX(), 1.0E-6d);
    }

    @Test
    public void 悬停水平_朝向0度时左移朝正X() {
        //yaw 0 = 南（+Z）：面朝南时左手边是东（+X）
        Vector v = RavenAirMobilityPassive.hoverHorizontalVelocity(0, 1, 0d,
                RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED);
        assertEquals("左移必须走 +X", 1d,
                v.getX() / RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED, 1.0E-6d);
        assertEquals("不该有 Z 分量", 0d, v.getZ(), 1.0E-6d);
    }

    @Test
    public void 悬停水平_对角不会更快() {
        double speed = RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED;
        Vector v = RavenAirMobilityPassive.hoverHorizontalVelocity(1, 1, 30d, speed);
        assertEquals("斜着走必须仍是 speed（已归一化），不能 √2 倍", speed, v.length(), 1.0E-6d);
    }

    @Test
    public void 悬停水平_幅度恒等于常量且不会自乘放大() {
        //★ 这是"无状态算法"要守住的性质：同样的输入永远给同样的输出
        double speed = RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED;
        Vector first = RavenAirMobilityPassive.hoverHorizontalVelocity(1, 0, 45d, speed);
        Vector second = RavenAirMobilityPassive.hoverHorizontalVelocity(1, 0, 45d, speed);
        assertEquals("两次调用必须完全一致（不会指数放大）", first.length(), second.length(), 1.0E-12d);
        assertEquals(speed, first.length(), 1.0E-6d);
    }

    @Test
    public void 悬停水平_是常态步速的三倍() {
        assertEquals("需求：「加速 3 倍悬停水平移动速度」", 3d,
                RavenAirMobilityPassive.HOVER_SPEED_MULTIPLIER, 1.0E-9d);
        assertEquals("常量与倍数必须自洽（≈3 × 0.2 格/刻）",
                RavenAirMobilityPassive.HOVER_SPEED_MULTIPLIER * 0.2d,
                RavenAirMobilityPassive.HOVER_HORIZONTAL_SPEED, 1.0E-9d);
    }

    // ───────── 双击空格 / 按蹲快速下落（2026-10-11 新增）─────────

    @Test
    public void 双击空格_窗口内算双击() {
        assertTrue("第 3 刻再按（窗口 4 刻）⇒ 算双击",
                RavenAirMobilityPassive.isDoubleTapJump(103, 100, true, false));
        assertTrue("刚好窗口边界 ⇒ 算双击",
                RavenAirMobilityPassive.isDoubleTapJump(104, 100, true, false));
    }

    @Test
    public void 双击空格_超窗口或没松手都不算() {
        assertFalse("超出 0.2 秒 ⇒ 不算双击",
                RavenAirMobilityPassive.isDoubleTapJump(105, 100, true, false));
        assertFalse("一直按住（没有边沿）⇒ 不算双击",
                RavenAirMobilityPassive.isDoubleTapJump(103, 100, true, true));
        assertFalse("没按 ⇒ 不算",
                RavenAirMobilityPassive.isDoubleTapJump(103, 100, false, false));
        assertFalse("本命第一次按 ⇒ 不算",
                RavenAirMobilityPassive.isDoubleTapJump(103, -1, true, false));
    }

    @Test
    public void 快速下落_按得越久落得越快且封顶() {
        double first = RavenAirMobilityPassive.fastFallSink(0);
        double later = RavenAirMobilityPassive.fastFallSink(10);
        assertTrue("起始必须向下", first > 0d);
        assertTrue("按得越久越快（" + first + " → " + later + "）", later > first);
        assertEquals("封顶在上限", RavenAirMobilityPassive.FAST_FALL_MAX_SINK,
                RavenAirMobilityPassive.fastFallSink(10000), 1.0E-9d);
        assertEquals("负数刻按 0 处理", RavenAirMobilityPassive.fastFallSink(-5),
                RavenAirMobilityPassive.fastFallSink(0), 1.0E-9d);
    }

    // ───────── 跃起判定（2026-10-11 放松）─────────

    @Test
    public void 跃起判定_身下一点五格内有方块即可() {
        assertTrue("贴地 ⇒ 可跃起", RavenAirMobilityPassive.canTakeOff(false, 0d));
        assertTrue("离地 0.5 格（半砖上）⇒ 可跃起", RavenAirMobilityPassive.canTakeOff(false, 0.5d));
        assertTrue("刚好 1.5 格 ⇒ 可跃起（需求边界）", RavenAirMobilityPassive.canTakeOff(false, 1.5d));
        assertFalse("离地 1.6 格 ⇒ 不可跃起", RavenAirMobilityPassive.canTakeOff(false, 1.6d));
        assertFalse("脚那格实心（嵌在方块里）⇒ 不可跃起", RavenAirMobilityPassive.canTakeOff(true, 0d));
    }

    @Test
    public void 跃起判定_比旧口径更宽松() {
        //★ 反证：旧口径 = 「正下方那一格必须实心」⇒ 离地 0.2 格 / 站在半砖上时判不出来
        //  ——这正是"右键有时候不灵敏"的根因；新口径必须在这种情形通过
        assertTrue("新口径在「离地一点点」时必须通过",
                RavenAirMobilityPassive.canTakeOff(false, 0.2d));
    }

    // ───────── 浮空进度条（2026-10-11："少一秒就 ■■□"）─────────

    @Test
    public void 浮空进度条_少一秒就少一格() {
        //需求原话："用 ■■■ 的进度条来显示，少一秒就 ■■□"
        assertEquals("满 3.5 秒 ⇒ ■■■", "■■■", RavenAirMobilityPassive.hoverBar(70));
        assertEquals("剩 2.5 秒 ⇒ ■■□", "■■□", RavenAirMobilityPassive.hoverBar(50));
        assertEquals("剩 1.5 秒 ⇒ ■□□", "■□□", RavenAirMobilityPassive.hoverBar(30));
        assertEquals("剩 0.5 秒 ⇒ □□□", "□□□", RavenAirMobilityPassive.hoverBar(10));
        assertEquals("刚归零 ⇒ □□□", "□□□", RavenAirMobilityPassive.hoverBar(0));
    }

    @Test
    public void 浮空进度条_边界与越界() {
        assertEquals("刚好 3 秒（60 刻）⇒ 仍是 ■■■（整秒数）", "■■■",
                RavenAirMobilityPassive.hoverBar(60));
        assertEquals("59 刻（差 1 刻到 3 秒）⇒ 掉一格", "■■□",
                RavenAirMobilityPassive.hoverBar(59));
        assertEquals("超过满时长也只是 ■■■（不越界）", "■■■",
                RavenAirMobilityPassive.hoverBar(9999));
        assertEquals("负数按 0 处理", "□□□", RavenAirMobilityPassive.hoverBar(-5));
    }

    @Test
    public void 浮空进度条_段数就是三() {
        assertEquals("浮空 3.5 秒 ⇒ 最多 3 段", 3, RavenAirMobilityPassive.HOVER_BAR_SEGMENTS);
        assertEquals("每段 1 秒、段数与时长自洽",
                RavenAirMobilityPassive.HOVER_BAR_SEGMENTS,
                RavenAirMobilityPassive.HOVER_DURATION_TICKS / 20);
        assertEquals("长度恒为段数", RavenAirMobilityPassive.HOVER_BAR_SEGMENTS,
                RavenAirMobilityPassive.hoverBar(9999).length());
    }

    // ───────── 大招落点标记的显示门限（2026-10-11）─────────

    @Test
    public void 大招落点标记_只有冷却走完且可释放才显示() {
        //需求原话："应该是冷却完全可以释放时，此技能才能手持显现落地位置"
        assertTrue("手持 + 没冷却 + 可释放 ⇒ 显示",
                RavenSomersaultSkill.shouldShowLandingMarker(true, false, true));
        assertFalse("没拿在手上 ⇒ 不显示",
                RavenSomersaultSkill.shouldShowLandingMarker(false, false, true));
        assertFalse("冷却中 ⇒ 不显示",
                RavenSomersaultSkill.shouldShowLandingMarker(true, true, true));
        assertFalse("闸门不允许（眩晕/沉默）⇒ 不显示",
                RavenSomersaultSkill.shouldShowLandingMarker(true, false, false));
        assertFalse("三条全不满足 ⇒ 不显示",
                RavenSomersaultSkill.shouldShowLandingMarker(false, true, false));
    }

    // ───────── 浮空机动：常量语义 ─────────

    @Test
    public void 浮空持续时长是三点五秒() {
        assertEquals("需求：「持续 3.5 秒」⇒ 70 刻", 70, RavenAirMobilityPassive.HOVER_DURATION_TICKS);
    }

    @Test
    public void 机动充能上限是三() {
        assertEquals("需求：「可最高存储 3 次」", 3, RavenAirMobilityPassive.MAX_CHARGES);
    }

    @Test
    public void 机动充能周期是十五秒且耗十能量() {
        assertEquals("需求：「每 15 秒」⇒ 300 刻", 300, RavenAirMobilityPassive.CHARGE_REGEN_TICKS);
        assertEquals("需求：「消耗 10 能量」", 10, RavenAirMobilityPassive.CHARGE_REGEN_ENERGY_COST);
    }

    @Test
    public void 空中机动间隔缩到一秒() {
        assertEquals("需求 2026-10-11：「机动冲刺使用间隔缩减至 1S」⇒ 20 刻",
                20, RavenAirMobilityPassive.AIR_DASH_INTERVAL_TICKS);
    }

    // ───────── 落地冲击 ─────────

    @Test
    public void 落地冲击_一米五及以上才触发() {
        assertTrue("刚好 1.5 格 ⇒ 触发（需求：「1.5 格及以上」）",
                RavenAirMobilityPassive.shouldImpactOnLanding(1.5d));
        assertTrue("6 格 ⇒ 触发", RavenAirMobilityPassive.shouldImpactOnLanding(6d));
        assertFalse("没武装过（0）⇒ 不触发", RavenAirMobilityPassive.shouldImpactOnLanding(0d));
        assertFalse("1.49 格 ⇒ 不触发", RavenAirMobilityPassive.shouldImpactOnLanding(1.49d));
    }

    @Test
    public void 落地冲击_半径伤害与击退数值() {
        assertEquals(5.0d, RavenAirMobilityPassive.LANDING_IMPACT_RADIUS, 1.0E-9d);
        assertEquals(10d, RavenAirMobilityPassive.LANDING_IMPACT_DAMAGE, 1.0E-9d);
        assertEquals(3, RavenAirMobilityPassive.LANDING_IMPACT_SANTE_MIN);
        assertEquals(6, RavenAirMobilityPassive.LANDING_IMPACT_SANTE_MAX);
        assertTrue("需求 2026-10-11：「落地冲击还会击退附近的敌人」⇒ 击退强度必须为正",
                RavenAirMobilityPassive.LANDING_IMPACT_KNOCKBACK > 0d);
    }

    // ───────── 机动充能：不该白扣能量 ─────────

    @Test
    public void 充能_满仓时不该消耗能量() {
        assertFalse("满 3 层 ⇒ 不充能（也就不能扣能量）",
                RavenAirMobilityPassive.shouldRegenCharge(3, 3, 100));
    }

    @Test
    public void 充能_能量刚好够才充() {
        assertTrue("2 层 + 能量 10 ⇒ 充", RavenAirMobilityPassive.shouldRegenCharge(2, 3, 10));
        assertFalse("2 层 + 能量 9 ⇒ 不充（不欠账）", RavenAirMobilityPassive.shouldRegenCharge(2, 3, 9));
        assertFalse("2 层 + 能量 0 ⇒ 不充", RavenAirMobilityPassive.shouldRegenCharge(2, 3, 0));
    }

    // ───────── 落地的几何判据 ─────────

    @Test
    public void 触地判据_贴着地面才算() {
        assertTrue("离地 0 ⇒ 踩在地上", RavenAirMobilityPassive.isTouchingGround(0d));
        assertTrue("半砖上（0.5）⇒ 仍算踩在地上", RavenAirMobilityPassive.isTouchingGround(0.5d));
        assertFalse("离地 1.0 格 ⇒ 不算踩地（★ 这正是旧「整格判据」误判的区间）",
                RavenAirMobilityPassive.isTouchingGround(1.0d));
        assertFalse("悬停在空中 ⇒ 不算踩地", RavenAirMobilityPassive.isTouchingGround(6d));
    }

    @Test
    public void 触地判据_阈值必须比整格判据更紧() {
        //★ 反证：整格判据（脚空 + 下方实心）在离地 ~1 格时就成立
        //  ⇒ 用离地 0.9 的姿态进入浮空会被立刻判成落地（浮空瞬间消失）
        //  新判据必须把 0.9 判成"未触地"，否则这条修复就白做了
        assertFalse("离地 0.9 格 ⇒ 未触地（否则浮空会瞬间消失）",
                RavenAirMobilityPassive.isTouchingGround(0.9d));
        assertTrue("阈值本身必须小于 1 格（整格判据的松紧度）",
                RavenAirMobilityPassive.GROUND_TOUCH_HEIGHT < 1.0d);
        assertTrue("阈值要能覆盖半砖（0.5）",
                RavenAirMobilityPassive.GROUND_TOUCH_HEIGHT >= 0.5d);
    }

    @Test
    public void 地面扫描_找到实心方块取顶面() {
        //方块占 [by, by+1) ⇒ 顶面 = by + 1；从 100 往下扫，第一个实心在 95 ⇒ 96
        assertEquals(96d, RavenAirMobilityPassive.groundLevelY(100, -64, by -> by <= 95), 1.0E-9d);
    }

    @Test
    public void 地面扫描_虚空回最低高度() {
        assertEquals("整列没有实心方块 ⇒ 回世界的 minHeight",
                -64d, RavenAirMobilityPassive.groundLevelY(100, -64, by -> false), 1.0E-9d);
    }

    // ───────── 锁定：轮转 / 视锥 / 闪烁 ─────────

    @Test
    public void 锁定轮转_三人以内按序取() {
        assertEquals(0, RavenRevolverMainWeapon.lockShotIndex(0, 3));
        assertEquals(1, RavenRevolverMainWeapon.lockShotIndex(1, 3));
        assertEquals(2, RavenRevolverMainWeapon.lockShotIndex(2, 3));
    }

    @Test
    public void 锁定轮转_人数不够时从头再轮() {
        assertEquals(0, RavenRevolverMainWeapon.lockShotIndex(0, 2));
        assertEquals(1, RavenRevolverMainWeapon.lockShotIndex(1, 2));
        assertEquals("第 3 发（下标 2）⇒ 回到第一个人", 0, RavenRevolverMainWeapon.lockShotIndex(2, 2));
        assertEquals("只有一个人 ⇒ 三发都打他",
                RavenRevolverMainWeapon.lockShotIndex(0, 1), RavenRevolverMainWeapon.lockShotIndex(2, 1));
    }

    @Test
    public void 锁定轮转_没有人时返回负一() {
        assertEquals("无候选 ⇒ -1（调用方据此改打准心）", -1,
                RavenRevolverMainWeapon.lockShotIndex(0, 0));
        assertEquals(-1, RavenRevolverMainWeapon.lockShotIndex(5, -3));
    }

    @Test
    public void 锁定视锥_边界() {
        assertTrue("刚好半角 ⇒ 在视野内",
                RavenRevolverMainWeapon.withinLockFov(RavenRevolverMainWeapon.LOCK_FOV_HALF_DEGREES));
        assertTrue("正前方 0° ⇒ 在视野内", RavenRevolverMainWeapon.withinLockFov(0d));
        assertFalse("超出半角 ⇒ 不在视野内",
                RavenRevolverMainWeapon.withinLockFov(RavenRevolverMainWeapon.LOCK_FOV_HALF_DEGREES + 0.01d));
    }

    @Test
    public void 锁定闪烁_周期是零点二五秒() {
        assertEquals("需求 2026-10-11：「0.25 秒闪烁一次」⇒ 周期 5 刻",
                5, RavenRevolverMainWeapon.LOCK_BLINK_PERIOD_TICKS);
        assertTrue("周期起点必须亮（否则第一眼看不到标记）", RavenRevolverMainWeapon.isLockBlinkOn(0));
        assertFalse("亮够之后必须灭", RavenRevolverMainWeapon.isLockBlinkOn(4));
        assertTrue("下一个周期再亮", RavenRevolverMainWeapon.isLockBlinkOn(5));
    }

    @Test
    public void 锁定闪烁_一个周期内亮的刻数正确且必须是少数() {
        int onCount = 0;
        for (int tick = 0; tick < RavenRevolverMainWeapon.LOCK_BLINK_PERIOD_TICKS; tick++) {
            if (RavenRevolverMainWeapon.isLockBlinkOn(tick)) {
                onCount++;
            }
        }
        assertEquals("一个周期里亮的刻数 = 常量",
                RavenRevolverMainWeapon.LOCK_BLINK_ON_TICKS, onCount);
        assertTrue("必须亮得比灭得少才算闪",
                RavenRevolverMainWeapon.LOCK_BLINK_ON_TICKS * 2
                        <= RavenRevolverMainWeapon.LOCK_BLINK_PERIOD_TICKS);
    }

    @Test
    public void 锁定闪烁_负数刻不炸() {
        assertEquals("负数刻按 floorMod 处理，与同余正数一致",
                RavenRevolverMainWeapon.isLockBlinkOn(0), RavenRevolverMainWeapon.isLockBlinkOn(-5));
    }

    @Test
    public void 锁定射击_实际能射几发() {
        assertEquals("请求 3 发、弹药 6 ⇒ 3", 3, RavenRevolverMainWeapon.shotsAffordable(3, 6));
        assertEquals("请求 3 发、弹药 1 ⇒ 1（不能凭空多射）", 1, RavenRevolverMainWeapon.shotsAffordable(3, 1));
        assertEquals("弹药 0 ⇒ 0", 0, RavenRevolverMainWeapon.shotsAffordable(3, 0));
        assertEquals("负数请求 ⇒ 0", 0, RavenRevolverMainWeapon.shotsAffordable(-1, 6));
    }

    // ───────── 主武器：射击 / 弹药 / 耐久 ─────────

    @Test
    public void 弹药回复_满仓或能量不足都不回复() {
        int cost = RavenRevolverMainWeapon.AMMO_REGEN_ENERGY_COST;
        assertTrue("不满仓 + 能量刚好够 ⇒ 回复", RavenRevolverMainWeapon.shouldRegenAmmo(5, 6, cost));
        assertFalse("满仓 ⇒ 不回复", RavenRevolverMainWeapon.shouldRegenAmmo(6, 6, 100));
        assertFalse("能量差一点 ⇒ 不回复", RavenRevolverMainWeapon.shouldRegenAmmo(5, 6, cost - 1));
    }

    @Test
    public void 弹药相关的耗能都改为三() {
        //需求 2026-10-11：自动回复一颗 2 → 3；随后换弹每发 2 → 3
        assertEquals("自动回复 1 枚 = 3 能量",
                3, RavenRevolverMainWeapon.AMMO_REGEN_ENERGY_COST);
        assertEquals("地面换弹每发 = 3 能量",
                3, RavenRevolverMainWeapon.RELOAD_ENERGY_PER_BULLET);
        assertEquals("★ 技能二的「瞬间补齐子弹」是同一件事 ⇒ 与主武器同口径",
                3, RavenDeathEyeSkill.RELOAD_ENERGY_PER_BULLET);
        assertEquals("★ 两条账都还是独立常量（只是当前取同值，将来可分别调）",
                RavenRevolverMainWeapon.AMMO_REGEN_ENERGY_COST,
                RavenRevolverMainWeapon.RELOAD_ENERGY_PER_BULLET);
    }

    @Test
    public void 装备_能量低于五十时速度消失() {
        //需求 2026-10-11："当能量低于 50 时，角色速度 2 消失"
        assertEquals("门槛就是 50", 50, RavenEquipmentsPassive.SPEED_ENERGY_THRESHOLD);
        assertFalse("49 ⇒ 低于门槛 ⇒ 没有速度",
                RavenEquipmentsPassive.shouldHavePermanentSpeed(49));
        assertTrue("刚好 50 ⇒ 仍有速度（需求写的是「低于 50」）",
                RavenEquipmentsPassive.shouldHavePermanentSpeed(50));
        assertTrue("51 ⇒ 有速度", RavenEquipmentsPassive.shouldHavePermanentSpeed(51));
        assertFalse("0 ⇒ 没有速度", RavenEquipmentsPassive.shouldHavePermanentSpeed(0));
        assertTrue("满能量 ⇒ 有速度", RavenEquipmentsPassive.shouldHavePermanentSpeed(100));
    }

    @Test
    public void 击发闸门_有弹且过了间隔才可击发() {
        assertTrue(RavenRevolverMainWeapon.canFire(1, 100, 100));
        assertFalse("没弹药 ⇒ 打不出", RavenRevolverMainWeapon.canFire(0, 100, 0));
        assertFalse("间隔未到 ⇒ 打不出", RavenRevolverMainWeapon.canFire(3, 99, 100));
    }

    @Test
    public void 耐久表达弹药_满弹不掉耐久空仓掉到极限() {
        int max = 465;      //原版弩的耐久上限
        assertEquals("满弹 ⇒ 伤害 0（耐久条满）", 0, RavenRevolverMainWeapon.ammoDamage(6, 6, max));
        assertEquals("空仓 ⇒ 伤害 = 上限 − 1（取上限会被当成已损坏）",
                max - 1, RavenRevolverMainWeapon.ammoDamage(0, 6, max));
        assertEquals("一半弹药 ⇒ 约一半耐久伤害",
                (max - 1) / 2, RavenRevolverMainWeapon.ammoDamage(3, 6, max));
    }

    @Test
    public void 耐久表达弹药_越少越深且不越界() {
        int max = 465;
        int previous = -1;
        for (int ammo = 6; ammo >= 0; ammo--) {
            int damage = RavenRevolverMainWeapon.ammoDamage(ammo, 6, max);
            assertTrue("弹药 " + ammo + " 的耐久伤害必须在 [0, max−1] 内，实际 = " + damage,
                    damage >= 0 && damage <= max - 1);
            assertTrue("弹药越少耐久伤害必须越大", damage >= previous);
            previous = damage;
        }
        assertEquals("没有耐久条（如结构空位）⇒ 不表达", 0,
                RavenRevolverMainWeapon.ammoDamage(0, 6, 0));
        assertEquals("弹药超上限 ⇒ 按上限夹", 0,
                RavenRevolverMainWeapon.ammoDamage(99, 6, max));
    }

    @Test
    public void 主武器前冲方向_夹掉向下但保留向上() {
        Vector down = RavenRevolverMainWeapon.dashDirection(new Vector(0d, -1d, 1d));
        assertTrue("低头时不能往下扎（y 必须 ≥ 0），实际 = " + down.getY(), down.getY() >= 0d);
        assertTrue("水平分量必须保留，实际 = " + down.getZ(), down.getZ() > 0d);

        Vector up = RavenRevolverMainWeapon.dashDirection(new Vector(0d, 1d, 1d));
        assertTrue("抬头时允许向上，实际 = " + up.getY(), up.getY() > 0d);
    }

    @Test
    public void 主武器前冲方向_退化输入不炸() {
        assertEquals("纯竖直向下（水平分量为 0）⇒ 退回 +Z", 1d,
                RavenRevolverMainWeapon.dashDirection(new Vector(0d, -1d, 0d)).getZ(), 1.0E-9d);
        assertEquals("null 也不炸", 1d, RavenRevolverMainWeapon.dashDirection(null).getZ(), 1.0E-9d);
    }

    @Test
    public void 主武器数值口径() {
        assertEquals("弹匣 6 枚", 6, RavenRevolverMainWeapon.MAGAZINE_SIZE);
        assertEquals("击发间隔 0.7 秒", 14, RavenRevolverMainWeapon.SHOT_COOLDOWN_TICKS);
        assertEquals("需求 2026-10-11：「近战攻击冷却缩减至 0.4 秒」⇒ 8 刻",
                8, RavenRevolverMainWeapon.MELEE_COOLDOWN_TICKS);
        assertEquals("子弹 20 格每秒 ⇒ 1 格/刻",
                1.0d, RavenRevolverMainWeapon.BULLET_SPEED_BLOCKS_PER_TICK, 1.0E-9d);
        assertEquals("蓄力 0.8 秒 ⇒ 16 刻", 16, RavenRevolverMainWeapon.LOCK_CHARGE_TICKS);
        assertEquals("锁定 3 枚", 3, RavenRevolverMainWeapon.LOCK_SHOTS);
        assertEquals("每发间隔 0.1 秒 ⇒ 2 刻", 2, RavenRevolverMainWeapon.LOCK_SHOT_INTERVAL_TICKS);
        assertEquals("锁定半径 20 格", 20.0d, RavenRevolverMainWeapon.LOCK_RANGE, 1.0E-9d);
        assertEquals("需求 2026-10-11：子弹物理伤害改为 10 点",
                10d, RavenRevolverMainWeapon.BULLET_PHYSICAL_DAMAGE, 1.0E-9d);
        assertEquals("近战仍是 12 点（本次只改子弹）",
                12d, RavenRevolverMainWeapon.MELEE_PHYSICAL_DAMAGE, 1.0E-9d);
    }

    @Test
    public void 空中前冲_零点二秒八格() {
        assertEquals("需求 2026-10-11：「在 0.2 秒内突进 8 格」⇒ 4 刻",
                4, RavenRevolverMainWeapon.AIR_DASH_STEPS);
        assertEquals("每刻位移 = 8 / 4 = 2 格",
                2.0d, RavenRevolverMainWeapon.AIR_DASH_STEP_PER_TICK, 1.0E-9d);
        assertEquals("总位移必须正好 8 格", RavenRevolverMainWeapon.AIR_DASH_DISTANCE,
                RavenRevolverMainWeapon.AIR_DASH_STEP_PER_TICK * RavenRevolverMainWeapon.AIR_DASH_STEPS,
                1.0E-9d);
    }

    // ───────── 技能一：升空突袭 ─────────

    @Test
    public void 升空突袭_地面形态方向是斜上方45度() {
        Vector direction = RavenSkyStrikeSkill.dashDirection(new Vector(0d, -1d, 1d));
        assertEquals("水平分量（低头也要保留水平前进）",
                Math.cos(Math.toRadians(45d)), direction.getZ(), 1.0E-6d);
        assertEquals("竖直分量 = sin45", Math.sin(Math.toRadians(45d)), direction.getY(), 1.0E-6d);
        assertTrue("必须是单位向量", Math.abs(direction.length() - 1d) < 1.0E-6d);
    }

    @Test
    public void 升空突袭_地面形态方向对退化输入不炸() {
        Vector fallback = RavenSkyStrikeSkill.dashDirection(new Vector(0d, 1d, 0d));
        assertTrue("纯竖直视线 ⇒ 退回水平 +Z 再抬 45°，仍向上", fallback.getY() > 0d);
        assertTrue(Math.abs(fallback.length() - 1d) < 1.0E-6d);
        assertEquals("null ⇒ 同退化分支", fallback.getY(),
                RavenSkyStrikeSkill.dashDirection(null).getY(), 1.0E-9d);
    }

    @Test
    public void 升空突袭_浮空形态是向前四格向上两格() {
        //需求 2026-10-11："向视角斜上方 0.2S 内单纯突进 4 格【向前 4 格，向上 2 格】"
        Vector step = RavenSkyStrikeSkill.hoverDashStep(new Vector(0d, -1d, 1d));
        assertEquals("每刻向前 = 4 / 4 = 1 格",
                RavenSkyStrikeSkill.HOVER_DASH_FORWARD / RavenSkyStrikeSkill.DASH_TICKS,
                step.getZ(), 1.0E-6d);
        assertEquals("每刻向上 = 2 / 4 = 0.5 格",
                RavenSkyStrikeSkill.HOVER_DASH_UP / RavenSkyStrikeSkill.DASH_TICKS,
                step.getY(), 1.0E-6d);
        assertEquals("走满 4 刻后向前 4 格", RavenSkyStrikeSkill.HOVER_DASH_FORWARD,
                step.getZ() * RavenSkyStrikeSkill.DASH_TICKS, 1.0E-6d);
        assertEquals("走满 4 刻后向上 2 格", RavenSkyStrikeSkill.HOVER_DASH_UP,
                step.getY() * RavenSkyStrikeSkill.DASH_TICKS, 1.0E-6d);
        assertTrue("浮空形态也必须向上（视角斜上方）", step.getY() > 0d);
    }

    @Test
    public void 升空突袭_浮空形态方向对退化输入不炸() {
        Vector step = RavenSkyStrikeSkill.hoverDashStep(new Vector(0d, -1d, 0d));
        assertEquals("纯竖直视线 ⇒ 水平分量退回 +Z",
                RavenSkyStrikeSkill.HOVER_DASH_FORWARD / RavenSkyStrikeSkill.DASH_TICKS,
                step.getZ(), 1.0E-6d);
        Vector nullStep = RavenSkyStrikeSkill.hoverDashStep(null);
        assertEquals("null ⇒ 同退化分支", step.getZ(), nullStep.getZ(), 1.0E-9d);
    }

    @Test
    public void 升空突袭_空中使用也要刷新浮空时间() {
        //需求 2026-10-11："一技能在空中使用也会刷新浮空时间"
        //★ 旧版条件是「!已在悬停 && 在空中」⇒ 已经在悬停时什么都不做（不刷新）
        assertEquals("在空中且已在悬停 ⇒ 刷新（旧版这里什么都不做）",
                RavenSkyStrikeSkill.HoverAction.REFRESH,
                RavenSkyStrikeSkill.hoverActionAfterFly(true, true));
        assertEquals("在空中但没悬停 ⇒ 进入",
                RavenSkyStrikeSkill.HoverAction.ENTER,
                RavenSkyStrikeSkill.hoverActionAfterFly(true, false));
        assertEquals("已经落地 ⇒ 不处理",
                RavenSkyStrikeSkill.HoverAction.NONE,
                RavenSkyStrikeSkill.hoverActionAfterFly(false, false));
        assertEquals("落地了但状态还没清 ⇒ 也不处理（以几何为准）",
                RavenSkyStrikeSkill.HoverAction.NONE,
                RavenSkyStrikeSkill.hoverActionAfterFly(false, true));
    }

    @Test
    public void 升空突袭_只有地面形态附带击退() {
        //需求 2026-10-11："地面使用 1 技能会对敌人造成击退效果"
        assertTrue("地面形态必须有击退", RavenSkyStrikeSkill.pathKnockback(false) > 0d);
        assertEquals("击退强度就是常量",
                RavenSkyStrikeSkill.PATH_KNOCKBACK, RavenSkyStrikeSkill.pathKnockback(false), 1.0E-9d);
        assertEquals("浮空形态不击退（需求写的是「地面使用」）",
                0d, RavenSkyStrikeSkill.pathKnockback(true), 1.0E-9d);
    }

    @Test
    public void 升空突袭_数值口径() {
        assertEquals("地面突进 7 格", 7.0d, RavenSkyStrikeSkill.DASH_DISTANCE, 1.0E-9d);
        assertEquals("冷却 8 秒 ⇒ 160 刻", 160, RavenSkyStrikeSkill.COOLDOWN_TICKS);
        assertEquals("路径伤害 10 点", 10d, RavenSkyStrikeSkill.PATH_PHYSICAL_DAMAGE, 1.0E-9d);
        assertEquals("位移耗时 0.2 秒 ⇒ 4 刻", 4, RavenSkyStrikeSkill.DASH_TICKS);
    }

    // ───────── 技能二：死亡之眼 ─────────

    @Test
    public void 死亡之眼_跃起高度() {
        assertEquals("需求：「向上跃起 3 格」", 64d + 3d, RavenDeathEyeSkill.jumpTargetY(64d), 1.0E-9d);
    }

    @Test
    public void 死亡之眼_锁定段到期判据() {
        assertFalse("还没到期", RavenDeathEyeSkill.lockExpired(100, 110));
        assertTrue("刚好到期", RavenDeathEyeSkill.lockExpired(110, 110));
        assertTrue("已过期", RavenDeathEyeSkill.lockExpired(120, 110));
        assertFalse("没有进行中的锁定（0）⇒ 永不到期", RavenDeathEyeSkill.lockExpired(1000, 0));
    }

    @Test
    public void 死亡之眼_数值口径() {
        assertEquals("冷却 20 秒 ⇒ 400 刻", 400, RavenDeathEyeSkill.COOLDOWN_TICKS);
        assertEquals("锁定段 0.5 秒 ⇒ 10 刻", 10, RavenDeathEyeSkill.LOCK_TICKS);
        assertEquals("锁定半径 20 格", 20.0d, RavenDeathEyeSkill.LOCK_RANGE, 1.0E-9d);
        assertEquals("每发回血 2 点", 2d, RavenDeathEyeSkill.LIFE_STEAL_HEAL, 1.0E-9d);
    }

    // ───────── 技能三：涅斯捷罗夫筋斗 ─────────

    @Test
    public void 筋斗_阶段迁移表穷举() {
        //IDLE：浮空 ⇒ 直接俯冲（不再跃起）；地面 ⇒ 先跃起
        assertEquals(RavenSomersaultSkill.Phase.RISE,
                RavenSomersaultSkill.nextPhase(RavenSomersaultSkill.Phase.IDLE, false));
        assertEquals(RavenSomersaultSkill.Phase.DIVE,
                RavenSomersaultSkill.nextPhase(RavenSomersaultSkill.Phase.IDLE, true));
        //RISE ⇒ DIVE（与是否浮空无关）
        assertEquals(RavenSomersaultSkill.Phase.DIVE,
                RavenSomersaultSkill.nextPhase(RavenSomersaultSkill.Phase.RISE, false));
        assertEquals(RavenSomersaultSkill.Phase.DIVE,
                RavenSomersaultSkill.nextPhase(RavenSomersaultSkill.Phase.RISE, true));
        //DIVE ⇒ IDLE（收工）
        assertEquals(RavenSomersaultSkill.Phase.IDLE,
                RavenSomersaultSkill.nextPhase(RavenSomersaultSkill.Phase.DIVE, false));
        assertEquals(RavenSomersaultSkill.Phase.IDLE,
                RavenSomersaultSkill.nextPhase(RavenSomersaultSkill.Phase.DIVE, true));
        assertEquals("null 兜底", RavenSomersaultSkill.Phase.IDLE,
                RavenSomersaultSkill.nextPhase(null, true));
    }

    @Test
    public void 筋斗_跃起后的高度按形态取值() {
        //需求："自己当前的跃起后的高度"，括注"【浮空状态就是当前高度】"
        assertEquals("地面形态 ⇒ 当前高度 + 跃起 12 格", 64d + 12d,
                RavenSomersaultSkill.apexYFor(64d, false), 1.0E-9d);
        assertEquals("浮空形态 ⇒ 就是当前高度（不再加跃起量）", 64d,
                RavenSomersaultSkill.apexYFor(64d, true), 1.0E-9d);
        assertEquals("两种形态必须正好差一个跃起高度",
                RavenSomersaultSkill.RISE_HEIGHT,
                RavenSomersaultSkill.apexYFor(70d, false) - RavenSomersaultSkill.apexYFor(70d, true),
                1.0E-9d);
    }

    @Test
    public void 筋斗_落点高度门限是跃起后高度减三() {
        //需求："此不可穿越方块高度是否高于或等于自己当前的跃起后的高度减少 3 格？不是则…作为落点"
        double apex = 76d;      //则门限 = 73
        assertTrue("顶面 70（低于 73）⇒ 可以落",
                RavenSomersaultSkill.isLandable(70d, apex));
        assertTrue("顶面刚好 72.9 ⇒ 可以落",
                RavenSomersaultSkill.isLandable(apex - RavenSomersaultSkill.LANDING_CLEARANCE - 0.1d, apex));
        assertFalse("顶面刚好等于 73 ⇒ 不行（需求写「高于或等于」都要排除）",
                RavenSomersaultSkill.isLandable(apex - RavenSomersaultSkill.LANDING_CLEARANCE, apex));
        assertFalse("顶面 75（贴着飞行高度）⇒ 不能落（只会撞墙侧）",
                RavenSomersaultSkill.isLandable(75d, apex));
        assertFalse("顶面比飞行高度还高 ⇒ 不能落",
                RavenSomersaultSkill.isLandable(90d, apex));
    }

    @Test
    public void 筋斗_落点上方必须有净空() {
        //需求："此不可穿越方块上两格有无不可穿越方块？无，则再进行判定"
        assertTrue("上方两格都可穿越 ⇒ 有净空", RavenSomersaultSkill.hasHeadroom(true, true));
        assertFalse("紧邻上方那一格不可穿越 ⇒ 没净空",
                RavenSomersaultSkill.hasHeadroom(false, true));
        assertFalse("再上面那一格不可穿越 ⇒ 也没净空",
                RavenSomersaultSkill.hasHeadroom(true, false));
        assertFalse("两格都不可穿越 ⇒ 没净空", RavenSomersaultSkill.hasHeadroom(false, false));
    }

    @Test
    public void 筋斗_垂足不得高于自己六格() {
        //需求："垂足不可高于自己 6 个格子"
        assertTrue("垂足低于自己 ⇒ 可以", RavenSomersaultSkill.isFootAcceptable(60d, 70d));
        assertTrue("垂足刚好高自己 6 格 ⇒ 可以（边界）",
                RavenSomersaultSkill.isFootAcceptable(76d, 70d));
        assertFalse("垂足高自己 6.1 格 ⇒ 不行", RavenSomersaultSkill.isFootAcceptable(76.1d, 70d));
        assertFalse("垂足高自己 20 格（悬崖）⇒ 不行", RavenSomersaultSkill.isFootAcceptable(90d, 70d));
    }

    @Test
    public void 筋斗_射线长度与步长() {
        assertEquals("需求：「射线长度为 25」", 25.0d, RavenSomersaultSkill.RAY_LENGTH, 1.0E-9d);
        assertTrue("步长必须显著小于 1 格，否则会整格跳过一堵一格厚的墙",
                RavenSomersaultSkill.RAY_STEP <= 0.5d);
        assertTrue("步长也不能小到白烧 CPU", RavenSomersaultSkill.RAY_STEP >= 0.05d);
        assertEquals("门限常量 = 3", 3.0d, RavenSomersaultSkill.LANDING_CLEARANCE, 1.0E-9d);
        assertEquals("垂足上限常量 = 6", 6.0d, RavenSomersaultSkill.MAX_FOOT_ABOVE_SELF, 1.0E-9d);
    }

    @Test
    public void 筋斗_撞墙必须转入就地砸地_不能冻在半空() {
        //★ "向高处位移会卡墙"的根因：旧实现撞墙只"停在原地"，而"到位"判据仍为假
        //  ⇒ 看门狗计数永远不加 ⇒ 玩家被永久冻结
        assertEquals("撞墙 ⇒ 必须转下坠（否则永久冻结）",
                RavenSomersaultSkill.DiveAction.PLUNGE,
                RavenSomersaultSkill.diveAction(false, false, true));
        assertEquals("到位 ⇒ 转下坠",
                RavenSomersaultSkill.DiveAction.PLUNGE,
                RavenSomersaultSkill.diveAction(false, true, false));
        assertEquals("已经在砸 ⇒ 保持下坠",
                RavenSomersaultSkill.DiveAction.PLUNGE,
                RavenSomersaultSkill.diveAction(true, false, false));
        assertEquals("畅通且没到位 ⇒ 继续突进",
                RavenSomersaultSkill.DiveAction.ADVANCE,
                RavenSomersaultSkill.diveAction(false, false, false));
        assertEquals("撞墙 + 已到位 ⇒ 还是下坠",
                RavenSomersaultSkill.DiveAction.PLUNGE,
                RavenSomersaultSkill.diveAction(false, true, true));
    }

    @Test
    public void 筋斗_十秒之后不再尝试突进() {
        //需求："大招最长持续 10S，超过 10S 则不会再尝试去突进，而是就地进行砸地操作"
        assertEquals("10 秒 ⇒ 200 刻", 200, RavenSomersaultSkill.MAX_FLIGHT_TICKS);
        assertEquals("10 秒 = 20 刻/秒 × 10",
                200, 20 * 10);
        assertFalse("还差一刻 ⇒ 继续突进",
                RavenSomersaultSkill.shouldStopAdvancing(99, 100));
        assertTrue("刚好到点 ⇒ 停止突进", RavenSomersaultSkill.shouldStopAdvancing(100, 100));
        assertTrue("早已超时 ⇒ 停止突进", RavenSomersaultSkill.shouldStopAdvancing(500, 100));
    }

    @Test
    public void 筋斗_击飞冲量总是向上且水平外推() {
        //需求 2026-10-11："大招会击飞附近的敌人"
        Vector straight = RavenSomersaultSkill.launchVelocity(0d, 0d);
        assertEquals("与落点重合 ⇒ 仍然向上",
                RavenSomersaultSkill.LAUNCH_UP, straight.getY(), 1.0E-9d);
        assertEquals("没有水平分量", 0d, straight.getX(), 1.0E-9d);

        Vector outward = RavenSomersaultSkill.launchVelocity(3d, 4d);
        assertEquals("向上分量恒定", RavenSomersaultSkill.LAUNCH_UP, outward.getY(), 1.0E-9d);
        assertEquals("水平分量大小 = LAUNCH_OUT", RavenSomersaultSkill.LAUNCH_OUT,
                Math.hypot(outward.getX(), outward.getZ()), 1.0E-6d);
        assertTrue("方向必须与偏移同向（远离落点）", outward.getX() > 0d && outward.getZ() > 0d);
    }

    @Test
    public void 筋斗_数值口径() {
        assertEquals("冷却 40 秒 ⇒ 800 刻", 800, RavenSomersaultSkill.COOLDOWN_TICKS);
        assertEquals("跃起 12 格", 12.0d, RavenSomersaultSkill.RISE_HEIGHT, 1.0E-9d);
        assertEquals("跃起 0.2 秒 ⇒ 4 刻", 4, RavenSomersaultSkill.RISE_TICKS);
        assertEquals("俯冲 25 格每秒 ⇒ 1.25 格/刻",
                1.25d, RavenSomersaultSkill.DIVE_STEP_PER_TICK, 1.0E-9d);
        assertEquals("射线 25 格", 25.0d, RavenSomersaultSkill.RAY_LENGTH, 1.0E-9d);
        assertEquals("r7", 7.0d, RavenSomersaultSkill.IMPACT_RADIUS, 1.0E-9d);
        assertEquals("地面 15 点", 15d, RavenSomersaultSkill.GROUND_PHYSICAL_DAMAGE, 1.0E-9d);
        assertEquals("浮空 25 点", 25d, RavenSomersaultSkill.HOVER_PHYSICAL_DAMAGE, 1.0E-9d);
        assertEquals("地面眩晕 1.5 秒 ⇒ 30 刻", 30, RavenSomersaultSkill.GROUND_STUN_TICKS);
        assertEquals("浮空眩晕 2 秒 ⇒ 40 刻", 40, RavenSomersaultSkill.HOVER_STUN_TICKS);
        assertTrue("浮空形态伤害必须更高",
                RavenSomersaultSkill.HOVER_PHYSICAL_DAMAGE > RavenSomersaultSkill.GROUND_PHYSICAL_DAMAGE);
        assertTrue("浮空形态眩晕必须更长",
                RavenSomersaultSkill.HOVER_STUN_TICKS > RavenSomersaultSkill.GROUND_STUN_TICKS);
    }

    // ───────── 装备：属性增量 ─────────

    @Test
    public void 装备_护甲增量把原版基础补到五() {
        assertEquals(2d, RavenEquipmentsPassive.modifierDelta(
                RavenEquipmentsPassive.VANILLA_LEATHER_CHESTPLATE_ARMOR,
                RavenEquipmentsPassive.TARGET_ARMOR), 1.0E-9d);
        assertEquals(-1d, RavenEquipmentsPassive.modifierDelta(
                RavenEquipmentsPassive.VANILLA_NETHERITE_LEGGINGS_ARMOR,
                RavenEquipmentsPassive.TARGET_ARMOR), 1.0E-9d);
        assertEquals(2d, RavenEquipmentsPassive.modifierDelta(
                RavenEquipmentsPassive.VANILLA_NETHERITE_BOOTS_ARMOR,
                RavenEquipmentsPassive.TARGET_ARMOR), 1.0E-9d);
    }

    @Test
    public void 装备_韧性增量把原版基础补到一() {
        assertEquals(1d, RavenEquipmentsPassive.modifierDelta(
                RavenEquipmentsPassive.VANILLA_LEATHER_CHESTPLATE_TOUGHNESS,
                RavenEquipmentsPassive.TARGET_TOUGHNESS), 1.0E-9d);
        assertEquals(-2d, RavenEquipmentsPassive.modifierDelta(
                RavenEquipmentsPassive.VANILLA_NETHERITE_LEGGINGS_TOUGHNESS,
                RavenEquipmentsPassive.TARGET_TOUGHNESS), 1.0E-9d);
        assertEquals(-2d, RavenEquipmentsPassive.modifierDelta(
                RavenEquipmentsPassive.VANILLA_NETHERITE_BOOTS_TOUGHNESS,
                RavenEquipmentsPassive.TARGET_TOUGHNESS), 1.0E-9d);
    }

    @Test
    public void 装备_靴子击退抗性增量是零点一() {
        assertEquals("原版 0.1 → 目标 0.2 ⇒ 增量 +0.1",
                0.1d, RavenEquipmentsPassive.modifierDelta(
                        RavenEquipmentsPassive.VANILLA_NETHERITE_BOOTS_KNOCKBACK_RESISTANCE,
                        RavenEquipmentsPassive.TARGET_BOOTS_KNOCKBACK_RESISTANCE), 1.0E-9d);
    }

    @Test
    public void 装备_常驻速度是二() {
        assertEquals("需求：「角色常驻速度 2」⇒ 原版增幅 1",
                1, RavenEquipmentsPassive.SPEED_AMPLIFIER);
    }

    // ───────── 随机区间：闭区间 ─────────

    @Test
    public void 随机区间_两端都能取到() {
        Random random = new Random(20261011L);
        boolean sawMin = false;
        boolean sawMax = false;
        for (int i = 0; i < 4000; i++) {
            int value = RavenRevolverMainWeapon.rollInclusive(random, 3, 6);
            assertTrue("越界：" + value, value >= 3 && value <= 6);
            sawMin |= value == 3;
            sawMax |= value == 6;
        }
        assertTrue("3 必须能取到（需求写 3~6，两端都含）", sawMin);
        assertTrue("6 必须能取到", sawMax);
    }

    @Test
    public void 随机区间_退化输入回下界() {
        assertEquals(3, RavenRevolverMainWeapon.rollInclusive(null, 3, 6));
        assertEquals(4, RavenRevolverMainWeapon.rollInclusive(new Random(1L), 4, 4));
    }

    @Test
    public void 随机区间_四个区间口径都一致() {
        Random random = new Random(7L);
        for (int i = 0; i < 500; i++) {
            int a = RavenAirMobilityPassive.rollInclusive(random, 3, 6);
            int b = RavenSkyStrikeSkill.rollInclusive(random, 3, 6);
            int c = RavenSomersaultSkill.rollInclusive(random, 4, 7);
            assertTrue(a >= 3 && a <= 6);
            assertTrue(b >= 3 && b <= 6);
            assertTrue(c >= 4 && c <= 7);
        }
    }

    // ───────── 空中机动：挂起标志必须能开能关（2026-10-11 真 BUG 的回归）─────────

    @Test
    public void 空中机动挂起标志必须能开能关() {
        //★ 回归：旧版主武器用「前冲剩余刻数 > 0」判收尾，而收尾恰好发生在"刚减到 0"之后
        //  ⇒ 判据永远为假 ⇒ 被动的挂起标志永远清不掉 ⇒ 悬停的物理与超时判定被永久挂起
        //  （表现：用一次主动机动后，浮空状态再也掉不下来、也不会超时）
        RavenAirMobilityPassive passive = new RavenAirMobilityPassive(
                RavenAirMobilityPassive.ID,
                new com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort(null, null, null),
                new RavenAirMobilityPassive.Specification());
        assertFalse("初始不该处于挂起", passive.isDashSuspended());
        passive.beginAirDash();
        assertTrue("开始空中机动 ⇒ 进入挂起", passive.isDashSuspended());
        passive.endAirDash(false);
        assertFalse("结束空中机动 ⇒ 必须解除挂起（否则悬停会被永久挂起）", passive.isDashSuspended());
        assertFalse("重复解除必须幂等", passive.isDashSuspended());
    }
}
