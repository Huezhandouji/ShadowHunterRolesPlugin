package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.SkillUtil;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

public class RedSolitaryArroganceSkill extends Skill {

    //O-5：任务句柄化，stop 时取消（阶段 2 由平台 Scheduler 提供，同时去掉 Folia 全局调度器误用）
    private Task attackTask;


    public RedSolitaryArroganceSkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(Component.text("孤妄自赏"),
                    Component.text("连续捅击四次。每次造成伤害，如果命中敌人，回复生命"),
                    200, 0, Material.FERMENTED_SPIDER_EYE);
        }

        @Override
        public RedSolitaryArroganceSkill create(String id, ComponentServices services){
            return new RedSolitaryArroganceSkill(id, services, this);
        }
    }

    /**
     * 批次③（B③）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * `canCastSkill` 不满足 → **直接返回**（**旧写法 `:34` 就是直接 return、不启冷却**，已现场核）；
     * 循环任务由 `svc().timers().runRepeating(1L, 6, …)` 创建（**登记进本组件资源表** ⇒ 角色清除时框架兜底取消）；
     * `:57` 射线几何仍用**静态** `SkillUtil.getPlayersInSightLine`（无状态工具，不进端口白名单）；
     * 伤害 8 与回血 4 **逐字不变**；冷却由本组件在施放成功处按声明值 **200** 启动。
     * <p>`isValid()` 守卫按四步等价链删除：任务登记进资源表 ⇒ `clear()` 的 `cancelAllAndClear()` 必取消它 ⇒
     * 延迟体在 `valid=false` 之后不可达。
     * <p>阶段 8：返回类型改 {@code void}（旧的施放结果枚举已删，返回值无消费点）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!svc().buffs().canCastSkill()) return;
        attackTask = svc().timers().runRepeating(1L, 6,
                new Runnable() {
                    private Player cas = caster;
                    private int cnt = 0;

                    @Override
                    public void run() {
                        if(cas == null || !cas.isOnline() || cas.isDead()) {
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
                            if(!svc().factions().isHostile(victim)) continue;

                            shouldRecoverHealth = true;
                            svc().damage().physicalDamage(victim, cas, 8);
                        }

                        if(shouldRecoverHealth){
                            svc().vitals().heal(4);
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
                }
        );
        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
    }

    @Override
    public void start() {
    }

    /**
     * 新基类（RoleComponent）停止钩子（阶段 4 B③ 同批完成 legacy→新钩子 转换）：容器在 legacy 扇出之后、
     * `cancelAllAndClear()` **之前**广播 ⇒ 与旧 `LifecycleAware.stop(...)` 等价（O-5 的取消）；框架另有兜底（幂等）。
     */
    @Override
    public void stop() {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
        }
    }
}
