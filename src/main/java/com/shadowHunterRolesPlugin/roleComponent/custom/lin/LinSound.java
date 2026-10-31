package com.shadowHunterRolesPlugin.roleComponent.custom.lin;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「林」的**纯音效工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求的音效播出去。边界：不读玩家状态、不写状态、不注册任务、不调用组件；
 * 传入 {@code null} 一律静默返回。
 *
 * <p>命名规范（用户定的规矩）：{@code <主要音效特制> + Lin + <技能> + <阶段> + 音效}。
 */
public final class LinSound {

    private LinSound() {
    }

    // ───────── ① 铁血镇压者 ─────────

    /**
     * **武器破碎音效**（需求："击中敌人带有武器破碎音效"）。
     *
     * <p>取 {@code ENTITY_ITEM_BREAK}（物品碎裂）+ 一记 {@code ITEM_SHIELD_BREAK} 的钝响，
     * 两层叠加才像"武器被打碎"而不是"捡到东西"。
     */
    public static void ironBloodShatterSound(World world, Location at) {
        play(world, at, Sound.ENTITY_ITEM_BREAK, 1f, 0.85f);
        play(world, at, Sound.ITEM_SHIELD_BREAK, 0.55f, 1.4f);
    }

    /** 镇压命中的一记闷响（衬在破碎音之后，让打击有重量）。 */
    public static void ironBloodImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 0.75f);
    }

    // ───────── ② 开普勒装置 ─────────

    /** **每秒的轻声经验吸取声音**（需求："带有每秒的轻声经验吸取声音"）。 */
    public static void keplerSiphonSound(World world, Location at) {
        play(world, at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.28f, 1.75f);
    }

    // ───────── ③ 殉道 ─────────

    /** 殉道瞬间的钟声 + 庄严和弦（"==殉道=="的听觉落点）。 */
    public static void martyrSound(World world, Location at) {
        play(world, at, Sound.BLOCK_BELL_RESONATE, 1f, 0.7f);
        play(world, at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.6f);
        play(world, at, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.8f);
    }

    // ───────── ④ 三重打击 ─────────

    /**
     * **三重打击释放音：信标生成声**（需求 2026-10-08："释放时音效是信标生成时的声音"）。
     *
     * <p>取 {@code BLOCK_BEACON_ACTIVATE} —— 原版信标激活/生成时的那记上扬和声。
     * <p>2026-10-31 需求："稍微加大一点…让它更明显、更惊艳" ⇒ 主音音量 **0.9 → 1.0**，
     * 并叠两层同色上扬音：导管激活（次中音）+ 紫晶钟（高音）。三音同时响成和弦，
     * 既保留"信标生成"的原需求音色，又让释放瞬间更厚、更抓耳。
     */
    public static void tripleStrikeCastSound(World world, Location at) {
        play(world, at, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1f);
        play(world, at, Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 1.25f);
        play(world, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.6f);
    }

    /**
     * **TNT 爆炸音效**（需求："TNT爆炸音效"；用于"每一次强化攻击命中"）。
     *
     * <p>2026-10-31 需求："稍微加大一点…更明显、更惊艳" ⇒ TNT 主音 **0.8 → 1.0**，
     * 另叠一记暴击的金属脆响（次中音）与紫晶碎裂（高音）：
     * 爆炸负责"重"，脆响负责"准"，紫晶负责"贵"，三层才像一次被强化过的打击。
     */
    public static void tripleStrikeExplosionSound(World world, Location at) {
        play(world, at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
        play(world, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, 1.45f);
        play(world, at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.55f, 1.6f);
    }

    /**
     * **第三层（最后一击）的收尾重音**（2026-10-31 新增："更惊艳"的仪式感）。
     *
     * <p>与普通命中同一颗 TNT 主音，再补"雷 + 钟 + 高音晶鸣"三件：雷声给爆发，
     * 钟声给收束，高音紫晶把余韵拉到空中。三层强化打完时响一次即可，
     * 由组件按"消费后层数归零"判定，不额外占用调度。
     */
    public static void tripleStrikeFinisherSound(World world, Location at) {
        tripleStrikeExplosionSound(world, at);
        play(world, at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45f, 1.35f);
        play(world, at, Sound.BLOCK_BELL_RESONATE, 0.55f, 1.15f);
        play(world, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 2.0f);
    }

    // ───────── ⑤ 影袭 ─────────

    /** **跃进音效**（需求："播放跃进音效"）。 */
    public static void shadowRaidDashSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.8f, 1.5f);
        play(world, at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.45f, 1.7f);
    }

    /** 影袭造成伤害时的一记紫晶脆响。 */
    public static void shadowRaidHitSound(World world, Location at) {
        play(world, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
        play(world, at, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.7f, 1.2f);
    }

    // ───────── ⑥ 透骨钉 ─────────

    /** 透骨钉就绪（拿在手上时的充能提示，由组建每 N 刻轻响一次）。 */
    public static void boneNailChargeSound(World world, Location at) {
        play(world, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.18f, 1.9f);
    }

    /**
     * **透骨钉开火：1.5 倍速烈焰人受击音 + 1.5 倍速火焰弹爆炸音**（需求 2026-10-08）。
     *
     * <p>★ pitch 1.5 = "1.5 倍速"（原版 pitch 1.0 即原速），两者都抬到 1.5。
     * <p>★ "火焰弹爆炸"用 {@code ENTITY_DRAGON_FIREBALL_EXPLODE} —— 1.21.11 里
     * <b>没有</b> {@code ENTITY_FIREBALL_EXPLODE}（那是旧版名字），火焰弹系只有这一个爆炸音。
     */
    public static void boneNailShotSound(World world, Location at) {
        play(world, at, Sound.ENTITY_BLAZE_HURT, 1f, 1.5f);
        play(world, at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.85f, 1.5f);
    }

    /** 透骨钉命中。 */
    public static void boneNailHitSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_HIT, 1f, 1.35f);
        play(world, at, Sound.BLOCK_ANVIL_LAND, 0.45f, 1.7f);
    }

    /**
     * **被钉墙的顿挫声**（需求："撞墙则无法移动/致盲/沉默"；2026-10-08 追加"钉到墙面"的听感）。
     *
     * <p>保留原石碎裂的顿响，叠一层"闪电击铜块"的脆响（{@code BLOCK_COPPER_HIT} + 电弧）。
     */
    public static void boneNailWallStopSound(World world, Location at) {
        play(world, at, Sound.BLOCK_STONE_BREAK, 1f, 0.6f);
        play(world, at, Sound.ENTITY_ITEM_BREAK, 0.6f, 0.7f);
        play(world, at, Sound.BLOCK_COPPER_HIT, 0.9f, 1.4f);
    }

    /**
     * **透骨钉"转好可释放"的提示三连**（需求 2026-10-08）：
     * <ol>
     *   <li>1.5 倍速活塞推出音（立即）；</li>
     *   <li>0.3 秒（6 刻）后 2 倍速活塞拉回音；</li>
     *   <li>再 0.1 秒（2 刻）后 2 倍速烈焰人受击音。</li>
     * </ol>
     *
     * <p>★ 需要**跨刻延时** ⇒ 本方法只负责"第 1 声 + 排定后两声"，
     * 由调用方（组件）提供调度宿主与"玩家是否还在线/还是本技能"的守卫。
     * 这里返回的是后两声的**相对刻偏移**，让组件用同一份时间表去排。
     */
    public static void boneNailReadyShiftSound(World world, Location at) {
        play(world, at, Sound.BLOCK_PISTON_EXTEND, 0.9f, 1.5f);
    }

    /** 就绪三连的第 2 声（2 倍速活塞拉回）—— 由组件在第 6 刻回调。 */
    public static void boneNailReadyContractSound(World world, Location at) {
        play(world, at, Sound.BLOCK_PISTON_CONTRACT, 0.9f, 2f);
    }

    /** 就绪三连的第 3 声（2 倍速烈焰人受击）—— 由组件在第 8 刻回调。 */
    public static void boneNailReadyBlazeSound(World world, Location at) {
        play(world, at, Sound.ENTITY_BLAZE_HURT, 0.8f, 2f);
    }

    /** 就绪三连的刻偏移：第 2 声（活塞拉回）。 */
    public static final int READY_CONTRACT_DELAY_TICKS = 6;

    /** 就绪三连的刻偏移：第 3 声（烈焰人受击），相对第 1 声。 */
    public static final int READY_BLAZE_DELAY_TICKS = 8;

    // ───────── 内部 ─────────

    private static void play(World world, Location at, Sound sound, float volume, float pitch) {
        if (world == null || at == null || sound == null) {
            return;
        }
        world.playSound(at, sound, volume, pitch);
    }
}
