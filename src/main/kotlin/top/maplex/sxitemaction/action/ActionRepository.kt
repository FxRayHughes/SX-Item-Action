package top.maplex.sxitemaction.action

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import taboolib.common.platform.function.getDataFolder
import taboolib.common.platform.function.releaseResourceFile
import taboolib.common.platform.function.warning
import top.maplex.sxitemaction.sxitem.SXItemGateway
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 动作配置与预制脚本仓库。
 *
 * 脚本文件按 `scripts/<engine>/<name>.<扩展名>` 管理，效果类似 SX-Item Random 目录：
 * 物品配置只保存 import 名与变量，同一脚本可被任意 SX-Item 物品复用。
 */
object ActionRepository {

    private val templates = ConcurrentHashMap<String, String>()
    private val diagnostics = mutableListOf<String>()
    val templateCount: Int get() = templates.size
    val diagnosticMessages: List<String> get() = synchronized(diagnostics) { diagnostics.toList() }

    fun reload() {
        val root = File(getDataFolder(), "scripts")
        if (!root.exists()) {
            releaseResourceFile("scripts/kether/example.ks", replace = false)
            releaseResourceFile("scripts/fluxon/example.fs", replace = false)
        }
        templates.clear()
        synchronized(diagnostics) { diagnostics.clear() }
        root.walkTopDown().filter { it.isFile }.forEach { file ->
            val engine = file.parentFile?.name?.lowercase()
            if (engine !in setOf("kether", "fluxon")) {
                recordDiagnostic("忽略未知脚本目录: ${file.relativeTo(root).invariantSeparatorsPath}")
                return@forEach
            }
            val expectedExtension = if (engine == "kether") "ks" else "fs"
            if (!file.extension.equals(expectedExtension, ignoreCase = true)) {
                recordDiagnostic("忽略扩展名不匹配的脚本: ${file.relativeTo(root).invariantSeparatorsPath}")
                return@forEach
            }
            val relative = file.relativeTo(root).invariantSeparatorsPath.substringBeforeLast('.')
            val key = relative.lowercase()
            val source = runCatching { file.readText(Charsets.UTF_8) }.getOrElse {
                recordDiagnostic("读取脚本失败 $relative: ${it.message}")
                return@forEach
            }
            if (source.isBlank()) {
                recordDiagnostic("脚本内容为空: $relative")
                return@forEach
            }
            if (templates.putIfAbsent(key, source) != null) {
                recordDiagnostic("脚本键重复，保留先加载项: $key")
            }
        }
    }

    /** 返回当前已加载模板键，命令输出排序后可稳定对比重载结果。 */
    fun templateKeys(): List<String> = templates.keys.sorted()

    /** 从物品生成器配置读取指定入口，可同时返回多个定义并按声明顺序执行。 */
    fun definitions(itemConfig: ConfigurationSection, trigger: ActionTrigger, player: Player): List<ActionDefinition> {
        val actionRoot = itemConfig.getConfigurationSection("Action")
            ?: itemConfig.getConfigurationSection("Actions")
            ?: return emptyList()
        val key = trigger.aliases.firstOrNull { actionRoot.contains(it) } ?: return emptyList()
        val raw = actionRoot.get(key)
        val nodes = when {
            raw is List<*> -> raw
            else -> listOf(raw)
        }
        return nodes.mapNotNull { parseNode(it, player, "Action.$key") }
    }

    private fun parseNode(raw: Any?, player: Player, path: String): ActionDefinition? {
        if (raw is String) {
            return ActionDefinition(ScriptEngine.KETHER, raw, false, emptyMap())
        }
        val node = when (raw) {
            is ConfigurationSection -> raw.getValues(false)
            is Map<*, *> -> raw.entries.associate { it.key.toString() to it.value }
            else -> return null
        }
        val engine = runCatching {
            ScriptEngine.valueOf(node["engine"]?.toString()?.uppercase() ?: "KETHER")
        }.getOrElse {
            warning("$path 使用了未知脚本引擎: ${node["engine"]}")
            recordDiagnostic("$path 使用了未知脚本引擎: ${node["engine"]}")
            return null
        }
        val imported = node["import"]?.toString()
        val source = if (imported != null) {
            val templateKey = "${engine.name.lowercase()}/${imported.substringBeforeLast('.')}".lowercase()
            templates[templateKey] ?: run {
                warning("$path 找不到预制脚本: $templateKey")
                recordDiagnostic("$path 找不到预制脚本: $templateKey")
                return null
            }
        } else {
            when (val script = node["script"]) {
                is List<*> -> script.joinToString("\n") { it.toString() }
                null -> return null
                else -> script.toString()
            }
        }
        val variableNode = node["variables"]
        val variables = when (variableNode) {
            is ConfigurationSection -> variableNode.getValues(false)
            is Map<*, *> -> variableNode.entries.associate { it.key.toString() to it.value }
            else -> emptyMap()
        }.mapValues { (_, value) -> normalize(value, player) }
        return ActionDefinition(engine, source, node["cancel"].toString().toBoolean(), variables)
    }

    /** 递归归一化 YAML 值，并让字符串先经过 SX-Item 表达式系统再进入脚本引擎。 */
    private fun normalize(value: Any?, player: Player): Any? = when (value) {
        is ConfigurationSection -> value.getValues(false).mapValues { normalize(it.value, player) }
        is Map<*, *> -> value.entries.associate { it.key.toString() to normalize(it.value, player) }
        is List<*> -> value.map { normalize(it, player) }
        is String -> SXItemGateway.resolveExpression(player, value)
        else -> value
    }

    private fun recordDiagnostic(message: String) {
        synchronized(diagnostics) {
            if (message !in diagnostics) diagnostics += message
        }
    }
}
