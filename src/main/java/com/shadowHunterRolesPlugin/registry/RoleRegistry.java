package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 角色模板的**纯容器**：只负责保存与查询，不再自己做注册。
 *
 * <p>关键差别：
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

    // **D-2 静态兼容桥已删除** —— 原 `install/active/hasRole/getRole/isValidRoleId`
    // 的静态形式调用方（`command/RoleCommand`、`manager/RoleManager`）已改为**构造注入**本容器的实例 API。
    // 因此本类现在只有实例成员：没有静态 Map、没有 static{}、也没有任何静态状态。

}
