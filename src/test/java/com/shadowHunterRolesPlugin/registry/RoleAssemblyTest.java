package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.builtin.DefaultSanTEZeroPunishment;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.passive.LinMartyrPassive;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * **装配期总闸门**：把 {@link RoleLoader} 里注册的**每一个**角色都真的装配一遍
 * （{@code builder.get().build()} + {@link Role#verifyDependencies()}），断言没有任何角色被跳过。
 *
 * <h2>为什么必须有这条</h2>
 * {@code RoleLoader#loadInto} 是 <b>fail-fast 且按角色隔离</b>的：某个角色装配失败
 * （缺必需依赖 / 槽位冲突 / 描述符写错）时只记一条 {@code SEVERE} 日志并跳过该角色，
 * <b>不会</b>让插件启动失败。后果是：角色静默消失，只有翻日志才能发现 ——
 * "改了代码、编译过了、测试全绿、进游戏却发现选不到这个角色"正是这样发生的。
 *
 * <p>本测试把那条"只在日志里"的失败搬到 JUnit 里：任何一个角色装不起来 ⇒ 测试变红。
 *
 * <h2>为什么能离线跑</h2>
 * {@code build()} 与 {@code verifyDependencies()} 只读装配期的一张小表
 * （每个组件声明的"提供类型 + 必需类型"），**不建实例、不碰 Bukkit 注册表**
 * （实例化发生在 {@code RoleInstance} 的构造期，本测试不触发）。
 * 与 {@code ComponentCycleAllowedTest} 同一做法。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li>本测试**不**覆盖组件构造期注入（{@code create(...)} 里的副作用）——那要真服务集；</li>
 *   <li>本测试**不**覆盖运行期行为（{@code start()} 取组件、{@code update()} 节拍），
 *       那些归各组件的专属单测与实测。</li>
 * </ul>
 */
public class RoleAssemblyTest {

    /**
     * **离线测试基设：先装一个最小 {@code KeyFactory} 桩**（★ 改前必读，这是本类最容易踩的一处）。
     *
     * <p>本测试会**加载所有角色组件类**，其中若干类带**静态**常量
     * {@code static final NamespacedKey X = KeyFactory.Registry.of(...)}
     * （例：{@code TekTridentMainWeapon#ATTACK_SPEED_KEY}、{@code RedEvolutionPassive#HEALTH_BONUS_KEY}）。
     * 而 {@code KeyFactory.Registry.of} 在**没人 install 过** 时抛
     * {@code IllegalStateException("KeyFactory is not installed yet (onEnable did not finish)")}
     * —— 生产里 {@code onEnable} 先 install 所以没事，**离线测试 JVM 里没人装**。
     *
     * <p>⇒ 类初始化抛 {@code ExceptionInInitializerError}；更糟的是 **JVM 会缓存这次失败**：
     * 之后任何触碰该类的代码都直接 {@code NoClassDefFoundError}，**即使后来补装了桩也救不回来**。
     * 症状因此是"某个测试类整片变红，报错却指向另一个看起来无关的类"，而且**随执行顺序变化**。
     *
     * <p>⇒ 本类**自己先装桩**（与 {@code CapabilityDispatchTest} / {@code BuffComponentOperationTest}
     * 同做法：{@code new NamespacedKey("shadowhunterroles", key)}，离线可构造）。
     * 该 install 是**全局静态副作用**（如实申报），但它幂等 ⇒ 谁先跑都一样，
     * 本测试不再依赖"别的测试类恰好先跑"。
     */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    /** 用真实的 {@link RoleLoader}（logger 只用来接装配失败的日志，本测试里不该有输出）。 */
    private static RoleLoader loader() {
        return new RoleLoader(Logger.getLogger("RoleAssemblyTest"));
    }

    /**
     * **每个角色都必须装配通过**（等价于"进游戏后 {@code /role set} 能看到它"）。
     *
     * <p>失败时把角色 id 与缺依赖清单一起报出来，便于直接定位。
     */
    @Test
    public void everyRegisteredRoleAssemblesAndVerifies() {
        List<String> failures = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            String id = definition.id();
            ids.add(id);
            try {
                Role role = definition.builder().get().build();
                role.verifyDependencies();
                List<String> missing = role.missingRequiredDependencies();
                if (!missing.isEmpty()) {
                    failures.add(id + " ⇒ 缺必需依赖: " + missing);
                }
            } catch (Throwable failure) {
                failures.add(id + " ⇒ " + failure.getClass().getSimpleName() + ": " + failure.getMessage());
            }
        }

        assertTrue("有角色装配失败（后果 = 进游戏后选不到该角色，只在日志里留一条 SEVERE）:\n"
                + String.join("\n", failures), failures.isEmpty());
        assertTrue("角色定义表不该为空", !ids.isEmpty());
    }

    /**
     * **林(Lin) 的默认特殊值惩罚必须关闭，只保留自己的「殉道」**（2026-10-31 需求）。
     *
     * <p>为什么需要单独钉：默认惩罚是 {@code withBuiltIns} 默认注册的；如果哪天有人把
     * {@code linBuilder} 的配置参数改回 {@code withBuiltIns(builder, faction)}，林会重新吃到
     * "归零眩晕 + 真伤"的默认惩罚 —— 编译与装配都不会报错，只有玩法不对。
     */
    @Test
    public void linDisablesDefaultPunishmentAndKeepsMartyr() {
        RoleLoader.Definition lin = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("lin".equals(definition.id())) {
                lin = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"lin\" 的角色", lin != null);
        Role role = lin.builder().get().build();
        role.verifyDependencies();
        java.util.Set<String> present = role.getComponents().keySet();
        assertFalse("林的默认特殊值惩罚必须关闭（punishmentEnabled=false），实际 = " + present,
                present.contains(DefaultSanTEZeroPunishment.ID));
        assertTrue("林自己的殉道惩罚必须仍在，实际 = " + present,
                present.contains(LinMartyrPassive.ID));
        //2026-10-31 并行需求「殉道者的遗装」给林补了第 7 个角色组件（lin_equipments_passive）
        assertTrue("林的殉道者遗装必须仍在，实际 = " + present,
                present.contains("lin_equipments_passive"));
        //2026-10-31 全员语音：林补了 `role_voice_script`（第 8 个角色组件）
        assertTrue("林的语音组件必须仍在，实际 = " + present,
                present.contains("role_voice_script"));
        int builtIns = builtInComponentCount();
        //总组件 = 内建(10) − 默认惩罚(1) + 8 个角色组件（主武器 / 3 技能 / 2 被动 / 遗装 / 语音）
        assertEquals("林 = 内建 " + builtIns + " − 默认惩罚 1 + 8 个角色组件；实际 = " + present,
                builtIns - 1 + 8, role.getComponents().size());
    }

    /**
     * **特克(Tek) 必须真的在注册表里，且五个组件齐全**。
     * <p>单独钉住以防"忘了在 {@code defaultDefinitions()} 加一行"——
     * 那种漏写在其它测试里没有任何表现。
     */
    @Test
    public void tekIsRegisteredWithFiveComponents() {
        RoleLoader.Definition tek = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("tek".equals(definition.id())) {
                tek = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"tek\" 的角色（漏了 ⇒ 进游戏选不到特克）",
                tek != null);

        Role role = tek.builder().get().build();
        role.verifyDependencies();

        assertTrue("特克缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        //组件表 = 内建组件 + 6 个角色组件（主武器 + 三个技能 + 一个被动 + 语音）
        //断言点落在"这 6 个 id 都在"，比断言总数更准（内建组件数量变化不该让本测试变红）
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("tekTridentMainWeapon", "tekXiaoSkill", "tekYueSkill",
                "tekTruthThrustSkill", "tekDestinyPassive", "role_voice_script")) {
            assertTrue("特克缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        //★ 内建条数**从注册表推导**，不写死：上游 2026-10-02 把内建从 7 个加到 9 个，
        //  写死的总数让本测试误报过一次 —— 而"框架有几个内建"不是本测试要管的事。
        int builtIns = builtInComponentCount();
        //2026-10-31 全员语音：特克补了 `role_voice_script`（方法名沿用历史，实际为 6 个角色组件）
        assertEquals("特克应有 6 个角色组件（= 内建 " + builtIns + " + 6）；实际 = " + present,
                builtIns + 6, role.getComponents().size());
    }

    /**
     * **绯红渡鸦(Raven) 必须真的在注册表里，且十个组件齐全**（含语音组件）。
     *
     * <p>与 {@link #tekIsRegisteredWithFiveComponents()} 同一理由：单独钉住以防
     * "忘了在 {@code defaultDefinitions()} 加一行"—— 那种漏写在其它测试里没有任何表现。
     * <p>★ 这一条对本角色尤其要紧：它的主武器与两个技能都依赖「浮空机动」被动，
     * 而依赖声明写错（或被动漏注册）的后果是**整个角色静默消失**（只在启动日志留一条 SEVERE）。
     */
    @Test
    public void ravenIsRegisteredWithSevenComponents() {
        RoleLoader.Definition raven = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("raven".equals(definition.id())) {
                raven = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"raven\" 的角色（漏了 ⇒ 进游戏选不到渡鸦）",
                raven != null);

        Role role = raven.builder().get().build();
        role.verifyDependencies();

        assertTrue("渡鸦缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        //组件表 = 内建组件 + 7 个角色组件（主武器 + 三个技能 + 三个被动，其中一个是语音）
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("raven_revolver_main_weapon", "raven_sky_strike_skill",
                "raven_death_eye_skill", "raven_somersault_skill",
                "raven_air_mobility_passive", "raven_equipments_passive",
                "raven_floating_text")) {
            assertTrue("渡鸦缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        int builtIns = builtInComponentCount();
        assertEquals("渡鸦应有 7 个角色组件（= 内建 " + builtIns + " + 7）；实际 = " + present,
                builtIns + 7, role.getComponents().size());
    }

    /**
     * **内建组件的条数**（从注册表推导，不写死）。
     *
     * <p>为什么用 {@code selfUpdateExample} 当基准：这个框架示例角色的组件表 =
     * **内建组件 + 它自己的 1 个示例组件**（{@code ExampleSelfRefreshingSkill}），
     * 因此"内建数 = 它的组件数 − 1"。
     * <p>★ 这样上游**新增 / 删除内建组件**时，本测试不会误报；而**某个角色的组件丢了**仍然会红。
     */
    private static int builtInComponentCount() {
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("selfUpdateExample".equals(definition.id())) {
                return definition.builder().get().build().getComponents().size() - 1;
            }
        }
        throw new AssertionError("注册表里必须有 selfUpdateExample（框架示例角色）");
    }

    /**
     * **芹泽沙耶香(Sayaka) 必须真的在注册表里，且六个组件齐全**（含语音组件）。
     *
     * <p>与 {@link #tekIsRegisteredWithFiveComponents()} / {@link #ravenIsRegisteredWithSevenComponents()}
     * 同一理由：单独钉住以防"忘了在 {@code defaultDefinitions()} 加一行"。
     * <p>★ 对本角色尤其要紧：技能二与大招都依赖主武器（防御判定窗口 / 风暴接管），
     * 主武器依赖笼手被动，全部五个作战组件又都依赖语音组件（喊话入口） ——
     * 任一依赖声明写错（或组件漏注册）的后果是**整个角色静默消失**。
     */
    @Test
    public void sayakaIsRegisteredWithSixComponents() {
        RoleLoader.Definition sayaka = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("sayaka".equals(definition.id())) {
                sayaka = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"sayaka\" 的角色（漏了 ⇒ 进游戏选不到芹泽沙耶香）",
                sayaka != null);

        Role role = sayaka.builder().get().build();
        role.verifyDependencies();

        assertTrue("芹泽沙耶香缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        //组件表 = 内建组件 + 6 个角色组件（主武器 + 三个技能 + 笼手被动 + 语音）
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("sayaka_gauntlet_passive", "sayaka_blade_main_weapon",
                "sayaka_grenade_skill", "sayaka_flash_slash_skill", "sayaka_blade_storm_skill",
                "sayaka_floating_text")) {
            assertTrue("芹泽沙耶香缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        int builtIns = builtInComponentCount();
        assertEquals("芹泽沙耶香应有 6 个角色组件（= 内建 " + builtIns + " + 6）；实际 = " + present,
                builtIns + 6, role.getComponents().size());
    }

    /**
     * **抗争(Resist) 必须真的在注册表里，且十个组件齐全**。
     *
     * <p>与 sayaka 的钉住测试同一理由：{@code defaultDefinitions()} 漏一行 ⇒ 进游戏选不到角色，
     * 而测试若只扫"已有角色"则永远绿。抗争还多一层风险：主武器与三个技能都依赖被动，
     * 衣物 / 语音是叶子 —— 依赖写错会**整个角色静默消失**。
     */
    @Test
    public void resistIsRegisteredWithSevenComponents() {
        RoleLoader.Definition resist = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("resist".equals(definition.id())) {
                resist = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"resist\" 的角色（漏了 ⇒ 进游戏选不到抗争）",
                resist != null);

        Role role = resist.builder().get().build();
        role.verifyDependencies();

        assertTrue("抗争缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        //组件表 = 内建组件 + 7 个角色组件（被动 + 主武器 + 三技能 + 衣物 + 语音）
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("resist_struggle_passive", "resist_main_weapon",
                "resist_roar_skill", "resist_conquest_skill", "resist_rampage_skill",
                "resist_equipments_passive", "resist_floating_text")) {
            assertTrue("抗争缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        int builtIns = builtInComponentCount();
        assertEquals("抗争应有 7 个角色组件（= 内建 " + builtIns + " + 7）；实际 = " + present,
                builtIns + 7, role.getComponents().size());
    }

    /**
     * **梅妻鹤子(Mikomako) 必须真的在注册表里，且十个组件齐全**（2026-10-21 重做）。
     *
     * <p>本次重做把"充能账本"和"离手武器"各自拆成独立组件：账本被主武器 + 三技能 + 被动共五处读，
     * 离手状态被主武器闸门读。任何一处漏注册，失败形态都是**整个角色静默消失**（只在启动日志留 SEVERE），
     * 因此用本条把九个 id 逐个钉住。
     */
    @Test
    public void mikomakoIsRegisteredWithTenComponents() {        RoleLoader.Definition Mikomako = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("Mikomako".equals(definition.id())) {
                Mikomako = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"Mikomako\" 的角色（漏了 ⇒ 进游戏选不到梅妻鹤子）",
                Mikomako != null);

        Role role = Mikomako.builder().get().build();
        role.verifyDependencies();

        assertTrue("梅妻鹤子缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("mikomako_charge", "mikomako_throw", "mikomako_san_drain",
                "mikomako_mainWeapon_juejue", "mikomako_skill_unconcern",
                "mikomako_skill_bloody_slash", "mikomako_skill_circle_slash",
                "mikomako_equippments_passive", "mikomako_madness_passive",
                "mikomako_floating_text")) {
            assertTrue("梅妻鹤子缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        int builtIns = builtInComponentCount();
        assertFalse("鹤子的默认特殊值惩罚必须关闭（punishmentEnabled=false），实际 = " + present,
                present.contains(DefaultSanTEZeroPunishment.ID));
        assertEquals("梅妻鹤子应有 10 个角色组件（= 内建 " + builtIns
                        + " − 默认惩罚 1 + 10）；实际 = " + present,
                builtIns - 1 + 10, role.getComponents().size());
    }

    /**
     * **冷识(lethe) 必须真的在注册表里，且十个组件齐全**（2026-10-26 改名后重钉）。
     *
     * <p>冷识的十个 id 由组件自己声明、由 {@code RoleLoader.letheBuilder()} 装配；
     * 漏注册任何一件，失败形态都是**整个角色静默消失**（只在启动日志留 SEVERE），
     * 因此用本条把十个 id 逐个钉住（与 {@link #mikomakoIsRegisteredWithTenComponents()} 同一理由）。
     */
    @Test
    public void letheIsRegisteredWithTenComponents() {
        RoleLoader.Definition lethe = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("lethe".equals(definition.id())) {
                lethe = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"lethe\" 的角色（漏了 ⇒ 进游戏选不到冷识）",
                lethe != null);

        Role role = lethe.builder().get().build();
        role.verifyDependencies();

        assertTrue("冷识缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("lethe_resonance_component", "lethe_evolution_passive",
                "lethe_quiver_component", "lethe_main_weapon", "lethe_forget_skill",
                "lethe_summon_skill", "lethe_reshape_skill", "lethe_shadows_passive",
                "lethe_equipments_passive", "lethe_floating_text")) {
            assertTrue("冷识缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        int builtIns = builtInComponentCount();
        assertEquals("冷识应有 10 个角色组件（= 内建 " + builtIns + " + 10）；实际 = " + present,
                builtIns + 10, role.getComponents().size());
    }

    /**
     * **红(red) 必须真的在注册表里，且八个组件齐全**（2026-10-21 重做）。
     *
     * <p>本次重做把"流血 / 饮血 / 何足挂齿"合成一个账本组件，并新增语音组件；
     * 漏注册任何一个，失败形态都是**整个角色静默消失**（只在启动日志留 SEVERE）。
     */
    @Test
    public void redIsRegisteredWithEightComponents() {
        RoleLoader.Definition red = null;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            if ("red".equals(definition.id())) {
                red = definition;
                break;
            }
        }
        assertTrue("defaultDefinitions() 里必须有 id = \"red\" 的角色（漏了 ⇒ 进游戏选不到红）",
                red != null);

        Role role = red.builder().get().build();
        role.verifyDependencies();

        assertTrue("红缺必需依赖: " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("red_bleed_passive", "red_evolution_passive",
                "red_sanctified_blade_main_weapon", "red_deeply_sorrow_skill",
                "red_evil_shock_skill", "red_solitary_arrogance_skill",
                "red_equipments_passive", "red_floating_text")) {
            assertTrue("红缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        int builtIns = builtInComponentCount();
        assertEquals("红应有 8 个角色组件（= 内建 " + builtIns + " + 8）；实际 = " + present,
                builtIns + 8, role.getComponents().size());
    }

    /**
     * **角色 id 不重复**（重复会让 {@code /role set} 只认到其中一个）。
     */
    @Test
    public void roleIdsAreUnique() {
        List<String> seen = new ArrayList<>();
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            assertTrue("角色 id 重复: " + definition.id(), !seen.contains(definition.id()));
            seen.add(definition.id());
        }
    }
}
