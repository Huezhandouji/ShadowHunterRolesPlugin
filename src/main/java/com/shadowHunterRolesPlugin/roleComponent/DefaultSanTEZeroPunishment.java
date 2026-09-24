package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.time.Duration;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.TimerComponent;

/**
 * 默认的「SanTE 归零惩罚」被动（组件侧最后一批 B⑨ 迁移）。
 * <p><b>迁移口径</b>：
 * <ul>
 *   <li>去 legacy `SanTEChangeAware` 与 `LifecycleAware` ⇒ 改走组件钩子
 *       {@code onSanTEChange(int, int)} 与无参 {@code stop()}（容器直接派发，
 *       不再经事件总线绕行）；
 *       <br><b>阶段 12 · t89 口径更正（不静默改写）</b>：本条经历两次更正 ——
 *       <br>① 原文写的是"改走**基类**新钩子 {@code RoleComponent#onSanTEChange}" —— 该钩子**已从基类迁出** ⇒ 那半句作废 ✗；
 *       <br>② 其后（t87）一度改为"由本类**显式实现某个能力接口**" —— 该做法也**已作废** ✗
 *       （用户硬规矩 **R-1**：SanTE 的家是 {@code SanTEComponent} ⇒ **不得再为它新增能力接口** ✗）；
 *       <br>③ **现行形态**：本类 `implements {@code SanTEComponent.Subscriber}`，并在 `start()` 里
 *       **向 {@code SanTEComponent} 订阅** ✓（只通知、不可否决 ✓）。
 *       <br><b>阶段 13 · t92a 口径更正（不静默改写）</b>：上面这半句**曾**写「在 `awake()` 里……订阅」——
 *       订阅时机**已迁到 `start()`**（硬规矩 **R-4**：`awake()` 只做构造期自检 / 只读自身，
 *       **不得取用其他组件** ✗）⇒ 该半句**作废** ✗，逐字保留于此：<i>「并在 `awake()` 里向
 *       {@code SanTEComponent} 订阅」</i>。旧口径原文保留：<i>「改走基类新钩子
 *       {@code RoleComponent#onSanTEChange(int, int)}」</i>。</li>
 *   <li>惩罚状态 `isInSanTEPunishment` 由聚合根搬进**组件私有字段**（该状态本就不该上 `RoleInstance`）；</li>
 *   <li>任务经**计时组件**登记本组件资源表（阶段 13 · t102：不再经服务集端口，改为组件本身用）、Buff 经 `svc().buffs()`、SanTE 经 `svc().sante()`、
 *       真伤经**生命组件**的真伤入口（阶段 13 · t102：同上）；**表现层（粒子/标题/音效）与全部数值逐字不变**。</li>
 * </ul>
 * <p><b>⭐ 语义修复（阶段 6 · 本批 t26；用户直接裁定）</b>：{@code inSanTEPunishment} 此前是**纯写不读的死状态**
 * （4 处出现 / **0 处读取**）⇒ 它声称的三件事一件都没做。现已恢复应有语义：
 * <ol>
 *   <li>**进入即标记**：归零判定通过后立即 {@code inSanTEPunishment = true}，**先于**起任务
 *       （消除"起任务与标记之间"的重入窗口）；</li>
 *   <li>**惩罚期间持续钉 SanTE = 0（逐 tick）**：{@link #update()} 里以 {@code inSanTEPunishment} 守卫，
 *       **每一 tick** 执行 {@code svc().sante().set(0)}
 *       （阶段 6 · t27 对齐用户裁定 **Q1 = A**：此前是"任务体每 40 刻钉一次"，整个惩罚只钉 3 次、
 *       两钉点之间可被其它组件抬高（如流血 `+4`）⇒ 现改为逐 tick，**惩罚期间每一 tick 都是 0**）；</li>
 *   <li>**忽略重入**：{@code onSanTEChange} 顶部守卫 {@code if (inSanTEPunishment) return;}
 *       ⇒ 惩罚进行中**不取消、不重启、不刷新 {@code count}、不重放标题/粒子、不重复上 STUN**；</li>
 *   <li>**结束回满（推迟到 STUN 结束）**：在**施加 STUN 的那一跳**（{@code count == 1}）预约
 *       {@code timerComponent().runLater(this, 100L, …)} ⇒ **STUN 100 刻到期那一刻**清标记并把 SanTE 恢复至 {@code max}
 *       （阶段 6 · t27 对齐用户裁定 **Q2 = A**：此前在第 3 跳 ≈4.05 s 就回满、而 STUN 到 5 s 才结束
 *       ⇒ 存在约 1 秒「已回满但仍在眩晕」的窗口 ⇒ 现已消除；回满在**同一处一次性**完成）。</li>
 * </ol>
 * <p><b>⚠️ 旧注释已被实测证伪并更正（t26）</b>：容器侧 `RoleInstance.dispatchSanTEChange` 的
 * {@code if(pre == now) return;} **只能**挡住「**已经是 0 还继续扣**」；而「**先回血再扣**」
 * （`pre = 4 → now = 0`）是**真变化** ⇒ **照样派发**。因此原注释所称
 * 「SanTE 已为 0 时再扣不再重复派发 ⇒ 惩罚不再被重复触发/延长（O-6 的重复任务路径由本批闭合）」
 * **与实测不符**：旧实现在该相位下会**取消并重启**惩罚（刷新 `count`、重上 STUN 100 刻、重放标题与粒子、再来三跳真伤）。
 * 真正闭合"重复触发/延长"的是 t26 的 **(1) 标记 + (3) 重入守卫**，**不是**容器侧的 `pre == now`。
 * <p><b>⚠️ 中止路径必须复位标记（静默失效防护；t26 建立 · t27 保持完整）</b>：{@code stop()}、玩家离线/死亡分支、
 * 任务取消/空转分支、以及**回满任务本身的正常结束**都必须把 {@code inSanTEPunishment} 复位 ——
 * 否则标记卡在 {@code true} ⇒ **逐 tick 钉 0 会一直生效、且后续归零永不触发惩罚**（绿灯不报的静默失效）。
 * 五条路径逐条标注为源码里的 {@code (5-①…⑤)}。
 */
