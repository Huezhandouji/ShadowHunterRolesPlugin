package com.shadowHunterRolesPlugin.core.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * ★ **风格悬浮文本工具箱** —— 与 {@link SoundUtil} / {@link ParticleUtil} 同级的工具类，
 * 收纳在 {@code core/util} 里，角色组件使用"风格实体文本"的**唯一入口**。
 *
 * <h2>它存在的理由（第一目标）</h2>
 * 上游两个数据包（「文本动画库重制版 V26.2」+「Special Texts V4.4」）只能在
 * "装了数据包的世界"里生效。本类把"风格实体文本"从数据包依赖里彻底解放出来：
 * <b>换任何地图、任何存档、不装任何数据包，只要插件在，效果就在。</b>
 *
 * <h2>收纳了什么（原来是 4 个独立类）</h2>
 * <ul>
 *   <li>{@link Style} —— 出现 / 消失风格表（数据包 {@code style 0..35} + Special Texts 模板），纯数据；</li>
 *   <li>{@link Anim} —— 插值数学核心（错峰时刻、进度曲线、缩放 / 位移 / 旋转 / 透明度），纯函数；</li>
 *   <li>{@link Instance} —— 一段活体文本（持有自己的全部字实体，逐刻推进）；</li>
 *   <li>{@link Pool} —— 池：限流 + 总数封顶 + 周期回收。</li>
 * </ul>
 * 前两者**零 Bukkit 依赖**（可离线穷举单测），后两者是 Bukkit 边界。
 *
 * <h2>怎么用（三步）</h2>
 * <pre>{@code
 * // ① 在组件的 start() 里建池
 * private final TextUtil.Pool textPool = TextUtil.newPool(this);
 *
 * // ② 任意时机吐一句话（内部已限流 + 封顶 + 随机摆位）
 * TextUtil.emit(textPool, player, "cast", "判决降临", TextUtil.STYLE_EMPHASIS);
 *
 * // ③ 组件 update() 每刻推一次；stop() 里收工
 * TextUtil.tick(textPool);
 * TextUtil.close(textPool);
 * }</pre>
 *
 * <h2>不做</h2>
 * 不决定"什么时候说话"（那是组件的策略）· 不读玩家血量 / 能量 ·
 * 不注册 Bukkit 任务（池由组件的 {@code update()} 驱动）⇒ 零框架改动、零生命周期负担。
 *
 * <h2>性能边界（必须遵守）</h2>
 * 悬浮文本是"每字一个展示实体"，比粒子贵得多。三层闸门：
 * ① 池内同时最多 {@link Pool#MAX_SEGMENTS} 段；② 单段最多 {@link #MAX_CHARS} 字；
 * ③ 全局最小间隔 {@link Pool#MIN_GAP_TICKS} 刻。别把这些阈值调大。
 */
public final class TextUtil {

    private TextUtil() {
    }

    // ═══════════════════════════════════════════════════════════════════
    //  一、风格表（纯数据，零 Bukkit）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 文本出现 / 消失风格表（**纯数据声明**，无 Bukkit 依赖 ⇒ 可离线穷举单测）。
     *
     * <p><b>来源</b>：把「文本动画库重制版 V26.2」的 {@code style 0..35} 与
     * 「Special Texts V4.4」的若干模板（fade / center_diffuse / step_diffuse /
     * slide* / increase / turnover）逐条对读后，抽成"出现动画维度 + 消失动画维度"两张表。
     * 数据包用 mcfunction + 记分板整数插值实现这些效果；本枚举只声明**观感**，
     * 具体插值由 {@link Anim} 用浮点算（精度高于记分板方案）。
     *
     * <p><b>为什么不是 557 个 mcfunction 的直译</b>：数据包的 mcfunction 有大量是
     * 「记分板搬运 + storage 中转」的机械胶水（例如 {@code operation/matrix/scale/matrix_calc}
     * 递归展开矩阵），真正的语义只有一句"缩放从 0 到 1 插值"。直译会把插件拖进
     * 一个无法维护的泥潭，且拿不到任何好处。
     */
    public enum Style {

        // ───────── 0..10：缩放类 ─────────

        /** 0 逐字放大出现（对应数据包 style=0）。 */
        POP_SCALE(InKind.SCALE_UP, OutKind.FADE),
        /** 1 逐字横向拉伸出现（style=1）。 */
        POP_STRETCH_X(InKind.SCALE_X_UP, OutKind.FADE),
        /** 2 逐字纵向拉伸出现（style=2）。 */
        POP_STRETCH_Y(InKind.SCALE_Y_UP, OutKind.FADE),
        /** 3 逐字缩小出现（style=3）。 */
        SHRINK_IN(InKind.SCALE_DOWN, OutKind.FADE),
        /** 4 逐字横向压缩出现（style=4）。 */
        SQUASH_X_IN(InKind.SCALE_X_DOWN, OutKind.FADE),
        /** 5 逐字纵向压缩出现（style=5）。 */
        SQUASH_Y_IN(InKind.SCALE_Y_DOWN, OutKind.FADE),
        /** 6 逐字随机缩放出现（style=6）。 */
        RANDOM_SCALE_IN(InKind.SCALE_RANDOM, OutKind.FADE),
        /** 7 逐字随机拉伸出现（style=7）。 */
        RANDOM_STRETCH_IN(InKind.SCALE_RANDOM, OutKind.FADE),
        /** 8 逐字随机压缩出现（style=8）。 */
        RANDOM_SQUASH_IN(InKind.SCALE_RANDOM, OutKind.FADE),
        /** 9 逐字横向伸缩出现（style=9）。 */
        PULSE_X_IN(InKind.SCALE_X_PULSE, OutKind.FADE),
        /** 10 逐字纵向伸缩出现（style=10）。 */
        PULSE_Y_IN(InKind.SCALE_Y_PULSE, OutKind.FADE),

        // ───────── 11..13：位移类 ─────────

        /** 11 逐字下落出现（style=11）。 */
        DROP_DOWN_IN(InKind.TRANSLATE_DOWN, OutKind.FADE),
        /** 12 逐字上浮出现（style=12）。 */
        FLOAT_UP_IN(InKind.TRANSLATE_UP, OutKind.FADE),
        /** 13 逐字随机上下滑动出现（style=13）。 */
        RANDOM_SLIDE_IN(InKind.TRANSLATE_RANDOM_V, OutKind.FADE),

        // ───────── 14..15：平面滑动 ─────────

        /** 14 逐字前进出现（style=14；有背景时观感不佳，数据包原话）。 */
        FORWARD_IN(InKind.TRANSLATE_FORWARD, OutKind.FADE),
        /** 15 逐字左滑出现（style=15）。 */
        SLIDE_LEFT_IN(InKind.TRANSLATE_LEFT, OutKind.FADE),

        // ───────── 16..21：翻转类（旋转 90° 入位） ─────────

        /** 16 逐字左翻转出现（style=16）。 */
        FLIP_LEFT_IN(InKind.ROTATE_Y_NEG, OutKind.FADE),
        /** 17 逐字右翻转出现（style=17）。 */
        FLIP_RIGHT_IN(InKind.ROTATE_Y_POS, OutKind.FADE),
        /** 18 逐字随机左右翻转出现（style=18）。 */
        FLIP_RANDOM_Y_IN(InKind.ROTATE_Y_RANDOM, OutKind.FADE),
        /** 19 逐字上翻出现（style=19）。 */
        FLIP_UP_IN(InKind.ROTATE_X_NEG, OutKind.FADE),
        /** 20 逐字下翻出现（style=20）。 */
        FLIP_DOWN_IN(InKind.ROTATE_X_POS, OutKind.FADE),
        /** 21 逐字随机上下翻出现（style=21）。 */
        FLIP_RANDOM_X_IN(InKind.ROTATE_X_RANDOM, OutKind.FADE),

        // ───────── 22..24：旋转类 ─────────

        /** 22 逐字左旋转出现（style=22）。 */
        SPIN_LEFT_IN(InKind.ROTATE_Z_NEG, OutKind.FADE),
        /** 23 逐字右旋转出现（style=23）。 */
        SPIN_RIGHT_IN(InKind.ROTATE_Z_POS, OutKind.FADE),
        /** 24 逐字随机左右旋转出现（style=24）。 */
        SPIN_RANDOM_IN(InKind.ROTATE_Z_RANDOM, OutKind.FADE),

        // ───────── 25..27：乱码类 ─────────

        /** 25 逐字从乱码显形出现（style=25）。 */
        DECODE_IN(InKind.OBFUSCATE_DECODE, OutKind.FADE),
        /** 26 逐字变为乱码后消失（style=26；含淡出音效）。 */
        OBFUSCATE_OUT(InKind.NONE, OutKind.OBFUSCATE),
        /** 27 逐字从乱码显形，变为乱码后消失（style=27）。 */
        DECODE_IN_OBFUSCATE_OUT(InKind.OBFUSCATE_DECODE, OutKind.OBFUSCATE),

        // ───────── 28..35：中心扩散 / 随机淡入淡出一族 ─────────

        /** 28 中心打字出现，中心扩散淡出（style=28）。 */
        CENTER_IN_CENTER_OUT(InKind.CENTER_TYPEWRITER, OutKind.CENTER_DIFFUSE_FADE),
        /** 29 中心扩散淡入，中心打字消失（style=29）。 */
        CENTER_FADE_IN_CENTER_OUT(InKind.CENTER_DIFFUSE_FADE, OutKind.CENTER_TYPEWRITER),
        /** 30 中心扩散淡入，中心扩散淡出（style=30）。 */
        CENTER_FADE_IN_CENTER_FADE_OUT(InKind.CENTER_DIFFUSE_FADE, OutKind.CENTER_DIFFUSE_FADE),
        /** 31 随机淡入，中心打字消失（style=31）。 */
        RANDOM_FADE_IN_CENTER_OUT(InKind.RANDOM_FADE, OutKind.CENTER_TYPEWRITER),
        /** 32 中心打字出现，随机淡出（style=32）。 */
        CENTER_IN_RANDOM_OUT(InKind.CENTER_TYPEWRITER, OutKind.RANDOM_FADE),
        /** 33 随机淡入，随机淡出（style=33）。 */
        RANDOM_FADE_IN_RANDOM_OUT(InKind.RANDOM_FADE, OutKind.RANDOM_FADE),
        /** 34 随机淡入，中心扩散淡出（style=34）。 */
        RANDOM_FADE_IN_CENTER_FADE_OUT(InKind.RANDOM_FADE, OutKind.CENTER_DIFFUSE_FADE),
        /** 35 中心扩散淡入，随机淡出（style=35）。 */
        CENTER_FADE_IN_RANDOM_OUT(InKind.CENTER_DIFFUSE_FADE, OutKind.RANDOM_FADE),

        // ───────── 附加：Special Texts 的高层模板等价物 ─────────

        /**
         * 纯淡入淡出（Special Texts 的 {@code fade}，数据包 V26.2 里没有对应编号）。
         * <p>观感最克制，适合"常驻氛围字"——不会抢走技能本体的视觉注意力。
         */
        PLAIN_FADE(InKind.RANDOM_FADE, OutKind.FADE),

        /**
         * 打字机出现 + 退格消失（Special Texts 的 {@code print2}；
         * 对应数据包 V26.2 的 typewriter 路径 style=1）。
         */
        TYPEWRITER_BACKSPACE(InKind.TYPEWRITER, OutKind.BACKSPACE),

        /**
         * 打字机出现 + 双向退格消失（Special Texts 的 {@code print3}；
         * 对应数据包 V26.2 的 typewriter 路径 style=2）。
         */
        TYPEWRITER_BACKSPACE_BOTH(InKind.TYPEWRITER, OutKind.BACKSPACE_BOTH),

        /**
         * 打字机出现后自然淡去（Special Texts 的 {@code print}；
         * 对应数据包 V26.2 的 typewriter 路径 style=0）。
         */
        TYPEWRITER_FADE(InKind.TYPEWRITER, OutKind.FADE),

        /**
         * 自小扩大出现（Special Texts 的 {@code increase}）。
         * <p>与 {@link #POP_SCALE} 的差别只在节奏（这条更慢、更整段）。
         */
        GROW_SLOW(InKind.SCALE_UP_SLOW, OutKind.FADE),

        /**
         * 自上方滑下出现（Special Texts 的 {@code slidedown}）。
         * <p>与 {@link #DROP_DOWN_IN} 的差别：这条是**整行**下滑而非逐字。
         */
        WHOLE_SLIDE_DOWN(InKind.WHOLE_SLIDE_DOWN, OutKind.FADE),

        /**
         * 整行中心扩散（Special Texts 的 {@code cener_diffuse}，拼写照上游原文）。
         */
        WHOLE_CENTER_DIFFUSE(InKind.WHOLE_CENTER_DIFFUSE, OutKind.FADE),

        // ───────── 附加：本插件原创（数据包里没有对应编号） ─────────

        /**
         * ★ **落地砸字**（原创）—— 文字从空中**受重力加速坠落**，砸到**真实地面**
         * 后压扁回弹，原地"呼吸"滞留一会儿，再**缓慢缩小**消失。
         *
         * <h2>与 {@link #DROP_DOWN_IN} 的区别（别选错）</h2>
         * <ul>
         *   <li>{@code DROP_DOWN_IN}：从上方 1.5 格**匀速**滑到**基准点**就位（不查地面），
         *       之后直接淡出 —— 是"落到位"；</li>
         *   <li>本样式：**真的往下掉**（速度按重力递增），落点由 {@code groundLevelY} 查方块得出，
         *       触地有压扁 / 回弹的物理表现，滞留期有呼吸缩放，最后缩小到 0 —— 是"砸到地上"。</li>
         * </ul>
         *
         * <h2>三个阶段</h2>
         * <ol>
         *   <li><b>落下</b>（{@link Anim#FALL_FRAMES} 帧）：Y 轴由起点加速坠向地面；
         *       因重力特性，逐字也会形成"先后着地"的节奏；</li>
         *   <li><b>落地</b>（{@link Anim#IMPACT_FRAMES} 帧）：压扁 → 回弹过冲 → 复原；</li>
         *   <li><b>滞留 + 消失</b>（{@code stayTicks} 刻呼吸 + {@link Anim#SHRINK_OUT_FRAMES} 帧缩小）。</li>
         * </ol>
         *
         * <h2>落点怎么来（★ 需要调用方提供地面高度）</h2>
         * 落点由 {@link TextUtil#spawnOnGround} 用 {@code groundLevelY} 查方块算出，
         * 写进 {@link Instance} 的 {@code groundDrop} 字段。若调用方走普通 {@link TextUtil#spawn}，
         * 没有地面信息 ⇒ 该字**退化**为"只在原地做压扁回弹 + 缩小消失"（不会掉到世界底部）。
         *
         * <h2>适用</h2>
         * 适合"重物砸下 / 判决落 hammer / 实体化宣言"一类观感；
         * 单字逐个坠地比整行一起掉更有分量。
         */
        GROUND_SMASH(InKind.GROUND_DROP, OutKind.SHRINK_AWAY),

        /**
         * ★★ **米塔风格 / 字体滚地**（移植自数据包 V26.2 的 {@code operation/drop} + world animation
         * 的掉落路径）—— 「**生成时是正常文本，消失时才滚地**」：
         * 字先**原地**正常显示（出现 + 停留，与普通悬浮字一样），
         * 到该消失的时候才从基准点**落到地面**、**触地反弹**、摩擦**滚停**、
         * **躺倒**（俯仰 −88°）在地上，停一会儿再**淡出**。
         *
         * <h2>与 {@link #GROUND_SMASH} 的区别（别选错）</h2>
         * <ul>
         *   <li>{@code GROUND_SMASH}：**一出现就**从空中竖直坠落 → 压扁回弹 → 呼吸 → **缩小**消失；</li>
         *   <li>本样式：**先正常显示**，**消失阶段**才滚地（落 + 弹 + 滚 + 躺倒 + 淡出）。</li>
         * </ul>
         *
         * <h2>物理（自积分，不借实体）</h2>
         * 数据包用"召唤 item 实体 + ride + 原版物理"实现；本实现**不用任何实体**，
         * 自己在 {@code advance()} 里积分（见 {@link Anim#scrollTrackAt} / {@link Anim#scrollRollTicksFor}）
         * ⇒ 可离线穷举单测、零实体开销。
         * 三个过程量：水平速度（摩擦衰减）、竖直速度（重力 + 反弹衰减）、俯仰角（滚动 → 躺倒）。
         *
         * <h2>地面怎么来</h2>
         * 由 {@link TextUtil#spawnOnGround} 查方块得**逐字**地面高度，存进
         * {@link Instance} 的 {@code groundRestPerChar}；滚地阶段据此下落。
         * 走普通 {@link TextUtil#spawn}（无地面信息）时会**退化**为"原地滚一滚 + 淡出"。
         */
        MITA_SCROLL(InKind.MITA_ROLL, OutKind.FADE),
        ;

        /** 出现阶段的行为类别（渲染侧按它选插值曲线）。 */
        public enum InKind {
            NONE,
            SCALE_UP, SCALE_X_UP, SCALE_Y_UP,
            SCALE_DOWN, SCALE_X_DOWN, SCALE_Y_DOWN,
            SCALE_RANDOM, SCALE_X_PULSE, SCALE_Y_PULSE,
            TRANSLATE_DOWN, TRANSLATE_UP, TRANSLATE_RANDOM_V, TRANSLATE_FORWARD, TRANSLATE_LEFT,
            ROTATE_Y_NEG, ROTATE_Y_POS, ROTATE_Y_RANDOM,
            ROTATE_X_NEG, ROTATE_X_POS, ROTATE_X_RANDOM,
            ROTATE_Z_NEG, ROTATE_Z_POS, ROTATE_Z_RANDOM,
            OBFUSCATE_DECODE,
            CENTER_TYPEWRITER, CENTER_DIFFUSE_FADE, RANDOM_FADE,
            /**
             * ★ 米塔风格：**位置自积分**——生成时原地不动（正常文本），
             * 消失阶段由 {@link Anim#scrollTrackAt} 给出落 + 弹 + 滚的位置（见 {@link #MITA_SCROLL}）。
             */
            MITA_ROLL,
            TYPEWRITER,
            SCALE_UP_SLOW,
            WHOLE_SLIDE_DOWN, WHOLE_CENTER_DIFFUSE,
            /** ★ 原创：受重力坠向真实地面，落点由调用方经 {@code groundLevelY} 给出。 */
            GROUND_DROP
        }

        /** 消失阶段的行为类别。 */
        public enum OutKind {
            FADE,
            OBFUSCATE,
            BACKSPACE,
            BACKSPACE_BOTH,
            CENTER_DIFFUSE_FADE,
            CENTER_TYPEWRITER,
            RANDOM_FADE,
            /** ★ 原创：**缩小到 0** 后消失（不是淡出）—— 落地的"缓慢缩小消失"用它。 */
            SHRINK_AWAY
        }

        private final InKind in;
        private final OutKind out;

        Style(InKind in, OutKind out) {
            this.in = in;
            this.out = out;
        }

        public InKind in() {
            return in;
        }

        public OutKind out() {
            return out;
        }

        /**
         * 本样式的**逐字错峰**是否需要"从中心向外"（中心扩散一族）。
         * <p>数据包的 center_diffuse 用 {@code (字数-1)/2 * 0.23} 算出中心偏移，
         * 再让每个字按"到中心的距离"决定启动刻；本方法把这个语义暴露成读口，
         * 渲染侧据此决定错峰顺序。
         */
        public boolean centerStaggered() {
            return in == InKind.CENTER_TYPEWRITER
                    || in == InKind.CENTER_DIFFUSE_FADE
                    || in == InKind.WHOLE_CENTER_DIFFUSE
                    || out == OutKind.CENTER_DIFFUSE_FADE
                    || out == OutKind.CENTER_TYPEWRITER;
        }

        /**
         * 本样式的**逐字错峰**是否需要"随机顺序"。
         * <p>对应数据包 style 6/7/8/13/18/21/24 与 31..35 的"随机"字样。
         */
        public boolean randomStaggered() {
            return in == InKind.SCALE_RANDOM
                    || in == InKind.TRANSLATE_RANDOM_V
                    || in == InKind.ROTATE_Y_RANDOM
                    || in == InKind.ROTATE_X_RANDOM
                    || in == InKind.ROTATE_Z_RANDOM
                    || in == InKind.RANDOM_FADE
                    || out == OutKind.RANDOM_FADE;
        }

        /**
         * 出现阶段是否需要"逐字依次点亮"（打字机 / 错峰一族）。
         * <p>{@code false} = 整段同时出现（如 {@link #PLAIN_FADE} 的整行淡入）。
         */
        public boolean staggeredIn() {
            return in != InKind.NONE;
        }

        /**
         * 按数据包 V26.2 的**特效动画编号**取样式（0..35）。
         * <p>编号越界 ⇒ 返回 {@link #POP_SCALE}（数据包对非法 style 的处理是"报错并停在默认值"，
         * 本实现选择静默回退，因为运行期不该因为一个数字丢出异常）。
         *
         * @param styleNumber 数据包的 style 值
         * @return 对应样式；越界时返回默认样式
         */
        public static Style byDatapackNumber(int styleNumber) {
            Style[] table = NUMBERED;
            if (styleNumber < 0 || styleNumber >= table.length) {
                return POP_SCALE;
            }
            return table[styleNumber];
        }

        /** 编号表（0..35；本枚举里带编号的那一批，顺序必须与枚举声明序一致）。 */
        private static final Style[] NUMBERED = {
                POP_SCALE, POP_STRETCH_X, POP_STRETCH_Y,
                SHRINK_IN, SQUASH_X_IN, SQUASH_Y_IN,
                RANDOM_SCALE_IN, RANDOM_STRETCH_IN, RANDOM_SQUASH_IN,
                PULSE_X_IN, PULSE_Y_IN,
                DROP_DOWN_IN, FLOAT_UP_IN, RANDOM_SLIDE_IN,
                FORWARD_IN, SLIDE_LEFT_IN,
                FLIP_LEFT_IN, FLIP_RIGHT_IN, FLIP_RANDOM_Y_IN, FLIP_UP_IN, FLIP_DOWN_IN, FLIP_RANDOM_X_IN,
                SPIN_LEFT_IN, SPIN_RIGHT_IN, SPIN_RANDOM_IN,
                DECODE_IN, OBFUSCATE_OUT, DECODE_IN_OBFUSCATE_OUT,
                CENTER_IN_CENTER_OUT, CENTER_FADE_IN_CENTER_OUT, CENTER_FADE_IN_CENTER_FADE_OUT,
                RANDOM_FADE_IN_CENTER_OUT, CENTER_IN_RANDOM_OUT, RANDOM_FADE_IN_RANDOM_OUT,
                RANDOM_FADE_IN_CENTER_FADE_OUT, CENTER_FADE_IN_RANDOM_OUT
        };

        /** 带数据包编号的样式个数（= 36，对应 style 0..35）。 */
        public static int numberedCount() {
            return NUMBERED.length;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  二、插值数学（纯函数，零 Bukkit）
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 文本动画的**纯函数数学核心**（零 Bukkit 依赖 ⇒ 可离线穷举单测）。
     *
     * <h2>它替代了什么</h2>
     * 数据包用「记分板 + storage + {@code data modify} 递归」做插值，例如
     * {@code operation/matrix/scale/matrix_calc} 把矩阵逐项乘完再拆成 3 元组。
     * 那套机制的**全部语义**就是"把一个 0→1 的进度按曲线映射成缩放 / 位移 / 旋转 / 透明度"。
     * 本类直接给出浮点函数，省掉整条搬运链，且**精度更高**（数据包受整数记分板限制，
     * 例如 {@code text_opacity} 被压到 0..255 的整数）。
     *
     * <h2>时间模型（与数据包一致的那部分）</h2>
     * <ul>
     *   <li>{@code speed} 与数据包同口径：间隔 {@code T = 3 * 100 / speed} 刻（下限 1 刻）；</li>
     *   <li>{@code scale} 同时放大"字距"与"实体缩放"（数据包 {@code shift = 0.228 * scale}，
     *       单字实体边长 {@code 0.225 * scale}）；</li>
     *   <li>{@code stay} = 全部字生成完后的滞留刻数（数据包默认 55）。</li>
     * </ul>
     *
     * <h2>边界</h2>
     * 本类不做任何 clamp 之外的状态变更；所有方法对非法入参（NaN / 负刻数）返回安全值，
     * 不抛异常（渲染路径上抛异常会被框架判为组件故障并隔离整个角色，代价过大）。
     */
    public static final class Anim {

        private Anim() {
        }

        // ───────── 常量：与数据包对齐 ─────────

        /** 单字展示实体边长（数据包 {@code calc_pos}：{@code 0.228 * scale} 是字距基数）。 */
        public static final double CHAR_PITCH_BASE = 0.228d;

        /** 数据包 speed 基准：间隔 = {@code 3 * 100 / speed} 刻。 */
        public static final int SPEED_BASE = 100;

        /** 数据包 speed 的基准间隔刻数。 */
        public static final int SPEED_BASE_TICKS = 3;

        /** 数据包默认滞留刻数（{@code stay} 的默认值）。 */
        public static final int DEFAULT_STAY_TICKS = 55;

        /** 出现阶段占用的插值帧数（数据包 fast 曲线 12 项 / slow 曲线 20 项的上限）。 */
        public static final int IN_FRAMES_MAX = 20;

        /** 消失阶段占用的插值帧数（数据包 {@code #value.15} ⇒ 把 255 分成 15 步）。 */
        public static final int OUT_FRAMES = 15;

        // ───────── 常量：落地砸字（原创）─────────

        /** 落下阶段帧数：从起点坠到地面用这么久（越大越慢、越"飘"）。 */
        public static final int FALL_FRAMES = 12;

        /** 落地冲击阶段帧数：压扁 → 回弹 → 复原（帧数固定，与下落距离无关 ⇒ 观感稳定）。 */
        public static final int IMPACT_FRAMES = 8;

        /** 缩小消失帧数：滞留结束到彻底消失的帧数（比 {@link #OUT_FRAMES} 慢 ⇒ "缓慢缩小"）。 */
        public static final int SHRINK_OUT_FRAMES = 22;

        /** 触地压扁时 Y 轴的最低比例。 */
        public static final double IMPACT_SQUASH_Y = 0.55d;

        /** 触地压扁时 X / Z 的外扩比例（体积看着守恒，压扁才不假）。 */
        public static final double IMPACT_SQUASH_XZ = 1.22d;

        /** 回弹过冲时 Y 轴的最高比例。 */
        public static final double IMPACT_BOUNCE_Y = 1.18d;

        /** 回弹过冲时 X / Z 的收缩比例。 */
        public static final double IMPACT_BOUNCE_XZ = 0.88d;

        /** 滞留期呼吸缩放的幅度（在 {@code 1} 与 {@code 1 - 此值} 之间来回）。 */
        public static final double BREATHE_AMPLITUDE = 0.03d;

        /** 滞留期呼吸一个来回所需的刻数。 */
        public static final int BREATHE_PERIOD_TICKS = 40;

        /**
         * ★ **颤抖**：文字出现后，其**局部坐标**在整个生命周期内抖动的幅度（格）。
         *
         * <p>实现方式 = 用确定性伪随机给每一帧一个抖动偏移（不是正弦）。
         * 正弦看着像"摇晃"，伪随机才像"抖"—— 因为颤抖的本质是**不规则的**高频位移。
         */
        public static final double TREMBLE_AMPLITUDE = 0.035d;

        /** ★ 颤抖的频率：每几帧换一次抖动方向（1 = 每帧都换，最躁）。 */
        public static final int TREMBLE_STEP_FRAMES = 1;

        /** 落地砸字的最大下落高度（格）：超过就按此值算（防玩家在几百格高空刷屏）。 */
        public static final double MAX_FALL_HEIGHT = 24d;

        // ───────── 常量：米塔风格（字体竖直掉落，移植自数据包 operation/drop）─────────

        /**
         * ★ **竖直初速度的额外抬升下限 / 上限**（格/刻）。
         *
         * <p>★ 2026-10-03 改版：本样式从"抛出去再滚"改成**纯竖直下落** ——
         * 生成时是正常文本（原地不动），消失时**在原地正下方竖直掉到地面**。
         * 因此水平分量（{@code SCROLL_SPEED_*}）与抛起（{@code SCROLL_LIFT_*}）已**废除**，
         * 改为一个"每字略有差异的下落初速"，让一排字落下时有轻微参差
         * （全部同一初速会像"一整块板平移下来"，很假）。
         */
        public static final double SCROLL_LIFT_MIN = 0.02d;

        /** 下落初速上限（格/刻）。 */
        public static final double SCROLL_LIFT_MAX = 0.10d;

        /** 下落重力（格/刻²）：每刻竖直速度递减这么多（越大地越"沉"）。 */
        public static final double SCROLL_GRAVITY = 0.045d;

        /**
         * 下落**反弹衰减系数**：每次触地后竖直速度乘以它（< 1 ⇒ 越弹越低）。
         *
         * <p>这就是数据包"骑在 item 实体上让它自由落体"的等价物 —— 数据包用原版物理，
         * 这里自己积分（不依赖任何实体 ⇒ 可离线单测、不占实体）。
         */
        public static final double SCROLL_BOUNCE_DAMPING = 0.42d;

        /** 下落的最低弹起阈值（格/刻）：竖直速度低于它就不再弹 ⇒ 直接贴地进入静止。 */
        public static final double SCROLL_REST_VELOCITY = 0.06d;

        /**
         * ★ **落地位置的横向漂移上限**（格）。
         *
         * <p>让每个字落在**自己那一小片**地面上（而不是全部叠在同一条竖线上），
         * 观感上像"被弹了一下、歪着停下去了"。
         * ★ 口径：字**只在下落/弹跳期间**横向漂移，**一旦静止就锁死** ——
         * 落地后它要被 `Billboard.FIXED` 钉住，若还在横向滑动，会出现"钉住了却还在平移"的矛盾。
         */
        public static final double SCROLL_DRIFT_MAX = 0.55d;

        /**
         * ★ 横向漂移速度的**衰减系数**：每次触地后水平速度乘以它。
         *
         * <p>越小 ⇒ 漂移越早停下（字更"稳"）。取 < 1 保证静止前速度已趋近 0，
         * 使"锁死那一刻"不出现可见的位置跳变。
         */
        public static final double SCROLL_DRIFT_DAMPING = 0.35d;

        /**
         * ★★ **落地躺倒朝向的每字随机幅度**（度）。
         *
         * <p>落地后不再全体统一 −88°，而是每字在
         * {@code SCROLL_LIE_PITCH ± SCROLL_LIE_JITTER} 内各取一个**固定**值
         * （由 {@code index + seed} 决定 ⇒ 同一句话每次播放一致，可离线单测）。
         * 这样一排字躺下去的"歪法"各不相同，而不是像盖了个章。
         */
        public static final float SCROLL_LIE_JITTER = 24f;

        /**
         * ★★ **落地后附加的偏航抖动**（度，绕 Y 轴）。
         *
         * <p>只有俯仰的话，一排字躺倒后朝向仍"齐刷刷"。加一点偏航
         * ⇒ 每个字落地后**转了个不同的方向**躺着，更像散落的实物。
         */
        public static final float SCROLL_LIE_YAW_JITTER = 30f;

        /**
         * ★★ **空中翻滚的额外圈数上限**（圈）。
         *
         * <p>"更丝滑的物理翻动" = 下落期间**多翻几圈**（而不是最多翻半圈到躺倒）。
         * 每字圈数由随机决定（{@code 0..上限}），落地前正好转到躺倒角 ⇒ 收尾自然。
         */
        public static final int SCROLL_TUMBLE_MAX_TURNS = 2;

        /** 落地"躺平"的基准俯仰角（度）：落地后字躺平（数据包 {@code tp ~ ~ ~ ~ -88}）。 */
        public static final float SCROLL_LIE_PITCH = -88f;

        /**
         * 滚地静止后**停留刻数**：字躺在地上不动这么久，然后开始淡出
         * （数据包落地后走 {@code fade/main}，以 {@code #value.15} 为步长降 {@code text_opacity}）。
         */
        public static final int SCROLL_REST_TICKS = 30;

        /** 滚地淡出帧数：与数据包 {@code fade} 的 15 步一致。 */
        public static final int SCROLL_FADE_FRAMES = 15;

        // ───────── 时间 ─────────

        /**
         * 逐字间隔（刻）：数据包 {@code T = floor(3 * 100 / speed)}，且至少 1 刻。
         *
         * @param speed 数据包口径的 speed（越大越快）；{@code <= 0} ⇒ 走默认 {@value #SPEED_BASE}
         */
        public static int staggerTicks(int speed) {
            int effective = speed <= 0 ? SPEED_BASE : speed;
            int ticks = (int) Math.floor(SPEED_BASE_TICKS * (double) SPEED_BASE / effective);
            return Math.max(1, ticks);
        }

        /**
         * 一段动画的总刻数：错峰铺满 + 出现插值 + 滞留 + 消失插值。
         *
         * @param charCount  字数（{@code <= 0} ⇒ 按 1 算）
         * @param speed      数据包口径的 speed
         * @param stayTicks  滞留刻数（{@code < 0} ⇒ 取默认 {@value #DEFAULT_STAY_TICKS}）
         * @param inFrames   出现插值帧数（{@code <= 1} ⇒ 无出现插值）
         * @param hasOut     是否有消失阶段
         */
        public static int totalTicks(int charCount, int speed, int stayTicks, int inFrames, boolean hasOut) {
            int count = Math.max(1, charCount);
            int stay = stayTicks < 0 ? DEFAULT_STAY_TICKS : stayTicks;
            int in = Math.max(1, inFrames);
            int out = hasOut ? OUT_FRAMES : 0;
            return (count - 1) * staggerTicks(speed) + in + stay + out;
        }

        /**
         * 按**风格**算一段动画的总刻数（渲染侧的权威口径；落地砸字走自己的三段时长）。
         *
         * @param style       风格；{@code null} ⇒ 按 {@link Style#PLAIN_FADE} 算
         * @param charCount   字数
         * @param speed       数据包口径的 speed
         * @param stayTicks   滞留刻数（{@code < 0} ⇒ 默认）
         * @param fallHeight  落地砸字的**下落高度**（格；其余样式忽略）。越高掉得越久。
         */
        public static int totalTicksOf(Style style, int charCount, int speed,
                                       int stayTicks, double fallHeight) {
            Style eff = style == null ? Style.PLAIN_FADE : style;
            int count = Math.max(1, charCount);
            int stay = stayTicks < 0 ? DEFAULT_STAY_TICKS : stayTicks;
            int stagger = (count - 1) * staggerTicks(speed);

            if (eff == Style.GROUND_SMASH) {
                return stagger + fallFramesFor(fallHeight) + IMPACT_FRAMES
                        + stay + SHRINK_OUT_FRAMES;
            }
            if (eff == Style.MITA_SCROLL) {
                // ★ 先"正常显示"（出现 + 停留），再滚地消失
                int in = 8;
                return stagger + in + stay + scrollRollTicksFor(fallHeight);
            }
            int in = eff.in() == Style.InKind.NONE ? 1 : 8;
            int out = eff.out() == Style.OutKind.SHRINK_AWAY
                    ? SHRINK_OUT_FRAMES
                    : (eff.out() == Style.OutKind.FADE || eff.out() == Style.OutKind.OBFUSCATE
                            ? OUT_FRAMES : 0);
            return stagger + in + stay + out;
        }

        /**
         * **下落帧数**（按高度算）：越高掉得越久，但设了上限
         * （{@link #MAX_FALL_HEIGHT} 封顶 ⇒ 玩家飞到 300 格高空也不会长时间挂着一串字）。
         *
         * <p>口径：每 1 格下落约 1 帧，最少 {@code 4} 帧（矮处也要看得出"掉下来"），
         * 最多按 {@link #MAX_FALL_HEIGHT} 算。
         */
        public static int fallFramesFor(double fallHeight) {
            double h = Math.max(0d, Math.min(fallHeight, MAX_FALL_HEIGHT));
            return (int) Math.max(4d, Math.round(h));
        }

        /**
         * 第 {@code index} 个字（共 {@code count} 个）的**启动刻**（错峰）。
         *
         * <p>三种错峰方式（对应数据包的三类语义）：
         * <ul>
         *   <li>顺序：{@code index * step}；</li>
         *   <li>中心外扩：以中点为 0，离中点越远越晚（数据包 center_diffuse 的
         *       {@code pos = (count-1)/2} 起手，再按 {@code 0.23} 逐字右移）；</li>
         *   <li>随机：由 {@code seed} 决定，此处用确定性打散（同一 seed 恒定 ⇒ 可单测）。</li>
         * </ul>
         *
         * @param index 字下标（0 起）；越界 ⇒ 返回 0
         * @param count 总字数（{@code <= 0} ⇒ 按 1 算）
         * @param style 风格（决定错峰方式）
         * @param speed 数据包口径的 speed
         * @param seed  随机错峰的种子（顺序 / 中心错峰忽略它）
         */
        public static int startTickOf(int index, int count, Style style, int speed, long seed) {
            int total = Math.max(1, count);
            int safeIndex = (index < 0 || index >= total) ? 0 : index;
            int step = staggerTicks(speed);

            if (style != null && style.centerStaggered()) {
                // 中心为 0，两侧对称外扩（数据包把"第一个字"放在最左，中心在 (count-1)/2 处）
                double center = (total - 1) / 2d;
                int distance = (int) Math.round(Math.abs(safeIndex - center));
                return distance * step;
            }
            if (style != null && style.randomStaggered()) {
                // 确定性打散：把 (index, seed) 混进一个 0..total-1 的置换位置
                return Math.floorMod(mix(safeIndex, seed), total) * step;
            }
            return safeIndex * step;
        }

        /** 确定性混合（不依赖 {@code java.util.Random} 的全局状态 ⇒ 可复现、可单测）。 */
        private static int mix(int index, long seed) {
            long x = index * 0x9E3779B97F4A7C15L ^ seed * 0xC2B2AE3D27D4EB4FL;
            x ^= (x >>> 29);
            x *= 0xBF58476D1CE4E5B9L;
            x ^= (x >>> 32);
            return (int) (x & 0x7FFFFFFFL);
        }

        // ───────── 插值曲线 ─────────

        /**
         * 出现进度：{@code 0 → 1}，带**过冲**（数据包 fast/slow 曲线的末尾有
         * {@code 1.033} 再回落 {@code 1.000} 的弹性感，本方法用 back-ease 复刻）。
         *
         * @param frame 当前帧（0 起）；{@code <= 0} ⇒ 返回 0；{@code >= frames} ⇒ 返回 1
         * @param frames 总帧数
         */
        public static double inProgress(int frame, int frames) {
            if (frames <= 1) {
                return 1d;
            }
            double t = frame / (double) (frames - 1);
            if (t <= 0d) {
                return 0d;
            }
            if (t >= 1d) {
                return 1d;
            }
            // back-ease-out：先冲过 1 再回落（系数 1.033 ≈ 数据包曲线的峰值）
            double s = 1.70158d * 1.033d;
            double p = t - 1d;
            return 1d + (s + 1d) * p * p * p + s * p * p;
        }

        /**
         * 消失进度：{@code 1 → 0}，线性（与数据包 {@code fade.step = 255/15} 的等步长一致）。
         *
         * @param frame 当前帧（0 起）；{@code <= 0} ⇒ 返回 1；{@code >= frames} ⇒ 返回 0
         * @param frames 总帧数
         */
        public static double outProgress(int frame, int frames) {
            if (frames <= 0) {
                return 0d;
            }
            double t = frame / (double) frames;
            if (t <= 0d) {
                return 1d;
            }
            if (t >= 1d) {
                return 0d;
            }
            return 1d - t;
        }

        /** 中心扩散的空间权重：离中心越远 ⇒ 值越大（0 在中心，1 在两端）。 */
        public static double centerWeight(int index, int count) {
            if (count <= 1) {
                return 0d;
            }
            double center = (count - 1) / 2d;
            double maxDistance = Math.max(center, 1d);
            double w = Math.abs(index - center) / maxDistance;
            return clamp01(w);
        }

        // ───────── 落地砸字（原创）─────────

        /**
         * **自由落体进度曲线**：{@code 0 → 1}，加速（{@code p = (frame/frames)²}）。
         *
         * <p>与线性下落的关键差别：重力让字"越掉越快"，起动慢、着地猛。
         * 这也是"地面重击感"的主要来源 —— 线性下落看起来像电梯，抛物线看起来才像东西掉下来。
         *
         * @param frame  已经历帧数（0 起）
         * @param frames 总帧数；{@code <= 0} ⇒ 返回 1（当作已落地）
         * @return 0..1 的进度（0 = 还在起点，1 = 已触地）
         */
        public static double fallProgress(int frame, int frames) {
            if (frames <= 0) {
                return 1d;
            }
            double t = clamp01(frame / (double) frames);
            return t * t;
        }

        /**
         * **落地冲击曲线**（只有 Y 轴）：压扁 → 回弹过冲 → 复原。
         *
         * <p>曲线形状（{@code t} 为冲击阶段进度 0..1）：
         * <pre>
         *   t=0.00  → 1.00   （触地瞬间还是原样）
         *   t=0.25  → 0.55   （最快的一瞬被压扁 —— IMPACT_SQUASH_Y）
         *   t=0.60  → 1.18   （回弹过冲 —— IMPACT_BOUNCE_Y）
         *   t=1.00  → 1.00   （稳定）
         * </pre>
         * 用两段抛物线拼出，不引任何缓动库（纯算术 ⇒ 可离线穷举单测）。
         *
         * @param frame 冲击阶段帧数（0 起）
         * @param frames 冲击阶段总帧数（{@link #IMPACT_FRAMES}）；{@code <= 0} ⇒ 返回 1
         */
        public static double impactScaleY(int frame, int frames) {
            if (frames <= 0) {
                return 1d;
            }
            double t = clamp01(frame / (double) frames);
            if (t <= 0.25d) {
                // 压扁段：1 → IMPACT_SQUASH_Y（抛物线下压）
                double s = t / 0.25d;
                return 1d - (1d - IMPACT_SQUASH_Y) * (2d * s - s * s);
            }
            if (t <= 0.60d) {
                // 回弹段：IMPACT_SQUASH_Y → IMPACT_BOUNCE_Y（抛物线上冲）
                double s = (t - 0.25d) / 0.35d;
                double eased = s * (2d - s);
                return IMPACT_SQUASH_Y + (IMPACT_BOUNCE_Y - IMPACT_SQUASH_Y) * eased;
            }
            // 稳定段：IMPACT_BOUNCE_Y → 1（缓慢收回）
            double s = (t - 0.60d) / 0.40d;
            double eased = s * (2d - s);
            return IMPACT_BOUNCE_Y + (1d - IMPACT_BOUNCE_Y) * eased;
        }

        /**
         * **落地冲击曲线**（X / Z 轴）：与 Y 轴反向 —— 压扁时外扩、回弹时收窄 ⇒ 看着像"体积守恒"。
         *
         * @param frame 冲击阶段帧数（0 起）
         * @param frames 冲击阶段总帧数；{@code <= 0} ⇒ 返回 1
         */
        public static double impactScaleXZ(int frame, int frames) {
            if (frames <= 0) {
                return 1d;
            }
            double t = clamp01(frame / (double) frames);
            if (t <= 0.25d) {
                double s = t / 0.25d;
                return 1d + (IMPACT_SQUASH_XZ - 1d) * (2d * s - s * s);
            }
            if (t <= 0.60d) {
                double s = (t - 0.25d) / 0.35d;
                double eased = s * (2d - s);
                return IMPACT_SQUASH_XZ + (IMPACT_BOUNCE_XZ - IMPACT_SQUASH_XZ) * eased;
            }
            double s = (t - 0.60d) / 0.40d;
            double eased = s * (2d - s);
            return IMPACT_BOUNCE_XZ + (1d - IMPACT_BOUNCE_XZ) * eased;
        }

        /**
         * **滞留期呼吸缩放**：在 {@code 1} 与 {@code 1 - }{@link #BREATHE_AMPLITUDE} 之间平滑来回。
         *
         * @param stayFrame 进入滞留后的帧数（0 起）
         * @return 缩放系数（恒 &gt; 0）
         */
        public static double breatheScale(int stayFrame) {
            if (BREATHE_PERIOD_TICKS <= 0) {
                return 1d;
            }
            // 余弦来回：0 → 最小 → 0，一个周期 = BREATHE_PERIOD_TICKS 刻
            double phase = (stayFrame % BREATHE_PERIOD_TICKS) / (double) BREATHE_PERIOD_TICKS;
            double wave = (1d - Math.cos(phase * Math.PI * 2d)) / 2d; // 0..1..0
            return 1d - BREATHE_AMPLITUDE * wave;
        }

        /**
         * **缓慢缩小进度**：{@code 1 → 0}，用 smoothstep 收尾（起步慢、结尾更慢）⇒ "缓慢消失"。
         *
         * @param frame 缩小阶段帧数（0 起）
         * @param frames 总帧数（{@link #SHRINK_OUT_FRAMES}）；{@code <= 0} ⇒ 返回 0
         */
        public static double shrinkProgress(int frame, int frames) {
            if (frames <= 0) {
                return 0d;
            }
            double t = clamp01(frame / (double) frames);
            double eased = t * t * (3d - 2d * t); // smoothstep
            return 1d - eased;
        }

        /**
         * ★ **颤抖偏移**：给某一帧算一个确定性的小抖动（格）。
         *
         * <p>用 {@code (frame, axis, seed)} 混出的确定性伪随机，而不是正弦 ——
         * 正弦看起来像"摇晃"，只有**不规则**的位移才像"发抖"。
         *
         * @param frame 当前帧
         * @param axis  轴：0=X，1=Y，2=Z
         * @param seed  种子
         * @param amplitude 抖动幅度（格）；{@code <= 0} ⇒ 返回 0
         * @return 该轴的偏移（格），落在 {@code [-amplitude, +amplitude]}
         */
        public static double trembleOffset(int frame, int axis, long seed, double amplitude) {
            if (amplitude <= 0d) {
                return 0d;
            }
            int step = TREMBLE_STEP_FRAMES <= 0 ? 1 : TREMBLE_STEP_FRAMES;
            int slot = frame / step; // 按频率量化：越大换得越慢
            double unit = unitOf(slot, axis + 11, seed); // 0..1
            return (unit * 2d - 1d) * amplitude;         // -amp..+amp
        }

        /**
         * ★ **渐变红**：愤怒语气的文字色 —— 按字下标在"亮红 → 暗红"之间插值。
         *
         * <p>越靠后越暗（像火在烧、气在升），整排读起来就是一条红色渐变。
         *
         * @param index 字下标（0 起）
         * @param count 总字数（{@code <= 1} ⇒ 恒为亮红）
         * @return ARGB 整数（alpha 恒 255，色相固定在红域）
         */
        public static int angryRedAt(int index, int count) {
            double t = count <= 1 ? 0d : clamp01(index / (double) (count - 1));
            // 亮红 (255, 72, 40) → 暗红 (128, 16, 16)
            int r = (int) Math.round(255 + (128 - 255) * t);
            int g = (int) Math.round(72 + (16 - 72) * t);
            int b = (int) Math.round(40 + (16 - 40) * t);
            return argb(255, r, g, b);
        }

        /** 打包成 Bukkit 的 ARGB 整数（避免在纯数学区引 {@code java.awt}）。 */
        public static int argb(int a, int r, int g, int b) {
            return ((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
        }

        /**
         * ★ **渐变**：把给定的基色按字下标向**暗端**插值。
         *
         * <p>用于"渐变色文字"：越靠后的字越暗（整排读起来是一条渐变带）。
         *
         * @param index 字下标
         * @param count 总字数（{@code <= 1} ⇒ 返回原色）
         * @param argb  基色（ARGB；只用低 24 位）
         * @param darken 末端的压暗比例（{@code 0..1}；0 = 不渐变，1 = 末端全黑）
         * @return 该字应显示的颜色（ARGB，alpha 恒 255）
         */
        public static int shadeToward(int index, int count, int argb, double darken) {
            double t = count <= 1 ? 0d : clamp01(index / (double) (count - 1));
            double keep = 1d - clamp01(darken) * t;
            int r = (int) Math.round(((argb >> 16) & 0xFF) * keep);
            int g = (int) Math.round(((argb >> 8) & 0xFF) * keep);
            int b = (int) Math.round((argb & 0xFF) * keep);
            return argb(255, r, g, b);
        }

        /**
         * ★ **从起点向下找第一块"站得住"的地面高度**（纯函数 ⇒ 可离线穷举单测）。
         *
         * <h2>与 Tek 落岳那套的区别</h2>
         * 落岳要的是"玩家脚下那格"，本方法要的是"**这个字**掉下去落在哪个 Y"，
         * 且必须容忍"一路到底都是空气"（虚空）与"从很高的地方往下"（高空刷屏）。
         *
         * <h2>判据</h2>
         * 从 {@code fromY - 1} 开始逐格向下，返回**第一格的 Y 坐标**（即站立面）。
         * 走到 {@code minY} 仍未命中 ⇒ 返回 {@code fallbackY}（调用方给的兜底高度，
         * 通常直接给基准点高度 ⇒ 视觉上"没掉多远"，而不是掉穿世界）。
         *
         * <h2>World 的边界</h2>
         * {@code world.getMinHeight()} 是硬底线；不查 {@code maxHeight} 是因为调用方
         * 只可能从高处往下掉。
         *
         * @param fromY     起点 Y（从这个高度的**下一格**开始往下查）
         * @param minY      最低查到哪（含）；{@code fromY <= minY} ⇒ 返回 {@code fallbackY}
         * @param fallbackY 查不到地面时的兜底高度
         * @param solid     "这一格是实心地面吗"的判据（世界查询；离线测试传纯函数桩）
         * @return 第一个实心格子的 Y
         */
        public static int groundLevelY(int fromY, int minY, int fallbackY,
                                       java.util.function.IntPredicate solid) {
            if (solid == null || fromY <= minY) {
                return fallbackY;
            }
            for (int y = fromY - 1; y >= minY; y--) {
                if (solid.test(y)) {
                    return y;
                }
            }
            return fallbackY;
        }

        /**
         * 下落过程中某一帧的 Y 坐标（纯函数）。
         *
         * @param startY  起点 Y
         * @param groundY 落点 Y
         * @param frame   已下落帧数
         * @param frames  下落总帧数
         * @return 该帧的 Y（{@code frame >= frames} ⇒ {@code groundY}）
         */
        public static double yAt(double startY, double groundY, int frame, int frames) {
            double p = fallProgress(frame, frames);
            return startY + (groundY - startY) * p;
        }

        // ───────── 米塔风格：竖直掉落（纯函数，全部可离线穷举）─────────

        /**
         * ★ 米塔风格的**水平速度**（格/刻，纯函数）。
         *
         * <p>★ 2026-10-03 改版：本样式已改为**纯竖直下落** ⇒ 水平速度恒 {@code 0}。
         * 保留这个方法是为了**旧读口 / 旧测试的兼容**（原来它返回一个散布速度）。
         *
         * @param index 字下标（忽略）
         * @param seed  段落种子（忽略）
         * @return 恒 {@code 0}
         * @deprecated 本样式不再有水平位移；仅是兼容读口，新代码不要依赖。
         */
        @Deprecated
        public static double scrollSpeedOf(int index, long seed) {
            return 0d;
        }

        /**
         * ★ 米塔风格的**某个字**的竖直初速度（格/刻，纯函数）。
         *
         * <p>正值 = 先被微微托起一点再落（让同排的字有参差，不至于像一整块板平移）。
         * ★ 改版后为了让字**尽快竖直落下去**，这个值很小（{@code 0.02~0.10}）——
         * 它只负责"参差"，不负责"抛高"。
         */
        public static double scrollLiftOf(int index, long seed) {
            double u = unitOf(Math.max(0, index), 2, seed);
            return SCROLL_LIFT_MIN + (SCROLL_LIFT_MAX - SCROLL_LIFT_MIN) * u;
        }

        /**
         * ★ **某字的横向漂移初速度**（格/刻，纯函数）。
         *
         * <p>每字不同 ⇒ 落下时朝径向的**速度**不同（有的歪得远、有的几乎原地），
         * 配合 {@link #scrollDriftAngleOf} 的方向 ⇒ 落点自然散开。
         *
         * @param index 字下标
         * @param seed  段落种子
         * @return 漂移速度（格/刻，恒 {@code >= 0}；{@code 0} 表示这个字原地落）
         */
        public static double scrollDriftOf(int index, long seed) {
            double u = unitOf(Math.max(0, index), 5, seed);
            // ★ 让一部分字几乎不漂（u 小 ⇒ 贴原竖线落下），避免"每个字都在飘"显得轻浮
            return SCROLL_DRIFT_MAX * 0.16d * u;
        }

        /**
         * ★ **某字的横向漂移方向**（弧度，纯函数，值域 {@code [0, 2π)}）。
         *
         * <p>用满圈方向 ⇒ 落点围绕原竖线散成一圈，而不是全朝同一侧偏。
         */
        public static double scrollDriftAngleOf(int index, long seed) {
            return unitOf(Math.max(0, index), 6, seed) * Math.PI * 2d;
        }

        /**
         * ★★ **某字落地躺倒的俯仰角**（度，纯函数）。
         *
         * <p>= {@link #SCROLL_LIE_PITCH} ± {@link #SCROLL_LIE_JITTER}。
         * ★ 每字**固定**（由 {@code index + seed} 决定）⇒ 落地后不再变化，
         * 既满足"每字躺得不一样"，又保证"钉住后绝不抖动"。
         */
        public static float scrollLiePitchOf(int index, long seed) {
            double u = unitOf(Math.max(0, index), 7, seed) * 2d - 1d;  // -1..1
            return SCROLL_LIE_PITCH + (float) (u * SCROLL_LIE_JITTER);
        }

        /**
         * ★★ **某字落地躺倒的偏航角**（度，纯函数）。
         *
         * <p>= ±{@link #SCROLL_LIE_YAW_JITTER}（绕 Y 轴）。
         * 让散落在地上的字**朝向各异**，而不是整排齐刷刷。
         */
        public static float scrollLieYawOf(int index, long seed) {
            double u = unitOf(Math.max(0, index), 8, seed) * 2d - 1d;  // -1..1
            return (float) (u * SCROLL_LIE_YAW_JITTER);
        }

        /**
         * ★★ **某字空中翻滚的总圈数**（纯函数）。
         *
         * <p>{@code 0..SCROLL_TUMBLE_MAX_TURNS}。配合 {@link #scrollTumblePitch}，
         * 让字在下落期间**边掉边翻**（有的翻两圈、有的不翻），比"单调转半圈"生动得多。
         */
        public static int scrollTurnsOf(int index, long seed) {
            double u = unitOf(Math.max(0, index), 9, seed);
            return (int) Math.round(u * SCROLL_TUMBLE_MAX_TURNS);
        }

        /**
         * ★ 米塔风格的**水平方向**（弧度，纯函数）。
         *
         * <p>★ 改版后水平位移已废除 ⇒ 这里恒返回 {@code 0}（保留仅为兼容旧读口 / 旧测试）。
         *
         * @param index 字下标（忽略）
         * @param seed  段落种子（忽略）
         * @deprecated 本样式不再有水平位移；仅是兼容读口。
         */
        @Deprecated
        public static double scrollAngleOf(int index, long seed) {
            return 0d;
        }

        /**
         * ★ **单字位置积分**（纯函数，一次算完 0..frame 的全部弹跳）。
         *
         * <p>模型与数据包"骑 item 实体跑原版物理"等价，但不借任何实体：
         * <pre>
         *   每刻： vy -= g
         *         y  += vy      ;  x += vx ;  z += vz
         *         若 y <= 地面： y = 地面； vy = -vy * 反弹衰减； vx,vz *= 漂移衰减
         *                       若 |vy| < 静止阈值 ⇒ vy = 0（贴地不再弹，**横向也锁死**）
         * </pre>
         * 用**迭代到 frame** 的方式实现（帧数很短，最多百来刻 ⇒ 不必闭式解，
         * 闭式解反而与"逐刻判定触地"不等价）。
         *
         * <p>★★ **横向漂移只在空中/弹跳期间发生，静止那一刻锁死**：
         * 落地后字会被 {@code Billboard.FIXED} 钉住，若此刻还在横滑，
         * 就成了"钉住了却还在平移"的矛盾动作。⇒ 静止后横向速度清零、位置冻结。
         *
         * <p>★★ **空中连续翻滚**：俯仰角 = 翻滚进度 × ({@code 圈数} × 360° + 躺倒角)，
         * 用**总下落进度**（而非瞬时速度）驱动 ⇒ 落点处正好停在躺倒角上，收尾自然不跳变。
         *
         * @param index       字下标
         * @param seed        段落种子
         * @param frame       已经过的刻数（{@code <= 0} ⇒ 返回起点）
         * @param startY      起点 Y（格，相对基准点）：字从这里开始运动
         * @param groundY     地面 Y（格，相对基准点；可以是**负数** = 地面在基准点下方）
         * @param liftVy      竖直初速度（格/刻；{@code > 0} = 先被托起一点，{@code 0} = 直接往下掉）
         * @param maxFrames   最多积分多少刻（防越界；{@code <= 0} ⇒ 按 1）
         * @return 一个 6 元组：
         *         {@code [x, z, y, pitch(度), yaw(度), restingAt(-1 = 尚未静止)]}
         *         ★ 注意顺序：**x, z 在前**，y 在第 3 位（与"水平在前"的空间直觉一致）。
         */
        public static double[] scrollTrackAt(int index, long seed, int frame,
                                             double startY, double groundY, double liftVy,
                                             int maxFrames) {
            int idx = Math.max(0, index);
            // 横向：每字一个速度 + 一个满圈方向 ⇒ 落点散成一小圈
            double driftSpeed = scrollDriftOf(idx, seed);
            double driftAngle = scrollDriftAngleOf(idx, seed);
            double vx = Math.cos(driftAngle) * driftSpeed;
            double vz = Math.sin(driftAngle) * driftSpeed;

            double vy = liftVy;
            double y = startY;
            double x = 0d;
            double z = 0d;
            int f = Math.max(0, Math.min(frame, Math.max(1, maxFrames)));
            boolean resting = false;
            int restingAt = -1;

            // 空中翻滚的进度基准：
            // ★ 必须与 y 解耦！首次触地时 y 会被夹到地面 ⇒ 若用 (startY-y)/drop 算进度，
            //   会在触地那一帧从"空中值"突跳到 1 ⇒ 姿态瞬间旋转（实测跳变 47°）。
            //   改用"理想自由落体所需刻数"作分母，进度 = 已过刻数 / 总刻数（单调、无跳变）。
            double drop = Math.max(1e-6d, startY - groundY);
            int turns = scrollTurnsOf(idx, seed);
            int fallFrames = Math.max(1, (int) Math.ceil(
                    Math.sqrt(2d * drop / Math.max(1e-6d, SCROLL_GRAVITY))));

            for (int i = 0; i < f; i++) {
                if (resting) {
                    // ★ 静止后横向也锁死（不许再滑），竖直已停
                    continue;
                }
                vy -= SCROLL_GRAVITY;
                y += vy;
                x += vx;
                z += vz;
                if (y <= groundY) {
                    y = groundY;
                    vy = -vy * SCROLL_BOUNCE_DAMPING;
                    // ★ 每碰一次地，横向被搓掉一部分 ⇒ 越弹越"收敛"，静止前已几乎不动
                    vx *= SCROLL_DRIFT_DAMPING;
                    vz *= SCROLL_DRIFT_DAMPING;
                    // 弹不动了 ⇒ 贴地静止（横向同时锁死）
                    if (Math.abs(vy) < SCROLL_REST_VELOCITY) {
                        vy = 0d;
                        vx = 0d;
                        vz = 0d;
                        resting = true;
                        restingAt = i + 1;
                    }
                }
            }

            float liePitch = scrollLiePitchOf(idx, seed);
            float lieYaw = scrollLieYawOf(idx, seed);
            double pitch;
            double yaw;
            if (resting || y <= groundY + 1e-6d) {
                // ★ 落地：用**该字自己的**躺倒角（每字不同），偏航也每字不同
                pitch = liePitch;
                yaw = lieYaw;
            } else {
                // ★ 空中：按"已经过多少刻 / 理想落地刻数"推进翻滚。
                //   ★ 分母是**估计的下落时长**、分子是**已过帧数**，两者都与 y 的截断无关
                //   ⇒ 单调推进、落地收敛、无突跳。
                double progress = Math.max(0d, Math.min(1d, f / (double) fallFrames));
                double tumble = -turns * 360d * (1d - progress);
                pitch = liePitch + tumble;
                // 偏航也随翻滚转一点，落地时收敛到该字自己的躺倒偏航
                yaw = lieYaw * progress;
            }
            return new double[]{x, z, y, pitch, yaw, restingAt};
        }

        /**
         * ★ 米塔风格**旧口径**（从 {@code groundDrop} 高处掉到 {@code y = 0} 的地面）。
         * 等价于
         * {@code scrollTrackAt(index, seed, frame, groundDrop, 0, scrollLiftOf(...), maxFrames)}。
         *
         * @return 6 元组，同 {@link #scrollTrackAt}
         */
        public static double[] scrollStateAt(int index, long seed, int frame,
                                             double groundDrop, int maxFrames) {
            return scrollTrackAt(index, seed, frame, Math.max(0d, groundDrop), 0d,
                    scrollLiftOf(index, seed), maxFrames);
        }

        /**
         * ★ **滚地阶段的总刻数上界**（纯函数，随下落高度单调不减）。
         *
         * <p>口径 = 首次下落 + 各级弹跳（等比衰减） + 静止停留 + 淡出，再留一点余量。
         * 用途有二：① {@link #totalTicksOf} 算整段时长；② 渲染侧做兜底回收判断
         * （**必须够大**，否则整段会在滚到一半时被强收）。
         *
         * @param fallDistance 下落距离（格，{@code >= 0}；{@link #MAX_FALL_HEIGHT} 封顶）
         * @return 从"开始滚"到"彻底消失"的刻数
         */
        public static int scrollRollTicksFor(double fallDistance) {
            double h = clampScrollFall(fallDistance);
            double g = Math.max(1e-6d, SCROLL_GRAVITY);
            double fall = Math.sqrt(2d * h / g);
            // 弹跳：每次速度 ×damping ⇒ 各级时长成等比，总时长 = fall * 2 * d / (1 - d)
            double bounce = fall * 2d * SCROLL_BOUNCE_DAMPING
                    / Math.max(1e-6d, 1d - SCROLL_BOUNCE_DAMPING);
            int motion = (int) Math.ceil(fall + bounce) + 12;
            return Math.max(12, motion) + SCROLL_REST_TICKS + SCROLL_FADE_FRAMES;
        }

        /**
         * ★ 米塔风格的**下落距离封顶**（格）。
         *
         * <p>与 {@link #MAX_FALL_HEIGHT} 同口径：玩家飞在几百格高空时，
         * 字不该"掉一整分钟"。★ 渲染侧与 {@link #scrollRollTicksFor} **必须用同一个封顶值**，
         * 否则"实际下落时长 > 预算时长" ⇒ 整段会被兜底逻辑提前强收（字掉到一半突然消失）。
         */
        public static double clampScrollFall(double fallDistance) {
            return Math.max(0d, Math.min(fallDistance, MAX_FALL_HEIGHT));
        }

        /**
         * ★ 米塔风格的**水平速度**（纯函数读口）。
         *
         * <p>★ 改版后本样式为**纯竖直下落** ⇒ 恒 {@code 0}（保留仅为兼容旧读口 / 旧测试）。
         *
         * @param index 字下标（忽略）
         * @param seed  段落种子（忽略）
         * @param frame 已经过的刻数（忽略）
         * @return 恒 {@code 0}
         * @deprecated 本样式不再有水平位移；仅是兼容读口。
         */
        @Deprecated
        public static double scrollVelocityX(int index, long seed, int frame) {
            return 0d;
        }

        /**
         * ★ 米塔风格的**淡出进度**（纯函数）：静止停留结束后，从 1 线性降到 0。
         *
         * <p>数据包 {@code fade/main} 每刻按 {@code #value.15} 的步长递减 {@code text_opacity}，
         * 减到 {@code 0..15} 就 {@code kill}。这里复刻成"15 帧线性淡出"。
         *
         * @param restFrame 进入静止后的刻数（{@code < 0} ⇒ 1）
         * @return 不透明度比例 {@code 1..0}
         */
        public static double scrollFadeOpacity(int restFrame) {
            int f = Math.max(0, restFrame);
            if (f >= SCROLL_FADE_FRAMES) {
                return 0d;
            }
            return 1d - f / (double) SCROLL_FADE_FRAMES;
        }

        // ───────── 变换分量 ─────────

        /**
         * 缩放分量（逐轴）。
         *
         * @param kind  出现 / 消失行为类别（决定从什么比例插值到 1）
         * @param p     进度（0..1，1 = 完全就位）
         * @param axis  轴：0=X，1=Y，2=Z；其它轴恒 1
         * @param index 字下标（随机类依赖它）
         * @param seed  随机种子
         * @return 该轴的比例（恒 {@code >= 0.01}，防止 0 缩放被客户端判为不可见后不再渲染）
         */
        public static double scale(Style.InKind kind, double p, int axis, int index, long seed) {
            double progress = clamp01(p);
            double base = switch (kind) {
                case SCALE_UP, SCALE_DOWN, SCALE_RANDOM, SCALE_UP_SLOW, WHOLE_CENTER_DIFFUSE -> progress;
                case SCALE_X_UP, SCALE_X_DOWN, SCALE_X_PULSE -> axis == 0 ? progress : 1d;
                case SCALE_Y_UP, SCALE_Y_DOWN, SCALE_Y_PULSE -> axis == 1 ? progress : 1d;
                // 未声明缩放行为的样式 ⇒ 恒 1（不缩放）
                default -> 1d;
            };
            if (kind == Style.InKind.SCALE_RANDOM) {
                // 随机缩放：每轴一个 0.35..1 的偏置，随进度收敛到 1
                double jitter = 0.35d + 0.65d * unitOf(index, axis, seed);
                base = progress + (1d - progress) * jitter * (1d - progress);
            }
            // Z 轴在"仅 X / 仅 Y"样式里必须是 1（否则薄片状）
            if (axis == 2 && kind != Style.InKind.SCALE_UP
                    && kind != Style.InKind.SCALE_DOWN
                    && kind != Style.InKind.SCALE_RANDOM
                    && kind != Style.InKind.SCALE_UP_SLOW
                    && kind != Style.InKind.WHOLE_CENTER_DIFFUSE) {
                return 1d;
            }
            return Math.max(0.01d, base);
        }

        /**
         * 位移分量（单位：格）。
         * <p>数据包的对应做法是给展示实体设 {@code transformation.translation}：
         * 例如 style=11 的 {@code specific} 直接写 {@code [0.0, 1.5, 0.0]}。
         *
         * @param kind  出现行为类别
         * @param p     进度（0..1，1 = 就位）
         * @param axis  轴：0=X，1=Y，2=Z
         * @param index 字下标（随机类依赖它）
         * @param seed  随机种子
         * @return 该轴偏移（格）
         */
        public static double translation(Style.InKind kind, double p, int axis, int index, long seed) {
            double progress = clamp01(p);
            double remaining = 1d - progress; // 1 = 尚未就位，0 = 已就位

            return switch (kind) {
                // 逐字下落：从上方 1.5 格落到位（数据包 style=11 的 1.5）
                case TRANSLATE_DOWN -> axis == 1 ? 1.5d * remaining : 0d;
                // 逐字上浮：从下方 1.5 格浮上来
                case TRANSLATE_UP -> axis == 1 ? -1.5d * remaining : 0d;
                // 随机上下：方向由种子定，幅度 1.5 格
                case TRANSLATE_RANDOM_V -> axis == 1
                        ? (unitOf(index, 0, seed) < 0.5d ? 1.5d : -1.5d) * remaining
                        : 0d;
                // 前进：沿 Z 正向滑入
                case TRANSLATE_FORWARD -> axis == 2 ? 1.2d * remaining : 0d;
                // 左滑：沿 X 负向滑入
                case TRANSLATE_LEFT -> axis == 0 ? -1.2d * remaining : 0d;
                // 整行下滑（Special Texts slidedown）：幅度更大、整行同起
                case WHOLE_SLIDE_DOWN -> axis == 1 ? 2.0d * remaining : 0d;
                // 中心扩散：沿 X 由中心向外展开（数据包 center_diffuse 的视感）
                case CENTER_DIFFUSE_FADE, WHOLE_CENTER_DIFFUSE -> 0d;
                // ★ 落地砸字：竖直位移由渲染层按"起点/落点"直接算（见 yAt），不在这里给偏移
                case GROUND_DROP -> 0d;
                default -> 0d;
            };
        }

        /**
         * 旋转分量（单位：度，绕指定轴）。
         * <p>翻转类入位角度为 90°，旋转类入位角度为 180°（两种观感在数据包里是分开的
         * {@code transformation.left_rotation} 取值）。
         *
         * @param kind  出现行为类别
         * @param p     进度（0..1）
         * @param axis  轴：0=X，1=Y，2=Z
         * @param index 字下标
         * @param seed  随机种子
         * @return 该轴角度（度）
         */
        public static double rotationDegrees(Style.InKind kind, double p, int axis, int index, long seed) {
            double remaining = 1d - clamp01(p);
            return switch (kind) {
                case ROTATE_Y_NEG -> axis == 1 ? -90d * remaining : 0d;
                case ROTATE_Y_POS -> axis == 1 ? 90d * remaining : 0d;
                case ROTATE_Y_RANDOM -> axis == 1
                        ? (unitOf(index, 0, seed) < 0.5d ? -90d : 90d) * remaining
                        : 0d;
                case ROTATE_X_NEG -> axis == 0 ? -90d * remaining : 0d;
                case ROTATE_X_POS -> axis == 0 ? 90d * remaining : 0d;
                case ROTATE_X_RANDOM -> axis == 0
                        ? (unitOf(index, 0, seed) < 0.5d ? -90d : 90d) * remaining
                        : 0d;
                case ROTATE_Z_NEG -> axis == 2 ? -180d * remaining : 0d;
                case ROTATE_Z_POS -> axis == 2 ? 180d * remaining : 0d;
                case ROTATE_Z_RANDOM -> axis == 2
                        ? (unitOf(index, 0, seed) < 0.5d ? -180d : 180d) * remaining
                        : 0d;
                default -> 0d;
            };
        }

        /**
         * 透明度（0..255 整数，与展示实体的 {@code text_opacity} 同域）。
         *
         * @param progress 0..1（1 = 完全不透明）
         */
        public static int opacity(double progress) {
            int value = (int) Math.round(clamp01(progress) * 255d);
            return Math.max(0, Math.min(255, value));
        }

        // ───────── 工具 ─────────

        /** 把任意值夹到 {@code [0,1]}；NaN ⇒ 0。 */
        public static double clamp01(double value) {
            if (Double.isNaN(value)) {
                return 0d;
            }
            if (value < 0d) {
                return 0d;
            }
            if (value > 1d) {
                return 1d;
            }
            return value;
        }

        /** 由 {@code (index, axis, seed)} 得到的确定性 0..1 伪随机（可复现、无全局状态）。 */
        public static double unitOf(int index, int axis, long seed) {
            int h = mix(index * 31 + axis, seed);
            return (h & 0x7FFFFFFFL) / (double) 0x7FFFFFFFL;
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  三、实体载体层（Bukkit 边界）
    // ═══════════════════════════════════════════════════════════════════

    /** 单段文本的字数上限（防呆：数据包原话"每个文字一个展示实体容易造成大量卡顿"）。 */
    public static final int MAX_CHARS = 24;

    /** 单字展示实体的字距（数据包 {@code calc_pos}：字距 = 0.228 * scale）。 */
    public static final double CHAR_PITCH = Anim.CHAR_PITCH_BASE;

    /** 展示实体可见距离。 */
    private static final float VIEW_RANGE = 8f;

    /**
     * ★ **文字外观配置**（颜色 / 渐变 / 泛光 / 颤抖 / 加粗）—— 把"长什么样"从"怎么动"里拆出来。
     *
     * <p>用不可变 record 表达，好处是{@link #spawn} 的调用方可以只传关心的那几项
     * （{@link #PLAIN} 是默认观感），不必被一堆 boolean 参数淹没。
     *
     * @param colorArgb  ★ 文字颜色（ARGB 整数；{@code 0} = 不改色 ⇒ 原版白）
     * @param gradient   ★ 是否按**字下标**做渐变（颜色从 {@code colorArgb} 向暗端过渡）
     * @param glow       ★ 是否**泛光**（原版 {@code has_glowing_effect} 式全字发光）
     * @param glowArgb   泛光/描边的颜色（ARGB；{@code 0} = 用 {@code colorArgb}）
     * @param tremble    ★ 是否**颤抖**（局部坐标逐帧抖动）
     * @param bold       ★ 是否**加粗**（{@code TextDecoration.BOLD}）。
     *                   泛光会顺带加粗（Java 版没有真发光 ⇒ 用"极亮 + 粗"近似），
     *                   本字段让**不泛光**的字也能单独加粗（"改得更明显一点"的场景）。
     */
    public record Look(int colorArgb, boolean gradient, boolean glow, int glowArgb,
                       boolean tremble, boolean bold) {

        /** 默认观感：不改色、不泛光、不颤抖、不加粗（等同于旧行为 ⇒ 老调用方零影响）。 */
        public static final Look PLAIN = new Look(0, false, false, 0, false, false);

        /** ★ 愤怒：渐变红 + 颤抖。 */
        public static final Look ANGRY = new Look(0, false, false, 0, true, false);

        /** ★ 神性：白色泛光。 */
        public static final Look HOLY = new Look(0, false, true, 0, false, false);

        /** ★ 愤怒加强版：颤抖 + 加粗（"改得更加明显点"）。 */
        public static final Look ANGRY_BOLD = new Look(0, false, false, 0, true, true);

        /** ★ 神性加强版：泛光 + 加粗（泛光本身已含加粗，这里显式声明以读起来清楚）。 */
        public static final Look HOLY_BOLD = new Look(0, false, true, 0, false, true);

        /** 只泛光，指定颜色。 */
        public static Look glowing(int argb) {
            return new Look(argb, false, true, 0, false, false);
        }

        /** {@code glowing} 的同义名（读起来更像"给某个色加泛光"）。 */
        public static Look glowOf(int argb) {
            return glowing(argb);
        }

        /** 指定颜色（无渐变、无泛光、无颤抖、不加粗）。 */
        public static Look colored(int argb) {
            return new Look(argb, false, false, 0, false, false);
        }

        /** 指定颜色 + 渐变。 */
        public static Look gradient(int argb) {
            return new Look(argb, true, false, 0, false, false);
        }

        /** {@code gradient} 的同义名。 */
        public static Look gradientOf(int argb) {
            return gradient(argb);
        }

        /** **兼容构造**（旧 5 参形态 ⇒ {@code bold=false}）—— 让既有调用点零改动。 */
        public Look(int colorArgb, boolean gradient, boolean glow, int glowArgb, boolean tremble) {
            this(colorArgb, gradient, glow, glowArgb, tremble, false);
        }

        /** 在本观感上追加"颤抖"。 */
        public Look withTremble() {
            return new Look(colorArgb, gradient, glow, glowArgb, true, bold);
        }

        /** 在本观感上追加"泛光"。 */
        public Look withGlow() {
            return new Look(colorArgb, gradient, true, glowArgb, tremble, bold);
        }

        /** 在本观感上追加"渐变"。 */
        public Look withGradient() {
            return new Look(colorArgb, true, glow, glowArgb, tremble, bold);
        }

        /** 在本观感上追加"加粗"。 */
        public Look withBold() {
            return new Look(colorArgb, gradient, glow, glowArgb, tremble, true);
        }

        /** 在本观感上换颜色。 */
        public Look withColor(int argb) {
            return new Look(argb, gradient, glow, glowArgb, tremble, bold);
        }

        /** 有效泛光色（没单独给就用文字色）。 */
        public int effectiveGlowArgb() {
            return glowArgb != 0 ? glowArgb : colorArgb;
        }
    }

    /**
     * 一段正在播放的悬浮文本（**实体载体**）。
     *
     * <h2>它替代了数据包的什么</h2>
     * 数据包播一段世界文本要生成「1 个 center 基准 + N 个字 + 1 个 marker」三类实体，
     * 再用标签（{@code cgt_anim_text.positional.animation.text} 等）与记分项把它们串起来，
     * 每刻靠 {@code execute as @e[tag=...]} 反查。本类改成**直接持有句柄**：
     * 一个 Instance 对象握住自己的全部字实体，逐刻就地更新，
     * 不需要任何标签、记分板或 storage 中转。
     *
     * <h2>关键观感取舍</h2>
     * <ul>
     *   <li>数据包逐字生成实体并用 {@code execute positioned ^0.23 ^ ^} 偏移，
     *       因此**字距固定 0.228 格**；本类同样逐字一实体（保留逐字错峰能力）；</li>
     *   <li>{@link Display#setInterpolationDuration(int)} 必须设 0 —— 数据包对
     *       animation 路径就是 {@code interpolation_duration set value 0}（不要客户端插值，
     *       否则每刻写入会被平滑掉，"逐字错峰"看起来像整体漂移）；</li>
     *   <li>{@code background} 默认透明（{@code setDefaultBackground(false)} +
     *       {@code setBackgroundColor(Color.fromARGB(0,0,0,0))}），与数据包
     *       {@code background:0} 口径一致；</li>
     *   <li>{@code seeThrough} 打开 ⇒ 不遮挡视线（数据包 {@code see_through} 语义）。</li>
     * </ul>
     *
     * <h2>边界</h2>
     * 本类只管"实体的生与死 + 每刻写一次变换"，**不决定**文本内容、不读玩家状态、
     * 不注册任务。玩家位置与随机摆放由上层组件决定。
     *
     * <h2>线程</h2>
     * 全部方法必须主线程调用（生成 / 传送实体）。调用方（组件 {@code update()}）本就在主线程。
     */
    public static final class Instance {

        private final String text;
        private final Style style;
        private final long seed;
        private final int speed;
        private final int stayTicks;
        private final float scale;
        private final int inFrames;

        /** 逐字错峰：每个字的启动刻（顺序 / 中心 / 随机三种，构造时一次算好，不再逐帧重算）。 */
        private final int[] startTicks;

        /**
         * ★ 落地砸字的**下落距离**（相对基准点的偏移，格；{@code >= 0}）。
         *
         * <p>语义 = "这个字要从基准点上方多少格开始往下掉" ⇒ 也是实体生成时的抬高量。
         * <p>只有 {@link Style#GROUND_SMASH} 会用到；其余样式恒 0。
         * <p>若调用方走普通 {@link TextUtil#spawn}（没给地面信息）⇒ 保持 0
         * ⇒ 该字"原地压扁回弹 + 缩小消失"，不会掉到世界底部。
         */
        private final float groundDrop;

        /**
         * ★ 落地砸字的**每字落点高度**（相对基准点的最终偏移，格；可达负值）。
         *
         * <p>与 {@link #groundDrop} 的区别：{@code groundDrop} 是"从哪儿开始掉"，
         * 本字段是"掉到哪儿". 两个都要有 —— 少了它就会把"基准点"误当"地面"
         * （症状 = 字停在半空、或反向上升）。
         * <p>空数组 ⇒ 全部用 0（落回基准点）；
         * {@code null} ⇒ 该段不走"落地"逐字路径。
         * <p>★ {@link Style#MITA_SCROLL} **只用本字段**（生成不抬高，消失时才落到这儿）。
         */
        private final float[] groundRestPerChar;

        /** ★ 文字外观（颜色 / 泛光 / 颤抖）。 */
        private final Look look;

        private final List<TextDisplay> chars = new ArrayList<>();

        /** 已运行刻数（每刻 +1）。 */
        private int tick;

        /** 本段是否已播完（播完后组件应调 {@link #retire(Instance)}）。 */
        private boolean finished;

        private Instance(String text, Style style, long seed, int speed,
                         int stayTicks, float scale, int inFrames,
                         int[] startTicks, float groundDrop, float[] groundRestPerChar,
                         Look look) {
            this.text = text;
            this.style = style;
            this.seed = seed;
            this.speed = speed;
            this.stayTicks = stayTicks;
            this.scale = scale;
            this.inFrames = inFrames;
            this.startTicks = startTicks;
            this.groundDrop = groundDrop;
            this.groundRestPerChar = groundRestPerChar;
            this.look = look == null ? Look.PLAIN : look;
        }

        /** 本段使用的外观配置。 */
        public Look look() {
            return look;
        }

        public int charCount() {
            return chars.size();
        }

        public boolean isFinished() {
            return finished;
        }

        /** 本段有多少个存活实体（供调用方做总量封顶）。 */
        public int entityCount() {
            return chars.size();
        }

        /** 本段总时长（刻）：从生成到最后一个字彻底消失。 */
        public int totalTicks() {
            return Anim.totalTicksOf(style, chars.size(), speed, stayTicks, maxFallDistance());
        }

        /**
         * ★ 本段**实际最大下落距离**（格）。
         *
         * <p>= 「抬高量 + 落点相对基准点的偏移」，取逐字最大值。
         * 落地砸字按它算下落帧数；其余样式恒 0。
         */
        private float maxFallDistance() {
            if (style == Style.MITA_SCROLL) {
                // 米塔风格：生成时在基准点（不抬高），消失时**从基准点落到地面**
                // ⇒ 下落距离 = 逐字落点偏移的绝对值（地面在基准点下方 ⇒ rest 为负）
                // ★ 用 clampScrollFall 封顶，与 scrollRollTicksFor 同口径（否则会被提前强收）
                float max = 0f;
                if (groundRestPerChar != null) {
                    for (int i = 0; i < groundRestPerChar.length; i++) {
                        max = Math.max(max, Math.abs(groundRestPerChar[i]));
                    }
                }
                return (float) Anim.clampScrollFall(max);
            }
            if (style != Style.GROUND_SMASH) {
                return 0f;
            }
            float max = Math.max(0f, groundDrop);
            if (groundRestPerChar != null) {
                for (int i = 0; i < groundRestPerChar.length; i++) {
                    // 该字的下落距离 = 抬高量 + 落点偏移（落点常在基准点下方 ⇒ rest 为负）
                    max = Math.max(max, groundDrop + groundRestPerChar[i]);
                }
            }
            return Math.max(0f, max);
        }
    }

    /**
     * 在指定位置生成一段悬浮文本（**主线程**）。
     *
     * @param origin  基准位置（文本行的起点，不是中心）；{@code null} 或其世界为 {@code null} ⇒ 返回 {@code null}
     * @param facing  文本朝向（度，MC 口径：{@code yaw}）；行方向沿此朝向的右手边铺开
     * @param rawText 文本（会被逐字拆分；空 / 全空白 ⇒ 返回 {@code null}）
     * @param style   风格；{@code null} ⇒ {@link Style#PLAIN_FADE}
     * @param speed   数据包口径的 speed（推荐 80~160）
     * @param stay    滞留刻数（{@code < 0} ⇒ 默认 {@value Anim#DEFAULT_STAY_TICKS}）
     * @param scale   整体缩放（{@code <= 0} ⇒ 按 1.0）
     * @return 活体句柄；失败（无世界 / 无文本 / 生成异常）⇒ {@code null}
     */
    public static Instance spawn(Location origin, float facing, String rawText,
                                 Style style, int speed, int stay, float scale) {
        return spawnInternal(origin, facing, rawText, style, speed, stay, scale, 0f,
                null, null, Look.PLAIN);
    }

    /**
     * 带外观配置的生成（颜色 / 泛光 / 颤抖）。
     *
     * @param look 外观；{@code null} ⇒ {@link Look#PLAIN}
     */
    public static Instance spawn(Location origin, float facing, String rawText,
                                 Style style, int speed, int stay, float scale, Look look) {
        return spawnInternal(origin, facing, rawText, style, speed, stay, scale, 0f,
                null, null, look);
    }

    /**
     * ★ **落地砸字专用**：在指定位置生成一段会**掉到真实地面**的悬浮文本（**主线程**）。
     *
     * <h2>与 {@link #spawn} 的唯一差别</h2>
     * 本方法会为**每个字**向下查一次地面（经 {@link Anim#groundLevelY}），
     * 于是每个字有各自的落点 —— 站在台阶上、斜坡上吐字时，字会各自掉到各自的地面，
     * 而不是整排字掉到同一个高度。
     *
     * <h2>地面怎么查</h2>
     * 从 `origin.y`（起点）向下逐格问 `world.getBlockAt(x, y, z).isSolid()`，
     * 直到世界最低高度。查不到（虚空）⇒ 该字退化到基准点高度（不会掉穿世界）。
     * 下落高度以 {@link Anim#MAX_FALL_HEIGHT} 封顶。
     *
     * <h2>线程</h2>
     * 必须主线程调用（要读方块）。本方法比 {@link #spawn} 多 N 次方块查询
     * ⇒ **只给"要落地"的样式用**（{@link Style#GROUND_SMASH} / {@link Style#MITA_SCROLL}），
     * 别给每次空闲氛围字都挂上。
     *
     * @param groundOffset 高度（格）：{@link Style#GROUND_SMASH} 是"起点相对基准点抬高多少"
     *                     （从 {@code origin.y + groundOffset} 往下掉）；{@link Style#MITA_SCROLL}
     *                     生成时**不抬高**（正常显示），此值只用来决定"从多高开始往下探地面"。
     *                     给 4~6 效果较明显；{@code <= 0} ⇒ 按 4 算。
     * @return 活体句柄；失败 ⇒ {@code null}
     */
    public static Instance spawnOnGround(Location origin, float facing, String rawText,
                                         Style style, int speed, int stay, float scale,
                                         double groundOffset) {
        return spawnOnGround(origin, facing, rawText, style, speed, stay, scale, groundOffset, Look.PLAIN);
    }

    /** 落地砸字 + 外观配置。 */
    public static Instance spawnOnGround(Location origin, float facing, String rawText,
                                         Style style, int speed, int stay, float scale,
                                         double groundOffset, Look look) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        World world = origin.getWorld();
        double offset = groundOffset <= 0d ? 4d : groundOffset;
        // 固定成「从上方开始掉」，且高度受封顶约束
        double capped = Math.min(offset, Anim.MAX_FALL_HEIGHT);

        // ★ 生成高度：只有"落地砸字"才抬高（字从空中掉下来）；
        //   米塔滚地是"生成时正常、消失时滚地" ⇒ 生成位置 = 基准点本身。
        boolean raisedSpawn = style == Style.GROUND_SMASH;
        double spawnY = raisedSpawn ? origin.getY() + capped : origin.getY();
        int startY = (int) Math.floor(spawnY);
        int minY = world.getMinHeight();

        // 逐字查地面：字的 X/Z 各不相同 ⇒ 落点可以不同（台阶 / 斜坡上更自然）
        String[] probe = splitGlyphs(rawText == null ? "" : rawText);
        int count = Math.min(Math.max(1, probe.length), MAX_CHARS);
        float[] drops = new float[count];
        float[] rests = new float[count];
        for (int i = 0; i < count; i++) {
            // 字沿行方向铺开，X/Z 与 origin 有偏移；这里用原点水平位置查（够用且便宜）
            int bx = (int) Math.floor(origin.getX());
            int bz = (int) Math.floor(origin.getZ());
            // 从 startY 往下找第一格实心；查不到（虚空）⇒ 兜底 = startY（原地掉一点点）
            int floorY = Anim.groundLevelY(startY, minY, startY,
                    y -> world.getBlockAt(bx, y, bz).isSolid());
            // 站立面 = 实心格顶面 ⇒ 字应停在该格上方 1 格
            double restAbs = (floorY == startY) ? (origin.getY()) : (floorY + 1d);
            // ★ 最终偏移（相对基准点）与下落距离
            rests[i] = (float) (restAbs - origin.getY());
            drops[i] = (float) Math.max(0d, spawnY - restAbs);
        }
        return spawnInternal(origin, facing, rawText, style, speed, stay, scale,
                (float) capped, drops, rests, look);
    }

    /** spawn / spawnOnGround 的公共实现（内部）。 */
    private static Instance spawnInternal(Location origin, float facing, String rawText,
                                          Style style, int speed, int stay, float scale,
                                          float groundOffset, float[] groundDrops,
                                          float[] groundRests, Look look) {
        if (origin == null || origin.getWorld() == null) {
            return null;
        }
        World world = origin.getWorld();
        String text = rawText == null ? "" : rawText;
        if (text.isBlank()) {
            return null;
        }
        String[] glyphs = splitGlyphs(text);
        if (glyphs.length == 0) {
            return null;
        }
        if (glyphs.length > MAX_CHARS) {
            // 超限不是异常：截断（调用方本应控制文案长度，这里兜底防卡顿）
            String[] trimmed = new String[MAX_CHARS];
            System.arraycopy(glyphs, 0, trimmed, 0, MAX_CHARS);
            glyphs = trimmed;
        }

        Style effStyle = style == null ? Style.PLAIN_FADE : style;
        int effSpeed = speed <= 0 ? Anim.SPEED_BASE : speed;
        int effStay = stay < 0 ? Anim.DEFAULT_STAY_TICKS : stay;
        float effScale = scale <= 0f ? 1f : scale;
        int inFrames = inFramesOf(effStyle);

        // 逐字启动刻一次算好（渲染时不再逐帧重算 ⇒ 快也更好测）
        long seed = text.hashCode() * 31L + glyphs.length;
        int[] starts = new int[glyphs.length];
        for (int i = 0; i < glyphs.length; i++) {
            starts[i] = Anim.startTickOf(i, glyphs.length, effStyle, effSpeed, seed);
        }

        float drop = effStyle == Style.GROUND_SMASH ? groundOffset : 0f;
        // ★ 米塔滚地：生成时不动（drop = 0），但**保留逐字地面落点** ⇒ 消失阶段据此滚下去
        float[] rests = (effStyle == Style.GROUND_SMASH || effStyle == Style.MITA_SCROLL)
                ? groundRests : null;
        Look effLook = look == null ? Look.PLAIN : look;

        Instance instance = new Instance(text, effStyle, seed, effSpeed, effStay, effScale,
                inFrames, starts, drop, rests, effLook);

        // 行首位置：把整行居中到 origin —— 数据包用 pos 向左偏移 (N-1)/2 格
        double pitch = CHAR_PITCH * effScale;
        double centerOffset = (glyphs.length - 1) / 2d * pitch;
        double yawRad = Math.toRadians(facing);
        // MC 口径：yaw=0 指向 +Z；右手边（行方向）是 yaw 顺时针 90°，即 (+X 方向转 yaw)
        double rightX = Math.cos(yawRad);
        double rightZ = -Math.sin(yawRad);

        try {
            // lambda 只能捕获 effectively-final 变量 ⇒ 先把长度取成局部常量
            final int glyphCount = glyphs.length;
            for (int i = 0; i < glyphCount; i++) {
                // 落地砸字：起点抬高 offset（浮在空中往下掉）
                double spawnY = drop > 0f ? drop : 0d;
                Location at = origin.clone().add(
                        rightX * (i * pitch - centerOffset),
                        spawnY,
                        rightZ * (i * pitch - centerOffset));
                // lambda 只能捕获 effectively-final ⇒ 取一份局部副本
                final String glyph = glyphs[i];
                final int glyphIndex = i;

                TextDisplay display = world.spawn(at, TextDisplay.class, entity -> {
                    applyTextAppearance(entity, glyph, glyphIndex, glyphCount, effLook);
                    // ★ 生成时一律 CENTER（面向玩家视线）；米塔风格落地后由 pinToGround 切成 FIXED
                    entity.setBillboard(billboardFor(false));
                    entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                    entity.setDefaultBackground(false);
                    entity.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                    entity.setSeeThrough(true);
                    entity.setShadowed(false);
                    entity.setViewRange(VIEW_RANGE);
                    // 不要客户端插值：每刻由本类写精确值（数据包 animation 路径同为 0）
                    entity.setInterpolationDuration(0);
                    entity.setTeleportDuration(0);
                    // 逐字透明度初始为 0（就位过程中抬到 255）
                    entity.setTextOpacity((byte) 0);
                });
                // 朝向 = 传入的 yaw（展示实体自身朝向只影响它作为父级的局部坐标系）
                display.setRotation(facing, 0f);
                instance.chars.add(display);
            }
        } catch (Throwable failure) {
            // 生成途中有任何一个失败（实体上限 / 世界卸载）⇒ 回收已生成的，返回 null
            retire(instance);
            return null;
        }
        return instance;
    }

    /**
     * ★ **把外观写进一个字实体**（颜色 / 渐变 / 泛光 / 加粗）。
     *
     * <p>三层叠加，顺序固定「先定色、再渐变、最后泛光 / 加粗」：
     * <ol>
     *   <li><b>定色</b>：{@code colorArgb != 0} ⇒ 用它；</li>
     *   <li><b>渐变</b>：{@code gradient} ⇒ 按字下标在 {@code colorArgb} 与它的暗端之间插值
     *       （整排读起来是一条渐变）；</li>
     *   <li><b>泛光</b>：{@code glow} ⇒ 换成极亮色 + 加粗，客户端渲染出的观感即"泛光"；</li>
     *   <li><b>加粗</b>：{@code bold} ⇒ 加 {@code TextDecoration.BOLD}（泛光之外的独立加粗，
     *       用来"把字改得更明显"）。</li>
     * </ol>
     *
     * <p>★ <b>为什么用"极亮 + 加粗"而不是真正的发光</b>：Java 版的 {@code text_display} 没有
     * 独立的发光开关（那是基岩版的 {@code has_glowing_effect}）。Java 版要"发光感"，
     * 通行做法就是把色拉到极亮并加粗 —— 观感上等价于"泛光白字"。
     *
     * <p>★ <b>加粗必须"先 color 后 decoration"</b>：Adventure 的 {@code Component} 是不可变的，
     * 每次 {@code color(...)} / {@code decoration(...)} 都返回新对象 ⇒ 链式写法必须把返回值接住。
     * 且 <b>BOLD 对 {@code text_display} 的观感 = 笔画变粗</b>（不是加描边），
     * 这正是"改得更加明显"要的效果。
     */
    private static void applyTextAppearance(TextDisplay display, String glyph,
                                            int index, int count, Look look) {
        int rgb = 0xFFFFFF; // 默认白

        if (look.gradient()) {
            // 渐变的基准色：给了 colorArgb 就从它渐到暗端，否则用内置"愤怒红"
            rgb = look.colorArgb() != 0
                    ? Anim.shadeToward(index, count, look.colorArgb(), 0.45d)
                    : Anim.angryRedAt(index, count);
        } else if (look.colorArgb() != 0) {
            rgb = look.colorArgb();
        }

        net.kyori.adventure.text.Component charComponent =
                net.kyori.adventure.text.Component.text(glyph)
                        .color(net.kyori.adventure.text.format.TextColor.color(rgb & 0xFFFFFF));

        if (look.glow()) {
            // ★ 泛光：拉满亮度 + 加粗 ⇒ 客户端观感是"发光白字"
            int glowRgb = look.effectiveGlowArgb() != 0
                    ? (look.effectiveGlowArgb() & 0xFFFFFF)
                    : 0xFFFFFF;
            charComponent = charComponent
                    .color(net.kyori.adventure.text.format.TextColor.color(glowRgb))
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);
        } else if (look.bold()) {
            // ★ 独立加粗（不泛光也要粗）
            charComponent = charComponent
                    .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true);
        }
        display.text(charComponent);
    }

    /**
     * 推进一帧（**主线程**，每 tick 调一次）。
     *
     * @return {@code true} = 本段仍在播放；{@code false} = 已播完（调用方应 retire 并移除）
     */
    public static boolean advance(Instance instance) {
        if (instance == null) {
            return false;
        }
        if (instance.finished) {
            return false;
        }
        if (instance.chars.isEmpty()) {
            instance.finished = true;
            return false;
        }

        Style style = instance.style;
        int count = instance.chars.size();
        int tick = instance.tick;
        int inFrames = instance.inFrames;

        boolean anyAlive = false;
        for (int i = 0; i < count; i++) {
            TextDisplay display = instance.chars.get(i);
            if (display == null || !display.isValid()) {
                continue;
            }
            int start = i < instance.startTicks.length ? instance.startTicks[i] : i;
            int local = tick - start;

            if (local < 0) {
                // 还没轮到这个字：保持完全透明（数据包的"透明度 0 隐形实体"）
                display.setTextOpacity((byte) 0);
                anyAlive = true;
                continue;
            }

            if (style == Style.GROUND_SMASH) {
                if (advanceGroundSmash(display, instance, i, local)) {
                    anyAlive = true;
                }
                continue;
            }

            if (style == Style.MITA_SCROLL) {
                if (advanceMitaScroll(display, instance, i, local)) {
                    anyAlive = true;
                }
                continue;
            }

            double opacity;
            if (local < inFrames) {
                opacity = inProgressOpacity(style, local, inFrames, i, count, instance.seed);
            } else if (local < inFrames + instance.stayTicks) {
                opacity = 1d;
            } else {
                int outFrame = local - inFrames - instance.stayTicks;
                if (style.out() == Style.OutKind.SHRINK_AWAY) {
                    // ★ 缩小消失：不是淡出，而是缩到 0（到点后已无体积）
                    double sp = Anim.shrinkProgress(outFrame, Anim.SHRINK_OUT_FRAMES);
                    applyFrame(display, instance, local, inFrames, i, count,
                            sp, instance.scale);
                    if (outFrame < Anim.SHRINK_OUT_FRAMES) {
                        anyAlive = true;
                    }
                    continue;
                }
                if (outFrame >= Anim.OUT_FRAMES) {
                    // 这个字已彻底消失：不杀掉（整段统一回收），只置透明
                    display.setTextOpacity((byte) 0);
                    continue;
                }
                opacity = Anim.outProgress(outFrame, Anim.OUT_FRAMES);
            }

            applyFrame(display, instance, local, inFrames, i, count,
                    opacity, instance.scale);
            anyAlive = true;
        }

        instance.tick = tick + 1;
        if (!anyAlive || tick > instance.totalTicks()) {
            instance.finished = true;
        }
        return !instance.finished;
    }

    /**
     * ★ **落地砸字**的单字推进（三阶段）。
     *
     * <pre>
     *   阶段 1 落下   [0, fallFrames)                     受重力加速坠向地面
     *   阶段 2 落地   [fallFrames, +IMPACT_FRAMES)        压扁 → 回弹 → 复原
     *   阶段 3 滞留   [.., +stayTicks)                    原地"呼吸"（微小缩放往复）
     *   阶段 4 消失   [.., +SHRINK_OUT_FRAMES)            缓慢缩小到 0
     * </pre>
     *
     * <p>已放弃的帧（阶段 4 走完）只置透明、不删实体 —— 整段统一由 {@link #retire(Instance)} 回收，
     * 避免同一段里的字"先后离场"时把父帧序算乱。
     *
     * @return {@code true} = 这个字还在（整段仍需推进）
     */
    private static boolean advanceGroundSmash(TextDisplay display, Instance instance, int index, int local) {
        // 该字的起落：groundDrop = 从哪儿开始掉（抬高量），rest = 掉到哪儿（最终偏移）
        float drop = instance.groundDrop;
        float rest = 0f;
        if (instance.groundRestPerChar != null && index < instance.groundRestPerChar.length) {
            rest = instance.groundRestPerChar[index];
        }
        int fallFrames = Anim.fallFramesFor(rest + drop);

        // ── 阶段 1：下落（从 drop 落到 rest）──
        if (local < fallFrames) {
            double y = Anim.yAt(drop, rest, local, fallFrames);
            applyGroundFrame(display, instance, index, 1d, 1d, (float) y);
            return true;
        }

        int afterFall = local - fallFrames;

        // ── 阶段 2：压扁回弹 ──
        if (afterFall < Anim.IMPACT_FRAMES) {
            double sy = Anim.impactScaleY(afterFall, Anim.IMPACT_FRAMES);
            double sxz = Anim.impactScaleXZ(afterFall, Anim.IMPACT_FRAMES);
            applyGroundFrame(display, instance, index, sy, sxz, rest);
            return true;
        }

        int afterImpact = afterFall - Anim.IMPACT_FRAMES;

        // ── 阶段 3：呼吸滞留 ──
        if (afterImpact < instance.stayTicks) {
            double breathe = Anim.breatheScale(afterImpact);
            applyGroundFrame(display, instance, index, breathe, breathe, rest);
            return true;
        }

        int afterStay = afterImpact - instance.stayTicks;

        // ── 阶段 4：缓慢缩小消失 ──
        if (afterStay < Anim.SHRINK_OUT_FRAMES) {
            double sp = Anim.shrinkProgress(afterStay, Anim.SHRINK_OUT_FRAMES);
            applyGroundFrame(display, instance, index, sp, sp, rest);
            return true;
        }
        display.setTextOpacity((byte) 0);
        return false;
    }

    /** 落地砸字的单帧写入：{@code sy}/{@code sxz} 是冲击/呼吸缩放，{@code yOffset} 是竖直偏移。 */
    private static void applyGroundFrame(TextDisplay display, Instance instance,
                                         int index, double sy, double sxz, float yOffset) {
        float base = instance.scale;
        Vector3f scaleVec = new Vector3f(
                (float) (sxz * base),
                (float) (sy * base),
                (float) (sxz * base));

        float tx = 0f;
        float tz = 0f;
        // ★ 颤抖：落地的字也能抖（"气得发抖"正好要这个叠加）
        if (instance.look.tremble()) {
            int frame = instance.tick;
            double amp = Anim.TREMBLE_AMPLITUDE * base;
            long perCharSeed = instance.seed + index * 7919L;
            tx = (float) Anim.trembleOffset(frame, 0, perCharSeed, amp);
            tz = (float) Anim.trembleOffset(frame, 2, perCharSeed, amp);
        }

        display.setTransformation(new Transformation(
                new Vector3f(tx, yOffset, tz),
                new Quaternionf(),
                scaleVec,
                new Quaternionf()));
        display.setTextOpacity((byte) 255);
    }

    /**
     * ★★ **米塔风格 / 字体掉落**的单字推进（移植自数据包 {@code operation/drop} 的物理）。
     *
     * <pre>
     *   阶段 1 正常显示 [0, inFrames + stayTicks)   原地出现 + 停留（**不位移**，就是普通悬浮字）
     *   阶段 2 掉落     [.., +掉落时长)             落到地面 → 弹跳 → 空中翻滚 → 平躺不动
     *   阶段 3 淡出     （含在掉落时长末段）         静止停留 SCROLL_REST_TICKS 后线性淡出
     * </pre>
     *
     * <p>★ **生成时是"正常文本"，消失时才掉** —— 这是本样式的设计口径：
     * 因此生成位置就是基准点（{@code spawnInternal} 不给它抬高），
     * 掉落在**消失阶段**才从 {@code groundRestPerChar} 取地面落点。
     *
     * <p>★★ **更强的物理感**（2026-10-04 加强），四个维度都做了随机化 + 连续化：
     * <ol>
     *   <li><b>落地位置小幅度不同</b>：每字有自己的横向漂移速度与方向 ⇒ 落点散成一小圈，
     *       而不是全部叠在同一条竖线上（{@link Anim#scrollDriftOf} / {@link Anim#scrollDriftAngleOf}）；</li>
     *   <li><b>落地角度不同</b>：每字的躺倒俯仰 = {@code −88° ± 24°}、偏航 = {@code ±30°}，
     *       由 {@code index + seed} 固定决定 ⇒ 散落得像实物，且**落地后永不变化**；</li>
     *   <li><b>空中自然翻转</b>：下落期间额外翻 {@code 0..2} 圈，用**总下落进度**驱动
     *       ⇒ 落地那一刻正好收敛到躺倒角，收尾不跳变；</li>
     *   <li><b>跳完平躺不动</b>：静止后横向/竖直全部冻结（积分里清零），
     *       并切 {@code Billboard.FIXED} ⇒ 真正"躺在地上不动"。</li>
     * </ol>
     *
     * <p>★★ **落地后不再随视角转动**：字实体默认 {@link Display.Billboard#CENTER}（始终面向玩家视线）
     * ⇒ 即使写了躺倒角，玩家一转视角，字也跟着转 ⇒ 躺平效果全废。
     * 因此**一旦触地**就把 billboard 切成 {@link Display.Billboard#FIXED}，
     * 让客户端按实体自身的固定朝向渲染，字就"钉"在地上了。
     *
     * <p>位置由纯函数 {@link Anim#scrollTrackAt} 一次算完（含全部弹跳）⇒ 渲染侧只做一次数组查询，
     * 既便宜又完全可离线复现。位移写进 {@code Transformation} 的平移，
     * 姿态（俯仰 + 偏航）写进**左乘四元数**。
     *
     * @return {@code true} = 这个字还在（整段仍需推进）
     */
    private static boolean advanceMitaScroll(TextDisplay display, Instance instance, int index, int local) {
        int count = instance.chars.size();
        int inFrames = Math.max(0, instance.inFrames);
        int stayEnd = inFrames + Math.max(0, instance.stayTicks);

        // ── 阶段 1：正常显示（原地出现 + 停留，与普通悬浮字一致）──
        if (local < stayEnd) {
            // 出现阶段给一个短淡入（避免"硬闪"）；其余时刻全不透明
            double opacity = inFrames <= 0 ? 1d
                    : Math.max(0d, Math.min(1d, local / (double) inFrames));
            applyFrame(display, instance, local, inFrames, index, count, opacity, instance.scale);
            return true;
        }

        // ── 阶段 2：掉落（落到正下方地面 → 弹跳 → 空中翻滚 → 平躺不动）──
        int rollFrame = local - stayEnd;
        float rest = groundRestOf(instance, index);            // 地面相对基准点（通常为负）
        // ★ 与 scrollRollTicksFor 同口径封顶（高空时不至于掉一整分钟，也不会被提前强收）
        rest = (float) -Anim.clampScrollFall(Math.abs(rest));
        int rollTotal = Anim.scrollRollTicksFor(Math.abs(rest));
        if (rollFrame > rollTotal) {
            display.setTextOpacity((byte) 0);
            return false;
        }

        // 起点 = 基准点（x/z = 0, y = 0），竖直初速度 = 该字自己的微抬（制造参差），地面在 rest
        double[] state = Anim.scrollTrackAt(index, instance.seed, rollFrame,
                0d, rest, Anim.scrollLiftOf(index, instance.seed), rollTotal + 1);
        float x = (float) state[0];
        float z = (float) state[1];
        float y = (float) state[2];
        float pitch = (float) state[3];
        float yaw = (float) state[4];
        int restingAt = (int) state[5];

        // ★★ 触地后：把 billboard 钉成 FIXED ⇒ 字不再随玩家视角转动（"躺在地上"的样子才立得住）
        if (restingAt >= 0) {
            pinToGround(display);
        }

        double opacity = 1d;
        if (restingAt >= 0) {
            int sinceRest = rollFrame - restingAt;
            if (sinceRest > Anim.SCROLL_REST_TICKS) {
                opacity = Anim.scrollFadeOpacity(sinceRest - Anim.SCROLL_REST_TICKS);
                if (opacity <= 0d) {
                    display.setTextOpacity((byte) 0);
                    return false;
                }
            }
        }

        float base = instance.scale;
        Vector3f scaleVec = new Vector3f(base, base, base);

        // ★ 姿态：先绕 Y 偏航（每字不同朝向），再绕 X 俯仰（翻滚 / 躺倒）
        //   顺序固定 ⇒ 同一组 (pitch, yaw) 永远得到同一姿态，可离线穷举
        Quaternionf rot = new Quaternionf()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch));

        Vector3f translationVec = new Vector3f(x, y, z);
        // ★ 颤抖：掉落的字也能抖（与正常显示阶段的观感一致）
        if (instance.look.tremble()) {
            double amp = Anim.TREMBLE_AMPLITUDE * base;
            long perCharSeed = instance.seed + index * 7919L;
            translationVec = new Vector3f(
                    x + (float) Anim.trembleOffset(instance.tick, 0, perCharSeed, amp),
                    y + (float) Anim.trembleOffset(instance.tick, 1, perCharSeed, amp),
                    z + (float) Anim.trembleOffset(instance.tick, 2, perCharSeed, amp));
        }

        display.setTransformation(new Transformation(
                translationVec,
                rot,
                scaleVec,
                new Quaternionf()));
        display.setTextOpacity((byte) Math.max(0, Math.min(255, (int) Math.round(opacity * 255))));
        return true;
    }

    /**
     * ★★ **把字"钉"在地上**：把 {@link Display.Billboard} 从 {@code CENTER} 切成 {@code FIXED}。
     *
     * <p>为什么必须切：{@code CENTER} 会让文本**始终面向玩家视线**（billboard 语义），
     * 于是我们写进 {@code Transformation} 的"躺倒 −88°"会被客户端的 billboard 旋转**叠加/覆盖**
     * ⇒ 玩家一转视角，地上的字也跟着转 ⇒ "躺平"完全看不出来。
     * 切成 {@code FIXED} 后，客户端按实体自身的固定朝向渲染，字就固定躺在那儿了。
     *
     * <p>幂等：已经是 {@code FIXED} 就不再写（每刻重设会带来无谓的实体数据包）。
     */
    private static void pinToGround(TextDisplay display) {
        try {
            Display.Billboard wanted = billboardFor(true);
            if (display.getBillboard() != wanted) {
                display.setBillboard(wanted);
            }
        } catch (Throwable ignored) {
            // 实体已失效 / 服务端不支持 ⇒ 退化为"仍随视角转"，不影响其余渲染
        }
    }

    /**
     * ★★ **米塔风格该用哪种 billboard**（纯函数，供离线穷举）。
     *
     * <p>规则：
     * <ul>
     *   <li>{@code resting == false}（还在空中）⇒ {@link Display.Billboard#CENTER}：
     *       与普通悬浮字一致，字**面向玩家视线**，怎么走都能看清；</li>
     *   <li>{@code resting == true}（已落地静止）⇒ {@link Display.Billboard#FIXED}：
     *       按实体自身固定朝向渲染，字**钉在地上**，玩家转视角它不再转
     *       （否则写进去的"躺倒 −88°"会被 billboard 旋转吃掉，躺平看不出）。</li>
     * </ul>
     *
     * @param resting 是否已经落地静止
     * @return 该阶段应使用的 billboard
     */
    public static Display.Billboard billboardFor(boolean resting) {
        return resting ? Display.Billboard.FIXED : Display.Billboard.CENTER;
    }

    /** 取某字的**地面落点偏移**（相对基准点；没有逐字数据 ⇒ 0 = 原地）。 */
    private static float groundRestOf(Instance instance, int index) {
        if (instance.groundRestPerChar != null && index < instance.groundRestPerChar.length) {
            return instance.groundRestPerChar[index];
        }
        return 0f;
    }

    /** 回收本段全部实体（幂等；主线程）。 */
    public static void retire(Instance instance) {
        if (instance == null) {
            return;
        }
        for (TextDisplay display : instance.chars) {
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
        instance.chars.clear();
        instance.finished = true;
    }

    /** 把一帧的变换写进一个字实体。 */
    private static void applyFrame(TextDisplay display, Instance instance, int local, int inFrames,
                                   int index, int count, double opacity, float baseScale) {
        Style style = instance.style;
        long seed = instance.seed;
        Style.InKind in = style.in();

        double p;
        if (local < inFrames) {
            p = Anim.inProgress(local, inFrames);
        } else {
            p = 1d;
        }

        // 中心 / 随机错峰的样子需要额外空间扩散；顺序错峰不需要
        double sx = Anim.scale(in, p, 0, index, seed);
        double sy = Anim.scale(in, p, 1, index, seed);
        double sz = Anim.scale(in, p, 2, index, seed);

        // 整体缩放叠加
        Vector3f scaleVec = new Vector3f(
                (float) (sx * baseScale),
                (float) (sy * baseScale),
                (float) (sz * baseScale));

        float tx = (float) Anim.translation(in, p, 0, index, seed);
        float ty = (float) Anim.translation(in, p, 1, index, seed);
        float tz = (float) Anim.translation(in, p, 2, index, seed);

        // ★ 颤抖：叠在（可能已存在的）位移之上。逐帧换向 ⇒ 不规则的"发抖"，不是"摇晃"。
        if (instance.look.tremble()) {
            double amp = Anim.TREMBLE_AMPLITUDE * baseScale;
            // 每个字用各自的种子偏移，否则整排会"同频抖动"（像整块板在晃，很假）
            long perCharSeed = seed + index * 7919L;
            tx += (float) Anim.trembleOffset(local, 0, perCharSeed, amp);
            ty += (float) Anim.trembleOffset(local, 1, perCharSeed, amp);
            tz += (float) Anim.trembleOffset(local, 2, perCharSeed, amp);
        }
        Vector3f translationVec = new Vector3f(tx, ty, tz);

        double rotX = Math.toRadians(Anim.rotationDegrees(in, p, 0, index, seed));
        double rotY = Math.toRadians(Anim.rotationDegrees(in, p, 1, index, seed));
        double rotZ = Math.toRadians(Anim.rotationDegrees(in, p, 2, index, seed));
        Quaternionf leftRotation = new Quaternionf().rotateXYZ((float) rotX, (float) rotY, (float) rotZ);

        display.setTransformation(new Transformation(
                translationVec,
                leftRotation,
                scaleVec,
                new Quaternionf()));

        display.setTextOpacity((byte) Anim.opacity(opacity));

        // 乱码类：出现阶段"乱码→显形"，消失阶段"显形→乱码"
        applyObfuscation(display, style, in, local, inFrames);
    }

    /** 乱码渲染：数据包 style 25/26/27 用 {@code obfuscated} 标记实现。 */
    private static void applyObfuscation(TextDisplay display, Style style,
                                         Style.InKind in, int local, int inFrames) {
        if (in == Style.InKind.OBFUSCATE_DECODE && local < inFrames) {
            // 前半段乱码，后半段显形（阈值取进度过半）
            boolean obfuscated = local < inFrames / 2;
            setObfuscated(display, obfuscated);
            return;
        }
        if (style.out() == Style.OutKind.OBFUSCATE) {
            // 消失阶段由 advance 收敛；此处按已过帧数决定（>= stay 后开始乱码）
            // 简化：出阶段一旦进入就已由外层控制透明度，这里只在"仍完全不透明"时保持显形
            setObfuscated(display, false);
        }
    }

    private static void setObfuscated(TextDisplay display, boolean obfuscated) {
        String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
                .plainText().serialize(display.text());
        if (obfuscated) {
            display.text(net.kyori.adventure.text.Component.text(plain)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.OBFUSCATED, true));
        } else {
            display.text(net.kyori.adventure.text.Component.text(plain));
        }
    }

    /** 逐字拆分（按 UTF-16 code unit 切，与数据包的"逐字符列表"口径一致）。 */
    static String[] splitGlyphs(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == ' ') {
                continue; // 空白不生成实体（数据包同样跳过无效字）
            }
            out.add(String.valueOf(c));
        }
        return out.toArray(new String[0]);
    }

    /** 出现插值帧数：依风格取（整行类用 16，逐字类用 8，落地砸字走自己的下落曲线）。 */
    static int inFramesOf(Style style) {
        Style.InKind in = style.in();
        if (in == Style.InKind.NONE) {
            return 1;
        }
        if (in == Style.InKind.WHOLE_SLIDE_DOWN
                || in == Style.InKind.WHOLE_CENTER_DIFFUSE
                || in == Style.InKind.SCALE_UP_SLOW) {
            return 16;
        }
        if (in == Style.InKind.GROUND_DROP) {
            return Anim.FALL_FRAMES; // 兜底：真正时长按高度算（见 fallFramesFor）
        }
        return 8;
    }

    /** 消失插值帧数（落地砸字的缩小消失更慢）。 */
    static int outFramesOf(Style style) {
        return style.out() == Style.OutKind.SHRINK_AWAY
                ? Anim.SHRINK_OUT_FRAMES
                : Anim.OUT_FRAMES;
    }

    /** 出现阶段的透明度曲线（乱码类前半段仍需可见，故不按进度直接压 0）。 */
    private static double inProgressOpacity(Style style, int local, int inFrames,
                                            int index, int count, long seed) {
        double p = Anim.inProgress(local, inFrames);
        Style.InKind in = style.in();
        if (in == Style.InKind.CENTER_DIFFUSE_FADE || in == Style.InKind.RANDOM_FADE
                || in == Style.InKind.WHOLE_CENTER_DIFFUSE) {
            // 淡入类：透明度跟着进度走（中心权重让外圈更晚亮起来）
            double w = style.centerStaggered()
                    ? Anim.centerWeight(index, count)
                    : Anim.unitOf(index, 7, seed);
            double adjusted = p - (1d - p) * w;
            return Anim.clamp01(adjusted);
        }
        // 其余样式：实体从一开始就可见（观感差异由 scale / translation 表达）
        return 1d;
    }

    /** 判断某实体是否是本工具箱生成的悬浮字（供其它系统避让 / 清理用）。 */
    public static boolean isFloatingGlyph(Entity entity) {
        return entity instanceof TextDisplay;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  四、池与门面（组件唯一入口）
    // ═══════════════════════════════════════════════════════════════════

    // ───────── 预设：常用观感（给调用方少写字） ─────────

    /** 克制：整行淡入淡出，最不抢戏（适合"常驻氛围字"）。 */
    public static final Style STYLE_CALM = Style.PLAIN_FADE;

    /** 凸显：逐字放大弹出（适合技能名）。 */
    public static final Style STYLE_EMPHASIS = Style.POP_SCALE;

    /** 碎裂：逐字乱码显形后乱码消失（适合"精神污染 / 侵蚀"主题）。 */
    public static final Style STYLE_GLITCH = Style.DECODE_IN_OBFUSCATE_OUT;

    /** 浮现：逐字上浮出现（适合低语 / 呢喃）。 */
    public static final Style STYLE_WHISPER = Style.FLOAT_UP_IN;

    /** 坠下：逐字下落出现（适合"宣告 / 宣判"）。 */
    public static final Style STYLE_DECREE = Style.DROP_DOWN_IN;

    /** 扩散：中心扩散淡入淡出（适合"爆发"）。 */
    public static final Style STYLE_BURST = Style.CENTER_FADE_IN_CENTER_FADE_OUT;

    /** 打字：打字机出现 + 自然淡去（适合"台词"）。 */
    public static final Style STYLE_MONOLOGUE = Style.TYPEWRITER_FADE;

    /**
     * ★ 落地砸字：字**受重力坠到真实地面**，触地压扁回弹 → 呼吸滞留 → 缓慢缩小消失。
     * <p>必须配 {@link #emitOnGround}（普通 {@link #emit} 不知道地面在哪，会退化成原地回弹）。
     */
    public static final Style STYLE_GROUND_SMASH = Style.GROUND_SMASH;

    /**
     * ★★ **米塔风格 / 字体滚地**：生成时是**正常文本**（原地显示 + 停留），
     * **消失时**才落到地面 → 反弹 → 摩擦滚停 → **躺倒**在地上 → 淡出。
     * <p>移植自数据包 V26.2 的 {@code operation/drop} + world animation 掉落路径。
     * <p>必须配 {@link #emitOnGround}（普通 {@link #emit} 不知道地面在哪 ⇒ 只会原地滚一滚）。
     */
    public static final Style STYLE_MITA_SCROLL = Style.MITA_SCROLL;

    /** 米塔风格的别名（口语称法）。 */
    public static final Style STYLE_TEXT_ROLL = Style.MITA_SCROLL;

    // ───────── 随机摆位 ─────────

    /**
     * 在玩家**周身**取一个随机位置（纯几何，不依赖世界查询）。
     *
     * <p>摆位模型：以玩家脚底为中心的水平圆环，半径落在 {@code [radiusMin, radiusMax]}，
     * 角度随机，高度落在 {@code [heightMin, heightMax]}（相对脚底）。
     * 圆环取样而非球体取样 ⇒ 字永远在"看得见的一圈"上，不会有一半概率落到脚正下方。
     *
     * @param owner      玩家；{@code null} ⇒ 返回 {@code null}
     * @param radiusMin  最小半径（格）；{@code < 0} ⇒ 0
     * @param radiusMax  最大半径（格）；{@code < radiusMin} ⇒ 取 radiusMin
     * @param heightMin  最低高度（格，相对脚底）
     * @param heightMax  最高高度（格）；{@code < heightMin} ⇒ 取 heightMin
     * @param random     随机源（调用方自持 ⇒ 可复现、可测）
     * @return 生成的基准位置（已做"不落在方块里"的最小修正）；世界为空 ⇒ {@code null}
     */
    public static Location rollAround(Player owner, double radiusMin, double radiusMax,
                                      double heightMin, double heightMax, Random random) {
        if (owner == null) {
            return null;
        }
        World world = owner.getWorld();
        if (world == null) {
            return null;
        }
        Random rng = random == null ? new Random() : random;

        double rMin = Math.max(0d, radiusMin);
        double rMax = Math.max(rMin, radiusMax);
        double radius = rMin + (rMax - rMin) * rng.nextDouble();

        double angle = rng.nextDouble() * Math.PI * 2d;
        double dx = Math.cos(angle) * radius;
        double dz = Math.sin(angle) * radius;

        double hMin = heightMin;
        double hMax = Math.max(hMin, heightMax);
        double dy = hMin + (hMax - hMin) * rng.nextDouble();

        Location base = owner.getLocation();
        Location at = base.clone().add(dx, dy, dz);

        // 最小修正：若落点所在方块"脚下没东西"，把它抬到脚底高度（避免字悬在虚空或埋进地里）
        Location ground = base.clone();
        ground.setY(base.getY());
        return at.getY() < ground.getY() - 1d ? ground : at;
    }

    /**
     * 朝向：让文本**面向玩家**（billboard=CENTER 已保证正面朝视线，
     * 这里给出的是行方向的基准 yaw，取玩家朝向的垂直方向 ⇒ 字横向铺开、正对玩家侧身）。
     */
    public static float facingFor(Player owner, Random random) {
        if (owner == null) {
            return 0f;
        }
        float yaw = owner.getLocation().getYaw();
        Random rng = random == null ? new Random() : random;
        // 左右各偏 0~35°，避免所有字都整整齐齐一条线
        float jitter = (rng.nextFloat() - 0.5f) * 70f;
        return yaw + jitter;
    }

    // ───────── 池：一次角色会话内的全部悬浮文本 ─────────

    /** 新建一个池（调用方：组件的 {@code start()}）。 */
    public static Pool newPool(Object ownerToken) {
        return new Pool(ownerToken);
    }

    /**
     * 悬浮文本池：管理一个角色实例在某段时间内的全部悬浮文本段。
     *
     * <h2>它管什么</h2>
     * <ul>
     *   <li>逐段推进（每刻一次）与到期回收；</li>
     *   <li>**总实体数封顶** —— 悬浮文本是"每字一实体"，不管住会比粒子更吃性能；</li>
     *   <li>**节流** —— 同一个触发时刻（如受击）短时间内反复来，只放一条；
     *       全局也有一个最小间隔，防止技能连打时糊成一片；</li>
     *   <li>**每段字数上限**（{@link TextUtil#MAX_CHARS}）。</li>
     * </ul>
     *
     * <h2>不做</h2>
     * 不自己开任务：{@link #tick()} 由调用方的 {@code update()} 每刻调一次。
     * 这样池的生命周期与组件严格同生共死，不需要任何框架支持。
     */
    public static final class Pool {

        /** 同时存活的最大段数（每段最多 24 字 ⇒ 实体内存上限 = 段数 × 字数）。 */
        public static final int MAX_SEGMENTS = 5;

        /** 单段最大字数（与生成器一致）。 */
        public static final int MAX_CHARS_PER_SEGMENT = TextUtil.MAX_CHARS;

        /** 全局最小间隔（刻）：两次吐字之间的硬性间隔，防连打糊屏。 */
        public static final int MIN_GAP_TICKS = 7;

        private final Object ownerToken;
        private final List<Instance> live = new ArrayList<>();
        private final Map<String, Integer> lastEmitTickByMoment = new ConcurrentHashMap<>();

        /** 池自持的随机源（不与其它组件抢用全局 Random ⇒ 可复现、可测）。 */
        private final Random random = new Random();

        private int currentTick;
        private int lastEmitTick = Integer.MIN_VALUE;

        private Pool(Object ownerToken) {
            this.ownerToken = ownerToken;
        }

        /** 本池自持的随机源（静态门面转发时用；调用方也可拿它做可复现测试）。 */
        public Random randomOf() {
            return random;
        }

        /** 池自持随机源的下一个 0..bound-1（测试友好的读口）。 */
        public int nextInt(int bound) {
            return random.nextInt(bound);
        }

        /**
         * 吐一句悬浮文本（池的实例方法；等价于 {@link TextUtil#emit(Pool, Player, String, String, Style)}）。
         *
         * @return 是否真的放出去了（被节流 / 封顶 / 参数无效则 false）
         */
        public boolean emit(Player owner, String moment, String text, Style style,
                            double radiusMin, double radiusMax, double heightMin, double heightMax,
                            int speed, int stay, float scale, Random random) {
            return emitInternal(owner, moment, text, style, Look.PLAIN, 0d,
                    radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale, random);
        }

        /** 带外观（颜色 / 泛光 / 颤抖）的吐字。 */
        public boolean emit(Player owner, String moment, String text, Style style, Look look,
                            double radiusMin, double radiusMax, double heightMin, double heightMax,
                            int speed, int stay, float scale, Random random) {
            return emitInternal(owner, moment, text, style, look, 0d,
                    radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale, random);
        }

        /**
         * ★ **落地砸字专用**：摆位后走 {@link TextUtil#spawnOnGround}（逐字查地面）。
         *
         * @param groundOffset 起点相对基准点的高度（格；{@code <= 0} ⇒ 按 4 算）
         */
        public boolean emitOnGround(Player owner, String moment, String text, Style style,
                                    double groundOffset,
                                    double radiusMin, double radiusMax,
                                    double heightMin, double heightMax,
                                    int speed, int stay, float scale, Random random) {
            return emitInternal(owner, moment, text, style, Look.PLAIN,
                    groundOffset <= 0d ? 4d : groundOffset,
                    radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale, random);
        }

        /** 落地砸字 + 外观。 */
        public boolean emitOnGround(Player owner, String moment, String text, Style style, Look look,
                                    double groundOffset,
                                    double radiusMin, double radiusMax,
                                    double heightMin, double heightMax,
                                    int speed, int stay, float scale, Random random) {
            return emitInternal(owner, moment, text, style, look,
                    groundOffset <= 0d ? 4d : groundOffset,
                    radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale, random);
        }

        /** {@code emit} / {@code emitOnGround} 的公共实现（{@code groundOffset > 0} ⇒ 查地面）。 */
        private boolean emitInternal(Player owner, String moment, String text, Style style,
                                     Look look, double groundOffset,
                                     double radiusMin, double radiusMax,
                                     double heightMin, double heightMax,
                                     int speed, int stay, float scale, Random random) {
            return emitInternal(owner, moment, text, style, look, groundOffset,
                    radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale, random, false);
        }

        /**
         * {@code emit} 的**全参数 + 强制**实现。
         *
         * @param force ★ {@code true} ⇒ 跳过 {@link #canEmit} 的节流（**只给连排台词队列用**）。
         *              连排台词本身就是"用户明确要的、按 {@code gap} 刻排好的"序列，
         *              若仍被 {@link #MIN_GAP_TICKS} 挡掉，就会出现"排了 3 句只播 2 句"
         *              （间隔 7 刻 &lt; 12 刻 ⇒ 奇数句全被吃掉）。这是有意开的口子。
         */
        private boolean emitInternal(Player owner, String moment, String text, Style style,
                                     Look look, double groundOffset,
                                     double radiusMin, double radiusMax,
                                     double heightMin, double heightMax,
                                     int speed, int stay, float scale, Random random,
                                     boolean force) {
            if (owner == null || text == null || text.isBlank()) {
                return false;
            }
            if (!force && !canEmit(moment)) {
                return false;
            }
            // 只清理已播完的段（不推进）—— 推进归 tick()，若这里也推进，
            // 同刻内"受击→say→emit"与"update→tick"会让段被推进两次，动画加速一倍
            reapDeadOnly();
            if (live.size() >= MAX_SEGMENTS) {
                return false;
            }
            Location at = rollAround(owner, radiusMin, radiusMax, heightMin, heightMax, random);
            if (at == null) {
                return false;
            }
            float facing = facingFor(owner, random);
            Instance instance = groundOffset > 0d
                    ? spawnOnGround(at, facing, text, style, speed, stay, scale, groundOffset, look)
                    : spawn(at, facing, text, style, speed, stay, scale, look);
            if (instance == null) {
                return false;
            }
            live.add(instance);
            markEmitted(moment);
            return true;
        }

        /**
         * 节流判定：同 moment 至少 {@value #MIN_GAP_TICKS} 刻间隔，且全局也至少这个间隔。
         * <p>不在本方法里记账 —— 记账在 {@link #markEmitted(String)}，
         * 这样"生成失败"不会白白吃掉一次配额。
         */
        private boolean canEmit(String moment) {
            if (lastEmitTick != Integer.MIN_VALUE
                    && currentTick - lastEmitTick < MIN_GAP_TICKS) {
                return false;
            }
            String key = moment == null ? "default" : moment;
            Integer last = lastEmitTickByMoment.get(key);
            return last == null || currentTick - last >= MIN_GAP_TICKS;
        }

        /** 记账一次"真的吐出去了"。 */
        private void markEmitted(String moment) {
            lastEmitTick = currentTick;
            lastEmitTickByMoment.put(moment == null ? "default" : moment, currentTick);
        }

        /** 每刻推进一次（**主线程**；由组件的 {@code update()} 调）。 */
        public void tick() {
            currentTick++;
            drainQueue();
            advanceAll();
            reapDeadOnly();
        }

        // ───── ★ 连排台词队列 ─────

        /** 待播的一句。 */
        private static final class Pending {
            final Player owner;
            final String moment;
            final String text;
            final Style style;
            final Look look;
            /** ★ 落地砸字的起落高度（格）；{@code <= 0} ⇒ 该句按普通（不查地面）路径播。 */
            final double groundOffset;
            /** ★ 弹出速度（决定逐字错峰间隔）；{@code <= 0} ⇒ {@link Anim#SPEED_BASE}。 */
            final int speed;
            final int dueTick;
            final int gapToNext;

            Pending(Player owner, String moment, String text, Style style, Look look,
                    double groundOffset, int speed, int dueTick, int gapToNext) {
                this.owner = owner;
                this.moment = moment;
                this.text = text;
                this.style = style;
                this.look = look;
                this.groundOffset = groundOffset;
                this.speed = speed;
                this.dueTick = dueTick;
                this.gapToNext = gapToNext;
            }
        }

        private final List<Pending> queue = new ArrayList<>();

        /**
         * ★ **排队若干句台词**（句间隔由 {@code gapTicks} 决定）。
         *
         * <p>刻意**不绕过节流**：排队只是"稍后说"，真到说的那一刻仍走 {@link #emitInternal}
         * 的封顶与节流 ⇒ 连排台词也不会突破三层闸门。
         *
         * @param groundOffset ★ **落地砸字**：{@code > 0} ⇒ 每句都走 {@link #spawnOnGround}
         *                     （真的查方块、真的掉到地面）；{@code <= 0} ⇒ 普通路径。
         *                     ★ 不传它会导致"排队播出来的字只会原地缩放"（踩过）。
         * @param speed        弹出速度（逐字错峰）；{@code <= 0} ⇒ {@link Anim#SPEED_BASE}
         * @return 实际排入的句数（空句会被剔除）
         */
        public int enqueue(Player owner, String moment, List<String> lines, Style style,
                           Look look, double groundOffset, int speed, int gapTicks) {
            if (owner == null || lines == null || lines.isEmpty()) {
                return 0;
            }
            // ★ 落地砸字 / 米塔滚地若调用方没给高度，兜底到与 spawnOnGround 同一个默认值（4 格）
            boolean needsGround = style == Style.GROUND_SMASH || style == Style.MITA_SCROLL;
            double effectiveOffset = needsGround
                    ? (groundOffset <= 0d ? 4d : groundOffset)
                    : groundOffset;
            int effSpeed = speed <= 0 ? Anim.SPEED_BASE : speed;
            int gap = gapTicks <= 0 ? SEQUENCE_GAP_TICKS : gapTicks;
            int queued = 0;
            int cursor = currentTick; // 第一句：下一拍就放（不再等一个 gap）
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                queue.add(new Pending(owner, moment, line, style, look,
                        effectiveOffset, effSpeed, cursor, gap));
                cursor += gap;
                queued++;
            }
            return queued;
        }

        /** 把到点的台词放出去（每刻调一次）。 */
        private void drainQueue() {
            if (queue.isEmpty()) {
                return;
            }
            for (int i = queue.size() - 1; i >= 0; i--) {
                Pending pending = queue.get(i);
                if (currentTick < pending.dueTick) {
                    continue;
                }
                queue.remove(i);
                if (pending.owner == null || !pending.owner.isOnline() || pending.owner.isDead()) {
                    continue; // 排队期间掉线 / 死了 ⇒ 这一句作废（连带后续照常）
                }
                // 真到说的这一刻，仍走统一闸门（封顶 MAX_SEGMENTS 仍生效 —— 这是有意的）
                // ★ force=true：绕开"同一 moment 至少 12 刻"的节流，因为连排台词
                //   本身就是按 gap（约 7 刻）排好的意图序列，被节流会"排 3 句只播 2 句"（踩过）
                // ★ 必须把 pending.groundOffset 透传下去：否则落地砸字的连排台词
                //   会退化成普通路径（groundDrop=0）⇒ 字只原地缩放，不砸地（踩过）
                emitInternal(pending.owner, pending.moment, pending.text, pending.style,
                        pending.look, pending.groundOffset, 1.2d, 2.6d, 1.1d, 2.2d,
                        pending.speed, -1, 1f, randomOf(), true);
            }
        }

        /** 队列里还有几句没放（调试 / 测试读口）。 */
        public int pendingCount() {
            return queue.size();
        }

        /** 推进所有存活段一帧（推进只在 tick 里发生，保证每刻恰好一次）。 */
        private void advanceAll() {
            for (Instance instance : live) {
                TextUtil.advance(instance);
            }
        }

        /** 只移除已播完的段（**不推进**；供 {@code emit()} 在封顶判定前清理占位）。 */
        private void reapDeadOnly() {
            for (int i = live.size() - 1; i >= 0; i--) {
                Instance instance = live.get(i);
                if (instance.isFinished()) {
                    retire(instance);
                    live.remove(i);
                }
            }
        }

        /** 立即清空全部（调用方：组件的 {@code stop()}）。 */
        public void close() {
            for (Instance instance : live) {
                retire(instance);
            }
            live.clear();
            queue.clear();
            lastEmitTickByMoment.clear();
            lastEmitTick = Integer.MIN_VALUE;
        }

        /** 当前存活段数（调试 / 测试读口）。 */
        public int liveSegmentCount() {
            return live.size();
        }

        /** 当前存活实体总数（调试 / 测试读口）。 */
        public int liveEntityCount() {
            int sum = 0;
            for (Instance instance : live) {
                sum += instance.entityCount();
            }
            return sum;
        }

        public Object ownerToken() {
            return ownerToken;
        }
    }

    // ───────── 静态门面（让调用方不必自己拼一长串参数） ─────────

    /**
     * 用工具箱默认参数吐一句（最常用的一条）。
     *
     * <p>默认摆位：半径 1.2~2.6 格、高度 1.1~2.2 格（相对脚底）；
     * 默认 speed {@value Anim#SPEED_BASE}、滞留取工具箱默认、缩放 1.0。
     *
     * @return 是否真的放出去了
     */
    public static boolean emit(Pool pool, Player owner, String moment, String text, Style style) {
        return emit(pool, owner, moment, text, style, 1.2d, 2.6d, 1.1d, 2.2d,
                Anim.SPEED_BASE, -1, 1.0f);
    }

    /**
     * 用工具箱默认摆位 + 指定缩放 / 速度吐一句（想微调观感时用）。
     */
    public static boolean emit(Pool pool, Player owner, String moment, String text, Style style, float scale) {
        return emit(pool, owner, moment, text, style, 1.2d, 2.6d, 1.1d, 2.2d,
                Anim.SPEED_BASE, -1, scale);
    }

    /**
     * 全参数版（摆位、速度、滞留、缩放都可控）。
     *
     * @param pool 池；{@code null} ⇒ 返回 {@code false}（调用方未建池）
     * @return 是否真的放出去了
     */
    public static boolean emit(Pool pool, Player owner, String moment, String text, Style style,
                               double radiusMin, double radiusMax, double heightMin, double heightMax,
                               int speed, int stay, float scale) {
        return emit(pool, owner, moment, text, style, Look.PLAIN,
                radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale);
    }

    /** 带外观的全参数版。 */
    public static boolean emit(Pool pool, Player owner, String moment, String text, Style style,
                               Look look,
                               double radiusMin, double radiusMax, double heightMin, double heightMax,
                               int speed, int stay, float scale) {
        if (pool == null) {
            return false;
        }
        return pool.emit(owner, moment, text, style, look,
                radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale,
                pool.randomOf());
    }

    // ───────── ★ 连续台词（一个技能吐 1~3 句，句间零点几秒）─────────

    /**
     * ★ **连排台词**：把若干句排队，**每隔几刻放一句**，形成"连贯的对话感"。
     *
     * <h2>为什么不用 Bukkit 调度器排延迟任务</h2>
     * 那会多出一条生命周期（还得自己防守玩家下线 / 换角色），而本工具箱的 {@link Pool}
     * 本来就是逐刻被组件 {@code update()} 驱动的 ⇒ 把"到点该说下一句"这件事放在池里，
     * 零额外生命周期、天然与组件同生共死。
     *
     * <h2>口吻</h2>
     * 句间隔 {@code 4~8} 刻（0.2~0.4 秒）正好像"一口气连着说"；
     * 太快会糊成一片（读不过来），太慢就散了。
     *
     * @param lines     要说的几句（{@code null} / 空 ⇒ 什么都不做）
     * @param gapTicks  句间隔（刻）；{@code <= 0} ⇒ 用 {@link #SEQUENCE_GAP_TICKS}
     * @return 实际排入的句数
     */
    public static int emitSequence(Pool pool, Player owner, String moment,
                                   List<String> lines, Style style, Look look, int gapTicks) {
        return emitSequence(pool, owner, moment, lines, style, look, 0d, 0, gapTicks);
    }

    /**
     * ★ **连排台词 + 落地砸字**：额外指定每句的起落高度与弹出速度。
     *
     * <p>{@code groundOffset > 0} ⇒ 每句都走 {@link #spawnOnGround}（真的查方块、真的砸地）；
     * {@code <= 0} ⇒ 普通路径。★ 风格是 {@link Style#GROUND_SMASH} 且这里给 {@code <= 0} 时，
     * 池内会兜底到 4 格（与 {@link #spawnOnGround} 同口径）。
     *
     * @param speed 弹出速度（逐字错峰）；{@code <= 0} ⇒ {@link Anim#SPEED_BASE}
     */
    public static int emitSequence(Pool pool, Player owner, String moment,
                                   List<String> lines, Style style, Look look,
                                   double groundOffset, int speed, int gapTicks) {
        if (pool == null) {
            return 0;
        }
        return pool.enqueue(owner, moment, lines, style, look, groundOffset, speed, gapTicks);
    }

    /**
     * 同时存活的最大段数（对外读口）—— 需求："文本可以同时存在最多 5 条"。
     * <p>等价于 {@link Pool#MAX_SEGMENTS}，供组件 / 测试 / 文档引用。
     */
    public static int maxConcurrentSegments() {
        return Pool.MAX_SEGMENTS;
    }

    /** 连排台词的默认句间隔（刻）：约 0.2 秒，像一口气连着说。 */
    public static final int SEQUENCE_GAP_TICKS = 4;

    /**
     * ★ **落地砸字专用**：吐一句会**掉到真实地面**的文本。
     *
     * <p>与 {@link #emit} 的差别只有一处：生成时走 {@link #spawnOnGround}（逐字查地面）。
     * 会多 N 次方块查询 ⇒ **只给 {@link Style#GROUND_SMASH} 用**。
     *
     * @param groundOffset 字从基准点上方多少格开始掉（格；{@code <= 0} ⇒ 按 4 算）
     */
    public static boolean emitOnGround(Pool pool, Player owner, String moment, String text, Style style,
                                       double groundOffset,
                                       double radiusMin, double radiusMax,
                                       double heightMin, double heightMax,
                                       int speed, int stay, float scale) {
        return emitOnGround(pool, owner, moment, text, style, Look.PLAIN, groundOffset,
                radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale);
    }

    /** 落地砸字 + 外观的全参数版。 */
    public static boolean emitOnGround(Pool pool, Player owner, String moment, String text, Style style,
                                       Look look, double groundOffset,
                                       double radiusMin, double radiusMax,
                                       double heightMin, double heightMax,
                                       int speed, int stay, float scale) {
        if (pool == null) {
            return false;
        }
        return pool.emitOnGround(owner, moment, text, style, look, groundOffset,
                radiusMin, radiusMax, heightMin, heightMax, speed, stay, scale,
                pool.randomOf());
    }

    /** 每刻推进一次（转发到池；池为 {@code null} 时静默）。 */
    public static void tick(Pool pool) {
        if (pool != null) {
            pool.tick();
        }
    }

    /** 收工：立即清空全部（转发到池；池为 {@code null} 时静默）。 */
    public static void close(Pool pool) {
        if (pool != null) {
            pool.close();
        }
    }

    // ───────── 词库：从候选中随机取一条（纯函数） ─────────

    /**
     * 从候选列表里取一条（确定性随机）。
     * <p>空列表 ⇒ {@code null}；{@code random} 为 {@code null} ⇒ 取第一条。
     */
    public static String pick(List<String> candidates, Random random) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (random == null) {
            return candidates.get(0);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    /** 词库：不可变，按时刻分组。 */
    public static final class Lexicon {

        private final Map<String, List<String>> byMoment;

        private Lexicon(Map<String, List<String>> byMoment) {
            this.byMoment = byMoment;
        }

        /** 新建（拷贝入参 ⇒ 调用方之后改原表不影响本词库）。 */
        public static Lexicon of(Map<String, List<String>> byMoment) {
            Map<String, List<String>> copy = new ConcurrentHashMap<>();
            if (byMoment != null) {
                byMoment.forEach((k, v) -> copy.put(k, v == null ? List.of() : List.copyOf(v)));
            }
            return new Lexicon(copy);
        }

        /** 取某时刻的全部候选（不可变；无该时刻 ⇒ 空列表）。 */
        public List<String> candidates(String moment) {
            List<String> list = byMoment.get(moment == null ? "default" : moment);
            return list == null ? List.of() : list;
        }

        /** 取某时刻的一条（无候选 ⇒ {@code null}）。 */
        public String pick(String moment, Random random) {
            return TextUtil.pick(candidates(moment), random);
        }

        /** 全部时刻名（调试 / 测试读口）。 */
        public Set<String> moments() {
            return Set.copyOf(byMoment.keySet());
        }

        /** 以某条兜底词库补齐缺失时刻（链式构造用）。 */
        public Lexicon withFallbackFrom(Supplier<Lexicon> fallback) {
            if (fallback == null) {
                return this;
            }
            Lexicon other = fallback.get();
            if (other == null) {
                return this;
            }
            Map<String, List<String>> merged = new ConcurrentHashMap<>(other.byMoment);
            merged.putAll(this.byMoment);
            return new Lexicon(merged);
        }
    }

    /** 词库便捷构造：把"时刻 → 候选文本"的散表组装成 {@link Lexicon}。 */
    public static Lexicon lexiconOf(Object... pairs) {
        if (pairs == null || pairs.length % 2 != 0) {
            return Lexicon.of(Map.of());
        }
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            String key = String.valueOf(pairs[i]);
            List<String> values = new ArrayList<>();
            Object raw = pairs[i + 1];
            if (raw instanceof String[] array) {
                values.addAll(List.of(array));
            } else if (raw instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null) {
                        values.add(String.valueOf(item));
                    }
                }
            } else if (raw != null) {
                values.add(String.valueOf(raw));
            }
            map.put(key, values);
        }
        return Lexicon.of(map);
    }

    /** 向量便捷：水平距离（供调用方做"靠近时才吐字"之类的判定）。 */
    public static double horizontalDistance(Location a, Location b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        Vector va = a.toVector();
        Vector vb = b.toVector();
        va.setY(0);
        vb.setY(0);
        return va.distance(vb);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  五、杂项
    // ═══════════════════════════════════════════════════════════════════

    /** 判断某实体是否是本工具箱生成的悬浮字（静态转发）。 */
    public static boolean isGlyphEntity(Entity entity) {
        return isFloatingGlyph(entity);
    }
}
