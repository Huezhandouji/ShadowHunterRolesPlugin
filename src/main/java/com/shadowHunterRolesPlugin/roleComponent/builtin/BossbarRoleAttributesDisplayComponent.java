package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.List;

/**
 * 角色属性 BOSS 条：显示 TE（SanTE）与能量。
 *
 * <h2>基础形态（未识别阵营的兜底）</h2>
 * 名字 = {@code "TE 74/100"}（SHADOW）或 {@code "SAN 74/100 || ENERGY 40/100"}（HUNTER）。
 *
 * <h2>★ 增强视觉（2026-10-30 冷识首发；2026-10-31 全量推广到两个阵营）</h2>
 * <b>影都（SHADOW）</b> —— 按冷识 {@code lethe} 的既有画法，一字不变：
 * <ul>
 *   <li><b>两侧流动箭头</b>：标题最左 {@code >>>>>>}、最右 {@code <<<<<<}，每
 *       {@value #ANIM_INTERVAL_TICKS} 刻（0.2 秒）移动一格高亮；箭头**底色随 TE 渐变**：
 *       满 TE = 亮紫，TE 越低越灰（见 {@link #teArrowColor}）；</li>
 *   <li><b>标题属性色</b>：SHADOW 的 TE = **亮黄**；</li>
 *   <li><b>BOSS 条本身 = TE 血量</b>：{@code progress = current / max}；</li>
 *   <li><b>惩罚态</b>（TE 归零）：金黄色乱码 + 会移动的「TE」单词，条颜色转金黄、进度归零。</li>
 * </ul>
 *
 * <b>猎影人（HUNTER）</b> —— 2026-10-31 新画法（用户需求）：
 * <ul>
 *   <li><b>最边缘两侧金色箭头</b>：左 {@code >>>>>}、右 {@code <<<<<}（各 {@value #HUNTER_ARROW_LENGTH} 个），
 *       带**白色涌动高亮**（每 0.2 秒移动一格）；</li>
 *   <li><b>箭头数量随 SAN 减少</b>：满 SAN = {@value #HUNTER_ARROW_LENGTH} 个、越少越少、SAN 归零 = 不显示箭头
 *       （见 {@link #hunterArrowCount}）；</li>
 *   <li><b>标题</b>：{@code SAN 74/100}（**亮黄**）+ {@code || ENERGY 40/100}（绿，保留旧画法）
 *       —— "SAN 之后依然显示能量值"；</li>
 *   <li><b>BOSS 条本身 = SAN 血量</b>：{@code progress = current / max}（SAN 越少条越短）；</li>
 *   <li>HUNTER **不进入乱码惩罚态**：SAN 归零时仍显示 {@code SAN 0/100 || ENERGY ...}（能量值不能丢）。</li>
 * </ul>
 *
 * <p><b>为什么做成纯函数</b>：{@link #enhancedTitle} / {@link #hunterEnhancedTitle} /
 * {@link #flowingArrows} / {@link #hunterFlowingArrows} / {@link #punishmentTitle} 都是
 * "输入数值与帧号 → 输出组件"的纯函数，因此可以在无服务器环境下逐帧断言箭头动画与惩罚乱码
 * （见 {@code BossbarEnhancedVisualTest}）。
 *
 * <p><b>★ 边界申报</b>：本类位于 {@code roleComponent/builtin/}（平时禁改区）。本次是用户明确要求的
 * BOSS 条视觉改造（2026-10-31），只改"标题 / 进度 / 颜色"的表现面，不动组件契约、不动框架。
 */
public class BossbarRoleAttributesDisplayComponent extends PassiveSkill {

    public final static String ID = "bossbarRoleAttributesDisplayPassive";

    /** 每 4 刻（0.2 秒）重绘一次标题：流动箭头与惩罚乱码都是逐帧动画。 */
    static final int ANIM_INTERVAL_TICKS = 4;

    /** 影都（SHADOW）标题两侧箭头的长度。 */
    static final int ARROW_LENGTH = 6;

    /** 猎影人（HUNTER）标题两侧箭头的长度（需求："最边缘两边有金色的 >>>>> <<<<<"，2026-10-31 由 3 增到 5）。 */
    static final int HUNTER_ARROW_LENGTH = 5;

    /** 惩罚态乱码长度（不含会移动的「TE」两字）。 */
    static final int GARBLE_LENGTH = 12;

    /** 惩罚态乱码池（MC 默认字体可渲染的几何 / 方块符号）。 */
    static final String GARBLE_POOL = "░▒▓█▚▞▙▟◤◥◣◢⌁";

