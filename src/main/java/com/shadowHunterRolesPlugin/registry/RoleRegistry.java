package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 角色模板的**纯容器**（阶段 3.1）：只负责保存与查询，不再自己做注册。
 *
 * <p>与阶段 2 之前的关键差别：
 * <ul>
 *   <li>**没有 static{} 初始化块** —— 注册改由 {@link RoleLoader} 在 {@code onEnable} 显式执行，
 *       装配失败不再可能变成 {@code ExceptionInInitializerError}（那会把整个类初始化拖垮）。</li>
 *   <li>**没有静态 Map** —— 容器状态是实例字段，由主类持有并注入。</li>
 * </ul>
 */
public class RoleRegistry {

    private final Map<String, Role> roles = new LinkedHashMap<>();

    /** 注册一个角色模板；同一 id 重复注册会覆盖（去重发生在 Role.Builder 层，见 §10 裁决 1）。 */
    public void register(Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role cannot be null");
        }
        roles.put(role.getId(), role);
    }

    public Role get(String roleId) {
        return roleId == null ? null : roles.get(roleId);
    }

    public boolean contains(String roleId) {
        return roleId != null && roles.containsKey(roleId);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(roles.keySet());
    }

    public Collection<Role> all() {
        return Collections.unmodifiableCollection(roles.values());
    }

    public int size() {
        return roles.size();
    }

    public boolean isEmpty() {
        return roles.isEmpty();
    }

    // ==========================================================================================
    // 临时兼容桥（**阶段 3 偏差，已申报**）
    //
    // 为什么还需要它：`command/RoleCommand.java` 与 `manager/RoleManager.java` 仍以
    // `RoleRegistry.hasRole(...) / getRole(...) / isValidRoleId(...)` 的静态形式调用，而这两个文件
    // **不在 t13 的 inScope 内**（本卡 inScope = registry/ + api/ + internal/api/ + 主类 + 小结），
    // 因此本卡无法把它们改成注入式。
    //
    // 与旧实现的区别（也是本卡要满足的部分）：这里**没有静态 Map、没有 static{}**，桥只持有
    // **一个实例引用**；未安装时立刻抛 IllegalStateException（fail-fast，而不是 NPE 或初始化崩溃）。
    // **移除点**：阶段 4 把这些调用点迁到 ComponentServices/上下文注入之后，本桥必须删除。
    // ==========================================================================================

    private static RoleRegistry active;

    public static void install(RoleRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry cannot be null");
        }
        active = registry;
    }

    public static RoleRegistry active() {
        RoleRegistry current = active;
        if (current == null) {
            throw new IllegalStateException("RoleRegistry has not been installed yet (expected in onEnable, before any role query).");
        }
        return current;
    }

    /** @deprecated 兼容桥，改用 {@link #active()}.{@link #contains(String)}；阶段 4 删除。 */
    @Deprecated
    public static boolean hasRole(String roleId) {
        return active().contains(roleId);
    }

    /** @deprecated 兼容桥，改用 {@link #active()}.{@link #get(String)}；阶段 4 删除。 */
    @Deprecated
    public static Role getRole(String roleId) {
        return active().get(roleId);
    }

    /** @deprecated 兼容桥，改用 {@link #active()}.{@link #contains(String)}；阶段 4 删除。 */
    @Deprecated
    public static boolean isValidRoleId(String id) {
        return active().contains(id);
    }

}
