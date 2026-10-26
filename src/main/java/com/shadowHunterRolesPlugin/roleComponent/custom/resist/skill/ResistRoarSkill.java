package com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistControl;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistState;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistStrugglePassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistSwordModel;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistVfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.util.List;

/**
 * 「抗争」一技能：**谷底的怒吼**（哭泣的黑曜石图标，CD 10 秒）。
 *
 * <ol>
 *   <li><b>0.3 秒引导</b>（2026-10-19 需求）：引导期间<b>缓慢 IV</b>，屏幕打出与大招同款的
 *       {@code 引导中：X.X秒}；引导结束才真正爆发（解控 / 拔剑 / 伤害）；</li>
 *   <li>末影龙怒吼；</li>
 *   <li><b>解除自身所有控制</b>：插件侧 STUN / SILENCE、原版有害药水、负跳跃 / 定身
 *       （2026-10-18 需求：只解控，**不干预**归零惩罚的周期真伤与 SanTE 钉 0）；</li>
 *   <li>从地底拔出 4 把巨剑，1.5 秒后落回消散；</li>
 *   <li>周围 r={@value #RADIUS} 所有敌人：{@value #PHYSICAL} 物理 + 击退 3；</li>
 *   <li>每命中一人：回复自身 {@value #TE_HEAL} 点 TE、战斗时间 +{@value #BATTLE_ADD} 秒。</li>
 * </ol>
 *
 * <h2>口径申报：内建"TE 归零惩罚"的解除边界</h2>
 * 本技能**只解除控制**：清 STUN / SILENCE / 原版有害药水 / 负跳跃。
 * {@code DefaultSanTEZeroPunishment} 的周期真实伤害与"惩罚期间钉 SanTE=0"仍按内建逻辑结算 ——
 * 2026-10-18 需求明确"不用清除真伤，只消除期间的控制"，故本组件**不再**逐刻抬 SanTE。
 */
