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
 * `EnergyComponent` 的两族**离线**单测：
 * <ol>
 *   <li><b>组件操作面</b>（payload 解析与返回值语义 ✓）：返回类型 {@code boolean → String} 之后
 *       断言一律为**字符串断言** ✓；</li>
 *   <li><b>监听器列表</b>（用户裁定「改用监听器列表，其他类只需要添加 {@code Consumer}」✓）。</li>
 * </ol>
 *
 * <p><b>判据来源</b>：设计定案 {@code debug-logs/测试记录/阶段13-组件操作面-设计定案.md}
 * §2（接口契约）· §10.2 护栏①（**冻结为单方法**）· §10.4（首 token 必为动词 · grammar 写进组件 javadoc ✓）；
 * 返回约定：{@code null} = 未识别/拒绝 ✗ · {@code ""} = 已识别无回值 ✓ ·
 * **非空串** = 规范化值 ✓ ⇒ 能量组件一律回"写后/当前值"（它总有一个可回的值，**从不**回空串 ✓）。
 *
 * <p><b>为什么能离线跑</b>：能量组件是**纯状态组件** —— 构造期只要一个**空服务集桩**（冻结件 §4 T-5
 * 批准的形态，与 {@code CapabilityDispatchTest} / {@code StateBindingByInstanceTest} 同一做法 ✓）
 * 与一个**可空的监听器**（传 {@code null} ⇒ 不登记任何监听 ✓）⇒ 不碰 Bukkit、无副作用 ✓。
 *
 * <p><b>判据边界（如实申报）</b>：本类**不**覆盖"变更通知是否真的触发置脏/事件"（那由容器注册的监听器
 * 承担，属运行级装配面 ✗）；也不覆盖指令面/派发器与 {@code RoleAPI} 收口（归后续片 ✗）。
 *
 * <p><b>订阅面的形态</b>：本组件持 **JDK {@code Consumer} 监听器列表** ✓（旧嵌套 {@code ChangeSink} 接口
 * 与旧单播字段已删除 ✗）⇒ 本类的记录型假件用 {@code Consumer}（{@link RecordingListener} ✓，
 * 比"写一个实现类"更简单 ✓）。
 */
public class EnergyComponentOperationTest {

    /** 与 {@code RoleInstance} 构造期一致：上限 100 ⇒ **构造后即满能量 100** ✓。 */
    private static final int MAX = 100;

    /** 空服务集桩 + 无监听器（{@code null} ⇒ 不登记监听 ✓）。 */
    private static EnergyComponent energy() {
        return new EnergyComponent("energy", new ComponentServices(null, null, null), MAX, null);
    }

    /**
     * **记录型监听器** —— 一个 {@code Consumer} 把每次载荷记下来即可（**不需要任何自定义接口** ✓）。
     */
    private static final class RecordingListener implements java.util.function.Consumer<EnergyComponent.Change> {
        final java.util.List<EnergyComponent.Change> seen = new java.util.ArrayList<>();

        @Override
        public void accept(EnergyComponent.Change change) {
            seen.add(change);
        }
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

    // ───────── ⑤ 监听器列表（只添加 Consumer ✓） ─────────

    /**
     * 载荷与顺序（**行为等价的核心**）：监听器收到的 {@code previous}/{@code current}/{@code max} 与
     * 写入路径的 clamp 结果逐条一致 ✓，且顺序 = **添加先后** ✓。
     */
    @Test
    public void listenersReceiveChangeWithPreviousCurrentAndMaxInRegistrationOrder() {
        EnergyComponent energy = new EnergyComponent("energy", new ComponentServices(null, null, null), MAX, null);
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        energy.addListener(energy, first);
        energy.addListener(energy, second);

        assertEquals("添加两条监听", 2, energy.listenerCount());
        energy.set(40);                 // 100 → 40
        energy.set(500);                // 40 → 100（clamp 到上限）

        assertEquals("第一条收到两次", 2, first.seen.size());
        assertEquals("第一条载荷 = 100→40", 100, first.seen.get(0).previous());
        assertEquals("第一条载荷 = 100→40", 40, first.seen.get(0).current());
        assertEquals("载荷带 max（与旧三参 lambda 逐字相同）", MAX, first.seen.get(0).max());
        assertEquals("clamp 后载荷 = 40→100", 100, first.seen.get(1).current());
        assertEquals("第二条同样收到两次（无条件通知 ✓）", 2, second.seen.size());
        assertEquals("两条载荷逐条相同", first.seen.get(0).current(), second.seen.get(0).current());
    }

