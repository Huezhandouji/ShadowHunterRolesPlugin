package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.HotbarActionable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarItem;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarPresentable;
import com.shadowHunterRolesPlugin.core.hotbar.HotbarSpec;
import com.shadowHunterRolesPlugin.core.hotbar.ItemKind;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

/**
 * 主动组件基类（设计 §4.3）：= 原 `Skill` + `MainWeapon` 去重后的并集，不多一个成员。
 * <p>
 * <b>阶段 6 · 统一装配</b>：本类降级为**可选的便利实现** —— 它只做一件事：把构造参数装进
 * {@link HotbarSpec} 并实现 {@link #spec()}（**唯一实现点**）。7 个表现访问器全部由
 * {@link HotbarPresentable} 的 `default` 方法提供 ⇒ 子类不再需要（也不应）逐个手写委托。
 * <p>
 * 行为分支（冷却表 / 闸门 / PDC 键 / 文案表）一律由**注册处**的 kind 决定
 * （`Role.Builder` 统一入口的入参 ⇒ `RoleInstance.createServices(id, kind)`）；
 * 本类 spec 里的 kind **只作表现用途**。新组件若不需要"能施放 + 能渲染"这套默认组合，
 * 可以只 `extends RoleComponent` 并按需实现能力接口（本批不删三个基类）。
 */
public abstract class ActiveComponent extends RoleComponent
        implements HotbarItem, HotbarActionable, HotbarPresentable {

    private final HotbarSpec spec;

    protected ActiveComponent(String id, ComponentServices services, Component displayName, Component description,
                              int cooldownTicks, int energyCost, Material icon, ItemKind kind) {
        super(id, services);
        this.spec = HotbarSpec.of(id, displayName, description, icon, cooldownTicks, energyCost, kind);
    }

    /** **唯一实现点**：表现规格（`getId` / `getDisplayName` / … 7 个访问器由接口 default 委托到本方法）。 */
    @Override
    public final HotbarSpec spec() {
        return spec;
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
