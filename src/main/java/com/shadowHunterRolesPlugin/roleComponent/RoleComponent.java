package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

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
 * ⑤ "先创建再注入"的过渡方法已删除 ⇒ 注入只可能发生在构造期。
 */
public abstract class RoleComponent {

    // ★ 嵌套类型的一条继承约束（JLS）：**类不得实现自己声明的嵌套接口**（循环继承 ✗）⇒
    //  凡需要由子类实现的接口，一律声明在**本基类**（子类写成 `extends RoleComponent implements RoleComponent.X` ✓）。

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

    /**
     * **按类型取同实例内的另一个组件**（基类便利口，转发到容器的查取入口）。
     *
     * <p>为什么放在基类：这是**"我有服务集 ⇒ 我能取组件"**的同义表达，**不含任何具体组件的语言**
     * （类型由调用方给）⇒ 与"基类不认识子类"的纪律不冲突 ✓。
     * 早先各组件各自写 `svc().components().get(X.class)`，被动组件连 `svc()` 都够不到（它是 protected）
     * ⇒ 现在统一走本口。
     *
     * <p>未命中 / 服务集不可用 ⇒ {@code null}（**不抛**：调用方普遍按"可缺失"处理）。
     */
    protected final <T> T findComponent(Class<T> type) {
        if (this.svc == null || this.svc.components() == null) {
            return null;
        }
        return this.svc.components().get(type);
    }

    /**
     * 取本角色实例内的另一个组件（**按类型**）。
     * <p><b>语义 = 添加顺序第一个满足可赋值性者</b>（用父类/接口查询会命中子类/实现类实例）；
     * 未注册 → {@code null}；冻结前调用 → 抛 {@code IllegalStateException}。要拿**全部**符合者请用
     * {@code svc().components().getAll(type)}。
     * 同类措辞已在 `core/ports/ComponentLookup` 与 `core/component/ComponentRegistry`（**两者均为现役位置**）改正，
     * 本处（组件侧**唯一取用入口**）是最后一块（`HotbarSpec` 家族的作废措辞亦已同法处理）。
     */
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

 //★ **六个"框架通用视图"已整体删除**（它们让基类认识热键栏 / 能量 / SanTE / 生命——那些是具体组件的语言）：
 //   requestRepaint()      → 渲染组件自己的 `markDirty()`（消费者直接持有渲染组件）
 //   readCurrentEnergy()   → 能量组件的 `current()`
 //   writeCurrentEnergy()  → 能量组件的 `set(int)`
 //   readCurrentSanTE()    → SanTE 组件的 `current()`（原基类那份**生产者 0**，已直删）
 //   writeCurrentSanTE()   → SanTE 组件的 `set(int)`（同上）
 //   heal(double)          → 生命组件的 `heal(double)`（原基类那份**消费者 0**）
 //  ⇒ 本基类现在**不含任何子类/具体组件的语言** ✓
    /** 停止生效：与 start 严格对称。返回后框架自动回收本组件登记的资源。 */
    public void stop() {
    }

    // ───────────── 每 tick ─────────────

    /** 20 Hz。禁止阻塞、禁止直接写热键栏（渲染由框架负责）。 */
    public void update() {
    }

    // ───────────── 领域事件 ─────────────

    //本基类**不再**声明 `onSanTEChange(int pre, int now)` ✗ ——
    //  **现行形态**：关心者向 `SanTEComponent` **添加监听**（`addListener` + JDK `Consumer`）✓
    //  —— SanTE 的家是组件 ⇒ **不得再为它新增能力接口** ✗。
    //  简言之：SanTE 的**真值持有者**早已是 SanTEComponent ⇒ 变更通知不该挂在**所有**组件的基类上。

    // ───────────── 装配期描述符 ─────────────

