package com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.MatinaRageVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import java.util.List;

/**
 * 「狂躁牧师·马提娜」技能之三：**神罚**。
 *
 * <h2>行为（需求逐条）</h2>
 * <ul>
 *   <li><b>引导 {@value #CHANNEL_SECONDS} 秒</b>后释放技能；</li>
 *   <li>对技能范围内 <b>r = {@value #AURA_RADIUS}</b> 的敌人<b>持续 {@value #DURATION_SECONDS} 秒</b>
 *       造成特殊值（SanTE）伤害，<b>共 {@value #TOTAL_SANTE_DAMAGE} 点</b>；</li>
 *   <li>整个技能是一个<b>大型魔法阵</b>：<b>旋转变换</b>、<b>魔法阵边缘就是技能边缘</b>（r = 25）；</li>
 *   <li>★ <b>脚下那层已换成外部「pixelart 粒子画」数据包导出的<u>像素画魔法阵</u></b>
 *       （2026-10-02 用户要求"用它替换掉脚下圆圈魔法阵"）：见
 *       {@link #PIXEL_CIRCLE_RESOURCE} / {@link #PIXEL_CIRCLE_SLICES} / {@link #pixelArt()}。
 *       <b>它不自转</b>（位图自转会糊，见 {@link #PIXEL_CIRCLE_PHASE}）；
 *       <b>上方 3 格的 r=10 小魔法阵仍是原来的几何画法、照旧自转</b>
 *       （用户只要求换脚下那层）—— 所以"旋转变换"这条在技能整体上仍然成立；</li>
 *   <li>自己周身环绕<b>白色旋转向上 10 格</b>粒子，且在自己<b>上方 3 格</b>处再生成一个
 *       <b>r = 10</b> 的魔法阵（粒子基本都是白色）；</li>
 *   <li>技能期间<b>定身</b>、获得<b>抗性 2</b>；</li>
 *   <li><b>技能物品附魔</b>提示正在释放技能（不是把物品替换成结构空位）；</li>
 *   <li><b>技能完全后</b>冷却 {@value #COOLDOWN_TICKS} 刻（50 秒）；<b>能量消耗 0</b>。</li>
 * </ul>
 *
 * <h2>口径申报</h2>
 * <ol>
 *   <li><b>"引导 3 秒"是施法动作</b>：按下后进入引导（3 秒），期间每刻定身 + 抗性 2，
 *       引导结束才开始 7 秒的伤害窗口 ⇒ 从按键到打完共 10 秒。</li>
 *   <li><b>70 点怎么分</b>：伤害窗口 7 秒 = 140 刻，每 <b>0.5 秒</b>（{@value #DAMAGE_INTERVAL_TICKS} 刻）
 *       结算一次 ⇒ 14 次 × {@value #DAMAGE_PER_TICK} 点 = <b>刚好 70 点</b>（不取整、不溢出）。</li>
 *   <li><b>"敌人"的判据</b> = {@code roleInfo().isHostileTo(uuid)}；每帧现算 ⇒ 走进阵里的人立刻被结算，
 *       走出去的立刻停止（与"范围内的敌人"这条需求一致）。</li>
 *   <li><b>定身与"免疫缓慢"的关系</b>：定身用 {@code SLOWNESS 255} + 清水平速度 + 越界拽回三件套，
 *       并且<b>每刻重刷</b>。若马提娜的狂暴值达到 10 层（免疫缓慢），被动会摘掉缓慢 ——
 *       但定身由本组件在自己的一刻里<b>写回</b>（谁后写谁生效），这正是需求那句
 *       "免疫缓慢，但无法免疫自己技能带来的定身" 的落点。</li>
 *   <li><b>"附魔提示"的落点</b>：覆写 {@link #buildItem()}，在引导/伤害窗口期间给技能物品加
 *       {@code setEnchantmentGlintOverride(true)}（只发光、不加词条、不换材质），
 *       并按需求<b>不</b>把它替换成结构空位（冷却态由基类默认画法处理）。</li>
 *   <li><b>本技能【不】产生狂暴值</b>：需求里狂暴值的唯一来源是"**每次成功治疗**"
 *       （海晶灯 / 无人机脉冲 / 医疗设备命中），而神罚只结算特殊值伤害、不治疗任何目标
 *       ⇒ 这里一次狂暴值都不加。
 *       <p>★ 修复记录：旧实现在每跳伤害后按<b>命中人数</b>加等量狂暴层数，
 *       属于需求之外的额外来源（已删）。</li>
 *   <li><b>特效分两段</b>：<b>引导期</b>只画"技能边缘一圈白色点阵 + 自身 10 格白色旋转向上螺旋"；
 *       <b>大型魔法阵（脚下 r=25 与上方 3 格的 r=10）只在引导结束后才展开</b>，
 *       并在伤害期随相位自转（旧实现从按键第一刻就把阵法全画出来了，与需求不符）。</li>
 *   <li><b>命中者的白色光柱（短暂）</b>：引导结束后，凡是**被本技能结算到的人**，脚下都会冒出一根
 *       <b>短暂</b>的竖直白色光柱粒子 —— 主体用短命的 {@code DUST}（约 1 秒即散）、
 *       只在柱顶点缀极少数 {@code END_ROD}，高度 {@value #PILLAR_HEIGHT} 格。
 *       配合每 {@value #DAMAGE_INTERVAL_TICKS} 刻一次的伤害跳刷新 ⇒ 观感是<b>一次次脉冲闪光</b>，
 *       而不是一根长期杵在那里的实体柱子。</li>
 * </ol>
 */
