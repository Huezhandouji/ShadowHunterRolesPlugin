package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.core.ports.ComponentLookup;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 阶段 11 · t77（α）：**按 id 的查询语义**入库覆盖（F-5）。
 *
 * <p>覆盖 {@link ComponentLookup} 的四个读口在同一容器上的**成对对称性**：
 * <ul>
 *   <li>按类型：{@code get(Type)} = 添加顺序**第一个**可赋值者 · {@code getAll(Type)} = **全部**（添加顺序）；</li>
 *   <li>按 id：{@code getById(id)} = 添加顺序**第一个**同 id 者 · {@code getAllById(id)} = **全部**同 id 者。</li>
 * </ul>
 * 不变量（逐条断言）：{@code getAllById(id).get(0) == getById(id)} ·
 * {@code getAll(type).get(0) == get(type)} · 无人符合 ⇒ **空列表**（不是 null）· 返回**不可变**列表 ·
 * {@code id == null} ⇒ 空列表（不抛）。
 *
 * <p><b>为什么能离线跑</b>：容器（{@code ComponentRegistry} + {@code ComponentLookupImpl}）按设计
 * **不碰 Bukkit** ⇒ 用全 null 的 {@link ComponentServices} 桩即可驱动（同一做法已在仓库外探针
 * `.scratch/t67/T67Probe.java` 上验证过）。本件因此**不需要起服**、也不需要 mocking 框架。
 *
 * <p><b>本件不覆盖</b>（如实申报，属 β 卡）：F-1/F-2/F-3 三条**状态面按实例**的反例 —— 它们需要
 * {@code RoleInstance}（进而需要 {@code Player}）⇒ 只能走运行级读数。
 */
public class ComponentLookupByIdTest {

    // ───────────── 组件族（Father -> C1 / C2，接口 Tag） ─────────────

    /** 纯接口（**不**继承 {@code RoleComponent}）—— 用来验证"接口查询命中实现类实例"。 */
    public interface Tag {
    }

    public static abstract class Father extends RoleComponent {
        Father(String id, ComponentServices services) {
            super(id, services);
        }
    }

    public static final class C1 extends Father implements Tag {
        C1(String id, ComponentServices services) {
            super(id, services);
        }
    }

    public static final class C2 extends Father {
        C2(String id, ComponentServices services) {
            super(id, services);
        }
    }

    // ───────────── 描述符（providedType 由泛型实参推导） ─────────────

    static class Spec<T extends RoleComponent> extends RoleComponent.Specification<T> {
        private final BiFunction<String, ComponentServices, T> factory;

        Spec(String label, BiFunction<String, ComponentServices, T> factory) {
            super(label);
            this.factory = factory;
        }

        @Override
        public T create(String id, ComponentServices services) {
            return factory.apply(id, services);
        }
    }

    static final class C1Spec extends Spec<C1> {
        C1Spec() { super("T77C1", C1::new); }
    }

    static final class C2Spec extends Spec<C2> {
        C2Spec() { super("T77C2", C2::new); }
    }

    /** 全 null 的服务集桩：容器读口不需要服务集里的任何成员（组件也不会调用它们）。 */
    private static final ComponentServices STUB =
            new ComponentServices(null, null, null);

    private static ComponentLookupImpl lookupOf(ComponentRegistry registry) {
        return new ComponentLookupImpl(registry, id -> STUB, Logger.getLogger("t77-test"));
    }

    private static List<String> idsOf(List<?> components) {
        List<String> out = new ArrayList<>();
        for (Object o : components) {
            out.add(o instanceof RoleComponent component ? component.getId() : "?");
        }
        return out;
    }

    // ───────────── A1：getById = 第一个 · getAllById = 全部（添加顺序） ─────────────

    @Test
    public void getByIdReturnsFirstAndGetAllByIdReturnsAllInAddOrder() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        C1 first = port.add("dup", new C1Spec());
        C1 second = port.add("dup", new C1Spec());
        C1 third = port.add("dup", new C1Spec());
        port.add("other", new C2Spec());
        registry.freeze();

