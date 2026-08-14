package top.maplex.sxitemaction.action

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.inventory.ItemStack

/** 支持的动作入口；配置名同时兼容 Zaphkiel 的蛇形与 MythicItemStyrke 的驼峰风格。 */
enum class ActionTrigger(vararg val aliases: String) {
    LEFT_CLICK("on-left-click", "on_left_click", "onLeftClick"),
    RIGHT_CLICK("on-right-click", "on_right_click", "onRightClick"),
    RIGHT_CLICK_ENTITY("on-right-click-entity", "on_right_click_entity", "onRightClickEntity"),
    ATTACK("on-attack", "on_attack", "onAttack"),
    BLOCK_BREAK("on-block-break", "on_block_break", "onBlockBreak"),
    BLOCK_PLACE("on-block-place", "on_block_place", "onBlockPlace"),
    ITEM_BREAK("on-item-break", "on_item_break", "onItemBreak"),
    CONSUME("on-consume", "on_consume", "onItemConsume", "onConsume"),
    DROP("on-drop", "on_drop", "onDrop"),
    PICKUP("on-pickup", "on_pickup", "onPickUp", "onPick"),
    SWAP_TO_MAINHAND("on-swap-to-mainhand", "on_swap_to_mainhand", "onSwapToMainHand"),
    SWAP_TO_OFFHAND("on-swap-to-offhand", "on_swap_to_offhand", "onSwapToOffhand"),
    TIMER("on-timer", "on_timer", "onTimer")
}

/** 脚本引擎策略标识，配置解析阶段即拒绝未知值。 */
enum class ScriptEngine { KETHER, FLUXON }

/**
 * 单个动作定义。
 *
 * `cancel` 在脚本调度前同步应用，因为 Kether 可能异步完成，届时 Bukkit 事件已不能安全取消。
 */
data class ActionDefinition(
    val engine: ScriptEngine,
    val source: String,
    val cancel: Boolean,
    val variables: Map<String, Any?>
)

/**
 * 向两套脚本引擎暴露的统一上下文。
 *
 * `variables` 是同一个可变映射：预制脚本可以读写它，并把结果继续传给另一个引擎，
 * 而顶层同名变量用于兼容 Kether/Fluxon 各自惯用的变量语法。
 */
data class ActionContext(
    val itemId: String,
    val player: Player,
    val item: ItemStack,
    val event: Event,
    val trigger: ActionTrigger,
    val variables: MutableMap<String, Any?>
) {
    /**
     * 设置底层 Bukkit 事件的取消状态。
     *
     * 返回 `false` 表示当前入口没有可取消事件（例如定时动作），脚本可据此选择降级逻辑。
     * 此方法必须在事件回调返回前调用；脚本一旦挂起并异步恢复，Bukkit 已不保证取消生效。
     */
    fun setEventCancelled(cancelled: Boolean): Boolean {
        val cancellable = event as? Cancellable ?: return false
        val wasCancelled = cancellable.isCancelled
        cancellable.isCancelled = cancelled
        if (!wasCancelled && cancelled) {
            ActionStatistics.recordCancellation()
        }
        return true
    }

    /** 取消当前 Bukkit 事件，供 Fluxon 使用 `&context.cancelEvent()` 直接调用。 */
    fun cancelEvent(): Boolean = setEventCancelled(true)

    /** 解除当前 Bukkit 事件的取消状态；仅应在明确允许覆盖其他监听器决定时使用。 */
    fun uncancelEvent(): Boolean = setEventCancelled(false)

    /** 返回当前事件是否已取消；不可取消的入口固定返回 `false`。 */
    fun isEventCancelled(): Boolean = (event as? Cancellable)?.isCancelled ?: false

    /** 根据声明式配置同步取消事件，确保不受异步脚本完成时机影响。 */
    fun cancelIfRequired(definition: ActionDefinition) {
        if (definition.cancel) {
            cancelEvent()
        }
    }

    /** 构造两套脚本引擎共享的变量视图，保证同名变量语义一致。 */
    fun bindings(): Map<String, Any?> = linkedMapOf<String, Any?>(
        "player" to player,
        "item" to item,
        "event" to event,
        "trigger" to trigger.name.lowercase(),
        "itemId" to itemId,
        "vars" to variables,
        "context" to this
    ).apply { putAll(variables) }
}
