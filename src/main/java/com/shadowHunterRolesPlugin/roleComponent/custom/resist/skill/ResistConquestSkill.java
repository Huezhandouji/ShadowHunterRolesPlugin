package com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistFov;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistState;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistStrugglePassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistSwordSwing;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 「抗争」二技能：**命运的征服**（下界石英图标，CD 15 秒）。
 *
 * <h2>流程</h2>
 * <ol>
 *   <li>引导 {@value #CHANNEL_TICKS} 刻（0.5 秒），期间 <b>缓慢 II + 抗性 III + 不可跳跃</b>，
 *       并锁住主武器与其它技能；</li>
 *   <li>第一刀（右→左水平，半径 8）：{@value #MULT_FIRST}× 剑气伤害 + 向左击退 2 + 击落；</li>
 *   <li>等 {@value #GAP_1_TICKS} 刻（0.4 秒）第二刀（左→右，半径 8）：同第一刀；</li>
 *   <li>等 {@value #GAP_2_TICKS} 刻（0.7 秒）第三刀（上→下砸地，半径 9）：{@value #MULT_FINAL}× 剑气伤害；</li>
 *   <li>每命中一人：战斗时间 +3 秒、命中音效 / 粒子、FOV 脉冲 0.1 秒；</li>
 *   <li>收尾：只要命中过任意一人 ⇒ 速度 III 1 秒。</li>
 * </ol>
 *
 * <h2>口径申报</h2>
 * <ul>
 *   <li><b>击落</b>：把目标设为不可飞行 + 负跳跃，并在命中后 {@value #SLAM_TICKS} 刻内持续以
 *       {@code -4 格/刻}（= 80 格/秒）下压 —— 这就是"速度 80 格每秒、0.1 秒后消散"的落地口径；</li>
 *   <li><b>施法锁</b>：主武器与其它技能通过 {@link ResistState} 的静态锁互相协调（组件不成环）；</li>
 *   <li>剑气伤害 = 被动当前剑气物理 × 倍率（特殊值 3~6 也按同一倍率取整）。</li>
 * </ul>
 */
public class ResistConquestSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_conquest_skill";

    /** 冷却（刻）——需求 15 秒。 */
    public static final int COOLDOWN_TICKS = 300;

    /** 引导刻数（0.5 秒）。 */
    public static final int CHANNEL_TICKS = 10;

    /** 第一刀→第二刀（0.4 秒）。 */
    public static final int GAP_1_TICKS = 8;

    /** 第二刀→第三刀（0.7 秒）。 */
    public static final int GAP_2_TICKS = 14;

    /** 第三刀后收尾。 */
    public static final int FINISH_TICKS = 10;

    /** 前两刀半径（需求 8）。 */
    public static final double RADIUS = 8.0d;

    /** 第三刀半径（需求 9）。 */
    public static final double FINAL_RADIUS = 9.0d;

    /** 垂直容差。 */
    public static final double VERTICAL_TOLERANCE = 3.5d;

    /** 伤害倍率。 */
    public static final double MULT_FIRST = 1.5d;
    public static final double MULT_FINAL = 2.0d;

    /** 击退强度（需求：击退 2）。 */
    public static final double KNOCKBACK = 2.0d;

    /** 击落持续（0.1 秒）。 */
    public static final int SLAM_TICKS = 2;

    /** 击落下压速度（80 格/秒 = 4 格/刻）。 */
    public static final double SLAM_VELOCITY = 4.0d;

    /** 每命中一人加时（秒）。 */
    public static final int BATTLE_ADD = 3;

    /** 命中过 ⇒ 速度 III 时长。 */
    public static final int SPEED_REWARD_TICKS = 20;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private FactionComponent faction;
    private ResistStrugglePassive passive;
    private ResistFloatingTextComponent voice;
    private HotbarRenderComponent render;

    private int phase;
    private int timer;
    private int chargeTick;
    private boolean anyHit;
    private final List<UUID> slamTargets = new ArrayList<>();
    private final java.util.Map<UUID, Boolean> flightBefore = new java.util.HashMap<>();
    private int slamUntil;

    /** 当前正在播放的巨型铁剑挥砍（需求 7）。 */
    private ResistSwordSwing swing;

    public ResistConquestSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 技能描述符。 */
    public static final class Specification extends Skill.Specification<ResistConquestSkill> {

        public Specification() {
            super(Component.text("命运的征服"),
                    List.of(
                            Component.text("引导 0.5 秒：期间缓慢 II、抗性 III、不可跳跃、禁用其它技能与主武器"),
                            Component.text("第一刀（右→左，r8）：1.5 倍剑气伤害 + 击退 2 + 击落"),
                            Component.text("0.4 秒后第二刀（左→右，r8）：同上"),
                            Component.text("0.7 秒后第三刀（上→下砸地，r9）：2.0 倍剑气伤害，命中触发 0.1 秒 FOV 放大"),
                            Component.text("每命中一人 +3 秒战斗时间；任意一刀命中 ⇒ 收尾获得速度 III 1 秒；冷却 15 秒")
                    ),
                    300, 0, Material.QUARTZ);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(ResistStrugglePassive.class);
        }

        @Override
        public ResistConquestSkill create(String id, ComponentServicesPort services) {
            return new ResistConquestSkill(id, services, this);
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
        if (swing != null) {
            swing.remove();
            swing = null;
        }
        cleanupEffects();
        ResistState.unlock(svc().self().player());
        phase = 0;
        timer = 0;
        restoreFlight();
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
        if (owner == null || !canUse() || isCoolingDown()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        startCooldown(COOLDOWN_TICKS);
        phase = 1;
        timer = CHANNEL_TICKS;
        chargeTick = 0;
        anyHit = false;
        slamTargets.clear();
        flightBefore.clear();
        slamUntil = 0;
        ResistState.lock(owner, now, CHANNEL_TICKS + GAP_1_TICKS + GAP_2_TICKS + FINISH_TICKS + 5);

        maintainControl(owner, now);
        World world = owner.getWorld();
        if (world != null) {
            ResistSound.conquestCharge(world, owner.getLocation(), 0);
            ResistVfx.conquestCharge(world, owner.getLocation(), 0);
        }
        if (voice != null) {
            voice.saySkill(owner, ResistFloatingTextComponent.MOMENT_SKILL_CONQUEST);
        }
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 每刻推进 ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (phase == 0) {
            return;
        }
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            finish(false);
            return;
        }
        int now = Bukkit.getCurrentTick();
        maintainControl(owner, now);
        tickSlam(now);
        if (swing != null && !swing.tick()) {
            swing = null;
        }

        World world = owner.getWorld();
        if (world == null) {
            finish(false);
            return;
        }

        switch (phase) {
            case 1 -> {
                chargeTick++;
                if (chargeTick % 2 == 0) {
                    ResistVfx.conquestCharge(world, owner.getLocation(), chargeTick);
                }
                if (chargeTick % 4 == 0) {
                    ResistSound.conquestCharge(world, owner.getLocation(), chargeTick / 4);
                }
                if (--timer <= 0) {
                    slash(owner, 1);
                    phase = 2;
                    timer = GAP_1_TICKS;
                }
            }
            case 2 -> {
                if (chargeTick++ % 2 == 0) {
                    ResistVfx.conquestCharge(world, owner.getLocation(), chargeTick);
                }
                if (--timer <= 0) {
                    slash(owner, 2);
                    phase = 3;
                    timer = GAP_2_TICKS;
                }
            }
            case 3 -> {
                if (chargeTick++ % 2 == 0) {
                    ResistVfx.conquestCharge(world, owner.getLocation(), chargeTick);
                }
                if (--timer <= 0) {
                    slash(owner, 3);
                    phase = 4;
                    timer = FINISH_TICKS;
                }
            }
            default -> {
                if (--timer <= 0) {
                    finish(anyHit);
                }
            }
        }
    }

    // ───────── 一刀 ─────────

    private void slash(Player owner, int stage) {
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        Location eye = owner.getEyeLocation();
        Vector flat = horizontal(eye.getDirection());
        double radius = stage >= 3 ? FINAL_RADIUS : RADIUS;
        double mult = stage >= 3 ? MULT_FINAL : MULT_FIRST;
        double physical = (passive != null ? passive.swordQiPhysical() : ResistStrugglePassive.SWORD_QI_PHYSICAL) * mult;

        ResistVfx.conquestSlash(world, eye, flat, stage);
        ResistSound.conquestSlash(world, eye, stage);

        //需求 7：每刀都配一把巨型铁剑的对应挥砍（水平扫 / 竖直砸）
        if (swing != null) {
            swing.remove();
        }
        swing = ResistSwordSwing.spawn(world, eye, flat, stage);

        Vector side = new Vector(-flat.getZ(), 0d, flat.getX());   //水平侧向量
        double pushSign = stage == 2 ? -1d : 1d;                    //第一刀向左、第二刀向右
        for (Player target : world.getNearbyPlayers(eye, radius + 2.0d)) {
            if (target == null || target.equals(owner) || !isAlive(target) || !isHostile(target)) {
                continue;
            }
            Location center = target.getLocation().clone().add(0d, 1d, 0d);
            Vector delta = center.toVector().subtract(eye.toVector());
            Vector flatDelta = new Vector(delta.getX(), 0d, delta.getZ());
            if (flatDelta.length() > radius) {
                continue;
            }
            if (flatDelta.dot(flat) <= 0d) {
                continue;   //背后不算（半圆）
            }
            if (Math.abs(delta.getY()) > VERTICAL_TOLERANCE) {
                continue;
            }

            if (vitals != null) {
                vitals.physicalDamage(target, owner, physical);
            }
            if (sante != null) {
                int special = passive != null ? passive.rollSpecial() : 3;
                sante.decreaseSanTE(target.getUniqueId(), (int) Math.round(special * mult));
            }
            target.setNoDamageTicks(0);

            //击退 2（第一刀向左、第二刀向右、第三刀向前）+ 击落（向下 4 格/刻）
            flightBefore.put(target.getUniqueId(), target.getAllowFlight());
            target.setAllowFlight(false);
            target.setFlying(false);
            target.setGliding(false);
            Vector push = stage >= 3 ? flat : side;
            target.setVelocity(push.clone().multiply(pushSign * KNOCKBACK).setY(-SLAM_VELOCITY));
            slamTargets.add(target.getUniqueId());
            slamUntil = Bukkit.getCurrentTick() + SLAM_TICKS;

            //FOV 放大 0.1 秒（每次命中触发）
            ResistFov.punch(owner, Bukkit.getCurrentTick(), 2);

            ResistVfx.conquestHit(world, target.getLocation(), stage);
            ResistSound.conquestHit(world, target.getLocation());
            if (passive != null) {
                passive.addBattleSeconds(owner, BATTLE_ADD, Bukkit.getCurrentTick());
            }
            anyHit = true;
        }
    }

    /** 击落：命中后 0.1 秒内持续下压（2 刻）。 */
    private void tickSlam(int now) {
        if (slamTargets.isEmpty()) {
            return;
        }
        if (now >= slamUntil) {
            restoreFlight();
            return;
        }
        for (UUID id : slamTargets) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline() || target.isDead()) {
                continue;
            }
            target.setFallDistance(0f);
            Vector current = target.getVelocity();
            target.setVelocity(new Vector(current.getX(), -SLAM_VELOCITY, current.getZ()));
        }
    }

    // ───────── 控制 / 收尾 ─────────

    private void maintainControl(Player owner, int now) {
        if (buff == null) {
            return;
        }
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, 10, 1);     //缓慢 II
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, 10, 2);   //抗性 III
        buff.applyPotionEffect(PotionEffectType.JUMP_BOOST, 10, 128); //负跳跃 ⇒ 不可跳
        ResistState.lock(owner, now, 4);                              //续锁（防止到期后漏解）
        ResistState.protectBuffs(owner, now, 10);                     //别让被动覆盖抗性 III
    }

    private void finish(boolean rewardSpeed) {
        Player owner = svc().self().player();
        if (swing != null) {
            swing.remove();
            swing = null;
        }
        phase = 0;
        timer = 0;
        restoreFlight();
        cleanupEffects();
        ResistState.unlock(owner);
        if (owner != null && rewardSpeed && buff != null) {
            buff.applyPotionEffect(PotionEffectType.SPEED, SPEED_REWARD_TICKS, 2);   //速度 III
            ResistState.protectBuffs(owner, Bukkit.getCurrentTick(), SPEED_REWARD_TICKS + 5);
        }
    }

    /** 击落收尾：把原先有飞行权限的玩家还回去（幂等；死亡/换角色也要走）。 */
    private void restoreFlight() {
        for (UUID id : slamTargets) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline() && Boolean.TRUE.equals(flightBefore.get(id))) {
                player.setAllowFlight(true);
            }
        }
        slamTargets.clear();
        flightBefore.clear();
    }

    private void cleanupEffects() {
        if (buff != null) {
            buff.removePotionEffect(PotionEffectType.SLOWNESS);
            buff.removePotionEffect(PotionEffectType.RESISTANCE);
            buff.removePotionEffect(PotionEffectType.JUMP_BOOST);
        }
    }

    // ───────── 工具 ─────────

    private static Vector horizontal(Vector vector) {
        if (vector == null) {
            return new Vector(0d, 0d, 1d);
        }
        Vector flat = new Vector(vector.getX(), 0d, vector.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            return new Vector(0d, 0d, 1d);
        }
        return flat.normalize();
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

    /** 当前阶段（0 = 空闲）。 */
    public int phase() {
        return phase;
    }
}
