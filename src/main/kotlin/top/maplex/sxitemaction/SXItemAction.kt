package top.maplex.sxitemaction

import org.tabooproject.fluxon.runtime.FluxonRuntime
import taboolib.common.platform.Plugin
import taboolib.common.platform.function.info
import taboolib.common.platform.function.submit
import taboolib.common.platform.function.warning
import top.maplex.sxitemaction.action.ActionRepository
import top.maplex.sxitemaction.script.FluxonRuntimeLoader
import top.maplex.sxitemaction.sxitem.SXItemLoreVariables

/**
 * SX-Item 的物品动作、自定义耐久与限时附属入口。
 *
 * 插件只消费 SX-Item 已生成物品上的身份标记及对应生成器配置，不注册额外物品格式，
 * 从而保证动作、耐久和限时功能不会意外接管其他物品插件。
 */
object SXItemAction : Plugin() {

    override fun onEnable() {
        // SX 生成器先解析顶层 Lore；必须提前注册整行变量，不能等物品生成事件才提供变量名。
        SXItemLoreVariables.register()
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

    /** 正常禁用时撤销全局变量，避免 SX 后续生成含内部标记的物品。 */
    override fun onDisable() {
        SXItemLoreVariables.unregister()
    }
}
