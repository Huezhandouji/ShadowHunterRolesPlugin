package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * 「梅妻鹤子」的音效表 —— 纯静态。
 *
 * <p>手感口径：普攻 = **破空声 + 敲铁 + 清脆命中** 三层叠加；技能 = 低频轰鸣打底 + 高频金属声点题。
 * 音量统一压在 1.0 以内（避免盖住队友语音），音高做 ±15% 抖动（连打不呆板）。
 */
public final class MikomakoSound {

    private MikomakoSound() {
    }

    private static float jitter(float base, float spread) {
        return base + (float) ((Math.random() * 2d - 1d) * spread);
    }

    /** 普攻破空。 */
    public static void slash(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, jitter(1.45f, 0.1f));
        world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.22f, jitter(1.85f, 0.12f));
    }

    /** 普攻命中：敲铁 + 清脆。 */
    public static void hit(Player player) {
        World world = player.getWorld();
        Location at = player.getLocation();
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.32f, jitter(1.75f, 0.08f));
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.7f, jitter(1.25f, 0.1f));
        world.playSound(at, Sound.BLOCK_ANVIL_HIT, 0.38f, jitter(1.6f, 0.1f));
    }

    /** 涨一层充能。 */
    public static void chargeGain(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.75f, jitter(1.35f, 0.08f));
        world.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.45f, 1.8f);
    }

    /** 满层。 */
    public static void fullCharge(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.55f, 1.45f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.9f);
    }

    /** 起手蓄力。 */
    public static void chargeStart(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.6f);
    }

    /** 投掷。 */
    public static void throwWeapon(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.9f, 1.35f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.8f);
    }

    /** 投掷命中。 */
    public static void impact(Location location, boolean charged) {
        World world = location.getWorld();
        world.playSound(location, Sound.BLOCK_ANVIL_LAND, 1.0f, 0.65f);
        world.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.5f);
        if (charged) {
            world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.45f, 1.75f);
        }
    }

    /** 拾回武器。 */
    public static void pickup(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.2f);
        world.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.6f);
    }

    /** 血腥连斩位移。 */
    public static void dash(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.55f, 1.55f);
        world.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 0.85f);
    }

    /** 圆弧斩起手（引导）。 */
    public static void channelStart(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_ACTIVATE, 0.55f, 0.75f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.35f, 1.2f);
    }

    /** 圆弧斩出刀（僵尸破门，1.5 倍速 —— 2026-10-21 需求）。 */
    public static void channelRelease(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.5f);
    }

    /** 圆弧斩引导中逐字浮现的"经验音效"（音高随进度上抬）。 */
    public static void channelTick(Player player, int step) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                0.55f, 1.05f + Math.max(0, step) * 0.16f);
    }

    /** 漫不经心。 */
    public static void skillCast(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8f, 1.55f);
        world.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.7f);
    }

    /** 0 档引导（0.5 秒）：蓄势上膛。 */
    public static void burstCharge(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.85f, 1.2f);
        world.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.6f);
    }

    /** 0 档爆发。 */
    public static void burst(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.55f, 1.55f);
        world.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.2f);
        world.playSound(player.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.6f, 0.85f);
    }

    /** 0 档期间每秒的灵魂呼啸（不刺耳、不遮挡）。 */
    public static void soulWail(Player player) {
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_VEX_AMBIENT, 0.45f, 0.65f);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.18f, 1.85f);
    }

    /** 满层攻击时的额外低频。 */
    public static void chargedHit(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.35f, 1.35f);
    }
}
