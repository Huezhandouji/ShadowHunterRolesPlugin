package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BossbarRoleAttributesDisplayComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.skill.MikomakoBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.skill.MikomakoCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.skill.MikomakoUnconcernSkill;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 被动：**须狂命** —— SanTE 越低，增益越多；归零时进入 15 秒的「狂命」形态。
 *
 * <h2>十档阈值（逐条对应需求）</h2>
 * <table>
 *   <tr><th>SanTE</th><th>效果</th></tr>
 *   <tr><td>100（≥90 时有效）</td><td>所有技能 CD 每秒额外恢复 0.5 秒</td></tr>
 *   <tr><td>≤90</td><td>速度 I</td></tr>
 *   <tr><td>≤80</td><td>普攻 +2 物理 / +1 特殊值</td></tr>
 *   <tr><td>≤70</td><td>每秒 +1 能量</td></tr>
 *   <tr><td>≤60</td><td>抗性 I</td></tr>
 *   <tr><td>≤50</td><td>每秒 -1 生命（直接写血，不打断疾跑、不改 FOV）；三处治疗 +2</td></tr>
 *   <tr><td>≤40</td><td>圆弧斩 +1 格；普攻 15% 拉回 1 格</td></tr>
 *   <tr><td>≤30</td><td>普攻 20% 概率本次全部伤害 ×1.5</td></tr>
 *   <tr><td>≤20</td><td>漫不经心充能 2 层</td></tr>
 *   <tr><td>≤10</td><td>所有技能附带"敌人缓慢 I + 自身 4 点吸收"</td></tr>
 *   <tr><td>0</td><td>狂命爆发（15 秒）</td></tr>
 * </table>
 *
 * <h2>★★ 口径申报一：SanTE 由"命中敌人"扣，不由时间扣（2026-10-21 需求 4）</h2>
 * 每次对敌人造成伤害（普攻 / 三个技能 / 投掷，**不含被动**）⇒ 自己 **-3 SanTE**；
 * 记账在四个消耗方都依赖的 {@link MikomakoSanDrainComponent} 里（本被动不参与扣值，
 * 否则"被动 requires 技能、技能 requires 被动"会成环 ⇒ 整个角色静默消失）。
 * 推进速度因此 = 命中频率：约 33 次命中从满值走到 0。
 *
 * <h2>★★ 口径申报二：「归零」在本角色用 SanTE = 1 表达</h2>
 * 2026-10-30 框架放开了 SanTE 下限（每角色可配，默认 -999），因此 `set` 不再必然夹在
 * {@code [0, max]}；但本角色的**点火判据**仍沿用旧口径：{@code current <= 1} 即视为"归零"，
 * 狂命形态在 **SanTE = 1** 时点火，并在 15 秒内**钉住 1**（对外表现就是"归零"）。
 * <p><b>显示上做到 -999</b>：狂命期间本组件接管 bossbar，把名字写成
 * {@code SAN -999/100 ｜ 狂命 Ns}（内建那条被隐藏，见口径申报四）。
 *
 * <h2>★★ 口径申报四：SAN 显示 = 自绘 bossbar，内建那条只读反射隐藏</h2>
 * 内建 {@code BossbarRoleAttributesDisplayComponent} 每帧用 {@code sante.current()} 重写名字，
 * 且没有提供"显示值覆盖"的接口。为了不改框架，本组件在 {@code start()} 里：
 * 反射读出它私有字段 {@code bossbar} → {@code hideBossBar} → 再 {@code showBossBar} 自己那条；
 * 之后每 5 刻（内建每帧都在改名）重新隐藏一次，保证屏幕上只有一条。
 * 取不到（字段改名 / 组件缺失）就**不接管**，宁可按旧显示，也不出现两条 bossbar。
 *
 * <h2>★★ 口径申报三：惩罚期不刷增益（自持惩罚，不再依赖内建组件）</h2>
 * 狂命结束后由本组件自己接管 5 秒惩罚：{@code penaltyTicks} 期间逐刻钉 SanTE = 0、
 * **不刷任何档位增益**，并施加 STUN {@value #PENALTY_TICKS} 刻；倒计时归零时回满并解锁。
 *
 * <h2>★★ 口径申报五：默认归零惩罚已在装配点关闭（2026-10-31 修复）</h2>
 * 内建 {@code DefaultSanTEZeroPunishment} 的触发是**变更监听**：只要 SanTE 在某一击里
 * 从 {@code >0} 掉到阈值，它就会在同一刻接管（眩晕 + 钉值 + 回满），把我们的
 * 「0.5 秒引导 → 15 秒狂命」顶掉 —— 症状就是"一受到特殊值伤害就进惩罚"。
 * <p>框架后来（2026-10-30）开放了**每角色 SanTE 配置**，其中
 * {@code SanTEComponent.Configuration#punishmentEnabled()} 可以关闭默认惩罚组件；
 * 因此鹤子现在的做法是：**装配点直接声明 {@code punishmentEnabled(false)}** ——
 * 默认惩罚组件根本不会注册，0 状态完全由本组件负责。
 * <p>历史包袱说明：旧实现靠只读反射取出内建组件的 {@code santeListener} 并按引用移除来"解绑"；
 * 该 workaround 已随配置能力上线而删除（框架源码一行未改）。
 */
public class MikomakoMadnessPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_madness_passive";

    /** 进入狂命前的引导时长（刻）= 0.5 秒（需求 3）。 */
    public static final int WINDUP_TICKS = 10;

    /** 引导期间缓慢 V ⇒ amplifier 4。 */
    public static final int WINDUP_SLOW_AMPLIFIER = 4;

    /** 引导期间禁止跳跃：把 JUMP_STRENGTH 从 0.42 压到 0 的修饰符键。 */
    private static final NamespacedKey NO_JUMP_KEY = KeyFactory.Registry.of("mikomako_no_jump");

    /** 每秒判定的周期（刻）。 */
    public static final int SECOND_TICKS = 20;

    /** 100 档：每秒减多少 CD（0.5 秒 = 10 刻）。 */
    public static final int CD_REFUND_PER_SECOND = 10;

    /** 狂命形态时长（刻）= 15 秒。 */
    public static final int BURST_TICKS = 300;

    /** 狂命结束后的自持惩罚时长（刻）= 5 秒（替代内建归零惩罚）。 */
    public static final int PENALTY_TICKS = 100;

    /** 狂命期间额外速度等级：速度 II ⇒ amplifier 1。 */
    public static final int BURST_SPEED_AMPLIFIER = 1;

    /** 狂命爆发的范围（需求 r=12）。 */
    public static final double BURST_RADIUS = 12d;

    /** 爆发对每个敌人的真实伤害（需求 5）。 */
    public static final double BURST_TRUE_DAMAGE = 5d;

    /** 爆发对每个敌人造成的黑暗时长（需求 1 秒）。 */
    public static final int BURST_DARKNESS_TICKS = 20;

    /** 每命中一个敌人：回血（需求 10）。 */
    public static final double BURST_HEAL_PER_ENEMY = 10d;

    /** 每命中一个敌人：回能（需求 20）。 */
    public static final int BURST_ENERGY_PER_ENEMY = 20;

    private SanTEComponent sante;
    private VitalsComponent vitals;
    private BuffComponent buff;
    private EnergyComponent energy;
    private FactionComponent faction;
    private MikomakoChargeComponent charge;
    private MikomakoFloatingTextComponent voice;
    private MikomakoUnconcernSkill unconcern;
    private MikomakoBloodySlashSkill bloodySlash;
    private MikomakoCircleSlashSkill circleSlash;

    private int secondTimer;
    private int burstTicks;
    private boolean inBurst;
    private boolean inWindup;
    private int windupTicks;
    private int windupPhase;
    /** 狂命结束后的自持惩罚倒计时（>0 = 惩罚期，钉 SanTE=0 且不刷增益）。 */
    private int penaltyTicks;
    private final List<TextUtil.Instance> windupTexts = new ArrayList<>();

    /** 本角色自绘的 BossBar（隐藏内建那条，见口径申报四）。 */
    private BossBar sanBar;
    private BossBar builtinBar;
    private int barTimer;

    public MikomakoMadnessPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符：被动不占栏位。 */
    public static final class Specification extends PassiveSkill.Specification<MikomakoMadnessPassive> {

        public Specification() {
            super(Component.text("须狂命"),
                    List.of(
                            Component.text("SanTE 越低，增益越多（十档）"),
                            Component.text("≥90：技能 CD 每秒 -0.5 秒"),
                            Component.text("≤90 速度 I / ≤80 普攻加伤 / ≤70 回能"),
                            Component.text("≤60 抗性 I / ≤50 流血换治疗加成"),
                            Component.text("≤40 圆弧斩 +1 格 / ≤30 暴击 / ≤20 双充能"),
                            Component.text("≤10 技能附控制余波 / 0：狂命爆发 15 秒")
                    ));
            requires(SanTEComponent.class).requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(EnergyComponent.class).requires(FactionComponent.class)
                    .requires(BossbarRoleAttributesDisplayComponent.class)
                    .requires(MikomakoChargeComponent.class)
                    .requires(MikomakoUnconcernSkill.class)
                    .requires(MikomakoBloodySlashSkill.class)
                    .requires(MikomakoCircleSlashSkill.class);
            requiresOptional(MikomakoFloatingTextComponent.class);
        }

        @Override
        public MikomakoMadnessPassive create(String id, ComponentServicesPort services) {
            return new MikomakoMadnessPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        sante = getComponent(SanTEComponent.class);
        vitals = getComponent(VitalsComponent.class);
        buff = getComponent(BuffComponent.class);
        energy = getComponent(EnergyComponent.class);
        faction = getComponent(FactionComponent.class);
        charge = getComponent(MikomakoChargeComponent.class);
        voice = getComponent(MikomakoFloatingTextComponent.class);
        unconcern = getComponent(MikomakoUnconcernSkill.class);
        bloodySlash = getComponent(MikomakoBloodySlashSkill.class);
        circleSlash = getComponent(MikomakoCircleSlashSkill.class);

        installSanBar(svc().self().player());
    }

    @Override
    public void update() {
        Player player = svc().self().player();
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        tickSanBar(player);

        if (inWindup) {
            tickWindup(player);
            return;
        }

        if (inBurst) {
            tickBurst(player);
            return;
        }

        // 自持惩罚期（替代内建归零惩罚）：钉 0、不刷增益，结束后回满
        if (penaltyTicks > 0) {
            tickPenalty();
            return;
        }

        int san = sante.current();

        // 点火：SanTE 到 1 即视为"归零"（见口径申报二）—— 先 0.5 秒引导，再进入 -999 状态
        if (san <= 1) {
            startWindup(player);
            return;
        }

        if (++secondTimer < SECOND_TICKS) {
            return;
        }
        secondTimer = 0;
        tickSecond(player, san);
    }

    /** 每秒：掉 SanTE、回能、流血、减 CD、刷新速度/抗性。 */
    private void tickSecond(Player player, int san) {
        // 2026-10-21 需求 4：不再按时间掉 SanTE（改由 MikomakoSanDrainComponent 按命中扣）
        if (MikomakoMadness.energyRegen(san)) {
            energy.increase(1);
        }
        if (MikomakoMadness.bloodDrain(san)) {
            double health = player.getHealth();
            if (health > 0d) {
                // 直接写血：不触发受伤事件 ⇒ 不打断疾跑、不改 FOV（需求逐字）；掉到 0 就是真的死
                player.setHealth(Math.max(0d, health - 1d));
            }
        }
        if (MikomakoMadness.cdBoost(san)) {
            refundCooldowns();
        }
        if (MikomakoMadness.speed(san)) {
            buff.applyPotionEffect(PotionEffectType.SPEED, SECOND_TICKS * 2, 0, true, false);
        }
        if (MikomakoMadness.resistance(san)) {
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, SECOND_TICKS * 2, 0, true, false);
        }
    }

    /** 100 档：三个技能各把剩余冷却削 0.5 秒。 */
    private void refundCooldowns() {
        refund(unconcern);
        refund(bloodySlash);
        refund(circleSlash);
    }

    private void refund(com.shadowHunterRolesPlugin.roleComponent.ActiveComponent skill) {
        if (skill == null) {
            return;
        }
        int remaining = skill.remainingCooldownTicks();
        if (remaining > 0) {
            skill.startCooldown(Math.max(0, remaining - CD_REFUND_PER_SECOND));
        }
    }

    // ───────── SanTE 显示（-999 只存在于显示层）─────────

    /**
     * 接管 SAN 显示：隐藏内建 bossbar，改用本角色自绘的一条（见口径申报四）。
     *
     * <p>取不到内建那条就**不接管**（宁可显示 1/100，也不要屏幕上出现两条 bossbar）。
     */
    private void installSanBar(Player player) {
        if (player == null) {
            return;
        }
        builtinBar = resolveBuiltinBar();
        if (builtinBar == null) {
            return;
        }
        sanBar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        player.hideBossBar(builtinBar);
        player.showBossBar(sanBar);
    }

    /** 内建 bossbar 是私有字段：只读反射取一次（不改框架、不改它的行为）。 */
    private BossBar resolveBuiltinBar() {
        try {
            Object component = getComponent(BossbarRoleAttributesDisplayComponent.class);
            if (component == null) {
                return null;
            }
            Field field = BossbarRoleAttributesDisplayComponent.class.getDeclaredField("bossbar");
            field.setAccessible(true);
            Object value = field.get(component);
            return value instanceof BossBar bar ? bar : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** 每 5 刻刷一次：狂命期间写死 {@code SAN -999/100}，其余时候与内建同格式。 */
    private void tickSanBar(Player player) {
        if (sanBar == null) {
            return;
        }
        if (++barTimer < 5) {
            return;
        }
        barTimer = 0;

        if (builtinBar != null) {
            // 内建那条每帧仍在改名 ⇒ 这里反复隐藏（幂等、极廉价），保证屏幕上只有一条 SAN
            player.hideBossBar(builtinBar);
        }

        if (inBurst || inWindup) {
            String suffix = inWindup
                    ? " ｜ 引导"
                    : " ｜ 狂命 " + Math.max(0, (BURST_TICKS - burstTicks) / SECOND_TICKS) + "s";
            sanBar.name(Component.text("SAN -999/" + sante.max(), NamedTextColor.RED)
                    .decorate(TextDecoration.BOLD)
                    .append(Component.text(suffix, NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
            return;
        }

        sanBar.name(Component.text("SAN " + sante.current() + "/" + sante.max(), NamedTextColor.YELLOW)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" || ", NamedTextColor.WHITE).decorate(TextDecoration.BOLD))
                .append(Component.text("ENERGY " + energy.current() + "/" + energy.max(),
                        NamedTextColor.GREEN).decorate(TextDecoration.BOLD)));
    }

    /** 是否正处于狂命形态（含 0.5 秒引导；供其它组件读口 / 诊断）。 */
    public boolean isBursting() {
        return inBurst || inWindup;
    }

    /** 狂命形态剩余刻数（诊断 / 显示读口）。 */
    public int burstRemainingTicks() {
        return inBurst ? Math.max(0, BURST_TICKS - burstTicks) : 0;
    }

    @Override
    public void stop() {
        Player player = svc().self().player();
        if (player != null) {
            clearNoJump(player);
            if (sanBar != null) {
                player.hideBossBar(sanBar);
            }
        }
        retireWindupTexts();
        inWindup = false;
        sanBar = null;
        builtinBar = null;
    }

    // ───────── 狂命引导（0.5 秒）─────────

    /**
     * 需求 3：进入 -999 之前先做 **0.5 秒引导** —— 缓慢 V + 禁止跳跃 + 环绕文字 + 伤害范围提示，
     * 引导结束才真正进入 15 秒狂命。
     */
    private void startWindup(Player player) {
        inWindup = true;
        windupTicks = 0;
        windupPhase = 0;
        sante.set(1);
        buff.clearDebuff();
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, WINDUP_TICKS + 6, WINDUP_SLOW_AMPLIFIER, true, false);
        applyNoJump(player);
        spawnWindupTexts(player);
        MikomakoVfx.burstRange(player.getLocation(), BURST_RADIUS, 0);
        MikomakoSound.burstCharge(player);
    }

    private void tickWindup(Player player) {
        windupTicks++;
        sante.set(1);
        if (windupTicks % 2 == 0) {
            MikomakoVfx.burstRange(player.getLocation(), BURST_RADIUS, ++windupPhase);
        }
        advanceWindupTexts();
        if (windupTicks >= WINDUP_TICKS) {
            inWindup = false;
            clearNoJump(player);
            retireWindupTexts();
            startBurst(player);
        }
    }

    /** 环绕鹤子周身的 8 个单字，逐个浮现（0.5 秒即散）。 */
    private void spawnWindupTexts(Player player) {
        retireWindupTexts();
        String[] words = {"疯", "狂", "命", "妄", "斩", "梦", "痛", "笑"};
        float yaw = player.getLocation().getYaw();
        TextUtil.Look look = new TextUtil.Look(0xFFFFC83C, false, true, 0xFFFF2A2A, false, true);
        for (int i = 0; i < words.length; i++) {
            double angle = Math.toRadians(i * 45d);
            Location at = player.getLocation().clone().add(Math.cos(angle) * 1.25d,
                    0.55d + (i % 3) * 0.5d, Math.sin(angle) * 1.25d);
            TextUtil.Instance instance = TextUtil.spawn(at, yaw + 180f - i * 45f, words[i],
                    TextUtil.STYLE_EMPHASIS, 120, 14, 1.15f, look);
            if (instance != null) {
                windupTexts.add(instance);
            }
        }
    }

    private void advanceWindupTexts() {
        Iterator<TextUtil.Instance> iterator = windupTexts.iterator();
        while (iterator.hasNext()) {
            TextUtil.Instance instance = iterator.next();
            if (!TextUtil.advance(instance)) {
                TextUtil.retire(instance);
                iterator.remove();
            }
        }
    }

    private void retireWindupTexts() {
        for (TextUtil.Instance instance : windupTexts) {
            TextUtil.retire(instance);
        }
        windupTexts.clear();
    }

    /** 禁跳：把 JUMP_STRENGTH 从 0.42 压到 0（0.5 秒后摘掉）。 */
    private void applyNoJump(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(NO_JUMP_KEY);
        attribute.addModifier(new AttributeModifier(NO_JUMP_KEY, -0.42d,
                AttributeModifier.Operation.ADD_NUMBER));
    }

    private void clearNoJump(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (attribute != null) {
            attribute.removeModifier(NO_JUMP_KEY);
        }
    }

    // ───────── 狂命形态 ─────────

    private void startBurst(Player player) {
        inBurst = true;
        penaltyTicks = 0;
        burstTicks = 0;
        secondTimer = 0;

        buff.clearDebuff();
        sante.set(1);
        if (charge != null) {
            charge.clear();
        }

        MikomakoVfx.diamondBurst(player.getLocation(), BURST_RADIUS);
        MikomakoSound.burst(player);
        if (voice != null) {
            voice.sayMoment(player, MikomakoFloatingTextComponent.MOMENT_BURST);
        }
        if (sanBar == null) {
            // 反射取不到内建 bossbar 时的兜底：至少在字幕上打出 -999
            player.showTitle(net.kyori.adventure.title.Title.title(
                    Component.empty(),
                    Component.text("SAN -999 / " + sante.max(), NamedTextColor.RED)
                            .decorate(TextDecoration.BOLD),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                            java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(300))));
        }

        for (Player victim : player.getLocation().getNearbyPlayers(BURST_RADIUS)) {
            if (victim == null || victim.equals(player) || !faction.isHostileTo(victim.getUniqueId())) {
                continue;
            }
            buff.applyPotionEffectTo(victim, PotionEffectType.DARKNESS, BURST_DARKNESS_TICKS, 0, true, false);
            vitals.trueDamage(victim, player, BURST_TRUE_DAMAGE);
            vitals.heal(player, BURST_HEAL_PER_ENEMY);
            energy.increase(BURST_ENERGY_PER_ENEMY);
        }
    }

    /** 狂命形态逐刻：钉住 SanTE=1、清控制、华丽粒子、每秒灵魂呼啸。 */
    private void tickBurst(Player player) {
        burstTicks++;
        sante.set(1);
        buff.clearDebuff();

        if (burstTicks % 5 == 0) {
            MikomakoVfx.burstAura(player);
        }
        // 2026-10-22 需求：-999 状态期间额外速度 II（每 20 刻续一次，覆盖下一个窗口）
        if (burstTicks % SECOND_TICKS == 1) {
            buff.applyPotionEffect(PotionEffectType.SPEED, SECOND_TICKS * 2, BURST_SPEED_AMPLIFIER, true, false);
        }
        if (burstTicks % SECOND_TICKS == 0) {
            MikomakoSound.soulWail(player);
        }
        if (burstTicks >= BURST_TICKS) {
            inBurst = false;
            // 自持惩罚（内建归零惩罚已被本角色解绑，见口径申报五）：钉 0 + 眩晕 5 秒，结束回满
            sante.set(0);
            buff.add(BuffType.STUN, PENALTY_TICKS);
            penaltyTicks = PENALTY_TICKS;
        }
    }

    /** 惩罚期逐刻：钉住 SanTE=0，倒计时结束回满并解锁。 */
    private void tickPenalty() {
        penaltyTicks--;
        sante.set(0);
        if (penaltyTicks <= 0) {
            penaltyTicks = 0;
            sante.set(sante.max());
        }
    }
}
