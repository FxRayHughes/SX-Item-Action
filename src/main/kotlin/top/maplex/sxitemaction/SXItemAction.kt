package top.maplex.sxitemaction

import org.tabooproject.fluxon.runtime.FluxonRuntime
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import top.maplex.sxitemaction.action.ActionRepository
import top.maplex.sxitemaction.script.FluxonRuntimeLoader

/**
 * SX-Item 的物品动作附属入口。
 *
 * 插件只消费 SX-Item 已生成物品上的身份标记及对应生成器配置，不注册额外物品格式，
 * 从而保证动作功能不会意外兼容或接管其他物品插件。
 */
object SXItemAction : Plugin() {

    override fun onEnable() {
        if (FluxonRuntimeLoader.isReady()) {
            FluxonRuntime.getInstance().primaryThreadExecutor = java.util.concurrent.Executor { task ->
                submit { task.run() }
            }
        } else {
            // Kether 动作仍可工作；Fluxon 调用会在执行边界返回带原因的失败 Future。
            warning("Fluxon 运行时不可用：${FluxonRuntimeLoader.failureMessage() ?: "初始化未完成"}")
        }
        ActionRepository.reload()
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("SX-Item")?.isEnabled != true) {
            warning("未检测到已启用的 SX-Item；动作监听器会保持空操作，直到下次完整重启并正确加载依赖")
        }
        info("SX-Item-Action 已加载 ${ActionRepository.templateCount} 个预制脚本")
    }
}