public class MatinaJudgmentSkill extends Skill {

    /** **本组件的登记 id**（★ 知识归属：组件自己）。 */
    public static final String ID = "matina_skill_judgment";

    /** 引导时长（秒）。 */
    private static final int CHANNEL_SECONDS = 3;

    /** 引导时长（刻）。 */
    private static final long CHANNEL_TICKS = CHANNEL_SECONDS * 20L;

    /** 伤害窗口时长（秒）。 */
    private static final int DURATION_SECONDS = 7;

    /** 伤害窗口时长（刻）。 */
    private static final long DURATION_TICKS = DURATION_SECONDS * 20L;

    /** 技能范围半径（格）—— 魔法阵边缘就是它。 */
    private static final double AURA_RADIUS = 25.0d;

    /** 上方那个小魔法阵的半径（格）。 */
    private static final double SKY_CIRCLE_RADIUS = 10.0d;

    /** 上方小魔法阵的高度（格）：自己上方 3 格。 */
    private static final double SKY_CIRCLE_HEIGHT = 3.0d;

    /** 自身环绕螺旋的高度（格）：需求原话 10 格。 */
    private static final double SPIRAL_HEIGHT = 10.0d;

    /** 特效帧间隔（刻）：每刻一帧（定身与粒子都要跟得上）。 */
    private static final long FRAME_INTERVAL_TICKS = 1L;

    /** 从引导开始到伤害窗口结束的总刻数。 */
    private static final long TOTAL_TICKS = CHANNEL_TICKS + DURATION_TICKS;

    /** 每一跳伤害之间的间隔（刻）：0.5 秒。 */
    private static final int DAMAGE_INTERVAL_TICKS = 10;

    /** 每一跳对每个敌人造成的特殊值伤害。 */
    private static final int DAMAGE_PER_TICK = 5;

    /** 特殊值伤害总量（= 14 跳 × 5 点）。 */
    private static final int TOTAL_SANTE_DAMAGE = 140 / DAMAGE_INTERVAL_TICKS * DAMAGE_PER_TICK;

    /** 冷却：50 秒 = 1000 刻（技能完全结束后才开始）。 */
    private static final int COOLDOWN_TICKS = 1000;

    /** 定身增幅（255 ⇒ 移速归零）。 */
    private static final int ROOT_AMPLIFIER = 255;

    /** 抗性增幅：1 ⇒ 抗性 II。 */
    private static final int RESISTANCE_AMPLIFIER = 1;

    /** 定身 / 抗性的刷新时长（每刻重刷 ⇒ 给 4 刻冗余）。 */
    private static final int ROOT_DURATION_TICKS = 4;

    /** 被推出锚点多少格就拽回来。 */
    private static final double ROOT_MAX_DRIFT = 0.8d;

