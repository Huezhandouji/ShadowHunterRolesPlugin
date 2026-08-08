package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill;

import com.destroystokyo.paper.ParticleBuilder;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MeiqiheziCircleSlashSkill extends Skill {

    public MeiqiheziCircleSlashSkill(){
        super(
                "meiqihezi_skill_circle_slash",
                Component.text("圆弧斩"),
                Component.text("前摇1秒后对7m范围内所有敌人造成20真实伤害"),
                200,
                15,
                Material.GOLD_INGOT
        );
    }

    @Override
    public void onRightClick(Player caster, RoleInstance instance){
        if (instance.getCurrentEnergy() < getEnergyCost()) return;
        if(!instance.getBuffManager().canCastSkill()) return;
        instance.decreaseEnergy(getEnergyCost());
        instance.startSkillCooldown(getId(), getCooldown());

        PotionEffect slowness = new PotionEffect(PotionEffectType.SLOWNESS, 20, 2, true, false);
        caster.addPotionEffect(slowness);

        Location loc = caster.getLocation();

        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);



        new BukkitRunnable(){

            @Override
            public void run() {
                if(caster.isDead() || !caster.isOnline()){
                    this.cancel();
                    return;
                }

                Location loc = caster.getLocation();
                ParticleBuilder pb = Particle.DUST.builder()
                        .count(1)
                        .color(Color.RED)
                        .offset(0, 0, 0);

                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                ParticleUtil.drawCircle(loc.clone().add(0, 1, 0), 7, pb, 80);

                Collection<? extends Player> victims = loc.getNearbyPlayers(7);

                RoleManager roleManager = RoleManager.getInstance();

                for(Player victim : victims){
                    if (!instance.isHostileTo(victim)) continue;
                    DamageUtil.dealtTrueDamage(victim, caster, 20);
                }

                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1f);
            }
        }.runTaskLater(ShadowHunterRolesPlugin.getInstance(), 20L);
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
