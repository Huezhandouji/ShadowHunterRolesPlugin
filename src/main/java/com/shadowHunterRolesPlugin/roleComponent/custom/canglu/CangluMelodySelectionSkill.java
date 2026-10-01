package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.ParticleUtil;
import com.shadowHunterRolesPlugin.core.util.SoundUtil;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.UUID;
import java.util.List;

/**
 * **旋律选取-青金石**（苍鹭）：向前发射一支抓钩。
 *
 * <h2>玩法</h2>
 * <ul>
 *   <li>右键 = 发射：抓钩沿**释放那一刻**的视线方向直线飞出（飞行途中玩家怎么转头、怎么走都不影响它），
 *       对命中的敌人造成 {@link #HOOK_DAMAGE} 点物理伤害；</li>
 *   <li>抓钩命中敌人 / 撞到阻挡实体的方块后，把苍鹭拉过去，
 *       整段行进期间获得抗性 {@link #RESISTANCE_AMPLIFIER}（原版增幅，4 级 = 抗性 IV）；</li>
 *   <li><b>技能完全结束后才开始冷却</b>（{@link #startCooldown()} 只在 {@link #finishGrapnel()} 里调一次），
 *       与「罪恶的辩护」同一条口径；</li>
 *   <li><b>可储存 2 个</b>：{@link Charges#MAX_CHARGE}，用掉后每 {@link #CHARGE_RESTORE_TICKS} 刻补 1 个
 *       （〈储存冷却 15 秒〉）；**手里还有 1 个就能放** —— 储存冷却只决定"什么时候补满"，
 *       拦施放的只有 {@link #isCoolingDown()}（它只看"手里还有没有"），
 *       掷出时消耗 {@link #ENERGY_COST} 点能量；</li>
 *   <li>物品名后缀实时显示剩余存量（形如 {@code (2/2)}），与澜冰左轮的弹夹显示同一形态。</li>
 * </ul>
 *
 * <h2>连线的粒子为什么"稀"就是"散得快"</h2>
 * 连线用 {@code ParticleUtil} 的画线工具撒 {@code END_ROD}。该粒子在服务端是
 * {@code SimpleParticleType}（没有可传的数据字段），客户端寿命固定、
 * 而画线工具走的是无 data、无速度的那条 {@code spawnParticle} 重载
 * ⇒ **单颗粒子的存活时长在插件侧调不了**。
 * 因此「线消失得更快」只能靠**减少同时在世的粒子数**来达成：
 * 珠子间距 {@link #LINE_PARTICLE_STEP} 与拉拽段的重画间隔 {@link #LINE_REDRAW_INTERVAL_TICKS}
 * 是这件事的两个旋钮（飞行段每刻都要重画，否则连线会脱节）。
 * <p>连线靠自己那一端的落点 = **腰部**（碰撞箱中心，见 {@link #waistLocation(Player)}），不是眼睛；
 * 而且它是**每帧实时取的当前腰部** —— 玩家走动时线的一端跟着身体走，这就是「抓钩头与身体的连线」。
 *
 * <h2>抓钩头为什么是 ItemDisplay 而不是 Marker</h2>
 * 需求指定「抓勾头 = 三叉戟展示实体」：{@link ItemDisplay} 无碰撞箱、不参与实体伤害，
 * 因此命中判定与伤害完全由本组件掌握；又能在世界里看见一把三叉戟飞出去。
 * <p>它是真实实体 ⇒ 实例销毁时必须清掉（见 {@link #stop()}），否则会留在世界里成为实体泄漏。
 *
 * <h2>为什么不用 TaskComponent</h2>
 * 飞行与拉拽都是「每刻推一小步」的连续状态机，组件自己的 {@link #update()} 就是 20 Hz 节拍
 * （旋转组件的容器每 tick 广播一次），因此不需要额外的周期任务、也不需要维护任务句柄。
 *
 * <h2>数值都是常量（要调只改这一处）</h2>
 * 见下方 {@code HOOK_DAMAGE} … {@code RESISTANCE_BUFFER_TICKS}。
 */
