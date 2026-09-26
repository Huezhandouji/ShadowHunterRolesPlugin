package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
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
        return "clear <player|@s>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        //★ 服务端也能执行（不必是玩家）—— 目标由第 1 参的选择器指定
        //★ **目标必填**：`/role clear` 不再默认给自己
        if(args.length == 0){
            sender.sendMessage(Component.text(PlayerTargets.targetRequired("/role clear <player|@s>")));
            return true;
        }
        if(args.length == 1){
            handleClear(sender, args[0]);
            return true;
        }
        //既有行为逐字保留：参数个数不匹配时静默返回（原实现是中央 switch 的 break → return true）
        return true;
    }

    private void handleClear(CommandSender sender, String targetName){
        //★ **目标必填**（统一解析：裸名 / @s / 选择器，且必须**恰好命中 1 名**在线玩家）
        PlayerTargets.Result resolved = PlayerTargets.resolve(sender, targetName);
        Player target = resolved.player();
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text(PlayerTargets.rejection(targetName, resolved,
                    "Cannot find the player you provided: " + targetName)));
            return;
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
