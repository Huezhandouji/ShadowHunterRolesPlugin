package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.DefaultSanTEZeroPunishment;
import com.shadowHunterRolesPlugin.roleComponent.TestSendSanTEAndEnergyChangeMsgPassive;
import com.shadowHunterRolesPlugin.roleComponent.TestSetSanTEToZeroSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import net.kyori.adventure.text.Component;

import java.util.HashMap;
import java.util.Map;

public class RoleRegistry {

    //角色模板缓存
    private static final Map<String, Role> ROLES = new HashMap<>();

    //装配器缓存
    private static final Map<String, Role.Builder> BUILDERS = new HashMap<>();

    public static boolean hasRole(String roleId){
        return ROLES.containsKey(roleId);
    }

    public static Role getRole(String roleId){
        return ROLES.getOrDefault(roleId, null);
    }

    public static Role.Builder getBuilder(String roleId){
        return BUILDERS.getOrDefault(roleId, null);
    }

    //静态初始化块,注册所有角色
    static {
        registerHunterMeiqihezi();
        registerShadowRed();
    }

    //检查一个id是否存在
    public static boolean isValidRoleId(String id) {
        return ROLES.containsKey(id);
    }

    private static void registerHunterMeiqihezi(){

        Role.Builder builder = new Role.Builder("meiqihezi")
                .displayName(Component.text("MeiqiHezi"))
                .description(Component.text("A hunter role."))
                .maxHP(40)
                .baseATK(10)
                .maxEnergy(100)
                .maxSanTE(100)
                .addSkill(MeiqiheziUnconcernSkill::new, 1)
                .addSkill(MeiqiheziBloodySlashSkill::new, 2)
                .addSkill(MeiqiheziCircleSlashSkill::new, 3)
                .addMainWeapon(MeiqiheziJuejueMainWeapon::new, 0)
                .faction(Faction.HUNTER)
                .addPassive(TestSendSanTEAndEnergyChangeMsgPassive::new)
                .addPassive(DefaultSanTEZeroPunishment::new)
                .addSkill(TestSetSanTEToZeroSkill::new, 4);


        BUILDERS.put("meiqihezi", builder);
        ROLES.put("meiqihezi", builder.build());
    }

    private static void registerShadowRed(){

        Role.Builder builder = new Role.Builder("red")
                .displayName(Component.text("Red"))
                .description(Component.text("A shadow role."))
                .maxHP(40)
                .baseATK(10)
                .maxEnergy(0)
                .maxSanTE(100)
                .faction(Faction.SHADOW);

        BUILDERS.put("red", builder);
        ROLES.put("red", builder.build());

    }

}
