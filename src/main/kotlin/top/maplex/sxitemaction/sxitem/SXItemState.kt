package top.maplex.sxitemaction.sxitem

import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack
import java.lang.reflect.Method

/** 使用 SX-Item 自己的 NBT/PDC 适配层保存状态，避免另引一套 NMS 映射而丢失现代组件。 */
object SXItemState {

    /** 用户约定的共享属性根键；只维护其中的生命周期字段，不能覆盖其他属性数据。 */
    const val ROOT = "SX-Attribute"

    /** 反射绑定公开接口而非实现类，兼容 SX-Item 内部的私有 PDC Wrapper。 */
    private class Access(
        val utility: Any,
        val wrap: Method,
        val get: Method,
        val set: Method,
        val save: Method
    )

    private val access: Access by lazy {
        val loader = requireNotNull(Bukkit.getPluginManager().getPlugin("SX-Item")).javaClass.classLoader
        val utility = Class.forName("github.saukiya.tools.nms.NbtUtil", true, loader)
        val compound = Class.forName("github.saukiya.tools.base.ICompound", true, loader)
        val wrapper = Class.forName("github.saukiya.tools.nms.NbtUtil\$ItemWrapper", true, loader)
        Access(
            requireNotNull(utility.getMethod("getInst").invoke(null)),
            utility.getMethod("getItemTagWrapper", ItemStack::class.java),
            compound.getMethod("get", String::class.java),
            compound.getMethod("set", String::class.java, Any::class.java),
            wrapper.getMethod("save")
        )
    }

    /** 只读状态；调用方必须先识别 SX-Item 身份，损坏的数据由业务层拒绝使用。 */
    fun read(item: ItemStack): Map<String, Any?> {
        val api = access
        val wrapper = api.wrap.invoke(api.utility, item)
        val raw = api.get.invoke(wrapper, ROOT) ?: return emptyMap()
        val value = requireNotNull(raw as? Map<*, *>) { "$ROOT 必须为复合数据，不能重置损坏状态" }
        return value.entries.associate { it.key.toString() to it.value }
    }

    /** 提交包含其他属性的完整节点快照；保存后重新获取 ItemMeta，避免旧元数据覆盖 NBT/PDC。 */
    fun write(item: ItemStack, state: Map<String, Any?>) {
        val api = access
        val wrapper = api.wrap.invoke(api.utility, item)
        api.set.invoke(wrapper, ROOT, HashMap(state))
        api.save.invoke(wrapper)
        // SX-Item 某些降级实现会吞掉保存异常；读回失败时拒绝继续，避免无声刷新期限。
        check(equivalent(read(item), state)) { "SX-Item 无法持久化物品耐久/限时状态" }
    }

    /** 共享属性可能包含 NBT 数组；反序列化会创建新数组，必须按内容校验而非引用相等。 */
    private fun equivalent(first: Any?, second: Any?): Boolean = when {
        first is Map<*, *> && second is Map<*, *> ->
            first.keys == second.keys && first.all { (key, value) -> equivalent(value, second[key]) }
        first is List<*> && second is List<*> ->
            first.size == second.size && first.indices.all { equivalent(first[it], second[it]) }
        else -> java.util.Objects.deepEquals(first, second)
    }
}
