package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;


public abstract class Skill extends ActiveComponent {

    /**
     * 阶段 4 B0b-2：改基到 {@link ActiveComponent}（kind = SKILL）。
     * 六个字段与对应 getter 已上移到基类（基类 getter 是 `final`）。
     * 阶段 4 收尾批⑤：首位两参 `(id, ComponentServices)` 为**构造期注入**（id 由注册处声明、服务集由容器注入）；
     * 其后的六个参数**顺序与含义与迁移前逐字一致** ⇒ 7 个技能子类只需在首位补这两个参数。
     * 旧回调 `onRightClick/onLeftClick/onDrop` 已在 T-1 ④ 删除（组件侧一律走新钩子 `onCast`；T-2 后无迁移标记）。
     */
    public Skill(String id, ComponentServices services, Component displayName, Component description, int cooldown, int energyCost, Material icon){
        super(id, services, displayName, description, cooldown, energyCost, icon, ItemKind.SKILL);
    }



    //技能物品
    //物品构建已上移到统一渲染器（core/hotbar/HotbarRenderer）：本类不再持有任何渲染入口（阶段 5 · T⑦ 收口）。

    public static class Utils{

        public static final NamespacedKey SKILL_KEY = KeyFactory.Registry.of(
                "skill_id"
        );

        public static boolean isSkillItem(ItemStack item){
            if(item == null || item.getType().isAir()) return false;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return false;

            return meta.getPersistentDataContainer().has(SKILL_KEY, PersistentDataType.STRING);
        }

        public static String getSkillId(ItemStack item){
            if(item == null || item.getType().isAir()) return null;
            ItemMeta meta = item.getItemMeta();
            if(meta == null) return null;

            PersistentDataContainer container = meta.getPersistentDataContainer();
            if(!container.has(SKILL_KEY, PersistentDataType.STRING)) return null;
            return container.get(SKILL_KEY, PersistentDataType.STRING);
        }

    }

}
