package com.shadowHunterRolesPlugin.roleComponent.custom.red;
import com.shadowHunterRolesPlugin.roleComponent.builtin.Buff;

import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import java.util.List;

public class RedEvilShockSkill extends Skill{

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "red_evilShock_skill";

    private BuffComponent buff;
    private SanTEComponent sante;

    public RedEvilShockSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 冷却 / 耗能 / 图标由这里声明，
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification<RedEvilShockSkill> {

        public Specification(){
            super(Component.text("煞气震赫"),
                    List.of(Component.text("对周围5格范围内的敌人造成3秒致盲和缓慢III，结算他们5层流血。恢复[红]的10点TE值")),
                    120, 0, Material.REDSTONE);
            requires(BuffComponent.class).requires(SanTEComponent.class);
        }

        @Override
        public RedEvilShockSkill create(String id, ComponentServicesPort services){
            return new RedEvilShockSkill(id, services, this);
        }
    }

    /**
     * 右击施放：`canCastSkill` 不满足时直接返回（该路径不启冷却），
     * 冷却由本组件在施放成功处按声明值启动；
     * 触发条件 / 范围 / 持续时间 / 增幅 / 层数 / 音效均与既有口径一致。
     */
    @Override
    public void onCast(CastSignal signal){
        Player caster = svc().self().player();
        if(!buff.canCastSkill()) return;

        RedBleedPassive bleed = getComponent(RedBleedPassive.class);
        for(Player p : caster.getLocation().getNearbyPlayers(5)){
            if(svc().roleInfo().isHostileTo(p.getUniqueId())){
                p.addPotionEffect(PotionEffectType.BLINDNESS.createEffect(61, 1));
                p.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(61, 3));
                //结算5层流血：写账本的唯一公开入口
                //流血被动未注册时直接跳过，不能让本技能抛 NPE
                if(bleed == null) continue;
                bleed.requestResolve(p.getUniqueId(), 5);
            }
        }
        sante.increase(10);

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITCH_CELEBRATE, 1, 1);
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_WITHER_SHOOT, 1, 1);

        startCooldown();   //组件自启冷却（框架不再代启动）

    }

    /**
     * **开始生效**：把协作组件一次查好缓存进字段（与本族模型一致）。
     * <p>取组件只能在本钩子里做，不得放 `awake()`；注册表装配后冻结，因此与按需解析恒等。
     */
    @Override
    public void start(){
        buff = svc().components().get(BuffComponent.class);
        sante = svc().components().get(SanTEComponent.class);
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buff.canCastSkill();
    }

    /**
     * **当前能量**：本组件不参与能量维度（声明耗能 0），因此返回声明值；
     * 能量组件的值被 clamp 到 `[0, max]`，故 `current() < 0` 恒假。
     */
    @Override
    protected int currentEnergy(){
        return getEnergyCost();
    }
}
