package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.platform.Task;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;

public class MeiqiheziCircleSlashSkill extends Skill {

    //O-4：前摇任务句柄化，stop 时取消（阶段 2 换成平台 Task）
    private Task castTask;


    public MeiqiheziCircleSlashSkill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(
                    Component.text("圆弧斩"),
                    Component.text("前摇1秒后对7m范围内所有敌人造成20真实伤害"),
                    200,
                    15,
                    Material.GOLD_INGOT
            );
        }

        @Override
        public MeiqiheziCircleSlashSkill create(String id, ComponentServices services){
            return new MeiqiheziCircleSlashSkill(id, services, this);
        }
    }

    /**
     * 批次②（B②-b-2）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 判定顺序（2026-09-18 调整）：**先判 `canCastSkill`** —— 不满足 → 直接返回（被禁用，不施放、不扣能量），
     * **再**做能量 `tryConsume` —— 不满足 → 直接返回（与旧路径一致、**不启冷却**）；
     * 冷却由本组件在施放成功处按声明值 **200** 启动；
     * 缓慢用 **5 参重载**（`ambient=true, particles=false` 逐字保真，R-1 方法族）；
     * 前摇任务改由 `svc().timers()` 创建（**登记进本组件资源表** ⇒ 角色清除时框架兜底取消）。
     * <p>阶段 8：返回类型改 {@code void}（旧的施放结果枚举已删，返回值无消费点 ⇒ 零行为变化）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!svc().buffs().canCastSkill()) return;
        if(!svc().energy().tryConsume(getEnergyCost())) return;

        //药水记账（O-7）：经端口施加，clear() 时只回收本系统施加的效果（标志位与旧写法逐字一致）
        svc().buffs().applyPotionEffect(PotionEffectType.SLOWNESS, 20, 2, true, false);

        Location loc = caster.getLocation();

        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);



        castTask = svc().timers().runLater(20L, new Runnable(){

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
                    if (!svc().factions().isHostile(victim)) continue;
                    svc().damage().trueDamage(victim, caster, 20);
                }

                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1f);
            }
        });
        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
    }

    @Override
    public void start() {
    }

    /**
     * 新基类（RoleComponent）的停止钩子（阶段 4 B②-c）：容器在 legacy 扇出之后、`cancelAllAndClear()`
     * **之前**广播 ⇒ 与旧 `LifecycleAware.stop(...)` 的行为等价（O-4 的前摇取消）；框架还会兜底取消本组件
     * 资源表内的任务（重复取消幂等）。
     */
    @Override
    public void stop() {
        if(castTask != null){
            castTask.cancel();
            castTask = null;
        }
    }

}
