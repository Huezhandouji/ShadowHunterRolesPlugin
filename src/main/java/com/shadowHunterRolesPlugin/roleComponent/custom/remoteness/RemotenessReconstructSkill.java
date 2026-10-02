package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 冷识（{@code remoteness}）的主动技能「**重构**」（灵魂灯）：**命中即刷新主武器冷却**的窗口。
 *
 * <h2>玩法</h2>
 * 施放后进入 {@link #WINDOW_TICKS}（10 秒）的窗口；窗口内**每当你射出的箭矢命中敌人**，
 * 主武器（{@link RemotenessFrostBowMainWeapon}）的冷却立即被清空 ⇒ 可以连续输出。
 * 窗口走完（10 秒）后才开始冷却 {@link #COOLDOWN_TICKS}（15 秒）——
 * 产品口径「CD-15s，技能结束后开始」，理由与「忘怀」同源：
 * 窗口内不能进冷却，否则"接下来 10 秒内每命中一次都刷新"这条效果会被自己挡住。
 *
 * <h2>★ 本类只做"开合窗口"，刷新动作在武器那一边</h2>
 * 命中判定与冷却刷新都发生在**箭矢命中**那一刻，而那一刻的所有权在
 * {@link RemotenessFrostBowMainWeapon#onProjectileHit}（它才是"这一次命中"的当事者）。
 * 本类因此只提供两个东西：① 生命周期（开窗 / 关窗 / 起冷却）；② 一个只读读口
 * {@link #isWindowActive()},让武器问"现在开着吗"。
 * <p><b>为什么不做成"本类订阅命中事件"</b>：那会让"命中"这条链出现第二个消费者，
 * 而它必须先知道"这支箭是谁的、算不算命中敌人"——那些判据全在武器那里；
 * 抄一份过来就会有两处判据，且两处一旦漂移不会报错。
 *
 * <h2>窗口为什么用节拍任务</h2>
 * 与「忘怀」同源：有明确时长 + 每秒一次的可见反馈（名称后缀 + 粒子），
 * 用 {@link TaskComponent} 的周期任务最直接，空闲时零开销；任务由任务组件统一回收（不泄漏）。
 */
public class RemotenessReconstructSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "remoteness_reconstruct_skill";

    // ───────── 数值（唯一修改点）─────────

    /** 窗口时长（200 刻 = 10 秒）。 */
    private static final int WINDOW_TICKS = 400;

    /** 冷却（300 刻 = 15 秒）：窗口结束之后才开始计时。 */
    private static final int COOLDOWN_TICKS = 600;

    /** 窗口节拍的任务周期（20 刻 = 1 秒）。 */
    private static final int TICK_PERIOD = 20;

    /** 窗口内的环绕粒子半径（格）。 */
    private static final double ORBIT_RADIUS = 1.1d;

    private static final int ORBIT_PARTICLE_COUNT = 6;

    // ───────── 状态 ─────────

    private TaskComponent timer;
    private BuffComponent buff;
    private HotbarRenderComponent render;

    /** 窗口是否激活 —— **武器唯一需要问的一件事**。 */
    private boolean windowActive;

    private ScheduledHandle windowTask;

    private int remainingTicks;

    public RemotenessReconstructSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<RemotenessReconstructSkill> {

        public Specification() {
            super(Component.text("重构"),
                    List.of(
                            Component.text("接下来20秒内，箭矢命中敌人时主武器冷却立即刷新"),
                            Component.text("CD 15s 技能结束后开始")
                    ),
                    COOLDOWN_TICKS,
                    0,
                    Material.SOUL_LANTERN);
            requires(BuffComponent.class).requires(TaskComponent.class).requires(HotbarRenderComponent.class);
        }

        @Override
        public RemotenessReconstructSkill create(String id, ComponentServicesPort services) {
            return new RemotenessReconstructSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        timer = svc().components().get(TaskComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /** 停止生效：取消窗口任务、清掉窗口状态与协作组件引用（不启动冷却 —— 实例已经销毁）。 */
    @Override
    public void stop() {
        cancelWindow();
        windowActive = false;
        remainingTicks = 0;
        timer = null;
        buff = null;
        render = null;
    }

    // ───────── 施放（唯一入口：右键）─────────

    /** 右键：开出 10 秒窗口。窗口已经在跑时再按一次**不重置**（避免刷时长）也不消耗任何东西。 */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        if (!canUse()) {
            return;
        }
        if (windowActive) {
            return;
        }
        Player self = selfPlayer();
        if (self == null) {
            return;
        }

        windowActive = true;
        remainingTicks = WINDOW_TICKS;
        self.getWorld().playSound(self.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);

        cancelWindow();
        windowTask = timer.addScheduleRepeating(this, TICK_PERIOD, TICK_PERIOD, () -> {
            Player owner = selfPlayer();
            if (owner == null || owner.isDead() || !owner.isOnline()) {
                finishWindow();
                return;
            }
            remainingTicks -= TICK_PERIOD;
            if (remainingTicks <= 0) {
                finishWindow();
                return;
            }
            drawOrbit(owner);
            repaint();
        });
        repaint();
    }

    /** 窗口收尾：取消节拍任务、关窗、**启动冷却**（"技能结束后开始"）。 */
    private void finishWindow() {
        cancelWindow();
        remainingTicks = 0;
        windowActive = false;
        startCooldown();
        repaint();
    }

    /** 取消窗口任务（幂等；不碰窗口状态与冷却 —— 那是调用方的语义）。 */
    private void cancelWindow() {
        if (windowTask != null) {
            windowTask.cancel();
            windowTask = null;
        }
    }

    // ───────── 表现 ─────────

    /** 窗口内每秒在角色腰间画一圈灵魂火粒子（窗口开着的可见凭据）。 */
    private void drawOrbit(Player owner) {
        double phase = (WINDOW_TICKS - remainingTicks) / 20d;
        for (int i = 0; i < ORBIT_PARTICLE_COUNT; i++) {
            double angle = phase + (2 * Math.PI * i / ORBIT_PARTICLE_COUNT);
            owner.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                    owner.getLocation().clone().add(Math.cos(angle) * ORBIT_RADIUS, 1.0, Math.sin(angle) * ORBIT_RADIUS),
                    1, 0, 0, 0, 0);
        }
    }

    /** 请求热键栏重绘（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    /** 物品外观：窗口激活时追加剩余秒数 + 附魔光效（只做追加，不重写基类三态画法）。 */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!windowActive) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        Component baseName = meta.displayName() != null ? meta.displayName() : getDisplayName();
        int seconds = Math.max(0, (int) Math.ceil(remainingTicks / 20d));
        meta.displayName(baseName.append(Component.text(" " + seconds + "s").color(NamedTextColor.AQUA)));
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        stack.setItemMeta(meta);
        return stack;
    }

    // ───────── 基类契约 + 对外读口 ─────────

    /** **闸门**：被眩晕 / 沉默时不许施放。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    /** 本组件不参与能量维度（声明耗能 0），故回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    /**
     * 窗口是否激活 —— **消费方 = {@link RemotenessFrostBowMainWeapon}**
     * （它在自己命中敌人的那一刻问一次："现在要不要刷新冷却"）。
     * <p>只读、无副作用；窗口的开合只有本类自己的节拍能改（没有第二个写口）。
     */
    public boolean isWindowActive() {
        return windowActive;
    }

    /** 窗口剩余刻数（未激活时为 0；供探针 / 诊断读，不参与行为决策）。 */
    public int remainingWindowTicks() {
        return windowActive ? Math.max(0, remainingTicks) : 0;
    }
}
