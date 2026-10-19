package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「抗争」的**跨组件状态**（角色包内静态真值持有者）。
 *
 * <h2>为什么要有它</h2>
 * 三个主动技能施放期间要**同时锁住主武器与其它技能**，但组件之间只能单向依赖
 * （主武器 / 技能都依赖被动；若主武器再依赖某个技能就会成环 ⇒ 整个角色静默消失）。
 * 因此把"锁"放在**不依赖任何组件**的一小块静态真值里：技能写、所有人读。
 *
 * <p>与 {@code TekTruth} / {@code SayakaImbalance} 同一形态：静态持有者放在角色自己的包里，
 * 不碰 {@code core/}；条目按到期刻自动失效（读时清理），玩家退出/换角色时由被动
 * {@link #clear(Player)} 收尾。
 */
public final class ResistState {

    private ResistState() {
    }

    /** 施法锁：键 = 玩家 UUID，值 = 到期刻（{@code Bukkit.getCurrentTick()} 口径）。 */
    private static final Map<UUID, Integer> LOCK_UNTIL = new ConcurrentHashMap<>();

    /** 增益保护：这段时间内被动不得覆盖抗性 / 速度（技能自己上的抗性 III / 速度 III 优先）。 */
    private static final Map<UUID, Integer> BUFF_PROTECT_UNTIL = new ConcurrentHashMap<>();

    /**
     * **上锁**：{@code ticks} 刻内主武器与其它技能不可用（技能自身的施放不受影响 ——
     * 它在入口处通过后才上锁）。
     */
    public static void lock(Player player, int nowTick, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        LOCK_UNTIL.put(player.getUniqueId(), nowTick + ticks);
    }

    /** 当前是否处于施法锁中（过期条目顺手清掉）。 */
    public static boolean isLocked(Player player, int nowTick) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        Integer until = LOCK_UNTIL.get(id);
        if (until == null) {
            return false;
        }
        if (nowTick >= until) {
            LOCK_UNTIL.remove(id, until);
            return false;
        }
        return true;
    }

    /** 解除施法锁（技能收尾 / 死亡 / stop）。 */
    public static void unlock(Player player) {
        if (player != null) {
            LOCK_UNTIL.remove(player.getUniqueId());
        }
    }

    /** 保护本技能自己上的增益，别被被动按阈值刷新覆盖。 */
    public static void protectBuffs(Player player, int nowTick, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        BUFF_PROTECT_UNTIL.put(player.getUniqueId(), nowTick + ticks);
    }

    /** 增益是否处于保护期（过期条目顺手清掉）。 */
    public static boolean buffsProtected(Player player, int nowTick) {
        if (player == null) {
            return false;
        }
        UUID id = player.getUniqueId();
        Integer until = BUFF_PROTECT_UNTIL.get(id);
        if (until == null) {
            return false;
        }
        if (nowTick >= until) {
            BUFF_PROTECT_UNTIL.remove(id, until);
            return false;
        }
        return true;
    }

    /** 清掉该玩家的全部条目（死亡 / 离线 / stop / 换角色）。 */
    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        LOCK_UNTIL.remove(id);
        BUFF_PROTECT_UNTIL.remove(id);
    }

    /** 当前锁条目数（诊断读口 / 离线单测用）。 */
    public static int lockCount() {
        return LOCK_UNTIL.size();
    }
}
