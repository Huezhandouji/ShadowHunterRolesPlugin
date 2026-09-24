package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.roleComponent.SkillUtil;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
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
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.TimerComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;

public class RedSolitaryArroganceSkill extends Skill {

    private TimerComponent timer;
    private BuffComponent buff;

    //任务句柄化：stop 时取消（平台 Scheduler 提供；不再误用 Folia 全局调度器）
    private Task attackTask;

    //生命能力改向**组件本身**取用，引用缓存在 start()。
 //既有写法经服务集的白名单端口成员取用；该端口是**纯转发**（同一组件的同一方法）⇒ 逐字等价。
    private VitalsComponent vitals;


    public RedSolitaryArroganceSkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
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
     * 迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
 * `canCastSkill` 不满足 → **直接返回**（**直接 return、不启冷却**，已现场核）；
     * 循环任务由 `timer.runRepeating(this, 1L, 6, …)` 创建（**登记进本组件资源表** ⇒ 角色清除时框架兜底取消）；
     * `:57` 射线几何仍用**静态** `SkillUtil.getPlayersInSightLine`（无状态工具，不进端口白名单）；
     * 伤害 8 与回血 4 **逐字不变**；冷却由本组件在施放成功处按声明值 **200** 启动。
     * <p>`isValid()` 守卫按四步等价链删除：任务登记进资源表 ⇒ `clear()` 的 `cancelAllAndClear()` 必取消它 ⇒
     * 延迟体在 `valid=false` 之后不可达。
     * <p>返回类型改 {@code void}（施放结果枚举已删，返回值无消费点）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buff.canCastSkill()) return;
        attackTask = timer.runRepeating(this, 1L, 6,
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
                            if(!svc().roleInfo().isHostile(victim)) continue;

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
        startCooldown();   //D1：组件自启冷却（框架不再代启动）
    }

    /**
     * **开始生效**：把生命组件**一次查好**缓存进字段 ✓。
     * <p>为什么在 {@code start()} 而不是 {@code awake()}：禁止在 {@code awake()} 里
     * 取用其他组件 ✗（awake 只做构造期自检 / 只读自身）；`start()` 相容器已冻结 ⇒ 容器查找合法 ✓。
     * <p>为什么缓存：本技能每 6 tick 结算一次，回血点在循环体内 ⇒ 重复查容器是纯浪费；
 * 端口引用本身也是**构造期就持有的引用** ⇒ 缓存与既有口径同族 ✓。
     * <p>等价性：该端口是**纯转发**（转发到本实例的同一个生命组件、同一个方法）⇒ 逐字等价 ✓。
     */
    @Override
    public void start() {
        timer = svc().components().get(TimerComponent.class);
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    /**
     * 新基类（RoleComponent）停止钩子：容器在 legacy 扇出之后、
     * `cancelAllAndClear()` **之前**广播 ⇒ 与旧 `LifecycleAware.stop(...)` 等价（取消）；框架另有兜底（幂等）。
     */
    @Override
    public void stop() {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
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
     * **当前能量**：本组件**不参与能量维度**（声明耗能 0）⇒ 返回声明值；
 * 与既有实现**逐字等价**（能量组件内 clamp 到 `[0, max]` ⇒ 原判定 `current() < 0` 恒假）。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
