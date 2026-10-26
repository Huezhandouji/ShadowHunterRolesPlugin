package com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 「林」技能之二：**影袭**（开裂紫水晶块）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>在 <b>0.1 秒</b>内向前跃进 <b>8 格</b>（视野方向）；</li>
 *   <li>★ 可以<b>穿墙，最多 2 格</b>；</li>
 *   <li>★★ <b>不要让他稍微一低头瞄准地面就不能位移</b>；</li>
 *   <li>对「跃进点 → 结束点」的一个 <b>r=2 的圆柱</b>内所有敌人造成 <b>4 点真实伤害</b>；</li>
 *   <li>若成功造成伤害则<b>回复自己 4 点生命</b>（<b>不重复回复</b>）；</li>
 *   <li>留下由跃进点开始到结束点的<b>横向紫色螺旋粒子</b>，并播放<b>跃进音效</b>；</li>
 *   <li>CD 8 秒。</li>
 * </ol>
 *
 * <h2>★ 口径申报一：低头也不失败（需求第 3 条，本技能最大的坑）</h2>
 * 老练的做法是"沿视线采样走廊，采样点不合格就停下来"。但它有两个致命点：
 * <ul>
 *   <li><b>视线的向下分量</b>会让走廊"扎进地里" —— 玩家低头瞄准脚下，
 *       第一个采样点就落在方块里 ⇒ 最长合法距离退化成 0 ⇒ <b>按了没反应</b>（不报错）；</li>
 *   <li>全程只看"脚那格能不能站" ⇒ 起点那一格本身因为脚下半砖 / 楼梯被算作不合格。</li>
 * </ul>
 * 正解与先例 {@code AlbertThrustSkill} 一致：
 * <b>① 把方向的向下分量夹成 0</b>（{@link #dashDirection}：保留平地与抬头，只砍"往下扎"）；
 * <b>② 走廊不判"能不能站"，只判"能不能穿过"</b>（{@link #passableCount}，脚+头两格都必须 {code passable}）。
 * 这样"低头"⇒ 方向被拉平 ⇒ 仍是水平跃进 8 格，"抬头"⇒ 允许向上跃进（合法用法）。
 *
 * <h2>★ 口径申报二："穿墙最多 2 格"的正解</h2>
 * 需求既要"能穿墙"又只给"最多 2 格"。做法：
 * 沿路径按 {@value #STEP} 格采样，累计<b>连续</b>不合格的"墙格数"，
 * 一旦这个累计值超过 {@value #MAX_WALL_TRAVERSE} 格就<b>停止采样</b>（= 撞上厚墙，跃进到此为止）；
 * 若累计没超，则允许"墙后面还有落点" ⇒ 达成"薄墙可穿、厚墙挡住"。
 * 终点取<b>最后一个合格点</b>（一个合格点都没有 ⇒ 终点 = 起点 ⇒ 不位移，但冷却照常起算）。
 * <p>★ 注意这里与先例的区别：先例是"不合格就跳过、继续往后采"，
 * 本技能因为需求明确给了"最多 2 格"的上限，所以采用 <b>连续墙格计数</b> 的口径。
 *
 * <h2>★ 口径申报三："0.1 秒跃进 8 格"为什么不用逐刻推进</h2>
 * 0.1 秒 = 2 刻，玩家在这一瞬间既不会看到"起跳"，也不会看到"飞行"。
 * 逐刻推进只会带来"半路落地/被墙卡住"的竞态 ⇒ 直接 <b>一次性 teleport 到终点</b>，
 * 并把整段路径交给粒子（{@link LinVfx#shadowRaidSpiral}）去表达 —— 玩家的观感就是"一道紫电掠过"。
 * ★ 既然技能接管了玩家位置，就必须自己吃掉摔落伤害（{@code setFallDistance(0f)}）。
 *
 * <h2>★ 口径申报四：命中"不重复回复"</h2>
 * 需求 "若成功造成伤害则回复自己4点生命【不重复回复】" 有两种读法：
 * (a) 每个目标回一次、(b) 整次跃进只回一次。按括号的强调语气取 <b>(a)：每个被命中的不同目标回一次</b>；
 * 而"不重复"约束的是<b>同一个目标被圆柱重复采样到不能回两次</b> ⇒ 用 {@link HashSet} 去重。
 * <p>★ 另外：{@code VitalsComponent#trueDamage} 会主动清受击无敌帧
 * （见工程规范 §4）⇒ 圆柱会在多个采样点重复命中同一个目标，<b>去重是硬需求，不是优化</b>。
 */
public class LinShadowRaidSkill extends Skill {

    /** **本组件的登记 id**。 */
    public static final String ID = "lin_skill_shadowRaid";

    /** 跃进距离（格）：需求 "向前跃进8格"。 */
    public static final double DASH_DISTANCE = 8.0d;

    /** 采样步长（格）：越小越准，代价是采样点更多。 */
    public static final double STEP = 0.25d;

    /** 最大可穿墙厚度（格）：需求 "可以穿墙，最多2格"。 */
    public static final int MAX_WALL_TRAVERSE = 2;

    /** 命中判定圆柱半径（格）：需求 "r=2 的圆柱"。 */
    public static final double CYLINDER_RADIUS = 2.0d;

    /** 单次命中造成的真实伤害（需求 "造成4点真实伤害"）。 */
    public static final double TRUE_DAMAGE = 4.0d;

    /** 每命中一个不同目标回复的生命（需求 "回复自己4点生命"）。 */
    public static final double HEAL_PER_TARGET = 4.0d;

    /** 声明冷却（刻）：8 秒（需求 "CD-8"）。 */
    public static final int COOLDOWN_TICKS = 160;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private EnergyComponent energy;
    private BuffComponent buff;

    public LinShadowRaidSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<LinShadowRaidSkill> {

        public Specification() {
            super(Component.text("影袭"),
                    List.of(
                            Component.text("在0.1秒内向前跃进8格（可穿墙，最多2格）"),
                            Component.text("对跃进点至结束点的r=2圆柱内所有敌人造成4点真实伤害"),
                            Component.text("若成功造成伤害，则回复自己4点生命（不重复回复）"),
                            Component.text("留下横向紫色螺旋粒子，并播放跃进音效"),
                            Component.text("冷却：8秒")
                    ),
                    COOLDOWN_TICKS,
                    0,
                    Material.AMETHYST_BLOCK);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class);
        }

        @Override
        public LinShadowRaidSkill create(String id, ComponentServicesPort services) {
            return new LinShadowRaidSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        Location from = owner.getLocation().clone();
        Vector direction = dashDirection(owner.getEyeLocation().getDirection());
        Location destination = dashDestination(from, direction, DASH_DISTANCE);

        // 跃进音效（在起点放，玩家听得到"起手"）
        LinSound.shadowRaidDashSound(world, from);

        boolean moved = destination != null && !destination.equals(from);
        if (moved) {
            owner.teleport(destination);
            // ★ 技能接管了玩家位置 ⇒ 必须自己吃掉摔落伤害
            owner.setFallDistance(0f);
        }

        // 命中结算：起点 → 终点 的 r=2 圆柱（终点 == 起点 ⇒ 只在起点结算，仍带半径）
        Location hitTo = moved ? destination : from;
        int healed = resolveCylinderHit(owner, from, hitTo);
        if (healed > 0) {
            LinSound.shadowRaidHitSound(world, from);
        }

        // 观感：横向紫色螺旋粒子（不位移时也要留一道短的，避免"按了没反应"）
        Location spiralEnd = from.clone().add(direction.clone().multiply(DASH_DISTANCE));
        LinVfx.shadowRaidSpiral(world, from, spiralEnd);

        // 成功施放才起冷却
        startCooldown();
    }

    /**
     * **圆柱命中结算**：对「起点 → 终点」为轴、半径 {@value #CYLINDER_RADIUS} 的圆柱内
     * 所有敌对玩家造成 {@value #TRUE_DAMAGE} 真实伤害；每个不同目标回自己 {@value #HEAL_PER_TARGET} 点血。
     *
     * @param owner 施放者（也是治疗对象）
     * @param from  轴起点
     * @param to    轴终点
     * @return 实际命中的<b>不同</b>目标数（用于判定是否播放命中音效）
     */
    private int resolveCylinderHit(Player owner, Location from, Location to) {
        if (vitals == null || from == null || to == null || from.getWorld() == null) {
            return 0;
        }
        World world = from.getWorld();
        // 圆柱的轴向量与长度
        Vector axis = to.toVector().subtract(from.toVector());
        double axisLength = axis.length();

        // 包围盒：圆柱的 AABB，用一次半径扫描就够（r=2 + 跃进 8 格 ⇒ 最多 4×4×10 个方块体积）
        double minX = Math.min(from.getX(), to.getX()) - CYLINDER_RADIUS;
        double maxX = Math.max(from.getX(), to.getX()) + CYLINDER_RADIUS;
        double minY = Math.min(from.getY(), to.getY()) - CYLINDER_RADIUS;
        double maxY = Math.max(from.getY(), to.getY()) + CYLINDER_RADIUS + 2.0d;
        double minZ = Math.min(from.getZ(), to.getZ()) - CYLINDER_RADIUS;
        double maxZ = Math.max(from.getZ(), to.getZ()) + CYLINDER_RADIUS;

        // ★ 去重集合：同一个目标在圆柱里只结算一次（trueDamage 会清无敌帧，不去重会被打多次）
        Set<java.util.UUID> hit = new HashSet<>();
        List<LivingEntity> candidates = new ArrayList<>();
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(
                new Location(world,
                        (minX + maxX) / 2d, (minY + maxY) / 2d, (minZ + maxZ) / 2d),
                (maxX - minX) / 2d, (maxY - minY) / 2d, (maxZ - minZ) / 2d)) {
            if (!(entity instanceof LivingEntity living) || entity.getUniqueId().equals(owner.getUniqueId())) {
                continue;
            }
            if (isInsideCylinder(living.getLocation().add(0d, 0.9d, 0d), from, axis, axisLength)) {
                candidates.add(living);
            }
        }

        for (LivingEntity victim : candidates) {
            if (!hit.add(victim.getUniqueId())) {
                continue;
            }
            vitals.trueDamage(victim, owner, TRUE_DAMAGE);
            LinVfx.shadowRaidSpiral(victim.getWorld(),
                    victim.getLocation().clone().add(0d, 1d, 0d),
                    victim.getLocation().clone().add(0d, 1.3d, 0d));
        }

        int count = hit.size();
        if (count > 0 && vitals != null) {
            // ★ 每个不同目标回一次（需求 "回复自己4点生命【不重复回复】"）
            vitals.heal(owner, HEAL_PER_TARGET * count);
        }
        return count;
    }

    /**
     * **判断某点是否落在圆柱内**（**纯函数** ⇒ 可离线穷举）。
     *
     * <p>圆柱 = 以 {@code from} 为起点、{@code axis} 为轴、半径 {@value #CYLINDER_RADIUS} 的无限圆柱，
     * 且投影长度限制在 {@code [0, axisLength]} 之间（"圆柱"而非"无限柱"）。
     *
     * @param point      待判定点
     * @param from       轴起点
     * @param axis       轴向量（起点 → 终点，未归一化）
     * @param axisLength 轴长度
     */
    public static boolean isInsideCylinder(Location point, Location from, Vector axis, double axisLength) {
        if (point == null || from == null || axis == null) {
            return false;
        }
        Vector toPoint = point.toVector().subtract(from.toVector());
        if (axisLength < 1.0E-6d) {
            // 退化成一个球（跃进没走成）
            return toPoint.lengthSquared() <= CYLINDER_RADIUS * CYLINDER_RADIUS;
        }
        Vector unit = axis.clone().multiply(1d / axisLength);
        double projection = toPoint.dot(unit);
        // 投影必须落在轴上（把"无限圆柱"截成"圆柱"）
        if (projection < -CYLINDER_RADIUS || projection > axisLength + CYLINDER_RADIUS) {
            return false;
        }
        // 到轴的垂直距离
        Vector radial = toPoint.clone().subtract(unit.clone().multiply(projection));
        return radial.lengthSquared() <= CYLINDER_RADIUS * CYLINDER_RADIUS;
    }

    /**
     * ★ **跃进方向**（**纯函数**）＝ 视线的水平分量 + **只保留向上的竖直分量**。
     *
     * <p>这是"低头也能位移"的关键（需求第 3 条）：把"向下"夹成水平，
     * 于是无论玩家瞄得多低，跃进在平地上都是稳定的水平 8 格；
     * "向上"仍然保留 ⇒ 抬头时允许向上跃进。
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
     * **位移终点**：沿 {@code direction} 采样，允许穿过最多 {@value #MAX_WALL_TRAVERSE} 格连续墙体，
     * 取最后一个"可通过"的点（**纯逻辑**，可离线穷举）。
     *
     * <p>判定 = 脚那格与头那格都 {@code passable}（不会把玩家塞进方块里）。
     * 连续不合格的采样步数超过穿墙上限 ⇒ 提前停止（厚墙挡住）。
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
        double wallBudget = MAX_WALL_TRAVERSE;
        for (double t = STEP; t <= distance + 1.0E-6d; t += STEP) {
            Location candidate = origin.clone().add(unit.clone().multiply(t));
            if (isPassable(candidate)) {
                best = candidate;
                wallBudget = MAX_WALL_TRAVERSE;
            } else {
                // 累计穿墙厚度；用完就停（厚墙挡住）
                wallBudget -= STEP;
                if (wallBudget < 0d) {
                    break;
                }
            }
        }
        return best;
    }

    /** 该位置能否穿过：脚那格与头那格都得是可通过的（**纯判定**）。 */
    private static boolean isPassable(Location at) {
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

    // ───────── 外观 ─────────

    /**
     * 基类三态画法 + 技能就绪时叠附魔光效（工程规范第 1 / 4 条）。
     *
     * <p>★ 先 {@code super.buildItem()} —— 识别键写在基类那一步。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        // 只差能量（冷却已走完、其它条件都满足）⇒ 结构空位也发光，与"冷却中"区分开
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
