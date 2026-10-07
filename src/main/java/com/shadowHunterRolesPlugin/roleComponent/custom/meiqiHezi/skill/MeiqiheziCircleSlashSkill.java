package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;
import com.shadowHunterRolesPlugin.core.util.ParticleUtil;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import java.util.List;

public class MeiqiheziCircleSlashSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "meiqihezi_skill_circle_slash";


    private BuffComponent buff;
    private EnergyComponent energy;
    private VitalsComponent vitals;
    private TaskComponent timer;

    //前摇任务句柄：stop 时取消（平台 Task）
    private ScheduledHandle castTask;


    public MeiqiheziCircleSlashSkill(String id, ComponentServicesPort services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 冷却 / 耗能 / 图标由这里声明，
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification<MeiqiheziCircleSlashSkill> {

        public Specification(){
            super(
                    Component.text("圆弧斩"),
                    List.of(Component.text("前摇1秒后对7m范围内所有敌人造成20真实伤害")),
                    200,
                    15,
                    Material.GOLD_INGOT
            );
            requires(BuffComponent.class).requires(EnergyComponent.class).requires(VitalsComponent.class).requires(TaskComponent.class);
            //范围伤害逐个受害者判敌 ⇒ 读阵营组件；缺它则本技能不索敌，装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public MeiqiheziCircleSlashSkill create(String id, ComponentServicesPort services){
            return new MeiqiheziCircleSlashSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        timer = svc().components().get(TaskComponent.class);
    }

    /**
     * 右击施放，判定顺序：**先判 `canCastSkill`** —— 不满足则直接返回（被禁用，不施放、不扣能量）；
     * **再**做能量 `tryConsume` —— 不满足则直接返回（不启冷却）；
     * 冷却由本组件在施放成功处按声明值 200 启动；缓慢用 5 参重载（`ambient=true, particles=false`）；
     * 前摇任务由计时组件创建（请求者在首位，**登记进本组件资源表** ⇒ 角色清除时框架兜底取消）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buff.canCastSkill()) return;
        if(!energy.tryConsume(getEnergyCost())) return;

 //药水记账：经 Buff 组件施加，clear() 时只回收本系统施加的效果（标志位逐字一致）
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, 20, 2, true, false);

        Location loc = caster.getLocation();

        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);



        castTask = timer.addScheduleLater(this, 20L, new Runnable(){

            @Override
            public void run() {
                if(caster.isDead() || !caster.isOnline()){
                    castTask.cancel();
                    return;
                }

                Location loc = caster.getLocation();
                ParticleBuilder pb = Particle.DUST.builder()
                        .count(1)
                        .color(Color.RED)
                        .offset(0, 0, 0);

                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                ParticleUtil.drawCircle(loc.clone().add(0, 1, 0), 7, pb, 80);

                Collection<? extends Player> victims = loc.getNearbyPlayers(7);

                for(Player victim : victims){
                    if (!svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) continue;
                    vitals.trueDamage(victim, caster, 20);
                }

                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1f);
            }
        });
        startCooldown();   //组件自启冷却（框架不再代启动）
    }



    /**
     * 停止钩子：容器在 `cancelAllAndClear()` 之前广播，因此本方法能先取消前摇任务；
     * 框架还会兜底取消本组件资源表内的任务（重复取消幂等）。
     */
    @Override
    public void stop() {
        if(castTask != null){
            castTask.cancel();
            castTask = null;
        }
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**：本组件声明耗能 15，因此必须给出真实能量（否则"能量不足"态不出现）；
     * 取的是同一个能量组件实例（注册表装配期后冻结、同类型实例唯一，因此字段引用与按需查找恒等）。
     */
    @Override
    protected int currentEnergy(){
        return energy.current();
    }

}
