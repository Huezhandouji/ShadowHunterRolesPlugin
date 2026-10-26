package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 一技能：**贯通新生**（白兰花）。
 *
 * <h2>需求</h2>
 * 施放期间**禁用其它技能与主武器**、缓慢 III、不可跳跃；连续四段戳击：
 * <pre>
 *   0.00s  7 格
 *   0.40s  7 格
 *   0.60s  7 格
 *   0.95s  8 格（额外缓慢 IV 0.5 秒）
 * </pre>
 * 每段对沿途所有敌人：5 层流血 + 0.05 秒定身（STUN 1 刻）+ 12 物理 + 4~8 特殊值；
 * 每段若命中，则给红一个短暂的 FOV 变化（速度 II 4 刻）。CD 10 秒（施放即进入）。
 */
public class RedDeeplySorrowSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "red_deeply_sorrow_skill";

    public static final double PHYSICAL = 12d;
    public static final int SANTE_MIN = 4;
    public static final int SANTE_MAX = 8;
    public static final int BLEED_STACKS = 5;
    public static final double PATH_RADIUS = 1.6d;
    public static final int ROOT_TICKS = 1;
    public static final int COOLDOWN = 200;

    /** 四段的触发刻（相对施放）。 */
    private static final int[] THRUST_TICKS = {1, 9, 13, 20};
    private static final double[] THRUST_LENGTHS = {7d, 7d, 7d, 8d};

    /** 施放期间禁跳的修饰符键。 */
    private static final NamespacedKey NO_JUMP_KEY = KeyFactory.Registry.of("red_thrust_no_jump");

    private VitalsComponent vitals;
    private BuffComponent buff;
    private FactionComponent faction;
    private SanTEComponent sante;
    private TaskComponent timers;
    private RedBleedPassive bleed;
    private RedSolitaryArroganceSkill ult;
    private RedFloatingTextComponent voice;

    private final List<ScheduledHandle> tasks = new ArrayList<>();
    private boolean casting;
    private final Runnable cooldownReducer = this::refundCooldown;

    public RedDeeplySorrowSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符：CD 200 刻 / 耗能 0 / 白兰花。 */
    public static final class Specification extends Skill.Specification<RedDeeplySorrowSkill> {

        public Specification() {
            super(
                    Component.text("贯通新生"),
                    List.of(
                            Component.text("四段穿刺（7/7/7/8 格）"),
                            Component.text("沿途：12 物理 + 4~8 特殊值 + 5 层流血"),
                            Component.text("每段附加 0.05 秒定身；末段额外缓慢 IV 0.5 秒"),
                            Component.text("期间禁用主武器与其它技能、缓慢 III、不可跳跃")
                    ),
                    COOLDOWN,
                    0,
                    Material.WHITE_TULIP
            );
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(SanTEComponent.class).requires(TaskComponent.class)
                    .requires(RedBleedPassive.class);
            requiresOptional(RedSolitaryArroganceSkill.class);
            requiresOptional(RedFloatingTextComponent.class);
        }

        @Override
        public RedDeeplySorrowSkill create(String id, ComponentServicesPort services) {
            return new RedDeeplySorrowSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        buff = getComponent(BuffComponent.class);
        faction = getComponent(FactionComponent.class);
        sante = getComponent(SanTEComponent.class);
        timers = getComponent(TaskComponent.class);
        bleed = getComponent(RedBleedPassive.class);
        ult = getComponent(RedSolitaryArroganceSkill.class);
        voice = getComponent(RedFloatingTextComponent.class);
        bleed.registerCooldownReducer(cooldownReducer);
    }

    @Override
    public void stop() {
        bleed.unregisterCooldownReducer(cooldownReducer);
        cancelTasks();
        clearNoJump(svc().self().player());
        casting = false;
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || casting || !buff.canCastSkill() || isCoolingDown()) {
            return;
        }
        casting = true;
        startCooldown();
        if (voice != null) {
            voice.sayMoment(caster, RedFloatingTextComponent.MOMENT_SKILL_THRUST);
        }

        buff.applyPotionEffect(PotionEffectType.SLOWNESS, 30, 2, true, false);
        applyNoJump(caster);

        for (int i = 0; i < THRUST_TICKS.length; i++) {
            final int index = i;
            tasks.add(timers.addScheduleLater(this, THRUST_TICKS[i], () -> thrust(index)));
        }
        tasks.add(timers.addScheduleLater(this, THRUST_TICKS[THRUST_TICKS.length - 1] + 2, () -> {
            Player player = svc().self().player();
            clearNoJump(player);
            casting = false;
        }));
    }

    /** 第 {@code index} 段戳击。 */
    private void thrust(int index) {
        Player caster = svc().self().player();
        if (caster == null || !caster.isOnline() || caster.isDead()) {
            cancelTasks();
            casting = false;
            return;
        }
        double length = THRUST_LENGTHS[index];
        boolean last = index == THRUST_LENGTHS.length - 1;

        Vector dir = caster.getEyeLocation().getDirection().normalize();
        Location eye = caster.getEyeLocation();
        List<Player> victims = new ArrayList<>();
        for (Player candidate : caster.getLocation().getNearbyPlayers(length + 1d)) {
            if (candidate == null || candidate.equals(caster) || !faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            Vector delta = candidate.getLocation().add(0d, 0.9d, 0d).toVector().subtract(eye.toVector());
            double along = delta.dot(dir);
            if (along < 0d || along > length) {
                continue;
            }
            double perp = delta.clone().subtract(dir.clone().multiply(along)).length();
            if (perp <= PATH_RADIUS) {
                victims.add(candidate);
            }
        }

        double bonus = ult != null && ult.isActive() ? RedSanctifiedBladeMainWeapon.ULT_DAMAGE_BONUS : 0d;
        for (Player victim : victims) {
            vitals.physicalDamage(victim, caster, PHYSICAL + bonus);
            int special = SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1);
            sante.decreaseSanTE(victim.getUniqueId(), (int) Math.round(special + bonus));
            bleed.applyStacks(victim.getUniqueId(), BLEED_STACKS);
            // 0.05 秒定身 = STUN 1 刻（插件侧控制，走 Buff 组件记账）
            buff.addTo(victim, BuffType.STUN, ROOT_TICKS);
            if (last) {
                buff.applyPotionEffectTo(victim, PotionEffectType.SLOWNESS, 10, 3, true, false);
            }
            RedFx.bloodSplat(victim, 16);
        }

        // FOV：命中瞬间给红一个极短的速度 II（客户端 FOV 会轻推一下）
        if (!victims.isEmpty()) {
            buff.applyPotionEffect(PotionEffectType.SPEED, 4, 1, true, false);
            bleed.onPlayerHit(caster);
            RedFx.pierceHit(caster);
        }
        RedFx.pierceCast(caster, length, true);
        RedFx.skillThrust(caster, last ? 0.8f : 1.05f);
        if (last) {
            buff.applyPotionEffect(PotionEffectType.SLOWNESS, 10, 3, true, false);
        }
    }

    /** 只在剩余 CD 内减，避免把已就绪的技能减成负数。 */
    private void refundCooldown() {
        int remaining = remainingCooldownTicks();
        if (remaining > 0) {
            startCooldown(Math.max(0, remaining - RedBleedPassive.FRUGAL_CD_REFUND));
        }
    }

    /** 是否正在施放（主武器 / 其它技能据此关闸）。 */
    public boolean isCasting() {
        return casting;
    }

    private void cancelTasks() {
        for (ScheduledHandle handle : tasks) {
            if (handle != null) {
                handle.cancel();
            }
        }
        tasks.clear();
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
        return buff != null && buff.canCastSkill() && !casting;
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
