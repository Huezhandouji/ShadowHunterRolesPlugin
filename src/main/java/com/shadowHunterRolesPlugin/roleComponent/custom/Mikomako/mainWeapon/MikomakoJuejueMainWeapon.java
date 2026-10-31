package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.mainWeapon;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoChargeComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoMadness;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoSanDrainComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoThrowComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako.MikomakoVfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 「梅妻鹤子」主武器：**决爵**（模型：钻石锄）。
 *
 * <h2>需求逐条</h2>
 * <ul>
 *   <li>触及范围 <b>+1</b>（满充能再 <b>+1</b>）；攻速压到 100；</li>
 *   <li>每次命中：<b>8 物理 + 3~6 特殊值</b>，击退 <b>0.2</b>；普攻 CD <b>0.1 秒</b>；</li>
 *   <li>充能 ≥1 时：每次命中额外 <b>回复 4 生命</b> + 对触及范围内所有敌人 <b>3 真实伤害</b>；</li>
 *   <li>右键**蓄力 0.8 秒**（subtitle 显示"蓄力中..."、缓慢 I），松手取消，满 0.8 秒丢出武器；</li>
 *   <li>每次尝试普攻都在准星前划出红金半圆弧剑气 + 破空声；命中追加敲铁声与清脆音效；</li>
 *   <li>武器离手期间主武器不可用（图标转红屏障）。</li>
 * </ul>
 *
 * <h2>★★ 口径申报：蓄力用 {@code startUsingItem} 而不是 {@code isHandRaised()} 裸判</h2>
 * 工程教训（§8）：{@code isHandRaised()} 在"没真正开始使用物品"时不可靠。这里主动
 * {@code startUsingItem(HAND)} 把状态置起来，每 6 刻续一次（防原版把它清掉），松手即取消；
 * 前 2 刻不判松手（刚置位时的抖动窗口）。
 *
 * <h2>★★ 口径申报：触及范围走物品属性，不动玩家属性</h2>
 * 用 {@code ENTITY_INTERACTION_RANGE} 的**物品修饰符**：换下武器即自动失效，不需要在
 * {@code stop()} 里回滚玩家属性（少一处"忘了还原"的静默失效）。
 */
public class MikomakoJuejueMainWeapon extends MainWeapon {

    /** 本组件的登记 id。 */
    public static final String ID = "mikomako_mainWeapon_juejue";

    /** 基础物理伤害（需求：8）。 */
    public static final double BASE_PHYSICAL = 8d;

    /** 特殊值伤害区间（需求：3~6）。 */
    public static final int SANTE_MIN = 3;
    public static final int SANTE_MAX = 6;

    /** 击退强度（2026-10-31 用户裁定：0.2）。 */
    public static final double KNOCKBACK = 0.2d;

    /** 蓄力时长（刻）—— 需求：0.8 秒。 */
    public static final int CHARGE_TICKS = 16;

    /** 蓄力期间每几刻续一次"正在使用物品"。 */
    public static final int USE_REFRESH_TICKS = 6;

    /** 蓄力 subtitle 刷新间隔（刻）。 */
    public static final int SUBTITLE_INTERVAL_TICKS = 10;

    /** 充能攻击的回复量（需求：4）。 */
    public static final double CHARGED_HEAL = 4d;

    /** 充能攻击的范围真实伤害（需求：3）。 */
    public static final double CHARGED_TRUE_DAMAGE = 3d;

    /** 充能攻击的范围基准（格）—— 基础触及 3 + 1（武器）+ 满层 1。 */
    public static final double CHARGED_RADIUS_BASE = 4d;

    /** 30 档暴击概率。 */
    public static final double CRIT_CHANCE = 0.20d;

    /** 30 档暴击倍率。 */
    public static final double CRIT_MULTIPLIER = 1.5d;

    /** 40 档拉回概率。 */
    public static final double PULL_CHANCE = 0.15d;

    /** 触及范围修饰符键（★ 静态键：离线测试需装 KeyFactory 桩）。 */
    private static final NamespacedKey REACH_KEY = KeyFactory.Registry.of("mikomako_reach");

    /** 攻速修饰符键。 */
    private static final NamespacedKey ATTACK_SPEED_KEY = KeyFactory.Registry.of("mikomako_attack_speed");

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private FactionComponent faction;
    private SanTEComponent sante;
    private MikomakoChargeComponent charge;
    private MikomakoThrowComponent throwSystem;
    private MikomakoSanDrainComponent sanDrain;
    private MikomakoFloatingTextComponent voice;
    private HotbarRenderComponent render;

    private boolean charging;
    private int chargeTicks;
    private int lastSubtitleTick;
    private int phase;

