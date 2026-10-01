package com.shadowHunterRolesPlugin.core.util;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.UUID;


public class DamageUtil {

 //小于这个水平距离平方时不计算击退，避免向量归一化得到NaN
    private static final double MIN_KNOCKBACK_LENGTH_SQUARED = 1.0E-6;

    public static final NamespacedKey LAST_DAMAGER_KEY = KeyFactory.Registry.of(
            "last_damager"
    );

    public static UUID getLastDamagerUUID(Player player) {
        String uuidString = player.getPersistentDataContainer().get(LAST_DAMAGER_KEY, PersistentDataType.STRING);
        if (uuidString == null) return null;
        return UUID.fromString(uuidString);
    }

    /**
     * 取"最后伤害者"那个玩家（本系统的**击杀归属**读口）。
     *
     * <p><b>为什么不看原版 {@code LivingEntity#getKiller()}</b>：本系统的伤害有相当一部分绕过原版
     * 事件（真伤是直接 {@code setHealth}、物伤会重设无伤刻），原版途径在那些路径上给不出击杀者；
     * 而 {@link #LAST_DAMAGER_KEY} 由两条路各写一次 —— {@link #dealtPhysicalDamage}（本系统施加的伤害）
     * 与 {@code listener/DamageTrackerListener}（原版玩家对玩家的伤害）—— 因此它是唯一覆盖两侧的口径。
     * 平台侧同名读口 = {@code RoleAPI#getLastDamagerUuid(Player)} 与 {@code RoleAPI#getLastDamager(Player)}
     * （本方法只多做一步"解析成玩家"）。
     *
     * <p><b>"解析不到"一律回 {@code null}</b>：从未被玩家伤害过 / 那条 UUID 对应的玩家已离线 ——
     * 调用方按"无击杀者"处理，不抛异常（击杀归属天然是"可能没有"的）。
     *
     * <p><b>本方法不判自杀</b>：自己打自己（例：{@code damage(self(), …)}）会把自己也写进那条 PDC，
     * 因此 {@code killer == victim} 是可能的返回值 —— "自伤致死不算法击杀"这条口径归调用方
     * （与外部消费方 {@code SHDFGamePlugin} 的处置同源）。
     *
     * <p><b>已知边界（如实申报）</b>：那条键在玩家死亡时**不会被清**，且只有"玩家直接打玩家"
     * 才会更新它 ⇒ ① 死在怪物 / 环境手里的那一次，读到的可能是本命里上一次的玩家伤害者；
     * ② 弹射物（箭矢）命中时 {@code DamageTrackerListener} 看到的是弹射物而非射手，故弓弩击杀不入账。
     * 两件事都在"写账侧"，本读口不擅自补救（改了就等于动既有记账口径）。
     *
     * @param victim 被伤害 / 被击杀的**玩家**；{@code null} 回 {@code null}
     * @return 最后伤害者（在线玩家）；无记录 / 已离线回 {@code null}
     */
    public static Player getLastDamager(Player victim) {
        if (victim == null) return null;
        UUID damager = getLastDamagerUUID(victim);
        if (damager == null) return null;
        return Bukkit.getPlayer(damager);
    }

 //真伤
    public static void dealtTrueDamage(LivingEntity victim, LivingEntity damager, double amount){
        if(victim == null || victim.isDead()) return;
        if(damager != null && victim instanceof Player player){
            GameMode gm = player.getGameMode();
            if(gm == GameMode.CREATIVE || gm == GameMode.SPECTATOR) return;

            DamageUtil.dealtPhysicalDamage(victim, damager, 0.0001);
        }
        victim.setHealth(Math.max(0, victim.getHealth() - amount));
    }
 //带有击退的重载，简化后续代码
    public static void dealtTrueDamage(LivingEntity victim, LivingEntity damager, double amount, double knockbackStrength){
        if(victim == null || victim.isDead()) return;
        dealtTrueDamage(victim, damager, amount);
        if(damager == null) return;
        applyKnockback(victim, damager.getLocation(), knockbackStrength);
    }

 //物理伤害
    public static void dealtPhysicalDamage(LivingEntity victim, LivingEntity damager, double amount){
        if(victim == null || victim.isDead()) return;

 //在pdc中记录最后伤害者
        if(damager != null && victim instanceof Player) {
            victim.getPersistentDataContainer().set(
                    DamageUtil.LAST_DAMAGER_KEY,
                    PersistentDataType.STRING,
                    damager.getUniqueId().toString()
            );
        }
 //造成伤害，绕过被玩家伤害事件
        victim.setNoDamageTicks(0);
        victim.damage(amount);
    }
 //带有击退的重载
    public static void dealtPhysicalDamage(LivingEntity victim, LivingEntity damager, double amount, double knockbackStrength){
        if(victim == null || victim.isDead()) return;
        dealtPhysicalDamage(victim, damager, amount);
        if(damager == null) return;
        applyKnockback(victim, damager.getLocation(), knockbackStrength);

    }

    public static void applyKnockback(LivingEntity target, Location source, double strength){
        if(target == null || source == null) return;
        if(target.getWorld() == null || source.getWorld() == null) return;
        if(!target.getWorld().equals(source.getWorld())) return;

        Vector dir = target.getLocation().toVector().subtract(source.toVector());
        dir.setY(0);

 //两个实体站在同一个方块里时水平向量长度为0，normalize() 会算出 NaN，setVelocity 会抛 "x not finite"
        if(dir.lengthSquared() < MIN_KNOCKBACK_LENGTH_SQUARED) return;

        dir.normalize();
        dir.multiply(strength);
        dir.setY(strength * 0.2);

        target.setVelocity(dir);
    }

}
