package com.shadowHunterRolesPlugin.roleComponent.custom.voice;

import com.shadowHunterRolesPlugin.core.ports.ComponentServicesPort;
import com.shadowHunterRolesPlugin.core.util.TextUtil;
import com.shadowHunterRolesPlugin.roleComponent.builtin.FloatingTextComponent;
import com.shadowHunterRolesPlugin.roleComponent.builtin.VitalsComponent;
import net.kyori.adventure.text.Component;

import java.util.List;
import java.util.Map;

/**
 * ★ **通用「剧本化语音」组件**（2026-10-31 全员语音需求）：
 * 用一份数据表（时刻 → 台词）撑起一个角色的全部悬浮语音，**不为每个新角色再抄一遍子类**。
 *
 * <h2>为什么需要它</h2>
 * 工程里每个已有角色都自带一个手写的 {@code XxxFloatingTextComponent}（红 / 冷识 / 抗争 /
 * 梅妻鹤子 / 苍鹭 / 马提娜 / 艾尔伯特 / 渡鸦 / 沙耶香），但仍有四个角色（林 / 特克 / 猎手 / 罪棘）
 * 没有语音。再为每个角色手抄一份子类，只会把「词表」与「渲染策略」越写越长；
 * 本组件把**渲染策略固定下来**，只把**词表**交给现场注入 ⇒ 新增角色只写内容，不写代码。
 *
 * <h2>它能说哪些时刻（全部走基类既有挂载点，零框架改动）</h2>
 * <ul>
 *   <li>{@link #MOMENT_SPAWN} 登场：{@code start()} 时必说一句；</li>
 *   <li>{@link #MOMENT_IDLE} 空闲：安静一段时间后按概率自语；</li>
 *   <li>{@link #MOMENT_HURT} 受击 / {@link #MOMENT_LOW_HP} 濒死：订阅「自己受到伤害」；</li>
 *   <li>{@link #MOMENT_KILL} 击杀：订阅「自己击杀了敌人」；</li>
 *   <li>{@link #MOMENT_DEATH} 死亡：受击钩子里血量归零时补一句遗言；</li>
 *   <li>{@link #MOMENT_CAST} 施法：**留给技能侧显式调用** {@code say(player, MOMENT_CAST, null)}
 *       —— 本组件不猜时机，嘴长在调用方手上。</li>
 * </ul>
 *
 * <h2>两套语气（招表时用 {@code shadowTone} 选）</h2>
 * <ul>
 *   <li><b>影都（SHADOW）</b>：巨大怪物，少有人性，却要有人性 ⇒ 台词短、低、带饥饿感；
 *       受击/濒死用颤抖的暗红，击杀用炸裂红；空闲是"怪物学着做人"的独白。</li>
 *   <li><b>猎影人（HUNTER）</b>：生活在复杂背景下的反英雄 ⇒ 台词冷静、带代价与自嘲；
 *       用泛光冷白，击杀不欢呼，只在记账。</li>
 * </ul>
 *
 * <h2>性能边界（继承基类，不放大）</h2>
 * 池的上限（同时 5 段 / 单段 24 字 / 全局 7 刻间隔）由基类统一把守；本组件只调
 * 空闲间隔与开口概率（默认 6 秒 ± 1.5 秒、30% 概率），避免话痨。
 */
public class ScriptedVoiceComponent extends FloatingTextComponent {

    /** 本组件的登记 id（每个挂它的角色都用同一个 id —— id 只在角色实例内唯一）。 */
    public static final String ID = "role_voice_script";

    /**
     * **自定义时刻：死亡遗言**。
     * <p>基类只定义了 idle / cast / hurt / kill / lowhp / spawn 六个时刻，"死亡"由各子类自己起名
     * （例：红的 {@code red_death}）。本组件统一用 {@code "death"}，由 {@link #onDamaged} 在血量归零时补一句。
     */
    public static final String MOMENT_DEATH = "death";

    /** 本角色的台词表（由描述符注入；运行期不可变）。 */
    private final Script script;

    public ScriptedVoiceComponent(String id, ComponentServicesPort services, Specification specification) {
        super(id, services, specification);
        this.script = specification.script();
    }

    @Override
    protected TextUtil.Lexicon lexicon() {
        return TextUtil.Lexicon.of(script.lines());
    }

    @Override
    protected TextUtil.Style styleFor(String moment) {
        return script.styleFor(moment);
    }

