package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nonnegative;

public class CangluHysteriaPassive extends PassiveSkill {

    public static final String ID = "cangluHysteriaPassive";

    private int stackCount;

    private static final int MAX_STACK_COUNT = 24;
    private static final int RESOLVE_COUNT = 4;

    private static final int SAN_RECOVER_AMOUNT = 4;
    private static final int TURE_DAMAGE_AMOUNT = 4;
    private static final int ABSORPTION_TIME = 160;
    private static final int ABSORPTION_LEVEL = 2;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;


    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（**声明数据的唯一来源**）
     */
    public CangluHysteriaPassive(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification {

        public Specification(){
            super(Component.text("创伤"), Component.text("苍鹭的创伤被动，给自己叠层数"));
        }

        @Override
        public CangluHysteriaPassive create(String id, ComponentServices services){
            return new CangluHysteriaPassive(id, services, this);
        }
    }

    @Override
    public void awake(){
        super.awake();
        stackCount = 0;
    }

    @Override
    public void start(){
        super.start();
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    @Override
    public void stop(){
        super.stop();
        vitals = null;
        sante = null;
        buff = null;
    }

    public void requestAddStackCount(@Nonnegative int amount){
        stackCount = Math.clamp(stackCount + amount, 0, MAX_STACK_COUNT);
    }

    public boolean requestResolve(Player victim){
        if(stackCount < RESOLVE_COUNT) return false;
        stackCount -= RESOLVE_COUNT;
        vitals.trueDamage(victim, svc().self().player(), 4);
        buff.applyPotionEffect(PotionEffectType.ABSORPTION.createEffect(ABSORPTION_TIME, ABSORPTION_LEVEL));
        sante.increase(12);
        return true;
    }
}
