package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「抗争」的音效工具箱（纯静态、传 {@code null} 静默返回）。
 *
 * <p>所有常量名都用 {@code javap -cp paper-api.jar org.bukkit.Sound} 核对过
 * （工程口径：音效名查 jar，不凭记忆）。
 */
public final class ResistSound {

    private ResistSound() {
    }

    // ───────── 主武器 ─────────

    /** 斗争命中：TNT 爆炸 + 刀风（需求指定 TNT 爆炸音效）。 */
    public static void mainHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.65f, 1.45f);
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.75f);
    }

    /** 剑气落点的小爆裂（只给非主目标的范围敌人，音量低一档）。 */
    public static void swordQiSplash(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.8f);
    }

    // ───────── 一技能：谷底的怒吼 ─────────

    /** 末影龙怒吼（需求指定）+ 掠夺者咆哮铺底。 */
    public static void roar(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.0f);
        world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 0.7f, 0.85f);
    }

    /** 解除控制：信标激活 + 重生锚耗尽（"枷锁断裂"的听感）。 */
    public static void controlBreak(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.35f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.9f);
    }

    /** 巨剑破土：铁傀儡重击 + 下界合金敲击。 */
    public static void swordsErupt(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.7f);
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.9f, 0.6f);
    }

    // ───────── 二技能：命运的征服 ─────────

    /** 引导蓄力（0..4 档，音高逐级升高）。 */
    public static void conquestCharge(World world, Location at, int step) {
        if (world == null || at == null) {
            return;
        }
        float pitch = 0.7f + Math.max(0, Math.min(4, step)) * 0.12f;
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, pitch);
        if (step >= 2) {
            world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, pitch + 0.3f);
        }
    }

    /** 三刀：破空 + 铁傀儡挥击；第三刀砸地额外加钉锤落地。 */
    public static void conquestSlash(World world, Location at, int stage) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.65f);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.9f, 1.1f);
        if (stage >= 3) {
            world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.8f);
        }
    }

    /** 命中：敲击铁块（需求指定）+ 暴击。 */
    public static void conquestHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 1.0f, 0.95f);
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 1.25f);
    }

    // ───────── 三技能：被欺压者的咆哮 ─────────

    /** 引导拔剑（0..3 档，越来越沉）。 */
    public static void rampageCharge(World world, Location at, int step) {
        if (world == null || at == null) {
            return;
        }
        float pitch = 0.85f - Math.max(0, Math.min(3, step)) * 0.08f;
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7f, pitch);
    }

    /** 突进破空。 */
    public static void rampageDash(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_2, 1.0f, 0.8f);
        world.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 0.5f, 0.7f);
    }

    /** 落点：巨剑破土 + 大范围冲击。 */
    public static void rampageLanding(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND, 1.0f, 0.7f);
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.85f);
        world.playSound(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 1.0f, 0.6f);
    }

    // ───────── 被动：斗争层数 ─────────

    /**
     * **每 5 层一档的音效**（{@code tier} = 1..8，对应 5..40 层；40 层后不再有新音效）。
     * 音高逐档升高，最后两档再叠一层泛音。
     */
    public static void tierUp(World world, Location at, int tier) {
        if (world == null || at == null || tier <= 0) {
            return;
        }
        int t = Math.min(8, tier);
        float pitch = 0.75f + t * 0.11f;
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, pitch);
        world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, pitch);
        if (t >= 7) {
            world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.35f, pitch + 0.25f);
        }
    }

    /** 40 层圆环的每 2 秒闪动。 */
    public static void ringPulse(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.6f);
    }
}
