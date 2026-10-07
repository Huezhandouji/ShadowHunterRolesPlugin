package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.SinThornVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;
import java.util.List;

/**
 * 「罪棘」技能之一：罪棘缠（下界石英）。
 *
 * <p>行为：1 秒前摇后，以自身为中心 7 格内的所有敌人被荆棘缠绕 ——
 * 8 点物理伤害 + 2 秒缓慢 255 + 2 秒失明。冷却 7 秒（140 刻），不耗能。
 *
 * <p>特效：
 * <ul>
 *   <li>引导期（整段 1 秒前摇）：角色四个角旁各自生成一颗农作物生长粒子
 *       （{@code Particle.HAPPY_VILLAGER}，即骨粉催熟作物时冒的那种绿点），并绕周身旋转
 *       —— 相位逐帧递增，因此转起来；点数为 1（"单个"）；</li>
 *   <li>落点：地面一圈荆棘环 + 前摇结束的音效。</li>
 * </ul>
 *
 * <p>前摇用计时组件登记（{@code TaskComponent#addScheduleLater}），角色清除时框架兜底取消，
 * {@code stop()} 里再显式取消一次（幂等）。
 * <p><b>缓慢 / 失明走 buff 组件的跨玩家入口</b>
 * （{@link BuffComponent#applyPotionEffectTo(Player, PotionEffectType, int, int)}）：
 * 效果进的是**承受方自己**的药水账本 ⇒ 他清除角色 / 组件停用时一并回收，也能被
 * {@code BuffComponent#clearDebuffOn(...)} 净化。承受方没有角色（没有账本）时本条**不施加**
 * （只吃物理伤害）—— 与 {@code RedEvilShockSkill} 的同一口径。
 */
