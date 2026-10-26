package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 二技能：**烟血震慑**（红石粉）。
 *
 * <ul>
 *   <li>引导 0.8 秒（16 刻）：缓慢 II、不可跳跃；</li>
 *   <li>随后 r=10：10~13 点特殊值伤害 + 黑暗 3 秒 + 缓慢 I 3 秒；</li>
 *   <li>同时快速结算范围内每个敌人 **6 层流血**（不足则按现有层数结算）；</li>
 *   <li>CD 10 秒。</li>
 * </ul>
 *
 * <p>表现：引导期用红/白/少量黑勾勒 r=10 范围，内部翻涌药水粒子；释放时女巫笑 + 药水破裂 + 幻翼叫声。
 */
public class RedEvilShockSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "red_evil_shock_skill";

    public static final int CHANNEL_TICKS = 16;
    public static final double RADIUS = 10d;
    public static final int SANTE_MIN = 10;
    public static final int SANTE_MAX = 13;
    public static final int DARKNESS_TICKS = 60;
    public static final int SLOW_TICKS = 60;
    public static final int SETTLE_STACKS = 6;
    public static final int COOLDOWN = 200;

    private static final NamespacedKey NO_JUMP_KEY = KeyFactory.Registry.of("red_shock_no_jump");

    private BuffComponent buff;
    private FactionComponent faction;
    private SanTEComponent sante;
    private TaskComponent timers;
    private RedBleedPassive bleed;
    private RedDeeplySorrowSkill thrust;
    private RedFloatingTextComponent voice;

    private ScheduledHandle channelTask;
    private ScheduledHandle releaseTask;
    private boolean channeling;
    private final Runnable cooldownReducer = this::refundCooldown;

    public RedEvilShockSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends Skill.Specification<RedEvilShockSkill> {

        public Specification() {
            super(
                    Component.text("烟血震慑"),
                    List.of(
                            Component.text("引导 0.8 秒（缓慢 II、不可跳跃）"),
                            Component.text("r=10：10~13 特殊值 + 黑暗 3 秒 + 缓慢 I 3 秒"),
                            Component.text("结算范围内每个敌人 6 层流血")
                    ),
                    COOLDOWN,
                    0,
                    Material.REDSTONE
            );
            requires(BuffComponent.class).requires(FactionComponent.class).requires(SanTEComponent.class)
                    .requires(TaskComponent.class).requires(RedBleedPassive.class)
                    .requires(RedDeeplySorrowSkill.class);
            requiresOptional(RedFloatingTextComponent.class);
        }

        @Override
        public RedEvilShockSkill create(String id, ComponentServicesPort services) {
            return new RedEvilShockSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = getComponent(BuffComponent.class);
        faction = getComponent(FactionComponent.class);
        sante = getComponent(SanTEComponent.class);
        timers = getComponent(TaskComponent.class);
        bleed = getComponent(RedBleedPassive.class);
        thrust = getComponent(RedDeeplySorrowSkill.class);
        voice = getComponent(RedFloatingTextComponent.class);
        bleed.registerCooldownReducer(cooldownReducer);
    }

    @Override
    public void stop() {
        bleed.unregisterCooldownReducer(cooldownReducer);
        cancelChannel();
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || channeling || !buff.canCastSkill() || isCoolingDown()
                || (thrust != null && thrust.isCasting())) {
            return;
        }
        channeling = true;
        startCooldown();

        buff.applyPotionEffect(PotionEffectType.SLOWNESS, CHANNEL_TICKS + 6, 1, true, false);
        applyNoJump(caster);
        RedFx.shockChannel(caster);

        final int[] phase = {0};
        channelTask = timers.addScheduleRepeating(this, 1L, 2L, () -> {
            Player player = svc().self().player();
            if (player == null || !player.isOnline() || player.isDead()) {
                cancelChannel();
                return;
            }
            phase[0]++;
            RedFx.rangeRing(player.getLocation(), RADIUS, phase[0], true);
            RedFx.potionField(player.getLocation(), RADIUS, phase[0]);
        });

        releaseTask = timers.addScheduleLater(this, CHANNEL_TICKS, this::release);
    }

    private void release() {
        Player caster = svc().self().player();
        cancelChannel();
        if (caster == null || !caster.isOnline() || caster.isDead()) {
            return;
        }
        Location center = caster.getLocation();
        List<Player> victims = new ArrayList<>();
        for (Player victim : center.getNearbyPlayers(RADIUS)) {
            if (victim == null || victim.equals(caster) || !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            int amount = SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1);
            sante.decreaseSanTE(victim.getUniqueId(), amount);
            buff.applyPotionEffectTo(victim, PotionEffectType.DARKNESS, DARKNESS_TICKS, 0, true, false);
            buff.applyPotionEffectTo(victim, PotionEffectType.SLOWNESS, SLOW_TICKS, 0, true, false);
            bleed.requestResolve(victim.getUniqueId(), SETTLE_STACKS);
            victims.add(victim);
        }

        RedFx.darkBurst(center, RADIUS);
        RedFx.shockRelease(caster);
        if (voice != null) {
            voice.sayMoment(caster, RedFloatingTextComponent.MOMENT_SKILL_SHOCK);
        }
        if (!victims.isEmpty()) {
            bleed.onPlayerHit(caster);
        }
    }

    private void refundCooldown() {
        int remaining = remainingCooldownTicks();
        if (remaining > 0) {
            startCooldown(Math.max(0, remaining - RedBleedPassive.FRUGAL_CD_REFUND));
        }
    }

    private void cancelChannel() {
        channeling = false;
        if (channelTask != null) {
            channelTask.cancel();
            channelTask = null;
        }
        if (releaseTask != null) {
            releaseTask.cancel();
            releaseTask = null;
        }
        clearNoJump(svc().self().player());
    }

    private void applyNoJump(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(NO_JUMP_KEY);
        attribute.addModifier(new AttributeModifier(NO_JUMP_KEY, -0.42d, AttributeModifier.Operation.ADD_NUMBER));
    }

    private void clearNoJump(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (attribute != null) {
            attribute.removeModifier(NO_JUMP_KEY);
        }
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill() && !channeling
                && (thrust == null || !thrust.isCasting());
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
