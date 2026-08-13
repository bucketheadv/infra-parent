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
import org.springframework.web.client.RestClientResponseException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.ConcurrentHashMap.newKeySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
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
    private val callbackExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "infra-schedule-log-callback").apply { isDaemon = true }
    }
    /** 每个日志 ID 独占一个队列锁；空队列会连同锁一起移除，避免长期执行后泄漏。 */
    private val buffers = ConcurrentHashMap<Long, LogBuffer>()
    /** 已提交到回调线程的日志刷写任务，避免高频业务日志重复排队。 */
    private val scheduledFlushes = newKeySet<Long>()
    /** 回调及其重试任务的上限，防止 Admin 长时间不可用导致无界内存堆积。 */
    private val pendingCallbacks = AtomicInteger()
    private val maxPendingCallbacks = 10_000
    /**
     * 队列暂满时保留的一份终态回调。以 logId 去重，既避免终态直接丢失，也不允许故障期间无界增长。
     * 进程崩溃后的终态仍由 Admin 僵尸日志回收处理，不能把内存缓存当作持久化消息队列。
     */
    private val pendingFinishes = ConcurrentHashMap<Long, PendingFinish>()
    /** 与 [pendingFinishes] 同步变更的容量计数，保证保底缓存上限在并发下严格成立。 */
    private val pendingFinishCount = AtomicInteger()
    /** 已入队或正在发送的终态，防止定时 drain 对同一 logId 重复投递。 */
    private val scheduledFinishDeliveries = newKeySet<Long>()
    private val maxPendingFinishes = 1_000
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
            requestFlush(logId)
        }
    }

    /**
     * 请求异步刷出一个日志批次。Handler 线程只负责入队，不能因 Admin 网络超时阻塞。
     * 同一个单线程回调器同时保证 started、过程日志和 finish 的提交顺序。
     */
    override fun flush(logId: Long) = requestFlush(logId)

    private fun requestFlush(logId: Long) {
        if (!scheduledFlushes.add(logId)) return
        if (!enqueueCallbackTask("过程日志") {
                try {
                    flushSynchronously(logId)
                } finally {
                    scheduledFlushes.remove(logId)
                }
            }) {
            scheduledFlushes.remove(logId)
        }
    }

    /** 仅由 [callbackExecutor] 或关闭钩子调用，允许执行 HTTP 上报。 */
    private fun flushSynchronously(logId: Long): Boolean {
        val buffer = buffers[logId] ?: return true
        while (true) {
            val batch = synchronized(buffer) {
                if (buffers[logId] !== buffer) return true
                buffer.queue.pollFirst()
            } ?: run {
                synchronized(buffer) {
                    if (buffers[logId] === buffer && buffer.queue.isEmpty()) buffers.remove(logId, buffer)
                }
                return true
            }
            bufferedLines.addAndGet(-batch.lines.size)
            when (publish(logId, batch)) {
                PublishResult.SUCCESS, PublishResult.DROP -> Unit
                PublishResult.RETRY -> {
                    synchronized(buffer) {
                        if (buffers[logId] === buffer) {
                            buffer.queue.addFirst(batch)
                            bufferedLines.addAndGet(batch.lines.size)
                        }
                    }
                    return false
                }
            }
        }
    }

    /** 定时刷出缓冲，降低对调度中心的请求频率。 */
    @Scheduled(fixedDelayString = $$"${infra.schedule.executor.handle-log-flush-millis:500}")
    fun flushAll() {
        buffers.keys.toList().forEach(::requestFlush)
        drainPendingFinishes()
    }

    /** 通知调度中心：该日志已离开队列、开始执行 handler（QUEUED → RUNNING）。 */
    fun markStarted(logId: Long, message: String = "执行中") {
        if (logId <= 0) return
        enqueueCallback(
            ScheduleWebPaths.EXECUTOR_LOG_STARTED.replace("{id}", logId.toString()),
            LogStartedRequest(message = message), "开始"
        )
    }

    /**
     * 终态回调异步提交；管理端条件更新会拒绝旧回调覆盖终态。
     */
    fun markFinished(logId: Long, result: JobExecutionResult, durationMillis: Long?) {
        if (logId <= 0) return
        val request = LogFinishRequest(
            success = result.success,
            message = result.message,
            discarded = result.discarded,
            cancelled = result.cancelled,
            durationMillis = durationMillis
        )
        enqueueFinishCallback(logId, request)
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
        // Spring 停机阶段允许有限同步刷写，运行中的 Handler 不会走到这里。
        buffers.keys.toList().forEach(::flushSynchronously)
        callbackExecutor.shutdownNow()
    }

    companion object {
        private const val BATCH_SIZE = 32
        private const val CALLBACK_MAX_RETRIES = 3
        private const val CALLBACK_RETRY_DELAY_MILLIS = 500L
        private const val FINISH_FLUSH_MAX_ATTEMPTS = 3
        private const val RESERVED_STATUS_CALLBACKS = 100

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
    private fun publish(logId: Long, batch: LogBatch): PublishResult {
        if (batch.lines.isEmpty()) return PublishResult.SUCCESS
        return runCatching {
            val token = properties.executor.accessToken?.takeIf { it.isNotBlank() }
            val request = client.post()
                .uri(ScheduleWebPaths.EXECUTOR_LOG_HANDLE_APPEND.replace("{id}", logId.toString()))
                .body(HandleLogAppendRequest(lines = batch.lines))
            if (properties.executor.authEnabled) {
                request.header(SCHEDULE_ACCESS_TOKEN_HEADER, token ?: "")
            }
            request.retrieve().toBodilessEntity()
            PublishResult.SUCCESS
        }.getOrElse { exception ->
            logger.warn("上报业务执行日志失败: logId={}, error={}", logId, exception.message)
            if (isPermanentHttpFailure(exception)) PublishResult.DROP else PublishResult.RETRY
        }
    }

    /** 已清理日志的 404 等永久错误直接丢弃，避免队首无限重试；408/429 仍可重试。 */
    private fun isPermanentHttpFailure(exception: Throwable): Boolean {
        val response = exception as? RestClientResponseException ?: return false
        val status = response.statusCode.value()
        return status in 400..499 && status != 408 && status != 429
    }

    /** 将首次回调也交给独立线程，防止 Admin 超时占用 JobThread。 */
    private fun enqueueCallback(path: String, body: Any, label: String) {
        enqueueCallbackTask(label) { postCallbackWithRetry(path, body, label) }
    }

    /** 过程日志遇到可恢复错误时，终态回调延后，避免 finish 先于业务日志到达。 */
    private fun enqueueFinishCallback(logId: Long, request: LogFinishRequest, flushAttempt: Int = 0) {
        if (!tryEnqueueFinish(logId, PendingFinish(request, flushAttempt))) {
            retainFinish(logId, PendingFinish(request, flushAttempt))
        }
    }

    /** 尝试占用一个回调槽位；队列可用时直接发送，不依赖保底缓存。 */
    private fun tryEnqueueFinish(logId: Long, finish: PendingFinish): Boolean {
        if (!scheduledFinishDeliveries.add(logId)) return false
        if (!enqueueCallbackTask("结束") {
                try {
                    sendFinish(logId, finish)
                } finally {
                    scheduledFinishDeliveries.remove(logId)
                }
            }) {
            scheduledFinishDeliveries.remove(logId)
            return false
        }
        return true
    }

    /** 为同一日志只保留一份终态；容量到顶时显式告警，Admin 仍会通过僵尸回收修正状态。 */
    private fun retainFinish(logId: Long, finish: PendingFinish) {
        if (pendingFinishes.containsKey(logId)) return
        while (true) {
            val current = pendingFinishCount.get()
            if (current >= maxPendingFinishes) {
                logger.error("终态保底缓存已满，无法保留结束回调: logId={}; Admin 将通过僵尸回收修正状态", logId)
                return
            }
            if (pendingFinishCount.compareAndSet(current, current + 1)) break
        }
        if (pendingFinishes.putIfAbsent(logId, finish) != null) {
            pendingFinishCount.decrementAndGet()
        }
    }

    /** 从保底缓存移除终态并同步释放容量。 */
    private fun removePendingFinish(logId: Long, expected: PendingFinish) {
        if (pendingFinishes.remove(logId, expected)) {
            pendingFinishCount.decrementAndGet()
        }
    }

    /** 已占用回调槽位后发送终态；成功、永久错误或重试用尽后才移除保底记录。 */
    private fun sendFinish(logId: Long, pending: PendingFinish) {
        if (!flushSynchronously(logId) && pending.flushAttempt < FINISH_FLUSH_MAX_ATTEMPTS) {
            val next = pending.copy(flushAttempt = pending.flushAttempt + 1)
            if (pendingFinishes.replace(logId, pending, next)) return
            retainFinish(logId, next)
            return
        }
        if (pending.flushAttempt >= FINISH_FLUSH_MAX_ATTEMPTS) {
            logger.warn("过程日志持续上报失败，先发送执行结束回调: logId={}, attempts={}", logId, pending.flushAttempt)
        }
        runCatching {
            postJson(
                client,
                ScheduleWebPaths.EXECUTOR_LOG_FINISH.replace("{id}", logId.toString()),
                pending.request
            )
        }.onSuccess {
            removePendingFinish(logId, pending)
        }.onFailure { exception ->
            if (isPermanentHttpFailure(exception) || pending.callbackAttempt >= CALLBACK_MAX_RETRIES) {
                logger.warn("上报执行结束失败并停止重试: logId={}, attempt={}, error={}", logId, pending.callbackAttempt, exception.message)
                removePendingFinish(logId, pending)
            } else {
                val next = pending.copy(callbackAttempt = pending.callbackAttempt + 1)
                if (!pendingFinishes.replace(logId, pending, next)) retainFinish(logId, next)
            }
        }
    }

    /** 下一次定时刷写时尝试把保底终态重新交给有界回调队列。 */
    private fun drainPendingFinishes() {
        pendingFinishes.entries.take(RESERVED_STATUS_CALLBACKS).forEach { (logId, pending) ->
            tryEnqueueFinish(logId, pending)
        }
    }

    private fun enqueueCallbackTask(label: String, task: () -> Unit): Boolean {
        if (!reserveCallback(label)) return false
        try {
            callbackExecutor.execute {
                try { task() } finally { pendingCallbacks.decrementAndGet() }
            }
            return true
        } catch (exception: RuntimeException) {
            pendingCallbacks.decrementAndGet()
            logger.warn("提交执行{}回调失败", label, exception)
            return false
        }
    }

    private fun scheduleCallbackTask(label: String, task: () -> Unit): Boolean {
        if (!reserveCallback(label)) return false
        try {
            callbackExecutor.schedule(
                { try { task() } finally { pendingCallbacks.decrementAndGet() } },
                CALLBACK_RETRY_DELAY_MILLIS,
                TimeUnit.MILLISECONDS
            )
            return true
        } catch (exception: RuntimeException) {
            pendingCallbacks.decrementAndGet()
            logger.warn("延迟提交执行{}回调失败", label, exception)
            return false
        }
    }

    private fun reserveCallback(label: String): Boolean {
        // 为 started/finish 等状态回调预留槽位，过程日志不能占满整个回调容量。
        // 所有非 finish 任务共享上限，始终为 finish 预留槽位。
        val limit = if (label == "结束") maxPendingCallbacks else maxPendingCallbacks - RESERVED_STATUS_CALLBACKS
        while (true) {
            val current = pendingCallbacks.get()
            if (current >= limit) {
                logger.warn("回调队列已满，丢弃执行{}回调", label)
                return false
            }
            if (!pendingCallbacks.compareAndSet(current, current + 1)) {
                continue
            }
            break
        }
        return true
    }

    /** 开始/结束回调失败时异步有限重试，不阻塞执行器 JobThread。 */
    private fun postCallbackWithRetry(path: String, body: Any, label: String, attempt: Int = 0) {
        runCatching { postJson(client, path, body) }
            .onSuccess { if (attempt > 0) logger.info("执行{}回调重试成功: path={}, attempt={}", label, path, attempt) }
            .onFailure { exception ->
                if (isPermanentHttpFailure(exception) || attempt >= CALLBACK_MAX_RETRIES) {
                    logger.warn("上报执行{}失败并停止重试: path={}, attempt={}, error={}", label, path, attempt, exception.message)
                } else {
                    scheduleCallbackTask(label) { postCallbackWithRetry(path, body, label, attempt + 1) }
                }
            }
    }

    private enum class PublishResult { SUCCESS, RETRY, DROP }

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

    /** 终态保底重投所需的最小上下文。 */
    private data class PendingFinish(
        val request: LogFinishRequest,
        val flushAttempt: Int,
        val callbackAttempt: Int = 0
    )

}

/** 执行器向调度中心追加业务日志的请求体。 */
data class HandleLogAppendRequest(
    /** 按产生顺序追加的业务日志行；服务端会限制单批和总日志大小。 */
    val lines: List<String>
)

/** 执行器通知调度中心日志开始真正执行。 */
data class LogStartedRequest(
    /** 执行器展示给管理端的开始执行说明。 */
    val message: String = "执行中"
)

/** 执行器在串行放行前同步回写终态。 */
data class LogFinishRequest(
    /** Handler 是否正常返回；与 discarded、cancelled 共同决定最终执行状态。 */
    val success: Boolean,
    /** 可展示的处理结果或失败原因。 */
    val message: String? = null,
    /** 是否因 DISCARD_LATER 等阻塞策略而未实际调用 Handler。 */
    val discarded: Boolean = false,
    /** 是否因管理员取消或 COVER_EARLY 覆盖而终止。 */
    val cancelled: Boolean = false,
    /** 执行器测得的实际耗时毫秒数；排队取消时可为 0 或为空。 */
    val durationMillis: Long? = null
)
