package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * 「冷识 · 疯癫主教」的**粒子工具箱**（冷蓝紫主题）。
 *
 * <h2>工具箱纪律（与全仓先例一致）</h2>
 * <ul>
 *   <li>{@code public final class} + 私有构造器 + 纯静态；</li>
 *   <li>只画粒子 —— **不读状态、不注册任务、不调组件**，因此可以被任何组件在任意时刻调用；</li>
 *   <li>传 {@code null}（世界 / 坐标）⇒ 静默返回，绝不抛异常（故障隔离会把整个角色下线）；</li>
 *   <li>主题色 / 几何半径都是 {@code public static final} 常量（改外观只动这里）。</li>
 * </ul>
 *
 * <h2>★ 粒子 data 契约（工程闸门 {@code ParticleDataContractTest} 扫全仓）</h2>
 * {@code DUST} / {@code DUST_COLOR_TRANSITION} / {@code FLASH} 的 data 类型不是 {@code Void}，
 * <b>必须传 data 且 data 在实参末位</b>；漏传 ⇒ 运行期抛 {@code missing required data class}
 * ⇒ 整个角色被隔离下线。本类每一处带 data 的调用都遵守该末位约定。
 *
 * <h2>冷识的画面语言</h2>
 * 冷识不是"暴怒的黑影"，是<b>冷静到发疯的主教</b>：所以主色是冷蓝与紫（理性、灵魂），
 * 高光是青白（圣职者的烛火），只在关键帧掺一点暗红 —— 那是「灼燃的黑色怪物」从袍子底下漏出来的东西。
 * <p>每个技能都按三层铺：<b>底层</b>大范围低密度（把技能的"场"撑出来）、
 * <b>中层</b>形状骨架（环 / 柱 / 立方体 / 菱形片，让玩家读得懂这招长什么样）、
 * <b>顶层</b>高亮闪点（在哪、什么时候、爆了）。落点 / 爆发 / 召回这类关键帧再补一次性爆发环。
 */
public final class LetheVfx {

    private LetheVfx() {
    }

    // ───────── 主题色 ─────────

    /** 亮蓝 —— 主色（冷识的"理性"面）。 */
    public static final Color BLUE = Color.fromRGB(90, 180, 255);

    /** 深紫 —— 主色（主教袍、共鸣圈、塑形的骨架）。 */
    public static final Color PURPLE = Color.fromRGB(150, 90, 255);

    /** 青白 —— 高光 / 灵魂火把的烛芯（顶层闪点专用）。 */
    public static final Color CYAN_WHITE = Color.fromRGB(200, 235, 255);

    /** 暗红 —— 唯一一点暖色，只做点缀（「灼燃的黑色怪物」的余烬）。 */
    public static final Color DARK_RED = Color.fromRGB(200, 60, 60);

    /** 暗紫 —— 底层大范围低密度底光（比 {@link #PURPLE} 更沉，适合每刻铺满）。 */
    public static final Color DEEP_PURPLE = Color.fromRGB(84, 40, 150);

    /** 金 —— 只在箭袋回收那里用一次（新箭羽的金属反光），其余技能不上暖色。 */
    public static final Color GOLD = Color.fromRGB(255, 205, 110);

    // ───────── 粉尘包（public 供其它组件复用同一套配色）─────────

    /** 亮蓝粉尘。 */
    public static final Particle.DustOptions BLUE_DUST = new Particle.DustOptions(BLUE, 1.0f);

    /** 深紫粉尘。 */
    public static final Particle.DustOptions PURPLE_DUST = new Particle.DustOptions(PURPLE, 1.15f);

    /** 青白粉尘（细，做闪点）。 */
    public static final Particle.DustOptions CYAN_DUST = new Particle.DustOptions(CYAN_WHITE, 0.75f);

    /** 暗红粉尘（少量点缀）。 */
    public static final Particle.DustOptions RED_DUST = new Particle.DustOptions(DARK_RED, 0.9f);

    /** 金粉尘（箭袋回收）。 */
    public static final Particle.DustOptions GOLD_DUST = new Particle.DustOptions(GOLD, 0.7f);

    /** 暗紫大颗粒（底层光幕，粒径大 ⇒ 少量就能铺出体积）。 */
    private static final Particle.DustOptions DEEP_PURPLE_DUST = new Particle.DustOptions(DEEP_PURPLE, 1.5f);

    /** 蓝→紫渐变（"冷识在动手"这件事的主渐变）。 */
    public static final Particle.DustTransition BLUE_TO_PURPLE =
            new Particle.DustTransition(Color.fromRGB(70, 150, 255), Color.fromRGB(160, 95, 255), 1.5f);

    /** 紫→青白渐变（爆发 / 收束的高亮端）。 */
    public static final Particle.DustTransition PURPLE_TO_CYAN =
            new Particle.DustTransition(Color.fromRGB(120, 70, 240), Color.fromRGB(215, 242, 255), 1.35f);

    /** 蓝→暗红渐变（血泪形态：蓝色里渗进"灼燃"）。 */
    private static final Particle.DustTransition BLUE_TO_RED =
            new Particle.DustTransition(Color.fromRGB(90, 180, 255), Color.fromRGB(205, 65, 60), 1.2f);

    /** {@code Particle.FLASH} 的 data —— 1.21 的 FLASH 要 {@link Color}，漏传会隔离整个角色。 */
    private static final Color FLASH_BLUE = Color.fromRGB(150, 210, 255);

    /** 更白的一档闪光（升级 / 塑形落地那种"过曝"的一帧）。 */
    private static final Color FLASH_WHITE = Color.fromRGB(228, 244, 255);

    // ───────── 几何口径（与 §3.1 的数值一一对应）─────────

    /** 共鸣圈半径（契约：圈是 r=3）。 */
    public static final double RING_RADIUS = 3.0d;

    /** 召圣超时自爆半径（契约：r=10）。 */
    public static final double DETONATE_RADIUS = 10.0d;

    /** 暗影光环半径（契约：r=12）。 */
    public static final double AURA_RADIUS = 12.0d;

    /** 塑形立方体半边长（整体约 4.4 格，读作"巨大"）。 */
    public static final double CUBE_HALF = 2.2d;

    // ═════════════════════════ 主武器：流连标记 ═════════════════════════

