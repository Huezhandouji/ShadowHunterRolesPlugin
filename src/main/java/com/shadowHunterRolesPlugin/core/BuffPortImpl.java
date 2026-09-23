package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.ports.BuffPort;
import com.shadowHunterRolesPlugin.frameworkLevel.BuffComponent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * {@link BuffPort} 的独立适配器（阶段 10 · t63 · A2）：**纯转发**到 {@link BuffComponent} ——
 * buff 记账表（{@code BuffManager}）与药水账本的持有者都已搬到组件，本类不持有任何状态 ✗。
 * <p><b>记账不绕过</b>：两个 {@code applyPotionEffect} 重载都走组件的**唯一账本写入入口**
 * （{@code player.addPotionEffect} + 记账）⇒ 与既有行为逐字一致（{@code clear()} 只回收账本内的类型）。
 * <p>第 3 步之前的**临时兼容层**；既有调用点一字未动。
 */
final class BuffPortImpl implements BuffPort {

    private final RoleInstance owner;

    BuffPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    private BuffComponent component() {
        return owner.buffComponent();
    }

    @Override
    public boolean canCastSkill() {
        return component().canCastSkill();
    }

    @Override
    public boolean canUseMainWeapon() {
        return component().canUseMainWeapon();
    }

    @Override
    public void add(BuffType type, int durationTicks) {
        component().add(type, durationTicks);
    }

    @Override
    public boolean has(BuffType type) {
        return component().has(type);
    }

    @Override
    public long remainingTicks(BuffType type) {
        return component().remainingTicks(type);
    }

    /**
     * R-1：效果对象按 {@code type.createEffect(durationTicks, amplifier)} 构造（**时长在前、增幅在后**），
     * 由组件走同一条**已记账**路径施加。
     */
    @Override
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier) {
        component().applyPotionEffect(type, durationTicks, amplifier);
    }

    /**
     * R-1 方法族的**标志位保真版**：{@code ambient}/{@code particles} 逐字进入效果对象
     * （{@code PotionEffect} 5 参构造的 {@code icon} 默认 true，与 {@code CircleSlash} 旧写法一致）。
     */
    @Override
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles) {
        component().applyPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }
}
