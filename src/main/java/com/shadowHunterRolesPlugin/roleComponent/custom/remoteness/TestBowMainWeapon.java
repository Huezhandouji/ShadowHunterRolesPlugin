package com.shadowHunterRolesPlugin.roleComponent.custom.remoteness;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.BowWeapon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 弓弩组件的示例（冷识角色 0 号栏）—— 同时是 {@link BowWeapon} 的最小用法示范。
 *
 * <p>本族的三个入口各在一处，这个组件把它们的落点都摆出来：
 * <ul>
 *   <li>{@link #onCast(CastSignal)}：左键 / Q（trigger 只会有 {@code LEFT_CLICK} 与 {@code DROP}）；</li>
 *   <li>{@link #onShoot(ShootSignal)}：箭矢离弦（拉弓归原版，因此"冷却中拉不开弓"由
 *       {@code listener/BowWeaponListener} 在右键入口把住）；</li>
 *   <li>近战命中**不在这里**：左键打人由 listener 直接拦截，本族没有攻击入口。</li>
 * </ul>
 * <p>两个入口都只放"动作栏回显 + 启动冷却"，目的是让三条管道的通断能在游戏里一眼验到；
 * 做真功能时把方法体换掉即可。
 */
public class TestBowMainWeapon extends BowWeapon {

    public static final String ID = "testBowMainWeapon";

    /**
     * 描述符口径的构造：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事（弓弩的能量消耗由类型恒为 0）。
     */
    public TestBowMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends BowWeapon.Specification<TestBowMainWeapon> {
        public Specification() {
            super(
                    Component.text("测试弓主武器"),
                    List.of(Component.text("测试武器 ")),
                    20,
                    Material.BOW
            );
        }

        @Override
        public TestBowMainWeapon create(String id, ComponentServicesPort services) {
            return new TestBowMainWeapon(id, services, this);
        }
    }

    @Override
    protected boolean canUse() {
        return !isCoolingDown();
    }

    /**
     * 施放路径（左键 / Q）：回显 trigger 后按声明值启动冷却 —— 这两行同时是"入口通不通"与
     * "冷却自启"两个契约的示范（框架与 listener 都不代启动冷却，组件在入口里自己调）。
     */
    @Override
    public void onCast(CastSignal signal) {
        Player self = svc().self() == null ? null : svc().self().player();
        if (self == null) return;
        self.sendActionBar(Component.text("onCast: " + signal.trigger()));
        startCooldown();
    }

    /**
     * 射击路径（由 {@code BowWeaponListener} 在箭矢离弦后投递）。
     * <p>射出的箭矢本体在 {@code signal.projectile()} 里，需要时可在本处继续加工
     * （例：按 {@code signal.force()} 分级改伤害 / 加粒子 / 给箭矢打标记）。
     */
    @Override
    public void onShoot(ShootSignal signal) {
        startCooldown();
    }
}
