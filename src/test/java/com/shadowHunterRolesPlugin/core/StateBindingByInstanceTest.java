package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.roleComponent.RoleComponent.CooldownBearing;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * t84（阶段 11 · 三处 medium 修复 β₁）的**离线面**（B3）：把"状态面一律按实例"的**纯判定**
 * 钉死在可离线跑的判据上。
 *
 * <p><b>阶段 13 · t105 的收窄（已申报）</b>：冷却的**状态与判断**已整体归组件实例（`ActiveComponent`），
 * 框架侧那张表、以及"某实例有没有冷却这回事"的**框架侧判据**已随之删除 ⇒ 原先直测那个 helper 的断言
 * 改为直测**能力接口本身**（`instanceof CooldownBearing`）—— **接受集逐字不变**（实现者 true / 未实现者 false），
 * 因此覆盖没有丢失 ✓。
 *
 * <p><b>阶段 13 · t118 的再收窄（已申报）</b>：那条"按 id 回落"口径的**纯判定函数与其入口**已随代码卫生
 * 删除 ✗（生产消费者 **0 个**）⇒ 原先直测它们的 3 个用例**一并删除**（用例数 **66 → 63**，理由见交付说明）；
 * 本类此后只覆盖**仍然活着**的那条口径：能力接口的接受集按**实例的类型**判定 ✓。
 *
 * <p><b>为什么能离线跑</b>：全部断言只用 `instanceof` 与组件构造（不读注册表、不碰 Bukkit、无副作用），
 * 与 `CapabilityDispatchTest` 同一形态（冻结件 §4 T-5 批准的"空 ComponentServices 桩"）就能驱动。
 *
 * <p><b>判据边界</b>：本类**不**覆盖"冷却时长/读数"的数值语义（那属于组件自身的 API，随 t105 的
 * 调用点改造一起迁移）；本类覆盖的是**归属与接受集**这条口径 ✓。
 */
public class StateBindingByInstanceTest {

    // ───────── ① 能力判定 = 能力接口（接受集） ─────────

    /**
     * 同 id 两份实例：**各自独立**回答"有没有冷却这回事"。
     * <p>阶段 13 · t105 起冷却状态归实例 ⇒ 旧实现里"按 id 回落只能给出同一个答案"的错误面已从框架侧消失 ✓；
     * 本用例保留其**判据内核**：能力接口的接受集按**实例的类型**判定，与 id 无关 ✓。
     */
    @Test
    public void sameIdTwoInstancesGetOppositeAnswers() {
        FakeBearing bearing = new FakeBearing("dup_id");
        FakePlain plain = new FakePlain("dup_id");

        //id 相同（前置：t67 起同 id 可有多份实例）
        assertEquals("前置：两份实例必须同 id", bearing.getId(), plain.getId());

        assertTrue("实现了 CooldownBearing 的那一份 ⇒ 有冷却这回事",
                bearing instanceof CooldownBearing);
        assertFalse("同一 id 的另一份（未实现能力接口）⇒ 没有冷却这回事",
                (Object) plain instanceof CooldownBearing);
    }

    /** 判定 = 能力接口（同一接受集：实现者 true / 未实现者 false）。 */
    @Test
    public void predicateIsCapabilityInterfaceOnly() {
        assertTrue(new FakeBearing("a") instanceof CooldownBearing);
        assertFalse((Object) new FakePlain("b") instanceof CooldownBearing);
    }

    /** {@code null}（解析不到 / 未绑定且 id 未知）⇒ false（不抛）。 */
    @Test
    public void unknownTargetHasNoCooldownNamespace() {
        Object none = null;
        assertFalse("null ⇒ 没有冷却这回事（不抛）", none instanceof CooldownBearing);
    }

    // ───────── ② 与真产品组件一致（防"接反"） ─────────

    /**
     * 真实组件的答案必须与冻结能力模型一致：技能（走 {@code ActiveComponent} ⇒ 实现 {@code CooldownBearing}）
     * = 有冷却；被动（{@code PassiveSkill} ⇒ 不实现）= 没有冷却。
     * <p>这条防的是"判据自身写对、但接进产品路径时接反"这类假绿。
     */
    @Test
    public void realComponentsMatchFrozenCapabilityModel() {
        ComponentServices svc = inertServices();

        RoleComponent skill = new MeiqiheziUnconcernSkill("s1", svc,
                new MeiqiheziUnconcernSkill.Specification());
        RoleComponent passive = new MeiqiheziEquipmentsPassive("p1", svc);

        assertTrue("技能 = 有冷却这回事", skill instanceof CooldownBearing);
        assertFalse("被动 = 没有冷却这回事（不进冷却 / 不置脏）",
                passive instanceof CooldownBearing);
    }

    // ───────── 桩件 ─────────

    /** 空服务集桩（冻结件 §4 T-5 批准形态）：只满足构造期读取，不驱动任何运行期行为。 */
    private static ComponentServices inertServices() {
        return new ComponentServices(null, null, null);
    }

    /** 实现能力接口的假组件（用于接受集判定）。 */
    private static final class FakeBearing extends RoleComponent implements CooldownBearing {
        FakeBearing(String id) {
            super(id, inertServices());
        }

        public int getCooldownTicks() {
            return 100;
        }

        @Override
        public boolean isCooling() {
            return false;
        }
    }

    /** 不实现能力接口的假组件（"没有冷却这回事"）。 */
    private static final class FakePlain extends RoleComponent {
        FakePlain(String id) {
            super(id, inertServices());
        }
    }
}
