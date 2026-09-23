package com.shadowHunterRolesPlugin.frameworkLevel;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

/**
 * 生命组件（阶段 10 · t63 · A1 改正）：系统级能力「生命 / 治疗」的**组件形态**（每角色实例一个，裁定③）。
 * <p><b>★ 本组件持有行为</b>：clamp 策略（{@code min(当前 + amount, Attribute.MAX_HEALTH)}）的**唯一实现**
 * 从容器搬到这里 —— **不再转调任何旧端口** ✗。
 * <p><b>状态归属如实申报</b>：生命的真值是 **Bukkit 玩家属性**（{@code player.getHealth()} /
 * {@code Attribute.MAX_HEALTH}）⇒ 不属"组件内部字段"而是**外部平台状态**（冻结件『〇之八』：
 * 组件**允许**依赖真正外部的东西 = Bukkit API ✓）。本组件**不复制**一份生命字段 ✗（那会立刻
 * 与客户端/服务端的真实生命值不同步 ⇒ 属"会撒谎的值"）。
 */
public class VitalsComponent extends RoleComponent {

    public VitalsComponent(String id, ComponentServices services) {
        super(id, services);
    }

    /** 治疗（内部按最大生命 clamp）—— 与原 {@code RoleInstance#heal} 逐字等价。 */
    public void heal(double amount) {
        Player player = svc().self().player();
        double newHealth = Math.min(player.getHealth() + amount,
                player.getAttribute(Attribute.MAX_HEALTH).getValue());
        player.setHealth(newHealth);
    }
}