public class SinThornEntangleSkill extends Skill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "sinThorn_skill_entangle";

    /** 前摇：1 秒 = 20 刻。 */
    private static final long WINDUP_TICKS = 20L;

    /** 前摇特效帧间隔：每 2 刻一帧，1 秒前摇共 10 帧。 */
    private static final long WINDUP_FRAME_INTERVAL_TICKS = 2L;

    /** 前摇特效总帧数（10 帧 × 2 刻 = 20 刻 = 1 秒）。 */
    private static final int WINDUP_FRAMES = 10;

    /** 缠绕半径（格）。 */
    private static final double ENTANGLE_RADIUS = 7.0;

    /** 缠绕伤害（物理）。 */
    private static final double ENTANGLE_DAMAGE = 8.0;

    /** 缓慢 / 失明持续时间：2 秒 = 40 刻。 */
    private static final int DEBUFF_DURATION_TICKS = 40;

    /** 缓慢的增幅（255，几乎无法移动）。 */
    private static final int SLOWNESS_AMPLIFIER = 255;

    private BuffComponent buff;
    private VitalsComponent vitals;
    private TaskComponent timer;

    /** 前摇任务句柄化：{@code stop()} 时取消。 */
    private ScheduledHandle castTask;

    /**
     * **是否正在前摇（引导）**。
     * <p>只为一件事服务：{@link #buildItem()} 在引导期间给技能物品加附魔光效，
     * 让"引导中"在快捷栏上看得见。
     */
    private boolean channelling = false;

    /**
     * **渲染组件**（热键栏）；重绘走"自己取渲染组件、调它的 {@code requestRepaint()}"这一条通道。
     */
    private HotbarRenderComponent render;

    public SinThornEntangleSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。
     */
    public static final class Specification extends Skill.Specification<SinThornEntangleSkill> {

        public Specification() {
            super(Component.text("罪棘缠"),
                    List.of(Component.text("1秒前摇后，召唤荆棘缠绕7格内所有敌人：8点物理伤害、2秒缓慢255与失明")),
                    140,
                    0,
                    Material.QUARTZ);
            requires(BuffComponent.class).requires(VitalsComponent.class).requires(TaskComponent.class)
                    .requires(HotbarRenderComponent.class)
                    //"7 格内所有敌人"逐个判敌 ⇒ 读阵营组件；缺它则本技能不索敌，装配期就拦住
                    .requires(FactionComponent.class);
        }

        @Override
        public SinThornEntangleSkill create(String id, ComponentServicesPort services) {
            return new SinThornEntangleSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
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

        //判定顺序与既有技能一致：先判能不能施放（被沉默/眩晕则不施放、不启冷却）
        if (!buff.canCastSkill()) {
            return;
        }

        //冷却已挪到"前摇结束"那一刻才启动，因此前摇期间 isCoolingDown 为假，
        //   这里必须自己挡住重复施放，否则连点会叠出两条前摇。
        if (channelling) {
            return;
        }

        caster.getWorld().playSound(caster.getLocation().clone(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.7f);

        //「技能引导时给相应物品附魔」：引导期间让本技能物品带附魔光效（buildItem 覆写里加）
        channelling = true;
        repaint();

        //① 引导期特效：10 帧、每 2 刻一帧，覆盖整段 1 秒前摇
        for (int frame = 1; frame <= WINDUP_FRAMES; frame++) {
            final int f = frame;
            timer.addScheduleLater(this, f * WINDUP_FRAME_INTERVAL_TICKS, () -> windupFrame(f));
        }

        //② 前摇结束：摘掉附魔光效 → 启动冷却 → 荆棘落地 + 结算
        //   「物品不会直接消失，而是存在一会再被替换」：冷却（图标换成结构空位）推迟到这一刻，
        //     前摇这 1 秒里物品一直是原样 + 附魔光效。
        castTask = timer.addScheduleLater(this, WINDUP_TICKS, () -> {
            channelling = false;
            startCooldown();
            repaint();

            Player owner = svc().self().player();
            if (owner == null || owner.isDead() || !owner.isOnline()) {
                return;
            }

            Location center = owner.getLocation();
            drawThornRing(center);

            for (Player victim : center.getNearbyPlayers(ENTANGLE_RADIUS)) {
                if (victim == null || victim.equals(owner)) continue;
                if (victim.isDead() || !victim.isOnline() || victim.getHealth() <= 0d) continue;
                if (!svc().components().get(FactionComponent.class).isHostile(victim)) continue;

                vitals.physicalDamage(victim, owner, ENTANGLE_DAMAGE);
                //缓慢 / 失明走 buff 组件的跨玩家入口 ⇒ 进**受害者自己**的药水账本
                //  （他清角色 / 组件停用时一并回收，也能被 clearDebuffOn 净化）；
                //  原先直接 victim.addPotionEffect 的效果没人认领。
                //  ★ 目标没有角色 ⇒ 本入口不写（没有账本）⇒ 那种玩家只吃伤害，不吃减益。行为变更，如实申报。
                buff.applyPotionEffectTo(victim, PotionEffectType.SLOWNESS, DEBUFF_DURATION_TICKS, SLOWNESS_AMPLIFIER);
                buff.applyPotionEffectTo(victim, PotionEffectType.BLINDNESS, DEBUFF_DURATION_TICKS, 0);
            }

            center.getWorld().playSound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1f, 0.6f);
        });

        //冷却不在这里启动 —— 见上面 castTask 里的注释（推迟到前摇结束）。
    }

    /**
     * **一帧前摇特效**：角色四个角旁各冒一颗农作物生长粒子，并绕周身旋转。
     * <p>相位随帧号递增即"旋转"；每处只放 1 颗即"单个"；四个方位用 45°/135°/225°/315°（即四个角）。
     */
    private void windupFrame(int frame) {
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        Location center = owner.getLocation();
        double phase = frame * 0.7d;                 // 每帧转 ~40°
        double radius = 1.25d;

        for (int i = 0; i < 4; i++) {
            double angle = Math.PI / 4d + phase + i * (Math.PI / 2d);   // 四个角 + 旋转
            Location at = center.clone().add(Math.cos(angle) * radius, 0.9d, Math.sin(angle) * radius);
            world.spawnParticle(Particle.HAPPY_VILLAGER, at, 1, 0, 0, 0, 0);
        }
    }

    /** 在地上画一圈荆棘粒子（纯装饰）。 */
    private void drawThornRing(Location center) {
        Particle.DustOptions options = new Particle.DustOptions(org.bukkit.Color.fromRGB(70, 15, 35), 1f);
        int points = 64;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2d * i / points;
            double x = Math.cos(angle) * ENTANGLE_RADIUS;
            double z = Math.sin(angle) * ENTANGLE_RADIUS;
            center.getWorld().spawnParticle(Particle.DUST, center.clone().add(x, 0.3, z), 1, 0, 0, 0, options);
        }
        SinThornVfx.spawnBurst(center.getWorld(), center, 24, 0.8d, Particle.CRIT);
    }

    /**
     * **引导中给技能物品加附魔光效**。
     * <p>做法：先按基类默认画法产出完整物品，再在引导期间补一个
     * {@code setEnchantmentGlintOverride(true)} —— 只加光效、不加任何真实附魔
     * （不会多出附魔词条，也不改任何数值）。
     * <p>引导开始/结束各调一次 {@code repaint()} 触发重绘（见 {@code onCast} 与前摇任务）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!channelling) {
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
        if (castTask != null) {
            castTask.cancel();
            castTask = null;
        }
        channelling = false;
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
