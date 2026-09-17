package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.skill;

import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.platform.Task;
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

    //O-4：前摇任务句柄化，stop 时取消（阶段 2 换成平台 Task）
    private Task castTask;


    public MeiqiheziCircleSlashSkill(){
        super(
                "meiqihezi_skill_circle_slash",
                Component.text("圆弧斩"),
                Component.text("前摇1秒后对7m范围内所有敌人造成20真实伤害"),
                200,
                15,
                Material.GOLD_INGOT
        );
        markMigrated();
    }

    /**
     * 批次②（B②-b-2）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 能量 = `tryConsume`（旧"先比较再 decreaseEnergy"合一）；`canCastSkill` 不满足 → `NO_COOLDOWN`
     * （旧路径直接 return、**不启冷却**）；冷却改为 `CAST` 由框架按声明值 **200** 启动；
     * 缓慢用 **5 参重载**（`ambient=true, particles=false` 逐字保真，R-1 方法族）；
     * 前摇任务改由 `svc().timers()` 创建（**登记进本组件资源表** ⇒ 角色清除时框架兜底取消）。
     */
    @Override
    public CastResult onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!svc().energy().tryConsume(getEnergyCost())) return CastResult.NO_COOLDOWN;
        if(!svc().buffs().canCastSkill()) return CastResult.NO_COOLDOWN;

        //药水记账（O-7）：经端口施加，clear() 时只回收本系统施加的效果（标志位与旧写法逐字一致）
        svc().buffs().applyPotionEffect(PotionEffectType.SLOWNESS, 20, 2, true, false);

        Location loc = caster.getLocation();

        loc.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);



        castTask = svc().timers().runLater(20L, new Runnable(){

            @Override
            public void run() {
                if(caster.isDead() || !caster.isOnline()){
                    castTask.cancel();
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

                for(Player victim : victims){
                    if (!svc().factions().isHostile(victim)) continue;
                    svc().damage().trueDamage(victim, caster, 20);
                }

                loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 1f);
            }
        });
        return CastResult.CAST;
    }

    @Override
    public void start() {
    }

    /**
     * 新基类（RoleComponent）的停止钩子（阶段 4 B②-c）：容器在 legacy 扇出之后、`cancelAllAndClear()`
     * **之前**广播 ⇒ 与旧 `LifecycleAware.stop(...)` 的行为等价（O-4 的前摇取消）；框架还会兜底取消本组件
     * 资源表内的任务（重复取消幂等）。
     */
    @Override
    public void stop() {
        if(castTask != null){
            castTask.cancel();
            castTask = null;
        }
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
