package top.maplex.sxitemaction.script

import org.tabooproject.fluxon.Fluxon
import org.tabooproject.fluxon.compiler.CompilationContext
import org.tabooproject.fluxon.runtime.FluxonRuntime
import taboolib.common.platform.function.warning
import taboolib.module.kether.KetherShell
import taboolib.module.kether.ScriptOptions
import taboolib.module.kether.printKetherErrorMessage
import top.maplex.sxitemaction.action.ActionContext
import top.maplex.sxitemaction.action.ActionDefinition
import top.maplex.sxitemaction.action.ActionStatistics
import top.maplex.sxitemaction.action.ScriptEngine
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

/** 统一脚本执行门面，通过策略分派隔离 Kether 与 Fluxon 的运行时差异。 */
object ScriptExecutor {

    fun execute(definition: ActionDefinition, context: ActionContext): CompletableFuture<Any?> {
        val startedAt = System.nanoTime()
        val execution = when (definition.engine) {
            ScriptEngine.KETHER -> executeKether(definition, context)
            ScriptEngine.FLUXON -> executeFluxon(definition, context)
        }
        return execution.whenComplete { _, throwable ->
            ActionStatistics.recordExecution(context.trigger, System.nanoTime() - startedAt, throwable != null)
        }
    }

    private fun executeKether(definition: ActionDefinition, context: ActionContext): CompletableFuture<Any?> {
        val options = ScriptOptions.new {
            sender(context.player)
            namespace(listOf("sxitemaction"))
            vars(context.bindings())
            detailError()
        }
        return KetherShell.eval(definition.source.lines(), options).handle { result, throwable ->
            if (throwable == null) {
                result
            } else {
                // 失败必须继续沿 Future 传播，否则后续动作会在错误上下文上继续执行，统计也会被误记为成功。
            throwable.printKetherErrorMessage()
                throw CompletionException(throwable)
            }
        }
    }

    private fun executeFluxon(definition: ActionDefinition, context: ActionContext): CompletableFuture<Any?> {
        val result = runCatching {
            // 先检查自定义下载器状态，避免运行库缺失时在事件线程暴露链接错误。
            FluxonRuntimeLoader.ensureReady()
            val environment = FluxonRuntime.getInstance().newEnvironment()
            context.bindings().forEach(environment::defineRootVariable)
            val compilation = CompilationContext(definition.source).apply {
                // 物品动作需要调用 Bukkit 对象；脚本目录因此只允许服务器管理员维护。
                setAllowReflectionAccess(true)
                setAllowJavaConstruction(true)
            }
            Fluxon.parse(compilation, environment).eval(environment)
        }.onFailure { throwable ->
            warning("Fluxon 动作执行失败 [${context.itemId}/${context.trigger}]: ${throwable.message}")
            throwable.printStackTrace()
        }
        return result.fold(
            onSuccess = { CompletableFuture.completedFuture(it) },
            onFailure = { throwable ->
                // Java 8 没有 CompletableFuture.failedFuture，显式构造异常 Future 保持最低运行版本。
                CompletableFuture<Any?>().also { it.completeExceptionally(throwable) }
            }
        )
    }
}
