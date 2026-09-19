package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.*;


public class MeiqiheziBloodySlashSkill extends Skill {

    public MeiqiheziBloodySlashSkill(){
        super(
                "meiqihezi_skill_bloody_slash",
                Component.text("血腥连斩"),
                Component.text("向前移动4格并斩击，重复四次"),
                160,
                8,
                Material.IRON_INGOT
        );
    }


    @Override
    public void onRightClick(Player caster, RoleInstance instance) {
        if (instance.getCurrentEnergy() < getEnergyCost()) return;
        if(!instance.getBuffManager().canCastSkill()) return;
        instance.decreaseEnergy(getEnergyCost());

        instance.startSkillCooldown(getId(), 160);
        new BukkitRunnable() {

            private int count = 0;
            private final Player player = caster;

            @Override
            public void run() {
                if (count >= 4) {
                    this.cancel();
                    return;
                }
                count += 1;

                Location loc = player.getLocation();
                Vector dir = loc.getDirection();
                player.setVelocity(dir.clone().setY(0).normalize().multiply(1.5));

                Collection<? extends Player> victims = loc.getNearbyPlayers(4);

                for (Player victim : victims) {
                    if (!instance.isHostileTo(victim)) continue;
                    victim.setNoDamageTicks(0);
                    victim.damage(14, player);
                }

                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1f);
                Location particleLoc = loc.clone().add(0, 1, 0);
                particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 30, 0.5, 0.5, 0.5, 0, dust);

                loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1f);
            }

        }.runTaskTimer(ShadowHunterRolesPlugin.getInstance(), 0L, 2L);

    }

    @Override
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

    @Override
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
}
