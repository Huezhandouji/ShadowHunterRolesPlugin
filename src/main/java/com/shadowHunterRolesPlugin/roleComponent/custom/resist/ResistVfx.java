package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * 「抗争」的粒子工具箱（纯静态、传 {@code null} 静默返回）。
 *
 * <p>主题：**白 + 灰为主、紫色为辅**（需求原话）。所有需要 data 的粒子都在末位传了 data
 * （{@code Particle.FLASH} 需要 {@link Color}，{@code DUST} 需要 {@link Particle.DustOptions}），
 * 由 {@code ParticleDataContractTest} 兜底。
 */
public final class ResistVfx {

    private ResistVfx() {
    }

    // ───────── 配色 ─────────

    /** 主白（亮白，带一点冷调）。 */
    public static final Particle.DustOptions WHITE =
            new Particle.DustOptions(Color.fromRGB(245, 245, 255), 1.15f);

    /** 次灰（冷灰）。 */
    public static final Particle.DustOptions GRAY =
            new Particle.DustOptions(Color.fromRGB(150, 152, 165), 1.0f);

    /** 点缀紫（抗争的"革命色"）。 */
    public static final Particle.DustOptions PURPLE =
            new Particle.DustOptions(Color.fromRGB(178, 102, 255), 1.05f);

    /** 深灰（阴影层）。 */
    public static final Particle.DustOptions DARK_GRAY =
            new Particle.DustOptions(Color.fromRGB(78, 78, 88), 1.0f);

    /** FLASH 的 data（白）。 */
    public static final Color FLASH_WHITE = Color.fromRGB(238, 238, 255);

    /** FLASH 的 data（紫）。 */
    public static final Color FLASH_PURPLE = Color.fromRGB(186, 120, 255);

    /**
     * **普攻命中粒子的生成高度**（相对脚底，2026-10-19 需求）。
     *
     * <p>原先是 +1.0（胸口/头部），近距离会糊住整个敌人上半身与准星；
     * 现在压到 {@value}（腰胯以下），并把 `EXPLOSION` 换成小一号的 `POOF`
     * （原版 `EXPLOSION` 尺寸固定、无缩放参数）⇒ 个数不变、观感更小、不挡视线。
     */
    public static final double IMPACT_HEIGHT = 0.55d;

    // ───────── 主武器剑气（3 种形态随机） ─────────

