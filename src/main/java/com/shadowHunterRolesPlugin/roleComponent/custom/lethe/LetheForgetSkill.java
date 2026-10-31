package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;

/**
 * 冷识（{@code lethe}）的主动技能「**遗忘**」（槽位 1）：**烙下时空裂缝 / 记忆归还**。
 *
 * <h2>玩法（两段式，同一件物品）</h2>
 * <ol>
 *   <li><b>第一次释放</b>：引导 {@value #MARK_CHANNEL_TICKS} 刻（0.8 秒，期间自身缓慢 IV）后，
 *       在**当前位置**烙下一个「时空裂缝」；从这一刻起物品由「遗忘」变成「回忆」
 *       （材质 {@code SOUL_TORCH → REDSTONE_TORCH}），**物品名每刻显示与裂缝的距离 + 剩余时间**，lore 同步一行；</li>
 *   <li><b>裂缝有效期</b> {@value #RIFT_LIFETIME_TICKS} 刻（10 秒）。冷识离裂缝超过
 *       {@value #MAX_RIFT_DISTANCE} 格、或裂缝到期 ⇒ 裂缝提前消散，物品变回「遗忘」，
 *       **不进冷却**（玩家可以马上重新标记 —— 这次释放并没有产生"归还"这个结果）；</li>
 *   <li><b>第二次释放</b>：引导 {@value #RETURN_CHANNEL_TICKS} 刻（1 秒，期间自身缓慢 III）后
 *       传送回裂缝点；传送成功后**立刻** {@link #startCooldown()}（{@value #COOLDOWN_SECONDS} 秒）。</li>
 * </ol>
 *
 * <h2>★ 为什么冷却只在"传送成功"那一刻起算</h2>
 * 施放管道（{@code SkillListener#cast}）的闸门是"**冷却中不派发**"，而本技能需要**同一件物品
 * 连按两次**（标记 → 归还）。若第一次释放就进冷却，第二次释放会被闸门直接挡掉 —— 技能退化成
 * "只能标记、永远回不去"。因此冷却唯一启动点是 {@link #completeReturn(Player)} 里传送成功之后那一行，
 * 与"冷却只在成功路径启动"的口径一致。
 *
 * <h2>★ 引导为什么放在 {@code update()} 的刻计时状态机里（而不是节拍任务）</h2>
 * 引导期只有 16 / 20 刻，且**每刻都要看两件事**：① 施放闸门是否还开着（被眩晕 / 沉默必须打断）；
 * ② 裂缝是否还在（第二次引导的 1 秒里裂缝可能到期或超距）。这两条判据与"当前刻"绑定，写在
 * {@link #update()} 的状态机里只有一条时间线；用周期任务则会多一条与组件生命周期并行的任务线，
 * 打断时还得额外把任务取消干净（本仓库已有两套节拍并存导致的状态漂移教训）。
 *
 * <h2>进化[3]「无憾」</h2>
 * {@link LetheEvolutionPassive#hasFateRecall()} 为 {@code true} 时：**第一次释放（= 裂缝真正烙下那一刻）**
 * 记录当前血量与 SanTE，**第二次传送回裂缝后**把两者还原到记录值。
 * <ul>
 *   <li>记录点选在"裂缝落地"而不是"按下技能"：只有裂缝真的存在，这份记录才有归属；引导被打断时
 *       不会留下一条陈旧记录（否则下一次归还可能还原到很久以前的血量）；</li>
 *   <li>血量向上还原走 {@link VitalsComponent#heal(Player, double)}（走生命组件的结算与受治疗账本）；
 *       向下还原只能直写平台 {@code player.setHealth(...)} —— 生命组件只提供"治疗量"入口，没有
 *       "扣到指定值"的入口，而这里要用 {@link VitalsComponent#damage} 会触发受伤回调（不是本意）；</li>
 *   <li>记录值 clamp 到 {@code [1, 当前上限]}：原版 {@code setHealth(0)} 会直接把玩家打死，
 *       而"还原"无论如何不该把活人变成尸体。</li>
 * </ul>
 *
 * <h2>清理面</h2>
 * 三条路径都必须把引导痕迹收干净（缓慢药水 + 状态机归位）：① 引导被打断（闸门关闭 / 玩家死亡）；
 * ② 裂缝到期或超距（会连带打断第二次引导）；③ 组件 {@link #stop()}（换角色 / 掉线 / 角色被清除）。
 * 药水一律走 {@link BuffComponent} 的账本路径（{@code applyPotionEffect} / {@code removePotionEffect}），
 * 不直接 {@code player.addPotionEffect} —— 绕过账本会在角色清除时留下没人回收的孤儿效果。
 */
