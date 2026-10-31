package com.shadowHunterRolesPlugin.roleComponent.builtin;

import com.shadowHunterRolesPlugin.core.Faction;
import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.roleComponent.base.PassiveSkill;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * ★ **名下血量 + SAN/TE 显示**（2026-10-31 需求）：
 * 在**其他玩家视角的名字下方**显示两段数值 —— 当前血量，以及本阵营的特殊值显示
 * （猎影人 = {@code SAN} 紫色、影都 = {@code TE} 亮黄色）。
 *
 * <h2>表现形态（为什么用 TextDisplay 而不是记分板）</h2>
 * 原版记分板 {@code BELOW_NAME} 槽位**全局只能挂一个目标且只能显示整数**，没法同时给出
 * "血量 + SAN/TE" 两段文字、更没法按阵营染色；因此本组件在每个角色玩家头顶生成一个
 * **非持久 TextDisplay**（无碰撞、不挡视线、随玩家移动），文字单行：
 * <pre>
 *   ❤ 24/40  SAN 72/100        ← 猎影人（SAN 紫）
 *   ❤ 24/40  TE 72/100         ← 影都（TE 亮黄）
 * </pre>
 * 文字位于"玩家身高 + {@value #NAME_GAP} 格"处 ⇒ 正好落在原版名字下方、头顶上方。
 *
 * <h2>它自己管生命周期</h2>
 * <ul>
 *   <li>角色实例 {@code start()} 后随 {@code update()} 生成；<b>死亡 / 观战 / 隐身 / 掉线 / 切维度</b>
 *       时自动撤回，恢复后自动重建；</li>
 *   <li>组件 {@code stop()}（清角色 / 插件禁用）时删除实体 —— 非持久实体也不会在重启后残留；</li>
 *   <li>每刻跟随玩家位置；组织文本只在数值变化时重发（省数据包）。</li>
 * </ul>
 *
 * <p><b>边界申报</b>：位于 {@code roleComponent/builtin/}（平时禁改区），本次是用户明确要求的显示需求；
 * 只新增一个内建显示组件 + 注册一行，不改任何组件契约。无角色玩家不显示（本组件只随角色实例存在）。
 */
public class NameplateVitalsDisplayComponent extends PassiveSkill {

    /** 本组件的登记 id。 */
    public static final String ID = "nameplateVitalsDisplay";

    /** 文字中心在头顶之上多少格（格）：最终高度 = 玩家当前身高 + 本值（0.5 格，避开原版名字）。 */
    public static final double NAME_GAP = 0.5d;

    /** 展示实体可见范围倍率（1.0 = 默认 64 格）。 */
    public static final float VIEW_RANGE = 1.0f;

    /**
     * 展示实体的**记分板标签**（运维 / 自动化测试的精确定位键）：
     * 世界里同时会有很多 {@code text_display}（角色语音的悬浮字也是它），
     * 用 {@code @e[type=minecraft:text_display,tag=shrp_nameplate]} 才能稳定选中本组件的实体。
     */
    public static final String SCOREBOARD_TAG = "shrp_nameplate";

    /** 血量文字颜色（红）。 */
    public static final TextColor HEALTH_COLOR = TextColor.color(0xFF5555);

    /** 猎影人 SAN 颜色：**紫色**（2026-10-31 用户口径）。 */
    public static final TextColor HUNTER_ATTRIBUTE_COLOR = TextColor.color(0xBB55FF);

    /** 影都 TE 颜色：**亮黄色**（2026-10-31 用户口径）。 */
    public static final TextColor SHADOW_ATTRIBUTE_COLOR = NamedTextColor.YELLOW;

    private SanTEComponent sante;
    private Faction faction;

    /** 当前展示实体（无/已撤回 ⇒ {@code null}）。 */
    private TextDisplay display;

    /** 文本缓存（只在数值变化时重发文本包）。 */
    private int lastHealth = Integer.MIN_VALUE;
    private int lastMaxHealth = Integer.MIN_VALUE;
    private int lastSante = Integer.MIN_VALUE;
    private int lastSanteMax = Integer.MIN_VALUE;

    public NameplateVitalsDisplayComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 被动描述符（不占热键栏）。 */
    public static final class Specification extends PassiveSkill.Specification<NameplateVitalsDisplayComponent> {

        public Specification() {
            super(Component.text("名下属性显示"),
                    List.of(
                            Component.text("其他玩家视角：名字下方显示 ❤ 血量 与 SAN/TE 特殊值"),
                            Component.text("猎影人 SAN = 紫色；影都 TE = 亮黄色"),
                            Component.text("死亡 / 观战 / 隐身 / 掉线时自动隐藏")
                    ));
            requires(FactionComponent.class);
            requires(SanTEComponent.class);
        }

        @Override
        public NameplateVitalsDisplayComponent create(String id, ComponentServicesPort services) {
            return new NameplateVitalsDisplayComponent(id, services, this);
        }
    }

    // ───────── 生命周期 ─────────

    @Override
    public void start() {
        sante = getComponent(SanTEComponent.class);
        FactionComponent factionComponent = getComponent(FactionComponent.class);
        faction = factionComponent == null ? Faction.UNKNOWN : factionComponent.faction();
        display = null;
        resetTextCache();
    }

    @Override
    public void stop() {
        despawn();
        sante = null;
        faction = null;
        resetTextCache();
    }

