package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * 冷识（{@code lethe}）的被动「**腐化的圣装**」：发放四件专属护甲（每件一条固定附魔）。
 *
 * <h2>四件（槽位 → 材质 → 附魔，逐条对齐需求）</h2>
 * <ul>
 *   <li><b>腐化的甲壳</b> · 下界合金头盔（{@link Material#NETHERITE_HELMET}）· 保护 I；</li>
 *   <li><b>连接的枷锁</b> · 锁链胸甲（{@link Material#CHAINMAIL_CHESTPLATE}）· 弹射物保护 I；</li>
 *   <li><b>恍然的受难</b> · 钻石护腿（{@link Material#DIAMOND_LEGGINGS}）· 迅捷潜行 III；</li>
 *   <li><b>无言的面临</b> · 下界合金靴（{@link Material#NETHERITE_BOOTS}）· 摔落保护 X。</li>
 * </ul>
 * <p><b>四件另各带「绑定诅咒 I」</b>（2026-10-31 需求："所有装甲加上绑定诅咒"）——
 * 穿上就脱不掉；与"进场即换装、离场清槽"的既有口径不冲突（{@code stop()} 直接写槽，不靠玩家手动脱）。
 * <p>四个名字取自"被腐化的圣物"这层叙事：甲壳是身体、枷锁是束缚、受难是过程、面临是结局。
 *
 * <h2>★ 为什么附魔用 {@code meta.addEnchant(ench, level, true)} 的第三参 {@code true}</h2>
 * 第三参 = **忽略等级上限**。需求给的等级里有的超出原版附魔台能产出的范围
 * （摔落保护 X、迅捷潜行 III 本身也属非常规获取），不开这个开关，平台会按各自上限截断或拒绝，
 * 附魔等级就不再是需求值。需求原文亦明确要求按此写法施加。
 *
 * <h2>★ 为什么 {@code stop()} 直接把四个槽置 null</h2>
 * 本类**发的是四个槽**（不像只发三件的同族被动要留头盔槽），因此可以严格按 {@code start()} 对称回收：
 * 发几件就收几件。已知代价（与 {@code ResistEquipmentsPassive} / {@code MikomakoEquipmentsPassive} 同规）：
 * 玩家原本穿在这四个槽里的装备会被覆盖并随 {@code stop()} 一并删除 —— 这是"进场即换装"的既有口径，
 * 本类不额外做 PDC 归属判定（那需要额外的物品标记，属另一件事）。
 *
 * <h2>★ 为什么"穿上"的语音不在 {@code start()} 里喊，而在最初几刻补喊</h2>
 * 语音组件的 {@code say(...)} 要求它自己的 {@code start()} 已经跑过（否则 {@code started == false}，
 * 调用被静默丢弃）。按契约的装配顺序，本组件排在语音组件之前 —— 在 {@code start()} 里喊必然丢掉。
 * 因此这里只记一个"待喊"窗口，在 {@code update()} 的最初 {@value #EQUIP_VOICE_RETRY_TICKS} 刻内
 * 逐刻尝试，喊出去就结束；装配序反过来时也一样成立（下一拍就能喊），窗口用完仍失败才放弃。
 * <p>特效与音效不受此限：{@code LetheVfx.equipForge} / {@code LetheSound.equipForge} 是纯静态表现，
 * 在 {@code start()} 当刻播即可。
 */
public class LetheEquipmentsPassive extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "lethe_equipments_passive";

    // ───────── 四件装备的名字（需求逐字）─────────

    /** 头盔名。 */
    public static final String HELMET_NAME = "腐化的甲壳";

    /** 胸甲名。 */
    public static final String CHESTPLATE_NAME = "连接的枷锁";

    /** 护腿名。 */
    public static final String LEGGINGS_NAME = "恍然的受难";

    /** 靴子名。 */
    public static final String BOOTS_NAME = "无言的面临";

    // ───────── 四件装备的附魔等级（每处只声明一次）─────────

    /** 头盔：保护 I。 */
    private static final int HELMET_ENCHANT_LEVEL = 1;

    /** 胸甲：弹射物保护 I。 */
    private static final int CHESTPLATE_ENCHANT_LEVEL = 1;

    /** 护腿：迅捷潜行 III。 */
    private static final int LEGGINGS_ENCHANT_LEVEL = 3;

    /** 靴子：摔落保护 X。 */
    private static final int BOOTS_ENCHANT_LEVEL = 10;

    /**
     * **四件共用**的绑定诅咒等级（2026-10-31 需求：所有护甲 + 绑定诅咒）。
     * <p>四件固定附魔各一条 + 绑定诅咒各一条 —— 绑定诅咒走同一个 {@link #build}，只写一次。
     */
    private static final int BINDING_CURSE_LEVEL = 1;

    // ───────── 主题色（= 契约 §3.4 粒子基调的同一组 ARGB）─────────

    /** 亮蓝。 */
    private static final TextColor BLUE = TextColor.color(0x5AB4FF);

    /** 紫。 */
    private static final TextColor PURPLE = TextColor.color(0x965AFF);

    /** 青白。 */
    private static final TextColor CYAN_WHITE = TextColor.color(0xC8EBFF);

    // ───────── 语音补喊窗口 ─────────

    /** 穿上语音的补喊窗口（刻）。40 刻 = 2 秒，足够覆盖装配序与语音组件的内部节流。 */
    private static final int EQUIP_VOICE_RETRY_TICKS = 40;

    /** 语音组件（穿上时喊「装备锻成」；声明为必需，仍做 null 判空以容忍离线单测）。 */
    private LetheFloatingTextComponent voice;

    /** 还要尝试喊几次"装备锻成"（{@code <= 0} = 不再尝试）。 */
    private int equipVoiceRetryTicks;

    public LetheEquipmentsPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 本组件的被动描述符：被动无栏位 ⇒ 不占热键栏；描述 4 行 = 四件装备各一行。 */
    public static final class Specification extends PassiveSkill.Specification<LetheEquipmentsPassive> {

        public Specification() {
            super(Component.text("腐化的圣装"),
                    List.of(
                            Component.text("腐化的甲壳 · 下界合金头盔（保护 I / 绑定诅咒 I）"),
                            Component.text("连接的枷锁 · 锁链胸甲（弹射物保护 I / 绑定诅咒 I）"),
                            Component.text("恍然的受难 · 钻石护腿（迅捷潜行 III / 绑定诅咒 I）"),
                            Component.text("无言的面临 · 下界合金靴（摔落保护 X / 绑定诅咒 I）")
                    ));
            //穿上时用语音组件喊「装备锻成」⇒ 声明为必需依赖，漏配在装配期就报（而不是静默无声）
            requires(LetheFloatingTextComponent.class);
        }

        @Override
        public LetheEquipmentsPassive create(String id, ComponentServicesPort services) {
            return new LetheEquipmentsPassive(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /** 开始生效：取语音组件、穿上四件、播锻造表现，并打开语音补喊窗口。 */
    @Override
    public void start() {
        voice = getComponent(LetheFloatingTextComponent.class);
        equipVoiceRetryTicks = EQUIP_VOICE_RETRY_TICKS;

        Player player = selfPlayer();
        if (player == null) {
            return;
        }
        equipAll(player);
        LetheVfx.equipForge(player.getWorld(), player.getLocation());
        LetheSound.equipForge(player.getWorld(), player.getLocation());
    }

    /**
     * 停止生效：**发几件收几件** —— 四槽置 null（见类注释的已知代价），清语音引用与补喊窗口。
     * <p>语音放最后清：{@code update()} 的补喊要用它，而 {@code stop()} 之后不会再跑 {@code update()}。
     */
    @Override
    public void stop() {
        Player player = selfPlayer();
        if (player != null) {
            player.getInventory().setHelmet(null);
            player.getInventory().setChestplate(null);
            player.getInventory().setLeggings(null);
            player.getInventory().setBoots(null);
        }
        voice = null;
        equipVoiceRetryTicks = 0;
    }

    /**
     * 每刻只做一件事：在补喊窗口内尝试喊一次「装备锻成」。
     * <p>窗口用尽 / 喊成功 / 语音组件缺席 ⇒ 关闭窗口（之后每刻直接返回，零开销）。
     */
    @Override
    public void update() {
        if (equipVoiceRetryTicks <= 0) {
            return;
        }
        Player player = selfPlayer();
        if (player == null || !player.isOnline()) {
            equipVoiceRetryTicks = 0;
            return;
        }
        if (voice == null) {
            equipVoiceRetryTicks = 0;
            return;
        }
        equipVoiceRetryTicks--;
        if (voice.say(player, LetheFloatingTextComponent.MOMENT_EQUIP_FORGE, null)) {
            equipVoiceRetryTicks = 0;
        }
    }

    // ───────── 装备发放 ─────────

    /** 穿上四件（顺序：头盔 → 胸甲 → 护腿 → 靴子，与槽位自上而下一致）。 */
    private void equipAll(Player player) {
        player.getInventory().setHelmet(build(Material.NETHERITE_HELMET, HELMET_NAME, PURPLE,
                Enchantment.PROTECTION, HELMET_ENCHANT_LEVEL));
        player.getInventory().setChestplate(build(Material.CHAINMAIL_CHESTPLATE, CHESTPLATE_NAME, BLUE,
                Enchantment.PROJECTILE_PROTECTION, CHESTPLATE_ENCHANT_LEVEL));
        player.getInventory().setLeggings(build(Material.DIAMOND_LEGGINGS, LEGGINGS_NAME, CYAN_WHITE,
                Enchantment.SWIFT_SNEAK, LEGGINGS_ENCHANT_LEVEL));
        player.getInventory().setBoots(build(Material.NETHERITE_BOOTS, BOOTS_NAME, PURPLE,
                Enchantment.FEATHER_FALLING, BOOTS_ENCHANT_LEVEL));
    }

    /**
     * 造一件装备：Adventure 显示名（加粗、去斜体）+ 固定附魔 + **绑定诅咒 I**（2026-10-31 需求）。
     * <p>{@code addEnchant} 的第三参 {@code true} = 忽略等级上限（理由见类注释）；
     * meta 取不到时原样返回（平台异常的兜底，不抛）。
     * <p>绑定诅咒对"进场即换装"没有功能影响（{@code stop()} 直接清槽、不依赖玩家手动脱下），
     * 它是需求要求的**叙事/束缚感**：穿上就脱不掉。
     */
    private static ItemStack build(Material material, String name, TextColor color,
                                   Enchantment enchantment, int level) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(Component.text(name, color, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.addEnchant(enchantment, level, true);
        meta.addEnchant(Enchantment.BINDING_CURSE, BINDING_CURSE_LEVEL, true);
        stack.setItemMeta(meta);
        return stack;
    }

    // ───────── 内部工具 ─────────

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }
}
