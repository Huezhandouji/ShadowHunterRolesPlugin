package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistConquestSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistRampageSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill.ResistRoarSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/**
 * 「抗争」被动：**不停歇的斗争**。
 *
 * <h2>战斗时间（层）</h2>
 * <ul>
 *   <li>上限 {@value #MAX_SECONDS} 秒；</li>
 *   <li>在战斗中（最近 {@value #COMBAT_IDLE_TICKS} 刻内命中过敌人 / 被敌人打过）每秒自然 +1 秒；</li>
 *   <li>主武器命中 +1 秒；技能按需求额外加时（一技能每人 +2、二技能每人 +3、三技能每人 +3）；</li>
 *   <li>**闲置消退**：距最后一次战斗 {@value #COMBAT_IDLE_TICKS} 刻（10 秒）后，每
 *       {@value #DECAY_INTERVAL_TICKS} 刻（0.1 秒）退 1 层，逐层归零。</li>
 * </ul>
 *
 * <h2>八档阈值（逐条对齐需求）</h2>
 * <pre>
 *  5s  剑气物理 +5          10s 抗性 I          15s 速度 I + 每 2 秒回 1 TE
 * 20s  生命回复 II          25s 每 3 秒额外回 4 生命   30s 抗性 II + 每秒减 0.2 秒技能 CD
 * 35s  剑气物理再 +5        40s 每 5 秒 8 点伤害吸收
 * </pre>
 *
 * <h2>表现</h2>
 * 每 5 层一枚绕身暴击粒子（最高 8 枚，压低高度、拉大半径，不挡视线）+ 一档音效；
 * 40 层（8 枚）后不再加粒子，改为**身后白灰双层圆环**常驻，每 2 秒扩大闪动一次。
 * actionbar 显示战斗时间（加粗暗红）——本组件是该角色的**唯一 actionbar 发布者**。
 */
