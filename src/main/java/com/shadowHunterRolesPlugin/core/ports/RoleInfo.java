package com.shadowHunterRolesPlugin.core.ports;

import com.shadowHunterRolesPlugin.core.Faction;
import org.bukkit.entity.Player;

/**
 * **角色信息服务**（ · A1）：聚合根（{@code core/Role}）的**只读服务面**。
 * <p><b>为什么需要它</b>：角色的**阵营**是"一个角色一份、全局静态"的属性 ⇒ 按 ADR-0006 的通用判据
 * 归**聚合根**（{@code Role}）持有；而组件不能直接摸 {@code Role}/{@code RoleInstance}（那会把
 * 内部实现细节变成组件契约）⇒ 由本端口提供**读取与行为**的唯一入口。
 * <p><b>取值来源 = 聚合根</b>：{@link #faction()} 读 {@code Role#getFaction()}
 * （**不**从每实例的组件状态取 —— 阵营不再随实例复制）。
 * <p><b>两个行为</b>（{@link #isHostile(Player)} / {@link #hasEnemyInRange(double)}）逐字沿用原
 * 阵营组件里的实现；**关系表仍留平台**（{@code platform.FactionLookup}：interface + 构造期注入 +
 * 静态数据，不随实例复制）。
 * <p><b>本卡为纯加性</b>：只新增本端口与它的实现，并把它挂进 {@code ComponentServices}；
 * **不删**任何既有成员 / 组件 / 端口 —— 删除与消费者接线由后续两张卡承担（见说明件 §3）。
 */
public interface RoleInfo {

 /** 角色 id（聚合根的身份；{@code Role} 只带 Id 的那一部分）。 */
    String id();

 /** 角色描述（**纯文本**，多行以 {@code '\n'} 连接；无描述 ⇒ 空串）。 */
    String description();

 /** 该角色所属阵营（读聚合根；不随实例复制）。 */
    Faction faction();

 /** 该玩家是否与本角色的阵营敌对（未选角色的玩家也算敌人）。 */
    boolean isHostile(Player victim);

 /** 半径内是否有敌人（几何 + 阵营语义逐字沿用原实现）。 */
    boolean hasEnemyInRange(double radius);
}
