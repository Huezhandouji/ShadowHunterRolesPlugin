package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
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
        //组件表 = 7 个内建组件 + 5 个角色组件（主武器 + 三个技能 + 一个被动）
        //断言点落在"这 5 个 id 都在"，比断言总数更准（内建组件数量变化不该让本测试变红）
        java.util.Set<String> present = role.getComponents().keySet();
        for (String id : List.of("tekTridentMainWeapon", "tekXiaoSkill", "tekYueSkill",
                "tekTruthThrustSkill", "tekDestinyPassive")) {
            assertTrue("特克缺组件 " + id + "，实际 = " + present, present.contains(id));
        }
        assertEquals("特克应有 5 个角色组件（7 个内建 + 5 = 12 条），实际 = " + present,
                12, role.getComponents().size());
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
