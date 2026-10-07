package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 「艾尔伯特」被动之一：**锁定**。
 *
 * <h2>它是什么（需求逐条）</h2>
 * <ol>
 *   <li><b>「猎杀目标」标记的唯一持有者</b> —— 右键射击命中、技能2 区域标记都会写进本账本；
 *       无人机与本人都从本类读它；</li>
 *   <li><b>「零件」层数的唯一持有者</b> —— 近战每命中一次 +1 层，满
 *       {@value #PARTS_REQUIRED} 层由调用方部署一架高斯无人机后清零；</li>
 *   <li>给武器提供两条读口：{@link #isMarked(UUID)}（额外真伤）、
 *       {@link #mark(UUID)}（右键命中时打标）。</li>
 * </ol>
 *
 * <h2>★ 口径申报：为什么"击杀猎杀目标立刻部署"的订阅不在这里</h2>
 * 那条效果需要"击杀 → 部署"两步，而部署能力住在 {@link AlbertDroneSystem}。
 * 若本类反过来依赖它，就形成 <b>本类 ⇄ 无人机系统</b> 的双向依赖 ——
 * 框架的 {@code Role#verifyDependencies()} 会按<b>依赖环</b>拒绝整个角色装配
 * （装配期抛 {@code ComponentDependencyException}，角色根本不注册，且只在日志里出现一条 SEVERE）。
 *
 * <p>⇒ 本项目把「击杀订阅」放在<b>无人机系统</b>一侧（它本来就依赖 {@code VitalsComponent}），
 * 由它查本类的 {@link #isMarked(UUID)} 再决定是否追加部署。
 * <b>依赖方向因此是单向的</b>：无人机系统 → 本类。
 *
 * <h2>★ 口径申报：标记到期用"到期刻"而不是"剩余刻数"</h2>
 * 账本存 {@code UUID → 到期刻}（{@code Bukkit.getCurrentTick()} 口径）。
 * 用到期刻的原因：每次刷新标记只需"覆盖写一个绝对值"，不必读旧值做加法
 * ⇒ 同 tick 内多次打标的结果是确定的（不会因为读取顺序不同而叠加出不同剩余时长）。
 */
public class AlbertLockOnPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_passive_lockOn";

    /** 标记持续时间：20 秒 = {@value #MARK_DURATION_TICKS} 刻（需求原话"标记对方为'猎杀目标'20秒"）。 */
    public static final int MARK_DURATION_TICKS = 400;

    /** 积攒多少层「零件」自动部署一架高斯无人机（需求原话"满4层自动部署一架"）。 */
    public static final int PARTS_REQUIRED = 4;

    /** 对「猎杀目标」的攻击附加的真实伤害（需求原话"附加1点真实伤害"）。 */
    public static final double MARK_TRUE_DAMAGE_BONUS = 1d;

    /**
     * 无人机对「猎杀目标」额外造成的**灵魂伤害**（需求原话"对猎杀目标额外造成1点灵魂伤害"）。
     *
     * <p>★ 口径：插件 {@code DamageKind} 只有 {@code PHYSICAL} / {@code TRUE}，**没有"灵魂"这一类型**
     * ⇒ 与工程既有口径一致，灵魂伤害取 {@code TRUE}（无视护甲）。
     * 先例：{@code HunterGrudgeMainWeapon} 的"10 点灵魂伤害"、罪棘的"魔法伤害"。
     */
    public static final double MARK_SOUL_BONUS_DAMAGE = 1d;

    /** 标记的视觉刷新间隔（刻）：每 10 刻画一次（省开销，观感上仍连续）。 */
    private static final int MARK_VISUAL_INTERVAL_TICKS = 10;

    /** {@code UUID → 到期刻}。 */
    private final Map<UUID, Integer> marks = new HashMap<>();

    /** 当前「零件」层数（{@code 0..PARTS_REQUIRED-1}；满层会被调用方消费并清零）。 */
    private int parts;

    /** 视觉刷新节拍。 */
    private int visualTick;

    public AlbertLockOnPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符。**不声明任何 albert 内部依赖** ——
     * 它是依赖图的<b>叶子</b>，这样才能让别的组件单方向依赖它（见类注释的口径申报）。
     */
    public static final class Specification extends PassiveSkill.Specification<AlbertLockOnPassive> {

        public Specification() {
            super(Component.text("锁定"),
                    List.of(
                            Component.text("无人机优先攻击范围内的「猎杀目标」"),
                            Component.text("你对「猎杀目标」的攻击附加 1 点真实伤害"),
                            Component.text("击杀「猎杀目标」时立刻部署一架无人机（不超过上限）"),
                            Component.text("近战每命中一次积攒 1 层「零件」，满 4 层自动部署一架高斯无人机")
                    ));
        }

        @Override
        public AlbertLockOnPassive create(String id, ComponentServicesPort services) {
            return new AlbertLockOnPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        marks.clear();
        parts = 0;
        visualTick = 0;
    }

    @Override
    public void stop() {
        marks.clear();
        parts = 0;
        visualTick = 0;
    }

    // ───────── 每刻：清理到期标记 + 画标记 ─────────

    @Override
    public void update() {
        if (marks.isEmpty()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        marks.entrySet().removeIf(entry -> isMarkExpired(now, entry.getValue()));

        visualTick++;
        if (visualTick % MARK_VISUAL_INTERVAL_TICKS != 0) {
            return;
        }
        // 视觉：在标记目标头顶画橙红菱形（跨世界 / 已下线 / 已死亡 ⇒ 静默跳过）
        for (UUID id : new HashSet<>(marks.keySet())) {
            Player target = Bukkit.getPlayer(id);
            if (target == null || !target.isOnline() || target.isDead() || target.getWorld() == null) {
                continue;
            }
            Location at = target.getLocation().clone().add(0d, 2.35d, 0d);
            AlbertVfx.markAlbertHuntTarget(target.getWorld(), at);
        }
    }

    // ───────── 标记账本 ─────────

    /**
     * 打上 / 刷新「猎杀目标」标记（20 秒）。
     *
     * @param target 目标玩家；{@code null} ⇒ 忽略
     * @return 本次是否为"新标记"（此前未被标记时返回 {@code true}）——
     *         调用方据此决定要不要播音效 / 台词，避免连续命中时"每次都喊一遍"
     */
    public boolean mark(Player target) {
        if (target == null) {
            return false;
        }
        int until = Bukkit.getCurrentTick() + MARK_DURATION_TICKS;
        Integer previous = marks.put(target.getUniqueId(), until);
        return previous == null || isMarkExpired(Bukkit.getCurrentTick(), previous);
    }

    /** 该目标当前是否是「猎杀目标」（已到期 / 未知 ⇒ {@code false}）。 */
    public boolean isMarked(UUID target) {
        if (target == null) {
            return false;
        }
        Integer until = marks.get(target);
        return until != null && !isMarkExpired(Bukkit.getCurrentTick(), until);
    }

    /** 消费掉一个标记（取走并移除；重复消费第二次返回 {@code false}）。 */
    public boolean consumeMark(UUID target) {
        if (target == null) {
            return false;
        }
        Integer until = marks.remove(target);
        return until != null && !isMarkExpired(Bukkit.getCurrentTick(), until);
    }

    /** 清空全部标记（换角色 / 收工）。 */
    public void clearMarks() {
        marks.clear();
    }

    /** 当前被标记的人数（读口，供探针 / HUD）。 */
    public int markCount() {
        return marks.size();
    }

    // ───────── 零件层数 ─────────

    /** 当前「零件」层数（{@code 0..PARTS_REQUIRED-1}）。 */
    public int parts() {
        return parts;
    }

    /**
     * 近战每命中一次调一次：层数 +1。
     *
     * @return {@code true} = 这一层**刚好装满** ⇒ 调用方应立刻部署一架无人机，
     *         并且本方法已经把层数<b>清零</b>（部署失败也不会"卡在满层"）
     */
    public boolean addPart() {
        parts++;
        if (parts >= PARTS_REQUIRED) {
            parts = 0;
            return true;
        }
        return false;
    }

    /** 直接清空零件层数（换角色 / 收工）。 */
    public void clearParts() {
        parts = 0;
    }

    // ───────── 纯函数（离线可测）─────────

    /**
     * 标记是否已过期（**纯函数**）。
     *
     * @param nowTick   当前刻
     * @param untilTick 到期刻
     * @return {@code true} = 已过期
     */
    public static boolean isMarkExpired(int nowTick, int untilTick) {
        return nowTick >= untilTick;
    }

    /**
     * 再命中一次之后的零件层数（**纯函数**，含满层回绕）。
     *
     * @param current 当前层数
     * @return 下一层数（{@code 0..PARTS_REQUIRED-1}）
     */
    public static int partsAfterHit(int current) {
        int next = current + 1;
        return next >= PARTS_REQUIRED ? 0 : Math.max(0, next);
    }

    /** 描述符引用的图标材质（被动不占热键栏，此值仅用于角色列表展示）。 */
    static Material displayIcon() {
        return Material.IRON_INGOT;
    }
}
