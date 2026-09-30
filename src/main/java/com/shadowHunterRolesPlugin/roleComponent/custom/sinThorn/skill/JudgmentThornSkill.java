package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.SinThornVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.List;

/**
 * 「罪棘」技能之三：审判孤刺（紫水晶碎片）。
 *
 * <p>行为：引导 2 秒后，对 20 格内所有敌人进行一次刺击 ——
 * 12 点魔法伤害；若目标承受前血量低于 20 则直接处决。冷却 40 秒（800 刻），不耗能。
 *
 * <p>引导完毕后释放：按下技能只是开始引导；伤害、尖牙形状特效与冷却
 * 都发生在引导结束（{@code release()}）那一刻 —— 引导期间快捷栏图标仍是"就绪"，
 * 靠内部的 {@code channeling} 标志挡住重复施放（冷却这时候还没起算，挡不住）。
 *
 * <p>特效与引导期约束：
 * <ul>
 *   <li>引导时不能移动：每帧刷新缓慢 255（移速归零）并清掉水平速度（含击退），
 *       另外一旦被推出锚点 0.8 格就拽回来 —— 双保险，确保"钉在原地"；</li>
 *   <li>同时获得抗性 2：每帧刷新 {@code RESISTANCE} 增幅 1（= 抗性 II）；</li>
 *   <li>自身四个方向立着 4 个静止十字架（末地烛粒子，位置不随帧变化）；</li>
 *   <li>大招范围边缘（20 格）一圈多个旋转的单个爆炸粒子（每处只 spawn 一颗，即"单个"）；</li>
 *   <li>范围内所有敌方身上持续冒单个爆炸粒子 + 紫色引导特效；</li>
 *   <li>引导结束释放：在自身位置升起一个由"单个爆炸粒子"排成的尖牙形状，
 *       并在每个被命中的敌人身上补一发小尖牙。</li>
 * </ul>
 * <p>缓慢 / 抗性走 {@code BuffComponent}（自己身上的效果由账本统一管，角色清除时一并回收）；
 * 敌人身上的粒子是纯装饰，不涉及账本。
 *
 * <p>伤害类型口径（如实申报）：插件只有 {@code DamageKind.PHYSICAL}（走护甲）与
 * {@code TRUE}（无视护甲，工程内叫"真伤"）两种，没有"魔法伤害"这一类型。
 * 这里按"魔法伤害 = 无视护甲"取 {@code TRUE} 作为最接近的既有原语；若日后工程补了魔法类型，本处应改回。
 */
