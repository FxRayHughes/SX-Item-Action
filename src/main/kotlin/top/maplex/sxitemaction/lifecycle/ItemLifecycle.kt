package top.maplex.sxitemaction.lifecycle

import org.bukkit.ChatColor
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.function.warning
import top.maplex.sxitemaction.sxitem.SXItemGateway
import top.maplex.sxitemaction.sxitem.SXItemState

/** 配置负责首次初始化，实际耐久和期限以 SX-Attribute NBT 为准；所有调用在 Bukkit 主线程中进行。 */
object ItemLifecycle {

    /** 持久化键协议：当前/最大耐久使用整数，到期值使用 Unix 毫秒，重启或物品更新不重新计时。 */
    private const val CURRENT = "Durability"
    private const val MAXIMUM = "MaxDurability"
    private const val EXPIRES = "ExpiresAt"
    /** 保存上次生成的完整显示行，修改模板时只替换本模块负责的 Lore。 */
    private const val DURABILITY_LORE = "DurabilityLore"
    private const val EXPIRY_LORE = "ExpiryLore"
    /** 受管 Lore 的模板/已显示文本对应表；update 保留状态后，新的位置标记优先于旧布局。 */
    private const val LORE_BINDINGS = "LoreBindings"
    /** 使用 SX 原生 <s:...> 变量；实际数值由每次检查的 NBT 快照注入，不另造百分号占位符。 */
    private const val DEFAULT_DURABILITY = "&7耐久度: <s:value>/<s:max-value>"
    private const val DEFAULT_EXPIRY = "&7到期时间: <s:time>"
    private val reportedErrors = hashSetOf<String>()

    /** 快照只包含业务数值；invalid 表示配置或持久化失败，禁止把异常物品当作永久有效。 */
    data class State(val current: Int?, val maximum: Int?, val expiresAt: Long?, val invalid: Boolean = false) {
        /** 边界包含到期时刻，避免多获得一个扫描周期的使用权。 */
        fun expired(now: Long = System.currentTimeMillis()): Boolean = expiresAt?.let { now >= it } == true
    }

    /** 首次从原有配置初始化并保存到 NBT；已存在的运行数值不因配置重载或 Lore 修改而重置。 */
    fun inspect(item: ItemStack, player: Player? = null): State? {
        val id = SXItemGateway.identify(item) ?: return null
        return try {
            val config = SXItemGateway.itemConfig(item)
            val saved = SXItemState.read(item)
            // 损坏状态不能用默认配置补齐，否则部分丢失的数据可能导致免费修复或续期。
            decode(saved)
            val state = saved.toMutableMap()
            // 同一次检查共用 SX 上下文，保证 <l:...> 在初始化和显示之间仍保持锁定语义。
            val expressions by lazy { SXItemGateway.itemExpressions(item, player) }
            initialize(item, config, state) { expressions }
            val result = decode(state)
            if (result.maximum != null || result.expiresAt != null) {
                expressions.bind(buildMap {
                    result.current?.let { put("value", it.toString()) }
                    result.maximum?.let { put("max-value", it.toString()) }
                    result.expiresAt?.let { put("time", SXItemGateway.formatDate(it)) }
                })
            }
            if (result.maximum != null) {
                require(item.type.maxDurability > 0) { "自定义耐久只支持具有原版耐久的材质" }
                state[DURABILITY_LORE] = color(expressions.replace(config?.getString("CustomDurability.lore") ?: DEFAULT_DURABILITY).toString())
            }
            if (result.expiresAt != null) {
                state[EXPIRY_LORE] = color(expressions.replace(config?.getString("TimeLimit.lore") ?: DEFAULT_EXPIRY).toString())
            }
            val lore = layoutLore(item.itemMeta?.lore.orEmpty(), saved, state)
            if (state != saved) SXItemState.write(item, state)
            render(item, lore, result)
            result
        } catch (error: Exception) {
            val message = "$id: ${error.cause?.message ?: error.message}"
            if (reportedErrors.size >= 256) reportedErrors.clear()
            if (reportedErrors.add(message)) warning("物品耐久/限时处理失败 $message")
            State(null, null, null, invalid = true)
        }
    }

    /** 扣取 Bukkit 已经过耐久附魔处理的损耗，负数仅供修复使用，不再进行第二次随机减免。 */
    fun damage(item: ItemStack, amount: Int, player: Player? = null): State? {
        val before = inspect(item, player) ?: return null
        if (before.invalid || before.maximum == null || before.current == null) return before
        val state = SXItemState.read(item).toMutableMap()
        state[CURRENT] = (before.current.toLong() - amount.toLong()).coerceIn(0, before.maximum.toLong()).toInt()
        SXItemState.write(item, state)
        return inspect(item, player)
    }

