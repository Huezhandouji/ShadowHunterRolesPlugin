package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistConquestSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistRampageSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistRoarSkill;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 「抗争」的离线机制契约（不碰 Bukkit）。
 *
 * <p>钉住：剑气伤害表、档位表、消退节拍、三个技能的半径/倍率/速度、语音字数与风格。
 */
public class ResistMechanicsTest {

    /**
     * ★ 必须装 KeyFactory 桩：{@code ResistFov} 有 {@code static final NamespacedKey}，
     * 类初始化会调 {@code KeyFactory.Registry.of(...)}；不装桩 ⇒ {@code ExceptionInInitializerError}，
     * 且<b>失败会被 JVM 缓存</b>（同 §4 铁律）。此处不能依赖"别的测试类先跑过"。
     */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    // ───────── 被动：剑气 / 档位 / 消退 ─────────

    /** 剑气物理：10 基础，5 秒 +5，35 秒再 +5。 */
    @Test
    public void swordQiPhysicalTable() {
        assertEquals(10, ResistStrugglePassive.swordQiPhysicalAt(0));
        assertEquals(10, ResistStrugglePassive.swordQiPhysicalAt(4));
        assertEquals(15, ResistStrugglePassive.swordQiPhysicalAt(5));
        assertEquals(15, ResistStrugglePassive.swordQiPhysicalAt(34));
        assertEquals(20, ResistStrugglePassive.swordQiPhysicalAt(35));
        assertEquals(20, ResistStrugglePassive.swordQiPhysicalAt(999));
    }

    /** 档位：每 5 秒一档，8 档封顶（40 秒）。 */
    @Test
    public void tierTable() {
        assertEquals(0, ResistStrugglePassive.tierAt(0));
        assertEquals(0, ResistStrugglePassive.tierAt(4));
        assertEquals(1, ResistStrugglePassive.tierAt(5));
        assertEquals(7, ResistStrugglePassive.tierAt(39));
        assertEquals(8, ResistStrugglePassive.tierAt(40));
        assertEquals("40 秒后不再加粒子 / 音效", 8, ResistStrugglePassive.tierAt(999));
    }

    /** 消退：10 秒（200 刻）起算，每 0.2 秒（4 刻）一层；掉刻按总时长补。 */
    @Test
    public void decaySchedule() {
        assertEquals(0, ResistStrugglePassive.decayStepsAt(199));
        assertEquals(1, ResistStrugglePassive.decayStepsAt(200));
        assertEquals(1, ResistStrugglePassive.decayStepsAt(201));
        assertEquals(2, ResistStrugglePassive.decayStepsAt(202));
        assertEquals("0.1 秒一层：80 刻退 40 层", 1 + 40, ResistStrugglePassive.decayStepsAt(200 + 40 * 2));
    }

    /** 需求常量逐条对齐。 */
    @Test
    public void passiveConstants() {
        assertEquals(999, ResistStrugglePassive.MAX_SECONDS);
        assertEquals(200, ResistStrugglePassive.COMBAT_IDLE_TICKS);   //10 秒
        assertEquals(2, ResistStrugglePassive.DECAY_INTERVAL_TICKS);  //0.1 秒（2026-10-19 调快）
        assertEquals(4, ResistStrugglePassive.CD_ACCEL_TICKS);        //0.2 秒/秒（不受消退影响）
        assertEquals(8, ResistStrugglePassive.MAX_ORBIT_PARTICLES);
        assertEquals(3, ResistStrugglePassive.SWORD_QI_SPECIAL_MIN);
        assertEquals(6, ResistStrugglePassive.SWORD_QI_SPECIAL_MAX);
        assertTrue(ResistStrugglePassive.reached(10, 10));
        assertFalse(ResistStrugglePassive.reached(9, 10));
    }

    // ───────── 主武器 ─────────

    @Test
    public void weaponNumbers() {
        assertEquals("0.6 秒", 12, ResistMainWeapon.ATTACK_COOLDOWN_TICKS);
        assertEquals(5.0d, ResistMainWeapon.AOE_RADIUS, 1.0E-9d);
        assertEquals("击退 1（2026-10-31 用户裁定，原 3）", 1.0d, ResistMainWeapon.KNOCKBACK_STRENGTH, 1.0E-9d);
        assertEquals("▓抗争▓", ResistMainWeapon.WEAPON_NAME);
    }

    // ───────── 三个主动 ─────────

    @Test
    public void roarNumbers() {
        assertEquals(200, ResistRoarSkill.COOLDOWN_TICKS);   //10 秒
        assertEquals(6.0d, ResistRoarSkill.RADIUS, 1.0E-9d);
        assertEquals(5.0d, ResistRoarSkill.PHYSICAL, 1.0E-9d);
        assertEquals(3.0d, ResistRoarSkill.KNOCKBACK, 1.0E-9d);
        assertEquals(15, ResistRoarSkill.TE_HEAL);
        assertEquals(2, ResistRoarSkill.BATTLE_ADD);
    }

