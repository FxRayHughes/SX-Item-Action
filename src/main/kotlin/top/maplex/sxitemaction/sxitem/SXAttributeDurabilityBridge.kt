package top.maplex.sxitemaction.sxitem

import org.bukkit.event.Event
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.RegisteredListener
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.function.submit
import top.maplex.sxitemaction.lifecycle.ItemLifecycle

/** 旧 SX-Attribute 在 LOW 按 Lore 扣耐久；仅过滤其对本附属受管物品的重复处理，保留其他插件的取消权。 */
object SXAttributeDurabilityBridge {

    private val installed = linkedMapOf<RegisteredListener, RegisteredListener>()

    /** 使用 Bukkit 公开注册接口包装已知旧监听器，不修改 SX-Attribute 配置或全局关闭其耐久功能。 */
    fun install() {
        val handlers = PlayerItemDamageEvent.getHandlerList()
        // SX-Attribute reload 可能撤销旧监听器，及时丢弃失效包装器以免保留旧插件对象。
        installed.keys.retainAll(handlers.registeredListeners.toSet())
        handlers.registeredListeners.filter { listener ->
            listener !in installed && listener.plugin.name == "SX-Attribute" &&
                listener.listener.javaClass.name == "github.saukiya.sxattribute.data.condition.sub.Durability"
        }.forEach { original ->
            val bridge = object : RegisteredListener(
                original.listener, EventExecutor { _, event -> original.callEvent(event) },
                original.priority, original.plugin, original.isIgnoringCancelled
            ) {
                override fun callEvent(event: Event) {
                    val damage = event as? PlayerItemDamageEvent
                    // 不解除已有取消，也不提前扣减；最终损耗仍由本插件 HIGHEST 监听器结算。
                    if (damage != null && !damage.isCancelled) {
                        val state = ItemLifecycle.inspect(damage.item, damage.player)
                        if (state != null && (state.invalid || state.maximum != null)) return
                    }
                    original.callEvent(event)
                }
            }
            handlers.unregister(original)
            handlers.register(bridge)
            installed[bridge] = original
        }
    }

    /** 兼容 SX-Attribute 晚于本插件启用；等启用过程注册完监听器后再安装。 */
    @SubscribeEvent
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin.name == "SX-Attribute") submit { install() }
    }

    /** 旧模块重载后可能重新注册处理器，延迟到本次重载完成再恢复单件物品的分工。 */
    @SubscribeEvent(bind = "github.saukiya.sxattribute.event.SXReloadEvent")
    fun onAttributeReload(@Suppress("UNUSED_PARAMETER") event: OptionalEvent) {
        submit { install() }
    }

    /** 只还原仍注册的包装器，不能重新启用已被 SX-Attribute 卸载或替换的监听器。 */
    fun uninstall() {
        val handlers = PlayerItemDamageEvent.getHandlerList()
        val active = handlers.registeredListeners.toSet()
        installed.forEach { (bridge, original) ->
            if (bridge in active) {
                handlers.unregister(bridge)
                if (original.plugin.isEnabled) handlers.register(original)
            }
        }
        installed.clear()
    }
}
