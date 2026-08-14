package top.maplex.sxitemaction.command

import org.bukkit.Bukkit
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack
import taboolib.common.platform.command.CommandBody
import taboolib.common.platform.command.CommandHeader
import taboolib.common.platform.command.mainCommand
import taboolib.common.platform.command.subCommand
import taboolib.common.platform.function.info
import taboolib.common.platform.ProxyCommandSender
import taboolib.common.platform.ProxyPlayer
import top.maplex.sxitemaction.action.ActionContext
import top.maplex.sxitemaction.action.ActionDefinition
import top.maplex.sxitemaction.action.ActionRepository
import top.maplex.sxitemaction.action.ActionStatistics
import top.maplex.sxitemaction.action.ActionTrigger
import top.maplex.sxitemaction.action.ScriptEngine
import top.maplex.sxitemaction.script.ScriptExecutor

/** 提供脚本重载、运行状态与静态校验命令，便于无客户端的服务器控制台验收。 */
@CommandHeader(name = "sxitemaction", aliases = ["sxia"], permission = "sxitemaction.admin")
object ActionCommand {

    @CommandBody
    val main = mainCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            sender.sendMessage("§6SX-Item-Action §7管理命令")
            sender.sendMessage("§e/sxia reload §7- 重载预制脚本")
            sender.sendMessage("§e/sxia status §7- 查看依赖与运行统计")
            sender.sendMessage("§e/sxia validate §7- 查看脚本仓库诊断")
            sender.sendMessage("§e/sxia selftest §7- 以当前玩家执行两套脚本与事件取消自检")
        }
    }

    @CommandBody
    val reload = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            ActionRepository.reload()
            sender.sendMessage("§a已重载 ${ActionRepository.templateCount} 个预制脚本，诊断 ${ActionRepository.diagnosticMessages.size} 项")
            info("SX-Item-Action scripts reloaded by ${sender.name}")
        }
    }

    @CommandBody
    val status = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val snapshot = ActionStatistics.snapshot()
            val sxItem = Bukkit.getPluginManager().getPlugin("SX-Item")
            sender.sendMessage("§6SX-Item-Action 状态")
            sender.sendMessage("§7SX-Item: ${if (sxItem?.isEnabled == true) "§a已启用 ${sxItem.description.version}" else "§c未启用"}")
            sender.sendMessage("§7预制脚本: §f${ActionRepository.templateCount} §8(${ActionRepository.templateKeys().joinToString()})")
            sender.sendMessage("§7执行/失败/取消: §f${snapshot.executions}/${snapshot.failures}/${snapshot.cancellations}")
            sender.sendMessage("§7平均耗时: §f${snapshot.averageMicros} μs")
        }
    }

    @CommandBody
    val validate = subCommand {
        execute<ProxyCommandSender> { sender, _, _ ->
            val diagnostics = ActionRepository.diagnosticMessages
            if (diagnostics.isEmpty()) {
                sender.sendMessage("§a脚本仓库静态校验通过")
            } else {
                sender.sendMessage("§e脚本仓库发现 ${diagnostics.size} 项问题:")
                diagnostics.forEach { sender.sendMessage("§7- §f$it") }
            }
        }
    }

    /**
     * 在真实服务器线程和真实玩家对象上执行最小闭环测试。
     *
     * 自检不依赖某个物品配置，避免为了诊断修改生产物品；它覆盖 Kether 注册动作、Fluxon 反射调用、
     * Future 串联和 Bukkit Cancellable 状态，物品识别则由玩家手持 SX-Item 单独验证。
     */
    @CommandBody
    val selftest = subCommand {
        execute<ProxyPlayer> { sender, _, _ ->
            // 命令层收到的是跨平台代理，按唯一名称取回当前在线 Bukkit 玩家后才能访问物品栏和事件 API。
            val player = Bukkit.getPlayerExact(sender.name)
                ?: error("自检只能由当前在线的 Bukkit 玩家执行")
            val item = player.inventory.itemInMainHand.takeUnless { it.type == org.bukkit.Material.AIR }
                ?: ItemStack(org.bukkit.Material.STONE)
            val event = SelfTestEvent()
            val variables = linkedMapOf<String, Any?>("message" to "SX-Item-Action runtime self-test")
            val context = ActionContext("__selftest__", player, item, event, ActionTrigger.RIGHT_CLICK, variables)
            val kether = ActionDefinition(ScriptEngine.KETHER, "sx-event cancel", false, emptyMap())
            val fluxon = ActionDefinition(ScriptEngine.FLUXON, "&context.uncancelEvent()", false, emptyMap())

            ScriptExecutor.execute(kether, context).thenCompose {
                check(event.isCancelled) { "Kether 未能取消测试事件" }
                ScriptExecutor.execute(fluxon, context)
            }.whenComplete { _, throwable ->
                // Future 可能由脚本线程完成，所有玩家消息仍切回 Bukkit 主线程发送。
                Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("SX-Item-Action")!!, Runnable {
                    if (throwable == null && !event.isCancelled) {
                        sender.sendMessage("§a自检通过：Kether、Fluxon、动作链与事件取消均正常")
                    } else {
                        sender.sendMessage("§c自检失败：${throwable?.cause?.message ?: throwable?.message ?: "Fluxon 未解除测试事件"}")
                    }
                })
            }
        }
    }

    /** 自检专用可取消事件；独立 HandlerList 避免污染任何真实游戏事件监听链。 */
    private class SelfTestEvent : Event(), Cancellable {
        private var cancelled = false

        override fun isCancelled(): Boolean = cancelled

        override fun setCancelled(cancel: Boolean) {
            cancelled = cancel
        }

        override fun getHandlers(): HandlerList = HANDLERS

        companion object {
            /** Bukkit 事件协议要求类级共享 HandlerList，即使事件不经 PluginManager 广播。 */
            @JvmStatic
            private val HANDLERS = HandlerList()
        }
    }
}
