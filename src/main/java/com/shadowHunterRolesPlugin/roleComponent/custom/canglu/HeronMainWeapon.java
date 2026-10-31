package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

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
public class HeronMainWeapon extends MainWeapon {

    public static final String ID = "cangluTraumaMainWeapon";

    //注：上游曾在此处加过一个 `COOLDOWN = 16` 常量并在 Specification 里使用它；
    //本地把它改成 `ATTACK_COOLDOWN_TICKS = 2`（需求：本武器 0.1 秒一击），
    //合并后那个常量已无任何引用 ⇒ 删除，避免与下面那个真正的攻击间隔常量混淆。
    private static final int DAMAGE = 10;
    private static final float KNOCKBACK = 0.5f;
    private static final int EXTRA_TURE_DAMAGE_AMOUNT = 5;
    private static final int SANTE_DAMAGE_MIN = 3;
    private static final int SANTE_DAMAGE_MAX = 7;
    private static final int ENERGY_THEFT_AMOUNT = 5;
    private static final int SPEED_EFFECT_LEVEL = 3;
    private static final int SPEED_EFFECT_TIME = 60;

    /** 攻击冷却 = 2 刻 = 0.1 秒。 */
    private static final int ATTACK_COOLDOWN_TICKS = 2;
    /** 上挑刀痕的分段数（每段三道平行痕）。 */
    private static final int UPSLASH_STEPS = 16;
    /** 刀痕从准星锚点往自己这侧让出的距离（贴到体表前侧，不插进身体；**只沿视线移动，不改横向**）。 */
    private static final double UPSLASH_FORWARD_OFFSET = 0.34d;
    /** 刀痕纵向高度（从目标脚上 0.12 起算，覆盖身高并略过顶）。 */
    private static final double UPSLASH_HEIGHT = 2.05d;
    /** 三道平行痕之间沿"屏幕右"的最大间距（= 加粗幅度；中段最宽、两端收尖）。 */
    private static final double UPSLASH_STRAND_GAP = 0.1d;

    /**
     * 武器**耐久条的刻度**：一件物品的耐久条画的是 {@code 剩余/上限}，所以把上限设成
     * {@value #DURABILITY_PER_STACK} × 16 + {@value #DURABILITY_HEADROOM}、把已损设成
     * {@code 1600 - 层数×100} ⇒ **剩下多少耐久就是多少层**（每层占满条 1/16 ≈ 6.25%）。
     * <p>留 {@value #DURABILITY_HEADROOM} 点余量是必须的：0 层时已损 = 1600 而不是上限，
     * 否则"0 层"= 耐久归零、下一次受耐久伤害就直接碎裂（玩家会丢武器）。
     * <p>★ 不能靠 {@code setUnbreakable(true)} 隐藏掉真实耐久 —— 1.13 起不毁物品**不画耐久条**，
     * 那就没有刻度可看了。
     */
    private static final int DURABILITY_PER_STACK = 100;
    private static final int DURABILITY_HEADROOM = 16;
    private static final int DURABILITY_MAX = 16 * DURABILITY_PER_STACK + DURABILITY_HEADROOM;
    /** 物品攻速修饰符的目标值（统一 100，见流程文档 §6.3）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;
    /** 攻速属性修饰符的命名键（先移除后添加 ⇒ 必须固定，否则去不掉旧的）。 */
    private static final NamespacedKey ATTACK_SPEED_KEY =
            KeyFactory.Registry.of("canglu_trauma_main_weapon_attack_speed");

    SanTEComponent sante;
    BuffComponent buff;
    EnergyComponent energy;
    VitalsComponent vitals;
    HeronTraumaPassive hysteriaPassive;
    HeronFateSkill fateSkill;
    HeronFloatingTextComponent voice;


