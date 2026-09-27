package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public final class SoundUtil {

    /**
     * **调度宿主**：★ 必须在**方法调用时**取，**不能**做成 `static final` 字段。
     *
     * <p>实测事故：本类曾有 {@code private static final JavaPlugin plugin = ShadowHunterRolesPlugin.getInstance();}
     * —— 静态字段在**类加载**时求值，而那时插件还没 `onEnable()`（单例是在
     * {@code ShadowHunterRolesPlugin#onEnable()} 里才赋值的）⇒ 该字段**恒为 null**
     * ⇒ 三个方法全部在第一行 {@code if(plugin == null) return;} 处**静默返回、一点声音都没有**。
     */
    private static JavaPlugin host() {
        return ShadowHunterRolesPlugin.getInstance();
    }

    public static void playNoticeSuccessCombinedSound(Player player) {
        JavaPlugin plugin = host();
        if(plugin == null){
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                new Consumer<ScheduledTask>() {
                    int count = 0;
                    @Override
                    public void accept(ScheduledTask scheduledTask) {
                        if(!player.isOnline() || player.isDead() || count >= 4){
                            scheduledTask.cancel();
                            return;
                        }
                        if (count == 0) {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
                        } else if (count == 1) {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        } else if (count == 2 || count == 3) {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                        }
                        count += 1;
                    }
                },
                1L, 2L
        );
    }

    public static void playNoticeFailCombinedSound(Player player) {
        JavaPlugin plugin = host();
        if(plugin == null){
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                new Consumer<ScheduledTask>() {
                    int count = 0;
                    @Override
                    public void accept(ScheduledTask scheduledTask) {
                        if(!player.isOnline() || player.isDead() || count >= 3){
                            scheduledTask.cancel();
                            return;
                        }
                        if (count == 0) {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
                        }
                        if(count >= 1){
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
                        }
                        count += 1;
                    }
                },
                1L, 2L
        );
    }

    public static void playGunReloadSound(Player player) {
        JavaPlugin plugin = host();
        if(plugin == null){
            return;
        }
        plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(
                plugin,
                new Consumer<ScheduledTask>() {
                    int count = 0;
                    @Override
                    public void accept(ScheduledTask scheduledTask) {
                        if(!player.isOnline() || player.isDead() || count >= 2){
                            scheduledTask.cancel();
                            return;
                        }
                        if (count == 0) {
                            player.playSound(player.getLocation(), Sound.BLOCK_PISTON_CONTRACT, 1f, 1f);
                        }
                        if(count >= 1){
                            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_HURT, 1f, 1.2f);
                        }
                        count += 1;
                    }
                },
                1L, 4L
        );
    }

}