public class ResistRoarSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_roar_skill";

    /** 冷却（刻）——需求 10 秒。 */
    public static final int COOLDOWN_TICKS = 200;

    /** 范围半径（需求 r=6）。 */
    public static final double RADIUS = 6.0d;

    /** 范围物理伤害（需求 5 点）。 */
    public static final double PHYSICAL = 5d;

    /** 击退强度（需求：击退 3）。 */
    public static final double KNOCKBACK = 3.0d;

    /** 每命中一人回复的 TE。 */
    public static final int TE_HEAL = 15;

    /** 每命中一人增加的战斗时间（秒）。 */
    public static final int BATTLE_ADD = 2;

    /** 引导时长（刻）—— 2026-10-19 需求：0.3 秒。 */
    public static final int CHANNEL_TICKS = 6;

    /** 引导期间的缓慢等级（amplifier 3 = 缓慢 IV）。 */
    public static final int CHANNEL_SLOW_AMPLIFIER = 3;

    /**
     * 「成功解控」台词相对怒吼台词的延后刻数。
     *
     * <p>两句同刻会被浮空文本的节流（间隔 ≥7 刻）随机吃掉一句 ⇒ 错开 8 刻，
     * 观感是"先怒吼、紧接一句挣脱的低语"。
     */
    public static final int CONTROLLED_LINE_DELAY_TICKS = 8;

    /** 巨剑动画：上升 / 悬停 / 回落（刻）—— 上升 8 → {@value #SWORD_RISE}，约 1.6~1.7 倍速。 */
    public static final int SWORD_RISE = 5;
    private static final int SWORD_HOLD = 30;
    private static final int SWORD_FALL = 10;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private FactionComponent faction;
    private ResistStrugglePassive passive;
    private ResistFloatingTextComponent voice;
    private HotbarRenderComponent render;

    private ResistSwordModel swords;
    private int phase;

    /** 是否在 0.3 秒引导中。 */
    private boolean channeling;

    /** 引导已过刻数。 */
    private int channelTick;

    /** 引导<b>开始前</b>采样的控制位（0 = 本来就没被控）。 */
    private int controlledBeforeChannel;

    /** 解控成功后安排喊挣脱台词的刻（0 = 无待发）。 */
    private int controlledSayAtTick;

    public ResistRoarSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 技能描述符。 */
    public static final class Specification extends Skill.Specification<ResistRoarSkill> {

        public Specification() {
            super(Component.text("谷底的怒吼"),
                    List.of(
                            Component.text("0.3 秒引导（缓慢 IV + 屏幕「引导中」）后发出末影龙怒吼"),
                            Component.text("解除自身所有控制（STUN / 沉默 / 负面药水 / 定身）"),
                            Component.text("若确实解掉了控制，会补一句挣脱台词"),
                            Component.text("从地底拔出 4 把巨剑（1.5 秒后落回消散）"),
                            Component.text("周围 6 格所有敌人：5 点物理 + 击退 3"),
                            Component.text("每命中一人：回复自身 15 点 TE、战斗时间 +2 秒"),
                            Component.text("冷却 10 秒")
                    ),
                    200, 0, Material.CRYING_OBSIDIAN);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(ResistStrugglePassive.class);
        }

        @Override
        public ResistRoarSkill create(String id, ComponentServicesPort services) {
            return new ResistRoarSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        passive = svc().components().get(ResistStrugglePassive.class);
        voice = getComponent(ResistFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
    }

    @Override
    public void stop() {
        Player owner = svc().self().player();
        if (owner != null) {
            owner.clearTitle();
            if (buff != null) {
                buff.removePotionEffect(PotionEffectType.SLOWNESS);
            }
        }
        channeling = false;
        channelTick = 0;
        controlledBeforeChannel = 0;
        controlledSayAtTick = 0;
        if (swords != null) {
            swords.remove();
            swords = null;
        }
        phase = 0;
        vitals = null;
        sante = null;
        buff = null;
        faction = null;
        passive = null;
        voice = null;
        render = null;
    }

    @Override
    protected boolean canUse() {
        Player owner = svc().self().player();
        if (owner == null) {
            return false;
        }
        if (channeling) {
            return false;      //引导中不能再按（否则引导会被重置）
        }
        if (ResistState.isLocked(owner, Bukkit.getCurrentTick())) {
            return false;
        }
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return 0;
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || !canUse() || isCoolingDown() || channeling) {
            return;
        }
        startCooldown(COOLDOWN_TICKS);
        //① 引导开始前采样控制位（★ 必须早于引导施加的缓慢 IV，否则采到的是自己的减速）
        controlledBeforeChannel = ResistControl.maskOf(buff, owner);
        //② 进入 0.3 秒引导（缓慢 IV + title），真正的爆发在 update() 的引导结束时结算
        channeling = true;
        channelTick = 0;
        World world = owner.getWorld();
        if (world != null) {
            ResistSound.rampageCharge(world, owner.getLocation(), 0);
            ResistVfx.rampageCharge(world, owner.getLocation(), 1);
        }
        if (render != null) {
            render.requestRepaint();
        }
    }

    /** 引导每刻：缓慢 IV + 引导 title + 收束粒子/递增音；到期后真正爆发。 */
    private void tickChannel(Player owner) {
        channelTick++;
        World world = owner.getWorld();
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SLOWNESS, 8, CHANNEL_SLOW_AMPLIFIER);
        }
        if (channelTick % 2 == 0) {
            showChannelTitle(owner);
            if (world != null) {
                ResistVfx.rampageCharge(world, owner.getLocation(), channelTick);
                ResistSound.rampageCharge(world, owner.getLocation(), channelTick / 2);
            }
        }
        if (channelTick >= CHANNEL_TICKS) {
            channeling = false;
            owner.clearTitle();
            if (buff != null) {
                buff.removePotionEffect(PotionEffectType.SLOWNESS);
            }
            releaseRoar(owner, world);
        }
    }

    /** 需求：与大招一致的「引导中：X.X秒」。 */
    private void showChannelTitle(Player owner) {
        double remain = Math.max(0d, (CHANNEL_TICKS - channelTick + 1) / 20d);
        Component main = Component.text(
                String.format(java.util.Locale.ROOT, "引导中：%.1f秒", remain),
                NamedTextColor.WHITE, TextDecoration.BOLD);
        owner.showTitle(Title.title(main, Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(400), Duration.ofMillis(80))));
    }

    /** 引导结束后的真正爆发：解控 → 四把巨剑 → r6 伤害/回 TE/加时 → 台词。 */
    private void releaseRoar(Player owner, World world) {
        int now = Bukkit.getCurrentTick();
        //① 解控（引导期间施加的缓慢 IV 一并摘掉）
        releaseControl(owner);

        //①-b 成功解控判定：引导前有控制、解控后一位不剩 ⇒ 稍后补一句挣脱台词
        int controlledAfter = ResistControl.maskOf(buff, owner);
        if (ResistControl.isSuccessfullyCleared(controlledBeforeChannel, controlledAfter)) {
            controlledSayAtTick = now + CONTROLLED_LINE_DELAY_TICKS;
        }
        controlledBeforeChannel = 0;

        //② 巨剑 + 音效/特效
        if (world != null) {
            swords = ResistSwordModel.spawn(world, owner.getLocation(), 4, 1.7d,
                    SWORD_RISE, SWORD_HOLD, SWORD_FALL, 6.0d);
            ResistSound.roar(world, owner.getLocation());
            ResistSound.controlBreak(world, owner.getLocation());
            ResistSound.swordsErupt(world, owner.getLocation());
            ResistVfx.roarEruption(world, owner.getLocation());
        }

        //③ r6 伤害 / 回 TE / 加时
        if (world != null) {
            for (Player target : world.getNearbyPlayers(owner.getLocation(), RADIUS)) {
                if (target == null || target.equals(owner) || !isAlive(target) || !isHostile(target)) {
                    continue;
                }
                if (vitals != null) {
                    vitals.physicalDamage(target, owner, PHYSICAL, KNOCKBACK);
                }
                target.setNoDamageTicks(0);
                if (sante != null) {
                    sante.increaseSanTE(owner.getUniqueId(), TE_HEAL);
                }
                if (passive != null) {
                    passive.addBattleSeconds(owner, BATTLE_ADD, now);
                }
                ResistVfx.swordQiImpact(world, target.getLocation(), 1);
            }
        }

        if (voice != null) {
            voice.saySkill(owner, ResistFloatingTextComponent.MOMENT_SKILL_ROAR);
        }
        phase = 2;
        if (render != null) {
            render.requestRepaint();
        }
    }

    /**
     * 解除所有控制：
     * <ul>
     *   <li>{@code buff.clearDebuff()} —— 插件侧 STUN / SILENCE + 原版 HARMFUL 药水；</li>
     *   <li>显式摘负跳跃 / 缓慢 / 失明 / 黑暗（负跳跃在原版分类里不是 HARMFUL，必须单摘）；</li>
     * </ul>
     * <p>★ 不碰 SanTE：只解控，惩罚的周期真伤照旧（需求 2026-10-18）。
     */
    private void releaseControl(Player owner) {
        if (buff != null) {
            buff.clearDebuff();
            buff.remove(BuffType.STUN);
            buff.remove(BuffType.SILENCE);
        }
        owner.removePotionEffect(PotionEffectType.SLOWNESS);
        owner.removePotionEffect(PotionEffectType.JUMP_BOOST);
        owner.removePotionEffect(PotionEffectType.BLINDNESS);
        owner.removePotionEffect(PotionEffectType.DARKNESS);
        owner.removePotionEffect(PotionEffectType.WEAKNESS);
        owner.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        owner.removePotionEffect(PotionEffectType.NAUSEA);
        owner.setFallDistance(0f);

    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            if (channeling) {
                channeling = false;
                channelTick = 0;
                controlledBeforeChannel = 0;
                controlledSayAtTick = 0;
                owner.clearTitle();
                if (buff != null) {
                    buff.removePotionEffect(PotionEffectType.SLOWNESS);
                }
            }
            if (swords != null) {
                swords.remove();
                swords = null;
            }
            phase = 0;
            return;
        }
        int now = Bukkit.getCurrentTick();

        //① 引导中：只推引导（缓慢 IV + title），不推进巨剑
        if (channeling) {
            tickChannel(owner);
            return;
        }

        //② 成功解控的补白（错开怒吼台词，避开节流）
        if (controlledSayAtTick > 0 && now >= controlledSayAtTick) {
            controlledSayAtTick = 0;
            if (voice != null) {
                voice.saySpecial(owner, ResistFloatingTextComponent.MOMENT_CONTROLLED);
            }
        }

        //③ 巨剑动画
        if (swords != null) {
            if (!swords.tick()) {
                swords = null;
                phase = 0;
            } else if (now % 2 == 0) {
                ResistVfx.roarDiamondOrbit(owner.getWorld(), owner.getLocation(), swords.age());
            }
        }
    }

    private boolean isHostile(Player other) {
        return faction != null && faction.isHostileTo(other.getUniqueId());
    }

    private static boolean isAlive(Player player) {
        //创造 / 旁观不参与索敌（否则击退/击落会作用到不该作用的玩家）
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }
}
