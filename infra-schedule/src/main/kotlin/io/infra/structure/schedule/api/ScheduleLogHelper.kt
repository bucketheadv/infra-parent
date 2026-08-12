package io.infra.structure.schedule.api

import io.infra.structure.schedule.model.JobExecutionContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicReference

/**
 * 将业务日志行投递到调度中心的抽象；由框架安装 [ScheduleLogReporter] 实现。
 */
interface ScheduleLogAppender {
    /** 缓冲一行业务日志。 */
    fun offer(logId: Long, line: String)

    /** 立即上报指定执行日志的缓冲内容。 */
    fun flush(logId: Long)
}

/**
 * 业务任务过程日志助手，语义对齐 xxl-job 的 `XxlJobHelper.log`。
 *
 * 与 xxl-job 落本地文件不同：日志进入内存队列，异步上报到调度中心，
 * 换节点后仍可通过执行日志查询保留内容。
 *
 * 用法（在 [ScheduleJobHandler.execute] 内）：
 * ```
 * ScheduleLogHelper.log("开始处理: {}", context.parameters)
 * ```
 *
 * 子线程若使用线程池，需自行传递上下文（[getContext] / [bind]）。
 */
object ScheduleLogHelper {
    private val contextHolder = InheritableThreadLocal<JobExecutionContext>()
    private val appenderRef = AtomicReference<ScheduleLogAppender?>()
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    /** 安装异步上报器；由自动配置调用。 */
    @JvmStatic
    fun install(appender: ScheduleLogAppender) {
        appenderRef.set(appender)
    }

    /** 绑定当前执行上下文；框架在 handler 入口调用。 */
    @JvmStatic
    fun bind(context: JobExecutionContext) {
        contextHolder.set(context)
    }

    /** 清理当前线程上下文；框架在 handler 出口调用。 */
    @JvmStatic
    fun unbind() {
        contextHolder.remove()
    }

    /** 当前绑定的执行上下文；未绑定返回 null。 */
    @JvmStatic
    fun getContext(): JobExecutionContext? = contextHolder.get()

    /** 当前执行日志 ID；未绑定返回 null。 */
    @JvmStatic
    fun getLogId(): Long? = contextHolder.get()?.logId?.takeIf { it > 0 }

    /** 同步刷出当前任务已缓冲的日志行。 */
    @JvmStatic
    fun flush() {
        val logId = getLogId() ?: return
        appenderRef.get()?.flush(logId)
    }

    /** 追加一行业务日志；无绑定上下文时返回 false。 */
    @JvmStatic
    fun log(message: String): Boolean {
        val logId = getLogId() ?: return false
        val appender = appenderRef.get() ?: return false
        appender.offer(logId, formatLine(message))
        return true
    }

    /** 按 `{}` 占位符格式化后追加一行。 */
    @JvmStatic
    fun log(pattern: String, vararg args: Any?): Boolean = log(format(pattern, args))

    /** 追加异常堆栈。 */
    @JvmStatic
    fun log(throwable: Throwable): Boolean {
        val detail = buildString {
            append(throwable.toString())
            throwable.stackTrace.take(40).forEach { frame ->
                append("\n\tat ").append(frame)
            }
        }
        return log(detail)
    }

    private fun formatLine(message: String): String =
        "${timeFormatter.format(Instant.now())} $message\n"

    private fun format(pattern: String, args: Array<out Any?>): String {
        if (args.isEmpty()) return pattern
        val builder = StringBuilder(pattern.length + 32)
        var argIndex = 0
        var index = 0
        while (index < pattern.length) {
            if (index + 1 < pattern.length && pattern[index] == '{' && pattern[index + 1] == '}') {
                builder.append(args.getOrNull(argIndex++)?.toString() ?: "null")
                index += 2
            } else {
                builder.append(pattern[index])
                index++
            }
        }
        return builder.toString()
    }
}
