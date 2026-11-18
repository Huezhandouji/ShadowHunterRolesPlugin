package com.shadowHunterRolesPlugin.core.util;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

public class DamageUtil {

 //小于这个水平距离平方时不计算击退，避免向量归一化得到NaN
    private static final double MIN_KNOCKBACK_LENGTH_SQUARED = 1.0E-6;

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

 //无来源伤害原版不会记 killer —— 伤害方是玩家时补写击杀归属。必须在 damage() 之前：
 //死亡事件在 damage() 内同步触发，写晚了 PlayerKilledHookListener 读不到
        setKillerIfPlayer(victim, damager);

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

 //爆炸伤害（2026-10-31 新增）
    /**
     * 爆炸伤害：走原版 {@code minecraft:explosion} 伤害类型。
     * <p>与 {@link #dealtPhysicalDamage} 的差别只有伤害类型通道：护甲与**爆炸保护**按原版标签减伤，
     * 死亡信息也是爆炸那一条；「击杀者随伤害源入账（原版结算自会记 killer）」与清无敌帧的口径完全一致。
     */
    public static void dealtExplosionDamage(LivingEntity victim, LivingEntity damager, double amount){
        applyTypedDamage(victim, damager, amount, explosionSource(damager));
    }
 //带有击退的重载
    public static void dealtExplosionDamage(LivingEntity victim, LivingEntity damager, double amount, double knockbackStrength){
        if(victim == null || victim.isDead()) return;
        dealtExplosionDamage(victim, damager, amount);
        if(damager == null) return;
        applyKnockback(victim, damager.getLocation(), knockbackStrength);
    }

 //弹射物伤害（2026-10-31 新增）
    /**
     * 弹射物伤害：走原版 {@code minecraft:arrow} 伤害类型。
     * <p>与 {@link #dealtPhysicalDamage} 的差别只有伤害类型通道：护甲与**弹射物保护**按原版标签减伤，
     * 死亡信息也是弹射物那一条；「击杀者随伤害源入账（原版结算自会记 killer）」与清无敌帧的口径完全一致。
     */
    public static void dealtProjectileDamage(LivingEntity victim, LivingEntity damager, double amount){
        applyTypedDamage(victim, damager, amount, projectileSource(damager));
    }
 //带有击退的重载
    public static void dealtProjectileDamage(LivingEntity victim, LivingEntity damager, double amount, double knockbackStrength){
        if(victim == null || victim.isDead()) return;
        dealtProjectileDamage(victim, damager, amount);
        if(damager == null) return;
        applyKnockback(victim, damager.getLocation(), knockbackStrength);
    }

    /** 无来源伤害原版不会记 killer：伤害方是玩家时用 {@code setKiller} 补写（读口 = 原版 {@code getKiller()}）。
     *  只在伤害方是玩家时写 —— setKiller 的入参就是 Player；null / 非玩家不碰（传 null 会清掉原版已记的归属）。 */
    private static void setKillerIfPlayer(LivingEntity victim, LivingEntity damager){
        if(!(victim instanceof Player playerVictim) || !(damager instanceof Player playerDamager)) return;
        playerVictim.setKiller(playerDamager);
    }

    /**
     * 按**指定伤害类型**结算：除伤害类型通道外，与物理伤害逐字同规
     * （清无敌帧 → {@code victim.damage(amount, source)}）。
     * <p>击杀归属随伤害源入账：施加者挂在 source 的 causing / direct 上，原版结算自会记 killer，
     * 不需要物理伤害那步手动补写。
     * <p>为什么不用 {@code victim.damage(amount, damager)}：那个重载的原版伤害类型是通用攻击，
     * 保护附魔的伤害标签对不上；这里要的是"爆炸 / 弹射物"这两条原版伤害类型。
     */
    private static void applyTypedDamage(LivingEntity victim, LivingEntity damager, double amount,
                                         DamageSource source){
        if(victim == null || victim.isDead()) return;
        victim.setNoDamageTicks(0);
        victim.damage(amount, source);

    }

    /**
     * 爆炸伤害类型：{@code minecraft:explosion} + 施加者。
     * <p><b>★ causingEntity 与 directEntity 必须成对设置</b>：Paper 的 {@code CraftDamageSourceBuilder#build()}
     * 在"只给 causing、没给 direct"时直接抛 {@code IllegalArgumentException
     * ("Direct entity must be set if causing entity is set")}（已按 server jar 字节码核对，2026-10-31）。
     * 本入口没有"是哪一次爆炸"这个入参 ⇒ 两个位置都填施加者。
     * <p>不带 {@code damageLocation}：本入口是"结算一次声明好的爆炸伤害"，不是"复制一次真实爆炸"；
     * 位置缺省不影响爆炸保护的标签减伤。
     */
    private static DamageSource explosionSource(LivingEntity damager){
        DamageSource.Builder builder = DamageSource.builder(DamageType.EXPLOSION);
        if(damager != null){
            builder.withCausingEntity(damager);
            builder.withDirectEntity(damager);
        }
        return builder.build();
    }

    /**
     * 弹射物伤害类型：{@code minecraft:arrow} + 施加者。
     * <p>同上：Paper 要求 causing / direct 成对，本入口没有"是哪一支弹射物"入参 ⇒ 两个位置都填施加者
     * （标签减伤只看伤害类型，不受影响；真实弹射物归属由调用方另行处理）。
     */
    private static DamageSource projectileSource(LivingEntity damager){
        DamageSource.Builder builder = DamageSource.builder(DamageType.ARROW);
        if(damager != null){
            builder.withCausingEntity(damager);
            builder.withDirectEntity(damager);
        }
        return builder.build();
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
