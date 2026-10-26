package com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link MatinaKuangPassive} 里**环绕粒子封顶**那一族的离线穷举单测。
 *
 * <h2>为什么要测</h2>
 * "30 层之后粒子不再增高"是一条**无报错**的需求：不封顶的话 100 层会画 50 颗粒子、
 * 堆到约 6.86 格高（盖准星、远处玩家也渲染 ⇒ 又吵又贵），但**运行期看不出这是 bug**
 * （只是"粒子比预想的多"）。所以把口径钉死在纯函数上。
 *
 * <p>{@code orbitParticleCount} / {@code orbitTopHeight} 是纯函数 ⇒ 不碰 Bukkit。
 * ★ 本测试类只加载 {@link MatinaKuangPassive} 这**一个**组件类（它自身不带
 * {@code static final NamespacedKey}）⇒ 不需要装 KeyFactory 桩。
 */
public class MatinaKuangOrbitTest {

    private static final double EPS = 1e-9;

    // ───────── 粒子数：每 2 层一颗，30 层封顶 ─────────

    @Test
    public void zeroLayersDrawsNothing() {
        assertEquals(0, MatinaKuangPassive.orbitParticleCount(0));
    }

    @Test
    public void negativeLayersDrawsNothing() {
        assertEquals(0, MatinaKuangPassive.orbitParticleCount(-5));
    }

    @Test
    public void oneLayerIsNotEnoughForAParticle() {
        // 每 2 层才加一颗 ⇒ 1 层仍是 0 颗
        assertEquals(0, MatinaKuangPassive.orbitParticleCount(1));
    }

    @Test
    public void everyTwoLayersAddsOneParticle() {
        for (int layers = 0; layers <= 30; layers++) {
            assertEquals("层数 " + layers + " 应画 " + (layers / 2) + " 颗",
                    layers / 2, MatinaKuangPassive.orbitParticleCount(layers));
        }
    }

    @Test
    public void fullLayerIsThirtyAndGivesFifteenParticles() {
        assertEquals(30, MatinaKuangPassive.orbitFullLayer());
        assertEquals(15, MatinaKuangPassive.orbitMaxParticles());
        assertEquals(15, MatinaKuangPassive.orbitParticleCount(30));
    }

    @Test
    public void particlesAreCappedAfterFullLayer() {
        // ★ 核心需求：30 层之后粒子不再增高（数量恒定在满配值）
        int capped = MatinaKuangPassive.orbitMaxParticles();
        for (int layers = 30; layers <= MatinaKuangPassive.KUANG_MAX + 50; layers++) {
            assertEquals("层数 " + layers + " 必须封顶在 " + capped,
                    capped, MatinaKuangPassive.orbitParticleCount(layers));
        }
    }

    @Test
    public void particleCountIsMonotonicNonDecreasing() {
        int prev = 0;
        for (int layers = 0; layers <= MatinaKuangPassive.KUANG_MAX; layers++) {
            int now = MatinaKuangPassive.orbitParticleCount(layers);
            assertTrue("粒子数必须单调不减，层数 = " + layers, now >= prev);
            prev = now;
        }
    }

    // ───────── 高度：30 层恰好 2 格，之后不再长 ─────────

    @Test
    public void topHeightReachesTwoBlocksAtFullLayer() {
        // "30 层时最高 2 格"—— 这是需求钉死的采样点
        assertEquals(2.0d, MatinaKuangPassive.orbitTopHeight(30), 1e-6);
    }

    @Test
    public void topHeightStopsGrowingAfterFullLayer() {
        // ★ 核心需求：到 30 层之后**高度**也不再增高
        double atFull = MatinaKuangPassive.orbitTopHeight(30);
        for (int layers = 31; layers <= MatinaKuangPassive.KUANG_MAX + 50; layers++) {
            assertEquals("层数 " + layers + " 高度不该再涨",
                    atFull, MatinaKuangPassive.orbitTopHeight(layers), EPS);
        }
    }

    @Test
    public void topHeightIsMonotonicNonDecreasing() {
        double prev = -1d;
        for (int layers = 0; layers <= MatinaKuangPassive.KUANG_MAX; layers++) {
            double now = MatinaKuangPassive.orbitTopHeight(layers);
            assertTrue("高度必须单调不减，层数 = " + layers, now >= prev - EPS);
            prev = now;
        }
    }

    @Test
    public void topHeightIsZeroWhenNoParticles() {
        assertEquals(0d, MatinaKuangPassive.orbitTopHeight(0), EPS);
        assertEquals(0d, MatinaKuangPassive.orbitTopHeight(1), EPS);
        assertEquals(0d, MatinaKuangPassive.orbitTopHeight(-3), EPS);
    }

    @Test
    public void singleParticleSitsAtTheOrigin() {
        // 2 层 = 1 颗 ⇒ 贴脚底（高度 0）
        assertEquals(1, MatinaKuangPassive.orbitParticleCount(2));
        assertEquals(0d, MatinaKuangPassive.orbitTopHeight(2), EPS);
    }

    @Test
    public void unstubbedFullLayerWouldOvershootBadly() {
        // 反证：不封顶的算法（层数/2）在 100 层会画 50 颗、堆到 6.86 格
        // ⇒ 本测试把"封顶确实拦住了它"这件事说清楚
        int uncapped = MatinaKuangPassive.KUANG_MAX / 2; // 100 / 2 = 50
        int cappedActual = MatinaKuangPassive.orbitParticleCount(MatinaKuangPassive.KUANG_MAX);
        assertTrue("封顶后必须显著少于不封顶", cappedActual < uncapped);
        assertEquals(15, cappedActual);
    }
}
