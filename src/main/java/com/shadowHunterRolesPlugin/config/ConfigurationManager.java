package com.shadowHunterRolesPlugin.config;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * 配置管理器：本插件的**唯一读盘口径** —— 所有配置字段都经这里取值。
 *
 * <p><b>为什么要有它</b>：在此之前"读配置"与"读 {@code ops.json}"两套机制各写一遍缓存与回落，
 * 且主类**从不调用 {@code saveDefaultConfig()}** ⇒ jar 内置的默认值只在内存里生效、
 * **永不落盘**到 {@code plugins/ShadowHunterRolesPlugin/config.yml}，运维根本发现不了那些字段。
 * 现在：① 默认配置**落盘**（主类 `onEnable` 先 `saveDefaultConfig()`）；② 取值只剩这一处；
 * ③ 每个字段的定义集中在 {@link ConfigKey}（默认值 / 校验 / 说明一处）。
 *
 * <p><b>生效时效（逐字一致，冻结语义）</b>：数据目录内 {@code config.yml} 带
 * {@value #TTL_MILLIS} ms TTL + **文件戳（mtime×31+长度）变更即失效** ⇒ 改完配置文件**最迟一个 TTL**
 * 内生效，**无需重启或 reload**。文件不存在时回落 **jar 内置默认值**（`plugin.getConfig()`）。
 *
 * <p><b>安全口径</b>：缺失 / 类型不符 / 非整数 / 文件读不动 ⇒ 回落默认值且**不抛异常**
 * （指令不会因此不可用）；数值越界 ⇒ 夹取到 `[min, max]`。
 *
 * <p><b>可观测</b>：首次读到某键、以及**因文件变更而重读导致取值变化**时，各打一行含
 * **新旧值**的 INFO（前缀 {@code [config]}）⇒ 运行级证据可直接取原始行。
 */
public final class ConfigurationManager {

    /**
 * 指令权限等级（等价于 `CommandAccess.CONFIG_KEY`）：字段名、默认值 3、域 0-4
     * **逐字沿用**已冻结的口径。
     */
    public static final ConfigKey<Integer> COMMAND_PERMISSION_LEVEL = ConfigKey.integer(
            "command-permission-level", 3, 0, 4,
            "使用本插件指令（/role 及其全部子指令）所需的最低权限等级；0-4，默认 3");

    /** 读盘缓存 TTL（毫秒）——与 {@code ops.json} 等级表同一机制（冻结语义）。 */
    private static final long TTL_MILLIS = 5_000L;

    /** 已安装的实例（主类在 `onEnable` 安装；与 `KeyFactory.Registry` 同一种静态桥的写法）。 */
    private static ConfigurationManager installed;

    private final Plugin plugin;
    private final File file;
    private final Logger logger;

    /** 上次读盘时刻（ms）；0 = 还没读过。 */
    private long loadedAt = 0L;
    /** 上次读盘时的文件戳（mtime×31+长度）；文件一变即失效。 */
    private long fileStamp = Long.MIN_VALUE;
    /** 本次刷新的读取源（文件存在 ⇒ 数据目录文件；否则 ⇒ jar 内置默认值）；读失败 ⇒ `null`（⇒ 一律默认值）。 */
    private Configuration source;
    /** 读取源的标签（写进日志，便于运维判断"读的到底是哪一份"）。 */
    private String sourceLabel = "unread";
    /** 上次读盘失败的原因（只打一行，不刷屏）。 */
    private String loadFailure = null;

    /** 每个键**上次已打日志的归一值**（取值变化时才再打一行，含新旧值）。 */
    private final Map<String, Object> loggedValue = new HashMap<>();

    public ConfigurationManager(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.file = new File(plugin.getDataFolder(), "config.yml");
        this.logger = plugin.getLogger();
    }

    /** 安装为全局实例（主类在 `onEnable` 调用；重复安装以后者为准）。 */
    public static void install(ConfigurationManager manager) {
        installed = manager;
    }

    /** 已安装的实例；**未安装 ⇒ `null`**（调用方自行回落默认值，不抛异常）。 */
    public static ConfigurationManager installed() {
        return installed;
    }

    /** 本插件数据目录内的 {@code config.yml}。 */
    public File getFile() {
        return file;
    }

    /**
     * 取一个配置键的当前值：类型化 + 越界夹取 + 缺失/非法**安全回落**（**永不抛异常**）。
     */
    public <T> T get(ConfigKey<T> key) {
        Objects.requireNonNull(key, "key");
        refreshIfStale();
        Object raw = source != null ? source.get(key.getPath(), null) : null;
        ConfigKey.Resolution<T> resolution = key.resolve(raw);
        logIfChanged(key, resolution);
        return resolution.getValue();
    }

    // ───────────── 内部：读盘 + 缓存 ─────────────

    private void refreshIfStale() {
        long now = System.currentTimeMillis();
        long stamp = file.isFile() ? (file.lastModified() * 31L + file.length()) : -1L;
        if (loadedAt != 0L && (now - loadedAt) < TTL_MILLIS && stamp == fileStamp) {
            return;
        }
        loadedAt = now;
        fileStamp = stamp;
        try {
            if (file.isFile()) {
                //数据目录里那份是运维的真实意愿 ⇒ 优先；loadConfiguration 对坏 YAML 不会抛（只告警）
                source = YamlConfiguration.loadConfiguration(file);
                sourceLabel = "file";
            } else {
 //文件不在 ⇒ 回落 jar 内置默认值（同一条分支）
                source = plugin.getConfig();
                sourceLabel = "jar-default";
            }
            if (loadFailure != null) {
                log("config readable again at " + file.getAbsolutePath() + " (source=" + sourceLabel + ")");
                loadFailure = null;
            }
        } catch (Throwable failure) {
            //读不动 ⇒ 一律默认值，绝不抛异常、绝不让指令因此不可用
            source = null;
            sourceLabel = "unreadable";
            String reason = failure.getClass().getSimpleName() + ": " + failure.getMessage();
            if (!reason.equals(loadFailure)) {
                loadFailure = reason;
                log("config file unreadable at " + file.getAbsolutePath()
                        + " — falling back to defaults — " + reason);
            }
        }
    }

    private void logIfChanged(ConfigKey<?> key, ConfigKey.Resolution<?> resolution) {
        String path = key.getPath();
        Object raw = resolution.getRaw();
        if (resolution.isDefaultValueUsed() && raw != null) {
            log("config " + path + "=" + raw + " is not a usable value — falling back to "
                    + key.getDefaultValue());
        }
        if (resolution.isClamped()) {
            log("config " + path + "=" + raw + " out of range " + key.getMin() + "-" + key.getMax()
                    + " — clamped to " + resolution.getValue());
        }
        Object current = resolution.getValue();
        Object previous = loggedValue.get(path);
        if (previous == null) {
            loggedValue.put(path, current);
            log("config " + path + " = " + current + " (source=" + sourceLabel + ")");
        } else if (!previous.equals(current)) {
            loggedValue.put(path, current);
            //因文件变更而重读时，**一行里同时给出新旧值** ⇒ 运行级证据可直接取原始行
            log("config " + path + ": " + previous + " -> " + current + " (source=" + sourceLabel + ")");
        }
    }

    private void log(String text) {
        logger.info("[config] " + text);
    }
}
