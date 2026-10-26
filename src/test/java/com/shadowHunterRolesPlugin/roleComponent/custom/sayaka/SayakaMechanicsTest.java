package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 芹泽沙耶香的**机制纯函数**离线单测（充能表 / 走廊 / 连段 / 弹道 / 风暴）。
 *
 * <h2>为什么本类**必须**先装 KeyFactory 桩</h2>
 * 本类会加载 {@link SayakaBladeMainWeapon}（读它的充能表常量）——该类有
 * {@code static final NamespacedKey ATTACK_SPEED_KEY = KeyFactory.Registry.of(...)}。
 * 没人 install 时类初始化抛异常，且 JVM 会缓存这次失败（事后补桩也救不回来）。
 * ⇒ 与 {@code RoleAssemblyTest} 同做法：{@code @BeforeClass} 先装桩。
 *
 * <h2>钉住的是什么</h2>
 * <ul>
 *   <li>需求里逐条写明的数值（8/2/2、5/7/9、4/6/8、2/3/4、9/18/30、5/9/15、3 层免疫、150/30 等）
 *       与实际常量一致（防"需求改了口径但代码没跟"）；</li>
 *   <li>三张充能表 + 越界钳制；</li>
 *   <li>居合 / 连斩的**走廊判据**（前向 / 侧向 / 高差三条件，背后不命中）；</li>
 *   <li>连段递増表与刷新上限；</li>
 *   <li>榴弹弹道的两段几何（总刻数、隆起、插值）；</li>
 *   <li>风暴的三段时刻表与半径判据。</li>
 * </ul>
 */
public class SayakaMechanicsTest {

    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    // ───────── 主武器：需求数值 ─────────

    /** 拔刀基础数值逐条对齐需求。 */
    @Test
    public void drawnBaselineNumbers() {
        assertEquals(8.0d, SayakaBladeMainWeapon.MELEE_PHYSICAL, 1.0E-9d);
        assertEquals(2.0d, SayakaBladeMainWeapon.MELEE_TRUE_DAMAGE, 1.0E-9d);
        assertEquals(2, SayakaBladeMainWeapon.MELEE_IMBALANCE);
        assertEquals(1.0d, SayakaBladeMainWeapon.BURST_HIT_HEAL, 1.0E-9d);
        assertEquals("2026-10-17 需求 6：主武器 CD 0.1 秒", 2, SayakaBladeMainWeapon.MELEE_COOLDOWN_TICKS);
        assertEquals(6, SayakaBladeMainWeapon.SHEATHE_PREP_TICKS);      //0.3 秒
        assertEquals("居合蓄力条与现实居合同长 0.3 秒", 6, SayakaBladeMainWeapon.IAI_CHARGE_TICKS);
        assertEquals("蓄力音每 2 刻一响", 2, SayakaBladeMainWeapon.IAI_CHARGE_SOUND_STEP_TICKS);
        assertEquals("纳刀期间 = 缓慢 IV（amplifier 3，不再是定身 255）", 3,
                SayakaBladeMainWeapon.SHEATHE_SLOW_AMPLIFIER);
        assertEquals("居合内部闸门 0.4 秒", 8, SayakaBladeMainWeapon.IAI_COOLDOWN_TICKS);
        assertEquals(8.0d, SayakaBladeMainWeapon.IAI_RANGE, 1.0E-9d);   //路径 8 米
        assertEquals(8.0d, SayakaBladeMainWeapon.IAI_PHYSICAL, 1.0E-9d);
        assertEquals(5, SayakaBladeMainWeapon.IAI_IMBALANCE);
        assertEquals(5, SayakaBladeMainWeapon.CHARGE_ENERGY_COST);      //每次充能 5 能量
        assertEquals(20, SayakaBladeMainWeapon.IMMUNE_TICKS);           //免疫 1 秒
    }

    // ───────── 主武器：居合蓄力提示（需求 1）─────────

