package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * **热键栏里"本系统的物品"的无状态工具**：识别键判定 + 按识别键清除。
 *
 * <h2>它为什么不是组件方法</h2>
 * 组件侧的硬规则是「**组件只能请求、不能写**」（帧末 flush 是唯一的渲染写点）⇒ 本工具**不是组件**、
 * 也不参与生命周期：它只按**识别键**扫玩家背包的前 9 格 ✓。
 *
 * <h2>它为什么不是容器的静态方法</h2>
 * 容器（{@code RoleInstance}）只保留"容器职责"的对外面（查取入口 / 隔离 / 生命周期）✗ ——
 * "清哪些物品"属**物品关注点**，归本工具 ✓（调用方 = 死亡 / 重生 / 清角色三条路径）。
 *
 * <h2>清除范围</h2>
 * **只清本系统写进去的**：识别键命中 {@link Skill.Utils#isSkillItem} 或
 * {@link MainWeapon.Utils#isMainWeapon} 的槽位 ⇒ 置空 ✓；**玩家自己的物品一律不动** ✗。
 */
public final class HotbarItems {

    /** 工具类：不实例化。 */
    private HotbarItems() {
    }

    /**
     * **把本系统写在热键栏里的物品清掉**（逐格判识别键；只扫前 9 格 = 热键栏本身 ✓）。
     * <p>语义与实现逐字沿用原先挂在容器上的同名入口 —— 调用点、调用时机、清除范围三者都不变 ✓。
     */
    public static void clearFrom(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack item = inv.getItem(i);
            if (Skill.Utils.isSkillItem(item)
                    || MainWeapon.Utils.isMainWeapon(item)) {
                inv.setItem(i, null);
            }
        }
    }
}
