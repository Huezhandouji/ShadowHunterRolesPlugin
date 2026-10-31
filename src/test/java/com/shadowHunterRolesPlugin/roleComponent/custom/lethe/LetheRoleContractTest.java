package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * 冷识（{@code lethe}）重做后的**离线契约测试**：钉住"跨模块约定"里最容易漂移的三件东西。
 *
 * <h2>为什么要有这一套</h2>
 * 冷识的 10 个组件由多道并行开发、按一份书面契约拼装；契约一旦漂移，编译期往往看不出来
 * （id 是字符串、语音时刻是字符串、进化档位是数字），真正出问题的形态是**进游戏后某个技能静默失效**。
 * 因此这里把三组"字符串 / 数字契约"变成编译期 + 测试期钉死的断言：
 * <ol>
 *   <li>10 个组件的注册 id 与契约 §1 逐字一致（{@code RoleLoader} 与玩家指令都按这些 id 找组件）；</li>
 *   <li>进化五档的查询面在正确等级翻转（1 级上限 +50 / 2 级圈上限 8 / 3~5 级只给查询布尔）；</li>
 *   <li>语音的 15 个时刻齐全且每个时刻都有台词（缺了只是"没有声音"，生产上不会报错）。</li>
 * </ol>
 *
 * <p>本类**不碰**任何平台状态：服务集是空壳（三个成员全 {@code null}），只构造组件、只读纯函数与查询面。
 * {@code KeyFactory} 是全局静态且要等插件 {@code onEnable} 才安装，因此本套件自己装桩
 * （与 {@code CapabilityDispatchTest} 同一纪律：谁先跑不该决定本套件能不能跑）。
 */
public class LetheRoleContractTest {

    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    /** 空服务集：本套件只构造组件、不驱动生命周期，三个成员全 null 即可。 */
    private static ComponentServicesPort inertServices() {
        return new ComponentServicesPort(null, null, null);
    }

    /**
     * **组件 id 契约**：10 个 id 与 {@code .lethe-rework/contract.md} §1 逐字一致。
     * <p>这些字符串同时是：{@code RoleLoader} 的装配键、{@code /role operation} 的组件参数、
     * 以及玩家侧调试指令的标识 —— 改一个字就是一次静默的"技能找不到"。
     */
    @Test
    public void componentIdsMatchContract() {
        assertEquals("lethe_resonance_component", LetheResonanceComponent.ID);
        assertEquals("lethe_evolution_passive", LetheEvolutionPassive.ID);
        assertEquals("lethe_quiver_component", LetheQuiverComponent.ID);
        assertEquals("lethe_main_weapon", LetheMainWeapon.ID);
        assertEquals("lethe_forget_skill", LetheForgetSkill.ID);
        assertEquals("lethe_summon_skill", LetheSummonSkill.ID);
        assertEquals("lethe_reshape_skill", LetheReshapeSkill.ID);
        assertEquals("lethe_shadows_passive", LetheShadowsPassive.ID);
        assertEquals("lethe_equipments_passive", LetheEquipmentsPassive.ID);
        assertEquals("lethe_floating_text", LetheFloatingTextComponent.ID);
    }

