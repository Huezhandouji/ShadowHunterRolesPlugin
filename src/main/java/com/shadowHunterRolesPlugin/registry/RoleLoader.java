package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.builtin.*;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluBlueIceRevolverSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluHysteriaPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluTraumaMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.mainWeapon.MatinaMedicalShovelMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaKuangPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill.MatinaJudgmentSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill.MatinaRedstoneDroneSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill.MatinaSeaCrystalLampSkill;
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
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.mainWeapon.SinThornFangMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive.LawWordPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive.SinThornPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill.JudgmentThornSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill.SinDefenseSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill.SinThornEntangleSkill;
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
                new Definition("selfUpdateExample", RoleLoader::selfUpdateExampleBuilder),
                new Definition("canglu", RoleLoader::cangluBuilder),
                new Definition("sinThorn", RoleLoader::sinThornBuilder),
                new Definition("matina", RoleLoader::matinaBuilder)
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

    private static Role.Builder cangluBuilder(){
        return withBuiltIns(new Role.Builder("canglu"))
                .displayName(Component.text("苍鹭"))
                .description(List.of(
                        Component.text("聋子?")
                ))
                .faction(Faction.HUNTER)
                .addComponent(CangluTraumaMainWeapon.ID, new CangluTraumaMainWeapon.Specification().setSlot(0))
                .addComponent(CangluBlueIceRevolverSkill.ID, new CangluBlueIceRevolverSkill.Specification().setSlot(1))
                .addComponent(CangluHysteriaPassive.ID, new CangluHysteriaPassive.Specification())
                .icon(Material.BLUE_ICE);
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

    /**
     * **罪棘**（阵营 SHADOW）：尖牙光环 + 律法罪罚 + 三个主动。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「罪棘之牙」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件
     *       （{@code listener/MainWeaponListener#onAttackPlayer}），而需求要求"玩家近战也触发律法之言"
     *       ⇒ 必须有它作为落点（否则那条效果永远不触发）；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏，**全部 0 耗能**（按裁定：只靠冷却限制强度）；</li>
     *   <li>两个被动同样经 {@code addComponent} 统一入口注册（被动无栏位 ⇒ 不占热键栏）；</li>
     *   <li>基础属性**不显式声明** ⇒ 与 {@code red} / {@code meiqihezi} 一致，走框架默认；</li>
     *   <li>数值：尖牙 6 点伤害 / 4 点 SanTE / 7 格 / 1.5 秒（强化期 0.5 秒且打全体）；
     *       律法之言 15 秒、每 0.5 秒 1 点 SanTE；罪棘缠 CD 7 秒；罪恶的辩护 5 秒 + <b>结束后</b> CD 10 秒；
     *       审判孤刺 CD 40 秒。</li>
     * </ul>
     */
    private static Role.Builder sinThornBuilder() {
        return withBuiltIns(new Role.Builder("sinThorn"))
                .displayName(Component.text("罪棘"))
                .description(List.of(
                        Component.text("你负以荆棘，亦负以罪孽"),
                        Component.text("罪棘：7格内的敌人每1.5秒被召唤者尖牙撕咬，受6点伤害并损失4点特殊值"),
                        Component.text("律法之言：每次攻击附加15秒罪罚，每0.5秒削减目标1点特殊值")
                ))
                .faction(Faction.SHADOW)
                .addComponent(SinThornFangMainWeapon.ID, new SinThornFangMainWeapon.Specification().setSlot(0))
                .addComponent(SinThornEntangleSkill.ID, new SinThornEntangleSkill.Specification().setSlot(1))
                .addComponent(SinDefenseSkill.ID, new SinDefenseSkill.Specification().setSlot(2))
                .addComponent(JudgmentThornSkill.ID, new JudgmentThornSkill.Specification().setSlot(3))
                .addComponent(SinThornPassive.ID, new SinThornPassive.Specification())
                .addComponent(LawWordPassive.ID, new LawWordPassive.Specification())
                .icon(Material.WITHER_ROSE);
    }

    /**
     * **狂躁牧师 · 马提娜(Matina)**（阵营 HUNTER）：医疗设备 + 诉说苦怒（狂暴值 {@code KUANG}）+ 三个主动。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「医疗设备」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件
     *       （{@code listener/MainWeaponListener#onAttackPlayer}）⇒ "每次命中"的回血 / 特殊值伤害 /
     *       狂暴结算都必须落在这一把武器上；铁铲 = {@code Material.IRON_SHOVEL}，
     *       攻速 0.1 秒（2 刻）且冷却中不出伤；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏：<b>海晶灯</b>（CD 6 秒 / 耗能 15）· <b>红石无人机</b>
     *       （CD 20 秒，技能完全后起算 / 耗能 20）· <b>神罚</b>（CD 50 秒，技能完全后起算 / 耗能 0）；</li>
     *   <li>被动「诉说苦怒」经 {@code addComponent} 统一入口注册（被动无栏位 ⇒ 不占热键栏），
     *       它是<b>狂暴值的唯一持有者</b>：层数 / 每秒衰减 / 阈值加成 / 暴走死亡判定 / 层数粒子与 bossbar
     *       全在它里面；</li>
     *   <li>基础属性<b>不显式声明</b> ⇒ 与 {@code red} / {@code meiqihezi} 一致，走框架默认
     *       （生命上限 40、能量上限 100、SanTE 上限 100）；</li>
     *   <li>数值：普攻 4 点物理 + 目标回血 8 + 目标 5 点特殊值；海晶灯 r8 治疗 10 点 / 敌军 3 秒
     *       缓慢 II + 凋零 II + 虚弱 II；无人机 r8 跟随 / r5 每秒治疗 4 点或 1 秒中毒 III / 持续 15 秒；
     *       神罚 r25、引导 3 秒 + 7 秒、共 70 点特殊值。</li>
     * </ul>
     */
    private static Role.Builder matinaBuilder() {
        return withBuiltIns(new Role.Builder("matina"))
                .displayName(Component.text("狂躁牧师·马提娜"))
                .description(List.of(
                        Component.text("狂暴值越高，越是接近神，也越是接近死"),
                        Component.text("医疗设备：每次命中造成4点物理伤害，为目标回复8点生命并造成5点特殊值伤害"),
                        Component.text("诉说苦怒：每次成功治疗或成功攻击增加1点狂暴值；每秒减少1层，高于60层时每秒可能直接死亡"),
                        Component.text("海晶灯：洒出药物治疗同阵营并削弱敌人"),
                        Component.text("远程医疗：放出爱心无人机跟随并持续治疗"),
                        Component.text("神罚：引导3秒后展开魔法阵，对范围内敌人倾泻特殊值伤害")
                ))
                .faction(Faction.HUNTER)
                .addComponent(MatinaMedicalShovelMainWeapon.ID,
                        new MatinaMedicalShovelMainWeapon.Specification().setSlot(0))
                .addComponent(MatinaSeaCrystalLampSkill.ID,
                        new MatinaSeaCrystalLampSkill.Specification().setSlot(1))
                .addComponent(MatinaRedstoneDroneSkill.ID,
                        new MatinaRedstoneDroneSkill.Specification().setSlot(2))
                .addComponent(MatinaJudgmentSkill.ID,
                        new MatinaJudgmentSkill.Specification().setSlot(3))
                .addComponent(MatinaKuangPassive.ID, new MatinaKuangPassive.Specification())
                .icon(Material.SEA_LANTERN);
    }

}
