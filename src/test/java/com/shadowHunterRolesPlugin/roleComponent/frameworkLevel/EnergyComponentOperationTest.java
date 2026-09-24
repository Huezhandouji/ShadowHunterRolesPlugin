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
 * 阶段 13 · t120（**组件操作面 · 第一片**）的试点单测：只测**能量组件的 payload 解析与返回值语义** ✓。
 *
 * <p><b>判据来源</b>：设计定案 {@code debug-logs/测试记录/阶段13-组件操作面-设计定案.md}
 * §2（接口契约：{@code true} = 已识别并按其语义处理、**不论语义上成功与否**）·
 * §10.2 护栏①（**冻结为单方法**）· §10.4（首 token 必为动词 · grammar 写进组件 javadoc ✓）。
 *
 * <p><b>为什么能离线跑</b>：能量组件是**纯状态组件** —— 构造期只要一个**空服务集桩**（冻结件 §4 T-5
 * 批准的形态，与 {@code CapabilityDispatchTest} / {@code StateBindingByInstanceTest} 同一做法 ✓）
 * 与一个**可空的 {@code ChangeSink}**（传 {@code null} ⇒ 组件内部用空实现 ✓）⇒ 不碰 Bukkit、无副作用 ✓。
 *
 * <p><b>判据边界（如实申报）</b>：本类**不**覆盖"变更通知是否真的触发置脏/事件"（那由容器在构造期注入的
 * {@link EnergyComponent.ChangeSink} 承担，属运行级装配面 ✗）；也不覆盖指令面/派发器与 {@code RoleAPI}
 * 收口（归后续片 ✗）。
 */
public class EnergyComponentOperationTest {

    /** 与 {@code RoleInstance} 构造期一致：上限 100 ⇒ **构造后即满能量 100** ✓。 */
    private static final int MAX = 100;

    /** 空服务集桩 + 空 sink（{@code null} ⇒ 组件内部替换为空实现 ✓）。 */
    private static EnergyComponent energy() {
        return new EnergyComponent("energy", new ComponentServices(null, null, null), MAX, null);
    }

    // ───────── ① 四个已识别动词（add / consume / set / current） ─────────

    /** {@code add 5} ⇒ 识别 + 增能（**先把能量压到 50** ⇒ 加后 55，不触上限、可直接观测 ✓）。 */
    @Test
    public void addIncreasesEnergy() {
        EnergyComponent energy = energy();
        assertTrue("前置：set 50", energy.onOperationCommand("set 50"));
        assertTrue("add 5 必须被识别", energy.onOperationCommand("add 5"));
        assertEquals("add 5 后当前能量", 55, energy.current());
    }

    /** {@code consume 30} ⇒ 识别 + 扣减（100 ⇒ 70）。 */
    @Test
    public void consumeDecreasesEnergy() {
        EnergyComponent energy = energy();
        assertTrue("consume 30 必须被识别", energy.onOperationCommand("consume 30"));
        assertEquals("consume 30 后当前能量", 70, energy.current());
    }

    /** {@code set 42} ⇒ 识别 + 精确写入。 */
    @Test
    public void setWritesExactValue() {
        EnergyComponent energy = energy();
        assertTrue("set 42 必须被识别", energy.onOperationCommand("set 42"));
        assertEquals("set 42 后当前能量", 42, energy.current());
    }

    /** {@code current} ⇒ 识别 + **只读**（无副作用）。 */
    @Test
    public void currentIsReadOnly() {
        EnergyComponent energy = energy();
        assertTrue("current 必须被识别", energy.onOperationCommand("current"));
        assertEquals("只读动词不得改动状态", MAX, energy.current());
    }

    // ───────── ② 拒绝面：未识别 / 语法错 / 参数不合法（一律 false 且无副作用） ─────────

