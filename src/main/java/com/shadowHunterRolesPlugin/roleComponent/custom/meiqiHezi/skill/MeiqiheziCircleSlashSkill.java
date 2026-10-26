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
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 三技能：**圆弧斩**（模型：烈焰膏）。
 *
 * <h2>需求</h2>
 * <ul>
 *   <li>引导 <b>0.8 秒</b>：自身<b>缓慢 IV + 抗性 III</b>；</li>
 *   <li>引导完毕：弓箭命中声 ⇒ 对周围 <b>8 格</b>（SanTE ≤ 40 时 <b>9 格</b>）敌人
 *       <b>14 物理 + 4~6 特殊值</b>，每命中一名敌人回复 <b>4 生命</b>；</li>
 *   <li>尝试消耗充能（最多 1 层）：范围内敌人各追加 <b>3 真实伤害</b>，每名敌人额外回复 <b>4 生命</b>
 *       （SanTE ≤ 50 时再 +2）；</li>
 *   <li>CD 6 秒、耗能 10；</li>
 *   <li>表现：起手金红边缘 → 从左手边甩出红金冲击波快速转满一圈 → 命中者身上翻滚菱形。</li>
 * </ul>
 *
 * <h2>★★ 口径申报：引导期间的减速/抗性用 Buff 组件记账</h2>
 * 走 {@code buff.applyPotionEffect} 而不是 {@code player.addPotionEffect}：角色被清 / 换角色时
 * 由 Buff 组件统一回收，不会把"缓慢 IV"留在玩家身上。
 */
public class MeiqiheziCircleSlashSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "meiqihezi_skill_circle_slash";

    /** 引导时长（刻）—— 需求 0.8 秒。 */
    public static final int CHANNEL_TICKS = 16;

    /** 引导期间缓慢等级 IV ⇒ amplifier 3。 */
    public static final int CHANNEL_SLOW_AMPLIFIER = 3;

    /** 引导期间抗性等级 III ⇒ amplifier 2。 */
    public static final int CHANNEL_RESIST_AMPLIFIER = 2;

    /** 基础范围（需求 8 格）。 */
    public static final double RADIUS = 8d;

    /** 40 档额外范围（需求 +1 格）。 */
    public static final double RADIUS_BONUS = 1d;

    /** 物理伤害（需求 14）。 */
    public static final double PHYSICAL = 14d;

    /** 特殊值伤害区间（需求 4~6）。 */
    public static final int SANTE_MIN = 4;
    public static final int SANTE_MAX = 6;

    /** 每名敌人回复（需求 4）。 */
    public static final double HEAL_PER_ENEMY = 4d;

    /** 消耗一层充能：每名敌人追加真实伤害（需求 3）。 */
    public static final double CHARGE_TRUE = 3d;

    /** 消耗一层充能：每名敌人额外回复（需求 4）。 */
    public static final double CHARGE_HEAL_PER_ENEMY = 4d;

    /** 冲击波动画刻数（一圈）。 */
    public static final int SWEEP_TICKS = 10;

    /** 10 档控制余波半径。 */
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

    private ScheduledHandle channelVfxTask;
    private ScheduledHandle detonateTask;
    private ScheduledHandle sweepTask;

    public MeiqiheziCircleSlashSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符：CD 120 刻 / 耗能 10。 */
    public static final class Specification extends Skill.Specification<MeiqiheziCircleSlashSkill> {

        public Specification() {
            super(
                    Component.text("圆弧斩"),
                    List.of(
                            Component.text("引导 0.8 秒（缓慢 IV + 抗性 III）"),
                            Component.text("周围 8 格：14 物理 + 4~6 特殊值"),
                            Component.text("每命中一名敌人回复 4 生命"),
                            Component.text("消耗 1 层充能：每敌 +3 真伤、+4 回复")
                    ),
                    120,
                    10,
                    Material.MAGMA_CREAM
            );
            requires(EnergyComponent.class).requires(BuffComponent.class).requires(VitalsComponent.class)
                    .requires(SanTEComponent.class).requires(FactionComponent.class)
                    .requires(TaskComponent.class).requires(MeiqiheziChargeComponent.class)
                    .requires(MeiqiheziSanDrainComponent.class);
            requiresOptional(MeiqiheziFloatingTextComponent.class);
        }

        @Override
        public MeiqiheziCircleSlashSkill create(String id, ComponentServicesPort services) {
            return new MeiqiheziCircleSlashSkill(id, services, this);
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
        final Player caster = svc().self().player();
        if (caster == null || !buff.canCastSkill()) {
            return;
        }
        if (!energy.tryConsume(getEnergyCost())) {
            return;
        }

        startCooldown();

        buff.applyPotionEffect(PotionEffectType.SLOWNESS, CHANNEL_TICKS + 6, CHANNEL_SLOW_AMPLIFIER, true, false);
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, CHANNEL_TICKS + 6, CHANNEL_RESIST_AMPLIFIER, true, false);
        MeiqiheziSound.channelStart(caster);
        if (voice != null) {
            voice.sayMoment(caster, MeiqiheziFloatingTextComponent.MOMENT_SKILL_CIRCLE);
        }

        // 引导开始：真实半径的范围红环 + 第一个字
        final double radius = RADIUS
                + (MeiqiheziMadness.circleReachBonus(sante.current()) ? RADIUS_BONUS : 0d);
        MeiqiheziVfx.channelRange(caster.getLocation(), radius, 0);
        showChannelText(caster, "圆");

        final int[] phase = {0};
        channelVfxTask = timers.addScheduleRepeating(this, 1L, 2L, () -> {
            Player player = svc().self().player();
            if (player == null || !player.isOnline() || player.isDead()) {
                cancelChannel(player);
                return;
            }
            phase[0]++;
            MeiqiheziVfx.channelRange(player.getLocation(), radius, phase[0]);
            switch (phase[0]) {
                case 2 -> {
                    showChannelText(player, "圆弧");
                    MeiqiheziSound.channelTick(player, 1);
                }
                case 4 -> {
                    showChannelText(player, "圆弧~");
                    MeiqiheziSound.channelTick(player, 2);
                }
                case 6 -> MeiqiheziSound.channelTick(player, 3);
                default -> {
                }
            }
        });

        detonateTask = timers.addScheduleLater(this, CHANNEL_TICKS, () -> detonate(caster, radius));
    }

    private void detonate(Player caster, double radius) {
        if (channelVfxTask != null) {
            channelVfxTask.cancel();
            channelVfxTask = null;
        }
        if (caster == null || !caster.isOnline() || caster.isDead()) {
            return;
        }

        int san = sante.current();
        Location center = caster.getLocation();
        Vector forward = center.getDirection().setY(0d);
        if (forward.lengthSquared() < 1e-6) {
            forward = new Vector(0, 0, 1);
        }
        forward.normalize();
        List<Player> victims = new ArrayList<>();

        for (Player victim : center.getNearbyPlayers(radius)) {
            if (victim == null || victim.equals(caster) || !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            victims.add(victim);
            vitals.physicalDamage(victim, caster, PHYSICAL);
            int special = SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1);
            sante.decreaseSanTE(victim.getUniqueId(), special);
            MeiqiheziVfx.slashHit(victim, victims.size());
        }

        if (!victims.isEmpty() && sanDrain != null) {
            sanDrain.onHit();
        }

        double heal = HEAL_PER_ENEMY * victims.size() + MeiqiheziMadness.healBonus(san) * victims.size();
        // 需求：充能层数**只在命中敌人时**才尝试消耗（空砍不扣层）
        int used = victims.isEmpty() ? 0 : charge.consume(1);
        if (used > 0) {
            for (Player victim : victims) {
                vitals.trueDamage(victim, caster, CHARGE_TRUE * used);
            }
            heal += CHARGE_HEAL_PER_ENEMY * victims.size();
            MeiqiheziVfx.chargeBurst(caster);
        }
        if (heal > 0d) {
            vitals.heal(caster, heal);
        }

        if (MeiqiheziMadness.controlEcho(san)) {
            applyControlEcho(caster);
        }

        MeiqiheziSound.channelRelease(caster);
        showSlashTitle(caster);
        MeiqiheziVfx.impact(center, used > 0);
        playSweep(center, radius, victims, used > 0, forward);
    }

    /** 引导文案：金色、逐字浮现（"圆" → "圆弧" → "圆弧~"），走 subtitle 不挡准星。 */
    private void showChannelText(Player player, String text) {
        player.showTitle(Title.title(
                Component.empty(),
                Component.text(text, NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(700), Duration.ofMillis(120))));
    }

    /** 出刀：屏幕正中一个金色的「斩！」。 */
    private void showSlashTitle(Player player) {
        player.showTitle(Title.title(
                Component.text("斩！", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(450), Duration.ofMillis(150))));
    }

    /** 冲击波：从**左手边**（90°）起、角度递减向右手推进，每刻 36°，十刻转满一圈。 */
    private void playSweep(Location center, double radius, List<Player> victims, boolean charged,
                           Vector forward) {
        final int[] step = {0};
        sweepTask = timers.addScheduleRepeating(this, 1L, 1L, () -> {
            step[0]++;
            double angle = 90d - step[0] * (360d / SWEEP_TICKS);
            MeiqiheziVfx.shockwaveWave(center, forward, radius, angle, step[0]);
            for (Player victim : victims) {
                if (victim != null && victim.isOnline() && !victim.isDead()) {
                    MeiqiheziVfx.rollingDiamond(victim, step[0]);
                }
            }
            if (step[0] >= SWEEP_TICKS) {
                if (sweepTask != null) {
                    sweepTask.cancel();
                    sweepTask = null;
                }
                if (charged) {
                    MeiqiheziVfx.chargedRing(center, radius);
                }
            }
        });
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

    private void cancelChannel(Player player) {
        if (channelVfxTask != null) {
            channelVfxTask.cancel();
            channelVfxTask = null;
        }
        if (detonateTask != null) {
            detonateTask.cancel();
            detonateTask = null;
        }
    }

    @Override
    public void stop() {
        cancelChannel(null);
        if (sweepTask != null) {
            sweepTask.cancel();
            sweepTask = null;
        }
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
