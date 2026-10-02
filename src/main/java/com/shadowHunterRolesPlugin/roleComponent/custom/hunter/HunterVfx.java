package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Random;

/**
 * 「猎手」的**纯粒子工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求里描述的观感形状算成坐标并 spawn 出去。所有几何都是**纯函数**
 * （给中心 / 半径 ⇒ 一组坐标），组件只负责"什么时候画、画在哪"。
 *
 * <p><b>为什么独立成类</b>：工程约定"组件需要专属类支持时，专属类放到与组件同级的文件夹下"
 * （照 {@code custom/matina/MatinaRageVfx}、{@code custom/tek/TekVfx} 的既有形态）
 * ⇒ 粒子几何不进组件、更不进框架。
 *
 * <p><b>命名规范</b>（工程口径 {@code <主要形状>+XX角色+XX技能+XX阶段}）：
 * 本类的公开方法名一律带「Hunter + 技能 + 阶段」三段，便于判断能否复用。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 * 主题色集中在下面的 {@code public static final} 常量里，要调色只改那一处。
 *
 * <h2>需求对应</h2>
 * <ul>
 *   <li>猎杀（标记）：被标记敌人身上一簇<b>灵魂沙粒子</b>
 *       ⇒ {@link #soulClusterHunterPreyMark(World, Location)}；</li>
 *   <li>猎杀（标记）：其坐标高度 {@value #DIAMOND_HEIGHT} 格处一个<b>紫色菱形边框</b>
 *       ⇒ {@link #purpleDiamondHunterPreyMark(World, Location)}；</li>
 *   <li>拉回（施放）：用<b>类似紫色的粒子</b>模拟穿刺攻击
 *       ⇒ {@link #purpleThrustHunterPullCast(World, Location, Location)}；</li>
 *   <li>拉回（命中）：被命中敌人身上出现<b>暴击粒子、单个爆炸粒子、紫色菱形边框</b>
 *       ⇒ {@link #critBurstHunterPullHit(World, Location)} +
 *       {@link #purpleDiamondHunterPullHit(World, Location)}。</li>
 * </ul>
 */
public final class HunterVfx {

    private HunterVfx() {
    }

    // ───────── 主题色 ─────────

    /** 标记 / 拉回的紫色（需求两处都点明"紫色"）。 */
    public static final Color MARK_PURPLE = Color.fromRGB(168, 64, 232);

    /** 穿刺轨迹的淡紫（与主紫拉开层次，像一道拉长的光）。 */
    public static final Color THRUST_VIOLET = Color.fromRGB(206, 152, 255);

    /** 灵魂青（灵魂沙 / 灵魂火的蓝白）。 */
    public static final Color SOUL_CYAN = Color.fromRGB(92, 220, 236);

    // ───────── 几何常量（需求写死的两个数）─────────

    /** 紫色菱形边框的半径（格）：菱形四个顶点到中心的距离。 */
    public static final double DIAMOND_RADIUS = 0.9d;

    /** 紫色菱形边框所在的高度（需求原话："在其坐标高度 0.8 格"）—— 相对目标脚底。 */
    public static final double DIAMOND_HEIGHT = 0.8d;

    /** 菱形每条边铺几颗粒子（4 条边 ⇒ 共 4×该值 颗）。 */
    private static final int DIAMOND_POINTS_PER_EDGE = 6;

    /** 穿刺轨迹的相邻粒子间距（格）。 */
    private static final double THRUST_STEP = 0.35d;

    /** 粒径：主粉尘。 */
    private static final float DUST_SIZE_MAIN = 1.1f;

    /** 粒径：副粉尘（更小，做层次）。 */
    private static final float DUST_SIZE_SUB = 0.8f;

    /** 仅供装饰性抖动的随机源（不影响任何判定）。 */
    private static final Random JITTER = new Random();

    // ───────── 通用：一个点上的单颗粒子 ─────────

