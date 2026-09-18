package com.shadowHunterRolesPlugin.core.ports;

/** SanTE 真值端口（SanTE 由框架独占真值 ⇒ 所有写入都经这里）。 */
public interface SanTEPort {

    void gain(int amount);

    void decrease(int amount);

    void set(int value);

    int current();

    int max();
}
