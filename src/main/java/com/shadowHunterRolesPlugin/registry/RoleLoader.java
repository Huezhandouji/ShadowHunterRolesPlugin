package com.shadowHunterRolesPlugin.registry;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.builtin.*;
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
import com.shadowHunterRolesPlugin.core.RoleInstance;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Boss;

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

    /**
     * **把 6 件内建组件加进装配表**（★ 与技能/被动**同一条路** ⇒ 它们就是普通组件）。
     *
     * <p><b>★ 必须排在最前</b>：`EnergyComponent` 的描述符要按 id 取到**渲染组件**并挂
     * 「变更即置脏」的监听 ⇒ 渲染组件必须**先**注册（本方法内部也把它放在第一位）。
     *
     * <p>顺序 = 渲染 / 能量 / SanTE / 生命 / buff / 任务（与既有 `buildBuiltIns` 的语句顺序逐字相同
     * ⇒ 装配序与行为都不变）。
     *
     * <p>★ 调用点 = 每个角色的 builder **开头**（三个角色都调）⇒ 内建块在模板组件之前，
     * 与「服务组件登记在模板之后」的旧序不同，但**渲染/能量之间的相对序不变** ✓。
     */
    private static Role.Builder withBuiltIns(Role.Builder builder) {
        return builder
                .addComponent(HotbarRenderComponent.ID, new HotbarRenderComponent.Specification())
                .addComponent(EnergyComponent.ID, new EnergyComponent.Specification())
                .addComponent(SanTEComponent.ID, new SanTEComponent.Specification())
                .addComponent(VitalsComponent.ID, new VitalsComponent.Specification())
                .addComponent(BuffComponent.ID, new BuffComponent.Specification())
                .addComponent(TaskComponent.ID, new TaskComponent.Specification())
                .addComponent(BossbarRoleAttributesDisplayPassive.ID, new BossbarRoleAttributesDisplayPassive.Specification());
    }

    private static Role.Builder meiqiheziBuilder() {

        return withBuiltIns(new Role.Builder("meiqihezi")
 //★ 「已被提供的类型」由**装配方**注入（`core/Role` 本身不认识任何组件类 ✓）——
 //  否则组件声明 `requires(框架级组件)` 会被模板侧的依赖校验误报成"缺必需依赖"。
                .displayName(Component.text("MeiqiHezi"))
                //Component.text("战斗疯子\n普攻20能量以上左键造成范围伤害并消耗能量，20以下只能打一个人\n一技能加速\n二技能三段突进并造成伤害\n三技能圆弧斩，范围真伤")
                .description(List.of(
                        Component.text("战斗疯子"),
                        Component.text("普攻20能量以上左键造成范围伤害并消耗能量，20以下只能打一个人"),
                        Component.text("一技能加速"),
                        Component.text("二技能三段突进并造成伤害"),
                        Component.text("三技能圆弧斩，范围真伤")
                ))
                //表现值（名字/描述/冷却/耗能/图标）随组件自己的 Specification 走，
 //装配点**只写 setSlot**；注册顺序与既有实现逐字一致（= 派发序 = 渲染序）。
                .addComponent(MeiqiheziUnconcernSkill.ID, new MeiqiheziUnconcernSkill.Specification().setSlot(1))
                .addComponent(MeiqiheziBloodySlashSkill.ID, new MeiqiheziBloodySlashSkill.Specification().setSlot(2))
                .addComponent(MeiqiheziCircleSlashSkill.ID, new MeiqiheziCircleSlashSkill.Specification().setSlot(3))
                .addComponent(MeiqiheziJuejueMainWeapon.ID, new MeiqiheziJuejueMainWeapon.Specification().setSlot(0))
                .faction(Faction.HUNTER)
                .addComponent(DefaultSanTEZeroPunishment.ID, new DefaultSanTEZeroPunishment.Specification())
                .addComponent(AutoRecoverSanTEHealthPassive.ID, new AutoRecoverSanTEHealthPassive.Specification())
                .addComponent(AutoRecoverEnergyPassive.ID, new AutoRecoverEnergyPassive.Specification())
                .addComponent(MeiqiheziEquipmentsPassive.ID, new MeiqiheziEquipmentsPassive.Specification())
                .icon(Material.DIAMOND_HOE));
    }

    private static Role.Builder redBuilder() {

        return withBuiltIns(new Role.Builder("red"))
                .displayName(Component.text("Red"))
                //Component.text("待到血腥降临，一切化为土尘\n普攻造成15流血\n一技能捅人恢复生命\n二技能致盲敌人并结算5层流血恢复te\n三技能烧自己te开启狂暴")
                .description(List.of(
                        Component.text("待到血腥降临，一切化为土尘"),
                        Component.text("普攻造成15流血"),
                        Component.text("一技能捅人恢复生命")
                ))
                .faction(Faction.SHADOW)
                .addComponent(RedBleedPassive.ID, new RedBleedPassive.Specification())
                .addComponent(RedSanctifiedBladeMainWeapon.ID, new RedSanctifiedBladeMainWeapon.Specification().setSlot(0))
                .addComponent(RedSolitaryArroganceSkill.ID, new RedSolitaryArroganceSkill.Specification().setSlot(1))
                .addComponent(RedEvilShockSkill.ID, new RedEvilShockSkill.Specification().setSlot(2))
                .addComponent(RedDeeplySorrowSkill.ID, new RedDeeplySorrowSkill.Specification().setSlot(3))
                .addComponent(AutoRecoverSanTEHealthPassive.ID, new AutoRecoverSanTEHealthPassive.Specification())
                .addComponent(RedEquipmentsPassive.ID, new RedEquipmentsPassive.Specification())
                .addComponent(DefaultSanTEZeroPunishment.ID, new DefaultSanTEZeroPunishment.Specification())
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

        return withBuiltIns(new Role.Builder("selfUpdateExample"))
                .displayName(Component.text("Self-Update Example"))
                .description(List.of(
                        Component.text("示例角色：演示「组件可请求重绘」"),
                        Component.text("组件只请求、不写：写入仍由框架在帧末 flush 完成")
                ))
                .faction(Faction.SHADOW)
                .addComponent(ExampleSelfRefreshingSkill.ID, new ExampleSelfRefreshingSkill.Specification().setSlot(0))
                .icon(Material.CLOCK);
    }

}
