package com.shadowHunterRolesPlugin.roleComponent.custom.hunter;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent.CastSignal;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Random;

/**
 * 「猎手」技能之三：**遁形**（哭泣的黑曜石）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>获得 <b>速度 VI</b>、<b>完全隐身</b>、<b>抗性 V</b>；</li>
 *   <li>期间<b>免疫缓慢</b>；</li>
 *   <li>若使用技能或攻击，将<b>中断</b>技能；</li>
 *   <li>持续 <b>15 秒</b>；</li>
 *   <li>CD <b>20 秒</b>，且是「<b>技能完全后</b>冷却」；</li>
 *   <li>（5 级进化）持续缩短为 <b>6 秒</b>，但 5 格内所有敌人每 0.5 秒受 3 点灵魂伤害与
 *       10 点特殊值伤害，造成伤害则回复 [猎手] 4 点生命。</li>
 * </ol>
 *
 * <h2>「技能完全后冷却」怎么落地</h2>
 * 声明冷却 = {@value #COOLDOWN_TICKS} 刻，但 {@code startCooldown()} **只在结束那一刻**调用
 * （见 {@link #endStealth}）—— 施放时不调，因此"持续 15 秒"这段时间不在冷却里，
 * 与罪棘的「罪恶的辩护 / 审判孤刺」同一条口径。
 *
 * <h2>中断由谁触发（组件互相持有是工程既有形态）</h2>
 * 插件没有"某玩家放了技能"的全局事件 ⇒ 由**同一角色内的其它组件主动通知**：
 * <ul>
 *   <li>{@link HunterGrudgeMainWeapon#onAttack} —— 攻击；</li>
 *   <li>{@link HunterPounceSkill#onCast} / {@link HunterPullSkill#onCast} —— 使用技能。</li>
 * </ul>
 * 三处都只调一行 {@link #breakStealth()}，判断"现在在不在遁形里"归本组件自己。
 *
 * <h2>★ 口径申报：隐身用原版 {@code INVISIBILITY}，不含"连盔甲手持物一起藏"</h2>
 * "完全隐身"按**原版隐身**实现（{@code PotionEffectType.INVISIBILITY}，
 * 短时长每刻重刷）。原版隐身**仍会显示盔甲与手持物** —— 要做到"连装备都看不见"需要发包
 * （协议层），那超出组件能力且会与其它插件冲突，故不做。这是如实申报的边界，不是遗漏。
 *
 * <h2>★ 口径申报：「免疫缓慢」= 每刻移除自身的缓慢效果</h2>
 * 本组件每刻 {@code removePotionEffect(SLOWNESS)}，因此遁形期间任何来源的缓慢都留不住。
 * <p>**连带影响（如实申报）**：{@code BuffType.STUN}（眩晕）按工程口径附带
 * {@code SLOWNESS 255} —— 遁形期间那一部分会被本组件一并清掉，即"遁形能削弱眩晕的减速成分"
 * （失明 / 黑暗仍在，技能闸门仍关闭）。若你不希望这样，在移除前加一道
 * {@code buff.has(BuffType.STUN)} 的排除即可（一行）。
 *
 * <h2>★ 口径申报：与进化被动「抗性 I」的覆盖关系</h2>
 * 本组件的抗性 V 会覆盖 {@link HunterEvolutionPassive} 那份长时长的抗性 I（同为 {@code RESISTANCE}）。
 * 结束那一刻本组件先摘掉自己那三个效果，再调
 * {@link HunterEvolutionPassive#reapplyPermanentEffects()} 把进化那份**补回来** ——
 * "谁覆盖谁负责还原"的显式分工。
 *
 * <h2>★ 口径申报：激活期间再次按下 = 忽略</h2>
 * 需求没写"能否重复施放"。本实现取**忽略**（不刷新时长、不叠层、不额外消耗），
 * 因为"技能完全后才进冷却"意味着激活期间本来就不在冷却里，若允许重施就变成可以无限续杯。
 *
 * <h2>表现（工程行为规范 §6.1）</h2>
 * 长期激活的技能物品必须**常亮附魔光效** ⇒ 覆写 {@link #buildItem()}（先 {@code super} 保住识别键）
 * 并在激活态加 {@code setEnchantmentGlintOverride(TRUE)}；开始 / 结束各请求一次重绘。
 */
