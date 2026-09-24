package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;


public class MeiqiheziUnconcernSkill extends Skill {

    private BuffComponent buff;

    public MeiqiheziUnconcernSkill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(
                    Component.text("漫不经心"),
                    Component.text("获得2秒速度5"),
                    100,
                    0,
                    Material.BLAZE_POWDER
            );
        }

        @Override
        public MeiqiheziUnconcernSkill create(String id, ComponentServices services){
            return new MeiqiheziUnconcernSkill(id, services, this);
        }
    }

    @Override
    public void start(){
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * 批次②（B②）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 药水经 {@code buffComponent().applyPotionEffect(...)} 施加 ⇒ **与旧写法同一条已记账路径**
     * （效果类型 SPEED / 时长 40 / 增幅 4 逐字不变；`new PotionEffect(type,40,4,false,true)` 与
     * `type.createEffect(40,4)` 的 ambient=false、particles=true 一致）。
     * `canCastSkill` 不满足时**直接返回**（该路径**不启冷却**）；
     * 冷却由本组件在施放成功处按声明值（100）启动。
     * <p>阶段 8：返回类型改 {@code void}（旧的施放结果枚举已删，返回值无消费点）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buffComponent().canCastSkill()) return;

        //药水记账（O-7）：经端口施加，clear() 时只回收本系统施加的效果
        buffComponent().applyPotionEffect(PotionEffectType.SPEED, 40, 4);

        caster.getWorld().playSound(
                caster.getLocation(),
                Sound.ITEM_TRIDENT_RIPTIDE_1,
                1f,
                1f
        );

        caster.getWorld().spawnParticle(Particle.EXPLOSION, caster.getLocation(), 1);

        startCooldown();   //D1：组件自启冷却（框架不再代启动）
    }

}
