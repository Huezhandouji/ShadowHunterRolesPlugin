package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;

/**
 * 黯然销魂（红）：持续扣减红的 SanTE，归零前给自己回血与力量。
 * <p><b>迁移口径</b>（四类 `*Aware` 实现者归零）：
 * <ul>
 *   <li>去 legacy `UpdateAware` 与 `SanTEChangeAware` ⇒ 改走基类新钩子 {@link #update()} 与
 *       {@link #onSanTEChange(int, int)}（容器对**注册表内组件**广播；SanTE 侧为"真变化才派发"，
 * 由既有实现完成、不新增可见变化）；</li>
 *   <li>聚合根调用端口化：`instance.decreaseSanTE(10)` → **SanTE 组件**的 `decrease(10)`（直接用组件）；
 *       `instance.applyPotionEffect(…createEffect(45, 5/2))` → **Buff 组件**的 `applyPotionEffect(type, 45, 5/2)`（同上）
 *       （**同一条已记账路径**）；`instance.startSkillCooldown(getId(), getCooldownTicks())` →
 *       **组件自持冷却**的 `startCooldown()`（状态归组件实例、框架只**转问** ✗）；</li>
 *   <li>**数值与间隔逐字不变**：冷却 `600` / 能量 `0` / 每秒（`20` tick）一结算 / 扣 `10` 点 SanTE /
 *       生命恢复 `45, 5` 与力量 `45, 2` / 音效 `ENTITY_WITHER_DEATH 2,1` 与 `ENTITY_WITHER_SHOOT 1,1`；</li>
 * </ul>
 */
public class RedDeeplySorrowSkill extends Skill {

    private BuffComponent buff;
    private SanTEComponent sante;

    /**
     * **本组件在 {@code SanTEComponent} 上的监听登记** —— 由 {@code start()} 里
     * {@code addListener} 的**返回值**填入，供 {@code stop()} 按**引用相等**移除 ✓
     * （{@code Consumer} 无身份标识 ⇒ 必须持有同一实例 ✓）。
     */
    private SanTEComponent.Listener santeListener;

    //该技能是否在执行中
    private boolean running = false;

    private int secondTickCount = 0;

    public RedDeeplySorrowSkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * **订阅 SanTE 变更**：**向 {@code SanTEComponent} 添加一条监听** ✓，而**不是**在类声明上
     * `implements` 某个接口 ✗ —— 后者正是用户点名的方向错误（`SanTE` 早已是组件 ⇒
     * **不得再为它新增能力接口** ✗）。
     * <p><b>时机 = {@code start()}</b>（`awake()` 只做构造期自检 / 只读自身，**不得取用其他组件** ✗），
     * 并与 {@link #stop()} 的移除成对 ✓（`addListener` 幂等 ⇒ 重复 start 不会重复登记 ✓）。
     * <p><b>通知顺序</b> = **添加先后** = 容器 `start()` 广播序（= 组件装配序）。
 * <b>与 {@code awake()} 是否同序需另证</b>（未做运行级取证）⇒ 不再宣称"awake 序" ✗。
     * <p>容器查找（而不是字段注入）⇒ 本组件**不持有** `SanTEComponent` 引用 ✓。
     * <p><b>监听登记实例存进 {@link #santeListener}</b>：{@code Consumer} 无身份标识 ⇒ 必须持有同一实例才能按引用移除 ✓。
     * —— 订阅已迁到 `start()` ⇒ 该表述**作废** ✗。
     * —— 该嵌套接口与 `subscribe` 入口**已删除** ✗（改为 JDK {@code Consumer} 监听器列表）⇒ 该表述**作废** ✗。
     */
    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        if (sante != null) {
            santeListener = sante.addListener(this, this::onSanTEChange);
        }
    }

    /**
     * 本组件的**描述符**：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(Component.text("黯然销魂"),
                    Component.text("持续扣减[红]的TE值，每秒10点，在TE值归零前获得持续的生命恢复5与力量2，在TE值归零后结束这个技能"),
                    600, 0, Material.REDSTONE_BLOCK);
            requires(BuffComponent.class);
            //sante 实取于 start()（:66）但代码自带 null 兜底（:67 订阅 / :141 退订）⇒ 按「实取但可为空」声明为**可选**；
            //它在 FRAMEWORK_PROVIDED_TYPES 白名单内、ServiceComponents.build 无条件构造 ⇒ 生产环境永不缺失（optional 与实际效果等价）
            requiresOptional(SanTEComponent.class);
        }

        @Override
        public RedDeeplySorrowSkill create(String id, ComponentServices services){
            return new RedDeeplySorrowSkill(id, services, this);
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        if(signal.trigger() != CastTrigger.RIGHT_CLICK) return;
        if(!buff.canCastSkill()) return;
        running = true;
        svc().self().player().getWorld().playSound(svc().self().player().getLocation().clone(), Sound.ENTITY_WITHER_DEATH, 2, 1);
        //冷却 600 由本组件在施放成功处按声明值启动（所有组件无条件走新管道）
        startCooldown();   //D1：组件自启冷却（框架不再代启动）
    }

    @Override
    public void update() {
        if(!running) return;

        secondTickCount++;
        if(secondTickCount < 20) return;
        secondTickCount = 0;

        Player caster = svc().self().player();

        sante.decrease(10);
        //药水记账：经 **Buff 组件**的入口（与框架**同一条已记账路径**），clear() 时只回收本系统施加的效果
        buff.applyPotionEffect(PotionEffectType.REGENERATION, 45, 5);
        buff.applyPotionEffect(PotionEffectType.STRENGTH, 45, 2);
        startCooldown();

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);
    }

    /**
     * **SanTE 变更回调**：入参为载荷 {@link SanTEComponent.Change}（旧形态的两个值
     * {@code (int pre, int now)} 装在一个 record 里 ✓，语义**逐字保留**：只看 {@code current <= 0} 那一支 ✓）。
     * <p><b>不再 {@code @Override}</b>：本方法**不再实现任何接口** ✗（旧 {@code SanTEComponent.Subscriber}
     * 已删除）⇒ 它是本类的**普通方法**，由 {@code start()} 里的方法引用
     * {@code this::onSanTEChange} 注册进监听器列表 ✓。
     */
    public void onSanTEChange(SanTEComponent.Change change) {
        if(change.current() <= 0){
            running = false;
            startCooldown();
        }
    }

    /**
     * **移除监听**：与 {@link #start()} 的添加**成对** ✓ —— 拆卸后不再被通知。
     * <p>框架在拆卸时调用 `stop()`（`cancelAllAndClear()` 兜底回收资源 ⇒ 本方法幂等 ✓；
     * {@code removeListener} 对不在名单里的登记是 no-op 且返回 {@code false} ✓ —— 与旧
     * {@code unsubscribe} 的 no-op 语义逐字一致）。
     */
    @Override
    public void stop() {
        if (sante != null && santeListener != null) {
            sante.removeListener(santeListener);
            santeListener = null;
        }
    }

    /**
     * **闸门放行？**（基类不再取 buff ⇒ 由本组件用**自己的字段**判）。
     */
    @Override
    protected boolean gateOpen(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**：本组件**不参与能量维度**（声明耗能 0）⇒ 返回声明值；
 * 与既有实现**逐字等价**（能量组件内 clamp 到 `[0, max]` ⇒ 原判定 `current() < 0` 恒假）。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
