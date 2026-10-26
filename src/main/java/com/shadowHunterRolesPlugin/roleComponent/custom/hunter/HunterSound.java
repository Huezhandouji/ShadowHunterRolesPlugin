package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「猎手」的**纯音效工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求的听感映射成"在某处播哪个原版音效"。
 * 命名口径（工程约定）：{@code <主要音效特制> + Hunter + <技能> + <阶段> + Sound}。
 *
 * <p><b>为什么独立成类</b>：同 {@link HunterVfx} —— 专属支持类放组件同级目录，
 * 不进组件、更不进框架（工程纪律：不改 `roleComponent/SoundUtil`，那是上游文件）。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；
 * 传入 {@code null} 一律静默返回。音效 / 音量 / 音高集中在方法体里，
 * 要调听感只改对应那一行。
 *
 * <h2>需求对应</h2>
 * <ul>
 *   <li>敌人被<b>初次标记</b> ⇒ 铁砧落地 {@link #anvilLandHunterPreyMarkSound}；</li>
 *   <li>被标记者挨<b>普攻</b> ⇒ <b>2 倍速</b>的僵尸村民转变
 *       {@link #zombieVillagerCureFastHunterGrudgeMarkedHitSound}；</li>
 *   <li>拉回<b>引导</b> ⇒ 蓄力 {@link #crossbowLoadHunterPullChannelSound}；</li>
 *   <li>拉回<b>投出</b> ⇒ 三叉戟投出 {@link #tridentThrowHunterPullCastSound}；</li>
 *   <li>拉回<b>命中</b> ⇒ 命中 {@link #tridentHitHunterPullImpactSound}；</li>
 *   <li>拉回<b>加速拖拽</b> ⇒ {@link #riptideHunterPullAccelerateSound}；</li>
 *   <li>扑击<b>突进</b> ⇒ 破空 {@link #riptideHunterPounceDashSound}；</li>
 *   <li>扑击<b>撕咬</b> ⇒ 唤魔者尖牙咬合 {@link #evokerFangsHunterPounceBiteSound}；</li>
 *   <li>遁形<b>开启</b> ⇒ 三叉戟落雷 {@link #tridentThunderHunterStealthCastSound}。</li>
 * </ul>
 */
public final class HunterSound {

    private HunterSound() {
    }

    // ───────── 猎杀（被动）─────────

    /**
     * **敌人被初次标记** —— 铁砧落地（需求原话：「敌人被初次标记会产生铁砧落地的音效」）。
     *
     * <p>★ 只在**初次**标记（该敌人从"没标记"变成"有标记"）时播；
     * CD 刷新导致的重放标记**不播**这个音 —— 否则每 6 秒一次铁砧噪音，
     * 且会与"初次"这个语义脱钩。
     */
    public static void anvilLandHunterPreyMarkSound(World world, Location at) {
        play(world, at, Sound.BLOCK_ANVIL_LAND, 1f, 1f);
    }

    // ───────── 遗愤（主武器）─────────

    /**
     * **被标记的敌人挨普攻** —— **2 倍速**的僵尸村民转变音效
     * （需求原话：「敌人在被标记的情况下被普通攻击会发出2倍速的僵尸村民转变音效」）。
     *
     * <p>原版音效默认 pitch 1.0；这里显式传 {@value #DOUBLE_SPEED_PITCH} ⇒ 播放速度翻倍。
     */
    public static void zombieVillagerCureFastHunterGrudgeMarkedHitSound(World world, Location at) {
        play(world, at, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, DOUBLE_SPEED_PITCH);
    }

    /** "2 倍速"的音高值（原版 pitch 的有效区间约 0.5~2.0）。 */
    public static final float DOUBLE_SPEED_PITCH = 2f;

    // ───────── 拉回（技能二）─────────

    /**
     * **引导（蓄力）开始** —— **2 倍速**播放的弩蓄力音（需求原话：
     * 「拉回是先蓄力0.3秒，后投出……蓄力改为0.3秒倍速播放的弩蓄力」）。
     *
     * <p>时长由调用方控制（{@code HunterPullSkill#CHANNEL_TICKS} = 6 刻 = 0.3 秒）；
     * 这里用 {@link #DOUBLE_SPEED_PITCH}（pitch 2.0）让同一段音在**一半时间**里放完 ⇒ "倍速"。
     * <p>★ 改前是"弩装填音 pitch 1.1 + 一层信标音"，两个音都不符合"倍速"这条口径 ⇒ 改成单音 + 2 倍速。
     */
    public static void crossbowLoadHunterPullChannelSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 1f, DOUBLE_SPEED_PITCH);
    }

    /**
     * **蓄力结束、技能投出** —— 三叉戟投出音（需求原话：
     * 「蓄力完成后投出技能，播放三叉戟投出音效」）。
     */
    public static void tridentThrowHunterPullCastSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_THROW, 1f, 1.2f);
    }

    /** **命中敌人** —— 穿刺命中（需求原话：「命中敌人后也加个命中音效」）。 */
    public static void tridentHitHunterPullImpactSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_HIT, 1f, 1.1f);
        play(world, at, Sound.ITEM_TRIDENT_RETURN, 0.5f, 1.6f);
    }

    /** **再次释放 ⇒ 加速拖拽**（需求：命中后不进入 CD，可再次释放加速拖拽）。 */
    public static void riptideHunterPullAccelerateSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.9f, 1.4f);
    }

    // ───────── 扑击（技能一）─────────

    /** **突进破空** —— 三叉戟激流冲刺（需求原话：「1技能给一个突进时破空音效」）。 */
    public static void riptideHunterPounceDashSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.3f);
    }

    /** **撕咬** —— 唤魔者尖牙咬合（需求原话：「撕咬时给一个唤魔者尖牙咬合音效」）。 */
    public static void evokerFangsHunterPounceBiteSound(World world, Location at) {
        play(world, at, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1f, 1.2f);
    }

    // ───────── 遁形（技能三 / 大招）─────────

    /** **开启** —— 三叉戟落雷（需求原话：「大招开启时给一个三叉戟落雷音效」）。 */
    public static void tridentThunderHunterStealthCastSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_THUNDER, 1f, 1f);
        play(world, at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.1f);
    }

    // ───────── 内部 ─────────

    /** 空安全的播放（{@code world} / {@code at} / {@code sound} 任一为 {@code null} 时静默返回）。 */
    private static void play(World world, Location at, Sound sound, float volume, float pitch) {
        if (world == null || at == null || sound == null) {
            return;
        }
        world.playSound(at, sound, volume, pitch);
    }
}
