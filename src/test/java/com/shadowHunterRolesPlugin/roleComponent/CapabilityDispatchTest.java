package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.hotbar.CooldownBearing;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItemProviding;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedBleedPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedDeeplySorrowSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvilShockSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSolitaryArroganceSkill;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * T-5（t50 §4）：锁住**能力模型的真值表**（t50 §1 第 ⑪ 条 = **可单测**）。
 * <p>为什么能单测：断言只问"这个对象实现了哪些能力接口"与"能力自报的真值"，不碰 Bukkit 注册表。
 * 组件的构造器只把 id / 服务集 / 描述符存起来（**不做副作用**）⇒ 可以用一个**空的 {@code ComponentServices}**
 * 把它们造出来（服务集从不被读：本测试不调 {@code update()} / {@code buildItem()}）。
 * 冻结件 §4 T-5 明写"本例只需手写一个 ComponentServices 空桩"⇒ 这是被冻结件批准的形态，不是绕过。
 * <p>真值表冻结的是**阶段 8 的能力模型**：谁产出物品 = 能力接口；"外观是否依赖活状态" = 组件**自报**的
 * {@code dependsOnLiveState()}（框架不再点名任何具体组件类）。`ExampleSelfRefreshingSkill`（t46 的 A8②
 * 示例）**故意**是"产出物品但外观不依赖活状态"的那一个 —— 它是这个模型存在的理由，本测试把它显式钉住。
 */
public class CapabilityDispatchTest {

    /** 空服务集：10 个端口全 null，构造组件时只被存下来（本测试从不读它）。 */
    private static ComponentServices inertServices() {
        return new ComponentServices(null, null, null, null, null, null, null, null, null, null);
    }

    /** 全部**既有**具体组件（含 t46 的示例组件）+ 每个组件的冻结期望（是否产出物品、是否依赖活状态）。 */
    private static Map<String, Object> components() {
        ComponentServices svc = inertServices();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("MeiqiheziBloodySlashSkill", new MeiqiheziBloodySlashSkill("t_1", svc, new MeiqiheziBloodySlashSkill.Specification()));
        out.put("MeiqiheziCircleSlashSkill", new MeiqiheziCircleSlashSkill("t_2", svc, new MeiqiheziCircleSlashSkill.Specification()));
        out.put("MeiqiheziUnconcernSkill", new MeiqiheziUnconcernSkill("t_3", svc, new MeiqiheziUnconcernSkill.Specification()));
        out.put("RedDeeplySorrowSkill", new RedDeeplySorrowSkill("t_4", svc, new RedDeeplySorrowSkill.Specification()));
        out.put("RedEvilShockSkill", new RedEvilShockSkill("t_5", svc, new RedEvilShockSkill.Specification()));
        out.put("RedSolitaryArroganceSkill", new RedSolitaryArroganceSkill("t_6", svc, new RedSolitaryArroganceSkill.Specification()));
        out.put("ExampleSelfRefreshingSkill", new ExampleSelfRefreshingSkill("t_7", svc, new ExampleSelfRefreshingSkill.Specification()));
        out.put("MeiqiheziJuejueMainWeapon", new MeiqiheziJuejueMainWeapon("t_8", svc, new MeiqiheziJuejueMainWeapon.Specification()));
        out.put("RedSanctifiedBladeMainWeapon", new RedSanctifiedBladeMainWeapon("t_9", svc, new RedSanctifiedBladeMainWeapon.Specification()));
        out.put("AutoRecoverEnergyPassive", new AutoRecoverEnergyPassive("t_10", svc));
        out.put("AutoRecoverSanTEHealthPassive", new AutoRecoverSanTEHealthPassive("t_11", svc));
        out.put("DefaultSanTEZeroPunishment", new DefaultSanTEZeroPunishment("t_12", svc));
        out.put("MeiqiheziEquipmentsPassive", new MeiqiheziEquipmentsPassive("t_13", svc));
        out.put("RedBleedPassive", new RedBleedPassive("t_14", svc));
        out.put("RedEquipmentsPassive", new RedEquipmentsPassive("t_15", svc));
        return out;
    }

