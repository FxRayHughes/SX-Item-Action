package top.maplex.sxitemaction.listener

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryOpenEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.event.EventPriority
import taboolib.common.platform.event.OptionalEvent
import taboolib.common.platform.event.SubscribeEvent
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import top.maplex.sxitemaction.lifecycle.ItemLifecycle
import top.maplex.sxitemaction.sxitem.SXItemGateway

/** 在生成/更新与使用边界维护物品生命周期，周期清理复用动作扫描，避免两套定时任务竞态。 */
object ItemLifecycleListener {

    /** SX-Item 为硬依赖，但使用 OptionalEvent 避免把它的实现打入本插件。 */
    @SubscribeEvent(bind = "github.saukiya.sxitem.event.SXItemSpawnEvent", priority = EventPriority.HIGHEST)
    fun onSXItemSpawnEvent(optional: OptionalEvent) {
        val event = optional.get<Any>()
        val item = event.javaClass.getMethod("getItem").invoke(event) as ItemStack
        // SX 的玩家上下文必须随生成事件传递，否则原生 PlaceholderAPI 表达式会丢失玩家。
        val player = event.javaClass.getMethod("getPlayer").invoke(event) as? Player
        ItemLifecycle.inspect(item, player)
    }

    /** 更新失败时取消 SX-Item 的替换，保留原物品而非悄悄清空已消耗耐久或到期时间。 */
    @SubscribeEvent(bind = "github.saukiya.sxitem.event.SXItemUpdateEvent", priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSXItemUpdateEvent(optional: OptionalEvent) {
        val event = optional.get<Any>()
        if ((event as Cancellable).isCancelled) return
        runCatching {
            val item = event.javaClass.getMethod("getItem").invoke(event) as ItemStack
            val oldItem = event.javaClass.getMethod("getOldItem").invoke(event) as ItemStack
            val player = event.javaClass.getMethod("getPlayer").invoke(event) as? Player
            ItemLifecycle.preserve(oldItem, item, player)
        }.onFailure {
            event.isCancelled = true
            warning("已阻止会丢失耐久/限时状态的 SX-Item 更新: ${it.message}")
        }
    }

    /** 返回 false 时同时取消原版行为；不依赖动作脚本是否配置，且不允许异常配置绕过限制。 */
    fun allow(player: Player, item: ItemStack?, event: Event? = null, broken: Boolean = false): Boolean {
        if (item == null) return true
        val state = ItemLifecycle.inspect(item, player) ?: return true
        val denied = state.invalid || state.expired() || (!broken && state.current == 0)
        if (!denied) return true
        (event as? Cancellable)?.isCancelled = true
        // 事件可能持有物品副本，清理背包真实槽位，不能仅把 event.item 置空。
        scanPlayer(player)
        return false
    }

    /** 按槽位检查每一件物品，不按 SX-Item 编号去重；同编号的两把剑可以拥有不同有效期。 */
    fun scanPlayer(player: Player) {
        scanInventory(player.inventory, player)
        scanInventory(player.enderChest, player)
        val cursor = player.itemOnCursor
        if (shouldRemove(cursor, player)) player.setItemOnCursor(null)
    }

    /** 只遍历已加载且被访问的容器；不会为了清理物品加载离线玩家或世界区块。 */
    fun scanInventory(inventory: Inventory, player: Player? = null) {
        for (slot in 0 until inventory.size) {
            val item = inventory.getItem(slot) ?: continue
            // 普通物品不克隆元数据，周期扫描只为 SX-Item 支付副本比较开销。
            if (SXItemGateway.identify(item) == null) continue
            val before = item.clone()
            if (shouldRemove(item, player)) inventory.setItem(slot, null)
            else if (item != before) inventory.setItem(slot, item)
        }
    }

    private fun shouldRemove(item: ItemStack, player: Player? = null): Boolean {
        val state = ItemLifecycle.inspect(item, player) ?: return false
        // 配置错误只禁用，不销毁，防止管理员写错日期造成不可恢复的数据损失。
        return !state.invalid && (state.expired() || state.current == 0)
    }

    /** 登录立即检查，并在下一 tick 覆盖其他插件于加入回调恢复背包的情形。 */
    @SubscribeEvent
    fun onPlayerJoinEvent(event: PlayerJoinEvent) {
        scanPlayer(event.player)
        submit(delay = 1) { if (event.player.isOnline) scanPlayer(event.player) }
    }

    /** 先于原版使用检查；空中交互即使原生预取消也必须检测两个手槽。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerInteractEvent(event: PlayerInteractEvent) {
        allow(event.player, event.item, event)
    }

    /** 右键实体支持副手，防止通过喂食/交互绕过主手动作的检查。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerInteractEntityEvent(event: PlayerInteractEntityEvent) {
        val item = if (event.hand == org.bukkit.inventory.EquipmentSlot.HAND) event.player.inventory.itemInMainHand
        else event.player.inventory.itemInOffHand
        allow(event.player, item, event)
    }

    /** 原版攻击不要求物品存在 Action 节点，也必须拒绝到期武器。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityDamageByEntityEvent(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        allow(player, player.inventory.itemInMainHand, event)
    }

    /** 弓的释放独立于右键事件，在实际发射前再次检查截止时间。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityShootBowEvent(event: EntityShootBowEvent) {
        val player = event.entity as? Player ?: return
        allow(player, event.bow, event)
    }

    /** 开始挖掘和完成破坏之间可能到期，必须在破坏事件再次校验。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onBlockBreakEvent(event: BlockBreakEvent) {
        allow(event.player, event.player.inventory.itemInMainHand, event)
    }

    /** 放置事件可能来自副手，使用事件物品而非假定主手。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onBlockPlaceEvent(event: BlockPlaceEvent) {
        allow(event.player, event.itemInHand, event)
    }

    /** 消耗完成时再校验，防止长时间食用跨过到期边界。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerItemConsumeEvent(event: PlayerItemConsumeEvent) {
        allow(event.player, event.item, event)
    }

    /** 拾取到期掉落物时删除实体，防止取消拾取后遗留无法处理的地面物品。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onEntityPickupItemEvent(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (!allow(player, event.item.itemStack, event) && shouldRemove(event.item.itemStack, player)) event.item.remove()
    }

    /** 允许有效物品正常交易；过期物品直接移除，取消丢弃会被 Bukkit 放回背包。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerDropItemEvent(event: PlayerDropItemEvent) {
        if (shouldRemove(event.itemDrop.itemStack, event.player)) {
            event.itemDrop.remove()
        } else {
            allow(event.player, event.itemDrop.itemStack, event)
        }
    }

    /** 换手使用事件给出的目标物品，两个方向都须检查。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onPlayerSwapHandItemsEvent(event: PlayerSwapHandItemsEvent) {
        allow(event.player, event.mainHandItem, event)
        allow(event.player, event.offHandItem, event)
    }

    /** 容器打开即清理，覆盖离线计时期间存放在箱子中的到期物品。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onInventoryOpenEvent(event: InventoryOpenEvent) {
        scanInventory(event.inventory, event.player as? Player)
    }

    /** 点击事务中先取消再延迟清理，避免改动光标/槽位让 Bukkit 事务覆盖清理结果。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onInventoryClickEvent(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val hotbar = event.hotbarButton.takeIf { it in 0..8 }?.let { player.inventory.getItem(it) }
        // 新版本 SWAP_OFFHAND 通过枚举名称兼容，保持最低 Bukkit API 可加载。
        val offhand = if (event.click.name == "SWAP_OFFHAND") player.inventory.itemInOffHand else null
        if (listOf(event.currentItem, event.cursor, hotbar, offhand).filterNotNull().any { blocked(it, player) }) {
            event.isCancelled = true
            submit(delay = 1) {
                if (player.isOnline) { scanPlayer(player); scanInventory(player.openInventory.topInventory, player) }
            }
        }
    }

    /** 拖拽事务尚未落槽，禁止过期光标被拆成多份写入背包。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onInventoryDragEvent(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (blocked(event.oldCursor, player)) {
            event.isCancelled = true
            submit(delay = 1) { if (player.isOnline) scanPlayer(player) }
        }
    }

    private fun blocked(item: ItemStack, player: Player?): Boolean = ItemLifecycle.inspect(item, player)?.let {
        it.invalid || it.expired() || it.current == 0
    } == true
}
