package com.shadowHunterRolesPlugin.command;
import com.shadowHunterRolesPlugin.command.ComponentOperationCommand;

import com.shadowHunterRolesPlugin.api.RoleAPI;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 主指令 {@code /role}（别名 {@code r}）—— **只做路由 + 统一报错 + 补全**。
 * <p>
 * 本类是 {@link SubCommand} 体系的中央分发器（风格对齐 {@code SHDFGamePlugin} 的 {@code ShdfGameCommand}）：
 * <ol>
 *     <li><b>权限门禁（阶段 6 · t30，用户裁定）</b>：单一判定点 {@link CommandAccess#check} ——
 *         <b>等级 ≥ 3 的玩家</b>放行、控制台/RCON 放行、其余一律拒绝；<b>执行与 Tab 补全共用同一道门</b>；</li>
 *     <li>非玩家发送者 → 统一拒绝（既有文案逐字保留）；</li>
 *     <li>无参数 → **静默返回**（既有行为：历史实现的 {@code sendHelp} 调用被注释掉，本卡按"行为等价"逐字保留）；</li>
 *     <li>按第一级参数路由到注册的子指令，**参数剥离后**交给该子指令；</li>
 *     <li>未知子指令 → 统一报错文案（既有文案逐字保留）；</li>
 *     <li>Tab 补全：第一级补全子指令名，其余交给命中的子指令。</li>
 * </ol>
 * <p>
 * <b>门禁覆盖面（子指令路径一次覆盖）</b>：{@code help} / {@code set} / {@code clear} / {@code energy} /
 * {@code debug} / {@code debug cooldown} / {@code debug sched} / {@code operation} —— 它们都必须经本类
 * {@link #onCommand} 或 {@link #onTabComplete} 的**第一行**才能抵达子指令实现（见 {@link #gate} 的唯一调用形态）。
 * <p>
 * <b>既有顶层命令不迁移 Brigadier</b>：注册方式仍是 `plugin.yml` 的 {@code commands: role:} + 主类
 * {@code getCommand("role").setExecutor(...)}（`docs/插件文档/开发指南-新增角色或组件.md` §4.5.1/§4.5.2）。
 */
public class RoleCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new TreeMap<>();

    public RoleCommand(RoleManager roleManager, RoleRegistry roleRegistry, RoleAPI roleAPI){
        registerSubCommand(new SetRoleCommand(roleManager, roleRegistry));
        registerSubCommand(new ClearRoleCommand(roleManager));
        registerSubCommand(new EnergyCommand(roleManager));
        registerSubCommand(new DebugCommand(roleManager));
        //阶段 13 · t127：组件操作面（`/role operation …`）—— 派发器**直接调公开 API** ✓（不提权、不加共享入口 ✓）
        registerSubCommand(new ComponentOperationCommand(roleManager, roleAPI));
        registerSubCommand(new HelpCommand());
    }

    private void registerSubCommand(SubCommand subCommand){
        subCommands.put(subCommand.getName().toLowerCase(Locale.ROOT), subCommand);
    }

    /**
     * **单一门禁点**（阶段 6 · t30）：执行侧与补全侧共用；返回 {@code true} = 放行。
     * <p>拒绝时已把玩家侧文案发出（非玩家 sender 不发聊天）；调用方直接 {@code return}。
     */
    private static boolean gate(CommandSender sender, String action){
        if(CommandAccess.check(sender, action)) return true;
        CommandAccess.sendNoPermission(sender);
        return false;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        //阶段 6 · t30（用户裁定）：等级 ≥ 3 门禁 —— 在**根入口第一行**，其下 7 条子指令路径全部经过此处
        if(!gate(sender, "/role")){
            return true;
        }

        if(!(sender instanceof Player player)){
            sender.sendMessage(Component.text("Only players can execute this command!"));
            return true;
        }

        if(args.length == 0){
            //既有可见行为逐字保留：/role 空参数不输出任何内容（历史实现的 sendHelp 调用被注释掉）
            return true;
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if(subCommand == null){
            player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
            return true;
        }

        //剥离第一个参数，剩余参数交给子指令
        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return subCommand.execute(player, subArgs);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        //阶段 6 · t30：补全侧与执行侧**同一道门**（不得只拦执行、补全仍泄漏子指令名）
        if(!gate(sender, "/role (tab)")){
            return List.of();
        }

        if(args.length == 1){
            return SubCommand.filter(subCommands.keySet(), args[0]);
        }

        SubCommand subCommand = subCommands.get(args[0].toLowerCase(Locale.ROOT));
        if(subCommand == null){
            return List.of();
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, subArgs.length);
        return subCommand.onTabComplete(sender, subArgs);
    }
}
