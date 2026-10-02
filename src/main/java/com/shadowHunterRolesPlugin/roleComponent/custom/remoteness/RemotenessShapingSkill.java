package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 冷识（{@code remoteness}）的主动技能「**塑造**」（青金石块）：范围压制 + 地面粒子陷阱。
 *
 * <h2>玩法（三段，按顺序发生）</h2>
 * <ol>
 *   <li><b>压制</b>：半径 {@link #RADIUS}（20）格内的敌人被施加漂浮
 *       （{@link RemotenessEvolutionPassive#shapingFloatTicks()}：4 秒；4 级起 7 秒），
 *       并被扣除 {@link RemotenessEvolutionPassive#shapingSanteDamage()}（30；4 级起 60）点特殊值
 *       —— 8 级起改为**直接清空**（{@link RemotenessEvolutionPassive#shapingClearsSante()}）；</li>
 *   <li><b>布场</b>：在半径 20 格内生成 {@link #MARKER_COUNT}（40）个 {@code END_ROD} 粒子点
 *       —— 每个点是一个 **{@link Marker} 实体**（无碰撞箱、不参与伤害的坐标载体），
 *       因此"碰到哪个点"可以精确判定；</li>
 *   <li><b>陷阱</b>：逐刻扫描这些点；**敌人碰到**（距离 ≤ {@link #TOUCH_RADIUS}）⇒ 对他施加
 *       {@link #STUN_TICKS}（1 秒）眩晕，**然后这个点消失**（一次性）。</li>
 * </ol>
 * 冷却在**施放那一刻**启动（产品口径只写了 "CD-20"，没有"技能结束后开始"），
 * 时长由档位决定（{@link RemotenessEvolutionPassive#shapingCooldownTicks()}：20 / 40 / 60 秒）。
 *
 * <h2>★ 为什么粒子点要真的生成实体（而不是"只画粒子，再自己算碰撞"）</h2>
 * 只画粒子的话，"这个点的坐标"只存在于本组件的一个 {@code List<Location>} 里；
 * 一旦有别的东西（区块卸载、世界边界、本组件的实例被回收）改变了世界状态，
 * 那份列表与世界之间就没有任何可核对的联系了。用 {@link Marker} 作载体后：
 * ① 每个点在世界里**有身份**（可取位置、可删、可判 {@code isValid()}）；
 * ② 组件销毁时"把点全部删掉"是一句 {@code marker.remove()}，不留任何孤儿；
 * ③ 判碰撞用的是实体真实位置，与渲染用的是同一个坐标（不会出现"看着碰到了却没判定"）。
 * <p>代价如实申报：40 个 Marker 是 40 个真实实体，每次施放都会在建/删各 40 个。
 * 它们**不会持久化**（{@link Marker#setPersistent(boolean)} 已关）⇒ 不写进世界存档。
 *
 * <h2>★ "眩晕 / 漂浮"打给的是别人（跨实例），因此要挑对那一份 buff 组件</h2>
 * 插件侧的 {@link BuffType#STUN} 记账在**受害者自己那个角色实例**的 buff 组件里
 * （本组件的 {@code buff} 字段是**本角色实例**的那一份，对它调 {@code add(STUN)} 只会晕自己）。
 * 因此 {@link #stun(Player)} 走 {@link BuffComponent#addTo(Player, BuffType, int)} ——
 * buff 组件自己的跨玩家入口（口径与 {@code SanTEComponent} 的跨实例入口同源：经插件单例取角色实例、
 * 再按组件 id 取、不写第二份 id 字面量），由它负责"挑对那一份组件"；
 * 本组件不再自己复制这段解析（原先那份 {@code buffComponentOf} 已删除）。
 * <p><b>原版漂浮同理</b>：压制阶段的 {@code LEVITATION} 走
 * {@link BuffComponent#applyPotionEffectTo(Player, PotionEffectType, int, int)} ⇒
 * 进受害者自己的**药水账本**（原先直接 {@code victim.addPotionEffect} 的效果没人认领）。
 * <p>对方没有角色实例时（例如没选角色的玩家）没有可挂的账本 ⇒ **两处都不施加任何东西**
 * （{@code addTo} 回 {@code false}；{@code applyPotionEffectTo} 回 {@code false}）——
 * 那种玩家只被扣特殊值。这是本次口径迁移带来的**行为变更**，如实申报。
 * <p><b>附注（老注释的更正，如实申报）</b>：这里原先接着写"退回原版失明 + 黑暗"，但代码里
 * <b>从未</b>存在那条退回路径（空分支直接 return）；现按 buff 组件的统一口径明确为
 * "目标无角色 ⇒ 不施加"，不再保留一条不存在的降级说明。
 *
 * <h2>两个"产品口径没给的值"（如实申报的假设，各自只有一个常量）</h2>
 * <ul>
 *   <li>{@link #MARKER_LIFETIME_TICKS}（10 秒）：口径只说"碰到就消失"，没说到期怎么算。
 *       给一个明确寿命是为了**不留下永久实体**（否则玩家走远之后，那些点会永远留在世界里）；</li>
 *   <li>{@link #TOUCH_RADIUS}（1.2 格）："碰到"需要一个判定半径，取略大于一格。</li>
 * </ul>
 */
public class RemotenessShapingSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "remoteness_shaping_skill";

    // ───────── 数值（唯一修改点）─────────

    /** 作用半径（格）。 */
    private static final double RADIUS = 20d;

    /** 生成的粒子点（Marker）个数。 */
    private static final int MARKER_COUNT = 40;

    /** 粒子点的寿命（200 刻 = 10 秒；见类注释的假设申报）。 */
    private static final int MARKER_LIFETIME_TICKS = 200;

    /** 陷阱扫描周期（2 刻）：碰撞判定的节拍，也是粒子重绘的节拍。 */
    private static final int SWEEP_PERIOD_TICKS = 2;

    /** "碰到"的判定半径（格）。 */
    private static final double TOUCH_RADIUS = 1.2d;

    /** 眩晕时长（20 刻 = 1 秒）。 */
    private static final int STUN_TICKS = 40;

    /** 漂浮的振幅（漂浮 1 级 = 增幅 0）。 */
    private static final int FLOAT_AMPLIFIER = 0;

    /** 冷却声明值（400 刻 = 20 秒）—— 描述符里的基础值；实际启动时用档位读数的显式刻数覆盖。 */
    private static final int BASE_COOLDOWN_TICKS = RemotenessEvolutionPassive.BASE_SHAPING_COOLDOWN_TICKS;

    // ───────── 状态 ─────────

    private TaskComponent timer;
    private BuffComponent buff;
    private SanTEComponent sante;

    /** 进化被动（档位读口的来源）；{@code null} = 没装它（退化为基线值，不抛）。 */
    private RemotenessEvolutionPassive evolution;

    /** 当前在世界里的粒子点（顺序 = 生成先后，便于诊断复现）。 */
    private final List<Marker> markers = new ArrayList<>();

    /** 扫描任务句柄；{@code null} = 没有在跑的场。 */
    private ScheduledHandle sweepTask;

    /** 场的剩余刻数（在本组件内累计，不用调度器的次数计数）。 */
    private int remainingTicks;

    /** 取随机落点的随机源（本组件私有；不共享全局随机，避免与其它组件互相干扰可复现性）。 */
    private final Random random = new Random();

    public RemotenessShapingSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<RemotenessShapingSkill> {

        public Specification() {
            super(Component.text("塑造"),
                    List.of(
                            Component.text("半径20内的敌人漂浮4秒，并被扣除30点特殊值"),
                            Component.text("在半径20内生成40个粒子，敌人碰到一个粒子时会被眩晕1秒，该粒子消失"),
                            Component.text("CD 20s 进化后延长")
                    ),
                    BASE_COOLDOWN_TICKS,
                    0,
                    Material.LAPIS_BLOCK);
            requires(BuffComponent.class).requires(SanTEComponent.class).requires(TaskComponent.class)
                    .requires(RemotenessEvolutionPassive.class);
        }

        @Override
        public RemotenessShapingSkill create(String id, ComponentServicesPort services) {
            return new RemotenessShapingSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        timer = svc().components().get(TaskComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        evolution = getComponent(RemotenessEvolutionPassive.class);
    }

    /**
     * 停止生效：把场收掉（取消扫描任务 + 删掉全部仍在世界里的粒子点）。
     * <p>这是**必须**的：{@link Marker} 是真实实体，不删就会在组件已死之后永远留在世界里
     * （没人再推它、也没人会删它）——与 {@code CangluBlueIceRevolverSkill} 回收自己的子弹同源。
     */
    @Override
    public void stop() {
        clearField();
        timer = null;
        buff = null;
        sante = null;
        evolution = null;
    }

    // ───────── 施放（唯一入口：右键）─────────

    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        if (!canUse()) {
            return;
        }
        Player self = selfPlayer();
        if (self == null) {
            return;
        }

        //档位读数在本刻定档（整轮不中途变）：冷却时长 / 漂浮时长 / 扣减量 / 是否清空
        int floatTicks = evolution != null
                ? evolution.shapingFloatTicks() : RemotenessEvolutionPassive.BASE_SHAPING_FLOAT_TICKS;
        int santeDamage = evolution != null
                ? evolution.shapingSanteDamage() : RemotenessEvolutionPassive.BASE_SHAPING_SANTE_DAMAGE;
        boolean purge = evolution != null && evolution.shapingClearsSante();
        int cooldownTicks = evolution != null
                ? evolution.shapingCooldownTicks() : RemotenessEvolutionPassive.BASE_SHAPING_COOLDOWN_TICKS;

        //① 压制：半径 20 内的敌人漂浮 + 扣特殊值（8 级起清空）
        //  漂浮走 buff 组件的跨玩家入口 ⇒ 进**受害者自己**的药水账本（他清角色时可被回收，
        //  也能被 clearDebuffOn 净化）；原先直接 victim.addPotionEffect 的效果没人认领。
        //  ★ 目标没有角色 ⇒ 本入口不写（没有账本）⇒ 那种玩家不吃漂浮，只被扣特殊值。行为变更，如实申报。
        int touched = 0;
        for (Player victim : hostilesWithin(self, self.getLocation(), RADIUS)) {
            if (buff != null) {
                buff.applyPotionEffectTo(victim, PotionEffectType.LEVITATION, floatTicks, FLOAT_AMPLIFIER);
            }
            if (sante != null) {
                sante.decreaseSanTE(victim.getUniqueId(), purge ? SanTEComponent.SANTE_MAX : santeDamage);
            }
            touched++;
        }

        //② 布场：先收掉上一轮可能仍在的点（正常玩法下冷却比场长，这里是防御性的一致性保证）
        clearField();
        spawnMarkers(self);

        //③ 起扫描任务（场的寿命在这一条节拍里倒计时）
        remainingTicks = MARKER_LIFETIME_TICKS;
        sweepTask = timer.addScheduleRepeating(this, SWEEP_PERIOD_TICKS, SWEEP_PERIOD_TICKS, () -> {
            Player owner = selfPlayer();
            if (owner == null || owner.isDead() || !owner.isOnline()) {
                clearField();
                return;
            }
            remainingTicks -= SWEEP_PERIOD_TICKS;
            sweep();
            if (remainingTicks <= 0 || markers.isEmpty()) {
                clearField();
            }
        });

        //④ 冷却：施放那一刻启动（本技能的口径），时长按档位
        startCooldown(cooldownTicks);
        self.getWorld().playSound(self.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.7f);
    }

    // ───────── 场的建立 / 扫描 / 拆除 ─────────

    /** 在施放点周围的地面上随机撒 {@link #MARKER_COUNT} 个点（每个点是一个 {@link Marker} 实体）。 */
    private void spawnMarkers(Player self) {
        World world = self.getWorld();
        Location origin = self.getLocation();
        for (int i = 0; i < MARKER_COUNT; i++) {
            Location spot = randomSpot(world, origin);
            if (spot == null) {
                continue;
            }
            Entity spawned = world.spawnEntity(spot, EntityType.MARKER);
            if (!(spawned instanceof Marker marker)) {
                spawned.remove();
                continue;
            }
            //不留存档：这些点只在这次技能的生命周期里存在
            marker.setPersistent(false);
            world.spawnParticle(Particle.END_ROD, spot, 1, 0, 0, 0, 0);
            markers.add(marker);
        }
    }

    /**
     * 在半径 {@link #RADIUS} 的**圆盘**内均匀取一个落点，Y 取该列地面的上一格。
     *
     * <p>为什么取圆盘（而不是球）：口径说"半径 20 范围内生成 40 个粒子"，而陷阱要"敌人碰到"——
     * 撒在空中的点玩家永远碰不到。落点吸到地面后，40 个点才是 40 个真正可触发的陷阱。
     * <p>均匀性：半径按 {@code sqrt(u)} 取（不修正的话点会向圆心聚集，边缘会变稀）。
     */
    private Location randomSpot(World world, Location origin) {
        if (world == null) {
            return null;
        }
        double radius = RADIUS * Math.sqrt(random.nextDouble());
        double angle = random.nextDouble() * 2 * Math.PI;
        double x = origin.getX() + Math.cos(angle) * radius;
        double z = origin.getZ() + Math.sin(angle) * radius;
        int groundY = world.getHighestBlockAt((int) Math.floor(x), (int) Math.floor(z)).getY() + 1;
        return new Location(world, x, groundY, z);
    }

    /**
     * 一轮扫描：重绘粒子、判碰撞、命中即"眩晕 + 该点消失"。
     * <p>遍历中会删元素 ⇒ 用迭代器（{@code remove()} 走迭代器，不在 {@code for-each} 里改表）。
     */
    private void sweep() {
        Player self = selfPlayer();
        Iterator<Marker> iterator = markers.iterator();
        while (iterator.hasNext()) {
            Marker marker = iterator.next();
            if (marker == null || !marker.isValid() || marker.isDead()) {
                iterator.remove();
                continue;
            }
            Location spot = marker.getLocation();
            if (spot.getWorld() == null) {
                marker.remove();
                iterator.remove();
                continue;
            }
            spot.getWorld().spawnParticle(Particle.END_ROD, spot, 1, 0, 0, 0, 0);

            //只取敌对玩家（自己不算）；碰到就晕、然后这个点消失（一次性）
            Player victim = firstHostileNear(self, spot, TOUCH_RADIUS);
            if (victim != null) {
                stun(victim);
                marker.remove();
                iterator.remove();
            }
        }
    }

    /**
     * 收场：取消扫描任务 + 删掉世界里剩下的全部点 + 清空列表（幂等）。
     * <p>**不启动冷却**：本技能的冷却在施放那一刻就启动了（与「忘怀」/「重构」的"结束后开始"相反）。
     */
    private void clearField() {
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
        for (Marker marker : markers) {
            if (marker != null && marker.isValid()) {
                marker.remove();
            }
        }
        markers.clear();
        remainingTicks = 0;
    }

    // ───────── 眩晕（跨实例：晕的是**别人**）─────────

    /**
     * 对受害者施加 {@link #STUN_TICKS}（1 秒）眩晕 —— 走 {@link BuffComponent#addTo(Player, BuffType, int)}
     * （buff 组件的跨玩家入口），由它把这次写入交给**受害者自己那份** buff 组件的 {@link BuffType#STUN}：
     * 那一条同时关闭他的技能闸门与主武器闸门（并带出原版失明 / 黑暗与移速修饰符），
     * 是本工程里"眩晕"的完整语义。
     *
     * <p><b>受害者没有角色实例时（例如没选角色的玩家）⇒ 不发生任何事</b>（{@code addTo} 回 {@code false}）：
     * 插件侧 buff 的账本挂在角色实例的 buff 组件上，没有角色就没有账本 —— 既没人记、也没人回收，
     * 写下去只会留一条孤儿效果。本技能因此<b>不</b>退回"直接给他上原版失明 / 黑暗"
     * （该退回路径曾写在注释里，但代码从未实现过；现按 buff 组件的统一口径明确为"不施加"）。
     * <p>跨实例解析、免疫闸门、取最大时长、角色清除时的一并回收全部由 {@code BuffComponent} 负责，
     * 本技能不再自己解析组件（原先那份 {@code buffComponentOf} 复制实现已删除）。
     */
    private void stun(Player victim) {
        if (buff == null) {
            return;     //未装配（start 之前）/ 已停用（stop 之后）——与同文件其它 buff 用点同规
        }
        buff.addTo(victim, BuffType.STUN, STUN_TICKS);
    }

    // ───────── 索敌（几何 + 判敌口径逐字沿用 roleInfo）─────────

    /** 半径 {@code radius} 内的**敌对**玩家（几何按真实直线距离过滤 —— 世界查询给的是立方体）。 */
    private List<Player> hostilesWithin(Player self, Location center, double radius) {
        List<Player> result = new ArrayList<>();
        World world = center.getWorld();
        if (world == null) {
            return result;
        }
        double radiusSquared = radius * radius;
        for (Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!(entity instanceof Player candidate) || candidate.equals(self)) {
                continue;
            }
            if (candidate.isDead() || !candidate.isOnline()) {
                continue;
            }
            if (!world.equals(candidate.getWorld())) {
                continue;
            }
            if (candidate.getLocation().distanceSquared(center) > radiusSquared) {
                continue;
            }
            if (!svc().roleInfo().isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            result.add(candidate);
        }
        return result;
    }

    /** 某个点附近最近的敌对玩家（无则 {@code null}）—— 陷阱的碰撞判定。 */
    private Player firstHostileNear(Player self, Location spot, double radius) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Player candidate : hostilesWithin(self, spot, radius)) {
            double distance = candidate.getLocation().distanceSquared(spot);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕 / 沉默时不许施放。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    /** 本组件不参与能量维度（声明耗能 0），故回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    // ───────── 诊断读口（供探针 / 运行级取证；不参与行为决策）─────────

    /** 当前世界里仍在场上的粒子点数（0 = 没有场）。 */
    public int liveMarkerCount() {
        return markers.size();
    }
}
