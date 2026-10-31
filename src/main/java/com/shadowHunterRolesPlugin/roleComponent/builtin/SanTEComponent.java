package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * SanTE 组件：系统级能力「SanTE」的组件形态（每角色实例一个）。
 * <p>SanTE 真值 {@code current} 与上限 {@code max} 都持有在本组件里，clamp 与
 * increase/decrease/set 的语义也全在本组件内实现，不转调任何端口。
 * <p><b>与容器的分工</b>：写入之后对平台说两句话 —— ① 把变更交给平台侧通道（容器以本组件
 * 名义登记的那一条监听；它是平台侧通知的唯一入口，只在写入路径里被调到）；② 再由容器在
 * 派发边界向监听器列表派发（含真变化闸门 · 逐监听器隔离 · 重入护栏）。组件只负责真值怎么变。
 * <p><b>容器侧不点名本类</b>：派发边界要逐条通知条目时走的是通用来源面
 * （{@link RoleInstance.ChangeListenerSource}，见 {@link #forEachChangeListener}），因此容器既不
 * 强转、也不接触本类的登记类型；平台侧那一条由本组件在交回时自行排除。
 * <p><b>两条通道各通知一次</b>：① 每次写入（含无变化的写入）各一次；② 只在真变化时一次。
 * 因此同一次真变化对容器侧是"写入路径 1 次 + 派发边界 0 次"（派发边界不再回调平台侧）。
 * <p><b>状态唯一</b>：真值只在本组件里（容器侧不持有该字段），对外只经 {@link #current()} / {@link #set(int)}。
 * <p><b>惩罚不再钉值（2026-10-30 用户裁定）</b>：惩罚组件（{@code DefaultSanTEZeroPunishment}）
 * 不再逐 tick 调 {@code set(阈值)} 钉住 TE —— 惩罚期间被攻击，TE 依然会掉（最低到 {@link #min()}，
 * 默认 -999），惩罚结束时由惩罚组件一次性 {@code set(max)} 拉回上限。本组件只负责 clamp 与派发。
 *
 * <h2>订阅面：JDK {@code Consumer} 监听器列表</h2>
 * 监听器列表 = 一个函数式接口的列表，其他类只需要添加 {@code Consumer} 即可。
 * 本组件持有 {@code List<Listener>}（{@link Listener} = {@code owner} + {@code Consumer<Change>}
 * 的成对登记，record），对外只暴露 {@link #addListener(RoleComponent, Consumer)}；
 * 用 JDK 的 {@code Consumer}，不新增自定义接口（「凡关注点已是组件，不得再为它新增能力接口」同理）。
 * 消费者在自己的 {@code start()} 里 {@code sante.addListener(this, change -> …)}，因此不再有任何类
 * 实现本组件的嵌套接口。
 * <p><b>两条通道</b>（本组件的分工边界）：名单里 owner = 本组件自身的那一条 = 平台侧通道
 * （容器以本组件名义登记：触发派发），写入路径直调它 —— 无变化写入也通知它、异常照常上抛；
 * 其余登记 = 订阅者，由容器在派发边界通知（真变化闸门 · 逐监听器故障隔离 · 重入合并三条都在那里），
 * 写入路径不直接通知订阅者。
 * <p><b>载荷取最小充分类型</b>：旧方法有两个入参（{@code pre} / {@code now}），不能退化成
 * {@code Consumer<Integer>}（两个既有消费者都靠 {@code pre} 与 {@code now} 的比较决定动作），
 * 因此用一个 record {@link Change}（record 不是接口，不违反"不新增接口"）。{@code max} 不进载荷
 * （两个消费者都不用它）。
 * <p><b>移除语义</b>：{@link #removeListener(Listener)} 按引用相等（{@code List.remove(Object)}
 * 的既有语义）—— 调用方须持有同一个 {@link Listener} 实例；{@link #addListener} 返回该实例，
 * 消费者存进私有字段即可。不做"按身份查询"的 {@code unsubscribe(this)}
 * （{@code Consumer} 无身份标识）。
 */
public class SanTEComponent extends RoleComponent implements OperationProvider, RoleInstance.ChangeListenerSource {

    /**
     * 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器装配时只读这个 id 与工厂（{@code Role.ComponentEntry}），不点名组件类。
     */
    public static final String ID = "sante";

    /** **默认** SanTE 上限（每个角色可在装配描述符里覆写，见 {@link Configuration}）。 */
    public static final int SANTE_MAX = 100;

    /** **默认** SanTE 下限：TE 最低可以降到这里（惩罚**不会**再把值钉在阈值上）。 */
    public static final int SANTE_MIN = -999;

    /** **默认** 惩罚触发阈值：降到 0 即进入惩罚（与既有实现逐字一致）。 */
    public static final int SANTE_PUNISHMENT_THRESHOLD = 0;

    /**
     * **每个角色的 SanTE 配置**（装配描述符的载荷 · 2026-10-30 框架改进）。
     *
     * <h2>字段</h2>
     * <ul>
     *   <li>{@code max} —— 上限，默认 {@value #SANTE_MAX}；</li>
     *   <li>{@code min} —— 下限，默认 {@value #SANTE_MIN}；</li>
     *   <li>{@code punishmentThreshold} —— 惩罚触发阈值，默认 {@value #SANTE_PUNISHMENT_THRESHOLD}
     *       （降到 0 进入惩罚）；</li>
     *   <li>{@code punishmentEnabled} —— 是否注册默认惩罚组件，默认 {@code true}。
     *       关闭后角色可自行实现惩罚（或不惩罚）。</li>
     * </ul>
     *
     * <h2>有效下界（2026-10-30 起 = min）</h2>
     * 惩罚**不再把 TE 钉在阈值上**：惩罚期间被攻击，TE 依然会掉（可以掉到负数），
     * 惩罚结束时由惩罚组件一次性拉回上限。因此有效下界就是 {@code min}（默认 <b>-999</b>），
     * {@code punishmentThreshold} 只决定"什么时候开始惩罚"。
     * <p>这条规则写成本 record 的 {@link #effectiveFloor()}（纯函数），组件从它取值。
     *
     * <h2>为什么是 record 而不是散装 setter</h2>
     * 配置是"每个角色一份的不可变值"，record 的 {@code withXxx} 天然给出"改一个字段、留其余默认"的
     * 链式写法；描述符只持有它，运行期读写仍走 {@link #max()} 等读口。
     */
    public record Configuration(int max, int min, int punishmentThreshold, boolean punishmentEnabled) {

        /** 默认配置：100 / -999 / 0 / 惩罚开启 ⇒ 与改动前行为逐字一致。 */
        public static final Configuration DEFAULT =
                new Configuration(SANTE_MAX, SANTE_MIN, SANTE_PUNISHMENT_THRESHOLD, true);

        public Configuration {
            //归一：上限非负；下限不超过上限；阈值夹在 [min, max] 内
            max = Math.max(0, max);
            min = Math.min(min, max);
            punishmentThreshold = Math.clamp(punishmentThreshold, min, max);
        }

        public Configuration withMax(int value) {
            return new Configuration(value, min, punishmentThreshold, punishmentEnabled);
        }

        public Configuration withMin(int value) {
            return new Configuration(max, value, punishmentThreshold, punishmentEnabled);
        }

        public Configuration withPunishmentThreshold(int value) {
            return new Configuration(max, min, value, punishmentEnabled);
        }

        public Configuration withPunishmentEnabled(boolean value) {
            return new Configuration(max, min, punishmentThreshold, value);
        }

        /**
         * **有效下界**（纯函数）：就是 {@code min}（默认 {@value #SANTE_MIN}）。
         * <p>2026-10-30 口径变更：惩罚不再把 TE 钉在阈值上 ⇒ 下界与 {@code punishmentThreshold} 无关。
         * <p>再与 {@code max} 取小，保证"上限被运行期调低（如进化档位还原）"时仍然是合法区间。
         */
        public int effectiveFloor() {
            return Math.min(min, max);
        }
    }

    /** 本组件的日志（逐监听器隔离时报出是谁抛了）。 */
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger("ShadowHunterRoles.sante");

    /**
     * 一次 SanTE 变更的载荷（record，不是接口）。
     *
     * <h2>为什么需要它</h2>
     * 一次变更有两个值（{@code previous} / {@code current}）；本组件的监听器列表用 JDK
     * {@code Consumer}，需要一个载体把这两个值一起交出去。取最小充分类型：只带两个既有消费者
     * 真正用到的值（{@code max} 不进载荷 —— 它不随"变化"而变）。
     *
     * <h2>为什么不退化成 {@code Consumer<Integer>}</h2>
     * 两个既有消费者都靠 {@code previous} 与 {@code current} 的比较决定动作：
     * <ul>
     *   <li>{@code DefaultSanTEZeroPunishment}：{@code now > 0 ⇒ return}（归零才进入惩罚）；</li>
     *   <li>{@code RedDeeplySorrowSkill}：{@code now <= 0 ⇒ 结束技能 + 起冷却}。</li>
     * </ul>
     * 若只给新值，消费者就得自己记住上一次的值，多一份可变状态、多一类时序错误，
     * 因此载荷必须同时带 {@code previous}。
     *
     * <h2>语义</h2>
     * 本记录只承载值、不带任何行为；通知仍无条件发生（"真变化才动作"由消费者自己比较）。
     */
    public record Change(int previous, int current) {
    }

    /**
     * 一条监听登记：{@code owner}（谁订阅）+ {@code listener}（怎么通知）成对持有。
     *
     * <h2>为什么把 owner 一起登记</h2>
     * 容器的派发纪律是「逐个经 {@code guardedCall} 调用」：异常时只隔离抛异常的那一个组件、
     * 其余照常收到。这条纪律需要每一条登记都知道自己属于哪个组件；而 {@code Consumer} 闭包
     * 没有身份，因此由注册方在 {@link #addListener(RoleComponent, Consumer)} 里显式给出。
     * <p>record 不是接口（不违反"不新增自定义接口"）。
     */
    public record Listener(RoleComponent owner, Consumer<Change> listener) {
    }

    /** 监听器名单 —— 顺序 = 添加先后（迭代序稳定，因此"按装配序通知"可复现）。 */
    private final List<Listener> listeners = new ArrayList<>();

    /**
     * 添加监听器（唯一的订阅入口）—— 消费者在自己的 {@code start()} 里调用
     * （时机 = 组件装配序），并在 {@code stop()} 里用 {@link #removeListener(Listener)} 成对移除。
     * <p><b>幂等</b>：同一 {@code owner} + 同一 {@code listener} 重复添加不重复登记。
     * <p><b>{@code owner} = 本组件自身（平台侧登记）</b>：写入路径直调它
     * （见 {@link #set(int)} —— 无变化写入也通知它、异常照常上抛）；
     * 其余 {@code owner} 都是订阅者（由容器在派发边界通知：真变化闸门 + 逐监听器故障隔离）。
     * <p>{@code owner} 或 {@code listener} 为 {@code null} 则忽略。
     * <p><b>返回值</b>：本次登记对应的 {@link Listener} 实例（调用方存起来，将来用
     * {@link #removeListener(Listener)} 按引用移除；被忽略的 {@code null} 入参回 {@code null}）。
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
     * 调用方必须持有同一个 {@code Listener} 实例（把 {@link #addListener} 的返回值存进私有字段即可）。
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
     * 把本轮变更的通知条目逐个交回框架（{@link RoleInstance.ChangeListenerSource} 的实现）。
     * <p><b>分工</b>：派发边界（真变化闸门 · 逐监听器故障隔离 · 重入合并）全部在容器侧，
     * 本方法只回答"这轮该通知哪些条目、每条该做什么"，容器不必知道本组件的登记类型。
     * <p>只排除 {@code owner == 本组件} 的条目（那条由 {@link #notifyListeners(Change)}
     * 在写入路径直调）；生产上该条目并不存在，所以实际效果 = 全部订阅者都交回容器。
     * <p><b>遍历安全性</b>沿用 {@link #forEachListener(Consumer)} 的快照语义（遍历中增删不影响本趟）。
     */
    @Override
    public void forEachChangeListener(int previous, int current, Consumer<RoleInstance.ChangeDelivery> delivery) {
        if (delivery == null) {
            return;
        }
        Change change = new Change(previous, current);
        forEachListener(entry -> {
            if (entry.owner() == this) {
                return;     // owner == 本组件的那条由写入路径直调，不交回容器（避免同一条被通知两次）
            }
            delivery.accept(new RoleInstance.ChangeDelivery(entry.owner(), () -> entry.listener().accept(change)));
        });
    }

    /**
     * 通知全部监听器（一趟直调）—— 本方法就是"派发"这件事的薄实现：
     * {@code forEachListener(entry -> entry.listener().accept(change))}。
     *
     * <h2>谁在什么时机调它</h2>
     * <ul>
     *   <li><b>生产路径</b>：容器（{@code RoleInstance.broadcastSanTEChange}）不用本方法 ——
     *       它走本组件实现的 {@link RoleInstance.ChangeListenerSource 通用来源面}
     *       （{@link #forEachChangeListener}，内部经 {@link #forEachListener(Consumer)} 把条目
     *       逐个交回），由容器逐个套 {@code guardedCall}（那是"逐监听器故障隔离"的唯一实现点）；</li>
     *   <li><b>本方法的存在理由</b>：给"没有容器"的离线单元测试一条与生产同源的派发路径
     *       （否则测试只能自己写循环，验的就成了测试自己的循环）。</li>
     * </ul>
     * <p><b>不是第二条变更通道</b>：本方法只读名单并调监听器，不改真值、不碰平台侧通道。
     */
    public void notifyListeners(Change change) {
        if (change == null) {
            return;
        }
        //无条件通知全部条目 —— 与 EnergyComponent#notifyListeners 逐字同形。
        forEachListener(entry -> {
            //逐监听器故障隔离：某个监听器抛异常时只记日志并继续，其余监听器照常收到。
            //刻意不在循环外层套 try —— 那会让首个异常吞掉排在其后的全部监听器。
            try {
                entry.listener().accept(change);
            } catch (RuntimeException listenerFailure) {
                LOG.warning("[sante] listener failed (owner=" + entry.owner().getId() + "): "
                        + listenerFailure);
            }
        });
    }

    /**
     * 上限（构造期由容器给出）。
     * <p><b>不是 final</b>：角色进化的「特殊值上限提升」档位需要运行期改写它（见 {@link #setMax(int)}）。
     * 这条写口是**经用户批准的一次边界跨越**（框架原本只有构造期上限），因此把改动压到最小：
     * 只放开这一个字段 + 一个受控写口，其余校验与语义一律不变。
     */
    private int max;

    /** 绝对下限（装配期由 {@link Configuration} 给出；惩罚关闭时 TE 可真的降到这里）。 */
    private final int min;

    /** 惩罚触发阈值（默认 0；默认惩罚组件按它触发并把 TE 钉在这个值上）。 */
    private final int punishmentThreshold;

    /** 是否启用默认惩罚（装配期由 {@link Configuration} 给出；只用于诊断与惩罚组件的自检）。 */
    private final boolean punishmentEnabled;

    /** 真值：当前 SanTE（唯一持有处）。 */
    private int current;

    /** 默认配置构造（上限 100 / 下限 -999 / 阈值 0 / 惩罚开启）。 */
    public SanTEComponent(String id, ComponentServicesPort services) {
        this(id, services, Configuration.DEFAULT);
    }

    /** 只覆写上限的兼容构造（下限 / 阈值 / 惩罚开关取默认；既有离线测试用这条）。 */
    public SanTEComponent(String id, ComponentServicesPort services, int max) {
        this(id, services, Configuration.DEFAULT.withMax(max));
    }

    /**
     * **完整配置构造**（每个角色的上限 / 下限 / 惩罚阈值 / 惩罚开关都在这里落地）。
     * <p>装配期由 {@link Specification#create(String, ComponentServicesPort)} 传入；
     * 选角色即满 SanTE（{@code current = max}），与既有构造期语义一致。
     */
    public SanTEComponent(String id, ComponentServicesPort services, Configuration configuration) {
        super(id, services);
        Configuration config = configuration == null ? Configuration.DEFAULT : configuration;
        this.max = config.max();
        this.min = config.min();
        this.punishmentThreshold = config.punishmentThreshold();
        this.punishmentEnabled = config.punishmentEnabled();
        this.current = this.max;
    }

    // ───────── clamp（有效下界 / 上限之间）─────────

    /**
     * 当前生效的下界：就是 {@code min}（默认 -999）；再与上限取小，保证区间合法。
     * <p><b>2026-10-30 起惩罚不再钉值</b>：下界与 {@code punishmentThreshold} 无关，
     * 阈值只决定惩罚何时触发。
     */
    private int clampFloor() {
        return Math.min(min, max);
    }

    /** 把任意写入值夹进 [floor, max]（唯一 clamp 实现点）。 */
    private int clamp(int value) {
        return Math.clamp(value, clampFloor(), max);
    }

    /**
     * 本组件的装配描述符（与技能/被动同规；不带栏位）。
     * <p><b>每个角色可自定义</b>（2026-10-30 框架改进）：上限 / 下限 / 惩罚阈值 / 是否启用默认惩罚。
     * 不写 = {@link Configuration#DEFAULT}（100 / -999 / 0 / 启用）⇒ **与改动前行为逐字一致**。
     * <p>典型用法（角色 builder 里）：
     * <pre>
     * new SanTEComponent.Specification()                 // 默认：100 / -999 / 降到 0 惩罚
     * new SanTEComponent.Specification(Configuration.DEFAULT.withMax(150))
     * new SanTEComponent.Specification().setMin(-500).setPunishmentThreshold(-100)
     * new SanTEComponent.Specification().setPunishmentEnabled(false)   // 不要默认惩罚
     * </pre>
     */
    public static final class Specification extends RoleComponent.Specification<SanTEComponent> {

        private Configuration configuration = Configuration.DEFAULT;

        public Specification() {
            super("SanTE");
        }

        public Specification(Configuration configuration) {
            this();
            if (configuration != null) {
                this.configuration = configuration;
            }
        }

        /** 覆写上限（默认 {@value #SANTE_MAX}）。 */
        public Specification setMax(int max) {
            configuration = configuration.withMax(max);
            return this;
        }

        /** 覆写下限（默认 {@value #SANTE_MIN}；惩罚开启时有效下界见 {@link Configuration#effectiveFloor()}）。 */
        public Specification setMin(int min) {
            configuration = configuration.withMin(min);
            return this;
        }

        /** 覆写惩罚触发阈值（默认 {@value #SANTE_PUNISHMENT_THRESHOLD} = 降到 0 进入惩罚）。 */
        public Specification setPunishmentThreshold(int punishmentThreshold) {
            configuration = configuration.withPunishmentThreshold(punishmentThreshold);
            return this;
        }

        /** 关闭默认惩罚组件（{@code withBuiltIns} 据此不注册 {@code DefaultSanTEZeroPunishment}）。 */
        public Specification setPunishmentEnabled(boolean punishmentEnabled) {
            configuration = configuration.withPunishmentEnabled(punishmentEnabled);
            return this;
        }

        /** 当前配置（装配点 / 离线测试读口）。 */
        public Configuration configuration() {
            return configuration;
        }

        @Override
        public SanTEComponent create(String id, ComponentServicesPort services) {
            return new SanTEComponent(id, services, configuration);
        }
    }

    /** 当前 SanTE（读口）。 */
    public int current() {
        return current;
    }

    /** SanTE 上限（构造期由容器给出；可被 {@link #setMax(int)} 在运行期改写）。 */
    public int max() {
        return max;
    }

    /** SanTE 绝对下限（装配期配置，默认 {@value #SANTE_MIN}）。 */
    public int min() {
        return min;
    }

    /** 惩罚触发阈值（装配期配置，默认 {@value #SANTE_PUNISHMENT_THRESHOLD}）。 */
    public int punishmentThreshold() {
        return punishmentThreshold;
    }

    /** 默认惩罚是否启用（装配期配置；关闭后由角色自行实现惩罚）。 */
    public boolean punishmentEnabled() {
        return punishmentEnabled;
    }

    /**
     * 当前**有效下界**（诊断读口）：= {@code min}（默认 -999），惩罚不再影响它。
     * <p>写出这个读口是为了让"为什么 TE 没有降到 -999"这类问题能被直接回答，而不是靠猜配置。
     */
    public int floor() {
        return clampFloor();
    }

    /**
     * 上限写口：把本实例的 SanTE 上限改为 {@code newMax}（负数按 0 处理），
     * 并把 {@code current} 夹回新上限区间；只有 {@code current} 真的变化时才通知订阅者
     * （上限本身不进 {@link Change} 载荷 —— 它不随"变化"而变，读上限仍走 {@link #max()}）。
     * <p><b>唯一允许的运行期上限入口</b>：使用者 = 角色侧「进化档位」组件
     * （冷识的「吟唱」档把上限 100 → 150，卸载时还原）。不要在别处另造第二条上限通道。
     */
    public void setMax(int newMax) {
        int previous = current;
        this.max = Math.max(0, newMax);
        current = clamp(current);
        if (current != previous) {
            notifyListeners(new Change(previous, current));
        }
    }

    /** 直接写入（组件内按 [floor, max] clamp；写后通知订阅者）。 */
    public void set(int value) {
        int previous = current;
        current = clamp(value);
        //走 notifyListeners(Change) 通知真正的订阅者（owner == 本组件的那条属"平台侧"约定）。
        //订阅者只拿变更通知；"真变化闸门 / 重入合并"由容器在派发边界另做（同一份名单）。
        notifyListeners(new Change(previous, current));
    }


    /** 增加 SanTE（内部按上限 clamp）。 */
    public void increase(int amount) {
        set(current + amount);
    }

    /** 减少 SanTE（内部按**有效下界** clamp；默认配置下 = 0，归零/到阈值惩罚由惩罚组件监听真变化后触发）。 */
    public void decrease(int amount) {
        set(current - amount);
    }

    // ───────── 跨实例：按玩家 UUID 增减别人的 SanTE（与能量组件同形）─────────

    /**
     * 给某个玩家（按 UUID）增加 SanTE，并返回真实增加量。
     *
     * <p>对方可能已接近上限，实际只加到 {@code max} 为止，返回值是真实那个数。
     *
     * @param target 目标玩家 UUID
     * @param amount 想增加的量（{@code <= 0} 不做任何事，返回 0）
     * @return 真实增加量；目标无角色实例 / 取不到本组件 / 无变化则回 0
     */
    public int increaseSanTE(UUID target, int amount) {
        SanTEComponent other = of(target);
        if (other == null || other == this || amount <= 0) {
            return 0;
        }
        int before = other.current();
        other.set(before + amount);
        return other.current() - before;
    }

    /**
     * 给某个玩家（按 UUID）减少 SanTE，并返回真实减少量。
     *
     * <p>对方可能不足，实际只扣到 {@code 0} 为止。要表达"不够就整个不生效"请用
     * {@link #decreaseSanTEAtMost(UUID, int, int)}。
     *
     * @param target 目标玩家 UUID
     * @param amount 想减少的量（{@code <= 0} 不做任何事，返回 0）
     * @return 真实减少量（恒 ≥ 0）；目标无角色实例 / 取不到本组件 / 无变化则回 0
     */
    public int decreaseSanTE(UUID target, int amount) {
        SanTEComponent other = of(target);
        if (other == null || other == this || amount <= 0) {
            return 0;
        }
        int before = other.current();
        other.set(before - amount);
        return before - other.current();
    }

    /**
     * 门槛式减少 SanTE：只有当对方当前 SanTE ≥ {@code minimumRequired} 时才扣，
     * 否则一点都不扣（返回 0）。
     *
     * @param minimumRequired 生效门槛（对方 SanTE < 它则返回 0；{@code <= 0} 则门槛不设）
     * @return 真实减少量（未过门槛则回 0）
     */
    public int decreaseSanTEAtMost(UUID target, int amount, int minimumRequired) {
        SanTEComponent other = of(target);
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
     * 解析某个玩家实例上的 SanTE 组件（跨实例；未命中一律 {@code null}）。
     *
     * <p>与能量组件的同形实现：经插件单例拿目标 {@code RoleInstance}，
     * 再按本组件自己的登记 id（本类的 {@code ID}）取同类实例，
     * 不新增端口、不持有 {@code RoleManager}、不写第二份 id 字面量。
     */
    private static SanTEComponent of(UUID target) {
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
        return component instanceof SanTEComponent sante ? sante : null;
    }

    /**
     * 检查 + 扣减合一：SanTE 不足则回 {@code false}，且不扣、不产生变更。
     *
     * <p>与 {@code EnergyComponent#tryConsume(int)} 的阈值语义逐字同形：
     * {@code amount <= 0} ⇒ {@code true}（不扣）；{@code current < amount} ⇒ {@code false}；
     * 否则 {@link #set(int)} 扣减并通知订阅者。
     */
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

    //消费者直接持有强类型 SanTEComponent，调 current() / set(int)。
    /**
     * 组件操作面：把外部字符串指令薄适配到本组件既有强类型方法（零新增状态通道）。
     * <p><b>grammar（首 token 必为动词，大小写敏感；参数以单个空格分隔）</b>：
     * <ul>
     *   <li>{@code current} —— 读：回当前 SanTE（无参，越界参数则未识别）；</li>
     *   <li>{@code max} —— 读：回上限（无参）；</li>
     *   <li>{@code min} —— 读：回**绝对下限**（装配期配置，默认 -999；无参）；</li>
     *   <li>{@code threshold} —— 读：回**惩罚触发阈值**（默认 0；无参）；</li>
     *   <li>{@code floor} —— 读：回**当前有效下界**（惩罚开启时 = max(min, 阈值)，默认 0；无参）；</li>
     *   <li>{@code set &lt;int&gt;} —— 写：调既有的 {@link #set(int)}（内部 clamp +
     *       平台侧通知照常），回写后值；<b>允许负数</b>（会被夹到有效下界）；</li>
     *   <li>{@code gain &lt;int≥0&gt;} —— 写：调既有的 {@link #increase(int)}，回写后值；</li>
     *   <li>{@code decrease &lt;int≥0&gt;} —— 写：调既有的 {@link #decrease(int)}，回写后值
     *       （不足则按既有 clamp 语义）。</li>
     * </ul>
     * <p><b>薄适配纪律</b>：本方法只调上述既有方法，不新增平行的状态改动路径、
     * 不绕过既有 clamp / 通知 / 置脏。
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
     * /role operation @s @s sante current
     * /role operation @s @s sante max
     * /role operation @s @s sante min
     * /role operation @s @s sante threshold
     * /role operation @s @s sante floor
     * /role operation @s @s sante gain 20       ⇒ 写后值
     * /role operation @s @s sante set -50       ⇒ 写后值（默认配置下夹到 0）
     * /role operation @s @s sante gain -1       ⇒ null（负数 ⇒ 参数不合法）
     * /role operation @s @s sante max 1         ⇒ null（只读动词不得带参数）
     * </pre>
     * 反例说明：第 8 条走参数不合法分支（增减方向负数则拒绝）；第 9 条走只读动词带参分支。
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
        if (verb.equals("min")) return parts.length == 1 ? Integer.toString(min()) : null;
        if (verb.equals("threshold")) return parts.length == 1 ? Integer.toString(punishmentThreshold()) : null;
        if (verb.equals("floor")) return parts.length == 1 ? Integer.toString(floor()) : null;
        if (!verb.equals("set") && !verb.equals("gain") && !verb.equals("decrease")) return null;
        if (parts.length != 2) return null;
        int amount;
        try {
            amount = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            return null;
        }
        //set 允许负数（角色下限可能为负）；gain / decrease 的方向参数仍必须非负
        if (!verb.equals("set") && amount < 0) return null;
        switch (verb) {
            case "set" -> set(amount);
            case "gain" -> increase(amount);
            default -> decrease(amount);
        }
        return Integer.toString(current());
    }
}
