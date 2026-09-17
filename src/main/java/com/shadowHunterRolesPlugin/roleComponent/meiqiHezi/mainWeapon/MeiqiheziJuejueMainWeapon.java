package com.shadowHunterRolesPlugin.roleComponent.meiqiHezi.mainWeapon;

import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.core.MainWeapon;
import com.shadowHunterRolesPlugin.core.ParticleUtil;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.dispatch.AttackSignal;
import com.shadowHunterRolesPlugin.core.dispatch.CastResult;
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


    public MeiqiheziJuejueMainWeapon() {
        super(
                "meiqihezi_mainWeapon_juejue",
                Component.text("Jue Jue"),
                Component.text("A ShadowHunter mainWeapon"),
                Material.DIAMOND_HOE,
                20
        );
        markMigrated();
    }

    /**
     * 攻击路径（新管道）。语义与旧 `onAttack(Player, Player, RoleInstance)` **逐条等价**：
     * 能量 `>= 20` 时**不进入本逻辑**（范围伤害写在左键路径），但**冷却仍照旧启动**（旧 listener 在调用前
     * 就启动了冷却）⇒ 两条分支都返回 {@code CAST}；能量 `< 20` 时造成单体 `8` 物理伤害 + `0.5` 击退（逐字不变）。
     */
    @Override
    public CastResult onAttack(AttackSignal signal) {
        Player attacker = svc().self().player();
        Player victim = signal.victim();

        //如果能量大于20，则进行范围伤害，写在左键路径，不进入这个逻辑
        if (svc().energy().current() >= 20) return CastResult.CAST;

        if (victim != null) {
            svc().damage().physicalDamage(victim, attacker, 8, 0.5);
        }
        return CastResult.CAST;
    }

    /**
     * 左键路径（新管道，`CastTrigger.LEFT_CLICK` 分支）。语义与旧 `onLeftClick(Player, RoleInstance)` **逐条等价**：
     * 能量 `< 20` 时**不做事且不启动冷却**（旧代码直接 return）⇒ 返回 {@code NO_COOLDOWN}；能量 `>= 20` 时
     * 扣 `5` 能量、半径 `5` 的粒子圆（**粒子表现同属可见行为，逐字保留**）、半径 `5` 内的敌对目标各受 `14` 点
     * 物理伤害、末尾音效不变 ⇒ 返回 {@code CAST}（冷却由框架按声明值启动）。
     */
    @Override
    public CastResult onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.LEFT_CLICK) {
            return CastResult.NO_COOLDOWN;
        }

        Player player = svc().self().player();
        if (svc().energy().current() < 20) return CastResult.NO_COOLDOWN;
        svc().energy().tryConsume(5);

        Location loc = player.getLocation();

        ParticleBuilder pb = Particle.DUST.builder()
                .count(1)
                .color(Color.RED)
                .offset(0, 0, 0);

        ParticleUtil.drawCircle(loc.clone().add(0, 1, 0), 5, pb, 50);

        Collection<? extends Player> victims = loc.getNearbyPlayers(5);

        for (Player victim : victims) {
            if (!svc().factions().isHostile(victim)) continue;
            svc().damage().physicalDamage(victim, player, 14);
        }

        loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);

        return CastResult.CAST;
    }

    /**
     * **过渡委托壳（B⑦ 保留，待接线后删除）**：`MainWeaponListener:58` 仍直接调本签名（攻击路径尚未接线到
     * `RoleInstance.handleAttack`，该方法全树 0 调用点）⇒ 为**不产生"近战静默空操作"的回归**，本壳仅
     * 1 行转调新钩子；冷却仍由 listener `:55` 按旧行为启动，**不存在双启动**。
     * 待 `listener/MainWeaponListener.java` 接线（另卡）后，本壳删除。
     */
    @Override
    public void onAttack(Player attacker, Player victim, RoleInstance instance) {
        onAttack(new AttackSignal(victim));
    }

}
