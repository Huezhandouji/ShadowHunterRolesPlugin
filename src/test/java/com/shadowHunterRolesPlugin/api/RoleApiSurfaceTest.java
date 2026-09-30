package com.shadowHunterRolesPlugin.api;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 锁住 {@link RoleAPI} 的对外面（只增不改）：断言是反射读到的公开方法签名集合
 * （{@code getDeclaredMethods()}）与下面这份冻结快照逐字相等；删任一方法、加方法、
 * 或改任一参数/返回类型都会立即变红。
 * <p>快照由冻结需求文档的签名清单加当前制品上的反射导出共同定下，其中包含
 * {@code executeComponentOperation(UUID, String, String)} 这一条 ——
 * 组件操作面的唯一操作入口（设计定案 §7.4 ②）。
 * <p>本测试只锁签名面，不涉及运行时语义（真能设置角色/清角色属运行级；
 * 成品物品/背包/PDC 属产物层，都不在这里）。
 */
public class RoleApiSurfaceTest {

    /**
     * 冻结快照。
     *
     * <p>废弃方法已被删除 —— 它们是"直接操作组件"时代的空壳
     * （恒回中性哨兵值 / 空操作）：阵营三条（读走 {@code RoleInfo#faction()}、写只在聚合根上）、
     * 生命 / 能量 / SanTE / 冷却读数与增减、以及所有 {@code Player} 重载。
     * <p>保留 {@code areHostile}：它**不是**空壳，而是转调 {@code RoleManager} 的可用实现，
     * 且判定链只从聚合根读阵营。
     * <p>排序后逐字比较，因此与声明顺序无关。
     */
    private static final String[] FROZEN_SIGNATURES = {
            "boolean areHostile(java.util.UUID,java.util.UUID)",
            "boolean clearPlayerRole(java.util.UUID)",
            "boolean hasRole(java.util.UUID)",
            "boolean isValidRoleId(java.lang.String)",
            "boolean setPlayerRole(java.util.UUID,java.lang.String)",
            "java.lang.String executeComponentOperation(java.util.UUID,java.lang.String,java.lang.String)",
            "java.lang.String getPlayerRoleId(java.util.UUID)",
            "java.util.List getRoleDescription(java.lang.String)",
            "java.util.List getRoles()",
            "java.util.Set getAllRoleIds()",
            "java.util.UUID getLastDamagerUuid(org.bukkit.entity.LivingEntity)",
            "net.kyori.adventure.text.Component getPlayerRoleDisplayName(java.util.UUID)",
            "net.kyori.adventure.text.Component getRoleDisplayName(java.lang.String)",
            "org.bukkit.Material getRoleIcon(java.lang.String)"
    };

    /** 现算（与快照同一条口径：declared methods ⇒ 只数本接口自己的声明）。 */
    private static List<String> reflectSignatures() {
        List<String> out = new ArrayList<>();
        for (Method m : RoleAPI.class.getDeclaredMethods()) {
            StringBuilder b = new StringBuilder(m.getReturnType().getName()).append(' ').append(m.getName()).append('(');
            Class<?>[] ps = m.getParameterTypes();
            for (int i = 0; i < ps.length; i++) { if (i > 0) b.append(','); b.append(ps[i].getName()); }
            b.append(')');
            out.add(b.toString());
        }
        Collections.sort(out);
        return out;
    }

    @Test
    public void publicSurfaceMatchesFrozenSnapshot() {
        List<String> actual = reflectSignatures();
        List<String> frozen = new ArrayList<>(Arrays.asList(FROZEN_SIGNATURES));
        Collections.sort(frozen);
        org.junit.Assert.assertEquals("RoleAPI method count drifted", FROZEN_SIGNATURES.length, actual.size());
        org.junit.Assert.assertEquals("RoleAPI signatures drifted", frozen, actual);
    }

    @Test
    public void frozenSnapshotHasNoDuplicates() {
        java.util.Set<String> s = new java.util.HashSet<>(Arrays.asList(FROZEN_SIGNATURES));
        org.junit.Assert.assertEquals("frozen snapshot must be a set of distinct signatures",
                FROZEN_SIGNATURES.length, s.size());
    }
}
