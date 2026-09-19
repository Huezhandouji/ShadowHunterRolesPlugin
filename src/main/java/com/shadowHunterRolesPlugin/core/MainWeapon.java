package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CombatHook;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;


public abstract class MainWeapon extends ActiveComponent implements CombatHook {

    /**
     * 阶段 4 B0b-3：改基到 {@link ActiveComponent}（kind = MAIN_WEAPON、**`energyCost` 恒传 0**：
     * 今天武器没有 energyCost 字段，填非 0 会让武器图标多出一个今天不存在的 `ENERGY LACK` 态）。
     * 五个字段与对应 getter 已上移到基类。
     * 阶段 4 收尾批⑤：首位两参 `(id, ComponentServices)` 为**构造期注入**；其后四参
     * **顺序与含义与迁移前逐字一致**（icon 仍在 cooldown 之前）⇒ 2 个武器子类只需在首位补这两个参数。
     * 旧回调 `onAttack/onLeftClick/onRightClick/onDrop`（后者已在 T-1 ④ 删除；组件侧一律走新钩子；T-2 后无迁移标记）。
     */
    public MainWeapon(String id, ComponentServices services, Component displayName, Component description, Material icon, int cooldown){
        super(id, services, displayName, description, cooldown, 0, icon, ItemKind.MAIN_WEAPON);
    }

    /** 攻击路径的新契约：今天 listener 在攻击后**无条件**启动武器冷却 ⇒ 默认 `SUCCEED`（设计 §4.3）。 */
    @Override
    public CastResult onAttack(AttackSignal signal){
        return CastResult.SUCCEED;
    }

    //物品构建已上移到统一渲染器（core/hotbar/HotbarRenderer）：本类不再持有任何渲染入口（阶段 5 · T⑦ 收口）。
    //注意：**主武器冷却名不带秒数**是与技能侧的冻结差异，接管后仍由渲染器的主武器分支保持。

    public static class Utils{

        public static final NamespacedKey MAIN_WEAPON_KEY = KeyFactory.Registry.of(
                "main_weapon_id"
        );

        public static boolean isMainWeapon(ItemStack item){
            if(item == null || item.getType().isAir()) return false;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return false;
            return meta.getPersistentDataContainer().has(MAIN_WEAPON_KEY, PersistentDataType.STRING);
        }

        public static String getWeaponId(ItemStack item){
            if(item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return null;
        return meta.getPersistentDataContainer().get(MAIN_WEAPON_KEY, PersistentDataType.STRING);
        }

    }

    //getters 已上移到 ActiveComponent（getId/getDisplayName/getDescription/getIcon/getCooldownTicks/getEnergyCost/getKind）

}
