package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ★ **战斗状态框架**（2026-10-31 需求：修改进入战斗 / 脱离战斗的框架）。
 *
 * <h2>进入战斗（六条规则的落点）</h2>
 * <ol>
 *   <li><b>周围 {@value #ENEMY_RANGE} 格内有敌人 ⇒ 一直处于战斗</b>：每刻用
 *       {@code FactionComponent#hasEnemyInRange} 刷新（敌人 = 敌对阵营玩家）；</li>
 *   <li><b>通过技能给敌人附加状态 ⇒ 进入战斗</b>：{@code BuffComponent} 施加 buff / 原版药水
 *       到敌对目标成功时，刷新施术者（与被施加者）—— 见 {@link #refreshFor(UUID)}；</li>
 *   <li><b>受到敌方攻击 ⇒ 进入战斗</b>：{@code listener/CombatStateListener} 在伤害事件里
 *       解析敌方玩家（直接 / 弹射物 / 插件伤害的 last_damager），刷新双方；</li>
 *   <li><b>被伤害性方块伤害 ⇒ 不进入战斗，改为进入回血间隔期</b>：
 *       监听器对 {@code EntityDamageByBlockEvent} 与方块类伤害原因走
 *       {@link #beginHealingIntervalFor(UUID)}（{@value #HEALING_INTERVAL_TICKS} 刻 = 3 秒）；</li>
 *   <li><b>长时间伤害性药水 / 被动印记</b>：只算被施加那一次（规则 2/3 的即时刷新）；
 *       后续持续伤害（中毒 / 凋零 / 龙息等）在监听器里被排除，<b>不再刷新</b>；</li>
 *   <li><b>脱战</b>：最后一次刷新后 {@value #COMBAT_MEMORY_TICKS} 刻（6 秒）无任何刷新 ⇒ 脱战。</li>
 * </ol>
 *
 * <h2>脱战恢复（每秒一跳）</h2>
 * <ul>
 *   <li><b>猎影人（HUNTER）</b>：+4 SanTE / +4 生命 / +5 能量；</li>
 *   <li><b>影都（SHADOW）</b>：+5 SanTE / +4 生命（无能量）；</li>
 *   <li><b>回血间隔期</b>（被方块伤害后 {@value #HEALING_INTERVAL_TICKS} 刻内）暂停恢复。</li>
 * </ul>
 *
 * <h2>跨实例读口</h2>
 * 伤害事件与 buff 施加点拿不到对方的角色实例 ⇒ 用本类的静态注册表
 * （{@code start()} 登记 / {@code stop()} 撤销，与 {@code FactionManager} 的"平台侧唯一权威"同姿态）：
 * {@link #refreshFor(UUID)} / {@link #beginHealingIntervalFor(UUID)} / {@link #inCombatFor(UUID)}。
 *
 * <h2>与旧内建的关系</h2>
 * 旧的 {@code AutoRecoverSanTEHealthPassive}（无敌人 10 格满 200 刻才回 3 SanTE + 1 生命）
 * 自本批起<b>不再注册</b>：本组件的规则 1/6 与脱战恢复完整取代它（类本身保留，供既有单测/引用）。
 */
public class CombatStateComponent extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "combatState";

    /** 规则 1：敌人判定半径（格）。 */
    public static final double ENEMY_RANGE = 4.0d;

    /** 规则 6：战斗记忆 —— 最后一次刷新后多少刻算脱战（6 秒 = 120 刻）。 */
    public static final int COMBAT_MEMORY_TICKS = 120;

    /** 规则 4：回血间隔期（3 秒 = 60 刻）。 */
    public static final int HEALING_INTERVAL_TICKS = 60;

    /** 脱战恢复的一跳周期（1 秒 = 20 刻）。 */
    public static final int REGEN_PERIOD_TICKS = 20;

    /** 本角色实例的跨实例注册表（`start()` 登记 / `stop()` 撤销）。 */
    private static final Map<UUID, CombatStateComponent> ACTIVE = new ConcurrentHashMap<>();

    private EnergyComponent energy;
    private SanTEComponent sante;
    private VitalsComponent vitals;
    private FactionComponent faction;

    /** 组件自己的刻表（每刻 `update()` 自增；所有时刻判定都用它 ⇒ 离线可测、不依赖 Bukkit tick）。 */
    private int tickCounter;

    /** 最后一次"刷新战斗"的组件刻（初值 = 视作 6 秒前 ⇒ 新建实例默认脱战）。 */
    private int lastCombatRefreshTick = -COMBAT_MEMORY_TICKS;

    /** 回血间隔期的结束刻（{@code > tickCounter} ⇒ 仍处于间隔期）。 */
    private int healingIntervalUntilTick;

    /** 脱战恢复的跳计时。 */
    private int regenCounter;

    public CombatStateComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 被动描述符（不占热键栏）。 */
    public static final class Specification extends PassiveSkill.Specification<CombatStateComponent> {

        public Specification() {
            super(Component.text("战斗状态"),
                    List.of(
                            Component.text("周围 4 格有敌人 / 给敌人上状态 / 受敌方攻击 ⇒ 进入战斗"),
                            Component.text("6 秒无以上刷新 ⇒ 脱战；被伤害性方块伤害不进入战斗"),
                            Component.text("脱战恢复/秒：猎影人 4 SanTE + 4 生命 + 5 能量；影都 5 SanTE + 4 生命"),
                            Component.text("回血间隔期 3 秒：期间不触发脱战恢复")
                    ));
            requires(EnergyComponent.class).requires(SanTEComponent.class)
                    .requires(VitalsComponent.class).requires(FactionComponent.class);
        }

        @Override
        public CombatStateComponent create(String id, ComponentServicesPort services) {
            return new CombatStateComponent(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        energy = getComponent(EnergyComponent.class);
        sante = getComponent(SanTEComponent.class);
        vitals = getComponent(VitalsComponent.class);
        faction = getComponent(FactionComponent.class);

        tickCounter = 0;
        lastCombatRefreshTick = -COMBAT_MEMORY_TICKS;
        healingIntervalUntilTick = 0;
        regenCounter = 0;

        Player self = selfPlayer();
        if (self != null) {
            ACTIVE.put(self.getUniqueId(), this);
        }
    }

    @Override
    public void stop() {
        Player self = selfPlayer();
        if (self != null) {
            ACTIVE.remove(self.getUniqueId(), this);
        }
        energy = null;
        sante = null;
        vitals = null;
        faction = null;
        tickCounter = 0;
        lastCombatRefreshTick = -COMBAT_MEMORY_TICKS;
        healingIntervalUntilTick = 0;
        regenCounter = 0;
    }

    // ───────── 每刻：规则 1 / 6 / 脱战恢复 ─────────

    @Override
    public void update() {
        Player self = selfPlayer();
        if (self == null || !self.isOnline() || self.isDead()) {
            return;
        }
        tickCounter++;

        //规则 1：周围 4 格有敌人 ⇒ 每刻刷新（"一直处于战斗中"）
        if (faction != null && faction.hasEnemyInRange(ENEMY_RANGE)) {
            refreshCombat();
        }

        //规则 4/6：战斗中 或 回血间隔期内 ⇒ 不恢复；两者都结束才走脱战恢复
        if (inCombat() || healingIntervalActive()) {
            regenCounter = 0;
            return;
        }
        if (++regenCounter < REGEN_PERIOD_TICKS) {
            return;
        }
        regenCounter = 0;
        applyOutOfCombatRegen(self);
    }

    // ───────── 状态读口 / 刷新口 ─────────

    /** 当前是否处于战斗中（规则 6：最后一次刷新距今 < 6 秒）。 */
    public boolean inCombat() {
        return combatFromIdleTicks(idleTicks());
    }

    /** 最后一次刷新到现在的刻数。 */
    public int idleTicks() {
        return tickCounter - lastCombatRefreshTick;
    }

    /** 是否处于回血间隔期（规则 4：被方块伤害后的 3 秒）。 */
    public boolean healingIntervalActive() {
        return tickCounter < healingIntervalUntilTick;
    }

    /** 刷新战斗状态（任何一条"进入/延续战斗"的规则都落到这里）。 */
    public void refreshCombat() {
        lastCombatRefreshTick = tickCounter;
    }

    /** 进入回血间隔期（规则 4：被伤害性方块伤害时调用；重复触发则重计时）。 */
    public void beginHealingInterval() {
        healingIntervalUntilTick = tickCounter + HEALING_INTERVAL_TICKS;
    }

    /** 本角色当前阵营（无阵营组件 ⇒ {@link Faction#UNKNOWN}）。 */
    public Faction factionValue() {
        return faction == null ? Faction.UNKNOWN : faction.faction();
    }

    // ───────── 纯函数（离线可穷举单测）─────────

    /** 规则 6：给定"离上次刷新多少刻"⇒ 是否仍在战斗中。 */
    public static boolean combatFromIdleTicks(int idleTicks) {
        return idleTicks < COMBAT_MEMORY_TICKS;
    }

    /** 脱战恢复量（每秒）：猎影人 4/4/5，影都 5/4/0（未知阵营 ⇒ 全 0）。 */
    public static Regen regenFor(Faction faction) {
        return switch (faction == null ? Faction.UNKNOWN : faction) {
            case HUNTER -> new Regen(4, 4, 5);
            case SHADOW -> new Regen(5, 4, 0);
            default -> new Regen(0, 0, 0);
        };
    }

    /** 每秒脱战恢复量（{@code sante / health / energy}）。 */
    public record Regen(int sante, int health, int energy) {
        public boolean any() {
            return sante > 0 || health > 0 || energy > 0;
        }
    }

    // ───────── 跨实例静态口（伤害监听器 / BuffComponent 用）─────────

    /** 刷新某玩家的战斗状态（无该玩家实例 ⇒ 静默 no-op）。 */
    public static void refreshFor(UUID uuid) {
        CombatStateComponent component = uuid == null ? null : ACTIVE.get(uuid);
        if (component != null) {
            component.refreshCombat();
        }
    }

    /** 让某玩家进入回血间隔期（无该玩家实例 ⇒ 静默 no-op）。 */
    public static void beginHealingIntervalFor(UUID uuid) {
        CombatStateComponent component = uuid == null ? null : ACTIVE.get(uuid);
        if (component != null) {
            component.beginHealingInterval();
        }
    }

    /** 某玩家是否在战斗中（无实例 ⇒ {@code false}，即"没有战斗状态可谈"）。 */
    public static boolean inCombatFor(UUID uuid) {
        CombatStateComponent component = uuid == null ? null : ACTIVE.get(uuid);
        return component != null && component.inCombat();
    }

    // ───────── 内部 ─────────

    private void applyOutOfCombatRegen(Player self) {
        Regen regen = regenFor(factionValue());
        if (!regen.any()) {
            return;
        }
        if (sante != null && regen.sante() > 0) {
            sante.increase(regen.sante());
        }
        if (vitals != null && regen.health() > 0) {
            vitals.heal(self, regen.health());
        }
        if (energy != null && regen.energy() > 0) {
            energy.increase(regen.energy());
        }
    }

    private Player selfPlayer() {
        try {
            return svc().self() == null ? null : svc().self().player();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
