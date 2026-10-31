package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoChargeComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoMadness;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 一技能：**漫不经心**（模型：烈焰粉）。
 *
 * <h2>需求</h2>
 * <ul>
 *   <li>使用后立即**尝试充能 1 层**（SanTE ≤ 20 时无条件 2 层）；</li>
 *   <li>**速度 VIII 持续 0.5 秒**；</li>
 *   <li>CD **1 秒**，**恢复 1 点能量**（注意是"回复"不是"消耗"）；</li>
 *   <li>SanTE ≤ 10 档：所有技能附带"周围敌人缓慢 I 1 秒 + 自身 4 点伤害吸收"。</li>
 * </ul>
 *
 * <p>本技能是整套循环的"点火器"：1 秒 CD + 回能，用来把充能层数顶到 3 层，
 * 因此它**不消耗能量**、也不消耗充能。
 */
public class MikomakoUnconcernSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_skill_unconcern";

    /** 速度时长（刻）—— 需求 0.5 秒。 */
    public static final int SPEED_TICKS = 10;

    /** 速度等级 VIII ⇒ amplifier 7。 */
    public static final int SPEED_AMPLIFIER = 7;

    /** 施放回复的能量（需求：1）。 */
    public static final int ENERGY_RESTORE = 1;

    /** 10 档：控制余波半径。 */
    public static final double ECHO_RADIUS = 6d;

    /** 10 档：敌人缓慢时长。 */
    public static final int ECHO_SLOW_TICKS = 20;

    /** 10 档：自身伤害吸收（4 点 = amplifier 0）。 */
    public static final int ABSORB_TICKS = 100;

    private BuffComponent buff;    private EnergyComponent energy;
    private SanTEComponent sante;
    private FactionComponent faction;
    private VitalsComponent vitals;
    private MikomakoChargeComponent charge;
    private MikomakoFloatingTextComponent voice;

    public MikomakoUnconcernSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符：CD 20 刻 / 耗能 0。 */
    public static final class Specification extends Skill.Specification<MikomakoUnconcernSkill> {

        public Specification() {
            super(
                    Component.text("漫不经心"),
                    List.of(
                            Component.text("立即充能 1 层（低 SanTE 时 2 层）"),
                            Component.text("速度 VIII 持续 0.5 秒"),
                            Component.text("恢复 1 点能量"),
                            Component.text("低 SanTE 时附带控制余波")
                    ),
                    20,
                    0,
                    Material.BLAZE_POWDER
            );
            requires(BuffComponent.class).requires(EnergyComponent.class).requires(SanTEComponent.class)
                    .requires(FactionComponent.class).requires(VitalsComponent.class)
                    .requires(MikomakoChargeComponent.class);
            requiresOptional(MikomakoFloatingTextComponent.class);
        }

        @Override
        public MikomakoUnconcernSkill create(String id, ComponentServicesPort services) {
            return new MikomakoUnconcernSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = getComponent(BuffComponent.class);
        energy = getComponent(EnergyComponent.class);
        sante = getComponent(SanTEComponent.class);
        faction = getComponent(FactionComponent.class);
        vitals = getComponent(VitalsComponent.class);
        charge = getComponent(MikomakoChargeComponent.class);
        voice = getComponent(MikomakoFloatingTextComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || !buff.canCastSkill()) {
            return;
        }

        int san = sante.current();
        int stacks = MikomakoMadness.doubleCharge(san) ? 2 : 1;
        charge.addStacks(stacks, caster);
        energy.increase(ENERGY_RESTORE);
        buff.applyPotionEffect(PotionEffectType.SPEED, SPEED_TICKS, SPEED_AMPLIFIER, true, false);

        MikomakoVfx.chargeBurst(caster);
        MikomakoSound.skillCast(caster);

        if (MikomakoMadness.controlEcho(san)) {
            applyControlEcho(caster);
        }

        if (voice != null) {
            voice.sayMoment(caster, MikomakoFloatingTextComponent.MOMENT_SKILL_UNCONCERN);
        }
        startCooldown();
    }

    /** 10 档：周围敌人缓慢 I 1 秒 + 自身 4 点伤害吸收。 */
    private void applyControlEcho(Player caster) {
        for (Player target : caster.getLocation().getNearbyPlayers(ECHO_RADIUS)) {
            if (target == null || target.equals(caster) || !faction.isHostileTo(target.getUniqueId())) {
                continue;
            }
            buff.applyPotionEffectTo(target, PotionEffectType.SLOWNESS, ECHO_SLOW_TICKS, 0, true, false);
        }
        buff.applyPotionEffect(PotionEffectType.ABSORPTION, ABSORB_TICKS, 0, true, false);
        MikomakoVfx.controlEcho(caster.getLocation());
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