    /**
     * **脚下像素魔法阵的直径（格）** —— 取 {@code 2 × } 技能半径。
     *
     * <p>即"魔法阵边缘就是技能边缘"这条既有需求在**新画法**下的延续：那张画本身带一圈外环，
     * 铺到整个直径上就还兼着"标出技能范围"的作用。
     */
    private static final double PIXEL_CIRCLE_DIAMETER = AURA_RADIUS * 2d;

    /**
     * 像素画**分几片刻完一遍**（★ 性能阀门）。
     *
     * <p>画有一千多个点；取 {@value #PIXEL_CIRCLE_SLICES} ⇒ **每刻约百来颗**
     * （与替换前的几何魔法阵同量级），每个点每 10 刻（0.5 秒）重画一次
     * —— 末地烛有存活期，所以看上去是连续的一张画。
     * <p>★ 调小 = 更亮但更费；调大 = 更省但会闪。改动前先读
     * {@link MatinaRageVfx#pixelCircle} 的"为什么按片画"。
     */
    private static final int PIXEL_CIRCLE_SLICES = 10;

    /**
     * **脚下像素画魔法阵的自转相位：恒为 0（即不自转）** —— 这是**有意**的，不是漏了。
     *
     * <h2>★ 为什么位图不能转（实测结论）</h2>
     * 原先的几何魔法阵（同心环 + 直径符线）**可以**自转：它**径向对称**，转过之后还是自己。
     * 而这张像素画**不是**径向对称的 ⇒ 两个后果叠加：
     * <ol>
     *   <li>粒子有存活期（末地烛尤其长）⇒ 客户端会把**相邻若干刻**的粒子一起显示；
     *   <li>本实现按片交错绘制（每刻只画 1/{@value #PIXEL_CIRCLE_SLICES}），
     *       一遍要 10 刻才画完 ⇒ 若这 10 刻里相位一直在走，各片就是在**不同角度**画的。
     * </ol>
     * ⇒ 结果是把画**糊成一片旋转残影**（离线仿真实测：相位每刻 +0.1 rad 时，
     * 一遍累计转 57°，图上细节全被抹平；相位固定时才是清晰的法阵）。
     * <p>粗算：半径 25 格处，转 0.012 rad 就位移约 0.3 格 = 一个像素宽
     * ⇒ 想"不糊"就必须**几乎不转**，那还不如干脆不转。
     *
     * <p>★ 想让法阵动起来的话，只有两条路（都要付代价）：① 每刻画完整张（一千多颗/刻，
     * 约 2 万粒子/秒，会明显加重客户端）；② 改成分段跳转（每遍换一个固定角度，会有台阶感）。
     * 两条都没做 —— 阵面周围的动感由上方小阵与白色螺旋提供。
     */
    private static final double PIXEL_CIRCLE_PHASE = 0d;

    /**
     * 像素画资源路径（随 jar 打包；由外部「pixelart 粒子画」数据包转换而来）。
     *
     * <p>★ 该画在插件侧**统一改用白色末地烛**（{@code Particle.END_ROD}）渲染 ⇒
     * 资源里第三列的 RGB 只是占位（解析器要读三列），不参与绘制。
     */
    private static final String PIXEL_CIRCLE_RESOURCE = "/matina_magic_circle.txt";

    /** **引导期**技能边缘的点数（引导期不展开魔法阵，只画这一圈边）。 */
    private static final int EDGE_POINT_COUNT = 120;

    /** 命中者身上**短暂竖直白色光柱**的高度（格）。 */
    private static final double PILLAR_HEIGHT = 4.0d;

    /** 命中者身上短暂竖直白色光柱的采样点数。 */
    private static final int PILLAR_POINTS = 16;

    /** 每一跳的爆炸粒子数量（"你看着怎么华丽怎么来"）。 */
    private static final int IMPACT_PARTICLES = 12;

    private BuffComponent buff;
    private SanTEComponent sante;
    private VitalsComponent vitals;
    private TaskComponent timer;
    private HotbarRenderComponent render;

    /** 引导 + 伤害窗口是否正在进行（挡重复施放：这段时间内冷却还没起算）。 */
    private boolean casting;