    /**
     * **进化五档**：等级来源 = 基类的击杀升级；这里直接驱动基类写口，逐级核对查询面翻转。
     * <p>1 / 2 级的两档会真的改协作组件（SanTE 上限、共鸣圈上限），离线没有协作组件 ⇒
     * 这两档在本测试里只验"查询布尔与常量"，真正的写入由 {@code LetheEvolutionPassive#onStart}
     * 在真实实例里做（生产路径）。
     */
    @Test
    public void evolutionTiersFlipAtExpectedLevels() {
        LetheEvolutionPassive evolution =
                new LetheEvolutionPassive("t_evo", inertServices(), new LetheEvolutionPassive.Specification());

        assertEquals("初始等级", 0, evolution.getCurrentEvolutionLevel());
        assertFalse("0 级无上限加成", evolution.hasSanteMaxBonus());
        assertFalse("0 级无圈上限提升", evolution.hasExtendedResonance());
        assertFalse("0 级无「无憾」", evolution.hasFateRecall());
        assertFalse("0 级无「救赎」", evolution.hasReducedReshapeCooldown());
        assertFalse("0 级无「新生」", evolution.hasNewLifeDrain());

        for (int level = 1; level <= LetheEvolutionPassive.MAX_EVOLUTION_LEVEL; level++) {
            assertEquals("升到第 " + level + " 档", level, evolution.increaseEvolutionLevel());
            assertNotNull("第 " + level + " 档必须有文案", LetheEvolutionPassive.tierTextOf(level));
            assertNotNull("第 " + level + " 档必须有升级消息", LetheEvolutionPassive.evolutionMessage(level));
        }

        assertTrue("1 级：特殊值上限 +50", evolution.hasSanteMaxBonus());
        assertEquals("上限增量固定 50", 50, evolution.santeMaxBonus());
        assertTrue("2 级：圈触发上限 8", evolution.hasExtendedResonance());
        assertEquals("提升后的圈上限固定 8", 8, evolution.extendedResonanceCap());
        assertTrue("3 级：无憾", evolution.hasFateRecall());
        assertTrue("4 级：救赎", evolution.hasReducedReshapeCooldown());
        assertTrue("5 级：新生", evolution.hasNewLifeDrain());

        assertNull("0 档没有文案", LetheEvolutionPassive.tierTextOf(0));
        assertNull("越界档位没有文案", LetheEvolutionPassive.tierTextOf(LetheEvolutionPassive.MAX_EVOLUTION_LEVEL + 1));
        assertNull("越界档位没有升级消息", LetheEvolutionPassive.evolutionMessage(0));
    }

    /**
     * **语音契约**：15 个时刻齐全、互不重复，且每个时刻都有非空台词。
     * <p>台词缺失不会抛异常（{@code say} 取不到词会安静返回 {@code false}），
     * 所以只能靠这条测试在离线阶段发现"某个技能永远不出声"。
     */
    @Test
    public void voiceMomentsCoverContractAndEveryMomentHasLines() {
        assertEquals("契约 §3.3 冻结 15 个时刻", 15, LetheFloatingTextComponent.ALL_MOMENTS.size());
        assertEquals("时刻不得重复", 15, new HashSet<>(LetheFloatingTextComponent.ALL_MOMENTS).size());

        List<String> lines = LetheFloatingTextComponent.allLines();
        assertFalse("台词库不能为空", lines.isEmpty());
        for (String line : lines) {
            assertFalse("台词不得为空白", line == null || line.isBlank());
        }
    }

    /**
     * **共鸣组件空态**：新实例不能带任何残留状态（标记 / 圈 / 抬升 / 召回），
     * 且**不依赖服务器时钟**的写口在空态下可安全调用；`liftMarkedArrows` 等运行期入口要读 `Bukkit.getCurrentTick()`，只能在真实服务端环境验（不进离线套件）。
     */
    @Test
    public void resonanceStartsCleanAndWritePortsAreSafeWhenEmpty() {
        LetheResonanceComponent resonance = new LetheResonanceComponent(
                "t_ring", inertServices(), new LetheResonanceComponent.Specification());

        assertEquals("初始无标记", 0, resonance.markedArrowCount());
        assertEquals("初始无共鸣圈", 0, resonance.activeRingCount());
        assertTrue("初始无标记点", resonance.markedArrowPoints().isEmpty());
        assertFalse("初始无抬升箭", resonance.hasLiftedArrows());
        assertFalse("初始不在召回", resonance.isRecalling());

        //纯字段写口：不依赖服务器时钟，空态下必须可安全调用
        resonance.setRingTriggerCap(8);
        resonance.setRingIntervalTicks(20);
        resonance.suspendRingDamage(true);
        resonance.suspendRingDamage(false);
        resonance.clearAll();
        assertEquals("清理后仍无标记", 0, resonance.markedArrowCount());
        assertEquals("清理后仍无共鸣圈", 0, resonance.activeRingCount());
    }
}
