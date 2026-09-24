package com.shadowHunterRolesPlugin.roleComponent;

/**
 * **「这个组件关心 SanTE 变化」的能力接口**（阶段 12 · t87 · 删除链第一批）。
 *
 * <p>承载一件**迁出物**（语义与签名**逐字不变**）：{@link #onSanTEChange(int, int)} —— SanTE 真值变化通知
 * （**默认空实现，与迁移前 {@code RoleComponent} 的默认实现逐字等价** ⇒ 不关心它的组件零改动即编译 ✓）。
 *
 * <h2>为什么迁出（用户点名 + {@code t85} 盘点结论）</h2>
 * 用户原话：「{@code RoleComponent} 抽象基类甚至还保留着 {@code onSanTEChange} 钩子，而 SanTE 早已经被做成组件」
 * ⇒ SanTE 的**真值持有者**早已是 {@link com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent}
 * ⇒ 它的**变更通知**没有理由继续挂在**所有**组件的抽象基类上 ✗（那会让每个组件都"看起来"关心 SanTE）。
 *
 * <h2>先例（本仓已有的同族动作）</h2>
 * 与 {@code core/hotbar/CooldownAware} 的 {@code onCooldownEnd} 同形 ✓ —— 那一刀把"冷却结束回调"从
 * {@code ActiveComponent} 移到能力接口，**派发点只要求接口、不再要求继承** ✓。本卡把同一动作应用到 SanTE ✓。
 *
 * <h2>派发契约（保持不变，逐字）</h2>
 * <ul>
 *   <li><b>只在 SanTE <u>真变化</u>时派发</b>（{@code pre == now} **不派发**）✓ —— 由
 *       {@code RoleInstance#broadcastSanTEChange} 与 {@code SanTEComponent} 侧共同保证，本卡**未改语义** ✗；</li>
 *   <li><b>按注册表顺序</b>广播（与 {@code update()} / {@code start()} / {@code stop()} 同源）✓；</li>
 *   <li><b>经唯一受保护入口</b>（{@code guardedCall}）调用 ⇒ 抛异常按**故障隔离**语义处置 ✓；</li>
 *   <li><b>可重入</b>：{@code update()} 广播期间组件改 SanTE ⇒ 本回调可在嵌套窗口里再次派发 ✓
 *       （既有 I-14 护栏不变 ✗）。</li>
 * </ul>
 *
 * <h2>为什么放在 {@code roleComponent/} 而不是 {@code core/hotbar/}</h2>
 * 先例 {@code CooldownAware} 与它的实现者（组件）同包；本仓组件层的**能力接口**有多处落点
 * （{@code HotbarItemProviding} / {@code HotbarActionable} 在 {@code core/hotbar/}，
 * {@code CooldownBearing} 亦在彼）。本卡 inScope 只含 {@code roleComponent/} ⇒ 落在本包 ✓；
 * 若队长要求与 {@code core/hotbar/} 的能力接口并列，属**一次纯移动**（改 package + 两个 import）✓ —— 已申报。
 */
public interface SanTEAware {

    /**
     * **SanTE 真值变化通知**。默认空实现（与迁移前 {@code RoleComponent} 的默认实现**逐字等价**）。
     *
     * @param pre 变化前的值
     * @param now 变化后的值（**只在 {@code pre != now} 时才会被调用**）
     */
    default void onSanTEChange(int pre, int now) {
    }
}
