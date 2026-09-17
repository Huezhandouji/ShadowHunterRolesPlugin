package com.shadowHunterRolesPlugin.roleComponent.red;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastTrigger;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

/**
 * 黯然销魂（红）：持续扣减红的 SanTE，归零前给自己回血与力量。
 * <p><b>批次⑩/B10 的迁移口径</b>（T 块第 ⑧ 项的前置：四类 `*Aware` 实现者归零）：
 * <ul>
 *   <li>去 legacy `UpdateAware` 与 `SanTEChangeAware` ⇒ 改走基类新钩子 {@link #update()} 与
 *       {@link #onSanTEChange(int, int)}（容器对**注册表内组件**广播；SanTE 侧为"真变化才派发"，
 *       由 B⑨ 完成、本卡不新增可见变化）；</li>
 *   <li>旧式聚合根调用端口化：`instance.decreaseSanTE(10)` → `svc().sante().decrease(10)`；
 *       `instance.applyPotionEffect(…createEffect(45, 5/2))` → `svc().buffs().applyPotionEffect(type, 45, 5/2)`
 *       （**同一条已记账路径** O-7）；`instance.startSkillCooldown(getId(), getCooldownTicks())` →
 *       `svc().cooldowns().start(getCooldownTicks())`（`CooldownPortImpl.start` 委托回
 *       `owner.startSkillCooldown(componentId, ticks)` ⇒ **同一张冷却表**）；</li>
 *   <li>**数值与间隔逐字不变**：冷却 `600` / 能量 `0` / 每秒（`20` tick）一结算 / 扣 `10` 点 SanTE /
 *       生命恢复 `45, 5` 与力量 `45, 2` / 音效 `ENTITY_WITHER_DEATH 2,1` 与 `ENTITY_WITHER_SHOOT 1,1`；</li>
 * </ul>
 */
public class RedDeeplySorrowSkill extends Skill {

    //该技能是否在执行中
    private boolean running = false;

    private int secondTickCount = 0;

    public RedDeeplySorrowSkill(String id, ComponentServices services) {
        super(id, services, Component.text("黯然销魂"),
                Component.text("持续扣减[红]的TE值，每秒10点，在TE值归零前获得持续的生命恢复5与力量2，在TE值归零后结束这个技能"),
                600,0, Material.REDSTONE_BLOCK);
    }

    @Override
    public CastResult onCast(CastSignal signal) {
        if(signal.trigger() != CastTrigger.RIGHT_CLICK) return CastResult.NO_COOLDOWN;
        if(!svc().buffs().canCastSkill()) return CastResult.NO_COOLDOWN;
        running = true;
        svc().self().player().getWorld().playSound(svc().self().player().getLocation().clone(), Sound.ENTITY_WITHER_DEATH, 2, 1);
        //冷却 600 由框架按声明值启动（T-2 ③ 后所有组件无条件走新管道）
        return CastResult.SUCCEED;
    }

    @Override
    public void update() {
        if(!running) return;

        secondTickCount++;
        if(secondTickCount < 20) return;
        secondTickCount = 0;

        Player caster = svc().self().player();

        svc().sante().decrease(10);
        //药水记账（O-7）：经 svc().buffs() 走 RoleInstance 的**同一条已记账路径**，clear() 时只回收本系统施加的效果
        svc().buffs().applyPotionEffect(PotionEffectType.REGENERATION, 45, 5);
        svc().buffs().applyPotionEffect(PotionEffectType.STRENGTH, 45, 2);
        svc().cooldowns().start(getCooldownTicks());

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);
    }

    @Override
    public void onSanTEChange(int pre, int now) {
        if(now <= 0){
            running = false;
            svc().cooldowns().start(getCooldownTicks());
        }
    }
}