    /**
     * 移除语义 = **按引用相等** ✓：移除后的监听器**不再收到**通知 ✓；不在名单里的登记是 **no-op** ✓。
     */
    @Test
    public void removeListenerIsByReferenceAndIdempotent() {
        EnergyComponent energy = new EnergyComponent("energy", new ComponentServices(null, null, null), MAX, null);
        RecordingListener kept = new RecordingListener();
        RecordingListener dropped = new RecordingListener();
        EnergyComponent.Listener keptEntry = energy.addListener(energy, kept);
        EnergyComponent.Listener droppedEntry = energy.addListener(energy, dropped);

        assertTrue("按引用移除命中 ⇒ true", energy.removeListener(droppedEntry));
        assertFalse("再移除同一条 ⇒ no-op ⇒ false", energy.removeListener(droppedEntry));
        assertEquals("名单只剩一条", 1, energy.listenerCount());

        energy.set(30);
        assertEquals("被移除者不再收到通知", 0, dropped.seen.size());
        assertEquals("留下者照常收到", 1, kept.seen.size());
        assertTrue("留下的登记仍可按引用移除", energy.removeListener(keptEntry));
        assertEquals("名单清空", 0, energy.listenerCount());
    }

    /**
     * **遍历期间增删的安全**：遍历中新增/移除监听器 ⇒ 不抛 {@code ConcurrentModificationException} ✓，
     * 且**不影响本次遍历**（快照语义 ✓）。
     */
    @Test
    public void mutatingListenersDuringIterationIsSafe() {
        EnergyComponent energy = new EnergyComponent("energy", new ComponentServices(null, null, null), MAX, null);
        RecordingListener late = new RecordingListener();
        RecordingListener existing = new RecordingListener();
        EnergyComponent.Listener existingEntry = energy.addListener(energy, existing);

        energy.forEachListener(entry -> {
            energy.removeListener(existingEntry);
            energy.addListener(energy, late);
        });

        assertEquals("本趟后名单 = 新加的那一条（原有一条已被移除 ✓）", 1, energy.listenerCount());

        energy.set(5);
        assertEquals("被移除者本趟后不再收到", 0, existing.seen.size());
        assertEquals("新加者从下一趟起收到", 1, late.seen.size());
    }

    /**
     * **旧订阅面确已删除**（"删除旧嵌套接口与旧单播字段"的判据 ✓，用**反射**离线核验）：
     * `ChangeSink` 类型与 `onEnergyChanged` 方法**都不存在** ✓；而 `EnergyCosting`（耗能声明面，
     * 与订阅无关）**仍在** ✓。
     */
    @Test
    public void legacyChangeSinkSurfaceIsGone() {
        for (Class<?> nested : EnergyComponent.class.getDeclaredClasses()) {
            assertFalse("旧嵌套接口 ChangeSink 必须已删除：发现了 " + nested.getSimpleName(),
                    nested.getSimpleName().equals("ChangeSink"));
        }
        for (Method method : EnergyComponent.class.getDeclaredMethods()) {
            assertFalse("旧单播入口必须已删除：" + method.getName(),
                    method.getName().equals("onEnergyChanged"));
        }
        assertTrue("耗能声明面 EnergyCosting 仍在（与订阅无关）",
                EnergyComponent.EnergyCosting.class != null);
    }
}