public class CangluMelodySelectionSkill extends Skill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "cangluMelodySelectionSkill";

    // ───────── 数值（唯一修改点）─────────

    /** 声明冷却（tick）：4 刻。真实启动时机 = 抓钩完全结束之后（不是施放那一刻）。 */
    private static final int COOLDOWN_TICKS = 40;
    /** 单次施放的能量消耗。 */
    private static final int ENERGY_COST = 4;
    /** 存量恢复周期（tick）：「储存冷却 15 秒」；存量上限与就绪判据住在 {@link Charges}。 */
    private static final int CHARGE_RESTORE_TICKS = 300;
    /** 抓钩命中的物理伤害（「对命中的敌人造成 12 点物理伤害」）。 */
    private static final int HOOK_DAMAGE = 12;
    /** 抓钩每刻前进的步数（步长 1 格 ⇒ 每刻最多 2 格）。 */
    private static final int FLIGHT_STEPS_PER_TICK = 2;
    /** 抓钩最大飞行距离（格）；超过即视为「没抓到东西」并收线。 */
    private static final double MAX_FLIGHT_DISTANCE = 48d;
    /** 命中判定半径（格）：圆心距小于它即算命中。 */
    private static final double HOOK_HIT_RADIUS = 0.85d;
    /** 拉拽速度：每刻把苍鹭朝锚点搬多少格（约 24 格/秒，比步行快得多但不是瞬移）。 */
    private static final double PULL_STEP_PER_TICK = 1.2d;
    /** 到锚点多近算「已被拉到位」（格）。 */
    private static final double PULL_ARRIVAL_DISTANCE = 1.2d;
    /** 拉拽的保险丝（tick）：极端地形下不至于无限拉着玩家。 */
    private static final int PULL_TIMEOUT_TICKS = 40;
    /** 行进期间给的抗性增幅（4 ⇒ 抗性 IV）。 */
    private static final int RESISTANCE_AMPLIFIER = 4;
    /** 抗性给多久（tick）：每刻刷新一次，因此只需要「比一刻长」。 */
    private static final int RESISTANCE_DURATION_TICKS = 20;
    /** 收尾时判定「抗性是否还盖在拉拽时间上」的余量（tick）：够短就直接摘掉，不留尾巴。 */
    private static final int RESISTANCE_BUFFER_TICKS = 10;

    // ───────── 抓钩连线的粒子（要调观感只改这两个）─────────

    /**
     * 连线上相邻两颗粒子的间距（格）——**越大 = 粒子越稀**。
     *
     * <p><b>为什么用"变稀"来实现「消失得更快」</b>：粒子在服务端是
     * {@code SimpleParticleType}（无额外数据字段）、客户端寿命固定，
     * 而 {@link ParticleUtil#drawLine} 只走无 data、无速度的那条 {@code spawnParticle} 重载
     * ⇒ **单颗粒子的寿命在插件侧不可调**。
     * 于是「线更快消失」唯一可动的量就是**同时在世的粒子数**：
     * 间距越大，每帧撒下的珠子越少，线散尽所需的时间就越短。
     *
     * <p><b>但间距同时决定"线能不能连起来"</b>：珠子是散点，间距必须小到让视觉上连成线。
     * 因此这个值是**两个要求的折中**：当前取 {@code 0.2d}（密铺优先，保证线看得见）。
     * 调大（例如 0.6）线会退化成断续的亮点串 —— 曾经实测过，那正是"线看着没了"的成因。
     * <p>间距越小珠子越多：一条 48 格满射程的线，间距 0.2 ⇒ 单帧约 240 颗。
     */
    private static final double LINE_PARTICLE_STEP = 0.2d;

    /**
     * 连线的重画间隔（刻）——**越大 = 撒得越少**。
     *
     * <p>只在拉拽段生效：那一段自己几乎不动，连线长度基本不变，
     * 因此 10 Hz 重画和 20 Hz 重画的观感一样，但每帧撒下的粒子数减半。
     * <p>飞行段必须每刻重画（抓钩头自己每刻前进 2 格），否则连线会脱节。
     */
    private static final int LINE_REDRAW_INTERVAL_TICKS = 2;

    // ───────── 命中音效（要调听感只改这一段）─────────

    /** **命中音效**：{@code entity.blaze.shoot}（撞方块与命中敌人都响）。 */
    private static final Sound HIT_SOUND = Sound.ENTITY_BLAZE_SHOOT;
    private static final float HIT_SOUND_VOLUME = 1f;
    private static final float HIT_SOUND_PITCH = 1.2f;

    /**
     * **命中敌人时补的 4 音旋律**（撞方块不响）。
     * <p>音高用**半音相对基音**写（见 {@link SoundUtil.Melody#pitchOf(int)}）：
     * {@code {0, 4, 7, 12}} = 根音—大三度—纯五度—八度，一个明亮的上行琶音，
     * 与"钩中了人"这件事相配；间隔 3 刻（0.15 秒）比澜冰的号角旋律更急促。
     */
    private static final SoundUtil.Melody HIT_MELODY = new SoundUtil.Melody(
            Sound.BLOCK_NOTE_BLOCK_HARP,
            1f,
            0.9d,
            new int[] { 0, 4, 7, 12 },
            3L);

    // ───────── 状态 ─────────

    /** 已结算的存量（0..{@link Charges#MAX_CHARGE}）。 */
    private int charge = Charges.MAX_CHARGE;
    /** 距下一次存量恢复还有多少刻。 */
    private int chargeRestoreTick = CHARGE_RESTORE_TICKS;

    /**
     * 当前在飞的抓钩；{@code null} = 手上没东西（常态）。
     * <p>它同时是「本技能正在施放中」的状态位：非空期间不接第二次发射。
     */
    private Grapnel grapnel;

    /**
     * **本次施放是否已经结算过**（收线 + 进冷却）。
     * <p>存在理由：拉拽到位与拉拽超时是两条独立的收尾路径，而它们可能在同一刻都成立；
     * 用这个位把「一次施放只收尾一次」钉死，冷却也就不会被重复刷新。
     */
    private boolean resolved;

    /**
     * **一滴抗性都还没给出去的那个 tick**（{@link #NO_RESISTANCE} = 没给过）。
     * <p>收尾时用它判定「能不能顺手把抗性摘干净」：只有「最后一次刷新给的抗性到收尾时会自然过期」
     * 才主动移除，否则留着让它自己走完 —— 那条路径不会留下比拉拽更长的尾巴。
     */
    private int firstResistanceTick = NO_RESISTANCE;
    private static final int NO_RESISTANCE = Integer.MIN_VALUE;

    // ───────── 依赖（全部在 start() 里一次查好）─────────

    private BuffComponent buff;
    private EnergyComponent energy;
    private VitalsComponent vitals;
    private HotbarRenderComponent render;

    public CangluMelodySelectionSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<CangluMelodySelectionSkill> {

        public Specification() {
            super(Component.text("旋律选取-青金石"),
                    List.of(Component.text("向前发射抓钩：命中敌人造成12点物理伤害，"
                            + "命中敌人或撞到方块后把苍鹭拉过去，行进期间获得抗性IV。技能完全结束后才开始冷却")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.LAPIS_LAZULI);
            requires(BuffComponent.class).requires(EnergyComponent.class).requires(VitalsComponent.class)
                    .requires(HotbarRenderComponent.class);
        }

        @Override
        public CangluMelodySelectionSkill create(String id, ComponentServicesPort services) {
            return new CangluMelodySelectionSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    protected void onAwake() {
        //本钩子由基类 `awake()` 调用（栏位登记已在基类里完成），不能也不需要调 super.awake()
        //只做不可见的初始化（契约：awake 不得产生玩家可见副作用）
        charge = Charges.MAX_CHARGE;
        chargeRestoreTick = CHARGE_RESTORE_TICKS;
        grapnel = null;
        resolved = false;
        firstResistanceTick = NO_RESISTANCE;
    }

    @Override
    public void start() {
        //契约：依赖字段在 start() 里一次查好（基类不代查）
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /**
     * **实例销毁**：把还在飞的抓钩头（真实实体）移除，并打断未完成的拉拽状态。
     * <p>不调用 {@link #finishGrapnel()}：实例已经死了，不该再写冷却、也不该再补特效。
     */
    @Override
    public void stop() {
        removeGrapnelHead();
        grapnel = null;
        resolved = false;
        firstResistanceTick = NO_RESISTANCE;
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        //闸门：被眩晕 / 沉默时不许发射（与主武器侧口径一致）
        if (!canUse()) {
            return;
        }
        //只接右键：本技能没有 Q / 左键形态
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        //手上已经有一支在飞 ⇒ 不接第二次（收线前它占着这条技能的"正在施放"位）
        if (grapnel != null) {
            return;
        }
        //存量与能量：两样都要有，且只有这里扣（不足则什么都不做）
        if (charge <= 0) {
            return;
        }
        if (energy == null || !energy.tryConsume(getEnergyCost())) {
            return;
        }

        Player self = svc().self().player();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }

        charge--;
        resolved = false;
        firstResistanceTick = NO_RESISTANCE;

        //方向**在此刻锁定**：抓钩头之后一直沿它直线前进，玩家飞行途中转头 / 走动都不影响。
        //  下面把 direction 当成 Grapnel 的不可变字段带着走，全程不再重读玩家朝向（这就是锁定的实现）。
        Location muzzle = self.getEyeLocation();
        Vector direction = muzzle.getDirection().normalize();
        ItemDisplay head = spawnGrapnelHead(muzzle);

        grapnel = new Grapnel(head, direction, 0d, 0, null, null);
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 1f, 1.2f);
        repaint();

        self.getWorld().playSound(muzzle, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1.5f);
    }

    /**
     * **闸门放行？**（基类不查容器，用本组件自己的字段判）。
     * <p>与 {@code CangluBlueIceRevolverSkill} 同一条口径：被眩晕 / 沉默（{@code canCastSkill()}）时不许发射。
     */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    /** **当前能量**：本技能参与能量维度（声明耗能 4），因此读真值。 */
    @Override
    protected int currentEnergy() {
        return energy == null ? 0 : energy.current();
    }

    // ───────── 就绪判定：只看「手里还有没有」 ─────────

    /**
     * **存量这件事里"能被离线钉住"的两样**：上限与就绪判据 —— 纯值（不碰 Bukkit、不读服务集、无副作用）；
     * 恢复周期仍在组件里（{@code CHARGE_RESTORE_TICKS}，它是逐刻节奏，不属于判据）。
     *
     * <h2>为什么单独一个嵌套类（实测约束，别把它合并回外层）</h2>
     * 判据要能被**离线件**钉住（见 {@code CangluMelodySelectionReadinessTest}），
     * 而外层类（本组件）的静态初始化要碰 {@code Sound} 注册表 —— {@link #HIT_SOUND} 与
     * {@link #HIT_MELODY} 是静态常量，无服务端的 JUnit 里那一句就会抛
     * {@code ExceptionInInitializerError: No RegistryAccess implementation found}（实测踩到）。
     * 嵌套类有自己的 class 文件 ⇒ 触碰它**不会**连带初始化外层类 ⇒ 判据在盘上就能跑。
     */
    static final class Charges {

        /** 存量上限（「可储存 2 个」）。 */
        static final int MAX_CHARGE = 2;

        private Charges() {
        }

        /**
         * **就绪判定（纯函数 · 离线可测）**：本组件"现在能不能放" = 手里还有存量 **且** 声明冷却已走完。
         *
         * <p>两个输入各占一维：{@code charge} = 已结算的存量（{@code 0..MAX_CHARGE}），
         * {@code parentCoolingDown} = 父类自持的声明冷却（{@code startCooldown()} 那一路，
         * 起点在 {@code finishGrapnel()}）。抽成静态纯函数的理由与 {@code IconState#of} 同一条：
         * 判据的形状可以离线钉住，不必起服。
         *
         * <h2>为什么是「存量 &gt; 0」而不是「存量已满」</h2>
         * 存量的两件事必须分开：**能不能放**只看手里有没有货，**什么时候补满**只决定下一发何时到手。
         * 曾经把两者合成一条读数（{@code super.isCoolingDown() || charge < MAX_CHARGE}），
         * 于是手里还剩 1 颗、储存冷却尚未走完的那 15 秒里，施放被 {@code SkillListener} 的预检
         * （读的就是 {@link #isCoolingDown()}）整段拦下 ——
         * 现象 =「还有抓钩，却按不出来」（本组件的 {@code onCast} 里那条判断一直是 {@code charge <= 0}，
         * 两者从此同源）。
         *
         * @param charge             当前存量
         * @param parentCoolingDown  父类自持的声明冷却是否未到期
         */
        static boolean readyToCast(int charge, boolean parentCoolingDown) {
            return charge > 0 && !parentCoolingDown;
        }
    }

    /**
     * **就绪读数**：非就绪 = 冷却中（判据本体 = {@link Charges#readyToCast(int, boolean)}）。
     *
     * <p>本方法有两个消费者，两者读的是同一份真值，因此它的语义只能是"现在能不能放"：
     * <ul>
     *   <li>施放预检（{@code SkillListener#cast}）：非就绪 ⇒ 整次施放被丢弃；</li>
     *   <li>图标三态（{@code Skill#buildItem} → {@code IconState#of}）：非就绪 ⇒ 画成冷却态。</li>
     * </ul>
     * 把「存量正在恢复」并进这条读数会让图标与闸门**一起说谎**：明明能放，图标说冷却、点下去没反应。
     * <p>存量恢复期的逐秒刷新（名称里的 {@code ", 12s"}）改由 {@link #tickChargeRestore()}
     * 自己每刻请求重绘 —— 那一维是"外观的刷新节拍"，不是"能不能用"。
     */
    @Override
    public boolean isCoolingDown() {
        return !Charges.readyToCast(charge, super.isCoolingDown());
    }

    // ───────── 每刻：存量恢复 → 飞行 → 拉拽 ─────────

    @Override
    public void update() {
        tickChargeRestore();

        if (grapnel == null) {
            return;
        }
        if (grapnel.phase() == GrapnelPhase.FLYING) {
            tickFlight();
        } else {
            tickPull();
        }
    }

    /**
     * 存量恢复：每 {@link #CHARGE_RESTORE_TICKS} 刻补 1 个，补满则停止计时。
     *
     * <p><b>存量未满期间每刻请求一次重绘</b>：名称里的 {@code ", 12s"} 是逐秒在变的活外观
     * （{@link #chargeRestoreCountdownSuffix()} 向上取整到秒），而帧末 flush 的每 tick 入口条件
     * （本组件已置脏 或 某个占栏位组件正在冷却）在"手里还有货"时不成立 ⇒
     * 恢复期必须由本组件自己置脏（这一维已从 {@link #isCoolingDown()} 里摘出来，理由见
     * ）。
     * <p>代价与摘出去之前**逐字相同**：那时是"存量没满 ⇒ 读数算冷却 ⇒ 框架每 tick 刷一次"，
     * 现在是"存量没满 ⇒ 自己每 tick 置脏一次"，两者的写物品次数一样（脏标记是布尔量、幂等，
     * 写物品仍只在帧末 flush 那一次）。存量满时本方法一次都不置脏 ⇒「空闲 tick 零 setItem」不变。
     */
    private void tickChargeRestore() {
        if (charge >= Charges.MAX_CHARGE) {
            chargeRestoreTick = CHARGE_RESTORE_TICKS;
            return;
        }
        //倒计时秒数每刻都可能变（补满那一颗的那个 tick 也不例外，见下方 charge++）
        repaint();
        if (--chargeRestoreTick > 0) {
            return;
        }
        chargeRestoreTick = CHARGE_RESTORE_TICKS;
        charge++;
    }

    // ───────── 飞行段 ─────────

    /**
     * **推进抓钩**：逐步（步长 1 格）前进，每一步都判定
     * ① 撞到阻挡实体的方块 ⇒ 锚定该处并开始拉拽；
     * ② 命中敌对玩家 ⇒ 造成物理伤害、锚定它并开始拉拽；
     * ③ 飞出 {@link #MAX_FLIGHT_DISTANCE} ⇒ 没抓到东西，收线（不拉拽）。
     *
     * <p>逐步判定的理由：一 tick 走 2 格，若只在终点判定，会穿过薄墙与敌人（隧穿）。
     */
    private void tickFlight() {
        Player self = svc().self().player();
        if (self == null || !self.isOnline() || self.isDead()) {
            finishGrapnel();
            return;
        }
        ItemDisplay head = grapnel.head();
        if (head == null || !head.isValid()) {
            finishGrapnel();
            return;
        }

        for (int step = 0; step < FLIGHT_STEPS_PER_TICK; step++) {
            Location next = head.getLocation().add(grapnel.direction());

            //① 撞到阻挡实体的方块：锚在方块表面外侧，别把玩家往墙里拉
            if (next.getBlock().isSolid()) {
                Location anchor = next.clone().add(grapnel.direction().clone().multiply(-0.5d));
                anchorAt(anchor, null, self);
                return;
            }

            //② 命中敌对玩家
            Player victim = firstHostileAt(next, self);
            if (victim != null) {
                head.teleport(next);
                vitals.physicalDamage(victim, self, HOOK_DAMAGE);
                anchorAt(next, victim.getUniqueId(), self);
                return;
            }

            //③ 推进 + 画出「抓钩头 ↔ 自己」的连线
            head.teleport(next);
            drawLineToSelf();

            double travelled = grapnel.travelledDistance() + 1.0d;
            grapnel = grapnel.withTravelled(travelled);
            if (travelled >= MAX_FLIGHT_DISTANCE) {
                //够远了还没抓到东西：收线，不拉拽
                finishGrapnel();
                return;
            }
        }
    }

    /**
     * **锚定**：记下锚点、切到拉拽阶段，并放一次命中的特效与音效。
     *
     * <p>音效分两层：
     * <ul>
     *   <li>任何一次命中（撞方块 / 命中敌人）都在**玩家位置**响一次
     *       {@code entity.blaze.shoot}（见 {@link #HIT_SOUND}）；</li>
     *   <li>命中**敌人**时再加一段 {@link #HIT_MELODY}（4 个音符）——
     *       "打中了"与"打中人了"听感上要能区分开。</li>
     * </ul>
     * 旋律走 {@link SoundUtil#playMelody}（一次性排完即止，不需要句柄）。
     *
     * @param anchor 锚点位置（命中敌人时 = 命中那一刻它的位置，之后按人取实时位置）
     * @param target 被命中的玩家 UUID；{@code null} = 锚在方块上
     * @param self   施放者（音效的声源；{@code null} 时只放锚点特效）
     */
    private void anchorAt(Location anchor, UUID target, Player self) {
        grapnel = grapnel.anchoredAt(anchor.clone(), target);
        //命中音效按需求放在**玩家位置**（不是锚点位置）：听感上"是我打中了"
        if (self != null && self.isOnline() && !self.isDead()) {
            self.getWorld().playSound(self.getLocation(), HIT_SOUND, HIT_SOUND_VOLUME, HIT_SOUND_PITCH);
            if (target != null) {
                SoundUtil.playMelody(self, HIT_MELODY);
            }
        }
        anchor.getWorld().playSound(anchor, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);
        anchor.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, anchor, 12, 0.2, 0.2, 0.2, 0.01);
    }

    // ───────── 拉拽段 ─────────

    /**
     * **把苍鹭拉向锚点**：每刻朝锚点搬 {@link #PULL_STEP_PER_TICK} 格，同时刷新抗性。
     *
     * <p>锚点是「玩家」时每刻重新读它当时的位置（人会跑）；锚点是「方块」时位置固定。
     * <p>三条收尾路径：到位 / 撞到墙（拉不动了）/ 超时。
     * <p>到位判定用「水平 + 竖直」的整段距离：落到锚点脚下也算到位，不会在墙里反复横跳。
     */
    private void tickPull() {
        Player self = svc().self().player();
        if (self == null || !self.isOnline() || self.isDead()) {
            finishGrapnel();
            return;
        }
        ItemDisplay head = grapnel.head();

        Location destination = resolveAnchorLocation(self);
        if (destination == null) {
            finishGrapnel();
            return;
        }

        //抗性 IV：整段行进期间持续刷新（每刻一次，因此只需要 1 秒的时长）
        applyResistance(self);

        Vector toAnchor = destination.toVector().subtract(self.getLocation().toVector());
        double distance = toAnchor.length();
        if (distance <= PULL_ARRIVAL_DISTANCE) {
            finishGrapnel();
            return;
        }

        Vector step = toAnchor.clone().multiply(Math.min(PULL_STEP_PER_TICK, distance) / distance);
        Location next = self.getLocation().clone().add(step);

        //撞到墙则停：宁可提前结束，也不把玩家塞进方块
        if (!next.getBlock().isPassable() && !next.clone().add(0, 1, 0).getBlock().isPassable()) {
            finishGrapnel();
            return;
        }

        //速度必须清零：直接搬位置之后再叠加原速度会抖动（且掉落速度会让玩家摔下去）
        self.setVelocity(new Vector(0, 0, 0));
        self.setFallDistance(0f);
        self.teleport(next);

        if (head != null && head.isValid()) {
            //抓钩头每刻都要跟着锚点（否则展示实体与目标脱节），但连线**按间隔重画**：
            //拉拽段里线长几乎不变，少撒几帧的观感一样，而线散得更快（见 LINE_REDRAW_INTERVAL_TICKS）
            head.teleport(destination);
            if (grapnel.pullTicks() % LINE_REDRAW_INTERVAL_TICKS == 0) {
                drawLineToSelf();
            }
        }

        int elapsed = grapnel.pullTicks() + 1;
        grapnel = grapnel.withPullTicks(elapsed);
        if (elapsed >= PULL_TIMEOUT_TICKS) {
            finishGrapnel();
        }
    }

    /** 锚点当前位置：命中敌人则跟着那个人，撞墙则固定在方块处；取不到（掉线 / 世界不同）回 {@code null}。 */
    private Location resolveAnchorLocation(Player self) {
        UUID anchor = grapnel.anchorPlayer();
        if (anchor == null) {
            Location block = grapnel.anchorLocation();
            return block == null ? null : block.clone();
        }
        Player target = Bukkit.getPlayer(anchor);
        if (target == null || !target.isOnline() || target.isDead()) {
            return null;
        }
        if (!target.getWorld().equals(self.getWorld())) {
            return null;
        }
        return target.getLocation().clone();
    }

    // ───────── 收尾 / 清理 ─────────

    /**
     * **收线（幂等）**：移除抓钩头 → 摘掉（可摘的）抗性 → **技能完全结束后才启动冷却** → 请求重绘。
     * <p>{@link #resolved} 把「一次施放只收尾一次」钉死：拉拽到位与超时可能在同一刻都成立。
     */
    private void finishGrapnel() {
        if (resolved) {
            return;
        }
        resolved = true;

        removeGrapnelHead();
        grapnel = null;
        clearResistanceIfExpiring();

        //「技能完全结束后开始冷却」：本处是全组件唯一调用 startCooldown() 的地方
        startCooldown();
        repaint();
    }

    /** 摘掉抓钩头（真实实体）：只在实体仍有效时移除。 */
    private void removeGrapnelHead() {
        if (grapnel == null) {
            return;
        }
        ItemDisplay head = grapnel.head();
        if (head != null && head.isValid()) {
            head.remove();
        }
    }

    // ───────── 抗性 ─────────

    /**
     * 刷新抗性 IV，并记下「第一次给的时刻」。
     * <p>那一笔记录只服务一件事：收尾时判断「最后一次刷新给的抗性是不是刚好会自然过期」。
     */
    private void applyResistance(Player self) {
        if (firstResistanceTick == NO_RESISTANCE) {
            firstResistanceTick = Bukkit.getCurrentTick();
        }
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, RESISTANCE_DURATION_TICKS, RESISTANCE_AMPLIFIER);
    }

    /**
     * 收尾时把抗性摘掉 —— **仅当**「拉拽总时长 + 余量 ≤ 一次刷新的时长」。
     * <p>否则说明拉拽比一次刷新还久（说明它早被更晚的一次刷新盖住了），
     * 这时主动移除反而会在行进还没结束的观感里留下空洞；让它自己过期更安全。
     */
    private void clearResistanceIfExpiring() {
        int first = firstResistanceTick;
        firstResistanceTick = NO_RESISTANCE;
        if (first == NO_RESISTANCE) {
            return;
        }
        int pullElapsed = Bukkit.getCurrentTick() - first;
        if (pullElapsed + RESISTANCE_BUFFER_TICKS > RESISTANCE_DURATION_TICKS) {
            return;
        }
        Player self = svc().self().player();
        if (self != null) {
            self.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }

    // ───────── 抓钩头的实体与特效 ─────────

    /** 生成抓钩头：三叉戟展示实体（无碰撞箱、无重力、不参与伤害），并立刻摆到炮口位置。 */
    private ItemDisplay spawnGrapnelHead(Location muzzle) {
        World world = muzzle.getWorld();
        ItemDisplay head = (ItemDisplay) world.spawnEntity(muzzle, EntityType.ITEM_DISPLAY);
        head.setItemStack(new ItemStack(Material.TRIDENT));
        head.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        head.setBillboard(Display.Billboard.FIXED);
        head.setGravity(false);
        head.setInvulnerable(true);
        head.setPersistent(false);
        head.setSilent(true);
        return head;
    }

    /**
     * 画出「抓钩头 ↔ 自己的身体」的连线（需求指定的 {@code ParticleUtil} 画线工具 + 粒子）。
     *
     * <p>两端**都是实时取的**：
     * <ul>
     *   <li>抓钩头 = 展示实体当前位置；</li>
     *   <li>自己 = {@link #waistLocation(Player)}，即**此刻**的腰部（碰撞箱中心）。</li>
     * </ul>
     * 因此玩家走动 / 转身时，线的一端跟着身体走 —— 这是"抓钩头与身体的实时连线"该有的样子。
     *
     * <p><b>曾经写错</b>：把内端钉在"释放那一刻的腰部"（存了一份世界坐标），
     * 于是玩家一走开，线就从抓钩头连回一个空位置 —— 看着像线没了。
     * 锁定的只有**抓钩头的前进方向**（释放朝向），不是连线的端点。
     *
     * <p>珠子间距 = {@link #LINE_PARTICLE_STEP}（越小越密、线越实；见那个常量的 javadoc）。
     */
    private void drawLineToSelf() {
        Player self = svc().self().player();
        ItemDisplay head = grapnel == null ? null : grapnel.head();
        if (self == null || head == null || !head.isValid()) {
            return;
        }
        Location headLocation = head.getLocation();
        ParticleUtil.drawLine(
                headLocation.getWorld(),
                headLocation.toVector(),
                waistLocation(self).toVector(),
                Particle.ELECTRIC_SPARK,
                LINE_PARTICLE_STEP);
    }

    /**
     * **玩家的腰部位置**。
     *
     * <p>取<b>碰撞箱中心</b>（{@code getBoundingBox().getCenter()}）：站立时正落在身高一半
     * （约 0.9 格，即腰），且用包围盒算而不是写死偏移量 ——
     * 潜行（身高降到 1.5）、坐船、骑乘时它会跟着变，写死 {@code +0.9} 在那些姿态下会偏。
     */
    private Location waistLocation(Player self) {
        BoundingBox box = self.getBoundingBox();
        return new Location(self.getWorld(), box.getCenterX(), box.getCenterY(), box.getCenterZ());
    }

    // ───────── 查敌 ─────────

    /** 该位置附近最近的敌对玩家（不含自己；无则 {@code null}）。 */
    private Player firstHostileAt(Location location, Player self) {
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : location.getWorld().getNearbyEntities(
                BoundingBox.of(location, HOOK_HIT_RADIUS, HOOK_HIT_RADIUS, HOOK_HIT_RADIUS))) {
            if (!(entity instanceof Player candidate) || candidate.equals(self) || candidate.isDead()) {
                continue;
            }
            if (!svc().roleInfo().isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(location);
            if (distance < best) {
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    // ───────── 物品外观（在基类三态画法之上加"剩余存量"）─────────

    /**
     * **物品增强**：先取基类的完整画法（三态材质 · 名称着色 · 冷却 {@code x.xs} 倒计时 · 状态行 · lore ·
     * 识别键 PDC），再在名称末尾追加剩余存量，以及**存量恢复倒计时**。
     *
     * <p>不重写整套三态画法：那套逻辑（含写识别键这一步）是冻结面，
     * 复制一份会立刻产生「两处实现漂移」，因此这里只做取回 + 追加后缀，其余原样保留。
     *
     * <p>显示形态（括号里是两件事：{@code 已有/上限} 与 {@code 下一颗还要几秒}）：
     * <ul>
     *   <li>{@code 旋律选取-青金石 (2/2)} —— 存量满，不显示秒数；</li>
     *   <li>{@code 旋律选取-青金石 (1/2, 12s)} —— 少一颗、正在储存，秒数逐秒递减；</li>
     *   <li>{@code 旋律选取-青金石 (0/2, 3s)} —— 用光，整段变红（一眼看出还在等存量）。</li>
     * </ul>
     * <p>秒数**只在缺存量时出现**：满存量时没有"正在恢复"这回事，写个 {@code 15s} 只会误导。
     * <p>逐秒递减依赖每刻重绘，两段各有一个来源：**声明冷却期**由框架的帧末入口条件刷
     * （{@code isCoolingDown()} 为真 ⇒ 每 tick 至少刷一次），**存量恢复期**由
     * {@link #tickChargeRestore()} 每刻请求一次重绘（那时本组件并不算"冷却中"，因此不再靠框架那一条）。
     *
     * <p>**抓钩在飞 / 正在拉拽期间给本物品加附魔光效**（{@link #isGrapnelOut()}）——
     * 与「罪恶的辩护」生效期、「罪棘缠」引导期同一形态：
     * 只补一个 {@code setEnchantmentGlintOverride(true)}，不加任何真实附魔（不多词条、不改数值）。
     * 光效的开关时机 = 发射时 {@code repaint()}、收线时 {@link #finishGrapnel()} 里那次 {@code repaint()}。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        //抓钩出手中 ⇒ 附魔光效（"这个技能正在使用中"在快捷栏上看得见）
        if (isGrapnelOut()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        Component baseName = meta.displayName() != null ? meta.displayName() : getDisplayName();
        NamedTextColor chargeColor = charge <= 0 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        meta.displayName(baseName.append(Component
                .text(" (" + charge + "/" + Charges.MAX_CHARGE + chargeRestoreCountdownSuffix() + ")")
                .color(chargeColor)));
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 存量恢复倒计时的显示片段：缺存量时回 {@code ", 12s"}；存量已满时回空串（不显示）。
     *
     * <p>秒数取**向上取整**：{@code chargeRestoreTick} 是"还剩多少刻"，剩 1 刻时向上取整得
     * {@code 1s}（而不是 {@code 0s}）—— 显示 {@code 0s} 会让玩家以为下一 tick 就有，
     * 但那时其实还差一点。归零后不再显示（那一刻存量 +1、计时被重置）。
     */
    private String chargeRestoreCountdownSuffix() {
        if (charge >= Charges.MAX_CHARGE || chargeRestoreTick <= 0) {
            return "";
        }
        long seconds = (chargeRestoreTick + 19L) / 20L;      // 向上取整到秒
        return ", " + seconds + "s";
    }

    /**
     * **抓钩是否出手中**（在飞 或 正在拉拽）。
     *
     * <p>判据用 {@link #grapnel} 非空，而不是"头实体还活着"：{@code grapnel} 才是本组件认定的
     * 施放状态位（收线时它被置空），实体只是它的载体 —— 两者取同一个真值源，才不会出现
     * 「实体没了但状态还在」这种半截状态导致光效挂住不摘。
     */
    private boolean isGrapnelOut() {
        return grapnel != null;
    }

    /** **请求重绘热键栏**（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 内部数据 ─────────

    /** 抓钩的两个阶段。 */
    private enum GrapnelPhase {
        /** 飞行中（还没抓到东西）。 */
        FLYING,
        /** 已锚定，正在把苍鹭拉过去。 */
        PULLING
    }

    /**
     * 一支在飞的抓钩（不可变；推进即换一个新值）。
     *
     * <p>{@code anchorPlayer} 与 {@code anchorLocation} 至多有一个非空：
     * 命中敌人则两个都记（位置是命中那一刻的，之后按人取实时位置）；撞墙则只记位置。
     */
    private record Grapnel(ItemDisplay head,
                           Vector direction,
                           double travelledDistance,
                           int pullTicks,
                           UUID anchorPlayer,
                           Location anchorLocation) {

        /** 当前阶段：还没锚点即飞行中。 */
        GrapnelPhase phase() {
            return anchorPlayer == null && anchorLocation == null ? GrapnelPhase.FLYING : GrapnelPhase.PULLING;
        }

        Grapnel withTravelled(double distance) {
            return new Grapnel(head, direction, distance, pullTicks, anchorPlayer, anchorLocation);
        }

        Grapnel withPullTicks(int ticks) {
            return new Grapnel(head, direction, travelledDistance, ticks, anchorPlayer, anchorLocation);
        }

        /** 锚定：{@code target} 为 {@code null} 表示「锚在方块上」。 */
        Grapnel anchoredAt(Location location, UUID target) {
            return new Grapnel(head, direction, travelledDistance, 0, target, location);
        }
    }
}
