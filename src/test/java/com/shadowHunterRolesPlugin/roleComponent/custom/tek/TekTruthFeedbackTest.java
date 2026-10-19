package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * 「特克」本轮三条"观感 / 提示"需求的离线单测。
 *
 * <h2>覆盖的需求</h2>
 * <ol>
 *   <li><b>真理特效起始高度 = 敌人坐标上方 0.6 格</b>，且层数越多环越高；</li>
 *   <li><b>某个敌人的真理跨过 10 层时提示一次，可提示多次</b>（用状态差，不是状态本身）；</li>
 *   <li><b>彩虹跳字</b>的取色数学（逐字铺满彩虹 + 相位流动）。</li>
 * </ol>
 *
 * <h2>判据边界（如实申报）</h2>
 * <ul>
 *   <li>覆盖的是这些需求的**纯函数内核**（高度常量、边沿判据、色相数学）；</li>
 *   <li><b>不</b>覆盖：粒子真的被画到哪个世界坐标、物品名真的被写进 NBT、
 *       音效真的被服务端播放、以及"敌人死亡 ⇒ 清账本"的清理动作本身
 *       （都要 Bukkit 运行环境；死亡清理的**判据**在代码里是
 *       {@code isDead() || health <= 0}，与 {@code RedBleedPassive} 同口径）。</li>
 * </ul>
 */
public class TekTruthFeedbackTest {

    // ───────── 需求 1：真理特效起始高度 0.6 格、层数越多越高 ─────────

    @Test
    public void 真理特效起始高度是零点六格() {
        //需求括注："将特效起始高度改为敌人坐标的 0.6 格高"
        assertEquals(0.6d, TekDestinyPassive.truthOrbitBaseHeightForTest(), 1.0E-9d);
    }

    @Test
    public void 层数越多环越高_每颗都要往上抬() {
        double stepY = TekDestinyPassive.truthOrbitStepYForTest();
        assertTrue("每颗必须严格往上抬，否则'层数越多越高'不成立，stepY=" + stepY, stepY > 0d);
    }

    @Test
    public void 起始高度必须是正数且不夸张() {
        double base = TekDestinyPassive.truthOrbitBaseHeightForTest();
        assertTrue("0.6 格应当为正", base > 0d);
        assertTrue("不该高过人头（否则看起来飘在空中）", base < 1.0d);
    }

    // ───────── 需求：层数到达 10 就不再叠高 ─────────

    /**
     * 一个空服务集桩上的被动实例（构造器只存字段、无副作用 ⇒ 离线可造，照 {@code MatinaKuangTest}）。
     * <p>只用来调 {@code orbitsFor(...)} 这个纯读口，不触发任何 Bukkit 访问。
     */
    private static final TekDestinyPassive PASSIVE = new TekDestinyPassive(
            TekDestinyPassive.ID,
            new com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort(null, null, null),
            new TekDestinyPassive.Specification());

    /** 走一遍"层数 → 粒子颗数"（颗数决定高度 ⇒ 封住颗数就是封住高度）。 */
    private static int orbits(int layers) {
        return PASSIVE.orbitsFor(layers);
    }

    @Test
    public void 十层以内_颗数随层数增长() {
        //10 层封顶前的增长性：颗数必须严格不降（否则"越高"不成立）
        int prev = orbits(0);
        for (int layers = 1; layers <= 10; layers++) {
            int now = orbits(layers);
            assertTrue("层数 " + layers + " 的颗数不应少于上一层（prev=" + prev + " now=" + now + "）",
                    now >= prev);
            prev = now;
        }
        assertTrue("10 层应当确实画出了粒子", orbits(10) > 0);
        assertTrue("10 层应当比 2 层更高（颗数更多）", orbits(10) > orbits(2));
    }

    @Test
    public void 到达十层后_不再叠高() {
        //★ 需求原话："当层数到达 10 就不再叠高"
        int atTen = orbits(10);
        for (int layers = 11; layers <= 100; layers++) {
            assertEquals("层数 " + layers + " 应停在 10 层的高度（颗数不再增长）",
                    atTen, orbits(layers));
        }
    }

