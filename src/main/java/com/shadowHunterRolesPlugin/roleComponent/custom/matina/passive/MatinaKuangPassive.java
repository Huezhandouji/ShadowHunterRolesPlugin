package com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.MatinaRageVfx;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * 「狂躁牧师·马提娜」被动：**诉说苦怒**（狂暴值函数 {@code KUANG}）。
 *
 * <h2>狂暴值本体</h2>
 * 一个 <b>0..{@value #KUANG_MAX}</b> 的整数层数，本组件是它的<b>唯一持有者</b>：
 * <ul>
 *   <li><b>增加</b>：{@link #addKuang(int)} —— 每次<b>成功治疗</b>或<b>成功攻击</b> +1
 *       （主武器的命中由武器调它；海晶灯按"每有一名角色接受治疗 +1"；无人机每个治疗脉冲 +1）；</li>
 *   <li><b>衰减</b>：每秒（{@value #DECAY_PERIOD_TICKS} 刻）−1，下限 0；</li>
 *   <li><b>爆表保护</b>：已在上限就再被加点 ⇒ 返回 {@code false}（调用方据此不再继续结算），
 *       不抛异常、不静默溢出。</li>
 * </ul>
 *
 * <h2>阈值加成（需求 §4「狂暴指数」逐条）</h2>
 * <table border="1">
 *   <tr><th>层数</th><th>效果</th><th>实现落点</th></tr>
 *   <tr><td>1</td><td>每次攻击回复自己 8 点生命</td><td>{@link #applyAttackBonuses(Player, Player)} 经生命组件</td></tr>
 *   <tr><td>4</td><td>每次攻击回复自己 1 点能量</td><td>同上，经能量组件</td></tr>
 *   <tr><td>7</td><td>每次攻击回复自己 5 点 san 值</td><td>同上，经 SanTE 组件</td></tr>
 *   <tr><td>10</td><td>免疫缓慢（<b>不免疫自己技能带来的定身</b>）</td><td>{@link #refreshLayerAuras(Player)} 每刻摘掉身上的缓慢</td></tr>
 *   <tr><td>15</td><td>攻击额外造成 6 点真伤</td><td>{@link #attackTrueDamage()}（与 20 层<b>不叠加</b>）</td></tr>
 *   <tr><td>20</td><td>攻击额外造成 8 点真伤</td><td>同上（20 层取代 15 层）</td></tr>
 *   <tr><td>30</td><td>获得抗性 2</td><td>同上，经 buff 组件（等级 II）</td></tr>
 *   <tr><td>&gt;60</td><td>每秒判定：基础 10% 直接死亡，每高 1 层 +1%，上限 100%；死亡后狂暴清 0</td>
 *       <td>{@link #rollOverloadDeath(Player)}</td></tr>
 *   <tr><td>每 2 层</td><td>周身多一个环绕自身的红石粒子，从<b>脚底</b>逐个叠高
 *       （30 层时最高 2 格）</td><td>{@link #drawRageOrbit(Player)}</td></tr>
 * </table>
 *
 * <h2>口径申报（如实）</h2>
 * <ol>
 *   <li><b>"免疫缓慢"只挡缓慢药水效果</b>：每刻扫描玩家身上的 {@code SLOWNESS} 并移除。
 *       它<b>不会</b>移除「神罚」引导期的定身 —— 引导期用的是同一个缓慢类型，
 *       所以定身由引导组件<b>每帧重刷</b>（谁后写谁生效），这正是需求"但无法免疫自己技能带来的定身"。</li>
 *   <li><b>第 1 层的"回复 8 点生命"</b>：走生命组件的标准治疗入口 ⇒ 按<b>上限</b>截断
 *       （满血时这次回复不产生效果），与需求字面一致。
 *       <p>★ 此处修的是一个真 bug：旧实现是「上限临时 +8 → 回满 → 去掉上限 → 再扣 8」，
 *       净效果 = <b>把当前生命设成「上限 − 8」</b>（40 血时恒为 32）——
 *       血量 38 时反而会<b>掉到 32</b>，与"回复 8 点"完全相反。</li>
 *   <li><b>真伤不叠加</b>：15 与 20 层同时存在时，取 20 层的 8 点（需求明写"不与 20 层叠加"）。</li>
 *   <li><b>层数显示</b>：本组件只负责<b>数值</b>；玩家可见的层数展示在主武器的 lore 行
 *       （见 {@code MatinaMedicalShovelMainWeapon}）以及本组件自己维护的一条 bossbar 上。</li>
 * </ol>
 */
public class MatinaKuangPassive extends PassiveSkill implements OperationProvider {

    /** **本组件的登记 id**（★ 知识归属：组件自己）。 */
    public static final String ID = "matina_passive_kuang";

    /** 狂暴值上限（层数）。 */
    public static final int KUANG_MAX = 100;

    /** 衰减周期：每秒 1 层 = 20 刻。 */
    public static final int DECAY_PERIOD_TICKS = 20;

    /** 层数 1：每次攻击回复自己 8 点生命。 */
    public static final int LAYER_SELF_HEAL = 1;
    /** 层数 4：每次攻击回复自己 1 点能量。 */
    public static final int LAYER_ENERGY_GAIN = 4;
    /** 层数 7：每次攻击回复自己 5 点 san 值。 */
    public static final int LAYER_SANTE_GAIN = 7;
    /** 层数 10：免疫缓慢。 */
    public static final int LAYER_SLOW_IMMUNE = 10;
    /** 层数 15：攻击额外 6 点真伤。 */
    public static final int LAYER_TRUE_DAMAGE_LOW = 15;
    /** 层数 20：攻击额外 8 点真伤（取代 15 层）。 */
    public static final int LAYER_TRUE_DAMAGE_HIGH = 20;
    /** 层数 30：获得抗性 2。 */
    public static final int LAYER_RESISTANCE = 30;
    /** 暴走判定起点：高于该层数每秒掷一次死亡判定。 */
    public static final int OVERLOAD_THRESHOLD = 60;

    /** 第 1 层的生命回复量。 */
    private static final double ATTACK_SELF_HEAL = 8d;
    /** 第 4 层的能量回复量。 */
    private static final int ATTACK_ENERGY_GAIN = 1;
    /** 第 7 层的 san 值回复量。 */
    private static final int ATTACK_SANTE_GAIN = 5;
    /** 15 层真伤。 */
    private static final double TRUE_DAMAGE_LOW = 6d;
    /** 20 层真伤。 */
    private static final double TRUE_DAMAGE_HIGH = 8d;

    /** 抗性刷新的短时长（本组件每 20 刻刷一次 ⇒ 给足冗余）。 */
    private static final int RESISTANCE_REFRESH_TICKS = 60;
    /** 抗性等级：增幅 1 = 抗性 II。 */
    private static final int RESISTANCE_AMPLIFIER = 1;

    /** 环绕粒子的半径（格）。 */
    private static final double ORBIT_RADIUS = 0.9d;
    /**
     * **两颗粒子之间的层数间隔**（需求：每两个层数增加一个粒子）。
     * <p>⇒ 实际画出的粒子数 = {@code floor(kuang / 该值)}。
     */
    private static final int ORBIT_LAYERS_PER_PARTICLE = 2;
    /**
     * **满速档的层数**（需求：到达 30 层时旋转粒子最高到 2 格）。
     * <p>它与 {@link #ORBIT_LAYERS_PER_PARTICLE} 一起定义整条高度曲线：
     * 30 层 ⇒ 15 颗 ⇒ 最高那颗恰好在脚底上方 {@value #ORBIT_MAX_HEIGHT} 格。
     */
    private static final int ORBIT_FULL_LAYER = 30;
    /** **满速档的最高高度**（格；从脚底起算）。 */
    private static final double ORBIT_MAX_HEIGHT = 2.0d;
    /**
     * 每层抬高（格）——"逐个叠高"。
     * <p>由「30 层时最高 2 格」反解：最高那颗是第 {@code 30/2 - 1 = 14} 个间隙 ⇒ 步长 = 2/14。
     */
    private static final double ORBIT_STEP_Y =
            ORBIT_MAX_HEIGHT / (ORBIT_FULL_LAYER / (double) ORBIT_LAYERS_PER_PARTICLE - 1d);

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private SanTEComponent sante;
    private BuffComponent buff;

    /** ★ 真值：当前狂暴层数（唯一持有处）。 */
    private int kuang;

    /** 每秒衰减用的计时（刻）。 */
    private int decayCounter;

    /** 环绕粒子的相位（每帧推进 ⇒ 整圈都在转）。 */
    private double orbitPhase;

    /** 本组件的 bossbar（层数展示的一条；随组件生命周期显示与隐藏）。 */
    private BossBar bossbar;

    public MatinaKuangPassive(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**被动描述符**（无栏位 ⇒ 天然不占热键栏）。
     */
    public static final class Specification extends PassiveSkill.Specification<MatinaKuangPassive> {

        public Specification() {
            super(Component.text("诉说苦怒"),
                    Component.text("每次成功治疗或成功攻击增加1点狂暴值；狂暴值越高加成越多，每秒减少1层，高于60层时每秒可能直接死亡"));
            requires(VitalsComponent.class).requires(EnergyComponent.class)
                    .requires(SanTEComponent.class).requires(BuffComponent.class);
        }

        @Override
        public MatinaKuangPassive create(String id, ComponentServices services) {
            return new MatinaKuangPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);

        bossbar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        Player owner = svc().self().player();
        if (owner != null) {
            owner.showBossBar(bossbar);
        }
    }

    @Override
    public void stop() {
        Player owner = svc().self().player();
        if (owner != null && bossbar != null) {
            owner.hideBossBar(bossbar);
        }
        bossbar = null;
        kuang = 0;
        decayCounter = 0;
        orbitPhase = 0d;
    }

    // ───────── 每刻：衰减 / 阈值光环 / 暴走判定 / 粒子 / 显示 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            return;
        }

        //① 每秒 −1 层，并在此节拍上做"每秒一次"的两件事（超载判定 + 阈值光环刷新）
        decayCounter++;
        if (decayCounter >= DECAY_PERIOD_TICKS) {
            decayCounter = 0;
            if (kuang > 0) {
                kuang--;
            }
            rollOverloadDeath(owner);
            refreshLayerAuras(owner);
        }

        //② 免疫缓慢（每刻都要判：定身之外的一切缓慢都不该留住）
        if (kuang >= LAYER_SLOW_IMMUNE) {
            stripSlowness(owner);
        }

        //③ 每层一个环绕自身的红石粒子（逐个叠高）
        drawRageOrbit(owner);

        //④ bossbar 显示当前狂暴值
        refreshBossbar();
    }

    // ───────── 公开读 / 写口（同角色其它组件用） ─────────

    /** 当前狂暴层数（读口）。 */
    public int kuang() {
        return kuang;
    }

    /**
     * **增加狂暴值**（唯一写入入口）。
     *
     * @param amount 增加量（&le; 0 ⇒ 不做任何事）
     * @return 是否计入：{@code false} = 已在上限（本次增加被丢弃，调用方据此停止后续结算）
     */
    public boolean addKuang(int amount) {
        if (amount <= 0) {
            return false;
        }
        if (kuang >= KUANG_MAX) {
            return false;
        }
        kuang = Math.min(KUANG_MAX, kuang + amount);
        return true;
    }

    /** **成功治疗 / 成功攻击各记 1 层**（需求原话；语义 = {@code addKuang(1)}）。 */
    public boolean addKuang() {
        return addKuang(1);
    }

    /** 直接把狂暴值清 0（暴走死亡后 / 调试用）。 */
    public void clearKuang() {
        kuang = 0;
    }

    /** 直接写入（调试 / 运维用；内部 clamp 到 {@code [0, KUANG_MAX]}）。 */
    public void setKuang(int value) {
        kuang = Math.clamp(value, 0, KUANG_MAX);
    }

    // ───────── 供给其它组件的效果口 ─────────

    /** 第 1 层是否生效。 */
    public boolean hasSelfHeal() {
        return kuang >= LAYER_SELF_HEAL;
    }

    /** 第 10 层是否生效（免疫缓慢）。 */
    public boolean hasSlowImmunity() {
        return kuang >= LAYER_SLOW_IMMUNE;
    }

    /** 第 30 层是否生效（抗性 2）。 */
    public boolean hasResistance() {
        return kuang >= LAYER_RESISTANCE;
    }

    /** 当前攻击附带的**额外真伤**（15 层 6 点；20 层起 8 点，**不叠加**）。 */
    public double attackTrueDamage() {
        if (kuang >= LAYER_TRUE_DAMAGE_HIGH) {
            return TRUE_DAMAGE_HIGH;
        }
        if (kuang >= LAYER_TRUE_DAMAGE_LOW) {
            return TRUE_DAMAGE_LOW;
        }
        return 0d;
    }

    /**
     * **一次成功攻击的狂暴结算**（主武器命中后调用一次）。
     *
     * <p>逐条对应需求：① 层数 1 ⇒ 回复自己 8 点生命；② 层数 4 ⇒ 回复 1 点能量；
     * ③ 层数 7 ⇒ 回复 5 点 san 值；④ 层数 15/20 ⇒ 对<b>本次命中者</b>追加真伤（不叠加）。
     * <p>每次调用还会记 1 层狂暴（"每次成功攻击 +1"）。
     *
     * @param attacker 攻击者（= 本组件的自己）
     * @param victim   本次命中者（可为 {@code null}：只结算自身加成，不追加真伤）
     */
    public void applyAttackBonuses(Player attacker, Player victim) {
        if (attacker == null) {
            return;
        }
        if (kuang >= LAYER_SELF_HEAL) {
            healSelf(attacker, ATTACK_SELF_HEAL);
        }
        if (kuang >= LAYER_ENERGY_GAIN && energy != null) {
            energy.increase(ATTACK_ENERGY_GAIN);
        }
        if (kuang >= LAYER_SANTE_GAIN && sante != null) {
            sante.increase(ATTACK_SANTE_GAIN);
        }
        if (victim != null && vitals != null) {
            double extra = attackTrueDamage();
            if (extra > 0d) {
                vitals.trueDamage(victim, attacker, extra);
            }
        }
        addKuang();
    }

    // ───────── 内部：阈值光环 ─────────

    /** 每秒刷新一次"持续型"阈值加成（目前只有 30 层的抗性 2）。 */
    private void refreshLayerAuras(Player owner) {
        if (kuang >= LAYER_RESISTANCE && buff != null) {
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, RESISTANCE_REFRESH_TICKS, RESISTANCE_AMPLIFIER);
        }
    }

    /**
     * **免疫缓慢**：把玩家身上的 {@code SLOWNESS} 全部摘掉。
     * <p>★ 刻意**不区分**来源 —— 需求已明写"无法免疫自己技能带来的定身"，而"神罚"引导期
     * <b>每帧重刷</b>定身 ⇒ 本处摘掉后会被引导帧立刻写回 ⇒ 定身照常生效（谁后写谁生效）。
     */
    private void stripSlowness(Player owner) {
        if (owner.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            owner.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    // ───────── 内部：暴走判定 ─────────

    /**
     * **高于 60 层时的每秒死亡判定**（需求）：基础 10%，每高 1 层 +1%，上限 100%。
     *
     * <p>死亡经生命组件的真伤入口结算（保留击杀归属与游戏模式判定），随后<b>狂暴清 0</b>。
     * <p>概率以「狂暴 − 60 + 9」表达：61 层 = 10%，70 层 = 19%，n ≥ 151 层 = 100%
     * （本组件的上限 100 ⇒ 实际最大 49%）。
     */
    private void rollOverloadDeath(Player owner) {
        if (kuang <= OVERLOAD_THRESHOLD) {
            return;
        }
        int percent = Math.clamp(kuang - OVERLOAD_THRESHOLD + 9, 10, 100);
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= percent) {
            return;
        }
        if (vitals != null) {
            double health = Math.max(owner.getHealth(), 0d);
            vitals.trueDamage(owner, owner, health + 1000d);
        }
        clearKuang();
    }

    // ───────── 内部：生命回复 / 粒子 / 显示 ─────────

    /**
     * **回复 {@code amount} 点生命**（第 1 层的"每次攻击回复自己 8 点生命"）。
     *
     * <p>走生命组件的标准治疗入口（{@link VitalsComponent#heal(Player, double)}）：
     * 内部按目标<b>当前上限</b>截断 ⇒ 满血时这次回复不产生效果，与需求字面一致。
     *
     * <p>★ 修复记录：旧实现走的是「上限临时 +{@code amount} → 回满 → 去掉上限 → 再扣 {@code amount}」，
     * 净效果是**把当前生命设成「上限 − amount」**（40 血时恒为 32）⇒ 血量高于 32 时反而掉血。
     * 现改为标准治疗，语义与"回复 8 点生命"对齐，且不再往玩家身上挂任何临时属性修饰符。
     */
    private void healSelf(Player owner, double amount) {
        if (vitals == null || owner == null || amount <= 0d) {
            return;
        }
        vitals.heal(owner, amount);
    }

    /**
     * **狂暴环绕粒子**：起点在<b>角色脚底</b>，每 {@value #ORBIT_LAYERS_PER_PARTICLE} 层加一颗，
     * 逐颗抬高；30 层时最高一颗恰好在脚底上方 {@value #ORBIT_MAX_HEIGHT} 格。
     *
     * <p>高度曲线是**连续**的（不封顶）：层数超过 30 后粒子继续按同一 {@link #ORBIT_STEP_Y}
     * 往上排（100 层 ⇒ 50 颗 ⇒ 约 6.86 格高），所以"到 30 层最高 2 格"描述的是**那一刻**的形态，
     * 不是一条硬上限 —— 需求只钉死了 30 层这个采样点。
     */
    private void drawRageOrbit(Player owner) {
        int particles = kuang / ORBIT_LAYERS_PER_PARTICLE;
        if (particles <= 0) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        orbitPhase += 0.16d;
        if (orbitPhase > Math.PI * 2d) {
            orbitPhase -= Math.PI * 2d;
        }
        //★ 锚点 = 脚底（owner.getLocation() 的 y 就是脚底）⇒ 不再 +1.0 抬到腰间
        Location center = owner.getLocation().clone();
        MatinaRageVfx.rageOrbit(world, center, particles,
                orbitPhase, ORBIT_RADIUS, ORBIT_STEP_Y);
    }

    /** 刷新 bossbar 文案：狂暴层数 + 当前生效的阈值标签。 */
    private void refreshBossbar() {
        if (bossbar == null) {
            return;
        }
        StringBuilder tags = new StringBuilder();
        if (kuang >= LAYER_SELF_HEAL) tags.append(" 回血");
        if (kuang >= LAYER_ENERGY_GAIN) tags.append(" 回能");
        if (kuang >= LAYER_SANTE_GAIN) tags.append(" 回san");
        if (kuang >= LAYER_SLOW_IMMUNE) tags.append(" 免缓");
        if (kuang >= LAYER_RESISTANCE) tags.append(" 抗性2");
        double extra = attackTrueDamage();
        if (extra > 0d) {
            tags.append(" 真伤+").append((int) extra);
        }

        Component name = Component.text("狂暴 ").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD)
                .append(Component.text(kuang + "/" + KUANG_MAX).color(NamedTextColor.RED).decorate(TextDecoration.BOLD))
                .append(Component.text(tags.toString()).color(NamedTextColor.GOLD));
        if (kuang > OVERLOAD_THRESHOLD) {
            name = name.append(Component.text("  ！暴走判定中").color(NamedTextColor.DARK_RED)
                    .decorate(TextDecoration.BOLD));
        }
        bossbar.name(name);
        bossbar.progress(Math.clamp(kuang / (float) KUANG_MAX, 0f, 1f));
    }

    // ───────── 调试操作面（可选加入 OperationProvider 的那一条） ─────────

    /**
     * **组件操作面**：把外部字符串指令**薄适配**到本组件既有强类型方法。
     *
     * <pre>
     * value            读：当前狂暴层数
     * add &lt;int≥0&gt;      写：{@link #addKuang(int)} ⇒ 回写后值
     * set &lt;int≥0&gt;      写：{@link #setKuang(int)}（内部 clamp）⇒ 回写后值
     * clear            写：狂暴清 0 ⇒ 回写后值
     * tags             读：当前生效的阈值标签（逗号分隔；无 ⇒ "none"）
     * </pre>
     * 三态返回：{@code null} = 未识别 / 拒绝；非空串 = 规范化值。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) {
            return null;
        }
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return null;
        }
        switch (tokens[0]) {
            case "value" -> {
                return tokens.length == 1 ? Integer.toString(kuang) : null;
            }
            case "tags" -> {
                if (tokens.length != 1) {
                    return null;
                }
                StringBuilder tags = new StringBuilder();
                if (kuang >= LAYER_SELF_HEAL) tags.append("heal8,");
                if (kuang >= LAYER_ENERGY_GAIN) tags.append("energy1,");
                if (kuang >= LAYER_SANTE_GAIN) tags.append("sante5,");
                if (kuang >= LAYER_SLOW_IMMUNE) tags.append("slowImmune,");
                if (attackTrueDamage() > 0d) tags.append("trueDamage").append((int) attackTrueDamage()).append(',');
                if (kuang >= LAYER_RESISTANCE) tags.append("resistance2,");
                if (tags.length() == 0) {
                    return "none";
                }
                tags.setLength(tags.length() - 1);
                return tags.toString();
            }
            case "add", "set" -> {
                if (tokens.length != 2) {
                    return null;
                }
                int amount = parseNonNegative(tokens[1]);
                if (amount < 0) {
                    return null;
                }
                if ("add".equals(tokens[0])) {
                    addKuang(amount);
                } else {
                    setKuang(amount);
                }
                return Integer.toString(kuang);
            }
            case "clear" -> {
                if (tokens.length != 1) {
                    return null;
                }
                clearKuang();
                return Integer.toString(kuang);
            }
            default -> {
                return null;
            }
        }
    }

    /** 非负整数解析：非数字 / 负数 / 溢出 ⇒ {@code -1}（调用方据此拒绝）。 */
    private static int parseNonNegative(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /** 供探针 / 调试读：本组件当前是否在超载区。 */
    public boolean overloaded() {
        return kuang > OVERLOAD_THRESHOLD;
    }

    /** 供探针 / 调试读：本组件当前 bossbar 的纯文本（未显示 ⇒ 空串）。 */
    public String bossbarText() {
        if (bossbar == null) {
            return "";
        }
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(bossbar.name());
    }

    /**
     * **主武器 lore 行**（狂暴值的玩家可见展示）。
     * <p>主武器只调这一行静态方法即可把当前层数写进自己的 lore。
     */
    public static Component loreLineFor(int kuang) {
        return Component.text("狂暴：" + kuang + " 层").color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                .append(Component.text(kuang > OVERLOAD_THRESHOLD ? "（暴走判定中）" : "")
                        .color(NamedTextColor.DARK_RED));
    }
}
