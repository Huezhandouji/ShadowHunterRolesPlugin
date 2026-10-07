package com.shadowHunterRolesPlugin.core.ports;

/**
 * 角色信息服务：**本实例**的只读信息面（角色模板的 id / 描述），<b>不含阵营</b>。
 * <p>角色的静态声明（id / 描述 / 默认阵营）归聚合根（{@code Role}）；本端口只把其中"与阵营无关"
 * 的那两条交给组件，因为组件不能直接摸 {@code Role}/{@code RoleInstance}
 * （那会把内部实现细节变成组件契约）。
 *
 * <p><b>★ 阵营不在本端口</b>：玩家当前的阵营是**每实例一份**的状态，真值与判定都在阵营组件
 * （{@code roleComponent/builtin/FactionComponent}）—— 读取与判敌走组件本身
 * （{@code svc().components().get(FactionComponent.class)}），跨实例读取走
 * {@code platform/FactionManager} 的注册表。本端口因此不含任何阵营成员，
 * 也不保留任何转发壳或默认实现（留一条默认转发 = 留下第二条活路径）。
 */
public interface RoleInfoPort {

    /** 角色 id（聚合根的身份；{@code Role} 只带 Id 的那一部分）。 */
    String id();

    /** 角色描述（纯文本，多行以 {@code '\n'} 连接；无描述 → 空串）。 */
    String description();
}