        assertSame("getById 必须返回**添加顺序第一个**同 id 者", first, port.getById("dup"));
        List<RoleComponent> all = port.getAllById("dup");
        assertEquals("getAllById 必须返回全部同 id 者", 3, all.size());
        assertSame("getAllById 的顺序 = 添加顺序 [0]", first, all.get(0));
        assertSame("getAllById 的顺序 = 添加顺序 [1]", second, all.get(1));
        assertSame("getAllById 的顺序 = 添加顺序 [2]", third, all.get(2));
        assertEquals("同 id 列表的 id 逐项都相等", List.of("dup", "dup", "dup"), idsOf(all));
    }

    @Test
    public void byIdPairIsSymmetric() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        port.add("x", new C1Spec());
        registry.freeze();

        assertEquals("无人符合时 getAllById 必须给出**空列表**（不是 null）", List.of(), port.getAllById("nope"));
        assertNull("同一条件下 getById 必须给出 null", port.getById("nope"));
        assertTrue("isEmpty() ⟺ getById == null（两侧同口径）",
                port.getAllById("x").isEmpty() == (port.getById("x") == null));
        assertSame("getAllById(id).get(0) 恒等于 getById(id)",
                port.getById("x"), port.getAllById("x").get(0));
    }

    @Test
    public void getAllByIdSingleItemBoundary() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        C1 only = port.add("solo", new C1Spec());
        port.add("other", new C2Spec());
        registry.freeze();

        List<RoleComponent> hits = port.getAllById("solo");
        assertEquals("单项边界：列表长度 = 1", 1, hits.size());
        assertSame("单项边界：元素即那个组件", only, hits.get(0));
    }

    @Test
    public void getAllByIdHandlesNullIdWithoutThrowing() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        port.add("a", new C1Spec());
        registry.freeze();

        assertEquals("id == null ⇒ 空列表（与 getById(null) == null 同口径：都不抛）",
                List.of(), port.getAllById(null));
        assertNull("getById(null) 仍是 null", port.getById(null));
    }

    @Test
    public void getAllByIdReturnsImmutableList() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        C1 one = port.add("a", new C1Spec());
        registry.freeze();

        List<RoleComponent> hits = port.getAllById("a");
        try {
            hits.add(one);
            fail("getAllById 必须返回**不可变**列表（写入应抛 UnsupportedOperationException）");
        } catch (UnsupportedOperationException expected) {
            // 期望路径
        }
        assertEquals("不可变失败后列表内容不变", 1, hits.size());
    }

    @Test
    public void getAllByIdFollowsInsertAtOrder() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        C1 appended = port.add("dup", new C1Spec());
        C1 inserted = port.insertAt(0, "dup", new C1Spec());
        C1 tail = port.add("dup", new C1Spec());
        registry.freeze();

        List<RoleComponent> hits = port.getAllById("dup");
        assertSame("插位后的顺序 = 容器当前序（插位者在下标 0）", inserted, hits.get(0));
        assertSame("其后是原来的追加序 [1]", appended, hits.get(1));
        assertSame("最后是后来的追加者", tail, hits.get(2));
        assertSame("getById 与 getAllById[0] 同目标（插位后亦然）", inserted, port.getById("dup"));
    }

    // ───────────── 与按类型的一对做对称性对拍 ─────────────

    @Test
    public void byTypePairIsSymmetric() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        C1 c1 = port.add("c1", new C1Spec());
        C2 c2 = port.add("c2", new C2Spec());
        registry.freeze();

        assertSame("get(父类) = 添加顺序第一个（先加的 C1）", c1, port.get(Father.class));
        assertSame("get(纯接口) = 命中实现类实例", c1, port.get(Tag.class));
        assertEquals("getAll(父类) 返回两个", 2, port.getAll(Father.class).size());
        assertSame("getAll(父类)[0] == get(父类)", c1, port.getAll(Father.class).get(0));
        assertSame("getAll(父类)[1] = 后加的 C2", c2, port.getAll(Father.class).get(1));
        assertEquals("getAll(接口) 只命中实现者", List.of("c1"), idsOf(port.getAll(Tag.class)));
        assertEquals("无人符合 ⇒ 空列表（按类型侧，用另一个族验证）",
                List.of(), idsOf(port.getAll(Single.class)));
        assertNull("无人符合 ⇒ get 为 null（按类型侧，同一个族）", port.get(Single.class));
    }

    /** 只用来验证"无人符合"的另一个族（避免与 C1/C2 的继承关系混淆）。 */
    public static final class Single extends RoleComponent {
        Single(String id, ComponentServices services) {
            super(id, services);
        }
    }

    @Test
    public void getAllIsImmutableAndEmptyForUnknownType() {
        ComponentRegistry registry = new ComponentRegistry();
        ComponentLookup port = lookupOf(registry);
        C1 c1 = port.add("c1", new C1Spec());
        registry.freeze();

        List<Father> all = port.getAll(Father.class);
        assertEquals("单项边界（按类型侧）", 1, all.size());
        assertSame("元素即那个组件", c1, all.get(0));
        assertFalse("空结果不是 null 而是空列表", port.getAll(Single.class) == null);
        try {
            all.add(c1);
            fail("getAll 必须返回不可变列表");
        } catch (UnsupportedOperationException expected) {
            // 期望路径
        }
    }
}
