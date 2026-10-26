package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「芹泽沙耶香 · 瓦尔基里」大招：**剑刃风暴**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>解放影切进入剑刃风暴：共 <b>三段</b>、每段间隔 <b>1 秒</b>；</li>
 *   <li>同时进入<b>纳刀状态</b>且<b>移动限制解除</b>；间隔中<b>持续居合判定</b>；</li>
 *   <li>位移规则：<b>直接瞬移至敌人身后</b>（无敌人可瞬移时改为向前位移 {@value #FORWARD_STEP} 格）；</li>
 *   <li>每段对周围 {@value #STAGE_RADIUS} 格【r=8】敌人 <b>15 点物理 + 10 点失衡</b>；
 *       对处于失衡爆发的敌人每段额外 <b>5 点真实伤害</b>；</li>
 *   <li>三段全部释放后：周围 {@value #FINAL_RADIUS} 米【12 米】内所有敌人 <b>黑暗 10 秒</b>；</li>
 *   <li>释放期间获得<b>抗性 2</b>、<b>免疫控制</b>；</li>
 *   <li>冷却 <b>60 秒</b>、消耗 <b>30 点能量</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报四：入范围锁定提示（2026-10-17 需求 8）</h2>
 * 风暴期间每刻扫描 {@value #STAGE_RADIUS} 格内的敌人：首次进入播一次收束环 + "咔"声（边沿判据），
 * 持续在范围内则每刻画 <b>2 枚红黑菱形粒子</b>（反向翻滚）；离开 / 死亡即摘除。
 * 只在提示层，不改变伤害与段数。
 *
 * <h2>★★ 口径申报一：三段时刻 = t0+20 / t0+40 / t0+60（起手式后一秒出第一段）</h2>
 * 「共三段、每段间隔 1 秒」⇒ 施放当刻是"起手式"（只开状态、不结算伤害），第一段在 <b>1 秒后</b>，
 * 其后每 {@value #STAGE_INTERVAL_TICKS} 刻一段；第三段结算完的<b>同刻</b>收尾（黑暗）。
 * ★ 首段推迟一秒的理由（与语音有关）：起手台词与"一。"若落在同刻，池的节流会随机丢掉一句 ——
 * 推迟一拍后两条台词恰好各隔 20 刻，"起手 → 一 → 二 → 三 → 收尾"干净利落。
 *
 * <h2>★★ 口径申报二：「瞬移至敌人身后」的择地与兜底</h2>
 * 目标 = {@value #SEEK_RADIUS} 格内最近的敌对玩家；落点先试"沿自己→敌人方向越过其
 * {@value #TELEPORT_BEHIND} 格"，被挡（脚/头实心或无支撑）则试反侧，再不行 ⇒ 兜底为
 * <b>沿视线水平方向向前 {@value #FORWARD_STEP} 格</b>（采样安全落点；同样被挡 ⇒ 原地斩）。
 * 无论走哪条路径，每段都必然结算（"无需命中刷新"）。
 *
 * <h2>★★ 口径申报三：「免疫控制」= 逐刻拔掉 STUN 账本 + 控制类原版效果</h2>
 * 框架级控制 = {@code BuffType.STUN}（技能闸门 + 主武器闸门），原版级 = 眩晕口径的
 * {@code SLOWNESS 255 / BLINDNESS / DARKNESS}（外加负等级 {@code JUMP_BOOST 128}）。
 * 风暴期间逐刻移除这四类（JUMP_BOOST 仅在增幅 ≥ 128 即负等级时移除，不误伤正跳提升）。
 * 抗性 2 = {@code RESISTANCE amplifier 1}，按 10 刻节拍刷新（40 刻时长防闪断）。
 */
public class SayakaBladeStormSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "sayaka_blade_storm_skill";

    // ───────── 数值口径 ─────────

    /** 段数（需求：共三段）。 */
    public static final int STAGES = 3;

    /** 段间隔（刻）—— 需求：每段间隔 1 秒。 */
    public static final int STAGE_INTERVAL_TICKS = 20;

    /** 每段范围（格）—— 需求：r=8。 */
    public static final double STAGE_RADIUS = 8.0d;

    /** 每段物理伤害（需求：15 点）。 */
    public static final double STAGE_PHYSICAL = 15d;

    /** 每段失衡积累（需求：10 点）。 */
    public static final int STAGE_IMBALANCE = 10;

    /** 对失衡爆发敌人的每段额外真实伤害（需求：5 点）。 */
    public static final double BURST_BONUS_TRUE = 5d;

    /** 收尾黑暗范围（米）—— 需求：12 米。 */
    public static final double FINAL_RADIUS = 12.0d;

    /** 黑暗持续（刻）—— 需求：10 秒。 */
    public static final int FINAL_DARKNESS_TICKS = 200;

    /** 无敌人可瞬移时的向前位移（格）—— 需求：每次释放斩击时向前位移 4 格。 */
    public static final double FORWARD_STEP = 4.0d;

    /** 瞬移目标搜索半径（格）—— 比段范围略大，允许"突进到视线边缘的敌人"。 */
    public static final double SEEK_RADIUS = 12.0d;

    /** 瞬移落点：越过敌人多少格（格）。 */
    public static final double TELEPORT_BEHIND = 1.2d;

    /** 抗性增幅（需求：抗性 2 ⇒ amplifier 1）。 */
    public static final int RESISTANCE_AMPLIFIER = 1;

    /** 冷却（刻）—— 需求：60 秒。 */
    public static final int COOLDOWN_TICKS = 1200;

    /** 能量消耗（需求：30 点）。 */
    public static final int ENERGY_COST = 30;

    /** 采样步长（格）。 */
    private static final double SAMPLE_STEP = 0.4d;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private FactionComponent faction;
    private SayakaBladeMainWeapon weapon;
    private SayakaFloatingTextComponent voice;

    // ───────── 运行期状态 ─────────

    /** 是否处于剑刃风暴。 */
    private boolean stormActive;

    /** 已结算段数（0..{@value #STAGES}）。 */
    private int stageIndex;

    /** 下一段结算刻。 */
    private int nextStageTick;

    /** 已被"锁定"的敌人（uuid → 首次进入的相位；只用于边沿播锁定反馈）。 */
    private final Map<UUID, Integer> locked = new HashMap<>();

    /** 锁定粒子的翻滚相位（逐刻递增）。 */
    private int lockPhase;

    public SayakaBladeStormSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（2026-10-17 需求：模型换成<b>蜘蛛网</b>{@code Material.COBWEB}）。 */
    public static final class Specification extends Skill.Specification<SayakaBladeStormSkill> {

        public Specification() {
            super(Component.text("剑刃风暴"),
                    List.of(Component.text("解放影切进入剑刃风暴：共三段、每段间隔 1 秒；同时进入纳刀状态且移动限制解除"),
                            Component.text("间隔中持续居合判定；位移改为直接瞬移至敌人身后（无敌人则向前位移 4 格）"),
                            Component.text("每段对周围 8 格敌人 15 点物理 + 10 点失衡；对失衡爆发的敌人每段额外 5 点真实伤害"),
                            Component.text("进入范围的敌人会被两枚红黑翻滚菱形粒子锁定（进入瞬间有锁定提示）"),
                            Component.text("三段全部释放后：周围 12 米内所有敌人黑暗 10 秒"),
                            Component.text("释放期间获得抗性 2、免疫控制；冷却 60 秒、消耗 30 点能量")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.COBWEB);
            requires(VitalsComponent.class).requires(EnergyComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(SayakaBladeMainWeapon.class).requires(SayakaFloatingTextComponent.class);
        }

        @Override
        public SayakaBladeStormSkill create(String id, ComponentServicesPort services) {
            return new SayakaBladeStormSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        weapon = svc().components().get(SayakaBladeMainWeapon.class);
        voice = svc().components().get(SayakaFloatingTextComponent.class);
        stormActive = false;
        stageIndex = 0;
        nextStageTick = 0;
        locked.clear();
        lockPhase = 0;
    }

    @Override
    public void stop() {
        if (stormActive && weapon != null) {
            weapon.endStorm();
        }
        stormActive = false;
        locked.clear();
        vitals = null;
        energy = null;
        buff = null;
        faction = null;
        weapon = null;
        voice = null;
    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        if (!stormActive) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            finishStorm(false);     //主体离场：收工（不再补黑暗）
            return;
        }
        int now = Bukkit.getCurrentTick();
        //① 期间维持：抗性 2 + 免疫控制
        maintainStormEffects(owner, now);
        //①.5 入范围锁定提示（需求 8：r8 内敌人被 2 个红黑随机翻滚菱形锁定）
        updateLockOn(owner);
        //② 段推进
        while (stormActive && stageIndex < STAGES && now >= nextStageTick) {
            resolveStage(owner, stageIndex);
            stageIndex++;
            if (stageIndex < STAGES) {
                nextStageTick += STAGE_INTERVAL_TICKS;
            } else {
                finalizeStorm(owner);   //三段释放完的同刻收尾：黑暗 + 结束
            }
        }
    }

    /** 风暴期间：抗性 2（按节拍刷新）+ 免疫控制（STUN 账本 + 控制类原版效果逐刻移除）。 */
    private void maintainStormEffects(Player owner, int now) {
        if (buff != null) {
            if (now % 10 == 0) {
                buff.applyPotionEffect(PotionEffectType.RESISTANCE, 40, RESISTANCE_AMPLIFIER);
            }
            buff.remove(BuffType.STUN);     //免疫控制：框架级眩晕账本直接摘掉
        }
        owner.removePotionEffect(PotionEffectType.SLOWNESS);
        owner.removePotionEffect(PotionEffectType.BLINDNESS);
        owner.removePotionEffect(PotionEffectType.DARKNESS);
        PotionEffect jump = owner.getPotionEffect(PotionEffectType.JUMP_BOOST);
        if (jump != null && jump.getAmplifier() >= 128) {
            owner.removePotionEffect(PotionEffectType.JUMP_BOOST);   //只拔负等级（禁移用），不误伤正跳提升
        }
    }

    /**
     * **入范围锁定提示**（2026-10-17 需求 8）。
     *
     * <p>每刻扫描 {@value #STAGE_RADIUS} 格内的敌人：
     * <ul>
     *   <li>**首次进入**（边沿判据：{@link #locked} 里还没有）⇒ 一个收束环 + 一声"咔"（避免每刻重复播）；</li>
     *   <li>**持续在范围**⇒ 每刻画 2 枚红黑菱形粒子（{@link SayakaVfx#stormLock}），
     *       相位逐刻递增 ⇒ 两枚菱形反向翻滚（"随机翻滚"的观感）；</li>
     *   <li>**离开 / 死亡**⇒ 从锁定表里摘掉（再进来会重新播锁定反馈）。</li>
     * </ul>
     */
    private void updateLockOn(Player owner) {
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        lockPhase++;
        Set<UUID> present = new HashSet<>();
        for (Player victim : world.getNearbyPlayers(owner.getLocation(), STAGE_RADIUS)) {
            if (victim == null || victim.equals(owner) || !isAlive(victim) || !isHostile(victim)) {
                continue;
            }
            UUID id = victim.getUniqueId();
            present.add(id);
            if (!locked.containsKey(id)) {
                locked.put(id, lockPhase);
                SayakaVfx.stormLockOn(world, victim.getLocation());
                SayakaSound.stormLockOn(world, victim.getLocation(), stageIndex);
            }
            SayakaVfx.stormLock(world, victim.getLocation(), lockPhase, id.hashCode());
        }
        locked.keySet().retainAll(present);
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || !canUse() || isCoolingDown() || stormActive) {
            return;
        }
        if (energy == null || energy.current() < ENERGY_COST || !energy.tryConsume(ENERGY_COST)) {
            SayakaSound.grenadeDenied(owner.getWorld(), owner.getLocation());
            if (voice != null) {
                voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_ENERGY_OUT);
            }
            return;
        }
        stormActive = true;
        stageIndex = 0;
        locked.clear();
        lockPhase = 0;
        nextStageTick = Bukkit.getCurrentTick() + STAGE_INTERVAL_TICKS;   //首段推迟一拍（口径申报一）
        startCooldown(COOLDOWN_TICKS);
        if (weapon != null) {
            weapon.beginStorm();    //强制纳刀 + 解除移动限制 + 让位抗性
        }
        SayakaSound.stormStart(owner.getWorld(), owner.getLocation());
        SayakaVfx.stormStage(owner.getWorld(), owner.getLocation(), STAGE_RADIUS, 0);
        if (voice != null) {
            voice.saySkill(owner, SayakaFloatingTextComponent.MOMENT_STORM_START);
        }
    }

    // ───────── 单段结算 ─────────

    /** 一段：位移（瞬移敌后 / 向前 4 格）→ r8 范围伤害 → 观感。 */
    private void resolveStage(Player owner, int stage) {
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        Location from = owner.getLocation().clone();
        Player target = nearestEnemy(owner);
        boolean resolved = false;
        if (target != null) {
            Location behind = behindSpot(owner, target);
            if (behind != null) {
                owner.teleport(behind);
                owner.setFallDistance(0f);
                SayakaVfx.blink(world, from, behind);
                SayakaSound.dash(world, behind);
                resolved = true;
            }
        }
        if (!resolved) {
            Location forward = forwardSpot(owner, FORWARD_STEP);
            if (forward != null) {
                owner.teleport(forward);
                owner.setFallDistance(0f);
                SayakaVfx.blink(world, from, forward);
                SayakaSound.dash(world, forward);
            }
        }
        //r8 范围伤害（以位移后的位置为心）
        Location center = owner.getLocation();
        int now = Bukkit.getCurrentTick();
        if (vitals != null) {
            for (Player victim : world.getNearbyPlayers(center, STAGE_RADIUS)) {
                if (victim == null || victim.equals(owner) || !isAlive(victim) || !isHostile(victim)) {
                    continue;
                }
                vitals.physicalDamage(victim, owner, STAGE_PHYSICAL);
                if (SayakaImbalance.isBursting(victim.getUniqueId(), now)) {
                    vitals.trueDamage(victim, owner, BURST_BONUS_TRUE);
                }
                SayakaImbalance.add(victim.getUniqueId(), STAGE_IMBALANCE, now, owner.getUniqueId());
                victim.setNoDamageTicks(0);
            }
        }
        SayakaVfx.stormStage(world, center, STAGE_RADIUS, stage);
        SayakaSound.stormStage(world, center, stage);
        //语音数拍（一 / 二 / 三）
        if (voice != null) {
            String moment = switch (stage) {
                case 0 -> SayakaFloatingTextComponent.MOMENT_STORM_STAGE_1;
                case 1 -> SayakaFloatingTextComponent.MOMENT_STORM_STAGE_2;
                default -> SayakaFloatingTextComponent.MOMENT_STORM_STAGE_3;
            };
            voice.saySpecial(owner, moment);
        }
    }

    /** 收尾：三段释放完 —— 12 米内黑暗 10 秒 + 结束风暴。 */
    private void finalizeStorm(Player owner) {
        World world = owner.getWorld();
        if (world != null) {
            for (Player victim : world.getNearbyPlayers(owner.getLocation(), FINAL_RADIUS)) {
                if (victim == null || victim.equals(owner) || !isAlive(victim) || !isHostile(victim)) {
                    continue;
                }
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS,
                        FINAL_DARKNESS_TICKS, 0, true, false, true));
            }
            SayakaVfx.stormFinal(world, owner.getLocation(), FINAL_RADIUS);
            SayakaSound.stormFinal(world, owner.getLocation());
        }
        if (voice != null) {
            voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_STORM_END);
        }
        finishStorm(true);
    }

    /** 结束风暴（{@code withFeedback} = 是否播"未收尾"的杂音；收尾路径已在 finalizeStorm 播过）。 */
    private void finishStorm(boolean withFeedback) {
        stormActive = false;
        stageIndex = 0;
        locked.clear();
        if (weapon != null) {
            weapon.endStorm();
        }
    }

    // ───────── 几何 ─────────

    /** 最近的敌对玩家（半径 {@value #SEEK_RADIUS}）。 */
    private Player nearestEnemy(Player owner) {
        World world = owner.getWorld();
        if (world == null) {
            return null;
        }
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : world.getNearbyPlayers(owner.getLocation(), SEEK_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate) || !isHostile(candidate)) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(owner.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 敌人身后的落点：先试"越过敌人"侧，再试反侧；都被挡 ⇒ {@code null}。 */
    private Location behindSpot(Player owner, Player enemy) {
        Vector dir = horizontal(enemy.getLocation().toVector().subtract(owner.getLocation().toVector()));
        if (dir == null) {
            return null;
        }
        Location continuing = enemy.getLocation().clone().add(dir.clone().multiply(TELEPORT_BEHIND));
        if (isSafeStanding(continuing)) {
            return continuing;
        }
        Location opposite = enemy.getLocation().clone().add(dir.clone().multiply(-TELEPORT_BEHIND));
        if (isSafeStanding(opposite)) {
            return opposite;
        }
        return null;
    }

    /** 沿视线水平方向向前采样安全落点。 */
    private Location forwardSpot(Player owner, double distance) {
        Vector axis = horizontal(owner.getEyeLocation().getDirection());
        if (axis == null) {
            axis = horizontal(owner.getFacing().getDirection());
        }
        if (axis == null) {
            return null;
        }
        Location origin = owner.getLocation();
        Location best = null;
        for (double t = SAMPLE_STEP; t <= distance + 1.0E-6d; t += SAMPLE_STEP) {
            Location candidate = origin.clone().add(axis.clone().multiply(t));
            if (isStandable(candidate)) {
                best = candidate;
            }
        }
        return best;
    }

    /** 落点可站：脚 + 头非实心、脚下有支撑。 */
    private static boolean isSafeStanding(Location spot) {
        if (spot == null || spot.getWorld() == null) {
            return false;
        }
        Location below = spot.clone().add(0d, -0.2d, 0d);
        return isStandable(spot) && isSolid(below);
    }

    /** 脚格与头格都可通过。 */
    private static boolean isStandable(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block head = at.clone().add(0d, 1d, 0d).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    /** 是否被实心方块占据。 */
    private static boolean isSolid(Location at) {
        return at.getBlock().getBoundingBox().getVolume() > 0d;
    }

    // ───────── 纯函数（离线可测）─────────

    /** 第 {@code stage} 段（0 起）相对施放时刻的应结算刻 —— 首段在起手 1 秒后（口径申报一）。 */
    public static int stageTick(int stage) {
        return (Math.max(0, stage) + 1) * STAGE_INTERVAL_TICKS;
    }

    /** {@code stage}（0 起）是否为最后一段。 */
    public static boolean stageIsFinale(int stage) {
        return stage >= STAGES - 1;
    }

    /** 距离平方是否在半径内（纯函数）。 */
    public static boolean inRadius(double distanceSquared, double radius) {
        return distanceSquared <= radius * radius;
    }

    // ───────── 闸门 ─────────

    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /** 是否处于剑刃风暴（读口 / 探针）。 */
    public boolean isStormActive() {
        return stormActive;
    }

    // ───────── 内部 ─────────

    private static Vector horizontal(Vector vector) {
        if (vector == null) {
            return null;
        }
        Vector flat = new Vector(vector.getX(), 0d, vector.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            return null;
        }
        return flat.normalize();
    }

    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }
}
