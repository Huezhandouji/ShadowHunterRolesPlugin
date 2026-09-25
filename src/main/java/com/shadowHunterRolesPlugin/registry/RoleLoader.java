package com.shadowHunterRolesPlugin.registry;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.builtin.AutoRecoverEnergyPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.AutoRecoverSanTEHealthPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.DefaultSanTEZeroPunishment;
import com.shadowHunterRolesPlugin.roleComponent.builtin.ExampleSelfRefreshingSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedBleedPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedDeeplySorrowSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvilShockSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSolitaryArroganceSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 显式角色装配器。
 *
 * <p>取代原先 {@link RoleRegistry} 里的 {@code static { ... }} 初始化块：
 * <ul>
 *   <li>装配发生在 {@code onEnable} 里、由主类**显式调用**，时序可见、可测试；</li>
 *   <li>**fail-fast 且按角色隔离**：某个角色装配失败（例如 §10 裁决 1 的槽位冲突抛出的
 *       {@link IllegalArgumentException}）→ 记 {@code SEVERE}、**该角色不被注册**，
 *       其余角色继续装配；**不会**升级成 {@code ExceptionInInitializerError} 拖垮整个插件。</li>
 * </ul>
 *
 * <p>角色定义（数值、文案、槽位、图标）逐字未改。
 */
public class RoleLoader {

    /** 一条角色定义：id 仅用于日志定位，构建逻辑由 {@code builder} 提供（便于注入失败用例做校验）。 */
    public record Definition(String id, Supplier<Role.Builder> builder) { }

    /**
     * 组件 id 常量（收尾批⑤）：**每个 id 字面量在整个仓里只声明一次** —— 组件构造器不再硬编码 id，
     * 装配条目统一写作 {@code (id, 组件::new, slot)}；id 由本处声明，容器在构造期把它与服务集一起交给组件。
     * （字符串多重集净变化 = 0：旧构造器里的 14 处字面量原值移到这里。）
     */
    private static final String ID_AUTO_RECOVER_ENERGY = "autoRecoverEnergy_passive";
    private static final String ID_AUTO_RECOVER_SANTE_HEALTH = "autoRecoverSanTEPassive";
    private static final String ID_DEFAULT_SAN_TE_ZERO_PUNISHMENT = "default_san_te_zero_punishment";
    private static final String ID_MEIQIHEZI_JUEJUE_MAIN_WEAPON = "meiqihezi_mainWeapon_juejue";
    private static final String ID_MEIQIHEZI_EQUIPMENTS = "meiqihezi_equippments_passive";
    private static final String ID_MEIQIHEZI_BLOODY_SLASH = "meiqihezi_skill_bloody_slash";
    private static final String ID_MEIQIHEZI_CIRCLE_SLASH = "meiqihezi_skill_circle_slash";
    private static final String ID_MEIQIHEZI_UNCONCERN = "meiqihezi_skill_unconcern";
    private static final String ID_RED_BLEED = "red_bleed_passive";
    private static final String ID_RED_DEEPLY_SORROW = "red_deeplySorrow_skill";
    private static final String ID_RED_EQUIPMENTS = "red_equippments_passive";
    private static final String ID_RED_EVIL_SHOCK = "red_evilShock_skill";
    private static final String ID_RED_SANCTIFIED_BLADE = "red_mainWeapon_sanctifiedBlade";
    private static final String ID_RED_SOLITARY_ARROGANCE = "red_solitaryArrogance_skill";

    /** 示例角色里的组件 id。 */
    private static final String ID_EXAMPLE_SELF_REFRESHING = "example_self_refreshing_skill";


    private final Logger logger;

    public RoleLoader(Logger logger) {
        this.logger = logger;
    }

    /**
     * 本插件的角色定义（原 {@code RoleRegistry} 静态块内容，逐字迁移）。
     * <p>追加**第三个** = 示例角色（给"组件可请求重绘"这条能力一个生产使用点）。
     * **既有两个角色的定义一字未动**（组件集合、注册序、表现值都不变）。
     */
    public List<Definition> defaultDefinitions() {
        return List.of(
                new Definition("meiqihezi", RoleLoader::meiqiheziBuilder),
                new Definition("red", RoleLoader::redBuilder),
                new Definition("selfUpdateExample", RoleLoader::selfUpdateExampleBuilder)
        );
    }

    public int loadInto(RoleRegistry registry) {
        return loadInto(registry, defaultDefinitions());
    }

