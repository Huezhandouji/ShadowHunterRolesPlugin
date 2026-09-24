package com.shadowHunterRolesPlugin.internal.api;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.EnergyComponent;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * 阶段 13 · t125：**组件操作面的派发面单测**（离线）—— 只测 {@code RoleAPIImpl.dispatchOperation(...)}
 * 这条**纯函数**（定位 → `#index` 消歧 → 转发 → 异常兜底 ✓）。
 *
 * <p><b>判据来源</b>：设计定案 {@code debug-logs/测试记录/阶段13/阶段13-组件操作面-设计定案.md} §4④（命中 0 份 /
 * 同 id 多份且未给下标 ⇒ **拒绝**，**绝不静默取第一份** ✗）· §6.8（组件内部异常 ⇒ 捕获、不得逃到调用方 ✗）·
 * §7.4 ②（`#index` 消歧写在 id 字符串里 ✓）· §10.3（返回值 = 规范化值字符串 / `null` 表失败 ✓）。
 *
 * <p><b>为什么能离线跑</b>：`dispatchOperation` 是**静态纯函数**（只吃一份组件列表 ✓，不读注册表、不碰
 * Bukkit、无副作用 ✓）⇒ 用桩件 + **真的 {@code EnergyComponent}**（纯状态组件，空服务集桩 + 可空 sink ✓）
 * 就能驱动 ✓，与 {@code CapabilityDispatchTest} 同一做法 ✓。
 *
 * <p><b>判据边界（如实申报）</b>：本类**不**覆盖"UUID → 角色实例"那半（`RoleAPIImpl.executeComponentOperation`
 * 的入口段：无实例 ⇒ `null` ✓）—— 那需要真的 `RoleManager`/`Player`（Bukkit 运行级 ✗）⇒ 本卡未覆盖 ✓。
 */
public class ComponentOperationDispatchTest {

    /** 空服务集桩（冻结件 §4 T-5 批准形态 ✓）。 */
    private static ComponentServices inertServices() {
        return new ComponentServices(null, null, null);
    }

    /** 实现了操作面的桩件：回 {@code "ok:" + payload}（便于断言"**原样**返回" ✓）。 */
    private static final class ProviderStub extends RoleComponent implements OperationProvider {
        ProviderStub(String id) {
            super(id, inertServices());
        }

        @Override
        public String onOperationCommand(String payload) {
            return "ok:" + payload;
        }
    }

    /** 存在但**未实现**操作面的桩件 ⇒ 不支持操作指令 ✓。 */
    private static final class PlainStub extends RoleComponent {
        PlainStub(String id) {
            super(id, inertServices());
        }
    }

    /** 组件内部抛异常的桩件（§6.8：捕获 ⇒ `null` ✓）。 */
    private static final class ExplodingStub extends RoleComponent implements OperationProvider {
        ExplodingStub(String id) {
            super(id, inertServices());
        }

        @Override
        public String onOperationCommand(String payload) {
            throw new IllegalStateException("boom");
        }
    }

    /** 回**空串**的桩件（= "已识别但无回值" ✓，必须与 `null` 区分开 ✓）。 */
    private static final class EmptyValueStub extends RoleComponent implements OperationProvider {
        EmptyValueStub(String id) {
            super(id, inertServices());
        }

        @Override
        public String onOperationCommand(String payload) {
            return "";
        }
    }

    /** 真能量组件（纯状态组件 ✓）：上限 100 ⇒ 构造后即满 100 ✓。 */
    private static EnergyComponent energyComponent() {
        return new EnergyComponent("energy", inertServices(), 100, null);
    }

    // ───────── ① 定位失败面 ─────────

    /** 未知 componentId（命中 **0 份**）⇒ `null` ✓。 */
    @Test
    public void unknownComponentIdYieldsNull() {
        List<RoleComponent> components = List.of(energyComponent());
        assertNull("命中 0 份 ⇒ 拒绝", RoleAPIImpl.dispatchOperation(components, "nope", "add 5"));
        assertNull("空 id ⇒ 语法不合法", RoleAPIImpl.dispatchOperation(components, "", "add 5"));
    }

