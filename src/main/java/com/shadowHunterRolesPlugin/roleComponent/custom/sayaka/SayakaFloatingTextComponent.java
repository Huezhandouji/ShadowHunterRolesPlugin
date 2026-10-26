package com.shadowHunterRolesPlugin.roleComponent.custom.sayaka;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 「芹泽沙耶香 · 瓦尔基里」的**语音库**（风格悬浮文本）。
 *
 * <h2>角色语气（用户给的台词按此风格落地）</h2>
 * 疲惫但温和、克制而可靠：对敌人说"对不起 / 走好"，对自己说"还行 / 别管我"，
 * 独处时露一点疲态（"好累。但还行。"）。⇒ 配色以黑红为底、音量（字号）中等偏大，
 * 不使用咄咄逼人的语气档。
 *
 * <h2>时刻分组（对应需求里的每一栏）</h2>
 * <table>
 *   <tr><th>需求栏</th><th>时刻常量</th><th>触发方式</th></tr>
 *   <tr><td>空闲 + 自言自语</td><td>{@link #MOMENT_IDLE}</td><td>基类自己计时（安静约 3 秒后随机吐一句）</td></tr>
 *   <tr><td>登场</td><td>{@link #MOMENT_SPAWN}</td><td>基类 {@code start()}</td></tr>
 *   <tr><td>主武器命中</td><td>{@link #MOMENT_WEAPON_HIT}</td><td>影切：拔刀左键命中 / **主动**居合命中（防御居合不喊，见下）</td></tr>
 *   <tr><td>技能一 · 释放</td><td>{@link #MOMENT_GRENADE_CAST}</td><td>榴弹发射时</td></tr>
 *   <tr><td>技能一 · 命中/持续</td><td>{@link #MOMENT_GRENADE_FIELD}</td><td>稳态场结算真的打到敌人时</td></tr>
 *   <tr><td>技能二 · 首次 / 刷新 / 最终段</td>
 *       <td>{@link #MOMENT_CHAIN_FIRST} / {@link #MOMENT_CHAIN_REFRESH} / {@link #MOMENT_CHAIN_FINAL}</td>
 *       <td>按段号分派（三段是**不同事件**，各自一个时刻 —— 不会同时触发）</td></tr>
 *   <tr><td>大招 · 起手 / 一 / 二 / 三 / 收尾</td>
 *       <td>{@link #MOMENT_STORM_START} · {@link #MOMENT_STORM_STAGE_1} · {@link #MOMENT_STORM_STAGE_2}
 *           · {@link #MOMENT_STORM_STAGE_3} · {@link #MOMENT_STORM_END}</td>
 *       <td>风暴组件按段喊；★ 第一段推迟到起手后 1 秒（见下）</td></tr>
 *   <tr><td>杀敌</td><td>{@link #MOMENT_KILL}</td><td>基类击杀订阅（一栏一组，无需分档）</td></tr>
 *   <tr><td>生命值低</td><td>{@link #MOMENT_LOW_HP}</td><td>基类（受伤后血量 ≤35% 触发）</td></tr>
 *   <tr><td>能量耗尽</td><td>{@link #MOMENT_ENERGY_OUT}</td><td>各组件**能量不足被拒**时喊（口径申报二）</td></tr>
 *   <tr><td>失衡爆发触发</td><td>{@link #MOMENT_BURST}</td><td>笼手被动在爆发开场结算时喊</td></tr>
 *   <tr><td>能量回复 / 笼手充能</td><td>{@link #MOMENT_CHARGE_GAIN}</td><td>笼手充能**自然回复**那一刻</td></tr>
 *   <tr><td>被控制 / 失神</td><td>{@link #MOMENT_CONTROLLED}</td>
 *       <td>本类**边沿判据**：技能闸门从"关"变"开"的那一刻（刚回过神）</td></tr>
 *   <tr><td>受击</td><td>{@link #MOMENT_HURT}</td><td>基类（框架受伤扇出）</td></tr>
 *   <tr><td>死亡</td><td>{@link #MOMENT_DEATH}</td>
 *       <td>★ 在 {@code onDamaged} 里判"这一下把血打到 0"——同步在死亡事件里喊，
 *           赶在框架清角色之前（角色一清，池就关了）</td></tr>
 * </table>
 *
 * <h2>★★ 口径申报一：大招"起手"与"一。"的节奏不重叠</h2>
 * 起手台词在施放当刻、第一段在 <b>1 秒后</b>（风暴组件把首段推迟一个间隔）。
 * 若首段即刻结算，两条台词会落在同一刻 ⇒ 池的节流会**随机丢掉一句**（不可复现）。
 * 推迟 1 秒后，起手与"一。/ 二。/ 三。"恰好各隔 20 刻 ⇒ 三段数拍干净利落。
 *
 * <h2>★★ 口径申报二：「能量耗尽」= 组件被拒的那一下，而不是血量式边沿</h2>
 * 能量掉到 0 之后还会被内建被动缓慢回满，做"归零边沿"会误报、还会与技能台词抢节流。
 * 因此本组台词由**各组件在"因能量不足被拒"的分支**喊（先例：马提娜的"无目标"由主武器喊）。
 *
 * <h2>★★ 口径申报三：防御居合不喊"主武器命中"</h2>
 * 防御居合与"挨打"同刻发生（受伤扇出 + 反击结算），两条台词会在同一刻抢节流 ⇒ 随机丢一句。
 * ⇒ 该路径**只保留基类的受击台词**（确定性取舍）；主动居合 / 拔刀左键照常喊「主武器命中」。
 *
 * <h2>性能</h2>
 * 悬浮文本是"每字一个实体"。高频时刻（命中 / 场结算）走 {@link #saySpecial(Player, String)}
 * 的内部节流（{@value #COMBAT_LINE_GAP_TICKS} 刻一条），叠在池自带的 {@code MIN_GAP_TICKS} 之上；
 * 台词一律 ≤ {@link TextUtil#MAX_CHARS} 字（唯一一句超长需求句已按此上限收编）。
 */
public class SayakaFloatingTextComponent extends FloatingTextComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "sayaka_floating_text";

    // ───────── 时刻标识（需求逐栏） ─────────

    /** 主武器命中（拔刀左键 / 主动居合）。 */
    public static final String MOMENT_WEAPON_HIT = "sayaka_weapon_hit";

    /** 技能一：榴弹释放。 */
    public static final String MOMENT_GRENADE_CAST = "sayaka_grenade_cast";

    /** 技能一：稳态场命中 / 持续期间。 */
    public static final String MOMENT_GRENADE_FIELD = "sayaka_grenade_field";

    /** 技能二：首次释放。 */
    public static final String MOMENT_CHAIN_FIRST = "sayaka_chain_first";

    /** 技能二：刷新时。 */
    public static final String MOMENT_CHAIN_REFRESH = "sayaka_chain_refresh";

    /** 技能二：第四次 / 最终段。 */
    public static final String MOMENT_CHAIN_FINAL = "sayaka_chain_final";

    /** 大招：起手。 */
    public static final String MOMENT_STORM_START = "sayaka_storm_start";

    /** 大招：第一段。 */
    public static final String MOMENT_STORM_STAGE_1 = "sayaka_storm_stage1";

    /** 大招：第二段。 */
    public static final String MOMENT_STORM_STAGE_2 = "sayaka_storm_stage2";

    /** 大招：第三段。 */
    public static final String MOMENT_STORM_STAGE_3 = "sayaka_storm_stage3";

    /** 大招：收尾。 */
    public static final String MOMENT_STORM_END = "sayaka_storm_end";

    /** 失衡爆发触发。 */
    public static final String MOMENT_BURST = "sayaka_burst";

    /** 能量耗尽（被拒时喊）。 */
    public static final String MOMENT_ENERGY_OUT = "sayaka_energy_out";

    /** 能量回复 / 笼手充能。 */
    public static final String MOMENT_CHARGE_GAIN = "sayaka_charge_gain";

    /** 被控制 / 失神（刚回过神）。 */
    public static final String MOMENT_CONTROLLED = "sayaka_controlled";

    /** 死亡。 */
    public static final String MOMENT_DEATH = "sayaka_death";

    // ───────── 节奏与外观参数 ─────────

    /** 全组最小缩放（比默认 1.0 大一档，但不喧宾夺主）。 */
    public static final float MIN_SCALE = 1.25f;

    /** 命中 / 状态类的高频台词内部节流（刻）——约 1 秒最多一句。 */
    public static final int COMBAT_LINE_GAP_TICKS = 20;

    // ───────── 主题色（黑红：烬红 / 暗红 / 焦黑红 / 灰白） ─────────

    /** 主色·烬红。 */
    private static final int CRIMSON = 0xD0162A;

    /** 暗红。 */
    private static final int DEEP_RED = 0x8C0F1E;

    /** 焦黑红（失神 / 恍惚档）。 */
    private static final int BLACK_RED = 0x52070F;

    /** 灰白（疲惫档）。 */
    private static final int ASH = 0x9C8E90;

    /** 亮白（大招数拍）。 */
    private static final int PALE = 0xF3E8E9;

    // ───────── 运行状态 ─────────

    /** 上一次说"命中 / 状态类"台词的刻（{@code -1000} = 从未）。 */
    private int lastCombatLineTick = -1000;

    /** 上一刻技能闸门是否打开（边沿判据 ⇒ 解控）。 */
    private boolean castReadyLastTick;

    /** 是否见过"技能闸门关闭"（避免登录第一刻平白喊一句）。 */
    private boolean controlSeenClosed;

    /** 这次受伤是否已经喊过死亡台词（防止同一具尸体连喊）。 */
    private boolean deathSaid;

    public SayakaFloatingTextComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    // ───────── 对外：技能 / 武器喊话的入口 ─────────

    /**
     * **技能台词**（一次连说 1~2 句）——技能在 {@code onCast} / 结算点调它。
     *
     * @return 实际排入的句数
     */
    public int saySkill(Player who, String moment) {
        if (moment == null) {
            return 0;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now - lastCombatLineTick < COMBAT_LINE_GAP_TICKS) {
            return 0;       //与命中台词共用同一道闸门 ⇒ 打得很密时不会"技能+命中"叠字
        }
        lastCombatLineTick = now;
        return sayBurst(who, moment, 1, 2, castBurstGapTicks());
    }

    /**
     * **状态 / 命中台词**（单句，自带内部节流）。
     *
     * @return 是否真的放出去了
     */
    public boolean saySpecial(Player who, String moment) {
        if (moment == null) {
            return false;
        }
        int now = org.bukkit.Bukkit.getCurrentTick();
        if (now - lastCombatLineTick < COMBAT_LINE_GAP_TICKS) {
            return false;
        }
        lastCombatLineTick = now;
        return say(who, moment, null);
    }

    // ───────── 每刻：失神恢复的自观察 ─────────

    /**
     * 每刻：先做一条**自观察**（技能闸门从"关"变"开"= 刚回过神），再交给基类跑空闲逻辑。
     * ★ 边沿判据（存上一帧的布尔），否则会退化成"每刻复读"。
     */
    @Override
    public void update() {
        Player me = me();
        if (me != null && me.isOnline()) {
            observeControlReleased(me);
        }
        super.update();
    }

    /** 被控制 / 失神：技能闸门从"关"变"开"的那一刻 = 刚回过神。 */
    private void observeControlReleased(Player me) {
        BuffComponent buff = findComponent(BuffComponent.class);
        boolean ready = buff == null || buff.canCastSkill();
        if (ready && !castReadyLastTick && controlSeenClosed) {
            say(me, MOMENT_CONTROLLED, null);
        }
        if (!ready) {
            controlSeenClosed = true;
        }
        castReadyLastTick = ready;
    }

    // ───────── 承受方回调：受伤 / 濒死 / 死亡 ─────────

    /**
     * 受伤通知（框架扇出；只观察）。
     *
     * <p>★ **死亡台词在这里喊**：这一下伤害把血打到 0 时，本方法是在死亡事件的同步路径里被调到的
     * ——**早于**框架清角色（清角色会把本组件的池关掉，之后再喊就没地方放了）。
     */
    @Override
    public void onDamaged(Player source, double amount) {
        Player me = me();
        if (me != null && me.getHealth() <= 0d && !deathSaid) {
            deathSaid = true;
            say(me, MOMENT_DEATH, null);
            return;     //临终不叠"受伤"这一句
        }
        super.onDamaged(source, amount);
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        deathSaid = false;
        castReadyLastTick = true;
        controlSeenClosed = false;
        lastCombatLineTick = -1000;
        super.start();
    }

    // ───────── 钩子覆写：词库 / 风格 / 观感 / 节奏 ─────────

    @Override
    protected TextUtil.Lexicon lexicon() {
        return LEXICON;
    }

    /**
     * ★ **风格**：按语义挑 19 种 —— 空闲克制、命中短促、数拍脉冲、失神乱码、死亡消散。
     */
    @Override
    protected TextUtil.Style styleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> TextUtil.Style.PLAIN_FADE;                       //空闲：克制
            case MOMENT_SPAWN -> TextUtil.Style.GROW_SLOW;                       //登场：缓缓放大
            case MOMENT_WEAPON_HIT -> TextUtil.Style.POP_SCALE;                  //命中：一记短促
            case MOMENT_GRENADE_CAST -> TextUtil.Style.SLIDE_LEFT_IN;            //投弹：横向滑出
            case MOMENT_GRENADE_FIELD -> TextUtil.Style.CENTER_FADE_IN_RANDOM_OUT;   //场压制：中心扩散
            case MOMENT_CHAIN_FIRST -> TextUtil.Style.SPIN_RIGHT_IN;             //首斩：旋转突进
            case MOMENT_CHAIN_REFRESH -> TextUtil.Style.POP_STRETCH_Y;           //连段：纵向弹开
            case MOMENT_CHAIN_FINAL -> TextUtil.Style.POP_STRETCH_X;             //收段：横向拉扯
            case MOMENT_STORM_START -> TextUtil.Style.CENTER_IN_CENTER_OUT;      //起手：居中打字
            case MOMENT_STORM_STAGE_1, MOMENT_STORM_STAGE_2, MOMENT_STORM_STAGE_3 ->
                    TextUtil.Style.PULSE_Y_IN;                                   //数拍：逐字脉冲
            case MOMENT_STORM_END -> TextUtil.Style.RANDOM_FADE_IN_RANDOM_OUT;   //收尾：零散隐现
            case MOMENT_BURST -> TextUtil.Style.DECODE_IN;                       //爆发：乱码显形
            case MOMENT_ENERGY_OUT -> TextUtil.Style.TYPEWRITER_FADE;            //能量空：打字机
            case MOMENT_CHARGE_GAIN -> TextUtil.Style.SHRINK_IN;                 //充能到账：收缩浮现
            case MOMENT_CONTROLLED -> TextUtil.Style.TYPEWRITER_BACKSPACE_BOTH;  //失神：断续退格
            case MOMENT_HURT -> TextUtil.Style.FLIP_RANDOM_Y_IN;                 //受击：翻面
            case MOMENT_LOW_HP -> TextUtil.Style.TYPEWRITER_BACKSPACE;           //濒死：单侧退格
            case MOMENT_KILL -> TextUtil.Style.FLOAT_UP_IN;                      //送别：上浮
            case MOMENT_DEATH -> TextUtil.Style.OBFUSCATE_OUT;                   //死亡：模糊消散
            default -> TextUtil.Style.PLAIN_FADE;
        };
    }

    /**
     * ★ **外观**：**每一组都 {@code withBold()}**；黑红配色按语气分档
     * （命中=烬红 / 大招数拍=亮白 / 疲态=灰白 / 危机=暗红颤抖 / 失神=焦黑红）。
     */
    @Override
    protected TextUtil.Look lookFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> TextUtil.Look.colored(ASH).withBold();
            case MOMENT_SPAWN -> TextUtil.Look.glowOf(CRIMSON).withBold();
            case MOMENT_WEAPON_HIT -> TextUtil.Look.colored(CRIMSON).withBold();
            case MOMENT_GRENADE_CAST -> TextUtil.Look.colored(DEEP_RED).withBold();
            case MOMENT_GRENADE_FIELD -> TextUtil.Look.gradientOf(CRIMSON).withBold();
            case MOMENT_CHAIN_FIRST -> TextUtil.Look.glowOf(CRIMSON).withBold();
            case MOMENT_CHAIN_REFRESH -> TextUtil.Look.colored(CRIMSON).withBold();
            case MOMENT_CHAIN_FINAL -> TextUtil.Look.gradientOf(CRIMSON).withTremble().withBold();
            case MOMENT_STORM_START -> TextUtil.Look.glowOf(CRIMSON).withBold();
            case MOMENT_STORM_STAGE_1, MOMENT_STORM_STAGE_2, MOMENT_STORM_STAGE_3 ->
                    TextUtil.Look.colored(PALE).withBold();
            case MOMENT_STORM_END -> TextUtil.Look.gradientOf(DEEP_RED).withBold();
            case MOMENT_BURST -> TextUtil.Look.glowOf(CRIMSON).withTremble().withBold();
            case MOMENT_ENERGY_OUT -> TextUtil.Look.colored(ASH).withBold();
            case MOMENT_CHARGE_GAIN -> TextUtil.Look.colored(CRIMSON).withBold();
            case MOMENT_CONTROLLED -> TextUtil.Look.colored(BLACK_RED).withTremble().withBold();
            case MOMENT_HURT -> TextUtil.Look.colored(DEEP_RED).withBold();
            case MOMENT_LOW_HP -> TextUtil.Look.colored(DEEP_RED).withTremble().withBold();
            case MOMENT_KILL -> TextUtil.Look.gradientOf(CRIMSON).withBold();
            case MOMENT_DEATH -> TextUtil.Look.gradientOf(DEEP_RED).withBold();
            default -> TextUtil.Look.colored(ASH).withBold();
        };
    }

    /** ★ **字号**：全部 ≥ {@link #MIN_SCALE}；越"重"越大（数拍 1.60 / 爆发 1.55 / 送别 1.50）。 */
    @Override
    protected float scaleFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> 1.25f;
            case MOMENT_ENERGY_OUT, MOMENT_CHARGE_GAIN -> 1.30f;
            case MOMENT_GRENADE_FIELD -> 1.35f;
            case MOMENT_HURT -> 1.35f;
            case MOMENT_WEAPON_HIT, MOMENT_LOW_HP, MOMENT_CONTROLLED -> 1.40f;
            case MOMENT_SPAWN, MOMENT_DEATH -> 1.45f;
            case MOMENT_GRENADE_CAST, MOMENT_CHAIN_REFRESH, MOMENT_CHAIN_FINAL,
                 MOMENT_STORM_END, MOMENT_KILL -> 1.50f;
            case MOMENT_CHAIN_FIRST, MOMENT_BURST -> 1.55f;
            case MOMENT_STORM_START, MOMENT_STORM_STAGE_1, MOMENT_STORM_STAGE_2,
                 MOMENT_STORM_STAGE_3 -> 1.60f;
            default -> MIN_SCALE;
        };
    }

    /** 节奏：命中/技能偏快，数拍最快，独处与死亡最慢。 */
    @Override
    protected int speedFor(String moment) {
        String key = moment == null ? "" : moment;
        return switch (key) {
            case MOMENT_IDLE -> 95;
            case MOMENT_DEATH -> 70;
            case MOMENT_STORM_STAGE_1, MOMENT_STORM_STAGE_2, MOMENT_STORM_STAGE_3 -> 160;
            case MOMENT_WEAPON_HIT, MOMENT_CHAIN_REFRESH, MOMENT_CHAIN_FINAL -> 140;
            case MOMENT_GRENADE_CAST, MOMENT_CHAIN_FIRST, MOMENT_STORM_START,
                 MOMENT_STORM_END, MOMENT_BURST -> 150;
            default -> 115;
        };
    }

    /** 空闲 / 濒死都启用（本角色的语音量本来就该多）。 */
    @Override
    protected boolean idleEnabled() {
        return true;
    }

    @Override
    protected boolean lowHpEnabled() {
        return true;
    }

    // ───────── 内部工具 ─────────

    /** 自己（拿不到 ⇒ {@code null}）。 */
    private Player me() {
        try {
            return svc().self() == null ? null : svc().self().player();
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ───────── 词库（需求逐句收编；受击 / 登场 / 死亡三组按同一语气自拟补全） ─────────

    private static final String[] IDLE_LINES = {
            "今天天气不错。",
            "诶，到我上场了吗？",
            "肚子有点饿了。",
            "一定要平安无事啊，一定会的。",
            "（哼歌）……啊，忘了叫什么名字了，这首。",
            "笼手好用是好用，就是脑子里会嗡嗡响。习惯了。",
            "活着真好。虽然累，但真好。",
            "刚才走神了。几秒？不知道。别告诉别人。",
            "有人需要帮忙吗？说一声。",
            "影尘的味道……像铁锈，又像雨。挺奇怪的。",
            "今天也活下来了。",
            "明天再说吧。",
            "好累。但还行。",
            "……再等等。快好了。"
    };

    private static final String[] SPAWN_LINES = {
            "芹泽沙耶香，到场。",
            "影切就位，开始吧。",
            "这边交给我。"
    };

    private static final String[] WEAPON_HIT_LINES = {
            "中！",
            "别动。",
            "一次不够，再来。",
            "失衡了。稳住。",
            "你挡不住我的。",
            "退后。",
            "如何。",
            "嗯，打得不错。",
            "再来一刀。",
            "站稳了。"
    };

    private static final String[] GRENADE_CAST_LINES = {
            "撑住这片区域。",
            "落点确认。",
            "别往那边去了。",
            "我这里安全，过来。",
            "站稳，我来压制。"
    };

    private static final String[] GRENADE_FIELD_LINES = {
            "失衡了。趁现在。",
            "别让它爬起来。",
            "压制住了。继续。",
            "好，就这样。"
    };

    private static final String[] CHAIN_FIRST_LINES = {
            "让开！",
            "借过。",
            "对不起，借你身体用一下。",
            "我很快的。"
    };

    private static final String[] CHAIN_REFRESH_LINES = {
            "还没完。",
            "再来！",
            "别急，还没到最后。",
            "还有呢。"
    };

    private static final String[] CHAIN_FINAL_LINES = {
            "最后一下。",
            "够你受了。",
            "收尾。"
    };

    private static final String[] STORM_START_LINES = {
            "全部让开。",
            "喝啊！！！",
            "别站在我前面。",
            "别小瞧我啊。"
    };

    private static final String[] STORM_1_LINES = {
            "一。",
            "别倒。"
    };

    private static final String[] STORM_2_LINES = {
            "二。",
            "还站着？"
    };

    private static final String[] STORM_3_LINES = {
            "三。",
            "结束了。"
    };

    private static final String[] STORM_END_LINES = {
            "搞定。下一个。",
            "都倒了？那走吧。",
            "累死了。"
    };

    private static final String[] BURST_LINES = {
            "就是现在！",
            "时机到了。",
            "快上！",
            "全部——动起来！"
    };

    private static final String[] ENERGY_OUT_LINES = {
            "笼手……没转起来。",
            "等我一下。缓一缓就好。",
            "能量空了。靠你们了。",
            "给我几秒。"
    };

    private static final String[] CHARGE_GAIN_LINES = {
            "好了。",
            "回来了。",
            "还能打。",
            "继续。"
    };

    private static final String[] CONTROLLED_LINES = {
            "……啊。",
            "等等。我没事。",
            "刚才怎么了？",
            "咕，我的头。",
            "我还在。"
    };

    private static final String[] HURT_LINES = {
            "嘶——没事。",
            "擦到了。继续。",
            "小伤。别慌。",
            "别管我，稳住你自己。"
    };

    private static final String[] LOW_HP_LINES = {
            "还行……死不了。",
            "小伤。别管我。",
            "我还能打。",
            "别过来！我自己的事。",
            "……有点疼。没事。"
    };

    private static final String[] KILL_LINES = {
            "对不起。",
            "走好。",
            "我会记住你的。",
            "又一个……没事。我扛得住。",
            "不疼了。睡吧。",
            "你尽力了。",
            "别怕。已经结束了。",
            "我送你回去。",
            "（轻声）……安息。"
    };

    private static final String[] DEATH_LINES = {
            "……这次，真的站不起来了。",
            "对不起……剩下的交给你们。",
            "终于……能歇会儿了。"
    };

    /** 整本词库（各时刻一组；击杀单组、无需分档 ⇒ 不需要覆写 {@code lexiconNow()}）。 */
    private static final TextUtil.Lexicon LEXICON = lexiconOf(
            MOMENT_IDLE, IDLE_LINES,
            MOMENT_SPAWN, SPAWN_LINES,
            MOMENT_WEAPON_HIT, WEAPON_HIT_LINES,
            MOMENT_GRENADE_CAST, GRENADE_CAST_LINES,
            MOMENT_GRENADE_FIELD, GRENADE_FIELD_LINES,
            MOMENT_CHAIN_FIRST, CHAIN_FIRST_LINES,
            MOMENT_CHAIN_REFRESH, CHAIN_REFRESH_LINES,
            MOMENT_CHAIN_FINAL, CHAIN_FINAL_LINES,
            MOMENT_STORM_START, STORM_START_LINES,
            MOMENT_STORM_STAGE_1, STORM_1_LINES,
            MOMENT_STORM_STAGE_2, STORM_2_LINES,
            MOMENT_STORM_STAGE_3, STORM_3_LINES,
            MOMENT_STORM_END, STORM_END_LINES,
            MOMENT_BURST, BURST_LINES,
            MOMENT_ENERGY_OUT, ENERGY_OUT_LINES,
            MOMENT_CHARGE_GAIN, CHARGE_GAIN_LINES,
            MOMENT_CONTROLLED, CONTROLLED_LINES,
            MOMENT_HURT, HURT_LINES,
            MOMENT_LOW_HP, LOW_HP_LINES,
            MOMENT_KILL, KILL_LINES,
            MOMENT_DEATH, DEATH_LINES
    );

    // ───────── 探针读口（同包离线单测用；都是只读，不改任何状态） ─────────

    /** **本角色全部时刻**（按需求的分栏顺序）——单测据此遍历"每一组都要有台词 / 都要加粗 / 都要放大"。 */
    static final List<String> ALL_MOMENTS = List.of(
            MOMENT_IDLE, MOMENT_SPAWN, MOMENT_WEAPON_HIT,
            MOMENT_GRENADE_CAST, MOMENT_GRENADE_FIELD,
            MOMENT_CHAIN_FIRST, MOMENT_CHAIN_REFRESH, MOMENT_CHAIN_FINAL,
            MOMENT_STORM_START, MOMENT_STORM_STAGE_1, MOMENT_STORM_STAGE_2, MOMENT_STORM_STAGE_3,
            MOMENT_STORM_END, MOMENT_BURST, MOMENT_ENERGY_OUT, MOMENT_CHARGE_GAIN,
            MOMENT_CONTROLLED, MOMENT_HURT, MOMENT_LOW_HP, MOMENT_KILL, MOMENT_DEATH);

    /** **全部台词**（按组展平）——校验"每句都放得进单段字数上限"与"一句不重"。 */
    static List<String> allLines() {
        List<String> out = new java.util.ArrayList<>();
        for (String[] group : List.of(IDLE_LINES, SPAWN_LINES, WEAPON_HIT_LINES,
                GRENADE_CAST_LINES, GRENADE_FIELD_LINES, CHAIN_FIRST_LINES, CHAIN_REFRESH_LINES,
                CHAIN_FINAL_LINES, STORM_START_LINES, STORM_1_LINES, STORM_2_LINES, STORM_3_LINES,
                STORM_END_LINES, BURST_LINES, ENERGY_OUT_LINES, CHARGE_GAIN_LINES,
                CONTROLLED_LINES, HURT_LINES, LOW_HP_LINES, KILL_LINES, DEATH_LINES)) {
            out.addAll(List.of(group));
        }
        return List.copyOf(out);
    }

    // ───────── 描述符 ─────────

    /**
     * 本组件的被动描述符。
     *
     * <p>★ 泛型自类型（{@code Specification<自类>}）是硬要求 —— 否则对外声明的类型停在
     * {@code FloatingTextComponent}，别的组件 {@code requires(SayakaFloatingTextComponent.class)}
     * 会报"missing component type"（工程踩过，见 §4）。
     */
    public static final class Specification
            extends FloatingTextComponent.Specification<SayakaFloatingTextComponent> {

        public Specification() {
            super(Component.text("芹泽沙耶香语音"),
                    List.of(
                            Component.text("空闲与自言自语：14 句（疲惫但温吞的独白）"),
                            Component.text("主武器命中：10 句；受击 4 句 / 濒死 5 句 / 失神恢复 5 句"),
                            Component.text("榴弹：释放 5 句 / 场压制 4 句；连斩：首斩 4 / 刷新 4 / 收尾 3 句"),
                            Component.text("大招：起手 4 句 + 数拍「一/二/三」各 2 句 + 收尾 3 句"),
                            Component.text("状态：失衡爆发 4 句 / 能量耗尽 4 句 / 充能到账 4 句 / 死亡 3 句"),
                            Component.text("击杀：9 句（「对不起。走好。」的送别口吻）"),
                            Component.text("全部台词加粗、按语义选用 19 种浮现风格；黑红主题配色")
                    ));
            //★ 击杀订阅 / 受伤扇出都要它（基类走 getComponent，这里显式声明以堵住装配期漏配）
            requires(VitalsComponent.class);
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new SayakaFloatingTextComponent(id, services, this);
        }
    }
}