    /**
     * **装配期描述符根类型**（不含 kind）：把"这个组件怎么造"从**工厂 + 值的哨兵**改成
     * **一个有类型的声明**。
     * <p>
     * <b>职责</b>：
     * <ul>
     *   <li>{@link #descriptorLabel()} —— **诊断标签**（**不是行为分支**：没有任何行为按它分叉，
     *       只出现在装配期异常的文案里，取值如 "Skill" / "MainWeapon" / "Passive"）；</li>
     *   <li>{@link #freeze()} —— 装配期冻结：产出**不可变快照** {@link Snapshot}。此后描述符自身也拒绝再改
     *       （`bindId` 之类一律抛异常）⇒ 同一个描述符实例被两个角色共享时不可能被串改；</li>
     *   <li>{@link #create(String, ComponentServices)} —— 抽象创建：由**具体描述符**决定造哪个类。</li>
     * </ul>
     * <b>★ 本根类型不含任何「栏位」语言</b>：栏位归**带栏位的那一支描述符**
     * （{@code roleComponent/builtin/hotbar/HotbarSpecification}，它才有 {@code setSlot}），
     * 运行期仲裁归**渲染组件**（{@code HotbarRenderComponent#registerSlot}）
     * ⇒ "被动等组件不占栏位"是**编译期事实**（它们的描述符没有那个口），不靠装配点自觉 ✓
     * <p><b>kind 已删</b>：kind 枚举（SKILL / MAIN_WEAPON / PASSIVE）与构造参数一起删除；
     * 表现面不再自述种类、行为分支也不再读它（热键栏物品完全由组件的 {@code buildItem()} 控制）。
     * <p>
     * <b>命名</b>：按本工程的 JavaBean 口径（设计 §4.3），不写成 record；访问器名沿用
     * {@code descriptorLabel()} 的口径。
     */
    public abstract static class Specification<T extends RoleComponent> {

        /**
         * **诊断标签**（**不是行为分支**）：旧 `kind` 的"可读性"由本字段承接 —— 它**只**用于装配期
         * 异常文案（保证文案逐字稳定），**不含任何枚举语义**、也没有任何行为按它分叉。
         */
        private final String descriptorLabel;

 //★ **本基类不持有栏位、也不声明栏位读口** —— 栏位语言整体归 `HotbarSpecification` ✓

        /** 冻结位：装配期 {@link #freeze()} 之后禁止再改（防止被共享后被串改）。 */
        private boolean frozen;

        /**
         * 装配期绑定的**注册 id**（{@link #bindId(String)} 写入；未绑定 ⇒ {@code null}）。
         * <p>与栏位同属"**必须由装配器设置**的参数"：组件自带的描述符不知道自己的注册 id，
         * 若不绑定，任何读 id 的代码都会拿到 {@code null}。
         */
        private String boundId;

        /**
         * **必需的依赖类型**：{@link #requires(Class)} 写入；装配期由
         * {@code core/Role#verifyDependencies()} 检查 —— 缺任一 ⇒ 抛 {@link ComponentDependencyException}
         * ⇒ 该角色**不注册**。
         */
        private final List<Class<? extends RoleComponent>> requiredTypes = new ArrayList<>();

        /**
         * **可选的依赖类型**：{@link #requiresOptional(Class)} 写入；缺失**不报错**（组件运行期自行处理
         * 查不到的情况）。可选与必需**不得声明同一个类型**（那是自相矛盾的声明 ⇒ 装配期报错）。
         */
        private final List<Class<? extends RoleComponent>> optionalTypes = new ArrayList<>();

        protected Specification(String descriptorLabel) {
            if (descriptorLabel == null || descriptorLabel.trim().isEmpty()) {
                throw new IllegalArgumentException("Component descriptor label cannot be null or empty.");
            }
            this.descriptorLabel = descriptorLabel;
        }

        /** **诊断标签**（只出现在装配期异常文案里；没有任何行为分支读它）。 */
        public final String descriptorLabel() {
            return descriptorLabel;
        }

