package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.CombatStateComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import javax.annotation.Nonnegative;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * **深度癔症**（苍鹭被动）：每次攻击 / 使用技能叠「创伤」，上限 {@value #MAX_STACK_COUNT} 层。
 *
 * <h2>层数口径（2026-10-26 定稿）</h2>
 * <ul>
 *   <li><b>叠层只做 clamp</b>：{@link #requestAddStackCount(int)} 到达上限后**不增也不减**，
 *       而且**不再顺手结算**（旧版满层就地结算、把 16 打成 12，看起来像"上限只有 12"，已删）；</li>
 *   <li><b>消耗只发生在显式结算</b>：{@link #requestResolve()} 每次扣 {@value #RESOLVE_COUNT} 层并回 4 点生命
 *       —— 这是被动本身的机制（强化普攻拿层数换伤害/SanTE/回血），<b>满层也照扣</b>。
 *       "满层不增不减"约束的是**叠加**，不是结算；</li>
 *   <li><b>档位 = 每 4 层一档</b>：{@link #tierOf(int)} → 0(缺省)/1(4)/2(8)/3(12)/4(16)，
 *       主武器名字色与 actionbar 的 "/" 配色都按档位走（{@link #tierColor(int)} 是唯一配色表），
 *       每跨过一档播一次对应音效（{@link #playTierSounds(int, int)}）。</li>
 * </ul>
 *
 * <h2>结算通知（本类唯一的对外口）</h2>
 * 本被动自己不认识任何具体技能：它只维护一份「结算时通知谁」的名单
 * （{@link #addResolveListener} / {@link #removeResolveListener}），
 * 「结算之后还要做什么」归订阅方自己实现。
 * <p>形态与既有 {@code EnergyComponent} 的监听名单一致（JDK {@code Consumer} + owner 成对登记），
 * 因此不为「已经是组件」的关注点新造能力接口。
 */
public class HeronTraumaPassive extends PassiveSkill {

    public static final String ID = "cangluHysteriaPassive";

    private int hysteriaTimer;
    private int stackCount;

    /**
     * 战斗状态（2026-10-31 修复）：**唯一真值来自框架** {@link CombatStateComponent}。
     * <p>本被动不再自己维护"脱战计时"—— 进入战斗 / 脱离战斗的判定必须与框架一致：
     * 周围 4 格有敌人 / 给敌人上状态 / 受敌方攻击 ⇒ 战斗；最后一次刷新后 6 秒无刷新 ⇒ 脱战。
     */
    private CombatStateComponent combat;

    /** 当前是否正在吃"深度癔症"的隐身（用于进入战斗时立刻摘掉，而不是等 45 刻自然过期）。 */
    private boolean hysteriaActive;

    private static final int MAX_STACK_COUNT = 16;
    private static final int RESOLVE_COUNT = 4;
    /** 档位数 = 16 / 4 = 4（4/8/12/16 各一档）。 */
    private static final int MAX_TIER = MAX_STACK_COUNT / RESOLVE_COUNT;

    /**
     * 档位 → 主武器名字色：0 灰（不足 4 层）/ 1 暗蓝 / 2 普通蓝 / 3 亮蓝 / 4 亮紫（满层）。
     * <p>配色只在这里写一份，主武器 {@code buildItem()} 调 {@link #tierColor(int)} 取 —— 免得
     * "层数档位"这件事在组件里各写一份而漂移。
     */
    private static final int[] TIER_COLORS = {
            0x8C8C8C,   // 0 档（不足 4 层）· 灰
            0x1E4FB8,   // 1 档（4~7）· 暗蓝
            0x2F7BFF,   // 2 档（8~11）· 普通蓝
            0x35C8FF,   // 3 档（12~15）· 亮蓝
            0xB96BFF    // 4 档（16 满层）· 亮紫
    };

    /** 每满一档的音效（4/8/12/16 各一句，逐档上扬）。 */
    private static final Sound[] TIER_SOUNDS = {
            Sound.BLOCK_AMETHYST_BLOCK_CHIME,
            Sound.BLOCK_NOTE_BLOCK_CHIME,
            Sound.BLOCK_NOTE_BLOCK_BELL,
            Sound.ENTITY_PLAYER_LEVELUP
    };
    /** 与 {@link #TIER_SOUNDS} 配套的音高。 */
    private static final float[] TIER_PITCH = {1.25f, 1.05f, 0.95f, 1.0f};

    /**
     * 一条结算通知登记：{@code owner}（谁订阅）+ {@code callback}（怎么通知）成对持有。
     * <p>{@code owner} 只为诊断与「按订阅者撤销」而存在（口径同 {@code EnergyComponent.Listener}）。
     */
    public record ResolveListener(RoleComponent owner, Consumer<HeronTraumaPassive> callback) { }

    /** 结算通知名单（顺序 = 登记先后；通知时取快照，遍历途中增删安全）。 */
    private final List<ResolveListener> resolveListeners = new ArrayList<>();

    private SanTEComponent sante;
    private BuffComponent buff;
    private HotbarRenderComponent render;
    private VitalsComponent vitals;
    /**
     * 受伤订阅句柄（{@code start()} 注册、{@code stop()} 按引用撤销）。
     * <p>★ 口径是上游 {@code 0c106f4} 之后的形态：**注册 consumer**，不再实现
     * {@code VitalsComponent.Participant} 接口（那条接口已删）。受治疗那条名单本被动不关心，
     * 因此**不再写一个空的 {@code onHealed}** —— 这正是新形态要消掉的东西。
     */
    private VitalsComponent.DamagedListener damagedEntry;

    BossBar bossbar;




    /**
     * @param id            注册 id（装配期由 {@code Role.Builder.addComponent} 绑定）
     * @param services      该 id 的服务集
     * @param specification 本组件自己的描述符（声明数据的唯一来源）
     */
    public HeronTraumaPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification<HeronTraumaPassive> {

        public Specification(){
            super(Component.text("创伤"), List.of(Component.text("苍鹭的创伤被动，给自己叠层数")));
            //受击要订阅 VitalsComponent 的受伤名单（装配期声明，运行期才取得到）
            requires(VitalsComponent.class);
            //深度癔症的"脱战"判定沿用框架战斗状态（2026-10-31 修复）
            requires(CombatStateComponent.class);
        }

        @Override
        public HeronTraumaPassive create(String id, ComponentServicesPort services){
            return new HeronTraumaPassive(id, services, this);
        }
    }

    @Override
    public void awake(){
        super.awake();
        stackCount = 0;
    }

    @Override
    public void start(){
        super.start();
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = svc().components().get(HotbarRenderComponent.class);
        //战斗状态唯一真值（框架组件；withBuiltIns 恒装，因此装配期依赖成立）
        combat = svc().components().get(CombatStateComponent.class);
        hysteriaActive = false;
        //订阅受伤（受击 ⇒ 回 1 SanTE）；受治疗不订阅（本被动没有对应行为）
        vitals = svc().components().get(VitalsComponent.class);
        if (vitals != null) {
            damagedEntry = vitals.addDamagedListener(this, event -> onDamaged(event.source(), event.amount()));
        }



        bossbar = BossBar.bossBar(
                Component.empty(),
                0f,
                BossBar.Color.BLUE,
                BossBar.Overlay.PROGRESS
        );
        // 2026-10-25：HUD 改为 actionbar「////」，不再使用 bossbar
    }

    @Override
    public void update(){
        Player player = svc().self().player();
        if(player == null || !player.isOnline()){
            return;
        }
        //16 格紧凑斜杠：暗灰 = 空缺；亮青 = 已叠但该组未满；**红 = 该 4 层组已满**
        net.kyori.adventure.text.Component bar = net.kyori.adventure.text.Component.empty();
        for (int i = 0; i < MAX_STACK_COUNT; i++) {
            boolean on = i < stackCount;
            //★ 每满 4 层，那一组 4 根 "/" 变红：组序 = i/4，组完整 ⇔ (i/4) < (stackCount/4)
            boolean groupComplete = on && (i / RESOLVE_COUNT) < (stackCount / RESOLVE_COUNT);
            bar = bar.append(net.kyori.adventure.text.Component.text("/",
                    !on ? net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY
                        : groupComplete ? net.kyori.adventure.text.format.NamedTextColor.RED
                                        : net.kyori.adventure.text.format.NamedTextColor.AQUA));
        }
        player.sendActionBar(net.kyori.adventure.text.Component.text("忧郁创伤 ", net.kyori.adventure.text.format.NamedTextColor.DARK_AQUA)
                .append(bar)
                .append(net.kyori.adventure.text.Component.text(
                        " " + stackCount + "/" + MAX_STACK_COUNT, net.kyori.adventure.text.format.NamedTextColor.WHITE)));

        //深度癔症（2026-10-31 修复）：**脱战判定直接沿用框架** {@link CombatStateComponent#inCombat()} ——
        //  周围 4 格有敌人 / 给敌人上状态 / 受敌方攻击都算战斗；最后一次刷新后 6 秒无刷新才算脱战。
        //  本被动不再自己数"5 秒"：那套本地计时与框架不一致（框架 6 秒，且不含"附近有敌人"）。
        if (combat == null || combat.inCombat()) {
            hysteriaTimer = 0;
            //进入战斗 ⇒ 立刻摘掉深度癔症的隐身（需求："直到再次进入战斗"）
            if (hysteriaActive) {
                hysteriaActive = false;
                if (buff != null) {
                    buff.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
            return;
        }
        if (++hysteriaTimer >= 20) {
            hysteriaTimer = 0;
            sante.increase(10);
            buff.applyPotionEffect(PotionEffectType.INVISIBILITY, 45, 0, true, false);
            hysteriaActive = true;
        }
    }

    /**
     * 受击回调（由 {@code start()} 里注册的 consumer 调）：抵消 1 点特殊值伤害。
     * <p>★ 2026-10-31：不再在这里清"脱战计时"—— 受击进入战斗由框架的
     * {@code listener/CombatStateListener} 统一刷新 {@link CombatStateComponent}，本被动只读结果。
     */
    private void onDamaged(Player source, double amount){
        sante.increase(1);
    }

    @Override
    public void stop()  {
        super.stop();
        //★ 按引用撤销订阅（消费方必须持有 addDamagedListener 的返回值）
        if (vitals != null && damagedEntry != null) {
            vitals.removeDamagedListener(damagedEntry);
        }
        damagedEntry = null;
        vitals = null;
        sante = null;
        buff = null;
        render = null;
        combat = null;
        hysteriaTimer = 0;
        hysteriaActive = false;
        //订阅方组件已随本实例一起销毁，把它们留下的登记一并丢掉（避免悬挂引用）
        resolveListeners.clear();
        bossbar = null;
    }

    /**
     * **叠加「创伤」**：按上限 clamp 后写回层数 —— **纯叠层，不做任何结算**。
     *
     * <h2>★ 为什么不再"满层就地结算"（2026-10-26 修 BUG）</h2>
     * 旧实现在这里调 {@code tryAutoResolveAtCap()}：层数一到 {@value #MAX_STACK_COUNT} 就结算一次
     * 扣 4 层 ⇒ 满层后再叠一发，数字**从 16 掉到 12**（与主武器那条显式结算叠加还能一次掉到 8），
     * 看起来像"上限只有 12"。
     * <p>现在的口径（需求原话）：**上限就是 16，满层后再叠不增也不减** ——
     * 叠层只负责 clamp；消耗只发生在显式 {@link #requestResolve()} 里（那是"强化普攻消耗 4 层"的被动机制，
     * 满层也照扣，别混为一谈）。
     *
     * <p>跨过 4 的倍数（4/8/12/16）时播对应档位音效，并请求一次热键栏重绘（主武器名字色按档位变）。
     */
    public void requestAddStackCount(@Nonnegative int amount){
        writeStacks(Math.clamp(stackCount + amount, 0, MAX_STACK_COUNT));
    }

    /**
     * **把「创伤」顶到上限，并结算一次**（湛蓝命运的「叠满创伤」走这条）。
     *
     * <p>与 {@link #requestAddStackCount(int)} 的差别只在**收尾的层数**：
     * 前者是"加多少算多少"；本方法是"结算归结算、层数照样顶满" ——
     * 需求要的形态是**满层 16/16** 同时当场兑现一次深度癔症（回 4 血 + 通知订阅方）。
     *
     * <p>顺序：先顶满并结算（这样订阅方在结算里读到的层数就是满层），结算完再把层数复位回满。
     * 复位只写层数、**不重复触发结算**，所以不会多给一份护盾。
     *
     * @return 本次是否真的结算了一次（层数本来就满、或结算条件不成立时为 {@code false}）
     */
    public boolean requestRefillStacks(){
        writeStacks(MAX_STACK_COUNT);
        boolean resolved = requestResolve();
        //层数复位：结算扣掉的 4 层补回来，最终停在满层
        writeStacks(MAX_STACK_COUNT);
        return resolved;
    }

    /**
     * **结算一次「深度癔症」**：层数不足 {@link #RESOLVE_COUNT} 则什么都不做（返回 {@code false}）。
     * <p>成立时按顺序：扣 {@value #RESOLVE_COUNT} 层 → 回 4 点生命 → 逐个通知订阅方。
     *
     * <p>★ **结算必扣 4 层，满层也一样** —— 这是被动本身的消耗机制（强化普攻拿 4 层创伤换
     * +5 真伤 / +7 SanTE / 回 4 血），不能因为"满层"就免单。
     * <p>需求里那条「满层后再叠加不增不减」约束的是**叠加**（{@link #requestAddStackCount(int)}：
     * 叠层只 clamp、不再顺手结算），不是结算 —— 两者是两件事，别合并。
     * <p>通知放在最后：订阅方读到的层数已是结算后的值。
     */
    public boolean requestResolve(){
        if(stackCount < RESOLVE_COUNT) return false;
        //结算必扣（满了也扣）：被动机制，不随层数满而免单
        writeStacks(stackCount - RESOLVE_COUNT);
        Player self = svc().self().player();
        if(self != null){
            //需求：强化普攻回 4 点生命（这里落在结算发生处，与武器侧解耦）
            self.setHealth(Math.min(self.getMaxHealth(), self.getHealth() + 4d));
        }
        //注：7 点特殊值伤害由主武器结算到目标身上（见 HeronMainWeapon）
        notifyResolveListeners();
        return true;
    }

    // ───────── 层数写入的唯一落点（clamp / 档位音效 / 重绘 都在这里）─────────

    /**
     * 写层数：唯一的写入点。变化时①按"跨过的档位"播音效（只在**上升**时播）②请求热键栏重绘。
     * <p>为什么收成一处：层数有三个写入者（主武器结算、左轮命中、湛蓝命运），
     * 音效与重绘若各写一遍必漏；收敛后"层数变了就一定有声有重绘"是结构保证。
     */
    private void writeStacks(int next){
        int before = stackCount;
        if(next == before){
            return;
        }
        stackCount = next;
        playTierSounds(tierOf(before), tierOf(next));
        if(render != null){
            //主武器名字色按档位变 ⇒ 必须自己请求重绘（主武器不在冷却时框架不会刷）
            render.requestRepaint();
        }
    }

    /**
     * 播放"新跨过的档位"音效：从 {@code fromTier + 1} 到 {@code toTier} 逐档播一遍。
     * <p>只在上升时响（下降 = 被结算消耗，静音，避免打架时噪音连成一片）；一次跨多档也能逐档补齐。
     */
    private void playTierSounds(int fromTier, int toTier){
        if(toTier <= fromTier){
            return;
        }
        Player player = svc().self().player();
        if(player == null){
            return;
        }
        for(int tier = fromTier + 1; tier <= Math.min(toTier, MAX_TIER); tier++){
            player.playSound(player.getLocation(), TIER_SOUNDS[tier - 1], 0.9f, TIER_PITCH[tier - 1]);
            if(tier >= MAX_TIER){
                //满层额外一层"完成"的泛音
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f);
            }
        }
    }

    /**
     * 层数 → 档位（纯函数）：不足 {@link #RESOLVE_COUNT} 层 = 0 档；否则 = 层数 / 4，封顶 {@value #MAX_TIER}。
     * <p>0 档 = 灰色；1~4 档 = 暗蓝 / 普通蓝 / 亮蓝 / 亮紫（见 {@link #tierColor(int)}）。
     */
    public static int tierOf(int stacks){
        if(stacks < RESOLVE_COUNT){
            return 0;
        }
        return Math.min(MAX_TIER, stacks / RESOLVE_COUNT);
    }

    /** 档位 → 主武器名字色（0 灰 / 1 暗蓝 / 2 普通蓝 / 3 亮蓝 / 4 亮紫）。 */
    public static int tierColor(int tier){
        return TIER_COLORS[Math.clamp(tier, 0, MAX_TIER)];
    }

    /** 当前档位（读口；主武器取名字色用）。 */
    public int tier(){
        return tierOf(stackCount);
    }

    /** 当前「创伤」层数（读口；诊断与自检用）。 */
    public int stackCount(){
        return stackCount;
    }

    /** 层数上限（读口）。 */
    public static int maxStackCount(){
        return MAX_STACK_COUNT;
    }

    /**
     * 登记一个「结算通知」订阅者（幂等）。
     *
     * <p>本被动是「深度癔症」这件事的唯一持有者，因此「它什么时候结算」也只能由它通知；
     * 订阅方（例如「湛蓝命运」）拿到通知后自己决定做什么 —— 本被动不认识任何技能类。
     *
     * @param owner    订阅者（诊断 / 撤销用；{@code null} 则忽略）
     * @param callback 收到通知时调用（参数 = 本被动自己；{@code null} 则忽略）
     * @return 本次登记对应的实例（用 {@link #removeResolveListener} 按引用撤销）；参数为 null 时回 {@code null}
     */
    public ResolveListener addResolveListener(RoleComponent owner, Consumer<HeronTraumaPassive> callback){
        if(owner == null || callback == null) return null;
        ResolveListener entry = new ResolveListener(owner, callback);
        if(!resolveListeners.contains(entry)) resolveListeners.add(entry);
        return entry;
    }

    /** 撤销登记（按引用相等；不在名单里则 no-op）。 */
    public boolean removeResolveListener(ResolveListener entry){
        return entry != null && resolveListeners.remove(entry);
    }

    /** 逐个通知（对名单取快照后再遍历：通知途中的增删不影响本次）。 */
    private void notifyResolveListeners(){
        for(ResolveListener entry : List.copyOf(resolveListeners)){
            entry.callback().accept(this);
        }
    }
}
