package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import com.shadowHunterRolesPlugin.manager.BuffManager;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * `BuffComponent` 的**组件操作面**离线单测。
 *
 * <h2>能离线跑的原因：最小测试替身（两件）</h2>
 * <p>① 生产里 {@code BuffManager} 由容器以 {@code new BuffManager(player, this, scheduler)} 构造 ⇒ **需要活 Player**；
 * 但它的**构造器只把三个实参存进字段**（零解引用），因此用 {@code super(null, null, null)} 构造的替身
 * **不需要 Player**。本类的 {@link StubBuffManager} 在此之上把**组件实际调用的那几个方法**换成内存实现，
 * 组件于是可离线构造（清单随组件加口而长：{@code addBuff} · {@code removeBuff} · {@code hasBuff} ·
 * {@code getRemainingTicks} · 两个闸门 · {@code clearDebuffs}）。
 * <p>② 真正的硬阻断其实在**类初始化**：{@code BuffManager} 的静态常量
 * {@code BUFF_MOVEMENT_SPEED_MODIFIER_KEY} 走 {@code KeyFactory.Registry.of(...)}，
 * 而 {@code KeyFactory} 的实现在插件 {@code onEnable} 才 install ⇒ 不装桩时**连类都加载不了**
 * （实测：{@code NoClassDefFoundError}）。因此本类在 {@link BeforeClass} 里 install 一个
 * **最小 KeyFactory 桩**（{@code new NamespacedKey("shadowhunterroles", key)}，离线可构造）。
 * <p>该桩的副作用（如实申报）：{@code KeyFactory.Registry} 是全局静态，本类的 install 对本 JVM
 * 内其它测试同样生效。**但不得把它当作别人的前提**：凡构造带静态 {@code NamespacedKey} 常量的组件的
 * 套件（现算：{@link VitalsComponentKilledListenerTest} 与 {@code EvolutionPassiveLevelUpListenerTest}）
 * 都各自在 {@code @BeforeClass} 里装同一份桩 —— 否则"谁先跑"会决定"谁能不能跑"（实测踩到过：
 * 套件顺序一变，那些套件整片红）。
 * <p>替身的语义边界（如实申报）：替身**不**模拟真实 {@code BuffManager} 的到期判定（tick 递减）/
 * STUN 属性修饰符 / 药水施加 / **原版负面药水的枚举与移除** / {@code addImmune} 的"进场先净化"那一步
 * —— 那些需要活玩家或活注册表，属运行级读数。
 * <p><b>已建模的部分</b>（本次新增）：{@code addBuff} 的**判定逻辑**（{@code null} 早退 · 免疫早退 ·
 * 取最大时长）逐条照搬生产实现 —— 它一个 {@code Player} 都不碰，因此"这次施加到底有没有落地"
 * （{@code BuffComponent#add} / {@code #addTo} 的返回值）这条读数可以离线冻结。
 *
 * <h2>哪些动词能离线驱动</h2>
 * 自侧 10 个 + 跨玩家 6 个 = <b>16 个动词全部经替身可达</b>（其中 4 个药水动词只在"参数非法"分支上可达）。
 * 逐条需要**活服务端**的例外：
 * <ul>
 *   <li>{@code clear} —— 只覆盖**空账本**路径：账本非空时它会逐个
 *       {@code player.removePotionEffect(...)} ⇒ 需要活 Player，因此归运行级。</li>
 *   <li>{@code clear_debuff} / {@code clear_debuff_on} —— 组件侧的转发与插件侧账本语义已全覆盖（替身把
 *       {@code clearDebuffs()} 换成内存实现）；但生产 {@code BuffManager.clearDebuffs()} 的
 *       **原版药水那一半**（{@code HARMFUL} 分类的枚举与移除）离线不可达：{@code PotionEffectType}
 *       需要活服务端的注册表才能初始化（实测：离线引用任意常量即 {@code ExceptionInInitializerError}）
 *       ⇒ 那半边同样归运行级。</li>
 *   <li>{@code effect} / {@code effect_to} / {@code uneffect} —— 参数合法时必然要查服务端注册表
 *       （{@code potionTypeOf} 逐名解析），归运行级；离线只冻"**参数个数**不符 ⇒ 在查注册表**之前**
 *       就拒绝"这条判据。</li>
 *   <li>{@link BuffComponent#removePotionEffect(PotionEffectType)} —— 同一堵墙：{@code PotionEffectType}
 *       在本基线上是 {@code abstract} 类，静态常量与派生实例都会触发 {@code <clinit>} ⇒ 离线拿不到实例；
 *       且账本 {@code appliedPotionTypes} 是 {@code private}、无可注入的缝 ⇒ 离线只冻 {@code null} 入参分支。</li>
 * </ul>
 */
public class BuffComponentOperationTest {

    /** **离线测试基设**：装一个最小 {@code KeyFactory} 桩，让带静态 {@code NamespacedKey} 常量的类可被加载。 */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    /** **最小测试替身**：buff 记账表换成内存表（不碰 Player、不碰实例）。 */
    private static final class StubBuffManager extends BuffManager {

        private final Map<BuffType, Integer> ticks = new LinkedHashMap<>();

        StubBuffManager() {
            super(null, null, null); // 基类构造只赋三个字段 ⇒ 零解引用
        }

        @Override
        public boolean addBuff(BuffType type, int durationTicks) {
            //把生产的**判定逻辑**照搬（null / 免疫早退 / 取最大时长）——它不碰 Player，因此离线可跑。
            //不照搬的部分：STUN 的属性修饰符、原版药水施加、以及 addImmune 的"进场先 clearDebuffs()"那一步
            //（那一步会连带清账本，会让"免疫之前的 STUN 还在不在"这条读数与真实实现分道扬镳，
            //  而它需要真实账本语义 —— 离线只冻结"免疫挡下新来的负面 buff"这条闸门）。
            if (type == null) {
                return false;
            }
            if (type != BuffType.IMMUNE && ticks.containsKey(BuffType.IMMUNE)) {
                return false;
            }
            Integer existing = ticks.get(type);
            if (existing != null) {
                if (durationTicks > existing) {
                    ticks.put(type, durationTicks);
                    return true;
                }
                return false;
            }
            ticks.put(type, durationTicks);
            return true;
        }

        /**
         * 移除缓冲的内存实现（本次新增）：生产的 {@code removeBuff} 会去动玩家属性修饰符与
         * {@code PotionEffectType} 常量 —— 后者**离线连类都初始化不了**
         * （实测 {@code ExceptionInInitializerError: PotionEffectType.<clinit>}，
         * 因为它在静态块里向服务端注册表要效果类型），因此离线必须换成纯账本操作。
         */
        @Override
        public void removeBuff(BuffType type) {
            ticks.remove(type);
        }

        @Override
        public boolean hasBuff(BuffType type) {
            return ticks.containsKey(type);
        }

        @Override
        public long getRemainingTicks(BuffType type) {
            return ticks.getOrDefault(type, 0);
        }

        @Override
        public boolean canCastSkill() {
            return !hasBuff(BuffType.STUN) && !hasBuff(BuffType.SILENCE);
        }

        @Override
        public boolean canUseMainWeapon() {
            return !hasBuff(BuffType.STUN);
        }

        /**
         * 清负面效果的内存实现：按 {@link BuffType#isDebuff()} 枚举（与生产 `BuffManager.clearDebuffs()`
         * 的插件侧**同一判据**）⇒ 本替身顺带把"哪些 buff 算负面"这条定义也纳入离线覆盖。
         * <p>不模拟生产方法里的原版药水那一半（那需要活玩家 + 活注册表，见类 javadoc）。
         */
        @Override
        public int clearDebuffs() {
            int cleared = 0;
            for (BuffType type : new ArrayList<>(ticks.keySet())) {
                if (type.isDebuff()) {
                    ticks.remove(type);
                    cleared++;
                }
            }
            return cleared;
        }
    }

    /** 玩家面替身：只提供 {@code player()} 的**空值**（空账本下不会被解引用）。 */
    private static final class StubSelf implements SelfPort {

        @Override
        public Player player() {
            return null;
        }

        @Override
        public UUID id() {
            return UUID.fromString("00000000-0000-0000-0000-000000000001");
        }
    }

    private static BuffComponent component() {
        return new BuffComponent("buffs",
                new ComponentServicesPort(new StubSelf(), null, null),
                new StubBuffManager());
    }

    // ───────── 合法读（闸门两个） ─────────

    /** 技能闸门：无 buff ⇒ `true`；`STUN` 后 ⇒ `false`（读的是既有强类型方法）。 */
    @Test
    public void canCastReadsTheExistingGate() {
        BuffComponent buffs = component();
        assertEquals("true", buffs.onOperationCommand("can_cast"));
        assertEquals("50", buffs.onOperationCommand("add STUN 50"));
        assertEquals("false", buffs.onOperationCommand("can_cast"));
    }

    /** 主武器闸门：无 buff ⇒ `true`；`STUN` 后 ⇒ `false`。 */
    @Test
    public void canWeaponReadsTheExistingGate() {
        BuffComponent buffs = component();
        assertEquals("true", buffs.onOperationCommand("can_weapon"));
        assertEquals("50", buffs.onOperationCommand("add STUN 50"));
        assertEquals("false", buffs.onOperationCommand("can_weapon"));
    }

    // ───────── 合法写 + 合法读（has / remaining） ─────────

    /** `add` 回**写后状态**；`has` / `remaining` 读同一份表（含"无该 buff"的 0）。 */
    @Test
    public void addHasAndRemainingShareOneTable() {
        BuffComponent buffs = component();
        assertEquals("写后状态 = 剩余刻", "120", buffs.onOperationCommand("add SILENCE 120"));
        assertEquals("true", buffs.onOperationCommand("has SILENCE"));
        assertEquals("120", buffs.onOperationCommand("remaining SILENCE"));
        assertEquals("false", buffs.onOperationCommand("has STUN"));
        assertEquals("0", buffs.onOperationCommand("remaining STUN"));
    }

    /** `0` 是合法的写入值（与既有 add 语义一致）。 */
    @Test
    public void zeroTicksIsAValidWrite() {
        BuffComponent buffs = component();
        assertEquals("0", buffs.onOperationCommand("add IMMUNE 0"));
        assertEquals("true", buffs.onOperationCommand("has IMMUNE"));
    }

    // ───────── 账本读口（count / clear） ─────────

    /** `count` / `clear` 读同一份账本；空账本 ⇒ `0`（非空路径需活 Player，见类 javadoc）。 */
    @Test
    public void countAndClearReportTheEmptyLedger() {
        BuffComponent buffs = component();
        assertEquals("0", buffs.onOperationCommand("count"));
        assertEquals("写后状态 = 账本数", "0", buffs.onOperationCommand("clear"));
        assertEquals("0", buffs.onOperationCommand("count"));
    }

    // ───────── 清负面效果（clear_debuff） ─────────

    /**
     * `clear_debuff` 只清**负面** buff：`STUN` / `SILENCE` 被清掉且闸门恢复，
     * 免疫类 `IMMUNE` **不动**（它不是负面效果 —— 清了它，"给自己上免疫"就成了自毁）；
     * 再清一次 ⇒ `0`（幂等，无副作用）。
     */
    @Test
    public void clearDebuffClearsNegativeBuffsOnly() {
        BuffComponent buffs = component();
        assertEquals("100", buffs.onOperationCommand("add STUN 100"));
        assertEquals("100", buffs.onOperationCommand("add SILENCE 100"));
        assertEquals("0", buffs.onOperationCommand("add IMMUNE 0"));

        assertEquals("写后状态 = 清掉的条数", "2", buffs.onOperationCommand("clear_debuff"));
        assertEquals("false", buffs.onOperationCommand("has STUN"));
        assertEquals("false", buffs.onOperationCommand("has SILENCE"));
        assertEquals("闸门恢复", "true", buffs.onOperationCommand("can_cast"));
        assertEquals("免疫不受影响", "true", buffs.onOperationCommand("has IMMUNE"));
        assertEquals("再清一次无可清", "0", buffs.onOperationCommand("clear_debuff"));
    }

    // ───────── 未识别 / 拒绝（三态） ─────────

    /** 未知动词（含**大小写不符**与多带参数的无参动词）⇒ 未识别 ⇒ `null`，且不改状态。 */
    @Test
    public void unknownVerbAndNoArgVerbsWithArgumentsAreRejected() {
        BuffComponent buffs = component();
        assertNull("未知动词", buffs.onOperationCommand("reset"));
        assertNull("大写不符（动词大小写敏感）", buffs.onOperationCommand("CAN_CAST"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("can_cast 1"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("count 1"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("clear 1"));
        assertNull("无参动词不得带参数", buffs.onOperationCommand("clear_debuff 1"));
        assertEquals("拒绝不得改状态", "true", buffs.onOperationCommand("can_cast"));
    }

    /** 空 / 空白 / null payload ⇒ 未识别 ⇒ `null`（javadoc 写明）。 */
    @Test
    public void emptyPayloadIsRejected() {
        BuffComponent buffs = component();
        assertNull("null", buffs.onOperationCommand(null));
        assertNull("空串", buffs.onOperationCommand(""));
        assertNull("纯空白", buffs.onOperationCommand("   "));
    }

    /** 参数非法：未知 buff id（含**大小写不符**）· 缺参 · 多参 · 非数字 · 负数 · 溢出 ⇒ `null`。 */
    @Test
    public void malformedArgumentsAreRejected() {
        BuffComponent buffs = component();
        assertNull("未知 buff id", buffs.onOperationCommand("has NOPE"));
        assertNull("buff id 大小写不符（严格 valueOf）", buffs.onOperationCommand("has silence"));
        assertNull("缺参", buffs.onOperationCommand("has"));
        assertNull("缺参", buffs.onOperationCommand("add SILENCE"));
        assertNull("多参", buffs.onOperationCommand("add SILENCE 1 2"));
        assertNull("非数字", buffs.onOperationCommand("add SILENCE abc"));
        assertNull("负数", buffs.onOperationCommand("add SILENCE -1"));
        assertNull("溢出", buffs.onOperationCommand("add SILENCE 999999999999"));
        assertEquals("全部被拒 ⇒ 表仍为空", "false", buffs.onOperationCommand("has SILENCE"));
    }

    // ═════════════ 跨玩家面（本次新增）：把 buff / 原版药水加给**别人** ═════════════
    //
    // 能离线跑的原因：跨玩家解析有两条**包内可见**的测试缝（生产各只有一条实现）——
    //   ① BuffComponent#targetOf(Player)    目标玩家 → 他那份 buff 组件
    //   ② BuffComponent#onlinePlayer(String) 指令里的名字 → 在线玩家
    // 覆写这两条即可让整条链（公开方法 + 指令动词）离线跑起来，且**验的是组件自己的逻辑**
    // （目标无角色 ⇒ 拒绝、目标免疫 ⇒ 不施加、取最大时长…），不是测试自己的循环。

    /**
     * `Player` 替身：本测验只需要一个**身份令牌**（解析已被覆写，不会真的把它交给服务端）。
     * `Player` 是 200+ 方法的巨接口，手写实现没有意义 ⇒ 用 JDK 动态代理，默认值规则 =
     * 对象回 {@code null}、原始类型回 {@code 0}/{@code false}（因此 {@code addPotionEffect} 回 false）。
     */
    private static Player phantomPlayer(String name) {
        return (Player) java.lang.reflect.Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> name;
                    case "toString" -> "phantom:" + name;
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> args != null && args.length == 1 && proxy == args[0];
                    default -> defaultValueOf(method.getReturnType());
                });
    }

    /** 原始类型的默认值（对象一律 {@code null}）—— 动态代理必须给每个方法一个合法返回。 */
    private static Object defaultValueOf(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return 0;
    }

    /** 玩家面替身（**带回显**）：`applyPotionEffect` 会解引用 `self().player()`。 */
    private static final class SelfOfPlayer implements SelfPort {

        private final Player player;

        SelfOfPlayer(Player player) {
            this.player = player;
        }

        @Override
        public Player player() {
            return player;
        }

        @Override
        public UUID id() {
            return UUID.nameUUIDFromBytes(player.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * 可测的**施法方**组件：覆写那两条测试缝 ——
     * {@code targetOf} 只认"受害者令牌"（其余 ⇒ {@code null}，模拟"目标无角色"），
     * {@code onlinePlayer} 只认 {@code victim} / {@code roleless} 两个名字（其余 ⇒ {@code null}，模拟"不在线"）。
     */
    private static final class Caster extends BuffComponent {

        private final BuffComponent victim;
        private final Player victimToken;
        private final Player rolelessToken;

        Caster(BuffManager ledger, BuffComponent victim, Player victimToken, Player rolelessToken) {
            super("buffs", new ComponentServicesPort(null, null, null), ledger);
            this.victim = victim;
            this.victimToken = victimToken;
            this.rolelessToken = rolelessToken;
        }

        @Override
        BuffComponent targetOf(Player target) {
            return target == victimToken ? victim : null;   //rolelessToken ⇒ null（在线但没角色）
        }

        @Override
        Player onlinePlayer(String name) {
            if ("victim".equals(name)) {
                return victimToken;
            }
            if ("roleless".equals(name)) {
                return rolelessToken;
            }
            return null;                                    //不在线的名字
        }
    }

    /** 一次跨玩家测试的装具：施法方（跨玩家入口）+ 受害者（真账本替身）+ 一个"有角色但没被解析到"的令牌。 */
    private static final class CrossFixture {

        final Player victimToken = phantomPlayer("Victim");
        final Player rolelessToken = phantomPlayer("Roleless");

        final BuffComponent victim = new BuffComponent("buffs",
                new ComponentServicesPort(new SelfOfPlayer(victimToken), null, null),
                new StubBuffManager());

        final Caster caster = new Caster(new StubBuffManager(), victim, victimToken, rolelessToken);
    }

    // ───────── 公开方法：写 / 读 / 撤 ─────────

    /**
     * `addTo` 写进的是**目标自己那份账本**（不是施法方的）——
     * 因此目标的闸门、免疫、到期与"角色清除时回收"全部照常生效。
     */
    @Test
    public void addToReachesTheTargetsOwnLedger() {
        CrossFixture fx = new CrossFixture();
        assertTrue("本次施加真的落地", fx.caster.addTo(fx.victimToken, BuffType.STUN, 100));
        assertTrue("落在目标账本里", fx.victim.has(BuffType.STUN));
        assertFalse("施法方自己的账本没被写", fx.caster.has(BuffType.STUN));
        assertEquals("读回来的是目标的剩余刻", 100L, fx.caster.remainingTicksOn(fx.victimToken, BuffType.STUN));
    }

    /** 目标无角色 / 不在线 / 类型为 `null` ⇒ **不施加**，回 `false`（本插件 buff 没有可挂的账本）。 */
    @Test
    public void addToIsRejectedWhenThereIsNoTargetLedger() {
        CrossFixture fx = new CrossFixture();
        assertFalse("在线但无角色", fx.caster.addTo(fx.rolelessToken, BuffType.STUN, 100));
        assertFalse("目标为 null", fx.caster.addTo(null, BuffType.STUN, 100));
        assertFalse("类型为 null", fx.caster.addTo(fx.victimToken, null, 100));
        assertFalse("一条也没进目标账本", fx.victim.has(BuffType.STUN));
    }

    /** 目标处于 `IMMUNE` ⇒ 免疫挡下新来的负面 buff（`addTo` 回 `false`），且**不**绕过它直接写账本。 */
    @Test
    public void addToIsSwallowedByTheTargetsImmunity() {
        CrossFixture fx = new CrossFixture();
        assertTrue(fx.victim.add(BuffType.IMMUNE, 100));
        assertFalse("免疫挡下眩晕", fx.caster.addTo(fx.victimToken, BuffType.STUN, 100));
        assertFalse("眩晕没进账本", fx.victim.has(BuffType.STUN));
        assertTrue("眩晕没上 ⇒ 目标技能闸门仍开", fx.caster.targetOf(fx.victimToken).canCastSkill());
    }

    /** "取最大时长"语义下，`addTo` 的返回值如实反映"有没有变化"（这正是它回 boolean 的理由）。 */
    @Test
    public void addToReportsWhetherTheDurationActuallyChanged() {
        CrossFixture fx = new CrossFixture();
        assertTrue("首次 ⇒ 新增", fx.caster.addTo(fx.victimToken, BuffType.SILENCE, 200));
        assertFalse("更短 ⇒ 不产生变化（保留 200）", fx.caster.addTo(fx.victimToken, BuffType.SILENCE, 100));
        assertFalse("等长 ⇒ 不产生变化", fx.caster.addTo(fx.victimToken, BuffType.SILENCE, 200));
        assertTrue("更长 ⇒ 延长", fx.caster.addTo(fx.victimToken, BuffType.SILENCE, 300));
        assertEquals("读回来是延长后的值", 300L, fx.caster.remainingTicksOn(fx.victimToken, BuffType.SILENCE));
    }

    /** 读类对"目标无角色 / 目标为 null"照常回 `false` / `0`（读操作没有"拒绝"这回事）。 */
    @Test
    public void readsReportFalseAndZeroForUnresolvableTargets() {
        CrossFixture fx = new CrossFixture();
        fx.caster.addTo(fx.victimToken, BuffType.STUN, 100);
        assertTrue("目标有该 buff", fx.caster.hasOn(fx.victimToken, BuffType.STUN));
        assertFalse("无角色 ⇒ false", fx.caster.hasOn(fx.rolelessToken, BuffType.STUN));
        assertFalse("null ⇒ false", fx.caster.hasOn(null, BuffType.STUN));
        assertEquals("无角色 ⇒ 0", 0L, fx.caster.remainingTicksOn(fx.rolelessToken, BuffType.STUN));
        assertEquals("null ⇒ 0", 0L, fx.caster.remainingTicksOn(null, BuffType.STUN));
    }

    /** `removeFrom` 从目标账本里撤掉 buff；目标无角色 ⇒ `false`（且什么都不做）。 */
    @Test
    public void removeFromClearsOnTheTarget() {
        CrossFixture fx = new CrossFixture();
        fx.caster.addTo(fx.victimToken, BuffType.STUN, 100);
        assertTrue("移除受理", fx.caster.removeFrom(fx.victimToken, BuffType.STUN));
        assertFalse("目标身上没有了", fx.victim.has(BuffType.STUN));
        assertTrue("幂等：再撤一次仍受理（移除是无条件的）", fx.caster.removeFrom(fx.victimToken, BuffType.STUN));
        assertFalse("无角色 ⇒ 拒绝", fx.caster.removeFrom(fx.rolelessToken, BuffType.STUN));
        assertFalse("类型为 null ⇒ 拒绝", fx.caster.removeFrom(fx.victimToken, null));
    }

    /** `clearDebuffOn` 净化的是**目标**的负面 buff（免疫类不动）；目标无角色 ⇒ `0`。 */
    @Test
    public void clearDebuffOnPurgesTheTargetsDebuffsOnly() {
        CrossFixture fx = new CrossFixture();
        fx.caster.addTo(fx.victimToken, BuffType.STUN, 100);
        fx.caster.addTo(fx.victimToken, BuffType.SILENCE, 100);
        assertEquals(2, fx.caster.clearDebuffOn(fx.victimToken));
        assertFalse(fx.victim.has(BuffType.STUN));
        assertFalse(fx.victim.has(BuffType.SILENCE));
        assertEquals("再清一次无可清", 0, fx.caster.clearDebuffOn(fx.victimToken));
        assertEquals("无角色 ⇒ 0（该读口本就不区分'没清到'与'没有账本'）", 0, fx.caster.clearDebuffOn(fx.rolelessToken));
    }

    /**
     * 原版药水那条线**离线不可达**（如实申报）：{@code PotionEffectType} 需要活服务端的注册表才能初始化
     * （离线引用任意常量即 {@code ExceptionInInitializerError}），而 `targetOf` 解析出的目标组件一旦进入
     * {@code applyPotionEffect} 就会解引用真实玩家。因此这里只覆盖"入参为 null ⇒ 拒绝"这条**纯判据**路径；
     * "药水真的进了目标自己的账本"归运行级读数（指令 {@code effect_to} 即为其取证入口）。
     */
    @Test
    public void potionPathIsRejectedForNullArgumentsOnly() {
        CrossFixture fx = new CrossFixture();
        assertFalse("效果对象为 null", fx.caster.applyPotionEffectTo(fx.victimToken, (PotionEffect) null));
        assertFalse("目标无角色", fx.caster.applyPotionEffectTo(fx.rolelessToken, (PotionEffect) null));
        assertFalse("药水类型为 null", fx.caster.applyPotionEffectTo(fx.victimToken, null, 100, 1));
        assertEquals("目标账本一条没进", 0, fx.victim.appliedPotionTypeCount());
    }

    // ───────── 指令动词：跨玩家那一组 ─────────

    /** 写类动词作用于目标：回**目标写后**的状态。 */
    @Test
    public void crossPlayerWriteVerbsActOnTheTarget() {
        CrossFixture fx = new CrossFixture();
        assertEquals("写后目标剩余刻", "100", fx.caster.onOperationCommand("add_to victim STUN 100"));
        assertTrue("确实写进了目标账本", fx.victim.has(BuffType.STUN));
        assertEquals("读回来的是目标的", "100", fx.caster.onOperationCommand("remaining_on victim STUN"));
        assertEquals("撤销后归零", "0", fx.caster.onOperationCommand("remove_from victim STUN"));
        assertEquals("清负面回条数", "0", fx.caster.onOperationCommand("clear_debuff_on victim"));
    }

    /** `clear_debuff_on` 回清掉的条数（目标无角色 ⇒ `null`：写类拒绝）。 */
    @Test
    public void clearDebuffOnVerbReportsTheClearedCount() {
        CrossFixture fx = new CrossFixture();
        fx.caster.onOperationCommand("add_to victim STUN 100");
        fx.caster.onOperationCommand("add_to victim SILENCE 100");
        assertEquals("2", fx.caster.onOperationCommand("clear_debuff_on victim"));
        assertNull("无角色 ⇒ 写类拒绝", fx.caster.onOperationCommand("clear_debuff_on roleless"));
    }

    /**
     * 跨玩家动词的两条拒绝口径**有意不同**（冻结这条裁定）：
     * <ul>
     *   <li>写类（{@code add_to} / {@code remove_from} / {@code clear_debuff_on}）：目标无角色 ⇒ `null`；</li>
     *   <li>读类（{@code has_on} / {@code remaining_on}）：目标无角色照常回 `false` / `0`；</li>
     *   <li>共同点：名字解析不到（不在线）⇒ `null`。</li>
     * </ul>
     */
    @Test
    public void crossPlayerVerbsRejectRolelessWritesButAnswerReads() {
        CrossFixture fx = new CrossFixture();
        assertNull("写：无角色 ⇒ 拒绝", fx.caster.onOperationCommand("add_to roleless STUN 100"));
        assertNull("写：无角色 ⇒ 拒绝", fx.caster.onOperationCommand("remove_from roleless STUN"));
        assertEquals("读：无角色照常回 false", "false", fx.caster.onOperationCommand("has_on roleless STUN"));
        assertEquals("读：无角色照常回 0", "0", fx.caster.onOperationCommand("remaining_on roleless STUN"));

        assertNull("不在线 ⇒ 写类拒绝", fx.caster.onOperationCommand("add_to ghost STUN 100"));
        assertNull("不在线 ⇒ 读类也拒绝（名字本身就是非法参数）", fx.caster.onOperationCommand("has_on ghost STUN"));
        assertNull("不在线 ⇒ 剩余刻同理", fx.caster.onOperationCommand("remaining_on ghost STUN"));
        assertNull("不在线 ⇒ 净化同理", fx.caster.onOperationCommand("clear_debuff_on ghost"));
    }

    /** 跨玩家动词的语法 / 参数非法一律 `null`，且不改任何状态。 */
    @Test
    public void crossPlayerVerbsRejectMalformedArguments() {
        CrossFixture fx = new CrossFixture();
        assertNull("add_to 缺参", fx.caster.onOperationCommand("add_to victim STUN"));
        assertNull("add_to 多参", fx.caster.onOperationCommand("add_to victim STUN 100 1"));
        assertNull("add_to 未知 buff id", fx.caster.onOperationCommand("add_to victim NOPE 100"));
        assertNull("add_to 大小写不符", fx.caster.onOperationCommand("add_to victim stun 100"));
        assertNull("add_to 非数字", fx.caster.onOperationCommand("add_to victim STUN abc"));
        assertNull("add_to 负数", fx.caster.onOperationCommand("add_to victim STUN -1"));
        assertNull("remove_from 缺参", fx.caster.onOperationCommand("remove_from victim"));
        assertNull("remove_from 未知 buff id", fx.caster.onOperationCommand("remove_from victim NOPE"));
        assertNull("has_on 缺参", fx.caster.onOperationCommand("has_on victim"));
        assertNull("has_on 未知 buff id", fx.caster.onOperationCommand("has_on victim NOPE"));
        assertNull("remaining_on 缺参", fx.caster.onOperationCommand("remaining_on victim"));
        assertNull("clear_debuff_on 无参", fx.caster.onOperationCommand("clear_debuff_on"));
        assertNull("clear_debuff_on 多参", fx.caster.onOperationCommand("clear_debuff_on victim 1"));
        assertFalse("全部被拒 ⇒ 目标账本仍空", fx.victim.has(BuffType.STUN));
    }

    /**
     * 药水动词的**参数个数**这条判据可以离线验（它在查注册表**之前**就拒绝）；
     * 参数合法时必然要去查服务端注册表 ⇒ 归运行级（见 {@link #potionPathIsRejectedForNullArgumentsOnly()}）。
     */
    @Test
    public void potionVerbsRejectWrongArityBeforeTouchingTheRegistry() {
        CrossFixture fx = new CrossFixture();
        assertNull("effect 缺参", fx.caster.onOperationCommand("effect BLINDNESS 100"));
        assertNull("effect 多参", fx.caster.onOperationCommand("effect BLINDNESS 100 1 0"));
        assertNull("effect_to 缺参", fx.caster.onOperationCommand("effect_to victim BLINDNESS 100"));
        assertNull("effect_to 多参", fx.caster.onOperationCommand("effect_to victim BLINDNESS 100 1 0"));
        assertNull("uneffect 缺参", fx.caster.onOperationCommand("uneffect"));
        assertNull("uneffect 多参", fx.caster.onOperationCommand("uneffect blindness 100"));
        assertEquals("全部被拒 ⇒ 账本没被动过", 0, fx.caster.appliedPotionTypeCount());
    }

    // ───────── 撤销路径（本次新增：组件自侧摘单个药水类型） ─────────

    /**
     * `removePotionEffect(null)` ⇒ `false`，且**一寸账本都不动** ——
     * 这是该方法离线可达的那条分支。
     *
     * <p><b>为什么"账本项真的被摘掉"这条读数离线到不了</b>（如实申报）：
     * <ol>
     *   <li>入参只能来自注册表解析（{@code effect} / {@code uneffect} 两个动词），而
     *       {@code PotionEffectType} 在本基线上是 <b>{@code abstract} 类</b>、其静态常量与派生实例
     *       都会触发 {@code <clinit>}（向服务端注册表要效果类型）⇒ 离线必然
     *       {@code ExceptionInInitializerError}（本类 {@link StubBuffManager#removeBuff(BuffType)}
     *       的注释里记着同一堵墙）；</li>
     *   <li>账本 {@code appliedPotionTypes} 是 <b>{@code private}</b>，替身无法从外面塞一个类型进去
     *       （也不该为它单开一条生产缝：那会凭空多一个只有测试用的口子）。</li>
     * </ol>
     * 因此"摘掉账本项"归运行级读数 —— 取证入口即指令
     * {@code /role operation @s @s buffs uneffect blindness}（回"该类型此前是否在账本里"，
     * 与 {@code count} 一前一后即可看到账本数减一）。
     */
    @Test
    public void removePotionEffectRejectsNullAndLeavesTheLedgerAlone() {
        BuffComponent buffs = component();
        assertFalse("类型为 null ⇒ false（不解析、不记账）", buffs.removePotionEffect(null));
        assertEquals("账本没被动过", 0, buffs.appliedPotionTypeCount());
        assertTrue("闸门也不受影响", buffs.canCastSkill());
    }
}