public class LetheForgetSkill extends Skill {

    /** 本组件的登记 id（与契约 §1 逐字一致）。 */
    public static final String ID = "lethe_forget_skill";

    // ───────── 数值（唯一修改点）─────────

    /** 冷却（240 刻 = 12 秒）：**第二次释放（传送成功）之后**才开始计时。 */
    private static final int COOLDOWN_TICKS = 240;

    /** 冷却秒数（只用于描述文案；与 {@link #COOLDOWN_TICKS} 同源换算，避免文案漂移）。 */
    private static final int COOLDOWN_SECONDS = COOLDOWN_TICKS / 20;

    /** 第一次释放的引导时长（16 刻 = 0.8 秒）。 */
    private static final int MARK_CHANNEL_TICKS = 16;

    /** 第二次释放的引导时长（20 刻 = 1 秒）。 */
    private static final int RETURN_CHANNEL_TICKS = 20;

    /** 引导期缓慢药水的安全余量（刻）：多给一点，保证引导期间药水不过期；结束时显式摘掉。 */
    private static final int SLOWNESS_SAFETY_TICKS = 10;

    /** 第一次释放的缓慢等级 IV ⇒ amplifier 3。 */
    private static final int MARK_SLOWNESS_AMPLIFIER = 3;

    /** 第二次释放的缓慢等级 III ⇒ amplifier 2。 */
    private static final int RETURN_SLOWNESS_AMPLIFIER = 2;

    /** 裂缝有效期（200 刻 = 10 秒）。 */
    private static final int RIFT_LIFETIME_TICKS = 200;

    /** 冷识离裂缝的最大允许距离（格）：超过即视为"失去联系"，裂缝提前消散。 */
    private static final double MAX_RIFT_DISTANCE = 75d;

    /** 裂缝引导线的绘制周期（20 刻 = 每秒一帧；只画方向，不必每刻）。 */
    private static final int RIFT_GUIDE_PERIOD_TICKS = 20;

    /**
     * 第二次引导期间"指向裂缝"的引导线重绘周期（4 刻 = 每秒 5 帧）。
     * <p>为什么比裂缝期的 1 秒/帧密：引导只有 1 秒，玩家在这 1 秒里会移动，线条要跟得上位置；
     * 又不逐刻画 —— {@code forgetGuide} 一次最多画 200+ 个点，逐刻画会白烧粒子。
     */
    private static final int RETURN_GUIDE_PERIOD_TICKS = 4;

    // ───────── 状态（每实例一份；不共享、不落表）─────────

    /** 技能当前处于哪一段（同一时刻只有一个状态，避免布尔组合出现"既在引导又在归还"的非法态）。 */
    private enum Phase {
        /** 空闲：没有裂缝、没有引导（常态）。 */
        IDLE,
        /** 第一次引导中：引导结束才烙下裂缝。 */
        CHANNEL_MARK,
        /** 第二次引导中：裂缝仍然有效（有效期照常倒计时）。 */
        CHANNEL_RETURN,
        /** 裂缝有效：等待第二次释放，或被超距 / 到期提前收掉。 */
        RIFT
    }

    private Phase phase = Phase.IDLE;

