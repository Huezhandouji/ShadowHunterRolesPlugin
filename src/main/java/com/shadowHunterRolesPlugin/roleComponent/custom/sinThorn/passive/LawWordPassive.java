package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 「罪棘」被动之二：律法之言。
 *
 * <p>行为：每次攻击给命中者挂上 15 秒罪罚，期间每 0.5 秒削减其 1 点 SanTE（特殊值）。
 * 15 秒共结算 30 次，合计 30 点特殊值伤害。再次命中刷新为满 15 秒（不做叠层）。
 *
 * <p>谁触发它（两条路）：
 * <ul>
 *   <li>{@code SinThornFangMainWeapon#onAttack} —— 玩家近战命中；</li>
 *   <li>{@link SinThornPassive} —— 召唤者尖牙每次咬中。</li>
 * </ul>
 * 两者都经本类的 {@link #applyLaw(UUID)} 写同一份私有账本
 * （与 {@code RedBleedPassive} 的账本私有化口径一致）。
 *
 * <p>时序：{@code update()} 每刻广播，本组件自己数 tick（0.5 秒一结算），不使用调度器；
 * {@code stop()} 清空账本（角色清除即全部失效）。
 */
public class LawWordPassive extends PassiveSkill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "sinThorn_passive_lawWord";

    /** 罪罚总时长：15 秒 = 300 刻。 */
    private static final int LAW_DURATION_TICKS = 300;

    /** 结算间隔：0.5 秒 = 10 刻。 */
    private static final int LAW_TICK_INTERVAL_TICKS = 10;

    /** 每次结算削减的 SanTE。 */
    private static final int SANTE_PER_TICK = 1;

    /** 账本：键 = 承受者 UUID，值 = 罪罚剩余刻数。 */
    private final Map<UUID, Integer> lawRemainingTicks = new HashMap<>();

    /**
     * **本实例的 SanTE 组件** —— 用它上面的跨实例入口
     * {@code decreaseSanTE(UUID, int)} 削目标的特殊值。
     * <p>跨实例削 SanTE 必须走这个官方入口，不绕公开 {@code RoleAPI}。
     */
    private SanTEComponent sante;

    private int tickCounter = 0;

    public LawWordPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符（无栏位，天然不占热键栏）。
     */
    public static final class Specification extends PassiveSkill.Specification<LawWordPassive> {

        public Specification() {
            super(Component.text("律法之言"),
                    List.of(Component.text("每次攻击附加15秒罪罚：每0.5秒削减目标1点特殊值")));
            //跨实例削 SanTE 走本组件自己的跨实例入口，因此 SanTE 组件是必需依赖
            requires(SanTEComponent.class);
        }

        @Override
        public LawWordPassive create(String id, ComponentServicesPort services) {
            return new LawWordPassive(id, services, this);
        }
    }

    /**
     * **挂罪罚的唯一公开入口**（近战与尖牙都走这里）。
     * <p>语义：写入的是 {@code update()} 结算用的同一份账本，不做并行存储；
     * 重复命中刷新为满时长（覆盖，不累加、不叠层）。
     */
    public void applyLaw(UUID victimId) {
        if (victimId == null) {
            return;
        }
        lawRemainingTicks.put(victimId, LAW_DURATION_TICKS);
    }

    /** 当前有罪罚在身的目标数（只读；排障用）。 */
    public int activeLawCount() {
        return lawRemainingTicks.size();
    }

    @Override
    public void start() {
        sante = svc().components().get(SanTEComponent.class);
    }

    @Override
    public void update() {
        if (lawRemainingTicks.isEmpty()) {
            return;
        }

        tickCounter++;
        if (tickCounter < LAW_TICK_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        //遍历中不直接改结构：先收集要移除的键（与 RedBleedPassive 同款）
        List<UUID> finished = new ArrayList<>();

        for (Map.Entry<UUID, Integer> entry : lawRemainingTicks.entrySet()) {
            UUID victimId = entry.getKey();
            Integer recorded = entry.getValue();
            int remaining = recorded != null ? recorded : 0;

            Player victim = Bukkit.getPlayer(victimId);
            //死亡（含躺在死亡界面）或下线的目标立刻停止结算，不能从尸体上继续削 SanTE
            if (victim == null || victim.isDead() || !victim.isOnline() || victim.getHealth() <= 0d) {
                finished.add(victimId);
                continue;
            }

            if (sante != null) {
                sante.decreaseSanTE(victimId, SANTE_PER_TICK);
            }

            int left = remaining - LAW_TICK_INTERVAL_TICKS;
            if (left <= 0) {
                finished.add(victimId);
            } else {
                lawRemainingTicks.put(victimId, left);
            }
        }

        for (UUID victimId : finished) {
            lawRemainingTicks.remove(victimId);
        }
    }

    @Override
    public void stop() {
        lawRemainingTicks.clear();
        tickCounter = 0;
    }
}