    /** 管理命令设置单件物品的最大耐久并补满；不修改同编号其他物品或生成配置。 */
    fun setDurability(item: ItemStack, maximum: Int, player: Player? = null) {
        require(SXItemGateway.identify(item) != null) { "只能修改 SX-Item 生成的物品" }
        require(maximum > 0 && item.type.maxDurability > 0) { "最大耐久须为正整数，且材质必须具有原版耐久" }
        val state = SXItemState.read(item).toMutableMap()
        state[MAXIMUM] = maximum
        state[CURRENT] = maximum
        SXItemState.write(item, state)
        check(inspect(item, player)?.invalid == false) { "物品配置无效，请检查控制台" }
    }

    /** 从命令执行时开始重新指定有效期，允许管理员显式延长已生成物品的期限。 */
    fun setTimeLimit(item: ItemStack, duration: String, player: Player? = null) {
        require(SXItemGateway.identify(item) != null) { "只能修改 SX-Item 生成的物品" }
        val expiresAt = deadline(duration)
        val state = SXItemState.read(item).toMutableMap()
        state[EXPIRES] = expiresAt
        SXItemState.write(item, state)
        check(inspect(item, player)?.invalid == false) { "物品配置无效，请检查控制台" }
    }

    /** 更新时保留已有耐久/期限，仅合并本模块字段，避免覆盖共享 SX-Attribute 节点中的其他属性。 */
    fun preserve(oldItem: ItemStack, newItem: ItemStack, player: Player? = null) {
        check(inspect(oldItem, player)?.invalid != true) { "旧物品状态无效，已阻止更新" }
        val old = SXItemState.read(oldItem)
        val generated = SXItemState.read(newItem)
        val state = generated.toMutableMap()
        listOf(CURRENT, MAXIMUM, EXPIRES, DURABILITY_LORE, EXPIRY_LORE, LORE_BINDINGS).forEach { key ->
            old[key]?.let { state[key] = it }
        }
        if (state != generated) SXItemState.write(newItem, state)
        check(inspect(newItem, player)?.invalid != true) { "新物品状态无效，已阻止更新" }
    }

    /** 原有配置和兼容 Lore 仅提供未初始化字段；存在 NBT 时不重新读取初始耐久或计算相对期限。 */
    private fun initialize(
        item: ItemStack,
        config: ConfigurationSection?,
        state: MutableMap<String, Any?>,
        expressions: () -> SXItemGateway.ItemExpressions
    ) {
        val durability = config?.getConfigurationSection("CustomDurability")
        val time = config?.getConfigurationSection("TimeLimit")
        require(config?.get("CustomDurability") == null || durability != null) { "CustomDurability 必须是配置节点" }
        require(config?.get("TimeLimit") == null || time != null) { "TimeLimit 必须是配置节点" }
        if (state[MAXIMUM] == null && durability != null) {
            val handler = expressions()
            val maximum = positiveInt(handler.replace(durability.get("max")), "CustomDurability.max")
            handler.bind(mapOf("max-value" to maximum.toString()))
            state[MAXIMUM] = maximum
            state[CURRENT] = durability.get("current")?.let { nonNegativeInt(handler.replace(it), "CustomDurability.current") } ?: maximum
        }
        if (state[EXPIRES] == null && time != null) {
            val duration = time.get("duration")
            val absolute = time.getString("expires-at")
            require((duration != null) xor (absolute != null)) { "TimeLimit 必须且只能配置 duration 或 expires-at" }
            val handler = expressions()
            state[EXPIRES] = if (duration != null) deadline(handler.replace(duration).toString())
            else SXItemGateway.parseDate(handler.replace(absolute!!).toString())
        }
        val lore = item.itemMeta?.lore.orEmpty()
        if (state[MAXIMUM] == null && item.type.maxDurability > 0) {
            lore.firstOrNull { legacyDurability(it) != null }?.let { line ->
                val values = legacyDurability(line)!!
                state[CURRENT] = values.first
                state[MAXIMUM] = values.second
                state[DURABILITY_LORE] = line
            }
        }
        if (state[EXPIRES] == null) {
            lore.firstOrNull { plain(it).matches(Regex("^到期时间\\s*[:：].*")) }?.let { line ->
                state[EXPIRES] = SXItemGateway.parseDate(plain(line).replaceFirst(Regex("^到期时间\\s*[:：]\\s*"), ""))
                state[EXPIRY_LORE] = line
            }
        }
    }