        /**
         * **声明"我需要同角色里还有某个组件"**。
         * <p><b>按类型声明</b>（不是按 id）：id 属于注册处，类型才是"我需要什么样的能力提供者"。
         * <p><b>语义</b>：装配期检查时，本组件**自己不算**提供者（用户原话是"检查自己需要的依赖（**其他组件**）"）
         * ⇒ 至少要有**另一个**组件的"提供类型"可赋值给这里声明的类型，否则视为缺依赖。
         * <p><b>失败形态</b>：缺任一必需依赖 ⇒ {@code core/Role#verifyDependencies()} 抛
         * {@link ComponentDependencyException} ⇒ {@code registry/RoleLoader#loadInto} 记 {@code SEVERE}
         * 并**跳过该角色**（不注册、不进游戏）。
         * <p><b>声明示例</b>（写在组件自己的嵌套 {@code Specification} 构造器里）：
         * <pre>{@code
         * public static final class Specification extends Skill.Specification<ExampleSkill> {
         *     public Specification() {
         *         super(Component.text("示例技能"), Component.text("示例描述"), 100, 0, Material.STONE);
         *         requires(EnergyComponent.class);              // 必需：没有能量组件就不许装配
         *         requiresOptional(RepaintRequirement.class);   // 可选：没有也不报错，运行期自查
         *     }
         *     @Override public ExampleSkill create(String id, ComponentServices services) { ... }
         * }
         * }</pre>
         * <p><b>为什么不在描述符里"顺手查一遍"</b>：描述符是**装配期对象**、拿不到运行期状态；
         * 依赖是**同角色其它组件**的有无问题 ⇒ 只能由框架在装配期（拿到整张组件表之后）统一检查。
         * <p><b>声明面的边界（如实申报）</b>：本方法只在**描述符**上；旧的无描述符装配入口
         * **已删除** ⇒ 现在**所有**组件都经描述符装配，因此**都有**声明面（被动也已各带一个嵌套描述符）。
         *
         * <p><b>一次只声明一个类型</b>：需要多个依赖时**链式调用** ——
         * {@code requires(A.class).requires(B.class)} ✓；重复声明同一类型是**幂等**的 ✓。
         *
         * @param type 必需的依赖组件类型（**不得为 null**）
         */
        public final Specification<T> requires(Class<? extends RoleComponent> type) {
            addDependencyType(requiredTypes, "requires", type);
            return this;
        }

        /**
         * **声明"有的话更好，没有也不报错"的依赖**（可选依赖）：缺失**不**阻止装配。
         * <p>与 {@link #requires(Class)} 的唯一差别 = 缺失时的行为：必需 ⇒ 抛异常阻止注册；可选 ⇒ 放行。
         * <p>同一个类型**不得**既必需又可选择（自相矛盾的声明 ⇒ {@code freeze()} 时抛
         * {@link IllegalStateException}）。
         *
         * <p><b>一次只声明一个类型</b>：需要多个可选依赖时**链式调用** ——
         * {@code requiresOptional(A.class).requiresOptional(B.class)} ✓；重复声明同一类型是**幂等**的 ✓。
         *
         * @param type 可选的依赖组件类型（**不得为 null**）
         */
        public final Specification<T> requiresOptional(Class<? extends RoleComponent> type) {
            addDependencyType(optionalTypes, "requiresOptional", type);
            return this;
        }

        /** 本描述符声明的**必需**依赖类型（不可变副本；顺序 = 声明顺序）。 */
        public final List<Class<? extends RoleComponent>> requiredTypes() {
            return List.copyOf(requiredTypes);
        }

        /** 本描述符声明的**可选**依赖类型（不可变副本；顺序 = 声明顺序）。 */
        public final List<Class<? extends RoleComponent>> optionalTypes() {
            return List.copyOf(optionalTypes);
        }