    @Override
    protected TextUtil.Look lookFor(String moment) {
        return script.lookFor(moment);
    }

    @Override
    protected float scaleFor(String moment) {
        return MOMENT_KILL.equals(moment) ? 1.25f : 1.0f;
    }

    @Override
    protected int idleIntervalTicks() {
        return script.idleIntervalTicks();
    }

    @Override
    protected int idleJitterTicks() {
        return script.idleJitterTicks();
    }

    @Override
    protected double idleChance() {
        return script.idleChance();
    }

    @Override
    protected double hurtChance() {
        return script.hurtChance();
    }

    /** 遗言钩子：受击后血量归零 ⇒ 补一句死亡台词（与红同规）。 */
    @Override
    public void onDamaged(org.bukkit.entity.Player source, double amount) {
        super.onDamaged(source, amount);
        org.bukkit.entity.Player self = svc().self() == null ? null : svc().self().player();
        if (self != null && (self.isDead() || self.getHealth() <= 0d)) {
            say(self, MOMENT_DEATH, null);
        }
    }

    /**
     * **台词表**（纯数据）：时刻 → 候选台词 + 两档观感 + 开口节奏。
     * <p>{@code shadowTone} 决定默认风格走"影都"还是"猎影人"那一套。
     */
    public static final class Script {

        private final Map<String, List<String>> lines;
        private final TextUtil.Look calmLook;
        private final TextUtil.Look intenseLook;
        private final boolean shadowTone;
        private final int idleIntervalTicks;
        private final int idleJitterTicks;
        private final double idleChance;
        private final double hurtChance;

        public Script(Map<String, List<String>> lines, TextUtil.Look calmLook, TextUtil.Look intenseLook,
                      boolean shadowTone, int idleIntervalTicks, int idleJitterTicks,
                      double idleChance, double hurtChance) {
            this.lines = lines;
            this.calmLook = calmLook;
            this.intenseLook = intenseLook;
            this.shadowTone = shadowTone;
            this.idleIntervalTicks = idleIntervalTicks;
            this.idleJitterTicks = idleJitterTicks;
            this.idleChance = idleChance;
            this.hurtChance = hurtChance;
        }

        /** 时刻 → 候选（只读）。 */
        public Map<String, List<String>> lines() {
            return lines;
        }

        /** 是否影都语气（SHADOW）。 */
        public boolean shadowTone() {
            return shadowTone;
        }

        public int idleIntervalTicks() {
            return idleIntervalTicks;
        }

        public int idleJitterTicks() {
            return idleJitterTicks;
        }

        public double idleChance() {
            return idleChance;
        }

        public double hurtChance() {
            return hurtChance;
        }

        /** 时刻 → 动画风格：登场低语、击杀炸裂、受击/濒死乱码、空闲沉淀。 */
        TextUtil.Style styleFor(String moment) {
            String key = moment == null ? MOMENT_IDLE : moment;
            return switch (key) {
                case MOMENT_SPAWN -> TextUtil.STYLE_WHISPER;
                case MOMENT_KILL -> TextUtil.STYLE_BURST;
                case MOMENT_HURT, MOMENT_LOW_HP -> TextUtil.STYLE_GLITCH;
                case MOMENT_CAST -> TextUtil.STYLE_EMPHASIS;
                default -> TextUtil.STYLE_CALM;
            };
        }

        /** 时刻 → 观感：重场合（施法/击杀/受击/濒死）用烈色，其余用本底冷色。 */
        TextUtil.Look lookFor(String moment) {
            String key = moment == null ? MOMENT_IDLE : moment;
            return switch (key) {
                case MOMENT_CAST, MOMENT_KILL, MOMENT_HURT, MOMENT_LOW_HP -> intenseLook;
                default -> calmLook;
            };
        }
    }

    /** 描述符：带一份台词表（内容由 {@link VoiceScripts} 提供）。 */
    public static final class Specification extends FloatingTextComponent.Specification<ScriptedVoiceComponent> {

        private final Script script;

        public Specification(Component displayName, List<Component> description, Script script) {
            super(displayName, description);
            this.script = script;
            requires(VitalsComponent.class);
        }

        public Script script() {
            return script;
        }

        @Override
        protected FloatingTextComponent createComponent(String id, ComponentServicesPort services) {
            return new ScriptedVoiceComponent(id, services, this);
        }
    }
}
