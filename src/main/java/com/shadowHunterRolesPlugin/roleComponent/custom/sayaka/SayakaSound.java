package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「芹泽沙耶香 · 瓦尔基里」的**音效工具箱**。
 *
 * <h2>工具箱纪律</h2>
 * 同 {@link SayakaVfx}：纯静态、只放音效、不读状态、不注册任务；{@code world == null} 静默返回。
 * 所有常量名都经过 **javap 逐条核对**（{@code paper-api-1.21.11}）—— 不允许凭记忆写音效名。
 *
 * <h2>听感设计（黑红主题）</h2>
 * <ul>
 *   <li><b>影</b>类（充能 / 瞬移 / 纳刀）= 低沉、收束：{@code BLOCK_RESPAWN_ANCHOR_CHARGE}、{@code ENTITY_ENDERMAN_TELEPORT}、{@code ITEM_TRIDENT_RETURN}；</li>
 *   <li><b>血</b>类（斩击 / 爆发 / 大招）= 锐利、爆裂：{@code ENTITY_PLAYER_ATTACK_SWEEP}、
 *       {@code ITEM_TRIDENT_THUNDER}、{@code ENTITY_WITHER_*}、{@code ENTITY_RAVAGER_*}。</li>
 * </ul>
 */
public final class SayakaSound {

    private SayakaSound() {
    }

    // ───────── 主武器 ─────────

