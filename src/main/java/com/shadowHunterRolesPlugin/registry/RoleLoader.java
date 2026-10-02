package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.builtin.*;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluBlueIceRevolverSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluDestinySkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluHysteriaPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluMelodySelectionSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluTraumaMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterEvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterGrudgeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterPounceSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterPreyPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterPullSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterStealthSkill;
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
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvilShockSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSolitaryArroganceSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.TestBowMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.mainWeapon.SinThornFangMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive.LawWordPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive.SinThornPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill.JudgmentThornSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill.SinDefenseSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill.SinThornEntangleSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.tek.TekDestinyPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.tek.TekTridentMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.tek.TekTruthThrustSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.tek.TekXiaoSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.tek.TekYueSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 显式角色装配器：装配发生在 {@code onEnable} 里、由主类显式调用，时序可见、可测试。
 *
 * <p><b>fail-fast 且按角色隔离</b>：某个角色装配失败（例如槽位冲突抛出的
 * {@link IllegalArgumentException}）则记 {@code SEVERE}、该角色不被注册，其余角色继续装配，
 * 不会升级成 {@code ExceptionInInitializerError} 拖垮整个插件。
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
     * 本插件的角色定义（自 {@code RoleRegistry} 静态块逐字迁移：组件集合、注册序、表现值都不变）。
     * <p>示例角色 {@code selfUpdateExample} 的用途 = 给"组件可请求重绘"这条能力一个生产使用点。
     */
    public List<Definition> defaultDefinitions() {
        return List.of(
                new Definition("meiqihezi", RoleLoader::meiqiheziBuilder),
                new Definition("red", RoleLoader::redBuilder),
                new Definition("selfUpdateExample", RoleLoader::selfUpdateExampleBuilder),
                new Definition("canglu", RoleLoader::cangluBuilder),
                new Definition("sinThorn", RoleLoader::sinThornBuilder),
                new Definition("matina", RoleLoader::matinaBuilder),
                new Definition("remoteness", RoleLoader::remotenessBuilder),
                new Definition("tek", RoleLoader::tekBuilder),
                new Definition("hunter", RoleLoader::hunterBuilder)
        );
    }

    public int loadInto(RoleRegistry registry) {
        return loadInto(registry, defaultDefinitions());
    }

    /**
     * 逐条装配并注册。
     * <p>在 {@code build()} 与 {@code register()} 之间插入 {@link Role#verifyDependencies()}：
     * 依赖不齐（或缺依赖环）的模板在注册之前就抛异常，由下面的 {@code catch (Throwable)}
     * 记一条 {@code SEVERE} 并跳过该角色，因此它根本不在注册表里
     * （既不会被 {@code /role set} 选中，也不会走到任何 {@code awake()}）。
     * 检查时机因此被钉死：{@code build()} 之后、任何 {@code awake()} 之前
     * （实例化发生在 {@code RoleInstance} 构造期，而只有注册过的模板才会被实例化）。
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
                //装配期依赖检查（缺必需依赖 / 依赖环则抛 ComponentDependencyException）
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
     * 把内建组件加进装配表（与技能 / 被动同一条路，它们就是普通组件）。
     *
     * <p><b>必须排在最前</b>：{@code EnergyComponent} 的描述符要按 id 取到渲染组件并挂
     * 「变更即置脏」的监听，因此渲染组件必须先注册（本方法内部也把它放在第一位）。
     *
     * <p>顺序 = 渲染 / 能量 / SanTE / 生命 / buff / 任务（装配序与行为都不变）。
     *
     * <p>调用点 = 每个角色的 builder 开头，内建块在模板组件之前；
     * 与「服务组件登记在模板之后」的旧序不同，但渲染 / 能量之间的相对序不变。
     */
    private static Role.Builder withBuiltIns(Role.Builder builder) {
        return builder
                .addComponent(HotbarRenderComponent.ID, new HotbarRenderComponent.Specification())
                .addComponent(EnergyComponent.ID, new EnergyComponent.Specification())
                .addComponent(SanTEComponent.ID, new SanTEComponent.Specification())
                .addComponent(VitalsComponent.ID, new VitalsComponent.Specification())
                .addComponent(BuffComponent.ID, new BuffComponent.Specification())
                .addComponent(TaskComponent.ID, new TaskComponent.Specification())
                .addComponent(BossbarRoleAttributesDisplayComponent.ID, new BossbarRoleAttributesDisplayComponent.Specification());
    }

    private static Role.Builder meiqiheziBuilder() {

        return withBuiltIns(new Role.Builder("meiqihezi")
 //「已被提供的类型」由装配方注入（core/Role 本身不认识任何组件类），
 //  否则组件声明 requires(框架级组件) 会被模板侧的依赖校验误报成"缺必需依赖"。
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
 //装配点只写 setSlot；注册顺序与既有实现逐字一致（= 派发序 = 渲染序）。
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
                //进化被动（无栏位、不占热键栏）：红的五档增益与升级消息都归它
                //  紧跟在流血被动之后 —— 流血被动与两个技能都声明它为必需依赖（档位读口）
                .addComponent(RedEvolutionPassive.ID, new RedEvolutionPassive.Specification())
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
     * 苍鹭（阵营 HUNTER）：创痕主武器 + 澜冰左轮 + 旋律选取 + 湛蓝命运，被动「深度癒症」。
     *
     * <p>装配口径：
     * <ul>
     *   <li><b>主武器「忧郁创痕」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件，
     *       「每次攻击叠一层创伤」因此必须有它作为落点；</li>
     *   <li>澜冰左轮占 1 号栏（右键射击、Q 换弹），旋律选取占 2 号栏（右键发射抓钩），
     *       湛蓝命运占 3 号栏（右键清负面 + 叠满创伤 + 8 秒强化窗口）；</li>
     *   <li>「深度癒症」经 {@code addComponent} 统一入口注册（被动无栏位，不占热键栏），
     *       它是「创伤」层数的唯一持有者，也是湛蓝命运 8 秒窗口的订阅源；</li>
     *   <li>基础属性不显式声明，与其余角色一致，走框架默认；</li>
     *   <li>数值：创痕 6 点物伤 + 12 点 SanTE + 抽 2 能量；
     *       左轮 每发 6 点物伤 / 8 发弹夹 / 换弹 8 能量 + 瞬移身后 4 点真伤；
     *       旋律选取 12 点物伤 + 拉拽（抗性 IV），CD 4 刻、耗能 4、存量 2 个、储存冷却 15 秒；
     *       湛蓝命运 速度 X 2 秒 + 叠满创伤 + 8 秒窗口，CD 16 刻、耗能 16。</li>
     * </ul>
     */
    private static Role.Builder cangluBuilder(){
        return withBuiltIns(new Role.Builder("canglu"))
                .displayName(Component.text("苍鹭"))
                .description(List.of(
                        Component.text("忧郁创痕"),
                        Component.text("深度癔症")
                ))
                .faction(Faction.HUNTER)
                .addComponent(CangluTraumaMainWeapon.ID, new CangluTraumaMainWeapon.Specification().setSlot(0))
                .addComponent(CangluBlueIceRevolverSkill.ID, new CangluBlueIceRevolverSkill.Specification().setSlot(1))
                .addComponent(CangluMelodySelectionSkill.ID, new CangluMelodySelectionSkill.Specification().setSlot(2))
                .addComponent(CangluDestinySkill.ID, new CangluDestinySkill.Specification().setSlot(3))
                .addComponent(CangluHysteriaPassive.ID, new CangluHysteriaPassive.Specification())
                .icon(Material.BLUE_ICE);
    }

    /**
     * 示例角色：唯一目的 = 给「组件可请求重绘」这条能力一个生产使用点
     * （没有使用点的能力 = 未验证的能力）。
     * <p>它不改动任何既有角色：{@code red} / {@code meiqihezi} 的组件集合与注册序一字未动。
     * <p>它的一个组件 = {@code ExampleSelfRefreshingSkill}（占槽 0），同时演示
     * 「请求式刷新」与「{@code dependsOnLiveState()==false} 则不每 tick 重绘」两件事。
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
     * 罪棘（阵营 SHADOW）：尖牙光环 + 律法罪罚 + 三个主动。
     *
     * <p>装配口径（各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「罪棘之牙」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件
     *       （{@code listener/MainWeaponListener#onAttackPlayer}），而"玩家近战也触发律法之言"
     *       要求有它作为落点（否则那条效果永远不触发）；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏，全部 0 耗能（只靠冷却限制强度）；</li>
     *   <li>两个被动同样经 {@code addComponent} 统一入口注册（被动无栏位，不占热键栏）；</li>
     *   <li>基础属性不显式声明，与 {@code red} / {@code meiqihezi} 一致，走框架默认；</li>
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

    public static Role.Builder remotenessBuilder(){
        return withBuiltIns(new Role.Builder("remoteness"))
                .displayName(Component.text("冷识"))
                .faction(Faction.SHADOW)
                .addComponent(TestBowMainWeapon.ID, new TestBowMainWeapon.Specification().setSlot(0))
                .icon(Material.SOUL_LANTERN);
    }

    /**
     * **特克(Tek)**：逆命天理（三叉戟）+ 逆转天意（「真理」层数持有者）+ 三个主动。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「逆命天理」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件
     *       （{@code listener/MainWeaponListener#onAttackPlayer}），"每次攻击 4 物理 + 4 真实 + 叠真理"
     *       因此必须落在这一把武器上；三叉戟 = {@code Material.TRIDENT}，
     *       攻击间隔 0.2 秒（4 刻）且冷却中不出伤，长按右键 0.8 秒蓄力突进 6 格（突进 CD 3 秒）；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏：<b>刺霄</b>（CD 10 秒）· <b>落岳</b>（CD 12 秒）·
     *       <b>真理之刺</b>（CD 20 秒，场上有人真理 ≥ 10 才解锁）；</li>
     *   <li>被动「逆转天意」经 {@code addComponent} 统一入口注册（被动无栏位 ⇒ 不占热键栏），
     *       它是<b>「真理」层数的门面</b>（真值在 {@code TekTruth} 静态账本，跨玩家实例可读），
     *       并负责"命中减 1 秒所有技能 CD / 得 1 点能量 / 能量 ≥ 90 且残血给护盾"；</li>
     *   <li>基础属性不显式声明 ⇒ 走框架默认（生命上限 40、能量上限 100、SanTE 上限 100）；</li>
     *   <li>数值：普攻 4 物理 + 4 真实；突进 6 格 / 3 秒 CD；刺霄 6.5 格 / 10 点物理；
     *       落岳 15 格高 / r5 / 15 点物理 + 1 秒眩晕；真理之刺 15 点真实 + 回自身 20 生命。</li>
     * </ul>
     */
    private static Role.Builder tekBuilder() {
        return withBuiltIns(new Role.Builder("tek"))
                .displayName(Component.text("特克"))
                .description(List.of(
                        Component.text("真理在枪尖上，命运在枪尖外"),
                        Component.text("逆命天理：每次攻击造成4点物理与4点真实伤害，并附加一层「真理」"),
                        Component.text("逆转天意：命中减少1秒所有技能冷却并获得1点能量；能量≥90且残血时获得20点伤害吸收"),
                        Component.text("刺霄：向前刺出一击并瞬移到最远被击中敌人身后"),
                        Component.text("落岳：跃起后砸落，对范围内敌人造成伤害与眩晕"),
                        Component.text("真理之刺：场上有真理≥10的角色时解锁，瞬移刺击并清空全场真理")
                ))
                .faction(Faction.HUNTER)
                .addComponent(TekTridentMainWeapon.ID, new TekTridentMainWeapon.Specification().setSlot(0))
                .addComponent(TekXiaoSkill.ID, new TekXiaoSkill.Specification().setSlot(1))
                .addComponent(TekYueSkill.ID, new TekYueSkill.Specification().setSlot(2))
                .addComponent(TekTruthThrustSkill.ID, new TekTruthThrustSkill.Specification().setSlot(3))
                .addComponent(TekDestinyPassive.ID, new TekDestinyPassive.Specification())
                .icon(Material.NETHERITE_SCRAP);
    }

    /**
     * **猎手(Hunter)**（阵营 {@link Faction#SHADOW}）：遗愤（下界合金剑）+
     * 猎杀（标记）+ 进化指数（五档）+ 三个主动。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「遗愤」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件
     *       （{@code listener/MainWeaponListener#onAttackPlayer}），"每次攻击回血 / 随机物理 +
     *       随机 SanTE / 对被标记者额外灵魂伤害"因此必须落在这一把武器上；
     *       下界合金剑 = {@code Material.NETHERITE_SWORD}，攻击间隔 0.3 秒（6 刻）且冷却中不出伤；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏：<b>扑击</b>（金锭，CD 3 秒）· <b>拉回</b>（垂泪藤，CD 6 秒）·
     *       <b>遁形</b>（哭泣的黑曜石，CD 20 秒且 <b>技能完全后</b>起算）；三者<b>全部 0 耗能</b>
     *       （需求未提耗能，只靠冷却限制强度 —— 与罪棘 / 特克同口径）；</li>
     *   <li>两个被动经 {@code addComponent} 统一入口注册（被动无栏位 ⇒ 不占热键栏）：
     *       <b>猎杀</b>（30 格内最近敌人的标记持有者 + 标记时给速度 III）与
     *       <b>进化指数</b>（五档，击杀升级，封顶 5 级）；</li>
     *   <li>基础属性<b>不显式声明</b> ⇒ 走框架默认（生命上限 40、能量上限 100、SanTE 上限 100）；</li>
     *   <li>数值：遗愤 6~10 物理 + 4~10 SanTE + 回自己 4 生命（0.3 秒一击）；
     *       扑击 4 格位移 → 3 格内 12 物理 + 3 秒缓慢 III + 回自己 4 生命；
     *       拉回 0.3 秒蓄力（2 倍速弩蓄力音）→ 12 格穿刺 15 物理（最多一人，打空也画紫色投出路径）
     *       → 0.8 秒后按每秒 10 格拽向视角前方（再次释放 20 格/秒，最多 2.5 秒）；
     *       遁形 15 秒速度 VI + 隐身 + 抗性 V + 免疫缓慢，用技能或攻击即中断。</li>
     * </ul>
     */
    private static Role.Builder hunterBuilder() {
        return withBuiltIns(new Role.Builder("hunter"))
                .displayName(Component.text("猎手"))
                .description(List.of(
                        Component.text("猎手：先标记猎物，再把它拽回眼前"),
                        Component.text("遗愤：每次攻击回复4点生命，造成6~10点物理与4~10点特殊值伤害"),
                        Component.text("猎杀：标记30格内最近的敌人，标记时获得10秒速度III，普攻被标记者额外造成10点灵魂伤害"),
                        Component.text("扑击：向前扑击4格，并撕咬3格内最近的一名敌人"),
                        Component.text("拉回：引导0.3秒后穿刺12格，0.8秒后把命中者拽向视角前方"),
                        Component.text("遁形：速度VI、隐身、抗性V并免疫缓慢，使用技能或攻击即中断"),
                        Component.text("进化指数：击杀敌人可进化五档")
                ))
                .faction(Faction.SHADOW)
                .addComponent(HunterGrudgeMainWeapon.ID,
                        new HunterGrudgeMainWeapon.Specification().setSlot(0))
                .addComponent(HunterPounceSkill.ID,
                        new HunterPounceSkill.Specification().setSlot(1))
                .addComponent(HunterPullSkill.ID,
                        new HunterPullSkill.Specification().setSlot(2))
                .addComponent(HunterStealthSkill.ID,
                        new HunterStealthSkill.Specification().setSlot(3))
                .addComponent(HunterPreyPassive.ID, new HunterPreyPassive.Specification())
                .addComponent(HunterEvolutionPassive.ID, new HunterEvolutionPassive.Specification())
                .icon(Material.NETHERITE_SWORD);
    }

}