        /**
         * **本组件提供什么类型**（依赖检查的"供给面"）：默认由**泛型实参**推导 ——
         * 例如 {@code class EnergyComponent.Specification extends RoleComponent.Specification<EnergyComponent>}
         * ⇒ 返回 {@code EnergyComponent.class}。
         * <p><b>为什么要可覆写</b>：三个家族描述符（{@code Skill.Specification} /
         * {@code MainWeapon.Specification} / {@code PassiveSkill.Specification}）的泛型实参是**家族基类**，
         * 推导结果因此是族级（{@code Skill.class} 等）；若某个组件希望被"按**具体类**依赖"，
         * 覆写本方法返回自己的具体类即可（{@code requires(SomeConcreteSkill.class)} 就能命中它）。
         * <p>推导不到时返回 {@link RoleComponent#getClass()} 的上界 —— 即 {@code RoleComponent.class}
         * （"我只声明自己是组件"），这**不会**满足任何更具体的依赖声明。
         */
        public Class<? extends RoleComponent> providedType() {
            return deriveProvidedType();
        }

        /** 泛型实参推导：沿超类链找第一个 `...Specification<X>` 实参（X 必须是组件类型）。 */
        @SuppressWarnings("unchecked")
        private Class<? extends RoleComponent> deriveProvidedType() {
            Class<?> current = getClass();
            while (current != null && current != Object.class) {
                Type superType = current.getGenericSuperclass();
                if (superType instanceof ParameterizedType parameterized) {
                    Type[] arguments = parameterized.getActualTypeArguments();
                    if (arguments.length == 1 && arguments[0] instanceof Class<?> raw
                            && RoleComponent.class.isAssignableFrom(raw)) {
                        return (Class<? extends RoleComponent>) raw;
                    }
                }
                current = current.getSuperclass();
            }
            return RoleComponent.class;
        }

        /** 声明写入（两处共用）：null 一律抛，重复声明幂等。 */
        private static void addDependencyType(List<Class<? extends RoleComponent>> target, String entry,
                                             Class<? extends RoleComponent> type) {
            if (type == null) {
                throw new IllegalArgumentException(entry + "(...) must not be null.");
            }
            if (!target.contains(type)) {
                target.add(type);
            }
        }

 //★ **栏位语言已从本基类彻底移除**：持有者 = 带栏位的那一支描述符
 //  （`builtin/hotbar/HotbarSpecification#slotOrNull()`），仲裁者 = 渲染组件
 //  （`HotbarRenderComponent#registerSlot`）⇒ 基类描述符**一个字都不提栏位** ✓

        /**
         * **冻结校验**（供**子类**在自己的可写口里复用）：已冻结 ⇒ 抛异常。
         * <p>冻结位是本类的私有状态 ⇒ 子类需要这个受保护读口，而不是各自重写一遍文案。
         */
        protected final void ensureMutable() {
            if (frozen) {
                throw new IllegalStateException(
                        "Component specification of kind " + descriptorLabel + " is frozen and cannot be changed.");
            }
        }

        /** 装配器绑定注册 id（与"栏位"同族：都是"必须由装配器设置"的参数）。 */
        public final void bindId(String id) {
            ensureMutable();
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Component ID cannot be null or empty.");
            }
            if (boundId != null && !boundId.equals(id)) {
                throw new IllegalStateException(
                        "Component specification of kind " + descriptorLabel + " is already bound to '" + boundId
                                + "'; refusing to rebind it to '" + id + "'.");
            }
            this.boundId = id;
        }

        /** 装配期绑定的注册 id；**未绑定 ⇒ {@code null}**（装配入口保证已装配的描述符都已绑定）。 */
        public final String boundId() {
            return boundId;
        }

