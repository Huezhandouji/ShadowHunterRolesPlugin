package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.ports.RoleInfoPort;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.List;

/**
 * {@link RoleInfoPort} 的独立适配器：持 {@code RoleInstance}，与
 * {@code *PortImpl} 家族同形。
 *
 * <p><b>只做"角色模板的只读信息"</b>：{@link #id()} 与 {@link #description()} 都取自本实例持有的
 * 角色模板（{@code Role}），与玩家状态无关。
 *
 * <p><b>★ 阵营已整体搬出本类</b>（判定与真值都在 {@code roleComponent/builtin/FactionComponent}）：
 * 本类不再读实例阵营、也不留任何转发壳 —— 阵营在这个工程里只有一个读取入口（组件本身）与一个
 * 跨实例权威（{@code platform/FactionManager} 的注册表）。
 * 因此本类此后不需要任何平台侧关系表：那个平台口已随本次改动整体删除，本类也不再持有任何平台引用。
 */
final class RoleInfoImpl implements RoleInfoPort {

    private final RoleInstance owner;

    RoleInfoImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public String id() {
        Role role = owner.getRole();
        return role == null ? null : role.getId();
    }

    @Override
    public String description() {
        Role role = owner.getRole();
        if (role == null) {
            return "";
        }
        List<Component> lines = role.getDescription();
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder joined = new StringBuilder();
        for (Component line : lines) {
            if (joined.length() > 0) {
                joined.append('\n');
            }
            joined.append(PlainTextComponentSerializer.plainText().serialize(line));
        }
        return joined.toString();
    }
}
