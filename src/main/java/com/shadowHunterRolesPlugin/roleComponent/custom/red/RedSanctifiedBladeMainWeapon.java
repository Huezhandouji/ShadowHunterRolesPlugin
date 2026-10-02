package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import java.util.List;

public class RedSanctifiedBladeMainWeapon extends MainWeapon {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "red_mainWeapon_sanctifiedBlade";

    private VitalsComponent vitals;
 //**可用性判定下放给子类**（基类不持 buff / energy、不查容器）⇒
    //  本组件自己持 buff 字段（在 start() 内一次查好）。
    private BuffComponent buff;

    //每次普攻施加的流血层数
    private static final int BLEED_STACKS_PER_HIT = 15;

    public RedSanctifiedBladeMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 图标 / 冷却由这里声明（主武器的能量消耗由类型恒为 0），
     * 栏位由装配点 {@code setSlot} 指定。
     */
    public static final class Specification extends MainWeapon.Specification<RedSanctifiedBladeMainWeapon> {

        public Specification(){
            super(
                    Component.text("至洁之刃"),
                    List.of(
                            Component.text("攻击施加流血效果"),
                            Component.text("=============================="),
                            Component.text("进化:"),
                            Component.text("1-红月落下,每个负有流血的敌人将会持续扣除特殊值, 1秒1点"),
                            Component.text("2-鲜血横飞,[孤妄自赏]多出2次攻击"),
                            Component.text("3-负罪凄凉-永久获得生命上限加10，并回满生命"),
                            Component.text("4-故不可知-[黯然销魂]的TE扣除速度减慢, 1秒4点"),
                            Component.text("5-猩红已至-流血每秒结算3层"),
                            Component.text("=============================="),
                            Component.text("被动:"),
                            Component.text("流血: 攻击给敌人叠加流血层数(上限15层),每秒结算1层、每层造成2点真实伤害,并回复自身4点TE值"),
                            Component.text("穿戴装备: 进入角色时发放一整套不可破坏的护甲(铁头盔/铁胸甲/皮革护腿/皮革靴)")
                    ),
                    Material.IRON_SWORD,
                    40
            );
            requires(VitalsComponent.class).requires(BuffComponent.class);
        }

        @Override
        public RedSanctifiedBladeMainWeapon create(String id, ComponentServicesPort services){
            return new RedSanctifiedBladeMainWeapon(id, services, this);
        }
    }

    /**
     * 攻击路径（`MainWeaponListener.onAttackPlayer` 接到 `instance.handleAttack`）：
     * 流血层数经 {@code RedBleedPassive.applyStacks(...)} 写入同一份私有账本；
     * 拿不到账本时只跳过流血、继续结算普攻伤害；伤害 `8` / 击退 `1`；
     * 冷却由本组件在施放成功处按声明值启动。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        Player attacker = svc().self().player();
        Player victim = signal.victim();

        if (victim != null) {
            //流血记录由 RedBleedPassive 私有持有：拿不到时只跳过流血结算，普攻伤害依然生效(不能直接return)
            //已经死亡(含死亡界面)的玩家不再施加流血，避免尸体继续吃流血结算
            RedBleedPassive bleed = getComponent(RedBleedPassive.class);
            if (bleed != null && RedBleedPassive.canReceiveBleed(victim)) {
                bleed.applyStacks(victim.getUniqueId(), BLEED_STACKS_PER_HIT);
            }

            vitals.physicalDamage(victim, attacker, 8, 1);
        }

        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1, 1.8f);

        //INSTANT_EFFECT 的数据类型是 Particle.Spell，必须传数据，否则 Paper 会抛 "missing required data"
        if (victim != null) {
            victim.spawnParticle(Particle.INSTANT_EFFECT, victim.getLocation().clone().add(0, 1, 0), 20, 1, 1, 1, new Particle.Spell(Color.RED, 1f));
        }

        //声明值是冷却的唯一真值来源
        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    /**
     * **开始生效**：把协作组件一次查好缓存进字段（与本族模型一致）。
     * <p>取组件只能在本钩子里做，不得放 `awake()`；注册表装配后冻结，因此与按需解析恒等。
     */
    @Override
    public void start(){
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canUseMainWeapon() || isCoolingDown();
    }

}
