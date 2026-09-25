package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 子指令 {@code energy}：{@code /role energy get [playerName]} / {@code /role energy set <value> [playerName]}。
 * <p>
 * 行为与原 {@code RoleCommand} 内联实现**逐字等价**，含两处历史行为：
 * <ul>
 *     <li>只写 {@code /role energy}（无动作）→ 输出统一报错文案并返回（原实现里这是 {@code energy} 分支自己的那句）；</li>
 *     <li>动作存在但参数个数不匹配、或动作未知（如 {@code /role energy get a b}、{@code /role energy foo}）
 *     → **回退到帮助文案**（原实现是内层 switch 的 break → {@code sendHelp(player)}），
 *     因此这里复用 {@link HelpCommand#sendHelp(CommandSender)} 这一份文案，不再另抄一遍。</li>
 * </ul>
 */
public class EnergyCommand implements SubCommand {

    private final RoleManager roleManager;

    public EnergyCommand(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    @Override
    public String getName(){
        return "energy";
    }

    @Override
    public String getUsage(){
        return "energy get [playerName] | energy set <value> [playerName]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)) return true;

        if(args.length < 1){
            player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
            return true;
        }

        switch (args[0]){
            case "get":
                //★ **目标必填**：`/role energy get` 不再默认给自己
                if(args.length == 2){
                    handleGetEnergy(player, args[1]);
                    return true;
                }
                if(args.length == 1){
                    player.sendMessage(Component.text(PlayerTargets.targetRequired("/role energy get <player|@s>")));
                    return true;
                }
                break;

            case "set":
                //★ **目标必填**：`/role energy set <n>` 不再默认给自己 —— 参数序 = set <n> <player|@s>
                if(args.length == 3){
                    handleSetEnergy(player, args[1], args[2]);
                    return true;
                }
                if(args.length == 2){
                    player.sendMessage(Component.text(PlayerTargets.targetRequired(
                            "/role energy set <amount> <player|@s>")));
                    return true;
                }
                break;
        }
        HelpCommand.sendHelp(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args){
        if(args.length == 1) return SubCommand.filter(List.of("get", "set"), args[0]);
        return List.of();
    }

    private void handleGetEnergy(Player sender, String targetName){
        //★ **目标必填**（统一解析：裸名 / @s / 选择器，且必须**恰好命中 1 名**在线玩家）
        PlayerTargets.Result resolved = PlayerTargets.resolve(sender, targetName);
        Player target = resolved.player();
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text(PlayerTargets.rejection(targetName, resolved,
                    "Cannot find the player you provided: " + targetName)));
            return;
        }
        if(!roleManager.hasRole(target)) {
            sender.sendMessage(Component.text(target.getName() + " has no role!"));
            return;
        }
        sender.sendMessage(Component.text("The current energy level of [" + target.getName() + "] is: " + energyOf(roleManager, target)));
    }

    private void handleSetEnergy(Player sender, String energyLevel, String targetName){
        //★ **目标必填**（统一解析：裸名 / @s / 选择器，且必须**恰好命中 1 名**在线玩家）
        PlayerTargets.Result resolved = PlayerTargets.resolve(sender, targetName);
        Player target = resolved.player();
        if(target == null || !target.isOnline()){
            sender.sendMessage(Component.text(PlayerTargets.rejection(targetName, resolved,
                    "Cannot find the player you provided: " + targetName)));
            return;
        }
        if(!roleManager.hasRole(target)) {
            sender.sendMessage(Component.text(target.getName() + " has no role!"));
            return;
        }

        try{
            int el = Integer.parseInt(energyLevel);
            writeEnergy(roleManager, target, el);
        }
        catch (NumberFormatException e){
            return;
        }
    }


    /**
     * **读数 / 设值都自己按 id 取能量组件**（★ 容器已删两个能量视图 —— 它不再指名任何组件）。
     * <p>取到通用面后调基类通用面：`readCurrentEnergy()` / `writeCurrentEnergy(...)` ✓
     */
    private static int energyOf(RoleManager roleManager, Player target){
        RoleComponent energy = energyComponentOf(roleManager, target);
        return energy == null ? 0 : energy.readCurrentEnergy();
    }

    private static void writeEnergy(RoleManager roleManager, Player target, int amount){
        RoleComponent energy = energyComponentOf(roleManager, target);
        if (energy != null) {
            energy.writeCurrentEnergy(amount);
        }
    }

    private static RoleComponent energyComponentOf(RoleManager roleManager, Player target){
        RoleInstance instance = roleManager.getRoleInstance(target);
        return instance == null ? null : instance.componentRegistry().getById(EnergyComponent.ID);
    }
}