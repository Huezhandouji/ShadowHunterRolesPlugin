package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffType;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * buff 组件：系统级能力「buff 施加 / 查询 / 闸门 / 药水记账」的
 * **组件形态**（每角色实例一个）。
 * <p><b>★ 本组件持有状态与行为</b>：
 * <ul>
 *   <li><b>buff 记账表</b> = {@link BuffManager} 实例（容器在构造期创建并交给本组件；该类属
 *       {@code manager/} 包、**不是组件** ⇒ 本组件只持有它、不改它）✓；</li>
 *   <li><b>药水记账**账本**</b> = {@link #appliedPotionTypes}（原 {@code RoleInstance} 的字段搬进本组件）
 *       ⇒ {@code clear()} 时只回收账本内的类型（语义逐字保留）✓；</li>
 *   <li><b>施加路径</b> = {@link #applyPotionEffect(PotionEffect)}（{@code player.addPotionEffect} +
 *       记账）：Bukkit API = 允许依赖的"外部东西" ✓。</li>
 * </ul>
 * <b>调用点一律直接用本组件</b> ✓（不经服务集端口转发）。
 */
public class BuffComponent extends RoleComponent implements OperationProvider {

    private final BuffManager buffManager;

    /** ★ 药水记账账本（原 {@code RoleInstance#appliedPotionTypes} 的持有者搬到这里）。 */
    private final Set<PotionEffectType> appliedPotionTypes = new LinkedHashSet<>();

    public BuffComponent(String id, ComponentServices services, BuffManager buffManager) {
        super(id, services);
 //★ 账本与持有者**成对**建立：`BuffManager` 需要"账本的持有者"这个引用才能在不回容器的前提下
 // 请求重绘 / 交药水记账 ⇒ 构造期把它接上（本组件的 `this` 在此已可安全传出：只存引用、不调用）。
        this.buffManager = buffManager;
        if (buffManager != null) {
            buffManager.bindOwner(this);
        }
    }

    /** buff 记账表本体（容器 {@code clear()} 仍需它做 {@code clearAll()} ⇒ 提供读口）。 */
    public BuffManager manager() {
        return buffManager;
    }

    // ───────── 生命周期：本组件自己的两个节拍（容器不再代劳）─────────

    /**
     * **开始生效：启动记账表的每 tick 更新**（两阶段构造的第二相）。
     *
     * <p><b>为什么在 {@code start()}</b>：构造期不得创建任何任务（构造中途抛错会泄漏永久 ticker）
     * ⇒ 记账表的更新只能在可见相启动 ✓（与 `BuffManager` 自己的契约同源）。
     *
     * <p><b>提交顺序与既有实现相同</b>：本 `start()` 由 `triggerLifecycleStart()` 按**注册序**广播，
     * 而记账表更新先于实例 ticker 启动 ⇒ 同一 tick 内先跑记账、再跑组件 `update()` ✓。
     */
    @Override
    public void start() {
        buffManager.startUpdater();
    }

    /**
     * **停止生效：回收本组件持有的两本账**（药水账本 + buff 记账表）。
     *
     * <p><b>顺序逐字沿用既有实现</b>：① 先只移除**本系统记账过**的药水效果
     * （不再无条件清空玩家身上的所有药水）② 再清账本 ✓。
     */
    @Override
    public void stop() {
        clearAppliedPotionEffects();
        clearBuffLedger();
    }

    /**
     * **清空 buff 记账表**（★ 本组件自己的状态自己回收；原由容器经转发入口代劳）。
     * <p>语义与原转发入口**逐字一致**：`manager().clearAll()` ✓。
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

    /** **账本写入的唯一入口**（原 {@code RoleInstance#applyPotionEffect} 的实现搬到这里）。 */
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
     * 回收账本内的全部药水（原 {@code RoleInstance#clear} 的那一段搬到这里）：只移除本系统记账过的类型，
     * **不**无条件清空玩家身上的所有药水效果。返回移除的类型数。
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
     * **组件操作面**：把外部字符串指令**薄适配**到本组件既有强类型方法（零新增状态通道 ✓）。
     *
     * <h2>grammar（首 token 必为动词，**大小写敏感**；参数以空白分隔）</h2>
     * <ul>
     *   <li>{@code can_cast} —— 读（无参）：技能闸门，回 {@code true}/{@code false}；</li>
     *   <li>{@code can_weapon} —— 读（无参）：主武器闸门，回 {@code true}/{@code false}；</li>
     *   <li>{@code has <buffType>} —— 读：是否处于该 buff 下，回 {@code true}/{@code false}；</li>
     *   <li>{@code remaining <buffType>} —— 读：剩余刻（无该 buff ⇒ {@code 0}）；</li>
     *   <li>{@code add <buffType> <ticks>} —— 写：调既有的 {@link #add(BuffType, int)}，回**写后**剩余刻；</li>
     *   <li>{@code count} —— 读（无参）：药水记账账本内的类型数；</li>
     *   <li>{@code clear} —— 写（无参）：调既有的 {@link #clearAppliedPotionEffects()}，回**写后**账本数。</li>
     * </ul>
     * <p><b>{@code <buffType>} 取严格 {@code valueOf}</b>：必须与 {@link BuffType} 的常量名**逐字相同**（全大写 ✓）
     * ⇒ 未知 id ⇒ 未识别（回 {@code null}）✗。
     *
     * <h2>三态返回</h2>
     * {@code null} = **未识别 / 拒绝执行**（未知动词 ✓ · 参数个数不符 ✓ · 未知 buff id ✓ · 非数字 / 负数 / 溢出 ✓ ·
     * 空或空白 payload ✓）；非空串 = **规范化值**（读类回当前值、写类回**写后状态** ✓）。
     *
     * <p><b>薄适配纪律</b>：本方法**只调**上述既有强类型方法 ⇒ 不新增平行的状态改动路径 ✗、
     * 不绕过既有的 buff 语义（闸门 / 取最大时长 / 药水记账）✓。
     *
     * <h2>payload 口径（★ 先读这一段）</h2>
     * <b>组件收到的是「含动词的整段 payload」</b> —— 外部指令面把**首 token 起、直到行尾**的整段
     * **原样**交给本方法（{@code OperationProvider} 明写「op 与 args **合并**后交给组件自解析」，
     * 派发器**不解析**它 ✗）⇒ 本方法自行切分 token ✓。
     * <p>★ <b>指令里的 {@code #index} 不是 op/args 分隔符</b>：{@code componentId[#index]} 的 {@code #}
     * 是**同 id 多份实例的下标**（在**派发层**就已被切掉，用于选中第几份实例；多份且未给下标 ⇒ 直接
     * 拒绝 ⇒ **本方法根本收不到**）⇒ 它**与 payload 无关** ✗ —— **不存在** {@code op#args} 这种形态 ✓。
     *
     * <h2>返回值三态（与 {@link OperationProvider} 契约逐字一致）</h2>
     * <ul>
     *   <li>{@code null} = **未识别或拒绝**（未知动词 / 语法错 / 参数不合法）✗；</li>
     *   <li>{@code ""}（空串）= **已识别但没有回值**（纯写操作）✓ —— ★ 本组件**从不**回空串
     *       （它总有一个可回的值：读类回当前值、写类回写后状态）✓；</li>
     *   <li>**非空串** = **规范化值** ✓。</li>
     * </ul>
     * <p>★ <b>组件内部抛出的 {@code RuntimeException} 由派发层吞掉并回 {@code null}</b>
     * （派发层在调用本方法处 {@code try}/{@code catch} ⇒ 异常不得逃到调用方）⇒ ★ 调用方
     * **无法**从 {@code null} 区分「语法错」与「组件崩了」✗（两者在外部看起来一样）。
     *
     * <h2>可直接照抄的指令</h2>
     * <pre>
     * /role operation query  @s buffs count
     * /role operation modify @s buffs add STUN 100  ⇒ 施加后剩余刻数
     * /role operation modify @s buffs add stun 100  ⇒ null（大小写不符 ⇒ 严格 valueOf 拒绝）
     * /role operation query  @s buffs remaining NOPE   ⇒ null（未知 buff id ⇒ 严格 valueOf 拒绝）
     * </pre>
     * ★ 反例说明：第 3 条走**严格 {@code valueOf}** 分支（{@code SILENCE}/{@code STUN}/{@code IMMUNE} 必须逐字相符）；第 4 条走**未知 buff id** 分支。
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

    /** 严格 {@code valueOf}：未知 / 大小写不符 ⇒ {@code null}（调用方据此回未识别 ✗）。 */
    private static BuffType buffTypeOf(String token) {
        try {
            return BuffType.valueOf(token);
        } catch (IllegalArgumentException notABuffType) {
            return null;
        }
    }

    /** 非负整数解析：非数字 / 负数 / 溢出 ⇒ {@code -1}（调用方据此拒绝 ✗）。 */
    private static int parseNonNegative(String token) {
        try {
            int value = Integer.parseInt(token);
            return value >= 0 ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
