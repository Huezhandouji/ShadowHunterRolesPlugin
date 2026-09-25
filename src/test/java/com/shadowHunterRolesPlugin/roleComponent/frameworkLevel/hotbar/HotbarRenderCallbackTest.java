package com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.hotbar;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * **渲染回调的"变化判据"单测**。
 *
 * <h2>测什么 / 不测什么（边界，先看这条）</h2>
 * 本类只钉**判据的决策形状**（{@link HotbarRenderer#contentDiffers}）—— 它是**纯函数、不碰 Bukkit** ✓。
 * <p><b>不测</b>（**如实申报**）：真正的渲染管线（"写物品 → 清脏 → 变化判据 → 扇出 → deliverHook"）
 * 需要**在线玩家 + 真实库存** ⇒ 属**运行级**，本卡（静态半）**不起服** ⇒ 那一段**无离线替代** ✗。
 * 所以本类的定位是"**把判据钉死**"，**不冒充**"管线已验证" ✗。
 *
 * <h2>判据语义（与生产代码逐字同构）</h2>
 * <ol>
 *   <li><b>首帧无基线</b> ⇒ 视作**有变化**（首刷应当被通知）✓</li>
 *   <li><b>该槽位上一帧不在基线里</b> ⇒ **有变化**（现在有了）✓</li>
 *   <li><b>在基线里且内容不同</b> ⇒ **有变化** ✓</li>
 *   <li><b>在基线里且内容相同</b> ⇒ **无变化** ⇒ **不得回调** ✗（这是 B3 的核心要求）</li>
 * </ol>
 */
public class HotbarRenderCallbackTest {

    /** 用例 1：首帧无基线 ⇒ 有变化（否则首刷不通知，玩家看不到首屏 ✓）。 */
    @Test
    public void firstFrameWithNoBaselineCountsAsChanged() {
        assertTrue("首帧（previous == null）必须视作有变化",
                HotbarRenderer.contentDiffers(null, 3, false));
    }

    /** 用例 2：基线里没有该槽位 ⇒ 有变化（该槽位从"空"变"有"）。 */
    @Test
    public void slotAbsentFromBaselineCountsAsChanged() {
        Map<Integer, String> baseline = new HashMap<>();
        baseline.put(0, "A");
        assertTrue("槽位 5 不在基线里 ⇒ 必须视作有变化",
                HotbarRenderer.contentDiffers(baseline, 5, false));
    }

    /** 用例 3：在基线里且内容不同 ⇒ 有变化（**这就是"内容变了但槽位没变"那条路径** ✓）。 */
    @Test
    public void sameSlotWithDifferentContentCountsAsChanged() {
        Map<Integer, String> baseline = new HashMap<>();
        baseline.put(3, "可用");
        assertTrue("同一槽位内容由 A 变 B ⇒ 必须视作有变化",
                HotbarRenderer.contentDiffers(baseline, 3, true));
    }

    /** 用例 4：**★ 在基线里且内容相同 ⇒ 无变化 ⇒ 不得回调**（B3 的核心要求）。 */
    @Test
    public void sameSlotWithIdenticalContentCountsAsUnchanged() {
        Map<Integer, String> baseline = new HashMap<>();
        baseline.put(3, "冷却中");
        assertFalse("同一槽位内容逐字相同 ⇒ 必须视作【无变化】，否则会多报回调",
                HotbarRenderer.contentDiffers(baseline, 3, false));
    }

    /** 用例 5：**区分力反证** —— 同一个基线、同一个槽位，只有"内容是否不同"这一位在变 ⇒ 判据确实由它驱动。 */
    @Test
    public void changeFlagAloneDrivesTheVerdict() {
        Map<Integer, String> baseline = new HashMap<>();
        baseline.put(1, "x");
        assertTrue("同一槽位 + incomingDifferent=true ⇒ true",
                HotbarRenderer.contentDiffers(baseline, 1, true));
        assertFalse("同一槽位 + incomingDifferent=false ⇒ false（与上一条形成对照）",
                HotbarRenderer.contentDiffers(baseline, 1, false));
    }

    /** 用例 6：多槽位基线里只动一格 ⇒ 只有那一格算变化（其余不得因"基线非空"而被误判为变化）。 */
    @Test
    public void onlyTheTouchedSlotReportsChange() {
        Map<Integer, String> baseline = new HashMap<>();
        baseline.put(0, "技能A");
        baseline.put(1, "技能B");
        baseline.put(2, "主武器");
        assertTrue("槽位 1 内容变了 ⇒ 有变化", HotbarRenderer.contentDiffers(baseline, 1, true));
        assertFalse("槽位 0 没动 ⇒ 无变化", HotbarRenderer.contentDiffers(baseline, 0, false));
        assertFalse("槽位 2 没动 ⇒ 无变化", HotbarRenderer.contentDiffers(baseline, 2, false));
    }

    /** 用例 7：空基线（**非 null 但零条目**）与"首帧 null"必须**同判**为有变化（都对"现在有了"成立）。 */
    @Test
    public void emptyBaselineBehavesLikeFirstFrameForAbsentSlots() {
        Map<Integer, String> empty = new HashMap<>();
        assertTrue("空基线里任何槽位都缺席 ⇒ 有变化（与首帧同判）",
                HotbarRenderer.contentDiffers(empty, 0, false));
    }
}
