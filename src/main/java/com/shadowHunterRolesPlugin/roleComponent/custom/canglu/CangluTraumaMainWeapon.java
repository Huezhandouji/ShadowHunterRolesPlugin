package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class CangluTraumaMainWeapon extends MainWeapon {

    public static final String ID = "cangluTraumaMainWeapon";


    private static final int EXTRA_TURE_DAMAGE_AMOUNT = 4;
    private static final int SANTE_DAMAGE_AMOUNT = 12;
    private static final int ENERGY_THEFT_AMOUNT = 2;
    private static final int SPEED_EFFECT_LEVEL = 5;
    private static final int SPEED_EFFECT_TIME = 80;

    SanTEComponent sante;
    BuffComponent buff;
    EnergyComponent energy;
    VitalsComponent vitals;
    CangluHysteriaPassive hysteriaPassive;


    /**
     * **描述符口径的构造**：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事（主武器的能量消耗由类型恒为 0）。
     *
     * @param id
     * @param services
     * @param specification
     */
    public CangluTraumaMainWeapon(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends MainWeapon.Specification<CangluTraumaMainWeapon> {

        public Specification(){
            super(
                    Component.text("忧郁创痕-主武器"),
                    Component.text("攻击造成12特殊值伤害并夺取2能量，增加移速。如果'创伤'层数不小于4，将结算4层创伤"),
                    Material.DIAMOND_SWORD,
                    40
            );
            requires(SanTEComponent.class);
            requires(EnergyComponent.class);
            requires(BuffComponent.class);
            requires(VitalsComponent.class);
            requires(CangluHysteriaPassive.class);
        }

        @Override
        public CangluTraumaMainWeapon create(String id, ComponentServices services){
            return new CangluTraumaMainWeapon(id, services, this);
        }
    }

    @Override
    public void onAwake(){

    }

    @Override
    public void start(){
        sante = svc().components().get(SanTEComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        hysteriaPassive = svc().components().get(CangluHysteriaPassive.class);
    }

    @Override
    public void stop(){

    }

    @Override
    public void onAttack(AttackSignal signal){

        if(!canUse()) return;

        UUID victimPid = signal.victim().getUniqueId();
        sante.decreaseSanTE(victimPid, SANTE_DAMAGE_AMOUNT);

        int actualEnergyStealAmount = energy.decreaseEnergy(victimPid, ENERGY_THEFT_AMOUNT);
        energy.increase(actualEnergyStealAmount);

        buff.applyPotionEffect(PotionEffectType.SPEED.createEffect(SPEED_EFFECT_TIME, SPEED_EFFECT_LEVEL));

        hysteriaPassive.requestAddStackCount(1);
        boolean passiveCasted = hysteriaPassive.requestResolve();

        if(passiveCasted){
            vitals.trueDamage(signal.victim(), svc().self().player(), EXTRA_TURE_DAMAGE_AMOUNT);
            svc().self().player().getWorld().playSound(svc().self().player().getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.5f);
        }
        else {
            svc().self().player().getWorld().playSound(svc().self().player().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1f);
        }



        startCooldown();
    }

    @Override
    protected boolean canUse() {
        return buff.canUseMainWeapon();
    }
}
