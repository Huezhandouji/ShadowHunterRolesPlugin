package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/** 组件查找端口：{@code RoleComponent.getComponent(T.class)} 的实现（设计 §4.4）。 */
public interface ComponentLookup {

    /**
     * 取本角色实例内的另一个组件（按具体类优先）。
     * 未注册 → {@code null}；<b>注册表冻结前调用 → 抛 {@code IllegalStateException}</b>。
     */
    <T extends RoleComponent> T get(Class<T> type);
}