public class HunterStealthSkill extends Skill {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "hunter_skill_stealth";

    /** 冷却（刻）—— 需求原话"CD-20"（20 秒），且**技能完全后**才起算。 */
    private static final int COOLDOWN_TICKS = 400;

    /** 耗能：需求未提 ⇒ 0。 */
    private static final int ENERGY_COST = 0;

    /** 基础持续时间（刻）—— 需求原话"持续15秒"。 */
    public static final int BASE_DURATION_TICKS = 300;

    /** 速度 VI 的增幅值（速度 N ⇒ amplifier N-1）。 */
    private static final int SPEED_AMPLIFIER = 5;

    /** 抗性 V 的增幅值。 */
    private static final int RESISTANCE_AMPLIFIER = 4;

    /** 三个自施效果的刷新时长（刻）：短时长 + 每刻重刷 ⇒ 结束那一刻最多残留 0.4 秒。 */
    private static final int EFFECT_REFRESH_TICKS = 8;

    // ───────── 5 级进化的灵魂脉冲（读数来自进化被动）─────────

    /** 脉冲半径（格）—— 需求原话"范围[5格]内"。 */
    public static final double PULSE_RADIUS = 5d;

    /** 脉冲间隔（刻）—— 需求原话"每0.5秒"（10 刻）。 */
    public static final int PULSE_INTERVAL_TICKS = 10;

    /** 每次脉冲的灵魂伤害（取 {@code TRUE}）—— 需求原话"3点的灵魂伤害"。 */
    private static final double PULSE_SOUL_DAMAGE = 3d;

    /** 每次脉冲的特殊值（SanTE）伤害 —— 需求原话"10点特殊值伤害"。 */
    private static final int PULSE_SANTE_DAMAGE = 10;

    /** 脉冲造成过伤害时回复自己的生命 —— 需求原话"为[猎手]回复4点生命"。 */
    private static final double PULSE_SELF_HEAL = 4d;

    // ───────── 大招过程的持续表现（需求：紫色粒子与药水粒子"随机释放"）─────────

    /** 表现粒子的重画间隔（刻）：每 2 刻一批（10 批/秒），够密又不刷屏。 */
    private static final int STEALTH_VFX_INTERVAL_TICKS = 2;

    /** 每批紫色粒子的颗数区间（含两端）—— "随机释放"的一层含义。 */
    private static final int STEALTH_PURPLE_MIN = 2;
    private static final int STEALTH_PURPLE_MAX = 4;

    /** 每批药水粒子的颗数区间（含两端）。 */
    private static final int STEALTH_POTION_MIN = 1;
    private static final int STEALTH_POTION_MAX = 3;

    /** 表现粒子的散布半径（格）—— 贴着身形，不铺满整个战场。 */
    private static final double STEALTH_VFX_RADIUS = 0.95d;

    /** 仅供装饰性抖动的随机源（不影响任何判定）。 */
    private static final Random VFX_JITTER = new Random();

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private BuffComponent buff;
    private HotbarRenderComponent render;
    private HunterEvolutionPassive evolution;

    // ───────── 状态 ─────────

    /** 是否正处于遁形。 */
    private boolean active;

    /** 剩余持续刻。 */
    private int remainingTicks;

    /** 脉冲节拍计数器。 */
    private int pulseTick;

    /** 表现粒子节拍计数器（紫色 / 药水粒子）。 */
    private int vfxTick;

    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HunterStealthSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符：哭泣的黑曜石（需求指定）+ 逐条对应需求的说明。 */
    public static final class Specification extends Skill.Specification<HunterStealthSkill> {

