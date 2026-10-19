package com.shadowHunterRolesPlugin.roleComponent.custom.lin;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * **「铁血镇压者」的攻击力削弱账本**（林体系的跨实例真值持有者）。
 *
 * <h2>为什么需要它</h2>
 * 需求："击中敌人…同时使其主武器攻击力下降 2 持续 5 秒"。
 * 被削弱的是**另一个玩家**（很可能是别的角色实例，甚至没装角色），
 * 而框架的组件查找面（{@code ComponentLookupPort}）<b>只覆盖本角色实例内</b>
 * ⇒ "谁被削到什么时候"这件事必须住在一处**玩家无关**的地方。
 *
 * <h2>为什么用属性修饰符，而不是虚弱药水</h2>
 * 需求字面是"**主武器攻击力**下降 2" ⇒ 应当是**攻击力数值**的减益，
 * 而不是"所有近战 −4"（虚弱 I 的语义）。
 * 用 {@link Attribute#ATTACK_DAMAGE} 的 {@code ADD_NUMBER(-2)} 修饰符：
 * <ul>
 *   <li>数值精确 = −2（与需求一字不差）；</li>
 *   <li>对原版近战同样生效（玩家换手/徒手也照样被削）—— 这是"攻击力"的本义；</li>
 *   <li>到期只需 {@code removeModifier(手部的那个键)}，无副作用残留。</li>
 * </ul>
 *
 * <h2>★★ 键必须"每玩家各不相同"</h2>
 * {@code AttributeModifier} 在**同一个玩家**身上按 {@code NamespacedKey} 去重，
 * 但**不同玩家之间互不影响**（Bukkit 按玩家实例持有属性表）。
 * 因此键可以用同一个名字 —— 但它必须**带归属前缀**（{@link #MODIFIER_KEY_PREFIX}），
 * 以免与别人的修饰符（生命上限键、攻速键）撞名后被 {@code removeModifier} 误删。
 *
 * <h2>口径申报（如实）</h2>
 * <ul>
 *   <li><b>不重复叠加，只刷新时长</b>：需求说"下降 2 持续 5 秒"，没说可叠层
 *       ⇒ 重复命中只把到期刻往后推（{@link #apply}），不变 −4 / −6 ——
 *       否则高频普攻（0.2 秒一击）会在 5 秒内叠到 −50，直接把人打废。</li>
 *   <li><b>到期不做定时任务</b>：本类不注册任何任务。到期由
 *       {@link #sweepExpired()} 在组件的每刻 {@code update()} 里顺手清理
 *       （惰性 + 每刻主动各留一半，删干净的时机可控）。</li>
 *   <li><b>死亡 / 离线也要摘掉</b>：玩家死亡时 Bukkit 会清空属性修饰符，
 *       离线时实体不存在 ⇒ 本类在 {@link #sweepExpired()} 里对这两类
 *       <b>直接从账本移除</b>（不尝试 removeModifier，避免对离线实体操作）。</li>
 *   <li>本类<b>不做阵营判定</b>：是否该削由组件用 {@code FactionComponent} 判完再调本类。</li>
 * </ul>
 */
public final class LinAttackDebuff {

    private LinAttackDebuff() {
    }

    /** 削弱幅度（需求原话"攻击力下降 2"）。 */
    public static final double REDUCTION = 2.0d;

    /** 削弱持续（刻）：5 秒（需求原话"持续5秒"）。 */
    public static final int DURATION_TICKS = 100;

    /** 修饰符键名前缀（归属标记：只删自己挂的，不碰生命上限 / 攻速那些键）。 */
    private static final String MODIFIER_KEY_PREFIX = "lin_iron_blood_weaken_";

    /** 账本：键 = 被削玩家的 UUID，值 = 到期刻（绝对刻，便于与 Bukkit 同口径比较）。 */
    private static final Map<UUID, Integer> UNTIL_TICK = new ConcurrentHashMap<>();

    /**
     * **施加（或刷新）削弱**。
     *
     * <p>重复命中只刷新到期刻，不叠层 —— 见类注释口径申报。返回是否真的改动了目标属性。
     *
     * @param target      被削的玩家（{@code null} ⇒ 无操作）
     * @param currentTick 当前刻（由组件传 {@code Bukkit.getCurrentTick()}；便于离线单测注入）
     */
    public static boolean apply(Player target, int currentTick) {
        if (target == null || !target.isOnline()) {
            return false;
        }
        int until = currentTick + DURATION_TICKS;
        UNTIL_TICK.put(target.getUniqueId(), until);
        writeModifier(target);
        return true;
    }

    /** 把 −{@value #REDUCTION} 写进目标的攻击力属性（先摘后加 ⇒ 幂等）。 */
    private static void writeModifier(Player target) {
        var attribute = target.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }
        AttributeModifier modifier = new AttributeModifier(
                keyFor(target), -REDUCTION, AttributeModifier.Operation.ADD_NUMBER);
        // 先摘后加：同一玩家被反复命中时不会叠加成 −4 / −6 / −8
        attribute.removeModifier(modifier);
        attribute.addModifier(modifier);
    }

    /**
     * **本刻该不该清理**（**纯函数** ⇒ 可离线穷举边界）。
     *
     * @param storedUntil  账本里记的到期刻（{@code null} / 无记录由调用方过滤）
     * @param currentTick  当前刻
     * @param online       目标是否在线
     * @param alive        目标是否存活（死亡时 Bukkit 已清属性 ⇒ 这里只负责清账本）
     * @return {@code true} ⇒ 应当从账本移除
     */
    public static boolean shouldExpire(Integer storedUntil, int currentTick, boolean online, boolean alive) {
        if (storedUntil == null) {
            return true;
        }
        if (!online || !alive) {
            return true;
        }
        return currentTick >= storedUntil;
    }

    /**
     * **清理所有已到期的削弱**（由组件的 {@code update()} 每刻调用）。
     *
     * <p>对"还活着且在线"的目标：摘掉属性修饰符；
     * 对"死亡 / 离线"的目标：只从账本删除（实体已不存在，不再碰属性）。
     */
    public static void sweepExpired(int currentTick) {
        for (Map.Entry<UUID, Integer> entry : UNTIL_TICK.entrySet()) {
            UUID id = entry.getKey();
            Player target = org.bukkit.Bukkit.getPlayer(id);
            boolean online = target != null && target.isOnline();
            boolean alive = online && !target.isDead();
            if (!shouldExpire(entry.getValue(), currentTick, online, alive)) {
                continue;
            }
            if (online) {
                removeModifierQuietly(target);
            }
            UNTIL_TICK.remove(id);
        }
    }

    /** 摘掉本账本挂的攻击力修饰符（不抛：属性可能已被死亡清空）。 */
    private static void removeModifierQuietly(Player target) {
        var attribute = target.getAttribute(Attribute.ATTACK_DAMAGE);
        if (attribute == null) {
            return;
        }
        attribute.removeModifier(keyFor(target));
    }

    /** 目标当前的削弱剩余刻（无削弱 ⇒ 0）。 */
    public static int remainingTicks(UUID target, int currentTick) {
        if (target == null) {
            return 0;
        }
        Integer until = UNTIL_TICK.get(target);
        if (until == null) {
            return 0;
        }
        return Math.max(0, until - currentTick);
    }

    /** 是否正被削弱。 */
    public static boolean isWeakened(UUID target, int currentTick) {
        return remainingTicks(target, currentTick) > 0;
    }

    /** 账本条数（探针 / 单测读口）。 */
    public static int size() {
        return UNTIL_TICK.size();
    }

    /** **仅测试用**：清空账本（离线单测之间互不串扰）。 */
    public static void resetForTest() {
        UNTIL_TICK.clear();
    }

    /**
     * 该玩家身上的修饰符键。
     *
     * <p>用固定的名字（不带 UUID）是安全的：Bukkit 的属性修饰符表是**按玩家实例**持有的，
     * 同名键在不同玩家身上互不影响。前缀只用来避免与生命上限键 / 攻速键撞名。
     */
    private static org.bukkit.NamespacedKey keyFor(Player target) {
        return new org.bukkit.NamespacedKey("shadowhunterroles", MODIFIER_KEY_PREFIX + target.getUniqueId());
    }
}