    /**
     * 逐条装配并注册。
     * <p><b>在 {@code build()} 与 {@code register()} 之间插入</b>
     * {@link Role#verifyDependencies()} —— 依赖不齐（或缺依赖环）的模板在**注册之前**就抛异常，
     * 由下面的既有 {@code catch (Throwable)} 记一条 {@code SEVERE} 并**跳过该角色**
     * ⇒ 它**根本不在注册表里**（既不会被 {@code /role set} 选中，也不会走到任何 {@code awake()}）。
     * 检查时机因此被钉死：**`build()` 之后、任何 `awake()` 之前**（实例化发生在 {@code RoleInstance} 构造期，
     * 而只有注册过的模板才会被实例化）。
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
                //装配期依赖检查（缺必需依赖 / 依赖环 ⇒ 抛 ComponentDependencyException）
                role.verifyDependencies();
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
                .maxEnergy(100)
                .maxSanTE(100)
                //表现值（名字/描述/冷却/耗能/图标）随组件自己的 Specification 走，
 //装配点**只写 setSlot**；注册顺序与既有实现逐字一致（= 派发序 = 渲染序）。
                .addComponent(ID_MEIQIHEZI_UNCONCERN, new MeiqiheziUnconcernSkill.Specification().setSlot(1))
                .addComponent(ID_MEIQIHEZI_BLOODY_SLASH, new MeiqiheziBloodySlashSkill.Specification().setSlot(2))
                .addComponent(ID_MEIQIHEZI_CIRCLE_SLASH, new MeiqiheziCircleSlashSkill.Specification().setSlot(3))
                .addComponent(ID_MEIQIHEZI_JUEJUE_MAIN_WEAPON, new MeiqiheziJuejueMainWeapon.Specification().setSlot(0))
                .faction(Faction.HUNTER)
                .addPassive(ID_DEFAULT_SAN_TE_ZERO_PUNISHMENT, DefaultSanTEZeroPunishment::new)
                .addPassive(ID_AUTO_RECOVER_SANTE_HEALTH, AutoRecoverSanTEHealthPassive::new)
                .addPassive(ID_AUTO_RECOVER_ENERGY, AutoRecoverEnergyPassive::new)
                .addPassive(ID_MEIQIHEZI_EQUIPMENTS, MeiqiheziEquipmentsPassive::new)
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
                .maxEnergy(0)
                .maxSanTE(100)
                .faction(Faction.SHADOW)
                .addPassive(ID_RED_BLEED, RedBleedPassive::new)
                .addComponent(ID_RED_SANCTIFIED_BLADE, new RedSanctifiedBladeMainWeapon.Specification().setSlot(0))
                .addComponent(ID_RED_SOLITARY_ARROGANCE, new RedSolitaryArroganceSkill.Specification().setSlot(1))
                .addComponent(ID_RED_EVIL_SHOCK, new RedEvilShockSkill.Specification().setSlot(2))
                .addComponent(ID_RED_DEEPLY_SORROW, new RedDeeplySorrowSkill.Specification().setSlot(3))
                .addPassive(ID_AUTO_RECOVER_SANTE_HEALTH, AutoRecoverSanTEHealthPassive::new)
                .addPassive(ID_RED_EQUIPMENTS, RedEquipmentsPassive::new)
                .addPassive(ID_DEFAULT_SAN_TE_ZERO_PUNISHMENT, DefaultSanTEZeroPunishment::new)
                .icon(Material.POPPY);
    }

    /**
     * **示例角色**：唯一目的 = 给「组件可请求重绘」这条能力一个**生产使用点**
     * （没有使用点的能力 = 未验证的能力）。
     * <p>它**不改动任何既有角色**：`red` / `meiqihezi` 的组件集合与注册序一字未动
     * ⇒ 外观取证与帧入口边界读数对本角色完全无感（代际对拍可证）。
     * <p>它的一个组件 = {@code ExampleSelfRefreshingSkill}（占槽 0），同时演示
     * 「请求式刷新」与「{@code dependsOnLiveState()==false} ⇒ 不每 tick 重绘」两件事。
     */
    private static Role.Builder selfUpdateExampleBuilder() {

        return new Role.Builder("selfUpdateExample")
                .displayName(Component.text("Self-Update Example"))
                .description(List.of(
                        Component.text("示例角色：演示「组件可请求重绘」"),
                        Component.text("组件只请求、不写：写入仍由框架在帧末 flush 完成")
                ))
                .maxHP(20)
                .maxEnergy(0)
                .maxSanTE(100)
                .faction(Faction.SHADOW)
                .addComponent(ID_EXAMPLE_SELF_REFRESHING, new ExampleSelfRefreshingSkill.Specification().setSlot(0))
                .icon(Material.CLOCK);
    }

}
