package com.shadowHunterRolesPlugin.roleComponent.custom.lin.passive;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinVfx;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.NamespacedKey;

import java.util.List;

/**
 * 「林」被动之三：**殉道**。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>当特殊值归零时，<b>林不受到特殊值惩罚</b>；</li>
 *   <li><b>永久扣除林 4 点生命上限</b>；</li>
 *   <li>同时<b>减少林 15 点血量</b>；</li>
 *   <li>actionbar 出现<b>红色加粗的「==殉道==」</b>字样；</li>
 *   <li><b>回复林的特殊值到 50</b>；</li>
 *   <li>林周身出现<b>爆炸粒子</b>，同时林身后出现<b>巨大的 4 格高白色十字架粒子</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一：与框架内建「归零惩罚」的正面冲突（已申报用户，选"压制"路线）</h2>
 * 框架给**每个角色无条件**装了 {@code DefaultSanTEZeroPunishment}
 * （见 {@code registry/RoleLoader#withBuiltIns}），它同样是"特殊值归零"的订阅者：
 * 归零 ⇒ 施加 STUN 100 刻 + 恐惧标题 + 每 40 刻一跳 1/3 上限真伤。
 * 而需求第 1 条要的是"林**不受到**特殊值惩罚"。
 * <p>框架是禁改区（不得改 {@code roleComponent/builtin/}），只能在角色侧绕。采用的路线：
 * <ol>
 *   <li><b>抢在归零之前把它抬回去</b>：本组件在 {@link SanTEComponent} 的变更监听里
 *       （注册序在装配表中靠后 ⇒ 通知序靠后，见下）把特殊值立刻设回
 *       {@value #RESTORE_SANTE}；</li>
 *   <li><b>逐刻压制</b>：内建惩罚期间它会在自己的 {@code update()} 里每刻 {@code sante.set(0)}，
 *       因此本组件也在 {@code update()} 里每刻把自己拉回 ≥{@value #RESTORE_SANTE}
 *       （谁后写谁生效 —— 本组件装配在后 ⇒ 本组件的 {@code update()} 后跑）。</li>
 * </ol>
 * <p><b>如实申报</b>：本手法<b>不能 100% 消掉</b>内建惩罚的"瞬时一次性表现" ——
 * 归零那一跳内建仍会打出一次 STUN 与恐惧标题（它起的是 {@code 40L} 周期任务，
 * 第 1 跳 delay 0）。能压住的是<b>持续部分</b>（每刻被钉的 0、以及后续的真伤跳数被压到最少）。
 * 若要 100% 干净，须上游给"角色可声明豁免归零惩罚"的能力 —— 那是框架改动，不在本次范围内。
 *
 * <h2>★ 口径申报二：生命上限惩罚为什么"累积但换角色清零"</h2>
 * 需求"永久扣除林 4 点生命上限"里的"永久"，按用户当面确认的口径 =
 * <b>本次在役期间累计，换角色即清零</b>（不是跨局持久化）。
 * <p>实现：每次殉道把 {@link #martyrCount} +1，然后以**同一把键**
 * （{@link #MARTYRDOM_KEY}）调用 {@link VitalsComponent#applyHealthModifier} ——
 * 同键重写 ⇒ 上限恰好是 {@code 40 − 4 × 次数}（不会叠成一堆修饰符）；
 * {@link #stop()}（换角色 / 下线 / 清角色都会走到）里 {@code removeHealthModifier(同一把键)} ⇒ 清零。
 * <p>★ 为什么不用生命组件那把 {@code role_health_modifier}：那把是"角色上限"（40，恒在），
 * 本类这把是"殉道惩罚"。两把键互不覆盖（参考先例 {@code RedEvolutionPassive}）。
 *
 * <h2>★ 口径申报三：扣 15 点血不能用真伤</h2>
 * "减少林 15 点血量"若走真伤，会触发受击无敌帧 / 死亡判定 / 击杀归属等一串副作用
 * （而且林正好在 15 血以下时会直接被打死 —— 那不是"减血"，那是"自杀"）。
 * 正解 = 直接 {@code owner.setHealth(max(0.5, 当前血量 − 15))}：
 * 保留 0.5 血的下限，语义就是"扣 15 点血"本身，不夹带其它事件。
 */
