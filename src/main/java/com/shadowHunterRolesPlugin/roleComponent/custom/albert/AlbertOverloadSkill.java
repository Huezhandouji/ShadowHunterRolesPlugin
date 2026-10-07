package com.shadowHunterRolesPlugin.roleComponent.custom.albert;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.Skill;
import com.shadowHunterRolesPlugin.roleComponent.builtin.BuffComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EnergyComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 「艾尔伯特」技能之四：**过载协议**（下界之星）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li>立刻<b>消耗所有无人机层数</b>，并把索敌范围<b>扩大到 30 格</b>；</li>
 *   <li><b>每有一层无人机层数就多生成一个自杀式无人机</b>；</li>
 *   <li>自杀式无人机<b>原地蓄力 3 秒</b>后<b>逐个飞出</b>攻击敌人；</li>
 *   <li>触碰到敌人后<b>停滞蓄力 1 秒</b>后爆炸，对周围
 *       {@value AlbertDroneSystem#KAMIKAZE_BLAST_RADIUS} 格内敌人造成
 *       <b>10 点灵魂伤害</b>与 <b>10 点特殊值伤害</b>；</li>
 *   <li>若成功命中敌人则<b>为艾尔伯特回复 8 点生命</b>；</li>
 *   <li>CD <b>30 秒</b>（<b>技能完全后冷却</b>）；能量消耗 <b>70</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报一："技能完全后冷却"的判据 = 自杀式无人机全部结束</h2>
 * 本技能没有"持续时间"这个字段 —— 它的"技能过程"就是<b>那批自杀式无人机的生命周期</b>
 * （蓄力 → 逐个起飞 → 各自引爆）。⇒ {@link #castActive} 在施放时置真，
 * 每刻问 {@link AlbertDroneSystem#overloadBusy()}；一旦队列空了就 {@code startCooldown()}。
 *
 * <p>★ 为什么要这么绕：工程既有口径是"冷却在<b>过程后</b>起算"（先例：马提娜的远程医疗、
 * 猎手的遁形），而框架的冷却没有"延迟启动"的概念 ⇒ 只能由组件自己在 {@code update()} 里起。
 *
 * <h2>★ 口径申报二：0 架无人机时也能放（只是没有自杀式无人机）</h2>
 * 需求写的是"立刻消耗所有无人机层数，每有一层就多生成一个" —— 「0 层」是这句话的合法输入
 * ⇒ 仍然消耗 70 能量、仍然扩到 30 格，只是不产生自杀式无人机（并且立刻进入冷却）。
 * 这比"0 层时禁止释放"更符合字面，也避免了"图标突然不可点"的困惑。
 *
 * <h2>★ 口径申报三：施放那一刻就把层数抽干净</h2>
 * "消耗所有无人机层数" ⇒ 编队在施放瞬间清空（{@code drones.clear()}）。
 * 这同时意味着<b>主动防御层数归零</b>（抗性 255 立刻失效）—— 这正是"过载"的代价，
 * 与需求"用你们换一条路"的台词方向一致。
 */
public class AlbertOverloadSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_skill_overload";

    /** 冷却：30 秒（需求原话"CD-30"，且"技能完全后冷却"）。 */
    private static final int COOLDOWN_TICKS = 600;

    /** 能量消耗（需求原话"消耗70能量"）。 */
    private static final int ENERGY_COST = 70;

    private AlbertDroneSystem drones;
    private EnergyComponent energy;
    private BuffComponent buff;
    private AlbertFloatingTextComponent floatingText;

    /** 本技能是否正在"进行中"（用来实现"技能完全后冷却"）。 */
    private boolean castActive;

    public AlbertOverloadSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符。 */
    public static final class Specification extends Skill.Specification<AlbertOverloadSkill> {

        public Specification() {
            super(Component.text("过载协议"),
                    List.of(
                            Component.text("消耗所有无人机层数，索敌范围扩大到 30 格"),
                            Component.text("每消耗一层，就生成一架自杀式无人机"),
                            Component.text("自杀式无人机原地蓄力 3 秒后逐个飞出"),
                            Component.text("触碰敌人后停滞 1 秒爆炸：7 格内敌人受到 10 点灵魂伤害与 10 点特殊值伤害"),
                            Component.text("成功命中敌人则为艾尔伯特回复 8 点生命"),
                            Component.text("冷却 30 秒（技能完全后开始计算）· 消耗 70 能量")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.NETHER_STAR);
            requires(AlbertDroneSystem.class).requires(EnergyComponent.class)
                    .requires(BuffComponent.class)
                    .requires(AlbertFloatingTextComponent.class);
        }

        @Override
        public AlbertOverloadSkill create(String id, ComponentServicesPort services) {
            return new AlbertOverloadSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        drones = svc().components().get(AlbertDroneSystem.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        floatingText = svc().components().get(AlbertFloatingTextComponent.class);
        castActive = false;
    }

    @Override
    public void stop() {
        castActive = false;
    }

    /**
     * 每刻：技能进行中且队列已空 ⇒ <b>这时才</b>启动冷却（"技能完全后冷却"）。
     */
    @Override
    public void update() {
        if (!castActive || drones == null) {
            return;
        }
        if (drones.overloadBusy()) {
            return;
        }
        castActive = false;
        startCooldown();
        Player owner = svc().self().player();
        World world = owner == null ? null : owner.getWorld();
        if (world != null) {
            AlbertSound.beaconDeactivateAlbertOverloadEndSound(world, owner.getLocation());
            AlbertVfx.overloadAlbertAftermath(world, owner.getLocation(), 0d);
        }
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || drones == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (isCoolingDown() || castActive) {
            return;
        }
        if (energy != null && !energy.tryConsume(ENERGY_COST)) {
            return;
        }
        World world = owner.getWorld();
        if (world == null) {
            return;
        }

        //① 抽干编队 ⇒ 每层一个自杀式无人机；② 索敌扩到 30 格
        int spawned = drones.startOverload();
        castActive = true;

        //★ 边界：0 层 ⇒ 队列本来就空 ⇒ 下一秒的 update() 会立刻进冷却（不必特判）
        Location at = owner.getLocation();
        AlbertSound.wardenChargeAlbertOverloadCastSound(world, at);
        AlbertVfx.overloadAlbertAftermath(world, at, 0d);

        if (floatingText != null) {
            floatingText.saySkill(owner, AlbertFloatingTextComponent.MOMENT_SKILL_4);
            if (spawned > 0) {
                floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_OVERLOAD);
            }
        }
    }

    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /** 读口：技能是否进行中（探针用）。 */
    public boolean overloadActive() {
        return castActive;
    }

    /**
     * **技能物品**：基类三态 + "编队层数越多，这一发越值钱"的提示
     * （层数 > 0 ⇒ 常亮，让玩家一眼知道现在放能换出几架自杀式无人机）。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        boolean worthIt = drones != null && drones.droneCount() > 0;
        if (worthIt && canUse()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