    /**
     * **斗争剑气**：从眼睛到命中点的一条"有重量"的剑气。
     *
     * @param variant 0 = 月牙横斩；1 = 爆裂直刺；2 = 螺旋钻击（需求：3 种随机）
     */
    public static void swordQi(World world, Location from, Location to, int variant) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 0.15d) {
            return;
        }
        Vector dir = delta.clone().normalize();
        Vector side = perpendicular(dir);
        int v = Math.floorMod(variant, 3);

        //三种剑气在"形状"上彻底分开：宽弧 / 细线 / 垂直砸落
        switch (v) {
            case 0 -> crescent(world, from, dir, side, length);
            case 1 -> lance(world, from, dir, side, length);
            default -> slamQi(world, to);
        }

        /*
         * ★ 2026-10-18 22:40 需求：路径上不要再密铺爆炸（"面前的爆炸太多了"）。
         *   现在只保留一次"刀锋抵达敌人"的表现：
         *     月牙 ⇒ 一道横斩扫过敌方；直刺 ⇒ 一颗爆炸粒子穿透敌方；裂地 ⇒ 由 slamQi 自带落点表现。
         */
        if (v == 0) {
            crescentSlashThrough(world, to, side);
        } else if (v == 1) {
            lancePierceThrough(world, to, dir);
        }

        //刀锋收尾：月牙=横向扫击，直刺=一点寒芒（砸落型已在 slamQi 里收尾）
        if (v == 0) {
            world.spawnParticle(Particle.SWEEP_ATTACK, to, 2, 0.25d, 0.15d, 0.25d, 0d);
        } else if (v == 1) {
            world.spawnParticle(Particle.CRIT, to, 8, 0.2d, 0.2d, 0.2d, 0.08d);
        }
    }

    /**
     * **月牙 · 横斩过敌**（需求）：在敌人身上沿**横向**铺一串单爆炸粒子 ——
     * 像一道横斩从敌人身上平着刮过去，两端微微后弯成弧。
     */
    private static void crescentSlashThrough(World world, Location to, Vector side) {
        Location body = to.clone().add(0d, IMPACT_HEIGHT, 0d);
        int points = 7;
        double span = 2.6d;
        for (int i = 0; i < points; i++) {
            double k = (i / (double) (points - 1)) - 0.5d;      //-0.5 → +0.5
            double bend = -Math.abs(k) * 0.5d;                   //两端略向后弯（弧）
            Location p = body.clone().add(side.clone().multiply(k * span));
            p.add(0d, bend, 0d);
            world.spawnParticle(Particle.POOF, p, 1, 0d, 0d, 0d, 0d);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, p, 2, 0.06d, 0.06d, 0.06d, 0d, WHITE);
            }
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, body, 3, 0.05d, 0.05d, 0.05d, 0d);
        world.spawnParticle(Particle.FLASH, body, 1, 0d, 0d, 0d, 0d, FLASH_WHITE);
    }

    /**
     * **直刺 · 穿透敌方**（需求）：一颗爆炸粒子从敌人**身前**穿到**身后**（前后各一个 + 中间光痕），
     * 看起来是"剑气捅穿了目标"，而不是在面前炸开。
     */
    private static void lancePierceThrough(World world, Location to, Vector dir) {
        Location body = to.clone().add(0d, IMPACT_HEIGHT, 0d);
        Location entry = body.clone().subtract(dir.clone().multiply(0.9d));
        Location exit = body.clone().add(dir.clone().multiply(0.9d));
        //零偏移的小爆烟 = 一颗实心粒子穿透，不做球形扩散
        world.spawnParticle(Particle.POOF, entry, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.POOF, exit, 1, 0d, 0d, 0d, 0d);
        for (int i = 0; i <= 6; i++) {
            double t = i / 6d;
            Location p = entry.clone().add(exit.toVector().subtract(entry.toVector()).multiply(t));
            world.spawnParticle(Particle.END_ROD, p, 1, 0d, 0d, 0d, 0.01d);
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, body, 8, 0.2d, 0.25d, 0.2d, 0.06d);
        world.spawnParticle(Particle.FLASH, body, 1, 0d, 0d, 0d, 0d, FLASH_WHITE);
    }

    /** 形态 0：**月牙横斩** —— 宽而厚的一片弧（白灰为主、紫色点缀）。 */
    private static void crescent(World world, Location from, Vector dir, Vector side, double length) {
        int steps = (int) Math.max(12d, length * 3.5d);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location base = from.clone().add(dir.clone().multiply(length * t));
            double arc = Math.sin(t * Math.PI) * 1.35d;
            for (int k = -2; k <= 2; k++) {
                Location p = base.clone().add(side.clone().multiply(arc + k * 0.22d));
                world.spawnParticle(Particle.DUST, p, 1, 0.03d, 0.05d, 0.03d, 0d,
                        k == 0 ? WHITE : GRAY);
            }
            if (i % 3 == 0) {
                world.spawnParticle(Particle.CRIT, base, 1, 0.08d, 0.08d, 0.08d, 0.02d);
            }
            if (i % 6 == 0) {
                world.spawnParticle(Particle.DUST, base, 1, 0.05d, 0.05d, 0.05d, 0d, PURPLE);
            }
        }
    }

    /** 形态 1：**爆裂直刺** —— 极窄、极亮的一条白线（与月牙形成“面 vs 线”的对比）。 */
    private static void lance(World world, Location from, Vector dir, Vector side, double length) {
        int steps = (int) Math.max(16d, length * 5d);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location p = from.clone().add(dir.clone().multiply(length * t));
            world.spawnParticle(Particle.DUST, p, 2, 0.02d, 0.02d, 0.02d, 0d, WHITE);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0.02d, 0.02d, 0.02d, 0.01d);
            }
        }
        Location tip = from.clone().add(dir.clone().multiply(length));
        for (int i = 0; i < 12; i++) {
            double a = i * (Math.PI * 2d / 12d);
            Vector out = side.clone().multiply(Math.cos(a)).add(new Vector(0d, Math.sin(a), 0d));
            world.spawnParticle(Particle.DUST, tip.clone().add(out.multiply(0.8d)), 1,
                    0.02d, 0.02d, 0.02d, 0d, i % 4 == 0 ? PURPLE : WHITE);
        }
    }

    /**
     * 形态 2：**裂地砸落** —— 从目标头顶 6.5 格垂直落下 + 地面白圈。
     * 只有这一种剑气会触发普攻的巨剑砸落动画（需求：砸落剑气才出巨剑）。
     */
    private static void slamQi(World world, Location to) {
        Location top = to.clone().add(0d, 6.5d, 0d);
        int steps = 18;
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            Location p = top.clone().add(0d, -6.5d * t, 0d);
            world.spawnParticle(Particle.DUST, p, 2, 0.08d, 0.05d, 0.08d, 0d,
                    i % 3 == 0 ? PURPLE : WHITE);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0.05d, 0.05d, 0.05d, 0.01d);
            }
        }
        ring(world, to.clone().add(0d, 0.1d, 0d), 1.6d, 22, WHITE);
        ring(world, to.clone().add(0d, 0.25d, 0d), 0.9d, 14, GRAY);
        world.spawnParticle(Particle.FLASH, to.clone().add(0d, 0.4d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_PURPLE);
        world.spawnParticle(Particle.LARGE_SMOKE, to, 8, 0.5d, 0.3d, 0.5d, 0.02d);
    }

    /**
     * **剑气命中敌人**：只在**敌人身上**打单个爆炸，不再向四周铺爆炸（2026-10-18 二次调整）。
     * 三种剑气各配不同的命中装饰（白环 / 电光 / 地面圈）。
     */
    public static void swordQiImpact(World world, Location at, int variant) {
        if (world == null || at == null) {
            return;
        }
        Location body = at.clone().add(0d, IMPACT_HEIGHT, 0d);
        int v = Math.floorMod(variant, 3);
        //★ 敌人身上只留**一颗**小爆烟（零偏移 ⇒ 不向四周扩散），高度已压到腰下
        world.spawnParticle(Particle.POOF, body, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.CRIT, body, 5, 0.12d, 0.12d, 0.12d, 0.04d);
        switch (v) {
            case 0 -> {
                world.spawnParticle(Particle.SWEEP_ATTACK, body, 2, 0.08d, 0.08d, 0.08d, 0d);
                ring(world, at.clone().add(0d, 0.12d, 0d), 1.2d, 16, WHITE);
            }
            case 1 -> {
                world.spawnParticle(Particle.ELECTRIC_SPARK, body, 8, 0.2d, 0.25d, 0.2d, 0.06d);
                world.spawnParticle(Particle.END_ROD, body, 3, 0.12d, 0.16d, 0.12d, 0.02d);
            }
            default -> {
                ring(world, at.clone().add(0d, 0.1d, 0d), 1.6d, 20, WHITE);
                world.spawnParticle(Particle.LARGE_SMOKE, at, 5, 0.28d, 0.18d, 0.28d, 0.02d);
            }
        }
    }

    /**
     * **r5 溅射的轻量命中**：只一颗单爆炸 + 两点火星。
     *
     * <p>★ 不给 FLASH / 不给地面环：周围一圈敌人被同时命中时，逐个打环 + 闪白会糊住整个屏幕
     * （2026-10-18 需求：「面前的爆炸粒子太多了」）。
     */
    public static void swordQiSplash(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location body = at.clone().add(0d, IMPACT_HEIGHT, 0d);
        world.spawnParticle(Particle.POOF, body, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.CRIT, body, 3, 0.1d, 0.1d, 0.1d, 0.03d);
    }

    /**
     * **普攻 r5 范围提示**（2026-10-18 二次调整）：只用一圈白色圆环 + 8 个白点，
     * 不再铺边缘爆炸、不再冒烟 —— 让"打到哪里"看得清，又不抢剑气本身的戏。
     */
    public static void aoeShockwave(World world, Location center, double radius) {
        if (world == null || center == null) {
            return;
        }
        ring(world, center.clone().add(0d, 0.08d, 0d), radius, 64, WHITE);
        ring(world, center.clone().add(0d, 0.16d, 0d), Math.max(0.5d, radius - 0.12d), 56, GRAY);
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4d);
            Location p = center.clone().add(Math.cos(a) * radius, 0.25d, Math.sin(a) * radius);
            world.spawnParticle(Particle.END_ROD, p, 1, 0d, 0d, 0d, 0d);
        }
    }

    // ───────── 一技能：谷底的怒吼 ─────────

    /** 怒吼开场：四道竖直菱形 + 紫白爆环 + 白闪。 */
    public static void roarEruption(World world, Location center) {
        if (world == null || center == null) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(45d + i * 90d);
            Vector dir = new Vector(Math.cos(a), 0d, Math.sin(a));
            verticalDiamond(world, center.clone().add(dir.clone().multiply(1.7d)), dir, 1.5d, 4.2d);
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center.clone().add(0d, 1d, 0d), 30,
                0.8d, 1.2d, 0.8d, 0.02d,
                new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(150, 90, 240), 1.2f));
        world.spawnParticle(Particle.LARGE_SMOKE, center, 16, 0.8d, 0.9d, 0.8d, 0.03d);
        world.spawnParticle(Particle.FLASH, center.clone().add(0d, 1d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_PURPLE);
        ring(world, center.clone().add(0d, 0.2d, 0d), 3.2d, 30, PURPLE);
    }

    /** 怒吼持续：绕身旋转/翻转的菱形几何（每 2 刻调一次）。 */
    public static void roarDiamondOrbit(World world, Location center, int phase) {
        if (world == null || center == null) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            double a = phase * 0.09d + i * (Math.PI / 2d);
            double r = 2.3d;
            Location p = center.clone().add(Math.cos(a) * r, 0.9d + 0.55d * Math.sin(phase * 0.12d + i), Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, p, 2, 0.05d, 0.05d, 0.05d, 0d, i % 2 == 0 ? WHITE : PURPLE);
            world.spawnParticle(Particle.ENCHANTED_HIT, p, 1, 0.02d, 0.02d, 0.02d, 0.01d);
        }
    }

    // ───────── 二技能：命运的征服 ─────────

    /** 引导：白灰粒子向中心收束 + 紫色外环。 */
    public static void conquestCharge(World world, Location center, int tick) {
        if (world == null || center == null) {
            return;
        }
        double r = 2.4d - (tick % 12) * 0.13d;
        for (int i = 0; i < 10; i++) {
            double a = tick * 0.28d + i * (Math.PI * 2d / 10d);
            Location p = center.clone().add(Math.cos(a) * r, 0.6d + 0.25d * Math.sin(tick * 0.2d + i), Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, i % 4 == 0 ? PURPLE : WHITE);
        }
        if (tick % 4 == 0) {
            ring(world, center.clone().add(0d, 0.12d, 0d), 1.6d, 14, GRAY);
        }
    }

    /**
     * **三刀斩击**。
     *
     * @param stage 1 = 右→左水平；2 = 左→右水平；3 = 上→下砸地（R9 的竖直弧 + 地面环）
     */
    public static void conquestSlash(World world, Location eye, Vector dir, int stage) {
        if (world == null || eye == null || dir == null) {
            return;
        }
        Vector flat = new Vector(dir.getX(), 0d, dir.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        flat.normalize();
        Vector side = new Vector(-flat.getZ(), 0d, flat.getX());
        double radius = stage >= 3 ? 9.0d : 8.0d;

        if (stage >= 3) {
            //竖直弧：从头顶劈到脚前
            int steps = 26;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double angle = Math.toRadians(80d - 160d * t);
                Vector offset = flat.clone().multiply(Math.cos(angle) * radius)
                        .add(new Vector(0d, Math.sin(angle) * radius, 0d));
                Location p = eye.clone().add(offset);
                world.spawnParticle(Particle.DUST, p, 2, 0.08d, 0.08d, 0.08d, 0d, i % 3 == 0 ? PURPLE : WHITE);
                world.spawnParticle(Particle.EXPLOSION, p, 1, 0.22d, 0.22d, 0.22d, 0d);
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.FIREWORK, p, 1, 0.2d, 0.2d, 0.2d, 0.03d);
                }
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.LARGE_SMOKE, p, 1, 0.12d, 0.12d, 0.12d, 0.01d);
                }
            }
            ring(world, eye.clone().add(flat.clone().multiply(3.2d)).setDirection(flat), 3.4d, 26, WHITE);
            world.spawnParticle(Particle.FLASH, eye.clone().add(flat.clone().multiply(2.4d)).add(0d, 0.4d, 0d),
                    1, 0d, 0d, 0d, 0d, FLASH_WHITE);
        } else {
            //水平弧：半圆扫击
            int steps = 30;
            double sign = stage == 1 ? 1d : -1d;
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double angle = Math.toRadians(-90d + 180d * t) * sign;
                Vector offset = flat.clone().multiply(Math.cos(angle) * radius)
                        .add(side.clone().multiply(Math.sin(angle) * radius));
                Location p = eye.clone().add(offset).add(0d, -0.35d, 0d);
                world.spawnParticle(Particle.DUST, p, 2, 0.06d, 0.06d, 0.06d, 0d, i % 4 == 0 ? GRAY : WHITE);
                //大量单个爆炸粒子模拟剑气（需求 2）
                world.spawnParticle(Particle.EXPLOSION, p, 1, 0.2d, 0.2d, 0.2d, 0d);
                if (i % 3 == 0) {
                    world.spawnParticle(Particle.FIREWORK, p, 1, 0.25d, 0.25d, 0.25d, 0.03d);
                }
                if (i % 5 == 0) {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, p, 2, 0.2d, 0.2d, 0.2d, 0.05d);
                }
                if (i % 6 == 0) {
                    world.spawnParticle(Particle.SWEEP_ATTACK, p, 1, 0d, 0d, 0d, 0d);
                }
            }
            //刀身中线：一道更亮的紫白
            for (int i = 0; i <= 18; i++) {
                double t = i / 18d;
                Location p = eye.clone().add(flat.clone().multiply(radius * t)).add(0d, 0.2d, 0d);
                world.spawnParticle(Particle.DUST, p, 1, 0.02d, 0.02d, 0.02d, 0d, i % 5 == 0 ? PURPLE : WHITE);
            }
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, eye.clone().add(flat.clone().multiply(radius * 0.5d)),
                18, 0.6d, 0.6d, 0.6d, 0.02d,
                new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(120, 120, 135), 1.1f));
    }

    /** 命中：白闪 + 紫色火星 + 灰黑烟 + 小环。 */
    public static void conquestHit(World world, Location at, int stage) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1d, 0d);
        world.spawnParticle(Particle.FLASH, center, 1, 0d, 0d, 0d, 0d, stage >= 3 ? FLASH_PURPLE : FLASH_WHITE);
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 12 + stage * 4, 0.55d, 0.6d, 0.55d, 0.12d);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 8, 0.5d, 0.55d, 0.5d, 0.08d);
        world.spawnParticle(Particle.SQUID_INK, at, 6, 0.4d, 0.35d, 0.4d, 0.03d);
        world.spawnParticle(Particle.EXPLOSION, center, 8, 0.9d, 0.9d, 0.9d, 0d);
        world.spawnParticle(Particle.FIREWORK, center, 6, 0.8d, 0.8d, 0.8d, 0.03d);
        ring(world, at.clone().add(0d, 0.1d, 0d), 1.8d, 20, WHITE);
        ring(world, at.clone().add(0d, 0.5d, 0d), 1.1d, 14, PURPLE);
    }

    // ───────── 三技能：被欺压者的咆哮 ─────────

    /**
     * **大招引导期间的竖向范围圈**（2026-10-18 需求 8）：以突进方向为轴，
     * 画自身（路径半径 5）、前方 15 格（路径半径 5）、前方 30 格（落点半径 10）三道竖圈。
     */
    public static void rampageRangeHint(World world, Location center, Vector dir, int phase) {
        if (world == null || center == null || dir == null) {
            return;
        }
        Vector flat = new Vector(dir.getX(), 0d, dir.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        flat.normalize();
        Vector side = new Vector(-flat.getZ(), 0d, flat.getX());
        Vector up = new Vector(0d, 1d, 0d);
        drawRing(world, center.clone().add(0d, 1.0d, 0d), side, up, 5.0d, 30, WHITE);

        double[] distances = {15d, 30d};
        double[] radii = {5d, 10d};
        for (int i = 0; i < distances.length; i++) {
            Location gate = center.clone().add(flat.clone().multiply(distances[i])).add(0d, 1.2d, 0d);
            drawRing(world, gate, side, up, radii[i], i == 1 ? 40 : 28, i == 1 ? WHITE : GRAY);
            if (phase % 2 == 0) {
                world.spawnParticle(Particle.END_ROD, gate, 3, 0.4d, 1.6d, 0.4d, 0.01d);
            }
        }
        if (phase % 4 == 0) {
            world.spawnParticle(Particle.FIREWORK, center.clone().add(0d, 1d, 0d), 2, 0.5d, 0.5d, 0.5d, 0.02d);
        }
    }

    /** 引导：脚下地面裂开般的白灰收束 + 偶发紫。 */
    public static void rampageCharge(World world, Location center, int tick) {
        if (world == null || center == null) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI * 2d / 8d) + tick * 0.18d;
            double r = 2.0d - (tick % 16) * 0.08d;
            Location p = center.clone().add(Math.cos(a) * r, 0.15d, Math.sin(a) * r);
            world.spawnParticle(Particle.DUST, p, 2, 0.05d, 0.05d, 0.05d, 0d, i % 3 == 0 ? GRAY : WHITE);
        }
        if (tick % 4 == 0) {
            world.spawnParticle(Particle.LARGE_SMOKE, center, 3, 0.9d, 0.1d, 0.9d, 0.01d);
        }
    }

    /** 突进尾迹：白灰拖尾 + 紫色电光。 */
    public static void rampageTrail(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 1d, 0d), 4, 0.3d, 0.5d, 0.3d, 0.02d, WHITE);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 3, 0.35d, 0.3d, 0.35d, 0.01d);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0d, 1d, 0d), 2, 0.3d, 0.4d, 0.3d, 0.05d);
        if (at.getWorld().getTime() % 2 == 0) {
            world.spawnParticle(Particle.DUST, at.clone().add(0d, 0.4d, 0d), 1, 0.2d, 0.1d, 0.2d, 0d, PURPLE);
        }
    }

    /** 落点：多层冲击环 + 白闪 + 灰黑烟柱 + 竖直菱形（保留旧签名）。 */
    public static void rampageLanding(World world, Location center, double radius) {
        rampageLanding(world, center, radius, new Vector(0d, 0d, 1d));
    }

    /**
     * 落点（带突进方向）：在旧版基础上追加**大量单爆炸粒子**与**沿方向的竖向圈**（需求 8）。
     */
    public static void rampageLanding(World world, Location center, double radius, Vector dir) {
        if (world == null || center == null) {
            return;
        }
        world.spawnParticle(Particle.FLASH, center.clone().add(0d, 0.8d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_WHITE);
        world.spawnParticle(Particle.SONIC_BOOM, center.clone().add(0d, 0.6d, 0d), 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 26, 1.4d, 0.8d, 1.4d, 0.04d);
        world.spawnParticle(Particle.SQUID_INK, center, 14, 1.2d, 0.6d, 1.2d, 0.04d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center.clone().add(0d, 0.8d, 0d), 40,
                1.2d, 0.9d, 1.2d, 0.03d,
                new Particle.DustTransition(Color.fromRGB(255, 255, 255), Color.fromRGB(120, 120, 135), 1.1f));
        for (int i = 0; i < 4; i++) {
            ring(world, center.clone().add(0d, 0.1d + i * 0.25d, 0d), Math.min(radius, 2.0d + i * 2.2d), 30, i % 2 == 0 ? WHITE : GRAY);
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(i * 60d);
            Vector spoke = new Vector(Math.cos(a), 0d, Math.sin(a));
            verticalDiamond(world, center.clone().add(spoke.clone().multiply(2.2d)), spoke, 1.2d, 3.2d);
        }

        //需求 8：大量单个白色爆炸粒子
        world.spawnParticle(Particle.EXPLOSION, center, 60, radius * 0.75d, 1.2d, radius * 0.75d, 0d);
        world.spawnParticle(Particle.FIREWORK, center, 24, radius * 0.7d, 1.0d, radius * 0.7d, 0.03d);

        //需求 8：沿突进方向的竖向圈（0/3/6/9 格，半径 3/5/7/9）
        Vector flat = dir == null ? new Vector(0d, 0d, 1d)
                : new Vector(dir.getX(), 0d, dir.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        flat.normalize();
        Vector side = new Vector(-flat.getZ(), 0d, flat.getX());
        Vector up = new Vector(0d, 1d, 0d);
        for (int i = 0; i < 4; i++) {
            Location gate = center.clone().add(flat.clone().multiply(i * 3d)).add(0d, 1.0d, 0d);
            drawRing(world, gate, side, up, 3d + i * 2d, 30, i % 2 == 0 ? WHITE : GRAY);
        }
    }

    // ───────── 被动：斗争层数 ─────────

    /**
     * 绕身缓慢旋绕的暴击粒子（每 5 层一枚，最多 8 枚；刻意压低高度、拉大半径以免遮挡视线）。
     */
    public static void struggleOrbit(World world, Location center, int count, int phase) {
        if (world == null || center == null) {
            return;
        }
        int n = Math.max(0, Math.min(count, 8));
        for (int i = 0; i < n; i++) {
            double a = phase * 0.045d + i * (Math.PI * 2d / Math.max(1, n));
            double r = 0.95d + 0.05d * i;          //逐个向外一点，避免叠在一起
            double y = 0.70d + 0.11d * i;          //逐个叠高，但最高 1.47 格（低于眼睛，不挡视线）
            Location p = center.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
            world.spawnParticle(Particle.CRIT, p, 1, 0d, 0d, 0d, 0d);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, PURPLE);
            }
        }
    }

    /**
     * 40 层的**身后双层圆环**：白外圈 + 灰内圈，垂直立于背后（不挡正前方视线）。
     *
     * @param flash 每 2 秒的扩张闪动
     */
    public static void struggleRing(World world, Location center, float yaw, int phase, boolean flash) {
        if (world == null || center == null) {
            return;
        }
        double rad = Math.toRadians(yaw);
        Vector forward = new Vector(-Math.sin(rad), 0d, Math.cos(rad));
        Vector right = new Vector(Math.cos(rad), 0d, Math.sin(rad));
        Location ringCenter = center.clone().add(forward.clone().multiply(-1.15d)).add(0d, 1.15d, 0d);
        double expand = flash ? 0.35d : 0d;
        drawRing(world, ringCenter, right, new Vector(0d, 1d, 0d), 1.55d + expand, 26, WHITE);
        drawRing(world, ringCenter, right, new Vector(0d, 1d, 0d), 1.15d + expand * 0.6d, 20, GRAY);
        if (flash) {
            world.spawnParticle(Particle.END_ROD, ringCenter, 6, 1.2d, 1.2d, 0.1d, 0.01d);
        }
        if (phase % 6 == 0) {
            world.spawnParticle(Particle.DUST, ringCenter, 1, 0.1d, 0.1d, 0.1d, 0d, PURPLE);
        }
    }

    /** 普攻「裂地」巨剑落地：小地面白圈 + 白闪 + 单爆炸 + 烟（不做大范围边缘爆炸）。 */
    public static void slamImpact(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at.clone().add(0d, 0.08d, 0d), 1.9d, 24, WHITE);
        ring(world, at.clone().add(0d, 0.18d, 0d), 1.1d, 16, GRAY);
        world.spawnParticle(Particle.FLASH, at.clone().add(0d, 0.5d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_WHITE);
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0d, 0.6d, 0d), 3, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 8, 0.5d, 0.3d, 0.5d, 0.02d);
    }

    // ───────── 几何工具 ─────────

    /** 水平朝上的圆环。 */
    private static void ring(World world, Location center, double radius, int points, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2d / points);
            Location p = center.clone().add(Math.cos(a) * radius, 0d, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, dust);
        }
    }

    /** 任意平面圆环（basisA / basisB 为平面内两正交单位向量）。 */
    private static void drawRing(World world, Location center, Vector basisA, Vector basisB,
                                 double radius, int points, Particle.DustOptions dust) {
        for (int i = 0; i < points; i++) {
            double a = i * (Math.PI * 2d / points);
            Vector offset = basisA.clone().multiply(Math.cos(a) * radius)
                    .add(basisB.clone().multiply(Math.sin(a) * radius));
            world.spawnParticle(Particle.DUST, center.clone().add(offset), 1, 0d, 0d, 0d, 0d, dust);
        }
    }

    /** 竖直菱形（"巨剑/压迫感"的几何形）。 */
    private static void verticalDiamond(World world, Location base, Vector dir, double halfWidth, double height) {
        Vector right = new Vector(-dir.getZ(), 0d, dir.getX()).normalize();
        int points = 3;
        // 上尖 → 右 → 下尖 → 左 → 回上
        Vector[] corners = new Vector[]{
                new Vector(0d, height, 0d),
                right.clone().multiply(halfWidth).add(new Vector(0d, height * 0.5d, 0d)),
                new Vector(0d, 0d, 0d),
                right.clone().multiply(-halfWidth).add(new Vector(0d, height * 0.5d, 0d)),
                new Vector(0d, height, 0d)
        };
        for (int i = 0; i < corners.length - 1; i++) {
            Vector a = corners[i];
            Vector b = corners[i + 1];
            for (int k = 0; k <= points; k++) {
                double t = k / (double) points;
                Location p = base.clone().add(a.clone().multiply(1d - t)).add(b.clone().multiply(t));
                world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, (i + k) % 3 == 0 ? PURPLE : WHITE);
            }
        }
    }

    /** 与方向正交的水平侧向量。 */
    private static Vector perpendicular(Vector dir) {
        Vector flat = new Vector(dir.getX(), 0d, dir.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            return new Vector(1d, 0d, 0d);
        }
        flat.normalize();
        return new Vector(-flat.getZ(), 0d, flat.getX());
    }
}
