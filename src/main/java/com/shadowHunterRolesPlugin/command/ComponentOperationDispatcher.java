package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * **组件操作面的派发器**（阶段 13 · t127 · 设计定案 §4 / §5 / §6）：指令层到组件操作面的**唯一通道** ✓。
 *
 * <p><b>它做的事</b>（逐条对齐 §4）：① 名称 → UUID（**仅在线** ✓）· ② UUID → {@link RoleInstance}（无实例 ⇒ 回绝 ✓）·
 * ③ 枚举实例的组件 id（**R-6**：只经容器枚举 ✓，不用反射 ✗）· ④ 定位目标组件（`componentId` 可带 `#index`，
 * **0 基** ✓）—— **命中 0 份** ⇒ 回绝 + 列出可用 id ✓；**同 id 多份且未给下标** ⇒ 回绝 + 提示 0 基序号 ✓
 * （**绝不静默取第一份** ✗）· ⑤ **粗粒度权限** ✓ · ⑥ **调公开的 {@link RoleAPI#executeComponentOperation}** ✓
 * 并据返回值回显 ✓ · ⑦ **审计** ✓（执行者 / 时间 / 目标 / 组件 id + 0 基下标 / **原始 payload** / 返回值 ✓）；
 * **全在主线程** ✓（非主线程 ⇒ 直接回绝 ✗）。
 *
 * <p><b>它不做的事</b> ✗：**不解析 payload 的 grammar**（那是组件的事 ✓）、**不做细粒度权限**
 * （op 名藏在 payload 里 ⇒ 归组件自查 ✓）、**不使用反射** ✗（§5）。
 *
 * <p><b>与 {@code RoleAPIImpl.dispatchOperation} 的分工</b> ✓：那条是**执行**（t125 起、包私有 ✓），
 * 本条是**校验 + 提示 + 审计**（它必须知道"为什么没命中"才能给出 §6 要求的回绝文案 ✓）⇒ 两者各自解析
 * `#index` 而**不共享入口** ✓（用户裁定：派发器**直接调公开 API** ✓ —— 不提权、不加共享入口 ✗）。
 *
 * <p><b>八类失败模式全部显式拒绝</b> ✓（§6，绝不静默 ✗）：① 玩家不在线 ② 无角色实例 ③ 组件 id 不存在（列出可用 id）
 * ④ 同 id 多份且无下标（提示 0 基）⑤ payload 语法错（**由组件回绝** ⇒ 本层只能看到 `null` ✓）⑥ 动词与组件能力不符
 * ⑦ 权限不足（**不泄露内部结构** ✓）⑧ 组件异常（**已在 API 层捕获** ✓ + 本层再兜一层 ✗ ⇒ 不得逃到指令层）。
 */
public final class ComponentOperationDispatcher {

    /** **每个组件的粗粒度权限节点前缀**（§5"粗粒度权限"）：实际节点 = {@code <prefix>.<componentId>} ✓。 */
    public static final String PERMISSION_PREFIX = "shadowhunterroles.operation";

    /** 审计行前缀（与既有 `[command-access]` 同一风格 ✓）。 */
    private static final String AUDIT_PREFIX = "[component-operation]";

    /** 表示"执行者自己"的记号（设计定案 §1 的例子用的是 `@s` ✓）。 */
    public static final String SELF_TOKEN = "@s";

    private final RoleManager roleManager;
    private final RoleAPI roleAPI;

    public ComponentOperationDispatcher(RoleManager roleManager, RoleAPI roleAPI) {
        this.roleManager = roleManager;
        this.roleAPI = roleAPI;
    }

    // ───────── 公开入口 ─────────

    /**
     * 派发一条组件操作。
     *
     * @param sender      指令执行者（本子指令只对玩家开放 ⇒ 实际总是 {@link Player} ✓，仍按通用 sender 处理 ✓）
     * @param verb        {@code query}（只读）或 {@code modify}（写）✓ —— 只影响**权限与回显措辞** ✓（两者都走同一 API 入口 ✓）
     * @param targetToken 目标玩家名 / {@code @s} / 空（空 ⇒ 执行者自己 ✓）
     * @param componentId 组件 id，可带 {@code #index}（**0 基** ✓）
     * @param payload     **整段**操作文本（**可含空格** ✓；由组件自解析 ✓）
     * @return 回显结果（{@code handled=false} = 已回绝 ✓）
     */
    public Outcome dispatch(CommandSender sender, String verb, String targetToken, String componentId, String payload) {
        String auditTarget = (targetToken == null || targetToken.isBlank()) ? SELF_TOKEN : targetToken;

        //⓪ 主线程（§4 ★）：不在主线程 ⇒ 回绝（组件状态与渲染都在主线程上）
        if (!Bukkit.isPrimaryThread()) {
            return refuse(sender, verb, auditTarget, componentId, payload, "not-on-primary-thread",
                    "This command can only run on the server thread.");
        }

        //① 名称 → UUID（仅在线 ✓）
        Player target = resolveTarget(sender, targetToken);
        if (target == null) {
            boolean known = targetToken != null && !targetToken.isBlank()
                    && Bukkit.getOfflinePlayer(targetToken).hasPlayedBefore();
            return refuse(sender, verb, auditTarget, componentId, payload, known ? "target-offline" : "target-not-found",
                    known
                            ? "Player '" + targetToken + "' is not online."
                            : "No online player named '" + targetToken + "'. (Omit the player to target yourself.)");
        }

        //② UUID → RoleInstance（无实例 ⇒ 回绝 ✓）
        RoleInstance instance = roleManager.getRoleInstance(target);
        if (instance == null) {
            return refuse(sender, verb, auditTarget, componentId, payload, "no-role-instance",
                    "Player '" + target.getName() + "' has no role instance.");
        }

        //③ + ④ 枚举 + 定位（纯函数 ✓）
        List<RoleComponent> components = instance.componentRegistry().all();
        Resolution resolution = resolve(components, componentId);
        switch (resolution.kind()) {
            case NO_SUCH_ID -> {
                return refuse(sender, verb, auditTarget, componentId, payload, "no-such-component-id",
                        "No component with id '" + resolution.id() + "' on " + target.getName()
                                + ". Available ids: " + String.join(", ", resolution.availableIds())
                                + " (if you meant a player, make sure they are online).");
            }
            case AMBIGUOUS -> {
                return refuse(sender, verb, auditTarget, componentId, payload, "ambiguous-component-id",
                        "Id '" + resolution.id() + "' matches " + resolution.matches()
                                + " components on " + target.getName() + "; use '" + resolution.id() + "#<index>'"
                                + " (indexes are 0-based: 0.." + (resolution.matches() - 1) + ").");
            }
            case BAD_INDEX -> {
                return refuse(sender, verb, auditTarget, componentId, payload, "bad-index",
                        "'#" + resolution.indexToken() + "' is not a usable index for '" + resolution.id()
                                + "' (0-based, available: 0.." + (resolution.matches() - 1) + ").");
            }
            case OK -> {
                //继续
            }
        }

        //⑤ 粗粒度权限（§5）：① 本仓唯一门禁（op 等级 ≥ 3，动作名带组件 ⇒ 日志可按组件分辨 ✓）
        //  ② 叠加**按组件粒度**的权限节点 ✓（对 op/控制台默认放行 ⇒ 不改变既有可用性 ✓，
        //     而权限插件可据此**逐组件**收紧 ✓）
        String node = PERMISSION_PREFIX + "." + resolution.id();
        if (!CommandAccess.check(sender, "/role operation " + verb + " " + resolution.id())) {
            CommandAccess.sendNoPermission(sender);
            return audit(sender, verb, auditTarget, componentId, payload, "denied-by-gate", null, false,
                    Component.empty());
        }
        if (!sender.hasPermission(node)) {
            //★ 不泄露内部结构（§6.7）：只说"没有权限"，不说命中了哪个组件实例 / 哪些 id 可用
            Component echo = Component.text("You do not have permission to operate this component.");
            if (sender instanceof Player player) {
                player.sendMessage(echo);
            }
            return audit(sender, verb, auditTarget, componentId, payload, "denied-by-node:" + node, null, false, echo);
        }

        //⑥ 调**公开** API（用户裁定 ✓）：读写都走它 ✓
        String returned;
        try {
            returned = roleAPI.executeComponentOperation(target.getUniqueId(), componentId, payload);
        } catch (RuntimeException unexpected) {
            //⑧ 兜底（API 层已捕获组件异常 ✓，这里防的是 API 自身/上游的意外 ⇒ 绝不逃到指令层 ✗）
            return refuse(sender, verb, auditTarget, componentId, payload,
                    "unexpected:" + unexpected.getClass().getSimpleName(), "The operation failed and was refused.");
        }

        //⑦ 回显 + 审计（三态：null = 未识别/被拒绝 ✓；"" = 已识别但无回值 ✓；非空 = 规范化值 ✓）
        String reason = returned == null ? "refused-by-component" : "ok";
        Component echo = returned == null
                ? Component.text(verb.equals("query")
                        ? "Unknown operation or refused by the component."
                        : "Unknown operation, or the component refused it.")
                : Component.text("OK: " + (returned.isEmpty() ? "(no value)" : returned));
        if (sender instanceof Player player) {
            player.sendMessage(echo);
        }
        return audit(sender, verb, auditTarget, componentId, payload, reason, returned, returned != null, echo);
    }

    // ───────── 纯函数：定位（离线可测 ✓） ─────────

    /**
     * **定位 + `#index` 解析**（**纯函数** ✓ —— 不碰 Bukkit、不读注册表、无副作用 ⇒ 可离线单测 ✓）。
     * <p>规则（与 {@code RoleAPI} 的 javadoc 逐条一致 ✓）：**最后一个** `#` 为分隔符 ✓；下标 **0 基** ✓；
     * id 为空 / 下标非数字 / 下标为负 ⇒ {@link Kind#BAD_INDEX}（id 为空时按"未知 id"处理 ✓）；
     * 命中 0 份 ⇒ {@link Kind#NO_SUCH_ID}（附**可用 id 列表** ✓）；同 id 多份且未给下标 ⇒ {@link Kind#AMBIGUOUS} ✓
     * （**绝不静默取第一份** ✗）；下标越界 ⇒ {@link Kind#BAD_INDEX} ✓。
     */
    static Resolution resolve(List<RoleComponent> components, String componentId) {
        String raw = componentId == null ? "" : componentId;
        int separator = raw.lastIndexOf('#');
        String id = separator < 0 ? raw : raw.substring(0, separator);
        String indexToken = separator < 0 ? null : raw.substring(separator + 1);

        List<String> available = availableIds(components);
        if (id.isEmpty()) {
            return new Resolution(Resolution.Kind.NO_SUCH_ID, null, raw, null, indexToken, 0, available);
        }

        List<RoleComponent> matches = new ArrayList<>();
        for (RoleComponent component : components) {
            if (component != null && id.equals(component.getId())) {
                matches.add(component);
            }
        }
        if (matches.isEmpty()) {
            return new Resolution(Resolution.Kind.NO_SUCH_ID, null, id, null, indexToken, 0, available);
        }

        if (indexToken == null) {
            if (matches.size() > 1) {
                return new Resolution(Resolution.Kind.AMBIGUOUS, null, id, null, null, matches.size(), available);
            }
            return new Resolution(Resolution.Kind.OK, matches.get(0), id, null, null, 1, available);
        }

        int index;
        try {
            index = Integer.parseInt(indexToken);
        } catch (NumberFormatException notAnIndex) {
            return new Resolution(Resolution.Kind.BAD_INDEX, null, id, null, indexToken, matches.size(), available);
        }
        if (index < 0 || index >= matches.size()) {
            return new Resolution(Resolution.Kind.BAD_INDEX, null, id, index, indexToken, matches.size(), available);
        }
        return new Resolution(Resolution.Kind.OK, matches.get(index), id, index, indexToken, matches.size(), available);
    }

    /** 该实例的**可用组件 id**（去重、按首次出现顺序 ✓；供回绝文案列举 ✓）。 */
    static List<String> availableIds(List<RoleComponent> components) {
        List<String> ids = new ArrayList<>();
        for (RoleComponent component : components) {
            if (component == null) continue;
            String id = component.getId();
            if (id != null && !ids.contains(id)) {
                ids.add(id);
            }
        }
        return List.copyOf(ids);
    }

    /** 组件定位结果（**纯数据** ⇒ 离线可测 ✓）。 */
    public record Resolution(Kind kind, RoleComponent target, String id, Integer index, String indexToken,
                             int matches, List<String> availableIds) {

        /** 定位的四种结局 ✓。 */
        public enum Kind {
            /** 唯一定位成功 ✓（给了 `#index` 且合法，或未给下标且恰好一份 ✓）。 */
            OK,
            /** 该实例没有这个 id ⇒ 回绝 + 列出可用 id ✓。 */
            NO_SUCH_ID,
            /** 同 id 多份且未给下标 ⇒ 回绝 + 提示 0 基序号 ✓（绝不静默取第一份 ✗）。 */
            AMBIGUOUS,
            /** 下标非数字 / 为负 / 越界 ⇒ 回绝 ✓。 */
            BAD_INDEX
        }
    }

    /** 一次派发的回显结果 ✓。 */
    public record Outcome(boolean handled, Component message) {
    }

    // ───────── 内部：解析目标 + 审计 ─────────

    /** 目标玩家：`@s`/空 ⇒ 执行者自己 ✓；否则**仅在线**按名解析 ✓（找不到 ⇒ {@code null} ✓）。 */
    private static Player resolveTarget(CommandSender sender, String targetToken) {
        if (targetToken == null || targetToken.isBlank() || SELF_TOKEN.equalsIgnoreCase(targetToken)) {
            return sender instanceof Player player ? player : null;
        }
        return Bukkit.getPlayerExact(targetToken);
    }

    /** 回绝路径：回显 + 审计（**失败也留痕** ✓ —— §6"全部显式拒绝，绝不静默" ✓）。 */
    private Outcome refuse(CommandSender sender, String verb, String target, String componentId, String payload,
                           String reason, String message) {
        Component echo = Component.text(message);
        if (sender instanceof Player player) {
            player.sendMessage(echo);
        }
        return audit(sender, verb, target, componentId, payload, reason, null, false, echo);
    }

    /**
     * **审计**（§5）：执行者 / 时间 / 目标 / 组件 id + **0 基下标** / **原始 payload** / 返回值 ✓。
     * <p>与玩家侧回显是两回事 ✓：审计走**服务端日志**（前缀 {@value #AUDIT_PREFIX}）⇒ 运行级证据可直接取原始行 ✓。
     */
    private Outcome audit(CommandSender sender, String verb, String target, String componentId, String payload,
                          String reason, String returned, boolean handled, Component echo) {
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if (plugin != null) {
            plugin.getLogger().info(AUDIT_PREFIX + " sender=" + senderName(sender)
                    + " at=" + System.currentTimeMillis()
                    + " verb=" + verb
                    + " target=" + target
                    + " componentId=" + componentId
                    + " payload=" + (payload == null ? "<null>" : '"' + payload + '"')
                    + " returned=" + (returned == null ? "<null>" : '"' + returned + '"')
                    + " reason=" + reason);
        }
        return new Outcome(handled, echo);
    }

    private static String senderName(CommandSender sender) {
        if (sender instanceof Player player) {
            return player.getName();
        }
        return sender == null ? "<null>" : sender.getClass().getSimpleName();
    }

    /** 供指令层把"可用 id"用于 Tab 补全 ✓（只读、无副作用 ✓）。 */
    public static List<String> tabComponentIds(List<RoleComponent> components) {
        return availableIds(components);
    }

    /** 小工具：把候选按前缀过滤（与 {@link SubCommand#filter} 同一口径 ✓）。 */
    static List<String> filterIds(List<String> ids, String prefix) {
        return SubCommand.filter(ids, prefix == null ? "" : prefix.toLowerCase(Locale.ROOT));
    }
}
