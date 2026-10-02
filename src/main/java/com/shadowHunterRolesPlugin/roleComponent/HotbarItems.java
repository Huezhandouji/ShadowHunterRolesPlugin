package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * **物品栏里"本系统的物品"的无状态工具**：识别键判定 + 按识别键清除。
 * <p>组件侧的硬规则是「组件只能请求、不能写」（帧末 flush 是唯一的渲染写点），因此本工具不是组件、
 * 也不参与生命周期：它只按识别键扫玩家背包里**全部合法槽位**
 * （{@code 0..}{@link HotbarSpecification#MAX_SLOT}）。容器（{@code RoleInstance}）的对外面只留容器
 * 职责（查取入口 / 隔离 / 生命周期），"清哪些物品"属物品关注点，归本工具，调用方 = 死亡 / 重生 / 清
 * 角色三条路径。
 * <h2>清除范围</h2>
 * **只清本系统写进去的**：识别键命中 {@link #isSystemItem} 的槽位则置空；
 * 玩家自己的物品一律不动。
 * <p>扫描上界 = {@link HotbarSpecification#MAX_SLOT}（**与栏位合法区间同一个真值来源**）：栏位放开到
 * 背包槽位之后若仍只扫前 9 格，落在 {@code 9..MAX_SLOT} 的本系统物品会在角色清除 / 死亡 / 重生后
 * **残留在背包里** —— 与"漏一个识别键"是同族的失败形态，只是这次漏的是上界。
 * <h2>★ 三支识别键的**唯一判定点** = {@link #isSystemItem(ItemStack)}</h2>
 * 这组键（{@link Skill.Utils#SKILL_KEY} · {@link MainWeapon.Utils#MAIN_WEAPON_KEY} ·
 * {@link BowWeapon.Utils#BOW_WEAPON_KEY}）的**清单只写在本类这一处**，三处消费者都读它：
 * <ol>
 *   <li>本类的 {@link #clearFrom(Player)}（角色清除 / 死亡 / 重生）；</li>
 *   <li>{@code listener/hook/HotbarItemProtectionListener}（拖拽 / F 键 / 漏斗 / 合成格的保护）；</li>
 *   <li>{@code builtin/HotbarRenderComponent} 的落位覆盖判据（`isOwnedItem`）——
 *       栏位上界放开到背包后，落位必须是"空格或本系统的格"才写，否则会销毁玩家放在那一格的物品。</li>
 * </ol>
 * **凡在物品栏里放东西的组件家族，其键都必须出现在 {@link #isSystemItem} 里**（漏一条 = 该家族的物品
 * 既清不掉、也保护不住、还会被落位覆盖判据当成"别人的东西"而拒绝刷新）。
 */
public final class HotbarItems {

    /** 工具类：不实例化。 */
    private HotbarItems() {
    }

    /**
     * **"这是本系统写进物品栏的东西吗"** —— 三支家族识别键的**唯一判定点**（清单见类注释）。
     * <p>键由各组件的 {@code buildItem()} 最后一步写入（技能 / 近战主武器 / 弓弩主武器各一个键）。
     * <p>三个读口（{@code Skill.Utils} / {@code MainWeapon.Utils} / {@code BowWeapon.Utils}）都自带
     * null 与空气判空，因此本方法可直接吃 {@code Inventory#getItem(...)} 的返回值（含 {@code null}）。
     */
    public static boolean isSystemItem(ItemStack item) {
        return Skill.Utils.isSkillItem(item)
                || MainWeapon.Utils.isMainWeapon(item)
                || BowWeapon.Utils.isBowWeapon(item);
    }

    /**
     * 把本系统写在玩家物品栏里的物品清掉（逐格判 {@link #isSystemItem}；扫满
     * {@code 0..}{@link HotbarSpecification#MAX_SLOT}）；调用点、调用时机与"只清本系统的"这条清除范围
     * 与迁移前一致 —— 变的只有上界（原先写死 9 格）。
     */
    public static void clearFrom(Player player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i <= HotbarSpecification.MAX_SLOT; i++) {
            if (isSystemItem(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }
}
