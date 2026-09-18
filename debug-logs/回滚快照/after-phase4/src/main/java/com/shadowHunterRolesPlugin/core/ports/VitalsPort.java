package com.shadowHunterRolesPlugin.core.ports;

/**
 * 生命端口：clamp 策略的唯一实现（保留 {@code RoleInstance.heal} 的语义：
 * {@code min(当前 + amount, Attribute.MAX_HEALTH)}）。
 */
public interface VitalsPort {

    void heal(double amount);
}
