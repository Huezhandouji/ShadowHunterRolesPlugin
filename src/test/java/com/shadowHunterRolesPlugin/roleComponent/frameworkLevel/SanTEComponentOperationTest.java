package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * `SanTEComponent` 的两族**离线**单测：
 * <ol>
 *   <li><b>组件操作面</b>（≥6 例 ✓）；</li>
 *   <li><b>监听器列表</b>（用户裁定「改用监听器列表，其他类只需要添加 {@code Consumer}」✓）。</li>
 * </ol>
 * <p>构造法照 `EnergyComponentOperationTest` 同形：`new SanTEComponent(id, new ComponentServices(null, null, null), max, sink)`
 * —— 服务集空桩 + **记录型假 sink**（既有 ChangeSink 通道 ✓，不新增平行通道 ✗）。
 *
 * <p><b>订阅面的形态</b>：旧形态的 `RecordingSink implements SanTEComponent.ChangeSink` 是**平台侧注入面**
 * （唯一实现者形态 = 容器 lambda）⇒ 保留该通道 ✓；而**订阅面**（原 `implements SanTEComponent.Subscriber`
 * + `subscribe/unsubscribe`）已换成 **JDK {@code Consumer} 监听器列表** ✓ ⇒ 本类含第 ② 族用例，
 * 且记录型假件用 {@code Consumer}（{@link RecordingListener} ✓，比"写一个实现类"更简单 ✓）。
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

    /**
     * **记录型监听器** —— 取代"写一个 {@code implements Subscriber} 的假件" ✓：
     * 一个 {@code Consumer} 把每次载荷记下来即可（**不再需要任何自定义接口** ✓）。
     */
    private static final class RecordingListener implements Consumer<SanTEComponent.Change> {
        final List<SanTEComponent.Change> seen = new ArrayList<>();

        @Override
        public void accept(SanTEComponent.Change change) {
            seen.add(change);
        }
    }

    private static SanTEComponent newComponent(RecordingSink sink) {
        return new SanTEComponent("sante", new ComponentServices(null, null, null), MAX, sink);
    }

    private static SanTEComponent newComponent() {
        return newComponent(new RecordingSink());
    }

    // ───────── ① 组件操作面 ─────────

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

    // ───────── ② 监听器列表（只添加 Consumer ✓）─────────

    /**
     * 载荷与顺序（**行为等价的核心**）：监听器收到的 {@code previous}/{@code current} 与
     * `set` 的 clamp 结果逐条一致 ✓，且顺序 = **添加先后** ✓。
     * <p>对照旧形态：`onSanTEChange(int pre, int now)` 的两个入参**原样**搬进 {@code Change} 记录 ✓。
     * <p><b>为什么这里显式调 {@code notifyListeners}</b>：本组件**不自己通知** ✗ —— 生产路径里派发由
     * 容器驱动（`RoleInstance.broadcastSanTEChange` ⇒ 逐个经 {@code guardedCall} 调监听器 ✓，
     * 且已先过 `pre == now` 的真变化闸门 ✓）；离线单测没有容器 ⇒ 用组件提供的
     * {@link SanTEComponent#notifyListeners(int, int)}（与生产**同源**的薄派发 ✓）显式驱动，
     * 而不是让测试自己写一遍循环 ✗。
     */
    @Test
    public void listenersReceiveChangeWithPreviousAndCurrentInRegistrationOrder() {
        SanTEComponent sante = newComponent();
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        sante.addListener(sante, first);
        sante.addListener(sante, second);

        assertEquals("添加两条监听", 2, sante.listenerCount());
        sante.set(40);                 // 100 → 40
        sante.notifyListeners(100, 40);
        sante.set(500);                // 40 → 100（clamp 到上限）
        sante.notifyListeners(40, 100);

        assertEquals("第一条收到两次", 2, first.seen.size());
        assertEquals("第一条载荷 = 100→40", 100, first.seen.get(0).previous());
        assertEquals("第一条载荷 = 100→40", 40, first.seen.get(0).current());
        assertEquals("clamp 后载荷 = 40→100", 100, first.seen.get(1).current());
        assertEquals("第二条同样收到两次（无条件通知 ✓）", 2, second.seen.size());
        assertEquals("两条载荷逐条相同", first.seen.get(0).current(), second.seen.get(0).current());
    }

    /**
     * 移除语义 = **按引用相等** ✓：移除后的监听器**不再收到**通知 ✓；不在名单里的登记是 **no-op** ✓
     * （返回 {@code false}，与旧 `unsubscribe` 的 no-op 语义逐字等价 ✓）。
     */
    @Test
    public void removeListenerIsByReferenceAndIdempotent() {
        SanTEComponent sante = newComponent();
        RecordingListener kept = new RecordingListener();
        RecordingListener dropped = new RecordingListener();
        SanTEComponent.Listener keptEntry = sante.addListener(sante, kept);
        SanTEComponent.Listener droppedEntry = sante.addListener(sante, dropped);

        assertTrue("按引用移除命中 ⇒ true", sante.removeListener(droppedEntry));
        assertFalse("再移除同一条 ⇒ no-op ⇒ false", sante.removeListener(droppedEntry));
        assertEquals("名单只剩一条", 1, sante.listenerCount());

        sante.notifyListeners(100, 30);
        assertEquals("被移除者不再收到通知", 0, dropped.seen.size());
        assertEquals("留下者照常收到", 1, kept.seen.size());
        assertTrue("留下的登记仍可按引用移除", sante.removeListener(keptEntry));
        assertEquals("名单清空", 0, sante.listenerCount());
    }

    /**
     * 幂等：**同一 owner + 同一监听器实例**重复添加**不重复登记** ✓（与旧 `subscribe` 的幂等语义一致 ✓）。
     * <p>★ 注意：**两个不同的 lambda 即使代码相同也是两个实例** ⇒ 不幂等 —— 这正是"按引用相等"的
     * 直接推论，也是消费者必须**把登记实例存进字段**的原因（两个既有消费者都已照此迁移 ✓）。
     */
    @Test
    public void addListenerIsIdempotentForTheSameInstance() {
        SanTEComponent sante = newComponent();
        RecordingListener once = new RecordingListener();
        SanTEComponent.Listener firstEntry = sante.addListener(sante, once);
        SanTEComponent.Listener secondEntry = sante.addListener(sante, once);

        assertEquals("同实例重复添加 ⇒ 只登记一次", 1, sante.listenerCount());
        assertEquals("两次添加回同一条登记（record 值相等 ✓）", firstEntry, secondEntry);

        sante.notifyListeners(100, 10);
        assertEquals("只收到一次通知（不是两次 ✓）", 1, once.seen.size());
    }

    /**
     * **遍历期间增删的安全**：遍历中新增/移除监听器
     * ⇒ 不抛 {@code ConcurrentModificationException} ✓，且**不影响本次遍历**（快照语义 ✓）。
     */
    @Test
    public void mutatingListenersDuringIterationIsSafe() {
        SanTEComponent sante = newComponent();
        RecordingListener late = new RecordingListener();
        RecordingListener existing = new RecordingListener();
        SanTEComponent.Listener existingEntry = sante.addListener(sante, existing);

        //遍历途中：先移除自己、再新增一条 —— 两者都不得影响本趟（快照 = 进入时已定 ✓）
        sante.forEachListener(entry -> {
            sante.removeListener(existingEntry);
            sante.addListener(sante, late);
        });

        assertEquals("本趟后名单 = 新加的那一条（原有一条已被移除 ✓）", 1, sante.listenerCount());

        sante.notifyListeners(100, 5);
        assertEquals("被移除者本趟后不再收到", 0, existing.seen.size());
        assertEquals("新加者从下一趟起收到", 1, late.seen.size());
    }

    /** 空值面：{@code null} owner / {@code null} 监听器 ⇒ 忽略（回 {@code null} ✓，旧 `subscribe(null)` 同义 ✓）。 */
    @Test
    public void nullInputsAreIgnored() {
        SanTEComponent sante = newComponent();
        assertNull("null owner", sante.addListener(null, change -> { }));
        assertNull("null listener", sante.addListener(sante, null));
        assertEquals("两条都被忽略 ⇒ 名单为空", 0, sante.listenerCount());
        assertFalse("移除 null ⇒ false（no-op ✓）", sante.removeListener(null));
        assertFalse("移除一条从未登记的 ⇒ false", sante.removeListener(new SanTEComponent.Listener(sante, change -> { })));
    }

    /**
     * **旧订阅面确已删除**（"删除旧嵌套接口与旧订阅入口"的判据 ✓，用**反射**离线核验）：
     * `Subscriber` 类型与 `subscribe` / `unsubscribe` / `subscriberCount` / `forEachSubscriber` 四个入口
     * **都不存在** ✓；而 `ChangeSink`（平台侧注入面）**仍在** ✓。
     */
    @Test
    public void legacySubscriberSurfaceIsGone() {
        for (Class<?> nested : SanTEComponent.class.getDeclaredClasses()) {
            assertFalse("旧嵌套接口 Subscriber 必须已删除：发现了 " + nested.getSimpleName(),
                    nested.getSimpleName().equals("Subscriber"));
        }
        for (Method method : SanTEComponent.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse("旧订阅入口必须已删除：" + name,
                    name.equals("subscribe") || name.equals("unsubscribe")
                            || name.equals("subscriberCount") || name.equals("forEachSubscriber"));
        }
        assertNotNull("平台侧注入面 ChangeSink 仍在（本次不动它 ✓）", SanTEComponent.ChangeSink.class);
    }
}
