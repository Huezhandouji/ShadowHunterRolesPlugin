package com.shadowHunterRolesPlugin.core.ports;

/** 能量真值端口。{@code tryConsume} = 检查 + 扣减合一（取代"先判断再扣"的两步写法）。 */
public interface EnergyPort {

    int current();

    boolean tryConsume(int amount);

    void gain(int amount);
}
