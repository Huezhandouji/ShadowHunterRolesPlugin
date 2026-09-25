package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.roleComponent.builtin.AutoRecoverEnergyPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * T-3（t50 §4）：锁住**装配期校验**（t50 §1 第 ⑨⑩ 条的纯半边）= 空 id / 重复 id / 重复槽位都要
 * fail-fast，正常装配要成功。
 * <p>为什么能单测：{@link Role.Builder} 的校验只读装配期内的一张小表（id 去重 + 栏位占用），
 * 不建实例、不碰 Bukkit 注册表 ⇒ 离线可跑（组件实例化发生在 {@code RoleInstance} 里，不在装配期）。
 * <p>文案冻结：`… already registered: …` 与 `Slot N is already occupied by '…'.`（t50 的读数把它逐字记下来了）。
 */
public class AssemblyValidationTest {

    private static Role.Builder builder(String id) {
        return new Role.Builder(id);
    }

    /** fail-fast ①：角色 id 为空 ⇒ IllegalArgumentException。 */
    @Test
    public void roleIdMustNotBeEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new Role.Builder(""));
        assertThrows(IllegalArgumentException.class, () -> new Role.Builder("   "));
        assertThrows(IllegalArgumentException.class, () -> new Role.Builder(null));
    }

    /** fail-fast ②：组件 id 为空 ⇒ IllegalArgumentException。 */
    @Test
    public void componentIdMustNotBeEmpty() {
        Role.Builder b = builder("r");
        assertThrows(IllegalArgumentException.class,
                () -> b.addComponent("", new MeiqiheziBloodySlashSkill.Specification().setSlot(1)));
    }

    /** fail-fast ③：id 去重是**跨类型**的（技能/被动共用同一命名空间）⇒ 文案点名类型词。 */
    @Test
    public void duplicateIdIsRejectedAcrossFamilies() {
        Role.Builder b = builder("r");
        b.addComponent("c_a", new MeiqiheziBloodySlashSkill.Specification().setSlot(1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> b.addComponent("c_a", new MeiqiheziCircleSlashSkill.Specification().setSlot(2)));
        assertEquals("Skill already registered: c_a", e.getMessage());

        Role.Builder b2 = builder("r2");
        b2.addComponent("c_a", new AutoRecoverEnergyPassive.Specification());
        assertThrows(IllegalArgumentException.class,
                () -> b2.addComponent("c_a", new MeiqiheziBloodySlashSkill.Specification().setSlot(1)));
    }

    /** fail-fast ④：重复槽位 ⇒ IllegalArgumentException（不"告警 + 覆盖"），文案点名占用者。 */
    @Test
    public void duplicateSlotIsRejectedAndNamesTheOccupant() {
        Role.Builder b = builder("r");
        b.addComponent("c_a", new MeiqiheziBloodySlashSkill.Specification().setSlot(1));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> b.addComponent("c_b", new MeiqiheziCircleSlashSkill.Specification().setSlot(1)));
        assertEquals("Slot 1 is already occupied by 'c_a'.", e.getMessage());
    }

    /** 正常装配：build() 成功、条目栏位正确、组件数正确。 */
    @Test
    public void validAssemblySucceeds() {
        Role.Builder b = builder("r");
        b.addComponent("c_a", new MeiqiheziBloodySlashSkill.Specification().setSlot(1));
        b.addComponent("c_b", new MeiqiheziCircleSlashSkill.Specification().setSlot(2));
        Role role = b.build();
        assertNotNull(role);
        assertEquals(2, role.getComponents().size());
 //★ 栏位视图已从聚合根删除（`getSlotMap` / `componentIdAtSlot`）⇒ 改读**条目携带的栏位值**
 //（与渲染组件读描述符的 `slot()` 同一来源）
        assertEquals(1, role.getComponents().get("c_a").getSlot());
        assertEquals(2, role.getComponents().get("c_b").getSlot());
        assertTrue(role.getSkillIds().contains("c_a"));
        assertTrue(role.getSkillIds().contains("c_b"));
    }

    /** 同一份描述符实例被两个角色共享时：后手改动影响不到先手（条目只持有不可变快照）。 */
    @Test
    public void sharedSpecificationIsFrozenAtFirstAssembly() {
        MeiqiheziCircleSlashSkill.Specification spec = new MeiqiheziCircleSlashSkill.Specification();
        spec.setSlot(5);
        Role r1 = builder("r1").addComponent("c_x", spec).build();
        assertThrows(IllegalStateException.class, () -> spec.setSlot(6));
 //★ 同上：改读条目携带的栏位值
        assertEquals(5, r1.getComponents().get("c_x").getSlot());
    }
}
