package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 「红」的粒子与音效库（纯静态，无状态）。
 *
 * <p>需求配色：**红白紫**为主，二技能带一点黑。所有需要 data 的粒子都把 data 放在末位实参
 * （工程闸门 {@code ParticleDataContractTest} 的判据）。
 */
public final class RedFx {

    public static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(0xE6, 0x12, 0x2B), 1.1f);
    public static final Particle.DustOptions DEEP_RED = new Particle.DustOptions(Color.fromRGB(0x8E, 0x0A, 0x1A), 1.2f);
    public static final Particle.DustOptions WHITE = new Particle.DustOptions(Color.fromRGB(0xF6, 0xF1, 0xF3), 1.0f);
    public static final Particle.DustOptions PURPLE = new Particle.DustOptions(Color.fromRGB(0x9B, 0x4D, 0xD8), 1.0f);
    public static final Particle.DustOptions BLACK = new Particle.DustOptions(Color.fromRGB(0x12, 0x0A, 0x12), 1.2f);

    private static final BlockData BLOOD_BLOCK = Material.RED_CONCRETE.createBlockData();

    private RedFx() {
    }

    // ───────── 普攻 / 穿刺 ─────────

    /**
     * 穿刺起手：先在面前画一个圆，四个四分之一处各一根短柱，圆中间再竖起主穿刺柱，
     * 柱上缠绕螺旋粒子（需求逐条）。
     */
    public static void pierceCast(Player player, double length, boolean grand) {
        World world = player.getWorld();
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();

        Location center = eye.clone().add(dir.clone().multiply(grand ? 1.9d : 1.45d));
        double radius = grand ? 1.15d : 0.8d;
        int points = grand ? 36 : 24;
        for (int i = 0; i < points; i++) {
            double a = Math.toRadians(i * (360d / points));
            Location at = center.clone().add(right.clone().multiply(Math.cos(a) * radius))
                    .add(up.clone().multiply(Math.sin(a) * radius));
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 3 == 0 ? WHITE : RED);
        }
        // 四个四分之一处：向外短柱
        for (int q = 0; q < 4; q++) {
            double a = Math.toRadians(q * 90d);
            Vector radial = right.clone().multiply(Math.cos(a)).add(up.clone().multiply(Math.sin(a)));
            for (int k = 1; k <= 3; k++) {
                Location at = center.clone().add(radial.clone().multiply(radius + k * 0.18d));
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, k % 2 == 0 ? PURPLE : RED);
            }
        }
        // 主穿刺柱 + 缠绕
        int steps = (int) Math.round(length / 0.25d);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location at = eye.clone().add(dir.clone().multiply(1.0d + t * length));
            world.spawnParticle(Particle.DUST, at, 1, 0.02d, 0.02d, 0.02d, 0d, i % 2 == 0 ? WHITE : RED);
            double angle = t * Math.PI * (grand ? 8d : 5d);
            Location spiral = at.clone().add(right.clone().multiply(Math.cos(angle) * 0.28d))
                    .add(up.clone().multiply(Math.sin(angle) * 0.28d));
            world.spawnParticle(Particle.DUST, spiral, 1, 0d, 0d, 0d, 0d, i % 3 == 0 ? PURPLE : DEEP_RED);
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, center, grand ? 3 : 1, 0.15d, 0.15d, 0.15d, 0d);
    }

    /** 命中：血迹喷溅（红方块 + 红尘）。 */
    public static void bloodSplat(Player victim, int amount) {
        if (victim == null || !victim.isOnline()) {
            return;
        }
        Location at = victim.getLocation().add(0d, 1.0d, 0d);
        victim.getWorld().spawnParticle(Particle.BLOCK, at, Math.max(6, amount), 0.28d, 0.35d, 0.28d, 0.12d, BLOOD_BLOCK);
        victim.getWorld().spawnParticle(Particle.DUST, at, 8, 0.25d, 0.3d, 0.25d, 0d, RED);
    }

    /** 流血每秒的滴落。 */
    public static void bleedTick(Player victim) {
        victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0d, 0.9d, 0d),
                10, 0.25d, 0.35d, 0.25d, 0.08d, BLOOD_BLOCK);
    }

    // ───────── 范围勾勒 ─────────

    /** 技能范围圈（{@code phase} 让边缘缓慢旋转）。 */
    public static void rangeRing(Location center, double radius, int phase, boolean dark) {
        World world = center.getWorld();
        int points = 80;
        for (int i = 0; i < points; i++) {
            double a = Math.toRadians(i * (360d / points) + phase * 3d);
            Location at = center.clone().add(Math.cos(a) * radius, 0.12d, Math.sin(a) * radius);
            Particle.DustOptions options = (i % 10 == 0) ? PURPLE : ((i % 2 == 0) ? RED : WHITE);
            if (dark && i % 7 == 0) {
                options = BLACK;
            }
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, options);
        }
    }

    /** 二技能：药水粒子场（范围内部缓慢翻涌）。 */
    public static void potionField(Location center, double radius, int phase) {
        World world = center.getWorld();
        for (int i = 0; i < 18; i++) {
            double a = Math.toRadians(i * 20d + phase * 11d);
            double r = radius * (0.25d + (i % 5) * 0.16d);
            Location at = center.clone().add(Math.cos(a) * r, 0.2d + (i % 4) * 0.35d, Math.sin(a) * r);
            world.spawnParticle(Particle.ENTITY_EFFECT, at, 1, 0.05d, 0.05d, 0.05d, 0d,
                    i % 3 == 0 ? Color.fromRGB(0x7A, 0x1F, 0x2E) : Color.fromRGB(0xE6, 0x12, 0x2B));
        }
        if (phase % 4 == 0) {
            world.spawnParticle(Particle.WITCH, center.clone().add(0d, 1.2d, 0d), 6,
                    radius * 0.5d, 0.4d, radius * 0.5d, 0d);
        }
    }

    /** 二技能收束：黑红向外爆开。 */
    public static void darkBurst(Location center, double radius) {
        World world = center.getWorld();
        for (int ring = 0; ring < 3; ring++) {
            double r = radius * (0.35d + ring * 0.3d);
            for (int i = 0; i < 40; i++) {
                double a = Math.toRadians(i * 9d + ring * 5d);
                Location at = center.clone().add(Math.cos(a) * r, 0.3d + ring * 0.4d, Math.sin(a) * r);
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                        i % 4 == 0 ? BLACK : (i % 2 == 0 ? RED : PURPLE));
            }
        }
        world.spawnParticle(Particle.WITCH, center.clone().add(0d, 1.2d, 0d), 40, 1.2d, 0.8d, 1.2d, 0.05d);
    }

    // ───────── 大招 ─────────

    /** 引导：四根红石柱从地面升起、旋转向上（{@code tick} 0..20）。 */
    public static void ultColumns(Location center, int tick) {
        World world = center.getWorld();
        double rise = Math.min(2.4d, tick * 0.14d);
        for (int q = 0; q < 4; q++) {
            double base = Math.toRadians(q * 90d + tick * 9d);
            double radius = 1.5d;
            Location baseLoc = center.clone().add(Math.cos(base) * radius, 0d, Math.sin(base) * radius);
            for (int y = 0; y < 6; y++) {
                Location at = baseLoc.clone().add(0d, rise + y * 0.35d, 0d);
                world.spawnParticle(Particle.BLOCK, at, 2, 0.12d, 0.12d, 0.12d, 0d, Material.REDSTONE_BLOCK.createBlockData());
                if (y % 2 == 0) {
                    world.spawnParticle(Particle.DUST, at, 1, 0.05d, 0.05d, 0.05d, 0d, RED);
                }
            }
        }
        if (tick % 2 == 0) {
            rangeRing(center, 12d, tick, false);
        }
    }

    /** 引导结束：四面红石柱汇入自身，再向外爆开。 */
    public static void ultConverge(Location center, double radius) {
        World world = center.getWorld();
        for (int q = 0; q < 4; q++) {
            double base = Math.toRadians(q * 90d + 40d);
            for (int i = 0; i < 14; i++) {
                double t = i / 14d;
                Location from = center.clone().add(Math.cos(base) * 1.5d, 2.4d, Math.sin(base) * 1.5d);
                org.bukkit.util.Vector start = from.toVector();
                org.bukkit.util.Vector end = center.clone().add(0d, 1d, 0d).toVector();
                Location at = start.clone().add(end.clone().subtract(start).multiply(t))
                        .toLocation(center.getWorld());
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? WHITE : RED);
            }
        }
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0d, 1d, 0d), 2, 0.6d, 0.4d, 0.6d, 0d);
        for (int i = 0; i < 60; i++) {
            double a = Math.toRadians(i * 6d);
            double r = radius * (0.3d + (i % 7) * 0.1d);
            Location at = center.clone().add(Math.cos(a) * r, 0.4d + (i % 5) * 0.5d, Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 5 == 0 ? PURPLE : RED);
        }
    }

    /** 大招期间：身周缓慢旋转的红白光环。 */
    public static void ultAura(Player player, int phase) {
        Location base = player.getLocation().add(0d, 1d, 0d);
        World world = base.getWorld();
        for (int i = 0; i < 16; i++) {
            double a = Math.toRadians(i * 22.5d + phase * 10d);
            Location at = base.clone().add(Math.cos(a) * 1.15d, Math.sin(a * 2d) * 0.35d, Math.sin(a) * 1.15d);
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, i % 4 == 0 ? WHITE : DEEP_RED);
        }
    }

    // ───────── 音效 ─────────

    public static void pierceCast(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.9f, 1.05f);
    }

    public static void pierceHit(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.8f, 0.9f);
        world.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.55f, 1.6f);
    }

    public static void skillThrust(Player player, double pitch) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, (float) pitch);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.8f);
    }

    public static void shockChannel(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 1.0f, 0.85f);
        world.playSound(player.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.8f, 0.9f);
    }

    public static void shockRelease(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_SPLASH_POTION_BREAK, 1.0f, 0.85f);
        world.playSound(player.getLocation(), Sound.ENTITY_PHANTOM_AMBIENT, 0.9f, 0.8f);
        world.playSound(player.getLocation(), Sound.ENTITY_WITCH_CELEBRATE, 0.8f, 1.0f);
    }

    public static void ultChannel(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.7f);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.5f, 1.2f);
    }

    public static void ultRelease(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
        world.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);
        world.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.6f, 0.8f);
    }

    public static void heal(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.3f);
    }
}
