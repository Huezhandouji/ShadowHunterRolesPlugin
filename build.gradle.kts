import java.util.Properties

plugins {
    id("java-library")
    id("xyz.jpenilla.run-paper") version "3.0.2"
    id("maven-publish")
    kotlin("jvm")
}


group = "com.shadowHunterRolesPlugin"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
    repositories {
        mavenLocal()
    }
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation(kotlin("test"))
    // 阶段 9 · 测试缝（t57）：JUnit 4 —— 按冻结件 §3.1 的 A 案选型（缓存里**已有** junit-4.13.2.jar ⇒
    // 这条声明不需要联网，`gradlew test --offline` 可解析）。JUnit 5 冻结为升级触发条件，不在本批。
    testImplementation("junit:junit:4.13.2")
    // 测试源集要读主源集的类型（`Material` 等）：用 **compileOnly 变体**（与主源集**同一份**已缓存依赖）——
    // `testImplementation` 会去解运行期变体，而它的部分传递依赖本机没有缓存 ⇒ 离线会失败（实测）。
    testCompileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

// ── 阶段 10 · t59（裁定 P3）：入库默认值 + 被忽略的本地覆盖 ────────────────────────────────
// 本机专属项（服务端 plugins 目录等）放在 **gradle.local.properties**：它被 .gitignore 忽略 ⇒ 不入库、
// 不影响别人；**文件不存在时是 no-op**（不报错），任务照常可用。查找顺序 = 本地覆盖 → 入库默认值。
val localPropertiesFile = layout.projectDirectory.file("gradle.local.properties").asFile
val localProperties = Properties().apply {
    if (localPropertiesFile.isFile) {
        // 必须按 **UTF-8** 读：`Properties.load(InputStream)` 是 ISO-8859-1 ⇒ 值里含 CJK 路径会被解成乱码 ✗
        // （本工程的路径恰好含 CJK，实测踩到）
        localPropertiesFile.reader(Charsets.UTF_8).use { load(it) }
    }
}

fun configValue(name: String): String? =
    localProperties.getProperty(name) ?: (project.findProperty(name) as String?)

// 阶段 10 · t59：源码是 UTF-8，而 t58 立的 charset 前置把守护进程默认字符集改成 GBK ⇒
// javac 的**默认源码编码**会跟着变成 GBK ⇒ 含非 GBK 字符的源文件报「编码 GBK 的不可映射字符」✗。
// 把源码编码**钉死为 UTF-8**，与守护进程字符集解耦（否则 `gradlew build` 一旦真编译就会红）。
tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// ── Javadoc 任务的同族前置（第四处"文本 I/O 必须各自钉死字符集"）─────────────────────────
// 现象：`gradlew javadoc` 在**中文路径**下必红 ——
//   `错误: 无法读取Input length = 1`（MalformedInputException）⇒ 不生成任何文档。
// 根因：**写方与读方用的不是一套字符集**
//   写方 = Gradle 用守护进程默认字符集（本工程被 t58 钉成 **GBK**）写 `build/tmp/javadoc/javadoc.options`；
//   读方 = JDK 21 的 javadoc 按 **UTF-8** 读 @argfile（JEP 400 起 `file.encoding` 恒为 UTF-8，与系统区域无关）。
//   ⇒ 参数文件里的 CJK 路径（本工程路径含「插件」）在 UTF-8 下是非法字节序列 ⇒ 解码失败 ⇒ 整个任务失败。
// 关键：`options.encoding` 只影响**源码**怎么读，**管不到 @argfile 的读法**，所以必须另想办法。
// 这里用的办法 = 让 javadoc 那个 JVM 的默认字符集回到 GBK（`-J-Dfile.encoding=GBK` 经 `options.jvmArgs`
// 下发给工具进程）⇒ 读方与写方一致。★ 不要顺手把守护进程改回 UTF-8：那会让 `:test` 的 argfile 反过来炸
// （t58 立 GBK 正是为它），两处必须各自钉自己的。
// 另注：`charSet`/`docEncoding` 钉的是**产物 HTML / 索引**的字符集，与上面的读写 charset 是两件事。
tasks.withType<Javadoc>().configureEach {
    options.encoding = "UTF-8"   // 源码（本工程全部源码是 UTF-8）
}


