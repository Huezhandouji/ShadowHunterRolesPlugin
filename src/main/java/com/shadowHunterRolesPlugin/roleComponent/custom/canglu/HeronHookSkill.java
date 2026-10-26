package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;

/**
 * 旋律选取（青金石，3 号位）：**双存储勾爪**（2026-10-25 重做）。
 *
 * <ul>
 *   <li>左键抛出勾爪（40 格/秒，最远 50 格），命中敌人 8 物理 + 4~6 特殊值</li>
 *   <li>再次释放 → 以 40 格/秒把自己拉过去，拉拽期间抗性 IV</li>
 *   <li>命中后最多附着 5 秒；附着期间**冰澜（音澜）只能使用右枪**</li>
 *   <li>存量 2 个，12 秒恢复一个，每个消耗 10 能量；**命中后才扣存量**
 *       （回复节拍与其它角色的存量同形：满仓不计时不扣能、缺能这一拍不充，见 {@link #advanceChargeRegen()}）</li>
 *   <li>绳索为白色粒子，飞行/附着时微微抛物线，拉拽时绷直</li>
 * </ul>
 */
public class HeronHookSkill extends Skill {

    public static final String ID = "cangluMelodySelectionSkill";

    public static final int MAX_CHARGES = 2;
    public static final int CHARGE_TICKS = 240;
    public static final int CHARGE_ENERGY = 10;
    public static final int COOLDOWN = 10;
    public static final double SPEED = 2.0d;
    public static final double MAX_DISTANCE = 50d;
    public static final double HIT_RADIUS = 1.5d;
    public static final int ATTACH_TICKS = 100;
    public static final double HOOK_DAMAGE = 8d;
    public static final int SANTE_MIN = 4;
    public static final int SANTE_MAX = 6;

    private enum Mode {IDLE, FLYING, ATTACHED, PULLING}

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private SanTEComponent sante;
    private FactionComponent faction;
    private BuffComponent buff;
    private HeronFloatingTextComponent voice;

    private Mode mode = Mode.IDLE;
    private ItemDisplay display;
    private ItemDisplay display2;
    private Location hookPos;
    private Vector dir = new Vector(0, 0, 1);
    private double flown;
    private int attachTicks;
    private UUID stuckEntity;
    private int charges = MAX_CHARGES;
    private int chargeTimer;
    private int phase;
    private com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent render;
    private int redFlash;
    private Location pullTarget;
    private boolean chargeSpent;
    private boolean hitGlow;

    public HeronHookSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends Skill.Specification<HeronHookSkill> {

        public Specification() {
            super(
                    Component.text("旋律选取"),
                    java.util.List.of(
                            Component.text("抛出勾爪（40 格/秒，最远 50 格）"),
                            Component.text("命中敌人：8 物理 + 4~6 特殊值"),
                            Component.text("再次释放把自己拉过去（抗性 IV）"),
                            Component.text("双存储，12 秒恢复一个（10 能量；缺能则不恢复）"),
                            Component.text("附着期间只能使用右枪")
                    ),
                    COOLDOWN,
                    0,
                    Material.LAPIS_LAZULI
            );
            requires(VitalsComponent.class).requires(EnergyComponent.class).requires(SanTEComponent.class)
                    .requires(FactionComponent.class).requires(BuffComponent.class);
        }

        @Override
        public HeronHookSkill create(String id, ComponentServicesPort services) {
            return new HeronHookSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        energy = getComponent(EnergyComponent.class);
        sante = getComponent(SanTEComponent.class);
        faction = getComponent(FactionComponent.class);
        buff = getComponent(BuffComponent.class);
        render = getComponent(com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent.class);
        voice = getComponent(HeronFloatingTextComponent.class);
    }

    @Override
    public void stop() {
        clearHook();
    }

    @Override
    public void onCast(CastSignal signal) {
        Player player = svc().self().player();
        if (player == null || signal.trigger() != CastTrigger.RIGHT_CLICK || !canUse()) {
            return;
        }
        if (mode == Mode.ATTACHED) {
            startPull(player);
        } else if (mode == Mode.IDLE) {
            launch(player);
        }
    }

    private void launch(Player player) {
        if (charges <= 0) {
            return;
        }
        chargeSpent = false;
        hitGlow = false;
        Vector look = player.getEyeLocation().getDirection().normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if(right.lengthSquared() < 1e-6){ right = new Vector(1, 0, 0); }
        right.normalize();
        //勾爪由**左手**发射（左手腹部高度，避免挡视野）
        hookPos = anchor(player).add(look.clone().multiply(0.8d));
        dir = look;
        mode = Mode.FLYING;
        flown = 0d;
        spawnDisplay(player);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.9f, 1.3f);
    }

