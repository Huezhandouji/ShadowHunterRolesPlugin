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

import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpecification;
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
    /**
     * **描述符口径的构造**（阶段 7 · B 步）：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事。
     */
    public Skill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 旧构造口径（表现参数内联）：**保留为兼容别名** —— 与迁移前逐字同序同义。
     *
     * @deprecated 改用 `(id, services, Specification)`：表现值写进组件自己的嵌套 `Specification`。
     */
    @Deprecated
    public Skill(String id, ComponentServices services, Component displayName, Component description, int cooldown, int energyCost, Material icon){
        super(id, services, HotbarSpecification.of(id, displayName, description, icon, cooldown, energyCost, ItemKind.SKILL));
    }

    /**
     * **技能描述符**（阶段 7 · A 步骨架）：kind 固定为 {@link ItemKind#SKILL}，**带栏位**
     * （继承 {@link HotbarSpecification} ⇒ 有 {@code setSlot}）。
     * <p>参数顺序 = 本类构造器去掉前两位（`id` / `services`）后的**原样顺序** ⇒ 阶段 7 · B 步的迁移是机械可对拍的：
     * 把构造实参从子类构造器**原样粘贴**进它自己的嵌套 `Specification` 即可。
     * <p>与主武器的规则差异（阶段 7 拍板"规则进类型"）：
     * <ul>
     *   <li>技能**可以**有非零能量消耗（`energyCost` 是本类型的构造参数）；</li>
     *   <li>{@code setSlot} 由本类型提供（技能占热键栏）。</li>
     * </ul>
     * 本类型**不实现** {@link #create(String, ComponentServices)} ⇒ 具体组件必须自己声明嵌套
     * `Specification` 并覆写它（编译期强制，不会漏）。
     */
    public abstract static class Specification extends HotbarSpecification<Skill> {

        /** 声明式构造（推荐）：id 属于注册处，不写进组件描述符。 */
        protected Specification(Component displayName, Component description, int cooldownTicks,
                                int energyCost, Material icon){
            this(null, displayName, description, cooldownTicks, energyCost, icon);
        }

        /** 带 id 的构造（表现面需要 id 时用；{@code null} = 由注册处给出）。 */
        protected Specification(String id, Component displayName, Component description, int cooldownTicks,
                                int energyCost, Material icon){
            super(id, displayName, description, icon, cooldownTicks, energyCost, ItemKind.SKILL);
        }

        /** 具体组件必须给出创建逻辑（保留抽象 ⇒ 漏写是**编译错误**，不是运行期惊喜）。 */
        @Override
        public abstract Skill create(String id, ComponentServices services);
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
