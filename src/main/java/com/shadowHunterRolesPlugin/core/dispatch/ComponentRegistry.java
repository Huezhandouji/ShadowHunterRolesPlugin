package com.shadowHunterRolesPlugin.core.dispatch;

import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件注册表（设计 §5.1 / §9 / 阶段 10 §4.6）：**组件集合（动态序）** + **每组件资源表** + 组件查找。
 * <p><b>阶段 10 · t55：容器语义放开</b>（裁定④ + 冻结件 §4.6）。改前是"装配即冻"——
 * {@code register()} 在 {@code frozen} 之后抛、{@code get/getById} 在**非** frozen 时抛 ⇒
 * 运行期**不可能**增删组件。现在改成**写放开、读始终可用**：
 * <ul>
 *   <li><b>写</b>（{@link #register} / {@link #insert} / {@link #remove} / {@link #removeById}）：
 *       <b>装配期与运行期都可调用</b>（"装配即冻"取消）；id 在容器内**唯一**（重复 ⇒ 抛）；
 *       <b>遍历窗口内一律拒绝</b>（{@link #beginIteration()} 与 {@link #endIteration()} 之间，
 *       即框架正在广播 {@code awake/start/stop/update/onSanTEChange} 时）⇒ "禁止遍历中修改"；</li>
 *   <li><b>读</b>（{@link #get} / {@link #getByType} / {@link #getById} / {@link #all}）：
 *       在**装配完成（{@link #freeze()}）之后始终可用**，含运行期增删**之后** —— 三个读口都返回
 *       **完整集合**（新加的组件立刻可见、被删的立刻不可见）。
 *       <b>装配期（{@code frozen=false}）仍禁止跨组件查找</b>：这是 t34 立下的既有护栏
 *       （组件在构造期只应拿到服务集，不得读到"还在一半"的组件表），本次**不放宽**（口径见说明件）。</li>
 * </ul>
 * <p><b>顺序语义</b>：{@link #all()} 的返回顺序 = **容器内当前序**（装配序 + 运行期追加/插位的实际位置）
 * ⇒ 它就是**渲染序与派发序**（冻结件 §5 第 8 项"动态序"）。{@link #all()} 返回**不可变快照**
 * （写时复制），因此框架遍历期间即使有并发修改请求（会被窗口护栏拒绝）也不会破坏本次遍历。
 */
public final class ComponentRegistry {

    private final List<RoleComponent> components = new ArrayList<>();
    private final Map<RoleComponent, List<Task>> resources = new IdentityHashMap<>();

    /**
     * **每组件一条依赖声明**（阶段 10 · t55 · 队长裁定 P2）：`组件 → 声明`。
     * 声明**从装配条目 / 描述符快照算出**（{@code Role.ComponentEntry} 或
     * {@code RoleComponent.Specification.Snapshot}），**不是手工维护的表** ⇒ 不会漂移。
     * 它是"删除前算反向依赖"的唯一数据来源。
     */
    private final Map<RoleComponent, Declaration> declarations = new IdentityHashMap<>();
    private boolean frozen;

    /** 遍历窗口深度（{@code >0} = 框架正在遍历组件表；见 {@link #beginIteration()}）。 */
    private int iterationDepth;

    // ───────────── 写口：装配期与运行期都可调用（"装配即冻"已取消） ─────────────

    /** 追加一个组件到容器末尾（装配期与运行期同一条路径）。 */
    public void register(RoleComponent component) {
        insert(components.size(), component, null);
    }

    /**
     * 追加一个组件并**登记它的依赖声明**（装配期由 {@code RoleInstance} 从 {@code Role.ComponentEntry}
     * 传入；运行期由 {@code ComponentLookupImpl} 从描述符快照传入）。
     */
    public void register(RoleComponent component, Declaration declaration) {
        insert(components.size(), component, declaration);
    }

    /** 在指定下标插入（无显式声明 ⇒ 声明取默认：提供类型 = 组件具体类、无必需依赖）。 */
    public void insert(int index, RoleComponent component) {
        insert(index, component, null);
    }

    /**
     * **在指定下标插入**一个组件并登记其依赖声明（运行期"插位"；{@code index == size()} 等价于追加）。
     * <p>护栏（按序检查，失败一律不留痕）：① null ⇒ {@code NullPointerException}；
     * ② **遍历窗口内** ⇒ {@code IllegalStateException}；③ 下标越界 ⇒ {@code IndexOutOfBoundsException}；
     * ④ id 已存在 ⇒ {@code IllegalArgumentException}（id 在容器内唯一 —— 它是资源表键与热键栏查表键）。
     *
     * @param declaration 依赖声明；{@code null} ⇒ 默认声明（提供类型 = {@code component.getClass()}、无必需依赖）
     */
    public void insert(int index, RoleComponent component, Declaration declaration) {
        if (component == null) {
            throw new NullPointerException("component");
        }
        if (isIterating()) {
            throw new IllegalStateException("ComponentRegistry is being iterated (the container is broadcasting); "
                    + "modifying it now is forbidden. Defer the change until after the broadcast (or call it from a non-broadcast path).");
        }
        if (index < 0 || index > components.size()) {
            throw new IndexOutOfBoundsException("Component index " + index + " is out of range [0, " + components.size() + "].");
        }
        for (RoleComponent existing : components) {
            if (existing.getId().equals(component.getId())) {
                throw new IllegalArgumentException("Component id already registered: " + component.getId());
            }
        }
        components.add(index, component);
        resources.computeIfAbsent(component, key -> new ArrayList<>());
        declarations.put(component, declaration != null ? declaration : Declaration.of(component));
    }

    /**
     * 从容器移除一个组件（**运行期删除**）；返回是否确实移除。
     * <p>顺序约定（由调用方保证，见 {@code core/ComponentLookupImpl#remove}）：**先 `stop()`、再回收资源**，
     * 最后才移除 ⇒ 移除之后容器里不再有它（{@code get/getById/all} 立刻看不到）。
     * <p>为免泄漏，本方法在移除时**再兜底取消一次**它的资源（幂等：已取消的句柄不会重复取消）。
     * <p>遍历窗口内调用 ⇒ {@code IllegalStateException}。
     */
    public boolean remove(RoleComponent component) {
        if (component == null) {
            return false;
        }
        if (isIterating()) {
            throw new IllegalStateException("ComponentRegistry is being iterated (the container is broadcasting); "
                    + "removing '" + component.getId() + "' now is forbidden.");
        }
        if (!components.remove(component)) {
            return false;
        }
        declarations.remove(component);
        cancelAll(component);
        return true;
    }

    /** 按 id 移除（未注册 ⇒ {@code false}）；语义同 {@link #remove(RoleComponent)}。 */
    public boolean removeById(String id) {
        RoleComponent component = findById(id);
        return component != null && remove(component);
    }

    // ───────────── 依赖声明与反向依赖（阶段 10 · t55 · 队长裁定 P2） ─────────────

    /**
     * 一条组件的**依赖声明**：`(id, 提供类型, 必需依赖类型)`。
     * <p><b>从声明算出，不手工维护</b>：装配期来自 {@code Role.ComponentEntry}（描述符的
     * {@code providedType()} / {@code requiredTypes()}），运行期来自描述符快照
     * （{@code Specification#freeze()}）—— 两条路径同一个来源。
     */
    public record Declaration(String id,
                              Class<? extends RoleComponent> providedType,
                              List<Class<? extends RoleComponent>> requiredTypes) {

        public Declaration {
            if (id == null || id.trim().isEmpty()) {
                throw new IllegalArgumentException("Component declaration id cannot be null or empty.");
            }
            if (providedType == null) {
                throw new NullPointerException("providedType");
            }
            requiredTypes = requiredTypes == null ? List.of() : List.copyOf(requiredTypes);
        }

        /** 默认声明（无描述符信息时）：提供类型 = 组件具体类、无必需依赖。 */
        public static Declaration of(RoleComponent component) {
            return new Declaration(component.getId(), component.getClass(), List.of());
        }
    }

    /** 某 id 的依赖声明（未注册 ⇒ {@code null}；**不做冻结检查**，供写路径使用）。 */
    public Declaration declarationOf(String id) {
        RoleComponent component = findById(id);
        return component != null ? declarations.get(component) : null;
    }

    /**
     * **反向依赖表（现算）**：谁把 {@code id} 提供的类型声明为**必需**依赖 ⇒ 返回
     * `阻止者 id → 它需要的类型`（保持容器序；空 = 无人必需它）。
     * <p>口径与装配期检查一致：**自己不算提供者**；匹配规则 = {@code required.isAssignableFrom(目标提供类型)}。
     * <p>用途（P2）：删除组件前先算这张表 —— 非空 ⇒ **拒绝删除**（否则"必需"会静默失效）。
     */
    public Map<String, Class<? extends RoleComponent>> requiredBy(String id) {
        Map<String, Class<? extends RoleComponent>> blockers = new LinkedHashMap<>();
        Declaration target = declarationOf(id);
        if (target == null) {
            return blockers;
        }
        for (RoleComponent component : components) {
            if (id.equals(component.getId())) {
                continue;
            }
            Declaration declaration = declarations.get(component);
            if (declaration == null) {
                continue;
            }
            for (Class<? extends RoleComponent> required : declaration.requiredTypes()) {
                if (required.isAssignableFrom(target.providedType())) {
                    blockers.putIfAbsent(declaration.id(), required);
                }
            }
        }
        return blockers;
    }

    /**
     * **候选声明的缺必需依赖清单（现算）**：{@code 缺的类型全名 → 该类型}（空 = 齐）。
     * <p>口径与装配期同源：候选**自己不算提供者**（用户原话"检查自己需要的依赖（**其他组件**）"）。
     * 运行期动态添加前用它挡住"带未满足必需依赖的组件进容器"（与装配期同一失败语义）。
     */
    public Map<String, Class<? extends RoleComponent>> missingProviders(Declaration candidate) {
        Map<String, Class<? extends RoleComponent>> missing = new LinkedHashMap<>();
        if (candidate == null) {
            return missing;
        }
        for (Class<? extends RoleComponent> required : candidate.requiredTypes()) {
            boolean satisfied = false;
            for (RoleComponent component : components) {
                if (candidate.id().equals(component.getId())) {
                    continue;
                }
                Declaration declaration = declarations.get(component);
                if (declaration != null && required.isAssignableFrom(declaration.providedType())) {
                    satisfied = true;
                    break;
                }
            }
            if (!satisfied) {
                missing.put(required.getName(), required);
            }
        }
        return missing;
    }

    // ───────────── 遍历窗口（"禁止遍历中修改"的护栏） ─────────────

    /**
     * 进入**遍历窗口**（框架在广播组件钩子前调用）：窗口内 {@link #insert} / {@link #remove} /
     * {@link #removeById} 一律抛 {@code IllegalStateException}。
     * <p>可嵌套（深度计数）：{@code update()} 广播期间组件改 SanTE ⇒ 内层 {@code onSanTEChange} 广播
     * 再次进入窗口是合法的。必须与 {@link #endIteration()} 成对（用 {@code finally}）。
     */
    public void beginIteration() {
        iterationDepth++;
    }

    /** 退出遍历窗口（与 {@link #beginIteration()} 成对；多退一次是幂等的 no-op）。 */
    public void endIteration() {
        if (iterationDepth > 0) {
            iterationDepth--;
        }
    }

    /** 是否处于遍历窗口内。 */
    public boolean isIterating() {
        return iterationDepth > 0;
    }

    // ───────────── 冻结（装配完成标记）与读口 ─────────────

    /** 组件集合装配完成后立即冻结（此后 {@code get} / {@code getById} 才合法；**写口不受它限制**）。 */
    public void freeze() {
        this.frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** 组件集合的**不可变快照**（顺序 = 容器内当前序 = 动态序）。 */
    public List<RoleComponent> all() {
        return List.copyOf(components);
    }

    /** 容器内组件个数。 */
    public int size() {
        return components.size();
    }

    /** 某组件在容器内的下标（不在容器内 ⇒ {@code -1}）。 */
    public int indexOf(RoleComponent component) {
        return components.indexOf(component);
    }

    /** 按具体类查找（线性扫描，≤6 个组件）；未注册 → null；冻结前调用 → 抛异常。 */
    public <T extends RoleComponent> T get(Class<T> type) {
        return getByType(type);
    }

    /**
     * **按类型查找**（与 {@link #get} 同一实现；本名是它的显式命名，供依赖注入/容器读口使用）：
     * 返回**第一个** {@code type.isInstance(...)} 的组件；未注册 → null；冻结前调用 → 抛异常。
     */
    public <T extends RoleComponent> T getByType(Class<T> type) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (!frozen) {
            throw new IllegalStateException("ComponentRegistry is not frozen yet; getComponent() is only allowed after assembly.");
        }
        for (RoleComponent component : components) {
            if (type.isInstance(component)) {
                return type.cast(component);
            }
        }
        return null;
    }

    /** 按组件 id 查找（施放管道用）；未注册 → null；冻结前调用 → 抛异常。 */
    public RoleComponent getById(String id) {
        if (id == null) {
            return null;
        }
        if (!frozen) {
            throw new IllegalStateException("ComponentRegistry is not frozen yet; getById() is only allowed after assembly.");
        }
        return findById(id);
    }

    /** 内部按 id 查找（**不做冻结检查**：移除路径在装配期也应可用）。 */
    private RoleComponent findById(String id) {
        if (id == null) {
            return null;
        }
        for (RoleComponent component : components) {
            if (id.equals(component.getId())) {
                return component;
            }
        }
        return null;
    }

    // ───────────── 每组件资源表（不变） ─────────────

    /** 登记本组件的资源（定时器等）；{@code stop()} 返回后由 {@link #cancelAll} 兜底回收。 */
    public void track(RoleComponent component, Task task) {
        if (component == null || task == null) {
            return;
        }
        resources.computeIfAbsent(component, key -> new ArrayList<>()).add(task);
    }

    /** 取消并清空某个组件登记的全部资源（框架在组件 {@code stop()} 返回后调用）。 */
    public int cancelAll(RoleComponent component) {
        List<Task> tasks = resources.remove(component);
        if (tasks == null) {
            return 0;
        }
        int cancelled = 0;
        for (Task task : tasks) {
            if (!task.isCancelled()) {
                task.cancel();
                cancelled++;
            }
        }
        return cancelled;
    }

    /** 取消全部组件登记的资源（{@code RoleInstance.clear()} 的兜底路径）。 */
    public int cancelAllAndClear() {
        int cancelled = 0;
        for (RoleComponent component : new ArrayList<>(resources.keySet())) {
            cancelled += cancelAll(component);
        }
        return cancelled;
    }
}
