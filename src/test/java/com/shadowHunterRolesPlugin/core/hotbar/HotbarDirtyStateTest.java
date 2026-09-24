package com.shadowHunterRolesPlugin.core.hotbar;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * **帧末 flush 的状态机半边**：`HotbarRenderComponent` 的
 * `markDirty()` / `isDirty()` / `clearDirty()` 是**纯状态机**：三个方法只碰脏标记与变化基线、不碰服务集。
 * <p>为什么能单测：构造组件用**空服务集桩**（`new ComponentServices(null, null, null)`，
 * 与 `CapabilityDispatchTest` / `EnergyComponentOperationTest` 同一形态），
 * 断言不需要 Bukkit、不需要服务器、不需要背包 ⇒ 离线可跑。
 * <p>运行半边（"帧末只刷一次 / 刷完清脏"）仍留运行级：那要真背包写入计数。
 * <p>本测试**不反射私有字段**：全部通过公开方法观察。
 */
public class HotbarDirtyStateTest {

    /** 空服务集桩（本测试只驱动状态机，不碰玩家 / 容器 / 角色信息服务）。 */
    private static HotbarRenderComponent component() {
        return new HotbarRenderComponent("hotbarRender", new ComponentServices(null, null, null));
    }

    /** 初值 = true（构造后的首刷必须有机会发生，即使没人置脏）。 */
    @Test
    public void startsDirtySoTheFirstFlushAlwaysRuns() {
        assertTrue("构造期初值必须是脏的（否则首位 flush 会被跳过）", component().isDirty());
    }

    /** 三方法的状态迁移：脏 →（清）→ 干净 →（置）→ 脏，且清脏是幂等的。 */
    @Test
    public void dirtyTransitionsAreExactlyTheThreeMethods() {
        HotbarRenderComponent component = component();

        component.clearDirty();
        assertFalse("clearDirty() 之后必须是干净的", component.isDirty());
        component.clearDirty();
        assertFalse("重复清脏必须保持干净（幂等）", component.isDirty());

        component.markDirty();
        assertTrue("markDirty() 之后必须是脏的", component.isDirty());
        component.markDirty();
        assertTrue("重复置脏必须仍然是脏的（幂等）", component.isDirty());

        component.clearDirty();
        assertFalse(component.isDirty());
    }

    /** 状态机不依赖服务集：三个脏标记方法在空桩下依然可用（这正是"纯半边"的定义）。 */
    @Test
    public void stateMachineDoesNotTouchTheServices() {
        HotbarRenderComponent component = component();
        component.markDirty();
        assertTrue(component.isDirty());
        component.clearDirty();
        assertFalse(component.isDirty());
    }
}