    /** 蓄力进度 / 蓄满判据 / 3 档递增音（0.3 秒）。 */
    @Test
    public void iaiChargeTimeline() {
        assertEquals("2026-10-17：蓄力粒子整组下移 0.5 格", 0.5d,
                SayakaVfx.IAI_CHARGE_HEIGHT, 1.0E-9d);
        assertEquals(0.0d, SayakaBladeMainWeapon.iaiChargeProgress(0), 1.0E-9d);
        assertEquals(0.5d, SayakaBladeMainWeapon.iaiChargeProgress(3), 1.0E-9d);
        assertEquals(1.0d, SayakaBladeMainWeapon.iaiChargeProgress(6), 1.0E-9d);
        assertEquals("越界夹到 1", 1.0d, SayakaBladeMainWeapon.iaiChargeProgress(999), 1.0E-9d);
        assertEquals(0.0d, SayakaBladeMainWeapon.iaiChargeProgress(-5), 1.0E-9d);

        assertFalse(SayakaBladeMainWeapon.iaiChargeComplete(5));
        assertTrue(SayakaBladeMainWeapon.iaiChargeComplete(6));
        assertTrue(SayakaBladeMainWeapon.iaiChargeComplete(100));

        //第 2/4/6 刻各一响（档位 0..2，音高逐级升高）
        assertEquals(0, SayakaBladeMainWeapon.iaiChargeSoundStep(0));
        assertEquals(0, SayakaBladeMainWeapon.iaiChargeSoundStep(1));
        assertEquals(0, SayakaBladeMainWeapon.iaiChargeSoundStep(2));
        assertEquals(1, SayakaBladeMainWeapon.iaiChargeSoundStep(4));
        assertEquals("第 6 刻封顶第 3 档（index 2）", 2, SayakaBladeMainWeapon.iaiChargeSoundStep(6));
        assertEquals(2, SayakaBladeMainWeapon.iaiChargeSoundStep(999));
    }

    /** 三张充能表逐条对齐需求（1/2/3 层）。 */
    @Test
    public void chargeTables() {
        assertEquals(0, SayakaBladeMainWeapon.drawnBonusImbalance(0));
        assertEquals(5, SayakaBladeMainWeapon.drawnBonusImbalance(1));
        assertEquals(7, SayakaBladeMainWeapon.drawnBonusImbalance(2));
        assertEquals(9, SayakaBladeMainWeapon.drawnBonusImbalance(3));
        assertEquals(4.0d, SayakaBladeMainWeapon.drawnBonusHeal(1), 1.0E-9d);
        assertEquals(6.0d, SayakaBladeMainWeapon.drawnBonusHeal(2), 1.0E-9d);
        assertEquals(8.0d, SayakaBladeMainWeapon.drawnBonusHeal(3), 1.0E-9d);
        assertEquals(2.0d, SayakaBladeMainWeapon.drawnBonusTrue(1), 1.0E-9d);
        assertEquals(3.0d, SayakaBladeMainWeapon.drawnBonusTrue(2), 1.0E-9d);
        assertEquals(4.0d, SayakaBladeMainWeapon.drawnBonusTrue(3), 1.0E-9d);

        assertEquals(9, SayakaBladeMainWeapon.defenseBonusImbalance(1));
        assertEquals(18, SayakaBladeMainWeapon.defenseBonusImbalance(2));
        assertEquals(30, SayakaBladeMainWeapon.defenseBonusImbalance(3));
        assertEquals(2.0d, SayakaBladeMainWeapon.defenseBonusTrue(1), 1.0E-9d);
        assertEquals(4.0d, SayakaBladeMainWeapon.defenseBonusTrue(3), 1.0E-9d);

        assertEquals(5, SayakaBladeMainWeapon.activeBonusImbalance(1));
        assertEquals(9, SayakaBladeMainWeapon.activeBonusImbalance(2));
        assertEquals(15, SayakaBladeMainWeapon.activeBonusImbalance(3));

        //越界钳制：超过 3 层按 3 层，0 / 负数无加成
        assertEquals(9, SayakaBladeMainWeapon.drawnBonusImbalance(4));
        assertEquals(30, SayakaBladeMainWeapon.defenseBonusImbalance(99));
        assertEquals(0, SayakaBladeMainWeapon.activeBonusImbalance(-1));
        assertEquals(0.0d, SayakaBladeMainWeapon.drawnBonusHeal(0), 1.0E-9d);
    }

