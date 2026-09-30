package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import java.util.List;


public class MeiqiheziUnconcernSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "meiqihezi_skill_unconcern";

    private BuffComponent buff;

    public MeiqiheziUnconcernSkill(String id, ComponentServicesPort services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 冷却 / 耗能 / 图标由这里声明，
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification<MeiqiheziUnconcernSkill> {

        public Specification(){
            super(
                    Component.text("漫不经心"),
                    List.of(Component.text("获得2秒速度5")),
                    100,
                    0,
                    Material.BLAZE_POWDER
            );
            requires(BuffComponent.class);
        }

        @Override
        public MeiqiheziUnconcernSkill create(String id, ComponentServicesPort services){
            return new MeiqiheziUnconcernSkill(id, services, this);
        }
    }

    @Override
    public void start(){
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * 右击施放：药水经本组件的 buff 字段（{@code buff.applyPotionEffect(...)}）施加，
     * 与既有写法同一条已记账路径（效果类型 SPEED / 时长 40 / 增幅 4；
     * `type.createEffect(40,4)` 的 ambient=false、particles=true 与
     * `new PotionEffect(type,40,4,false,true)` 一致）。
     * `canCastSkill` 不满足时直接返回（不启冷却）；冷却由本组件在施放成功处按声明值（100）启动。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buff.canCastSkill()) return;

        //药水记账：经 Buff 组件施加，clear() 时只回收本系统施加的效果
        buff.applyPotionEffect(PotionEffectType.SPEED, 40, 4);

        caster.getWorld().playSound(
                caster.getLocation(),
                Sound.ITEM_TRIDENT_RIPTIDE_1,
                1f,
                1f
        );

        caster.getWorld().spawnParticle(Particle.EXPLOSION, caster.getLocation(), 1);

        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**：本组件不参与能量维度（声明耗能 0），因此返回声明值；
     * 能量组件的值被 clamp 到 `[0, max]`，故 `current() < 0` 恒假。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }

}
