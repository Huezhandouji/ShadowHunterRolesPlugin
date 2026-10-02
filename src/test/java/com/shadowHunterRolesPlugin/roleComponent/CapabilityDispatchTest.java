package com.shadowHunterRolesPlugin.roleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.ExampleSelfRefreshingSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.DefaultSanTEZeroPunishment;
import com.shadowHunterRolesPlugin.roleComponent.builtin.AutoRecoverSanTEHealthPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.AutoRecoverEnergyPassive;

import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon.MeiqiheziJuejueMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziBloodySlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziCircleSlashSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedBleedPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedDeeplySorrowSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedEvilShockSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSolitaryArroganceSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.RemotenessEvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.RemotenessFrostBowMainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.RemotenessOblivionSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.RemotenessReconstructSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.RemotenessShapingSkill;
import com.shadowHunterRolesPlugin.roleComponent.custom.remoteness.RemotenessStartEndPassive;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 锁住能力模型的真值表。
 * <p>能单测的原因：断言只问"这个对象实现了哪些能力接口"与"能力自报的真值"，不碰 Bukkit 注册表。
 * 组件的构造器只把 id / 服务集 / 描述符存起来（**不做副作用**），因此可以用一个**空的
 * {@code ComponentServicesPort}** 把它们造出来（服务集从不被读：本测试不调 {@code update()} /
 * {@code buildItem()}）。
 * <p>真值表冻结的是能力模型：谁产出物品 = 主动组件家族（技能 ∪ 主武器）；"外观是否依赖活状态" =
 * 组件自报的 {@code dependsOnLiveState()}（框架不再点名任何具体组件类）。{@code ExampleSelfRefreshingSkill}
 * 故意是"产出物品但外观不依赖活状态"的那一个 —— 它是这个模型存在的理由，本测试把它显式钉住。
 * <p>判据形态：热键栏那三件事已不再是"实现了哪个接口"（那三个接口已删除），而改为
 * "是不是主动组件家族" + "自报的真值"；接受集与旧形态逐字相同（旧接口的唯一实现者就是主动组件家族）。
 */
public class CapabilityDispatchTest {

    /**
     * **离线测试基设**：装一个最小 {@code KeyFactory} 桩。
     * <p>{@code RedEvolutionPassive} 有静态常量 {@code HEALTH_BONUS_KEY}（走 {@code KeyFactory.Registry.of(...)}），
     * 而该实现只在插件 {@code onEnable} 才 install ⇒ 不装桩时连类都加载不了
     * （{@code ExceptionInInitializerError}）。本套件**自己装**，不搭别人的便车
     * （{@code Registry} 是全局静态，谁先跑不该决定本套件能不能跑 —— 这条顺序依赖是实测踩到的）。
     */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    /** 空服务集：三个成员全 null，构造组件时只被存下来（本测试从不读它）。 */
    private static ComponentServicesPort inertServices() {
        return new ComponentServicesPort(null, null, null);
    }

    /** 全部**既有**具体组件（含示例组件）+ 每个组件的冻结期望（是否产出物品、是否依赖活状态）。 */
    private static Map<String, Object> components() {
        ComponentServicesPort svc = inertServices();
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
        out.put("AutoRecoverEnergyPassive", new AutoRecoverEnergyPassive("t_10", svc, new AutoRecoverEnergyPassive.Specification()));
        out.put("AutoRecoverSanTEHealthPassive", new AutoRecoverSanTEHealthPassive("t_11", svc, new AutoRecoverSanTEHealthPassive.Specification()));
        out.put("DefaultSanTEZeroPunishment", new DefaultSanTEZeroPunishment("t_12", svc, new DefaultSanTEZeroPunishment.Specification()));
        out.put("MeiqiheziEquipmentsPassive", new MeiqiheziEquipmentsPassive("t_13", svc, new MeiqiheziEquipmentsPassive.Specification()));
        out.put("RedBleedPassive", new RedBleedPassive("t_14", svc, new RedBleedPassive.Specification()));
        out.put("RedEquipmentsPassive", new RedEquipmentsPassive("t_15", svc, new RedEquipmentsPassive.Specification()));
        out.put("RedEvolutionPassive", new RedEvolutionPassive("t_16", svc, new RedEvolutionPassive.Specification()));
        //冷识（remoteness）一族（2026-10-02 立）：主武器是弓弩族（extends Skill ⇒ 仍是产出物品者）
        out.put("RemotenessEvolutionPassive", new RemotenessEvolutionPassive("t_17", svc, new RemotenessEvolutionPassive.Specification()));
        out.put("RemotenessStartEndPassive", new RemotenessStartEndPassive("t_18", svc, new RemotenessStartEndPassive.Specification()));
        out.put("RemotenessFrostBowMainWeapon", new RemotenessFrostBowMainWeapon("t_19", svc, new RemotenessFrostBowMainWeapon.Specification()));
        out.put("RemotenessOblivionSkill", new RemotenessOblivionSkill("t_20", svc, new RemotenessOblivionSkill.Specification()));
        out.put("RemotenessReconstructSkill", new RemotenessReconstructSkill("t_21", svc, new RemotenessReconstructSkill.Specification()));
        out.put("RemotenessShapingSkill", new RemotenessShapingSkill("t_22", svc, new RemotenessShapingSkill.Specification()));
        return out;
    }

