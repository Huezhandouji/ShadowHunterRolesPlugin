package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.*;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import com.shadowHunterRolesPlugin.roleComponent.red.*;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoleRegistry {

    //角色模板缓存
    private static final Map<String, Role> ROLES = new HashMap<>();

    public static boolean hasRole(String roleId){
        return ROLES.containsKey(roleId);
    }

    public static Role getRole(String roleId){
        return ROLES.getOrDefault(roleId, null);
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
                //Component.text("战斗疯子\n普攻20能量以上左键造成范围伤害并消耗能量，20以下只能打一个人\n一技能加速\n二技能三段突进并造成伤害\n三技能圆弧斩，范围真伤")
                .description(List.of(
                        Component.text("战斗疯子"),
                        Component.text("普攻20能量以上左键造成范围伤害并消耗能量，20以下只能打一个人"),
                        Component.text("一技能加速"),
                        Component.text("二技能三段突进并造成伤害"),
                        Component.text("三技能圆弧斩，范围真伤")
                ))
                .maxHP(40)
                .baseATK(10)
                .maxEnergy(100)
                .maxSanTE(100)
                .addSkill(MeiqiheziUnconcernSkill::new, 1)
                .addSkill(MeiqiheziBloodySlashSkill::new, 2)
                .addSkill(MeiqiheziCircleSlashSkill::new, 3)
                .addMainWeapon(MeiqiheziJuejueMainWeapon::new, 0)
                .faction(Faction.HUNTER)
                .addPassive(DefaultSanTEZeroPunishment::new)
                .addPassive(AutoRecoverSanTEHealthPassive::new)
                .addPassive(AutoRecoverEnergyPassive::new)
                .addPassive(MeiqiheziEquipmentsPassive::new)
                .icon(Material.DIAMOND_HOE);


        ROLES.put("meiqihezi", builder.build());
    }

    private static void registerShadowRed(){

        Role.Builder builder = new Role.Builder("red")
                .displayName(Component.text("Red"))
                //Component.text("待到血腥降临，一切化为土尘\n普攻造成15流血\n一技能捅人恢复生命\n二技能致盲敌人并结算5层流血恢复te\n三技能烧自己te开启狂暴")
                .description(List.of(
                        Component.text("待到血腥降临，一切化为土尘"),
                        Component.text("普攻造成15流血"),
                        Component.text("一技能捅人恢复生命")
                ))
                .maxHP(40)
                .baseATK(10)
                .maxEnergy(0)
                .maxSanTE(100)
                .faction(Faction.SHADOW)
                .addPassive(RedBleedPassive::new)
                .addMainWeapon(RedSanctifiedBladeMainWeapon::new, 0)
                .addSkill(RedSolitaryArroganceSkill::new, 1)
                .addSkill(RedEvilShockSkill::new, 2)
                .addSkill(RedDeeplySorrowSkill::new, 3)
                .addPassive(AutoRecoverSanTEHealthPassive::new)
                .addPassive(RedEquipmentsPassive::new)
                .addPassive(DefaultSanTEZeroPunishment::new)
                .icon(Material.POPPY);

        ROLES.put("red", builder.build());

    }

}
