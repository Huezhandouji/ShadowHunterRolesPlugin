package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 子指令 {@code clear}：{@code /role clear [playerName]} —— 清除自己或指定在线玩家的角色。
 * <p>
 * 行为与原 {@code RoleCommand} 内联实现**逐字等价**，含参数个数不是 0/1 时的**静默返回**。
 */
public class ClearRoleCommand implements SubCommand {

    private final RoleManager roleManager;

    public ClearRoleCommand(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    @Override
    public String getName(){
        return "clear";
    }

    @Override
    public String getUsage(){
        return "clear [playerName]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)) return true;

        if(args.length == 0){
            handleClear(player, null);
            return true;
        }
        if(args.length == 1){
            handleClear(player, args[0]);
            return true;
        }
        //既有行为逐字保留：参数个数不匹配时静默返回（原实现是中央 switch 的 break → return true）
        return true;
    }

    private void handleClear(Player sender, String targetName){
        // 确定目标玩家
        Player target;
        if(targetName == null){
            target = sender; // 默认自己
        }
        else{
            target = Bukkit.getPlayer(targetName);
            if(target == null || !target.isOnline()){
                sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
                return;
            }
        }

        //检查是否有角色
        if(!roleManager.hasRole(target)){
            if(target.equals(sender)){
                sender.sendMessage(Component.text("You have no role yet!"));
            }
            else{
                sender.sendMessage(Component.text("[" + targetName + "] has no role yet!"));
            }
            return;
        }

        //移除角色
        boolean success = roleManager.clearRole(target);
        if(success){
            if(target.equals(sender)){
                sender.sendMessage(Component.text("Your role has been cleared."));
            }
            else{
                sender.sendMessage(Component.text("The role of [" + targetName + "] has been cleared."));
                target.sendMessage(Component.text("Your role has been cleared."));
            }
        }
        else {
            sender.sendMessage(Component.text("Failed to clear the role."));
        }
    }
}
