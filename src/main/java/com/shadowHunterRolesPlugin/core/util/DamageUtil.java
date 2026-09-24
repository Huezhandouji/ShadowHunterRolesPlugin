package com.shadowHunterRolesPlugin.core.util;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
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

    public static UUID getLastDamagerUUID(LivingEntity player) {
        String uuidString = player.getPersistentDataContainer().get(LAST_DAMAGER_KEY, PersistentDataType.STRING);
        if (uuidString == null) return null;
        return UUID.fromString(uuidString);
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
