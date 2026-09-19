package com.shadowHunterRolesPlugin.core.hotbar;

/**
 * 热键栏物品的种类（阶段 6 起为**注册处的权威标记**：行为分支一律读注册 kind，组件自述 kind 只作表现）。
 * <p>{@link #PASSIVE} = 被动：不占热键栏、不参与渲染、也没有冷却 —— 冷却端口的 PASSIVE 分支采用
 * 显式无声语义（恒就绪 / 恒 0 / 不写任何表 / end 恒 false）。
 */
public enum ItemKind {
    SKILL,
    MAIN_WEAPON,
    PASSIVE
}