    /** 未知动词 / 大小写不符 ⇒ 未识别。 */
    @Test
    public void unknownVerbIsRejected() {
        EnergyComponent energy = energy();
        assertFalse("未知动词必须拒绝", energy.onOperationCommand("frobnicate 1"));
        assertFalse("本片试点只接四个动词 ⇒ max 未识别", energy.onOperationCommand("max"));
        assertFalse("动词大小写敏感 ⇒ Add 未识别", energy.onOperationCommand("Add 5"));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    /** 空 payload（{@code null} / 空串 / 纯空白）⇒ 未识别（本组件自定的语义 ✓）。 */
    @Test
    public void emptyPayloadIsRejected() {
        EnergyComponent energy = energy();
        assertFalse("null ⇒ 未识别", energy.onOperationCommand(null));
        assertFalse("空串 ⇒ 未识别", energy.onOperationCommand(""));
        assertFalse("纯空白 ⇒ 未识别", energy.onOperationCommand("   "));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    /** 语法错：缺参 / 多参 / 非数字 / 负数 / 只读动词带参 ⇒ 拒绝。 */
    @Test
    public void malformedArgumentsAreRejected() {
        EnergyComponent energy = energy();
        assertFalse("缺参", energy.onOperationCommand("add"));
        assertFalse("非数字", energy.onOperationCommand("add abc"));
        assertFalse("多参", energy.onOperationCommand("add 1 2"));
        assertFalse("负数", energy.onOperationCommand("add -1"));
        assertFalse("只读动词带参", energy.onOperationCommand("current 5"));
        assertFalse("set 缺参", energy.onOperationCommand("set"));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    /** 整数溢出（超出 {@code int}）⇒ 拒绝（不得静默截断 ✗）。 */
    @Test
    public void numericOverflowIsRejected() {
        EnergyComponent energy = energy();
        assertFalse("溢出必须拒绝", energy.onOperationCommand("add 99999999999"));
        assertEquals("拒绝路径不得改动状态", MAX, energy.current());
    }

    // ───────── ③ 边界与返回值语义 ─────────

    /** 写动词的 clamp **仍由组件承担**（字符串面只是薄适配层 ✓）。 */
    @Test
    public void writesAreClampedByComponent() {
        EnergyComponent energy = energy();
        assertTrue(energy.onOperationCommand("set 999"));
        assertEquals("set 超上限 ⇒ clamp 到 max", MAX, energy.current());
        assertTrue(energy.onOperationCommand("add 999"));
        assertEquals("add 超上限 ⇒ clamp 到 max", MAX, energy.current());
    }

    /** {@code consume} 能量不足 ⇒ **已识别但语义未达成** ⇒ 仍返回 {@code true}（设计定案 §2 ✓），且无变更 ✓。 */
    @Test
    public void insufficientConsumeIsRecognizedButChangesNothing() {
        EnergyComponent energy = energy();
        assertTrue("已识别的动词即使语义未达成也返回 true（§2：不论语义上成功与否）",
                energy.onOperationCommand("consume 999"));
        assertEquals("能量不足 ⇒ 不扣、不产生变更", MAX, energy.current());
    }

    // ───────── ④ 接口形状护栏（AK1①：独立顶层接口 + 冻结为单方法） ─────────

    /**
     * 把设计定案 §2 / §10.2 护栏①**钉成可执行的判据**：
     * 独立顶层接口（不内嵌 ✗）· 冻结为单方法（不得再加方法/默认实现 ✗）· 唯一方法签名 · 能量组件选择实现 ✓。
     */
    @Test
    public void operationProviderIsTopLevelAndFrozenAsSingleMethod() {
        assertNull("OperationProvider 必须是**独立顶层接口**（不内嵌）",
                OperationProvider.class.getEnclosingClass());

        Method[] declared = OperationProvider.class.getDeclaredMethods();
        assertEquals("**冻结为单方法**：不得再加方法 / 默认实现", 1, declared.length);
        assertEquals("返回类型", "boolean", declared[0].getReturnType().getName());
        assertEquals("方法名", "onOperationCommand", declared[0].getName());
        assertEquals("参数个数（v2 定案：只有一个 payload 字符串）", 1, declared[0].getParameterCount());
        assertEquals("参数类型", "java.lang.String", declared[0].getParameterTypes()[0].getName());
        assertFalse("冻结为单方法 ⇒ 不得是默认方法", declared[0].isDefault());

        assertTrue("能量组件必须**选择实现**该接口（试点 ✓）", energy() instanceof OperationProvider);
    }
}
