package com.shadowHunterRolesPlugin.roleComponent.custom.lin;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * 「林」的**纯粒子工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求的观感形状算成坐标并 spawn 出去。所有几何都是**纯函数**
 * （给相位 / 半径 / 点数 ⇒ 一组坐标），组件只负责"什么时候画、画在哪"。
 *
 * <p><b>为什么独立成类</b>：工程约定"组件需要专属类支持时，专属类放到与组件同级的文件夹下"
 * （照 {@code custom/tek/TekVfx} 的既有形态）⇒ 粒子几何不进组件、更不进框架。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 * 主题色集中在下面几个 {@code public static final} 常量里，要调色只改那一处。
 *
 * <p>命名规范（用户定的规矩）：{@code <主要形状> + Lin + <技能> + <阶段>}。
 */
public final class LinVfx {

    private LinVfx() {
    }

    // ───────── 主题色 ─────────

    /** 林的主色：紫（打击 / 镇压）。 */
    public static final Color LIN_PURPLE = Color.fromRGB(150, 60, 220);

    /** 亮紫（叠一层更亮的紫做层次）。 */
    public static final Color LIN_PURPLE_BRIGHT = Color.fromRGB(205, 130, 255);

    /** 殉道 / 十字架的白。 */
    public static final Color LIN_HOLY_WHITE = Color.fromRGB(255, 255, 255);

    /** 「开普勒装置」不死图腾的金绿（原版不死图腾的观感色）。 */
    public static final Color LIN_TOTEM_GREEN = Color.fromRGB(120, 230, 140);

    /** 「三重打击」石英白。 */
    public static final Color LIN_QUARTZ_WHITE = Color.fromRGB(245, 245, 235);

    /** 「透骨钉」狙击枪的白灰主色调。 */
    public static final Color LIN_RAIL_WHITE = Color.fromRGB(240, 242, 248);

    /** 「透骨钉」狙击枪的灰。 */
    public static final Color LIN_RAIL_GRAY = Color.fromRGB(140, 146, 158);

    private static final float SIZE_MAIN = 1.2f;
    private static final float SIZE_SUB = 0.9f;

    // ───────── ① 铁血镇压者：紫色打击粒子 ─────────