    @Test
    public void conquestNumbers() {
        assertEquals(300, ResistConquestSkill.COOLDOWN_TICKS);   //15 秒
        assertEquals("0.5 秒引导", 10, ResistConquestSkill.CHANNEL_TICKS);
        assertEquals("0.4 秒间隔", 8, ResistConquestSkill.GAP_1_TICKS);
        assertEquals("0.7 秒间隔", 14, ResistConquestSkill.GAP_2_TICKS);
        assertEquals(8.0d, ResistConquestSkill.RADIUS, 1.0E-9d);
        assertEquals(9.0d, ResistConquestSkill.FINAL_RADIUS, 1.0E-9d);
        assertEquals(1.5d, ResistConquestSkill.MULT_FIRST, 1.0E-9d);
        assertEquals(2.0d, ResistConquestSkill.MULT_FINAL, 1.0E-9d);
        assertEquals(2.0d, ResistConquestSkill.KNOCKBACK, 1.0E-9d);
        assertEquals(2, ResistConquestSkill.SLAM_TICKS);          //0.1 秒
        assertEquals("80 格/秒 = 4 格/刻", 4.0d, ResistConquestSkill.SLAM_VELOCITY, 1.0E-9d);
        assertEquals(3, ResistConquestSkill.BATTLE_ADD);
        assertEquals(20, ResistConquestSkill.SPEED_REWARD_TICKS); //1 秒速度 III
    }

    @Test
    public void rampageNumbers() {
        assertEquals(800, ResistRampageSkill.COOLDOWN_TICKS);   //40 秒
        assertEquals("0.8 秒引导", 16, ResistRampageSkill.CHANNEL_TICKS);
        assertEquals("25 格/秒 = 1.25 格/刻", 1.25d, ResistRampageSkill.DASH_STEP, 1.0E-9d);
        assertEquals(30d, ResistRampageSkill.DASH_DISTANCE, 1.0E-9d);
        assertEquals(5d, ResistRampageSkill.PATH_RADIUS, 1.0E-9d);
        assertEquals(10d, ResistRampageSkill.LAND_RADIUS, 1.0E-9d);
        assertEquals(8d, ResistRampageSkill.PATH_PHYSICAL, 1.0E-9d);
        assertEquals(4, ResistRampageSkill.PATH_SPECIAL_MIN);
        assertEquals(6, ResistRampageSkill.PATH_SPECIAL_MAX);
        assertEquals(10d, ResistRampageSkill.LAND_PHYSICAL, 1.0E-9d);
        assertEquals(5, ResistRampageSkill.LAND_SPECIAL_MIN);
        assertEquals(10, ResistRampageSkill.LAND_SPECIAL_MAX);
        assertEquals(3, ResistRampageSkill.BATTLE_ADD);
    }

    /** HUD：三位补零、无秒数后缀（需求 1）。 */
    @Test
    public void hudFormat() {
        assertEquals("斗争// 000 //不停", ResistStrugglePassive.hudText(0));
        assertEquals("斗争// 007 //不停", ResistStrugglePassive.hudText(7));
        assertEquals("斗争// 999 //不停", ResistStrugglePassive.hudText(1234));
    }

    /** 二技能的巨剑挥砍：时长 + 真实刀路参数（需求 7）。 */
    @Test
    public void swordSwingGeometry() {
        assertEquals(5, ResistSwordSwing.DURATION_TICKS);
        assertEquals("剑柄在身体附近（剑长 6）", 3.0d, ResistSwordSwing.SWING_RADIUS, 1.0E-9d);
        assertEquals("左右各 105°", 105.0d, ResistSwordSwing.SWING_ARC_DEGREES, 1.0E-9d);
    }

    /** 消退节拍 0.1 秒一层；CD 减免仍是 0.2 秒/秒（两者解耦）。 */
    @Test
    public void decayIsFasterThanCdAccel() {
        assertEquals("0.1 秒一层", 2, ResistStrugglePassive.DECAY_INTERVAL_TICKS);
        assertEquals("CD 减免保持 0.2 秒/秒", 4, ResistStrugglePassive.CD_ACCEL_TICKS);
        assertTrue("消退不得连带削弱 CD 减免",
                ResistStrugglePassive.DECAY_INTERVAL_TICKS < ResistStrugglePassive.CD_ACCEL_TICKS);
    }

