package com.shadowHunterRolesPlugin.roleComponent.custom.tek;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「特克」的**纯音效工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求的听感映射成"在某处播哪个原版音效"。
 * 命名口径（工程约定）：{@code <主要音效特制> + 特克 + <技能> + <阶段> + 音效}。
 *
 * <p><b>为什么独立成类</b>：同 {@link TekVfx} —— 专属支持类放组件同级目录，不进组件、更不进框架。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务、不调用组件；传入 {@code null} 一律静默返回。
 * 音效 / 音量 / 音高集中在常量里，要调听感只改那一处。
 */
public final class TekSound {

    private TekSound() {
    }

    // ───────── 主武器：逆命天理 ─────────

    /** 普攻命中：金铁相击（三叉戟挥砍的替代音）。 */
    public static void tridentHitSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_HIT, 0.9f, 1.25f);
    }

    /** 普攻命中附加真实伤害时的低频闷响（"真理"被写进去）。 */
    public static void tridentTrueHitSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_THUNDER, 0.35f, 1.6f);
    }

    /** 蓄力开始的提示音（按住右键的瞬间）。 */
    public static void chargeStartSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.7f, 0.9f);
    }

    /**
     * **蓄力中**的连续音效（需求：蓄力过程中会有蓄力音效）。
     *
     * <p>音高随蓄力进度**逐级升高** ⇒ 听感是"充能越来越满"。
     *
     * @param index 第几个音符（从 0 起）
     * @param total 总共几个音符（&le; 0 时按 1 处理）
     */
    public static void chargeUpNote(World world, Location at, int index, int total) {
        int steps = Math.max(1, total);
        int clamped = Math.max(0, Math.min(index, steps - 1));
        //0.7 → 1.9 逐级升高（原版 pitch 有效区间约 0.5~2.0，越过会被静默钳制）
        float pitch = 0.7f + (1.2f * clamped / steps);
        play(world, at, Sound.BLOCK_NOTE_BLOCK_HARP, 0.9f, pitch);
        play(world, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.25f, pitch);
    }

    /** **蓄力已满**（达到 0.8 秒、等待松手）：一声清脆的"就绪"提示。 */
    public static void chargeArmedSound(World world, Location at) {
        play(world, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.9f);
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.6f, 1.7f);
    }

    /** **蓄力作废**（没满 0.8 秒就松手）：一声低沉的落空音。 */
    public static void chargeAbortSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    /** 蓄力完成、突进瞬间（0.8 秒到点）。 */
    public static void dashLaunchSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.1f);
    }

    /** 突进命中敌人。 */
    public static void dashImpactSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.3f);
    }

    /** 突进冷却完毕（武器上的附魔光效提示同时生效）。 */
    public static void dashReadySound(World world, Location at) {
        play(world, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.8f);
    }

    // ───────── 被动：逆转天意 ─────────

    /** 每次命中减少技能 CD 的细碎回响（安静，避免刷屏）。 */
    public static void destinyTickSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BIT, 0.45f, 1.9f);
    }

    /** 达到 90 能量、护盾触发。 */
    public static void destinyShieldSound(World world, Location at) {
        play(world, at, Sound.ITEM_TOTEM_USE, 0.6f, 1.5f);
        play(world, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f);
    }

    // ───────── 刺霄 · 回响碎片 ─────────

    /** 刺出瞬间的破空声。 */
    public static void xiaoThrustSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.9f, 1.4f);
        play(world, at, Sound.ENTITY_ARROW_SHOOT, 0.6f, 1.6f);
    }

    /** 瞬移到最远敌人身后的闪现声。 */
    public static void xiaoBlinkSound(World world, Location at) {
        play(world, at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
    }

    /** 身后被阻挡、没有瞬移时的落空提示。 */
    public static void xiaoBlockedSound(World world, Location at) {
        play(world, at, Sound.BLOCK_ANVIL_LAND, 0.35f, 1.9f);
    }

    // ───────── 落岳 · 凝灰岩 ─────────

    /** 跃起时的上升音。 */
    public static void yueLeapSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.5f);
    }

    /**
     * **在空中再次释放 ⇒ 切换为加速下落**的提示音。
     * <p>需求里这是"第二段"，所以要与跃起声（{@link #yueLeapSound}）和落地声
     * （{@link #yueImpactSound}）都区分开：用一声下沉感的哨音 + 破空。
     */
    public static void yueAccelerateSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.9f, 0.8f);
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 1.4f);
    }

    /** 落地爆炸的重击音（同时配眩晕）。 */
    public static void yueImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.1f);
        play(world, at, Sound.BLOCK_STONE_BREAK, 1f, 0.7f);
    }

    /**
     * **没能落地就收工**（虚空 / 落点站不住）：一声落空音。
     * <p>此时**不**放爆炸音、也不结算伤害（需求：必须完全落地才释放落地攻击）。
     */
    public static void yueAbortSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.6f);
    }

    // ───────── 真理之刺 · 下界之心 ─────────

    /** 解锁 / 施放：下界之心的低频轰鸣（"当场上有真理≥10 的角色"时才响）。 */
    public static void verdictCastSound(World world, Location at) {
        play(world, at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.85f);
        play(world, at, Sound.ITEM_TRIDENT_THUNDER, 0.8f, 0.9f);
    }

    /** 贯穿命中的真实伤害重击。 */
    public static void verdictStrikeSound(World world, Location at) {
        play(world, at, Sound.ENTITY_WITHER_HURT, 0.6f, 1.6f);
    }

    /** 收走全场真理 + 回复自身生命。 */
    public static void verdictConsumeSound(World world, Location at) {
        play(world, at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.9f, 1.4f);
        play(world, at, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
    }

    /** 技能未解锁（场上没有真理≥10 的角色）时的拒绝音。 */
    public static void verdictLockedSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
    }

    /**
     * **某个敌人的真理层数刚刚达到"解锁阈值"的提示音**（需求：达到 10 层时提示一次）。
     *
     * <p>★ 三级上行 + 铃音，听感是"来了/可以了"，与其它音效（蓄力、落地、刺出）都区分得开。
     * <p>只对**特克自己**响（它是"我的终结技解锁了"的信号）。
     */
    public static void truthUnlockedAlertSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.2f);
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.5f);
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.9f);
        play(world, at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
    }

    // ───────── 内部 ─────────

    /** 空安全的播放（{@code world}/{@code at} 为 null 时静默返回）。 */
    private static void play(World world, Location at, Sound sound, float volume, float pitch) {
        if (world == null || at == null || sound == null) {
            return;
        }
        world.playSound(at, sound, volume, pitch);
    }
}
