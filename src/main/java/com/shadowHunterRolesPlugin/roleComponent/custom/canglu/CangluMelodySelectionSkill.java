package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.yaml.snakeyaml.error.Mark;



public class CangluMelodySelectionSkill extends Skill {

    private static final int GRAPNEL_SPEED = 2;
    private static final int GRAPNEL_MAX_LIVING_TIME = 40;

    //依赖组件
    private BuffComponent buff;
    private VitalsComponent vitals;
    private TaskComponent task;
    private EnergyComponent energy;

    private Grapnel grapnel;

    /**
     * 首位两参 `(id, ComponentServices)` 为**构造期注入**（id 由注册处声明、服务集由容器注入）。
     * <p>
     * **描述符口径的构造**：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事。
     *
     * @param id
     * @param services
     * @param specification
     */
    public CangluMelodySelectionSkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends Skill.Specification<CangluMelodySelectionSkill> {

        Specification(Component displayName, Component description, int cooldownTicks, int energyCost, Material icon) {
            super(
                    Component.text("旋律选取"),
                    Component.text("发射抓钩，命中敌人或者碰到方块后，将苍鹭拉过去，期间获得抗性4，对碰到的敌人造成12物理伤害。"),
                    80, 0, Material.LAPIS_LAZULI
            );
        }

        @Override
        public CangluMelodySelectionSkill create(String id, ComponentServices services) {
            return new CangluMelodySelectionSkill(id, services, this);
        }
    }

    @Override
    public void onAwake()
    {
    }

    @Override
    public void start(){
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        task = svc().components().get(TaskComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        grapnel = null;
    }

    @Override
    public void onCast(CastSignal signal) {
        if(signal.trigger() != CastTrigger.RIGHT_CLICK) return;
        if(!buff.canCastSkill()) return;
        if(!energy.tryConsume(getEnergyCost())) return;

        if(grapnel != null) return;

        Player self = svc().self().player();
        Location muzzle = self.getLocation();

        Marker marker = (Marker) self.getWorld().spawnEntity(muzzle, EntityType.MARKER);

        grapnel = new Grapnel(marker, muzzle.getDirection().normalize(), 0);
    }

    @Override
    public void stop(){

    }

    @Override
    protected boolean canUse() {
        return buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    private void advanceGrapnel() {
        if(grapnel == null) return;
    }

    private static final record Grapnel(
            Marker marker,
            Vector direction,
            int livingTime
    ){}


}
