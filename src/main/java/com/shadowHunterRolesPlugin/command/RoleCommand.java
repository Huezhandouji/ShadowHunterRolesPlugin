package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.scheduler.BukkitScheduler;
import org.jetbrains.annotations.NotNull;

public class RoleCommand implements CommandExecutor {

    private final RoleManager roleManager;

    public RoleCommand(RoleManager roleManager){
        this.roleManager = roleManager;
    }


    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        if(!(sender instanceof Player player)){
            sender.sendMessage("Only players can execute this command!");
            return true;
        }

        if(args.length == 0){
            //sendHelp(player);
            return true;
        }

        switch (args[0]){
            case "set":
                if(args.length == 2){
                    handleSet(player, args[1], null);
                    return true;
                }
                else if(args.length == 3){
                    handleSet(player, args[1], args[2]);
                    return true;
                }
                break;

            case "clear":
                if(args.length == 1){
                    handleClear(player, null);
                    return true;
                }
                else if(args.length == 2){
                    handleClear(player, args[1]);
                    return true;
                }
                break;

            case "energy":
                switch (args[1]){
                    case "get":
                        if(args.length == 2) {
                            handleGetEnergy(player, null);
                            return true;
                        }
                        if(args.length == 3){
                            handleGetEnergy(player, args[2]);
                            return true;
                        }
                    case "set":
                        if(args.length == 3){
                            handleSetEnergy(player, args[2], null);
                            return true;
                        }
                        if(args.length == 4){
                            handleSetEnergy(player, args[2], args[3]);
                            return true;
                        }
                }

            case "help":
                sendHelp(player);
                return true;

            default:
                player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
                return true;
        }
        return true;
    }

    private void handleGetEnergy(Player sender, String targetName){
        Player target;

        if(targetName == null) target = sender;
        else target = Bukkit.getPlayer(targetName);
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
            return;
        }
        if(!roleManager.hasRole(target)) {
            sender.sendMessage(Component.text(target.getName() + " has no role!"));
            return;
        }
        sender.sendMessage(Component.text("The current energy level of [" + target.getName() + "] is: " + roleManager.getRoleInstance(target).getCurrentEnergy()));
    }

    private void handleSetEnergy(Player sender, String energyLevel, String targetName){
        Player target;

        if(targetName == null) target = sender;
        else target = Bukkit.getPlayer(targetName);
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
            return;
        }
        if(!roleManager.hasRole(target)) {
            sender.sendMessage(Component.text(target.getName() + " has no role!"));
            return;
        }

        try{
            int el = Integer.parseInt(energyLevel);
            roleManager.getRoleInstance(target).setCurrentEnergy(el);
        }
        catch (NumberFormatException e){
            return;
        }


    }

    private void handleSet(Player sender, String roleId, String targetName){
        if(!RoleRegistry.hasRole(roleId)){
            sender.sendMessage(Component.text("Role '" + roleId + "' not exist!"));
            return;
        }

        //选择角色
        Player target;
        if(targetName == null){
            target = sender; // 默认是自己
        }
        else{
            target = Bukkit.getPlayer(targetName);
            if(target == null || !target.isOnline()){
                sender.sendMessage(Component.text("Cannot find the player you provided: " + targetName));
                return;
            }
        }

        boolean success = roleManager.selectRole(target, roleId);
        if(success){
            if(targetName == null) sender.sendMessage(Component.text("Your role has been set: " + RoleRegistry.getRole(roleId).getId()));
            else sender.sendMessage(Component.text("The role of player [ " + targetName + "] has been set: " + RoleRegistry.getRole(roleId).getId()));
        }
        else{
            sender.sendMessage(Component.text("Role set operation failed."));
        }
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
        return;
    }

    private void sendHelp(Player player){
        player.sendMessage(Component.text("=== ROLE SYSTEM COMMAND ==="));
        player.sendMessage(Component.text("/role set <roleId> <playerName>  --set role"));
        player.sendMessage(Component.text("/role set <roleId>  --set role for yourself"));
        player.sendMessage(Component.text("/role clear <playerName>  --clear role"));
        player.sendMessage(Component.text("/role clear  --clear your role"));
    }
}
