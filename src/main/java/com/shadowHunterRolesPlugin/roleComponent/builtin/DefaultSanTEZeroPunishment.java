package com.shadowHunterRolesPlugin.roleComponent.builtin;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;

/**
 * 默认的「SanTE 归零惩罚」被动。
 * <p><b>当前形态</b>：
 * <ul>
 *   <li>不实现任何 legacy 生命周期接口：关心点通过向 {@code SanTEComponent} 添加监听取得
 *       （{@code sante.addListener(this, this::onSanTEChange)}，只通知、不可否决），
 *       并在 `stop()` 里按引用相等移除该登记 —— 订阅时机 = `start()`
 *       （`awake()` 只做构造期自检 / 只读自身，不得取用其他组件）；</li>
 *   <li>惩罚状态住在组件私有字段（该状态本就不该上 `RoleInstance`）；</li>
 *   <li>任务经计时组件登记本组件资源表、Buff 经 Buff 组件、SanTE 经 SanTE 组件（均直接用组件本身，
 *       不经服务集端口），真伤经生命组件的真伤入口；表现层（粒子/标题/音效）与全部数值逐字不变。</li>
 * </ul>
 * <p><b>语义要点</b>：{@code inSanTEPunishment} 若只是纯写不读的死状态（4 处出现 / 0 处读取），
 * 它声称的三件事一件都没做。现已恢复应有语义：
 * <ol>
 *   <li>进入即标记：归零判定通过后立即 {@code inSanTEPunishment = true}，先于起任务
 *       （消除"起任务与标记之间"的重入窗口）；</li>
 *   <li><b>惩罚期间不再钉值</b>（2026-10-30 用户裁定放开）：<b>没有</b> {@code update()} 逐 tick 钉
 *       SanTE 了 —— 惩罚期间被攻击，TE 依然会掉（可掉到负数，最低到 {@code SanTEComponent#min()}，
 *       默认 -999）；惩罚结束后由下面的回满任务一次性拉回上限；</li>
 *   <li>忽略重入：{@code onSanTEChange} 顶部守卫 {@code if (inSanTEPunishment) return;}，
 *       因此惩罚进行中不取消、不重启、不刷新 {@code count}、不重放标题/粒子、不重复上 STUN；</li>
 *   <li>结束回满（推迟到 STUN 结束）：在施加 STUN 的那一跳（{@code count == 1}）预约
 *       {@code timer.addScheduleLater(this, stunTicks, …)}，STUN 到期那一刻清标记并把 SanTE
 *       一次性恢复至 {@code max}（期间不钉值 ⇒ 玩家在惩罚里被打掉的值会一直保留到这一刻）。</li>
 * </ol>
 *
 * <h2>表现层（2026-10-31 增强）</h2>
 * 原有迸发粒子（图腾 30 + 灵魂 30 / 每跳灵魂 30）**不变**；新增：
 * <ul>
 *   <li><b>2 次不死图腾粒子爆炸</b>：触发瞬间一次、0.4 秒后再一次（两层扩散 + END_ROD 拉丝）；</li>
 *   <li><b>每 0.5 秒翻滚菱形</b>：大黑色菱形（半径 1.65 / 大粒子）正向翻滚，小灰色菱形（半径 0.85 /
 *       小粒子）**反方向**翻滚 —— 见 {@link #spawnRollingDiamonds(Player, int)}。</li>
 * </ul>
 * <p><b>一条曾被实测证伪的旧说法</b>：容器侧 `RoleInstance.dispatchSanTEChange` 的
 * `preSanTE == newSanTE` 早返回只能挡住「已经是 0 还继续扣」；「先回血再扣」（`pre = 4 → now = 0`）
 * 是真变化，照样派发。因此「SanTE 已为 0 时再扣不再重复派发，惩罚不再被重复触发/延长」与实测不符：
 * 那种实现在该相位下会取消并重启惩罚（刷新 `count`、重上 STUN 100 刻、重放标题与粒子、再来三跳真伤）。
 * 真正闭合"重复触发/延长"的是 (1) 进入即标记 + (3) 重入守卫，不是容器侧的早返回。
 * <p><b>中止路径必须复位标记（静默失效防护）</b>：{@code stop()}、玩家离线/死亡分支、
 * 任务取消/空转分支、以及回满任务本身的正常结束都必须把 {@code inSanTEPunishment} 复位 ——
 * 否则标记卡在 {@code true}，后续归零永不触发惩罚（绿灯不报的静默失效）。
 * 五条路径逐条标注为源码里的 {@code (5-①…⑤)}。
 */