public class DefaultSanTEZeroPunishment extends PassiveSkill implements SanTEComponent.Subscriber {

    /**
     * **VitalsComponent 取用入口**（阶段 13 · t102）：向**组件本身**取用（R-6），不再经服务集的白名单端口成员。
     * <p>按需解析（**不缓存**）：R-4 只禁 `awake()`；不缓存引用 ⇒ 不引入生命周期耦合
     * （基类/子类各自覆写 `start()` 时，缓存的引用可能静默为空 ✗）。
     */
    private final VitalsComponent vitalsComponent(){
        return svc().components().get(VitalsComponent.class);
    }

    /**
     * **TimerComponent 取用入口**（阶段 13 · t102）：向**组件本身**取用（R-6），不再经服务集的白名单端口成员。
     * <p>按需解析（**不缓存**）：R-4 只禁 `awake()`；不缓存引用 ⇒ 不引入生命周期耦合
     * （基类/子类各自覆写 `start()` 时，缓存的引用可能静默为空 ✗）。
     */
    private final TimerComponent timerComponent(){
        return svc().components().get(TimerComponent.class);
    }
    public DefaultSanTEZeroPunishment(String id, ComponentServices services) {
        super(
                id,
                services,
                null,
                null
        );
    }

    //O-6：任务句柄（阶段 2 换成平台 Task，null = 没有任务在跑）
    private Task punishmentTask;
    //回满任务句柄（Q2 = A：STUN 100 刻结束时一次性回满；与上面同属本组件资源表）
    private Task punishmentRestoreTask;

