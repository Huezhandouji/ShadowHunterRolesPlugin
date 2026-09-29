package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.SinThornVfx;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive.SinThornPassive;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import java.util.List;

/**
 * 「罪棘」技能之二：罪恶的辩护（枯萎的灌木）。
 *
 * <p>行为：5 秒内每 0.5 秒刷新一次 8 点伤害吸收；期间「罪棘」尖牙
 * 攻速加快（1.5 秒 → 0.5 秒）并改为咬范围内所有敌人。
 * 冷却 10 秒，且是「技能完全后」冷却：{@code onCast} 里不启动冷却，
 * 由持续任务跑满 5 秒后自己收尾时才 {@code startCooldown()}。
 *
 * <p>特效：
 * <ul>
 *   <li>自身位置上方 1 格，每 2 刻重画一圈白色电火花粒子（{@code Particle.ELECTRIC_SPARK}）
 *       组成的 4 个十字架，相位缓增，慢慢绕角色转一圈（约 8 秒一圈）；
 *       该粒子本身消散很快（比末地烛那种长拖尾干脆得多）；</li>
 *   <li>光环边缘（7 格）那圈更大的旋转十字架由 {@link SinThornPassive} 在强化态下自绘
 *       （它才知道强化的真实起止，避免两处各存一份状态），位置已比角色高 1 格。</li>
 * </ul>
 *
 * <p>吸收用原版 {@code ABSORPTION}（每级 4 点，增幅 1 = 8 点），刷新周期 0.5 秒、
 * 每次给 0.75 秒时长（留 5 刻余量，避免刷新间隙里吸收被清零而闪烁）。
 */
