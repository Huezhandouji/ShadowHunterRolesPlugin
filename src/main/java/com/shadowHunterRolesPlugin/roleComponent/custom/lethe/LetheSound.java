package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

/**
 * 「冷识 · 疯癫主教」的**音效工具箱**。
 *
 * <h2>工具箱纪律</h2>
 * 与 {@link LetheVfx} 同：{@code public final class} + 私有构造器 + 纯静态；只播放音效，
 * 不读状态、不注册任务、不碰组件；{@code world == null} 或 {@code at == null} 静默返回。
 *
 * <h2>★ 宿主纪律（教训文档 §6.7）</h2>
 * 本类**没有任何字段**：不持有 plugin、不持有 World、不持有 scheduler。
 * 静态字段在类加载期求值，而那时插件还没 {@code onEnable()} ⇒ 字段恒为 null ⇒
 * 所有方法都在第一行静默返回、一点声音都没有。所以声源一律用入参 {@code world}/{@code at}，
 * 世界 / 坐标每次调用现取。
 *
 * <h2>听感设计（压迫感口径）</h2>
 * 冷识的音色统一压在三类低音上，每个技能换组合，不换家族：
 * <ul>
 *   <li><b>钟</b>（{@code BLOCK_BELL_*}）—— 主教的仪式感，也是共鸣圈的节拍器；
     *       <b>音量统一压到原来的 25%（-75%）</b>：共鸣圈每 0.25 秒一拍、落圈也敲，
     *       原始音量在实战里会盖住其它音色（2026-10-30 按反馈调整）；</li>
 *   <li><b>龙吟 / 凋灵</b>（{@code ENTITY_ENDER_DRAGON_*}、{@code ENTITY_WITHER_*}）—— 大招的重量；</li>
 *   <li><b>监守者</b>（{@code ENTITY_WARDEN_*}）—— 无形之物逼近时的压迫底噪。</li>
 * </ul>
 * 灵魂 / 圣所那一类（火把、抬升、回收）用魂沙、导管、末影之眼把"圣职"这一面补齐；
 * 只有锻造与箭袋回收允许一点金属 / 金铃的亮音（那是物件的声音，不是法术的声音）。
 */
public final class LetheSound {

    private LetheSound() {
    }

    // ═════════════════════════ 主武器：流连标记 ═════════════════════════