    /** 当前这一段已进行的刻数（引导计时；不需要跨段保留）。 */
    private int phaseTicks;

    /**
     * 裂缝位置（含世界）；{@code null} = 没有裂缝。
     * <p>存**克隆体**：{@link Location} 可变，存玩家位置引用会让后续任何原地修改（例如别人
     * {@code setY}）连带改掉"记下来的那个点"。
     */
    private Location rift;

    /** 裂缝剩余有效刻数（在本组件内累计，不依赖任何调度器的次数计数）。 */
    private int riftTicksLeft;

    /** 进化[3]「无憾」：这一次标记是否已记录血量 / SanTE（只在裂缝存在期间为 {@code true}）。 */
    private boolean fateRecorded;

    /** 进化[3]：标记那一刻的血量（{@link #fateRecorded} 为 {@code false} 时无意义）。 */
    private double recordedHealth;

    /** 进化[3]：标记那一刻的 SanTE（同上）。 */
    private int recordedSante;

    /**
     * 引导期缓慢是否由**本组件自己**记过账。
     * <p>为什么需要这个标记：{@link BuffComponent#removePotionEffect(PotionEffectType)} 会无条件摘掉
     * 该类型药水（不只是本组件施加的那一次）。若在"没引导"的四条清理路径上也盲摘 SLOWNESS，
     * 就会把**别人打给冷识的减速**一起解掉 —— 那是玩家不应得的净化解控。只有确实自己施加过才摘。
     */
    private boolean channelSlownessApplied;

    // ───────── 协作组件（只在 start() 取；stop() 置空）─────────

    private BuffComponent buff;
    private VitalsComponent vitals;
    private SanTEComponent sante;
    private LetheEvolutionPassive evolution;
    private HotbarRenderComponent render;
    private LetheFloatingTextComponent voice;

    /**
     * 重塑技能（**可选**依赖）：它引导期间要禁用其它技能，本技能在施放入口问一次
     * {@link LetheReshapeSkill#isChanneling()}。
     * <p>为什么用 {@code requiresOptional} + 判空：三个技能互相引用若都用 {@code requires} 会成环
     * （重塑也可能需要问别的技能）；可选声明缺失时只是"不禁用"，不会阻断装配。
     */
    private LetheReshapeSkill reshape;

    public LetheForgetSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /**
     * 本组件的描述符（栏位由装配点 {@code setSlot(1)} 指定 —— 栏位语言不写进组件）。
     * <p>依赖按"本类实际取用的每个组件类型"逐个声明（契约 §2.5）：缺失时装配期就失败，
     * 而不是运行期静默变成"技能按了没反应"。
     */
    public static final class Specification extends Skill.Specification<LetheForgetSkill> {

        public Specification() {
            super(Component.text("遗忘"),
                    List.of(
                            Component.text("第一次：引导0.8秒后，在脚下烙下时空裂缝（10秒，物品变身「回忆」）"),
                            Component.text("裂缝期间物品lore实时显示与裂缝的距离；离裂缝超过75格则裂缝消散"),
                            Component.text("第二次：引导1秒后传送回裂缝点，传送成功后开始冷却"),
                            Component.text("进化[3]「无憾」：标记时记录血量与特殊值，归还时还原"),
                            Component.text("CD " + COOLDOWN_SECONDS + "s（第二次释放、传送成功后才开始）")
                    ),
                    COOLDOWN_TICKS,
                    0,
                    Material.SOUL_TORCH);
            requires(BuffComponent.class)
                    .requires(VitalsComponent.class)
                    .requires(SanTEComponent.class)
                    .requires(LetheEvolutionPassive.class)
                    .requires(HotbarRenderComponent.class)
                    .requires(LetheFloatingTextComponent.class)
                    //可选：只用于"重塑引导期禁用其它技能"，缺失不阻止装配
                    .requiresOptional(LetheReshapeSkill.class);
        }

