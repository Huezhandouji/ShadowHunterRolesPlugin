package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 三技能（大招）：**血亲呼唤**（红石块）。
 *
 * <h2>引导 1 秒</h2>
 * 缓慢 IV + 抗性 V；四根红石柱从地面旋转升起、汇入自身后向外爆开。
 *
 * <h2>引导结束</h2>
 * <ul>
 *   <li>r=12 内每个敌人结算 <b>5 层流血</b> + 黑暗 1 秒；</li>
 *   <li>红：最大生命 <b>+10</b>、回复 <b>10</b>、速度 IV、抗性 II；</li>
 *   <li>主武器与一技能的全部伤害 <b>+3</b>（物理与特殊值都加）；</li>
 *   <li>期间受到的控制（失明 / 黑暗 / 缓慢 ≥4 / 定身 / 沉默）统一改为**缓慢 III（同时长）**；</li>
 *   <li>免疫中毒 / 凋零；</li>
 *   <li>血量进入最后 20% ⇒ 获得 3 秒抗性 V，抗性结束即结束大招；</li>
 *   <li>每秒扣 2 SanTE，每 8 秒 +2（上限 6）；12 格内没有敌人时不扣；SanTE 归零 ⇒ 结束并接受归零惩罚。</li>
 * </ul>
 * CD 20 秒，**大招结束后**才进入冷却。
 */
