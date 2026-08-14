package top.maplex.sxitemaction.script

import taboolib.common.Inject
import taboolib.module.kether.*
import top.maplex.sxitemaction.action.ActionContext

/**
 * 为 Kether 提供事件取消动作。
 *
 * 语法为 `sx-event cancel|uncancel|cancelled`；动作只操作当前 ActionContext，
 * 不允许脚本通过全局状态误取消其他玩家或其他物品触发的事件。
 */
@Inject
object KetherEventAction {

    /** 注册到附属独立命名空间，避免与服务器现有 Kether 动作发生名称冲突。 */
    @KetherParser(["sx-event"], namespace = "sxitemaction")
    fun parser() = scriptParser {
        val operation = it.expects("cancel", "uncancel", "cancelled")
        actionNow {
            val context = variables().get<Any?>("context").orElse(null) as? ActionContext
                ?: error("sx-event 只能在 SX-Item-Action 物品动作上下文中使用")
            when (operation) {
                "cancel" -> context.cancelEvent()
                "uncancel" -> context.uncancelEvent()
                "cancelled" -> context.isEventCancelled()
                else -> false
            }
        }
    }
}