        @Override
        public LetheForgetSkill create(String id, ComponentServicesPort services) {
            return new LetheForgetSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        evolution = getComponent(LetheEvolutionPassive.class);
        render = svc().components().get(HotbarRenderComponent.class);
        voice = svc().components().get(LetheFloatingTextComponent.class);
        reshape = findComponent(LetheReshapeSkill.class);
    }

    /**
     * 停止生效：把引导与裂缝收干净（摘缓慢、状态机归位），**不启动冷却**
     * （实例已经销毁，"再进冷却"没有任何意义，还会误伤重选角色后的新鲜实例）。
     */
    @Override
    public void stop() {
        clearTransientState();
        buff = null;
        vitals = null;
        sante = null;
        evolution = null;
        render = null;
        voice = null;
        reshape = null;
    }

    // ───────── 施放（唯一入口：右键）─────────

    /**
     * 右键：没有裂缝 ⇒ 开始第一次引导；有裂缝 ⇒ 开始第二次引导。
     *
     * <p><b>闸门必须自己判</b>：{@code SkillListener#cast} 只查 {@code isCoolingDown()}，
     * **不查** {@code canUse()}（沉默 / 眩晕）—— 而本技能有两段式行为，"冷却中不派发"完全挡不住
     * "沉默中按出标记"。因此这里显式判 {@link #canUse()}。
     * <p>重复施放：引导中再按一次直接忽略（不叠引导、不重置计时）。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (signal.trigger() != CastTrigger.RIGHT_CLICK) {
            return;   //左右键 / Q 在本技能上没有语义；原版行为已被 listener 取消，这里只留空落点
        }
        if (isCoolingDown()) {
            return;
        }
        if (!canUse()) {
            return;
        }
        if (reshape != null && reshape.isChanneling()) {
            return;   //重塑引导期间禁用其它技能（契约 B3 的跨技能约定）
        }
        if (phase == Phase.CHANNEL_MARK || phase == Phase.CHANNEL_RETURN) {
            return;   //引导期间重复施放直接忽略
        }
        Player self = selfPlayer();
        if (self == null) {
            return;
        }
        beginChannel(self, phase != Phase.RIFT);
    }

    // ───────── 每刻：刻计时状态机（引导 / 裂缝有效期）─────────

    @Override
    public void update() {
        if (phase == Phase.IDLE && rift == null) {
            return;   //常态零开销：既没引导也没裂缝
        }
        Player self = selfPlayer();
        if (self == null || !self.isOnline() || self.isDead()) {
            clearTransientState();
            return;
        }
        //裂缝的有效期与超距判定先跑：第二次引导期间裂缝也可能先没，必须先打断引导再继续
        if (rift != null && !tickRift(self)) {
            return;
        }
        switch (phase) {
            case CHANNEL_MARK -> tickMarkChannel(self);
            case CHANNEL_RETURN -> tickReturnChannel(self);
            default -> {
                //IDLE / RIFT：没有引导要做
            }
        }
    }

    /** 第一次引导的一刻：缓慢 IV +（引导结束才落裂缝）。 */
    private void beginChannel(Player self, boolean marking) {
        phase = marking ? Phase.CHANNEL_MARK : Phase.CHANNEL_RETURN;
        phaseTicks = 0;
        if (buff != null) {
            int amplifier = marking ? MARK_SLOWNESS_AMPLIFIER : RETURN_SLOWNESS_AMPLIFIER;
            int duration = (marking ? MARK_CHANNEL_TICKS : RETURN_CHANNEL_TICKS) + SLOWNESS_SAFETY_TICKS;
            buff.applyPotionEffect(PotionEffectType.SLOWNESS, duration, amplifier);
            //记账位在"确实走账本路径施加过"之后才置真（见 channelSlownessApplied 的注释）
            channelSlownessApplied = true;
        }
        //第二次引导：每刻画出玩家 → 裂缝的引导线（玩家能看见自己正被拉向哪）
        if (!marking && rift != null) {
            LetheVfx.forgetGuide(self.getWorld(), self.getLocation(), rift);
        }
        repaint();
    }

