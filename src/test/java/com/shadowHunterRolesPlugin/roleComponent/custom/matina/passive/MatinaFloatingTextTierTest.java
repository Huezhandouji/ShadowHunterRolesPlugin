package com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive;

import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.datapack.FloatingTextComponent;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * {@link MatinaFloatingTextComponent} 的**语气分档**与**外观随档位变化**的离线单测。
 *
 * <h2>为什么要测</h2>
 * "层数越高越有攻击性 / 班味，越低越有神性 / 平静"是需求原话，但它是**无报错**的行为：
 * 分档写错只会"台词一直是一个口气"，运行期很难察觉。这里把分档边界、
 * 三档词库互不相同、以及外观（颜色 / 加粗）随档位切换这三件事钉死。
 *
 * <h2>★ KeyFactory 桩</h2>
 * {@link MatinaFloatingTextComponent} 继承链上会碰到带 {@code static final NamespacedKey}
 * 的框架类 ⇒ 离线 JVM 里必须先装 {@code KeyFactory} 桩（否则类初始化抛
 * {@code ExceptionInInitializerError}，且 JVM 会把失败**缓存住**，事后补装也救不回来）。
 */
public class MatinaFloatingTextTierTest {

    @BeforeClass
    public static void installKeyFactoryStub() {
        // 幂等安装：生产里 onEnable 会先装，离线测试里没人装 ⇒ 由本测试类兜住
        com.shadowHunterRolesPlugin.platform.KeyFactory.Registry.install(
                key -> new org.bukkit.NamespacedKey("shadowhunterroles", key));
    }

    // ───────── 分档边界（纯函数） ─────────

    @Test
    public void zeroLayersIsCalmTier() {
        assertEquals(MatinaFloatingTextComponent.Tier.CALM,
                MatinaFloatingTextComponent.tierOf(0));
    }

    @Test
    public void calmBoundaryIsInclusive() {
        assertEquals(MatinaFloatingTextComponent.Tier.CALM,
                MatinaFloatingTextComponent.tierOf(MatinaFloatingTextComponent.TIER_CALM_MAX));
    }

    @Test
    public void justAboveCalmBoundaryIsStrained() {
        assertEquals(MatinaFloatingTextComponent.Tier.STRAINED,
                MatinaFloatingTextComponent.tierOf(MatinaFloatingTextComponent.TIER_CALM_MAX + 1));
    }

    @Test
    public void justBelowFrenzyBoundaryIsStrained() {
        assertEquals(MatinaFloatingTextComponent.Tier.STRAINED,
                MatinaFloatingTextComponent.tierOf(MatinaFloatingTextComponent.TIER_ANGRY_MIN - 1));
    }

    @Test
    public void frenzyBoundaryIsInclusive() {
        assertEquals(MatinaFloatingTextComponent.Tier.FRENZY,
                MatinaFloatingTextComponent.tierOf(MatinaFloatingTextComponent.TIER_ANGRY_MIN));
    }

    @Test
    public void negativeLayersTreatedAsCalm() {
        assertEquals(MatinaFloatingTextComponent.Tier.CALM,
                MatinaFloatingTextComponent.tierOf(-10));
    }

    @Test
    public void thresholdsAreOrdered() {
        assertTrue("平静上限必须低于狂暴下限",
                MatinaFloatingTextComponent.TIER_CALM_MAX < MatinaFloatingTextComponent.TIER_ANGRY_MIN);
        assertTrue("狂暴下限不该超过狂暴值上限",
                MatinaFloatingTextComponent.TIER_ANGRY_MIN <= MatinaKuangPassive.KUANG_MAX);
    }

    @Test
    public void tierNeverRegressesAsLayersGrow() {
        // 单调性：层数只增不减 ⇒ 档位只能"更躁"，不该回退
        int prev = -1;
        for (int layers = 0; layers <= MatinaKuangPassive.KUANG_MAX; layers++) {
            int now = MatinaFloatingTextComponent.tierOf(layers).ordinal();
            assertTrue("档位必须单调不降，层数 = " + layers, now >= prev);
            prev = now;
        }
    }

    // ───────── 加粗（"改得更加明显点"） ─────────

    @Test
    public void everyTierMakesTextBold() {
        // 需求"给其文本加粗"⇒ 三档都必须粗（只是颜色 / 抖动不同）
        assertTrue("愤怒档必须加粗", TextUtil.Look.gradientOf(0xFF3A20).withTremble().withBold().bold());
        assertTrue("神性档必须加粗", TextUtil.Look.glowOf(0xFFFFFF).withBold().bold());
        assertTrue("平静档也必须加粗", TextUtil.Look.colored(0xE8E4FF).withBold().bold());
    }

    // ───────── 风格：全时刻、全档位统一米塔滚地 ─────────

    /**
     * ★ 探针：把 {@code protected} 的风格钩子暴露出来，用于离线断言。
     *
     * <p>{@code RoleComponent} 构造期会拒绝 {@code null} 服务集 ⇒ 这里给一个
     * **三个成员全 null 的空壳端口**（本测试只调风格钩子，不碰任何服务）。
     */
    private static final class StyleProbe extends MatinaFloatingTextComponent {
        StyleProbe() {
            super("probe",
                    new com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort(null, null, null),
                    null);
        }

        TextUtil.Style style(String moment) {
            return styleFor(moment);
        }
    }

    @Test
    public void everyMomentUsesMitaScrollForAllTiers() {
        // 需求："让 matina 用上这个字体（米塔 / 字体滚地）" ⇒ 六个时刻全部 MITA_SCROLL
        StyleProbe probe = new StyleProbe();
        String[] moments = {
                FloatingTextComponent.MOMENT_CAST,
                FloatingTextComponent.MOMENT_HURT,
                FloatingTextComponent.MOMENT_LOW_HP,
                FloatingTextComponent.MOMENT_KILL,
                FloatingTextComponent.MOMENT_SPAWN,
                null, // 空闲 / 未知时刻
        };
        for (String moment : moments) {
            assertEquals("时刻 [" + moment + "] 必须走米塔滚地",
                    TextUtil.Style.MITA_SCROLL, probe.style(moment));
        }
    }

    @Test
    public void idleStyleIsMitaScrollEvenWhenNoKuangComponent() {
        // ★ 平静档（取不到狂暴组件 ⇒ kuang=0）的空闲字也必须走米塔滚地
        StyleProbe probe = new StyleProbe();
        assertEquals(0, probe.kuang());
        assertEquals(MatinaFloatingTextComponent.Tier.CALM, probe.tier());
        assertEquals(TextUtil.Style.MITA_SCROLL, probe.style(null));
        assertEquals(TextUtil.Style.MITA_SCROLL, probe.style(FloatingTextComponent.MOMENT_IDLE));
    }

    @Test
    public void matinaNoLongerUsesGroundSmash() {
        // 反证：本次切换后，马提娜**不再**使用砸地风格（防止两套风格串味）
        StyleProbe probe = new StyleProbe();
        assertNotEquals(TextUtil.Style.GROUND_SMASH, probe.style(FloatingTextComponent.MOMENT_CAST));
        assertNotEquals(TextUtil.Style.GROUND_SMASH, probe.style(null));
    }
}
