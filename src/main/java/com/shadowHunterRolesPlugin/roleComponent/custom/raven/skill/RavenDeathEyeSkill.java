package com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.mainWeapon.RavenRevolverMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenAirMobilityPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenFloatingTextComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 「林晨霜」技能之二：**死亡之眼-末影之眼**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>地面时</b>：0.2 秒内向上跃起 <b>3 格</b>；同时<b>瞬间补齐子弹</b>
 *       （每装一发消耗 2 能量）；在 <b>0.5 秒</b>内锁定周围 <b>r=20</b> 内的所有敌人，
 *       并<b>轮流射出所有</b>"散华"式改弹药（每发间隔 <b>0.1 秒</b>）；</li>
 *   <li><b>地面时</b>：使用后若处于空中 ⇒ 触发浮空机动、刷新机动 CD 间隔、<b>存储一个主动机动</b>；</li>
 *   <li><b>浮空时</b>：快速装填，0.5 秒内锁定 r=20 内所有敌人并轮流射出所有弹药，
 *       <b>每发命中额外回复自己 2 点生命</b>；</li>
 *   <li><b>无论什么状态</b>：使用后周围<b>无敌人</b> ⇒ 刷新一技能 CD；</li>
 *   <li><b>无论什么状态</b>：若此技能击杀敌人 ⇒ 立刻存储一个主动机动，且立刻刷新一技能；</li>
 *   <li>CD <b>20 秒</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一："锁定 0.5 秒"是一个真实的等待段</h2>
 * 需求写的是"在 0.5 秒内锁定…**并**轮流射出" ⇒ 先锁后射，不是同刻齐发。
 * 本类用 {@link #lockUntilTick} 做 10 刻（0.5 秒）的锁定段：这段里每刻画锁定标记
 * （与浮空锁定的观感同源），到期才把名单交给主武器的连发通道。
 *
 * <h2>★★ 口径申报二：射击复用主武器的连发通道，不另造一套</h2>
 * "同浮空状态下射击锁定逻辑一致"是需求原话 ⇒ 索敌与排射**都走**
 * {@link RavenRevolverMainWeapon#lockTargetsInRange} / {@link RavenRevolverMainWeapon#startVolley}
 * （离准心最近的排序 + 人数不够从头再轮 + 每发 0.1 秒 + 每发扣 1 枚弹药）。
 * 好处：两条路的弹道、去重、空仓收尾、回血口径**只可能有一份实现**。
 *
 * <h2>★★ 口径申报三："击杀 ⇒ 充能一层"与被动是同一条规则，不重复发</h2>
 * 被动「浮空机动」已经订阅了"击杀敌人 ⇒ 立刻充 1 层"（需求原话），而本技能那条
 * "若此技能击杀敌人则立即存储一个主动机动"是**同一件事的复述**
 * ⇒ 若本类再发一次，一次击杀会变成 +2 层（静默超标）。
 * 因此本类的击杀订阅**只做"刷新一技能"**那半件，充能交给被动。
 *
 * <h2>★★ 口径申报四："跃起 3 格"是一次瞬时抬升</h2>
 * 与工程既有口径一致（{@code LinShadowRaidSkill} 的 8 格跃进也是同刻 teleport）：
 * 需求里的"0.2 秒"描述的是动作时长，而本类的做法是**一次性把落点算准并落上去**
 * （避免逐刻插值被重力/碰撞吃掉）。落地后立刻进浮空机动，因此"抬起来就停住"是可见的。
 */
public class RavenDeathEyeSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "raven_death_eye_skill";

    /** 冷却（刻）：需求 CD 20 秒。 */
    public static final int COOLDOWN_TICKS = 400;

    /** 能量消耗：0（耗能发生在"每装一发 2 能量"那一步，需求原话）。 */
    private static final int ENERGY_COST = 0;

    /**
     * 每装一发的能量消耗——与主武器**同一口径**（需求 2026-10-11 一并改为 **3 能量**）。
     * <p>★ 技能二的"瞬间补齐子弹"是同一件事（装一发子弹的耗能），因此跟着主武器走。
     */
    public static final int RELOAD_ENERGY_PER_BULLET = 3;

    /** 地面形态的跃起高度（格）：需求"向上跃起 3 格"。 */
    public static final double JUMP_HEIGHT = 3.0d;

    /** 锁定段时长（刻）：需求"在 0.5 秒内锁定"。 */
    public static final int LOCK_TICKS = 10;

    /** 锁定半径（格）：需求"周围 r=20 内所有敌人"。 */
    public static final double LOCK_RANGE = 20.0d;

    /** 散射命中回血量（需求："每发弹药击中敌人将额外回复自己 2 点生命"）。 */
    public static final double LIFE_STEAL_HEAL = 2d;

    /**
     * 击杀归属窗口（刻）：连发结束后的这段时间里发生的击杀，算作"此技能的击杀"。
     *
     * <p>判据不可能 100% 精确（子弹是主武器在飞的弹体、击杀广播不带"是谁的技能"），
     * 因此取"连发窗口 + 3 秒"这条**如实申报**的近似口径：越界即不算。
     */
    public static final int KILL_ATTRIBUTION_TICKS = 60;

    private VitalsComponent vitals;
    private BuffComponent buff;
    private EnergyComponent energy;
    private RavenAirMobilityPassive flight;

    /** 语音组件（技能二那一栏台词）。 */
    private RavenFloatingTextComponent voice;
    private RavenRevolverMainWeapon weapon;
    private RavenSkyStrikeSkill skyStrike;
    private HotbarRenderComponent render;

    /** 击杀订阅（按引用存下来才能在 stop() 里撤销）。 */
    private VitalsComponent.PlayerKilledListener killEntry;

    /** 锁定段到期刻（{@code 0} = 没有进行中的锁定）。 */
    private int lockUntilTick;

    /** 锁定段锁定到的名单（到期后排射）。 */
    private List<Player> pendingTargets = List.of();

    /** 到期排射时是否给射手回血（浮空形态为真）。 */
    private boolean pendingHealOnHit;

    /** 击杀归属窗口到期刻。 */
    private int killWindowUntilTick;

    public RavenDeathEyeSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<RavenDeathEyeSkill> {

        public Specification() {
            super(Component.text("死亡之眼"),
                    List.of(
                            Component.text("地面：0.2 秒内向上跃起 3 格，瞬间补齐子弹（每发 3 能量），"),
                            Component.text("      0.5 秒内锁定 r20 内所有敌人并轮流射出全部弹药（每发间隔 0.1 秒）"),
                            Component.text("地面使用后若处于空中：触发浮空机动、刷新机动 CD 间隔、存储一个主动机动"),
                            Component.text("浮空：快速装填并锁定 r20 内所有敌人齐射，每发命中额外回复自己 2 点生命"),
                            Component.text("周围无敌人 ⇒ 刷新一技能 CD；此技能击杀 ⇒ 刷新一技能 CD"),
                            Component.text("冷却 20 秒")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.ENDER_EYE);
            requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(EnergyComponent.class)
                    .requires(RavenAirMobilityPassive.class)
                    .requires(RavenRevolverMainWeapon.class).requires(RavenSkyStrikeSkill.class)
                    .requires(RavenFloatingTextComponent.class);
        }

        @Override
        public RavenDeathEyeSkill create(String id, ComponentServicesPort services) {
            return new RavenDeathEyeSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        flight = svc().components().get(RavenAirMobilityPassive.class);
        voice = svc().components().get(RavenFloatingTextComponent.class);
        weapon = svc().components().get(RavenRevolverMainWeapon.class);
        skyStrike = svc().components().get(RavenSkyStrikeSkill.class);
        render = getComponent(HotbarRenderComponent.class);
        lockUntilTick = 0;
        pendingTargets = List.of();
        pendingHealOnHit = false;
        killWindowUntilTick = 0;

        //★ 只做"刷新一技能"那半件（充能那半件归被动，见类注释口径申报三）
        if (vitals != null) {
            killEntry = vitals.addPlayerKilledListener(this, event -> {
                if (event == null) {
                    return;
                }
                Player self = svc().self().player();
                if (self != null && event.getKiller() != self) {
                    return;
                }
                if (org.bukkit.Bukkit.getCurrentTick() > killWindowUntilTick) {
                    return;     //不在连发窗口内 ⇒ 不算"此技能的击杀"
                }
                refreshSkyStrike();
            });
        }
    }

    @Override
    public void stop() {
        if (vitals != null && killEntry != null) {
            vitals.removePlayerKilledListener(killEntry);
        }
        killEntry = null;
        lockUntilTick = 0;
        pendingTargets = List.of();
        pendingHealOnHit = false;
        killWindowUntilTick = 0;
        vitals = null;
        buff = null;
        energy = null;
        flight = null;
        voice = null;
        weapon = null;
        skyStrike = null;
        render = null;
    }

    // ───────── 每刻：推进锁定段 ─────────

    @Override
    public void update() {
        if (lockUntilTick <= 0) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            lockUntilTick = 0;
            pendingTargets = List.of();
            return;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        //锁定段：按 0.25 秒的节拍闪烁锁定标记（与浮空锁定的观感同源）
        if (RavenRevolverMainWeapon.isLockBlinkOn(now)) {
            for (Player target : pendingTargets) {
                if (target != null && target.isOnline() && !target.isDead()) {
                    RavenVfx.ravenLockMarker(owner.getWorld(), owner.getLocation(),
                            target.getLocation(), now * 0.4d);
                }
            }
        }
        if (now < lockUntilTick) {
            return;
        }
        lockUntilTick = 0;
        fireVolley(owner);
    }

    /** 把名单交给主武器的连发通道（"轮流射出所有弹药"）。 */
    private void fireVolley(Player owner) {
        if (weapon == null) {
            return;
        }
        List<Player> targets = pendingTargets;
        boolean heal = pendingHealOnHit;
        pendingTargets = List.of();
        pendingHealOnHit = false;
        if (targets.isEmpty()) {
            return;
        }
        int shots = weapon.ammo();      //需求："轮流射出所有'散华'式改弹药" ⇒ 倾泻当前弹匣
        if (shots <= 0) {
            return;     //没有弹药可倾泻（装填被能量限制住的极端情况）
        }
        weapon.startVolley(targets, shots, heal);
        killWindowUntilTick = org.bukkit.Bukkit.getCurrentTick() + KILL_ATTRIBUTION_TICKS;
        RavenSound.ravenLockOnSound(owner.getWorld(), owner.getLocation());
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill() || weapon == null) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        boolean hovering = flight != null && flight.isHovering();

        //① 瞬间补齐子弹（两种形态都要；需求："每装填一发发子弹 2 能量"）
        int loaded = weapon.instantReload(RELOAD_ENERGY_PER_BULLET);
        if (hovering) {
            RavenSound.ravenAirQuickLoadSound(world, owner.getLocation());
            RavenVfx.ravenQuickLoad(world, owner.getLocation().clone().add(0d, 1d, 0d));
        } else {
            RavenSound.ravenDeathEyeReloadSound(world, owner.getLocation());
            RavenVfx.ravenFullReloadBurst(world, owner.getLocation().clone().add(0d, 1d, 0d));
        }
        if (loaded <= 0) {
            //装不进任何一发（弹药已满或能量不足）：给一声落空，但技能照常往下走（锁定仍要做）
            RavenSound.ravenRevolverDryFireSound(world, owner.getLocation());
        }

        //② 地面形态：先向上跃起 3 格（0.2 秒的瞬时抬升，见类注释口径申报四）
        if (!hovering) {
            Location origin = owner.getLocation();
            Location destination = origin.clone();
            destination.setY(jumpTargetY(origin.getY()));
            owner.teleport(destination);
            owner.setFallDistance(0f);
            RavenSound.ravenDeathEyeJumpSound(world, origin);
            RavenVfx.ravenDeathEyeJumpBurst(world, origin);
        }

        //需求"二技能：死亡之眼"那一栏语音
        if (voice != null) {
            voice.saySkill(owner, RavenFloatingTextComponent.MOMENT_SKILL_DEADEYE);
        }

        //③ 锁定 r20 内所有敌人（同一套索敌口径）
        List<Player> targets = weapon.lockTargetsInRange(owner, LOCK_RANGE);

        //④ 无敌人 ⇒ 刷新一技能（需求："若使用后周围无敌人则为自己刷新一技能 cd"）
        if (targets.isEmpty()) {
            refreshSkyStrike();
            RavenSound.ravenDeathEyeRefreshSound(world, owner.getLocation());
            startCooldown();
            return;
        }

        //⑤ 进 0.5 秒锁定段，到期排射（"先锁后射"）
        pendingTargets = targets;
        pendingHealOnHit = hovering;     //需求：浮空形态"每发命中额外回复自己 2 点生命"
        lockUntilTick = org.bukkit.Bukkit.getCurrentTick() + LOCK_TICKS;
        for (Player target : targets) {
            RavenVfx.ravenLockConfirm(world, target.getLocation());
        }

        //⑥ 地面形态：使用后处于空中 ⇒ 触发浮空 + 刷新机动间隔 + 存储一个主动机动（需求原话）
        if (!hovering && flight != null) {
            flight.enterHover();
            flight.grantCharge();
            weapon.resetAirDashInterval();
        }
        startCooldown();
    }

    /** 刷新一技能的 CD（"刷新" = 直接了结它的待落地状态并清掉冷却）。 */
    private void refreshSkyStrike() {
        if (skyStrike != null) {
            skyStrike.settleImmediately();
        }
        Player owner = svc().self().player();
        if (owner != null) {
            RavenSound.ravenDeathEyeRefreshSound(owner.getWorld(), owner.getLocation());
        }
    }

    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **地面形态跃起后的目标 Y**（纯函数）。
     *
     * <p>单独抽出来是为了让"跃起 3 格"这条能被单测钉住 —— 写错时**不报错**，
     * 只是"跳得高一点/低一点"，运行期很难发现。
     */
    public static double jumpTargetY(double originY) {
        return originY + JUMP_HEIGHT;
    }

    /** 锁定段是否已到期（纯函数）。 */
    public static boolean lockExpired(int nowTick, int lockUntil) {
        return lockUntil > 0 && nowTick >= lockUntil;
    }
}
