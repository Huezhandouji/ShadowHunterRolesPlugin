package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 「猎手」被动之一：**猎杀**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>标记 30 格内**最近的敌人**；</li>
 *   <li>敌人被<b>初次标记</b>时，身上冒出一簇<b>灵魂沙粒子</b>，并在其<b>坐标高度
 *       {@value HunterVfx#DIAMOND_HEIGHT} 格</b>生成一个<b>紫色菱形边框</b>；</li>
 *   <li>标记的**同时**获得 <b>10 秒速度 III</b>；</li>
 *   <li>普攻被标记的敌人会造成**额外 10 点灵魂伤害**；</li>
 *   <li>标记 CD = **6 秒**。</li>
 * </ol>
 *
 * <h2>★ 标记模型（2026-10-02 用户明确，取代原"单一标记"实现）</h2>
 * <ol>
 *   <li><b>粒子只在"初次标记"那一刻冒出，不持续冒</b> —— 旧实现每 2 刻重画一次被否掉；</li>
 *   <li><b>场上可以同时存在多个被标记的敌人</b>（标记挂在敌人身上，不是"只有一个"）；</li>
 *   <li><b>标记 CD 刷新时，场上所有有标记的敌人身上的标记也会刷新</b>
 *       —— 即"重放一次标记的粒子 + 重画菱形边框"，但<b>不重播铁砧音</b>
 *       （铁砧音专属"初次"，否则每 6 秒一次铁砧噪音）；</li>
 *   <li><b>敌人被普通攻击后就会失去这个标记</b> —— 标记由那次普攻**消费掉**
 *       （消费方 = {@link HunterGrudgeMainWeapon}，它调 {@link #consumeMark(UUID)}）；</li>
 *   <li>标记在该敌人死亡 / 掉线 / 换世界时**自动清理**。</li>
 * </ol>
 *
 * <h2>★ 口径申报：标记不设距离上限，但"新标记"仍限 30 格</h2>
 * "30 格"是**挑新目标**的搜索半径（需求原话"标记 30 格内最近的敌人"）。
 * 已经挂上的标记**不因对方跑远而消失** —— 否则"场上所有有标记的敌人身上的标记也会刷新"
 * 这句话在对方走出 30 格后就没有对象了。标记的失效条件只有三条：
 * 被杀 / 掉线 / 换世界（外加被普攻消费）。
 *
 * <h2>★ 2026-10-02 用户新增：**被标记的敌人死亡 ⇒ 即刻刷新标记 CD**</h2>
 * 需求原话：「标记的敌人死亡后会**即刻刷新标记 CD，立刻标记其它敌人**」。
 * <p>实现：{@link #update()} 的"清理失效标记"一步现在会回报**是否有被标记的敌人死亡**
 * （见 {@link #purgeInvalidMarks()}）—— 有就把 {@code markReadyAtTick} 清成当前刻，
 * 于是同一刻的 CD 节点立即放行、当场挑一个新目标标记。
 * <p>★ 只认**死亡**，不认"掉线 / 换世界"（见 {@link #isDeadNow(UUID)}）：
 * 后两者不算"猎物被猎杀"，白送一次重新标记会让"卡掉线"变成刷标记的手段。
 *
 * <h2>★ 口径申报：速度 III 绑在"CD 节点"上</h2>
 * 需求："标记的同时获得 10 秒速度 III"。CD（6 秒）短于速度时长（10 秒），
 * 因此只要场上持续有标记，速度 III 事实上常驻 —— 这是**按字面实现的结果**，
 * 且与"CD 刷新会刷新所有标记"同源（每次刷新都是一次"标记"）
 * ⇒ 本实现把"给速度"绑在**每一次 CD 节点**（而不是"仅新目标出现时"）。
 * <p>若你要改成"只在新目标出现时给速度"，把 {@link #grantSpeed()} 的调用移进
 * {@link #markNewTarget} 即可（一行）。
 *
 * <h2>★ 口径申报：灵魂伤害取 TRUE（真伤）</h2>
 * 插件 {@code DamageKind} 只有 {@code PHYSICAL} / {@code TRUE}，**没有"灵魂"这一类型**
 * ⇒ 需求里的"灵魂伤害"按"无视护甲的伤害"取 {@code TRUE}，与工程既有口径一致
 * （先例：罪棘的"魔法伤害"同样取 {@code TRUE}）。该类伤害由主武器侧结算，
 * 本类只声明常量 {@link #SOUL_BONUS_DAMAGE}。
 *
 * <h2>不在本类里验的（如实申报）</h2>
 * 粒子 / 音效 / 速度 / 索敌四处都要求真实玩家与真实世界。
 * 离线单测里 {@code self} 为 {@code null} ⇒ {@link #update()} 第一行就安静返回，
 * 因此只有纯函数 {@link #withinMarkRange(double)} 与 <b>标记集合的增删语义</b>
 * （{@link #markedCount()} / {@link #isMarked(UUID)} / {@link #consumeMark(UUID)}）可离线验。
 */
public class HunterPreyPassive extends PassiveSkill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "hunter_passive_prey";

    /** **挑新目标**的搜索半径（格）—— 需求原话"30格内"。 */
    public static final double MARK_RANGE = 30d;

    /** 标记冷却（刻）—— 需求原话"标记CD-6"（6 秒）。 */
    public static final int MARK_COOLDOWN_TICKS = 120;

    /** 标记时给的速度时长（刻）—— 需求原话"10秒"。 */
    private static final int SPEED_DURATION_TICKS = 200;

    /** 速度 III 的增幅值（{@code 速度 N ⇒ amplifier N-1}）。 */
    private static final int SPEED_AMPLIFIER = 2;

    /** 普攻被标记敌人的**额外**灵魂伤害点数（由主武器侧结算；见类注释的口径申报）。 */
    public static final int SOUL_BONUS_DAMAGE = 10;

    // ───────── 实例状态：标记集合（可多名并存，插入序稳定以便遍历可复现）─────────

    /** 场上有标记的敌人 UUID（LinkedHashSet ⇒ 遍历顺序稳定，便于排障时读数可复现）。 */
    private final Set<UUID> markedIds = new LinkedHashSet<>();

    /** 下一次允许"标记新目标"的刻（{@code Bukkit.getCurrentTick()} 口径）。 */
    private int markReadyAtTick;

    private BuffComponent buff;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HunterPreyPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符：显示名「猎杀」+ 逐条对应需求的描述。 */
    public static final class Specification extends PassiveSkill.Specification<HunterPreyPassive> {

        public Specification() {
            super(Component.text("猎杀"), List.of(
                    Component.text("标记 30 格内最近的敌人"),
                    Component.text("初次标记时目标身上冒出灵魂沙粒子与紫色菱形边框"),
                    Component.text("标记时获得 10 秒速度 III"),
                    Component.text("标记冷却 6 秒；刷新时场上所有标记一并刷新"),
                    Component.text("普攻被标记者额外造成 10 点灵魂伤害，并消耗掉该标记")
            ));
            requires(BuffComponent.class).requires(FactionComponent.class);
        }

        @Override
        public HunterPreyPassive create(String id, ComponentServicesPort services) {
            return new HunterPreyPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 依赖只在 {@code start()} 取（硬约定：不在构造器 / {@code awake()} 里取）。 */
    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        markedIds.clear();
        //首帧即可标记（不额外等一个 CD）
        markReadyAtTick = Bukkit.getCurrentTick();
    }

    /** 停止生效：与 {@link #start()} 严格对称（标记随实例销毁而消失）。 */
    @Override
    public void stop() {
        markedIds.clear();
        markReadyAtTick = 0;
        buff = null;
    }

    // ───────── 每 tick ─────────

    /**
     * 每 tick 只做一件事：**清理失效标记 + 到点走一次 CD 节点**。
     *
     * <p>★ 粒子**不再每刻/每 2 刻重画**：只在"初次标记"与"CD 刷新"两个事件点各放一次
     * （见类注释的口径申报第 1、3 条）。
     */
    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            //拿不到玩家时不清标记：玩家换个实例回来（重新装角色）时标记集合本就随实例重建
            return;
        }

        //① 清理失效标记（死亡 / 掉线 / 换世界）
        //  ★ 需求："**标记的敌人死亡后会即刻刷新标记 CD，立刻标记其它敌人**"
        //    ⇒ 只要有**被标记的敌人死了**，就把 CD 清零 ⇒ 下面的 CD 节点本刻就放行、立刻去标记别人。
        //    只认"死亡"不认"掉线 / 换世界"：那两种不算"猎物被猎杀"，不该白送一次重新标记。
        int now = Bukkit.getCurrentTick();
        if (purgeInvalidMarks()) {
            markReadyAtTick = now;
        }

        //② CD 节点
        if (now < markReadyAtTick) {
            return;
        }
        markReadyAtTick = now + MARK_COOLDOWN_TICKS;

        //  ②-1 挑一名新的（没被标记过的）最近敌人 ⇒ 初次标记（铁砧音 + 粒子）
        Player nearest = nearestUnmarkedHostile(owner);
        boolean gainedNew = nearest != null && markNewTarget(nearest);

        //  ②-2 刷新场上所有标记（重放粒子，不重播铁砧音）
        int refreshed = refreshAllMarks();

        //  ②-3 有标记在场 ⇒ 续速度 III（见类注释的口径申报）
        if (gainedNew || refreshed > 0) {
            grantSpeed();
        }
    }

    // ───────── 对外读口 / 写口（主武器侧消费）─────────

    /** 某个 UUID 当前是否被标记。 */
    public boolean isMarked(UUID id) {
        return id != null && markedIds.contains(id);
    }

    /**
     * **消费掉某敌人的标记**（普攻命中时由 {@link HunterGrudgeMainWeapon} 调用）。
     *
     * <p>语义 = "取走并移除"：返回 {@code true} 表示这一击打掉了一个真实存在的标记
     * （调用方据此追加额外伤害 / 更多粒子 / 2 倍速音效）。
     * 同一个标记只会被消费一次 —— 第二次调用返回 {@code false}。
     *
     * @param id 被命中的敌人
     * @return 是否真的消费掉了一个标记
     */
    public boolean consumeMark(UUID id) {
        return id != null && markedIds.remove(id);
    }

    /** 当前场上被标记的敌人数（排障 / 离线单测读口）。 */
    public int markedCount() {
        return markedIds.size();
    }

    // ───────── 内部：标记 / 刷新 / 清理 ─────────

    /**
     * **初次标记**一名敌人：加入集合、播铁砧落地音、放一次灵魂粒子 + 紫色菱形边框。
     *
     * @return 是否真的新增（已在集合中 ⇒ {@code false}）
     */
    private boolean markNewTarget(Player target) {
        if (!markedIds.add(target.getUniqueId())) {
            return false;
        }
        drawMarkVfx(target);
        World world = target.getWorld();
        if (world != null) {
            HunterSound.anvilLandHunterPreyMarkSound(world, target.getLocation());
        }
        return true;
    }

    /**
     * **刷新场上所有标记**：对每个仍有效的被标记者重放一次"灵魂粒子 + 紫色菱形边框"。
     *
     * <p>★ 刻意**不**重播铁砧音：那是"初次"专属（见 {@link HunterSound#anvilLandHunterPreyMarkSound}）。
     *
     * @return 实际刷新了几个
     */
    private int refreshAllMarks() {
        int refreshed = 0;
        for (UUID id : new ArrayList<>(markedIds)) {
            Player target = validTarget(id);
            if (target == null) {
                continue;
            }
            drawMarkVfx(target);
            refreshed++;
        }
        return refreshed;
    }

    /**
     * 清掉已失效（死亡 / 掉线 / 换世界）的标记。
     *
     * @return 本次是否有**被标记的敌人死亡** —— 被调用方用来"即刻刷新标记 CD"（需求）。
     *         与"掉线 / 换世界"区分开：那两种不算猎物被猎杀。
     */
    private boolean purgeInvalidMarks() {
        boolean anyDied = false;
        for (UUID id : new ArrayList<>(markedIds)) {
            if (validTarget(id) != null) {
                continue;
            }
            if (isDeadNow(id)) {
                anyDied = true;
            }
            markedIds.remove(id);
        }
        return anyDied;
    }

    /**
     * 该 UUID 此刻是否**躺着**（死亡 / 血量 ≤ 0）。
     *
     * <p>与 {@link #isTrackable(Player)} 的区别：那个把"掉线"也算不可用；
     * 本方法只认**死亡** —— 需求只对"猎物死亡"给"即刻刷新标记 CD"这条待遇。
     * 离线时 {@code Bukkit.getPlayer(...)} 回 {@code null} ⇒ 本方法回 {@code false}（按掉线处理）。
     */
    private static boolean isDeadNow(UUID id) {
        Player player = id == null ? null : Bukkit.getPlayer(id);
        return player != null && (player.isDead() || player.getHealth() <= 0d);
    }

    /** 在目标身上画「一簇灵魂沙粒子 + 脚底上方 {@value HunterVfx#DIAMOND_HEIGHT} 格的紫色菱形边框」。 */
    private void drawMarkVfx(Player target) {
        World world = target.getWorld();
        if (world == null) {
            return;
        }
        Location feet = target.getLocation();
        HunterVfx.soulClusterHunterPreyMark(world, feet.clone().add(0d, 1.0d, 0d));
        HunterVfx.purpleDiamondHunterPreyMark(world, feet);
    }

    /** 给自己续 10 秒速度 III（"标记的同时获得"；走 buff 组件这条"只作用自己"的正确口）。 */
    private void grantSpeed() {
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS, SPEED_AMPLIFIER);
        }
    }

    // ───────── 内部：索敌 ─────────

    /**
     * 30 格内**最近的、当前还没被标记的**敌对玩家；没有则回 {@code null}。
     *
     * <p>为什么排除"已被标记的"：CD 节点的语义是"标记**新的**敌人"，
     * 已有标记由 {@link #refreshAllMarks()} 负责刷新。否则每 6 秒都会把同一个最近的人
     * 当"新目标"，铁砧音会变成周期性噪音，且集合永远涨不上去。
     *
     * <p>判敌走全角色唯一真值点 {@code svc().components().get(FactionComponent.class).isHostileTo(uuid)}；
     * 仍然显式跳过自己（既省一次查表，也让"自己不会被标记"在代码里看得见）。
     */
    private Player nearestUnmarkedHostile(Player owner) {
        Location center = owner.getLocation();
        Player best = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (Player candidate : center.getNearbyPlayers(MARK_RANGE)) {
            if (candidate == null || candidate.equals(owner)) {
                continue;
            }
            if (!isTrackable(candidate)) {
                continue;
            }
            if (markedIds.contains(candidate.getUniqueId())) {
                continue;
            }
            Location candidateLocation = candidate.getLocation();
            if (!sameWorld(candidateLocation, center)) {
                continue;
            }
            if (!svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            double distanceSquared = candidateLocation.distanceSquared(center);
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                best = candidate;
            }
        }
        return best;
    }

    /** 按 UUID 取出"仍有效"的玩家：在线 + 未死 + 血量 > 0。 */
    private Player validTarget(UUID id) {
        Player target = Bukkit.getPlayer(id);
        return isTrackable(target) ? target : null;
    }

    /** 一个玩家是否还值得被标记（在线 / 未死 / 有血量）。{@code null} ⇒ false。 */
    private static boolean isTrackable(Player player) {
        return player != null && player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** 两个坐标是否在同一个世界（{@code distance} 跨世界会抛，必须先判）。 */
    private static boolean sameWorld(Location a, Location b) {
        return a != null && b != null && a.getWorld() != null && a.getWorld().equals(b.getWorld());
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * **某距离是否落在"挑新目标"的搜索范围内**（纯函数 ⇒ 可离线穷举）。
     *
     * <p>边界口径：{@code 0} 计入（贴身也算在范围内）；负距离视为不合法 ⇒ {@code false}；
     * {@value #MARK_RANGE} 本身**计入**（"30 格内"含 30）。
     * <p>★ 只用于"挑新目标"；已有标记不因距离超标而消失（见类注释的口径申报）。
     *
     * @param distance 与施法者的距离（格）
     */
    public static boolean withinMarkRange(double distance) {
        return distance >= 0d && distance <= MARK_RANGE;
    }
}
