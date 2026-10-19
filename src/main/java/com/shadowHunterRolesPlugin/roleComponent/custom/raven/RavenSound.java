package com.shadowHunterRolesPlugin.roleComponent.custom.raven;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「绯红渡鸦 · 林晨霜」的**纯音效工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>职责单一：把需求的音效播出去。边界：不读玩家状态、不写状态、不注册任务、不调用组件；
 * 传入 {@code null} 一律静默返回。
 *
 * <p>命名规范（用户定的规矩）：{@code <主要音效特质> + Raven + <技能> + <阶段> + Sound}。
 *
 * <h2>★ 所有 {@link Sound} 常量都经 {@code javap} 从 paper-api 1.21.11 的常量表核实过存在</h2>
 * {@code Sound} 是枚举 ⇒ 离线 {@code valueOf} 会抛 {@code No RegistryAccess implementation found}，
 * 因此本文件里的每一个名字都是"反编译 jar 抄下来的"，不是猜的。
 */
public final class RavenSound {

    private RavenSound() {
    }

    // ───────── ① 义体与散华左轮：射击 ─────────

    /**
     * **全威力弹击发**（需求："发射一枚全威力弹"；2026-10-11 追加"2 倍速烈焰人受击音效"）。
     *
     * <p>弩机释放 + 一支箭被射出，再叠一层被抬到 <b>2 倍速</b>的烈焰人受击 —— 三层才像"散华"的枪声。
     */
    public static void ravenRevolverShotSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_SHOOT, 1f, 1.15f);
        play(world, at, Sound.ENTITY_ARROW_SHOOT, 0.55f, 1.6f);
        //需求 2026-10-11："射击时的音效加一个 2 倍速的烈焰人受击音效"
        play(world, at, Sound.ENTITY_BLAZE_HURT, 0.6f, 2f);
    }

    /** 空仓扣扳机（需求未直接写，但"六枚弹药"用尽时必须有反馈，否则玩家以为卡了）。 */
    public static void ravenRevolverDryFireSound(World world, Location at) {
        play(world, at, Sound.BLOCK_LEVER_CLICK, 0.7f, 0.6f);
        play(world, at, Sound.BLOCK_DISPENSER_FAIL, 0.4f, 1.4f);
    }

    /** 子弹命中敌人体（"每枚造成 10 点物理伤害与 3~6 点特殊值伤害"的听觉落点）。 */
    public static void ravenBulletHitSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_HIT, 1f, 1.2f);
        play(world, at, Sound.ENTITY_ARROW_HIT_PLAYER, 0.6f, 1.5f);
    }

    /** 子弹撞墙消失（需求："撞墙消失"）。 */
    public static void ravenBulletWallSound(World world, Location at) {
        play(world, at, Sound.BLOCK_STONE_BREAK, 0.45f, 1.7f);
        play(world, at, Sound.ENTITY_ITEM_BREAK, 0.3f, 1.3f);
    }

    // ───────── ② 换弹 ─────────

    /**
     * 换弹开始（需求："在地面时丢出武器视为为武器换弹"；
     * 2026-10-11 追加"换弹时的音效加一个 1.5 倍速活塞推出的音效"）。
     */
    public static void ravenReloadStartSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_LOADING_START, 0.9f, 1.1f);
        //需求 2026-10-11：1.5 倍速活塞推出
        play(world, at, Sound.BLOCK_PISTON_EXTEND, 0.85f, 1.5f);
    }

    /** 每装一发（需求："换弹时间为 0.5S 每发"）—— 组件会每 10 刻回调一次。 */
    public static void ravenReloadClipSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.7f, 1.25f);
    }

    /** 换弹完成 / 被塞满（需求："每装填一发发子弹 2 能量"的收尾声）。 */
    public static void ravenReloadFinishSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_LOADING_END, 0.95f, 1.1f);
    }

    /** 换弹被打断（需求："点击左键视为打断换弹操作"）。 */
    public static void ravenReloadInterruptSound(World world, Location at) {
        play(world, at, Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.1f);
    }

    // ───────── ③ 浮空机动 ─────────

    /** 进入浮空机动（需求："短暂滞空，可缓慢移动与射击"）。 */
    public static void ravenHoverEnterSound(World world, Location at) {
        play(world, at, Sound.ENTITY_BREEZE_WHIRL, 0.5f, 1.3f);
        play(world, at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 1.9f);
    }

    /** 浮空结束（触地或超时）。 */
    public static void ravenHoverExitSound(World world, Location at) {
        play(world, at, Sound.ENTITY_BREEZE_IDLE_AIR, 0.4f, 0.85f);
    }

    /**
     * **主动解除浮空开始下落**（需求 2026-10-11："0.2S 内双击空格关闭悬停状态开始下落" /
     * "按住蹲的快速落地"）—— 一声"泄气"式的下坠提示，与"超时结束"的轻响区分开。
     */
    public static void ravenHoverDropSound(World world, Location at) {
        play(world, at, Sound.ENTITY_BREEZE_SLIDE, 0.7f, 0.9f);
        play(world, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.4f, 0.7f);
    }

    /** 空中主动机动：前冲（需求："向前位移 8 格，并再次触发浮空机动状态"）。 */
    public static void ravenAirDashSound(World world, Location at) {
        play(world, at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.75f, 1.5f);
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.7f, 1.6f);
    }

    /** 空中快速装填（需求："改为快速装填 2 枚子弹"）。 */
    public static void ravenAirQuickLoadSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_QUICK_CHARGE_3, 0.8f, 1.4f);
    }

    /**
     * 切换瞄准模式（需求 2026-10-11："当悬浮状态时，手持主武器左键攻击切换状态时的提示音【经验的叮】"）。
     *
     * <p>★ 需求写的就是**经验球的"叮"** ⇒ 两个方向都是同一声（摘掉原版的望远镜音）。
     */
    public static void ravenAimModeToggleSound(World world, Location at, boolean lockMode) {
        play(world, at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.85f, lockMode ? 1.4f : 1.8f);
    }

    // ───────── ④ 锁定射击 ─────────

    /** 蓄力开始（需求："右键将在 0.8S 内蓄力"）。 */
    public static void ravenLockChargeSound(World world, Location at) {
        play(world, at, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.65f, 1.5f);
    }

    /**
     * **锁定确立音效**（需求："锁定时会有锁定音效"）。
     *
     * <p>取远古守卫者的诅咒（一记低沉上扬的"被盯上"感）+ 信标选力，两层叠出锁定感。
     */
    public static void ravenLockOnSound(World world, Location at) {
        play(world, at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.25f);
        play(world, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
    }

    /** 蓄力后判定"周围无敌人"⇒ 直接以准心射一发（需求原话）。 */
    public static void ravenLockNoTargetSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
    }

    /**
     * **锁定成功**（需求 2026-10-11："锁定成功后给一个提示音效"）。
     *
     * <p>取一记**钟**（{@code BLOCK_NOTE_BLOCK_BELL}）打底 + 一条经验"叮"，
     * 与"蓄力中"的守卫者音、与"射击"的弩音都能分开 —— 玩家能明确听到"锁上了"。
     */
    public static void ravenLockSuccessSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.4f);
        play(world, at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.9f);
    }

    /** 逐发轮流射出时每一发的击发提示（比普通射击更轻，避免三连太吵）。 */
    public static void ravenLockVolleySound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_SHOOT, 0.7f, 1.45f);
    }

    // ───────── ⑤ 机动充能 ─────────

    /** 存下一层机动（需求："每 15 秒消耗 10 能量存储一个机动"）。 */
    public static void ravenChargeStoredSound(World world, Location at) {
        play(world, at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.5f);
    }

    /** 击杀即刻充能一层（需求："若击杀敌人则立即将主动机动模式充能一层"）。 */
    public static void ravenChargeFromKillSound(World world, Location at) {
        play(world, at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.85f, 1.6f);
        play(world, at, Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.8f);
    }

    // ───────── ⑥ 技能一：升空突袭-风弹 ─────────

    /** 义腿变形机构启动 + 向斜上方突进（需求："林晨霜启动'绝影'义腿变形机构，向斜上方 0.2S 内突进 7 格"）。 */
    public static void ravenSkyStrikeLaunchSound(World world, Location at) {
        play(world, at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.9f, 1.35f);
        play(world, at, Sound.ENTITY_BREEZE_SHOOT, 0.7f, 1.2f);
    }

    /** 风弹路径命中（需求："对路径上所有敌人造成单次 10 点物理伤害"）。 */
    public static void ravenSkyStrikeHitSound(World world, Location at) {
        play(world, at, Sound.ENTITY_WIND_CHARGE_WIND_BURST, 0.85f, 1.4f);
        play(world, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.5f);
    }

    // ───────── ⑦ 技能二：死亡之眼-末影之眼 ─────────

    /** 地面形态：向上跃起 3 格（需求："快速在 0.2S 内向上跃起 3 格"）。 */
    public static void ravenDeathEyeJumpSound(World world, Location at) {
        play(world, at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.6f);
        play(world, at, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.7f, 1.2f);
    }

    /** 瞬补弹药（需求："同时进行一次子弹装填【瞬间补齐子弹】"）。 */
    public static void ravenDeathEyeReloadSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.3f);
        play(world, at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 1.5f);
    }

    /** 散射命中回血（需求："每发弹药击中敌人将额外回复自己 2 点生命"）。 */
    public static void ravenDeathEyeLifeStealSound(World world, Location at) {
        play(world, at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.9f);
    }

    /** 周围无敌人 ⇒ 刷新一技能（需求："若使用后周围无敌人则为自己刷新一技能 cd"）。 */
    public static void ravenDeathEyeRefreshSound(World world, Location at) {
        play(world, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.8f);
    }

    // ───────── ⑧ 技能三：涅斯捷罗夫筋斗-沉重核心 ─────────

    /** 垂直跃起 12 格（需求："在 0.2S 内垂直跃起 12 格"）。 */
    public static void ravenSomersaultLaunchSound(World world, Location at) {
        play(world, at, Sound.ITEM_MACE_SMASH_AIR, 0.9f, 0.9f);
        play(world, at, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 1.3f);
    }

    /** 高速俯冲（需求："向指定方向高速俯冲[25 格每秒速度]"）。 */
    public static void ravenSomersaultDiveSound(World world, Location at) {
        play(world, at, Sound.ENTITY_PHANTOM_SWOOP, 0.85f, 0.8f);
        play(world, at, Sound.ENTITY_BREEZE_SLIDE, 0.55f, 0.9f);
    }

    /** 沉重核心落地（需求："落地时对周围 r=7 敌人造成 15 点物理伤害…与 1.5 秒眩晕"）。 */
    public static void ravenSomersaultLandSound(World world, Location at) {
        play(world, at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1f, 0.85f);
        play(world, at, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 0.75f);
        play(world, at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.7f);
    }

    // ───────── ⑨ 落地冲击（被动） ─────────

    /** 落地冲击 r5 结算（需求："到达地面后对周围 r=5 敌人造成 10 点物理伤害 3~6 特殊值伤害"）。 */
    public static void ravenLandingImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.1f);
        play(world, at, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
    }

    /** 从悬浮状态落地 ⇒ 2 秒速度 6（需求："每次从悬浮状态落地后你都会获得 2 秒的速度 6"）。 */
    public static void ravenLandingHasteSound(World world, Location at) {
        play(world, at, Sound.ENTITY_BREEZE_JUMP, 0.7f, 1.3f);
    }

    // ───────── ⑩ 装备 ─────────

    /** 穿上灰黑皮革胸甲（角色装配时一次）。 */
    public static void ravenEquipLeatherSound(World world, Location at) {
        play(world, at, Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.9f, 1f);
    }

    /** 穿上下界合金护腿 / 靴（角色装配时一次）。 */
    public static void ravenEquipNetheriteSound(World world, Location at) {
        play(world, at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.85f, 1.1f);
    }

    // ───────── 内部 ─────────

    private static void play(World world, Location at, Sound sound, float volume, float pitch) {
        if (world == null || at == null || sound == null) {
            return;
        }
        world.playSound(at, sound, volume, pitch);
    }
}
