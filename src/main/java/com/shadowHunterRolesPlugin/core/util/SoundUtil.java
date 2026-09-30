package com.shadowHunterRolesPlugin.core.util;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * 音效工具：一次性音效串（{@code playNotice*} / {@code playGunReloadSound}）
 * + 可复用的**音符旋律**（{@link Melody} / {@link #playMelody} / {@link #loopMelody}）。
 *
 * <h2>两类东西的差别</h2>
 * 前者是"写死的几拍"（自己数几下就结束，调用方只给玩家）；
 * 后者是"可复用的参数"（哪些音、什么节奏、播几遍、什么时候停）——
 * 每个想播旋律的技能都抄一遍调度代码是没必要的，因此收敛成 {@link Melody} + 两个方法。
 *
 * <h2>音高怎么表达（旋律）</h2>
 * 用**相对基音的半音数**（{@code 0} = 基音、{@code 12} = 高八度、{@code -5} = 低四度），
 * 播放时换算成原版 pitch：{@code pitch = base·2^(semitones/12)}。
 * <p>不直接写原版 {@code 0.5~2.0} 的数：那种数字既看不出音程关系，也很容易越界
 * （越界会被服务端静默钳制，听感就"不对但查不出来"）。
 *
 * <h2>调度口径</h2>
 * 本类统一走 {@code GlobalRegionScheduler}（宿主 = 插件单例）：
 * 现有三个方法用"周期任务 + 自己计数"表达一串音效，旋律用"每个音符一个延迟任务"表达。
 * {@link #playMelody} 返回 {@link ScheduledTask} 列表，调用方**不需要**它时可以不接；
 * 需要提前停时逐个 {@code cancel()} 即可 —— 已取消 / 已跑完的句柄再取消是 no-op。
 *
 * <h2>线程</h2>
 * 全部只读玩家位置并播放声音，均在主线程（全局区域调度器）执行，符合 Bukkit 线程纪律。
 */
public final class SoundUtil {

    /**
     * 调度宿主：必须在方法调用时取，不能做成 `static final` 字段 —— 静态字段在类加载时求值，
     * 而那时插件还没 {@code onEnable()}（单例在 {@code ShadowHunterRolesPlugin#onEnable()} 里才赋值）
     * ⇒ 字段恒为 null ⇒ 所有方法全在第一行 {@code if(plugin == null) return;} 处静默返回、一点声音都没有。
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

    // ───────── 音符旋律（可复用参数）─────────

    /**
     * 一段旋律的**声明**：音色 + 音量 + 一串音高 + 节奏。
     *
     * <p>纯数据（不可变），可以被多个技能共享、也可以作为常量挂在使用方；
     * 不含任何调度状态 —— "播到哪了"由 {@link #playMelody} 的返回值表达。
     *
     * @param sound         音色（原版音符盒的 {@code BLOCK_NOTE_BLOCK_*} 系列最像"音符"）
     * @param volume        单个音符的音量
     * @param basePitch     半音表里 {@code 0} 对应的原版 pitch
     * @param semitones     相对基音的半音数序列（可正可负；空数组 = 没有音符）
     * @param intervalTicks 相邻两个音符的间隔（刻）；{@code <= 0} 归一到 1
     */
    public record Melody(Sound sound, float volume, double basePitch, int[] semitones, long intervalTicks) {

        /** 一个音符的时长（刻）—— 整段旋律的时长 = 它 × 音符数。 */
        public long durationTicks() {
            return Math.max(1L, intervalTicks) * semitones.length;
        }

        /** 第 {@code index} 个音符的原版 pitch（下标越界抛 {@link IndexOutOfBoundsException}）。 */
        public double pitchAt(int index) {
            return pitchOf(semitones[index]);
        }

        /** 半音 → 原版 pitch：{@code basePitch·2^(semitones/12)}。 */
        public double pitchOf(int semitonesFromBase) {
            return basePitch * Math.pow(2d, semitonesFromBase / 12d);
        }
    }

    /**
     * **把一段旋律排出去**（每个音符一个延迟任务，从调用时刻起算）。
     *
     * <p>第一个音符在 {@code intervalTicks} 之后响 —— 与"先放一个起手音效、再进旋律"
     * 的用法对齐（旋律不会盖住起手音）。
     *
     * @param player 听者 / 声源玩家（{@code null} / 空旋律 / 插件未加载 ⇒ 什么都不做，回空表）
     * @param melody 旋律声明
     * @return 已排定的音符任务（顺序 = 播放顺序）；调用方可留存以便提前取消
     */
    public static List<ScheduledTask> playMelody(Player player, Melody melody) {
        List<ScheduledTask> handles = new ArrayList<>();
        JavaPlugin plugin = host();
        if (plugin == null || player == null || melody == null || melody.semitones().length == 0) {
            return handles;
        }
        long interval = Math.max(1L, melody.intervalTicks());
        for (int i = 0; i < melody.semitones().length; i++) {
            final double pitch = melody.pitchAt(i);
            final long delay = interval * (i + 1);
            handles.add(plugin.getServer().getGlobalRegionScheduler().runDelayed(plugin, task -> {
                //听者状态每个音符现查：中途掉线 / 死亡就不必再响了
                if (!player.isOnline() || player.isDead()) {
                    return;
                }
                Location at = player.getLocation();
                player.playSound(at, melody.sound(), melody.volume(), (float) pitch);
            }, delay));
        }
        return handles;
    }

    /**
     * **循环播放一段旋律，直到 {@code shouldContinue} 不再成立**。
     *
     * <h2>为什么用"回调判继续"而不是"返回句柄让调用方取消"</h2>
     * 本工程的组件层有自己的句柄类型（{@code roleComponent.ScheduledHandle}，只有 {@code cancel()} +
     * {@code isCancelled()}），而这里拿到的是 Paper 的 {@link ScheduledTask}
     * （{@code cancel()} 返回枚举）。两者形状不同，为了不把工具层拧进组件层、
     * 也不给组件层加适配包袱，循环的**停止条件**改由调用方用回调表达：
     * 技能只要在回调里回答"我的窗口还开着吗"，其余（什么时候重来、什么时候自己停）都在这里。
     * <p>回调抛出的异常按"停止循环"处理（工具层不替调用方决定重试）。
     *
     * <p>听者掉线 / 死亡一律自停：这是与插件生命周期无关的硬条件，不劳调用方每次都判。
     *
     * @param player         听者 / 声源玩家
     * @param melody         旋律声明
     * @param loopTicks      两遍之间的间隔（刻）；{@code <= 0} 归一到 {@code melody.durationTicks()}
     * @param shouldContinue 每遍开播前的"还要继续吗"；{@code null} = 一直继续
     * @return 循环任务句柄（想提前掐掉也可以自己 {@code cancel()}）；插件未加载 / 参数非法时回 {@code null}
     */
    public static ScheduledTask loopMelody(Player player, Melody melody, long loopTicks,
                                           BooleanSupplier shouldContinue) {
        JavaPlugin plugin = host();
        if (plugin == null || player == null || melody == null || melody.semitones().length == 0) {
            return null;
        }
        long period = loopTicks > 0 ? loopTicks : melody.durationTicks();
        //Paper 的 runAtFixedRate 不接受 initialDelay <= 0 ⇒ 用周期的下一拍（语义同"下一 tick 首次执行"）
        return plugin.getServer().getGlobalRegionScheduler().runAtFixedRate(plugin, task -> {
            if (!player.isOnline() || player.isDead()) {
                task.cancel();
                return;
            }
            if (shouldContinue != null) {
                boolean go;
                try {
                    go = shouldContinue.getAsBoolean();
                } catch (RuntimeException failure) {
                    task.cancel();
                    return;
                }
                if (!go) {
                    task.cancel();
                    return;
                }
            }
            playMelody(player, melody);
        }, period, period);
    }

}
