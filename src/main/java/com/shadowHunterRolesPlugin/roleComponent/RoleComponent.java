package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
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

    // ───────────── 装配期描述符（阶段 7 · A 步） ─────────────

    /**
     * **装配期描述符根类型**（阶段 7 · A 步骨架）：把"这个组件怎么造"与"它占不占热键栏"从
     * **工厂 + 值的哨兵**（旧：`slot = -1`）改成**一个有类型的声明**。
     * <p>
     * <b>职责</b>：
     * <ul>
     *   <li>{@link #kind()} —— 权威种类，**在 {@link #create(String, ComponentServices)} 之前即可读**
     *       （服务集在组件被构造**之前**就要它：`RoleInstance.createServices(id, kind)`）；</li>
     *   <li>{@link #hasSlot()} / {@link #slot()} —— 占不占栏位。**"不占栏位"是栏位的缺失**（本类型内部
     *       用可空的 `Integer` 表达），**不是 `-1` 哨兵**；无栏位时 {@link #slot()} **抛异常**而不是返回哨兵；</li>
     *   <li>{@link #freeze()} —— 装配期冻结：产出**不可变快照** {@link Snapshot}。此后描述符自身也拒绝再改
     *       （`setSlot` 之类一律抛异常）⇒ 同一个描述符实例被两个角色共享时不可能被串改；</li>
     *   <li>{@link #create(String, ComponentServices)} —— 抽象创建：由**具体描述符**决定造哪个类。</li>
     * </ul>
     * <b>规则进类型</b>（阶段 7 · A 步）：带栏位的分支是 {@code core/hotbar/HotbarSpecification}
     * （它有 {@code setSlot}）；被动描述符 {@code PassiveSkill.Specification} **继承本根类型**、
     * 因此**没有** {@code setSlot} —— "被动不占栏位"于是成为**编译期事实**，不再靠装配点自觉。
     * <p>
     * <b>命名</b>：按本工程的 JavaBean 口径（设计 §4.3），不写成 record；访问器名沿用
     * {@code kind()} / {@code slot()} / {@code hasSlot()} 与既有 {@code HotbarSpec.kind()} 一致。
     */
    public abstract static class Specification<T extends RoleComponent> {

        /** 权威种类：**必须在 create() 之前可读**。 */
        private final ItemKind kind;

        /** 栏位；{@code null} = 不占栏位（**类型的缺失，不是 -1 哨兵**）。 */
        private Integer slot;

        /** 冻结位：装配期 {@link #freeze()} 之后禁止再改（防止被共享后被串改）。 */
        private boolean frozen;

        protected Specification(ItemKind kind) {
            if (kind == null) {
                throw new IllegalArgumentException("Component kind cannot be null.");
            }
            this.kind = kind;
        }

        /** **权威 kind**（行为分支的唯一来源由注册处承载，这里只作装配期分派用）。 */
        public final ItemKind kind() {
            return kind;
        }

        /** 占不占热键栏；{@code false} = 不占（不进槽位表）。 */
        public final boolean hasSlot() {
            return slot != null;
        }

        /** 栏位（0..8）；**未设栏位 ⇒ 抛异常**（绝不回落 `-1` 哨兵）。 */
        public final int slot() {
            if (slot == null) {
                throw new IllegalStateException(
                        "Component specification of kind " + kind + " has no slot assigned.");
            }
            return slot;
        }

        /**
         * 装配器设置栏位（**唯一一处**会在装配期写入的参数；其余表现字段由组件自己的描述符声明默认值）。
         * 非法值、重复设置、冻结后设置一律抛异常。
         */
        protected final void assignSlot(int slot) {
            if (frozen) {
                throw new IllegalStateException(
                        "Component specification of kind " + kind + " is frozen and cannot be changed.");
            }
            if (slot < 0 || slot > 8) {
                throw new IllegalArgumentException("Slot must be between 0 and 8, got: " + slot);
            }
            if (this.slot != null && this.slot != slot) {
                throw new IllegalStateException(
                        "Slot already assigned to " + this.slot + " for kind " + kind + "; refusing to move it to " + slot + ".");
            }
            this.slot = slot;
        }

        /**
         * **装配期冻结**：返回本描述符的**不可变快照**（`kind` + 栏位（可有可无）+ 工厂三元组），
         * 并把本实例置为只读。
         * <p>装配入口 {@code Role.Builder.addComponent(String, Specification)} 只使用这份快照
         * ⇒ 角色模板**不持有描述符对象**，两个角色共用一个描述符实例也互不影响。
         * <p><b>带栏位必填</b>：子类若声明"本类型必须有栏位"（{@link #requiresSlot()}），则未设栏位时
         * **在此抛异常** —— 不占栏位必须由**类型**表达（用无栏位的描述符），不得静默降级。
         */
        public final Snapshot freeze() {
            if (requiresSlot() && slot == null) {
                throw new IllegalStateException(
                        "A hotbar specification of kind " + kind + " must be given a slot (setSlot) before assembly.");
            }
            this.frozen = true;
            return new Snapshot(kind, slot, this::create);
        }

        /** 本类型的描述符是否**必须**有栏位（默认 `false`；带栏位分支覆写为 `true`）。 */
        protected boolean requiresSlot() {
            return false;
        }

        /** 抽象创建：由具体描述符决定造哪个组件类。 */
        public abstract T create(String id, ComponentServices services);

        /**
         * 装配期不可变快照：**装配表唯一持有的形态**（`kind` + 栏位（可有可无）+ 工厂）。
         * 字段全 `final`、无 setter ⇒ 拿不到可变面。
         */
        public static final class Snapshot {

            private final ItemKind kind;
            private final Integer slot;
            private final ComponentFactory<? extends RoleComponent> factory;

            private Snapshot(ItemKind kind, Integer slot,
                             ComponentFactory<? extends RoleComponent> factory) {
                this.kind = kind;
                this.slot = slot;
                this.factory = factory;
            }

            public ItemKind getKind() {
                return kind;
            }

            public boolean hasSlot() {
                return slot != null;
            }

            /** 栏位（0..8）；**无栏位 ⇒ 抛异常**（本形态不再有 `-1` 哨兵）。 */
            public int getSlot() {
                if (slot == null) {
                    throw new IllegalStateException(
                            "Component '" + kind + "' does not occupy a hotbar slot.");
                }
                return slot;
            }

            public ComponentFactory<? extends RoleComponent> getFactory() {
                return factory;
            }
        }
    }
}