    @Test
    public void 叠高上限就是十层() {
        assertEquals("叠高上限 = 10（需求）", 10, TekDestinyPassive.truthOrbitMaxLayersForTest());
    }

    @Test
    public void 叠高上限与解锁阈值当前一致_分开改要有意识() {
        //需求里那个"10"同时出现在"解锁阈值"与"叠高上限"两处。
        //两者刻意写成独立常量（便于将来分开调）；本条是**绊线**：
        //若你只改了其中一个，它会变红，提醒你"这是有意的吗？"
        assertEquals("叠高上限与解锁阈值当前相等；若要有意分开，请一并更新本条与注释",
                TekTruth.UNLOCK_THRESHOLD, TekDestinyPassive.truthOrbitMaxLayersForTest());
    }

    @Test
    public void 颗数与层数的换算关系正确() {
        int per = TekDestinyPassive.layersPerParticleForTest();
        assertTrue("每颗代表的层数必须为正", per > 0);
        //10 层 = 10 / per 颗
        assertEquals(10 / per, orbits(10));
        //0 层 / 负数层 ⇒ 不画
        assertEquals(0, orbits(0));
        assertEquals("负数按 0 处理（纯函数要自洽）", 0, orbits(-5));
    }

    // ───────── 需求 4：跨阈值提示一次，且可提示多次 ─────────

    @Test
    public void 第一次达到十层_应当提示() {
        assertTrue("9 → 10 = 第一次达到", TekDestinyPassive.crossesUnlockThreshold(9, 10));
    }

    @Test
    public void 已经在阈值之上继续叠加_不应重复提示() {
        assertFalse("10 → 11：已经在阈值上，不重复提示",
                TekDestinyPassive.crossesUnlockThreshold(10, 11));
        assertFalse("20 → 21：同上", TekDestinyPassive.crossesUnlockThreshold(20, 21));
        assertFalse("50 → 51：同上", TekDestinyPassive.crossesUnlockThreshold(50, 51));
    }

    @Test
    public void 掉回阈值之下再达到_应当再次提示() {
        //★ 需求原话："即使他已经提醒过一次，但他再次达到时，也会提醒一次"
        assertTrue("（曾到 15，掉回 9）9 → 10 ⇒ 再提示一次",
                TekDestinyPassive.crossesUnlockThreshold(9, 10));
    }

    @Test
    public void 阈值边界_十正好算达到() {
        assertTrue("9 → 10 命中（>= 阈值）", TekDestinyPassive.crossesUnlockThreshold(9, 10));
        //一次加满（理论上不会发生，但语义上该提示）
        assertTrue("0 → 10 也应提示", TekDestinyPassive.crossesUnlockThreshold(0, 10));
        assertTrue("0 → 30 也应提示", TekDestinyPassive.crossesUnlockThreshold(0, 30));
    }

    @Test
    public void 未达阈值_不提示() {
        assertFalse(TekDestinyPassive.crossesUnlockThreshold(0, 9));
        assertFalse(TekDestinyPassive.crossesUnlockThreshold(8, 9));
    }

    @Test
    public void 多次达到可以多次提示_穷举一段序列() {
        //模拟一段真实序列：打到 10（提示）→ 继续叠加（不提示）→ 被清到 9 → 再打到 10（再提示）
        int[] beforeSequence = {9, 10, 20, 9};
        int[] afterSequence = {10, 11, 21, 10};
        int notified = 0;
        for (int i = 0; i < beforeSequence.length; i++) {
            if (TekDestinyPassive.crossesUnlockThreshold(beforeSequence[i], afterSequence[i])) {
                notified++;
            }
        }
        //9→10 ✓、10→11 ✗、20→21 ✗、9→10 ✓ = 2 次
        assertEquals("同一敌人被多次达到就该多次提示", 2, notified);
    }

    // ───────── 需求 4 的另一半：彩虹跳字 ─────────

    @Test
    public void 彩虹_基本色相取色正确() {
        assertEquals("色相 0 = 红", 0xFF0000, TekRainbow.rgbOf(0f));
        assertEquals("色相 1/6 = 黄", 0xFFFF00, TekRainbow.rgbOf(1f / 6f));
        assertEquals("色相 1/3 = 绿", 0x00FF00, TekRainbow.rgbOf(1f / 3f));
        assertEquals("色相 1/2 = 青", 0x00FFFF, TekRainbow.rgbOf(0.5f));
        assertEquals("色相 2/3 = 蓝", 0x0000FF, TekRainbow.rgbOf(2f / 3f));
    }

