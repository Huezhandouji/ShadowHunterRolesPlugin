package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

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

    /**
     * **墨紫**（2026-10-31 用户："粒子稍微再改偏向紫色，以及黑色结合"）。
     *
     * <p>介于主紫与黑之间的过渡色：用于环的外圈 / 吸入尾迹，让紫不是"亮晶晶的紫"，而是"带墨的紫"。
     */
    public static final Color INK_PURPLE = Color.fromRGB(78, 24, 112);

    /**
     * **近黑（带紫调）**：吸入动画的"暗部"与核心。比纯黑亮一点 ⇒ 在夜/暗处仍能看出层次，
     * 不会变成"什么都看不见"。
     */
    public static final Color INK_BLACK = Color.fromRGB(22, 10, 30);

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
     *
     * <p><b>2026-10-31 增强</b>：线上每 3 段补一颗 SOUL（魂被钉穿），并在两端各补一个爆点 ——
     * 起点是"出手的炸响"，终点是"命中的落点"；另沿中段加两列平行速度线，让这道光有宽度。
     */
    public static void purpleThrustHunterPullCast(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        double distance = from.distance(to);
        if (distance <= 0.001d) {
            dust(world, to, INK_PURPLE, DUST_SIZE_MAIN);
            return;
        }
        int steps = Math.max(2, (int) Math.ceil(distance / THRUST_STEP));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Location at = from.clone().add(
                    (to.getX() - from.getX()) * t,
                    (to.getY() - from.getY()) * t,
                    (to.getZ() - from.getZ()) * t);
            //① 主轨：紫 / 墨紫交替，粗一号（2026-10-31：由"紫/淡紫"改为"紫/墨紫"，整体更沉、更偏紫黑）
            dust(world, at, (i % 2 == 0) ? MARK_PURPLE : INK_PURPLE, DUST_SIZE_MAIN);
            //② 每 3 段一颗灵魂，暗示"这一枪是钉魂的"
            if (i % 3 == 0) {
                world.spawnParticle(Particle.SOUL, at, 1, 0.06d, 0.06d, 0.06d, 0.004d);
            }
        }
        //③ 速度线：主轨两侧各一条更细的平行虚线（只取中段，避免两端糊）
        Vector axis = to.toVector().subtract(from.toVector());
        if (axis.lengthSquared() > 1.0E-6d) {
            axis.normalize();
            Vector up = new Vector(0d, 1d, 0d);
            Vector right = axis.getCrossProduct(up);
            if (right.lengthSquared() < 1.0E-6d) {
                right = new Vector(1d, 0d, 0d);
            }
            right.normalize();
            for (int side = -1; side <= 1; side += 2) {
                for (int i = 0; i < 6; i++) {
                    double t = 0.22d + 0.56d * i / 5d;
                    Location at = from.clone().add(axis.clone().multiply(distance * t))
                            .add(right.clone().multiply(side * 0.42d))
                            .add(0d, 0.18d, 0d);
                    dust(world, at, side > 0 ? INK_PURPLE : INK_BLACK, DUST_SIZE_SUB);
                }
            }
        }
        //④ 两端爆点：起点"出手炸响"，终点"命中落点"
        ringHunterBurst(world, from, 0.65d, INK_PURPLE, 10, 0d);
        world.spawnParticle(Particle.CRIT, from, 8, 0.25d, 0.25d, 0.25d, 0.12d);
        ringHunterBurst(world, to, 1.0d, MARK_PURPLE, 14, 0d);
        world.spawnParticle(Particle.CRIT, to, 14, 0.35d, 0.35d, 0.35d, 0.16d);
        world.spawnParticle(Particle.SOUL, to, 8, 0.3d, 0.35d, 0.3d, 0.02d);
    }

    /**
     * **牵引光带**（拉回 · 持续）：在"被拽的目标"与"视角前锚点"之间放 3 颗粒子，
     * 让"正被拉过去"这件事在观感上有来源。
     *
     * <p><b>2026-10-31 增强</b>：3 颗点扩成一条"锁链"——沿线段等距铺紫/青交替的链环、
     * 每隔一档加一颗更大的紫点当"锁扣"，并在目标脚下补一圈拖拽冲击环与灵魂火星。
     * 成本仍是每刻十余颗（拉回上限 50 刻），不会因为"丰富"变成粒子雨。
     *
     * <p><b>2026-10-31 第二轮调整</b>：用户反馈"拉拽时灵魂粒子太多" ⇒ **灵魂粒子频率降到原来的
     * 1/4（删去 75%）**：链上的 SOUL 从"每 2 段一颗"改为"每 8 段一颗"，目标胸口 SOUL 2 颗 → 1 颗；
     * 链环粉尘 / 锁扣 / 拖拽冲击环 / 擦地烟保持不变（被删的只是灵魂粒子）。
     *
     * @param target 目标当前位置
     * @param anchor 锚点（角色视角前方）
     */
    public static void pullTetherHunterPullDrag(World world, Location target, Location anchor) {
        if (world == null || target == null || anchor == null) {
            return;
        }
        double distance = target.distance(anchor);
        int steps = Math.max(3, (int) Math.ceil(distance / 0.55d));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (steps + 1);
            Location at = target.clone().add(
                    (anchor.getX() - target.getX()) * t,
                    (anchor.getY() - target.getY()) * t + 0.6d,
                    (anchor.getZ() - target.getZ()) * t);
            //链环：紫 / 墨紫交替；每第 3 段放大一号当"锁扣"
            boolean lock = i % 3 == 0;
            dust(world, at, lock ? MARK_PURPLE : INK_PURPLE, lock ? DUST_SIZE_MAIN : DUST_SIZE_SUB);
            //灵魂粒子：2026-10-31 用户反馈"拉拽时太多" ⇒ 每 8 段一颗（原每 2 段一颗的 1/4，删去 75%）
            if (i % 8 == 0) {
                world.spawnParticle(Particle.SOUL, at, 1, 0.05d, 0.05d, 0.05d, 0.004d);
            }
        }
        //目标脚下：拖拽冲击环 + 擦地火星（"被硬生生拽走"的落点）
        ringHunterBurst(world, target, 0.7d, INK_PURPLE, 8, 0.05d);
        world.spawnParticle(Particle.SMOKE, target.clone().add(0d, 0.1d, 0d), 3, 0.2d, 0.05d, 0.2d, 0.01d);
        //胸口灵魂：2 → 1（同一轮的"删去 75%"调整）
        world.spawnParticle(Particle.SOUL, target.clone().add(0d, 0.9d, 0d), 1, 0.18d, 0.2d, 0.18d, 0.01d);
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
     * <p><b>2026-10-31 增强（用户："更惊艳、丰富、有力量感"）</b>：原实现只有一条青线和 5 颗云。
     * 现在同一帧里叠四层：① 青紫交替的划痕（把"一道光"变成"紫电裹着青锋"）；
     * ② 划痕两侧每段一颗 SOUL（残魂被劈开）；③ 后缘一小圈外扩环（速度感）；④ 脚底擦地烟与 CRIT 火星。
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
                Location point = from.clone().add(
                        (to.getX() - from.getX()) * t,
                        (to.getY() - from.getY()) * t,
                        (to.getZ() - from.getZ()) * t);
                //① 划痕主体：紫 / 墨紫交替（2026-10-31：去掉青色，整体偏紫黑）
                dust(world, point, (i % 2 == 0) ? MARK_PURPLE : INK_PURPLE, DUST_SIZE_SUB);
                //② 划痕两侧的残魂：左右各半格，像被锋刃劈开的灵气
                if (i % 2 == 0) {
                    world.spawnParticle(Particle.SOUL, point.clone().add(0d, 0.35d, 0d), 1, 0.28d, 0.1d, 0.28d, 0.008d);
                }
            }
        }
        //③ 后缘外扩环：一个随位置移动的短命冲击环，给出"速度"的边界
        ringHunterBurst(world, at.clone().add(0d, 0.55d, 0d), 0.55d, INK_PURPLE, 8, 0d);
        //④ 外扩气浪 + 脚底擦地烟 + 火星：力量感落在"蹬地"这一下
        world.spawnParticle(Particle.CLOUD, at, 5, 0.25d, 0.2d, 0.25d, 0.03d);
        world.spawnParticle(Particle.SMOKE, at.clone().add(0d, -0.45d, 0d), 4, 0.22d, 0.05d, 0.22d, 0.01d);
        world.spawnParticle(Particle.CRIT, at.clone().add(0d, 0.7d, 0d), 4, 0.2d, 0.2d, 0.2d, 0.08d);
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

    // ───────── 一技能「扑击」：2026-10-31 增强 ─────────

    /**
     * **扑击 · 起手聚气**：脚下双环（外紫内青）+ 八方向上浮的灵魂 + 身前一道前向风压。
     *
     * <p>原实现起手完全没有粒子 —— 玩家按下去只有音效，看不出"要冲了"。
     * 这里把"力量从地里被拽上来"做成三段：环（蓄）→ 魂（上浮）→ 前向气浪（指向）。
     * 只画一帧，成本极低。
     *
     * @param feet 玩家脚底坐标
     */
    public static void castGatherHunterPounceCharge(World world, Location feet) {
        if (world == null || feet == null) {
            return;
        }
        //① 脚下同心双环：外紫内墨（蓄势；2026-10-31 内环由青改墨紫，配色更沉）
        ringHunterBurst(world, feet, 1.35d, MARK_PURPLE, 16, 0.05d);
        ringHunterBurst(world, feet, 0.85d, INK_PURPLE, 10, 0.05d);
        //② 八方向上浮的残魂：力量从地里冒出来
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2d * i / 8d;
            Location at = feet.clone().add(Math.cos(angle) * 1.05d, 0.15d + i * 0.12d, Math.sin(angle) * 1.05d);
            world.spawnParticle(Particle.SOUL, at, 1, 0.02d, 0.02d, 0.02d, 0.008d);
        }
        //③ 前向风压：脚前方 1~2.2 格的紫墨短气浪（给出"要往哪扑"的指向）
        Vector forward = feet.getDirection();
        forward.setY(0d);
        if (forward.lengthSquared() < 1.0E-6d) {
            forward = new Vector(0d, 0d, 1d);
        }
        forward.normalize();
        for (int i = 1; i <= 4; i++) {
            Location at = feet.clone().add(forward.clone().multiply(0.55d * i)).add(0d, 0.35d, 0d);
            dust(world, at, i % 2 == 0 ? MARK_PURPLE : INK_PURPLE, DUST_SIZE_SUB);
        }
    }

    /**
     * **扑击 · 撕咬命中**：三道爪痕 + 灵魂爆点 + 暴击火星 + 单发爆炸 + 脚下冲击环 + 上冲魂柱。
     *
     * <p>需求只要求"撕咬"，未规定粒子；这里按用户 2026-10-31 的"惊艳、丰富、力量感"补足：
     * 爪痕给出"咬"的形状，爆炸给出"重"，魂柱与冲击环给出"从地面蹬起来又砸下去"的闭环。
     *
     * @param chest 目标胸口锚点（脚底 + 1 格）
     * @param feet  目标脚底
     * @param axis  扑击方向（水平；{@code null} 时用世界 Z 轴兜底）
     */
    public static void biteImpactHunterPounceImpact(World world, Location chest, Location feet, Vector axis) {
        if (world == null || chest == null) {
            return;
        }
        //① 三道爪痕：沿"垂直于扑击方向"的横向排开，从胸口斜向下划开
        Vector forward = axis == null ? new Vector(0d, 0d, 1d) : axis.clone();
        forward.setY(0d);
        if (forward.lengthSquared() < 1.0E-6d) {
            forward = new Vector(0d, 0d, 1d);
        }
        forward.normalize();
        Vector lateral = new Vector(-forward.getZ(), 0d, forward.getX());
        for (int claw = -1; claw <= 1; claw++) {
            double offset = claw * 0.38d;
            Location start = chest.clone()
                    .add(lateral.clone().multiply(offset))
                    .add(forward.clone().multiply(-0.2d))
                    .add(0d, 0.85d, 0d);
            Location end = chest.clone()
                    .add(lateral.clone().multiply(offset * 1.6d))
                    .add(forward.clone().multiply(0.15d))
                    .add(0d, -0.55d, 0d);
            for (int i = 0; i <= 5; i++) {
                double t = (double) i / 5d;
                Location at = start.clone().add(
                        (end.getX() - start.getX()) * t,
                        (end.getY() - start.getY()) * t,
                        (end.getZ() - start.getZ()) * t);
                dust(world, at, claw == 0 ? MARK_PURPLE : INK_PURPLE, claw == 0 ? DUST_SIZE_MAIN : DUST_SIZE_SUB);
            }
        }
        //② 命中核心：魂爆 + 暴击 + 单发爆炸（"咬实了"的分量）
        world.spawnParticle(Particle.SOUL, chest, 14, 0.4d, 0.5d, 0.4d, 0.03d);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, chest, 8, 0.3d, 0.4d, 0.3d, 0.015d);
        world.spawnParticle(Particle.CRIT, chest, 16, 0.45d, 0.55d, 0.45d, 0.18d);
        world.spawnParticle(Particle.SWEEP_ATTACK, chest, 2, 0.25d, 0.25d, 0.25d, 0d);
        world.spawnParticle(Particle.EXPLOSION, chest, 1, 0d, 0d, 0d, 0d);
        //③ 落在脚下的闭环：冲击环 + 上冲魂柱
        if (feet != null) {
            ringHunterBurst(world, feet, 1.5d, MARK_PURPLE, 18, 0.05d);
            for (int i = 0; i < 6; i++) {
                world.spawnParticle(Particle.SOUL, feet.clone().add(0d, 0.1d + i * 0.22d, 0d),
                        1, 0.18d, 0.05d, 0.18d, 0.01d);
            }
        }
    }

    /**
     * **扑击 · 空扑收势**：没咬到任何人时的"咬空"反馈 —— 一小圈青环 + 零星火星与烟。
     *
     * <p>原实现扑空完全无粒子 ⇒ 玩家会以为技能没生效。这里只给"挥空了"的轻量反馈，
     * 与命中时的重爆点拉开强度差，不制造"空扑也有收益"的误读。
     *
     * @param at 玩家当前位置（脚下）
     */
    public static void whiffHunterPounceWhiff(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ringHunterBurst(world, at, 1.0d, INK_PURPLE, 12, 0.05d);
        world.spawnParticle(Particle.CRIT, at.clone().add(0d, 1.0d, 0d), 6, 0.3d, 0.25d, 0.3d, 0.05d);
        world.spawnParticle(Particle.SMOKE, at.clone().add(0d, 0.2d, 0d), 3, 0.2d, 0.05d, 0.2d, 0.01d);
    }

    // ───────── 二技能「拉回」：2026-10-31 增强 ─────────

    /**
     * **拉回 · 蓄力汇聚**（引导 0.3 秒，逐刻调用）：一圈**垂直面**的吸入环随引导收束，
     * 白点只在最外圈、逐刻向里挪 —— 读起来是"空气被吸进手里"。
     *
     * <p><b>2026-10-31 第二轮需求（用户）</b>：
     * <ol>
     *   <li><b>白色粒子外移、改成吸入动画</b>：原先白点（{@code END_ROD}）堆在眼睛位置，
     *       既挡视线又像"在原地冒光"。现在白点只出现在**最外圈**（离准星最远），
     *       且外圈半径随刻数收缩 ⇒ 被"吸"向手心；</li>
     *   <li><b>减少视线遮挡</b>：汇聚中心由组件给到**胸前偏下 + 沿朝向前移 0.65 格**，
     *       环面垂直于朝向（侧立），不横在准星正前方；竖直半径再乘 0.5，最高点压在眼睛下方；</li>
     *   <li><b>配色偏紫 + 黑色结合</b>：主环三色交替（主紫 / 墨紫 / 近黑），尾迹全用墨紫，
     *       核心用近黑裹紫 ⇒ 整体从"紫青亮片"变成"墨紫吸入"。</li>
     * </ol>
     *
     * @param center  汇聚中心（组件给"胸前偏下、沿朝向前移"的位置；**不要给眼睛位置**）
     * @param forward 施法者水平朝向（{@code null} 时按世界 Z 轴兜底）
     * @param tick    引导已过的刻数（0 起）
     */
    public static void channelGatherHunterPullChannel(World world, Location center, Vector forward, int tick) {
        if (world == null || center == null) {
            return;
        }
        Vector axis = forward == null ? new Vector(0d, 0d, 1d) : forward.clone();
        axis.setY(0d);
        if (axis.lengthSquared() < 1.0E-6d) {
            axis = new Vector(0d, 0d, 1d);
        }
        axis.normalize();
        Vector right = axis.getCrossProduct(new Vector(0d, 1d, 0d));
        if (right.lengthSquared() < 1.0E-6d) {
            right = new Vector(1d, 0d, 0d);
        }
        right.normalize();
        Vector up = right.getCrossProduct(axis).normalize();

        int total = 5;
        int t = Math.max(0, Math.min(tick, total));
        double progress = (double) t / total;
        // 吸入：外圈 1.10 → 0.65、内圈 0.62 → 0.12；整圈随进度旋转（螺旋吸入）
        double outer = 1.10d - 0.45d * progress;
        double inner = 0.62d - 0.50d * progress;
        double spin = progress * Math.PI * 1.5d;

        //① 白色粒子外移：只在最外圈出现，每刻随外圈收缩向里挪（被吸进来，不挡准星）
        for (int i = 0; i < 4; i++) {
            double angle = spin + Math.PI * 2d * i / 4d;
            Location at = center.clone()
                    .add(right.clone().multiply(Math.cos(angle) * outer))
                    .add(up.clone().multiply(Math.sin(angle) * outer * 0.5d));
            world.spawnParticle(Particle.END_ROD, at, 1, 0d, 0d, 0d, 0d);
        }
        //② 吸入环：主紫 / 墨紫 / 近黑三色交替，半径交错（内一圈外一圈 ⇒ 有"层"在往手里收）
        for (int i = 0; i < 10; i++) {
            double angle = spin + Math.PI * 2d * i / 10d;
            double r = inner + (outer - inner) * (i % 2 == 0 ? 0.18d : 0.62d);
            Location at = center.clone()
                    .add(right.clone().multiply(Math.cos(angle) * r))
                    .add(up.clone().multiply(Math.sin(angle) * r * 0.5d));
            Color color = (i % 3 == 0) ? INK_BLACK : ((i % 3 == 1) ? MARK_PURPLE : INK_PURPLE);
            dust(world, at, color, i % 2 == 0 ? DUST_SIZE_SUB : DUST_SIZE_MAIN);
        }
        //③ 吸入尾迹：三条从外圈指向内圈的暗紫短线（越蓄越短 ⇒ 吸入感）
        for (int i = 0; i < 3; i++) {
            double angle = spin + Math.PI * 2d * i / 3d;
            for (int k = 1; k <= 3; k++) {
                double r = inner + (outer - inner) * (k / 4d);
                Location at = center.clone()
                        .add(right.clone().multiply(Math.cos(angle) * r))
                        .add(up.clone().multiply(Math.sin(angle) * r * 0.5d));
                dust(world, at, INK_PURPLE, DUST_SIZE_SUB);
            }
        }
        //④ 核心：近黑裹着一点紫（吸满那一刻再补两颗火星）
        dust(world, center, INK_BLACK, DUST_SIZE_MAIN * 1.3f);
        if (t >= total) {
            world.spawnParticle(Particle.CRIT, center, 2, 0.06d, 0.06d, 0.06d, 0.01d);
        }
    }

    /**
     * **拉回 · 命中冲击扇**：以被命中者为心，沿"拉回方向"的垂直平面炸开 8 道放射线。
     *
     * <p>原命中只有暴击 + 单爆炸 + 菱形，缺"这一击打出了冲击波"的方向感。
     * 8 道放射线从 0.3 格铺到 1.2 格，长短不一（越接近水平方向越长），读起来像被钉住后炸开的冲击。
     *
     * @param chest        被命中者胸口
     * @param pullDirection 拉回方向（目标 → 施法者；{@code null} 时用世界 Z 轴兜底）
     */
    public static void impactFanHunterPullHit(World world, Location chest, Vector pullDirection) {
        if (world == null || chest == null) {
            return;
        }
        Vector axis = pullDirection == null ? new Vector(0d, 0d, 1d) : pullDirection.clone();
        if (axis.lengthSquared() < 1.0E-6d) {
            axis = new Vector(0d, 0d, 1d);
        }
        axis.normalize();
        Vector right = axis.getCrossProduct(new Vector(0d, 1d, 0d));
        if (right.lengthSquared() < 1.0E-6d) {
            right = new Vector(1d, 0d, 0d);
        }
        right.normalize();
        Vector up = right.getCrossProduct(axis).normalize();
        for (int ray = 0; ray < 8; ray++) {
            double angle = Math.PI * 2d * ray / 8d;
            Vector dir = right.clone().multiply(Math.cos(angle)).add(up.clone().multiply(Math.sin(angle)));
            //水平方向（±x）那几道更长，竖直方向更短 ⇒ 冲击扇"扁"的有速度感
            double length = 0.85d + 0.45d * Math.abs(Math.cos(angle));
            for (int i = 1; i <= 4; i++) {
                double d = length * i / 4d;
                Location at = chest.clone().add(dir.clone().multiply(d));
                dust(world, at, i % 2 == 0 ? MARK_PURPLE : INK_PURPLE,
                        i == 4 ? DUST_SIZE_MAIN : DUST_SIZE_SUB);
            }
        }
        world.spawnParticle(Particle.SOUL, chest, 10, 0.35d, 0.45d, 0.35d, 0.02d);
        world.spawnParticle(Particle.CRIT, chest, 12, 0.4d, 0.45d, 0.4d, 0.16d);
    }

    /**
     * **拉回 · 0.8 秒锁链蓄势**（等待相逐刻调用）：目标与锚点之间一条暗紫虚线 + 身上收缩锁环。
     *
     * <p>命中后到真正开拉之间有 0.8 秒空窗，原实现什么都不画 ⇒ 玩家以为技能结束了。
     * 这条"正在锁住"的线与环填上这段空窗，并把"还有多久开拉"做成可见的收缩。
     *
     * @param targetChest   被命中者胸口
     * @param anchor        施法者视角前锚点
     * @param remainingTicks 等待相剩余刻（0..{@code delayLeft}）
     */
    public static void delayLockHunterPullDelay(World world, Location targetChest, Location anchor, int remainingTicks) {
        if (world == null || targetChest == null || anchor == null) {
            return;
        }
        //① 暗紫虚线：目标 → 锚点（每 1.1 格一颗，比拖拽时的锁链更稀疏 ⇒ "还没拉紧"）
        double distance = targetChest.distance(anchor);
        int steps = Math.max(2, (int) Math.ceil(distance / 1.1d));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (steps + 1);
            Location at = targetChest.clone().add(
                    (anchor.getX() - targetChest.getX()) * t,
                    (anchor.getY() - targetChest.getY()) * t,
                    (anchor.getZ() - targetChest.getZ()) * t);
            dust(world, at, INK_PURPLE, DUST_SIZE_SUB);
        }
        //② 目标身上的锁环：半径随剩余刻收缩（0.9 → 0.35），把倒计时变成可见的"越锁越紧"
        int total = 16;
        int remain = Math.max(0, Math.min(remainingTicks, total));
        double radius = 0.35d + 0.55d * ((double) remain / total);
        ringHunterBurst(world, targetChest, radius, MARK_PURPLE, 10, 0d);
        //③ 锚点处的"钩爪"火星：指示"要拉到哪"
        world.spawnParticle(Particle.CRIT, anchor, 2, 0.12d, 0.12d, 0.12d, 0.02d);
    }

    /**
     * **拉回 · 起拉瞬间**：锁链从"暗紫虚线"变成"亮紫实链"，目标胸口一记爆点。
     *
     * <p>0.8 秒结束的那一刻必须有一个明确的"抓住了"事件，否则开拉本身没有重音。
     *
     * @param targetChest 被命中者胸口
     * @param anchor      锚点
     */
    public static void gripBurstHunterPullBegin(World world, Location targetChest, Location anchor) {
        if (world == null || targetChest == null) {
            return;
        }
        ringHunterBurst(world, targetChest, 0.85d, MARK_PURPLE, 14, 0d);
        world.spawnParticle(Particle.SOUL, targetChest, 8, 0.3d, 0.35d, 0.3d, 0.02d);
        world.spawnParticle(Particle.CRIT, targetChest, 8, 0.25d, 0.25d, 0.25d, 0.1d);
        if (anchor != null) {
            //亮紫实链：比等待相的虚线更密更亮
            double distance = targetChest.distance(anchor);
            int steps = Math.max(2, (int) Math.ceil(distance / 0.45d));
            for (int i = 1; i <= steps; i++) {
                double t = (double) i / (steps + 1);
                Location at = targetChest.clone().add(
                        (anchor.getX() - targetChest.getX()) * t,
                        (anchor.getY() - targetChest.getY()) * t,
                        (anchor.getZ() - targetChest.getZ()) * t);
                dust(world, at, i % 2 == 0 ? MARK_PURPLE : INK_PURPLE, DUST_SIZE_MAIN);
            }
        }
    }

    /**
     * **拉回 · 再次释放（加速）**：施法者脚下一圈青环 + 一道更亮的"收紧链"。
     *
     * <p>"再次释放"原实现只有音效；补一圈环与一道亮链，让"我把绳子猛地一扯"这个动作看得见。
     *
     * @param ownerLocation 施法者脚下
     * @param targetChest   被拉目标胸口（可为 {@code null} ⇒ 只画脚下环）
     */
    public static void accelerateBurstHunterPullSprint(World world, Location ownerLocation, Location targetChest) {
        if (world == null || ownerLocation == null) {
            return;
        }
        ringHunterBurst(world, ownerLocation, 1.0d, MARK_PURPLE, 12, 0.05d);
        world.spawnParticle(Particle.CRIT, ownerLocation.clone().add(0d, 1.0d, 0d),
                6, 0.25d, 0.3d, 0.25d, 0.06d);
        if (targetChest == null) {
            return;
        }
        double distance = ownerLocation.distance(targetChest);
        int steps = Math.max(2, (int) Math.ceil(distance / 0.5d));
        for (int i = 1; i <= steps; i++) {
            double t = (double) i / (steps + 1);
            Location at = ownerLocation.clone().add(
                    (targetChest.getX() - ownerLocation.getX()) * t,
                    (targetChest.getY() - ownerLocation.getY()) * t + 0.9d,
                    (targetChest.getZ() - ownerLocation.getZ()) * t);
            if (i % 2 == 0) {
                dust(world, at, INK_PURPLE, DUST_SIZE_SUB);
            }
        }
    }

    /**
     * **拉回 · 到位**：目标被拽到锚点那一下的收尾爆点（单发爆炸 + 环 + 魂柱 + 火星）。
     *
     * @param at 目标落点（脚底）
     */
    public static void arriveBurstHunterPullImpact(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ringHunterBurst(world, at, 1.4d, MARK_PURPLE, 18, 0.05d);
        ringHunterBurst(world, at, 0.8d, INK_PURPLE, 10, 0.05d);
        world.spawnParticle(Particle.EXPLOSION, at.clone().add(0d, 1.0d, 0d), 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.SOUL, at.clone().add(0d, 1.0d, 0d), 12, 0.4d, 0.5d, 0.4d, 0.03d);
        world.spawnParticle(Particle.CRIT, at.clone().add(0d, 1.0d, 0d), 12, 0.4d, 0.4d, 0.4d, 0.16d);
        for (int i = 0; i < 5; i++) {
            world.spawnParticle(Particle.SOUL, at.clone().add(0d, 0.1d + i * 0.24d, 0d),
                    1, 0.16d, 0.05d, 0.16d, 0.01d);
        }
        world.spawnParticle(Particle.SMOKE, at.clone().add(0d, 0.1d, 0d), 5, 0.25d, 0.05d, 0.25d, 0.01d);
    }

    /**
     * **拉回 · 目标脱离**：它自己挣脱了 —— 轻量紫雾 + 一圈散开的链环（不画重爆点，避免误读成"拉到了"）。
     *
     * @param chest 目标胸口
     */
    public static void escapePuffHunterPullEscape(World world, Location chest) {
        if (world == null || chest == null) {
            return;
        }
        world.spawnParticle(Particle.CLOUD, chest, 6, 0.3d, 0.3d, 0.3d, 0.02d);
        world.spawnParticle(Particle.SOUL, chest, 4, 0.25d, 0.3d, 0.25d, 0.01d);
        ringHunterBurst(world, chest, 0.9d, INK_PURPLE, 10, 0d);
    }

    // ───────── 内部几何：水平菱形边框 ─────────

    /**
     * 画一个**水平圆环**（{@code points} 个等分点），常用于起手 / 命中 / 拖拽的冲击环。
     *
     * <p>纯几何：不读任何状态；入参为 {@code null}、半径 ≤ 0 或点数 ≤ 0 时静默返回。
     *
     * @param center  环心
     * @param radius  半径（格）
     * @param color   颜色（本类主题色常量）
     * @param points  环上点数
     * @param yOffset 相对 {@code center} 的高度偏移
     */
    private static void ringHunterBurst(World world, Location center, double radius, Color color, int points,
                                        double yOffset) {
        if (world == null || center == null || color == null || radius <= 0d || points <= 0) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2d * i / points;
            Location at = center.clone().add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
            dust(world, at, color, DUST_SIZE_MAIN);
        }
    }

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