    /** 第一次引导每刻：闸门关闭则打断；到点则落裂缝。 */
    private void tickMarkChannel(Player self) {
        phaseTicks++;
        //引导期画面：蓝色几何粒子绕角色而上（需求 2026-10-30）
        LetheVfx.forgetOpenOrbit(self.getWorld(), self.getLocation(), phaseTicks);
        if (!canUse()) {
            interruptChannel();
            return;
        }
        if (phaseTicks >= MARK_CHANNEL_TICKS) {
            placeRift(self);
        }
    }

    /** 第二次引导每刻：闸门关闭则打断；到点则传送归还。 */
    private void tickReturnChannel(Player self) {
        phaseTicks++;
        //关裂隙画面：蓝色几何粒子反向旋绕而上（需求 2026-10-30）
        LetheVfx.forgetCloseOrbit(self.getWorld(), self.getLocation(), phaseTicks);
        if (!canUse()) {
            interruptChannel();
            return;
        }
        if (rift != null && phaseTicks % RETURN_GUIDE_PERIOD_TICKS == 0) {
            LetheVfx.forgetGuide(self.getWorld(), self.getLocation(), rift);
        }
        if (phaseTicks >= RETURN_CHANNEL_TICKS) {
            completeReturn(self);
        }
    }

    /**
     * 裂缝有效期与超距判定（每刻）：到期、跨世界、超过 {@value #MAX_RIFT_DISTANCE} 格 ⇒ 提前消散。
     *
     * @return {@code true} = 裂缝仍然有效（本刻继续跑后续状态）；{@code false} = 已消散（状态已清理）
     */
    private boolean tickRift(Player self) {
        riftTicksLeft--;
        if (riftTicksLeft <= 0) {
            expireRift();
            return false;
        }
        World riftWorld = rift.getWorld();
        if (riftWorld == null || !riftWorld.equals(self.getWorld())) {
            //跨世界无法计算距离，等同于"远到失去联系"⇒ 同样提前消散
            expireRift();
            return false;
        }
        if (self.getLocation().distance(rift) > MAX_RIFT_DISTANCE) {
            expireRift();
            return false;
        }
        if (riftTicksLeft % RIFT_GUIDE_PERIOD_TICKS == 0) {
            LetheVfx.forgetGuide(self.getWorld(), self.getLocation(), rift);
        }
        //物品 lore 的"实时距离"要靠每刻重绘；只在本状态机里请求，空闲时零 setItem
        repaint();
        return true;
    }

    /** 第一次引导结束：在当前脚下烙下裂缝、切换物品双态、记录「无憾」数值。 */
    private void placeRift(Player self) {
        rift = self.getLocation().clone();
        riftTicksLeft = RIFT_LIFETIME_TICKS;
        phase = Phase.RIFT;
        phaseTicks = 0;
        removeSlowness();

        //进化[3]「无憾」：记录点 = 裂缝真正烙下的那一刻（见类注释"记录点"一段）
        fateRecorded = false;
        if (evolution != null && evolution.hasFateRecall()) {
            fateRecorded = true;
            recordedHealth = self.getHealth();
            recordedSante = sante != null ? sante.current() : 0;
        }

        World world = self.getWorld();
        LetheVfx.forgetPlace(world, rift);
        LetheSound.forgetPlace(world, rift);
        say(self, LetheFloatingTextComponent.MOMENT_FORGET_MARK);
        repaint();
    }

