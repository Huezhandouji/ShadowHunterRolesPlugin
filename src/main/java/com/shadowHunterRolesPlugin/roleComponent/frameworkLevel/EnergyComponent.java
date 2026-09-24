package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

/**
 * 能量组件（阶段 10 · t63 · A1 改正）：系统级能力「能量」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>★ 本组件持有状态与行为</b>：能量真值 {@code current} 与上限 {@code max} 都在本组件里，
 * clamp、检查+扣减合一、增加，全部在本组件内实现 —— **不再转调任何旧端口** ✗
 * （用户口径：{@code ComponentServices} 只保留「玩家实例 + 组件服务」，其他能力做成组件）。
 * <p><b>与容器的分工</b>：能量变化对外的两件**平台事** —— 热键栏置脏（触点④）与
 * {@code EnergyChangeEvent} 事件发布 —— 由容器在构造期以 {@link ChangeSink} 注入；
 * 组件只负责"真值怎么变"，容器只负责"变了之后对平台说什么"。这条分界让组件**不需要**
 * {@code svc()} 就能完整实现能力语义（唯一的 {@code svc()} 用途 = {@code self()} 取玩家）。
 * <p><b>状态唯一</b>：容器侧（{@code RoleInstance}）**不再**持有能量字段 ✗ —— 它只保留
 * {@code getCurrentEnergy()/setCurrentEnergy(...)} 这类**视图**方法（{@code RoleAPI} 的四组对外
 * 入口一字不动）。
 * <p><b>每实例一个</b>：由容器在实例构造期直接构造（**不进 {@code Role} 模板** ⇒ 装配表逐格不变），
 * 并以 id {@code "energy"} 登记进实例容器（可被 {@code svc().components().get(EnergyComponent.class)} 取到）。
 */
public class EnergyComponent extends RoleComponent {

    /**
     * 变更通知（容器在构造期注入）：把「置脏 + 事件」这两件平台事留给容器。
     * <p>注入面**刻意不叫 {@code svc()}**：{@code ComponentServices} 的成员数由冻结件钉死（恰好 10），
     * 而这两件事也不是"组件服务"（它们不提供服务，而是"容器对平台的反应"）。
     */
    public interface ChangeSink {

        /** 能量真值发生变化后调用（**无条件**调用：与既有"无条件置脏 + 无条件事件"逐字一致）。 */
        void onEnergyChanged(int previous, int current, int max);
    }

    /**
     * **「这个组件耗能量」的能力接口**（阶段 13 · t111 第①片：从 `core/hotbar/` 顶层迁入）。
     * <p><b>归属原则</b>：耗能是**能量面**的声明 ⇒ 本接口归能量组件所有（用户裁定"能力各归其家"），
     * <p><b>【已作废】旧口径原文（阶段 6 原文，逐字保留）</b>：「这个组件耗能量」的能力接口（阶段 6），      * 其**顶层形态位于 `core/hotbar/` 包**（顶层文件已于阶段 13 · t111 删除 ✗）——      * 阶段 13 · t111 第①片起改为**本嵌套形态**，原 5 处引用已全部改为嵌套限定名 ✓。
     * <p>{@code MainWeapon} 的 {@code energyCost ≡ 0} 不变量由本接口承载（构造器第 6 位恒传 0）；
     * 非零能量成本只有两个技能（`MeiqiheziBloodySlashSkill` = 8 / `MeiqiheziCircleSlashSkill` = 15）。
     * <p>实现方式沿用旧口径：由 `HotbarRenderComponent.HotbarPresentable` 的 `default` 满足，并由 `ActiveComponent` 显式转发（本组件不实现它，只承载声明面 ✓）。
     */
    public interface EnergyCosting {

        /** 扔放所需能量（点）；{@code 0} = 不耗能（{@code ENERGY_LACK} 态不可达）。 */
        int getEnergyCost();
    }

    private final int max;
    private final ChangeSink sink;

    /** ★ 真值：当前能量（唯一持有处）。 */
    private int current;

    public EnergyComponent(String id, ComponentServices services, int max, ChangeSink sink) {
        super(id, services);
        this.max = Math.max(0, max);
        this.sink = sink != null ? sink : (previous, value, limit) -> { };
        //与既有 RoleInstance 构造期逐字一致：选角色即满能量
        this.current = this.max;
    }

    /** 当前能量（读口）。 */
    public int current() {
        return current;
    }

    /** 能量上限（本组件的状态之一，构造期由容器给出）。 */
    public int max() {
        return max;
    }

    /** 直接写入（组件内 clamp；写后通知容器）。 */
    public void set(int value) {
        int previous = current;
        current = Math.clamp(value, 0, max);
        sink.onEnergyChanged(previous, current, max);
    }

    /** 检查 + 扣减合一；能量不足 ⇒ {@code false} 且不扣（阈值语义与既有端口逐字一致）。 */
    public boolean tryConsume(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (current < amount) {
            return false;
        }
        set(current - amount);
        return true;
    }

    /** 增加能量（内部按上限 clamp）。 */
    public void gain(int amount) {
        set(current + amount);
    }

    /** 减少能量（内部按 0 下限 clamp）。 */
    public void decrease(int amount) {
        set(current - amount);
    }
}
