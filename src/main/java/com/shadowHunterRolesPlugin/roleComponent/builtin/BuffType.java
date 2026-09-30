package com.shadowHunterRolesPlugin.roleComponent.builtin;

/**
 * buff 类型（本系统自己持有的"异常状态"，与原版药水效果是两套记账）。
 *
 * <p><b>每个常量自带"是不是负面效果"这一属性</b>（{@link #isDebuff()}）：清负面效果
 * （{@code manager/BuffManager#clearDebuffs()}）按它枚举，而不是在清理方维护第二张类型名单
 * ⇒ 新增常量时编译器强制表态（构造器没有默认值），不会因为漏改某张名单而静默漏清。
 */
public enum BuffType {

    /** 沉默：技能闸门关闭（不能施放技能）。 */
    SILENCE(true),

    /** 眩晕：技能闸门 + 主武器闸门都关闭，并附带原版失明 / 黑暗与移速修饰符。 */
    STUN(true),

    /**
     * 免疫：不是负面效果 —— 它反而是"负面效果免疫"的来源
     * （进场即净化，随后挡住新来的负面 buff 与原版负面药水）⇒ 清负面效果时不得把它清掉。
     */
    IMMUNE(false);

    private final boolean debuff;

    BuffType(boolean debuff) {
        this.debuff = debuff;
    }

    /** 是否属于负面效果（清负面效果时只清这些；免疫 / 增益一律不动）。 */
    public boolean isDebuff() {
        return debuff;
    }
}
