package com.shadowHunterRolesPlugin.api;

import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * T-7（t50 §4）：锁住 {@link RoleAPI} 的**对外面**（「只增不改」）。
 * <p>断言 = 反射读到的**公开方法签名集合**（`getDeclaredMethods()`）与下面这份**冻结快照**逐字相等；
 * 删任一方法、加方法、或改任一参数/返回类型 ⇒ 立即红。
 * <p>快照来源 = 冻结件 `阶段9-测试缝范围-需求与验收.md` §6 C-10（现算 57）+ 本卡在 `f91776a` 代制品上
 * 用反射导出（导出脚本 = 仓库外 `.scratch/t57/T57Probe.java`，非交付物）。
 * <p><b>阶段 13 · t125（已批准的"只增"）</b>：快照 **57 → 58** ✓ —— 新增项**仅一条**：
 * {@code java.lang.String executeComponentOperation(java.util.UUID,java.lang.String,java.lang.String)}
 * （组件操作面的**唯一**操作入口 ✓，设计定案 §7.4 ②）；**旧 57 条一字未动** ✓（旧集合 ⊆ 新集合 ✓）。
 * <p>**不涉及运行时语义**（真能设置角色/清角色 = 运行级）：本测试只锁签名面。
 * <p>产物层判据（成品物品/背包/PDC）**不在**本测试内（那是假缝，见冻结件 §5）。
 */
public class RoleApiSurfaceTest {

    /**
     * 冻结快照（现算 **16** 条）。
     *
     * <p>★ **本轮收缩 58 → 16**：废弃方法已被删除（"直接操作组件"时代的空壳 —— 恒回中性哨兵值 / 空操作）：
     * 阵营三条（读走 {@code RoleInfo#faction()}、写只在聚合根上）、生命 / 能量 / SanTE / 冷却读数与增减、
     * 以及所有 {@code Player} 重载。
     * <p>★ **保留** {@code areHostile}（它**不是**空壳 —— 转调 {@code RoleManager} 的可用实现，且判定链
     * 只从聚合根读阵营）。
     * <p>排序后逐字比较 ⇒ 与声明顺序无关。
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