public class RedSolitaryArroganceSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "red_solitary_arrogance_skill";

    public static final int CHANNEL_TICKS = 20;
    public static final double RADIUS = 12d;
    public static final int SETTLE_STACKS = 5;
    public static final int DARKNESS_TICKS = 20;
    public static final double HEALTH_BONUS = 10d;
    public static final double HEAL_ON_START = 10d;
    public static final double DAMAGE_BONUS = 3d;
    public static final int COOLDOWN_AFTER_END = 400;
    public static final int FINISH_RESIST_TICKS = 60;

    /** 「没有敌人就不扣 SanTE」的判定半径（2026-10-21 需求：由 12 扩到 20）。 */
    public static final double NO_DRAIN_RADIUS = 20d;

    private static final NamespacedKey ULT_HEALTH_KEY = KeyFactory.Registry.of("red_ult_health");

    private VitalsComponent vitals;
    private BuffComponent buff;
    private FactionComponent faction;
    private SanTEComponent sante;
    private RedBleedPassive bleed;
    private RedDeeplySorrowSkill thrust;
    private RedFloatingTextComponent voice;

    private boolean channeling;
    private boolean active;
    private int channelTicks;
    private int ultTicks;
    private int secondTimer;
    private boolean finishing;
    private int finishTicks;
    private int phase;

    public RedSolitaryArroganceSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends Skill.Specification<RedSolitaryArroganceSkill> {

        public Specification() {
            super(
                    Component.text("血亲呼唤"),
                    List.of(
                            Component.text("引导 1 秒（缓慢 IV + 抗性 V）"),
                            Component.text("r=12：结算 5 层流血 + 黑暗 1 秒"),
                            Component.text("最大生命 +10、回 10、速度 IV、抗性 II、伤害 +3"),
                            Component.text("控制转为缓慢 III；免疫中毒 / 凋零"),
                            Component.text("每秒扣 SanTE，归零或残血抗性结束即收招")
                    ),
                    COOLDOWN_AFTER_END,
                    0,
                    Material.REDSTONE_BLOCK
            );
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(SanTEComponent.class).requires(RedBleedPassive.class)
                    .requires(RedDeeplySorrowSkill.class);
            requiresOptional(RedFloatingTextComponent.class);
        }

        @Override
        public RedSolitaryArroganceSkill create(String id, ComponentServicesPort services) {
            return new RedSolitaryArroganceSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        buff = getComponent(BuffComponent.class);
        faction = getComponent(FactionComponent.class);
        sante = getComponent(SanTEComponent.class);
        bleed = getComponent(RedBleedPassive.class);
        thrust = getComponent(RedDeeplySorrowSkill.class);
        voice = getComponent(RedFloatingTextComponent.class);
        bleed.registerCooldownReducer(this::refundCooldown);
    }

    @Override
    public void stop() {
        if (active) {
            endUltimate(false);
        }
        channeling = false;
        active = false;
        finishing = false;
        Player player = svc().self().player();
        if (player != null) {
            vitals.removeHealthModifier(ULT_HEALTH_KEY);
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || channeling || active || !buff.canCastSkill() || isCoolingDown()
                || (thrust != null && thrust.isCasting())) {
            return;
        }
        channeling = true;
        channelTicks = 0;
        phase = 0;
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, CHANNEL_TICKS + 6, 3, true, false);
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, CHANNEL_TICKS + 6, 4, true, false);
        RedFx.ultChannel(caster);
    }

    @Override
    public void update() {
        Player player = svc().self().player();
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (channeling) {
            channelTicks++;
            RedFx.ultColumns(player.getLocation(), channelTicks);
            if (channelTicks >= CHANNEL_TICKS) {
                release(player);
            }
            return;
        }
        if (active) {
            tickActive(player);
        }
    }

    private void release(Player caster) {
        channeling = false;
        active = true;
        ultTicks = 0;
        secondTimer = 0;
        finishing = false;
        finishTicks = 0;

        Location center = caster.getLocation();
        List<Player> victims = new ArrayList<>();
        for (Player victim : center.getNearbyPlayers(RADIUS)) {
            if (victim == null || victim.equals(caster) || !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            bleed.requestResolve(victim.getUniqueId(), SETTLE_STACKS);
            buff.applyPotionEffectTo(victim, PotionEffectType.DARKNESS, DARKNESS_TICKS, 0, true, false);
            victims.add(victim);
        }

        vitals.applyHealthModifier(ULT_HEALTH_KEY, VitalsComponent.BASE_MAX_HEALTH + HEALTH_BONUS);
        vitals.heal(caster, HEAL_ON_START);
        buff.applyPotionEffect(PotionEffectType.SPEED, 60, 3, true, false);
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, 60, 1, true, false);

        RedFx.ultConverge(center, RADIUS);
        RedFx.ultRelease(caster);
        if (voice != null) {
            voice.sayMoment(caster, RedFloatingTextComponent.MOMENT_ULT_START);
        }
        if (!victims.isEmpty()) {
            bleed.onPlayerHit(caster);
        }
    }

    private void tickActive(Player player) {
        ultTicks++;
        phase++;

        // 控制统一转缓慢 III（同时长）
        convertControl(player, PotionEffectType.BLINDNESS);
        convertControl(player, PotionEffectType.DARKNESS);
        convertSlow(player);
        convertBuff(player, BuffType.STUN);
        convertBuff(player, BuffType.SILENCE);

        // 免疫中毒 / 凋零
        player.removePotionEffect(PotionEffectType.POISON);
        player.removePotionEffect(PotionEffectType.WITHER);

        if (phase % 20 == 0) {
            buff.applyPotionEffect(PotionEffectType.SPEED, 40, 3, true, false);
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, 40, 1, true, false);
        }
        if (phase % 5 == 0) {
            RedFx.ultAura(player, phase);
        }

        // 残血保护：进入最后 20% ⇒ 抗性 V 3 秒，结束后收招
        double max = player.getMaxHealth();
        if (!finishing && player.getHealth() <= max * 0.2d) {
            finishing = true;
            finishTicks = FINISH_RESIST_TICKS;
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, FINISH_RESIST_TICKS, 4, true, false);
        }
        if (finishing && --finishTicks <= 0) {
            endUltimate(true);
            return;
        }

        // SanTE 消耗：12 格内有敌人才扣
        if (++secondTimer >= 20) {
            secondTimer = 0;
            if (hasEnemyNear(player, NO_DRAIN_RADIUS)) {
                int step = Math.min(6, 2 + 2 * (ultTicks / 160));
                sante.decrease(step);
                if (sante.current() <= 0) {
                    // 归零：结束并交给内建归零惩罚
                    endUltimate(true);
                }
            }
        }
    }

    /** 失明 / 黑暗 ⇒ 同时长的缓慢 III。 */
    private void convertControl(Player player, PotionEffectType type) {
        PotionEffect effect = player.getPotionEffect(type);
        if (effect == null) {
            return;
        }
        player.removePotionEffect(type);
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, effect.getDuration(), 2, true, false);
    }

    /** 缓慢 ≥ IV ⇒ 缓慢 III（同时长）。 */
    private void convertSlow(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.SLOWNESS);
        if (effect == null || effect.getAmplifier() < 3) {
            return;
        }
        player.removePotionEffect(PotionEffectType.SLOWNESS);
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, effect.getDuration(), 2, true, false);
    }

    /** 插件侧 STUN / SILENCE ⇒ 同时长的缓慢 III。 */
    private void convertBuff(Player player, BuffType type) {
        if (!buff.has(type)) {
            return;
        }
        long remaining = buff.remainingTicks(type);
        buff.remove(type);
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, (int) Math.max(1, remaining), 2, true, false);
    }

    private boolean hasEnemyNear(Player player, double radius) {
        for (Player candidate : player.getLocation().getNearbyPlayers(radius)) {
            if (candidate != null && !candidate.equals(player) && faction.isHostileTo(candidate.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    /** 收招：摘掉上限加成与控制增益，进入 20 秒冷却。 */
    private void endUltimate(boolean startCooldown) {
        if (!active && !channeling) {
            return;
        }
        active = false;
        channeling = false;
        finishing = false;
        Player player = svc().self().player();
        if (player != null) {
            vitals.removeHealthModifier(ULT_HEALTH_KEY);
            player.removePotionEffect(PotionEffectType.SPEED);
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }
        if (startCooldown) {
            startCooldown(COOLDOWN_AFTER_END);
        }
        if (player != null && voice != null) {
            voice.sayMoment(player, RedFloatingTextComponent.MOMENT_ULT_END);
        }
    }

    /** 主武器 / 一技能的额外伤害（大招期间 +3）。 */
    public double damageBonus() {
        return active ? DAMAGE_BONUS : 0d;
    }

    public boolean isActive() {
        return active;
    }

    private void refundCooldown() {
        int remaining = remainingCooldownTicks();
        if (remaining > 0) {
            startCooldown(Math.max(0, remaining - RedBleedPassive.FRUGAL_CD_REFUND));
        }
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill() && !channeling && !active
                && (thrust == null || !thrust.isCasting());
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
