package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「特克」主武器**蓄力状态机**的离线单测（{@link TekTridentMainWeapon#decideCharge}）。
 *
 * <h2>为什么值得测</h2>
 * "按住右键 0.8 秒 → <b>松手</b>才突刺"这条需求在 Bukkit 上没有现成信号
 * （没有右键松开事件），落地方式绕了两层：
 * <ol>
 *   <li>我们主动 {@code startUsingItem} 把"使用中"状态起起来 ⇒ 才有举枪姿势与
 *       {@code isHandRaised()} 这个可靠判据；起不来则降级到 click-chatter + 间隙；</li>
 *   <li>还要用"每 6 刻刷新一次使用状态"把"已使用刻数"压在 10 以下，
 *       否则原版三叉戟的 {@code releaseUsing} 会把武器**投掷出去**。</li>
 * </ol>
 * 这两层里任何一处写错，运行期症状都是"点不出来 / 一直不出手 / 武器被扔出去"，
 * 而**都没有报错**。判定与阈值因此在这里被钉住。
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li><b>不</b>覆盖：{@code isHandRaised()} 是否真的生效（要真实客户端 + 服务端），
 *       本测试只保证"给它什么输入就得到什么判定"；</li>
 *   <li><b>不</b>覆盖：原版 {@code TridentItem.releaseUsing} 的真实行为 ——
 *       本测试只用"HOLD_REFRESH_AT_TICKS 必须 &lt; 10"把那条保护钉在阈值层
 *       （10 刻是原版投掷分支的门槛，见组件里 {@code HOLD_REFRESH_AT_TICKS} 的 javadoc）。</li>
 * </ul>
 */
public class TekChargeDecisionTest {

    /** 实现里的阈值（从组件读，避免测试硬编码魔数与实现漂移）。 */
    private static final int CHARGE_TICKS = TekTridentMainWeapon.chargeTicksForTest();
    private static final int GAP = TekTridentMainWeapon.releaseGapTicksForTest();
    private static final int MAX_TICKS = TekTridentMainWeapon.chargeMaxTicksForTest();
    private static final int REFRESH_AT = TekTridentMainWeapon.holdRefreshAtTicksForTest();

    private static TekTridentMainWeapon.ChargeDecision decide(int ticks, boolean armed, boolean released) {
        return TekTridentMainWeapon.decideCharge(ticks, armed, released);
    }

    // ───────── 阈值自身的健全性 ─────────

    @Test
    public void 阈值合理() {
        assertEquals("0.8 秒 = 16 刻", 16, CHARGE_TICKS);
        assertTrue("间隙阈值必须大于客户端重复派发周期(约 4 刻)，否则会误判松手", GAP > 4);
        assertEquals("防呆上限 = 蓄满 + 40 刻", CHARGE_TICKS + 40, MAX_TICKS);
    }

    @Test
    public void 刷新节拍必须小于十刻() {
        //★ 最关键的一条：原版三叉戟 releaseUsing 的门槛是"已使用 ≥ 10 刻则投掷武器"。
        //  刷新节拍必须严格小于 10，否则松手时原版会把武器扔出去。
        assertTrue("HOLD_REFRESH_AT_TICKS 必须 < 10（原版投掷门槛），实际 = " + REFRESH_AT,
                REFRESH_AT < 10);
        assertTrue("刷新节拍还得 > 0，否则每刻刷新没有意义", REFRESH_AT > 0);
    }

    // ───────── 松手是决定性输入 ─────────

    @Test
    public void 未蓄满就松手_应作废() {
        assertEquals(TekTridentMainWeapon.ChargeDecision.ABORT,
                decide(6, false, true));
    }

    @Test
    public void 恰好蓄满后松手_应出手() {
        assertEquals(TekTridentMainWeapon.ChargeDecision.FIRE,
                decide(CHARGE_TICKS, true, true));
    }

    @Test
    public void 蓄满后仍按着_应等待而非出手() {
        //★ 这条是"松手才出手"的核心判据：满了但不松手 ⇒ 不出手
        assertEquals(TekTridentMainWeapon.ChargeDecision.CONTINUE,
                decide(CHARGE_TICKS, true, false));
    }

    @Test
    public void 蓄满但一直不松手_到上限强制出手() {
        assertEquals(TekTridentMainWeapon.ChargeDecision.FIRE,
                decide(MAX_TICKS, true, false));
    }

    @Test
    public void 未满且仍按着_应继续蓄力() {
        assertEquals(TekTridentMainWeapon.ChargeDecision.CONTINUE,
                decide(1, false, false));
        assertEquals(TekTridentMainWeapon.ChargeDecision.CONTINUE,
                decide(CHARGE_TICKS - 1, false, false));
    }

    @Test
    public void 上限之前一帧仍在等待() {
        assertEquals(TekTridentMainWeapon.ChargeDecision.CONTINUE,
                decide(MAX_TICKS - 1, true, false));
    }

    // ───────── 无论哪条路径都不能卡死 ─────────

    @Test
    public void 每条路径都有出口() {
        //① 正常路径：蓄满 → 松手
        assertTrue(decide(CHARGE_TICKS, true, true) == TekTridentMainWeapon.ChargeDecision.FIRE);
        //② 防呆路径：一直不松手
        assertTrue(decide(MAX_TICKS, true, false) == TekTridentMainWeapon.ChargeDecision.FIRE);
        //③ 提前松手
        assertTrue(decide(CHARGE_TICKS - 1, false, true) == TekTridentMainWeapon.ChargeDecision.ABORT);
    }

    @Test
    public void 作废与出手互斥且与是否蓄满一致() {
        //松手时：蓄满才出手，没蓄满一律作废（不允许出现"没满还出手"）
        for (int ticks = 0; ticks <= MAX_TICKS + 5; ticks++) {
            boolean armed = ticks >= CHARGE_TICKS;
            TekTridentMainWeapon.ChargeDecision d = decide(ticks, armed, true);
            if (armed) {
                assertTrue("ticks=" + ticks + " 蓄满松手应出手", d == TekTridentMainWeapon.ChargeDecision.FIRE);
            } else {
                assertTrue("ticks=" + ticks + " 未满松手应作废", d == TekTridentMainWeapon.ChargeDecision.ABORT);
            }
        }
    }
}