public class DefaultSanTEZeroPunishment extends PassiveSkill {

    /** 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。 */
    public static final String ID = "default_san_te_zero_punishment";

    /** 翻滚菱形的闪烁周期（刻）= 10 刻 = 0.5 秒（需求：每 0.5 秒闪烁一次）。 */
    public static final int FX_PERIOD_TICKS = 10;

    /** 触发时的**不死图腾粒子爆炸次数**（需求：2 次；原有迸发粒子保留，不计入）。 */
    public static final int TOTEM_BURST_COUNT = 2;

    /** 第二次图腾爆炸相对触发时刻的延迟（刻）= 8 刻 ≈ 0.4 秒（两次读得出来是"两下"）。 */
    public static final int SECOND_BURST_DELAY_TICKS = 8;

    private TaskComponent timer;
    private VitalsComponent vitals;
    private BuffComponent buff;
    private SanTEComponent sante;

    /**
     * 本组件在 {@code SanTEComponent} 上的监听登记 —— 由 {@code start()} 里 {@code addListener} 的
     * 返回值填入，供 {@code stop()} 按引用相等移除（{@code Consumer} 无身份标识，必须持有同一实例）。
     */
    private SanTEComponent.Listener santeListener;

    public DefaultSanTEZeroPunishment(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符（被动走统一的 {@code addComponent} 入口，因此无栏位、天然不占热键栏）。
     * <p><b>每个角色可自定义惩罚**节奏与强度**</b>（2026-10-30 框架改进）：
     * 眩晕时长 / 惩罚跳数 / 每跳真伤占最大生命百分比。不写 = 100 刻 / 3 跳 / 10%
     * ⇒ **与改动前行为逐字一致**（原实现 3 跳、每跳 ≈0.3×最大生命×0.33333 ≈ 10%）。
     * <p><b>触发点与是否启用</b>由 {@link SanTEComponent.Configuration} 决定（阈值默认 0、启用默认 true），
     * 本描述符不再重复一份阈值。
     */
    public static final class Specification extends PassiveSkill.Specification<DefaultSanTEZeroPunishment> {

        /** 默认眩晕时长 = 100 刻 = 5 秒（与改动前一致）。 */
        public static final int DEFAULT_STUN_TICKS = 100;
        /** 默认惩罚跳数（与改动前一致）。 */
        public static final int DEFAULT_BEATS = 3;
        /** 默认每跳真伤 = 最大生命的 10%（原实现 3 跳合计 30%）。 */
        public static final double DEFAULT_DAMAGE_PER_BEAT_PERCENT = 10d;

        private int stunTicks = DEFAULT_STUN_TICKS;
        private int beats = DEFAULT_BEATS;
        private double damagePerBeatPercentOfMaxHealth = DEFAULT_DAMAGE_PER_BEAT_PERCENT;

        public Specification(){
            super(null, null);
            requires(TaskComponent.class).requires(VitalsComponent.class).requires(BuffComponent.class)
                    //归零惩罚的标题文案按自己的阵营分叉 ⇒ 阵营组件是必需依赖
                    .requires(FactionComponent.class);
            //sante 实取但代码自带 null 兜底（`start()` 的 if (sante != null) 订阅 / `stop()` 的退订），
            //因此按「实取但可为空」声明为可选。
            requiresOptional(SanTEComponent.class);
        }

        /** 覆写眩晕时长（刻；默认 {@value #DEFAULT_STUN_TICKS}）。 */
        public Specification setStunTicks(int stunTicks) {
            this.stunTicks = Math.max(1, stunTicks);
            return this;
        }

        /** 覆写惩罚跳数（默认 {@value #DEFAULT_BEATS}；每跳间隔固定 40 刻）。 */
        public Specification setBeats(int beats) {
            this.beats = Math.max(1, beats);
            return this;
        }

        /** 覆写每跳真伤占最大生命的百分比（默认 {@value #DEFAULT_DAMAGE_PER_BEAT_PERCENT}%）。 */
        public Specification setDamagePerBeatPercentOfMaxHealth(double percent) {
            this.damagePerBeatPercentOfMaxHealth = Math.max(0d, percent);
            return this;
        }

        public int stunTicks() {
            return stunTicks;
        }

        public int beats() {
            return beats;
        }

        public double damagePerBeatPercentOfMaxHealth() {
            return damagePerBeatPercentOfMaxHealth;
        }

        @Override
        public DefaultSanTEZeroPunishment create(String id, ComponentServicesPort services){
            return new DefaultSanTEZeroPunishment(id, services, this);
        }
    }

    //任务句柄（平台 Task；null = 没有任务在跑）
    private ScheduledHandle punishmentTask;
    //回满任务句柄（STUN 100 刻结束时一次性回满；与上面同属本组件资源表）
    private ScheduledHandle punishmentRestoreTask;
    /** 惩罚期间"翻滚菱形"的 0.5 秒闪烁任务（惩罚结束/中止时取消）。 */
    private ScheduledHandle punishmentFxTask;
    /** 第二次不死图腾爆炸的一次性任务（触发后 0.4 秒）。 */
    private ScheduledHandle punishmentSecondBurstTask;

    /**
     * 订阅 SanTE 变更：向 {@code SanTEComponent} 添加一条监听，而不是实现某个能力接口
     * （SanTE 的家是组件，因此消费者向组件本身取用/订阅）。
     * <p><b>时机 = {@code start()}</b>（`awake()` 只做构造期自检 / 只读自身，不得取用其他组件）；
     * 与 {@link #stop()} 的移除成对（`addListener` 本身幂等，因此重复 start 不会重复登记）。
     * <p><b>通知顺序</b> = 添加先后 = 容器 `start()` 广播序（= 组件装配序，因为 start 也按容器序广播）。
     * 与 {@code awake()} 是否同序需另证（未做运行级取证），不宣称"awake 序"。
     * <p><b>取用形态</b>：四个协作组件为字段 + 在本 `start()` 内赋值（与全仓统一形态一致）。
     * <p><b>监听登记实例存进 {@link #santeListener}</b>：{@code Consumer} 无身份标识，
     * 必须持有同一实例才能按引用移除。
     */
    @Override
    public void start() {
        timer = svc().components().get(TaskComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        if (sante != null) {
            santeListener = sante.addListener(this, this::onSanTEChange);
        }
    }

    //惩罚状态 = 组件私有字段（不上 RoleInstance）
    private boolean inSanTEPunishment = false;

    /**
     * **是否该触发惩罚**（纯函数，离线可测）：TE 已经降到阈值或以下 ⇒ 触发。
     * <p>默认阈值 = 0（降到 0 进入惩罚，与既有实现一致）；角色把阈值调到 -50 就变成
     * "降到 -50 才进入惩罚"。
     */
    public static boolean triggersPunishment(int threshold, int current) {
        return current <= threshold;
    }

    /**
     * SanTE 变更回调：入参为载荷 {@link SanTEComponent.Change}（旧形态的两个值
     * {@code (int pre, int now)} 装在一个 record 里，语义逐字保留）。
     * <p>本方法不实现任何接口，是本类的普通方法，由 {@code start()} 里的方法引用
     * {@code this::onSanTEChange} 注册进监听器列表。
     */
    public void onSanTEChange(SanTEComponent.Change change) {
        //载荷里的两个值 = 旧入参的两个值（顺序与含义逐字不变）
        int now = change.current();
        if (sante == null) return;
        //(3) 忽略重入：惩罚进行中直接返回 —— 不取消、不重启、不刷新 count、不重放表现层
        if(inSanTEPunishment) return;
        //角色可关闭默认惩罚（关闭后由角色自行实现）；默认 true
        if(!sante.punishmentEnabled()) return;
        //触发阈值由角色配置（默认 0 ⇒ 既有行为）
        int threshold = sante.punishmentThreshold();
        if(!triggersPunishment(threshold, now)) return;
        Faction faction = svc().components().get(FactionComponent.class).faction();

        //惩罚节奏/强度的配置在触发时抓一次快照（运行期改描述符不影响已开始的这一轮）
        Specification config = (Specification) specification();
        int stunTicks = config.stunTicks();
        int beats = Math.max(1, config.beats());
        double damagePerBeatPercent = Math.max(0d, config.damagePerBeatPercentOfMaxHealth());

        //单一活动任务不变量：先取消仍在跑的旧任务，因此不产生任务泄漏。
        //注意：这里不是"重入保护" —— 重入由上面的 (3) 守卫处理，本行只保证同时最多一个任务对象。
        cancelPunishmentTask();

        //(1) 进入即标记：必须先于起任务（消除"起任务与标记之间"的重入窗口）
        inSanTEPunishment = true;

        //新增表现（2026-10-31）：2 次图腾爆炸 + 每 0.5 秒一对反向翻滚的黑/灰菱形
        startPunishmentParticleTasks();

        punishmentTask = timer.addScheduleRepeating(this, 0L, 40L, new Runnable() {

                    int count = 0;
                    Player player = svc().self().player();

                    @Override
                    public void run() {
                        if (punishmentTask == null || punishmentTask.isCancelled()) {
                            //(5-③ 防御性) 任务已不在则标记不得滞留（否则后续归零永不触发惩罚）
                            inSanTEPunishment = false;
                            cancelParticleTasks();
                            return;
                        }
                        if (!player.isOnline() || player.isDead()) {
                            //(5-② 中止路径) 离线/死亡：取消任务并复位标记
                            punishmentTask.cancel();
                            inSanTEPunishment = false;
                            cancelParticleTasks();
                            return;
                        }


                        if (count >= beats) {
                            //(5-④ 防御性) 收尾跳之后的一次空转：取消并确保标记已复位
                            punishmentTask.cancel();
                            inSanTEPunishment = false;
                            cancelParticleTasks();
                            return;
                        }
                        count += 1;

                        //(2) 钉阈值不在这里：它由 update() 逐 tick 执行，
                        //    因此惩罚期间"每一 tick 都钉在阈值上"，不再有两钉点之间被抬高的窗口。

                        Location loc = player.getLocation();

                        if (count == 1) {
                            buff.add(BuffType.STUN, stunTicks);

                            //(4) 回满推迟到 STUN 结束：STUN 在本跳施加、持续 stunTicks 刻，自本跳起
                            //    stunTicks 刻后（= STUN 到期那一刻）清标记并一次性回满。
                            //    期间**不钉值**（2026-10-30 放开）：被打掉的值会一直保留到回满这一刻。
                            punishmentRestoreTask = timer.addScheduleLater(DefaultSanTEZeroPunishment.this,
                                    (long) stunTicks, () -> {
                                //(5-⑤ 正常结束) 若标记已被中止路径复位则不再回满（防越权恢复）
                                if (!inSanTEPunishment) return;
                                //先清标记：否则同一 tick 的逐 tick 钉会把刚回满的值立刻抹回去
                                inSanTEPunishment = false;
                                cancelParticleTasks();
                                sante.set(sante.max());
                            });
                            player.playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1f);

                            Location particleLoc = loc.clone().add(0, 1, 0);
                            particleLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, particleLoc, 30, 0.5d, 0.5d, 0.5d);
                            particleLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 30, 0.5d, 0.5d, 0.5d);

                            long titleMillis = stunTicks * 50L;
                            Title title = Title.title(
                                    Component.empty(),
                                    Component.empty()
                            );
                            if (faction == Faction.HUNTER) {
                                title = Title.title(
                                        Component.text("⊠恐惧正在注视着你⊠", NamedTextColor.LIGHT_PURPLE),
                                        Component.empty(),
                                        Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(titleMillis),
                                                Duration.ZERO
                                        )
                                );
                            } else if (faction == Faction.SHADOW) {
                                title = Title.title(
                                        Component.text("⊠痛苦摸上脊背⊠", NamedTextColor.YELLOW),
                                        Component.empty(),
                                        Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(titleMillis),
                                                Duration.ZERO
                                        )
                                );
                            }

                            player.showTitle(title);

                        }

                        if (count != 1) {
                            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.1f);
                            Location particleLoc = loc.clone().add(0, 1, 0);
                            particleLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 30, 0.5d, 0.5d, 0.5d);
                        }
                        double maxHealth = player.getAttribute(Attribute.MAX_HEALTH) != null
                                ? player.getAttribute(Attribute.MAX_HEALTH).getValue() : 20d;
                        vitals.trueDamage(player, null, maxHealth * damagePerBeatPercent / 100d);
                        //回满不在末跳：已推迟到 STUN 结束（见 count == 1 处的回满任务）。
                    }
                });
    }

    // ───────── 惩罚表现（2026-10-31 需求：2 次图腾爆炸 + 每 0.5 秒翻滚菱形）─────────

    /**
     * 启动惩罚表现任务：
     * <ol>
     *   <li><b>两次不死图腾粒子爆炸</b>：触发瞬间一次，{@value #SECOND_BURST_DELAY_TICKS} 刻后（≈0.4 秒）
     *       再一次 —— 原有迸发粒子（图腾 30 + 灵魂 30）保留不动，这里只是额外叠加；</li>
     *   <li><b>翻滚菱形</b>：每 {@value #FX_PERIOD_TICKS} 刻（0.5 秒）闪烁一次 —— 一个大黑色菱形 +
     *       一个**反向翻滚**的小灰色菱形（见 {@link #spawnRollingDiamonds}）。</li>
     * </ol>
     * 两个任务都挂在计时组件资源表上，由 {@link #cancelParticleTasks()} 统一取消。
     */
    private void startPunishmentParticleTasks() {
        Player owner = svc().self().player();
        if (owner == null) {
            return;
        }
        //第一次爆炸：触发瞬间
        totemBurst(owner.getWorld(), owner.getLocation());
        //第二次爆炸：0.4 秒后（两次读得出来是"两下"）
        punishmentSecondBurstTask = timer.addScheduleLater(this, (long) SECOND_BURST_DELAY_TICKS, () -> {
            if (!inSanTEPunishment) return;
            Player now = svc().self().player();
            if (now == null || !now.isOnline() || now.isDead()) return;
            totemBurst(now.getWorld(), now.getLocation());
        });
        //翻滚菱形：每 0.5 秒闪烁一次
        int[] frame = {0};
        punishmentFxTask = timer.addScheduleRepeating(this, 0L, FX_PERIOD_TICKS, () -> {
            if (!inSanTEPunishment) {
                cancelParticleTasks();
                return;
            }
            Player now = svc().self().player();
            if (now == null || !now.isOnline() || now.isDead()) {
                cancelParticleTasks();
                return;
            }
            frame[0]++;
            spawnRollingDiamonds(now, frame[0]);
        });
    }

    /** 取消惩罚表现任务（幂等；惩罚结束 / 中止 / 被新惩罚顶替时都走这里）。 */
    private void cancelParticleTasks() {
        if (punishmentFxTask != null) {
            punishmentFxTask.cancel();
            punishmentFxTask = null;
        }
        if (punishmentSecondBurstTask != null) {
            punishmentSecondBurstTask.cancel();
            punishmentSecondBurstTask = null;
        }
    }

    /** 一次不死图腾粒子爆炸（两层：外圈扩散 + 内圈高密；TOTEM 为主、END_ROD 拉丝）。 */
    private static void totemBurst(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        Location center = at.clone().add(0d, 1.0d, 0d);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 80, 0.9d, 1.0d, 0.9d, 0.08d);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, center, 40, 0.35d, 0.5d, 0.35d, 0.02d);
        world.spawnParticle(Particle.END_ROD, center, 16, 1.2d, 1.2d, 1.2d, 0.05d);
    }

    /**
     * 一对**反向翻滚**的菱形（每 0.5 秒闪一次）：
     * <ul>
     *   <li>大黑色菱形：半径 1.65、粒子尺寸 2.2，正向翻滚；</li>
     *   <li>小灰色菱形：半径 0.85、粒子尺寸 0.95，**反方向**翻滚（{@link #smallDiamondSpin} 取负）。</li>
     * </ul>
     */
    private static void spawnRollingDiamonds(Player player, int frame) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        Location center = player.getLocation().add(0d, 1.15d, 0d);
        spawnDiamond(world, center, 1.65d, 2.2f, Color.fromRGB(18, 18, 18),
                bigDiamondSpin(frame), 0.35d, 6);
        spawnDiamond(world, center.clone().add(0d, 0.1d, 0d), 0.85d, 0.95f, Color.fromRGB(150, 150, 150),
                smallDiamondSpin(frame), -0.35d, 4);
    }

    /**
     * 画一个菱形：四个顶点沿边采样，先绕 Y 轴旋转 {@code spin}（翻滚），再绕 X 轴倾斜 {@code tilt}，
     * 最后以 DUST 逐点画出。
     */
    private static void spawnDiamond(World world, Location center, double radius, float size, Color color,
                                     double spin, double tilt, int edgePoints) {
        Particle.DustOptions dust = new Particle.DustOptions(color, size);
        double cosSpin = Math.cos(spin);
        double sinSpin = Math.sin(spin);
        double cosTilt = Math.cos(tilt);
        double sinTilt = Math.sin(tilt);
        for (int edge = 0; edge < 4; edge++) {
            Vector from = diamondVertex(edge, radius);
            Vector to = diamondVertex((edge + 1) % 4, radius);
            for (int i = 0; i < edgePoints; i++) {
                double t = i / (double) edgePoints;
                Vector point = from.clone().multiply(1d - t).add(to.clone().multiply(t));
                //绕 Y 翻滚
                double x = point.getX() * cosSpin - point.getZ() * sinSpin;
                double z = point.getX() * sinSpin + point.getZ() * cosSpin;
                //绕 X 倾斜
                double y = point.getY() * cosTilt - z * sinTilt;
                double z2 = point.getY() * sinTilt + z * cosTilt;
                world.spawnParticle(Particle.DUST, center.clone().add(x, y, z2), 1, 0d, 0d, 0d, 0d, dust);
            }
        }
    }

    /** 菱形的四个顶点（上 / 右 / 下 / 左）；{@code index} 越界按左处理。 */
    static Vector diamondVertex(int index, double radius) {
        return switch (index) {
            case 0 -> new Vector(0d, radius, 0d);
            case 1 -> new Vector(radius, 0d, 0d);
            case 2 -> new Vector(0d, -radius, 0d);
            default -> new Vector(-radius, 0d, 0d);
        };
    }

    /** 大黑色菱形的翻滚角（帧 → 弧度）：正向。 */
    static double bigDiamondSpin(int frame) {
        return frame * 0.30d;
    }

    /** 小灰色菱形的翻滚角：负向 ⇒ 与大菱形**反向**（需求）。 */
    static double smallDiamondSpin(int frame) {
        return -frame * 0.45d;
    }

    //取消仍在运行的惩罚任务/回满任务/表现任务并复位句柄（所有句柄都属本组件资源表）
    private void cancelPunishmentTask(){
        if(punishmentTask != null){
            punishmentTask.cancel();
            punishmentTask = null;
        }
        if(punishmentRestoreTask != null){
            punishmentRestoreTask.cancel();
            punishmentRestoreTask = null;
        }
        cancelParticleTasks();
    }

    /**
     * 停止生效：取消任务并复位惩罚标记。框架 `cancelAllAndClear()` 兜底取消同一句柄，因此幂等。
     * <p>(5-① 中止路径) 复位标记是必须的：若玩家在惩罚中被清角色/下线，标记滞留 {@code true} 会让
     * 后续归零永不触发惩罚（静默失效、绿灯不报）。
     */
    @Override
    public void stop() {
        cancelPunishmentTask();
        inSanTEPunishment = false;
        //移除监听：订阅在 start() 登记，本行与它成对（按引用移除；不在名单里是 no-op，因此幂等）。
        if (sante != null && santeListener != null) {
            sante.removeListener(santeListener);
            santeListener = null;
        }
    }
}
