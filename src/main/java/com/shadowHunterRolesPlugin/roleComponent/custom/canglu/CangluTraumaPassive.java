package com.shadowHunterRolesPlugin.roleComponent.custom.canglu;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.SanTEComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import com.shadowHunterRolesPlugin.roleComponent.custom.red.RedBleedPassive;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.UUID;

public class CangluTraumaPassive extends PassiveSkill {

    /** **本组件的登记 id**（★ 组件自己声明；**不得与同角色内其它组件重名**）。 */
    public static final String ID = "cangluTraumaPassive";

    private SanTEComponent sante;
    private BuffComponent buff;
    private VitalsComponent vitals;

    private HashMap<UUID, Integer> traumaStackRecord;

    public int MAX_TRAUMA_STACK_COUNT = 24;
    public int RESOLVE_TRAUMA_STACK_COUNT = 4;

    private Listener playerDeathListener;

    public CangluTraumaPassive(String id, ComponentServices services, Specification specification) {
        super(id, services, specification);
    }

    public static final class Specification extends PassiveSkill.Specification {

        public Specification(){
            super(Component.text("创伤"), Component.text("苍鹭的创伤层数被动"));
            requires(SanTEComponent.class);
            requires(BuffComponent.class);
            requires(VitalsComponent.class);
        }

        @Override
        public CangluTraumaPassive create(String id, ComponentServices services){
            return new CangluTraumaPassive(id, services, this);
        }
    }

    public final class PlayerDeathListener implements Listener {
        @EventHandler
        public void onPlayerDeath(PlayerDeathEvent e){
            UUID pid =  e.getEntity().getUniqueId();
            traumaStackRecord.remove(pid);
        }
    }

    @Override
    public void awake(){
        super.awake();
        traumaStackRecord = new HashMap<>();

        playerDeathListener = new PlayerDeathListener();
        ShadowHunterRolesPlugin.getInstance().getServer().getPluginManager().registerEvents(playerDeathListener, ShadowHunterRolesPlugin.getInstance());
    }

    @Override
    public void start(){
        super.start();
        sante = svc().components().get(SanTEComponent.class);
        buff = svc().components().get(BuffComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    @Override
    public void stop(){
        super.stop();
        HandlerList.unregisterAll(playerDeathListener);
        playerDeathListener = null;
        traumaStackRecord.clear();
        traumaStackRecord = null;
    }

    public boolean addTraumaStack(UUID pid, int count){
        if(!svc().roleInfo().isHostileTo(pid)){
            return false;
        }

        if(!traumaStackRecord.containsKey(pid)){
            traumaStackRecord.put(pid, count);
        }
        else{
            int newStackCount = Math.clamp(traumaStackRecord.get(pid) + count, 0, MAX_TRAUMA_STACK_COUNT);
            traumaStackRecord.put(pid, newStackCount);
        }

        return true;
    }

    public boolean resolveTraumaStack(UUID pid){
        if(!svc().roleInfo().isHostileTo(pid)){
            return false;
        }
        if(!traumaStackRecord.containsKey(pid)){
            return false;
        }

        Player target =  Bukkit.getPlayer(pid);
        if(target == null || target.isDead()){
            return false;
        }

        int preStack = traumaStackRecord.get(pid);
        if(preStack < MAX_TRAUMA_STACK_COUNT){
            return false;
        }

        int newStack = Math.clamp(preStack - RESOLVE_TRAUMA_STACK_COUNT, 0, MAX_TRAUMA_STACK_COUNT);
        traumaStackRecord.put(pid, newStack);

        Player self = svc().self().player();

        vitals.trueDamage(target, self, 4);
        buff.applyPotionEffect(PotionEffectType.ABSORPTION.createEffect(160, 2));
        sante.increase(12);

        return true;
    }

}