    /** 第二次引导结束：传送回裂缝、还原「无憾」记录、清裂缝状态，然后**立刻**起冷却。 */
    private void completeReturn(Player self) {
        Location target = rift;
        removeSlowness();
        if (target == null || target.getWorld() == null || !target.getWorld().equals(self.getWorld())) {
            //引导期间裂缝失效（理论上 tickRift 已先拦，这里是防御性一致）：按"裂缝消散"处理，不进冷却
            expireRift();
            return;
        }
        self.teleport(target.clone());

        //还原必须在传送之后：先回到裂缝，再把状态改回去（顺序与"归还"的语义一致）
        restoreFateRecord(self);

        //裂缝使命完成：清状态，然后启动冷却（本技能唯一的成功路径）
        rift = null;
        riftTicksLeft = 0;
        phase = Phase.IDLE;
        phaseTicks = 0;

        World world = self.getWorld();
        LetheVfx.forgetReturn(world, self.getLocation());
        LetheSound.forgetReturn(world, self.getLocation());
        say(self, LetheFloatingTextComponent.MOMENT_FORGET_RETURN);

        startCooldown();
        repaint();
    }

    /**
     * 进化[3]「无憾」的还原：把血量与 SanTE 写回记录值。
     * <p>细节与理由见类注释「进化[3]」一节（血量向上走生命组件、向下直写平台并 clamp 到上限）。
     */
    private void restoreFateRecord(Player self) {
        if (!fateRecorded) {
            return;
        }
        fateRecorded = false;
        if (evolution == null || !evolution.hasFateRecall()) {
            return;   //记录之后档位掉了（理论上不会发生）⇒ 不还原，按"没有这份记录"处理
        }
        double maxHealth = VitalsComponent.BASE_MAX_HEALTH;
        if (self.getAttribute(Attribute.MAX_HEALTH) != null) {
            maxHealth = self.getAttribute(Attribute.MAX_HEALTH).getValue();
        }
        //下限 1：setHealth(0) 会直接打死玩家；"还原"不该产生新的死亡
        double targetHealth = Math.max(1d, Math.min(recordedHealth, maxHealth));
        if (self.getHealth() < targetHealth && vitals != null) {
            vitals.heal(self, targetHealth - self.getHealth());
        } else {
            self.setHealth(targetHealth);
        }
        if (sante != null) {
            sante.set(recordedSante);   //set 内部 clamp 到 [0, 上限]，不需要再判
        }
    }

    /**
     * 引导被打断（眩晕 / 沉默）：摘掉缓慢、状态机归位，**不启动冷却**。
     * <p>若第二次引导被打断，裂缝仍然存在 ⇒ 回到 {@link Phase#RIFT} 继续倒计时（玩家还有机会再按一次）。
     */
    private void interruptChannel() {
        removeSlowness();
        phase = rift != null ? Phase.RIFT : Phase.IDLE;
        phaseTicks = 0;
        repaint();
    }

    /**
     * 裂缝提前消散（到期 / 超距 / 跨世界）：清裂缝与「无憾」记录，**不启动冷却**。
     * <p>会连带打断第二次引导（若正在引导中）—— 缓慢效果在这里一并摘掉。
     */
    private void expireRift() {
        rift = null;
        riftTicksLeft = 0;
        fateRecorded = false;
        phase = Phase.IDLE;
        phaseTicks = 0;
        removeSlowness();
        repaint();
    }

    /** 玩家不可用时的兜底清理（不启动冷却：没有产生任何结果）。 */
    private void clearTransientState() {
        rift = null;
        riftTicksLeft = 0;
        fateRecorded = false;
        phase = Phase.IDLE;
        phaseTicks = 0;
        removeSlowness();
        repaint();
    }

    // ───────── 物品外观（双态 + 实时距离）─────────

