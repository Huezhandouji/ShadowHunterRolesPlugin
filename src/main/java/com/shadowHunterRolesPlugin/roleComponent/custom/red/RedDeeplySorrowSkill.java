package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import java.util.List;

/**
 * 黯然销魂（红）：持续扣减红的 SanTE，归零前给自己回血与力量。
 * <p>扣 SanTE 走 SanTE 组件的 `decrease(10)`；回血与力量走 Buff 组件的 `applyPotionEffect(type, 45, 5/2)`
 * （同一条已记账路径）；冷却由组件自持的 `startCooldown()` 启动（状态归组件实例、框架只转问）。
 * <p>数值与间隔：冷却 `600` / 能量 `0` / 每秒（`20` tick）一结算 / 扣 `10` 点 SanTE /
 * 生命恢复 `45, 5` 与力量 `45, 2` / 音效 `ENTITY_WITHER_DEATH 2,1` 与 `ENTITY_WITHER_SHOOT 1,1`。
 */
public class RedDeeplySorrowSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "red_deeplySorrow_skill";

    private BuffComponent buff;
    private SanTEComponent sante;

    /**
     * 本组件在 {@code SanTEComponent} 上的监听登记：由 {@code start()} 里 {@code addListener} 的返回值
     * 填入，供 {@code stop()} 按引用相等移除（{@code Consumer} 无身份标识，必须持有同一实例）。
     */
    private SanTEComponent.Listener santeListener;

    //该技能是否在执行中
    private boolean running = false;

    private int secondTickCount = 0;

    public RedDeeplySorrowSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * **订阅 SanTE 变更**：向 {@code SanTEComponent} 添加一条监听，而不是在类声明上 `implements`
     * 某个接口 —— SanTE 的家是组件，不得再为它新增能力接口。
     * <p><b>时机 = {@code start()}</b>（`awake()` 只做构造期自检 / 只读自身，不得取用其他组件），
     * 并与 {@link #stop()} 的移除成对（`addListener` 幂等，因此重复 start 不会重复登记）。
     * <p><b>通知顺序</b> = 添加先后 = 容器 `start()` 广播序（= 组件装配序）；
     * 与 `awake()` 是否同序未做运行级取证，故不宣称"awake 序"。
     * <p><b>监听登记实例存进 {@link #santeListener}</b>：{@code Consumer} 无身份标识，
     * 必须持有同一实例才能按引用移除。
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
     * 本组件的描述符：名字 / 描述 / 冷却 / 耗能 / 图标由这里声明，
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification<RedDeeplySorrowSkill> {

        public Specification(){
            super(Component.text("黯然销魂"),
                    List.of(Component.text("持续扣减[红]的TE值，每秒10点，在TE值归零前获得持续的生命恢复5与力量2，在TE值归零后结束这个技能")),
                    600, 0, Material.REDSTONE_BLOCK);
            requires(BuffComponent.class);
            //sante 实取于 start() 但代码自带 null 兜底（订阅 / 退订两处）⇒ 按「实取但可为空」声明为**可选**；
            //它在装配期的「已被提供类型」清单内、由容器无条件构造 ⇒ 生产环境永不缺失（optional 与实际效果等价）
            requiresOptional(SanTEComponent.class);
        }

        @Override
        public RedDeeplySorrowSkill create(String id, ComponentServicesPort services){
            return new RedDeeplySorrowSkill(id, services, this);
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        if(signal.trigger() != CastTrigger.RIGHT_CLICK) return;
        if(!buff.canCastSkill()) return;
        running = true;
        svc().self().player().getWorld().playSound(svc().self().player().getLocation().clone(), Sound.ENTITY_WITHER_DEATH, 2, 1);
        //冷却 600 由本组件在施放成功处按声明值启动
        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    @Override
    public void update() {
        if(!running) return;

        secondTickCount++;
        if(secondTickCount < 20) return;
        secondTickCount = 0;

        Player caster = svc().self().player();

        sante.decrease(10);
        //药水记账：经 Buff 组件的入口（与框架同一条已记账路径），clear() 时只回收本系统施加的效果
        buff.applyPotionEffect(PotionEffectType.REGENERATION, 45, 5);
        buff.applyPotionEffect(PotionEffectType.STRENGTH, 45, 2);
        startCooldown();

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);
    }

    /**
     * **SanTE 变更回调**：入参是载荷 {@link SanTEComponent.Change}（两个值装在一个 record 里），
     * 只看 {@code current <= 0} 那一支。
     * <p>本方法**不再 {@code @Override}**：它不实现任何接口，是本类的普通方法，
     * 由 {@code start()} 里的方法引用 {@code this::onSanTEChange} 注册进监听器列表。
     */
    public void onSanTEChange(SanTEComponent.Change change) {
        if(change.current() <= 0){
            running = false;
            startCooldown();
        }
    }

    /**
     * **移除监听**：与 {@link #start()} 的添加成对 —— 拆卸后不再被通知。
     * <p>框架在拆卸时调用 `stop()`（`cancelAllAndClear()` 兜底回收资源），因此本方法幂等：
     * {@code removeListener} 对不在名单里的登记是 no-op 并返回 {@code false}。
     */
    @Override
    public void stop() {
        if (sante != null && santeListener != null) {
            sante.removeListener(santeListener);
            santeListener = null;
        }
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**：本组件不参与能量维度（声明耗能 0），因此返回声明值；
     * 能量组件的值被 clamp 到 `[0, max]`，故 `current() < 0` 恒假。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
