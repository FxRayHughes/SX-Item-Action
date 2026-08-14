package top.maplex.sxitemaction.sxitem

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method

/**
 * SX-Item 4.x 的窄边界网关。
 *
 * 附属不把 SX-Item 打入产物，反射仅用于跨插件类加载器访问其公开管理器 API；
 * 所有反射细节集中于此，业务层仍使用 Bukkit 强类型对象。
 */
object SXItemGateway {

    private data class Accessor(
        val itemManager: Any,
        val getItemKey: Method,
        val getGenerator: Method,
        val getConfig: Method
    )

    @Volatile
    private var accessor: Accessor? = null

    /** 仅当物品带有 SX-Item 身份键时返回编号，杜绝名称或 lore 猜测造成的误兼容。 */
    fun identify(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR) return null
        val api = resolve() ?: return null
        return runCatching { api.getItemKey.invoke(api.itemManager, item) as? String }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** 返回身份编号对应生成器的原始配置，动作因此可以直接写在 SX-Item 物品节点内。 */
    fun itemConfig(item: ItemStack): ConfigurationSection? {
        val api = resolve() ?: return null
        val generator = runCatching { api.getGenerator.invoke(api.itemManager, item) }.getOrNull() ?: return null
        return runCatching { api.getConfig.invoke(generator) as? ConfigurationSection }.getOrNull()
    }

    /** 使用 SX-Item 原生 ExpressionHandler 解析字符串，保留随机变量及 PlaceholderAPI 行为。 */
    fun resolveExpression(player: Player, value: String): Any? {
        val plugin = Bukkit.getPluginManager().getPlugin("SX-Item") ?: return value
        return runCatching {
            val type = Class.forName("github.saukiya.sxitem.data.expression.ExpressionHandler", true, plugin.javaClass.classLoader)
            val handler = type.getConstructor(Player::class.java).newInstance(player)
            type.getMethod("replace", Any::class.java).invoke(handler, value)
        }.getOrDefault(value)
    }

    private fun resolve(): Accessor? {
        accessor?.let { return it }
        val plugin = Bukkit.getPluginManager().getPlugin("SX-Item") ?: return null
        if (!plugin.isEnabled) return null
        return synchronized(this) {
            accessor ?: runCatching {
                val pluginType = plugin.javaClass
                val manager = pluginType.getMethod("getItemManager").invoke(null)
                val managerType = manager.javaClass
                val generatorType = Class.forName("github.saukiya.sxitem.data.item.IGenerator", true, pluginType.classLoader)
                Accessor(
                    manager,
                    managerType.getMethod("getItemKey", ItemStack::class.java),
                    managerType.getMethod("getGenerator", ItemStack::class.java),
                    generatorType.getMethod("getConfig")
                )
            }.getOrNull()?.also { accessor = it }
        }
    }
}
