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

public class CangluTraumaMainWeapon extends MainWeapon {
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

    public static final class Specification extends MainWeapon.Specification {

        public Specification(){
            super(
                    Component.text("忧郁创痕-主武器"),
                    Component.text("攻击造成12特殊值伤害并夺取2能量，增加移速。如果'创伤'层数不小于4，将结算4层创伤"),
                    Material.IRON_SWORD,
                    100
            );
            requires(SanTEComponent.class);
            requires(EnergyComponent.class);
        }

        @Override
        public CangluTraumaMainWeapon create(String id, ComponentServices services){
            return new CangluTraumaMainWeapon(id, services, this);
        }
    }

    @Override
    protected boolean canUse() {
        return false;
    }
}
