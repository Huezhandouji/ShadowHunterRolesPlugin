package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;
import com.shadowHunterRolesPlugin.core.util.ParticleUtil;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.platform.Task;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TimerComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;

public class MeiqiheziCircleSlashSkill extends Skill {


    private BuffComponent buff;
    private EnergyComponent energy;
    private VitalsComponent vitals;
    private TimerComponent timer;

    //前摇任务句柄化：stop 时取消（平台 Task）
    private Task castTask;


    public MeiqiheziCircleSlashSkill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
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
            requires(BuffComponent.class).requires(EnergyComponent.class).requires(VitalsComponent.class).requires(TimerComponent.class);
        }

        @Override
        public MeiqiheziCircleSlashSkill create(String id, ComponentServices services){
            return new MeiqiheziCircleSlashSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        timer = svc().components().get(TimerComponent.class);
    }

    /**
     * 迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 判定顺序（2026-09-18 调整）：**先判 `canCastSkill`** —— 不满足 → 直接返回（被禁用，不施放、不扣能量），
     * **再**做能量 `tryConsume` —— 不满足 → 直接返回（与旧路径一致、**不启冷却**）；
     * 冷却由本组件在施放成功处按声明值 **200** 启动；
     * 缓慢用 **5 参重载**（`ambient=true, particles=false` 逐字保真）；
     * 前摇任务改由**计时组件**创建（不再经服务集端口、改为组件本身用，**请求者在首位**；
     * **登记进本组件资源表** ⇒ 角色清除时框架兜底取消）。
     * <p>返回类型改 {@code void}（施放结果枚举已删，返回值无消费点 ⇒ 零行为变化）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buff.canCastSkill()) return;
        if(!energy.tryConsume(getEnergyCost())) return;

 //药水记账：经端口施加，clear() 时只回收本系统施加的效果（标志位逐字一致）
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, 20, 2, true, false);

        Location loc = caster.getLocation();

        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);



        castTask = timer.runLater(this, 20L, new Runnable(){

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
                    if (!svc().roleInfo().isHostile(victim)) continue;
                    vitals.trueDamage(victim, caster, 20);
                }

                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1f);
            }
        });
        startCooldown();   //D1：组件自启冷却（框架不再代启动）
    }



    /**
     * 新基类（RoleComponent）的停止钩子：容器在 legacy 扇出之后、`cancelAllAndClear()`
     * **之前**广播 ⇒ 与旧 `LifecycleAware.stop(...)` 的行为等价（前摇取消）；框架还会兜底取消本组件
     * 资源表内的任务（重复取消幂等）。
     */
    @Override
    public void stop() {
        if(castTask != null){
            castTask.cancel();
            castTask = null;
        }
    }

    /**
     * **闸门放行？**（基类不再取 buff ⇒ 由本组件用**自己的字段**判）。
     */
    @Override
    protected boolean gateOpen(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**：本组件**声明耗能 15** ⇒ 必须给出真实能量（否则"能量不足"态不出现 ✗）；
 * 与既有实现**逐字等价**：同一个能量组件实例（注册表装配期后冻结、同类型实例唯一 ⇒ 字段引用与按需查找恒等 ✓）。
     */
    @Override
    protected int currentEnergy(){
        return energy.current();
    }

}
