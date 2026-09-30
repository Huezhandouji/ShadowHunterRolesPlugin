package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 子指令 {@code debug}：调试工具树的入口，只做 op 门控 + 按话题路由（对齐 {@code SHDFGamePlugin} 的
 * {@code DebugCommand} 风格，但把两个话题各自独立成类）。
 * <p>用法：{@code /role debug <cooldown|sched> ...}
 * <ul>
 *     <li>{@code cooldown} → {@link DebugCooldownCommand}：冷却四态查看/结束/重启；</li>
 *     <li>{@code sched} → {@link DebugSchedCommand}：调度器实证探针（矩阵 / 端口链 / 取消语义）。</li>
 * </ul>
 * <p><b>门控与原实现逐字等价</b>：原内联实现里 op 判定位于 {@code handleDebugCooldown} 的最前面、且该方法先于
 * {@code handleDebugSched} 调用 ⇒ 对非 op 玩家，任何形式的 {@code /role debug ...}（含无话题、未知话题）
 * 都得到同一句权限文案，而不是"参数错误"文案。本类把该门控提升到调试树入口，保持同一可见行为。
 */
public class DebugCommand implements SubCommand {

    //门控文案只保留一处：唯一副本 = {@link CommandAccess#NO_PERMISSION}，本类直接引用它（全仓仅此一处口径）。

    /** 控制台/日志路径的统一前缀（按它即可 grep 出本类全部日志行）。 */
    static final String CONSOLE_PREFIX = "[command-debug]";

    /**
     * 把与玩家侧同一份调试文本写入服务端日志（`logs/latest.log`）—— 只新增日志路径，不动 chat：
     * 玩家侧仍是 Adventure {@code Component}（两者相加，不互相取代）。插件实例不可用时静默跳过
     * （不抛异常、不改变玩家侧可见行为）。
     */
    static void log(String text){
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if(plugin == null) return;
        plugin.getLogger().info(CONSOLE_PREFIX + " " + text);
    }

    private final Map<String, SubCommand> topics = new TreeMap<>();

    public DebugCommand(RoleManager roleManager){
        registerTopic(new DebugCooldownCommand(roleManager));
        registerTopic(new DebugSchedCommand(roleManager));
    }

    private void registerTopic(SubCommand topic){
        topics.put(topic.getName().toLowerCase(Locale.ROOT), topic);
    }

    @Override
    public String getName(){
        return "debug";
    }

    @Override
    public String getUsage(){
        return "debug <cooldown|sched> ...";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)) return true;

        //门控：与根入口同一 helper（等级 ≥ 3）—— 冗余的一道防线，口径只有一套
        if(!CommandAccess.check(sender, "/role debug")){
            player.sendMessage(Component.text(CommandAccess.NO_PERMISSION));
            return true;
        }

        SubCommand topic = args.length == 0 ? null : topics.get(args[0].toLowerCase(Locale.ROOT));
        if(topic == null){
            player.sendMessage(Component.text("Wrong arguments. Use /role help to learn how to use."));
            return true;
        }

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return topic.execute(player, rest);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args){
        //补全与执行用同一道门（同 helper）：非玩家或等级 < 3 不暴露调试话题
        if(!(sender instanceof Player player) || !CommandAccess.check(sender, "/role debug (tab)")) return List.of();

        if(args.length == 1) return SubCommand.filter(topics.keySet(), args[0]);

        SubCommand topic = topics.get(args[0].toLowerCase(Locale.ROOT));
        if(topic == null) return List.of();

        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);
        return topic.onTabComplete(player, rest);
    }
}
