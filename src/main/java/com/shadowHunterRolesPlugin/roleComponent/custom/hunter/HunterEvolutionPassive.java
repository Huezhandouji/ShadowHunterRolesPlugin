package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 「猎手」被动之二：**进化指数**（把 {@link EvolutionPassive} 的「击杀 ⇒ 进化一级」接到猎手自己的五档增益上）。
 *
 * <h2>本类做/不做什么</h2>
 * <ul>
 *   <li><b>做</b>三件事：① 持有五档的**数值与文案**（每档只在这里声明一次）；
 *       ② 每次升级向玩家发一条本档消息（{@code [进化]} 加粗淡紫 + 其后文案白色不加粗）；
 *       ③ 提供**只读效果读口**给猎手的其它组件取用当前档位下的生效值。</li>
 *   <li><b>不做</b>：等级怎么涨是基类的（击杀订阅 + {@code increaseEvolutionLevel()}），
 *       本类一个字不重写；封顶除外 —— 基类明写「要封顶就在自己的子类里拦」，见
 *       {@link #onPlayerKilled(VitalsComponent.PlayerKilledEvent)}。</li>
 * </ul>
 *
 * <h2>五档（档位号 = 触发该档所需的进化等级）</h2>
 * <ol>
 *   <li><b>你想起了美好</b> —— 获得永久<b>生命恢复 II</b>（本类自己施加）；</li>
 *   <li><b>你想起了家庭</b> —— 获得永久<b>抗性 I</b>（本类自己施加）；</li>
 *   <li><b>你想起了训练的日子</b> —— 所有<b>技能</b> CD 减少 1 秒（由各技能读
 *       {@link #adjustedSkillCooldownTicks(int)} 生效）；</li>
 *   <li><b>你想起了游猎</b> —— [遗愤] 攻击回复 <b>5 点 TE 值</b>（{@link HunterGrudgeMainWeapon} 读
 *       {@link #grudgeSanTERestore()}）；</li>
 *   <li><b>你要抓住那些憧憬</b> —— [遁形] 持续时间缩短为 <b>6 秒</b>，但在范围 <b>5 格</b>内所有敌人每
 *       <b>0.5 秒</b>受到 <b>3 点灵魂伤害</b>与 <b>10 点特殊值伤害</b>，若成功造成伤害会为 [猎手] 回复
 *       <b>4 点生命</b>（{@link HunterStealthSkill} 读 {@link #stealthDurationTicks(int)} 与
 *       {@link #stealthSoulPulse()}）。</li>
 * </ol>
 * 每档的文案逐字来自产品口径，只在本类的 {@link #TIERS} 里出现一次
 * （描述符与升级消息共用它 ⇒ 两处文案不可能漂移）。
 *
 * <h2>★ 口径申报：第 3 档的"技能 CD 减少 1"= 减 1 秒，且不含主武器</h2>
 * 需求写"所有技能CD减少1"，单位未写。本实现取 **1 秒 = {@value #SKILL_COOLDOWN_RELIEF_TICKS} 刻**
 * （与工程里"CD-6"=6 秒、冷却名以秒显示的既有口径一致）。
 * <p>"技能"取**占 1/2/3 号栏的三个主动**，**不含 0 号栏的主武器** —— 与工程既有的
 * {@code TekDestinyPassive}「减少 1 秒所有技能 CD（只看技能，不含主武器）」同口径。
 * <p>实现方式 = **每次施放成功时按缩短后的时长起冷却**（读 {@link #adjustedSkillCooldownTicks(int)}），
 * 而不是"开局把所有冷却改短"：冷却时长来自各组件描述符里的声明值（冻结面），
 * 本类只提供"该用多少"的换算，不碰任何组件的声明。
 *
 * <h2>★ 口径申报：1 / 2 档那两个"永久"效果怎么落地</h2>
 * "永久" = **角色存续期内长期有效**（角色在死亡 / 掉线 / 清角色时整体回收，故不存在跨角色的永久）。
 * 落法 = 用一把 {@value #PERMANENT_EFFECT_TICKS} 刻（1 小时）的长时长药水效果，经
 * {@link BuffComponent#applyPotionEffect(PotionEffectType, int, int)}（**只作用自己**的正确口）施加。
 * <p>为什么不"每 tick 重刷"：本角色里 {@link HunterStealthSkill#update()} 也在刷同名的
 * {@code RESISTANCE}（抗性 V，短时长）——两边都高频重刷会互相覆盖、表现为抗性在 I 与 V 之间跳。
 * 因此本类**只在升级时施加一次**，并额外提供 {@link #reapplyPermanentEffects()} 供遁形在结束时
 * 把它自己覆盖掉的那份补回来（唯一调用方 = {@link HunterStealthSkill#endStealth}）。
 *
 * <h2>不在本类里验的（如实申报）</h2>
 * 发消息与写药水两条都要求真实玩家（{@code svc().self().player()}）。
 * 离线单测里 {@code self} 为 {@code null} ⇒ 两处都安静跳过（不抛），因此只有
 * "档位 ⇒ 生效值"这一半在离线可验。
 */
public class HunterEvolutionPassive extends EvolutionPassive {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "hunter_evolution_passive";

    // ───────── 档位号（= 触发该档所需的进化等级）─────────

    /** 1 级 · 你想起了美好。 */
    private static final int MEMORY_OF_GOODNESS_TIER = 1;

    /** 2 级 · 你想起了家庭。 */
    private static final int MEMORY_OF_FAMILY_TIER = 2;

    /** 3 级 · 你想起了训练的日子。 */
    private static final int MEMORY_OF_TRAINING_TIER = 3;

    /** 4 级 · 你想起了游猎。 */
    private static final int MEMORY_OF_HUNTING_TIER = 4;

    /** 5 级 · 你要抓住那些憧憬（= 最后一档）。 */
    private static final int GRASP_THE_YEARLING_TIER = 5;

    /** 进化封顶：5 级（= 最后一档的档位号）。 */
    public static final int MAX_EVOLUTION_LEVEL = GRASP_THE_YEARLING_TIER;

    // ───────── 各档数值（每档只在这里声明一次）─────────

    /** 1 级 · 生命恢复的增幅值（生命恢复 II ⇒ 1）。 */
    private static final int REGENERATION_AMPLIFIER = 1;

    /** 2 级 · 抗性的增幅值（抗性 I ⇒ 0）。 */
    private static final int RESISTANCE_AMPLIFIER = 0;

    /** 3 级 · 所有**技能** CD 减少的刻数（1 秒）。 */
    public static final int SKILL_COOLDOWN_RELIEF_TICKS = 20;

    /** 4 级 · [遗愤] 每次攻击回复的 TE（SanTE）值。 */
    private static final int GRUDGE_SANTE_RESTORE = 5;

    /** 5 级 · [遁形] 缩短后的持续时间（刻）—— 需求原话"6秒"。 */
    public static final int STEALTH_SHORT_DURATION_TICKS = 120;

    /**
     * 1 / 2 档"永久"效果的时长（刻）：1 小时。
     * <p>取"很长但有限"而不是 {@code Integer.MAX_VALUE}：药水时长在客户端 HUD 上有上限语义，
     * 而"永久"在本工程里的真实含义只是"角色存续期内"（角色会被整体回收）。
     */
    private static final int PERMANENT_EFFECT_TICKS = 20 * 60 * 60;

    // ───────── 档位文案（描述符与升级消息的唯一来源）─────────

    /** 一档进化的静态声明：档位号 + 该档文案（文案逐字来自产品口径）。 */
    private record Tier(int level, String text) {
    }

    /** 五档文案 —— 顺序 = 档位序（描述符与升级消息都从这里读）。 */
    private static final List<Tier> TIERS = List.of(
            new Tier(MEMORY_OF_GOODNESS_TIER, "你想起了美好,获得永久生命恢复2"),
            new Tier(MEMORY_OF_FAMILY_TIER, "你想起了家庭,获得永久抗性1"),
            new Tier(MEMORY_OF_TRAINING_TIER, "你想起了训练的日子,所有技能CD减少1"),
            new Tier(MEMORY_OF_HUNTING_TIER, "你想起了游猎,[遗愤]攻击回复5点TE值"),
            new Tier(GRASP_THE_YEARLING_TIER,
                    "你要抓住那些憧憬,[遁形]持续时间缩短为6秒,"
                            + "但在范围[5格]内的所有敌人每0.5秒受到3点的灵魂伤害与10点特殊值伤害,"
                            + "若成功造成伤害,会为[猎手]回复4点生命,其它不变")
    );

    /**
     * 某一档的文案（形如 {@code "1-你想起了美好,…"}，编号与文案之间是 {@code "-"}）。
     *
     * <p>纯函数、不读实例状态 ⇒ 离线可验（升级消息与描述符共用它）。
     *
     * @param level 档位号
     * @return 该档文案；不在 {@code 1..}{@link #MAX_EVOLUTION_LEVEL} 范围内回 {@code null}
     */
    public static String tierTextOf(int level) {
        for (Tier tier : TIERS) {
            if (tier.level() == level) {
                return tier.level() + "-" + tier.text();
            }
        }
        return null;
    }

    // ───────── 升级消息 ─────────

    /** 消息前缀：「进化」之后是**两个空格**（与工程既有口径逐字一致）。 */
    private static final String EVOLUTION_TAG = "[进化]  ";

    /** 前缀样式：加粗淡紫。 */
    private static final TextColor EVOLUTION_TAG_COLOR = NamedTextColor.LIGHT_PURPLE;

    /** 正文样式：白色不加粗。 */
    private static final TextColor EVOLUTION_BODY_COLOR = NamedTextColor.WHITE;

    /**
     * 某一档发给玩家的整行消息（**纯文本投影**）：{@code "[进化]  N-<文案>"}。
     *
     * @param level 档位号
     * @return 该档消息；不在档位范围内回 {@code null}（调用方据此跳过发送）
     */
    public static String evolutionMessageOf(int level) {
        String tierText = tierTextOf(level);
        return tierText == null ? null : EVOLUTION_TAG + tierText;
    }

    /**
     * 某一档发给玩家的整行消息（**带样式**；真正发给玩家的是它）。
     *
     * <p>正文显式写 {@code BOLD=false}：Adventure 的子组件**继承**父组件样式，
     * 只写白色不关粗体，正文仍会继承前缀的粗体 —— 显式关掉才能把"后面的文案不加粗"钉在类型上。
     *
     * @param level 档位号
     * @return 该档消息；不在档位范围内回 {@code null}
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

    private VitalsComponent vitals;
    private BuffComponent buff;

    /**
     * 本组件挂在自己身上的那条升级登记（{@code owner = this}）。
     * <p>基类 {@code stop()} 会把整份名单清空，因此不需要按引用单独撤。
     */
    private EvolutionLevelUpListener levelUpEntry;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HunterEvolutionPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：显示名「进化指数」+ 五档文案（描述行 = 与升级消息同源的 {@link #tierTextOf(int)}）。
     * <p>依赖只有家族描述符已经声明的 {@link VitalsComponent}
     * （{@code EvolutionPassive.Specification} 代全体子类声明，本类不重复声明）。
     */
    public static final class Specification extends EvolutionPassive.Specification<HunterEvolutionPassive> {

        public Specification() {
            super(Component.text("进化指数"), List.of(
                    Component.text(tierTextOf(MEMORY_OF_GOODNESS_TIER)),
                    Component.text(tierTextOf(MEMORY_OF_FAMILY_TIER)),
                    Component.text(tierTextOf(MEMORY_OF_TRAINING_TIER)),
                    Component.text(tierTextOf(MEMORY_OF_HUNTING_TIER)),
                    Component.text(tierTextOf(GRASP_THE_YEARLING_TIER))
            ));
        }

        @Override
        public HunterEvolutionPassive create(String id, ComponentServicesPort services) {
            return new HunterEvolutionPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 子类自己的"开始生效"：取依赖、自己订阅自己的升级，并把已经达到的永久效果补上
     * （时机 = 基类挂完击杀订阅之后）。
     */
    @Override
    protected void onStart() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        levelUpEntry = addEvolutionLevelUpListener(this, this::onEvolutionLevelUp);
        reapplyPermanentEffects();
    }

    /**
     * 停止生效：先做基类那三件事（撤击杀订阅 / 丢升级监听名单 / 清基类自己的协作组件引用），
     * 再清本类的字段。等级**不清零**（基类口径：已经发生过的事实，实例销毁即随之消失）。
     */
    @Override
    public void stop() {
        super.stop();
        vitals = null;
        buff = null;
        levelUpEntry = null;
    }

    /**
     * 击杀 ⇒ 进化一级（**生产上唯一的升级入口**）。
     *
     * <p>覆写只为封顶：已到 {@link #MAX_EVOLUTION_LEVEL} 时不再调 {@code super}
     * （于是不升级、不发消息）。判断"算不算一次击杀"仍然完全归基类，本类一个字不改。
     */
    @Override
    protected void onPlayerKilled(VitalsComponent.PlayerKilledEvent event) {
        if (getCurrentEvolutionLevel() >= MAX_EVOLUTION_LEVEL) {
            return;
        }
        super.onPlayerKilled(event);
    }

    // ───────── 升级 ⇒ 发消息 / 施加永久效果 ─────────

    /** 收到一次升级：① 向玩家发本档消息；② 把该档带来的永久效果补上（幂等）。 */
    private void onEvolutionLevelUp(LevelUp levelUp) {
        announce(levelUp.current());
        reapplyPermanentEffects();
    }

    /** 向玩家发一条档位消息（**带样式**）；拿不到玩家或该档没有文案时安静跳过 —— 不抛。 */
    private void announce(int level) {
        Player player = selfPlayer();
        Component message = evolutionMessage(level);
        if (player == null || message == null) {
            return;
        }
        player.sendMessage(message);
    }

    // ───────── 永久效果（1 / 2 档）─────────

    /**
     * **重新施加本档位下应有的永久药水效果**（幂等；未达档位则什么都不做）。
     *
     * <p>调用点两处：① 升级时（本类）；② **遁形结束时**（{@link HunterStealthSkill}）——
     * 遁形会用自己的短时长 {@code RESISTANCE}（抗性 V）覆盖掉本类这份抗性 I，
     * 结束后由它把这份补回来。这是本工程里"谁覆盖谁负责还原"的显式分工。
     *
     * <p>走 {@link BuffComponent#applyPotionEffect(PotionEffectType, int, int)}（**只作用自己**的正确口）。
     */
    public void reapplyPermanentEffects() {
        if (buff == null) {
            return;
        }
        if (hasReached(MEMORY_OF_GOODNESS_TIER)) {
            buff.applyPotionEffect(PotionEffectType.REGENERATION, PERMANENT_EFFECT_TICKS, REGENERATION_AMPLIFIER);
        }
        if (hasReached(MEMORY_OF_FAMILY_TIER)) {
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, PERMANENT_EFFECT_TICKS, RESISTANCE_AMPLIFIER);
        }
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null} —— 发消息这一处容忍它）。 */
    private Player selfPlayer() {
        SelfPort self = svc().self();
        return self == null ? null : self.player();
    }

    // ───────── 效果读口（猎手的其它组件按当前档位取"现在是几点/几次/几秒"）─────────

    /** 当前等级是否已达到某一档。 */
    private boolean hasReached(int tier) {
        return getCurrentEvolutionLevel() >= tier;
    }

    /**
     * 3 级 · 所有**技能** CD 每次施放应减少的刻数（未达该档回 {@code 0}）。
     * <p>消费者 = 三个主动技能，在**施放成功**那条路径上读它。
     */
    public int skillCooldownReliefTicks() {
        return hasReached(MEMORY_OF_TRAINING_TIER) ? SKILL_COOLDOWN_RELIEF_TICKS : 0;
    }

    /**
     * **把一次技能的声明冷却换算成本次实际应起的冷却**（纯函数式换算 ⇒ 可离线验）。
     *
     * <p>未达 3 级时原样返回 {@code baseTicks}；已达则减 {@link #SKILL_COOLDOWN_RELIEF_TICKS} 刻。
     * <p>下限保护取 {@code 1} 刻而非 {@code 0}：{@code ActiveComponent.startCooldown(<=0)}
     * 的语义是"直接清冷却、返回 false"，而"技能 CD 减少 1 秒"不该把短 CD 技能变成**无冷却**
     * ⇒ 保留 1 刻，使"施放过一次"这件事仍然成立。
     *
     * @param baseTicks 组件描述符里声明的冷却刻数（&le; 0 时原样返回，交给基类按"无冷却"处理）
     */
    public int adjustedSkillCooldownTicks(int baseTicks) {
        if (baseTicks <= 0) {
            return baseTicks;
        }
        int relief = skillCooldownReliefTicks();
        return relief <= 0 ? baseTicks : Math.max(1, baseTicks - relief);
    }

    /**
     * 4 级 · [遗愤] 每次攻击回复的 TE（SanTE）值（未达该档回 {@code 0}）。
     * <p>消费者 = {@link HunterGrudgeMainWeapon}（它持有 SanTE 组件，在命中路径上结算）。
     */
    public int grudgeSanTERestore() {
        return hasReached(MEMORY_OF_HUNTING_TIER) ? GRUDGE_SANTE_RESTORE : 0;
    }

    /**
     * 5 级 · [遁形] 的持续时间（刻）—— 该档起缩短为 {@link #STEALTH_SHORT_DURATION_TICKS}（6 秒），
     * 未达该档原样返回组件声明的 15 秒。
     * <p>消费者 = {@link HunterStealthSkill}（在施放那一刻定档，整轮不中途变）。
     */
    public int stealthDurationTicks(int baseTicks) {
        return hasReached(GRASP_THE_YEARLING_TIER) ? STEALTH_SHORT_DURATION_TICKS : baseTicks;
    }

    /**
     * 5 级 · [遁形] 期间是否开启"5 格内每 0.5 秒"的灵魂脉冲。
     * <p>消费者 = {@link HunterStealthSkill#update()}。
     */
    public boolean stealthSoulPulse() {
        return hasReached(GRASP_THE_YEARLING_TIER);
    }
}
