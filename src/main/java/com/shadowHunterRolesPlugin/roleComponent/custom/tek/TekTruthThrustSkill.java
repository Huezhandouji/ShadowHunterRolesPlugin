package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * 「特克」技能三：**真理之刺 · 下界之心**（终结技）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>解锁条件</b>：当场上有「真理」≥ {@link TekTruth#UNLOCK_THRESHOLD}（= 10）的角色时才可解锁使用；</li>
 *   <li>使用后立刻对<b>最近的一名真理 ≥ 10 的角色</b>进行一次
 *       <b>{@value #TRUE_DAMAGE} 点真实伤害的瞬移刺击</b>；</li>
 *   <li>并<b>去除场上所有「真理」层数</b>；</li>
 *   <li><b>回复特克 {@value #HEAL} 生命值</b>；</li>
 *   <li>冷却 {@value #COOLDOWN_SECONDS} 秒（= {@value #COOLDOWN_TICKS} 刻）。</li>
 * </ol>
 *
 * <h2>「最近的一名真理 ≥ 10 的角色」怎么选</h2>
 * 候选 = {@link TekTruth#snapshot()}（场上所有真理 ≥ {@value #UNLOCK_THRESHOLD} 的玩家 UUID）
 * ∩ **在线 + 存活 + 敌对**，在其中取离自己最近的一个。找不到（都死了 / 都下线了）⇒ 不施放、不进冷却。
 *
 * <h2>「瞬移刺击」怎么落地</h2>
 * 瞬移到目标<b>正前方 {@value #APPROACH_DISTANCE} 格</b>（自己与目标之间、靠近目标那一侧），
 * 保持朝向目标，然后结算真实伤害；观感 = 一条红金穿刺轨线 + 裁决光环。
 *
 * <h2>解锁闸门在物品上的表达</h2>
 * 未解锁 ⇒ {@link #buildItem()} 不叠光效（灰色结构空位/红屏障由基类画），并在 lore 里点明条件；
 * 解锁 ⇒ 常亮附魔光效。施放前的二次校验仍在 {@link #onCast} 里做（物品外观只是提示，不是闸门）。
 */
public class TekTruthThrustSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "tekTruthThrustSkill";

    // ───────── 数值口径（唯一修改点）─────────

    /** 真实伤害。 */
    private static final double TRUE_DAMAGE = 15d;

    /** 回复自身生命值（需求：回复特克 20 生命值）。 */
    private static final double HEAL = 20d;

    /** 瞬移落点距目标的距离（格）。 */
    private static final double APPROACH_DISTANCE = 1.6d;

    /** 冷却（刻）：20 秒 = 400 刻。 */
    private static final int COOLDOWN_TICKS = 400;

    /** 冷却（秒；文案用）。 */
    private static final String COOLDOWN_SECONDS = "20";

    /** 能量消耗：0。 */
    private static final int ENERGY_COST = 0;

    private VitalsComponent vitals;
    private BuffComponent buff;
    private EnergyComponent energy;
    private TekDestinyPassive destiny;
    private HotbarRenderComponent render;

    /**
     * 彩虹跳字的**相位**（每刻推进 ⇒ 颜色在字间流动）。
     * <p>只在"已解锁"期间推进；未解锁时归零，下次解锁从干净的相位重新开始。
     */
    private int rainbowPhase;

    public TekTruthThrustSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<TekTruthThrustSkill> {

        public Specification() {
            super(Component.text("真理之刺"),
                    List.of(Component.text("当场上有「真理」≥ " + TekTruth.UNLOCK_THRESHOLD
                                    + " 的角色时解锁"),
                            Component.text("使用后立刻对最近的一名真理 ≥ " + TekTruth.UNLOCK_THRESHOLD
                                    + " 的角色进行 " + (int) TRUE_DAMAGE + " 点真实伤害的瞬移刺击"),
                            Component.text("去除场上所有「真理」层数，并回复特克 " + (int) HEAL + " 生命值"),
                            Component.text("冷却 " + COOLDOWN_SECONDS + " 秒")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.NETHER_STAR);
            requires(VitalsComponent.class).requires(BuffComponent.class)
                    .requires(EnergyComponent.class).requires(TekDestinyPassive.class)
                    .requires(HotbarRenderComponent.class).requires(FactionComponent.class);
        }

        @Override
        public TekTruthThrustSkill create(String id, ComponentServicesPort services) {
            return new TekTruthThrustSkill(id, services, this);
        }
    }

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        destiny = svc().components().get(TekDestinyPassive.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /**
     * **每刻**：已解锁时推进彩虹相位并请求重绘（需求：技能名变彩虹跳字）。
     *
     * <p>★ 为什么必须自己请求重绘：基类的"冷却中每刻刷新"只覆盖**冷却**这一种活状态；
     * 本技能"已解锁但不在冷却"是**另一个**会变的外观（颜色在动），
     * 而框架的空闲 tick 是零 {@code setItem} 的 ⇒ 不主动置脏，玩家看到的会是**静止**的彩虹。
     * 用渲染组件的 {@code markDirty()} 请求（组件只请求、不写，写入仍归框架帧末 flush）。
     */
    @Override
    public void update() {
        boolean unlocked = canUse() && isUnlocked();
        if (!unlocked) {
            //未解锁 ⇒ 相位归零（下次解锁从干净的相位开始；也让外观停在"非彩虹"）
            if (rainbowPhase != 0) {
                rainbowPhase = 0;
                repaint();
            }
            return;
        }
        rainbowPhase++;
        repaint();
    }

    /** 请求热键栏重绘（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.markDirty();
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || vitals == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (isCoolingDown()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        //① 解锁闸门（二次校验）：场上必须有真理 ≥ 10 的**可打击**角色
        Player target = nearestUnlockedTarget(owner);
        if (target == null) {
            TekSound.verdictLockedSound(world, owner.getLocation());
            return;
        }

        //② 施放音 + 裁决光环
        TekSound.verdictCastSound(world, owner.getLocation());
        TekVfx.verdictAura(world, owner.getLocation().clone().add(0d, 0.05d, 0d), 1.8d, 0d, 20);

        //③ 瞬移到目标前方，保持朝向目标
        Location origin = owner.getLocation().clone();
        Location spot = approachSpot(owner, target);
        if (spot != null) {
            owner.teleport(spot);
            faceTowards(owner, target);
            TekVfx.blinkEcho(world, origin);
        }

        //④ 穿刺轨线 + 15 点真实伤害
        Location from = owner.getEyeLocation().clone();
        Location to = target.getLocation().clone().add(0d, 1d, 0d);
        TekVfx.thrustLine(world, from, to, 0.3d);
        vitals.trueDamage(target, owner, TRUE_DAMAGE);
        TekSound.verdictStrikeSound(world, to);
        TekVfx.truthStripped(world, to, TekTruth.layersOf(target.getUniqueId()));

        //⑤ 去除场上所有「真理」层数
        int removed = destiny != null ? destiny.clearAllTruth() : TekTruth.clearAll();

        //⑥ 回复自身生命
        vitals.heal(owner, HEAL);
        TekSound.verdictConsumeSound(world, owner.getLocation());

        startCooldown();
    }

    /**
     * **最近的一名"真理 ≥ 10 且可打击"的角色**。
     *
     * <p>候选来自 {@link TekTruth#snapshot()}（场上账本），逐个过滤：必须在线、存活、与自身敌对；
     * 在其中取离自己最近的一个。
     *
     * @return 目标；无合格者 ⇒ {@code null}
     */
    private Player nearestUnlockedTarget(Player owner) {
        Player best = null;
        double bestDist = Double.MAX_VALUE;
        Location here = owner.getLocation();
        for (TekTruth.Target entry : TekTruth.snapshot()) {
            if (entry.layers() < TekTruth.UNLOCK_THRESHOLD) {
                continue;
            }
            UUID id = entry.playerId();
            Player candidate = Bukkit.getPlayer(id);
            if (candidate == null || candidate.equals(owner) || !isAlive(candidate)) {
                continue;
            }
            if (!candidate.getWorld().equals(owner.getWorld())) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(id)) {
                continue;
            }
            double dist = candidate.getLocation().distanceSquared(here);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        return best;
    }

    /** 瞬移落点：目标朝向自身那一侧、距目标 {@link #APPROACH_DISTANCE} 格（不安全 ⇒ {@code null}，就地施放）。 */
    private static Location approachSpot(Player owner, Player target) {
        Vector dir = target.getLocation().toVector().subtract(owner.getLocation().toVector());
        Vector flat = new Vector(dir.getX(), 0d, dir.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            return null;
        }
        flat.normalize();
        Location spot = target.getLocation().clone().subtract(flat.multiply(APPROACH_DISTANCE));
        //保留原位高度（不做地形安全检查：这是瞬移刺击，允许穿墙观感）
        spot.setY(owner.getLocation().getY());
        return spot;
    }

    /** 让 {@code owner} 面向 {@code target}（水平朝向）。 */
    private static void faceTowards(Player owner, Player target) {
        Vector dir = target.getLocation().toVector().subtract(owner.getLocation().toVector());
        if (dir.lengthSquared() < 1.0E-6d) {
            return;
        }
        Location look = owner.getLocation().clone();
        look.setDirection(dir);
        owner.teleport(look);
    }

    /** 是否已解锁（排障 / 探针读口）。 */
    public boolean isUnlocked() {
        return TekTruth.isUnlocked();
    }

    /** 存活判定。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    // ───────── 技能物品画法：解锁 ⇒ 常亮光效 ─────────

    /**
     * 基类三态画法 + 「解锁 / 未解锁」表达：
     * <ul>
     *   <li><b>已解锁</b>（场上有人真理 ≥ {@value TekTruth#UNLOCK_THRESHOLD}）
     *       ⇒ <b>名字变彩虹跳字</b>（需求：3 技能名称变为彩虹跳字）+ 常亮附魔光效；</li>
     *   <li>未解锁 ⇒ 保留基类画法（冷却 / 结构空位 / 红屏障）+ 不加光效；</li>
     *   <li>冷却走完只是能量不足（本技能耗能 0 ⇒ 不可达）⇒ 结构空位也发光（照规范）。
     *       <b>DISABLED（红屏障）不叠光效</b>（那是更高优先级的"现在使不了"）。</li>
     * </ul>
     *
     * <p>★ 彩虹化的口径申报：直接把**基类刚画好的整条名字**（可能带
     * {@code " x.xs"} / {@code " DISABLED"} 后缀）逐字彩虹化 ⇒ 后缀文字仍在，
     * 因此"冷却中秒数 / 被禁用"这些信息**不会因为变彩虹而丢失**；
     * 三态状态本身另有**材质**（结构空位 / 红屏障）在表达，与名字着色无关。
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
        if (canUse() && isUnlocked()) {
            //★ 彩虹跳字：拿基类画好的名字逐字染色 + 叠加相位（相位由 update() 每刻推进）
            Component baseName = meta.displayName() != null ? meta.displayName() : getDisplayName();
            meta.displayName(TekRainbow.animated(baseName, rainbowPhase));
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
            return stack;
        }
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }
}
