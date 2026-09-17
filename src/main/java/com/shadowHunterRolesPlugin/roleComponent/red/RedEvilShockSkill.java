package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

public class RedEvilShockSkill extends Skill{

    public RedEvilShockSkill() {
        super("red_evilShock_skill", Component.text("煞气震赫"),
                Component.text("对周围5格范围内的敌人造成3秒致盲和缓慢III，结算他们5层流血。恢复[红]的10点TE值"),
                120,0, Material.REDSTONE);
        markMigrated();
    }

    /**
     * 批次①（B①）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 触发条件/范围/持续时间/增幅/层数/音效均不变；`canCastSkill` 不满足时返回 {@link CastResult#NO_COOLDOWN}
     * （今天该路径直接 return、**不启冷却**）；冷却改为 {@link CastResult#SUCCEED}，由框架按声明值启动。
     */
    @Override
    public CastResult onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!svc().buffs().canCastSkill()) return CastResult.NO_COOLDOWN;

        RedBleedPassive bleed = getComponent(RedBleedPassive.class);
        for(Player p : caster.getLocation().getNearbyPlayers(5)){
            if(svc().factions().isHostile(p)){
                p.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(61, 1));
                p.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(61, 3));
                //结算5层流血：写账本的唯一公开入口（硬约束第 18 条前移）
                //O-9：流血被动未注册时直接跳过，不能让本技能抛 NPE
                if(bleed == null) continue;
                bleed.requestResolve(p.getUniqueId(), 5);
            }
        }
        svc().sante().gain(10);

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);

        return CastResult.SUCCEED;
    }
}
