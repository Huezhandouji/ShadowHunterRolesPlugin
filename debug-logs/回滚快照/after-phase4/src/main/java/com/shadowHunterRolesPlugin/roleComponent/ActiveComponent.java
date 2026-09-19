package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItem;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
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
     * T-2 ① 后**不再有迁移标记**：所有组件**无条件**走新管道（单一入口 = handleCast/handleAttack）；
     */

    protected ActiveComponent(String id, ComponentServices services, Component displayName, Component description,
                              int cooldownTicks, int energyCost, Material icon, ItemKind kind) {
        super(id, services);
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.cooldownTicks = cooldownTicks;
        this.energyCost = energyCost;
        this.kind = kind;
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

    /**
     * 冷却结束通知（阶段 4 追补 · 冷却自管理 D4）：默认**空实现** ⇒ 既有组件零改动即可编译。
     * <p>允许在回调里再 {@code start(...)}（例如"到点立刻接下一段"）；容器**先移除冷却条目、再回调**
     * ⇒ 回调内再 {@code end()} 只会得到 {@code false}（不会同步递归重入）。
     */
    public void onCooldownEnd(CooldownEndReason reason) {
    }

    /** 冷却结束的原因（D4 三分法）。 */
    public enum CooldownEndReason {
        /** 自然到期。 */
        EXPIRED,
        /** 被组件/外部显式 {@code end()} 结束（"冷却中结束冷却"的情形）。 */
        ENDED_BY_COMPONENT,
        /** 在冷却中被再次 {@code start(...)} 顶替（重复开启按新的来）。 */
        RESTARTED
    }
}