    /** 3 层 + 防御成功 ⇒ 免疫（主动触发 / 低层数都不签）。 */
    @Test
    public void immunityOnlyForFullChargeDefense() {
        assertTrue(SayakaBladeMainWeapon.grantsImmunity(true, 3));
        assertFalse(SayakaBladeMainWeapon.grantsImmunity(true, 2));
        assertFalse(SayakaBladeMainWeapon.grantsImmunity(false, 3));
        assertFalse(SayakaBladeMainWeapon.grantsImmunity(false, 0));
    }

    /**
     * 居合走廊：**三维点—射线**（2026-10-18 需求 6：随准星俯仰）。
     *
     * <p>旧口径是"水平叉积 + 竖直容差 2.5"，导致抬头也打不到高处的敌人；现在射线跟着准星，
     * 垂直距离用同一个半径（1.6）收口。
     */
    @Test
    public void iaiCorridorGeometry() {
        //轴 = +Z（水平瞄准）
        assertTrue(SayakaBladeMainWeapon.insideIaiCorridor(0d, 0d, 5d, 0d, 0d, 1d));
        assertTrue("边界前向 8 格含", SayakaBladeMainWeapon.insideIaiCorridor(0d, 0d, 8d, 0d, 0d, 1d));
        assertFalse("前向 8.5 格超界", SayakaBladeMainWeapon.insideIaiCorridor(0d, 0d, 8.5d, 0d, 0d, 1d));
        assertFalse("背后不命中", SayakaBladeMainWeapon.insideIaiCorridor(0d, 0d, -1d, 0d, 0d, 1d));
        assertTrue("侧向边界 1.6 含", SayakaBladeMainWeapon.insideIaiCorridor(1.6d, 0d, 4d, 0d, 0d, 1d));
        assertFalse("侧向 1.7 超界", SayakaBladeMainWeapon.insideIaiCorridor(1.7d, 0d, 4d, 0d, 0d, 1d));

        //★ 抬头 45°：轴 = (0, √0.5, √0.5) ⇒ 视觉上"随准星"
        double s = Math.sqrt(0.5d);
        double high = 4d * Math.sqrt(2d);   //8 格射线的 45° 落点分量
        assertTrue("抬头后能打到 8 格内的高处敌人",
                SayakaBladeMainWeapon.insideIaiCorridor(0d, high, high, 0d, s, s));
        assertFalse("水平瞄准时打不到 5 格高处（旧口径会误判为命中）",
                SayakaBladeMainWeapon.insideIaiCorridor(0d, 5d, 5d, 0d, 0d, 1d));

        //轴 = +X
        assertTrue(SayakaBladeMainWeapon.insideIaiCorridor(5d, 0d, 0d, 1d, 0d, 0d));
        assertFalse("侧向（+Z）不再命中", SayakaBladeMainWeapon.insideIaiCorridor(0d, 0d, 5d, 1d, 0d, 0d));
    }

    // ───────── 技能二：连段 ─────────