    public MikomakoJuejueMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符：名称 / 图标 / 冷却（2 刻 = 0.1 秒）。 */
    public static final class Specification extends MainWeapon.Specification<MikomakoJuejueMainWeapon> {

        public Specification() {
            super(
                    Component.text("决爵"),
                    List.of(
                            Component.text("每次命中：8 物理 + 3~6 特殊值，击退 0.2"),
                            Component.text("每秒耗 5 能量充能（上限 3 层）"),
                            Component.text("充能攻击：回 4 生命 + 触及范围 3 真伤"),
                            Component.text("右键蓄力 0.8 秒丢出武器"),
                            Component.text("触及 +1，满充能再 +1")
                    ),
                    Material.DIAMOND_HOE,
                    2
            );
            requires(VitalsComponent.class).requires(EnergyComponent.class).requires(BuffComponent.class)
                    .requires(FactionComponent.class).requires(SanTEComponent.class)
                    .requires(MikomakoChargeComponent.class).requires(MikomakoThrowComponent.class)
                    .requires(MikomakoSanDrainComponent.class);
            requiresOptional(MikomakoFloatingTextComponent.class);
        }

        @Override
        public MikomakoJuejueMainWeapon create(String id, ComponentServicesPort services) {
            return new MikomakoJuejueMainWeapon(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = getComponent(VitalsComponent.class);
        energy = getComponent(EnergyComponent.class);
        buff = getComponent(BuffComponent.class);
        faction = getComponent(FactionComponent.class);
        sante = getComponent(SanTEComponent.class);
        charge = getComponent(MikomakoChargeComponent.class);
        throwSystem = getComponent(MikomakoThrowComponent.class);
        sanDrain = getComponent(MikomakoSanDrainComponent.class);
        voice = getComponent(MikomakoFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
    }

    // ───────── 物品：光效 / 触及 / 攻速 ─────────

    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean glint = charge != null && charge.hasCharge();
        meta.setEnchantmentGlintOverride(glint);
        meta.setUnbreakable(true);

        double reach = 1d + (charge == null ? 0 : charge.extraReach());
        meta.removeAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE);
        meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE, new AttributeModifier(
                REACH_KEY, reach, AttributeModifier.Operation.ADD_NUMBER));
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, 100d, AttributeModifier.Operation.ADD_NUMBER));

        stack.setItemMeta(meta);
        return stack;
    }

    // ───────── 攻击 ─────────

    @Override
    public void onAttack(AttackSignal signal) {
        Player attacker = svc().self().player();
        Player victim = signal.victim();
        if (attacker == null || victim == null) {
            return;
        }
        if (charging || throwSystem.isOut() || isCoolingDown() || !canUse()) {
            return;
        }
        if (!faction.isHostileTo(victim.getUniqueId())) {
            return;
        }

        int san = sante.current();
        boolean crit = MikomakoMadness.critChance(san) && ThreadLocalRandom.current().nextDouble() < CRIT_CHANCE;
        double multiplier = crit ? CRIT_MULTIPLIER : 1d;

        double physical = BASE_PHYSICAL + (MikomakoMadness.attackBonus(san) ? 2d : 0d);
        int special = SANTE_MIN + ThreadLocalRandom.current().nextInt(SANTE_MAX - SANTE_MIN + 1);
        if (MikomakoMadness.attackBonus(san)) {
            special += 1;
        }

        vitals.physicalDamage(victim, attacker, physical * multiplier, KNOCKBACK);
        sante.decreaseSanTE(victim.getUniqueId(), (int) Math.round(special * multiplier));

        // 2026-10-21 需求 4：每次命中敌人扣自己 3 点 SanTE（多目标只记一次）
        if (sanDrain != null) {
            sanDrain.onHit();
        }

        if (MikomakoMadness.pullChance(san)
                && ThreadLocalRandom.current().nextDouble() < PULL_CHANCE) {
            pullToward(attacker, victim);
        }

        if (charge.hasCharge()) {
            // 2026-10-21 需求：普攻**每命中消耗 1 层**充能（消耗成功才结算回血 + 范围真伤）；
            // 半径按"消耗前"的层数取，满层那一下仍然享受 +1 格
            double radius = CHARGED_RADIUS_BASE + charge.extraReach();
            if (charge.consume(1) > 0) {
                chargedStrike(attacker, san, radius);
            }
        }

        MikomakoVfx.slashArc(attacker, ThreadLocalRandom.current().nextInt(4));
        MikomakoVfx.hitMarker(victim, phase++);
        MikomakoSound.slash(attacker);
        MikomakoSound.hit(attacker);
        say(MikomakoFloatingTextComponent.MOMENT_HIT);
        startCooldown();
    }

    /** 充能攻击：回血 + 触及范围内 3 点真实伤害（{@code radius} 由调用方在消耗前算好）。 */
    private void chargedStrike(Player attacker, int san, double radius) {
        double heal = CHARGED_HEAL + MikomakoMadness.healBonus(san);
        vitals.heal(attacker, heal);

        Location center = attacker.getLocation();
        for (Player candidate : center.getNearbyPlayers(radius)) {
            if (candidate == null || candidate.equals(attacker)) {
                continue;
            }
            if (!faction.isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            vitals.trueDamage(candidate, attacker, CHARGED_TRUE_DAMAGE);
        }
        MikomakoSound.chargedHit(attacker);
        MikomakoVfx.fullChargePulse(attacker);
    }

    /** 40 档：把敌人向自己拉回 1 格。 */
    private void pullToward(Player attacker, Player victim) {
        Vector delta = attacker.getLocation().toVector().subtract(victim.getLocation().toVector());
        if (delta.lengthSquared() < 1e-4) {
            return;
        }
        victim.setVelocity(delta.normalize().multiply(0.62d).setY(0.18d));
    }

    // ───────── 施放：右键蓄力 / 左键空挥 ─────────

    @Override
    public void onCast(CastSignal signal) {
        if (signal == null || signal.trigger() == null) {
            return;
        }
        Player player = svc().self().player();
        if (player == null) {
            return;
        }

        if (signal.trigger() == CastTrigger.LEFT_CLICK) {
            if (charging || throwSystem.isOut() || isCoolingDown() || !canUse()) {
                return;
            }
            MikomakoVfx.slashArc(player, ThreadLocalRandom.current().nextInt(4));
            MikomakoSound.slash(player);
            startCooldown();
            return;
        }

        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;
        }
        beginCharge(player);
    }

    private void beginCharge(Player player) {
        if (charging || throwSystem.isOut() || isCoolingDown() || !canUse()) {
            return;
        }
        charging = true;
        chargeTicks = 0;
        lastSubtitleTick = 0;
        player.startUsingItem(EquipmentSlot.HAND);
        buff.applyPotionEffect(PotionEffectType.SLOWNESS, CHARGE_TICKS + 8, 0, true, false);
        MikomakoSound.chargeStart(player);
        showChargeSubtitle(player);
        say(MikomakoFloatingTextComponent.MOMENT_CHARGE);
    }

    @Override
    public void update() {
        if (!charging) {
            return;
        }
        Player player = svc().self().player();
        if (player == null || !player.isOnline() || player.isDead()) {
            charging = false;
            return;
        }

        chargeTicks++;
        // 松手取消：前 2 刻是 startUsingItem 的置位抖动窗口，不判
        if (chargeTicks > 2 && !player.isHandRaised()) {
            cancelCharge(player);
            return;
        }
        if (chargeTicks % USE_REFRESH_TICKS == 0 && chargeTicks < CHARGE_TICKS) {
            player.startUsingItem(EquipmentSlot.HAND);
        }
        if (chargeTicks - lastSubtitleTick >= SUBTITLE_INTERVAL_TICKS) {
            lastSubtitleTick = chargeTicks;
            showChargeSubtitle(player);
        }
        if (chargeTicks >= CHARGE_TICKS) {
            releaseThrow(player);
        }
        requestRepaint();
    }

    private void showChargeSubtitle(Player player) {
        player.showTitle(Title.title(
                Component.empty(),
                Component.text("蓄力中...", NamedTextColor.GOLD).decorate(TextDecoration.BOLD),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(600), Duration.ofMillis(120))));
    }

    private void cancelCharge(Player player) {
        charging = false;
        if (player != null && player.isOnline()) {
            player.clearActiveItem();
        }
        requestRepaint();
    }

    private void releaseThrow(Player player) {
        charging = false;
        player.clearActiveItem();
        if (throwSystem.throwWeapon(player)) {
            MikomakoSound.throwWeapon(player);
            say(MikomakoFloatingTextComponent.MOMENT_THROW);
            startCooldown();
        }
        requestRepaint();
    }

    // ───────── 闸门 ─────────

    @Override
    protected boolean canUse() {
        return buff != null && buff.canUseMainWeapon() && throwSystem != null && !throwSystem.isOut();
    }

    /** 语音（可选组件；缺失则静默）。 */
    private void say(String moment) {
        if (voice == null) {
            return;
        }
        Player player = svc().self().player();
        if (player == null) {
            return;
        }
        voice.sayMoment(player, moment);
    }

    private void requestRepaint() {
        if (render != null) {
            render.markDirty();
        }
    }
}
