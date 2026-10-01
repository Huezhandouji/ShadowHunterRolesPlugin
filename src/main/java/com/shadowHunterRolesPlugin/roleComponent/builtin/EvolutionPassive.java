package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * **进化**被动基类（继承 {@link PassiveSkill}）：把「击杀」折算成一档档的进化等级，
 * 并在每次升级时把这件事通知给订阅方（"几级给什么增益"归订阅方自己实现）。
 *
 * <h2>本类做什么 / 不做什么</h2>
 * <ul>
 *   <li><b>做</b>三件事：① 持有**每实例一份**的进化等级（{@link #getCurrentEvolutionLevel()}）；
 *       ② 在 {@link #start()} 里把自己挂到本实例的 {@link VitalsComponent} 击杀订阅面上
 *       （{@link VitalsComponent#addPlayerKilledListener}），每次击杀使等级 +1；
 *       ③ 提供升级订阅面（{@link #addEvolutionLevelUpListener} /
 *       {@link #removeEvolutionLevelUpListener}）与两个写口
 *       {@link #increaseEvolutionLevel()} / {@link #decreaseEvolutionLevel()}。</li>
 *   <li><b>不做</b>：本类**不认识任何增益** —— 不碰生命 / 能量 / SanTE / Buff / 物品，也不决定
 *       "几级封顶"。因此同一个基类可以同时服务"每级加攻速"与"每 3 级回一次血"两种角色，
 *       而两种角色的差异全部落在它们各自的{@link EvolutionPassive}子类里（订阅升级、自己动手）。
 *       这条边界与「能力各归其家」同源：进化只持有"等级"这一件事。</li>
 * </ul>
 *
 * <h2>为什么挂 {@link VitalsComponent}，而不是自己听 {@code PlayerDeathEvent}</h2>
 * 「谁杀的」这条链的**终点与唯一口径**都在生命组件：击杀者由
 * {@code DamageUtil.getLastDamager} 定出（不是原版 {@code getKiller()} —— 本系统一部分伤害绕过
 * 原版事件），且投递**只发到击杀者那一侧**的实例（见 {@code listener/hook/PlayerKilledHookListener}）。
 * 因此在 {@code start()} 里订阅本实例自己的击杀名单，"杀人就进化"就已经完整成立：
 * 自杀 / 无击杀者 / 击杀者无角色 / 非玩家击杀这些边界全由那条链处理，本类一条都不必重写。
 *
 * <h2>等级的口径</h2>
 * <ul>
 *   <li>等级是**每实例**状态（每个玩家、每个角色实例各一份），初值 {@code 0}；</li>
 *   <li>写口**恰好两个**：{@link #increaseEvolutionLevel()}（+1）与 {@link #decreaseEvolutionLevel()}（−1）
 *       —— 击杀路径与任何外部调试路径都只走这两个，没有第三条各写一遍的通道；</li>
 *   <li>**下限 {@code 0}**（不会到负数）：减到 0 就停住，与 SanTE / 能量 / 「创伤」层数
 *       各处的 clamp 口径同源 —— 等级是"计数"，负的计数是另一个概念（若日后真要"负进化"，
 *       那是新产品口径，须另行裁定，不在本方法里顺手放开）；</li>
 *   <li><b>只有升级会发通知</b>（见下节）：{@link #increaseEvolutionLevel()} 必发
 *       {@link LevelUp}，{@link #decreaseEvolutionLevel()} **不发** ——
 *       因为订阅面的名字（以及你为它定的 {@code addEvolutionLevelUpListener}）就是"**升**级"；
 *       让它在降级时也响，订阅方收到的就成了"一条下行的升级通知"（类型在说谎）。</li>
 *   <li>等级**不落盘、不跨角色存活**：角色实例销毁即消失（本类不持有任何外部状态）。</li>
 * </ul>
 *
 * <h2>降级为什么不发通知（以及订阅方该怎么办）</h2>
 * 降级是**调用方自己发起**的动作 —— 它刚刚调了 {@link #decreaseEvolutionLevel()}，返回值就是新等级，
 * 不存在"我改了但没人知道"的问题；真正在"跟随等级"的订阅方（例如"每级 +2 生命上限"）本来就不该
 * 把增益做成不可回退的累加，而应在自己的节拍里读 {@link #getCurrentEvolutionLevel()} 重算，
 * 或由发起降级的那一方显式通知它。
 * <p>如果某个角色确实需要一条**对称的"等级变化"通道**（升降都响），那是新的一件事
 * （新名字、新载荷语义），需要时再加 —— 不在这里顺手复用"升级"那条。
 *
 * <h2>生命周期（与框架的既有次序）</h2>
 * {@link #start()} 取协作组件并挂订阅（依赖只在 {@code start()} 取，这是本工程的硬约定）；
 * {@link #stop()} 与之严格对称：撤销那条击杀订阅、丢掉升级监听名单。
 * 等级**不因销毁而回退** —— 它是"已经发生过的事实"，实例销毁时自然一并消失
 * （与"显式调 {@link #decreaseEvolutionLevel()} 降一级"是两件事）。
 *
 * <h2>为什么不占热键栏</h2>
 * {@link PassiveSkill} 家族没有栏位语言（其描述符没有 {@code setSlot}），因此"被动不占热键栏"
 * 是编译期事实，而不是装配点的自觉。
 */
public abstract class EvolutionPassive extends PassiveSkill {

    /**
     * 一次**升级**的载荷：只给前后两个等级（取最小充分类型）。
     *
     * <p>为什么带上 {@code previous}：与 {@code SanTEComponent.Change} 同源的理由 ——
     * 「每 3 级一次」「只在偶数级」这类规则靠当前值取模就能判，但"这一刻跨过了哪一档"
     * 只有前后两个值同时在手上才判得干净；只给新值会逼订阅方自己再存一份"上次的等级"，
     * 多一份可变状态、多一类时序错误。
     *
     * <p>本记录只承载值、不带任何行为（record 不是接口，不违反"不新增自定义接口"）。
     */
    public record LevelUp(int previous, int current) {
    }

    /**
     * 一条升级监听登记：{@code owner}（谁订阅）+ {@code listener}（怎么通知）成对持有
     * （形态与 {@code EnergyComponent.Listener} / {@code SanTEComponent.Listener} /
     * {@link VitalsComponent.PlayerKilledListener} 一致）。
     *
     * <p>为什么把 {@code owner} 一起登记：通知纪律是「逐条故障隔离」——
     * 某一条抛异常时只记日志并继续，其余照常收到。做到这件事的前提是每一条登记都知道自己属于谁，
     * 而 {@code Consumer} 闭包没有身份 ⇒ 由注册方在
     * {@link #addEvolutionLevelUpListener(RoleComponent, Consumer)} 里显式给出。
     *
     * <p>与前面几个监视面的一处**不同**（如实申报）：本名单的派发点就在本组件内部
     * （见 {@link #notifyEvolutionLevelUpListeners(LevelUp)}），不经容器的
     * {@code RoleInstance#deliverHook}；因此"逐个故障隔离"由本组件自己实现，
     * 而 {@code owner} 在这里的用途 = 诊断归因（日志点名是谁抛的）。
     */
    public record EvolutionLevelUpListener(RoleComponent owner, Consumer<LevelUp> listener) {
    }

    /** 升级监听名单 —— 顺序 = 登记先后（迭代序稳定，因此"按登记序通知"可复现）。 */
    private final List<EvolutionLevelUpListener> levelUpListeners = new ArrayList<>();

    /** 逐条故障隔离的日志（与生命组件同规：报出是谁抛了）。 */
    private static final Logger LOG = Logger.getLogger("ShadowHunterRoles.evolution");

    /** 本实例的进化等级（初值 {@code 0}；下限 {@code 0} —— 两个写口都不越界）。 */
    private int evolutionLevel;

    /**
     * 本实例的生命组件（{@link #start()} 里一次取好缓存进字段）。
     * <p>{@code null} = 该角色没装生命组件（当前装配表里不会发生；缺了也只是不进化，不抛）。
     */
    private VitalsComponent vitals;

    /**
     * 本组件挂在生命组件上的那条击杀登记 —— {@link #stop()} 按引用把它撤下来。
     * <p>不持有它就无法撤销（{@code Consumer} 闭包没有身份），因此与 {@code start()} 成对维护。
     */
    private VitalsComponent.PlayerKilledListener killedEntry;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    protected EvolutionPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 生命周期：挂上 / 撤下击杀订阅 ─────────

    /**
     * 开始生效：先做本族必须做的那一步（把击杀订阅挂到本实例的生命组件上），再调子类自己的钩子
     * {@link #onStart()}。
     *
     * <p><b>本方法是 {@code final}（子类只能覆写 {@link #onStart()}）</b> ——
     * 理由与 {@code ActiveComponent#awake()} 那条同一口径：
     * 本族"杀人就 +1"这条能力**全部**依赖这里的那一行订阅；若子类覆写 {@code start()} 而忘了
     * {@code super.start()}，结果不是报错，而是**静默失效**（等级永远停在 0，日志里一句都没有）。
     * 实测事故先例：{@code CangluBlueIceRevolverSkill} 曾覆写 {@code awake()} 且未调 {@code super}，
     * 栏位登记没执行 ⇒ 热键栏里没有该物品、且无任何报错。{@code final} 把"漏调"从**静默失效**
     * 变成**编译错误**（本仓口径：漏写要成为编译错误，不是运行期惊喜）。
     *
     * <p>依赖只在 {@code start()} 取（不在 {@code awake()}：那时装配序未定）；取到 {@code null}
     * （角色没装生命组件）时就不订阅、也不抛 —— 与被动家族"可缺失即容忍"的既有口径一致
     * （代价如实申报：那种角色永远停在 0 级，且不会有任何报错）。
     */
    @Override
    public final void start() {
        vitals = svc().components().get(VitalsComponent.class);
        if (vitals != null) {
            killedEntry = vitals.addPlayerKilledListener(this, event -> onPlayerKilled(event));
        }
        onStart();
    }

    /**
     * 子类自己的"开始生效"钩子（默认空）—— 由 {@link #start()} 在挂完击杀订阅之后调用。
     *
     * <p>契约：与本族的 {@code start()} 同一阶段，因此**可以**做玩家可见的副作用（发装备、施加属性、
     * 显示 BossBar 都行）—— 与 {@code ActiveComponent#onAwake()} 相反（那个在原 {@code awake()} 阶段，
     * 那时其它组件还没 {@code start()}，因此只准解析依赖、不准改可见状态）。
     * 强类型依赖的解析就写在这里（本工程的硬约定 R-4：组件依赖只在 {@code start()} 阶段取，
     * 不在 {@code awake()} / 构造器里取）：
     * <pre>{@code
     * public class MyEvolution extends EvolutionPassive {
     *     private BuffComponent buff;
     *     private EvolutionLevelUpListener levelUpEntry;   // 供按引用撤销
     *
     *     @Override
     *     protected void onStart() {
     *         buff = svc().components().get(BuffComponent.class);
     *         //本组件自己也可以是订阅方：owner = this（登记实例存进字段，便于按引用撤销）
     *         levelUpEntry = addEvolutionLevelUpListener(this, levelUp -> buff.applyPotionEffect(...));
     *     }
     * }
     * }</pre>
     * <p>顺序保证：本钩子在击杀订阅挂好之后才跑，因此"登记完订阅"与"能收击杀"两件事之间没有窗口。
     */
    protected void onStart() {
    }

    /**
     * 停止生效：撤销击杀订阅、丢掉升级监听名单、清掉协作组件引用（与 {@link #start()} 严格对称）。
     *
     * <p><b>覆写者必须调 {@code super.stop()}</b>（本方法**不是** {@code final}：清自己的状态是子类的正当事务，
     * 而它多为"置字段为 null / 取消自己的任务"，与基类那三件事不重叠）。
     * 与 {@link #start()} 相反，这里漏调 {@code super} 的后果是**无害的**：
     * 生命组件与本组件同属一个角色实例、同一时刻一起销毁，那条没撤下来的登记随之消失。
     * 不把它设成 {@code final} 只是避免为一件无风险的事多开一个钩子（与 {@code ActiveComponent} 的
     * "只冻结 {@code awake()}、不冻结 {@code start()/stop()}"同一取舍）。
     *
     * <p>为什么要显式撤销：订阅名单住在**另一个**组件（生命组件）里，它不在本组件的回收范围内；
     * 不撤就留下一条指向本实例的悬挂登记。
     * <p>等级**不清零**（已经发生过的事实）：实例被销毁后它自然消失，不需要也不应该"回退"。
     */
    @Override
    public void stop() {
        if (vitals != null && killedEntry != null) {
            vitals.removePlayerKilledListener(killedEntry);
        }
        vitals = null;
        killedEntry = null;
        //订阅方组件已随本实例一起销毁，把它们留下的登记一并丢掉（避免悬挂引用）
        levelUpListeners.clear();
    }

    // ───────── 击杀 ⇒ 进化 ─────────

    /**
     * 收到一次「**我是击杀者**」的通知 ⇒ 进化一级。
     *
     * <p>为什么这里不做"是不是我杀的"判断：本名单只挂在**击杀者那一侧**的实例上，
     * 投递方（{@code listener/hook/PlayerKilledHookListener}）已按击杀者取出本实例再投递
     * （见 {@link VitalsComponent#addPlayerKilledListener(RoleComponent, Consumer)} 的 javadoc）。
     * 再判一次等于把同一条口径抄两遍 —— 两处一旦漂移，错的那一处还不会报错。
     *
     * <p>唯一的覆写理由：角色自己想收窄"算不算一次击杀"（例如只要敌对阵营的人头）。
     * 覆写者若不去调 {@code super}，就等于自己接管了这条判断 —— 那是角色侧的产品口径，本类不预设。
     *
     * @param event 击杀载荷（击杀者 / 被杀者 / 两个角色 id）；本类只用到"发生过一次击杀"这一点
     */
    protected void onPlayerKilled(VitalsComponent.PlayerKilledEvent event) {
        increaseEvolutionLevel();
    }

    // ───────── 等级：读口 + 两个写口（升级 / 降级）─────────

    /** 当前进化等级（读口）。 */
    public int getCurrentEvolutionLevel() {
        return evolutionLevel;
    }

    /**
     * **进化值 +1**（两个写口之一；也是**唯一发通知**的那个），并逐个通知升级订阅方。
     *
     * <p>语义：先写等级、后通知 ⇒ 订阅方在自己的回调里读到的一定是**升级后**的等级
     * （{@link #getCurrentEvolutionLevel()} 与载荷的 {@code current} 同值），因此"按新等级发增益"
     * 不需要猜时序。
     *
     * <p>无上限（等级不封顶由角色侧自己判：要封顶就在自己的 {@code EvolutionPassive} 子类里拦）。
     *
     * @return 升级后的等级（调用方多半不用；需要时不必再读一次）
     */
    public int increaseEvolutionLevel() {
        int previous = evolutionLevel;
        evolutionLevel = previous + 1;
        notifyEvolutionLevelUpListeners(new LevelUp(previous, evolutionLevel));
        return evolutionLevel;
    }

    /**
     * **进化值 −1**（另一个写口）：减少一层进化。
     *
     * <h2>下限 {@code 0}（不是负数）</h2>
     * 等级是"计数"，到 0 就停住 —— 与 SanTE / 能量 / 「创伤」层数各处的 clamp 口径同源。
     * 因此在 0 级调用它**什么都不发生**：等级仍是 0、**不发通知**（没有变化就没有通知 ——
     * "等级变了几次 = 通知了几次"这条不变量因此在两个写口上都成立）。
     *
     * <h2>为什么不发通知</h2>
     * 订阅面的名字就是"**升**级"（{@link #addEvolutionLevelUpListener}）：降级时也响，等于给订阅方
     * 发一条下行的"升级通知"。降级是**调用方自己发起**的（它拿到返回值就知道结果），
     * 而"跟着等级重算增益"的订阅方应读 {@link #getCurrentEvolutionLevel()} 自行重算 ——
     * 详见类注释「降级为什么不发通知」一节。
     *
     * @return 降级后的等级（0 级时为 {@code 0}）
     */
    public int decreaseEvolutionLevel() {
        if (evolutionLevel <= 0) {
            return 0;
        }
        evolutionLevel = evolutionLevel - 1;
        return evolutionLevel;
    }

    // ───────── 升级订阅面（组件对外的唯一变更通道）─────────

    /**
     * 登记一个「升级通知」订阅者（幂等）。
     *
     * <p>本组件是"进化等级"这件事的唯一持有者，因此"它什么时候升级"也只能由它通知；
     * 订阅方（通常是同角色的另一个组件）拿到通知后自己决定做什么 —— 本类不认识任何具体组件。
     *
     * <p><b>订阅方可以就是本组件自己</b>：{@code owner = this}，在自己的 {@link #onStart()} 里登记
     * （例：{@code addEvolutionLevelUpListener(this, levelUp -> ...)}）—— 界面侧要"升级时播特效、
     * 刷新自己的显示"，这条就是最短的路。登记发生在自己身上，不经过任何组件查找。
     *
     * <p>用法与时机：在订阅方自己的 {@code start()} 里登记
     * （那时依赖已解析、本组件已生效），并把返回值存进私有字段以便按引用撤销：
     * <pre>{@code
     * EvolutionPassive evolution = svc().components().get(MyEvolution.class);
     * levelUpEntry = evolution.addEvolutionLevelUpListener(this, levelUp -> {
     *     if (levelUp.current() % 3 != 0) return;   // 例：每 3 级发一次增益
     *     heal(4);
     * });
     * }</pre>
     *
     * @param owner    订阅者（诊断归因用；{@code null} 则忽略）
     * @param listener 收到升级通知时调用（参数 = 前后两个等级；{@code null} 则忽略）
     * @return 本次登记对应的实例（用 {@link #removeEvolutionLevelUpListener} 按引用撤销）；
     *         参数为 {@code null} 时回 {@code null}
     */
    public EvolutionLevelUpListener addEvolutionLevelUpListener(RoleComponent owner, Consumer<LevelUp> listener) {
        if (owner == null || listener == null) {
            return null;
        }
        EvolutionLevelUpListener entry = new EvolutionLevelUpListener(owner, listener);
        if (!levelUpListeners.contains(entry)) {
            levelUpListeners.add(entry);
        }
        return entry;
    }

    /**
     * 撤销升级监听（按引用相等；不在名单里则 no-op 且返回 {@code false}）——
     * 调用方必须持有同一个 {@link EvolutionLevelUpListener} 实例（把
     * {@link #addEvolutionLevelUpListener(RoleComponent, Consumer)} 的返回值存进私有字段即可）。
     */
    public boolean removeEvolutionLevelUpListener(EvolutionLevelUpListener entry) {
        return entry != null && levelUpListeners.remove(entry);
    }

    /** 当前升级监听数（诊断读口；供探针与运行级取证使用）。 */
    public int evolutionLevelUpListenerCount() {
        return levelUpListeners.size();
    }

    /**
     * 逐个把升级登记交给调用方（"逐监听器故障隔离"的承载面）。
     *
     * <p>与 {@code VitalsComponent#forEachPlayerKilledListener} 同规：只提供遍历、不替调用方做派发决策；
     * 遍历前对名单取快照（{@code List.copyOf}）⇒ 遍历途中添加 / 移除监听器既不抛
     * {@code ConcurrentModificationException}，也不影响本趟（本次通知的接受集在进入时已定）。
     */
    public void forEachEvolutionLevelUpListener(Consumer<EvolutionLevelUpListener> action) {
        if (action == null) {
            return;
        }
        for (EvolutionLevelUpListener entry : List.copyOf(levelUpListeners)) {
            action.accept(entry);
        }
    }

    /**
     * 通知全部升级订阅方（一趟直调，逐条 try/catch）。
     *
     * <p><b>本组件内部就是生产派发点</b>（与生命组件不同：那条链在容器边界上逐个经
     * {@code RoleInstance#deliverHook}）⇒ 那条"只隔离抛异常的那一个、其余照常收到"的性质
     * 必须由本方法自己保住，因此这里逐条 try/catch，某个监听器抛异常时只记日志并继续。
     *
     * <p><b>不是第二条变更通道</b>：本方法只读名单并调监听器，不改等级（写口只有
     * {@link #increaseEvolutionLevel()}）；它也**不会**被击杀路径之外的任何地方调用。
     * 公开它的理由与生命组件同源：给"没有容器"的离线单元测试一条与生产同源的派发路径
     * （否则测试只能自己写循环，验的就成了测试自己的循环）。
     *
     * @param levelUp 本次升级的载荷；{@code null} 则不发通知
     */
    public void notifyEvolutionLevelUpListeners(LevelUp levelUp) {
        if (levelUp == null) {
            return;
        }
        forEachEvolutionLevelUpListener(entry -> {
            try {
                entry.listener().accept(levelUp);
            } catch (RuntimeException listenerFailure) {
                LOG.warning("[evolution] levelUp listener failed (owner=" + entry.owner().getId() + "): "
                        + listenerFailure);
            }
        });
    }

    // ───────── 装配期描述符 ─────────

    /**
     * 进化被动描述符（纯声明）：自带显示名与描述，继承描述符根类型（{@link PassiveSkill.Specification}）。
     * <p>没有栏位（被动家族的通则：拿着被动描述符根本写不出指定栏位的代码），因此不占热键栏。
     * <p>泛型化（{@code <E>} = 本组件自己的类型）：参数化后 {@code providedType()} 推导出具体进化类，
     * 别的组件可以 {@code requires(某具体进化.class)}（理由与 {@code Skill.Specification} 同源）。
     * <p>本类型**代本家族统一声明** {@code requires(VitalsComponent.class)} —— 凡进化组件都要读生命组件
     * （{@link #start()} 的唯一取用点），因此这条依赖不是"某些子类的选择"，而是家族的事实；
     * 写在基类描述符里，子类不可能漏声明。
     * <p>本类型不实现 {@link #create(String, ComponentServicesPort)}，具体组件必须自己声明嵌套
     * {@code Specification} 并覆写它（编译期强制）。
     */
    public abstract static class Specification<E extends EvolutionPassive> extends PassiveSkill.Specification<E> {

        protected Specification(Component displayName, List<Component> description) {
            super(displayName, description);
            requires(VitalsComponent.class);
        }
    }
}
