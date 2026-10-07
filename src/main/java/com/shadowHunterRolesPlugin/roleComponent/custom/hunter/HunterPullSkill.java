package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * 「猎手」技能之二：**拉回**（垂泪藤）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>引导 0.3 秒</b>（有蓄力音效），之后向前方 <b>12 格</b>造成一次 <b>15 点物理伤害</b>的穿刺攻击；</li>
 *   <li>用**类似紫色的粒子**模拟攻击轨迹；被命中的敌人身上出现<b>暴击粒子、单个爆炸粒子、
 *       紫色菱形边框</b>；</li>
 *   <li><b>一次最多攻击一名敌人</b>；</li>
 *   <li>并在 <b>0.8 秒</b>后尝试将被命中的敌人<b>拉回</b>到角色**视角前方**；</li>
 *   <li>拉回方式 = 敌人以 <b>每秒 10 格</b>的速度向此角色视角前方位移；
 *       <b>再次释放 ⇒ 每秒 20 格</b>（加速拖拽）；</li>
 *   <li><b>最多只能拉着敌人 2.5 秒</b>；</li>
 *   <li>期间，敌人可以**通过一次技能的主动位移来脱离控制**；</li>
 *   <li>CD <b>6 秒</b>。</li>
 * </ol>
 *
 * <h2>★ 2026-10-02 用户新增的四条</h2>
 * <ol>
 *   <li><b>速度</b>：常规 10 格/秒、再次释放 20 格/秒（见下方量化口径）；</li>
 *   <li><b>时长上限 2.5 秒</b>（{@value #PULL_MAX_TICKS} 刻）；</li>
 *   <li><b>打空也有投出路径</b>：紫色穿刺路径**无条件**画 —— 有目标画到目标胸口，
 *       没目标从眼睛沿水平前向画满 {@value #THRUST_RANGE} 格
 *       （改前它在"命中"分支里 ⇒ 空放时一点粒子都没有）；</li>
 *   <li><b>被命中的敌人死亡 ⇒ 不再被继续控制拖拽</b>（需求 2026-10-02 二次明确）。
 *       判据统一为纯函数 {@link #isDraggable(boolean, boolean, double)}，用在**四处**：
 *       <ol>
 *         <li>{@link #resolveThrust}：**被这一击本身打死**（15 点物理）⇒ 当场收工，
 *             <b>不进等待相</b>（否则 0.8 秒后还会去拉一个尸体，冷却也拖到那时才起算）；</li>
 *         <li>{@link #update()} 每刻先扫一次被命中者：死了/没了 ⇒ 立刻 {@link #finishPullCycle()}；</li>
 *         <li>{@link #beginPull}：0.8 秒到点前再确认一次；</li>
 *         <li>{@link #stepPull} 的**写坐标之前**：全技能唯一的他人位移点，防御性再确认一次
 *             ⇒ <b>任何情况下都不会对尸体写坐标</b>。</li>
 *       </ol>
 *       收工都走 {@code reset()} ⇒ 拉拽连线不再画、状态不再占着"正在进行"。</li>
 * </ol>
 *
 * <h2>★ 音效（2026-10-02 用户指定）</h2>
 * <table border="1">
 *   <tr><th>时机</th><th>表现</th></tr>
 *   <tr><td>蓄力开始</td><td>{@link HunterSound#crossbowLoadHunterPullChannelSound}
 *       = **2 倍速**的弩蓄力音（时长 = 引导 {@value #CHANNEL_TICKS} 刻 = 0.3 秒）</td></tr>
 *   <tr><td>蓄力完成、投出</td><td>{@link HunterSound#tridentThrowHunterPullCastSound}
 *       = **三叉戟投出音** + **屏幕微小缩放**</td></tr>
 *   <tr><td>命中敌人</td><td>{@link HunterSound#tridentHitHunterPullImpactSound}</td></tr>
 *   <tr><td>再次释放加速</td><td>{@link HunterSound#riptideHunterPullAccelerateSound}</td></tr>
 * </table>
 * ★ **屏幕微缩放的实现口径**：按用户建议，给自己上 **{@value #SCREEN_ZOOM_TICKS} 刻**的
 * {@code SLOWNESS}（增幅 {@value #SCREEN_ZOOM_AMPLIFIER}）—— 原版 FOV 随移动速度变化，
 * 一个极短的减速会让画面产生一次**极轻微的回缩**。取 1 刻 = 0.05 秒。
 * <p>为什么不用"更好的方法"：Bukkit API **没有**直接改 FOV 的口子
 * （`setFovMultiplier` 之类不存在；发包改 FOV 属协议层，会与其它插件冲突）。
 * 想调强度只改这两个常量（想更明显就把刻数改 2、增幅改 1）。
 *
 * <h2>★ 冷却语义（2026-10-02 用户明确，与其它技能不同）</h2>
 * 需求原话："二技能在命中敌人后**不进入 CD**，而是可以再次释放**加速拖拽**，
 * 或者就是正常速度慢慢拖拽，**只有敌人到位后才进入 CD**"。
 * <table border="1">
 *   <tr><th>情形</th><th>是否进 CD</th></tr>
 *   <tr><td>穿刺**没打中**任何人</td><td>**进 CD** —— 没有可拉的对象，不存在"等它到位"</td></tr>
 *   <tr><td>穿刺**打中**了</td><td>**不进 CD**（0.8 秒等待 + 拉回全程都可再次释放）</td></tr>
 *   <tr><td>拉回结束（到位 / 逃脱 / 目标死亡 / 2.5 秒超时）</td><td>**进 CD**</td></tr>
 * </table>
 * ⇒ 因此"再次释放"这个入口只在 {@link Phase#DELAY} 与 {@link Phase#PULL} 两相可达，
 * 效果是**加速**而不是重新引导（见 {@link #acceleratePull}）。
 *
 * <h2>★ 加速拖拽的量化口径（2026-10-02 用户明确）</h2>
 * 基础速度 = {@value #PULL_SPEED_PER_TICK} 格/刻（**每秒 10 格**，需求原话"常规下为10格每秒"）。
 * 每次"再次释放"给速度加 {@value #PULL_ACCELERATION_STEP_PER_TICK} 格/刻（每秒 10 格），
 * 上限 {@value #PULL_SPEED_MAX_PER_TICK} 格/刻（每秒 20 格）
 * ⇒ **按一次就封顶**（0.5 → 1.0；需求只给了"再次释放"这一档，连按不会更快）。
 * 换算见纯函数 {@link #acceleratedPullSpeed(int)}。
 *
 * <h2>★ 口径申报："脱离控制"的判据 = 目标自己产生了我们没施加的位移</h2>
 * 插件**没有**"敌人使用了位移技能"这样的事件可监听 ⇒ 只能从**位置**反推：
 * 每刻我们把自己写到的位置记成 {@code expected}，下一可见刻读目标的实际位置，
 * 若实际位置离 {@code expected} 超过 {@value #ESCAPE_DISTANCE} 格，就判定"它自己动过了"⇒ 放它走。
 *
 * <p>阈值取 {@value #ESCAPE_DISTANCE} 格的依据（可复算）：普通走路 ≈ 4.3 格/秒 = 0.215 格/刻，
 * 自由落体 1 刻约 0.08 格，而技能位移（突进 / 传送）普遍是一次 3 格以上 ——
 * 取 2.5 格可以把"自己走两步"与"用技能位移"区分开，又留足落地抖动余量。
 * <p>判据抽成纯函数 {@link #pullEscaped}（离线可穷举）。
 *
 * <h2>★ 口径申报：拉回有 **2.5 秒**上限</h2>
 * 需求原话（2026-10-02）：「**最多只能拉着敌人 2.5S**」⇒ 上限 = {@value #PULL_MAX_TICKS} 刻。
 * <p>这个上限同时兜住一种病态情形：「以每秒 N 格向锚点位移」在没有上限时，若目标被地形卡住
 * （锚点在墙里 / 目标被方块挡）会**永远**拽下去、技能也**永远不进 CD**。
 *
 * <h2>★ 口径申报：锚点取"视角水平前方 {@value #PULL_ANCHOR_DISTANCE} 格、高度取施法者脚底"</h2>
 * "拉回到角色视角前方"：本实现取施法者**当前位置** + **视线水平分量归一** ×
 * {@value #PULL_ANCHOR_DISTANCE} 格，**Y 取施法者脚底高度**（不跟着抬头抬到天上，
 * 否则被拽的人会悬空）。锚点每刻重算 ⇒ 施法者转身 / 移动时目标会被继续拽向新的锚点。
 * <p>目标朝向（yaw / pitch）**不强行改**：只挪位置，避免"被拽的人视角被抢"。
 * <p>★ 引导只是"0.3 秒后出伤"，**不含定身**（需求未要求不可移动 / 不可打断）。
 */
public class HunterPullSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "hunter_skill_pullback";

    /** 冷却（刻）—— 需求原话"CD-6"（6 秒），但**只在拉回结束后**才起算（见类注释）。 */
    private static final int COOLDOWN_TICKS = 120;

    /** 耗能：需求未提 ⇒ 0。 */
    private static final int ENERGY_COST = 0;

    /** 引导时长（刻）—— 需求原话"引导0.3秒"（6 刻）。 */
    private static final int CHANNEL_TICKS = 6;

    /** 穿刺射程（格）—— 需求原话"前方[12格]"。 */
    public static final double THRUST_RANGE = 12d;

    /** 穿刺的侧向容差（格）—— 长廊半宽（≈3 格宽）。 */
    public static final double THRUST_LATERAL = 1.6d;

    /** 穿刺的高差容差（格）—— **脚底对脚底**。 */
    public static final double THRUST_VERTICAL = 2.5d;

    /** 穿刺伤害 —— 需求原话"15点物理伤害"。 */
    private static final double THRUST_DAMAGE = 15d;

    /** 命中后等多久开始拉（刻）—— 需求原话"0.8秒后"（16 刻）。 */
    private static final int PULL_DELAY_TICKS = 16;

    /** 拉回基础速度（格/刻）—— 需求原话"常规下为10格每秒"（10 / 20 = 0.5）。 */
    public static final double PULL_SPEED_PER_TICK = 10d / 20d;

    /**
     * **每次"再次释放"给速度加的增量（格/刻）** —— 需求原话"再次释放为20格每秒"。
     *
     * <p>基础 {@value #PULL_SPEED_PER_TICK}（10 格/秒）+ 本增量 {@code 0.5}（10 格/秒）
     * = {@value #PULL_SPEED_MAX_PER_TICK}（20 格/秒）⇒ **按一次即到上限**（需求只说了"再次释放"这一档）。
     * <p>连按多次不会再快（上限封顶）—— 计数器只为不让它无限增长。
     */
    public static final double PULL_ACCELERATION_STEP_PER_TICK = 10d / 20d;

    /** 拉回速度上限（格/刻）—— 每秒 20 格。 */
    public static final double PULL_SPEED_MAX_PER_TICK = 20d / 20d;

    /** 加速次数的计数上限（速度早已封顶；设上限只为不让计数器无限增长）。 */
    private static final int MAX_PULL_ACCELERATION = 8;

    /** 锚点离施法者的水平距离（格）—— "视角前方"的落点。 */
    public static final double PULL_ANCHOR_DISTANCE = 2.5d;

    /** 认为"已经拉到锚点"的距离（格）。 */
    private static final double PULL_ARRIVE_DISTANCE = 1.0d;

    /** 拉回的时长上限（刻）—— **2.5 秒**（需求原话"最多只能拉着敌人2.5S"；50 刻）。 */
    public static final int PULL_MAX_TICKS = 50;

    /** 脱离控制的位移阈值（格）—— 见类注释的判据推导。 */
    public static final double ESCAPE_DISTANCE = 2.5d;

    /** "屏幕微小缩放"的持续刻数 —— 0.05 秒 = 1 刻（需求原话）。 */
    public static final int SCREEN_ZOOM_TICKS = 1;

    /** "屏幕微小缩放"的缓慢增幅（0 = 缓慢 I，最轻的一档）。 */
    public static final int SCREEN_ZOOM_AMPLIFIER = 0;

    /** 状态机相位。 */
    private enum Phase {
        /** 空闲。 */
        IDLE,
        /** 引导中（0.3 秒）。 */
        CHANNEL,
        /** 已命中，等待 0.8 秒后再拉（**此相不在 CD 里**，可按技能加速）。 */
        DELAY,
        /** 正在拉（**此相不在 CD 里**，可按技能加速）。 */
        PULL
    }

    private VitalsComponent vitals;
    private BuffComponent buff;
    private SanTEComponent sante;
    private HunterEvolutionPassive evolution;
    private HunterStealthSkill stealth;

    // ───────── 状态机字段 ─────────

    private Phase phase = Phase.IDLE;

    /** 引导剩余刻。 */
    private int channelLeft;

    /** 拉回前剩余等待刻。 */
    private int delayLeft;

    /** 拉回剩余刻（上限兜底）。 */
    private int pullLeft;

    /** 被拉目标的 UUID。 */
    private UUID pullTargetId;

    /**
     * 我们上一次把目标写到哪儿。
     * <p>下一刻用它做**脱离判据**的基准（见 {@link #pullEscaped}）。
     */
    private Location expected;

    /** 本轮"再次释放"累计了几次（只在 {@link #reset()} 里归零 ⇒ 跨 DELAY / PULL 两相保持）。 */
    private int pullAcceleration;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HunterPullSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符：垂泪藤（需求指定）+ 逐条对应需求的说明。 */
    public static final class Specification extends Skill.Specification<HunterPullSkill> {

        public Specification() {
            super(Component.text("拉回"),
                    List.of(
                            Component.text("引导 0.3 秒后向前方 12 格穿刺，造成 15 点物理伤害"),
                            Component.text("一次最多命中一名敌人"),
                            Component.text("0.8 秒后把被命中者以每秒 8 格拽向视角前方"),
                            Component.text("命中后不进入冷却：可再次释放加速拖拽，敌人到位后才进冷却"),
                            Component.text("对方可用一次技能位移脱离控制；冷却 6 秒")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.WEEPING_VINES);
            requires(VitalsComponent.class).requires(FactionComponent.class);
            requires(BuffComponent.class);
            requires(HunterEvolutionPassive.class);
            requires(HunterStealthSkill.class);
        }

        @Override
        public HunterPullSkill create(String id, ComponentServicesPort services) {
            return new HunterPullSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        evolution = svc().components().get(HunterEvolutionPassive.class);
        stealth = svc().components().get(HunterStealthSkill.class);
    }

    /** 停止生效：清空状态机（角色被清 / 死亡时不留下"拉一半"的状态），**不起冷却**。 */
    @Override
    public void stop() {
        reset();
        vitals = null;
        buff = null;
        sante = null;
        evolution = null;
        stealth = null;
    }

    // ───────── 施放 ─────────

    /**
     * 施放入口，两种语义（★ 需求："命中后不进入 CD，可以再次释放加速拖拽"）：
     * <ol>
     *   <li><b>空闲</b> ⇒ 进入引导相（播蓄力音效）；</li>
     *   <li><b>已命中、正在等待/拖拽</b>（{@link Phase#DELAY} / {@link Phase#PULL}）
     *       ⇒ **加速拖拽**（不重新引导、不重置状态机）；</li>
     *   <li><b>引导中</b>（{@link Phase#CHANNEL}）⇒ 忽略（避免把引导重置掉）。</li>
     * </ol>
     */
    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            return;
        }

        if (phase == Phase.DELAY || phase == Phase.PULL) {
            acceleratePull(owner);
            return;
        }
        if (phase != Phase.IDLE) {
            return;
        }
        if (isCoolingDown() || !canUse()) {
            return;
        }

        //"使用技能" ⇒ 中断遁形（需求："若使用技能或攻击，将中断技能"）
        if (stealth != null) {
            stealth.breakStealth();
        }

        phase = Phase.CHANNEL;
        channelLeft = CHANNEL_TICKS;

        //"2技能引导时给一次类似的蓄力音效"
        World world = owner.getWorld();
        if (world != null) {
            HunterSound.crossbowLoadHunterPullChannelSound(world, owner.getLocation());
        }
    }

    // ───────── 每 tick：推进状态机 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            if (phase != Phase.IDLE) {
                //技能已经放出去了 ⇒ 照常进冷却（避免"死了就白嫖一次拉回"）
                finishPullCycle();
            }
            return;
        }

        //★ 需求："**被拉回命中的敌人死亡后将清除身上的拉回命中拖拽效果**"
        //  ⇒ 每刻先查一次被命中者：只要它没了（死亡 / 掉线 / 换世界）就**立刻**收工。
        //    reset() 会清掉 pullTargetId / expected / 加速计数 ⇒ 尸身上不再画拉拽连线，
        //    也不会继续占着"正在进行"的状态。
        //  （改前只由 beginPull / stepPull 兜底，最迟晚一刻才收工；DELAY 相更是一直不查。）
        if (pullTargetId != null && pullTarget() == null) {
            finishPullCycle();
            return;
        }

        switch (phase) {
            case CHANNEL -> {
                channelLeft--;
                if (channelLeft <= 0) {
                    resolveThrust(owner);
                }
            }
            case DELAY -> {
                delayLeft--;
                if (delayLeft <= 0) {
                    beginPull();
                }
            }
            case PULL -> stepPull(owner);
            case IDLE -> {
                //什么都不做
            }
        }
    }

    // ───────── ① 穿刺结算 ─────────

    /**
     * 引导结束：播投出音效 + 屏幕微缩放，找**一名**敌人打穿刺。
     *
     * <p>★ 冷却语义分叉（需求）：
     * <ul>
     *   <li><b>打中</b> ⇒ **不进冷却**，转入 0.8 秒等待，等拉回结束才进；</li>
     *   <li><b>没打中</b> ⇒ 立刻进冷却（没有可拉的对象）。</li>
     * </ul>
     */
    private void resolveThrust(Player owner) {
        phase = Phase.IDLE;

        //投出表现：三叉戟投出音 + 屏幕微小缩放（0.05 秒缓慢）
        World world = owner.getWorld();
        if (world != null) {
            HunterSound.tridentThrowHunterPullCastSound(world, owner.getLocation());
        }
        applyScreenZoom(owner);

        Player target = findThrustTarget(owner);

        //★ 需求："就算打空拉回，也会出现紫色的技能投出路径效果" ⇒ 路径**无条件**画：
        //  有目标 ⇒ 画到目标胸口；没目标（空放）⇒ 从眼睛沿水平前向画满整段射程。
        //  （改前这段在"打中"分支里 ⇒ 空放时什么粒子都没有）
        if (world != null) {
            Location from = owner.getEyeLocation();
            Location to = target != null
                    ? target.getLocation().clone().add(0d, 1.0d, 0d)
                    : thrustEndpoint(owner);
            //"用类似紫色的粒子来模拟攻击"
            HunterVfx.purpleThrustHunterPullCast(world, from, to);
        }

        if (target == null || vitals == null) {
            //空放 ⇒ 进冷却（路径已经在上面画过了）
            finishPullCycle();
            return;
        }

        vitals.physicalDamage(target, owner, THRUST_DAMAGE);

        if (world != null) {
            Location chest = target.getLocation().clone().add(0d, 1.0d, 0d);
            //命中者身上：暴击粒子 + 单个爆炸粒子 + 紫色菱形边框
            HunterVfx.critBurstHunterPullHit(world, chest);
            HunterVfx.purpleDiamondHunterPullHit(world, target.getLocation());
            //"命中敌人后也加个命中音效"
            HunterSound.tridentHitHunterPullImpactSound(world, chest);
        }

        //★ 需求："**被拉回命中的敌人，死亡后不再会被继续控制拖拽**"
        //  ⇒ 如果这一击**本身就把它打死了**，就别进等待相了：否则 0.8 秒后还会去拉一个尸体，
        //    而且冷却要拖到那时才起算（"白占着正在进行"）。
        //  ⇒ 当场收工（`finishPullCycle` 会清状态并立刻起冷却）。
        if (!isDraggable(target.isOnline(), target.isDead(), target.getHealth())) {
            finishPullCycle();
            return;
        }

        //★ 命中且目标仍然活着 ⇒ 不进冷却，转入等待相
        pullTargetId = target.getUniqueId();
        phase = Phase.DELAY;
        delayLeft = PULL_DELAY_TICKS;
    }

    /**
     * **空放时的投出路径终点**：从眼睛沿**水平前向**推满 {@value #THRUST_RANGE} 格。
     *
     * <p>高度与眼睛持平、方向取水平前向（不是含俯仰的视线）—— 与命中判据
     * {@link #insideThrustCorridor} 的"水平长廊"同口径，让"看到的路径"与"能打中的范围"一致。
     * 朝向退化（视线与身体朝向都近乎垂直）时退回起点 ⇒ 路径退化为一个点，不抛。
     */
    private static Location thrustEndpoint(Player owner) {
        Location from = owner.getEyeLocation();
        Vector axis = horizontalAxis(owner);
        if (axis == null) {
            return from.clone();
        }
        return from.clone().add(axis.clone().multiply(THRUST_RANGE));
    }

    /**
     * 前方 12 格**长廊**内最近的一名敌人（最多一名 —— 需求原话"一次最多攻击一名敌人"）。
     *
     * <p>几何 = 工程既有口径的「长廊」（不用射线：射线是一条线，准星稍偏即落空）：
     * 侧向用**水平叉积**（天然不含竖直分量），高差**脚底对脚底**单独设容差。
     * 判据抽成纯函数 {@link #insideThrustCorridor}。
     */
    private Player findThrustTarget(Player owner) {
        Location center = owner.getLocation();
        Vector axis = horizontalAxis(owner);
        if (axis == null) {
            return null;
        }
        Player best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Player candidate : center.getNearbyPlayers(THRUST_RANGE)) {
            if (candidate == null || candidate.equals(owner)) {
                continue;
            }
            if (!candidate.isOnline() || candidate.isDead() || candidate.getHealth() <= 0d) {
                continue;
            }
            Location at = candidate.getLocation();
            if (at.getWorld() == null || center.getWorld() == null || !at.getWorld().equals(center.getWorld())) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            double dx = at.getX() - center.getX();
            double dz = at.getZ() - center.getZ();
            double dy = at.getY() - center.getY();
            if (!insideThrustCorridor(dx, dz, dy, axis.getX(), axis.getZ())) {
                continue;
            }
            double along = dx * axis.getX() + dz * axis.getZ();
            if (along < bestAlong) {
                bestAlong = along;
                best = candidate;
            }
        }
        return best;
    }

    // ───────── ② 拉回 ─────────

    /** 0.8 秒到点：把目标记为待拉对象并进入拉回相。 */
    private void beginPull() {
        Player target = pullTarget();
        if (target == null) {
            //等待期间目标没了（死亡 / 掉线）⇒ 本轮收工并进冷却
            finishPullCycle();
            return;
        }
        phase = Phase.PULL;
        pullLeft = PULL_MAX_TICKS;
        expected = target.getLocation().clone();
    }

    /**
     * 拉回一拍：先判**脱离**，再算锚点、判**抵达**，最后把目标朝锚点挪
     * {@link #acceleratedPullSpeed 当前速度}格。
     *
     * <p>四种收工情形（全部进冷却）：目标没了 / 逃脱 / 到位 / 超时。
     */
    private void stepPull(Player owner) {
        Player target = pullTarget();
        if (target == null) {
            finishPullCycle();
            return;
        }

        Location actual = target.getLocation();

        //① 脱离判据：目标自己产生了我们没施加的位移 ⇒ 放它走
        if (expected != null && pullEscaped(
                expected.getX(), expected.getY(), expected.getZ(),
                actual.getX(), actual.getY(), actual.getZ(),
                ESCAPE_DISTANCE)) {
            finishPullCycle();
            return;
        }

        //② 锚点 = 视角水平前方（每刻重算 ⇒ 施法者转身会把目标拽向新方向）
        Location anchor = pullAnchor(owner);

        //③ 已到锚点 ⇒ 正常收工（"只有敌人到位后才进入 CD"）
        if (actual.getWorld() == null || anchor.getWorld() == null
                || !actual.getWorld().equals(anchor.getWorld())
                || actual.distance(anchor) <= PULL_ARRIVE_DISTANCE) {
            finishPullCycle();
            return;
        }

        //④ 朝锚点挪一格步长（保持目标原有朝向，不强行给视角）
        Vector direction = anchor.toVector().subtract(actual.toVector());
        if (direction.lengthSquared() < 1.0E-6d) {
            finishPullCycle();
            return;
        }
        direction.normalize().multiply(acceleratedPullSpeed(pullAcceleration));
        Location next = actual.clone().add(direction);
        next.setYaw(actual.getYaw());
        next.setPitch(actual.getPitch());

        //★ 防御性第二道（需求："死亡后不再会被继续控制拖拽"）：
        //  本方法开头虽然已经取过一次"活着的目标"，但在**真正写入位置**这一步之前再确认一次 ——
        //  这是全技能**唯一**对别人做位移的地方，宁可我方多读一次血量，也不要对尸体写坐标。
        if (!isDraggable(target.isOnline(), target.isDead(), target.getHealth())) {
            finishPullCycle();
            return;
        }
        target.teleport(next);
        expected = next.clone();

        World world = next.getWorld();
        if (world != null) {
            HunterVfx.pullTetherHunterPullDrag(world, next, anchor);
        }

        //⑤ 超时兜底
        pullLeft--;
        if (pullLeft <= 0) {
            finishPullCycle();
        }
    }

    /**
     * **再次释放 ⇒ 加速拖拽**（需求原话："可以再次释放加速拖拽，或者就是正常速度慢慢拖拽"）。
     *
     * <p>只累加一个计数（速度由 {@link #acceleratedPullSpeed} 换算并封顶），
     * 不重置状态机、不重新引导、不额外消耗冷却 —— 玩家想慢慢拽就**不按**。
     */
    private void acceleratePull(Player owner) {
        if (pullAcceleration < MAX_PULL_ACCELERATION) {
            pullAcceleration++;
        }
        World world = owner.getWorld();
        if (world != null) {
            HunterSound.riptideHunterPullAccelerateSound(world, owner.getLocation());
        }
    }

    /**
     * **给自己一次"屏幕微小缩放"**：按用户建议，用 {@value #SCREEN_ZOOM_TICKS} 刻的
     * {@code SLOWNESS}（增幅 {@value #SCREEN_ZOOM_AMPLIFIER}）实现 —— 原版 FOV 随移动速度变化，
     * 极短减速 ⇒ 画面一次轻微回缩。
     * <p>走 buff 组件那条"只作用自己"的正确口（它是自己身上的效果）。
     */
    private void applyScreenZoom(Player owner) {
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SLOWNESS, SCREEN_ZOOM_TICKS, SCREEN_ZOOM_AMPLIFIER);
        }
    }

    /** 当前待拉 / 正拉的目标；没有 / 已失效时回 {@code null}。 */
    private Player pullTarget() {
        if (pullTargetId == null) {
            return null;
        }
        Player target = org.bukkit.Bukkit.getPlayer(pullTargetId);
        if (target == null) {
            return null;
        }
        //判据抽成纯函数（离线可测）：在线 && 未死 && 有血量
        return isDraggable(target.isOnline(), target.isDead(), target.getHealth()) ? target : null;
    }

    /**
     * **目标此刻还能不能被拖拽**（纯函数 ⇒ 可离线单测）。
     *
     * <p>三条同时成立：<b>在线</b>、<b>未死</b>、<b>血量 &gt; 0</b>。
     * <p>★ 需求：「被拉回命中的敌人，**死亡后不再会被继续控制拖拽**」——
     * 于是这个判据被用在**三处**（都必须是同一口径，漏一处就会拖到尸体）：
     * <ol>
     *   <li>{@link #pullTarget()}（取目标时）；</li>
     *   <li>{@link #update()}（每刻先扫一次被命中者：死了立刻收工）；</li>
     *   <li>{@link #stepPull} 的**写坐标之前**（全技能唯一的他人位移点，防御性再确认一次）。</li>
     * </ol>
     * <p>另：{@link #resolveThrust} 里"被这一击打死就不进等待相"用的是同一个判据。
     *
     * @param online 目标是否在线
     * @param dead   目标是否处于死亡状态
     * @param health 目标当前血量
     */
    static boolean isDraggable(boolean online, boolean dead, double health) {
        return online && !dead && health > 0d;
    }

    /** 锚点 = 施法者当前位置 + 视线水平前方 {@value #PULL_ANCHOR_DISTANCE} 格，高度取施法者脚底。 */
    private static Location pullAnchor(Player owner) {
        Location base = owner.getLocation();
        Vector axis = horizontalAxis(owner);
        if (axis == null) {
            return base.clone();
        }
        return base.clone().add(axis.clone().multiply(PULL_ANCHOR_DISTANCE));
    }

    /** 水平朝向单位向量；视线近垂直时退回身体朝向；都退化则回 {@code null}。 */
    private static Vector horizontalAxis(Player owner) {
        Vector view = owner.getLocation().getDirection();
        view.setY(0d);
        if (view.lengthSquared() < 1.0E-6d) {
            view = owner.getFacing().getDirection();
            view.setY(0d);
        }
        if (view.lengthSquared() < 1.0E-6d) {
            return null;
        }
        return view.normalize();
    }

    /** **本轮收工**：清状态机并起冷却（拉回"到位后才进 CD"的唯一落点）。 */
    private void finishPullCycle() {
        reset();
        startCooldown(adjustedCooldownTicks());
    }

    /** 回到空闲并清掉全部状态机字段（含加速计数）。 */
    private void reset() {
        phase = Phase.IDLE;
        channelLeft = 0;
        delayLeft = 0;
        pullLeft = 0;
        pullTargetId = null;
        expected = null;
        pullAcceleration = 0;
    }

    /** 本次应起的冷却（3 级进化起减 1 秒）。 */
    private int adjustedCooldownTicks() {
        return evolution == null ? getCooldownTicks() : evolution.adjustedSkillCooldownTicks(getCooldownTicks());
    }

    /** 闸门：被眩晕 / 沉默时不可用。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /** 0 耗能 ⇒ 不参与能量维度。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **加速后的拉回速度**（格/刻；纯函数 ⇒ 可离线穷举）。
     *
     * <p>{@code 0} 次加速 = 基础 {@value #PULL_SPEED_PER_TICK}（每秒 8 格，"正常速度慢慢拖拽"）；
     * 每次 +{@value #PULL_ACCELERATION_STEP_PER_TICK}（每秒 4 格）；封顶
     * {@value #PULL_SPEED_MAX_PER_TICK}（每秒 20 格）。负数按 0 处理（不抛）。
     *
     * @param accelerationCount 本轮累计的"再次释放"次数
     */
    public static double acceleratedPullSpeed(int accelerationCount) {
        if (accelerationCount <= 0) {
            return PULL_SPEED_PER_TICK;
        }
        double speed = PULL_SPEED_PER_TICK + accelerationCount * PULL_ACCELERATION_STEP_PER_TICK;
        return Math.min(PULL_SPEED_MAX_PER_TICK, speed);
    }

    /**
     * **前方"长廊"命中判据**（纯函数 ⇒ 可离线穷举）。
     *
     * <p>三条同时成立（全部以**水平面 + 脚底**为基准，避免"眼睛 vs 脚底差 1.62 格"污染侧向）：
     * <ol>
     *   <li>前向：{@code 0 <= dx*ax + dz*az <= }{@value #THRUST_RANGE}；</li>
     *   <li>侧向：{@code |dx*az - dz*ax| <= }{@value #THRUST_LATERAL}（**水平叉积**，天然不含竖直分量）；</li>
     *   <li>高差：{@code |dy| <= }{@value #THRUST_VERTICAL}（脚底对脚底）。</li>
     * </ol>
     *
     * @param dx    目标 − 施法者的水平 X 差（脚底对脚底）
     * @param dz    目标 − 施法者的水平 Z 差
     * @param dy    目标 − 施法者的竖直 Y 差
     * @param axisX 视线水平单位向量的 X 分量（调用方保证已归一）
     * @param axisZ 视线水平单位向量的 Z 分量
     */
    public static boolean insideThrustCorridor(double dx, double dz, double dy, double axisX, double axisZ) {
        double along = dx * axisX + dz * axisZ;
        if (along < 0d || along > THRUST_RANGE) {
            return false;
        }
        double lateral = Math.abs(dx * axisZ - dz * axisX);
        if (lateral > THRUST_LATERAL) {
            return false;
        }
        return Math.abs(dy) <= THRUST_VERTICAL;
    }

    /**
     * **目标是否已脱离控制**（纯函数 ⇒ 可离线穷举）。
     *
     * <p>判据 = 目标**实际位置**与"我们上一次把它写到哪儿"之间的距离是否超过 {@code threshold}。
     * 传 {@code threshold <= 0}（或 NaN）时回 {@code false}（宁可不放走，也不因参数错误误判逃脱）。
     *
     * @param expectedX 我们上一次写入的坐标（拉回时由本组件记录）
     * @param actualX   这一刻读到的实际坐标
     * @param threshold 脱离阈值（格）；需求口径下取 {@link #ESCAPE_DISTANCE}
     */
    public static boolean pullEscaped(double expectedX, double expectedY, double expectedZ,
                                      double actualX, double actualY, double actualZ,
                                      double threshold) {
        if (!(threshold > 0d)) {
            return false;
        }
        double dx = actualX - expectedX;
        double dy = actualY - expectedY;
        double dz = actualZ - expectedZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) > threshold;
    }
}
