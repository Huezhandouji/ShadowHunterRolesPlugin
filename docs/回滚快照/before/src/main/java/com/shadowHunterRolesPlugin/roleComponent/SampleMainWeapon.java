package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.MainWeapon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public class SampleMainWeapon extends MainWeapon {

    public SampleMainWeapon() {
        super(
                "sample_main_weapon",
                Component.text("Sample MainWeapon"),
                Component.text("A ShadowHunter MainWeapon"),
                Material.DIAMOND_SWORD,
                20
        );
    }
}