    /** 引导是否已经结束（= 进入伤害窗口）。 */
    private boolean channelDone;

    /** 已进行的总刻数（从引导开始算）。 */
    private long elapsedTicks;

    /** 定身锚点。 */
    private Location anchor;

    /** 魔法阵自转相位。 */
    private double circlePhase;

    /** 像素画的分片下标（每刻 +1、循环；见 {@link #PIXEL_CIRCLE_SLICES}）。 */
    private int pixelSlice;

    /**
     * 像素画数据（**JVM 级懒加载一次**，所有马提娜实例共用）。
     * <p>{@code null} = 资源缺失或解析失败 ⇒ 脚下不画阵（其余特效照常），并只报一次日志。
     */
    private static MatinaRageVfx.PixelArt pixelArt;

    /** 是否已经尝试过加载（资源真缺失时避免每刻重试）。 */
    private static boolean pixelArtLoadTried;

    /** 帧任务句柄。 */
    private com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle frameTask;

    /** 结束任务句柄。 */
    private com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle finishTask;

    public MatinaJudgmentSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends Skill.Specification<MatinaJudgmentSkill> {

        public Specification() {
            super(Component.text("神罚"),
                    List.of(Component.text("引导 3 秒后，在脚下展开 25 格魔法阵"),
                            Component.text("持续 7 秒，对范围内敌人共造成 70 点特殊值伤害"),
                            Component.text("期间自身定身并免疫击退（抗性 II）")),
                    COOLDOWN_TICKS,
                    0,
                    Material.HEART_OF_THE_SEA);
            requires(BuffComponent.class).requires(SanTEComponent.class).requires(VitalsComponent.class)
                    .requires(TaskComponent.class).requires(HotbarRenderComponent.class);
        }

        @Override
        public MatinaJudgmentSkill create(String id, ComponentServicesPort services) {
            return new MatinaJudgmentSkill(id, services, this);
        }
    }

    /** **开始生效**：协作组件一次查好缓存进字段（依赖只在 {@code start()} 取）。 */
    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        timer = svc().components().get(TaskComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        //★ 冷却被推迟到"技能完全结束"才起算 ⇒ 这段时间靠自己的标志挡重复施放
        if (casting) {
            return;
        }
        casting = true;
        channelDone = false;
        elapsedTicks = 0L;
        circlePhase = 0d;
        pixelSlice = 0;
        anchor = caster.getLocation().clone();

        //技能物品加附魔光效（需求：附魔来提示正在释放技能）
        repaint();

        World world = caster.getWorld();
        if (world != null) {
            world.playSound(caster.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1.2f, 0.7f);
            world.playSound(caster.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.6f);
        }

        //每刻一帧：定身 + 抗性 2 + 魔法阵 + 螺旋 + 上方小阵
        frameTask = timer.addScheduleRepeating(this, 1L, FRAME_INTERVAL_TICKS, this::frame);
        //到点收工（引导 3 秒 + 伤害 7 秒）
        finishTask = timer.addScheduleLater(this, TOTAL_TICKS, this::finish);
    }

