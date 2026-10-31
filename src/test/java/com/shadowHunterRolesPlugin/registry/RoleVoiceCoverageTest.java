package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * **全员语音覆盖闸门**（2026-10-31 需求：给所有角色加入对应的语音表现内容）。
 *
 * <p>语音内容本身由各角色自己的 {@code XxxFloatingTextComponent}（手写词库）或
 * {@code ScriptedVoiceComponent}（{@link com.shadowHunterRolesPlugin.roleComponent.custom.voice.VoiceScripts}
 * 提供的剧本化台词表）承载；本测试只回答一个问题：
 * <b>注册表里的每一个世界观角色，是否都恰好挂了一个语音组件、且挂的是期望的那一个？</b>
 *
 * <p>为什么必须单独钉：语音组件是"纯叶子"（只依赖内建、不被任何技能要求），
 * 漏注册**不会**让任何角色装配失败 —— 只会安静地少一层表现。只有本测试会变红。
 * 示例角色 {@code selfUpdateExample} 不参与（它不是世界观角色，是组件演示）。
 */
public class RoleVoiceCoverageTest {

    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    private static RoleLoader loader() {
        return new RoleLoader(Logger.getLogger("RoleVoiceCoverageTest"));
    }

    /** 所有可能的语音组件 id（9 个手写族 + 1 个剧本化通用族）。 */
    private static final Set<String> ALL_VOICE_IDS = Set.of(
            "mikomako_floating_text",
            "red_floating_text",
            "heron_floating_text",
            "matina_floating_text",
            "lethe_floating_text",
            "albert_floating_text",
            "raven_floating_text",
            "resist_floating_text",
            "sayaka_floating_text",
            "role_voice_script"
    );

    /** 角色 id → 期望的语音组件 id（顺序 = 注册表顺序）。 */
    private static final Map<String, String> EXPECTED = expectedVoices();

    private static Map<String, String> expectedVoices() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("Mikomako", "mikomako_floating_text");
        map.put("red", "red_floating_text");
        map.put("Heron", "heron_floating_text");
        map.put("sinThorn", "role_voice_script");
        map.put("matina", "matina_floating_text");
        map.put("lethe", "lethe_floating_text");
        map.put("tek", "role_voice_script");
        map.put("hunter", "role_voice_script");
        map.put("albert", "albert_floating_text");
        map.put("lin", "role_voice_script");
        map.put("raven", "raven_floating_text");
        map.put("sayaka", "sayaka_floating_text");
        map.put("resist", "resist_floating_text");
        return Map.copyOf(map);
    }

    /** 每个世界观角色恰好一个语音组件，且就是期望的那一个。 */
    @Test
    public void everyWorldRoleHasExactlyItsExpectedVoiceComponent() {
        int checked = 0;
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            String roleId = definition.id();
            Role role = definition.builder().get().build();
            role.verifyDependencies();
            Set<String> present = role.getComponents().keySet();
            long voiceCount = present.stream().filter(ALL_VOICE_IDS::contains).count();

            if ("selfUpdateExample".equals(roleId)) {
                assertEquals("示例角色不参与语音覆盖（它是组件演示，不是世界观角色），实际 = " + present,
                        0L, voiceCount);
                continue;
            }

            String expected = EXPECTED.get(roleId);
            assertNotNull("注册表出现未登记语音期望的角色：" + roleId
                    + "（在本测试的 EXPECTED 表里补一行，否则「漏挂语音」会静默通过）", expected);
            assertEquals(roleId + " 的语音组件数量应为 1，实际 = " + present, 1L, voiceCount);
            assertTrue(roleId + " 缺语音组件 " + expected + "，实际 = " + present,
                    present.contains(expected));
            checked++;
        }
        assertEquals("注册表里的世界观角色数变了：期望表也要同步", EXPECTED.size(), checked);
    }

    /** 期望表里不能出现"僵尸条目"（角色被改名/删除后必须同步，否则覆盖是假的）。 */
    @Test
    public void expectedVoiceTableHasNoStaleRole() {
        Set<String> registered = new java.util.HashSet<>();
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            registered.add(definition.id());
        }
        for (String roleId : EXPECTED.keySet()) {
            assertTrue("EXPECTED 里有注册表已不存在的角色：" + roleId, registered.contains(roleId));
        }
        assertFalse("注册表里不应只剩示例角色", registered.isEmpty());
    }
}
