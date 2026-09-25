package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 子指令 {@code set}：{@code /role set <roleId> [playerName]} —— 给自己或指定在线玩家装配角色。
 * <p>
 * 行为与原 {@code RoleCommand} 内联实现**逐字等价**，含两处易被改写掉的历史行为：
 * <ul>
 *     <li>参数个数不是 1 或 2 时（如 {@code /role set}、{@code /role set a b c}）**静默返回**，不输出任何内容；</li>
 *     <li>角色 id 不存在 / 目标玩家不在线 / 装配失败的文案逐字不变。</li>
 * </ul>
 */
public class SetRoleCommand implements SubCommand {

    private final RoleManager roleManager;
    private final RoleRegistry roleRegistry;

    public SetRoleCommand(RoleManager roleManager, RoleRegistry roleRegistry){
        this.roleManager = roleManager;
        this.roleRegistry = roleRegistry;
    }

    @Override
    public String getName(){
        return "set";
    }

    @Override
    public String getUsage(){
        return "set <roleId> [playerName]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)) return true;

        if(args.length == 1){
            handleSet(player, args[0], null);
            return true;
        }
        if(args.length == 2){
            handleSet(player, args[0], args[1]);
            return true;
        }
        //既有行为逐字保留：参数个数不匹配时静默返回（原实现是中央 switch 的 break → return true）
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args){
        if(args.length == 1) return SubCommand.filter(roleRegistry.ids(), args[0]);
        return List.of();
    }

    private void handleSet(Player sender, String roleId, String targetName){
        if(!roleRegistry.contains(roleId)){
            sender.sendMessage(Component.text("Role '" + roleId + "' not exist!"));
            return;
        }

        //选择角色：省略目标 ⇒ 自己；否则经统一解析（裸名 / @s / 选择器，且必须恰好命中 1 名在线玩家）
        Player target;
        if(targetName == null){
            target = sender; // 默认是自己
        }
        else{
            PlayerTargets.Result resolved = PlayerTargets.resolve(sender, targetName);
            target = resolved.player();
            if(target == null || !target.isOnline()){
                sender.sendMessage(Component.text(PlayerTargets.rejection(targetName, resolved,
                        "Cannot find the player you provided: " + targetName)));
                return;
            }
        }

        boolean success = roleManager.selectRole(target, roleId);
        if(success){
            if(targetName == null) sender.sendMessage(Component.text("Your role has been set: " + roleRegistry.get(roleId).getId()));
            else sender.sendMessage(Component.text("The role of player [ " + targetName + "] has been set: " + roleRegistry.get(roleId).getId()));
        }
        else{
            sender.sendMessage(Component.text("Role set operation failed."));
        }
    }
}
