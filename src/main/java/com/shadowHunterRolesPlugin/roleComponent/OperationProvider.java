package com.shadowHunterRolesPlugin.roleComponent;

/**
 * 组件操作面：让外部指令面（管理员 / 调试面）能按「玩家 + 组件 id」操作任意组件状态。
 * 组件自行选择实现本接口，未实现的组件不支持操作指令。
 *
 * <h2>契约（只有一个方法，返回 String）</h2>
 * <ul>
 *   <li>{@code null} = 未识别（未知动词 / 空 payload）或拒绝执行（语法错 / 参数不合法）；</li>
 *   <li>{@code ""} = 已识别但没有回值（纯写操作）；</li>
 *   <li>非空串 = 规范化值（读操作回值；写操作可回"写后状态"），格式由组件自定。</li>
 * </ul>
 * 返回值与 {@code RoleAPI#executeComponentOperation(UUID, String, String)} 的 {@code String} 逐字对齐：
 * 布尔换成字符串后组件才能把值交出来，读缺口由此关闭，读写都走这一个操作入口。
 * 参数是整段 payload 字符串（op 与 args 合并后交给组件自解析），本接口不解析它。
 *
 * <h2>三条护栏（防先例）</h2>
 * <ol>
 *   <li>冻结为单方法：不得再加方法、不得加默认实现；组件需要更多能力时暴露自己的方法，不扩本接口；</li>
 *   <li>包定位 = 组件契约：本接口留在 {@code roleComponent/}，不进 {@code core/} 的"公共能力区"（后者已清空）；</li>
 *   <li>将来若两个以上组件需要同一操作面，走"折进组件"的既有做法，不再加顶层接口。</li>
 * </ol>
 *
 * <h2>允许存在的条件</h2>
 * <ul>
 *   <li>允许：能力接口表达的东西不可能成为组件时可做接口 —— "可被外部指令操作"是跨切面开关
 *       （无状态、无生命周期、不按实例持有）；</li>
 *   <li>禁止：表达的东西符合组件定义时（拥有状态 / 生命周期 / 按实例持有）一律做成组件
 *       —— 冷却状态、热键栏提供、耗能声明都已折进组件，那些接口完全多余。</li>
 * </ul>
 *
 * <h2>payload 约定</h2>
 * 首 token 必为操作动词（{@code add 5} / {@code consume 3} / {@code current} / {@code set 120}），
 * 派发器可选用它做权限校验与 Tab 补全（组件可忽略）；其余由组件自解析，
 * grammar 必须写进实现它的组件的 javadoc，本接口不规定 grammar。
 * 本接口只定义"组件如何接受一条操作指令"，指令面 / 派发器 / {@code RoleAPI} 收口不归它。
 */
public interface OperationProvider {

    /**
     * 处理一条操作指令（整段 payload 原样交给组件）。
     *
     * @param payload 整段剩余文本（可含空格）；{@code null} / 空串 / 纯空白 的语义由组件自行定义，
     *                但必须写进该组件的 javadoc
     * @return {@code null} = 未识别（未知动词 / 空 payload）或拒绝执行（语法错 / 参数不合法）；
     *         {@code ""} = 已识别但没有回值（纯写操作）；
     *         非空串 = 规范化值（读操作回值 / 写操作回"写后状态"）
     */
    String onOperationCommand(String payload);
}
