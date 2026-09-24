package com.shadowHunterRolesPlugin.core.hotbar;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.HotbarRenderComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 热键栏**表现规格 + 带栏位描述符**（阶段 6 立、阶段 7 · A 步改全拼并升格为描述符、**阶段 8 收敛为纯声明**）：
 * 把过去分散在基类里的表现字段收敛成一个不可变值对象，并**同时**承担
 * {@link RoleComponent.Specification} 的"怎么造这个组件"的职责（两者**合一**，不并列）。
 * <p>
 * 分工：
 * <ul>
 * <li>组件侧的唯一实现点 = 主动组件基类的 {@code specification()} —— 组件只写这一处；</li>
 * <li>本类自身即**声明面**（图标 / 显示名 / 描述 / 冷却 / 耗能），
 * 因此渲染组件的读口 {@code specificationOf(component)} 直接返回本对象，无需适配代码；</li>
 * <li><b>纯声明</b>：描述符不自述种类；
 * "物品长什么样（含运行期状态）"由组件基类的 {@code buildItem()} 回答（渲染侧读口 = {@code buildItemOf}）；</li>
 * <li><b>栏位必填</b>：本类型 {@link #requiresSlot()} = {@code true}（不带栏位的组件用另一支描述符）。</li>
 * </ul>
 * <b>栏位</b>：本类型是**带栏位**的那一支 —— 装配器用
 * {@link #setSlot(int)} 指定它在热键栏里的位置，装配期未设栏位则
 * {@link RoleComponent.Specification#freeze()} **抛异常**（绝不静默变成"不占栏位"）。
 * <b>"不占栏位"由类型表达</b>：不带栏位的组件用 {@code PassiveSkill.Specification}（它继承根类型、
 * **没有** {@code setSlot}）；本类型内部不再出现 `-1` 哨兵。
 * <p>
 * 命名沿用工程的 JavaBean 风格（设计 §4.3：不引入 record 风格访问器）；
 * 旧短名 {@code HotbarSpec} 保留为 `@Deprecated` 别名（见该类）。
 * <p><b> 说明</b>：上面的旧口径**** —— 该类（`HotbarSpec`）**已删除**；
 * 全拼 {@link HotbarSpecification} 是**唯一**入口。（旧口径原文保留不删，便于回溯。）
 */
public class HotbarSpecification<T extends RoleComponent>
        extends RoleComponent.Specification<T> {

 /**
 * **声明的 id**（{@link #of} 传入，可为 {@code null}）。
 * <p>阶段 7 · C 步起：{@link #getId()} **优先**返回**装配期绑定的注册 id**
 * （{@link RoleComponent.Specification#bindId(String)}，由装配入口写入）；只有未经装配的描述符
 * （例如探针直接构造的）才回落到这里 ⇒ **字段不再撒谎**。
 */
    private final String declaredId;
    private final Component displayName;
    private final Component description;
    private final Material icon;
    private final int cooldownTicks;
    private final int energyCost;

    protected HotbarSpecification(String descriptorLabel, String id, Component displayName, Component description,
                                  Material icon, int cooldownTicks, int energyCost) {
        super(descriptorLabel);
        this.declaredId = id;
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = energyCost;
    }

 /**
 * 唯一的构造入口（不可变 ⇒ 组件可在构造期一次建好）。
 * @param descriptorLabel 诊断标签（**不是行为分支**；只出现在装配期异常的文案里，取值如 "Skill"）
 */
    public static <T extends RoleComponent> HotbarSpecification<T> of(String descriptorLabel, String id,
                                                                     Component displayName, Component description,
                                                                     Material icon, int cooldownTicks, int energyCost) {
        return new HotbarSpecification<>(descriptorLabel, id, displayName, description, icon, cooldownTicks, energyCost);
    }

 /** 本类型**必须**有栏位：见 {@link RoleComponent.Specification#freeze()} 的 fail-fast。 */
    @Override
    protected boolean requiresSlot() {
        return true;
    }

 /**
 * **装配器设置栏位**（这一支唯一会在装配期写入的参数；其余表现字段由组件自己的描述符声明默认值）。
 * 冻结后调用、重复改成别的位置、越界（非 0..8）一律抛异常。
 */
    public final HotbarSpecification<T> setSlot(int slot) {
        assignSlot(slot);
        return this;
    }

 /**
 * **失败关闭（fail-fast）**：本类的默认创建体不造任何组件 —— 具体组件由**组件自己声明的嵌套
 * `Specification`** 覆写本方法给出（阶段 7 · B 步落地）。把裸的 {@link HotbarSpecification}
 * 交给装配入口会立刻在这里抛异常，而不是造出一个语义不明的组件。
 * <p>本类的另一半职责是"组件内部的声明值对象"：主动组件基类的 {@code specification()}
 * 返回它、基类默认画法读它，那条路径**从不调用本方法**。
 */
    @Override
    public T create(String id, ComponentServices services) {
        throw new UnsupportedOperationException(
                "HotbarSpecification is a presentation/descriptor base and cannot create a component by itself; "
                        + "declare a component-nested Specification and override create(String, ComponentServices).");
    }

 /**
 * 组件 id：**优先**取装配期绑定的注册 id（{@link RoleComponent.Specification#bindId(String)}），
 * 未绑定时才回落到 {@link #of} 传入的声明 id。
 * <p>阶段 7 · C 步的修法（A7 · 选 (a)）：B 步后组件自带的描述符一律走"不带 id 的构造"，若只留声明 id，
 * 这个字段就会**恒为 null 而仍可被读**⇒ 现在装配入口把注册 id
 * 绑进描述符，字段与注册处**同源同值**。
 */
    public String getId() {
        String bound = boundId();
        return bound != null ? bound : declaredId;
    }

 /**
 * **基础物品**（阶段 7 · C 步 · 默认实现）：由图标 / 显示名 / 描述生成热键栏物品底稿 ——
 * 材质 = {@link #getIcon()}、显示名 = {@link #getDisplayName()}、lore = 单行 {@link #getDescription()}。
 * <p><b>阶段 8 起</b>：它只提供**底稿**（材质 / 名称 / 描述），状态装饰
 * （三态材质覆盖 / 名称颜色与加粗 / 冷却秒数或后缀 / 状态行 lore / 分隔线 / 两个 PDC 键）
 * 由 `core/Skill#buildItem()` 与 `core/MainWeapon#buildItem()` 施加，因此本方法**不碰**这些冻结面。
 * <p>需要特殊底稿的组件：在自己的嵌套 `Specification` 里覆写本方法即可。
 */
    public ItemStack baseItem(String id) {
        ItemStack stack = new ItemStack(icon);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(displayName);
            meta.lore(description != null ? List.of(description) : List.of());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public Component getDescription() {
        return description;
    }

    public Material getIcon() {
        return icon;
    }

    public int getCooldownTicks() {
        return cooldownTicks;
    }

    public int getEnergyCost() {
        return energyCost;
    }
}
