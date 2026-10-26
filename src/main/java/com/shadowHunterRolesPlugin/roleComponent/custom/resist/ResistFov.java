package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * **FOV 脉冲小工具**（「命运的征服」命中时约 0.1 秒的视野放大）。
 *
 * <h2>为什么不是直接改 FOV</h2>
 * 原版协议里没有"设置视野角"的数据包，Bukkit / Paper 也没有 FOV 接口
 * （已核对 1.21.11 的 {@code Player} 公开面：无 {@code fov} 相关方法）。
 * 客户端 FOV 的**唯一服务端可控输入**是移动速度属性
 * （{@code Attribute.MOVEMENT_SPEED} 参与 {@code getFieldOfViewModifier()} 的计算）。
 *
 * <p>因此本工具的做法 = 给移动速度挂一个 **{@code ADD_SCALAR} 的临时修饰符**，
 * 持续极短（默认 2 刻 = 0.1 秒）后摘除：
 * <ul>
 *   <li>只动属性、不动药水 ⇒ 不会顶掉角色被动 / 技能上的速度效果；</li>
 *   <li>修饰符用固定键，重复触发先摘后加（工程口径：不去重会叠加）；</li>
 *   <li>需要有人每刻推进到期回收 —— 由被动 {@link ResistStrugglePassive} 调 {@link #tick}。</li>
 * </ul>
 *
 * <p><b>如实申报</b>：速度属性同时会让人在 0.1 秒里略微加速（视野与移速是同一个属性，
 * 无法只改其中一个）；这是"无客户端 Mod 前提下"能做到的最接近 FOV 放大的效果。
 */
public final class ResistFov {

    private ResistFov() {
    }

    /** 修饰符键（固定 ⇒ 可重复摘除）。 */
    private static final NamespacedKey PULSE_KEY = KeyFactory.Registry.of("resist_fov_pulse");

    /** 视野放大幅度：+85% 移速 ⇒ 肉眼可见的视野扩张。 */
    private static final double PULSE_AMOUNT = 0.85d;

    private static final AttributeModifier PULSE = new AttributeModifier(
            PULSE_KEY, PULSE_AMOUNT, AttributeModifier.Operation.ADD_SCALAR);

    /** 到期刻（键 = 玩家 UUID）。 */
    private static final Map<UUID, Integer> UNTIL = new ConcurrentHashMap<>();

    /**
     * 触发一次 FOV 脉冲。
     *
     * @param ticks 持续刻数（需求 0.1 秒 ⇒ 传 2）
     */
    public static void punch(Player player, int nowTick, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        speed.removeModifier(PULSE);
        speed.addModifier(PULSE);
        UNTIL.put(player.getUniqueId(), nowTick + ticks);
    }

    /** 每刻推进：到期就摘掉修饰符（幂等）。 */
    public static void tick(Player player, int nowTick) {
        if (player == null) {
            return;
        }
        UUID id = player.getUniqueId();
        Integer until = UNTIL.get(id);
        if (until == null) {
            return;
        }
        if (nowTick >= until) {
            clear(player);
        }
    }

    /** 立刻摘除（死亡 / 离线 / stop / 换角色）。 */
    public static void clear(Player player) {
        if (player == null) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(PULSE);
        }
        UNTIL.remove(player.getUniqueId());
    }

    /** 当前是否处于脉冲中（诊断读口 / 单测用）。 */
    public static boolean isPulsing(Player player) {
        return player != null && UNTIL.containsKey(player.getUniqueId());
    }

    /** 脉冲力度（只读常量，单测用）。 */
    public static double pulseAmount() {
        return PULSE_AMOUNT;
    }
}
