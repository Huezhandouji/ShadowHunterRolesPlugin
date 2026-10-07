package com.shadowHunterRolesPlugin.platform;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 阵营的<b>唯一权威 + 注册表</b>（{@code UUID → FactionComponent}）；纯静态。
 *
 * <p>它是"平台层"的最后一块：领域层（{@link Hostility} / {@link FactionRelation} / {@link CombatPresence}）
 * 只吃纯数据，本类负责取数据（查注册表、读游戏模式）并转发判定。
 *
 * <p><b>注册表语义</b>：谁注册进它，谁就"有阵营"；没注册（未选角色 / 掉线 / 组件已被移除）⇒
 * {@link Faction#UNKNOWN} ⇒ 恒为敌人（既有口径：没有阵营就是敌人）。注册与注销都由
 * {@code FactionComponent} 自己驱动（构造期 {@code register}、{@code stop()} 里 {@code unregister}）
 * ⇒ 本类不需要监听器、不需要注入、不需要单例。
 *
 * <p><b>为什么不是接口</b>：阵营读取整体迁进组件之后，剩下的消费者只有"读别人的阵营 / 判敌"这一件事
 * ⇒ 一个纯静态管理器足够，原先那个平台口（含它的注入与匿名实现）一概删除。
 *
 * <p><b>判敌法则不在本类</b>：本类只做"查表 + 取在场 + 转发"，三条规则与求值顺序在
 * {@link Hostility} + {@link FactionRelation}，本类一个字都不抄。
 */
public final class FactionManager {

    /** 注册表：唯一权威。键 = 玩家 UUID，值 = 该玩家当前的阵营组件。 */
    private static final Map<UUID, FactionComponent> COMPONENTS = new ConcurrentHashMap<>();

    private FactionManager() {
    }

    // ───────── 生命周期：由 FactionComponent 自己调用（构造期注册、stop() 注销）─────────

    /** 注册（构造期调用）；同 UUID 覆盖 —— 换角色时"新实例构造"发生在"旧实例清角色"之前。 */
    public static void register(FactionComponent component) {
        if (component != null && component.ownerId() != null) {
            COMPONENTS.put(component.ownerId(), component);
        }
    }

    /**
     * 注销（{@code stop()} 调用）；幂等。
     *
     * <p>★ <b>两参 {@code remove(key, value)}</b>：只有"当前登记的那一个"才被移除 ——
     * 换角色时的顺序是"新实例先构造（注册）→ 旧实例后 {@code clear()}（注销）"，
     * 用单参 {@code remove(key)} 会把新登记误删（该玩家瞬间变 UNKNOWN = 对所有人敌对）。
     */
    public static void unregister(FactionComponent component) {
        if (component != null && component.ownerId() != null) {
            COMPONENTS.remove(component.ownerId(), component);
        }
    }

    // ───────── 查询面（跨实例；唯一权威 = 注册表里的那个组件）─────────

    /** 某玩家当前的阵营；未注册（未选角色 / 掉线 / 组件已被移除）⇒ {@link Faction#UNKNOWN}。 */
    public static Faction factionOf(UUID uuid) {
        FactionComponent component = uuid != null ? COMPONENTS.get(uuid) : null;
        return component != null ? component.faction() : Faction.UNKNOWN;
    }

    /** {@code self} 是否视 {@code other} 为敌人（非对称；法则与求值顺序见 {@link Hostility}）。 */
    public static boolean isHostile(Faction self, UUID other) {
        return other != null
                && Hostility.isHostileTo(self, factionOf(other),
                        CombatPresence.participates(onlinePlayer(other)));
    }

    /** {@code first} 是否视 {@code second} 为敌人（非对称）。 */
    public static boolean isHostile(UUID first, UUID second) {
        return first != null && second != null && isHostile(factionOf(first), second);
    }

    /** 当前登记数（诊断 / 测试读口：用来断言"注销后不残留"）。 */
    public static int registeredCount() {
        return COMPONENTS.size();
    }

    /**
     * 取在线玩家对象（"在场"那一维的唯一输入）。
     *
     * <p>★ <b>为什么先看 {@code getServer()}</b>：{@code Bukkit.getPlayer(uuid)} 直接解引用
     * {@code Bukkit} 的服务端单例（未装载服务端时抛 {@code NullPointerException}）。
     * 没有服务端 ⇒ 本方法回 {@code null}，判定交给 {@link CombatPresence}
     * 的既有口径（{@code null} = 读不到游戏模式 ⇒ 按"在场"处理）。
     * 生产路径不变：有服务端时本方法就是 {@code Bukkit.getPlayer(uuid)}。
     */
    private static Player onlinePlayer(UUID uuid) {
        return Bukkit.getServer() == null ? null : Bukkit.getPlayer(uuid);
    }
}
