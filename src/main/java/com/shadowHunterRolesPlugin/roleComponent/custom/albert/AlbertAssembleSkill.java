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
 * 「艾尔伯特」技能之一：**高斯装配**（铁锭）。
 *
 * <h2>需求逐条</h2>
 * <ol>
 *   <li><b>立刻部署一架高斯无人机</b>；</li>
 *   <li><b>立刻派出拥有的所有无人机再索敌攻击一次</b> —— 无视 6 秒节拍与 0.7 秒闸门；
 *       ★ 这一波的<b>索敌范围抬到 25 格</b>，<b>走出这一波就回到 15 格</b>（见口径申报四）；</li>
 *   <li><b>若释放时已经满 4 架</b> ⇒ 额外在原地生成一个<b>哨戒无人机</b>：
 *       不计入无人机层数、仅能存在一个、固定常驻原地、对进入 r=10 的敌人持续缓慢 I、
 *       并向艾尔伯特报警（actionbar）、存续时间无限；</li>
 *   <li>CD <b>8 秒</b>；能量消耗 <b>10</b>。</li>
 * </ol>
 *
 * <h2>★★ 口径申报四：25 格是"一波"的范围，不是"一段窗口"（2026-10-06 收窄）</h2>
 * 旧实现开了一个 15 秒的 25 格窗口 ⇒ 那段时间无人机一直能隔着 25 格追人，
 * 比"这一轮打得更远"慷慨太多。现在改成：
 * <b>只在"派出全部无人机"这一次目标选取里生效</b>，调用返回即恢复 15 格
 * （实现见 {@link AlbertDroneSystem#assembleNow()}）。
 * <p>已经起飞的那些无人机不受影响 —— 它们的<b>目标在起飞那一刻就定死了</b>，
 * 被收窄的只是"下一轮该打谁"。
 *
 * <h2>★ 口径申报一："若释放时满 4 架"取的是<b>施放前</b>的读数</h2>
 * 需求写的是"若<b>释放时</b>无人机满 4 架" ⇒ 判定必须发生在第 ① 步"部署一架"<b>之前</b>。
 * 若先部署再判定，则"3 架时释放"会变成 4 架 ⇒ 白送一个哨戒 —— 与需求相反。
 *
 * <h2>★ 口径申报二：哨戒无人机不计入层数</h2>
 * 需求明写"不计入无人机层数的哨戒无人机" ⇒ 它住在 {@link AlbertDroneSystem} 的
 * {@code sentry} 字段里，与 {@code drones} 列表完全分离 ⇒
 * 它既不会被「主动防御」消耗，也不会被技能4「过载协议」抽成自杀式无人机。
 *
 * <h2>★ 口径申报三：重复释放会<b>替换</b>旧的哨戒</h2>
 * 需求说"仅能存在一个" ⇒ 再次满编释放时旧的被新位置替换
 * （而不是"已有就不再生成"，那样玩家换个地方放哨会失败）。
 */
public class AlbertAssembleSkill extends Skill {

    /** 本组件的登记 id。 */
    public static final String ID = "albert_skill_assemble";

    /** 冷却：8 秒（需求原话"CD-8"）。 */
    private static final int COOLDOWN_TICKS = 160;

    /** 能量消耗（需求原话"能量消耗10"）。 */
    private static final int ENERGY_COST = 10;

    private AlbertDroneSystem drones;
    private EnergyComponent energy;
    private BuffComponent buff;
    private AlbertFloatingTextComponent floatingText;

    public AlbertAssembleSkill(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的描述符（栏位由装配点 {@code setSlot} 指定）。 */
    public static final class Specification extends Skill.Specification<AlbertAssembleSkill> {

        public Specification() {
            super(Component.text("高斯装配"),
                    List.of(
                            Component.text("立刻部署一架高斯无人机"),
                            Component.text("立刻派出所有无人机再索敌攻击一次（这一波索敌范围 25 格，随后回到 15 格）"),
                            Component.text("释放时若已满 4 架，额外在原地留下一个哨戒无人机"),
                            Component.text("哨戒无人机：r=10 持续缓慢 I，有人越线会报警"),
                            Component.text("冷却 8 秒 · 消耗 10 能量")
                    ),
                    COOLDOWN_TICKS,
                    ENERGY_COST,
                    Material.IRON_INGOT);
            requires(AlbertDroneSystem.class).requires(EnergyComponent.class)
                    .requires(BuffComponent.class)
                    .requires(AlbertFloatingTextComponent.class);
        }

        @Override
        public AlbertAssembleSkill create(String id, ComponentServicesPort services) {
            return new AlbertAssembleSkill(id, services, this);
        }
    }

    @Override
    public void start() {
        drones = svc().components().get(AlbertDroneSystem.class);
        energy = svc().components().get(EnergyComponent.class);
        buff = svc().components().get(BuffComponent.class);
        floatingText = svc().components().get(AlbertFloatingTextComponent.class);
    }

    @Override
    public void onCast(CastSignal signal) {
        Player owner = svc().self().player();
        if (owner == null || drones == null || buff == null || !buff.canCastSkill()) {
            return;
        }
        if (isCoolingDown()) {
            return;
        }
        if (energy != null && !energy.tryConsume(ENERGY_COST)) {
            return;
        }

        // ★ 判定必须在"部署一架"之前（见类注释口径申报一）
        boolean wasFull = drones.isFull();

        //① 立刻部署一架
        drones.deployOne();
        //② ③ 立刻派出所有无人机（索敌范围只在这一波抬到 25 格，返回即回 15 格）
        drones.assembleNow();
        //④ 满编 ⇒ 哨戒无人机
        boolean sentryPlaced = wasFull && drones.deploySentry(owner.getLocation());

        World world = owner.getWorld();
        if (world != null) {
            Location at = owner.getLocation();
            AlbertVfx.deployAlbertAssemble(world, at, 0d);
            AlbertSound.conduitActivateAlbertAssembleCastSound(world, at);
            if (sentryPlaced) {
                AlbertVfx.sentryAlbertAssemble(world, at, 0d);
                AlbertSound.eyeOfEnderAlbertSentryDeploySound(world, at);
            }
        }

        if (floatingText != null) {
            floatingText.saySkill(owner, AlbertFloatingTextComponent.MOMENT_SKILL_1);
            if (sentryPlaced) {
                floatingText.saySpecial(owner, AlbertFloatingTextComponent.MOMENT_SENTRY);
            }
        }

        startCooldown();
    }

    /** 闸门：被沉默 / 眩晕时不可施放。 */
    @Override
    protected boolean canUse() {
        return buff != null && buff.canCastSkill();
    }

    /** 当前能量（参与能量维度）。 */
    @Override
    protected int currentEnergy() {
        return energy != null ? energy.current() : getEnergyCost();
    }

    /**
     * 在基类三态画法之上叠一条"本次能否额外生成哨戒"的提示：
     * 满编（= 会额外出哨戒）⇒ 附魔光效常亮。
     */
    @Override
    public ItemStack buildItem() {
        ItemStack stack = super.buildItem();
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        if (drones != null && drones.isFull()) {
            meta.setEnchantmentGlintOverride(Boolean.TRUE);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
