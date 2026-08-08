package com.shadowHunterRolesPlugin.core.RoleComponentAware;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;

public interface LifecycleAware {

    //如果一个角色组件需要在设置角色和清理角色时执行操作，实现这个接口

    void onSet(Player player, RoleInstance instance);

    //在清理时，必须清理在onSet创建的所有资源
    void onClear(Player player, RoleInstance instance);


}
