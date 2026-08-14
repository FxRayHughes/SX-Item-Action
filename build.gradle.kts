import io.izzel.taboolib.gradle.*
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    java
    id("io.izzel.taboolib") version "2.0.36"
    kotlin("jvm") version "2.3.0"
}

taboolib {
    env {
        // 动作附属只依赖平台、脚本与命令模块；避免无关 NMS 模块扩大版本耦合面。
        // CommandHelper 6.3 的玩家会话生命周期依赖 BukkitUtil，必须显式安装以避免加入事件缺类。
        install(Basic, Bukkit, BukkitUtil, Kether, CommandHelper)
        // Fluxon 官方仓库的 POM 在部分 Aether 版本上解析不稳定，沿用 Monoceros 的 Legacy 下载策略。
        enableLegacyDependencyResolver = true
    }
    version {
        // 6.3.0 平台层包含 1.21.11 映射；旧 6.2.4 会在该版本禁用命令与事件注册。
        taboolib = "6.3.0-932e79c"
        coroutines = "1.8.1"
    }
    // 编译产物中的 Fluxon 类型引用必须与运行时下载重定向目标完全一致，避免污染或复用其他插件的运行时。
    relocate("org.tabooproject.fluxon", "top.maplex.sxitemaction.engine.fluxon")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://repo.tabooproject.org/repository/releases")
    }
    maven {
        // 枫溪的仓库
        url = uri("https://nexus.maplex.top/repository/maven-public/")
        isAllowInsecureProtocol = true
    }
    maven {
        url = uri("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    }
}

dependencies {
    // 使用最低支持线编译 Bukkit 事件 API，避免产物绑定现代服务端实现类。
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly(kotlin("stdlib"))

    // Fluxon 作为附属内置脚本运行时，版本与官方 releases 仓库当前稳定版保持一致。
    compileOnly("org.tabooproject.fluxon:core:1.7.2")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.processResources {
    // plugin.yml 必须写入稳定版本号，否则 Bukkit 会把字面量 ${version} 当作插件版本展示。
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}


kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("1.8")
    }
}

configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
