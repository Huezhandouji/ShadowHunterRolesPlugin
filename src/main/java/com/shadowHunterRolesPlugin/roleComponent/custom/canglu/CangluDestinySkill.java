package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.ParticleUtil;
import com.shadowHunterRolesPlugin.core.util.SoundUtil;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.List;

/**
 * **湛蓝命运**（苍鹭）：把「创伤」一次拉满的爆发技。
 *
 * <h2>行为（按顺序）</h2>
 * <ol>
 *   <li>解除自己身上的**所有负面效果** —— 走 {@link BuffComponent#clearDebuff()}
 *       （插件侧负面 buff + 玩家身上的原版 {@code HARMFUL} 药水，一次清干净）；</li>
 *   <li>获得 {@link #SPEED_DURATION_TICKS} 刻（2 秒）速度 {@link #SPEED_AMPLIFIER}
 *       （原版增幅 9 = 速度 X）；</li>
 *   <li>把「创伤」**顶到上限**（停在满层 24/24）并立刻结算一次「深度癔症」——
 *       走 {@code CangluHysteriaPassive#requestRefillStacks()}（护盾与 SAN 回复当场到手）；</li>
 *   <li>接下去 {@link #WINDOW_TICKS} 刻（8 秒）里，**每触发一次「深度癔症」**就额外获得
 *       {@link #ABSORPTION_REFRESH_TICKS} 刻（8 秒）伤害吸收 {@link #ABSORPTION_AMPLIFIER}
 *       （原版增幅 1 = 吸收 II）。</li>
 * </ol>
 *
 * <h2>特效</h2>
 * <ul>
 *   <li><b>施放瞬间</b>：背后画出淡蓝色斜正方形（菱形 3×3、粒子间距 0.2 格，
 *       见 {@link #spawnSigilBurst(Player)}），随后响**一次** {@code entity.wither.death}
 *       （{@link #playCastHorn()}，整段技能只响这一次）；</li>
 *   <li><b>持续期</b>：每 {@link #AMBIENT_PERIOD_TICKS} 刻（10 刻）在玩家身边撒少量蓝色粒子
 *       **并把菱形重画一遍**（{@link #spawnAmbientParticles()} 末尾调 {@link #drawSigil(Player)}）；
 *       同一节拍下，8 音旋律每 {@link #SOUND_LOOP_TICKS} 刻**重复一遍，直到技能结束**
 *       （{@link #startMelodyLoop()}）；</li>
 *   <li>窗口一关（{@link #closeWindow()}）这些任务全部取消，技能结束后不再有粒子与声音。</li>
 * </ul>
 * 所有观感数值集中在“特效数值”那一段常量里，要调只改那里。
 *
 * <h2>8 秒窗口怎么实现的：订阅，而不是反向认识技能</h2>
 * 「深度癔症」的持有者是 {@link CangluHysteriaPassive}，它对外只开一个「结算通知」名单
 * （{@code addResolveListener}）。本组件在 {@link #start()} 里登记、在 {@link #stop()} 里撤销，
 * 收到通知时自己判「窗口还开着吗」。
 * <p>因此本组件不认识被动的内部层数，被动也不认识本组件 —— 两边只在「结算发生了」这一件事上耦合。
 *
 * <h2>「技能完全结束后才开始冷却」</h2>
 * 与「罪恶的辩护」同一条口径：{@link #onCast(CastSignal)} 里**不**调 {@code startCooldown()}，
 * 由 {@link #closeWindow()} 收尾（8 秒窗口走完 / 角色被清）时才启动冷却。
 */
