// ============================================================================
// 探针插件（工装件 · 阶段 13 · t129）—— **独立构建**，与主工程完全隔离：
//   * 本目录自带 settings.gradle.kts ⇒ 它是**独立的 Gradle 构建** ✓
//   * 主工程的 settings.gradle.kts **未** include 本目录 ⇒ 主 `jar` 任务**看不到**它 ✓
//     ⇒ 硬约束①「不得进入发布 jar」由**构建拓扑**保证（不是靠约定 ✓）
// 构建（一条命令）：`.\probe\build.ps1`（= 先 `gradlew jar` 出主 jar，再 `gradlew -p probe jar`）
// 产物：`probe/build/libs/ShadowHunterProbe-1.0.0.jar` ⇒ 由【读数卡】复制进 `run/plugins/` ✓
// ============================================================================
plugins {
    id("java-library")
}

group = "com.shadowHunterProbe"
version = "1.0.0"

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    // 服务端 API（与主工程同一版本 ✓）
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    // 主插件类型面（RoleAPI / RoleComponent / ComponentServices / RoleInstance / RoleManager / VitalsComponent）
    // ★ 只用于**编译**（compileOnly ✓）⇒ 探针 jar 里**不含**主插件任何类 ✓（运行时由服务端 classpath 提供 ✓）
    compileOnly(files("../build/libs/ShadowHunterRolesPlugin-1.0.0.jar"))
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

tasks.jar {
    archiveFileName.set("ShadowHunterProbe-1.0.0.jar")
    // ★ 不把任何依赖打进 jar（compileOnly 已是 compile-only ✓；这里再显式声明一遍意图 ✓）
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
