package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.registry.RoleRegistry;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RoleCommand implements CommandExecutor {

    private final RoleManager roleManager;
    //阶段 4（⑤）：RoleRegistry 改为构造注入（D-2 静态桥已删）
    private final RoleRegistry roleRegistry;

    public RoleCommand(RoleManager roleManager, RoleRegistry roleRegistry){
        this.roleManager = roleManager;
        this.roleRegistry = roleRegistry;
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
                if(args.length < 2){
                    player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
                    return true;
                }
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
                        break;
                    case "set":
                        if(args.length == 3){
                            handleSetEnergy(player, args[2], null);
                            return true;
                        }
                        if(args.length == 4){
                            handleSetEnergy(player, args[2], args[3]);
                            return true;
                        }
                        break;
                }
                sendHelp(player);
                return true;

            case "debug":
                //临时调试入口（冷却自管理冒烟）：/role debug cooldown <status|end|restart> <slot|componentId>
                if(handleDebugCooldown(player, args)) return true;
                player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
                return true;

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
        if(!roleRegistry.contains(roleId)){
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
            if(targetName == null) sender.sendMessage(Component.text("Your role has been set: " + roleRegistry.get(roleId).getId()));
            else sender.sendMessage(Component.text("The role of player [ " + targetName + "] has been set: " + roleRegistry.get(roleId).getId()));
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

    // ═════════ 临时调试入口（冷却自管理冒烟用；**冒烟结束可整段删除**）═════════
    // 删除清单：本段（到本类末尾前的全部 DEBUG 方法）+ 上面 switch 的 case "debug" + 本行上方 5 个 import
    //           + RoleInstance.servicesOf(...)（同一段注释里也有删除说明）。

    private boolean handleDebugCooldown(Player player, String[] args){
        //门控：仅 op（对普通玩家零可见行为）
        if(!player.isOp()){
            player.sendMessage(Component.text("You do not have permission to use this command."));
            return true;
        }
        if(args.length < 2 || !"cooldown".equals(args[1])) return false;
        if(args.length < 4){
            player.sendMessage(Component.text("Usage: /role debug cooldown <status|end|restart> <slot|componentId>"));
            return true;
        }
        if(!roleManager.hasRole(player)){
            player.sendMessage(Component.text("You have no role yet!"));
            return true;
        }

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null){
            player.sendMessage(Component.text("Role instance not found for you."));
            return true;
        }

        String action = args[2];
        String target = args[3];
        String componentId = resolveComponentId(instance, target);
        if(componentId == null){
            player.sendMessage(Component.text("No component found for: " + target));
            return true;
        }
        RoleComponent component = instance.componentRegistry().getById(componentId);
        if(!(component instanceof ActiveComponent active)){
            player.sendMessage(Component.text("Not an active component (skill/main weapon): " + componentId));
            return true;
        }
        ComponentServices services = instance.servicesOf(componentId);
        if(services == null){
            player.sendMessage(Component.text("No services bound for component: " + componentId));
            return true;
        }
        CooldownPort cooldowns = services.cooldowns();
        int declared = active.getCooldownTicks();

        switch (action){
            case "status":{
                int remaining = cooldowns.remainingTicks();
                boolean cooling = remaining > 0;
                player.sendMessage(Component.text("[cooldown] " + componentId
                        + " | cooling=" + cooling
                        + " | remainingTicks=" + remaining
                        + " | remainingSeconds=" + String.format("%.2f", remaining / 20.0)
                        + " | declaredTicks=" + declared));
                return true;
            }
            case "end":{
                boolean wasCooling = cooldowns.remainingTicks() > 0;
                boolean ended = cooldowns.end();
                player.sendMessage(Component.text("[cooldown] end(" + componentId + ") returned=" + ended
                        + " | wasCooling=" + wasCooling
                        + (ended ? " | ENDED_BY_COMPONENT dispatched (onCooldownEnd)" : " | no-op (was not cooling)")));
                return true;
            }
            case "restart":{
                boolean wasCooling = cooldowns.remainingTicks() > 0;
                cooldowns.start(declared);
                int remaining = cooldowns.remainingTicks();
                player.sendMessage(Component.text("[cooldown] restart(" + componentId + ") wasCooling=" + wasCooling
                        + (wasCooling ? " | RESTARTED dispatched (old segment dropped)" : " | no old segment was cooling")
                        + " | newRemainingTicks=" + remaining));
                return true;
            }
            default:
                player.sendMessage(Component.text("Usage: /role debug cooldown <status|end|restart> <slot|componentId>"));
                return true;
        }
    }

    /** 目标解析：纯数字 = 热键栏槽位（读 {@code Role.getSlotMap()}，不猜）；否则按组件 id（须在注册表内）。 */
    private String resolveComponentId(RoleInstance instance, String target){
        if(target.matches("\\d+")){
            Integer slot = Integer.parseInt(target);
            return instance.getRole().getSlotMap().get(slot);
        }
        return instance.componentRegistry().getById(target) != null ? target : null;
    }

    private void sendHelp(Player player){
        player.sendMessage(Component.text("=== ROLE SYSTEM COMMAND ==="));
        player.sendMessage(Component.text("/role set <roleId> <playerName>  --set role"));
        player.sendMessage(Component.text("/role set <roleId>  --set role for yourself"));
        player.sendMessage(Component.text("/role clear <playerName>  --clear role"));
        player.sendMessage(Component.text("/role clear  --clear your role"));
    }
}
