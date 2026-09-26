package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Boss;

import javax.inject.Named;


public class BossbarRoleAttributesDisplayPassive extends PassiveSkill {

    public final static String ID = "bossbarRoleAttributesDisplayPassive";

    private EnergyComponent energy;
    private SanTEComponent sante;

    private BossBar bossbar;

    public BossbarRoleAttributesDisplayPassive(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification {


        public Specification() {
            super(Component.text("bossbar角色属性数值显示"),
                    Component.text("ccb"));
        }

        @Override
        public PassiveSkill create(String id, ComponentServices services) {
            return new BossbarRoleAttributesDisplayPassive(id, services, this);
        }
    }


    @Override
    public void start(){
        energy = svc().components().get(EnergyComponent.class);
        sante = svc().components().get(SanTEComponent.class);

        if(energy == null && sante == null){
            svc().components().remove(getClass());
        }

        if(svc().roleInfo().faction() == Faction.SHADOW){
            energy = null;
        }

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
        Component newName = Component.empty();
        if(sante != null){
            switch (svc().roleInfo().faction()){
                case Faction.SHADOW -> {
                    newName = newName.append(Component.text("TE " + sante.current() + "/" + sante.max()).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
                }
                case Faction.HUNTER -> {
                    newName = newName.append(Component.text("SAN " + sante.current() + "/" + sante.max()).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
                }
            }
        }
        if(energy != null){
            newName = newName.append(Component.text(" || ").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            newName = newName.append(Component.text("ENERGY " + energy.current() + "/" + energy.max()).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }

        bossbar.name(newName);
    }


    @Override
    public void stop(){
        svc().self().player().hideBossBar(bossbar);
        bossbar = null;
        energy = null;
        sante = null;
    }
}
