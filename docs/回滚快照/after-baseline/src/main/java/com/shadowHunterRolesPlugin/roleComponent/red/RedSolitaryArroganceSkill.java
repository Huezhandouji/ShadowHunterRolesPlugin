package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.roleComponent.SkillUtil;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Consumer;

public class RedSolitaryArroganceSkill extends Skill {

    public RedSolitaryArroganceSkill() {
        super("red_solitaryArrogance_skill", Component.text("孤妄自赏"),
                Component.text("连续捅击四次。每次造成伤害，如果命中敌人，回复生命"),
                200,0, Material.FERMENTED_SPIDER_EYE);
    }

    @Override
    public void onRightClick(Player caster, RoleInstance instance){
        if(!instance.getBuffManager().canCastSkill()) return;
        ShadowHunterRolesPlugin.getInstance().getServer().getGlobalRegionScheduler().runAtFixedRate(
                ShadowHunterRolesPlugin.getInstance(),
                new Consumer<ScheduledTask>() {
                    private Player cas = caster;
                    private RoleInstance casterIns = instance;
                    private int cnt = 0;

                    @Override
                    public void accept(ScheduledTask scheduledTask) {
                        if(cas == null || !cas.isOnline() || cas.isDead() || casterIns == null) {
                            scheduledTask.cancel();
                            return;
                        }

                        //执行4次
                        cnt++;
                        if(cnt > 4){
                            scheduledTask.cancel();
                            return;
                        }

                        //射线检测打中的敌人
                        List<Player> playersInSightLine = SkillUtil.getPlayersInSightLine(cas, 5, 0.4);
                        boolean shouldRecoverHealth = false;
                        for(Player victim : playersInSightLine){
                            if(victim == null || victim.isDead() || !victim.isOnline()) continue;
                            if(!casterIns.isHostileTo(victim)) continue;

                            shouldRecoverHealth = true;
                            DamageUtil.dealtPhysicalDamage(victim, cas, 8);
                        }

                        if(shouldRecoverHealth){
                            casterIns.heal(4);
                        }

                        //特效
                        Location location = cas.getEyeLocation().clone();
                        Vector eachForwardDistance = location.getDirection().divide(new Vector(2, 2, 2));
                        location.subtract(new Vector(0, 0.3, 0));
                        for(int i = 0; i < 10; i++){
                            location.add(eachForwardDistance);
                            //ELECTRIC_SPARK 的数据类型是 Void，不能传 data 参数
                            location.getWorld().spawnParticle(Particle.END_ROD, location, 3, 0.1, 0.1, 0.1, 0);
                        }
                        location.getWorld().playSound(location, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1, 1);
                    }
                },
                1L, 6
        );
        instance.startSkillCooldown(getId(), getCooldown());
    }
}
