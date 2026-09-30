package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nonnegative;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * **深度癔症**（苍鹭被动）：每次攻击 / 使用技能叠 1 层「创伤」，满 {@link #RESOLVE_COUNT} 层结算一次
 * —— 消耗 4 层、回 12 SAN 值、给 4 点不可叠加护盾（原版 {@code ABSORPTION} 增幅 {@link #ABSORPTION_LEVEL}，
 * 由于每次刷新都覆盖同一个效果，因此天然不可叠加）。
 *
 * <h2>结算通知（本类唯一的对外口）</h2>
 * 本被动自己不认识任何具体技能：它只维护一份「结算时通知谁」的名单
 * （{@link #addResolveListener} / {@link #removeResolveListener}），
 * 「结算之后还要做什么」归订阅方自己实现。
 * <p>当前唯一订阅方 = 苍鹭的「湛蓝命运」：它在施放后的 8 秒窗口里，
 * 每次收到本通知就给自己补一次 8 秒伤害吸收 2。
 * <p>形态与既有 {@code EnergyComponent} 的监听名单一致（JDK {@code Consumer} + owner 成对登记），
 * 因此不为「已经是组件」的关注点新造能力接口。
 */
public class CangluHysteriaPassive extends PassiveSkill {

    public static final String ID = "cangluHysteriaPassive";

    private int stackCount;

    private static final int MAX_STACK_COUNT = 24;
    private static final int RESOLVE_COUNT = 4;

    private static final int SAN_RECOVER_AMOUNT = 4;
    private static final int ABSORPTION_TIME = 160;
    private static final int ABSORPTION_LEVEL = 1;

    /**
     * 一条结算通知登记：{@code owner}（谁订阅）+ {@code callback}（怎么通知）成对持有。
     * <p>{@code owner} 只为诊断与「按订阅者撤销」而存在（口径同 {@code EnergyComponent.Listener}）。
     */
    public record ResolveListener(RoleComponent owner, Consumer<CangluHysteriaPassive> callback) { }

    /** 结算通知名单（顺序 = 登记先后；通知时取快照，遍历途中增删安全）。 */
    private final List<ResolveListener> resolveListeners = new ArrayList<>();

    private SanTEComponent sante;
    private BuffComponent buff;

    BossBar bossbar;




    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public CangluHysteriaPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification<CangluHysteriaPassive> {

        public Specification(){
            super(Component.text("创伤"), List.of(Component.text("苍鹭的创伤被动，给自己叠层数")));
        }

        @Override
        public CangluHysteriaPassive create(String id, ComponentServicesPort services){
            return new CangluHysteriaPassive(id, services, this);
        }
    }

    @Override
    public void awake(){
        super.awake();
        stackCount = 0;
    }

    @Override
    public void start(){
        super.start();
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);



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
        float progress;
        if(stackCount <= 0){
            progress = 0f;
        }
        else{
            progress = (float) stackCount / MAX_STACK_COUNT;
        }
        bossbar.progress(progress);
        bossbar.name(Component.text("创伤 " + stackCount + "/" + MAX_STACK_COUNT, NamedTextColor.BLUE, TextDecoration.BOLD));
    }

    @Override
    public void stop()  {
        super.stop();
        sante = null;
        buff = null;
        //订阅方组件已随本实例一起销毁，把它们留下的登记一并丢掉（避免悬挂引用）
        resolveListeners.clear();
        svc().self().player().hideBossBar(bossbar);
        bossbar = null;
    }

    /**
     * **叠加「创伤」**：按上限 clamp 后写回层数；**层数达到上限（满层）时立刻结算一次**
     * （「第 4 层创伤时结算」这条规则的兜底：层数既然已经堆满，就不可能还欠着一次结算）。
     *
     * <p>为什么要在这里兜底：{@code requestResolve()} 是显式口，而写层数的调用方各有各的节奏
     * （主武器每次攻击显式结算一次、澜冰左轮的命中只叠层、湛蓝命运一次叠满）。
     * 若只叠层不判，就会出现「层数满了却永远不结算」的状态 —— 而满层正是需求里
     * 「湛蓝命运叠满创伤」必须当场兑现一次深度癔症的那个时点。
     *
     * <p>幂等性：结算后层数降到上限以下，因此同一次「叠满」只会结算一次；
     * 显式结算（主武器那条路）与这里的兜底不会重复触发同一个第 4 层。
     */
    public void requestAddStackCount(@Nonnegative int amount){
        stackCount = Math.clamp(stackCount + amount, 0, MAX_STACK_COUNT);
        tryAutoResolveAtCap();
    }

    /**
     * **把「创伤」顶到上限，并结算一次**（湛蓝命运的「叠满创伤」走这条）。
     *
     * <p>与 {@link #requestAddStackCount(int)} 的差别只在**收尾的层数**：
     * 前者是"加多少算多少"（满了就地结算，因此加满之后会掉到 20）；
     * 本方法是"结算归结算、层数照样顶满" —— 需求要的形态是**满层 24/24** 同时
     * 当场兑现一次深度癔症（护盾 + SAN 回复 + 通知订阅方），两件事都要。
     *
     * <p>顺序：先顶满并结算（这样订阅方在结算里读到的层数就是满层），结算完再把层数复位回满。
     * 复位只写层数、**不重复触发结算**，所以不会多给一份护盾。
     *
     * @return 本次是否真的结算了一次（层数本来就满、或结算条件不成立时为 {@code false}）
     */
    public boolean requestRefillStacks(){
        stackCount = MAX_STACK_COUNT;
        boolean resolved = requestResolve();
        //层数复位：结算扣掉的 4 层补回来，最终停在满层
        stackCount = MAX_STACK_COUNT;
        return resolved;
    }

    /** 满层即结算（{@link #requestAddStackCount(int)} 的兜底逻辑；单独成方法便于两条入口共用）。 */
    private void tryAutoResolveAtCap(){
        if(stackCount >= MAX_STACK_COUNT){
            requestResolve();
        }
    }

    /**
     * **结算一次「深度癔症」**：层数不足 {@link #RESOLVE_COUNT} 则什么都不做（返回 {@code false}）。
     * <p>成立时按顺序：扣 4 层 → 不可叠加护盾（原版 {@code ABSORPTION}，每次覆盖同一效果）→ 回 12 SAN 值
     * → 逐个通知订阅方（{@link #addResolveListener}）。
     * <p>通知放在最后：订阅方读到的层数已是结算后的值。
     */
    public boolean requestResolve(){
        if(stackCount < RESOLVE_COUNT) return false;
        stackCount -= RESOLVE_COUNT;
        buff.applyPotionEffect(PotionEffectType.ABSORPTION.createEffect(ABSORPTION_TIME, ABSORPTION_LEVEL));
        sante.increase(SAN_RECOVER_AMOUNT);
        notifyResolveListeners();
        return true;
    }

    /** 当前「创伤」层数（读口；诊断与自检用）。 */
    public int stackCount(){
        return stackCount;
    }

    /** 层数上限（读口）。 */
    public static int maxStackCount(){
        return MAX_STACK_COUNT;
    }

    /**
     * 登记一个「结算通知」订阅者（幂等）。
     *
     * <p>本被动是「深度癔症」这件事的唯一持有者，因此「它什么时候结算」也只能由它通知；
     * 订阅方（例如「湛蓝命运」）拿到通知后自己决定做什么 —— 本被动不认识任何技能类。
     *
     * @param owner    订阅者（诊断 / 撤销用；{@code null} 则忽略）
     * @param callback 收到通知时调用（参数 = 本被动自己；{@code null} 则忽略）
     * @return 本次登记对应的实例（用 {@link #removeResolveListener} 按引用撤销）；参数为 null 时回 {@code null}
     */
    public ResolveListener addResolveListener(RoleComponent owner, Consumer<CangluHysteriaPassive> callback){
        if(owner == null || callback == null) return null;
        ResolveListener entry = new ResolveListener(owner, callback);
        if(!resolveListeners.contains(entry)) resolveListeners.add(entry);
        return entry;
    }

    /** 撤销登记（按引用相等；不在名单里则 no-op）。 */
    public boolean removeResolveListener(ResolveListener entry){
        return entry != null && resolveListeners.remove(entry);
    }

    /** 逐个通知（对名单取快照后再遍历：通知途中的增删不影响本次）。 */
    private void notifyResolveListeners(){
        for(ResolveListener entry : List.copyOf(resolveListeners)){
            entry.callback().accept(this);
        }
    }
}