public class LinMartyrPassive extends PassiveSkill {

    /** **本组件的登记 id**。 */
    public static final String ID = "lin_passive_martyr";

    /** 每次殉道扣除的生命上限（需求："永久扣除林4点生命上限"）。 */
    public static final double HEALTH_CAP_PENALTY = 4.0d;

    /** 每次殉道同时减少的血量（需求："减少林15点血量"）。 */
    public static final double HEALTH_LOSS = 15.0d;

    /** 殉道后特殊值回复到的值（需求："回复林的特殊值到50"）。 */
    public static final int RESTORE_SANTE = 50;

    /** 扣血后的血量下限（避免"减少 15 点血"把玩家直接抹死）。 */
    private static final double MIN_HEALTH_AFTER_LOSS = 0.5d;

    /**
     * **殉道生命上限修饰符的键**（本类私有 —— 与生命组件那把 {@code role_health_modifier} 是两把）。
     * <p>同键重写实现"累积但只有一条修饰符"，换角色时按同一把键精确摘除。
     */
    private static final NamespacedKey MARTYRDOM_KEY = KeyFactory.Registry.of("lin_martyrdom_penalty");

    private SanTEComponent sante;
    private VitalsComponent vitals;
    private BuffComponent buff;

    /** 本组件在 {@code SanTEComponent} 上的监听登记（{@code stop()} 按引用撤销）。 */
    private SanTEComponent.Listener santeListener;

    /** **真值：本次在役期间已殉道的次数**（上限惩罚 = 次数 × 4）。 */
    private int martyrCount;

    /** 防重入：归零回调与逐刻压制在同一次归零里只处理一次"殉道"。 */
    private boolean resolving;

    public LinMartyrPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符（无栏位 ⇒ 天然不占热键栏）。 */
    public static final class Specification extends PassiveSkill.Specification<LinMartyrPassive> {

        public Specification() {
            super(Component.text("殉道"),
                    List.of(
                            Component.text("特殊值归零时不受到特殊值惩罚"),
                            Component.text("但永久扣除4点生命上限，同时减少15点血量"),
                            Component.text("回复特殊值到50，并留下白色十字架与爆炸粒子"),
                            Component.text("生命上限的扣除在本次在役期间累计，换角色即清零")
                    ));
            requires(SanTEComponent.class).requires(VitalsComponent.class).requires(BuffComponent.class);
        }

        @Override
        public LinMartyrPassive create(String id, ComponentServicesPort services) {
            return new LinMartyrPassive(id, services, this);
        }
    }

    @Override
    public void start() {
        sante = svc().components().get(SanTEComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
        buff = svc().components().get(BuffComponent.class);
        martyrCount = 0;
        resolving = false;
        if (sante != null) {
            santeListener = sante.addListener(this, this::onSanTEChange);
        }
    }

    /**
     * 停用：① 撤销监听；② **摘掉殉道带来的上限惩罚**（需求口径："换角色即清零"）；
     * ③ 复位本地计数。
     */
    @Override
    public void stop() {
        if (sante != null && santeListener != null) {
            sante.removeListener(santeListener);
            santeListener = null;
        }
        if (vitals != null) {
            vitals.removeHealthModifier(MARTYRDOM_KEY);
        }
        martyrCount = 0;
        resolving = false;
    }

    /**
     * 每刻：**压制内建归零惩罚**（口径申报一）。
     *
     * <p>内建 {@code DefaultSanTEZeroPunishment} 在惩罚期间每刻把特殊值钉 0；
     * 本组件装配序靠后 ⇒ 本方法后跑 ⇒ 把它拉回 {@value #RESTORE_SANTE}，
     * 于是下一次内建钉 0 与本组件拉回之间只存在极短窗口（玩家不可见）。
     */
    @Override
    public void update() {
        if (sante == null) {
            return;
        }
        // 只在"确实被钉到低位"时拉回 —— 不干扰正常的高位波动
        if (sante.current() < RESTORE_SANTE && martyrCount > 0) {
            sante.set(RESTORE_SANTE);
        }
    }

    /**
     * **特殊值变更回调**：值归零 ⇒ 触发一次殉道。
     *
     * <p>★ 注意通知序：内建的归零惩罚监听在本组件之前注册（装配表靠前），
     * 因此它先收到这次归零并起任务；本组件随后把值抬回 {@value #RESTORE_SANTE}。
     */
    public void onSanTEChange(SanTEComponent.Change change) {
        if (change == null || resolving) {
            return;
        }
        if (change.current() > 0) {
            return;
        }
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return;
        }
        resolving = true;
        try {
            runMartyrdom(owner);
        } finally {
            resolving = false;
        }
    }

