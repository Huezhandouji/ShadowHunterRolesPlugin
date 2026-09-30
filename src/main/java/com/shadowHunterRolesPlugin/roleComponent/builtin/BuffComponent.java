package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.BukkitSchedulerAdapter;
import org.bukkit.Bukkit;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashSet;
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
 *   <li>负面效果清理 = {@link #clearDebuff()}（转发到 {@link BuffManager#clearDebuffs()}）：
 *       清插件侧负面 buff + 玩家身上 {@code HARMFUL} 分类的原版药水效果。</li>
 * </ul>
 * 调用点一律直接用本组件（不经服务集端口转发）。
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

    /** 施加 buff（时长 = 游戏刻）。 */
    public void add(BuffType type, int durationTicks) {
        buffManager.addBuff(type, durationTicks);
    }

    /** 是否处于该 buff 下。 */
    public boolean has(BuffType type) {
        return buffManager.hasBuff(type);
    }

    /** 剩余刻（无该 buff ⇒ 0）。 */
    public long remainingTicks(BuffType type) {
        return buffManager.getRemainingTicks(type);
    }

    /** 施加药水效果（已记账路径；参数顺序 = 时长在前、增幅在后，与既有端口逐字一致）。 */
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier) {
        applyPotionEffect(type.createEffect(durationTicks, amplifier));
    }

    /** 同前，但 {@code ambient}/{@code particles} 标志位逐字进入效果对象（5 参构造的 {@code icon} 默认 true）。 */
    public void applyPotionEffect(PotionEffectType type, int durationTicks, int amplifier,
                                  boolean ambient, boolean particles) {
        applyPotionEffect(new PotionEffect(type, durationTicks, amplifier, ambient, particles));
    }

    /** 账本写入的唯一入口。 */
    public void applyPotionEffect(PotionEffect effect) {
        if (effect == null) {
            return;
        }
        svc().self().player().addPotionEffect(effect);
        appliedPotionTypes.add(effect.getType());
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

    /**
     * 组件操作面：把外部字符串指令薄适配到本组件既有强类型方法（不新增状态通道）。
     *
     * <h2>grammar（首 token 必为动词，大小写敏感；参数以空白分隔）</h2>
     * <ul>
     *   <li>{@code can_cast} —— 读（无参）：技能闸门，回 {@code true}/{@code false}；</li>
     *   <li>{@code can_weapon} —— 读（无参）：主武器闸门，回 {@code true}/{@code false}；</li>
     *   <li>{@code has <buffType>} —— 读：是否处于该 buff 下，回 {@code true}/{@code false}；</li>
     *   <li>{@code remaining <buffType>} —— 读：剩余刻（无该 buff ⇒ {@code 0}）；</li>
     *   <li>{@code add <buffType> <ticks>} —— 写：调既有的 {@link #add(BuffType, int)}，回写后剩余刻；</li>
     *   <li>{@code count} —— 读（无参）：药水记账账本内的类型数；</li>
     *   <li>{@code clear} —— 写（无参）：调既有的 {@link #clearAppliedPotionEffects()}，回写后账本数。</li>
     *   <li>{@code clear_debuff} —— 写（无参）：调既有的 {@link #clearDebuff()}，回清掉的条数
     *       （插件侧负面 buff + 原版负面药水）。</li>
     * </ul>
     * <p>{@code <buffType>} 取严格 {@code valueOf}：必须与 {@link BuffType} 的常量名逐字相同（全大写），
     * 未知 id 即未识别（回 {@code null}）。
     *
     * <p>薄适配纪律：本方法只调上述既有强类型方法，因此不新增平行的状态改动路径，
     * 也不绕过既有的 buff 语义（闸门 / 取最大时长 / 药水记账）。
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
     * </pre>
     * 反例说明：第 3 条走严格 {@code valueOf} 分支（{@code SILENCE}/{@code STUN}/{@code IMMUNE}
     * 必须逐字相符）；第 4 条走未知 buff id 分支。
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
