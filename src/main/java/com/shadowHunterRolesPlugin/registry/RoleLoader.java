package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.builtin.*;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertAssembleSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertDroneSystem;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertHuntOrderSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertLockOnPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertOverloadSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.albert.AlbertThrustSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronRevolverSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronFateSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronTraumaPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronHookSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.HeronMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterEvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterGrudgeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterPounceSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterPreyPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterPullSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.hunter.HunterStealthSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.mainWeapon.LinIronBloodMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.passive.LinKeplerPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.passive.LinMartyrPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill.LinBoneNailSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill.LinShadowRaidSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill.LinTripleStrikeSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.mainWeapon.MatinaMedicalShovelMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaKuangPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill.MatinaJudgmentSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill.MatinaRedstoneDroneSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill.MatinaSeaCrystalLampSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziChargeComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziMadnessPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziSanDrainComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.MeiqiheziThrowComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.mainWeapon.RavenRevolverMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenAirMobilityPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill.RavenDeathEyeSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill.RavenSkyStrikeSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill.RavenSomersaultSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedBleedPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedDeeplySorrowSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvilShockSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSolitaryArroganceSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.*;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistStrugglePassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.passive.ResistEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistConquestSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistRampageSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistRoarSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sayaka.SayakaBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.sayaka.SayakaBladeStormSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sayaka.SayakaFlashSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.sayaka.SayakaFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.sayaka.SayakaGauntletPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.sayaka.SayakaGrenadeSkill;
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
                new Definition("Heron", RoleLoader::cangluBuilder),
                new Definition("sinThorn", RoleLoader::sinThornBuilder),
                new Definition("matina", RoleLoader::matinaBuilder),
                new Definition("remoteness", RoleLoader::remotenessBuilder),
                new Definition("tek", RoleLoader::tekBuilder),
                new Definition("hunter", RoleLoader::hunterBuilder),
                new Definition("albert", RoleLoader::albertBuilder),
                new Definition("lin", RoleLoader::linBuilder),
                new Definition("raven", RoleLoader::ravenBuilder),
                new Definition("sayaka", RoleLoader::sayakaBuilder),
                new Definition("resist", RoleLoader::resistBuilder)
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
     * <p>顺序 = 渲染 / 能量 / SanTE / 生命 / buff / 任务 / 阵营（装配序与行为都不变）。
     *
     * <p>调用点 = 每个角色的 builder 开头，内建块在模板组件之前；
     * 与「服务组件登记在模板之后」的旧序不同，但渲染 / 能量之间的相对序不变。
     *
     * <p><b>★ 声明阵营由装配点显式传入</b>：{@code declared} 就是同一角色
     * {@code Role.Builder#faction(...)} 写的那个值（本方法在链式调用里先于 {@code .faction(...)} 执行
     * ⇒ 描述符无法从 builder 反查，只能由调用方给）。阵营组件的描述符<b>每次都要 new</b>：
     * {@code create} 经方法引用捕获描述符实例，共享一份会让两个角色拿到同一个声明阵营。
     */
    private static Role.Builder withBuiltIns(Role.Builder builder, Faction declared) {
        return builder
                .addComponent(HotbarRenderComponent.ID, new HotbarRenderComponent.Specification())
                .addComponent(EnergyComponent.ID, new EnergyComponent.Specification())
                .addComponent(SanTEComponent.ID, new SanTEComponent.Specification())
                .addComponent(VitalsComponent.ID, new VitalsComponent.Specification())
                .addComponent(BuffComponent.ID, new BuffComponent.Specification())
                .addComponent(TaskComponent.ID, new TaskComponent.Specification())
                .addComponent(FactionComponent.ID, new FactionComponent.Specification(declared))
                .addComponent(AutoRecoverSanTEHealthPassive.ID, new AutoRecoverSanTEHealthPassive.Specification())
                .addComponent(DefaultSanTEZeroPunishment.ID, new DefaultSanTEZeroPunishment.Specification())
                .addComponent(BossbarRoleAttributesDisplayComponent.ID, new BossbarRoleAttributesDisplayComponent.Specification());
    }

    private static Role.Builder meiqiheziBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;

        return withBuiltIns(new Role.Builder("meiqihezi"), faction)
 //「已被提供的类型」由装配方注入（core/Role 本身不认识任何组件类），
 //  否则组件声明 requires(框架级组件) 会被模板侧的依赖校验误报成"缺必需依赖"。
                .displayName(Component.text("梅妻鹤子"))
                //Component.text("战斗疯子\n普攻20能量以上左键造成范围伤害并消耗能量，20以下只能打一个人\n一技能加速\n二技能三段突进并造成伤害\n三技能圆弧斩，范围真伤")
                .description(List.of(
                        Component.text("休言半纸无多重，万斛离愁尽耐担"),
                        Component.text("普攻：8 物理 + 3~6 特殊值；每秒耗 5 能量充能（上限 3 层）"),
                        Component.text("充能攻击：回 4 生命 + 触及范围 3 真伤；满层触及 +2"),
                        Component.text("右键蓄力 0.8 秒丢出决爵，命中者插着跑；靠近拾回"),
                        Component.text("须狂命：SanTE 越低增益越多，归零时爆发 15 秒")
                ))
                //表现值（名字/描述/冷却/耗能/图标）随组件自己的 Specification 走，
 //装配点只写 setSlot；注册顺序与既有实现逐字一致（= 派发序 = 渲染序）。
                .addComponent(MeiqiheziChargeComponent.ID, new MeiqiheziChargeComponent.Specification())
                .addComponent(MeiqiheziThrowComponent.ID, new MeiqiheziThrowComponent.Specification())
                .addComponent(MeiqiheziSanDrainComponent.ID, new MeiqiheziSanDrainComponent.Specification())
                .addComponent(MeiqiheziUnconcernSkill.ID, new MeiqiheziUnconcernSkill.Specification().setSlot(1))
                .addComponent(MeiqiheziBloodySlashSkill.ID, new MeiqiheziBloodySlashSkill.Specification().setSlot(2))
                .addComponent(MeiqiheziCircleSlashSkill.ID, new MeiqiheziCircleSlashSkill.Specification().setSlot(3))
                .addComponent(MeiqiheziJuejueMainWeapon.ID, new MeiqiheziJuejueMainWeapon.Specification().setSlot(0))
                .faction(faction)
                .addComponent(MeiqiheziEquipmentsPassive.ID, new MeiqiheziEquipmentsPassive.Specification())
                .addComponent(MeiqiheziMadnessPassive.ID, new MeiqiheziMadnessPassive.Specification())
                .addComponent(MeiqiheziFloatingTextComponent.ID, new MeiqiheziFloatingTextComponent.Specification())
                .icon(Material.DIAMOND_HOE);
    }

    private static Role.Builder redBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.SHADOW;

        return withBuiltIns(new Role.Builder("red"), faction)
                .displayName(Component.text("红"))
                //Component.text("待到血腥降临，一切化为土尘\n普攻造成15流血\n一技能捅人恢复生命\n二技能致盲敌人并结算5层流血恢复te\n三技能烧自己te开启狂暴")
                .description(List.of(
                        Component.text("待到血腥降临，一切化为土尘"),
                        Component.text("纯白之刺：前方锥形穿刺，8 物理 + 3~6 特殊值 + 5 层流血"),
                        Component.text("饮血 / 何足挂齿：流血敌人回血回 SanTE，每 3 次命中减 CD"),
                        Component.text("贯通新生 / 烟血震慑 / 血亲呼唤：四段穿刺、范围结算、燃烧 SanTE 的狂暴")
                ))
                .faction(faction)
                .addComponent(RedBleedPassive.ID, new RedBleedPassive.Specification())
                //进化被动（无栏位、不占热键栏）：红的五档增益与升级消息都归它
                //  紧跟在流血被动之后 —— 流血被动与两个技能都声明它为必需依赖（档位读口）
                .addComponent(RedEvolutionPassive.ID, new RedEvolutionPassive.Specification())
                .addComponent(RedSanctifiedBladeMainWeapon.ID, new RedSanctifiedBladeMainWeapon.Specification().setSlot(0))
                //技能栏位按需求描述顺序：1=贯通新生 / 2=烟血震慑 / 3=血亲呼唤
                .addComponent(RedDeeplySorrowSkill.ID, new RedDeeplySorrowSkill.Specification().setSlot(1))
                .addComponent(RedEvilShockSkill.ID, new RedEvilShockSkill.Specification().setSlot(2))
                .addComponent(RedSolitaryArroganceSkill.ID, new RedSolitaryArroganceSkill.Specification().setSlot(3))
                .addComponent(RedEquipmentsPassive.ID, new RedEquipmentsPassive.Specification())
                .addComponent(RedFloatingTextComponent.ID, new RedFloatingTextComponent.Specification())
                .icon(Material.POPPY);
    }

    /**
     * 苍鹭（阵营 HUNTER）：忧郁创痕（主武器）+ 音澜（双持左轮）+ 旋律选取（勾爪）+ 湛蓝命运（大招），
     * 被动「深度癔症」。
     *
     * <p>装配口径：
     * <ul>
     *   <li><b>主武器「忧郁创痕」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件，
     *       「每次攻击叠一层创伤」因此必须有它作为落点；</li>
     *   <li>音澜占 1 号栏（左键左枪 / 右键右枪 / 双手同按齐射、Q 双手换弹），
     *       旋律选取占 2 号栏（右键发射勾爪、再按拉拽），
     *       湛蓝命运占 3 号栏（右键突进 + 展开领域）；</li>
     *   <li>「深度癔症」经 {@code addComponent} 统一入口注册（被动无栏位，不占热键栏），
     *       它是「创伤」层数的唯一持有者；</li>
     *   <li>基础属性不显式声明，与其余角色一致，走框架默认；</li>
     *   <li>数值（真值在各自组件里，这里是摘要）：忧郁创痕 10 物伤 + 3~7 SanTE + 抽 5 能量、0.1 秒一击；
     *       音澜 双枪各 6 发、单发 8 物伤 + 3~5 SanTE、换弹 32 刻 / 每把 15 能量；
     *       旋律选取 8 物伤 + 4~6 SanTE、存量 2 个、12 秒恢复一个 / 10 能量；
     *       湛蓝命运 8 格突进 + r25 领域 20 秒，<b>每次使用消耗 10 能量</b>，
     *       领域内 CD 4 秒、领域结束后 25 秒冷却。</li>
     * </ul>
     */
    private static Role.Builder cangluBuilder(){
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;

        return withBuiltIns(new Role.Builder("Heron"), faction)
                .displayName(Component.text("Heron"))
                .description(List.of(
                        Component.text("忧郁创痕"),
                        Component.text("深度癔症")
                ))
                .faction(faction)
                .addComponent(HeronMainWeapon.ID, new HeronMainWeapon.Specification().setSlot(0))
                .addComponent(HeronRevolverSkill.ID, new HeronRevolverSkill.Specification().setSlot(1))
                .addComponent(HeronHookSkill.ID, new HeronHookSkill.Specification().setSlot(2))
                .addComponent(HeronFateSkill.ID, new HeronFateSkill.Specification().setSlot(3))
                .addComponent(HeronEquipmentsPassive.ID, new HeronEquipmentsPassive.Specification())
                .addComponent(HeronFloatingTextComponent.ID, new HeronFloatingTextComponent.Specification())
                .addComponent(HeronTraumaPassive.ID, new HeronTraumaPassive.Specification())
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
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.SHADOW;

        return withBuiltIns(new Role.Builder("selfUpdateExample"), faction)
                .displayName(Component.text("Self-Update Example"))
                .description(List.of(
                        Component.text("示例角色：演示「组件可请求重绘」"),
                        Component.text("组件只请求、不写：写入仍由框架在帧末 flush 完成")
                ))
                .faction(faction)
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
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.SHADOW;

        return withBuiltIns(new Role.Builder("sinThorn"), faction)
                .displayName(Component.text("罪棘"))
                .description(List.of(
                        Component.text("你负以荆棘，亦负以罪孽"),
                        Component.text("罪棘：7格内的敌人每1.5秒被召唤者尖牙撕咬，受6点伤害并损失4点特殊值"),
                        Component.text("律法之言：每次攻击附加15秒罪罚，每0.5秒削减目标1点特殊值")
                ))
                .faction(faction)
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
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;
        return withBuiltIns(new Role.Builder("matina"), faction)
                .displayName(Component.text("狂躁牧师·马提娜"))
                .description(List.of(
                        Component.text("狂暴值越高，越是接近神，也越是接近死"),
                        Component.text("医疗设备：每次命中造成4点物理伤害，为目标回复8点生命并造成5点特殊值伤害"),
                        Component.text("诉说苦怒：每次成功治疗或成功攻击增加1点狂暴值；每秒减少1层，高于60层时每秒可能直接死亡"),
                        Component.text("海晶灯：洒出药物治疗同阵营（含自己）并削弱敌人"),
                        Component.text("远程医疗：放出爱心无人机跟随并持续治疗（含自己）"),
                        Component.text("神罚：引导3秒后展开魔法阵，对范围内敌人倾泻特殊值伤害")
                ))
                .faction(faction)
                .addComponent(MatinaMedicalShovelMainWeapon.ID,
                        new MatinaMedicalShovelMainWeapon.Specification().setSlot(0))
                .addComponent(MatinaSeaCrystalLampSkill.ID,
                        new MatinaSeaCrystalLampSkill.Specification().setSlot(1))
                .addComponent(MatinaRedstoneDroneSkill.ID,
                        new MatinaRedstoneDroneSkill.Specification().setSlot(2))
                .addComponent(MatinaJudgmentSkill.ID,
                        new MatinaJudgmentSkill.Specification().setSlot(3))
                .addComponent(MatinaKuangPassive.ID, new MatinaKuangPassive.Specification())
                .addComponent(MatinaFloatingTextComponent.ID, new MatinaFloatingTextComponent.Specification())
                .icon(Material.SEA_LANTERN);
    }

    /**
     * 冷识（阵营 SHADOW）：冷淡（弓）+ 忘怀 / 重构 / 塑造 + 「始末」与八档进化。
     *
     * <p>装配口径：
     * <ul>
     *   <li><b>主武器「冷淡」占 0 号栏</b> —— 本族是弓弩（{@code BowWeapon}）：右键归原版
     *       （拉弓 / 击发），命中由 {@code BowWeaponListener} 在 {@code ProjectileHitEvent} 上
     *       交回组件；</li>
     *   <li>忘怀 / 重构 / 塑造 / 箭矢占 1 / 2 / 3 / 4 号栏（箭矢是"槽空即补发"的自愈式弹药），
     *       全部 0 耗能（只靠冷却限制强度）；</li>
     *   <li><b>进化被动排在消费者之前</b>：{@code start()} 按注册序广播，而「始末」与「塑造」
     *       都在自己的 {@code start()} 里取它、并在逐刻节拍里读它的档位读数 ⇒ 先注册它，
     *       "读口第一次被读到之前它已经生效"才是结构上的事实；</li>
     *   <li>「始末」是冷识的持续增益与 5 级光环的节拍持有者（被动无栏位，不占热键栏）；</li>
     *   <li>基础属性不显式声明，与其余角色一致，走框架默认；</li>
     *   <li>数值：冷淡 9 点特殊值 + 6 点物伤（飞行途中 4 格内再 3 特殊值 + 2 真伤），CD 2 秒；
     *       忘怀 标记 30 秒窗口、CD 20 秒（结束后开始）；重构 10 秒窗口、CD 15 秒（结束后开始）；
     *       塑造 半径 20、漂浮 4 秒 + 30 特殊值、40 个粒子陷阱（碰到晕 1 秒），CD 20 秒；
     *       进化八档见 {@code RemotenessEvolutionPassive}。</li>
     * </ul>
     */
    public static Role.Builder remotenessBuilder(){
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.SHADOW;

        return withBuiltIns(new Role.Builder("remoteness"), faction)
                .displayName(Component.text("冷识"))
                .description(List.of(
                        Component.text("始末：永久速度一、跳跃提升一"),
                        Component.text("冷淡：箭矢命中扣除9点特殊值并造成6点物理伤害；飞行途中4格内的敌人被扣除3点特殊值与2点真实伤害")
                ))
                .faction(faction)
                //进化被动（无栏位）：八档数值与升级消息都归它；排在消费者之前（见方法注释）
                .addComponent(RemotenessEvolutionPassive.ID, new RemotenessEvolutionPassive.Specification())
                .addComponent(RemotenessStartEndPassive.ID, new RemotenessStartEndPassive.Specification())
                .addComponent(RemotenessFrostBowMainWeapon.ID, new RemotenessFrostBowMainWeapon.Specification().setSlot(0))
                .addComponent(RemotenessOblivionSkill.ID, new RemotenessOblivionSkill.Specification().setSlot(1))
                .addComponent(RemotenessReconstructSkill.ID, new RemotenessReconstructSkill.Specification().setSlot(2))
                .addComponent(RemotenessShapingSkill.ID, new RemotenessShapingSkill.Specification().setSlot(3))
                .addComponent(RemotenessArrowGivingSkill.ID, new RemotenessArrowGivingSkill.Specification().setSlot(17))
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
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;
        return withBuiltIns(new Role.Builder("tek"), faction)
                .displayName(Component.text("特克"))
                .description(List.of(
                        Component.text("真理在枪尖上，命运在枪尖外"),
                        Component.text("逆命天理：每次攻击造成4点物理与4点真实伤害，并附加一层「真理」"),
                        Component.text("逆转天意：命中减少1秒所有技能冷却并获得1点能量；能量≥90且残血时获得20点伤害吸收"),
                        Component.text("刺霄：向前刺出一击并瞬移到最远被击中敌人身后"),
                        Component.text("落岳：跃起后砸落，对范围内敌人造成伤害与眩晕"),
                        Component.text("真理之刺：场上有真理≥10的角色时解锁，瞬移刺击并清空全场真理")
                ))
                .faction(faction)
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
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.SHADOW;
        return withBuiltIns(new Role.Builder("hunter"), faction)
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
                .faction(faction)
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

    /**
     * **艾尔伯特(Albert)**：构筑者铳剑 + 锁定（标记 / 零件）+ 过度响应协议（无人机编队）
     * + 四个主动。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「构筑者铳剑」占 0 号栏</b> —— 插件只把攻击事件投递给主武器组件
     *       （{@code listener/MainWeaponListener#onAttackPlayer}），
     *       "近战 9 物理 + 3~6 特殊值 / 积攒零件"必须落在这一把武器上；
     *       右键射击也走它的 {@code onCast}（同一把武器，两种攻击形态）；</li>
     *   <li>四个主动占 <b>1 / 2 / 3 / 4</b> 号栏：<b>高斯装配</b>（铁锭，CD 8 秒 / 耗能 10）·
     *       <b>猎杀指令</b>（金锭，CD 12 秒 / 耗能 10）· <b>全功率推进</b>（末影珍珠，
     *       无 CD、储存 2 次、每 8 秒耗 6 能量回一次）· <b>过载协议</b>（下界之星，
     *       CD 30 秒<b>技能完全后</b>起算 / 耗能 70）；</li>
     *   <li><b>两个被动经 {@code addComponent} 统一入口注册</b>（被动无栏位 ⇒ 不占热键栏）：
     *       <b>锁定</b>（「猎杀目标」标记与「零件」层数的持有者）与
     *       <b>过度响应协议</b>（无人机编队 + 主动防御 + 光环 + actionbar HUD）；</li>
     *   <li>★ <b>注册序有语义</b>：锁定被动排在无人机系统之前 —— 后者在自己的 {@code start()} 里
     *       取前者（依赖解析走装配期，但"读口第一次被读到之前它已经在表里"仍应成立）；</li>
     *   <li>悬浮文本「独白」最后注册（它是依赖图的叶子，别的组件单方向依赖它）；</li>
     *   <li>基础属性<b>不显式声明</b> ⇒ 走框架默认（生命上限 40、能量上限 100、SanTE 上限 100）；</li>
     *   <li>★ <b>阵营 = {@link Faction#HUNTER}</b>（需求未指定 ⇒ 取默认假设，
     *       要改只需动这一行）。</li>
     * </ul>
     */
    private static Role.Builder albertBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;
        return withBuiltIns(new Role.Builder("albert"), faction)
                .displayName(Component.text("艾尔伯特"))
                .description(List.of(
                        Component.text("未来像雾……可我已经学会在雾里装填"),
                        Component.text("构筑者铳剑：近战 9 物理 + 3~6 特殊值，每命中积攒 1 层「零件」"),
                        Component.text("满 4 层零件自动部署一架高斯无人机（最多 4 架）"),
                        Component.text("右键射击：6 发弹匣的亚音速穿甲弹，命中标记「猎杀目标」20 秒"),
                        Component.text("锁定：无人机优先打猎杀目标；击杀猎杀目标立刻补充一架无人机"),
                        Component.text("过度响应协议：无人机主动防御抵消攻击，主动防御期间获得抗性 255"),
                        Component.text("高斯装配 / 猎杀指令 / 全功率推进 / 过载协议")
                ))
                .faction(faction)
                //被动先行：锁定（标记 + 零件）→ 无人机编队（读标记、部署、主动防御、HUD）
                .addComponent(AlbertLockOnPassive.ID, new AlbertLockOnPassive.Specification())
                .addComponent(AlbertDroneSystem.ID, new AlbertDroneSystem.Specification())
                //主武器 + 四个主动（0 / 1 / 2 / 3 / 4 号栏）
                .addComponent(AlbertMainWeapon.ID, new AlbertMainWeapon.Specification().setSlot(0))
                .addComponent(AlbertAssembleSkill.ID, new AlbertAssembleSkill.Specification().setSlot(1))
                .addComponent(AlbertHuntOrderSkill.ID, new AlbertHuntOrderSkill.Specification().setSlot(2))
                .addComponent(AlbertThrustSkill.ID, new AlbertThrustSkill.Specification().setSlot(3))
                .addComponent(AlbertOverloadSkill.ID, new AlbertOverloadSkill.Specification().setSlot(4))
                //悬浮文本（叶子：只依赖 VitalsComponent）
                .addComponent(AlbertFloatingTextComponent.ID, new AlbertFloatingTextComponent.Specification())
                .icon(Material.IRON_SWORD);
    }

    /**
     * **林(Lin)**（阵营 HUNTER）：铁血镇压者（铁剑）+ 三重打击 / 影袭 / 透骨钉 +
     * 开普勒装置 / 殉道。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「铁血镇压者」占 0 号栏</b>（{@code Material.IRON_SWORD}）——
     *       插件只把攻击事件投递给主武器组件，而"每次攻击 8 物理 + 4~10 特殊值 + 回 1 能量 +
     *       削敌攻击力 + 紫色打击粒子 + 破碎音效"都必须落在这一把武器上；
     *       攻速 100（原版冷却 0.2 秒）且冷却中不出伤（{@code onAttack} 开头自判）；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏：<b>三重打击</b>（CD 8 秒 / 耗能 0）· <b>影袭</b>
     *       （CD 8 秒 / 耗能 0）· <b>透骨钉</b>（CD 25 秒 / 耗能 25）；</li>
     *   <li>两个被动经 {@code addComponent} 统一入口注册（被动无栏位 ⇒ 不占热键栏）：
     *       「开普勒装置」是能量 / 特殊值的循环引擎，「殉道」是特殊值归零时的替代惩罚；</li>
     *   <li><b>装配序有意安排</b>：两个被动排在主武器与技能之后 ——
     *       组件之间没有回指依赖（主武器 → 三重打击是单向的），因此顺序不影响装配校验；</li>
     *   <li>基础属性<b>不显式声明</b> ⇒ 与其余角色一致，走框架默认（生命上限 40 / 能量 100 / SanTE 100）；</li>
     *   <li>数值：铁血镇压者 8 物理 + 4~10 特殊值 + 1 能量 + 敌攻 −2（5 秒）；
     *       三重打击 3 层 / 每层 3 真伤 + 3 特殊值 / 窗口 5 秒；影袭 8 格突进（穿墙 ≤2 格）+
     *       r2 圆柱 4 真伤 + 每目标回 4 血；透骨钉 10 物理 + 8 格推动（0.3 秒），
     *       撞墙则 2 秒无法移动 / 致盲 / 沉默，未撞则 2 秒缓慢 IV + 致盲。</li>
     * </ul>
     *
     * <p>★ <b>已知的口径取舍（如实申报）</b>：需求"林不受到特殊值惩罚"与框架内建
     * {@code DefaultSanTEZeroPunishment}（无条件装配）正面冲突，只能在角色侧用"拉回 + 逐刻压制"
     * 缓解，无法 100% 消除归零那一瞬的眩晕 —— 详见 {@link LinMartyrPassive} 类注释。
     */
    private static Role.Builder linBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;
        return withBuiltIns(new Role.Builder("lin"), faction)
                .displayName(Component.text("林"))
                .description(List.of(
                        Component.text("以血为薪，以骨为钉"),
                        Component.text("铁血镇压者：每次攻击造成8点物理与4~10点特殊值伤害，回复1点能量，并使目标攻击力下降2"),
                        Component.text("开普勒装置：每秒回复1点能量；特殊值偏低时以4:1转换为特殊值，能量越高获得越多增益"),
                        Component.text("殉道：特殊值归零时不受到惩罚，但永久扣除4点生命上限并流失15点血量，随后回满至50"),
                        Component.text("三重打击：加强下三次攻击，额外造成真实伤害与特殊值伤害"),
                        Component.text("影袭：瞬间向前跃进8格并撕裂路径上的敌人"),
                        Component.text("透骨钉：发射一条高速白色射线，命中后推动目标，撞墙则将其钉死")
                ))
                .faction(faction)
                .addComponent(LinIronBloodMainWeapon.ID, new LinIronBloodMainWeapon.Specification().setSlot(0))
                .addComponent(LinTripleStrikeSkill.ID, new LinTripleStrikeSkill.Specification().setSlot(1))
                .addComponent(LinShadowRaidSkill.ID, new LinShadowRaidSkill.Specification().setSlot(2))
                .addComponent(LinBoneNailSkill.ID, new LinBoneNailSkill.Specification().setSlot(3))
                .addComponent(LinKeplerPassive.ID, new LinKeplerPassive.Specification())
                .addComponent(LinMartyrPassive.ID, new LinMartyrPassive.Specification())
                .icon(Material.IRON_SWORD);
    }

    /**
     * **绯红渡鸦 · 林晨霜(Raven)**（阵营 {@link Faction#HUNTER}）：散华『改』（弩）+
     * 升空突袭 / 死亡之眼 / 涅斯捷罗夫筋斗 + 浮空机动 / 穿戴装备。
     *
     * <p>装配口径（逐条对应需求；各组件 javadoc 里有更细的行为与口径申报）：
     * <ul>
     *   <li><b>主武器「散华『改』」占 0 号栏</b>（{@code Material.CROSSBOW}）——
     *       插件只把攻击事件投递给主武器组件，而"近战 12 物理 + 3~6 特殊值 / 击退 2 /
     *       右键射击 / 地面丢出换弹 / 空中丢出机动 / 浮空锁定射击"都必须落在这一把武器上；
     *       攻速 100 且 {@code onAttack} 开头自判冷却（近战 CD 0.4 秒）；</li>
     *   <li>三个主动占 1 / 2 / 3 号栏：<b>升空突袭</b>（CD 8 秒，地面形态<b>落地才结算</b>）·
     *       <b>死亡之眼</b>（CD 20 秒）· <b>涅斯捷罗夫筋斗</b>（CD 40 秒），
     *       三者<b>全部 0 耗能</b>（耗能发生在武器装填那一步）；</li>
     *   <li><b>两个被动经 {@code addComponent} 统一入口注册</b>（被动无栏位 ⇒ 不占热键栏）：
     *       <b>浮空机动</b>（空中状态的唯一持有者：悬停 / 瞄准模式 / 机动充能 / 落地冲击 /
     *       <b>actionbar HUD</b>）与 <b>穿戴装备</b>（三件盔甲 + 角色常驻速度 2；★ 能量低于 50 时速度消失、回到 50 立刻恢复）；</li>
     *   <li>★ <b>注册序有语义</b>：浮空机动排在最前 —— 主武器与三个技能都在自己的
     *       {@code start()} 里取它，且技能一的"能否起跳"与技能三的落点预测都读它的几何读数；
     *       "读口第一次被读到之前它已经在表里"才是结构上的事实（同 {@code albert} 的口径）；</li>
     *   <li><b>语音组件「散华语音」最后注册</b>（被动无栏位）：它按需求给了 15 组台词
     *       （空闲 / 命中 / 三个技能 / 三种击杀 / 浮空 / 落地冲击 / 濒死 / 解控 / 换弹 /
     *       无人可锁 / 死亡），全部**加大字号 + 加粗 + 15 种不同浮现风格**。
     *       ★ 注册序有语义：它读「浮空机动」的"击杀是否顺手充了机动"标记，
     *       而那个标记由浮空机动的击杀订阅写入 ⇒ 浮空机动必须排在它前面；</li>
     *   <li>基础属性<b>不显式声明</b> ⇒ 走框架默认（生命上限 40 / 能量 100 / SanTE 100）；</li>
     *   <li>★ <b>阵营 = {@link Faction#HUNTER}</b>（需求未指定 ⇒ 取用户当面确认值，
     *       要改只需动这一行）。</li>
     * </ul>
     *
     * <p>★ <b>已知的口径取舍（如实申报）</b>：
     * <ol>
     *   <li>主武器的<b>近战冷却（0.4 秒）会挡住右键 / 左键 / Q</b> —— 施放管道统一查
     *       {@code isCoolingDown()}，彻底解耦必须改框架（禁改区）。0.4 秒已小于射击间隔
     *       （0.7 秒）⇒ 实际不构成可感损失；</li>
     *   <li><b>"悬停"用关重力 + 读原始输入实现</b>（水平按按键 + 朝向自算、垂直按跳键），
     *       而不是原版飞行 —— 详见 {@link RavenAirMobilityPassive} 的口径申报二 / 三；</li>
     *   <li><b>落地区域标记是"落点射线"的实时预览</b>（与俯冲目标出自同一个函数
     *       {@code RavenSomersaultSkill#resolveLandingPoint}）⇒ "标记与实际不符"这一类 BUG
     *       被结构性消除；只要技能在主手就显示，<b>不看冷却状态</b>。
     *       ★ 落点规则 = 头部射出 25 格射线 → 可穿越方块穿过 / 不可穿越方块需（上方 2 格净空
     *       <b>且</b> 高度 &lt; 跃起后高度 − 3）→ 全不合格则末端垂直下坠取垂足（不得高于自己 6 格）。</li>
     * </ol>
     */
    private static Role.Builder ravenBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;
        return withBuiltIns(new Role.Builder("raven"), faction)
                .displayName(Component.text("绯红渡鸦·林晨霜"))
                .description(List.of(
                        Component.text("SACPD 试验部队总队长——空中机动射手"),
                        Component.text("散华『改』：近战 12 点物理 + 3~6 点特殊值（击退 2）；右键发射全威力弹（10 点物理 + 3~6 点特殊值）"),
                        Component.text("弹匣 6 枚；每 2 秒消耗 3 能量自动回复 1 枚；换弹每发 3 能量；耐久条显示弹药量"),
                        Component.text("地面丢出武器：换弹（每发 0.5 秒 / 3 能量，左键可打断）"),
                        Component.text("浮空机动：空中丢出武器快装 2 发并 0.2 秒内前冲 8 格；上限 3 层，15 秒耗 10 能量充 1 层"),
                        Component.text("浮空机动中武器转为锁定模式：右键蓄力 0.8 秒后轮流锁定 3 名敌人；空中左键切换瞄准模式"),
                        Component.text("0.2 秒内双击空格，或按住蹲 ⇒ 主动解除浮空开始下落（按住蹲越久落得越快）"),
                        Component.text("升空突袭：向斜上方突进 7 格撕裂路径上的敌人（地面形态附带击退）；浮空时改为向前 4 格、向上 2 格并刷新浮空"),
                        Component.text("死亡之眼：跃起并瞬间补满弹药，锁定 20 格内所有敌人齐射"),
                        Component.text("涅斯捷罗夫筋斗：跃起 12 格后高速俯冲；落点由 25 格射线判定（可穿越方块穿过、不可穿越方块需"),
                        Component.text("      上方 2 格净空且高度低于「跃起后高度 − 3」，否则末端垂直下坠取垂足）"),
                        Component.text("      最长持续 10 秒；超时或撞墙则不再尝试突进，就地进行砸地；落地造成范围伤害、眩晕并击飞"),
                        Component.text("落地冲击：空中位移结束且离地 ≥1.5 格时，落地对 r5 内敌人追加伤害、击退并获得速度 6")
                ))
                .faction(faction)
                //被动先行：浮空机动（空中状态持有者）→ 装备（角色特质，依赖图叶子）
                .addComponent(RavenAirMobilityPassive.ID, new RavenAirMobilityPassive.Specification())
                .addComponent(RavenEquipmentsPassive.ID, new RavenEquipmentsPassive.Specification())
                //主武器 + 三个主动（0 / 1 / 2 / 3 号栏）
                .addComponent(RavenRevolverMainWeapon.ID, new RavenRevolverMainWeapon.Specification().setSlot(0))
                .addComponent(RavenSkyStrikeSkill.ID, new RavenSkyStrikeSkill.Specification().setSlot(1))
                .addComponent(RavenDeathEyeSkill.ID, new RavenDeathEyeSkill.Specification().setSlot(2))
                .addComponent(RavenSomersaultSkill.ID, new RavenSomersaultSkill.Specification().setSlot(3))
                //★ 语音组件**最后注册**：它的"击杀分三组"要靠读浮空机动的充能标记，
                //  而那个标记由浮空机动的击杀订阅打上 —— 注册序 = 订阅序 ⇒ 必须先有浮空机动。
                .addComponent(RavenFloatingTextComponent.ID, new RavenFloatingTextComponent.Specification())
                .icon(Material.CROSSBOW);
    }

    /**
     * 「芹泽沙耶香 · 瓦尔基里」（SACPD 试验部队 / 猎影人）——正面持续作战 / 失衡爆发核心 / 能量循环。
     *
     * <p><b>注册顺序有语义</b>：
     * <ol>
     *   <li>笼手被动排最前（它是"失衡爆发"的执行器与 actionbar HUD 的唯一发布者，
     *       且是依赖图的叶子 —— 其余组件都 requires 它）；</li>
     *   <li>主武器 = 0 号栏（影切：拔刀 / 纳刀双形态 + 充能 + 居合）；</li>
     *   <li>技能一 = 1 号栏（影稳态榴弹发射器）；技能二 = 2 号栏（瞬影连斩）；大招 = 3 号栏（剑刃风暴）；</li>
     *   <li>技能二 / 大招 requires 主武器（防御判定窗口 / 风暴接管），但主武器不认识它们 ⇒ 无依赖环；</li>
     *   <li>语音组件排最后（纯叶子）：台词由其余五个组件经 {@code saySpecial / saySkill} 显式喊话。</li>
     * </ol>
     */
    /**
     * 「抗争 · Resist」（SHADOW）——被压迫者的革命领袖。
     *
     * <p>依赖图（无环）：
     * <pre>
     *   ResistStrugglePassive（叶子：战斗时间 / 八档阈值 / HUD 唯一发布者）
     *        ↑ requires
     *   ResistMainWeapon / 三个主动技能
     *   语音与衣物是纯叶子（只依赖内建）。
     * </pre>
     */
    private static Role.Builder resistBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.SHADOW;
        return withBuiltIns(new Role.Builder("resist"), faction)
                .displayName(Component.text("抗争 · Resist"))
                .description(List.of(
                        Component.text("SHADOW 的革命领袖——越战越强，以斗争时间换取八档增益"),
                        Component.text("▓抗争▓【铁剑】：左键击退 3 + 随机白/灰/紫剑气（TNT 音效）"),
                        Component.text("      目标周围 5 格所有敌人：10 物理 + 3~6 特殊值；每次命中战斗时间 +1 秒；CD 0.6 秒"),
                        Component.text("不停歇的斗争：战斗时间上限 999 秒，每 5 秒一档——"),
                        Component.text("      5s 剑气+5 / 10s 抗性 I / 15s 速度 I + 每 2 秒回 1 TE / 20s 生命回复 II"),
                        Component.text("      25s 每 3 秒回 4 生命 / 30s 抗性 II + 每秒减 0.2 秒技能 CD / 35s 剑气再+5 / 40s 每 5 秒 8 点吸收"),
                        Component.text("      10 秒未命中或被攻击 ⇒ 每 0.1 秒退 1 层；每 5 层一枚绕身暴击粒子 + 一档音效"),
                        Component.text("      40 层后身后常驻白灰双层圆环，每 2 秒扩大闪动一次；actionbar 显示战斗时间"),
                        Component.text("谷底的怒吼【哭泣的黑曜石】：末影龙怒吼，解除全部控制（STUN/定身/负面药水），拔出 4 把巨剑"),
                        Component.text("      r6 内 5 物理 + 击退 3；每命中一人回 15 TE、战斗时间 +2 秒；冷却 10 秒"),
                        Component.text("命运的征服【下界石英】：引导 0.5 秒（缓慢 II / 抗性 III / 禁跳 / 禁其它技能与主武器）"),
                        Component.text("      三刀半圆（r8 / r8 / r9）：1.5 / 1.5 / 2.0 倍剑气伤害 + 击退 2 + 击落；命中触发 0.1 秒 FOV 放大"),
                        Component.text("      每命中一人 +3 秒战斗时间；任意一刀命中 ⇒ 收尾速度 III 1 秒；冷却 15 秒"),
                        Component.text("被欺压者的咆哮【重生锚】：引导 0.8 秒拔剑，向前突进 30 格（25 格/秒，撞墙即停）"),
                        Component.text("      途中 r5：8 物理 + 4~6 特殊值（每人一次）；落点 r10：10 物理 + 5~10 特殊值（可叠加）"),
                        Component.text("      每次命中 +3 秒战斗时间；冷却 40 秒"),
                        Component.text("衣物：▓磐山▓ / ▓亡我▓ / ▓重林▓ / ▓阔地▓（各保护 I）"),
                        Component.text("语音：三个技能点名台词 + 空闲/命中/受控/受击/濒死/击杀/死亡的反问句")
                ))
                .faction(faction)
                //被动先行：战斗时间账本 / 八档阈值 / actionbar 唯一发布者（依赖图叶子）
                .addComponent(ResistStrugglePassive.ID, new ResistStrugglePassive.Specification())
                //主武器 + 三个主动（0 / 1 / 2 / 3 号栏）
                .addComponent(ResistMainWeapon.ID, new ResistMainWeapon.Specification().setSlot(0))
                .addComponent(ResistRoarSkill.ID, new ResistRoarSkill.Specification().setSlot(1))
                .addComponent(ResistConquestSkill.ID, new ResistConquestSkill.Specification().setSlot(2))
                .addComponent(ResistRampageSkill.ID, new ResistRampageSkill.Specification().setSlot(3))
                //衣物（纯内建依赖）
                .addComponent(ResistEquipmentsPassive.ID, new ResistEquipmentsPassive.Specification())
                //★ 语音最后注册：纯叶子，台词由其余组件显式喊
                .addComponent(ResistFloatingTextComponent.ID, new ResistFloatingTextComponent.Specification())
                .icon(Material.IRON_SWORD);
    }

    private static Role.Builder sayakaBuilder() {
        //本角色声明阵营的唯一书写点：同一变量传给内建段（阵营组件）与 .faction(...)
        Faction faction = Faction.HUNTER;
        return withBuiltIns(new Role.Builder("sayaka"), faction)
                .displayName(Component.text("芹泽沙耶香·瓦尔基里"))
                .description(List.of(
                        Component.text("SACPD 试验部队——失衡爆发核心 / 能量循环"),
                        Component.text("影稳态失衡：攻击积累失衡（上限 30），积满触发爆发——目标周围 5 格内敌人各 8 真伤、虚弱 1、每秒 1 点 SAN 流失（20 秒）"),
                        Component.text("          爆发期间不再积累；同时为周围 20 格内猎影人每秒回复 1 点 SAN 与 1 点能量"),
                        Component.text("笼手：任意敌人爆发时自身立即回 60 能量 / 1 笼手充能；另有 4 枚追踪粒子飞到身边才各回 2 生命"),
                        Component.text("          充能上限 3、每 30 秒回 1；能量上限 100；actionbar 显示 □/◇/◆ 三槽层数"),
                        Component.text("影切【下界合金剑】：丢出切换「拔刀 / 纳刀」；右键消耗全部笼手充能为其注入充能（每次 5 能量）"),
                        Component.text("拔刀：近战 CD 0.1 秒；8 物理 + 2 真伤 + 2 失衡；充能兑现：+5/7/9 失衡、回 4/6/8 生命、周围 4 格 2/3/4 真伤"),
                        Component.text("纳刀：0.3 秒准备（期间缓慢 4）；纳刀后缓慢 4 并持抗性 I；0.3 秒斜杠蓄力条 + 递增音；蓄满提示「居合准备完毕」"),
                        Component.text("      居合对路径 8 米敌人 5 失衡 + 8 物理；一次居合后 / 切走主武器都会立刻解除纳刀"),
                        Component.text("      充能兑现（防御）：+9/18/30 失衡与 2/3/4 真伤；（主动）：+5/9/15 失衡；3 层防御成功免疫 1 秒"),
                        Component.text("榴弹发射器：30 格/秒抛物线（带后座与落地冲击）、最远 20 格；炸开 5 物理（r5）并生成 5 秒稳态场"),
                        Component.text("      场内敌人每秒 1 真伤 + 2 失衡并缓慢 2（重叠不积累）；自己入场获速度 4 + 再生 1"),
                        Component.text("      存储上限 6、每 10 秒存 1、开局满；射击耗 6 能量、射速 0.8 秒"),
                        Component.text("瞬影连斩：0.15 秒突进斩击 12 物理 + 4 失衡；命中回 6 生命、+1 笼手充能、3 秒内可再放（每段 +1 真伤 / +2 失衡）"),
                        Component.text("      最多连续刷新 4 次（共 5 段）；每次使用后 5 秒防御判定（被击触发居合并锁定敌人方向）；冷却 20 秒"),
                        Component.text("剑刃风暴：三段、每段间隔 1 秒；纳刀且解除移动限制、持续居合判定；瞬移至敌后（无敌人则前移 4 格）"),
                        Component.text("      每段 r8 内 15 物理 + 10 失衡（爆发中敌人额外 5 真伤）；进入范围的敌人被红黑菱形锁定提示"),
                        Component.text("      收尾 12 米黑暗 10 秒；抗性 2、免疫控制；冷却 60 秒"),
                        Component.text("语音：空闲 14 句 / 命中与技能全程台词 / 击杀 9 句 / 状态台词（濒死·能量·失神·死亡）")
                ))
                .faction(faction)
                //被动先行：笼手（失衡爆发执行器 / 充能持有者 / HUD 唯一发布者 —— 依赖图叶子）
                .addComponent(SayakaGauntletPassive.ID, new SayakaGauntletPassive.Specification())
                //主武器 + 三个主动（0 / 1 / 2 / 3 号栏）
                .addComponent(SayakaBladeMainWeapon.ID, new SayakaBladeMainWeapon.Specification().setSlot(0))
                .addComponent(SayakaGrenadeSkill.ID, new SayakaGrenadeSkill.Specification().setSlot(1))
                .addComponent(SayakaFlashSlashSkill.ID, new SayakaFlashSlashSkill.Specification().setSlot(2))
                .addComponent(SayakaBladeStormSkill.ID, new SayakaBladeStormSkill.Specification().setSlot(3))
                //★ 语音组件最后注册：它是纯叶子（只依赖内建），台词全部由其余五个组件显式喊话
                .addComponent(SayakaFloatingTextComponent.ID, new SayakaFloatingTextComponent.Specification())
                .icon(Material.NETHERITE_SWORD);
    }

}