        public Specification() {
            super(Component.text("遁形"),
                    List.of(
                            Component.text("获得速度 VI、完全隐身、抗性 V"),
                            Component.text("期间免疫缓慢"),
                            Component.text("使用技能或攻击将中断遁形"),
                            Component.text("持续 15 秒，冷却在技能完全后起算（20 秒）"),
                            Component.text("5 级进化：持续缩短为 6 秒，"
                                    + "但 5 格内敌人每 0.5 秒受 3 点灵魂伤害与 10 点特殊值伤害，"
                                    + "造成伤害则回复自己 4 点生命")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.CRYING_OBSIDIAN);
            requires(VitalsComponent.class);
            requires(SanTEComponent.class);
            requires(BuffComponent.class);
            requires(HotbarRenderComponent.class);
            requires(HunterEvolutionPassive.class);
        }

        @Override
        public HunterStealthSkill create(String id, ComponentServicesPort services) {
            return new HunterStealthSkill(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 依赖只在 {@code start()} 取。 */
    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
        evolution = svc().components().get(HunterEvolutionPassive.class);
    }

    /**
     * 停止生效：清掉本组件施加的三个效果（**不起冷却** —— 角色被清 / 玩家死亡不该记一次冷却），
     * 并保持与 {@link #start()} 严格对称。
     */
    @Override
    public void stop() {
        Player owner = svc().self().player();
        if (owner != null) {
            clearSelfEffects(owner);
            if (evolution != null) {
                //把自己覆盖掉的进化抗性补回来（角色即将被整体回收时也无害）
                evolution.reapplyPermanentEffects();
            }
        }
        active = false;
        remainingTicks = 0;
        pulseTick = 0;
        vfxTick = 0;
        vitals = null;
        sante = null;
        buff = null;
        render = null;
        evolution = null;
    }

    // ───────── 施放 ─────────

    /**
     * 施放入口。
     *
     * <p>闸门顺序：**已在遁形 ⇒ 忽略**（口径见类注释）→ 冷却 → 眩晕/沉默 → 取玩家 → 起状态。
     * <p>★ **不在这里起冷却**："技能完全后冷却"，冷却在 {@link #endStealth} 里起。
     */
    @Override
    public void onCast(CastSignal signal) {
        if (active) {
            return;
        }
        if (isCoolingDown() || !canUse()) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            return;
        }

        int duration = evolution == null
                ? BASE_DURATION_TICKS
                : evolution.stealthDurationTicks(BASE_DURATION_TICKS);
        if (duration <= 0) {
            return;
        }

        active = true;
        remainingTicks = duration;
        pulseTick = 0;
        vfxTick = 0;
        applySelfEffects(owner);

        //"大招开启时给一个三叉戟落雷音效"
        World world = owner.getWorld();
        if (world != null) {
            HunterSound.tridentThunderHunterStealthCastSound(world, owner.getLocation());
        }

        repaint();
    }

    // ───────── 每 tick ─────────

    /**
     * 每 tick：① 重刷速度 VI / 隐身 / 抗性 V；② 移除缓慢（免疫缓慢）；
     * ③（5 级）每 {@value #PULSE_INTERVAL_TICKS} 刻一次灵魂脉冲；④ 到点收工。
     */
    @Override
    public void update() {
        if (!active) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || owner.isDead() || !owner.isOnline()) {
            //拿不到玩家 ⇒ 状态作废，但**不起冷却**（角色会被整体回收；死亡不该记一次冷却）
            active = false;
            remainingTicks = 0;
            pulseTick = 0;
            vfxTick = 0;
            repaint();
            return;
        }

        applySelfEffects(owner);
        drawStealthVfx(owner);

        if (evolution != null && evolution.stealthSoulPulse()) {
            pulseTick++;
            if (pulseTick % PULSE_INTERVAL_TICKS == 0) {
                soulPulse(owner);
            }
        }

        remainingTicks--;
        if (remainingTicks <= 0) {
            endStealth(owner);
        }
    }

