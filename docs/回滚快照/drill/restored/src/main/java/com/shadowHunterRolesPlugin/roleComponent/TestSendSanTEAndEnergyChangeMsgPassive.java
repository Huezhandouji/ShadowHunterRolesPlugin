package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.EnergyChangeAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.SanTEChangeAware;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public class TestSendSanTEAndEnergyChangeMsgPassive extends PassiveSkill implements LifecycleAware, EnergyChangeAware, SanTEChangeAware {
    public TestSendSanTEAndEnergyChangeMsgPassive() {
        super(
                "test_send_san_te_and_energy_change_msg_passive",
                null,
                null
        );
    }

    @Override
    public void onSet(Player player, RoleInstance instance) {
        player.sendMessage(Component.text("onSet() triggered!"));
    }

    @Override
    public void onClear(Player player, RoleInstance instance) {
        player.sendMessage(Component.text("onClear() triggered!"));
    }

    @Override
    public void onEnergyChange(Player player, RoleInstance instance, int preEnergy, int newEnergy) {
        player.sendMessage(Component.text("Current energy: " + newEnergy));
    }

    @Override
    public void onSanTEChange(Player player, RoleInstance instance, int preSanTE, int newSanTE) {
        player.sendMessage(Component.text("Current SanTE: " + newSanTE));
    }
}