public class ResistStrugglePassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_struggle_passive";

    // ───────── 数值口径 ─────────

    /** 战斗时间上限（秒）。 */
    public static final int MAX_SECONDS = 999;

    /** 闲置判定（刻）——需求：10 秒没命中 / 没挨打就开始消退。 */
    public static final int COMBAT_IDLE_TICKS = 200;

    /** 消退节拍（刻）—— 2026-10-19 需求：脱战后 **每 0.1 秒退 1 层**（原 0.2 秒）。 */
    public static final int DECAY_INTERVAL_TICKS = 2;

    /**
     * 30 层档位：所有技能 CD **每秒**额外恢复的刻数（4 刻 = 0.2 秒）。
     *
     * <p>★ 与 {@link #DECAY_INTERVAL_TICKS} 拆开：消退节拍调快不该连带削弱 CD 减免。
     */
    public static final int CD_ACCEL_TICKS = 4;

    /** 自然增长节拍（刻）——每秒 +1 秒。 */
    public static final int GROW_INTERVAL_TICKS = 20;

    /** 每多少层一个档位 / 一枚粒子。 */
    public static final int TIER_STEP = 5;

    /** 绕身暴击粒子上限（需求：8 个后不再叠加）。 */
    public static final int MAX_ORBIT_PARTICLES = 8;

    /** 剑气基础物理伤害（需求：10 点）。 */
    public static final double SWORD_QI_PHYSICAL = 10d;

    /** 剑气特殊值伤害下限 / 上限（需求：3~6 点，闭区间）。 */
    public static final int SWORD_QI_SPECIAL_MIN = 3;
    public static final int SWORD_QI_SPECIAL_MAX = 6;

    /** 5 层 / 35 层各 +5 物理。 */
    public static final int SWORD_QI_PHYSICAL_BONUS = 5;

    /** HUD 保活间隔（刻）。 */
    private static final int HUD_KEEPALIVE_TICKS = 20;

    private VitalsComponent vitals;
    private BuffComponent buff;
    private SanTEComponent sante;
    private FactionComponent faction;

    /** 受伤订阅句柄（{@code start()} 注册、{@code stop()} 按引用撤销；触发 {@link #onDamaged}）。 */
    private VitalsComponent.DamagedListener damagedEntry;

    private final Random random = new Random();

    /** 战斗时间（秒 / 层）。 */
    private int battleSeconds;

    /** 最后一次"命中敌人 / 被敌人攻击"的刻。 */
    private int lastCombatTick = Integer.MIN_VALUE / 2;

    /** 下一次消退的刻。 */
    private int nextDecayTick;

    /** 下一次自然增长的刻。 */
    private int nextGrowthTick;

    /** 上一次已经播过音效的档位（0..8）。 */
    private int lastTier;

    /** 各类周期效果的下一拍。 */
    private int teTick;
    private int healTick;
    private int absorbTick;
    private int cdTick;
    private int effectTick;

    /** 粒子/环的相位。 */
    private int phase;
    private int ringTick;

    /** HUD 变化检测。 */
    private int lastHudSecond = -1;
    private int hudTick;

    /** 哪些增益是本组件上的（阈值掉下去时只摘自己上的）。 */
    private boolean resistanceApplied;
    private boolean speedApplied;
    private boolean regenApplied;

    private boolean started;

    public ResistStrugglePassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 被动描述符（无栏位）。 */
    public static final class Specification extends PassiveSkill.Specification<ResistStrugglePassive> {

        public Specification() {
            super(Component.text("不停歇的斗争"),
                    List.of(
                            Component.text("战斗时间上限 999 秒；每 5 秒解锁一档增益，共 8 档"),
                            Component.text("5s 剑气物理+5 / 10s 抗性 I / 15s 速度 I + 每 2 秒回 1 TE / 20s 生命回复 II"),
                            Component.text("25s 每 3 秒回 4 生命 / 30s 抗性 II + 技能 CD 加速 / 35s 剑气再+5 / 40s 每 5 秒 8 点吸收"),
                            Component.text("10 秒未命中或被攻击 ⇒ 每 0.1 秒退 1 层；每 5 层一枚绕身暴击粒子 + 一档音效"),
                            Component.text("40 层后身后常驻白灰双层圆环，每 2 秒扩大闪动一次"))
            );
            requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(SanTEComponent.class).requires(FactionComponent.class);
        }

        @Override
        public ResistStrugglePassive create(String id, ComponentServicesPort services) {
            return new ResistStrugglePassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        faction = svc().components().get(FactionComponent.class);

        //订阅受伤：被敌人攻击也算"仍在战斗"（见 onDamaged）
        if (vitals != null && damagedEntry == null) {
            damagedEntry = vitals.addDamagedListener(this, event -> onDamaged(event.source(), event.amount()));
        }

        int now = Bukkit.getCurrentTick();
        battleSeconds = 0;
        lastCombatTick = now - COMBAT_IDLE_TICKS - 1;   //开局不在战斗中
        nextDecayTick = now + COMBAT_IDLE_TICKS;
        nextGrowthTick = now + GROW_INTERVAL_TICKS;
        lastTier = 0;
        teTick = now + 40;
        healTick = now + 60;
        absorbTick = now + 100;
        cdTick = now + 20;
        effectTick = 0;
        phase = 0;
        ringTick = 0;
        lastHudSecond = -1;
        hudTick = 0;
        resistanceApplied = false;
        speedApplied = false;
        regenApplied = false;
        started = true;
    }

    @Override
    public void stop() {
        Player owner = svc().self().player();
        if (owner != null) {
            ResistFov.clear(owner);
            ResistState.clear(owner);
            if (buff != null) {
                buff.removePotionEffect(PotionEffectType.RESISTANCE);
                buff.removePotionEffect(PotionEffectType.SPEED);
                buff.removePotionEffect(PotionEffectType.REGENERATION);
            }
        }
        if (damagedEntry != null && vitals != null) {
            vitals.removeDamagedListener(damagedEntry);
        }
        damagedEntry = null;
        vitals = null;
        buff = null;
        sante = null;
        faction = null;
        started = false;
    }

    @Override
    public void update() {
        if (!started) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            if (owner != null) {
                ResistFov.clear(owner);     //死亡/离线：别把速度修饰符留在尸体上
            }
            return;
        }

        ResistFov.tick(owner, now);
        advanceClock(owner, now);
        applyThresholds(owner, now);
        publishHud(owner);
        drawParticles(owner, now);
    }

    // ───────── 对外读 / 写面 ─────────

    /** 当前战斗时间（秒）。 */
    public int battleSeconds() {
        return battleSeconds;
    }

    /** 当前档位（0..8）。 */
    public int tier() {
        return Math.min(MAX_ORBIT_PARTICLES, battleSeconds / TIER_STEP);
    }

    /** 当前剑气物理伤害（10 基础 + 5 层 / 35 层各 +5）。 */
    public int swordQiPhysical() {
        return swordQiPhysicalAt(battleSeconds);
    }

    /**
     * 标记"发生过战斗"（命中 / 挨打都算），并把 10 秒消退计时重新拉满。
     * 主武器与三个技能在命中时调用。
     */
    public void markCombat(int nowTick) {
        lastCombatTick = nowTick;
        nextDecayTick = nowTick + COMBAT_IDLE_TICKS;
        if (nextGrowthTick < nowTick) {
            nextGrowthTick = nowTick + GROW_INTERVAL_TICKS;
        }
    }

    /**
     * 增加战斗时间（命中奖励）。
     *
     * @param seconds 加多少秒（需求：主武器 +1、一技能每人 +2、二/三技能每人 +3）
     */
    public void addBattleSeconds(Player owner, int seconds, int nowTick) {
        if (owner == null || seconds <= 0) {
            return;
        }
        markCombat(nowTick);
        setBattleSeconds(battleSeconds + seconds, owner);
    }

    /** 闭区间随机特殊值伤害（3~6，两端都含）。 */
    public int rollSpecial() {
        return SWORD_QI_SPECIAL_MIN + random.nextInt(SWORD_QI_SPECIAL_MAX - SWORD_QI_SPECIAL_MIN + 1);
    }

    // ───────── 时间推进 ─────────

    private void advanceClock(Player owner, int now) {
        boolean inCombat = now - lastCombatTick < COMBAT_IDLE_TICKS;
        if (inCombat) {
            if (now >= nextGrowthTick) {
                int steps = 1 + (now - nextGrowthTick) / GROW_INTERVAL_TICKS;
                setBattleSeconds(battleSeconds + steps, owner);
                nextGrowthTick += steps * GROW_INTERVAL_TICKS;
            }
            nextDecayTick = now + COMBAT_IDLE_TICKS;
            return;
        }
        if (battleSeconds > 0 && now >= nextDecayTick) {
            int steps = 1 + (now - nextDecayTick) / DECAY_INTERVAL_TICKS;
            setBattleSeconds(Math.max(0, battleSeconds - steps), owner);
            nextDecayTick += steps * DECAY_INTERVAL_TICKS;
        }
    }

    /** 写入战斗时间并处理"跨档"的音效（掉刻也一次补齐，但最多只补到最后播的一档）。 */
    private void setBattleSeconds(int value, Player owner) {
        int clamped = Math.max(0, Math.min(MAX_SECONDS, value));
        battleSeconds = clamped;
        int tier = Math.min(MAX_ORBIT_PARTICLES, clamped / TIER_STEP);
        if (tier > lastTier) {
            World world = owner == null ? null : owner.getWorld();
            if (world != null) {
                for (int t = lastTier + 1; t <= tier; t++) {
                    ResistSound.tierUp(world, owner.getLocation(), t);
                }
            }
        }
        lastTier = tier;
    }

    // ───────── 阈值效果 ─────────

    private void applyThresholds(Player owner, int now) {
        boolean locked = ResistState.isLocked(owner, now);
        boolean protectedBuffs = ResistState.buffsProtected(owner, now);
        effectTick++;

        if (effectTick % 10 == 0 && buff != null) {
            if (!locked && !protectedBuffs) {
                if (battleSeconds >= 10) {
                    buff.applyPotionEffect(PotionEffectType.RESISTANCE, 30, battleSeconds >= 30 ? 1 : 0);
                    resistanceApplied = true;
                } else if (resistanceApplied) {
                    buff.removePotionEffect(PotionEffectType.RESISTANCE);
                    resistanceApplied = false;
                }
                if (battleSeconds >= 15) {
                    buff.applyPotionEffect(PotionEffectType.SPEED, 30, 0);
                    speedApplied = true;
                } else if (speedApplied) {
                    buff.removePotionEffect(PotionEffectType.SPEED);
                    speedApplied = false;
                }
            } else {
                //技能自己上的抗性 III / 速度 III 优先，本组件这一拍不动它们
                resistanceApplied = false;
                speedApplied = false;
            }

            if (battleSeconds >= 20) {
                buff.applyPotionEffect(PotionEffectType.REGENERATION, 30, 1);
                regenApplied = true;
            } else if (regenApplied) {
                buff.removePotionEffect(PotionEffectType.REGENERATION);
                regenApplied = false;
            }
        }

        if (battleSeconds >= 15 && sante != null && now >= teTick) {
            sante.increase(1);
            teTick = now + 40;
        }
        if (battleSeconds >= 25 && vitals != null && now >= healTick) {
            vitals.heal(owner, 4d);
            healTick = now + 60;
        }
        if (battleSeconds >= 40 && buff != null && now >= absorbTick) {
            //伤害吸收 8 点 = 4 颗心 ⇒ amplifier 1（每级 4 点）
            buff.applyPotionEffect(PotionEffectType.ABSORPTION, 120, 1);
            absorbTick = now + 100;
        }
        if (battleSeconds >= 30 && now >= cdTick) {
            accelerateCooldowns();
            cdTick = now + 20;
        }
    }

    /** 30 层：所有技能 CD 每秒额外恢复 {@value #CD_ACCEL_TICKS} 刻（0.2 秒）。 */
    private void accelerateCooldowns() {
        accelerate(getComponent(ResistRoarSkill.class));
        accelerate(getComponent(ResistConquestSkill.class));
        accelerate(getComponent(ResistRampageSkill.class));
    }

    private static void accelerate(com.shadowHunterRolesPlugin.roleComponent.ActiveComponent skill) {
        if (skill == null) {
            return;
        }
        int remaining = skill.remainingCooldownTicks();
        if (remaining > CD_ACCEL_TICKS) {
            skill.startCooldown(remaining - CD_ACCEL_TICKS);
        } else if (remaining > 0) {
            skill.stopCooldown();
        }
    }

    // ───────── HUD / 表现 ─────────

    private void publishHud(Player owner) {
        boolean changed = battleSeconds != lastHudSecond;
        hudTick++;
        if (!changed && hudTick < HUD_KEEPALIVE_TICKS) {
            return;
        }
        hudTick = 0;
        lastHudSecond = battleSeconds;
        //2026-10-18 需求：`斗争// 000 //不停`（三位补零、去掉秒数）
        owner.sendActionBar(Component.text(hudText(battleSeconds),
                NamedTextColor.DARK_RED, TextDecoration.BOLD));
    }

    private void drawParticles(Player owner, int now) {
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        phase++;
        int tier = tier();
        if (tier > 0 && phase % 2 == 0) {
            ResistVfx.struggleOrbit(world, owner.getLocation(), tier, phase);
        }
        if (battleSeconds >= 40) {
            boolean flash = ringTick++ % 40 == 0;
            if (phase % 2 == 0) {
                ResistVfx.struggleRing(world, owner.getLocation(), owner.getLocation().getYaw(), phase, flash);
            }
            if (flash) {
                ResistSound.ringPulse(world, owner.getLocation());
            }
        }
    }

    // ───────── 受伤订阅（VitalsComponent#addDamagedListener） ─────────

    /**
     * 被敌人攻击也算"仍在战斗"——需求：10 秒**未攻击到敌人 或 被敌人攻击**才消退。
     * <p>触发点 = {@code start()} 里经 {@code VitalsComponent#addDamagedListener} 登记的监听
     * （平台事件结算之后投递，只观察、不改事件）。
     */
    private void onDamaged(Player source, double amount) {
        if (!started) {
            return;
        }
        markCombat(Bukkit.getCurrentTick());
    }

    // ───────── 纯函数（离线单测） ─────────

    /** HUD 文案（纯函数）：`斗争// 000 //不停`，三位补零、无秒数后缀。 */
    public static String hudText(int seconds) {
        int value = Math.max(0, Math.min(MAX_SECONDS, seconds));
        return String.format(java.util.Locale.ROOT, "斗争// %03d //不停", value);
    }

    /** 给定战斗秒数时的剑气物理伤害。 */
    public static int swordQiPhysicalAt(int seconds) {
        int value = (int) SWORD_QI_PHYSICAL;
        if (seconds >= 5) {
            value += SWORD_QI_PHYSICAL_BONUS;
        }
        if (seconds >= 35) {
            value += SWORD_QI_PHYSICAL_BONUS;
        }
        return value;
    }

    /** 给定战斗秒数时的档位（0..8）。 */
    public static int tierAt(int seconds) {
        return Math.min(MAX_ORBIT_PARTICLES, Math.max(0, seconds) / TIER_STEP);
    }

    /** 闲置 {@code idleTicks} 刻后应当退掉几层（0.1 秒一层，10 秒起算）。 */
    public static int decayStepsAt(int idleTicks) {
        if (idleTicks < COMBAT_IDLE_TICKS) {
            return 0;
        }
        return 1 + (idleTicks - COMBAT_IDLE_TICKS) / DECAY_INTERVAL_TICKS;
    }

    /** 该秒数是否已解锁某档（供技能侧读，纯函数）。 */
    public static boolean reached(int seconds, int threshold) {
        return seconds >= threshold;
    }
}
