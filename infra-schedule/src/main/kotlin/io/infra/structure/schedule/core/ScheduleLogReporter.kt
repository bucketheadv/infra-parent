package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleLogAppender
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.JobExecutionResult
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * 缓冲 [io.infra.structure.schedule.api.ScheduleLogHelper] 写出的业务日志，并异步上报。
 *
 * - 配置了 `adminAddress` 时 HTTP POST 到调度中心；
 * - 否则直接写入本地 [ScheduleExecutionLogRepository]（同进程调度+执行场景）。
 */
class ScheduleLogReporter(
    private val properties: InfraScheduleProperties,
    private val localRepository: ScheduleExecutionLogRepository
) : ScheduleLogAppender, DisposableBean {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val buffers = ConcurrentHashMap<Long, ConcurrentLinkedQueue<String>>()
    private val bufferedLines = AtomicInteger()
    private val adminBaseUrl = properties.executor.adminAddress?.takeIf { it.isNotBlank() }?.removeSuffix("/")
    private val client = adminBaseUrl?.let { baseUrl ->
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(properties.executor.connectTimeoutMillis))
                setReadTimeout(Duration.ofMillis(minOf(properties.executor.readTimeoutMillis, 5_000L)))
            })
            .build()
    }

    override fun offer(logId: Long, line: String) {
        buffers.computeIfAbsent(logId) { ConcurrentLinkedQueue() }.add(line)
        if (bufferedLines.incrementAndGet() >= BATCH_SIZE) {
            flushAll()
        }
    }

    override fun flush(logId: Long) {
        val lines = drain(logId)
        if (lines.isEmpty()) return
        publish(logId, lines)
    }

    /** 定时刷出缓冲，降低对调度中心的请求频率。 */
    @Scheduled(fixedDelayString = $$"${infra.schedule.executor.handle-log-flush-millis:500}")
    fun flushAll() {
        buffers.keys.toList().forEach { flush(it) }
    }

    /** 通知调度中心：该日志已离开队列、开始执行 handler（QUEUED → RUNNING）。 */
    fun markStarted(logId: Long, message: String = "执行中") {
        if (logId <= 0) return
        val remote = client
        if (remote != null) {
            runCatching {
                postJson(
                    remote,
                    ScheduleWebPaths.EXECUTOR_LOG_STARTED.replace("{id}", logId.toString()),
                    LogStartedRequest(message = message)
                )
            }.onFailure { exception ->
                logger.warn("上报执行开始失败: logId={}, error={}", logId, exception.message)
            }
            return
        }
        runCatching {
            localRepository.markRunningIfQueued(logId, message)
        }.onFailure { exception ->
            logger.warn("本地标记执行开始失败: logId={}, error={}", logId, exception.message)
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
        val remote = client
        if (remote != null) {
            runCatching {
                postJson(remote, ScheduleWebPaths.EXECUTOR_LOG_FINISH.replace("{id}", logId.toString()), request)
            }.onFailure { exception ->
                logger.warn("上报执行结束失败: logId={}, error={}", logId, exception.message)
            }
            return
        }
        runCatching {
            applyLocalFinish(logId, request)
        }.onFailure { exception ->
            logger.warn("本地标记执行结束失败: logId={}, error={}", logId, exception.message)
        }
    }

    private fun applyLocalFinish(logId: Long, request: LogFinishRequest) {
        val current = localRepository.findById(logId) ?: return
        if (!current.status.isActive()) return
        val status = finishStatus(request)
        localRepository.updateIfRunning(
            current.copy(
                finishTime = System.currentTimeMillis(),
                status = status,
                message = finishMessage(request),
                durationMillis = request.durationMillis
            )
        )
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

        fun finishStatus(request: LogFinishRequest): ExecutionStatus = when {
            request.discarded -> ExecutionStatus.SKIPPED
            request.cancelled -> ExecutionStatus.CANCELLED
            request.success -> ExecutionStatus.SUCCESS
            else -> ExecutionStatus.FAILED
        }

        fun finishMessage(request: LogFinishRequest): String = when {
            request.discarded -> request.message ?: "丢弃后续调度"
            request.cancelled -> request.message ?: "任务执行被取消"
            request.success -> {
                val value = request.message?.takeIf { it.isNotBlank() }
                if (value == null) "执行成功" else "执行成功：$value"
            }
            else -> request.message ?: "任务处理器返回失败"
        }
    }

    private fun drain(logId: Long): List<String> {
        val queue = buffers[logId] ?: return emptyList()
        val lines = ArrayList<String>()
        while (true) {
            val line = queue.poll() ?: break
            lines += line
            bufferedLines.decrementAndGet()
        }
        if (queue.isEmpty()) {
            buffers.remove(logId, queue)
        }
        return lines
    }

    private fun publish(logId: Long, lines: List<String>) {
        val chunk = lines.joinToString(separator = "")
        if (chunk.isEmpty()) return
        val remote = client
        if (remote != null) {
            runCatching {
                val token = properties.executor.accessToken?.takeIf { it.isNotBlank() }
                val request = remote.post()
                    .uri(ScheduleWebPaths.EXECUTOR_LOG_HANDLE_APPEND.replace("{id}", logId.toString()))
                    .body(HandleLogAppendRequest(lines = lines))
                if (properties.executor.authEnabled) {
                    request.header(SCHEDULE_ACCESS_TOKEN_HEADER, token ?: "")
                }
                request.retrieve().toBodilessEntity()
            }.onFailure { exception ->
                logger.warn("上报业务执行日志失败: logId={}, error={}", logId, exception.message)
            }
            return
        }
        runCatching {
            localRepository.appendHandleLog(logId, chunk)
        }.onFailure { exception ->
            logger.warn("本地写入业务执行日志失败: logId={}, error={}", logId, exception.message)
        }
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
