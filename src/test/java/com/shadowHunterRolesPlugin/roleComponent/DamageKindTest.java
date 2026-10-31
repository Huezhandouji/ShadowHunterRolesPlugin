package com.shadowHunterRolesPlugin.roleComponent;

import com.shadowHunterRolesPlugin.core.util.DamageUtil;
import com.shadowHunterRolesPlugin.platform.KeyFactory;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 锁住「伤害类型 ↔ 伤害原语」的对应关系（2026-10-31 新增爆炸 / 弹射物两类）。
 *
 * <h2>为什么要有这条</h2>
 * {@link DamageKind} 是统一入口 {@code VitalsComponent.damage(Player, double, DamageKind)}
 * 的类型参数：新增一个枚举值却忘了加原语，编译期不会报错（{@code switch} 里漏 case 才会），
 * 但运行期要么静默走错分支、要么抛 {@code NoSuchMethodException}。本测试按
 * **命名约定**把"枚举值 ↔ {@code DamageUtil.dealt<Kind>Damage} ↔
 * {@code VitalsComponent.<kind>Damage}"三处钉在一起：
 * <ul>
 *   <li>{@code PHYSICAL} → {@code dealtPhysicalDamage} / {@code physicalDamage}</li>
 *   <li>{@code TRUE} → {@code dealtTrueDamage} / {@code trueDamage}</li>
 *   <li>{@code EXPLOSION} → {@code dealtExplosionDamage} / {@code explosionDamage}</li>
 *   <li>{@code PROJECTILE} → {@code dealtProjectileDamage} / {@code projectileDamage}</li>
 * </ul>
 * 每个原语还必须有带击退强度的四参重载（与既有 PHYSICAL / TRUE 同形）。
 *
 * <h2>判据边界（如实申报）</h2>
 * 本测试是**API 冻结**（方法存在 / 参数类型 / 枚举集合），不跑真实伤害结算 ——
 * 那需要活服务器（原版伤害类型注册表、护甲减伤、死亡信息）。结算路径的实测归运行期验证。
 */
public class DamageKindTest {

    /** 离线基设：{@link DamageUtil} / {@link VitalsComponent} 的静态键依赖 {@code KeyFactory}。 */
    @BeforeClass
    public static void installKeyFactoryStub() {
        KeyFactory.Registry.install(key -> new NamespacedKey("shadowhunterroles", key));
    }

    /** 枚举取值集合与顺序（顺序即发布面：老值不挪位，新值只追加）。 */
    @Test
    public void damageKindsAreExactlyTheFourSupportedOnes() {
        List<String> names = new ArrayList<>();
        for (DamageKind kind : DamageKind.values()) {
            names.add(kind.name());
        }
        assertEquals("DamageKind 取值集合变了就必须同步改本测试（并确认全部原语已落地）",
                List.of("PHYSICAL", "TRUE", "EXPLOSION", "PROJECTILE"), names);
    }

    /** 每个枚举值都必须有：DamageUtil 的三参原语 + VitalsComponent 的三参/四参原语。 */
    @Test
    public void everyKindHasMatchingDamagePrimitives() throws Exception {
        for (DamageKind kind : DamageKind.values()) {
            String pascal = pascalCase(kind);
            String utilMethod = "dealt" + pascal + "Damage";
            String vitalsMethod = pascal.toLowerCase(Locale.ROOT) + "Damage";

            assertNotNull("DamageUtil 缺 " + utilMethod + "（DamageKind." + kind + " 无原语）",
                    DamageUtil.class.getMethod(utilMethod, LivingEntity.class, LivingEntity.class, double.class));
            assertNotNull("VitalsComponent 缺 " + vitalsMethod + "（三参）",
                    VitalsComponent.class.getMethod(vitalsMethod, LivingEntity.class, LivingEntity.class, double.class));
            assertNotNull("VitalsComponent 缺 " + vitalsMethod + "（四参：带击退强度）",
                    VitalsComponent.class.getMethod(vitalsMethod, LivingEntity.class, LivingEntity.class, double.class, double.class));
        }
    }

    /** 统一入口必须仍然接受"枚举 + 玩家 + 伤害量"这三件（新类型经它即可调用）。 */
    @Test
    public void unifiedDamageEntryStillAcceptsDamageKind() throws Exception {
        assertNotNull("统一入口 VitalsComponent.damage(Player, double, DamageKind) 不见了",
                VitalsComponent.class.getMethod("damage", Player.class, double.class, DamageKind.class));
    }

    /** {@code EXPLOSION} → {@code Explosion}、{@code PROJECTILE} → {@code Projectile}。 */
    private static String pascalCase(DamageKind kind) {
        String lower = kind.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
