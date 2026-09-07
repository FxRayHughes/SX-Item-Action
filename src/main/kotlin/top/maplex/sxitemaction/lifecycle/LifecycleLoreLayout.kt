package top.maplex.sxitemaction.lifecycle

import top.maplex.sxitemaction.sxitem.SXItemLoreVariables

/** 只替换已标记的显示片段，不重新生成整份 Lore，保留 SX 随机结果及其他插件追加的文字。 */
object LifecycleLoreLayout {

    /** 保存模板与上次完整显示行，后续按出现次数匹配；不把易受插行影响的槽位序号当作身份。 */
    data class Result(
        val lines: List<String>,
        val bindings: List<Map<String, String>>,
        val placed: Set<String>
    )

    /** 支持指定顺序、重复引用和同一行的多个变量；缺少对应功能时移除该变量，保留其余文字。 */
    fun resolve(lines: List<String>, previous: Any?, values: Map<String, String>): Result {
        val markers = SXItemLoreVariables.markers
        val bindings = (previous as? List<*>)?.mapNotNull { raw ->
            val entry = raw as? Map<*, *> ?: return@mapNotNull null
            val template = entry["template"] as? String ?: return@mapNotNull null
            val rendered = entry["rendered"] as? String ?: return@mapNotNull null
            template to rendered
        }.orEmpty().toMutableList()
        val nextBindings = mutableListOf<Map<String, String>>()
        val placed = mutableSetOf<String>()
        val result = mutableListOf<String>()
        lines.forEach { line ->
            val hasMarker = markers.values.any { line.contains(it) }
            val match = if (hasMarker) -1 else bindings.indexOfFirst { it.second == line }
            val template = when {
                hasMarker -> line
                match >= 0 -> bindings.removeAt(match).first
                else -> { result.add(line); return@forEach }
            }
            var rendered = template
            markers.forEach { (name, marker) ->
                if (template.contains(marker)) {
                    placed.add(name)
                    rendered = rendered.replace(marker, values[name].orEmpty())
                }
            }
            // 整行只含未启用功能时删除该行，不让内部标记出现在物品说明中。
            if (rendered.isNotEmpty()) {
                result.add(rendered)
                nextBindings.add(mapOf("template" to template, "rendered" to rendered))
            }
        }
        return Result(result, nextBindings, placed)
    }
}