    /**
     * **哪些阵营吃增强画法**（2026-10-31 全量推广）：
     * SHADOW = 冷识既有画法（紫箭头 + TE）；HUNTER = 金色箭头 + SAN + ENERGY。
     * UNKNOWN（没阵营的兜底）仍走旧画法。
     */
    static boolean enhancedFor(Faction faction) {
        return faction == Faction.SHADOW || faction == Faction.HUNTER;
    }

    private EnergyComponent energy;
    private SanTEComponent sante;

    /** 阵营：决定标题里属性名（TE / SAN）与它的文字颜色（见 {@link #attributeTitleColor}）。 */
    private Faction faction;

    private BossBar bossbar;

    /** 本实例是否走增强画法（见 {@link #enhancedFor}）。 */
    private boolean enhanced;

    /** 动画计数（逐 tick 累加，只在 {@link #ANIM_INTERVAL_TICKS} 的整数倍推进一帧）。 */
    private int animTicks;

    /** 当前动画帧号（流动箭头与乱码都按它取相位）。 */
    private int frame;

    public BossbarRoleAttributesDisplayComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification<BossbarRoleAttributesDisplayComponent> {


        public Specification() {
            super(Component.text("bossbar角色属性数值显示"),
                    List.of(Component.text("ccb")));
            //阵营分支（配色与显示名）读阵营组件；缺它则本组件不做展示 —— 装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public BossbarRoleAttributesDisplayComponent create(String id, ComponentServicesPort services) {
            return new BossbarRoleAttributesDisplayComponent(id, services, this);
        }
    }


    @Override
    public void start(){
        energy = svc().components().get(EnergyComponent.class);
        sante = svc().components().get(SanTEComponent.class);

        if(energy == null && sante == null){
            svc().components().remove(getClass());
        }

        faction = svc().components().get(FactionComponent.class).faction();
        if(faction == Faction.SHADOW){
            energy = null;
        }

        //增强视觉按阵营推广（2026-10-31）：影都走冷识画法、猎影人走金色画法
        enhanced = enhancedFor(faction);
        animTicks = 0;
        frame = 0;

        bossbar = BossBar.bossBar(
                Component.empty(),
                0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );

        svc().self().player().showBossBar(bossbar);
    }

