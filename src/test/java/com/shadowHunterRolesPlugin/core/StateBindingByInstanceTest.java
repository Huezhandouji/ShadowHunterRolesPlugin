package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.core.hotbar.CooldownBearing;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.passive.MeiqiheziEquipmentsPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill.MeiqiheziUnconcernSkill;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * t84（阶段 11 · 三处 medium 修复 β₁）的**离线面**（B3）：把"状态面一律按实例"的两个**纯判定**
 * 钉死在可离线跑的判据上 —— 它们正是 F-1（冷却能力判定）/ F-2（冷却结束派发目标）/ F-3（计时请求者）
 * 三条 medium 的**唯一裁决点**。
 *
 * <p><b>为什么能离线跑</b>：本卡抽出的两个 helper（{@link RoleInstance#hasCooldownNamespace(RoleComponent)}、
 * {@link RoleInstance#preferBound(RoleComponent, RoleComponent)}）都是**静态纯函数**：不读注册表、
 * 不碰 Bukkit、无副作用 ⇒ 与 `CapabilityDispatchTest` 同一形态（冻结件 §4 T-5 批准的"空 ComponentServices 桩"）
 * 就能驱动。
 *
 * <p><b>本测试钉住的三件事</b>：
 * <ol>
 *   <li><b>判定按实例</b>：同 id 两份实例（一份 {@code CooldownBearing}、一份不是）必须得到**相反**的答案
 *       —— 旧口径（按 id 取"添加顺序第一个"）在这组输入上**物理上给不出两个答案** ✗，这就是 F-1 的根因；</li>
 *   <li><b>回落保留</b>：未绑定时取按 id 解析的实例（可为 {@code null}），**不得静默丢弃**；</li>
 *   <li><b>与真组件一致</b>：真实产品组件（技能 = 有冷却 / 被动 = 没有冷却）在本判定下的答案与冻结能力模型一致
 *       —— 防止"helper 自己写对、接进产品路径时接反"。</li>
 * </ol>
 */
public class StateBindingByInstanceTest {

    /** 空服务集：10 个端口全 null，构造组件时只被存下来（本测试从不读它）。 */
    private static ComponentServices inertServices() {
        return new ComponentServices(null, null, null, null, null, null, null, null, null, null);
    }

    /** 有冷却的假组件（最小实现：只声明能力）。 */
    private static class FakeBearing extends RoleComponent implements CooldownBearing {
        FakeBearing(String id) {
            super(id, inertServices());
        }

        @Override
        public int getCooldownTicks() {
            return 20;
        }

        @Override
        public boolean isCooling() {
            return false;
        }
    }

    /** 没有冷却的假组件（不实现能力接口）。 */
    private static class FakePlain extends RoleComponent {
        FakePlain(String id) {
            super(id, inertServices());
        }
    }

    // ───────── ① 判定按实例（F-1 的核心） ─────────

    /**
     * **同 id 两份实例**：一份实现 {@code CooldownBearing}、一份不实现 ⇒ 判定必须给出**相反**答案。
     * <p>这是 F-1 的区分力证明：按 id 的旧口径对这两个实例只能返回**同一个**答案（第一个同 id 者）
     * ⇒ 必然有一份判错 ✗。
     */
    @Test
    public void sameIdTwoInstancesGetOppositeAnswers() {
        FakeBearing bearing = new FakeBearing("dup_id");
        FakePlain plain = new FakePlain("dup_id");

        //id 相同（前置：t67 起同 id 可有多份实例）
        assertEquals("前置：两份实例必须同 id", bearing.getId(), plain.getId());

        assertTrue("实现了 CooldownBearing 的那一份 ⇒ 有冷却这回事",
                RoleInstance.hasCooldownNamespace(bearing));
        assertFalse("同一 id 的另一份（未实现能力接口）⇒ 没有冷却这回事",
                RoleInstance.hasCooldownNamespace(plain));
    }

    /** 判定 = 能力接口（同一接受集：实现者 true / 未实现者 false）。 */
    @Test
    public void predicateIsCapabilityInterfaceOnly() {
        assertTrue(RoleInstance.hasCooldownNamespace(new FakeBearing("a")));
        assertFalse(RoleInstance.hasCooldownNamespace(new FakePlain("b")));
    }

    /** {@code null}（解析不到 / 未绑定且 id 未知）⇒ false（与改前的未知 id 口径逐字一致）。 */
    @Test
    public void unknownTargetHasNoCooldownNamespace() {
        assertFalse("null ⇒ 没有冷却这回事（不抛）",
                RoleInstance.hasCooldownNamespace(null));
    }

    // ───────── ② 回落保留（不得静默丢弃） ─────────

    /** 已绑定 ⇒ **绑定实例**胜出（哪怕按 id 回落到的是另一个同 id 实例）。 */
    @Test
    public void boundInstanceWinsOverIdFallback() {
        FakeBearing bound = new FakeBearing("dup_id");
        FakePlain byId = new FakePlain("dup_id");

        assertSame("绑定实例优先（F-1/F-2/F-3 的唯一裁决点）",
                bound, RoleInstance.preferBound(bound, byId));
    }

    /** 未绑定（{@code null}）⇒ 按 id 回落的实例（**保留回落**：构造早于组件、框架级服务组件都靠它）。 */
    @Test
    public void unboundFallsBackToIdResolution() {
        FakePlain byId = new FakePlain("energy");
        assertSame("未绑定 ⇒ 回落实例（不静默丢弃）",
                byId, RoleInstance.preferBound(null, byId));
    }

    /** 两边都没有 ⇒ {@code null}（下游按"解析不到就什么都不做"处理，不抛）。 */
    @Test
    public void nothingToResolveYieldsNull() {
        assertNull(RoleInstance.preferBound(null, null));
    }

    // ───────── ③ 与真产品组件一致（防"接反"） ─────────

    /**
     * 真实组件的答案必须与冻结能力模型一致：技能（走 {@code ActiveComponent} ⇒ 实现 {@code CooldownBearing}）
     * = 有冷却；被动（{@code PassiveSkill} ⇒ 不实现）= 没有冷却。
     * <p>这条防的是"helper 自身写对、但接进产品路径时判据接反"这类假绿。
     */
    @Test
    public void realComponentsMatchFrozenCapabilityModel() {
        ComponentServices svc = inertServices();

        RoleComponent skill = new MeiqiheziUnconcernSkill("s1", svc,
                new MeiqiheziUnconcernSkill.Specification());
        RoleComponent passive = new MeiqiheziEquipmentsPassive("p1", svc);

        assertTrue("技能 = 有冷却这回事", RoleInstance.hasCooldownNamespace(skill));
        assertFalse("被动 = 没有冷却这回事（不写表 / 不派发 / 不置脏）",
                RoleInstance.hasCooldownNamespace(passive));
    }
}