    /** 冻结真值表：`产出物品`（= HotbarItemProviding）与 `依赖活状态`（= dependsOnLiveState()）。 */
    private static final String[][] EXPECTED = {
            // 组件名, 是否产出物品, 是否依赖活状态（"N/A" = 不产出物品 ⇒ 该方法不存在）
            {"MeiqiheziBloodySlashSkill", "true", "true"},
            {"MeiqiheziCircleSlashSkill", "true", "true"},
            {"MeiqiheziUnconcernSkill", "true", "true"},
            {"RedDeeplySorrowSkill", "true", "true"},
            {"RedEvilShockSkill", "true", "true"},
            {"RedSolitaryArroganceSkill", "true", "true"},
            {"ExampleSelfRefreshingSkill", "true", "false"},
            {"MeiqiheziJuejueMainWeapon", "true", "false"},
            {"RedSanctifiedBladeMainWeapon", "true", "false"},
            {"AutoRecoverEnergyPassive", "false", "N/A"},
            {"AutoRecoverSanTEHealthPassive", "false", "N/A"},
            {"DefaultSanTEZeroPunishment", "false", "N/A"},
            {"MeiqiheziEquipmentsPassive", "false", "N/A"},
            {"RedBleedPassive", "false", "N/A"},
            {"RedEquipmentsPassive", "false", "N/A"}
    };

    /** 逐组件比对冻结真值表（15 个具体组件；枚举里没有的面 = 新增组件 ⇒ 本断言会提醒补表）。 */
    @Test
    public void capabilityTruthTableIsFrozen() {
        Map<String, Object> actual = components();
        assertEquals("具体组件数量与冻结表不一致（新增/删除组件都要更新本表）",
                EXPECTED.length, actual.size());
        for (String[] row : EXPECTED) {
            String name = row[0];
            assertTrue("冻结表里的组件在仓内不存在：" + name, actual.containsKey(name));
            Object component = actual.get(name);
            boolean provider = component instanceof HotbarItemProviding;
            assertEquals(name + " 的『产出物品』能力", Boolean.parseBoolean(row[1]), provider);
            if (provider) {
                boolean live = ((HotbarItemProviding) component).dependsOnLiveState();
                assertEquals(name + " 的『外观依赖活状态』能力", Boolean.parseBoolean(row[2]), live);
            } else {
                assertEquals(name + " 不产出物品 ⇒ 无该能力（冻结表写 N/A）", "N/A", row[2]);
            }
        }
        List<String> names = new ArrayList<>(actual.keySet());
        for (String[] row : EXPECTED) {
            assertTrue("冻结表里的组件在枚举里缺失：" + row[0], names.contains(row[0]));
        }
    }

    /** 产出物品者 = {技能家族} ∪ {主武器家族}（= `HotbarPresentable` 簇）；被动**不在**簇内。 */
    @Test
    public void providersAreExactlyTheActiveComponentFamilies() {
        for (Object component : components().values()) {
            boolean provider = component instanceof HotbarItemProviding;
            boolean expected = component instanceof Skill || component instanceof MainWeapon;
            assertEquals("产出物品者的集合必须 = 技能家族 ∪ 主武器家族：" + component.getClass().getSimpleName(),
                    expected, provider);
            if (provider) {
                assertTrue("产出物品者必须在 HotbarPresentable 簇内（能力簇四合一）",
                        component instanceof HotbarPresentable);
                assertTrue("能出现在热键栏的组件必须有冷却能力（isCooling 的接受集）",
                        component instanceof CooldownBearing);
            } else {
                assertFalse("被动不得进热键栏能力簇：" + component.getClass().getSimpleName(),
                        component instanceof HotbarPresentable);
            }
        }
    }

    /** A8② 的显式例外：**技能家族里**唯一"外观不依赖活状态"的就是那个示例（它是能力模型存在的理由）；
     *  主武器不是技能族（它按 A12 边界本来就 false，由另一个测试单独钉住）。 */
    @Test
    public void onlyTheExampleOverridesTheSkillFamilyDefaultToFalse() {
        List<String> exceptions = new ArrayList<>();
        for (Map.Entry<String, Object> e : components().entrySet()) {
            Object component = e.getValue();
            if (!(component instanceof Skill)) continue;
            if (!(component instanceof HotbarItemProviding)) continue;
            if (!((HotbarItemProviding) component).dependsOnLiveState()) exceptions.add(e.getKey());
        }
        assertEquals("技能家族里『外观不依赖活状态』的必须恰好是示例那一个（t46 A8②）",
                java.util.Collections.singletonList("ExampleSelfRefreshingSkill"), exceptions);
    }

    /** 技能家族的默认能力 = true；主武器/其它 = 默认 false（`Skill` 是唯一的覆写点）。 */
    @Test
    public void skillFamilyDefaultsToLiveStateAndMainWeaponDoesNot() {
        for (Object component : components().values()) {
            if (!(component instanceof HotbarItemProviding)) continue;
            boolean live = ((HotbarItemProviding) component).dependsOnLiveState();
            if (component instanceof MainWeapon) {
                assertFalse("主武器不得声明依赖活状态（A12 边界：冷却名不带秒数）", live);
            }
            if (component instanceof Skill && component.getClass() == MeiqiheziBloodySlashSkill.class) {
                assertTrue("技能家族默认依赖活状态（外观含秒数）", live);
            }
        }
    }
}