    @Test
    public void 彩虹_色相环绕() {
        assertEquals("色相 1 等价于 0", TekRainbow.rgbOf(0f), TekRainbow.rgbOf(1f));
        assertEquals("色相 -1 等价于 0", TekRainbow.rgbOf(0f), TekRainbow.rgbOf(-1f));
        assertEquals("色相 2.25 等价于 0.25", TekRainbow.rgbOf(0.25f), TekRainbow.rgbOf(2.25f));
    }

    @Test
    public void 彩虹_一段字铺满整圈彩虹() {
        int total = 10;
        //首字色相 = 0（红），末字色相 ≈ (total-1)/total ⇒ 接近但不到 1（紫红）
        assertEquals(0f, TekRainbow.hueFor(0, total, 0), 1.0E-6f);
        float last = TekRainbow.hueFor(total - 1, total, 0);
        assertTrue("末字色相应接近一圈的末尾，实际 = " + last, last > 0.85f && last < 1f);
        //中间的字依次递增（才叫"彩虹"而不是"闪色"）
        for (int i = 1; i < total; i++) {
            assertTrue("色相应随字序递增",
                    TekRainbow.hueFor(i, total, 0) > TekRainbow.hueFor(i - 1, total, 0));
        }
    }

    @Test
    public void 彩虹_相位推进会让同一个字换颜色() {
        int total = 8;
        int a = TekRainbow.rgbOf(TekRainbow.hueFor(0, total, 0));
        int b = TekRainbow.rgbOf(TekRainbow.hueFor(0, total, 5));
        int c = TekRainbow.rgbOf(TekRainbow.hueFor(0, total, 20));
        assertNotEquals("相位推进 ⇒ 颜色必须变化（'跳字'的前提）", a, b);
        assertNotEquals("相位继续推进 ⇒ 继续变化", b, c);
    }

    @Test
    public void 彩虹_相位一圈后回到起点() {
        int total = 8;
        assertEquals("相位走满一圈 PHASE_CYCLE ⇒ 回到起点颜色",
                TekRainbow.hueFor(3, total, 0), TekRainbow.hueFor(3, total, TekRainbow.PHASE_CYCLE), 1.0E-6f);
        assertEquals("走两圈也一样",
                TekRainbow.hueFor(3, total, 7), TekRainbow.hueFor(3, total, 7 + 2 * TekRainbow.PHASE_CYCLE), 1.0E-6f);
    }

    @Test
    public void 彩虹_总字数为一也不炸() {
        //单字名字：色相只有 0 + 相位，不能除零
        float hue = TekRainbow.hueFor(0, 1, 3);
        assertTrue("必须落在 [0,1)", hue >= 0f && hue < 1f);
        assertEquals("total=0 也不能除零", 0f, TekRainbow.hueFor(0, 0, 0), 1.0E-6f);
    }

    @Test
    public void 彩虹_空底稿返回空组件不抛异常() {
        //buildItem 里底稿可能为 null（防御性），这里钉住不抛
        assertTrue(TekRainbow.animated(null, 0) != null);
        assertEquals("", net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(TekRainbow.animated(null, 0)));
    }

    // ───────── 需求：敌人死亡 ⇒ 真理**一定**消失（不是"概率消失"）─────────

    /**
     * {@code isCarrierGone(...)} 的**穷举**：4 个布尔 = 16 种组合，逐个钉住语义。
     *
     * <p>这是"层数只是概率消失"的最小可测内核 —— 旧版只把死亡判据写在
     * {@code update()} 的守卫**之后**（本人一死就整段跳过），且只依赖轮询。
     * 本条只验"判据本身够不够宽"：任一条件成立即判离场。
     */
    @Test
    public void 离场判据_任一条件成立即离场() {
        //16 种组合：只要有一项为 true 就必须判离场（取或，不是取与）
        for (int mask = 0; mask < 16; mask++) {
            boolean offline = (mask & 1) != 0;
            boolean notOnline = (mask & 2) != 0;
            boolean dead = (mask & 4) != 0;
            boolean healthZero = (mask & 8) != 0;
            boolean expected = offline || notOnline || dead || healthZero;
            assertEquals("组合 mask=" + mask
                            + "（offline=" + offline + ", notOnline=" + notOnline
                            + ", dead=" + dead + ", healthZero=" + healthZero + "）判据不符",
                    expected,
                    TekDestinyPassive.isCarrierGone(offline, notOnline, dead, healthZero));
        }
    }

