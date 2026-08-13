package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleLogAppender
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.JobExecutionResult
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * 缓冲 [io.infra.structure.schedule.api.ScheduleLogHelper] 写出的业务日志，并异步上报。
 *
 * 所有执行状态和业务日志均通过 HTTP 上报调度中心，由调度中心 MySQL 统一持久化。
 */
class ScheduleLogReporter(
    private val properties: InfraScheduleProperties
) : ScheduleLogAppender, DisposableBean {
    private val logger = LoggerFactory.getLogger(javaClass)
    /** 每个日志 ID 独占一个队列锁；空队列会连同锁一起移除，避免长期执行后泄漏。 */
    private val buffers = ConcurrentHashMap<Long, LogBuffer>()
    private val bufferedLines = AtomicInteger()
    private val maxBufferedLines = properties.executor.handleLogMaxBufferedLines.coerceIn(1_000, 1_000_000)
    private val adminBaseUrl = requireNotNull(properties.executor.adminAddress?.takeIf { it.isNotBlank() }) {
        "执行器必须配置 infra.schedule.executor.admin-address，调度状态统一由 MySQL 调度中心维护"
    }.removeSuffix("/")
    private val client = RestClient.builder()
            .baseUrl(adminBaseUrl)
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(properties.executor.connectTimeoutMillis))
                setReadTimeout(Duration.ofMillis(minOf(properties.executor.readTimeoutMillis, 5_000L)))
            })
            .build()

    override fun offer(logId: Long, line: String) {
        if (!reserveLine()) {
            logger.warn("执行过程日志缓冲已达上限，丢弃新日志行: logId={}, max={}", logId, maxBufferedLines)
            return
        }
        // 追加与 flush 共用同一对象锁：flush 删除空缓冲期间，追加方会检测映射是否仍有效并重试，
        // 不会把新批次写进已从 Map 移除的旧队列。
        while (true) {
            val buffer = buffers.computeIfAbsent(logId) { LogBuffer() }
            var accepted = false
            synchronized(buffer) {
                if (buffers[logId] === buffer) {
                    buffer.queue.addLast(LogBatch(lines = listOf(line)))
                    accepted = true
                }
            }
            if (accepted) break
        }
        if (bufferedLines.get() >= BATCH_SIZE) {
            flushAll()
        }
    }

    override fun flush(logId: Long) {
        val buffer = buffers[logId] ?: return
        synchronized(buffer) {
            // 此缓冲可能刚被上一轮 flush 清空并从 Map 移除；不能处理过期对象。
            if (buffers[logId] !== buffer) return
            while (true) {
                val batch = buffer.queue.pollFirst() ?: break
                bufferedLines.addAndGet(-batch.lines.size)
                if (!publish(logId, batch)) {
                    buffer.queue.addFirst(batch)
                    bufferedLines.addAndGet(batch.lines.size)
                    return
                }
            }
            buffers.remove(logId, buffer)
        }
    }

    /** 定时刷出缓冲，降低对调度中心的请求频率。 */
    @Scheduled(fixedDelayString = $$"${infra.schedule.executor.handle-log-flush-millis:500}")
    fun flushAll() {
        buffers.keys.toList().forEach { flush(it) }
    }

    /** 通知调度中心：该日志已离开队列、开始执行 handler（QUEUED → RUNNING）。 */
    fun markStarted(logId: Long, message: String = "执行中") {
        if (logId <= 0) return
        runCatching {
            postJson(
                client,
                ScheduleWebPaths.EXECUTOR_LOG_STARTED.replace("{id}", logId.toString()),
                LogStartedRequest(message = message)
            )
        }.onFailure { exception ->
            logger.warn("上报执行开始失败: logId={}, error={}", logId, exception.message)
        }
    }

    /**
     * 在放行下一票前同步回写终态，避免串行队列里下一条已 markStarted、上一条 finishLog 未完成时出现多条 RUNNING。
     */
    fun markFinished(logId: Long, result: JobExecutionResult, durationMillis: Long?) {
        if (logId <= 0) return
        flush(logId)
        val request = LogFinishRequest(
            success = result.success,
            message = result.message,
            discarded = result.discarded,
            cancelled = result.cancelled,
            durationMillis = durationMillis
        )
        runCatching {
            postJson(client, ScheduleWebPaths.EXECUTOR_LOG_FINISH.replace("{id}", logId.toString()), request)
        }.onFailure { exception ->
            logger.warn("上报执行结束失败: logId={}, error={}", logId, exception.message)
        }
    }

    private fun postJson(remote: RestClient, path: String, body: Any) {
        val token = properties.executor.accessToken?.takeIf { it.isNotBlank() }
        val request = remote.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
            .body(body)
        if (properties.executor.authEnabled) {
            request.header(SCHEDULE_ACCESS_TOKEN_HEADER, token ?: "")
        }
        request.retrieve().toBodilessEntity()
    }

    override fun destroy() {
        flushAll()
    }

    companion object {
        private const val BATCH_SIZE = 32

        /** 将执行器回调结果映射为持久化的日志终态。 */
        fun finishStatus(request: LogFinishRequest): ExecutionStatus = when {
            request.discarded -> ExecutionStatus.SKIPPED
            request.cancelled -> ExecutionStatus.CANCELLED
            request.success -> ExecutionStatus.SUCCESS
            else -> ExecutionStatus.FAILED
        }

        /** 生成管理端可直接展示的执行结果说明。 */
        fun finishMessage(request: LogFinishRequest): String = when {
            request.discarded -> request.message ?: "丢弃后续调度"
            request.cancelled -> request.message ?: "任务执行被取消"
            request.success -> request.message?.takeIf { it.isNotBlank() }?.let { "执行成功：$it" } ?: "执行成功"
            else -> request.message ?: "任务处理器返回失败"
        }
    }

    /** 返回 false 时调用方把失败批次放回队首，避免日志上报乱序。 */
    private fun publish(logId: Long, batch: LogBatch): Boolean {
        if (batch.lines.isEmpty()) return true
        return runCatching {
            val token = properties.executor.accessToken?.takeIf { it.isNotBlank() }
            val request = client.post()
                .uri(ScheduleWebPaths.EXECUTOR_LOG_HANDLE_APPEND.replace("{id}", logId.toString()))
                .body(HandleLogAppendRequest(lines = batch.lines))
            if (properties.executor.authEnabled) {
                request.header(SCHEDULE_ACCESS_TOKEN_HEADER, token ?: "")
            }
            request.retrieve().toBodilessEntity()
            true
        }.getOrElse { exception ->
            logger.warn("上报业务执行日志失败: logId={}, error={}", logId, exception.message)
            false
        }
    }

    /** 原子预占一行缓冲容量，避免多个 Handler 并发越过上限。 */
    private fun reserveLine(): Boolean {
        while (true) {
            val current = bufferedLines.get()
            if (current >= maxBufferedLines) return false
            if (bufferedLines.compareAndSet(current, current + 1)) return true
        }
    }

    /** 同一日志的一个待上报批次。 */
    private data class LogBatch(val lines: List<String>)

    /** 同一执行日志的待上报批次队列及其互斥锁。 */
    private class LogBuffer {
        val queue = ConcurrentLinkedDeque<LogBatch>()
    }
}

/** 执行器向调度中心追加业务日志的请求体。 */
data class HandleLogAppendRequest(
    val lines: List<String>
)

/** 执行器通知调度中心日志开始真正执行。 */
data class LogStartedRequest(
    val message: String = "执行中"
)

/** 执行器在串行放行前同步回写终态。 */
data class LogFinishRequest(
    val success: Boolean,
    val message: String? = null,
    val discarded: Boolean = false,
    val cancelled: Boolean = false,
    val durationMillis: Long? = null
)
