package com.shadowHunterRolesPlugin.command;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.RemoteConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 指令权限门禁 —— **全插件唯一的权限判定口径**（阶段 6 · t30；用户裁定「给本插件的指令都加上权限等级 ≥ 3 的限制」）。
 * <p>
 * <b>为什么读 {@code ops.json}</b>：本版 `paper-api`（1.21.11-R0.1-SNAPSHOT）**不提供可读的等级 API**
 * （全 jar 扫 `getOpLevel`/`setOpLevel`/`getPermissionLevel`/`permissionLevel`/`opLevel` 均 0 命中；
 * `Player` 内唯一含 `OpLevel` 的成员是只写向客户端的 {@code sendOpLevel(byte)}）⇒ 等级的唯一权威来源是
 * 服务器根目录的 {@code ops.json}。
 * <p>
 * <b>定位方式（不硬编码任何"启动目录名"）</b>：{@code Bukkit.getWorldContainer()}（= 世界目录的父目录 = 服务器根目录，
 * {@code ops.json} 与 {@code world/} 同级）。
 * <p>
 * <b>判定规则</b>：
 * <ol>
 *   <li>{@link Player} 且等级 ≥ **要求等级** ⇒ 允许；</li>
 *   <li>{@link Player} 且等级 &lt; 要求等级（含**不在 `ops.json` 中**）⇒ 拒绝；</li>
 *   <li>控制台 / RCON ⇒ **允许**（等价等级 4）；</li>
 *   <li>其他 {@link CommandSender}（如命令方块）⇒ 拒绝。</li>
 * </ol>
 * <b>要求等级 = 配置字段</b>：{@code config.yml} 的 {@value #CONFIG_KEY}
 * （取值范围 0-{@value #MAX_LEVEL}，默认 {@value #DEFAULT_REQUIRED_LEVEL}；越界自动夹取、缺失/非整数/不可读回落默认且不抛异常）。
 * <p><b>生效时效</b>：本字段与 {@code ops.json} 等级表**同一套失效机制** —— 数据目录内配置文件的
 * {@value #CONFIG_TTL_MILLIS} ms TTL + 文件戳（mtime×长度）变更即失效 ⇒ 改完配置文件**最迟一个 TTL** 内生效，
 * **无需重启或 reload**（文件不存在时回落到 jar 内置默认值）。
 * <p>
 * <b>失败关闭（fail-closed）</b>：{@code ops.json} 缺失 / 不可读 / JSON 非法 / 任一条目缺 `name` 或 `level`
 * ⇒ **整个文件视为不可信 ⇒ 所有玩家一律拒绝**，并打一条**含原因的 SEVERE**（不静默）；恢复后打一条 INFO。
 * <p>
 * <b>缓存与生效时效</b>：等级表带 **{@value #CACHE_TTL_MILLIS} ms TTL** + **文件戳（mtime×长度）变更即失效**
 * ⇒ 不每次执行都读盘；运维改完 {@code ops.json} 后**最迟一个 TTL** 内生效（且文件一旦变动立即失效）。
 * <p>
 * <b>日志</b>：每次判定打一行 INFO（{@code [command-access] allowed/denied … (level=…)}）⇒ 运行级证据可直接取原始行；
 * 该行是**服务端日志**，与玩家侧文案（{@link #NO_PERMISSION}）是两回事。
 */
final class CommandAccess {

    /** {@code config.yml} 里的要求等级字段名（用户裁定：等级由配置给出，不硬编码）。 */
    static final String CONFIG_KEY = "command-permission-level";

    /** 配置缺失/非法时使用的要求等级（= 用户裁定的 3）。 */
    static final int DEFAULT_REQUIRED_LEVEL = 3;

    /** 允许的等级上限（Minecraft 权限等级范围 0-4）。 */
    static final int MAX_LEVEL = 4;

    /** 上次已打日志的要求等级（配置值变化时再打一行，便于运行级取证）。 */
    private static int loggedRequiredLevel = Integer.MIN_VALUE;

    /** 配置文件读取缓存 TTL（毫秒）——与 ops.json 等级表同一机制。 */
    private static final long CONFIG_TTL_MILLIS = 5_000L;

    /** 上次读配置文件的时刻（ms）。 */
    private static long configLoadedAt = 0L;

    /** 上次读配置文件时的文件戳（mtime×31+长度）；文件一变即失效。 */
    private static long configFileStamp = Long.MIN_VALUE;

    /** 玩家侧拒绝文案（与 `DebugCommand` 既有文案**逐字相同**；沿用工程既有风格）。 */
    static final String NO_PERMISSION = "You do not have permission to use this command.";

    /** 等级表缓存 TTL（毫秒）。 */
    private static final long CACHE_TTL_MILLIS = 5_000L;

    /** 等级表：玩家名（小写）→ 等级；**失败关闭**时为空表（谁都不算有权限）。 */
    private static Map<String, Integer> levels = Map.of();
    /** 上次读盘时刻（ms）。 */
    private static long loadedAt = 0L;
    /** 上次读盘时的文件戳（mtime×31+长度）；文件一变即失效。 */
    private static long loadedFileStamp = Long.MIN_VALUE;
    /** 上次读盘是否失败（用于"失败只报一次、恢复再报一次"）。 */
    private static boolean lastLoadFailed = false;
    /** 上次失败原因（用于抑制重复 SEVERE）。 */
    private static String lastFailureReason = null;
    /** 是否已把解析到的 ops.json 绝对路径打进日志（一次性）。 */
    private static boolean pathLogged = false;

    private CommandAccess() {
    }

    /** 服务器根目录下的 {@code ops.json}（由 world container 推导，不硬编码启动目录名）。 */
    static File opsFile() {
        return new File(Bukkit.getWorldContainer(), "ops.json");
    }

    /** 本插件数据目录内的 {@code config.yml}（缺失时回落 jar 内置默认值）。 */
    static File configFile() {
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        return plugin != null ? new File(plugin.getDataFolder(), "config.yml") : null;
    }

    /**
     * 单一门禁判定：{@code true} = 允许本次指令（同时把判定结果打进服务端日志）。
     *
     * @param sender 指令发送者
     * @param action 用于日志的可读动作名（例如 {@code /role} / {@code /role debug}）
     */
    static boolean check(CommandSender sender, String action) {
        if (sender instanceof Player player) {
            refreshIfStale();
            Integer level = levels.get(player.getName().toLowerCase(Locale.ROOT));
            int effective = level != null ? level : 0;
            int required = requiredLevel();
            boolean allowed = effective >= required;
            log((allowed ? "allowed " : "denied ") + action + " for " + player.getName()
                    + " (level=" + effective + ", required=" + required + ")");
            return allowed;
        }
        if (sender instanceof ConsoleCommandSender || sender instanceof RemoteConsoleCommandSender) {
            log("allowed " + action + " for console (level=4 equivalent)");
            return true;
        }
        log("denied " + action + " for " + sender.getClass().getSimpleName() + " (unsupported sender)");
        return false;
    }

    /**
     * 当前**要求等级**：读数据目录内 {@code config.yml} 的 {@value #CONFIG_KEY}
     * （{@value #CONFIG_TTL_MILLIS} ms TTL + 文件戳失效，与 {@code ops.json} 同一机制）；
     * 文件不存在时回落 jar 内置默认；越界夹到 {@code 0}-{@value #MAX_LEVEL}；
     * 缺失键 / 非整数 / 读不动 ⇒ 回落 {@value #DEFAULT_REQUIRED_LEVEL} 且**不抛异常**（指令不会因此不可用）。
     * 值变化时打一行 INFO（便于运行级取证：配置确实被读到了）。
     */
    static int requiredLevel() {
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if (plugin == null) {
            return DEFAULT_REQUIRED_LEVEL;
        }
        long now = System.currentTimeMillis();
        File file = new File(plugin.getDataFolder(), "config.yml");
        long stamp = file.isFile() ? (file.lastModified() * 31L + file.length()) : -1L;

        int cached = loggedRequiredLevel;
        if (now - configLoadedAt < CONFIG_TTL_MILLIS && stamp == configFileStamp && configLoadedAt != 0L) {
            return cached == Integer.MIN_VALUE ? DEFAULT_REQUIRED_LEVEL : cached;
        }
        configLoadedAt = now;
        configFileStamp = stamp;

        int value;
        try {
            value = file.isFile()
                    ? YamlConfiguration.loadConfiguration(file).getInt(CONFIG_KEY, DEFAULT_REQUIRED_LEVEL)
                    : plugin.getConfig().getInt(CONFIG_KEY, DEFAULT_REQUIRED_LEVEL);
        } catch (Throwable t) {
            //A15：非整数 / 坏文件 / 读不动 ⇒ 回落默认，绝不抛异常、绝不让指令因此不可用
            value = DEFAULT_REQUIRED_LEVEL;
            log("config " + CONFIG_KEY + " unreadable — falling back to " + DEFAULT_REQUIRED_LEVEL
                    + " (" + t.getClass().getSimpleName() + ")");
        }
        int clamped = Math.clamp(value, 0, MAX_LEVEL);
        if (clamped != value) {
            log("config " + CONFIG_KEY + "=" + value + " out of range 0-" + MAX_LEVEL + " — clamped to " + clamped);
        }
        if (loggedRequiredLevel != clamped) {
            loggedRequiredLevel = clamped;
            log("required level = " + clamped + " (config " + CONFIG_KEY + ")");
        }
        return clamped;
    }

    /** 把玩家侧拒绝文案发给该 sender（非玩家 sender 不发聊天）。 */
    static void sendNoPermission(CommandSender sender) {
        if (sender instanceof Player player) {
            player.sendMessage(Component.text(NO_PERMISSION));
        }
    }

    // ───────────── 内部：读盘 + 缓存 ─────────────

    private static void refreshIfStale() {
        long now = System.currentTimeMillis();
        File file = opsFile();
        long stamp = file.isFile() ? (file.lastModified() * 31L + file.length()) : -1L;

        boolean fresh = (now - loadedAt) < CACHE_TTL_MILLIS && stamp == loadedFileStamp && loadedAt != 0L;
        if (fresh) return;

        loadedAt = now;
        loadedFileStamp = stamp;
        try {
            Map<String, Integer> parsed = parse(file);
            levels = parsed;
            if (lastLoadFailed) {
                lastLoadFailed = false;
                lastFailureReason = null;
                log("ops.json readable again: " + file.getAbsolutePath() + " (entries=" + parsed.size() + ")");
            }
            if (!pathLogged) {
                pathLogged = true;
                log("ops.json resolved at " + file.getAbsolutePath() + " (entries=" + parsed.size() + ")");
            }
        } catch (Throwable t) {
            //失败关闭：整个文件不可信 ⇒ 所有玩家一律拒绝（宁严不松），并响亮报错（不静默）
            levels = Map.of();
            String reason = t.getClass().getSimpleName() + ": " + t.getMessage();
            if (!lastLoadFailed || !reason.equals(lastFailureReason)) {
                lastLoadFailed = true;
                lastFailureReason = reason;
                severe("cannot read ops.json at " + file.getAbsolutePath()
                        + " — all players will be denied — " + reason);
            }
        }
    }

    private static Map<String, Integer> parse(File file) throws IOException {
        if (!file.isFile()) {
            throw new IOException("ops.json not found");
        }
        String raw = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        JsonElement root = JsonParser.parseString(raw);          //JSON 非法 ⇒ 抛异常 ⇒ 失败关闭
        if (!root.isJsonArray()) {
            throw new IOException("ops.json root is not a JSON array");
        }
        Map<String, Integer> parsed = new HashMap<>();
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                throw new IOException("ops.json contains a non-object entry");
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("name") || entry.get("name").isJsonNull()
                    || entry.get("name").getAsString().isBlank()) {
                throw new IOException("ops.json entry without a usable name");
            }
            if (!entry.has("level") || entry.get("level").isJsonNull() || !entry.get("level").isJsonPrimitive()
                    || !entry.get("level").getAsJsonPrimitive().isNumber()) {
                throw new IOException("ops.json entry '" + entry.get("name").getAsString() + "' without an integer level");
            }
            parsed.put(entry.get("name").getAsString().toLowerCase(Locale.ROOT), entry.get("level").getAsInt());
        }
        return Map.copyOf(parsed);
    }

    private static void log(String text) {
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if (plugin == null) return;
        plugin.getLogger().info("[command-access] " + text);
    }

    private static void severe(String text) {
        ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
        if (plugin == null) return;
        plugin.getLogger().severe("[command-access] " + text);
    }
}
