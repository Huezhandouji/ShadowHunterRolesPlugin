package com.shadowHunterRolesPlugin.core;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
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

import javax.naming.Name;
import java.awt.*;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;


public abstract class Skill {

    private final String id;
    private final Component displayName;
    private final Component description;
    private final int cooldown;
    private final int energyCost;

    private final Material icon;

    public Skill(String id, Component displayName, Component description, int cooldown, int energyCost, Material icon){
        this.id = id;
        this.displayName = displayName;
        this.description = description;
        this.cooldown = cooldown;
        this.energyCost = energyCost;
        this.icon = icon;
    }

    public void onRightClick(Player caster, RoleInstance instance){}

    public void onLeftClick(Player caster, RoleInstance instance){}

    public void onDrop(Player caster, RoleInstance instance){}

    public int getCooldown() { return cooldown; }
    public String getId() { return id; }
    public Component getDisplayName() { return displayName; }
    public Component getDescription() { return description; }
    public int getEnergyCost() { return energyCost; }
    public Material getIcon() { return icon; }


    //技能物品
    //创建一个技能物品
    public ItemStack createIconItem(RoleInstance instance) {
        if(instance == null) return null;
        boolean isReady = instance.isSkillReady(id);
        boolean canCast = instance.getBuffManager().canCastSkill();
        Material material;

        if (!isReady) {
            material = Material.STRUCTURE_VOID;
        } else if (!canCast) {
            material = Material.BARRIER;
        } else {
            material = icon;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        List<Component> lore = new ArrayList<>();

        if(!isReady){
            meta.displayName(displayName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(" " + String.format("%.1f", instance.getRemainingSkillCooldownSeconds(id)) + "s")
                            .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD)));
            lore.add(Component.text("Skill is on cooldown."));
        }
        else if(!canCast){
            meta.displayName(displayName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                            .color(NamedTextColor.RED).decorate(TextDecoration.BOLD));
            lore.add(Component.text("Skill has been disabled."));
        }else {
            meta.displayName(displayName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
            lore.add(Component.text("Skill is ready."));
        }

        lore.add(Component.text("===================="));
        lore.add(description);

        meta.lore(lore);

        meta.getPersistentDataContainer().set(Utils.SKILL_KEY, PersistentDataType.STRING, id);

        item.setItemMeta(meta);

        return item;


    }

    //获取物品displayName
    public Component getDisplayName(RoleInstance instance){
        if(instance == null){
            return Component.text("RoleInstance is Null!");
        }

        boolean isReady = instance.isSkillReady(id);
        boolean canCast = instance.getBuffManager().canCastSkill();

        if(!isReady){
            return displayName.color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD).append(Component.text(" " + String.format("%.1f", instance.getRemainingSkillCooldownSeconds(id)) + "s")
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.BOLD));
        }
        else if(!canCast){
           return displayName.color(NamedTextColor.RED).decorate(TextDecoration.BOLD).append(Component.text(" DISABLED"))
                    .color(NamedTextColor.RED).decorate(TextDecoration.BOLD);
        }else {
            return displayName.color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD);
        }

    }

    public static class Utils{

        public static final NamespacedKey SKILL_KEY = new NamespacedKey(
                ShadowHunterRolesPlugin.getInstance(),
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

            NamespacedKey key = new NamespacedKey(
                    ShadowHunterRolesPlugin.getInstance(),
                    "skill_id"
            );
            PersistentDataContainer container = meta.getPersistentDataContainer();
            if(!container.has(key, PersistentDataType.STRING)) return null;
            return container.get(key, PersistentDataType.STRING);
        }

    }

}
