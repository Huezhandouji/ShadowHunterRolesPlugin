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
        clearHitInterval(victim);
        victim.damage(amount);
 //★ 扣血**之后**再清一次：`damage()` 会把无敌帧重新设回 20 刻 ⇒ 只清"扣血前"那一次的话，
 //  间隔会在同一刻内立刻长回来，下一次（别人打的）伤害照旧被吞。
        clearHitInterval(victim);
    }

    /**
     * **取消受击间隔（全局口径）** —— 让"连着打"不再被原版的受击无敌帧吃掉。
     *
     * <p><b>为什么必须有这一步</b>：原版 {@code LivingEntity#hurt} 在结算后会写入
     * {@code noDamageTicks}（默认 20 刻 = 0.5 秒）；这段时间内**后续任何伤害**（包括别的玩家、
     * 别的组件造成的）都会被吞掉或按"只补差额"处理。这是**每个实体自身**的状态，
     * 所以**不是**改一处配置就能全局生效的，只能在每次结算伤害时清掉。
     *
     * <p><b>三处一起做才叫"取消"</b>：
     * <ol>
     *   <li>{@code setMaximumNoDamageTicks(0)}：把"受伤后该免疫多久"这个**上限本身**设为 0
     *       （部分版本 {@code hurt()} 会读它来回填 {@code noDamageTicks}）；</li>
     *   <li>{@code setNoDamageTicks(0)}：清掉**当前**残留的免疫帧 ⇒ 本次伤害必定足额结算；</li>
     *   <li>扣血**之后**再清一次（见 {@link #dealtPhysicalDamage} 的两个调用点）。</li>
     * </ol>
     *
     * <p><b>边界（如实申报）</b>：本方法只作用于**经本插件伤害入口结算过的实体**。
     * 纯原版来源（例如两个玩家互相普攻、且都不带本插件的武器）若在两次本插件伤害之间发生，
     * 那一下仍可能被它自己刚写回的免疫帧挡住 —— 要连那种情况也一并取消，需要每刻扫描实体
     * （成本与收益不成比例），故不做。
     */
    public static void clearHitInterval(LivingEntity victim){
        if(victim == null) return;
        victim.setMaximumNoDamageTicks(0);
        victim.setNoDamageTicks(0);
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
