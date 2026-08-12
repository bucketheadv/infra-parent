package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleLogAppender
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
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

    override fun destroy() {
        flushAll()
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

    private companion object {
        const val BATCH_SIZE = 32
    }
}

/** 执行器向调度中心追加业务日志的请求体。 */
data class HandleLogAppendRequest(
    val lines: List<String>
)
