package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「芹泽沙耶香 · 瓦尔基里」技能一：**影稳态榴弹发射器**（模型：发射器）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>向指定区域发射一枚榴弹：飞行速度 <b>30 格/秒</b>（2026-10-17 需求 3 由 15 提速）、
 *       <b>抛物线</b>、<b>下落快</b>、最远打击范围 <b>20 格</b>；</li>
 *   <li>炸开：<b>5 点物理伤害</b>，落点范围 <b>5 格</b>；并生成<b>稳态粒子场</b>，持续 <b>5 秒</b>；</li>
 *   <li>场内敌人每秒 <b>1 点真实伤害 + 2 点失衡</b>，<b>范围重叠不积累</b>，并获得<b>缓慢 2</b>；</li>
 *   <li>存储：每 <b>10 秒</b>存 1 枚、上限 <b>6 枚</b>、<b>开局满存储</b>；弹药数由<b>物品栏中的物品个数</b>表达
 *       （拿在手上时 actionbar 也显示）；</li>
 *   <li>射击：耗 <b>6 点能量</b>、射速 <b>0.8 秒/发</b>；</li>
 *   <li>沙耶香<b>身处场内</b>时获得<b>速度 4 + 再生 1</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：弹道 = 逐刻插值的抛物线（无实体、无调度器），**一定落地**</h2>
 * 发射时先求**落点**（{@link #resolveLanding}）：沿准星射线找第一个实心方块（最远 {@value #RANGE_MAX} 格），
 * 没有阻挡就取射线末端，然后把该 XZ **向下扫到地面**（{@link #groundAt}）——
 * 因此落点永远在地面上：**朝天放炮也不会在空中炸开**。
 * 位置 = 线性插值 + {@code 4·apex·t·(1-t)} 的抛物隆起（两端为 0，{@code t=1} 正好落在落点）。
 * 逐刻推进时按顺序判三件事：**命中敌人 ⇒ 在其位置炸开 / 撞地形 ⇒ 在接触点炸开 / 到达落点 ⇒ 落地炸开**；
 * 敌人判定用"线段到敌人"的距离（{@value #HIT_RADIUS} 格），弹速再快也不会穿人。
 *
 * <h2>★★ 口径申报五：后座与"屏幕缩放"（2026-10-17 需求 3）</h2>
 * <ul>
 *   <li><b>后座</b>：发射当刻把视角 <b>上抬 {@value #RECOIL_PITCH_DEGREES} 度</b>，
 *       {@value #RECOIL_TICKS} 刻后<b>一次性归位</b>（只传送两拍，且保留动量 ⇒ 不会定身）。
 *       实现用 {@code teleport}（工程口径：{@code setRotation} 只改服务端朝向、推不到玩家镜头）。</li>
 *   <li><b>屏幕缩放</b>：Minecraft 没有公开的 FOV 控制口 ⇒ 用 <b>{@value #ZOOM_TICKS} 刻的
 *       速度 I</b>造成客户端 FOV 轻微推近（这是本工程能做到的最接近"屏幕缩放"的手段，如实申报）。
 *       已有更强速度（例如稳态场的速度 4）时不施加，避免把玩家增益顶掉。</li>
 * </ul>
 *
 * <h2>★★ 口径申报二：「范围重叠不积累」= 按受害者记 20 刻去重表</h2>
 * 多个场的每秒结算若相位错开，同一敌人可能被两个场各打一次 ⇒ 需求明确不允许。
 * 实现 = 每受害者记录"上次被场结算的刻"，{@value #FIELD_PULSE_TICKS} 刻内互斥（跨场共享同一张表）。
 *
 * <h2>★★ 口径申报三：场每秒伤害 ⇒ 真实伤害（2026-10-14 需求更新）</h2>
 * 原「1 点法术伤害」按既有角色口径落在 SanTE 削减；**2026-10-14 用户明确改判为真实伤害**
 * （{@code vitals.trueDamage}）。本角色体系内三处「法术」调用点已全部同步改判（另两处见
 * {@code SayakaBladeMainWeapon} / {@code SayakaFlashSlashSkill}）。
 *
 * <h2>★★ 口径申报四：图标与识别键</h2>
 * 弹药数用 {@code setAmount} 表达（1 枚也显示为 1 —— 原版不渲染数量 1，属如实取舍）；
 * "榴弹耗尽"分支会把材质换成结构空位 ⇒ ★ 按工程铁律<b>必须重写识别键</b>
 * （否则物品既点不动、又不会被角色清除扫掉）。
 */
public class SayakaGrenadeSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "sayaka_grenade_skill";

    // ───────── 弹药 / 射击 ─────────

    /** 存储上限（需求：最多 6 发）。 */
    public static final int AMMO_MAX = 6;

    /** 存储回复间隔（刻）—— 需求：每 10 秒存 1 枚。 */
    public static final int AMMO_REGEN_TICKS = 200;

    /** 每发射击的能量消耗（需求：6 点）。 */
    public static final int FIRE_ENERGY_COST = 6;

    /** 射速（刻）—— 需求：0.8 秒/发。 */
    public static final int FIRE_INTERVAL_TICKS = 16;

    // ───────── 弹道 ─────────

    /** 水平飞行速度（格/刻）—— 2026-10-17 需求 3：由 15 格/秒提到 <b>30 格/秒</b>。 */
    public static final double FLIGHT_SPEED_PER_TICK = 30.0d / 20d;

    /** 最远打击范围（格）—— 需求：20 格。 */
    public static final double RANGE_MAX = 20.0d;

    /** 弹道最短刻数（贴脸也要有个抛物过程）。 */
    public static final int FLIGHT_MIN_TICKS = 6;

    /** 抛物隆起上限（格）—— 「下落快」= 不过分抛高。 */
    public static final double ARC_APEX_MAX = 3.0d;

    /** 炸开物理伤害（需求：5 点）。 */
    public static final double EXPLODE_PHYSICAL = 5d;

    /** 炸开范围（需求：落点范围 5 格）。 */
    public static final double EXPLODE_RADIUS = 5.0d;

    /** **命中敌人**的判定半径（格）—— 按"线段到敌人"的距离算，弹速再快也不会穿人。 */
    public static final double HIT_RADIUS = 1.4d;

    /** 落点向下扫描地面的最大深度（格）。 */
    private static final int GROUND_SCAN_DEPTH = 160;

    // ───────── 后座 / 屏幕缩放（2026-10-17 需求 3）─────────

    /** 发射后座：视角上抬角度（度）。 */
    public static final double RECOIL_PITCH_DEGREES = 4.5d;

    /** 后座回落时长（刻）—— 0.3 秒回到原视角。 */
    public static final int RECOIL_TICKS = 6;

    /** "屏幕缩放"近似的短促速度效果时长（刻）。 */
    public static final int ZOOM_TICKS = 4;

    /**
     * 后座传送用的旗标：<b>保留三轴动量与角速度</b>。
     *
     * <p>不加这些旗标时 {@code teleport} 会把玩家动量清零 —— 每刻一次就是"定身"。
     * 位置仍是绝对坐标（Paper 的 Relative 系列只管速度是否保留）。
     */
    private static final TeleportFlag[] TELEPORT_KEEP_MOMENTUM = {
            TeleportFlag.Relative.VELOCITY_X,
            TeleportFlag.Relative.VELOCITY_Y,
            TeleportFlag.Relative.VELOCITY_Z,
            TeleportFlag.Relative.VELOCITY_ROTATION
    };

    // ───────── 稳态场 ─────────

    /** 场持续（刻）—— 需求：5 秒。 */
    public static final int FIELD_TICKS = 100;

    /** 场半径（格）—— 与落点范围同口径。 */
    public static final double FIELD_RADIUS = 5.0d;

    /** 场每秒结算间隔（刻）。 */
    public static final int FIELD_PULSE_TICKS = 20;

    /** 场每秒：真实伤害（2026-10-14 需求更新：原「1 点法术」改为 1 点真实伤害）。 */
    public static final double FIELD_TRUE_PER_PULSE = 1d;

    /** 场每秒：失衡积累（需求：2 点）。 */
    public static final int FIELD_IMBALANCE_PER_PULSE = 2;

    /** 场给敌人的缓慢增幅（需求：缓慢 2 ⇒ amplifier 1）。 */
    public static final int FIELD_SLOW_AMPLIFIER = 1;

    /** 场给敌人的缓慢时长（刻，按拍刷新）。 */
    public static final int FIELD_SLOW_TICKS = 40;

    /** 场给沙耶香的速度增幅（需求：速度 4 ⇒ amplifier 3）。 */
    public static final int SELF_SPEED_AMPLIFIER = 3;

    /** 场给沙耶香的再生增幅（需求：再生 1 ⇒ amplifier 0）。 */
    public static final int SELF_REGEN_AMPLIFIER = 0;

    /** 场给自身的 buff 时长（刻，按拍刷新）。 */
    public static final int SELF_BUFF_TICKS = 40;

    /** "范围重叠不积累"的受害者去重窗口（刻）。 */
    public static final int FIELD_DEDUPE_TICKS = 20;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private FactionComponent faction;
    private SayakaGauntletPassive passive;
    private SayakaFloatingTextComponent voice;
    private HotbarRenderComponent render;

    // ───────── 运行期状态 ─────────

    /** 存储中的榴弹数。 */
    private int ammo = AMMO_MAX;

    /** 存储节拍。 */
    private int regenTick;

    /** 在飞的榴弹。 */
    private final List<Grenade> grenades = new ArrayList<>();

    /** 活跃的稳态场。 */
    private final List<Field> fields = new ArrayList<>();

    /** 「范围重叠不积累」去重表：受害者 → 上次被场结算的刻。 */
    private final Map<UUID, Integer> lastFieldHitTick = new HashMap<>();

    /** 去重表清理节拍。 */
    private int dedupeSweepTick;

    /** 后座剩余刻（> 0 = 还在回落）。 */
    private int recoilTicks;

    /** 上一刻已经施加到视角上的抬升角（"减帐"用 ⇒ 后座期间玩家仍能自由瞄准）。 */
    private double recoilApplied;

    /** 上一帧外观签名。 */
    private String lastRenderSignature = "";

    /** 一枚在飞的榴弹。 */
    private static final class Grenade {
        private final Location origin;
        private final Location target;
        private final double apex;
        private final int totalTicks;
        private int elapsed;

        private Grenade(Location origin, Location target, double apex, int totalTicks) {
            this.origin = origin;
            this.target = target;
            this.apex = apex;
            this.totalTicks = totalTicks;
        }
    }

    /** 一个稳态场。 */
    private static final class Field {
        private final World world;
        private final Location center;
        private final int untilTick;
        private int nextPulseTick;

        private Field(World world, Location center, int untilTick, int nextPulseTick) {
            this.world = world;
            this.center = center;
            this.untilTick = untilTick;
            this.nextPulseTick = nextPulseTick;
        }
    }

    public SayakaGrenadeSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（模型：发射器；射速 0.8 秒由组件冷却表达）。 */
    public static final class Specification extends Skill.Specification<SayakaGrenadeSkill> {

        public Specification() {
            super(Component.text("影稳态榴弹发射器"),
                    List.of(
                            Component.text("向指定区域发射榴弹：30 格/秒抛物线（发射有后座、落地有冲击）、最远 20 格；炸开 5 点物理伤害（范围 5 格）"),
                            Component.text("落点生成稳态粒子场 5 秒：场内敌人每秒 1 点真实伤害 + 2 点失衡并获得缓慢 2"),
                            Component.text("范围重叠不积累；沙耶香身处场内获得速度 4 + 再生 1"),
                            Component.text("存储：上限 6 枚、每 10 秒存 1 枚、开局满存储；物品个数即弹药数"),
                            Component.text("射击：耗 6 点能量、射速 0.8 秒")
                    ),
                    FIRE_INTERVAL_TICKS,
                    FIRE_ENERGY_COST,
                    Material.DISPENSER);
            requires(VitalsComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class)
                    .requires(FactionComponent.class).requires(SayakaGauntletPassive.class)
                    .requires(SayakaFloatingTextComponent.class);
        }

        @Override
        public SayakaGrenadeSkill create(String id, ComponentServicesPort services) {
            return new SayakaGrenadeSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        passive = svc().components().get(SayakaGauntletPassive.class);
        voice = svc().components().get(SayakaFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        ammo = AMMO_MAX;    //开局满存储
        regenTick = 0;
        grenades.clear();
        fields.clear();
        lastFieldHitTick.clear();
        recoilTicks = 0;
        recoilApplied = 0d;
        lastRenderSignature = "";
    }

    @Override
    public void stop() {
        grenades.clear();
        fields.clear();
        lastFieldHitTick.clear();
        recoilTicks = 0;
        recoilApplied = 0d;
        vitals = null;
        energy = null;
        buff = null;
        faction = null;
        passive = null;
        voice = null;
        render = null;
    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            grenades.clear();
            fields.clear();
            lastFieldHitTick.clear();
            recoilTicks = 0;        //死亡 / 离场：把未回落的视角后座作废（别在复活后补一脚）
            recoilApplied = 0d;
            return;
        }
        int now = Bukkit.getCurrentTick();
        advanceAmmoRegen(owner);
        advanceGrenades(owner, now);
        advanceFields(owner, now);
        tickRecoil(owner);
        sweepDedupe(now);
        //HUD：拿在手上时显示弹药（actionbar 发布者是笼手被动）
        if (passive != null) {
            String heldId = Skill.Utils.getSkillId(owner.getInventory().getItemInMainHand());
            passive.publishGrenade(getId().equals(heldId), ammo, AMMO_MAX);
        }
        requestRenderIfChanged();
    }

    /** 存储回复：每 10 秒 1 枚（满则这一拍作废）。 */
    private void advanceAmmoRegen(Player owner) {
        if (++regenTick >= AMMO_REGEN_TICKS) {
            regenTick -= AMMO_REGEN_TICKS;
            if (ammo < AMMO_MAX) {
                ammo++;
                requestRepaint();
            }
        }
    }

    /**
     * 榴弹逐刻推进。判定顺序（不可交换）：
     * <ol>
     *   <li><b>命中敌人</b> ⇒ 在**敌人所在位置**炸开（需求：「命中敌人在其位置生成」）；</li>
     *   <li><b>撞到地形</b> ⇒ 在接触点炸开（沿线段四点采样，高速不会穿墙）；</li>
     *   <li><b>到达落点</b> ⇒ 落点已经过 {@link #groundAt} 落在地面 ⇒ 落地炸开。</li>
     * </ol>
     * ★ 2026-10-17：这三条合起来保证**永远不会有"空中爆开"**——朝天空发射也会飞到地面再炸。
     */
    private void advanceGrenades(Player owner, int now) {
        if (grenades.isEmpty()) {
            return;
        }
        Iterator<Grenade> iterator = grenades.iterator();
        while (iterator.hasNext()) {
            Grenade grenade = iterator.next();
            Location previous = grenadePosition(grenade);      //上一刻位置（线段起点）
            grenade.elapsed++;
            Location at = grenadePosition(grenade);
            if (at == null || at.getWorld() != owner.getWorld()) {
                iterator.remove();
                continue;
            }
            World world = at.getWorld();
            //① 命中敌人：以敌人自身位置为爆点（这一段是"打人"而不是"落地"）
            Player hit = nearestEnemyAlong(owner, world, previous, at);
            if (hit != null) {
                explode(owner, hit.getLocation().clone());
                iterator.remove();
                continue;
            }
            //② 撞地形：在线段上找第一个实心点（防高速穿墙）
            Location wall = firstSolidAlong(previous, at);
            if (wall != null) {
                explode(owner, wall);
                iterator.remove();
                continue;
            }
            //③ 到达落点：落点已在地面 ⇒ 落地爆炸
            if (grenade.elapsed >= grenade.totalTicks) {
                explode(owner, grenade.target.clone());
                iterator.remove();
                continue;
            }
            SayakaVfx.grenadeTrail(world, at);
        }
    }

    /** 沿线段找最近的敌对玩家（点—线段距离 ≤ {@value #HIT_RADIUS}）。 */
    private Player nearestEnemyAlong(Player owner, World world, Location from, Location to) {
        if (world == null || from == null || to == null) {
            return null;
        }
        double radius = from.distance(to) + HIT_RADIUS + 0.5d;
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : to.getNearbyPlayers(radius)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate) || !isHostile(candidate)) {
                continue;
            }
            Location body = candidate.getLocation().clone().add(0d, 1.0d, 0d);
            double distance = pointSegmentDistance(body.toVector(), from.toVector(), to.toVector());
            if (distance <= HIT_RADIUS && distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 线段上的第一个实心方块点（四点采样；没有 ⇒ {@code null}）。 */
    private static Location firstSolidAlong(Location from, Location to) {
        if (from == null || to == null || from.getWorld() == null) {
            return null;
        }
        int samples = 4;
        for (int i = 1; i <= samples; i++) {
            double t = i / (double) samples;
            Location point = from.clone().add(to.toVector().subtract(from.toVector()).multiply(t));
            if (isSolid(point)) {
                return point;
            }
        }
        return null;
    }

    /** 该位置是否是"墙"（有实体碰撞箱的方块）。 */
    private static boolean isSolid(Location at) {
        return at != null && at.getBlock().getBoundingBox().getVolume() > 0d;
    }

    /** 弹体当前位置（纯插值 + 抛物隆起）。 */
    private static Location grenadePosition(Grenade grenade) {
        double t = Math.min(1d, grenade.elapsed / (double) grenade.totalTicks);
        Location at = grenade.origin.clone().add(grenade.target.toVector().subtract(grenade.origin.toVector()).multiply(t));
        at.add(0d, arcOffset(t, grenade.apex), 0d);
        return at;
    }

    /** 炸开：伤害 + 稳态场。 */
    private void explode(Player owner, Location at) {
        World world = at.getWorld();
        if (world == null) {
            return;
        }
        SayakaVfx.grenadeExplode(world, at, EXPLODE_RADIUS);
        SayakaSound.grenadeExplode(world, at);
        if (vitals != null) {
            for (Player victim : world.getNearbyPlayers(at, EXPLODE_RADIUS)) {
                if (victim == null || victim.equals(owner) || !isAlive(victim) || !isHostile(victim)) {
                    continue;
                }
                vitals.physicalDamage(victim, owner, EXPLODE_PHYSICAL);
                victim.setNoDamageTicks(0);
            }
        }
        int now = Bukkit.getCurrentTick();
        fields.add(new Field(world, at.clone(), now + FIELD_TICKS, now + FIELD_PULSE_TICKS));
    }

    /** 稳态场推进：边界观感 + 每秒结算（含重叠去重、自身增益）。 */
    private void advanceFields(Player owner, int now) {
        if (fields.isEmpty()) {
            return;
        }
        Iterator<Field> iterator = fields.iterator();
        while (iterator.hasNext()) {
            Field field = iterator.next();
            if (now > field.untilTick) {
                iterator.remove();
                continue;
            }
            if (now % 2 == 0) {
                SayakaVfx.fieldRing(field.world, field.center, FIELD_RADIUS, now);
            }
            if (now >= field.nextPulseTick) {
                field.nextPulseTick = now + FIELD_PULSE_TICKS;
                pulseField(owner, field, now);
            }
        }
    }

    /** 场的一拍：对场内敌人（去重后）结算真伤 / 失衡 / 缓慢；对在场内的自己给速度 + 再生。 */
    private void pulseField(Player owner, Field field, int now) {
        SayakaVfx.fieldTickFlash(field.world, field.center, FIELD_RADIUS);
        SayakaSound.fieldTick(field.world, field.center);
        boolean caughtAnyone = false;
        for (Player victim : field.world.getNearbyPlayers(field.center, FIELD_RADIUS)) {
            if (victim == null || victim.equals(owner) || !isAlive(victim) || !isHostile(victim)) {
                continue;
            }
            int last = lastFieldHitTick.getOrDefault(victim.getUniqueId(), Integer.MIN_VALUE / 2);
            if (now - last < FIELD_DEDUPE_TICKS) {
                continue;   //范围重叠不积累（跨场共享同一张去重表）
            }
            lastFieldHitTick.put(victim.getUniqueId(), now);
            caughtAnyone = true;
            if (vitals != null) {
                vitals.trueDamage(victim, owner, FIELD_TRUE_PER_PULSE);
            }
            SayakaImbalance.add(victim.getUniqueId(), FIELD_IMBALANCE_PER_PULSE, now, owner.getUniqueId());
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    FIELD_SLOW_TICKS, FIELD_SLOW_AMPLIFIER, true, false, true));
        }
        //语音：场真的压到人时喊一句（"命中 / 持续期间"那一栏）
        if (caughtAnyone && voice != null) {
            voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_GRENADE_FIELD);
        }
        //沙耶香身处场内：速度 4 + 再生 1
        if (owner.getWorld() == field.world
                && owner.getLocation().distanceSquared(field.center) <= FIELD_RADIUS * FIELD_RADIUS) {
            if (buff != null) {
                buff.applyPotionEffect(PotionEffectType.SPEED, SELF_BUFF_TICKS, SELF_SPEED_AMPLIFIER);
                buff.applyPotionEffect(PotionEffectType.REGENERATION, SELF_BUFF_TICKS, SELF_REGEN_AMPLIFIER);
            }
            SayakaVfx.fieldSelfBuff(field.world, owner.getLocation());
        }
    }

    /** 去重表定期清理（防长时间战斗内存堆积）。 */
    private void sweepDedupe(int now) {
        if (++dedupeSweepTick < 200) {
            return;
        }
        dedupeSweepTick = 0;
        lastFieldHitTick.entrySet().removeIf(entry -> now - entry.getValue() > 400);
    }

    // ───────── 施放 ─────────

    /** 右键 / 左键 / Q：发射一枚榴弹（30 格/秒、最远 20 格、耗 6 能量、射速 0.8 秒）。 */
    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || !canUse() || isCoolingDown()) {
            return;
        }
        if (ammo <= 0) {
            SayakaSound.grenadeDenied(owner.getWorld(), owner.getLocation());
            return;
        }
        if (energy == null || energy.current() < FIRE_ENERGY_COST) {
            SayakaSound.grenadeDenied(owner.getWorld(), owner.getLocation());
            if (voice != null) {
                voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_ENERGY_OUT);
            }
            return;
        }
        Location eye = owner.getEyeLocation();
        Location target = resolveLanding(eye);
        if (target == null || !energy.tryConsume(FIRE_ENERGY_COST)) {
            return;
        }
        ammo--;
        startCooldown(FIRE_INTERVAL_TICKS);
        Location origin = eye.clone();
        double horizontal = horizontalDistance(origin, target);
        int totalTicks = flightTicks(horizontal);
        double apex = arcApex(horizontal);
        grenades.add(new Grenade(origin, target, apex, totalTicks));
        //观感 / 手感：炮口后喷 + 击发重音 + 视角后座 + 屏幕轻微缩放
        SayakaVfx.grenadeMuzzle(owner.getWorld(), eye, eye.getDirection());
        SayakaSound.grenadeFire(owner.getWorld(), eye);
        SayakaSound.grenadeRecoil(owner.getWorld(), eye);
        applyRecoilKick(owner);
        applyScreenZoom(owner);
        if (voice != null) {
            voice.saySkill(owner, SayakaFloatingTextComponent.MOMENT_GRENADE_CAST);
        }
        requestRepaint();
    }

    /**
     * **后座抬镜**（发射当刻，一次）。
     *
     * <p>★ 2026-10-17 修复「释放时定身」：旧实现每刻 {@code teleport} 一次（共 6 次）来做"线性回落"，
     * 每次都让服务端重新确认位置 ⇒ 玩家在 0.3 秒内被反复拉回，表现就是"放炮后被钉住"。
     * 现在只投递**两拍**：起手抬一次 + {@value #RECOIL_TICKS} 刻后归位一次，
     * 且传送带 {@code VELOCITY_*} 旗标（保留动量）。
     */
    private void applyRecoilKick(Player owner) {
        recoilTicks = RECOIL_TICKS;
        recoilApplied = RECOIL_PITCH_DEGREES;
        Location loc = owner.getLocation();
        loc.setPitch(clampPitch(loc.getPitch() + RECOIL_PITCH_DEGREES));
        owner.teleport(loc, TELEPORT_KEEP_MOMENTUM);
    }

    /** 后座计时：只在**归位那一拍**再传送一次（保持期间不动镜头）。 */
    private void tickRecoil(Player owner) {
        if (recoilTicks <= 0) {
            return;
        }
        recoilTicks--;
        if (recoilTicks > 0) {
            return;
        }
        restoreRecoil(owner);
    }

    /**
     * **视角归位**：把当初抬上去的角度减回去。
     *
     * <p>读的是<b>当前</b> pitch（保持期间玩家转视角照样生效），只减掉本组件施加的偏移。
     */
    private void restoreRecoil(Player owner) {
        if (recoilApplied == 0d) {
            return;
        }
        Location loc = owner.getLocation();
        loc.setPitch(clampPitch(loc.getPitch() - recoilApplied));
        owner.teleport(loc, TELEPORT_KEEP_MOMENTUM);
        recoilApplied = 0d;
    }

    /** pitch 夹在 ±90（Bukkit 的合法范围）。 */
    private static float clampPitch(double pitch) {
        return (float) Math.max(-90d, Math.min(90d, pitch));
    }

    /**
     * "屏幕缩放"近似：给 {@value #ZOOM_TICKS} 刻的速度 I（客户端会把 FOV 轻微推近）。
     *
     * <p>★ 已有更强速度（稳态场的速度 4）时不施加 —— 不能让一次炮击把玩家的场增益顶掉。
     */
    private void applyScreenZoom(Player owner) {
        PotionEffect existing = owner.getPotionEffect(PotionEffectType.SPEED);
        if (existing != null && existing.getAmplifier() > 0) {
            return;
        }
        owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ZOOM_TICKS, 0, true, false, false));
    }

    /**
     * **求落点**（一定在地面上）：
     * <ol>
     *   <li>沿准星射线找第一个实心方块（最远 {@value #RANGE_MAX} 格）——打到墙 / 地形就以那里为准；</li>
     *   <li>20 格内没有阻挡 ⇒ 取射线末端；</li>
     *   <li>把该 XZ **向下扫到地面**（{@link #groundAt}）⇒ 落点永远贴地。</li>
     * </ol>
     * 这就是"朝天放炮也不会在空中炸开"的根：落点的 Y 来自地面扫描，而不是射线末端。
     */
    private static Location resolveLanding(Location eye) {
        if (eye == null || eye.getWorld() == null) {
            return null;
        }
        Vector direction = eye.getDirection();
        if (direction.lengthSquared() < 1.0E-6d) {
            return null;
        }
        Vector unit = direction.clone().normalize();
        double step = 0.25d;
        Location hit = null;
        for (double travelled = step; travelled <= RANGE_MAX + 1.0E-6d; travelled += step) {
            Location probe = eye.clone().add(unit.clone().multiply(travelled));
            if (isSolid(probe)) {
                hit = probe;
                break;
            }
        }
        Location raw = hit != null ? hit : eye.clone().add(unit.multiply(RANGE_MAX));
        return groundAt(raw.getWorld(), raw.getX(), raw.getZ(), raw.getY());
    }

    /**
     * **在指定 XZ 上向下找地面**：从 {@code fromY} 起逐格下探（最多 {@value #GROUND_SCAN_DEPTH} 格），
     * 返回第一个实心方块的**顶面**（+0.02 避免与方块同高）；扫到世界底部 ⇒ 用底部高度兜底。
     */
    private static Location groundAt(World world, double x, double z, double fromY) {
        if (world == null) {
            return null;
        }
        Location probe = new Location(world, x, Math.min(world.getMaxHeight() - 1d, fromY), z);
        for (int i = 0; i < GROUND_SCAN_DEPTH; i++) {
            Block block = probe.getBlock();
            if (block.getBoundingBox().getVolume() > 0d) {
                return new Location(world, x, block.getBoundingBox().getMaxY() + 0.02d, z);
            }
            if (probe.getY() <= world.getMinHeight()) {
                break;
            }
            probe = probe.clone().add(0d, -1d, 0d);
        }
        return new Location(world, x, world.getMinHeight() + 0.02d, z);
    }

    /**
     * 点到线段的距离（纯函数 ⇒ 离线可测）：投影参数夹在 {@code [0,1]}，退化线段按点距处理。
     *
     * <p>命中敌人用它而不是"到当前帧坐标的距离"——弹速 30 格/秒（1.5 格/刻）时，
     * 逐帧点距会从敌人两侧各跳过一次，造成"穿人而过"。
     */
    public static double pointSegmentDistance(Vector point, Vector from, Vector to) {
        if (point == null || from == null || to == null) {
            return Double.MAX_VALUE;
        }
        Vector segment = to.clone().subtract(from);
        double lengthSquared = segment.lengthSquared();
        if (lengthSquared < 1.0E-9d) {
            return point.distance(from);
        }
        double t = point.clone().subtract(from).dot(segment) / lengthSquared;
        t = Math.max(0d, Math.min(1d, t));
        Vector closest = from.clone().add(segment.multiply(t));
        return point.distance(closest);
    }

    // ───────── 纯函数（离线可测）─────────

    /** 总飞行刻数（纯函数）：水平距离 / 速度，向上取整，且不低于 {@value #FLIGHT_MIN_TICKS}。 */
    public static int flightTicks(double horizontalDistance) {
        return Math.max(FLIGHT_MIN_TICKS, (int) Math.ceil(Math.max(0d, horizontalDistance) / FLIGHT_SPEED_PER_TICK));
    }

    /**
     * 后座抬升角（纯函数）：发射当刻的抬镜角度（保持期不衰减，归位是另一拍）。
     *
     * <p>保留此读口是为了让"后座强度"仍然可被离线断言钉住；真正的时序见
     * {@link #applyRecoilKick} / {@link #tickRecoil}。
     */
    public static double recoilOffset(int remainingTicks) {
        return remainingTicks > 0 ? RECOIL_PITCH_DEGREES : 0d;
    }

    /** 抛物隆起高度（纯函数）：随水平距离缓增并封顶 {@value #ARC_APEX_MAX}。 */
    public static double arcApex(double horizontalDistance) {
        return Math.min(ARC_APEX_MAX, 1.0d + Math.max(0d, horizontalDistance) * 0.1d);
    }

    /** 抛物隆起量（纯函数）：{@code 4·apex·t·(1-t)}，两端为 0、中点为 apex。 */
    public static double arcOffset(double t, double apex) {
        double clamped = Math.max(0d, Math.min(1d, t));
        return 4d * Math.max(0d, apex) * clamped * (1d - clamped);
    }

    /** 两点水平距离（纯函数）。 */
    public static double horizontalDistance(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    // ───────── 闸门 / 外观 ─────────

    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /**
     * 图标：弹药数 = 物品个数；耗尽 ⇒ 结构空位 + 光效（与"冷却中/能量不足"两种结构空位区分）；
     * 未满仓 ⇒ 名称后缀显示下一发倒计时。
     * ★ 换材质分支必须重写识别键（工程铁律）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (!canUse() || isCoolingDown()) {
            //眩晕 = 红屏障 / 冷却 = 基类的结构空位画法：两态都保留基类，只补数量
            stack.setAmount(Math.max(1, Math.min(ammo, AMMO_MAX)));
            stack.setItemMeta(meta);
            return stack;
        }
        if (ammo <= 0) {
            stack.setType(Material.STRUCTURE_VOID);
            meta = stack.getItemMeta();
            if (meta == null) {
                return stack;
            }
            meta.displayName(nameWith(NamedTextColor.GRAY, " 榴弹耗尽"));
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            //★ setType 之后必须重写识别键（否则点击无反应 + 角色清除时残留）
            meta.getPersistentDataContainer().set(Skill.Utils.SKILL_KEY, PersistentDataType.STRING, getId());
            stack.setItemMeta(meta);
            stack.setAmount(1);
            return stack;
        }
        //有弹药 & 就绪
        if (currentEnergy() < getEnergyCost()) {
            //能量不足：基类是"结构空位 + 无光效"，按规范补光效（与冷却区分）
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } else if (ammo < AMMO_MAX) {
            meta.displayName(nameWith(NamedTextColor.GREEN, " 下一发 " + secondsText(regenRemainingTicks())));
        }
        stack.setItemMeta(meta);
        stack.setAmount(Math.max(1, Math.min(ammo, AMMO_MAX)));
        return stack;
    }

    /** 名称 + 后缀（与基类同口径：着色 + 加粗）。 */
    private Component nameWith(NamedTextColor color, String suffix) {
        return getDisplayName().color(color).decorate(TextDecoration.BOLD)
                .append(Component.text(suffix).color(color).decorate(TextDecoration.BOLD));
    }

    /** 刻 → {@code "x.xs"}。 */
    private static String secondsText(int ticks) {
        return String.format("%.1fs", Math.max(0, ticks) / 20f);
    }

    /** 下一发榴弹的剩余刻数。 */
    public int regenRemainingTicks() {
        return AMMO_REGEN_TICKS - regenTick;
    }

    /** 当前弹药（读口 / 单测）。 */
    public int ammo() {
        return ammo;
    }

    /** 弹药数 / 倒计时秒数变了才请求重绘（非冷却期没人替我们刷）。 */
    private void requestRenderIfChanged() {
        if (render == null) {
            return;
        }
        String signature = ammo + "/" + (regenRemainingTicks() / 20);
        if (!signature.equals(lastRenderSignature)) {
            lastRenderSignature = signature;
            render.requestRepaint();
        }
    }

    private void requestRepaint() {
        lastRenderSignature = "";
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 内部 ─────────

    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }
}
