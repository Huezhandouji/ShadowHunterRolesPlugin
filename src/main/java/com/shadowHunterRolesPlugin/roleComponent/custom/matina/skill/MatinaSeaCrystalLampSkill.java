package com.shadowHunterRolesPlugin.roleComponent.custom.matina.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.MatinaRageVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaKuangPassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.matina.passive.MatinaFloatingTextComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * 「狂躁牧师·马提娜」技能之一：**牧师本命 · 海晶灯**（图标 {@code SEA_LANTERN}）。
 *
 * <h2>行为（需求逐条）</h2>
 * <ul>
 *   <li><b>立刻</b>洒出药物，对 <b>r = {@value #EFFECT_RADIUS}</b> 的<b>同阵营</b>角色回复
 *       <b>{@value #ALLY_HEAL}</b> 生命；</li>
 *   <li>被治疗对象身上：<b>爱心粒子</b> + <b>骨粉催熟特效</b>（原版 {@code HAPPY_VILLAGER}），
 *       并播放<b>升级音效</b>（{@code ENTITY_PLAYER_LEVELUP}）；</li>
 *   <li>对<b>不同阵营</b>角色：<b>3 秒</b>缓慢 II + 凋零 II + 虚弱 II，身上冒
 *       <b>村民愤怒粒子</b>与<b>旋转上升的紫色粒子</b>，并播放<b>僵尸村民转变音效（2 倍速）</b>；</li>
 *   <li><b>每有一名角色接受治疗</b>，增加 <b>1 点狂暴值</b>；</li>
 *   <li><b>CD {@value #COOLDOWN_TICKS} 刻</b>（6 秒）、<b>能量消耗 {@value #ENERGY_COST}</b>。</li>
 * </ul>
 *
 * <h2>口径申报</h2>
 * <ol>
 *   <li><b>本技能没有第二个半径参数</b>：需求只给了"r = 8"。敌对光晕与治疗<b>共用
 *       r = 8</b>（这就是"洒出去的药物"的同一个范围），若日后要拆成两个半径，改
 *       {@link #ENEMY_RADIUS} 一处即可。</li>
 *   <li><b>"同阵营"的判据</b> = {@code !getComponent(FactionComponent.class).isHostileTo(uuid)}（未选角色的玩家按平台口径算敌对；★ 2026-10-07 上游把阵营搬进组件后，旧口 {@code roleInfo()} 已不再含阵营）。</li>
 *   <li><b>音效的"2 倍速"</b> = 原版 {@code ENTITY_ZOMBIE_VILLAGER_CONVERTED} 以
 *       {@code pitch 2.0} 播放（音高翻倍即 2 倍速的听感）。</li>
 *   <li><b>持续型药水效果的时长</b>：{@code 3 秒 = 60 刻}，按"能盖住 3 秒"给
 *       {@value #DEBUFF_DURATION_TICKS} 刻（多给 5 刻的冗余，避免刚好卡在最后一刻被判定过期）。</li>
 *   <li><b>物品栏的附魔光效</b>：<b>冷却已走完、只是能量不足</b>时，结构空位上也加附魔光效
 *       （见 {@link #buildItem()}）⇒ "冷却好了只是没能量"与"还在冷却"一眼可分。
 *       被眩晕 / 沉默（红屏障）时不叠光效 —— 那是优先级更高的"现在使不了"。</li>
 * </ol>
 */
public class MatinaSeaCrystalLampSkill extends Skill {

    /** **本组件的登记 id**（★ 知识归属：组件自己）。 */
    public static final String ID = "matina_skill_seaCrystalLamp";

    /** 洒药范围（格）：治疗与敌意效果共用。 */
    public static final double EFFECT_RADIUS = 8.0d;

    /** 敌对光晕范围（格）：需求未给第二半径 ⇒ 与治疗同半径。 */
    public static final double ENEMY_RADIUS = EFFECT_RADIUS;

    /** 对同阵营回复的生命。 */
    private static final double ALLY_HEAL = 10.0d;

    /** 敌方减益的等级：II（增幅 1）。 */
    private static final int DEBUFF_AMPLIFIER = 1;

    /** 敌方减益时长（单位：秒）—— 需求原话 3 秒。 */
    private static final int DEBUFF_SECONDS = 3;

    /** 敌方减益时长（刻；含 5 刻冗余）。 */
    private static final int DEBUFF_DURATION_TICKS = DEBUFF_SECONDS * 20 + 5;

    /** 冷却：6 秒 = 120 刻。 */
    private static final int COOLDOWN_TICKS = 120;

    /** 能量消耗：15。 */
    private static final int ENERGY_COST = 15;

    private VitalsComponent vitals;
    private EnergyComponent energy;
    private BuffComponent buff;
    private MatinaKuangPassive kuang;
    /** 施法台词（砸地风格）；装配期声明依赖 ⇒ 这里直接取。 */
    private MatinaFloatingTextComponent floatingText;

    public MatinaSeaCrystalLampSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**（栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends Skill.Specification<MatinaSeaCrystalLampSkill> {

        public Specification() {
            super(Component.text("牧师本命·海晶灯"),
                    List.of(Component.text("立刻洒出药物：8 格内同阵营角色回复 10 点生命"),
                            Component.text("不同阵营角色获得 3 秒缓慢 II、凋零 II 与虚弱 II"),
                            Component.text("每治疗一名角色增加 1 点狂暴值")),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.SEA_LANTERN);
            requires(VitalsComponent.class).requires(EnergyComponent.class)
                    .requires(BuffComponent.class).requires(MatinaKuangPassive.class)
                    .requires(MatinaFloatingTextComponent.class).requires(FactionComponent.class);
        }

        @Override
        public MatinaSeaCrystalLampSkill create(String id, ComponentServicesPort services) {
            return new MatinaSeaCrystalLampSkill(id, services, this);
        }
    }

    /** **开始生效**：协作组件一次查好缓存进字段（依赖只在 {@code start()} 取）。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        kuang = svc().components().get(MatinaKuangPassive.class);
        floatingText = svc().components().get(MatinaFloatingTextComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();
        if (caster == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        //能量门槛（声明耗能由 currentEnergy() 读出；不足 ⇒ 不施放、不进冷却）
        if (energy != null && !energy.tryConsume(ENERGY_COST)) {
            return;
        }

        // ★ 施法台词：一次吐 1~3 句（砸地风格 + 泛光白 + 颤抖），间隔约 0.35 秒
        if (floatingText != null) {
            floatingText.onCast(caster);
        }

        World world = caster.getWorld();
        Location center = caster.getLocation().clone();
        if (world == null) {
            return;
        }

        //① 施放本身的场面：药光 + 海晶灯的音效
        MatinaRageVfx.dustRing(world, center.clone().add(0d, 0.2d, 0d), EFFECT_RADIUS,
                64, 0d, MatinaRageVfx.HOLY_WHITE, 0.9f);
        MatinaRageVfx.boneMeal(world, center.clone().add(0d, 1.2d, 0d));
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.6f);

        int healedCount = 0;

        //② 范围内逐个判定阵营：同阵营 ⇒ 治疗；不同阵营 ⇒ 三重减益
        for (Player candidate : center.getNearbyPlayers(EFFECT_RADIUS)) {
            if (candidate == null || candidate.equals(caster) || !isAlive(candidate)) {
                continue;
            }
            if (svc().components().get(FactionComponent.class).isHostileTo(candidate.getUniqueId())) {
                afflict(world, candidate);
            } else {
                if (heal(world, candidate)) {
                    healedCount++;
                }
            }
        }

        //③「每有一名角色接受治疗，增加 1 点狂暴值」
        if (kuang != null && healedCount > 0) {
            kuang.addKuang(healedCount);
        }

        //④ 冷却由本组件在施放成功处按声明值启动（组件自持冷却）
        startCooldown();
    }

    /**
     * **【同阵营形态】治疗一名同阵营角色**：回血 + 爱心 + 骨粉 + 升级音效；返回是否真的结算了治疗。
     * <p>★ **本形态只给加成、不给任何负面效果**（需求：对同阵营的只会给与加成而不会给予负面效果）
     * —— 负面效果一律走 {@link #afflict(World, Player)}，两条分支互斥。
     */
    private boolean heal(World world, Player ally) {
        if (vitals == null) {
            return false;
        }
        vitals.heal(ally, ALLY_HEAL);
        Location base = ally.getLocation().clone();
        MatinaRageVfx.hearts(world, base.clone().add(0d, 2.2d, 0d), 4, 0.4d);
        MatinaRageVfx.boneMeal(world, base.clone().add(0d, 1.0d, 0d));
        world.playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        return true;
    }

    /**
     * **【敌方形态】对一名不同阵营角色施加三重减益**（缓慢 II / 凋零 II / 虚弱 II，各 3 秒）
     * + 两条敌方特效。
     *
     * <p>★ **本形态只给负面、不给任何增益**（需求：对敌人只会给与负面效果而没有增益）。
     *
     * <p><b>★ 修复记录 —— 减益曾经全部落在施法者自己身上</b>：
     * 旧实现走 {@code buff.applyPotionEffect(...)}，而 {@link BuffComponent} 的
     * {@code applyPotionEffect} 是**只作用于自己**的口（内部写死
     * {@code svc().self().player().addPotionEffect(...)}，**没有目标重载**）⇒
     * 这三条减益实际加在**马提娜自己**身上，敌人一点都没吃到。
     * 表现正是"对同阵营的（自己）被给了负面效果、对敌人的却没有负面效果"。
     * <p>对**他人**施药的既有写法 = 直接 {@code target.addPotionEffect(...)}
     * （先例：{@code RedEvilShockSkill} / {@code SinThornEntangleSkill}）—— 本方法照此改。
     * 另：不经 {@code buff} 也就**不会**把敌人的减益记进**自己**的药水账本
     * （{@code clearAppliedPotionEffects} 只会清自己身上的），语义更干净。
     */
    private void afflict(World world, Player enemy) {
        //★ 落在【敌人】身上：直接用目标自身的 addPotionEffect（不能用 buff 那个"只作用自己"的口）
        enemy.addPotionEffect(PotionEffectType.SLOWNESS.createEffect(DEBUFF_DURATION_TICKS, DEBUFF_AMPLIFIER));
        enemy.addPotionEffect(PotionEffectType.WITHER.createEffect(DEBUFF_DURATION_TICKS, DEBUFF_AMPLIFIER));
        enemy.addPotionEffect(PotionEffectType.WEAKNESS.createEffect(DEBUFF_DURATION_TICKS, DEBUFF_AMPLIFIER));

        Location base = enemy.getLocation().clone();
        //村民愤怒粒子 + 旋转上升的紫色粒子
        MatinaRageVfx.villagerAngry(world, base.clone().add(0d, 1.8d, 0d));
        MatinaRageVfx.purpleRising(world, base.clone().add(0d, 0.2d, 0d), 2.2d, 0d, 14);
        //僵尸村民转变音效（2 倍速）
        world.playSound(base, Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 2f);
    }

    /** 存活判定（死亡 / 死亡界面 / 已下线一律排除）。 */
    private static boolean isAlive(Player player) {
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d;
    }

    /** **闸门放行？**（基类不查容器 ⇒ 用本组件自己的字段判）。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /** **当前能量**：本组件参与能量维度（声明耗能 15）⇒ 读能量组件的真值。 */
    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /**
     * **技能物品**：基类三态画法 + "冷却已走完只是缺条件"的提示。
     *
     * <p>需求（本轮）：<b>满足 CD 条件而其它条件不满足时，结构空位也加上附魔光效</b>。
     *
     * <p>本技能的"其它条件"只有两条：
     * <ul>
     *   <li><b>能量不足</b> ⇒ 基类画成 {@code STRUCTURE_VOID} + 灰字 {@code ENERGY LACK}。
     *       此时<b>加附魔光效</b> ⇒ "冷却好了、只是没能量"与"还在冷却"一眼可分；</li>
     *   <li><b>被眩晕 / 沉默</b>（{@code DISABLED}）⇒ 基类画成红屏障。这是"现在使不了"的更高优先级
     *       状态，<b>保持屏障、不叠光效</b>。</li>
     * </ul>
     *
     * <p>★ 覆写口径照仓库既有先例（{@code MatinaRedstoneDroneSkill#buildItem} /
     * {@code CangluBlueIceRevolverSkill#buildItem}）：先 {@code super.buildItem()} 拿基类成品、
     * 再在其上后处理 ⇒ 识别键 / 名称 / lore 全部保留。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        //冷却已走完、闸门放行（未被眩晕/沉默），但能量不足 ⇒ 结构空位也发光
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
                stack.setItemMeta(meta);
            }
        }
        return stack;
    }
}
