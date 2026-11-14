package com.shadowHunterRolesPlugin.internal.event;

import org.bukkit.event.Event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 插件内事件总线（实例形态）：按事件类型订阅 / 发布，事件基类为 Bukkit 的 {@link Event}
 * （编译期强制），{@link Subscription} 令牌可退订。
 *
 * <p><b>与 Bukkit 事件系统的关系</b>：本总线是 {@code RoleAPI#subscribeEvents} 的内部实现件，
 * 外部插件不得 import 本类（经 API 订阅）。发布**不经** {@code Bukkit.getPluginManager().callEvent}
 * ⇒ 挂 {@code @EventHandler} 的 Bukkit 监听器收不到这里的事件；分发语义由本类自定义（见下）。
 *
 * <p><b>生命周期</b>：实例由主类在 {@code onEnable} 构造，随 RoleManager / RoleAPIImpl /
 * PlayerListener 构造注入；{@code onDisable} 调 {@link #unsubscribeAll()} ——
 * 订阅不跨本插件的 disable→enable 存活（下游须用新的 RoleAPI 重新订阅）。
 *
 * <p>分发语义（接口合约，调用方可以依赖）：
 * <ul>
 *   <li><b>层级分发</b>：按 {@code event.getClass()} 起沿继承链上行至 {@link Event}（含根）逐层查表
 *       —— 子类事件会同时送达其各父类直至根的订阅者，父类订阅者不收子类之外的事件；
 *       层间先子后父（最特殊优先），同层内按订阅顺序，
 *       同一处理器在链上多层订阅则按层各收一次；</li>
 *   <li><b>按订阅顺序同步分发</b>：同一事件类型内先订阅先收到；</li>
 *   <li><b>快照迭代</b>：发布入口对整条分发链的订阅列表一次性取快照，处理器在回调中
 *       订阅 / 退订不影响本次分发——含对尚未轮到的父类层的新订阅同样不送达本次事件，
 *       本次发布前已订阅者即使刚被退订也仍会收到；</li>
 *   <li><b>异常隔离</b>：单个处理器抛出任意 {@link Throwable} 不中断其余处理器
 *       （一个坏监听器不得饿死同事件的其他订阅者），以 SEVERE 记日志后继续分发；</li>
 *   <li><b>只读载荷</b>：本插件发布的事件均为不可变快照（无取消位）⇒ 分发不涉及
 *       "取消后中止"语义，发布方也不必在 publish 返回后检查任何标志。</li>
 * </ul>
 *
 * <p>线程合约：非线程安全（HashMap/ArrayList）——订阅、退订、发布须在同一线程内完成
 * （约定服务器主线程）。本类为纯 Java 数据结构，不触碰游戏状态，自身不做主线程断言。
 */
public final class RoleEventBus {

    /** 处理器异常日志走 JUL（类名 Logger），保持本类可在无服务器环境（单测）使用。 */
    private static final Logger LOGGER = Logger.getLogger(RoleEventBus.class.getName());

    /** eventType → 有序订阅列表（订阅序即分发序）；空列表随最后一次退订移除，防键累积。 */
    private final Map<Class<?>, List<Consumer<?>>> listeners = new HashMap<>();

    /**
     * 创建一个空总线（由主类在 onEnable 构造并注入各使用方；外部订阅入口是
     * {@code RoleAPI#subscribeEvents}，不是本类）。
     */
    public RoleEventBus() {
    }

    /**
     * 订阅事件。同一处理器可对同一事件类型重复订阅（按次收到、各自持独立令牌）。
     *
     * <p>handler 逆变（{@code Consumer<? super T>}，PECS）：接受键类型或其父类事件的
     * 处理器均可订阅——如 {@code RoleAPI#subscribeEvents} 的 {@code Consumer<Event>}
     * 即订阅在 {@code Event.class} 根上，与层级分发配套（订阅父类即收子类，
     * 处理器按父类视角消费）。
     *
     * @param eventType 事件类型（须为 {@link Event} 子类，编译期强制；层级匹配——
     *                   本类型及其所有子类的事件均会送达，见类注）
     * @param handler   事件处理器
     * @return 可退订的订阅令牌
     * @throws IllegalArgumentException eventType 或 handler 为 null
     */
    public <T extends Event> Subscription subscribe(final Class<T> eventType, final Consumer<? super T> handler) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType must not be null (RoleEventBus.subscribe)");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null (RoleEventBus.subscribe)");
        }
        final List<Consumer<?>> list = listeners.computeIfAbsent(eventType, k -> new ArrayList<>());
        list.add(handler);
        return new Subscription(eventType, handler);
    }

    /**
     * 发布事件：按 {@code event.getClass()} 起沿继承链层级匹配（先子后父，见类注），
     * 快照迭代同步分发，异常隔离见类注。无订阅者时为空操作。
     *
     * <p>回传同一实例：事件为一次性只读载体、按引用分发，返回值即入参本身（非拷贝）。
     * 事件载荷不可变 ⇒ 调用方无需检查任何"处理器是否改写了事件"的标志。
     *
     * @param event 事件实例（每次发布新建，勿复用）
     * @return 同一事件实例；类型参数保留编译期具体子类型，调用方免强转即可读子类载荷
     * @throws IllegalArgumentException event 为 null
     */
    public <T extends Event> T publish(final T event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null (RoleEventBus.publish)");
        }
        //发布入口对全链一次性取快照（先子后父拼平）：回调中对任一层——含尚未轮到的父类层——
        //订阅/退订均不影响本次分发，与类注快照合约一致
        final List<Consumer<?>> dispatchList = new ArrayList<>();
        for (Class<?> type = event.getClass(); Event.class.isAssignableFrom(type); type = type.getSuperclass()) {
            final List<Consumer<?>> handlers = listeners.get(type);
            if (handlers != null) {
                dispatchList.addAll(handlers);
            }
        }
        for (final Consumer<?> handler : dispatchList) {
            dispatch(event, handler);
        }
        return event;
    }

    /**
     * 单个处理器分发。强转安全性由 subscribe 的键控保证：订阅时以 {@code Class<T>}
     * （T 上界为 {@link Event}）为键、存入 {@code Consumer<? super T>}——
     * 处理器接受的事件类型为键类型或其父类，而层级分发只按事件继承链上的类型查表，
     * 事件运行时类型必为键类型的子类，故查出的处理器必然接受该事件实例。
     */
    @SuppressWarnings("unchecked")
    private void dispatch(final Event event, final Consumer<?> handler) {
        try {
            ((Consumer<Event>) handler).accept(event);
        } catch (final Throwable t) {
            //一个坏处理器不得饿死其余订阅者；Error 一并拦截以保证分发完整，交由日志暴露
            LOGGER.log(Level.SEVERE, "[ShadowHunterRoles:event-bus] handler for event "
                + event.getClass().getName() + " threw, continuing dispatch", t);
        }
    }

    /** 退订（幂等）。仅 {@link Subscription#unsubscribe()} 调用。 */
    private void unsubscribe(final Class<?> eventType, final Consumer<?> handler) {
        final List<Consumer<?>> list = listeners.get(eventType);
        if (list == null) {
            return;
        }
        list.remove(handler);
        if (list.isEmpty()) {
            listeners.remove(eventType);
        }
    }

    /**
     * 清空本实例全部订阅（幂等）。主类在 {@code onDisable} 调用：本插件停用后
     * 订阅者不该再被回调（下游若持有旧 RoleAPI 引用，其订阅随本调用一并失效）。
     */
    public void unsubscribeAll() {
        listeners.clear();
    }

    /**
     * 订阅令牌：绑定创建它的总线实例（非静态内部类，跨实例令牌无法成立），
     * {@link #unsubscribe()} 幂等，重复调用无害。
     */
    public final class Subscription {

        private final Class<?> eventType;
        private final Consumer<?> handler;
        private boolean unsubscribed = false;

        private Subscription(final Class<?> eventType, final Consumer<?> handler) {
            this.eventType = eventType;
            this.handler = handler;
        }

        /** 取消订阅；已取消再调用为空操作。 */
        public void unsubscribe() {
            if (!unsubscribed) {
                RoleEventBus.this.unsubscribe(eventType, handler);
                unsubscribed = true;
            }
        }

        /** 是否已取消。 */
        public boolean isUnsubscribed() {
            return unsubscribed;
        }
    }
}
