package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.mainWeapon;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive.LawWordPassive;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import java.util.List;

/**
 * 「罪棘」的主武器：罪棘之牙。
 *
 * <p>为什么需要主武器：插件把玩家攻击事件只投递给主武器组件
 * （{@code listener/MainWeaponListener#onAttackPlayer} 先判手持物是不是主武器，再按该武器的 id 派发
 * {@code onAttack}）。被动组件收不到攻击信号，因此"玩家近战也触发律法之言"这条
 * 必须有一把主武器作为落点。
 *
 * <p>攻击路径：8 点物理伤害（带击退）→ 经 {@link LawWordPassive#applyLaw} 给命中者挂「律法之言」
 * 罪罚，与 {@code SinThornPassive} 的尖牙写同一份账本（拿不到账本时只跳过罪罚，普攻伤害照常结算）。
 * 冷却由本组件在攻击成功处按声明值启动。
 */
public class SinThornFangMainWeapon extends MainWeapon {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "sinThorn_mainWeapon_fang";

    /** 普攻物理伤害。 */
    private static final double ATTACK_DAMAGE = 8.0;

    /** 普攻击退强度。 */
    private static final double ATTACK_KNOCKBACK = 1.0;

    /**
     * **普攻冷却：0.5 秒 = 10 刻**。
     * <p>声明值同时决定两件事：① 快捷栏图标在这段时间里显示"冷却中"；
     * ② {@code onAttack} 里用它挡住冷却期内的攻击（冷却中攻击不出伤）。
     */
    private static final int ATTACK_COOLDOWN_TICKS = 10;

    private VitalsComponent vitals;
    private BuffComponent buff;

    /** 「律法之言」账本（同角色内另一个被动）；拿不到时按 {@code null} 容忍。 */
    private LawWordPassive lawWord;

    public SinThornFangMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（主武器耗能由类型恒为 0；栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends MainWeapon.Specification<SinThornFangMainWeapon> {

        public Specification() {
            super(Component.text("罪棘之牙"),
                    List.of(Component.text("召唤者的尖牙：攻击造成8点物理伤害，并施加「律法之言」——15秒内每0.5秒削减目标1点特殊值")),
                    Material.NETHERITE_AXE,
                    ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(LawWordPassive.class);
        }

        @Override
        public SinThornFangMainWeapon create(String id, ComponentServicesPort services) {
            return new SinThornFangMainWeapon(id, services, this);
        }
    }

    /**
     * **开始生效**：协作组件一次查好缓存进字段（只在 {@code start()} 取）。
     */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        lawWord = svc().components().get(LawWordPassive.class);
    }

    /**
     * **攻击路径**（由 {@code MainWeaponListener} 在近战命中时投递）。
     *
     * <p>攻速闸门：主武器攻击冷却 0.5 秒（{@link #ATTACK_COOLDOWN_TICKS}），
     * 且冷却中攻击不出伤 —— 冷却期直接 return，连声音/粒子都不放。
     * <p>{@code MainWeaponListener} 对攻击不做冷却判定（它只判手持物是不是主武器），
     * 所以这道闸门必须由本组件自己把住。
     */
    @Override
    public void onAttack(AttackSignal signal) {
        //冷却中则这一下攻击完全不出伤（也不进账本、不放特效）
        if (isCoolingDown()) {
            return;
        }

        Player attacker = svc().self().player();
        Player victim = signal.victim();

        if (victim != null) {
            vitals.physicalDamage(victim, attacker, ATTACK_DAMAGE, ATTACK_KNOCKBACK);

            //罪罚账本由 LawWordPassive 私有持有：拿不到时只跳过罪罚，普攻伤害依然生效
            if (lawWord != null) {
                lawWord.applyLaw(victim.getUniqueId());
            }

            victim.spawnParticle(Particle.DUST,
                    victim.getLocation().clone().add(0, 1, 0), 8, 0.4, 0.4, 0.4,
                    new Particle.DustOptions(Color.fromRGB(90, 20, 40), 1f));
        }

        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.8f);

        //冷却由本组件在攻击成功处按声明值启动
        startCooldown();
    }

    /**
     * **闸门放行？**（基类不查容器，由本组件用自己的 {@code buff} 字段判）。
     */
    @Override
    protected boolean canUse() {
        return buff.canUseMainWeapon();
    }
}
