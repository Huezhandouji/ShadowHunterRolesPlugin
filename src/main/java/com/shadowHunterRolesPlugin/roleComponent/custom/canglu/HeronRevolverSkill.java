package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 音澜：**双持左轮**（海晶砂粒模型，2 号位）。
 *
 * <ul>
 *   <li>左右各 6 发；物品数量 = 两把中**较高**的弹药，两把都满 ⇒ 附魔光效；名字里显示 `左 x/6 右 y/6`</li>
 *   <li>左键 = **左枪**，右键 = **右枪**；两条键**同刻都按下** ⇒ 齐射（{@link #flushInput()}）</li>
 *   <li>Q = 双手换弹（模拟"丢掉整把枪换弹匣"）</li>
 *   <li>每把枪射击间隔 0.15 秒；子弹 50 格/秒，8 物理 + 3~5 特殊值 + 0.5 击退</li>
 *   <li>换弹 1.6 秒 / 每把 15 能量；打空自动换弹；换弹进度显示在 subtitle</li>
 *   <li>子弹从**左右手**分别射出，向准星微微聚拢</li>
 * </ul>
 *
 * <h2>★ 为什么物品材质必须是「右键没有使用动作」的</h2>
 * 物品材质曾用 {@code SNOWBALL}（雪球）。雪球是**可右键使用**的物品 ⇒ 客户端 {@code startUseItem()}
 * 在 {@code useItem} 成功后会自己 {@code swing(hand)}，即**额外发一个挥臂包**；
 * 服务端于是把一次右键翻译成 `RIGHT_CLICK + LEFT_CLICK` 两个触发器 ⇒ 按右键会同时开两把枪，
 * 而"只开右枪"根本无法表达。
 * <p>换成 {@link Material#PRISMARINE_CRYSTALS}（右键无使用动作）后，客户端不再补发挥臂包：
 * 左键只来 {@code LEFT_CLICK}、右键只来 {@code RIGHT_CLICK}、双手同按则两个都来 —
 * 三个需求一次到位。**不要再把材质改回雪球 / 鸡蛋 / 末影珍珠这类投掷物。**
 */
public class HeronRevolverSkill extends Skill {

    public static final String ID = "cangluBlueIceRevolverSkill";

    public static final int MAX_AMMO = 6;
    public static final int RELOAD_ENERGY = 15;
    public static final int RELOAD_TICKS = 32;
    public static final int FIRE_INTERVAL_TICKS = 3;
    public static final int BULLET_DAMAGE = 8;
    public static final int SANTE_MIN = 3;
    public static final int SANTE_MAX = 5;
    public static final double BULLET_KNOCKBACK = 0.5d;
    public static final double SPEED_PER_TICK = 2.5d;
    public static final int BULLET_LIFE_TICKS = 20;
    /**
     * 单发命中半径（格）—— 到目标**身体竖直轴**的距离，不是到胸口那一个点的距离。
     * <p>0.65 → 0.90：左右两枪的枪口本来就横向错开 {@link #SIDE_OFFSET}（向准星 40 格处聚拢），
     * 敌人正好站在准星上（= 两发弹道中间）时，细半径会让两发都从两侧擦过去 ⇒ 表现为"打不到"。
     */
    public static final double HIT_RADIUS = 0.9d;
    public static final double SIDE_OFFSET = 0.38d;
    /** 身体轴取样高度（脚底向上）：0.2 ~ 1.8 覆盖站/蹲/跳的各种姿势。 */
    private static final double BODY_LOW = 0.2d;
    private static final double BODY_HIGH = 1.8d;
    /** 弹道线段上的取样层数（线段 ≤ {@link #SPEED_PER_TICK} 格，8 层误差 < 0.2 格）。 */
    private static final int BODY_SAMPLES = 8;

    private static final int[] EMPTY = {0, 0};

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private SanTEComponent sante;
    private FactionComponent faction;
    private BuffComponent buff;
    private HotbarRenderComponent render;
    private HeronTraumaPassive trauma;
    private HeronHookSkill hook;

    private final int[] ammo = {MAX_AMMO, MAX_AMMO};
    private final int[] fireCooldown = {0, 0};
    private final int[] reloadLeft = {0, 0};
    private final boolean[] reloading = {false, false};
    private final List<Bullet> bullets = new ArrayList<>();
    private boolean pendingLeft;
    private boolean pendingRight;

    public HeronRevolverSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends Skill.Specification<HeronRevolverSkill> {

        public Specification() {
            super(
                    Component.text("音澜"),
                    List.of(
                            Component.text("双持左轮：左右各 6 发，左键左枪 / 右键右枪"),
                            Component.text("左右键同刻按下：两把枪一起开火"),
                            Component.text("单发 8 物理 + 3~5 特殊值 + 0.5 击退"),
                            Component.text("换弹 1.6 秒、每把 15 能量；打空自动换弹"),
                            Component.text("Q：双手换弹")
                    ),
                    1,
                    0,
                    //★ 不能改用雪球/鸡蛋/末影珍珠：可右键使用的物品会让客户端补发挥臂包，
                    //  一次右键变成 RIGHT_CLICK + LEFT_CLICK（见类 javadoc）
                    Material.PRISMARINE_CRYSTALS
            );
            requires(VitalsComponent.class).requires(EnergyComponent.class).requires(SanTEComponent.class)
                    .requires(FactionComponent.class).requires(BuffComponent.class)
                    .requires(HeronTraumaPassive.class);
            requiresOptional(HotbarRenderComponent.class);
            requires(HeronHookSkill.class);
        }

        @Override
        public HeronRevolverSkill create(String id, ComponentServicesPort services) {
            return new HeronRevolverSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        energy = getComponent(EnergyComponent.class);
        sante = getComponent(SanTEComponent.class);
        faction = getComponent(FactionComponent.class);
        buff = getComponent(BuffComponent.class);
        trauma = getComponent(HeronTraumaPassive.class);
        hook = getComponent(HeronHookSkill.class);
        render = getComponent(HotbarRenderComponent.class);
    }

    @Override
    public void stop() {
        bullets.clear();
        java.util.Arrays.fill(ammo, MAX_AMMO);
        java.util.Arrays.fill(reloading, false);
    }

    @Override
    public void onCast(CastSignal signal) {
        if (!canUse()) {
            return;
        }
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        switch (signal.trigger()) {
            //★ 左键 = 左枪，右键 = 右枪：两条键各记一个 pending，**同刻都按下才齐射**（见 flushInput）。
            //  这里不再有"潜行右键 = 换弹"的分支 —— 那会让蹲着打架时右枪永远打不出去（需求：右键必须开火）；
            //  换弹统一走 Q（DROP）。
            case LEFT_CLICK -> pendingLeft = true;
            case RIGHT_CLICK -> pendingRight = true;
            case DROP -> {
                reload(player, 0);
                reload(player, 1);
            }
        }
    }

    /**
     * 每刻统一结算按键：**左键只开左枪，右键只开右枪；同刻都按下才齐射**。
     * 把开火推迟到 tick 末尾，避免"同一次点击派发多个触发器"被当成双发。
     */
    private void flushInput() {
        Player player = svc().self().player();
        if (player == null) {
            pendingLeft = false;
            pendingRight = false;
            return;
        }
        if (pendingLeft) {
            fire(player, 0);
        }
        if (pendingRight) {
            fire(player, 1);
        }
        pendingLeft = false;
        pendingRight = false;
    }

    // ───────── 开火 ─────────

    private void fire(Player player, int hand) {
        //勾爪附着期间只能使用右枪
        if (hand == 0 && hook != null && hook.isAttached()) {
            return;
        }
        if (reloading[hand] || fireCooldown[hand] > 0) {
            return;
        }
        if (ammo[hand] <= 0) {
            reload(player, hand);
            return;
        }
        ammo[hand]--;
        fireCooldown[hand] = FIRE_INTERVAL_TICKS;

        Vector look = player.getEyeLocation().getDirection().normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1e-6) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        //hand 0 = 左手 ⇒ 屏幕左侧（-right），hand 1 = 右手 ⇒ 右侧
        double side = hand == 0 ? -SIDE_OFFSET : SIDE_OFFSET;
        Location origin = player.getEyeLocation().add(right.clone().multiply(side))
                .add(look.clone().multiply(0.4d));
        // 微微向准星聚拢：目标点取视线上 40 格处
        Location aim = player.getEyeLocation().add(look.clone().multiply(40d));
        Vector dir = aim.toVector().subtract(origin.toVector()).normalize();

        bullets.add(new Bullet(origin.clone(), dir, player.getUniqueId()));

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT,
                0.55f, 2.5f);
        player.getWorld().spawnParticle(Particle.DUST, origin, 6, 0.05d, 0.05d, 0.05d, 0d,
                new Particle.DustOptions(Color.fromRGB(150, 220, 255), 0.9f));
        syncItem();
        if (ammo[hand] <= 0) {
            reload(player, hand);
        }
    }

    private void reload(Player player, int hand) {
        if (reloading[hand] || ammo[hand] >= MAX_AMMO) {
            return;
        }
        if (energy == null || !energy.tryConsume(RELOAD_ENERGY)) {
            return;
        }
        reloading[hand] = true;
        reloadLeft[hand] = RELOAD_TICKS;
    }

    // ───────── 逐刻 ─────────

    @Override
    public void update() {
        flushInput();
        Player player = svc().self().player();
        if (player == null || !player.isOnline()) {
            return;
        }
        for (int hand = 0; hand < 2; hand++) {
            if (fireCooldown[hand] > 0) {
                fireCooldown[hand]--;
            }
            if (!reloading[hand]) {
                continue;
            }
            if (--reloadLeft[hand] <= 0) {
                reloading[hand] = false;
                ammo[hand] = MAX_AMMO;
                syncItem();
            }
        }
        tickReloadHud(player);
        tickBullets(player);
    }

    private void tickReloadHud(Player player) {
        if (!reloading[0] && !reloading[1]) {
            return;
        }
        int hand = reloading[1] && !reloading[0] ? 1 : 0;
        int total = RELOAD_TICKS;
        int left = reloadLeft[hand];
        int filled = Math.max(0, Math.min(10, (total - left) * 10 / total));
        String bar = "█".repeat(filled) + "░".repeat(10 - filled);
        String text = "换弹 " + bar + "  " + (hand == 0 ? "左" : "右");
        player.showTitle(Title.title(Component.empty(),
                Component.text(text, NamedTextColor.AQUA).decorate(TextDecoration.BOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(300), Duration.ZERO)));
    }

    private void tickBullets(Player shooter) {
        if (bullets.isEmpty()) {
            return;
        }
        List<Bullet> dead = new ArrayList<>();
        for (Bullet bullet : bullets) {
            if (++bullet.life > BULLET_LIFE_TICKS) {
                dead.add(bullet);
                continue;
            }
            Location from = bullet.pos.clone();
            Location next = from.clone().add(bullet.dir.clone().multiply(SPEED_PER_TICK));
            RayTraceResult blockHit = from.getWorld().rayTraceBlocks(from, bullet.dir, SPEED_PER_TICK,
                    FluidCollisionMode.NEVER, true);
            Player victim = firstHostileAlong(shooter, from, next);
            if (victim != null) {
                hit(shooter, victim, next);
                dead.add(bullet);
                continue;
            }
            if (blockHit != null) {
                from.getWorld().spawnParticle(Particle.DUST, blockHit.getHitPosition().toLocation(from.getWorld()),
                        5, 0.05d, 0.05d, 0.05d, 0d, new Particle.DustOptions(Color.fromRGB(110, 190, 250), 0.8f));
                dead.add(bullet);
                continue;
            }
            bullet.pos = next;
            from.getWorld().spawnParticle(Particle.DUST, next, 2, 0.02d, 0.02d, 0.02d, 0d,
                    new Particle.DustOptions(Color.fromRGB(170, 230, 255), 0.7f));
        }
        bullets.removeAll(dead);
    }

    /**
     * 本刻弹道（{@code from → to}）上第一个敌对目标。
     *
     * <p>两处判定都必须对：
     * <ul>
     *   <li><b>搜索球</b>：以**线段中点**为心、半径 = 半长 + {@link #HIT_RADIUS} + 1.2。
     *       旧实现以 {@code from} 为心、固定 2.2 格 —— 而一帧要走 {@link #SPEED_PER_TICK} 格，
     *       目标正好落在弹道**末端**时到 {@code from} 的距离可能 > 2.2，于是"看着穿过去了却没伤害"。
     *       这是"概率性打不到"的主要来源。</li>
     *   <li><b>命中体</b>：到目标**身体竖直轴**（脚上 {@value #BODY_LOW} ~ {@value #BODY_HIGH}）的最近距离，
     *       不是到"脚上 0.9 格"那一个点的距离（蹲下、跳跃、半砖上都会偏）。</li>
     * </ul>
     */
    private Player firstHostileAlong(Player shooter, Location from, Location to) {
        Vector seg = to.toVector().subtract(from.toVector());
        double len = seg.length();
        if (len < 1e-6) {
            return null;
        }
        Location mid = from.clone().add(seg.clone().multiply(0.5d));
        double searchRadius = len * 0.5d + HIT_RADIUS + 1.2d;
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : mid.getNearbyPlayers(searchRadius)) {
            if (candidate == null || candidate.equals(shooter) || !faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            double distance = distanceToBody(from, seg, candidate);
            //取最近的那个：命中结算给最先被打到的人，而不是遍历顺序里的第一个
            if (distance <= HIT_RADIUS && distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** 弹道线段到"目标身体竖直轴"的最近距离（分层取样；线段 ≤ 2.5 格 ⇒ 误差 < 0.2 格）。 */
    private static double distanceToBody(Location from, Vector seg, Player target) {
        Location feet = target.getLocation();
        double low = feet.getY() + BODY_LOW;
        double high = feet.getY() + BODY_HIGH;
        double best = Double.MAX_VALUE;
        for (int i = 0; i <= BODY_SAMPLES; i++) {
            Vector point = from.toVector().add(seg.clone().multiply(i / (double) BODY_SAMPLES));
            //把该点的 y 夹到身体轴区间内 ⇒ 等价于"点到竖直线段"的距离
            double y = Math.clamp(point.getY(), low, high);
            Vector onAxis = new Vector(feet.getX(), y, feet.getZ());
            best = Math.min(best, point.distance(onAxis));
        }
        return best;
    }

    private void hit(Player shooter, Player victim, Location at) {
        vitals.physicalDamage(victim, shooter, BULLET_DAMAGE, BULLET_KNOCKBACK);
        sante.decreaseSanTE(victim.getUniqueId(),
                SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1));
        if (trauma != null) {
            trauma.requestAddStackCount(1);
        }
        at.getWorld().spawnParticle(Particle.DUST, at, 10, 0.15d, 0.15d, 0.15d, 0d,
                new Particle.DustOptions(Color.fromRGB(150, 220, 255), 1.1f));
        at.getWorld().spawnParticle(Particle.CRIT, at, 6, 0.1d, 0.1d, 0.1d, 0.05d);
    }

    // ───────── 物品 ─────────

    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        String skillId = null;
        ItemMeta baseMeta = stack.getItemMeta();
        if (baseMeta != null) {
            skillId = baseMeta.getPersistentDataContainer()
                    .get(Skill.Utils.SKILL_KEY, PersistentDataType.STRING);
        }
        boolean empty = ammo[0] <= 0 && ammo[1] <= 0;
        if (empty) {
            stack.setType(Material.BARRIER);
            stack.setAmount(1);
        } else {
            stack.setAmount(Math.max(1, Math.max(ammo[0], ammo[1])));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        Component name = Component.text("音澜", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text("  左 " + ammo[0] + "/" + MAX_AMMO, NamedTextColor.WHITE))
                .append(Component.text("  右 " + ammo[1] + "/" + MAX_AMMO, NamedTextColor.WHITE));
        if (empty) {
            name = Component.text("音澜", NamedTextColor.AQUA).decorate(TextDecoration.BOLD)
                    .append(Component.text("  无子弹", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        }
        meta.displayName(name);
        if (empty && skillId != null) {
            meta.getPersistentDataContainer().set(Skill.Utils.SKILL_KEY, PersistentDataType.STRING, skillId);
        }
        meta.setEnchantmentGlintOverride(!empty && ammo[0] >= MAX_AMMO && ammo[1] >= MAX_AMMO);
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
        return 0;
    }

    /** 单发子弹。 */
    private static final class Bullet {
        private Location pos;
        private final Vector dir;
        private final java.util.UUID owner;
        private int life;

        private Bullet(Location pos, Vector dir, java.util.UUID owner) {
            this.pos = pos;
            this.dir = dir;
            this.owner = owner;
        }
    }
}