    /** 冻结真值表：`产出物品`（= 主动组件家族）与 `依赖活状态`（= dependsOnLiveState()）。 */
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
            {"RedEquipmentsPassive", "false", "N/A"},
            {"RedEvolutionPassive", "false", "N/A"},
            //冷识一族：两个被动（不产物品）+ 弓弩主武器 + 三个技能（产出物品且外观带冷却秒数）
            {"RemotenessEvolutionPassive", "false", "N/A"},
            {"RemotenessStartEndPassive", "false", "N/A"},
            {"RemotenessFrostBowMainWeapon", "true", "true"},
            {"RemotenessOblivionSkill", "true", "true"},
            {"RemotenessReconstructSkill", "true", "true"},
            {"RemotenessShapingSkill", "true", "true"}
    };

    /** 逐组件比对冻结真值表（22 个具体组件；枚举里没有的面 = 新增组件 ⇒ 本断言会提醒补表）。 */
    @Test
    public void capabilityTruthTableIsFrozen() {
        Map<String, Object> actual = components();
        assertEquals("具体组件数量与冻结表不一致（新增/删除组件都要更新本表）",
                EXPECTED.length, actual.size());
        for (String[] row : EXPECTED) {
            String name = row[0];
            assertTrue("冻结表里的组件在仓内不存在：" + name, actual.containsKey(name));
            Object component = actual.get(name);
            boolean provider = component instanceof ActiveComponent;
            assertEquals(name + " 的『产出物品』能力", Boolean.parseBoolean(row[1]), provider);
            if (provider) {
                boolean live = ((ActiveComponent) component).dependsOnLiveState();
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

    /** 产出物品者 = {技能家族} ∪ {主武器家族}（= 主动组件家族）；被动**不在**其内。 */
    @Test
    public void providersAreExactlyTheActiveComponentFamilies() {
        for (Object component : components().values()) {
            boolean provider = component instanceof ActiveComponent;
            boolean expected = component instanceof Skill || component instanceof MainWeapon;
            assertEquals("产出物品者的集合必须 = 技能家族 ∪ 主武器家族：" + component.getClass().getSimpleName(),
                    expected, provider);
            if (provider) {
                assertNotNull("产出物品者必须提供声明面（specification 非 null —— 图标/显示名/描述/冷却/耗能都住在它上面）",
                        ((ActiveComponent) component).specification());
                assertTrue("能出现在热键栏的组件必须有冷却这回事（主动组件家族的接受集）",
                        component instanceof ActiveComponent);
            } else {
                assertFalse("被动不得进热键栏家族：" + component.getClass().getSimpleName(),
                        component instanceof ActiveComponent);
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
            if (!(component instanceof ActiveComponent)) continue;
            if (!((ActiveComponent) component).dependsOnLiveState()) exceptions.add(e.getKey());
        }
        assertEquals("技能家族里『外观不依赖活状态』的必须恰好是示例那一个（t46 A8②）",
                java.util.Collections.singletonList("ExampleSelfRefreshingSkill"), exceptions);
    }

    /** 技能家族的默认能力 = true；主武器/其它 = 默认 false（`Skill` 是唯一的覆写点）。 */
    @Test
    public void skillFamilyDefaultsToLiveStateAndMainWeaponDoesNot() {
        for (Object component : components().values()) {
            if (!(component instanceof ActiveComponent)) continue;
            boolean live = ((ActiveComponent) component).dependsOnLiveState();
            if (component instanceof MainWeapon) {
                assertFalse("主武器不得声明依赖活状态（A12 边界：冷却名不带秒数）", live);
            }
            if (component instanceof Skill && component.getClass() == MeiqiheziBloodySlashSkill.class) {
                assertTrue("技能家族默认依赖活状态（外观含秒数）", live);
            }
        }
    }
}
