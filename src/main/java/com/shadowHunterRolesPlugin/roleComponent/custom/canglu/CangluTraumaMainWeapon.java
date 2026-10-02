package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedSanctifiedBladeMainWeapon;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;
import java.util.List;

/**
 * 苍鹭 · 忧郁创痕（主武器）。
 *
 * <p><b>攻击间隔 = 0.1 秒 / 次</b>（需求）。要做到 0.1s 必须三件齐备，缺一即失效：
 * <ol>
 *   <li>声明冷却 = 2 刻（{@value #ATTACK_COOLDOWN_TICKS}）；</li>
 *   <li>{@code onAttack} <b>开头</b>自判 {@code isCoolingDown()} ——
 *       <b>派发侧不替主武器挡冷却</b>：{@code MainWeaponListener} 只在技能路径判冷却，
 *       主武器必须自己判，否则冷却期内再次攻击仍会出伤；</li>
 *   <li>物品攻速设为 {@value #ATTACK_SPEED_VALUE}（默认钻石剑 1.6 ⇒ 原版冷却 ≈ 12.5 刻 ≈ 0.62 秒，
 *       会把 2 刻的间隔压长成 0.62 秒）。</li>
 * </ol>
 */
public class CangluTraumaMainWeapon extends MainWeapon {

    public static final String ID = "cangluTraumaMainWeapon";

    //注：上游曾在此处加过一个 `COOLDOWN = 16` 常量并在 Specification 里使用它；
    //本地把它改成 `ATTACK_COOLDOWN_TICKS = 2`（需求：本武器 0.1 秒一击），
    //合并后那个常量已无任何引用 ⇒ 删除，避免与下面那个真正的攻击间隔常量混淆。
    private static final int DAMAGE = 6;
    private static final float KNOCKBACK = 0.5f;
    private static final int EXTRA_TURE_DAMAGE_AMOUNT = 4;
    private static final int SANTE_DAMAGE_AMOUNT = 12;
    private static final int ENERGY_THEFT_AMOUNT = 2;
    private static final int SPEED_EFFECT_LEVEL = 5;
    private static final int SPEED_EFFECT_TIME = 80;

    /** 攻击冷却 = 2 刻 = 0.1 秒。 */
    private static final int ATTACK_COOLDOWN_TICKS = 2;
    /** 物品攻速修饰符的目标值（统一 100，见流程文档 §6.3）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;
    /** 攻速属性修饰符的命名键（先移除后添加 ⇒ 必须固定，否则去不掉旧的）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("canglu_trauma_main_weapon_attack_speed");

    SanTEComponent sante;
    BuffComponent buff;
    EnergyComponent energy;
    VitalsComponent vitals;
    CangluHysteriaPassive hysteriaPassive;


    /**
     * **描述符口径的构造**：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事（主武器的能量消耗由类型恒为 0）。
     *
     * @param id
     * @param services
     * @param specification
     */
    public CangluTraumaMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends MainWeapon.Specification<CangluTraumaMainWeapon> {

        public Specification(){
            super(
                    Component.text("忧郁创痕-主武器"),
                    List.of(
                            Component.text("攻击造成12特殊值伤害并夺取2能量，增加移速。如果'创伤'层数不小于4，将结算4层创伤"),
                            Component.text("=============================="),
                            Component.text("被动:"),
                            Component.text("深度癔症: 每次攻击或使用技能叠加1层「创伤」,第4层时普通攻击造成额外4点灵魂伤害、获得4点不可叠加护盾并回复12点SAN值(最多24层)")
                    ),
                    Material.DIAMOND_SWORD,
                    ATTACK_COOLDOWN_TICKS
            );
            requires(SanTEComponent.class);
            requires(EnergyComponent.class);
            requires(BuffComponent.class);
            requires(VitalsComponent.class);
            requires(CangluHysteriaPassive.class);
        }

        @Override
        public CangluTraumaMainWeapon create(String id, ComponentServicesPort services){
            return new CangluTraumaMainWeapon(id, services, this);
        }
    }

    @Override
    public void onAwake(){

    }

    @Override
    public void start(){
        sante = svc().components().get(SanTEComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        hysteriaPassive = svc().components().get(CangluHysteriaPassive.class);
    }

    @Override
    public void stop(){

    }

    /**
     * 物品实例：先 {@code super.buildItem()}（识别键写在基类那一步，漏了会点击无反应），
     * 再叠加攻速修饰符。
     */
    @Override
    public ItemStack buildItem(){
        ItemStack stack = super.buildItem();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        applyAttackSpeed(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 攻速统一 100：先移除再添加（不去重会叠加成 100/200/300）。 */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    @Override
    public void onAttack(AttackSignal signal){

        //★ 主武器必须自判冷却（派发侧不替主武器挡），否则 0.1s 只是纸面数字
        //  （合并上游时此处出现重复守卫，已去重；顺序 = 先冷却、再闸门）
        if(isCoolingDown()) return;
        if(!canUse()) return;

        UUID victimPid = signal.victim().getUniqueId();
        sante.decreaseSanTE(victimPid, SANTE_DAMAGE_AMOUNT);

        int actualEnergyStealAmount = energy.decreaseEnergy(victimPid, ENERGY_THEFT_AMOUNT);
        energy.increase(actualEnergyStealAmount);

        buff.applyPotionEffect(PotionEffectType.SPEED.createEffect(SPEED_EFFECT_TIME, SPEED_EFFECT_LEVEL));

        hysteriaPassive.requestAddStackCount(1);
        boolean passiveCasted = hysteriaPassive.requestResolve();

        vitals.physicalDamage(signal.victim(), svc().self().player(), DAMAGE, KNOCKBACK);
        //★ 连打必需（组件侧，不动框架）：见 clearHitInterval 的说明
        clearHitInterval(signal.victim());

        if(passiveCasted){
            vitals.trueDamage(signal.victim(), svc().self().player(), EXTRA_TURE_DAMAGE_AMOUNT);
            svc().self().player().getWorld().playSound(svc().self().player().getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.5f);
        }
        else {
            svc().self().player().getWorld().playSound(svc().self().player().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1f);
        }



        startCooldown();
    }

    @Override
    protected boolean canUse() {
        return buff.canUseMainWeapon();
    }

    /**
     * **清掉目标的受击无敌帧**（0.1 秒连打必需；★ **组件侧实现，不动框架**）。
     *
     * <p><b>为什么需要</b>：{@code DamageUtil.dealtPhysicalDamage} 在扣血**前**会
     * {@code setNoDamageTicks(0)}（上游行为，本 PR 未改），这已保证"经由本插件伤害入口"
     * 的连续命中都打得中；但 {@code damage()} 结算后会把 {@code noDamageTicks} **设回 20 刻**
     * ⇒ 若这 20 刻内目标先被**不经本插件入口**的来源（纯原版近战等）打中，我们下一击仍会被吞。
     * 扣血之后**再清一次**即可免疫这种情形。
     *
     * <p>★ 口径申报：本工程**不**在框架侧做这件事（{@code core/util/DamageUtil} 保持上游原样），
     * 代价是"每个快速连打的调用点都要记得写一行"。目前只在本武器与另两个 0.1s/0.2s 主武器
     * （{@code MatinaMedicalShovelMainWeapon} / {@code TekTridentMainWeapon}）以及特克突进上调用。
     */
    private static void clearHitInterval(Player victim) {
        if (victim != null) {
            victim.setNoDamageTicks(0);
        }
    }
}
