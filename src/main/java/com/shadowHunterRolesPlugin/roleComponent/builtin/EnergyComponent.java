package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * 能量组件：系统级能力「能量」的组件形态（每角色实例一个）。
 * <p>能量真值 {@code current} 与上限 {@code max} 都持有在本组件里，clamp、检查+扣减合一、
 * 增加全部在本组件内实现，不转调任何端口（{@code ComponentServicesPort} 只保留「玩家实例 + 组件服务」，
 * 其他能力一律做成组件）。
 * <p><b>与容器的分工</b>：能量变化之后对外的平台事是热键栏置脏 —— 由容器把一条监听器注册进
 * 本组件的名单来承担；组件只负责"真值怎么变"，容器只负责"变了之后做什么"。
 * 这条分界让组件不需要 {@code svc()} 就能完整实现能力语义（唯一的 {@code svc()} 用途 =
 * {@code self()} 取玩家）。
 *
 * <h2>订阅面：JDK {@code Consumer} 监听器列表</h2>
 * 监听器列表 = 一个函数式接口的列表，其他类只需要添加 {@code Consumer} 即可。
 * 本组件持有 {@code List<Listener>}（{@link Listener} = {@code owner} + {@code Consumer<Change>}
 * 的成对登记，record），对外只暴露 {@link #addListener(RoleComponent, Consumer)}；
 * 用 JDK 的 {@code Consumer}，不新增自定义接口（「凡关注点已是组件，不得再为它新增能力接口」同理）。
 * <p><b>载荷</b>：取最小充分类型 —— record {@link Change} 带 {@code previous} / {@code current} /
 * {@code max} 三个值（监听器据此置脏）。
 * <p><b>移除语义</b>：{@link #removeListener(Listener)} 按引用相等（{@code List.remove(Object)}
 * 的既有语义）—— 调用方须持有同一个 {@link Listener} 实例；{@link #addListener} 返回该实例，
 * 需要移除的消费者把它存进私有字段即可。容器注册的是与实例同寿命的监听器，无需移除。
 *
 * <p><b>状态唯一</b>：容器侧（{@code RoleInstance}）不再持有能量字段，只保留
 * {@code getCurrentEnergy()/setCurrentEnergy(...)} 这类视图方法（{@code RoleAPI} 的四组对外入口
 * 一字不动）。
 * <p><b>每实例一个</b>：由容器在实例构造期直接构造（不进 {@code Role} 模板，装配表逐格不变），
 * 并以 id {@code "energy"} 登记进实例容器（可被 {@code svc().components().get(EnergyComponent.class)}
 * 取到）。
 * <p><b>组件操作面</b>：本组件选择实现 {@link OperationProvider} ——
 * 外部指令面可把整段 payload 交给 {@link #onOperationCommand(String)} 自解析；
 * grammar 与返回值语义写在该方法的 javadoc 里（本组件不扩那个接口：需要更多能力时暴露自己的方法）。
 */
public class EnergyComponent extends RoleComponent implements OperationProvider {

    /**
     * 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器在装配期只读这个 id 与工厂（{@code Role.ComponentEntry}），不点名组件类。
     */
    public static final String ID = "energy";
    /** 本组件的能量上限（组件侧配置 · 占位值）。 */
    public static final int ENERGY_MAX = 100;

    /**
     * 一次能量变更的载荷（record，不是接口）。
     *
     * <h2>为什么需要它</h2>
     * 一次变更携带三个值（{@code previous} / {@code current} / {@code max}）；本组件的
     * 监听器列表用 JDK {@code Consumer}，需要一个载体把这三个值一起交出去。
     * 取最小充分类型：三个值都带上（监听器据此置脏）。
     */
    public record Change(int previous, int current, int max) {
    }

    /**
     * 一条监听登记：{@code owner}（谁订阅）+ {@code listener}（怎么通知）成对持有。
     *
     * <h2>为什么把 owner 一起登记</h2>
     * 若由容器逐个经 {@code guardedCall} 派发，异常时要能点名到具体组件，因此每一条登记都需要
     * 知道自己属于哪个组件；而 {@code Consumer} 闭包没有身份，所以由注册方在
     * {@link #addListener(RoleComponent, Consumer)} 里显式给出。
     * <p>record 不是接口（不违反"不新增自定义接口"）。
     */
    public record Listener(RoleComponent owner, Consumer<Change> listener) {
    }


    /** 监听器名单 —— 顺序 = 添加先后（迭代序稳定，因此"按注册序通知"可复现）。 */
    private final List<Listener> listeners = new ArrayList<>();

    private final int max;

    /** 真值：当前能量（唯一持有处）。 */
    private int current;

    public EnergyComponent(String id, ComponentServicesPort services, int max, Consumer<Change> listener) {
        super(id, services);
        this.max = Math.max(0, max);
        if (listener != null) {
            listeners.add(new Listener(this, listener));
        }
        //与既有 RoleInstance 构造期逐字一致：选角色即满能量
        this.current = this.max;
    }

    /**
     * 本组件的装配描述符（与技能/被动同规；不带栏位）。
     *
     * <p>「一次变更则请求重绘一次」的接线在 {@code create} 里完成：描述符从 {@code services}
     * 按 id 取到渲染组件并挂上「变更即置脏」的监听。
     *
     * <p><b>注册顺序前提</b>：渲染组件必须先注册（它排在装配清单第一位），因此此处按 id 一定取得到；
     * 取不到时不挂监听，也不抛异常。
     */
    public static final class Specification extends RoleComponent.Specification<EnergyComponent> {

        public Specification() {
            super("Energy");
        }

        @Override
        public EnergyComponent create(String id, ComponentServicesPort services) {
            return new EnergyComponent(id, services, ENERGY_MAX, change -> {
                RoleComponent render = services.components().getById(HotbarRenderComponent.ID);
                if (render instanceof HotbarRenderComponent hotbar) {
                    hotbar.markDirty();
                }
            });
        }
    }

    /**
     * 添加监听器（唯一的订阅入口）—— 订阅方调用，并在需要撤销时用
     * {@link #removeListener(Listener)} 成对移除。
     * <p><b>幂等</b>：同一 {@code owner} + 同一 {@code listener} 重复添加不重复登记。
     * <p>{@code owner} 或 {@code listener} 为 {@code null} 则忽略（回 {@code null}）。
     * <p><b>返回值</b>：本次登记对应的 {@link Listener} 实例（调用方存起来，将来用
     * {@link #removeListener(Listener)} 按引用移除）。
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
     * 移除监听器（按引用相等；不在名单里则 no-op 且返回 {@code false}）——
     * 调用方必须持有同一个 {@link Listener} 实例（把 {@link #addListener} 的返回值存进私有字段即可）。
     */
    public boolean removeListener(Listener entry) {
        return entry != null && listeners.remove(entry);
    }

    /** 当前监听器数（诊断读口；供探针与运行级取证使用）。 */
    public int listenerCount() {
        return listeners.size();
    }

    /**
     * 逐个把监听登记交给调用方（"逐监听器故障隔离"的承载面）。
     * <p><b>为什么需要它</b>：若由本组件自己"一趟循环全调"，某个监听器抛异常，
     * 排在它后面的监听器就收不到通知。容器侧用的是"逐个经 {@code guardedCall} 调用"，
     * 只隔离抛异常的那一个、其余照常收到 —— 这条性质必须保住。
     * <p>所以这里不替调用方做派发决策，只提供遍历：由容器决定"怎么调、怎么护"。
     * <p><b>遍历期间增删的安全</b>：本方法对名单取快照后再遍历（{@code List.copyOf}），
     * 遍历途中 {@link #addListener(RoleComponent, Consumer)} / {@link #removeListener(Listener)}
     * 不会触发 {@code ConcurrentModificationException}，也不影响本次遍历
     * （快照语义 = 本次通知的接受集在进入时已定）。
     * <p><b>不通知快照之外的新增</b>：本趟已开始，新加的监听器从下一趟起收到（可复现、不随实现漂移）。
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
     * 通知全部监听器（一趟直调）—— 本方法就是"派发"这件事的薄实现：
     * {@code forEachListener(entry -> entry.listener().accept(change))}。
     * <p><b>调用时机</b>：真值写入路径（{@link #set(int)}）在 clamp 之后调用它 —— 与既有口径
     * 逐字一致：{@code previous == current} 时也通知，需要"只在真变化时动作"的监听器自己比较。
     * <p><b>不是第二条变更通道</b>：本方法只读名单并调监听器，不改真值。
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

    //消费者直接持有强类型 EnergyComponent，调 current() / set(int)。

    /** 直接写入（组件内 clamp；写后通知监听器）。 */
    public void set(int value) {
        int previous = current;
        current = Math.clamp(value, 0, max);
        notifyListeners(new Change(previous, current, max));
    }

    /** 检查 + 扣减合一；能量不足则回 {@code false} 且不扣（阈值语义与 {@code SanTEComponent#tryConsume(int)} 逐字同形）。 */
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

    // ───────── 跨实例：按玩家 UUID 增减别人的能量（"削减敌人能量"这类技能用）─────────

    /**
     * 给某个玩家（按 UUID）增加能量，并返回真实增加量。
     *
     * <p><b>为什么返回真实量</b>：对方可能已接近上限，实际只加了 {@code max - current}。
     * 调用方（例如"按实际回复量结算"的技能）需要真实值，而不是自己请求的那个数。
     *
     * @param target 目标玩家 UUID
     * @param amount 想增加的量（{@code <= 0} 不做任何事，返回 0）
     * @return 真实增加量（clamp 之后）；目标无角色实例 / 取不到能量组件 / 无变化则回 0
     */
    public int increaseEnergy(UUID target, int amount) {
        EnergyComponent other = energyOf(target);
        if (other == null || other == this || amount <= 0) {
            return 0;
        }
        int before = other.current();
        other.set(before + amount);
        return other.current() - before;
    }

    /**
     * 给某个玩家（按 UUID）减少能量，并返回真实减少量。
     *
     * <p><b>为什么返回真实量</b>：对方能量可能不足，实际只扣到 {@code 0} 为止。
     * 若要"能量不足就整个不生效"，先用 {@link #current()} 自行判定，
     * 或用 {@link #decreaseAtMost(UUID, int, int)} 表达"至少要有这么多才扣"。
     *
     * @param target 目标玩家 UUID
     * @param amount 想减少的量（{@code <= 0} 不做任何事，返回 0）
     * @return 真实减少量（clamp 之后，恒 ≥ 0）；目标无角色实例 / 取不到能量组件 / 无变化则回 0
     */
    public int decreaseEnergy(UUID target, int amount) {
        EnergyComponent other = energyOf(target);
        if (other == null || other == this || amount <= 0) {
            return 0;
        }
        int before = other.current();
        other.set(before - amount);
        return before - other.current();
    }

    /**
     * 门槛式减少：只有当对方当前能量 ≥ {@code minimumRequired} 时才扣减，
     * 否则一点都不扣（返回 0）。
     *
     * <p>用途：设计"抽能"类技能时常见的两种口径 ——
     * ① 有多少扣多少则用 {@link #decreaseEnergy(UUID, int)}；
     * ② 不够就整个不生效则用本方法（{@code minimumRequired = amount}）。
     *
     * @param minimumRequired 生效门槛（对方能量 < 它则返回 0；{@code <= 0} 则门槛不设）
     * @return 真实减少量（未过门槛则回 0）
     */
    public int decreaseAtMost(UUID target, int amount, int minimumRequired) {
        EnergyComponent other = energyOf(target);
        if (other == null || other == this || amount <= 0) {
            return 0;
        }
        if (minimumRequired > 0 && other.current() < minimumRequired) {
            return 0;
        }
        int before = other.current();
        other.set(before - amount);
        return before - other.current();
    }

    /**
     * 解析某个玩家实例上的能量组件（跨实例；未命中一律 {@code null}）。
     *
     * <p>组件层唯一的跨实例取法：经插件单例拿到目标 {@code RoleInstance}，
     * 再按本组件自己的登记 id（{@link #ID}）取同类实例。
     * 本组件不新增端口、不持有 {@code RoleManager}，id 也不写第二份字面量。
     * <p>目标无角色 / 未加载则回 {@code null}（调用方按"无事可做"处理）。
     */
    private static EnergyComponent energyOf(UUID target) {
        if (target == null) {
            return null;
        }
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if (plugin == null) {
            return null;
        }
        RoleInstance instance = plugin.roleInstanceOf(target);
        if (instance == null) {
            return null;
        }
        RoleComponent component = instance.componentRegistry().getById(ID);
        return component instanceof EnergyComponent energy ? energy : null;
    }

    // ───────── 组件操作面 ─────────

    /**
     * 操作面 grammar（首 token 必为操作动词，只接这四个）：
     * <pre>
     * add &lt;非负整数&gt;      增能（内部按上限 clamp；等价于 {@link #increase(int)}）
     * consume &lt;非负整数&gt;  试扣（能量不足则不扣、不产生变更；等价于 {@link #tryConsume(int)}）
     * set &lt;非负整数&gt;      直接写入（内部按 [0, max] clamp；等价于 {@link #set(int)}）
     * current             只读：当前能量（无副作用；读口 = {@link #current()}）
     * </pre>
     * <b>严格规则（逐条可测）</b>：动词小写、大小写敏感；{@code add}/{@code consume}/{@code set}
     * 必须且只带一个非负整数（缺参 / 多参 / 非数字 / 负数 / 溢出则拒绝）；
     * {@code current} 不得带参数；payload 为 {@code null} / 空串 / 纯空白则无动词，视为未识别
     * （本组件把"空 payload"定义为未识别）。
     * <p><b>四个动词一律回"写后 / 当前的能量值"</b>（规范化十进制字符串，如 {@code "55"}）——
     * {@code consume} 因能量不足而未扣时仍算已识别，回未变的当前值（如 {@code "100"}）而不是 {@code null}。
     * <p><b>副作用与置脏</b>：三个写动词一律经本组件的既有强类型方法，变更通知（置脏）由
     * 容器注册的监听器照常触发 —— 不新增第二条变更通道（"一套实现、两套门面"：
     * 字符串面只是薄适配层）；{@code current} 无副作用。
     *
     * <h2>payload 口径</h2>
     * <b>组件收到的是「含动词的整段 payload」</b> —— 外部指令面把首 token 起、直到行尾的整段
     * 原样交给本方法（{@code OperationProvider} 明写「op 与 args 合并后交给组件自解析」，
     * 派发器不解析它），因此本方法自行切分 token。
     * <p><b>指令里的 {@code #index} 不是 op/args 分隔符</b>：{@code componentId[#index]} 的 {@code #}
     * 是同 id 多份实例的下标（在派发层就已被切掉，用于选中第几份实例；多份且未给下标则直接
     * 拒绝，本方法根本收不到），它与 payload 无关 —— 不存在 {@code op#args} 这种形态。
     *
     * <h2>返回值三态（与 {@link OperationProvider} 契约逐字一致）</h2>
     * <ul>
     *   <li>{@code null} = 未识别或拒绝（未知动词 / 语法错 / 参数不合法）；</li>
     *   <li>{@code ""}（空串）= 已识别但没有回值（纯写操作）—— 本组件从不回空串
     *       （它总有一个可回的值：读类回当前值、写类回写后状态）；</li>
     *   <li>非空串 = 规范化值。</li>
     * </ul>
     * <p><b>组件内部抛出的 {@code RuntimeException} 由派发层吞掉并回 {@code null}</b>
     * （派发层在调用本方法处 {@code try}/{@code catch}，异常不得逃到调用方），因此调用方
     * 无法从 {@code null} 区分「语法错」与「组件崩了」（两者在外部看起来一样）。
     *
     * <h2>可直接照抄的指令</h2>
     * <pre>
     * /role operation @s @s energy current
     * /role operation @s @s energy add 5        ⇒ "5"（写后值）
     * /role operation @s @s energy add -5       ⇒ null（负数 ⇒ 参数不合法）
     * /role operation @s @s energy current 1    ⇒ null（只读动词不得带参数）
     * </pre>
     * 反例说明：第 3 条走参数不合法分支（{@code parseNonNegative} 见负值则回 {@code -1}，据此拒绝）；
     * 第 4 条走只读动词带参分支。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) return null;
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return null;   // 空 / 纯空白 payload 则未识别
        switch (tokens[0]) {
            case "current" -> {
                if (tokens.length != 1) return null;                     // 只读动词不得带参数
                return Integer.toString(current);                        // 回当前值（无副作用）
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
                return Integer.toString(current);                        // 一律回"写后值"（含"不足未扣"则未变值）
            }
            default -> {
                return null;                                             // 未知动词则未识别
            }
        }
    }

    /** 非负整数解析：非数字 / 负数 / 溢出则回 {@code -1}（调用方据此拒绝）。 */
    private static int parseNonNegative(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
