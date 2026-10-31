package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
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
 *   <li><b>组件操作面</b>；</li>
 *   <li><b>监听器列表</b>（用户裁定「改用监听器列表，其他类只需要添加 {@code Consumer}」）。</li>
 * </ol>
 * <p>构造法照 `EnergyComponentOperationTest` 同形：
 * `new SanTEComponent(id, new ComponentServicesPort(null, null, null), max)`
 * —— 服务集空桩 + **记录型假件**（JDK {@code Consumer}，不新增平行通道）。
 *
 * <p>两条通道（与生产的分工逐字一致）：**平台侧通道** = 名单里 owner = **组件自身**的那一条
 * ⇒ 写入路径直调它（无变化写入也调、异常照常上抛）；**订阅者通道** = owner 为**别的组件**的登记
 * ⇒ 生产里由容器在派发边界通知，离线单测用 {@link SanTEComponent#notifyListeners} 显式驱动。
 * 两族假件都是 {@code Consumer}（{@link RecordingListener}，比"写一个实现类"更简单）。
 */
public class SanTEComponentOperationTest {

    private static final int MAX = 100;

    /**
     * **记录型监听器** —— 一个 {@code Consumer} 把每次载荷记下来即可（**不再需要任何自定义接口**）；
     * 它既当**平台侧回调**（owner = 组件自身登记），也当**订阅者**（owner = 别的组件登记）。
     */
    private static final class RecordingListener implements Consumer<SanTEComponent.Change> {
        final List<SanTEComponent.Change> seen = new ArrayList<>();

        @Override
        public void accept(SanTEComponent.Change change) {
            seen.add(change);
        }
    }

    /** 订阅者用的假 owner（任意组件 —— 名单只按引用相等比较，不看具体类型）。 */
    private static final class StubOwner extends RoleComponent {
        StubOwner() { super("stubOwner", new ComponentServicesPort(null, null, null)); }
    }

    /** 订阅者共用的 owner 实例（幂等判据要的是**同一个** owner）。 */
    private static final StubOwner SUBSCRIBER = new StubOwner();

    /** 带**平台侧登记**（owner = 组件自身）的组件：写入路径会直调它。 */
    private static SanTEComponent newComponent(RecordingListener platform) {
        SanTEComponent sante = new SanTEComponent("sante", new ComponentServicesPort(null, null, null), MAX);
        sante.addListener(sante, platform);
        return sante;
    }

    /** 不带平台侧登记的组件（只有订阅者名单 ⇒ 便于逐条核对名单语义）。 */
    private static SanTEComponent newComponent() {
        return new SanTEComponent("sante", new ComponentServicesPort(null, null, null), MAX);
    }

    // ───────── 组件操作面 ─────────

    /** 写入动词回「写后值」；读动词回当前值（与能量组件同风格）。 */
    @Test
    public void writeVerbsReturnPostValueAndReadVerbsReturnCurrent() {
        RecordingListener platform = new RecordingListener();
        SanTEComponent sante = newComponent(platform);

        assertEquals("set 50 ⇒ 写后值", "50", sante.onOperationCommand("set 50"));
        assertEquals("set 后当前值", 50, sante.current());
        assertEquals("gain 5 ⇒ 写后值", "55", sante.onOperationCommand("gain 5"));
        assertEquals("current ⇒ 当前值", "55", sante.onOperationCommand("current"));
        assertEquals("max ⇒ 上限", Integer.toString(MAX), sante.onOperationCommand("max"));
        assertEquals("decrease 30 ⇒ 写后值", "25", sante.onOperationCommand("decrease 30"));
        assertTrue("平台侧通道被走到（零新增通道 ✓）", platform.seen.size() >= 3);
    }

    // ───────── 上限写口（角色进化档位用；经用户批准的框架边界改动）─────────

    /** 上限上调：current 不变、上限抬高后可在新边界内写满。 */
    @Test
    public void setMaxRaisesCapAndKeepsCurrent() {
        SanTEComponent sante = newComponent();
        assertEquals("构造期满值", MAX, sante.current());
        sante.setMax(SanTEComponent.SANTE_MAX + 50);
        assertEquals("上限已抬高", 150, sante.max());
        assertEquals("current 不因抬上限而变化", MAX, sante.current());
        sante.set(150);
        assertEquals("新上限内可写满", 150, sante.current());
    }

    /** 上限下调：current 夹回新上限，且只有当前值真的被夹动时才通知订阅者。 */
    @Test
    public void setMaxClampsCurrentAndNotifiesOnlyOnRealChange() {
        RecordingListener subscriber = new RecordingListener();
        SanTEComponent sante = newComponent();
        sante.addListener(SUBSCRIBER, subscriber);
        sante.set(80);
        subscriber.seen.clear();

        sante.setMax(60);
        assertEquals("上限已下调", 60, sante.max());
        assertEquals("current 夹回新上限", 60, sante.current());
        assertEquals("夹动 ⇒ 通知 1 次", 1, subscriber.seen.size());

        sante.setMax(120);
        assertEquals("上限上调不夹 current", 60, sante.current());
        assertEquals("current 未变 ⇒ 不再通知", 1, subscriber.seen.size());
    }

    /** 负上限按 0 处理（与构造期同一归一规则）。 */
    @Test
    public void setMaxTreatsNegativeAsZero() {
        SanTEComponent sante = newComponent();
        sante.setMax(-5);
        assertEquals("上限归零", 0, sante.max());
        assertEquals("current 夹到 0", 0, sante.current());
    }

    /** 未知动词（含大小写差异）⇒ 未识别 ⇒ null（不改状态）。 */
    @Test
    public void unknownVerbIsRejected() {
        SanTEComponent sante = newComponent(new RecordingListener());
        sante.onOperationCommand("set 40");
        assertNull(sante.onOperationCommand("reset 1"));
        assertNull(sante.onOperationCommand("SET 40"));
        assertNull(sante.onOperationCommand("Current"));
        assertEquals("未知动词不得改状态", 40, sante.current());
    }

    /** 空 / 空白 / null payload ⇒ null（已在 javadoc 写明）。 */
    @Test
    public void emptyPayloadIsRejected() {
        SanTEComponent sante = newComponent(new RecordingListener());
        assertNull("null", sante.onOperationCommand(null));
        assertNull("空串", sante.onOperationCommand(""));
        assertNull("空白", sante.onOperationCommand("   "));
    }

    /** 参数非法（非数字 / 负数 / 溢出 / 个数不对）⇒ null。 */
    @Test
    public void malformedArgumentsAreRejected() {
        SanTEComponent sante = newComponent(new RecordingListener());
        assertNull("非数字", sante.onOperationCommand("set abc"));
        assertNull("负数", sante.onOperationCommand("gain -1"));
        assertNull("溢出", sante.onOperationCommand("set 999999999999"));
        assertNull("缺参", sante.onOperationCommand("set"));
        assertNull("多参", sante.onOperationCommand("set 1 2"));
        assertNull("只读动词带参", sante.onOperationCommand("current 1"));
    }

    /** 写入超上限 ⇒ 走既有 clamp（回写后值 = 上限，不是请求值）。 */
    @Test
    public void setAboveMaxIsClampedByExistingPath() {
        SanTEComponent sante = newComponent(new RecordingListener());
        assertEquals("set 500 ⇒ clamp 到上限", Integer.toString(MAX), sante.onOperationCommand("set 500"));
        assertEquals(MAX, sante.current());
    }

    /** 边界：`decrease` 不足 ⇒ **已识别**（回非空），且夹在 min 之上（2026-10-30 起下界 = min，可为负）。 */
    @Test
    public void decreaseBelowZeroStaysRecognisedAndClampsAtMin() {
        SanTEComponent sante = newComponent(new RecordingListener());
        sante.onOperationCommand("set 20");
        String after = sante.onOperationCommand("decrease 999");
        assertNotNull("已识别（非 null ✓）", after);
        assertEquals("set 20 后 decrease 999 ⇒ 20-999 = -979（夹在 min -999 之上）", -979, sante.current());
        assertTrue("不得低于 min", sante.current() >= SanTEComponent.SANTE_MIN);
    }

    /** `set 0` 是合法写入（0 是被允许的边界值）。 */
    @Test
    public void zeroIsAValidWrite() {
        SanTEComponent sante = newComponent(new RecordingListener());
        assertEquals("0", sante.onOperationCommand("set 0"));
        assertEquals(0, sante.current());
        assertEquals("gain 3 ⇒ 3", "3", sante.onOperationCommand("gain 3"));
    }

    // ───────── 监听器列表（只添加 Consumer）─────────

    /**
     * 载荷与顺序（**行为等价的核心**）：监听器收到的 {@code previous}/{@code current} 与
     * `set` 的 clamp 结果逐条一致，且顺序 = **添加先后**。
     * <p>对照旧形态：`onSanTEChange(int pre, int now)` 的两个入参**原样**搬进 {@code Change} 记录。
     * <p>**写入路径自己就通知订阅者**（`set` → {@link SanTEComponent#notifyListeners}）——
     * 与 {@code EnergyComponent} 逐字同形 ⇒ 离线单测**不需要**再手工补一次派发。
     * <p>（生产路径里容器另有一层派发，带 `pre == now` 真变化闸门与逐条 `guardedCall`。）
     */
    @Test
    public void listenersReceiveChangeWithPreviousAndCurrentInRegistrationOrder() {
        SanTEComponent sante = newComponent();
        RecordingListener first = new RecordingListener();
        RecordingListener second = new RecordingListener();
        sante.addListener(SUBSCRIBER, first);
        sante.addListener(SUBSCRIBER, second);

        assertEquals("添加两条监听", 2, sante.listenerCount());
        sante.set(40);                 // 100 → 40（写入路径自行通知 ⇒ 每条收到 1 次）
        sante.set(500);                // 40 → 100（clamp 到上限）

        assertEquals("第一条收到两次", 2, first.seen.size());
        assertEquals("第一条载荷 = 100→40", 100, first.seen.get(0).previous());
        assertEquals("第一条载荷 = 100→40", 40, first.seen.get(0).current());
        assertEquals("clamp 后载荷 = 40→100", 100, first.seen.get(1).current());
        assertEquals("第二条同样收到两次（无条件通知 ✓）", 2, second.seen.size());
        assertEquals("两条载荷逐条相同", first.seen.get(0).current(), second.seen.get(0).current());
    }

    /**
     * **回归**（真 bug 的判据）：SanTE **归零**时订阅者**必须**收到那条变更。
     *
     * <p>曾经的形态：写入路径只通知 `owner == 本组件` 的那条（"平台侧"），而**产线上不存在**这样的登记
     * ⇒ `decrease` 到 0 时**没有任何订阅者被通知** ⇒ 依赖"归零即结束"的技能（例：红的黯然销魂）
     * 永远不结束。本测试锁住"归零必达"。
     */
    @Test
    public void decreaseToZeroNotifiesSubscribers() {
        SanTEComponent sante = newComponent();
        RecordingListener subscriber = new RecordingListener();
        sante.addListener(SUBSCRIBER, subscriber);

        sante.set(10);                 // 100 → 10
        sante.decrease(10);            // 10 → 0（归零）

        assertEquals("写入两次 ⇒ 两条通知", 2, subscriber.seen.size());
        SanTEComponent.Change zeroing = subscriber.seen.get(subscriber.seen.size() - 1);
        assertEquals("归零那条的旧值 = 10", 10, zeroing.previous());
        assertEquals("归零那条的当前值 = 0", 0, zeroing.current());
    }

    /**
     * **写入路径同时到达「平台侧登记」与「订阅者」**：
     * {@code set(...)} 无条件通知名单里的**每一条**（**无变化**的写入也通知 —— 事件发布时机不变），
     * 与 {@code EnergyComponent} 同形。
     */
    @Test
    public void writePathNotifiesEveryListenerEntry() {
        RecordingListener platform = new RecordingListener();
        SanTEComponent sante = newComponent(platform);
        RecordingListener subscriber = new RecordingListener();
        sante.addListener(SUBSCRIBER, subscriber);

        sante.set(40);
        sante.set(40);

        assertEquals("平台侧收到两次（含无变化写入 ✓）", 2, platform.seen.size());
        assertEquals("平台侧载荷 = clamp 结果", 40, platform.seen.get(1).current());
        assertEquals("订阅者同样收到两次 ✓", 2, subscriber.seen.size());
        assertEquals("两边载荷一致", platform.seen.get(1).current(), subscriber.seen.get(1).current());
    }

    /**
     * 移除语义 = **按引用相等**：移除后的监听器**不再收到**通知；不在名单里的登记是 **no-op**
     * （返回 {@code false}，与旧 `unsubscribe` 的 no-op 语义逐字等价）。
     */
    @Test
    public void removeListenerIsByReferenceAndIdempotent() {
        SanTEComponent sante = newComponent();
        RecordingListener kept = new RecordingListener();
        RecordingListener dropped = new RecordingListener();
        SanTEComponent.Listener keptEntry = sante.addListener(SUBSCRIBER, kept);
        SanTEComponent.Listener droppedEntry = sante.addListener(SUBSCRIBER, dropped);

        assertTrue("按引用移除命中 ⇒ true", sante.removeListener(droppedEntry));
        assertFalse("再移除同一条 ⇒ no-op ⇒ false", sante.removeListener(droppedEntry));
        assertEquals("名单只剩一条", 1, sante.listenerCount());

        sante.notifyListeners(new SanTEComponent.Change(100, 30));
        assertEquals("被移除者不再收到通知", 0, dropped.seen.size());
        assertEquals("留下者照常收到", 1, kept.seen.size());
        assertTrue("留下的登记仍可按引用移除", sante.removeListener(keptEntry));
        assertEquals("名单清空", 0, sante.listenerCount());
    }

    /**
     * 幂等：**同一 owner + 同一监听器实例**重复添加**不重复登记**（与旧 `subscribe` 的幂等语义一致）。
     * <p>**两个不同的 lambda 即使代码相同也是两个实例** ⇒ 不幂等 —— 这正是"按引用相等"的
     * 直接推论，也是消费者必须**把登记实例存进字段**的原因（两个既有消费者都已照此迁移）。
     */
    @Test
    public void addListenerIsIdempotentForTheSameInstance() {
        SanTEComponent sante = newComponent();
        RecordingListener once = new RecordingListener();
        SanTEComponent.Listener firstEntry = sante.addListener(SUBSCRIBER, once);
        SanTEComponent.Listener secondEntry = sante.addListener(SUBSCRIBER, once);

        assertEquals("同实例重复添加 ⇒ 只登记一次", 1, sante.listenerCount());
        assertEquals("两次添加回同一条登记（record 值相等 ✓）", firstEntry, secondEntry);

        sante.notifyListeners(new SanTEComponent.Change(100, 10));
        assertEquals("只收到一次通知（不是两次 ✓）", 1, once.seen.size());
    }

    /**
     * **遍历期间增删的安全**：遍历中新增/移除监听器
     * ⇒ 不抛 {@code ConcurrentModificationException}，且**不影响本次遍历**（快照语义）。
     */
    @Test
    public void mutatingListenersDuringIterationIsSafe() {
        SanTEComponent sante = newComponent();
        RecordingListener late = new RecordingListener();
        RecordingListener existing = new RecordingListener();
        SanTEComponent.Listener existingEntry = sante.addListener(SUBSCRIBER, existing);

        //遍历途中：先移除自己、再新增一条 —— 两者都不得影响本趟（快照 = 进入时已定）
        sante.forEachListener(entry -> {
            sante.removeListener(existingEntry);
            sante.addListener(SUBSCRIBER, late);
        });

        assertEquals("本趟后名单 = 新加的那一条（原有一条已被移除 ✓）", 1, sante.listenerCount());

        sante.notifyListeners(new SanTEComponent.Change(100, 5));
        assertEquals("被移除者本趟后不再收到", 0, existing.seen.size());
        assertEquals("新加者从下一趟起收到", 1, late.seen.size());
    }

    // ───────── 每角色 TE/SAN 配置（2026-10-30 框架改进）─────────

    /** 默认配置 = 100 / -999 / 阈值 0 / 惩罚开启；**有效下界 = min = -999**（2026-10-30 起惩罚不再钉值）。 */
    @Test
    public void defaultConfigurationMatchesLegacyBehaviour() {
        SanTEComponent.Configuration config = SanTEComponent.Configuration.DEFAULT;
        assertEquals("默认上限", 100, config.max());
        assertEquals("默认下限", -999, config.min());
        assertEquals("默认惩罚阈值", 0, config.punishmentThreshold());
        assertTrue("默认启用惩罚", config.punishmentEnabled());
        assertEquals("2026-10-30 起有效下界 = min（惩罚不再钉值）", -999, config.effectiveFloor());

        SanTEComponent sante = newComponent();
        assertEquals("默认 max", 100, sante.max());
        assertEquals("默认 min", -999, sante.min());
        assertEquals("默认 threshold", 0, sante.punishmentThreshold());
        assertEquals("默认有效下界 = -999", -999, sante.floor());
    }

    /** 自定义上限 / 下限 / 阈值：clamp 用 **min**（阈值只决定惩罚触发点）。 */
    @Test
    public void customBoundsAndThresholdAreHonoured() {
        SanTEComponent.Configuration config = new SanTEComponent.Configuration(150, -500, -100, true);
        SanTEComponent sante = new SanTEComponent("sante", new ComponentServicesPort(null, null, null), config);

        assertEquals("构造期满 SanTE", 150, sante.current());
        assertEquals("有效下界 = min（-500，与阈值无关）", -500, sante.floor());

        sante.set(-9999);
        assertEquals("低于有效下界 ⇒ 夹到 -500", -500, sante.current());
        sante.set(9999);
        assertEquals("高于上限 ⇒ 夹到 150", 150, sante.current());

        //运行期改上限：current 夹回新上限，有效下界同步收紧
        sante.set(150);
        sante.setMax(120);
        assertEquals("setMax 后 current 夹到 120", 120, sante.current());
        assertEquals("上限 120", 120, sante.max());
    }

    /** 下限生效（默认 -999）：TE 可以真的降到负数（惩罚期间被攻击也会掉，不再钉值）。 */
    @Test
    public void punishmentDisabledAllowsNegativeDownToMin() {
        SanTEComponent.Configuration config = SanTEComponent.Configuration.DEFAULT
                .withPunishmentEnabled(false);
        SanTEComponent sante = new SanTEComponent("sante", new ComponentServicesPort(null, null, null), config);

        assertEquals("关闭惩罚 ⇒ 有效下界 = min", -999, sante.floor());
        assertFalse("punishmentEnabled 反映配置", sante.punishmentEnabled());

        sante.set(-5000);
        assertEquals("夹到下限 -999", -999, sante.current());
        sante.set(-300);
        assertEquals("允许写负数", -300, sante.current());
        sante.increase(100);
        assertEquals("从负数往上加", -200, sante.current());
    }

    /** 描述符的链式 setter：完全不写 = 默认；写哪项改哪项。 */
    @Test
    public void specificationFluentSettersCompose() {
        SanTEComponent.Specification defaults = new SanTEComponent.Specification();
        assertEquals("默认配置", SanTEComponent.Configuration.DEFAULT, defaults.configuration());

        SanTEComponent.Specification custom = new SanTEComponent.Specification()
                .setMax(180).setMin(-600).setPunishmentThreshold(-60).setPunishmentEnabled(false);
        SanTEComponent.Configuration config = custom.configuration();
        assertEquals(180, config.max());
        assertEquals(-600, config.min());
        assertEquals(-60, config.punishmentThreshold());
        assertFalse(config.punishmentEnabled());

        SanTEComponent created = custom.create("sante", new ComponentServicesPort(null, null, null));
        assertEquals("描述符 create 走同一份配置", 180, created.max());
        assertEquals("有效下界 = min（惩罚关闭）", -600, created.floor());
    }

    /** 操作面新增读口 min / threshold / floor；set 允许负数（按有效下界 clamp）。 */
    @Test
    public void operationExposesBoundsAndAllowsNegativeSet() {
        SanTEComponent defaultComponent = newComponent(new RecordingListener());
        assertEquals("min 读口", "-999", defaultComponent.onOperationCommand("min"));
        assertEquals("threshold 读口", "0", defaultComponent.onOperationCommand("threshold"));
        assertEquals("floor 读口（默认 -999）", "-999", defaultComponent.onOperationCommand("floor"));
        assertEquals("默认配置下 set -50 直接生效", "-50", defaultComponent.onOperationCommand("set -50"));

        SanTEComponent negative = new SanTEComponent("sante",
                new ComponentServicesPort(null, null, null), SanTEComponent.Configuration.DEFAULT.withPunishmentEnabled(false));
        assertEquals("关闭惩罚后 set -50 生效", "-50", negative.onOperationCommand("set -50"));
        assertEquals("floor 读口 = -999", "-999", negative.onOperationCommand("floor"));
        assertNull("gain 的方向参数仍必须非负", negative.onOperationCommand("gain -1"));
    }

    /**
     * 惩罚触发阈值是**纯函数**（默认 0）：`current <= threshold` 即触发；角色可把阈值调到负数。
     */
    @Test
    public void punishmentTriggerUsesConfiguredThreshold() {
        assertTrue("默认：降到 0 触发", DefaultSanTEZeroPunishment.triggersPunishment(0, 0));
        assertTrue("默认：降到负数也触发", DefaultSanTEZeroPunishment.triggersPunishment(0, -5));
        assertFalse("默认：still above 0 不触发", DefaultSanTEZeroPunishment.triggersPunishment(0, 1));
        assertTrue("自定义阈值 -50：到 -50 触发", DefaultSanTEZeroPunishment.triggersPunishment(-50, -50));
        assertFalse("自定义阈值 -50：-49 还不触发", DefaultSanTEZeroPunishment.triggersPunishment(-50, -49));
    }

    /** 惩罚组件的节奏/强度可自定义；不写 = 100 刻 / 3 跳 / 10%（与改动前一致）。 */
    @Test
    public void punishmentSpecificationDefaultsAndTunables() {
        DefaultSanTEZeroPunishment.Specification defaults =
                new DefaultSanTEZeroPunishment.Specification();
        assertEquals("默认眩晕 100 刻", 100, defaults.stunTicks());
        assertEquals("默认 3 跳", 3, defaults.beats());
        assertEquals("默认每跳 10% 最大生命", 10d, defaults.damagePerBeatPercentOfMaxHealth(), 0.0001d);

        DefaultSanTEZeroPunishment.Specification custom =
                new DefaultSanTEZeroPunishment.Specification()
                        .setStunTicks(60).setBeats(5).setDamagePerBeatPercentOfMaxHealth(6.5d);
        assertEquals(60, custom.stunTicks());
        assertEquals(5, custom.beats());
        assertEquals(6.5d, custom.damagePerBeatPercentOfMaxHealth(), 0.0001d);

        //非法值归一：时长/跳数至少 1，伤害百分比非负
        DefaultSanTEZeroPunishment.Specification normalized =
                new DefaultSanTEZeroPunishment.Specification()
                        .setStunTicks(0).setBeats(-3).setDamagePerBeatPercentOfMaxHealth(-1d);
        assertEquals(1, normalized.stunTicks());
        assertEquals(1, normalized.beats());
        assertEquals(0d, normalized.damagePerBeatPercentOfMaxHealth(), 0.0001d);
    }

    /** 惩罚**不再钉值**：组件不得再覆写 {@code update()} 逐 tick 写 SanTE；阈值也不影响有效下界。 */
    @Test
    public void punishmentNoLongerPinsSanTe() {
        for (Method method : DefaultSanTEZeroPunishment.class.getDeclaredMethods()) {
            assertFalse("惩罚组件不得再逐 tick 钉 SanTE：发现了 update()",
                    method.getName().equals("update"));
        }
        assertEquals("阈值只决定触发点，不影响下界",
                -999, SanTEComponent.Configuration.DEFAULT.withPunishmentThreshold(-100).effectiveFloor());
    }

    /** 空值面：{@code null} owner / {@code null} 监听器 ⇒ 忽略（回 {@code null}，旧 `subscribe(null)` 同义）。 */
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
     * **旧订阅面确已删除**（"删除旧嵌套接口与旧入口"的判据，用**反射**离线核验）：
     * `Subscriber` 类型与 `subscribe` / `unsubscribe` / `subscriberCount` / `forEachSubscriber` 四个入口
     * **都不存在**；`ChangeSink` 类型与 `onSanTEChanged` 单播入口**也都不存在**
     * （平台侧通道已改成构造期给出的 JDK {@code Consumer} ⇒ 名单里没有它的一条）。
     */
    @Test
    public void legacySubscriberSurfaceIsGone() {
        for (Class<?> nested : SanTEComponent.class.getDeclaredClasses()) {
            String nestedName = nested.getSimpleName();
            assertFalse("旧嵌套接口 Subscriber 必须已删除：发现了 " + nestedName,
                    nestedName.equals("Subscriber"));
            assertFalse("旧嵌套接口 ChangeSink 必须已删除：发现了 " + nestedName,
                    nestedName.equals("ChangeSink"));
        }
        for (Method method : SanTEComponent.class.getDeclaredMethods()) {
            String name = method.getName();
            assertFalse("旧订阅入口必须已删除：" + name,
                    name.equals("subscribe") || name.equals("unsubscribe")
                            || name.equals("subscriberCount") || name.equals("forEachSubscriber"));
            assertFalse("旧单播入口必须已删除：" + name, name.equals("onSanTEChanged"));
        }
    }
}