    @Test
    public void 离场判据_全部为假才留在场上() {
        assertFalse("四项全假 = 还在场上，不能清",
                TekDestinyPassive.isCarrierGone(false, false, false, false));
    }

    @Test
    public void 离场判据_离线必须清() {
        assertTrue("离线（getPlayer 返回 null）必须清，否则留到 3 分钟惰性过期",
                TekDestinyPassive.isCarrierGone(true, false, false, false));
    }

    @Test
    public void 离场判据_血量归零必须清() {
        //★ 这是修"概率消失"的关键一条：死亡界面上 isDead() 仍可能为 false，
        //  只靠 isDead() 会漏 ⇒ 血量归零必须独立成立。
        assertTrue("血量 ≤ 0 必须清（isDead() 不可靠）",
                TekDestinyPassive.isCarrierGone(false, false, false, true));
        assertTrue("哪怕 isDead() 说还活着，血量归零也要清",
                TekDestinyPassive.isCarrierGone(false, false, false, true));
    }

    @Test
    public void 离场判据_死亡必须清() {
        assertTrue("isDead() 成立必须清",
                TekDestinyPassive.isCarrierGone(false, false, true, false));
    }

    @Test
    public void 离场判据_不在线必须清() {
        assertTrue("isOnline() 为假必须清",
                TekDestinyPassive.isCarrierGone(false, true, false, false));
    }

    // ───────── 账本层的"清了就是清了"（幂等）─────────

    @Test
    public void 清层幂等_重复清同一个目标不炸且第二次为0() {
        //事件通道与轮询通道可能对同一个目标各清一次 ⇒ clearOne 必须幂等
        java.util.UUID victim = java.util.UUID.randomUUID();
        try {
            TekTruth.addOne(victim);
            TekTruth.addOne(victim);
            TekTruth.addOne(victim);
            assertEquals(3, TekTruth.layersOf(victim));

            int first = TekTruth.clearOne(victim);
            int second = TekTruth.clearOne(victim);

            assertEquals("第一次清掉 3 层", 3, first);
            assertEquals("第二次已经没有条目 ⇒ 0", 0, second);
            assertEquals("清完层数归零", 0, TekTruth.layersOf(victim));
        } finally {
            TekTruth.resetForTest();
        }
    }

    @Test
    public void 清层后账本里不再有该目标() {
        java.util.UUID victim = java.util.UUID.randomUUID();
        try {
            TekTruth.addOne(victim);
            assertTrue("加过之后快照里应当能看到",
                    TekTruth.snapshot().stream().anyMatch(t -> t.playerId().equals(victim)));
            TekTruth.clearOne(victim);
            assertFalse("清掉之后快照里不应再出现",
                    TekTruth.snapshot().stream().anyMatch(t -> t.playerId().equals(victim)));
        } finally {
            TekTruth.resetForTest();
        }
    }

    @Test
    public void 两条通道先到先清_后到的是无操作() {
        //模拟：轮询通道先清（比如血量归零的那一 tick），随后事件通道才到
        java.util.UUID victim = java.util.UUID.randomUUID();
        try {
            TekTruth.addOne(victim);
            TekTruth.addOne(victim);
            //轮询通道
            TekTruth.clearOne(victim);
            //事件通道（同刻稍后）—— 必须是无操作，不能复活条目
            TekTruth.clearOne(victim);
            assertEquals("后到的清不能凭空造出条目", 0, TekTruth.layersOf(victim));
            assertFalse("账本里不该留空条目",
                    TekTruth.snapshot().stream().anyMatch(t -> t.playerId().equals(victim)));
        } finally {
            TekTruth.resetForTest();
        }
    }
}
