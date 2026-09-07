package top.maplex.sxitemaction.sxitem

import org.bukkit.Bukkit
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent

/** 向 SX 原生 Random 注册整行变量；生成阶段先保留位置，物品获得 NBT 身份后再填写真实状态。 */
object SXItemLoreVariables {

    /** 内部位置标记必须避开 SX 的 <...> 语法，才能经过其递归表达式和锁定变量而不被吞掉。 */
    val markers: Map<String, String> = linkedMapOf(
        "CustomDurability" to "\uE000SXIA:CustomDurability\uE001",
        "TimeLimit" to "\uE000SXIA:TimeLimit\uE001"
    )

    /** 只恢复仍由本插件占有的注册项，避免卸载时覆盖其他插件后来注册的变量。 */
    private class Registration(
        val map: MutableMap<String, Any>,
        val installed: Map<String, Any>,
        val previous: Map<String, Any?>
    )

    private var registration: Registration? = null

    /** 注册全局变量，无需管理员额外创建 RandomString 文件；SX 本地 Random 不应占用这两个名字。 */
    fun register() {
        val plugin = requireNotNull(Bukkit.getPluginManager().getPlugin("SX-Item"))
        val manager = plugin.javaClass.getMethod("getRandomManager").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val map = manager.javaClass.getMethod("getMap").invoke(manager) as MutableMap<String, Any>
        val current = registration
        if (current != null && current.map === map && current.installed.all { (key, node) -> map[key] === node }) return
        val nodeType = Class.forName("github.saukiya.sxitem.data.random.nodes.SingletonNode", true, plugin.javaClass.classLoader)
        val nodes = markers.mapValues { (_, marker) -> nodeType.getConstructor(String::class.java).newInstance(marker) }
        val previous = markers.keys.associateWith { map[it] }
        map.putAll(nodes)
        registration = Registration(map, nodes, previous)
    }

    /** SX reload 会清空全局 Random；其随后更新玩家物品之前必须重新注册整行变量。 */
    @SubscribeEvent(bind = "github.saukiya.sxitem.event.SXItemReloadEvent")
    fun onSXItemReloadEvent(@Suppress("UNUSED_PARAMETER") event: OptionalEvent) {
        register()
    }

    /** 释放本插件的注册项，避免正常禁用后全局变量继续产生无法处理的位置标记。 */
    fun unregister() {
        val current = registration ?: return
        current.installed.forEach { (key, node) ->
            if (current.map[key] === node) {
                val previous = current.previous[key]
                if (previous == null) current.map.remove(key) else current.map[key] = previous
            }
        }
        registration = null
    }
}
