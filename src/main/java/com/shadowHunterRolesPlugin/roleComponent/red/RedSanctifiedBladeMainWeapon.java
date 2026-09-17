package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class RedSanctifiedBladeMainWeapon extends MainWeapon {

    //每次普攻施加的流血层数
    private static final int BLEED_STACKS_PER_HIT = 15;

    public RedSanctifiedBladeMainWeapon() {
        super(
                "red_mainWeapon_sanctifiedBlade",
                Component.text("至洁之刃"),
                Component.text("攻击施加流血效果"),
                Material.IRON_SWORD,
                100
        );
        markMigrated();
    }

    /**
     * 攻击路径（新管道）。语义与旧 `onAttack(Player, Player, RoleInstance)` **逐条等价**：
     * 流血层数经 {@code RedBleedPassive.applyStacks(...)} 写入**同一份私有账本**（B⑦ 删掉上下文设施后
     * 的唯一通路）；**拿不到账本时只跳过流血、继续结算普攻伤害**（原意保留）；伤害 `8` / 击退 `1` 逐字不变。
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
        return CastResult.CAST;
    }

    /**
     * **过渡委托壳（B⑦ 保留，待接线后删除）**：`MainWeaponListener:58` 仍直接调本签名（攻击路径尚未接线到
     * `RoleInstance.handleAttack`，该方法全树 0 调用点）⇒ 为**不产生"近战静默空操作"的回归**，本壳仅
     * 1 行转调新钩子；冷却仍由 listener `:55` 按旧行为启动，**不存在双启动**。
     * 待 `listener/MainWeaponListener.java` 接线（另卡）后，本壳删除。
     */
    @Override
    public void onAttack(Player attacker, Player victim, RoleInstance instance) {
        onAttack(new AttackSignal(victim));
    }

}
