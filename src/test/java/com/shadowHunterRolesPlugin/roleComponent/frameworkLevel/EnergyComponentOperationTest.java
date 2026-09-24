package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 阶段 13 · t120（**组件操作面 · 第一片**）的试点单测：只测**能量组件的 payload 解析与返回值语义** ✓；
 * **阶段 13 · t124** 起随接口迁移：返回类型 {@code boolean → String} ⇒ 本类断言同步改为**字符串断言** ✓。
 *
 * <p><b>判据来源</b>：设计定案 {@code debug-logs/测试记录/阶段13-组件操作面-设计定案.md}
 * §2（接口契约）· §10.2 护栏①（**冻结为单方法**）· §10.4（首 token 必为动词 · grammar 写进组件 javadoc ✓）；
 * **t124 的返回约定**（用户裁定"布尔换成字符串"）：{@code null} = 未识别/拒绝 ✗ · {@code ""} = 已识别无回值 ✓ ·
 * **非空串** = 规范化值 ✓ ⇒ 能量组件一律回"写后/当前值"（它总有一个可回的值，**从不**回空串 ✓）。
 *
 * <p><b>为什么能离线跑</b>：能量组件是**纯状态组件** —— 构造期只要一个**空服务集桩**（冻结件 §4 T-5
 * 批准的形态，与 {@code CapabilityDispatchTest} / {@code StateBindingByInstanceTest} 同一做法 ✓）
 * 与一个**可空的 {@code ChangeSink}**（传 {@code null} ⇒ 组件内部用空实现 ✓）⇒ 不碰 Bukkit、无副作用 ✓。
 *
 * <p><b>判据边界（如实申报）</b>：本类**不**覆盖"变更通知是否真的触发置脏/事件"（那由容器在构造期注入的
 * {@link EnergyComponent.ChangeSink} 承担，属运行级装配面 ✗）；也不覆盖指令面/派发器与 {@code RoleAPI}
 * 收口（归后续片 ✗）。
 *
 * <p><b>订阅面 · 本类为何仍是"假 sink"而不是"记录型 {@code Consumer}"（如实申报）</b>：
 * 本组件**没有监听器列表** —— 它只有 {@link EnergyComponent.ChangeSink}（**现算 0 个类 implements 它**；
 * 唯一实现形态 = 容器构造期传的 lambda）⇒ 这里传 {@code null} 正是"已经用函数式形态"的写法 ✓。
 * 真正需要改造的 `SanTEComponent` 其假 sink **已改成记录型 {@code Consumer}** ✓
 * （见 {@code SanTEComponentOperationTest}）。
 */
public class EnergyComponentOperationTest {

    /** 与 {@code RoleInstance} 构造期一致：上限 100 ⇒ **构造后即满能量 100** ✓。 */
    private static final int MAX = 100;

    /** 空服务集桩 + 空 sink（{@code null} ⇒ 组件内部替换为空实现 ✓）。 */
    private static EnergyComponent energy() {
        return new EnergyComponent("energy", new ComponentServices(null, null, null), MAX, null);
    }

    // ───────── ① 四个已识别动词（add / consume / set / current）—— 一律回"写后/当前值" ─────────

    /** {@code add 5} ⇒ 回**写后值**（先把能量压到 50 ⇒ 加后 55，不触上限、可直接观测 ✓）。 */
    @Test
    public void addIncreasesEnergy() {
        EnergyComponent energy = energy();
        assertEquals("前置 set 50 ⇒ 回写后值", "50", energy.onOperationCommand("set 50"));
        assertEquals("add 5 ⇒ 回写后值", "55", energy.onOperationCommand("add 5"));
        assertEquals("add 5 后当前能量", 55, energy.current());
    }

    /** {@code consume 30} ⇒ 回**写后值**（100 ⇒ 70）。 */
    @Test
    public void consumeDecreasesEnergy() {
        EnergyComponent energy = energy();
        assertEquals("consume 30 ⇒ 回写后值", "70", energy.onOperationCommand("consume 30"));
        assertEquals("consume 30 后当前能量", 70, energy.current());
    }

    /** {@code set 42} ⇒ 回**写后值**（精确写入）。 */
    @Test
    public void setWritesExactValue() {
        EnergyComponent energy = energy();
        assertEquals("set 42 ⇒ 回写后值", "42", energy.onOperationCommand("set 42"));
        assertEquals("set 42 后当前能量", 42, energy.current());
    }

    /** {@code current} ⇒ 回**当前值**（读操作；**无副作用** ✓）。 */
    @Test
    public void currentIsReadOnly() {
        EnergyComponent energy = energy();
        assertEquals("current ⇒ 回当前值", "100", energy.onOperationCommand("current"));
        assertEquals("只读动词不得改动状态", MAX, energy.current());
    }

    // ───────── ② 拒绝面：未识别 / 语法错 / 参数不合法（一律 null 且无副作用） ─────────