// 阶段 10 · t55：`processResources` 的 `expand` **默认用平台默认字符集** ⇒ 守护进程被钉成 GBK 时
// （t58 为 `:test` 的 argfile 立的前置），UTF-8 的 `plugin.yml` 会被回写成**非 UTF-8** ⇒
// Paper 报 `Invalid plugin.yml / MalformedInputException` ⇒ **jar 不可加载**（产品级）。
// 这里把资源过滤的字符集钉死为 UTF-8，与源码编码、守护进程字符集**解耦**（同族第三次：javac 默认源码编码 ·
// Properties.load(InputStream) · 本处的 expand ⇒ 凡文本 I/O 都要各自钉死）。
tasks.withType<org.gradle.language.jvm.tasks.ProcessResources>().configureEach {
    filteringCharset = "UTF-8"
}

tasks {
    runServer {
        // Configure the Minecraft version for our task.
        // This is the only required configuration besides applying the plugin.
        // Your plugin's jar (or shadowJar if present) will be used automatically.
        minecraftVersion("1.21.11")
        jvmArgs("-Xms2G", "-Xmx2G")
    }

    test {
        // 测试**运行期**复用主源集已缓存的 compile classpath（paper-api + adventure + guava …）：
        // 全程离线可解析，且只挂在 test 任务上 ⇒ 成品 jar / runServer 一律不受影响（判据 C-06）。
        classpath += sourceSets.main.get().compileClasspath
    }

    processResources {
        val props = mapOf("version" to version)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

}
kotlin {
    jvmToolchain(21)
}

//阶段 10 · t59（A4）：目标目录来自 本地覆盖(gradle.local.properties) → 入库默认值(gradle.properties)。
//两种"没有可用目标"的情形都给**可诊断提示**并跳过复制（绝不静默指向/创建不存在的目录 ✗）：
//  ① 完全没配置 pluginCopyPath；② 配了但那个目录不存在（例：新克隆里没有 run/ 也没有本机覆盖）。
tasks.register<Copy>("copyPluginJar") {
    val localValue = localProperties.getProperty("pluginCopyPath")
    val dest = configValue("pluginCopyPath")?.let { project.file(it) }
    val source = if (localValue != null) "gradle.local.properties（本地覆盖）" else "gradle.properties（入库默认值）"
    val jarProvider = layout.buildDirectory.file("libs/${project.name}-${project.version}.jar")
    val valid = dest != null && dest.isDirectory
    // 始终给 source ⇒ 任务不会判 NO-SOURCE（否则 doLast 里的诊断提示不会执行 ✗）
    from(jarProvider)
    // 无可用目标时**不写任何真实位置**：只写 build/ 下的占位输出，让诊断提示能被执行到 ✓
    into(if (valid) dest!! else layout.buildDirectory.dir("copyPluginJar-no-target"))
    doFirst {
        // 目标与来源在任何一次执行里都先打出来（成功/失败都能看到）✓
        logger.lifecycle("[copyPluginJar] 目标目录 = ${dest ?: "(未配置)"} ；来源 = $source")
    }
    doLast {
        if (dest == null) {
            logger.warn("[copyPluginJar] 未配置 pluginCopyPath ⇒ **本次未复制**（占位输出在 build/copyPluginJar-no-target）。" +
                    "请在 gradle.local.properties（不入库）里写 pluginCopyPath=<服务端 plugins 目录> 后重跑。")
        } else if (!valid) {
            logger.warn("[copyPluginJar] 目标目录不存在：$dest ⇒ **本次未复制**（占位输出在 build/copyPluginJar-no-target，" +
                    "没有自动创建该目录）。请在 gradle.local.properties 里把 pluginCopyPath 指向真实的服务端 plugins 目录。")
        } else {
            logger.lifecycle("[copyPluginJar] 已复制到 $dest ；来源 = $source")
        }
    }
}

tasks.named("build"){
    finalizedBy("copyPluginJar")
}