public class CangluDestinySkill extends Skill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "cangluDestinySkill";

    // ───────── 数值（唯一修改点）─────────

    /** 声明冷却（tick）：16 刻。真实启动时机 = 8 秒窗口结束后（不是施放那一刻）。 */
    private static final int COOLDOWN_TICKS = 16;
    /** 单次施放的能量消耗。 */
    private static final int ENERGY_COST = 16;
    /** 速度持续：2 秒 = 40 刻。 */
    private static final int SPEED_DURATION_TICKS = 40;
    /** 速度增幅：9 ⇒ 速度 X（原版增幅从 0 计）。 */
    private static final int SPEED_AMPLIFIER = 9;
    /** 「深度癔症」的强化窗口：8 秒 = 160 刻。 */
    private static final int WINDOW_TICKS = 160;
    /** 窗口内每次结算给的伤害吸收时长：8 秒 = 160 刻。 */
    private static final int ABSORPTION_REFRESH_TICKS = 160;
    /** 伤害吸收增幅：1 ⇒ 吸收 II（8 点）。 */
    private static final int ABSORPTION_AMPLIFIER = 1;

    // ───────── 特效数值（要调观感只改这一段）─────────

    /**
     * 背后菱形（斜正方形）的半对角线长度（格）。
     * <p>“3×3 大小”按**边长 3** 取：斜正方形的对角线与边长同尺度，
     * 半对角线取 1.5 即视觉上"3×3 那么大"（周长 = {@code 4·√2·1.5} ≈ 8.5 格）。
     */
    private static final double SIGIL_HALF_DIAGONAL = 1.5d;
    /** 菱形的半高（竖直方向的那条对角线），比横向略大 ⇒ 拉成纵向菱形而不是正方块。 */
    private static final double SIGIL_HALF_HEIGHT = 1.9d;
    /** 菱形画在背后多远处（格）。 */
    private static final double SIGIL_BEHIND_DISTANCE = 0.55d;
    /** 菱形中心相对脚底的高度（格）——略高于腰部，落在上半身背后。 */
    private static final double SIGIL_CENTER_HEIGHT = 1.15d;

    /**
     * 菱形边框上相邻两颗粒子的间距（格）。间距 = 0.2 ⇒ 整圈约
     * {@code 4·√2·1.5 / 0.2} ≈ 42 颗。
     * <p>按固定间距**逐点摆**（而不是靠 {@code count=0 + speed} 让客户端展成线）：
     * 需求明确给了间距，逐点摆才能保证它就是 0.2 格，也让"重画一遍"的粒子量可预估。
     */
    private static final double SIGIL_PARTICLE_STEP = 0.2d;

    /** 淡蓝色粉尘：这是菱形的主色（{@code DUST} 带颜色数据，能精确取"淡蓝"）。 */
    private static final Color SIGIL_COLOR = Color.fromRGB(150, 220, 255);
    /** 粉尘粒径（{@code Particle.DustOptions} 的 size）。 */
    private static final float SIGIL_DUST_SIZE = 1.1f;
    /** 菱形中散出的 {@code SOUL_FIRE_FLAME} 数量（0 = 该粒子不出现）。 */
    private static final int SIGIL_SOUL_COUNT = 14;

    /** 持续期环境粒子的节拍：每 10 刻一次。 */
    private static final long AMBIENT_PERIOD_TICKS = 10L;
    /** 每次环境粒子的基准数量（"相对少量"）。 */
    private static final int AMBIENT_AMOUNT = 5;
    /** 环境粒子撒在玩家周围多大范围内。 */
    private static final double AMBIENT_SPREAD = 0.85d;
    /** 环境粒子的消散速度（越大越飘）。 */
    private static final double AMBIENT_SPEED = 0.02d;
    /** 环境淡蓝色粉尘的颜色（与菱形同色系、略深一点，避免糊成一片）。 */
    private static final Color AMBIENT_COLOR = Color.fromRGB(110, 190, 250);
    /** 环境粉尘粒径。 */
    private static final float AMBIENT_DUST_SIZE = 0.9f;

    /** 施放瞬间的号角音效：{@code entity.wither.death}（整段技能**只响一次**）。 */
    private static final Sound CAST_SOUND = Sound.ENTITY_WITHER_DEATH;
    /** 号角音量 / 音高。 */
    private static final float CAST_SOUND_VOLUME = 1f;
    private static final float CAST_SOUND_PITCH = 1.15f;

    /** 旋律的基音（= 半音表里的 0 = G4）。 */
    private static final double MELODY_BASE_PITCH = 0.85d;
    /** 旋律八个音的半音表：G4 A4 B4 D5 C5 A4 B4 G5。 */
    private static final int[] MELODY_SEMITONES = { 0, 2, 4, 7, 5, 2, 4, 12 };
    /** 相邻两个音符之间的间隔（刻）：4 刻 = 0.2 秒，8 音约 1.4 秒。 */
    private static final long NOTE_INTERVAL_TICKS = 4L;
    /** 单个音符的音量。 */
    private static final float NOTE_VOLUME = 1f;
    /**
     * **旋律的循环周期（刻）**：一遍走完（8 音 × {@link #NOTE_INTERVAL_TICKS} ≈ 1.4 秒）后，
     * 再过这么久重来一遍，直到技能结束。
     * <p>取 28 刻 ≈ 1.4 秒的等长静默：一遍刚收尾下一遍就起，听感上像连续吟唱。
     * <p><b>循环的只有旋律</b> —— 号角只在施放瞬间响一次。
     */
    private static final long SOUND_LOOP_TICKS = 28L;

    /**
     * **本技能的旋律**（声明值；实播归 {@link SoundUtil}）。
     * <p>音高用**半音相对基音**写，{@link SoundUtil.Melody#pitchOf(int)} 负责换算成原版 pitch：
     * 直接写 {@code 0.5~2.0} 的数既看不出音程关系，也很容易越界被服务端静默钳制。
     * <p>声明放在数值常量**之后**：上面的半音表与间隔是它的构造实参。
     */
    private static final SoundUtil.Melody CAST_MELODY = new SoundUtil.Melody(
            Sound.BLOCK_NOTE_BLOCK_HARP,     // 竖琴：原版音符盒的默认音色，最接近"音符"
            NOTE_VOLUME,
            MELODY_BASE_PITCH,
            MELODY_SEMITONES,
            NOTE_INTERVAL_TICKS);

    // ───────── 状态 ─────────

    /**
     * 强化窗口的截止刻（{@code Bukkit.getCurrentTick()} 口径）；{@code 0} = 窗口没开。
     * <p>它同时是「本技能正在生效」的状态位：窗口开着期间不接第二次施放。
     */
    private int windowUntilTick = 0;

    /** 被动侧留下的登记（{@code stop()} 时按引用撤销）。 */
    private CangluHysteriaPassive.ResolveListener resolveListener;

    /** 窗口收尾任务句柄（角色被清时靠 {@code stop()} 兜底取消）。 */
    private ScheduledHandle closeTask;

    /** 持续期的环境粒子节拍任务句柄（10 刻一次）。 */
    private ScheduledHandle ambientTask;

    /** 环境粒子的随机源（只动装饰性数值，不影响任何判定）。 */
    private final Random random = new Random();

    // ───────── 依赖（全部在 start() 里一次查好）─────────

    private BuffComponent buff;
    private EnergyComponent energy;
    private CangluHysteriaPassive hysteriaPassive;
    private TaskComponent timer;
    private HotbarRenderComponent render;

    public CangluDestinySkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<CangluDestinySkill> {

        public Specification() {
            super(Component.text("湛蓝命运"),
                    List.of(Component.text("解除自身所有负面效果，获得2秒速度X并叠满[创伤]；"
                            + "接下去8秒里每触发一次[深度癔症]就获得8秒伤害吸收II。技能完全结束后才开始冷却")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.LAPIS_BLOCK);
            requires(BuffComponent.class).requires(EnergyComponent.class).requires(TaskComponent.class)
                    .requires(HotbarRenderComponent.class);
            //被动是"有的话更好"：苍鹭模板里它必在，但本组件不该因它缺失就整条装配失败
            requiresOptional(CangluHysteriaPassive.class);
        }

        @Override
        public CangluDestinySkill create(String id, ComponentServicesPort services) {
            return new CangluDestinySkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    protected void onAwake() {
        //本钩子由基类 `awake()` 调用（栏位登记已在基类里完成），不能也不需要调 super.awake()
        //只做不可见的初始化（契约：awake 不得产生玩家可见副作用）
        windowUntilTick = 0;
    }

    @Override
    public void start() {
        //契约：依赖字段在 start() 里一次查好（基类不代查）
        buff = svc().components().get(BuffComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        hysteriaPassive = svc().components().get(CangluHysteriaPassive.class);
        timer = svc().components().get(TaskComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);

        //订阅「深度癔症」的结算通知：被动是可选依赖 ⇒ 取不到就不订阅（本技能其余部分照常可用）
        if (hysteriaPassive != null) {
            resolveListener = hysteriaPassive.addResolveListener(this, ignored -> onHysteriaResolved());
        }
    }

    /**
     * **实例销毁**：撤销订阅、取消三个特效 / 收尾任务句柄、关掉窗口。
     * <p>不调 {@link #closeWindow()}：实例已经死了，不该再写冷却、也不该再请求重绘。
     */
    @Override
    public void stop() {
        if (hysteriaPassive != null && resolveListener != null) {
            hysteriaPassive.removeResolveListener(resolveListener);
        }
        resolveListener = null;
        if (closeTask != null) {
            closeTask.cancel();
            closeTask = null;
        }
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
        windowUntilTick = 0;
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        //闸门：被眩晕 / 沉默时不许施放（与其余技能同一纪律）
        if (!canUse()) {
            return;
        }
        //只接右键：本技能没有 Q / 左键形态
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        //窗口开着 ⇒ 不接第二次（收尾前它占着这条技能的"正在施放"位）
        if (isWindowOpen()) {
            return;
        }
        if (energy == null || !energy.tryConsume(getEnergyCost())) {
            return;
        }
        Player self = svc().self().player();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }

        //① 解除所有负面效果（插件侧 buff + 原版 HARMFUL 药水）
        buff.clearDebuff();

        //② 2 秒速度 X
        buff.applyPotionEffect(PotionEffectType.SPEED, SPEED_DURATION_TICKS, SPEED_AMPLIFIER);

        //③ 开窗口要赶在补层之前：补满这一笔就会立刻结算一次「深度癔症」，
        //   而"接下来的 8 秒"从施放那一刻起算 ⇒ 这一次也该吃到伤害吸收
        windowUntilTick = Bukkit.getCurrentTick() + WINDOW_TICKS;

        //④ 把「创伤」**顶到上限**并立刻结算一次「深度癔症」
        //   （走 requestRefillStacks 而不是 requestAddStackCount：需求要的形态是
        //     "满层 24/24" 同时当场兑现护盾 + SAN 回复 + 通知订阅方，两件事都要 ——
        //     后者会让层数在结算后停在 20/24）
        if (hysteriaPassive != null) {
            hysteriaPassive.requestRefillStacks();
        }

        //⑤ 窗口收尾：8 秒后关窗口并启动冷却（「技能完全结束后才开始冷却」）
        closeTask = timer.addScheduleLater(this, WINDOW_TICKS, this::closeWindow);

        //⑥ 持续期的环境粒子：整段窗口里每 AMBIENT_PERIOD_TICKS 刻撒一小撮 + 重画一次菱形
        ambientTask = timer.addScheduleRepeating(this, AMBIENT_PERIOD_TICKS, AMBIENT_PERIOD_TICKS,
                this::spawnAmbientParticles);

        //⑦ 特效：背后的淡蓝菱形（爆发一次）+ 号角（一次）+ 旋律（循环到技能结束）
        spawnSigilBurst(self);
        playCastHorn();
        startMelodyLoop();

        repaint();
    }

    // ───────── 特效：背后的淡蓝色菱形 ─────────

    /**
     * **在玩家背后画一次淡蓝色斜正方形（菱形）**：几何交给
     * {@link ParticleUtil#drawDiamond(Location, Vector, Vector, double, double, Color, float, double)}
     * （可复用），本方法只负责回答"画在哪、朝哪、多大"。
     *
     * <h2>画在哪 / 朝哪</h2>
     * 平面垂直于玩家**当前**视线：中心 = 背后 {@link #SIGIL_BEHIND_DISTANCE} 格、
     * 脚底之上 {@link #SIGIL_CENTER_HEIGHT} 格；两条半轴 = 视线的水平右方向 + 世界竖直，
     * 因此它立在背后、正对玩家。因为会周期性重画，玩家转身后菱形也跟着转。
     *
     * <p>调用点：施放瞬间一次（{@link #spawnSigilBurst}）、之后每
     * {@link #AMBIENT_PERIOD_TICKS} 刻重画一次（{@link #spawnAmbientParticles}）。
     */
    private void drawSigil(Player self) {
        ParticleUtil.drawDiamond(
                sigilCenter(self),
                rightOf(horizontalLook(self)),
                new Vector(0, 1, 0),
                SIGIL_HALF_DIAGONAL,
                SIGIL_HALF_HEIGHT,
                SIGIL_COLOR,
                SIGIL_DUST_SIZE,
                SIGIL_PARTICLE_STEP);
    }

    /**
     * **施放瞬间的爆发**：一次精确菱形 + 一簇魂火（给"命运降临"一点体积感）。
     * <p>周期性重画只走 {@link #drawSigil}、不带这簇魂火 —— 否则整段窗口里
     * 魂火会一直糊在背后。
     */
    private void spawnSigilBurst(Player self) {
        drawSigil(self);
        if (SIGIL_SOUL_COUNT > 0) {
            self.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, sigilCenter(self), SIGIL_SOUL_COUNT,
                    SIGIL_HALF_DIAGONAL, SIGIL_HALF_HEIGHT, SIGIL_HALF_DIAGONAL, 0.02d);
        }
    }

    /**
     * 菱形中心的世界坐标：玩家背后 {@link #SIGIL_BEHIND_DISTANCE} 格、
     * 脚底之上 {@link #SIGIL_CENTER_HEIGHT} 格。
     */
    private Location sigilCenter(Player self) {
        Vector backward = horizontalLook(self).multiply(-SIGIL_BEHIND_DISTANCE);
        return self.getLocation().clone().add(backward).add(0, SIGIL_CENTER_HEIGHT, 0);
    }

    /** 视线的**水平**单位方向；视线垂直时水平分量退化，退回默认 +Z。 */
    private static Vector horizontalLook(Player self) {
        Vector look = self.getEyeLocation().getDirection().clone().setY(0);
        if (look.lengthSquared() < 1e-6) {
            return new Vector(0, 0, 1);
        }
        return look.normalize();
    }

    /** 水平右方向（Bukkit/原版口径：{@code right = (-dz, 0, dx)}）。 */
    private static Vector rightOf(Vector look) {
        return new Vector(-look.getZ(), 0, look.getX()).normalize();
    }

    /**
     * **持续期的少量蓝色粒子**：以玩家当前位置为中心撒一小撮，
     * **并把背后的菱形重画一遍**（位置与朝向都取"此刻"的玩家状态）。
     * <p>每 {@link #AMBIENT_PERIOD_TICKS} 刻由 {@link #ambientTask} 调一次；
     * 窗口关掉时任务被取消，因此不会在技能结束后继续撒。
     */
    private void spawnAmbientParticles() {
        Player self = svc().self().player();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }
        Location center = self.getLocation().add(0, 1d, 0);
        //每次数量在基准上下浮动，避免看着像固定节拍
        int amount = AMBIENT_AMOUNT + random.nextInt(3);
        self.getWorld().spawnParticle(Particle.DUST, center, amount,
                AMBIENT_SPREAD, AMBIENT_SPREAD, AMBIENT_SPREAD,
                AMBIENT_SPEED, new Particle.DustOptions(AMBIENT_COLOR, AMBIENT_DUST_SIZE));
        self.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, center, 1,
                AMBIENT_SPREAD * 0.6d, AMBIENT_SPREAD * 0.6d, AMBIENT_SPREAD * 0.6d, 0.01d);

        //同一个节拍把背后的菱形重画一遍（位置与朝向取"此刻"的玩家状态）
        drawSigil(self);
    }

    // ───────── 特效：号角（一次）+ 8 音旋律（循环到技能结束） ─────────

    /**
     * **旋律循环到技能结束**：旋律声明见 {@link #CAST_MELODY}，
     * 实播交给 {@link SoundUtil#loopMelody}（每 {@link #SOUND_LOOP_TICKS} 刻一遍）。
     *
     * <p><b>号角不在这里</b>：{@code entity.wither.death} 只在施放那一刻响一次，
     * 由 {@link #playCastHorn()} 负责 —— 循环里重复它会在整段窗口里一直叠着凋零的嘶吼。
     *
     * <p><b>为什么不停在这里</b>：停止条件用"窗口还开着吗"交给工具层判（见
     * {@link SoundUtil#loopMelody} 的口径），因此本组件不需要为它多持一个句柄；
     * 窗口一关，下一拍工具层自己就停了。
     */
    private void startMelodyLoop() {
        Player self = svc().self().player();
        if (self == null) {
            return;
        }
        SoundUtil.loopMelody(self, CAST_MELODY, SOUND_LOOP_TICKS, this::isWindowOpen);
    }

    /** **施放瞬间的号角**：{@code entity.wither.death}，整段技能只响这一次。 */
    private void playCastHorn() {
        Player self = svc().self().player();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }
        self.getWorld().playSound(self.getLocation(), CAST_SOUND, CAST_SOUND_VOLUME, CAST_SOUND_PITCH);
    }

    /**
     * **闸门放行？**（基类不查容器，用本组件自己的字段判）。
     * <p>被眩晕 / 沉默（{@code canCastSkill()}）时不许施放 —— 与其余技能同一条口径。
     */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    /** **当前能量**：本技能参与能量维度（声明耗能 16），因此读真值。 */
    @Override
    protected int currentEnergy() {
        return energy == null ? 0 : energy.current();
    }

    // ───────── 窗口 ─────────

    /** 窗口还开着吗（{@code Bukkit.getCurrentTick()} 口径）。 */
    private boolean isWindowOpen() {
        return Bukkit.getCurrentTick() < windowUntilTick;
    }

    /**
     * **收到一次「深度癔症」结算**：窗口还开着才给奖励（关掉之后到达的通知一律忽略）。
     * <p>本被动每结算一次就会通知一次，而窗口这一段里的每一次都该吃到伤害吸收 II（8 秒）。
     */
    private void onHysteriaResolved() {
        if (!isWindowOpen()) {
            return;
        }
        if (buff == null) {
            return;
        }
        Player self = svc().self().player();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }
        buff.applyPotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_REFRESH_TICKS, ABSORPTION_AMPLIFIER);
    }

    /** **收尾（幂等）**：关窗口 → 取消三个句柄 → **技能完全结束后才启动冷却** → 请求重绘。 */
    private void closeWindow() {
        if (windowUntilTick == 0) {
            return;
        }
        windowUntilTick = 0;
        if (closeTask != null) {
            closeTask.cancel();
            closeTask = null;
        }
        //环境粒子只该在窗口里撒：收尾时一并取消（否则技能结束后还会继续飘）
        if (ambientTask != null) {
            ambientTask.cancel();
            ambientTask = null;
        }
        //「技能完全结束后开始冷却」：本处是全组件唯一调用 startCooldown() 的地方
        startCooldown();
        repaint();
    }

    // ───────── 物品外观（在基类三态画法之上加"窗口剩余秒数"）─────────

    /**
     * **物品增强**：先取基类的完整画法（三态材质 · 名称着色 · 冷却 {@code x.xs} 倒计时 · 状态行 · lore ·
     * 识别键 PDC），再在生效期把名称着色改成青色 + 附魔光效。
     *
     * <p>为什么不重写整套三态画法：那套逻辑（含写识别键这一步）是冻结面，复制一份会立刻产生
     * 「两处实现漂移」，因此这里只做取回 + 追加后缀，其余原样保留。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!isWindowOpen()) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        stack.setItemMeta(meta);
        return stack;
    }

    /** **请求重绘热键栏**（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }
}
