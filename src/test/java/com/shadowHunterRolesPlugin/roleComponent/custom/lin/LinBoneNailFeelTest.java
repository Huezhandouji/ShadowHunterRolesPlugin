package com.shadowHunterRolesPlugin.roleComponent.custom.lin;

import com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill.LinBoneNailSkill;
import org.bukkit.Location;
import org.bukkit.util.Vector;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 「林 · 透骨钉」2026-10-08 追加的手感 / 几何**纯函数**测试。
 *
 * <h2>为什么只测这几条</h2>
 * 本次追加里，绝大多数是"播个音效 / spawn 个粒子"的副作用（离线跑不了，也无需跑）；
 * 但有两处**纯计算**是本轮新写的，且写错了**不会报错、只会手感诡异** ——
 * 正是最该被闸门盯住的类型：
 * <ul>
 *   <li>{@link LinBoneNailSkill#clampPitch(float)} —— 后坐力抬头量必须夹在合法 pitch 区间，
 *       否则"抬到 -95°"会让视角在客户端被原版夹回、观感是"抬一半又掉回来"；</li>
 *   <li>{@link LinVfx#muzzleLocation(Location, Vector)} —— 枪口位置必须跟着朝向走，
 *       写错会变成"爆炸粒子永远出现在固定方向"（抬头开枪时最明显）。</li>
 * </ul>
 */
public class LinBoneNailFeelTest {

    // ───────── 后坐力：pitch 夹取 ─────────

    /** 常规区间原样返回（不夹）。 */
    @Test
    public void clampPitchKeepsNormalValues() {
        assertEquals(0f, LinBoneNailSkill.clampPitch(0f), 1.0E-6f);
        assertEquals(45f, LinBoneNailSkill.clampPitch(45f), 1.0E-6f);
        assertEquals(-45f, LinBoneNailSkill.clampPitch(-45f), 1.0E-6f);
        assertEquals(-90f, LinBoneNailSkill.clampPitch(-90f), 1.0E-6f);
        assertEquals(90f, LinBoneNailSkill.clampPitch(90f), 1.0E-6f);
    }

    /**
     * 越界必须夹住：向天开枪时（pitch 已接近 -90）再抬会越过 -90，
     * 不夹的话客户端会把它当非法值处理，观感是"抬到一半又弹回来"。
     */
    @Test
    public void clampPitchClampsBeyondLimits() {
        assertEquals(-90f, LinBoneNailSkill.clampPitch(-95f), 1.0E-6f);
        assertEquals(-90f, LinBoneNailSkill.clampPitch(-200f), 1.0E-6f);
        assertEquals(90f, LinBoneNailSkill.clampPitch(95f), 1.0E-6f);
        assertEquals(90f, LinBoneNailSkill.clampPitch(1000f), 1.0E-6f);
    }

    // ───────── 枪口位置：跟着朝向走 ─────────

    /** 枪口在眼睛前方固定距离、且略低（"端在手上"而不是"从眼睛里射出来"）。 */
    @Test
    public void muzzleSitsAheadOfEyeAndSlightlyLower() {
        Location eye = new Location(null, 10d, 64d, 10d, 0f, 0f);
        // 朝向世界 -Z（yaw 0 的水平朝向）
        Vector facing = new Vector(0d, 0d, -1d);

        Location muzzle = LinVfx.muzzleLocation(eye, facing);
        assertNotNull(muzzle);
        // 必须落在 -Z 方向（前方）
        assertTrue("枪口应在朝向前方（-Z），实际 z=" + muzzle.getZ(), muzzle.getZ() < eye.getZ());
        // 且略低于眼睛
        assertTrue("枪口应略低于眼睛，实际 y=" + muzzle.getY(), muzzle.getY() < eye.getY());
        // 水平朝向应与视线一致（x 不偏）
        assertEquals(eye.getX(), muzzle.getX(), 1.0E-6d);
    }

    /** 换一个朝向（世界 +X）：枪口要跟着转到 +X，不能仍停在 -Z。 */
    @Test
    public void muzzleFollowsFacingDirection() {
        Location eye = new Location(null, 0d, 64d, 0d, 0f, 0f);
        Vector facing = new Vector(1d, 0d, 0d);

        Location muzzle = LinVfx.muzzleLocation(eye, facing);
        assertNotNull(muzzle);
        assertTrue("枪口应在朝向前方（+X），实际 x=" + muzzle.getX(), muzzle.getX() > eye.getX());
        assertEquals(eye.getZ(), muzzle.getZ(), 1.0E-6d);
    }

    /**
     * 视线里带大量俯仰分量时，**水平**朝向仍要稳定（枪是横着端的，不跟着抬头往天上指）。
     * 这正是"低头瞄准地面也要能正常开枪"的同一条几何前提。
     */
    @Test
    public void muzzleUsesHorizontalFacingOnly() {
        Location eye = new Location(null, 0d, 64d, 0d, 0f, 0f);
        // 极度低头（几乎垂直向下）——水平分量应仍解析为 -Z
        Vector steepDown = new Vector(0d, -1d, -0.001d);

        Location muzzle = LinVfx.muzzleLocation(eye, steepDown);
        assertNotNull(muzzle);
        assertTrue("低头时枪口仍应落在水平前方，实际 z=" + muzzle.getZ(), muzzle.getZ() < eye.getZ());
        // 不应因为"向下看"就把枪口拉到脚底（y 只掉固定的那一点）
        assertTrue("枪口不应被俯仰带下去太多，实际 y=" + muzzle.getY(), muzzle.getY() > eye.getY() - 1.0d);
    }

    /** 退化朝向（null）⇒ 退回世界 -Z，不抛异常。 */
    @Test
    public void muzzleHandlesNullFacing() {
        Location eye = new Location(null, 0d, 64d, 0d, 0f, 0f);
        Location muzzle = LinVfx.muzzleLocation(eye, null);
        assertNotNull(muzzle);
        assertTrue("null 朝向应退回 -Z，实际 z=" + muzzle.getZ(), muzzle.getZ() < eye.getZ());
    }

    /** null 眼睛 ⇒ null（静默，不抛）。 */
    @Test
    public void muzzleHandlesNullEye() {
        assertEquals(null, LinVfx.muzzleLocation(null, new Vector(0d, 0d, -1d)));
    }
}
