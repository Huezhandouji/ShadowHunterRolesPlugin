package com.shadowHunterRolesPlugin.roleComponent.custom.raven;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

/**
 * 「绯红渡鸦 · 林晨霜」的**纯粒子工具**（无状态、零组件依赖、零资源文件）。
 *
 * <p>职责单一：把需求的观感画出来。边界：不读玩家状态、不写状态、不注册任务、不调用组件；
 * 传入 {@code null} 一律静默返回。
 *
 * <h2>★ 粒子 data 契约（改本文件前必读）</h2>
 * Paper 1.21.11 里 {@link Particle#getDataType()} 非 {@code Void} 的粒子**必须传 data**，
 * 否则运行期抛 {@code IllegalArgumentException: missing required data class …}
 * ⇒ 组件在 {@code onCast} 抛异常 ⇒ <b>整个角色被隔离下线</b>。
 * 本文件里带 data 的粒子只有 {@link Particle#DUST}（{@code DustOptions}），
 * 且**一律走"data 在末位实参"的那个重载**；其余全部是无 data 粒子。
 * 工程闸门 {@code ParticleDataContractTest} 会扫描本文件并盯住这一条。
 *
 * <h2>★ 为什么不用 {@link Particle#FLASH}</h2>
 * {@code FLASH} 的 data 是 {@code org.bukkit.Color}（与 {@code ENTITY_EFFECT} 同族），
 * 忘记传 data 就是上面那条"整角色下线"。**要爆闪用 {@code EXPLOSION} / {@code CRIT} 叠亮色 DUST**，
 * 本文件因此完全不出现 {@code FLASH}。
 */
public final class RavenVfx {

    private RavenVfx() {
    }

    // ───────── 配色（绯红渡鸦：血绯 + 铁灰 + 白）─────────

    /** 主色：绯红（枪口火光 / 弹道 / 冲击的核心色）。 */
    public static final Color CRIMSON = Color.fromRGB(198, 26, 46);

    /** 亮绯（爆闪中心，比 {@link #CRIMSON} 更亮更粉，用来做"炸开"的一瞬）。 */
    public static final Color CRIMSON_LIGHT = Color.fromRGB(255, 92, 104);

    /** 锁定红（需求："红色的锁定粒子"⇒ 纯度更高的红，与主色区分）。 */
    public static final Color LOCK_RED = Color.fromRGB(255, 36, 36);

    /** 渡鸦羽色：近黑的冷灰（浮空 / 俯冲的气质色）。 */
    public static final Color RAVEN_DARK = Color.fromRGB(38, 38, 48);

    /** 义体钢灰（风弹 / 尾焰）。 */
    public static final Color STEEL = Color.fromRGB(176, 186, 198);

    /** 净白（穿刺 / 装填 / 高光）。 */
    public static final Color WHITE = Color.fromRGB(245, 245, 250);

    /** 主粒子尺寸。 */
    private static final float SIZE_MAIN = 1.05f;

    /** 细碎粒子尺寸（拖尾 / 边缘）。 */
    private static final float SIZE_FINE = 0.65f;

    // ───────── ① 义体与散华左轮：枪口 / 弹道 / 命中 ─────────

    /**
     * **枪口火光**（需求 2026-10-08 同族："射出时在枪口位置出现单个爆炸粒子"）。
     *
     * @param eye    眼睛位置
     * @param facing 视线方向（未归一化也可）
     */
    public static void ravenMuzzleFlash(World world, Location eye, Vector facing) {
        if (world == null || eye == null || facing == null) {
            return;
        }
        Location muzzle = eye.clone().add(facing.clone().normalize().multiply(1.1d));
        world.spawnParticle(Particle.EXPLOSION, muzzle, 1, 0d, 0d, 0d, 0d);
        dust(world, muzzle, 8, 0.12d, CRIMSON_LIGHT, SIZE_MAIN);
        dust(world, muzzle, 4, 0.18d, STEEL, SIZE_FINE);
    }

