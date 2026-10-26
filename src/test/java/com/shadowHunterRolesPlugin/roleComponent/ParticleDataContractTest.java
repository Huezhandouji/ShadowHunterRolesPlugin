package com.shadowHunterRolesPlugin.roleComponent;

import org.bukkit.Particle;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertTrue;

/**
 * 源码级回归闸门：**需要 data 的粒子绝不允许"不带 data 就 spawn"**。
 *
 * <h2>为什么要有这个闸门</h2>
 * 林 Lin 的二技能（{@code lin_skill_tripleStrike}）曾在运行期崩过一次，日志是：
 * <pre>
 *   exception=java.lang.IllegalArgumentException: missing required data class org.bukkit.Color
 *     at org.bukkit.craftbukkit.CraftParticle.createParticleParam(CraftParticle.java:70)
 *     at org.bukkit.craftbukkit.CraftWorld.spawnParticle(CraftWorld.java:1769)
 * </pre>
 * 根因 = {@code Particle.FLASH} 在 Paper 里**带数据类型**（{@code org.bukkit.Color}），
 * 旧写法漏传 data。危险之处不在"那一颗粒子没出来"，而在**故障隔离**：
 * 组件在 {@code onCast} 抛异常 ⇒ {@code RoleInstance.quarantine()} ⇒ **整个角色被下线**
 * ⇒ 该角色所有技能（左键/右键/Q）全部失灵，而日志只留下一句 component failure。
 *
 * <h2>本闸门在测什么</h2>
 * 静态扫描 {@code src/main/java} 下全部 {@code spawnParticle(...)} 调用，对**数据类型非 {@code Void}** 的粒子，
 * 断言实参个数足够把 data 递进去。
 *
 * <h2>为什么不能只数实参个数（★ 本闸门的设计要点）</h2>
 * 初版曾用"顶层逗号 ≥ 6 就算递了 data"，**实测漏检**：把林 Lin 的 FLASH 旧 BUG 注入回去后闸门仍然通过。
 * 根因是**重载歧义** —— 以 7 个实参（6 个顶层逗号）为例，{@code World} 上有两个候选：
 * <pre>
 *   spawnParticle(Particle, Location, int, double, double, double)      // 6 参，**无** data
 *   spawnParticle(Particle, Location, int, double, double, double, T)  // 7 参，有 data
 * </pre>
 * 两者逗号数同为 6 ⇒ **数逗号无法区分**。真正的区别在**最后一个实参的类型**：
 * 旧 BUG 传的是 {@code 0d}（double ⇒ 命中无 data 重载），修好的版本传的是 {@code LIN_HOLY_WHITE}（Color ⇒ 命中有 data 重载）。
 *
 * <h2>因此改用"末位实参"判据</h2>
 * 观察所有带 data 的重载：{@code T data} **永远是最后一个形参**。于是不必猜"data 在第几位"，
 * 直接看**最后一个实参**即可 —— 对非数值型 data 的粒子：
 * <ul>
 *   <li>末位是<b>裸数字字面量</b>（{@code 0d} / {@code 0.5} / {@code 0}…）⇒ 命中的一定是**无 data 重载**
 *       ⇒ **漏传 data**。</li>
 *   <li>末位是变量 / {@code new Xxx(...)} / {@code Color.RED} 等表达式 ⇒ 形状对，放行。</li>
 * </ul>
 * 该判据对"带不带 {@code speed}"两种写法都成立（实测四种写法全对）：
 * <pre>
 *   (Particle.DUST, at, 18, .4d,.4d,.4d, .05d, new DustOptions(…))  末位=表达式 ⇒ 放行
 *   (Particle.DUST, at,  1,   0,   0,   0,        options)          末位=表达式 ⇒ 放行
 *   (Particle.FLASH, at, 1, 0d, 0d, 0d, 0d)                        末位=0d     ⇒ ★报错
 *   (Particle.FLASH, at, 1, 0d, 0d, 0d, 0d, LIN_HOLY_WHITE)        末位=表达式 ⇒ 放行
 * </pre>
 * <b>为什么不用"第 7 个实参"固定下标</b>：初版试过，产生 36 处误报 ——
 * 因为带 {@code speed} 的调用把 data 顶到了第 8 位，第 7 位本就是合法的 {@code speed} 数字。
 * 只有"末位"这个看**相对位置**的判据才同时覆盖两种重载。
 *
 * <h2>为什么用"顶层逗号"而不是"实参计数"</h2>
 * data 常常是内联表达式（{@code new Particle.DustOptions(...)}、{@code Color.RED}…），
 * 其内部的逗号**属于那个构造调用**、不属于 {@code spawnParticle}。
 * 只有**括号/花括号深度为 0** 处的逗号才是实参分隔符 —— 本测试按此切分，
 * 所以像 {@code new Particle.Spell(Color.RED, 1f)} 这种内嵌逗号不会被误判成多余实参。
 *
 * <p>2026-10-08 建立。基线：本仓库 128 处 {@code spawnParticle} 调用全部合规。
 */
