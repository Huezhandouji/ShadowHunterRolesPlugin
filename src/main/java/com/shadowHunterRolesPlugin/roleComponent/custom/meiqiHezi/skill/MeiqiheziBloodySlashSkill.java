package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziChargeComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziMadness;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziSanDrainComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 二技能：**血腥连斩**（模型：金锭）。
 *
 * <h2>需求</h2>
 * <ul>
 *   <li>向前突进 <b>10 格 / 0.2 秒</b>（4 刻，每刻 2.5 格）；</li>
 *   <li>沿途 <b>r=2</b> 的敌人：<b>12 物理 + 4~6 特殊值</b>；</li>
 *   <li>尝试消耗充能（**最多 1 层**）：沿途敌人各追加 <b>3 真实伤害</b>，自身回复 <b>3 生命</b>
 *       （SanTE ≤ 50 时 +2）；</li>
 *   <li>成功命中任一敌人 ⇒ **CD -6 秒**；</li>
 *   <li>CD 8 秒、耗能 5。</li>
 * </ul>
 *
 * <h2>★★ 口径申报：逐刻位移用 teleport，不用 setVelocity</h2>
 * 需求是"0.2 秒走完 10 格"的**定量**位移；速度法受摩擦/击退/地形影响，落点不可控。
 * 这里逐刻 {@code teleport} 到 {@code 起点 + dir × 2.5 × i}，撞墙即提前收束，
 * 并把 {@code fallDistance} 清零（工程口径：接管位置必须每刻清坠落距离）。
 */
public class MeiqiheziBloodySlashSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "meiqihezi_skill_bloody_slash";

    /** 突进总距离（格）。 */
    public static final double DASH_DISTANCE = 10d;

    /** 突进刻数（需求 0.2 秒）。 */
    public static final int DASH_TICKS = 4;

    /** 每刻位移 = 10 / 4 = 2.5 格。 */
    public static final double STEP = DASH_DISTANCE / DASH_TICKS;

    /** 沿途伤害半径（需求 r=2）。 */
    public static final double HIT_RADIUS = 2d;

    /** 物理伤害（需求 12）。 */
    public static final double PHYSICAL = 12d;

    /** 特殊值伤害区间（需求 4~6）。 */
    public static final int SANTE_MIN = 4;
    public static final int SANTE_MAX = 6;

    /** 每层充能的追加真实伤害（需求 3）。 */
    public static final double CHARGE_TRUE = 3d;

    /** 消耗一层充能回复的生命（需求 3）。 */
    public static final double CHARGE_HEAL = 3d;

    /** 命中后缩短的冷却（需求 6 秒）。 */
    public static final int COOLDOWN_REFUND_TICKS = 120;

    /** 10 档：控制余波半径。 */
    public static final double ECHO_RADIUS = 6d;

    private EnergyComponent energy;
    private BuffComponent buff;
    private VitalsComponent vitals;
    private SanTEComponent sante;
    private FactionComponent faction;
    private TaskComponent timers;
    private MeiqiheziChargeComponent charge;
    private MeiqiheziSanDrainComponent sanDrain;
    private MeiqiheziFloatingTextComponent voice;

    private ScheduledHandle dashTask;
    private final Set<UUID> hitOnce = new HashSet<>();

    public MeiqiheziBloodySlashSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符：CD 160 刻 / 耗能 5。 */
    public static final class Specification extends Skill.Specification<MeiqiheziBloodySlashSkill> {

        public Specification() {
            super(
                    Component.text("血腥连斩"),
                    List.of(
                            Component.text("0.2 秒突进 10 格"),
                            Component.text("沿途 r=2：12 物理 + 4~6 特殊值"),
                            Component.text("消耗 1 层充能：+3 真伤，回复 3 生命"),
                            Component.text("命中敌人则 CD 缩短 6 秒")
                    ),
                    160,
                    5,
                    Material.GOLD_INGOT
            );
            requires(EnergyComponent.class).requires(BuffComponent.class).requires(VitalsComponent.class)
                    .requires(SanTEComponent.class).requires(FactionComponent.class)
                    .requires(TaskComponent.class).requires(MeiqiheziChargeComponent.class)
                    .requires(MeiqiheziSanDrainComponent.class);
            requiresOptional(MeiqiheziFloatingTextComponent.class);
        }

        @Override
        public MeiqiheziBloodySlashSkill create(String id, ComponentServicesPort services) {
            return new MeiqiheziBloodySlashSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        energy = getComponent(EnergyComponent.class);
        buff = getComponent(BuffComponent.class);
        vitals = getComponent(VitalsComponent.class);
        sante = getComponent(SanTEComponent.class);
        faction = getComponent(FactionComponent.class);
        timers = getComponent(TaskComponent.class);
        charge = getComponent(MeiqiheziChargeComponent.class);
        sanDrain = getComponent(MeiqiheziSanDrainComponent.class);
        voice = getComponent(MeiqiheziFloatingTextComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || !buff.canCastSkill()) {
            return;
        }
        if (!energy.tryConsume(getEnergyCost())) {
            return;
        }

        hitOnce.clear();
        startCooldown();

        final Location start = caster.getLocation().clone();
        final Vector dir = start.getDirection().setY(0d).normalize();
        final int[] tick = {0};

        MeiqiheziSound.dash(caster);
        if (voice != null) {
            voice.sayMoment(caster, MeiqiheziFloatingTextComponent.MOMENT_SKILL_BLOODY);
        }

        dashTask = timers.addScheduleRepeating(this, 1L, 1L, () -> {
            Player player = svc().self().player();
            if (player == null || !player.isOnline() || player.isDead()) {
                finishDash(caster);
                return;
            }
            tick[0]++;
            Location next = start.clone().add(dir.clone().multiply(STEP * tick[0]));
            if (isBlocked(next)) {
                finishDash(caster);
                return;
            }
            player.teleport(next);
            player.setFallDistance(0f);
            MeiqiheziVfx.dashTrail(next);
            damageAround(player, next);
            if (tick[0] >= DASH_TICKS) {
                finishDash(caster);
            }
        });
    }

    private boolean isBlocked(Location location) {
        return location.getBlock().getType().isSolid()
                || location.clone().add(0d, 1d, 0d).getBlock().getType().isSolid();
    }

    private void damageAround(Player caster, Location center) {
        int san = sante.current();
        for (Player victim : center.getNearbyPlayers(HIT_RADIUS)) {
            if (victim == null || victim.equals(caster) || !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            if (!hitOnce.add(victim.getUniqueId())) {
                continue;
            }
            vitals.physicalDamage(victim, caster, PHYSICAL);
            int special = SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1);
            sante.decreaseSanTE(victim.getUniqueId(), special);
            MeiqiheziVfx.hitMarker(victim, victim.getTicksLived());
        }
        MeiqiheziSound.hit(caster);
    }

    private void finishDash(Player caster) {
        if (dashTask != null) {
            dashTask.cancel();
            dashTask = null;
        }
        if (!hitOnce.isEmpty()) {
            int san = sante.current();
            if (sanDrain != null) {
                sanDrain.onHit();
            }
            int used = charge.consume(1);
            if (used > 0) {
                for (UUID id : hitOnce) {
                    Player victim = org.bukkit.Bukkit.getPlayer(id);
                    if (victim != null && victim.isOnline() && !victim.isDead()) {
                        vitals.trueDamage(victim, caster, CHARGE_TRUE * used);
                    }
                }
                vitals.heal(caster, CHARGE_HEAL + MeiqiheziMadness.healBonus(san));
                MeiqiheziVfx.chargeBurst(caster);
            }
            if (MeiqiheziMadness.controlEcho(san)) {
                applyControlEcho(caster);
            }
            startCooldown(Math.max(0, getCooldownTicks() - COOLDOWN_REFUND_TICKS));
            MeiqiheziSound.chargedHit(caster);
        }
    }

    private void applyControlEcho(Player caster) {
        for (Player target : caster.getLocation().getNearbyPlayers(ECHO_RADIUS)) {
            if (target == null || target.equals(caster) || !faction.isHostileTo(target.getUniqueId())) {
                continue;
            }
            buff.applyPotionEffectTo(target, PotionEffectType.SLOWNESS, 20, 0, true, false);
        }
        buff.applyPotionEffect(PotionEffectType.ABSORPTION, 100, 0, true, false);
        MeiqiheziVfx.controlEcho(caster.getLocation());
    }

    @Override
    public void stop() {
        if (dashTask != null) {
            dashTask.cancel();
            dashTask = null;
        }
        hitOnce.clear();
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy == null ? 0 : energy.current();
    }
}
