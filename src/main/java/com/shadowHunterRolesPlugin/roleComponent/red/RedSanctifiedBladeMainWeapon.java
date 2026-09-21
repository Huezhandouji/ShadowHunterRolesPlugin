package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class RedSanctifiedBladeMainWeapon extends MainWeapon {

    //每次普攻施加的流血层数
    private static final int BLEED_STACKS_PER_HIT = 15;

    public RedSanctifiedBladeMainWeapon(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 图标 / 冷却逐字段一致；
     * 主武器的能量消耗由类型恒为 0），栏位由装配点 {@code setSlot} 指定。
     */
    public static final class Specification extends MainWeapon.Specification {

        public Specification(){
            super(
                    Component.text("至洁之刃"),
                    Component.text("攻击施加流血效果"),
                    Material.IRON_SWORD,
                    100
            );
        }

        @Override
        public RedSanctifiedBladeMainWeapon create(String id, ComponentServices services){
            return new RedSanctifiedBladeMainWeapon(id, services, this);
        }
    }

    /**
     * 攻击路径（新管道；B⑦ 已把 `MainWeaponListener.onAttackPlayer` 接到 `instance.handleAttack`）。
     * 语义与旧 `onAttack(Player, Player, RoleInstance)` **逐条等价**：流血层数经
     * {@code RedBleedPassive.applyStacks(...)} 写入**同一份私有账本**；**拿不到账本时只跳过流血、
     * 继续结算普攻伤害**（原意保留）；伤害 `8` / 击退 `1` 逐字不变；冷却由本组件在施放成功处按声明值启动。
     */
    @Override
    public CastResult onAttack(AttackSignal signal) {
        Player attacker = svc().self().player();
        Player victim = signal.victim();

        if (victim != null) {
            //流血记录由 RedBleedPassive 私有持有：拿不到时只跳过流血结算，普攻伤害依然生效(不能直接return)
            //已经死亡(含死亡界面)的玩家不再施加流血，避免尸体继续吃流血结算
            RedBleedPassive bleed = getComponent(RedBleedPassive.class);
            if (bleed != null && RedBleedPassive.canReceiveBleed(victim)) {
                bleed.applyStacks(victim.getUniqueId(), BLEED_STACKS_PER_HIT);
            }

            svc().damage().physicalDamage(victim, attacker, 8, 1);
        }

        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1, 1.8f);

        //INSTANT_EFFECT 的数据类型是 Particle.Spell，必须传数据，否则 Paper 会抛 "missing required data"
        if (victim != null) {
            victim.spawnParticle(Particle.INSTANT_EFFECT, victim.getLocation().clone().add(0, 1, 0), 20, 1, 1, 1, new Particle.Spell(Color.RED, 1f));
        }

        //冷却由框架按 getCooldownTicks() 启动（声明值是唯一真值来源）
        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
        return CastResult.SUCCEED;
    }

}