public class ParticleDataContractTest {

    /** 保留它，免得有人以为"粒子清单写死在代码里"就没事 —— 真正的判据永远是 {@code getDataType()}。 */
    private static final String MAIN_SRC = "src/main/java";

    /**
     * 从 paper-api 现算"需要 data"的粒子集合。
     *
     * <p>刻意**不硬编码**名单：上游 Paper 随时会给某颗粒子加/去掉数据类型，
     * 硬编码的名单会在那天悄悄过期，而本闸门存在的全部意义就是"跟着 API 走"。
     */
    private static Set<Particle> dataRequiringParticles() {
        return EnumSet.allOf(Particle.class).stream()
                .filter(p -> p.getDataType() != Void.class)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(Particle.class)));
    }

    /** 该粒子的 data 类型是不是"数值"（Float/Integer 等）——数值型不能用"是不是数字字面量"来判 bug。 */
    private static boolean dataIsNumeric(Particle p) {
        Class<?> d = p.getDataType();
        return Number.class.isAssignableFrom(d)
                || d == float.class || d == double.class || d == int.class
                || d == long.class || d == short.class || d == byte.class;
    }

    @Test
    public void everyDataRequiringParticleIsSpawnedWithData() throws IOException {
        Set<Particle> needsData = dataRequiringParticles();

        // 自我校验：若 getDataType() 全部返回 Void，说明取数方式失效（而不是"仓库很干净"）。
        assertTrue("Particle#getDataType() 未返回任何带数据的粒子，判据已失效，请检查 paper-api 依赖",
                !needsData.isEmpty());

        Path root = Paths.get(MAIN_SRC);
        assertTrue("找不到源码目录：" + root.toAbsolutePath(), Files.isDirectory(root));

        List<String> offenders = new ArrayList<>();
        int[] checked = {0};

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String src = new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
                scanFile(file, src, needsData, offenders, checked);
            }
        }

        // 与"帧独立"同样重要的正向读数：确实扫到了东西（防止正则整体失效导致真空绿）。
        assertTrue("未扫描到任何 spawnParticle 调用，闸门可能失效", checked[0] > 0);

        assertTrue("以下调用对**需要 data** 的粒子漏传了 data（运行期会抛 "
                        + "\"missing required data class\"，并因故障隔离把整个角色下线）：\n"
                        + String.join("\n", offenders),
                offenders.isEmpty());
    }

    /** 在单个文件里找出全部 {@code spawnParticle(} 调用并逐条核对。 */
    private void scanFile(Path file, String src, Set<Particle> needsData,
                          List<String> offenders, int[] checked) {
        String needle = "spawnParticle";
        int from = 0;
        while (true) {
            int idx = src.indexOf(needle, from);
            if (idx < 0) {
                return;
            }
            from = idx + needle.length();

            // 必须是真正的调用：spawnParticle 之后（跳过空白）紧跟 '('
            int open = idx + needle.length();
            while (open < src.length() && Character.isWhitespace(src.charAt(open))) {
                open++;
            }
            if (open >= src.length() || src.charAt(open) != '(') {
                continue;
            }

            int close = matchingClose(src, open);
            if (close < 0) {
                continue; // 括号不配平（不该发生）——跳过，交给编译器处理
            }
            String call = src.substring(open, close + 1);

            Particle particle = particleOf(call);
            if (particle == null || !needsData.contains(particle)) {
                continue;
            }
            // 数值型 data 的粒子（DRAGON_BREATH / SCULK_CHARGE / SHRIEK）不适用本判据，跳过以免误报。
            if (dataIsNumeric(particle)) {
                continue;
            }

            List<String> args = splitTopLevelArgs(call);
            // 只审"以 Location 定位"的调用（(x,y,z) 定位那族重载实参布局不同，本仓库不用）。
            if (!locatesWithLocation(args)) {
                continue;
            }
            checked[0]++;

            // ★ 核心判据：非数值型 data 的粒子，**末位实参**绝不能是裸数字字面量。
            //   （所有带 data 的重载里，`T data` 都是最后一个形参 ⇒ 看末位即看 data 位。）
            String last = args.isEmpty() ? "" : args.get(args.size() - 1);
            if (looksNumeric(last)) {
                int line = 1;
                for (int i = 0; i < idx; i++) {
                    if (src.charAt(i) == '\n') {
                        line++;
                    }
                }
                offenders.add(String.format(Locale.ROOT,
                        "  %s:%d  Particle.%s 需要 %s，但末位实参是数字字面量 `%s`（⇒ 命中了无 data 重载）",
                        file, line, particle.name(), particle.getDataType().getSimpleName(), last));
            }
        }
    }

    /**
     * 该实参是否"看起来是数值字面量"（如 {@code 0}、{@code 0d}、{@code 0.5F}、{@code 1L}）。
     *
     * <p>只认**纯字面量**，不认表达式：像 {@code args.get(6)} 是 {@code new Particle.DustOptions(...)}
     * 或 {@code LIN_HOLY_WHITE} 这种，一律不算数字 ⇒ 不报错。这正是我们要的——
     * 只有"写死了个数字顶上 data 位"才是 bug。
     */
    private boolean looksNumeric(String arg) {
        String t = arg.trim();
        if (t.isEmpty()) {
            return false;
        }
        // 去掉尾部可能存在的转型/后缀组合，如 `(float) 0` 不在此列（含空格⇒表达式，不判）
        return t.matches("[+-]?(?:\\d+\\.?\\d*|\\.\\d+)[dDfFlL]?")
                || t.matches("[+-]?0[xX][0-9a-fA-F]+[lL]?");
    }

    /**
     * 该调用的第 2 个实参是不是 {@link org.bukkit.Location}（或其表达式）？
     *
     * <p>用来区分两族重载：{@code (Particle, Location, int, …)} 与
     * {@code (Particle, x, y, z, int, …)}。后者的 data 落点与前者不同，
     * 本仓库只走前者；若哪天有人用后者，会在此被跳过而非误报。
     * 判据：第二个实参不是裸数字字面量。
     */
    private boolean locatesWithLocation(List<String> args) {
        if (args.size() < 2) {
            return false;
        }
        return !looksNumeric(args.get(1));
    }

    /** 按顶层逗号把 {@code (a, b, c)} 切成 {@code [a, b, c]}；空参数 ⇒ 空列表。 */
    private List<String> splitTopLevelArgs(String call) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = 1; // 跳过开头的 '('
        for (int i = 1; i < call.length() - 1; i++) {
            char c = call.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == ',' && depth == 0) {
                out.add(call.substring(start, i));
                start = i + 1;
            }
        }
        if (start < call.length() - 1) {
            out.add(call.substring(start, call.length() - 1));
        }
        return out;
    }

    /** 取调用里出现的第一个 {@code Particle.XXX} 常量名，转回枚举；找不到/不是常量则 null。 */
    private Particle particleOf(String call) {
        int p = call.indexOf("Particle.");
        if (p < 0) {
            return null;
        }
        int nameStart = p + "Particle.".length();
        int nameEnd = nameStart;
        while (nameEnd < call.length()) {
            char c = call.charAt(nameEnd);
            if (Character.isLetterOrDigit(c) || c == '_') {
                nameEnd++;
            } else {
                break;
            }
        }
        if (nameEnd == nameStart) {
            return null;
        }
        String name = call.substring(nameStart, nameEnd);
        try {
            return Particle.valueOf(name);
        } catch (IllegalArgumentException notAConstant) {
            return null; // 例如 Particle.DustOptions / Particle.Spell（是内部类，不是枚举常量）
        }
    }

    /**
     * 从 {@code '('} 开始找到配平的 {@code ')'}，返回其下标；扫描时同时兼顾
     * {@code ()}、{@code []}、{@code {}} 三种括号，避免字符串/数组初始化里的括号干扰。
     */
    private int matchingClose(String src, int open) {
        int depth = 0;
        for (int i = open; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
