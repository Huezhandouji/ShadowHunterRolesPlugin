package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「红」主武器：**纯白之刺**（铁剑）。
 *
 * <ul>
 *   <li>左键 = **前方锥形穿刺**：触及范围内所有敌人，各 8 物理 + 3~6 特殊值，并挂 5 层流血；</li>
 *   <li>CD **0.3 秒**（6 刻）；</li>
 *   <li>声音：起手僵尸破门；命中玻璃碎裂 + 敲铁；</li>
 *   <li>特效：面前画圆 → 四分之一处四根短柱 → 圆心竖起主穿刺柱，柱身缠绕螺旋；命中喷血。</li>
 * </ul>
 *
 * <p>武器在「贯通新生」施放期间被禁用（读该技能的 {@code isCasting()}）；大招期间 +3 伤害。
 */
public class RedSanctifiedBladeMainWeapon extends MainWeapon {

    /** 本组件的登记 id。 */
    public static final String ID = "red_sanctified_blade_main_weapon";

    public static final double PHYSICAL = 8d;
    public static final int SANTE_MIN = 3;
    public static final int SANTE_MAX = 6;
    public static final int BLEED_STACKS = 5;
    public static final double RANGE = 4.2d;
    public static final double CONE_DEGREES = 40d;
    public static final int COOLDOWN_TICKS = 6;
    public static final double ULT_DAMAGE_BONUS = 3d;

    /** 攻速修饰符键（+100 ⇒ 无攻击冷却）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY = KeyFactory.Registry.of("red_attack_speed");

    private VitalsComponent vitals;
    private BuffComponent buff;
    private FactionComponent faction;
    private SanTEComponent sante;
    private RedBleedPassive bleed;
    private RedDeeplySorrowSkill thrust;
    private RedSolitaryArroganceSkill ult;
    private RedFloatingTextComponent voice;

    public RedSanctifiedBladeMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends MainWeapon.Specification<RedSanctifiedBladeMainWeapon> {

        public Specification() {
            super(
                    Component.text("纯白之刺"),
                    List.of(
                            Component.text("左键：前方锥形穿刺（8 物理 + 3~6 特殊值）"),
                            Component.text("每次命中挂 5 层流血"),
                            Component.text("CD 0.3 秒"),
                            Component.text("贯通新生施放期间禁用")
                    ),
                    Material.IRON_SWORD,
                    COOLDOWN_TICKS
            );
            requires(VitalsComponent.class).requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(SanTEComponent.class).requires(RedBleedPassive.class);
            requiresOptional(RedDeeplySorrowSkill.class);
            requiresOptional(RedSolitaryArroganceSkill.class);
            requiresOptional(RedFloatingTextComponent.class);
        }

        @Override
        public RedSanctifiedBladeMainWeapon create(String id, ComponentServicesPort services) {
            return new RedSanctifiedBladeMainWeapon(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        buff = getComponent(BuffComponent.class);
        faction = getComponent(FactionComponent.class);
        sante = getComponent(SanTEComponent.class);
        bleed = getComponent(RedBleedPassive.class);
        thrust = getComponent(RedDeeplySorrowSkill.class);
        ult = getComponent(RedSolitaryArroganceSkill.class);
        voice = getComponent(RedFloatingTextComponent.class);
    }

    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setUnbreakable(true);
            // 需求 4：主武器常驻附魔光效
            meta.setEnchantmentGlintOverride(true);
            // 需求 1：攻击速度压到 100（先移除再添加，避免重绘叠加）
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    ATTACK_SPEED_KEY, 100d, AttributeModifier.Operation.ADD_NUMBER));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public void onAttack(AttackSignal signal) {
        Player victim = signal.victim();
        if (victim == null) {
            return;
        }
        if (!faction.isHostileTo(victim.getUniqueId())) {
            return;
        }
        pierce();
    }

    @Override
    public void onCast(CastSignal signal) {
        if (signal != null && signal.trigger() == CastTrigger.LEFT_CLICK) {
            pierce();
        }
    }

    /** 前方锥形穿刺。 */
    private void pierce() {
        Player attacker = svc().self().player();
        if (attacker == null || isCoolingDown() || !canUse()) {
            return;
        }

        Vector look = attacker.getEyeLocation().getDirection().setY(0d);
        if (look.lengthSquared() < 1e-6) {
            look = attacker.getLocation().getDirection().setY(0d);
        }
        look.normalize();

        List<Player> victims = new ArrayList<>();
        for (Player candidate : attacker.getLocation().getNearbyPlayers(RANGE)) {
            if (candidate == null || candidate.equals(attacker) || !faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            Vector delta = candidate.getLocation().toVector().subtract(attacker.getLocation().toVector()).setY(0d);
            if (delta.lengthSquared() < 1e-6) {
                continue;
            }
            double angle = Math.toDegrees(Math.acos(Math.clamp(look.dot(delta.normalize()), -1d, 1d)));
            if (angle <= CONE_DEGREES) {
                victims.add(candidate);
            }
        }

        double bonus = ult != null && ult.isActive() ? ULT_DAMAGE_BONUS : 0d;
        for (Player victim : victims) {
            vitals.physicalDamage(victim, attacker, PHYSICAL + bonus);
            int special = SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1);
            sante.decreaseSanTE(victim.getUniqueId(), (int) Math.round(special + bonus));
            bleed.applyStacks(victim.getUniqueId(), BLEED_STACKS);
            RedFx.bloodSplat(victim, 12);
        }

        RedFx.pierceCast(attacker, RANGE, false);
        if (!victims.isEmpty()) {
            RedFx.pierceHit(attacker);
            bleed.onPlayerHit(attacker);
            if (voice != null) {
                voice.sayMoment(attacker, RedFloatingTextComponent.MOMENT_HIT);
            }
        }

        startCooldown();
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canUseMainWeapon()
                && (thrust == null || !thrust.isCasting());
    }
}