    /**
     * **执行一次殉道**（需求逐条）。
     *
     * @param owner 林（必然在线且存活 —— 由调用方守卫）
     */
    private void runMartyrdom(Player owner) {
        // ① 累计殉道次数 ⇒ 上限惩罚 = 次数 × 4（同键重写，不叠修饰符）
        martyrCount++;
        if (vitals != null) {
            double cap = VitalsComponent.BASE_MAX_HEALTH - HEALTH_CAP_PENALTY * martyrCount;
            // applyHealthModifier 的入参是"上限总量"（内部算 cap − BASE_MAX_HEALTH）
            vitals.applyHealthModifier(MARTYRDOM_KEY, Math.max(1d, cap));
        }

        // ② 减少 15 点血量（直接写生命，不走真伤 —— 见口径申报三）
        double current = owner.getHealth();
        owner.setHealth(Math.max(MIN_HEALTH_AFTER_LOSS, current - HEALTH_LOSS));

        // ③ 回复特殊值到 50
        if (sante != null) {
            sante.set(RESTORE_SANTE);
        }

        // ④ actionbar：红色加粗的「==殉道==」
        owner.sendActionBar(Component.text("==殉道==", NamedTextColor.RED, TextDecoration.BOLD));

        // ⑤ 观感：周身爆炸粒子 + 身后 4 格高白色十字架 + 音效
        Location at = owner.getLocation().clone();
        LinVfx.martyrBurst(owner.getWorld(), at);
        LinVfx.martyrCross(owner.getWorld(), at, owner.getEyeLocation().getDirection());
        LinSound.martyrSound(owner.getWorld(), at);
    }

    // ───────── 读口 / 纯函数（离线可验）─────────

    /** 本次在役期间已殉道的次数。 */
    public int martyrCount() {
        return martyrCount;
    }

    /**
     * ★ **某次殉道后的生命上限总量**（**纯函数** ⇒ 可离线穷举）。
     *
     * @param martyrCount 已殉道次数（负值按 0）
     * @return 上限总量（下限 1，避免归零/负上限）
     */
    public static double healthCapAfter(int martyrCount) {
        int times = Math.max(0, martyrCount);
        return Math.max(1d, VitalsComponent.BASE_MAX_HEALTH - HEALTH_CAP_PENALTY * times);
    }

    /**
     * ★ **某次殉道后的剩余血量**（**纯函数** ⇒ 可离线穷举）。
     *
     * @param currentHealth 扣血前的血量
     * @return 扣血后的血量（下限 {@value #MIN_HEALTH_AFTER_LOSS}）
     */
    public static double healthAfterLoss(double currentHealth) {
        return Math.max(MIN_HEALTH_AFTER_LOSS, currentHealth - HEALTH_LOSS);
    }

    /** 当前 tick（不参与纯函数，仅便于外部探针读取时统一口径）。 */
    static int currentTick() {
        return Bukkit.getCurrentTick();
    }
}
