package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * 「猎手」技能之一：**扑击**（金锭）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>向前<b>扑击 4 格</b>；</li>
 *   <li>在**结尾处**对范围 <b>3 格内</b>**最近的一名**敌人进行<b>撕咬</b>
 *       （<b>撕咬时视角朝向目标</b>）；</li>
 *   <li>造成 <b>12 点物理伤害</b>与 <b>3 秒缓慢 III</b>；</li>
 *   <li>并回复 [猎手] <b>4 点生命</b>；</li>
 *   <li>CD <b>3 秒</b>。</li>
 * </ol>
 *
 * <h2>★ 口径：撕咬只对**最近的一名**敌人生效（用户 2026-10-02 明确）</h2>
 * "范围 3 格内"只用于**挑人**，不用于"扫射"：结尾那一刻选出该范围内**最近的一名**敌对玩家，
 * 只对它结算 12 点物理 + 3 秒缓慢 III，并把视角转向它。
 * <p>因此本技能的实际收益上限恒为"一名敌人 + 自己回 4 点生命"，
 * 与"范围内全体各挨一下"是完全不同的强度口径 —— 改这条等于改技能强度，故写进类文档。
 *
 * <h2>位移口径（照工程的"位移技能"清单）</h2>
 * <ul>
 *   <li>扑击用 {@code teleport} <b>逐刻推进</b>（4 格分 {@value #POUNCE_STEP_TICKS} 刻，每刻 1 格），
 *       <b>不</b>交给原版重力或速度向量 ⇒ 落点确定、可预期；</li>
 *   <li>★ 接管了玩家位置就必须**自己吃掉摔落伤害**：每刻 {@code owner.setFallDistance(0f)}
 *       （teleport 本身也会重置，双保险）；</li>
 *   <li>水平分量驱动、**Y 保持不变** ⇒ 不会因为抬头而"飞上天"，也不会因为低头而钻进地面。</li>
 * </ul>
 *
 * <h2>★ 口径申报：这 0.2 秒的推进算"扑击"过程，不是"引导"</h2>
 * 需求只写"向前扑击 4 格，并在结尾处……"，**没有**要求定身、不可打断或前摇。
 * 因此本实现**不**限制玩家在这 4 刻内的移动输入（玩家自己走位与扑击叠加是允许的，
 * 扑击只是每刻叠加一段位移），**不**做"引导中不能施放其它技能"的封锁。
 *
 * <h2>★ 口径申报：撕咬的 4 点回复是"咬到了才有"</h2>
 * 需求把"回复 4 点生命"写在"撕咬"这句之后 ⇒ 本实现**咬中那名敌人才回复**
 * （3 格内无敌人 ⇒ 空扑不回血）。这样"扑空"与"扑中"在结果上可区分，而不是无条件收益。
 *
 * <h2>视角朝向的实现口径</h2>
 * "撕咬时视角朝向目标" = 把玩家**传送到原地但换成面向目标的 yaw / pitch**
 * （{@code Location#setDirection}）。服务端改朝向只能经 teleport 落地，
 * 因此这一步会与扑击最后一步的位移合并成**同一次 teleport**，不会产生二次位移。
 * 目标唯一（最近的那名）⇒ 朝向没有歧义。
 */
public class HunterPounceSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "hunter_skill_pounce";

    /** 冷却（刻）—— 需求原话"CD-3"（3 秒）。 */
    private static final int COOLDOWN_TICKS = 60;

    /** 耗能：需求未提 ⇒ 0（只靠冷却限制强度，与罪棘 / 特克的三个主动同口径）。 */
    private static final int ENERGY_COST = 0;

    /** 扑击距离（格）—— 需求原话"向前扑击4格"。 */
    public static final double POUNCE_DISTANCE = 4d;

    /** 扑击用几刻走完 4 格（4 刻 = 0.2 秒，每刻 1 格）。 */
    private static final int POUNCE_STEP_TICKS = 4;

    /** 撕咬范围（格）—— 需求原话"范围[3格内]"。 */
    public static final double BITE_RADIUS = 3d;

    /** 撕咬的物理伤害 —— 需求原话"12点物理伤害"。 */
    private static final double BITE_DAMAGE = 12d;

    /** 缓慢时长（刻）—— 需求原话"3秒"。 */
    private static final int SLOWNESS_TICKS = 60;

    /** 缓慢 III 的增幅值（{@code 缓慢 N ⇒ amplifier N-1}）。 */
    private static final int SLOWNESS_AMPLIFIER = 2;

    /** 撕咬命中后回复自己的生命 —— 需求原话"回复[猎手]4点生命"。 */
    private static final double SELF_HEAL = 4d;

    private VitalsComponent vitals;
    private BuffComponent buff;
    private HunterEvolutionPassive evolution;
    private HunterStealthSkill stealth;

    // ───────── 扑击状态机（只在本组件的 update() 里推进）─────────

    /** 是否正在扑击。 */
    private boolean pouncing;

    /** 还剩几刻位移。 */
    private int stepsLeft;

    /**
     * 每刻位移向量（施放那一刻按朝向定好，中途不随转头改变 ⇒ 落点确定）。
     */
    private Vector stepVector;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HunterPounceSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：金锭（需求指定）+ 五条逐字对应需求的说明。
     * <p>技能必须有栏位（{@code setSlot} 由装配点给），耗能声明为 0。
     */
    public static final class Specification extends Skill.Specification<HunterPounceSkill> {

        public Specification() {
            super(Component.text("扑击"),
                    List.of(
                            Component.text("向前扑击 4 格"),
                            Component.text("在结尾处撕咬 3 格内最近的一名敌人，视角转向它"),
                            Component.text("造成 12 点物理伤害与 3 秒缓慢 III"),
                            Component.text("咬中时回复自己 4 点生命"),
                            Component.text("冷却 3 秒")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.GOLD_INGOT);
            requires(VitalsComponent.class);
            requires(BuffComponent.class);
            requires(HunterEvolutionPassive.class);
            requires(HunterStealthSkill.class);
        }

        @Override
        public HunterPounceSkill create(String id, ComponentServicesPort services) {
            return new HunterPounceSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        evolution = svc().components().get(HunterEvolutionPassive.class);
        stealth = svc().components().get(HunterStealthSkill.class);
    }

    /**
     * 停止生效：清空扑击状态（含位移）。
     * <p>角色被清 / 玩家死亡时框架会调本方法 ⇒ 不会留下"扑到一半"的状态。
     */
    @Override
    public void stop() {
        pouncing = false;
        stepsLeft = 0;
        stepVector = null;
        vitals = null;
        buff = null;
        evolution = null;
        stealth = null;
    }

    // ───────── 施放 ─────────

    /**
     * 施放入口（左键 / 右键 / Q 三个 trigger 都汇到这里）。
     *
     * <p>闸门顺序：**正在扑击 ⇒ 忽略**（不打断、不叠位移、不吃冷却）→ 冷却 → 眩晕/沉默 →
     * 取玩家 → 中断遁形 → 定方向 → 起步。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (pouncing) {
            return;
        }
        //技能侧派发管道（SkillListener.cast）已经挡过冷却，这里再判一次是双保险（无副作用）
        if (isCoolingDown()) {
            return;
        }
        if (!canUse()) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            return;
        }

        //"使用技能" ⇒ 中断遁形（需求："若使用技能或攻击，将中断技能"）
        if (stealth != null) {
            stealth.breakStealth();
        }

        Vector direction = horizontalDirection(owner);
        if (direction == null) {
            return;
        }

        pouncing = true;
        stepsLeft = POUNCE_STEP_TICKS;
        stepVector = direction.multiply(POUNCE_DISTANCE / POUNCE_STEP_TICKS);

        //"突进时破空音效"：只在起步那一下播一次（4 刻内连播 4 次会糊成噪音）
        World world = owner.getWorld();
        if (world != null) {
            HunterSound.riptideHunterPounceDashSound(world, owner.getLocation());
        }
    }

    // ───────── 每 tick：推进扑击 ─────────

    /**
     * 推进扑击位移；走完全程后结算撕咬。
     *
     * <p>死亡 / 掉线 / 取不到玩家 ⇒ 收工（不给收益），但**照常进冷却** ——
     * 与"出手失败也起冷却"的既有口径一致，避免"死了就能白嫖一次扑击"。
     */
    @Override
    public void update() {
        if (!pouncing) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            pouncing = false;
            stepsLeft = 0;
            stepVector = null;
            startCooldown(adjustedCooldownTicks());
            return;
        }

        Location current = owner.getLocation();
        Location next = current.clone().add(stepVector);
        //Y 保持不变（扑击是水平位移）
        next.setY(current.getY());
        //★ 位移技能必须自己吃掉摔落伤害
        owner.setFallDistance(0f);
        owner.teleport(next);

        //"突进时……粒子"：每刻沿位移拉一段空气划痕（4 刻连成一条尾迹）
        World world = next.getWorld();
        if (world != null) {
            HunterVfx.dashTrailHunterPounceDash(world, next, current, next);
        }

        stepsLeft--;
        if (stepsLeft <= 0) {
            pouncing = false;
            stepVector = null;
            bite(owner);
            startCooldown(adjustedCooldownTicks());
        }
    }

    // ───────── 撕咬结算 ─────────

    /**
     * 结尾处的撕咬：**只对 {@value #BITE_RADIUS} 格内最近的一名敌人**结算一次
     * （12 点物理 + 3 秒缓慢 III），咬中即回复自己 4 点生命并把**视角转向它**。
     *
     * <p>★ 口径（2026-10-02 用户明确）："撕咬只对最近的一名敌人生效" ——
     * 不是"范围内全体各挨一下"。因此本方法**选出唯一目标后就返回**，
     * 不遍历结算（范围只用来"挑人"，不用来"扫射"）。
     */
    private void bite(Player owner) {
        Player victim = nearestHostile(owner);
        if (victim == null) {
            return;
        }
        if (vitals != null) {
            vitals.physicalDamage(victim, owner, BITE_DAMAGE);
        }
        //给【他人】上药水必须用目标自身的 addPotionEffect（buff 那个口只作用自己）
        victim.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(SLOWNESS_TICKS, SLOWNESS_AMPLIFIER));
        //"撕咬时……唤魔者尖牙咬合音效"
        World world = victim.getWorld();
        if (world != null) {
            HunterSound.evokerFangsHunterPounceBiteSound(world, victim.getLocation());
        }
        if (vitals != null) {
            vitals.heal(owner, SELF_HEAL);
        }
        //"撕咬时视角朝向目标" —— 目标唯一，直接朝向它
        faceTarget(owner, victim);
    }

    /**
     * {@value #BITE_RADIUS} 格内**最近的敌对玩家**（没有回 {@code null}）。
     *
     * <p>判敌走全角色唯一真值点 {@code svc().roleInfo().isHostileTo(uuid)}；
     * 仍然显式跳过自己（既省一次查表，也让"不会咬到自己"这件事在代码里看得见）。
     */
    private Player nearestHostile(Player owner) {
        Location center = owner.getLocation();
        Player best = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (Player candidate : center.getNearbyPlayers(BITE_RADIUS)) {
            if (candidate == null || candidate.equals(owner)) {
                continue;
            }
            if (!candidate.isOnline() || candidate.isDead() || candidate.getHealth() <= 0d) {
                continue;
            }
            Location at = candidate.getLocation();
            if (at.getWorld() == null || center.getWorld() == null || !at.getWorld().equals(center.getWorld())) {
                continue;
            }
            if (!svc().roleInfo().isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            double distanceSquared = at.distanceSquared(center);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = candidate;
            }
        }
        return best;
    }

    // ───────── 内部工具 ─────────

    /** 本次应起的冷却（3 级进化起减 1 秒；见 {@link HunterEvolutionPassive#adjustedSkillCooldownTicks(int)}）。 */
    private int adjustedCooldownTicks() {
        return evolution == null ? getCooldownTicks() : evolution.adjustedSkillCooldownTicks(getCooldownTicks());
    }

    /**
     * **水平朝向单位向量**（扑击方向）。
     *
     * <p>取视线的水平分量并归一；视线接近垂直（朝正上 / 正下）时水平分量趋于 0
     * ⇒ 退回**身体朝向** {@code player.getFacing().getDirection()}（工程既有口径：
     * 视线近垂直时不要让技能空放）。两者都退化时回 {@code null}（调用方放弃施放）。
     */
    private static Vector horizontalDirection(Player owner) {
        Vector view = owner.getLocation().getDirection();
        view.setY(0d);
        if (view.lengthSquared() < 1.0E-6d) {
            view = owner.getFacing().getDirection();
            view.setY(0d);
        }
        if (view.lengthSquared() < 1.0E-6d) {
            return null;
        }
        return view.normalize();
    }

    /**
     * 把玩家原地转向目标（"撕咬时视角朝向目标"）。
     * <p>只改 yaw / pitch、不改坐标：在玩家**当前坐标**上 {@code setDirection} 后 teleport。
     */
    private static void faceTarget(Player owner, Player target) {
        Vector toTarget = target.getLocation().toVector().subtract(owner.getLocation().toVector());
        if (toTarget.lengthSquared() < 1.0E-6d) {
            return;
        }
        Location at = owner.getLocation().clone();
        at.setDirection(toTarget);
        owner.teleport(at);
    }

    /** 闸门：被眩晕 / 沉默时技能不可用（与基类三态判定的"禁用"维一致）。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /**
     * 本技能 0 耗能 ⇒ **不参与能量维度**，直接回声明耗能（"恰好够"）。
     * <p>口径来自基类 javadoc：声明耗能 ≤ 0 的组件必须这样回答，
     * 否则会凭空多出一个"能量不足"态。
     */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
