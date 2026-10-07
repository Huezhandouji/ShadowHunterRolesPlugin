package com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.sinThorn.SinThornVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * 「罪棘」的核心被动：召唤者尖牙。
 *
 * <p>一次撕咬的三段时序：
 * <ol>
 *   <li>生成前（0.3 秒 = 6 刻，每 2 刻一帧共 3 帧）：目标周身一圈旋转向上的骨白螺旋；</li>
 *   <li>生成瞬间 = 特效最高峰：在敌人身下破土一枚真正的唤魔者尖牙实体
 *       （{@code EvokerFangs}，owner 设为召唤者以免咬到自己）+ 一蓬爆点；</li>
 *   <li>生成后 0.3 秒：在生成位置结算 4 点 SanTE（特殊值），并顺带挂上「律法之言」罪罚。</li>
 * </ol>
 *
 * <p>伤害口径：被动总伤害维持不变 —— 原来由代码结算的
 * 「6 点物理伤害」现在改由尖牙实体自己那一下承担（原版 {@code EvokerFangs} 咬一口 = 6 点），
 * 所以本类不再额外调 {@code physicalDamage}（否则会翻倍）；
 * 而「4 点特殊值」仍由代码在生成后 0.3 秒结算。
 * 副作用：尖牙的 6 点是原版的魔法伤害（不是物理），且若目标在尖牙抬起前跑出判定框会咬空。
 *
 * <p>常态每次只咬最近的一个；「罪恶的辩护」生效期间（{@link #setEmpowered(boolean)}）
 * 攻速 1.5 秒 → 0.5 秒、改为咬范围内所有敌人，并在光环边缘持续画出更大的旋转十字架。
 */
public class SinThornPassive extends PassiveSkill {

    /** **本组件的登记 id**（知识归属：组件自己）。 */
    public static final String ID = "sinThorn_passive_thorn";

    /** 尖牙的攻击半径（格）。 */
    private static final double FANG_RADIUS = 7.0;

    /** 常态攻击间隔：1.5 秒 = 30 刻。 */
    private static final int NORMAL_INTERVAL_TICKS = 30;

    /** 「罪恶的辩护」期间的攻击间隔：0.5 秒 = 10 刻。 */
    private static final int EMPOWERED_INTERVAL_TICKS = 10;

    /** 单次撕咬结算的 SanTE（特殊值）。 */
    private static final int FANG_SANTE_DAMAGE = 4;

    /** 生成前的前摇：0.3 秒 = 6 刻。 */
    private static final long FANG_WINDUP_TICKS = 6L;

    /** 生成后到结算特殊值的延时：0.3 秒 = 6 刻。 */
    private static final long FANG_SETTLE_DELAY_TICKS = 6L;

    /** 前摇螺旋的帧间隔：每 2 刻一帧。 */
    private static final long SPIRAL_FRAME_INTERVAL_TICKS = 2L;

    /** 前摇螺旋的总帧数（3 帧 × 2 刻 = 6 刻 = 0.3 秒）。 */
    private static final int SPIRAL_FRAMES = 3;

    /** 「律法之言」罪罚账本（同角色内的另一个被动）；拿不到时按 {@code null} 容忍。 */
    private LawWordPassive lawWord;

    /**
     * **本实例的 SanTE 组件** —— 用它上面的跨实例入口
     * {@code decreaseSanTE(UUID, int)} 削敌人的特殊值。
     * <p>跨实例削 SanTE 必须走这个官方入口，不绕公开 {@code RoleAPI}。
     */
    private SanTEComponent sante;

    /** 计时组件（前摇 / 结算延时都要用它登记，角色清除时框架兜底取消）。 */
    private TaskComponent timer;

    private int tickCounter = 0;
    private int vfxTick = 0;
    private double crossPhase = 0d;
    private boolean empowered = false;

    public SinThornPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的被动描述符（无栏位，天然不占热键栏）。
     */
    public static final class Specification extends PassiveSkill.Specification<SinThornPassive> {

        public Specification() {
            super(Component.text("罪棘"),
                    List.of(Component.text("靠近你的敌人（7格内）每1.5秒被召唤者尖牙撕咬，受到6点伤害并损失4点特殊值")));
            requires(TaskComponent.class);
            //`requires(具体被动.class)` 按具体类推导，因此这两个依赖如实声明为必需
            //  （二者同属本角色，必然同时装配）
            requires(SanTEComponent.class);
            requires(LawWordPassive.class);
            //尖牙索敌逐个候选判敌 ⇒ 读阵营组件；缺它则本被动不索敌，装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public SinThornPassive create(String id, ComponentServicesPort services) {
            return new SinThornPassive(id, services, this);
        }
    }

    /** **开始生效**：协作组件一次查好缓存进字段（只在 {@code start()} 取）。 */
    @Override
    public void start() {
        lawWord = svc().components().get(LawWordPassive.class);
        timer = svc().components().get(TaskComponent.class);
        sante = svc().components().get(SanTEComponent.class);
    }

    /**
     * **「罪恶的辩护」开关**：开启时攻速 0.5 秒、打范围内所有敌人，并在光环边缘画旋转十字架。
     */
    public void setEmpowered(boolean value) {
        this.empowered = value;
    }

    @Override
    public void update() {
        Player self = svc().self().player();
        if (self == null || self.isDead() || !self.isOnline()) {
            return;
        }

        //强化期：光环边缘一圈「快速消散的白色粒子」绕角色旋转（不再是十字架）
        //Y 取 getLocation()，方法内建 +1，正好是"比角色高 1 格"
        if (empowered) {
            vfxTick++;
            if (vfxTick % 2 == 0) {
                crossPhase += 0.12d;
                SinThornVfx.spawnOrbitingPoints(self.getWorld(), self.getLocation(),
                        FANG_RADIUS, crossPhase, 36, 0.30d, Particle.ELECTRIC_SPARK);
            }
        }

        tickCounter++;
        int interval = empowered ? EMPOWERED_INTERVAL_TICKS : NORMAL_INTERVAL_TICKS;
        if (tickCounter < interval) {
            return;
        }
        tickCounter = 0;

        List<Player> targets = hostilesAround(self);
        if (targets.isEmpty()) {
            return;
        }

        //常态：只咬最近的一个；强化期：范围内所有敌人
        if (!empowered) {
            targets = List.of(nearest(self, targets));
        }

        for (Player victim : targets) {
            bite(self, victim);
        }
    }

    /**
     * **一次完整撕咬**：前摇螺旋 → 尖牙破土（峰值）→ 0.3 秒后结算特殊值。
     * <p>三段都经计时组件登记（{@code addScheduleLater}），因此角色清除时自动取消，不留悬挂任务。
     */
    private void bite(Player self, Player victim) {
        final Location at = victim.getLocation().clone();
        final World world = self.getWorld();
        if (world == null) {
            return;
        }

        //① 生成前：0.3 秒内三帧「旋转向上」螺旋（相位与半径逐帧变大，越转越急、越高）
        for (int frame = 1; frame <= SPIRAL_FRAMES; frame++) {
            final int f = frame;
            timer.addScheduleLater(this, f * SPIRAL_FRAME_INTERVAL_TICKS, () -> {
                World w = self.getWorld();
                if (w == null) {
                    return;
                }
                SinThornVfx.spawnSpiral(w, at.clone(), f * 0.9d, 0.65d + f * 0.05d, 1.8d,
                        12, SinThornVfx.BONE_WHITE);
            });
        }

        //② 生成瞬间 = 特效最高峰：真正的唤魔者尖牙在敌人身下破土（owner = 召唤者，避免咬到自己）
        timer.addScheduleLater(this, FANG_WINDUP_TICKS, () -> {
            World w = self.getWorld();
            if (w == null) {
                return;
            }
            EvokerFangs fang = w.spawn(at.clone(), EvokerFangs.class);
            if (fang != null) {
                fang.setOwner(self);
            }
            SinThornVfx.spawnBurst(w, at.clone(), 18, 0.45d, Particle.CRIT);
            w.playSound(at.clone(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1f, 0.8f);
        });

        //③ 生成后 0.3 秒：在生成位置结算 4 点特殊值，并挂上「律法之言」罪罚
        timer.addScheduleLater(this, FANG_WINDUP_TICKS + FANG_SETTLE_DELAY_TICKS, () -> {
            Player current = Bukkit.getPlayer(victim.getUniqueId());
            if (current == null || current.isDead() || !current.isOnline() || current.getHealth() <= 0d) {
                return;
            }
            if (sante != null) {
                sante.decreaseSanTE(current.getUniqueId(), FANG_SANTE_DAMAGE);
            }
            if (lawWord != null) {
                lawWord.applyLaw(current.getUniqueId());
            }
        });
    }

    @Override
    public void stop() {
        tickCounter = 0;
        vfxTick = 0;
        crossPhase = 0d;
        empowered = false;
    }

    /**
     * **范围内还活着的敌对玩家**（不含自己；死亡 / 死亡界面 / 已下线一律排除）。
     * 敌人语义 = {@code svc().components().get(FactionComponent.class).isHostile(...)}（未选角色的玩家也算敌人）。
     */
    private List<Player> hostilesAround(Player self) {
        List<Player> result = new ArrayList<>();
        for (Player candidate : self.getLocation().getNearbyPlayers(FANG_RADIUS)) {
            if (candidate == null || candidate.equals(self)) continue;
            if (candidate.isDead() || !candidate.isOnline() || candidate.getHealth() <= 0d) continue;
            if (!svc().components().get(FactionComponent.class).isHostile(candidate)) continue;
            result.add(candidate);
        }
        return result;
    }

    /** 取列表中离自己最近的一个（列表非空时调用）。 */
    private Player nearest(Player self, List<Player> candidates) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Player candidate : candidates) {
            double distance = candidate.getLocation().distanceSquared(self.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }
}
