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
 * 锁住装配期校验的纯半边：空 id / 重复 id 都要 fail-fast，正常装配要成功；
 * 栏位冲突**不**归装配期（见下）。
 * <p>能单测的原因：{@link Role.Builder} 的校验只读装配期内的一张小表（id 去重 + 栏位占用），
 * 不建实例、不碰 Bukkit 注册表，因此离线可跑（组件实例化发生在 {@code RoleInstance} 里，不在装配期）。
 * <p>文案冻结：`… already registered: …` 与 `Slot N is already occupied by '…'.` 逐字一致。
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

    /**
     * 栏位冲突的判据已从装配期移到"实例登记期"（口径变更的守卫）。
     *
     * <p>装配器**不再**认识栏位（快照里没有它），因此两个组件声明同一栏位时，
     * {@code addComponent} **不再抛异常** —— 冲突由
     * {@code HotbarRenderComponent#registerSlot}（{@code ActiveComponent#awake()} 里调用）
     * 用同一句冻结文案 {@code Slot N is already occupied by 'X'.} 抛出。
     *
     * <p>本用例锁住"装配期确实放行了"这一事实：否则有人把校验加回装配期，
     * 就会与"栏位归渲染组件仲裁"的单一判据重复（两处判据必须只有一处，见 {@code 组件模型.md} §6.4）。
     */
    @Test
    public void assemblyNoLongerRejectsDuplicateSlots() {
        Role.Builder b = builder("r");
        MeiqiheziBloodySlashSkill.Specification specA = new MeiqiheziBloodySlashSkill.Specification();
        specA.setSlot(1);
        MeiqiheziCircleSlashSkill.Specification specB = new MeiqiheziCircleSlashSkill.Specification();
        specB.setSlot(1);

        //装配期放行（不再抛）—— 冲突留给渲染组件的登记期
        b.addComponent("c_a", specA);
        b.addComponent("c_b", specB);

        //两侧各自都"声明了栏位 1"（真值在描述符里，未被装配期改动）
        assertEquals(1, (int) specA.slotOrNull());
        assertEquals(1, (int) specB.slotOrNull());
    }

    /** 正常装配：build() 成功、栏位由描述符持有、组件数正确。 */
    @Test
    public void validAssemblySucceeds() {
        Role.Builder b = builder("r");
        MeiqiheziBloodySlashSkill.Specification specA = new MeiqiheziBloodySlashSkill.Specification();
        specA.setSlot(1);
        MeiqiheziCircleSlashSkill.Specification specB = new MeiqiheziCircleSlashSkill.Specification();
        specB.setSlot(2);
        b.addComponent("c_a", specA);
        b.addComponent("c_b", specB);
        Role role = b.build();
        assertNotNull(role);
        assertEquals(2, role.getComponents().size());
 //栏位值**只**住在描述符里（条目与 Role 实例都不再持有）⇒ 断言读描述符本身
 //（与渲染组件读 `specification().slot()` 同一来源）
        assertEquals(1, (int) specA.slotOrNull());
        assertEquals(2, (int) specB.slotOrNull());
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
 //同上：读描述符自身的栏位（冻结后仍可读）
        assertEquals(5, (int) spec.slotOrNull());
    }
}