    // ───────── 外部中断入口 ─────────

    /**
     * **中断遁形**（供同角色的主武器 / 其它技能调用；不在遁形中时是空操作）。
     *
     * <p>需求："若使用技能或攻击，将中断技能" ⇒ 中断 = 立刻收工，并**照常起冷却**
     * （技能已经"完全结束"了，只是结束得更早）。
     *
     * @return 本次调用是否真的中断了一个正在进行的遁形
     */
    public boolean breakStealth() {
        if (!active) {
            return false;
        }
        Player owner = svc().self().player();
        endStealth(owner);
        return true;
    }

    /** 当前是否在遁形中（读口 / 排障用）。 */
    public boolean isStealthed() {
        return active;
    }

    /** 剩余持续刻（不在遁形中回 0）。 */
    public int remainingStealthTicks() {
        return Math.max(0, remainingTicks);
    }

    // ───────── 内部：起效 / 收工 ─────────

    /**
     * 施加 / 重刷本组件的三个效果 + 免疫缓慢。
     *
     * <p>三个效果都经 {@link BuffComponent#applyPotionEffect(PotionEffectType, int, int)}
     * —— **只作用自己**的正确口（工程纪律：`buff` 的那个口没有目标重载）。
     * 时长取 {@value #EFFECT_REFRESH_TICKS} 刻并每刻重刷：结束那一刻最多残留 0.4 秒。
     */
    private void applySelfEffects(Player owner) {
        if (buff != null) {
            buff.applyPotionEffect(PotionEffectType.SPEED, EFFECT_REFRESH_TICKS, SPEED_AMPLIFIER);
            buff.applyPotionEffect(PotionEffectType.RESISTANCE, EFFECT_REFRESH_TICKS, RESISTANCE_AMPLIFIER);
            buff.applyPotionEffect(PotionEffectType.INVISIBILITY, EFFECT_REFRESH_TICKS, 0);
        }
        //"期间免疫缓慢"：每刻把缓慢从自己身上摘掉
        owner.removePotionEffect(PotionEffectType.SLOWNESS);
    }

    /**
     * 收工：停掉重刷、摘掉本组件那三个效果、把进化那份被覆盖的抗性补回来，然后起冷却。
     *
     * <p>**只摘本组件这三个类型**：{@code REGENERATION}（进化 1 档）不动 ——
     * 这里刻意**不用** {@code buff.clearAppliedPotionEffects()}，
     * 因为那把口的粒度是**整个角色实例**（会把进化那两份永久效果一起清掉）。
     */
    private void endStealth(Player owner) {
        active = false;
        remainingTicks = 0;
        pulseTick = 0;
        vfxTick = 0;
        if (owner != null) {
            clearSelfEffects(owner);
            if (evolution != null) {
                evolution.reapplyPermanentEffects();
            }
        }
        //"技能完全后冷却"：冷却在这里起算
        startCooldown(adjustedCooldownTicks());
        repaint();
    }

    /** 摘掉本组件施加的三个效果（隐身要显式摘，否则会残留到短时长自然到期）。 */
    private void clearSelfEffects(Player owner) {
        owner.removePotionEffect(PotionEffectType.INVISIBILITY);
        owner.removePotionEffect(PotionEffectType.SPEED);
        owner.removePotionEffect(PotionEffectType.RESISTANCE);
    }

