package com.shadowHunterRolesPlugin.core.hotbar;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 旧短名（**只保留为 `@Deprecated` 别名**，阶段 7 · A 步全拼改名）：阶段 6 引入的
 * {@code HotbarSpec} 在阶段 7 改名为 {@link HotbarSpecification}（全拼）并升格为"表现规格 + 带栏位描述符"。
 * <p>
 * 本类**只做两件事**：① 让旧名仍然可解析（所有旧调用点无需改名即可继续编译）；
 * ② 把旧的 `of(...)` 静态工厂转发到新类。**没有**独立实现，也没有第二套字段。
 * <p>
 * 命名沿用工程的 JavaBean 风格（设计 §4.3：不引入 record 风格访问器）。
 *
 * @deprecated 改用 {@link HotbarSpecification}（新名 = 全拼；旧名不再新增功能）。
 */
@Deprecated
public class HotbarSpec<T extends RoleComponent> extends HotbarSpecification<T> {

    private HotbarSpec(String id, Component displayName, Component description, Material icon,
                       int cooldownTicks, int energyCost, ItemKind kind) {
        super(id, displayName, description, icon, cooldownTicks, energyCost, kind);
    }

    /**
     * 旧构造入口的别名（语义与参数顺序逐字不变）。
     *
     * @deprecated 改用 {@link HotbarSpecification#of(String, Component, Component, Material, int, int, ItemKind)}。
     */
    @Deprecated
    public static <T extends RoleComponent> HotbarSpec<T> of(String id, Component displayName, Component description,
                                                             Material icon, int cooldownTicks, int energyCost,
                                                             ItemKind kind) {
        return new HotbarSpec<>(id, displayName, description, icon, cooldownTicks, energyCost, kind);
    }
}
