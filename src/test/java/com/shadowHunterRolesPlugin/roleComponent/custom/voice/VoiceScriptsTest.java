package com.shadowHunterRolesPlugin.roleComponent.custom.voice;

import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 全员语音台词的**内容闸门**（2026-10-31 需求：给所有角色加入对应语音表现内容，
 * 要求惊艳、符合设定 —— 影都：巨大怪物，少有人性，却要有人性；猎影人：复杂背景下的反英雄）。
 *
 * <h2>判据（结构 + 世界观口径）</h2>
 * <ul>
 *   <li>四个补齐角色各有一套完整台词：登场 / 空闲 / 受击 / 濒死 / 击杀 / 死亡；</li>
 *   <li>每条台词非空、去空白后非空、长度 ≤ {@link TextUtil#MAX_CHARS}（悬浮文本单段上限）；</li>
 *   <li>同一角色内不得重复台词（"惊艳"首先不能复读）；</li>
 *   <li>台词量达标（每角色 ≥ {@value #MIN_LINES_PER_ROLE} 句）；</li>
 *   <li><b>影都口径</b>：hunter / sinThorn 必须带"人性残片"（灯 / 名字 / 疼 / 记忆 等词至少出现一处），
 *       否则"巨大怪物"就只剩怪物、没有"却要有人性"；</li>
 *   <li><b>猎影人口径</b>：lin / tek 必须带"代价 / 账 / 证词 / 真理"一类反英雄关键词；</li>
 *   <li>四个角色按阵营选对语气开关（SHADOW ⇒ {@code shadowTone=true}；HUNTER ⇒ {@code false}）。</li>
 * </ul>
 */
public class VoiceScriptsTest {

    /** 每个角色至少这么多句（六类时刻合计）。 */
    private static final int MIN_LINES_PER_ROLE = 14;

    /** 影都台词的"人性残片"关键词。 */
    private static final List<String> HUMAN_FRAGMENTS =
            List.of("灯", "名字", "疼", "记得", "想起", "眨眼", "叫");

    /** 猎影人台词的"反英雄"关键词。 */
    private static final List<String> ANTI_HERO_WORDS =
            List.of("账", "证", "枪", "真理", "册", "案", "血", "灯");

    /** 六类基础时刻都必须有台词。 */
    private static final List<String> REQUIRED_MOMENTS = List.of(
            FloatingTextComponent.MOMENT_SPAWN,
            FloatingTextComponent.MOMENT_IDLE,
            FloatingTextComponent.MOMENT_HURT,
            FloatingTextComponent.MOMENT_LOW_HP,
            FloatingTextComponent.MOMENT_KILL,
            ScriptedVoiceComponent.MOMENT_DEATH);

    /** 四个补齐角色的 id（与 {@link VoiceScripts#SCRIPTED_ROLE_IDS} 一致）。 */
    @Test
    public void scriptedRoleIdsAreTheFourMissingRoles() {
        assertEquals(List.of("lin", "tek", "hunter", "sinThorn"), VoiceScripts.SCRIPTED_ROLE_IDS);
        for (String roleId : VoiceScripts.SCRIPTED_ROLE_IDS) {
            assertNotNull("缺少角色的台词表：" + roleId, VoiceScripts.scriptFor(roleId));
        }
    }

    /** 每个补齐角色：六类时刻齐全、台词非空且不超长、同角色不重复、总量达标。 */
    @Test
    public void everyScriptHasCompleteNonRepeatingMoments() {
        for (String roleId : VoiceScripts.SCRIPTED_ROLE_IDS) {
            ScriptedVoiceComponent.Script script = VoiceScripts.scriptFor(roleId);
            Map<String, List<String>> lines = script.lines();
            for (String moment : REQUIRED_MOMENTS) {
                List<String> candidates = lines.get(moment);
                assertNotNull(roleId + " 缺时刻 " + moment, candidates);
                assertFalse(roleId + " 的时刻 " + moment + " 没有台词", candidates.isEmpty());
                for (String line : candidates) {
                    assertNotNull(roleId + " / " + moment + " 有空台词", line);
                    String trimmed = line.trim();
                    assertFalse(roleId + " / " + moment + " 有空白台词", trimmed.isEmpty());
                    assertTrue(roleId + " / " + moment + " 台词超长（悬浮文本上限 "
                                    + TextUtil.MAX_CHARS + "）：" + trimmed,
                            trimmed.length() <= TextUtil.MAX_CHARS);
                }
            }

            Set<String> seen = new HashSet<>();
            int total = 0;
            for (List<String> candidates : lines.values()) {
                for (String line : candidates) {
                    total++;
                    assertTrue(roleId + " 台词重复：" + line, seen.add(line.trim()));
                }
            }
            assertTrue(roleId + " 台词量不足（" + total + " < " + MIN_LINES_PER_ROLE + "）",
                    total >= MIN_LINES_PER_ROLE);
        }
    }

    /** 影都角色（hunter / sinThorn）必须保留"人性残片"，猎影人（lin / tek）必须带反英雄关键词。 */
    @Test
    public void factionToneIsVisibleInTheLines() {
        assertFactionTone("hunter", true, HUMAN_FRAGMENTS);
        assertFactionTone("sinThorn", true, HUMAN_FRAGMENTS);
        assertFactionTone("lin", false, ANTI_HERO_WORDS);
        assertFactionTone("tek", false, ANTI_HERO_WORDS);
    }

    private static void assertFactionTone(String roleId, boolean shadowTone, List<String> requiredWords) {
        ScriptedVoiceComponent.Script script = VoiceScripts.scriptFor(roleId);
        assertEquals(roleId + " 的阵营语气开关不对", shadowTone, script.shadowTone());
        List<String> all = new ArrayList<>();
        for (List<String> candidates : script.lines().values()) {
            all.addAll(candidates);
        }
        boolean hit = all.stream().anyMatch(line -> requiredWords.stream().anyMatch(line::contains));
        assertTrue(roleId + " 的台词里缺少阵营关键词 " + requiredWords + "，实际 = " + all, hit);
    }
}
