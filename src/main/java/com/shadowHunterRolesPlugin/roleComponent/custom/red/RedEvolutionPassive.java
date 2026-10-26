package com.shadowHunterRolesPlugin.roleComponent.custom.red;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.ports.SelfPort;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.builtin.EvolutionPassive;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 「红」的四档进化（2026-10-21 重做版）。
 *
 * <table>
 *   <tr><th>档</th><th>名称</th><th>效果</th></tr>
 *   <tr><td>1</td><td>深沉</td><td>流血层数上限 15 → <b>20</b></td></tr>
 *   <tr><td>2</td><td>彷徨</td><td>「何足挂齿」+4 SanTE / +2 生命；每次触发额外结算所有流血敌人 4 层</td></tr>
 *   <tr><td>3</td><td>殷红</td><td>最大生命 +10</td></tr>
 *   <tr><td>4</td><td>浸血</td><td>每秒额外结算 1 层流血；「饮血」额外触发一次</td></tr>
 * </table>
 *
 * <p>本类只持有"档位"与"3 档的血上限修饰符"，其余效果由流血被动按 {@code getCurrentEvolutionLevel()}
 * 读取（读口都在文件底部）。升级入口 = 击杀（基类）。
 */
public class RedEvolutionPassive extends EvolutionPassive {

    /** 本组件的登记 id。 */
    public static final String ID = "red_evolution_passive";

    /** 3 档的生命上限修饰符（★ 静态键：离线测试需装 KeyFactory 桩）。 */
    public static final NamespacedKey HEALTH_BONUS_KEY = KeyFactory.Registry.of("red_health_bonus");

    /** 档位总数。 */
    public static final int MAX_EVOLUTION_LEVEL = 4;

    /** 3 档（殷红）。 */
    private static final int TIER_CRIMSON = 3;

    /** 3 档额外生命上限。 */
    public static final double CRIMSON_MAX_HEALTH_BONUS = 10d;

    /** 1 档后的流血上限。 */
    public static final int DEEP_BLEED_CAP = 20;

    private VitalsComponent vitals;
    private EvolutionLevelUpListener levelUpEntry;
    private boolean healthApplied;

    public RedEvolutionPassive(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
    }

    /** 描述符。 */
    public static final class Specification extends EvolutionPassive.Specification<RedEvolutionPassive> {

        public Specification() {
            super(Component.text("红 · 进化"),
                    List.of(
                            Component.text("1-『深沉』流血层数上限增加到 20"),
                            Component.text("2-『彷徨』何足挂齿 +4 特殊值 / +2 生命，并结算所有流血敌人 4 层"),
                            Component.text("3-『殷红』获得 10 永久最大生命值"),
                            Component.text("4-『浸血』每秒额外结算 1 层流血、饮血额外触发一次")
                    ));
        }

        @Override
        public RedEvolutionPassive create(String id, ComponentServicesPort services) {
            return new RedEvolutionPassive(id, services, this);
        }
    }

    @Override
    protected void onStart() {
        vitals = svc().components().get(VitalsComponent.class);
        levelUpEntry = addEvolutionLevelUpListener(this, this::onLevelUp);
    }

    @Override
    public void stop() {
        super.stop();
        if (vitals != null && selfPlayer() != null) {
            vitals.removeHealthModifier(HEALTH_BONUS_KEY);
        }
        vitals = null;
        levelUpEntry = null;
        healthApplied = false;
    }

    @Override
    protected void onPlayerKilled(VitalsComponent.PlayerKilledEvent event) {
        if (getCurrentEvolutionLevel() >= MAX_EVOLUTION_LEVEL) {
            return;
        }
        super.onPlayerKilled(event);
    }

    private void onLevelUp(LevelUp levelUp) {
        announce(levelUp.current());
        if (!healthApplied && levelUp.current() >= TIER_CRIMSON) {
            healthApplied = true;
            applyCrimson();
        }
    }

    private void applyCrimson() {
        Player player = selfPlayer();
        if (player == null || vitals == null) {
            return;
        }
        vitals.applyHealthModifier(HEALTH_BONUS_KEY, VitalsComponent.BASE_MAX_HEALTH + CRIMSON_MAX_HEALTH_BONUS);
        vitals.restoreFull(player);
    }

    private void announce(int level) {
        Player player = selfPlayer();
        Component message = evolutionMessage(level);
        if (player != null && message != null) {
            player.sendMessage(message);
        }
    }

    private Player selfPlayer() {
        SelfPort self = svc().self();
        return self == null ? null : self.player();
    }

    /** 档位文案（升级消息用）。 */
    public static Component evolutionMessage(int level) {
        String text = switch (level) {
            case 1 -> "『深沉』——流血的上限，也变深了。";
            case 2 -> "『彷徨』——我数着你的脚步，一遍又一遍。";
            case 3 -> "『殷红』——我的血，为你多留了一分。";
            case 4 -> "『浸血』——浸透了，就再也洗不掉了。";
            default -> null;
        };
        if (text == null) {
            return null;
        }
        return Component.text("红 · " + level + " ", NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(text, NamedTextColor.WHITE));
    }

    // ───────── 效果读口 ─────────

    /** 流血上限：1 档后 20，否则 15。 */
    public int bleedCap() {
        return getCurrentEvolutionLevel() >= 1 ? DEEP_BLEED_CAP : RedBleedPassive.BASE_MAX_BLEED;
    }

    /** 2 档：何足挂齿额外回复的特殊值。 */
    public int frugalSanTEBonus() {
        return getCurrentEvolutionLevel() >= 2 ? 4 : 0;
    }

    /** 2 档：何足挂齿额外回复的生命。 */
    public double frugalHealBonus() {
        return getCurrentEvolutionLevel() >= 2 ? 2d : 0d;
    }

    /** 2 档：何足挂齿触发时对所有流血敌人结算的层数。 */
    public int frugalSettleStacks() {
        return getCurrentEvolutionLevel() >= 2 ? 4 : 0;
    }

    /** 4 档：每秒额外结算的层数。 */
    public int extraSettlePerSecond() {
        return getCurrentEvolutionLevel() >= 4 ? 1 : 0;
    }

    /** 4 档：饮血额外触发次数（总次数 = 1 + 本值）。 */
    public int extraDrinkTimes() {
        return getCurrentEvolutionLevel() >= 4 ? 1 : 0;
    }
}
