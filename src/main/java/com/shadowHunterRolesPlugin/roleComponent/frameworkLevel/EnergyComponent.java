package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
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
 * <p><b>订阅面 · 本组件为什么"不动"（如实申报）</b>：`SanTEComponent` 的嵌套 {@code Subscriber} 接口
 * 已换成 **JDK {@code Consumer} 监听器列表** ✓；而本组件**没有同类的嵌套订阅接口** ——
 * 它只有 {@link ChangeSink}，且现算**没有任何类 {@code implements} 它**（唯一实现形态 = 容器构造期传的
 * lambda ✓；测试传 {@code null}）⇒ 它已经是"一个函数式接口 + 直接传 lambda"的形态 ✓，
 * 改成 {@code List<Consumer<…>>} 只会把单播变多播、**不解决用户点名的问题**（"其他类实现自己的嵌套接口"）✗
 * ⇒ 本组件**不改** ✓，判断依据写在此处 ✓（用户原话覆盖的两个组件里，真正有待改造的是 `SanTEComponent` ✓）。
 * <p><b>状态唯一</b>：容器侧（{@code RoleInstance}）**不再**持有能量字段 ✗ —— 它只保留
 * {@code getCurrentEnergy()/setCurrentEnergy(...)} 这类**视图**方法（{@code RoleAPI} 的四组对外
 * 入口一字不动）。
 * <p><b>每实例一个</b>：由容器在实例构造期直接构造（**不进 {@code Role} 模板** ⇒ 装配表逐格不变），
 * 并以 id {@code "energy"} 登记进实例容器（可被 {@code svc().components().get(EnergyComponent.class)} 取到）。
 * <p><b>阶段 13 · t120（组件操作面 · 第一片）</b>：本组件**选择实现** {@link OperationProvider} ✓ ——
 * 外部指令面可把整段 payload 交给 {@link #onOperationCommand(String)} 自解析 ✓；
 * <b>grammar 与返回值语义写在该方法的 javadoc 里</b> ✓（本组件**不扩**那个接口 ✗：需要更多能力时
 * 暴露**自己的**方法 ✓ —— 设计定案 §10.2 护栏①）。
 */
public class EnergyComponent extends RoleComponent implements OperationProvider {

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

    // ───────── 阶段 13 · t120：组件操作面（设计定案 §2 / §10.4；本片 = 能量试点） ─────────

    /**
     * **操作面 grammar**（设计定案 §10.4：首 token 必为操作动词 ✓；本片试点只接这四个 ✓）：
     * <pre>
     * add &lt;非负整数&gt;      增能（内部按上限 clamp；等价于 {@link #gain(int)}）
     * consume &lt;非负整数&gt;  试扣（能量不足 ⇒ 不扣、不产生变更；等价于 {@link #tryConsume(int)}）
     * set &lt;非负整数&gt;      直接写入（内部按 [0, max] clamp；等价于 {@link #set(int)}）
     * current             只读：当前能量（**无副作用**；读口 = {@link #current()}）
     * </pre>
     * <b>严格规则（逐条可测）</b>：动词**小写**、**大小写敏感** ✓；{@code add}/{@code consume}/{@code set}
     * **必须**且**只带一个非负整数**（缺参 / 多参 / 非数字 / 负数 / 溢出 ⇒ 拒绝 ✗）；
     * {@code current} **不得**带参数 ✗；payload 为 {@code null} / 空串 / 纯空白 ⇒ **无动词 ⇒ 未识别** ✗
     * （本组件把"空 payload"定义为**未识别** ✓ —— 设计定案 §1 允许组件自定该语义 ✓）。
     * <p><b>返回值语义</b>（与 {@link OperationProvider} 的契约逐字一致；**阶段 13 · t124 起返回字符串** ✓）：
     * **四个动词一律回"写后 / 当前的能量值"**（规范化十进制字符串，如 {@code "55"} ✓）—— 本组件**从不**返回空串
     * （它总有一个可回的值 ✓）；**未知动词 / 空 payload / 语法错 / 参数不合法 ⇒ {@code null}** ✗
     * （= 未识别或拒绝 ✓）。注意 {@code consume} 因能量不足而未扣时**仍算已识别** ✓ ⇒ 回**未变**的当前值
     * （如 {@code "100"}）✓ 而不是 {@code null} ✓。
     * <p><b>副作用与置脏</b>：三个写动词一律经本组件的**既有强类型方法** ⇒ 变更通知（置脏 + 事件）由容器
     * 注入的 {@link ChangeSink} **照常触发** ✓ —— **不新增第二条变更通道** ✗（设计定案 §7.2"一套实现、
     * 两套门面"：字符串面只是**薄适配层** ✓）；{@code current} 无副作用 ✓。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) return null;
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return null;   // 空 / 纯空白 payload ⇒ 未识别
        switch (tokens[0]) {
            case "current" -> {
                if (tokens.length != 1) return null;                     // 只读动词不得带参数
                return Integer.toString(current);                        // 回当前值（无副作用 ✓）
            }
            case "add", "consume", "set" -> {
                if (tokens.length != 2) return null;                     // 必须且只带一个参数
                int amount = parseNonNegative(tokens[1]);
                if (amount < 0) return null;                             // 非数字 / 负数 / 溢出
                switch (tokens[0]) {
                    case "add" -> gain(amount);
                    case "consume" -> tryConsume(amount);
                    default -> set(amount);
                }
                return Integer.toString(current);                        // 一律回"写后值"（含"不足未扣"⇒ 未变值 ✓）
            }
            default -> {
                return null;                                             // 未知动词 ⇒ 未识别
            }
        }
    }

    /** 非负整数解析：非数字 / 负数 / 溢出 ⇒ {@code -1}（调用方据此拒绝 ✗）。 */
    private static int parseNonNegative(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