    /**
     * **箭尾拖尾**：从上一刻位置到此刻位置的一条蓝紫细线。
     *
     * <p>箭是高速物体，玩家实际看到的是"线"而不是"点"，所以这里按距离铺点：
     * 每 0.3 格一颗、蓝紫交替（冷色收束），每 5 颗插一枚青白晶点（
     * 让轨迹在暗处也找得到），末端再加一簇青白 —— 箭尾刚好是"最亮的那一头"。
     *
     * @param from 上一刻的箭位置（或弓口）
     * @param to   此刻的箭位置
     */
    public static void arrowMarkTrail(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            // 箭几乎没动（悬停 / 刚离弦）：只补一小簇冷光，避免拖尾断成两截
            world.spawnParticle(Particle.DUST, to, 2, 0.06d, 0.06d, 0.06d, 0d, BLUE_DUST);
            return;
        }
        int points = (int) Math.min(32d, Math.max(2d, Math.ceil(length / 0.3d)));
        Vector step = delta.clone().multiply(1d / points);
        for (int i = 0; i <= points; i++) {
            Location p = from.clone().add(step.clone().multiply(i));
            world.spawnParticle(Particle.DUST, p, 1, 0.02d, 0.02d, 0.02d, 0d,
                    i % 2 == 0 ? BLUE_DUST : PURPLE_DUST);
            if (i % 3 == 0) {
                // 向上一指宽的紫色余影：拖尾有厚度，不像一根单薄的线
                world.spawnParticle(Particle.DUST, p.clone().add(0d, 0.06d, 0d), 1, 0d, 0d, 0d, 0d, PURPLE_DUST);
            }
            if (i % 5 == 0) {
                world.spawnParticle(Particle.END_ROD, p, 1, 0.01d, 0.01d, 0.01d, 0d);
            }
        }
        world.spawnParticle(Particle.DUST, to, 2, 0.07d, 0.07d, 0.07d, 0d, CYAN_DUST);
    }

    /**
     * **命中处的紫色十字爆点**：流连标记被兑现的一瞬。
     *
     * <p>十字是"记号"的视觉语言（{@link #forgetGuide} 的指路终点也用同一枚），
     * 本体只是一枚贴身的紫色十字 + 中心青白闪点 + 一圈冷紫外扩；
     * 删掉任何一层都会变成"一团糊住的紫色"，所以三层都保留。
     */
    public static void arrowMarkHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 0.05d, 0d);
        cross(world, center, 0.75d, 0.13d, PURPLE_DUST);
        // 顶层的三颗青白：命中点必须是视野里最亮的那个像素
        world.spawnParticle(Particle.DUST, center, 4, 0.05d, 0.05d, 0.05d, 0d, CYAN_DUST);
        world.spawnParticle(Particle.END_ROD, center, 3, 0.05d, 0.05d, 0.05d, 0.02d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 10, 0.28d, 0.28d, 0.28d, 0.04d, PURPLE_TO_CYAN);
        // 一点暗红：被标记的东西在"烧"
        world.spawnParticle(Particle.DUST, center, 3, 0.22d, 0.22d, 0.22d, 0d, RED_DUST);
    }

    // ═════════════════════════ 共鸣圈 ═════════════════════════

    /**
     * **共鸣圈生成**：r=3 的紫色环第一次铺到地上。
     *
     * <p>三层：贴地的深紫主环（48 点，够圆）、抬 0.45 格的蓝环（双线读感，让圈有厚度）、
     * 抬 0.9 格的窄内环（暗示它接下来会往里收）。圈缘立八束灵魂火，像八支插在地上的蜡烛 ——
     * 主教在布阵，不是在放烟花。最后补一记生成爆闪（只此一次）。
     */
    public static void ringSpawn(World world, Location center) {
        if (world == null || center == null) {
            return;
        }
        Location ground = center.clone().add(0d, 0.12d, 0d);
        ring(world, ground, RING_RADIUS, 48, PURPLE_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.45d, 0d), RING_RADIUS * 0.985d, 40, BLUE_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.9d, 0d), RING_RADIUS * 0.62d, 26, PURPLE_DUST, 0d);
        // 底层：圈内大范围低密度的冷蓝底光（把 r=3 的体积先撑出来）
        world.spawnParticle(Particle.DUST, ground, 26, RING_RADIUS / 2d, 0.1d, RING_RADIUS / 2d, 0.004d, BLUE_DUST);
        // 圈缘的八束烛火
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4d);
            Location p = ground.clone().add(Math.cos(a) * RING_RADIUS, 0d, Math.sin(a) * RING_RADIUS);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0.03d, 0.02d, 0.03d, 0.01d);
            world.spawnParticle(Particle.DUST, p.clone().add(0d, 0.35d, 0d), 1, 0d, 0d, 0d, 0d, CYAN_DUST);
        }
        // 生成瞬间：一次性爆发环
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, ground.clone().add(0d, 0.3d, 0d),
                24, RING_RADIUS / 2.4d, 0.35d, RING_RADIUS / 2.4d, 0.06d, BLUE_TO_PURPLE);
        world.spawnParticle(Particle.END_ROD, ground, 10, 0.2d, 0.1d, 0.2d, 0.03d);
    }

    /**
     * **共鸣圈脉冲**：每 0.25 秒结算一次时的那一下"呼吸"。
     *
     * <p>读数是 {@code triggersLeft}：剩余触发次数越多 ⇒ 环越贴近 r=3、越偏紫；
     * 越接近耗尽 ⇒ 环往里收、颜色往青白走，最后两次干脆换暗红火星 ——
     * 玩家不用数数字也知道"这个圈快没了"。
     *
     * @param triggersLeft 剩余可触发次数（契约默认 5，进化后 8；越界一律夹紧，不抛异常）
     */
    public static void ringPulse(World world, Location center, int triggersLeft) {
        if (world == null || center == null) {
            return;
        }
        int left = Math.max(0, Math.min(8, triggersLeft));
        // 归一化到"默认 5 次"这个读数上：5 次以上保持满半径，往下线性收缩到 1/3 半径
        double ratio = Math.min(1.0d, left / 5.0d);
        double radius = RING_RADIUS * (0.34d + 0.66d * ratio);
        Location ground = center.clone().add(0d, 0.12d, 0d);
        Particle.DustOptions main = left >= 4 ? PURPLE_DUST : (left >= 2 ? BLUE_DUST : CYAN_DUST);
        ring(world, ground, radius, 32, main, 0d);
        ring(world, ground.clone().add(0d, 0.3d, 0d), radius * 0.74d, 20, left <= 1 ? CYAN_DUST : BLUE_DUST, 0d);
        // 竖排刻度：随环一起变短 ⇒ "收紧"有第二个维度可读
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4d);
            double tick = 0.1d + 0.35d * ratio;
            Location p = ground.clone().add(Math.cos(a) * radius, 0d, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p.clone().add(0d, tick, 0d), 1, 0d, 0d, 0d, 0d, CYAN_DUST);
        }
        // 底层：圈内极淡的蓝尘（每刻调用，量必须小）
        world.spawnParticle(Particle.DUST, center, 4, radius / 2.5d, 0.25d, radius / 2.5d, 0.004d, BLUE_DUST);
        if (left <= 1) {
            // 告急：暗红火星从圈内浮上来
            world.spawnParticle(Particle.DUST, ground, 6, radius / 2.2d, 0.15d, radius / 2.2d, 0d, RED_DUST);
        }
    }

    /**
     * **共鸣圈 · 简化脉冲**：蓝圈 + 白点 + 圈内少量散落粒子（待机态画法）。
     *
     * <p>这是 2026-10-30 的"视觉降噪"口径：圈生成后 1 秒内、以及**打中敌人**的那些脉冲
     * 用完整画法 {@link #ringPulse(World, Location, int)}；其余脉冲退化成本函数 ——
     * 只保留"圈还在、还在转"的信息量，把粒子预算让给真正在命中的那一刻。
     * <p>画法：24 点蓝环（每 0.25 秒重新描一遍）＋ 4 颗环上的白点（END_ROD）＋ 圈心少量青白散点。
     */
    public static void ringPulseSimple(World world, Location center) {
        if (world == null || center == null) {
            return;
        }
        int points = 24;
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2d / points) * i;
            Location point = center.clone().add(Math.cos(angle) * RING_RADIUS, 0.05d, Math.sin(angle) * RING_RADIUS);
            world.spawnParticle(Particle.DUST, point, 1, 0.02d, 0.02d, 0.02d, 0d, BLUE_DUST);
        }
        for (int i = 0; i < 4; i++) {
            double angle = (Math.PI / 2d) * i + 0.35d;
            Location point = center.clone().add(Math.cos(angle) * RING_RADIUS, 0.28d, Math.sin(angle) * RING_RADIUS);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
        world.spawnParticle(Particle.DUST, center.clone().add(0d, 0.22d, 0d), 4,
                RING_RADIUS * 0.55d, 0.06d, RING_RADIUS * 0.55d, 0d, CYAN_DUST);
    }
    /**
     * **共鸣圈散掉**：三段环同时往里收 + 魂气上升。
     *
     * <p>散场不是"啪一下没了"，而是"被抽干"：外紫、中蓝、内青白三圈同帧出现，
     * 视觉上自然读成由外向内坍缩；再配灵魂火往上走，说明魂被收走了。
     */
    public static void ringExpire(World world, Location center) {
        if (world == null || center == null) {
            return;
        }
        Location ground = center.clone().add(0d, 0.1d, 0d);
        ring(world, ground, RING_RADIUS, 40, PURPLE_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.4d, 0d), RING_RADIUS * 0.62d, 28, BLUE_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.85d, 0d), RING_RADIUS * 0.26d, 16, CYAN_DUST, 0d);
        for (int i = 0; i < 14; i++) {
            double a = i * 0.45d;
            double rr = RING_RADIUS * (0.15d + 0.8d * ((i % 5) / 4d));
            Location p = ground.clone().add(Math.cos(a) * rr, 0.2d + (i % 4) * 0.45d, Math.sin(a) * rr);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0d, 0.02d, 0d, 0.01d);
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, ground.clone().add(0d, 0.4d, 0d),
                26, RING_RADIUS / 2.6d, 0.5d, RING_RADIUS / 2.6d, 0.05d, PURPLE_TO_CYAN);
        world.spawnParticle(Particle.DUST, ground, 8, 0.4d, 0.3d, 0.4d, 0d, RED_DUST);
    }

    // ═════════════════════════ 遗忘 ═════════════════════════

    /**
     * **遗忘落点**：一支灵魂火把插进地里，旁边撕开一道裂缝。
     *
     * <p>两件东西同时出现，才读得出"这里被写了一个遗忘"：
     * <ul>
     *   <li><b>火把</b> = 贴地一圈暗紫 + 向上收窄的螺旋青蓝火柱（灵魂火，不是普通火）；</li>
     *   <li><b>裂缝</b> = 一道贴身的竖向细长粒子带 —— 刻意只在水平方向摆 1~2 颗宽、沿 Y 拉 22 段，
     *       这样看起来是"被划开的一道口子"，而不是又一根柱子。</li>
     * </ul>
     * 落点再补一记青白闪与暗红余烬：这是"灼燃的黑色怪物"存在的唯一线索。
     */
    public static void forgetPlace(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location base = at.clone().add(0d, 0.05d, 0d);
        // ── 灵魂火把 ──
        ring(world, base, 1.15d, 20, PURPLE_DUST, 0d);
        for (int i = 0; i < 16; i++) {
            double t = i / 15d;
            double y = 0.15d + t * 2.4d;
            double rr = 0.22d + 0.16d * Math.sin(t * Math.PI * 3d);
            double a = t * 3.2d;
            Location p = base.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0.02d, 0.02d, 0.02d, 0.008d);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, t < 0.5d ? CYAN_DUST : PURPLE_DUST);
            }
        }
        // ── 裂缝：竖向细长粒子带（水平方向只占 1~2 颗的宽度）──
        for (int i = 0; i < 22; i++) {
            double y = i * 0.16d;
            double sway = Math.sin(i * 1.35d) * 0.09d;
            double bulge = Math.cos(i * 0.9d) * 0.07d;
            Location p = base.clone().add(sway, y, 0.45d + bulge);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, i % 3 == 0 ? CYAN_DUST : PURPLE_DUST);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, p.clone().add(0.16d, 0d, 0d), 1, 0d, 0d, 0d, 0d, PURPLE_DUST);
            }
        }
        // ── 落点关键帧 ──
        world.spawnParticle(Particle.END_ROD, base.clone().add(0d, 0.3d, 0d), 8, 0.15d, 0.25d, 0.15d, 0.02d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(0d, 0.8d, 0d),
                20, 0.3d, 0.7d, 0.3d, 0.05d, BLUE_TO_PURPLE);
        world.spawnParticle(Particle.DUST, base, 4, 0.3d, 0.1d, 0.3d, 0d, RED_DUST);
    }

    /**
     * **遗忘收回**：逆向收束 —— 魂从四面八方被吸回施法者身上。
     *
     * <p>与 {@link #forgetPlace} 刻意做成镜像：那边是"火柱往外立"，这边是
     * "螺旋往里塌"。半径与高度都随进度线性归零，最后在胸口高度收成一记青白闪光，
     * 玩家一眼能看出"这一笔被抹掉了、能量回到主教手里"。
     */
    public static void forgetReturn(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location base = at.clone();
        int points = 36;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);        // 0 = 最外层, 1 = 心
            double rr = 1.9d * (1d - t);
            double y = 2.3d * (1d - t) - 0.1d;
            double a = t * Math.PI * 4d;
            Location p = base.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, t > 0.72d ? CYAN_DUST : BLUE_DUST);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0d, 0d, 0d, 0d);
            }
        }
        Location heart = base.clone().add(0d, 1.0d, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, heart, 18, 0.18d, 0.25d, 0.18d, 0.05d, PURPLE_TO_CYAN);
        world.spawnParticle(Particle.END_ROD, heart, 10, 0.12d, 0.18d, 0.12d, 0.04d);
        world.spawnParticle(Particle.DUST, base.clone().add(0d, 0.1d, 0d), 6, 0.5d, 0.1d, 0.5d, 0d, PURPLE_DUST);
    }

    /**
     * **遗忘指引**：从施法者到裂缝的一条虚线。
     *
     * <p>虚线的意义是"指向"，所以它不能是连续的光带（那看起来像激光）：
     * 每 0.6 格亮、再 0.6 格暗，越靠近裂缝越白。终点放一枚与 {@link #arrowMarkHit}
     * 同族的紫色小十字 —— 全角色的"记号"就是这一个形状，玩家学一次就够。
     *
     * @param from 施法者位置（会抬到视线高度）
     * @param to   裂缝位置
     */
    public static void forgetGuide(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            return;
        }
        Vector unit = delta.clone().multiply(1d / length);
        double step = 0.22d;
        int total = (int) Math.min(220d, Math.ceil(length / step));
        Location eye = from.clone().add(0d, 0.9d, 0d);
        for (int i = 0; i <= total; i++) {
            double d = i * step;
            // 虚线：亮 0.6 格、暗 0.6 格（用距离分段判，而不是看 i 的奇偶 ⇒ 与长度无关）
            if ((int) (d / 0.6d) % 2 == 1) {
                continue;
            }
            double near = d / length;
            Location p = eye.clone().add(unit.clone().multiply(d));
            world.spawnParticle(Particle.DUST, p, 1, 0.03d, 0.03d, 0.03d, 0d, near > 0.6d ? CYAN_DUST : BLUE_DUST);
            if (i % 3 == 0) {
                // 虚线上沿的一点紫：线有两股，不像一根弹簧
                world.spawnParticle(Particle.DUST, p.clone().add(0d, 0.16d, 0d), 1, 0d, 0d, 0d, 0d, PURPLE_DUST);
            }
        }
        Location tip = to.clone().add(0d, 0.9d, 0d);
        cross(world, tip, 0.35d, 0.11d, PURPLE_DUST);
        world.spawnParticle(Particle.END_ROD, tip, 2, 0.05d, 0.05d, 0.05d, 0.01d);
    }

    // ═════════════════════════ 召圣 ═════════════════════════

    /**
     * **召回升空**：抬升的那一瞬，脚下炸开一座向上的光柱。
     *
     * <p>光柱越往上越稀、越白（"被抽上楼上的圣座"），下层是暗紫贴地环，
     * 中层每隔 0.9 格横插一束紫光当作"梯级"—— 高度差因此可读，
     * 玩家能看出箭被抬了几格。
     */
    public static void summonLift(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location base = at.clone().add(0d, 0.05d, 0d);
        ring(world, base, 1.35d, 22, PURPLE_DUST, 0d);
        ring(world, base.clone().add(0d, 0.35d, 0d), 0.85d, 16, BLUE_DUST, 0d);
        int points = 18;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            double y = 0.2d + t * 3.8d;
            double a = t * Math.PI * 2.6d;
            double rr = 0.32d * (1d - 0.55d * t);
            Location p = base.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            world.spawnParticle(Particle.DUST, p, 1, 0.02d, 0.02d, 0.02d, 0d, t > 0.66d ? CYAN_DUST : BLUE_DUST);
        }
        for (int i = 0; i < 4; i++) {
            double y = 0.6d + i * 0.9d;
            world.spawnParticle(Particle.DUST, base.clone().add(0d, y, 0d), 3, 0.22d, 0.06d, 0.22d, 0d, PURPLE_DUST);
        }
        world.spawnParticle(Particle.END_ROD, base.clone().add(0d, 1.4d, 0d), 6, 0.15d, 0.9d, 0.15d, 0.03d);
    }

    /**
     * **召圣悬停**：箭停在半空时的三圈轨道。
     *
     * <p>「悬停」不能只靠不动 —— 不动的粒子看起来像卡住了。所以给三层高度
     * （0.55 / 1.25 / 1.95 格）各套一圈颜色不同的环，且每层起始角错开 0.7 弧度：
     * 同一个 tick 里三圈的点不会对齐，读起来就是三条各自慢转的轨道。
     */
    public static void summonHover(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        double[] heights = {0.55d, 1.25d, 1.95d};
        Particle.DustOptions[] colors = {BLUE_DUST, PURPLE_DUST, CYAN_DUST};
        for (int h = 0; h < heights.length; h++) {
            int points = 14 + h * 2;
            double radius = 1.1d - h * 0.14d;
            for (int i = 0; i < points; i++) {
                double a = i * (2d * Math.PI / points) + h * 0.7d;
                Location p = at.clone().add(Math.cos(a) * radius, heights[h], Math.sin(a) * radius);
                world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, colors[h]);
            }
        }
        for (int i = 0; i < 8; i++) {
            double a = i * 0.9d;
            Location p = at.clone().add(Math.cos(a) * 0.9d, 0.35d + (i % 4) * 0.5d, Math.sin(a) * 0.9d);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0d, 0.015d, 0d, 0.005d);
        }
        world.spawnParticle(Particle.END_ROD, at.clone().add(0d, 1.6d, 0d), 3, 0.25d, 0.5d, 0.25d, 0.01d);
    }

    /**
     * **召回高速拖尾**：箭飞回施法者途中的每一刻。
     *
     * <p>调用方每刻只给"此刻的箭在哪"，所以密度全堆在这一点上 —— 位移本身就把点连成拖尾。
     * 主体是紫 + 蓝两层（紫在外、蓝在芯），再按坐标哈希每几颗插一枚青白火花头，
     * 免得整条尾巴是均匀的一条光（均匀 = 看不出速度）。
     */
    public static void summonRecallTrail(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 3, 0.05d, 0.05d, 0.05d, 0.01d, PURPLE_DUST);
        world.spawnParticle(Particle.DUST, at, 2, 0.04d, 0.04d, 0.04d, 0.01d, BLUE_DUST);
        world.spawnParticle(Particle.END_ROD, at, 1, 0.02d, 0.02d, 0.02d, 0d);
        int hash = Math.floorMod(at.getBlockX() * 31 + at.getBlockY() * 17 + at.getBlockZ() * 13, 3);
        if (hash == 0) {
            // 火花头：拖尾上每隔几颗出现一次的高亮点
            world.spawnParticle(Particle.DUST, at, 1, 0.02d, 0.02d, 0.02d, 0d, CYAN_DUST);
        }
        if (hash == 1) {
            // 暗红余烬：这支箭"经过的地方"被灼过
            world.spawnParticle(Particle.DUST, at, 1, 0.06d, 0.06d, 0.06d, 0d, RED_DUST);
        }
    }

    /**
     * **召圣超时自爆**：r=10 的紫色冲击环 + 一圈灵魂火。
     *
     * <p>这是全场最大的一个场面，量级要压过其它技能但**不能糊**，所以分四层：
     * ①r=10 主环（72 点，圈缘立 26 束灵魂火 + 26 颗散魂）；
     * ②r=6.5 / r=3 的内收环（层次，告诉玩家"中心在哪"）；
     * ③环内低密度紫底光（把 10 格半径的空腔填出体积）；
     * ④中心的一记白闪 + 爆发柱 + 一撮暗红余烬。
     */
    public static void summonDetonate(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location base = at.clone().add(0d, 0.1d, 0d);
        ring(world, base, DETONATE_RADIUS, 72, PURPLE_DUST, 0d);
        ring(world, base.clone().add(0d, 0.45d, 0d), DETONATE_RADIUS * 0.65d, 48, BLUE_DUST, 0d);
        ring(world, base.clone().add(0d, 0.9d, 0d), DETONATE_RADIUS * 0.3d, 28, CYAN_DUST, 0d);
        world.spawnParticle(Particle.DUST, base, 60, DETONATE_RADIUS / 2.2d, 0.5d, DETONATE_RADIUS / 2.2d, 0.01d, PURPLE_DUST);
        for (int i = 0; i < 26; i++) {
            double a = i * (2d * Math.PI / 26d);
            Location p = base.clone().add(Math.cos(a) * DETONATE_RADIUS, 0d, Math.sin(a) * DETONATE_RADIUS);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 2, 0.08d, 0.18d, 0.08d, 0.03d);
            world.spawnParticle(Particle.SOUL, p, 1, 0.15d, 0.3d, 0.15d, 0.02d);
        }
        world.spawnParticle(Particle.FLASH, base.clone().add(0d, 1.2d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_BLUE);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(0d, 1.0d, 0d),
                50, 1.2d, 2.2d, 1.2d, 0.09d, PURPLE_TO_CYAN);
        for (int i = 0; i < 12; i++) {
            Location p = base.clone().add(0d, 0.6d + i * 0.5d, 0d);
            world.spawnParticle(Particle.DUST, p, 2, 0.35d, 0.05d, 0.35d, 0d, i % 2 == 0 ? BLUE_DUST : CYAN_DUST);
        }
        world.spawnParticle(Particle.DUST, base, 10, DETONATE_RADIUS / 2.6d, 0.2d, DETONATE_RADIUS / 2.6d, 0d, RED_DUST);
    }

    // ═════════════════════════ 重塑 ═════════════════════════

    /**
     * **重塑·立方体下落**：一尊巨大的蓝色方块从天上砸下来，还在转。
     *
     * <p>几何全部在这里算，调用方只喂 {@code tick}：
     * <ul>
     *   <li>高度：0..40 tick 线性从 11.5 格落到地面（落地后不再动，只继续自转）；</li>
     *   <li>姿态：绕 X 轴 {@code roll} + 绕 Y 轴 {@code yaw} 双轴旋转 —— 只转 Y 会像陀螺，
     *       加一点 X 才读得出"这是块失重的石头"；</li>
     *   <li>骨架：8 个顶点 + 12 条棱（每条棱 4 段采样），顶点用青白单独提亮；</li>
     *   <li>气氛：方块内一点紫雾、下方一条下垂拖尾、地面的暗紫落影（越近越亮）。</li>
     * </ul>
     *
     * @param tick 引导开始起的刻数（0 起，越界夹紧；负数按 0 处理）
     */
    public static void reshapeCubeFall(World world, Location at, int tick) {
        if (world == null || at == null) {
            return;
        }
        int t = Math.max(0, tick);
        double progress = Math.min(1.0d, t / 40.0d);
        double fall = 11.5d * (1d - progress);
        double half = CUBE_HALF;
        Location center = at.clone().add(0d, half + fall, 0d);
        double yaw = t * 0.16d;
        double roll = t * 0.11d;
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        // 八个顶点：先绕 X 轴滚（roll），再绕 Y 轴偏（yaw）
        double[][] corner = new double[8][3];
        for (int i = 0; i < 8; i++) {
            double x = ((i & 4) == 0 ? -1d : 1d) * half;
            double y = ((i & 2) == 0 ? -1d : 1d) * half;
            double z = ((i & 1) == 0 ? -1d : 1d) * half;
            double ry = y * cosRoll - z * sinRoll;
            double rz = y * sinRoll + z * cosRoll;
            corner[i][0] = x * cosYaw - rz * sinYaw;
            corner[i][1] = ry;
            corner[i][2] = x * sinYaw + rz * cosYaw;
        }
        // 棱：下标只差一位的两个顶点才相连（bit2=x / bit1=y / bit0=z）
        Particle.DustOptions edgeDust = (t % 2 == 0) ? BLUE_DUST : PURPLE_DUST;
        for (int a = 0; a < 8; a++) {
            for (int b = a + 1; b < 8; b++) {
                int diff = a ^ b;
                if (diff != 1 && diff != 2 && diff != 4) {
                    continue;
                }
                for (int s = 0; s <= 3; s++) {
                    double f = s / 3d;
                    Location p = center.clone().add(
                            corner[a][0] + (corner[b][0] - corner[a][0]) * f,
                            corner[a][1] + (corner[b][1] - corner[a][1]) * f,
                            corner[a][2] + (corner[b][2] - corner[a][2]) * f);
                    world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, edgeDust);
                }
            }
        }
        // 顶点提亮：轮廓要"锐"，不然 12 条棱会散成雾
        for (int i = 0; i < 8; i++) {
            Location p = center.clone().add(corner[i][0], corner[i][1], corner[i][2]);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, CYAN_DUST);
        }
        // 方块内部的一点紫雾 + 顶上垂下的一条拖尾（说明它在掉）
        world.spawnParticle(Particle.DUST, center, 6, half * 0.6d, half * 0.6d, half * 0.6d, 0.004d, PURPLE_DUST);
        for (int i = 0; i < 5; i++) {
            Location p = center.clone().add(0d, half + 0.35d + i * 0.55d, 0d);
            world.spawnParticle(Particle.DUST, p, 1, 0.12d, 0d, 0.12d, 0d, BLUE_DUST);
        }
        // 地面落影：越接近落地越亮、越密（"落点在哪"提前告诉玩家）
        Location shadow = at.clone().add(0d, 0.08d, 0d);
        ring(world, shadow, 2.6d, 28, PURPLE_DUST, 0d);
        if (progress > 0.55d) {
            double spread = 2.2d * progress;
            world.spawnParticle(Particle.DUST, shadow, 6, spread, 0.05d, spread, 0.002d, BLUE_DUST);
        }
        if (progress > 0.85d) {
            world.spawnParticle(Particle.DUST, shadow, 5, 1.6d, 0.12d, 1.6d, 0d, RED_DUST);
        }
    }

    /**
     * **重塑落地**：地面冲击环 + 垂直光柱。
     *
     * <p>这是"方块砸进地里"的那一帧：三层地面环由外向内、一道 6 格高的光柱、
     * 一记白闪、以及沿圈抛出去的灵魂火碎片。光柱必须存在 —— 否则只是"地上炸了一下"，
     * 读不出"塑形是把地面立起来"这件事。
     */
    public static void reshapeImpact(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location base = at.clone().add(0d, 0.1d, 0d);
        ring(world, base, 4.6d, 40, PURPLE_DUST, 0d);
        ring(world, base.clone().add(0d, 0.35d, 0d), 3.0d, 30, BLUE_DUST, 0d);
        ring(world, base.clone().add(0d, 0.7d, 0d), 1.5d, 18, CYAN_DUST, 0d);
        // 垂直光柱：螺旋上升，越往上越白
        for (int i = 0; i < 20; i++) {
            double t = i / 19d;
            double y = 0.2d + t * 5.8d;
            double a = t * Math.PI * 3d;
            double rr = 0.45d * (1d - t);
            Location p = base.clone().add(Math.cos(a) * rr, y, Math.sin(a) * rr);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, t > 0.6d ? CYAN_DUST : BLUE_DUST);
        }
        world.spawnParticle(Particle.FLASH, base.clone().add(0d, 0.8d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_WHITE);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, base.clone().add(0d, 0.6d, 0d),
                46, 1.8d, 0.9d, 1.8d, 0.07d, BLUE_TO_PURPLE);
        // 溅出去的碎屑：灵魂火 + 散魂，越外围越稀
        for (int i = 0; i < 16; i++) {
            double a = i * (Math.PI * 2d / 16d);
            double rr = 1.2d + (i % 4) * 1.1d;
            Location p = base.clone().add(Math.cos(a) * rr, 0.15d + (i % 3) * 0.5d, Math.sin(a) * rr);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, p, 1, 0d, 0.02d, 0d, 0.01d);
            if (i % 2 == 0) {
                world.spawnParticle(Particle.SOUL, p, 1, 0.1d, 0.2d, 0.1d, 0.01d);
            }
        }
        world.spawnParticle(Particle.DUST, base, 8, 2.4d, 0.15d, 2.4d, 0d, RED_DUST);
    }

    /**
     * **重塑·碎片**：落点炸开的一张巨大菱形片（静止）。
     *
     * <p>用 {@code index} 决定"往哪个方向、多远、多高"：方位按黄金角铺开
     * （相邻两片不会重叠在同一方向），距离随 index 线性拉开 0.85 格。
     * 每片是**竖直平面里的一张菱形薄片**（法线朝外、微微后仰），
     * 外沿蓝、内缩一圈紫 —— 两层错开读作"有厚度"，而不是一张纸。
     * <p>「静止」是指本方法不随时间动：调用方每个 index 只调一次，碎片就钉在那里。
     *
     * @param index 碎片序号（0 起；负数按 0 处理）
     */
    public static void reshapeShard(World world, Location at, int index) {
        if (world == null || at == null) {
            return;
        }
        int idx = Math.max(0, index);
        // 黄金角铺方位 + 线性拉开距离 + 按 index 错开高度（同一圈不会齐平）
        double golden = Math.PI * (3d - Math.sqrt(5d));
        double angle = idx * golden;
        double dist = 1.4d + idx * 0.85d;
        double lift = 0.6d + (idx % 4) * 0.7d;
        Vector radial = new Vector(Math.cos(angle), 0d, Math.sin(angle));
        Location center = at.clone().add(radial.getX() * dist, lift, radial.getZ() * dist);
        // 薄片平面：一张半轴沿径向、一张半轴沿"上偏一点"（后仰 0.35 弧度 ⇒ 不是一块直立的纸）
        Vector right = radial.clone();
        Vector up = new Vector(0d, 1d, 0d).multiply(Math.cos(0.35d))
                .add(right.clone().multiply(Math.sin(0.35d)));
        Vector side = new Vector(-radial.getZ(), 0d, radial.getX());
        diamondSheet(world, center, side, up, 0.9d, 1.5d, 0.3d, BLUE_DUST);
        diamondSheet(world, center.clone().add(radial.clone().multiply(-0.14d)), side, up,
                0.62d, 1.05d, 0.34d, PURPLE_DUST);
        // 中心的一道竖直裂纹 + 顶端青白闪点
        for (int i = -4; i <= 4; i++) {
            Location p = center.clone().add(0d, i * 0.32d, 0d);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, CYAN_DUST);
        }
        world.spawnParticle(Particle.END_ROD, center, 2, 0.1d, 0.2d, 0.1d, 0.01d);
    }

    /**
     * **重塑·浮空**：地面上浮起来的一团蓝色碎块。
     *
     * <p>"团块"的关键是**形状固定**：偏移量写死成一张表，每次调用画出来的都是同一团，
     * 逐刻叠加就成为稳定悬浮的实体；若用随机偏移，每刻形状都在抖，看起来像噪点。
     * 上浮方向用魂火给出来 —— 这一团是被"托"起来的，不是飘着的。
     */
    public static void reshapeFloat(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        double[][] blob = {
                {0.0d, 0.15d, 0.0d}, {0.35d, 0.32d, 0.12d}, {-0.3d, 0.4d, 0.22d},
                {0.12d, 0.62d, -0.28d}, {-0.22d, 0.75d, -0.1d}, {0.28d, 0.9d, 0.2d},
                {-0.1d, 1.05d, 0.05d}, {0.05d, 1.3d, -0.15d}, {-0.25d, 1.45d, 0.18d}
        };
        for (int i = 0; i < blob.length; i++) {
            Location p = at.clone().add(blob[i][0], blob[i][1], blob[i][2]);
            world.spawnParticle(Particle.DUST, p, 1, 0.09d, 0.09d, 0.09d, 0.002d,
                    i % 3 == 2 ? PURPLE_DUST : BLUE_DUST);
            if (i % 4 == 0) {
                world.spawnParticle(Particle.DUST, p, 1, 0.18d, 0.05d, 0.18d, 0.002d, BLUE_DUST);
            }
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 1.1d, 0d), 2, 0.25d, 0.35d, 0.25d, 0d, CYAN_DUST);
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0d, 0.7d, 0d), 2, 0.22d, 0.5d, 0.22d, 0.012d);
        world.spawnParticle(Particle.END_ROD, at.clone().add(0d, 1.35d, 0d), 2, 0.14d, 0.14d, 0.14d, 0.01d);
    }

    /**
     * **重塑·标记爆点**：小方块炸开的那个"点"。
     *
     * <p>与大立方体故意同形不同量级：一样是 8 顶点 + 12 条棱，但半边长只有 0.34 格，
     * 而且棱只画中点 —— 玩家会认出"这是那个方块的缩小版"，同时它足够轻快、不糊视野。
     */
    public static void reshapeMarkExplosion(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        double half = 0.34d;
        // 八顶点：青白，爆点本体
        for (int i = 0; i < 8; i++) {
            Location p = at.clone().add(
                    ((i & 4) == 0 ? -1d : 1d) * half,
                    ((i & 2) == 0 ? -1d : 1d) * half,
                    ((i & 1) == 0 ? -1d : 1d) * half);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, CYAN_DUST);
        }
        // 十二条棱的中点：只取中点即可读出"方块"，爆点要轻
        for (int a = 0; a < 8; a++) {
            for (int b = a + 1; b < 8; b++) {
                int diff = a ^ b;
                if (diff != 1 && diff != 2 && diff != 4) {
                    continue;
                }
                double midX = (((a & 4) == 0 ? -1d : 1d) + ((b & 4) == 0 ? -1d : 1d)) * half * 0.5d;
                double midY = (((a & 2) == 0 ? -1d : 1d) + ((b & 2) == 0 ? -1d : 1d)) * half * 0.5d;
                double midZ = (((a & 1) == 0 ? -1d : 1d) + ((b & 1) == 0 ? -1d : 1d)) * half * 0.5d;
                world.spawnParticle(Particle.DUST, at.clone().add(midX, midY, midZ), 1, 0d, 0d, 0d, 0d, BLUE_DUST);
            }
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 14, 0.35d, 0.35d, 0.35d, 0.05d, BLUE_TO_PURPLE);
        world.spawnParticle(Particle.DUST, at, 5, 0.45d, 0.45d, 0.45d, 0.02d, PURPLE_DUST);
        world.spawnParticle(Particle.DUST, at, 2, 0.3d, 0.3d, 0.3d, 0d, RED_DUST);
    }

    // ═════════════════════════ 常驻 / 进化 ═════════════════════════

    /**
     * **暗影光环**（r=12）：光环每跳一拍时打出去的一道暗紫光幕。
     *
     * <p>★ 调用节奏不是"逐刻"，而是**每 3 秒一拍**（光环的侵蚀结算节拍）。
     * 所以它必须是一张**成形的**脉冲环：12 格的圆上按 32 点铺满（每 2.4 格一颗 ⇒
     * 读成一条虚线环而不是几颗散点），内侧再压一圈更淡的紫做出"幕"的厚度；
     * 8 个方位各立一道 3 颗高的暗紫帘 —— 幕高只到 1.5 格左右，
     * 压迫感来自"12 格范围 + 稀疏的一整圈"，不来自堆高。
     * <p>最后沿环缘撒 4 颗散魂：这一拍同时也在扣特殊值，魂被带走的画面要与数值同拍。
     */
    public static void auraTick(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location ground = at.clone().add(0d, 0.06d, 0d);
        ring(world, ground, AURA_RADIUS, 32, DEEP_PURPLE_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.5d, 0d), AURA_RADIUS * 0.985d, 20, PURPLE_DUST, 0d);
        for (int i = 0; i < 8; i++) {
            double a = i * (Math.PI / 4d);
            double cos = Math.cos(a);
            double sin = Math.sin(a);
            for (int h = 0; h < 3; h++) {
                Location p = ground.clone().add(cos * AURA_RADIUS, 0.3d + h * 0.5d, sin * AURA_RADIUS);
                world.spawnParticle(Particle.DUST, p, 1, 0.08d, 0.02d, 0.08d, 0d, DEEP_PURPLE_DUST);
            }
        }
        // 圈内极稀的底光：只负责把 12 格的空腔"填"出来，不参与形状
        world.spawnParticle(Particle.DUST, at, 8, AURA_RADIUS / 3.5d, 0.35d, AURA_RADIUS / 3.5d, 0.002d, DEEP_PURPLE_DUST);
        for (int i = 0; i < 4; i++) {
            double a = i * (Math.PI / 2d) + 0.4d;
            Location p = ground.clone().add(Math.cos(a) * AURA_RADIUS, 0.15d, Math.sin(a) * AURA_RADIUS);
            world.spawnParticle(Particle.SOUL, p, 1, 0.1d, 0.25d, 0.1d, 0.01d);
        }
    }

    /**
     * **锻造到账**（装备被动成型）：一小簇锻造火花。
     *
     * <p>只有这里允许金属火花（{@code ELECTRIC_SPARK} + {@code CRIT}）：
     * 它是"新护甲被敲出来"的机械动作，与灵魂系技能区分开。
     * 再加一点金粉与暗红，说明这件盔甲是被冷识「签下」的，不是普通铁匠活。
     */
    public static void equipForge(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at.clone().add(0d, 0.15d, 0d), 0.9d, 16, BLUE_DUST, 0d);
        world.spawnParticle(Particle.ELECTRIC_SPARK, at, 10, 0.35d, 0.3d, 0.35d, 0.06d);
        world.spawnParticle(Particle.CRIT, at, 8, 0.3d, 0.3d, 0.3d, 0.12d);
        world.spawnParticle(Particle.DUST, at, 8, 0.3d, 0.35d, 0.3d, 0.02d, GOLD_DUST);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0d, 0.6d, 0d),
                12, 0.3d, 0.5d, 0.3d, 0.04d, PURPLE_TO_CYAN);
        world.spawnParticle(Particle.DUST, at, 3, 0.4d, 0.2d, 0.4d, 0d, RED_DUST);
    }

    /**
     * **进化爆闪**：升级的那一帧，也是整个角色最亮的一帧。
     *
     * <p>三层扩散环（青白 → 蓝 → 紫，由内向外）+ 白闪 + 进阶光柱 + 一堆 END_ROD，
     * 最后混一撮金粉：主教升座时衣袍上落下的一点香灰。
     */
    public static void evolutionFlash(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        ring(world, at.clone().add(0d, 0.1d, 0d), 1.4d, 20, CYAN_DUST, 0d);
        ring(world, at.clone().add(0d, 0.5d, 0d), 2.4d, 28, BLUE_DUST, 0d);
        ring(world, at.clone().add(0d, 0.9d, 0d), 3.4d, 34, PURPLE_DUST, 0d);
        world.spawnParticle(Particle.FLASH, center, 1, 0d, 0d, 0d, 0d, FLASH_WHITE);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 44, 0.9d, 1.4d, 0.9d, 0.08d, PURPLE_TO_CYAN);
        for (int i = 0; i < 16; i++) {
            Location p = at.clone().add(0d, 0.2d + i * 0.28d, 0d);
            world.spawnParticle(Particle.DUST, p, 1, 0.2d, 0d, 0.2d, 0d, i % 2 == 0 ? CYAN_DUST : BLUE_DUST);
        }
        world.spawnParticle(Particle.END_ROD, center, 14, 0.5d, 0.9d, 0.5d, 0.05d);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 6, 0.4d, 0.7d, 0.4d, 0.06d);
        world.spawnParticle(Particle.DUST, at, 6, 0.5d, 0.3d, 0.5d, 0d, GOLD_DUST);
    }

    /**
     * **箭袋回收**：背包口往上收的一圈光 —— 箭被吸回袋里。
     *
     * <p>方向必须是"往回"：光点贴着背包口、并且带一点向内的速度（负 extra），
     * 玩家才读得出"收回来了"而不是"又长出来一支"。
     * <p>蓝金两色是"新箭（金羽）与旧魂（蓝）"的和解；血泪形态下混进暗红渐变与灵魂火，
     * 那是这支箭的代价。
     *
     * @param bloodTear 是否血泪形态（回收里混进暗红）
     */
    public static void quiverRestore(World world, Location at, boolean bloodTear) {
        if (world == null || at == null) {
            return;
        }
        Location mouth = at.clone().add(0d, 0.9d, 0d);
        ring(world, mouth, 0.55d, 14, BLUE_DUST, 0d);
        for (int i = 0; i < 10; i++) {
            double a = i * (Math.PI * 2d / 10d);
            double rr = 0.55d * (1d - i * 0.012d);
            Location p = mouth.clone().add(Math.cos(a) * rr, i * 0.07d, Math.sin(a) * rr);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? GOLD_DUST : BLUE_DUST);
        }
        // 向内的速度（负 extra）⇒ 光点往背包口里沉
        world.spawnParticle(Particle.END_ROD, mouth.clone().add(0d, 0.25d, 0d), 5, 0.2d, 0.1d, 0.2d, -0.02d);
        world.spawnParticle(Particle.DUST, mouth, 6, 0.22d, 0.18d, 0.22d, -0.01d,
                bloodTear ? RED_DUST : GOLD_DUST);
        if (bloodTear) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, mouth, 12, 0.25d, 0.3d, 0.25d, 0.03d, BLUE_TO_RED);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, mouth, 3, 0.18d, 0.18d, 0.18d, 0.02d);
            world.spawnParticle(Particle.DUST, mouth, 4, 0.2d, 0.25d, 0.2d, 0d, RED_DUST);
        } else {
            world.spawnParticle(Particle.DUST, mouth, 4, 0.2d, 0.25d, 0.2d, 0d, CYAN_DUST);
        }
    }

    // ═════════════════════════ 受伤反馈 ═════════════════════════

    /**
     * **灵魂受伤**：暗紫裂片往外崩。
     *
     * <p>三片裂片用三条短折线表达「被割开的魂」（裂片是"断口"，不是散点），
     * 再叠散魂与幽匿魂火 —— 这两种粒子自带"灵魂的烟"的语义，不需要额外解释。
     * 颜色整体压在暗紫区间，与 {@link #damageSante} 的青蓝碎光区分得很开：
     * 玩家被不同口径打中时，看颜色就知道自己掉的是哪一管。
     */
    public static void damageSoul(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        for (int s = 0; s < 3; s++) {
            double a = s * (Math.PI * 2d / 3d) + 0.35d;
            double ca = Math.cos(a);
            double sa = Math.sin(a);
            for (int i = 0; i < 4; i++) {
                double d = 0.18d + i * 0.16d;
                Location p = center.clone().add(ca * d, 0.12d * i - 0.1d, sa * d);
                world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d,
                        i % 2 == 0 ? PURPLE_DUST : DEEP_PURPLE_DUST);
            }
        }
        world.spawnParticle(Particle.SOUL, center, 4, 0.28d, 0.25d, 0.28d, 0.02d);
        world.spawnParticle(Particle.SCULK_SOUL, center, 2, 0.3d, 0.2d, 0.3d, 0.01d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 10, 0.3d, 0.3d, 0.3d, 0.04d, PURPLE_TO_CYAN);
    }

    /**
     * **特殊值受伤**：青蓝碎光炸开。
     *
     * <p>与灵魂伤刻意反着做：那边是"往下崩的暗碎片"，这边是"往外炸的亮结晶"
     * （END_ROD 的星屑 + 青白粉尘 + 附魔命中光），最后只留一颗紫做收边。
     * 亮 ≠ 伤害更大：它是"SanTE 这条读数在掉"的专用颜色。
     */
    public static void damageSante(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        world.spawnParticle(Particle.END_ROD, center, 8, 0.35d, 0.35d, 0.35d, 0.06d);
        world.spawnParticle(Particle.DUST, center, 10, 0.4d, 0.4d, 0.4d, 0.02d, CYAN_DUST);
        world.spawnParticle(Particle.DUST, center, 6, 0.45d, 0.45d, 0.45d, 0.02d, BLUE_DUST);
        world.spawnParticle(Particle.ENCHANTED_HIT, center, 4, 0.3d, 0.3d, 0.3d, 0.05d);
        world.spawnParticle(Particle.DUST, center, 2, 0.35d, 0.35d, 0.35d, 0d, PURPLE_DUST);
    }

    // ═════════════════════════ 内部几何工具 ═════════════════════════

    /**
     * 水平圆环：在 {@code center} 所在高度的水平面上按等角铺 {@code points} 颗粉尘。
     *
     * <p>为什么自算而不用现成工具：本类要的圆环几乎都要「指定抬升高度 + 指定颜色」，
     * 自己算两行三角函数就够，也省得为每颗点构造一次 builder。
     */
    private static void ring(World world, Location center, double radius, int points,
                             Particle.DustOptions dust, double extraY) {
        if (points <= 0 || radius <= 0d) {
            return;
        }
        for (int i = 0; i < points; i++) {
            double a = i * (2d * Math.PI / points);
            Location p = center.clone().add(Math.cos(a) * radius, extraY, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, dust);
        }
    }

    /**
     * 十字记号（横向 + 竖向各一条，每条从中心向外逐点铺）。
     *
     * <p>隔着 0.1 格留出一颗空隙再做一点深度方向的回声，是为了让十字在斜视角下
     * 也不塌成一条线（纯二维十字只有正对时才认得出）。
     */
    private static void cross(World world, Location center, double arm, double step, Particle.DustOptions dust) {
        if (arm <= 0d || step <= 0d) {
            return;
        }
        int segments = Math.max(1, (int) Math.ceil(arm / step));
        for (int s = 0; s <= segments; s++) {
            double d = s * step;
            world.spawnParticle(Particle.DUST, center.clone().add(d, 0d, 0d), 1, 0d, 0d, 0d, 0d, dust);
            world.spawnParticle(Particle.DUST, center.clone().add(-d, 0d, 0d), 1, 0d, 0d, 0d, 0d, dust);
            world.spawnParticle(Particle.DUST, center.clone().add(0d, d, 0d), 1, 0d, 0d, 0d, 0d, dust);
            world.spawnParticle(Particle.DUST, center.clone().add(0d, -d, 0d), 1, 0d, 0d, 0d, 0d, dust);
        }
        // 竖臂的宽度回声：贴两小段横臂，让"十字"有厚度（斜着看也不消失）
        for (int s = 1; s <= 2; s++) {
            double y = arm * (0.35d + 0.3d * s);
            world.spawnParticle(Particle.DUST, center.clone().add(0.1d, y, 0d), 1, 0d, 0d, 0d, 0d, dust);
            world.spawnParticle(Particle.DUST, center.clone().add(-0.1d, -y, 0d), 1, 0d, 0d, 0d, 0d, dust);
        }
    }

    /**
     * 一张菱形薄片（四条边逐点铺）。
     *
     * @param axisA 第一条半轴（单位向量，对应半对角线 {@code halfA}）
     * @param axisB 第二条半轴（单位向量，对应半对角线 {@code halfB}）
     */
    private static void diamondSheet(World world, Location center, Vector axisA, Vector axisB,
                                     double halfA, double halfB, double step, Particle.DustOptions dust) {
        if (halfA <= 0d || halfB <= 0d || step <= 0d) {
            return;
        }
        double[][] vertices = {{0d, halfB}, {halfA, 0d}, {0d, -halfB}, {-halfA, 0d}};
        for (int edge = 0; edge < vertices.length; edge++) {
            double ax = vertices[edge][0];
            double ay = vertices[edge][1];
            double bx = vertices[(edge + 1) % vertices.length][0];
            double by = vertices[(edge + 1) % vertices.length][1];
            double length = Math.hypot(bx - ax, by - ay);
            int segments = Math.max(2, (int) Math.ceil(length / step));
            for (int i = 0; i <= segments; i++) {
                double f = i / (double) segments;
                Location p = center.clone()
                        .add(axisA.clone().multiply(ax + (bx - ax) * f))
                        .add(axisB.clone().multiply(ay + (by - ay) * f));
                world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, dust);
            }
        }
    }
    // ───────── 施法期「环绕角色」的几何粒子（2026-10-30 按需求追加）─────────

    /**
     * **开裂隙**：蓝色几何粒子绕角色**旋绕而上**。
     * <p>画面意图：不是"脚下炸一下"，而是"地上的法阵开始转、把人往上缠"——
     * 因此用正方形四角的螺旋上升（几何感）＋低层青白底环，读作"正在开门"。
     *
     * @param tick 引导期已过的刻数（调用方每刻递增；只用来算相位，不持有状态）
     */
    public static void forgetOpenOrbit(World world, Location at, int tick) {
        if (world == null || at == null) {
            return;
        }
        for (int corner = 0; corner < 4; corner++) {
            double angle = tick * 0.32d + corner * (Math.PI / 2d);
            double radius = 0.95d;
            double rise = 0.15d + ((tick * 0.09d) + corner * 0.55d) % 2.3d;
            Location point = at.clone().add(Math.cos(angle) * radius, rise, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 2, 0.05d, 0.05d, 0.05d, 0d, BLUE_DUST);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.02d, 0.04d, 0.02d, 0.01d);
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 0.05d, 0d), 10, 0.85d, 0.04d, 0.85d, 0d, CYAN_DUST);
    }

    /**
     * **关裂隙**（返回/收回）：蓝色几何粒子**反向加速**旋绕而上，像被裂缝吸回去。
     * <p>与 {@link #forgetOpenOrbit} 的差别只在旋向与速度：开门是缓、闭环是急，玩家能听/看出方向。
     */
    public static void forgetCloseOrbit(World world, Location at, int tick) {
        if (world == null || at == null) {
            return;
        }
        for (int corner = 0; corner < 4; corner++) {
            double angle = -tick * 0.46d + corner * (Math.PI / 2d);
            double radius = 0.95d - 0.05d * corner;
            double rise = 0.1d + ((tick * 0.13d) + corner * 0.5d) % 2.6d;
            Location point = at.clone().add(Math.cos(angle) * radius, rise, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 2, 0.05d, 0.05d, 0.05d, 0d, BLUE_DUST);
            world.spawnParticle(Particle.END_ROD, point, 1, 0.02d, 0.04d, 0.02d, 0.012d);
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 0.05d, 0d), 8, 0.8d, 0.04d, 0.8d, 0d, PURPLE_DUST);
    }

    /**
     * **大招（重塑）**：紫色粒子绕角色盘旋（引导期每刻）。
     * <p>画面意图：主教在做最后的祷告——紫是"主教袍/共鸣"，青白是烛火；
     * 六点双环错位旋转，比单环更像"有东西正在头顶成形"。
     *
     * @param tick 引导期已过的刻数（只算相位）
     */
    public static void reshapeAura(World world, Location at, int tick) {
        if (world == null || at == null) {
            return;
        }
        for (int i = 0; i < 6; i++) {
            double angle = tick * 0.25d + i * (Math.PI / 3d);
            double radius = (i % 2 == 0) ? 1.55d : 1.15d;
            double height = 0.55d + (i % 3) * 0.5d;
            Location point = at.clone().add(Math.cos(angle) * radius, height, Math.sin(angle) * radius);
            world.spawnParticle(Particle.DUST, point, 2, 0.04d, 0.04d, 0.04d, 0d, PURPLE_DUST);
        }
        world.spawnParticle(Particle.SOUL_FIRE_FLAME, at.clone().add(0d, 0.8d, 0d), 3, 0.9d, 0.7d, 0.9d, 0.005d);
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 0.1d, 0d), 8, 1.1d, 0.08d, 1.1d, 0d, PURPLE_DUST);
    }
}
