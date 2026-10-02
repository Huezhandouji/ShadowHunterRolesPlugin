package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 冷识（{@code remoteness}）的主动技能「**忘怀**」（灵魂火把）：**标记此处 / 回到此处**。
 *
 * <h2>玩法</h2>
 * <ol>
 *   <li>第一次施放：把**当前脚下**记成标记点，进入 {@link #WINDOW_TICKS}（30 秒）的标记窗口；</li>
 *   <li>窗口内再次施放：立即传送回标记点，窗口结束**并开始冷却**；</li>
 *   <li>窗口自然走完（30 秒内没有再次施放）：窗口结束**并开始冷却**。</li>
 * </ol>
 * 冷却 {@link #COOLDOWN_TICKS}（20 秒）。产品口径「技能结束后开始」正是这个意思：
 * 冷却**不**在第一次施放时启动 —— 否则"再次在 30 秒内使用该技能"会被冷却闸门直接挡住。
 *
 * <h2>★ 为什么"再施放"不会被冷却挡住（这是本技能唯一的实现陷阱）</h2>
 * 施放管道（{@code SkillListener#cast}）的闸门是"**冷却中不派发**"。因此本技能把冷却
 * **推迟到窗口结束那一刻**才启动，于是窗口内 {@code isCoolingDown()} 恒为 {@code false}、
 * 第二次施放能正常到达 {@link #onCast(CastSignal)}。反过来说：**不要**在第一次施放里调
 * {@link #startCooldown()} —— 那会让本技能退化成"只能标记、永远回不去"。
 *
 * <h2>窗口用"自己的节拍任务"而不是"每 tick 的 update()"</h2>
 * 一个有明确时长的窗口（30 秒）+ 一个只需要每秒跑一次的收尾动作，用
 * {@link TaskComponent} 的周期任务表达最直接：空闲时零开销（没在标记就没有任务）。
 * 任务由 {@link TaskComponent} 统一登记 ⇒ 角色清除时自动被取消（不泄漏），
 * 本类再在自己的 {@link #stop()} 里显式取消一次（提前停手的正当事务）。
 *
 * <h2>标记点的可见性</h2>
 * 窗口内每秒在标记点画一圈灵魂火粒子（{@code SOUL_FIRE_FLAME}），并把剩余秒数追加到物品名末尾
 * （每秒请求一次重绘）—— 否则玩家无从知道"还剩多久"。
 *
 * <h2>冷却由组件自启</h2>
 * 与所有技能同规：框架与 listener 都**不**代启动冷却，本组件在"窗口确实结束了"处自己调
 * {@link #startCooldown()}。
 */
public class RemotenessOblivionSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "remoteness_oblivion_skill";

    // ───────── 数值（唯一修改点）─────────

    /** 标记窗口时长（600 刻 = 30 秒）。 */
    private static final int WINDOW_TICKS = 600;

    /** 冷却（400 刻 = 20 秒）：窗口结束（返回或超时）之后才开始计时。 */
    private static final int COOLDOWN_TICKS = 400;

    /** 窗口节拍的任务周期（20 刻 = 1 秒）。 */
    private static final int TICK_PERIOD = 20;

    /** 标记点粒子的显示半径（格）。 */
    private static final double MARK_PARTICLE_RADIUS = 0.45d;

    /** 标记点粒子数（每秒一帧）。 */
    private static final int MARK_PARTICLE_COUNT = 12;

    // ───────── 状态 ─────────

    /** 协作组件（{@code start()} 里一次取好）。 */
    private TaskComponent timer;
    private BuffComponent buff;
    private HotbarRenderComponent render;

    /**
     * 标记点；{@code null} = 没有激活的标记（常态）。
     * <p>存克隆体（不是玩家位置的引用）：位置是不可变值，但 {@code Location} 本身可变 ⇒
     * 存引用会让后续对它的任何原地修改（例如 {@code setY}）连带改掉"记下来的那个点"。
     */
    private Location mark;

    /** 窗口节拍的任务句柄；{@code null} = 没有在跑的窗口。 */
    private ScheduledHandle windowTask;

    /** 窗口剩余刻数（在本组件内累计，不用调度器的次数计数）。 */
    private int remainingTicks;

    public RemotenessOblivionSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<RemotenessOblivionSkill> {

        public Specification() {
            super(Component.text("忘怀"),
                    List.of(
                            Component.text("标记此处；30秒内再次使用则返回标记位置"),
                            Component.text("再次使用或窗口超时后开始冷却"),
                            Component.text("CD 20s（技能结束后开始）")
                    ),
                    COOLDOWN_TICKS,
                    0,
                    Material.SOUL_TORCH);
            requires(BuffComponent.class).requires(TaskComponent.class).requires(HotbarRenderComponent.class);
        }

        @Override
        public RemotenessOblivionSkill create(String id, ComponentServicesPort services) {
            return new RemotenessOblivionSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        timer = svc().components().get(TaskComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /** 停止生效：取消窗口任务并丢弃标记（与"窗口结束"的收尾不同 —— 这里**不**启动冷却）。 */
    @Override
    public void stop() {
        cancelWindow();
        mark = null;
        remainingTicks = 0;
        timer = null;
        buff = null;
        render = null;
    }

    // ───────── 施放（唯一入口：右键）─────────

    /**
     * 右键：没有标记 ⇒ 标记此处；已有标记 ⇒ 返回标记点。
     * <p>其它 trigger（左键 / Q）在本技能上没有语义 —— 原版行为已被 {@code SkillListener} 取消，
     * 这里只留一个"什么都不做"的落点（不消耗、不进冷却，因此玩家不会误触进冷却）。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        if (!canUse()) {
            return;
        }
        Player self = selfPlayer();
        if (self == null) {
            return;
        }

        if (mark == null) {
            beginWindow(self);
        } else {
            recall(self);
        }
    }

    /** 第一次施放：记录标记点、进入 30 秒窗口、起节拍任务（**此处不启动冷却**，见类注释）。 */
    private void beginWindow(Player self) {
        mark = self.getLocation().clone();
        remainingTicks = WINDOW_TICKS;
        World world = self.getWorld();
        world.playSound(self.getLocation(), Sound.BLOCK_SOUL_SAND_PLACE, 1f, 1.4f);

        cancelWindow();
        windowTask = timer.addScheduleRepeating(this, TICK_PERIOD, TICK_PERIOD, () -> {
            Player owner = selfPlayer();
            if (owner == null || owner.isDead() || !owner.isOnline()) {
                finishWindow(false);
                return;
            }
            remainingTicks -= TICK_PERIOD;
            if (remainingTicks <= 0) {
                finishWindow(false);
                return;
            }
            drawMark(owner);
            repaint();
        });
        repaint();
    }

    /** 第二次施放：传送回标记点，随后窗口结束并开始冷却。 */
    private void recall(Player self) {
        Location target = mark;
        World world = self.getWorld();
        if (target != null && target.getWorld() != null && target.getWorld().equals(world)) {
            self.teleport(target);
            world.playSound(self.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.4f);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, self.getLocation().add(0, 1, 0), 40,
                    0.5, 0.6, 0.5, 0.02);
        }
        finishWindow(true);
    }

    /**
     * 窗口收尾：取消节拍任务、丢掉标记、**启动冷却**（"技能结束后开始"）。
     *
     * @param recalled 是否是因为"玩家返回"而收尾（只影响文案；两条路的冷却与清理完全一致）
     */
    private void finishWindow(boolean recalled) {
        cancelWindow();
        mark = null;
        remainingTicks = 0;
        startCooldown();
        repaint();
    }

    /** 取消窗口任务（幂等；不碰标记与冷却 —— 那是调用方的语义）。 */
    private void cancelWindow() {
        if (windowTask != null) {
            windowTask.cancel();
            windowTask = null;
        }
    }

    // ───────── 表现 ─────────

    /** 在标记点画一帧灵魂火粒子（拿不到世界就安静跳过）。 */
    private void drawMark(Player owner) {
        Location target = mark;
        if (target == null || target.getWorld() == null) {
            return;
        }
        target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,
                target.clone().add(0, 1, 0), MARK_PARTICLE_COUNT,
                MARK_PARTICLE_RADIUS, MARK_PARTICLE_RADIUS, MARK_PARTICLE_RADIUS, 0.01);
    }

    /** 请求热键栏重绘（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    /**
     * 物品外观：**窗口激活时**在名称后追加剩余秒数并加一层附魔光效（不写真实附魔）。
     * <p>先取基类的完整画法（三态材质 · 名称着色 · 冷却秒数 · 状态行 · lore · 识别键），
     * 再只做"追加"——不重写整套三态画法（那是冻结面，复制一份必然漂移）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (mark == null) {
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

    // ───────── 基类契约 ─────────

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

    // ───────── 诊断读口（供探针 / 运行级取证；不参与行为决策）─────────

    /** 当前是否有激活的标记（真值口径 = {@link #mark} 非空）。 */
    public boolean hasMark() {
        return mark != null;
    }

    /** 窗口剩余刻数（没有标记时为 0）。 */
    public int remainingWindowTicks() {
        return Math.max(0, remainingTicks);
    }
}
