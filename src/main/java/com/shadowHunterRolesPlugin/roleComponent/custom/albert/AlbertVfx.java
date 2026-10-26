package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/**
 * 「艾尔伯特」的**纯粒子工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求里描述的观感形状算成坐标并 spawn 出去。所有几何都是**纯函数**
 * （给中心 / 半径 ⇒ 一组坐标），组件只负责"什么时候画、画在哪"。
 *
 * <p><b>命名规范</b>（工程口径 {@code <主要形状>+XX角色+XX技能+XX阶段}）：
 * 本类公开方法名一律带「Albert + 技能 + 阶段」三段，便于判断能否复用。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 *
 * <h2>需求对应（四种菱形粒子图 + 主题色）</h2>
 * <ul>
 *   <li><b>高斯无人机</b> ⇒ <b>小菱形白色粒子图</b>{@link #diamondGaussDrone}；</li>
 *   <li><b>哨戒无人机</b> ⇒ <b>蓝色菱形</b>{@link #diamondSentryDrone}；</li>
 *   <li><b>自杀式无人机</b> ⇒ <b>红色菱形</b>{@link #diamondKamikazeDrone}；</li>
 *   <li><b>无人机诱饵</b> ⇒ <b>匍匐于地面的灰色菱形</b>{@link #diamondDecoyGround}；</li>
 *   <li><b>整体用色</b> = 紫 / 黑 / 白 / 红（见下面的 {@code public static final} 常量）。</li>
 * </ul>
 *
 * <h2>★ 「菱形」怎么画（单一实现点）</h2>
 * 用 {@link #diamondOctahedron}(World, Location, Color, double, float, int) —— 六个顶点
 * （±X / ±Y / ±Z）的**八面体**外框，再补上每条棱的中点。
 * <p>为什么不用"平面菱形"：平面菱形只在正对它的角度才像菱形，玩家绕到侧面就成了一条线
 * （无人机是会到处飞的目标，视角不可控）。八面体在**任意视角**下都读得出"菱形 / 晶体"的轮廓，
 * 且点数固定（12 点）⇒ 开销可预测。
 */
public final class AlbertVfx {

    private AlbertVfx() {
    }

    // ───────── 主题色（需求：紫 / 黑 / 白 / 红）─────────

    /** 主色·紫（技能主流、过载协议、诱饵爆炸）。 */
    public static final Color PURPLE = Color.fromRGB(148, 62, 230);

    /** 暗紫（紫的加重端，做渐变 / 描边）。 */
    public static final Color DEEP_PURPLE = Color.fromRGB(88, 26, 148);

    /** 黑（机械 / 抑制器 / 亚音速弹的低调色）。 */
    public static final Color BLACK = Color.fromRGB(22, 20, 28);

    /** 白（高斯无人机本体 —— 需求点明"白色"）。 */
    public static final Color WHITE = Color.fromRGB(242, 246, 255);

    /** 红（自杀式无人机 / 危险 / 过载警戒 —— 需求点明"红色"）。 */
    public static final Color RED = Color.fromRGB(222, 42, 44);

    /** 哨戒无人机的蓝（需求点明"蓝色"）。 */
    public static final Color SENTRY_BLUE = Color.fromRGB(64, 152, 255);

    /** 诱饵的灰（需求点明"灰色"）。 */
    public static final Color DECOY_GRAY = Color.fromRGB(132, 132, 140);

    /** 猎杀目标的标记橙红（与主题红拉开层次，一眼能和伤害红区分）。 */
    public static final Color MARK_AMBER = Color.fromRGB(255, 138, 40);

    // ───────── 几何常量 ─────────

    /** 高斯无人机的菱形半径（格）。小 —— 需求写的是"小菱形"。 */
    public static final double GAUSS_RADIUS = 0.30d;

    /** 哨戒无人机的菱形半径（格）：比高斯略大，因为它是"常驻地标"。 */
    public static final double SENTRY_RADIUS = 0.42d;

    /** 自杀式无人机的菱形半径（格）。 */
    public static final double KAMIKAZE_RADIUS = 0.34d;

    /** 诱饵的菱形半径（格）：铺在地面上，所以要宽而扁。 */
    public static final double DECOY_RADIUS = 0.75d;

