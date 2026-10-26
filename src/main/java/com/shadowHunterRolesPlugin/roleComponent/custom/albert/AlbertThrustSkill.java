package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 「艾尔伯特」技能之三：**全功率推进**（末影珍珠）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>向指定方向<b>位移 8 格</b>（<b>无视 1 格厚的墙体</b>）；</li>
 *   <li>位移期间获得<b>抗性 4</b>；</li>
 *   <li>在<b>起点</b>留下一个<b>无人机诱饵</b>（不视为无人机点数）：存在 <b>4 秒</b>，
 *       敌人进入半径 {@value AlbertDroneSystem#DECOY_TRIGGER_RADIUS} 格时爆炸，
 *       造成 <b>10 点物理伤害</b>与 <b>2 秒缓慢 III</b> 和 <b>3~10 点特殊值伤害</b>；</li>
 *   <li><b>可储存 2 次</b>，<b>每 8 秒回复一次</b>【每回复一次消耗 6 能量】。</li>
 * </ol>
 *
 * <h2>★ 口径申报一：闸门 = "储存次数" + 0.5 秒释放间隔（两道）</h2>
 * 需求原文只给了"可储存 2 次 / 每 8 秒回复一次"，<b>没有给冷却</b>；但两次储存可以在同一瞬间
 * 连点放出 ⇒ 2026-10-06 按反馈补上 <b>0.5 秒</b>的释放间隔（声明冷却 =
 * {@value #RELEASE_INTERVAL_TICKS} 刻）。
 * <p>两道闸门的分工：
 * <ul>
 *   <li><b>储存次数</b>（{@link #charges}）—— 管"总共能放几次"；</li>
 *   <li><b>释放间隔</b>（声明冷却）—— 管"两次之间至少隔多久"。</li>
 * </ul>
 * 二者互不替代：只剩 1 次时也不会因为冷却而白等，满 2 次时也不能"一秒穿两次墙"。
 *
 * <h2>★★ 口径申报二：为什么必须"先跃起 0.5 格"（2026-10-06 修的真实缺陷）</h2>
 * 反馈原话："有概率在平地不能向前位移，应该是其方块宽度检测器的问题"。
 * <p><b>根因不在检测器，在走廊的高度</b>：突进的走廊是沿<b>视线方向</b>采样的，而视线带俯仰
 * ⇒ 只要瞄得<b>略低</b>（最平常的姿势），走廊几格之内就扎进地面，
 * {@link #dashDestination} 的 {@code isStandable} 把后面的采样点全判成"不合格"
 * ⇒ 终点退化成脚下附近 ⇒ <b>看起来就是"平地也突进不动"</b>。
 * <p>两道修法<b>缺一不可</b>：
 * <ol>
 *   <li><b>先向上跃起 {@value #DASH_LIFT} 格</b>（{@link #pinHop}）—— 走廊整体抬高到地面之上；</li>
 *   <li><b>把方向的向下分量夹成 0</b>（{@link #dashDirection}）—— 只抬不夹，瞄得越低头掉得越快，
 *       8 格内照样扎回地里。<b>向上的分量保留</b>（抬头向上突进是本技能的合法用法）。</li>
 * </ol>
 * <p>落地方式 = <b>两段式</b>：释放那一刻先钉住 {@value #HOP_TICKS} 刻的"跃起位"，
 * 之后由 {@link #advanceDash} 向前突进。不做成"同一 tick 连两次 teleport"是因为那样
 * 只有后一次可见 —— 玩家看到的只是"凭空出现在 8 格外"，起跳那一下被完全吃掉。
 *
 * <h2>★ 口径申报三：次数耗尽 ⇒ 图标进"结构空位"</h2>
 * 需求："当次数耗尽时应该进入结构空位状态"。
 * <p>因为声明冷却只有 0.5 秒，冷却态与"没次数"这两个状态<b>共用同一种材质</b>
 * （{@code STRUCTURE_VOID}）⇒ 按工程既有口径（角色制作五条规范第 4 条），
 * <b>"冷却已走完、只是没次数"这一档也叠附魔光效</b>，这样一眼能分开：
 * <ul>
 *   <li>冷却中 ⇒ 结构空位 + 灰色 + {@code x.xs} 秒数，<b>不发光</b>；</li>
 *   <li>没次数但冷却已走完 ⇒ 结构空位 + 灰色 + {@code " 次数耗尽"}，<b>发光</b>；</li>
 *   <li>有次数 ⇒ 原材质 + <b>物品堆叠数 = 剩余次数</b>，发光。</li>
 * </ul>
 *
 * <h2>★ 口径申报四："无视 1 格厚墙体"怎么落地</h2>
 * 做法 = <b>沿方向连续采样 {@value #DASH_STEP} 格步长，取最远的"人能站住"的点</b>：
 * <ol>
 *   <li>采样点若是实心（就是那堵墙）⇒ 跳过，但<b>不中断扫描</b>；</li>
 *   <li>穿墙之后的空位若合法 ⇒ 被记成新的候选终点（越远越优先）。</li>
 * </ol>
 * ⇒ 1 格厚的墙自然被"跨越"（墙这一格不合格、墙后那格合格）；
 * 而 <b>8 格厚的实心山体</b>不会有合格点 ⇒ 位移距离退化为 0（原地不动），不会把玩家埋进石头里。
 *
 * <h2>★ 口径申报五：位移自己吃掉摔落伤害</h2>
 * 工程既有铁律：<b>技能接管了玩家位置 ⇒ 必须自己吃掉摔落伤害</b>
 * ⇒ 跃起期与突进后都立即 {@code setFallDistance(0f)}
 * （否则"抬起 0.5 格后下落"与"穿墙后坠地"都会掉血）。
 */
public class AlbertThrustSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_skill_thrust";

    /**
     * 两次释放之间的间隔（刻）= 0.5 秒。
     *
     * <p>声明为<b>公开</b>常量而不是私有：它是"两道闸门"里的一道，属于本技能对外的行为契约
     * （单测与文档都引用它），藏起来只会让"改了一处忘了另一处"重现。
     */
    public static final int RELEASE_INTERVAL_TICKS = 10;

    /** 能量消耗 = 0（耗能发生在"回复一次储存"那一步，需求原话"每回复一次消耗6能量"）。 */
    private static final int ENERGY_COST = 0;

    /** 位移距离（格，需求原话"位移 8 格"）。 */
    public static final double DASH_DISTANCE = 8.0d;

    /** 采样步长（格）：越小越精确、越贵；0.5 对 8 格位移足够。 */
    public static final double DASH_STEP = 0.5d;

    /**
     * ★ **释放时先向"视角上方"跃起的高度**（格）。
     *
     * <p>为什么必须抬这一下（2026-10-06 实测反馈的根因）：突进的走廊是沿<b>视线方向</b>采样的，
     * 而视线带俯仰 ⇒ 只要玩家瞄得**略低**，走廊几格之内就扎进地面，
     * {@code isStandable} 把后面的采样点全判掉 ⇒ <b>平地也突进不动</b>
     * （表面像"方块宽度检测器坏了"，其实是走廊高度掉了）。
     * <p>抬起 {@value #DASH_LIFT} 格以后，走廊整体高于地面 ⇒ 平地上恒有合法落点。
     * <p>同时把方向的**向下分量夹成 0**（见 {@link #dashDirection}）—— 两者缺一不可：
     * 只抬不夹，瞄得越低头掉得越快，8 格内照样扎进地里。
     */
    public static final double DASH_LIFT = 0.5d;

    /**
     * **跃起阶段持续刻数**：这段时间把玩家钉在"起点 + {@value #DASH_LIFT} 格"，
     * 之后才向前突进（需求原话"先跃起 0.5 格，随后再向前突进"）。
     * <p>3 刻 = 0.15 秒 —— 够看清"起跳"，又不至于让人悬空太久。
     */
    public static final int HOP_TICKS = 3;

    /** 储存上限（需求原话"可储存 2 次"）。 */
    public static final int MAX_CHARGES = 2;

    /** 储存回复周期（刻）：8 秒（需求原话"每 8 秒回复一次"）。 */
    public static final int CHARGE_REGEN_TICKS = 160;

    /** 每次回复储存的耗能（需求原话"每回复一次消耗6能量"）。 */
    public static final int CHARGE_REGEN_ENERGY_COST = 6;

    /** 位移期间的抗性（需求原话"获得抗性 4"）⇒ 增幅 3。 */
    public static final int DASH_RESISTANCE_AMPLIFIER = 3;

    /** 抗性持续（刻）：1.5 秒（够覆盖位移 + 落地）。 */
    public static final int DASH_RESISTANCE_TICKS = 30;

    private AlbertDroneSystem drones;
    private EnergyComponent energy;
    private BuffComponent buff;
    private VitalsComponent vitals;
    private AlbertFloatingTextComponent floatingText;

    /**
     * 渲染组件（**只用来请求重绘**）。
     *
     * <p>★ 为什么需要它：名称后缀里的"下一次储存回复 CD"是在<b>非冷却期</b>倒计时的，
     * 而框架只在"组件冷却中"才每刻自动刷 ⇒ 不加这一口，玩家看到的永远停在第一次画出来的那个秒数
     * （症状 = "秒数不动"）。工程既有先例：{@code MatinaMedicalShovelMainWeapon}、
     * {@code TekTruthThrustSkill}。
     */
    private HotbarRenderComponent render;

    /** 剩余储存次数。 */
    private int charges = MAX_CHARGES;

    /** 储存回复节拍。 */
    private int regenTick;

    /**
     * 跃起阶段的到期刻（{@code -1} = 没有进行中的突进）。
     * <p>用绝对刻而不是倒计时，是为了和 {@link #regenRemainingTicks()} 之类读数同口径
     * （{@code Bukkit.getCurrentTick()}）。
     */
    private int hopUntilTick = -1;

    /** 跃起的基准位置（= 释放那一刻的脚下位置）；跃起阶段每刻据它把玩家钉在原地正上方。 */
    private Location hopOrigin;

    public AlbertThrustSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<AlbertThrustSkill> {

        public Specification() {
            super(Component.text("全功率推进"),
                    List.of(
                            Component.text("释放时先向视角上方跃起 0.5 格，随后向前突进 8 格（无视 1 格厚的墙体）"),
                            Component.text("位移期间获得抗性 4"),
                            Component.text("在起点留下无人机诱饵，存在 4 秒"),
                            Component.text("诱饵：敌人进入 4 格时爆炸，造成 10 点物理伤害、2 秒缓慢 III 与 3~10 点特殊值伤害"),
                            Component.text("可储存 2 次，每 8 秒回复一次（每回复一次消耗 6 能量；满 2 次后不再扣能量）"),
                            Component.text("未满仓时物品名称后缀显示下一次回复的倒计时"),
                            Component.text("两次释放之间间隔 0.5 秒；次数耗尽时图标进入结构空位")
                    ),
                    RELEASE_INTERVAL_TICKS,
                    ENERGY_COST,
                    Material.ENDER_PEARL);
            requires(AlbertDroneSystem.class).requires(EnergyComponent.class)
                    .requires(BuffComponent.class).requires(VitalsComponent.class)
                    .requires(AlbertFloatingTextComponent.class);
        }

        @Override
        public AlbertThrustSkill create(String id, ComponentServicesPort services) {
            return new AlbertThrustSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        drones = svc().components().get(AlbertDroneSystem.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        floatingText = svc().components().get(AlbertFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        charges = MAX_CHARGES;
        regenTick = 0;
        hopUntilTick = -1;
        hopOrigin = null;
    }

    /** 停用：清掉进行中的突进状态（否则下次启用会带着旧的跃起基准往前冲）。 */
    @Override
    public void stop() {
        hopUntilTick = -1;
        hopOrigin = null;
    }

    /**
     * 每刻：① 回复储存；② 未满仓时请求重绘（让名称后缀的倒计时动起来）。
     *
     * <p>★ <b>存储满 2 后不再消耗能量</b>（需求原话）：满仓时<b>计时归零并直接返回</b> ——
     * 既不加次数、也不扣能量。计时归零这条是刻意的：让"用掉一次"之后总是从完整的 8 秒重新算，
     * 而不是捡到连续周期剩下的零头（否则"满仓时挂了 7.9 秒，一用掉就立刻回满"，手感像作弊）。
     */
    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        // ★ 未满仓 ⇒ 名称后缀在倒计时，必须自己请求重绘（框架只在冷却期自动刷）
        if (charges < MAX_CHARGES && render != null) {
            render.requestRepaint();
        }
        advanceRegen();
        // ★ 突进第二段：跃起结束 ⇒ 向前突进（拆成两段见 advanceDash 的说明）
        advanceDash(owner);
    }

    /**
     * **储存回复节拍**（每 {@value #CHARGE_REGEN_TICKS} 刻一次，每次耗
     * {@value #CHARGE_REGEN_ENERGY_COST} 能量）。
     *
     * <p>★ <b>存储满 {@value #MAX_CHARGES} 后不再消耗能量</b>（需求原话）：
     * 满仓时<b>计时归零并直接返回</b> —— 既不加上限、也不扣能量。
     * 计时归零这条是刻意的：让"用掉一次"之后总是从完整的 8 秒重新算，
     * 而不是捡连续周期剩下的零头（否则"满仓挂了 7.9 秒，一用掉就立刻回满"，手感像作弊）。
     */
    private void advanceRegen() {
        if (charges >= MAX_CHARGES) {
            regenTick = 0;
            return;
        }
        regenTick++;
        if (regenTick < CHARGE_REGEN_TICKS) {
            return;
        }
        regenTick = 0;
        int available = energy != null ? energy.current() : 0;
        // 能量不足 ⇒ 这一拍不回（不欠账、不排队）；满仓时上面已经 return 过了
        if (!shouldRegenCharge(charges, MAX_CHARGES, available)) {
            return;
        }
        if (energy.tryConsume(CHARGE_REGEN_ENERGY_COST)) {
            charges++;
        }
    }

    /**
     * **是否应当消耗能量回复一次储存**（**纯函数**）。
     *
     * <p>★ 需求原话"存储到 2 后不再继续消耗能量存储"⇒ 满仓时<b>连能量都不该扣</b>。
     * 用纯函数表达这条判断，是为了让"满仓时白扣能量"这种静默错误能被单测钉住
     * （它不报错、也不影响手感，只是资源被悄悄吃掉）。
     *
     * @param charges    当前储存数
     * @param maxCharges 储存上限
     * @param energy     当前能量
     */
    public static boolean shouldRegenCharge(int charges, int maxCharges, int energy) {
        return charges < maxCharges && energy >= CHARGE_REGEN_ENERGY_COST;
    }

    /** 距离下一次储存回复还有多少刻（已满仓 / 未在回复 ⇒ 0）。 */
    public int regenRemainingTicks() {
        return charges >= MAX_CHARGES ? 0 : Math.max(0, CHARGE_REGEN_TICKS - regenTick);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || drones == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (charges <= 0) {
            return;
        }
        // 第二道闸门：两次释放之间的 0.5 秒间隔（施放管道也查，这里是双保险）
        if (isCoolingDown()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        charges--;

        Location origin = owner.getLocation();
        //① 起点留诱饵（不视为无人机点数）
        drones.placeDecoy(origin);
        //② 抗性 4（覆盖"跃起 → 突进 → 落地"整段）
        owner.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,
                DASH_RESISTANCE_TICKS, DASH_RESISTANCE_AMPLIFIER, true, false, false));
        //③ 第一段：向视角上方跃起 0.5 格（钉住 HOP_TICKS 刻；第二段由 update() 推进）
        hopOrigin = origin.clone();
        hopUntilTick = org.bukkit.Bukkit.getCurrentTick() + HOP_TICKS;
        pinHop(owner);

        //④ 观感
        AlbertVfx.thrustAlbertOverdrive(world, origin, origin.clone().add(0d, DASH_LIFT, 0d));
        AlbertSound.riptideAlbertOverdriveDashSound(world, origin);
        AlbertSound.trapdoorAlbertDecoyPlaceSound(world, origin);

        if (floatingText != null) {
            floatingText.saySkill(owner, AlbertFloatingTextComponent.MOMENT_SKILL_3);
        }

        // 0.5 秒释放间隔（成功施放才起算）
        startCooldown();
    }

    /**
     * **突进的第二段**（跃起结束 ⇒ 向前突进；由 {@link #update()} 每刻推进）。
     *
     * <p>为什么拆成两段而不是"同一个 tick 里连做两次 teleport"：需求要的是
     * 「<b>先</b>跃起 0.5 格，<b>随后</b>再向前突进」—— 同一 tick 内两次 teleport 只有后一次可见，
     * 玩家看到的只是"凭空出现在 8 格外"，起跳那一下完全被吃掉。
     */
    private void advanceDash(Player owner) {
        if (hopUntilTick < 0) {
            return;
        }
        if (hopOrigin == null || hopOrigin.getWorld() == null
                || hopOrigin.getWorld() != owner.getWorld()) {
            // 跨世界 / 状态丢失 ⇒ 直接收工（不突进），避免把人传到错误的世界
            hopUntilTick = -1;
            hopOrigin = null;
            return;
        }
        if (!owner.isOnline() || owner.isDead()) {
            hopUntilTick = -1;
            hopOrigin = null;
            return;
        }
        if (org.bukkit.Bukkit.getCurrentTick() < hopUntilTick) {
            // 跃起阶段：每刻重新钉住 —— 否则重力会在 3 刻里把这一抬吃掉大半
            pinHop(owner);
            return;
        }

        // ── 跃起结束：向前突进 ──
        hopUntilTick = -1;
        Location from = owner.getLocation();
        hopOrigin = null;
        Vector direction = dashDirection(owner.getEyeLocation().getDirection());
        Location destination = dashDestination(from, direction, DASH_DISTANCE);
        if (destination != null && !destination.equals(from)) {
            owner.teleport(destination);
        }
        // 技能接管了玩家位置 ⇒ 必须自己吃掉摔落伤害
        owner.setFallDistance(0f);
        if (destination != null) {
            AlbertVfx.thrustAlbertOverdrive(owner.getWorld(), from, destination);
        }
    }

    /** 把玩家钉在"跃起基准 + {@value #DASH_LIFT} 格"（并持续清摔落距离）。 */
    private void pinHop(Player owner) {
        if (hopOrigin == null) {
            return;
        }
        owner.teleport(hopOrigin.clone().add(0d, DASH_LIFT, 0d));
        owner.setFallDistance(0f);
    }

    /**
     * ★ **突进方向**（**纯函数**）＝ 视线的水平分量 + **只保留向上的竖直分量**。
     *
     * <p>为什么要夹掉向下的分量（见 {@link #DASH_LIFT} 的说明）：走廊是沿视线采样的，
     * 瞄得越低走廊扎地越快。把"向下"夹成水平 ⇒ 无论怎么瞄，平地上都能稳定前进 8 格；
     * 而"向上"保留 ⇒ 抬头时仍可向上突进（这是本技能的合法用法，不该一起砍掉）。
     *
     * @param look 视线方向（未归一化也可）；{@code null} / 退化 ⇒ 退回 {@code +Z}
     * @return 单位向量
     */
    public static Vector dashDirection(Vector look) {
        if (look == null) {
            return new Vector(0d, 0d, 1d);
        }
        Vector flat = new Vector(look.getX(), Math.max(0d, look.getY()), look.getZ());
        if (flat.lengthSquared() < 1.0E-9d) {
            return new Vector(0d, 0d, 1d);
        }
        return flat.normalize();
    }

    /**
     * **位移终点**：沿 {@code direction} 采样，取最远的"人能站住"的点（**纯逻辑**，可离线穷举）。
     *
     * <p>判定 = 脚那格与头那格都 {@code passable}（不会把玩家塞进方块里）。
     * 穿墙靠的是"不合格就跳过、继续往后采" —— 见类注释口径申报二。
     *
     * @param origin    起点
     * @param direction 方向（内部归一化）
     * @param distance  最大距离（格）
     * @return 终点（一个合格点都没有 ⇒ 返回起点）
     */
    private Location dashDestination(Location origin, Vector direction, double distance) {
        if (origin == null || origin.getWorld() == null || direction == null) {
            return origin;
        }
        Vector unit = direction.clone();
        if (unit.lengthSquared() < 1.0E-9d) {
            return origin;
        }
        unit.normalize();
        Location best = origin.clone();
        for (double t = DASH_STEP; t <= distance + 1.0E-6d; t += DASH_STEP) {
            Location candidate = origin.clone().add(unit.clone().multiply(t));
            if (isStandable(candidate)) {
                best = candidate;
            }
        }
        return best;
    }

    /** 该位置能否站人：脚那格与头那格都得是可通过的。 */
    private static boolean isStandable(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block head = at.clone().add(0d, 1d, 0d).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }
    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    // ───────── 读口 ─────────

    /** 剩余储存次数。 */
    public int charges() {
        return charges;
    }

    /**
     * **技能物品**：基类三态 + 本技能自己的三条状态表达。
     *
     * <ol>
     *   <li><b>有次数</b> ⇒ 原材质 + <b>物品堆叠数 = 剩余次数</b>（1 或 2）+ 附魔光效；</li>
     *   <li><b>未满仓</b> ⇒ 名称后缀显示<b>下一次储存回复的 CD 时间</b>
     *       （需求："当未存储满时，会在物品名称后缀出现 CD 时间"）；</li>
     *   <li><b>没次数 + 冷却已走完</b> ⇒ <b>结构空位</b> + 灰色 + {@code " 次数耗尽"} + 附魔光效
     *       （发光是为了与"冷却中"分开 —— 两者共用 {@code STRUCTURE_VOID} 材质）；</li>
     *   <li><b>没次数 + 冷却中</b> ⇒ 交给基类的冷却画法（结构空位 + 灰色 + {@code x.xs}），不发光；</li>
     *   <li><b>被眩晕 / 沉默</b> ⇒ 保留基类的红屏障 + {@code DISABLED}，本类不覆盖
     *       （那是优先级更高的"现在使不了"）。</li>
     * </ol>
     *
     * <p>★ 覆写口径照仓库既有先例：先调 {@code super.buildItem()} 拿基类成品（识别键 / lore 全保留），
     * 再在它之上改写名称与材质。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        // ── 被眩晕 / 沉默：保留基类的红屏障，不覆盖 ──
        if (!canUse()) {
            if (charges <= 0) {
                stack.setAmount(1);
            } else {
                stack.setAmount(Math.max(1, Math.min(charges, MAX_CHARGES)));
            }
            stack.setItemMeta(meta);
            return stack;
        }

        // ── 次数耗尽 ⇒ 结构空位 ──
        if (charges <= 0) {
            stack.setAmount(1);
            if (isCoolingDown()) {
                // 冷却中：基类已画成"结构空位 + 秒数"，保留（不发光 ⇒ 与下一档区分）
                stack.setItemMeta(meta);
                return stack;
            }
            stack.setType(Material.STRUCTURE_VOID);
            meta = stack.getItemMeta();
            if (meta == null) {
                return stack;
            }
            meta.displayName(nameWith(NamedTextColor.GRAY, " 次数耗尽"));
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
            return stack;
        }

        // ── 还有次数：堆叠数 = 剩余次数 ──
        stack.setAmount(Math.max(1, Math.min(charges, MAX_CHARGES)));
        if (isCoolingDown()) {
            // 释放间隔（0.5 秒）：灰色 + 秒数，不发光
            meta.displayName(nameWith(NamedTextColor.GRAY, " " + secondsText(remainingCooldownTicks())));
        } else if (charges < MAX_CHARGES) {
            // ★ 未满仓：后缀 = 下一次储存回复的倒计时（发光 ⇒ 与"冷却中"区分）
            meta.displayName(nameWith(NamedTextColor.GREEN, " " + secondsText(regenRemainingTicks())));
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } else {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** 名称 + 后缀（着色 + 加粗；与基类的名称画法同口径）。 */
    private Component nameWith(NamedTextColor color, String suffix) {
        return getDisplayName().color(color).decorate(TextDecoration.BOLD)
                .append(Component.text(suffix).color(color).decorate(TextDecoration.BOLD));
    }

    /** 刻 → {@code "x.xs"}（与基类的秒数格式串同口径）。 */
    private static String secondsText(int ticks) {
        return String.format("%.1f", Math.max(0, ticks) / 20f) + "s";
    }
}