    /** 连段递増表：失衡 4/6/8/10/12、真伤 0/1/2/3/4；第 5 段后必须收链。 */
    @Test
    public void chainTablesAndRefreshLimit() {
        assertEquals(4, SayakaFlashSlashSkill.chainImbalance(0));
        assertEquals(6, SayakaFlashSlashSkill.chainImbalance(1));
        assertEquals(8, SayakaFlashSlashSkill.chainImbalance(2));
        assertEquals(10, SayakaFlashSlashSkill.chainImbalance(3));
        assertEquals(12, SayakaFlashSlashSkill.chainImbalance(4));
        assertEquals("越界按末档钳制", 12, SayakaFlashSlashSkill.chainImbalance(9));

        assertEquals(0, SayakaFlashSlashSkill.chainTrue(0));
        assertEquals(1, SayakaFlashSlashSkill.chainTrue(1));
        assertEquals(4, SayakaFlashSlashSkill.chainTrue(4));
        assertEquals("越界按末档钳制", 4, SayakaFlashSlashSkill.chainTrue(99));

        assertEquals(12.0d, SayakaFlashSlashSkill.BASE_PHYSICAL, 1.0E-9d);
        assertEquals(4, SayakaFlashSlashSkill.BASE_IMBALANCE);
        assertEquals(6.0d, SayakaFlashSlashSkill.HIT_HEAL, 1.0E-9d);
        assertEquals("初始 1 + 刷新 4 = 5 段", 5, SayakaFlashSlashSkill.CHAIN_MAX_CASTS);
        assertEquals("3 秒重放窗口", 60, SayakaFlashSlashSkill.REFRESH_WINDOW_TICKS);
        assertEquals("冷却 20 秒", 400, SayakaFlashSlashSkill.COOLDOWN_TICKS);
        assertEquals("每次 5 能量", 5, SayakaFlashSlashSkill.ENERGY_COST);

        assertTrue(SayakaFlashSlashSkill.chainContinues(4));
        assertFalse("第 5 段（= 第 4 次刷新后）不得再续", SayakaFlashSlashSkill.chainContinues(5));
    }

    /** 突进 0.15 秒（3 刻）与进度曲线（2026-10-17 需求 7）。 */
    @Test
    public void dashTiming() {
        assertEquals("0.15 秒 = 3 刻", 3, SayakaFlashSlashSkill.DASH_TICKS);
        assertEquals(0.0d, SayakaFlashSlashSkill.dashProgress(0), 1.0E-9d);
        assertEquals(1d / 3d, SayakaFlashSlashSkill.dashProgress(1), 1.0E-9d);
        assertEquals(2d / 3d, SayakaFlashSlashSkill.dashProgress(2), 1.0E-9d);
        assertEquals("第 3 刻到位", 1.0d, SayakaFlashSlashSkill.dashProgress(3), 1.0E-9d);
        assertEquals(1.0d, SayakaFlashSlashSkill.dashProgress(99), 1.0E-9d);
    }

    /** 连斩走廊：与居合同口径的几何。 */
    @Test
    public void slashCorridorGeometry() {
        assertTrue(SayakaFlashSlashSkill.insideSlashCorridor(0d, 6d, 0d, 0d, 1d));
        assertFalse(SayakaFlashSlashSkill.insideSlashCorridor(0d, -1d, 0d, 0d, 1d));
        assertFalse(SayakaFlashSlashSkill.insideSlashCorridor(1.6d, 4d, 0d, 0d, 1d));
        assertTrue(SayakaFlashSlashSkill.insideSlashCorridor(0d, 7d, 2.5d, 0d, 1d));
        assertFalse(SayakaFlashSlashSkill.insideSlashCorridor(0d, 7.5d, 0d, 0d, 1d));
    }

    // ───────── 技能一：榴弹弹道 ─────────

