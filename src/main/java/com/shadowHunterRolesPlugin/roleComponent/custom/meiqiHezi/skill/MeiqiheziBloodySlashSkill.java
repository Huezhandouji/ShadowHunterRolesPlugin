package com.shadowHunterRolesPlugin.roleComponent.custom.meiqiHezi.skill;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.DamageKind;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.TimerComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.*;


public class MeiqiheziBloodySlashSkill extends Skill {

    //技能任务句柄化：stop 时取消，避免角色被清除后仍结算伤害（平台 Task）
    private Task attackTask;

    // ───────── 四个协作组件引用**缓存在 start()** ─────────
    //`awake()` 只做构造期自检 / 只读自身 ⇒ 容器查找**不得**放 `awake()`；`start()` 相容器已冻结
    //⇒ 查询合法 ✓，且"一次查、处处用"（避免每次施放 / 每 tick 重复查容器）。
    //本类**不再**经服务集的白名单端口成员取用这四个能力 ⇒ 改为**向组件本身**取用 ✓
    //（容器里它们每实例恰好一个 ⇒ 与旧路径拿到的是**同一批实例** ✓）。
    private EnergyComponent energy;
    private BuffComponent buffs;
    private TimerComponent timers;
    private VitalsComponent vitals;


    public MeiqiheziBloodySlashSkill(String id, ComponentServices services, Specification specification){
        super(id, services, specification);
    }

    /**
     * 本组件的**描述符**：表现值默认值 = 原构造实参（名字 / 描述 / 冷却 / 耗能 / 图标逐字段一致），
     * 栏位由装配点 {@code setSlot} 指定，创建逻辑把描述符自己交给组件。
     */
    public static final class Specification extends Skill.Specification {

        public Specification(){
            super(
                    Component.text("血腥连斩"),
                    Component.text("向前移动4格并斩击，重复四次"),
                    160,
                    8,
                    Material.IRON_INGOT
            );
        }

        @Override
        public MeiqiheziBloodySlashSkill create(String id, ComponentServices services){
            return new MeiqiheziBloodySlashSkill(id, services, this);
        }
    }


    /**
     * **开始生效**：把四个协作组件**一次查好**缓存进字段 ✓。
     * <p><b>为什么在 {@code start()} 而不是 {@code awake()}</b>：禁止在 {@code awake()} 里
     * **取用其他组件** ✗（awake 只做构造期自检 / 只读自身）；`start()` 相容器已冻结 ⇒ 容器查找合法 ✓。
     * <p><b>为什么缓存</b>：这些引用在一次实例生命周期内不变（组件集合在装配后固定）⇒ 重复查容器是纯浪费；
 * 既有实现经服务集端口取用，端口本身也是**构造期就持有的引用** ⇒ 缓存与既有口径同族 ✓。
     * <p><b>等价性论据</b>：四个框架级服务组件**每实例恰好一个**且已登记进实例容器（框架无条件创建 + 登记）
     * ⇒ 按类型查容器与旧端口取到的是**同一批实例** ✓（本类不参与运行期动态增删，引用不会失效）。
     * <p><b>边界</b>：若服务组件因**同 id 冲突**未被登记（框架对该情形记 WARNING 并跳过登记），
 * 这里的字段会是 {@code null} ⇒ 后续使用点 NPE（既有写法不会）。该形态需要角色模板里存在与服务组件同名的
     * id，产品模板里没有 ⇒ 属病态配置，未加额外守卫（不静默吞掉框架级异常）。
     */
    @Override
    public void start() {
        energy = svc().components().get(EnergyComponent.class);
        buffs = svc().components().get(BuffComponent.class);
        timers = svc().components().get(TimerComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    /**
     * 右击施放（新管道；批次⑧-b/B⑧-b 迁移）。与旧 `onRightClick(Player, RoleInstance)` **逐条等价**：
     * 能量不足 / 被禁用时**直接返回且不启动冷却**（旧代码即如此）⇒ 早返回跳过后续语句；
     * 否则扣能量 `8`、以 `0L` 初始延迟 / `2L` 周期启动前摇任务（**登记进本组件资源表**，角色清除时由框架兜底取消
     * ⇒ 原 `isValid()` 守卫不需要）、四周 `4` 格内敌对目标各受 `14` 点物理伤害、粒子/音效逐字不变；
     * 冷却由本组件在施放成功处按声明值 **160** 启动。
     * <p>返回类型改 {@code void}（施放结果枚举已删，返回值无消费点）。
     * <p><b>四个能力改向组件本身取用</b>（引用在 {@link #start()} 缓存）；
     * 伤害改走**含 {@link DamageKind} 的新入口** —— `PHYSICAL` ⇒ {@code dealtPhysicalDamage}
 * （与物理伤害原语**同一条 {@code DamageUtil} 路径** ✓），来源仍是**本实例玩家** ✓
     * ⇒ 与旧调用点（victim / source / 14）**逐字等价** ✓。**数值 / 触发条件 / 冷却 / 表现层一字未改** ✓。
     */
    @Override
    public void onCast(CastSignal signal) {
        Player caster = svc().self().player();

        if (energy.current() < getEnergyCost()) return;
        if(!buffs.canCastSkill()) return;
        energy.tryConsume(getEnergyCost());

        attackTask = timers.runRepeating(this, 0L, 2L, new Runnable() {

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
                    if (!svc().roleInfo().isHostile(victim)) continue;
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

        startCooldown();   //D1：组件自启冷却（框架不再代启动）
    }

    @Override
    public void stop() {
        if(attackTask != null){
            attackTask.cancel();
            attackTask = null;
        }
    }

    /**
     * **闸门放行？**（基类不再取 buff ⇒ 由本组件用**自己的字段**判）。
     */
    @Override
    protected boolean gateOpen(){
        return buffs.canCastSkill();
    }

    /**
     * **当前能量**：本组件**声明耗能 8** ⇒ 必须给出真实能量（否则"能量不足"态不出现 ✗）；
 * 与既有实现**逐字等价**：同一个能量组件实例（注册表装配期后冻结、同类型实例唯一 ⇒ 字段引用与按需查找恒等 ✓）。
     */
    @Override
    protected int currentEnergy(){
        return energy.current();
    }

}
