package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 子指令 {@code /role operation …} —— **组件操作面的指令面**（阶段 13 · t127 · 设计定案 §1 / §3 / §10.4）。
 *
 * <p><b>语法</b> ✓：
 * <pre>
 * /role operation query  [player|@s] &lt;componentId[#index]&gt; [payload…]
 * /role operation modify [player|@s] &lt;componentId[#index]&gt; &lt;payload…&gt;
 * </pre>
 * <b>动词</b> = {@code query}（只读）/ {@code modify}（写）✓ —— 两者都走**同一个** API 入口 ✓，
 * 动词只影响**权限与回显措辞** ✓（§5）。<b>目标</b>省略（或写 {@code @s}）⇒ 默认**执行者自己** ✓。
 * <p><b>目标与 id 的消歧</b> ✓：第 2 个参数**当且仅当**它是 {@code @s} 或一个**在线**玩家名时才当作目标 ✓；
 * 否则它本身就是 {@code componentId}（目标 = 自己）✓ —— 这样 `query energy current` 不会被误读成"目标 = energy" ✗。
 * <p><b>payload 一律原样交给组件**自解析**</b> ✓（首 token 必为操作动词 ✓；本类**不解释**它 ✗）。
 *
 * <p><b>注册</b> ✓：本子指令由主指令 {@link RoleCommand} 在构造期登记（`/role` 根命令仍走 `plugin.yml` + `setExecutor` ✓，
 * **未新增根命令** ⇒ 无需改 `plugin.yml` ✓；设计定案 §9.1 的"root 用现有根还是新根"在此取**现有根** ✓）。
 *
 * <p><b>Tab 补全</b> ✓：第 1 段补 {@code query|modify} ✓；第 2 段补**在线玩家名 + 自己的组件 id** ✓；
 * 第 3 段补目标的组件 id ✓。★ **op 名与参数不可补** ✗ —— 设计定案 §2 的"无自报清单"取舍所致
 * （组件不自报可用操作 ⇒ 只能补到 {@code componentId} ✓，op 与参数需**手写文档** ✓）；补全同样受根门禁约束 ✓。
 *
 * <p><b>文本</b>：一律 Adventure {@link Component} ✓（**不用 {@code ChatColor}** ✗）。
 */
public class ComponentOperationCommand implements SubCommand {

    /** 只读动词 ✓。 */
    private static final String VERB_QUERY = "query";
    /** 写动词 ✓。 */
    private static final String VERB_MODIFY = "modify";

    private final RoleManager roleManager;
    private final ComponentOperationDispatcher dispatcher;

    public ComponentOperationCommand(RoleManager roleManager, RoleAPI roleAPI) {
        this.roleManager = roleManager;
        this.dispatcher = new ComponentOperationDispatcher(roleManager, roleAPI);
    }

    @Override
    public String getName() {
        return "operation";
    }

    @Override
    public String getUsage() {
        return "Usage: /role operation <query|modify> [player|@s] <componentId[#index]> [payload...]"
                + "  e.g. /role operation query @s energy current   |   /role operation modify Steve energy add 5";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can execute this command!"));
            return true;
        }

        if (args.length < 2) {
            player.sendMessage(Component.text(getUsage()));
            return true;
        }

        String verb = args[0].toLowerCase(Locale.ROOT);
        if (!verb.equals(VERB_QUERY) && !verb.equals(VERB_MODIFY)) {
            player.sendMessage(Component.text("Unknown verb '" + args[0] + "'."));
            player.sendMessage(Component.text(getUsage()));
            return true;
        }

        //目标与 id 的消歧（见类 javadoc ✓）：只有"@s / 在线玩家名"才算目标
        String targetToken;
        String componentId;
        int payloadFrom;
        if (isTargetToken(args[1])) {
            if (args.length < 3) {
                player.sendMessage(Component.text(getUsage()));
                return true;
            }
            targetToken = args[1];
            componentId = args[2];
            payloadFrom = 3;
        } else {
            targetToken = null;                 //省略 ⇒ 执行者自己 ✓
            componentId = args[1];
            payloadFrom = 2;
        }

        String payload = payloadFrom >= args.length
                ? ""
                : String.join(" ", Arrays.copyOfRange(args, payloadFrom, args.length));

        //§6.6 在 v2 定案下**可实现的**那一半 ✓：modify 没有 payload ⇒ 没有可写的东西 ⇒ 回绝
        //（"query 却调写操作"无法在派发器侧判定 ✗ —— payload 里没有 query/modify 信息、组件也无自报清单 ✗ ⇒ 已申报）
        if (verb.equals(VERB_MODIFY) && payload.isBlank()) {
            player.sendMessage(Component.text("modify needs an operation payload,"
                    + " e.g. /role operation modify " + componentId + " set 50"));
            return true;
        }

        dispatcher.dispatch(player, verb, targetToken, componentId, payload);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        if (args.length == 1) {
            return SubCommand.filter(List.of(VERB_QUERY, VERB_MODIFY), args[0]);
        }
        if (args.length == 2) {
            List<String> candidates = new ArrayList<>();
            for (Player online : Bukkit.getOnlinePlayers()) {
                candidates.add(online.getName());
            }
            candidates.add(ComponentOperationDispatcher.SELF_TOKEN);
            candidates.addAll(componentIdsOf(player));
            return SubCommand.filter(candidates, args[1]);
        }
        if (args.length == 3) {
            Player target = isTargetToken(args[1]) ? Bukkit.getPlayerExact(args[1]) : player;
            return SubCommand.filter(target == null ? List.of() : componentIdsOf(target), args[2]);
        }
        //★ op 名与参数**不可补** ✗（无自报清单 ⇒ 只能补到 componentId ✓；如实说明见类 javadoc）
        return List.of();
    }

    /** 第 2 个参数是否**当目标**（`@s` 或在线玩家名 ✓）。 */
    private boolean isTargetToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return ComponentOperationDispatcher.SELF_TOKEN.equalsIgnoreCase(token)
                || Bukkit.getPlayerExact(token) != null;
    }

    /** 某玩家实例上的组件 id（无实例 ⇒ 空表 ✓；R-6：只经容器枚举 ✓）。 */
    private List<String> componentIdsOf(Player player) {
        RoleInstance instance = roleManager.getRoleInstance(player);
        if (instance == null) {
            return List.of();
        }
        List<RoleComponent> components = instance.componentRegistry().all();
        return ComponentOperationDispatcher.tabComponentIds(components);
    }
}