    // ───────── 菱形（八面体外框）─────────

    /**
     * ★ **菱形外框**（八面体六顶点 + 棱中点）—— 任意视角都读得出菱形轮廓。
     *
     * <p>八个三角面在视觉上合并成"上下两个尖、中间一个方"的钻石形，
     * 因此从侧面看是菱形、从上方看是菱形、从斜角看仍是晶体。
     *
     * @param center 中心
     * @param color  颜色
     * @param radius 半径（格，六个顶点到中心的距离）
     * @param size   粒子尺寸
     * @param seed   相位种子（让各架无人机的抖动不同，避免整齐划一）
     */
    public static void diamondOctahedron(World world, Location center, Color color,
                                         double radius, float size, long seed) {
        if (world == null || center == null || color == null || radius <= 0d) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        // 六顶点
        double[][] vertices = {
                {radius, 0d, 0d}, {-radius, 0d, 0d},
                {0d, radius, 0d}, {0d, -radius, 0d},
                {0d, 0d, radius}, {0d, 0d, -radius}
        };
        for (double[] v : vertices) {
            world.spawnParticle(Particle.DUST, center.clone().add(v[0], v[1], v[2]),
                    1, 0d, 0d, 0d, 0d, dust);
        }
        // 每一对相邻顶点（= 八面体的棱）的中点 ⇒ 补出"棱"的读感
        for (int i = 0; i < vertices.length; i++) {
            for (int j = i + 1; j < vertices.length; j++) {
                double[] a = vertices[i];
                double[] b = vertices[j];
                // 相对的两个顶点不连棱（i 与 i^1 是互斥轴）
                if (a[0] == -b[0] && a[1] == -b[1] && a[2] == -b[2]) {
                    continue;
                }
                world.spawnParticle(Particle.DUST,
                        center.clone().add((a[0] + b[0]) * 0.5d, (a[1] + b[1]) * 0.5d, (a[2] + b[2]) * 0.5d),
                        1, 0d, 0d, 0d, 0d, dust);
            }
        }
        // ★ 点一个极小的"芯"（黑）—— 需求点明黑色参与用色，也让它不像纯描边的空架子
        world.spawnParticle(Particle.DUST, center, 1, 0d, 0d, 0d, 0d,
                new Particle.DustOptions(BLACK, Math.max(0.35f, size * 0.6f)));
    }

    /** **高斯无人机的白色小菱形**（需求原话："高斯无人机用小菱形白色粒子图替代"）。 */
    public static void diamondGaussDrone(World world, Location at) {
        diamondOctahedron(world, at, WHITE, GAUSS_RADIUS, 0.75f, 0L);
        // 尾迹感：一点点极淡的白烟，让它"在飞"
        if (world != null && at != null) {
            world.spawnParticle(Particle.END_ROD, at, 1, 0.02d, 0.02d, 0.02d, 0d);
        }
    }

    /** **哨戒无人机的蓝色菱形**（需求原话："哨戒无人机用蓝色菱形粒子图替代"）。 */
    public static void diamondSentryDrone(World world, Location at) {
        diamondOctahedron(world, at, SENTRY_BLUE, SENTRY_RADIUS, 0.9f, 0L);
    }

