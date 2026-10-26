package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.FactionManager;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * 「芹泽沙耶香 · 瓦尔基里」被动：**笼手 · 影尘吸收装备**（含「失衡爆发」的全局执行器）。
 *
 * <h2>本类是哪几件事的唯一真值持有者 / 执行者</h2>
 * <ol>
 *   <li><b>笼手充能</b>：上限 {@value #MAX_CHARGES} 点，每 {@value #CHARGE_REGEN_TICKS} 刻（30 秒）自动回复 1 点。
 *       主武器（右键充能）与技能二（命中奖励）通过 {@link #takeAllCharges()} / {@link #tryAddCharge()} 读写。</li>
 *   <li><b>能量上限 = 100</b>（2026-10-14 需求更新：由 90 改为 100 ⇒ 与本框架内建上限一致，
 *       不再需要任何压制）。本组件仍每刻核对一遍（超过 {@value #ENERGY_CAP} 就拉回）——
 *       作为自愈护栏保留；在内建上限不变的前提下它永不触发。</li>
 *   <li><b>失衡爆发的执行</b>：账本（{@link SayakaImbalance}）只记真值；
 *       每刻由本组件扫描——开场（8 真伤 / 虚弱 / 奖励）、每秒结算（扣 SAN / 回复猎影人）、
 *       到期收尾，全部在这里发生（★ 工程口径：清扫必须在早退守卫**之前**）。</li>
 *   <li><b>actionbar + subtitle 的唯一发布者</b>：主武器与技能一用
 *       {@link #publishBladeState(String, int)} / {@link #publishGrenade(boolean, int, int)} /
 *       {@link #publishIai(boolean, double, boolean)} **推**读数过来，本组件统一发布
 *       （actionbar 一行、后写覆盖先写 ⇒ 必须单一发布者）。
 *       <ul>
 *         <li><b>actionbar</b>（屏幕最底部）：形态 / 充能层数 / <b>笼手三槽 □/◇/◆</b> / 榴弹 /
 *             「□居合准备完毕□」—— 层数指示器按 2026-10-17 需求**放在这里**；</li>
 *         <li><b>subtitle</b>（准星下方）：**仅**在纳刀蓄力那 1 秒显示斜杠蓄力条，结束即
 *             {@code clearTitle()}（不再常驻）。</li>
 *       </ul></li>
 *   <li><b>2026-10-17 需求 5</b>：失衡爆发的 8 点真伤改以<b>目标为心</b>的 r{@value #BURST_RADIUS}
 *       范围结算（"周围敌人 = 目标的周围敌人"）+ 范围冲击特效；生命奖励改为
 *       {@value #HEAL_MOTES} 枚<b>曲线追猎粒子</b>，飞到纱耶香身边才回血。</li>
 *   <li><b>2026-10-18 闲置消退</b>：每刻调用 {@link SayakaImbalance#decay(int)} —— 有层数的敌人
 *       若 {@value SayakaImbalance#DECAY_IDLE_TICKS} 刻（10 秒）没被叠层，此后每
 *       {@value SayakaImbalance#DECAY_INTERVAL_TICKS} 刻（0.3 秒）退 1 层；每退一层播
 *       "影尘下沉"的粒子与轻音（{@link SayakaVfx#imbalanceDecay}）。</li>
 * </ol>
 *
 * <h2>依赖图（不可出现环）</h2>
 * <pre>
 *   SayakaGauntletPassive（本类；叶子，只依赖内建组件）
 *        ↑ requires
 *   SayakaBladeMainWeapon ──requires──→ 本类
 *        ↑ requires
 *   SayakaFlashSlashSkill / SayakaBladeStormSkill
 *   SayakaGrenadeSkill ──requires──→ 本类
 * </pre>
 * 本类**不**认识任何 sayaka 自定义组件 ⇒ 无环。
 */
public class SayakaGauntletPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "sayaka_gauntlet_passive";

    // ───────── 数值口径（唯一修改点）─────────

    /** 笼手充能上限（需求：上限 3 点）。 */
    public static final int MAX_CHARGES = 3;

    /** 笼手充能自然回复间隔（刻）—— 需求：「每 30 秒自动回复 1 点」。 */
    public static final int CHARGE_REGEN_TICKS = 600;

    /** 能量上限（2026-10-14 需求更新：100 —— 与内建上限一致 ⇒ 不再压制；护栏恒不触发）。 */
    public static final int ENERGY_CAP = 100;

    /** 失衡爆发的开场真实伤害（需求：8 点）。 */
    public static final double BURST_TRUE_DAMAGE = 8d;

    /**
     * 失衡爆发的**范围伤害半径**（2026-10-17 需求 5：「所谓周围敌人，是指目标的周围敌人」）。
     *
     * <p>圆心 = 被触发的**目标**（不是沙耶香）；半径取 5 格（与榴弹落点范围同口径）。
     * 8 点真伤对范围内所有敌人各结算一次（含目标本身）。
     */
    public static final double BURST_RADIUS = 5.0d;

    /** 回血追猎粒子数量（需求：4 个）。 */
    public static final int HEAL_MOTES = 4;

    /** 追猎粒子飞行速度（格/刻）—— 需求：40 格/秒。 */
    public static final double MOTE_SPEED_PER_TICK = 40.0d / 20d;

    /** 追猎粒子的到达判定距离（格）—— 进到这个范围就算"飞到纱耶香周围"。 */
    public static final double MOTE_ARRIVE_DISTANCE = 1.2d;

    /** 追猎粒子的寿命上限（刻）：追不上（玩家瞬移 / 跨维度）就强制收工，防孤儿粒子。 */
    public static final int MOTE_MAX_LIFETIME_TICKS = 60;

    /** 失衡爆发的「虚弱 1」持续（刻）—— 需求：20 秒。 */
    public static final int BURST_WEAKNESS_TICKS = 400;

    /** 失衡爆发的奖励：能量（需求：60 点）。 */
    public static final int BURST_REWARD_ENERGY = 60;

    /** 失衡爆发的奖励：生命（需求：8 点）。 */
    public static final double BURST_REWARD_HEAL = 8d;

    /** 失衡爆发的奖励：笼手充能（需求：1 点）。 */
    public static final int BURST_REWARD_CHARGE = 1;

    /** 「周围 20 格内所有猎影人」（需求原话）的半径。 */
    public static final double ALLY_RADIUS = 20.0d;

    /** 猎影人每秒回复的 SAN 值（需求：1 点）。 */
    public static final int ALLY_SAN_PER_SEC = 1;

    /** 猎影人每秒回复的能量（需求：1 点）。 */
    public static final int ALLY_ENERGY_PER_SEC = 1;

    /** HUD 保活重发间隔（刻）：内容没变也隔一阵重发一次（actionbar 会被别的插件/原版消息顶掉）。 */
    private static final int HUD_KEEPALIVE_TICKS = 60;

    /** HUD 分隔符。 */
    private static final String HUD_SEPARATOR = " | ";

    // ───────── 屏幕提示（2026-10-17 需求 1 / 2）─────────

    /** 蓄力条字符：全角斜杠（中文客户端必带字形，不会显示成空白方块）。 */
    public static final String IAI_BAR_CHAR = "／";

    /** 蓄力条格数（屏幕上能看到多少根斜杠）。 */
    public static final int IAI_BAR_SLOTS = 14;

    /** 蓄力条与"笼手"提示之间的间隔（**整行居中**：2026-10-18 需求 2 去掉了原来的右侧留白）。 */
    private static final String IAI_HUD_GAP = "   ";

    /** 笼手槽位·空位。 */
    public static final char GLYPH_EMPTY = '□';

    /** 笼手槽位·可用充能（笼手里有、但还没注入武器）。 */
    public static final char GLYPH_AVAILABLE = '◇';

    /** 笼手槽位·已注入主武器（"当前主武器有层数"）。 */
    public static final char GLYPH_FORCED = '◆';

    /** subtitle 保活重发间隔（刻）—— actionbar / subtitle 都会被别的消息顶掉，必须定期续命。 */
    private static final int SUBTITLE_KEEPALIVE_TICKS = 160;

    /** subtitle 的单次显示时长（刻）：必须**大于**保活间隔，否则两帧之间会闪断。 */
    private static final int SUBTITLE_STAY_TICKS = 200;

    // ───────── 协作组件 ─────────

    private EnergyComponent energy;
    private SanTEComponent sante;
    private VitalsComponent vitals;
    private FactionComponent faction;
    private SayakaFloatingTextComponent voice;

    // ───────── 运行期状态 ─────────

    /** 笼手充能（0..{@value #MAX_CHARGES}）。 */
    private int charges;

    /** 充能回复节拍（0..{@value #CHARGE_REGEN_TICKS}）。 */
    private int chargeRegenTick;

    // ── HUD 推入读数（主武器 / 技能一每刻推）──

    private String bladeLabel = "拔刀";
    private int bladeChargeLevel;
    private boolean grenadeHeld;
    private int grenadeCurrent;
    private int grenadeMax = SayakaGrenadeSkill.AMMO_MAX;

    private String lastHudText = "";
    private int hudTick;
    private int auraTick;

    // ── 屏幕提示状态（主武器每刻推）──

    /** 蓄力条是否显示（纳刀 / 蓄势中）。 */
    private boolean iaiVisible;

    /** 蓄力进度 0..1。 */
    private double iaiProgress;

    /** 居合是否已就绪（蓄满 + 闸门已过）。 */
    private boolean iaiReady;

    /** subtitle 上次发布的内容（变化检测）。 */
    private String lastSubtitleText = "";

    /** subtitle 保活节拍。 */
    private int subtitleTick;

    /** 在飞的回血追猎粒子。 */
    private final List<HealMote> healMotes = new ArrayList<>();

    /** 追猎粒子推进节拍（诊断用）。 */
    private int moteTick;

    /**
     * 一枚**回血追猎粒子**：从敌人身边随机冒出，沿二次贝塞尔曲线飞向纱耶香，
     * **到达那一刻才回血**（{@link #healAmount}）。
     */
    private static final class HealMote {
        private final Location start;
        private final Location control;
        private final double healAmount;
        private final int seed;
        private final double totalLength;
        private double t;
        private int age;

        private HealMote(Location start, Location control, double healAmount, int seed, double totalLength) {
            this.start = start;
            this.control = control;
            this.healAmount = healAmount;
            this.seed = seed;
            this.totalLength = Math.max(0.5d, totalLength);
        }
    }

    public SayakaGauntletPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（被动 = 无栏位）。 */
    public static final class Specification extends PassiveSkill.Specification<SayakaGauntletPassive> {

        public Specification() {
            super(Component.text("笼手 · 影尘吸收装备"),
                    List.of(
                            Component.text("失衡爆发：敌人失衡条积满 30 点时立即触发——对目标周围 5 格内所有敌人造成 8 点真实伤害"),
                            Component.text("          目标获得虚弱 1（20 秒）并每秒流失 1 点 SAN 值；爆发期间不再积累失衡，结束后重新积累"),
                            Component.text("          并为周围 20 格内所有猎影人每秒回复 1 点 SAN 值与 1 点能量（20 秒）"),
                            Component.text("任意敌人触发失衡爆发时：自身立即回复 60 点能量并获得 1 点笼手充能"),
                            Component.text("          另有 4 枚黑红追踪粒子从敌人身边飞出，抵达自身时才各回复 2 点生命（共 8 点）"),
                            Component.text("笼手充能上限 3 点，每 30 秒自动回复 1 点；actionbar 显示 □/◇/◆ 三槽层数")
                    ));
            requires(EnergyComponent.class).requires(SanTEComponent.class)
                    .requires(VitalsComponent.class).requires(FactionComponent.class)
                    .requires(SayakaFloatingTextComponent.class);
        }

        @Override
        public SayakaGauntletPassive create(String id, ComponentServicesPort services) {
            return new SayakaGauntletPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        energy = svc().components().get(EnergyComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        faction = svc().components().get(FactionComponent.class);
        voice = svc().components().get(SayakaFloatingTextComponent.class);
        charges = 0;
        chargeRegenTick = 0;
        bladeLabel = "拔刀";
        bladeChargeLevel = 0;
        grenadeHeld = false;
        lastHudText = "";
        hudTick = 0;
        iaiVisible = false;
        iaiProgress = 0d;
        iaiReady = false;
        lastSubtitleText = "";
        subtitleTick = 0;
        healMotes.clear();
        moteTick = 0;
        pinEnergy();
    }

    @Override
    public void stop() {
        //角色被换掉 / 卸载：把可能还挂着的蓄力条收干净（否则会残留到 stay 到期）
        Player owner = svc().self().player();
        if (owner != null && owner.isOnline() && !lastSubtitleText.isEmpty()) {
            owner.clearTitle();
        }
        lastSubtitleText = "";
        healMotes.clear();
        energy = null;
        sante = null;
        vitals = null;
        faction = null;
        voice = null;
    }

    @Override
    public void update() {
        int now = Bukkit.getCurrentTick();
        //① ★ 全局账本的清扫：必须放在早退守卫**之前**（谁死了都得有人来清 —— 工程口径）
        SayakaImbalance.sweep(now, target -> {
            Player carrier = Bukkit.getPlayer(target);
            return carrier == null || !carrier.isOnline() || carrier.isDead() || carrier.getHealth() <= 0d;
        });
        //①½ 闲置消退（2026-10-18 需求）：10 秒没被叠层 ⇒ 每 0.3 秒退 1 层。
        //   ★ 放在守卫之前：层数挂在**敌人身上**，不该因为沙耶香阵亡就冻结。
        //   ★ 爆发中的条目不消退（那时层数已归零，见账本 decay 的守卫）。
        for (SayakaImbalance.DecayEvent event : SayakaImbalance.decay(now)) {
            Player carrier = Bukkit.getPlayer(event.target());
            if (carrier == null || !carrier.isOnline() || carrier.getWorld() == null) {
                continue;
            }
            SayakaVfx.imbalanceDecay(carrier.getWorld(), carrier.getLocation().clone().add(0d, 1.0d, 0d));
            SayakaSound.imbalanceDecay(carrier.getWorld(), carrier.getLocation(), event.after());
        }

        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            healMotes.clear();      //主体离场：在飞的粒子作废（不回血）
            iaiVisible = false;     //蓄力条不许挂在尸体上
            iaiReady = false;
            return;
        }
        //② 能量上限护栏（100 = 与内建一致 ⇒ 常态恒不触发）
        pinEnergy();
        //③ 笼手自然回复
        advanceChargeRegen(owner);
        //④ 失衡爆发执行（开场 / 每秒结算）
        processBursts(owner, now);
        //⑤ 回血追猎粒子推进（到达才回血）
        advanceHealMotes(owner);
        //⑥ 再压一次（爆发奖励 / 猎影人回复可能把能量顶上去）
        pinEnergy();
        //⑦ HUD（唯一发布者：actionbar + subtitle）
        publishHud(owner);
        publishSubtitle(owner);
        //⑧ 敌人在爆发中：给一点持续观感（绕敌人转的黑红光环）
        if (++auraTick % 2 == 0) {
            drawBurstAuras(now);
        }
    }

    // ───────── ① 笼手充能 ─────────

    /** 当前笼手充能（读口）。 */
    public int charges() {
        return charges;
    }

    /** 笼手充能上限（读口）。 */
    public int maxCharges() {
        return MAX_CHARGES;
    }

    /**
     * **增加 1 点笼手充能**（技能二命中 / 需求奖励走这里；到达上限 ⇒ 返回 {@code false} 且不加）。
     */
    public boolean tryAddCharge() {
        if (charges >= MAX_CHARGES) {
            return false;
        }
        charges++;
        return true;
    }

    /**
     * **取走全部笼手充能**（主武器右键「武器充能」走这里）。
     *
     * @return 实际取走的层数（0 = 没有充能，调用方据此给落空反馈）
     */
    public int takeAllCharges() {
        int taken = charges;
        charges = 0;
        return taken;
    }

    /** 每 30 秒回复 1 点（满则这一拍作废，不欠账）。 */
    private void advanceChargeRegen(Player owner) {
        if (++chargeRegenTick >= CHARGE_REGEN_TICKS) {
            chargeRegenTick -= CHARGE_REGEN_TICKS;
            if (charges < MAX_CHARGES) {
                charges++;
                SayakaSound.chargeStored(owner.getWorld(), owner.getLocation(), false);
                SayakaVfx.chargeStored(owner.getWorld(), owner.getLocation(), false);
                //语音：「能量回复 / 笼手充能」那一栏（自然回复这一路）
                if (voice != null) {
                    voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_CHARGE_GAIN);
                }
            }
        }
    }

    /** 能量护栏：超过 {@value #ENERGY_CAP} 就拉回（上限 = 100 与内建一致 ⇒ 恒不触发，纯自愈用）。 */
    private void pinEnergy() {
        if (energy != null && energy.current() > ENERGY_CAP) {
            energy.set(ENERGY_CAP);
        }
    }

    // ───────── ② 失衡爆发执行 ─────────

    /** 扫描账本：处理**自己触发**的爆发（开场效果 / 每秒结算）。 */
    private void processBursts(Player owner, int now) {
        UUID selfId = owner.getUniqueId();
        List<SayakaImbalance.View> views = SayakaImbalance.snapshot();
        for (SayakaImbalance.View view : views) {
            if (view.burstOwner() == null || !view.burstOwner().equals(selfId)) {
                continue;   //不是自己触发的爆发：由触发者的被动实例负责（多实例不重复结算）
            }
            Player victim = Bukkit.getPlayer(view.target());
            if (victim == null || !victim.isOnline() || victim.isDead() || victim.getHealth() <= 0d) {
                //2026-10-18 需求 3：敌人死亡 ⇒ 失衡立刻结束，爆发窗口 / 拍数（"CD"）一并清零
                SayakaImbalance.clear(view.target());
                continue;
            }
            if (view.burstUntilTick() <= now) {
                continue;   //已到期：等待 sweep 清条目（层数已归零、重新积累由 add 自然支持）
            }
            //① 开场：8 真伤 + 虚弱 + 触发者奖励（先标记 ⇒ 幂等）
            if (!view.startProcessed()) {
                SayakaImbalance.markStartProcessed(view.target());
                processBurstStart(owner, victim);
            }
            //② 每秒结算：扣 SAN + 回复猎影人
            int due = SayakaImbalance.drainTargetFor(now - view.burstStartTick(), view.drainedCount());
            if (due > view.drainedCount()) {
                resolveBurstSecond(owner, victim);
                SayakaImbalance.markDrained(view.target());
            }
        }
    }

    /**
     * 爆发开场。
     *
     * <p>2026-10-17 需求 5 改动两处：
     * <ol>
     *   <li>8 点真伤的**圆心改为被触发的目标**（「所谓周围敌人，是指目标的周围敌人」）——
     *       对 {@value #BURST_RADIUS} 格内所有敌人各结算一次；</li>
     *   <li>**回血改为延迟**：不再立刻 +8，而是放出 {@value #HEAL_MOTES} 枚追猎粒子，
     *       飞到纱耶香身边才按 8/4 分次回血（见 {@link #spawnHealMotes} / {@link #advanceHealMotes}）。</li>
     * </ol>
     * 能量 +60 与笼手 +1 仍**立即**到账（需求原文只说"回复效果"延迟，能量/充能是立刻的奖励）。
     */
    private void processBurstStart(Player owner, Player victim) {
        World world = victim.getWorld();
        //① 被触发的目标**必定**吃满 8 点真伤（不论阵营判定如何 —— 它是失衡条的持有人）
        if (vitals != null) {
            vitals.trueDamage(victim, owner, BURST_TRUE_DAMAGE);
            victim.setNoDamageTicks(0);
        }
        //② 「周围敌人」= 目标周围：以目标为心 r5，范围内其余所有敌人同样各 8 点真实伤害
        if (vitals != null && world != null) {
            for (Player other : world.getNearbyPlayers(victim.getLocation(), BURST_RADIUS)) {
                if (other == null || other.equals(owner) || other.equals(victim)
                        || !isAlive(other) || !isHostile(other)) {
                    continue;
                }
                vitals.trueDamage(other, owner, BURST_TRUE_DAMAGE);
                other.setNoDamageTicks(0);
            }
        }
        //「敌人获得虚弱 1」——给他人上药水直接施加（工程口径；BuffComponent 的自灌口只作用于自己）
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                BURST_WEAKNESS_TICKS, 0, true, false, true));
        //③ 立即奖励：能量 / 笼手充能（生命奖励延后到粒子到达）
        if (energy != null) {
            energy.increase(BURST_REWARD_ENERGY);
        }
        for (int i = 0; i < BURST_REWARD_CHARGE; i++) {
            if (tryAddCharge()) {
                break;
            }
        }
        //④ 观感：爆发柱（目标身上）+ 范围冲击（目标周围）
        SayakaVfx.burstErupt(world, victim.getLocation().clone().add(0d, 1d, 0d));
        SayakaVfx.burstBlast(world, victim.getLocation(), BURST_RADIUS);
        SayakaSound.burstTriggered(world, victim.getLocation());
        SayakaVfx.chargeStored(owner.getWorld(), owner.getLocation(), true);
        SayakaSound.chargeStored(owner.getWorld(), owner.getLocation(), true);
        //⑤ 4 枚回血追猎粒子（到达才回血）
        spawnHealMotes(owner, victim);
        //语音：「失衡爆发触发」那一栏（爆发是单一事件 ⇒ 一句一组，不复读）
        if (voice != null) {
            voice.saySkill(owner, SayakaFloatingTextComponent.MOMENT_BURST);
        }
    }

    // ───────── 回血追猎粒子（需求 5）─────────

    /**
     * 从敌人身边**随机位置**放出 {@value #HEAL_MOTES} 枚粒子。
     *
     * <p>"随机"用**确定性伪随机**（目标 UUID 的散列 + 序号）实现：不引入 {@code Random}
     * （离线测试可复现、也不占全局随机源），观感上每次爆发的位置都不同。
     * 控制点 = 起点与纱耶香的中间 + 随机侧向弯曲 ⇒ 飞行轨迹是一条**曲线**。
     */
    private void spawnHealMotes(Player owner, Player victim) {
        World world = victim.getWorld();
        if (world == null) {
            return;
        }
        if (owner.getWorld() != world) {
            //跨维度（极端情况）：粒子无从飞行 ⇒ 退回立即回血，不让玩家白丢奖励
            if (vitals != null) {
                vitals.heal(owner, BURST_REWARD_HEAL);
            }
            return;
        }
        Location origin = victim.getLocation().clone().add(0d, 1.0d, 0d);
        Location eye = owner.getEyeLocation().clone();
        double each = BURST_REWARD_HEAL / HEAL_MOTES;
        int baseSeed = victim.getUniqueId().hashCode();
        for (int i = 0; i < HEAL_MOTES; i++) {
            double angle = ((baseSeed >>> (i * 5 + 1)) & 0xFF) / 255d * Math.PI * 2d;
            double radius = 0.5d + ((baseSeed >>> (i * 3 + 2)) & 0x7) / 14d;
            double dy = (i % 2 == 0) ? 0.35d : -0.2d;
            Location start = origin.clone().add(Math.cos(angle) * radius, dy, Math.sin(angle) * radius);
            Vector toOwner = eye.toVector().subtract(start.toVector());
            Vector flat = new Vector(toOwner.getX(), 0d, toOwner.getZ());
            Vector side = flat.lengthSquared() < 1.0E-6d
                    ? new Vector(1d, 0d, 0d)
                    : new Vector(-flat.getZ(), 0d, flat.getX()).normalize();
            double bend = (i % 2 == 0 ? 1d : -1d) * (1.1d + 0.4d * i);
            Location control = start.clone()
                    .add(toOwner.clone().multiply(0.5d))
                    .add(side.multiply(bend))
                    .add(0d, 0.9d, 0d);
            double total = start.distance(control) + control.distance(eye);
            healMotes.add(new HealMote(start, control, each, i, total));
        }
    }

    /**
     * 推进追猎粒子：每刻沿曲线前进一步（{@value #MOTE_SPEED_PER_TICK} 格/刻 = 40 格/秒），
     * **到达那一刻才回血**（每枚 {@code 8/4 = 2} 点）。
     */
    private void advanceHealMotes(Player owner) {
        if (healMotes.isEmpty()) {
            return;
        }
        Location end = owner.getEyeLocation().clone().add(0d, -0.25d, 0d);
        World world = end.getWorld();
        if (world == null) {
            healMotes.clear();
            return;
        }
        moteTick++;
        Iterator<HealMote> iterator = healMotes.iterator();
        while (iterator.hasNext()) {
            HealMote mote = iterator.next();
            Location current = bezier(mote.start, mote.control, end, Math.min(1d, mote.t), world);
            boolean arrived = mote.t >= 1d
                    || current.distanceSquared(end) <= MOTE_ARRIVE_DISTANCE * MOTE_ARRIVE_DISTANCE;
            if (arrived) {
                if (vitals != null) {
                    vitals.heal(owner, mote.healAmount);
                }
                SayakaVfx.healthMoteArrive(world, owner.getLocation());
                SayakaSound.healthMoteArrive(world, owner.getLocation());
                iterator.remove();
                continue;
            }
            SayakaVfx.healthMoteTrail(world, current, mote.seed);
            mote.age++;
            if (mote.age > MOTE_MAX_LIFETIME_TICKS) {
                iterator.remove();      //追不上（瞬移 / 长时间逃逸）：作废，不白给回血
                continue;
            }
            mote.t = Math.min(1d, mote.t + MOTE_SPEED_PER_TICK / mote.totalLength);
        }
    }

    /**
     * 二次贝塞尔取点（纯函数）：{@code B(t) = (1-t)²·P0 + 2(1-t)t·C + t²·P2}。
     * 两端为起点/终点，中段被控制点拉成曲线。
     */
    public static Location bezier(Location start, Location control, Location end, double t, World world) {
        if (start == null || control == null || end == null || world == null) {
            return null;
        }
        double clamped = Math.max(0d, Math.min(1d, t));
        double u = 1d - clamped;
        Vector point = start.toVector().multiply(u * u)
                .add(control.toVector().multiply(2d * u * clamped))
                .add(end.toVector().multiply(clamped * clamped));
        return point.toLocation(world);
    }

    /** 爆发每秒结算：敌人 -1 SAN；周围 20 格内的猎影人 +1 SAN / +1 能量。 */
    private void resolveBurstSecond(Player owner, Player victim) {
        World world = victim.getWorld();
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(), 1);
        }
        if (world != null) {
            for (Player ally : world.getNearbyPlayers(victim.getLocation(), ALLY_RADIUS)) {
                if (ally == null || ally.equals(victim) || ally.isDead() || ally.getHealth() <= 0d) {
                    continue;
                }
                if (FactionManager.factionOf(ally.getUniqueId()) != Faction.HUNTER) {
                    continue;   //「所有猎影人」= 猎影人阵营（无角色者不在其列）
                }
                if (ally.equals(owner)) {
                    //★ 跨实例口对 self 恒回 0（"他人"语义）⇒ 自己这一份走本组件自己的组件读/写口
                    if (sante != null) {
                        sante.increase(ALLY_SAN_PER_SEC);
                    }
                    if (energy != null) {
                        energy.increase(ALLY_ENERGY_PER_SEC);
                    }
                } else {
                    if (sante != null) {
                        sante.increaseSanTE(ally.getUniqueId(), ALLY_SAN_PER_SEC);
                    }
                    if (energy != null) {
                        energy.increaseEnergy(ally.getUniqueId(), ALLY_ENERGY_PER_SEC);
                    }
                }
                SayakaVfx.allyBless(world, ally.getLocation());
            }
            SayakaVfx.burstDrain(world, victim.getLocation());
            SayakaSound.burstTick(world, victim.getLocation());
        }
    }

    /** 给"正在爆发中"的敌人画持续光环（由触发者的被动实例画）。 */
    private void drawBurstAuras(int now) {
        Player owner = svc().self().player();
        if (owner == null) {
            return;
        }
        UUID selfId = owner.getUniqueId();
        for (SayakaImbalance.View view : SayakaImbalance.snapshot()) {
            if (view.burstOwner() == null || !view.burstOwner().equals(selfId)) {
                continue;
            }
            if (view.burstUntilTick() <= now) {
                continue;
            }
            Player victim = Bukkit.getPlayer(view.target());
            if (victim == null || !victim.isOnline() || victim.isDead() || victim.getHealth() <= 0d) {
                continue;   //死亡目标不再画爆发光环（失衡已由 sweep / processBursts 结束）
            }
            SayakaVfx.burstAura(victim.getWorld(), victim.getLocation(), now);
        }
    }

    // ───────── ③ HUD（唯一发布者）─────────

    /** 主武器推入形态读数（每刻调用，代价 = 两次字段写 + 一次字符串比较在发布处）。 */
    public void publishBladeState(String label, int weaponChargeLevel) {
        this.bladeLabel = label == null ? "拔刀" : label;
        this.bladeChargeLevel = Math.max(0, weaponChargeLevel);
    }

    /**
     * 主武器推入**居合蓄力**读数（需求 1）。
     *
     * @param visible  是否显示蓄力条（纳刀 / 蓄势中；风暴期间不显示）
     * @param progress 0..1
     * @param ready    是否可以释放居合（蓄满 + 闸门已过）
     */
    public void publishIai(boolean visible, double progress, boolean ready) {
        this.iaiVisible = visible;
        this.iaiProgress = SayakaVfx.clamp01(progress);
        this.iaiReady = visible && ready;
    }

    /** 技能一推入榴弹读数（{@code held} = 榴弹发射器此刻是否拿在主手）。 */
    public void publishGrenade(boolean held, int current, int max) {
        this.grenadeHeld = held;
        this.grenadeCurrent = current;
        this.grenadeMax = Math.max(1, max);
    }

    /** 组装并发布 actionbar（内容变了立刻发；没变则每 {@value #HUD_KEEPALIVE_TICKS} 刻保活重发）。 */
    private void publishHud(Player owner) {
        String text = hudPlainText();
        if (!text.equals(lastHudText)) {
            lastHudText = text;
            hudTick = 0;
            owner.sendActionBar(hudComponent());
            return;
        }
        if (++hudTick >= HUD_KEEPALIVE_TICKS) {
            hudTick = 0;
            owner.sendActionBar(hudComponent());
        }
    }

    /**
     * 发布**居合蓄力条**（subtitle：准星下方）。
     *
     * <p>★ 2026-10-17 需求：<b>笼手层数指示器搬到 actionbar</b>，因此这个常驻 subtitle 被撤掉了；
     * subtitle 现在**只在纳刀蓄力那 1 秒内出现**，蓄力结束立刻 {@code clearTitle()} 收干净
     * （不留一条常驻文本挡视线）。
     * <p>★ 位置说明：Minecraft 没有"屏幕任意坐标画文本"的公开口子，能落在准星下方且能靠右的
     * 唯一载体就是 title 的 subtitle（actionbar 会跑到屏幕最底部）。
     */
    private void publishSubtitle(Player owner) {
        String text = subtitlePlainText();
        if (!text.equals(lastSubtitleText)) {
            lastSubtitleText = text;
            subtitleTick = 0;
            if (text.isEmpty()) {
                owner.clearTitle();     //蓄力结束：把上一帧的条收掉（不会留下常驻文本）
            } else {
                showSubtitle(owner);
            }
            return;
        }
        if (text.isEmpty()) {
            return;         //没有内容：不重发
        }
        if (++subtitleTick >= SUBTITLE_KEEPALIVE_TICKS) {
            subtitleTick = 0;
            showSubtitle(owner);
        }
    }

    private void showSubtitle(Player owner) {
        owner.showTitle(Title.title(Component.empty(), subtitleComponent(),
                0, SUBTITLE_STAY_TICKS, 0));
    }

    /** actionbar 的纯文本形态（变化检测用，与 {@link #hudComponent()} 一一对应）。 */
    private String hudPlainText() {
        StringBuilder sb = new StringBuilder();
        if (iaiReady) {
            sb.append("□居合准备完毕□").append(HUD_SEPARATOR);
        }
        sb.append('[').append(bladeLabel).append(']');
        if (bladeChargeLevel > 0) {
            sb.append(" 充能x").append(bladeChargeLevel);
        }
        sb.append(HUD_SEPARATOR).append(chargeGlyphs(charges, bladeChargeLevel, MAX_CHARGES)).append(" 笼手");
        if (grenadeHeld) {
            sb.append(HUD_SEPARATOR).append("榴弹 ").append(Math.max(0, grenadeCurrent)).append('/').append(grenadeMax);
        }
        return sb.toString();
    }

    /** actionbar 的彩色形态（黑红主题：形态=暗红加粗，充能/弹药=红，分隔=深灰）。 */
    private Component hudComponent() {
        Component body = Component.empty();
        if (iaiReady) {
            body = body.append(Component.text("□居合准备完毕□", NamedTextColor.RED, TextDecoration.BOLD))
                    .append(Component.text(HUD_SEPARATOR, NamedTextColor.DARK_GRAY));
        }
        Component head = Component.text("[" + bladeLabel + "]", NamedTextColor.DARK_RED, TextDecoration.BOLD);
        if (bladeChargeLevel > 0) {
            head = head.append(Component.text(" 充能x" + bladeChargeLevel, NamedTextColor.RED, TextDecoration.BOLD));
        }
        Component tail = Component.text(HUD_SEPARATOR, NamedTextColor.DARK_GRAY)
                .append(slotsComponent())
                .append(Component.text(" 笼手", NamedTextColor.DARK_RED, TextDecoration.BOLD));
        if (grenadeHeld) {
            tail = tail.append(Component.text(HUD_SEPARATOR, NamedTextColor.DARK_GRAY))
                    .append(Component.text("榴弹 " + Math.max(0, grenadeCurrent) + "/" + grenadeMax,
                            NamedTextColor.DARK_RED));
        }
        return body.append(head).append(tail);
    }

    /**
     * subtitle 的纯文本形态（变化检测用）。
     *
     * <p>★ 2026-10-18 需求 2 / 4：整行**居中**（去掉右侧留白），并在蓄力条左侧带上
     * 与普攻状态**同一套**笼手层数提示（□/◇/◆ + "笼手"），所以纳刀时也能看到层数。
     * 空串 = 现在不该有 subtitle。
     */
    private String subtitlePlainText() {
        if (!iaiVisible) {
            return "";
        }
        return chargeGlyphs(charges, bladeChargeLevel, MAX_CHARGES) + IAI_HUD_GAP + "笼手" + IAI_HUD_GAP
                + iaiBarPlainText() + (iaiReady ? "#ready" : "");
    }

    /**
     * subtitle 的彩色形态：**笼手层数提示 + 居合蓄力条**（整行居中）。
     *
     * <p>层数提示与 actionbar 用的是同一个 {@link #slotsComponent()} —— 两处读数永远一致。
     */
    private Component subtitleComponent() {
        Component line = slotsComponent()
                .append(Component.text(IAI_HUD_GAP + "笼手" + IAI_HUD_GAP, NamedTextColor.DARK_RED, TextDecoration.BOLD));
        int filled = iaiFilledSlots(iaiProgress, IAI_BAR_SLOTS);
        Component bar;
        if (iaiReady) {
            bar = Component.text(repeat(IAI_BAR_CHAR, filled), NamedTextColor.RED, TextDecoration.BOLD)
                    .append(Component.text(repeat(IAI_BAR_CHAR, IAI_BAR_SLOTS - filled),
                            NamedTextColor.DARK_RED, TextDecoration.BOLD));
        } else {
            bar = Component.text(repeat(IAI_BAR_CHAR, filled), NamedTextColor.DARK_RED, TextDecoration.BOLD)
                    .append(Component.text(repeat(IAI_BAR_CHAR, IAI_BAR_SLOTS - filled),
                            NamedTextColor.DARK_GRAY));
        }
        return line.append(bar);
    }

    /** 蓄力条的纯文本形态（只用于变化检测，字符与 {@link #subtitleComponent()} 一致）。 */
    private String iaiBarPlainText() {
        int filled = iaiFilledSlots(iaiProgress, IAI_BAR_SLOTS);
        return repeat(IAI_BAR_CHAR, filled) + repeat(IAI_BAR_CHAR, IAI_BAR_SLOTS - filled);
    }

    /**
     * 槽位符号的**逐字符着色**版本：{@code ◆}=亮红（已注入）、{@code ◇}=暗红（可用）、
     * {@code □}=深灰（空）。三档颜色一眼可辨，不用数符号。
     */
    private Component slotsComponent() {
        String glyphs = chargeGlyphs(charges, bladeChargeLevel, MAX_CHARGES);
        Component out = Component.empty();
        for (int i = 0; i < glyphs.length(); i++) {
            char glyph = glyphs.charAt(i);
            NamedTextColor color = switch (glyph) {
                case GLYPH_FORCED -> NamedTextColor.RED;
                case GLYPH_AVAILABLE -> NamedTextColor.DARK_RED;
                default -> NamedTextColor.DARK_GRAY;
            };
            out = out.append(Component.text(String.valueOf(glyph), color, TextDecoration.BOLD));
        }
        return out;
    }

    // ───────── 层数符号（纯函数，离线可测）─────────

    /**
     * **笼手层数符号串**（需求 2 的规则）：
     * <ul>
     *   <li>主武器**已有层数**时，最前面的槽位无论原来是 □ 还是 ◇ 都变成 {@code ◆}；</li>
     *   <li>笼手**可用层数**占接下来的槽位，显示 {@code ◇}；</li>
     *   <li>其余槽位是 {@code □}。</li>
     * </ul>
     * 例：笼手 2 层、武器 1 层 ⇒ {@code ◆◇□}；笼手 3 层、武器 0 层 ⇒ {@code ◇◇◇}。
     *
     * @param gauntletCharges 笼手当前可用充能（0..max）
     * @param weaponLevel     主武器已注入层数（0..max）
     * @param slots           槽位总数（= 笼手上限）
     */
    public static String chargeGlyphs(int gauntletCharges, int weaponLevel, int slots) {
        int total = Math.max(0, slots);
        int forced = Math.max(0, Math.min(total, weaponLevel));
        int available = Math.max(0, Math.min(total - forced, gauntletCharges));
        StringBuilder sb = new StringBuilder(total);
        for (int i = 0; i < total; i++) {
            if (i < forced) {
                sb.append(GLYPH_FORCED);
            } else if (i < forced + available) {
                sb.append(GLYPH_AVAILABLE);
            } else {
                sb.append(GLYPH_EMPTY);
            }
        }
        return sb.toString();
    }

    /** 蓄力条已填充的格数（纯函数）：四舍五入到格，夹在 0..slots。 */
    public static int iaiFilledSlots(double progress, int slots) {
        int total = Math.max(0, slots);
        double p = SayakaVfx.clamp01(progress);
        return Math.max(0, Math.min(total, (int) Math.round(p * total)));
    }

    /** 字符串重复（{@code times <= 0} ⇒ 空串）。 */
    private static String repeat(String unit, int times) {
        if (unit == null || times <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(unit.length() * times);
        for (int i = 0; i < times; i++) {
            sb.append(unit);
        }
        return sb.toString();
    }

    /** 存活判定。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** 敌对判定（口径归阵营组件）。 */
    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }
}
