package com.shadowHunterRolesPlugin.roleComponent.custom.matina;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/**
 * 「狂躁牧师·马提娜」的**纯粒子工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>本类只做一件事：把需求里那些"看着华丽"的粒子形状算成坐标并 spawn 出去 ——
 * 所有几何都是**纯函数**（给相位 / 半径 / 点数 ⇒ 一组 {@link Location}），
 * 因此组件侧只负责"什么时候画、画在哪"，形状细节全在这里。
 *
 * <p><b>为什么独立成类</b>：工程约定"组件需要专属类支持时，专属类放到与组件同级的文件夹下"
 * （照 {@code custom/sinThorn/SinThornVfx} 的既有形态）⇒ 粒子几何不进组件、更不进框架。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 *
 * <p>★ <b>像素画魔法阵的粒子种类</b>（{@link #pixelCircle}）：一律用**白色末地烛**
 * （{@link Particle#END_ROD}），位置取自外部「pixelart 粒子画」数据包，**颜色不取画里的 RGB**。
 */
public final class MatinaRageVfx {

    private MatinaRageVfx() {
    }

    /** 狂暴层数的环绕粒子颜色（红石红）。 */
    public static final Color RAGE_RED = Color.fromRGB(200, 30, 30);

    /** 「神罚」魔法阵的白色（带一点冷调，在夜晚也清楚）。 */
    public static final Color HOLY_WHITE = Color.fromRGB(240, 245, 255);

    // ───────── 通用：一个点上的单颗粒子 ─────────

    /** 在某一格放一颗**红石粉**（{@code DUST}）粒子。 */
    public static void dust(World world, Location at, Color color, float size) {
        if (world == null || at == null || color == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d, new Particle.DustOptions(color, size));
    }

