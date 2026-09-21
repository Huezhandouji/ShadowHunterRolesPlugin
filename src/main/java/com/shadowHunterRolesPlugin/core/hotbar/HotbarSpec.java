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
 * <p><b>阶段 8 清点（如实记录）</b>：仓内使用者**只剩本文件与两处 javadoc 提及**
 * （{@code HotbarPresentable#spec()} 的旧类型说明、{@code RoleComponent} 的命名说明）
 * ⇒ 无真实调用点；本批次**保留**它（删一个 public 类属 API 收缩，不在本卡的目标内），
 * 只把 kind 形参同步删掉。
 *
 * @deprecated 改用 {@link HotbarSpecification}（新名 = 全拼；旧名不再新增功能）。
 */
@Deprecated
public class HotbarSpec<T extends RoleComponent> extends HotbarSpecification<T> {

    private HotbarSpec(String descriptorLabel, String id, Component displayName, Component description, Material icon,
                       int cooldownTicks, int energyCost) {
        super(descriptorLabel, id, displayName, description, icon, cooldownTicks, energyCost);
    }

    /**
     * 旧构造入口的别名（语义与参数顺序逐字不变）。
     *
     * @deprecated 改用 {@link HotbarSpecification#of(String, String, Component, Component, Material, int, int)}。
     */
    @Deprecated
    public static <T extends RoleComponent> HotbarSpec<T> of(String descriptorLabel, String id, Component displayName,
                                                             Component description, Material icon, int cooldownTicks,
                                                             int energyCost) {
        return new HotbarSpec<>(descriptorLabel, id, displayName, description, icon, cooldownTicks, energyCost);
    }
}
