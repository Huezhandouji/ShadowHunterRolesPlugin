package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * 「芹泽沙耶香 · 瓦尔基里」的**粒子工具箱**（黑红主题）。
 *
 * <h2>工具箱纪律（与全仓先例一致）</h2>
 * <ul>
 *   <li>{@code public final class} + 私有构造器 + 纯静态；</li>
 *   <li>只画粒子 —— **不读状态、不注册任务、不调组件**；</li>
 *   <li>传 {@code null}（世界 / 坐标）⇒ 静默返回；</li>
 *   <li>主题色 / 数值全部是 {@code public static final} 常量（改外观只动这里）。</li>
 * </ul>
 *
 * <h2>★ 粒子 data 契约（工程闸门 {@code ParticleDataContractTest} 扫全仓）</h2>
 * {@code DUST} / {@code DUST_COLOR_TRANSITION} 的 data 类型非 {@code Void}，
 * <b>必须传 data 且 data 在末位实参</b>；漏传 ⇒ 组件抛
 * {@code missing required data class} ⇒ 整个角色被隔离下线。
 * 本类的每个 {@code DUST} 调用都显式带上 {@link Particle.DustOptions}。
 *
 * <h2>黑红口径</h2>
 * 「黑」= {@link #BLACK_DUST}（近黑微红）+ {@code SMOKE}；「红」= {@link #RED_DUST} + {@link #CRIMSON}。
 * 两者混排 ⇒ 影尘 + 血色。{@code DUST_COLOR_TRANSITION}（黑→红渐变）专门用在"爆发 / 大招"这种大场面。
 *
 * <h2>★ 2026-10-17 需求：层数分档</h2>
 * 拔刀 / 居合的攻击特效按**本次兑现的笼手层数**分 4 档（0/1/2/3）：层数越高，粒子越多、
 * 环越多、颜色越艳（3 档额外加 FLASH 与 CRIT 火星）。见 {@link #drawnSlash} / {@link #iaiSlash}。
 */
public final class SayakaVfx {

    private SayakaVfx() {
    }

    // ───────── 主题色 ─────────

    /** 近黑（略带红）—— "影尘"。 */
    public static final Particle.DustOptions BLACK_DUST = new Particle.DustOptions(Color.fromRGB(18, 4, 8), 1.4f);

    /** 血红 —— 主色。 */
    public static final Particle.DustOptions RED_DUST = new Particle.DustOptions(Color.fromRGB(214, 16, 28), 1.1f);

    /** 深红（更暗的强调色）。 */
    public static final Particle.DustOptions DEEP_RED_DUST = new Particle.DustOptions(Color.fromRGB(120, 4, 12), 1.2f);

    /** 黑→红渐变 —— 爆发 / 大招专用（比纯色更"炸"）。 */
    public static final Particle.DustTransition CRIMSON =
            new Particle.DustTransition(Color.fromRGB(20, 0, 4), Color.fromRGB(236, 26, 36), 1.6f);

    /** 追猎粒子的亮红（比 {@link #RED_DUST} 更亮，便于在暗背景里追踪飞行路径）。 */
    public static final Particle.DustOptions MOTE_RED_DUST =
            new Particle.DustOptions(Color.fromRGB(255, 40, 48), 0.9f);

    /**
     * {@code FLASH} 的 data（★ 1.21.11 的 FLASH 需要 {@code Color}，漏传 ⇒ 运行期抛
     * {@code missing required data class} ⇒ 整个角色被隔离下线 —— 已被
     * {@code ParticleDataContractTest} 抓过一次）。
     */
    public static final Color FLASH_RED = Color.fromRGB(255, 72, 84);

    /** 居合蓄力粒子的环绕中心高度（格，脚底起算）—— 2026-10-17 需求：由 1.0 下移到 0.5。 */
    public static final double IAI_CHARGE_HEIGHT = 0.5d;

    // ───────── 失衡 / 爆发 ─────────

    /** 失衡积累命中：小簇黑红飞尘（每次普攻 / 技能命中都画）。 */
    public static void imbalanceHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, 5, 0.25d, 0.25d, 0.25d, 0.02d, BLACK_DUST);
        world.spawnParticle(Particle.DUST, at, 4, 0.3d, 0.3d, 0.3d, 0.02d, RED_DUST);
    }

    /**
     * **失衡层数闲置消退**（2026-10-18 需求：10 秒未叠层后每 0.3 秒退 1 层）。
     *
     * <p>做成"影尘往下沉"：一小簇黑影与暗红从身上往脚下坠，明确区别于"被打中"的
     * 上飘火星 —— 玩家一眼能看出"层数在掉，不是又被叠了"。
     */
    public static void imbalanceDecay(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.SQUID_INK, at, 3, 0.22d, 0.28d, 0.22d, -0.06d);
        world.spawnParticle(Particle.DUST, at.clone().add(0d, -0.35d, 0d), 3,
                0.2d, 0.15d, 0.2d, 0d, DEEP_RED_DUST);
        world.spawnParticle(Particle.DUST, at, 2, 0.3d, 0.35d, 0.3d, 0d, BLACK_DUST);
    }

    /** **失衡爆发**开场：黑红柱状喷发 + 冲击环（需求："立即触发失衡爆发"，观感要重）。 */
    public static void burstErupt(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 40, 0.5d, 0.7d, 0.5d, 0.06d, CRIMSON);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 18, 0.45d, 0.6d, 0.45d, 0.02d);
        world.spawnParticle(Particle.SQUID_INK, at, 12, 0.4d, 0.5d, 0.4d, 0.05d);
        ring(world, at, 2.2d, 22, RED_DUST, 0.1d);
        ring(world, at.clone().add(0d, 1.0d, 0d), 1.4d, 16, BLACK_DUST, 0d);
    }

    /**
     * **失衡爆发的范围冲击**（2026-10-17 需求：被动造成伤害要有范围特效）。
     *
     * <p>"周围敌人"= **目标周围**的敌人 ⇒ 圆心就是被触发的目标。这里是那个圆心的可读观感：
     * 地面三层扩散环 + 上升的影尘柱 + 中心黑红渐变。
     */
    public static void burstBlast(World world, Location center, double radius) {
        if (world == null || center == null) {
            return;
        }
        double r = Math.max(0.5d, radius);
        Location ground = center.clone().add(0d, 0.15d, 0d);
        ring(world, ground, r, 30, RED_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.35d, 0d), r * 0.66d, 22, BLACK_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.7d, 0d), r * 0.36d, 16, RED_DUST, 0d);
        //影尘柱：沿半径随机高度向上喷
        for (int i = 0; i < 18; i++) {
            double a = (i / 18d) * Math.PI * 2d;
            double rr = r * (0.25d + 0.75d * ((i % 5) / 4d));
            Location p = ground.clone().add(Math.cos(a) * rr, 0.15d + (i % 4) * 0.45d, Math.sin(a) * rr);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? DEEP_RED_DUST : BLACK_DUST);
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, ground.clone().add(0d, 0.5d, 0d),
                26, r / 2.2d, 0.5d, r / 2.2d, 0.05d, CRIMSON);
        world.spawnParticle(Particle.LARGE_SMOKE, ground.clone().add(0d, 0.4d, 0d),
                10, r / 2.6d, 0.35d, r / 2.6d, 0.02d);
    }

    /** 爆发**持续期光环**：每 2 刻绕敌人一圈（黑红交替，缓慢自转）。 */
    public static void burstAura(World world, Location at, int phase) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        double angle = (phase % 40) * (Math.PI / 20d);
        for (int i = 0; i < 4; i++) {
            double a = angle + i * (Math.PI / 2d);
            Location p = center.clone().add(Math.cos(a) * 0.9d, 0d, Math.sin(a) * 0.9d);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? RED_DUST : BLACK_DUST);
        }
        world.spawnParticle(Particle.SMOKE, center, 1, 0.35d, 0.4d, 0.35d, 0.005d);
    }

    /** 爆发**每秒结算**：脚下红色脉冲环（"在持续掉 SAN"的可读读数）。 */
    public static void burstDrain(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at.clone().add(0d, 0.2d, 0d), 1.1d, 12, RED_DUST, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0d, 1.0d, 0d),
                6, 0.3d, 0.4d, 0.3d, 0.01d, CRIMSON);
    }

    /** 猎影人**回复**：暗红微光（每秒结算时给受益者一点可读反馈）。 */
    public static void allyBless(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 1.2d, 0d), 3, 0.3d, 0.4d, 0.3d, 0d, RED_DUST);
    }

    // ───────── 笼手回血追猎粒子 ─────────

    /** 追猎粒子飞行中的拖尾（黑红两层，每刻 1 个点）。 */
    public static void healthMoteTrail(World world, Location at, int seed) {
        if (world == null || at == null) {
            return;
        }
        Location p = at.clone();
        world.spawnParticle(Particle.DUST, p, 1, 0.02d, 0.02d, 0.02d, 0d, MOTE_RED_DUST);
        if ((seed & 1) == 0) {
            world.spawnParticle(Particle.SMOKE, p, 1, 0.02d, 0.02d, 0.02d, 0.002d);
        }
    }

    /** 追猎粒子**到达**纱耶香：一声红色小绽（此时才回血）。 */
    public static void healthMoteArrive(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        world.spawnParticle(Particle.DUST, center, 6, 0.28d, 0.35d, 0.28d, 0.02d, RED_DUST);
        world.spawnParticle(Particle.HEART, center, 1, 0.1d, 0.1d, 0.1d, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 4, 0.2d, 0.25d, 0.2d, 0.01d, CRIMSON);
    }

    // ───────── 主武器：充能 / 拔纳 / 居合 ─────────

    /** 武器充能：身前黑红涡旋（层数越高越密）。 */
    public static void chargeLoad(World world, Location at, Vector dir, int level) {
        if (world == null || at == null) {
            return;
        }
        Location front = at.clone().add(dir.clone().multiply(0.8d));
        int n = 6 + 4 * Math.max(1, Math.min(3, level));
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, front, n, 0.25d, 0.3d, 0.25d, 0.03d, CRIMSON);
        world.spawnParticle(Particle.SQUID_INK, front, 4, 0.2d, 0.2d, 0.2d, 0.02d);
    }

    /** 充能被消耗（攻击 / 居合兑现）：黑红一爆（层数越高越炸）。 */
    public static void chargeConsumed(World world, Location at, int level) {
        if (world == null || at == null) {
            return;
        }
        int lv = Math.max(1, Math.min(3, level));
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 6 + 6 * lv,
                0.35d + 0.1d * lv, 0.35d, 0.35d + 0.1d * lv, 0.05d, CRIMSON);
        if (lv >= 3) {
            ring(world, at, 1.6d, 18, RED_DUST, 0d);
        }
    }

    /** 拔刀 / 纳刀切换：身侧两道影尘。 */
    public static void stanceToggle(World world, Location at, boolean toSheathed) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 1.0d, 0d), 10, 0.5d, 0.5d, 0.5d, 0.01d,
                toSheathed ? BLACK_DUST : RED_DUST);
        world.spawnParticle(Particle.SMOKE, at.clone().add(0d, 0.6d, 0d), 6, 0.4d, 0.3d, 0.4d, 0.01d);
    }

    // ───────── 纳刀蓄力（2026-10-17 需求 1）─────────

    /**
     * **居合蓄力中的环绕影尘**（progress 0..1，越接近满越密、越亮）。
     *
     * <p>刻意做成"绕腿螺旋"而不是贴地圆环：蓄力条在屏幕上增长，世界里的影尘同步往刀上聚，
     * 玩家不用看 HUD 也能感到"快好了"。
     *
     * <p>★ 2026-10-17 需求：整组粒子<b>下移到腰线以下</b>（{@value #IAI_CHARGE_HEIGHT} = 0.5，
     * 竖向偏移再压到 -0.45..0，世界高度约 0.05~0.65）——<b>不进第一人称准星范围</b>，
     * 同时把螺旋臂 5→4、半径 0.85→0.7、每臂 3→2 点，进一步减少遮挡。
     */
    public static void iaiCharge(World world, Location at, double progress, int phase) {
        if (world == null || at == null) {
            return;
        }
        double p = clamp01(progress);
        Location center = at.clone().add(0d, IAI_CHARGE_HEIGHT, 0d);
        int arms = 2 + (int) Math.round(p * 2);          //2 → 4 条螺旋臂
        double radius = 0.7d - 0.2d * p;                 //越蓄越收束（0.7 → 0.5）
        int perArm = 1 + (int) Math.round(p);            //每臂 1 → 2 点
        for (int arm = 0; arm < arms; arm++) {
            double base = phase * 0.26d + arm * (2d * Math.PI / arms);
            for (int i = 0; i < perArm; i++) {
                double a = base + i * 0.2d;
                //视线保护：竖向只允许在腰线以下微微起伏（不与镜头同高）
                double y = -0.45d + p * 0.45d + 0.1d * Math.sin(a * 1.7d);
                Location pnt = center.clone().add(Math.cos(a) * radius, y, Math.sin(a) * radius);
                world.spawnParticle(Particle.DUST, pnt, 1, 0d, 0d, 0d, 0d,
                        i % 2 == 0 ? RED_DUST : BLACK_DUST);
            }
        }
        if (p > 0.6d) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 2 + (int) Math.round(p * 2),
                    0.2d, 0.25d, 0.2d, 0.01d, CRIMSON);
        }
    }

    /**
     * **蓄力满**：刀气压满的清晰提示。
     *
     * <p>★ 2026-10-17 需求：<b>不要遮挡视线</b> —— 移除原来的 {@code FLASH}（全屏白闪），
     * 两圈光环全部压到<b>贴地高度</b>（0.08 / 0.30），粒子量也减半。
     */
    public static void iaiReady(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        ring(world, at.clone().add(0d, 0.08d, 0d), 1.35d, 20, RED_DUST, 0d);
        ring(world, at.clone().add(0d, 0.30d, 0d), 0.85d, 14, BLACK_DUST, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0d, 0.3d, 0d),
                14, 0.35d, 0.18d, 0.35d, 0.02d, CRIMSON);
        world.spawnParticle(Particle.CRIT, at.clone().add(0d, 0.4d, 0d),
                6, 0.4d, 0.18d, 0.4d, 0.06d);
    }

    // ───────── 拔刀 / 居合（按兑现层数分档）─────────

    /**
     * 拔刀左键攻击命中：横扫弧线。
     *
     * <p>★ 层数分档（2026-10-17 需求 4）：
     * <b>0 层</b> 素横扫；<b>1 层</b> 加深红地面环；<b>2 层</b> 加黑烟与渐变爆点；
     * <b>3 层</b> 再加白闪 + 火星 + 外圈黑影环（"越华丽"）。
     *
     * @param level 本次兑现的充能层数（0..3；越界夹紧）
     */
    public static void drawnSlash(World world, Location at, int level) {
        if (world == null || at == null) {
            return;
        }
        int lv = clampLevel(level);
        world.spawnParticle(Particle.SWEEP_ATTACK, at, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.DUST, at, 6 + 4 * lv, 0.4d, 0.3d, 0.4d, 0.03d, RED_DUST);
        if (lv >= 1) {
            world.spawnParticle(Particle.SQUID_INK, at, 2 + 2 * lv, 0.3d, 0.3d, 0.3d, 0.02d);
            ring(world, at, 0.9d + 0.2d * lv, 12 + 4 * lv, lv >= 3 ? RED_DUST : DEEP_RED_DUST, 0d);
        }
        if (lv >= 2) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 8 + 4 * lv,
                    0.45d, 0.4d, 0.45d, 0.05d, CRIMSON);
            world.spawnParticle(Particle.LARGE_SMOKE, at, 3 + lv, 0.4d, 0.35d, 0.4d, 0.01d);
        }
        if (lv >= 3) {
            world.spawnParticle(Particle.FLASH, at, 1, 0d, 0d, 0d, 0d, FLASH_RED);
            world.spawnParticle(Particle.CRIT, at, 10, 0.6d, 0.5d, 0.6d, 0.12d);
            ring(world, at, 2.0d, 22, BLACK_DUST, 0d);
            ring(world, at.clone().add(0d, 0.5d, 0d), 1.25d, 16, RED_DUST, 0d);
        }
    }

    /**
     * **居合**斩线：从起点到终点的一条黑红刀痕（防御触发 = 更亮）。
     *
     * <p>★ 2026-10-18 需求 4：<b>出刀粒子更华丽</b> —— 主线之外再加一条平行"影痕"、
     * 起点绽开、沿途火星，且所有档位都成立（旧版只在 2 层以上才有侧影）。
     *
     * @param level 本次兑现的充能层数（0..3；越界夹紧）
     */
    public static void iaiSlash(World world, Location from, Location to, boolean defensive, int level) {
        if (world == null || from == null || to == null) {
            return;
        }
        int lv = clampLevel(level);
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            return;
        }
        Vector flat = new Vector(delta.getX(), 0d, delta.getZ());
        Vector side = flat.lengthSquared() < 1.0E-6d
                ? new Vector(0d, 0d, 1d)
                : new Vector(-flat.getZ(), 0d, flat.getX()).normalize();
        Vector step = delta.clone().multiply(1d / Math.max(1d, length * 2d));
        int points = (int) Math.ceil(length * 2d);
        int perPoint = (defensive ? 2 : 1) + lv;
        for (int i = 0; i <= points; i++) {
            Location p = from.clone().add(step.clone().multiply(i)).add(0d, 1.0d, 0d);
            world.spawnParticle(Particle.DUST, p, perPoint, 0.06d, 0.08d, 0.06d, 0d,
                    defensive ? RED_DUST : DEEP_RED_DUST);
            //平行影痕：偏移 0.22 格，只隔点画 ⇒ 刀痕看起来是"双线"
            if (i % 2 == 0) {
                world.spawnParticle(Particle.DUST, p.clone().add(side.clone().multiply(0.22d)),
                        1, 0d, 0d, 0d, 0d, BLACK_DUST);
                world.spawnParticle(Particle.DUST, p.clone().add(side.clone().multiply(-0.22d)),
                        1, 0d, 0d, 0d, 0d, BLACK_DUST);
            }
            //沿途火星：每 4 点一簇
            if (i % 4 == 0) {
                world.spawnParticle(Particle.CRIT, p.clone().add(0d, 0.15d, 0d), 2, 0.12d, 0.15d, 0.12d, 0.05d);
            }
        }
        world.spawnParticle(Particle.SWEEP_ATTACK, to.clone().add(0d, 1.0d, 0d),
                (defensive ? 2 : 1) + (lv >= 2 ? 1 : 0), 0d, 0d, 0d, 0d);
        //起点绽开（出刀那一瞬）
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, from.clone().add(0d, 1d, 0d),
                10 + 4 * lv, 0.28d, 0.35d, 0.28d, 0.04d, CRIMSON);
        if (lv >= 3) {
            world.spawnParticle(Particle.FLASH, to.clone().add(0d, 1.0d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_RED);
            world.spawnParticle(Particle.CRIT, to.clone().add(0d, 1.0d, 0d), 12, 0.5d, 0.4d, 0.5d, 0.1d);
            ring(world, to.clone().add(0d, 1.0d, 0d), 1.8d, 20, RED_DUST, 0d);
        }
    }

    /**
     * **居合命中**（2026-10-18 需求 4：命中粒子更华丽）。
     *
     * <p>比拔刀的 {@link #imbalanceHit} 重一档：黑红渐变爆点 + 墨水影尘 + 火星 +
     * 地面血色环 + 胸口黑影环；2 层以上再加一记白闪（在**敌人身上**，不糊玩家镜头）。
     */
    public static void iaiHit(World world, Location at, int level) {
        if (world == null || at == null) {
            return;
        }
        int lv = clampLevel(level);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 18 + 6 * lv,
                0.45d, 0.5d, 0.45d, 0.06d, CRIMSON);
        world.spawnParticle(Particle.SQUID_INK, at, 8 + 2 * lv, 0.4d, 0.45d, 0.4d, 0.04d);
        world.spawnParticle(Particle.CRIT, at, 12 + 4 * lv, 0.5d, 0.5d, 0.5d, 0.14d);
        ring(world, at.clone().add(0d, 0.1d, 0d), 1.2d + 0.2d * lv, 18, RED_DUST, 0d);
        ring(world, at.clone().add(0d, 0.55d, 0d), 0.8d, 12, BLACK_DUST, 0d);
        if (lv >= 2) {
            world.spawnParticle(Particle.FLASH, at, 1, 0d, 0d, 0d, 0d, FLASH_RED);
        }
    }

    /** 充能拔刀的范围真伤圈（r=4；层数越高环越多）。 */
    public static void aoeCleave(World world, Location center, double radius, int level) {
        if (world == null || center == null) {
            return;
        }
        int lv = clampLevel(level);
        ring(world, center.clone().add(0d, 0.2d, 0d), radius, 24, RED_DUST, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 14 + 4 * lv,
                radius / 2d, 0.4d, radius / 2d, 0.04d, CRIMSON);
        if (lv >= 2) {
            ring(world, center.clone().add(0d, 0.45d, 0d), radius * 0.7d, 20, BLACK_DUST, 0d);
        }
        if (lv >= 3) {
            world.spawnParticle(Particle.FLASH, center.clone().add(0d, 0.6d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_RED);
            world.spawnParticle(Particle.CRIT, center, 10, radius / 2.4d, 0.35d, radius / 2.4d, 0.1d);
        }
    }

    /** 3 层充能防御成功后的 1 秒**无敌罩**。 */
    public static void immuneShield(World world, Location at, int phase) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        double angle = (phase % 30) * (Math.PI / 15d);
        for (int i = 0; i < 6; i++) {
            double a = angle + i * (Math.PI / 3d);
            world.spawnParticle(Particle.DUST, center.clone().add(Math.cos(a) * 0.75d, 0.15d * Math.sin(a * 2d), Math.sin(a) * 0.75d),
                    1, 0d, 0d, 0d, 0d, RED_DUST);
        }
    }

    // ───────── 技能一：榴弹 ─────────

    /** 榴弹飞行拖尾（黑烟 + 深红点 + 高频亮点，弹速越快越需要可读轨迹）。 */
    public static void grenadeTrail(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.SMOKE, at, 2, 0.08d, 0.08d, 0.08d, 0.005d);
        world.spawnParticle(Particle.DUST, at, 2, 0.1d, 0.1d, 0.1d, 0d, DEEP_RED_DUST);
        world.spawnParticle(Particle.CRIT, at, 1, 0.05d, 0.05d, 0.05d, 0.01d);
    }

    /**
     * **炮口后喷**（需求 3「有力量感」）：沿视线反向喷一圈黑烟 + 火星，且正前方一束亮红。
     *
     * @param dir 视线单位向量（可为 null ⇒ 只画身前）
     */
    public static void grenadeMuzzle(World world, Location eye, Vector dir) {
        if (world == null || eye == null) {
            return;
        }
        Vector forward = dir == null ? new Vector(0d, 0d, 1d) : dir.clone().normalize();
        Location muzzle = eye.clone().add(forward.clone().multiply(0.9d));
        Location back = eye.clone().add(forward.clone().multiply(-0.2d));
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, muzzle, 12, 0.18d, 0.18d, 0.18d, 0.06d, CRIMSON);
        world.spawnParticle(Particle.LARGE_SMOKE, back, 8, 0.22d, 0.18d, 0.22d, 0.05d);
        world.spawnParticle(Particle.SQUID_INK, back, 5, 0.18d, 0.15d, 0.18d, 0.04d);
        world.spawnParticle(Particle.CRIT, muzzle, 10, 0.16d, 0.16d, 0.16d, 0.2d);
        world.spawnParticle(Particle.ELECTRIC_SPARK, muzzle, 4, 0.12d, 0.12d, 0.12d, 0.08d);
    }

    /**
     * 榴弹爆开（5 格）—— 需求 3「落地的粒子冲击力」。
     *
     * <p>加了四层：白闪（瞬间）、声波环（冲击波）、三层地面扩散环（由外到内）、
     * 上扬的扬尘柱 + 火星（爆点的"碎屑"）。比原版只有一团渐变要"砸"得多。
     */
    public static void grenadeExplode(World world, Location at, double radius) {
        if (world == null || at == null) {
            return;
        }
        double r = Math.max(0.5d, radius);
        Location ground = at.clone().add(0d, 0.12d, 0d);
        world.spawnParticle(Particle.FLASH, at.clone().add(0d, 0.6d, 0d), 1, 0d, 0d, 0d, 0d, FLASH_RED);
        world.spawnParticle(Particle.SONIC_BOOM, at.clone().add(0d, 0.4d, 0d), 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 42, r / 2.4d, 0.7d, r / 2.4d, 0.09d, CRIMSON);
        world.spawnParticle(Particle.LARGE_SMOKE, at, 22, r / 3.0d, 0.6d, r / 3.0d, 0.04d);
        world.spawnParticle(Particle.SQUID_INK, at, 14, r / 3.4d, 0.5d, r / 3.4d, 0.06d);
        world.spawnParticle(Particle.CRIT, at, 16, r / 2.6d, 0.5d, r / 2.6d, 0.14d);
        //三层地面冲击环（外 → 中 → 内）
        ring(world, ground, r, 34, RED_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.25d, 0d), r * 0.62d, 26, DEEP_RED_DUST, 0d);
        ring(world, ground.clone().add(0d, 0.5d, 0d), r * 0.3d, 18, BLACK_DUST, 0d);
        //扬尘柱：沿圈上扬
        for (int i = 0; i < 16; i++) {
            double a = (i / 16d) * Math.PI * 2d;
            double rr = r * (0.2d + 0.55d * ((i % 4) / 3d));
            Location p = ground.clone().add(Math.cos(a) * rr, 0.2d + (i % 3) * 0.55d, Math.sin(a) * rr);
            world.spawnParticle(Particle.CLOUD, p, 1, 0d, 0.02d, 0d, 0.01d);
        }
    }

    /** 稳态粒子场：边界环（每 2 刻一段错峰绘制 ⇒ 省粒子又看着连续）。 */
    public static void fieldRing(World world, Location center, double radius, int phase) {
        if (world == null || center == null) {
            return;
        }
        int points = 20;
        int offset = Math.floorMod(phase * 2, points);
        for (int i = 0; i < 6; i++) {
            double a = ((i + offset) % points) * (2d * Math.PI / points);
            Location p = center.clone().add(Math.cos(a) * radius, 0.12d, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, i % 2 == 0 ? DEEP_RED_DUST : BLACK_DUST);
        }
        world.spawnParticle(Particle.SMOKE, center.clone().add(0d, 0.3d, 0d), 1, radius / 2d, 0.1d, radius / 2d, 0.002d);
    }

    /** 稳态场每秒结算：场内红光一闪。 */
    public static void fieldTickFlash(World world, Location center, double radius) {
        if (world == null || center == null) {
            return;
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center.clone().add(0d, 0.4d, 0d),
                14, radius / 2.4d, 0.2d, radius / 2.4d, 0.01d, CRIMSON);
    }

    /** 自己身处稳态场：速度 / 再生到账。 */
    public static void fieldSelfBuff(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at.clone().add(0d, 1.0d, 0d), 4, 0.3d, 0.5d, 0.3d, 0.01d, RED_DUST);
        world.spawnParticle(Particle.ENCHANTED_HIT, at.clone().add(0d, 1.2d, 0d), 2, 0.2d, 0.3d, 0.2d, 0.01d);
    }

    // ───────── 技能二 / 大招 ─────────

    /** 瞬影连斩：突进拖尾（`level` = 命中时持有的笼手层数，越高越华丽）。 */
    public static void dashTrail(World world, Location from, Location to, int level) {
        if (world == null || from == null || to == null) {
            return;
        }
        int lv = clampLevel(level);
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            return;
        }
        Vector step = delta.clone().multiply(1d / Math.max(1d, length * 1.5d));
        int points = (int) Math.ceil(length * 1.5d);
        for (int i = 0; i <= points; i++) {
            Location p = from.clone().add(step.clone().multiply(i)).add(0d, 1.0d, 0d);
            world.spawnParticle(Particle.DUST, p, 1, 0.05d, 0.08d, 0.05d, 0d, RED_DUST);
            if (i % 3 == 0) {
                world.spawnParticle(Particle.SQUID_INK, p, 1, 0.05d, 0.05d, 0.05d, 0.01d);
            }
            if (lv >= 2 && i % 2 == 0) {
                world.spawnParticle(Particle.DUST_COLOR_TRANSITION, p, 1, 0.04d, 0.04d, 0.04d, 0d, CRIMSON);
            }
        }
        if (lv >= 3) {
            world.spawnParticle(Particle.SWEEP_ATTACK, to.clone().add(0d, 1.0d, 0d), 2, 0.3d, 0.2d, 0.3d, 0d);
            world.spawnParticle(Particle.CRIT, to.clone().add(0d, 1.0d, 0d), 8, 0.4d, 0.3d, 0.4d, 0.08d);
        }
    }

    /** 瞬移（大招"瞬移至其身后"）：起点影尘收束 + 终点黑红绽开。 */
    public static void blink(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        world.spawnParticle(Particle.SQUID_INK, from.clone().add(0d, 1d, 0d), 12, 0.3d, 0.5d, 0.3d, 0.03d);
        world.spawnParticle(Particle.DUST, from.clone().add(0d, 1d, 0d), 8, 0.3d, 0.5d, 0.3d, 0.02d, BLACK_DUST);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, to.clone().add(0d, 1d, 0d), 16, 0.35d, 0.5d, 0.35d, 0.05d, CRIMSON);
    }

    /** 剑刃风暴每一段：以自身为心的黑红斩环。 */
    public static void stormStage(World world, Location at, double radius, int stage) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        world.spawnParticle(Particle.SWEEP_ATTACK, center, 3, 0.6d, 0.3d, 0.6d, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 26 + 8 * Math.max(0, stage),
                radius / 2.4d, 0.5d, radius / 2.4d, 0.06d, CRIMSON);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 10, radius / 3.4d, 0.4d, radius / 3.4d, 0.03d);
        ring(world, at.clone().add(0d, 0.2d, 0d), radius * 0.8d, 28, RED_DUST, 0d);
    }

    /** 剑刃风暴收尾：黑暗降临的大黑红环。 */
    public static void stormFinal(World world, Location at, double radius) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, at.clone().add(0d, 1d, 0d),
                60, radius / 2.2d, 1.0d, radius / 2.2d, 0.08d, CRIMSON);
        world.spawnParticle(Particle.SQUID_INK, at.clone().add(0d, 1d, 0d),
                40, radius / 2.6d, 0.8d, radius / 2.6d, 0.06d);
        world.spawnParticle(Particle.LARGE_SMOKE, at.clone().add(0d, 0.5d, 0d),
                24, radius / 2.8d, 0.4d, radius / 2.8d, 0.02d);
        ring(world, at.clone().add(0d, 0.2d, 0d), radius, 36, BLACK_DUST, 0d);
    }

    // ───────── 大招锁定提示（2026-10-17 需求 8）─────────

    /**
     * **入范围锁定**：两枚红黑菱形粒子在敌人身上翻滚（每刻调用）。
     *
     * <p>菱形 = 在局部 XY 平面取四个顶点（再取四条边中点让轮廓可读），
     * 先绕 Z 轴 {@code roll}（翻滚），再绕 Y 轴 {@code yaw}（公转），最后平移到位。
     * {@code phase} 逐刻递增 ⇒ 两个角度都在变 ⇒ "随机翻滚"的观感。
     */
    public static void stormLock(World world, Location at, int phase, int seed) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.05d, 0d);
        //红菱形与黑菱形反向翻滚、错开相位 ⇒ 看起来像两枚互相缠绕的锁
        diamond(world, center, 0.95d, phase * 0.13d + seed * 1.7d, phase * 0.27d + seed, RED_DUST);
        diamond(world, center, 0.8d, -phase * 0.11d + seed * 0.9d + Math.PI, -phase * 0.23d + seed * 2.1d, BLACK_DUST);
    }

    /** 锁定成立的一瞬（敌人刚踏进 r8）：一个收束环 + 两道勾。 */
    public static void stormLockOn(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.05d, 0d);
        ring(world, at.clone().add(0d, 0.15d, 0d), 1.25d, 18, RED_DUST, 0d);
        world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 14, 0.3d, 0.4d, 0.3d, 0.02d, CRIMSON);
        world.spawnParticle(Particle.CRIT, center, 6, 0.35d, 0.35d, 0.35d, 0.04d);
    }

    // ───────── 笼手 / 提示 ─────────

    /** 笼手充能到账（爆发奖励 / 连斩命中 / 自然回复）。 */
    public static void chargeStored(World world, Location at, boolean fromBurst) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        world.spawnParticle(Particle.DUST, center, fromBurst ? 14 : 8, 0.4d, 0.5d, 0.4d, 0.02d, RED_DUST);
        world.spawnParticle(Particle.SQUID_INK, center, fromBurst ? 8 : 4, 0.3d, 0.4d, 0.3d, 0.02d);
        if (fromBurst) {
            world.spawnParticle(Particle.DUST_COLOR_TRANSITION, center, 10, 0.3d, 0.5d, 0.3d, 0.04d, CRIMSON);
        }
    }

    // ───────── 内部工具 ─────────

    /** 画一枚菱形（4 顶点 + 4 边中点）。 */
    private static void diamond(World world, Location center, double size,
                                double yaw, double roll, Particle.DustOptions option) {
        double cosYaw = Math.cos(yaw);
        double sinYaw = Math.sin(yaw);
        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);
        for (int i = 0; i < 8; i++) {
            //顶点在 i 偶数，边中点在 i 奇数
            double angle = Math.PI / 4d * i;
            double scale = (i % 2 == 0) ? size : size * 0.7071067811865476d;
            double lx = Math.cos(angle) * scale;
            double ly = Math.sin(angle) * scale;
            double rx = lx * cosRoll - ly * sinRoll;
            double ry = lx * sinRoll + ly * cosRoll;
            double wx = rx * cosYaw;
            double wz = -rx * sinYaw;
            world.spawnParticle(Particle.DUST, center.clone().add(wx, ry, wz), 1, 0d, 0d, 0d, 0d, option);
        }
    }

    /** 水平圆环（默认铺在给定高度上）。 */
    private static void ring(World world, Location center, double radius, int points, Particle.DustOptions option,
                             double extraY) {
        for (int i = 0; i < points; i++) {
            double a = i * (2d * Math.PI / points);
            Location p = center.clone().add(Math.cos(a) * radius, extraY, Math.sin(a) * radius);
            world.spawnParticle(Particle.DUST, p, 1, 0d, 0d, 0d, 0d, option);
        }
    }

    /** 0..1 夹紧（纯函数，供蓄力进度使用）。 */
    public static double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }

    /** 0..3 夹紧（层数分档）。 */
    public static int clampLevel(int level) {
        return Math.max(0, Math.min(3, level));
    }
}
