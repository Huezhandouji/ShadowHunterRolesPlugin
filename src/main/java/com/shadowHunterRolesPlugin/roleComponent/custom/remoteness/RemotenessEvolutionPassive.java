package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 冷识（{@code remoteness}）的进化被动：把 {@link EvolutionPassive} 的「击杀 ⇒ 进化一级」
 * 接到冷识自己的八档增益上。
 *
 * <h2>本类做/不做什么</h2>
 * <ul>
 *   <li><b>做</b>三件事：① 持有八档的**数值与文案**（每档只在这里声明一次）；
 *       ② 每次升级向玩家发一条本档消息（{@code [进化]} 加粗淡紫 + 其后文案白色不加粗）；
 *       ③ 7 级起，"击杀一名敌人"这条**即时**增益（+30 特殊值、6 秒伤害吸收 II）在这里结算。
 *       另外提供一组**只读效果读口**给冷识的其它组件取用当前档位下的生效值。</li>
 *   <li><b>不做</b>：等级怎么涨是基类的，本类一个字不重写；**永久**增益（速度 / 跳跃 / 恢复 / 抗性）
 *       的施加与刷新在 {@link RemotenessStartEndPassive}（它持有"每 20 刻刷新一次"的节拍），
 *       本类只回答"现在该是几级"；5 级光环的每秒结算同样在那个被动的节拍里发动，本类只给"每秒扣几点"。</li>
 * </ul>
 * 这条分工与「谁持有数据谁动手」一致：每秒 / 每刻的效果写在**持有节拍**的组件里、由它读本类的读口；
 * 本类不认识任何"账本"，因此反过来不需要任何 setter。
 *
 * <h2>八档（档位号 = 触发该档所需的进化等级）</h2>
 * <ol>
 *   <li>获得永久跳跃提升 2（{@link RemotenessStartEndPassive} 读取）；</li>
 *   <li>获得永久速度 3（同上）；</li>
 *   <li>获得持续生命恢复 2（同上）；</li>
 *   <li>塑造：对敌人的特殊值伤害 +30、漂浮时间升到 7 秒、CD 延长到 40 秒（{@link RemotenessShapingSkill} 读取）；</li>
 *   <li>身边半径 10 内的敌人持续扣除特殊值，每秒 2 点（{@link RemotenessStartEndPassive} 读取）；</li>
 *   <li>获得永久抗性 1（{@link RemotenessStartEndPassive} 读取）；</li>
 *   <li>击杀一名敌人时，自己 +30 特殊值，并获得 6 秒伤害吸收 2（**本类自己施加**）；</li>
 *   <li>[塑造] 直接清空敌人特殊值，CD 延长到 60 秒（{@link RemotenessShapingSkill} 读取）。</li>
 * </ol>
 * 每档的文案逐字来自产品口径，只在本类的 {@link #TIERS} 里出现一次
 * （描述符与升级消息共用它 ⇒ 两处文案不可能漂移）。
 *
 * <h2>封顶 8 级（为什么在 {@code onPlayerKilled} 里拦）</h2>
 * 基类的等级不封顶，且明写「要封顶就在自己的子类里拦」。本类拦在 {@link #onPlayerKilled} ——
 * 那是**生产上唯一**的升级入口。与 {@code RedEvolutionPassive} 有一处**不同**（如实申报）：
 * 红封顶之后直接 {@code return}（它的 5 级就是最后一档，之后击杀没有任何效果）；
 * 冷识的 7 级增益是"从 7 级起每次击杀都给"，封顶（8 级）之后这条增益**仍然生效**，
 * 因此封顶分支不是"什么都不做"，而是"不升级、但仍结算击杀增益"。
 *
 * <h2>与 {@code RedEvolutionPassive} 的一处结构差异</h2>
 * 红的 3 级用了一把**自己的** {@code NamespacedKey} 往生命上限上挂修饰符，因此要成对维护
 * 施加 / 撤销两处。冷识的八档没有"改平台属性"这一项（速度 / 跳跃 / 抗性 / 吸收全是药水效果），
 * 全部走 {@link BuffComponent} 的记账路径 ⇒ 本类**不持有任何 NamespacedKey**，
 * 也不需要在 {@code stop()} 里摘任何东西（药水账本由 buff 组件在自己的 {@code stop()} 里统一回收）。
 *
 * <h2>不在本类里验的（如实申报）</h2>
 * 「发消息」与「写特殊值 / 施加吸收」都要求真实玩家。
 * 离线单测里 {@code self} 为 {@code null} / 服务集为惰性 ⇒ 两处都安静跳过（不抛），
 * 因此只有"档位 ⇒ 生效值 / 文案"这一半在离线可验（见 {@code RemotenessEvolutionPassiveTest}）。
 */
public class RemotenessEvolutionPassive extends EvolutionPassive {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "remoteness_evolution_passive";

    // ───────── 档位号（= 触发该档所需的进化等级）─────────

    /** 1 级 · 永久跳跃提升 2。 */
    private static final int JUMP_UPGRADE_TIER = 1;

    /** 2 级 · 永久速度 3。 */
    private static final int SPEED_UPGRADE_TIER = 2;

    /** 3 级 · 持续生命恢复 2。 */
    private static final int REGENERATION_TIER = 3;

    /** 4 级 · 塑造强化（伤害 / 漂浮时长 / CD）。 */
    private static final int SHAPING_UPGRADE_TIER = 4;

    /** 5 级 · 光环（半径 10 内敌人每秒扣特殊值）。 */
    private static final int AURA_TIER = 5;

    /** 6 级 · 永久抗性 1。 */
    private static final int RESISTANCE_TIER = 6;

    /** 7 级 · 击杀奖励（+30 特殊值、6 秒伤害吸收 2）。 */
    private static final int KILL_REWARD_TIER = 7;

    /** 8 级 · [塑造] 清空敌人特殊值 + CD 60 秒（= 最后一档）。 */
    private static final int SHAPING_PURGE_TIER = 8;

    /** 进化封顶：8 级（= 最后一档的档位号）。 */
    public static final int MAX_EVOLUTION_LEVEL = SHAPING_PURGE_TIER;

    // ───────── 各档数值（每档只在这里声明一次）─────────

    /** 1 级 · 永久跳跃提升的增幅（跳跃提升 2 = 增幅 1）。 */
    private static final int JUMP_UPGRADE_AMPLIFIER = 1;

    /** 2 级 · 永久速度的增幅（速度 3 = 增幅 2）。 */
    private static final int SPEED_UPGRADE_AMPLIFIER = 2;

    /** 3 级 · 持续生命恢复的增幅（生命恢复 2 = 增幅 1）。 */
    private static final int REGENERATION_AMPLIFIER = 1;

    /** 6 级 · 永久抗性的增幅（抗性提升 1 = 增幅 0）。 */
    private static final int RESISTANCE_AMPLIFIER = 0;

    /** 4 级 · [塑造] 对敌人特殊值伤害的增量（基线 30 ⇒ 60）。 */
    private static final int SHAPING_BONUS_SANTE_DAMAGE = 30;

    /** 4 级 · [塑造] 的漂浮时长（4 秒 → 7 秒 = 140 刻）。 */
    private static final int SHAPING_UPGRADED_FLOAT_TICKS = 140;

    /** 4 级 · [塑造] 的冷却（40 秒 = 800 刻）。 */
    private static final int SHAPING_UPGRADED_COOLDOWN_TICKS = 800;

    /** 8 级 · [塑造] 的冷却（60 秒 = 1200 刻）。 */
    private static final int SHAPING_PURGED_COOLDOWN_TICKS = 1200;

    /** 5 级 · 光环每秒扣除的特殊值。 */
    private static final int AURA_SANTE_DRAIN_PER_SECOND = 2;

    /** 7 级 · 每次击杀给自己增加的特殊值。 */
    private static final int KILL_SANTE_REWARD = 30;

    /** 7 级 · 击杀后获得的伤害吸收时长（6 秒 = 120 刻）。 */
    private static final int KILL_ABSORPTION_TICKS = 120;

    /** 7 级 · 击杀后获得的伤害吸收增幅（伤害吸收 2 = 增幅 1）。 */
    private static final int KILL_ABSORPTION_AMPLIFIER = 1;

    // ───────── 基线（未达该档时的生效值；也是"取不到本组件"时消费者的兜底值）─────────

    /** 基线：永久速度的增幅（被动"始末"的固有速度一 = 增幅 0）。 */
    public static final int BASE_SPEED_AMPLIFIER = 0;

    /** 基线：永久跳跃提升的增幅（被动"始末"的固有跳跃提升一 = 增幅 0）。 */
    public static final int BASE_JUMP_AMPLIFIER = 0;

    /** 基线：[塑造] 对敌人特殊值的基础伤害（4 级起 60）。 */
    public static final int BASE_SHAPING_SANTE_DAMAGE = 30;

    /** 基线：[塑造] 的基础漂浮时长（4 秒 = 80 刻；4 级起 7 秒）。 */
    public static final int BASE_SHAPING_FLOAT_TICKS = 80;

    /** 基线：[塑造] 的基础冷却（20 秒 = 400 刻；4 级起 40 秒、8 级起 60 秒）。 */
    public static final int BASE_SHAPING_COOLDOWN_TICKS = 400;

    /** 基线：光环每秒扣除的特殊值（5 级起 2 ⇒ 未达该档时这一项是 no-op）。 */
    public static final int BASE_AURA_SANTE_DRAIN_PER_SECOND = 0;

    // ───────── 档位文案（描述符与升级消息的唯一来源）─────────

    /** 一档进化的静态声明：档位号 + 该档文案（文案逐字来自产品口径）。 */
    private record Tier(int level, String text) {
    }

    /** 八档文案 —— 顺序 = 档位序（描述符与升级消息都从这里读）。 */
    private static final List<Tier> TIERS = List.of(
            new Tier(JUMP_UPGRADE_TIER, "获得永久跳跃提升2"),
            new Tier(SPEED_UPGRADE_TIER, "获得永久速度3"),
            new Tier(REGENERATION_TIER, "获得持续生命恢复2"),
            new Tier(SHAPING_UPGRADE_TIER, "塑造对敌人特殊值伤害提高30点,并漂浮时间上升至7秒,CD延长至40s"),
            new Tier(AURA_TIER, "在你身边半径10范围内的敌人会持续扣除特殊值, 1秒2点"),
            new Tier(RESISTANCE_TIER, "获得永久抗性1"),
            new Tier(KILL_REWARD_TIER, "在击杀一名敌人时, 增加自己30sante, 获得6秒的伤害吸收2"),
            new Tier(SHAPING_PURGE_TIER, "[塑造]直接清空敌人sante, CD延长至60s")
    );

    /**
     * 某一档的文案（形如 {@code "1-获得永久跳跃提升2"}，编号与文案之间是 {@code "-"}）。
     *
     * <p>纯函数、不读实例状态 ⇒ 离线可验（升级消息与描述符共用它）。
     *
     * @param level 档位号
     * @return 该档文案；不在 {@code 1..}{@link #MAX_EVOLUTION_LEVEL} 范围内回 {@code null}
     *         （"没有这一档"因此可判定，而不是回一个空串让人猜）
     */
    public static String tierTextOf(int level) {
        for (Tier tier : TIERS) {
            if (tier.level() == level) {
                return tier.level() + "-" + tier.text();
            }
        }
        return null;
    }

    // ───────── 升级消息：前缀与两种形态（纯文本 / 带样式）─────────

    /**
     * 消息前缀：「进化」。
     * <p>前缀之后**不留空格**，档位文案紧跟着它（{@code [进化]1-…}）—— 与 {@code RedEvolutionPassive}
     * 同一口径（全仓两处进化消息的排版就此对齐）。
     */
    private static final String EVOLUTION_TAG = "[进化]";

    /** 前缀样式：加粗淡紫。 */
    private static final TextColor EVOLUTION_TAG_COLOR = NamedTextColor.LIGHT_PURPLE;

    /** 正文样式：白色不加粗（与普通聊天文本同观感）。 */
    private static final TextColor EVOLUTION_BODY_COLOR = NamedTextColor.WHITE;

    /**
     * 某一档发给玩家的整行消息（**纯文本投影**）：{@code "[进化]" + "N-<文案>"}。
     *
     * <p>与 {@link #evolutionMessage(int)} 是同一行的两种形态，共用 {@link #EVOLUTION_TAG} 与
     * {@link #tierTextOf(int)} ⇒ 文案与排版只有一处来源。
     *
     * @param level 档位号
     * @return 该档消息；不在档位范围内回 {@code null}（调用方据此跳过发送）
     */
    public static String evolutionMessageOf(int level) {
        String tierText = tierTextOf(level);
        return tierText == null ? null : EVOLUTION_TAG + tierText;
    }

    /**
     * 某一档发给玩家的整行消息（**带样式**；真正发给玩家的是它）：
     * {@code [进化]} 加粗淡紫，其后的档位文案正常颜色、**白色且不加粗**。
     *
     * <p><b>为什么正文要显式写 {@code BOLD=false}</b>：Adventure 里子组件**继承**父组件的样式，
     * 正文只写白色、不显式关掉粗体，它仍会继承前缀的粗体 —— "后面的文案不加粗"就不成立。
     *
     * @param level 档位号
     * @return 该档消息；不在档位范围内回 {@code null}（调用方据此跳过发送）
     */
    public static Component evolutionMessage(int level) {
        String tierText = tierTextOf(level);
        if (tierText == null) {
            return null;
        }
        return Component.text(EVOLUTION_TAG, EVOLUTION_TAG_COLOR)
                .decoration(TextDecoration.BOLD, true)
                .append(Component.text(tierText, EVOLUTION_BODY_COLOR)
                        .decoration(TextDecoration.BOLD, false));
    }

    // ───────── 实例状态 ─────────

    /**
     * 特殊值组件（7 级击杀奖励要 +30）—— {@link #onStart()} 里一次取好缓存进字段。
     * <p>{@code null} = 该角色没装特殊值组件（当前装配表里不会发生；缺了只是不给奖励，不抛）。
     */
    private SanTEComponent sante;

    /**
     * buff 组件（7 级击杀奖励要挂 6 秒伤害吸收）—— 同上。
     */
    private BuffComponent buff;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public RemotenessEvolutionPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：显示名「进化」+ 八档文案（描述行 = 与升级消息同源的 {@link #tierTextOf(int)}）。
     * <p>依赖只有家族描述符已经声明的 {@link VitalsComponent}
     * （{@code EvolutionPassive.Specification} 代全体子类声明，本类不重复声明）——
     * 本类实取的两个组件（特殊值 / buff）只在**7 级之后**才被用到，且 7 级之前的击杀同样走
     * {@code onPlayerKilled}，因此它们**不**在实取清单的必需要求里；缺了只丢奖励、不阻止装配。
     * <p>被动无栏位 ⇒ 天然不占热键栏（拿着被动描述符写不出指定栏位的代码）。
     */
    public static final class Specification extends EvolutionPassive.Specification<RemotenessEvolutionPassive> {

        public Specification() {
            super(Component.text("进化"),
                    List.of(
                            Component.text(tierTextOf(JUMP_UPGRADE_TIER)),
                            Component.text(tierTextOf(SPEED_UPGRADE_TIER)),
                            Component.text(tierTextOf(REGENERATION_TIER)),
                            Component.text(tierTextOf(SHAPING_UPGRADE_TIER)),
                            Component.text(tierTextOf(AURA_TIER)),
                            Component.text(tierTextOf(RESISTANCE_TIER)),
                            Component.text(tierTextOf(KILL_REWARD_TIER)),
                            Component.text(tierTextOf(SHAPING_PURGE_TIER))
                    ));
        }

        @Override
        public RemotenessEvolutionPassive create(String id, ComponentServicesPort services) {
            return new RemotenessEvolutionPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 子类自己的"开始生效"：把 7 级奖励要用到的两个组件一次取好缓存。
     *
     * <p>时机 = 基类 {@code start()} 挂完击杀订阅之后（因此不存在"登记完订阅却收不到击杀"的窗口）；
     * 依赖只在这里取，不在 {@code awake()}（那时装配序未定）。
     * <p>本类**不**再订阅击杀：基类已经把「击杀 ⇒ 升级」挂在同一条名单上，
     * 而 7 级奖励与"升级"是同一件事的两个后果 ⇒ 写在覆写后的 {@link #onPlayerKilled} 里
     * （一条订阅、一个入口，不制造第二条平行的击杀通道）。
     */
    @Override
    protected void onStart() {
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    /** 停止生效：与 {@link #onStart()} 对称地清掉两个协作组件引用；等级不清零（基类口径）。 */
    @Override
    public void stop() {
        super.stop();
        sante = null;
        buff = null;
    }

    /**
     * 击杀 ⇒ ① 未封顶则进化一级；② 7 级起结算击杀奖励。
     *
     * <p>覆写有两个理由，逐条说明：
     * <ol>
     *   <li><b>封顶</b>：已到 {@link #MAX_EVOLUTION_LEVEL} 时不再调 {@code super}
     *       （于是不升级、不发消息）；</li>
     *   <li><b>7 级奖励</b>：封顶之后这条奖励仍然生效 ⇒ 封顶分支**不能**直接 return，
     *       必须先把奖励结掉（见类注释「封顶 8 级」一节）。</li>
     * </ol>
     * 判断"算不算一次击杀"仍然完全归基类，本类一个字不改
     * （因此这里必须调 {@code super} 而不是自己再写一遍 {@code increaseEvolutionLevel()}）。
     *
     * @param event 击杀载荷（本类只用"发生过一次击杀"这一点）
     */
    @Override
    protected void onPlayerKilled(VitalsComponent.PlayerKilledEvent event) {
        if (getCurrentEvolutionLevel() >= MAX_EVOLUTION_LEVEL) {
            grantKillReward();
            return;
        }
        super.onPlayerKilled(event);
        grantKillReward();
    }

    /**
     * 7 级起：每次击杀给自己 +30 特殊值，并挂 6 秒伤害吸收 2。
     *
     * <p>两个动作都要求对应的组件在场：拿不到（离线单测 / 该角色没装）时安静跳过，不抛 ——
     * 与"被动可缺失即容忍"的既有口径一致。吸收走 buff 组件的**记账**路径
     * （而非直接 {@code addPotionEffect}），因此角色清除时它会被一并回收。
     */
    private void grantKillReward() {
        if (getCurrentEvolutionLevel() < KILL_REWARD_TIER) {
            return;
        }
        if (sante != null) {
            sante.increase(KILL_SANTE_REWARD);
        }
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.ABSORPTION, KILL_ABSORPTION_TICKS, KILL_ABSORPTION_AMPLIFIER);
        }
    }

    // ───────── 效果读口（冷识的其它组件按当前档位取"现在是几级 / 多少"）─────────

    /** 当前等级是否已达到某一档。 */
    private boolean hasReached(int tier) {
        return getCurrentEvolutionLevel() >= tier;
    }

    /**
     * 永久速度的增幅：基线 {@link #BASE_SPEED_AMPLIFIER}（= 0，即被动固有的"速度一"），
     * 2 级起 {@link #SPEED_UPGRADE_AMPLIFIER}（= 2，即"速度 3"）。
     * <p>消费者 = {@link RemotenessStartEndPassive}（它持有"每 20 刻刷新一次"的节拍）。
     */
    public int speedAmplifier() {
        return hasReached(SPEED_UPGRADE_TIER) ? SPEED_UPGRADE_AMPLIFIER : BASE_SPEED_AMPLIFIER;
    }

    /**
     * 永久跳跃提升的增幅：基线 {@link #BASE_JUMP_AMPLIFIER}（= 0，即被动固有的"跳跃提升一"），
     * 1 级起 {@link #JUMP_UPGRADE_AMPLIFIER}（= 1，即"跳跃提升 2"）。
     * <p>消费者 = {@link RemotenessStartEndPassive}。
     */
    public int jumpAmplifier() {
        return hasReached(JUMP_UPGRADE_TIER) ? JUMP_UPGRADE_AMPLIFIER : BASE_JUMP_AMPLIFIER;
    }

    /** 3 级起持续给予生命恢复 2（{@code true} = 该持续增益应当在场）。消费者 = {@link RemotenessStartEndPassive}。 */
    public boolean hasRegeneration() {
        return hasReached(REGENERATION_TIER);
    }

    /** 生命恢复的增幅（{@code hasRegeneration()} 为 {@code true} 时才有意义）。 */
    public int regenerationAmplifier() {
        return REGENERATION_AMPLIFIER;
    }

    /** 6 级起持续给予抗性提升 1（{@code true} = 该持续增益应当在场）。消费者 = {@link RemotenessStartEndPassive}。 */
    public boolean hasResistance() {
        return hasReached(RESISTANCE_TIER);
    }

    /** 抗性提升的增幅（{@code hasResistance()} 为 {@code true} 时才有意义 —— 抗性提升 1 = 增幅 0）。 */
    public int resistanceAmplifier() {
        return RESISTANCE_AMPLIFIER;
    }

    /**
     * 5 级 · 光环：身边半径 10 内的敌人每秒被扣除的特殊值。
     * <p>基线 {@link #BASE_AURA_SANTE_DRAIN_PER_SECOND}（= 0 ⇒ 未达该档时调用方整段是 no-op）。
     * <p>消费者 = {@link RemotenessStartEndPassive}（它持有每秒节拍，并负责"半径 10 / 只算敌人"的几何与判敌）。
     */
    public int auraSanteDrainPerSecond() {
        return hasReached(AURA_TIER) ? AURA_SANTE_DRAIN_PER_SECOND : BASE_AURA_SANTE_DRAIN_PER_SECOND;
    }

    /**
     * [塑造] 对范围内敌人扣除的特殊值：基线 {@link #BASE_SHAPING_SANTE_DAMAGE}（= 30），
     * 4 级起 +{@link #SHAPING_BONUS_SANTE_DAMAGE}（= 60）。
     * <p>8 级时本读数**不再使用**（那档改为"直接清空"，见 {@link #shapingClearsSante()}）——
     * 保留它是因为 8 级也可能被 {@code decreaseEvolutionLevel()} 降回去，两条读数各自独立可判。
     * <p>消费者 = {@link RemotenessShapingSkill}。
     */
    public int shapingSanteDamage() {
        return BASE_SHAPING_SANTE_DAMAGE + (hasReached(SHAPING_UPGRADE_TIER) ? SHAPING_BONUS_SANTE_DAMAGE : 0);
    }

    /**
     * [塑造] 的漂浮时长（刻）：基线 {@link #BASE_SHAPING_FLOAT_TICKS}（= 80 = 4 秒），
     * 4 级起 {@link #SHAPING_UPGRADED_FLOAT_TICKS}（= 140 = 7 秒）。
     * <p>消费者 = {@link RemotenessShapingSkill}。
     */
    public int shapingFloatTicks() {
        return hasReached(SHAPING_UPGRADE_TIER) ? SHAPING_UPGRADED_FLOAT_TICKS : BASE_SHAPING_FLOAT_TICKS;
    }

    /**
     * [塑造] 的冷却（刻）：基线 {@link #BASE_SHAPING_COOLDOWN_TICKS}（= 400 = 20 秒），
     * 4 级起 {@link #SHAPING_UPGRADED_COOLDOWN_TICKS}（= 800 = 40 秒），
     * 8 级起 {@link #SHAPING_PURGED_COOLDOWN_TICKS}（= 1200 = 60 秒）。
     * <p>三档是**叠加的档位**（8 级也满足 4 级），因此从高到低判一次。
     * <p>消费者 = {@link RemotenessShapingSkill}（用显式刻数启动冷却，而不是描述符里的声明值）。
     */
    public int shapingCooldownTicks() {
        if (hasReached(SHAPING_PURGE_TIER)) {
            return SHAPING_PURGED_COOLDOWN_TICKS;
        }
        if (hasReached(SHAPING_UPGRADE_TIER)) {
            return SHAPING_UPGRADED_COOLDOWN_TICKS;
        }
        return BASE_SHAPING_COOLDOWN_TICKS;
    }

    /**
     * 8 级 · [塑造] 是否改为"直接清空敌人特殊值"（{@code true} = 清空，忽略
     * {@link #shapingSanteDamage()}）。
     * <p>消费者 = {@link RemotenessShapingSkill}。
     */
    public boolean shapingClearsSante() {
        return hasReached(SHAPING_PURGE_TIER);
    }

    /**
     * 7 级 · 每次击杀给自己增加的特殊值（供诊断 / 探针读；结算在本类 {@link #grantKillReward()} 里）。
     * <p>回的是**声明值**（不是"当前是否生效"）—— "生效与否"由 {@link #getCurrentEvolutionLevel()} 与
     * {@link #KILL_REWARD_TIER} 在调用点判，本读口不替调用方做状态判断。
     */
    public int killSanteReward() {
        return KILL_SANTE_REWARD;
    }
}
