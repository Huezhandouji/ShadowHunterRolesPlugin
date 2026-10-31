package com.shadowHunterRolesPlugin.registry;

import com.shadowHunterRolesPlugin.core.Role;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * **战斗状态框架的装配闸门**（2026-10-31 需求：修改进入战斗 / 脱离战斗的框架）。
 *
 * <p>本测试钉住两件事：
 * <ol>
 *   <li>注册表里的<b>每一个</b>角色都恰好挂了内建 {@code combatState} 组件
 *       （漏挂不会让任何角色装配失败 ⇒ 只能靠本测试变红）；</li>
 *   <li>旧的 {@code autoRecoverSanTEPassive} 已从内建段<b>退场</b>（由 combatState 接管脱战恢复）——
 *       防止以后有人"顺手加回来"导致双重恢复。</li>
 * </ol>
 */
public class CombatStateWiringTest {

    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    private static RoleLoader loader() {
        return new RoleLoader(Logger.getLogger("CombatStateWiringTest"));
    }

    /** 每个角色都有且只有一个 combatState，且不再有旧自动恢复组件。 */
    @Test
    public void everyRoleGetsCombatStateAndLegacyAutoRecoverIsGone() {
        Set<String> checked = new LinkedHashSet<>();
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            String roleId = definition.id();
            Role role = definition.builder().get().build();
            role.verifyDependencies();

            Set<String> present = role.getComponents().keySet();
            assertTrue(roleId + " 缺战斗状态组件 combatState，实际 = " + present,
                    present.contains("combatState"));
            assertFalse(roleId + " 仍注册旧的 autoRecoverSanTEPassive（会与脱战恢复双重回血），实际 = " + present,
                    present.contains("autoRecoverSanTEPassive"));
            assertTrue(roleId + " 的必需依赖不完整: " + role.missingRequiredDependencies(),
                    role.missingRequiredDependencies().isEmpty());

            checked.add(roleId);
        }
        assertEquals("注册表角色数变了：战斗状态覆盖也要同步", 14, checked.size());
        assertTrue("示例角色也必须挂战斗状态（framework demo 同样吃框架）",
                checked.contains("selfUpdateExample"));
    }
}
