package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.LifecycleAware;
import com.shadowHunterRolesPlugin.core.RoleComponentAware.SanTEChangeAware;
import com.shadowHunterRolesPlugin.platform.Task;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.time.Duration;

public class DefaultSanTEZeroPunishment extends PassiveSkill implements SanTEChangeAware, LifecycleAware {
    public DefaultSanTEZeroPunishment() {
        super(
                "default_san_te_zero_punishment",
                null,
                null
        );
    }

    //O-6：任务句柄（阶段 2 换成平台 Task，null = 没有任务在跑）
    private Task punishmentTask;

    @Override
    public void onSanTEChange(Player player, RoleInstance instance, int preSanTE, int newSanTE) {
        if(newSanTE > 0) return;
        Faction faction = instance.getFaction();

        //O-6：重入保护 —— 先取消仍在跑的旧惩罚任务再起新任务
        //（原实现直接覆盖 taskId，旧任务永远无法取消，泄漏且会在结束后改写 SanTE）
        cancelPunishmentTask();

        punishmentTask = instance.rolesContext().scheduler().runRepeating(new Runnable() {

                    int count = 0;
                    Player player = instance.getPlayer();

                    double totalDamageAmount = player.getAttribute(Attribute.MAX_HEALTH) != null ?
                            player.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.3d : 20;

                    @Override
                    public void run() {
                        if (punishmentTask == null || punishmentTask.isCancelled()) return;
                        if (!player.isOnline() || player.isDead()) {
                            punishmentTask.cancel();
                            return;
                        }


                        if (count >= 3) {
                            punishmentTask.cancel();
                            return;
                        }
                        count += 1;

                        Location loc = player.getLocation();

                        if (count == 1) {
                            instance.setIsInSanTEPunishmentState(true);

                            instance.getBuffManager().addBuff(BuffType.STUN, 100);
                            player.playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1f);

                            Location particleLoc = loc.clone().add(0, 1, 0);
                            particleLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, particleLoc, 30, 0.5d, 0.5d, 0.5d);
                            particleLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 30, 0.5d, 0.5d, 0.5d);

                            Title title = Title.title(
                                    Component.empty(),
                                    Component.empty()
                            );
                            if (faction == Faction.HUNTER) {
                                title = Title.title(
                                        Component.text("⊠恐惧正在注视着你⊠", NamedTextColor.LIGHT_PURPLE),
                                        Component.empty(),
                                        Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(5000L),
                                                Duration.ZERO
                                        )
                                );
                            } else if (faction == Faction.SHADOW) {
                                title = Title.title(
                                        Component.text("⊠痛苦摸上脊背⊠", NamedTextColor.YELLOW),
                                        Component.empty(),
                                        Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(5000L),
                                                Duration.ZERO
                                        )
                                );
                            }

                            player.showTitle(title);

                        }

                        if (count != 1) {
                            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.1f);
                            Location particleLoc = loc.clone().add(0, 1, 0);
                            particleLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 30, 0.5d, 0.5d, 0.5d);
                        }
                        DamageUtil.dealtTrueDamage(player, null, totalDamageAmount * 0.33333d);

                        if (count == 3) {
                            instance.setIsInSanTEPunishmentState(false);
                            instance.setCurrentSanTE(instance.getMaxSanTE());
                            instance.setIsInSanTEPunishmentState(false);
                        }
                    }
                }, 0L, 40L);
    }

    @Override
    public void start(Player player, RoleInstance instance) {

    }

    //取消仍在运行的惩罚任务并复位句柄（O-6）
    private void cancelPunishmentTask(){
        if(punishmentTask != null){
            punishmentTask.cancel();
            punishmentTask = null;
        }
    }

    @Override
    public void stop(Player player, RoleInstance instance) {
        cancelPunishmentTask();
    }
}