    /** 在某一格放一颗指定颜色的**红石粉**（{@code DUST}）粒子；任一入参为 {@code null} 时静默返回。 */
    public static void dust(World world, Location at, Color color, float size) {
        if (world == null || at == null || color == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, new Particle.DustOptions(color, size));
    }

    // ───────── 猎杀（被动 · 标记）─────────

    /**
     * **一簇灵魂沙粒子**（猎杀 · 标记）。
     *
     * <p>主粒子 = {@code Particle.SOUL}（灵魂沙 / 灵魂火冒出的那种蓝白魂火），
     * 外面再叠一小撮 {@code Particle.SOUL_FIRE_FLAME} 做体积感。
     *
     * @param center 标记目标身上的锚点（调用方给"脚底 + 1 格"附近，粒子自带散布）
     */
    public static void soulClusterHunterPreyMark(World world, Location center) {
        if (world == null || center == null) {
            return;
        }
        world.spawnParticle(Particle.SOUL, center, 7, 0.35d, 0.45d, 0.35d, 0.01d);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, center, 4, 0.25d, 0.35d, 0.25d, 0.005d);
    }

    /**
     * **紫色菱形边框**（猎杀 · 标记）：在目标脚底上方 {@value #DIAMOND_HEIGHT} 格处画一个水平菱形轮廓。
     *
     * @param feet 目标**脚底**坐标（本方法自己加高度）
     */
    public static void purpleDiamondHunterPreyMark(World world, Location feet) {
        diamondHunterMark(world, feet, DIAMOND_RADIUS, MARK_PURPLE, DUST_SIZE_MAIN);
    }

    // ───────── 拉回（技能）─────────

    /**
     * **紫色菱形边框**（拉回 · 命中）：与标记那个同形，半径略大一圈以便与被标记者区分。
     *
     * @param feet 被命中敌人**脚底**坐标
     */
    public static void purpleDiamondHunterPullHit(World world, Location feet) {
        diamondHunterMark(world, feet, DIAMOND_RADIUS * 1.2d, MARK_PURPLE, DUST_SIZE_MAIN);
    }

    /**
     * **暴击爆点**（拉回 · 命中）：{@code CRIT} 暴击粒子 + **单个**爆炸粒子（需求点名的两样）。
     *
     * @param at 被命中敌人身上（调用方给胸口高度）
     */
    public static void critBurstHunterPullHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.CRIT, at, 14, 0.3d, 0.4d, 0.3d, 0.12d);
        // "单个爆炸粒子" ⇒ count 恰为 1、无散布、无速度
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0d, 0d, 0d, 0d);
    }

    /**
     * **紫色穿刺轨迹**（拉回 · 施放）：从 {@code from} 到 {@code to} 铺一条紫 / 淡紫交替的直线粒子，
     * 用"一道拉长的紫芒"模拟穿刺攻击。
     *
     * <p>间距固定 {@value #THRUST_STEP} 格（与距离无关 ⇒ 近距离不会挤成一坨）。
     * 两端过近时退化为在终点放一颗粒子（不除零）。
     */
    public static void purpleThrustHunterPullCast(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        double distance = from.distance(to);
        if (distance <= 0.001d) {
            dust(world, to, THRUST_VIOLET, DUST_SIZE_MAIN);
            return;
        }
        int steps = Math.max(2, (int) Math.ceil(distance / THRUST_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location at = from.clone().add(
                    (to.getX() - from.getX()) * t,
                    (to.getY() - from.getY()) * t,
                    (to.getZ() - from.getZ()) * t);
            dust(world, at, (i % 2 == 0) ? MARK_PURPLE : THRUST_VIOLET, DUST_SIZE_SUB);
        }
    }

    /**
     * **牵引光带**（拉回 · 持续）：在"被拽的目标"与"视角前锚点"之间放 3 颗粒子，
     * 让"正被拉过去"这件事在观感上有来源。
     *
     * @param target 目标当前位置
     * @param anchor 锚点（角色视角前方）
     */
    public static void pullTetherHunterPullDrag(World world, Location target, Location anchor) {
        if (world == null || target == null || anchor == null) {
            return;
        }
        for (int i = 1; i <= 3; i++) {
            double t = i / 4d;
            Location at = target.clone().add(
                    (anchor.getX() - target.getX()) * t,
                    (anchor.getY() - target.getY()) * t + 0.6d,
                    (anchor.getZ() - target.getZ()) * t);
            dust(world, at, THRUST_VIOLET, DUST_SIZE_SUB);
        }
    }

    // ───────── 遗愤（主武器）─────────

    /**
     * **普攻命中**（常规）：目标胸口一小簇灵魂青。
     *
     * <p>与"打中被标记者"的爆点区分开 —— 常规命中只是"打到了"的轻量反馈。
     */
    public static void hitSoulHunterGrudgeAttack(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.SOUL, at, 3, 0.2d, 0.25d, 0.2d, 0.005d);
        dust(world, at, SOUL_CYAN, DUST_SIZE_SUB);
    }

    /**
     * **被标记的敌人挨普攻 ⇒ 更多粒子**（需求原话：「并且此次普通攻击会产生更多粒子」）。
     *
     * <p>相对 {@link #hitSoulHunterGrudgeAttack} 的"常规三颗"：
     * 灵魂粒子 ×{@value #MARK_CONSUME_SOUL_COUNT}、青紫双色粉尘、暴击粒子，
     * 外加一圈紫色菱形边框 —— 让"标记被这一击吃掉了"在画面上有一个明确的事件点。
     */
    public static void consumeBurstHunterGrudgeMarkedHit(World world, Location at, Location feet) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.SOUL, at, MARK_CONSUME_SOUL_COUNT, 0.45d, 0.55d, 0.45d, 0.02d);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 10, 0.35d, 0.45d, 0.35d, 0.01d);
        world.spawnParticle(Particle.CRIT, at, 18, 0.4d, 0.5d, 0.4d, 0.14d);
        for (int i = 0; i < 10; i++) {
            dust(world, at.clone().add(
                    (JITTER.nextDouble() - 0.5d) * 1.1d,
                    (JITTER.nextDouble() - 0.5d) * 1.1d,
                    (JITTER.nextDouble() - 0.5d) * 1.1d), (i % 2 == 0) ? MARK_PURPLE : SOUL_CYAN, DUST_SIZE_MAIN);
        }
        if (feet != null) {
            purpleDiamondHunterPullHit(world, feet);
        }
    }

    /** 打中被标记者时那一簇灵魂粒子的**颗数**（"更多粒子"的量化口径，便于一处调参）。 */
    private static final int MARK_CONSUME_SOUL_COUNT = 22;

    // ───────── 扑击（技能一）─────────

    /**
     * **突进破空**（需求原话：「1技能给一个突进时破空音效和粒子」）。
     *
     * <p>沿"从 {@code from} 到 {@code to}"拉一条**淡青色的空气划痕**，并在 {@code at} 处补一圈外扩气浪 ——
     * 语汇取自工程既有的"刺击轨线"（一道被拉长的光），颜色换成灵魂青以与拉回的紫区分。
     *
     * @param at   当前扑击位置（每刻调用 ⇒ 连成一条尾迹）
     * @param from 起笔点（一般给上一刻的位置）
     * @param to   收笔点（一般给这一刻的位置）
     */
    public static void dashTrailHunterPounceDash(World world, Location at, Location from, Location to) {
        if (world == null || at == null) {
            return;
        }
        if (from != null && to != null) {
            double distance = from.distance(to);
            int steps = Math.max(1, (int) Math.ceil(distance / THRUST_STEP));
            for (int i = 0; i <= steps; i++) {
                double t = (double) i / steps;
                dust(world, from.clone().add(
                        (to.getX() - from.getX()) * t,
                        (to.getY() - from.getY()) * t,
                        (to.getZ() - from.getZ()) * t), SOUL_CYAN, DUST_SIZE_SUB);
            }
        }
        //外扩气浪：短命白云，表示"撞开空气"
        world.spawnParticle(Particle.CLOUD, at, 5, 0.25d, 0.2d, 0.25d, 0.03d);
    }

    // ───────── 遁形（技能三 / 大招）─────────

    /**
     * **大招过程中的紫色粒子随机释放**（需求原话：「大招过程中的紫色粒子的随机释放」）。
     *
     * <p>"随机释放" = 位置随机（球壳内均匀取点）、颗数也在一个小区间内浮动 ——
     * 观感是"紫雾在周身不规则地浮出"，而不是一圈规整的环。
     *
     * @param count 本次最多放几颗（&le; 0 ⇒ 不画）
     */
    public static void randomPurpleHunterStealth(World world, Location center, int count, double radius) {
        if (world == null || center == null || count <= 0 || radius <= 0d) {
            return;
        }
        for (int i = 0; i < count; i++) {
            double theta = JITTER.nextDouble() * Math.PI * 2d;
            double phi = Math.acos(2d * JITTER.nextDouble() - 1d);
            double r = radius * Math.cbrt(JITTER.nextDouble());
            Location at = center.clone().add(
                    r * Math.sin(phi) * Math.cos(theta),
                    radius * 0.6d + r * Math.cos(phi) * 0.8d,
                    r * Math.sin(phi) * Math.sin(theta));
            dust(world, at, MARK_PURPLE, DUST_SIZE_SUB);
        }
    }

    /**
     * **大招过程中的药水粒子随机释放**（需求原话：「还有药水粒子随机释放」）。
     *
     * <p>用原版**药水漩涡**粒子（{@code ENTITY_EFFECT}，自带颜色数据），
     * 颜色在青→紫之间随机取 ⇒ 与紫雾混在一起像"身上在冒药水"。
     */
    public static void potionSpiralHunterStealth(World world, Location center, int count, double radius) {
        if (world == null || center == null || count <= 0 || radius <= 0d) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Location at = center.clone().add(
                    (JITTER.nextDouble() - 0.5d) * radius * 2d,
                    JITTER.nextDouble() * radius * 1.6d,
                    (JITTER.nextDouble() - 0.5d) * radius * 2d);
            world.spawnParticle(Particle.ENTITY_EFFECT, at, 1, 0d, 0d, 0d, 0d,
                    JITTER.nextBoolean() ? MARK_PURPLE : SOUL_CYAN);
        }
    }

    // ───────── 内部几何：水平菱形边框 ─────────

    /**
     * 画一个水平面上的**菱形轮廓**：四个顶点 {(+r,0), (0,+r), (−r,0), (0,−r)} 连成 4 条边，
     * 每条边等距铺 {@value #DIAMOND_POINTS_PER_EDGE} 颗粒子。
     *
     * <p>纯几何：不读任何状态；入参为 {@code null} 或半径 ≤ 0 时静默返回。
     */
    private static void diamondHunterMark(World world, Location feet, double radius, Color color, float size) {
        if (world == null || feet == null || color == null || radius <= 0d) {
            return;
        }
        double centerY = feet.getY() + DIAMOND_HEIGHT;
        double[][] vertices = {
                { radius, 0d },
                { 0d, radius },
                { -radius, 0d },
                { 0d, -radius }
        };
        for (int edge = 0; edge < vertices.length; edge++) {
            double[] from = vertices[edge];
            double[] to = vertices[(edge + 1) % vertices.length];
            for (int i = 0; i < DIAMOND_POINTS_PER_EDGE; i++) {
                double t = (double) i / DIAMOND_POINTS_PER_EDGE;
                Location at = new Location(world,
                        feet.getX() + from[0] + (to[0] - from[0]) * t,
                        centerY,
                        feet.getZ() + from[1] + (to[1] - from[1]) * t);
                dust(world, at, color, size);
            }
        }
    }
}
