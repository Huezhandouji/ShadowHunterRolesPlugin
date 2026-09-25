package com.shadowHunterRolesPlugin.roleComponent;

/**
 * **装配期依赖检查失败**：缺必需依赖，或依赖图里有环。
 * <p>
 * 为什么用异常（而不是"告警后继续"）：用户计划第三条的原话是「如果发现没有获取到依赖**应该抛出异常**
 * 让框架**阻止当前角色的加载和注册**」⇒ 这是**硬失败**语义：坏掉的模板绝不注册、绝不进游戏。
 * <p>
 * 谁抛：{@code core/Role#verifyDependencies()}（装配期检查的唯一实现点）。
 * 谁接：{@code registry/RoleLoader#loadInto} 的既有 {@code catch (Throwable)} 分支 —— 记一条
 * {@code SEVERE}、**跳过该角色**、其余角色继续装配（fail-fast 且按角色隔离）。
 * <p>
 * 继承 {@link IllegalStateException} 的理由：它表达的是"**当前装配状态不允许继续**"，
 * 而不是"调用方传了非法参数"（后者才是 {@link IllegalArgumentException}）。
 */
public class ComponentDependencyException extends IllegalStateException {

    public ComponentDependencyException(String message) {
        super(message);
    }
}