    /** 语音播报节奏（2026-10-19 需求）：空闲 5 秒一判、30% 开口；受伤 20%。 */
    @Test
    public void voiceBroadcastCadence() {
        assertEquals("空闲周期 = 5 秒", 100, ResistFloatingTextComponent.IDLE_PERIOD_TICKS);
        assertEquals("空闲开口概率 30%", 0.3d, ResistFloatingTextComponent.IDLE_CHANCE, 1.0E-9d);
        assertEquals("受伤开口概率 20%", 0.2d, ResistFloatingTextComponent.HURT_CHANCE, 1.0E-9d);
        assertTrue("概率必须小于 1（稀疏化）", ResistFloatingTextComponent.IDLE_CHANCE < 1d
                && ResistFloatingTextComponent.HURT_CHANCE < 1d);
    }

    /** 解控判定小工具：只有"本来被控 + 解完干净"才算成功解控（2026-10-19 需求）。 */
    @Test
    public void controlClearVerdict() {
        int stunSlow = ResistControl.BIT_STUN | ResistControl.BIT_SLOWNESS;
        assertFalse("本来没被控 ⇒ 不算成功解控", ResistControl.isSuccessfullyCleared(0, 0));
        assertTrue("有控 → 干净 = 成功", ResistControl.isSuccessfullyCleared(stunSlow, 0));
        assertFalse("解完还留着 = 失败", ResistControl.isSuccessfullyCleared(stunSlow, ResistControl.BIT_SLOWNESS));
        assertFalse("本来就没控、解完也没控 ≠ 成功", ResistControl.isSuccessfullyCleared(0, ResistControl.BIT_STUN));
        assertEquals("残留位", ResistControl.BIT_SLOWNESS,
                ResistControl.remaining(stunSlow, ResistControl.BIT_SLOWNESS | ResistControl.BIT_BLINDNESS));
        assertFalse(ResistControl.hasAnyControl(0));
        assertTrue(ResistControl.hasAnyControl(ResistControl.BIT_JUMP_LOCK));
        assertEquals("NONE", ResistControl.describe(0));
        assertEquals("STUN+SLOWNESS", ResistControl.describe(stunSlow));
        //九个位互不重叠（同一玩家多控叠加时不会互相覆盖）
        int all = ResistControl.BIT_STUN | ResistControl.BIT_SILENCE | ResistControl.BIT_SLOWNESS
                | ResistControl.BIT_JUMP_LOCK | ResistControl.BIT_BLINDNESS | ResistControl.BIT_DARKNESS
                | ResistControl.BIT_WEAKNESS | ResistControl.BIT_FATIGUE | ResistControl.BIT_NAUSEA;
        assertEquals("9 个独立位", 9, Integer.bitCount(all));
    }

    /** 一技能：0.3 秒引导（缓慢 IV）+ 巨剑上升约 1.7 倍速（2026-10-19 需求）。 */
    @Test
    public void roarChannelAndSwordRise() {
        assertEquals("0.3 秒 = 6 刻", 6, ResistRoarSkill.CHANNEL_TICKS);
        assertEquals("缓慢 IV", 3, ResistRoarSkill.CHANNEL_SLOW_AMPLIFIER);
        assertEquals("巨剑上升 8 刻 → 5 刻（≈1.6 倍速，目标 1.7）", 5, ResistRoarSkill.SWORD_RISE);
        assertTrue("必须快于原来的 8 刻", ResistRoarSkill.SWORD_RISE < 8);
        assertEquals("挣脱台词错开 8 刻（避开 ≥7 刻的台词节流）", 8,
                ResistRoarSkill.CONTROLLED_LINE_DELAY_TICKS);
    }

    /** 普攻命中粒子高度下移（2026-10-19：不挡敌人上半身与准星）。 */
    @Test
    public void impactHeightLowered() {
        assertEquals("腰胯以下", 0.55d, ResistVfx.IMPACT_HEIGHT, 1.0E-9d);
        assertTrue("必须明显低于胸口高度", ResistVfx.IMPACT_HEIGHT < 0.8d);
    }

    /**
     * 二技能巨剑的摆平角（2026-10-18 23:02）：模型 45° 斜向 ⇒ 横向补 45° 才能让剑身水平
     * （"补 45° 到正、再横向 90° 到水平" 合计 -45°，且必须作为**先应用**的旋转）。
     */
    @Test
    public void swingBladeIsHorizontal() {
        assertEquals("绕 Z 轴 -45° = 45°斜 → 水平", -45.0d, ResistSwordSwing.BLADE_FLAT_DEGREES, 1.0E-9d);
        assertEquals("前两刀绕剑身中轴线自转 90°（斩击姿态，仅前两刀）", 90.0d,
                ResistSwordSwing.BLADE_TWIST_DEGREES, 1.0E-9d);
    }

