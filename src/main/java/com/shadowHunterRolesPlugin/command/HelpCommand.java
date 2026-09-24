package com.shadowHunterRolesPlugin.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;

/**
 * 子指令 {@code help}：{@code /role help} —— 打印帮助文案（一行标题 + 每个一级子指令的用法）。
 * <p>
 * 覆盖全部一级子指令（{@code set} / {@code clear} / {@code energy} / {@code debug} / {@code operation}）；
 * 本类**只做展示**，不参与权限判定（门禁在 {@link CommandAccess}）。{@link EnergyCommand} 的参数不匹配回退
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

    /** 帮助文案（唯一副本；{@link EnergyCommand} 的参数不匹配回退也用它） */
    static void sendHelp(CommandSender sender){
        sender.sendMessage(Component.text("=== ROLE SYSTEM COMMAND ==="));
        sender.sendMessage(Component.text("/role set <roleId> <playerName>  --set role"));
        sender.sendMessage(Component.text("/role set <roleId>  --set role for yourself"));
        sender.sendMessage(Component.text("/role clear <playerName>  --clear role"));
        sender.sendMessage(Component.text("/role clear  --clear your role"));
        sender.sendMessage(Component.text("/role energy get [playerName]  --read energy"));
        sender.sendMessage(Component.text("/role energy set <value> [playerName]  --set energy"));
        sender.sendMessage(Component.text("/role debug <cooldown|sched> ...  --debug tools"));
        sender.sendMessage(Component.text("/role operation <query|modify> [player] <componentId[#index]> [payload]  --operate a component"));
    }
}
