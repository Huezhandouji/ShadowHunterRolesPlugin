package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.roleComponent.ScheduledHandle;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.DamageKind;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FactionComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.TaskComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;


public class MeiqiheziBloodySlashSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "meiqihezi_skill_bloody_slash";

    //任务句柄：stop 时取消，避免角色被清除后仍结算伤害（平台 Task）
    private ScheduledHandle attackTask;

    // ───────── 四个协作组件引用**缓存在 start()** ─────────
    //`awake()` 只做构造期自检 / 只读自身，因此容器查找**不得**放 `awake()`；`start()` 时容器已冻结，
    //查询合法，且"一次查、处处用"（避免每次施放 / 每 tick 重复查容器）。
    //四个能力**向组件本身**取用（容器里它们每实例恰好一个 ⇒ 与端口取到的是同一批实例）。
    private EnergyComponent energy;
    private BuffComponent buffs;
    private TaskComponent timers;
    private VitalsComponent vitals;


    public MeiqiheziBloodySlashSkill(String id, ComponentServicesPort services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的描述符：名字 / 描述 / 冷却 / 耗能 / 图标由这里声明，
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification<MeiqiheziBloodySlashSkill> {

        public Specification(){
            super(
                    Component.text("血腥连斩"),
                    List.of(Component.text("向前移动4格并斩击，重复四次")),
                    160,
                    8,
                    Material.IRON_INGOT
            );
            requires(EnergyComponent.class).requires(BuffComponent.class).requires(TaskComponent.class).requires(VitalsComponent.class);
            //每次连斩的受害者判敌 ⇒ 读阵营组件；缺它则本技能不索敌，装配期就拦住
            requires(FactionComponent.class);
        }

        @Override
        public MeiqiheziBloodySlashSkill create(String id, ComponentServicesPort services){
            return new MeiqiheziBloodySlashSkill(id, services, this);
        }
    }


    /**
     * **开始生效**：把四个协作组件一次查好缓存进字段。
     * <p>时机 = {@code start()}，不在 {@code awake()}：禁止在 {@code awake()} 里取用其他组件
     * （awake 只做构造期自检 / 只读自身）；`start()` 时容器已冻结，容器查找合法。
     * <p>缓存理由：这些引用在一次实例生命周期内不变（组件集合装配后固定），重复查容器是纯浪费；
     * 四个框架级服务组件每实例恰好一个且已登记进实例容器，因此按类型查容器与端口取到的是同一批实例
     * （本类不参与运行期动态增删，引用不会失效）。
     * <p><b>边界</b>：若服务组件因**同 id 冲突**未被登记（框架对该情形记 WARNING 并跳过登记），
     * 这里的字段会是 {@code null}，后续使用点 NPE。该形态需要角色模板里存在与服务组件同名的 id，
     * 产品模板里没有，属病态配置，未加额外守卫。
     */
    @Override
    public void start() {
        energy = svc().components().get(EnergyComponent.class);
        buffs = svc().components().get(BuffComponent.class);
        timers = svc().components().get(TaskComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    /**
     * 右击施放：能量不足 / 被禁用时直接返回且不启动冷却；否则扣能量 `8`、
     * 以 `0L` 初始延迟 / `2L` 周期启动前摇任务（登记进本组件资源表，角色清除时由框架兜底取消，
     * 因此 `isValid()` 守卫不需要）、四周 `4` 格内敌对目标各受 `14` 点物理伤害、粒子 / 音效；
     * 冷却由本组件在施放成功处按声明值 **160** 启动。
     * <p>四个能力向组件本身取用（引用在 {@link #start()} 缓存）；
     * 伤害走含 {@link DamageKind} 的入口：`PHYSICAL` ⇒ {@code dealtPhysicalDamage}
     * （与物理伤害原语同一条 {@code DamageUtil} 路径），来源仍是本实例玩家。
     */
    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();

        if (energy.current() < getEnergyCost()) return;
        if(!buffs.canCastSkill()) return;
        energy.tryConsume(getEnergyCost());

        attackTask = timers.addScheduleRepeating(this, 0L, 2L, new Runnable() {

            private int count = 0;
            private final Player player = caster;

            @Override
            public void run() {
                if (count >= 4) {
                    attackTask.cancel();
                    return;
                }
                count += 1;

                Location loc = player.getLocation();
                Vector dir = loc.getDirection();
                player.setVelocity(dir.clone().setY(0).normalize().multiply(1.5));

                Collection<? extends Player> victims = loc.getNearbyPlayers(4);

                for (Player victim : victims) {
                    if (!svc().components().get(FactionComponent.class).isHostileTo(victim.getUniqueId())) continue;
                    victim.setNoDamageTicks(0);
                    vitals.damage(victim, 14, DamageKind.PHYSICAL);
                }

                loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 1);
                Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1f);
                Location particleLoc = loc.clone().add(0, 1, 0);
                particleLoc.getWorld().spawnParticle(Particle.DUST, particleLoc, 30, 0.5, 0.5, 0.5, 0, dust);

                loc.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1f);
            }

        });

        startCooldown();   //组件自启冷却（框架不再代启动）
    }

    @Override
    public void stop() {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
        }
    }

    /**
     * **闸门放行？**（基类不取 buff，由本组件用自己的字段判）。
     */
    @Override
    protected boolean canUse(){
        return buffs.canCastSkill();
    }

    /**
     * **当前能量**：本组件声明耗能 8，因此必须给出真实能量（否则"能量不足"态不出现）；
     * 取的是同一个能量组件实例（注册表装配期后冻结、同类型实例唯一，因此字段引用与按需查找恒等）。
     */
    @Override
    protected int currentEnergy(){
        return energy.current();
    }

}
