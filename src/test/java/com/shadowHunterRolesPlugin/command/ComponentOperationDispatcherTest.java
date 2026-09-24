package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 13 · t127：**组件操作面 · 指令面**的离线单测 —— 只测派发器里那条**纯函数**
 * {@link ComponentOperationDispatcher#resolve(List, String)}（定位 + `#index` 解析 + 回绝分类 ✓）。
 *
 * <p><b>判据来源</b>：设计定案 §4④（命中 0 份 ⇒ 回绝 + 列出可用 id；**多份且无 `#index` ⇒ 回绝并要求 index** ✗）
 * · §6.3/§6.4（两类回绝 ✓）· §7.4 ②（`#index` 写在 id 字符串里 ✓）· 用户裁定（**0 基** ✓）。
 *
 * <p><b>为什么能离线跑</b>：`resolve` 是**静态纯函数**（只吃一份组件列表 ✓，不碰 Bukkit、不读注册表、无副作用 ✓）
 * ⇒ 用桩件就能驱动 ✓（与 `CapabilityDispatchTest` / `ComponentOperationDispatchTest` 同一做法 ✓）。
 *
 * <p><b>判据边界（如实申报）</b>：本类**不**覆盖派发器的 Bukkit 面 —— 名称→在线玩家（{@code Bukkit.getPlayerExact} ✗）、
 * 主线程判定、`CommandAccess` 门禁、审计日志、以及**真的调用 `RoleAPI.executeComponentOperation`**（需要活实例 ✗）
 * ⇒ 这些属**运行级**（本卡静态半 ✗，已申报）。
 */
public class ComponentOperationDispatcherTest {

    /** 空服务集桩（冻结件 §4 T-5 批准形态 ✓）。 */
    private static ComponentServices inertServices() {
        return new ComponentServices(null, null, null);
    }

    /** 普通桩件（定位只看 id ✓，不需要任何能力面 ✓）。 */
    private static final class Stub extends RoleComponent {
        Stub(String id) {
            super(id, inertServices());
        }
    }

    private static List<RoleComponent> components(String... ids) {
        return java.util.Arrays.stream(ids).map(Stub::new).map(RoleComponent.class::cast).toList();
    }

    // ───────── ① 唯一定位成功 ─────────

    /** 单份 + 不给下标 ⇒ {@code OK}，且目标是那一份 ✓。 */
    @Test
    public void singleMatchWithoutIndexIsOk() {
        List<RoleComponent> list = components("energy", "sanTE");
        ComponentOperationDispatcher.Resolution r = ComponentOperationDispatcher.resolve(list, "energy");
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.OK, r.kind());
        assertSame("目标 = 命中那一份", list.get(0), r.target());
        assertEquals("id", "energy", r.id());
        assertNull("未给下标 ⇒ index 为 null", r.index());
        assertEquals("可用 id 按首次出现顺序去重", List.of("energy", "sanTE"), r.availableIds());
    }

    /** 单份 + 显式 `#0` ⇒ 合法（0 基 ✓）。 */
    @Test
    public void singleMatchWithExplicitZeroIndexIsOk() {
        List<RoleComponent> list = components("energy");
        ComponentOperationDispatcher.Resolution r = ComponentOperationDispatcher.resolve(list, "energy#0");
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.OK, r.kind());
        assertSame(list.get(0), r.target());
        assertEquals("0 基下标", Integer.valueOf(0), r.index());
    }

    // ───────── ② 回绝：id 不存在 / 多份歧义 / 下标不合法 ─────────

    /** 命中 **0 份** ⇒ {@code NO_SUCH_ID}，并带上**可用 id 列表** ✓（§6.3）。 */
    @Test
    public void unknownIdIsRefusedWithAvailableIds() {
        ComponentOperationDispatcher.Resolution r =
                ComponentOperationDispatcher.resolve(components("energy", "sanTE"), "nope");
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.NO_SUCH_ID, r.kind());
        assertNull("回绝 ⇒ 无目标", r.target());
        assertEquals("列出可用 id", List.of("energy", "sanTE"), r.availableIds());
    }

    /** 空 id（如 `"#0"`）⇒ 也按**未知 id** 回绝 ✓（语法不合法，不得当成"任意组件"✗）。 */
    @Test
    public void emptyIdIsRefused() {
        ComponentOperationDispatcher.Resolution r = ComponentOperationDispatcher.resolve(components("energy"), "#0");
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.NO_SUCH_ID, r.kind());
        assertTrue("可用 id 仍被列出", r.availableIds().contains("energy"));
    }

    /** 同 id **多份且未给下标** ⇒ {@code AMBIGUOUS}（**绝不静默取第一份** ✗，§6.4 ✓）。 */
    @Test
    public void duplicateIdWithoutIndexIsAmbiguous() {
        ComponentOperationDispatcher.Resolution r =
                ComponentOperationDispatcher.resolve(components("dup", "dup"), "dup");
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.AMBIGUOUS, r.kind());
        assertNull("歧义 ⇒ 无目标", r.target());
        assertEquals("命中份数（供 0 基提示）", 2, r.matches());
    }

    /** 同 id 多份 + 显式 0 基下标 ⇒ 精确定位（`#0` / `#1` 各指一份 ✓）。 */
    @Test
    public void indexDisambiguatesDuplicates() {
        List<RoleComponent> list = components("dup", "dup", "energy");
        ComponentOperationDispatcher.Resolution first = ComponentOperationDispatcher.resolve(list, "dup#0");
        ComponentOperationDispatcher.Resolution second = ComponentOperationDispatcher.resolve(list, "dup#1");
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.OK, first.kind());
        assertEquals(ComponentOperationDispatcher.Resolution.Kind.OK, second.kind());
        assertSame("0 基 = 第一份", list.get(0), first.target());
        assertSame("0 基 = 第二份", list.get(1), second.target());
    }

    /** 下标**非数字** / **为负** / **越界** ⇒ {@code BAD_INDEX} ✓（回绝，不得静默夹取 ✗）。 */
    @Test
    public void malformedIndexIsRefused() {
        List<RoleComponent> list = components("energy");
        for (String bad : List.of("energy#abc", "energy#-1", "energy#1", "energy#")) {
            ComponentOperationDispatcher.Resolution r = ComponentOperationDispatcher.resolve(list, bad);
            assertEquals("'" + bad + "' 必须回绝", ComponentOperationDispatcher.Resolution.Kind.BAD_INDEX, r.kind());
            assertNull("回绝 ⇒ 无目标", r.target());
        }
    }

    /** 分隔符口径：**最后一个** `#` 为分隔符 ✓（`a#b` 里的 `#` 不参与 id ✓）。 */
    @Test
    public void lastHashIsTheSeparator() {
        ComponentOperationDispatcher.Resolution r =
                ComponentOperationDispatcher.resolve(components("energy"), "energy#0#1");
        assertEquals("'energy#0' 不是已知 id ⇒ 未知 id", ComponentOperationDispatcher.Resolution.Kind.NO_SUCH_ID, r.kind());
        assertEquals("id 取最后一个 # 之前的部分", "energy#0", r.id());
    }

    /** 可用 id 列表：**去重**且保持**首次出现顺序** ✓（供回绝文案与 Tab 补全共用 ✓）。 */
    @Test
    public void availableIdsAreDeduplicatedInOrder() {
        assertEquals(List.of("a", "b"), ComponentOperationDispatcher.availableIds(components("a", "b", "a")));
        assertEquals("空容器 ⇒ 空表（不抛 ✓）", List.of(), ComponentOperationDispatcher.availableIds(List.of()));
    }
}
