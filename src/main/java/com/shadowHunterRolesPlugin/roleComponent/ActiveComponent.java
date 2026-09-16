package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItem;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 主动组件基类（设计 §4.3）：= 原 `Skill` + `MainWeapon` 去重后的并集，不多一个成员。
 * 两个薄子类（`Skill` / `MainWeapon`）在迁移批次里改为 `extends ActiveComponent`。
 */
public abstract class ActiveComponent extends RoleComponent implements HotbarItem, HotbarActionable {

    private final Component displayName;
    private final Component description;
    private final Material icon;
    private final int cooldownTicks;
    private final int energyCost;
    private final ItemKind kind;

    /**
     * 迁移标记（阶段 4 混合派发判据，硬约束第 12 条）：**默认 false**。
     * <b>严禁</b>用 {@code instanceof ActiveComponent/RoleComponent} 作派据 —— 三个薄基类改基之后
     * 全部 10 个组件都会 instanceof，未迁移组件会被送进新管道而其 {@link #onCast} 默认是 no-op
     * ⇒ 旧回调（onRightClick/onLeftClick/onDrop/onAttack）永不触发 = 行为静默回归。
     * 因此只有**显式标记**（{@link #markMigrated()}）的组件才走新管道。
     */
    private boolean migrated = false;

    protected ActiveComponent(String id, Component displayName, Component description,
                              int cooldownTicks, int energyCost, Material icon, ItemKind kind) {
        super(id);
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = energyCost;
        this.kind = kind;
    }

    /** 是否已迁移到新管道（默认 false；由迁移该组件的批次在自己的构造器里调用 {@link #markMigrated()}）。 */
    public final boolean isMigrated() {
        return migrated;
    }

    /** 由迁移批次的组件构造器调用一次：把自己标记为"走新管道"。 */
    protected final void markMigrated() {
        this.migrated = true;
    }

    @Override
    public final Component getDisplayName() {
        return displayName;
    }

    @Override
    public final Component getDescription() {
        return description;
    }

    @Override
    public final Material getIcon() {
        return icon;
    }

    @Override
    public final int getCooldownTicks() {
        return cooldownTicks;
    }

    @Override
    public final int getEnergyCost() {
        return energyCost;
    }

    @Override
    public final ItemKind getKind() {
        return kind;
    }

    /**
     * 默认：不做事、也**不**进冷却（与今天 listener 的行为一致：未重写的热键栏触发只做就绪预检）。
     * 两种 kind 统一为此默认值，不需要按 kind 分支。
     */
    @Override
    public CastResult onCast(CastSignal signal) {
        return CastResult.NO_COOLDOWN;
    }
}