    /**
     * **一帧**：定身 / 抗性 / 特效 / （伤害窗口内的）每 0.5 秒一次特殊值结算。
     */
    private void frame() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            abort();
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            abort();
            return;
        }

        elapsedTicks++;
        if (!channelDone && elapsedTicks >= CHANNEL_TICKS) {
            channelDone = true;
            world.playSound(owner.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.4f, 0.8f);
        }

        //① 定身 + 抗性 2（每刻重刷 ⇒ 与"免疫缓慢"的被动抢写时以本组件为准）
        applyRoot(owner);

        //② 引导期只画【技能边缘 + 自身白色螺旋】；魔法阵要等**引导结束**才展开
        circlePhase += 0.10d;
        Location ground = owner.getLocation().clone().add(0d, 0.12d, 0d);
        if (channelDone) {
            //引导已结束 ⇒ 脚下【像素画魔法阵】+ 上方 3 格的 r=10 小魔法阵
            //★ 脚下那层已由"几何魔法阵"换成外部「pixelart 粒子画」数据包导出的画；
            //  按片交错绘制（每刻约 点数/sliceCount 颗）—— 见 MatinaRageVfx#pixelCircle 的说明。
            //★ 该画的各点一律用【白色末地烛】渲染（需求指定），颜色不取画里的 RGB。
            MatinaRageVfx.PixelArt art = pixelArt();
            if (art != null) {
                //★ 相位恒为 PIXEL_CIRCLE_PHASE（= 0，不自转）—— 位图自转会糊，理由见该常量的注释
                MatinaRageVfx.pixelCircle(world, ground, PIXEL_CIRCLE_DIAMETER, PIXEL_CIRCLE_PHASE,
                        pixelSlice, PIXEL_CIRCLE_SLICES, art, MatinaRageVfx.PIXEL_DUST_SIZE);
                pixelSlice = (pixelSlice + 1) % PIXEL_CIRCLE_SLICES;
            }
            MatinaRageVfx.magicCircle(world, ground.clone().add(0d, SKY_CIRCLE_HEIGHT, 0d),
                    SKY_CIRCLE_RADIUS, -circlePhase * 1.5d, 56);
        } else {
            //引导期：只有一圈"技能边缘"白色点阵（不放魔法阵）
            MatinaRageVfx.dustRing(world, ground, AURA_RADIUS, EDGE_POINT_COUNT, circlePhase,
                    MatinaRageVfx.HOLY_WHITE, 1.1f);
        }
        //自身 10 格白色旋转向上螺旋：**引导期与伤害期都在**
        MatinaRageVfx.holySpiralUp(world, owner.getLocation().clone().add(0d, 0.2d, 0d),
                SPIRAL_HEIGHT, 1.1d, circlePhase * 2.4d, 48);

        //③ 伤害窗口：每 0.5 秒对范围内每个敌人结算一次特殊值伤害
        if (channelDone && elapsedTicks % DAMAGE_INTERVAL_TICKS == 0) {
            applyDamageTick(world, owner);
        }
    }

    /** 定身三件套：缓慢 255 + 清水平速度 + 越界拽回锚点；同时刷新抗性 2。 */
    private void applyRoot(Player owner) {
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SLOWNESS, ROOT_DURATION_TICKS, ROOT_AMPLIFIER);
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, ROOT_DURATION_TICKS, RESISTANCE_AMPLIFIER);
        }
        Vector velocity = owner.getVelocity();
        owner.setVelocity(new Vector(0d, velocity.getY(), 0d));

        if (anchor != null && anchor.getWorld() == owner.getWorld()
                && anchor.distance(owner.getLocation()) > ROOT_MAX_DRIFT) {
            Location back = anchor.clone();
            back.setYaw(owner.getLocation().getYaw());
            back.setPitch(owner.getLocation().getPitch());
            owner.teleport(back);
        }
    }

    /** **一跳特殊值伤害**：r = 25 内每个敌人 −5 点 SanTE + 一身白色爆炸粒子。 */
    private void applyDamageTick(World world, Player owner) {
        if (sante == null) {
            return;
        }
        Location center = owner.getLocation();
        int hit = 0;
        for (Player victim : center.getNearbyPlayers(AURA_RADIUS)) {
            if (victim == null || victim.equals(owner)) {
                continue;
            }
            if (!victim.isOnline() || victim.isDead() || victim.getHealth() <= 0d) {
                continue;
            }
            if (!svc().roleInfo().isHostileTo(victim.getUniqueId())) {
                continue;
            }
            sante.decreaseSanTE(victim.getUniqueId(), DAMAGE_PER_TICK);
            world.spawnParticle(org.bukkit.Particle.EXPLOSION, victim.getLocation().clone().add(0d, 1.0d, 0d),
                    1, 0d, 0d, 0d, 0d);
            MatinaRageVfx.purpleRising(world, victim.getLocation().clone(), 1.8d, circlePhase, 10);
            //需求：引导成功后，内部所有**受到技能影响的人**都会冒出**短暂的**竖直白色光柱粒子
            MatinaRageVfx.whitePillar(world, victim.getLocation().clone(), PILLAR_HEIGHT, PILLAR_POINTS);
            hit++;
        }
        if (hit > 0) {
            world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f, 0.7f);
            world.spawnParticle(org.bukkit.Particle.EXPLOSION, center.clone().add(0d, 1.0d, 0d),
                    IMPACT_PARTICLES, 0d, 0d, 0d, 0d);
        }
    }

    /**
     * **收工**：引导结束后的第 7 秒（= 技能完全结束）—— 解除定身与抗性、**在此启动冷却**、
     * 摘掉附魔光效。
     */
    private void finish() {
        detachTasks();
        casting = false;
        channelDone = false;
        elapsedTicks = 0L;
        anchor = null;

        Player owner = svc().self().player();
        if (owner != null) {
            owner.removePotionEffect(PotionEffectType.SLOWNESS);
            owner.removePotionEffect(PotionEffectType.RESISTANCE);
        }
        //★ "技能完全后冷却"：真正结束的这一刻才起算
        startCooldown();
        repaint();
        if (owner != null) {
            World world = owner.getWorld();
            if (world != null) {
                world.playSound(owner.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.8f, 1.6f);
            }
        }
    }

    /** 施法中被打断（死亡 / 掉线）⇒ 清理并收工，但不给冷却（没有真的放出去）。 */
    private void abort() {
        detachTasks();
        casting = false;
        channelDone = false;
        elapsedTicks = 0L;
        anchor = null;
        repaint();
    }

    /** 拆掉两个任务句柄（幂等）。 */
    private void detachTasks() {
        if (frameTask != null) {
            frameTask.cancel();
            frameTask = null;
        }
        if (finishTask != null) {
            finishTask.cancel();
            finishTask = null;
        }
    }

    /** 请求热键栏重绘（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    /**
     * **施法期间给技能物品加附魔光效**（需求：技能物品附魔来提示正在释放技能）。
     * <p>只加 {@code setEnchantmentGlintOverride(true)} —— 不加真实附魔、不改材质、
     * 更不把它替换成结构空位；冷却态仍由基类默认画法处理。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!casting) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public void stop() {
        detachTasks();
        casting = false;
        channelDone = false;
        elapsedTicks = 0L;
        anchor = null;
        circlePhase = 0d;
    }

    /** **闸门放行？**（基类不查容器 ⇒ 用本组件自己的字段判）。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /** **当前能量**：本组件不参与能量维度（声明耗能 0）⇒ 返回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    /** 是否正在施法（引导 + 伤害窗口）。 */
    public boolean casting() {
        return casting;
    }

    /** 是否已过引导（= 伤害窗口内）。 */
    public boolean channelDone() {
        return channelDone;
    }

    /** 特殊值总伤害（读口：需求声明值 70）。 */
    public static int totalSanteDamage() {
        return TOTAL_SANTE_DAMAGE;
    }

    /** 技能范围半径（读口：需求声明值 25 格，= 魔法阵边缘）。 */
    public static double auraRadius() {
        return AURA_RADIUS;
    }

    // ───────── 像素画魔法阵：资源加载（归组件；Vfx 侧保持"纯几何、零状态"）─────────

    /**
     * **懒加载像素画**（首次用到时读一次插件资源，之后走静态缓存）。
     *
     * <p>资源：{@link #PIXEL_CIRCLE_RESOURCE}，格式 {@code <x> <y> <RRGGBB>}，
     * {@code x/y} 的单位是 **0.1 格**（由外部「pixelart 粒子画」数据包的 mcfunction
     * 去重落格而来 —— 当前源包为 1029 点，网格 257 × 251 ≈ 25.7 × 25.1 格）。
     *
     * <p>★ <b>归一化必须"两轴同除以最大边"</b>：源画两轴的像素间距并不相同
     * （x 与 y 在数据包里都是 0.5 格步进，但去重后的网格跨度不同），若按"各自归一"
     * 就会把画**拉伸** —— 所以这里只用统一的比例因子，长宽比因此与源画逐格一致。
     *
     * <p>★ 第三列的 RGB 在**当前渲染路径下不参与绘制**（一律白色末地烛）；保留三列是
     * 为了解析器口径统一、也便于将来切回红石画法。
     *
     * @return 可用的画；资源缺失 / 为空 / 解析失败 ⇒ {@code null} 并**只报一次**日志
     *         （脚下不画阵，其余特效不受影响 —— 与工程"可缺失即容忍"的既有口径一致）
     */
    private static synchronized MatinaRageVfx.PixelArt pixelArt() {
        if (pixelArtLoadTried) {
            return pixelArt;
        }
        pixelArtLoadTried = true;
        try (InputStream in = MatinaJudgmentSkill.class.getResourceAsStream(PIXEL_CIRCLE_RESOURCE)) {
            if (in == null) {
                Bukkit.getLogger().warning("[matina] 像素画资源缺失（" + PIXEL_CIRCLE_RESOURCE
                        + "）⇒ 神罚脚下魔法阵不画，其余特效不受影响");
                return null;
            }
            MatinaRageVfx.PixelArt art = parsePixelArt(in);
            if (art == null) {
                Bukkit.getLogger().warning("[matina] 像素画资源为空 ⇒ 神罚脚下魔法阵不画");
                return null;
            }
            pixelArt = art;
            Bukkit.getLogger().info("[matina] 神罚像素画魔法阵已加载：" + art.size() + " 点");
            return pixelArt;
        } catch (Exception failure) {
            Bukkit.getLogger().warning("[matina] 像素画加载失败 ⇒ 神罚脚下魔法阵不画：" + failure);
            return null;
        }
    }

    /**
     * **纯解析**：把像素画资源流读成归一化的 {@link MatinaRageVfx.PixelArt}。
     *
     * <p>★ 与 {@link #pixelArt()} 分开的理由有二：① 本方法**不碰 Bukkit、不写日志**
     * ⇒ 可离线单测（{@code pixelArt()} 里的 {@code Bukkit.getLogger()} 在测试 JVM 里会炸）；
     * ② 于是"解析与归一化"这条最容易出错的口径能被测试钉住。
     *
     * <p>格式：每行 {@code <x> <y> <RRGGBB>}，{@code #} 开头为注释、空行忽略。
     * <p>★ <b>归一化口径</b>：两轴**同除以最大边**（不是各自归一）—— 源画两轴像素间距不同
     * （x 0.1 格 / y 0.2 格），各自归一会把画横向拉伸一倍。
     *
     * @param in 资源流
     * @return 归一化后的画；没有任何有效行 ⇒ {@code null}
     * @throws java.io.IOException 读流失败
     */
    static MatinaRageVfx.PixelArt parsePixelArt(InputStream in) throws java.io.IOException {
        List<int[]> points = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;       //注释 / 空行
                }
                String[] token = line.split("\\s+");
                if (token.length < 3) {
                    continue;
                }
                int px = Integer.parseInt(token[0]);
                int py = Integer.parseInt(token[1]);
                int rgb = Integer.parseInt(token[2], 16);
                points.add(new int[]{px, py, rgb});
                if (px < minX) {
                    minX = px;
                }
                if (px > maxX) {
                    maxX = px;
                }
                if (py < minY) {
                    minY = py;
                }
                if (py > maxY) {
                    maxY = py;
                }
            }
        }
        if (points.isEmpty()) {
            return null;
        }
        //★ 中心与跨度都取**真实 min/max**（不假定数据从 0 开始 —— 那样对非零起点的输入会偏移）；
        //  两轴同除以"较大那一维的跨度"⇒ 局部坐标落在 −0.5~+0.5 且**长宽比与源画一致**
        //  （各自归一会把画拉伸：源画两轴像素间距本就不同）。
        double centerX = (minX + maxX) / 2d;
        double centerY = (minY + maxY) / 2d;
        double span = Math.max(1d, Math.max(maxX - minX, maxY - minY));
        float[] localX = new float[points.size()];
        float[] localY = new float[points.size()];
        Color[] colors = new Color[points.size()];
        for (int i = 0; i < points.size(); i++) {
            int[] point = points.get(i);
            localX[i] = (float) ((point[0] - centerX) / span);
            localY[i] = (float) ((point[1] - centerY) / span);
            colors[i] = Color.fromRGB(point[2]);
        }
        return new MatinaRageVfx.PixelArt(localX, localY, colors);
    }
}
