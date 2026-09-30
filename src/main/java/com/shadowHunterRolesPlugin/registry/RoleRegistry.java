package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 角色模板的纯容器：只负责保存与查询，注册由 {@link RoleLoader} 在 {@code onEnable} 显式执行。
 *
 * <p><b>两条不变量</b>：
 * <ul>
 *   <li>没有 {@code static { }} 初始化块，装配失败不会变成 {@code ExceptionInInitializerError}
 *       （那会把整个类初始化拖垮）；</li>
 *   <li>没有静态 Map，容器状态是实例字段，由主类持有并注入。</li>
 * </ul>
 */
public class RoleRegistry {

    private final Map<String, Role> roles = new LinkedHashMap<>();

    /** 注册一个角色模板；同一 id 重复注册会覆盖（去重发生在 Role.Builder 层）。 */
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

    // 本类只有实例成员：没有静态 Map、没有 static {}、也没有任何静态状态。

}
