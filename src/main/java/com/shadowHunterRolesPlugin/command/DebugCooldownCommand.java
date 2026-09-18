package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.core.ports.CooldownPort;
import com.shadowHunterRolesPlugin.manager.RoleManager;
import com.shadowHunterRolesPlugin.roleComponent.ActiveComponent;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 子指令 {@code debug cooldown}：{@code /role debug cooldown <status|end|restart> <slot|componentId>}
 * —— 冷却自管理（组件声明值为唯一真值源）的观察与干预四态。
 * <p>
 * **op 门控由 {@link DebugCommand} 在调试树入口承担**（原实现是在本段最前面判定，可见行为相同）。
 * 输出文案与既有内联实现**逐字相同**；{@code status|end|restart} 的语义、参数个数不足时的用法提示、
 * 以及"槽位 → 组件 id"的解析规则均未改动（本卡只重组指令结构，不碰冷却语义）。
 */
public class DebugCooldownCommand implements SubCommand {

    private final RoleManager roleManager;

    public DebugCooldownCommand(RoleManager roleManager){
        this.roleManager = roleManager;
    }

    @Override
    public String getName(){
        return "cooldown";
    }

    @Override
    public String getUsage(){
        return "Usage: /role debug cooldown <status|end|restart> <slot|componentId>";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args){
        if(!(sender instanceof Player player)) return true;

        if(args.length < 2){
            send(player, getUsage());
            return true;
        }
        if(!roleManager.hasRole(player)){
            send(player, "You have no role yet!");
            return true;
        }

        RoleInstance instance = roleManager.getRoleInstance(player);
        if(instance == null){
            send(player, "Role instance not found for you.");
            return true;
        }

        String action = args[0];
        String target = args[1];
        String componentId = resolveComponentId(instance, target);
        if(componentId == null){
            send(player, "No component found for: " + target);
            return true;
        }
        RoleComponent component = instance.componentRegistry().getById(componentId);
        if(!(component instanceof ActiveComponent active)){
            send(player, "Not an active component (skill/main weapon): " + componentId);
            return true;
        }
        ComponentServices services = instance.servicesOf(componentId);
        if(services == null){
            send(player, "No services bound for component: " + componentId);
            return true;
        }
        CooldownPort cooldowns = services.cooldowns();
        int declared = active.getCooldownTicks();

        switch (action){
            case "status":{
                int remaining = cooldowns.remainingTicks();
                boolean cooling = remaining > 0;
                send(player, "[cooldown] " + componentId
                        + " | cooling=" + cooling
                        + " | remainingTicks=" + remaining
                        + " | remainingSeconds=" + String.format("%.2f", remaining / 20.0)
                        + " | declaredTicks=" + declared);
                return true;
            }
            case "end":{
                boolean wasCooling = cooldowns.remainingTicks() > 0;
                boolean ended = cooldowns.end();
                send(player, "[cooldown] end(" + componentId + ") returned=" + ended
                        + " | wasCooling=" + wasCooling
                        + (ended ? " | ENDED_BY_COMPONENT dispatched (onCooldownEnd)" : " | no-op (was not cooling)"));
                return true;
            }
            case "restart":{
                boolean wasCooling = cooldowns.remainingTicks() > 0;
                cooldowns.start(declared);
                int remaining = cooldowns.remainingTicks();
                send(player, "[cooldown] restart(" + componentId + ") wasCooling=" + wasCooling
                        + (wasCooling ? " | RESTARTED dispatched (old segment dropped)" : " | no old segment was cooling")
                        + " | newRemainingTicks=" + remaining);
                return true;
            }
            default:
                send(player, getUsage());
                return true;
        }
    }

    /**
     * 双写：**玩家侧**（Adventure {@code Component}，文本与既有实现逐字相同）+ **服务端日志**（{@link DebugCommand#log}，
     * 带 {@code [command-debug]} 前缀）—— 用户要求"调试信息**也**输出至服务端控制台"，故是相加而非取代。
     */
    private void send(Player player, String text){
        player.sendMessage(Component.text(text));
        DebugCommand.log(text);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, String[] args){
        if(args.length == 1) return SubCommand.filter(List.of("status", "end", "restart"), args[0]);
        return List.of();
    }

    /** 目标解析：纯数字 = 热键栏槽位（读 {@code Role.getSlotMap()}，不猜）；否则按组件 id（须在注册表内）。 */
    private String resolveComponentId(RoleInstance instance, String target){
        if(target.matches("\\d+")){
            Integer slot = Integer.parseInt(target);
            return instance.getRole().getSlotMap().get(slot);
        }
        return instance.componentRegistry().getById(target) != null ? target : null;
    }
}
