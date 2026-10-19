package com.shadowHunterRolesPlugin.roleComponent.custom.resist.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistFloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistState;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistStrugglePassive;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistSwordModel;
import com.shadowHunterRolesPlugin.roleComponent.custom.resist.ResistVfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * 「抗争」三技能：**被欺压者的咆哮**（重生锚图标，CD 40 秒）。
 *
 * <ol>
 *   <li>引导 {@value #CHANNEL_TICKS} 刻（0.8 秒）拔出 4 把巨剑；</li>
 *   <li>向前突进最多 {@value #DASH_DISTANCE} 格，速度 {@value #DASH_STEP} 格/刻（25 格/秒），
 *       撞到方块立即停止；</li>
 *   <li>突进途中 r={@value #PATH_RADIUS} 的敌人：{@value #PATH_PHYSICAL} 物理 + 4~6 特殊值，
 *       每人**每段突进只命中一次**；</li>
 *   <li>落点 r={@value #LAND_RADIUS} 的敌人：{@value #LAND_PHYSICAL} 物理 + 5~10 特殊值
 *       （与途中命中**可以叠加**）；</li>
 *   <li>每次命中 +3 秒战斗时间。</li>
 * </ol>
 */
public class ResistRampageSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "resist_rampage_skill";

    /** 冷却（刻）——需求 40 秒。 */
    public static final int COOLDOWN_TICKS = 800;

    /** 引导 0.8 秒。 */
    public static final int CHANNEL_TICKS = 16;

    /** 突进速度：25 格/秒 = 1.25 格/刻。 */
    public static final double DASH_STEP = 1.25d;

    /** 最大突进距离（30 格）。 */
    public static final double DASH_DISTANCE = 30d;

    /** 突进途中命中半径（r=5）。 */
    public static final double PATH_RADIUS = 5.0d;

    /** 落点半径（r=10）。 */
    public static final double LAND_RADIUS = 10.0d;

    /** 途中：8 物理 + 4~6 特殊值。 */
    public static final double PATH_PHYSICAL = 8d;
    public static final int PATH_SPECIAL_MIN = 4;
    public static final int PATH_SPECIAL_MAX = 6;

    /** 落点：10 物理 + 5~10 特殊值。 */
    public static final double LAND_PHYSICAL = 10d;
    public static final int LAND_SPECIAL_MIN = 5;
    public static final int LAND_SPECIAL_MAX = 10;

    /** 每次命中加时（秒）。 */
    public static final int BATTLE_ADD = 3;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private FactionComponent faction;
    private ResistStrugglePassive passive;
    private ResistFloatingTextComponent voice;
    private HotbarRenderComponent render;

    private final Random random = new Random();
    private final Set<UUID> dashHits = new HashSet<>();

    private int phase;          //0 空闲 / 1 引导 / 2 突进 / 3 落点收尾
    private int channelTick;
    private int aftermathTimer;
    private double travelled;
    private Vector direction = new Vector(0d, 0d, 1d);
    private ResistSwordModel channelSwords;
    private ResistSwordModel landingSwords;

    public ResistRampageSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 技能描述符。 */
    public static final class Specification extends Skill.Specification<ResistRampageSkill> {

        public Specification() {
            super(Component.text("被欺压者的咆哮"),
                    List.of(
                            Component.text("引导 0.8 秒，从地底拔出 4 把巨剑"),
                            Component.text("向前突进最多 30 格（25 格/秒），撞到方块立即停止"),
                            Component.text("途中 5 格内敌人：8 物理 + 4~6 特殊值（每人只命中一次）"),
                            Component.text("落点 10 格内敌人：10 物理 + 5~10 特殊值（可与途中叠加）"),
                            Component.text("每次命中 +3 秒战斗时间；冷却 40 秒")
                    ),
                    800, 0, Material.RESPAWN_ANCHOR);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(BuffComponent.class).requires(FactionComponent.class)
                    .requires(ResistStrugglePassive.class);
        }

        @Override
        public ResistRampageSkill create(String id, ComponentServicesPort services) {
            return new ResistRampageSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        faction = svc().components().get(FactionComponent.class);
        passive = svc().components().get(ResistStrugglePassive.class);
        voice = getComponent(ResistFloatingTextComponent.class);
        render = getComponent(HotbarRenderComponent.class);
    }

    @Override
    public void stop() {
        Player owner = svc().self().player();
        if (owner != null) {
            owner.clearTitle();
            if (buff != null) {
                buff.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            }
        }
        if (channelSwords != null) {
            channelSwords.remove();
            channelSwords = null;
        }
        if (landingSwords != null) {
            landingSwords.remove();
            landingSwords = null;
        }
        ResistState.unlock(svc().self().player());
        phase = 0;
        dashHits.clear();
        vitals = null;
        sante = null;
        buff = null;
        faction = null;
        passive = null;
        voice = null;
        render = null;
    }

    @Override
    protected boolean canUse() {
        Player owner = svc().self().player();
        if (owner == null) {
            return false;
        }
        if (ResistState.isLocked(owner, Bukkit.getCurrentTick())) {
            return false;
        }
        return buff == null || buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return 0;
    }

    // ───────── 施放 ─────────

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || !canUse() || isCoolingDown()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        startCooldown(COOLDOWN_TICKS);
        phase = 1;
        channelTick = 0;
        aftermathTimer = 0;
        travelled = 0d;
        dashHits.clear();
        direction = horizontal(owner.getEyeLocation().getDirection());
        ResistState.lock(owner, now, CHANNEL_TICKS + 30);

        World world = owner.getWorld();
        if (world != null) {
            channelSwords = ResistSwordModel.spawn(world, owner.getLocation(), 4, 1.8d, 8, 60, 10, 5.5d);
            ResistSound.rampageCharge(world, owner.getLocation(), 0);
            ResistVfx.rampageCharge(world, owner.getLocation(), 0);
        }
        if (voice != null) {
            voice.saySkill(owner, ResistFloatingTextComponent.MOMENT_SKILL_RAMPAGE);
        }
        if (render != null) {
            render.requestRepaint();
        }
    }

    // ───────── 每刻 ─────────

    @Override
    public void update() {
        if (phase == 0) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead() || owner.getHealth() <= 0d) {
            finish();
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            finish();
            return;
        }

        switch (phase) {
            case 1 -> tickChannel(owner, world);
            case 2 -> tickDash(owner, world);
            default -> tickAftermath();
        }
    }

    private void tickChannel(Player owner, World world) {
        channelTick++;
        //需求 6：引导期间缓慢 IV
        if (buff != null) {
            buff.applyPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 6, 3);
        }
        if (channelTick % 2 == 0) {
            showChannelTitle(owner);
            ResistVfx.rampageCharge(world, owner.getLocation(), channelTick);
            //需求 8：竖向范围圈（自身 / 15 格 / 30 格）
            ResistVfx.rampageRangeHint(world, owner.getLocation(), direction, channelTick);
        }
        if (channelSwords != null) {
            channelSwords.tick();
        }
        if (channelTick % 4 == 0) {
            ResistSound.rampageCharge(world, owner.getLocation(), channelTick / 4);
        }
        if (channelTick >= CHANNEL_TICKS) {
            owner.clearTitle();
            if (buff != null) {
                buff.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            }
            if (channelSwords != null) {
                channelSwords.remove();
                channelSwords = null;
            }
            phase = 2;
            ResistSound.rampageDash(world, owner.getLocation());
        }
    }

    /** 需求 6：屏幕 title 显示「引导中：X.X秒」。 */
    private void showChannelTitle(Player owner) {
        double remain = Math.max(0d, (CHANNEL_TICKS - channelTick) / 20d);
        Component main = Component.text(
                String.format(java.util.Locale.ROOT, "引导中：%.1f秒", remain),
                NamedTextColor.WHITE, TextDecoration.BOLD);
        owner.showTitle(Title.title(main, Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(500), Duration.ofMillis(100))));
    }

    private void tickDash(Player owner, World world) {
        if (blocked(owner.getLocation(), direction)) {
            land(owner, world);
            return;
        }
        Location next = owner.getLocation().clone().add(direction.clone().multiply(DASH_STEP));
        next.setYaw(owner.getLocation().getYaw());
        next.setPitch(owner.getLocation().getPitch());
        owner.teleport(next);
        owner.setFallDistance(0f);
        travelled += DASH_STEP;

        ResistVfx.rampageTrail(world, owner.getLocation());
        if (Bukkit.getCurrentTick() % 4 == 0) {
            ResistSound.rampageDash(world, owner.getLocation());
        }
        hitPathTargets(owner, world);

        if (travelled >= DASH_DISTANCE) {
            land(owner, world);
        }
    }

    private void tickAftermath() {
        if (landingSwords != null && !landingSwords.tick()) {
            landingSwords = null;
        }
        if (--aftermathTimer <= 0) {
            finish();
        }
    }

    // ───────── 命中 ─────────

    /** 途中：r5 内每个敌人只命中一次。 */
    private void hitPathTargets(Player owner, World world) {
        for (Player target : world.getNearbyPlayers(owner.getLocation(), PATH_RADIUS)) {
            if (target == null || target.equals(owner) || !isAlive(target) || !isHostile(target)) {
                continue;
            }
            if (!dashHits.add(target.getUniqueId())) {
                continue;   //本段已命中
            }
            if (vitals != null) {
                vitals.physicalDamage(target, owner, PATH_PHYSICAL);
            }
            if (sante != null) {
                sante.decreaseSanTE(target.getUniqueId(), roll(PATH_SPECIAL_MIN, PATH_SPECIAL_MAX));
            }
            target.setNoDamageTicks(0);
            if (passive != null) {
                passive.addBattleSeconds(owner, BATTLE_ADD, Bukkit.getCurrentTick());
            }
            ResistVfx.swordQiImpact(world, target.getLocation(), 0);
            ResistSound.swordQiSplash(world, target.getLocation());
        }
    }

    /** 落点：r10 内所有敌人（与途中命中可叠加）。 */
    private void land(Player owner, World world) {
        Location center = owner.getLocation().clone();
        phase = 3;
        aftermathTimer = 60;
        landingSwords = ResistSwordModel.spawn(world, center, 4, 2.2d, 8, 26, 10, 7.0d);
        ResistSound.rampageLanding(world, center);
        ResistVfx.rampageLanding(world, center, LAND_RADIUS, direction);
        ResistState.unlock(owner);

        for (Player target : world.getNearbyPlayers(center, LAND_RADIUS)) {
            if (target == null || target.equals(owner) || !isAlive(target) || !isHostile(target)) {
                continue;
            }
            if (vitals != null) {
                vitals.physicalDamage(target, owner, LAND_PHYSICAL);
            }
            if (sante != null) {
                sante.decreaseSanTE(target.getUniqueId(), roll(LAND_SPECIAL_MIN, LAND_SPECIAL_MAX));
            }
            target.setNoDamageTicks(0);
            if (passive != null) {
                passive.addBattleSeconds(owner, BATTLE_ADD, Bukkit.getCurrentTick());
            }
            ResistVfx.conquestHit(world, target.getLocation(), 3);
        }
    }

    private void finish() {
        Player owner = svc().self().player();
        if (owner != null) {
            owner.clearTitle();
            if (buff != null) {
                buff.removePotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS);
            }
        }
        if (channelSwords != null) {
            channelSwords.remove();
            channelSwords = null;
        }
        if (landingSwords != null) {
            landingSwords.remove();
            landingSwords = null;
        }
        ResistState.unlock(svc().self().player());
        phase = 0;
        aftermathTimer = 0;
        dashHits.clear();
    }

    // ───────── 工具 ─────────

    /** 前方下一格是否被方块挡住（脚部 + 头部两处采样）。 */
    private static boolean blocked(Location from, Vector dir) {
        Location next = from.clone().add(dir.clone().multiply(DASH_STEP));
        World world = next.getWorld();
        if (world == null) {
            return true;
        }
        for (double dy : new double[]{0.1d, 1.0d}) {
            Block block = world.getBlockAt(next.getBlockX(),
                    (int) Math.floor(next.getY() + dy), next.getBlockZ());
            if (!block.isPassable() && block.getBoundingBox().getVolume() > 0d) {
                return true;
            }
        }
        return false;
    }

    private static Vector horizontal(Vector vector) {
        if (vector == null) {
            return new Vector(0d, 0d, 1d);
        }
        Vector flat = new Vector(vector.getX(), 0d, vector.getZ());
        if (flat.lengthSquared() < 1.0E-6d) {
            return new Vector(0d, 0d, 1d);
        }
        return flat.normalize();
    }

    private int roll(int min, int max) {
        return min + random.nextInt(Math.max(1, max - min + 1));
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

    /** 当前阶段（0 空闲 / 1 引导 / 2 突进 / 3 收尾）。 */
    public int phase() {
        return phase;
    }

    /** 已突进距离（诊断读口）。 */
    public double travelled() {
        return travelled;
    }
}
