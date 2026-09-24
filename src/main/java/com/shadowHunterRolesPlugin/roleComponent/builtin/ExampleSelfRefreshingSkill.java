package com.shadowHunterRolesPlugin.roleComponent.builtin;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * **「外观会自己变」的示例组件**（阶段 8 · t46）：本类同时是两项能力的**生产使用点**
 * （C-15 配套②：没有使用点的能力 = 未验证的能力）。
 * <p>
 * <b>它演示的第一件事 = 组件可以「请求重绘」</b>：组件**只请求、不写** —— 它从容器里取
 * {@link HotbarRenderComponent}（阶段 12 · t86 起的**唯一**重绘通道），**不持有**渲染器（**阶段 13 · t109 起改为持有渲染组件字段** ✗；旧口径原文「**不持有**渲染器」**已作废**）、
 * **不持有**任何 Bukkit 库存对象。请求只置脏，真正的写入仍由框架在**帧末 flush** 完成
 * ⇒ 「空闲 tick 零 setItem」逐字不变。
 * <p>
 * <b>t86 的取代动作（不静默改写）</b>：旧形态是本类实现 <i>{@code RepaintRequestable}</i> 并由框架
 * 把 <i>{@code RepaintRequester}</i> 绑给它 —— 那是**两条并存的重绘通道** ✗。t86 起改为
 * 「**从容器取渲染组件、调 {@code requestRepaint()}**」这一条通道 ✓（旧的
 * {@code RepaintRequestable} / {@code RepaintRequester} **两个类型已删除** ✓）。
 * <p>
 * <b>它演示的第二件事 = {@code dependsOnLiveState()} 为 {@code false} 的组件不被每 tick 重绘</b>（A8）：
 * 本类覆写了 {@link #buildItem()}，把父类画的 {@code " x.xs"} 秒数后缀**去掉** ⇒ 冷却期间它的外观
 * **不再逐刻变化** ⇒ 能力覆写为 {@code false} 是**诚实**的（不是把刷新关掉硬省），于是它冷却时
 * 不会被每 tick 重绘（对照：{@code core/Skill} 家族的默认画法带秒数 ⇒ 能力为 {@code true} ⇒ 每刻刷）。
 * 它自己的刷新节拍由 {@link #update()} 里的**显式请求**给出。
 * <p>
 * <b>请求窗口（本示例的口径，便于取证）</b>：前 {@value #REQUEST_WINDOW_START_TICKS} 刻**不请求**
 * （"请求前不刷"的对照窗）；之后到 {@value #REQUEST_WINDOW_END_TICKS} 刻之间每
 * {@value #REQUEST_PERIOD_TICKS} 刻请求一次（"请求后刷新"）；窗口结束后**再次安静**（证明确实停了）。
 * <p>
 * <b>边界</b>：本类**不**在 {@code buildItem()} 里写背包、**不**读任何渲染器状态 —— 它只产出物品。
 */
public class ExampleSelfRefreshingSkill extends Skill {

    //阶段 13 · t109：渲染组件引用改为**字段 + 在 start() 内赋值**（与全仓统一形态一致 ✓）——
    //  R-4：取组件只能在本钩子（或新写/既有 start()）里做 ✗，不得放 awake()；
    //  注册表装配期后冻结 ⇒ 缓存引用与按需查找**恒等** ✓（未装配时仍为 null ⇒ 下面的静默检查逐字保留 ✓）。
    private HotbarRenderComponent renderComponent;

    //阶段 13 · t110：**可用性判定下放给子类**（用户裁定：基类不持 buff / energy、不查容器）⇒
    //  本组件自己持 buff 字段（在既有 start() 内一次查好 ✓，与全仓统一形态一致）。
    private BuffComponent buff;

    /** 请求窗口起点（刻）：此前不请求 ⇒ 用于"请求前不刷"的对照窗。 */
    public static final int REQUEST_WINDOW_START_TICKS = 100;
    /** 请求窗口终点（刻）：此后不再请求 ⇒ 用于证明"请求确实停了"。 */
    public static final int REQUEST_WINDOW_END_TICKS = 300;
    /** 请求周期（刻）：窗口内每这么多刻请求一次。 */
    public static final int REQUEST_PERIOD_TICKS = 20;

    /** 本组件自己的活状态（外观里显示的计数）。 */
    private int ticks;

    public ExampleSelfRefreshingSkill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 组件自己的状态变化点：窗口内每 {@value #REQUEST_PERIOD_TICKS} 刻**主动请求**一次重绘。
     * <p>这正是"外观由组件决定"所缺的那一环：框架并不知道本组件的外观需要更新。
     * <p><b>t86</b>：请求走**渲染组件**这一条通道（容器查找；未装配时为 {@code null} ⇒ 静默不请求 ✓）。
     */
    @Override
    public void update(){
        ticks++;
        if(ticks < REQUEST_WINDOW_START_TICKS || ticks > REQUEST_WINDOW_END_TICKS) return;
        if(ticks % REQUEST_PERIOD_TICKS != 0) return;
        if(renderComponent != null){
            renderComponent.requestRepaint();
        }
    }

    /**
     * {@code false} = **外观不依赖活状态**：本类把父类的 {@code x.xs} 秒数后缀去掉了
     * ⇒ 冷却期间外观恒定 ⇒ 不需要框架每 tick 刷（刷新由 {@link #update()} 的显式请求驱动）。
     */
    @Override
    public boolean dependsOnLiveState(){
        return false;
    }

    /**
     * 覆写父类画法：**去掉** {@code " x.xs"} 秒数后缀（这正是"外观不再依赖活状态"的那一处），
     * 并追加一行本组件自己的计数（它的刷新节拍 = 显式请求）。
     */
    @Override
    public ItemStack buildItem(){
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if(meta != null && meta.displayName() != null){
            String plain = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            meta.displayName(Component.text(plain.replaceAll("\\s+\\d+\\.\\ds$", "")));
        }
        if(meta != null){
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Component.text("示例：请求式刷新计数 #" + ticks));
            meta.lore(lore);
        }
        if(meta != null){
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /** 供探针/调试读取（不改任何玩家可见状态）。 */
    public int getTicks(){
        return ticks;
    }

    /** 技能描述符（纯声明）：**冷却 200 刻** ⇒ 冷却期可用来演示"能力为 false ⇒ 不每刻刷"。 */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(Component.text("自刷新示例"),
                    Component.text("外观会自己变：组件只请求重绘，不写物品"),
                    200,
                    0,
                    Material.CLOCK);
        }

        @Override
        public ExampleSelfRefreshingSkill create(String id, ComponentServices services){
            return new ExampleSelfRefreshingSkill(id, services, this);
        }
    }

    /**
     * **开始生效**（阶段 13 · t109）：把渲染组件**一次查好**缓存进字段 ✓（原先是在 `update()` 里按需查找 ✗）。
     * <p>R-4：取组件只能在本钩子里做 ✗ —— 不得放 `awake()`；注册表装配期后冻结 ⇒ 与按需查找恒等 ✓。
     */
    @Override
    public void start(){
        renderComponent = svc().components().get(HotbarRenderComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * **闸门放行？**（阶段 13 · t110：基类不再取 buff ⇒ 由本组件用**自己的字段**判）。
     */
    @Override
    protected boolean gateOpen(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**（阶段 13 · t110）：本组件**不参与能量维度**（声明耗能 0）⇒ 返回声明值；
     * 与迁移前**逐字等价**（能量组件内 clamp 到 `[0, max]` ⇒ 原判定 `current() < 0` 恒假）。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
