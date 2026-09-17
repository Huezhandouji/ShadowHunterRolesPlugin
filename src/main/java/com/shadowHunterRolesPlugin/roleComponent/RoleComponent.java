package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;

/**
 * 新侧组件基类（设计 §2 / §4.4）。**只允许出现 `svc` 字段 + 钩子方法 + `getComponent`** ——
 * 任何"顺手加个 helper"都属越界（那属于端口或组件私有方法的职责）。
 * <p>
 * <b>svc 注入（终态形态：构造期注入，五条件）</b>：容器经 {@link ComponentFactory} 在**创建组件时**
 * 就把服务集交给本构造函数，**没有"创建后再注入"的中间态**：
 * ① 注入必然是构造的一部分，因此必然发生在任何注册/钩子（含 `awake()`）之前；
 * ② 服务集由 `final` 字段承接 ⇒ 只可能注入一次，重复注入在类型上不可表达；
 * ③ {@link #svc()} 保留"未注入即抛"的防御语义（禁止静默 null）；
 * ④ 组件构造点唯一（容器内单一创建路径）⇒ id 与服务集只可能成对产生；
 * ⑤ 过渡期的"先创建再注入"方法已在阶段 4 收尾批次删除（见交付小结的待办）。
 */
public abstract class RoleComponent {

    private final String id;
    private final ComponentServices svc;

    protected RoleComponent(String id, ComponentServices services) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component id cannot be null or empty.");
        }
        if (services == null) {
            throw new NullPointerException("ComponentServices");
        }
        this.id = id;
        this.svc = services;
    }

    public final String getId() {
        return id;
    }

    /** 受保护访问器（条件 ③）：服务集在构造期注入，这里仍保留防御性检查，绝不静默返回 null。 */
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