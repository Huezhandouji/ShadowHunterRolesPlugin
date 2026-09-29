package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.SkillUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;

public class RedSolitaryArroganceSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "red_solitaryArrogance_skill";

    private TaskComponent timer;
    private BuffComponent buff;

    //任务句柄：stop 时取消（句柄由平台 Scheduler 提供）
    private ScheduledHandle attackTask;

    //生命能力向**组件本身**取用，引用缓存在 start()。
    //服务集的白名单端口是对同一组件同一方法的**纯转发**，故两者逐字等价。
    private VitalsComponent vitals;


    public RedSolitaryArroganceSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 冷却 / 耗能 / 图标由这里声明，
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification<RedSolitaryArroganceSkill> {

        public Specification(){
            super(Component.text("孤妄自赏"),
                    List.of(Component.text("连续捅击四次。每次造成伤害，如果命中敌人，回复生命")),
                    200, 0, Material.FERMENTED_SPIDER_EYE);
            requires(TaskComponent.class).requires(BuffComponent.class).requires(VitalsComponent.class);
        }

        @Override
        public RedSolitaryArroganceSkill create(String id, ComponentServicesPort services){
            return new RedSolitaryArroganceSkill(id, services, this);
        }
    }

    /**
     * 右击施放：`canCastSkill` 不满足时直接返回（不启冷却）；
     * 循环任务由 `timer.addScheduleRepeating(this, 1L, 6, …)` 创建（登记进本组件资源表，角色清除时框架兜底取消）；
     * 射线几何用静态 `SkillUtil.getPlayersInSightLine`（无状态工具，不进端口白名单）；
     * 伤害 8、回血 4；冷却由本组件在施放成功处按声明值 200 启动。
     * <p>`isValid()` 守卫不需要：任务登记进资源表后，`clear()` 的 `cancelAllAndClear()` 必取消它，
     * 延迟体在 `valid=false` 之后不可达。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buff.canCastSkill()) return;
        attackTask = timer.addScheduleRepeating(this, 1L, 6,
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
                            if(!svc().roleInfo().isHostileTo(victim.getUniqueId())) continue;

                            shouldRecoverHealth = true;
                            vitals.physicalDamage(victim, cas, 8);
                        }

                        if(shouldRecoverHealth){
                            vitals.heal(4);
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
        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    /**
     * **开始生效**：把生命组件一次查好缓存进字段。
     * <p>时机 = {@code start()}，不在 {@code awake()}：禁止在 {@code awake()} 里取用其他组件
     * （awake 只做构造期自检 / 只读自身）；`start()` 时容器已冻结，容器查找合法。
     * <p>缓存理由：本技能每 6 tick 结算一次、回血点在循环体内，重复查容器是纯浪费；
     * 端口引用本身也是构造期就持有的引用，因此缓存与既有口径同族（该端口是纯转发 ⇒ 逐字等价）。
     */
    @Override
    public void start() {
        timer = svc().components().get(TaskComponent.class);
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    /**
     * 停止钩子：容器在 `cancelAllAndClear()` 之前广播，因此本方法能先取消循环任务；
     * 框架另有兜底取消，重复取消幂等。
     */
    @Override
    public void stop() {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
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
     * **当前能量**：本组件不参与能量维度（声明耗能 0），因此返回声明值；
     * 能量组件的值被 clamp 到 `[0, max]`，故 `current() < 0` 恒假。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
