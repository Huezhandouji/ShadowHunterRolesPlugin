package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.ports.BuffPort;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** {@link BuffPort} 的独立适配器：闸门语义仍由 {@code BuffManager} 单一实现。 */
final class BuffPortImpl implements BuffPort {

    private final RoleInstance owner;

    BuffPortImpl(RoleInstance owner) {
        this.owner = owner;
    }

    @Override
    public boolean canCastSkill() {
        return owner.getBuffManager().canCastSkill();
    }

    @Override
    public boolean canUseMainWeapon() {
        return owner.getBuffManager().canUseMainWeapon();
    }

    @Override
    public void add(BuffType type, int durationTicks) {
        owner.getBuffManager().addBuff(type, durationTicks);
    }

    @Override
    public boolean has(BuffType type) {
        return owner.getBuffManager().hasBuff(type);
    }

    @Override
    public long remainingTicks(BuffType type) {
        return owner.getBuffManager().getRemainingTicks(type);
    }

    /**
     * R-1：**直接委托** {@code RoleInstance.applyPotionEffect(PotionEffect)} —— 与组件侧旧写法
     * `instance.applyPotionEffect(effect)` **同一条已记账路径**（不绕过记账）；效果对象按
     * `type.createEffect(durationTicks, amplifier)` 构造，参数顺序为**时长在前、增幅在后**。
     */
    @Override
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier) {
        owner.applyPotionEffect(type.createEffect(durationTicks, amplifier));
    }

    /**
     * R-1 方法族的**标志位保真版**：`ambient`/`particles` 逐字进入效果对象（`PotionEffect` 5 参构造的
     * `icon` 默认 true，与 `CircleSlash` 旧写法一致）；同样**直接委托**已记账路径，不绕过记账。
     */
    @Override
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier, boolean ambient, boolean particles) {
        owner.applyPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }
}
