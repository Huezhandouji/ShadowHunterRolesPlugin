package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.DamageUtil;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.SkillUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

public class RedSolitaryArroganceSkill extends Skill implements LifecycleAware {

    //O-5：任务句柄化，stop 时取消（阶段 2 由平台 Scheduler 提供，同时去掉 Folia 全局调度器误用）
    private Task attackTask;


    public RedSolitaryArroganceSkill() {
        super("red_solitaryArrogance_skill", Component.text("孤妄自赏"),
                Component.text("连续捅击四次。每次造成伤害，如果命中敌人，回复生命"),
                200,0, Material.FERMENTED_SPIDER_EYE);
    }

    @Override
    public void onRightClick(Player caster, RoleInstance instance){
        if(!instance.getBuffManager().canCastSkill()) return;
        attackTask = instance.rolesContext().scheduler().runRepeating(
                new Runnable() {
                    private Player cas = caster;
                    private RoleInstance casterIns = instance;
                    private int cnt = 0;

                    @Override
                    public void run() {
                        //实例已失效（角色被清除）时立即停止，不再以旧实例结算伤害
                        if(cas == null || !cas.isOnline() || cas.isDead() || casterIns == null || !casterIns.isValid()) {
                            attackTask.cancel();
                            return;
                        }

                        //执行4次
                        cnt++;
                        if(cnt > 4){
                            attackTask.cancel();
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
        instance.startSkillCooldown(getId(), getCooldownTicks());
    }

    @Override
    public void start(Player player, RoleInstance instance) {
    }

    @Override
    public void stop(Player player, RoleInstance instance) {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
        }
    }
}
