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

    /** 冻结快照（现算 58 个 —— 阶段 13 · t125 起：57 + 新增 {@code executeComponentOperation} ✓；排序后逐字比较 ⇒ 与声明顺序无关）。 */
    private static final String[] FROZEN_SIGNATURES = {
            "boolean areHostile(java.util.UUID,java.util.UUID)",
            "boolean areHostile(org.bukkit.entity.Player,org.bukkit.entity.Player)",
            "boolean clearPlayerRole(java.util.UUID)",
            "boolean clearPlayerRole(org.bukkit.entity.Player)",
            "boolean hasRole(java.util.UUID)",
            "boolean hasRole(org.bukkit.entity.Player)",
            "boolean isSkillReady(java.util.UUID,java.lang.String)",
            "boolean isSkillReady(org.bukkit.entity.Player,java.lang.String)",
            "boolean isValidRoleId(java.lang.String)",
            "boolean setPlayerRole(java.util.UUID,java.lang.String)",
            "boolean setPlayerRole(org.bukkit.entity.Player,java.lang.String)",
            "com.shadowHunterRolesPlugin.core.Faction getFaction(java.util.UUID)",
            "com.shadowHunterRolesPlugin.core.Faction getFaction(org.bukkit.entity.Player)",
            "double getPlayerHealth(java.util.UUID)",
            "double getPlayerHealth(org.bukkit.entity.Player)",
            "double getPlayerMaxHealth(java.util.UUID)",
            "double getPlayerMaxHealth(org.bukkit.entity.Player)",
            "int getPlayerEnergy(java.util.UUID)",
            "int getPlayerEnergy(org.bukkit.entity.Player)",
            "int getPlayerMaxEnergy(java.util.UUID)",
            "int getPlayerMaxEnergy(org.bukkit.entity.Player)",
            "int getPlayerMaxSanTE(java.util.UUID)",
            "int getPlayerMaxSanTE(org.bukkit.entity.Player)",
            "int getPlayerSanTE(java.util.UUID)",
            "int getPlayerSanTE(org.bukkit.entity.Player)",
            "int getSkillCooldownTick(java.util.UUID,java.lang.String)",
            "int getSkillCooldownTick(org.bukkit.entity.Player,java.lang.String)",
            //阶段 13 · t125：新增项**仅此一条**（组件操作面的唯一入口 ✓）—— 旧 57 条一字未动 ✓
            "java.lang.String executeComponentOperation(java.util.UUID,java.lang.String,java.lang.String)",
            "java.lang.String getPlayerRoleId(java.util.UUID)",
            "java.lang.String getPlayerRoleId(org.bukkit.entity.Player)",
            "java.util.List getRoleDescription(java.lang.String)",
            "java.util.List getRoles()",
            "java.util.OptionalInt getPlayerSanTEOptional(java.util.UUID)",
            "java.util.OptionalInt getPlayerSanTEOptional(org.bukkit.entity.Player)",
            "java.util.Set getAllRoleIds()",
            "java.util.UUID getLastDamagerUuid(org.bukkit.entity.LivingEntity)",
            "net.kyori.adventure.text.Component getPlayerRoleDisplayName(java.util.UUID)",
            "net.kyori.adventure.text.Component getPlayerRoleDisplayName(org.bukkit.entity.Player)",
            "net.kyori.adventure.text.Component getRoleDisplayName(java.lang.String)",
            "org.bukkit.Material getRoleIcon(java.lang.String)",
            "void decreaseEnergy(java.util.UUID,int)",
            "void decreaseEnergy(org.bukkit.entity.Player,int)",
            "void decreaseSanTE(java.util.UUID,int)",
            "void decreaseSanTE(org.bukkit.entity.Player,int)",
            "void healPlayer(java.util.UUID,double)",
            "void healPlayer(org.bukkit.entity.Player,double)",
            "void increaseEnergy(java.util.UUID,int)",
            "void increaseEnergy(org.bukkit.entity.Player,int)",
            "void increaseSanTE(java.util.UUID,int)",
            "void increaseSanTE(org.bukkit.entity.Player,int)",
            "void resetFaction(java.util.UUID)",
            "void resetFaction(org.bukkit.entity.Player)",
            "void setFaction(java.util.UUID,com.shadowHunterRolesPlugin.core.Faction)",
            "void setFaction(org.bukkit.entity.Player,com.shadowHunterRolesPlugin.core.Faction)",
            "void setPlayerEnergy(java.util.UUID,int)",
            "void setPlayerEnergy(org.bukkit.entity.Player,int)",
            "void setPlayerSanTE(java.util.UUID,int)",
            "void setPlayerSanTE(org.bukkit.entity.Player,int)"
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
