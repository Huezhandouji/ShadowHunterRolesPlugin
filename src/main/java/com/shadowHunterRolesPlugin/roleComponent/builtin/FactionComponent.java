package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import com.shadowHunterRolesPlugin.platform.FactionManager;
import com.shadowHunterRolesPlugin.roleComponent.OperationProvider;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阵营组件：系统级能力「阵营」的组件形态（每角色实例一个）。
 *
 * <h2>状态唯一（两个值，各有归属）</h2>
 * <ul>
 *   <li>{@code declared} = 本角色<b>声明</b>的默认阵营：<b>装配期由描述符携带</b>
 *       （{@link Specification}），构造期写入一次、此后只读 ⇒ 它没有写入口；</li>
 *   <li>{@code current} = 本实例<b>当前</b>阵营：唯一真值，初值 = {@code declared}，
 *       此后只被 {@link #set(Faction)} / {@link #reset()} 改写。</li>
 * </ul>
 * 容器侧（{@code core/RoleInstance}）不再持有任何阵营字段 ⇒ 判敌读数只有这一条来源。
 *
 * <h2>权威与注册</h2>
 * 本组件是"每实例一份的状态持有者"，<b>权威与注册表在 {@link FactionManager}</b>：
 * 构造期 {@code register(this)}、{@link #stop()} 里 {@code unregister(this)}。
 * 因此本组件<b>不需要</b>任何"不可移除"保护：组件被移除 ⇒ 注册表少一条 ⇒
 * 该玩家按"没有阵营 ⇒ 敌人"的既有口径处理。
 *
 * <h2>缺失 = 无阵营 = 敌人</h2>
 * 注册表里查不到（未选角色 / 掉线 / 组件已被移除）与 {@link Faction#UNKNOWN} 同档 ⇒ 恒为敌对。
 * 判敌法则<b>不在本组件</b>（在 {@code platform/Hostility} + {@code platform/FactionRelation}），
 * 本组件只持有真值并转发。
 *
 * <h2>★ 读与写都走组件操作面（本组件实现 {@link OperationProvider}）</h2>
 * 阵营<b>没有专用 API 成员</b>：查询与改写一律经
 * {@code RoleAPI#executeComponentOperation(uuid, "faction", payload)}（等价于 {@code /role operation}），
 * 动词表见 {@link #onOperationCommand(String)} 的 javadoc。方向由"操作落在谁的实例上"表达：
 * "A 是否视 B 为敌" = 在 <b>A 的实例</b>上执行 {@code hostile <B 的 uuid>}。
 */
public class FactionComponent extends RoleComponent implements OperationProvider {

    /** 组件 id（谁是什么 id 由谁说了算）。 */
    public static final String ID = "faction";

    /** 本角色声明的默认阵营（装配期由描述符给出，此后只读；{@link #reset()} 的回落目标）。 */
    private final Faction declared;

    /** 真值：本实例当前阵营（唯一持有处）。 */
    private Faction current;

    public FactionComponent(String id, ComponentServicesPort services, Faction declared) {
        super(id, services);
        if (declared == null) {
            throw new NullPointerException("declared faction");
        }
        this.declared = declared;
        //选角色即取声明值（与既有容器构造期那句"实例阵营 = 角色模板声明的默认阵营"逐字等价）
        this.current = declared;
        //★ 注册进唯一权威：此后 FactionManager#factionOf(ownerId()) 就读到这里
        FactionManager.register(this);
    }

    /**
     * 与 {@code start()} 严格对称的回收钩子：<b>注销自己</b>（幂等）。
     * <p>所有实例销毁路径（换角色 / 死亡 / 掉线 / 清角色 / 故障隔离四步的第①步）都已收敛到
     * 容器的 {@code clear()} → 生命周期 {@code stop()} ⇒ 本方法不需要新增任何监听器。
     * <p>{@link FactionManager#unregister} 用两参 {@code remove(key, value)}：只有"当前登记的那一个"
     * 才被移除，因此换角色时"新实例先注册、旧实例后注销"的顺序不会误删新登记。
     */
    @Override
    public void stop() {
        FactionManager.unregister(this);
    }

    /**
     * 注册表键 = 本实例玩家的 UUID（唯一身份，取自 {@link SelfPort#id()}）。
     * <p>身份由 {@link SelfPort} 给出（既有契约：实例存活期内 {@code player()} 恒非空）；
     * 拿不到身份（{@code null}）⇒ 本组件不登记 —— 没有键可登记，读取侧按"没有阵营"处理。
     */
    public UUID ownerId() {
        return svc().self().id();
    }

    /** 本实例当前阵营（每实例一份；含 {@link Faction#UNKNOWN}）。 */
    public Faction faction() {
        return current;
    }

    /** 本角色声明的默认阵营（只读；{@link #reset()} 的回落目标）。 */
    public Faction declared() {
        return declared;
    }

    /** 写入本实例当前阵营；{@code null} 非法。 */
    public void set(Faction faction) {
        if (faction == null) {
            throw new NullPointerException("faction cannot be null");
        }
        this.current = faction;
    }

    /** 复位为本角色声明的默认阵营（幂等：连做两次结果相同）。 */
    public void reset() {
        this.current = declared;
    }

    // ───────── 判定面：本组件只做"取自己的阵营 → 转发 FactionManager" ─────────
    // 三条规则与求值顺序仍在 platform/Hostility + platform/FactionRelation，本组件一个字都不抄。

    /**
     * <b>我</b>是否视 {@code target} 为敌人（target = 目标方）。
     *
     * <p>对方没有阵营组件（未选角色 / 掉线 / 组件已被移除）与对方阵营为 {@link Faction#UNKNOWN}
     * 同档：{@link FactionManager#factionOf} 把两者都读成 {@code UNKNOWN} ⇒ 对任一有阵营的一方
     * <b>恒为敌对</b>。这不是一条新分支 —— 它就是"双方都有阵营且阵营相同才不敌对"这一行的补集。
     *
     * @param target 对方玩家 UUID（{@code null} → {@code false}）
     */
    public boolean isHostileTo(UUID target) {
        return target != null && FactionManager.isHostile(faction(), target);
    }

    /**
     * <b>{@code first} 是否视 {@code second} 为敌人</b>（<b>非对称</b>：first = 发起方，second = 目标方）。
     * <p>"在场"那一维只读目标方 ⇒ {@code isHostile(a,b)} 与 {@code isHostile(b,a)} 一般不同值。
     */
    public boolean isHostile(UUID first, UUID second) {
        return first != null && second != null && FactionManager.isHostile(first, second);
    }

    /**
     * 与 {@link #isHostileTo(UUID)} 等价的 {@code Player} 形态（纯委托；{@code null} → {@code false}）。
     */
    public boolean isHostile(Player victim) {
        return victim != null && isHostileTo(victim.getUniqueId());
    }

    /**
     * 半径内是否有敌人（几何与过滤逐字沿用既有实现）：<b>没有阵营的玩家算敌人</b>，
     * <b>创造 / 旁观者不算</b>（那道判定在关系表的"对方不在场 ⇒ 不敌对"里，本方法不另行过滤）。
     * <p>己方是否在场<b>不影响</b>本方法；{@code getNearbyPlayers} 只看得到在线玩家
     * ⇒ 离线者永不构成本方法的"敌人"。
     */
    public boolean hasEnemyInRange(double radius) {
        Location loc = svc().self() == null || svc().self().player() == null
                ? null : svc().self().player().getLocation();
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        for (Player p : loc.getNearbyPlayers(radius)) {
            if (p == null) {
                continue;
            }
            if (isHostileTo(p.getUniqueId())) {
                return true;
            }
        }
        return false;
    }

    // ───────── 组件操作面（阵营唯一的查询 / 改写入口）─────────

    /**
     * 操作面 grammar（首 token 必为操作动词，只接这六个）：
     * <pre>
     * faction                      只读：本实例当前阵营名（{@code SHADOW} / {@code HUNTER} / {@code UNKNOWN}）
     * declared                     只读：本角色声明的默认阵营名
     * hostile &lt;uuid&gt;            只读：我是否视该玩家为敌人（{@code true} / {@code false}）
     * enemy_in_range &lt;半径&gt;       只读：半径内是否有敌人（{@code true} / {@code false}；半径 = 非负十进制数）
     * set &lt;阵营名&gt;               写：改本实例当前阵营，回写后阵营名
     * reset                        写：复位为本角色声明的默认阵营，回复位后阵营名
     * </pre>
     * <b>严格规则（逐条可测）</b>：动词小写、大小写敏感；只读动词不得带参数，
     * {@code hostile} / {@code enemy_in_range} / {@code set} 必须且只带一个参数；
     * {@code hostile} 的参数必须是合法 UUID（否则拒绝）；{@code enemy_in_range} 的参数必须是
     * 非负有限十进制数（负数 / {@code NaN} / {@code Infinity} / 非数字一律拒绝）；
     * {@code set} 只接 {@code SHADOW} / {@code HUNTER} / {@code UNKNOWN} 三个名字（大小写敏感）；
     * payload 为 {@code null} / 空串 / 纯空白 ⇒ 未识别。
     *
     * <p><b>返回值三态</b>（与 {@link OperationProvider} 契约逐字一致）：{@code null} = 未识别或拒绝；
     * 本组件从不回 {@code ""}（六个动词总有一个可回的值）；非空串 = 规范化值 ——
     * 读动词回读数、写动词回"写后阵营名"。
     *
     * <p><b>方向语义</b>：{@code hostile <uuid>} 是<b>本实例（= 操作目标玩家自己）</b>是否视对方为敌人，
     * 因此<b>非对称</b> —— "A 是否视 B 为敌"要在 A 的实例上执行，"B 是否视 A 为敌"要在 B 的实例上执行。
     *
     * <p><b>写动词与判敌的关系</b>：{@code set UNKNOWN} 会让该玩家按"没有阵营"参与判敌
     * （对所有人敌对，含同角色的其他人）；要回到角色声明值用 {@code reset}。
     *
     * <h2>可直接照抄的指令</h2>
     * <pre>
     * /role operation @s @s faction faction
     * /role operation @s @s faction declared
     * /role operation @s @s faction hostile 00000000-0000-0000-0000-000000000001
     * /role operation @s @s faction enemy_in_range 10
     * /role operation @s @s faction set HUNTER
     * /role operation @s @s faction reset
     * </pre>
     * 反例：{@code set hunter}（小写 ⇒ 未识别）、{@code faction 1}（只读动词带参 ⇒ 未识别）、
     * {@code enemy_in_range -1}（负数 ⇒ 拒绝）。
     */
    @Override
    public String onOperationCommand(String payload) {
        if (payload == null) return null;
        String[] tokens = payload.trim().split("\\s+");
        if (tokens.length == 0 || tokens[0].isEmpty()) return null;   // 空 / 纯空白 payload 则未识别
        switch (tokens[0]) {
            case "faction" -> {
                if (tokens.length != 1) return null;                  // 只读动词不得带参数
                return current.name();
            }
            case "declared" -> {
                if (tokens.length != 1) return null;
                return declared.name();
            }
            case "hostile" -> {
                if (tokens.length != 2) return null;                  // 必须且只带一个参数
                UUID target = parseUuid(tokens[1]);
                return target == null ? null : Boolean.toString(isHostileTo(target));
            }
            case "enemy_in_range" -> {
                if (tokens.length != 2) return null;
                double radius = parseNonNegativeRadius(tokens[1]);
                return radius < 0 ? null : Boolean.toString(hasEnemyInRange(radius));
            }
            case "set" -> {
                if (tokens.length != 2) return null;
                Faction target = parseFaction(tokens[1]);
                if (target == null) return null;                      // 认不出的阵营名 ⇒ 拒绝
                set(target);
                return current.name();                                // 一律回"写后值"
            }
            case "reset" -> {
                if (tokens.length != 1) return null;
                reset();
                return current.name();
            }
            default -> {
                return null;                                          // 未知动词则未识别
            }
        }
    }

    /** 非负有限半径解析：非数字 / 负数 / {@code NaN} / {@code Infinity} 一律回 {@code -1}（调用方据此拒绝）。 */
    private static double parseNonNegativeRadius(String token) {
        try {
            double value = Double.parseDouble(token);
            return value >= 0 && !Double.isInfinite(value) ? value : -1;
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    /** 阵营名解析（大小写敏感）：认不出则回 {@code null}。 */
    private static Faction parseFaction(String token) {
        for (Faction candidate : Faction.values()) {
            if (candidate.name().equals(token)) {
                return candidate;
            }
        }
        return null;
    }

    /** UUID 解析：非法格式则回 {@code null}（调用方据此拒绝）。 */
    private static UUID parseUuid(String token) {
        try {
            return UUID.fromString(token);
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }

    /**
     * 装配描述符：<b>默认阵营在这里指定</b>（装配期口径）。
     *
     * <h2>★ 一个描述符实例只能服务一个角色</h2>
     * {@code Specification#freeze()} 把工厂记为 {@code this::create}
     * ⇒ {@code create} 经方法引用捕获<b>本描述符实例</b>，声明值随它走。
     * 因此把同一份 {@code Specification} 交给两个角色，两侧都会拿到同一个声明阵营 ——
     * 装配点必须<b>每次 {@code new}</b>（{@code registry/RoleLoader#withBuiltIns} 的调用点即如此）。
     *
     * <p>另一个既有约束同向生效：快照一经 {@code freeze()} 即只读，{@code bindId} 也不允许重绑成
     * 另一个 id ⇒ 共享同一实例本身就会被拒绝。
     */
    public static final class Specification extends RoleComponent.Specification<FactionComponent> {

        /**
         * 装配期自记账：组件 id → 该描述符携带的声明阵营（只为"两处声明值同值"的对账提供读面）。
         *
         * <p>★ <b>口径 = "最近一次装配时该 id 携带的声明值"</b>：本组件的注册 id 在所有角色里都是
         * 同一个（{@value FactionComponent#ID}），因此这张表只能回答"最近装配的那一个"。
         * 对账断言必须<b>紧随一次装配</b>读它（{@code RoleAssemblyTest} 的循环正是逐条装配后立刻断言）。
         * 用"先到先得"（{@code putIfAbsent}）会让第二个角色起全部读到第一个角色的声明值，
         * 那正是对账要抓的"两处不一致"，却会变成假红 —— 故此处按覆盖写入。
         */
        private static final Map<String, Faction> DECLARED_BY_ID = new ConcurrentHashMap<>();

        private final Faction declared;

        /**
         * @param declared 本角色声明的默认阵营；{@code null} 归一为 {@link Faction#UNKNOWN}
         *                 （与 {@code Role.Builder#faction(null)} 同口径 —— 装配点传什么这里就存什么）
         */
        public Specification(Faction declared) {
            super("Faction");
            this.declared = declared != null ? declared : Faction.UNKNOWN;
        }

        /**
         * 装配期记账（在装配器 {@code bindId(id)} 之后、{@code freeze()} 之内执行一次）：
         * 此时注册 id 已绑定，声明值随描述符走 ⇒ 对账表的读面不需要构造任何实例。
         */
        @Override
        protected void validateForFreeze() {
            record(boundId(), declared);
        }

        @Override
        public FactionComponent create(String id, ComponentServicesPort services) {
            //兜底再记一次（幂等）：不经装配器而直接造实例的路径也让对账表有值
            record(id, declared);
            return new FactionComponent(id, services, declared);
        }

        /** 记一条"某 id 携带的声明阵营"（{@code id} 为 {@code null} ⇒ 忽略）。 */
        static void record(String id, Faction declared) {
            if (id != null) {
                DECLARED_BY_ID.put(id, declared);
            }
        }

        /** 某个已装配 id 的声明阵营；未见过该 id ⇒ {@code null}。 */
        public static Faction declaredOf(String componentId) {
            return DECLARED_BY_ID.get(componentId);
        }
    }
}