    /** **子弹飞行拖尾**（每刻在弹头位置画一小簇；由组件逐刻调用）。 */
    public static void ravenBulletTrail(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        dust(world, at, 2, 0.05d, CRIMSON, SIZE_FINE);
        world.spawnParticle(Particle.SMOKE, at, 1, 0.03d, 0.03d, 0.03d, 0d);
    }

    /** 子弹命中敌人体（绯红火花 + 一记白色爆点）。 */
    public static void ravenBulletHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.CRIT, at, 12, 0.25d, 0.25d, 0.25d, 0.1d);
        world.spawnParticle(Particle.ENCHANTED_HIT, at, 8, 0.2d, 0.2d, 0.2d, 0.08d);
        dust(world, at, 10, 0.22d, CRIMSON, SIZE_MAIN);
        dust(world, at, 4, 0.1d, WHITE, SIZE_FINE);
    }

    /** 子弹撞墙消失（需求："撞墙消失"）—— 一小片灰烟 + 石屑。 */
    public static void ravenBulletWall(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.SMOKE, at, 6, 0.12d, 0.12d, 0.12d, 0.02d);
        dust(world, at, 8, 0.16d, RAVEN_DARK, SIZE_FINE);
    }

    // ───────── ② 换弹 ─────────

    /** 每装一发：在腰间画一小簇白色 + 钢灰（与 {@code RavenSound#ravenReloadClip} 同步）。 */
    public static void ravenReloadClip(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        dust(world, at, 6, 0.18d, WHITE, SIZE_FINE);
        dust(world, at, 4, 0.22d, STEEL, SIZE_FINE);
        world.spawnParticle(Particle.ENCHANT, at, 5, 0.25d, 0.25d, 0.25d, 0.35d);
    }

    /** 换弹被打断：一簇散开的灰烟（需求："点击左键视为打断换弹操作"）。 */
    public static void ravenReloadInterrupt(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.POOF, at, 6, 0.2d, 0.2d, 0.2d, 0.02d);
        dust(world, at, 6, 0.2d, RAVEN_DARK, SIZE_FINE);
    }

    /** 瞬间补齐弹药（技能二地面形态）—— 一圈向内的白色收束。 */
    public static void ravenFullReloadBurst(World world, Location bodyMid) {
        if (world == null || bodyMid == null) {
            return;
        }
        world.spawnParticle(Particle.ENCHANT, bodyMid, 24, 0.55d, 0.55d, 0.55d, 0.9d);
        ring(world, bodyMid.clone().subtract(0d, 0.4d, 0d), 0.85d, 16, WHITE, SIZE_FINE);
    }

    // ───────── ③ 浮空机动 ─────────

    /**
     * **浮空环**：脚下两圈反向旋转的绯红细环（每刻推进 {@code phase} ⇒ 看起来在转）。
     *
     * <p>★ 这是**几何环**（径向对称）⇒ 可以安全推进相位；位图才不能自转（工程既有口径）。
     */
    public static void ravenHoverRing(World world, Location feet, double phase) {
        if (world == null || feet == null) {
            return;
        }
        Location base = feet.clone().add(0d, 0.12d, 0d);
        ringPhase(world, base, 0.95d, 20, phase, CRIMSON, SIZE_FINE);
        ringPhase(world, base.clone().add(0d, 0.28d, 0d), 0.62d, 14, -phase * 0.7d, STEEL, SIZE_FINE);
        world.spawnParticle(Particle.END_ROD, base, 1, 0.25d, 0.05d, 0.25d, 0.005d);
    }

    /** 进入 / 刷新浮空机动：一圈向外炸开的绯红（"义体启动"的一瞬）。 */
    public static void ravenHoverEnterBurst(World world, Location feet) {
        if (world == null || feet == null) {
            return;
        }
        ring(world, feet.clone().add(0d, 0.15d, 0d), 1.5d, 28, CRIMSON_LIGHT, SIZE_MAIN);
        world.spawnParticle(Particle.GUST, feet, 1, 0d, 0.1d, 0d, 0d);
        dust(world, feet.clone().add(0d, 0.5d, 0d), 14, 0.5d, RAVEN_DARK, SIZE_FINE);
    }

    /** 脱离浮空（超时 / 触地）。 */
    public static void ravenHoverExitBurst(World world, Location feet) {
        if (world == null || feet == null) {
            return;
        }
        world.spawnParticle(Particle.CLOUD, feet, 10, 0.4d, 0.1d, 0.4d, 0.02d);
        dust(world, feet, 10, 0.45d, RAVEN_DARK, SIZE_FINE);
    }

    // ───────── ④ 空中主动机动（丢出主武器前冲）─────────

    /** 前冲拖尾：从起点到终点的一条绯红拉丝 + 沿途钢灰。 */
    public static void ravenAirDashTrail(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        line(world, from.clone().add(0d, 1d, 0d), to.clone().add(0d, 1d, 0d), 0.35d, CRIMSON, SIZE_MAIN, 2);
        line(world, from.clone().add(0d, 0.4d, 0d), to.clone().add(0d, 0.4d, 0d), 0.5d, STEEL, SIZE_FINE, 1);
    }

    /** 快速装填（空中）—— 腰间两簇立即亮起的白点。 */
    public static void ravenQuickLoad(World world, Location bodyMid) {
        if (world == null || bodyMid == null) {
            return;
        }
        dust(world, bodyMid, 12, 0.35d, WHITE, SIZE_MAIN);
        world.spawnParticle(Particle.ENCHANTED_HIT, bodyMid, 8, 0.3d, 0.3d, 0.3d, 0.15d);
    }

    // ───────── ⑤ 锁定（蓄力 / 十字加圆圈）─────────

    /**
     * **蓄力进度环**（需求："右键将在 0.8S 内蓄力"）—— 半径随蓄力收缩的红环，
     * 让"还有多久锁上"看得见。
     *
     * @param progress 0..1（1 = 蓄满）
     */
    public static void ravenLockCharge(World world, Location feet, double progress) {
        if (world == null || feet == null) {
            return;
        }
        double clamped = Math.max(0d, Math.min(1d, progress));
        double radius = 2.4d - 1.5d * clamped;
        Location base = feet.clone().add(0d, 0.15d, 0d);
        ring(world, base, radius, 24, LOCK_RED, SIZE_FINE);
        world.spawnParticle(Particle.SCULK_CHARGE_POP, feet.clone().add(0d, 1d, 0d), 1,
                0.3d, 0.3d, 0.3d, 0d);
    }

    /**
     * **锁定标记：十字加圆圈**（需求逐字："十字加圆圈的红色锁定粒子在目标与 raven 链接的中点"）。
     *
     * @param raven  渡鸦自己的位置（取其头顶）
     * @param target 被锁定的敌人位置（取其胸口）
     */
    public static void ravenLockMarker(World world, Location raven, Location target, double phase) {
        if (world == null || raven == null || target == null) {
            return;
        }
        Location mid = raven.clone().add(0d, 0.5d, 0d)
                .add(target.clone().add(0d, 1d, 0d)).multiply(0.5d);
        reticle(world, mid, 0.55d, phase);
    }

    /** 锁定确立：目标身上炸开一圈红（"被盯上"的一瞬）。 */
    public static void ravenLockConfirm(World world, Location target) {
        if (world == null || target == null) {
            return;
        }
        Location chest = target.clone().add(0d, 1d, 0d);
        ring(world, chest, 0.8d, 20, LOCK_RED, SIZE_MAIN);
        reticle(world, chest, 0.7d, 0d);
        world.spawnParticle(Particle.ENCHANTED_HIT, chest, 10, 0.25d, 0.25d, 0.25d, 0.1d);
    }

    /** 锁定射击的弹道（从枪口直连目标胸口）。 */
    public static void ravenLockBeam(World world, Location muzzle, Location targetChest) {
        if (world == null || muzzle == null || targetChest == null) {
            return;
        }
        line(world, muzzle, targetChest, 0.4d, CRIMSON_LIGHT, SIZE_FINE, 1);
    }

    // ───────── ⑥ 技能一：升空突袭-风弹 ─────────

    /** 风弹突进的路径（横向绯红螺旋 + 钢灰尾迹）。 */
    public static void ravenWindBombPath(World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return;
        }
        spiral(world, from, to, 0.45d, 3, CRIMSON, SIZE_FINE);
        line(world, from, to, 0.4d, STEEL, SIZE_FINE, 1);
    }

    /** 风弹路径命中敌人。 */
    public static void ravenWindBombHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.spawnParticle(Particle.GUST, at, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.CRIT, at, 10, 0.3d, 0.3d, 0.3d, 0.08d);
        dust(world, at, 10, 0.3d, CRIMSON_LIGHT, SIZE_MAIN);
    }

    // ───────── ⑦ 技能二：死亡之眼-末影之眼 ─────────

    /** 地面形态：跃起时脚下的一圈紫黑（末影之眼气质）。 */
    public static void ravenDeathEyeJumpBurst(World world, Location feet) {
        if (world == null || feet == null) {
            return;
        }
        ring(world, feet.clone().add(0d, 0.1d, 0d), 1.0d, 20, RAVEN_DARK, SIZE_MAIN);
        world.spawnParticle(Particle.REVERSE_PORTAL, feet.clone().add(0d, 1d, 0d), 30,
                0.5d, 0.6d, 0.5d, 0.02d);
        world.spawnParticle(Particle.SCULK_SOUL, feet.clone().add(0d, 0.6d, 0d), 12,
                0.4d, 0.3d, 0.4d, 0.01d);
    }

    /** 散射命中回血：目标身上一圈白色收束 + 一道回到自己的细线。 */
    public static void ravenLifeSteal(World world, Location target, Location self) {
        if (world == null || target == null || self == null) {
            return;
        }
        Location chest = target.clone().add(0d, 1d, 0d);
        dust(world, chest, 8, 0.25d, WHITE, SIZE_MAIN);
        line(world, chest, self.clone().add(0d, 1d, 0d), 0.5d, CRIMSON_LIGHT, SIZE_FINE, 1);
    }

    // ───────── ⑧ 技能三：涅斯捷罗夫筋斗-沉重核心 ─────────

    /**
     * **落地区域标记：一圈 + 一个叉**（需求："手持该技能将给出大致落地区域标记圈和叉"）。
     *
     * <p>由技能在 {@code update()} 里每刻画在**预测落点**上，让玩家知道会砸到哪。
     *
     * @param center 预测落点中心
     * @param radius 标记半径（格）
     */
    public static void ravenLandingMarker(World world, Location center, double radius) {
        if (world == null || center == null) {
            return;
        }
        Location flat = center.clone();
        ring(world, flat, radius, 32, CRIMSON, SIZE_FINE);
        crossFlat(world, flat, radius * 0.85d, CRIMSON, SIZE_FINE);
        dust(world, flat, 6, 0.15d, CRIMSON_LIGHT, SIZE_FINE);
    }

    /** 俯冲拖尾（每刻在玩家身上画一簇：近黑羽色 + 绯红边缘）。 */
    public static void ravenSomersaultDiveTrail(World world, Location at, double phase) {
        if (world == null || at == null) {
            return;
        }
        dust(world, at, 5, 0.28d, RAVEN_DARK, SIZE_MAIN);
        dust(world, at, 3, 0.34d, CRIMSON, SIZE_FINE);
        ringPhase(world, at.clone().subtract(0d, 1d, 0d), 0.7d, 12, phase, CRIMSON_LIGHT, SIZE_FINE);
    }

    /** 沉重核心落地：r7 冲击（双重环 + 十字 + 大量扬尘）。 */
    public static void ravenSomersaultLandBurst(World world, Location center, double radius) {
        if (world == null || center == null) {
            return;
        }
        world.spawnParticle(Particle.EXPLOSION_EMITTER, center, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.GUST_EMITTER_LARGE, center, 1, 0d, 0d, 0d, 0d);
        ring(world, center, radius, 40, CRIMSON, SIZE_MAIN);
        ring(world, center.clone().add(0d, 0.35d, 0d), radius * 0.6d, 28, CRIMSON_LIGHT, SIZE_FINE);
        crossFlat(world, center, radius * 0.9d, WHITE, SIZE_MAIN);
        world.spawnParticle(Particle.LARGE_SMOKE, center, 24, radius * 0.5d, 0.3d, radius * 0.5d, 0.02d);
        dust(world, center.clone().add(0d, 0.6d, 0d), 30, radius * 0.5d, RAVEN_DARK, SIZE_FINE);
    }

    // ───────── ⑨ 落地冲击（被动）─────────

    /**
     * 落地冲击 r5（比技能三轻一档：单环 + 短十字）
     * —— 2026-10-11 追加"重锤落地攻击"的观感（尘浪 + 爆点 + 碎屑上抛）。
     */
    public static void ravenLandingImpact(World world, Location center, double radius) {
        if (world == null || center == null) {
            return;
        }
        //重锤一击：地面尘浪 + 两处爆点 + 上抛的碎屑
        world.spawnParticle(Particle.GUST_EMITTER_LARGE, center, 1, 0d, 0d, 0d, 0d);
        world.spawnParticle(Particle.GUST_EMITTER_SMALL, center, 2, radius * 0.4d, 0d, radius * 0.4d, 0d);
        world.spawnParticle(Particle.EXPLOSION, center.clone().add(0d, 0.25d, 0d), 2,
                radius * 0.25d, 0d, radius * 0.25d, 0d);
        world.spawnParticle(Particle.SMALL_GUST, center.clone().add(0d, 0.4d, 0d), 3,
                radius * 0.3d, 0d, radius * 0.3d, 0d);
        ring(world, center, radius, 32, CRIMSON, SIZE_MAIN);
        crossFlat(world, center, radius * 0.75d, WHITE, SIZE_FINE);
        ring(world, center.clone().add(0d, 0.22d, 0d), radius * 0.55d, 20, CRIMSON_LIGHT, SIZE_FINE);
        dust(world, center.clone().add(0d, 0.4d, 0d), 16, radius * 0.45d, RAVEN_DARK, SIZE_FINE);
        //碎屑上抛：CRIT 往上打才有"被砸起来"的重量
        world.spawnParticle(Particle.CRIT, center.clone().add(0d, 0.3d, 0d), 14,
                radius * 0.35d, 0.35d, radius * 0.35d, 0.35d);
    }

    /** 从浮空落地获得速度 6：脚下一圈向外的绯红加速环。 */
    public static void ravenLandingHasteBurst(World world, Location feet) {
        if (world == null || feet == null) {
            return;
        }
        ring(world, feet.clone().add(0d, 0.08d, 0d), 1.3d, 22, CRIMSON_LIGHT, SIZE_FINE);
        world.spawnParticle(Particle.SMALL_GUST, feet.clone().add(0d, 0.3d, 0d), 1, 0d, 0d, 0d, 0d);
    }

    /** 机动充能到手（池里多一层）—— 一圈收拢进身体的绯红。 */
    public static void ravenChargeStored(World world, Location bodyMid) {
        if (world == null || bodyMid == null) {
            return;
        }
        ring(world, bodyMid, 1.1d, 20, CRIMSON_LIGHT, SIZE_FINE);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, bodyMid, 8, 0.3d, 0.4d, 0.3d, 0.35d);
    }

    /** 击杀即刻充能（需求："若击杀敌人则立即将主动机动模式充能一层"）。 */
    public static void ravenChargeFromKill(World world, Location bodyMid) {
        if (world == null || bodyMid == null) {
            return;
        }
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, bodyMid, 16, 0.4d, 0.5d, 0.4d, 0.5d);
        dust(world, bodyMid, 14, 0.4d, CRIMSON_LIGHT, SIZE_MAIN);
    }

    // ───────── ⑩ 基础图元（内部复用；全部"data 在末位"）─────────

    /** 一簇 DUST（**data 在末位实参** —— 契约的唯一书写点）。 */
    private static void dust(World world, Location at, int count, double spread, Color color, float size) {
        if (world == null || at == null || color == null) {
            return;
        }
        world.spawnParticle(Particle.DUST, at, count, spread, spread, spread, 0d,
                new Particle.DustOptions(color, size));
    }

    /** 水平圆环（{@code phase = 0} 时起点在 +X）。 */
    private static void ring(World world, Location center, double radius, int points,
                             Color color, float size) {
        ringPhase(world, center, radius, points, 0d, color, size);
    }

    /** 带相位的水平圆环（推进 {@code phase} ⇒ 视觉上自转；几何环可安全自转）。 */
    private static void ringPhase(World world, Location center, double radius, int points,
                                  double phase, Color color, float size) {
        if (world == null || center == null || color == null) {
            return;
        }
        int n = Math.max(3, points);
        for (int i = 0; i < n; i++) {
            double a = phase + (2d * Math.PI * i) / n;
            Location at = center.clone().add(Math.cos(a) * radius, 0d, Math.sin(a) * radius);
            dust(world, at, 1, 0d, color, size);
        }
    }

    /** 水平十字（沿 X 与 Z 两轴）。 */
    private static void crossFlat(World world, Location center, double half, Color color, float size) {
        if (world == null || center == null || color == null) {
            return;
        }
        for (double d = -half; d <= half + 1.0E-6d; d += 0.25d) {
            dust(world, center.clone().add(d, 0d, 0d), 1, 0d, color, size);
            dust(world, center.clone().add(0d, 0d, d), 1, 0d, color, size);
        }
    }

    /**
     * **瞄准镜式十字加圆圈**（需求原话的那个形状）：水平圆环 + 环内水平十字 + 四角刻度。
     *
     * @param phase 推进相位 ⇒ 圆环自转（十字不动，读起来仍像准镜）
     */
    private static void reticle(World world, Location center, double radius, double phase) {
        if (world == null || center == null) {
            return;
        }
        ringPhase(world, center, radius, 18, phase, LOCK_RED, SIZE_MAIN);
        crossFlat(world, center, radius * 0.75d, LOCK_RED, SIZE_FINE);
        //四角刻度：让"锁定"比"普通准镜"更硬朗
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4d + i * Math.PI / 2d;
            Location tick = center.clone().add(Math.cos(a) * radius * 1.12d, 0d, Math.sin(a) * radius * 1.12d);
            dust(world, tick, 3, 0.06d, LOCK_RED, SIZE_MAIN);
        }
    }

    /** 两点之间的一条点线（{@code density} = 每格画几簇）。 */
    private static void line(World world, Location from, Location to, double step,
                             Color color, float size, int density) {
        if (world == null || from == null || to == null || color == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            return;
        }
        Vector unit = delta.clone().multiply(1d / length);
        int perStep = Math.max(1, density);
        for (double t = 0d; t <= length + 1.0E-6d; t += step) {
            Location at = from.clone().add(unit.clone().multiply(t));
            dust(world, at, perStep, 0.04d, color, size);
        }
    }

    /**
     * **螺旋**（绕"两点连线轴"旋转）：风弹 / 透骨钉那类"螺旋射线"的通用画法。
     *
     * @param radius 螺旋半径（格）
     * @param turns  绕轴转多少圈
     */
    private static void spiral(World world, Location from, Location to, double radius, double turns,
                               Color color, float size) {
        if (world == null || from == null || to == null || color == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            return;
        }
        Vector axis = delta.clone().multiply(1d / length);
        //构造与轴垂直的两个基向量
        Vector helper = Math.abs(axis.getY()) > 0.9d ? new Vector(1d, 0d, 0d) : new Vector(0d, 1d, 0d);
        Vector u = axis.clone().crossProduct(helper).normalize();
        Vector v = axis.clone().crossProduct(u).normalize();

        double step = 0.35d;
        for (double t = 0d; t <= length + 1.0E-6d; t += step) {
            double angle = (t / length) * turns * 2d * Math.PI;
            double ox = (Math.cos(angle) * u.getX() + Math.sin(angle) * v.getX()) * radius;
            double oy = (Math.cos(angle) * u.getY() + Math.sin(angle) * v.getY()) * radius;
            double oz = (Math.cos(angle) * u.getZ() + Math.sin(angle) * v.getZ()) * radius;
            Location at = from.clone().add(axis.clone().multiply(t)).add(ox, oy, oz);
            dust(world, at, 1, 0d, color, size);
        }
    }
}
