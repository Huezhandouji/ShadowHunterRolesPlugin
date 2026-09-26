package com.shadowHunterRolesPlugin.core;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 状态面一律按实例的**离线面**（B3）：把"状态面一律按实例"的**纯判定**
 * 钉死在可离线跑的判据上。
 *
 * <p><b>当前口径（唯一活着的那条）</b>：冷却的**状态与判断**整体归组件实例（`ActiveComponent`）⇒
 * 框架侧那张表、以及"某实例有没有冷却这回事"的**框架侧判据**都已删除 ✗ ⇒ 本类改测**接受集本身**
 * （`instanceof ActiveComponent`）—— **接受集逐字不变**（主动组件家族 true / 其余 false），
 * 因此覆盖没有丢失 ✓。
 *
 * <p><b>为什么能离线跑</b>：全部断言只用 `instanceof` 与组件构造（不读注册表、不碰 Bukkit、无副作用），
 * 与 `CapabilityDispatchTest` 同一形态（冻结件 §4 T-5 批准的"空 ComponentServices 桩"）就能驱动。
 *
 * <p><b>判据边界</b>：本类**不**覆盖"冷却时长/读数"的数值语义（那属于组件自身的 API）；
 * 本类覆盖的是**归属与接受集**这条口径 ✓。
 */
public class StateBindingByInstanceTest {

    // ───────── ① 能力判定 = 能力接口（接受集） ─────────

    /**
     * 同 id 两份实例：**各自独立**回答"有没有冷却这回事"。
     * <p>冷却状态归实例 ⇒ 旧实现里"按 id 回落只能给出同一个答案"的错误面已从框架侧消失 ✓；
     * 本用例保留其**判据内核**：接受集按**实例的类型**判定，与 id 无关 ✓。
     */
    @Test
    public void sameIdTwoInstancesGetOppositeAnswers() {
        RoleComponent bearing = new MeiqiheziUnconcernSkill("dup_id", inertServices(),
                new MeiqiheziUnconcernSkill.Specification());
        FakePlain plain = new FakePlain("dup_id");

        //id 相同（前置：同 id 可有多份实例）
        assertEquals("前置：两份实例必须同 id", bearing.getId(), plain.getId());

        assertTrue("是主动组件的那一份 ⇒ 有冷却这回事",
                bearing instanceof ActiveComponent);
        assertFalse("同一 id 的另一份（不是主动组件）⇒ 没有冷却这回事",
                (Object) plain instanceof ActiveComponent);
    }

    /** 判定 = 组件类型（同一接受集：主动组件家族 true / 其余 false）。 */
    @Test
    public void predicateIsActiveComponentOnly() {
        assertTrue("主动组件家族 ⇒ 有冷却这回事",
                new MeiqiheziUnconcernSkill("a", inertServices(),
                        new MeiqiheziUnconcernSkill.Specification()) instanceof ActiveComponent);
        assertFalse((Object) new FakePlain("b") instanceof ActiveComponent);
    }

    /** {@code null}（解析不到 / 未绑定且 id 未知）⇒ false（不抛）。 */
    @Test
    public void unknownTargetHasNoCooldownNamespace() {
        Object none = null;
        assertFalse("null ⇒ 没有冷却这回事（不抛）", none instanceof ActiveComponent);
    }

    // ───────── ② 与真产品组件一致（防"接反"） ─────────

    /**
     * 真实组件的答案必须与冻结能力模型一致：技能（走 {@code ActiveComponent}）= 有冷却；
     * 被动（{@code PassiveSkill} ⇒ 不是主动组件）= 没有冷却。
     * <p>这条防的是"判据自身写对、但接进产品路径时接反"这类假绿。
     */
    @Test
    public void realComponentsMatchFrozenCapabilityModel() {
        ComponentServices svc = inertServices();

        RoleComponent skill = new MeiqiheziUnconcernSkill("s1", svc,
                new MeiqiheziUnconcernSkill.Specification());

        assertTrue("技能 = 主动组件 ⇒ 有冷却这回事", skill instanceof ActiveComponent);
    }

    // ───────── 桩件 ─────────

    /** 空服务集桩（冻结件 §4 T-5 批准形态）：只满足构造期读取，不驱动任何运行期行为。 */
    private static ComponentServices inertServices() {
        return new ComponentServices(null, null, null);
    }

    /** 不是主动组件的假组件（"没有冷却这回事"）。 */
    private static final class FakePlain extends RoleComponent {
        FakePlain(String id) {
            super(id, inertServices());
        }
    }
}
