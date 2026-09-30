package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.SoundUtil;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * **澜冰左轮**（苍鹭）：一把八发左轮。
 *
 * <h2>玩法</h2>
 * <ul>
 *   <li>右键 = 射击：每发子弹 6 点物理伤害 + 击退；弹夹打空后若能量足够则自动开始换弹；
 *       物品名上实时显示剩余子弹数；</li>
 *   <li>Q（丢弃键）= 换弹：消耗 8 点能量，瞬移到最近一名敌人身后（半径 8），
 *       并对它造成 4 点真实伤害；换弹会启动冷却（{@link #RELOAD_COOLDOWN_TICKS}），
 *       图标按普通技能的冷却形态显示倒计时；</li>
 *   <li>子弹：离开枪口后每 tick 前进，撞墙、命中敌人或超时则销毁。</li>
 * </ul>
 *
 * <h2>子弹用 Marker 而不是真实投射物</h2>
 * Marker 无碰撞箱、不可被击退、不参与实体伤害（纯视觉/坐标载体），因此伤害与命中判定
 * 完全由本组件掌握，不会被原版投射物规则（爆炸、流体、被清除、`ProjectileHitEvent` 的取消）干扰。
 *
 * <h2>数值都是常量（要调只改这一处）</h2>
 * 见下方 {@code MAX_MAGAZINE_CAPACITY} … {@code RELOAD_AUTO_CHECK_INTERVAL}。
 */
public class CangluBlueIceRevolverSkill extends Skill {

    public static final String ID = "cangluBlueIceRevolverSkill";
    // ───────── 数值（唯一修改点）─────────

    /** 一个弹夹的容量。 */
    private static final int MAX_MAGAZINE_CAPACITY = 8;
    /** 换弹的能量消耗。 */
    private static final int RELOAD_ENERGY_CONSUMPTION = 8;
    /**
     * **换弹冷却**（tick）：换弹成功后调 {@code startCooldown(RELOAD_COOLDOWN_TICKS)}，则
     * 物品图标按普通技能的冷却形态显示（灰名 + `x.xs` 倒计时 + 状态行），
     * 且期间框架按 {@code isCoolingDown()} 拦住再次施放（换弹占用时长 = 该值）。
     */
    private static final int RELOAD_COOLDOWN_TICKS = 40;
    /** 每发子弹的物理伤害。 */
    private static final int BULLET_DAMAGE = 6;
    /** 每发子弹的击退强度（传给 {@code VitalsComponent#physicalDamage}）。 */
    private static final double BULLET_KNOCKBACK = 0.1;
    /** 换弹瞬移后对目标造成的真实伤害。 */
    private static final int RELOAD_TRUE_DAMAGE = 4;
    /** 换弹瞬移的搜索半径（格）。 */
    private static final double RELOAD_SEARCH_RADIUS = 8.0;
    /** 瞬移落点与目标之间的距离（"身后 1 格"）。 */
    private static final double RELOAD_BEHIND_DISTANCE = 1.0;
    /** 子弹寿命（tick），到点销毁，避免无限飞行。 */
    private static final int BULLET_MAX_LIVING_TIME = 20;
    /** 子弹每 tick 前进的步数（步长 = 1 格，因此每 tick 最多 3 格）。 */
    private static final int BULLET_STEPS_PER_TICK = 3;
    /** 命中判定半径（格）：圆心距小于它即算命中。 */
    private static final double BULLET_HIT_RADIUS = 0.6;
    /** 弹道粒子：每一步画几个点（纯视觉）。 */
    private static final int PARTICLES_PER_STEP = 5;
    /** "弹夹空 + 有能量则自动换弹"的检查间隔（tick）。 */
    private static final int RELOAD_AUTO_CHECK_INTERVAL = 20;



    // ───────── 状态 ─────────

    /** 当前弹夹剩余子弹数。 */
    private int currentBulletCount;
    /** 飞行中的子弹（本组件私有；实例销毁时必须全部清除，见 {@link #stop()}）。 */
    private final List<BulletData> bullets = new ArrayList<>();
    /** 自动换弹检查的节拍计数。 */
    private int autoReloadTick;

    /**
     * **待办**：换弹完成（冷却走完）那一刻要执行的瞬移 + 真伤。
     *
     * <p>瞬移必须发生在换弹完成之后（换弹期间不动位置），
     * 而"完成"由冷却到期表达，因此到期检测在 {@link #update()} 里，执行体存在这里。
     * <p>{@code null} = 没有待办（常态），每 tick 的检测只是一次空引用比较，零开销。
     */
    private PendingReload pendingReload;

    /** 换弹完成后的待办动作（{@code target} = 登记时锁定的目标 UUID）。 */
    private static final class PendingReload {
        private final UUID target;

        private PendingReload(UUID target) {
            this.target = target;
        }
    }

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private CangluHysteriaPassive hysteriaPassive;

    public CangluBlueIceRevolverSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends Skill.Specification<CangluBlueIceRevolverSkill> {

        public Specification(){
            super(Component.text("澜冰左轮"),
                    List.of(Component.text("苍鹭的左轮：右键射击，Q 换弹（消耗 8 能量并瞬移至最近敌人身后）")),
                    //冷却 = 0：本件是武器（靠弹夹节流），不是技能；若声明 600，
                    //   图标会按 30 秒冷却画，而射击实际不受它拦（本组件不调 startCooldown），
                    //   声明与行为会不一致。
                    0, 0, Material.CROSSBOW);
            //依赖 = 实取清单（`start()` 里的两个 get 调用点）
            requires(VitalsComponent.class).requires(EnergyComponent.class);
            //SanTE 不在本组件实取清单里，声明为"可选"（装配期不因它缺失而拒绝）
            requiresOptional(SanTEComponent.class);
        }

        @Override
        public CangluBlueIceRevolverSkill create(String id, ComponentServicesPort services){
            return new CangluBlueIceRevolverSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    protected void onAwake(){
        //本钩子由基类 `awake()` 调用（栏位登记已在基类里完成），不能也不需要调 super.awake()
        //只做不可见的初始化（契约：awake 不得产生玩家可见副作用）
        currentBulletCount = MAX_MAGAZINE_CAPACITY;
        autoReloadTick = 0;
    }

    @Override
    public void start(){
        //契约：依赖字段在 start() 里一次查好（基类不代查）
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        hysteriaPassive = svc().components().get(CangluHysteriaPassive.class);
    }

    /**
     * **实例销毁**：必须把还在飞的子弹全部移除 —— Marker 是真实实体，
     * 不清就会留在世界里（组件已死、再没人推它），造成实体泄漏。
     */
    @Override
    public void stop(){
        //丢弃换弹待办：实例已销毁，不该再瞬移/造成伤害（否则是"死后打人"）
        pendingReload = null;
        for(BulletData bullet : bullets){
            bullet.getMarker().remove();
        }
        bullets.clear();
    }

    // ───────── 施放（唯一入口）─────────

    @Override
    public void onCast(CastSignal signal) {
        //闸门：被眩晕 / 沉默时不许开枪 / 换弹（禁用态靠闸门表达，与其余技能同一纪律）
        if(!canUse()){
            return;
        }
        if(signal.trigger() == CastTrigger.DROP){
            //Q = 换弹
            reload();
            return;
        }
        if(signal.trigger() == CastTrigger.RIGHT_CLICK){
            shoot();
        }
    }

    /**
     * **射击**：弹夹里有子弹则打一发（播放枪声）；打空则尝试自动换弹
     * （这一步让"子弹打光后自动开始换弹"成立）。
     * <p>打空且能量不足时不换弹 —— 玩家之后可按 Q 手动重试。
     */
    private void shoot(){
        if(currentBulletCount <= 0){
            tryAutoReload();
            return;
        }
        currentBulletCount--;
        createBullet();

        Player self = svc().self().player();
        self.getWorld().playSound(self.getLocation(), Sound.ENTITY_ENDER_DRAGON_HURT, 1, 1.5f);
    }

    /**
     * **换弹**：能量足够才成立（不足则什么都不做，玩家可见反馈是"没反应"）。
     * <p>成立时按顺序：扣 8 能量 → 启动换弹冷却（图标进入普通技能的冷却形态）→ 弹夹回满
     * → 登记待办（换弹完成那一刻再瞬移到目标身后并造成 4 点真实伤害）。
     *
     * <p>瞬移发生在换弹【完成之后】：目标在登记时锁定（此刻最近的敌人），
     * 到期执行时才取它的当时位置 —— 中途找不到/走远/目标消失则只跳过瞬移与真伤，不影响换弹。
     * <p>找不到敌人也照样换弹（`pendingReload` 带 `null` 目标）——
     * 否则"附近没人时无法换弹"会很难用。
     */
    private void reload(){
        if(energy == null || !energy.tryConsume(RELOAD_ENERGY_CONSUMPTION)){
            return;
        }
        //换弹冷却：组件自己在"施放成功处"按声明值启动（框架不代启动），因此图标显示倒计时
        startCooldown(RELOAD_COOLDOWN_TICKS);
        currentBulletCount = MAX_MAGAZINE_CAPACITY;

        Player self = svc().self().player();
        Player nearest = self == null ? null : findNearestEnemy(self, RELOAD_SEARCH_RADIUS);
        //登记待办：目标在【此刻】锁定（可为 null = 附近没人，只换弹不瞬移）
        pendingReload = new PendingReload(nearest == null ? null : nearest.getUniqueId());

        if (self != null) {
            self.getWorld().playSound(self.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 1, 0.8f);
        }
    }

    /**
     * **冷却走完那一刻的收尾**（每 tick 检测）：执行 {@link #reload()} 登记的待办。
     *
     * <p>判定 = "有待办且已不在冷却中"；执行后立刻清空待办，因此恰好执行一次
     * （若被打断 —— 例如组件 {@code stop()} —— 待办随之丢弃，见 {@link #stop()}）。
     */
    private void finishPendingReload(){
        if(pendingReload == null || isCoolingDown()){
            return;
        }
        PendingReload pending = pendingReload;
        pendingReload = null;

        Player self = svc().self().player();
        if(self == null){
            return;
        }
        //声音属于换弹完成本身（与"有没有目标"无关），必须在所有 early-return 之前播；
        //  否则附近没人时换弹会【没声音】（那是另一码事，跟瞬移无关）
        SoundUtil.playGunReloadSound(self);

        if(pending.target == null){
            return;      //附近没人则只换弹：不瞬移、不真伤
        }
        Player target = Bukkit.getPlayer(pending.target);
        if(target == null || !target.isOnline() || target.isDead()){
            return;
        }

        //取【执行时】的位置：目标在这 2 秒里可能已经移动 —— 落点必须贴合它现在的朝向
        if(!target.getWorld().equals(self.getWorld())){
            return;
        }
        if(self.getLocation().distanceSquared(target.getLocation()) > RELOAD_SEARCH_RADIUS * RELOAD_SEARCH_RADIUS){
            return;      //走远了则只换弹，不瞬移
        }

        teleportBehind(self, target);
        if(vitals != null){
            vitals.trueDamage(target, self, RELOAD_TRUE_DAMAGE);
        }


    }

    /**
     * **自动换弹检查**（每 {@link #RELOAD_AUTO_CHECK_INTERVAL} tick 一次）：
     * 只有当"弹夹空且不在冷却中"时才尝试。
     *
     * <p>不判断"是否手持本左轮"：弹夹空就自动换弹（枪收在背包里、或正拿着别的东西时也一样）。
     * 代价 = 能量可能在玩家没留意时被扣掉 —— 这是有意的口径。
     * <p>能量是否够由 {@link #reload()} 自己判（唯一判定点），这里不重复查。
     */
    private void tryAutoReload(){
        if(currentBulletCount > 0 || energy == null || isCoolingDown()){
            return;
        }
        reload();
    }
    // ───────── 每 tick ─────────

    @Override
    public void update() {
        //换弹完成检测：冷却走完那一刻执行待办的瞬移 + 真伤（见 finishPendingReload）
        finishPendingReload();

        if(autoReloadTick++ >= RELOAD_AUTO_CHECK_INTERVAL){
            autoReloadTick = 0;
            tryAutoReload();
        }

        Iterator<BulletData> it = bullets.iterator();
        while(it.hasNext()){
            BulletData bullet = it.next();
            if(!advanceBullet(bullet)){
                //撞墙 / 命中敌人 / 超时则销毁（三种情况都在 advanceBullet 里判定）
                bullet.getMarker().remove();
                it.remove();
            }
        }
    }

    /**
     * **推进一颗子弹**；返回 {@code false} = 该销毁。
     *
     * <p>逐步（步长 1 格）推进，每一步都判定：
     * ① 超时（{@link #BULLET_MAX_LIVING_TIME}）则销毁；
     * ② 撞墙（脚下那格不可通行）则销毁；
     * ③ 命中敌对玩家（圆心距 < {@link #BULLET_HIT_RADIUS}）则造成物理伤害 + 击退，随后销毁。
     *
     * <p>逐步判定的理由：一 tick 走 3 格，若只在终点判定，会穿过薄墙与敌人（隧穿）。
     */
    private boolean advanceBullet(BulletData bullet){
        Marker marker = bullet.getMarker();
        if(marker == null || !marker.isValid()){
            return false;     //已被外部清除（例如世界卸载）则直接销毁
        }
        if(bullet.getLivingTime() >= BULLET_MAX_LIVING_TIME){
            return false;
        }
        bullet.addLivingTime(1);

        Player self = svc().self().player();
        for(int step = 0; step < BULLET_STEPS_PER_TICK; step++){
            Location next = marker.getLocation().add(bullet.getDirection());

            //② 撞墙
            if(!next.getBlock().isPassable()){
                return false;
            }

            //③ 命中敌人（只取敌对玩家；不含自己）
            Player victim = firstHostileIn(next);
            if(victim != null){
                vitals.physicalDamage(victim, self, BULLET_DAMAGE, BULLET_KNOCKBACK);
                hysteriaPassive.requestAddStackCount(1);
                return false;
            }

            //推进 + 画弹道
            marker.teleport(next);
            for(int i = 0; i < PARTICLES_PER_STEP; i++){
                next.getWorld().spawnParticle(Particle.END_ROD, next, 1, 0, 0, 0, 0);
            }
        }
        return true;
    }

    // ───────── 查敌 / 瞬移 ─────────

    /** 半径内最近的敌对玩家（无则 {@code null}）。 */
    private Player findNearestEnemy(Player self, double radius){
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for(Entity entity : self.getNearbyEntities(radius, radius, radius)){
            if(!(entity instanceof Player candidate) || candidate.equals(self)){
                continue;
            }
            if(!svc().roleInfo().isHostileTo(candidate.getUniqueId())){
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(self.getLocation());
            if(distance < best){
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /**
     * **瞬移到目标身后**（"身后" = 目标朝向的反方向一格）。
     * <p>落点若不可站立（墙里），保持原位不动 —— 宁可不瞬移，也不把玩家塞进方块。
     */
    private void teleportBehind(Player self, Player target){
        Vector behind = target.getLocation().getDirection().normalize().multiply(-RELOAD_BEHIND_DISTANCE);
        Location destination = target.getLocation().clone().add(behind);
        destination.setDirection(target.getLocation().getDirection());
        if(!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()){
            return;     //落点被占（含头部空间）则放弃瞬移
        }
        self.teleport(destination);
        self.getWorld().playSound(self.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1, 1.4f);
    }

    /** 该位置附近是否有敌对玩家（命中判定用）；有则返回最近的那一个。 */
    private Player firstHostileIn(Location location){
        Player self = svc().self().player();
        BoundingBox box = BoundingBox.of(location, BULLET_HIT_RADIUS, BULLET_HIT_RADIUS, BULLET_HIT_RADIUS);
        Player nearest = null;
        double best = Double.MAX_VALUE;
        for(Entity entity : location.getWorld().getNearbyEntities(box)){
            if(!(entity instanceof Player candidate) || candidate.equals(self) || candidate.isDead()){
                continue;
            }
            if(!svc().roleInfo().isHostileTo(candidate.getUniqueId())){
                continue;
            }
            double distance = candidate.getLocation().distanceSquared(location);
            if(distance < best){
                best = distance;
                nearest = candidate;
            }
        }
        return nearest;
    }

    /** 从枪口生成一颗子弹（方向 = 玩家视线方向）。 */
    private void createBullet(){
        Player self = svc().self().player();
        Location muzzle = self.getEyeLocation();
        Marker marker = (Marker) self.getWorld().spawnEntity(muzzle, EntityType.MARKER);
        bullets.add(new BulletData(marker, muzzle.getDirection().normalize(), 0));
    }

    // ───────── 物品外观（在基类三态画法之上加"剩余子弹"）─────────

    /**
     * **物品增强**：先取基类的完整画法（三态材质 · 名称着色 · 冷却 `x.xs` 倒计时 · 状态行 · lore ·
     * 识别键 PDC），再在名称末尾追加剩余子弹数。
     *
     * <p>不重写整套三态画法：那套逻辑（含写识别键这一步）是冻结面，
     * 复制一份会立刻产生"两处实现漂移"，因此这里只做取回 + 追加后缀，其余原样保留。
     *
     * <p>显示形态：`<基类名> (5/8)` —— 打空时整段变红（一眼看出需要换弹）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if(meta == null){
            return stack;
        }
        Component baseName = meta.displayName() != null ? meta.displayName() : getDisplayName();
        NamedTextColor ammoColor = currentBulletCount <= 0 ? NamedTextColor.RED : NamedTextColor.YELLOW;
        meta.displayName(baseName.append(Component
                .text(" (" + currentBulletCount + "/" + MAX_MAGAZINE_CAPACITY + ")")
                .color(ammoColor)));
        stack.setItemMeta(meta);
        return stack;
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕（STUN）时不许开枪 / 换弹 —— 与主武器侧口径一致。 */
    @Override
    protected boolean canUse() {
        BuffComponent buff = svc().components().get(BuffComponent.class);
        return buff == null || buff.canUseMainWeapon();
    }

    /** 本组件不参与能量维度（声明耗能 0），故回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    /** 飞行中的子弹数据（纯数据；销毁时由调用方负责 {@code marker.remove()}）。 */
    private static final class BulletData {
        private final Marker marker;
        private final Vector direction;
        private int livingTime;

        private BulletData(Marker marker, Vector direction, int livingTime){
            this.marker = marker;
            this.direction = direction;
            this.livingTime = livingTime;
        }

        public int getLivingTime() {
            return livingTime;
        }

        public void addLivingTime(int tick){
            this.livingTime += tick;
        }

        public Marker getMarker() {
            return marker;
        }

        public Vector getDirection() {
            return direction;
        }
    }
}
