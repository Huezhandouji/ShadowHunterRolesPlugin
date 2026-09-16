package com.shadowHunterRolesPlugin.core.dispatch;

import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 组件注册表（设计 §5.1 / §9）：组件集合 + **每组件资源表** + 组件查找。
 * <ul>
 *   <li>{@code get} 在**冻结前**调用 → 抛 {@code IllegalStateException}（装配期禁止跨组件查找）；</li>
 *   <li>{@code track} 把任务登记进**该组件专属**资源表，{@code stop()} 返回后由框架兜底取消。</li>
 * </ul>
 */
public final class ComponentRegistry {

    private final List<RoleComponent> components = new ArrayList<>();
    private final Map<RoleComponent, List<Task>> resources = new IdentityHashMap<>();
    private boolean frozen;

    /** 注册一个组件（装配期；须在 {@link #freeze()} 之前）。 */
    public void register(RoleComponent component) {
        if (component == null) {
            throw new NullPointerException("component");
        }
        if (frozen) {
            throw new IllegalStateException("ComponentRegistry is frozen; cannot register '" + component.getId() + "'.");
        }
        components.add(component);
        resources.put(component, new ArrayList<>());
    }

    /** 组件集合装配完成后立即冻结（此后 {@code get} 才合法）。 */
    public void freeze() {
        this.frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public List<RoleComponent> all() {
        return List.copyOf(components);
    }

    /** 按具体类查找（线性扫描，≤6 个组件）；未注册 → null；冻结前调用 → 抛异常。 */
    public <T extends RoleComponent> T get(Class<T> type) {
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
