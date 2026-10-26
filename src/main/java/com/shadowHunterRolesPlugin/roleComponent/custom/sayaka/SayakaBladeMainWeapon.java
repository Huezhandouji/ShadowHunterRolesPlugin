package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 「芹泽沙耶香 · 瓦尔基里」主武器：**影切【下界合金剑】**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>丢出武器（Q）</b>切换拔刀 / 纳刀形态（形态在 actionbar 显示）；</li>
 *   <li><b>右键</b> = 武器充能：消耗全部笼手充能（{@link SayakaGauntletPassive#takeAllCharges()}），
 *       每次耗 {@value #CHARGE_ENERGY_COST} 点能量；<b>切换形态立即损失已充能层数</b>；</li>
 *   <li><b>拔刀</b>：左键 = {@value #MELEE_PHYSICAL} 物理 + {@value #MELEE_TRUE_DAMAGE} 真实伤害 + 2 失衡；
 *       对处于失衡爆发的敌人每次额外回复自身 1 生命；</li>
 *   <li><b>纳刀</b>：进入需 {@value #SHEATHE_PREP_TICKS} 刻（0.3 秒）准备，期间<b>缓慢 4</b>；
 *       纳刀后<b>缓慢 4</b> 并持 {@code RESISTANCE I}；居合对路径 {@value #IAI_RANGE} 米敌人
 *       {@value #IAI_PHYSICAL} 物理 + {@value #IAI_IMBALANCE} 失衡；</li>
 *   <li><b>居合触发</b>：被敌人攻击（防御）/ 左键（主动）；</li>
 *   <li><b>充能加成</b>：拔刀表 / 纳刀防御表 / 纳刀主动表逐级递增（见下方常量）；
 *       3 层充能的纳刀防御成功后<b>免疫任何攻击 1 秒</b>。</li>
 *   <li><b>2026-10-17 需求</b>：近战 CD 改 <b>0.1 秒</b>（{@value #MELEE_COOLDOWN_TICKS} 刻）；
 *       纳刀后屏幕出现<b>斜杠蓄力条</b>（<b>0.3 秒</b>，{@value #IAI_CHARGE_TICKS} 刻，与现实居合同长）
 *       + 环绕影尘 + 每 2 刻一次的递增复合音；蓄满且闸门就绪 ⇒ 被动在 actionbar 打「□居合准备完毕□」；
 *       <b>一次居合攻击后纳刀状态解除</b>；<b>主手换掉影切（切到别的格子）也立刻解除纳刀</b>。</li>
 *   <li><b>2026-10-18 需求</b>：居合判定改为<b>三维点—射线</b>（随准星俯仰 ⇒ 能打高处敌人）；
 *       纳刀<b>不能跳跃</b>（缓慢 IV + 负跳跃）；居合蓄力提示<b>整行居中</b>且带上笼手层数；
 *       出刀 / 命中的粒子加档更华丽。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：三种计时器互不共用（不占框架冷却）</h2>
 * 近战（{@link #nextAttackTick}）、居合（{@link #nextIaiTick}）都是<b>本类自己的刻闸门</b>，
 * <b>不调用</b> {@code startCooldown()}。理由：施放管道（{@code MainWeaponListener#cast}）
 * 对所有入口（右键 / 左键 / Q）统一查 {@code isCoolingDown()} —— 若把近战冷却登记成组件冷却，
 * 命中后的 0.1 秒里 <b>连 Q 换形态、左键居合都会被挡</b>（形态武器不能这样）。
 * 因此本类的冷却维度全部自持；描记符里的冷却声明值仅作文案（框架对主武器无统一冷却入口）。
 *
 * <h2>★★ 口径申报二：法术伤害 ⇒ 真实伤害（2026-10-14 需求更新）</h2>
 * 需求里的「法术伤害」最初按既有角色口径落在 <b>SanTE（特殊值）削减</b>上；
 * <b>2026-10-14 用户明确改判为真实伤害</b>（{@code vitals.trueDamage}）。
 * 本角色体系内共有三处调用点（全部同步改判）：拔刀左键 {@value #MELEE_TRUE_DAMAGE} 点、
 * 连斩每段 +1 点、榴弹稳态场每秒 1 点。
 *
 * <h2>★★ 口径申报三：纳刀「减速」的实现（2026-10-17 需求 3 改判）</h2>
 * 旧实现是 {@code SLOWNESS 255 + JUMP_BOOST 128}（完全不能走、不能跳）。现在改为
 * <b>{@code SLOWNESS amplifier }{@value #SHEATHE_SLOW_AMPLIFIER}</b> = <b>缓慢 IV</b>，每刻刷新；
 * 解除时显式摘除该类型（同 {@code HunterStealthSkill} 的既有口径：只摘本组件施加的）。
 * <p>★ FOV 说明：Minecraft 的视野宽度由「移动速度属性」驱动，<b>任何减速药水都会让 FOV 略微收窄</b>
 * ——插件侧没有独立的 FOV 控制口。相比旧值 255（视野几乎缩成望远镜），缓慢 IV 的收窄小得多；
 * 若要求"零 FOV 变化"，只能把 {@link #SHEATHE_SLOW_AMPLIFIER} 调到 0（即不减速）。
 * <p>大招期间「移动限制解除」⇒ 本组件对大招风暴态跳过减速与自身抗性（由大招组件负责抗性 II）。
 *
 * <h2>★★ 口径申报四：「3 层充能 + 防御成功 ⇒ 免疫 1 秒」</h2>
 * 判据 = <b>防御触发</b>的居合且当次消耗的充能层数 = 3（"防御成功"即防御动作成立的那一刻，
 * 不要求斩击命中）。实现 = {@code setInvulnerable(true)} {@value #IMMUNE_TICKS} 刻，
 * 到期 / 死亡 / 离线 / {@code stop()} 四路回收（自愈：每刻核对到期）。
 */
public class SayakaBladeMainWeapon extends MainWeapon {

    /** 本组件的登记 id。 */
    public static final String ID = "sayaka_blade_main_weapon";

    // ───────── 拔刀 ─────────

    /** 近战闸门（刻）—— 0.1 秒（2026-10-17 需求 6；口径申报一：本类自持，不占框架冷却）。 */
    public static final int MELEE_COOLDOWN_TICKS = 2;

    /** 拔刀左键物理伤害（需求：8 点）。 */
    public static final double MELEE_PHYSICAL = 8d;

    /** 拔刀左键真实伤害（2026-10-14 需求更新：原「2 点法术」改为 2 点真实伤害）。 */
    public static final double MELEE_TRUE_DAMAGE = 2d;

    /** 拔刀左键失衡积累（需求：2 点）。 */
    public static final int MELEE_IMBALANCE = 2;

    /** 对失衡爆发敌人攻击的额外回血（需求：每次 1 点）。 */
    public static final double BURST_HIT_HEAL = 1d;

    /** 攻击速度统一值（压掉原版攻击冷却；先摘后加防叠加）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;

    /** 攻速修饰符键。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("sayaka_blade_attack_speed");

    // ───────── 纳刀 / 居合 ─────────

    /** 进入纳刀的准备时间（刻）—— 0.3 秒（现实居合 ≈ 0.2~0.3 秒）。 */
    public static final int SHEATHE_PREP_TICKS = 6;

    /**
     * 居合**蓄力条**从零到满的时长（刻）。
     *
     * <p>★ 2026-10-17 需求 3：<b>与现实的居合时间一致</b> —— 由 1 秒（20 刻）压到
     * <b>0.3 秒（6 刻）</b>，并与 {@link #SHEATHE_PREP_TICKS} 同步 ⇒ 纳刀到位的同一刻就蓄满，
     * "按 Q → 可以居合"总共 0.3 秒（现实居合的一次拔刀斩约 0.2~0.3 秒）。
     * <p>这条时长仍然只服务**提示**（屏幕蓄力条 / 环绕影尘 / 递增音效），不改伤害判定：
     * 真正的闸门是 {@link #nextIaiTick}。
     */
    public static final int IAI_CHARGE_TICKS = 6;

    /** 蓄力音的节拍（刻）—— 每 2 刻一次，共 3 次（0..2 档，音高逐级升高；0.3 秒内形成一次"疾蓄"）。 */
    public static final int IAI_CHARGE_SOUND_STEP_TICKS = 2;

    /** 居合路径长度（米/格）—— 需求：「对路径 8 米上敌方」。 */
    public static final double IAI_RANGE = 8.0d;

    /**
     * 居合走廊的**侧向半径**（格）—— 到「眼睛 → 准星方向」这条射线的垂直距离。
     *
     * <p>★ 2026-10-18 需求 6：判定改为**三维**（随准星俯仰）—— 旧实现只投影水平面 +
     * 单独的竖直容差 2.5 格，导致<b>抬头也打不到高处的敌人</b>。现在射线跟着准星走：
     * 抬多少度就打多少度，垂直距离用同一个半径收口。
     */
    public static final double IAI_LATERAL = 1.6d;

    /** 居合基础物理伤害（需求：8 点）。 */
    public static final double IAI_PHYSICAL = 8d;

    /** 居合基础失衡积累（需求：5 点）。 */
    public static final int IAI_IMBALANCE = 5;

    /** 居合内部闸门（刻）—— 0.4 秒（现实居合收刀再构约 0.4 秒；频繁被打也不会连环触发）。 */
    public static final int IAI_COOLDOWN_TICKS = 8;

    /**
     * 纳刀期间的缓慢等级（2026-10-17 需求 3：由"完全定身"改为<b>缓慢 4</b>）。
     *
     * <p>★ 实现为 {@code SLOWNESS} 的 amplifier，值 = 等级 − 1 ⇒ <b>3</b>。
     * <p>★ 如实申报：Minecraft 的 FOV 由「移动速度属性」驱动，任何减速（药水 / 属性修饰符）
     * 都会让视野略微收窄 —— 这是原版耦合，插件侧没有独立的 FOV 控制口。
     * 相比旧值 255（速度≈0 ⇒ 视野几乎缩成望远镜），缓慢 4 的收窄幅度小得多；
     * 若仍然不想要任何 FOV 变化，只能把本常量改成 0（= 纳刀完全不减速，也就不再有"定身感"）。
     */
    public static final int SHEATHE_SLOW_AMPLIFIER = 3;

    /**
     * 纳刀期间的**负跳跃**增幅（2026-10-18 需求 5：纳刀状态下不能跳跃）。
     *
     * <p>现代版本的负等级跳跃 = 起跳被抑制（与旧实现同一手法，见工程既有口径）。
     * 与缓慢 IV 叠加：能小幅挪动、但跳不起来。
     */
    public static final int SHEATHE_JUMP_AMPLIFIER = 128;

    // ───────── 武器充能 ─────────

    /** 每次武器充能的能量消耗（需求：5 点）。 */
    public static final int CHARGE_ENERGY_COST = 5;

    /** 充能层数上限（= 笼手上限 3）。 */
    public static final int MAX_CHARGE_LEVEL = 3;

    /** 拔刀充能：额外失衡积累（1/2/3 层 ⇒ 5/7/9）。 */
    public static final int[] DRAWN_BONUS_IMBALANCE = {5, 7, 9};

    /** 拔刀充能：回复自身生命（1/2/3 层 ⇒ 4/6/8）。 */
    public static final double[] DRAWN_BONUS_HEAL = {4d, 6d, 8d};

    /** 拔刀充能：周围敌人真实伤害（1/2/3 层 ⇒ 2/3/4）。 */
    public static final double[] DRAWN_BONUS_TRUE = {2d, 3d, 4d};

    /** 拔刀充能的范围真伤半径（需求：周围 4 格）。 */
    public static final double DRAWN_BONUS_RADIUS = 4.0d;

    /** 纳刀·防御触发充能：额外失衡（1/2/3 层 ⇒ 9/18/30）。 */
    public static final int[] SHEATH_DEFENSE_BONUS_IMBALANCE = {9, 18, 30};

    /** 纳刀·防御触发充能：真实伤害（1/2/3 层 ⇒ 2/3/4）。 */
    public static final double[] SHEATH_DEFENSE_BONUS_TRUE = {2d, 3d, 4d};

    /** 纳刀·主动触发充能：额外失衡（1/2/3 层 ⇒ 5/9/15）。 */
    public static final int[] SHEATH_ACTIVE_BONUS_IMBALANCE = {5, 9, 15};

    /** 3 层防御成功后的免疫时长（刻）—— 需求：1 秒。 */
    public static final int IMMUNE_TICKS = 20;

    /** 技能二「防御判定」窗口时长（刻）—— 需求：5 秒（由技能二通过 {@link #armDefenseWindow} 开启）。 */
    public static final int DEFENSE_WINDOW_TICKS = 100;

    /** 防御居合后「锁定敌人方向」的有效期（刻）—— 供技能二下一次突进定向。 */
    public static final int LOCK_TICKS = 100;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private FactionComponent faction;

    /** 受伤订阅句柄（{@code start()} 注册、{@code stop()} 按引用撤销；触发 {@link #onDamaged}）。 */
    private VitalsComponent.DamagedListener damagedEntry;
    private SayakaGauntletPassive passive;
    private SayakaFloatingTextComponent voice;

    /** 渲染组件（只用来请求重绘：形态 / 充能都在非冷却期变化）。 */
    private HotbarRenderComponent render;

    // ───────── 运行期状态 ─────────

    /** 形态。 */
    public enum Stance {
        /** 拔刀。 */
        DRAWN,
        /** 纳刀。 */
        SHEATHED
    }

    private Stance stance = Stance.DRAWN;

    /** 纳刀准备剩余刻（> 0 = 蓄势中，期间禁移）。 */
    private int prepRemainingTicks;

    /** 居合蓄力累计刻（0..{@value #IAI_CHARGE_TICKS}；**只服务提示**，不参与闸门）。 */
    private int iaiChargeTick;

    /** 本次纳刀是否已播过"蓄满"提示（边沿判据 ⇒ 不每刻重复播）。 */
    private boolean iaiReadyAnnounced;

    /** 武器充能层数（0..{@value #MAX_CHARGE_LEVEL}）。 */
    private int chargeLevel;

    /** 近战闸门到期刻。 */
    private int nextAttackTick;

    /** 居合闸门到期刻。 */
    private int nextIaiTick;

    /** 免疫到期刻。 */
    private int invulnUntilTick;

    /** 当前这份免疫是不是本组件施加的（回收只回收自己那份）。 */
    private boolean invulnApplied;

    /** 当前禁移效果是不是本组件施加的（解除时只摘自己那两类型）。 */
    private boolean slowApplied;

    /** 当前抗性 I 是不是本组件施加的。 */
    private boolean resistanceApplied;

    /** 技能二：防御判定窗口到期刻（0 = 无窗口）。 */
    private int defenseWindowUntil;

    /** 技能二：本窗口内的那一次居合是否已用掉（需求：「每次刷新后仅一次居合动作」）。 */
    private boolean defenseWindowUsed;

    /** 防御居合后锁定的敌人（技能二下一次突进优先朝它）。 */
    private java.util.UUID lockedEnemyId;

    /** 锁定到期刻。 */
    private int lockedUntilTick;

    /** 剑刃风暴（大招）接管标志：禁移与自身抗性让位给大招组件。 */
    private boolean stormActive;

    /** 进入风暴前的形态（风暴结束恢复）。 */
    private Stance stanceBeforeStorm = Stance.DRAWN;

    /** 上一帧外观签名（变了才请求重绘）。 */
    private String lastRenderSignature = "";

    public SayakaBladeMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 主武器名（黑红主题：暗红加粗「影切」）。 */
    public static Component weaponName() {
        return Component.text("影切").color(NamedTextColor.DARK_RED).decorate(TextDecoration.BOLD);
    }

    /** 本组件的描述符（下界合金剑 = 需求指定材质）。 */
    public static final class Specification extends MainWeapon.Specification<SayakaBladeMainWeapon> {

        public Specification() {
            super(weaponName(),
                    List.of(
                            Component.text("丢出武器（Q）：切换「拔刀 / 纳刀」形态（actionbar 显示）"),
                            Component.text("右键：武器充能——消耗全部笼手充能注入影切（每次 5 能量）；切换形态立即损失充能层数"),
                            Component.text("拔刀 · 左键：8 点物理 + 2 点真实伤害，积累 2 点失衡（近战 CD 0.1 秒）；对失衡爆发的敌人额外回复自身 1 生命"),
                            Component.text("拔刀 · 充能兑现：额外失衡 +5/7/9；回复 4/6/8 生命；周围 4 格敌人 2/3/4 真实伤害（层数越高特效越华丽）"),
                            Component.text("纳刀：0.3 秒准备（期间缓慢 4）；纳刀后缓慢 4 并持抗性 I；0.3 秒斜杠蓄力条 + 递增音效"),
                            Component.text("纳刀 · 居合：对路径 8 米敌人 5 失衡 + 8 物理（被敌人攻击 / 左键触发）；蓄满提示「居合准备完毕」"),
                            Component.text("纳刀 · 一次居合后 / 把主武器换到其他格子，都会立刻解除纳刀"),
                            Component.text("纳刀 · 充能兑现（防御触发）：额外 9/18/30 失衡与 2/3/4 真伤；（主动触发）：额外 5/9/15 失衡"),
                            Component.text("3 层充能的纳刀防御成功后：免疫任何攻击 1 秒")
                    ),
                    Material.NETHERITE_SWORD,
                    MELEE_COOLDOWN_TICKS);
            requires(VitalsComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class)
                    .requires(FactionComponent.class).requires(SayakaGauntletPassive.class)
                    .requires(SayakaFloatingTextComponent.class);
        }

        @Override
        public SayakaBladeMainWeapon create(String id, ComponentServicesPort services) {
            return new SayakaBladeMainWeapon(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        passive = svc().components().get(SayakaGauntletPassive.class);
        voice = svc().components().get(SayakaFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        //订阅受伤：纳刀中被敌对玩家攻击 ⇒ 触发防御居合（见 onDamaged）
        if (vitals != null && damagedEntry == null) {
            damagedEntry = vitals.addDamagedListener(this, event -> onDamaged(event.source(), event.amount()));
        }
        resetRuntime();
    }

    @Override
    public void stop() {
        if (damagedEntry != null && vitals != null) {
            vitals.removeDamagedListener(damagedEntry);
        }
        damagedEntry = null;
        Player owner = svc().self().player();
        releaseOwnerEffects(owner);
        resetRuntime();
        vitals = null;
        energy = null;
        buff = null;
        faction = null;
        passive = null;
        voice = null;
        render = null;
    }

    /** 复位全部运行期状态。 */
    private void resetRuntime() {
        stance = Stance.DRAWN;
        prepRemainingTicks = 0;
        iaiChargeTick = 0;
        iaiReadyAnnounced = false;
        chargeLevel = 0;
        nextAttackTick = 0;
        nextIaiTick = 0;
        invulnUntilTick = 0;
        invulnApplied = false;
        slowApplied = false;
        resistanceApplied = false;
        defenseWindowUntil = 0;
        defenseWindowUsed = false;
        lockedEnemyId = null;
        lockedUntilTick = 0;
        stormActive = false;
        stanceBeforeStorm = Stance.DRAWN;
        lastRenderSignature = "";
    }

    /** 摘掉本组件对玩家施加过的三类副作用（免疫 / 缓慢 / 抗性）。 */
    private void releaseOwnerEffects(Player owner) {
        if (owner == null) {
            invulnApplied = false;
            slowApplied = false;
            resistanceApplied = false;
            return;
        }
        if (invulnApplied) {
            owner.setInvulnerable(false);
            invulnApplied = false;
        }
        if (slowApplied) {
            owner.removePotionEffect(PotionEffectType.SLOWNESS);
            owner.removePotionEffect(PotionEffectType.JUMP_BOOST);
            slowApplied = false;
        }
        if (resistanceApplied) {
            if (buff != null) {
                buff.removePotionEffect(PotionEffectType.RESISTANCE);
            } else {
                owner.removePotionEffect(PotionEffectType.RESISTANCE);
            }
            resistanceApplied = false;
        }
    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        int now = Bukkit.getCurrentTick();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            //主体离场：把自己那份副作用收干净（防"死亡时挂着无敌 / 禁移"）
            if (owner != null && owner.isOnline()) {
                releaseOwnerEffects(owner);
            } else {
                invulnApplied = false;
                slowApplied = false;
                resistanceApplied = false;
            }
            prepRemainingTicks = 0;
            iaiChargeTick = 0;
            iaiReadyAnnounced = false;
            //死亡 / 离场：形态回拔刀（否则复活后还黏着"纳刀"的缓慢，蓄力条也会重挂）
            stanceBeforeStorm = Stance.DRAWN;
            if (!stormActive) {
                stance = Stance.DRAWN;
            }
            if (passive != null) {
                passive.publishIai(false, 0d, false);   //离场 / 死亡：把屏幕上的蓄力条收掉
            }
            return;
        }
        //⓪ 主手换掉影切 ⇒ 立刻停止纳刀（需求 3：换到物品栏其他格子就解除）
        if (!isHoldingMainWeapon(owner)) {
            cancelSheathe(owner);
        }
        //① 纳刀准备推进
        if (prepRemainingTicks > 0 && --prepRemainingTicks == 0) {
            stance = Stance.SHEATHED;
            SayakaSound.sheathed(owner.getWorld(), owner.getLocation());
            requestRepaint();
        }
        //①.5 居合蓄力（屏幕条 / 环绕影尘 / 递增复合音）—— 只做提示，不改闸门
        advanceIaiCharge(owner, now);
        //② 免疫到期回收（自愈：不管是谁把时间推到这儿的）
        if (invulnApplied && now >= invulnUntilTick) {
            owner.setInvulnerable(false);
            invulnApplied = false;
        }
        //③ 纳刀 / 蓄势的状态维持（缓慢 4 + 抗性 I）
        maintainStanceEffects(owner, now);
        //④ HUD 推给被动（actionbar / subtitle 的唯一发布者是它）
        if (passive != null) {
            passive.publishBladeState(hudLabel(), chargeLevel);
            boolean charging = iaiCharging();
            passive.publishIai(charging, iaiChargeProgress(iaiChargeTick), isIaiReady(now));
        }
        //⑤ 外观变了才请求重绘
        requestRenderIfChanged();
    }

    /**
     * 纳刀 / 蓄势期间：<b>缓慢 4</b>（不再是"完全定身"）+ <b>不能跳跃</b> + 抗性 I
     * （按 10 刻节拍刷新，40 刻时长防闪断）。
     *
     * <p>★ 2026-10-17 需求 3：把旧的 {@code SLOWNESS 255}（完全不能走）换成
     * {@code SLOWNESS amplifier 3}（缓慢 IV）—— 还能挪动，只是明显变慢。
     * <p>★ 2026-10-18 需求 5：跳跃另外压掉（{@code JUMP_BOOST 128} 负等级），
     * 所以纳刀是"能走、不能跳"，而不是"完全钉住"。
     */
    private void maintainStanceEffects(Player owner, int now) {
        boolean locked = (stance == Stance.SHEATHED && !stormActive) || prepRemainingTicks > 0;
        if (locked) {
            owner.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 10,
                    SHEATHE_SLOW_AMPLIFIER, true, false, false));
            owner.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 10,
                    SHEATHE_JUMP_AMPLIFIER, true, false, false));
            slowApplied = true;
        } else if (slowApplied) {
            owner.removePotionEffect(PotionEffectType.SLOWNESS);
            owner.removePotionEffect(PotionEffectType.JUMP_BOOST);
            slowApplied = false;
        }
        //抗性 I：纳刀且不在风暴时才由本组件负责
        boolean wantResistance = stance == Stance.SHEATHED && prepRemainingTicks == 0 && !stormActive;
        if (wantResistance) {
            if (!resistanceApplied || now % 10 == 0) {
                if (buff != null) {
                    buff.applyPotionEffect(PotionEffectType.RESISTANCE, 40, 0);
                }
            }
            resistanceApplied = true;
        } else if (resistanceApplied) {
            if (buff != null) {
                buff.removePotionEffect(PotionEffectType.RESISTANCE);
            }
            resistanceApplied = false;
        }
    }

    // ───────── 纳刀蓄力提示（需求 1）─────────

    /** 是否处于"纳刀 / 蓄势"（决定蓄力条要不要显示）。风暴期间由大招接管 ⇒ 不显示。 */
    private boolean iaiCharging() {
        return !stormActive && (stance == Stance.SHEATHED || prepRemainingTicks > 0);
    }

    /** 居合是否**此刻可放**（蓄满 + 纳刀到位 + 内部闸门已过）。 */
    private boolean isIaiReady(int now) {
        return iaiCharging() && prepRemainingTicks == 0
                && iaiChargeComplete(iaiChargeTick) && now >= nextIaiTick;
    }

    /**
     * 推进蓄力提示：进度条 + 世界粒子 + 每 4 刻一次递增复合音；蓄满播一次"就绪"。
     *
     * <p>★ 与真实闸门解耦：即使蓄力条满了（1 秒），若 {@link #nextIaiTick} 未到
     * （例如刚打完一次居合又立刻纳刀），也不会显示"准备完毕"—— 显示与判定永远一致。
     */
    private void advanceIaiCharge(Player owner, int now) {
        if (!iaiCharging()) {
            iaiChargeTick = 0;
            iaiReadyAnnounced = false;
            return;
        }
        if (iaiChargeTick < IAI_CHARGE_TICKS) {
            iaiChargeTick++;
            if (iaiChargeTick % IAI_CHARGE_SOUND_STEP_TICKS == 0) {
                //第 4/8/12/16/20 刻各一响，档位 0..4（音高逐级升高）
                SayakaSound.iaiChargeTick(owner.getWorld(), owner.getLocation(),
                        iaiChargeSoundStep(iaiChargeTick));
            }
        }
        if (now % 2 == 0) {
            SayakaVfx.iaiCharge(owner.getWorld(), owner.getLocation(),
                    iaiChargeProgress(iaiChargeTick), now);
        }
        if (!iaiReadyAnnounced && isIaiReady(now)) {
            iaiReadyAnnounced = true;
            SayakaVfx.iaiReady(owner.getWorld(), owner.getLocation());
            SayakaSound.iaiReady(owner.getWorld(), owner.getLocation());
        }
    }

    /**
     * **解除纳刀**（2026-10-17 需求 1：一次居合攻击后纳刀状态被解除）。
     *
     * <p>带"拔刀"音效与影尘反馈（这是玩家主动结束纳刀的那一刻）。
     * 风暴期间不解除（大招本身要纳刀态）。
     */
    private void releaseSheathe(Player owner) {
        if (stance != Stance.SHEATHED || stormActive) {
            return;
        }
        dropSheatheState(owner, true);
    }

    /**
     * **换掉主武器 ⇒ 立刻停止纳刀**（2026-10-17 需求 3：「比如换到物品栏的其他格子」）。
     *
     * <p>与 {@link #releaseSheathe} 的区别：这条路**不播"拔刀"音效** —— 玩家只是切了格子，
     * 快速轮换物品栏时不该一串乱响；但状态与副作用（缓慢 / 抗性 / 蓄力条）必须同刻收干净。
     */
    private void cancelSheathe(Player owner) {
        if (stormActive || (stance != Stance.SHEATHED && prepRemainingTicks <= 0)) {
            return;
        }
        dropSheatheState(owner, false);
    }

    /** 清掉纳刀 / 蓄势的全部状态与副作用；{@code withFeedback} = 播"拔刀"音效 + 影尘。 */
    private void dropSheatheState(Player owner, boolean withFeedback) {
        stance = Stance.DRAWN;
        prepRemainingTicks = 0;
        iaiChargeTick = 0;
        iaiReadyAnnounced = false;
        if (owner != null) {
            if (slowApplied) {
                owner.removePotionEffect(PotionEffectType.SLOWNESS);
                owner.removePotionEffect(PotionEffectType.JUMP_BOOST);
                slowApplied = false;
            }
            if (resistanceApplied) {
                if (buff != null) {
                    buff.removePotionEffect(PotionEffectType.RESISTANCE);
                } else {
                    owner.removePotionEffect(PotionEffectType.RESISTANCE);
                }
                resistanceApplied = false;
            }
            if (withFeedback) {
                SayakaSound.drawn(owner.getWorld(), owner.getLocation());
                SayakaVfx.stanceToggle(owner.getWorld(), owner.getLocation(), false);
            }
        }
        requestRepaint();
    }

    /**
     * **主手是否还拿着影切**。
     *
     * <p>★ 必须走 {@link MainWeapon.Utils#getWeaponId} —— 主武器写的是 {@code MAIN_WEAPON_KEY}，
     * 与技能的 {@code SKILL_KEY} 不是同一个键；用错会把"拿着影切"误判成 false，
     * 于是每刻都取消纳刀（纳刀永远起不来）。
     */
    private boolean isHoldingMainWeapon(Player owner) {
        return getId().equals(MainWeapon.Utils.getWeaponId(owner.getInventory().getItemInMainHand()));
    }

    // ───────── 攻击（拔刀近战）─────────

    /**
     * 拔刀左键攻击（{@code MainWeaponListener#onAttackPlayer} 是唯一投递点）。
     *
     * <p>顺序（不可交换）：自判冷却 → 形态/蓄势闸门 → 8 物理 → 2 真伤 → 2 失衡
     * → 爆发敌人回血 → 充能兑现（失衡 / 回血 / 范围真伤）→ 清无敌帧 → 观感。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        Player owner = svc().self().player();
        Player victim = signal == null ? null : signal.victim();
        if (owner == null || victim == null || vitals == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (!canUse()) {
            return;
        }
        if (prepRemainingTicks > 0) {
            return;     //蓄势中不挥刀
        }
        if (stance == Stance.SHEATHED) {
            //纳刀形态下左键命中敌人 = 主动居合（朝该敌人；闸门在 resolveIai 内部）
            resolveIai(owner, aimDirection(owner, victim.getLocation()), false, null);
            return;
        }
        if (now < nextAttackTick) {
            return;     //口径申报一：近战闸门自持
        }
        nextAttackTick = now + MELEE_COOLDOWN_TICKS;

        //① 8 物理 + 2 真实伤害
        vitals.physicalDamage(victim, owner, MELEE_PHYSICAL);
        vitals.trueDamage(victim, owner, MELEE_TRUE_DAMAGE);
        //② 2 点失衡
        SayakaImbalance.add(victim.getUniqueId(), MELEE_IMBALANCE, now, owner.getUniqueId());
        //③ 对处于失衡爆发的敌人：额外回血 1
        if (SayakaImbalance.isBursting(victim.getUniqueId(), now)) {
            vitals.heal(owner, BURST_HIT_HEAL);
        }
        //④ 充能兑现（若本击携带）
        int lvl = chargeLevel;
        if (lvl > 0) {
            chargeLevel = 0;
            SayakaImbalance.add(victim.getUniqueId(), drawnBonusImbalance(lvl), now, owner.getUniqueId());
            vitals.heal(owner, drawnBonusHeal(lvl));
            applyDrawnChargeAoe(owner, victim, lvl);
            SayakaVfx.chargeConsumed(owner.getWorld(), victim.getLocation().clone().add(0d, 1d, 0d), lvl);
            SayakaSound.chargeConsumed(owner.getWorld(), victim.getLocation());
            requestRepaint();
        }
        //⑤ 连打必需：清受击无敌帧
        victim.setNoDamageTicks(0);
        //⑥ 观感（2026-10-17 需求 4：按本次兑现层数分档 —— lvl 越高越华丽）
        World world = owner.getWorld();
        if (world != null) {
            SayakaVfx.drawnSlash(world, victim.getLocation().clone().add(0d, 1d, 0d), lvl);
            SayakaSound.meleeHit(world, victim.getLocation());
            SayakaVfx.imbalanceHit(world, victim.getLocation().clone().add(0d, 1.2d, 0d));
            SayakaSound.imbalanceHit(world, victim.getLocation());
        }
        //⑦ 语音（主武器命中）
        if (voice != null) {
            voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_WEAPON_HIT);
        }
    }

    /** 拔刀充能的「周围 4 格敌人 2/3/4 真实伤害」（以被击者为心，不含被击者本人）。 */
    private void applyDrawnChargeAoe(Player owner, Player primary, int level) {
        World world = primary.getWorld();
        if (world == null || vitals == null) {
            return;
        }
        double damage = drawnBonusTrue(level);
        for (Player other : world.getNearbyPlayers(primary.getLocation(), DRAWN_BONUS_RADIUS)) {
            if (other == null || other.equals(owner) || other.equals(primary)
                    || !isAlive(other) || !isHostile(other)) {
                continue;
            }
            vitals.trueDamage(other, owner, damage);
            other.setNoDamageTicks(0);
        }
        SayakaVfx.aoeCleave(world, primary.getLocation(), DRAWN_BONUS_RADIUS, level);
    }

    // ───────── 施放入口 ─────────

    @Override
    public void onCast(CastSignal signal) {
        if (signal == null) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !canUse()) {
            return;
        }
        switch (signal.trigger()) {
            case RIGHT_CLICK -> onRightClick(owner);
            case LEFT_CLICK -> onLeftClick(owner);
            case DROP -> onDrop(owner);
        }
    }

    /** 右键：武器充能（消耗全部笼手充能 + 5 能量）。 */
    private void onRightClick(Player owner) {
        if (stormActive || prepRemainingTicks > 0) {
            return;
        }
        if (passive == null) {
            return;
        }
        if (passive.charges() <= 0) {
            SayakaSound.chargeDenied(owner.getWorld(), owner.getLocation());
            return;
        }
        if (energy == null || !energy.tryConsume(CHARGE_ENERGY_COST)) {
            SayakaSound.chargeDenied(owner.getWorld(), owner.getLocation());   //"每次武器充能后消耗 5 点能量"
            if (voice != null) {
                voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_ENERGY_OUT);
            }
            return;
        }
        int taken = passive.takeAllCharges();
        if (taken <= 0) {
            SayakaSound.chargeDenied(owner.getWorld(), owner.getLocation());
            return;
        }
        chargeLevel = Math.min(MAX_CHARGE_LEVEL, taken);
        Location mid = owner.getLocation().clone().add(0d, 1d, 0d);
        SayakaVfx.chargeLoad(owner.getWorld(), mid, owner.getEyeLocation().getDirection(), chargeLevel);
        SayakaSound.chargeLoad(owner.getWorld(), owner.getLocation(), chargeLevel);
        requestRepaint();
    }

    /** 左键（挥空 / 点方块）：纳刀时 = 主动居合。 */
    private void onLeftClick(Player owner) {
        if (stormActive) {
            return;
        }
        if (stance == Stance.SHEATHED && prepRemainingTicks == 0) {
            resolveIai(owner, aimDirection(owner, null), false, null);
        }
    }

    /** Q：切换形态（风暴期间由大招接管，忽略）。 */
    private void onDrop(Player owner) {
        if (stormActive) {
            return;
        }
        if (prepRemainingTicks > 0) {
            //蓄势中再按 = 取消，回到拔刀
            prepRemainingTicks = 0;
            SayakaSound.drawn(owner.getWorld(), owner.getLocation());
            requestRepaint();
            return;
        }
        if (stance == Stance.DRAWN) {
            prepRemainingTicks = SHEATHE_PREP_TICKS;
            chargeLevel = 0;    //需求：切换形态立即损失所有已充能层数
            iaiChargeTick = 0;
            iaiReadyAnnounced = false;
            SayakaSound.sheathePrep(owner.getWorld(), owner.getLocation());
            SayakaVfx.stanceToggle(owner.getWorld(), owner.getLocation(), true);
        } else {
            stance = Stance.DRAWN;
            chargeLevel = 0;
            iaiChargeTick = 0;
            iaiReadyAnnounced = false;
            SayakaSound.drawn(owner.getWorld(), owner.getLocation());
            SayakaVfx.stanceToggle(owner.getWorld(), owner.getLocation(), false);
        }
        requestRepaint();
    }

    // ───────── 居合 ─────────

    /**
     * **居合斩**（防御触发 / 主动触发共用；路径 = 8 米长廊）。
     *
     * @param defensive 是否由"被敌人攻击"触发（决定充能加成表与免疫判定）
     * @param attacker  防御触发的攻击者（用于技能二的"锁定敌人方向"）
     */
    private void resolveIai(Player owner, Vector axis, boolean defensive, Player attacker) {
        int now = Bukkit.getCurrentTick();
        if (now < nextIaiTick) {
            return;     //居合内部闸门
        }
        if (axis == null) {
            axis = aimDirection(owner, null);
        }
        if (axis == null) {
            return;
        }
        int lvl = chargeLevel;
        chargeLevel = 0;
        List<Player> hits = collectCorridorHits(owner, axis, IAI_RANGE);
        int imbalance = IAI_IMBALANCE + (defensive ? defenseBonusImbalance(lvl) : activeBonusImbalance(lvl));
        double bonusTrue = defensive ? defenseBonusTrue(lvl) : 0d;
        for (Player victim : hits) {
            if (vitals != null) {
                vitals.physicalDamage(victim, owner, IAI_PHYSICAL);
                if (bonusTrue > 0d) {
                    vitals.trueDamage(victim, owner, bonusTrue);
                }
            }
            SayakaImbalance.add(victim.getUniqueId(), imbalance, now, owner.getUniqueId());
            victim.setNoDamageTicks(0);
            //2026-10-18 需求 4：居合命中的粒子（比拔刀更华丽，层数越高越炸）
            SayakaVfx.iaiHit(victim.getWorld(), victim.getLocation().clone().add(0d, 1.0d, 0d), lvl);
        }
        nextIaiTick = now + IAI_COOLDOWN_TICKS;
        //观感：刀痕从自身到路径末端（**沿准星的三维方向**，抬头时刀痕也向上）
        World world = owner.getWorld();
        if (world != null) {
            Location from = owner.getLocation().clone();
            Location to = from.clone().add(axis.clone().multiply(IAI_RANGE));
            SayakaVfx.iaiSlash(world, from, to, defensive, lvl);
            SayakaSound.iai(world, owner.getLocation(), defensive);
            if (lvl > 0) {
                SayakaSound.chargeConsumed(world, owner.getLocation());
            }
        }
        //3 层充能 + 防御成功 ⇒ 免疫任何攻击 1 秒（口径申报四）
        if (grantsImmunity(defensive, lvl)) {
            owner.setInvulnerable(true);
            invulnApplied = true;
            invulnUntilTick = now + IMMUNE_TICKS;
            SayakaVfx.immuneShield(world, owner.getLocation(), now);
            SayakaSound.immuneShield(world, owner.getLocation());
        }
        //技能二的防御窗口：消费 + 锁定攻击者方向（"居合完成后可以直接锁定敌人方向"）
        if (defensive && defenseWindowUntil > now) {
            defenseWindowUsed = true;
            if (attacker != null) {
                lockedEnemyId = attacker.getUniqueId();
                lockedUntilTick = now + LOCK_TICKS;
            }
        }
        //语音：只给"主动居合 + 命中"（防御居合与受击同刻抢节流 ⇒ 只留受击台词，见语音组件申报三）
        if (!defensive && !hits.isEmpty() && voice != null) {
            voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_WEAPON_HIT);
        }
        //需求 1：一次居合攻击后解除纳刀（风暴期间由大招接管，不解除）
        releaseSheathe(owner);
        if (lvl > 0) {
            requestRepaint();
        }
    }

    /** 收集居合走廊内的敌对目标（三维点—射线判定，**随准星俯仰**，工程长廊口径的 3D 版）。 */
    private List<Player> collectCorridorHits(Player owner, Vector axis, double range) {
        List<Player> hits = new ArrayList<>();
        Location eye = owner.getEyeLocation();
        if (eye.getWorld() == null) {
            return hits;
        }
        for (Player candidate : eye.getWorld().getNearbyPlayers(eye, range + 2.0d)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate) || !isHostile(candidate)) {
                continue;
            }
            //目标取**身体中心**（脚底 +1）做点—射线判定 ⇒ 站在台阶 / 高处也不会因脚底偏差漏判
            Location body = candidate.getLocation().clone().add(0d, 1.0d, 0d);
            if (insideIaiCorridor(body.getX() - eye.getX(), body.getY() - eye.getY(), body.getZ() - eye.getZ(),
                    axis.getX(), axis.getY(), axis.getZ())) {
                hits.add(candidate);
            }
        }
        return hits;
    }

    // ───────── 受伤订阅（防御居合触发点）─────────

    /**
     * 受伤 ⇒ 触发"防御居合"（{@code start()} 里经 {@code VitalsComponent#addDamagedListener}
     * 登记的监听在结算之后投递；改不了量，只用来触发）。
     *
     * <p>触发条件（同时成立）：来源是敌对玩家、自身处于<b>纳刀</b>（或技能二的防御窗口 / 风暴态）、
     * 居合闸门已就绪。落点方向 = 朝攻击者。
     */
    private void onDamaged(Player source, double amount) {
        if (source == null) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return;
        }
        if (!isHostile(source)) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        boolean sheathedReady = stance == Stance.SHEATHED && prepRemainingTicks == 0;
        boolean windowActive = defenseWindowUntil > now && !defenseWindowUsed;
        if (!sheathedReady && !windowActive) {
            return;
        }
        if (now < nextIaiTick) {
            return;
        }
        resolveIai(owner, aimDirection(owner, source.getLocation()), true, source);
    }

    // ───────── 对外面（技能二 / 大招）─────────

    /** 技能二：开启/刷新「防御判定」窗口（5 秒，一次窗口内至多一次防御居合）。 */
    public void armDefenseWindow(int nowTick) {
        defenseWindowUntil = nowTick + DEFENSE_WINDOW_TICKS;
        defenseWindowUsed = false;
    }

    /** 技能二：取"被防御居合锁定的敌人"（供下一次突进定向；过期/离线自动失效）。 */
    public Player lockedEnemy(int nowTick) {
        if (lockedEnemyId == null || nowTick > lockedUntilTick) {
            lockedEnemyId = null;
            return null;
        }
        Player target = Bukkit.getPlayer(lockedEnemyId);
        if (target == null || !target.isOnline() || target.isDead() || target.getHealth() <= 0d) {
            lockedEnemyId = null;
            return null;
        }
        return target;
    }

    /** 大招：进入剑刃风暴（强制纳刀、解除禁移、让位抗性）。 */
    public void beginStorm() {
        if (stormActive) {
            return;
        }
        stormActive = true;
        stanceBeforeStorm = stance;
        stance = Stance.SHEATHED;
        prepRemainingTicks = 0;
        chargeLevel = 0;
        iaiChargeTick = 0;
        iaiReadyAnnounced = false;
        Player owner = svc().self().player();
        if (owner != null) {
            //解除禁移立刻生效（不让玩家多挨 1 刻的缓慢）
            owner.removePotionEffect(PotionEffectType.SLOWNESS);
            owner.removePotionEffect(PotionEffectType.JUMP_BOOST);
            slowApplied = false;
        }
    }

    /** 大招：结束剑刃风暴（恢复原形态；抗性交还给本组件的正常维持逻辑）。 */
    public void endStorm() {
        if (!stormActive) {
            return;
        }
        stormActive = false;
        stance = stanceBeforeStorm == null ? Stance.DRAWN : stanceBeforeStorm;
        prepRemainingTicks = 0;
        Player owner = svc().self().player();
        if (owner != null && buff != null) {
            //大招期间由大招组件刷的抗性 II 由本组件接管：先摘掉，随后的正常维持逻辑会按形态补抗性 I
            buff.removePotionEffect(PotionEffectType.RESISTANCE);
            resistanceApplied = false;
        }
        requestRepaint();
    }

    /** 大招：是否处于剑刃风暴。 */
    public boolean isStormActive() {
        return stormActive;
    }

    // ───────── 闸门 / 外观 ─────────

    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    /** HUD / 名称用的形态标签。 */
    private String hudLabel() {
        if (stormActive) {
            return "剑刃风暴";
        }
        if (prepRemainingTicks > 0) {
            return "纳刀·蓄势";
        }
        return stance == Stance.SHEATHED ? "纳刀" : "拔刀";
    }

    /**
     * 主武器物品：攻速统一 100（先摘后加）；携带充能层数时发光；名称带形态后缀。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
        meta.setEnchantmentGlintOverride(chargeLevel > 0);
        Component suffix = Component.text(" 「" + hudLabel() + "」").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
        if (chargeLevel > 0) {
            suffix = suffix.append(Component.text(" 充能x" + chargeLevel, NamedTextColor.RED, TextDecoration.BOLD));
        }
        meta.displayName(weaponName().append(suffix));
        stack.setItemMeta(meta);
        return stack;
    }

    /** 外观签名变了才请求重绘（形态 / 蓄势 / 充能都在"非冷却期"变化 ⇒ 必须自己驱动）。 */
    private void requestRenderIfChanged() {
        if (render == null) {
            return;
        }
        String signature = stance + "/" + prepRemainingTicks + "/" + chargeLevel + "/" + stormActive;
        if (!signature.equals(lastRenderSignature)) {
            lastRenderSignature = signature;
            render.requestRepaint();
        }
    }

    private void requestRepaint() {
        lastRenderSignature = "";
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 纯函数（离线可测）─────────

    /** 拔刀充能：额外失衡（0 层 ⇒ 0；1/2/3 ⇒ 5/7/9）。 */
    public static int drawnBonusImbalance(int level) {
        return tableOf(DRAWN_BONUS_IMBALANCE, level);
    }

    /** 拔刀充能：回复生命（0 层 ⇒ 0；1/2/3 ⇒ 4/6/8）。 */
    public static double drawnBonusHeal(int level) {
        return tableOf(DRAWN_BONUS_HEAL, level);
    }

    /** 拔刀充能：范围真实伤害（0 层 ⇒ 0；1/2/3 ⇒ 2/3/4）。 */
    public static double drawnBonusTrue(int level) {
        return tableOf(DRAWN_BONUS_TRUE, level);
    }

    /** 纳刀防御触发：额外失衡（0 层 ⇒ 0；1/2/3 ⇒ 9/18/30）。 */
    public static int defenseBonusImbalance(int level) {
        return tableOf(SHEATH_DEFENSE_BONUS_IMBALANCE, level);
    }

    /** 纳刀防御触发：真实伤害（0 层 ⇒ 0；1/2/3 ⇒ 2/3/4）。 */
    public static double defenseBonusTrue(int level) {
        return tableOf(SHEATH_DEFENSE_BONUS_TRUE, level);
    }

    /** 纳刀主动触发：额外失衡（0 层 ⇒ 0；1/2/3 ⇒ 5/9/15）。 */
    public static int activeBonusImbalance(int level) {
        return tableOf(SHEATH_ACTIVE_BONUS_IMBALANCE, level);
    }

    /** 是否签发 1 秒免疫（口径申报四：防御触发 且 消耗满 3 层）。 */
    public static boolean grantsImmunity(boolean defensive, int level) {
        return defensive && level >= MAX_CHARGE_LEVEL;
    }

    /** 蓄力进度（纯函数，0..1）：{@code elapsed / IAI_CHARGE_TICKS}。 */
    public static double iaiChargeProgress(int elapsedTicks) {
        if (elapsedTicks <= 0) {
            return 0d;
        }
        return Math.min(1d, elapsedTicks / (double) IAI_CHARGE_TICKS);
    }

    /** 蓄力是否已满（纯函数）。 */
    public static boolean iaiChargeComplete(int elapsedTicks) {
        return elapsedTicks >= IAI_CHARGE_TICKS;
    }

    /**
     * 蓄力音档位（纯函数，0..2）：第 2/4/6 刻分别是 0/1/2 档。
     *
     * <p>3 档对应 3 次音（0.3 秒内），音高逐级升高 —— "逐步升高的复合蓄力音"。
     */
    public static int iaiChargeSoundStep(int elapsedTicks) {
        if (elapsedTicks < IAI_CHARGE_SOUND_STEP_TICKS) {
            return 0;
        }
        return Math.min(2, elapsedTicks / IAI_CHARGE_SOUND_STEP_TICKS - 1);
    }

    /**
     * **居合走廊命中判据**（纯函数 ⇒ 离线单测）：
     * 目标必须落在「眼睛沿准星方向 {@value #IAI_RANGE} 格」的**三维圆柱**内 ——
     * 前向距离 ∈ [0, 8]、到射线的垂直距离 ≤ {@value #IAI_LATERAL}。
     *
     * <p>★ 2026-10-18：由「水平叉积 + 竖直容差」改为三维点—射线距离 ⇒ <b>随准星俯仰</b>，
     * 抬枪就能打到高处（以及低处）的敌人。
     *
     * @param dx    目标（身体中心）相对眼睛的位移
     * @param axisX 准星单位向量
     */
    public static boolean insideIaiCorridor(double dx, double dy, double dz,
                                            double axisX, double axisY, double axisZ) {
        double along = dx * axisX + dy * axisY + dz * axisZ;
        //★ 端点留 1e-6 的浮点余量：斜向命中恰好落在 8.0 时，不会因舍入误差被判出界
        if (along < 0d || along > IAI_RANGE + 1.0E-6d) {
            return false;
        }
        //垂直分量 = delta − axis·along（点到射线的垂足）
        double perpX = dx - axisX * along;
        double perpY = dy - axisY * along;
        double perpZ = dz - axisZ * along;
        double perpendicular = Math.sqrt(perpX * perpX + perpY * perpY + perpZ * perpZ);
        return perpendicular <= IAI_LATERAL;
    }

    /** 数组取值（越界 / 非正 ⇒ 0）。 */
    private static int tableOf(int[] table, int level) {
        if (table == null || level <= 0) {
            return 0;
        }
        return table[Math.min(level, table.length) - 1];
    }

    /** 数组取值（越界 / 非正 ⇒ 0）。 */
    private static double tableOf(double[] table, int level) {
        if (table == null || level <= 0) {
            return 0d;
        }
        return table[Math.min(level, table.length) - 1];
    }

    // ───────── 内部几何 ─────────

    /**
     * **居合方向**：朝目标的**身体中心**（脚底 +1），否则取准星视线。
     *
     * <p>★ 2026-10-18 需求 6：必须保留<b>竖直分量</b>（旧实现水平化 ⇒ 抬头打不到高处）。
     * 传 {@code null} 时就是准星方向（抬头 / 低头都算）。
     */
    private static Vector aimDirection(Player owner, Location target) {
        Location eye = owner.getEyeLocation();
        if (target != null) {
            Vector delta = target.toVector().add(new Vector(0d, 1.0d, 0d)).subtract(eye.toVector());
            if (delta.lengthSquared() > 1.0E-6d) {
                return delta.normalize();
            }
        }
        Vector look = eye.getDirection();
        if (look.lengthSquared() > 1.0E-6d) {
            return look.clone().normalize();
        }
        return new Vector(0d, 0d, 1d);
    }

    /** 存活判定。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** 敌对判定（口径归阵营组件，本类不重写）。 */
    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }
}