    /**
     * 底稿双态：有裂缝时是「回忆」（红石火把），否则走描述符声明的「遗忘」（灵魂火把）。
     * <p>为什么覆写 {@code baseItem} 而不是在 {@code buildItem} 里换材质：材质与显示名是"底稿"，
     * 基类的冻结画法（三态材质 / 名称着色 / 冷却秒数 / 状态行 / 识别键）都从底稿读，
     * 覆写底稿 = 只在基底上换一张脸，不重写任何一条冻结装饰规则。
     */
    @Override
    public ItemStack baseItem(String id) {
        if (rift == null) {
            return super.baseItem(id);
        }
        ItemStack stack = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            //物品名直接带「距离 + 剩余时间」：玩家低头就能读到，不必展开 lore（2026-10-30 需求）
            meta.displayName(recallName());
            meta.lore(getDescription());
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * 物品外观：裂缝存在时在 lore 追加"与裂缝的实时距离 + 剩余有效时间"。
     * <p>先取基类完整画法再追加（不重写三态画法 —— 那是冻结面）；距离由 {@link #update()} 每刻
     * 请求重绘推动刷新。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        Location point = rift;
        if (point == null) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(distanceLine(point));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * 「回忆」形态的物品名：**回忆 · 距裂缝 X.X 格 · 剩余 Y.Ys**（需求要求名字里就带这两个读数）。
     * <p>跨世界 / 拿不到玩家时退回纯「回忆」——不显示假数字（与 lore 距离行同一口径）。
     */
    private Component recallName() {
        Player self = selfPlayer();
        Location point = rift;
        if (self == null || point == null || point.getWorld() == null
                || !point.getWorld().equals(self.getWorld())) {
            return Component.text("回忆", NamedTextColor.AQUA, TextDecoration.BOLD);
        }
        double distance = self.getLocation().distance(point);
        double seconds = riftTicksLeft / 20d;
        return Component.text(String.format("回忆 · %.1f格 · %.1fs", distance, seconds),
                NamedTextColor.AQUA, TextDecoration.BOLD);
    }

    /** 距离行（跨世界时不能算距离，明确写"不在同一世界"而不是显示一个假数字）。 */
    private Component distanceLine(Location point) {
        Player self = selfPlayer();
        World world = point.getWorld();
        if (self == null || world == null || !world.equals(self.getWorld())) {
            return Component.text("裂缝：已不在同一世界", NamedTextColor.AQUA);
        }
        double distance = self.getLocation().distance(point);
        double seconds = riftTicksLeft / 20d;
        return Component.text(String.format("距裂缝 %.1f 格 · 剩余 %.1fs", distance, seconds),
                NamedTextColor.AQUA);
    }

    // ───────── 基类契约 ─────────

    /** **闸门**：被眩晕 / 沉默时不许施放（listener 不查它，必须这里判）。 */
    @Override
    protected boolean canUse() {
        return buff == null || buff.canCastSkill();
    }

    /** 本组件不参与能量维度（声明耗能 0），故回声明值。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }

    // ───────── 私有工具 ─────────

    /**
     * 摘掉引导期缓慢（走 buff 组件的账本路径，幂等；组件缺失时安静跳过）。
     * <p>**只摘自己施加过的那一次**（{@link #channelSlownessApplied}）：否则会把敌人打给冷识的减速
     * 一并解掉，等于白送一次解控。
     */
    private void removeSlowness() {
        if (!channelSlownessApplied) {
            return;
        }
        channelSlownessApplied = false;
        if (buff != null) {
            buff.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    /** 请求热键栏重绘（拿不到渲染组件就静默跳过）。 */
    private void repaint() {
        if (render != null) {
            render.requestRepaint();
        }
    }

    /** 喊一句语音（台词走语音组件的词库；组件缺失时安静跳过）。 */
    private void say(Player self, String moment) {
        if (voice != null && self != null) {
            voice.say(self, moment, null);
        }
    }

    // ───────── 诊断读口（供探针 / 运行级取证；不参与行为决策）─────────

    /** 当前是否有有效的裂缝。 */
    public boolean hasRift() {
        return rift != null;
    }

    /** 裂缝剩余有效刻数（没有裂缝时为 0）。 */
    public int riftTicksLeft() {
        return rift == null ? 0 : Math.max(0, riftTicksLeft);
    }
}