    /**
     * **大招过程的持续表现**：紫色粒子与药水粒子**随机释放**
     * （需求原话：「大招过程中的紫色粒子的随机释放还有药水粒子随机释放」）。
     *
     * <p>"随机"体现在两处：<b>位置随机</b>（球壳内均匀取点）与<b>每批颗数随机</b>
     * （在 {@value #STEALTH_PURPLE_MIN}~{@value #STEALTH_PURPLE_MAX} /
     * {@value #STEALTH_POTION_MIN}~{@value #STEALTH_POTION_MAX} 之间浮动，
     * 由 {@link HunterVfx} 内部实现）。本方法只负责**节拍**（每
     * {@value #STEALTH_VFX_INTERVAL_TICKS} 刻一批）。
     */
    private void drawStealthVfx(Player owner) {
        vfxTick++;
        if (vfxTick % STEALTH_VFX_INTERVAL_TICKS != 0) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }
        Location center = owner.getLocation();
        HunterVfx.randomPurpleHunterStealth(world, center,
                STEALTH_PURPLE_MIN + VFX_JITTER.nextInt(STEALTH_PURPLE_MAX - STEALTH_PURPLE_MIN + 1),
                STEALTH_VFX_RADIUS);
        HunterVfx.potionSpiralHunterStealth(world, center,
                STEALTH_POTION_MIN + VFX_JITTER.nextInt(STEALTH_POTION_MAX - STEALTH_POTION_MIN + 1),
                STEALTH_VFX_RADIUS);
    }

    /**
     * 5 级进化的**灵魂脉冲**：{@value #PULSE_RADIUS} 格内所有敌人各吃
     * 3 点灵魂伤害（取 {@code TRUE}）+ 10 点特殊值伤害；
     * **只要对至少一名敌人造成了伤害**，就回复自己 {@value #PULSE_SELF_HEAL} 点生命。
     *
     * <p>口径同猎杀：插件没有"灵魂"这一 {@code DamageKind} ⇒ 取 {@code TRUE}（真伤）。
     * <p>给【他人】扣 SanTE 走官方跨实例入口 {@code SanTEComponent#decreaseSanTE(UUID, int)}。
     */
    private void soulPulse(Player owner) {
        Location center = owner.getLocation();
        int damaged = 0;
        for (Player candidate : center.getNearbyPlayers(PULSE_RADIUS)) {
            if (candidate == null || candidate.equals(owner)) {
                continue;
            }
            if (!candidate.isOnline() || candidate.isDead() || candidate.getHealth() <= 0d) {
                continue;
            }
            Location at = candidate.getLocation();
            if (at.getWorld() == null || center.getWorld() == null || !at.getWorld().equals(center.getWorld())) {
                continue;
            }
            if (!svc().roleInfo().isHostileTo(candidate.getUniqueId())) {
                continue;
            }
            if (vitals != null) {
                vitals.trueDamage(candidate, owner, PULSE_SOUL_DAMAGE);
            }
            if (sante != null) {
                sante.decreaseSanTE(candidate.getUniqueId(), PULSE_SANTE_DAMAGE);
            }
            damaged++;
        }
        if (damaged > 0 && vitals != null) {
            vitals.heal(owner, PULSE_SELF_HEAL);
        }
    }

    // ───────── 外观：长期激活 ⇒ 常亮光效 ─────────

    /**
     * 覆写默认画法：**先 {@code super.buildItem()}**（识别键写在基类那一步，漏了会点击无反应），
     * 再在**激活态**加常亮附魔光效（只发光、不加词条、不换材质）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (!active) {
            return stack;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setEnchantmentGlintOverride(Boolean.TRUE);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 请求一次热键栏重绘（开始 / 结束都要，否则光效要等下一个节拍）。 */
    private void repaint() {
        if (render != null) {
            render.markDirty();
        }
    }

    /** 本次应起的冷却（3 级进化起减 1 秒）。 */
    private int adjustedCooldownTicks() {
        return evolution == null ? getCooldownTicks() : evolution.adjustedSkillCooldownTicks(getCooldownTicks());
    }

    /** 闸门：被眩晕 / 沉默时不可用。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /** 0 耗能 ⇒ 不参与能量维度。 */
    @Override
    protected int currentEnergy() {
        return getEnergyCost();
    }
}
