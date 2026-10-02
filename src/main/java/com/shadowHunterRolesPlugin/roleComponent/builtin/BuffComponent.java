package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * buff 组件：系统级能力「buff 施加 / 查询 / 闸门 / 药水记账」的组件形态（每角色实例一个）。
 *
 * <h2>本组件持有状态与行为</h2>
 * <ul>
 *   <li>buff 记账表 = {@link BuffManager} 实例，由本组件在构造期自建；该类属 {@code manager/} 包、
 *       不是组件，本组件只持有它、不改它；</li>
 *   <li>药水记账账本 = {@link #appliedPotionTypes}：{@code clear()} 时只回收账本内的类型（语义逐字保留）；</li>
 *   <li>施加路径 = {@link #applyPotionEffect(PotionEffect)}（{@code player.addPotionEffect} + 记账）：
 *       Bukkit API 属允许依赖的外部东西；</li>
 *   <li>撤销路径 = {@link #removePotionEffect(PotionEffectType)}（单个类型：效果与账本项一起摘掉 ——
 *       绕过它直接 {@code player.removePotionEffect} 会在账本里留下陈旧类型）；</li>
 *   <li>负面效果清理 = {@link #clearDebuff()}（转发到 {@link BuffManager#clearDebuffs()}）：
 *       清插件侧负面 buff + 玩家身上 {@code HARMFUL} 分类的原版药水效果。</li>
 * </ul>
 * 调用点一律直接用本组件（不经服务集端口转发）。
 *
 * <h2>★ 两种目标：自己 / 别人（两套口，一条实现）</h2>
 * 本组件有两组入口，语义完全相同，差别只在"效果落在谁身上"：
 * <ul>
 *   <li><b>自己</b> = {@link #add(BuffType, int)} / {@link #has(BuffType)} / {@link #remove(BuffType)} /
 *       {@link #applyPotionEffect(PotionEffect)} —— 作用对象恒为 {@code svc().self().player()}；</li>
 *   <li><b>别人</b> = {@link #addTo(Player, BuffType, int)} / {@link #hasOn(Player, BuffType)} /
 *       {@link #removeFrom(Player, BuffType)} / {@link #applyPotionEffectTo(Player, PotionEffect)} 等
 *       —— 作用对象 = 传进来的那个玩家。</li>
 * </ul>
 * <p><b>关键：跨玩家不是"往目标身上直接写"，而是把这次写入交给目标自己那份 buff 组件</b>
 * （{@link #targetOf(Player)} 解析）。因此目标那一侧的账本、闸门、属性修饰符、药水记账、
 * {@code IMMUNE} 免疫、buff 到期与"角色清除时回收"全部照常生效 —— 本组件不复制第二套记账，
 * 也不开"绕开账本直接 {@code addPotionEffect}"的口子。
 * <p><b>目标无角色 ⇒ 一律不施加</b>（回 {@code false}）：buff 的家是"角色实例上的 buff 组件"，
 * 没有角色就没有账本 —— 既没人记、也没人回收，写下去只会留下一份孤儿效果。
 * 这条口径与 {@code SanTEComponent#increaseSanTE} / {@code EnergyComponent#increaseEnergy} 的
 * 跨实例入口同源（未命中目标组件 ⇒ 回 0 / false，什么都不做）。
 */
public class BuffComponent extends RoleComponent implements OperationProvider {

    /**
     * 本组件的登记 id（知识归属：组件自己 —— 谁是什么 id 由谁说了算）。
     * <p>容器装配时只读这个 id + 工厂（{@code data}），不点名组件类。
     */
    public static final String ID = "buffs";

    private final BuffManager buffManager;

    /** 药水记账账本（{@code clear()} 时只回收此表内的类型）。 */
    private final Set<PotionEffectType> appliedPotionTypes = new LinkedHashSet<>();

    /**
     * 生产构造：记账表由本组件自建（不再由容器 {@code new} 好再交进来）。
     *
     * <p>它与本组件成对存在：账本需要「持有者」这个引用，才能在不回容器的前提下请求重绘 / 交药水记账，
     * 故构造期一次建好并接上（{@code this} 在此只被存引用、未被调用，因此无构造期逃逸）。
     *
     * <p>调度器 = 平台面（{@link BukkitSchedulerAdapter}，底层 Paper 的
     * {@code GlobalRegionScheduler}），不是容器，因此本组件不依赖 {@code RoleInstance}。
     */
    public BuffComponent(String id, ComponentServicesPort services) {
        super(id, services);
        this.buffManager = new BuffManager(svc().self().player(), this, new BukkitSchedulerAdapter(
                Bukkit.getPluginManager().getPlugin("ShadowHunterRolesPlugin")));
    }

    /** 本组件的装配描述符（与技能/被动同规；不带栏位、无额外依赖 —— 记账表由组件自建）。 */
    public static final class Specification extends RoleComponent.Specification<BuffComponent> {

        public Specification() {
            super("Buff");
        }

        @Override
        public BuffComponent create(String id, ComponentServicesPort services) {
            return new BuffComponent(id, services);
        }
    }

    /**
     * 测试接缝（包私有）：注入一个替身记账表，供离线单测构造（不需要活 Player）。
     * <p>生产路径只用上面的公开构造，因此记账表由本组件自建。
     */
    BuffComponent(String id, ComponentServicesPort services, BuffManager stub) {
        super(id, services);
        this.buffManager = stub;
    }

    /** buff 记账表本体（容器 {@code clear()} 仍需它做 {@code clearAll()} ⇒ 提供读口）。 */
    public BuffManager manager() {
        return buffManager;
    }

    // ───────── 生命周期：本组件自己的两个节拍（容器不再代劳）─────────

    /**
     * 开始生效：启动记账表的每 tick 更新（两阶段构造的第二相）。
     *
     * <p>必须在此启动、不能放构造期：构造期不得创建任何任务（构造中途抛错会泄漏永久 ticker），
     * 因此记账表的更新只能在可见相启动（与 {@code BuffManager} 自己的契约同源）。
     *
     * <p>提交顺序与既有实现相同：本 {@code start()} 由 {@code triggerLifecycleStart()} 按注册序广播，
     * 而记账表更新先于实例 ticker 启动，因此同一 tick 内先跑记账、再跑组件 {@code update()}。
     */
    @Override
    public void start() {
        buffManager.startUpdater();
        //buff 移除后的重绘由渲染组件自己订阅（它认识本组件），因此本组件不做任何渲染相关动作
    }

    /**
     * 停止生效：回收本组件持有的两本账（药水账本 + buff 记账表）。
     *
     * <p>顺序逐字沿用既有实现：① 先只移除本系统记账过的药水效果
     * （不再无条件清空玩家身上的所有药水）② 再清账本。
     */
    @Override
    public void stop() {
        clearAppliedPotionEffects();
        clearBuffLedger();
    }

    /**
     * 清空 buff 记账表（本组件自己的状态自己回收）。
     * <p>语义逐字一致：{@code manager().clearAll()}。
     */
    public void clearBuffLedger() {
        buffManager.clearAll();
    }

    /** 技能闸门（可否施放技能）。 */
    public boolean canCastSkill() {
        return buffManager.canCastSkill();
    }

    /** 主武器闸门（可否使用主武器）。 */
    public boolean canUseMainWeapon() {
        return buffManager.canUseMainWeapon();
    }

    /**
     * 施加 buff（时长 = 游戏刻）—— 作用对象 = 本组件的玩家自己。
     *
     * <p>回 {@code true} ⇔ 本组件的账本状态真的变了（新增 / 时长被延长）。
     * {@code false} 的三种情形见 {@link BuffManager#addBuff(BuffType, int)}：
     * 处于 {@code IMMUNE} 下、已有更长的同类 buff、{@code type} 为 {@code null}。
     * <p>原为 {@code void}：既有调用点（技能 / 主武器 / 惩罚组件）全部丢弃返回值，改签名不影响它们。
     */
    public boolean add(BuffType type, int durationTicks) {
        return buffManager.addBuff(type, durationTicks);
    }

    /**
     * 移除 buff —— 作用对象 = 本组件的玩家自己（{@link #addTo(Player, BuffType, int)} 对别人）。
     *
     * <p>幂等：没有该 buff 也照常回收它带出的原版效果与属性修饰符
     * （{@link BuffManager#removeBuff(BuffType)} 的既有语义，属"无条件撤销"）。
     * {@code type} 为 {@code null} 时不动（调用方 bug，不抛）。
     */
    public void remove(BuffType type) {
        if (type == null) {
            return;
        }
        buffManager.removeBuff(type);
    }

    /** 是否处于该 buff 下。 */
    public boolean has(BuffType type) {
        return buffManager.hasBuff(type);
    }

    /** 剩余刻（无该 buff ⇒ 0）。 */
    public long remainingTicks(BuffType type) {
        return buffManager.getRemainingTicks(type);
    }

    /**
     * 施加药水效果（已记账路径；参数顺序 = 时长在前、增幅在后，与既有端口逐字一致）。
     * <p>回平台是否真的装上（口径见 {@link #applyPotionEffect(PotionEffect)}）；既有调用点全部丢弃返回值。
     */
    public boolean applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier) {
        return applyPotionEffect(type.createEffect(durationTicks, amplifier));
    }

    /**
     * 同前，但 {@code ambient}/{@code particles} 标志位逐字进入效果对象（5 参构造的 {@code icon} 默认 true）。
     * <p>回平台是否真的装上（口径同前）。
     */
    public boolean applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier,
                                     boolean ambient, boolean particles) {
        return applyPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /**
     * 账本写入的唯一入口（自己那一侧）。
     *
     * <p><b>返回值 = 平台是否真的把效果装上了</b>（{@code player.addPotionEffect} 的既有返回值）。
     * <p><b>账本照记不误</b>（与"此刻身上真有什么"本就是两个口径）：例如处于 {@code IMMUNE} 时
     * {@code listener/hook/ImmunePotionListener} 会取消这次原始施加、平台回 {@code false}，
     * 但类型仍进账本 —— 否则角色清除时就漏收这一条（{@link #clearAppliedPotionEffects()} 只回收账本内的类型）。
     * <p>原为 {@code void}：既有 20 余处调用点全部丢弃返回值（技能 / 被动 / 主武器 / {@code BuffManager} 内部），改签名不影响它们。
     */
    public boolean applyPotionEffect(PotionEffect effect) {
        if (effect == null) {
            return false;
        }
        boolean applied = svc().self().player().addPotionEffect(effect);
        appliedPotionTypes.add(effect.getType());
        return applied;
    }

    /** 账本内的类型数（读口，供取证/诊断）。 */
    public int appliedPotionTypeCount() {
        return appliedPotionTypes.size();
    }

    /**
     * 回收账本内的全部药水：只移除本系统记账过的类型，不无条件清空玩家身上的所有药水效果。
     * 返回移除的类型数。
     */
    public int clearAppliedPotionEffects() {
        int removed = appliedPotionTypes.size();
        Player player = svc().self().player();
        for (PotionEffectType type : appliedPotionTypes) {
            player.removePotionEffect(type);
        }
        appliedPotionTypes.clear();
        return removed;
    }

    /**
     * 显式摘掉**某一个**原版药水类型（自己那一侧）—— 效果与药水账本**同时**摘掉。
     *
     * <p><b>为什么把它收进本组件</b>：药水账本的持有者只有一个。调用点原先直接
     * {@code player.removePotionEffect(type)} 也能把效果摘掉，但账本里会留下一个**已经不在身上的类型**
     * （陈旧项）：它没有直接后果（后续 {@link #clearAppliedPotionEffects()} 只是再移除一次不存在的效果），
     * 却让"账本 = 本系统施加过、仍可能存在的效果"这条读数失真。
     *
     * <p>作用域窄：只摘这一个类型。要清**全部**本系统记过的类型用 {@link #clearAppliedPotionEffects()}；
     * 要清负面（插件侧 + 原版 {@code HARMFUL}）用 {@link #clearDebuff()}。
     *
     * @param type 要摘掉的药水类型；{@code null} ⇒ 什么都不做
     * @return 该类型此前是否在**账本**里（= 本系统记过它；不代表"玩家身上当时真的有"）
     */
    public boolean removePotionEffect(PotionEffectType type) {
        if (type == null) {
            return false;
        }
        svc().self().player().removePotionEffect(type);
        return appliedPotionTypes.remove(type);
    }

    /**
     * 清空负面效果：**插件侧** buff 账本里的负面项（{@link BuffType#isDebuff() STUN / SILENCE}，
     * 各自带出的原版药水与属性修饰符一并回收）+ **玩家身上的原版负面药水**
     * （{@code HARMFUL} 分类的全部效果）。
     *
     * <p>本组件只做转发：真值在记账表（{@link BuffManager#clearDebuffs()}）——
     * 与其余读 / 写口同一形态（组件不自己持有 buff 状态）。
     *
     * <p><b>免疫类不动</b>：{@code IMMUNE} 不是负面效果 ⇒ 不会被本方法清掉。
     * 反过来说，"给自己上免疫"这条路径会把本方法当净化步骤用（{@code BuffManager#addImmune}）。
     *
     * @return 被清掉的条数（口径见 {@link BuffManager#clearDebuffs()}）= 插件侧每个被移除的负面 buff 记 1
     *         （它带出的原版药水随它计入，不重复计数）+ 此外仍在身上的原版 {@code HARMFUL} 类型各记 1
     */
    public int clearDebuff() {
        return buffManager.clearDebuffs();
    }

    // ───────── 跨实例：把 buff / 原版药水施加到**别的玩家**身上 ─────────
    //
    // 口径（与 SanTEComponent#increaseSanTE / EnergyComponent#increaseEnergy 的跨实例入口同源）：
    //   ① 一律"交给目标自己那份组件去做"，本组件不复制第二套账本；
    //   ② 目标无角色实例 / 容器里没有 buff 组件 ⇒ 什么都不做，写类回 false、读类回 false/0。
    //
    // 为什么收 Player 而不是 UUID：与 VitalsComponent#damage/heal 同规 —— 技能手里本来就是 Player
    // （索敌、投射物命中的结果），多一步 getUniqueId 只是噪音；解析内部仍按 UUID 走，
    // 因此血统与 SanTE/Energy 那条路完全一致。目标为 null 一律按"未命中"处理（回 false/0，不抛）。

    /**
     * 给**目标玩家**施加插件侧 buff（{@code STUN} / {@code SILENCE} / {@code IMMUNE}），
     * 并返回这次施加是否真的落地。
     *
     * <p><b>与 {@link #add(BuffType, int)} 的关系</b>：语义逐字相同，只换了作用对象 ——
     * 本方法等价于"先在目标身上解析出他那份 buff 组件，再对它调 {@code add}"。
     * 因此目标那一侧的账本、技能 / 主武器闸门、{@code STUN} 带出的原版失明 / 黑暗与移速修饰符、
     * 免疫闸门、到期回收、角色清除时的一并回收，全部照常生效。
     *
     * <p><b>返回 {@code false} 的三种情形</b>：
     * <ul>
     *   <li>目标为 {@code null} / 不在线 / <b>没有角色实例</b> ⇒ 没有可挂的账本，<b>不施加</b>；</li>
     *   <li>目标此刻处于 {@code IMMUNE} 下 ⇒ 免疫挡下新来的负面 buff（既有语义，不绕过）；</li>
     *   <li>目标已有更长的同类 buff ⇒ 按"取最大时长"语义，本次请求不产生变化。</li>
     * </ul>
     * <p>换言之：{@code true} 才代表"这一下真的生效了"。调用方（例如"晕住他就追加后续结算"的技能）
     * 可以据此决定要不要走后续分支。
     *
     * @param target        目标玩家（可以是自己 —— 那与 {@link #add(BuffType, int)} 等价）
     * @param type          buff 类型；{@code null} ⇒ 回 {@code false}
     * @param durationTicks 时长（游戏刻）
     */
    public boolean addTo(Player target, BuffType type, int durationTicks) {
        BuffComponent other = targetOf(target);
        return other != null && other.add(type, durationTicks);
    }

    /**
     * 从**目标玩家**身上移除插件侧 buff（{@link #remove(BuffType)} 的跨玩家版本）。
     *
     * <p>幂等：目标本来没有该 buff 也照常撤销它带出的原版效果与属性修饰符（既有语义）。
     *
     * @param target 目标玩家
     * @param type   buff 类型；{@code null} ⇒ 回 {@code false}
     * @return 目标是否有可受理本次移除的 buff 系统（即"目标有角色实例且容器里有 buff 组件"）。
     *         注意：{@code true} <b>不</b>表示"目标本来有该 buff"—— 移除是无条件的。
     *         目标没角色 ⇒ {@code false}，且什么都不做。
     */
    public boolean removeFrom(Player target, BuffType type) {
        BuffComponent other = targetOf(target);
        if (other == null || type == null) {
            return false;
        }
        other.remove(type);
        return true;
    }

    /**
     * 查询**目标玩家**是否处于该 buff 下（{@link #has(BuffType)} 的跨玩家版本）。
     *
     * @return 目标是否有该 buff；目标为 {@code null} / 无角色实例 / 没有该 buff 一律 {@code false}
     *         （读类不区分"没有角色"与"没有该 buff"，两者都是"他此刻不处于该 buff 下"）
     */
    public boolean hasOn(Player target, BuffType type) {
        BuffComponent other = targetOf(target);
        return other != null && other.has(type);
    }

    /**
     * 查询**目标玩家**该 buff 的剩余刻（{@link #remainingTicks(BuffType)} 的跨玩家版本）。
     *
     * @return 剩余刻；目标无角色 / 没有该 buff 一律 {@code 0}（口径同 {@link #remainingTicks(BuffType)}）
     */
    public long remainingTicksOn(Player target, BuffType type) {
        BuffComponent other = targetOf(target);
        return other == null ? 0L : other.remainingTicks(type);
    }

    /**
     * 净化**目标玩家**身上的负面效果（{@link #clearDebuff()} 的跨玩家版本）：
     * 插件侧负面 buff（{@code STUN} / {@code SILENCE} 及其带出的原版效果）+ 目标身上的原版负面药水。
     *
     * <p>典型用途：给自己上一发免疫的同时顺手解开队友 / 解开被控的目标。
     *
     * @return 被清掉的条数（口径见 {@link BuffManager#clearDebuffs()}）；目标无角色 ⇒ {@code 0}
     *         （"没清到任何东西"与"清除方没有可挂的账本"同值 —— 该读口本来就不区分二者）
     */
    public int clearDebuffOn(Player target) {
        BuffComponent other = targetOf(target);
        return other == null ? 0 : other.clearDebuff();
    }

    /** 给**目标玩家**施加原版药水效果（{@link #applyPotionEffect(PotionEffectType, int, int)} 的跨玩家版本）。 */
    public boolean applyPotionEffectTo(Player target, PotionEffectType type, int durationTicks, int amplifier) {
        return type != null && applyPotionEffectTo(target, type.createEffect(durationTicks, amplifier));
    }

    /** 同前，但 {@code ambient}/{@code particles} 标志位逐字进入效果对象（5 参构造的 {@code icon} 默认 true）。 */
    public boolean applyPotionEffectTo(Player target, PotionEffectType type, int durationTicks, int amplifier,
                                       boolean ambient, boolean particles) {
        return type != null && applyPotionEffectTo(target,
                new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /**
     * 给**目标玩家**施加原版药水效果（账本路径的跨玩家版本）。
     *
     * <p><b>为什么要经过目标那份组件</b>：原版药水进的是目标自己的药水账本
     * （{@link #appliedPotionTypes}）⇒ 目标角色被清除 / 组件 {@code stop()} 时，
     * 这条效果<b>会被他自己的账本回收</b>；换成本组件直接 {@code target.addPotionEffect(...)}
     * 就会留下一条没人认领的效果（既有的部分技能就这么干，是本方法存在的理由）。
     * <p>因此本方法<b>不</b>把类型记进调用方自己的账本 —— 一个效果只有一个持有者。
     *
     * @return 与 {@link #addTo(Player, BuffType, int)} 同一口径 —— {@code true} ⇔ 效果<b>真的落在了目标身上</b>
     *         （目标有账本，且平台受理了这次写入）。{@code false} 两种情形：
     *         目标无角色实例（没有账本，不施加）；或平台未受理（例如目标处于 {@code IMMUNE} 下时
     *         {@code listener/hook/ImmunePotionListener} 会取消负面的原版药水、{@code addPotionEffect} 回
     *         {@code false}；效果比身上的更弱也会被拒）。
     *         <b>注意账本与"是否真的上了"仍是两个口径</b>：平台回 {@code false} 时目标账本照记不误
     *         （既有语义，见 {@link #applyPotionEffect(PotionEffect)}），否则角色清除时会漏收这一条。
     */
    public boolean applyPotionEffectTo(Player target, PotionEffect effect) {
        BuffComponent other = targetOf(target);
        return other != null && other.applyPotionEffect(effect);
    }

    /**
     * 解析**目标玩家自己**那份 buff 组件（跨实例；未命中一律 {@code null}）。
     *
     * <p>路径与 {@code SanTEComponent} / {@code EnergyComponent} 的跨实例入口同形：经插件单例拿到目标
     * {@code RoleInstance}，再按本组件自己的登记 id（{@link #ID}）取同类实例 —— 不新增端口、
     * 不持有 {@code RoleManager}、不写第二份 id 字面量。
     * <p>目标为 {@code null} / 不在线 / 无角色实例 / 未加载 / 容器里没有 buff 组件 ⇒ {@code null}
     * （调用方按"未命中"处理：写类回 {@code false}、读类回 {@code false} / {@code 0}）。
     *
     * <p><b>包内可见的测试缝</b>（如实申报）：生产只有这一条实现（跨实例解析需要活的插件单例 +
     * 活角色实例，离线不可达）；包内可见只为让离线单测覆写它注入替身，与包私有测试构造器同规。
     */
    BuffComponent targetOf(Player target) {
        if (target == null) {
            return null;
        }
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if (plugin == null) {
            return null;
        }
        RoleInstance instance = plugin.roleInstanceOf(target.getUniqueId());
        if (instance == null) {
            return null;
        }
        RoleComponent component = instance.componentRegistry().getById(ID);
        return component instanceof BuffComponent resolved ? resolved : null;
    }

    /**
     * 把指令里的名字解析成在线玩家（**只认精确名字**）——
     * 走 {@code Bukkit.getPlayerExact}：不做大小写模糊匹配、不做别名、不看离线玩家。
     *
     * <p>不在线 / 名字不存在 ⇒ {@code null}（调用方据此回"未识别"）。
     *
     * <p><b>包内可见的测试缝</b>：同 {@link #targetOf(Player)}（离线环境的 {@code Bukkit} 恒回空，
     * 覆写它才能把指令面整条链跑起来）。
     */
    Player onlinePlayer(String name) {
        return name == null ? null : Bukkit.getPlayerExact(name);
    }

    /**
     * 组件操作面：把外部字符串指令薄适配到本组件既有强类型方法（不新增状态通道）。
     *
     * <h2>grammar（首 token 必为动词，大小写敏感；参数以空白分隔）</h2>
     * <h3>只作用于"自己"（本组件的玩家）</h3>
     * <ul>
     *   <li>{@code can_cast} —— 读（无参）：技能闸门，回 {@code true}/{@code false}；</li>
     *   <li>{@code can_weapon} —— 读（无参）：主武器闸门，回 {@code true}/{@code false}；</li>
     *   <li>{@code has <buffType>} —— 读：是否处于该 buff 下，回 {@code true}/{@code false}；</li>
     *   <li>{@code remaining <buffType>} —— 读：剩余刻（无该 buff ⇒ {@code 0}）；</li>
     *   <li>{@code add <buffType> <ticks>} —— 写：调既有的 {@link #add(BuffType, int)}，回写后剩余刻；</li>
     *   <li>{@code count} —— 读（无参）：药水记账账本内的类型数；</li>
     *   <li>{@code clear} —— 写（无参）：调既有的 {@link #clearAppliedPotionEffects()}，回写后账本数。</li>
     *   <li>{@code clear_debuff} —— 写（无参）：调既有的 {@link #clearDebuff()}，回清掉的条数
     *       （插件侧负面 buff + 原版负面药水）；</li>
     *   <li>{@code effect <potionType> <ticks> <amplifier>} —— 写：调既有的
     *       {@link #applyPotionEffect(PotionEffect)}，回平台是否真的装上（{@code true}/{@code false}）；</li>
     *   <li>{@code uneffect <potionType>} —— 写：调既有的 {@link #removePotionEffect(PotionEffectType)}，
     *       回该类型此前是否在账本里（{@code true}/{@code false}）—— 效果与账本项一起摘掉。</li>
     * </ul>
     * <h3>作用于"别人"（跨玩家；与上面的动词成对，后缀 {@code _to} / {@code _on} / {@code _from}）</h3>
     * <ul>
     *   <li>{@code add_to <player> <buffType> <ticks>} —— 写：调 {@link #addTo(Player, BuffType, int)}，
     *       回<b>目标写后</b>剩余刻（目标处于 {@code IMMUNE} / 已有更长同类 buff ⇒ 回 {@code 0} 或旧值）；</li>
     *   <li>{@code remove_from <player> <buffType>} —— 写：调 {@link #removeFrom(Player, BuffType)}，
     *       回目标写后剩余刻（恒 {@code "0"}）；</li>
     *   <li>{@code has_on <player> <buffType>} —— 读：调 {@link #hasOn(Player, BuffType)}；</li>
     *   <li>{@code remaining_on <player> <buffType>} —— 读：调 {@link #remainingTicksOn(Player, BuffType)}；</li>
     *   <li>{@code clear_debuff_on <player>} —— 写：调 {@link #clearDebuffOn(Player)}，回清掉的条数；</li>
     *   <li>{@code effect_to <player> <potionType> <ticks> <amplifier>} —— 写：调
     *       {@link #applyPotionEffectTo(Player, PotionEffect)}，回平台是否真的把效果装上
     *       （与 {@code effect} 同口径；目标处于 {@code IMMUNE} 时负面的原版药水会被取消 ⇒ {@code false}）。</li>
     * </ul>
     * <p>{@code <buffType>} 取严格 {@code valueOf}：必须与 {@link BuffType} 的常量名逐字相同（全大写），
     * 未知 id 即未识别（回 {@code null}）。
     * <p>{@code <potionType>} 取<b>注册表键名</b>（{@code blindness} / {@code minecraft:jump_boost} 两种写法都收；
     * 大小写不敏感，本方法先转小写再解析），未知名字即未识别（回 {@code null}）。
     * <p>{@code <player>} 取<b>在线玩家的精确名字</b>（{@code Bukkit.getPlayerExact}：不模糊匹配、不含离线玩家）。
     *
     * <h2>跨玩家动词的"读 / 写"两种拒绝口径（有意不同，如实申报）</h2>
     * <ul>
     *   <li><b>写类</b>（{@code add_to} / {@code remove_from} / {@code clear_debuff_on} /
     *       {@code effect_to}）：目标无角色实例（没有可挂的账本）⇒ 回 {@code null}（拒绝执行）——
     *       与"目标无角色一律不施加"这条裁定一致；</li>
     *   <li><b>读类</b>（{@code has_on} / {@code remaining_on}）：目标无角色照常回 {@code false} / {@code 0}
     *       （与"目标没有该 buff"同值）—— 读操作没有"拒绝"这回事，问了就得给个数。</li>
     * </ul>
     * 两种情况的共同点：<b>玩家名字解析不到（不在线）一律回 {@code null}</b>（参数不合法）。
     *
     * <p>薄适配纪律：本方法只调上述既有强类型方法，因此不新增平行的状态改动路径，
     * 也不绕过既有的 buff 语义（闸门 / 取最大时长 / 药水记账 / 免疫）。
     *
     * <h2>payload 口径</h2>
     * 组件收到的是「含动词的整段 payload」：外部指令面把首 token 起、直到行尾的整段原样交给本方法
     * （{@code OperationProvider} 明写「op 与 args 合并后交给组件自解析」，派发器不解析它），
     * 因此本方法自行切分 token。
     * <p>指令里的 {@code #index} 不是 op/args 分隔符：{@code componentId[#index]} 的 {@code #}
     * 是同 id 多份实例的下标（在派发层就已被切掉，用于选中第几份实例；多份且未给下标即直接
     * 拒绝，本方法根本收不到），它与 payload 无关 —— 不存在 {@code op#args} 这种形态。
     *
     * <h2>返回值三态（与 {@link OperationProvider} 契约逐字一致）</h2>
     * <ul>
     *   <li>{@code null} = 未识别 / 拒绝执行（未知动词、语法错、参数不合法、空或空白 payload；
     *       其中参数不合法 = 参数个数不符 / 未知 buff id / 非数字 / 负数 / 溢出）；</li>
     *   <li>{@code ""}（空串）= 已识别但没有回值（纯写操作）—— 本组件从不回空串
     *       （它总有一个可回的值：读类回当前值、写类回写后状态）；</li>
     *   <li>非空串 = 规范化值（读类回当前值、写类回写后状态）。</li>
     * </ul>
     * <p>组件内部抛出的 {@code RuntimeException} 由派发层吞掉并回 {@code null}
     * （派发层在调用本方法处 {@code try}/{@code catch}，异常不得逃到调用方），
     * 因此调用方无法从 {@code null} 区分「语法错」与「组件崩了」（两者在外部看起来一样）。
     *
     * <h2>可直接照抄的指令</h2>
     * <pre>
     * /role operation @s @s buffs count
     * /role operation @s @s buffs add STUN 100  ⇒ 施加后剩余刻数
     * /role operation @s @s buffs add stun 100  ⇒ null（大小写不符 ⇒ 严格 valueOf 拒绝）
     * /role operation @s @s buffs remaining NOPE   ⇒ null（未知 buff id ⇒ 严格 valueOf 拒绝）
     * /role operation @s @s buffs clear_debuff     ⇒ 清掉的条数（插件侧负面 buff + 原版负面药水）
     * /role operation @s @s buffs effect blindness 100 1        ⇒ true（给自己上原版失明）
     * /role operation @s @s buffs uneffect blindness            ⇒ true / false（该类型此前是否在账本里）
     * /role operation @s Steve buffs add_to Steve STUN 100      ⇒ "100"（把眩晕打给 Steve）
     * /role operation @s Steve buffs has_on Steve STUN          ⇒ "true"
     * /role operation @s @s buffs add_to Steve STUN 100         ⇒ "100"（施法者是谁不影响跨玩家效果）
     * /role operation @s @s buffs add_to Nobody STUN 100        ⇒ null（不在线 ⇒ 玩家名字解析不到）
     * /role operation @s @s buffs add_to Alice STUN 100         ⇒ null（Alice 没角色 ⇒ 无账本可挂，拒绝）
     * /role operation @s @s buffs has_on Alice STUN             ⇒ "false"（读类不拒绝，只是"没这回事"）
     * /role operation @s @s buffs effect_to Steve nope 100 1    ⇒ null（未知药水名 ⇒ 未识别）
     * </pre>
     * 反例说明：第 3 条走严格 {@code valueOf} 分支（{@code SILENCE}/{@code STUN}/{@code IMMUNE}
     * 必须逐字相符）；第 4 条走未知 buff id 分支；第 8~10 条分别走"玩家不在线""目标无角色（写类拒绝）"
     * "目标无角色（读类不拒绝）"三条分支；第 11 条走未知药水名分支。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) {
            return null;
        }
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            return null;
        }
        String verb = tokens[0];
        switch (verb) {
            case "can_cast" -> {
                return tokens.length == 1 ? Boolean.toString(canCastSkill()) : null;
            }
            case "can_weapon" -> {
                return tokens.length == 1 ? Boolean.toString(canUseMainWeapon()) : null;
            }
            case "count" -> {
                return tokens.length == 1 ? Integer.toString(appliedPotionTypeCount()) : null;
            }
            case "clear" -> {
                if (tokens.length != 1) {
                    return null;
                }
                clearAppliedPotionEffects();
                return Integer.toString(appliedPotionTypeCount());
            }
            case "clear_debuff" -> {
                if (tokens.length != 1) {
                    return null;
                }
                return Integer.toString(clearDebuff());
            }
            case "has" -> {
                BuffType type = tokens.length == 2 ? buffTypeOf(tokens[1]) : null;
                return type == null ? null : Boolean.toString(has(type));
            }
            case "remaining" -> {
                BuffType type = tokens.length == 2 ? buffTypeOf(tokens[1]) : null;
                return type == null ? null : Long.toString(remainingTicks(type));
            }
            case "add" -> {
                if (tokens.length != 3) {
                    return null;
                }
                BuffType type = buffTypeOf(tokens[1]);
                int ticks = parseNonNegative(tokens[2]);
                if (type == null || ticks < 0) {
                    return null;
                }
                add(type, ticks);
                return Long.toString(remainingTicks(type));
            }
            case "effect" -> {
                if (tokens.length != 4) {
                    return null;
                }
                PotionEffectType potion = potionTypeOf(tokens[1]);
                int duration = parseNonNegative(tokens[2]);
                int amplifier = parseNonNegative(tokens[3]);
                if (potion == null || duration < 0 || amplifier < 0) {
                    return null;
                }
                return Boolean.toString(applyPotionEffect(potion.createEffect(duration, amplifier)));
            }
            case "uneffect" -> {
                if (tokens.length != 2) {
                    return null;
                }
                PotionEffectType potion = potionTypeOf(tokens[1]);
                return potion == null ? null : Boolean.toString(removePotionEffect(potion));
            }

            // ───────── 跨玩家：读类（目标无角色照常回 false / 0，只对"玩家不存在"回 null）─────────

            case "has_on" -> {
                if (tokens.length != 3) {
                    return null;
                }
                Player target = onlinePlayer(tokens[1]);
                BuffType type = buffTypeOf(tokens[2]);
                return target == null || type == null ? null : Boolean.toString(hasOn(target, type));
            }
            case "remaining_on" -> {
                if (tokens.length != 3) {
                    return null;
                }
                Player target = onlinePlayer(tokens[1]);
                BuffType type = buffTypeOf(tokens[2]);
                return target == null || type == null ? null : Long.toString(remainingTicksOn(target, type));
            }

            // ───────── 跨玩家：写类（目标无角色 ⇒ 没有可挂的账本 ⇒ 回 null 拒绝）─────────

            case "add_to" -> {
                if (tokens.length != 4) {
                    return null;
                }
                Player target = onlinePlayer(tokens[1]);
                BuffType type = buffTypeOf(tokens[2]);
                int ticks = parseNonNegative(tokens[3]);
                if (target == null || type == null || ticks < 0) {
                    return null;
                }
                BuffComponent other = targetOf(target);
                if (other == null) {
                    return null;
                }
                other.add(type, ticks);
                return Long.toString(other.remainingTicks(type));
            }
            case "remove_from" -> {
                if (tokens.length != 3) {
                    return null;
                }
                Player target = onlinePlayer(tokens[1]);
                BuffType type = buffTypeOf(tokens[2]);
                if (target == null || type == null) {
                    return null;
                }
                BuffComponent other = targetOf(target);
                if (other == null) {
                    return null;
                }
                other.remove(type);
                return Long.toString(other.remainingTicks(type));
            }
            case "clear_debuff_on" -> {
                if (tokens.length != 2) {
                    return null;
                }
                Player target = onlinePlayer(tokens[1]);
                if (target == null) {
                    return null;
                }
                BuffComponent other = targetOf(target);
                if (other == null) {
                    return null;
                }
                return Integer.toString(other.clearDebuff());
            }
            case "effect_to" -> {
                if (tokens.length != 5) {
                    return null;
                }
                Player target = onlinePlayer(tokens[1]);
                PotionEffectType potion = potionTypeOf(tokens[2]);
                int duration = parseNonNegative(tokens[3]);
                int amplifier = parseNonNegative(tokens[4]);
                if (target == null || potion == null || duration < 0 || amplifier < 0) {
                    return null;
                }
                BuffComponent other = targetOf(target);
                if (other == null) {
                    return null;    //目标无角色 ⇒ 没有账本可挂 ⇒ 拒绝（与"一律不施加"的裁定一致）
                }
                return Boolean.toString(other.applyPotionEffect(potion.createEffect(duration, amplifier)));
            }
            default -> {
                return null;
            }
        }
    }

    /** 严格 {@code valueOf}：未知 / 大小写不符 ⇒ {@code null}（调用方据此回未识别）。 */
    private static BuffType buffTypeOf(String token) {
        try {
            return BuffType.valueOf(token);
        } catch (IllegalArgumentException notABuffType) {
            return null;
        }
    }

    /**
     * 药水类型名 → {@link PotionEffectType}（未知名 ⇒ {@code null}）。
     *
     * <p><b>走服务端注册表</b>：{@code RegistryAccess.registryAccess().getRegistry(RegistryKey.MOB_EFFECT)}
     * → {@code Registry#get(NamespacedKey)}。判据来自服务端自己的注册表，因此本组件不维护任何药水名单，
     * 原版新增 / 改名的效果都不需要改代码。
     *
     * <p><b>为什么不是 {@code PotionEffectType.getByName(...)}</b>：那三个静态查取口
     * （{@code getByName} / {@code getByKey} / {@code values}）连同 {@code getName} / {@code getId}
     * 在本基线（Paper 1.21.11）上<b>全部已弃用</b>（{@code javap -v} 实测 {@code Deprecated: true}，
     * since 1.20.3）⇒ 新代码不碰。
     *
     * <p><b>两种写法都收</b>：{@code blindness} 与 {@code minecraft:blindness} 等价
     * （{@code NamespacedKey.fromString} 的既有语义：缺命名空间即补 {@code minecraft}）。
     * 先转小写 ⇒ 大小写不敏感，未知名字回 {@code null}（调用方据此回未识别）。
     */
    private static PotionEffectType potionTypeOf(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        NamespacedKey key = NamespacedKey.fromString(token.toLowerCase(Locale.ROOT));
        if (key == null) {
            return null;
        }
        return RegistryAccess.registryAccess()
                .getRegistry(RegistryKey.MOB_EFFECT)
                .get(key);
    }

    /** 非负整数解析：非数字 / 负数 / 溢出 ⇒ {@code -1}（调用方据此拒绝）。 */
    private static int parseNonNegative(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
