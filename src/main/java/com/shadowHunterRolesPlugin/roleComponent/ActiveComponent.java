package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 主动组件基类（技能与主武器的并集）：声明「能施放 / 能攻击」的组件。
 *
 * <h2>本类做什么</h2>
 * 只做两件事：① 把装配期描述符装进字段并给出 {@link #specification()}；
 * ② 持有每实例一份的冷却状态与就绪判定 {@link #isCoolingDown()}。
 *
 * <h2>声明数据从哪来</h2>
 * 图标 / 显示名 / 描述 / 冷却声明值 / 耗能声明值 / 基础物品都住在 {@link HotbarSpecification}
 * （装配期描述符，由组件自己的嵌套 `Specification` 声明）；本类只把组件侧仍需要的几个转出去。
 * <p>{@link #buildItem()} 保持抽象：默认画法由两个家族基类给出（技能带秒数、主武器不带）。
 * <p>{@link #dependsOnLiveState()} 默认 `false`；「外观每刻自己变」的组件覆写为 `true`。
 *
 * <h2>冷却</h2>
 * 状态与判断都在本类，框架不登记、不派发、不落表 —— 需要读数时转问组件。
 * <p>施放成功才调 {@link #startCooldown()}（失败时不要调）。
 *
 * <h2>入口词汇</h2>
 * {@link #onCast(CastSignal)} / {@link #onAttack(AttackSignal)} 与 {@link CastTrigger} /
 * {@link CastSignal} / {@link AttackSignal} 都归组件 —— 施放与攻击由物品支持类组件处理。
 */
public abstract class ActiveComponent extends RoleComponent {

    /** 热键栏触发的三种来源（listener 只做"事件 → trigger"翻译；`onCast` 入口的输入词汇）。 */
    public enum CastTrigger {
        RIGHT_CLICK,
        LEFT_CLICK,
        DROP
    }

    /** 施放信号：只带这一次的数据（不可变）。施动者永远是 {@code svc().self().player()}。 */
    public record CastSignal(CastTrigger trigger) {
    }

    /** 攻击信号：攻击者永远是自己（{@code svc().self().player()}），这里只带受害者。 */
    public record AttackSignal(Player victim) {
    }

    private final HotbarSpecification<?> specification;

    /**
     * 冷却实例状态：到期刻（{@code Bukkit.getCurrentTick()} 口径）；`0` = 无冷却，与"从未进过冷却"同义。
     * <p>状态只属于本实例：同 id 的两个实例各自持有自己的字段，不共享、不串扰
     * （组件自己持有冷却与判断，框架不知道冷却）。
     */
    private int cooldownUntilTick = 0;

    /**
     * 装配期把自己要占的栏位登记给渲染组件（栏位的运行期归属 = 渲染组件）。
     *
     * <p>为什么在 {@code awake()}：框架保证每个组件都被调用一次，而多数子类不覆写它。
     * <p>登记内容 = 描述符声明的栏位（{@link HotbarSpecification#slotOrNull()}）；
     * 无栏位传 {@code null}，渲染组件据此撤销登记。
     * <p>渲染组件按注册序排在最前（内建块首位），因此本方法执行时它已在容器里。
     *
     * <h2>为什么本方法是 {@code final}</h2>
     * 子类不能覆写它 —— 它只做"栏位登记"这一件事，而登记绝不允许被漏掉；
     * 需要自己的装配期初始化时覆写钩子 {@link #onAwake()}（本方法会替你调用）。
     * <p>实测事故：{@code CangluBlueIceRevolverSkill} 曾覆写 {@code awake()} 且未调 super，
     * 于是登记没执行、热键栏里没有左轮，且无任何报错 —— 改为 {@code final} 后从编译期堵死这条路。
     */
    @Override
    public final void awake() {
        HotbarRenderComponent render = findComponent(HotbarRenderComponent.class);
        if (render != null) {
            // 栏位读口只在带栏位的那一支描述符上（`HotbarSpecification`）；
            //  基类描述符不含栏位语言，因此这里显式取那一支（本类的 spec 恒为它）
            HotbarSpecification<?> slotSpec = specification();
            render.registerSlot(this, slotSpec == null ? null : slotSpec.slotOrNull());
        }
        onAwake();
    }

    /**
     * 子类自己的装配期初始化钩子（默认空）。
     *
     * <p>契约同 {@code awake()}：幂等、不得改动任何玩家可见状态、不得取用其它组件
     * （依赖解析放 {@code start()}）。
     * <p>由 {@link #awake()} 在栏位登记之后调用，因此顺序有保证（登记先于子类初始化）。
     */
    protected void onAwake() {
    }

    protected ActiveComponent(String id, ComponentServicesPort services, HotbarSpecification<?> specification) {
        super(id, services);
        this.specification = specification;
    }

    /** 唯一实现点：表现规格（声明数据的读面；组件侧与渲染侧都从这里读）。 */
    public final HotbarSpecification<?> specification() {
        return specification;
    }

    // ───────── 热键栏物品的产出面（本类自己声明） ─────────

    /**
     * 产出本组件在当前时刻的热键栏物品（完整形态：材质 / 名称 / 后缀 / lore / 识别键都已就位）。
     * <p>由框架在帧末 flush 的写物品段调用（每帧至多一次/组件），并与注册序同序遍历；
     * 组件不得在此方法里写玩家背包（写物品的唯一落点仍是渲染器的那一次槽位写入）。
     * <p>本类保持抽象：默认画法由两个家族基类给出（技能侧带 {@code x.xs} 秒数、主武器侧不带）——
     * 画物品要读运行期状态（冷却剩余刻数 / 闸门 / 当前能量），描述符拿不到这些。
     */
    public abstract ItemStack buildItem();

    /**
     * 本组件的外观是否依赖"活状态"：{@code true} = 它的外观会在没有框架置脏事件的情况下自己变
     * （例如技能冷却名里的 {@code x.xs} 秒数每刻都在变），因此只要它在冷却中，框架就必须每 tick 至少刷一次，
     * 否则玩家看到的是陈旧外观。
     * <p>为什么这是自报值、而不是框架里的一句 {@code instanceof Skill}：把「外观含秒数」
     * 写死成具体类，则①第三类"带倒计时外观"的组件加进来时必须改框架文件；
     * ②覆写 {@link #buildItem()} 去掉秒数外观的子类仍会被每 tick 重绘（覆写白写）。
     * 改为自报后，新组件只加新文件即可（默认 {@code false}，需要就覆写 {@code true}），
     * 而"覆写掉活状态外观"的子类可以覆写成 {@code false}，不再每 tick 重绘。
     * <p>默认值 = {@code false}：只有技能家族的默认画法带秒数，该家族覆写为 {@code true}，
     * 主武器与被动保持 {@code false} ⇒ 既有组件的真值表逐字不变。
     * <p>本读数只回答"要不要每刻刷"；"写不写"仍由帧末 flush 决定（空闲 tick 零 setItem 不变）。
     * 外观依赖活状态、但变化不是每刻的组件应返回 {@code false}，并在状态真的变了时
     * 用渲染组件的 {@code requestRepaint()} 主动请求 —— 那才是它的刷新节拍。
     */
    public boolean dependsOnLiveState() {
        return false;
    }

    // ───────── 声明数据的组件侧读数（家族基类与外部读数点用；数据源 = 描述符） ─────────

    /** 显示名（数据源 = 描述符）。 */
    public Component getDisplayName() {
        return specification().getDisplayName();
    }

    /** 描述（数据源 = 描述符；零到多行，行序 = lore 行序，恒非 null）。 */
    public List<Component> getDescription() {
        return specification().getDescription();
    }

    /** 冷却声明值（数据源 = 描述符）—— 唯一真值来源（{@link #startCooldown()} 读它）。 */
    public int getCooldownTicks() {
        return specification().getCooldownTicks();
    }

    /** 耗能声明值（数据源 = 描述符）；{@code 0} = 不耗能（{@code ENERGY_LACK} 态不可达）。 */
    public int getEnergyCost() {
        return specification().getEnergyCost();
    }

    /**
     * 基础物品（热键栏物品底稿：材质 / 显示名 / 描述）—— 委托给描述符的同名方法，
     * 需要特殊底稿的组件在自己的 `Specification` 里覆写即可。
     */
    public ItemStack baseItem(String id) {
        return specification().baseItem(id);
    }

    /**
     * 开始（或覆盖式重启）本组件的冷却：时长取声明值（{@link #getCooldownTicks()}，唯一真值来源）。
     * <p>等价于 {@link #startCooldown(int) startCooldown(getCooldownTicks())}；保留无参形态是因为
     * 全部产品调用点都是"按声明值启动"。
     *
     * @return 是否真的写入了冷却状态：声明值 ≤ 0（"没有冷却这回事"，如被动）时不写状态并返回 `false`，
     *         与既有的无声语义逐字一致
     */
    public boolean startCooldown() {
        return startCooldown(getCooldownTicks());
    }

    /**
     * 按给定时长开始（或覆盖式重启）冷却。
     * <p>语义：从当前刻重算到期（覆盖旧值，不做"取较大值"的续期），与"以本次调用时刻重算"的
     * 覆盖式重启逐字等价。
     * <p>存在理由：调试命令需要按显式刻数驱动冷却，本重载让那条路径成为纯委托，
     * 不再需要框架侧的表。
     *
     * @param ticks 冷却时长（tick）；≤ 0 时不写状态并返回 `false`
     * @return 是否真的写入了冷却状态
     */
    public boolean startCooldown(int ticks) {
        if (ticks <= 0) {
            cooldownUntilTick = 0;
            return false;
        }
        cooldownUntilTick = org.bukkit.Bukkit.getCurrentTick() + ticks;
        return true;
    }

    /** 结束冷却（幂等）：清空本实例的状态；已不在冷却中也不报错、不产生副作用。 */
    public void stopCooldown() {
        cooldownUntilTick = 0;
    }

    /**
     * 冷却状态读数：本实例的冷却是否正在进行（未到期）—— 逐字读本组件实例的
     * {@link #cooldownUntilTick}，不经任何端口。
     * <p>框架的帧末 flush 用它驱动"冷却中每 tick 至少刷一次"（技能名里的秒数才会逐刻递减）；
     * 这条节拍链与展示链（{@code %.1f} 秒数）读的是同一份状态。
     * <p>框架侧的唯一消费者 = 帧末 flush 的入口条件（"有占栏位组件在冷却，本 tick 至少刷一次"）；
     * 该条件按 {@code instanceof ActiveComponent} 取接受集（组件自持冷却后框架不需要任何冷却接口）。
     */
    public boolean isCoolingDown() {
        return org.bukkit.Bukkit.getCurrentTick() < cooldownUntilTick;
    }

    /** 剩余冷却刻数：不在冷却中时为 `0`（不返回负数）。 */
    public int remainingCooldownTicks() {
        return Math.max(0, cooldownUntilTick - org.bukkit.Bukkit.getCurrentTick());
    }

    // ───────── 热键栏提交面（本类给出的唯一取用入口）─────────

    /**
     * 把本组件的热键栏物品提交给机制面，并取回句柄（回调注册的落点）。
     * <p>为什么由基类提供这一处：机制面的 id 与类型是框架级知识 —— 让每个具体组件各自去认
     * {@code HotbarRenderComponent.ID} 与 {@link HotbarRenderComponent}，等于把同一份框架级知识
     * 在每个组件里各抄一遍。本类一次性持有它，具体组件只写一行 `submitToHotbar(...)`。
     * <p>槽位口径：提交槽位 = 本组件自己的表现规格里声明的槽位（{@link #specification()} 的
     * `slot()`）—— 与拉取式路径（{@code renderPlan()} 用 {@code specification.slot()}）同源，
     * 因此提交式条目覆盖同一槽位，不会出现"一个组件占两格"。
     * <p>调用时机：组件在自己的 `start()` 里调用（那时强类型依赖已解析、物品画法可读）。
     *
     * @param item 要提交的物品（通常 {@code buildItem()} 的产物；{@code null} 时不提交、返回 {@code null}）
     * @return 该槽位的句柄（回调注册面）；渲染组件缺失 / 本组件不占栏位 / item 为 null 时返回 `null`
     */
    protected final HotbarRenderComponent.HotbarItemHandle submitToHotbar(ItemStack item) {
        if (item == null || !specification().hasSlot()) {
            return null;
        }
        RoleComponent found = svc().components().getById(HotbarRenderComponent.ID);
        if (!(found instanceof HotbarRenderComponent render)) {
            return null;
        }
        return render.submit(specification().slot(), getId(), item);
    }

    /**
     * 物品使用入口（施放）：默认不做事、也不进冷却（与 listener 的行为一致：未重写的热键栏
     * 触发只做就绪预检）。
     * <p>返回值为 {@code void}：本方法不是覆写任何接口，而是本组件的自有声明，因此没有 {@code @Override}。
     */
    public void onCast(CastSignal signal) {
    }

    /**
     * 物品使用入口（攻击）：默认不做事（与 {@link #onCast(CastSignal)} 同族）。
     * <p>声明位置在本类，因此容器侧派发与施放路径同构：判据 = 声明了主动入口的组件
     * （{@code instanceof ActiveComponent}），而不是点名某个具体家族；技能 / 主武器各自覆写。
     */
    public void onAttack(AttackSignal signal) {
    }

    // 冷却到期 / 被结束 / 被重启都不由框架通知：组件自持冷却状态，框架不持有、也不派发。
}
