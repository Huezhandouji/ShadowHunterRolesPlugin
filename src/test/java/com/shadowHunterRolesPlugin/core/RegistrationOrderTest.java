package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.roleComponent.builtin.AutoRecoverEnergyPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * T-4（t50 §4）：锁住**注册序 → 栏位视图**（t50 §1 第 ⑨ 条的纯半边），并且**专门锁 `LinkedHashMap` 的顺序性**。
 * <p>为什么能单测：装配表是一张纯内存表；{@code getComponents()} / {@code getSlotMap()} /
 * {@code componentIdAtSlot(int)} 都只读它，不需要实例、不需要 Bukkit ⇒ 离线可跑。
 * <p>顺序为什么最贵：本项目"注册序 = 渲染序 = 派发序"是冻结面；把表换成 `HashMap` 就会静默乱序。
 * 本测试因此内置一条**样本判别力守卫**：同一批 id 装进 {@link HashMap} 时的迭代序与登记序**必须不同**
 * （现算样本 `c_1…c_8` 满足）—— 若哪天 JDK 让两者巧合相同，这条守卫会先红，提示"该换样本了"，
 * 免得顺序断言在不知情的情况下失去判别力。
 */
public class RegistrationOrderTest {

    private static final String[] IDS = {"c_1", "c_2", "c_3", "c_4", "c_5", "c_6", "c_7", "c_8"};

    /** 8 个带栏位条目 + 1 个无栏位条目 ⇒ 遍历序 = 登记序；无栏位者不在 slotMap 里。 */
    @Test
    public void componentsKeepRegistrationOrderAndSlotViewSkipsSlotless() {
        Role.Builder b = new Role.Builder("r");
        for (int i = 0; i < IDS.length; i++) {
            b.addComponent(IDS[i], new MeiqiheziBloodySlashSkill.Specification().setSlot(i));
        }
        b.addComponent("c_9_no_slot", new AutoRecoverEnergyPassive.Specification());
        Role role = b.build();

        List<String> actualOrder = new ArrayList<>(role.getComponents().keySet());
        List<String> expectedOrder = new ArrayList<>(Arrays.asList(IDS));
        expectedOrder.add("c_9_no_slot");
        assertEquals("遍历序必须等于登记序（LinkedHashMap 的语义）", expectedOrder, actualOrder);

 //★ 栏位视图已从聚合根删除 ⇒ 改为断言**条目携带的栏位值**（与渲染组件读描述符同一来源）
        for (int i = 0; i < IDS.length; i++) {
            assertEquals("栏位 " + i + " 必须写在登记时给出的那个条目上",
                    i, role.getComponents().get(IDS[i]).getSlot());
        }
        assertFalse("无栏位者不得带栏位值", role.getComponents().get("c_9_no_slot").hasSlot());
    }

    /** 栏位 0..8 全覆盖：9 个条目都能各占一格。 */
    @Test
    public void everySlotCanBeOccupiedExactlyOnce() {
        Role.Builder b = new Role.Builder("r");
        for (int i = 0; i <= 8; i++) {
            b.addComponent("slot_" + i, new MeiqiheziBloodySlashSkill.Specification().setSlot(i));
        }
        Role role = b.build();
        for (int i = 0; i <= 8; i++) {
            assertEquals("栏位 " + i + " 必须写在条目上", i, role.getComponents().get("slot_" + i).getSlot());
        }
    }

    /** 顺序**不是**按 id 排序出来的：故意用乱序 id 登记，遍历序仍是登记序。 */
    @Test
    public void orderIsInsertionNotAlphabetical() {
        Role.Builder b = new Role.Builder("r");
        b.addComponent("zzz", new MeiqiheziBloodySlashSkill.Specification().setSlot(3));
        b.addComponent("aaa", new MeiqiheziBloodySlashSkill.Specification().setSlot(1));
        b.addComponent("mmm", new MeiqiheziBloodySlashSkill.Specification().setSlot(2));
        Role role = b.build();
        assertEquals("组件表遍历序 = 登记序（不是字母序）",
                Arrays.asList("zzz", "aaa", "mmm"), new ArrayList<>(role.getComponents().keySet()));
 //★ 栏位视图已删除 ⇒ 逐条断言**条目携带的栏位值**
        assertEquals(3, role.getComponents().get("zzz").getSlot());
        assertEquals(1, role.getComponents().get("aaa").getSlot());
        assertEquals(2, role.getComponents().get("mmm").getSlot());
    }

    /** 样本判别力守卫：同一批 id 的 {@link HashMap} 迭代序与登记序**不同** ⇒ 上面对顺序的断言不可能是恒真。 */
    @Test
    public void sampleHasDiscriminatingPowerAgainstHashMap() {
        Map<String, String> hashMap = new HashMap<>();
        Map<String, String> linked = new LinkedHashMap<>();
        for (String id : IDS) {
            hashMap.put(id, id);
            linked.put(id, id);
        }
        List<String> insertion = Arrays.asList(IDS);
        assertEquals("LinkedHashMap 必须保序（这是正向对照）", insertion, new ArrayList<>(linked.keySet()));
        assertNotEquals("该样本在 HashMap 下必须乱序，否则顺序断言失去判别力（请换样本）",
                insertion, new ArrayList<>(hashMap.keySet()));
        assertTrue("HashMap 的键集必须与样本一致（只比顺序，不比内容）",
                hashMap.keySet().containsAll(insertion));
    }
}
