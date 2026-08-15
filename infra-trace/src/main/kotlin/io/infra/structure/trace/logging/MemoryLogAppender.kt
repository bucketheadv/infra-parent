package io.infra.structure.trace.logging

import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 基于内存的日志收集器，按 traceId 索引日志供链路详情页展示。
 *
 * 由 [io.infra.structure.trace.autoconfiguration.TraceAutoConfiguration]
 * 在 `@Bean` 方法中调用 [register] 完成注册。
 *
 * @author sven
 */
class MemoryLogAppender : AppenderBase<ILoggingEvent>() {

    override fun append(event: ILoggingEvent) {
        val mdc = event.mdcPropertyMap ?: return
        val traceId = mdc["traceId"] ?: return
        val spanId = mdc["spanId"]
        val parentSpanId = mdc["parentSpanId"]
        val entry = LogEntry(
            timestamp = event.timeStamp,
            level = event.level.toString(),
            serviceName = serviceNameHolder,
            logger = event.loggerName?.substringAfterLast('.') ?: "",
            message = event.formattedMessage ?: "",
            traceId = traceId,
            spanId = spanId,
            parentSpanId = parentSpanId,
            exceptionType = event.throwableProxy?.className,
            exception = event.throwableProxy?.let { formatThrowable(it) }
        )
        val deque: ConcurrentLinkedDeque<LogEntry> = buffer.getOrPut(traceId) { ConcurrentLinkedDeque() }
        deque.add(entry)
        while (deque.size > MAX_PER_TRACE) {
            deque.poll()
        }
        trimGlobal()
    }

    private fun formatThrowable(proxy: ch.qos.logback.classic.spi.IThrowableProxy): String {
        val sb = StringBuilder()
        sb.append(proxy.className)
        proxy.message?.let { sb.append(": ").append(it) }
        sb.append("\n")
        proxy.stackTraceElementProxyArray?.forEach { ste ->
            sb.append("    at ").append(ste.toString()).append("\n")
        }
        return sb.toString()
    }

    private fun trimGlobal() = Companion.trimGlobal()

    companion object {
        private const val MAX_TRACES = 200
        private const val MAX_PER_TRACE = 500
        private val buffer = ConcurrentHashMap<String, ConcurrentLinkedDeque<LogEntry>>()
        private val registered = AtomicBoolean(false)
        private var serviceNameHolder: String? = null

        /**
         * 注册 appender 到 Logback ROOT logger（仅执行一次）。
         *
         * @param context 可选的 [LoggerContext]；为 null 时从 [LoggerFactory] 获取
         * @param serviceName 产生日志的应用名（spring.application.name），随日志条目一并采集
         */
        @JvmStatic
        fun register(context: LoggerContext? = null, serviceName: String? = null) {
            if (serviceName != null) serviceNameHolder = serviceName
            if (!registered.compareAndSet(false, true)) return
            val ctx = context ?: run {
                val factory = LoggerFactory.getILoggerFactory()
                if (factory is LoggerContext) factory else {
                    println("[MemoryLogAppender] ILoggerFactory is not LoggerContext: ${factory.javaClass.name}")
                    return
                }
            }
            val rootLogger = ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)
            val appender = MemoryLogAppender()
            appender.context = ctx
            appender.name = "memoryLogAppender"
            appender.start()
            rootLogger.addAppender(appender)
            println("[MemoryLogAppender] registered, isStarted=${appender.isStarted}")
        }

        /** 按 traceId 查询日志 */
        fun findByTraceId(traceId: String, limit: Int = 200): List<LogEntry> {
            val deque = buffer[traceId] ?: return emptyList()
            val list = deque.toList().sortedBy { it.timestamp }
            return if (list.size > limit) list.takeLast(limit) else list
        }

        /** 取出并清空某 traceId 的全部日志，供请求结束时批量上报 */
        fun drainByTraceId(traceId: String): List<LogEntry> {
            val deque = buffer.remove(traceId) ?: return emptyList()
            return deque.toList()
        }

        /** 写入一批日志（供追踪后台接收各服务上报后存储） */
        fun addEntries(logs: List<LogEntry>) {
            for (entry in logs) {
                val deque = buffer.getOrPut(entry.traceId) { ConcurrentLinkedDeque() }
                deque.add(entry)
                while (deque.size > MAX_PER_TRACE) {
                    deque.poll()
                }
            }
            trimGlobal()
        }

        /** 缓存总量超限时，按插入序淘汰最早的 trace，防止内存无界增长 */
        private fun trimGlobal() {
            if (buffer.size <= MAX_TRACES) return
            val iterator = buffer.entries.iterator()
            while (buffer.size > MAX_TRACES && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }

        /** 按关键字搜索 traceId（匹配日志内容） */
        fun searchTraceIds(keyword: String, limit: Int = 100): List<String> {
            val lower = keyword.lowercase()
            val result = mutableListOf<String>()
            for ((traceId, deque) in buffer) {
                if (deque.any { it.message.lowercase().contains(lower) || it.logger.lowercase().contains(lower) }) {
                    result.add(traceId)
                    if (result.size >= limit) break
                }
            }
            return result
        }
    }
}
