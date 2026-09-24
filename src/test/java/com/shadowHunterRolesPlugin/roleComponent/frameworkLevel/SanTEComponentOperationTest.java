package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * `SanTEComponent` 组件操作面（阶段 13 · t136）的**离线**单测（≥6 例 ✓）。
 * <p>构造法照 `EnergyComponentOperationTest` 同形：`new SanTEComponent(id, new ComponentServices(null, null, null), max, sink)`
 * —— 服务集空桩 + **记录型假 sink**（既有 ChangeSink 通道 ✓，不新增平行通道 ✗）。
 */
public class SanTEComponentOperationTest {

    private static final int MAX = 100;

    /** 记录型 ChangeSink：把「真变化」逐条记下，供断言既有通道被走到（不需要 Bukkit ✓）。 */
    private static final class RecordingSink implements SanTEComponent.ChangeSink {
        final List<int[]> changes = new ArrayList<>();

        @Override
        public void onSanTEChanged(int previous, int current, int max) {
            changes.add(new int[]{previous, current});
        }
    }

    private static SanTEComponent newComponent(RecordingSink sink) {
        return new SanTEComponent("sante", new ComponentServices(null, null, null), MAX, sink);
    }

    /** 写入动词回「写后值」；读动词回当前值（试点同风格 ✓）。 */
    @Test
    public void writeVerbsReturnPostValueAndReadVerbsReturnCurrent() {
        RecordingSink sink = new RecordingSink();
        SanTEComponent sante = newComponent(sink);

        assertEquals("set 50 ⇒ 写后值", "50", sante.onOperationCommand("set 50"));
        assertEquals("set 后当前值", 50, sante.current());
        assertEquals("gain 5 ⇒ 写后值", "55", sante.onOperationCommand("gain 5"));
        assertEquals("current ⇒ 当前值", "55", sante.onOperationCommand("current"));
        assertEquals("max ⇒ 上限", Integer.toString(MAX), sante.onOperationCommand("max"));
        assertEquals("decrease 30 ⇒ 写后值", "25", sante.onOperationCommand("decrease 30"));
        assertTrue("既有 ChangeSink 通道被走到（零新增通道 ✓）", sink.changes.size() >= 3);
    }

    /** 未知动词（含大小写差异）⇒ 未识别 ⇒ null（不改状态 ✓）。 */
    @Test
    public void unknownVerbIsRejected() {
        SanTEComponent sante = newComponent(new RecordingSink());
        sante.onOperationCommand("set 40");
        assertNull(sante.onOperationCommand("reset 1"));
        assertNull(sante.onOperationCommand("SET 40"));
        assertNull(sante.onOperationCommand("Current"));
        assertEquals("未知动词不得改状态", 40, sante.current());
    }

    /** 空 / 空白 / null payload ⇒ null（已在 javadoc 写明 ✓）。 */
    @Test
    public void emptyPayloadIsRejected() {
        SanTEComponent sante = newComponent(new RecordingSink());
        assertNull("null", sante.onOperationCommand(null));
        assertNull("空串", sante.onOperationCommand(""));
        assertNull("空白", sante.onOperationCommand("   "));
    }

    /** 参数非法（非数字 / 负数 / 溢出 / 个数不对）⇒ null。 */
    @Test
    public void malformedArgumentsAreRejected() {
        SanTEComponent sante = newComponent(new RecordingSink());
        assertNull("非数字", sante.onOperationCommand("set abc"));
        assertNull("负数", sante.onOperationCommand("gain -1"));
        assertNull("溢出", sante.onOperationCommand("set 999999999999"));
        assertNull("缺参", sante.onOperationCommand("set"));
        assertNull("多参", sante.onOperationCommand("set 1 2"));
        assertNull("只读动词带参", sante.onOperationCommand("current 1"));
    }

    /** 写入超上限 ⇒ 走既有 clamp（回写后值 = 上限 ✓，不是请求值）。 */
    @Test
    public void setAboveMaxIsClampedByExistingPath() {
        SanTEComponent sante = newComponent(new RecordingSink());
        assertEquals("set 500 ⇒ clamp 到上限", Integer.toString(MAX), sante.onOperationCommand("set 500"));
        assertEquals(MAX, sante.current());
    }

    /** 边界：`decrease` 不足 ⇒ **已识别**（回非空 ✓）且值单调不增（既有 clamp 语义 ✓）。 */
    @Test
    public void decreaseBelowZeroStaysRecognisedAndMonotone() {
        SanTEComponent sante = newComponent(new RecordingSink());
        sante.onOperationCommand("set 20");
        String after = sante.onOperationCommand("decrease 999");
        assertNotNull("已识别（非 null ✓）", after);
        assertTrue("值单调不增", sante.current() <= 20);
        assertTrue("非负", sante.current() >= 0);
    }

    /** `set 0` 是合法写入（0 是被允许的边界值 ✓）。 */
    @Test
    public void zeroIsAValidWrite() {
        SanTEComponent sante = newComponent(new RecordingSink());
        assertEquals("0", sante.onOperationCommand("set 0"));
        assertEquals(0, sante.current());
        assertEquals("gain 3 ⇒ 3", "3", sante.onOperationCommand("gain 3"));
    }
}