package com.shadowHunterRolesPlugin.roleComponent.custom.Mikomako;

/**
 * 「须狂命」的阈值口径 —— 纯函数表，武器 / 技能 / 被动共用同一份判据。
 *
 * <p>SanTE 越低增益越多，每个档位是「≤ 阈值」的累计区间（不是区间段）：
 * 例如 SanTE = 55 时 90 / 80 / 70 / 60 四档同时生效。
 *
 * <p>本类**不读组件、不碰状态**，只做"给定一个 san 值，返回该档是否生效"的判定，
 * 因此三个调用点（{@code MikomakoMainWeapon} / 三个技能 / {@code MikomakoMadnessPassive}）
 * 不可能各写一套而在边界上跑偏。
 */
public final class MikomakoMadness {

    /** SanTE 上限（与 {@code SanTEComponent.SANTE_MAX} 同值，仅用于阈值表达）。 */
    public static final int SANTE_MAX = 100;

    /** 100 档：技能 CD 每秒额外恢复 0.5s —— 只在 san ≥ 90 时还有效（掉到 90 以下即消失）。 */
    public static final int TIER_CD_BOOST = 90;

    /** 90 档：速度 I。 */
    public static final int TIER_SPEED = 90;

    /** 80 档：普攻 +2 物理 / +1 特殊值。 */
    public static final int TIER_ATTACK = 80;

    /** 70 档：每秒 +1 能量。 */
    public static final int TIER_ENERGY = 70;

    /** 60 档：抗性 I。 */
    public static final int TIER_RESIST = 60;

    /** 50 档：每秒 -1 生命；三处治疗额外 +2。 */
    public static final int TIER_BLOOD = 50;

    /** 40 档：圆弧斩范围 +1 格；普攻 15% 把敌人拉回 1 格。 */
    public static final int TIER_REACH = 40;

    /** 30 档：普攻 20% 概率本次全部伤害 ×1.5。 */
    public static final int TIER_CRIT = 30;

    /** 20 档：漫不经心无条件充能 2 层。 */
    public static final int TIER_DOUBLE_CHARGE = 20;

    /** 10 档：技能附带缓慢 I + 4 点伤害吸收。 */
    public static final int TIER_CONTROL = 10;

    /** 0 档：巨型爆发。 */
    public static final int TIER_BURST = 0;

    private MikomakoMadness() {
    }

    /** 100 档：技能 CD 加速（san ≥ 90，且尚未进入 0 状态）。 */
    public static boolean cdBoost(int san) {
        return san >= TIER_CD_BOOST;
    }

    /** 90 档：速度 I。 */
    public static boolean speed(int san) {
        return san <= TIER_SPEED;
    }

    /** 80 档：普攻加伤。 */
    public static boolean attackBonus(int san) {
        return san <= TIER_ATTACK;
    }

    /** 70 档：每秒 +1 能量。 */
    public static boolean energyRegen(int san) {
        return san <= TIER_ENERGY;
    }

    /** 60 档：抗性 I。 */
    public static boolean resistance(int san) {
        return san <= TIER_RESIST;
    }

    /** 50 档：每秒 -1 生命。 */
    public static boolean bloodDrain(int san) {
        return san <= TIER_BLOOD;
    }

    /** 50 档：充能攻击 / 圆弧斩 / 血腥连斩 的每处治疗 +2。 */
    public static int healBonus(int san) {
        return san <= TIER_BLOOD ? 2 : 0;
    }

    /** 40 档：圆弧斩范围 +1 格。 */
    public static boolean circleReachBonus(int san) {
        return san <= TIER_REACH;
    }

    /** 40 档：普攻 15% 把敌人拉回 1 格。 */
    public static boolean pullChance(int san) {
        return san <= TIER_REACH;
    }

    /** 30 档：普攻 20% 概率本次全部伤害 ×1.5。 */
    public static boolean critChance(int san) {
        return san <= TIER_CRIT;
    }

    /** 20 档：漫不经心充能 2 层。 */
    public static boolean doubleCharge(int san) {
        return san <= TIER_DOUBLE_CHARGE;
    }

    /** 10 档：技能附带缓慢 I + 4 点伤害吸收。 */
    public static boolean controlEcho(int san) {
        return san <= TIER_CONTROL;
    }
}
