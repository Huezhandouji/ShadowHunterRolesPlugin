package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * SanTE 组件（阶段 10 · t63 · A1 改正）：系统级能力「SanTE」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>★ 本组件持有状态与行为</b>：SanTE 真值 {@code current} 与上限 {@code max} 都在本组件里，
 * clamp 与 gain/decrease/set 的语义全在本组件内实现 —— **不再转调任何旧端口** ✗。
 * <p><b>与容器的分工</b>：写后对平台说两句话 —— 发布 {@code SanTEChangeEvent} 与
 * 向组件广播 {@code onSanTEChange(pre, now)}（含 I-14 重入护栏）—— 由容器以 {@link ChangeSink}
 * 注入；组件只负责真值怎么变。
 * <p><b>状态唯一</b>：容器侧**不再**持有 {@code currentSanTE} 字段 ✗（只保留视图方法）。
 * <p><b>归零惩罚的钉 0 语义不变</b>：惩罚组件（{@code DefaultSanTEZeroPunishment}）仍按既有方式
 * 调 {@code svc().sante().set(0)} 逐 tick 钉 0 ⇒ 走的还是这一条 clamp + 派发路径。
 */
public class SanTEComponent extends RoleComponent {

    /** 变更通知（容器在构造期注入）：事件发布 + {@code onSanTEChange} 派发（含重入护栏）都在容器侧。 */
    public interface ChangeSink {

        /** SanTE 真值发生变化后调用（**无条件**调用：与既有"无条件事件 + 无条件派发"逐字一致）。 */
        void onSanTEChanged(int previous, int current, int max);
    }

    private final int max;
    private final ChangeSink sink;

    /** ★ 真值：当前 SanTE（唯一持有处）。 */
    private int current;

    public SanTEComponent(String id, ComponentServices services, int max, ChangeSink sink) {
        super(id, services);
        this.max = Math.max(0, max);
        this.sink = sink != null ? sink : (previous, value, limit) -> { };
        //与既有 RoleInstance 构造期逐字一致：选角色即满 SanTE
        this.current = this.max;
    }

    /** 当前 SanTE（读口）。 */
    public int current() {
        return current;
    }

    /** SanTE 上限（构造期由容器给出）。 */
    public int max() {
        return max;
    }

    /** 直接写入（组件内 clamp；写后通知容器）。 */
    public void set(int value) {
        int previous = current;
        current = Math.clamp(value, 0, max);
        sink.onSanTEChanged(previous, current, max);
    }

    /** 增加 SanTE（内部按上限 clamp）。 */
    public void gain(int amount) {
        set(current + amount);
    }

    /** 减少 SanTE（内部按 0 下限 clamp；归零惩罚由既有组件监听真变化后触发）。 */
    public void decrease(int amount) {
        set(current - amount);
    }
}
