package com.shadowHunterRolesPlugin.roleComponent.custom.lethe;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.RoleComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 冷识「箭袋」组件：**血泪 / 箭矢的发放与持续补给**。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li><b>进场发放</b>：2 根「血泪」（{@code TIPPED_ARROW}，瞬时伤害 II，金色加粗名）+
 *       14 根「箭矢」（{@code ARROW}，白色加粗名）；</li>
 *   <li><b>持续补给</b>：每 6 秒（120 刻）尝试补 1 根箭矢；
 *       **箭矢满 14 之后**每 10 秒（200 刻）尝试补 1 根血泪（未满时血泪计时清零）；
 *       只有当前数量低于上限（箭矢 14 / 血泪 2）才补；</li>
 *   <li><b>角色清除时回收</b>：把带本组件标记的血泪 / 箭矢从背包里移除（不残留）。</li>
 * </ul>
 *
 * <h2>为什么"每拍补 1 根"而不是"缺多少补多少"</h2>
 * 补给的节拍本身就是这个角色的资源节奏：一次补满会让"打空箭袋"没有代价，也会让补给表现
 * （箭袋回收入袋的光）只在开战前出现一次。按节拍补 ⇒ 玩家能感知"箭在回"。
 *
 * <h2>为什么"背包满了就跳过这一拍，不欠账"</h2>
 * 若把没补上的数量记成欠账，玩家一腾出空间就会一次性涌出十几根箭 —— 看起来像凭空复制，
 * 且要多维护一份"欠了几发"的状态。跳过 = 这一拍作废，规则单一且可预期：
 * 背包满 ⇒ 这次补给失败，下一拍再试。
 *
 * <h2>物品身份为什么用 PDC 标记（而不是材质 / 名称）</h2>
 * 玩家会捡到原版箭、也可能拿到别的插件给的同名箭；材质（{@code ARROW} / {@code TIPPED_ARROW}）
 * 和显示名都**不是唯一身份**。因此每根补给品都带一个持久化键
 * {@code lethe_quiver_kind}（值 {@code blood_tear} / {@code arrow}）：
 * <ul>
 *   <li>计数只数自己的补给品（原版箭不计入上限，也不会阻止补给）；</li>
 *   <li>{@link #stop()} 只回收自己的补给品（绝不误删玩家自己捡的普通箭）；</li>
 *   <li>物品可以自由拖动 / 丢弃 / 捡起，身份跟着物品走，不依赖"它原来在第几格"。</li>
 * </ul>
 *
 * <h2>为什么物品不锁槽、不取消背包点击</h2>
 * 箭袋是**弹药**，不是装备：玩家需要把它拖到顺手的位置、分给队友、按需丢弃。
 * 本组件只负责"发"与"回收"，不接管玩家的背包操作（角色清除时按 PDC 精准回收，见上）。
 *
 * <h2>发放位置（为什么从第 4 格起找空格）</h2>
 * 0~3 号栏位归本角色的主武器与三个技能，渲染器会在每帧对这些格子**强刷**（框架既有口径）。
 * 补给品若落在 0~3 号格，会在下一次重绘时被系统物品覆盖而凭空消失。因此本组件只在
 * 4 号及之后的**主背包格**（0~35）里找空格；玩家自己把箭拖进 0~3 号格仍会被覆盖，
 * 那是渲染器的既有口径，本组件不为其增加第二套保护。
 *
 * <h2>为什么用 {@code update()} 累计节拍而不是排任务</h2>
 * 组件本来就被每 tick 调用一次，两个节拍只是两个整数计数；不排任务就没有需要提前取消的句柄，
 * 角色清除（{@link #stop()}）天然零残留。
 *
 * <p>本组件不占热键栏（描述符直接继承 {@code RoleComponent.Specification}，没有栏位口径），
 * 也不依赖任何兄弟组件（只操作自己的背包 + 表现工具箱）。
 */
public class LetheQuiverComponent extends RoleComponent {

    /** 本组件的登记 id（id 由组件自己声明）。 */
    public static final String ID = "lethe_quiver_component";

    // ───────── 物品身份（PDC 键 + 两种取值，全小写）─────────

    /** 补给品的身份键：值决定它是血泪还是箭矢。 */
    private static final NamespacedKey QUIVER_KIND_KEY = KeyFactory.Registry.of("lethe_quiver_kind");

    /** 血泪的键值。 */
    private static final String KIND_BLOOD_TEAR = "blood_tear";

    /** 箭矢的键值。 */
    private static final String KIND_ARROW = "arrow";

    // ───────── 上限与节拍（唯一修改点）─────────

    /** 血泪上限（根）。 */
    private static final int BLOOD_TEAR_CAP = 2;

    /** 箭矢上限（根）。 */
    private static final int ARROW_CAP = 14;

    /** 箭矢补给间隔（120 刻 = 6 秒）。 */
    private static final int ARROW_REFILL_INTERVAL_TICKS = 120;

    /** 血泪补给间隔（200 刻 = 10 秒；且只有箭矢满 14 根后才开始计时）。 */
    private static final int BLOOD_TEAR_REFILL_INTERVAL_TICKS = 200;

    // ───────── 发放位置与名称 ─────────

    /**
     * 系统保留的热键栏格数：本角色的主武器（0）与三个技能（1/2/3）共占前 4 格。
     * <p>补给品只从这一格之后找位置，理由见类注释「发放位置」。
     */
    private static final int RESERVED_HOTBAR_SLOTS = 4;

    /** 玩家主背包（含热键栏）的格数；盔甲与副手不在补给品的发放范围内。 */
    private static final int MAIN_INVENTORY_SIZE = 36;

    /** 「血泪」的显示名：金色加粗。 */
    private static final Component BLOOD_TEAR_NAME =
            Component.text("血泪", NamedTextColor.GOLD).decorate(TextDecoration.BOLD);

    /** 「箭矢」的显示名：白色加粗。 */
    private static final Component ARROW_NAME =
            Component.text("箭矢", NamedTextColor.WHITE).decorate(TextDecoration.BOLD);

    // ───────── 运行期状态 ─────────

    /** 箭矢补给节拍计数（每 tick +1，到间隔清零）。 */
    private int arrowRefillTimer;

    /** 血泪补给节拍计数（同上）。 */
    private int bloodTearRefillTimer;

    public LetheQuiverComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services);
    }

    /**
     * 本组件的描述符：不带栏位、无兄弟组件依赖（补给品只进玩家自己的背包）。
     */
    public static final class Specification extends RoleComponent.Specification<LetheQuiverComponent> {

        public Specification() {
            super("Quiver");
        }

        @Override
        public LetheQuiverComponent create(String id, ComponentServicesPort services) {
            return new LetheQuiverComponent(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    /**
     * 开始生效：立刻发放一次满额补给（2 根血泪 + 14 根箭矢），并把两个节拍归零。
     * <p>立刻发放而不等第一拍：否则选完角色会有最长 4 秒的"无箭可射"窗口。
     */
    @Override
    public void start() {
        arrowRefillTimer = 0;
        bloodTearRefillTimer = 0;
        Player player = selfPlayer();
        if (player == null) {
            return;
        }
        fillUpTo(player, KIND_BLOOD_TEAR, BLOOD_TEAR_CAP, true);
        fillUpTo(player, KIND_ARROW, ARROW_CAP, false);
    }

    /**
     * 停止生效：把本组件发放的补给品从背包里全部移除（角色清除时不残留）。
     * <p>只认 PDC 身份 ⇒ 不会误删玩家自己捡到的普通箭 / 别的插件给的箭。
     * <p>不处理玩家已经丢在地上的补给品（那是玩家自己的操作结果，且已不在背包里）。
     */
    @Override
    public void stop() {
        arrowRefillTimer = 0;
        bloodTearRefillTimer = 0;
        Player player = selfPlayer();
        if (player == null) {
            return;
        }
        removeQuiverItems(player);
    }

    // ───────── 每 tick：两条补给节拍 ─────────

    @Override
    public void update() {
        Player player = selfPlayer();
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        if (++arrowRefillTimer >= ARROW_REFILL_INTERVAL_TICKS) {
            arrowRefillTimer = 0;
            tryRefill(player, KIND_ARROW, ARROW_CAP, false);
        }
        //血泪的恢复前置：**箭矢补满 14 根之后**才开始计时（2026-10-30 需求）；
        //  没满就把计时清零 ⇒ 第一次血泪恢复发生在"箭矢满"后的第 10 秒，而不是"进场后第 10 秒"。
        if (countKind(player.getInventory(), KIND_ARROW) < ARROW_CAP) {
            bloodTearRefillTimer = 0;
        } else if (++bloodTearRefillTimer >= BLOOD_TEAR_REFILL_INTERVAL_TICKS) {
            bloodTearRefillTimer = 0;
            tryRefill(player, KIND_BLOOD_TEAR, BLOOD_TEAR_CAP, true);
        }
    }

    /**
     * 一拍补给：数量低于上限且**真的放进背包了**才播表现。
     * <p>背包满时 {@link #giveOne(Player, ItemStack, String)} 回 {@code false} ⇒ 这一拍作废
     * （不欠账，下一拍再试；理由见类注释）。
     */
    private void tryRefill(Player player, String kind, int cap, boolean bloodTear) {
        if (countKind(player.getInventory(), kind) >= cap) {
            return;
        }
        ItemStack item = bloodTear ? newBloodTear() : newArrow();
        if (!giveOne(player, item, kind)) {
            return;
        }
        World world = player.getWorld();
        if (world == null) {
            return;
        }
        LetheVfx.quiverRestore(world, player.getLocation(), bloodTear);
        LetheSound.quiverRestore(world, player.getLocation(), bloodTear);
    }

    /** 进场发放：从当前数量一路补到上限（中途背包满则停手，剩余部分交给后续节拍）。 */
    private void fillUpTo(Player player, String kind, int cap, boolean bloodTear) {
        PlayerInventory inventory = player.getInventory();
        int current = countKind(inventory, kind);
        while (current < cap) {
            ItemStack item = bloodTear ? newBloodTear() : newArrow();
            if (!giveOne(player, item, kind)) {
                return;
            }
            current++;
        }
    }

    // ───────── 背包操作 ─────────

    /**
     * 把一根补给品放进玩家背包：先并入已有的同类堆，再找主背包空格。
     *
     * <p>为什么先并堆：血泪与箭矢都是同名同 PDC 的物品，理应叠放；每根各占一格会
     * 白白吃掉 16 个格子（本角色的背包还要放玩家自己的东西）。
     * <p>为什么空格从 {@link #RESERVED_HOTBAR_SLOTS} 起找：见类注释「发放位置」。
     *
     * @return 是否真的放进了背包（{@code false} = 主背包已满，调用方按"这一拍作废"处理）
     */
    private static boolean giveOne(Player player, ItemStack item, String kind) {
        PlayerInventory inventory = player.getInventory();
        //① 并入已有的同类堆（血泪/箭矢的 max stack 是 64，上限远小于它，不会溢出）
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing == null || !kind.equals(quiverKindOf(existing))) {
                continue;
            }
            if (existing.getAmount() >= existing.getMaxStackSize()) {
                continue;
            }
            existing.setAmount(existing.getAmount() + 1);
            //写回同一格：某些平台实现里 getItem 给的是镜像，显式 setItem 才保证数量落回背包
            inventory.setItem(slot, existing);
            return true;
        }
        //② 找主背包空格（跳过 0~3 号系统保留格，理由见类注释）
        for (int slot = RESERVED_HOTBAR_SLOTS; slot < MAIN_INVENTORY_SIZE; slot++) {
            ItemStack existing = inventory.getItem(slot);
            if (existing != null && !existing.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, item);
            return true;
        }
        return false;
    }

    /** 统计某种补给品的总根数（按 PDC 身份；多格 / 多堆累加）。 */
    private static int countKind(Inventory inventory, String kind) {
        int total = 0;
        for (ItemStack stack : inventory.getContents()) {
            if (kind.equals(quiverKindOf(stack))) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** 把背包里全部本组件补给品移除，返回移除的根数。 */
    private static int removeQuiverItems(Player player) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        int removed = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (quiverKindOf(stack) == null) {
                continue;
            }
            removed += stack.getAmount();
            inventory.setItem(slot, null);
        }
        return removed;
    }

    /** 读一件物品的补给品身份（不是本组件的补给品回 {@code null}）。 */
    private static String quiverKindOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(QUIVER_KIND_KEY, PersistentDataType.STRING);
    }

    // ───────── 物品构造 ─────────

    /**
     * 造一根「血泪」：{@code TIPPED_ARROW} + 瞬时伤害 II 的自定义药水效果 + 金色加粗名 + PDC 身份。
     * <p>为什么用"自定义效果"而不是基础药水类型：基础药水类型只到 I 级（{@code HARMING}），
     * II 级（{@code STRONG_HARMING}）是原版对"伤害药水"的命名；需求要的是 II 级瞬时伤害，
     * 自定义效果能精确表达放大器等级（{@code amplifier = 1} 即 II 级）。
     * <p>{@code instanceof PotionMeta} 是防御性收窄：箭矢物品在受支持的服务端版本上就是
     * {@code PotionMeta}；若某版本给出别的 meta 形态，名字与 PDC 身份仍然成立（只是没有药水效果），
     * 不抛异常。
     */
    private static ItemStack newBloodTear() {
        ItemStack stack = new ItemStack(Material.TIPPED_ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(BLOOD_TEAR_NAME);
        if (meta instanceof PotionMeta potion) {
            //瞬时效果只看等级；时长给 1 刻（原版对瞬时效果的时长没有语义）
            potion.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1, false, false), true);
        }
        meta.getPersistentDataContainer().set(QUIVER_KIND_KEY, PersistentDataType.STRING, KIND_BLOOD_TEAR);
        stack.setItemMeta(meta);
        return stack;
    }

    /** 造一根「箭矢」：{@code ARROW} + 白色加粗名 + PDC 身份。 */
    private static ItemStack newArrow() {
        ItemStack stack = new ItemStack(Material.ARROW);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.displayName(ARROW_NAME);
        meta.getPersistentDataContainer().set(QUIVER_KIND_KEY, PersistentDataType.STRING, KIND_ARROW);
        stack.setItemMeta(meta);
        return stack;
    }

    // ───────── 内部工具 ─────────

    /** 本实例的玩家（离线 / 服务集未绑定时回 {@code null}）。 */
    private Player selfPlayer() {
        return svc().self() == null ? null : svc().self().player();
    }
}
