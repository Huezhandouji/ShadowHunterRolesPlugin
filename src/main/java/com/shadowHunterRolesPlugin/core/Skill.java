package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.platform.KeyFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import java.util.ArrayList;
import java.util.List;


public abstract class Skill extends ActiveComponent {

    /**
     * 阶段 4 B0b-2：改基到 {@link ActiveComponent}（kind = SKILL）。
     * 六个字段与对应 getter 已上移到基类（基类 getter 是 `final`）；**构造参数顺序不变**
     * ⇒ 7 个技能子类的 `super(...)` 一字不改。旧回调 `onRightClick/onLeftClick/onDrop` 已在 T-1 ④ 删除（组件侧一律走新钩子 `onCast`）
     * （收尾开关为 false、`isMigrated()` 全 false ⇒ 派发仍全走旧路径）。
     */
    public Skill(String id, Component displayName, Component description, int cooldown, int energyCost, Material icon){
        super(id, displayName, description, cooldown, energyCost, icon, ItemKind.SKILL);
    }

    /**
     * T-1 (4) 的残留项（需 T-1b 收口）：RedDeeplySorrowSkill 仍是未迁移组件、其施放仍走该 legacy 入口
     * （@Override public void onRightClick(Player, RoleInstance)）⇒ 基类声明暂留，待该组件的施放路径迁到
     * onCast(CastSignal) + markMigrated() 后与 T-1b 一并删除。
     */
    public void onRightClick(Player caster, RoleInstance instance){}


    //技能物品
    //创建一个技能物品
    public ItemStack createIconItem(RoleInstance instance) {
        if(instance == null) return null;
        boolean isReady = instance.isSkillReady(getId());
        boolean canCast = instance.getBuffManager().canCastSkill();
        Material material;

        if (!isReady) {
            material = Material.STRUCTURE_VOID;
        } else if (!canCast) {
            material = Material.BARRIER;
        } else if(instance.getCurrentEnergy() < getEnergyCost()) {
            material = Material.STRUCTURE_VOID;
        }
        else {
            material = getIcon();
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();

        if(!isReady){
            meta.displayName(getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(" " + String.format("%.1f", instance.getRemainingSkillCooldownSeconds(getId())) + "s")
                            .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)));
            lore.add(Component.text("Skill is on cooldown."));
        }
        else if(!canCast){
            meta.displayName(getDisplayName().color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                            .color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text("Skill has been disabled."));
        }
        else if(instance.getCurrentEnergy() < getEnergyCost()){
            meta.displayName(getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(" ENERGY LACK"))
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
        }
        else {
            meta.displayName(getDisplayName().color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            lore.add(Component.text("Skill is ready."));
        }

        lore.add(Component.text("===================="));
        lore.add(getDescription());

        meta.lore(lore);

        meta.getPersistentDataContainer().set(Utils.SKILL_KEY, PersistentDataType.STRING, getId());

        item.setItemMeta(meta);

        return item;


    }

    //获取物品displayName
    public Component getDisplayName(RoleInstance instance){
        if(instance == null){
            return Component.text("RoleInstance is Null!");
        }

        boolean isReady = instance.isSkillReady(getId());
        boolean canCast = instance.getBuffManager().canCastSkill();

        if(!isReady){
            return getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(" " + String.format("%.1f", instance.getRemainingSkillCooldownSeconds(getId())) + "s")
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
        }
        else if(!canCast){
           return getDisplayName().color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }
        else if(instance.getCurrentEnergy() < getEnergyCost()){
            return getDisplayName().color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(" ENERGY LACK"))
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD);
        }
        else {
            return getDisplayName().color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        }

    }

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
