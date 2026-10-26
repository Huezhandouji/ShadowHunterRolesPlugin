package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「艾尔伯特」的**纯音效工具**（无状态、零组件依赖、零 YAML）。
 *
 * <p>命名规范（工程口径 {@code <主要音效特制>+XX角色+XX技能+XX阶段+音效}）：
 * 每个方法名都把"用的是什么音 + 谁 + 哪个技能 + 什么阶段"写全，便于判断能否复用。
 *
 * <p><b>听感设计（需求原话："角色音效具有压迫感，每个技能的每个状态都有其特色"）</b>：
 * 全部音效走**低频 + 金属 / 机械**的组合 —— 铁砧、活塞、监守者、凋灵、末影龙。
 * 不用任何明亮 / 欢快的音（铃铛只在"哨戒报警"这一处用作**反差**，反而更刺耳）。
 *
 * <p><b>边界</b>：不读玩家状态、不写任何状态、不注册任务；传入 {@code null} 一律静默返回。
 */
public final class AlbertSound {

    private AlbertSound() {
    }

    // ───────── 主武器 ─────────

    /** **构筑者铳剑 · 近战命中**：铁傀儡挥击 + 铁砧轻响（机械咬合感）。 */
    public static void ironGolemAttackAlbertGunbladeMeleeImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.85f, 1.25f);
        play(world, at, Sound.BLOCK_ANVIL_LAND, 0.45f, 1.6f);
    }

    /** **构筑者铳剑 · 右键射击**：弩机 + 低音活塞（"亚音速"= 闷而不是脆）。 */
    public static void crossbowShootAlbertGunbladeShotSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_SHOOT, 1f, 0.75f);
        play(world, at, Sound.BLOCK_PISTON_EXTEND, 0.7f, 0.9f);
    }

    /** **构筑者铳剑 · 射击命中**：重击 + 金属短音。 */
    public static void netheriteHitAlbertGunbladeShotImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 1.1f);
        play(world, at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.7f, 1.3f);
    }

    /** **构筑者铳剑 · 弹匣打空**：空响（"咔"）—— 不悦耳，但必须能听出来。 */
    public static void clickEmptyAlbertGunbladeDryFireSound(World world, Location at) {
        play(world, at, Sound.UI_BUTTON_CLICK, 0.8f, 0.6f);
        play(world, at, Sound.BLOCK_LEVER_CLICK, 0.6f, 0.7f);
    }

    /** **构筑者铳剑 · 装填一发**（每 5 秒的被动回复）：短促的上膛声。 */
    public static void reloadAlbertGunbladeMagazineSound(World world, Location at) {
        play(world, at, Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.6f, 1.5f);
        play(world, at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.4f, 1.7f);
    }

    // ───────── 标记 / 零件 ─────────

    /** **猎杀目标 · 标记完成**：远古守卫者诅咒 + 低沉钟（"你被写进名单了"）。 */
    public static void elderGuardianCurseAlbertHuntTargetSound(World world, Location at) {
        play(world, at, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.35f);
        play(world, at, Sound.BLOCK_BELL_USE, 0.5f, 0.7f);
    }

    /** **零件入膛 · 装满一层**：齿轮卡榫的短音。 */
    public static void latchAlbertPartLoadedSound(World world, Location at) {
        play(world, at, Sound.BLOCK_IRON_DOOR_CLOSE, 0.45f, 1.9f);
    }

    /** **零件满 4 层 · 自动部署**：信标激活（"升空"的宣告）。 */
    public static void beaconActivateAlbertAutoDeploySound(World world, Location at) {
        play(world, at, Sound.BLOCK_BEACON_ACTIVATE, 0.85f, 1.4f);
        play(world, at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.2f);
    }

    // ───────── 高斯无人机 ─────────

    /** **无人机出击**：焰火升空 + 三叉戟投出（锋利、快）。 */
    public static void fireworkShootAlbertDroneLaunchSound(World world, Location at) {
        play(world, at, Sound.ENTITY_FIREWORK_ROCKET_SHOOT, 0.85f, 1.35f);
        play(world, at, Sound.ITEM_TRIDENT_THROW, 0.55f, 1.5f);
    }

    /** **无人机命中**：暴击 + 下界合金块重击。 */
    public static void critAlbertDroneImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.9f, 1.2f);
        play(world, at, Sound.BLOCK_NETHERITE_BLOCK_BREAK, 0.5f, 1.4f);
    }

    /** **无人机返航**：三叉戟回旋（"回来了"）。 */
    public static void tridentReturnAlbertDroneReturnSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RETURN, 0.45f, 1.6f);
    }

    /** **无人机被击落 / 主动防御消耗**：玻璃碎裂 + 物品损坏（克制但清楚）。 */
    public static void glassBreakAlbertDroneDownSound(World world, Location at) {
        play(world, at, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.3f);
        play(world, at, Sound.ENTITY_ITEM_BREAK, 0.6f, 1.1f);
    }

    // ───────── 被动：过度响应协议 ─────────

    /** **主动防御触发**：盾牌格挡 + 铁砧落（"又挡下一发"）。 */
    public static void shieldBlockAlbertOverResponseSound(World world, Location at) {
        play(world, at, Sound.ITEM_SHIELD_BLOCK, 1f, 0.85f);
        play(world, at, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
    }

    // ───────── 技能1 高斯装配 ─────────

    /** **高斯装配 · 释放**：导管激活 + 信标（"编队展开"）。 */
    public static void conduitActivateAlbertAssembleCastSound(World world, Location at) {
        play(world, at, Sound.BLOCK_CONDUIT_ACTIVATE, 0.9f, 1.2f);
        play(world, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 1.1f);
    }

    /** **高斯装配 · 哨戒无人机就位**：末影之眼放置（"看住这片区域"）。 */
    public static void eyeOfEnderAlbertSentryDeploySound(World world, Location at) {
        play(world, at, Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.8f, 1.3f);
        play(world, at, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.7f, 1.1f);
    }

    /** **哨戒无人机 · 有人越线**：铃铛（唯一一处用明亮音 —— 当警报用）。 */
    public static void bellAlbertSentryAlarmSound(World world, Location at) {
        play(world, at, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
        play(world, at, Sound.BLOCK_BELL_RESONATE, 0.9f, 1.5f);
    }

    // ───────── 技能2 猎杀指令 ─────────

    /** **猎杀指令 · 发射信号弹**：焰火 + 幻术师镜像（诡异、命令感）。 */
    public static void fireworkLaunchAlbertHuntOrderCastSound(World world, Location at) {
        play(world, at, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.1f);
        play(world, at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.0f);
    }

    /** **猎杀指令 · 区域落点**：凋灵尖啸的低音版（"那片区域不要留活口"）。 */
    public static void witherShootAlbertHuntOrderImpactSound(World world, Location at) {
        play(world, at, Sound.ENTITY_WITHER_SHOOT, 0.9f, 1.4f);
        play(world, at, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.7f, 1.2f);
    }

    // ───────── 技能3 全功率推进 ─────────

    /** **全功率推进 · 位移**：三叉戟激流 III + 末影人传送（"穿过去"）。 */
    public static void riptideAlbertOverdriveDashSound(World world, Location at) {
        play(world, at, Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 1.15f);
        play(world, at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.75f, 1.4f);
    }

    /** **全功率推进 · 留下诱饵**：铁陷阱闭合（"诱饵留下"）。 */
    public static void trapdoorAlbertDecoyPlaceSound(World world, Location at) {
        play(world, at, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.85f, 0.8f);
        play(world, at, Sound.BLOCK_PISTON_CONTRACT, 0.6f, 1.2f);
    }

    /** **诱饵爆炸**：通用爆炸 + 监守者怒吼（低沉、近距离、压迫）。 */
    public static void explodeAlbertDecoySound(World world, Location at) {
        play(world, at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        play(world, at, Sound.ENTITY_WARDEN_ROAR, 0.6f, 1.5f);
    }

    // ───────── 技能4 过载协议 ─────────

    /** **过载协议 · 释放**：监守者蓄力（长音，把"全部引爆"的压力铺开）。 */
    public static void wardenChargeAlbertOverloadCastSound(World world, Location at) {
        play(world, at, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1.0f);
        play(world, at, Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.4f);
    }

    /** **过载协议 · 自杀式无人机蓄力**：凋灵蓄力（逐个起飞前的倒计时感）。 */
    public static void witherChargeAlbertKamikazeChargeSound(World world, Location at) {
        play(world, at, Sound.ENTITY_WITHER_SHOOT, 0.7f, 1.7f);
    }

    /** **自杀式无人机爆炸**：末影龙火球爆炸 + 灵魂爆（"用你们换一条路"）。 */
    public static void dragonFireballAlbertKamikazeExplodeSound(World world, Location at) {
        play(world, at, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1f, 1.2f);
        play(world, at, Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.9f);
    }

    /** **过载协议 · 完全结束**：信标熄灭（"过载结束"）。 */
    public static void beaconDeactivateAlbertOverloadEndSound(World world, Location at) {
        play(world, at, Sound.BLOCK_BEACON_DEACTIVATE, 0.9f, 1.2f);
    }

    // ───────── 通用 ─────────

    /** 统一的播放出口（世界 / 位置任一为 null ⇒ 静默）。 */
    private static void play(World world, Location at, Sound sound, float volume, float pitch) {
        if (world == null || at == null || sound == null) {
            return;
        }
        world.playSound(at, sound, volume, pitch);
    }
}
