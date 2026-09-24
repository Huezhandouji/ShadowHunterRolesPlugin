package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;

public class RedEvilShockSkill extends Skill{

    private BuffComponent buff;
    private SanTEComponent sante;

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
        if(!buff.canCastSkill()) return;

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
        sante.gain(10);

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);

        startCooldown();   //D1：组件自启冷却（框架不再代启动）

    }

    /**
     * **开始生效**（阶段 13 · t107）：把协作组件**一次查好**缓存进字段 ✓（与本族模型一致）。
     * <p>R-4：取组件只能在本钩子里做 ✗ —— 不得放 `awake()`；注册表装配后冻结 ⇒ 与按需解析恒等 ✓。
     */
    @Override
    public void start(){
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
    }

    /**
     * **闸门放行？**（阶段 13 · t110：基类不再取 buff ⇒ 由本组件用**自己的字段**判）。
     */
    @Override
    protected boolean gateOpen(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**（阶段 13 · t110）：本组件**不参与能量维度**（声明耗能 0）⇒ 返回声明值；
     * 与迁移前**逐字等价**（能量组件内 clamp 到 `[0, max]` ⇒ 原判定 `current() < 0` 恒假）。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
