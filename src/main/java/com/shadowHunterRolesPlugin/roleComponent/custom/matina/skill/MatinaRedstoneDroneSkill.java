package com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.MatinaRageVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaKuangPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaFloatingTextComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 「狂躁牧师·马提娜」技能之二：**远程医疗 · 红石**（无人机）。
 *
 * <h2>形态</h2>
 * <b>无人机不是实体，而是持续生成的爱心粒子</b>（需求原话）—— 本组件只持有一个"漂浮点"
 * （{@code position} 的 x/y/z），每帧在那里画爱心，并按模式补上模式标记。
 *
 * <h2>模式（需求逐条）</h2>
 * <b>三个模式</b>：按键在"紧密跟随 ⇄ 派出"之间来回切；召回到位后自动落回紧密跟随。
 * <ul>
 *   <li><b>① 紧密跟随（初始）</b>：<b>永远钉在角色视角右上角</b>，<b>不受距离或位置限制</b>
 *       —— 每刻直接写入坐标，不飞行、不判碰撞（{@link #pinToView(Player)}）；</li>
 *   <li><b>② 派出</b>：<b>按下那一刻</b>采样朝向快照（{@link #snapshotForward(Player)}），
 *       此后沿这条<b>直线持续向前</b>飞行，玩家再转头<b>不改变航向</b>；</li>
 *   <li><b>③ 召回</b>：飞回视角右上角；<b>抵达即自动进入 ①</b>。
 *       召回途中按键无效（"只有到了召回位才能再切"）。</li>
 *   <li><b>跟随 r = {@value #FOLLOW_RADIUS} 内最近的角色，优先不同阵营</b>
 *       （找到谁就以谁为目标：目标为敌 ⇒ 对敌输出，目标为友 ⇒ 支援）；</li>
 *   <li><b>靠近目标（非自己的角色）⇒ 在它坐标上方 {@value #TARGET_HOVER_HEIGHT} 格悬停</b>；
 *       目标移动就继续飞向新的悬停位；</li>
 *   <li><b>飞行速度</b>：派出直线巡航 {@value #SPEED_DISPATCH} 格/秒；
 *       <b>附近有人</b>或<b>召回中</b> ⇒ {@value #SPEED_ASSIST} 格/秒
 *       <p>★ 实现口径：速度按"格/刻"<b>逐刻累加</b>到一个走位储蓄里，攒够 1 格才真的走一格
 *       ⇒ 平均速度**精确**等于声明值；</li>
 *   <li><b>太远（&gt; {@value #MAX_LEASH_DISTANCE} 格）或撞墙</b> ⇒ 自动折返角色身边（变召回模式）；</li>
 *   <li><b>持续 {@value #DRONE_DURATION_TICKS} 刻（15 秒）</b>；</li>
 *   <li><b>每秒</b>治愈 r = {@value #PULSE_RADIUS} 内<b>同阵营</b>角色 {@value #PULSE_HEAL} 点生命，
 *       对不同阵营角色每秒造成 <b>1 秒中毒 III</b>；</li>
 *   <li><b>CD {@value #COOLDOWN_TICKS} 刻（20 秒），技能完全结束后才开始冷却</b>；
 *       <b>能量消耗 {@value #ENERGY_COST}</b>。</li>
 * </ul>
 *
 * <h2>口径申报</h2>
 * <ol>
 *   <li><b>"视角右上角"的落地方式</b>：Minecraft 没有屏幕空间坐标系 ⇒ 本组件用
 *       "视线的前 / 右 / 上"三轴把无人机放在<b>右上稍前</b>的位置（半径 {@value #VIEW_RIGHT_OFFSET} /
 *       高 {@value #VIEW_UP_OFFSET} / 前 {@value #VIEW_FORWARD_OFFSET}）。观感 = 贴着视角右上角，
 *       且随转身一起转（紧密跟随模式**每刻重算**该位置）。</li>
 *   <li><b>"派出方向"只采一次</b>：需求"以无人机释放时朝向为前方，并持续直线向前"
 *       ⇒ 快照存在 {@link #dispatchDirection}，抛出后玩家转头<b>不影响航向</b>。
 *       快照只取<b>水平分量</b>（抬头不该让无人机往上窜）。</li>
 *   <li><b>"每秒治愈同阵营 4 点" = 每个治疗脉冲记 1 点狂暴值</b>（需求：每次成功治疗 +1）。
 *       连续 15 秒 ⇒ 最多 15 点。</li>
 *   <li><b>"召回模式 = 白糖"</b>：原版没有"白糖"这个物品，白糖的对应物是 {@code Material.SUGAR}。
 *       它同时出现在两处：无人机本体旁的 {@code Particle.ITEM} 标记，以及**技能物品本身的材质**
 *       （在役时派出 ⇒ {@code Material.REDSTONE}、其余 ⇒ {@code Material.SUGAR}，见 {@link #buildItem()}）。</li>
 *   <li><b>紧密跟随不做碰撞判定</b>：需求明写"不受距离或者位置限制" ⇒ 硬钉，跟着玩家穿墙也算。</li>
 *   <li><b>撞墙判定</b>用方块的**碰撞箱是否为空**（{@code getBoundingBox().getVolume() &gt; 0}）
 *       ⇒ 台阶、栅栏、草等不挡路的东西不会误判成墙。</li>
 *   <li><b>物品栏上的模式提示</b>：在役时技能物品材质随模式变（派出 = 红石粉，其余 = 白糖），
 *       并**常亮附魔光效**；冷却已走完只是能量不足时，结构空位上也发光（见 {@link #buildItem()}）。</li>
 * </ol>
 */
public class MatinaRedstoneDroneSkill extends Skill implements OperationProvider {

    /** **本组件的登记 id**（★ 知识归属：组件自己）。 */
    public static final String ID = "matina_skill_redstoneDrone";

    /** 冷却：20 秒 = 400 刻（技能完全结束后才开始）。 */
    private static final int COOLDOWN_TICKS = 400;

    /** 能量消耗：20。 */
    private static final int ENERGY_COST = 20;

    /** 无人机持续时间：15 秒 = 300 刻。 */
    private static final int DRONE_DURATION_TICKS = 300;

    /** 一秒的刻数（速度换算用）。 */
    private static final double TICKS_PER_SECOND = 20d;

    /** 跟随目标搜索半径（格）。 */
    private static final double FOLLOW_RADIUS = 8.0d;

    /** 治疗 / 中毒脉冲半径（格）。 */
    private static final double PULSE_RADIUS = 5.0d;

    /** 每秒脉冲的治疗量。 */
    private static final double PULSE_HEAL = 4.0d;

    /** 每秒脉冲给敌方造成的中毒时长（1 秒 = 20 刻）。 */
    private static final int PULSE_POISON_TICKS = 20;

    /** 中毒等级：III（增幅 2）。 */
    private static final int PULSE_POISON_AMPLIFIER = 2;

    /** 飞行速度：**派出**（直线向前 / 追目标）格/秒。 */
    private static final double SPEED_DISPATCH = 4.0d;

    /** 飞行速度：**附近有人 / 召回 / 紧密跟随** 格/秒。 */
    private static final double SPEED_ASSIST = 8.0d;

    /** 离角色超过该距离 ⇒ 自动折返（变召回模式）。 */
    private static final double MAX_LEASH_DISTANCE = 50.0d;

    /** 悬停高度：停在目标（非自己的角色）坐标**上方**这么多格。 */
    private static final double TARGET_HOVER_HEIGHT = 2.5d;

    /** 抵达悬停位 / 召回位的判定半径。 */
    private static final double ARRIVE_DISTANCE = 1.2d;

    /** 视角右上角的横向偏移（格）。 */
    private static final double VIEW_RIGHT_OFFSET = 0.95d;

    /** 视角右上角的竖向偏移（格）。 */
    private static final double VIEW_UP_OFFSET = 0.85d;

    /** 视角右上角的纵向（前）偏移（格）。 */
    private static final double VIEW_FORWARD_OFFSET = 1.2d;

    /** 召唤 / 召回音效音高。 */
    private static final float SUMMON_PITCH = 1.8f;

    /** **派出模式**的技能物品材质（需求：红石粉 = 派出）。 */
    private static final Material DISPATCH_ICON = Material.REDSTONE;

    /** **召回模式**的技能物品材质（需求：白糖 = 召回）。 */
    private static final Material RECALL_ICON = Material.SUGAR;

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private SanTEComponent sante;
    private MatinaKuangPassive kuang;
    /** 施法台词（砸地风格）；装配期声明依赖 ⇒ 这里直接取。 */
    private MatinaFloatingTextComponent floatingText;

    // ───────── 运行期状态（无人机） ─────────

    /**
     * 无人机的**三个模式**（需求原话：召回 → 派出 → 紧密跟随）。
     *
     * <ul>
     *   <li>{@link #FOLLOW_CLOSE}：**初始模式 = 紧密跟随**。永久钉在角色视角右上角，
     *       <b>不受距离与位置限制</b>，每刻直接写位置（见 {@link #pinToView(Player)}）；</li>
     *   <li>{@link #DISPATCH}：**派出**。沿"释放那一刻的朝向快照"直线向前飞
     *       （{@link #dispatchDirection}）；有目标时改为奔赴目标上方的悬停位；</li>
     *   <li>{@link #RECALL}：**召回**。飞回视角右上角；抵达后自动转回 {@link #FOLLOW_CLOSE}。</li>
     * </ul>
     */
    private enum Mode { FOLLOW_CLOSE, DISPATCH, RECALL }

    /** 无人机是否在役。 */
    private boolean active;

    /** 当前模式。 */
    private Mode mode = Mode.FOLLOW_CLOSE;

    /** 已存活刻数。 */
    private int ageTicks;

    /** 走位储蓄（格）：速度按"格/刻"逐刻累加，攒够 1 格才真的走一格 ⇒ 平均速度精确等于声明值。 */
    private double stepBudget;

    /** 粒子相位（每帧推进，让环绕 / 尾迹动起来）。 */
    private double phase;

    /**
     * **派出方向快照**（需求：以无人机释放时朝向为前方，并持续<b>直线</b>向前）。
     * <p>★ 关键：它只在**按下派出**那一次采样（{@link #snapshotForward(Player)}），此后玩家转头
     * <b>不再影响</b>航向 —— 这才是"持续直线向前"。旧实现每帧读 `owner.getEyeLocation()`，
     * 等于"跟着视角拐弯"，与需求相反。
     * <p>它同时被用作"整条直线"的参考：{@link #flightAim} 沿该向量无限延伸，不设终点。
     */
    private final Vector dispatchDirection = new Vector(0d, 0d, 1d);

    /** 无人机当前位置（浮点三元组）。 */
    private double x;
    private double y;
    private double z;

    public MatinaRedstoneDroneSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends Skill.Specification<MatinaRedstoneDroneSkill> {

        public Specification() {
            super(Component.text("远程医疗·红石"),
                    List.of(Component.text("释放无人机：贴着视角持续跟随"),
                            Component.text("再次按下向前飞出（红石粉=派出、白糖=召回）"),
                            Component.text("每秒治疗 5 格内同阵营 4 点生命；对敌方每秒施加 1 秒中毒 III"),
                            Component.text("持续 15 秒")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.REDSTONE);
            requires(VitalsComponent.class).requires(EnergyComponent.class)
                    .requires(BuffComponent.class).requires(SanTEComponent.class)
                    .requires(MatinaKuangPassive.class)
                    .requires(MatinaFloatingTextComponent.class).requires(FactionComponent.class);
        }

        @Override
        public MatinaRedstoneDroneSkill create(String id, ComponentServicesPort services) {
            return new MatinaRedstoneDroneSkill(id, services, this);
        }
    }

    /** **开始生效**：协作组件一次查好缓存进字段（依赖只在 {@code start()} 取）。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        kuang = svc().components().get(MatinaKuangPassive.class);
        floatingText = svc().components().get(MatinaFloatingTextComponent.class);
    }

    // ───────── 施放：第一次召唤 / 之后切换模式 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill()) {
            return;
        }

        //① 已在役 ⇒ 这次按键 = 切换模式（需求：再次按下技能会向前方飞行 / 再次按下召回）
        if (active) {
            toggleMode(owner);
            return;
        }

        //② 冷却中不能重新召唤（挂机期按键不做事；toggle 那一路不受冷却影响）
        if (isCoolingDown()) {
            return;
        }
        if (energy != null && !energy.tryConsume(ENERGY_COST)) {
            return;
        }

        // ★ 施法台词：只在**首次召唤**时喊（切换模式那一路已提前 return，不会刷屏）
        if (floatingText != null) {
            floatingText.onCast(owner);
        }

        //③ 召唤：初始 = **紧密跟随**（贴着视角右上角，不受距离限制）
        Location spawn = recallAnchor(owner);
        x = spawn.getX();
        y = spawn.getY();
        z = spawn.getZ();
        active = true;
        mode = Mode.FOLLOW_CLOSE;
        ageTicks = 0;
        stepBudget = 0d;
        phase = 0d;

        World world = owner.getWorld();
        if (world != null) {
            world.playSound(owner.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, SUMMON_PITCH);
            MatinaRageVfx.hearts(world, spawn, 6, 0.3d);
        }
    }

    /**
     * **切换模式**（需求：再按一次 ⇒ 派出并直线向前飞；召回后 ⇒ 回到紧密跟随）。
     *
     * <table border="1">
     *   <tr><th>按下时模式</th><th>结果</th></tr>
     *   <tr><td>紧密跟随</td><td>派出：<b>此刻</b>采样朝向快照，之后直线向前（{@link #snapshotForward(Player)}）</td></tr>
     *   <tr><td>派出</td><td>召回：飞回视角右上角，抵达后自动转回紧密跟随</td></tr>
     *   <tr><td>召回</td><td>按住不放**无效**（只有到了召回位才能再切）</td></tr>
     * </table>
     */
    private void toggleMode(Player owner) {
        if (mode == Mode.RECALL) {
            //"只有无人机完全召回到身边时才能再次改变模式" —— 召回途中按键无效
            return;
        }
        mode = mode == Mode.DISPATCH ? Mode.RECALL : Mode.DISPATCH;
        if (mode == Mode.DISPATCH) {
            //★ 派出方向的采样点：就是这一帧。此后不再重采。
            snapshotForward(owner);
        }
        stepBudget = 0d;
        World world = owner.getWorld();
        if (world != null) {
            world.playSound(owner.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f,
                    mode == Mode.DISPATCH ? 1.6f : 0.8f);
        }
    }

    /**
     * **立刻采样朝向快照**（需求：以无人机释放时朝向为前方，并持续直线向前）。
     *
     * <p>只取<b>水平分量</b>并归一化（无人机不该因为抬头就往上窜）；
     * 视线几乎垂直时用玩家水平朝向兜底，仍退化则退回 {@code +Z}。
     */
    private void snapshotForward(Player owner) {
        Vector look = owner.getEyeLocation().getDirection();
        Vector flat = new Vector(look.getX(), 0d, look.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            Vector body = owner.getLocation().getDirection();
            flat = new Vector(body.getX(), 0d, body.getZ());
        }
        if (flat.lengthSquared() < 1.0E-6d) {
            flat = new Vector(0d, 0d, 1d);
        }
        dispatchDirection.copy(flat.normalize());
    }

    // ───────── 每刻：飞行 / 目标 / 脉冲 / 粒子 / 到期 ─────────

    @Override
    public void update() {
        if (!active) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            endDrone(true);
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            endDrone(true);
            return;
        }

        //① 到期：15 秒后自行消散，并"技能完全后"才开始冷却
        ageTicks++;
        if (ageTicks >= DRONE_DURATION_TICKS) {
            world.playSound(owner.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.4f);
            endDrone(false);
            return;
        }

        //② 跟随目标（**以无人机自身坐标为搜索中心**，r=8 内最近的角色，优先不同阵营）
        //   ★ 必须以无人机位置为圆心：以玩家为中心的话，无人机飞出几格后就判不到"附近有人"，
        //     速度不升档、也不会追人（旧实现的真实缺陷）。
        Player target = nearestTrackable(new Location(world, x, y, z), owner);

        //③ **紧密跟随 = 硬钉在视角右上角**：不受距离/位置限制 ⇒ 每刻直接写位置，不走"逐格飞行"
        if (mode == Mode.FOLLOW_CLOSE) {
            pinToView(owner);
        } else {
            Location here = new Location(world, x, y, z);
            Location aim = flightAim(owner, target, here);
            //速度：召回 或 **附近有人** ⇒ 8 格/秒；纯派出直线巡航 ⇒ 4 格/秒
            double speed = (mode == Mode.RECALL || target != null) ? SPEED_ASSIST : SPEED_DISPATCH;
            stepBudget += speed / TICKS_PER_SECOND;
            if (stepBudget >= 1d) {
                stepBudget -= 1d;
                step(aim, here);
            }
            //召回抵达视角右上角 ⇒ 自动进入紧密跟随（需求：到了召回位就死死贴着）
            if (mode == Mode.RECALL && arrivedAt(new Location(world, x, y, z), recallAnchor(owner))) {
                mode = Mode.FOLLOW_CLOSE;
            }
        }

        //④ 绳子：离角色超过 50 格 ⇒ 自动折返（变召回模式）
        Location current = new Location(world, x, y, z);
        if (sameWorld(current, owner.getLocation())
                && current.distance(owner.getLocation()) > MAX_LEASH_DISTANCE) {
            mode = Mode.RECALL;
        }

        //⑤ 每秒脉冲：治疗同阵营 / 中毒敌方（每治疗一人记 1 点狂暴）
        if (ageTicks % 20 == 0) {
            pulse(world, current);
        }

        //⑥ 粒子（爱心本体 + 模式标记）
        phase += 0.45d;
        MatinaRageVfx.drone(world, current, mode == Mode.DISPATCH, phase);
    }

    /**
     * **硬钉到视角右上角**（紧密跟随模式的本体）。
     * <p>需求："永远固定于自己角色的视野右上角，<b>不受到距离或者位置限制</b>"
     * ⇒ 直接<b>写入</b>坐标，不做飞行插值、不做距离判定、不做碰撞判定。
     */
    private void pinToView(Player owner) {
        Location view = recallAnchor(owner);
        if (view == null) {
            return;
        }
        x = view.getX();
        y = view.getY();
        z = view.getZ();
    }

    /** **召回位 / 紧密跟随位**（视角右上角；玩家转头它就跟着转）。 */
    private Location recallAnchor(Player owner) {
        Location view = MatinaRageVfx.viewOffset(owner, VIEW_RIGHT_OFFSET, VIEW_UP_OFFSET,
                VIEW_FORWARD_OFFSET);
        return view != null ? view : owner.getEyeLocation().clone();
    }

    /**
     * 目的地（只用于**派出 / 召回**两个"飞过去"的模式；紧密跟随不走这里）：
     *
     * <ol>
     *   <li><b>召回</b> ⇒ 视角右上角（{@link #recallAnchor(Player)}）；</li>
     *   <li><b>派出 + 有目标</b> ⇒ 目标坐标<b>上方 {@value #TARGET_HOVER_HEIGHT} 格</b>的悬停位
     *       （需求：靠近非自己的角色时在它坐标上 2.5 格悬停；目标移动就奔新位置）；</li>
     *   <li><b>派出 + 无目标</b> ⇒ 沿<b>释放时快照</b>的朝向<b>无限延伸的直线</b>上取一点
     *       （需求：持续直线向前）。<b>不读当前视线</b> ⇒ 玩家转头不会改变航向。</li>
     * </ol>
     */
    private Location flightAim(Player owner, Player target, Location here) {
        if (mode == Mode.RECALL) {
            return recallAnchor(owner);
        }
        if (target != null) {
            return hoverAnchor(target);
        }
        //直线巡航：沿快照方向向前取一点（取多长都行 —— step() 只走一格）
        return here.clone().add(dispatchDirection.clone().multiply(1d));
    }

    /** 目标头顶的**悬停位**（目标坐标 + {@value #TARGET_HOVER_HEIGHT} 格）。 */
    private static Location hoverAnchor(Player target) {
        return target.getLocation().clone().add(0d, TARGET_HOVER_HEIGHT, 0d);
    }

    /** 朝目的地走一格；撞墙则折返（变召回模式）。 */
    private void step(Location aim, Location here) {
        Vector delta = aim.toVector().subtract(here.toVector());
        if (delta.lengthSquared() < 1.0E-6d) {
            return;
        }
        Vector unit = delta.clone().normalize();
        Location next = here.clone().add(unit);

        //撞墙判定：目标格是否有实体碰撞箱（草 / 台阶 / 栅栏这类"不挡路"的方块不算墙）
        if (isSolid(next)) {
            mode = Mode.RECALL;
            return;
        }
        x = next.getX();
        y = next.getY();
        z = next.getZ();
    }

    /** 该位置是否是"墙"（有实体碰撞箱的方块）。 */
    private static boolean isSolid(Location at) {
        Block block = at.getBlock();
        return block.getBoundingBox().getVolume() > 0.0d;
    }

    /** 是否已抵达给定锚点（判据 = 视角右上角召回位，不是玩家脚底）。 */
    private static boolean arrivedAt(Location current, Location anchor) {
        if (!sameWorld(current, anchor)) {
            return false;
        }
        return current.distance(anchor) <= ARRIVE_DISTANCE;
    }

    /** 两个位置是否同世界（跨世界比较会抛异常 ⇒ 必须先判）。 */
    private static boolean sameWorld(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && a.getWorld() == b.getWorld();
    }

    /**
     * **每秒脉冲**：r=5 内同阵营治疗 4 点（爱心 + 骨粉 + 升级音效），
     * 不同阵营施加 1 秒中毒 III（村民愤怒 + 紫色粒子）。
     */
    private void pulse(World world, Location center) {
        int healed = 0;
        for (Player candidate : center.getNearbyPlayers(PULSE_RADIUS)) {
            if (candidate == null || !isAlive(candidate)) {
                continue;
            }
            if (svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                //★ 必须直接对"目标"上药水。buff.applyPotionEffect(...) 是**只作用于自己**的口
                //  （内部写死 self().player().addPotionEffect），用它给敌人上毒会把中毒加在自己身上。
                candidate.addPotionEffect(
                        PotionEffectType.POISON.createEffect(PULSE_POISON_TICKS, PULSE_POISON_AMPLIFIER));
                MatinaRageVfx.villagerAngry(world, candidate.getLocation().clone().add(0d, 1.8d, 0d));
                MatinaRageVfx.purpleRising(world, candidate.getLocation().clone(), 1.4d, phase, 8);
            } else {
                if (vitals == null) {
                    continue;
                }
                vitals.heal(candidate, PULSE_HEAL);
                MatinaRageVfx.hearts(world, candidate.getLocation().clone().add(0d, 2.2d, 0d), 3, 0.35d);
                MatinaRageVfx.boneMeal(world, candidate.getLocation().clone().add(0d, 1.0d, 0d));
                world.playSound(candidate.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                healed++;
            }
        }
        if (healed > 0 && kuang != null) {
            kuang.addKuang(healed);
        }
        if (healed == 0) {
            //纯装饰：脉冲节拍给无人机一点小反馈
            MatinaRageVfx.hearts(world, center, 1, 0.15d);
        }
    }

    /**
     * **可追踪目标**：以 {@code center}（= 无人机自身坐标）为圆心，r = {@code FOLLOW_RADIUS} 内
     * <b>最近的角色</b>，<b>优先不同阵营</b>（先在所有敌对里取最近；没有敌对再在友方里取最近），
     * 并永远排除施法者 {@code owner} 本人。
     * <p>★ 搜索中心必须由调用方传入无人机位置 —— 以玩家为中心会让"飞出几格"的无人机视野全空。
     */
    private Player nearestTrackable(Location center, Player owner) {
        if (center == null || center.getWorld() == null) {
            return null;
        }
        List<Player> hostiles = new ArrayList<>();
        List<Player> friendlies = new ArrayList<>();
        for (Player candidate : center.getNearbyPlayers(FOLLOW_RADIUS)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                hostiles.add(candidate);
            } else {
                friendlies.add(candidate);
            }
        }
        Player best = nearest(center, hostiles);
        return best != null ? best : nearest(center, friendlies);
    }

    /** 列表里离参照点最近的一个（空表 ⇒ {@code null}）。 */
    private static Player nearest(Location center, List<Player> candidates) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : candidates) {
            double distance = candidate.getLocation().distanceSquared(center);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /** 存活判定（死亡 / 死亡界面 / 已下线一律排除）。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /**
     * **收工**：清状态；{@code silent} = 不做收尾动作。
     *
     * <p>★ 冷却在这里启动 —— 需求"技能完全后冷却"：召唤 → 15 秒在役 → <b>结束后</b>才起算 20 秒。
     */
    private void endDrone(boolean silent) {
        active = false;
        mode = Mode.FOLLOW_CLOSE;
        ageTicks = 0;
        stepBudget = 0d;
        startCooldown();
        if (!silent) {
            Player owner = svc().self().player();
            if (owner != null) {
                World world = owner.getWorld();
                if (world != null) {
                    MatinaRageVfx.hearts(world, owner.getLocation().clone().add(0d, 1.2d, 0d), 4, 0.3d);
                }
            }
        }
    }

    @Override
    public void stop() {
        active = false;
        mode = Mode.FOLLOW_CLOSE;
        ageTicks = 0;
        stepBudget = 0d;
        phase = 0d;
    }

    // ───────── 读口（排障 / 探针用） ─────────

    /** 无人机是否在役。 */
    public boolean droneActive() {
        return active;
    }

    /** 是否派出模式（红石粉标记）。 */
    public boolean droneDispatched() {
        return mode == Mode.DISPATCH;
    }

    /** 是否正在召回。 */
    public boolean droneReturning() {
        return mode == Mode.RECALL;
    }

    /** 是否处于紧密跟随（初始模式）。 */
    public boolean droneFollowingClosely() {
        return mode == Mode.FOLLOW_CLOSE;
    }

    /** 已存活刻数。 */
    public int droneAgeTicks() {
        return ageTicks;
    }

    /** **闸门放行？**（基类不查容器 ⇒ 用本组件自己的字段判）。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /**
     * **当前能量**（参与能量维度，声明耗能 {@value #ENERGY_COST}）。
     *
     * <p>★ **在役期间一律回"恰好够"** —— 需求："能量只会影响能否释放技能，而不会影响技能
     * **释放后**的状态"。无人机一旦放出去，图标就不该因为能量掉到 20 以下而退化成结构空位 /
     * 灰字 {@code ENERGY LACK}。
     */
    @Override
    protected int currentEnergy() {
        if (active) {
            return getEnergyCost();
        }
        return energy != null ? energy.current() : getEnergyCost();
    }

    // ───────── 技能物品画法（在基类三态之上叠加"模式 + 附魔光效"） ─────────

    /**
     * **技能物品**：基类三态画法 + 本技能自己的两条状态表达。
     *
     * <ol>
     *   <li><b>在役时材质随模式变</b>（需求：红石粉 = 派出，白糖 = 召回）——
     *       这是"无人机现在什么模式"在物品栏上的提示（旧实现只在粒子旁画了个小标记，
     *       物品本身纹丝不动）；</li>
     *   <li><b>附魔光效 = "冷却已走完"的通用提示</b>：
     *       <ul>
     *         <li><b>在役（使用中）⇒ 常亮</b>，且**不受能量多少影响**（能量只决定能否释放）；</li>
     *         <li>不在役、但**冷却已走完只是缺条件（能量不足）** ⇒ 结构空位上也发光，
     *             这样"还在冷却"与"冷却好了只是没能量"一眼能分开；</li>
     *         <li>冷却中 ⇒ 不发光（冷却态仍由基类画成结构空位 + 秒数）。</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * <p>★ 覆写口径照仓库既有先例（{@code CangluBlueIceRevolverSkill#buildItem}）：
     * 先调 {@code super.buildItem()} 拿基类成品，再在其上后处理 ⇒ 识别键 / 名称 / lore 全部保留
     * （键必须保留，否则点击无反应、角色清除时物品残留）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        if (active) {
            //在役：材质 = 当前模式（派出 → 红石粉；召回 → 白糖），光效常亮。
            //★ 唯一例外 = 被眩晕/沉默（DISABLED）：此时基类画的是红屏障，那是"现在使不了"这个
            //  更高优先级的状态 ⇒ **保留屏障**，只叠光效（能量与否不参与这一判断）。
            if (canUse()) {
                stack.setType(mode == Mode.DISPATCH ? DISPATCH_ICON : RECALL_ICON);
            }
            meta = stack.getItemMeta();
            if (meta == null) {
                return stack;
            }
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
            return stack;
        }

        //不在役：冷却已走完却缺条件（能量不足）⇒ 结构空位也加附魔光效
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // ───────── 操作面（排障 / 探针） ─────────

    /**
     * **组件操作面**（照 {@code MatinaKuangPassive} 的先例：把字符串指令薄适配到既有强类型方法）。
     *
     * <pre>
     * state     读：active=true mode=follow_close age=12 pos=1.00,65.00,2.00
     * toggle    写：等价于"再按一次技能"（召回途中按键无效）⇒ 回写后的 state
     * </pre>
     * 三态返回：{@code null} = 未识别 / 拒绝；非空串 = 规范化值。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) {
            return null;
        }
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return null;
        }
        switch (tokens[0]) {
            case "state" -> {
                return tokens.length == 1 ? stateText() : null;
            }
            case "toggle" -> {
                if (tokens.length != 1) {
                    return null;
                }
                Player owner = svc().self().player();
                if (owner == null || !active) {
                    return null;
                }
                toggleMode(owner);
                return stateText();
            }
            default -> {
                return null;
            }
        }
    }

    /** 一行状态文本（排障 / 探针读口）。 */
    private String stateText() {
        return "active=" + active
                + " mode=" + mode.name().toLowerCase(java.util.Locale.ROOT)
                + " age=" + ageTicks
                + " pos=" + String.format(java.util.Locale.ROOT, "%.2f,%.2f,%.2f", x, y, z);
    }
}
