package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.mainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
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
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import java.util.List;

public class MeiqiheziJuejueMainWeapon extends MainWeapon {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "meiqihezi_mainWeapon_juejue";

    private VitalsComponent vitals;
    private EnergyComponent energy;
 //**可用性判定下放给子类**（基类不持 buff / energy、不查容器）⇒
    //  本组件自己持 buff 字段（在 start() 内一次查好）。
    private BuffComponent buff;


    public MeiqiheziJuejueMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 图标 / 冷却由这里声明（主武器的能量消耗由类型恒为 0），
     * 栏位由装配点 {@code setSlot} 指定。
     */
    public static final class Specification extends MainWeapon.Specification<MeiqiheziJuejueMainWeapon> {

        public Specification(){
            super(
                    Component.text("Jue Jue"),
                    List.of(Component.text("A ShadowHunter mainWeapon")),
                    Material.DIAMOND_HOE,
                    20
            );
            requires(VitalsComponent.class).requires(EnergyComponent.class).requires(BuffComponent.class);
            //范围伤害逐个受害者判敌 ⇒ 读阵营组件；缺它则本武器不索敌，装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public MeiqiheziJuejueMainWeapon create(String id, ComponentServicesPort services){
            return new MeiqiheziJuejueMainWeapon(id, services, this);
        }
    }

    /**
     * 攻击路径（`MainWeaponListener.onAttackPlayer` 接到 `instance.handleAttack`）。
     * <p>两条分支：
     * <ul>
     *   <li>能量 `>= 20`：走{@link #castAreaDamage(Player)} 那一套范围逻辑（与左键分支同一套）；</li>
     *   <li>能量 `< 20`：单体 `8` 物理伤害 + `0.5` 击退。</li>
     * </ul>
     * 两条分支各自启动一次冷却，单次命中不会双启动。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        Player attacker = svc().self().player();
        Player victim = signal.victim();

 //如果能量大于20，则进行范围伤害 —— 不能直接 return
        if (energy.current() >= 20) {
            castAreaDamage(attacker);
            startCooldown();   //组件自启冷却（框架不再代启动）
            return;
        }

        if (victim != null) {
            vitals.physicalDamage(victim, attacker, 8, 0.5);
        }
        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    /**
     * 左键路径（`CastTrigger.LEFT_CLICK` 分支）：能量 `< 20` 时不做事且不启动冷却；
     * 能量 `>= 20` 时打出范围伤害，冷却由本组件在施放成功处按声明值启动。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.LEFT_CLICK) {
            return;
        }

        Player player = svc().self().player();
        if (energy.current() < 20) return;

        castAreaDamage(player);
        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    /**
     * 范围伤害（左键与"能量 >= 20 的近战命中"共用同一套逻辑）：
     * 扣能量 `5`；半径 `5` 的粒子圆、粒子数 `50`；半径 `5` 判定圈内敌对目标各 `14` 点物理伤害；末尾音效。
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
            if (!svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) continue;
            vitals.physicalDamage(victim, player, 14);
        }

        loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
    }

    /**
     * **开始生效**：把协作组件一次查好缓存进字段（与本族模型一致）。
     * <p>取组件只能在本钩子里做，不得放 `awake()`；注册表装配后冻结，因此与按需解析恒等。
     */
    @Override
    public void start(){
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canUseMainWeapon();
    }

}
