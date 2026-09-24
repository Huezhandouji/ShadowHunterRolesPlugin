package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.dispatch.CastSignal;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;

public class RedEvilShockSkill extends Skill{

    /**
     * **SanTEComponent 取用入口**（阶段 13 · t103）：向**组件本身**取用（R-6），不再经服务集的白名单端口成员。
     * <p>按需解析（**不缓存**）：R-4 只禁 `awake()`；不缓存引用 ⇒ 不引入生命周期耦合
     * （基类/子类各自覆写 `start()` 时，缓存的引用可能静默为空 ✗）。
     */
    private final SanTEComponent santeComponent(){
        return svc().components().get(SanTEComponent.class);
    }

    /**
     * **BuffComponent 取用入口**（阶段 13 · t103）：向**组件本身**取用（R-6），不再经服务集的白名单端口成员。
     * <p>按需解析（**不缓存**）：R-4 只禁 `awake()`；不缓存引用 ⇒ 不引入生命周期耦合
     * （基类/子类各自覆写 `start()` 时，缓存的引用可能静默为空 ✗）。
     */
    private final BuffComponent buffComponent(){
        return svc().components().get(BuffComponent.class);
    }

    public RedEvilShockSkill(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（阶段 7 · B 步）：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(Component.text("煞气震赫"),
                    Component.text("对周围5格范围内的敌人造成3秒致盲和缓慢III，结算他们5层流血。恢复[红]的10点TE值"),
                    120, 0, Material.REDSTONE);
        }

        @Override
        public RedEvilShockSkill create(String id, ComponentServices services){
            return new RedEvilShockSkill(id, services, this);
        }
    }

    /**
     * 批次①（B①）迁移：旧 `onRightClick(Player, RoleInstance)` 的**逐条等价**新写法。
     * 触发条件/范围/持续时间/增幅/层数/音效均不变；`canCastSkill` 不满足时**直接返回**
     * （该路径**不启冷却**）；冷却由本组件在施放成功处按声明值启动。
     * <p>阶段 8：返回类型改 {@code void}（旧的施放结果枚举已删，返回值无消费点）。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buffComponent().canCastSkill()) return;

        RedBleedPassive bleed = getComponent(RedBleedPassive.class);
        for(Player p : caster.getLocation().getNearbyPlayers(5)){
            if(svc().roleInfo().isHostile(p)){
                p.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(61, 1));
                p.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(61, 3));
                //结算5层流血：写账本的唯一公开入口（硬约束第 18 条前移）
                //O-9：流血被动未注册时直接跳过，不能让本技能抛 NPE
                if(bleed == null) continue;
                bleed.requestResolve(p.getUniqueId(), 5);
            }
        }
        santeComponent().gain(10);

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);

        svc().cooldowns().start(getCooldownTicks());   //D1：组件自启冷却（框架不再代启动）
    }
}