    /**
     * **订阅 SanTE 变更**（阶段 12 · t89 · C2 · 用户裁定 (b)）：**向 {@code SanTEComponent} 订阅** ✓，
     * 而**不是**实现某个能力接口 ✗（硬规矩 R-1：SanTE 的家是组件 ⇒ 消费者向**组件本身**取用/订阅 ✓）。
     * <p><b>时机 = {@code start()}（阶段 13 · t92a 迁移 —— R-4 修复）</b>：R-4 禁止在 {@code awake()} 里
     * **取用其他组件** ✗（awake 只做构造期自检 / 只读自身）⇒ 本段**整段**从 {@code awake()} 迁到
     * {@code start()}；与 {@link #stop()} 的退订**成对** ✓（`subscribe` 本身幂等 ⇒ 重复 start 不会重复登记 ✓）。
     * <p><b>通知顺序</b> = **订阅先后** = 容器 `start()` 广播序（= 组件装配序，因为 start 也按容器序广播）。
     * <b>与 {@code awake()} 是否同序需另证</b>（本卡未做运行级取证）⇒ 不再宣称"awake 序" ✗。
     * <p>用容器查找（`svc().components().get(...)`）而不是字段注入 ⇒ 本组件**不持有** `SanTEComponent` 引用，
     * 与"组件只通过容器协作"的既有纪律一致 ✓。
     * <p><b>【已作废】旧口径原文（阶段 12 · t89 原文，逐字保留）</b>：
     * 「时机 = `awake()`（生命周期里"构造之后、start 之前"）⇒ 与装配序一致：**先 awake 的组件先订阅**
     * ⇒ 通知顺序 = 订阅先后 = 装配序 ✓。」—— 订阅迁到 `start()` 之后该表述**作废** ✗（订阅现在发生在 start 相）。
     */
    @Override
    public void start() {
        SanTEComponent sante = svc().components().get(SanTEComponent.class);
        if (sante != null) {
            sante.subscribe(this);
        }
    }

    //B⑨：惩罚状态搬进组件私有字段（原 RoleInstance.isSanTEPunishment 已删）
    private boolean inSanTEPunishment = false;

    /**
     * 逐 tick 钩子（容器按注册表顺序每 tick 广播一次）—— **(2) 惩罚期间逐 tick 钉 SanTE = 0**（用户裁定 Q1 = A）。
     * <p>与容器侧「真变化才派发」的关系（**无重入循环、无任务泄漏**）：
     * <ul>
     *   <li>已是 0 时 {@code set(0)} ⇒ 容器侧 `if(pre == now) return;` ⇒ **不派发**；</li>
     *   <li>若本 tick 更早的组件把 SanTE 抬高（例：`RedBleedPassive` 的 `+4`），本行就是**真变化** ⇒ 派发一次 `(pre>0 → 0)`；
     *       该次派发回到本组件时被 {@link #onSanTEChange(int, int)} 顶部的重入守卫挡下 ⇒ **不取消、不重启、不刷新 count**；</li>
     *   <li>钉 0 与惩罚任务彼此独立：本方法**不创建/不取消任何任务** ⇒ 不产生任务泄漏。</li>
     * </ul>
     */
    @Override
    public void update() {
        if (inSanTEPunishment) {
            svc().sante().set(0);
        }
    }

