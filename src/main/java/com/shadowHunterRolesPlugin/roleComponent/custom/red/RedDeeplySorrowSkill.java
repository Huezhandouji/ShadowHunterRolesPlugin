package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastTrigger;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
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
public class RedDeeplySorrowSkill extends Skill implements SanTEComponent.Subscriber {

    //该技能是否在执行中
    private boolean running = false;

    private int secondTickCount = 0;

    public RedDeeplySorrowSkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * **订阅 SanTE 变更**（阶段 12 · t89 · C2 · 用户裁定 (b)）：**向 {@code SanTEComponent} 订阅** ✓，
     * 而**不是**在类声明上 `implements` 某个能力接口 ✗ —— 后者正是用户点名的方向错误
     * （`SanTE` 早已是组件 ⇒ 硬规矩 R-1：**不得再为它新增能力接口** ✗）。
     * <p>时机 = `awake()` ⇒ 通知顺序 = 订阅先后 = 组件装配序 ✓。
     * <p>容器查找（而不是字段注入）⇒ 本组件**不持有** `SanTEComponent` 引用 ✓。
     */
    @Override
    public void awake() {
        SanTEComponent sante = svc().components().get(SanTEComponent.class);
        if (sante != null) {
            sante.subscribe(this);
        }
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(Component.text("黯然销魂"),
                    Component.text("持续扣减[红]的TE值，每秒10点，在TE值归零前获得持续的生命恢复5与力量2，在TE值归零后结束这个技能"),
                    600, 0, Material.REDSTONE_BLOCK);
        }

        @Override
        public RedDeeplySorrowSkill create(String id, ComponentServices services){
            return new RedDeeplySorrowSkill(id, services, this);
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        if(signal.trigger() != CastTrigger.RIGHT_CLICK) return;
        if(!svc().buffs().canCastSkill()) return;
        running = true;
        svc().self().player().getWorld().playSound(svc().self().player().getLocation().clone(), Sound.ENTITY_WITHER_DEATH, 2, 1);
        //冷却 600 由本组件在施放成功处按声明值启动（T-2 ③ 后所有组件无条件走新管道）
        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
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
