package com.shadowHunterRolesPlugin.command;

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
 *     <li>非玩家发送者 → 统一拒绝（既有文案逐字保留）；</li>
 *     <li>无参数 → **静默返回**（既有行为：历史实现的 {@code sendHelp} 调用被注释掉，本卡按"行为等价"逐字保留）；</li>
 *     <li>按第一级参数路由到注册的子指令，**参数剥离后**交给该子指令；</li>
 *     <li>未知子指令 → 统一报错文案（既有文案逐字保留）；</li>
 *     <li>Tab 补全：第一级补全子指令名，其余交给命中的子指令。</li>
 * </ol>
 * <p>
 * <b>既有顶层命令不迁移 Brigadier</b>：注册方式仍是 `plugin.yml` 的 {@code commands: role:} + 主类
 * {@code getCommand("role").setExecutor(...)}（`docs/插件文档/开发指南-新增角色或组件.md` §4.5.1/§4.5.2）。
 */
public class RoleCommand implements CommandExecutor, TabCompleter {

    private final Map<String, SubCommand> subCommands = new TreeMap<>();

    public RoleCommand(RoleManager roleManager, RoleRegistry roleRegistry){
        registerSubCommand(new SetRoleCommand(roleManager, roleRegistry));
        registerSubCommand(new ClearRoleCommand(roleManager));
        registerSubCommand(new EnergyCommand(roleManager));
        registerSubCommand(new DebugCommand(roleManager));
        registerSubCommand(new HelpCommand());
    }

    private void registerSubCommand(SubCommand subCommand){
        subCommands.put(subCommand.getName().toLowerCase(Locale.ROOT), subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
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
