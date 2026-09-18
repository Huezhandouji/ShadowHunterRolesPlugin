package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.ComponentLookup;
import com.shadowHunterRolesPlugin.core.dispatch.ComponentRegistry;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * {@link ComponentLookup} 的独立适配器（包级私有；禁止 {@code RoleInstance} 直接 implements 端口）。
 * 反向引用只存在这里。
 */
final class ComponentLookupImpl implements ComponentLookup {

    private final ComponentRegistry registry;

    ComponentLookupImpl(ComponentRegistry registry) {
        this.registry = registry;
    }

    @Override
    public <T extends RoleComponent> T get(Class<T> type) {
        return registry.get(type);
    }
}
