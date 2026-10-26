package com.shadowHunterRolesPlugin.roleComponent.custom.resist;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.AttackSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.MainWeapon;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 「抗争」主武器：**斗争**（完整名 = {@code ▓抗争▓}，铁剑，亮紫色加粗）。
 *
 * <h2>左键命中</h2>
 * <ol>
 *   <li>主目标**击退 3**（走 {@code DamageUtil} 的击退重载，强度 3）；</li>
 *   <li>随机一种剑气（月牙 / 爆裂 / 螺旋），白灰为主、紫色点缀；</li>
 *   <li>TNT 爆炸音效；</li>
 *   <li>以**目标为心 r=5** 的所有敌人：10 点物理 + 3~6 点特殊值（TE）伤害；</li>
 *   <li>自身战斗时间 +1 秒；</li>
 *   <li>攻击 CD **0.6 秒**（{@value #ATTACK_COOLDOWN_TICKS} 刻，本组件自持闸门）。</li>
 * </ol>
 *
 * <p>★ 攻击入口由框架 listener 接管（原版伤害已被取消）⇒ 本组件必须自己出伤、
 * 自己判 CD；{@code canUse()} 还要看「命运的征服」是否正在施法锁里。
 */
public class ResistMainWeapon extends MainWeapon {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_main_weapon";

    /** 武器完整名（需求：亮紫色、加粗、▓抗争▓）。 */
    public static final String WEAPON_NAME = "▓抗争▓";

    /** 攻击 CD（刻）——需求 0.6 秒。 */
    public static final int ATTACK_COOLDOWN_TICKS = 12;

    /** 范围伤害半径（需求 r=5）。 */
    public static final double AOE_RADIUS = 5.0d;

    /** 主目标击退强度（需求：击退 3）。 */
    public static final double KNOCKBACK_STRENGTH = 3.0d;

    /** 三种剑气的下标（需求：差异明显）。 */
    public static final int VARIANT_CRESCENT = 0;   //月牙横斩
    public static final int VARIANT_LANCE = 1;      //爆裂直刺
    public static final int VARIANT_SLAM = 2;       //裂地砸落（唯一触发巨剑砸落）

    /** 剑气种类数。 */
    public static final int VARIANT_COUNT = 3;

    /** 攻速统一 100（工程口径；先摘后加）。 */
    private static final double ATTACK_SPEED_VALUE = 100d;
    private static final NamespacedKey ATTACK_SPEED_KEY = KeyFactory.Registry.of("resist_attack_speed");

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private FactionComponent faction;
    private ResistStrugglePassive passive;
    private ResistFloatingTextComponent voice;
    private HotbarRenderComponent render;

    private final Random random = new Random();

    /** 下一次可攻击的刻（自持闸门）。 */
    private int nextAttackTick;

    /** 正在播放的"巨剑砸落"动画（世界实体 ⇒ stop 必须全部回收）。 */
    private final List<ResistSwordSlam> slams = new ArrayList<>();

    public ResistMainWeapon(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 主武器描述符。 */
    public static final class Specification extends MainWeapon.Specification<ResistMainWeapon> {

        public Specification() {
            super(Component.text(WEAPON_NAME, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                    List.of(
                            Component.text("左键：击退 3，随机释放「月牙横斩 / 爆裂直刺 / 裂地砸落」三种剑气（TNT 音效）"),
                            Component.text("裂地砸落额外附带巨剑竖直砸落；以目标为心 5 格内所有敌人：10 物理 + 3~6 特殊值"),
                            Component.text("每次命中使战斗时间 +1 秒；攻击 CD 0.6 秒"),
                            Component.text("-----宁作死亡的武器")
                    ),
                    Material.IRON_SWORD, ATTACK_COOLDOWN_TICKS);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(ResistStrugglePassive.class);
        }

        @Override
        public ResistMainWeapon create(String id, ComponentServicesPort services) {
            return new ResistMainWeapon(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        passive = svc().components().get(ResistStrugglePassive.class);
        voice = getComponent(ResistFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        nextAttackTick = 0;
        slams.clear();
    }

    @Override
    public void stop() {
        for (ResistSwordSlam slam : slams) {
            slam.remove();
        }
        slams.clear();
        vitals = null;
        sante = null;
        buff = null;
        faction = null;
        passive = null;
        voice = null;
        render = null;
    }

    /** 被眩晕 / 施法锁住时不可用。 */
    @Override
    protected boolean canUse() {
        Player owner = svc().self().player();
        if (owner == null) {
            return false;
        }
        if (ResistState.isLocked(owner, Bukkit.getCurrentTick())) {
            return false;
        }
        return buff == null || buff.canUseMainWeapon();
    }

    // ───────── 攻击 ─────────

    @Override
    public void onAttack(AttackSignal signal) {
        Player owner = svc().self().player();
        Player victim = signal == null ? null : signal.victim();
        if (owner == null || victim == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (now < nextAttackTick) {
            return;     //0.6 秒自持闸门（框架不挡冷却 ⇒ 必须在这里判）
        }
        if (!canUse()) {
            return;
        }
        World world = victim.getWorld();
        if (world == null) {
            return;
        }
        nextAttackTick = now + ATTACK_COOLDOWN_TICKS;
        //★ 2026-10-18 需求 4：同步走框架冷却，热键栏才会渲染出"CD 结构空位"（STRUCTURE_VOID）
        startCooldown(ATTACK_COOLDOWN_TICKS);

        //① 主目标：物理 + 击退 3 + 特殊值
        double physical = passive != null ? passive.swordQiPhysical() : ResistStrugglePassive.SWORD_QI_PHYSICAL;
        int variant = random.nextInt(3);
        if (vitals != null) {
            vitals.physicalDamage(victim, owner, physical, KNOCKBACK_STRENGTH);
        }
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(), rollSpecial());
        }
        victim.setNoDamageTicks(0);

        //② 剑气（从眼睛到目标身体中心，三种形态随机）
        ResistVfx.swordQi(world, owner.getEyeLocation(),
                victim.getLocation().clone().add(0d, 1d, 0d), variant);
        ResistVfx.swordQiImpact(world, victim.getLocation(), variant);
        //只留一圈白色范围提示（不再沿边缘铺爆炸）
        ResistVfx.aoeShockwave(world, victim.getLocation(), AOE_RADIUS);
        //需求 9：只有「裂地砸落」剑气才配巨剑砸落动画（竖直向下砸）
        if (variant == VARIANT_SLAM) {
            ResistSwordSlam slam = ResistSwordSlam.spawn(world, victim.getLocation(), 6.0d);
            if (slam != null) {
                slams.add(slam);
            }
        }
        ResistSound.mainHit(world, victim.getLocation());

        //③ 目标周围 r5：所有敌人 10 物理 + 3~6 特殊值
        for (Player other : world.getNearbyPlayers(victim.getLocation(), AOE_RADIUS)) {
            if (other == null || other.equals(owner) || other.equals(victim) || !isAlive(other) || !isHostile(other)) {
                continue;
            }
            if (vitals != null) {
                vitals.physicalDamage(other, owner, physical);
            }
            if (sante != null) {
                sante.decreaseSanTE(other.getUniqueId(), rollSpecial());
            }
            other.setNoDamageTicks(0);
            //★ r5 溅射用轻量版：一人一颗单爆炸，不打环不闪白（否则一圈人一起炸会很糊）
            ResistVfx.swordQiSplash(world, other.getLocation());
            ResistSound.swordQiSplash(world, other.getLocation());
        }

        //④ 战斗时间 +1 秒 / 语音 / 重绘
        if (passive != null) {
            passive.addBattleSeconds(owner, 1, now);
        }
        if (voice != null) {
            voice.saySpecial(owner, ResistFloatingTextComponent.MOMENT_HIT);
        }
        if (render != null) {
            render.requestRepaint();
        }
    }

    private int rollSpecial() {
        return passive != null ? passive.rollSpecial()
                : ResistStrugglePassive.SWORD_QI_SPECIAL_MIN
                + random.nextInt(ResistStrugglePassive.SWORD_QI_SPECIAL_MAX
                - ResistStrugglePassive.SWORD_QI_SPECIAL_MIN + 1);
    }

    private boolean isHostile(Player other) {
        return faction != null && faction.isHostileTo(other.getUniqueId());
    }

    private static boolean isAlive(Player player) {
        //创造 / 旁观不参与索敌（否则击退/击落会作用到不该作用的玩家）
        return player.isOnline() && !player.isDead() && player.getHealth() > 0d
                && player.getGameMode() != org.bukkit.GameMode.CREATIVE
                && player.getGameMode() != org.bukkit.GameMode.SPECTATOR;
    }

    // ───────── 外观：亮紫加粗 + 攻速 100 ─────────

    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        //需求：完整名字永远是 ▓抗争▓（亮紫、加粗）—— 覆盖基类按状态改的绿/红/灰
        meta.displayName(Component.text(WEAPON_NAME, NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        applyAttackSpeed(meta);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 攻速统一压到 100：先摘后加（{@code buildItem()} 会被反复调用）。 */
    private static void applyAttackSpeed(ItemMeta meta) {
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                ATTACK_SPEED_KEY, ATTACK_SPEED_VALUE, AttributeModifier.Operation.ADD_NUMBER));
    }

    // ───────── 每刻：推进巨剑砸落动画 ─────────

    @Override
    public void update() {
        if (slams.isEmpty()) {
            return;
        }
        Iterator<ResistSwordSlam> iterator = slams.iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().tick()) {
                iterator.remove();
            }
        }
    }

    // ───────── 读口（单测 / 诊断） ─────────

    /** 下一次可攻击的刻。 */
    public int nextAttackTick() {
        return nextAttackTick;
    }
}
