package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.ParticleUtil;
import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastTrigger;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;

public class MeiqiheziJuejueMainWeapon extends MainWeapon {


    public MeiqiheziJuejueMainWeapon(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 图标 / 冷却逐字段一致；
     * 主武器的能量消耗由类型恒为 0），栏位由装配点 {@code setSlot} 指定。
     */
    public static final class Specification extends MainWeapon.Specification {

        public Specification(){
            super(
                    Component.text("Jue Jue"),
                    Component.text("A ShadowHunter mainWeapon"),
                    Material.DIAMOND_HOE,
                    20
            );
        }

        @Override
        public MeiqiheziJuejueMainWeapon create(String id, ComponentServices services){
            return new MeiqiheziJuejueMainWeapon(id, services, this);
        }
    }

    /**
     * 攻击路径（新管道；B⑦ 已把 `MainWeaponListener.onAttackPlayer` 接到 `instance.handleAttack`）。
     * <p>语义与旧路径**逐条等价** —— 旧 listener 在近战命中时**同时**调 `onAttack(...)` 与 `onLeftClick(...)`：
     * <ul>
     *   <li>能量 `>= 20`：`onAttack` 直接返回（不做事），**范围伤害由 `onLeftClick` 打出** ⇒ 本方法在此分支
     *       调用同一套范围逻辑（{@link #castAreaDamage(Player)}），**行为等价**；</li>
     *   <li>能量 `< 20`：单体 `8` 物理伤害 + `0.5` 击退（逐字不变）。</li>
     * </ul>
     * 两条分支都返回 {@code SUCCEED}（旧路径的冷却由 listener 在调用前启动 ⇒ 等价、且**不双启动**）。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        Player attacker = svc().self().player();
        Player victim = signal.victim();

        //如果能量大于20，则进行范围伤害（旧写法在 onLeftClick 里，由 listener 同帧调用）—— 不能直接 return
        if (svc().energy().current() >= 20) {
            castAreaDamage(attacker);
            svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
            return;
        }

        if (victim != null) {
            svc().damage().physicalDamage(victim, attacker, 8, 0.5);
        }
        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
    }

    /**
     * 左键路径（新管道，`CastTrigger.LEFT_CLICK` 分支）。语义与旧 `onLeftClick(Player, RoleInstance)`
     * **逐条等价**：能量 `< 20` 时**不做事且不启动冷却**（旧代码直接 return）⇒ 返回 {@code NO_COOLDOWN}；
     * 能量 `>= 20` 时打出范围伤害 ⇒ 返回 {@code SUCCEED}（冷却由本组件在施放成功处按声明值启动）。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.LEFT_CLICK) {
            return;
        }

        Player player = svc().self().player();
        if (svc().energy().current() < 20) return;

        castAreaDamage(player);
        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
    }

    /**
     * 范围伤害（左键与"能量>=20 的近战命中"共用同一套逻辑，**可见数值逐字保留**）：
     * 扣能量 `5`；半径 `5` 的粒子圆、粒子数 `50`；半径 `5` 判定圈内敌对目标各 `14` 点物理伤害；末尾音效不变。
     */
    private void castAreaDamage(Player player) {
        svc().energy().tryConsume(5);

        Location loc = player.getLocation();

        ParticleBuilder pb = Particle.DUST.builder()
                .count(1)
                .color(Color.RED)
                .offset(0, 0, 0);

        ParticleUtil.drawCircle(loc.clone().add(0, 1, 0), 5, pb, 50);

        Collection<? extends Player> victims = loc.getNearbyPlayers(5);

        for (Player victim : victims) {
            if (!svc().roleInfo().isHostile(victim)) continue;
            svc().damage().physicalDamage(victim, player, 14);
        }

        loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
    }

}