    /** 解析秒或组合单位 1d2h30m；拒绝负数、零、溢出以及部分匹配，避免意外永久有效。 */
    fun deadline(input: String, now: Long = System.currentTimeMillis()): Long {
        val value = input.trim().lowercase()
        val seconds = if (value.matches(Regex("[0-9]+"))) value.toLong() else {
            require(value.matches(Regex("(?:[0-9]+[dhms])+"))) { "时长格式应为秒数或 1d2h30m" }
            Regex("([0-9]+)([dhms])").findAll(value).fold(0L) { total, match ->
                val factor = when (match.groupValues[2]) { "d" -> 86400L; "h" -> 3600L; "m" -> 60L; else -> 1L }
                Math.addExact(total, Math.multiplyExact(match.groupValues[1].toLong(), factor))
            }
        }
        require(seconds > 0) { "有效时长必须大于零" }
        return Math.addExact(now, Math.multiplyExact(seconds, 1000L))
    }

    private fun decode(state: Map<String, Any?>): State {
        val maximum = state[MAXIMUM]?.let { positiveInt(it, MAXIMUM) }
        val current = state[CURRENT]?.let { nonNegativeInt(it, CURRENT) }
        require((maximum == null) == (current == null)) { "耐久持久化数据不完整" }
        require(maximum == null || current!! <= maximum) { "当前耐久不能超过最大耐久" }
        val expiry = state[EXPIRES]?.toString()?.toLongOrNull()
        require(state[EXPIRES] == null || expiry != null) { "到期时间持久化数据损坏" }
        return State(current, maximum, expiry)
    }

    private fun positiveInt(value: Any?, name: String): Int = nonNegativeInt(value, name).also {
        require(it > 0) { "$name 必须大于零" }
    }

    private fun nonNegativeInt(value: Any?, name: String): Int = requireNotNull(value?.toString()?.toIntOrNull()) {
        "$name 必须为整数"
    }.also { require(it >= 0) { "$name 不能为负数" } }

    private fun legacyDurability(line: String): Pair<Int, Int>? {
        val match = Regex("^(?:耐久度|Durability)\\s*[:：]\\s*([0-9]+)\\s*/\\s*([0-9]+)$", RegexOption.IGNORE_CASE)
            .matchEntire(plain(line)) ?: return null
        return nonNegativeInt(match.groupValues[1], CURRENT) to positiveInt(match.groupValues[2], MAXIMUM)
    }

    private fun color(value: String): String = ChatColor.translateAlternateColorCodes('&', value)
    private fun plain(value: String): String = ChatColor.stripColor(color(value)).orEmpty().trim()

    private fun layoutLore(lines: List<String>, previous: Map<String, Any?>, state: MutableMap<String, Any?>): List<String> {
        val layout = LifecycleLoreLayout.resolve(lines, previous[LORE_BINDINGS], buildMap {
            (state[DURABILITY_LORE] as? String)?.let { put("CustomDurability", it) }
            (state[EXPIRY_LORE] as? String)?.let { put("TimeLimit", it) }
        })
        if (layout.bindings.isNotEmpty()) state[LORE_BINDINGS] = layout.bindings
        else state.remove(LORE_BINDINGS)
        val lore = layout.lines.toMutableList()
        listOf(DURABILITY_LORE, EXPIRY_LORE).forEach { key ->
            val name = if (key == DURABILITY_LORE) "CustomDurability" else "TimeLimit"
            // 使用整行变量后由模板控制位置，不再在末尾追加第二份；没有变量的旧配置保持原有行为。
            if (name in layout.placed) return@forEach
            val line = state[key] as? String ?: return@forEach
            val old = previous[key] as? String
            // 更新会重建初始 Lore；找不到上次显示行时替换明确识别的兼容行，不以该行覆盖 NBT 数值。
            val exact = lore.indexOfFirst { it == old || it == line }
            val index = if (exact >= 0) exact else lore.indexOfFirst {
                if (key == DURABILITY_LORE) legacyDurability(it) != null
                else plain(it).matches(Regex("^到期时间\\s*[:：].*"))
            }
            if (index >= 0) lore[index] = line else lore.add(line)
        }
        return lore
    }

    private fun render(item: ItemStack, lore: List<String>, values: State) {
        val meta = item.itemMeta ?: return
        if (lore != meta.lore.orEmpty()) {
            meta.lore = lore
            item.itemMeta = meta
        }
        if (values.maximum != null && values.current != null && item.type.maxDurability > 0) {
            // 保留至少一个原版耐久点；破坏由自定义耐久零值处理，不让取整使原版提前销毁。
            val vanillaMax = item.type.maxDurability.toInt()
            val damage = ((values.maximum - values.current).toLong() * vanillaMax / values.maximum)
                .coerceIn(0, (vanillaMax - 1).toLong()).toShort()
            @Suppress("DEPRECATION")
            if (item.durability != damage) item.durability = damage
        }
    }
}