public class JudgmentThornSkill extends Skill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "sinThorn_skill_judgment";

    /** 引导时长：2 秒 = 40 刻。 */
    private static final long CHANNEL_TICKS = 40L;

    /** 引导特效帧间隔：每 2 刻一帧。 */
    private static final long CHANNEL_FRAME_INTERVAL_TICKS = 2L;

    /** 审判半径（格）。 */
    private static final double JUDGMENT_RADIUS = 20.0;

    /** 刺击伤害。 */
    private static final double JUDGMENT_DAMAGE = 12.0;

    /** 处决门槛：承受前血量低于该值则直接秒杀。 */
    private static final double EXECUTE_HEALTH_THRESHOLD = 20.0;

    /** 处决时用的"足够大"的伤害量（保证一定归零，且仍走组件入口）。 */
    private static final double EXECUTE_OVERKILL = 1000d;

    /** 引导期缓慢增幅（255，移速归零）。 */
    private static final int ROOT_SLOWNESS_AMPLIFIER = 255;

    /** 引导期抗性增幅：1 即抗性 II。 */
    private static final int RESISTANCE_AMPLIFIER = 1;

    /** 被推出锚点多少格就拽回来（0.8 格）。 */
    private static final double ROOT_MAX_DRIFT = 0.8d;

    /**
     * 范围边缘爆炸粒子每帧的相位增量（弧度）。
     * <p>每 2 刻一帧，即每秒 10 帧，每帧 1.0 rad = 每秒 10 rad，一圈约 0.63 秒。
     */
    private static final double EDGE_PHASE_PER_FRAME = 1.0d;

    /** 敌方身上爆炸粒子的出现间隔：0.5 秒 = 10 刻（紫色引导柱仍然每帧都画）。 */
    private static final int TARGET_EXPLOSION_INTERVAL_TICKS = 10;

    /** 紫色引导柱的高度（格）与半径（格）。 */
    private static final double PURPLE_COLUMN_HEIGHT = 4.0d;
    private static final double PURPLE_COLUMN_RADIUS = 0.6d;

    private BuffComponent buff;
    private VitalsComponent vitals;
    private TaskComponent timer;

    /** 释放（引导结束）任务句柄。 */
    private ScheduledHandle castTask;

    /** 引导期特效任务句柄。 */
    private ScheduledHandle channelVfxTask;

    /** 引导锚点（"不能移动"的基准位置）。 */
    private Location anchor;

    /** 边缘爆炸粒子的旋转相位。 */
    private double edgePhase = 0d;

    /** 引导已经过的刻数（用来把"敌方身上爆炸粒子"的节奏压到每 0.5 秒一次）。 */
    private int channelTicks = 0;

    /**
     * **是否正在引导**。
     * <p>冷却被移到"释放"那一刻启动，因此引导期间 {@code isCoolingDown()} 为假，
     * 这个标志就是唯一的重复施放闸门（见 {@code onCast}）。
     */
    private boolean channeling = false;

    /**
     * **渲染组件**（热键栏）；重绘走"取渲染组件再调它的 {@code requestRepaint()}"这条通道。
     */
    private HotbarRenderComponent render;

    public JudgmentThornSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends Skill.Specification<JudgmentThornSkill> {

        public Specification() {
            super(Component.text("审判孤刺"),
                    List.of(Component.text("引导2秒后，对20格内所有敌人刺出：12点魔法伤害；若其血量低于20则直接秒杀")),
                    800,
                    0,
                    Material.AMETHYST_SHARD);
            requires(BuffComponent.class).requires(VitalsComponent.class).requires(TaskComponent.class)
                    .requires(HotbarRenderComponent.class);
        }

        @Override
        public JudgmentThornSkill create(String id, ComponentServicesPort services) {
            return new JudgmentThornSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        timer = svc().components().get(TaskComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /** **请求重绘热键栏**（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();

        if (!buff.canCastSkill()) {
            return;
        }

        //冷却已移到"释放"那一刻启动，因此引导期间不再被 isCoolingDown 挡住，
        //   所以这里必须自己挡重复施放，否则连点会叠出两条引导。
        if (channeling) {
            return;
        }
        channeling = true;

        //「技能引导时给相应物品附魔」：引导期间让技能物品带附魔光效（buildItem 覆写里加）
        repaint();

        anchor = caster.getLocation().clone();
        edgePhase = 0d;
        channelTicks = 0;

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.6f);

        //引导期特效 + 定身 + 抗性2（每 2 刻一帧）
        channelVfxTask = timer.addScheduleRepeating(this, CHANNEL_FRAME_INTERVAL_TICKS,
                CHANNEL_FRAME_INTERVAL_TICKS, this::channelFrame);

        //引导结束则释放
        castTask = timer.addScheduleLater(this, CHANNEL_TICKS, this::release);

        //冷却不在这里启动 —— 改由 release() 在引导结束、技能真正放出去的那一刻启动，
        //   这样"引导中 / 已释放"在快捷栏图标上也能区分（与二技能的"技能完全后冷却"同一口径）。
    }

    /**
     * **一帧引导特效**：定身 + 抗性 2 + 四向静止十字架 + 边缘旋转爆炸粒子 + 敌方身上爆炸/紫色特效。
     */
    private void channelFrame() {
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            stopChannelVfx();
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        //①「引导时不能移动」：缓慢 255（移速归零）+ 清掉水平速度（含击退）+ 越界拽回锚点
        //   时长只给"够撑到下一帧"（帧间隔 + 4 刻）—— 引导一结束不再刷新，效果自然过期；
        //     同时 release() 里还会显式移除一次，保证"引导结束定身立即消失"。
        int shortDuration = (int) CHANNEL_FRAME_INTERVAL_TICKS + 4;
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, shortDuration, ROOT_SLOWNESS_AMPLIFIER);
        //②「同时获得抗性2」：RESISTANCE 增幅 1 = 抗性 II
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, shortDuration, RESISTANCE_AMPLIFIER);

        Vector velocity = owner.getVelocity();
        owner.setVelocity(new Vector(0d, velocity.getY(), 0d));

        if (anchor != null && anchor.getWorld() == world
                && anchor.distance(owner.getLocation()) > ROOT_MAX_DRIFT) {
            Location back = anchor.clone();
            back.setYaw(owner.getLocation().getYaw());
            back.setPitch(owner.getLocation().getPitch());
            owner.teleport(back);
        }

        edgePhase += EDGE_PHASE_PER_FRAME;
        channelTicks += (int) CHANNEL_FRAME_INTERVAL_TICKS;
        Location center = owner.getLocation();

        //③「自身四个方向的 4 个静止十字架」：位置固定（不含相位），所以不转
        for (int i = 0; i < 4; i++) {
            double angle = i * (Math.PI / 2d);
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            SinThornVfx.spawnCross(world, center.clone().add(dx * 1.7d, 1.0d, dz * 1.7d),
                    dx, dz, 0.95d, 5, Particle.END_ROD);
        }

        //④「大招范围边缘产生多个旋转的单个爆炸粒子」
        SinThornVfx.spawnOrbitingExplosions(world, center, JUDGMENT_RADIUS, edgePhase, 12, 0.6d);

        //⑤ 敌方身上：每 0.5 秒一次单个爆炸粒子；紫色引导柱每帧都画
        //   —— 柱子是"连续垂直 4 格、绕目标旋转"的螺旋（见 SinThornVfx#spawnPurpleHelix）
        boolean explosionFrame = channelTicks % TARGET_EXPLOSION_INTERVAL_TICKS == 0;

        for (Player victim : center.getNearbyPlayers(JUDGMENT_RADIUS)) {
            if (victim == null || victim.equals(owner)) continue;
            if (victim.isDead() || !victim.isOnline() || victim.getHealth() <= 0d) continue;
            if (!svc().roleInfo().isHostile(victim)) continue;

            Location base = victim.getLocation();

            if (explosionFrame) {
                world.spawnParticle(Particle.EXPLOSION, base.clone().add(0, 1, 0), 1, 0, 0, 0, 0);
            }

            //紫色引导柱：每帧重画，相位随帧推进，因此绕着目标转
            SinThornVfx.spawnPurpleHelix(world, base, edgePhase * 1.7d,
                    PURPLE_COLUMN_HEIGHT, PURPLE_COLUMN_RADIUS, 20);
        }
    }

    /** **引导结束即释放大招**：尖牙形状特效 + 结算伤害/处决。 */
    private void release() {
        stopChannelVfx();
        channeling = false;
        repaint();   //摘掉引导期的附魔光效

        //「引导结束定身效果消失」：显式摘掉引导期挂上的缓慢与抗性
        //  （刷新时给的就是短时长，这里再删一次，结束瞬间立刻恢复行动）
        Player caster = svc().self().player();
        if (caster != null) {
            caster.removePotionEffect(PotionEffectType.SLOWNESS);
            caster.removePotionEffect(PotionEffectType.RESISTANCE);
        }

        //「引导完毕后释放技能」：技能真正放出去的这一刻才进冷却。
        //  放在 owner 判空之前 —— 人死了/掉线也一样要进冷却，否则等于白嫖一次。
        startCooldown();

        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        Location center = owner.getLocation();
        float yaw = (float) Math.toRadians(center.getYaw());

        //「产生由单个爆炸粒子特效组成的尖牙形状特效」——自身位置升起一枚大尖牙
        SinThornVfx.spawnFangShape(world, center.clone(), yaw, 3.2d);

        for (Player victim : center.getNearbyPlayers(JUDGMENT_RADIUS)) {
            if (victim == null || victim.equals(owner)) continue;
            if (victim.isDead() || !victim.isOnline() || victim.getHealth() <= 0d) continue;
            if (!svc().roleInfo().isHostile(victim)) continue;

            double healthBefore = victim.getHealth();
            if (healthBefore < EXECUTE_HEALTH_THRESHOLD) {
                //直接处决：伤害量 = 当前血量 + 冗余（经生命组件，保留击杀归属与游戏模式判定）
                vitals.trueDamage(victim, owner, healthBefore + EXECUTE_OVERKILL);
            } else {
                vitals.trueDamage(victim, owner, JUDGMENT_DAMAGE);
            }

            //每个被命中的敌人身上补一枚小尖牙（同样由单个爆炸粒子排成）
            SinThornVfx.spawnFangShape(world, victim.getLocation().clone(),
                    (float) Math.toRadians(victim.getLocation().getYaw()), 1.2d);
        }

        world.playSound(center, Sound.ENTITY_WITHER_DEATH, 1f, 1.4f);
    }

    /** 停掉引导特效任务（释放时 / 异常时 / 组件停用时都调；幂等）。 */
    private void stopChannelVfx() {
        if (channelVfxTask != null) {
            channelVfxTask.cancel();
            channelVfxTask = null;
        }
    }

    /**
     * **引导中给技能物品加附魔光效**。
     * <p>先按基类默认画法产出完整物品，再在引导期间补一个
     * {@code setEnchantmentGlintOverride(true)} —— 只加光效、不加真实附魔（不会多出词条、不改数值）。
     * <p>引导开始/结束各调一次 {@code repaint()} 触发重绘（见 {@code onCast} 与 {@code release()}）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!channeling) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public void stop() {
        stopChannelVfx();
        if (castTask != null) {
            castTask.cancel();
            castTask = null;
        }
        anchor = null;
        channeling = false;
    }

    /** **闸门放行？**（基类不查容器，用本组件自己的字段判）。 */
    @Override
    protected boolean canUse() {
        return buff.canCastSkill();
    }

    /** **当前能量**：本组件不参与能量维度（声明耗能 0），返回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
