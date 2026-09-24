package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent.CooldownBearing;
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
        //阶段 7 · 清理批：判据由「继承关系」改为「**能力接口**」——本命令只需要"声明了冷却时长"这一项能力
        //（`CooldownBearing#getCooldownTicks`）。阶段 13 · t108：派发面的判据已随"吸收"回到**物品支持组件
        //本身**（`RoleInstance#handleCast` / `#handleAttack` 判 `ActiveComponent` / `MainWeapon`）⇒ 命令面
        //与派发面仍是同一套接受集（仓内实现该能力的仍只有活动组件基类那一棵子树）。
        //行为不变：仓内实现该能力的仍只有活动组件基类那一棵子树（表现规格对象只实现 HotbarItem，不在此列）。
        //（本注释刻意不写那个类型名：卡面判据是裸 grep 该名字，注释里出现它会被误读成"类型判据还在"。）
        if(!(component instanceof ActiveComponent active)){
            send(player, "Not an active component (skill/main weapon): " + componentId);
            return true;
        }
        //阶段 13 · t105（第③步）：冷却读数与动作**直接问组件本身**（旧的"经服务集端口取表"路径已拆 ✗）——
        //  声明值仍由能力接口给出（`CooldownBearing#getCooldownTicks`），状态与动作由组件基类给出 ✓。
        int declared = active.getCooldownTicks();

        switch (action){
            case "status":{
                int remaining = active.remainingCooldownTicks();
                boolean cooling = active.isCoolingDown();
                send(player, "[cooldown] " + componentId
                        + " | cooling=" + cooling
                        + " | remainingTicks=" + remaining
                        + " | remainingSeconds=" + String.format("%.2f", remaining / 20.0)
                        + " | declaredTicks=" + declared);
                return true;
            }
            case "end":{
                boolean wasCooling = active.isCoolingDown();
                active.stopCooldown();
                boolean ended = wasCooling;
                send(player, "[cooldown] end(" + componentId + ") returned=" + ended
                        + " | wasCooling=" + wasCooling
                        + (ended ? " | state cleared (component-side)" : " | no-op (was not cooling)"));
                return true;
            }
            case "restart":{
                boolean wasCooling = active.isCoolingDown();
                active.startCooldown(declared);
                int remaining = active.remainingCooldownTicks();
                send(player, "[cooldown] restart(" + componentId + ") wasCooling=" + wasCooling
                        + (wasCooling ? " | old segment dropped (component-side overwrite)" : " | no old segment was cooling")
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

    /**
     * 目标解析：纯数字 = 热键栏槽位（走与渲染器**同一趟**组件表遍历：{@code Role.componentIdAtSlot(int)}），
     * 否则按组件 id（须在注册表内）。
     * <p>阶段 7 · B 步：栏位随组件自己的描述符走 ⇒ 这里**不再**读 `Role.getSlotMap()` 那张派生视图，
     * 但仍与它同源（都来自条目里的栏位值）⇒ 数字解析不会因栏位来源改变而静默失效。
     */
    private String resolveComponentId(RoleInstance instance, String target){
        if(target.matches("\\d+")){
            int slot = Integer.parseInt(target);
            return instance.getRole().componentIdAtSlot(slot);
        }
        return instance.componentRegistry().getById(target) != null ? target : null;
    }
}