        /**
         * **装配期冻结**：返回本描述符的**不可变快照**（栏位（可有可无）+ 工厂 + **依赖声明** + **提供类型**），
         * 并把本实例置为只读。
         * <p>装配入口 {@code Role.Builder.addComponent(String, Specification)} 只使用这份快照
         * ⇒ 角色模板**不持有描述符对象**，两个角色共用一个描述符实例也互不影响。
         * <p><b>栏位不进快照</b>：栏位归「带栏位那一支描述符」与渲染组件，基类快照不含它 ✓
         * "带栏位必填"的 fail-fast 由**带栏位那一支描述符**自己做（{@code HotbarSpecification}）✓。
         * <p><b>依赖声明的自检</b>：同一个类型不得**既必需又可选择** ⇒ 抛
         * {@link IllegalStateException}（自相矛盾的声明必须在装配期就喊出来，而不是"看哪条先被读到"）。
         */
        /**
         * **冻结前的子类自检**（默认空）：带栏位那一支描述符在此做"必须有栏位"的 fail-fast。
         * <p>为什么是钩子而不是覆写 {@link #freeze()}：freeze 是 `final` 的**单一实现点**
         * （快照形状必须唯一）⇒ 子类只能插校验，不能改形状 ✓。
         */
        protected void validateForFreeze() {
        }

        public final Snapshot freeze() {
            validateForFreeze();
            for (Class<? extends RoleComponent> type : optionalTypes) {
                if (requiredTypes.contains(type)) {
                    throw new IllegalStateException(
                            "Component specification of kind " + descriptorLabel + " declares '" + type.getName()
                                    + "' as both required and optional; pick one.");
                }
            }
            this.frozen = true;
            return new Snapshot(descriptorLabel, this::create, providedType(), requiredTypes, optionalTypes);
        }

        /** 抽象创建：由具体描述符决定造哪个组件类。 */
        public abstract T create(String id, ComponentServices services);

        /**
         * 装配期不可变快照：**装配表唯一持有的形态**（栏位（可有可无）+ 工厂 + 依赖声明 + 提供类型）。
         * 装配期不可变快照：**装配表唯一持有的形态**（工厂 + 依赖声明 + 提供类型）。
         */
        public static final class Snapshot {

            /** 诊断标签（与 {@link Specification#descriptorLabel()} 同源；只出现在异常文案里）。 */
            private final String descriptorLabel;
            private final ComponentFactory<? extends RoleComponent> factory;
            /** 本组件**提供**的类型（依赖检查的供给面）。 */
            private final Class<? extends RoleComponent> providedType;
            /** **必需**依赖（缺任一 ⇒ 抛 {@link ComponentDependencyException}）。 */
            private final List<Class<? extends RoleComponent>> requiredTypes;
            /** **可选**依赖（缺失不报错）。 */
            private final List<Class<? extends RoleComponent>> optionalTypes;

            private Snapshot(String descriptorLabel,
                             ComponentFactory<? extends RoleComponent> factory,
                             Class<? extends RoleComponent> providedType,
                             List<Class<? extends RoleComponent>> requiredTypes,
                             List<Class<? extends RoleComponent>> optionalTypes) {
                this.descriptorLabel = descriptorLabel;
                this.factory = factory;
                this.providedType = providedType;
                this.requiredTypes = List.copyOf(requiredTypes);
                this.optionalTypes = List.copyOf(optionalTypes);
            }

            /** **诊断标签**（只出现在装配期异常文案里；没有任何行为分支读它）。 */
            public String getDescriptorLabel() {
                return descriptorLabel;
            }

            public ComponentFactory<? extends RoleComponent> getFactory() {
                return factory;
            }

            /** 本组件**提供**的类型（依赖检查按它匹配）。 */
            public Class<? extends RoleComponent> getProvidedType() {
                return providedType;
            }

            /** 本组件声明的**必需**依赖类型（不可变副本）。 */
            public List<Class<? extends RoleComponent>> getRequiredTypes() {
                return requiredTypes;
            }

            /** 本组件声明的**可选**依赖类型（不可变副本）。 */
            public List<Class<? extends RoleComponent>> getOptionalTypes() {
                return optionalTypes;
            }
        }
    }
}
