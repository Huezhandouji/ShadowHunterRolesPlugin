package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.destroystokyo.paper.ParticleBuilder;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.core.util.ParticleUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Collection;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;

public class MeiqiheziJuejueMainWeapon extends MainWeapon {

    /** **本组件的登记 id**（★ 知识归属：组件自己 —— 谁是什么 id 由谁说了算）。 */
    public static final String ID = "meiqihezi_mainWeapon_juejue";

    private VitalsComponent vitals;
    private EnergyComponent energy;
 //**可用性判定下放给子类**（基类不持 buff / energy、不查容器）⇒
    //  本组件自己持 buff 字段（在既有 start() 内一次查好 ✓）。
    private BuffComponent buff;


    public MeiqiheziJuejueMainWeapon(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**：表现值默认值 = 原构造实参（名字 / 描述 / 图标 / 冷却逐字段一致；
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
            requires(VitalsComponent.class).requires(EnergyComponent.class).requires(BuffComponent.class);
        }

        @Override
        public MeiqiheziJuejueMainWeapon create(String id, ComponentServices services){
            return new MeiqiheziJuejueMainWeapon(id, services, this);
        }
    }

    /**
     * 攻击路径（新管道；`MainWeaponListener.onAttackPlayer` 接到 `instance.handleAttack`）。
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

 //如果能量大于20，则进行范围伤害（早先写法在 onLeftClick 里，由 listener 同帧调用）—— 不能直接 return
        if (energy.current() >= 20) {
            castAreaDamage(attacker);
            startCooldown();   //D1：组件自启冷却（框架不再代启动）
            return;
        }

        if (victim != null) {
            vitals.physicalDamage(victim, attacker, 8, 0.5);
        }
        startCooldown();   //D1：组件自启冷却（框架不再代启动）
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
        if (energy.current() < 20) return;

        castAreaDamage(player);
        startCooldown();   //D1：组件自启冷却（框架不再代启动）
    }

    /**
     * 范围伤害（左键与"能量>=20 的近战命中"共用同一套逻辑，**可见数值逐字保留**）：
     * 扣能量 `5`；半径 `5` 的粒子圆、粒子数 `50`；半径 `5` 判定圈内敌对目标各 `14` 点物理伤害；末尾音效不变。
     */
    private void castAreaDamage(Player player) {
        energy.tryConsume(5);

        Location loc = player.getLocation();

        ParticleBuilder pb = Particle.DUST.builder()
                .count(1)
                .color(Color.RED)
                .offset(0, 0, 0);

        ParticleUtil.drawCircle(loc.clone().add(0, 1, 0), 5, pb, 50);

        Collection<? extends Player> victims = loc.getNearbyPlayers(5);

        for (Player victim : victims) {
            if (!svc().roleInfo().isHostile(victim)) continue;
            vitals.physicalDamage(victim, player, 14);
        }

        loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
    }

    /**
     * **开始生效**：把协作组件**一次查好**缓存进字段 ✓（与本族模型一致）。
     * <p>取组件只能在本钩子里做 ✗ —— 不得放 `awake()`；注册表装配后冻结 ⇒ 与按需解析恒等 ✓。
     */
    @Override
    public void start(){
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * **闸门放行？**（基类不再取 buff ⇒ 由本组件用**自己的字段**判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canUseMainWeapon();
    }

}