    /** 前两刀绕垂直轴平挥、方向相反；第三刀是竖直抡砸（2026-10-18 22:40 需求 1）。 */
    @Test
    public void swordSwingAxisRotation() {
        //第 1 刀：右 → 左（-105 → +105）
        assertEquals(-105.0d, ResistSwordSwing.sweepAngleDegrees(1, 0d), 1.0E-9d);
        assertEquals(0.0d, ResistSwordSwing.sweepAngleDegrees(1, 0.5d), 1.0E-9d);
        assertEquals(105.0d, ResistSwordSwing.sweepAngleDegrees(1, 1d), 1.0E-9d);
        //第 2 刀：左 → 右（反向）
        assertEquals(105.0d, ResistSwordSwing.sweepAngleDegrees(2, 0d), 1.0E-9d);
        assertEquals(-105.0d, ResistSwordSwing.sweepAngleDegrees(2, 1d), 1.0E-9d);
        //第 3 刀：举过头顶 → 砸进地面
        assertEquals(85.0d, ResistSwordSwing.slamPitchDegrees(0d), 1.0E-9d);
        assertEquals(-88.0d, ResistSwordSwing.slamPitchDegrees(1d), 1.0E-9d);
        assertTrue("半程已抡到水平以下", ResistSwordSwing.slamPitchDegrees(0.5d) < 0d);
        assertFalse(ResistSwordSwing.isSlamStage(2));
        assertTrue(ResistSwordSwing.isSlamStage(3));
    }

    /** 三种剑气下标：只有「裂地砸落」触发巨剑砸落（需求 9）。 */
    @Test
    public void swordQiVariants() {
        assertEquals(0, ResistMainWeapon.VARIANT_CRESCENT);
        assertEquals(1, ResistMainWeapon.VARIANT_LANCE);
        assertEquals(2, ResistMainWeapon.VARIANT_SLAM);
        assertEquals(3, ResistMainWeapon.VARIANT_COUNT);
    }

    @Test
    public void fovPulseAmount() {
        assertEquals(0.85d, ResistFov.pulseAmount(), 1.0E-9d);
    }

    // ───────── 语音 ─────────

    /** 每个时刻都有台词；全部 ≤24 字；一句不重。 */
    @Test
    public void voiceLinesAreCompleteAndShort() {
        ResistFloatingTextComponent voice = newVoice();
        for (String moment : ResistFloatingTextComponent.ALL_MOMENTS) {
            assertFalse("时刻 " + moment + " 没有台词",
                    voice.linesOf(moment).isEmpty());
        }
        Set<String> seen = new HashSet<>();
        for (String line : ResistFloatingTextComponent.allLines()) {
            assertTrue("超长（>24 字）: " + line, line.length() <= 24);
            assertTrue("重复台词: " + line, seen.add(line));
        }
        assertTrue("台词总量应 ≥ 40 句", seen.size() >= 40);
    }

    /** 三个技能点名的台词必须在库里（逐字）。 */
    @Test
    public void requiredSkillLinesExist() {
        Set<String> lines = new HashSet<>(ResistFloatingTextComponent.allLines());
        for (String line : new String[]{
                "压迫不止，抗争无休", "奴役永存，反抗永在", "痛苦回眸，死亡不现",
                "你所希望的黎明真的是黎明吗？", "你看见了那无边无底的黑暗吗？",
                "我要在谷底，撕裂你", "你们忘了你们所拥有的，你们忘了你们所失去的！",
                "黑暗，莫想笼罩！", "我不再祈求白夜，我要摧毁黎明！",
                "天际线！天际线。。。。我要斩开这一切！",
                "你们真的能走出这里吗！", "让白昼来，让白昼来审判！"}) {
            assertTrue("缺需求台词: " + line, lines.contains(line));
        }
    }

    /** 技能台词加粗 + 放大（白紫主题）。 */
    @Test
    public void skillVoiceIsBoldAndLarge() {
        ResistFloatingTextComponent voice = newVoice();
        for (String moment : new String[]{
                ResistFloatingTextComponent.MOMENT_SKILL_ROAR,
                ResistFloatingTextComponent.MOMENT_SKILL_CONQUEST,
                ResistFloatingTextComponent.MOMENT_SKILL_RAMPAGE}) {
            assertTrue("技能台词必须加粗", voice.lookFor(moment).bold());
            assertTrue("技能台词必须放大（≥1.3）", voice.scaleFor(moment) >= 1.3f);
        }
        assertNotNull(voice.lookFor(ResistFloatingTextComponent.MOMENT_IDLE));
        assertTrue("空闲也要加粗（需求：这些字体加粗放大）", voice.lookFor(ResistFloatingTextComponent.MOMENT_IDLE).bold());
    }

    private static ResistFloatingTextComponent newVoice() {
        return new ResistFloatingTextComponent(ResistFloatingTextComponent.ID,
                new ComponentServicesPort(null, null, null),
                new ResistFloatingTextComponent.Specification());
    }
}