    @Override
    public void update() {
        Player player = selfPlayer();
        if (player == null || !player.isOnline()) {
            despawn();
            return;
        }
        if (!shouldShow(player)) {
            despawn();
            return;
        }

        //换维度 / 实体失效 ⇒ 重建（跨世界传送不如就地重建稳）
        if (display != null && (!display.isValid() || !display.getWorld().equals(player.getWorld()))) {
            despawn();
        }
        if (display == null) {
            spawn(player);
            if (display == null) {
                return;
            }
        }

        //跟随：每刻贴到"身高 + NAME_GAP"处（TextDisplay 的 Billboard.CENTER 会始终面向观察者）
        display.teleport(displayLocation(player));

        //文本：数值变化才重发
        int health = displayHealth(player);
        int maxHealth = displayMaxHealth(player);
        int santeCurrent = sante == null ? 0 : sante.current();
        int santeMax = sante == null ? 0 : sante.max();
        if (health != lastHealth || maxHealth != lastMaxHealth
                || santeCurrent != lastSante || santeMax != lastSanteMax) {
            display.text(nameplateText(faction, player.getHealth(), maxHealth,
                    santeCurrent, santeMax));
            lastHealth = health;
            lastMaxHealth = maxHealth;
            lastSante = santeCurrent;
            lastSanteMax = santeMax;
        }
    }

    // ───────── 纯函数（离线可穷举单测）─────────

    /** 属性名：猎影人 = {@code SAN}，影都（及未知）= {@code TE}。 */
    public static String attributeLabel(Faction faction) {
        return faction == Faction.HUNTER ? "SAN" : "TE";
    }

    /** 属性颜色：猎影人 = 紫，影都 = 亮黄。 */
    public static TextColor attributeColor(Faction faction) {
        return faction == Faction.HUNTER ? HUNTER_ATTRIBUTE_COLOR : SHADOW_ATTRIBUTE_COLOR;
    }

    /**
     * 名下文字（纯函数）：{@code ❤ 血量/上限  SAN cur/max} 或 {@code ❤ 血量/上限  TE cur/max}。
     *
     * @param faction      阵营（决定 SAN/TE 与颜色；{@code null} 按 TE/亮黄处理）
     * @param health       当前血量（显示时向上取整，且夹在 0..上限）
     * @param maxHealth    血量上限（非法 ≤0 ⇒ 按 20 显示）
     * @param santeCurrent 特殊值当前值
     * @param santeMax     特殊值上限（≤0 ⇒ 不显示该段）
     */
    public static Component nameplateText(Faction faction, double health, int maxHealth,
                                          int santeCurrent, int santeMax) {
        int hpMax = maxHealth <= 0 ? (int) VitalsComponent.BASE_MAX_HEALTH : maxHealth;
        int hp = clamp((int) Math.ceil(health), 0, hpMax);
        Component out = Component.text("❤ ", HEALTH_COLOR)
                .append(Component.text(hp + "/" + hpMax, HEALTH_COLOR));
        if (santeMax > 0) {
            TextColor color = attributeColor(faction);
            out = out.append(Component.text("  "))
                    .append(Component.text(attributeLabel(faction) + " ", color)
                            .decoration(TextDecoration.BOLD, true))
                    .append(Component.text(santeCurrent + "/" + santeMax, color)
                            .decoration(TextDecoration.BOLD, true));
        }
        return out;
    }

    /** 展示高度：玩家当前身高 + {@value #NAME_GAP}（下蹲时身高变矮，文字跟着下移）。 */
    public static double displayY(double entityHeight) {
        return entityHeight + NAME_GAP;
    }

    /** 血量取整（向上，避免 0.5 血显示成 0）。 */
    static int displayHealth(Player player) {
        return (int) Math.ceil(player.getHealth());
    }

    /** 血量上限（属性缺失 ⇒ 20）。 */
    static int displayMaxHealth(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? VitalsComponent.BASE_MAX_HEALTH : attribute.getValue();
        return (int) Math.round(max);
    }

    // ───────── 实体管理 ─────────

    private void spawn(Player player) {
        try {
            TextDisplay spawned = player.getWorld().spawn(displayLocation(player), TextDisplay.class, entity -> {
                entity.addScoreboardTag(SCOREBOARD_TAG);
                entity.setBillboard(Display.Billboard.CENTER);
                entity.setAlignment(TextDisplay.TextAlignment.CENTER);
                entity.setDefaultBackground(false);
                entity.setSeeThrough(false);
                entity.setShadowed(true);
                entity.setViewRange(VIEW_RANGE);
                entity.setInterpolationDuration(0);
                entity.setTeleportDuration(0);
                entity.setTextOpacity((byte) -1);
                entity.setPersistent(false);
                entity.text(nameplateText(faction, player.getHealth(), displayMaxHealth(player),
                        sante == null ? 0 : sante.current(), sante == null ? 0 : sante.max()));
            });
            display = spawned;
            resetTextCache();
        } catch (Throwable ignored) {
            display = null;
        }
    }

    private void despawn() {
        if (display != null) {
            try {
                display.remove();
            } catch (Throwable ignored) {
                //实体已不在（区块卸载 / 服务器关停途中）⇒ 无需处理
            }
            display = null;
        }
    }

    private Location displayLocation(Player player) {
        return player.getLocation().add(0d, displayY(player.getHeight()), 0d);
    }

    /** 何时展示：活着、非观战、非隐身。 */
    private static boolean shouldShow(Player player) {
        if (player.isDead() || player.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        return !player.isInvisible() && !player.hasPotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void resetTextCache() {
        lastHealth = Integer.MIN_VALUE;
        lastMaxHealth = Integer.MIN_VALUE;
        lastSante = Integer.MIN_VALUE;
        lastSanteMax = Integer.MIN_VALUE;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private Player selfPlayer() {
        try {
            return svc().self() == null ? null : svc().self().player();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