    /** 同 id **多份且未给 `#index`** ⇒ `null`（**绝不静默取第一份** ✗）。 */
    @Test
    public void duplicateIdWithoutIndexIsRefused() {
        List<RoleComponent> components = List.of(new ProviderStub("dup"), new ProviderStub("dup"));
        assertNull("同 id 两份且未给下标 ⇒ 拒绝",
                RoleAPIImpl.dispatchOperation(components, "dup", "x"));
    }

    /** 给了 `#index` ⇒ 取**那一个**（**0 基** ✓；两份同 id 时能分辨 ✓）。 */
    @Test
    public void indexDisambiguatesDuplicates() {
        List<RoleComponent> components = List.of(
                new PlainStub("dup"),          // #0 = 非 provider ⇒ 回 null
                new ProviderStub("dup"));      // #1 = provider ⇒ 回值
        assertNull("下标 0 = 非 provider ⇒ null", RoleAPIImpl.dispatchOperation(components, "dup#0", "x"));
        assertEquals("下标 1 = provider ⇒ 原样返回", "ok:x", RoleAPIImpl.dispatchOperation(components, "dup#1", "x"));
    }

    /** 单份时给 `#0` 合法；下标**越界** / **非数字** / **负数** ⇒ `null` ✓。 */
    @Test
    public void malformedOrOutOfRangeIndexYieldsNull() {
        List<RoleComponent> components = List.of(new ProviderStub("one"));
        assertEquals("#0 合法（单份也能显式指定 ✓）", "ok:x", RoleAPIImpl.dispatchOperation(components, "one#0", "x"));
        assertNull("越界", RoleAPIImpl.dispatchOperation(components, "one#5", "x"));
        assertNull("非数字", RoleAPIImpl.dispatchOperation(components, "one#abc", "x"));
        assertNull("负数", RoleAPIImpl.dispatchOperation(components, "one#-1", "x"));
        assertNull("空 id + 下标（'#0'）⇒ 语法不合法", RoleAPIImpl.dispatchOperation(components, "#0", "x"));
    }

    // ───────── ② 目标组件能力面 ─────────

    /** 目标组件存在但**未实现** `OperationProvider` ⇒ `null`（不支持操作指令 ✓）。 */
    @Test
    public void componentWithoutOperationSurfaceYieldsNull() {
        List<RoleComponent> components = List.of(new PlainStub("plain"));
        assertNull("未实现操作面 ⇒ null", RoleAPIImpl.dispatchOperation(components, "plain", "add 5"));
    }

    /** 组件内部**异常** ⇒ 捕获后 `null`（**不得逃到调用方** ✗）。 */
    @Test
    public void componentFailureIsCaughtAndYieldsNull() {
        List<RoleComponent> components = List.of(new ExplodingStub("boom"));
        assertNull("组件抛异常 ⇒ 捕获、回 null（不逃出 ✓）",
                RoleAPIImpl.dispatchOperation(components, "boom", "add 5"));
    }

    /** `""`（已识别但**无回值**）必须与 `null`（失败）**区分开** ✓。 */
    @Test
    public void emptyValueIsDistinctFromNull() {
        List<RoleComponent> components = List.of(new EmptyValueStub("quiet"));
        assertEquals("已识别无回值 ⇒ 空串（不是 null ✓）",
                "", RoleAPIImpl.dispatchOperation(components, "quiet", "write-only"));
    }

    // ───────── ③ 真组件 happy path（能量组件 ✓） ─────────

    /** 真能量组件的 happy path：`set 42` ⇒ 回**写后值** `"42"` ✓（读 `current` 亦然 ✓）。 */
    @Test
    public void energyComponentHappyPathReturnsValue() {
        List<RoleComponent> components = List.of(energyComponent());
        assertEquals("写操作 ⇒ 回写后状态", "42", RoleAPIImpl.dispatchOperation(components, "energy", "set 42"));
        assertEquals("读操作 ⇒ 回值本身", "42", RoleAPIImpl.dispatchOperation(components, "energy", "current"));
        assertNull("未识别动词 ⇒ null", RoleAPIImpl.dispatchOperation(components, "energy", "frobnicate"));
    }
}
