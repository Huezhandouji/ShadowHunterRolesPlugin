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

//自动复制到服务端文件夹
tasks.register<Copy>("copyPluginJar") {
    // 只有当属性存在时才注册复制动作
    val dest = project.findProperty("pluginCopyPath") as String? ?: return@register
    from(layout.buildDirectory.file("libs/${project.name}-${project.version}.jar"))
    into(dest)
}

tasks.named("build"){
    finalizedBy("copyPluginJar")
}
