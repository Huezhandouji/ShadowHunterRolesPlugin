package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 新侧组件基类。只允许出现 `svc` 字段 + 钩子方法 + `getComponent` ——
 * 任何"顺手加个 helper"都属越界（那属于端口或组件私有方法的职责）。
 * <p>
 * svc 注入（构造期注入）四条：容器经 {@link ComponentFactory} 在创建组件时就把服务集交给本构造函数，
 * 没有"创建后再注入"的中间态 ——
 * ① 注入必然是构造的一部分，因此必然发生在任何注册 / 钩子（含 `awake()`）之前；
 * ② 服务集由 `final` 字段承接，只可能注入一次，重复注入在类型上不可表达；
 * ③ {@link #svc()} 保留"未注入即抛"的防御语义（禁止静默 null）；
 * ④ 组件构造点唯一（容器内单一创建路径），id 与服务集只可能成对产生。
 */
public abstract class RoleComponent {

    // 嵌套类型的一条继承约束（JLS）：类不得实现自己声明的嵌套接口（循环继承），
    //  因此凡需要由子类实现的接口，一律声明在本基类
    //  （子类写成 `extends RoleComponent implements RoleComponent.X`）。

    private final String id;
    private final ComponentServicesPort svc;

    protected RoleComponent(String id, ComponentServicesPort services) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("Component id cannot be null or empty.");
        }
        if (services == null) {
            throw new NullPointerException("ComponentServicesPort");
        }
        this.id = id;
        this.svc = services;
    }

    public final String getId() {
        return id;
    }

    /** 受保护访问器（条件 ③）：服务集在构造期注入，这里仍保留防御性检查，绝不静默返回 null。 */
    protected final ComponentServicesPort svc() {
        ComponentServicesPort current = this.svc;
        if (current == null) {
            throw new IllegalStateException("Component '" + id + "' is not bound to ComponentServicesPort yet.");
        }
        return current;
    }

    /**
     * 按类型取同实例内的另一个组件（基类便利口，转发到容器的查取入口）。
     *
     * <p>放在基类的理由：这是"我有服务集，因此我能取组件"的同义表达，不含任何具体组件的语言
     * （类型由调用方给），与"基类不认识子类"的纪律不冲突；被动组件够不到 protected 的 {@code svc()}，
     * 统一走本口。
     *
     * <p>未命中 / 服务集不可用时返回 {@code null}（不抛：调用方普遍按"可缺失"处理）。
     */
    protected final <T> T findComponent(Class<T> type) {
        if (this.svc == null || this.svc.components() == null) {
            return null;
        }
        return this.svc.components().get(type);
    }

    /**
     * 取本角色实例内的另一个组件（按类型）。
     * <p>语义 = 添加顺序第一个满足可赋值性者（用父类 / 接口查询会命中子类 / 实现类实例）；
     * 未注册 → {@code null}；冻结前调用 → 抛 {@code IllegalStateException}。要拿全部符合者用
     * {@code svc().components().getAll(type)}。
     * <p>本处是组件侧的唯一取用入口，口径与 {@code core/ports/ComponentLookupPort}、
     * {@code core/component/ComponentRegistry} 一致。
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

 // 本基类不得含任何子类 / 具体组件的语言（热键栏 / 能量 / SanTE / 生命各有其组件）：
 //  重绘请求走渲染组件的 `requestRepaint()`，能量与 SanTE 的读写各自的 `current()` / `set(int)`，
 //  治疗走生命组件的 `heal(double)` —— 本基类一个通用视图都不设。
    /** 停止生效：与 start 严格对称。返回后框架自动回收本组件登记的资源。 */
    public void stop() {
    }

    // ───────────── 每 tick ─────────────

    /** 20 Hz。禁止阻塞、禁止直接写热键栏（渲染由框架负责）。 */
    public void update() {
    }

    // ───────────── 领域事件 ─────────────

    // SanTE 的变更通知不挂在本基类上：真值持有者是 `SanTEComponent`，关心者向它添加监听
    //  （`addListener` + JDK `Consumer`）；也不得为它新增能力接口（SanTE 的家是组件）。

    // ───────────── 装配期描述符 ─────────────

    /**
     * 装配期描述符根类型（不含 kind）：把"这个组件怎么造"从工厂 + 值的哨兵改成一个有类型的声明。
     * <p>
     * 职责：
     * <ul>
     *   <li>{@link #descriptorLabel()} —— 诊断标签（不是行为分支：没有任何行为按它分叉，
     *       只出现在装配期异常的文案里，取值如 "Skill" / "MainWeapon" / "Passive"）；</li>
     *   <li>{@link #freeze()} —— 装配期冻结：产出不可变快照 {@link Snapshot}。此后描述符自身也拒绝再改
     *       （`bindId` 之类一律抛异常），因此同一个描述符实例被两个角色共享时不可能被串改；</li>
     *   <li>{@link #create(String, ComponentServicesPort)} —— 抽象创建：由具体描述符决定造哪个类。</li>
     * </ul>
     * 本根类型不含任何「栏位」语言：栏位归带栏位的那一支描述符
     * （{@code roleComponent/builtin/hotbar/HotbarSpecification}，它才有 {@code setSlot}），
     * 运行期仲裁归渲染组件（{@code HotbarRenderComponent#registerSlot}），
     * 因此"被动等组件不占栏位"是编译期事实（它们的描述符没有那个口），不靠装配点自觉。
     * <p>本类型不自述种类：行为分支不读种类，热键栏物品完全由组件的 {@code buildItem()} 控制。
     * <p>命名按本工程的 JavaBean 口径，不写成 record；访问器名沿用 {@code descriptorLabel()} 的口径。
     */
    public abstract static class Specification<T extends RoleComponent> {

        /**
         * 诊断标签（不是行为分支）：只用于装配期异常文案（保证文案逐字稳定），
         * 不含任何枚举语义、也没有任何行为按它分叉。
         */
        private final String descriptorLabel;

 // 本基类不持有栏位、也不声明栏位读口 —— 栏位语言整体归 `HotbarSpecification`。

        /** 冻结位：装配期 {@link #freeze()} 之后禁止再改（防止被共享后被串改）。 */
        private boolean frozen;

        /**
         * 装配期绑定的注册 id（{@link #bindId(String)} 写入；未绑定 ⇒ {@code null}）。
         * <p>与栏位同属"必须由装配器设置"的参数：组件自带的描述符不知道自己的注册 id，
         * 若不绑定，任何读 id 的代码都会拿到 {@code null}。
         */
        private String boundId;

        /**
         * 必需的依赖类型：{@link #requires(Class)} 写入；装配期由
         * {@code core/Role#verifyDependencies()} 检查 —— 缺任一抛
         * {@link ComponentDependencyException}，该角色不注册。
         */
        private final List<Class<? extends RoleComponent>> requiredTypes = new ArrayList<>();

        /**
         * 可选的依赖类型：{@link #requiresOptional(Class)} 写入；缺失不报错（组件运行期自行处理
         * 查不到的情况）。可选与必需不得声明同一个类型（自相矛盾的声明，装配期报错）。
         */
        private final List<Class<? extends RoleComponent>> optionalTypes = new ArrayList<>();

        protected Specification(String descriptorLabel) {
            if (descriptorLabel == null || descriptorLabel.trim().isEmpty()) {
                throw new IllegalArgumentException("Component descriptor label cannot be null or empty.");
            }
            this.descriptorLabel = descriptorLabel;
        }

        /** 诊断标签（只出现在装配期异常文案里；没有任何行为分支读它）。 */
        public final String descriptorLabel() {
            return descriptorLabel;
        }

        /**
         * 声明"我需要同角色里还有某个组件"。
         * <p>按类型声明（不是按 id）：id 属于注册处，类型才是"我需要什么样的能力提供者"。
         * <p>语义：装配期检查时，本组件自己不算提供者，至少要有另一个组件的"提供类型"可赋值给
         * 这里声明的类型，否则视为缺依赖。
         * <p>失败形态：缺任一必需依赖则 {@code core/Role#verifyDependencies()} 抛
         * {@link ComponentDependencyException}，{@code registry/RoleLoader#loadInto}
         * 记 {@code SEVERE} 并跳过该角色（不注册、不进游戏）。
         * <p>声明示例（写在组件自己的嵌套 {@code Specification} 构造器里）：
         * <pre>{@code
         * public static final class Specification extends Skill.Specification<ExampleSkill> {
         *     public Specification() {
         *         super(Component.text("示例技能"), List.of(Component.text("示例描述")), 100, 0, Material.STONE);
         *         requires(EnergyComponent.class);              // 必需：没有能量组件就不许装配
         *         requiresOptional(SanTEComponent.class);       // 可选：没有也不报错，运行期自查
         *     }
         *     @Override public ExampleSkill create(String id, ComponentServicesPort services) { ... }
         * }
         * }</pre>
         * <p>不在描述符里自己查一遍：描述符是装配期对象、拿不到运行期状态，而依赖是同角色其它组件的
         * 有无问题，只能由框架在装配期（拿到整张组件表之后）统一检查。
         * <p>声明面的边界（如实申报）：本方法只在描述符上；所有组件都经描述符装配
         * （被动也已各带一个嵌套描述符），因此都有声明面。
         *
         * <p>一次只声明一个类型：需要多个依赖时链式调用
         * {@code requires(A.class).requires(B.class)}；重复声明同一类型幂等。
         *
         * @param type 必需的依赖组件类型（不得为 null）
         */
        public final Specification<T> requires(Class<? extends RoleComponent> type) {
            addDependencyType(requiredTypes, "requires", type);
            return this;
        }

        /**
         * 声明"有的话更好，没有也不报错"的依赖（可选依赖）：缺失不阻止装配。
         * <p>与 {@link #requires(Class)} 的唯一差别 = 缺失时的行为：必需抛异常阻止注册，可选放行。
         * <p>同一个类型不得既必需又可选择（自相矛盾的声明，{@code freeze()} 时抛
         * {@link IllegalStateException}）。
         *
         * <p>一次只声明一个类型：需要多个可选依赖时链式调用
         * {@code requiresOptional(A.class).requiresOptional(B.class)}；重复声明同一类型幂等。
         *
         * @param type 可选的依赖组件类型（不得为 null）
         */
        public final Specification<T> requiresOptional(Class<? extends RoleComponent> type) {
            addDependencyType(optionalTypes, "requiresOptional", type);
            return this;
        }

        /** 本描述符声明的必需依赖类型（不可变副本；顺序 = 声明顺序）。 */
        public final List<Class<? extends RoleComponent>> requiredTypes() {
            return List.copyOf(requiredTypes);
        }

        /** 本描述符声明的可选依赖类型（不可变副本；顺序 = 声明顺序）。 */
        public final List<Class<? extends RoleComponent>> optionalTypes() {
            return List.copyOf(optionalTypes);
        }

        /**
         * 本组件提供什么类型（依赖检查的"供给面"）：默认由泛型实参推导 ——
         * 例如 {@code class EnergyComponent.Specification extends RoleComponent.Specification<EnergyComponent>}
         * ⇒ 返回 {@code EnergyComponent.class}。
         * <p>为什么要可覆写：三个家族描述符（{@code Skill.Specification} /
         * {@code MainWeapon.Specification} / {@code PassiveSkill.Specification}）的泛型实参是家族基类，
         * 推导结果因此是族级（{@code Skill.class} 等）；若某个组件希望被"按具体类依赖"，
         * 覆写本方法返回自己的具体类即可（{@code requires(SomeConcreteSkill.class)} 就能命中它）。
         * <p>推导不到时返回 {@link RoleComponent#getClass()} 的上界 —— 即 {@code RoleComponent.class}
         * （"我只声明自己是组件"），这不会满足任何更具体的依赖声明。
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

 // 栏位语言不在本基类：持有者 = 带栏位的那一支描述符
 //  （`builtin/hotbar/HotbarSpecification#slotOrNull()`），仲裁者 = 渲染组件
 //  （`HotbarRenderComponent#registerSlot`），基类描述符一个字都不提栏位。

        /**
         * 冻结校验（供子类在自己的可写口里复用）：已冻结则抛异常。
         * <p>冻结位是本类的私有状态，因此子类需要这个受保护读口，而不是各自重写一遍文案。
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

        /** 装配期绑定的注册 id；未绑定时为 {@code null}（装配入口保证已装配的描述符都已绑定）。 */
        public final String boundId() {
            return boundId;
        }

        /**
         * 冻结前的子类自检（默认空）：带栏位那一支描述符在此做"必须有栏位"的 fail-fast。
         * <p>做成钩子而不覆写 {@link #freeze()}：freeze 是 `final` 的单一实现点（快照形状必须唯一），
         * 子类只能插校验、不能改形状。
         */
        protected void validateForFreeze() {
        }

        /**
         * 装配期冻结：返回本描述符的不可变快照（{@link Snapshot}：工厂 + 依赖声明 + 提供类型），
         * 并把本实例置为只读。
         * <p>装配入口 {@code Role.Builder.addComponent(String, Specification)} 只使用这份快照，
         * 因此角色模板不持有描述符对象，两个角色共用一个描述符实例也互不影响。
         * <p>栏位不进快照：栏位归「带栏位那一支描述符」与渲染组件，基类快照不含它；
         * "带栏位必填"的 fail-fast 由带栏位那一支描述符自己做（{@code HotbarSpecification}）。
         * <p>依赖声明的自检：同一个类型不得既必需又可选择，否则抛 {@link IllegalStateException}
         * （自相矛盾的声明必须在装配期就喊出来，而不是"看哪条先被读到"）。
         */
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
        public abstract T create(String id, ComponentServicesPort services);

        /**
         * 装配期不可变快照：装配表唯一持有的形态（工厂 + 依赖声明 + 提供类型）。
         */
        public static final class Snapshot {

            /** 诊断标签（与 {@link Specification#descriptorLabel()} 同源；只出现在异常文案里）。 */
            private final String descriptorLabel;
            private final ComponentFactory<? extends RoleComponent> factory;
            /** 本组件提供的类型（依赖检查的供给面）。 */
            private final Class<? extends RoleComponent> providedType;
            /** 必需依赖（缺任一则抛 {@link ComponentDependencyException}）。 */
            private final List<Class<? extends RoleComponent>> requiredTypes;
            /** 可选依赖（缺失不报错）。 */
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

            /** 诊断标签（只出现在装配期异常文案里；没有任何行为分支读它）。 */
            public String getDescriptorLabel() {
                return descriptorLabel;
            }

            public ComponentFactory<? extends RoleComponent> getFactory() {
                return factory;
            }

            /** 本组件提供的类型（依赖检查按它匹配）。 */
            public Class<? extends RoleComponent> getProvidedType() {
                return providedType;
            }

            /** 本组件声明的必需依赖类型（不可变副本）。 */
            public List<Class<? extends RoleComponent>> getRequiredTypes() {
                return requiredTypes;
            }

            /** 本组件声明的可选依赖类型（不可变副本）。 */
            public List<Class<? extends RoleComponent>> getOptionalTypes() {
                return optionalTypes;
            }
        }
    }
}
