package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;

/**
 * 敌对判定的<b>合成真值</b>：把「在场」与「阵营」两个维度合成一个判定。
 *
 * <p>与两个维度真值类（{@link CombatPresence} = 在场、{@link FactionRelation} = 阵营）同族、同写法：
 * 纯静态、不触服务端 ⇒ 离线可测。唯一的调用方是 {@link FactionManager}
 * （它取数据、本类判真值），因此测试校验的是生产真值本身，而不是复述一份可能漂移的语义。
 *
 * <h2>★ 判敌口径（2026 语义变更，已非对称）</h2>
 *
 * <p>本口径由「同阵营优先」+「只看对方在场」两条构成，三条规则的落点逐条如下：
 * <ol>
 *   <li><b>同阵营 ⇒ 任何情况下非敌对</b>（<b>最高优先级</b>）：双方都有阵营且阵营相同，
 *       无论对方是生存 / 创造 / 旁观、无论自己是什么模式，都不敌对。
 *       落点 = {@link FactionRelation#isHostileTo} 的「有阵营且同阵营 ⇒ 不敌对」。</li>
 *   <li><b>对方是创造 / 旁观 ⇒ 非敌对</b>：对方不在场就不打他 —— 创造 / 旁观者不被索敌选中。
 *       落点 = 本方法第一行的 {@code if (!otherInPlay) return false;}。</li>
 *   <li><b>其余一律敌对</b>：对方在场且不同阵营（含「任一方没有阵营」⇒ {@link Faction#UNKNOWN}，
 *       含「对方没有阵营组件」—— 读取侧把它塌缩成同一个 {@code UNKNOWN}）⇒ 敌对。</li>
 * </ol>
 *
 * <h2>★ 自己的在场状态<b>不参与</b>判定</h2>
 *
 * <p>本方法<b>只读对方</b>的 {@code otherInPlay}，<b>不读自己</b>的在场状态。
 * 因此「我是创造 / 旁观，敌人其他模式 ⇒ <b>敌对</b>」是规则②与③的直接推论，
 * 而非一条单独的规则 —— 它没有专门的一行代码。
 *
 * <p>★ <b>与旧口径的差异（有意变更，不是回归）</b>：旧口径是「创造 / 旁观者<b>双向</b>都不敌对」
 * （己方不在场 ⇒ 一律 return false）。本口径只挡「对方不在场」这一侧，己方不在场不再豁免。
 * 旧口径由旧实现（已删除的实例侧判定）里那道「自己不在场」的前置闸门实现。
 *
 * <h2>★ 非对称性：两个 isHostile 不再同值</h2>
 *
 * <p>阵营关系仍是对称的（{@link FactionRelation#isHostile} 的对称性不受本次变更影响），
 * 但<b>合成判定是非对称的</b> —— 因为「在场」那一维只取对方：
 * <pre>
 *   Hostility.isHostileTo(A, B, bInPlay)   =  bInPlay &amp;&amp; !(同阵营(A,B))
 *   ⇒ isHostileTo(A, B, …) ≠ isHostileTo(B, A, …)   一般成立
 * </pre>
 * 例：{@code A} 是创造模式（{@code aInPlay=false}）、{@code B} 是生存且异阵营 ⇒
 * {@code isHostileTo(A,B) = true}（A 该把 B 当敌人），而 {@code isHostileTo(B,A) = false}（B 不该打创造模式的 A）。
 *
 * <p>因此 <b>两个玩家的判定必须写出方向</b>：{@link FactionManager#isHostile(java.util.UUID, java.util.UUID)}
 * 的语义是「<b>first</b> 是否视 <b>second</b> 为敌人」（first = 发起方，second = 目标），
 * 而不是旧注释里的「两者之间是否敌对（对称）」。旧的逐字对称断言已随口径变更作废。
 *
 * <p><b>边界</b>：{@code null} 阵营一律按「没有阵营」（{@link Faction#UNKNOWN}）处理 ⇒ 敌对，
 * 与 {@link FactionRelation} 同口径。{@code otherInPlay = false} 是唯一的一票否决，
 * 它先于阵营维度求值 —— 这就是「同阵营任何情况下非敌对」在「对方不在场」时同样成立的原因
 * （两条规则此时同向，不产生冲突）。
 */
public final class Hostility {

    private Hostility() {
    }

    /**
     * 「{@code selfFaction} 是否视 otherFaction（对方）为敌人」——合成真值，唯一的实现点。
     *
     * <p>求值顺序不可交换：
     * <ol>
     *   <li>对方不在场（创造 / 旁观）⇒ <b>false</b>（一票否决，先于阵营维度）；</li>
     *   <li>否则交给 {@link FactionRelation#isHostileTo}：同阵营 ⇒ false，其余（含没有阵营）⇒ true。</li>
     * </ol>
     *
     * <p>★ <b>不接收自己</b>的在场状态：己方是创造 / 旁观时本方法照常求值，
     * 语义 = 规则③（对方在场且不同阵营 ⇒ 敌对）。
     *
     * @param selfFaction  己方阵营（{@code null} 视为未知 = 没有阵营 ⇒ 敌对）
     * @param otherFaction 对方阵营（{@code null} 视为未知 = 没有阵营 ⇒ 敌对）
     * @param otherInPlay  对方是否<b>在场</b>（生存 / 冒险 = {@code true}；创造 / 旁观 = {@code false}）；
     *                     离线者读不到游戏模式，按在场处理（真值 = {@link CombatPresence}）
     * @return 是否敌对
     */
    public static boolean isHostileTo(Faction selfFaction, Faction otherFaction, boolean otherInPlay) {
        if (!otherInPlay) {
            return false;
        }
        return FactionRelation.isHostileTo(selfFaction, otherFaction);
    }

    /**
     * 两个玩家的判定（<b>非对称</b>）：语义 = 「{@code first} 是否视 {@code second} 为敌人」。
     *
     * <p>只是把上面的合成真值按「first = 发起方」展开一遍，便于调用点把方向写显 —— 别把两个形参
     * 当成无序的一对。
     *
     * @param first     发起方（其阵营决定「有没有同阵营豁免」；<b>其在场状态不参与</b>）
     * @param second    目标方（其<b>在场状态</b>参与判定）
     * @param secondInPlay 目标方是否在场
     */
    public static boolean isHostile(Faction first, Faction second, boolean secondInPlay) {
        return isHostileTo(first, second, secondInPlay);
    }
}