    /**
     * **描述符口径的构造**：表现值由组件自己的 {@link Specification} 提供，
     * 本构造器只做"把描述符转交给基类"这一件事（主武器的能量消耗由类型恒为 0）。
     *
     * @param id
     * @param services
     * @param specification
     */
    public HeronMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends MainWeapon.Specification<HeronMainWeapon> {

        public Specification(){
            super(
                    Component.text("忧郁创痕-主武器"),
                    List.of(
                            //★ 备注必须与实际一致（2026-10-30 修）：
                            //  每次攻击 = 3~7 特殊值伤害（SANTE_DAMAGE_MIN..MAX，拥影阵营再 -1）＋夺取 5 能量；
                            //  4 层结算 = 额外 7 特殊值伤害（见 onAttack 的 passiveCasted 分支）。
                            Component.text("攻击造成3~7特殊值伤害并夺取5能量，增加移速；对拥影阵营额外-1特殊值并回复自身5能量。如果'创伤'层数不小于4，将结算4层创伤"),
                            Component.text("=============================="),
                            Component.text("被动:"),
                            Component.text("深度癔症: 普通攻击或左轮叠加1层「创伤」(湛蓝命运突进+2层)，第4层时普通攻击结算4层：额外5点灵魂伤害、7点特殊值伤害并回复自身4点生命；目标特殊值为0时再+5灵魂伤害(最多16层)")
                    ),
                    Material.DIAMOND_SWORD,
                    ATTACK_COOLDOWN_TICKS
            );
            requires(SanTEComponent.class);
            requires(EnergyComponent.class);
            requires(BuffComponent.class);
            requires(VitalsComponent.class);
            requires(HeronTraumaPassive.class);
        }

        @Override
        public HeronMainWeapon create(String id, ComponentServicesPort services){
            return new HeronMainWeapon(id, services, this);
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
        hysteriaPassive = svc().components().get(HeronTraumaPassive.class);
        fateSkill = svc().components().get(HeronFateSkill.class);
        voice = svc().components().get(HeronFloatingTextComponent.class);
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
        //★ 名字色 = 「创伤」档位色（0 灰 / 1 暗蓝 / 2 普通蓝 / 3 亮蓝 / 4 亮紫）。
        //  配色表唯一写在 HeronTraumaPassive（层数归属方），本处只取色 —— 避免两处各写一套。
        int tier = hysteriaPassive != null ? hysteriaPassive.tier() : 0;
        meta.displayName(net.kyori.adventure.text.Component.text("〓忧郁创伤〓")
                .color(net.kyori.adventure.text.format.TextColor.color(HeronTraumaPassive.tierColor(tier)))
                .decoration(net.kyori.adventure.text.format.TextDecoration.BOLD, true));
        applyAttackSpeed(meta);
        //★ 武器耐久 = 「创伤」层数刻度（耐久条 = 一张 16 格的量表，见上面常量注释）
        applyStackGauge(meta, hysteriaPassive != null ? hysteriaPassive.stackCount() : 0);
        //有档位（≥4 层）即带附魔光效，与"名字已变色"同步
        if(hysteriaPassive != null){
            meta.setEnchantmentGlintOverride(tier >= 1);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** 攻速统一 100：先移除再添加（不去重会叠加成 100/200/300）。 */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    /**
     * **耐久条 = 「创伤」层数量表**：16 层满条、0 层只剩一点，每层 1/16。
     * <p>读数口在物品的耐久条上（悬停 + F3+H 还能看到 {@code 剩余 Durability} 数字 = 层数×100）。
     * <p>{@code setUnbreakable(false)} 是必须的：不毁物品不画耐久条。
     */
    private static void applyStackGauge(ItemMeta meta, int stacks) {
        if (!(meta instanceof org.bukkit.inventory.meta.Damageable damageable)) {
            return;
        }
        damageable.setUnbreakable(false);
        damageable.setMaxDamage(DURABILITY_MAX);
        //0 层 = 只剩 HEADROOM 点；16 层 = 0 点已损（满条）
        damageable.setDamage(Math.max(0, DURABILITY_MAX - DURABILITY_HEADROOM - stacks * DURABILITY_PER_STACK));
    }

    @Override
    public void onAttack(AttackSignal signal){

        //★ 主武器必须自判冷却（派发侧不替主武器挡），否则 0.1s 只是纸面数字
        //  （合并上游时此处出现重复守卫，已去重；顺序 = 先冷却、再闸门）
        if(isCoolingDown()) return;
        if(!canUse()) return;

        UUID victimPid = signal.victim().getUniqueId();
        sante.decreaseSanTE(victimPid, SANTE_DAMAGE_MIN + java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(SANTE_DAMAGE_MAX - SANTE_DAMAGE_MIN + 1));

        int actualEnergyStealAmount = energy.decreaseEnergy(victimPid, ENERGY_THEFT_AMOUNT);
        energy.increase(actualEnergyStealAmount);
        //对 SHADOW：额外 -1 特殊值，并额外回自己 5 能量
        if(com.shadowHunterRolesPlugin.platform.FactionManager.factionOf(victimPid)
                == com.shadowHunterRolesPlugin.core.Faction.SHADOW){
            sante.decreaseSanTE(victimPid, 1);
            energy.increase(5);
        }

        buff.applyPotionEffect(PotionEffectType.SPEED.createEffect(SPEED_EFFECT_TIME, SPEED_EFFECT_LEVEL));

        hysteriaPassive.requestAddStackCount(1);
        boolean passiveCasted = hysteriaPassive.requestResolve();

        vitals.physicalDamage(signal.victim(), svc().self().player(), DAMAGE, KNOCKBACK);
        //上挑刀光：画在被打中的目标身上（不是自己脸前）
        upSlash(signal.victim());
        //湛蓝命运领域内：每次攻击额外 1 点真实伤害
        if(fateSkill != null && fateSkill.isInDomain(signal.victim())){
            vitals.trueDamage(signal.victim(), svc().self().player(), 1d);
        }
        //★ 连打必需（组件侧，不动框架）：见 clearHitInterval 的说明
        clearHitInterval(signal.victim());

        if(passiveCasted){
            vitals.trueDamage(signal.victim(), svc().self().player(), EXTRA_TURE_DAMAGE_AMOUNT);
            //强化普攻：额外 7 点特殊值伤害（打到目标身上）
            sante.decreaseSanTE(victimPid, 7);
            //目标正处于特殊值惩罚（TE ≤ 惩罚阈值，默认 0）⇒ 额外 5 点真实伤害。
            //★ 2026-10-30 起惩罚不再把 TE 钉在 0：惩罚期间 TE 可能为负，所以用 "≤ 阈值" 而不是 "== 0"
            if(sante.current() <= sante.punishmentThreshold()){
                vitals.trueDamage(signal.victim(), svc().self().player(), 5d);
            }
            vitals.heal(svc().self().player(), 4d);
            //独特命中音 + 蓝白复合特效
            Player self = svc().self().player();
            self.getWorld().playSound(self.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_HURT, 0.7f, 1.8f);
            for(int i = 0; i < 18; i++){
                double a = Math.toRadians(i * 20d);
                Location at = signal.victim().getLocation().add(0d, 0.9d, 0d)
                        .add(Math.cos(a) * 0.7d, Math.sin(a) * 0.5d, Math.sin(a) * 0.7d);
                self.getWorld().spawnParticle(org.bukkit.Particle.DUST, at, 1, 0d, 0d, 0d, 0d,
                        new org.bukkit.Particle.DustOptions(i % 2 == 0
                                ? org.bukkit.Color.fromRGB(120, 200, 255)
                                : org.bukkit.Color.fromRGB(245, 250, 255), 1.2f));
            }
            if(voice != null){ voice.sayMoment(self, HeronFloatingTextComponent.MOMENT_HIT); }
            svc().self().player().getWorld().playSound(svc().self().player().getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.7f, 1.5f);
        }
        else {
            svc().self().player().getWorld().playSound(svc().self().player().getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1f);
        }



        startCooldown();
    }

    /**
     * 上挑刀光：贴在被打中的敌人身上、从脚边扫过头顶，**横向严格压在准星轴上**。
     *
     * <h2>为什么这样摆（2026-10-26 第三次修）</h2>
     * 演进踩过的三个坑：
     * <ol>
     *   <li>「以视线为法线的平面 + {@code right·cosθ}」⇒ 弧身整体偏到准星**右下方**；</li>
     *   <li>「含准星轴的竖直平面」⇒ 与视线共面，正面看只有一条线；</li>
     *   <li>「目标脚下 + 朝攻击者偏移 + 横向弓起 {@code sin(πt)*0.42}」⇒ 贴在敌人身上了，但那个弓形把
     *       笔画中段横推 0.42 格，**对不齐准星**（本次报的问题）。</li>
     * </ol>
     * 现在把定位拆成两件互不干扰的事：
     * <ul>
     *   <li><b>横向</b>：锚点取"**准星轴上、目标所在深度**"的那一点
     *       （{@code eye + look·d}，{@code d} = 目标胸口在自己视线上的投影），再沿视线往自己这侧让
     *       {@value #UPSLASH_FORWARD_OFFSET} 格避免插进身体 —— 只沿视线移动，**横向零偏移**；</li>
     *   <li><b>纵向</b>：从目标脚上 0.12 到脚上 2.17（身高尺度），与横向解耦；</li>
     *   <li><b>形状</b>：宽度按 {@code sin(πt)} 收成"两端尖、中段宽"的刀痕，三道平行痕
     *       （±{@value #UPSLASH_STRAND_GAP}）**对称分布在准星轴两侧**，所以中心线永远压在准星上。</li>
     * </ul>
     * 粒子尺寸 1.35/1.15（= 加粗），扫击音在目标处播。
     */
    private void upSlash(Player victim){
        Player self = svc().self().player();
        if(self == null || victim == null){ return; }
        Location eye = self.getEyeLocation();
        Vector look = eye.getDirection().normalize();
        Vector right = look.clone().crossProduct(new Vector(0, 1, 0));
        if(right.lengthSquared() < 1e-6){ right = new Vector(1, 0, 0); }
        right.normalize();
        //准星轴上、目标胸口深度的那一点（横向 = 准星，唯一真值）
        Location chest = victim.getLocation().add(0d, 1.0d, 0d);
        double depth = Math.clamp(chest.toVector().subtract(eye.toVector()).dot(look), 0.6d, 8d);
        Location onRay = eye.clone().add(look.clone()
                .multiply(Math.max(0.2d, depth - UPSLASH_FORWARD_OFFSET)));
        double baseY = victim.getLocation().getY() + 0.12d;
        for(int i = 0; i <= UPSLASH_STEPS; i++){
            double t = i / (double) UPSLASH_STEPS;
            double y = baseY + UPSLASH_HEIGHT * t;
            //宽度剖面：两端收尖、中段最宽（刀痕感），且**对称于准星轴**
            double halfWidth = UPSLASH_STRAND_GAP * (0.25d + 0.75d * Math.sin(Math.PI * t));
            for(int strand = -1; strand <= 1; strand++){
                Location at = new Location(victim.getWorld(), onRay.getX(), y, onRay.getZ())
                        .add(right.clone().multiply(strand * halfWidth));
                victim.getWorld().spawnParticle(org.bukkit.Particle.DUST, at, 1, 0.02d, 0.02d, 0.02d, 0d,
                        new org.bukkit.Particle.DustOptions(i % 4 == 0
                                ? org.bukkit.Color.fromRGB(245, 250, 255)
                                : org.bukkit.Color.fromRGB(120, 200, 255),
                                strand == 0 ? 1.35f : 1.15f));
            }
        }
        self.getWorld().playSound(victim.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.2f);
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
