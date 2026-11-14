package com.shadowHunterRolesPlugin.internal.event;

import com.shadowHunterRolesPlugin.api.event.RoleClearEvent;
import com.shadowHunterRolesPlugin.api.event.RoleDeathEvent;
import com.shadowHunterRolesPlugin.api.event.RoleEvent;
import com.shadowHunterRolesPlugin.api.event.RoleSetEvent;
import org.bukkit.event.Event;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 钉住 {@link RoleEventBus} 的分发合约（2026-11-10 新增，随 API 订阅面 {@code RoleAPI#subscribeEvents} 落地）。
 *
 * <h2>为什么要有这条</h2>
 * 事件总线是外部插件消费本插件状态变化的唯一通道，而它的语义全是**行为承诺**：
 * 层级分发、快照迭代、异常隔离、订阅序即分发序——这些编译期都看不见，改坏一处
 * （比如换成迭代原表、或把异常放出去）下游要跑起来才炸。本测试把它们逐条钉死：
 * <ul>
 *   <li>订阅 {@code Event} 根 ⇒ 收到本插件发布的所有事件（= {@code subscribeEvents} 的承诺）；</li>
 *   <li>订阅具体类 / 中间基类 ⇒ 只收对应子树（层级匹配）；先子后父；</li>
 *   <li>快照迭代：回调中退订不影响本次分发、新订阅不插队本次；</li>
 *   <li>异常隔离：一个 handler 抛 Throwable 不饿死其余订阅者；</li>
 *   <li>退订幂等、unsubscribeAll 清空；null 入参拒绝。</li>
 * </ul>
 *
 * <h2>判据边界（如实申报）</h2>
 * 只测总线的纯数据结构语义，不起服务器——三个事件类虽继承 Bukkit {@code Event}，
 * 构造与 HandlerList 样板均为纯 Java（无静态服务器引用），离线可构造。
 * RoleManager / PlayerListener 侧的"发布点接线"（谁在何时发哪条）归运行期验证。
 */
public class RoleEventBusTest {

    private RoleEventBus bus;
    private UUID uuid;

    @Before
    public void freshBus() {
        bus = new RoleEventBus();
        uuid = UUID.randomUUID();
    }

    /** 订阅 Event 根 ⇒ 收到本插件发布的所有事件，且按发布顺序、按引用原样送达。 */
    @Test
    public void rootSubscriptionReceivesEveryEventInPublishOrder() {
        List<Event> received = new ArrayList<>();
        bus.subscribe(Event.class, received::add);

        RoleSetEvent set = new RoleSetEvent(uuid, "Mikomako");
        RoleClearEvent clear = new RoleClearEvent(uuid, "Mikomako");
        RoleDeathEvent death = new RoleDeathEvent(uuid, "Mikomako");
        bus.publish(set);
        bus.publish(clear);
        bus.publish(death);

        assertEquals(3, received.size());
        assertSame(set, received.get(0));
        assertSame(clear, received.get(1));
        assertSame(death, received.get(2));
    }

    /** 订阅具体类 ⇒ 只收该类事件；同类型先订阅先收到（订阅序即分发序）。 */
    @Test
    public void concreteSubscriptionReceivesOnlyThatTypeInSubscriptionOrder() {
        List<String> log = new ArrayList<>();
        bus.subscribe(RoleSetEvent.class, event -> log.add("first-subscribed"));
        bus.subscribe(RoleSetEvent.class, event -> log.add("second-subscribed"));
        //注意：不能在 handler 里 fail()——总线的异常隔离会把 AssertionError 吞成日志，
        //"没收到"必须用收集后断言来钉
        List<Event> deaths = new ArrayList<>();
        bus.subscribe(RoleDeathEvent.class, deaths::add);

        bus.publish(new RoleSetEvent(uuid, "Mikomako"));
        bus.publish(new RoleClearEvent(uuid, "Mikomako"));
        RoleDeathEvent death = new RoleDeathEvent(uuid, "Mikomako");
        bus.publish(death);

        assertEquals(List.of("first-subscribed", "second-subscribed"), log);
        //订阅 RoleDeathEvent 只收到 Death 那一条（Set / Clear 不送）
        assertEquals(1, deaths.size());
        assertSame(death, deaths.get(0));
    }

    /** 订阅中间基类 RoleEvent ⇒ 收全部子类事件；同一次发布先子（具体类层）后父（基类层）。 */
    @Test
    public void intermediateBaseSubscriptionReceivesSubclasses_childLayerFirst() {
        List<String> order = new ArrayList<>();
        bus.subscribe(RoleEvent.class, event -> order.add("base"));
        bus.subscribe(RoleDeathEvent.class, event -> order.add("concrete"));

        bus.publish(new RoleDeathEvent(uuid, "Mikomako"));

        assertEquals(List.of("concrete", "base"), order);
    }

    /** 快照迭代：回调中退订别人 ⇒ 本次仍送达（已入快照）、下次不再；回调中新订阅 ⇒ 本次不插队。 */
    @Test
    public void snapshotIterationSubscriptionChangesDoNotAffectCurrentDispatch() {
        List<String> log = new ArrayList<>();
        List<RoleEvent> earlySink = new ArrayList<>();
        RoleEventBus.Subscription early = bus.subscribe(RoleEvent.class, event -> {
            log.add("early");
            earlySink.add(event);
        });
        bus.subscribe(RoleEvent.class, event -> {
            log.add("second");
            early.unsubscribe();                                       // 退订别人：本次它仍会收到（已入快照）
            bus.subscribe(RoleEvent.class, e -> log.add("newcomer"));  // 新订阅：本次不送达
        });

        bus.publish(new RoleSetEvent(uuid, "Mikomako"));   // 快照 [early, second]
        bus.publish(new RoleSetEvent(uuid, "red"));        // 快照 [second, newcomer]

        assertEquals(List.of("early", "second", "second", "newcomer"), log);
        assertEquals(1, earlySink.size());                 // early 只收到第一条（退订后不再收到）
    }

    /** 异常隔离：一个 handler 抛 RuntimeException / Error 不饿死其余订阅者，publish 正常返回。 */
    @Test
    public void throwingHandlerDoesNotStarveOthers() {
        List<Event> received = new ArrayList<>();
        bus.subscribe(Event.class, event -> {
            throw new IllegalStateException("坏订阅者");
        });
        bus.subscribe(Event.class, received::add);
        bus.subscribe(Event.class, event -> {
            throw new AssertionError("Error 也应被隔离");
        });

        RoleSetEvent published = bus.publish(new RoleSetEvent(uuid, "Mikomako"));

        assertEquals(1, received.size());
        assertSame(published, received.get(0));
    }

    /** 退订幂等（重复调用无害）；退订后不再收到后续事件；可重新订阅。 */
    @Test
    public void unsubscribeIsIdempotentAndStopsFutureDelivery() {
        List<Event> received = new ArrayList<>();
        RoleEventBus.Subscription token = bus.subscribe(Event.class, received::add);

        bus.publish(new RoleSetEvent(uuid, "Mikomako"));
        token.unsubscribe();
        token.unsubscribe();                                        // 幂等
        assertTrue(token.isUnsubscribed());
        bus.publish(new RoleClearEvent(uuid, "Mikomako"));

        assertEquals(1, received.size());

        bus.subscribe(Event.class, received::add);                  // 重新订阅照常收到
        bus.publish(new RoleDeathEvent(uuid, "Mikomako"));
        assertEquals(2, received.size());
    }

    /** unsubscribeAll 清空全部订阅（主类 onDisable 的停用纪律），幂等。 */
    @Test
    public void unsubscribeAllClearsEverySubscription() {
        List<Event> received = new ArrayList<>();
        bus.subscribe(Event.class, received::add);
        bus.subscribe(RoleEvent.class, received::add);

        bus.unsubscribeAll();
        bus.unsubscribeAll();
        bus.publish(new RoleSetEvent(uuid, "Mikomako"));

        assertTrue(received.isEmpty());
    }

    /** 事件是只读载荷：getter 原样回构造值；publish 回传同一实例（按引用分发）。 */
    @Test
    public void eventsAreReadOnlyPayloadsAndPublishReturnsSameInstance() {
        UUID other = UUID.randomUUID();
        RoleSetEvent set = new RoleSetEvent(other, "Mikomako");
        assertEquals(other, set.getPlayerUuid());
        assertEquals("Mikomako", set.getRoleId());
        assertTrue(set instanceof Event);

        assertSame(set, bus.publish(set));
    }

    /** null 入参一律 IllegalArgumentException（fail fast，含 publish 侧）。 */
    @Test
    public void nullArgumentsAreRejected() {
        try {
            bus.subscribe(null, event -> { });
            fail("eventType 为 null 必须拒绝");
        } catch (IllegalArgumentException expected) { }
        try {
            bus.subscribe(Event.class, null);
            fail("handler 为 null 必须拒绝");
        } catch (IllegalArgumentException expected) { }
        try {
            bus.publish(null);
            fail("event 为 null 必须拒绝");
        } catch (IllegalArgumentException expected) { }
    }

    /** 无订阅者时 publish 为空操作（不抛、照常回传）。 */
    @Test
    public void publishWithoutSubscribersIsNoOp() {
        RoleDeathEvent death = new RoleDeathEvent(uuid, "Mikomako");
        assertSame(death, bus.publish(death));
    }
}
