package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 「梅妻鹤子」的全部粒子画法 —— 纯静态、无状态、无调度。
 *
 * <p>配色铁律：**红**（#FF2020）+ **金**（#FFC83C）为主，黑（#1A0505）与紫（#8A2BE2）只在
 * 「满充能」与「0 档爆发」两处出现。所有 {@code DUST} 调用都把 {@code DustOptions} 放在**末位实参**
 * （工程闸门 {@code ParticleDataContractTest} 的判据），需要 data 的粒子一律显式传。
 */
public final class MeiqiheziVfx {

    /** 主红。 */
    public static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xFF, 0x20, 0x20), 1.1f);

    /** 副红（更暗，做拖尾）。 */
    public static final Particle.DustOptions RED_DARK = new Particle.DustOptions(Color.fromRGB(0x9E, 0x0B, 0x0B), 1.0f);

    /** 金。 */
    public static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(0xFF, 0xC8, 0x3C), 1.0f);

    /** 黑（满充能菱形）。 */
    public static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(0x14, 0x04, 0x04), 1.2f);

    /** 紫（只在 0 档爆发里少量出现）。 */
    public static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(0x8A, 0x2B, 0xE2), 1.0f);

    /** 白（闪电）。 */
    public static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(0xF2, 0xF6, 0xFF), 0.9f);

    private MeiqiheziVfx() {
    }

    // ───────── 普攻：准星前的半圆弧剑气 ─────────

    /**
     * 在准星前方划出一道（或两道）半圆弧剑气。
     *
     * <p><b>★★ 2026-10-21 修：弧面必须"正对向前"</b>。旧写法把弧画在
     * {@code (right, up)} 张成的平面里 —— 那个平面**垂直于视线**，于是剑气像一面挡在
     * 脸前的圆盘，而不是"朝前扫出去"。现在改为：弧面由 **视线方向 {@code dir}** 与
     * 面内横向 {@code inPlane = normal × dir} 张成，弧的**顶点沿准星方向鼓出**，
     * 两端落在左右两侧 ⇒ 剑气是"从左手划到右手、朝正前方劈出"的一道弧。
     *
     * @param style 0=横扫（水平面）/ 1=上挑 / 2=下劈 / 3=竖斩（垂直面），每次攻击随机取一种
     */
    public static void slashArc(Player player, int style) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector worldUp = new Vector(0, 1, 0);
        Vector right = dir.clone().crossProduct(worldUp);
        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();

        int mode = Math.floorMod(style, 4);
        // 弧面的法线：横扫 = 世界上方；竖斩 = 右方；上挑/下劈 = 绕视线各偏 38°
        Vector normal;
        if (mode == 3) {
            normal = right.clone();
        } else if (mode == 0) {
            normal = worldUp.clone();
        } else {
            normal = rotate(worldUp.clone(), dir, Math.toRadians(mode == 1 ? 38d : -38d));
        }
        Vector inPlane = normal.clone().crossProduct(dir);
        if (inPlane.lengthSquared() < 1e-6) {
            inPlane = right.clone();
        }
        inPlane.normalize();

        // 2026-10-21 需求 3：整体下移 0.35 格 —— 原来正好压在准星上，挡视野
        Location base = eye.clone().add(0d, -0.35d, 0d).add(dir.clone().multiply(1.35d));
        double radius = 1.55d;
        int points = 22;
        for (int i = 0; i <= points; i++) {
            double theta = Math.toRadians(-75d + 150d * i / (double) points);
            Vector offset = dir.clone().multiply(Math.cos(theta) * radius)
                    .add(inPlane.clone().multiply(Math.sin(theta) * radius));
            Location at = base.clone().add(offset);
            Particle.DustOptions options = (i % 2 == 0) ? RED : GOLD;
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, options);
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, base, 1, 0d, 0d, 0d, 0d);

        // 少量火星向准星前方散开，增加冲击感
        world.spawnParticle(Particle.CRIT, base, 6, 0.35d, 0.35d, 0.35d, 0.05d);
        world.spawnParticle(Particle.END_ROD, base, 2, 0.15d, 0.15d, 0.15d, 0.01d);
    }

    /** 命中敌人：胸口竖菱形（不遮挡视线，尺寸克制）。 */
    public static void hitMarker(Player victim, int phase) {
        if (victim == null || !victim.isOnline()) {
            return;
        }
        diamond(victim, 0.42d, 0.20d, phase, RED, GOLD);
        victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0d, 1.0d, 0d),
                4, 0.16d, 0.24d, 0.16d, 0.02d);
    }

    /** 敌人身上"翻滚的菱形"（圆弧斩命中后 5 刻内的持续表现）。 */
    public static void rollingDiamond(Player victim, int phase) {
        if (victim == null || !victim.isOnline()) {
            return;
        }
        diamond(victim, 0.55d, 0.26d, phase * 3, RED, GOLD);
    }

    // ───────── 充能 ─────────

    /** 每涨一层：周身红色粒子涌现（三层环 + 火星）。 */
    public static void chargeBurst(Player player) {
        Location base = player.getLocation().add(0d, 0.9d, 0d);
        World world = base.getWorld();
        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.55d + ring * 0.42d;
            for (int i = 0; i < 14; i++) {
                double angle = Math.toRadians(i * (360d / 14d) + ring * 12d);
                Location at = base.clone().add(Math.cos(angle) * radius, 0.05d * ring, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                        ring == 2 ? GOLD : RED);
            }
        }
        world.spawnParticle(Particle.CRIT, base, 26, 0.55d, 0.75d, 0.55d, 0.12d);
        world.spawnParticle(Particle.END_ROD, base, 6, 0.3d, 0.5d, 0.3d, 0.02d);
    }

    /** 满层：背后黑红重叠的竖菱形（每 2 刻画一帧，{@code phase} 让它缓慢自转）。 */
    public static void chargeAura(Player player, int phase) {
        Location back = behind(player, 0.95d).add(0d, 1.05d, 0d);
        diamondAt(back, 1.05d, 0.46d, phase, BLACK, RED);
        Location inner = behind(player, 0.72d).add(0d, 1.05d, 0d);
        diamondAt(inner, 0.78d, 0.34d, -phase, RED, GOLD);
        player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, back, 2, 0.16d, 0.35d, 0.16d, 0.0d);
    }

    /** 满层每 2 秒一次的"放大闪动"：更大的黑红菱形 + 外扩环（2026-10-21 需求 2）。 */
    public static void chargeAuraBurst(Player player) {
        Location back = behind(player, 1.15d).add(0d, 1.15d, 0d);
        diamondAt(back, 1.95d, 0.95d, 0, BLACK, RED);
        diamondAt(back, 1.50d, 0.72d, 45, RED, GOLD);
        World world = back.getWorld();
        for (int i = 0; i < 24; i++) {
            double angle = Math.toRadians(i * 15d);
            Location at = back.clone().add(Math.cos(angle) * 1.55d, Math.sin(angle) * 0.18d,
                    Math.sin(angle) * 1.55d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? RED : BLACK);
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, back, 10, 0.45d, 0.75d, 0.45d, 0.0d);
    }

    /** 满层额外的那圈黑红光晕（低频）。 */
    public static void fullChargePulse(Player player) {
        Location base = player.getLocation().add(0d, 0.2d, 0d);
        for (int i = 0; i < 18; i++) {
            double angle = Math.toRadians(i * 20d);
            Location at = base.clone().add(Math.cos(angle) * 1.35d, 0d, Math.sin(angle) * 1.35d);
            base.getWorld().spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? BLACK : RED_DARK);
        }
    }

    // ───────── 投掷 ─────────

    /** 飞行中的红金旋转拖尾。 */
    public static void throwTrail(Location location, int phase) {
        World world = location.getWorld();
        for (int i = 0; i < 3; i++) {
            double angle = Math.toRadians(phase * 26d + i * 120d);
            double radius = 0.42d;
            Location at = location.clone().add(Math.cos(angle) * radius, Math.sin(angle) * radius * 0.6d,
                    Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i == 1 ? GOLD : RED);
        }
        if (phase % 3 == 0) {
            world.spawnParticle(Particle.END_ROD, location, 1, 0.06d, 0.06d, 0.06d, 0.0d);
        }
    }

    /** 插在地面/敌人身上时的低频余韵。 */
    public static void stuckPulse(Location location, int phase) {
        World world = location.getWorld();
        double angle = Math.toRadians(phase * 18d);
        for (int i = 0; i < 6; i++) {
            double a = angle + i * Math.PI / 3d;
            Location at = location.clone().add(Math.cos(a) * 0.35d, 0.1d + (i % 2) * 0.25d, Math.sin(a) * 0.35d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? RED : GOLD);
        }
    }

    /** 命中冲击：环 + 竖菱形；充能时追加白色闪电状粒子垂直劈向地面。 */
    public static void impact(Location location, boolean charged) {
        World world = location.getWorld();
        for (int ring = 0; ring < 3; ring++) {
            double radius = 0.6d + ring * 0.55d;
            for (int i = 0; i < 16; i++) {
                double angle = Math.toRadians(i * 22.5d + ring * 8d);
                Location at = location.clone().add(Math.cos(angle) * radius, 0.05d, Math.sin(angle) * radius);
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, ring == 1 ? GOLD : RED);
            }
        }
        world.spawnParticle(Particle.CRIT, location, 30, 0.5d, 0.4d, 0.5d, 0.2d);
        world.spawnParticle(Particle.EXPLOSION, location, 1);
        if (charged) {
            lightningToGround(location);
        }
    }

    /** 白色闪电：从命中点垂到地面，沿途锯齿抖动。 */
    public static void lightningToGround(Location from) {
        World world = from.getWorld();
        int maxY = 12;
        for (int i = 0; i <= maxY; i++) {
            Location at = from.clone().add((Math.random() - 0.5d) * 0.18d, -i * 0.55d,
                    (Math.random() - 0.5d) * 0.18d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, WHITE);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.ELECTRIC_SPARK, at, 1, 0.04d, 0.04d, 0.04d, 0.0d);
            }
            if (at.getBlock().getType().isSolid()) {
                break;
            }
        }
    }

    /** 拾取：收束光环。 */
    public static void pickup(Location location) {
        World world = location.getWorld();
        for (int i = 0; i < 20; i++) {
            double angle = Math.toRadians(i * 18d);
            double radius = 1.1d - (i % 5) * 0.15d;
            Location at = location.clone().add(Math.cos(angle) * radius, 0.15d + (i % 3) * 0.1d,
                    Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? GOLD : RED);
        }
        world.spawnParticle(Particle.END_ROD, location, 10, 0.3d, 0.4d, 0.3d, 0.02d);
    }

    // ───────── 技能 ─────────

    /** 血腥连斩：突进残影。 */
    public static void dashTrail(Location location) {
        World world = location.getWorld();
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(i * 36d);
            Location at = location.clone().add(Math.cos(angle) * 0.75d, 0.5d + Math.sin(angle) * 0.6d,
                    Math.sin(angle) * 0.75d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? RED : RED_DARK);
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, location.clone().add(0d, 1d, 0d), 1, 0d, 0d, 0d, 0d);
    }

    /** 0 档引导期间的**伤害范围提示**（半径 12，红 + 金 + 少量紫）。 */
    public static void burstRange(Location center, double radius, int phase) {
        World world = center.getWorld();
        double pulse = radius + Math.sin(phase * 0.7d) * 0.12d;
        for (int i = 0; i < 72; i++) {
            double angle = Math.toRadians(i * 5d + phase * 3d);
            Location at = center.clone().add(Math.cos(angle) * pulse, 0.12d, Math.sin(angle) * pulse);
            Particle.DustOptions options = (i % 9 == 0) ? PURPLE : (i % 2 == 0 ? RED : GOLD);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, options);
        }
        for (int i = 0; i < 24; i++) {
            double angle = Math.toRadians(i * 15d - phase * 4d);
            Location at = center.clone().add(Math.cos(angle) * (radius + 0.3d), 0.05d,
                    Math.sin(angle) * (radius + 0.3d));
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, BLACK);
        }
    }

    /**
     * 圆弧斩引导期间的**范围指示环**：固定画在真实半径上（不收缩），红色为主、金点点缀，
     * 带轻微呼吸脉动 ⇒ 一眼能看出"这一刀打多远"。
     */
    public static void channelRange(Location center, double radius, int phase) {
        World world = center.getWorld();
        double pulse = radius + Math.sin(phase * 0.55d) * 0.10d;
        int points = 72;
        for (int i = 0; i < points; i++) {
            double angle = Math.toRadians(i * (360d / points) + phase * 2d);
            Location at = center.clone().add(Math.cos(angle) * pulse, 0.10d, Math.sin(angle) * pulse);
            Particle.DustOptions options = (i % 6 == 0) ? GOLD : RED;
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, options);
        }
        // 外圈虚环，加厚边界
        for (int i = 0; i < 36; i++) {
            double angle = Math.toRadians(i * 10d - phase * 3d);
            Location at = center.clone().add(Math.cos(angle) * (radius + 0.22d), 0.06d,
                    Math.sin(angle) * (radius + 0.22d));
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, RED_DARK);
        }
    }

    /**
     * 圆弧斩出刀的**波浪式剑气**：从左手边开始、向右推进（角度递减），
     * 半径按 {@code sin(3θ + phase)} 起伏 ⇒ 看上去是一圈"波浪"在绕圈。
     */
    public static void shockwaveWave(Location center, Vector forward, double radius,
                                     double startDeg, int phase) {
        World world = center.getWorld();
        Vector flat = forward.clone().setY(0d);
        if (flat.lengthSquared() < 1e-6) {
            flat = new Vector(0, 0, 1);
        }
        flat.normalize();
        Vector left = new Vector(0, 1, 0).crossProduct(flat).normalize();

        for (int layer = 0; layer < 3; layer++) {
            double baseRadius = radius - layer * 0.30d;
            for (int i = 0; i <= 24; i++) {
                double deg = startDeg - i * (70d / 24d);
                double rad = Math.toRadians(deg);
                double wave = Math.sin(rad * 3d + phase * 0.8d + layer * 0.6d) * 0.34d;
                double r = baseRadius + wave;
                Location at = center.clone()
                        .add(flat.clone().multiply(Math.cos(rad) * r))
                        .add(left.clone().multiply(Math.sin(rad) * r))
                        .add(0d, 0.35d + layer * 0.28d, 0d);
                Particle.DustOptions options = (i % 2 == 0) ? RED : GOLD;
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, options);
            }
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, center.clone().add(0d, 1d, 0d), 1, 0d, 0d, 0d, 0d);
    }

    /** 圆弧斩命中单个敌人：胸口红金爆点 + 竖菱形（不挡视线）。 */
    public static void slashHit(Player victim, int phase) {
        if (victim == null || !victim.isOnline()) {
            return;
        }
        World world = victim.getWorld();
        Location at = victim.getLocation().add(0d, 1.0d, 0d);
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5d + phase * 17d);
            Location point = at.clone().add(Math.cos(angle) * 0.65d, Math.sin(angle) * 0.55d,
                    Math.sin(angle) * 0.65d);
            world.spawnParticle(Particle.DUST, point, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? RED : GOLD);
        }
        world.spawnParticle(Particle.CRIT, at, 10, 0.28d, 0.35d, 0.28d, 0.12d);
        world.spawnParticle(Particle.ENCHANTED_HIT, at, 6, 0.22d, 0.30d, 0.22d, 0.03d);
        diamond(victim, 0.55d, 0.24d, phase * 5, RED, GOLD);
    }

    /** 0 档：巨型菱形爆发（红 / 金 / 少量紫）。 */
    public static void diamondBurst(Location center, double radius) {
        World world = center.getWorld();
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0d, 1d, 0d), 3, 1.2d, 0.8d, 1.2d, 0.0d);
        // 竖直大菱形（三层，逐层放大）
        for (int layer = 0; layer < 3; layer++) {
            double r = radius * (0.45d + layer * 0.3d);
            int points = 40;
            for (int i = 0; i < points; i++) {
                double t = i / (double) points * Math.PI * 2d;
                double x = Math.sin(t) * Math.abs(Math.cos(t)) * r;
                double y = Math.cos(t) * Math.abs(Math.cos(t)) * r * 0.9d;
                Location at = center.clone().add(x, 1.1d + y, 0d);
                Particle.DustOptions options = (i % 7 == 0) ? PURPLE : (i % 2 == 0 ? RED : GOLD);
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, options);
            }
        }
        // 水平双环
        for (int ring = 0; ring < 2; ring++) {
            double r = ring == 0 ? radius * 0.6d : radius;
            for (int i = 0; i < 36; i++) {
                double angle = Math.toRadians(i * 10d);
                Location at = center.clone().add(Math.cos(angle) * r, 0.2d + ring * 0.9d, Math.sin(angle) * r);
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, (i % 9 == 0) ? PURPLE : GOLD);
            }
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center.clone().add(0d, 1d, 0d), 30, 1.1d, 0.9d, 1.1d, 0.02d);
        world.spawnParticle(Particle.END_ROD, center.clone().add(0d, 1d, 0d), 24, 1.0d, 1.0d, 1.0d, 0.03d);
    }

    /** 0 档：爆发期间的华丽黑红环绕（每秒一次）。 */
    public static void burstAura(Player player) {
        Location base = player.getLocation().add(0d, 1d, 0d);
        World world = base.getWorld();
        for (int i = 0; i < 26; i++) {
            double angle = Math.toRadians(i * (360d / 26d));
            double r = 1.55d;
            Location at = base.clone().add(Math.cos(angle) * r, Math.sin(angle * 3d) * 0.55d, Math.sin(angle) * r);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 3 == 0 ? BLACK : RED);
        }
        world.spawnParticle(Particle.SOUL, base, 6, 0.7d, 0.9d, 0.7d, 0.01d);
        world.spawnParticle(Particle.END_ROD, base, 4, 0.5d, 0.7d, 0.5d, 0.01d);
    }

    /** 圆弧斩充能版收束：一圈金红光环 + 上升的末地烛。 */
    public static void chargedRing(Location center, double radius) {
        World world = center.getWorld();
        for (int i = 0; i < 48; i++) {
            double angle = Math.toRadians(i * 7.5d);
            Location at = center.clone().add(Math.cos(angle) * radius, 0.18d, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? GOLD : RED);
        }
        world.spawnParticle(Particle.END_ROD, center.clone().add(0d, 1d, 0d), 18,
                radius * 0.55d, 0.5d, radius * 0.55d, 0.02d);
    }

    /** 10 档：技能施加的缓慢环。 */
    public static void controlEcho(Location center) {        World world = center.getWorld();
        for (int i = 0; i < 24; i++) {
            double angle = Math.toRadians(i * 15d);
            Location at = center.clone().add(Math.cos(angle) * 3.2d, 0.1d, Math.sin(angle) * 3.2d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, RED_DARK);
        }
    }

    // ───────── 内部工具 ─────────

    private static void diamond(Player target, double height, double width, int phase,
                                Particle.DustOptions first, Particle.DustOptions second) {
        Location base = target.getLocation().add(0d, 1.0d, 0d);
        float yaw = target.getLocation().getYaw();
        double rad = Math.toRadians(yaw + 90d + phase * 14d);
        Vector right = new Vector(Math.cos(rad), 0d, Math.sin(rad));
        World world = base.getWorld();
        for (int i = 0; i < 12; i++) {
            double t = i / 12d * Math.PI * 2d;
            double x = Math.sin(t) * width;
            double y = Math.cos(t) * height;
            Location at = base.clone().add(right.clone().multiply(x)).add(0d, y, 0d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? first : second);
        }
    }

    private static void diamondAt(Location center, double height, double width, int phase,
                                  Particle.DustOptions first, Particle.DustOptions second) {
        World world = center.getWorld();
        double rad = Math.toRadians(phase * 9d);
        Vector axis = new Vector(Math.cos(rad), 0d, Math.sin(rad));
        for (int i = 0; i < 14; i++) {
            double t = i / 14d * Math.PI * 2d;
            double x = Math.sin(t) * width;
            double y = Math.cos(t) * height;
            Location at = center.clone().add(axis.clone().multiply(x)).add(0d, y, 0d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? first : second);
        }
    }

    /** 玩家背后 {@code distance} 格（水平）。 */
    private static Location behind(Player player, double distance) {
        Vector backward = player.getLocation().getDirection().setY(0d);
        if (backward.lengthSquared() < 1e-6) {
            backward = new Vector(0, 0, 1);
        }
        backward.normalize().multiply(-distance);
        return player.getLocation().clone().add(backward);
    }

    /** 绕过任意轴旋转向量（Rodrigues）。 */
    private static Vector rotate(Vector v, Vector axis, double angle) {
        Vector k = axis.clone().normalize();
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        Vector cross = k.clone().crossProduct(v);
        double dot = k.dot(v);
        return v.clone().multiply(cos).add(cross.multiply(sin)).add(k.clone().multiply(dot * (1d - cos)));
    }
}
