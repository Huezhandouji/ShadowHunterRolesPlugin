package com.shadowHunterRolesPlugin.config;

/**
 * 一个配置键的**完整声明**：把「路径 / 默认值 / 类型 / 校验（取值范围） / 说明」
 * 集中在一个对象里，使配置文件里的每一个字段都**只有一处权威定义**。
 * <p>
 * <b>为什么要它</b>：在它之前，"默认值"同时散在 `config.yml` 的注释、读盘代码里的字面量、以及调用点的
 * 兜底分支三处；加一个字段要改三个地方，且"越界夹取 / 缺失回落"的口径容易各写一套。
 * <p>
 * <b>安全口径（本类型的硬约束）</b>：{@link #resolve(Object)} **永不抛异常** ——
 * 缺失 / 类型不符 / 不可解析一律回落 {@link #getDefaultValue()}，数值越界按
 * {@link #getMin()} / {@link #getMax()} **夹取**（与既有 `command-permission-level` 的语义逐字一致）；
 * 调用方据此保证"配置坏了也不会让功能不可用"。
 * <p>命名沿用工程的 JavaBean 风格（设计 §4.3：不引入 record 风格访问器）。
 *
 * @param <T> 本键的类型（今天用到 {@link Integer} 与 {@link String}、{@link Boolean}）
 */
public final class ConfigKey<T> {

    private final String path;
    private final T defaultValue;
    private final T min;
    private final T max;
    private final String description;

    private ConfigKey(String path, T defaultValue, T min, T max, String description) {
        if (path == null || path.trim().isEmpty()) {
            throw new IllegalArgumentException("Config key path cannot be null or empty.");
        }
        if (defaultValue == null) {
            throw new IllegalArgumentException("Config key '" + path + "' needs a default value.");
        }
        this.path = path;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.description = description != null ? description : "";
    }

    /**
     * 整数键：越界**夹取**到 `[min, max]`；非 {@link Number}（含缺失、字符串、坏文件）⇒ 默认值。
     */
    public static ConfigKey<Integer> integer(String path, int defaultValue, int min, int max, String description) {
        if (min > max) {
            throw new IllegalArgumentException("Config key '" + path + "': min > max.");
        }
        if (defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException("Config key '" + path + "': default " + defaultValue
                    + " is outside " + min + "-" + max + ".");
        }
        return new ConfigKey<>(path, defaultValue, min, max, description);
    }

    /** 字符串键：非 {@link String}（含缺失）⇒ 默认值。 */
    public static ConfigKey<String> text(String path, String defaultValue, String description) {
        return new ConfigKey<>(path, defaultValue, null, null, description);
    }

    /** 布尔键：非 {@link Boolean}（含缺失）⇒ 默认值。 */
    public static ConfigKey<Boolean> bool(String path, boolean defaultValue, String description) {
        return new ConfigKey<>(path, defaultValue, null, null, description);
    }

    /** 配置文件里的字段名（点号分层，与 Bukkit 的 `get` 路径一致）。 */
    public String getPath() {
        return path;
    }

    /** 缺失 / 非法时的回落值。 */
    public T getDefaultValue() {
        return defaultValue;
    }

    /** 取值范围下界（仅数值键非 `null`）。 */
    public T getMin() {
        return min;
    }

    /** 取值范围上界（仅数值键非 `null`）。 */
    public T getMax() {
        return max;
    }

    /** 一句话说明（写给人看；与 `config.yml` 里的注释同义）。 */
    public String getDescription() {
        return description;
    }

    /**
     * 把从文件读到的**任意原始值**归一到本键的类型与取值范围：**永不抛异常**。
     *
     * @param raw 读盘得到的原始值（可为 `null`）
     * @return 归一结果（值 + 是否落回默认 + 是否被夹取 + 原始值），供调用方决定要不要打日志
     */
    @SuppressWarnings("unchecked")
    public Resolution<T> resolve(Object raw) {
        if (defaultValue instanceof Integer) {
            // 与既有语义逐字一致：只有 Number 才算整数（带引号的 "3" 与非整数 abc 一样回落默认值）
            if (!(raw instanceof Number number)) {
                return fallback(raw);
            }
            int value = number.intValue();
            int clamped = Math.clamp(value, (Integer) min, (Integer) max);
            return new Resolution<>((T) Integer.valueOf(clamped), false, clamped != value, raw);
        }
        if (defaultValue instanceof Boolean) {
            if (!(raw instanceof Boolean flag)) {
                return fallback(raw);
            }
            return new Resolution<>((T) flag, false, false, raw);
        }
        if (raw instanceof String text) {
            return new Resolution<>((T) text, false, false, raw);
        }
        return fallback(raw);
    }

    /** 缺失 / 类型不符 / 不可解析 ⇒ 默认值（**不抛异常**）。 */
    private Resolution<T> fallback(Object raw) {
        return new Resolution<>(defaultValue, true, false, raw);
    }

    @Override
    public String toString() {
        return "ConfigKey(" + path + ", default=" + defaultValue + ")";
    }

    /**
     * 归一结果：**不可变**。`defaultValueUsed` = 缺失 / 类型不符 / 不可解析；
     * `clamped` = 数值越界被夹取；`raw` = 读盘原始值（用于日志里给运维看"你写的是什么"）。
     */
    public static final class Resolution<T> {

        private final T value;
        private final boolean defaultValueUsed;
        private final boolean clamped;
        private final Object raw;

        private Resolution(T value, boolean defaultValueUsed, boolean clamped, Object raw) {
            this.value = value;
            this.defaultValueUsed = defaultValueUsed;
            this.clamped = clamped;
            this.raw = raw;
        }

        /** 归一后的值（保证落在本键的类型与取值范围内）。 */
        public T getValue() {
            return value;
        }

        /** 是否落回了默认值（缺失 / 类型不符 / 读不动）。 */
        public boolean isDefaultValueUsed() {
            return defaultValueUsed;
        }

        /** 是否被夹取（原始数值越界）。 */
        public boolean isClamped() {
            return clamped;
        }

        /** 读盘得到的原始值（可为 `null`）。 */
        public Object getRaw() {
            return raw;
        }
    }
}
