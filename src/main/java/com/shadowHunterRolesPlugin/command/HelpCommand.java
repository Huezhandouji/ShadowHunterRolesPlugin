package com.shadowHunterRolesPlugin.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * 子指令 {@code help}：{@code /role help} —— 打印既有帮助文案（5 行）。
 * <p>
 * 文案与既有实现**逐字相同**（本卡只重组结构，不改玩家可见输出）。{@link EnergyCommand} 的参数不匹配回退
 * 也调用同一个 {@link #sendHelp(CommandSender)}，避免同一段文案出现两份而走样。
 */
public class HelpCommand implements SubCommand {

    @Override
    public String getName(){
        return "help";
    }

    @Override
    public String getUsage(){
        return "help";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        sendHelp(sender);
        return true;
    }

    /** 既有帮助文案（逐字保留；不再新抄第二份） */
    static void sendHelp(CommandSender sender){
        sender.sendMessage(Component.text("=== ROLE SYSTEM COMMAND ==="));
        sender.sendMessage(Component.text("/role set <roleId> <playerName>  --set role"));
        sender.sendMessage(Component.text("/role set <roleId>  --set role for yourself"));
        sender.sendMessage(Component.text("/role clear <playerName>  --clear role"));
        sender.sendMessage(Component.text("/role clear  --clear your role"));
    }
}
