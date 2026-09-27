package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nonnegative;

public class CangluHysteriaPassive extends PassiveSkill {

    public static final String ID = "cangluHysteriaPassive";

    private int stackCount;

    private static final int MAX_STACK_COUNT = 24;
    private static final int RESOLVE_COUNT = 4;

    private static final int SAN_RECOVER_AMOUNT = 4;
    private static final int ABSORPTION_TIME = 160;
    private static final int ABSORPTION_LEVEL = 1;

    private SanTEComponent sante;
    private BuffComponent buff;

    BossBar bossbar;




    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（**声明数据的唯一来源**）
     */
    public CangluHysteriaPassive(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification<CangluHysteriaPassive> {

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
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);



        bossbar = BossBar.bossBar(
                Component.empty(),
                0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );
        svc().self().player().showBossBar(bossbar);
    }

    @Override
    public void update(){
        float progress;
        if(stackCount <= 0){
            progress = 0f;
        }
        else{
            progress = (float) stackCount / MAX_STACK_COUNT;
        }
        bossbar.progress(progress);
        bossbar.name(Component.text("创伤 " + stackCount + "/" + MAX_STACK_COUNT, NamedTextColor.BLUE, TextDecoration.BOLD));
    }

    @Override
    public void stop()  {
        super.stop();
        sante = null;
        buff = null;
        svc().self().player().hideBossBar(bossbar);
        bossbar = null;
    }

    public void requestAddStackCount(@Nonnegative int amount){
        stackCount = Math.clamp(stackCount + amount, 0, MAX_STACK_COUNT);
    }

    public boolean requestResolve(){
        if(stackCount < RESOLVE_COUNT) return false;
        stackCount -= RESOLVE_COUNT;
        buff.applyPotionEffect(PotionEffectType.ABSORPTION.createEffect(ABSORPTION_TIME, ABSORPTION_LEVEL));
        sante.increase(12);
        return true;
    }
}
