package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 能量组件：系统级能力「能量」的**组件形态**（每角色实例一个）。
 * <p><b>★ 本组件持有状态与行为</b>：能量真值 {@code current} 与上限 {@code max} 都在本组件里，
 * clamp、检查+扣减合一、增加，全部在本组件内实现 —— **不再转调任何旧端口** ✗
 * （用户口径：{@code ComponentServices} 只保留「玩家实例 + 组件服务」，其他能力做成组件）。
 * <p><b>与容器的分工</b>：能量变化之后对外的**平台事**是热键栏置脏 —— 由容器把一条
 * 监听器注册进本组件的名单来承担；组件只负责"真值怎么变"，容器只负责"变了之后做什么"。
 * 这条分界让组件**不需要**
 * {@code svc()} 就能完整实现能力语义（唯一的 {@code svc()} 用途 = {@code self()} 取玩家）。
 *
 * <h2>订阅面：<b>JDK {@code Consumer} 监听器列表</b></h2>
 * <b>旧形态</b>：本组件曾嵌套一个 {@code public interface ChangeSink}（唯一方法
 * {@code onEnergyChanged(int previous, int current, int max)}），并由一个**单播字段**持有它 ✗ ——
 * **没有任何类 {@code implements} 它**（唯一实现形态 = 容器构造期传的 lambda ⇒ 它其实是"单播字段"，
 * 而不是被其他类实现的接口 ✗）。**监听器列表**的形态是：「一个函数式接口的列表，其他类只需要添加
 * {@code Consumer} 即可」✓。
 * <p><b>新形态</b>：本组件持有 {@code List<Listener>}（{@link Listener} = {@code owner} +
 * {@code Consumer<Change>} 的**成对**登记，record ✓），对外只暴露 {@link #addListener(RoleComponent, Consumer)} ✓；
 * ★ **用 JDK 的 {@code Consumer}**（**不新增自定义接口** ✗ —— 「凡关注点已是组件 ⇒ 不得再为它新增能力接口」
 * 同理）✓。
 * <p><b>载荷取「最小充分类型」</b>：旧方法有三个入参（{@code previous} / {@code current} / {@code max}）⇒
 * 用一个**小 record** {@link Change}（**record 不是接口** ⇒ 不违反"不新增接口"✓）—— 三者都保留 ✓
 * （{@code max} 与旧载荷一致 ⇒ 监听器拿到的值与旧 lambda 逐字相同 ✓）。
 * <p><b>移除语义</b>：{@link #removeListener(Listener)} 按**引用相等**（{@code List.remove(Object)} 的既有语义）
 * ✓ —— 调用方须持有**同一个** {@link Listener} 实例（{@link #addListener(RoleComponent, Consumer)} **返回**该实例
 * ⇒ 需要移除的消费者把它存进私有字段即可 ✓）。容器注册的是**与实例同寿命**的监听器 ⇒ 无需移除 ✓。
 *
 * <p><b>状态唯一</b>：容器侧（{@code RoleInstance}）**不再**持有能量字段 ✗ —— 它只保留
 * {@code getCurrentEnergy()/setCurrentEnergy(...)} 这类**视图**方法（{@code RoleAPI} 的四组对外
 * 入口一字不动）。
 * <p><b>每实例一个</b>：由容器在实例构造期直接构造（**不进 {@code Role} 模板** ⇒ 装配表逐格不变），
 * 并以 id {@code "energy"} 登记进实例容器（可被 {@code svc().components().get(EnergyComponent.class)} 取到）。
 * <p><b>组件操作面</b>：本组件**选择实现** {@link OperationProvider} ✓ ——
 * 外部指令面可把整段 payload 交给 {@link #onOperationCommand(String)} 自解析 ✓；
 * <b>grammar 与返回值语义写在该方法的 javadoc 里</b> ✓（本组件**不扩**那个接口 ✗：需要更多能力时
 * 暴露**自己的**方法 ✓（不扩本接口）。
 */
public class EnergyComponent extends RoleComponent implements OperationProvider {

    /**
     * **本组件的登记 id**（★ 知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器装配时只读这个 **id + 工厂**（{@code data}），不点名组件类 ✓。
     */
    public static final String ID = "energy";
    /** **本组件的能量上限**（★ 组件侧配置 · 占位值；原住在框架级清单里，现归组件自己）。 */
    public static final int ENERGY_MAX = 100;

    /**
     * **一次能量变更的载荷**（**record，不是接口** ✓）。
     *
     * <h2>为什么需要它</h2>
     * 一次变更携带三个值（{@code previous} / {@code current} / {@code max}）；本组件的
     * 监听器列表用 JDK {@code Consumer} ⇒ 需要一个**载体**把这三个值一起交出去 ✓。
     * 取**最小充分类型**：逐字保留旧载荷的三个值 ✓（监听器据此置脏 ✓）。
     */
    public record Change(int previous, int current, int max) {
    }

    /**
     * **一条监听登记**：{@code owner}（谁订阅）+ {@code listener}（怎么通知）**成对**持有 ✓。
     *
     * <h2>为什么把 owner 一起登记</h2>
     * 若由容器**逐个**经受保护调用派发，异常时要能**点名到具体组件** ⇒ 每一条登记都需要知道自己属于哪个组件 ✓ ——
     * 旧 {@code ChangeSink} 形态靠"lambda 闭包"隐式归属（无法点名 ✗）；改用 {@code Consumer} 后闭包**没有身份** ✗
     * ⇒ 由注册方在 {@link #addListener(RoleComponent, Consumer)} 里**显式给出** ✓。
     * <p>record 不是接口 ✓（不违反"不新增自定义接口"）。
     */
    public record Listener(RoleComponent owner, Consumer<Change> listener) {
    }

    /**
     * **「这个组件耗能量」的能力接口**：耗能是**能量面**的声明 ⇒ 本接口归能量组件所有
     * （能力各归其家：耗能声明与能量真值同属能量面）。
     * 非零能量成本只有两个技能（`MeiqiheziBloodySlashSkill` = 8 / `MeiqiheziCircleSlashSkill` = 15）。
     * <p>实现方式：由 `HotbarRenderComponent.HotbarPresentable` 的 `default` 满足，并由 `ActiveComponent`
     * 显式转发（本组件不实现它，只承载声明面 ✓）。
     */
    public interface EnergyCosting {

        /** 扔放所需能量（点）；{@code 0} = 不耗能（{@code ENERGY_LACK} 态不可达）。 */
        int getEnergyCost();
    }

    /** **监听器名单** —— 顺序 = 添加先后 ✓（迭代序稳定 ⇒ "按注册序通知"可复现 ✓）。 */
    private final List<Listener> listeners = new ArrayList<>();

    private final int max;

    /** ★ 真值：当前能量（唯一持有处）。 */
    private int current;

    public EnergyComponent(String id, ComponentServices services, int max, Consumer<Change> listener) {
        super(id, services);
        this.max = Math.max(0, max);
        if (listener != null) {
            listeners.add(new Listener(this, listener));
        }
        //与既有 RoleInstance 构造期逐字一致：选角色即满能量
        this.current = this.max;
    }

    /**
     * **添加监听器**（**唯一**的订阅入口 ✓）—— 订阅方调用，并在需要撤销时用
     * {@link #removeListener(Listener)} 成对移除 ✓。
     * <p><b>幂等</b>：同一 {@code owner} + 同一 {@code listener} 重复添加**不重复登记** ✓。
     * <p>{@code owner} 或 {@code listener} 为 {@code null} ⇒ **忽略**（回 {@code null} ✓）。
     * <p><b>返回值</b>：本次登记对应的 {@link Listener} 实例（**调用方存起来** ⇒ 将来用
     * {@link #removeListener(Listener)} 按引用移除 ✓）。
     */
    public Listener addListener(RoleComponent owner, Consumer<Change> listener) {
        if (owner == null || listener == null) {
            return null;
        }
        Listener entry = new Listener(owner, listener);
        if (!listeners.contains(entry)) {
            listeners.add(entry);
        }
        return entry;
    }

    /**
     * **移除监听器**（按**引用相等**；不在名单里 ⇒ no-op 且返回 {@code false} ✓）——
     * 调用方必须持有**同一个** {@link Listener} 实例（把 {@link #addListener} 的返回值存进私有字段即可 ✓）。
     */
    public boolean removeListener(Listener entry) {
        return entry != null && listeners.remove(entry);
    }

    /** 当前监听器数（诊断读口；供探针与运行级取证使用）。 */
    public int listenerCount() {
        return listeners.size();
    }

    /**
     * **逐个**把监听登记交给调用方（"逐监听器故障隔离"的承载面）✓。
     * <p><b>为什么需要它</b>：若由本组件自己"一趟循环全调"，某个监听器抛异常 ⇒
     * **排在它后面的监听器就收不到通知** ✗。容器侧用的是"**逐个**经受保护调用"
     * ⇒ 只隔离抛异常的那一个、其余照常收到 ✓ —— 这条性质**必须保住** ✗。
     * <p>所以这里**不**替调用方做派发决策，只提供**遍历**：由容器决定"怎么调、怎么护" ✓。
     * <p><b>遍历期间增删的安全</b>：本方法对名单取**快照**后再遍历（{@code List.copyOf}）
     * ⇒ 遍历途中 {@link #addListener(RoleComponent, Consumer)} / {@link #removeListener(Listener)}
     * 不会触发 {@code ConcurrentModificationException}，也**不影响本次遍历**（快照语义 = 本次通知的接受集在进入时已定 ✓）。
     * <p><b>不通知快照之外的新增</b>：本趟已开始 ⇒ 新加的监听器从**下一趟**起收到 ✓（可复现、不随实现漂移 ✓）。
     */
    public void forEachListener(Consumer<Listener> action) {
        if (action == null) {
            return;
        }
        for (Listener entry : List.copyOf(listeners)) {
            action.accept(entry);
        }
    }

    /**
     * **通知全部监听器**（一趟直调）—— 本方法就是"派发"这件事的**薄实现**：
     * {@code forEachListener(entry -> entry.listener().accept(change))} ✓。
     * <p><b>调用时机</b>：真值写入路径（{@link #set(int)}）在 clamp 之后调用它 ✓ —— 与旧单播字段的
     * "无条件通知"时机**逐字一致** ✓（{@code previous == current} 时也通知 ✓，需要"只在真变化时动作"的
     * 监听器自己比较 ✓）。
     * <p><b>不是第二条变更通道</b> ✗：本方法**只**读名单并调监听器，不改真值 ✓。
     */
    public void notifyListeners(Change change) {
        if (change == null) {
            return;
        }
        forEachListener(entry -> entry.listener().accept(change));
    }

    /** 当前能量（读口）。 */
    public int current() {
        return current;
    }

    /** 能量上限（本组件的状态之一，构造期由容器给出）。 */
    public int max() {
        return max;
    }

    // ───────── 基类通用面（框架按 id 取到通用面即可读/写，不必认识本组件）─────────

    /** {@inheritDoc} —— 框架视图的读数落点。 */
    @Override
    public int readCurrentEnergy() {
        return current;
    }

    /** {@inheritDoc} —— 框架视图的写入落点（clamp 与通知仍在 {@link #set(int)} 里）。 */
    @Override
    public void writeCurrentEnergy(int value) {
        set(value);
    }

    /** 直接写入（组件内 clamp；写后通知监听器）。 */
    public void set(int value) {
        int previous = current;
        current = Math.clamp(value, 0, max);
        notifyListeners(new Change(previous, current, max));
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
    public void increase(int amount) {
        set(current + amount);
    }

    /** 减少能量（内部按 0 下限 clamp）。 */
    public void decrease(int amount) {
        set(current - amount);
    }

    // ───────── 组件操作面（能量试点） ─────────

    /**
     * **操作面 grammar**（首 token 必为操作动词 ✓；试点只接这四个 ✓）：
     * <pre>
     * add &lt;非负整数&gt;      增能（内部按上限 clamp；等价于 {@link #increase(int)}）
     * consume &lt;非负整数&gt;  试扣（能量不足 ⇒ 不扣、不产生变更；等价于 {@link #tryConsume(int)}）
     * set &lt;非负整数&gt;      直接写入（内部按 [0, max] clamp；等价于 {@link #set(int)}）
     * current             只读：当前能量（**无副作用**；读口 = {@link #current()}）
     * </pre>
     * <b>严格规则（逐条可测）</b>：动词**小写**、**大小写敏感** ✓；{@code add}/{@code consume}/{@code set}
     * **必须**且**只带一个非负整数**（缺参 / 多参 / 非数字 / 负数 / 溢出 ⇒ 拒绝 ✗）；
     * {@code current} **不得**带参数 ✗；payload 为 {@code null} / 空串 / 纯空白 ⇒ **无动词 ⇒ 未识别** ✗
     * （本组件把"空 payload"定义为**未识别** ✓ —— 允许组件自定该语义 ✓）。
     * <p><b>返回值语义</b>（与 {@link OperationProvider} 的契约逐字一致）：**四个动词一律回"写后 / 当前的能量值"**
     * （规范化十进制字符串，如 {@code "55"} ✓）—— 本组件**从不**返回空串（它总有一个可回的值 ✓）；
     * **未知动词 / 空 payload / 语法错 / 参数不合法 ⇒ {@code null}** ✗（= 未识别或拒绝 ✓）。
     * 注意 {@code consume} 因能量不足而未扣时**仍算已识别** ✓ ⇒ 回**未变**的当前值（如 {@code "100"}）✓
     * 而不是 {@code null} ✓。
     * <p><b>副作用与置脏</b>：三个写动词一律经本组件的**既有强类型方法** ⇒ 变更通知（置脏）由
     * 容器注册的监听器**照常触发** ✓ —— **不新增第二条变更通道** ✗（"一套实现、
     * 两套门面"：字符串面只是**薄适配层** ✓）；{@code current} 无副作用 ✓。
     *
     * <h2>payload 口径（★ 先读这一段）</h2>
     * <b>组件收到的是「含动词的整段 payload」</b> —— 外部指令面把**首 token 起、直到行尾**的整段
     * **原样**交给本方法（{@code OperationProvider} 明写「op 与 args **合并**后交给组件自解析」，
     * 派发器**不解析**它 ✗）⇒ 本方法自行切分 token ✓。
     * <p>★ <b>指令里的 {@code #index} 不是 op/args 分隔符</b>：{@code componentId[#index]} 的 {@code #}
     * 是**同 id 多份实例的下标**（在**派发层**就已被切掉，用于选中第几份实例；多份且未给下标 ⇒ 直接
     * 拒绝 ⇒ **本方法根本收不到**）⇒ 它**与 payload 无关** ✗ —— **不存在** {@code op#args} 这种形态 ✓。
     *
     * <h2>返回值三态（与 {@link OperationProvider} 契约逐字一致）</h2>
     * <ul>
     *   <li>{@code null} = **未识别或拒绝**（未知动词 / 语法错 / 参数不合法）✗；</li>
     *   <li>{@code ""}（空串）= **已识别但没有回值**（纯写操作）✓ —— ★ 本组件**从不**回空串
     *       （它总有一个可回的值：读类回当前值、写类回写后状态）✓；</li>
     *   <li>**非空串** = **规范化值** ✓。</li>
     * </ul>
     * <p>★ <b>组件内部抛出的 {@code RuntimeException} 由派发层吞掉并回 {@code null}</b>
     * （派发层在调用本方法处 {@code try}/{@code catch} ⇒ 异常不得逃到调用方）⇒ ★ 调用方
     * **无法**从 {@code null} 区分「语法错」与「组件崩了」✗（两者在外部看起来一样）。
     *
     * <h2>可直接照抄的指令</h2>
     * <pre>
     * /role operation query  @s energy current
     * /role operation modify @s energy add 5        ⇒ "5"（写后值）
     * /role operation modify @s energy add -5       ⇒ null（负数 ⇒ 参数不合法）
     * /role operation query  @s energy current 1    ⇒ null（只读动词不得带参数）
     * </pre>
     * ★ 反例说明：第 3 条走**参数不合法**分支（{@code parseNonNegative} 见负值 ⇒ 回 {@code -1} ⇒ 拒绝）；第 4 条走**只读动词带参**分支。
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
                    case "add" -> increase(amount);
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