    /** 未知动词 / 大小写不符 ⇒ **{@code null}**（未识别）。 */
    @Test
    public void unknownVerbIsRejected() {
        EnergyComponent energy = energy();
        assertNull("未知动词必须拒绝（null）", energy.onOperationCommand("frobnicate 1"));
        assertNull("本片试点只接四个动词 ⇒ max 未识别（null）", energy.onOperationCommand("max"));
        assertNull("动词大小写敏感 ⇒ Add 未识别（null）", energy.onOperationCommand("Add 5"));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    /** 空 payload（{@code null} / 空串 / 纯空白）⇒ **{@code null}**（本组件自定的语义 ✓）。 */
    @Test
    public void emptyPayloadIsRejected() {
        EnergyComponent energy = energy();
        assertNull("null ⇒ 未识别（null）", energy.onOperationCommand(null));
        assertNull("空串 ⇒ 未识别（null）", energy.onOperationCommand(""));
        assertNull("纯空白 ⇒ 未识别（null）", energy.onOperationCommand("   "));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    /** 语法错：缺参 / 多参 / 非数字 / 负数 / 只读动词带参 ⇒ **{@code null}**。 */
    @Test
    public void malformedArgumentsAreRejected() {
        EnergyComponent energy = energy();
        assertNull("缺参", energy.onOperationCommand("add"));
        assertNull("非数字", energy.onOperationCommand("add abc"));
        assertNull("多参", energy.onOperationCommand("add 1 2"));
        assertNull("负数", energy.onOperationCommand("add -1"));
        assertNull("只读动词带参", energy.onOperationCommand("current 5"));
        assertNull("set 缺参", energy.onOperationCommand("set"));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    /** 整数溢出（超出 {@code int}）⇒ **{@code null}**（不得静默截断 ✗）。 */
    @Test
    public void numericOverflowIsRejected() {
        EnergyComponent energy = energy();
        assertNull("溢出必须拒绝（null）", energy.onOperationCommand("add 99999999999"));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    // ───────── ③ 边界与返回值语义 ─────────

    /** 写动词的 clamp **仍由组件承担**（字符串面只是薄适配层 ✓）⇒ 回值即 clamp 后的值 ✓。 */
    @Test
    public void writesAreClampedByComponent() {
        EnergyComponent energy = energy();
        assertEquals("set 超上限 ⇒ 回 clamp 后的值", "100", energy.onOperationCommand("set 999"));
        assertEquals("set 超上限 ⇒ clamp 到 max", MAX, energy.current());
        assertEquals("add 超上限 ⇒ 回 clamp 后的值", "100", energy.onOperationCommand("add 999"));
        assertEquals("add 超上限 ⇒ clamp 到 max", MAX, energy.current());
    }

    /**
     * {@code consume} 能量不足 ⇒ **已识别但语义未达成** ⇒ 回**未变的当前值**（**不是 {@code null}** ✓），
     * 且状态无变更 ✓ —— 这正是 t124"回写后值"约定对"语义未达成"的处置 ✓。
     */
    @Test
    public void insufficientConsumeIsRecognizedButChangesNothing() {
        EnergyComponent energy = energy();
        assertEquals("已识别 ⇒ 回未变的当前值（非 null）",
                "100", energy.onOperationCommand("consume 999"));
        assertEquals("能量不足 ⇒ 不扣、不产生变更", MAX, energy.current());
    }

    // ───────── ④ 接口形状护栏（AK1①：独立顶层接口 + 冻结为单方法；AO2：返回类型钉死） ─────────

    /**
     * 把设计定案 §2 / §10.2 护栏①**钉成可执行的判据**：
     * 独立顶层接口（不内嵌 ✗）· 冻结为单方法（不得再加方法/默认实现 ✗）· 唯一方法签名
     * （**阶段 13 · t124 起返回类型 = {@code String.class}** ✓）· 能量组件选择实现 ✓。
     */
    @Test
    public void operationProviderIsTopLevelAndFrozenAsSingleMethod() {
        assertNull("OperationProvider 必须是**独立顶层接口**（不内嵌）",
                OperationProvider.class.getEnclosingClass());

        Method[] declared = OperationProvider.class.getDeclaredMethods();
        assertEquals("**冻结为单方法**：不得再加方法 / 默认实现", 1, declared.length);
        assertEquals("返回类型（阶段 13 · t124：布尔换成字符串 ⇒ 组件能把值交出来）",
                String.class, declared[0].getReturnType());
        assertEquals("方法名", "onOperationCommand", declared[0].getName());
        assertEquals("参数个数（v2 定案：只有一个 payload 字符串）", 1, declared[0].getParameterCount());
        assertEquals("参数类型", "java.lang.String", declared[0].getParameterTypes()[0].getName());
        assertFalse("冻结为单方法 ⇒ 不得是默认方法", declared[0].isDefault());

        assertTrue("能量组件必须**选择实现**该接口（试点 ✓）", energy() instanceof OperationProvider);
    }
}
