package com.shadowHunterRolesPlugin.core.component;

import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件注册表（设计 §5.1 / §9 / 阶段 10 §4.6）：**组件集合（动态序）** + **每组件资源表** + 组件查找。
 * <p><b>：容器语义放开</b>（裁定④ + 冻结件 §4.6）。改前是"装配即冻"——
 * {@code register()} 在 {@code frozen} 之后抛、{@code get/getById} 在**非** frozen 时抛 ⇒
 * 运行期**不可能**增删组件。现在改成**写放开、读始终可用**：
 * <ul>
 * <li><b>写</b>（{@link #register} / {@link #insert} / {@link #remove} / {@link #removeById}）：
 * <b>装配期与运行期都可调用</b>（"装配即冻"取消）；id **可重复**（ 放开 ——
 * 用户新路线图第 2 条：添加组件时允许重复组件）；
 * <b>遍历窗口内一律拒绝</b>（{@link #beginIteration()} 与 {@link #endIteration()} 之间，
 * 即框架正在广播 {@code awake/start/stop/update/onSanTEChange} 时）⇒ "禁止遍历中修改"；</li>
 * <li><b>读</b>（{@link #get} / {@link #getByType} / {@link #getAll} / {@link #getById} / {@link #all}）：
 * 在**装配完成（{@link #freeze()}）之后始终可用**，含运行期增删**之后** —— 读口都返回
 * **完整集合**（新加的组件立刻可见、被删的立刻不可见）。
 * <b>装配期（{@code frozen=false}）仍禁止跨组件查找</b>：这是 立下的既有护栏
 * （组件在构造期只应拿到服务集，不得读到"还在一半"的组件表），本次**不放宽**（口径见说明件）。</li>
 * </ul>
 * <p><b>查询语义（ 按用户新路线图第 1 条统一）</b>：类型条件 = **可赋值性**
 * （{@code type.isInstance(component)} ⇒ 父类/接口查询命中子类实例），顺序 = **添加顺序**
 * （容器当前序，不是 id 序、不是具体类优先）：
 * <ul>
 * <li>{@link #get(Class)} / {@link #getByType(Class)} = **第一个**符合条件的；</li>
 * <li>{@link #getAll(Class)} = **全部**符合条件的，按添加顺序；</li>
 * <li>{@link #getById(String)} = **第一个** id 相等的（重复 id 下与 {@link #removeById} 同口径）。</li>
 * </ul>
 * <p><b>顺序语义</b>：{@link #all()} 的返回顺序 = **容器内当前序**（装配序 + 运行期追加/插位的实际位置）
 * ⇒ 它就是**渲染序与派发序**（冻结件 §5 第 8 项"动态序"）。{@link #all()} 返回**不可变快照**
 * （写时复制），因此框架遍历期间即使有并发修改请求（会被窗口护栏拒绝）也不会破坏本次遍历。
 */
public final class ComponentRegistry {

    private final List<RoleComponent> components = new ArrayList<>();
    private final Map<RoleComponent, List<Task>> resources = new IdentityHashMap<>();

 /**
 * **每组件一条依赖声明**（ · P2）：`组件 → 声明`。
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
 * ② **遍历窗口内** ⇒ {@code IllegalStateException}；③ 下标越界 ⇒ {@code IndexOutOfBoundsException}。
 * <p><b>（用户新路线图第 2 条）：id 唯一性已放开</b> —— 同一个 id **可以**在容器内出现多次
 * （旧护栏 ④"id 已存在 ⇒ {@code IllegalArgumentException}" 已删除）。随之而来的两条口径：
 * <ul>
 * <li>{@link #getById(String)} / {@link #removeById(String)} 取/删的都是**添加顺序第一个**同 id 者
 * ⇒ 与 {@link #get(Class)} 的"第一个"同口径；</li>
 * <li><b>资源表不受影响</b>：{@link #resources} 与 {@link #declarations} 都是
 * {@link IdentityHashMap}（**按组件实例**索引）⇒ 两个同 id 组件各持各的资源与声明
 * （id 从不是这两张表的键）。</li>
 * </ul>
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

 /**
 * 按 id 移除（未注册 ⇒ {@code false}）；语义同 {@link #remove(RoleComponent)}。
 * <p>：id 可重复 ⇒ 本口移除的是**添加顺序第一个**同 id 者（与 {@link #getById(String)} 同目标）；
 * 其余同 id 者**留在容器里**（不会被连带移除）。
 */
    public boolean removeById(String id) {
        RoleComponent component = findById(id);
        return component != null && remove(component);
    }

 // ───────────── 依赖声明与反向依赖（ · P2） ─────────────

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

 /**
 * 某 id 的依赖声明（未注册 ⇒ {@code null}；**不做冻结检查**，供写路径使用）。
 * <p>：id 可重复 ⇒ 返回的是**添加顺序第一个**同 id 者的声明
 * （与 {@link #getById(String)} 同口径）。要按实例取声明请直接查 {@link #declarations} 的内部表口径
 * （见 {@link #requiredBy(RoleComponent)}）。
 */
    public Declaration declarationOf(String id) {
        RoleComponent component = findById(id);
        return component != null ? declarations.get(component) : null;
    }

 /**
 * **反向依赖表（现算）**：谁把 {@code id} 提供的类型声明为**必需**依赖 ⇒ 返回
 * `阻止者 id → 它需要的类型`（保持容器序；空 = 无人必需它）。
 * <p>口径与装配期检查一致：**自己不算提供者**；匹配规则 = {@code required.isAssignableFrom(目标提供类型)}。
 * <p><b>（id 可重复）</b>：本重载的**目标** = 添加顺序第一个同 id 者
 * （与 {@link #getById(String)} / {@link #removeById(String)} 同目标 —— 删除守卫要保护的正是"会被删掉的那一个"）。
 * 若需要**按实例**精确判定（例如隔离路径逐个挑"当前无人依赖"的组件），用
 * {@link #requiredBy(RoleComponent)}。
 * <p>用途（P2）：删除组件前先算这张表 —— 非空 ⇒ **拒绝删除**（否则"必需"会静默失效）。
 */
    public Map<String, Class<? extends RoleComponent>> requiredBy(String id) {
        return requiredBy(findById(id));
    }

 /**
 * **反向依赖表（现算）· 按实例**：谁把 {@code target} 提供的类型声明为**必需**依赖。
 * <p><b>★ 的实质修正</b>：旧实现"自己不算提供者"是**按 id 排除**的
 * （{@code if (id.equals(component.getId())) continue;}）⇒ id 可重复之后，它会**把另一个同 id 的
 * 依赖者也一并跳过** ⇒ 反向依赖表**漏掉真正的阻止者** ⇒ 删除守卫误判"无人依赖"。
 * 现在改为**按实例排除**（{@code component == target}）。
 * <p>阻止者**按 id 入表**（与旧口径一致：一个阻止者只记一条）；当**多个阻止者共享同一 id** 时，
 * 第 2 个起加 `#2`/`#3`… 后缀 ⇒ **一个阻止者都不会被静默合并掉**
 * （判据：两个同 id 组件各自被他人必需 ⇒ 表里两条）。
 */
    public Map<String, Class<? extends RoleComponent>> requiredBy(RoleComponent target) {
        Map<String, Class<? extends RoleComponent>> blockers = new LinkedHashMap<>();
        if (target == null) {
            return blockers;
        }
        Declaration targetDeclaration = declarations.get(target);
        if (targetDeclaration == null) {
            return blockers;
        }
 //一个阻止者只记一条（按**实例**去重，保持容器序）；同 id 多阻止者用 `#n` 区分
        Map<String, Integer> occurrences = new HashMap<>();
        for (RoleComponent component : components) {
            if (component == target) {
                continue;
            }
            Declaration declaration = declarations.get(component);
            if (declaration == null) {
                continue;
            }
            Class<? extends RoleComponent> matched = null;
            for (Class<? extends RoleComponent> required : declaration.requiredTypes()) {
                if (required.isAssignableFrom(targetDeclaration.providedType())) {
                    matched = required;
                    break;
                }
            }
            if (matched == null) {
                continue;
            }
            String base = declaration.id();
            int occurrence = occurrences.merge(base, 1, Integer::sum);
            blockers.put(occurrence == 1 ? base : base + "#" + occurrence, matched);
        }
        return blockers;
    }

 /**
 * **候选声明的缺必需依赖清单（现算）**：{@code 缺的类型全名 → 该类型}（空 = 齐）。
 * <p>口径与装配期同源：候选**自己不算提供者**。
 * <p><b>（id 可重复）的实质修正</b>：旧实现把"自己不算提供者"写成
 * {@code if (candidate.id().equals(component.getId())) continue;} —— 而候选此刻**还不在容器里**
 * （本方法只在 {@code add}/{@code insertAt} 的注册**之前**调用）⇒ 那条跳过从来只可能排除
 * **另一个同 id 的既有组件** ⇒ id 可重复之后会**误报"缺依赖"**（把一个真实的提供者当成自己跳过）。
 * 现在直接去掉该跳过：候选不在容器里 ⇒ 天然不会被算成自己的提供者；
 * 同 id 的既有组件则**正常**参与提供者判定。无重复 id 时行为逐字不变。
 */
    public Map<String, Class<? extends RoleComponent>> missingProviders(Declaration candidate) {
        Map<String, Class<? extends RoleComponent>> missing = new LinkedHashMap<>();
        if (candidate == null) {
            return missing;
        }
        for (Class<? extends RoleComponent> required : candidate.requiredTypes()) {
            boolean satisfied = false;
            for (RoleComponent component : components) {
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

 /** 按具体类/父类/接口查找（线性扫描，≤6 个组件）；未注册 → null；冻结前调用 → 抛异常。 */
    public <T> T get(Class<T> type) {
        return getByType(type);
    }

 /**
 * **按类型查找**（与 {@link #get} 同一实现；本名是它的显式命名，供依赖注入/容器读口使用）：
 * 返回**第一个**满足 {@code type.isInstance(...)} 的组件 —— 顺序 = **添加顺序**（容器当前序）。
 * <p><b>类型条件 = 可赋值性</b>（{@code isInstance}）⇒ 用**父类或接口**查询会命中子类/实现类实例
 * （例：抽象 `FatherComponent` 派生 `C1Component` / `C2Component`，先加 C1、后加 C2
 * ⇒ `get(FatherComponent.class)` 返回 **C1**）。
 * <p><b>类型形参不设上界</b>（）：旧签名是 {@code <T extends RoleComponent>}，那样
 * **纯接口**（不继承 {@code RoleComponent} 的接口，例如 `Tag`）**根本无法作为实参** ——
 * 而用户第 1 条明写"父类**或接口**查询命中子类实例" ⇒ 改为无上界 {@code <T>} + {@code type.cast(...)}
 * （匹配时才 cast ⇒ 对任意 {@code type} 都**安全**：不匹配就返回 null / 空列表）。
 * 既有调用点（{@code T extends RoleComponent} 的实参）**源码级不变**。
 * <p><b>不是"具体类优先"</b>（ 修正旧措辞）：旧 javadoc 写着"具体类优先"，而实现一直是
 * 纯线性扫描 ⇒ 那是对**行为撒谎的值**家族 ⇒ 本卡按真实语义改写 →。
 * <p>未注册 → {@code null}；冻结前调用 → 抛异常。
 */
    public <T> T getByType(Class<T> type) {
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

 /**
 * **按类型取全部**（ · 用户新路线图第 1 条新增）：返回**全部**满足
 * {@code type.isInstance(...)} 的组件，顺序 = **添加顺序**（容器当前序）。
 * <p>与 {@link #get(Class)} **同一条件、同一顺序**，只是不截断到第一个 ⇒
 * {@code getAll(T).isEmpty()} ⟺ {@code get(T) == null}，且 `getAll` 的首元素恒等于 `get`。
 * <p>类型形参同样**无上界** ⇒ 支持**接口**查询（`getAll(Tag.class)` 返回全部实现者）。
 * <p>边界：`type == null` ⇒ {@code NullPointerException}；冻结前调用 ⇒ {@code IllegalStateException}；
 * 无人符合 ⇒ **空列表**（不是 null）；返回**不可变**列表。
 */
    public <T> List<T> getAll(Class<T> type) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (!frozen) {
            throw new IllegalStateException("ComponentRegistry is not frozen yet; getAll() is only allowed after assembly.");
        }
        List<T> matches = new ArrayList<>();
        for (RoleComponent component : components) {
            if (type.isInstance(component)) {
                matches.add(type.cast(component));
            }
        }
        return List.copyOf(matches);
    }

 /**
 * 按组件 id 查找（施放管道用）：**添加顺序第一个** id 相等者；未注册 → null；冻结前调用 → 抛异常。
 * <p>：id 可重复 ⇒ 本读口 = "第一个"（与 {@link #get(Class)} 的"第一个"同口径、
 * 与 {@link #removeById(String)} 同目标）。
 */
    public RoleComponent getById(String id) {
        if (id == null) {
            return null;
        }
        if (!frozen) {
            throw new IllegalStateException("ComponentRegistry is not frozen yet; getById() is only allowed after assembly.");
        }
        return findById(id);
    }

 /** 内部按 id 查找（**不做冻结检查**：移除路径在装配期也应可用）；返回**添加顺序第一个**同 id 者。 */
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