    /** 弹道两段几何：总刻数（30 格/秒、下限 6 刻）、隆起（缓增封顶 3）、插值两端为 0。 */
    @Test
    public void grenadeTrajectory() {
        assertEquals("贴脸也有抛物线过程", 6, SayakaGrenadeSkill.flightTicks(0d));
        assertEquals("7.5 格 = 5 刻但受下限 ⇒ 6", 6, SayakaGrenadeSkill.flightTicks(7.5d));
        assertEquals(10, SayakaGrenadeSkill.flightTicks(15d));
        assertEquals("20 格 = 14 刻（0.7 秒）", 14, SayakaGrenadeSkill.flightTicks(20d));
        assertEquals("2026-10-17 需求 3：弹速 30 格/秒", 1.5d,
                SayakaGrenadeSkill.FLIGHT_SPEED_PER_TICK, 1.0E-9d);

        assertEquals(1.0d, SayakaGrenadeSkill.arcApex(0d), 1.0E-9d);
        assertEquals(2.0d, SayakaGrenadeSkill.arcApex(10d), 1.0E-9d);
        assertEquals("封顶 3 格（下落快）", 3.0d, SayakaGrenadeSkill.arcApex(50d), 1.0E-9d);

        assertEquals("两端为 0", 0.0d, SayakaGrenadeSkill.arcOffset(0d, 2d), 1.0E-9d);
        assertEquals("中点为 apex", 2.0d, SayakaGrenadeSkill.arcOffset(0.5d, 2d), 1.0E-9d);
        assertEquals(0.0d, SayakaGrenadeSkill.arcOffset(1d, 2d), 1.0E-9d);
        assertEquals("越界参数被夹到端点 ⇒ 为 0", 0.0d, SayakaGrenadeSkill.arcOffset(2d, 2d), 1.0E-9d);

        assertEquals(5.0d, SayakaGrenadeSkill.horizontalDistance(
                new Location(null, 0d, 0d, 0d), new Location(null, 3d, 9d, 4d)), 1.0E-9d);

        //需求数值
        assertEquals(6, SayakaGrenadeSkill.AMMO_MAX);
        assertEquals(200, SayakaGrenadeSkill.AMMO_REGEN_TICKS);   //10 秒
        assertEquals(6, SayakaGrenadeSkill.FIRE_ENERGY_COST);
        assertEquals(16, SayakaGrenadeSkill.FIRE_INTERVAL_TICKS); //0.8 秒
        assertEquals(5.0d, SayakaGrenadeSkill.EXPLODE_PHYSICAL, 1.0E-9d);
        assertEquals(5.0d, SayakaGrenadeSkill.EXPLODE_RADIUS, 1.0E-9d);
        assertEquals(100, SayakaGrenadeSkill.FIELD_TICKS);        //5 秒
        assertEquals(1.0d, SayakaGrenadeSkill.FIELD_TRUE_PER_PULSE, 1.0E-9d);
        assertEquals(2, SayakaGrenadeSkill.FIELD_IMBALANCE_PER_PULSE);
        assertEquals(1, SayakaGrenadeSkill.FIELD_SLOW_AMPLIFIER); //缓慢 2
        assertEquals(3, SayakaGrenadeSkill.SELF_SPEED_AMPLIFIER); //速度 4
        assertEquals(0, SayakaGrenadeSkill.SELF_REGEN_AMPLIFIER); //再生 1
        //后座 / 屏幕缩放（需求 3）
        assertEquals(4.5d, SayakaGrenadeSkill.RECOIL_PITCH_DEGREES, 1.0E-9d);
        assertEquals(6, SayakaGrenadeSkill.RECOIL_TICKS);
        assertEquals("发射当刻满抬升（保持期不衰减，归位是另一拍）", 4.5d,
                SayakaGrenadeSkill.recoilOffset(6), 1.0E-9d);
        assertEquals("保持期内仍是满抬升", 4.5d, SayakaGrenadeSkill.recoilOffset(3), 1.0E-9d);
        assertEquals("归位那一拍为 0", 0.0d, SayakaGrenadeSkill.recoilOffset(0), 1.0E-9d);
        assertEquals("越界仍是满值", 4.5d, SayakaGrenadeSkill.recoilOffset(99), 1.0E-9d);
        assertEquals(4, SayakaGrenadeSkill.ZOOM_TICKS);
    }

