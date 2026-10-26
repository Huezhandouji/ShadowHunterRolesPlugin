package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 「特克」真理账本 {@link TekTruth} 的**离线**单测。
 *
 * <h2>为什么能离线跑</h2>
 * 本类是纯静态逻辑：不碰 Bukkit 注册表、不取任何组件、不读玩家对象（键 = UUID）。
 * 因此整类可脱离服务端直接断言（与 {@code CapabilityDispatchTest} 同一做法）。
 *
 * <h2>判据来源</h2>
 * 需求「真理」那几条：每次命中 +1 层；「真理之刺」在场上有角色 ≥ 10 层时解锁；
 * 施放后「去除场上所有真理层数」。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li><b>不</b>覆盖：组件侧"命中 ⇒ 调 onHit"的派发（需要 Player / 事件，属运行级）；</li>
 *   <li><b>不</b>覆盖：护盾阈值触发、冷却减少（都要 Bukkit 的 tick 与组件实例）。</li>
 * </ul>
 */
public class TekTruthTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    /** 每条用例前清空账本（静态状态 ⇒ 必须显式隔离）。 */
    @Before
    public void reset() {
        TekTruth.resetForTest();
    }

    @Test
    public void 空账本读零层() {
        assertEquals(0, TekTruth.layersOf(A));
        assertEquals(0, TekTruth.size());
        assertFalse(TekTruth.isUnlocked());
    }

    @Test
    public void null键读零层且不写账本() {
        assertEquals(0, TekTruth.layersOf(null));
        assertEquals(0, TekTruth.add(null, 5));
        assertEquals(0, TekTruth.size());
    }

    @Test
    public void 叠加一层返回新层数() {
        assertEquals(1, TekTruth.addOne(A));
        assertEquals(2, TekTruth.addOne(A));
        assertEquals(3, TekTruth.add(A, 1));
        assertEquals(3, TekTruth.layersOf(A));
        assertEquals(1, TekTruth.size());
    }

    @Test
    public void 层数按目标各自独立() {
        TekTruth.add(A, 7);
        TekTruth.add(B, 3);
        assertEquals(7, TekTruth.layersOf(A));
        assertEquals(3, TekTruth.layersOf(B));
        assertEquals(2, TekTruth.size());
    }

    @Test
    public void 归零的条目即时移除() {
        TekTruth.add(A, 4);
        assertEquals(1, TekTruth.size());
        //一次减回去 ⇒ 0 层不留下空条目
        assertEquals(0, TekTruth.add(A, -4));
        assertEquals(0, TekTruth.layersOf(A));
        assertEquals(0, TekTruth.size());
    }

    @Test
    public void 层数上限被钳制() {
        TekTruth.add(A, TekTruth.MAX_LAYERS + 50);
        assertEquals(TekTruth.MAX_LAYERS, TekTruth.layersOf(A));
    }

    @Test
    public void 解锁阈值是十层() {
        assertEquals(10, TekTruth.UNLOCK_THRESHOLD);
        TekTruth.add(A, 9);
        assertFalse("9 层不该解锁", TekTruth.isUnlocked());
        TekTruth.add(A, 1);
        assertTrue("10 层应当解锁", TekTruth.isUnlocked());
    }

    @Test
    public void 解锁只看是否存在达标者_与其它低层者无关() {
        TekTruth.add(A, 3);
        TekTruth.add(B, 4);
        assertFalse(TekTruth.isUnlocked());
        TekTruth.add(B, 6); // B 到 10
        assertTrue(TekTruth.isUnlocked());
    }

    @Test
    public void 清空单个目标返回被清层数() {
        TekTruth.add(A, 6);
        assertEquals(6, TekTruth.clearOne(A));
        assertEquals(0, TekTruth.layersOf(A));
        //再清一次：没东西可清 ⇒ 0
        assertEquals(0, TekTruth.clearOne(A));
    }

    @Test
    public void 清空全场返回总层数() {
        TekTruth.add(A, 5);
        TekTruth.add(B, 8);
        assertEquals(13, TekTruth.clearAll());
        assertEquals(0, TekTruth.size());
        assertEquals(0, TekTruth.layersOf(A));
        assertEquals(0, TekTruth.layersOf(B));
    }

    @Test
    public void 快照列出全部有真理的目标() {
        TekTruth.add(A, 12);
        TekTruth.add(B, 2);
        List<TekTruth.Target> snapshot = TekTruth.snapshot();
        assertEquals(2, snapshot.size());
        int total = snapshot.stream().mapToInt(TekTruth.Target::layers).sum();
        assertEquals(14, total);
    }

    @Test
    public void 快照为空时不抛异常() {
        assertTrue(TekTruth.snapshot().isEmpty());
    }
}
