package com.shadowHunterRolesPlugin.roleComponent.service;

import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.potion.PotionEffectType;

/**
 * buff 组件（阶段 10 · t55 · A1）：系统级能力「buff 施加 / 查询 / 记账」的**组件形态**
 * （每角色实例一个，裁定③）。
 * <p><b>薄封装</b>：内部**转调既有端口** {@code BuffPort}（闸门语义仍由 {@code BuffManager} 单一实现；
 * 药水施加仍走容器已记账的路径，不绕过记账）。
 * <p><b>使用示例（其他组件内）</b>：
 * <pre>{@code
 * private BuffComponent buffs;
 * @Override public void awake() { buffs = getComponent(BuffComponent.class); }
 * @Override public void onCast(CastSignal signal) {
 *     if (!buffs.canCastSkill()) return;                 // 闸门
 *     buffs.add(BuffType.SILENCE, 40);                   // 施加 + 记账
 * }
 * }</pre>
 * <p><b>装配示例</b>：{@code builder.addComponent("buffs", new BuffComponent.Specification());}（不占栏位）。
 * <p><b>本卡不改任何调用点</b>：既有组件仍走 {@code svc().buffs()}。
 */
public class BuffComponent extends RoleComponent {

    public BuffComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 技能闸门（可否施放技能）。 */
    public boolean canCastSkill() {
        return svc().buffs().canCastSkill();
    }

    /** 主武器闸门（可否使用主武器）。 */
    public boolean canUseMainWeapon() {
        return svc().buffs().canUseMainWeapon();
    }

    /** 施加 buff（时长 = 游戏刻）。 */
    public void add(BuffType type, int durationTicks) {
        svc().buffs().add(type, durationTicks);
    }

    /** 是否处于该 buff 下。 */
    public boolean has(BuffType type) {
        return svc().buffs().has(type);
    }

    /** 剩余刻（无该 buff ⇒ 0）。 */
    public long remainingTicks(BuffType type) {
        return svc().buffs().remainingTicks(type);
    }

    /** 施加药水效果（**走容器已记账的路径**：`clear()` 时只回收账本内的类型）。 */
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier) {
        svc().buffs().applyPotionEffect(type, durationTicks, amplifier);
    }

    /** 同前，但 `ambient` / `particles` 标志位逐字进入效果对象。 */
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier,
                                  boolean ambient, boolean particles) {
        svc().buffs().applyPotionEffect(type, durationTicks, amplifier, ambient, particles);
    }

    /** 装配描述符：**不占栏位**；提供类型 = {@code BuffComponent.class}。 */
    public static final class Specification extends RoleComponent.Specification<BuffComponent> {

        public Specification() {
            super("BuffComponent");
        }

        @Override
        public BuffComponent create(String id, ComponentServices services) {
            return new BuffComponent(id, services);
        }
    }
}
