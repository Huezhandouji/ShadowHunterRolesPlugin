package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.Random;

/**
 * 「特克」的**纯粒子工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求的观感形状算成坐标并 spawn 出去。所有几何都是**纯函数**
 * （给相位 / 半径 / 点数 ⇒ 一组坐标），组件只负责"什么时候画、画在哪"。
 *
 * <p><b>为什么独立成类</b>：工程约定"组件需要专属类支持时，专属类放到与组件同级的文件夹下"
 * （照 {@code custom/matina/MatinaRageVfx} 的既有形态）⇒ 粒子几何不进组件、更不进框架。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 * 主题色集中在下面几个 {@code public static final} 常量里，要调色只改那一处。
 */
public final class TekVfx {

    private TekVfx() {
    }

    // ───────── 主题色 ─────────

    /** 真理主色：金（{@code PRIMARY} 那一支，最贴近"真理/裁决"的观感）。 */
    public static final org.bukkit.Color TRUTH_GOLD = org.bukkit.Color.fromRGB(255, 214, 92);

    /** 真理副色：近白的淡金（叠在金粉上出层次）。 */
    public static final org.bukkit.Color TRUTH_PALE = org.bukkit.Color.fromRGB(255, 244, 205);

    /** 突进 / 刺击的青色（与金色区分，表示"穿刺"）。 */
    public static final org.bukkit.Color THRUST_CYAN = org.bukkit.Color.fromRGB(120, 240, 235);

    /** 「落岳」落地的凝灰岩灰（{@code TUFF} 的对应色）。 */
    public static final org.bukkit.Color TUFF_GRAY = org.bukkit.Color.fromRGB(150, 146, 135);

    /** 真理之刺的深红（贯穿 + 鸣响）。 */
    public static final org.bukkit.Color VERDICT_RED = org.bukkit.Color.fromRGB(220, 60, 70);

    /** 粒径：主粉尘。 */
    private static final float DUST_SIZE_MAIN = 1.1f;

    /** 粒径：副粉尘（更小，做层次）。 */
    private static final float DUST_SIZE_SUB = 0.85f;

    /** 仅供装饰性抖动的随机源（不影响任何判定）。 */
    private static final Random JITTER = new Random();

    // ───────── 通用：一个点上的单颗粒子 ─────────

    /** 在某一格放一颗指定颜色的**红石粉**（{@code DUST}）粒子。 */
    public static void dust(World world, Location at, org.bukkit.Color color, float size) {
        if (world == null || at == null || color == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, new Particle.DustOptions(color, size));
    }

    // ───────── 真理层数的环绕粒子 ─────────

    /**
     * **真理层数的环绕粒子**：脚底一圈，每层多一颗、逐个叠高。
     *
     * @param layers 要画的**粒子数**（&le; 0 ⇒ 不画）—— 由调用方把层数折算好（如 {@code truth / 2}），
     *               本方法只认"画几颗"
     * @param phase  当前相位（弧度；调用方每帧推进 ⇒ 整圈一起转）
     * @param radius 环绕半径（格）
     * @param stepY  每颗抬高多少格（"逐个叠高"；第 0 颗贴着 {@code center}）
     */
    public static void truthOrbit(World world, Location center, int layers, double phase,
                                  double radius, double stepY) {
        if (world == null || center == null || layers <= 0) {
            return;
        }
        for (int i = 0; i < layers; i++) {
            double angle = phase + i * (Math.PI * 2d / Math.max(1, layers));
            Location at = center.clone().add(
                    Math.cos(angle) * radius,
                    i * stepY,
                    Math.sin(angle) * radius);
            dust(world, at, TRUTH_GOLD, DUST_SIZE_MAIN);
        }
    }

    // ───────── 刺击（刺霄 / 真理之刺）─────────

