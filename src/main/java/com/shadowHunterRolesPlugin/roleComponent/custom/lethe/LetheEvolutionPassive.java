package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 冷识（{@code lethe}）的进化被动：把 {@link EvolutionPassive} 的「击杀 ⇒ 进化一级」接到冷识的**五档**进化上。
 *
 * <h2>五档（档位号 = 触发该档所需的进化等级；等级来源 = 基类 {@link #getCurrentEvolutionLevel()}）</h2>
 * <ol>
 *   <li>『吟唱』特殊值上限 +50 —— <b>本类直接生效</b>（见下「上限写口」）；</li>
 *   <li>『共鸣』共鸣圈触发上限 8 —— <b>本类直接生效</b>（{@code LetheResonanceComponent#setRingTriggerCap(8)}）；</li>
 *   <li>『无憾』遗忘会记录目标，并在归还时还原其血量与特殊值 —— 只提供查询布尔，
 *       由遗忘技能读取并实现（{@link #hasFateRecall()}）；</li>
 *   <li>『救赎』重塑冷却减半 —— 只提供查询布尔，由重塑技能读取并实现
 *       （{@link #hasReducedReshapeCooldown()}）；</li>
 *   <li>『新生』重塑吸血 + 圈内敌人反哺 —— 只提供查询布尔，由重塑 / 共鸣圈读取并实现
 *       （{@link #hasNewLifeDrain()}）。</li>
 * </ol>
 *
 * <h2>★ 上限写口（本档依赖一次经用户批准的框架最小改动）</h2>
 * 冷识 1 级要求 SanTE 上限 +50。原框架的 {@code SanTEComponent.max} 是构造期定死的只读值，
 * 本重做为它增加了受控写口 {@code SanTEComponent#setMax(int)}（Lead 已向用户申报为「边界跨越」，
 * 改动压到最小：只放开上限字段 + 一个写口，clamp / 通知语义不变）。因此本类**真的**把上限改掉：
 * <ul>
 *   <li>升级到 1 级（或角色重进时已 ≥1 级）⇒ {@code setMax(SANTE_MAX + 50)}；</li>
 *   <li>未达 1 级 / 卸载 ⇒ {@code setMax(SANTE_MAX)} 还原；</li>
 *   <li>两处都做**幂等同步**：先比 {@code sante.max()} 与目标值，相同就不写 —— 避免无意义的 clamp 与通知。</li>
 * </ul>
 *
 * <h2>★ 2 级"圈触发上限 8"为什么在升级回调里直接改</h2>
 * 触发上限的真值在共鸣组件（跨组件状态），本类不是它的持有者，因此只调它公开的写口
 * {@code setRingTriggerCap(...)}。因此依赖声明用 {@code requiresOptional} 而不是 {@code requires}：
 * 共鸣组件与本类是"同一实例里的两个兄弟"，若两边都写成必需依赖会造成装配期互相等待
 * （成环风险）；而本档的语义是"有共鸣组件就把上限抬高"，缺了只是这一档不生效、其余照常
 * ⇒ 可选依赖 + 取不到就安静跳过，才是与家族口径一致的做法（被动可缺失即容忍）。
 * <p>取用时做**晚取兜底**：装配序若让本类先 {@code start()}，第一次取到 {@code null} 也不会永久失去该档 ——
 * 每次同步时若字段仍为空就再查一次（成本 = 每次升级一次查表，可忽略）。
 *
 * <h2>升级播报</h2>
 * 升级回调（基类写完等级**之后**才通知 ⇒ 回调里读到的一定是升级后的等级）里做三件事：
 * ① 把本档需要的"直接生效"效果幂等同步；② 把 {@link #evolutionMessage(int)} 这一档文案发给玩家；
 * ③ 播 {@code LetheVfx.evolutionFlash} + {@code LetheSound.evolutionLevelUp} + 语音
 * {@link LetheFloatingTextComponent#MOMENT_EVOLVE}（三项表现均由 D 道实现，本类只按契约函数名调用）。
 *
 * <h2>封顶 5 级（为什么在 {@code onPlayerKilled} 里拦）</h2>
 * 基类的等级不封顶，且明写「要封顶就在自己的子类里拦」。本类拦在 {@link #onPlayerKilled} ——
 * 那是生产上唯一的升级入口；已满级时不再调 {@code super}（于是不升级、不发消息）。
 *
 * <h2>降级口径（如实申报）</h2>
 * 基类在**降级**时不发通知（订阅面的名字就是"升级"）。本类因此不监听降级：生产上没有降级路径
 * （只有调试命令会调 {@code decreaseEvolutionLevel()}），调试降级后本档效果要到下一次
 * {@code start()} / 升级才会重新对齐。这是基类既有口径的直接后果，不在本类另开一条"等级变化"通道。
 *
 * <h2>不在本类里验的（如实申报）</h2>
 * 发消息 / 播表现都要求真实玩家；离线（{@code self} 为 {@code null}）时整段安静跳过（不抛）。
 * {@code setMax} / {@code setRingTriggerCap} 对协作组件的调用在离线单测里需要那两份组件在场，
 * 缺席时由 {@code null} 判定跳过。
 */
public class LetheEvolutionPassive extends EvolutionPassive {

    /** 本组件的登记 id。 */
    public static final String ID = "lethe_evolution_passive";

    // ───────── 档位号（= 触发该档所需的进化等级）─────────

    /** 1 级『吟唱』：特殊值上限 +50。 */
    private static final int TIER_CHANT = 1;

    /** 2 级『共鸣』：共鸣圈触发上限 8。 */
    private static final int TIER_RESONANCE = 2;

    /** 3 级『无憾』：遗忘记录并还原血量 / 特殊值。 */
    private static final int TIER_FATE = 3;

    /** 4 级『救赎』：重塑冷却减半。 */
    private static final int TIER_REDEMPTION = 4;

    /** 5 级『新生』：重塑吸血 + 圈内敌人反哺。 */
    private static final int TIER_NEW_LIFE = 5;

    /** 进化封顶：5 级（= 最后一档的档位号）。 */
    public static final int MAX_EVOLUTION_LEVEL = TIER_NEW_LIFE;

    // ───────── 各档数值（每档只在这里声明一次）─────────

    /** 1 级『吟唱』：特殊值上限的增量（需求 +50）。 */
    public static final int SANTE_MAX_BONUS = 50;

    /** 2 级『共鸣』：共鸣圈的触发上限（需求 8）。 */
    public static final int EXTENDED_RESONANCE_CAP = 8;

    /**
     * 共鸣圈触发上限的**默认值**（契约 §3.1：{@code setRingTriggerCap(int cap)} 默认 5）。
     * 本类在"未达 2 级 / 卸载"时用它还原；契约没有为它提供公开常量，因此这里显式写死并注明来源。
     */
    private static final int BASE_RING_TRIGGER_CAP = 5;

    // ───────── 档位文案（描述符与升级消息的唯一来源）─────────

    /** 一档进化的静态声明：档位号 + 该档文案。 */
    private record Tier(int level, String text) {
    }

    /** 五档文案 —— 顺序 = 档位序（描述符与升级消息都从这里读，两处文案不可能漂移）。 */
    private static final List<Tier> TIERS = List.of(
            new Tier(TIER_CHANT, "『吟唱』特殊值上限提高50点"),
            new Tier(TIER_RESONANCE, "『共鸣』共鸣圈的触发上限提升至8"),
            new Tier(TIER_FATE, "『无憾』遗忘会记录目标, 并在归还时还原其血量与特殊值"),
            new Tier(TIER_REDEMPTION, "『救赎』重塑的冷却时间减半"),
            new Tier(TIER_NEW_LIFE, "『新生』重塑获得吸血, 共鸣圈内的敌人会反哺冷识")
    );

    /**
     * 某一档的文案（形如 {@code "1-『吟唱』…"}，编号与文案之间是 {@code "-"}，与旧口径一致）。
     *
     * <p>纯函数、不读实例状态 ⇒ 离线可验（描述符与升级消息共用它）。
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

    /** 消息前缀：「进化」（其后不留空格，档位文案紧跟着它）。 */
    private static final String EVOLUTION_TAG = "[进化]";

    /** 前缀样式：加粗淡紫。 */
    private static final TextColor EVOLUTION_TAG_COLOR = NamedTextColor.LIGHT_PURPLE;

    /** 正文样式：白色不加粗（与普通聊天文本同观感）。 */
    private static final TextColor EVOLUTION_BODY_COLOR = NamedTextColor.WHITE;

    /**
     * 某一档发给玩家的整行消息（**带样式**）：{@code [进化]} 加粗淡紫，
     * 其后的档位文案白色且**显式不加粗**（Adventure 子组件会继承父组件的样式，不显式关掉会继承粗体）。
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

    // ───────── 实例状态（start() 里一次取好）─────────

    /** 特殊值组件：1 级要改它的上限（写口 {@code setMax}）。 */
    private SanTEComponent sante;

    /**
     * 共鸣组件：2 级要改圈触发上限。
     * <p>{@code null} = 装配表里没有它（可选依赖）；取不到时每次同步会再晚取一次。
     */
    private LetheResonanceComponent resonance;

    /** 语音组件：升级时喊 {@link LetheFloatingTextComponent#MOMENT_EVOLVE}。 */
    private LetheFloatingTextComponent voice;

    /** 本组件自己的升级监听登记（与 {@code start()} 成对；基类 {@code stop()} 会清空整张名单）。 */
    private EvolutionLevelUpListener levelUpEntry;

    public LetheEvolutionPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：显示名「进化」+ 五档文案（描述行 = 与升级消息同源的 {@link #tierTextOf(int)}）。
     * <p>依赖声明：{@link SanTEComponent}（1 级上限写口）与 {@link LetheFloatingTextComponent}（升级语音）
     * 都是本类**确实会取**的兄弟组件 ⇒ 声明为必需，漏配在装配期就报，而不是运行期静默退化；
     * 共鸣组件是可选（成环风险 + 缺了只是 2 档不生效）。基类描述符已代本家族声明 {@code VitalsComponent}。
     * <p>被动无栏位 ⇒ 天然不占热键栏。
     */
    public static final class Specification extends EvolutionPassive.Specification<LetheEvolutionPassive> {

        public Specification() {
            super(Component.text("进化"),
                    List.of(
                            Component.text(tierTextOf(TIER_CHANT)),
                            Component.text(tierTextOf(TIER_RESONANCE)),
                            Component.text(tierTextOf(TIER_FATE)),
                            Component.text(tierTextOf(TIER_REDEMPTION)),
                            Component.text(tierTextOf(TIER_NEW_LIFE))
                    ));
            requires(SanTEComponent.class)
                    .requires(LetheFloatingTextComponent.class)
                    //2 档的圈上限写口在共鸣组件上：可选（缺了只是该档不生效，不阻止装配；也避开成环）
                    .requiresOptional(LetheResonanceComponent.class);
        }

        @Override
        public LetheEvolutionPassive create(String id, ComponentServicesPort services) {
            return new LetheEvolutionPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 子类自己的"开始生效"（时机 = 基类挂完击杀订阅之后）：
     * 取协作组件、自己订阅自己的升级，并做一次**幂等同步** —— 角色重进时若已带等级，
     * 上限 / 圈上限也要与等级一致（不能只靠"升级回调"这一条路径）。
     */
    @Override
    protected void onStart() {
        sante = svc().components().get(SanTEComponent.class);
        voice = getComponent(LetheFloatingTextComponent.class);
        resonance = findComponent(LetheResonanceComponent.class);
        levelUpEntry = addEvolutionLevelUpListener(this, this::onLevelUp);
        syncTierEffects();
    }

    /**
     * 停止生效：先把本类改过的两个共享读数还原，再清引用。
     * <p>还原顺序在清引用之前（要用到组件字段）；先做基类收尾（撤击杀订阅 / 丢监听名单），
     * 与 {@code start()} 严格对称。
     * <p>上限还原 = {@code setMax(SANTE_MAX)}（会把 current 一并夹回基准上限）；
     * 圈上限还原 = 契约默认值 5。
     */
    @Override
    public void stop() {
        super.stop();
        if (sante != null) {
            sante.setMax(SanTEComponent.SANTE_MAX);
        }
        if (resonance != null) {
            resonance.setRingTriggerCap(BASE_RING_TRIGGER_CAP);
        }
        sante = null;
        resonance = null;
        voice = null;
        levelUpEntry = null;
    }

    /**
     * 击杀 ⇒ 进化一级（**生产上唯一的升级入口**）；封顶 5 级后不再调 {@code super}
     * （于是不升级、不发消息，也不播表现）。判断"算不算一次击杀"仍完全归基类。
     */
    @Override
    protected void onPlayerKilled(VitalsComponent.PlayerKilledEvent event) {
        if (getCurrentEvolutionLevel() >= MAX_EVOLUTION_LEVEL) {
            return;
        }
        super.onPlayerKilled(event);
    }

    // ───────── 升级 ⇒ 生效 / 播报 ─────────

    /**
     * 收到一次升级：① 幂等同步本档的"直接生效"效果；② 档位文案上屏；③ 播表现。
     * <p>此时基类已写完新等级 ⇒ 三个动作读到的都是升级后的档位。
     */
    private void onLevelUp(LevelUp levelUp) {
        syncTierEffects();
        announce(levelUp.current());
        playLevelUpFeedback();
    }

    /**
     * 把两个"本类直接生效"的档位同步到当前等级：
     * <ol>
     *   <li>1 级 ⇒ 特殊值上限 {@code SANTE_MAX + 50}；否则还原 {@code SANTE_MAX}；</li>
     *   <li>2 级 ⇒ 圈触发上限 {@link #EXTENDED_RESONANCE_CAP}；否则还原契约默认值 5。</li>
     * </ol>
     * 全部幂等（重复调用结果相同），因此 {@code start()} 与每次升级都调它是安全的。
     */
    private void syncTierEffects() {
        syncSanteMax();
        syncRingTriggerCap();
    }

    /** 1 级：特殊值上限 +50（幂等：目标值与当前上限相同就不写，避免无意义的 clamp 与通知）。 */
    private void syncSanteMax() {
        if (sante == null) {
            return;
        }
        int targetMax = SanTEComponent.SANTE_MAX + (hasSanteMaxBonus() ? santeMaxBonus() : 0);
        if (sante.max() == targetMax) {
            return;
        }
        sante.setMax(targetMax);
    }

    /** 2 级：共鸣圈触发上限 8；否则还原默认 5。组件可选 ⇒ 取不到就整段 no-op。 */
    private void syncRingTriggerCap() {
        LetheResonanceComponent ring = resonanceOrLate();
        if (ring == null) {
            return;
        }
        ring.setRingTriggerCap(hasExtendedResonance() ? extendedResonanceCap() : BASE_RING_TRIGGER_CAP);
    }

    /** 取共鸣组件（可选依赖）：装配序导致 {@code start()} 时没取到的话，这里再查一次。 */
    private LetheResonanceComponent resonanceOrLate() {
        if (resonance == null) {
            resonance = findComponent(LetheResonanceComponent.class);
        }
        return resonance;
    }

    /** 把本档文案发给玩家（无玩家 / 该档无文案时安静跳过 —— 不抛）。 */
    private void announce(int level) {
        Player player = selfPlayer();
        Component message = evolutionMessage(level);
        if (player == null || message == null) {
            return;
        }
        player.sendMessage(message);
    }

    /**
     * 升级表现：进化闪光 + 升级音效 + 语音（三项都按契约函数名调用；{@code LetheVfx} / {@code LetheSound}
     * 的入参 null 一律静默返回，因此这里不需要额外判空）。
     */
    private void playLevelUpFeedback() {
        Player player = selfPlayer();
        if (player == null) {
            return;
        }
        LetheVfx.evolutionFlash(player.getWorld(), player.getLocation());
        LetheSound.evolutionLevelUp(player.getWorld(), player.getLocation());
        if (voice != null) {
            voice.say(player, LetheFloatingTextComponent.MOMENT_EVOLVE, null);
        }
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null} —— 播报这一处容忍它）。 */
    private Player selfPlayer() {
        SelfPort self = svc().self();
        return self == null ? null : self.player();
    }

    // ───────── 查询面（契约 §3.2 冻结；供遗忘 / 重塑技能与诊断读）─────────

    /** 当前等级是否已达到某一档。 */
    private boolean hasReached(int tier) {
        return getCurrentEvolutionLevel() >= tier;
    }

    /** 1 级『吟唱』：特殊值上限是否 +50（{@code true} = 本类已把上限抬到 {@code SANTE_MAX + 50}）。 */
    public boolean hasSanteMaxBonus() {
        return hasReached(TIER_CHANT);
    }

    /** 1 级『吟唱』：特殊值上限的固定增量（{@value #SANTE_MAX_BONUS}）。 */
    public int santeMaxBonus() {
        return SANTE_MAX_BONUS;
    }

    /** 2 级『共鸣』：共鸣圈触发上限是否已提升到 8。 */
    public boolean hasExtendedResonance() {
        return hasReached(TIER_RESONANCE);
    }

    /** 2 级『共鸣』：提升后的圈触发上限（{@value #EXTENDED_RESONANCE_CAP}）。 */
    public int extendedResonanceCap() {
        return EXTENDED_RESONANCE_CAP;
    }

    /** 3 级『无憾』：遗忘是否"记录并在归还时还原血量 / 特殊值"（消费者 = 遗忘技能）。 */
    public boolean hasFateRecall() {
        return hasReached(TIER_FATE);
    }

    /** 4 级『救赎』：重塑冷却是否减半（消费者 = 重塑技能）。 */
    public boolean hasReducedReshapeCooldown() {
        return hasReached(TIER_REDEMPTION);
    }

    /** 5 级『新生』：重塑吸血 / 圈内敌人反哺是否生效（消费者 = 重塑技能与共鸣圈）。 */
    public boolean hasNewLifeDrain() {
        return hasReached(TIER_NEW_LIFE);
    }
}
