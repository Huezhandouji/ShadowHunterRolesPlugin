package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastTrigger;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * 湛蓝命运（末影水晶，4 号位）：**突进 + 领域**。
 *
 * <ul>
 *   <li>突进：0.15 秒内向前 8 格，抗性 IV、**10 点伤害吸收（持续 4 秒）**、回复 8 SanTE、叠 2 层创伤</li>
 *   <li>领域：半径 25 格、持续 20 秒；领域内本技能 CD 变为 4 秒且只生效突进</li>
 *   <li>领域内每次攻击额外 +1 真实伤害；离开领域即消失</li>
 *   <li>四把巨剑插在领域边缘缓慢旋转；结束时沉入地面</li>
 *   <li>领域结束后才进入 25 秒冷却</li>
 * </ul>
 *
 * <h2>★ 能量口径：<b>每一次使用（展开领域 / 领域内突进）都恰好消耗 {@value #ENERGY_COST} 能量</b></h2>
 * 三条入口（展开领域、领域内突进、以及"领域已结束但巨剑还在下沉"的那段）**全部**走
 * {@link #spendEnergy(Player)} —— 它是唯一的扣能点：先硬判 {@code current() >= 10}，再
 * {@code tryConsume(10)}，两者都成立才算这次使用成功。任何一条路径都不允许"先出效果再判能量"。
 * <p>配套的三道闸门（缺一就会出现"某次使用不扣能"或"扣了能却没出效果"）：
 * <ol>
 *   <li>只认 {@link CastTrigger#RIGHT_CLICK}：左键 / Q 不再施放（{@code END_CRYSTAL} 可被左键误触，
 *       以前会把一次误点当成一次使用）；</li>
 *   <li>{@code sinking} 期间（领域已结束、巨剑正在消退）不许突进 —— 以前这 12 刻里
 *       {@code domainCenter} 还没清空，能再突进一次而图标显示的是 25 秒冷却；</li>
 *   <li>突进分支也要过 {@code isCoolingDown()} —— 领域结束时那 25 秒冷却对突进同样有效。</li>
 * </ol>
 */
public class HeronFateSkill extends Skill {

    public static final String ID = "cangluDestinySkill";

    public static final int ENERGY_COST = 10;
    public static final double DASH_DISTANCE = 8d;
    public static final int DASH_TICKS = 3;
    /** 每次突进获得 10 点伤害吸收，持续 {@value #ABSORPTION_TICKS} 刻 = 4 秒（与技能介绍一致）。 */
    public static final double ABSORPTION_AMOUNT = 10d;
    public static final int ABSORPTION_TICKS = 80;
    public static final double DOMAIN_RADIUS = 25d;
    public static final int DOMAIN_TICKS = 400;
    public static final int DASH_COOLDOWN_TICKS = 80;
    public static final int COOLDOWN_AFTER_DOMAIN = 500;

    private static final Sound[] DISCS = {
            Sound.MUSIC_DISC_CAT, Sound.MUSIC_DISC_BLOCKS, Sound.MUSIC_DISC_CHIRP,
            Sound.MUSIC_DISC_FAR, Sound.MUSIC_DISC_MALL, Sound.MUSIC_DISC_STRAD
    };

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private SanTEComponent sante;
    private BuffComponent buff;
    private HeronTraumaPassive trauma;

    private Location domainCenter;
    private int domainTicks;
    private int dashCooldown;
    /** 伤害吸收的到期刻（{@code 0} = 没有我们在计时的那层吸收）。 */
    private int absorptionUntilTick;
    private int phase;
    private final List<ItemDisplay> swords = new ArrayList<>();
    private boolean sinking;
    private int sinkTicks;
    private int swordAnim;
    private HeronFloatingTextComponent voice;

    public HeronFateSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends Skill.Specification<HeronFateSkill> {

        public Specification() {
            super(
                    Component.text("湛蓝命运"),
                    List.of(
                            Component.text("突进 8 格（抗性 IV / 10 点吸收 4 秒 / 回 8 SanTE / 2 层创伤）"),
                            Component.text("展开半径 25 格、持续 20 秒的领域"),
                            Component.text("领域内本技能 CD 4 秒，只能突进"),
                            Component.text("领域内攻击额外 +1 真实伤害"),
                            Component.text("每次使用消耗 10 能量"),
                            Component.text("领域结束后进入 25 秒冷却")
                    ),
                    COOLDOWN_AFTER_DOMAIN,
                    ENERGY_COST,
                    Material.END_CRYSTAL
            );
            requires(VitalsComponent.class).requires(EnergyComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(HeronTraumaPassive.class);
        }

        @Override
        public HeronFateSkill create(String id, ComponentServicesPort services) {
            return new HeronFateSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        energy = getComponent(EnergyComponent.class);
        sante = getComponent(SanTEComponent.class);
        buff = getComponent(BuffComponent.class);
        trauma = getComponent(HeronTraumaPassive.class);
        voice = getComponent(HeronFloatingTextComponent.class);
    }

    @Override
    public void stop() {
        removeSwords();
        //本组件没了就没人收尾吸收 ⇒ 只清"我们自己给的那一层"（别人给得更多时不动）
        Player player = svc().self().player();
        if (absorptionUntilTick > 0 && player != null && player.getAbsorptionAmount() <= ABSORPTION_AMOUNT) {
            player.setAbsorptionAmount(0d);
        }
        absorptionUntilTick = 0;
    }

    @Override
    public void onCast(CastSignal signal) {
        Player player = svc().self().player();
        if (player == null || !buff.canCastSkill()) {
            return;
        }
        //★ 只认右键：左键 / Q 不是"使用大招"，不该扣能也不该出效果
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        //★ 领域已结束、巨剑正在下沉的那 12 刻：既不能重开领域也不能突进
        if (sinking) {
            return;
        }
        if (domainCenter == null) {
            //展开领域：冷却 / 能量两道闸门都在扣能**之前**
            if (isCoolingDown() || !spendEnergy(player)) {
                return;
            }
            openDomain(player);
            dash(player);
            return;
        }
        if (dashCooldown > 0 || isCoolingDown() || !spendEnergy(player)) {
            return;
        }
        dash(player);
        dashCooldown = DASH_COOLDOWN_TICKS;
        //领域内 CD 4 秒：图标也走真实冷却
        startCooldown(DASH_COOLDOWN_TICKS);
    }

    /**
     * **唯一扣能点**：硬判"够不够 10"再扣，返回这次使用是否被允许。
     *
     * <p>{@code EnergyComponent#tryConsume} 本身已经是"不足则 {@code false} 且不扣"，
     * 这里显式再读一次 {@code current()} 是为了让"能量不足"这条分支**永远不产生效果**，
     * 且能顺手给玩家一个失败音（不占 actionbar —— 那条通道归创伤被动）。
     * <p>调用点只有三处（展开领域 / 领域内突进 / 未来新增入口），**不要在别处再写一次扣能**。
     */
    private boolean spendEnergy(Player player) {
        if (energy == null) {
            return false;
        }
        if (energy.current() < ENERGY_COST || !energy.tryConsume(ENERGY_COST)) {
            if (player != null) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.6f);
            }
            return false;
        }
        return true;
    }

    private void openDomain(Player player) {
        domainCenter = player.getLocation().clone();
        domainTicks = 0;
        phase = 0;
        sinking = false;
        spawnSwords(player);
        if (voice != null) { voice.sayMoment(player, HeronFloatingTextComponent.MOMENT_DOMAIN); }
        player.getWorld().playSound(player.getLocation(), DISCS[java.util.concurrent.ThreadLocalRandom.current().nextInt(DISCS.length)],
                1.0f, 1.0f);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 0.7f);
    }

    private void dash(Player player) {
        Vector dir = player.getEyeLocation().getDirection().setY(0).normalize();
        Location target = player.getLocation().clone();
        RayTraceResult hit = player.getWorld().rayTraceBlocks(player.getEyeLocation(), dir, DASH_DISTANCE,
                org.bukkit.FluidCollisionMode.NEVER, true);
        double distance = hit == null ? DASH_DISTANCE
                : Math.max(0d, player.getEyeLocation().toVector().distance(hit.getHitPosition()) - 1d);
        target.add(dir.clone().multiply(distance));
        player.teleport(target);
        player.setFallDistance(0f);
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, 20, 3, true, false);
        applyAbsorption(player);
        sante.increase(8);
        if (trauma != null) {
            trauma.requestAddStackCount(2);
        }
        for (int i = 0; i < 20; i++) {
            Location at = player.getLocation().add(0d, 0.4d + i * 0.08d, 0d)
                    .add(dir.clone().multiply(-i * 0.25d));
            player.getWorld().spawnParticle(Particle.DUST, at, 1, 0.1d, 0.1d, 0.1d, 0d,
                    new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.1f));
        }
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 1.4f);
    }

    /**
     * **4 秒的 10 点伤害吸收**（需求："每次使用获得 4S 的 10 点伤害吸收，和技能介绍一致"）。
     *
     * <p>为什么不用原版 {@code ABSORPTION} 效果：那一档是 4 点一跳（0 级 4 / 1 级 8 / 2 级 12），
     * 拼不出正好 10 点。所以走 {@code setAbsorptionAmount(10)} + 自己计时到期清零 ——
     * 数值精确 10，时长精确 {@value #ABSORPTION_TICKS} 刻，重复施放只是刷新计时（不叠加）。
     */
    private void applyAbsorption(Player player) {
        player.setAbsorptionAmount(ABSORPTION_AMOUNT);
        absorptionUntilTick = org.bukkit.Bukkit.getCurrentTick() + ABSORPTION_TICKS;
    }

    /**
     * 吸收到期收尾：只清"我们自己给的那一层" —— 若此刻吸收量 > 10（别的来源给的更多），
     * 说明玩家身上叠了更大的护盾，不动它，避免把别人的效果一起抹掉。
     */
    private void tickAbsorption() {
        if (absorptionUntilTick <= 0 || org.bukkit.Bukkit.getCurrentTick() < absorptionUntilTick) {
            return;
        }
        absorptionUntilTick = 0;
        Player player = svc().self().player();
        if (player != null && player.getAbsorptionAmount() <= ABSORPTION_AMOUNT) {
            player.setAbsorptionAmount(0d);
        }
    }

    @Override
    public void update() {
        if (dashCooldown > 0) {
            dashCooldown--;
        }
        //★ 4 秒吸收的到期必须**先于**"领域还在不在"的判据：领域结束/玩家死亡时
        //  领域分支会直接 return，吸收就永远没人收了。
        tickAbsorption();
        if (domainCenter == null) {
            return;
        }
        //★ 消退必须能自己推进，且**排在所有"玩家还活着吗"的判据之前**：
        //  旧顺序是先判 isDead()→endDomain(false)，而 endDomain 一进来就把 sinking 置 true；
        //  下一刻又因为玩家还死着直接 return ⇒ tickSink 永远不跑 ⇒ 巨剑永留、domainCenter 永不清空，
        //  大招从此只能突进、再也开不出领域。
        if (sinking) {
            tickSink();
            return;
        }
        Player player = svc().self().player();
        if (player == null || !player.isOnline() || player.isDead()) {
            endDomain(false);
            return;
        }
        if (++domainTicks >= DOMAIN_TICKS) {
            endDomain(true);
            return;
        }
        if (player.getWorld() != domainCenter.getWorld()
                || player.getLocation().distanceSquared(domainCenter) > DOMAIN_RADIUS * DOMAIN_RADIUS) {
            endDomain(true);
            return;
        }
        phase++;
        drawCircle(domainCenter);
        animateSwords(player);
    }

    private void drawCircle(Location center) {
        for (int i = 0; i < 90; i++) {
            double a = Math.toRadians(i * 4d + phase);
            Location at = center.clone().add(Math.cos(a) * DOMAIN_RADIUS, 0.15d, Math.sin(a) * DOMAIN_RADIUS);
            center.getWorld().spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                    new Particle.DustOptions(Color.fromRGB(110, 190, 250), 1.2f));
        }
        if (phase % 10 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = Math.toRadians(i * 15d - phase * 2d);
                Location at = center.clone().add(Math.cos(a) * 6d, 0.2d, Math.sin(a) * 6d);
                center.getWorld().spawnParticle(Particle.END_ROD, at, 1, 0d, 0d, 0d, 0d);
            }
        }
    }

    private void spawnSwords(Player player) {
        removeSwords();
        swordAnim = 0;
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90d);
            Location at = domainCenter.clone().add(Math.cos(a) * (DOMAIN_RADIUS - 2d), 1.0d,
                    Math.sin(a) * (DOMAIN_RADIUS - 2d));
            ItemDisplay sword = (ItemDisplay) player.getWorld().spawnEntity(at, org.bukkit.entity.EntityType.ITEM_DISPLAY);
            sword.setItemStack(new ItemStack(Material.DIAMOND_SWORD));
            sword.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            sword.setBillboard(Display.Billboard.FIXED);
            sword.setGravity(false);
            sword.setInvulnerable(true);
            sword.setPersistent(false);
            sword.setSilent(true);
            sword.setViewRange(3f);
            //剑尖朝下，直插地面
            sword.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf(new AxisAngle4f((float) Math.toRadians(-135d), 0, 0, 1)),
                    new Vector3f(6f), new Quaternionf()));
            swords.add(sword);
        }
    }

    /**
     * 入场动画：**先提起 → 悬停 → 向下砸 → 落地冲击**，之后贴地绕圈旋转。
     * 时间轴（刻）：0~10 提起 / 10~16 悬停 / 16~20 下砸 / 20 冲击 / 20+ 旋转。
     */
    private void animateSwords(Player player) {
        swordAnim++;
        double y;
        if (swordAnim <= 10) {
            y = 1.0d + 5.0d * swordAnim / 10d;
        } else if (swordAnim <= 16) {
            y = 6.0d;
        } else if (swordAnim <= 20) {
            y = 6.0d - 5.0d * (swordAnim - 16) / 4d;
        } else {
            y = 1.0d;
        }
        for (int i = 0; i < swords.size(); i++) {
            ItemDisplay sword = swords.get(i);
            if (sword == null || !sword.isValid()) {
                continue;
            }
            double a = Math.toRadians(i * 90d + Math.max(0, swordAnim - 20) * 2d);
            Location at = domainCenter.clone().add(Math.cos(a) * (DOMAIN_RADIUS - 2d), y,
                    Math.sin(a) * (DOMAIN_RADIUS - 2d));
            sword.teleport(at);
            //剑身不自转，只围绕角色公转
            sword.setTransformation(new Transformation(new Vector3f(),
                    new Quaternionf(new AxisAngle4f((float) Math.toRadians(-135d), 0, 0, 1)),
                    new Vector3f(6f), new Quaternionf()));
            //每 2 刻一道蓝色剑光
            if (swordAnim % 2 == 0) {
                for (int k = 0; k < 3; k++) {
                    Location spark = at.clone().add(0d, 1.0d + k * 2.2d, 0d);
                    at.getWorld().spawnParticle(Particle.DUST, spark, 1, 0.15d, 0.3d, 0.15d, 0d,
                            new Particle.DustOptions(Color.fromRGB(120, 200, 255), 1.1f));
                }
            }
        }
        //下砸落地冲击
        if (swordAnim == 20 && domainCenter != null) {
            for (int i = 0; i < 4; i++) {
                double a = Math.toRadians(i * 90d);
                Location base = domainCenter.clone().add(Math.cos(a) * (DOMAIN_RADIUS - 2d), 0.2d,
                        Math.sin(a) * (DOMAIN_RADIUS - 2d));
                for (int k = 0; k < 24; k++) {
                    double ra = Math.toRadians(k * 15d);
                    Location at = base.clone().add(Math.cos(ra) * 1.2d, 0d, Math.sin(ra) * 1.2d);
                    base.getWorld().spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                            new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.2f));
                }
                base.getWorld().spawnParticle(Particle.SWEEP_ATTACK, base, 1, 0d, 0d, 0d, 0d);
            }
            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.8f, 0.6f);
            player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 0.7f);
        }
    }

    /** 领域结束：巨剑在随后 12 刻里缓慢下沉，然后移除并进入冷却。 */
    private void endDomain(boolean startCooldown) {
        if (sinking) {
            return;
        }
        sinking = true;
        sinkTicks = 0;
        if (startCooldown) {
            startCooldown(COOLDOWN_AFTER_DOMAIN);
        }
    }

    private void tickSink() {
        sinkTicks++;
        float scale = Math.max(0f, 6f * (1f - sinkTicks / 12f));
        for (ItemDisplay sword : swords) {
            if (sword != null && sword.isValid()) {
                //消退时同时缓缓下落
                sword.teleport(sword.getLocation().subtract(0d, 0.15d, 0d));
                sword.setTransformation(new Transformation(new Vector3f(),
                        new Quaternionf(new AxisAngle4f((float) Math.toRadians(-135d), 0, 0, 1)),
                        new Vector3f(scale), new Quaternionf()));
                if (sinkTicks >= 12) {
                    sword.remove();
                }
            }
        }
        if (sinkTicks >= 12) {
            swords.clear();
            domainCenter = null;
            sinking = false;
        }
    }

    private void removeSwords() {
        for (ItemDisplay sword : swords) {
            if (sword != null && sword.isValid()) {
                sword.remove();
            }
        }
        swords.clear();
    }

    /** 目标是否站在领域内（主武器 +1 真伤判定）。 */
    public boolean isInDomain(Player target) {
        return domainCenter != null && target != null
                && target.getWorld() == domainCenter.getWorld()
                && target.getLocation().distanceSquared(domainCenter) <= DOMAIN_RADIUS * DOMAIN_RADIUS;
    }

    /**
     * 「放行」只回答**闸门**（眩晕 / 沉默），**不含能量**。
     * <p>★ 这是全仓技能的统一口径（先例 `SayakaGrenadeSkill` / `TekTruthThrustSkill`）：
     * 能量维度由框架的 {@code IconState.of(ready, canUse, currentEnergy, getEnergyCost())}
     * 单独判 —— 顺序是「冷却 → 禁用 → **能量不足** → 就绪」。
     * <p>旧写法把 `energy.current() >= ENERGY_COST` 也塞进本方法 ⇒ 能量不足时会先命中
     * `!canUse` 那一档，图标变成**红色屏障 DISABLED**（"被眩晕"的样子），
     * 与其它角色的**结构空位 ENERGY LACK**不一致。改回只判闸门即可。
     */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        //与其它技能同形：取不到组件时回"恰好够"，避免误显示能量不足
        return energy != null ? energy.current() : getEnergyCost();
    }
}
