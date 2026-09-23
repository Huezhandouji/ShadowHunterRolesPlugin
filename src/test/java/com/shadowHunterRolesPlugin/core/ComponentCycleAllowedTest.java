package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ComponentDependencyException;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 10 · t68：**允许组件环形依赖**（用户新路线图第三条）的装配期判据。
 *
 * <p><b>本卡是一次口径反转</b>：{@code t54} 依据当时的裁定 Q2「禁止依赖循环」，把环检测实现成
 * {@link Role#verifyDependencies()} 里的硬失败。新路线图第三条**改为允许环形依赖** ⇒ 环分支已删除。
 * 本测试把**新的边界**钉住 —— 只放开环，**不动**另一半。
 *
 * <p><b>为什么能单测</b>：{@code verifyDependencies()} 只读装配期的一张小表（每个组件的
 * "提供类型 + 必需类型"），**不建实例、不碰 Bukkit 注册表**（实例化发生在 {@code RoleInstance} 的构造期）
 * ⇒ 离线可跑。下面两个夹具组件的 {@code create(...)} **永不被调用**，正是这一点让本测试不需要任何
 * 运行期依赖。
 *
 * <p><b>判据（逐条对应卡面 A1/A2/A3）</b>：
 * <ol>
 *   <li>A1 互环（A↔B 互相 {@code requires}）⇒ {@code verifyDependencies()} **不抛**；</li>
 *   <li>A3 自环（A 依赖自己）⇒ **仍抛**（A 不算自己的提供者 ⇒ 落成"缺必需依赖"，**不是**环检测）；</li>
 *   <li>A2 缺必需依赖（B 需要 C，而 C 不在表里）⇒ **仍抛**，且消息点名缺的类型；</li>
 *   <li>对照：B 的依赖被满足 ⇒ 不抛（证明上一条不是"永远抛"）。</li>
 * </ol>
 * <p>第 2、3 条是**边界**：它们保证"删掉环检测"**没有**顺手放松缺依赖的硬失败。
 */
public class ComponentCycleAllowedTest {

    // ───────────── 测试夹具：两个互相依赖的组件类型 ─────────────
    //
    // 必须是**具体类**：`providedType()` 由描述符的泛型实参推导（`RoleComponent.Specification<T>`），
    // 而产品里三个家族描述符的泛型实参是**家族基类**（族级提供类型）⇒ 用产品组件**声明不出**
    // 按具体类的环。夹具参数化到具体类，正好给出"能声明出环"的最小形态。

    /** 夹具组件 A：提供 {@code CycleComponentA}。 */
    public static final class CycleComponentA extends RoleComponent {
        public CycleComponentA(String id, ComponentServices services) {
            super(id, services);
        }
    }

    /** 夹具组件 B：提供 {@code CycleComponentB}。 */
    public static final class CycleComponentB extends RoleComponent {
        public CycleComponentB(String id, ComponentServices services) {
            super(id, services);
        }
    }

    /** A 的描述符：声明**必需** {@code CycleComponentB}。 */
    public static final class SpecA extends RoleComponent.Specification<CycleComponentA> {
        public SpecA(boolean requireB) {
            super("CycleA");
            if (requireB) requires(CycleComponentB.class);
        }

        @Override
        public CycleComponentA create(String id, ComponentServices services) {
            throw new AssertionError("assembly-time check must not create instances (A)");
        }
    }

    /** B 的描述符：声明**必需** {@code CycleComponentA}。 */
    public static final class SpecB extends RoleComponent.Specification<CycleComponentB> {
        public SpecB(boolean requireA) {
            super("CycleB");
            if (requireA) requires(CycleComponentA.class);
        }

        @Override
        public CycleComponentB create(String id, ComponentServices services) {
            throw new AssertionError("assembly-time check must not create instances (B)");
        }
    }

    /** 只有 A、A 又要求 B ⇒ 缺依赖（B 不在表里）。 */
    private static Role onlyARequiringB() {
        return new Role.Builder("r_cycle_missing").addComponent("c_a", new SpecA(true)).build();
    }

    // ───────────── A1：互环必须装配通过（本卡的核心正向读数）─────────────

    /**
     * **A1 互环**：A 的必需类型由 B 提供、B 的必需类型由 A 提供 ⇒ **不得抛**。
     * <p>本卡之前这里会抛 {@code ComponentDependencyException: … dependency cycle: c_a -> c_b -> c_a}。
     */
    @Test
    public void mutualCycleAssemblesSuccessfully() {
        Role role = new Role.Builder("r_cycle_mutual")
                .addComponent("c_a", new SpecA(true))
                .addComponent("c_b", new SpecB(true))
                .build();

        // 不抛 = 正向读数；再显式读一次诊断清单，证明它是"空"而不是"没跑到"
        role.verifyDependencies();
        assertTrue("互环的缺依赖清单必须为空，实际 = " + role.missingRequiredDependencies(),
                role.missingRequiredDependencies().isEmpty());

        // 两个组件确实都进了表（不是"因为没装配所以没检查"）
        assertEquals(2, role.getComponents().size());
        assertTrue(role.getComponents().containsKey("c_a"));
        assertTrue(role.getComponents().containsKey("c_b"));
    }

    // ───────────── A3：自环仍硬失败（不是环检测，是"自己不算提供者"）─────────────

    /**
     * **A3 自环**：A 声明必需 A 自己 ⇒ **仍抛**，且形态是**缺必需依赖**（点名 A 自己）。
     * <p>为什么这不算"环检测残留"：A **不算自己的提供者**是**匹配规则**的一部分
     * （用户原话"检查自己需要的依赖（**其他组件**）"）⇒ 它落成一条可读的缺依赖错误，
     * 而不是一个能被自己满足的假通过。删掉环检测**没有**放松这一条。
     */
    @Test
    public void selfCycleStillFailsAsMissingDependency() {
        Role role = new Role.Builder("r_cycle_self")
                .addComponent("c_a", new SpecA(false).requires(CycleComponentA.class))
                .build();

        ComponentDependencyException e = assertThrows(ComponentDependencyException.class, role::verifyDependencies);
        assertTrue("消息必须点名组件 id，实际 = " + e.getMessage(), e.getMessage().contains("'c_a'"));
        assertTrue("消息必须点名缺的类型，实际 = " + e.getMessage(),
                e.getMessage().contains(CycleComponentA.class.getName()));
        assertTrue("消息必须仍是'缺必需依赖'的形态（不得出现环字样），实际 = " + e.getMessage(),
                e.getMessage().contains("requires missing component type"));
        assertEquals("自环必须恰好报一条，实际 = " + role.missingRequiredDependencies(),
                1, role.missingRequiredDependencies().size());
    }

    // ───────────── A2：缺必需依赖仍硬失败（t54 的另一半，原样保留）─────────────

    /**
     * **A2 缺必需依赖**：只放了一个 A，而 A 要求 B（B 不在表里）⇒ **仍抛**，消息点名 B。
     * <p>这是本卡"只放开环、不动另一半"的**直接判据**。
     */
    @Test
    public void missingRequiredDependencyStillFails() {
        Role role = onlyARequiringB();

        ComponentDependencyException e = assertThrows(ComponentDependencyException.class, role::verifyDependencies);
        assertTrue("消息必须点名缺的类型，实际 = " + e.getMessage(),
                e.getMessage().contains(CycleComponentB.class.getName()));
        assertTrue("消息必须点名角色 id，实际 = " + e.getMessage(), e.getMessage().contains("'r_cycle_missing'"));
        assertTrue("消息必须点名组件 id，实际 = " + e.getMessage(), e.getMessage().contains("'c_a'"));
        assertEquals(1, role.missingRequiredDependencies().size());
    }

    /**
     * **对照（证明上一条不是"永远抛"）**：同一份 A，只要把 B 放进来 ⇒ **不抛**。
     * <p>没有这条，{@code missingRequiredDependencyStillFails} 可能只是因为"这个方法总抛"而通过。
     */
    @Test
    public void sameRoleSucceedsOnceTheDependencyIsPresent() {
        Role role = new Role.Builder("r_cycle_missing")
                .addComponent("c_a", new SpecA(true))
                .addComponent("c_b", new SpecB(false))
                .build();

        role.verifyDependencies();
        assertTrue(role.missingRequiredDependencies().isEmpty());
    }

    // ───────────── 边界：环检测的公共入口已不存在（防"留着死代码"）─────────────

    /**
     * **A1 的另一半证据**：{@code dependencyCycles()} 这个公共入口**已从 {@link Role} 移除**。
     * <p>为什么单独钉：留一个不再被调用的环检测，会让下一个读者以为"环仍被禁止"（死代码会撒谎）。
     * 本测试用反射确认它**真的不在了** —— 这条会随"有人手滑把它加回来"而变红。
     */
    @Test
    public void cycleDetectorEntryPointIsGone() {
        boolean present = false;
        for (java.lang.reflect.Method m : Role.class.getMethods()) {
            if (m.getName().equals("dependencyCycles")) present = true;
        }
        assertTrue("Role.dependencyCycles() 应已随 t68 删除（环已允许，留着它只会误导读者）", !present);
    }
}