    /**
     * **紫色打击粒子**（需求："附带有紫色的打击粒子特效"）。
     *
     * <p>形状 = 命中点周围一圈向外扩散的紫色粉尘 + 一点亮紫，做出"被钝器砸中"的冲击感。
     *
     * @param world  世界（{@code null} ⇒ 静默）
     * @param at     命中位置（{@code null} ⇒ 静默）
     */
    public static void ironBloodHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 12, 0.35d, 0.35d, 0.35d, 0d,
                new Particle.DustOptions(LIN_PURPLE, SIZE_MAIN));
        world.spawnParticle(Particle.DUST, at, 6, 0.22d, 0.22d, 0.22d, 0d,
                new Particle.DustOptions(LIN_PURPLE_BRIGHT, SIZE_SUB));
        world.spawnParticle(Particle.CRIT, at, 4, 0.2d, 0.2d, 0.2d, 0.05d);
    }

    // ───────── ② 开普勒装置：能量转化 / 踪迹 ─────────

    /**
     * **能量转化时喷出的不死图腾粒子**（需求："慢慢喷出不死图腾的粒子"）。
     *
     * <p>"慢慢"= 每次只给稀疏的几颗、且带一点上升速度 ⇒ 看上去是缓缓升腾而不是爆开。
     *
     * @param world 世界
     * @param at    玩家脚下
     */
    public static void keplerConvert(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0d, 0.2d, 0d),
                2, 0.25d, 0.05d, 0.25d, 0.012d);
    }

    /**
     * **脚下少量不死图腾踪迹粒子**（需求："当能量到达一定值时，林脚下出现少量不死图腾的踪迹粒子"）。
     *
     * <p>沿脚下一小圈铺几颗，刻意做得比 {@link #keplerConvert} 更"贴地、更稀" ——
     * 它的语义是"状态标记"，不是"施法特效"。
     *
     * @param world 世界
     * @param at    玩家脚下
     * @param phase 相位（由组件每刻推进 ⇒ 缓慢绕行）
     */
    public static void keplerFootprint(World world, Location at, double phase) {
        if (world == null || at == null) {
            return;
        }
        final int points = 4;
        final double radius = 0.75d;
        for (int i = 0; i < points; i++) {
            double angle = phase + (Math.PI * 2d / points) * i;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            world.spawnParticle(Particle.TOTEM_OF_UNDYING,
                    at.clone().add(x, 0.08d, z), 1, 0d, 0d, 0d, 0d);
        }
    }

    // ───────── ③ 殉道：爆炸 + 巨大白色十字架 ─────────

    /**
     * **殉道：周身爆炸粒子 + 身后巨大 4 格高白色十字架**（需求逐字：）。
     *
     * <p>十字架画法 = 以玩家为中心、在**朝向后方的竖直平面**上画一个"竖杆 + 横杆"的十字：
     * <ul>
     *   <li>竖杆：从脚下向上 {@link #CROSS_HEIGHT} 格，位于玩家身后 {@code CROSS_DISTANCE} 格；</li>
     *   <li>横杆：竖杆的 0.62 高度处左右各 {@code CROSS_ARM} 格。</li>
     * </ul>
     * 平面取"玩家朝向的侧向 × 世界竖直"⇒ 无论玩家朝哪，十字都端正地立在身后，
     * 而不是变成一个斜的投影。
     *
     * @param world  世界
     * @param center 玩家位置
     * @param facing 玩家水平朝向（可为 {@code null} ⇒ 退回世界 -Z）
     */
    public static void martyrCross(World world, Location center, Vector facing) {
        if (world == null || center == null) {
            return;
        }
        Vector forward = horizontalUnit(facing);
        Vector back = forward.clone().multiply(-1d);
        Vector side = new Vector(-forward.getZ(), 0d, forward.getX());

        Location base = center.clone().add(back.clone().multiply(CROSS_DISTANCE));

        // 竖杆（自脚下向上 CROSS_HEIGHT 格）
        for (double y = 0d; y <= CROSS_HEIGHT; y += CROSS_STEP) {
            Location point = base.clone().add(0d, y, 0d);
            world.spawnParticle(Particle.DUST, point, 2, 0.06d, 0.06d, 0.06d, 0d,
                    new Particle.DustOptions(LIN_HOLY_WHITE, SIZE_MAIN));
        }
        // 横杆（竖杆 62% 高度处，左右各 CROSS_ARM 格）
        double armY = CROSS_HEIGHT * CROSS_ARM_RATIO;
        for (double x = -CROSS_ARM; x <= CROSS_ARM; x += CROSS_STEP) {
            Location point = base.clone().add(side.clone().multiply(x)).add(0d, armY, 0d);
            world.spawnParticle(Particle.DUST, point, 2, 0.06d, 0.06d, 0.06d, 0d,
                    new Particle.DustOptions(LIN_HOLY_WHITE, SIZE_MAIN));
        }
        // 整体轮廓再铺一层"泛光"（末端烛光让白色更亮）
        world.spawnParticle(Particle.END_ROD, base.clone().add(0d, CROSS_HEIGHT, 0d), 6, 0.1d, 0.1d, 0.1d, 0.01d);
    }

    /** 殉道瞬间：周身爆炸粒子（需求："林周身出现爆炸粒子"）。 */
    public static void martyrBurst(World world, Location center) {
        if (world == null || center == null) {
            return;
        }
        Location at = center.clone().add(0d, 1d, 0d);
        world.spawnParticle(Particle.EXPLOSION, at, 3, 0.6d, 0.7d, 0.6d, 0d);
        world.spawnParticle(Particle.DUST, at, 40, 0.7d, 0.9d, 0.7d, 0.08d,
                new Particle.DustOptions(LIN_HOLY_WHITE, SIZE_MAIN));
        world.spawnParticle(Particle.END_ROD, at, 24, 0.6d, 0.8d, 0.6d, 0.14d);
    }

    /** 十字架高度（格，需求原话"巨大的4格高"）。 */
    public static final double CROSS_HEIGHT = 4.0d;

    /** 十字架到玩家的水平距离（格）：站在身后一点点，不与人重叠。 */
    private static final double CROSS_DISTANCE = 0.8d;

    /** 横杆半长（格）。 */
    private static final double CROSS_ARM = 1.2d;

    /** 横杆高度占竖杆全高的比例。 */
    private static final double CROSS_ARM_RATIO = 0.62d;

    /** 十字架描点步长（格）。 */
    private static final double CROSS_STEP = 0.22d;

    // ───────── ④ 三重打击：白色爆炸粒子 ─────────

    /**
     * **三重打击命中：暴击粒子**（需求 2026-10-08 改版："去掉林身上的爆炸粒子，改成暴击粒子"）。
     *
     * <p>★ 改动口径：原实现是"白色爆炸粒子"（{@code EXPLOSION} + 白尘 + {@code FLASH}），
     * 现改为<b>原版暴击星芒</b>（{@code CRIT}）—— 它是"会心一击"的通用视觉语言，
     * 与"强化过的三次普攻"语义更贴，也不再触发爆炸的"大招感"。
     * 周围的白色石英尘保留（否则只有星芒会显得太素、看不出"白色"这一层要求）。
     *
     * <p>★ {@code FLASH} 已**移除**：它正是 2026-10-08 那起
     * {@code missing required data class org.bukkit.Color} 崩溃的源头（漏传 data ⇒ 整个角色被下线）。
     * 本方法不再使用它，也就不会再踩那个坑。若将来要用，务必走**带 data 的泛型重载**。
     */
    public static void tripleStrikeBurst(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        // 暴击星芒（主视觉）
        world.spawnParticle(Particle.CRIT, at, 18, 0.35d, 0.35d, 0.35d, 0.12d);
        // 白色石英尘（衬底，保住"白色"这一层要求）
        world.spawnParticle(Particle.DUST, at, 12, 0.35d, 0.35d, 0.35d, 0.04d,
                new Particle.DustOptions(LIN_QUARTZ_WHITE, SIZE_MAIN));
    }

    /**
     * **三重打击施放：在林身体中间生成不死图腾爆炸粒子**（需求 2026-10-08：
     * "于林的身体中间生成不死图腾爆炸粒子"）。
     *
     * <p>"身体中间" = 玩家位置向上 1 格（脚与头之间，约命中心口）——
     * 直接取 {@code getLocation()} 会落在脚底，观感是"地上冒图腾"而不是"身上炸图腾"。
     *
     * @param world  世界
     * @param bodyMid 玩家身体中部（调用方已抬高 1 格）
     */
    public static void tripleStrikeTotemBurst(World world, Location bodyMid) {
        if (world == null || bodyMid == null) {
            return;
        }
        // 图腾金绿的一团"爆炸式"扩散（用速度偏差做出向外炸开的形状）
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, bodyMid, 40, 0.5d, 0.6d, 0.5d, 0.35d);
        // 内层再垫一圈更密的小范围图腾粒子 ⇒ 中心有"实心感"
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, bodyMid, 18, 0.2d, 0.25d, 0.2d, 0.08d);
    }

    // ───────── ⑤ 影袭：横向紫色螺旋 ─────────

    /**
     * **影袭留下的横向紫色螺旋粒子**（需求："留下由跃进点开始到结束点的横向紫色螺旋粒子"）。
     *
     * <p>螺旋 = 沿跃进路径前进的同时绕轴旋转：{@code 角度 = t * TURNS * 2π + phaseOf(t)}。
     * 半径做一点收放（中间粗、两端细）⇒ 看上去像一道被拉开的紫电。
     *
     * @param world 世界
     * @param from  跃进点
     * @param to    结束点
     */
    public static void shadowRaidSpiral(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6d) {
            return;
        }
        Vector axis = path.clone().normalize();
        Vector side = perpendicularUnit(axis);
        Vector up = axis.clone().crossProduct(side).normalize();

        final int samples = 90;
        final double turns = 3.0d;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double angle = t * turns * Math.PI * 2d;
            // 半径做"中间粗、两端细"的收放
            double radius = 0.45d * Math.sin(Math.PI * t) + 0.12d;
            Vector offset = side.clone().multiply(Math.cos(angle) * radius)
                    .add(up.clone().multiply(Math.sin(angle) * radius));
            Location point = from.clone().add(path.clone().multiply(t)).add(offset);
            Particle.DustOptions options = (i % 2 == 0)
                    ? new Particle.DustOptions(LIN_PURPLE, SIZE_MAIN)
                    : new Particle.DustOptions(LIN_PURPLE_BRIGHT, SIZE_SUB);
            world.spawnParticle(Particle.DUST, point, 1, 0d, 0d, 0d, 0d, options);
        }
    }

    // ───────── ⑥ 透骨钉：手部科幻狙击枪 + 射线 ─────────

    /**
     * **手部勾勒的科幻狙击枪**（需求："在其手部勾勒一把由白色灰色为主色调的科幻粒子狙击枪"）。
     *
     * <p>画法 = 在"持弓动作"的手部位置，沿视线方向拉出一条**细长的白灰枪管**，
     * 并在枪身中段加一个"瞄准镜"小环。整体随玩家朝向走 ⇒ 看上去像是真的端着一把枪。
     *
     * @param world  世界
     * @param eye    玩家眼睛位置
     * @param facing 视线方向
     */
    public static void boneNailRifle(World world, Location eye, Vector facing) {
        if (world == null || eye == null) {
            return;
        }
        Vector forward = horizontalUnit(facing);
        Vector right = new Vector(-forward.getZ(), 0d, forward.getX());
        Vector up = new Vector(0d, 1d, 0d);

        // 枪口基准：右前方一点、略低于视线（像端在手上）
        Location stock = eye.clone().add(forward.clone().multiply(0.35d))
                .add(right.clone().multiply(0.28d)).add(0d, -0.32d, 0d);

        // 枪管：沿视线向前 1.6 格
        final double barrel = 1.6d;
        final int tubePoints = 14;
        for (int i = 0; i <= tubePoints; i++) {
            double t = (double) i / tubePoints;
            Location point = stock.clone().add(forward.clone().multiply(barrel * t));
            Particle.DustOptions options = (i % 3 == 0)
                    ? new Particle.DustOptions(LIN_RAIL_GRAY, SIZE_SUB)
                    : new Particle.DustOptions(LIN_RAIL_WHITE, SIZE_SUB);
            world.spawnParticle(Particle.DUST, point, 1, 0.01d, 0.01d, 0.01d, 0d, options);
        }
        // 瞄准镜：枪管中段的一个小竖环
        Location scopeBase = stock.clone().add(forward.clone().multiply(barrel * 0.45d)).add(0d, 0.12d, 0d);
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI * 2d * i / 6d;
            Location point = scopeBase.clone()
                    .add(right.clone().multiply(Math.cos(angle) * 0.08d))
                    .add(up.clone().multiply(Math.sin(angle) * 0.08d));
            world.spawnParticle(Particle.DUST, point, 1, 0d, 0d, 0d, 0d,
                    new Particle.DustOptions(LIN_RAIL_GRAY, SIZE_SUB));
        }
        // 枪口焰：末端一颗亮白
        world.spawnParticle(Particle.END_ROD,
                stock.clone().add(forward.clone().multiply(barrel)), 1, 0d, 0d, 0d, 0d);
    }

    /**
     * **透骨钉射出的螺旋白色射线**（需求："发射出一条每秒前进30格的大威力白色射线，伴随螺旋状粒子"）。
     *
     * <p>由组件沿射线逐段推进并每段调本方法；这里只画"从 {@code from} 到 {@code to} 这一段"的螺旋。
     *
     * @param world    世界
     * @param from     这一段起点
     * @param to       这一段终点
     * @param phase    螺旋相位（由组件推进 ⇒ 每段接得上）
     */
    public static void boneNailBeam(World world, Location from, Location to, double phase) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector path = to.toVector().subtract(from.toVector());
        double length = path.length();
        if (length < 1.0E-6d) {
            return;
        }
        Vector axis = path.clone().normalize();
        Vector side = perpendicularUnit(axis);
        Vector up = axis.clone().crossProduct(side).normalize();

        final int samples = 10;
        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            double angle = phase + t * Math.PI * 1.6d;
            double radius = 0.22d;
            Vector offset = side.clone().multiply(Math.cos(angle) * radius)
                    .add(up.clone().multiply(Math.sin(angle) * radius));
            Location point = from.clone().add(path.clone().multiply(t)).add(offset);
            world.spawnParticle(Particle.DUST, point, 2, 0.02d, 0.02d, 0.02d, 0d,
                    new Particle.DustOptions(LIN_RAIL_WHITE, SIZE_MAIN));
        }
        // 中轴再加一条亮白芯，让射线"有骨"
        world.spawnParticle(Particle.END_ROD, from.clone().add(path.clone().multiply(0.5d)),
                3, 0.03d, 0.03d, 0.03d, 0d);
    }

    /** 透骨钉命中时的白色爆点。 */
    public static void boneNailImpact(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.DUST, at, 20, 0.35d, 0.35d, 0.35d, 0.06d,
                new Particle.DustOptions(LIN_RAIL_WHITE, SIZE_MAIN));
    }

    /**
     * **透骨钉开火瞬间的枪口爆炸粒子**（需求 2026-10-08："在射出时在枪口位置出现单个爆炸粒子"）。
     *
     * <p>枪口位置 = 眼睛沿视线向前 {@value #MUZZLE_AHEAD} 格、略下（与
     * {@link #boneNailRifle} 里枪管末端同一处）⇒ 观感是"枪口喷了一下"。
     *
     * <p>★ "单个"按需求取字面语义：{@code EXPLOSION} 只发 <b>1 颗</b>、且无扩散偏差。
     *
     * @param world  世界
     * @param eye    玩家眼睛位置
     * @param facing 视线方向
     */
    public static void boneNailMuzzleFlash(World world, Location eye, Vector facing) {
        if (world == null || eye == null) {
            return;
        }
        Location muzzle = muzzleLocation(eye, facing);
        world.spawnParticle(Particle.EXPLOSION, muzzle, 1, 0d, 0d, 0d, 0d);
    }

    /** 枪口位置（纯函数 ⇒ 组件与测试共用同一口径）。 */
    public static Location muzzleLocation(Location eye, Vector facing) {
        if (eye == null) {
            return null;
        }
        Vector forward = horizontalUnit(facing);
        return eye.clone().add(forward.multiply(MUZZLE_AHEAD)).add(0d, -0.32d, 0d);
    }

    /** 枪口到眼睛的前方距离（格）：与 {@link #boneNailRifle} 的枪管末端对齐。 */
    private static final double MUZZLE_AHEAD = 1.6d;

    /**
     * **命中敌人：白色闪电击中铜块的粒子**（需求 2026-10-08：
     * "敌人被击中会附带白色的闪电击中铜块粒子"）。
     *
     * <p>★ 为什么用 {@code ELECTRIC_SPARK}：1.21.11 的 {@code Particle} 枚举里
     * **没有**"铜块被闪电击中"这个独立粒子（那是 {@code Sound.BLOCK_COPPER_BREAK} 的听觉事件）。
     * 视觉上最贴切的就是 {@code ELECTRIC_SPARK}（原版电火花，闪电击铜块时周围迸的那种）——
     * 白色由粒子自身的亮白观感给出，无需 data。
     *
     * @param world  世界
     * @param at     敌人身体中部
     */
    public static void boneNailCopperSpark(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 28, 0.4d, 0.55d, 0.4d, 0.22d);
    }

    /**
     * **白色穿刺特效**（需求 2026-10-08："白色的穿刺特效"）。
     *
     * <p>形状 = 沿射线方向的一根**细长白色刺**：从命中点沿入射方向向前后各伸一段，
     * 用密集的白尘点串出来，末端加一颗亮白 ⇒ 看上去像一根钉穿过去的锥。
     *
     * @param world  世界
     * @param at     命中点（敌人身体中部）
     * @param axis   入射方向（射线方向；{@code null} ⇒ 退回世界 -Z）
     */
    public static void boneNailPierce(World world, Location at, Vector axis) {
        if (world == null || at == null) {
            return;
        }
        Vector dir = (axis == null) ? new Vector(0d, 0d, -1d) : axis.clone();
        if (dir.lengthSquared() < 1.0E-9d) {
            dir = new Vector(0d, 0d, -1d);
        }
        dir.normalize();

        final int points = 12;
        final double halfLength = 0.95d;
        for (int i = -points; i <= points; i++) {
            double t = (double) i / points * halfLength;
            Location point = at.clone().add(dir.clone().multiply(t));
            // 越靠尖端越细：用半径随 |t| 收缩来表达"刺"
            float size = (float) (SIZE_SUB * (1.0d - 0.5d * Math.abs(t) / halfLength));
            world.spawnParticle(Particle.DUST, point, 1, 0.02d, 0.02d, 0.02d, 0d,
                    new Particle.DustOptions(LIN_HOLY_WHITE, Math.max(0.3f, size)));
        }
        // 尖端亮白
        world.spawnParticle(Particle.END_ROD, at.clone().add(dir.clone().multiply(halfLength)),
                3, 0.04d, 0.04d, 0.04d, 0.01d);
        world.spawnParticle(Particle.END_ROD, at.clone().add(dir.clone().multiply(-halfLength)),
                3, 0.04d, 0.04d, 0.04d, 0.01d);
    }

    /**
     * **被钉墙控制期间的"3 个平面菱形围住敌人身体"**（需求 2026-10-08）。
     *
     * <p>三个菱形等分绕竖直轴摆放（相位 0 / 120° / 240°），每个菱形都是**竖直平面**里的一个
     * 四点闭合框（上尖 / 下尖 / 左右角）⇒ 合起来是一个"围笼"，且都是平面图形（不是立体环）。
     *
     * @param world  世界
     * @param center 敌人身体中部
     * @param phase  相位（组件每刻推进 ⇒ 菱形缓慢升降 / 呼吸）
     */
    public static void boneNailPinDiamonds(World world, Location center, double phase) {
        if (world == null || center == null) {
            return;
        }
        final int faces = 3;
        final int stepsPerEdge = 7;
        final double halfWidth = 0.72d;
        final double halfHeight = 1.05d;
        for (int f = 0; f < faces; f++) {
            double yaw = phase * 0.0d + (Math.PI * 2d / faces) * f;
            // 该菱形所在竖直平面的"横向"单位向量（水平面里绕竖直轴旋转）
            Vector side = new Vector(Math.cos(yaw), 0d, Math.sin(yaw));
            // 四个顶点：右角 / 上尖 / 左角 / 下尖
            Vector right = side.clone().multiply(halfWidth);
            Vector left = side.clone().multiply(-halfWidth);
            Vector top = new Vector(0d, halfHeight, 0d);
            Vector bottom = new Vector(0d, -halfHeight, 0d);
            drawEdge(world, center, right, top, stepsPerEdge);
            drawEdge(world, center, top, left, stepsPerEdge);
            drawEdge(world, center, left, bottom, stepsPerEdge);
            drawEdge(world, center, bottom, right, stepsPerEdge);
        }
    }

    /** 从 {@code a} 到 {@code b} 连一条白色尘线（供 {@link #boneNailPinDiamonds} 描菱形边）。 */
    private static void drawEdge(World world, Location center, Vector a, Vector b, int steps) {
        if (steps <= 0) {
            return;
        }
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vector offset = a.clone().multiply(1.0d - t).add(b.clone().multiply(t));
            world.spawnParticle(Particle.DUST, center.clone().add(offset), 1, 0d, 0d, 0d, 0d,
                    new Particle.DustOptions(LIN_HOLY_WHITE, SIZE_SUB));
        }
    }

    /** 钉墙瞬间的再次爆点（需求 2026-10-08："当敌人被钉到墙面上时，则再次播放爆炸粒子"）。 */
    public static void boneNailPinBurst(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.DUST, at, 24, 0.45d, 0.45d, 0.45d, 0.1d,
                new Particle.DustOptions(LIN_HOLY_WHITE, SIZE_MAIN));
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 16, 0.4d, 0.5d, 0.4d, 0.2d);
    }

    // ───────── 内部几何 ─────────

    /** 水平单位向量（{@code null} 或退化 ⇒ 退回世界 -Z）。 */
    private static Vector horizontalUnit(Vector direction) {
        if (direction == null) {
            return new Vector(0d, 0d, -1d);
        }
        Vector flat = new Vector(direction.getX(), 0d, direction.getZ());
        if (flat.lengthSquared() < 1.0E-9d) {
            return new Vector(0d, 0d, -1d);
        }
        return flat.normalize();
    }

    /** 取一条与 {@code axis} 垂直的单位向量（确定性 ⇒ 可复现）。 */
    private static Vector perpendicularUnit(Vector axis) {
        Vector reference = (Math.abs(axis.getY()) > 0.9d)
                ? new Vector(1d, 0d, 0d)
                : new Vector(0d, 1d, 0d);
        Vector side = axis.clone().crossProduct(reference);
        if (side.lengthSquared() < 1.0E-9d) {
            return new Vector(1d, 0d, 0d);
        }
        return side.normalize();
    }
}
