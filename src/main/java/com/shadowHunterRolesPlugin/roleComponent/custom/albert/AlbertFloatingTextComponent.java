package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.datapack.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 「艾尔伯特」的**风格悬浮文本**（继承通用 {@link FloatingTextComponent}，只换词库 / 风格 / 外观）。
 *
 * <h2>人设：一个已经选好路、但仍在迷茫的人</h2>
 * <ul>
 *   <li><b>低沉、克制</b> —— 不喊口号。革命在他嘴里是"铁锈和灰"，不是"光辉与黎明"；</li>
 *   <li><b>带一点自嘲</b> —— 承认自己在雾里、承认不知道明天会不会来；</li>
 *   <li><b>机械感</b> —— 说话像在下指令（"过来。""命中。""下一个。"），
 *       谈及无人机时语气会软一点（那是他唯一的"旧部"）。</li>
 * </ul>
 *
 * <h2>★ 语气随场合迁移（本类的核心机制）</h2>
 * <table border="1">
 *   <tr><th>场合</th><th>语气</th><th>观感</th></tr>
 *   <tr><td>{@link #MOMENT_IDLE} 空闲</td><td>迷茫、冷淡、偶尔自嘲</td>
 *       <td>冷灰紫 · 纯淡入淡出（最克制，不抢戏）</td></tr>
 *   <tr><td>施法 / 技能</td><td>短促、命令式</td>
 *       <td>白泛光 + 加粗 · <b>砸地</b>（机械的重量感）</td></tr>
 *   <tr><td>受击 / 濒死</td><td>咬着牙的克制</td><td>暗红 + 颤抖</td></tr>
 *   <tr><td>击杀</td><td>冷静，像在记账</td><td>冷白 + 泛光 · 砸地</td></tr>
 *   <tr><td>无人机相关</td><td>克制的情感（"它替我开了枪"）</td><td>纯白 · 砸地 / 淡入</td></tr>
 *   <tr><td>过载协议</td><td>沉重，但不煽情</td><td>猩红渐变 + 颤抖 · 砸地</td></tr>
 * </table>
 *
 * <h2>★★ 风格选择（需求："请自行理解台词，并为其配上相应的风格文本和台词颜色"）</h2>
 * <ul>
 *   <li><b>砸地</b>（{@link TextUtil.Style#GROUND_SMASH}）用于"有重量"的场合 ——
 *       施法、击杀、无人机部署、过载。理由：阿尔伯特的台词是<b>短促的命令</b>，
 *       一个字一个字砸下来最像"机械咬合"，而且与他"构筑者"的身份一致；</li>
 *   <li><b>纯淡入淡出</b>（{@link TextUtil.Style#PLAIN_FADE}）用于"轻"的场合 ——
 *       空闲自语、受击、隐形。理由：这些是<b>独白</b>，不该有动作感
 *       （空闲砸地会让角色看起来一直在用力）。</li>
 * </ul>
 *
 * <h2>★ 依赖</h2>
 * 只声明 {@link VitalsComponent}（受击扇出与击杀订阅都走它）。
 * <b>不依赖任何 albert 组件</b> ⇒ 它是依赖图的叶子，别的组件可以单方向依赖它
 * （反向依赖会构成依赖环，装配期直接失败）。
 */
public class AlbertFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_floating_text";

    // ───────── 扩展时刻（基类只定义了 idle / cast / hurt / kill / lowhp / spawn）─────────

    /** 主武器（近战）命中。 */
    public static final String MOMENT_MELEE_HIT = "melee_hit";
    /** 右键射击命中。 */
    public static final String MOMENT_SHOT_HIT = "shot_hit";
    /** 新标记一个「猎杀目标」。 */
    public static final String MOMENT_LOCK = "lock";
    /** 技能一：高斯装配。 */
    public static final String MOMENT_SKILL_1 = "skill_assemble";
    /** 技能二：猎杀指令。 */
    public static final String MOMENT_SKILL_2 = "skill_hunt_order";
    /** 技能三：全功率推进。 */
    public static final String MOMENT_SKILL_3 = "skill_thrust";
    /** 技能四：过载协议。 */
    public static final String MOMENT_SKILL_4 = "skill_overload";
    /** 击杀「猎杀目标」。 */
    public static final String MOMENT_KILL_MARKED = "kill_marked";
    /** 无人机击杀。 */
    public static final String MOMENT_KILL_DRONE = "kill_drone";
    /** 无人机满员。 */
    public static final String MOMENT_FULL = "drones_full";
    /** 战斗中但一架无人机都没有。 */
    public static final String MOMENT_NO_DRONE = "drones_none";
    /** 触发隐形（周围无敌人）。 */
    public static final String MOMENT_CLOAK = "cloak";
    /** 哨戒无人机报警。 */
    public static final String MOMENT_SENTRY = "sentry_alarm";
    /** 触发过度响应协议（主动防御挡下一击）。 */
    public static final String MOMENT_SHIELD = "shield_trigger";
    /** 被控制 / 解控。 */
    public static final String MOMENT_CONTROLLED = "controlled";
    /** 过载协议结束之后。 */
    public static final String MOMENT_OVERLOAD = "overload_after";

    // ───────── 主题色（与 AlbertVfx 同一套色系：紫 / 黑 / 白 / 红）─────────

    /** 空闲·冷灰紫（低饱和，不抢戏）。 */
    private static final int COLD_GRAY_PURPLE = 0x9E9AB8;

    /** 施法·泛光白（机械指令感）。 */
    private static final int COLD_WHITE = 0xF2F6FF;

    /** 受击 / 濒死·暗红。 */
    private static final int DULL_RED = 0xC03A34;

    /** 击杀·冷白偏紫。 */
    private static final int KILL_PALE = 0xE6E2F5;

    /** 无人机相关·纯白（那是他的"旧部"）。 */
    private static final int DRONE_WHITE = 0xFFFFFF;

    /** 标记 / 哨戒·橙红（警戒）。 */
    private static final int ALERT_AMBER = 0xFF8A28;

    /** 隐形·淡紫。 */
    private static final int CLOAK_VIOLET = 0xC9B8FF;

    /** 过载·猩红。 */
    private static final int OVERLOAD_RED = 0xDE2A2C;

    /** 砸地起落高度（格）：施法最高（最重）。 */
    private static final double CAST_FALL_HEIGHT = 6.0d;

    /** 砸地起落高度（格）：击杀 / 无人机 / 过载。 */
    private static final double EMPHASIS_FALL_HEIGHT = 4.0d;

    /** 砸地起落高度（格）：其余。 */
    private static final double LIGHT_FALL_HEIGHT = 3.0d;

    /** 词库（不可变纯数据，构造期建一次）。 */
    private final TextUtil.Lexicon lexicon = buildLexicon();

    /**
     * ★ **战斗台词的内部节流**：主武器命中 / 右键命中是高频事件
     * （近战 0.4 秒一次、射击 0.1 秒一发）⇒ 不节流会变成"机器人复读机"。
     * 池自带的 {@code MIN_GAP_TICKS}（7 刻）挡不住这个密度，因此这里再设一道
     * {@value #COMBAT_LINE_GAP_TICKS} 刻的闸门 —— 平均 1.25 秒最多一句命中台词。
     */
    private static final int COMBAT_LINE_GAP_TICKS = 25;

    /** 上一次说"命中台词"的刻（{@code -1000} = 从未）。 */
    private int lastCombatLineTick = -1000;

    public AlbertFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 对外：技能 / 武器喊话的入口 ─────────

    /**
     * **技能台词**（每个技能一套独立词库 ⇒ 连说 1~2 句）。
     *
     * @param who    主体
     * @param moment 技能对应的时刻常量（{@link #MOMENT_SKILL_1} 等）
     * @return 实际排入的句数
     */
    public int saySkill(Player who, String moment) {
        return sayBurst(who, moment, 1, 2, castBurstGapTicks());
    }

    /**
     * **状态台词**（单句）—— 主武器命中 / 右键命中 / 主动防御 / 哨戒报警 / 满员 / 无无人机 等。
     *
     * <p>★ 命中类（{@link #MOMENT_MELEE_HIT} / {@link #MOMENT_SHOT_HIT}）额外走
     * {@value #COMBAT_LINE_GAP_TICKS} 刻的内部节流 —— 见字段注释。
     *
     * @return 是否真的放出去了（未启动 / 被节流 / 无候选 ⇒ {@code false}）
     */
    public boolean saySpecial(Player who, String moment) {
        if (moment == null) {
            return false;
        }
        boolean combat = MOMENT_MELEE_HIT.equals(moment) || MOMENT_SHOT_HIT.equals(moment);
        if (combat) {
            int now = org.bukkit.Bukkit.getCurrentTick();
            if (now - lastCombatLineTick < COMBAT_LINE_GAP_TICKS) {
                return false;
            }
            lastCombatLineTick = now;
        }
        return say(who, moment, null);
    }

    // ───────── 钩子覆写 ─────────

    @Override
    public void start() {
        super.start();
    }

    /** 本组件只有一本词库，且与运行期状态无关 ⇒ 直接用缓存的那一份。 */
    @Override
    protected TextUtil.Lexicon lexicon() {
        return lexicon;
    }

    /**
     * ★ **风格**：按"这句话有没有重量"分两路（见类注释）。
     */
    @Override
    protected TextUtil.Style styleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE, MOMENT_HURT, MOMENT_LOW_HP, MOMENT_CLOAK -> TextUtil.Style.PLAIN_FADE;
            default -> TextUtil.Style.GROUND_SMASH;
        };
    }

    /**
     * ★ **外观**：色调由场合决定（紫 / 黑 / 白 / 红 四色系）。
     */
    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            // ── 施法 / 技能：泛光白 + 加粗（机械指令）──
            case MOMENT_CAST, MOMENT_SKILL_1, MOMENT_SKILL_2, MOMENT_SKILL_3 ->
                    TextUtil.Look.glowOf(COLD_WHITE).withBold();
            // ── 过载：猩红渐变 + 颤抖 + 加粗（牺牲无人机的沉重）──
            case MOMENT_SKILL_4, MOMENT_OVERLOAD ->
                    TextUtil.Look.gradientOf(OVERLOAD_RED).withTremble().withBold();
            // ── 受击 / 濒死：暗红 + 颤抖（咬着牙的克制）──
            case MOMENT_HURT, MOMENT_LOW_HP ->
                    TextUtil.Look.colored(DULL_RED).withTremble().withBold();
            // ── 击杀：冷白泛光（像在记账）──
            case MOMENT_KILL, MOMENT_KILL_MARKED ->
                    TextUtil.Look.glowOf(KILL_PALE).withBold();
            // ── 无人机相关：纯白（那是他的旧部）──
            case MOMENT_KILL_DRONE, MOMENT_FULL, MOMENT_NO_DRONE ->
                    TextUtil.Look.glowOf(DRONE_WHITE).withBold();
            // ── 标记 / 哨戒：橙红警戒 ──
            case MOMENT_LOCK, MOMENT_SHOT_HIT, MOMENT_SENTRY ->
                    TextUtil.Look.colored(ALERT_AMBER).withBold();
            // ── 主动防御：白 + 颤抖（扛了一下的紧绷）──
            case MOMENT_SHIELD ->
                    TextUtil.Look.glowOf(DRONE_WHITE).withTremble().withBold();
            // ── 隐形：淡紫（安静）──
            case MOMENT_CLOAK ->
                    TextUtil.Look.colored(CLOAK_VIOLET).withBold();
            // ── 被控制：暗红 + 颤抖 ──
            case MOMENT_CONTROLLED ->
                    TextUtil.Look.colored(DULL_RED).withTremble().withBold();
            // ── 登场：泛光白 ──
            case MOMENT_SPAWN ->
                    TextUtil.Look.glowOf(COLD_WHITE).withBold();
            // ── 近战命中：冷白 ──
            case MOMENT_MELEE_HIT ->
                    TextUtil.Look.colored(COLD_WHITE).withBold();
            // ── 空闲：冷灰紫（迷茫、冷淡）──
            default ->
                    TextUtil.Look.colored(COLD_GRAY_PURPLE).withBold();
        };
    }

    /** 砸地高度：施法最高，情绪高点次之，其余最轻。 */
    @Override
    protected double groundOffsetFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_CAST, MOMENT_SKILL_1, MOMENT_SKILL_2, MOMENT_SKILL_3, MOMENT_SKILL_4 ->
                    CAST_FALL_HEIGHT;
            case MOMENT_KILL, MOMENT_KILL_MARKED, MOMENT_KILL_DRONE, MOMENT_OVERLOAD,
                 MOMENT_FULL, MOMENT_NO_DRONE, MOMENT_SPAWN ->
                    EMPHASIS_FALL_HEIGHT;
            default -> LIGHT_FALL_HEIGHT;
        };
    }

    /** 语速：战斗短促（快），空闲慢（冷淡）。 */
    @Override
    protected int speedFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> 95;
            case MOMENT_HURT, MOMENT_LOW_HP -> 110;
            default -> 125;
        };
    }

    /** 施法连说：阿尔伯特话少 ⇒ 1~2 句，句间隔 0.3 秒。 */
    @Override
    protected int castBurstMin() {
        return 1;
    }

    @Override
    protected int castBurstMax() {
        return 2;
    }

    @Override
    protected int castBurstGapTicks() {
        return 6;
    }

    @Override
    protected boolean idleEnabled() {
        return true;
    }

    @Override
    protected boolean lowHpEnabled() {
        return true;
    }

    // ───────── 词库 ─────────

    /** 阿尔伯特的完整语音库（逐条对应需求给出的台词表）。 */
    private static TextUtil.Lexicon buildLexicon() {
        return lexiconOf(
                // ── 空闲：迷茫、冷淡、偶尔自嘲 ──
                MOMENT_IDLE, new String[]{
                        "未来像雾……可我已经学会在雾里装填。",
                        "他们叫它秩序。我只闻到铁锈和灰。",
                        "无人机没有信仰，它们只听从坐标。真羡慕。",
                        "如果这条路注定烧尽，那就让它照亮一点什么。",
                        "我不确定明天会不会来，但今天得有人扣下扳机。",
                        "有什么东西在响……是在催我。",
                        "我只负责今天。",
                        "我犯下的错，只能我来弥补。"
                },
                // ── 登场 ──
                MOMENT_SPAWN, new String[]{
                        "构筑者铳剑——升空。",
                        "零件够用，坐标清晰。",
                        "路已经选好了。剩下的交给今天。"
                },
                // ── 主武器（近战）命中：短促、命令式 ──
                MOMENT_MELEE_HIT, new String[]{
                        "过来。", "命中。", "你被写进猎杀名单了。",
                        "让开，或者倒下。", "以你们的方式，暴力。",
                        "零件入膛，无人机升空。"
                },
                // ── 右键射击命中 ──
                MOMENT_SHOT_HIT, new String[]{
                        "亚音速穿甲——穿透。", "标记完成。慢慢毒发吧。",
                        "猎杀目标，确认。", "别躲。", "标记……你完了。",
                        "六发，够写一段墓志铭。"
                },
                // ── 技能一：高斯装配 ──
                MOMENT_SKILL_1, new String[]{
                        "高斯装配——升空。", "零件够了，展开无人机。",
                        "哨戒就位。谁越线，谁留下。", "编队刷新，全部开火。",
                        "看住这片区域。", "天空归我。"
                },
                // ── 技能二：猎杀指令 ──
                MOMENT_SKILL_2, new String[]{
                        "猎杀指令：集火。", "信号已发，那片区域不要留活口。",
                        "无人机，锁定猎杀目标。", "五秒集火。够撕碎他们。",
                        "标记他们，一个都别漏。", "目标区域锁定——开火。"
                },
                // ── 技能三：全功率推进 ──
                MOMENT_SKILL_3, new String[]{
                        "全功率推进——穿过去。", "墙挡不住我。", "诱饵留下，我们走。",
                        "别被追上。", "只是换条路。", "两次储备，够我绕到他们背后。"
                },
                // ── 技能四：过载协议 ──
                MOMENT_SKILL_4, new String[]{
                        "过载协议——全部引爆。", "无人机，最后一道命令：清场。",
                        "用你们换一条路。", "看看谁能活下来吧。", "这也许是天罚吧。",
                        "继续烧起来吧。", "点火。"
                },
                // ── 杀敌：普通击杀 ──
                MOMENT_KILL, new String[]{
                        "又少一颗钉子。", "浪费时间。", "渣滓。",
                        "无人机记下了。", "下一个。", "别挡路。", "出局。"
                },
                // ── 杀敌：猎杀目标 ──
                MOMENT_KILL_MARKED, new String[]{
                        "目标清除。补充一架无人机。", "猎杀完成。你跑不掉的。",
                        "标记解除——以死亡的方式。"
                },
                // ── 杀敌：无人机击杀 ──
                MOMENT_KILL_DRONE, new String[]{
                        "它替我开了枪。", "看，天空也会审判。", "无人机，干得漂亮。"
                },
                // ── 特殊：无人机满员 ──
                MOMENT_FULL, new String[]{
                        "编队满员。天空是我们的。", "四架就位。该清场了。",
                        "你们永远是我的王牌。"
                },
                // ── 特殊：战斗中，无无人机 ──
                MOMENT_NO_DRONE, new String[]{
                        "啧。", "还有机会。", "这算什么。"
                },
                // ── 特殊：低生命 / 濒死 ──
                MOMENT_LOW_HP, new String[]{
                        "还能走。还能开枪。", "别急着庆祝，我还没倒。",
                        "还没输。", "即便如此……", "别停……继续走……", "不甘心。"
                },
                // ── 特殊：触发隐形 ──
                MOMENT_CLOAK, new String[]{
                        "以逸代劳。", "安静点……猎杀刚刚开始。"
                },
                // ── 特殊：哨戒无人机报警 ──
                MOMENT_SENTRY, new String[]{
                        "哨戒报警——有人进来了。", "有人踩线了。准备集火。"
                },
                // ── 特殊：触发过度响应协议 ──
                MOMENT_SHIELD, new String[]{
                        "主动防御触发。", "又挡下一发……"
                },
                // ── 特殊：锁定猎杀目标 ──
                MOMENT_LOCK, new String[]{
                        "猎杀目标确认。别让他跑。", "找到你了。"
                },
                // ── 特殊：被控制 / 解控 ──
                MOMENT_CONTROLLED, new String[]{
                        "控制不了我……。", "想让我跪下？你不够格。"
                },
                // ── 特殊：过载协议之后 ──
                MOMENT_OVERLOAD, new String[]{
                        "过载结束。", "让火焰烧的大一些吧。"
                },
                // ── 受击 ──
                MOMENT_HURT, new String[]{
                        "……", "记下了。", "不疼。", "继续。",
                        "这点代价，算轻的。"
                }
        );
    }

    /**
     * 本组件的描述符。
     *
     * <p>★ 只 {@code requires(VitalsComponent.class)} —— 受击扇出与击杀订阅都走它。
     * 本类<b>不</b>声明对任何 albert 组件的依赖 ⇒ 别的组件能单方向依赖它而不成环。
     */
    public static final class Specification extends FloatingTextComponent.Specification<AlbertFloatingTextComponent> {

        public Specification() {
            super(Component.text("独白").color(NamedTextColor.LIGHT_PURPLE),
                    List.of(Component.text("状态改变时，周身浮现风格化文本"),
                            Component.text("空闲 / 施法 / 受击 / 击杀 / 濒死 / 登场各有一组台词"),
                            Component.text("另有无人机、哨戒、隐形、主动防御、过载等专属台词")));
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new AlbertFloatingTextComponent(id, services, this);
        }
    }
}
