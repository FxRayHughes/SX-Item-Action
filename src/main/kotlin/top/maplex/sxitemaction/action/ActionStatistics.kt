package top.maplex.sxitemaction.action

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 动作运行统计中心，用于实服诊断脚本执行量、失败量与取消量。
 *
 * 统计只保存聚合计数，不持有 Player、Event 或 ItemStack，避免插件长期运行时形成对象泄漏。
 */
object ActionStatistics {

    private val executions = AtomicLong()
    private val failures = AtomicLong()
    private val cancellations = AtomicLong()
    private val totalNanos = AtomicLong()
    private val triggerExecutions = ConcurrentHashMap<ActionTrigger, AtomicLong>()

    /** 记录一次完成的脚本执行，耗时包含脚本自身同步部分及异步 Future 等待时间。 */
    fun recordExecution(trigger: ActionTrigger, elapsedNanos: Long, failed: Boolean) {
        executions.incrementAndGet()
        triggerExecutions.computeIfAbsent(trigger) { AtomicLong() }.incrementAndGet()
        totalNanos.addAndGet(elapsedNanos.coerceAtLeast(0))
        if (failed) failures.incrementAndGet()
    }

    /** 记录一次从未取消变为已取消的状态变化，避免重复调用导致计数膨胀。 */
    fun recordCancellation() {
        cancellations.incrementAndGet()
    }

    /** 返回不可变快照，命令输出期间不会阻塞脚本执行线程。 */
    fun snapshot(): ActionStatisticsSnapshot {
        val count = executions.get()
        return ActionStatisticsSnapshot(
            executions = count,
            failures = failures.get(),
            cancellations = cancellations.get(),
            averageMicros = if (count == 0L) 0 else totalNanos.get() / count / 1_000,
            triggers = triggerExecutions.mapValues { it.value.get() }
        )
    }
}

/** 管理命令使用的稳定统计值对象。 */
data class ActionStatisticsSnapshot(
    val executions: Long,
    val failures: Long,
    val cancellations: Long,
    val averageMicros: Long,
    val triggers: Map<ActionTrigger, Long>
)
