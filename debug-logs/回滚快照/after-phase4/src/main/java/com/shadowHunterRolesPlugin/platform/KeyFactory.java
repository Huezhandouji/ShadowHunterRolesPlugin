package com.shadowHunterRolesPlugin.platform;

import org.bukkit.NamespacedKey;

/** NamespacedKey 的唯一来源：领域层不再自己 new NamespacedKey(plugin, ...)。 */
public interface KeyFactory {

    NamespacedKey of(String key);

    /**
     * 静态常量的唯一入口。
     * <p>
     * {@code Skill.Utils.SKILL_KEY} / {@code MainWeapon.Utils.MAIN_WEAPON_KEY} /
     * {@code DamageUtil.LAST_DAMAGER_KEY} / {@code BuffManager.BUFF_MOVEMENT_SPEED_MODIFIER_KEY}
     * 都是 {@code public static final}，必须在**类初始化时**取值（组件在渲染代码里直接引用 SKILL_KEY，
     * 阶段 4.4 统一渲染器之前不得删除）；而 {@link KeyFactory} 的实现在 onEnable 才可用，
     * 故保留这一层**极窄**的静态桥：只暴露 {@code of(String)}，install 由主类在 onEnable 第一段完成。
     * 任何在这些常量之前触达 KeyFactory 的调用都会得到明确的 IllegalStateException，而不是 NPE。
     */
    final class Registry {

        private static KeyFactory installed;

        private Registry() {
        }

        public static void install(KeyFactory factory) {
            installed = factory;
        }

        public static NamespacedKey of(String key) {
            KeyFactory factory = installed;
            if (factory == null) {
                throw new IllegalStateException("KeyFactory is not installed yet (onEnable did not finish).");
            }
            return factory.of(key);
        }
    }
}
