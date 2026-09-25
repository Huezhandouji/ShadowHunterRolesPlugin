package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * SanTE 组件：系统级能力「SanTE」的**组件形态**（每角色实例一个）。
 * <p><b>★ 本组件持有状态与行为</b>：SanTE 真值 {@code current} 与上限 {@code max} 都在本组件里，
 * clamp 与 increase/decrease/set 的语义全在本组件内实现 —— **不再转调任何旧端口** ✗。
 * <p><b>与容器的分工</b>：写后对平台说两句话 —— ① 把变更交给**平台侧通道**（容器**以本组件名义**登记的那一条
 * 监听；它是**平台侧通知的唯一入口**，只在写入路径里被调到），② 再由容器在**派发边界**向
 * 监听器列表派发（含真变化闸门 · 逐监听器隔离 · 重入护栏）；组件只负责真值怎么变。
 * <p><b>容器侧不点名本类</b>：派发边界要"逐条通知条目"时走的是**通用来源面**
 * （{@link RoleInstance.ChangeListenerSource}，见 {@link #forEachChangeListener}）⇒ 容器既不强转、
 * 也不接触本类的登记类型 ✓；平台侧那一条由本组件在交回时自行排除 ✓。
 * <p><b>两条通道各通知一次</b>：① 每次写入（含无变化的写入）各一次；② 只在**真变化**时一次
 * ⇒ 同一次真变化对容器侧是"写入路径 1 次 + 派发边界 0 次"（派发边界**不**再回调平台侧）。
 * <p><b>状态唯一</b>：容器侧**不再**持有 {@code currentSanTE} 字段 ✗（只保留视图方法）。
 * <p><b>归零惩罚的钉 0 语义不变</b>：惩罚组件（{@code DefaultSanTEZeroPunishment}）仍按既有方式
 * 调**组件自身**的 {@code set(0)} 逐 tick 钉 0（协作组件引用在 `start()` 内一次取好、存进字段 ✓）⇒ 走的还是这一条 clamp + 派发路径。
 *
 * <h2>订阅面：<b>JDK {@code Consumer} 监听器列表</b></h2>
 * <b>旧形态</b>：本组件曾嵌套一个 {@code public interface Subscriber}（唯一方法
 * {@code onSanTEChange(int pre, int now)}，带默认空实现）⇒ 消费者必须在**类声明上** {@code implements
 * SanTEComponent.Subscriber} ✗。**监听器列表**的形态是：「一个函数式接口的列表，其他类只需要添加
 * {@code Consumer} 即可」✓。
 * <p><b>新形态</b>：本组件持有 {@code List<Listener>}（{@link Listener} = {@code owner} + {@code Consumer<Change>}
 * 的**成对**登记，record ✓），对外只暴露 {@link #addListener(RoleComponent, Consumer)} ✓；
 * ★ **用 JDK 的 {@code Consumer}**（**不新增自定义接口** ✗ —— 「凡关注点已是组件 ⇒ 不得再为它新增能力接口」
 * 同理）✓。消费者在自己的 {@code start()} 里 {@code sante.addListener(this, change -> …)} ⇒ **不再有任何类
 * 实现本组件的嵌套接口** ✓。
 * <p><b>两条通道</b>（本组件的分工边界 ✓）：名单里 **owner = 本组件自身**的那一条 = **平台侧通道**
 * （容器以本组件名义登记：触发派发）⇒ 写入路径**直调**它 —— 无变化写入也通知它 ✓、
 * 异常照常上抛 ✓；**其余登记 = 订阅者** ⇒ 由容器在**派发边界**通知（真变化闸门 ·
 * 逐监听器故障隔离 · 重入合并三条都在那里 ✓）⇒ 写入路径**不**直接通知订阅者 ✗。
 * <p><b>载荷取「最小充分类型」</b>：旧方法有两个入参（{@code pre} / {@code now}）⇒ 不能退化成
 * {@code Consumer<Integer>} ✗（两个既有消费者都靠 {@code pre} 与 {@code now} 的比较决定动作）⇒ 用一个**小 record**
 * {@link Change}（**record 不是接口** ⇒ 不违反"不新增接口"✓）。{@code max} **不进载荷** ✗（两个消费者都不用它）。
 * <p><b>移除语义</b>：{@link #removeListener(Listener)} 按**引用相等**（{@code List.remove(Object)} 的既有语义）
 * ✓ —— 调用方须持有**同一个** {@link Listener} 实例（{@link #addListener(RoleComponent, Consumer)} **返回**该实例
 * ⇒ 消费者存进私有字段即可 ✓）。不做"按身份查询"的 {@code unsubscribe(this)} ✗（{@code Consumer} 无身份标识）。
 */
public class SanTEComponent extends RoleComponent implements OperationProvider, RoleInstance.ChangeListenerSource {

    /**
     * **本组件的登记 id**（★ 知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器装配时只读这个 **id + 工厂**（{@code data}），不点名组件类 ✓。
     */
    public static final String ID = "sante";
    /** **本组件的 SanTE 上限**（★ 组件侧配置 · 占位值；原住在框架级清单里，现归组件自己）。 */
    public static final int SANTE_MAX = 100;

    /** 本组件的日志（逐监听器隔离时报出**是谁**抛了）。 */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("ShadowHunterRoles.sante");

    /**
     * **一次 SanTE 变更的载荷**（**record，不是接口** ✓）。
     *
     * <h2>为什么需要它</h2>
     * 一次变更有两个值（{@code previous} / {@code current}）；本组件的监听器列表用 JDK
     * {@code Consumer} ⇒ 需要一个**载体**把这两个值一起交出去 ✓。取**最小充分类型**：
     * 只带两个既有消费者真正用到的值 ✓（{@code max} 不进载荷 ✗ —— 它不随"变化"而变）。
     *
     * <h2>为什么不退化成 {@code Consumer<Integer>}</h2>
     * 两个既有消费者都靠 {@code previous} 与 {@code current} 的**比较**决定动作：
     * <ul>
     *   <li>{@code DefaultSanTEZeroPunishment}：{@code now > 0 ⇒ return}（归零才进入惩罚）；</li>
     *   <li>{@code RedDeeplySorrowSkill}：{@code now <= 0 ⇒ 结束技能 + 起冷却}。</li>
     * </ul>
     * 若只给新值，消费者就得**自己记住上一次的值** ⇒ 多一份可变状态、多一类时序错误 ✗
     * ⇒ 载荷必须同时带 {@code previous} ✓。
     *
     * <h2>语义与旧形态逐字相同</h2>
     * 本记录**只承载值**、不带任何行为 ✓；通知仍**无条件**发生（"真变化才动作"由消费者自己比较 ✓）。
     */
    public record Change(int previous, int current) {
    }

    /**
     * **一条监听登记**：{@code owner}（谁订阅）+ {@code listener}（怎么通知）**成对**持有 ✓。
     *
     * <h2>为什么把 owner 一起登记</h2>
     * 容器的派发纪律是「**逐个**经 {@code guardedCall} 调用」⇒ 异常时只隔离**抛异常的那一个**组件、
     * 其余照常收到 ✓。这条纪律需要**每一条登记都知道自己属于哪个组件** ✓ —— 旧 {@code Subscriber} 形态靠
     * {@code subscriber instanceof RoleComponent} 反推（接口实现者恰好就是组件 ✓）；改用 {@code Consumer} 后
     * 闭包**没有身份** ✗ ⇒ 由注册方在 {@link #addListener(RoleComponent, Consumer)} 里**显式给出** ✓。
     * <p>record 不是接口 ✓（不违反"不新增自定义接口"）。
     */
    public record Listener(RoleComponent owner, Consumer<Change> listener) {
    }

    /** **监听器名单** —— 顺序 = 添加先后 ✓（迭代序稳定 ⇒ "按装配序通知"可复现 ✓）。 */
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * **添加监听器**（**唯一**的订阅入口 ✓）—— 消费者在自己的 {@code start()} 里调用
     * （⇒ 时机 = 组件装配序 ✓），并在 {@code stop()} 里用 {@link #removeListener(Listener)} 成对移除 ✓。
     * <p><b>幂等</b>：同一 {@code owner} + 同一 {@code listener} 重复添加**不重复登记** ✓
     * （与旧 {@code subscribe} 的幂等语义逐字一致）。
     * <p><b>{@code owner} = 本组件自身 ⇒ 平台侧登记</b> ✓：写入路径**直调**它
     * （见 {@link #set(int)} —— 无变化写入也通知它 ✓、异常照常上抛 ✓）；
     * 其余 {@code owner} ⇒ **订阅者** ✓（由容器在**派发边界**通知：真变化闸门 + 逐监听器故障隔离 ✓）。
     * <p>{@code owner} 或 {@code listener} 为 {@code null} ⇒ **忽略**（旧 {@code subscribe(null)} 同样是 no-op ✓）。
     * <p><b>返回值</b>：本次登记对应的 {@link Listener} 实例（**调用方存起来** ⇒ 将来用
     * {@link #removeListener(Listener)} 按引用移除 ✓；被忽略的 {@code null} 入参 ⇒ 回 {@code null} ✓）。
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
     * 调用方必须持有**同一个** {@code Listener} 实例（把 {@link #addListener} 的返回值存进私有字段即可 ✓）。
     * <p>按引用移除与"按身份退订"的语义**逐字等价**（同样按
     * {@code List.remove(Object)} ⇒ 不在名单里是 no-op ✓）。
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
     * **排在它后面的监听器就收不到通知** ✗。容器侧用的是"**逐个**经 {@code guardedCall} 调用"
     * ⇒ 只隔离抛异常的那一个、其余照常收到 ✓ —— 这条性质**必须保住** ✗。
     * <p>所以这里**不**替调用方做派发决策，只提供**遍历**：由容器决定"怎么调、怎么护" ✓。
     * <p><b>遍历期间增删的安全</b>：本方法对名单取**快照**后再遍历
     * （{@code List.copyOf}）⇒ 遍历途中 {@link #addListener(RoleComponent, Consumer)} /
     * {@link #removeListener(Listener)} 不会触发 {@code ConcurrentModificationException}，
     * 也**不影响本次遍历**（快照语义 = 本次通知的接受集在进入时已定 ✓）。
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
     * **把本轮变更的通知条目逐个交回框架**（{@link RoleInstance.ChangeListenerSource} 的实现）。
     * <p><b>分工</b>：派发边界（真变化闸门 · 逐监听器故障隔离 · 重入合并）全部在容器侧 ✓，
     * 本方法只回答"这轮该通知哪些条目、每条该做什么" ⇒ 容器**不必知道**本组件的登记类型 ✓。
     * <p><b>平台侧那一条不在此列</b>：它的 {@code owner} 就是本组件自身，写入路径已经直调过它
     * ⇒ 这里必须排除（否则平台侧会多收一次）。
     * <p><b>遍历安全性</b>沿用 {@link #forEachListener(Consumer)} 的快照语义 ✓（遍历中增删不影响本趟）。
     */
    @Override
    public void forEachChangeListener(int previous, int current, Consumer<RoleInstance.ChangeDelivery> delivery) {
        if (delivery == null) {
            return;
        }
        Change change = new Change(previous, current);
        forEachListener(entry -> {
            if (entry.owner() == this) {
                return;                             //平台侧那一条：写入路径直调 ⇒ 不属于订阅者
            }
            delivery.accept(new RoleInstance.ChangeDelivery(entry.owner(), () -> entry.listener().accept(change)));
        });
    }

    /**
     * **通知全部监听器**（一趟直调）—— 本方法就是"派发"这件事的**薄实现**：
     * {@code forEachListener(entry -> entry.listener().accept(change))} ✓。
     *
     * <h2>谁在什么时机调它</h2>
     * <ul>
     *   <li><b>生产路径</b>：容器（{@code RoleInstance.broadcastSanTEChange}）**不**用本方法 ✗ ——
     *       它走本组件实现的 {@link RoleInstance.ChangeListenerSource 通用来源面}（{@link #forEachChangeListener}，
     *       内部经 {@link #forEachListener(Consumer)} 把条目逐个交回），由容器**逐个**套 {@code guardedCall} ✓
     *       （那是"逐监听器故障隔离"的唯一实现点 ✓）；</li>
     *   <li><b>本方法的存在理由</b>：给"没有容器"的**离线单元测试**一条与生产同源的派发路径 ✓
     *       （否则测试只能自己写循环，验的就成了测试自己的循环 ✗）。</li>
     * </ul>
     * <p><b>不是第二条变更通道</b> ✗：本方法**只**读名单并调监听器，不改真值、不碰平台侧通道 ✓。
     */
    public void notifyListeners(Change change) {
        if (change == null) {
            return;
        }
 //★ **逐监听器故障隔离由本组件自持**（原在容器侧 `guardedCall` 里）——
 // 异常 ⇒ **只记日志并继续**，其余监听器照常收到 ✓。
 // ★ 刻意**不**在循环外层套 try：那会让首个异常吞掉排在其后的全部监听器。
        forEachListener(entry -> {
            if (entry.owner() == this) {
                return;                             //平台侧那一条：写入路径直调 ⇒ 不属于订阅者
            }
            try {
                entry.listener().accept(change);
            } catch (RuntimeException listenerFailure) {
                LOG.warning("[sante] listener failed (owner=" + entry.owner().getId() + "): "
                        + listenerFailure);
            }
        });
    }

    private final int max;

    /** ★ 真值：当前 SanTE（唯一持有处）。 */
    private int current;

    public SanTEComponent(String id, ComponentServices services, int max) {
        super(id, services);
        this.max = Math.max(0, max);
        //与既有 RoleInstance 构造期逐字一致：选角色即满 SanTE
        this.current = this.max;
    }

    /**
     * **本组件的装配描述符**（与技能/被动同规；不带栏位）。
     * <p>上限取组件自己的常量 ⇒ 描述符无额外依赖 ✓。
     */
    public static final class Specification extends RoleComponent.Specification<SanTEComponent> {

        public Specification() {
            super("SanTE");
        }

        @Override
        public SanTEComponent create(String id, ComponentServices services) {
            return new SanTEComponent(id, services, SANTE_MAX);
        }
    }

    /** 当前 SanTE（读口）。 */
    public int current() {
        return current;
    }

    /** SanTE 上限（构造期由容器给出）。 */
    public int max() {
        return max;
    }

    /** 直接写入（组件内 clamp；写后通知平台侧通道）。 */
    public void set(int value) {
        int previous = current;
        current = Math.clamp(value, 0, max);
        notifyPlatform(new Change(previous, current));
    }

    /**
     * **只通知平台侧通道**（**平台侧通知的唯一入口** ✓）。
     * <p><b>为什么不走 {@link #notifyListeners(Change)}</b>：订阅者必须在容器的**派发边界**被通知
     * —— 真变化闸门（无变化写入不派发）、逐监听器故障隔离、重入合并三条都在那里 ✓；
     * 写入路径直接通知订阅者会绕过这三条 ✗。
     * <p>本方法**不做**任何隔离：平台侧回调抛异常 ⇒ **照常上抛**（与写入路径的既有语义一致 ✓）。
     * <p><b>调用时机</b>：只在写入路径（{@link #set(int)}）里，**且不筛真变化** ——
     * "有没有真变化"由容器在派发边界过闸门（`pre == now` 直接返回）✓ ⇒
     * 「变更何时到达平台侧」的语义逐字不变。
     */
    private void notifyPlatform(Change change) {
        forEachListener(entry -> {
            if (entry.owner() == this) {
                entry.listener().accept(change);
            }
        });
    }

    /** 增加 SanTE（内部按上限 clamp）。 */
    public void increase(int amount) {
        set(current + amount);
    }

    /** 减少 SanTE（内部按 0 下限 clamp；归零惩罚由既有组件监听真变化后触发）。 */
    public void decrease(int amount) {
        set(current - amount);
    }
    /**
     * **组件操作面**：把外部字符串指令**薄适配**到本组件既有强类型方法（零新增状态通道 ✓）。
     * <p><b>grammar（首 token 必为动词，大小写敏感；参数以单个空格分隔）</b>：
     * <ul>
     *   <li>{@code current} —— 读：回**当前 SanTE**（无参 ✓，越界参数 ⇒ 未识别）；</li>
     *   <li>{@code max} —— 读：回**上限**（无参 ✓）；</li>
     *   <li>{@code set &lt;int≥0&gt;} —— 写：调既有的 {@link #set(int)}（内部 clamp + 平台侧通知照常 ✓），回**写后值**；</li>
     *   <li>{@code gain &lt;int≥0&gt;} —— 写：调既有的 {@link #increase(int)} ✓，回**写后值**；</li>
     *   <li>{@code decrease &lt;int≥0&gt;} —— 写：调既有的 {@link #decrease(int)} ✓，回**写后值**（不足则按既有 clamp 语义 ✓）。</li>
     * </ul>
     * <p><b>三态返回</b>：{@code null} = **未识别 / 拒绝执行**（未知动词 ✓ · 参数缺失/多余 ✓ · 非数字/负数/溢出 ✓ · 空或空白 payload ✓）；
     * 非空串 = **规范化值**（写类回写后值、读类回当前值 ✓）。
     * <p><b>薄适配纪律</b>：本方法**只调**上述既有方法 ⇒ 不新增平行的状态改动路径 ✗、不绕过既有 clamp / 通知 / 置脏 ✓。
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
     * /role operation query  @s sante current
     * /role operation query  @s sante max
     * /role operation modify @s sante gain 20       ⇒ 写后值
     * /role operation modify @s sante gain -1       ⇒ null（负数 ⇒ 参数不合法）
     * /role operation query  @s sante max 1         ⇒ null（只读动词不得带参数）
     * </pre>
     * ★ 反例说明：第 4 条走**参数不合法**分支（负数 ⇒ 拒绝）；第 5 条走**只读动词带参**分支。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) return null;
        String text = payload.trim();
        if (text.isEmpty()) return null;
        String[] parts = text.split(" ");
        String verb = parts[0];
        if (verb.isEmpty()) return null;
        if (verb.equals("current")) return parts.length == 1 ? Integer.toString(current()) : null;
        if (verb.equals("max")) return parts.length == 1 ? Integer.toString(max()) : null;
        if (!verb.equals("set") && !verb.equals("gain") && !verb.equals("decrease")) return null;
        if (parts.length != 2) return null;
        int amount;
        try {
            amount = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (amount < 0) return null;
        switch (verb) {
            case "set" -> set(amount);
            case "gain" -> increase(amount);
            default -> decrease(amount);
        }
        return Integer.toString(current());
    }
}
