package com.shadowHunterRolesPlugin.core.hotbar;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * T-8（t50 §4）：锁住**帧末 flush 的状态机半边**（t50 §1 第 ⑦ 条）—— `HotbarRenderer` 的
 * `markDirty()` / `isDirty()` / `clearDirty()` 是**纯状态机**：三个方法只碰 `dirty` 字段、不读 owner。
 * <p>为什么能单测：构造器只把 owner 存进字段（`HotbarRenderer(null)` 合法；三个方法都不解引用它），
 * 断言不需要 Bukkit、不需要服务器、不需要背包 ⇒ 离线可跑（t50 的未覆盖项 3 由此闭合）。
 * <p>运行半边（"帧末只刷一次 / 刷完清脏"）仍留运行级：那要真背包写入计数（t43/t46 的三窗口径）。
 * <p>本测试**不反射私有字段**：全部通过公开三方法观察。
 */
public class HotbarDirtyStateTest {

    /** 初值 = true（构造后的首刷必须有机会发生，即使没人置脏）。 */
    @Test
    public void startsDirtySoTheFirstFlushAlwaysRuns() {
        assertTrue("构造期初值必须是脏的（否则首位 flush 会被跳过）", new HotbarRenderer(null).isDirty());
    }

    /** 三方法的状态迁移：脏 →（清）→ 干净 →（置）→ 脏，且清脏是幂等的。 */
    @Test
    public void dirtyTransitionsAreExactlyTheThreeMethods() {
        HotbarRenderer renderer = new HotbarRenderer(null);

        renderer.clearDirty();
        assertFalse("clearDirty() 之后必须是干净的", renderer.isDirty());
        renderer.clearDirty();
        assertFalse("重复清脏必须保持干净（幂等）", renderer.isDirty());

        renderer.markDirty();
        assertTrue("markDirty() 之后必须是脏的", renderer.isDirty());
        renderer.markDirty();
        assertTrue("重复置脏必须仍然是脏的（幂等）", renderer.isDirty());

        renderer.clearDirty();
        assertFalse(renderer.isDirty());
    }

    /** 状态机不依赖 owner：owner 为 null 时三方法依然可用（这正是"纯半边"的定义）。 */
    @Test
    public void stateMachineDoesNotTouchTheOwner() {
        HotbarRenderer renderer = new HotbarRenderer(null);
        renderer.markDirty();
        assertTrue(renderer.isDirty());
        renderer.clearDirty();
        assertFalse(renderer.isDirty());
    }
}
