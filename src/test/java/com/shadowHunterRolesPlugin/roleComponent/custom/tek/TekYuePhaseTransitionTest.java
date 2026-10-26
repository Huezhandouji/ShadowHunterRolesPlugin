package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「特克」技能二（落岳）**两段式释放**的离线单测
 * （{@link TekYueSkill#phaseAfterCast(TekYueSkill.Phase)}）。
 *
 * <h2>需求</h2>
 * <ol>
 *   <li><b>直接释放</b>：向上跃起，随后<b>正常原版下落</b>（交给重力，期间获得<b>抗性 II</b>）；
 *       下落完毕后技能才进冷却；</li>
 *   <li><b>在空中再次释放</b>：改为<b>加速下落</b>；下落完毕后技能才进冷却。</li>
 * </ol>
 *
 * <h2>为什么值得测</h2>
 * "再次释放 ⇒ 加速"这条迁移很容易写成"只要不在役就跃起"，
 * 或写成"在空中再按就重置回跃起"（那玩家可以一直往上飞）。
 * 而且它的错误在运行期**不报错**，只表现为"按了没用"或"越按越高"。
 * 把它做成纯函数后可以穷举掉全部四种输入。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li>本测试只覆盖**阶段迁移这张表**与**两档步长的相对关系**；</li>
 *   <li><b>不</b>覆盖：{@code teleport} 位移、漂浮药水、摔落距离清零、
 *       "下落完毕才起冷却"（在 {@code impact()} / {@code endWithoutImpact()} 里，要服务端）；
 *   <li><b>不</b>覆盖：落地判据与地面扫描（那是 {@code TekYueLandingTest} 的职责）。</li>
 * </ul>
 */
public class TekYuePhaseTransitionTest {

    private static TekYueSkill.Phase after(TekYueSkill.Phase current) {
        return TekYueSkill.phaseAfterCast(current);
    }

    // ───────── 需求第 1 条：直接释放 ⇒ 跃起 ─────────

    @Test
    public void 在地面释放_应当跃起() {
        assertEquals(TekYueSkill.Phase.RISING, after(TekYueSkill.Phase.IDLE));
    }

    // ───────── 需求第 2 条：空中再次释放 ⇒ 加速下落 ─────────

    @Test
    public void 跃起中再释放_应当转为加速下落() {
        assertEquals("跃起段中途再按 ⇒ 立刻转加速下落（不是重新跃起）",
                TekYueSkill.Phase.FALLING_FAST, after(TekYueSkill.Phase.RISING));
    }

    @Test
    public void 慢速下落中再释放_应当转为加速下落() {
        assertEquals("不加速下落途中再按 ⇒ 切加速",
                TekYueSkill.Phase.FALLING_FAST, after(TekYueSkill.Phase.FALLING_NORMAL));
    }

    // ───────── 幂等：已在加速再按无效 ─────────

    @Test
    public void 已在加速下落时再释放_应当没有变化() {
        assertEquals("已经在加速档 ⇒ 再按无效（幂等），返回值必须等于入参",
                TekYueSkill.Phase.FALLING_FAST, after(TekYueSkill.Phase.FALLING_FAST));
    }

    // ───────── 穷举全表 ─────────

    @Test
    public void 全表穷举_只有地面会跃起() {
        int risingCount = 0;
        for (TekYueSkill.Phase current : TekYueSkill.Phase.values()) {
            TekYueSkill.Phase next = after(current);
            if (next == TekYueSkill.Phase.RISING) {
                risingCount++;
                assertEquals("只有 IDLE 能跃起", TekYueSkill.Phase.IDLE, current);
            } else {
                assertEquals("非地面态再释放一律转加速下落",
                        TekYueSkill.Phase.FALLING_FAST, next);
            }
        }
        assertEquals("四个阶段里恰好只有一个入口指向跃起", 1, risingCount);
    }

    @Test
    public void 迁移结果永远落在合法阶段上() {
        for (TekYueSkill.Phase current : TekYueSkill.Phase.values()) {
            TekYueSkill.Phase next = after(current);
            assertTrue("结果不得为 null", next != null);
            assertNotEquals("迁移不得产生 IDLE（冷却/收工才回 IDLE）",
                    TekYueSkill.Phase.IDLE, next);
        }
    }

    // ───────── 加速档与抗性（防"档位写成一样"/"抗性等级写错"）─────────

    @Test
    public void 加速档必须比原版自由落体更快() {
        //需求：第一档 = 正常原版下落（交给重力），第二档 = 加速下落。
        //⇒ 加速档必须明显快于"原版从 15 格落下的平均速度"，否则玩家按了没感觉。
        //原版从 15 格落到约需 20 刻（均速 ≈ 0.75 格/刻），取 2.0 作保守下界。
        double fast = TekYueSkill.fallFastPerTickForTest();
        assertTrue("加速档应当显著快于原版自由落体均速，实际 = " + fast, fast >= 2.0d);
    }

    @Test
    public void 抗性必须是二级() {
        //需求："下落时给个抗性2"。原版增幅从 0 计 ⇒ 1 = 抗性提升 II。
        assertEquals("抗性等级必须恰好是 II（增幅 1）", 1, TekYueSkill.resistanceAmplifierForTest());
    }
}
