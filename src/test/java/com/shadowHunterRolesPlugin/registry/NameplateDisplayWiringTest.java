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
import static org.junit.Assert.assertTrue;

/**
 * **名下属性显示的装配闸门**（2026-10-31 需求：增加名下血量显示和 SAN/TE 显示）。
 *
 * <p>显示组件是"纯叶子"（只依赖内建、不被任何技能要求）：漏注册**不会**让角色装配失败，
 * 只会安静地少一层表现 ⇒ 必须由本测试钉住"注册表里每个世界观角色都恰好挂一个"。
 */
public class NameplateDisplayWiringTest {

    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    private static RoleLoader loader() {
        return new RoleLoader(Logger.getLogger("NameplateDisplayWiringTest"));
    }

    @Test
    public void everyRoleHasExactlyOneNameplateDisplay() {
        Set<String> checked = new LinkedHashSet<>();
        for (RoleLoader.Definition definition : loader().defaultDefinitions()) {
            String roleId = definition.id();
            Role role = definition.builder().get().build();
            role.verifyDependencies();

            Set<String> present = role.getComponents().keySet();
            long count = present.stream().filter("nameplateVitalsDisplay"::equals).count();
            assertEquals(roleId + " 名下属性显示组件数量应为 1，实际 = " + present, 1L, count);
            assertTrue(roleId + " 的必需依赖不完整: " + role.missingRequiredDependencies(),
                    role.missingRequiredDependencies().isEmpty());
            checked.add(roleId);
        }
        assertEquals("注册表角色数变了：名下显示覆盖也要同步", 14, checked.size());
        assertTrue("示例角色同样要挂名下显示（框架演示也吃框架）",
                checked.contains("selfUpdateExample"));
    }
}
