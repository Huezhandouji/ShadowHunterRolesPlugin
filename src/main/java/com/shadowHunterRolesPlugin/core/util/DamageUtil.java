package com.shadowHunterRolesPlugin.core.util;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
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
     * 与 {@code listener/DamageTrackerListener}（原版玩家伤害：直接命中与玩家射出的弹射物都算）——
     * 因此它是唯一覆盖两侧的口径。
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
     * <p><b>已知边界（如实申报）</b>：那条键在玩家死亡时**不会被清** ⇒ 死在怪物 / 环境手里的那一次，
     * 读到的可能是本命里上一次的玩家伤害者（本读口不判记录是否过期，那归产品口径）。
     * 弹射物（箭矢等）这条曾是边界，写账侧已按 {@code Projectile#getShooter} 归属到射手补齐；
     * 发射器等非玩家射手仍不入账（口径同"怪物伤害不入账"）。
     * 本读口不做写账侧之外的补救（改了就等于动既有记账口径）。
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
        writeLastDamager(victim, damager);

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
     * 死亡信息也是爆炸那一条；PDC「最后伤害者」与清无敌帧的口径完全一致。
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
     * 死亡信息也是弹射物那一条；PDC「最后伤害者」与清无敌帧的口径完全一致。
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

    /** 在 PDC 中记录最后伤害者（物理 / 爆炸 / 弹射物三条路径共用同一口径）。 */
    private static void writeLastDamager(LivingEntity victim, LivingEntity damager){
        if(victim == null || damager == null || !(victim instanceof Player)) return;
        victim.getPersistentDataContainer().set(
                DamageUtil.LAST_DAMAGER_KEY,
                PersistentDataType.STRING,
                damager.getUniqueId().toString()
        );
    }

    /**
     * 按**指定伤害类型**结算：除伤害类型通道外，与物理伤害逐字同规
     * （PDC 记账 → 清无敌帧 → {@code victim.damage(amount, source)}）。
     * <p>为什么不用 {@code victim.damage(amount, damager)}：那个重载的原版伤害类型是通用攻击，
     * 保护附魔的伤害标签对不上；这里要的是"爆炸 / 弹射物"这两条原版伤害类型。
     */
    private static void applyTypedDamage(LivingEntity victim, LivingEntity damager, double amount,
                                         DamageSource source){
        if(victim == null || victim.isDead()) return;
        writeLastDamager(victim, damager);
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
