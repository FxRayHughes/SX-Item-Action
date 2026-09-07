package top.maplex.sxitemaction.listener

import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.PrepareAnvilEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.event.player.PlayerItemMendEvent
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.warning
import top.maplex.sxitemaction.lifecycle.ItemLifecycle

/** 将最终原版损耗转入自定义耐久，同时封锁会只修复原版显示条的修复入口。 */
object CustomDurabilityListener {

    /** HIGHEST 接收其他插件调整后的损耗；取消事件后自行处理破坏，避免原版再次扣减。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerItemDamageEvent(event: PlayerItemDamageEvent) {
        if (event.isCancelled) return
        if (!ItemLifecycleListener.allow(event.player, event.item, event)) return
        val state = ItemLifecycle.inspect(event.item, event.player) ?: return
        if (state.maximum == null || event.damage <= 0) return
        event.isCancelled = true
        runCatching {
            val after = ItemLifecycle.damage(event.item, event.damage, event.player) ?: return
            if (after.current == 0) {
                // 广播单件快照，使 on-item-break 能读取物品身份；只销毁本次受损的一件。
                val broken = event.item.clone().also { it.amount = 1 }
                event.item.amount = (event.item.amount - 1).coerceAtLeast(0)
                if (event.item.amount > 0) ItemLifecycle.setDurability(event.item, after.maximum!!, event.player)
                Bukkit.getPluginManager().callEvent(PlayerItemBreakEvent(event.player, broken))
                event.player.playSound(event.player.location, Sound.ENTITY_ITEM_BREAK, 1f, 1f)
            }
        }.onFailure { warning("自定义耐久扣减失败: ${it.cause?.message ?: it.message}") }
    }

    /** 与参考插件一致：经验修补不影响自定义耐久，让经验继续走服务端的取消处理。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerItemMendEvent(event: PlayerItemMendEvent) {
        if (!ItemLifecycleListener.allow(event.player, event.item, event)) return
        if (ItemLifecycle.inspect(event.item, event.player)?.maximum != null) event.isCancelled = true
    }

    /** 铁砧无法正确合并自定义数值，禁用涉及受管物品的输出，修复统一走管理命令/API。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPrepareAnvilEvent(event: PrepareAnvilEvent) {
        if ((0..1).any { managed(event.inventory.getItem(it), event.view.player as? Player) }) event.result = null
    }

    /** 合成修复会清除持久化数据，包含受管物品的合成一律不产生输出。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun onPrepareItemCraftEvent(event: PrepareItemCraftEvent) {
        if (event.inventory.matrix.any { managed(it, event.view.player as? Player) }) event.inventory.result = null
    }

    /** 输出槽为铁砧/砂轮的 2、工作台的 0；再次守住取出边界，覆盖结果已缓存的情形。 */
    @SubscribeEvent(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        val top = event.view.topInventory
        val inputs = when (top.type.name) {
            "ANVIL", "GRINDSTONE" -> if (event.rawSlot == 2) listOf(0, 1) else return
            "CRAFTING", "WORKBENCH" -> if (event.rawSlot == 0) (1 until top.size).toList() else return
            else -> return
        }
        if (inputs.any { managed(top.getItem(it), event.whoClicked as? Player) }) event.isCancelled = true
    }

    private fun managed(item: ItemStack?, player: Player?): Boolean = item?.let { ItemLifecycle.inspect(it, player) }?.let {
        it.invalid || it.maximum != null || it.expiresAt != null
    } == true
}
