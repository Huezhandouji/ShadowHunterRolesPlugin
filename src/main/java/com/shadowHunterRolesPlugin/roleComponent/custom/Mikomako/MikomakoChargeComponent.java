package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

/**
 * 「决爵」的**充能账本**（0~3 层）+ 唯一的 actionbar 发布者。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>每秒**尝试**消耗 5 点能量换 1 层充能（能量不足则这一秒白等，不扣任何东西）；</li>
 *   <li>最多 3 层；</li>
 *   <li>层数用 actionbar 的 {@code /////////} 进度条 + **不同颜色**表达；</li>
 *   <li>每涨一层：周身红色粒子涌现一次；</li>
 *   <li>满层：背后出现黑红重叠的竖菱形粒子（每 2 刻刷新）。</li>
 * </ol>
 *
 * <h2>为什么单独做成一个组件</h2>
 * 充能被**四处**读：主武器（光效 / 满层触及 / 充能攻击）、三个技能（消耗 1 层）、被动（0 档清空）。
 * 若把计数器放在主武器里，其余组件就得互相 {@code requires} 同一个业务类，形成"技能依赖武器"的假耦合。
 * 账本独立后，四个消费方都只依赖"充能"这一个概念。
 *
 * <p><b>actionbar 单一发布者</b>：本角色只有本组件向 actionbar 写内容（工程口径：后写覆盖先写）。
 */
public class MikomakoChargeComponent extends RoleComponent {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_charge";

    /** 层数上限。 */
    public static final int MAX_STACKS = 3;

    /** 充能判定周期（刻）—— 需求：每秒一次。 */
    public static final int GAIN_INTERVAL_TICKS = 20;

    /** 每次尝试消耗的能量。 */
    public static final int GAIN_ENERGY_COST = 3;

    /** actionbar 刷新间隔（刻）。 */
    public static final int BAR_INTERVAL_TICKS = 5;

    /** 满层光环刷新间隔（刻）—— 2026-10-21 需求 2：0.25 秒一次。 */
    public static final int AURA_INTERVAL_TICKS = 5;

    /** 满层的"放大闪动"间隔（刻）—— 每 2 秒一次。 */
    public static final int AURA_BURST_INTERVAL_TICKS = 40;

    private EnergyComponent energy;
    private HotbarRenderComponent render;
    private MikomakoFloatingTextComponent voice;

    private int stacks;
    private int gainTimer;
    private int barTimer;
    private int auraTimer;
    private int phase;

    public MikomakoChargeComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services);
    }

    /** 装配描述符：只依赖能量（账本的真值来源）。 */
    public static final class Specification extends RoleComponent.Specification<MikomakoChargeComponent> {

        public Specification() {
            super("MikomakoCharge");
            requires(EnergyComponent.class);
            requiresOptional(HotbarRenderComponent.class);
            requiresOptional(MikomakoFloatingTextComponent.class);
        }

        @Override
        public MikomakoChargeComponent create(String id, ComponentServicesPort services) {
            return new MikomakoChargeComponent(id, services, this);
        }
    }

    @Override
    public void start() {
        energy = getComponent(EnergyComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        voice = getComponent(MikomakoFloatingTextComponent.class);
    }

    @Override
    public void update() {
        Player player = svc().self().player();
        if (player == null || !player.isOnline()) {
            return;
        }

        if (++gainTimer >= GAIN_INTERVAL_TICKS) {
            gainTimer = 0;
            // 满层：**不尝试、不扣能量**（需求 5）
            if (!isFull()) {
                attemptGain(player);
            }
        }

        if (++barTimer >= BAR_INTERVAL_TICKS) {
            barTimer = 0;
            publishBar(player);
        }

        if (stacks >= MAX_STACKS) {
            auraTimer++;
            if (auraTimer % AURA_INTERVAL_TICKS == 0) {
                phase++;
                MikomakoVfx.chargeAura(player, phase);
            }
            if (auraTimer % AURA_BURST_INTERVAL_TICKS == 0) {
                MikomakoVfx.chargeAuraBurst(player);
            }
        } else {
            auraTimer = 0;
        }
    }

    /** 每秒一次：层数未满且能量够，就换一层。 */
    private void attemptGain(Player player) {
        if (stacks >= MAX_STACKS || energy == null) {
            return;
        }
        if (!energy.tryConsume(GAIN_ENERGY_COST)) {
            return;
        }
        addStacks(1, player);
    }

    /**
     * 加层（外部只经此入口，保证"涨层即刷新光效 / 重绘图标 / 粒子"三件事不会被漏做）。
     *
     * @param amount 层数（正数）
     * @return 真实增加量
     */
    public int addStacks(int amount, Player player) {
        if (amount <= 0) {
            return 0;
        }
        int before = stacks;
        stacks = Math.min(MAX_STACKS, stacks + amount);
        int gained = stacks - before;
        if (gained <= 0) {
            return 0;
        }
        if (player != null && player.isOnline()) {
            MikomakoVfx.chargeBurst(player);
            MikomakoSound.chargeGain(player);
            if (stacks >= MAX_STACKS) {
                MikomakoSound.fullCharge(player);
                if (voice != null) {
                    voice.sayMoment(player, MikomakoFloatingTextComponent.MOMENT_FULL_CHARGE);
                }
            }
            publishBar(player);
        }
        requestRepaint();
        return gained;
    }

    /**
     * 尝试消耗层数。
     *
     * @param max 最多消耗几层（≤0 视为不消耗）
     * @return 真实消耗的层数
     */
    public int consume(int max) {
        if (max <= 0 || stacks <= 0) {
            return 0;
        }
        int used = Math.min(max, stacks);
        stacks -= used;
        requestRepaint();
        return used;
    }

    /** 清空全部层数（0 档爆发后调用）。 */
    public void clear() {
        if (stacks == 0) {
            return;
        }
        stacks = 0;
        requestRepaint();
    }

    /** 当前层数。 */
    public int stacks() {
        return stacks;
    }

    /** 是否至少有 1 层。 */
    public boolean hasCharge() {
        return stacks > 0;
    }

    /** 是否满层（3 层）。 */
    public boolean isFull() {
        return stacks >= MAX_STACKS;
    }

    /** 满层额外 +1 触及（基础 +1 由武器固定给）。 */
    public int extraReach() {
        return isFull() ? 1 : 0;
    }

    private void requestRepaint() {
        if (render != null) {
            render.markDirty();
        }
    }

    /** actionbar：{@code /////////} 进度条，颜色随层数变化。 */
    private void publishBar(Player player) {
        NamedTextColor color = switch (stacks) {
            case 0 -> NamedTextColor.DARK_GRAY;
            case 1 -> NamedTextColor.RED;
            case 2 -> NamedTextColor.GOLD;
            default -> NamedTextColor.YELLOW;
        };

        int slots = 9;
        int filled = stacks * 3;
        Component bar = Component.empty();
        for (int i = 0; i < slots; i++) {
            boolean on = i < filled;
            Component ch = Component.text("/").color(on ? color : NamedTextColor.DARK_GRAY);
            if (on && stacks >= MAX_STACKS) {
                ch = ch.decorate(TextDecoration.BOLD);
            }
            bar = bar.append(ch);
        }

        Component text = Component.text("充能 ", NamedTextColor.GRAY)
                .append(bar)
                .append(Component.text(" " + stacks + "/" + MAX_STACKS + (stacks >= MAX_STACKS ? " MAX" : ""), color));
        player.sendActionBar(text);
    }
}