    /**
     * **离弦**：一声低沉的弓响 + 凋灵的短嘶。
     *
     * <p>原版弓响偏高偏轻，撑不起"这一箭带着标记"的重量，所以叠一层
     * {@code ENTITY_WITHER_SHOOT} 的低吼：听感上箭一离弦就在"烧"，
     * 玩家从声音就知道它命中会有事发生。
     */
    public static void bowShoot(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_ARROW_SHOOT, 0.9f, 0.72f);
        world.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.3f, 1.55f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.22f, 1.1f);
    }

    /**
     * **命中**：闷响 + 紫色晶体被敲响的清脆一声。
     *
     * <p>{@code BLOCK_AMETHYST_BLOCK_HIT} 是紫水晶被敲的音色 —— 与粒子里的深紫十字
     * 是同一套语言（"记号被钉住了"）。末位再补一点晶鸣，让命中点在嘈杂战斗里也能被听见。
     */
    public static void arrowHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_ARROW_HIT, 0.85f, 1.0f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.45f, 1.35f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.9f);
    }

    /**
     * **近战命中**（冷识主武器 Lether 的近战攻击，2026-10-31）：挥击闷响 + 一声低紫晶体。
     * <p>与 {@link #arrowHit} 的区别只是"更贴身的打击感"：去掉末位高频晶鸣，换成短促低晶鸣。
     */
    public static void meleeHit(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 1.0f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.5f, 0.85f);
    }

    // ═════════════════════════ 共鸣圈 ═════════════════════════

    /**
     * **共鸣圈生成**：导管启动 + 一记低钟 + 幽灵低鸣。
     *
     * <p>这一声要回答"地上刚刚多了一个圈"：导管启动给"阵"的仪式感，
     * 钟声定住节拍（后面每次脉冲都踩在这个音色上），幽灵低鸣把圈里的空气弄冷。
     */
    public static void ringSpawn(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_CONDUIT_ACTIVATE, 0.6f, 0.88f);
        world.playSound(at, Sound.BLOCK_BELL_RESONATE, 0.14f, 0.55f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.65f, 0.8f);
        world.playSound(at, Sound.ENTITY_PHANTOM_AMBIENT, 0.25f, 0.9f);
    }

    /**
     * **共鸣圈脉冲**：魂沙的沙响 + 一声极轻的晶鸣（0.25 秒一次的高频调用）。
     *
     * <p>★ 钟声**不在这里**：钟由共鸣组件的"节拍器"按 2.5 秒 / 命中时 1 秒的节奏单独敲
     * （见 {@link #ringBell(World, Location)}）。每脉冲都敲钟是 2026-10-30 之前的写法，
     * 实测在圈多时会盖掉其它音色。
     */
    public static void ringPulse(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_SOUL_SAND_STEP, 0.3f, 0.75f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.14f, 1.75f);
    }

    /**
     * **共鸣圈的钟**：主教的心跳，一记低钟。
     *
     * <p>节奏由调用方（共鸣组件）决定：**待机 2.5 秒一记；圈在打中人时加快到 1 秒一记**。
     * 音量压得很低（0.14）—— 它是背景节拍，不是打击音。
     */
    public static void ringBell(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BELL_RESONATE, 0.14f, 0.55f);
    }

    /**
     * **共鸣圈散掉**：信标熄灭 + 重生锚抽空 + 一声凋灵的痛。
     *
     * <p>三种"收尾"音叠在一起：信标是"阵撤了"，重生锚是"魂被抽走了"，
     * 凋灵的痛是圈里那些东西最后的一点不甘。
     */
    public static void ringExpire(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 0.6f, 0.7f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.55f, 0.85f);
        world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.25f, 0.7f);
    }

    // ═════════════════════════ 遗忘 ═════════════════════════

    /**
     * **遗忘落点**：火把插进魂沙 + 末影之眼的"启" + 一记钟。
     *
     * <p>这是"主教在写一笔"的动作：魂沙给火把的落地感，末影之眼给仪式的起手，
     * 低钟把这一笔钉在时间上；最后一点幽灵低鸣说明裂缝里有东西在呼吸。
     */
    public static void forgetPlace(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_SOUL_SAND_PLACE, 0.9f, 0.65f);
        world.playSound(at, Sound.ENTITY_ENDER_EYE_LAUNCH, 0.7f, 0.75f);
        world.playSound(at, Sound.BLOCK_BELL_RESONATE, 0.18f, 0.5f);
        world.playSound(at, Sound.ENTITY_PHANTOM_AMBIENT, 0.35f, 0.7f);
    }

    /**
     * **遗忘收回**：三叉戟回归音当骨架，叠一层被抽空的闷响与短距离瞬移的"吸"。
     *
     * <p>回归音本身自带"由远及近"的多普勒感，正好对应粒子的逆向收束；
     * 重生锚抽空那一下是"这一笔被抹掉"，末影人瞬移的细响是能量离开空气的缝隙。
     */
    public static void forgetReturn(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.8f, 0.85f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 1.25f);
        world.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.45f, 0.8f);
    }

    // ═════════════════════════ 召圣 ═════════════════════════

    /**
     * **抬升**：导管启动的"圣所升起" + 监守者的蓄力 + 上升气流。
     *
     * <p>监守者蓄力（{@code ENTITY_WARDEN_SONIC_CHARGE}）是这条声音线的核心：
     * 它天生就是"有东西正在被抬到你脸上"的压迫音。音量给 0.35，
     * 让它垫在导管音底下，不抢主音但一直在。
     */
    public static void summonLift(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_CONDUIT_ACTIVATE, 0.65f, 0.9f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.35f, 1.45f);
        world.playSound(at, Sound.ENTITY_BREEZE_JUMP, 0.45f, 0.85f);
    }

    /**
     * **召回**：三叉戟回归 + 激流 + 幻翼俯冲的破空声。
     *
     * <p>三样都是"高速物体划开空气"的音色，叠起来就是一串由远到近的呼啸；
     * 召回是全场最快的一拍，声音必须比粒子更早让玩家意识到"它回来了"。
     */
    public static void summonRecall(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_TRIDENT_RETURN, 0.9f, 1.35f);
        world.playSound(at, Sound.ITEM_TRIDENT_RIPTIDE_2, 0.6f, 1.45f);
        world.playSound(at, Sound.ENTITY_PHANTOM_SWOOP, 0.5f, 1.25f);
    }

    /**
     * **超时自爆**：全场最重的一击（r=10）。
     *
     * <p>五层叠成一根"从低到高"的声柱：爆炸（身体）+ 重锤砸地（地面）+
     * 监守者声波（穿透）+ 凋灵降临（恐惧）+ 龙吟（威严）。
     * 音量分配遵循一个原则：**频率越低，音量越大**，这样远处玩家先听到的是"闷",
     * 近处才听见细节 —— 与"压迫感"的听感目标一致。
     */
    public static void summonDetonate(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.8f);
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.7f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.55f, 1.15f);
        world.playSound(at, Sound.ENTITY_WITHER_SPAWN, 0.45f, 1.35f);
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.45f, 0.8f);
    }

    // ═════════════════════════ 重塑 ═════════════════════════

    /**
     * **重塑引导**：一记极低的钟 + 重生锚充能 + 监守者蓄力。
     *
     * <p>引导是"在这段时间里持续播放"的，所以三个音量全压在 0.5 以下：
     * 玩家要能听见"方块在天上转"，同时还得听得清队友喊话。
     * 音高刻意选在 0.55~0.75（低八度区间），听感是"从地底往上顶"。
     */
    public static void reshapeChannel(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 0.6f);
        world.playSound(at, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.45f, 0.55f);
        world.playSound(at, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.3f, 0.75f);
    }

    /**
     * **重塑落地**：重锤砸地 + 远雷 + 龙吟 + 监守者咆哮。
     *
     * <p>"落地"这一下必须比自爆更**实**而不是更响：所以主角是
     * {@code ITEM_MACE_SMASH_GROUND_HEAVY}（金属撞地的实体感），
     * 龙吟与监守者各给一半音量做"大东西真的站起来了"的注脚，远雷只负责把远处的人拉回头。
     */
    public static void reshapeImpact(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ITEM_MACE_SMASH_GROUND_HEAVY, 1.0f, 0.65f);
        world.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 0.7f);
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 0.75f);
        world.playSound(at, Sound.ENTITY_WARDEN_ROAR, 0.35f, 1.15f);
    }

    /**
     * **碎片浮空**：旋风 + 空隙气流 + 一记晶鸣。
     *
     * <p>这一招是"安静地立起来"，所以刻意**不用**打击类音效：
     * 旋风给旋转的形状，气流给"悬浮"的轻，晶鸣只用来标定高度（声音会随位置变化）。
     */
    public static void reshapeFloat(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_BREEZE_WHIRL, 0.5f, 0.8f);
        world.playSound(at, Sound.ENTITY_BREEZE_IDLE_AIR, 0.35f, 1.25f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.55f);
    }

    // ═════════════════════════ 被动 / 装备 ═════════════════════════

    /**
     * **进化升级**：升级音 + 龙吟 + 高音钟 + 信标启动。
     *
     * <p>四层是"人对大事件"的听觉结构：先是熟悉的升级提示（我知道我变强了），
     * 再是龙吟（这次不一样），然后是钟（仪式确认），最后信标把余韵拖长。
     * 钟的音量给到 0.9、音高 0.75 —— 教堂大钟不在高处，在胸腔里。
     */
    public static void evolutionLevelUp(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.9f);
        world.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.75f, 0.9f);
        world.playSound(at, Sound.BLOCK_BELL_RESONATE, 0.22f, 0.75f);
        world.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
    }

    /**
     * **锻造到账**：铁砧落地 + 下界合金被敲 + 一簇火焰。
     *
     * <p>这一声是"物件"的声音，所以允许金属音：铁砧给重量，下界合金给材质的硬，
     * 火焰给"刚出炉"。三者叠起来像一次停在最后一锤的锻造 —— 盔甲成型了。
     */
    public static void equipForge(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.85f);
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.5f, 1.35f);
        world.playSound(at, Sound.ENTITY_BLAZE_SHOOT, 0.35f, 1.15f);
    }

    /**
     * **箭袋回收**：拾取声 + 金铃晶鸣；血泪形态换成凋灵的闷痛与魂沙声。
     *
     * <p>正常形态要"轻快、干净"（箭回来了，可以继续射）；
     * 血泪形态要"沉"：凋灵那一声是这支箭咬到过的东西的回响。
     *
     * @param bloodTear 是否血泪形态
     */
    public static void quiverRestore(World world, Location at, boolean bloodTear) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.ENTITY_ITEM_PICKUP, 0.45f, 1.55f);
        world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.85f);
        if (bloodTear) {
            world.playSound(at, Sound.ENTITY_WITHER_HURT, 0.35f, 0.75f);
            world.playSound(at, Sound.BLOCK_SOUL_SAND_PLACE, 0.5f, 0.6f);
        } else {
            world.playSound(at, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.7f);
        }
    }

    // ═════════════════════════ 通用反馈 ═════════════════════════

    /**
     * **无法施放**：下界合金闷响 + 发射器的空响。
     *
     * <p>失败音最难做的是"不刺耳但也不含糊"：不用 UI 音（太轻，战斗中听不见），
     * 用两个都在中低频的"实心"音 —— 玩家立刻知道"扣不动"，但不会觉得刺耳。
     */
    public static void denied(World world, Location at) {
        if (world == null || at == null) {
            return;
        }
        world.playSound(at, Sound.BLOCK_NETHERITE_BLOCK_HIT, 0.55f, 0.7f);
        world.playSound(at, Sound.BLOCK_DISPENSER_FAIL, 0.45f, 0.85f);
    }
}