    /**
     * **狂暴层数的环绕粒子**（需求：每两个层数多一个环绕自身的红石粒子，从脚底逐个叠高）。
     *
     * @param layers  要画的**粒子数**（&le; 0 ⇒ 不画）——由调用方把层数折算好
     *                （{@code kuang / 2}），本方法只认"画几颗"
     * @param phase   当前相位（弧度；调用方每帧推进 ⇒ 整圈一起转）
     * @param radius  环绕半径（格）
     * @param stepY   每颗抬高多少格（"逐个叠高"；第 0 颗贴着 {@code center}）
     */
    public static void rageOrbit(World world, Location center, int layers, double phase,
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
            dust(world, at, RAGE_RED, 0.9f);
        }
    }

    // ───────── 环 / 圆 / 魔法阵 ─────────

    /** 水平**圆环**（{@code points} 个点；半径 {@code radius}）。 */
    public static void ring(World world, Location center, double radius, int points, Particle particle) {
        if (world == null || center == null || particle == null || points <= 0 || radius <= 0d) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2d * i / points;
            world.spawnParticle(particle, center.clone().add(
                    Math.cos(angle) * radius, 0d, Math.sin(angle) * radius), 1, 0d, 0d, 0d, 0d);
        }
    }

    /** 水平**红石圆环**（半径与相位可调 ⇒ 连续几帧即是"旋转的环"）。 */
    public static void dustRing(World world, Location center, double radius, int points,
                                double phase, Color color, float size) {
        if (world == null || center == null || points <= 0 || radius <= 0d) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double angle = phase + Math.PI * 2d * i / points;
            dust(world, center.clone().add(
                    Math.cos(angle) * radius, 0d, Math.sin(angle) * radius), color, size);
        }
    }

    /**
     * **大型魔法阵**（需求：整个技能变成一个大型魔法阵，旋转变换，魔法阵边缘就是技能边缘）。
     *
     * <p>阵面由四层同心结构拼出"华丽"：
     * <ol>
     *   <li><b>外缘</b>（= 技能半径本身）—— 白色 {@code END_ROD} 点阵，标出"边缘就是技能边缘"；</li>
     *   <li><b>次外环</b>（0.86r）与<b>内环</b>（0.34r）—— 白色红石自转环；</li>
     *   <li><b>符线</b>—— 两条互相垂直的直径 + 两条对角线，从中心拉到外缘；</li>
     *   <li><b>节点</b>—— 三圈上的白色爆点（{@code END_ROD}），渲染"阵眼"。</li>
     * </ol>
     *
     * @param phase       自转相位（弧度；调用方每帧推进 ⇒ "旋转变换"）
     * @param pointBudget 点阵预算（决定细腻度；内部按预算分配各圈点数）
     */
    public static void magicCircle(World world, Location center, double radius,
                                   double phase, int pointBudget) {
        if (world == null || center == null || radius <= 0d) {
            return;
        }
        int budget = Math.max(48, pointBudget);

        // ① 外缘（白色的"技能边缘"）
        int outerPoints = budget;
        for (int i = 0; i < outerPoints; i++) {
            double angle = Math.PI * 2d * i / outerPoints;
            world.spawnParticle(Particle.END_ROD, center.clone().add(
                    Math.cos(angle) * radius, 0d, Math.sin(angle) * radius), 1, 0d, 0d, 0d, 0d);
        }

        // ② 次外环 / 内环（白色红石，自转）
        dustRing(world, center, radius * 0.86d, Math.max(24, budget / 2), phase, HOLY_WHITE, 1.1f);
        dustRing(world, center, radius * 0.55d, Math.max(16, budget / 3), -phase * 1.6d, HOLY_WHITE, 0.9f);
        dustRing(world, center, radius * 0.34d, Math.max(12, budget / 4), phase * 2.2d, HOLY_WHITE, 0.8f);

        // ③ 符线：两条直径 + 两条对角线（从中心拉到外缘）
        int linePoints = Math.max(8, budget / 6);
        for (int line = 0; line < 4; line++) {
            double lineAngle = phase * 0.5d + line * (Math.PI / 4d);
            double dx = Math.cos(lineAngle);
            double dz = Math.sin(lineAngle);
            for (int step = 1; step <= linePoints; step++) {
                double t = (double) step / linePoints;
                world.spawnParticle(Particle.END_ROD, center.clone().add(
                        dx * radius * t, 0d, dz * radius * t), 1, 0d, 0d, 0d, 0d);
                world.spawnParticle(Particle.END_ROD, center.clone().add(
                        -dx * radius * t, 0d, -dz * radius * t), 1, 0d, 0d, 0d, 0d);
            }
        }

        // ④ 节点：三圈上的白色爆点（阵眼）
        nodeRing(world, center, radius, phase, 8);
        nodeRing(world, center, radius * 0.86d, -phase * 1.6d, 6);
        nodeRing(world, center, radius * 0.34d, phase * 2.2d, 4);
    }

    /** 某一圈上等分的**爆点节点**（每个节点一颗 {@code END_ROD} + 一点微小抖动）。 */
    private static void nodeRing(World world, Location center, double radius, double phase, int nodes) {
        if (radius <= 0d || nodes <= 0) {
            return;
        }
        for (int i = 0; i < nodes; i++) {
            double angle = phase + Math.PI * 2d * i / nodes;
            world.spawnParticle(Particle.END_ROD, center.clone().add(
                    Math.cos(angle) * radius, 0.05d, Math.sin(angle) * radius), 1, 0d, 0d, 0d, 0.01d);
        }
    }

    /**
     * **向上旋转的白色螺旋**（需求：自己周身环绕白色旋转向上 10 格粒子）。
     *
     * @param height    螺旋总高度（格；"10 格"由此参数给出）
     * @param radius    基础半径（格）
     * @param phase     相位（每帧推进 ⇒ 越转越高的观感）
     * @param points    采样点（每点一颗粒子）
     */
    public static void holySpiralUp(World world, Location base, double height, double radius,
                                    double phase, int points) {
        if (world == null || base == null || points <= 0 || height <= 0d) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = phase + t * Math.PI * 4d;      // 两圈
            double y = t * height;
            double r = radius * (1d - 0.45d * t);          // 越高越收
            Location at = base.clone().add(Math.cos(angle) * r, y, Math.sin(angle) * r);
            world.spawnParticle(Particle.END_ROD, at, 1, 0d, 0d, 0d, 0d);
            if (i % 3 == 0) {
                dust(world, at, HOLY_WHITE, 0.7f);
            }
        }
    }

    // ───────── 治疗 / 敌意 / 无人机 ─────────

    /**
     * **短暂的竖直白色光柱**（需求：引导成功后，内部所有受到技能影响的人都会冒出竖直白色光柱粒子）。
     *
     * <p>形态 = 一列**严格竖直**的白色光柱（每点一颗，不抖动 ⇒ 柱体不散）。
     *
     * <p>★ **"短暂"的落点**：柱体的主粒子用 {@code DUST}（红石系粒子，存活约 0.5–1 秒即散）
     * 而不是 {@code END_ROD}（原版存活最久的白粒子之一，会在空中滞留十几秒）⇒ 观感是
     * <b>"一闪即散的光柱"</b>，配合每 0.5 秒一次的伤害跳刷新，读起来就是一次次脉冲，
     * 而不是一根长期杵在那里的实体柱子。
     * <p>只在柱顶附近点缀极少数 {@code END_ROD}（占比 1/6）保留"白得发亮"的尖端，
     * 其余全部走短命粒子。
     *
     * @param base   柱底（一般取目标脚底）
     * @param height 柱高（格）
     * @param points 采样点数（每点一颗粒子）
     */
    public static void whitePillar(World world, Location base, double height, int points) {
        if (world == null || base == null || points <= 0 || height <= 0d) {
            return;
        }
        for (int i = 0; i <= points; i++) {
            double t = (double) i / points;
            Location at = base.clone().add(0d, t * height, 0d);
            //主粒子 = 短命的白色红石；只有靠近柱顶的那几点才用长命的 END_ROD 提亮
            if (t >= 0.8d && i % 3 == 0) {
                world.spawnParticle(Particle.END_ROD, at, 1, 0d, 0d, 0d, 0d);
            }
            dust(world, at, HOLY_WHITE, 0.7f);
        }
    }

    /** **爱心粒子**（治疗落在谁身上就在谁头上冒；也用来当无人机的"持续生成的爱心"）。 */
    public static void hearts(World world, Location at, int count, double spread) {
        if (world == null || at == null || count <= 0) {
            return;
        }
        world.spawnParticle(Particle.HEART, at, count, spread, spread * 0.5d, spread, 0d);
    }

    /**
     * **骨粉催熟特效**（需求：被治疗对象身上冒出爱心粒子和骨粉催熟特效）。
     * <p>粒子形态 = 原版"骨粉催熟"的 {@code HAPPY_VILLAGER}（绿色小十字）。
     */
    public static void boneMeal(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.HAPPY_VILLAGER, at, 14, 0.45d, 0.6d, 0.45d, 0d);
    }

    /** **村民愤怒粒子**（需求：被伤害对象身上会冒出村民愤怒粒子）。 */
    public static void villagerAngry(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.ANGRY_VILLAGER, at, 10, 0.45d, 0.6d, 0.45d, 0d);
    }

    /** **旋转上升的紫色粒子**（需求：被伤害对象身上的第二条特效）。 */
    public static void purpleRising(World world, Location base, double height, double phase, int points) {
        if (world == null || base == null || points <= 0) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double angle = phase + t * Math.PI * 3d;
            double y = t * height;
            Location at = base.clone().add(Math.cos(angle) * 0.45d, y, Math.sin(angle) * 0.45d);
            world.spawnParticle(Particle.WITCH, at, 1, 0d, 0d, 0d, 0d);
        }
    }

    /**
     * **无人机本体的粒子**（需求：无人机用持续生成的爱心替代）。
     *
     * @param core   无人机当前位置
     * @param mode   模式标记：{@code true} = 派出（红石粉），{@code false} = 召回（白糖）
     * @param phase  相位（用于模式标记的小幅环绕）
     */
    public static void drone(World world, Location core, boolean mode, double phase) {
        if (world == null || core == null) {
            return;
        }
        //本体 = 持续生成的爱心
        world.spawnParticle(Particle.HEART, core, 2, 0.15d, 0.15d, 0.15d, 0d);
        //模式标记：红石粉 = 派出，白糖 = 召回
        if (mode) {
            dust(world, core.clone().add(0d, 0.35d, 0d), RAGE_RED, 1.0f);
        } else {
            world.spawnParticle(Particle.ITEM, core, 2, 0.12d, 0.12d, 0.12d, 0d,
                    new ItemStack(org.bukkit.Material.SUGAR));
        }
        //一点尾迹，让飞行有方向感
        world.spawnParticle(Particle.ELECTRIC_SPARK, core.clone().add(
                Math.cos(phase) * 0.25d, -0.15d, Math.sin(phase) * 0.25d), 1, 0d, 0d, 0d, 0d);
    }

    // ───────── 几何小工具（给无人机用） ─────────

    /**
     * **把"相对玩家视角"的偏移转成世界坐标**：右向量 = 视线 × 世界上方。
     *
     * <p>需求里的"角色视角右上角"在世界空间里只能这样表达：以<b>视线方向</b>为前方算出右手方向，
     * 再把无人机放在"右上、稍前"的位置 ⇒ 玩家转头时它跟着转（观感上贴着视角）。
     *
     * @param player  参照玩家
     * @param right   向右偏移（格）
     * @param up      向上偏移（格）
     * @param forward 向前偏移（格；负数 = 稍后）
     */
    public static Location viewOffset(Player player, double right, double up, double forward) {
        if (player == null) {
            return null;
        }
        Vector look = player.getEyeLocation().getDirection();
        Vector flat = new Vector(look.getX(), 0d, look.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            //视线几乎垂直 ⇒ 用水平朝向兜底（否则叉积退化）
            flat = player.getLocation().getDirection();
            flat = new Vector(flat.getX(), 0d, flat.getZ());
        }
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        flat.normalize();
        Vector rightVector = flat.clone().crossProduct(new Vector(0d, 1d, 0d));
        if (rightVector.lengthSquared() < 1.0E-6d) {
            rightVector = new Vector(1d, 0d, 0d);
        }
        rightVector.normalize();
        return player.getEyeLocation().clone()
                .add(rightVector.multiply(right))
                .add(new Vector(0d, up, 0d))
                .add(flat.multiply(forward));
    }

    /** 点到点的**单位方向**（零长度 ⇒ 全零向量，调用方自行判）。 */
    public static Vector direction(Location from, Location to) {
        if (from == null || to == null) {
            return new Vector();
        }
        return to.toVector().subtract(from.toVector());
    }

    // ───────── 像素画魔法阵（数据源：外部「pixelart 粒子画」数据包）─────────

    /**
     * **一张已归一化的像素画**（纯数据容器；本类不缓存任何东西，由调用方持有并复用）。
     *
     * <p>{@code localX/localY} 是**归一化局部坐标**（−0.5 ~ +0.5，两轴同除以最大边
     * ⇒ 长宽比与源画一致）；{@code colors} 与它们一一对应。
     * <p>为什么做成 record 由调用方持有：本类的契约是"纯几何、零状态"
     * （见类注释的边界申报）⇒ 资源读取与缓存归组件，本类只负责把坐标算出来并 spawn。
     *
     * <p>★ {@code colors} 目前**只用于保留源画数据**：{@link #pixelCircle} 一律走白色末地烛，
     * 不读颜色（需求指定）。留着它是因为资源格式本来就带 RGB 列，且将来想切回红石画法时
     * 无需重新解析。
     */
    public record PixelArt(float[] localX, float[] localY, Color[] colors) {

        /** 点数（三数组等长；空画回 0）。 */
        public int size() {
            return localX == null ? 0 : localX.length;
        }
    }

    /**
     * 像素画每颗点的 **dust 尺寸**（源数据包用的是 0.2）。
     *
     * <p>★ <b>现已不使用</b>：那张画统一改用**白色末地烛**（{@link Particle#END_ROD}）渲染，
     * 而末地烛**没有尺寸参数**（它不是红石系粒子）。常量保留是为了不破坏既有读口与文档，
     * 也让"想切回红石画法"时有个现成的默认值。
     *
     * <p>历史：源画按 0.2 格间距密排，要把它**铺满整个技能直径**（放大 ≈2 倍）
     * ⇒ 点间距变宽，沿用 0.2 会让画面显得稀疏发暗 ⇒ 曾取 0.5 补偿。
     */
    public static final float PIXEL_DUST_SIZE = 0.5f;

    /** 像素画各点的**末地烛爆点抖动**（末地烛自带一点速度 ⇒ 0 才是一颗静止的亮点）。 */
    private static final double PIXEL_END_ROD_SPREAD = 0d;

    /**
     * **像素画魔法阵**：把一张像素画**平铺在脚下**，替换原来的几何魔法阵。
     *
     * <h2>粒子种类（★ 2026-10-03 起）</h2>
     * 各点一律用**白色末地烛**（{@link Particle#END_ROD}）—— 这是需求方明确指定的：
     * 位置取自外部粒子画，颜色不取画里的 RGB，而是"全部白色末地烛"。
     * ⇒ 参数里的 {@code art} 只贡献**坐标**（{@code localX/localY}）与**点数**；
     * {@code colors} 与 {@code dustSize} 在此路径下**不参与绘制**（保留形参是为了
     * 不破坏既有签名与 {@code PixelArt} 的数据完整性，也便于将来切回红石画法）。
     *
     * <p>为什么这样换反而更好看：末地烛是原版里"亮白、细长、拖尾"的粒子，
     * 存活期比 dust 长 ⇒ 按片绘制时**残影叠得更自然**，整幅画看上去更"发光"。
     *
     * <h2>铺法与朝向</h2>
     * <ul>
     *   <li>画的局部 {@code x} 轴 → 世界 {@code +x}；画的局部 {@code y} 轴（源里 +y 朝上）
     *       → 世界 <b>−z</b>（俯视时"上"= 北）⇒ 从上方看是正立的；</li>
     *   <li>再整体绕 Y 轴旋转 {@code phase} 弧度 ⇒ 与几何魔法阵一样**自转**；</li>
     *   <li>{@code diameter} 决定铺多大：局部坐标最大边 1.0 映射到 {@code diameter} 格。</li>
     * </ul>
     *
     * <h2>★ 为什么按"片"画（sliceIndex / sliceCount）</h2>
     * 这张画有一千多个点，**每刻全画 ≈ 2 万粒子/秒**，会把客户端刷爆。
     * 而粒子有存活期 ⇒ 只要**每隔几刻把同一个点重画一次**，玩家看到的就是连续的画。
     * 因此本方法每刻只画"第 {@code sliceIndex} 片"（共 {@code sliceCount} 片），
     * 由调用方逐刻递增下标 ⇒ 每刻的粒子数与"画全一遍所需刻数"成反比。
     * <p>例：{@code sliceCount = 10} ⇒ 每刻约百来颗粒子（**与替换前的几何魔法阵同量级**），
     * 每个点每 10 刻（0.5 秒）重画一次。
     *
     * @param center     地面圆心（调用方给的位置就是阵面所在高度）
     * @param diameter   阵面直径（格）—— 想铺满技能范围就传 {@code 2 × 技能半径}
     * @param phase      自转相位（弧度）
     * @param sliceIndex 本刻画第几片（内部取模，负数也安全）
     * @param sliceCount 共分几片（≤ 0 ⇒ 当作 1 片，即整幅一次画完）
     * @param art        像素画数据（{@code null} / 空 ⇒ 静默不画）；只用到它的坐标
     * @param dustSize   历史参数（现路径不读，见上文"粒子种类"）
     */
    public static void pixelCircle(World world, Location center, double diameter, double phase,
                                   int sliceIndex, int sliceCount, PixelArt art, float dustSize) {
        if (world == null || center == null || art == null || diameter <= 0d) {
            return;
        }
        int n = art.size();
        if (n <= 0) {
            return;
        }
        int slices = Math.max(1, sliceCount);
        int start = Math.floorMod(sliceIndex, slices);

        double cos = Math.cos(phase);
        double sin = Math.sin(phase);
        float[] lx = art.localX();
        float[] ly = art.localY();

        //★ 交错：只画 start, start+slices, start+2*slices … —— 每刻换个起手点，几刻内铺满一遍
        for (int i = start; i < n; i += slices) {
            double px = lx[i] * diameter;
            double py = ly[i] * diameter;
            // 绕 Y 轴旋转 + 把画的上方向映射到 −z
            double dx = px * cos - py * sin;
            double dz = px * sin + py * cos;
            //★ 一律白色末地烛（需求指定；位置来自画，颜色不取画的 RGB）
            world.spawnParticle(Particle.END_ROD, center.clone().add(dx, 0d, dz), 1,
                    0d, 0d, 0d, PIXEL_END_ROD_SPREAD);
        }
    }
}
