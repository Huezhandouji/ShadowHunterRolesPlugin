package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * 「特克」技能一：**刺霄 · 回响碎片**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>向<b>前方 {@value #THRUST_RANGE} 格</b>刺出一击；</li>
 *   <li>造成 <b>{@value #DAMAGE} 点物理伤害</b>并附加 <b>一层「真理」</b>；</li>
 *   <li>同时<b>瞬移到最远的被击中敌方身后</b>；</li>
 *   <li><b>若其后被阻挡则不会瞬移</b>（身后那格是实心方块 / 落点不安全 ⇒ 留在原地）；</li>
 *   <li>冷却 {@value #COOLDOWN_SECONDS} 秒（= {@value #COOLDOWN_TICKS} 刻）。</li>
 * </ol>
 *
 * <h2>「最远的被击中敌方」怎么选</h2>
 * 命中集合 = 以**准星射线**为轴、前方 {@value #THRUST_RANGE} 格内命中的**敌对**玩家
 * （走仓库既有的 {@code SkillUtil#getPlayersInSightLine}，只看可见性不看方块遮挡，与
 * {@code RedSolitaryArroganceSkill} 同口径；再补一层
 * {@code svc().components().get(FactionComponent.class).isHostileTo(uuid)} 的敌对过滤，含"没选角色算敌人"）。
 * 在命中集合里取**离自己最远**的那一个作为瞬移目标。
 *
 * <p>★ <b>命中判定曾经是错的</b>（详见 {@link #collectHits}）：旧实现自算"垂直于轴的侧向距离"，
 * 却把**脚底**坐标与**眼睛**原点相减 ⇒ 竖直分量（约 1.62 格）全被当成侧向偏离
 * ⇒ 阈值 1.3 恒被超出 ⇒ **平地上几乎永远打不到人**。已改为射线工具。
 *
 * <h2>「身后」与「被阻挡」</h2>
 * 身后 = 从该敌人位置<b>沿自己→敌人的水平方向再前进 {@value #BEHIND_DISTANCE} 格</b>的位置
 * （即"越过敌人、站到它的另一边"）。落点判定 = 该处两格高（脚 + 头）都不是实心方块，
 * 且脚下有支撑。任一不满足 ⇒ {@code null}（不瞬移）。
 *
 * <h2>粒子 / 声音</h2>
 * 轨线归 {@link TekVfx#thrustLine}，音效归 {@link TekSound}。
 */
public class TekXiaoSkill extends Skill implements OperationProvider {

    /** 本组件的登记 id。 */
    public static final String ID = "tekXiaoSkill";

    // ───────── 数值口径（唯一修改点）─────────

    /** 刺出距离（格）。 */
    private static final double THRUST_RANGE = 6.5d;

    /**
     * **侧向容差（格）** —— 敌人相对"正前方"这条线的**水平**偏移不得超过它。
     *
     * <p>★ 这是"能不能打中"的关键手感参数。取 1.5 ⇒ 正前方约 3 格宽的长廊内都算命中，
     * 玩家不必把准星对得分毫不差。
     */
    private static final double THRUST_LATERAL_RADIUS = 1.5d;

    /**
     * **高差容差（格）** —— 敌人与自身**脚底对脚底**的高度差不得超过它。
     *
     * <p>★ 口径申报：这里比的是 **脚底 Y**（不是眼睛）。
     * 站在同一平面上的两个玩家 ⇒ 高差恒为 0 ⇒ 必然通过；
     * 只有"明显上下错开"（台阶 / 站在方块上）才会被挡。
     * <p>取 2.5 覆盖一格的台阶与半砖上下，但不至于打到头顶或脚底很远的人。
     */
    private static final double THRUST_VERTICAL_TOLERANCE = 2.5d;

    /** 刺击伤害（物理）。 */
    private static final double DAMAGE = 10d;

    /** 命中后瞬移到敌人身后多少格（格）。 */
    private static final double BEHIND_DISTANCE = 1.2d;

    /** 冷却（刻）：10 秒 = 200 刻。 */
    private static final int COOLDOWN_TICKS = 200;

    /** 冷却（秒；文案用）。 */
    private static final String COOLDOWN_SECONDS = "10";

    /** 能量消耗：0（需求未给耗能）。 */
    private static final int ENERGY_COST = 0;

    /** 轨线粒子间距（格）。 */
    private static final double TRAIL_STEP = 0.35d;

    private VitalsComponent vitals;
    private BuffComponent buff;
    private EnergyComponent energy;
    private TekDestinyPassive destiny;

    // ───────── 上次施放的选取统计（**排障读口**，不参与任何判定） ─────────

    /** 上次施放时"进了搜索半径"的玩家总数。 */
    private int scanned;

    /** 其中因**阵营**被刷掉的数量（"打不到"时先看这个：它 = scanned ⇒ 问题在阵营判定）。 */
    private int rejectedHostile;

    /** 其中因**方位 / 高差**被刷掉的数量（它偏大 ⇒ 调 {@link #THRUST_LATERAL_RADIUS} 等容差）。 */
    private int rejectedGeometry;

    /** 上次施放的命中人数。 */
    private int lastHits;

    public TekXiaoSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<TekXiaoSkill> {

        public Specification() {
            super(Component.text("刺霄"),
                    List.of(Component.text("向前方 " + THRUST_RANGE + " 格刺出一击，造成 "
                                    + (int) DAMAGE + " 点物理伤害并附加一层「真理」"),
                            Component.text("同时瞬移到最远的被击中敌方身后（若其后被阻挡则不会瞬移）"),
                            Component.text("冷却 " + COOLDOWN_SECONDS + " 秒")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.ECHO_SHARD);
            requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(EnergyComponent.class).requires(TekDestinyPassive.class).requires(FactionComponent.class);
        }

        @Override
        public TekXiaoSkill create(String id, ComponentServicesPort services) {
            return new TekXiaoSkill(id, services, this);
        }
    }

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        destiny = svc().components().get(TekDestinyPassive.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || vitals == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        //★ 技能侧自己也判冷却（派发侧会挡，但双保险；见流程文档 §6.2）
        if (isCoolingDown()) {
            return;
        }
        if (energy != null && getEnergyCost() > 0 && !energy.tryConsume(getEnergyCost())) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        Location eye = owner.getEyeLocation().clone();
        Vector axis = horizontal(eye.getDirection());
        if (axis == null) {
            return;
        }

        //① 命中集合：以"正前方一条长廊"为准（见 collectHits 的两次修复说明）
        List<Player> hits = collectHits(owner);
        lastHits = hits.size();

        //② 轨线粒子 + 刺出音效（沿视线方向画一条枪芒）
        Location end = eye.clone().add(axis.clone().multiply(THRUST_RANGE));
        TekVfx.thrustLine(world, eye, end, TRAIL_STEP);
        TekSound.xiaoThrustSound(world, eye);

        //③ 结算伤害 + 叠真理，并找出"最远的被击中敌方"
        Player farthest = null;
        double farthestDist = -1d;
        for (Player victim : hits) {
            vitals.physicalDamage(victim, owner, DAMAGE);
            if (destiny != null) {
                destiny.onHit(victim);
            } else {
                TekTruth.addOne(victim.getUniqueId());
            }
            double dist = victim.getLocation().distanceSquared(owner.getLocation());
            if (dist > farthestDist) {
                farthestDist = dist;
                farthest = victim;
            }
        }

        //④ 瞬移到最远敌人身后（被阻挡则不瞬移）
        if (farthest != null) {
            Location behind = behindSpot(farthest, owner);
            if (behind != null) {
                Location origin = owner.getLocation().clone();
                owner.teleport(behind);
                TekVfx.blinkEcho(world, origin);
                TekSound.xiaoBlinkSound(world, behind);
            } else {
                //身后被挡：留在原地，给一声落空提示
                TekSound.xiaoBlockedSound(world, owner.getLocation());
            }
        }

        startCooldown();
    }

    /**
     * **收集命中**：取"正前方一条长廊"内的**敌对**玩家。
     *
     * <p>三条同时成立才算命中（全部以**水平面 + 脚底**为基准）：
     * <ol>
     *   <li><b>前向距离</b>：{@code 0 ≤ 沿轴分量 ≤ }{@value #THRUST_RANGE} 格；</li>
     *   <li><b>侧向偏移</b>：|水平叉积| ≤ {@value #THRUST_LATERAL_RADIUS} 格；</li>
     *   <li><b>高差</b>：|敌人脚底 Y − 自己脚底 Y| ≤ {@value #THRUST_VERTICAL_TOLERANCE} 格。</li>
     * </ol>
     *
     * <h2>★ 这里修过两次，两次都要记住（改前必读）</h2>
     * <b>第一次（"平地上永远打不到人"）</b>：最初是自己在轴上逐点取样、再算
     * "垂直于轴的投影距离"当侧向距离，但**原点取眼睛、目标取脚底** ⇒ 那 1.62 格的高差
     * 整个落进"侧向距离"里 ⇒ 恒大于阈值 1.3 ⇒ 平地对枪必然落空。
     * <p><b>第二次（"还是打不准"）</b>：改成射线（{@code SkillUtil#getPlayersInSightLine}）后
     * 高差算是算对了，但**射线本质是一条线** —— 目标包围盒必须被那条线穿过才命中，
     * 准星稍微偏一点就落空，实战手感是"打不中"。
     * <p>⇒ <b>正解 = 长廊（corridor）而不是线（ray）</b>：
     * 把"侧向"限制在**纯水平面**内算（用水平叉积，天然不含竖直分量），
     * 高差另用**脚底对脚底**单独设容差。两个量各自干净，互不污染，且足够宽容。
     *
     * <p>注：本条**只看方位与高差，不看方块遮挡**（与 {@code RedSolitaryArroganceSkill} 等既有角色同口径）。
     */
    private List<Player> collectHits(Player owner) {
        List<Player> hits = new ArrayList<>();
        scanned = 0;
        rejectedHostile = 0;
        rejectedGeometry = 0;

        Vector axis = horizontal(owner.getEyeLocation().getDirection());
        if (axis == null) {
            //★ 视线几乎垂直（朝正上/正下）⇒ 退回**身体朝向**，而不是让整个技能空放
            //  （旧实现直接 return ⇒ 抬头/低头时技能毫无反应，且无任何提示）
            axis = horizontal(owner.getFacing().getDirection());
        }
        if (axis == null) {
            //连朝向都退化（不该发生）⇒ 退回 +X，至少技能不是"什么都不做"
            axis = new Vector(1d, 0d, 0d);
        }

        Location origin = owner.getLocation();
        //搜索半径取"前向 + 侧向"的对角上界，避免漏掉斜前方的人
        double searchRadius = Math.hypot(THRUST_RANGE, THRUST_LATERAL_RADIUS);
        for (Player candidate : origin.getNearbyPlayers(searchRadius)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            scanned++;
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                rejectedHostile++;
                continue;
            }
            Location t = candidate.getLocation();
            //① 前向 / 侧向 ② 高差 —— 判定抽成纯函数（可离线单测，见 TekXiaoCorridorTest）
            if (!insideThrustCorridor(
                    t.getX() - origin.getX(),
                    t.getZ() - origin.getZ(),
                    t.getY() - origin.getY(),
                    axis.getX(), axis.getZ())) {
                rejectedGeometry++;
                continue;
            }
            hits.add(candidate);
        }
        return hits;
    }

    /**
     * **命中判据（纯函数 ⇒ 可离线单测见 {@code TekXiaoCorridorTest}）**。
     *
     * <p>三条同时成立：前向距离在 {@code [0, }{@value #THRUST_RANGE}{@code ]}、
     * 侧向偏移 ≤ {@value #THRUST_LATERAL_RADIUS}、高差 ≤ {@value #THRUST_VERTICAL_TOLERANCE}。
     *
     * <p>★ 三个入参都在**同一个基准**上取（脚底对脚底、水平对水平），
     * 因此竖直分量**不可能**混进"侧向偏移"里 —— 这正是本方法存在的理由（见 {@link #collectHits}）。
     *
     * @param dx 敌人相对自己的水平 X 偏移（脚底对脚底）
     * @param dz 敌人相对自己的水平 Z 偏移
     * @param dy 敌人相对自己的**竖直**偏移（脚底对脚底）
     * @param axisX 前向轴的水平 X 分量（已归一化）
     * @param axisZ 前向轴的水平 Z 分量（已归一化）
     */
    static boolean insideThrustCorridor(double dx, double dz, double dy,
                                       double axisX, double axisZ) {
        double along = dx * axisX + dz * axisZ;
        if (along < 0d || along > THRUST_RANGE) {
            return false;
        }
        double lateral = Math.abs(dx * axisZ - dz * axisX);
        if (lateral > THRUST_LATERAL_RADIUS) {
            return false;
        }
        return Math.abs(dy) <= THRUST_VERTICAL_TOLERANCE;
    }

    /** 暴露给单测的阈值。 */
    static double thrustRangeForTest() {
        return THRUST_RANGE;
    }

    /** 暴露给单测的阈值。 */
    static double thrustLateralRadiusForTest() {
        return THRUST_LATERAL_RADIUS;
    }

    /** 暴露给单测的阈值。 */
    static double thrustVerticalToleranceForTest() {
        return THRUST_VERTICAL_TOLERANCE;
    }

    /**
     * **敌人身后的落点**：从敌人位置沿"自己 → 敌人"的水平方向再前进 {@link #BEHIND_DISTANCE} 格。
     *
     * @return 可行落点；被阻挡 / 不安全 ⇒ {@code null}
     */
    private Location behindSpot(Player enemy, Player owner) {
        Vector dir = horizontal(enemy.getLocation().toVector()
                .subtract(owner.getLocation().toVector()));
        if (dir == null) {
            return null;
        }
        Location spot = enemy.getLocation().clone().add(dir.multiply(BEHIND_DISTANCE));
        return isSafeStanding(spot) ? spot : null;
    }

    /** 落点是否安全：脚 + 头两格非实心，且脚下有支撑（不掉进虚空）。 */
    private static boolean isSafeStanding(Location spot) {
        if (spot == null || spot.getWorld() == null) {
            return false;
        }
        Location feet = spot.clone();
        Location head = spot.clone().add(0d, 1d, 0d);
        Location below = spot.clone().add(0d, -0.2d, 0d);
        return !isSolid(feet) && !isSolid(head) && isSolid(below);
    }

    /** 该处是否被实心方块占据。 */
    private static boolean isSolid(Location at) {
        Block block = at.getBlock();
        return block.getBoundingBox().getVolume() > 0d;
    }

    /** 水平化 + 归一化（近乎垂直 ⇒ 返回 {@code null}）。 */
    private static Vector horizontal(Vector vector) {
        if (vector == null) {
            return null;
        }
        Vector flat = new Vector(vector.getX(), 0d, vector.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            return null;
        }
        return flat.normalize();
    }

    /** 存活判定。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    // ───────── 操作面（排障 / 探针） ─────────

    /**
     * **组件操作面**（把字符串指令薄适配到既有读口）。
     *
     * <pre>
     * state   读：hits=1 scanned=3 rejectedHostile=0 rejectedGeometry=2
     *              range=6.5 lateral=1.5 vertical=2.5
     * </pre>
     *
     * <p>★ 用途 = 定位"为什么打不到人"（每个数字对应一层判据）：
     * <ul>
     *   <li>{@code scanned} = 半径内共有几个玩家（0 ⇒ 人根本不在附近，或范围太小）；</li>
     *   <li>{@code rejectedHostile} ≈ {@code scanned} ⇒ **阵营**判定把它们全刷了
     *       （同阵营 / 有一方在创造·旁观模式）；</li>
     *   <li>{@code rejectedGeometry} 偏大 ⇒ 人在附近但**方位或高差**不合格
     *       ⇒ 调 {@code lateral} / {@code vertical}；</li>
     *   <li>{@code hits} = 最终命中数（0 就确实没打中，问题在上面的某一层）。</li>
     * </ul>
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) {
            return null;
        }
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length != 1 || tokens[0].isEmpty()) {
            return null;
        }
        if (!"state".equals(tokens[0])) {
            return null;
        }
        return "hits=" + lastHits
                + " scanned=" + scanned
                + " rejectedHostile=" + rejectedHostile
                + " rejectedGeometry=" + rejectedGeometry
                + " range=" + THRUST_RANGE
                + " lateral=" + THRUST_LATERAL_RADIUS
                + " vertical=" + THRUST_VERTICAL_TOLERANCE;
    }
}
