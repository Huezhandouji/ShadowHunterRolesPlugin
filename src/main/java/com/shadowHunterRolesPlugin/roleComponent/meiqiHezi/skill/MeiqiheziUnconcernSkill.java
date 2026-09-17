package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;


public class MeiqiheziUnconcernSkill extends Skill {

    public MeiqiheziUnconcernSkill(String id, ComponentServices services){
        super(
                id,
                services,
                Component.text("漫不经心"),
                Component.text("获得2秒速度5"),
                100,
                0,
                Material.BLAZE_POWDER
        );
    }

    /**
     * 批次②（B②）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 药水经 {@code svc().buffs().applyPotionEffect(...)} 施加 ⇒ **与旧写法同一条已记账路径**
     * （效果类型 SPEED / 时长 40 / 增幅 4 逐字不变；`new PotionEffect(type,40,4,false,true)` 与
     * `type.createEffect(40,4)` 的 ambient=false、particles=true 一致）。
     * `canCastSkill` 不满足时返回 {@link CastResult#NO_COOLDOWN}（今天该路径直接 return、**不启冷却**）；
     * 冷却改为 {@link CastResult#SUCCEED}，由框架按声明值（100）启动。
     */
    @Override
    public CastResult onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!svc().buffs().canCastSkill()) return CastResult.NO_COOLDOWN;

        //药水记账（O-7）：经端口施加，clear() 时只回收本系统施加的效果
        svc().buffs().applyPotionEffect(PotionEffectType.SPEED, 40, 4);

        caster.getWorld().playSound(
                caster.getLocation(),
                Sound.ITEM_TRIDENT_RIPTIDE_1,
                1f,
                1f
        );

        caster.getWorld().spawnParticle(Particle.EXPLOSION, caster.getLocation(), 1);

        return CastResult.SUCCEED;
    }

}
