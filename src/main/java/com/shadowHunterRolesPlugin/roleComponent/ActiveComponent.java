package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.roleComponent.builtin.hotbar.HotbarSpecification;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.ServiceComponents;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 主动组件基类（设计 §4.3）：= 原 `Skill` + `MainWeapon` 去重后的并集，不多一个成员。
 *
 * <h2>本类在装配里的位置</h2>
 * 本类降级为**可选的便利实现** —— 它只做一件事：把构造参数装进
 * {@link HotbarSpecification} 并给出 {@link #specification()}（**唯一实现点**）。
 *
 * <h2>热键栏能力：由本类**自己声明**，不再是"实现某个能力接口"</h2>
 * 本类**不实现**任何热键栏能力接口 ✗ —— 那三个接口已随"能力 = 渲染组件读的数据"这一口径整体删除 ✗
 * （见 {@code HotbarRenderComponent} 的读口 {@code specificationOf} / {@code buildItemOf} /
 * {@code dependsOnLiveStateOf}）。现在：
 * <ul>
 *   <li><b>声明数据</b>（图标 / 显示名 / 描述 / 冷却声明值 / 耗能声明值 / 基础物品）住在
 *       {@link HotbarSpecification}（装配期描述符）✓，本类只把其中**组件侧仍需要的几个**转出去
 *       （见下：家族基类与外部读数点要用）；</li>
 *   <li><b>物品产出</b> = {@link #buildItem()}：本类**保持抽象** ✗ —— 默认画法由两个家族基类给出
 *       （{@code Skill} 带秒数、{@code MainWeapon} 不带）；画物品要读运行期状态，做不到在这里按家族分叉；</li>
 *   <li><b>外观是否依赖活状态</b> = {@link #dependsOnLiveState()}：默认 {@code false}，
 *       只有"外观每刻自己变"的家族覆写为 {@code true}。</li>
 * </ul>
 *
 * <h2>表现规格是装配期描述符</h2>
 * 表现规格**不由构造实参内联**，而是由组件自己的嵌套 `Specification` 声明、经装配入口
 * {@code Role.Builder.addComponent(id, specification)} 交给容器，容器再经
 * {@code Specification.create(id, services)} 把它交给本构造器。
 * <p>本类持有的这一份同时是**基类默认画法的读面**（{@link #specification()}）；它由装配期
 * {@code freeze()} 置为只读 ⇒ 同一实例被多个玩家实例共享也不会被串改。
 * <p>表现规格类改全拼（`HotbarSpec` → {@link HotbarSpecification}；旧短名类已删除 ✓）。
 * 本类持有的这一份是**声明值对象**：它没有栏位（栏位由装配器在描述符上设置），
 * 也从不由装配入口消费 —— 因此这条路径与更早的形态逐字等价。
 *
 * <h2>冷却：状态与判断都在本类</h2>
 * 每实例一份 {@code cooldownUntilTick}（同 id 的两个实例**各自独立** ✓），框架既不登记、也不派发、
 * 更不落表；框架侧只在需要读数时**转问组件**（{@link #isCoolingDown()} / 读数口），公开面一条不删 ✓。
 * <p>"冷却结束回调"已随能力接口一并删除 —— 全库**零覆写点** ⇒ 删除**零行为变化** ✓。
 * <p>物品使用入口（{@link #onCast(CastSignal)}）与入口词汇（{@link CastTrigger} / {@link CastSignal} /
 * {@link AttackSignal}）都归本组件：施放与攻击由「物品支持类组件」处理 ✓。
 */
public abstract class ActiveComponent extends RoleComponent
        implements EnergyComponent.EnergyCosting {

    /** **热键栏触发的三种来源**（listener 只做"事件 → trigger"翻译；`onCast` 入口的输入词汇）。 */
    public enum CastTrigger {
        RIGHT_CLICK,
        LEFT_CLICK,
        DROP
    }

    /** **施放信号**：只带这一次的数据（不可变）。施动者永远是 {@code svc().self().player()}。 */
    public record CastSignal(CastTrigger trigger) {
    }

    /** **攻击信号**：攻击者永远是自己（{@code svc().self().player()}），这里只带受害者。 */
    public record AttackSignal(Player victim) {
    }

    private final HotbarSpecification<?> specification;

    /**
     * **冷却实例状态**：到期刻（{@code Bukkit.getCurrentTick()} 口径）；`0` = 无冷却 ⇒ 与"从未进过冷却"同义。
     * <p>状态**只属于本实例**：同 id 的两个实例各自持有自己的字段 ⇒ 不共享、不串扰 ✓
     * （组件自己持有冷却与判断，框架不知道冷却）。
     */
    private int cooldownUntilTick = 0;

    protected ActiveComponent(String id, ComponentServices services, HotbarSpecification<?> specification) {
        super(id, services);
        this.specification = specification;
    }

    /** **唯一实现点**：表现规格（声明数据的读面；组件侧与渲染侧都从这里读）。 */
    public final HotbarSpecification<?> specification() {
        return specification;
    }

    // ───────── 热键栏物品的产出面（本类自己声明） ─────────

    /**
     * **产出本组件在当前时刻的热键栏物品**（**完整形态**：材质 / 名称 / 后缀 / lore / 识别键都已就位）。
     * <p>由框架在**帧末 flush** 的写物品段调用（每帧至多一次/组件），并与**注册序**同序遍历；
     * 组件**不得**在此方法里写玩家背包（写物品的唯一落点仍是渲染器的那一次槽位写入）。
     * <p>本类**保持抽象** ✗：默认画法由两个家族基类给出（技能侧带 {@code x.xs} 秒数、主武器侧不带）——
     * 画物品要读运行期状态（冷却剩余刻数 / 闸门 / 当前能量），描述符拿不到这些。
     */
    public abstract ItemStack buildItem();

    /**
     * **本组件的外观是否依赖"活状态"**：{@code true} = 它的外观会在**没有框架置脏事件**的情况下自己变
     * （例如技能冷却名里的 {@code x.xs} 秒数每刻都在变）⇒ 只要它在冷却中，框架就必须**每 tick** 至少刷一次，
     * 否则玩家看到的是陈旧外观。
     * <p><b>为什么这是一个自报值、而不是框架里的一句 {@code instanceof Skill}</b>：把「外观含秒数」
     * **写死成具体类** ⇒ ① 第三类"带倒计时外观"的组件加进来时**必须改框架文件** ✗；
     * ② 覆写 {@link #buildItem()} 去掉秒数外观的子类**仍会被每 tick 重绘**（白写）✗。
     * 改为自报后：新组件**只加新文件**即可（默认 {@code false}，需要就覆写 {@code true}）✓，
     * 而"覆写掉活状态外观"的子类可以覆写成 {@code false} ⇒ **不再每 tick 重绘** ✓。
     * <p><b>默认值 = {@code false}</b>：只有技能家族的默认画法带秒数 ⇒ 该家族覆写为 {@code true}，
     * 主武器与被动保持 {@code false} ⇒ **既有组件的真值表逐字不变**。
     * <p>注意：本读数只回答"**要不要**每刻刷"；"**写不写**"仍由帧末 flush 决定（空闲 tick 零 setItem 不变）。
     * 外观依赖活状态、但变化**不是每刻**的组件应返回 {@code false}，并在状态真的变了时
     * 用渲染组件的 {@code requestRepaint()} **主动请求** —— 那才是它的刷新节拍。
     */
    public boolean dependsOnLiveState() {
        return false;
    }

    // ───────── 声明数据的组件侧读数（家族基类与外部读数点用；数据源 = 描述符） ─────────
    //★ 求值与"接口 default 委托"逐字相同（都是 specification().getX()）⇒ 调用点零改动 ✓。

    /** 显示名（数据源 = 描述符）。 */
    public Component getDisplayName() {
        return specification().getDisplayName();
    }

    /** 描述（数据源 = 描述符）。 */
    public Component getDescription() {
        return specification().getDescription();
    }

    /** 冷却**声明值**（数据源 = 描述符）—— 唯一真值来源（{@link #startCooldown()} 读它）。 */
    public int getCooldownTicks() {
        return specification().getCooldownTicks();
    }

    /** 耗能**声明值**（数据源 = 描述符）；同时满足 {@link EnergyComponent.EnergyCosting} 的声明。 */
    @Override
    public int getEnergyCost() {
        return specification().getEnergyCost();
    }

    /**
     * **基础物品**（热键栏物品**底稿**：材质 / 显示名 / 描述）—— 委托给描述符的同名方法
     * ⇒ 需要特殊底稿的组件**在自己的 `Specification` 里覆写**即可。
     */
    public ItemStack baseItem(String id) {
        return specification().baseItem(id);
    }

    /**
     * **开始（或覆盖式重启）本组件的冷却**：时长取**声明值**（{@link #getCooldownTicks()}，唯一真值来源）。
     * <p>等价于 {@link #startCooldown(int) startCooldown(getCooldownTicks())} —— 保留无参形态是因为
     * 全部产品调用点都是"按声明值启动" ✓。
     *
     * @return 是否真的写入了冷却状态：声明值 ≤ 0（"没有冷却这回事"，如被动）⇒ **不写状态并返回 `false`**，
     *         与既有的无声语义逐字一致 ✓
     */
    public boolean startCooldown() {
        return startCooldown(getCooldownTicks());
    }

    /**
     * **按给定时长开始（或覆盖式重启）冷却**。
     * <p>语义：从**当前刻**重算到期（覆盖旧值，不做"取较大值"的续期 ✗）—— 与"以本次调用时刻重算"
     * 的覆盖式重启**逐字等价** ✓。
     * <p>存在理由：兼容薄壳（原冷却端口）与调试命令需要按**显式刻数**驱动冷却（旧端口签名是
     * `start(int ticks)`）⇒ 本重载让那条路径成为**纯委托**，不再需要框架侧的表 ✓。
     *
     * @param ticks 冷却时长（tick）；≤ 0 ⇒ 不写状态并返回 `false`
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

    /** **结束冷却**（幂等）：清空本实例的状态；已不在冷却中也**不报错、不产生副作用** ✓。 */
    public void stopCooldown() {
        cooldownUntilTick = 0;
    }

    /**
     * **冷却状态读数**：本实例的冷却是否**正在进行**（未到期）——
     * 逐字读**本组件实例**的 {@link #cooldownUntilTick}（不经任何端口 ✗）。
     * <p>框架的帧末 flush 用它驱动"冷却中每 tick 至少刷一次"（技能名里的秒数才会逐刻递减）——
     * 这条**节拍链**与展示链（{@code %.1f} 秒数）读的是同一份状态 ✓。
     * <p>框架侧的**唯一消费者** = 帧末 flush 的入口条件（"有占栏位组件在冷却 ⇒ 本 tick 至少刷一次"）；
     * 该条件按 {@code instanceof ActiveComponent} 取接受集 ✓（组件自持冷却后框架不需要任何冷却接口 ✗）。
     */
    public boolean isCoolingDown() {
        return org.bukkit.Bukkit.getCurrentTick() < cooldownUntilTick;
    }

    /** **剩余冷却刻数**：不在冷却中 ⇒ `0`（**不返回负数** ✓）。 */
    public int remainingCooldownTicks() {
        return Math.max(0, cooldownUntilTick - org.bukkit.Bukkit.getCurrentTick());
    }

    // ───────── 热键栏提交面（本类给出的**唯一**取用入口）─────────

    /**
     * **把本组件的热键栏物品提交给机制面，并取回句柄**（回调注册的落点）。
     * <p><b>为什么由基类提供这一处</b>：机制面的 **id 与类型是框架级知识** —— 让每个具体组件各自
     * 去认 `{@code ServiceComponents.ID_HOTBAR_RENDER}` 与 `{@link HotbarRenderComponent}`，
     * 等于把同一份框架级知识抄 8 遍 ✗。本类一次性持有它，具体组件只写一行 `submitToHotbar(...)` ✓。
     * <p><b>槽位口径</b>：提交槽位 = 本组件**自己的表现规格**里声明的槽位（{@link #specification()} 的
     * `slot()`）—— 与拉取式路径（{@code renderPlan()} 用 {@code specification.slot()}）**同源** ✓
     * ⇒ 提交式条目**覆盖同一槽位**，不会出现"一个组件占两格"。
     * <p><b>调用时机</b>：组件在自己的 `start()` 里调用（那时强类型依赖已解析、物品画法可读）✓。
     *
     * @param item 要提交的物品（通常 {@code buildItem()} 的产物；{@code null} ⇒ 不提交、返回 {@code null}）
     * @return 该槽位的句柄（回调注册面）；**渲染组件缺失 / 本组件不占栏位 / item 为 null** ⇒ `null`
     */
    protected final HotbarRenderComponent.HotbarItemHandle submitToHotbar(ItemStack item) {
        if (item == null || !specification().hasSlot()) {
            return null;
        }
        RoleComponent found = svc().components().getById(ServiceComponents.ID_HOTBAR_RENDER);
        if (!(found instanceof HotbarRenderComponent render)) {
            return null;
        }
        return render.submit(specification().slot(), getId(), item);
    }

    /**
     * **物品使用入口（施放）**：默认不做事、也**不**进冷却（与 listener 的行为一致：未重写的热键栏
     * 触发只做就绪预检）。
     * <p>返回值为 {@code void}（施放结果枚举已删 —— 它**没有任何消费点**）。
     * <p>本方法**不是**覆写任何接口：它是本组件的**自有声明**（原 `HotbarActionable` 已被吸收 ✗）
     * ⇒ 无 {@code @Override}；方法签名与默认体**逐字未变** ✓。
     */
    public void onCast(CastSignal signal) {
    }

    /**
     * **物品使用入口（攻击）**：默认不做事（与 {@link #onCast(CastSignal)} 同族）。
     * <p>★ **本方法的位置**：原先只声明在 {@code roleComponent/base/MainWeapon} ⇒ 容器侧派发时必须
     * `instanceof MainWeapon` 强转（= 框架**点名具体家族**，与 {@code t6}/{@code t7} 消灭的
     * 「容器强转」同族 ✗）。上提到本类后，容器侧与施放路径**同构**：判据 = **声明了主动入口的组件**
     * （{@code instanceof ActiveComponent}），具体组件（技能 / 主武器）自行覆写 ✓。
     * <p>签名与默认体**逐字未变**；{@code MainWeapon} 侧保留其声明（覆写者与 javadoc 一字未动）✓。
     */
    public void onAttack(AttackSignal signal) {
    }

    //冷却结束回调（原 CooldownAware）与框架侧派发点已整体删除 ——
    //  组件自持冷却状态后，"到期/被结束/被重启"都不再由框架通知（框架不持有、也不派发 ✓）。
}