public class SinDefenseSkill extends Skill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "sinThorn_skill_defense";

    /** 技能总时长：5 秒 = 100 刻。 */
    private static final int DURATION_TICKS = 100;

    /** 吸收刷新间隔：0.5 秒 = 10 刻。 */
    private static final int REFRESH_INTERVAL_TICKS = 10;

    /** 每次给的吸收时长：0.75 秒 = 15 刻（比刷新间隔多 5 刻，避免间隙闪烁）。 */
    private static final int ABSORPTION_DURATION_TICKS = 15;

    /** 吸收增幅：1 即 8 点（2 颗心的吸收）。 */
    private static final int ABSORPTION_AMPLIFIER = 1;

    /** 十字架特效帧间隔：每 2 刻一帧。 */
    private static final long VFX_FRAME_INTERVAL_TICKS = 2L;

    /**
     * 十字架每帧的相位增量（弧度）。
     * <p>每 2 刻一帧，即每秒 10 帧，每帧 0.08 rad = 每秒 0.8 rad，一圈 2π / 0.8 ≈ 7.9 秒。
     */
    private static final double CROSS_PHASE_PER_FRAME = 0.08d;

    /** 十字架环绕半径（贴在身体外侧）。 */
    private static final double CROSS_ORBIT_RADIUS = 1.55d;

    private BuffComponent buff;
    private TaskComponent timer;

    /** 吸收刷新任务句柄。 */
    private ScheduledHandle defenseTask;

    /** 十字架特效任务句柄。 */
    private ScheduledHandle vfxTask;

    /** 已经过的刻数（在本组件内累计，不用调度器的次数计数）。 */
    private int elapsedTicks = 0;

    /** 十字架旋转相位。 */
    private double crossPhase = 0d;

    /**
     * **是否正在生效（那 5 秒里）**。
     * <p>只为一件事服务：{@link #buildItem()} 在生效期间给技能物品加附魔光效。
     */
    private boolean active = false;

    /**
     * **渲染组件**（热键栏）；重绘走"取渲染组件再调它的 {@code requestRepaint()}"这条通道。
     */
    private HotbarRenderComponent render;

    public SinDefenseSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends Skill.Specification<SinDefenseSkill> {

        public Specification() {
            super(Component.text("罪恶的辩护"),
                    List.of(Component.text("5秒内每0.5秒刷新8点伤害吸收；期间[罪棘]攻速加快，并改为撕咬范围内所有敌人")),
                    200,
                    0,
                    Material.DEAD_BUSH);
            requires(BuffComponent.class).requires(TaskComponent.class).requires(HotbarRenderComponent.class);
        }

        @Override
        public SinDefenseSkill create(String id, ComponentServicesPort services) {
            return new SinDefenseSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        timer = svc().components().get(TaskComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
    }

    /** **请求重绘热键栏**（取渲染组件再调；拿不到就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();

        if (!buff.canCastSkill()) {
            return;
        }

        //防止重复施放把两个持续任务叠在一起（冷却期本就被 isCoolingDown 挡住，这里是兜底）
        if (defenseTask != null) {
            return;
        }

        elapsedTicks = 0;
        crossPhase = 0d;
        setThornEmpowered(true);

        //「使用中也加附魔特效」：整段 5 秒生效期让技能物品带附魔光效（buildItem 覆写里加）
        active = true;
        repaint();

        //两个音效同时播（同一 tick 里两次 playSound，客户端听感上是一个合声）
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_SHULKER_HURT_CLOSED, 1f, 0.8f);
        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_SHULKER_OPEN, 1f, 0.8f);

        //① 吸收刷新（每 0.5 秒一次，共 10 次）
        defenseTask = timer.addScheduleRepeating(this, REFRESH_INTERVAL_TICKS, REFRESH_INTERVAL_TICKS, () -> {
            Player owner = svc().self().player();
            if (owner == null || owner.isDead() || !owner.isOnline()) {
                finishDefense();
                return;
            }

            buff.applyPotionEffect(PotionEffectType.ABSORPTION, ABSORPTION_DURATION_TICKS, ABSORPTION_AMPLIFIER);

            elapsedTicks += REFRESH_INTERVAL_TICKS;
            if (elapsedTicks >= DURATION_TICKS) {
                finishDefense();
            }
        });

        //② 十字架特效（每 2 刻一帧；随技能一起被 finishDefense 取消）
        vfxTask = timer.addScheduleRepeating(this, VFX_FRAME_INTERVAL_TICKS, VFX_FRAME_INTERVAL_TICKS, () -> {
            Player owner = svc().self().player();
            if (owner == null || owner.isDead() || !owner.isOnline()) {
                return;
            }
            crossPhase += CROSS_PHASE_PER_FRAME;
            //自身位置上方 1 格处、缓慢绕角色转的 4 个白色电火花十字架
            SinThornVfx.spawnOrbitingCrosses(owner.getWorld(),
                    owner.getLocation().clone().add(0, 1, 0),
                    CROSS_ORBIT_RADIUS, crossPhase, 4, 0.55d, 4, Particle.ELECTRIC_SPARK);
        });

        //「技能完全后冷却」：此处不调 startCooldown()，由 finishDefense() 收尾时启动
    }

    /** 收尾：取消两个持续任务 → 摘掉附魔光效 → 关掉尖牙强化 → 启动冷却（技能完全结束后才开始算 10 秒）。 */
    private void finishDefense() {
        if (defenseTask != null) {
            defenseTask.cancel();
            defenseTask = null;
        }
        if (vfxTask != null) {
            vfxTask.cancel();
            vfxTask = null;
        }
        setThornEmpowered(false);
        elapsedTicks = 0;
        active = false;
        startCooldown();
        repaint();
    }

    /** 开关「罪棘」的强化态；被动未注册时静默跳过（不能因此让技能崩掉）。 */
    private void setThornEmpowered(boolean value) {
        SinThornPassive thorn = getComponent(SinThornPassive.class);
        if (thorn != null) {
            thorn.setEmpowered(value);
        }
    }

    /**
     * **生效期间给技能物品加附魔光效**。
     * <p>先按基类默认画法产出完整物品，再在生效期补一个 {@code setEnchantmentGlintOverride(true)} ——
     * 只加光效、不加真实附魔（不多词条、不改数值）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!active) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    @Override
    public void stop() {
        if (defenseTask != null) {
            defenseTask.cancel();
            defenseTask = null;
        }
        if (vfxTask != null) {
            vfxTask.cancel();
            vfxTask = null;
        }
        setThornEmpowered(false);
        elapsedTicks = 0;
        active = false;
    }

    /** **闸门放行？**（基类不查容器，用本组件自己的字段判）。 */
    @Override
    protected boolean canUse() {
        return buff.canCastSkill();
    }

    /** **当前能量**：本组件不参与能量维度（声明耗能 0），返回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
