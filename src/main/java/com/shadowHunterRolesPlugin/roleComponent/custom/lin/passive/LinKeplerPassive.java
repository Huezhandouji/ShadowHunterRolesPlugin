package com.shadowHunterRolesPlugin.roleComponent.custom.lin.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 「林」被动之二：**开普勒装置**（能量 / 特殊值 的循环引擎）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>每秒回复 1 点能量</b>；</li>
 *   <li>当<b>特殊值比能量低</b>时，<b>优先把能量转换成特殊值</b>，
 *       每秒转换速率 <b>4 能量 → 1 特殊值</b>（即每 0.25 秒转换 1 能量）；</li>
 *   <li>能量 &gt; 25 ⇒ 给林<b>速度 I</b>；</li>
 *   <li>能量 &gt; 50 ⇒ 给林<b>生命恢复 I</b>；</li>
 *   <li><b>开始能量转化时</b>，会慢慢喷出<b>不死图腾粒子</b>，并带有<b>每秒的轻声经验吸取音效</b>；</li>
 *   <li>当能量到达一定值时，林<b>脚下出现少量不死图腾的踪迹粒子</b>。</li>
 * </ol>
 *
 * <h2>★ 口径申报一："每秒回复 1 点能量"与"转换"是同一节拍的两种去向</h2>
 * 需求同时存在"+1 能量/秒"与"能量→特殊值 4:1"，如果不区分，二者会互相打架
 * （一边加一边减，净效果不明）。本实现把一秒钟拆成两件事、同一节拍判定：
 * <ol>
 *   <li><b>先结算转换</b>：若 {@code 特殊值 < 能量} ⇒ 本秒消耗 4 点能量、给 1 点特殊值
 *       （★ 如实申报：需求写"每 0.25 秒转换 1 能量"，但同一句话也写了"每秒转换速率为 4 能量/特殊值"
 *       —— 两者是同一个速率的两种说法。本实现按<b>每秒一次、一次转 4 能量</b>落，
 *       与"每秒 4 能量/1 特殊值"逐字一致，且不引入 4 倍节拍开销）；</li>
 *   <li><b>再结算回能</b>：若没在转换（或转换后仍满足条件）⇒ 本秒 +1 能量。
 *       ★ 只要"仍在转换"就<b>不给</b>这 +1 —— 否则会变成"转出去 4、加回 1"，
 *       与"优先将能量转换成特殊值"（优先 = 有冲突时以转换为准）相反。</li>
 * </ol>
 *
 * <h2>★ 口径申报二："能量到达一定值"取 25</h2>
 * 需求第 6 条只说"到达一定值"，没给数。取 {@value #FOOTPRINT_ENERGY}（= 速度档的阈值 25）：
 * 理由 = 那时"装置明显在工作"（已经给上了速度），脚下留印与增益同源、玩家可自洽解释；
 * 同时避免低能量时脚下常年拖尾（那会变成噪声）。该常数是**可调**的，改一处即可。
 *
 * <h2>★ 口径申报三：增益为什么"每秒重刷"而不是"状态变化才加"</h2>
 * 药水效果有<b>时长</b>，且别人（敌人 / 其它技能）可以给我们上同类效果并覆盖。
 * 若只在"跨过阈值的那一刻"加一次，30 刻后就自然消失、再也不会回来（玩家以为被动坏了）。
 * 因此按 {@value #AURA_REFRESH_TICKS} 刻为一个节拍**重刷**，时长远大于节拍 ⇒ 效果连续不断；
 * 跌破阈值时则**主动摘掉**（否则会残留到药水自然结束）。
 */
public class LinKeplerPassive extends PassiveSkill {

    /** **本组件的登记 id**。 */
    public static final String ID = "lin_passive_kepler";

    /** 装置的基础节拍：每秒一次（刻）。 */
    public static final int TICK_PERIOD = 20;

    /** 每节拍回复的能量（需求："每秒回复一点能量"）。 */
    public static final int ENERGY_GAIN_PER_SECOND = 1;

    /** 每节拍转换消耗的能量（需求："每秒转换速率为4能量/特殊值"）。 */
    public static final int CONVERT_ENERGY_COST = 4;

    /** 每节拍转换产出的特殊值（需求："4 能量/特殊值"）。 */
    public static final int CONVERT_SANTE_GAIN = 1;

    /** 速度阈值（需求："当能量大于25时"）。 */
    public static final int SPEED_THRESHOLD = 25;

    /** 生命恢复阈值（需求："当能量大于50时"）。 */
    public static final int REGEN_THRESHOLD = 50;

    /** ★ 脚下踪迹粒子的能量阈值（口径申报二）。 */
    public static final int FOOTPRINT_ENERGY = 25;

    /** 增益重刷时长（刻）：远大于节拍 ⇒ 连续不断。 */
    private static final int AURA_REFRESH_TICKS = 60;

    private EnergyComponent energy;
    private SanTEComponent sante;
    private BuffComponent buff;

    /** ★ 渲染组件：能量/特殊值读数在非冷却期也在变 ⇒ 必须自己请求重绘。 */
    private HotbarRenderComponent render;

    /** 节拍计时（刻）。 */
    private int tickCounter;

    /** 踪迹粒子相位（每节拍推进 ⇒ 脚下那几颗在慢慢转）。 */
    private double footprintPhase;

    /** 上一帧是否在转换（用于"开始转换"的边沿判据：只在开始那一刻播音效）。 */
    private boolean converting;

    public LinKeplerPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符（无栏位 ⇒ 天然不占热键栏）。 */
    public static final class Specification extends PassiveSkill.Specification<LinKeplerPassive> {

        public Specification() {
            super(Component.text("开普勒装置"),
                    List.of(
                            Component.text("每秒回复1点能量"),
                            Component.text("当特殊值比能量低时，优先以每秒4能量的速率转换为特殊值"),
                            Component.text("能量大于25时获得速度I，大于50时获得生命恢复I"),
                            Component.text("转换期间喷出不死图腾粒子并伴随轻声经验吸取音效"),
                            Component.text("能量足够时脚下出现不死图腾踪迹粒子")
                    ));
            requires(EnergyComponent.class).requires(SanTEComponent.class).requires(BuffComponent.class);
        }

        @Override
        public LinKeplerPassive create(String id, ComponentServicesPort services) {
            return new LinKeplerPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        energy = svc().components().get(EnergyComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        tickCounter = 0;
        footprintPhase = 0d;
        converting = false;
    }

    @Override
    public void stop() {
        tickCounter = 0;
        footprintPhase = 0d;
        converting = false;
    }

    /**
     * 每刻：① 每秒节拍做转换 / 回能 / 音效；② 每刻画脚下踪迹粒子；③ 请求重绘。
     */
    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || energy == null || sante == null) {
            return;
        }

        // ① 每秒节拍：转换优先，其次回能
        tickCounter++;
        if (tickCounter >= TICK_PERIOD) {
            tickCounter = 0;
            runSecondBeat(owner);
        }

        // ② 增益按节拍刷新（每刻判阈值、按固定时长重刷 ⇒ 连续不断）
        refreshAuras(owner);

        // ③ 脚下踪迹粒子（能量达到阈值时）
        drawFootprint(owner);

        // ④ ★ 能量/特殊值读数在非冷却期也在变 ⇒ 自己请求重绘（框架只在组件冷却期自动刷）
        if (render != null) {
            render.requestRepaint();
        }
    }

    /**
     * **每秒节拍**：转换（若有需要）→ 回能 → 音效。
     */
    private void runSecondBeat(Player owner) {
        boolean shouldConvert = shouldConvert(energy.current(), sante.current());
        if (shouldConvert && energy.current() >= CONVERT_ENERGY_COST) {
            energy.decrease(CONVERT_ENERGY_COST);
            sante.increase(CONVERT_SANTE_GAIN);
            // 转换观感：图腾粒子（慢慢喷）+ 轻声经验吸取音效
            Location at = owner.getLocation().clone().add(0d, 1d, 0d);
            LinVfx.keplerConvert(owner.getWorld(), at);
            LinSound.keplerSiphonSound(owner.getWorld(), owner.getLocation());
            converting = true;
        } else {
            // 没有在转换 ⇒ 本秒 +1 能量（需求："每秒回复一点能量"）
            energy.increase(ENERGY_GAIN_PER_SECOND);
            converting = false;
        }
    }

    /**
     * ★ **是否需要转换**（**纯函数** ⇒ 可离线穷举）：
     * 需求"当特殊值比能量低时，优先将能量转换成特殊值"。
     *
     * @param energy 当前能量
     * @param sante  当前特殊值
     * @return 是否应当把能量转换为特殊值
     */
    public static boolean shouldConvert(int energy, int sante) {
        return sante < energy;
    }

    /**
     * **增益刷新**：能量 &gt; 25 ⇒ 速度 I；能量 &gt; 50 ⇒ 生命恢复 I。
     * 跌破阈值则主动摘掉（避免残留到药水自然结束）。
     */
    private void refreshAuras(Player owner) {
        if (buff == null) {
            return;
        }
        int current = energy.current();
        if (current > SPEED_THRESHOLD) {
            buff.applyPotionEffect(PotionEffectType.SPEED, AURA_REFRESH_TICKS, 0);
        } else if (owner.hasPotionEffect(PotionEffectType.SPEED)) {
            // ★ 只摘自己那一档带来的速度：用同一时长口径无法分辨来源 ⇒ 直接移除
            owner.removePotionEffect(PotionEffectType.SPEED);
        }
        if (current > REGEN_THRESHOLD) {
            buff.applyPotionEffect(PotionEffectType.REGENERATION, AURA_REFRESH_TICKS, 0);
        } else if (owner.hasPotionEffect(PotionEffectType.REGENERATION)) {
            owner.removePotionEffect(PotionEffectType.REGENERATION);
        }
    }

    /** 脚下踪迹粒子（能量达到 {@value #FOOTPRINT_ENERGY} 时）。 */
    private void drawFootprint(Player owner) {
        if (energy.current() < FOOTPRINT_ENERGY) {
            return;
        }
        footprintPhase += 0.35d;
        if (footprintPhase > Math.PI * 2d) {
            footprintPhase -= Math.PI * 2d;
        }
        LinVfx.keplerFootprint(owner.getWorld(), owner.getLocation(), footprintPhase);
    }

    // ───────── 读口 ─────────

    /** 当前是否处于"能量 → 特殊值"的转换状态（读口 / 探针）。 */
    public boolean converting() {
        return converting;
    }
}
