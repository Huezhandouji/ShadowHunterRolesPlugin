package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.AutoRecoverEnergyPassive;
import com.shadowHunterRolesPlugin.roleComponent.AutoRecoverSanTEHealthPassive;
import com.shadowHunterRolesPlugin.roleComponent.DefaultSanTEZeroPunishment;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import com.shadowHunterRolesPlugin.roleComponent.red.RedBleedPassive;
import com.shadowHunterRolesPlugin.roleComponent.red.RedDeeplySorrowSkill;
import com.shadowHunterRolesPlugin.roleComponent.red.RedEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.red.RedEvilShockSkill;
import com.shadowHunterRolesPlugin.roleComponent.red.RedSanctifiedBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.red.RedSolitaryArroganceSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 显式角色装配器（阶段 3.2）。
 *
 * <p>取代原先 {@link RoleRegistry} 里的 {@code static { ... }} 初始化块：
 * <ul>
 *   <li>装配发生在 {@code onEnable} 里、由主类**显式调用**，时序可见、可测试；</li>
 *   <li>**fail-fast 且按角色隔离**：某个角色装配失败（例如 §10 裁决 1 的槽位冲突抛出的
 *       {@link IllegalArgumentException}）→ 记 {@code SEVERE}、**该角色不被注册**，
 *       其余角色继续装配；**不会**升级成 {@code ExceptionInInitializerError} 拖垮整个插件。</li>
 * </ul>
 *
 * <p>角色定义（数值、文案、槽位、图标）与阶段 0–2 完全一致，逐字未改。
 */
public class RoleLoader {

    /** 一条角色定义：id 仅用于日志定位，构建逻辑由 {@code builder} 提供（便于注入失败用例做校验）。 */
    public record Definition(String id, Supplier<Role.Builder> builder) { }

    private final Logger logger;

    public RoleLoader(Logger logger) {
        this.logger = logger;
    }

    /** 本插件的两个角色定义（原 {@code RoleRegistry} 静态块内容，逐字迁移）。 */
    public List<Definition> defaultDefinitions() {
        return List.of(
                new Definition("meiqihezi", RoleLoader::meiqiheziBuilder),
                new Definition("red", RoleLoader::redBuilder)
        );
    }

    public int loadInto(RoleRegistry registry) {
        return loadInto(registry, defaultDefinitions());
    }

    /**
     * 逐条装配并注册。
     *
     * @return 成功注册的角色数
     */
    public int loadInto(RoleRegistry registry, List<Definition> definitions) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        int registered = 0;
        for (Definition definition : definitions) {
            String id = definition.id();
            try {
                Role.Builder builder = definition.builder().get();
                Role role = builder.build();
                registry.register(role);
                registered++;
            } catch (Throwable failure) {
                // fail-fast：只跳过这一个角色，其余角色照常装配；绝不让异常冒泡成 ExceptionInInitializerError
                logger.log(Level.SEVERE,
                        "Role '" + id + "' failed assembly validation and was NOT registered; continuing with the remaining roles.",
                        failure);
            }
        }
        return registered;
    }

    private static Role.Builder meiqiheziBuilder() {

        return new Role.Builder("meiqihezi")
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
    }

    private static Role.Builder redBuilder() {

        return new Role.Builder("red")
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
    }

}