    @Override
    public void update(){
        if (enhanced) {
            updateEnhanced();
            return;
        }
        // ───────── 旧画法（非增强角色）：一字不变 ─────────
        Component newName = Component.empty();
        if(sante != null){
            switch (svc().components().get(FactionComponent.class).faction()){
                case Faction.SHADOW -> {
                    newName = newName.append(Component.text("TE " + sante.current() + "/" + sante.max()).color(NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
                }
                case Faction.HUNTER -> {
                    newName = newName.append(Component.text("SAN " + sante.current() + "/" + sante.max()).color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
                }
            }
        }
        if(energy != null){
            newName = newName.append(Component.text(" || ").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            newName = newName.append(Component.text("ENERGY " + energy.current() + "/" + energy.max()).color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }

        bossbar.name(newName);
    }

    /**
     * 增强画法：进度条 = 当前阵营的"血量"（SHADOW 看 TE、HUNTER 看 SAN；每 tick 更新，数值变化立刻可见）；
     * 标题每 {@value #ANIM_INTERVAL_TICKS} 刻重建一次（箭头与乱码的逐帧动画）。
     */
    private void updateEnhanced() {
        if (bossbar == null || sante == null) {
            return;
        }
        int current = sante.current();
        int max = sante.max();
        boolean hunter = faction == Faction.HUNTER;
        //猎影人**不进入乱码惩罚态**：SAN 归零时仍要显示 SAN/ENERGY（见类注释）
        boolean punishing = !hunter && current <= 0;

        //① BOSS 条本身 = TE / SAN 血量：越少越短；影都惩罚态归零
        bossbar.progress(punishing ? 0f : ratio(current, max));
        bossbar.color(punishing ? BossBar.Color.YELLOW : BossBar.Color.PURPLE);

        //② 标题逐帧：0.2 秒一帧
        if (animTicks % ANIM_INTERVAL_TICKS == 0) {
            if (hunter) {
                int energyCurrent = energy == null ? 0 : energy.current();
                int energyMax = energy == null ? 0 : energy.max();
                bossbar.name(hunterEnhancedTitle(current, max, energyCurrent, energyMax, frame));
            } else {
                bossbar.name(enhancedTitle(current, max, frame, faction));
            }
            frame++;
        }
        animTicks++;
    }

    /** TE 比例（0~1，max 非法时回 0）。 */
    static float ratio(int current, int max) {
        if (max <= 0) {
            return 0f;
        }
        return Math.max(0f, Math.min(1f, current / (float) max));
    }

    /**
     * **影都（SHADOW）增强标题的纯函数**（离线可测）：{@code >>>>>> TE 74/100 <<<<<<}；
     * TE 归零时换成金黄色乱码 + 移动的「TE」。猎影人（HUNTER）不走这里 —— 见
     * {@link #hunterEnhancedTitle}（2026-10-31 起两个阵营各一套画法）。
     *
     * <p>配色口径（2026-10-30 用户裁定，**只改标题、不动 BOSS 条本身的颜色**）：
     * <ul>
     *   <li>属性名与数值：TE = **亮黄**（见 {@link #attributeTitleColor}）；</li>
     *   <li>两侧箭头底色：满 TE = **亮紫**，随 TE 减少线性褪成深灰（见 {@link #teArrowColor}）；</li>
     *   <li>箭头上的流动高亮保持白色。</li>
     * </ul>
     *
     * @param current TE 当前值
     * @param max     TE 上限
     * @param frame   动画帧号（每 0.2 秒 +1；只影响相位与乱码，不影响数值）
     * @param faction 阵营（仅用于取属性名/颜色；{@code null} 按 SHADOW 处理）
     */
    static Component enhancedTitle(int current, int max, int frame, Faction faction) {
        float teRatio = ratio(current, max);
        TextColor attributeColor = attributeTitleColor(faction);
        String attributeLabel = attributeLabel(faction);
        Component title = Component.empty();
        title = title.append(flowingArrows(true, frame, teRatio));
        if (current <= 0) {
            //惩罚态：金黄色乱码 + 会移动的「TE」单词
            title = title.append(Component.text(" ")).append(punishmentTitle(frame));
        } else {
            title = title.append(Component.text(" " + attributeLabel + " ")
                    .color(attributeColor).decorate(TextDecoration.BOLD));
            title = title.append(Component.text(current + "/" + max)
                    .color(attributeColor).decorate(TextDecoration.BOLD));
        }
        //右箭头前留一个空格：否则中文/数字会贴着「<」，读起来像是数值的一部分
        title = title.append(Component.text(" "));
        title = title.append(flowingArrows(false, frame, teRatio));
        return title;
    }

    /**
     * **猎影人标题的箭头数量**：满 SAN = {@value #HUNTER_ARROW_LENGTH} 个，SAN 越少越少，归零 = 0 个
     * （2026-10-31 用户口径："SAN 越少金色 > 也越少"）。
     * <p>纯函数 ⇒ 可离线穷举：四舍五入到 0..{@value #HUNTER_ARROW_LENGTH}，随 SAN 单调不增。
     */
    static int hunterArrowCount(float sanRatio) {
        float clamped = Math.max(0f, Math.min(1f, sanRatio));
        int count = Math.round(clamped * HUNTER_ARROW_LENGTH);
        return Math.max(0, Math.min(HUNTER_ARROW_LENGTH, count));
    }

    /**
     * 一侧的**金色流动箭头**（猎影人）：{@code count} 个 {@code >} / {@code <}，
     * 底色 = 金色，其中**一格白色高亮**随 {@code frame} 移动（"白色涌动"）。
     * <p>{@code count <= 0} 时返回空组件（SAN 归零就没有箭头）。
     */
    static Component hunterFlowingArrows(boolean left, int frame, int count) {
        Component block = Component.empty();
        if (count <= 0) {
            return block;
        }
        int phase = Math.floorMod(frame, count);
        for (int i = 0; i < count; i++) {
            int index = left ? i : (count - 1 - i);
            boolean lit = index == phase;
            block = block.append(Component.text(left ? ">" : "<")
                    .color(lit ? NamedTextColor.WHITE : NamedTextColor.GOLD)
                    .decorate(TextDecoration.BOLD));
        }
        return block;
    }

    /**
     * **猎影人增强标题的纯函数**（离线可测）：{@code >>>>> SAN 74/100 || ENERGY 40/100 <<<<<}。
     *
     * <p>2026-10-31 用户口径：
     * <ul>
     *   <li>最边缘两侧金色箭头（{@code >>>} / {@code <<<}），带白色涌动高亮；</li>
     *   <li>SAN 越少，金色箭头越少（{@link #hunterArrowCount}）；</li>
     *   <li>SAN 标题色 = **亮黄**；</li>
     *   <li>SAN 之后**依然显示能量值**（与旧画法一致：{@code || ENERGY cur/max}，绿色）；</li>
     *   <li>HUNTER 不做乱码惩罚态 ⇒ SAN 归零时照常显示数值（能量值不能丢）。</li>
     * </ul>
     *
     * @param sanCurrent    SAN 当前值
     * @param sanMax        SAN 上限
     * @param energyCurrent 能量当前值（{@code energyMax <= 0} 视为没有能量组件 ⇒ 不显示该段）
     * @param energyMax     能量上限
     * @param frame         动画帧号（只影响高亮相位）
     */
    static Component hunterEnhancedTitle(int sanCurrent, int sanMax,
                                         int energyCurrent, int energyMax, int frame) {
        int arrows = hunterArrowCount(ratio(sanCurrent, sanMax));
        Component title = Component.empty();
        title = title.append(hunterFlowingArrows(true, frame, arrows));
        if (arrows > 0) {
            title = title.append(Component.text(" "));
        }
        title = title.append(Component.text("SAN ").color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        title = title.append(Component.text(sanCurrent + "/" + sanMax)
                .color(NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        if (energyMax > 0) {
            title = title.append(Component.text(" || ").color(NamedTextColor.WHITE).decorate(TextDecoration.BOLD));
            title = title.append(Component.text("ENERGY " + energyCurrent + "/" + energyMax)
                    .color(NamedTextColor.GREEN).decorate(TextDecoration.BOLD));
        }
        if (arrows > 0) {
            title = title.append(Component.text(" "));
        }
        title = title.append(hunterFlowingArrows(false, frame, arrows));
        return title;
    }

    /**
     * 标题里属性名的颜色：**影都的 TE = 亮黄，猎影人的 SAN = 亮黄**（2026-10-31 用户口径：
     * "SAN 的标题颜色改为亮黄色"）。
     * <p>注意这是**标题文字色**，不是 BOSS 条本身的颜色（条颜色另有其逻辑）。
     */
    static TextColor attributeTitleColor(Faction faction) {
        return NamedTextColor.YELLOW;
    }

    /** 标题里的属性名：SHADOW 显示「TE」、HUNTER 显示「SAN」。 */
    static String attributeLabel(Faction faction) {
        return faction == Faction.HUNTER ? "SAN" : "TE";
    }

    /**
     * 箭头底色：**满 TE = 亮紫（light purple #FF55FF）→ TE 归零 = 深灰（dark gray #555555）**，
     * 中间按 TE 比例线性插值（红/蓝通道抬升、绿通道固定 85）。
     * <p>抽成纯函数的理由与其它视觉参数一致：颜色写错不会报错，只会"看起来没变"。
     */
    static TextColor teArrowColor(float teRatio) {
        float t = Math.max(0f, Math.min(1f, teRatio));
        int red = Math.round(85f + (255f - 85f) * t);
        int green = 85;
        int blue = Math.round(85f + (255f - 85f) * t);
        return TextColor.color(red, green, blue);
    }

    /**
     * 一侧的流动箭头：{@value #ARROW_LENGTH} 个同一方向的箭头，其中**一格高亮**，
     * 高亮位置随 {@code frame} 移动（左侧向右流、右侧向左流，读作"从两端往中间收"）。
     * <p>底色随 TE 高低变化（{@link #teArrowColor}：满 TE 亮紫 → 深灰），高亮恒为白色。
     */
    static Component flowingArrows(boolean left, int frame, float teRatio) {
        TextColor baseColor = teArrowColor(teRatio);
        Component block = Component.empty();
        int phase = Math.floorMod(frame, ARROW_LENGTH);
        for (int i = 0; i < ARROW_LENGTH; i++) {
            int index = left ? i : (ARROW_LENGTH - 1 - i);
            boolean lit = index == phase;
            Component arrow = Component.text(left ? ">" : "<")
                    .color(lit ? NamedTextColor.WHITE : baseColor)
                    .decorate(TextDecoration.BOLD);
            block = block.append(arrow);
        }
        return block;
    }

    /**
     * 惩罚态标题：金黄色乱码为主体，中间嵌一个**会移动的「TE」**（每帧右移一格，走到头回到最左）。
     * <p>乱码用确定性伪随机（{@code frame} 与位置共同决定字符），因此同一帧渲染结果稳定、可离线断言；
     * 帧一变整条就换字，形成"大量金黄色乱码"的闪烁感。
     */
    static Component punishmentTitle(int frame) {
        Component out = Component.empty();
        int tePosition = Math.floorMod(frame, GARBLE_LENGTH);
        for (int i = 0; i < GARBLE_LENGTH; i++) {
            if (i == tePosition) {
                out = out.append(Component.text("TE").color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
                continue;
            }
            char glyph = GARBLE_POOL.charAt(Math.floorMod(i * 31 + frame * 17, GARBLE_POOL.length()));
            NamedTextColor color = (i % 3 == 0) ? NamedTextColor.YELLOW : NamedTextColor.GOLD;
            out = out.append(Component.text(String.valueOf(glyph)).color(color));
        }
        return out;
    }

    @Override
    public void stop(){
        svc().self().player().hideBossBar(bossbar);
        bossbar = null;
        energy = null;
        sante = null;
        enhanced = false;
        animTicks = 0;
        frame = 0;
    }
}
