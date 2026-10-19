package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
 * 「芹泽沙耶香 · 瓦尔基里」技能二：**瞬影连斩**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>向指定方向突进并斩击：路径敌人 <b>12 点物理 + 4 点失衡</b>；</li>
 *   <li>命中 ⇒ 回复自身 <b>6 生命</b>、获得 <b>1 点笼手充能</b>（不超上限）、
 *       并在 <b>3 秒内可再次使用</b>（每次递增 <b>+1 真伤 / +2 失衡</b>）；</li>
 *   <li><b>最多连续刷新 4 次</b>，第 4 次（刷新）后技能<b>立即进入冷却</b>；每次使用需命中才能刷新，否则进入冷却；</li>
 *   <li>每次使用后，<b>5 秒内持续防御判定</b>：被敌人攻击触发一次居合（每次刷新后<b>仅一次</b>），
 *       居合完成后可<b>直接锁定敌人方向</b>（下一次突进朝它）；</li>
 *   <li>冷却 <b>20 秒</b>；每次斩击消耗 <b>5 点能量</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：连斩次数 = 最多 5 段（初始 1 + 刷新 4）</h2>
 * 「最多连续刷新 4 次」= 刷新事件至多 4 次 ⇒ 连段共 5 次（第 5 次打完后立即进冷却）。
 * 段号 {@code k}（0 起）决定加值：失衡 {@code 4 + 2k}、真伤 {@code k}（物理恒 12、回血恒 6）。
 *
 * <h2>★★ 口径申报二：3 秒 / 5 秒两个窗口的取舍</h2>
 * 需求同时给了「3 秒内可再次使用」与「5 秒时限内命中才能刷新」。本实现：
 * <b>命中即时结算</b>（突进与命中在同刻完成）⇒ 5 秒"命中时限"在结构上不可能先于 3 秒窗口触发；
 * 3 秒窗口是本技能唯一的"可否再施放"闸门（拿 3 秒作数，如实申报）。
 *
 * <h2>★★ 口径申报四：突进 = 3 刻插值位移（2026-10-17 需求 7）</h2>
 * 原先是一次性 {@code teleport}（瞬移观感）；现在按需求改成 <b>0.15 秒（3 刻）</b>内逐刻插值到落点，
 * <b>到位那刻才结算伤害</b>。位移期间 {@code dashRemaining > 0} ⇒ 施放入口早退（不会叠加两次突进）；
 * 死亡 / 离开 / 结算完三路都清空状态（不会留下"半路悬空"的玩家）。
 *
 * <h2>★★ 口径申报三：防御判定落在主武器上</h2>
 * 「固定姿态防御判定 + 居合」是纳刀体系的既有能力（{@code SayakaBladeMainWeapon}）——
 * 本技能只<b>武装窗口</b>（{@link SayakaBladeMainWeapon#armDefenseWindow(int)}），
 * 由武器在受击回调里执行居合（一次窗口一次）并记录"锁定的敌人"；
 * 本技能下一次施放时经 {@link SayakaBladeMainWeapon#lockedEnemy(int)} 取方向。
 */
public class SayakaFlashSlashSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "sayaka_flash_slash_skill";

    // ───────── 数值口径 ─────────

    /** 突进距离（格）—— 需求未给，取 6 格（口径申报）。 */
    public static final double DASH_DISTANCE = 6.0d;

    /**
     * 突进耗时（刻）—— 2026-10-17 需求 7：<b>0.15 秒内完成突进动作</b>（3 刻）。
     *
     * <p>实现 = 逐刻把玩家传送到插值点（而不是一次性瞬移），第 {@value #DASH_TICKS} 刻到位后
     * 才结算伤害与命中收益。位移期间不吞输入：{@code dashRemaining > 0} 时 {@code onCast} 直接早退。
     */
    public static final int DASH_TICKS = 3;

    /** 斩击走廊长度（格）= 突进距离 + 1（覆盖落点前后）。 */
    public static final double SLASH_RANGE = DASH_DISTANCE + 1.0d;

    /** 侧向容差（格，水平叉积）。 */
    public static final double SLASH_LATERAL = 1.5d;

    /** 高差容差（格，脚底对脚底）。 */
    public static final double SLASH_VERTICAL = 2.5d;

    /** 基础物理伤害（需求：12 点）。 */
    public static final double BASE_PHYSICAL = 12d;

    /** 基础失衡积累（需求：4 点）。 */
    public static final int BASE_IMBALANCE = 4;

    /** 每段递増：失衡 +2（需求）。 */
    public static final int IMBALANCE_STEP = 2;

    /** 每段递増：真实伤害 +1（2026-10-14 需求更新：原「法术 +1」改为真伤）。 */
    public static final int TRUE_STEP = 1;

    /** 命中回复生命（需求：6 点）。 */
    public static final double HIT_HEAL = 6d;

    /** 连段上限（初始 1 + 刷新 4 = 5；口径申报一）。 */
    public static final int CHAIN_MAX_CASTS = 5;

    /** 重新施放窗口（刻）—— 需求：3 秒。 */
    public static final int REFRESH_WINDOW_TICKS = 60;

    /** 冷却（刻）—— 需求：20 秒。 */
    public static final int COOLDOWN_TICKS = 400;

    /** 每次斩击的能量消耗（需求：5 点）。 */
    public static final int ENERGY_COST = 5;

    /** 采样步长（格）。 */
    private static final double SAMPLE_STEP = 0.4d;

    // ───────── 协作组件 ─────────

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private FactionComponent faction;
    private SayakaGauntletPassive passive;
    private SayakaBladeMainWeapon weapon;
    private SayakaFloatingTextComponent voice;
    private HotbarRenderComponent render;

    // ───────── 运行期状态 ─────────

    /** 当前连段已完成的段数（0 = 不在连段中）。 */
    private int chainCasts;

    /** 重新施放窗口到期刻。 */
    private int refreshWindowUntil;

    /** 突进剩余刻（> 0 = 正在位移，期间拒绝新的施放）。 */
    private int dashRemaining;

    /** 突进起点 / 终点 / 上一刻位置（拖尾用）。 */
    private Location dashFrom;
    private Location dashTo;
    private Location dashLast;

    /** 起手时收集好的命中名单与段号（到位后才结算）。 */
    private List<Player> pendingHits = new ArrayList<>();
    private int pendingCastIndex;

    /** 上一帧外观签名。 */
    private String lastRenderSignature = "";

    public SayakaFlashSlashSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<SayakaFlashSlashSkill> {

        public Specification() {
            super(Component.text("瞬影连斩"),
                    List.of(Component.text("向指定方向 0.15 秒突进并斩击：路径敌人 12 点物理伤害 + 4 点失衡积累"),
                            Component.text("命中：回复 6 生命、获得 1 点笼手充能；3 秒内可再次使用（每段 +1 真伤 / +2 失衡）"),
                            Component.text("最多连续刷新 4 次（共 5 段），第 4 次刷新后立即进入冷却；未命中即进入冷却"),
                            Component.text("每次使用后 5 秒内持续防御判定：被敌人攻击触发一次居合（每次刷新后仅一次）"),
                            Component.text("居合完成后可直接锁定敌人方向（下一次突进朝它）；冷却 20 秒，每次消耗 5 能量")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.IRON_SWORD);
            requires(VitalsComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class)
                    .requires(FactionComponent.class).requires(SayakaGauntletPassive.class)
                    .requires(SayakaBladeMainWeapon.class).requires(SayakaFloatingTextComponent.class);
        }

        @Override
        public SayakaFlashSlashSkill create(String id, ComponentServicesPort services) {
            return new SayakaFlashSlashSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        passive = svc().components().get(SayakaGauntletPassive.class);
        weapon = svc().components().get(SayakaBladeMainWeapon.class);
        voice = svc().components().get(SayakaFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        chainCasts = 0;
        refreshWindowUntil = 0;
        clearDash();
        lastRenderSignature = "";
    }

    @Override
    public void stop() {
        chainCasts = 0;
        refreshWindowUntil = 0;
        clearDash();
        vitals = null;
        energy = null;
        buff = null;
        faction = null;
        passive = null;
        weapon = null;
        voice = null;
        render = null;
    }

    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            chainCasts = 0;
            refreshWindowUntil = 0;
            clearDash();
            return;
        }
        int now = Bukkit.getCurrentTick();
        //① 突进推进（0.15 秒内完成；到位才结算）
        advanceDash(owner, now);
        //② 窗口到期未续 ⇒ 收链并进入冷却（"否则进入冷却"）
        if (dashRemaining == 0 && chainCasts > 0 && now > refreshWindowUntil && !isCoolingDown()) {
            endChain(true);
        }
        requestRenderIfChanged();
    }

    /**
     * 推进 0.15 秒突进：第 {@code k} 刻把玩家放到 {@code k/3} 的插值点，到位那一刻才结算。
     *
     * <p>为什么不在施放当刻结算：需求 7 明确"在 0.15 秒内完成突进动作"——
     * 位移与伤害若同刻完成，玩家看到的仍是瞬移；分段位移才有"冲过去"的观感。
     * 位移期间 {@code dashRemaining > 0} ⇒ {@code onCast} 早退（不会叠加两次突进）。
     */
    private void advanceDash(Player owner, int now) {
        if (dashRemaining <= 0 || dashFrom == null || dashTo == null) {
            return;
        }
        dashRemaining--;
        double progress = 1d - dashRemaining / (double) DASH_TICKS;
        Location at = dashFrom.clone().add(dashTo.toVector().subtract(dashFrom.toVector()).multiply(progress));
        Location previous = dashLast == null ? dashFrom.clone() : dashLast.clone();
        owner.teleport(at);
        owner.setFallDistance(0f);
        if (owner.getWorld() != null) {
            SayakaVfx.dashTrail(owner.getWorld(), previous, at, passive == null ? 0 : passive.charges());
        }
        dashLast = at.clone();
        if (dashRemaining == 0) {
            List<Player> hits = pendingHits;
            int castIndex = pendingCastIndex;
            clearDash();
            resolveChain(owner, hits, castIndex, now);
        }
    }

    /** 清空突进状态（死亡 / 离开 / 结算完）。 */
    private void clearDash() {
        dashRemaining = 0;
        dashFrom = null;
        dashTo = null;
        dashLast = null;
        pendingHits = new ArrayList<>();
        pendingCastIndex = 0;
    }

    // ───────── 施放 ─────────

    /**
     * 施放：突进 + 路径斩击 + 命中续段。
     *
     * <p>顺序（不可交换）：闩闸门 → 能量 → 方向（锁定敌人优先）→ 命中收集 → 位移 →
     * 结算（物理 / 真伤 / 失衡）→ 命中收益（回血 / 充能 / 续段 / 防御窗口）或断链冷却。
     */
    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || !canUse() || isCoolingDown() || dashRemaining > 0) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (chainCasts > 0 && now > refreshWindowUntil) {
            endChain(true);     //防竞态：窗口过期而 update 还没补冷却
            return;
        }
        if (energy == null || energy.current() < ENERGY_COST || !energy.tryConsume(ENERGY_COST)) {
            SayakaSound.grenadeDenied(owner.getWorld(), owner.getLocation());
            if (voice != null) {
                voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_ENERGY_OUT);
            }
            return;
        }
        //① 方向：防御居合锁定的敌人优先（"可以直接锁定敌人方向"）
        Vector axis = null;
        if (weapon != null) {
            Player locked = weapon.lockedEnemy(now);
            if (locked != null) {
                axis = horizontal(locked.getLocation().toVector()
                        .subtract(owner.getLocation().toVector()));
            }
        }
        if (axis == null) {
            axis = horizontal(owner.getEyeLocation().getDirection());
        }
        if (axis == null) {
            axis = horizontal(owner.getFacing().getDirection());
        }
        if (axis == null) {
            axis = new Vector(1d, 0d, 0d);
        }
        //② 命中收集（从原点出发的走廊）
        List<Player> hits = collectHits(owner, axis);
        int castIndex = chainCasts;
        //③ 首斩语音在**起手时**喊（别等 0.15 秒到位）
        if (voice != null && castIndex == 0) {
            voice.saySkill(owner, SayakaFloatingTextComponent.MOMENT_CHAIN_FIRST);
        }
        //④ 位移：0.15 秒内分 3 刻完成；没有任何可站落点 ⇒ 原地斩（同刻结算）
        Location from = owner.getLocation().clone();
        Location destination = dashDestination(from, axis, DASH_DISTANCE);
        SayakaSound.dash(owner.getWorld(), from);
        if (destination == null) {
            resolveChain(owner, hits, castIndex, now);
            return;
        }
        dashFrom = from;
        dashTo = destination;
        dashLast = from.clone();
        dashRemaining = DASH_TICKS;
        pendingHits = hits;
        pendingCastIndex = castIndex;
        SayakaVfx.dashTrail(owner.getWorld(), from, destination, passive == null ? 0 : passive.charges());
    }

    /**
     * **到位结算**：伤害 → 命中收益（回血 / 充能 / 续段 / 防御窗口）或断链冷却。
     *
     * <p>由 {@link #advanceDash} 在突进最后一刻调用，或被"原地斩"路径直接调用。
     */
    private void resolveChain(Player owner, List<Player> hits, int castIndex, int now) {
        //① 结算
        for (Player victim : hits) {
            if (vitals != null) {
                vitals.physicalDamage(victim, owner, BASE_PHYSICAL);
                double extraTrue = chainTrue(castIndex);
                if (extraTrue > 0d) {
                    vitals.trueDamage(victim, owner, extraTrue);
                }
            }
            SayakaImbalance.add(victim.getUniqueId(), chainImbalance(castIndex), now, owner.getUniqueId());
            victim.setNoDamageTicks(0);
        }
        //② 命中收益 / 断链
        if (!hits.isEmpty()) {
            if (vitals != null) {
                vitals.heal(owner, HIT_HEAL);
            }
            if (passive != null && passive.tryAddCharge()) {
                SayakaVfx.chargeStored(owner.getWorld(), owner.getLocation(), false);
                SayakaSound.chargeStored(owner.getWorld(), owner.getLocation(), false);
            }
            SayakaSound.chainHit(owner.getWorld(), owner.getLocation(), castIndex);
            //③ 语音：刷新 / 收尾（首斩已在起手时喊过，不重复）
            if (voice != null && castIndex > 0) {
                if (castIndex >= CHAIN_MAX_CASTS - 1) {
                    voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_CHAIN_FINAL);
                } else {
                    voice.saySpecial(owner, SayakaFloatingTextComponent.MOMENT_CHAIN_REFRESH);
                }
            }
            //④ 每次使用后 5 秒防御判定（"每次刷新后仅一次居合动作"由武器侧保证；末段同样武装）
            if (weapon != null) {
                weapon.armDefenseWindow(now);
            }
            chainCasts++;
            if (chainContinues(chainCasts)) {
                refreshWindowUntil = now + REFRESH_WINDOW_TICKS;
            } else {
                endChain(true);     //第 4 次刷新后（第 5 段打完）立即进入冷却
            }
        } else {
            SayakaSound.chainMissed(owner.getWorld(), owner.getLocation());
            endChain(true);
        }
    }

    /** 收链：进（或不进）冷却并清零连段。 */
    private void endChain(boolean cooldown) {
        chainCasts = 0;
        refreshWindowUntil = 0;
        if (cooldown) {
            startCooldown(COOLDOWN_TICKS);
        }
        requestRenderIfChanged();
    }

    /** 收集走廊内敌人（水平叉积侧向 + 脚底高差，工程长廊口径）。 */
    private List<Player> collectHits(Player owner, Vector axis) {
        List<Player> hits = new ArrayList<>();
        Location origin = owner.getLocation();
        if (origin.getWorld() == null) {
            return hits;
        }
        for (Player candidate : origin.getNearbyPlayers(SLASH_RANGE + 2.0d)) {
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate) || !isHostile(candidate)) {
                continue;
            }
            Location t = candidate.getLocation();
            if (insideSlashCorridor(t.getX() - origin.getX(), t.getZ() - origin.getZ(), t.getY() - origin.getY(),
                    axis.getX(), axis.getZ())) {
                hits.add(candidate);
            }
        }
        return hits;
    }

    /** 沿轴采样出最远的"能站人"落点（一个都没有 ⇒ {@code null} = 原地斩）。 */
    private static Location dashDestination(Location origin, Vector axis, double distance) {
        if (origin == null || origin.getWorld() == null || axis == null) {
            return null;
        }
        Location best = null;
        for (double t = SAMPLE_STEP; t <= distance + 1.0E-6d; t += SAMPLE_STEP) {
            Location candidate = origin.clone().add(axis.clone().multiply(t));
            if (isStandable(candidate)) {
                best = candidate;
            }
        }
        return best;
    }

    /** 该位置能否站人：脚格与头格都得可通过。 */
    private static boolean isStandable(Location at) {
        if (at == null || at.getWorld() == null) {
            return false;
        }
        Block feet = at.getBlock();
        Block head = at.clone().add(0d, 1d, 0d).getBlock();
        return feet.isPassable() && head.isPassable();
    }

    // ───────── 纯函数（离线可测）─────────

    /** 第 {@code castIndex} 段（0 起）的失衡积累 = {@value #BASE_IMBALANCE} + 2k（封顶在最后一档）。 */
    public static int chainImbalance(int castIndex) {
        int k = Math.max(0, Math.min(castIndex, CHAIN_MAX_CASTS - 1));
        return BASE_IMBALANCE + IMBALANCE_STEP * k;
    }

    /** 第 {@code castIndex} 段（0 起）的真实伤害 = k（2026-10-14 需求更新：原「法术」改真伤）。 */
    public static int chainTrue(int castIndex) {
        int k = Math.max(0, Math.min(castIndex, CHAIN_MAX_CASTS - 1));
        return TRUE_STEP * k;
    }

    /** 已打 {@code castsResolved} 段后是否还能续（{@code false} ⇒ 该进冷却）。 */
    public static boolean chainContinues(int castsResolved) {
        return castsResolved < CHAIN_MAX_CASTS;
    }

    /** 突进进度（纯函数，0..1）：{@code elapsed / DASH_TICKS}，越界夹紧。 */
    public static double dashProgress(int elapsedTicks) {
        if (elapsedTicks <= 0) {
            return 0d;
        }
        return Math.min(1d, elapsedTicks / (double) DASH_TICKS);
    }

    /** 走廊命中判据（纯函数）：前向 ∈ [0, {@value #SLASH_RANGE}]、侧向 ≤ {@value #SLASH_LATERAL}、高差 ≤ {@value #SLASH_VERTICAL}。 */
    public static boolean insideSlashCorridor(double dx, double dz, double dy, double axisX, double axisZ) {
        double along = dx * axisX + dz * axisZ;
        if (along < 0d || along > SLASH_RANGE) {
            return false;
        }
        double lateral = Math.abs(dx * axisZ - dz * axisX);
        if (lateral > SLASH_LATERAL) {
            return false;
        }
        return Math.abs(dy) <= SLASH_VERTICAL;
    }

    // ───────── 闸门 / 外观 ─────────

    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /** 图标：连段中显示段数后缀；能量不足按规范补光效。 */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean energyLack = !isCoolingDown() && canUse() && currentEnergy() < getEnergyCost();
        if (energyLack) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
        } else if (chainCasts > 0) {
            meta.displayName(getDisplayName().color(NamedTextColor.RED).decorate(TextDecoration.BOLD)
                    .append(Component.text(" 连斩x" + chainCasts, NamedTextColor.RED, TextDecoration.BOLD)));
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** 段数变化才请求重绘（非冷却期没人替我们刷）。 */
    private void requestRenderIfChanged() {
        if (render == null) {
            return;
        }
        String signature = chainCasts + "/" + isCoolingDown();
        if (!signature.equals(lastRenderSignature)) {
            lastRenderSignature = signature;
            render.requestRepaint();
        }
    }

    // ───────── 内部 ─────────

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

    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    private boolean isHostile(Player player) {
        return faction == null || faction.isHostileTo(player.getUniqueId());
    }
}
