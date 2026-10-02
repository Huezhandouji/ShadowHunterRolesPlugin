package com.shadowHunterRolesPlugin.roleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * 锁住装配期描述符的封冻与 fail-fast。
 * <p>能单测的原因：{@code RoleComponent.Specification} 是装配期数据 —— 全部判定只读写它自己的字段
 * （frozen / slot / boundId），不触 Bukkit 注册表、不构物品，因此离线可跑。
 * <p>这一族发生过真实回归（描述符封冻被绕过 / 未设栏位静默降级 / 越界不校验），所以判据必须钉死。
 * <p>本测试只用公开 API（不反射私有字段、不加测试后门）；被测描述符取真实组件的嵌套描述符
 * （{@link MeiqiheziBloodySlashSkill.Specification}，继承 {@link HotbarSpecification} ⇒ 带栏位那一支：
 * 装配期未 setSlot 就 freeze 必抛。这正是本类要锁的口径之一）。
 */
public class DescriptorFreezeTest {

    private static HotbarSpecification<?> fresh() {
        return new MeiqiheziBloodySlashSkill.Specification();
    }

    /** 装配期绑定 id：绑定前 null，绑定后同值（id 属于注册处），重复绑定幂等。 */
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
        //快照**不再带栏位**：栏位归描述符自己（上面两行已断言）与渲染组件的登记表
        //  ⇒ 装配期不再有栏位冲突判定（仲裁在 `HotbarRenderComponent#registerSlot`）
        //  此处只断言快照本身可用（工厂 + 依赖声明的载体）
        assertNotNull("快照必须带工厂（装配表只持有它）", snapshot.getFactory());
    }

    /**
     * fail-fast ①：越界栏位 ⇒ IllegalArgumentException（文案冻结）。
     * <p>上界已从热键栏的 9 格放开到**玩家背包的槽位总数**（{@link HotbarSpecification#MAX_SLOT}），
     * 因此"越界"的取样点跟着上移；文案里的数字由常量拼出。
     */
    @Test
    public void slotOutOfRangeThrows() {
        assertThrows(IllegalArgumentException.class, () -> fresh().setSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> fresh().setSlot(HotbarSpecification.MAX_SLOT + 1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> fresh().setSlot(HotbarSpecification.MAX_SLOT + 1));
        assertEquals("Slot must be between 0 and " + HotbarSpecification.MAX_SLOT + ", got: "
                + (HotbarSpecification.MAX_SLOT + 1), e.getMessage());
    }

    /**
     * 上界是**闭**的（差一错误最爱发生的位置）：{@code MAX_SLOT} 可设、{@code MAX_SLOT + 1} 不可设。
     * <p>同时把上界的**值**冻住：{@code 40} = 41 个槽位（热键栏 9 + 背包主格 27 + 盔甲 4 + 副手 1）− 1。
     * 依据是 1.21.11 paper-api 的 {@code PlayerInventory#setItem} 契约原文（见 {@link HotbarSpecification#MAX_SLOT}
     * 的 javadoc）。这个数字要改，必须连同那份依据一起改 —— 这正是本断言的用途。
     */
    @Test
    public void slotBoundaryIsInclusive() {
        HotbarSpecification<?> spec = fresh();
        spec.setSlot(HotbarSpecification.MAX_SLOT);
        assertTrue("上界本身必须可设（闭区间）", spec.hasSlot());
        assertEquals(HotbarSpecification.MAX_SLOT, spec.slot());

        assertEquals("上界冻住：41 个背包槽位 ⇒ 合法索引 0..40", 40, HotbarSpecification.MAX_SLOT);
        assertThrows(IllegalArgumentException.class,
                () -> fresh().setSlot(HotbarSpecification.MAX_SLOT + 1));
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

    /**
     * **冻结不改值**：冻结**之后**描述符自己读到的栏位仍是冻结前设的那个值
     * （快照不再带栏位 ⇒ 断言点从"快照里的值"移到"描述符自己的值"，语义不变：
     * 冻结是**封住写口**，不是**清掉数据**）。
     */
    @Test
    public void descriptorKeepsTheSlotValueThatWasSetBeforeFreeze() {
        HotbarSpecification<?> spec = fresh();
        spec.setSlot(3);
        RoleComponent.Specification.Snapshot snapshot = spec.freeze();
        assertEquals("冻结后描述符仍持有栏位 3", 3, spec.slot());
        assertNotNull("快照必须带工厂（装配表只持有它）", snapshot.getFactory());
    }
}
