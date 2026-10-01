package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 红（{@code red}）的进化被动：把 {@link EvolutionPassive} 的「击杀 ⇒ 进化一级」接到红自己的五档增益上。
 *
 * <h2>本类做/不做什么</h2>
 * <ul>
 *   <li><b>做</b>三件事：① 持有五档的**数值与文案**（每档只在这里声明一次）；
 *       ② 每次升级向玩家发一条本档消息（{@code [进化]} 加粗淡紫 + 其后文案白色不加粗，
 *       见 {@link #evolutionMessage(int)}）；③ 3 级那一刻施加「生命上限 +10 并回满生命」（一次性）。
 *       另外提供四个**只读效果读口**给红的其它组件取用当前档位下的生效值。</li>
 *   <li><b>不做</b>：等级怎么涨是基类的（击杀订阅 + {@code increaseEvolutionLevel()}），本类一个字不重写；
 *       流血账本不在本类（在 {@link RedBleedPassive}），因此 1 级/5 级那两个"每秒"效果是**由流血被动
 *       在自己的每秒节拍里读取本类的读口**实现的 —— 谁持有数据谁动手，本类只回答"现在是几档、该是多少"。</li>
 * </ul>
 *
 * <h2>五档（档位号 = 触发该档所需的进化等级）</h2>
 * <ol>
 *   <li>红月落下 —— 每个负有流血的敌人每秒被扣 1 点特殊值（{@link RedBleedPassive} 读取）；</li>
 *   <li>鲜血横飞 —— [孤妄自赏] 多出 2 次攻击（{@link RedSolitaryArroganceSkill} 读取）；</li>
 *   <li>负罪凄凉 —— 永久生命上限 +10 并回满生命（**本类自己施加**）；</li>
 *   <li>故不可知 —— [黯然销魂] 的 TE 扣除速度减慢为每秒 4 点（{@link RedDeeplySorrowSkill} 读取）；</li>
 *   <li>猩红已至 —— 流血每秒结算 3 层（{@link RedBleedPassive} 读取）。</li>
 * </ol>
 * 每档的文案逐字来自产品口径，只在本类的 {@link #TIERS} 里出现一次
 * （描述符与升级消息共用它 ⇒ 两处文案不可能漂移）。
 *
 * <h2>封顶 5 级（为什么在 {@code onPlayerKilled} 里拦）</h2>
 * 基类的等级不封顶，且明写「要封顶就在自己的子类里拦」。本类拦在 {@link #onPlayerKilled} ——
 * 那是**生产上唯一**的升级入口（本插件没有第二处调用 {@code increaseEvolutionLevel()} 的路径）。
 * 第 5 档之后再击杀不升级、也不发消息：第 5 档就是最后一档，多出来的等级没有任何效果，
 * 让它涨下去只会得到一串"第 6 条并不存在的档位文案"。
 * <p>解除依赖的那条路（若日后确有第 6 档）：在 {@link #TIERS} 末尾加一行即可，
 * {@link #MAX_EVOLUTION_LEVEL} 与两个读口都跟着它走。
 *
 * <h2>3 级的上限加成：为什么用一把**自己的**键</h2>
 * 生命上限修饰符的键是「谁的状态谁持有」：生命组件那把 {@code role_health_modifier} 表达「角色上限」，
 * 本类这把 {@link #HEALTH_BONUS_KEY} 表达「进化带来的额外上限」。两把键 ⇒ 两个修饰符**叠加**
 * （40 + 10 = 50），谁也覆盖不了谁；{@link #stop()} 只摘自己那一把（与施加严格对称）。
 * <p>数值口径如实申报：{@code VitalsComponent#applyHealthModifier} 的入参是**上限总量**
 * （它内部算 {@code cap − BASE_MAX_HEALTH}），因此本类要表达纯加成 +10 时传的是
 * {@code BASE_MAX_HEALTH + 10}（于是 modifier 的值恰为 +10）。
 *
 * <h2>3 级只施加一次（幂等位）</h2>
 * 「永久获得」是**已经发生过的事实**：即便日后有人手动降级再升回来，也不该再叠一次 +10
 * （那不是"再次获得"，那是把同一份增益记两遍）。因此用一个私有布尔位
 * {@link #healthBonusApplied} 把「一次性」表达在类型里，而不是靠"等级只增不减"这条外部假设。
 *
 * <h2>不在本类里验的（如实申报）</h2>
 * 「发消息」与「写属性」两条都要求真实玩家（{@code svc().self().player()}）。
 * 离线单测里 {@code self} 为 {@code null} ⇒ 两处都安静跳过（不抛），因此
 * 只有"档位 ⇒ 生效值/文案"这一半在离线可验（见 {@code RedEvolutionPassiveTest}）。
 */
public class RedEvolutionPassive extends EvolutionPassive {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "red_evolution_passive";

    // ───────── 档位号（= 触发该档所需的进化等级）─────────

    /** 1 级 · 红月落下。 */
    private static final int CRIMSON_MOON_TIER = 1;

    /** 2 级 · 鲜血横飞。 */
    private static final int BLOOD_SPRAY_TIER = 2;

    /** 3 级 · 负罪凄凉。 */
    private static final int SORROWFUL_GUILT_TIER = 3;

    /** 4 级 · 故不可知。 */
    private static final int UNKNOWABLE_TIER = 4;

    /** 5 级 · 猩红已至（= 最后一档）。 */
    private static final int CRIMSON_ARRIVED_TIER = 5;

    /** 进化封顶：5 级（= 最后一档的档位号）。 */
    public static final int MAX_EVOLUTION_LEVEL = CRIMSON_ARRIVED_TIER;

    // ───────── 各档数值（每档只在这里声明一次）─────────

    /** 1 级 · 红月落下：每个负有流血的敌人每秒被扣的特殊值。 */
    private static final int CRIMSON_MOON_SANTE_DRAIN_PER_SECOND = 1;

    /** 2 级 · 鲜血横飞：[孤妄自赏] 额外增加的攻击次数。 */
    private static final int BLOOD_SPRAY_EXTRA_ATTACKS = 2;

    /** 3 级 · 负罪凄凉：永久加在生命上限上的值。 */
    private static final double SORROWFUL_GUILT_MAX_HEALTH_BONUS = 10d;

    /** 4 级 · 故不可知：[黯然销魂] 减慢后的每秒 TE 扣除。 */
    private static final int UNKNOWABLE_SANTE_DRAIN_PER_SECOND = 4;

    /** 5 级 · 猩红已至：流血每秒结算的层数。 */
    private static final int CRIMSON_ARRIVED_BLEED_STACKS_PER_SECOND = 3;

    // ───────── 基线（未达该档时的生效值；也是"取不到本组件"时消费者的兜底值）─────────

    /** 基线：[孤妄自赏] 在进化前的攻击次数（2 级起 +2 ⇒ 6）。 */
    public static final int BASE_SOLITARY_ARROGANCE_ATTACKS = 4;

    /** 基线：[黯然销魂] 在进化前的每秒 TE 扣除（4 级起减到 4）。 */
    public static final int BASE_DEEPLY_SORROW_SANTE_DRAIN_PER_SECOND = 10;

    /** 基线：流血在进化前的每秒结算层数（5 级起 3）。 */
    public static final int BASE_BLEED_SETTLE_STACKS_PER_SECOND = 1;

    /** 基线：进化前没有"流血削特殊值"这回事（1 级起每秒 1 点）。 */
    public static final int BASE_BLEED_SANTE_DRAIN_PER_SECOND = 0;

    /**
     * 3 级生命上限加成自己的修饰符键（自己的状态自己管）。
     * <p>与生命组件那把 {@code role_health_modifier} 是**两把不同的键**：那把表达"角色上限"，
     * 这把表达"进化带来的额外上限"，两个修饰符叠加而非互相覆盖。
     */
    public static final NamespacedKey HEALTH_BONUS_KEY =
            KeyFactory.Registry.of("red_evolution_health_bonus");

    // ───────── 档位文案（描述符与升级消息的唯一来源）─────────

    /** 一档进化的静态声明：档位号 + 该档文案（文案逐字来自产品口径）。 */
    private record Tier(int level, String text) {
    }

    /** 五档文案 —— 顺序 = 档位序（描述符与升级消息都从这里读）。 */
    private static final List<Tier> TIERS = List.of(
            new Tier(CRIMSON_MOON_TIER, "红月落下,每个负有流血的敌人将会持续扣除特殊值, 1秒1点"),
            new Tier(BLOOD_SPRAY_TIER, "鲜血横飞,[孤妄自赏]多出2次攻击"),
            new Tier(SORROWFUL_GUILT_TIER, "负罪凄凉-永久获得生命上限加10，并回满生命"),
            new Tier(UNKNOWABLE_TIER, "故不可知-[黯然销魂]的TE扣除速度减慢, 1秒4点"),
            new Tier(CRIMSON_ARRIVED_TIER, "猩红已至-流血每秒结算3层")
    );

    /**
     * 某一档的文案（形如 {@code "1-红月落下,…"}，编号与文案之间是 {@code "-"}）。
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

    /** 消息前缀：「进化」之后是**两个空格**（与产品口径逐字一致）。 */
    private static final String EVOLUTION_TAG = "[进化]  ";

    /** 前缀样式：加粗淡紫。 */
    private static final TextColor EVOLUTION_TAG_COLOR = NamedTextColor.LIGHT_PURPLE;

    /** 正文样式：白色不加粗（与普通聊天文本同观感）。 */
    private static final TextColor EVOLUTION_BODY_COLOR = NamedTextColor.WHITE;

    /**
     * 某一档发给玩家的整行消息（**纯文本投影**）：{@code "[进化]  N-<文案>"}。
     *
     * <p>与 {@link #evolutionMessage(int)} 是同一行的两种形态，共用
     * {@link #EVOLUTION_TAG} 与 {@link #tierTextOf(int)} ⇒ 文案与排版只有一处来源；
     * 单测把"带样式那一条的可见文本"钉回本方法，两者不可能各说一套。
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
     * 显式关掉才能把这条口径钉在类型上（不是靠"看起来是白的"）。
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
     * 本实例的生命组件（{@link #onStart()} 里一次取好缓存进字段）。
     * <p>{@code null} = 该角色没装生命组件（当前装配表里不会发生；缺了只是不加上限、不回满，不抛）。
     */
    private VitalsComponent vitals;

    /**
     * 本组件挂在自己身上的那条升级登记（{@code owner = this}）。
     * <p>基类 {@code stop()} 会把整份名单清空，因此不需要按引用单独撤；留着它是为了让
     * "自己订阅了自己"这件事在字段里可见（与 {@code EvolutionPassive.stop()} 的清理口径一致）。
     */
    private EvolutionLevelUpListener levelUpEntry;

    /** 3 级的上限加成是否已经施加过（"永久获得"只发生一次；见类注释）。 */
    private boolean healthBonusApplied;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public RedEvolutionPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：显示名「进化」+ 五档文案（描述行 = 与升级消息同源的 {@link #tierTextOf(int)}）。
     * <p>依赖只有家族描述符已经声明的 {@link VitalsComponent}
     * （{@code EvolutionPassive.Specification} 代全体子类声明，本类不重复声明）。
     * <p>被动无栏位 ⇒ 天然不占热键栏（拿着被动描述符写不出指定栏位的代码）。
     */
    public static final class Specification extends EvolutionPassive.Specification<RedEvolutionPassive> {

        public Specification() {
            super(Component.text("进化"),
                    List.of(
                            Component.text(tierTextOf(CRIMSON_MOON_TIER)),
                            Component.text(tierTextOf(BLOOD_SPRAY_TIER)),
                            Component.text(tierTextOf(SORROWFUL_GUILT_TIER)),
                            Component.text(tierTextOf(UNKNOWABLE_TIER)),
                            Component.text(tierTextOf(CRIMSON_ARRIVED_TIER))
                    ));
        }

        @Override
        public RedEvolutionPassive create(String id, ComponentServicesPort services) {
            return new RedEvolutionPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 子类自己的"开始生效"：把生命组件一次取好缓存，并**自己订阅自己的升级**
     * （{@code owner = this} —— 不需要任何跨组件查找，见基类的推荐写法）。
     *
     * <p>时机 = 基类 {@code start()} 挂完击杀订阅之后（因此不存在"登记完订阅却收不到击杀"的窗口）；
     * 依赖只在这里取，不在 {@code awake()}（那时装配序未定）。
     */
    @Override
    protected void onStart() {
        vitals = svc().components().get(VitalsComponent.class);
        levelUpEntry = addEvolutionLevelUpListener(this, this::onEvolutionLevelUp);
    }

    /**
     * 停止生效：先做基类那三件事（撤击杀订阅 / 丢升级监听名单 / 清基类自己的协作组件引用），
     * 再摘掉本类自己施加的上限加成 —— 与 3 级那一次的施加**严格对称**。
     *
     * <p>为什么必须显式摘：那把修饰符写在玩家属性上，不在本组件的回收范围内；
     * 不摘就留下一条"角色已清除但上限还多 10"的陈旧状态。
     * <p>为什么带 {@code selfPlayer() != null} 这道闸门：属性读写以"本实例的玩家"为作用对象，
     * 拿不到玩家（离线 / 未在场）时属性本来就写不上、也摘不到 —— 与
     * {@link #applySorrowfulGuilt()} 同一条容错口径（那里也是先判玩家再动手）。
     * <p>等级**不清零**（基类口径：已经发生过的事实，实例销毁即随之消失）。
     */
    @Override
    public void stop() {
        super.stop();
        if (vitals != null && selfPlayer() != null) {
            vitals.removeHealthModifier(HEALTH_BONUS_KEY);
        }
        vitals = null;
        levelUpEntry = null;
        healthBonusApplied = false;
    }

    /**
     * 击杀 ⇒ 进化一级（**生产上唯一的升级入口**）。
     *
     * <p>覆写只为封顶：已到 {@link #MAX_EVOLUTION_LEVEL} 时不再调 {@code super}
     * （于是不升级、不发消息）。判断"算不算一次击杀"仍然完全归基类，本类一个字不改
     * （因此这里必须调 {@code super} 而不是自己再写一遍 {@code increaseEvolutionLevel()}）。
     */
    @Override
    protected void onPlayerKilled(VitalsComponent.PlayerKilledEvent event) {
        if (getCurrentEvolutionLevel() >= MAX_EVOLUTION_LEVEL) {
            return;
        }
        super.onPlayerKilled(event);
    }

    // ───────── 升级 ⇒ 发消息 / 3 级施加增益 ─────────

    /**
     * 收到一次升级：① 向玩家发本档消息；② 首次达到 3 级时施加"生命上限 +10 并回满生命"。
     *
     * <p>顺序：先发消息后动属性 —— 消息读的是等级（已经写好了），与属性无关，因此无时序含义；
     * 这样写只是让"玩家先看到发生了什么"。
     */
    private void onEvolutionLevelUp(LevelUp levelUp) {
        announce(levelUp.current());
        if (!healthBonusApplied && levelUp.current() >= SORROWFUL_GUILT_TIER) {
            healthBonusApplied = true;
            applySorrowfulGuilt();
        }
    }

    /** 向玩家发一条档位消息（**带样式**：前缀加粗淡紫、正文白色不加粗）；拿不到玩家或该档没有文案时安静跳过 —— 不抛。 */
    private void announce(int level) {
        Player player = selfPlayer();
        Component message = evolutionMessage(level);
        if (player == null || message == null) {
            return;
        }
        player.sendMessage(message);
    }

    /**
     * 3 级 · 负罪凄凉：给本玩家挂上"生命上限 +10"的修饰符，并把当前生命补满。
     *
     * <p>上限只经 {@link VitalsComponent}（生命的唯一持有者）写，本类不直接碰属性；
     * 加完之后 {@code restoreFull} 读的是**属性当前值**（= 加过上限后的新值），因此"回满"回的是新的满。
     * <p>拿不到玩家（离线 / 角色不在场）时安静跳过：写不了属性，也不该因此把升级路径弄崩。
     */
    private void applySorrowfulGuilt() {
        Player player = selfPlayer();
        if (player == null || vitals == null) {
            return;
        }
        vitals.applyHealthModifier(HEALTH_BONUS_KEY,
                VitalsComponent.BASE_MAX_HEALTH + SORROWFUL_GUILT_MAX_HEALTH_BONUS);
        vitals.restoreFull(player);
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null} —— 发消息与写属性两处都容忍它）。 */
    private Player selfPlayer() {
        SelfPort self = svc().self();
        return self == null ? null : self.player();
    }

    // ───────── 效果读口（红的其它组件按当前档位取"现在是几点/几次/几层"）─────────

    /** 当前等级是否已达到某一档。 */
    private boolean hasReached(int tier) {
        return getCurrentEvolutionLevel() >= tier;
    }

    /**
     * 1 级 · 红月落下：每个负有流血的敌人每秒被扣的特殊值；未达该档为
     * {@link #BASE_BLEED_SANTE_DRAIN_PER_SECOND}（= 0，即没有这个效果）。
     * <p>消费者 = {@link RedBleedPassive}（它持有流血账本，在自己的每秒节拍里逐个受害者结算）。
     */
    public int bleedSanTEDrainPerSecond() {
        return hasReached(CRIMSON_MOON_TIER)
                ? CRIMSON_MOON_SANTE_DRAIN_PER_SECOND
                : BASE_BLEED_SANTE_DRAIN_PER_SECOND;
    }

    /**
     * 2 级 · 鲜血横飞：[孤妄自赏] 的攻击次数（基线 {@link #BASE_SOLITARY_ARROGANCE_ATTACKS} = 4，
     * 该档起 6）。
     * <p>消费者 = {@link RedSolitaryArroganceSkill}（在施放那一刻定档，整轮不中途变）。
     */
    public int solitaryArroganceAttackCount() {
        return BASE_SOLITARY_ARROGANCE_ATTACKS + (hasReached(BLOOD_SPRAY_TIER) ? BLOOD_SPRAY_EXTRA_ATTACKS : 0);
    }

    /**
     * 4 级 · 故不可知：[黯然销魂] 每秒扣除的 TE（基线
     * {@link #BASE_DEEPLY_SORROW_SANTE_DRAIN_PER_SECOND} = 10，该档起 4）。
     * <p>消费者 = {@link RedDeeplySorrowSkill}（每秒结算一次，因此升级后**下一次结算**就按新值走）。
     */
    public int deeplySorrowSanTEDrainPerSecond() {
        return hasReached(UNKNOWABLE_TIER)
                ? UNKNOWABLE_SANTE_DRAIN_PER_SECOND
                : BASE_DEEPLY_SORROW_SANTE_DRAIN_PER_SECOND;
    }

    /**
     * 5 级 · 猩红已至：流血每秒结算的层数（基线
     * {@link #BASE_BLEED_SETTLE_STACKS_PER_SECOND} = 1，该档起 3）。
     * <p>消费者 = {@link RedBleedPassive}（用它决定每秒从每个受害者身上结算掉几层）。
     */
    public int bleedSettleStacksPerSecond() {
        return hasReached(CRIMSON_ARRIVED_TIER)
                ? CRIMSON_ARRIVED_BLEED_STACKS_PER_SECOND
                : BASE_BLEED_SETTLE_STACKS_PER_SECOND;
    }
}
