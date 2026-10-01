package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * **热键栏里"本系统的物品"的无状态工具**：识别键判定 + 按识别键清除。
 * <p>组件侧的硬规则是「组件只能请求、不能写」（帧末 flush 是唯一的渲染写点），因此本工具不是组件、
 * 也不参与生命周期：它只按识别键扫玩家背包的前 9 格。容器（{@code RoleInstance}）的对外面只留容器
 * 职责（查取入口 / 隔离 / 生命周期），"清哪些物品"属物品关注点，归本工具，调用方 = 死亡 / 重生 / 清
 * 角色三条路径。
 * <h2>清除范围</h2>
 * **只清本系统写进去的**：识别键命中 {@link Skill.Utils#isSkillItem} ·
 * {@link MainWeapon.Utils#isMainWeapon} 或 {@link BowWeapon.Utils#isBowWeapon} 的槽位则置空；
 * 玩家自己的物品一律不动。
 * <p>三支识别键 = 三个家族基类各一个（道具 / 近战主武器 / 弓弩主武器）：**凡在热键栏里放东西的组件
 * 家族，其键都必须在这里出现**，否则角色清除后它的物品会残留在背包里（这条是加家族时的必改点）。
 */
public final class HotbarItems {

    /** 工具类：不实例化。 */
    private HotbarItems() {
    }

    /**
     * 把本系统写在热键栏里的物品清掉（逐格判识别键；只扫前 9 格 = 热键栏本身）；调用点、调用时机、
     * 清除范围与迁移前逐字一致。
     */
    public static void clearFrom(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (Skill.Utils.isSkillItem(item)
                    || MainWeapon.Utils.isMainWeapon(item)
                    || BowWeapon.Utils.isBowWeapon(item)) {
                inv.setItem(i, null);
            }
        }
    }
}