    @Override
    public void onSanTEChange(int pre, int now) {
        //(3) 忽略重入（用户裁定）：惩罚进行中直接返回 —— 不取消、不重启、不刷新 count、不重放表现层
        if(inSanTEPunishment) return;
        if(now > 0) return;
        Faction faction = svc().roleInfo().faction();

        //O-6：单一活动任务不变量（先取消仍在跑的旧任务；旧实现的"任务泄漏"在此闭合）
        //注意：这里**不是**"重入保护" —— 重入由上面的 (3) 守卫处理，本行只保证同时最多一个任务对象。
        cancelPunishmentTask();

        //(1) 进入即标记：必须先于起任务（消除"起任务与标记之间"的重入窗口）
        inSanTEPunishment = true;

        punishmentTask = timerComponent().runRepeating(this, 0L, 40L, new Runnable() {

                    int count = 0;
                    Player player = svc().self().player();

                    double totalDamageAmount = player.getAttribute(Attribute.MAX_HEALTH) != null ?
                            player.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.3d : 20;

                    @Override
                    public void run() {
                        if (punishmentTask == null || punishmentTask.isCancelled()) {
                            //(5-③ 防御性) 任务已不在 ⇒ 标记不得滞留（否则后续归零永不触发惩罚）
                            inSanTEPunishment = false;
                            return;
                        }
                        if (!player.isOnline() || player.isDead()) {
                            //(5-② 中止路径) 离线/死亡：取消任务并复位标记
                            punishmentTask.cancel();
                            inSanTEPunishment = false;
                            return;
                        }


                        if (count >= 3) {
                            //(5-④ 防御性) 收尾跳之后的一次空转：取消并确保标记已复位
                            punishmentTask.cancel();
                            inSanTEPunishment = false;
                            return;
                        }
                        count += 1;

                        //(2) 钉 0 已移出任务体：改为 {@link #update()} 里**逐 tick** 执行（用户裁定 Q1 = A）
                        //    ⇒ 惩罚期间"每一 tick 都是 0"，不再有两钉点之间被抬高的窗口。

                        Location loc = player.getLocation();

                        if (count == 1) {
                            svc().buffs().add(BuffType.STUN, 100);

                            //(4) 回满推迟到 STUN 结束（用户裁定 Q2 = A）：STUN 在本跳施加、持续 100 刻
                            //    ⇒ 自本跳起 100 刻后（= STUN 到期那一刻）清标记并一次性回满。
                            //    ⇒ 惩罚期间 SanTE 全程真正为 0（逐 tick 钉 + 结束后才回满），消除"已回满但仍眩晕"的窗口。
                            punishmentRestoreTask = timerComponent().runLater(DefaultSanTEZeroPunishment.this, 100L, () -> {
                                //(5-⑤ 正常结束) 若标记已被中止路径复位 ⇒ 不再回满（防越权恢复）
                                if (!inSanTEPunishment) return;
                                //先清标记：否则同一 tick 的逐 tick 钉 0 会把刚回满的值立刻抹回 0
                                inSanTEPunishment = false;
                                svc().sante().set(svc().sante().max());
                            });
                            player.playSound(loc, Sound.ITEM_TOTEM_USE, 1f, 1f);

                            Location particleLoc = loc.clone().add(0, 1, 0);
                            particleLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, particleLoc, 30, 0.5d, 0.5d, 0.5d);
                            particleLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 30, 0.5d, 0.5d, 0.5d);

                            Title title = Title.title(
                                    Component.empty(),
                                    Component.empty()
                            );
                            if (faction == Faction.HUNTER) {
                                title = Title.title(
                                        Component.text("⊠恐惧正在注视着你⊠", NamedTextColor.LIGHT_PURPLE),
                                        Component.empty(),
                                        Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(5000L),
                                                Duration.ZERO
                                        )
                                );
                            } else if (faction == Faction.SHADOW) {
                                title = Title.title(
                                        Component.text("⊠痛苦摸上脊背⊠", NamedTextColor.YELLOW),
                                        Component.empty(),
                                        Title.Times.times(
                                                Duration.ZERO,
                                                Duration.ofMillis(5000L),
                                                Duration.ZERO
                                        )
                                );
                            }

                            player.showTitle(title);

                        }

                        if (count != 1) {
                            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 1f, 0.1f);
                            Location particleLoc = loc.clone().add(0, 1, 0);
                            particleLoc.getWorld().spawnParticle(Particle.SCULK_SOUL, particleLoc, 30, 0.5d, 0.5d, 0.5d);
                        }
                        vitalsComponent().trueDamage(player, null, totalDamageAmount * 0.33333d);
                        //(4) 回满**不再**发生在第 3 跳：已推迟到 STUN 结束（见 count == 1 处的回满任务，Q2 = A）
                    }
                });
    }

    //取消仍在运行的惩罚任务/回满任务并复位句柄（O-6；两把句柄都属本组件资源表）
    private void cancelPunishmentTask(){
        if(punishmentTask != null){
            punishmentTask.cancel();
            punishmentTask = null;
        }
        if(punishmentRestoreTask != null){
            punishmentRestoreTask.cancel();
            punishmentRestoreTask = null;
        }
    }

    /**
     * 停止生效（新钩子，无参）：取消任务 **并复位惩罚标记**。框架 `cancelAllAndClear()` 兜底取消同一句柄 ⇒ 幂等。
     * <p>(5-① 中止路径) 复位标记是必须的：若玩家在惩罚中被清角色/下线，标记滞留 {@code true} 会让
     * **后续归零永不触发惩罚**（静默失效、绿灯不报）。
     */
    @Override
    public void stop() {
        cancelPunishmentTask();
        inSanTEPunishment = false;
        //阶段 12 · t89：**退订**（阶段 13 · t92a：订阅已迁到 start() ⇒ 本行与 start() 对称 ✓
        //  ⇒ 拆卸后不再被通知 ✓；旧口径原文「与 awake() 的订阅对称」在迁移后作废 ✗）
        SanTEComponent sante = svc().components().get(SanTEComponent.class);
        if (sante != null) {
            sante.unsubscribe(this);
        }
    }
}
