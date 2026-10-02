package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class RemotenessArrowGivingSkill extends Skill {

    public static final String ID = "remotenessArrowGivingSkill";

    HotbarRenderComponent hr;

    /**
     * 首位两参 `(id, ComponentServicesPort)` 为构造期注入（id 由注册处声明、服务集由容器注入）。
     * <p>
     * 描述符口径的构造：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事。
     *
     * @param id
     * @param services
     * @param specification
     */
    public RemotenessArrowGivingSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends Skill.Specification<RemotenessArrowGivingSkill> {

        public Specification() {
            super(Component.text("箭矢"),
                    List.of(
                            Component.text("ccb")
                    ),
                    0,
                    0,
                    Material.ARROW);
            requires(HotbarRenderComponent.class);
        }

        @Override
        public RemotenessArrowGivingSkill create(String id, ComponentServicesPort services) {
            return new RemotenessArrowGivingSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        hr = svc().components().get(HotbarRenderComponent.class);
    }
    @Override
    public void stop() {
        hr = null;
    }

    @Override
    public void update(){
        if (hr == null) return;
        Player self = svc().self().player();
        if (self == null) return;
        if (self.getInventory().getItem(specification().slot()) != null) return;
        hr.requestRepaint();
    }

    @Override
    protected boolean canUse() {
        return true;
    }

    @Override
    protected int currentEnergy() {
        return 100;
    }
}
