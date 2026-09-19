package com.shadowHunterRolesPlugin.core.RoleComponentAware;

import com.shadowHunterRolesPlugin.core.RoleInstance;
import org.bukkit.entity.Player;

public interface LifecycleAware {

    //如果一个角色组件需要在角色开始生效和停止生效时执行操作，实现这个接口

    //awake阶段的目的是解析跨组件依赖并缓存引用，在全部组件创建完成之后、start之前执行
    //必须保证幂等，且不得改动任何玩家可见状态：此时stop尚未调用，重入会叠加效果
    default void awake(Player player, RoleInstance instance) {}

    //开始生效：初始化数据、发放装备、注册tick等
    void start(Player player, RoleInstance instance);

    //停止生效，与start严格对称：在stop时，必须清理在start创建的所有资源
    void stop(Player player, RoleInstance instance);


}
