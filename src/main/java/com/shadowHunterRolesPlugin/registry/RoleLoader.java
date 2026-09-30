package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.roleComponent.builtin.*;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluBlueIceRevolverSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluDestinySkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluHysteriaPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluMelodySelectionSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.canglu.CangluTraumaMainWeapon;
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
                new Definition("sinThorn", RoleLoader::sinThornBuilder)
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
                .addComponent(BossbarRoleAttributesDisplayPassive.ID, new BossbarRoleAttributesDisplayPassive.Specification());
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

}