    /** 榴弹命中敌人的线段判定：点—线段距离（1.5 格/刻的弹速也不会穿人）。 */
    @Test
    public void grenadeHitSegment() {
        org.bukkit.util.Vector from = new org.bukkit.util.Vector(0d, 0d, 0d);
        org.bukkit.util.Vector to = new org.bukkit.util.Vector(3d, 0d, 0d);
        assertEquals("线段上的点", 0.0d,
                SayakaGrenadeSkill.pointSegmentDistance(new org.bukkit.util.Vector(1.5d, 0d, 0d), from, to),
                1.0E-9d);
        assertEquals("侧向 1 格", 1.0d,
                SayakaGrenadeSkill.pointSegmentDistance(new org.bukkit.util.Vector(1.5d, 1d, 0d), from, to),
                1.0E-9d);
        assertEquals("越过终点 ⇒ 按终点算", 1.0d,
                SayakaGrenadeSkill.pointSegmentDistance(new org.bukkit.util.Vector(4d, 0d, 0d), from, to),
                1.0E-9d);
        assertEquals("越过起点 ⇒ 按起点算", 1.0d,
                SayakaGrenadeSkill.pointSegmentDistance(new org.bukkit.util.Vector(-1d, 0d, 0d), from, to),
                1.0E-9d);
        assertEquals("退化线段 ⇒ 直接点距", 2.0d,
                SayakaGrenadeSkill.pointSegmentDistance(new org.bukkit.util.Vector(2d, 0d, 0d), from, from),
                1.0E-9d);
        assertEquals("命中半径 1.4 格", 1.4d, SayakaGrenadeSkill.HIT_RADIUS, 1.0E-9d);
    }

    // ───────── 大招：风暴 ─────────

    /** 三段时刻表 = 起手后 20 / 40 / 60 刻（首段推迟一拍 —— 与语音节奏对齐，口径申报一）；第 3 段为收尾段；半径判据。 */
    @Test
    public void stormScheduleAndRadius() {
        assertEquals("首段在起手式 1 秒后（口径申报一）", 20, SayakaBladeStormSkill.stageTick(0));
        assertEquals(40, SayakaBladeStormSkill.stageTick(1));
        assertEquals(60, SayakaBladeStormSkill.stageTick(2));
        assertFalse(SayakaBladeStormSkill.stageIsFinale(0));
        assertFalse(SayakaBladeStormSkill.stageIsFinale(1));
        assertTrue(SayakaBladeStormSkill.stageIsFinale(2));

        assertTrue(SayakaBladeStormSkill.inRadius(8.0d * 8.0d, 8.0d));
        assertFalse(SayakaBladeStormSkill.inRadius(8.0d * 8.0d + 0.1d, 8.0d));
        assertTrue(SayakaBladeStormSkill.inRadius(12.0d * 12.0d, 12.0d));

        //需求数值
        assertEquals(3, SayakaBladeStormSkill.STAGES);
        assertEquals(20, SayakaBladeStormSkill.STAGE_INTERVAL_TICKS);  //1 秒
        assertEquals(15.0d, SayakaBladeStormSkill.STAGE_PHYSICAL, 1.0E-9d);
        assertEquals(10, SayakaBladeStormSkill.STAGE_IMBALANCE);
        assertEquals(5.0d, SayakaBladeStormSkill.BURST_BONUS_TRUE, 1.0E-9d);
        assertEquals(12.0d, SayakaBladeStormSkill.FINAL_RADIUS, 1.0E-9d);
        assertEquals(200, SayakaBladeStormSkill.FINAL_DARKNESS_TICKS); //10 秒
        assertEquals(4.0d, SayakaBladeStormSkill.FORWARD_STEP, 1.0E-9d);
        assertEquals(1, SayakaBladeStormSkill.RESISTANCE_AMPLIFIER);   //抗性 2
        assertEquals("冷却 60 秒", 1200, SayakaBladeStormSkill.COOLDOWN_TICKS);
        assertEquals("消耗 30 能量", 30, SayakaBladeStormSkill.ENERGY_COST);
    }

    // ───────── 被动：笼手 / 能量口径 ─────────

