package com.shadowHunterRolesPlugin.command;

import com.shadowHunterRolesPlugin.ShadowHunterRolesPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * 玩家目标解析：四条带玩家目标参数的子指令（{@code set} / {@code clear} / {@code energy} / {@code operation}）
 * 共用的**唯一**解析入口。
 *
 * <p><b>接受集</b>：省略目标（{@code null} / 空白）与 {@code @s} ⇒ 执行者自己；以 {@code @} 开头的 token ⇒ 交
 * {@link Bukkit#selectEntities(CommandSender, String)}；其余 ⇒ 裸玩家名（先精确匹配，再宽松匹配）。
 *
 * <p><b>唯一的接收判据</b>：解析结果**恰好 1 名在线玩家**。选择器命中 0 名、命中的不是玩家（{@code @e} 落在
 * 实体上）、或命中多于一名，一律拒绝 —— **不静默取第一个**。判定不按 token 内容分流（同一个 token 只有一把尺），
 * 失败的**原因**只经 {@link Result#failure()} 交给调用方挑措辞。
 *
 * <p><b>异常</b>：{@link Bukkit#selectEntities(CommandSender, String)} 对语法非法的选择器抛
 * {@link IllegalArgumentException}，本类**就地捕获**并归为 {@link Failure#MALFORMED} ⇒ 不向指令层冒泡。
 *
 * <p><b>可见面</b>：本类包私有、无公开成员；它新增的玩家侧文本只有 {@link #rejection} 在**选择器**失败时给出的三句，
 * 非选择器失败一律回调用方自己的既有句。
 */
final class PlayerTargets {

    /** 解析失败的真实原因（只用于挑措辞，不参与判定）。 */
    enum Failure {
        /** 以 {@code @} 开头、但选择器语法非法。 */
        MALFORMED,
        /** 选择器语法合法、却没有命中玩家（含命中非玩家实体）。 */
        NO_MATCH,
        /** 选择器命中多于一名。 */
        MULTIPLE,
        /** 非选择器输入（裸玩家名）：两查皆空，或命中的玩家已离线。 */
        NOT_FOUND
    }

    /** 解析结果：{@code player} 非空 ⇒ 恰好 1 名在线玩家；否则 {@code failure} 给出原因。 */
    record Result(Player player, Failure failure, int matched) {

        static Result hit(Player player) {
            return new Result(player, null, 1);
        }

        static Result miss(Failure failure, int matched) {
            return new Result(null, failure, matched);
        }

        /** 是否解析成功（成功 ⟺ {@link #player()} 非空）。 */
        boolean resolved() {
            return player != null;
        }
    }

    private PlayerTargets() {
    }

    /** 解析一段目标 token；任何失败或歧义 ⇒ 失败结果（**不抛**）。 */
    static Result resolve(CommandSender sender, String raw) {
        if (raw == null || raw.isBlank()) {
            return selfOrMiss(sender);
        }
        if (ComponentOperationDispatcher.SELF_TOKEN.equalsIgnoreCase(raw)) {
            return selfOrMiss(sender);
        }
        if (raw.startsWith("@")) {
            return resolveSelector(sender, raw);
        }
        Player exact = Bukkit.getPlayerExact(raw);
        if (exact != null) {
            return exact.isOnline() ? Result.hit(exact) : Result.miss(Failure.NOT_FOUND, 0);
        }
        Player lenient = Bukkit.getPlayer(raw);
        if (lenient == null || !lenient.isOnline()) {
            return Result.miss(Failure.NOT_FOUND, 0);
        }
        return Result.hit(lenient);
    }

    /**
     * 回绝措辞：**选择器**失败按真实原因分三句；**非选择器**失败回 {@code notFoundSentence}（调用方自己的既有句）。
     *
     * @param token            用户**原样**输入的那一段（选择器三句里原样回显，不归一化、不加引号）
     * @param result           {@link #resolve} 的失败结果
     * @param notFoundSentence 非选择器失败时调用方的既有文案（由调用方给出，本类不改它）
     */
    static String rejection(String token, Result result, String notFoundSentence) {
        return switch (result.failure()) {
            case MALFORMED -> "Cannot parse the target selector you provided: " + token;
            case NO_MATCH -> "No online player matched the target selector you provided: " + token;
            case MULTIPLE -> "The target selector you provided matched " + result.matched()
                    + " players; it must match exactly one: " + token;
            case NOT_FOUND -> notFoundSentence;
        };
    }

    private static Result resolveSelector(CommandSender sender, String raw) {
        List<Entity> selected;
        try {
            selected = Bukkit.selectEntities(sender, raw);
        } catch (IllegalArgumentException malformed) {
            ShadowHunterRolesPlugin plugin = ShadowHunterRolesPlugin.getInstance();
            if (plugin != null) {
                plugin.getLogger().info("[target-selector] rejected token=" + raw
                        + " exception=" + malformed.getClass().getName());
            }
            return Result.miss(Failure.MALFORMED, 0);
        }
        if (selected == null || selected.isEmpty()) {
            return Result.miss(Failure.NO_MATCH, 0);
        }
        if (selected.size() > 1) {
            return Result.miss(Failure.MULTIPLE, selected.size());
        }
        Entity only = selected.get(0);
        if (!(only instanceof Player player) || !player.isOnline()) {
            return Result.miss(Failure.NO_MATCH, selected.size());
        }
        return Result.hit(player);
    }

    private static Result selfOrMiss(CommandSender sender) {
        return sender instanceof Player player ? Result.hit(player) : Result.miss(Failure.NOT_FOUND, 0);
    }
}
