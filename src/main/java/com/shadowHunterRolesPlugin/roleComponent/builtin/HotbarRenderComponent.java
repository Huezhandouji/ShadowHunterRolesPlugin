package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarRenderer;
import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 框架级物品渲染组件。
 * <h2>它是什么 / 不是什么（先看这条边界）</h2>
 * 本组件把过去散在框架里、"由渲染器与角色实例各自持有"的渲染意图面收拢成一个组件形态：
 * <ul>
 * <li>是：意图登记 + 置脏的唯一归属点 —— 组件（以及框架自身）要"请求重绘"时走的是本组件登记的通道；</li>
 * <li>不是：它自己不出手写物品 —— 全仓唯一的物品写点仍是它内部持有的渲染器里那一次槽位写入
 * （{@code roleComponent/builtin/hotbar/HotbarRenderer#render()}）。本组件不暴露渲染器本身，也不给组件侧
 * 任何写入面，因此「组件只能请求、不能写」这条硬边界逐字保留。</li>
 * </ul>
 * <p>即：本组件是"要重绘"这件事的拥有者，"怎么写"归它自己持有的 {@link HotbarRenderer} —— 组件把本帧计划
 * 交出去、渲染器负责落位，框架侧只跟本组件打交道（见 {@link #flush()}）。
 * <h2>为什么要有它（归属理由）</h2>
 * 渲染器的管道职责只剩"按注册序取计划 → 唯一写点落位"，而意图面（谁在什么时候要求重绘）此前没有组件形态。
 * 收进一个框架级组件后：
 * <ul>
 * <li>它在容器里可见、可查询（{@code getAllByType} / {@code getComponent}），后续"不可动物品保护"与回调
 * 有明确的挂载点；</li>
 * <li>它与其他框架级服务组件同构（同包同族：{@code VitalsComponent} / {@code EnergyComponent} /
 * {@code SanTEComponent} / {@code BuffComponent} / {@code TaskComponent}，共 5 个；阵营不是容器里的
 * 服务组件，它的真值住在聚合根 {@code core/Role}）；</li>
 * <li>它是 {@code RoleComponent} 的子类，受既有生命周期与故障隔离（{@code guardedCall}）管辖。</li>
 * </ul>
 * <h2>本组件不做的事（边界）</h2>
 * <ul>
 * <li>不可动物品的保护（Inventory 指定位置不可动）不在本组件职责内；</li>
 * <li>受伤 / 治疗之外的渲染回调面（"物品被点击 / 被移动"等）同上；</li>
 * <li>冻结面迁移与代际对拍不在本组件职责内。</li>
 * </ul>
 */
public class HotbarRenderComponent extends RoleComponent {

    /**
     * 本组件的登记 id（谁是什么 id 由谁说了算）：容器装配时只读这个 id + 工厂（{@code data}），不点名组件类。
     */
    public static final String ID = "hotbarRender";

    // 脏标记由本组件自持（不接受外部注入的置脏回调），因此不存在"没接线就静默失效"的失效形态 —— 见 `requestRepaint()`。

    /**
     * 渲染器（唯一写点所在），由本组件持有并驱动。构造期注入一次（{@code null} 则本组件自建一个，见构造器）；
     * 渲染器不持有状态（脏标记 / 变化基线 / 帧入口条件都在本组件里），因此本组件是"要重绘"与"什么时候刷"的
     * 唯一归属点，渲染器只是它的写物品手。
     */
    private final HotbarRenderer hotbarRenderer;

    // ───────── 热键栏身份键（机制面唯一持有者） ─────────

    /**
     * 热键栏物品的唯一身份键（值 = 该物品实例的随机 UUID 字符串）。
     * <p>定义点唯一：全仓只有本处定义它（形态与 {@code Skill.Utils.SKILL_KEY} /
     * {@code MainWeapon.Utils.MAIN_WEAPON_KEY} 逐字同形：都经 {@code KeyFactory.Registry.of(String)} 的
     * 唯一入口）。
     * <p>身份粒度 = 物品实例（不是「组件 + 槽位」），因此同一组件可提交多个实例、各自独立句柄。
     * <p>写侧只在绑键步（{@link #bindIdentity}）出现，且与那次 {@code setItemMeta} 同一次完成（不得先写
     * meta 再取回二次写入）。
     */
    public static final NamespacedKey HOTBAR_ID_KEY = KeyFactory.Registry.of("hotbar_id");

    /** 机制面日志（前缀固定 {@code [hotbar]}；不依赖 Bukkit 静态入口，离线构造组件时也安全）。 */
    private static final Logger LOG = Logger.getLogger("ShadowHunterRoles.hotbar");

    /**
     * **活跃提交**：槽位 → 该槽位当前生效的那一个物品实例（同一槽位至多一条，重复提交 = 替换）。
     * <p>插入序 = 提交序；{@link #renderPlan()} 在拉取式条目之后按此序覆盖同槽位条目。
     */
    private final Map<Integer, Submission> liveSubmissions = new LinkedHashMap<>();

    /**
     * 已失效提交（按槽位留最近一条）：只为把「失效了但槽位仍被点击」这一异常态报出来（不得静默丢弃）
     * —— 不参与渲染、不参与派发。
     */
    private final Map<Integer, Submission> retiredSubmissions = new LinkedHashMap<>();

    /**
     * 置脏标记。初值 = {@code true}，因此即便无人置脏，首位 flush 也会完成首刷
     * （另有第一帧一次同步首刷，见 {@link #firstFlush()}）。
     */
    private boolean dirty = true;

    /** 本帧是否真的改了东西（由写物品段置位，由 {@link #consumeChanged()} 读取并清除）。 */
    private boolean changed;

    /**
     * 上一帧处于冷却中的组件（用于「冷却结束」那一次的收尾重绘）。
     * <p>为什么需要它：帧入口条件是「本组件已置脏 或 有占栏位组件正在冷却」。冷却结束的那一刻两个条件同时
     * 不成立，若就此返回，图标会停在冷却态、直到下一次置脏才恢复（现象 = 冷却好了但图标延迟一两秒才变回）。
     * 因此本集合记住"上一帧谁在冷却"，凡是这一帧已不在冷却的则强制置脏一次（恰好一次）。
     */
    private final Set<String> coolingLastFrame = new LinkedHashSet<>();

    /** 首刷是否已完成（由第一帧 `update()` 完成，见 {@link #start()} 的说明）。 */
    private boolean firstFlushDone = false;

    /**
     * 「本帧真的刷新了」的通知名单（函数式接口 · 使用者自己的函数）。
     * <p>登记方式：想收通知的组件在自己的 {@code start()} 里调 {@link #addRenderListener(Runnable)} 把
     * 自己的函数加进来。本组件不按类型匹配使用者 —— 那会要求使用者实现本组件的嵌套接口（= 机制面替使用者
     * 规定形状）；用 JDK 的 {@code Runnable} 则零自定义接口。
     * <p>顺序 = 登记序（{@code CopyOnWriteArrayList} 的遍历语义），与既有"按注册序扇出"逐字一致。
     * <p>回执：{@code add} 返回同一条函数（引用相等），需要注销者把它存起来调
     * {@link #removeRenderListener(Runnable)} 即可。
     */
    private final List<Runnable> renderListeners = new CopyOnWriteArrayList<>();

    /**
     * 上一帧真正写入的槽位内容（变化基线）。{@code null} = 尚无基线（首次渲染），视作"有变化"
     * （首刷应当被通知）。
     */
    private Map<Integer, ItemStack> lastRendered;

    /**
     * 渲染器由本组件自建（计划 = 本组件的 {@link #renderPlan()}），构造点唯一在组件内部，框架侧不经手
     * 渲染器本身。
     */
    public HotbarRenderComponent(String id, ComponentServicesPort services) {
        super(id, services);
        this.hotbarRenderer = new HotbarRenderer(this::renderPlan);
    }

    /**
     * 本组件的装配描述符（与技能 / 被动同规；不带栏位、无额外依赖）。
     * <p>本组件不占热键栏（它渲染热键栏，自己不是栏位里的物品），因此用极简描述符，不能用
     * {@link HotbarSpecification}（它的 {@code validateForFreeze()} 会要求必须设栏位）。
     */
    public static final class Specification extends RoleComponent.Specification<HotbarRenderComponent> {

        public Specification() {
            super("HotbarRender");
        }

        @Override
        public HotbarRenderComponent create(String id, ComponentServicesPort services) {
            return new HotbarRenderComponent(id, services);
        }
    }

    /**
     * 帧末活动（每 tick 调一次，落点 = 组件更新与到期扫描之后）：判脏 → 写物品 → 清脏 → 取变更，
     * 这条顺序不可交换。
     * <p>入口条件：① 本组件已置脏，或 ② 外观依赖活状态的占栏位组件正在冷却，则每 tick 至少刷一次
     * （否则技能名里的 {@code " x.xs"} 不再逐 tick 递减 = 可见行为变化）。两个条件都不成立则不写任何槽位
     * （「空闲 tick 零 {@code setItem}」）。
     * <p>只置脏、不写物品的是 {@link #requestRepaint()}；本方法才是写物品的那一半。
     */
    public void flush() {
 // 「冷却结束」收尾：凡上一帧在冷却、这一帧已不在冷却的组件则强制置脏一次
 //（否则切换那一刻两个入口条件都不成立，图像会停在冷却态直到下次置脏）
        markDirtyForFinishedCooldowns();
        if (!dirty && !hasCoolingTickingComponent()) {
            return;
        }
        renderNow();
        dirty = false;
        rememberCooling();
 // "本帧真的刷新了"的通知由本组件自己扇出（名单 = 使用者 addRenderListener 登记的函数）
        if (consumeChanged()) {
            notifyRendered();
        }
    }

    /** 冷却结束则置脏一次（恰好一次）：与 {@link #coolingLastFrame} 对比得出。 */
    private void markDirtyForFinishedCooldowns() {
        if (coolingLastFrame.isEmpty()) {
            return;
        }
        for (String id : coolingLastFrame) {
            RoleComponent component = svc().components() == null ? null : svc().components().getById(id);
            if (component instanceof ActiveComponent active && !active.isCoolingDown()) {
                dirty = true;                     //本轮至少有一次收尾重绘
                return;
            }
        }
    }

    /** 记下**本帧**仍在冷却的组件 id（用于下一帧检测"冷却结束"）。 */
    private void rememberCooling() {
        coolingLastFrame.clear();
        if (svc().components() == null) {
            return;
        }
        for (RoleComponent component : svc().components().all()) {
            if (component instanceof ActiveComponent active && active.isCoolingDown()) {
                coolingLastFrame.add(component.getId());
            }
        }
    }

    /**
     * 同步首刷一次（写物品段，不看入口条件）。
     * <p>由本组件自己的第一帧 {@code update()} 调用 —— 见 {@link #start()} 与 {@link #update()} 的说明。
     */
    public void firstFlush() {
        renderNow();
    }

    /** 写物品段（两个入口共用的那一半）：取计划 → 交渲染器落位 → 记基线与变化结论。 */
    private void renderNow() {
        HotbarRenderer.Result result = hotbarRenderer.render(playerOrNull(), lastRendered);
        lastRendered = result.baseline();
        changed = result.changed();
    }

    /** 本实例的玩家；服务集未带自身面时回 {@code null}（无玩家则不写任何槽位）。 */
    private Player playerOrNull() {
        return (svc().self() == null) ? null : svc().self().player();
    }

    /**
     * 本帧是否发生了真实变化（渲染回调的取值点）：读取并清除（一次性，"读过即消费"）。
     * <p>清除语义很重要：若不清除，下一帧即使什么都没变，也会沿用上一帧的 {@code true} 而多报。
     */
    public boolean consumeChanged() {
        boolean value = this.changed;
        this.changed = false;
        return value;
    }

    /**
     * 置脏（幂等：同一 tick 多次置脏与一次等价）。不写物品。
     * <p>调用者 = 需要重绘的一方（施放 / 攻击成功后由 listener 经本组件的通用面请求；能量变更、buff 移除
     * 同理）；组件侧的重绘请求走 {@link #requestRepaint()}。
     */
    public void markDirty() {
        this.dirty = true;
    }

    // ───────── 回调面：函数式接口名单（使用者自己 add 自己的函数）─────────

    /**
     * 登记「本帧真的刷新了」的通知（函数式接口：传一个 {@code Runnable} 即可）。
     * <p>谁调：想收通知的组件在自己的 {@code start()} 里调一次。本组件不按类型匹配使用者，因此使用者不必
     * 实现本组件的任何接口。
     * <p>时机：只在本帧真的有变化时被调（写入完成之后）；改不了这一帧的结果。
     * <p>故障隔离：逐个调用，某个抛异常则只记日志并继续，其余监听器照常收到（不在循环外层套一层 try，
     * 那会让首异常吞掉后续全部）。
     * @param listener 使用者的函数（{@code null} 则忽略）
     * @return 同一条函数（引用相等）；需要注销者把它存进字段，交给 {@link #removeRenderListener(Runnable)}
     */
    public Runnable addRenderListener(Runnable listener) {
        if (listener != null) {
            renderListeners.add(listener);
        }
        return listener;
    }

    /**
     * 注销通知（按引用相等，与 {@link List#remove(Object)} 的既有语义一致）。
     * @param listener {@link #addRenderListener(Runnable)} 当时返回的同一条函数
     * @return 是否真的移除了
     */
    public boolean removeRenderListener(Runnable listener) {
        return listener != null && renderListeners.remove(listener);
    }

    /** 当前登记的监听器条数（诊断读口；不参与任何行为决策）。 */
    public int renderListenerCount() {
        return renderListeners.size();
    }

    /**
     * 把"本帧真的刷新了"通知给全部登记的函数（逐个调用 + 逐个故障隔离）。
     * <p>调用者 = 帧末 flush 的尾部（本组件自己）。顺序 = 登记序；不用外层 try：首异常只隔离它自己，
     * 后续照常收到。
     */
    private void notifyRendered() {
        for (Runnable listener : renderListeners) {
            try {
                listener.run();
            } catch (RuntimeException listenerFailure) {
                LOG.warning("[hotbar] render listener failed: " + listenerFailure);
            }
        }
    }

    /** 是否已置脏（帧末入口条件之一）。 */
    public boolean isDirty() {
        return dirty;
    }

    // ───────── 生命周期：本组件自己的两个节拍（容器不再代劳）─────────

    /**
     * 开始生效：不在这里首刷。
     * <p>为什么不能在这里首刷（实证的 NPE）：{@code start()} 是逐组件广播的，而本组件在注册表最前
     * （内建块先于角色组件注册），因此本组件 {@code start()} 跑的时候，其余组件一个都还没轮到 {@code start()}。
     * 而组件普遍在自己的 {@code start()} 里解析依赖字段（例：{@code RedSanctifiedBladeMainWeapon} 在
     * {@code start()} 里取 {@code BuffComponent}），此刻取画法会拿到 {@code null} 字段而 NPE。
     * <p>因此首刷推迟到第一帧 {@code update()}（那时所有组件的 {@code awake()} / {@code start()} 都已执行完），
     * 见 {@link #update()}。代价 = 热键栏晚最多 1 tick（50 ms）就绪。
     */
    @Override
    public void start() {
        // 订阅 buff 移除：buff 消失会改变图标外观（"被沉默 / 眩晕"的禁用态等），需要置脏重绘。
        // 依赖方向 = 渲染组件主动去认识 buff 组件（而不是记账器 / 别的组件反向认识渲染）。
        BuffComponent buffs = findComponent(BuffComponent.class);
        if(buffs != null){
            buffs.manager().onBuffRemoved(this::markDirty);
        }
    }

    /**
     * 每 tick：帧末刷新（判脏 → 写物品 → 清脏 → 扇出"真的刷新了"）。
     * <p>第一帧先做首刷：那时所有组件的 {@code awake()} / {@code start()} 都已执行完，取画法安全
     * （这也是首刷不能放在 {@link #start()} 里的原因）。
     * <p>为什么在 {@code update()}：刷新点 = tick 末尾、其余组件更新之后，因此本组件的 {@code update()} 天然
     * 最后跑，时序与"容器在 update 广播之后调 flush"逐字等价。
     * <p>入口条件在 {@link #flush()} 内部（未置脏且无占栏位者冷却则不写任何槽位）。
     */
    @Override
    public void update() {
        if (!firstFlushDone) {
            firstFlushDone = true;
            firstFlush();
            rememberCooling();
            return;
        }
        flush();
    }

    /**
     * 帧末 flush 完成后清脏。唯一消费者 = {@link #flush()} 的尾部；组件 / 渲染器内部都不得调用
     * （会吞掉本 tick 的可见更新）。
     */
    public void clearDirty() {
        this.dirty = false;
    }

    /**
     * 「组件 id → 栏位」登记表（本组件自持；由组件在装配期向本组件登记自己的栏位）。
     * <p>为什么不让渲染侧去描述符里读：那是"框架要认识带栏位描述符"，而栏位是热键栏的契约，归本组件持有最
     * 自然（组件自己知道自己要占哪一格）。顺序 = 登记顺序（= 组件装配序），因此落位结果可复现。
     */
    private final Map<String, Integer> slotByComponent = new LinkedHashMap<>();

    /**
     * 组件登记自己的栏位（唯一入口；{@code slot == null} 则撤销登记 = 不占栏位）。
     * <p>仲裁：同一栏位被两个组件占用则抛 {@link IllegalArgumentException}（fail-fast）。
     * <p>两处判据的"同一种说法"：本方法与装配期 {@code core/Role.Builder#addComponent} 扫描述符快照那一段
     * 共用同一套异常类型与文案，因此同一判据只有一种说法（两者拦的是不同阶段的坏法：装配期拦"模板写错"，
     * 本处拦"运行期登记错"，且本处还覆盖不经装配器的动态添加那条路径）。
     * <p>同一组件重复登记同一栏位则幂等（换栏位则先撤旧再记新）。
     */
    public void registerSlot(RoleComponent component, Integer slot) {
        if (component == null) {
            return;
        }
        if (slot == null) {
            slotByComponent.remove(component.getId());
            return;
        }
        // 校验与文案逐字对齐装配期（core/Role.Builder#addComponent 扫快照的那一段）：
        // ① 越界则抛同一句 IllegalArgumentException
        // ② 冲突则抛同一句冻结文案（Slot N is already occupied by 'X'.），只追加一个分句点名"谁想占"，
        //    冻结的主句一字未动，因此既有断言不受影响。
        if (slot < 0 || slot > 8) {
            throw new IllegalArgumentException("Slot must be between 0 and 8, got: " + slot);
        }
        for (Map.Entry<String, Integer> occupied : slotByComponent.entrySet()) {
            if (!occupied.getKey().equals(component.getId()) && occupied.getValue().equals(slot)) {
                throw new IllegalArgumentException("Slot " + slot + " is already occupied by '"
                        + occupied.getKey() + "'. (registration refused for '" + component.getId() + "')");
            }
        }
        slotByComponent.put(component.getId(), slot);
    }

    /** 某组件登记的栏位（未登记则为 {@code null} = 不占热键栏）。 */
    public Integer slotOf(String componentId) {
        return componentId == null ? null : slotByComponent.get(componentId);
    }

    /** 已登记的栏位数（诊断读口）。 */
    public int registeredSlotCount() {
        return slotByComponent.size();
    }

    /**
     * **本帧计划**：按注册序给出全部占栏位、且本帧能产出物品的 {@code (槽位, 物品)} 对。注册序 = 容器内当前序
     * （动态序 = 渲染序）；栏位取自组件自己的表现规格（装配期由装配点 {@code setSlot} 指定，与装配条目里的
     * 栏位同源），物品取自组件自己的默认画法读口（{@link #buildItemOf(RoleComponent)}）。
     * <p>不占栏位、或本帧画不出物品的组件不进计划（与"跳过该槽位"同义）。
     */
    private List<HotbarRenderer.Entry> renderPlan() {
        Map<Integer, ItemStack> bySlot = new LinkedHashMap<>();
        // ① 拉取式：物品来自组件自己的默认画法读口 —— 画法归属不动
        if (svc().components() != null) {
            for (RoleComponent component : svc().components().all()) {
                ItemStack item = buildItemOf(component);
                if (item == null) continue;
                // 落位读登记表（组件自己登记的栏位）；未登记则不占热键栏
                Integer slot = slotByComponent.get(component.getId());
                if (slot == null) continue;
                bySlot.put(slot, item);
            }
        }
        // ② 提交式：机制面在绑键步写身份键（恰好一次 setItemMeta）；同槽位覆盖拉取式条目
        for (Submission submission : liveSubmissions.values()) {
            ItemStack bound = bindIdentity(submission);
            if (bound != null) {
                bySlot.put(submission.slot, bound);
            }
        }
        List<HotbarRenderer.Entry> plan = new ArrayList<>(bySlot.size());
        for (Map.Entry<Integer, ItemStack> entry : bySlot.entrySet()) {
            plan.add(new HotbarRenderer.Entry(entry.getKey(), entry.getValue()));
        }
        return plan;
    }

    /**
     * **帧入口条件之一**：是否存在占栏位组件正在冷却。
     * <p>判据是"占栏位且正在冷却"，不是"外观依赖活状态"：两者都要能刷 ——
     * <ul>
     * <li>依赖活状态的（技能家族，名里有秒数）每 tick 都要刷（否则秒数不再递减 = 可见行为变化）；</li>
     * <li>不依赖活状态的（主武器）在冷却期间也必须能刷 —— 它的图标要切到冷却态，若只在"置脏那一刻"刷一次，
     * 之后冷却结束前没人再刷，图标就与真实状态脱节。</li>
     * </ul>
     * 不占栏位的组件不参与（它们不进渲染计划，刷了也看不见）。
     */
    private boolean hasCoolingTickingComponent() {
        if (svc().components() == null) {
            return false;
        }
        for (RoleComponent component : svc().components().all()) {
            // "占栏位"读登记表（与渲染落位同源）
            if (slotByComponent.get(component.getId()) == null) continue;
            if (component instanceof ActiveComponent active && active.isCoolingDown()) return true;
        }
        return false;
    }

    /**
     * **请求重绘**：把"要重绘"这件事置到本组件的脏标记上，下一次帧末 flush 才会写物品。这是唯一的组件侧渲染
     * 意图入口。
     * <p>本组件自持脏标记（{@link #markDirty()}）：早期形态是"注入一个 {@code RepaintSink} 回调"，那个注入点
     * 失去调用者后回调恒为 {@code null}，{@code requestRepaint()} 会静默变成空操作（射击 / 能量变化 / 冷却
     * 置脏全部失效）；现直接置本组件的字段，不可能再出现"没接线就静默失效"。
     * <p>幂等：脏标记是布尔量，同一 tick 内多次调用与一次等价。
     * <p>不写物品：返回后物品尚未被改写（「空闲 tick 零 {@code setItem}」逐字不变）。
     */
    public void requestRepaint() {
        markDirty();
    }

    /**
     * 渲染回调（读侧：只通知、不可否决）。
     * <p>形态（照 {@code VitalsComponent.Participant}）：方法返回 {@code void}，因此"改量"与"否决"在类型上
     * 不可表达，实现者只能观察，不能干预渲染结果。
     * <p>触发点 = 渲染器 {@code HotbarRenderer.render()} 真正完成一次刷新之后：这是唯一能覆盖"内容变了但
     * 槽位没变"的时机（例：同一个技能格从"可用"变"冷却中"，槽位没动、物品换了，按槽位变化判会漏）。
     * <p>变化判据（无变化不得回调）：触发不等于"每帧都回调" —— 渲染器维护上一帧实际写入的槽位内容基线，
     * 只有"本帧与上一帧真的不同"才算一次变化，无变化的那一帧回调 0 次。
     * <ul>
     * <li>判据 = {@code HotbarRenderer} 的逐槽位内容比较（记 slot + {@code ItemStack}）；</li>
     * <li>不能拿脏标记当判据：脏标记只表示"有人请求过重绘"，请求之后重建的物品可能逐字相同，会多报；</li>
     * <li>首帧（无基线）视作"有变化"，因此首刷会回调 1 次。</li>
     * </ul>
     * <p>顺序（逐条，不可交换）：判脏 → 写物品（唯一写点）→ 清脏；然后若本帧有真实变化，把本组件自持的
     * 监听器名单逐个调一遍（某个抛异常则按故障隔离语义只隔离它、其余照常收到）。回调只读不取消：它不参与
     * "写什么"的决策，改不了这一帧的渲染结果。
     * <p>通知面 = {@link #addRenderListener(Runnable)} 登记的函数名单。
     * <p>边界（如实申报）：回调只在"有变化"时响，想"每帧都做事"的组件不该用它；另：回调不保证"物品已被
     * 玩家看到" —— 它紧跟写入，客户端同步由平台负责。
     */

    // ───────── 提交面：其他组件提交物品 → 机制面绑身份 → 返回句柄 ─────────

    /**
     * 句柄的键（listener 收窄后的「哪种键」；由机制面按槽位派发到句柄回调）。
     * <p>组件侧词汇不动（{@code CastTrigger} / {@code CastSignal} / {@code AttackSignal} 仍归组件），本枚举
     * 只是机制面内部的键分类。
     */
    public enum HotbarKey { LEFT, RIGHT, DROP, ATTACK }

    /**
     * **热键栏物品句柄**：提交者拿它设回调、查身份、注销或替换。
     * <p>失效语义：{@link #release()} / {@link #replace(ItemStack)} 以及机制面的批量注销都会让本句柄立即
     * 失效，之后绝不允许回调被调用（机制面在派发前逐句柄检查 {@code live}，
     * 见 {@link #dispatch(int, HotbarKey)}）。
     */
    public interface HotbarItemHandle {

        /** 设左键回调（{@code null} = 清除该键回调）。 */
        HotbarItemHandle onLeftClick(Runnable callback);

        /** 设右键回调（{@code null} = 清除该键回调）。 */
        HotbarItemHandle onRightClick(Runnable callback);

        /** 设 Q 丢回调（{@code null} = 清除；Q 已被复用为"用技能"）。 */
        HotbarItemHandle onDrop(Runnable callback);

        /** 设攻击命中承受方回调（{@code null} = 清除）。 */
        HotbarItemHandle onAttackVictim(Runnable callback);

        /** 本句柄当前对应的槽位；已失效则 {@code OptionalInt.empty()}。 */
        OptionalInt slot();

        /** 本实例的身份值（= PDC 键的值）。失效后仍返回同一个值（它是实例身份，不是"是否生效"）。 */
        String uuid();

        /** 注销：此后本句柄不再收到任何回调。粒度 = 按实例（不影响同一组件的兄弟句柄）。 */
        void release();

        /** 替换本实例的内容（槽位不变；旧句柄随之失效，返回新句柄）。 */
        HotbarItemHandle replace(ItemStack item);
    }

    /**
     * 一条提交：实例身份（{@code uuid}）由机制面在提交时生成并由本对象持有，因此每帧重组物品时把同一个
     * {@code uuid} 再写回新副本，uuid 不漂移（这就是"回调不丢"的前提：身份不随 {@code ItemStack} 对象重建
     * 而变）。
     */
    private final class Submission {
        private final int slot;
        private final String componentId;
        private final String uuid;
        private final ItemStack base;
        private Runnable leftClick;
        private Runnable rightClick;
        private Runnable drop;
        private Runnable attackVictim;
        private volatile boolean live = true;

        private Submission(int slot, String componentId, String uuid, ItemStack base) {
            this.slot = slot;
            this.componentId = componentId;
            this.uuid = uuid;
            this.base = base;
        }

        private Runnable callbackFor(HotbarKey key) {
            return switch (key) {
                case LEFT -> leftClick;
                case RIGHT -> rightClick;
                case DROP -> drop;
                case ATTACK -> attackVictim;
            };
        }

        private HotbarItemHandle handle() {
            return new Handle(this);
        }

        private void retire() {
            HotbarRenderComponent.this.retireSubmission(this);
        }

        private HotbarItemHandle replaceWith(ItemStack item) {
            return HotbarRenderComponent.this.replaceSubmission(this, item);
        }
    }

    /** 句柄实现：只做「转发到 Submission」+「失效翻转」；不持有任何 Bukkit 库存对象。 */
    private static final class Handle implements HotbarItemHandle {
        private final Submission submission;

        private Handle(Submission submission) {
            this.submission = submission;
        }

        @Override public HotbarItemHandle onLeftClick(Runnable callback) { submission.leftClick = callback; return this; }
        @Override public HotbarItemHandle onRightClick(Runnable callback) { submission.rightClick = callback; return this; }
        @Override public HotbarItemHandle onDrop(Runnable callback) { submission.drop = callback; return this; }
        @Override public HotbarItemHandle onAttackVictim(Runnable callback) { submission.attackVictim = callback; return this; }

        @Override public OptionalInt slot() {
            return submission.live ? OptionalInt.of(submission.slot) : OptionalInt.empty();
        }

        @Override public String uuid() {
            return submission.uuid;
        }

        @Override public void release() {
            submission.retire();
        }

        @Override public HotbarItemHandle replace(ItemStack item) {
            return submission.replaceWith(item);
        }
    }

    /**
     * 提交入口：吃「槽位 + 提交者 id + 物品」，机制面自己在绑键步写入 {@link #HOTBAR_ID_KEY}（值 = 本次提交
     * 新生成的随机 UUID）。
     * <p>componentId 以字符串传（不传组件引用、不传具体组件类型），因此机制面不需要提交者的类型。
     * <p>重复提交同一槽位 = 替换：旧实例句柄立即失效。成功则置一次脏（下一次帧末 flush 落位）。
     */
    public HotbarItemHandle submit(int slot, String componentId, ItemStack item) {
        if (slot < 0) {
            throw new IllegalArgumentException("[hotbar] slot must be >= 0: " + slot);
        }
        if (componentId == null || componentId.isEmpty()) {
            throw new IllegalArgumentException("[hotbar] componentId must not be null/empty");
        }
        if (item == null) {
            throw new IllegalArgumentException("[hotbar] item must not be null");
        }
        Submission previous = liveSubmissions.get(slot);
        if (previous != null) {
            retireSubmission(previous);
        }
        Submission submission = new Submission(slot, componentId, UUID.randomUUID().toString(), item.clone());
        liveSubmissions.put(slot, submission);
        markDirty();
        return submission.handle();
    }

    /** 该槽位当前是否有活跃句柄（= listener 收窄后的取消判据）。 */
    public boolean hasLiveHandle(int slot) {
        Submission submission = liveSubmissions.get(slot);
        return submission != null && submission.live;
    }

    /** 该槽位活跃句柄的身份值（无活跃句柄则为 {@code null}）。 */
    public String identityOf(int slot) {
        Submission submission = liveSubmissions.get(slot);
        return (submission != null && submission.live) ? submission.uuid : null;
    }

    /**
     * 按槽位反查组件 id（栏位知识的归属：本组件）。
     * <p>读的是本组件的登记表（{@code slotByComponent}），与 {@link #renderPlan()} 落位同源，因此反查结果
     * 与渲染结果不可能脱节。未占用则 {@code null}。
     * <p>实例方法：登记表是实例状态，反查必须问"这一个实例"。
     * @param components 要扫的组件集（顺序 = 调用方给的顺序；命中即返回）
     * @param slot 槽位（0..8）
     */
    public String componentIdAtSlot(List<RoleComponent> components, int slot) {
        if (components == null) {
            return null;
        }
        for (RoleComponent component : components) {
            if (component == null) {
                continue;
            }
            // 读登记表（与 renderPlan 同源，不会与渲染结果脱节）；未登记则不占栏位
            Integer registered = slotByComponent.get(component.getId());
            if (registered != null && registered == slot) {
                return component.getId();
            }
        }
        return null;
    }

    /** 活跃句柄数（诊断读口）。 */
    public int liveHandleCount() {
        return liveSubmissions.size();
    }

    /**
     * 按槽位派发（listener 收窄为「事件 → (槽位, 键)」之后的落点）。
     * <p>逐句柄故障隔离由本机制面自带：捕获 + 记日志 + 继续 —— 不引用容器的受保护调用设施（那会把
     * 「SanTE 逐监听器隔离」的口径从 1 变 2），也不由机制面决定"是否隔离该组件"（归属留容器）。
     * <p>不得静默吞事件：每次"本可以派发却没派发"都留一行可 grep 的日志（前缀固定 {@code [hotbar]}）：
     * 无活跃句柄为 DEBUG 级；回调为 {@code null} / 句柄已失效为 WARNING 级。
     * @return {@code true} = 该槽位有活跃句柄（则调用方照常取消事件、物品受保护）；{@code false} = 无
     */
    public boolean dispatch(int slot, HotbarKey key) {
        Submission submission = liveSubmissions.get(slot);
        if (submission == null || !submission.live) {
            Submission retired = retiredSubmissions.get(slot);
            if (retired != null) {
                LOG.log(Level.WARNING, "[hotbar] handle expired but slot clicked slot=" + slot
                        + " key=" + key + " by=" + retired.componentId);
            } else {
                LOG.log(Level.FINE, "[hotbar] no live handle slot=" + slot + " key=" + key);
            }
            return false;
        }
        Runnable callback = submission.callbackFor(key);
        if (callback == null) {
            LOG.log(Level.WARNING, "[hotbar] no-callback slot=" + slot + " key=" + key + " by=" + submission.componentId);
            return true;
        }
        try {
            callback.run();
        } catch (RuntimeException ex) {
            LOG.log(Level.WARNING, "[hotbar] callback threw slot=" + slot + " key=" + key
                    + " by=" + submission.componentId, ex);
        }
        return true;
    }

    /**
     * 注销某个组件提交的全部句柄（供生命周期接线：组件 {@code stop()} / 被隔离 / 角色清除）。
     * 接线点属聚合根侧。
     */
    public void releaseHandlesOf(String componentId) {
        for (Submission submission : List.copyOf(liveSubmissions.values())) {
            if (submission.componentId.equals(componentId)) {
                retireSubmission(submission);
            }
        }
    }

    /** 注销**全部**句柄（角色清除 / 实例销毁路径）。 */
    public void releaseAllHandles() {
        for (Submission submission : List.copyOf(liveSubmissions.values())) {
            retireSubmission(submission);
        }
    }

    /** 失效 = 状态翻转 + 移出活跃表 + 留痕（不得静默丢弃）。 */
    private void retireSubmission(Submission submission) {
        if (!submission.live) {
            return;
        }
        submission.live = false;
        liveSubmissions.remove(submission.slot, submission);
        retiredSubmissions.put(submission.slot, submission);
        markDirty();
    }

    /** 替换实现：旧实例失效 + 按同一槽位 / 同一提交者重新提交（新 uuid = 新身份）。 */
    private HotbarItemHandle replaceSubmission(Submission old, ItemStack item) {
        int slot = old.slot;
        String componentId = old.componentId;
        retireSubmission(old);
        return submit(slot, componentId, item);
    }

    /**
     * 绑键步（机制面唯一写身份键处）：克隆提交内容 → 写 {@link #HOTBAR_ID_KEY} → 恰好一次
     * {@code setItemMeta}（不得先写 meta 再取回二次写入）。
     * <p>写入值 = 该提交自己的 uuid，因此每帧重组都写同一个值，uuid 不漂移。
     */
    private ItemStack bindIdentity(Submission submission) {
        ItemStack bound = submission.base.clone();
        ItemMeta meta = bound.getItemMeta();
        if (meta == null) {
            return null;
        }
        meta.getPersistentDataContainer().set(HOTBAR_ID_KEY, PersistentDataType.STRING, submission.uuid);
        bound.setItemMeta(meta);
        return bound;
    }

    // ───────── 热键栏能力的读侧契约：渲染组件按组件读数据 ─────────
    // 声明面 / 物品产出面 / 能上热键栏这三件事都是数据：组件用自己的公开方法表达，本组件只提供三个静态读口，
    // 不新增任何接口（接口只在"表达的东西不能成为组件"时才有理由，这三件事都能由组件自己的方法表达，
    // 也就不必让每个组件去 implements 一个嵌套类型）。接受集与旧接口逐字相同：旧接口的唯一实现者是主动组件基类及其子类
    // （技能家族 ∪ 主武器家族）经 `instanceof ActiveComponent` 命中同一个集合；被动组件两侧都不在内。

    /**
     * 读声明面（装配期数据：图标 / 显示名 / 描述 / 冷却声明值 / 耗能声明值 / 基础物品）：该组件持有的
     * {@link HotbarSpecification}；没有声明面（不在热键栏家族）则为 {@code null}。
     * <p>数据源 = 描述符（装配期对象，{@code freeze()} 之后只读）；组件侧经它自己的 {@code specification()}
     * 暴露。
     */
    public static HotbarSpecification<?> specificationOf(RoleComponent component) {
        return component instanceof ActiveComponent active ? active.specification() : null;
    }

    /**
     * 读"本组件这一帧的物品"（完整形态：材质 / 名称 / 后缀 / lore / 识别键都已就位）—— 由框架在帧末 flush
     * 的写物品段调用（每帧至多一次/组件），并与注册序同序遍历；组件不得在此方法里写玩家背包（写物品的唯一
     * 落点仍是渲染器的那一次槽位写入）。
     * <p>不产出物品的组件（不在热键栏家族）则为 {@code null}（渲染器据此跳过该槽位）。
     * <p>为什么默认画法在组件基类而不是描述符：画物品要读运行期状态（冷却剩余刻数 / 闸门 / 当前能量），而
     * 描述符是装配期对象、拿不到 {@code svc()}，因此默认画法写在技能与主武器这两个组件基类里（三态材质 ·
     * 六条状态文案 · 技能带 {@code x.xs} 而主武器不带 · 两个 PDC 键 · 声明数据取自描述符）。
     * <p>覆写者须知（PDC 键与六条文案均允许组件覆写，覆写者自负其责）：键写错则监听器前置闸门识别不到该物品
     * （点击它无任何反应）；键缺失则角色清除时扫不到它（物品残留在背包/热键栏里）。框架不再为此提供保障
     * （不再"写完回读校验"）—— 这两条是已申报的代价，不是缺陷。
     */
    public static ItemStack buildItemOf(RoleComponent component) {
        return component instanceof ActiveComponent active ? active.buildItem() : null;
    }

    /**
     * **读"外观是否依赖活状态"**：{@code true} = 它的外观会在没有框架置脏事件的情况下自己变（例如技能冷却名里
     * 的 {@code x.xs} 秒数每刻都在变），因此只要它在冷却中，框架就必须每 tick 至少刷一次，否则玩家看到的是
     * 陈旧外观。
     * <p>为什么是自报值、而不是框架里的一句 {@code instanceof Skill}：把「外观含秒数」写死成具体类，则
     * ① 第三类"带倒计时外观"的组件加进来时必须改框架文件；② 覆写掉秒数外观的子类仍会被每 tick 重绘（白写）。
     * 改为自报后：新组件只加新文件即可（默认 {@code false}，需要就覆写 {@code true}），而"覆写掉活状态外观"
     * 的子类可以覆写成 {@code false}，不再每 tick 重绘。
     * <p>默认值 = {@code false}：只有技能家族的默认画法带秒数，该家族覆写为 {@code true}，主武器与被动保持
     * {@code false}，因此既有组件的真值表逐字不变。
     * <p>不产出物品的组件（不在热键栏家族）为 {@code false}（与旧形态"接口默认值 false"同义）。
     * <p>本读数只回答"要不要每刻刷"；"写不写"仍由帧末 flush 决定（空闲 tick 零 setItem 不变）。外观依赖活
     * 状态、但变化不是每刻的组件应返回 {@code false}，并在状态真的变了时用 {@link #requestRepaint()} 主动
     * 请求 —— 那才是它的刷新节拍。
     */
    public static boolean dependsOnLiveStateOf(RoleComponent component) {
        return component instanceof ActiveComponent active && active.dependsOnLiveState();
    }
}
