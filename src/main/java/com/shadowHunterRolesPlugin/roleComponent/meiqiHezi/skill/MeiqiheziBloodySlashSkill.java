package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;


public class MeiqiheziBloodySlashSkill extends Skill {

    //O-3：技能任务句柄化，stop 时取消，避免角色被清除后仍结算伤害（阶段 2 换成平台 Task）
    private Task attackTask;


    public MeiqiheziBloodySlashSkill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(
                    Component.text("血腥连斩"),
                    Component.text("向前移动4格并斩击，重复四次"),
                    160,
                    8,
                    Material.IRON_INGOT
            );
        }

        @Override
        public MeiqiheziBloodySlashSkill create(String id, ComponentServices services){
            return new MeiqiheziBloodySlashSkill(id, services, this);
        }
    }


    /**
     * 右击施放（新管道；批次⑧-b/B⑧-b 迁移）。与旧 `onRightClick(Player, RoleInstance)` **逐条等价**：
     * 能量不足 / 被禁用时**直接返回且不启动冷却**（旧代码即如此）⇒ 早返回跳过后续语句；
     * 否则扣能量 `8`、以 `0L` 初始延迟 / `2L` 周期启动前摇任务（**登记进本组件资源表**，角色清除时由框架兜底取消
     * ⇒ 原 `isValid()` 守卫不需要）、四周 `4` 格内敌对目标各受 `14` 点物理伤害、粒子/音效逐字不变；
     * 冷却由本组件在施放成功处按声明值 **160** 启动。
     * <p>阶段 8：返回类型改 {@code void}（旧的施放结果枚举已删，返回值无消费点）。
     */
    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();

        if (svc().energy().current() < getEnergyCost()) return;
        if(!svc().buffs().canCastSkill()) return;
        svc().energy().tryConsume(getEnergyCost());

        attackTask = svc().timers().runRepeating(0L, 2L, new Runnable() {

            private int count = 0;
            private final Player player = caster;

            @Override
            public void run() {
                if (count >= 4) {
                    attackTask.cancel();
                    return;
                }
                count += 1;

                Location loc = player.getLocation();
                Vector dir = loc.getDirection();
                player.setVelocity(dir.clone().setY(0).normalize().multiply(1.5));

                Collection<? extends Player> victims = loc.getNearbyPlayers(4);

                for (Player victim : victims) {
                    if (!svc().factions().isHostile(victim)) continue;
                    victim.setNoDamageTicks(0);
                    svc().damage().physicalDamage(victim, player, 14);
                }

                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1f);
                Location particleLoc = loc.clone().add(0, 1, 0);
                particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 30, 0.5, 0.5, 0.5, 0, dust);

                loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1f);
            }

        });

        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
    }

    @Override
    public void stop() {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
        }
    }

}
