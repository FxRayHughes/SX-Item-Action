package top.maplex.sxitemaction.sxitem

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method
import java.text.DateFormat
import java.text.ParsePosition
import java.util.Date

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

    /** 单次物品解析上下文；复用 SX 的本地 Random、锁定变量、嵌套表达式和玩家占位符行为。 */
    class ItemExpressions internal constructor(
        private val handler: Any,
        private val replaceMethod: Method,
        private val otherVariables: MutableMap<String, String>
    ) {
        /** 使用 SX 的对象替换入口，保留 [int]/[long] 等原生类型转换，不自行实现占位符替换。 */
        fun replace(value: Any?): Any? = replaceMethod.invoke(handler, value)

        /** 将当前 NBT 状态注入 SX 的 otherMap；其优先级高于同名 Random，避免显示随机数冒充实际状态。 */
        fun bind(values: Map<String, String>) {
            otherVariables.putAll(values)
        }
    }

    /** 创建一次检查共用的 SX 解析器；不使用全局单例，避免玩家之间串变量或共享锁定随机结果。 */
    fun itemExpressions(item: ItemStack, player: Player? = null): ItemExpressions {
        val plugin = requireNotNull(Bukkit.getPluginManager().getPlugin("SX-Item"))
        val loader = plugin.javaClass.classLoader
        val type = Class.forName("github.saukiya.sxitem.data.expression.ExpressionHandler", true, loader)
        val api = requireNotNull(resolve()) { "SX-Item 物品管理器不可用" }
        val generator = api.getGenerator.invoke(api.itemManager, item)
        // 默认生成器公开 randomMap；导入等生成器没有此接口，沿用 SX 的全局 Random 查找。
        val randomGetter = generator?.javaClass?.methods?.firstOrNull { it.name == "getRandomMap" && it.parameterCount == 0 }
        val localRandom = randomGetter?.invoke(generator)
        val handler = type.getConstructor(Player::class.java, Map::class.java).newInstance(player, localRandom)
        @Suppress("UNCHECKED_CAST")
        val variables = type.getMethod("getOtherMap").invoke(handler) as MutableMap<String, String>
        return ItemExpressions(handler, type.getMethod("replace", Any::class.java), variables)
    }

    /** 直接使用 SX 的日期格式器格式化已保存的截止时间，不能再次执行 <t:时长> 来生成新期限。 */
    fun formatDate(timestamp: Long): String = dateFormatter().format(Date(timestamp))

    /** 按 SX 的日期格式及时区读取固定日期；克隆后严格解析，不改变 SX 自己的线程局部格式器。 */
    fun parseDate(value: String): Long {
        val text = value.trim()
        val formatter = dateFormatter().apply { isLenient = false }
        val position = ParsePosition(0)
        val date = formatter.parse(text, position)
        require(date != null && position.index == text.length) { "日期不符合 SX-Item TimeFormat: $text" }
        return date.time
    }

    private fun dateFormatter(): DateFormat {
        val plugin = requireNotNull(Bukkit.getPluginManager().getPlugin("SX-Item"))
        val local = plugin.javaClass.getMethod("getSdf").invoke(null) as ThreadLocal<*>
        return (local.get() as DateFormat).clone() as DateFormat
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
