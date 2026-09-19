package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.*;
import com.shadowHunterRolesPlugin.platform.Task;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * 默认的「SanTE 归零惩罚」被动（组件侧最后一批 B⑨ 迁移）。
 * <p><b>迁移口径</b>：
 * <ul>
 *   <li>去 legacy `SanTEChangeAware` 与 `LifecycleAware` ⇒ 改走基类新钩子
 *       {@link RoleComponent#onSanTEChange(int, int)} 与无参 {@code stop()}（容器按注册表顺序直接派发，
 *       不再经事件总线绕行）；</li>
 *   <li>惩罚状态 `isInSanTEPunishment` 由聚合根搬进**组件私有字段**（该状态本就不该上 `RoleInstance`）；</li>
 *   <li>任务经 `svc().timers()` 登记本组件资源表、Buff 经 `svc().buffs()`、SanTE 经 `svc().sante()`、
 *       真伤经 `svc().damage()`；**表现层（粒子/标题/音效）与全部数值逐字不变**。</li>
 * </ul>
 * <p><b>⭐ 语义修复（阶段 6 · 本批 t26；用户直接裁定）</b>：{@code inSanTEPunishment} 此前是**纯写不读的死状态**
 * （4 处出现 / **0 处读取**）⇒ 它声称的三件事一件都没做。现已恢复应有语义：
 * <ol>
 *   <li>**进入即标记**：归零判定通过后立即 {@code inSanTEPunishment = true}，**先于**起任务
 *       （消除"起任务与标记之间"的重入窗口）；</li>
 *   <li>**惩罚期间持续钉 SanTE = 0**：任务体**每跳**执行 {@code svc().sante().set(0)}
 *       （40 tick 一跳 ⇒ 钉点为触发后第 1 / 41 / 81 tick；两钉点之间其它组件的写入（如流血 `+4`）
 *       会在下一跳被重新钉回 0）；</li>
 *   <li>**忽略重入**：{@code onSanTEChange} 顶部守卫 {@code if (inSanTEPunishment) return;}
 *       ⇒ 惩罚进行中**不取消、不重启、不刷新 {@code count}、不重放标题/粒子、不重复上 STUN**；</li>
 *   <li>**结束回满**：第三次跳（{@code count == 3}）清标记并把 SanTE 恢复至 {@code max}（逐字保留迁移前行为）。</li>
 * </ol>
 * <p><b>⚠️ 旧注释已被实测证伪并更正（本批）</b>：容器侧 `RoleInstance.dispatchSanTEChange` 的
 * {@code if(pre == now) return;} **只能**挡住「**已经是 0 还继续扣**」；而「**先回血再扣**」
 * （`pre = 4 → now = 0`）是**真变化** ⇒ **照样派发**。因此原注释所称
 * 「SanTE 已为 0 时再扣不再重复派发 ⇒ 惩罚不再被重复触发/延长（O-6 的重复任务路径由本批闭合）」
 * **与实测不符**：旧实现在该相位下会**取消并重启**惩罚（刷新 `count`、重上 STUN 100 刻、重放标题与粒子、再来三跳真伤）。
 * 真正闭合"重复触发/延长"的是本批的 **(1) 标记 + (3) 重入守卫**，**不是**容器侧的 `pre == now`。
 * <p><b>⚠️ 中止路径必须复位标记（静默失效防护）</b>：{@code stop()}、玩家离线/死亡分支、任务取消/空转分支
 * 都必须把 {@code inSanTEPunishment} 复位 —— 否则标记卡在 {@code true} ⇒ **后续归零永不触发惩罚**
 * （绿灯不报的静默失效）。
 */
public class DefaultSanTEZeroPunishment extends PassiveSkill {
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

    //B⑨：惩罚状态搬进组件私有字段（原 RoleInstance.isInSanTEPunishment 已删）
    private boolean inSanTEPunishment = false;

    @Override
    public void onSanTEChange(int pre, int now) {
        //(3) 忽略重入（用户裁定）：惩罚进行中直接返回 —— 不取消、不重启、不刷新 count、不重放表现层
        if(inSanTEPunishment) return;
        if(now > 0) return;
        Faction faction = svc().factions().faction();

        //O-6：单一活动任务不变量（先取消仍在跑的旧任务；旧实现的"任务泄漏"在此闭合）
        //注意：这里**不是**"重入保护" —— 重入由上面的 (3) 守卫处理，本行只保证同时最多一个任务对象。
        cancelPunishmentTask();

        //(1) 进入即标记：必须先于起任务（消除"起任务与标记之间"的重入窗口）
        inSanTEPunishment = true;

        punishmentTask = svc().timers().runRepeating(0L, 40L, new Runnable() {

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

                        //(2) 惩罚期间持续钉 0（本跳）。钉 0 **不产生派发**：容器侧 `if(pre == now) return;`
                        //   对"已经是 0 再置 0"直接返回 ⇒ 不会经 onSanTEChange 重入本组件（无重入循环）。
                        //   若两钉点之间被其它组件抬高（如流血 +4），本行的 set(0) 才是"真变化"，
                        //   会派发一次 (pre>0 → 0) —— 那一次仍被 (3) 守卫挡下，不会重启惩罚。
                        svc().sante().set(0);

                        Location loc = player.getLocation();

                        if (count == 1) {
                            svc().buffs().add(BuffType.STUN, 100);
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
                        svc().damage().trueDamage(player, null, totalDamageAmount * 0.33333d);

                        if (count == 3) {
                            //(4) 结束回满：清标记 + 恢复至 max（逐字保留迁移前行为；原实现此处是**重复两次**置 false，已去重）
                            inSanTEPunishment = false;
                            svc().sante().set(svc().sante().max());
                        }
                    }
                });
    }

    //取消仍在运行的惩罚任务并复位句柄（O-6）
    private void cancelPunishmentTask(){
        if(punishmentTask != null){
            punishmentTask.cancel();
            punishmentTask = null;
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
    }
}