    /** 拔刀攻击命中：锐利横扫。 */
    public static void meleeHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.1f);
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 0.9f);
    }

    /** 武器充能（注入笼手层数）。 */
    public static void chargeLoad(World world, Location at, int level) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.9f, 0.8f + 0.15f * Math.max(1, level));
        world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5f, 1.4f);
    }

    /** 充能被兑现（攻击 / 居合消耗）。 */
    public static void chargeConsumed(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.5f);
    }

    /** 无充能 / 无能量 / 无法充能：落空提示。 */
    public static void chargeDenied(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.6f, 0.7f);
    }

    /**
     * **居合蓄力的复合递增音**（2026-10-17 需求 1：逐步升高的复合蓄力音效）。
     *
     * <p>每 {@code 4} 刻一次、共 5 次：一层低频"充能"（{@code BLOCK_RESPAWN_ANCHOR_CHARGE}）
     * + 一层高频"晶鸣"（{@code BLOCK_AMETHYST_BLOCK_CHIME}）——两层都随 {@code step} 升调，
     * 所以是"复合"且在"逐步升高"。音量刻意压低（0.3 左右），只做铺垫不抢戏。
     *
     * @param step 第几次（0 起）；越界会夹在 0..4
     */
    public static void iaiChargeTick(World world, Location at, int step) {
        if (world == null || at == null) {
            return;
        }
        int s = Math.max(0, Math.min(4, step));
        float pitch = 0.75f + 0.11f * s;
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.30f, pitch);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.16f, pitch + 0.45f);
        if (s >= 3) {
            world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.12f, 1.2f + 0.1f * s);
        }
    }

    /** **居合蓄力完成**：一声清亮的"可以了"（与蓄力音同一音色家族，但高八度收尾）。 */
    public static void iaiReady(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.75f, 1.55f);
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.55f, 1.15f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.9f);
    }

    /** 进入纳刀准备（0.3 秒）。 */
    public static void sheathePrep(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.8f, 1.3f);
    }

    /** 纳刀到位。 */
    public static void sheathed(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.7f, 0.8f);
    }

    /** 拔刀出鞘。 */
    public static void drawn(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.3f);
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.5f, 1.6f);
    }

    /** 居合（防御触发 = 沉一声重击；主动触发 = 利落的挥砍）。 */
    public static void iai(World world, Location at, boolean defensive) {
        if (world == null || at == null) {
            return;
        }
        if (defensive) {
            world.playSound(at, Sound.ITEM_TRIDENT_HIT, 1.0f, 1.2f);
            world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.5f, 1.6f);
        } else {
            world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.35f);
            world.playSound(at, Sound.ITEM_MACE_SMASH_AIR, 0.6f, 1.4f);
        }
    }

    /** 3 层充能防御成功：1 秒无敌罩。 */
    public static void immuneShield(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.8f);
    }

    // ───────── 失衡 / 爆发 ─────────

    /** 命中叠失衡（轻）。 */
    public static void imbalanceHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.35f, 1.7f);
    }

    /** **失衡层数闲置消退**：一声很轻的"沙落"，音高随剩余层数略降（越退越沉）。 */
    public static void imbalanceDecay(World world, Location at, int remaining) {
        if (world == null || at == null) {
            return;
        }
        float pitch = 0.75f + Math.max(0, Math.min(remaining, 10)) * 0.03f;
        world.playSound(at, Sound.BLOCK_SOUL_SAND_STEP, 0.28f, pitch);
    }

    /** **失衡爆发**开场：雷击 + 凋灵闷响（黑红大场面的"重"）。 */
    public static void burstTriggered(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 1.0f, 0.9f);
        world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.2f);
    }

    /** 爆发每秒结算。 */
    public static void burstTick(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.25f, 1.8f);
    }

    // ───────── 笼手 ─────────

    /** 笼手充能到账（自然回复 / 连斩命中 / 爆发奖励）。 */
    public static void chargeStored(World world, Location at, boolean fromBurst) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, fromBurst ? 0.8f : 0.45f, fromBurst ? 1.1f : 1.6f);
    }

    // ───────── 技能一：榴弹 ─────────

    /**
     * 发射榴弹（需求 3：要"力量感"）。
     *
     * <p>三层叠加：沉重的机械击发（{@code ENTITY_IRON_GOLEM_ATTACK}，低音）+ 短促的爆响
     * （{@code ITEM_MACE_SMASH_AIR}）+ 原有的火焰喷出。弹速提上去之后，击发声必须更"实"，
     * 否则会像"滋"一下就没。
     */
    public static void grenadeFire(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.85f, 0.62f);
        world.playSound(at, Sound.ITEM_MACE_SMASH_AIR, 0.65f, 0.85f);
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.9f, 0.7f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.6f);
    }

    /** 发射瞬间的**后座闷响**（贴耳，只用 0.25 音量避免盖住击发）。 */
    public static void grenadeRecoil(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.45f, 0.75f);
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.3f, 0.55f);
    }

    /** 榴弹炸开（需求 3：落地冲击力 —— 加低频与碎裂层）。 */
    public static void grenadeExplode(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.75f);
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.65f);
        world.playSound(at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f, 1.2f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 1.35f);
    }

    /** 稳态场每秒结算。 */
    public static void fieldTick(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 0.35f, 0.6f);
    }

    /** 无榴弹 / 能量不足。 */
    public static void grenadeDenied(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.6f, 0.75f);
    }

    // ───────── 技能二 / 大招 ─────────

    /** 瞬影连斩突进。 */
    public static void dash(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.5f);
        world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_3, 0.8f, 1.1f);
    }

    /** 连斩命中（每段递增音高）。 */
    public static void chainHit(World world, Location at, int chainIndex) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.0f + 0.12f * Math.max(0, chainIndex));
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.0f + 0.1f * Math.max(0, chainIndex));
    }

    /** 技能二错过目标（进入冷却）。 */
    public static void chainMissed(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.4f, 0.6f);
    }

    /** 大招每一段斩击。 */
    public static void stormStage(World world, Location at, int stage) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f + 0.1f * Math.max(0, stage));
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 0.9f, 0.9f + 0.08f * Math.max(0, stage));
        world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.45f, 1.3f);
    }

    /** 大招收尾（黑暗降临）。 */
    public static void stormFinal(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.0f, 0.8f);
        world.playSound(at, Sound.ITEM_TRIDENT_THUNDER, 0.9f, 0.7f);
        world.playSound(at, Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.2f);
    }

    /** 大招开启。 */
    public static void stormStart(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.8f, 0.9f);
    }

    /** 大招期间：敌人**踏入 r8 被锁定**（一声短促的"咔"，音高按时序略微上扬）。 */
    public static void stormLockOn(World world, Location at, int stage) {
        if (world == null || at == null) {
            return;
        }
        int s = Math.max(0, stage);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.55f, 1.35f + 0.08f * s);
        world.playSound(at, Sound.ENTITY_ARROW_HIT_PLAYER, 0.4f, 1.6f + 0.05f * s);
    }

    // ───────── 笼手回血追猎粒子 ─────────

    /** 追猎粒子**到达**纱耶香（此时才回血）：一声短促的红色"叮"。 */
    public static void healthMoteArrive(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.7f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.2f, 2.0f);
    }
}
