package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;

/**
 * 新侧组件基类（设计 §2 / §4.4）。**只允许出现 `svc` 字段 + 钩子方法 + `getComponent`** ——
 * 任何"顺手加个 helper"都属越界（那属于端口或组件私有方法的职责）。
 * <p>
 * <b>svc 注入（过渡期形态，队长批准，五条件）</b>：终态是构造期注入（`ComponentFactory`）；
 * 过渡期由容器在创建组件后**立刻** `bind(...)`：
 * ① 容器在 `supplier.get()` 之后、任何注册/钩子（含 `awake`）之前调用；
 * ② 只允许一次（重复调用、或进入生命周期之后再调用 → 抛异常）；
 * ③ 未绑定时经 {@link #svc()} 访问 → 抛 `IllegalStateException`（禁止静默 null）；
 * ④ 组件构造点唯一（容器内单一创建路径），使 bind 不可能被遗漏；
 * ⑤ 终态收尾必须切回构造期注入并删除 `bind`（阶段 4 收尾批次，见交付小结的待办）。
 */
public abstract class RoleComponent {

    private final String id;

    private ComponentServices svc;
    private boolean lifecycleStarted;

    protected RoleComponent(String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component id cannot be null or empty.");
        }
        this.id = id;
    }

    public final String getId() {
        return id;
    }

    // ───────────── 容器专用（条件 ①②④） ─────────────

    /** ★容器专用：仅单一创建路径在创建组件后立刻调用。 */
    public final void bind(ComponentServices services) {
        if (services == null) {
            throw new NullPointerException("ComponentServices");
        }
        if (this.svc != null) {
            throw new IllegalStateException("ComponentServices already bound for component '" + id + "'.");
        }
        if (this.lifecycleStarted) {
            throw new IllegalStateException("bind() is only allowed before the component lifecycle starts (component '" + id + "').");
        }
        this.svc = services;
    }

    /** ★容器专用：进入 `awake` 阶段前调用；此后 `bind` 一律抛异常（条件 ②）。 */
    public final void markLifecycleStarted() {
        this.lifecycleStarted = true;
    }

    /** 受保护访问器（条件 ③）：未绑定时抛异常，绝不静默返回 null。 */
    protected final ComponentServices svc() {
        ComponentServices current = this.svc;
        if (current == null) {
            throw new IllegalStateException("Component '" + id + "' is not bound to ComponentServices yet.");
        }
        return current;
    }

    /** 取本角色实例内的另一个组件（按具体类优先；未注册 → null，冻结前调用 → 抛异常）。 */
    protected final <T extends RoleComponent> T getComponent(Class<T> type) {
        return svc().components().get(type);
    }

    // ───────────── 生命周期：框架按固定顺序无条件广播 ─────────────

    /** 装配阶段：只解析跨组件依赖并缓存引用。幂等；不得改动任何玩家可见状态。 */
    public void awake() {
    }

    /** 开始生效：初始化数据、发放装备、登记定时器。 */
    public void start() {
    }

    /** 停止生效：与 start 严格对称。返回后框架自动回收本组件登记的资源。 */
    public void stop() {
    }

    // ───────────── 每 tick ─────────────

    /** 20 Hz。禁止阻塞、禁止直接写热键栏（渲染由框架负责）。 */
    public void update() {
    }

    // ───────────── 领域事件 ─────────────

    /** 只在 SanTE **真变化**时派发（pre == now 不派发）。 */
    public void onSanTEChange(int pre, int now) {
    }
}
