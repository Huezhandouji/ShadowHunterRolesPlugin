package com.shadowHunterRolesPlugin.roleComponent.custom.lin.skill;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.HotbarRenderComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinSound;
import com.shadowHunterRolesPlugin.roleComponent.custom.lin.LinVfx;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 「林」技能之一：**三重打击**（石英）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>加强下<b>三次攻击</b>，每次攻击额外造成 <b>3 点真实伤害</b>和 <b>3 点特殊值伤害</b>；</li>
 *   <li>每一次攻击附带<b>白色爆炸粒子</b>，和 <b>TNT 爆炸音效</b>；</li>
 *   <li>玩家最多同时拥有 <b>3 层</b>强化攻击层数；</li>
 *   <li>所有层数在 <b>5 秒后消失</b>；</li>
 *   <li><b>每一次攻击将刷新所有层数消失时间</b>；</li>
 *   <li>CD 8 秒（技能释放进入 CD）。</li>
 * </ol>
 *
 * <h2>★ 口径申报零：2026-10-08 的观感改版（用户追加）</h2>
 * <ul>
 *   <li><b>释放音</b>：由 TNT 爆炸声改为<b>信标生成声</b>（{@code BLOCK_BEACON_ACTIVATE}）；</li>
 *   <li><b>释放粒子</b>：不再画"林身上的爆炸粒子"，改为在<b>林身体中间</b>（{@code +1 格}）
 *       生成一团<b>不死图腾爆炸粒子</b>；</li>
 *   <li><b>命中粒子</b>：由白色爆炸粒子改为<b>暴击粒子</b>（{@code CRIT}）+ 白色石英尘衬底；
 *       TNT 爆炸音效保持不变（那是"命中"的听觉落点，用户没改）。</li>
 * </ul>
 * ⚠ 这次改版**顺带移除了 {@code Particle.FLASH} 的使用** —— 它正是 2026-10-08 那起
 * {@code missing required data class org.bukkit.Color} 崩溃的源头（漏传 data ⇒ 整个角色被下线）。
 * 详见 {@link LinVfx#tripleStrikeBurst}。
 *
 * <h2>★ 口径申报一：层数放在技能里，"攻击"由主武器回调</h2>
 * 插件只把攻击事件投递给**主武器**组件，技能收不到 ⇒ 层数归本技能私有持有，
 * 由主武器在命中后调 {@link #onBasicAttackLanded} 来消费。
 * 依赖方向单向：主武器 → 技能（不成环，不会触发装配失败）。
 *
 * <h2>★ 口径申报二："刷新消失时间"的正确读法</h2>
 * 需求有两句话看似矛盾："所有层数在 5 秒后消失" 与 "每一次攻击将刷新所有层数消失时间"。
 * 按玩家实际意图取<b>后者的字面语义</b>：
 * <b>每次攻击都把整个窗口推到"现在 + 5 秒"</b>（不管还剩几层）。
 * ⇒ 只要还在持续进攻，窗口就一直续着；一旦停手 5 秒，剩余层数才一起消失。
 * <p>注意这与"每次攻击重置为自己的 5 秒"不同：<b>刷新是全局的、按次数的</b>，
 * 所以 3 层可以横跨多次刷新，但每层只生效一次（被消费即减一）。
 *
 * <h2>★ 口径申报三：层数 = 剩余强化次数，不是"伤害翻倍数"</h2>
 * "最多同时拥有 3 层"= 最多同时挂着 3 次强化机会。
 * 再次施放**不叠到 6**，而是把层数<b>补到 3</b>并重开窗口（"最多"是硬上限）。
 *
 * <h2>★ 口径申报四：为什么自己请求重绘</h2>
 * 层数的倒计时发生在"技能不在冷却"的时段（5 秒窗口 &lt; 8 秒 CD 之外的窗口期更明显），
 * 而框架只在"组件冷却中"才每刻自动刷
 * ⇒ 不自己 {@code render.requestRepaint()}，玩家看到的层数 / 秒数会**永远停在第一帧**（不报错）。
 */
public class LinTripleStrikeSkill extends Skill {

    /** **本组件的登记 id**。 */
    public static final String ID = "lin_skill_tripleStrike";

    /** 强化层数上限（需求："玩家最多同时拥有3层强化攻击层数"）。 */
    public static final int MAX_STACKS = 3;

    /** 窗口时长（刻）：5 秒（需求："所有层数在5秒后消失"）。 */
    public static final int WINDOW_TICKS = 100;

    /** 每次强化击追加的真实伤害（需求："额外造成3点真实伤害"）。 */
    public static final double BONUS_TRUE_DAMAGE = 3.0d;

    /** 每次强化击追加的特殊值伤害（需求："3点特殊值伤害"）。 */
    public static final int BONUS_SANTE_DAMAGE = 3;

    /** 声明冷却（刻）：8 秒（需求："CD-8"）。 */
    public static final int COOLDOWN_TICKS = 160;

    private VitalsComponent vitals;
    private SanTEComponent sante;
    private EnergyComponent energy;
    private BuffComponent buff;

    /**
     * 渲染组件（**只用来请求重绘**）。
     *
     * <p>★ 为什么需要它：层数的到期倒计时是在<b>非冷却期</b>推进的，
     * 框架只在"组件冷却中"才每刻自动刷 ⇒ 见类注释口径申报四。
     */
    private HotbarRenderComponent render;

    /** 剩余强化层数（0 ⇒ 没有强化在身）。 */
    private int stacks;

    /** 窗口到期刻（绝对刻；{@code -1} = 无窗口）。 */
    private int windowUntilTick = -1;

    public LinTripleStrikeSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<LinTripleStrikeSkill> {

        public Specification() {
            super(Component.text("三重打击"),
                    List.of(
                            Component.text("加强下三次攻击，每次额外造成3点真实伤害和3点特殊值伤害"),
                            Component.text("释放时周身爆开不死图腾粒子并响起信标生成声"),
                            Component.text("强化攻击命中时附带暴击粒子与TNT爆炸音效"),
                            Component.text("最多同时拥有3层强化攻击层数"),
                            Component.text("所有层数在5秒后消失，每一次攻击将刷新所有层数消失时间"),
                            Component.text("冷却：8秒")
                    ),
                    COOLDOWN_TICKS,
                    0,
                    Material.QUARTZ);
            requires(VitalsComponent.class).requires(SanTEComponent.class)
                    .requires(EnergyComponent.class).requires(BuffComponent.class);
        }

        @Override
        public LinTripleStrikeSkill create(String id, ComponentServicesPort services) {
            return new LinTripleStrikeSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        vitals = svc().components().get(VitalsComponent.class);
        sante = svc().components().get(SanTEComponent.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        render = getComponent(HotbarRenderComponent.class);
        stacks = 0;
        windowUntilTick = -1;
    }

    /** 停用：清掉在身的强化层数（换角色后不该还留着强化）。 */
    @Override
    public void stop() {
        stacks = 0;
        windowUntilTick = -1;
    }

    /**
     * 每刻：① 窗口到期 ⇒ 清空层数；② 还有层数 ⇒ 请求重绘（让层数 / 秒数动起来）。
     */
    @Override
    public void update() {
        Player owner = svc().self().player();
        if (owner == null || !owner.isOnline()) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (stacks > 0 && isWindowExpired(windowUntilTick, now)) {
            stacks = 0;
            windowUntilTick = -1;
        }
        // ★ 有层数在身 ⇒ 名称后缀在倒计时，必须自己请求重绘（框架只在冷却期自动刷）
        if (stacks > 0 && render != null) {
            render.requestRepaint();
        }
    }

    /** 释放：**补满到 3 层**并重开 5 秒窗口（"最多 3 层"是硬上限，不叠到 6）。 */
    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        stacks = MAX_STACKS;
        windowUntilTick = Bukkit.getCurrentTick() + WINDOW_TICKS;
        // 观感（2026-10-08 改版）：施放时在林**身体中间**炸一团不死图腾粒子；
        // ★ 不再画"林身上的爆炸粒子"（改为命中时才出暴击粒子）。
        Location bodyMid = owner.getLocation().clone().add(0d, 1d, 0d);
        LinVfx.tripleStrikeTotemBurst(owner.getWorld(), bodyMid);
        // 释放音（2026-10-08 改版）：信标生成声
        LinSound.tripleStrikeCastSound(owner.getWorld(), owner.getLocation());
        if (render != null) {
            render.requestRepaint();
        }
        // 成功施放才起冷却
        startCooldown();
    }

    /**
     * **普攻命中回调**（由 {@link com.shadowHunterRolesPlugin.roleComponent.custom.lin.mainWeapon.LinIronBloodMainWeapon}
     * 在命中后调用）。
     *
     * <p>两条语义按顺序执行：
     * <ol>
     *   <li><b>刷新窗口</b>（需求："每一次攻击将刷新所有层数消失时间"）——
     *       ★ 只在"还有层数在身"时刷新：层数已耗尽（0 层）时的普攻不该凭空开出新窗口；</li>
     *   <li><b>消费一层</b>并追加强化效果（3 真实 + 3 特殊值 + 白色爆炸粒子 + TNT 音效）。</li>
     * </ol>
     *
     * @param attacker 攻击者（= 林的玩家）
     * @param victim   被命中者
     */
    public void onBasicAttackLanded(Player attacker, Player victim) {
        if (stacks <= 0 || victim == null || attacker == null) {
            return;
        }
        int now = Bukkit.getCurrentTick();
        if (isWindowExpired(windowUntilTick, now)) {
            stacks = 0;
            windowUntilTick = -1;
            return;
        }
        // ① 刷新窗口（"每一次攻击将刷新所有层数消失时间"）
        windowUntilTick = now + WINDOW_TICKS;
        // ② 消费一层
        stacks--;

        // ③ 追加效果：3 点真实伤害 + 3 点特殊值伤害
        if (vitals != null) {
            vitals.trueDamage(victim, attacker, BONUS_TRUE_DAMAGE);
        }
        if (sante != null) {
            sante.decreaseSanTE(victim.getUniqueId(), BONUS_SANTE_DAMAGE);
        }
        // ④ 观感：暴击粒子 + TNT 爆炸音效（2026-10-08：白色爆炸粒子已改为暴击粒子）
        //    2026-10-31：音量上调并叠层；最后一层（消费后归零）改用带雷/钟/晶鸣的收尾重音
        LinVfx.tripleStrikeBurst(victim.getWorld(), victim.getLocation().clone().add(0d, 1d, 0d));
        if (stacks == 0) {
            LinSound.tripleStrikeFinisherSound(victim.getWorld(), victim.getLocation());
        } else {
            LinSound.tripleStrikeExplosionSound(victim.getWorld(), victim.getLocation());
        }

        if (render != null) {
            render.requestRepaint();
        }
    }

    /**
     * **窗口是否已过期**（**纯函数** ⇒ 可离线穷举边界）。
     *
     * @param untilTick 到期刻（{@code -1} / 负数 = 无窗口）
     * @param now       当前刻
     */
    public static boolean isWindowExpired(int untilTick, int now) {
        return untilTick < 0 || now >= untilTick;
    }

    /** 剩余强化层数（读口 / 探针）。 */
    public int stacks() {
        return stacks;
    }

    /** 窗口剩余刻（无窗口 ⇒ 0）。 */
    public int windowRemainingTicks() {
        if (windowUntilTick < 0) {
            return 0;
        }
        return Math.max(0, windowUntilTick - Bukkit.getCurrentTick());
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    // ───────── 外观：在役（有层数）⇒ 常亮 + 名称后缀 ─────────

    /**
     * 基类三态画法 + **有层数时叠附魔光效**（工程规范第 1 / 4 条）。
     *
     * <p>★ 先 {@code super.buildItem()} —— 识别键写在基类那一步。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        if (stack == null) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (stacks > 0) {
            // 在役 ⇒ 常亮光效（一眼看出"身上还挂着强化"）
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
            return stack;
        }
        // 只差能量（冷却已走完、其它条件都满足）⇒ 结构空位也发光，与"冷却中"区分开
        if (!isCoolingDown() && canUse() && currentEnergy() < getEnergyCost()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
