package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 冷识（{@code lethe}）的主动技能「**召圣**」（槽位 2）：**抬箭 → 召回 / 自爆**。
 *
 * <h2>玩法（5 秒窗口，一次成功抬升换一次二选一）</h2>
 * <ol>
 *   <li><b>第一次释放</b>：立刻让 {@link LetheResonanceComponent#liftMarkedArrows()} 把「带标记的箭」
 *       原地上升 {@value #LIFT_HEIGHT} 格；那之后它们的共鸣圈频率变为 1 秒/次且**不再消失**
 *       （频率与"不消失"由共鸣组件在同一个调用里改，本技能不重复设参）。
 *       <b>口径对齐（如实申报）</b>：契约措辞是"全部标记箭"，而 A 道实现只抬**已经落成共鸣圈**的标记
 *       （飞行中的箭没有"停点"可抬，必须等它落地）。本技能按返回值判定成功与否，因此两种口径下行为都一致；
 *       产品文案已写成"全部共鸣圈"，若要求"飞行中的箭也抬"需要 A 道扩展。
 *       物品模型变成 {@code LANTERN}，物品名后缀显示**剩余可再次释放的时间**（形如 {@code 召圣 · 3.2s}）；</li>
 *   <li><b>5 秒窗口内再次释放</b>：{@link LetheResonanceComponent#recallLiftedArrows()} 召回全部升起的圈
 *       （以 {@value #RECALL_SPEED} 格/秒飞向冷识，途中伤害由共鸣组件逐刻结算）；
 *       **召回全部结束之后**才启动冷却；</li>
 *   <li><b>5 秒内没有再次释放</b>：{@link LetheResonanceComponent#detonateLiftedArrows()} 自爆
 *       （r={@value #DETONATE_RADIUS}），**自爆结束之后**启动冷却。</li>
 * </ol>
 *
 * <h2>★ 冷却为什么等"完全结束"，以及"结束"的判据是什么</h2>
 * 契约 §3.1 把召回 / 自爆的逐刻结算放在共鸣组件里：本技能只发令，**结算终点在它那里**。
 * <ul>
 *   <li><b>召回</b>：{@link LetheResonanceComponent#recallLiftedArrows()} 一被调用，被抬起的圈就从
 *       标记表里摘走、变成"逻辑箭"（A 道实现如此）—— 也就是说
 *       {@link LetheResonanceComponent#hasLiftedArrows()} 在召回开始的**同一刻**就变成 {@code false}，
 *       它回答的是"还有没有悬停的圈"，**不是**"召回飞完了没有"。真正的召回终点由
 *       {@link LetheResonanceComponent#isRecalling()}（逻辑箭清空）表达。用前者会导致"箭还在飞、
 *       冷却已经起算"，因此本类用后者；</li>
 *   <li><b>自爆</b>：{@link LetheResonanceComponent#detonateLiftedArrows()} 在同一刻完成伤害结算并
 *       摘掉圈，所以"自爆结束"= 抬升态消失，用 {@code hasLiftedArrows()} 判定即可（仍留一拍等待，
 *       见下一条的上界说明）。</li>
 * </ul>
 * <p><b>关于 {@code isRecalling()} 的契约偏差（如实申报）</b>：契约 §3.1 的函数清单里只有
 * {@code hasLiftedArrows()}，没有 {@code isRecalling()}；但前者在 A 道实现里无法表达"召回飞行中"，
 * 照契约写就满足不了"召回全部结束后才起冷却"这条产品要求。A 道的实现额外公开了
 * {@code isRecalling()}，本类直接使用（**不重复定义**）；若 Lead 决定把它收进契约，§3.1 需要补一行。
 *
 * <h2>★ 为什么等待有一个上界（唯一的防御性常量）</h2>
 * 即使有 {@code isRecalling()}，两个"等待结算"的段仍各给一个上界
 * （{@value #RECALL_WAIT_TIMEOUT_TICKS} / {@value #DETONATE_WAIT_TIMEOUT_TICKS} 刻）：若另一侧的实现
 * 在某个 edge case 里没有把抬升态 / 召回态清掉，本技能就会永远停在"等待结束"，冷却永不启动
 * ⇒ 技能被彻底锁死（不可恢复）。到点无论如何都收尾进冷却：宁可早一点，也不要锁死。
 *
 * <h2>★ 表现归属：抬升 / 召回 / 自爆的粒子与音效由共鸣组件播放，本类不重复</h2>
 * 任务书要求本技能负责 {@code summonLift / summonHover / summonRecallTrail / summonDetonate} 与对应音效；
 * 但 A 道的 {@link LetheResonanceComponent} 已经在这三个动作的**内部**逐点播了
 * {@code summonLift}、{@code summonHover}、{@code summonDetonate}、{@code summonRecallTrail} 与
 * {@code summonLift / summonRecall / summonDetonate} 音效（各自在动作发生点播放，位置最准确：
 * 召回轨迹用的是**真实飞行逻辑箭**的每刻位置，本技能根本拿不到那个位置）。
 * 因此本类只补两处"组件侧没有、且不重复"的表现：
 * <ol>
 *   <li><b>悬停逐刻</b>：{@link LetheVfx#summonHover} 在窗口内每 3 刻画在**被抬起的圈**上
 *       （组件只在抬升那一帧画过一次；"悬停"需要连续帧才读得出来）；</li>
 *   <li><b>被拒提示</b>：场上没有可抬升的圈时播 {@link LetheSound#denied}。</li>
 * </ol>
 * 若 Lead 裁定"表现统一回到技能侧"，则删除 A 道组件内的本地播放、在本类补调用即可
 * （调用点已按动作顺序留空说明，不写死重复调用）。
 *
 * <h2>无标记箭时按不出效果</h2>
 * 抬升数量为 0（场上没有"已落地的共鸣圈"）⇒ 播拒绝音并直接返回：**不消耗冷却、不进窗口**。
 * 这一条是本技能自己判的（施放闸门只查冷却，不查"有没有箭"）。
 */
public class LetheSummonSkill extends Skill {

    /** 本组件的登记 id（与契约 §1 逐字一致）。 */
    public static final String ID = "lethe_summon_skill";

    // ───────── 数值（唯一修改点）─────────

    /** 冷却（200 刻 = 10 秒）：抬升后的窗口**完全结束**（召回完成 / 自爆完成）才开始计时。 */
    private static final int COOLDOWN_TICKS = 200;

    /** 抬升后的可再次释放窗口（100 刻 = 5 秒）。 */
    private static final int WINDOW_TICKS = 100;

    /** 抬升高度（格）—— 只用于文案；真实抬升动作由共鸣组件执行。 */
    private static final int LIFT_HEIGHT = 1;

    /** 召回速度（格/秒）—— 只用于文案；真实飞行由共鸣组件逐刻推进。 */
    private static final int RECALL_SPEED = 40;

    /** 自爆半径（格）—— 只用于文案；真实爆炸由共鸣组件结算。 */
    private static final int DETONATE_RADIUS = 10;

    /**
     * 召回等待上界（200 刻 = 10 秒）：{@value #RECALL_SPEED} 格/秒下可覆盖 400 格（远超常规交战距离）；
     * 到点仍未清态则强制收尾（见类注释「为什么等待有一个上界」）。
     */
    private static final int RECALL_WAIT_TIMEOUT_TICKS = 200;

    /** 自爆等待上界（20 刻 = 1 秒）：自爆是瞬时的，这里只给"逐刻结算"留一拍。 */
    private static final int DETONATE_WAIT_TIMEOUT_TICKS = 20;

    // ───────── 状态 ─────────

    /** 技能当前处于哪一段（一次施放只走一条路，用枚举而不是多个布尔避免非法组合）。 */
    private enum Phase {
        /** 空闲：没有抬升中的圈（常态）。 */
        IDLE,
        /** 5 秒窗口：圈已抬升、共鸣圈挂着，等玩家再按一次。 */
        WINDOW,
        /** 召回已发令，等待"召回全部结束"后进冷却。 */
        RECALLING,
        /** 自爆已发令，等待"抬升态消失"后进冷却。 */
        DETONATING
    }

    private Phase phase = Phase.IDLE;

    /** 窗口剩余刻数（只在 {@link Phase#WINDOW} 有意义）。 */
    private int windowTicksLeft;

    /** 等待段剩余刻数（只在 {@link Phase#RECALLING} / {@link Phase#DETONATING} 有意义）。 */
    private int waitTicksLeft;

    /**
     * 被抬起的圈的位置（窗口内逐刻画悬停用）。
     * <p>由 {@link #pairLiftedPoints(List, List)} 从"抬升前后两次点位查询"配对得出；
     * 配对失败时为空列表 ⇒ 只是少画悬停，**不影响任何判定**（判定只看共鸣组件的公开状态）。
     */
    private final List<Location> liftedPoints = new ArrayList<>();

    // ───────── 协作组件（只在 start() 取；stop() 置空）─────────

    private LetheResonanceComponent resonance;
    private BuffComponent buff;
    private HotbarRenderComponent render;
    private LetheFloatingTextComponent voice;

    /**
     * 重塑技能（**可选**依赖）：它引导期间禁用其它技能。
     * <p>用 {@code requiresOptional} + 判空，避免三个技能互相 {@code requires} 成环。
     */
    private LetheReshapeSkill reshape;

    public LetheSummonSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot(2)} 指定）。 */
    public static final class Specification extends Skill.Specification<LetheSummonSkill> {

        public Specification() {
            super(Component.text("召圣"),
                    List.of(
                            Component.text("第一次：全部共鸣圈原地上升" + LIFT_HEIGHT
                                    + "格，圈改为1秒/次且不再消失"),
                            Component.text("5秒内再次使用：圈以" + RECALL_SPEED
                                    + "格/秒飞回冷识，途中伤害由共鸣结算"),
                            Component.text("5秒内未再次使用：原地自爆，半径" + DETONATE_RADIUS + "格"),
                            Component.text("召回全部结束 / 自爆结束后开始冷却"),
                            Component.text("CD " + (COOLDOWN_TICKS / 20) + "s（技能完全结束后开始）")
                    ),
                    COOLDOWN_TICKS,
                    0,
                    Material.SOUL_LANTERN);
            requires(LetheResonanceComponent.class)
                    .requires(BuffComponent.class)
                    .requires(HotbarRenderComponent.class)
                    .requires(LetheFloatingTextComponent.class)
                    //可选：只用于"重塑引导期禁用其它技能"，缺失不阻断装配
                    .requiresOptional(LetheReshapeSkill.class);
        }

        @Override
        public LetheSummonSkill create(String id, ComponentServicesPort services) {
            return new LetheSummonSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        resonance = svc().components().get(LetheResonanceComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
        voice = svc().components().get(LetheFloatingTextComponent.class);
        reshape = findComponent(LetheReshapeSkill.class);
    }

    /**
     * 停止生效：只把本技能的状态机归位，**不主动召回/自爆、不启动冷却**。
     * <p>抬升中的圈归共鸣组件所有：角色清除时它自己的 {@code stop()} 会 {@code clearAll()}，
     * 本技能越权去替它收尾反而会造出第二条清理路径（谁持有谁清理）。
     */
    @Override
    public void stop() {
        phase = Phase.IDLE;
        windowTicksLeft = 0;
        waitTicksLeft = 0;
        liftedPoints.clear();
        resonance = null;
        buff = null;
        render = null;
        voice = null;
        reshape = null;
    }

    // ───────── 施放（唯一入口：右键）─────────

    /**
     * 右键：空闲 ⇒ 抬升；窗口内 ⇒ 召回。其它阶段（等待结算）一律忽略。
     * <p>闸门（冷却 / 沉默眩晕 / 重塑引导）必须在此自判 —— 见遗忘技能的同类注释。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        if (isCoolingDown()) {
            return;
        }
        if (!canUse()) {
            return;
        }
        if (reshape != null && reshape.isChanneling()) {
            return;   //重塑引导期间禁用其它技能
        }
        Player self = selfPlayer();
        if (self == null || resonance == null) {
            return;
        }
        switch (phase) {
            case IDLE -> lift(self);
            case WINDOW -> recall();
            //RECALLING / DETONATING：结算中，按了也没用（再发一次令会把"正在飞"变成"再抬一次"）
            default -> {
            }
        }
    }

    /** 第一次释放：抬升全部共鸣圈并开 5 秒窗口。 */
    private void lift(Player self) {
        //抬升前取一次点位：共鸣组件的抬升只改"停点高度"，不动点位顺序 ⇒ 两次查询可按序配对，
        //  从而在不新增契约读口的前提下拿到"哪些点真的被抬了"（画悬停用；见 pairLiftedPoints）。
        List<Location> before = safePoints(resonance.markedArrowPoints());
        int lifted = resonance.liftMarkedArrows();
        if (lifted <= 0) {
            //场上没有"已落地的共鸣圈"（或都站不下人）⇒ 抬升为空。不消耗冷却、不进窗口。
            LetheSound.denied(self.getWorld(), self.getLocation());
            return;
        }
        phase = Phase.WINDOW;
        windowTicksLeft = WINDOW_TICKS;
        waitTicksLeft = 0;
        liftedPoints.clear();
        liftedPoints.addAll(pairLiftedPoints(before, safePoints(resonance.markedArrowPoints())));

        //抬升那一帧的粒子/音效由共鸣组件在 liftMarkedArrows() 内部逐点播放（见类注释「表现归属」），
        //  本类不重复调用 summonLift / summonLift 音效。
        say(self, LetheFloatingTextComponent.MOMENT_SUMMON_LIFT);
        repaint();
    }

    /**
     * 窗口内第二次释放：召回全部升起的圈。
     * <p>召回轨迹与音效由共鸣组件在召回动作里播放（真实飞行位置只有它持有），本技能只负责状态机。
     */
    private void recall() {
        phase = Phase.RECALLING;
        waitTicksLeft = RECALL_WAIT_TIMEOUT_TICKS;
        resonance.recallLiftedArrows();
        say(selfPlayer(), LetheFloatingTextComponent.MOMENT_SUMMON_RECALL);
        repaint();
    }

    /** 窗口超时：自爆全部升起的圈（伤害与爆炸表现由共鸣组件结算）。 */
    private void detonate() {
        phase = Phase.DETONATING;
        waitTicksLeft = DETONATE_WAIT_TIMEOUT_TICKS;
        resonance.detonateLiftedArrows();
        say(selfPlayer(), LetheFloatingTextComponent.MOMENT_SUMMON_DETONATE);
        repaint();
    }

    // ───────── 每刻：三个状态的推进（窗口倒计时 / 等待结算）─────────

    @Override
    public void update() {
        if (phase == Phase.IDLE) {
            return;   //常态零开销
        }
        Player self = selfPlayer();
        if (self == null || !self.isOnline() || self.isDead()) {
            //玩家不可用：只归位本技能状态机（抬升中的圈由共鸣组件在角色清除时清）
            phase = Phase.IDLE;
            windowTicksLeft = 0;
            waitTicksLeft = 0;
            liftedPoints.clear();
            return;
        }
        switch (phase) {
            case WINDOW -> tickWindow(self);
            case RECALLING -> tickRecalling();
            case DETONATING -> tickDetonating();
            default -> {
            }
        }
    }

    /** 窗口每刻：倒计时 + 悬停表现；到点自爆。 */
    private void tickWindow(Player self) {
        windowTicksLeft--;
        if (windowTicksLeft <= 0) {
            detonate();
            return;
        }
        if (resonance == null || !resonance.hasLiftedArrows()) {
            //抬升态被外部清掉了（例如共鸣组件清场）：没有可炸的圈 ⇒ 直接进"等待结算"这一拍，
            //下一 tick 就会走 finish() 进冷却（不让技能白白停在窗口里）
            phase = Phase.DETONATING;
            waitTicksLeft = 1;
            return;
        }
        //悬停逐刻（组件只在抬升那一帧画过；"悬停"必须连续帧才读得出来）。
        //  每 3 刻一帧（2026-10-30 按反馈再降频）：summonHover 单次约 60 个粒子，圈多时逐刻画会把粒子预算烧光；
        if (windowTicksLeft % 3 == 0) {
            for (Location point : liftedPoints) {
                LetheVfx.summonHover(point.getWorld(), point);
            }
        }
        repaint();   //物品名后缀的剩余时间每刻要变
    }

    /** 召回等待："召回全部结束"（{@link LetheResonanceComponent#isRecalling()} 转 false）或到上界 ⇒ 进冷却。 */
    private void tickRecalling() {
        waitTicksLeft--;
        if (resonance == null || !resonance.isRecalling() || waitTicksLeft <= 0) {
            finish();
        }
    }

    /** 自爆等待："抬升态消失"或到上界 ⇒ 进冷却。 */
    private void tickDetonating() {
        waitTicksLeft--;
        if (resonance == null || !resonance.hasLiftedArrows() || waitTicksLeft <= 0) {
            finish();
        }
    }

    /**
     * 收尾：技能"完全结束"⇒ 启动冷却（本技能唯一的成功路径）。
     * <p>抬升失败时不会走到这里（那条路径根本不进窗口）。
     */
    private void finish() {
        phase = Phase.IDLE;
        windowTicksLeft = 0;
        waitTicksLeft = 0;
        liftedPoints.clear();
        startCooldown();
        repaint();
    }

    // ───────── 物品外观（模型切换 + 剩余时间后缀）─────────

    /**
     * 底稿双态：窗口内是「召圣 · 3.2s」（灯笼模型，后缀 = 剩余可再次释放时间），
     * 否则走描述符声明的灵魂灯「召圣」。
     * <p>后缀为什么放显示名而不是 lore：契约要求"物品名后缀显示剩余可使用时间"，
     * 而基类三态画法会在这个底稿名上继续着色/加冷却秒数，显示名是唯一贴切的位置。
     */
    @Override
    public ItemStack baseItem(String id) {
        if (phase != Phase.WINDOW) {
            return super.baseItem(id);
        }
        ItemStack stack = new ItemStack(Material.LANTERN);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            double seconds = Math.max(0, windowTicksLeft) / 20d;
            meta.displayName(Component.text("召圣 · "
                    + String.format(Locale.ROOT, "%.1f", seconds) + "s"));
            meta.lore(getDescription());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕 / 沉默时不许施放（listener 不查它，必须这里判）。 */
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

    // ───────── 私有工具 ─────────

    /** 过滤掉世界已失效的点（查询失败当作"没有点"）。 */
    private List<Location> safePoints(List<Location> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        return points.stream()
                .filter(point -> point != null && point.getWorld() != null)
                .toList();
    }

    /**
     * 从"抬升前 / 抬升后"两次点位查询配对出**真的被抬起**的圈。
     *
     * <p>为什么不直接问共鸣组件：契约 §3.1 只给 {@code markedArrowPoints()}（全部标记点）与
     * {@code hasLiftedArrows()}（有没有抬起者），没有"抬起点的位置"读口 ⇒ 若要逐刻画悬停，
     * 只能自己辨认"哪个点高了两格"。
     *
     * <p>配对规则（保守，宁可少画不可画错）：两次查询长度相等时按下标配对；只有"水平坐标相同、
     * 高度差恰好 {@value #LIFT_HEIGHT} 格"的点才被认作抬升点 —— 其余一律跳过。
     * 这条规则依赖"抬升只改停点 Y、不改点位顺序"（A 道实现用插入序稳定的标记表）；
     * 若哪天点位顺序变了，配对会自然全部跳过（悬停少画一层），**不会画到错误位置、也不影响任何判定**。
     */
    private List<Location> pairLiftedPoints(List<Location> before, List<Location> after) {
        if (before.isEmpty() || before.size() != after.size()) {
            return List.of();
        }
        List<Location> result = new ArrayList<>();
        for (int i = 0; i < after.size(); i++) {
            Location oldPoint = before.get(i);
            Location newPoint = after.get(i);
            if (!oldPoint.getWorld().equals(newPoint.getWorld())) {
                continue;
            }
            boolean sameColumn = Math.abs(oldPoint.getX() - newPoint.getX()) < 0.01d
                    && Math.abs(oldPoint.getZ() - newPoint.getZ()) < 0.01d;
            double rise = newPoint.getY() - oldPoint.getY();
            if (sameColumn && Math.abs(rise - LIFT_HEIGHT) < 0.01d) {
                result.add(newPoint.clone());
            }
        }
        return result;
    }

    /** 请求热键栏重绘（拿不到渲染组件就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    /** 喊一句语音（台词走语音组件的词库）。 */
    private void say(Player self, String moment) {
        if (voice != null && self != null) {
            voice.say(self, moment, null);
        }
    }

    // ───────── 诊断读口（供探针 / 运行级取证；不参与行为决策）─────────

    /** 5 秒窗口是否开着。 */
    public boolean isWindowActive() {
        return phase == Phase.WINDOW;
    }

    /** 窗口剩余刻数（窗口没开时为 0）。 */
    public int windowTicksLeft() {
        return phase == Phase.WINDOW ? Math.max(0, windowTicksLeft) : 0;
    }

    /** 是否停在"等待召回/自爆结算"的收尾段（诊断用；正常态应很快回落到 IDLE）。 */
    public boolean isAwaitingSettlement() {
        return phase == Phase.RECALLING || phase == Phase.DETONATING;
    }
}