    private void spawnDisplay(Player player) {
        display = (ItemDisplay) player.getWorld().spawnEntity(hookPos, org.bukkit.entity.EntityType.ITEM_DISPLAY);
        display.setItemStack(new ItemStack(Material.DIAMOND_PICKAXE));
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setPersistent(false);
        display.setSilent(true);
        display.setViewRange(1.5f);
        display.setRotation(player.getLocation().getYaw(), 0f);
        display.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(0.45f), new Quaternionf(new AxisAngle4f((float) Math.PI / 4f, 0, 0, 1))));
        //第二把：与第一把 90° 错开（两把镐交错）
        display2 = (ItemDisplay) player.getWorld().spawnEntity(hookPos, org.bukkit.entity.EntityType.ITEM_DISPLAY);
        display2.setItemStack(new ItemStack(Material.DIAMOND_PICKAXE));
        display2.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display2.setBillboard(org.bukkit.entity.Display.Billboard.FIXED);
        display2.setGravity(false);
        display2.setInvulnerable(true);
        display2.setPersistent(false);
        display2.setSilent(true);
        display2.setViewRange(1.5f);
        display2.setRotation(player.getLocation().getYaw(), 0f);
        display2.setTransformation(new Transformation(new Vector3f(), new Quaternionf(),
                new Vector3f(0.45f), new Quaternionf(new AxisAngle4f((float) Math.PI / 4f, 1, 0, 0))));
    }

    @Override
    public void update() {
        Player player = svc().self().player();
        if (player == null || !player.isOnline()) {
            clearHook();
            return;
        }
        phase++;
        advanceChargeRegen();
        switch (mode) {
            case FLYING -> tickFlying(player);
            case ATTACHED -> tickAttached(player);
            case PULLING -> tickPulling(player);
            default -> {
            }
        }
        //★ 存储恢复倒计时：**每 2 刻（0.1 秒）置一次脏**
        //  框架只在"占栏位且 isCoolingDown()"时才逐刻重绘，而本技能用的是**存量计时器**
        //  （chargeTimer），从不进冷却 ⇒ 不自己请求重绘的话，名字里的秒数会一动不动，
        //  直到下一次 charges 变化才跳一大格。
        if (charges < MAX_CHARGES && phase % 2 == 0) {
            syncItem();
        }
    }

    /**
     * **存量回复节拍** —— 与 {@code AlbertThrustSkill} / {@code RavenAirMobilityPassive} 的
     * 「储存回复」逐条同形（本仓口径）：
     * <ol>
     *   <li><b>满仓 ⇒ 计时归零、直接返回</b>：既不加上限、也不扣能量。
     *       计时归零是刻意的 —— 让"用掉一个"之后总是从完整的 {@value #CHARGE_TICKS} 刻重新算，
     *       而不是捡上个周期剩下的零头（旧实现满仓时仍在计时，用掉一个常常"立刻又回一个"）；</li>
     *   <li><b>未满仓才计时</b>；到点先把计时归零；</li>
     *   <li>用纯函数 {@link #shouldRegenCharge(int, int, int)} 判"该不该充"；</li>
     *   <li><b>能量不足 ⇒ 这一拍不充</b>（不欠账、不排队），下一拍重新等满一个周期 ——
     *       与其它角色的存储一致；名字后缀同时改显示「缺能」（见 {@link #buildItem()}）。</li>
     * </ol>
     */
    private void advanceChargeRegen() {
        //① 满仓：不计时、不扣能量
        if (charges >= MAX_CHARGES) {
            chargeTimer = 0;
            return;
        }
        //② 未满仓才计时
        if (++chargeTimer < CHARGE_TICKS) {
            return;
        }
        chargeTimer = 0;
        //③④ 该不该充 + 能量不足则这一拍不充
        int available = energy != null ? energy.current() : 0;
        if (!shouldRegenCharge(charges, MAX_CHARGES, available)) {
            return;
        }
        if (energy.tryConsume(CHARGE_ENERGY)) {
            charges++;
            syncItem();
        }
    }

    /**
     * **是否应当消耗能量回复一次存量**（**纯函数**，口径与
     * {@code AlbertThrustSkill#shouldRegenCharge} / {@code RavenAirMobilityPassive} 的同名函数一致）：
     * 未满仓 **且** 能量够付一次存储费。
     * <p>抽成纯函数的理由同先例：让"满仓还扣能量 / 缺能也硬充"这类静默错误能被单测钉住。
     */
    public static boolean shouldRegenCharge(int charges, int maxCharges, int energy) {
        return charges < maxCharges && energy >= CHARGE_ENERGY;
    }

    private void tickFlying(Player player) {
        if (display == null || !display.isValid() || hookPos == null) {
            clearHook();
            return;
        }
        Location next = hookPos.clone().add(dir.clone().multiply(SPEED));
        flown += SPEED;
        RayTraceResult blockHit = hookPos.getWorld().rayTraceBlocks(hookPos, dir, SPEED,
                org.bukkit.FluidCollisionMode.NEVER, true);
        Player victim = findVictim(player, hookPos, next);
        if (victim != null) {
            hit(player, victim, next);
            return;
        }
        if (blockHit != null) {
            hookPos = blockHit.getHitPosition().toLocation(hookPos.getWorld())
                    .add(dir.clone().multiply(-0.1d));
            spendCharge();
            mode = Mode.ATTACHED;
            attachTicks = 0;
            redFlash = 3;
            display.teleport(hookPos);
            if (display2 != null && display2.isValid()) { display2.teleport(hookPos); }
            player.getWorld().playSound(hookPos, Sound.BLOCK_CHAIN_PLACE, 0.8f, 1.4f);
            return;
        }
        if (flown >= MAX_DISTANCE) {
            clearHook();
            return;
        }
        hookPos = next;
        display.teleport(hookPos);
        if (display2 != null && display2.isValid()) { display2.teleport(hookPos); }
        rope(player, hookPos, 0.5d, false);
        //每 0.25 秒一闪
        if (phase % 5 == 0) {
            player.getWorld().spawnParticle(Particle.DUST, hookPos, 8, 0.08d, 0.08d, 0.08d, 0d,
                    new Particle.DustOptions(Color.fromRGB(160, 220, 255), 1.2f));
        }
    }

    private void tickAttached(Player player) {
        if (hookPos == null || display == null || !display.isValid()) {
            clearHook();
            return;
        }
        if (stuckEntity != null) {
            Player target = org.bukkit.Bukkit.getPlayer(stuckEntity);
            if (target == null || !target.isOnline() || target.isDead()) {
                clearHook();
                return;
            }
            hookPos = target.getLocation().add(0d, 0.9d, 0d);
            display.teleport(hookPos);
            if (display2 != null && display2.isValid()) { display2.teleport(hookPos); }
        }
        rope(player, hookPos, 0.02d, redFlash > 0);
        if (redFlash > 0) { redFlash--; }
        if (++attachTicks >= ATTACH_TICKS
                || player.getLocation().distanceSquared(hookPos) > MAX_DISTANCE * MAX_DISTANCE) {
            clearHook();
        }
    }

    private void startPull(Player player) {
        mode = Mode.PULLING;
        //拉拽终点：命中实体 ⇒ 实体脚下；命中方块 ⇒ 找一个能站人的位置（不往地里拉）
        Player target = stuckEntity == null ? null : org.bukkit.Bukkit.getPlayer(stuckEntity);
        pullTarget = (target != null && target.isOnline() && !target.isDead())
                ? target.getLocation().clone()
                : safeStand(hookPos);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.9f, 1.2f);
    }

    private void tickPulling(Player player) {
        if (hookPos == null || display == null || !display.isValid()) {
            clearHook();
            return;
        }
        Location dest = pullTarget != null ? pullTarget : hookPos;
        Location aim = dest.clone().add(0d, 1.0d, 0d);
        Vector delta = aim.toVector().subtract(player.getEyeLocation().toVector());
        double distance = delta.length();
        if (distance <= 1.6d) {
            //落点再做一次安全检查：脚/头两格可站立，否则往上找
            Location landing = safeStand(dest);
            //★★ `Player#teleport(Location)` 会把**视角**一起写成该 Location 的 yaw/pitch，
            //   而 safeStand() 造出来的是 `new Location(world, x, y, z)`（yaw=0 / pitch=0）；
            //   命中实体时 pullTarget 还是**目标**的 Location（yaw = 对方朝向）。
            //   ⇒ 到位瞬间角色会"突然转一下视角"（转向正南 / 转向敌人的朝向）。
            //   修法：把落点的朝向显式改成玩家**当前**朝向再做传送，位置变、视角不变。
            Location view = player.getLocation();
            landing.setYaw(view.getYaw());
            landing.setPitch(view.getPitch());
            player.teleport(landing);
            player.setVelocity(new Vector(0, 0, 0));
            player.setFallDistance(0f);
            clearHook();
            return;
        }
        Vector step = delta.normalize().multiply(Math.min(SPEED, Math.max(0.25d, distance - 1.2d)));
        player.setVelocity(step);
        player.setFallDistance(0f);
        player.getWorld().spawnParticle(Particle.DUST, player.getLocation().add(0d, 0.5d, 0d), 1,
                0.1d, 0.1d, 0.1d, 0d, new Particle.DustOptions(Color.fromRGB(200, 240, 255), 1f));
        buff.applyPotionEffect(PotionEffectType.RESISTANCE, 20, 3, true, false);
    }

    /** 从给定点向上找一个"两格空气 + 脚下实心"的落脚点，避免拉进地面卡住。 */
    private Location safeStand(Location at) {
        org.bukkit.World world = at.getWorld();
        int bx = at.getBlockX();
        int bz = at.getBlockZ();
        int startY = at.getBlockY();
        for (int dy = 0; dy <= 5; dy++) {
            int y = startY + dy;
            Location feet = new Location(world, bx + 0.5d, y, bz + 0.5d);
            boolean air = feet.getBlock().isPassable() && feet.clone().add(0d, 1d, 0d).getBlock().isPassable();
            boolean floor = feet.clone().add(0d, -1d, 0d).getBlock().getType().isSolid();
            if (air && floor) {
                return feet;
            }
        }
        return at.clone().add(0d, 1.2d, 0d);
    }

    private Player findVictim(Player shooter, Location from, Location to) {
        for (Player candidate : from.getNearbyPlayers(1.8d)) {
            if (candidate == null || candidate.equals(shooter) || !faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            if (candidate.getLocation().add(0d, 0.9d, 0d).distanceSquared(to) <= HIT_RADIUS * HIT_RADIUS) {
                return candidate;
            }
        }
        return null;
    }

    private void hit(Player player, Player victim, Location at) {
        vitals.physicalDamage(victim, player, HOOK_DAMAGE);
        sante.decreaseSanTE(victim.getUniqueId(),
                SANTE_MIN + java.util.concurrent.ThreadLocalRandom.current()
                        .nextInt(SANTE_MAX - SANTE_MIN + 1));
        stuckEntity = victim.getUniqueId();
        hookPos = victim.getLocation().add(0d, 0.9d, 0d);
        spendCharge();
        mode = Mode.ATTACHED;
        attachTicks = 0;
        redFlash = 3;
        display.teleport(hookPos);
        player.getWorld().playSound(at, Sound.ENTITY_ARROW_HIT, 0.9f, 0.8f);
        if (voice != null) { voice.sayMoment(player, HeronFloatingTextComponent.MOMENT_HOOK); }
        player.getWorld().spawnParticle(Particle.CRIT, at, 8, 0.2d, 0.2d, 0.2d, 0.1d);
    }

    /** 勾爪锚点：左手侧、腹部高度，离视线足够低，不挡视野。 */
    private Location anchor(Player player) {
        Vector look = player.getEyeLocation().getDirection().setY(0);
        if (look.lengthSquared() < 1e-6) { look = new Vector(0, 0, 1); }
        look.normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) { right = new Vector(1, 0, 0); }
        right.normalize();
        return player.getLocation().add(0d, 1.0d, 0d)
                .add(right.multiply(-0.38d))
                .add(look.multiply(0.3d));
    }

    /** 绳索：飞行时向下垂的抛物线，附着后绷直；{@code red} = 命中瞬间的红闪。 */
    private void rope(Player player, Location point, double sag, boolean red) {
        Location from = anchor(player);
        Vector delta = point.toVector().subtract(from.toVector());
        int steps = Math.max(4, (int) (delta.length() * 2));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double wave = Math.sin(t * Math.PI) * sag;
            Location at = from.clone().add(delta.clone().multiply(t)).add(0d, -wave, 0d);
            player.getWorld().spawnParticle(Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                    new Particle.DustOptions(red ? Color.fromRGB(255, 70, 70)
                            : Color.fromRGB(245, 248, 255), red ? 1.1f : 0.8f));
        }
    }

    /** 命中（实体或方块）才扣一层，每次投掷最多扣一次。 */
    private void spendCharge() {
        if (chargeSpent) {
            return;
        }
        chargeSpent = true;
        hitGlow = true;
        if (charges > 0) {
            charges--;
            syncItem();
        }
    }

    /** 是否有勾爪正附着（音澜据此禁用左枪）。 */
    public boolean isAttached() {
        return mode == Mode.ATTACHED;
    }

    private void clearHook() {
        if (display != null && display.isValid()) {
            display.remove();
        }
        if (display2 != null && display2.isValid()) {
            display2.remove();
        }
        display2 = null;
        display = null;
        hookPos = null;
        pullTarget = null;
        stuckEntity = null;
        mode = Mode.IDLE;
    }

    /** 栏位/手持：存量、存储 CD、命中后的红色名字 + 附魔；0 个时用结构空位。 */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        String skillId = null;
        ItemMeta baseMeta = stack.getItemMeta();
        if (baseMeta != null) {
            skillId = baseMeta.getPersistentDataContainer()
                    .get(Skill.Utils.SKILL_KEY, PersistentDataType.STRING);
        }
        String name = "旋律选取";
        if (charges <= 0) {
            stack.setType(Material.STRUCTURE_VOID);
            stack.setAmount(1);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        Component display = Component.text(name,
                        hitGlow ? NamedTextColor.RED : NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("  " + charges + "/" + MAX_CHARGES, NamedTextColor.WHITE));
        if (charges < MAX_CHARGES) {
            int available = energy != null ? energy.current() : 0;
            if (!shouldRegenCharge(charges, MAX_CHARGES, available)) {
                //★ 能量不足 ⇒ 这一拍根本存不上（口径同其它角色的存储）。
                //  此时**不要**再显示"数到 0 又从 12.0s 开始"的倒计时 —— 那看起来像存储坏了；
                //  直接写"缺能"，与技能"缺能就不放行"的表达一致。
                display = display.append(Component.text("  缺能", NamedTextColor.RED));
            } else {
                //★ 0.1 秒精度（"每 0.1 秒一跳"）：配套 update() 里每 2 刻 requestRepaint 一次；
                //  取整成整数秒会让数字 12 秒才动一次，看起来像卡住。
                double remain = Math.max(0d, CHARGE_TICKS - chargeTimer) / 20d;
                display = display.append(Component.text(
                        "  " + String.format(java.util.Locale.ROOT, "%.1f", remain) + "s", NamedTextColor.GRAY));
            }
        }
        meta.displayName(display);
        if (charges <= 0 && skillId != null) {
            meta.getPersistentDataContainer().set(Skill.Utils.SKILL_KEY, PersistentDataType.STRING, skillId);
        }
        meta.setEnchantmentGlintOverride(hitGlow);
        stack.setItemMeta(meta);
        return stack;
    }

    private void syncItem() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    @Override
    protected boolean canUse() {
        return buff == null || buff.canUseMainWeapon();
    }

    @Override
    protected int currentEnergy() {
        return energy == null ? 0 : energy.current();
    }
}