    /**
     * **穿刺轨线**：从 {@code from} 到 {@code to} 的一条直线粒子（每 {@code step} 格一颗，
     * 沿线交替金 / 青两色，观感像一道刺出的枪芒）。
     *
     * @param step 相邻两颗粒子的间距（格，&le; 0 时按 0.35 处理）
     */
    public static void thrustLine(World world, Location from, Location to, double step) {
        if (world == null || from == null || to == null) {
            return;
        }
        double spacing = step > 0d ? step : 0.35d;
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-6d) {
            dust(world, from, TRUTH_GOLD, DUST_SIZE_MAIN);
            return;
        }
        Vector dir = delta.normalize();
        int points = (int) Math.floor(length / spacing) + 1;
        for (int i = 0; i < points; i++) {
            Location at = from.clone().add(dir.clone().multiply(i * spacing));
            dust(world, at, (i % 2 == 0) ? TRUTH_GOLD : THRUST_CYAN, DUST_SIZE_SUB);
        }
    }

    /** **突进残影**：在释放者身后拉一串青色尾迹（每帧调一次，{@code back} = 向后第几格）。 */
    public static void dashTrail(World world, Location at, double spread) {
        if (world == null || at == null) {
            return;
        }
        double s = spread > 0d ? spread : 0.35d;
        world.spawnParticle(Particle.DUST, at, 3, s, s * 0.5d, s, 0d,
                new Particle.DustOptions(THRUST_CYAN, DUST_SIZE_MAIN));
    }

    // ───────── 落岳（跃起 / 落地爆炸）─────────

    /** **跃起床**：向上的一柱凝灰岩灰 + 金粉（跃起 15 格时每帧画在脚下）。 */
    public static void leapColumn(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        dust(world, at, TUFF_GRAY, DUST_SIZE_MAIN);
        dust(world, at.clone().add(0d, 0.4d, 0d), TRUTH_GOLD, DUST_SIZE_SUB);
        world.spawnParticle(Particle.CLOUD, at, 4, 0.3d, 0.1d, 0.3d, 0.02d);
    }

    /**
     * **落地爆炸**：r = {@code radius} 的凝灰岩尘环 + 中心金柱 + 外圈真理金环。
     * 需求"落地位置产生爆炸粒子"。
     */
    public static void impactBurst(World world, Location center, double radius, double phase) {
        if (world == null || center == null) {
            return;
        }
        double r = radius > 0d ? radius : 5d;
        //① 中心金柱
        for (double y = 0d; y <= 1.6d; y += 0.25d) {
            dust(world, center.clone().add(0d, y, 0d), TRUTH_GOLD, DUST_SIZE_MAIN);
        }
        //② 凝灰岩尘环（内圈，密）
        int inner = Math.max(12, (int) (r * 8));
        for (int i = 0; i < inner; i++) {
            double angle = Math.PI * 2d * i / inner;
            Location at = center.clone().add(Math.cos(angle) * r * 0.55d, 0.15d, Math.sin(angle) * r * 0.55d);
            dust(world, at, TUFF_GRAY, DUST_SIZE_MAIN);
        }
        //③ 外圈金环（带相位，观感在转）
        int outer = Math.max(16, (int) (r * 7));
        for (int i = 0; i < outer; i++) {
            double angle = phase + Math.PI * 2d * i / outer;
            Location at = center.clone().add(Math.cos(angle) * r, 0.1d, Math.sin(angle) * r);
            dust(world, at, TRUTH_PALE, DUST_SIZE_SUB);
        }
        //④ 爆炸核心
        world.spawnParticle(Particle.EXPLOSION, center, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.CLOUD, center, 12, r * 0.35d, 0.2d, r * 0.35d, 0.04d);
    }

    // ───────── 真理之刺（裁决）─────────

    /** **裁决光环**：施法者脚下一圈深红 + 不断上升的金色小星（表示"全场的真理被收走"）。 */
    public static void verdictAura(World world, Location center, double radius, double phase, int points) {
        if (world == null || center == null || points <= 0) {
            return;
        }
        double r = radius > 0d ? radius : 1.6d;
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2d * i / points;
            Location at = center.clone().add(Math.cos(angle) * r, 0.08d, Math.sin(angle) * r);
            dust(world, at, VERDICT_RED, DUST_SIZE_MAIN);
        }
        world.spawnParticle(Particle.END_ROD, center.clone().add(0d, 0.6d, 0d), 3,
                r * 0.6d, 0.4d, r * 0.6d, 0.01d);
    }

    /** **真理被剥离**：在一名敌人身上炸出一团金色（层数被清空 / 被结算时的观感）。 */
    public static void truthStripped(World world, Location at, int layers) {
        if (world == null || at == null) {
            return;
        }
        int amount = Math.max(6, Math.min(48, layers * 4));
        world.spawnParticle(Particle.DUST, at, amount, 0.5d, 0.7d, 0.5d, 0.02d,
                new Particle.DustOptions(TRUTH_GOLD, DUST_SIZE_MAIN));
        world.spawnParticle(Particle.END_ROD, at, 8, 0.4d, 0.5d, 0.4d, 0.03d);
    }

    /** **主武器突进就绪**：在武器持有者手上打一缕金闪（每帧一次，节流后调用）。 */
    public static void dashReadySpark(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 2, 0.12d, 0.12d, 0.12d, 0d,
                new Particle.DustOptions(TRUTH_GOLD, DUST_SIZE_SUB));
        world.spawnParticle(Particle.CRIT, at, 1, 0.1d, 0.1d, 0.1d, 0d);
    }

    // ───────── 蓄力（按住右键的 0.8 秒）─────────

    /**
     * **蓄力中的收敛粒子**（需求：蓄力过程中会有粒子）。
     *
     * <p>观感 = 一圈**由外向内收缩**的金色能量 + 底部升起的火花 + 手上汇聚的亮点：
     * {@code progress} 从 0 → 1，环半径从 {@code outerRadius} 收到几乎贴手，
     * 越接近蓄满越密、越亮 ⇒ 一眼能看出"还差多少"。
     *
     * @param progress    蓄力进度（0~1；超界会被夹住）
     * @param phase       相位（调用方每刻推进，让环上下浮动）
     * @param outerRadius 起手时的环半径（格）
     */
    public static void chargeConverge(World world, Location center, double progress, double phase,
                                      double outerRadius) {
        if (world == null || center == null) {
            return;
        }
        double p = Math.max(0d, Math.min(1d, progress));
        double outer = outerRadius > 0d ? outerRadius : 1.6d;
        //环半径随进度收缩（外侧 → 贴身）
        double radius = outer * (1d - 0.75d * p);
        int points = 6 + (int) Math.round(10d * p);
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2d * i / points;
            double y = 0.15d + 0.9d * p + Math.sin(angle * 2d + phase) * 0.12d;
            Location at = center.clone().add(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            //用两色交替 + 越满越大颗，做出"充能"的层次
            Color color = (i % 2 == 0) ? TRUTH_GOLD : THRUST_CYAN;
            float size = DUST_SIZE_SUB + 0.45f * (float) p;
            dust(world, at, color, size);
        }
        //底部升起的小火花 + 手心汇聚的亮点
        world.spawnParticle(Particle.END_ROD, center.clone().add(0d, 0.35d, 0d),
                1 + (int) Math.round(2d * p), radius * 0.6d, 0.35d, radius * 0.6d, 0.012d);
        if (p >= 0.999d) {
            //蓄满：手心炸出一圈金环（与 chargeArmedSound 同步的那一下）
            world.spawnParticle(Particle.DUST, center.clone().add(0d, 1.0d, 0d), 12,
                    0.25d, 0.25d, 0.25d, 0.02d, new Particle.DustOptions(TRUTH_PALE, DUST_SIZE_MAIN));
        }
    }

    /** **闪避 / 瞬移残影**（瞬移到敌人身后时，在原位置留一团）。 */
    public static void blinkEcho(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 8, 0.3d, 0.5d, 0.3d, 0.01d);
        world.spawnParticle(Particle.DUST, at, 10, 0.35d, 0.6d, 0.35d, 0.02d,
                new Particle.DustOptions(THRUST_CYAN, DUST_SIZE_SUB));
    }

    /** 装饰性抖动：给某个位置加一点点随机偏移（只动装饰，不影响判定）。 */
    public static Location jitter(Location base, double amount) {
        if (base == null || amount <= 0d) {
            return base;
        }
        return base.clone().add(
                (JITTER.nextDouble() - 0.5d) * 2d * amount,
                (JITTER.nextDouble() - 0.5d) * 2d * amount,
                (JITTER.nextDouble() - 0.5d) * 2d * amount);
    }
}
