package com.shadowHunterRolesPlugin.roleComponent.custom.raven.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.RavenVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenAirMobilityPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.raven.passive.RavenFloatingTextComponent;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 「林晨霜」技能之一：**升空突袭**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>地面</b>：向<b>斜上方</b>在 <b>0.2 秒</b>内突进 <b>7 格</b>，进入空中
 *       ——前提是<b>身下 1.5 格内有方块</b>（2026-10-11 由"正下方必须实心"放松）；</li>
 *   <li><b>2026-10-11 新增 · 浮空形态</b>：处于浮空机动时使用 ⇒ 向<b>视角斜上方</b>在 0.2 秒内
 *       单纯突进 <b>4 格</b>（<b>向前 4 格 + 向上 2 格</b>），不要求脚下有方块；</li>
 *   <li>对<b>路径上所有敌人</b>造成<b>单次 10 点物理伤害和 3~6 点特殊值伤害</b>；</li>
 *   <li>若使用后处于空中 ⇒ 触发被动<b>浮空机动状态</b>；</li>
 *   <li>CD <b>8 秒</b>，地面形态<b>落地才进行 CD 结算</b>（用完不落地就一直不进冷却）；</li>
 *   <li><b>2026-10-11</b>：进入空中、CD 未结算时，<b>图标换成结构空位</b>
 *       （见 {@link #buildItem()} —— 换材质后**必须重写识别键**，否则物品会变成"没人认领"的死物）。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：突进是 0.2 秒的逐刻位移（不是同刻 teleport）</h2>
 * 需求写的是"在 0.2 秒内" ⇒ 拆成 {@value #DASH_TICKS} 刻（= 0.2 秒）逐刻 {@code teleport}
 * （与主武器的空中前冲同一口径）。伤害在**位移走完那一刻**沿完整路径结算，
 * 而不是在按下键的瞬间 —— 这样"擦身而过"与"正面撞上"的观感一致。
 * <p>★ 位移中途玩家死亡 / 下线 ⇒ <b>收工且不出手</b>（不 teleport 尸体、不白结算）。
 *
 * <h2>★★ 口径申报二："落地才结算 CD"只对**地面形态**成立</h2>
 * 地面形态按需求把 CD 推迟到落地（{@link #awaitingLanding}）；若浮空形态也这么做，
 * 玩家就能在悬停里**无限连放**（悬停期间永远不会落地）⇒ 浮空形态改为**立即起冷却**。
 * 两者图标都落在 {@code STRUCTURE_VOID} 上（冷却态本来就是它），因此视觉上不冲突。
 *
 * <h2>★★ 口径申报三：路径伤害自己带"本次已命中名单"</h2>
 * {@code DamageUtil.dealtPhysicalDamage} 会**清零受击无敌帧**⇒ 引擎不会替你挡重复伤害。
 * 本类沿路径按 0.5 格步长采样，用 {@link HashSet} 去重，**先查重再结算**。
 */
public class RavenSkyStrikeSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "raven_sky_strike_skill";

    /** 冷却（刻）：需求 CD 8 秒。 */
    public static final int COOLDOWN_TICKS = 160;

    /** 能量消耗：需求未提 ⇒ 0（与罪棘 / 猎手 / 特克同口径）。 */
    private static final int ENERGY_COST = 0;

    /** 位移耗时（刻）：需求"在 0.2S 内" ⇒ 4 刻。**两种形态共用**。 */
    public static final int DASH_TICKS = 4;

    /** 地面形态的突进距离（格）：需求 7 格。 */
    public static final double DASH_DISTANCE = 7.0d;

    /** 地面形态突进的仰角（度）：需求"斜上方" ⇒ 取 45°。 */
    public static final double DASH_ELEVATION_DEGREES = 45.0d;

    /**
     * 浮空形态的突进分量（需求 2026-10-11）："单纯突进 4 格【向前 4 格，向上 2 格】"。
     * <p>★ 注意合计位移是 {@code √(4²+2²) ≈ 4.47} 格，不是 4 格 —— 需求括注给的是**分量**，
     * 按分量实现才是它写的那个形状。
     */
    public static final double HOVER_DASH_FORWARD = 4.0d;
    public static final double HOVER_DASH_UP = 2.0d;

    /** 路径伤害的采样步长（格）。 */
    public static final double PATH_SAMPLE_STEP = 0.5d;

    /** 路径命中半径（格）：以突进线为轴的圆柱半宽。 */
    public static final double PATH_RADIUS = 2.0d;

    /** 路径物理伤害（需求：10 点，**单次**）。 */
    public static final double PATH_PHYSICAL_DAMAGE = 10d;

    /** 路径特殊值伤害区间（需求："3~6 点"）。 */
    public static final int PATH_SANTE_MIN = 3;
    public static final int PATH_SANTE_MAX = 6;

    /**
     * **地面形态对路径敌人的击退强度**——需求 2026-10-11："地面使用 1 技能会对敌人造成击退效果"。
     * <p>口径申报：需求没给等级，取 {@value}（比主武器的 2 略轻 —— 技能是范围清场，
     * 不该把人推出太远）。**浮空形态不击退**（需求写的是"地面使用"）。
     */
    public static final double PATH_KNOCKBACK = 1.5d;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private FactionComponent faction;
    private RavenAirMobilityPassive flight;

    /** 语音组件（技能一那一栏台词）。 */
    private RavenFloatingTextComponent voice;
    private HotbarRenderComponent render;

    private final Random random = new Random();

    // ───────── 运行期状态 ─────────

    /** CD 是否在等落地（需求："落地才进行 cd 结算"）。 */
    private boolean awaitingLanding;

    /** 位移剩余刻数（{@code 0} = 没有进行中的突进）。 */
    private int dashRemaining;

    /** 位移起点。 */
    private Location dashOrigin;

    /** 位移终点（用来算路径伤害与逐刻落点）。 */
    private Location dashTarget;

    /** 每刻位移向量。 */
    private Vector dashStep = new Vector();

    /** 本次施放是否**浮空形态**（决定是否给击退）。 */
    private boolean hoverForm;

    /**
     * 本次施放是**浮空形态**吗（决定 CD 是"落地结算"还是"立即"）。
     * <p>见类注释口径申报二：浮空形态若也等落地，玩家就能在悬停里无限连放。
     */
    private boolean immediateCooldown;

    public RavenSkyStrikeSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<RavenSkyStrikeSkill> {

        public Specification() {
            super(Component.text("升空突袭"),
                    List.of(
                            Component.text("启动「绝影」义腿变形机构，向斜上方 0.2 秒内突进 7 格（身下 1.5 格内需有方块）"),
                            Component.text("浮空机动中使用：向视角斜上方 0.2 秒内突进（向前 4 格、向上 2 格）"),
                            Component.text("对路径上所有敌人造成单次 10 点物理伤害与 3~6 点特殊值伤害"),
                            Component.text("若使用后处于空中，触发并刷新被动「浮空机动」；地面形态对路径敌人附带击退"),
                            Component.text("地面形态冷却 8 秒且落地才开始结算；在空中未结算时图标变为结构空位")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.WIND_CHARGE);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(RavenAirMobilityPassive.class)
                    .requires(RavenFloatingTextComponent.class);
        }

        @Override
        public RavenSkyStrikeSkill create(String id, ComponentServicesPort services) {
            return new RavenSkyStrikeSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        flight = svc().components().get(RavenAirMobilityPassive.class);
        voice = svc().components().get(RavenFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        awaitingLanding = false;
        resetDash();
    }

    @Override
    public void stop() {
        awaitingLanding = false;
        resetDash();
        vitals = null;
        sante = null;
        buff = null;
        faction = null;
        flight = null;
        voice = null;
        render = null;
    }

    private void resetDash() {
        dashRemaining = 0;
        dashOrigin = null;
        dashTarget = null;
        dashStep = new Vector();
    }

    // ───────── 每刻：推进位移 + 落地结算 CD ─────────

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            resetDash();        //★ 位移中死亡 / 下线 ⇒ 收工且不出手
            awaitingLanding = false;
            return;
        }
        if (dashRemaining > 0) {
            advanceDash(owner);
            return;
        }
        //落地那一刻把 CD 结算了（需求："落地才进行 cd 结算"）
        if (awaitingLanding && flight != null && !flight.isHovering() && flight.canTakeOffHere()) {
            awaitingLanding = false;
            startCooldown();
            if (render != null) {
                render.requestRepaint();     //让"结构空位 + 待落地"立刻变回正常
            }
        }
    }

    /** 逐刻位移；走完那一刻结算路径伤害并（若在空中）进浮空。 */
    private void advanceDash(Player owner) {
        if (dashOrigin == null || dashOrigin.getWorld() != owner.getWorld()) {
            resetDash();
            return;
        }
        int done = DASH_TICKS - dashRemaining + 1;
        Location next = dashOrigin.clone().add(dashStep.clone().multiply(done));
        owner.teleport(next);
        owner.setFallDistance(0f);      //★ 技能接管位置 ⇒ 必须自己吃掉摔落伤害
        RavenVfx.ravenWindBombPath(owner.getWorld(),
                next.clone().subtract(dashStep.clone()),
                next.clone().add(0d, 0.2d, 0d));
        dashRemaining--;
        if (dashRemaining > 0) {
            return;
        }
        //走完：沿完整路径结算伤害
        Location target = dashTarget != null ? dashTarget : next;
        damageAlongPath(owner, dashOrigin, target);
        resetDash();
        //★ 需求 2026-10-11："一技能在空中使用也会刷新浮空时间"
        //  ⇒ 判据只看"人在不在空中"：未悬停则**进入**、已悬停则**刷新**
        //  （旧版条件里那句 !isHovering() 正是"空中使用不刷新"的原因）
        if (flight != null) {
            switch (hoverActionAfterFly(!flight.canTakeOffHere(), flight.isHovering())) {
                case ENTER -> flight.enterHover();
                case REFRESH -> flight.refreshHover();
                case NONE -> { /* 已经落地 ⇒ 什么也不做 */ }
            }
        }
        if (!immediateCooldown) {
            awaitingLanding = true;
        }
        immediateCooldown = false;
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (dashRemaining > 0) {
            return;         //位移还没走完
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        boolean hovering = flight != null && flight.isHovering();
        hoverForm = hovering;

        Location origin = owner.getLocation();
        Vector step;
        Location target;
        if (hovering) {
            //★ 浮空形态（需求 2026-10-11）：向前 4 格 + 向上 2 格，0.2 秒
            step = hoverDashStep(owner.getEyeLocation().getDirection());
            target = origin.clone().add(step.clone().multiply(DASH_TICKS));
        } else {
            //地面形态：需要"身下 1.5 格内有方块"（2026-10-11 放松）
            if (flight != null && !flight.canTakeOffHere()) {
                return;
            }
            Vector direction = dashDirection(owner.getEyeLocation().getDirection());
            target = dashDestination(origin, direction, DASH_DISTANCE);
            step = target.toVector().subtract(origin.toVector()).multiply(1d / DASH_TICKS);
        }

        dashOrigin = origin.clone();
        dashTarget = target;
        dashStep = step;
        dashRemaining = DASH_TICKS;
        immediateCooldown = hovering;      //浮空形态立即起冷却（见口径申报二）

        RavenSound.ravenSkyStrikeLaunchSound(world, origin);
        //需求"一技能：升空突袭"那一栏语音
        if (voice != null) {
            voice.saySkill(owner, RavenFloatingTextComponent.MOMENT_SKILL_DASH);
        }
        if (hovering) {
            startCooldown();
        }
        advanceDash(owner);     //本刻立刻走第一步（响应更跟手）
    }

    /** 沿"起点 → 终点"的线段采样，对每个敌人**只结算一次** 10 物理 + 3~6 特殊值。 */
    private void damageAlongPath(Player owner, Location from, Location to) {
        if (from == null || to == null) {
            return;
        }
        World world = from.getWorld();
        if (world == null || vitals == null) {
            return;
        }
        Vector delta = to.toVector().subtract(from.toVector());
        double length = delta.length();
        if (length < 1.0E-4d) {
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(length / PATH_SAMPLE_STEP));
        Set<UUID> alreadyHit = new HashSet<>();
        for (int i = 0; i <= steps; i++) {
            Location point = from.clone().add(delta.clone().multiply(i / (double) steps));
            for (Player victim : world.getNearbyPlayers(point, PATH_RADIUS)) {
                if (victim == null || victim.equals(owner) || !isAlive(victim) || !isHostile(victim)) {
                    continue;
                }
                //★ 先查重、再结算（顺序反了照样打两次）
                if (!alreadyHit.add(victim.getUniqueId())) {
                    continue;
                }
                //★ 需求 2026-10-11："地面使用 1 技能会对敌人造成击退效果"（浮空形态不击退）
                double knockback = pathKnockback(hoverForm);
                if (knockback > 0d) {
                    vitals.physicalDamage(victim, owner, PATH_PHYSICAL_DAMAGE, knockback);
                } else {
                    vitals.physicalDamage(victim, owner, PATH_PHYSICAL_DAMAGE);
                }
                if (sante != null) {
                    sante.decreaseSanTE(victim.getUniqueId(),
                            rollInclusive(random, PATH_SANTE_MIN, PATH_SANTE_MAX));
                }
                victim.setNoDamageTicks(0);
                Location chest = victim.getLocation().clone().add(0d, 1d, 0d);
                RavenVfx.ravenWindBombHit(world, chest);
                RavenSound.ravenSkyStrikeHitSound(world, chest);
            }
        }
    }

    // ───────── 外观 ─────────

    /**
     * 技能物品：
     * <ol>
     *   <li>基类三态（就绪 / 冷却 / 禁用）；</li>
     *   <li>★ <b>"待落地"⇒ 材质换成结构空位</b>（需求 2026-10-11："当进入空中而不能进行冷却时，
     *       模型换成结构空位"）+ 附魔光效（与"冷却中"共用同一材质 ⇒ 必须发光才分得开，见 §3 规范 4）；</li>
     *   <li>★ <b>换材质后必须重写识别键</b>：{@code setType} 会把旧 meta 丢掉 ⇒ 不补键的话
     *       物品既不被输入管道认领（点击无反应），也不会被角色清除时的扫描清掉（残留背包）。</li>
     * </ol>
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!awaitingLanding || isCoolingDown()) {
            return stack;
        }
        stack.setType(Material.STRUCTURE_VOID);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)
                .append(Component.text(" 待落地").color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)));
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        //★ 重写识别键（换材质后 meta 是全新的，键丢了物品就成了"没人认领"的死物）
        meta.getPersistentDataContainer().set(identifyKey(), PersistentDataType.STRING, getId());
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    // ───────── 读口（供技能二"刷新一技能 cd"）─────────

    /** 是否还在等落地结算 CD。 */
    public boolean awaitingLanding() {
        return awaitingLanding;
    }

    /** **立刻了结这次"待落地"**（技能二刷新一技能时调用，幂等）。 */
    public void settleImmediately() {
        awaitingLanding = false;
        stopCooldown();
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **地面形态的突进方向**（纯函数）＝ 视线的**水平分量** + 固定
     * {@value #DASH_ELEVATION_DEGREES}° 仰角。
     *
     * <p>为什么不用视线原方向：视线带俯仰 ⇒ 玩家一低头就"往地里扎"，
     * 沿线的落点扫描会全部不合格、终点退化成原地（"平地也突不动"）。
     */
    public static Vector dashDirection(Vector look) {
        Vector flat = horizontalOf(look);
        double radians = Math.toRadians(DASH_ELEVATION_DEGREES);
        double horizontal = Math.cos(radians);
        double vertical = Math.sin(radians);
        return new Vector(flat.getX() * horizontal, vertical, flat.getZ() * horizontal).normalize();
    }

    /**
     * **浮空形态的每刻位移**（纯函数）——需求 2026-10-11："向前 4 格、向上 2 格"，
     * 分 {@value #DASH_TICKS} 刻走完。
     */
    public static Vector hoverDashStep(Vector look) {
        Vector flat = horizontalOf(look);
        return new Vector(flat.getX() * HOVER_DASH_FORWARD / DASH_TICKS,
                HOVER_DASH_UP / DASH_TICKS,
                flat.getZ() * HOVER_DASH_FORWARD / DASH_TICKS);
    }

    /** 视线的水平单位分量（退化成竖直视线 ⇒ 退回 {@code +Z}）。 */
    private static Vector horizontalOf(Vector look) {
        Vector flat = look == null ? new Vector(0d, 0d, 1d) : new Vector(look.getX(), 0d, look.getZ());
        if (flat.lengthSquared() < 1.0E-9d) {
            flat = new Vector(0d, 0d, 1d);
        }
        return flat.normalize();
    }

    /**
     * **本次路径伤害该带多大击退**（纯函数）——需求 2026-10-11："**地面**使用 1 技能会对敌人造成击退"。
     * <p>浮空形态返回 {@code 0}（不击退）。
     */
    public static double pathKnockback(boolean hovering) {
        return hovering ? 0d : PATH_KNOCKBACK;
    }

    /** 施放收尾时对浮空要做的动作。 */
    public enum HoverAction {
        /** 不需要处理（已经落地）。 */
        NONE,
        /** 进入浮空。 */
        ENTER,
        /** 只刷新剩余时间（已在浮空且仍在空中）。 */
        REFRESH
    }

    /**
     * **施放收尾对浮空做什么**（纯函数）——需求 2026-10-11："一技能在空中使用也会刷新浮空时间"。
     *
     * <p>★ 旧版条件是 {@code !isHovering() && 空中} ⇒ **已经在悬停时什么都不做**（不刷新），
     * 那正是"空中使用不刷新浮空时间"的原因。现在只看"人在不在空中"。
     *
     * @param airborne          是否在空中（离地）
     * @param currentlyHovering 此刻是否已经处于悬停
     */
    public static HoverAction hoverActionAfterFly(boolean airborne, boolean currentlyHovering) {
        if (!airborne) {
            return HoverAction.NONE;
        }
        return currentlyHovering ? HoverAction.REFRESH : HoverAction.ENTER;
    }

    /** 闭区间随机整数 {@code [min, max]}（需求写"3~6 点"，两端都含）。 */
    public static int rollInclusive(Random random, int min, int max) {
        if (random == null || max <= min) {
            return min;
        }
        return min + random.nextInt(max - min + 1);
    }

    /** 是否活着且在场。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** 是否敌对（判敌口径归阵营组件）。 */
    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }

    /** 沿方向采样，取最远的"人能站住"的点（一个合格点都没有 ⇒ 原地，不会把人塞进方块）。 */
    private static Location dashDestination(Location origin, Vector direction, double distance) {
        if (origin == null || origin.getWorld() == null || direction == null) {
            return origin;
        }
        Vector unit = direction.clone();
        if (unit.lengthSquared() < 1.0E-9d) {
            return origin;
        }
        unit.normalize();
        Location best = origin.clone();
        for (double t = 0.5d; t <= distance + 1.0E-6d; t += 0.5d) {
            Location candidate = origin.clone().add(unit.clone().multiply(t));
            if (isPassable(candidate)) {
                best = candidate;
            }
        }
        return best;
    }

    /** 该位置能否站人：脚那格与头那格都得是可通过的。 */
    private static boolean isPassable(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block head = at.clone().add(0d, 1d, 0d).getBlock();
        return feet.isPassable() && head.isPassable();
    }
}
