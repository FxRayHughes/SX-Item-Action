package top.maplex.sxitemaction.listener

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemBreakEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.Schedule
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import top.maplex.sxitemaction.action.ActionContext
import top.maplex.sxitemaction.action.ActionRepository
import top.maplex.sxitemaction.action.ActionTrigger
import top.maplex.sxitemaction.script.ScriptExecutor
import top.maplex.sxitemaction.sxitem.SXItemGateway
import java.util.concurrent.Executor

/**
 * 将 Bukkit 生命周期事件适配成统一物品动作。
 *
 * 每个处理器都使用事件实际携带的物品引用，尤其不把换手后的主副手物品混淆；
 * 这修正了参考实现中依赖当前背包状态而可能选错物品的风险。
 */
object ItemActionListener {

    /** Future 后续动作必须回到服务端主线程，避免 Kether 异步完成后直接访问 Bukkit 对象。 */
    private val mainThreadExecutor = Executor { task -> submit { task.run() } }

    @SubscribeEvent
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != null && event.hand != EquipmentSlot.HAND) return
        val trigger = when (event.action) {
            Action.LEFT_CLICK_AIR, Action.LEFT_CLICK_BLOCK -> ActionTrigger.LEFT_CLICK
            Action.RIGHT_CLICK_AIR, Action.RIGHT_CLICK_BLOCK -> ActionTrigger.RIGHT_CLICK
            else -> return
        }
        dispatch(event.player, event.item, event, trigger)
    }

    @SubscribeEvent
    fun onInteractEntity(event: PlayerInteractEntityEvent) {
        if (event.hand != EquipmentSlot.HAND) return
        dispatch(event.player, event.player.inventory.itemInMainHand, event, ActionTrigger.RIGHT_CLICK_ENTITY)
    }

    @SubscribeEvent
    fun onAttack(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        dispatch(player, player.inventory.itemInMainHand, event, ActionTrigger.ATTACK)
    }

    @SubscribeEvent
    fun onBlockBreak(event: BlockBreakEvent) =
        dispatch(event.player, event.player.inventory.itemInMainHand, event, ActionTrigger.BLOCK_BREAK)

    @SubscribeEvent
    fun onBlockPlace(event: BlockPlaceEvent) =
        dispatch(event.player, event.itemInHand, event, ActionTrigger.BLOCK_PLACE)

    @SubscribeEvent
    fun onItemBreak(event: PlayerItemBreakEvent) =
        dispatch(event.player, event.brokenItem, event, ActionTrigger.ITEM_BREAK)

    @SubscribeEvent
    fun onConsume(event: PlayerItemConsumeEvent) =
        dispatch(event.player, event.item, event, ActionTrigger.CONSUME)

    @SubscribeEvent
    fun onDrop(event: PlayerDropItemEvent) =
        dispatch(event.player, event.itemDrop.itemStack, event, ActionTrigger.DROP)

    @SubscribeEvent
    fun onPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        dispatch(player, event.item.itemStack, event, ActionTrigger.PICKUP)
    }

    @SubscribeEvent
    fun onSwap(event: PlayerSwapHandItemsEvent) {
        event.mainHandItem?.let { dispatch(event.player, it, event, ActionTrigger.SWAP_TO_MAINHAND) }
        event.offHandItem?.let { dispatch(event.player, it, event, ActionTrigger.SWAP_TO_OFFHAND) }
    }

    /**
     * 定时动作每秒对同一物品编号只执行一次，避免玩家堆叠多个同 ID 物品时意外倍增效果。
     * 扫描发生在主线程，因为脚本会直接接触 Bukkit 对象。
     */
    @Schedule(period = 20)
    fun onTimer() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val visited = hashSetOf<String>()
            player.inventory.contents.filterNotNull().forEach { item ->
                val itemId = SXItemGateway.identify(item) ?: return@forEach
                if (visited.add(itemId)) {
                    dispatch(player, item, TimerEvent, ActionTrigger.TIMER)
                }
            }
        }
    }

    private fun dispatch(player: Player, item: ItemStack?, event: Event, trigger: ActionTrigger) {
        val itemId = SXItemGateway.identify(item) ?: return
        val actualItem = item ?: return
        val config = SXItemGateway.itemConfig(actualItem) ?: return
        val definitions = ActionRepository.definitions(config, trigger, player)
        if (definitions.isEmpty()) return
        val sharedVariables = linkedMapOf<String, Any?>()
        val context = ActionContext(itemId, player, actualItem, event, trigger, sharedVariables)
        // 所有取消标记必须在事件处理器返回前应用，不能等待前序 Kether Future 完成。
        definitions.forEach(context::cancelIfRequired)
        val first = definitions.first()
        sharedVariables.putAll(first.variables)
        // 首动作必须在原始事件回调内启动，脚本才能在首次挂起前动态取消事件。
        definitions.drop(1).fold(ScriptExecutor.execute(first, context)) { chain, definition ->
            chain.thenComposeAsync({
                sharedVariables.putAll(definition.variables)
                ScriptExecutor.execute(definition, context)
            }, mainThreadExecutor)
        }
    }

    /** 定时入口没有原生 Bukkit 事件，使用稳定单例作为脚本中的 `event` 值。 */
    private object TimerEvent : Event() {
        private val handlers = HandlerList()

        /** Bukkit Event 协议要求每种事件暴露稳定的 HandlerList，即使该事件只作为脚本上下文。 */
        override fun getHandlers(): HandlerList = handlers
    }
}
