package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import com.shadowHunterRolesPlugin.core.ports.ComponentServices;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.VitalsComponent;
import net.kyori.adventure.text.Component;
import com.shadowHunterRolesPlugin.roleComponent.frameworkLevel.SanTEComponent;

public class AutoRecoverSanTEHealthPassive extends PassiveSkill {

    private SanTEComponent sante;

    private int noEnemySurroundTime = 0;
    private int tickSecondRecord = 0;

    //阶段 13 · t101（第①批）：生命能力改向**组件本身**取用（R-6），引用缓存在 start()。
    //旧写法经服务集的白名单端口成员取用；该端口是**纯转发**（同一组件的同一方法）⇒ 逐字等价。
    private VitalsComponent vitals;

    public AutoRecoverSanTEHealthPassive(String id, ComponentServices services) {
        super(id, services, Component.text("自动恢复SanTE"), Component.text("当周围10格没有敌人五秒后, 开始自动恢复SanTE, 每秒3"));
    }

    /**
     * **开始生效**（阶段 13 · t101 第①批新增）：把生命组件**一次查好**缓存进字段 ✓。
     * <p>为什么在 {@code start()} 而不是 {@code awake()}：硬规矩 **R-4** 禁止在 {@code awake()} 里
     * 取用其他组件 ✗（awake 只做构造期自检 / 只读自身）；`start()` 相容器已冻结 ⇒ 容器查找合法 ✓。
     * <p>为什么缓存：本被动**每 tick** 跑一次 `update()`，回血点在其内层判定里 ⇒ 重复查容器是纯浪费；
     * 旧的端口引用本身也是**构造期就持有的引用** ⇒ 缓存与旧口径同族 ✓。
     * <p>等价性：该端口是**纯转发**（转发到本实例的同一个生命组件、同一个方法）⇒ 逐字等价 ✓。
     */
    @Override
    public void start() {
        sante = svc().components().get(SanTEComponent.class);
        vitals = svc().components().get(VitalsComponent.class);
    }

    /**
     * 批次⑤（B⑤）迁移：旧 `update(Player, RoleInstance)` 的**逐条等价**新写法。
     * 数值/间隔**逐字不变**：半径 `10`、累计上限 `200` tick、每秒判定 `20` tick、`+3` SanTE、`+1` 生命；
     * SanTE 为 0 时提前 return 的短路**保持**。阵营判定走 `svc().roleInfo().hasEnemyInRange(10)`
     * （语义 = 原 `SkillUtil.hasEnemyInRange`）；SanTE 改走 `sante.increase(3)`、
     * 生命改走**生命组件**的回血入口（阶段 13 · t101 第①批：不再经服务集端口，改为组件本身用）✓。
     * 容器在 tick 里对该组件广播 `update()`（B⑤ 第 1 步，`:802`）。
     */
    @Override
    public void update() {
        if(svc().roleInfo().hasEnemyInRange(10)){
            if(noEnemySurroundTime != 0) noEnemySurroundTime = 0;
        }
        else{
            if(noEnemySurroundTime < 200){
                noEnemySurroundTime++;
            }
        }

        if(sante.current() <= 0) return;

        if(noEnemySurroundTime >= 200){
            tickSecondRecord++;
            if(tickSecondRecord >= 20){
                tickSecondRecord = 0;
                sante.increase(3);
                vitals.heal(1);
            }
        }
    }
}
