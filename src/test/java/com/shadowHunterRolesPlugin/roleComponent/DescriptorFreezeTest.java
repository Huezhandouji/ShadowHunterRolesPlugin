package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * T-1（t50 §4）：锁住**装配期描述符的封冻与 fail-fast**（t50 §1 第 10 条 = **可单测**）。
 * <p>为什么能单测：{@code RoleComponent.Specification} 是**装配期数据** —— 全部判定只读写它自己的字段
 * （frozen / slot / boundId），不触 Bukkit 注册表、不构物品 ⇒ 离线可跑。
 * <p>为什么最贵：这一族**发生过真实回归**（描述符封冻被绕过 / 未设栏位静默降级 / 越界不校验）。
 * <p>本测试**只用公开 API**（不反射私有字段、不加测试后门）；被测描述符取真实组件的嵌套描述符
 * （{@link MeiqiheziBloodySlashSkill.Specification}，继承 {@link HotbarSpecification} ⇒ requiresSlot()==true）。
 */
public class DescriptorFreezeTest {

    private static HotbarSpecification<?> fresh() {
        return new MeiqiheziBloodySlashSkill.Specification();
    }

    /** 装配期绑定 id：绑定前 null，绑定后同值（t35 的 A7 选 (a)：id 属于注册处）。 */
    @Test
    public void idIsBoundAtAssemblyTime() {
        HotbarSpecification<?> spec = fresh();
        assertEquals("未装配的描述符不得自报 id", null, spec.getId());
        spec.bindId("bound_id");
        assertEquals("bindId 后 id 必须与注册处同值", "bound_id", spec.getId());
        spec.bindId("bound_id"); // 同 id 重复绑定是幂等的
        assertEquals("bound_id", spec.getId());
    }

    /** 栏位的缺失：未设栏位时 hasSlot()==false，且 slot() 抛异常（不是返回 -1 哨兵）。 */
    @Test
    public void missingSlotIsAbsenceNotSentinel() {
        HotbarSpecification<?> spec = fresh();
        assertFalse(spec.hasSlot());
        assertThrows(IllegalStateException.class, spec::slot);
    }

    /** 设栏位后：hasSlot()==true、slot() 返回该值、Snapshot 带同值。 */
    @Test
    public void slotIsReadableAfterSetAndInSnapshot() {
        HotbarSpecification<?> spec = fresh();
        spec.setSlot(4);
        assertTrue(spec.hasSlot());
        assertEquals(4, spec.slot());
        RoleComponent.Specification.Snapshot snapshot = spec.freeze();
        assertNotNull(snapshot);
        assertTrue(snapshot.hasSlot());
        assertEquals(4, snapshot.getSlot());
    }

    /** fail-fast ①：越界栏位 ⇒ IllegalArgumentException（文案冻结）。 */
    @Test
    public void slotOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> fresh().setSlot(9));
        assertThrows(IllegalArgumentException.class, () -> fresh().setSlot(-1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> fresh().setSlot(9));
        assertEquals("Slot must be between 0 and 8, got: 9", e.getMessage());
    }

    /** fail-fast ②：重复改成别的位置 ⇒ IllegalStateException（拒绝静默搬家）。 */
    @Test
    public void slotCannotBeMovedSilently() {
        HotbarSpecification<?> spec = fresh();
        spec.setSlot(1);
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> spec.setSlot(2));
        assertTrue("文案要点名旧位置", e.getMessage().contains("already assigned to 1"));
    }

    /** fail-fast ③：冻结后再改 ⇒ IllegalStateException（封冻是**结构**约束，不是约定）。 */
    @Test
    public void frozenSpecificationRejectsFurtherChanges() {
        HotbarSpecification<?> spec = fresh();
        spec.setSlot(1);
        spec.freeze();
        IllegalStateException a = assertThrows(IllegalStateException.class, () -> spec.setSlot(2));
        IllegalStateException b = assertThrows(IllegalStateException.class, () -> spec.bindId("other_id"));
        assertTrue(a.getMessage().contains("frozen"));
        assertTrue(b.getMessage().contains("frozen"));
    }

    /** fail-fast ④：**未设栏位就 freeze** ⇒ IllegalStateException（带栏位那支必须显式给位）。 */
    @Test
    public void freezeWithoutSlotThrows() {
        HotbarSpecification<?> spec = fresh();
        IllegalStateException e = assertThrows(IllegalStateException.class, spec::freeze);
        assertTrue("文案要点名必须先 setSlot", e.getMessage().contains("must be given a slot"));
    }

    /** 冻结**不改值**：Snapshot 里的栏位 = 冻结前设的值；未设栏位者不可能冻结成功。 */
    @Test
    public void snapshotKeepsTheValueThatWasSetBeforeFreeze() {
        HotbarSpecification<?> spec = fresh();
        spec.setSlot(3);
        RoleComponent.Specification.Snapshot snapshot = spec.freeze();
        assertEquals(3, snapshot.getSlot());
        assertNotNull("快照必须带工厂（装配表只持有它）", snapshot.getFactory());
    }
}