    /** 笼手上限 3 / 30 秒回复 / 能量 90 / 爆发数值。 */
    @Test
    public void gauntletAndBurstNumbers() {
        assertEquals(3, SayakaGauntletPassive.MAX_CHARGES);
        assertEquals(600, SayakaGauntletPassive.CHARGE_REGEN_TICKS);   //30 秒
        assertEquals(100, SayakaGauntletPassive.ENERGY_CAP);
        assertEquals(8.0d, SayakaGauntletPassive.BURST_TRUE_DAMAGE, 1.0E-9d);
        assertEquals(400, SayakaGauntletPassive.BURST_WEAKNESS_TICKS); //20 秒
        assertEquals(60, SayakaGauntletPassive.BURST_REWARD_ENERGY);
        assertEquals(8.0d, SayakaGauntletPassive.BURST_REWARD_HEAL, 1.0E-9d);
        assertEquals(1, SayakaGauntletPassive.BURST_REWARD_CHARGE);
        assertEquals(20.0d, SayakaGauntletPassive.ALLY_RADIUS, 1.0E-9d);
        assertEquals(1, SayakaGauntletPassive.ALLY_SAN_PER_SEC);
        assertEquals(1, SayakaGauntletPassive.ALLY_ENERGY_PER_SEC);
        //需求 5：以目标为心的范围伤害 + 4 枚 40 格/秒的追猎粒子
        assertEquals(5.0d, SayakaGauntletPassive.BURST_RADIUS, 1.0E-9d);
        assertEquals(4, SayakaGauntletPassive.HEAL_MOTES);
        assertEquals("40 格/秒 = 2 格/刻", 2.0d, SayakaGauntletPassive.MOTE_SPEED_PER_TICK, 1.0E-9d);
        assertEquals("每枚粒子回 8/4 = 2 点", 2.0d,
                SayakaGauntletPassive.BURST_REWARD_HEAL / SayakaGauntletPassive.HEAL_MOTES, 1.0E-9d);
    }

    // ───────── 屏幕 HUD（需求 1 / 2）─────────

    /** 笼手三槽：◆（已注入主武器）占位优先，其后 ◇（笼手可用），其余 □。 */
    @Test
    public void gauntletGlyphs() {
        assertEquals("◇◇◇", SayakaGauntletPassive.chargeGlyphs(3, 0, 3));
        assertEquals("◆◇◇（2 层可用 + 1 层已注入）", "◆◇◇", SayakaGauntletPassive.chargeGlyphs(2, 1, 3));
        assertEquals("◆◆◇", SayakaGauntletPassive.chargeGlyphs(2, 2, 3));
        assertEquals("◆◆◆", SayakaGauntletPassive.chargeGlyphs(3, 3, 3));
        assertEquals("武器层数可占满全部槽位", "◆◆◆", SayakaGauntletPassive.chargeGlyphs(0, 3, 3));
        assertEquals("全空", "□□□", SayakaGauntletPassive.chargeGlyphs(0, 0, 3));
        assertEquals("越界钳制", "◆◆◆", SayakaGauntletPassive.chargeGlyphs(99, 99, 3));
        assertEquals("负值按 0 处理", "□□□", SayakaGauntletPassive.chargeGlyphs(-3, -1, 3));
    }

    /** 斜杠蓄力条：四舍五入到格并夹在 0..slots。 */
    @Test
    public void iaiBarSlots() {
        assertEquals(14, SayakaGauntletPassive.IAI_BAR_SLOTS);
        assertEquals(0, SayakaGauntletPassive.iaiFilledSlots(0d, 14));
        assertEquals(7, SayakaGauntletPassive.iaiFilledSlots(0.5d, 14));
        assertEquals(14, SayakaGauntletPassive.iaiFilledSlots(1d, 14));
        assertEquals(14, SayakaGauntletPassive.iaiFilledSlots(9d, 14));
        assertEquals(0, SayakaGauntletPassive.iaiFilledSlots(-1d, 14));
    }
}