    /** **自杀式无人机的红色菱形**（需求原话："自杀式无人机则用红色菱形粒子代替"）。 */
    public static void diamondKamikazeDrone(World world, Location at) {
        diamondOctahedron(world, at, RED, KAMIKAZE_RADIUS, 0.85f, 0L);
        if (world != null && at != null) {
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 1, 0.03d, 0.03d, 0.03d, 0d);
        }
    }

    /**
     * **无人机诱饵的灰色菱形，匍匐于地面**（需求原话："无人机诱饵则是匍匐于地面的灰色菱形粒子"）。
     *
     * <p>与其它三个的差别 = <b>压扁并贴地</b>：竖直半径只有水平的 {@value #DECOY_FLATTEN} 倍，
     * 且整体下沉 {@code 0.05} 格 ⇒ 读起来是"摊在地上的一枚装置"，不是"浮在空中的无人机"。
     */
    public static void diamondDecoyGround(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location flat = at.clone().subtract(0d, 0.05d, 0d);
        Particle.DustOptions dust = new Particle.DustOptions(DECOY_GRAY, 0.8f);
        // 水平四个顶点（宽）+ 竖直两个顶点（扁）
        double h = DECOY_RADIUS;
        double v = DECOY_RADIUS * DECOY_FLATTEN;
        double[][] pts = {
                {h, 0d, 0d}, {-h, 0d, 0d}, {0d, 0d, h}, {0d, 0d, -h},
                {0d, v, 0d}, {0d, -v, 0d},
                // 棱中点（只在水平环内补，压扁后竖直棱很短，补了反而糊）
                {h * 0.5d, 0d, h * 0.5d}, {-h * 0.5d, 0d, h * 0.5d},
                {h * 0.5d, 0d, -h * 0.5d}, {-h * 0.5d, 0d, -h * 0.5d}
        };
        for (double[] p : pts) {
            world.spawnParticle(Particle.DUST, flat.clone().add(p[0], p[1], p[2]), 1, 0d, 0d, 0d, 0d, dust);
        }
    }

    /** 诱饵菱形的压扁系数（竖直半径 = 水平半径 × 它）。 */
    public static final double DECOY_FLATTEN = 0.22d;

    // ───────── 跟随位（待机分布：左右角 + 左右肩）─────────

    /**
     * ★ **视角空间偏移**（玩家视线的前 / 右 / 上三轴）。
     *
     * <p>Minecraft 没有屏幕空间坐标系 ⇒ 用"视线的前 / 右 / 上"三轴表达
     * "视角右上角"这种说法（工程既有先例：{@code MatinaRageVfx.viewOffset}）。
     *
     * @param player  观察者
     * @param right   沿"右"方向的偏移（格，负 = 左）
     * @param up      沿"上"方向的偏移（格，负 = 下）
     * @param forward 沿"前"方向的偏移（格）
     * @return 世界坐标；{@code player} 为 null / 世界为 null ⇒ {@code null}
     */
    public static Location viewOffset(Player player, double right, double up, double forward) {
        if (player == null || player.getWorld() == null) {
            return null;
        }
        Location eye = player.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        // 右 = look × worldUp（右手系）
        Vector rightAxis = look.clone().crossProduct(new Vector(0d, 1d, 0d));
        if (rightAxis.lengthSquared() < 1.0E-6d) {
            // 视线几乎垂直 ⇒ 右轴退化，用身体朝向兜底
            Vector body = player.getLocation().getDirection();
            rightAxis = body.clone().crossProduct(new Vector(0d, 1d, 0d));
            if (rightAxis.lengthSquared() < 1.0E-6d) {
                rightAxis = new Vector(1d, 0d, 0d);
            }
        }
        rightAxis.normalize();
        Vector upAxis = rightAxis.clone().crossProduct(look).normalize();

        return eye.clone()
                .add(rightAxis.multiply(right))
                .add(upAxis.multiply(up))
                .add(look.multiply(forward));
    }

    /**
     * ★ **四架高斯无人机的待机位**（需求原话："待机时会平等地分布在艾尔伯特视角左右角，和左右肩"）。
     *
     * <p>槽位口径（{@code index} = 0..3）：
     * <ol start="0">
     *   <li>视角<b>右上角</b>；</li>
     *   <li>视角<b>左上角</b>；</li>
     *   <li><b>右肩</b>；</li>
     *   <li><b>左肩</b>。</li>
     * </ol>
     * "平等地分布" = 四个位两两对称，谁先部署谁先占（先占角、再占肩）。
     *
     * @param player 主人
     * @param index  槽位下标（自动取模 0..3）
     */
    public static Location dockSlot(Player player, int index) {
        int i = Math.floorMod(index, DOCK_RIGHT.length);
        return viewOffset(player, DOCK_RIGHT[i], DOCK_UP[i], DOCK_FORWARD[i]);
    }

    /** 待机位的"右"分量（格）：角比肩靠外。 */
    private static final double[] DOCK_RIGHT = {0.92d, -0.92d, 0.62d, -0.62d};

    /** 待机位的"上"分量（格）：角在视线上方，肩在视线**下方**（肩本来就低于眼睛）。 */
    private static final double[] DOCK_UP = {0.88d, 0.88d, -0.34d, -0.34d};

    /**
     * 待机位的"前"分量（格）：角在视线前方，**肩退到视线之后**。
     *
     * <p>★ 2026-10-06 调整：原来两肩在 {@code +0.55}（= 眼睛前方 0.55 格），
     * 距离太近 ⇒ 玩家第一人称下看到两团**贴脸**的白菱形，把屏幕两侧糊住。
     * 现在退到 {@code -0.45}（眼睛后方），只在转头 / 第三视角时才看得见 ——
     * 既保住了"左右肩各一架"的编队读感，又不占视野。
     */
    private static final double[] DOCK_FORWARD = {1.15d, 1.15d, -0.45d, -0.45d};

    /** 待机位数量（= 高斯无人机上限）。 */
    public static final int DOCK_SLOTS = 4;

    // ───────── 各技能 / 状态的观感 ─────────

    /** **主动防御触发**：白 + 紫的护盾环 + 黑点抑制感（空中炸开一小圈）。 */
    public static void shieldBurstAlbertOverResponse(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at, 1.05d, 14, WHITE, 0.85f);
        ring(world, at, 1.35d, 12, PURPLE, 0.8f);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 8, 0.4d, 0.4d, 0.4d, 0.05d);
    }

    /** **弹药射击**：亚音速穿甲弹的黑色弹道 + 一点白（低可见度，符合"亚音速"）。 */
    public static void tracerAlbertGunbladeShot(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-6d) {
            return;
        }
        Vector unit = delta.clone().multiply(1d / length);
        int points = (int) Math.max(2d, Math.min(24d, length * 1.6d));
        for (int i = 0; i <= points; i++) {
            Location at = from.clone().add(unit.clone().multiply(length * i / points));
            world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                    new Particle.DustOptions(BLACK, 0.6f));
            if (i % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, at, 1, 0d, 0d, 0d, 0d);
            }
        }
    }

    /** **近战命中**：白 + 紫的短线火花（铳剑的机械咬合感）。 */
    public static void hitSparkAlbertGunbladeMelee(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.CRIT, at, 10, 0.28d, 0.35d, 0.28d, 0.08d);
        world.spawnParticle(Particle.DUST, at, 8, 0.3d, 0.35d, 0.3d, 0d,
                new Particle.DustOptions(PURPLE, 0.8f));
    }

    /** **猎杀标记**（右键命中 / 技能2 区域标记）：橙红菱形 + 灵魂火。 */
    public static void markAlbertHuntTarget(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        diamondOctahedron(world, at, MARK_AMBER, 0.6d, 0.9f, 0L);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at, 3, 0.2d, 0.25d, 0.2d, 0.005d);
    }

    /** **技能1 高斯装配**：围绕自身的紫色部署环 + 白菱形脉冲。 */
    public static void deployAlbertAssemble(World world, Location center, double phase) {
        if (world == null || center == null) {
            return;
        }
        ring(world, center.clone().add(0d, 1.0d, 0d), 1.6d, 20, PURPLE, 0.85f);
        ring(world, center.clone().add(0d, 2.0d, 0d), 1.2d, 14, WHITE, 0.8f);
        for (int i = 0; i < 4; i++) {
            double angle = phase + i * Math.PI / 2d;
            Location at = center.clone().add(Math.cos(angle) * 1.4d, 1.4d, Math.sin(angle) * 1.4d);
            diamondOctahedron(world, at, WHITE, GAUSS_RADIUS, 0.75f, 0L);
        }
    }

    /** **哨戒无人机常驻**：蓝菱形 + 地面蓝环（每刻画，按片交错）。 */
    public static void sentryAlbertAssemble(World world, Location at, double phase) {
        if (world == null || at == null) {
            return;
        }
        diamondSentryDrone(world, at.clone().add(0d, 1.2d, 0d));
        // 地面环：只画 1/4 的点（按片交错，靠粒子存活期看起来连续）
        ringStaggered(world, at, 10d, 40, SENTRY_BLUE, 0.7f, (int) phase, 4);
    }

    /** **哨戒报警**（有敌人进入哨戒范围）：蓝色→橙红的急促闪烁环。 */
    public static void alarmAlbertSentry(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at.clone().add(0d, 1.2d, 0d), 0.9d, 12, MARK_AMBER, 1.0f);
        world.spawnParticle(Particle.ENCHANTED_HIT, at.clone().add(0d, 1.2d, 0d), 8, 0.3d, 0.3d, 0.3d, 0.05d);
    }

    /** **技能2 猎杀指令**：信号弹升空 + 落点大环。 */
    public static void signalAlbertHuntOrder(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length > 1.0E-6d) {
            Vector unit = delta.clone().multiply(1d / length);
            for (double t = 0d; t <= length; t += 0.5d) {
                Location at = from.clone().add(unit.clone().multiply(t));
                // 抛物线的视觉近似：中段抬高
                double arc = Math.sin(Math.PI * (t / length)) * 2.2d;
                world.spawnParticle(Particle.DUST, at.clone().add(0d, arc, 0d), 1, 0d, 0d, 0d, 0d,
                        new Particle.DustOptions(MARK_AMBER, 0.9f));
            }
        }
        ring(world, to, 10d, 48, MARK_AMBER, 0.85f);
        ring(world, to, 10d, 24, RED, 0.7f);
    }

    /**
     * ★★ **技能2 目标区域的持续高亮**（窗口期内每刻画）。
     *
     * <p>做法 = <b>地面主环 + 内环 + 四根角柱 + 一圈抬升标记</b>，四者都用
     * {@link #ringStaggered} 的"按片交错"写法 ⇒ 每刻只画一小撮点，
     * 靠粒子存活期看起来是**常亮**的（10 秒窗口 × 每秒全画 = 上万颗，必卡）。
     *
     * @param center 区域中心
     * @param radius 区域半径（格）
     * @param phase  相位（每刻推进 ⇒ 环上的点会缓缓流动）
     */
    public static void highlightAlbertHuntOrder(World world, Location center, double radius, double phase) {
        if (world == null || center == null || radius <= 0d) {
            return;
        }
        int tick = (int) phase;
        // 地面主环（橙红）+ 内环（紫，反向流动）
        ringStaggered(world, center, radius, 72, MARK_AMBER, 0.9f, tick, 6);
        ringStaggered(world, center, radius * 0.62d, 48, PURPLE, 0.8f, -tick, 5);
        // 四根角柱：半径处每 90° 一根，3 格高的竖直虚线
        for (int corner = 0; corner < 4; corner++) {
            double angle = Math.PI / 2d * corner;
            double cx = Math.cos(angle) * radius;
            double cz = Math.sin(angle) * radius;
            for (double h = 0d; h <= 3d; h += 0.75d) {
                Location at = center.clone().add(cx, h, cz);
                world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                        new Particle.DustOptions(h <= 0.1d ? MARK_AMBER : RED, 0.85f));
            }
        }
        // 抬升标记：环上每隔一段升一颗"信号弹残余"
        if (tick % 4 == 0) {
            for (int i = 0; i < 4; i++) {
                double angle = phase * 0.4d + i * Math.PI / 2d;
                Location at = center.clone().add(Math.cos(angle) * radius * 0.8d, 1.2d,
                        Math.sin(angle) * radius * 0.8d);
                world.spawnParticle(Particle.END_ROD, at, 1, 0.02d, 0.02d, 0.02d, 0d);
            }
        }
    }

    /** **技能3 全功率推进**：起点的紫色冲击 + 路径上的残影。 */
    public static void thrustAlbertOverdrive(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        ring(world, from.clone().add(0d, 1.0d, 0d), 1.1d, 14, PURPLE, 0.95f);
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length > 1.0E-6d) {
            Vector unit = delta.clone().multiply(1d / length);
            for (double t = 0d; t <= length; t += 0.6d) {
                Location at = from.clone().add(unit.clone().multiply(t));
                world.spawnParticle(Particle.DUST, at.clone().add(0d, 1.0d, 0d), 1, 0.06d, 0.06d, 0.06d, 0d,
                        new Particle.DustOptions(t % 1.2d < 0.6d ? PURPLE : WHITE, 0.85f));
            }
        }
    }

    /** **诱饵爆炸**：灰→紫→红的扩散爆点 + 缓慢效果环。 */
    public static void explodeAlbertDecoy(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.DUST, at, 30, 0.5d, 0.5d, 0.5d, 0d,
                new Particle.DustOptions(DECOY_GRAY, 1.1f));
        ring(world, at, 4d, 32, PURPLE, 0.9f);
        ring(world, at, 4d, 16, RED, 0.8f);
    }

    /** **技能4 蓄力**：地面红色收缩环（越收越紧）。 */
    public static void chargeAlbertOverload(World world, Location at, double progress) {
        if (world == null || at == null) {
            return;
        }
        double radius = 2.2d * (1d - Math.max(0d, Math.min(1d, progress)) * 0.7d);
        ring(world, at, radius, 24, RED, 0.9f);
        ring(world, at.clone().add(0d, 0.6d, 0d), radius * 0.7d, 16, PURPLE, 0.8f);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0d, 0.3d, 0d), 3,
                0.5d, 0.15d, 0.5d, 0.01d);
    }

    /** **自杀式无人机爆炸**：红紫双环 + 灵魂爆。 */
    public static void explodeAlbertKamikaze(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION, at, 1, 0d, 0d, 0d, 0d);
        ring(world, at, 7d, 56, RED, 1.0f);
        ring(world, at, 7d, 28, PURPLE, 0.85f);
        world.spawnParticle(Particle.SOUL, at, 20, 1.2d, 0.8d, 1.2d, 0.04d);
    }

    /** **无人机被击落 / 主动防御消耗**：白菱形碎成 6 点（"机毁"的克制表达）。 */
    public static void shatterAlbertDrone(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 6, 0.35d, 0.35d, 0.35d, 0d,
                new Particle.DustOptions(WHITE, 0.9f));
        world.spawnParticle(Particle.LARGE_SMOKE, at, 3, 0.2d, 0.2d, 0.2d, 0.01d);
    }

    /** **过载结束**：全场紫红沉降（"火焰烧大一些"的收尾）。 */
    public static void overloadAlbertAftermath(World world, Location at, double phase) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at, 6d, 36, RED, 0.9f);
        ring(world, at, 9d, 48, PURPLE, 0.75f);
    }

    // ───────── 基础几何（环）─────────

    /** 在水平面画一个圆环（{@code points} 个点，每点一颗 DUST）。 */
    public static void ring(World world, Location center, double radius, int points,
                            Color color, float size) {
        if (world == null || center == null || color == null || radius <= 0d || points <= 0) {
            return;
        }
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        for (int i = 0; i < points; i++) {
            double angle = 2d * Math.PI * i / points;
            world.spawnParticle(Particle.DUST,
                    center.clone().add(Math.cos(angle) * radius, 0d, Math.sin(angle) * radius),
                    1, 0d, 0d, 0d, 0d, dust);
        }
    }

    /**
     * **按片交错的环**：只画 {@code 1/stride} 的点，相位由 {@code phase} 决定。
     *
     * <p>★ 口径（工程既有实测结论）：一个 {@code points=40} 的环每刻全画 = 每秒 800 颗粒子，
     * 挂在一个常驻地标（哨戒无人机，存续无限）上会明显吃性能。
     * 靠粒子存活期"看上去连续" ⇒ 每刻只画 1/stride，观感几乎无损。
     */
    public static void ringStaggered(World world, Location center, double radius, int points,
                                     Color color, float size, int phase, int stride) {
        if (world == null || center == null || color == null || radius <= 0d || points <= 0) {
            return;
        }
        int step = Math.max(1, stride);
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        int start = Math.floorMod(phase, points);
        for (int i = start; i < start + points; i += step) {
            double angle = 2d * Math.PI * Math.floorMod(i, points) / points;
            world.spawnParticle(Particle.DUST,
                    center.clone().add(Math.cos(angle) * radius, 0d, Math.sin(angle) * radius),
                    1, 0d, 0d, 0d, 0d, dust);
        }
    }
}
