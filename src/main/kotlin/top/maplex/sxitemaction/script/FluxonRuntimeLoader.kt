package top.maplex.sxitemaction.script

import taboolib.common.LifeCycle
import taboolib.common.PrimitiveSettings
import taboolib.common.env.DependencyScope
import taboolib.common.env.JarRelocation
import taboolib.common.env.RuntimeEnvDependency
import taboolib.common.platform.Awake
import java.io.File
import java.util.Base64

/**
 * 在 TabooLib 平台初始化前下载并隔离 SX-Item-Action 私有的 Fluxon 运行时。
 *
 * 下载流程沿用 Monoceros 的双仓库策略：Fluxon 仓库只获取主包，Maven Central
 * 负责解析传递依赖。该类不能直接引用 Fluxon 类型，否则 JVM 可能在下载完成前解析类签名。
 */
object FluxonRuntimeLoader {

    private const val FLUXON_VERSION = "1.7.2"
    private const val FLUXON_REPOSITORY = "https://repo.tabooproject.org/repository/releases"
    private const val MAVEN_CENTRAL_REPOSITORY = "https://repo.maven.apache.org/maven2"
    private const val MAX_ATTEMPTS = 3
    private const val RETRY_DELAY_MILLIS = 2_000L

    @Volatile
    private var ready = false

    @Volatile
    private var failure: Throwable? = null

    /**
     * 在 CONST 生命周期准备运行库，确保后续平台类开始执行前 Fluxon 已进入插件类加载器。
     */
    @Awake(LifeCycle.CONST)
    fun download() {
        if (hasCompleteRuntime()) {
            ready = true
            return
        }
        var lastFailure: Throwable? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                loadRuntime()
                if (hasCompleteRuntime()) {
                    ready = true
                    failure = null
                    println("[SX-Item-Action] Fluxon $FLUXON_VERSION 运行时加载完成")
                    return
                }
                lastFailure = IllegalStateException(
                    "下载结束后仍缺少重定向的运行类：${missingRuntimeClasses().joinToString()}"
                )
            } catch (throwable: Throwable) {
                lastFailure = throwable
            }
            if (attempt + 1 < MAX_ATTEMPTS) {
                Thread.sleep(RETRY_DELAY_MILLIS)
            }
        }
        failure = lastFailure
        System.err.println(
            "[SX-Item-Action] Fluxon $FLUXON_VERSION 运行时加载失败：${lastFailure?.message ?: "未知错误"}"
        )
    }

    /** 返回私有 Fluxon 运行时及关键传递依赖是否均已注入。 */
    fun isReady(): Boolean {
        return ready
    }

    /**
     * 在脚本执行边界阻止缺失运行库的调用，避免把 NoClassDefFoundError 泄漏到 Bukkit 事件线程。
     */
    fun ensureReady() {
        if (!ready) {
            throw IllegalStateException("Fluxon 运行时不可用：${failure?.message ?: "初始化未完成"}", failure)
        }
    }

    /** 返回启动期下载失败原因，供插件入口输出一次可诊断的告警。 */
    fun failureMessage(): String? {
        return failure?.message
    }

    private fun loadRuntime() {
        val coordinate = "${fluxonGroupId()}:core:$FLUXON_VERSION"
        val scopes = listOf(DependencyScope.RUNTIME, DependencyScope.COMPILE)
        val relocations = buildRelocations()
        val libraryDirectory = File(PrimitiveSettings.FILE_LIBS)

        // Fluxon 仓库只下载主包，避免它承担 Maven Central 传递树解析而产生不完整闭包。
        RuntimeEnvDependency().loadDependency(
            coordinate,
            libraryDirectory,
            relocations,
            FLUXON_REPOSITORY,
            true,
            true,
            false,
            scopes,
            false
        )
        // 第二次解析允许 Central 补齐 ASM、JLine、Jansi、JNA 等实际运行依赖。
        RuntimeEnvDependency().loadDependency(
            coordinate,
            libraryDirectory,
            relocations,
            MAVEN_CENTRAL_REPOSITORY,
            true,
            true,
            true,
            scopes,
            false
        )
    }

    private fun buildRelocations(): List<JarRelocation> {
        val relocatedRoot = relocatedFluxonPackage()
        val libraryRoot = "$relocatedRoot.libs."
        return listOf(
            JarRelocation(fluxonGroupId(), relocatedRoot),
            JarRelocation("javax.annotation.", "${libraryRoot}javax.annotation."),
            JarRelocation("org.checkerframework.", "${libraryRoot}org.checkerframework."),
            JarRelocation("com.google.errorprone.", "${libraryRoot}com.google.errorprone."),
            JarRelocation("com.google.j2objc.", "${libraryRoot}com.google.j2objc."),
            JarRelocation("com.google.thirdparty.", "${libraryRoot}com.google.thirdparty."),
            JarRelocation("org.objectweb.asm.", "${libraryRoot}org.objectweb.asm."),
            JarRelocation("org.jetbrains.annotations.", "${libraryRoot}org.jetbrains.annotations."),
            JarRelocation("org.intellij.lang.annotations.", "${libraryRoot}org.intellij.lang.annotations."),
            JarRelocation("org.jline.", "${libraryRoot}org.jline."),
            JarRelocation("org.fusesource.jansi.", "${libraryRoot}org.fusesource.jansi."),
            JarRelocation("com.sun.jna.", "${libraryRoot}com.sun.jna.")
        )
    }

    private fun hasCompleteRuntime(): Boolean {
        return missingRuntimeClasses().isEmpty()
    }

    private fun missingRuntimeClasses(): List<String> {
        val relocatedRoot = relocatedFluxonPackage()
        return listOf(
            "$relocatedRoot.runtime.FluxonRuntime",
            "$relocatedRoot.libs.org.objectweb.asm.Type",
            "$relocatedRoot.libs.org.jline.reader.LineReader",
            "$relocatedRoot.libs.com.sun.jna.Native"
        ).filterNot(::isClassAvailable)
    }

    private fun isClassAvailable(className: String): Boolean {
        return runCatching { Class.forName(className, false, javaClass.classLoader) }.isSuccess
    }

    private fun fluxonGroupId(): String {
        // 构建插件会重写待 relocate 的字符串；Base64 保证 Maven 坐标保持原始 groupId。
        return String(Base64.getDecoder().decode("b3JnLnRhYm9vcHJvamVjdC5mbHV4b24="), Charsets.UTF_8)
    }

    private fun relocatedFluxonPackage(): String {
        // 分段生成目标包可避免构建器把已经重定向的目标常量再次处理。
        return listOf("top", "maplex", "sxitemaction", "engine", "fluxon").joinToString(".")
    }
}
