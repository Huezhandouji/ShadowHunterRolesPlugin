package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;


public class DamageUtil {

    public static final NamespacedKey LAST_DAMAGER_KEY = new NamespacedKey(
            ShadowHunterRolesPlugin.getInstance(),
            "last_damager"
    );

    //真伤
    public static void dealtTrueDamage(LivingEntity victim, LivingEntity damager, double amount){
        if(victim == null || victim.isDead()) return;
        if(damager != null && victim instanceof Player){
            DamageUtil.dealtPhysicalDamage(victim, damager, 0);
        }
        victim.setHealth(Math.max(0, victim.getHealth() - amount));
    }
    //带有击退的重载，简化后续代码
    public static void dealtTrueDamage(LivingEntity victim, LivingEntity damager, double amount, double knockbackStrength){
        if(victim == null || victim.isDead()) return;
        dealtTrueDamage(victim, damager, amount);
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
        victim.damage(amount);
    }
    //带有击退的重载
    public static void dealtPhysicalDamage(LivingEntity victim, LivingEntity damager, double amount, double knockbackStrength){
        if(victim == null || victim.isDead()) return;
        dealtPhysicalDamage(victim, damager, amount);
        applyKnockback(victim, damager.getLocation(), knockbackStrength);

    }

    public static void applyKnockback(LivingEntity target, Location source, double strength){
        if(source == null) return;
        Vector dir = target.getLocation().toVector().subtract(source.toVector());
        dir.setY(0);
        dir.normalize();
        dir.multiply(strength);
        dir.setY(strength * 0.2);

        target.setVelocity(dir);
    }

}
