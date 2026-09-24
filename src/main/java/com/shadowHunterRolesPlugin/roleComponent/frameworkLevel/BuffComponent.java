package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel;

import com.shadowHunterRolesPlugin.core.BuffType;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * buff 组件（阶段 10 · t63 · A1 改正）：系统级能力「buff 施加 / 查询 / 闸门 / 药水记账」的
 * **组件形态**（每角色实例一个，裁定③）。
 * <p><b>★ 本组件持有状态与行为</b>：
 * <ul>
 *   <li><b>buff 记账表</b> = {@link BuffManager} 实例（容器在构造期创建并交给本组件；该类属
 *       {@code manager/} 包、**不在本卡 inScope** ⇒ 不改它，只把它"归谁持有"搬过来）✓；</li>
 *   <li><b>药水记账**账本**</b> = {@link #appliedPotionTypes}（原 {@code RoleInstance} 的字段搬进本组件）
 *       ⇒ {@code clear()} 时只回收账本内的类型（O-7 语义逐字保留）✓；</li>
 *   <li><b>施加路径</b> = {@link #applyPotionEffect(PotionEffect)}（{@code player.addPotionEffect} +
 *       记账）：Bukkit API = 允许依赖的"外部东西" ✓。</li>
 * </ul>
 * <b>不再转调任何旧端口</b> ✗（原实现是经服务集端口的转发形态；阶段 13 · t103 起调用点一律**直接用本组件**）。
 */
public class BuffComponent extends RoleComponent {

    private final BuffManager buffManager;

    /** ★ 药水记账账本（原 {@code RoleInstance#appliedPotionTypes} 的持有者搬到这里）。 */
    private final Set<PotionEffectType> appliedPotionTypes = new LinkedHashSet<>();

    public BuffComponent(String id, ComponentServices services, BuffManager buffManager) {
        super(id, services);
        this.buffManager = buffManager;
    }

    /** buff 记账表本体（容器 {@code clear()} 仍需它做 {@code clearAll()} ⇒ 提供读口）。 */
    public BuffManager manager() {
        return buffManager;
    }

    /** 技能闸门（可否施放技能）。 */
    public boolean canCastSkill() {
        return buffManager.canCastSkill();
    }

    /** 主武器闸门（可否使用主武器）。 */
    public boolean canUseMainWeapon() {
        return buffManager.canUseMainWeapon();
    }

    /** 施加 buff（时长 = 游戏刻）。 */
    public void add(BuffType type, int durationTicks) {
        buffManager.addBuff(type, durationTicks);
    }

    /** 是否处于该 buff 下。 */
    public boolean has(BuffType type) {
        return buffManager.hasBuff(type);
    }

    /** 剩余刻（无该 buff ⇒ 0）。 */
    public long remainingTicks(BuffType type) {
        return buffManager.getRemainingTicks(type);
    }

    /** 施加药水效果（已记账路径；参数顺序 = 时长在前、增幅在后，与既有端口逐字一致）。 */
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier) {
        applyPotionEffect(type.createEffect(durationTicks, amplifier));
    }

    /** 同前，但 {@code ambient}/{@code particles} 标志位逐字进入效果对象（5 参构造的 {@code icon} 默认 true）。 */
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier,
                                  boolean ambient, boolean particles) {
        applyPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /** **账本写入的唯一入口**（原 {@code RoleInstance#applyPotionEffect} 的实现搬到这里）。 */
    public void applyPotionEffect(PotionEffect effect) {
        if (effect == null) {
            return;
        }
        svc().self().player().addPotionEffect(effect);
        appliedPotionTypes.add(effect.getType());
    }

    /** 账本内的类型数（读口，供取证/诊断）。 */
    public int appliedPotionTypeCount() {
        return appliedPotionTypes.size();
    }

    /**
     * 回收账本内的全部药水（原 {@code RoleInstance#clear} 的那一段搬到这里）：只移除本系统记账过的类型，
     * **不**无条件清空玩家身上的所有药水效果（O-7 / D6）。返回移除的类型数。
     */
    public int clearAppliedPotionEffects() {
        int removed = appliedPotionTypes.size();
        Player player = svc().self().player();
        for (PotionEffectType type : appliedPotionTypes) {
            player.removePotionEffect(type);
        }
        appliedPotionTypes.clear();
        return removed;
    }
}
