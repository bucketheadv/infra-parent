package io.infra.structure.schedule.admin.service

import io.infra.structure.schedule.core.ExecutorAddresses
import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.ExecutorTaskTracker
import io.infra.structure.schedule.admin.core.HttpScheduleCancelClient
import io.infra.structure.schedule.core.LogFinishRequest
import io.infra.structure.schedule.core.RoutedExecutor
import io.infra.structure.schedule.core.ScheduleCalculator
import io.infra.structure.schedule.core.ScheduleLogReporter
import io.infra.structure.schedule.model.ExecutionLogPage
import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionLog
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleJob
import io.infra.structure.schedule.model.ScheduleJobDraft
import io.infra.structure.schedule.model.ScheduleTriggerOutbox
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import io.infra.structure.schedule.repository.ScheduleTriggerOutboxRepository
import io.infra.structure.schedule.repository.StaleRunningLogRef
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicLong

/** 编排任务定义、分布式领取、执行器分发、重试与下一次触发时间推进。 */
class ScheduleService(
    private val jobRepository: ScheduleJobRepository,
    private val logRepository: ScheduleExecutionLogRepository,
    private val triggerOutboxRepository: ScheduleTriggerOutboxRepository,
    private val executorRegistry: ExecutorRegistry,
    private val workerExecutor: ExecutorService,
    private val attemptExecutor: ExecutorService,
    private val taskTracker: ExecutorTaskTracker,
    private val cancelClient: HttpScheduleCancelClient,
    private val claimLeaseMillis: Long,
    private val schedulerId: String,
    private val maxExecutionMillis: Long,
    private val outboxLeaseExecutor: ScheduledExecutorService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    /** 进程生命周期唯一的租约 owner；避免同一 scheduler-id 重启后发生 ABA 误续租。 */
    private val ownerToken = "${schedulerId.trim().ifBlank { "schedule" }.take(80)}-${UUID.randomUUID()}"
    /** 调度侧等待执行器返回的 Future，按日志 ID 可中断。 */
    private val attemptFutures = ConcurrentHashMap<Long, Future<*>>()
    /** 僵尸探活的并发上限，避免大量失联节点把回收线程池和网络连接打满。 */
    private val staleProbePermits = Semaphore(16)
    /** 取消确认与普通僵尸日志各自按主键推进的查询游标，避免总是扫描最老的一页。 */
    private val cancellationProbeCursor = AtomicLong()
    private val staleProbeCursor = AtomicLong()
    /** 取消确认中的远程补偿最早重试时刻，避免每轮扫描都重复发送 cancel。 */
    private val cancellationRetryAt = ConcurrentHashMap<Long, Long>()

    /** 校验并创建任务，同时计算首次定时触发时间。 */
    fun create(draft: ScheduleJobDraft): ScheduleJob {
        val now = System.currentTimeMillis()
        val job = buildJob(0, draft, now)
        ScheduleCalculator.validate(job)
        return jobRepository.save(job.copy(nextTriggerAt = initialTriggerAt(job, now)))
    }

    /** 覆盖更新任务可编辑字段，并撤销旧配置可能遗留的租约；不改变当前启停状态。 */
    fun update(id: Long, draft: ScheduleJobDraft): ScheduleJob {
        val now = System.currentTimeMillis()
        return jobRepository.updateAndCancelPendingOutbox(id, now) { current ->
            val updated = buildJob(id, draft, now).copy(
                createTime = current.createTime,
                status = current.status,
                lastTriggerAt = current.lastTriggerAt
            )
            ScheduleCalculator.validate(updated)
            updated.copy(nextTriggerAt = initialTriggerAt(updated, now), claimOwner = null, claimUntil = null)
        }
    }

    /** 启用或停用任务；停用后不再触发新的定时执行。 */
    fun setStatus(id: Long, status: JobStatus): ScheduleJob {
        val now = System.currentTimeMillis()
        if (status == JobStatus.DISABLED) {
            return jobRepository.updateAndCancelPendingOutbox(id, now) { current ->
                if (current.status == JobStatus.DISABLED) current else current.copy(
                    status = JobStatus.DISABLED,
                    nextTriggerAt = null,
                    claimOwner = null,
                    claimUntil = null,
                    updateTime = now
                )
            }
        }
        return jobRepository.updateLocked(id) { current ->
            checkNotNull(current.executorId) {
                "任务未绑定有效执行器，无法启用: jobId=$id"
            }
            if (current.status == JobStatus.ENABLED) current else current.copy(
                status = JobStatus.ENABLED,
                nextTriggerAt = ScheduleCalculator.nextFutureTriggerAt(current, now, now),
                claimOwner = null,
                claimUntil = null,
                updateTime = now
            )
        }
    }

    /** 删除任务前取消未投递触发与活跃执行，避免删除后仍继续运行。 */
    fun delete(id: Long): Boolean {
        val now = System.currentTimeMillis()
        // 先在事务内锁定并停用任务，撤销 PENDING/PROCESSING Outbox；从这一刻起旧工作线程
        // 即使持有旧任务快照，也无法再通过 appendIfJobEnabled 创建新日志。
        if (!jobRepository.disableAndCancelPendingOutbox(id, now)) return false
        // 删除语义强于暂停：撤销尚未开始的管理员立即执行，当前租约工作线程续租失败后不得再调用执行器。
        triggerOutboxRepository.cancelPendingByJobId(id, now, includeManual = true)
        while (true) {
            val activeLogs = logRepository.findActiveByJobId(id, 1_000)
            if (activeLogs.isEmpty()) break
            activeLogs.forEach { log ->
                // 先进入取消确认中，下一页查询不会重复发送取消；远端未收到中断时仍由僵尸探活
                // 持续观察，不能提前写终态而让仍在运行的任务失去追踪。
                if (logRepository.requestCancellation(log.copy(message = "任务已删除，等待执行器确认终止"), timeout = false)) {
                    attemptFutures.remove(log.id)?.cancel(true)
                    killExecutorTask(log)
                }
            }
            if (activeLogs.size < 1_000) break
        }
        return jobRepository.deleteAndCancelPendingOutbox(id, System.currentTimeMillis())
    }

    /** 返回管理端可见的全部任务。 */
    fun jobs(): List<ScheduleJob> = jobRepository.findAll()

    /** 获取一个任务；不存在时抛出受控业务错误。 */
    fun job(id: Long): ScheduleJob = requireJob(id)

    /** 分页查询指定任务的执行审计记录。 */
    fun executionLogs(jobId: Long, page: Int = 1, pageSize: Int = 20): ExecutionLogPage =
        queryExecutionLogs(ExecutionLogQuery(jobId = jobId), page, pageSize)

    /** 按任务、执行器、状态与触发时间范围分页查询执行日志。 */
    fun queryExecutionLogs(
        query: ExecutionLogQuery,
        page: Int = 1,
        pageSize: Int = query.limit
    ): ExecutionLogPage {
        val triggerTimeFrom = query.triggerTimeFrom
        val triggerTimeTo = query.triggerTimeTo
        require(triggerTimeFrom == null || triggerTimeTo == null || triggerTimeFrom <= triggerTimeTo) {
            "触发时间范围无效：开始时间不能晚于结束时间"
        }
        val size = pageSize.coerceIn(1, 1_000)
        val currentPage = page.coerceAtLeast(1)
        val offset = (currentPage - 1) * size
        val filter = query.copy(offset = 0, limit = size)
        val total = logRepository.count(filter)
        val items = if (total == 0L) {
            emptyList()
        } else {
            logRepository.query(filter.copy(offset = offset))
        }
        return ExecutionLogPage(items = items, total = total, page = currentPage, pageSize = size)
    }

    /** 按日志主键查询单条执行记录。 */
    fun executionLog(logId: Long): JobExecutionLog =
        logRepository.findById(logId) ?: error("执行日志不存在: $logId")

    /** 追加业务执行过程日志（执行器异步上报）。 */
    fun appendHandleLog(logId: Long, lines: List<String>): Boolean {
        if (lines.isEmpty()) return logRepository.findById(logId) != null
        val chunk = lines.joinToString(separator = "") { line ->
            if (line.endsWith("\n")) line else "$line\n"
        }
        return logRepository.appendHandleLog(logId, chunk)
    }

    /** 预览任务接下来若干次调度时间，供管理端展示。 */
    fun nextTriggerTimes(id: Long, count: Int = 10): List<Long> {
        val job = requireJob(id)
        ScheduleCalculator.validate(job)
        return ScheduleCalculator.nextTriggerTimes(job, System.currentTimeMillis(), count.coerceIn(1, 100))
    }

    /** 按尚未保存的调度配置预览接下来若干次调度时间。 */
    fun previewNextTriggerTimes(draft: ScheduleJobDraft, count: Int = 10): List<Long> {
        val job = buildJob(0, draft, System.currentTimeMillis())
        ScheduleCalculator.validate(job)
        return ScheduleCalculator.nextTriggerTimes(job, System.currentTimeMillis(), count.coerceIn(1, 100))
    }

    /** 忽略任务的定时计划，写入可靠 Outbox 后立即异步执行（不推进 cron，暂停任务同样允许）。 */
    fun triggerNow(id: Long): Boolean {
        val now = System.currentTimeMillis()
        return jobRepository.enqueueManual(
            ScheduleTriggerOutbox(
                jobId = id,
                triggerTime = now,
                manualTrigger = true,
                createTime = now,
                updateTime = now
            )
        )
    }

    /**
     * 终止指定日志对应的一次触发（排队或运行中），不影响同任务其他日志。
     * 先记录取消确认状态再中断调度侧 Future / 通知执行器，避免回写竞态。
     * @return false 表示该日志当前不在排队/运行中
     */
    fun cancelRunningLog(logId: Long): Boolean {
        val log = logRepository.findById(logId) ?: error("执行日志不存在: $logId")
        if (!log.status.isActive()) return false
        logger.warn(
            "管理员中止单次执行: jobId={}, logId={}, status={}, target={}",
            log.jobId,
            log.id,
            log.status,
            log.targetAddress
        )
        val updated = logRepository.requestCancellation(
            log.copy(message = "管理员终止执行，等待执行器确认终止"), timeout = false
        )
        if (updated) {
            attemptFutures.remove(logId)?.cancel(true)
            killExecutorTask(log)
            return true
        }
        val current = logRepository.findById(logId)
        return current?.status == ExecutionStatus.CANCELLING ||
            current?.status == ExecutionStatus.TIMING_OUT ||
            current?.status?.isActive() == false
    }

    /** 执行器回调：排队日志进入真正执行。 */
    fun markExecutionStarted(logId: Long, message: String): Boolean =
        logRepository.markRunningIfQueued(logId, message.ifBlank { "执行中" })

    /**
     * 执行器在串行放行下一票前同步回写终态。
     * 调度侧随后的 finishLog 若已非 active 则自然跳过。
     */
    fun completeExecutionFromExecutor(
        logId: Long,
        success: Boolean,
        message: String?,
        discarded: Boolean,
        cancelled: Boolean,
        durationMillis: Long?
    ): Boolean {
        val request = LogFinishRequest(
            success = success,
            message = message,
            discarded = discarded,
            cancelled = cancelled,
            durationMillis = durationMillis
        )
        // 不能先读再写：管理员超时请求可能恰好发生在两步之间。仓储使用单条条件 UPDATE
        // 根据数据库当前状态决定最终状态，TIMING_OUT 始终保持 TIMEOUT 语义。
        val completed = logRepository.finishFromExecutor(
            JobExecutionLog(
                id = logId,
                jobId = 0,
                executorId = null,
                triggerTime = 0,
                finishTime = System.currentTimeMillis(),
                status = ScheduleLogReporter.finishStatus(request),
                message = ScheduleLogReporter.finishMessage(request),
                durationMillis = durationMillis
            ),
            timeoutMessage = "任务执行超时，执行器已确认终止"
        )
        if (completed) cancellationRetryAt.remove(logId)
        return completed
    }

    /** 远程地址走 HTTP cancel；无 HTTP 地址时走本进程 [ExecutorTaskTracker]。 */
    private fun killExecutorTask(log: JobExecutionLog) {
        killExecutorTask(log.id, log.targetAddress)
    }

    /** 以日志 ID 与落库目标地址重发取消请求，避免任务定义删除后丢失可定位的执行器。 */
    private fun killExecutorTask(logId: Long, targetAddress: String?): Boolean {
        val probeUrl = resolveExecutorProbeUrl(targetAddress)
        if (probeUrl == null) {
            val cancelled = taskTracker.cancel(logId)
            logger.warn("已请求本地执行器中止: logId={}, accepted={}", logId, cancelled)
            return cancelled
        }
        val ok = cancelClient.cancel(probeUrl, logId)
        logger.warn(
            "已请求远程执行器中止: logId={}, target={}, accepted={}",
            logId,
            probeUrl,
            ok
        )
        return ok
    }

    /**
     * 由调度线程周期性调用，按页领取并异步提交到期任务。
     *
     * 对齐 xxl-job：领取后**立即推进**下次触发时间，执行中仍可产生重叠触发，
     * 由执行器侧阻塞策略（SERIAL / DISCARD_LATER / COVER_EARLY）处理。
     */
    fun dispatchDueJobs(pageSize: Int, maxPages: Int) {
        val now = System.currentTimeMillis()
        repeat(maxPages.coerceAtLeast(1)) {
            val claimed = jobRepository.claimDueJobs(now, pageSize.coerceAtLeast(1), claimLeaseMillis, ownerToken)
            claimed.forEach { job ->
                val triggerTime = job.nextTriggerAt ?: now
                if (!completeScheduleAndEnqueue(job, triggerTime)) {
                    logger.warn("推进调度进度失败（租约可能已丢失）: jobId={}", job.id)
                    return@forEach
                }
            }
            if (claimed.size < pageSize) return
        }
    }

    /** 从可靠 Outbox 领取已提交触发并交给本节点工作线程。 */
    fun dispatchTriggerOutbox(pageSize: Int, maxPages: Int) {
        val now = System.currentTimeMillis()
        repeat(maxPages.coerceAtLeast(1)) {
            val claimed = triggerOutboxRepository.claimPending(now, pageSize.coerceIn(1, 1_000), claimLeaseMillis, ownerToken)
            claimed.forEach { outbox ->
                val job = jobRepository.findById(outbox.jobId)
                if (job == null || (job.status != JobStatus.ENABLED && !outbox.manualTrigger)) {
                    triggerOutboxRepository.cancelPendingByJobId(
                        outbox.jobId,
                        System.currentTimeMillis(),
                        includeManual = job == null
                    )
                    return@forEach
                }
                if (submit(job, outbox)) {
                    Unit
                } else {
                    triggerOutboxRepository.releaseForRetry(
                        outbox.id, ownerToken, requireNotNull(outbox.claimToken), "调度工作线程拒绝执行", retryAt(outbox.attemptCount)
                    )
                }
            }
            if (claimed.size < pageSize) return
        }
    }

    /** 分批清理超过保留期且已终态的日志。 */
    fun cleanupFinishedLogs(retentionMillis: Long, batchSize: Int): Int {
        if (retentionMillis <= 0) return 0
        return logRepository.deleteFinishedBefore(
            System.currentTimeMillis() - retentionMillis,
            batchSize.coerceIn(1, 10_000)
        )
    }

    /** 分批清理已投递或已取消的历史 Outbox，避免可靠投递表无限增长。 */
    fun cleanupCompletedOutbox(retentionMillis: Long, batchSize: Int): Int {
        if (retentionMillis <= 0) return 0
        return triggerOutboxRepository.deleteCompletedBefore(
            System.currentTimeMillis() - retentionMillis,
            batchSize.coerceIn(1, 10_000)
        )
    }

    /**
     * 回收长时间停留在活跃状态的僵尸执行日志。
     *
     * 无论是否常驻，都会先向目标节点按 logId 探活：
     * - 仍在跑或排队：跳过
     * - 明确不在跑：标记 LOST
     * - 节点不可达或协议未知：保留记录，等待后续探活确认
     *
     * 回收只改日志状态，不会 kill 执行进程。
     */
    fun reapStaleRunningLogs(staleAfterMillis: Long, batchSize: Int): Int {
        val now = System.currentTimeMillis()
        val threshold = staleAfterMillis.coerceAtLeast(claimLeaseMillis).coerceAtLeast(60_000L)
        val staleBefore = now - threshold
        val limit = batchSize.coerceIn(1, 1_000)
        // 取消确认中的任务必须立即探活并补偿 cancel，不能等待默认十分钟的僵尸阈值。
        // 给普通僵尸回收保留至少一半额度，避免大量取消确认记录长期挤占 LOST 识别。
        val cancellationLimit = (limit + 1) / 2
        val cancellationCandidates = scanWithIdCursor(
            cancellationProbeCursor,
            cancellationLimit,
            logRepository::findPendingCancellationCandidates
        )
        val staleCandidates = scanWithIdCursor(staleProbeCursor, limit - cancellationCandidates.size) { afterId, pageSize ->
            logRepository.findStaleRunningCandidates(staleBefore, afterId, pageSize)
        }
        val candidates = cancellationCandidates + staleCandidates
        if (candidates.isEmpty()) return 0
        val message = "执行日志超时回收（已确认目标进程不存在，阈值 ${threshold}ms）"
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        val remainingCandidates = ArrayDeque(candidates)
        val pending = mutableListOf<Pair<StaleRunningLogRef, CompletableFuture<Boolean?>>>()
        fun startNextProbe() {
            val candidate = remainingCandidates.removeFirstOrNull() ?: return
            pending += candidate to CompletableFuture.supplyAsync({
                if (!staleProbePermits.tryAcquire()) return@supplyAsync null
                try {
                    probeLogState(candidate.id, candidate.targetAddress)
                } finally {
                    staleProbePermits.release()
                }
            }, attemptExecutor)
        }
        // 不将整批候选一次性提交后再抢信号量；只维持有限在途请求，完成一个再补一个。
        repeat(minOf(STALE_PROBE_CONCURRENCY, candidates.size)) { startNextProbe() }
        var reaped = 0
        while (pending.isNotEmpty() && System.nanoTime() < deadline) {
            val completed = pending.firstOrNull { it.second.isDone }
            if (completed == null) {
                Thread.sleep(10L)
                continue
            }
            pending.remove(completed)
            val candidate = completed.first
            val state = runCatching { completed.second.get() }.getOrNull()
            when (state) {
                false -> if (closeMissingExecutorLog(candidate, now, message)) reaped++
                true -> retryPendingCancellation(candidate)
                null -> Unit
            }
            startNextProbe()
        }
        pending.forEach { it.second.cancel(true) }
        if (reaped > 0) {
            logger.warn("已回收僵尸活跃日志 {} 条（trigger_time <= {}）", reaped, staleBefore)
        }
        return reaped
    }

    /** 只有执行器明确不存在时，才能收口取消确认或回收真正的僵尸日志。 */
    private fun closeMissingExecutorLog(candidate: StaleRunningLogRef, now: Long, lostMessage: String): Boolean = when (candidate.status) {
        ExecutionStatus.CANCELLING -> logRepository.finishPendingCancellation(
            candidate.id, ExecutionStatus.CANCELLING, ExecutionStatus.CANCELLED, now,
            now - candidate.triggerTime, "执行器已确认终止"
        ).also { if (it) cancellationRetryAt.remove(candidate.id) }
        ExecutionStatus.TIMING_OUT -> logRepository.finishPendingCancellation(
            candidate.id, ExecutionStatus.TIMING_OUT, ExecutionStatus.TIMEOUT, now,
            now - candidate.triggerTime, "任务执行超时，执行器已确认终止"
        ).also { if (it) cancellationRetryAt.remove(candidate.id) }
        else -> logRepository.markLostIfActive(candidate.id, now, lostMessage)
    }

    /**
     * 以主键游标分页；扫描到表尾时回绕到 0，保证高频取消或长期运行记录也能轮换获得探测机会。
     * 游标仅决定扫描公平性，最终收口仍由状态条件更新保证并发安全。
     */
    private fun scanWithIdCursor(
        cursor: AtomicLong,
        limit: Int,
        query: (afterId: Long, pageSize: Int) -> List<StaleRunningLogRef>
    ): List<StaleRunningLogRef> {
        if (limit <= 0) return emptyList()
        val afterId = cursor.get().coerceAtLeast(0)
        var page = query(afterId, limit)
        if (page.isEmpty() && afterId > 0) page = query(0, limit)
        if (page.isNotEmpty()) cursor.set(page.last().id)
        return page
    }

    /** 已确认仍在执行的取消确认记录按退避重发精确 cancel，网络短暂失败不会让任务永久失控。 */
    private fun retryPendingCancellation(candidate: StaleRunningLogRef) {
        if (candidate.status != ExecutionStatus.CANCELLING && candidate.status != ExecutionStatus.TIMING_OUT) return
        val now = System.currentTimeMillis()
        var shouldRetry = false
        cancellationRetryAt.compute(candidate.id) { _, retryAt ->
            if (retryAt == null || retryAt <= now) {
                shouldRetry = true
                now + CANCELLATION_RETRY_INTERVAL_MILLIS
            } else {
                retryAt
            }
        }
        if (!shouldRetry) return
        attemptFutures.remove(candidate.id)?.cancel(true)
        killExecutorTask(candidate.id, candidate.targetAddress)
    }

    /**
     * 查询目标节点上该 logId 是否仍在执行或排队。
     * 远程走执行器 `/running`；本地查 [ExecutorTaskTracker] 与调度侧 attempt Future。
     */
    /** true=确认活跃，false=确认不存在，null=网络/协议未知，未知状态必须保守保留。 */
    private fun probeLogState(logId: Long, targetAddress: String?): Boolean? {
        if (attemptFutures[logId]?.isDone == false) return true
        val probeUrl = resolveExecutorProbeUrl(targetAddress)
        if (probeUrl == null) {
            return taskTracker.isRunning(logId)
        }
        return when (cancelClient.isRunning(probeUrl, logId)) {
            true -> true
            false -> false
            null -> {
                logger.warn("探活执行器不可达，保留活跃日志等待下次确认: logId={}, target={}", logId, probeUrl)
                null
            }
        }
    }

    /** 将日志中的目标地址解析为可探活的 baseUrl；本地执行返回 null。 */
    private fun resolveExecutorProbeUrl(targetAddress: String?): String? {
        val target = targetAddress?.trim()?.takeIf { it.isNotBlank() && it != "本地" } ?: return null
        return ExecutorAddresses.normalizeHttpBaseUrl(target)
    }

    /** 异步提交一次触发；阻塞策略由执行器 JobThread 解释。 */
    private fun submit(job: ScheduleJob, outbox: ScheduleTriggerOutbox): Boolean = try {
        workerExecutor.execute worker@{
            val lease = OutboxLease(outbox)
            // 领取后排队期间可能接近租约边界；工作线程实际开始前先续租，失败则不再发起远程调用。
            if (!lease.renewNow()) {
                logger.warn("Outbox 投递租约已丢失，跳过执行: outboxId={}, jobId={}", outbox.id, outbox.jobId)
                return@worker
            }
            val renewal = renewOutboxClaim(lease)
            try {
                if (lease.lost) return@worker
                when (execute(job, outbox.id, lease.token, outbox.triggerTime, outbox.manualTrigger, lease::renewNow)) {
                    DispatchOutcome.RETRY -> if (!lease.lost) {
                        triggerOutboxRepository.releaseForRetry(
                            outbox.id, ownerToken, lease.token, "执行器调用结果仍未知，等待退避后重投", retryAt(outbox.attemptCount)
                        )
                    }

                    DispatchOutcome.COMPLETE -> if (!lease.lost && !triggerOutboxRepository.markDispatched(
                            outbox.id, ownerToken, lease.token, System.currentTimeMillis()
                        )
                    ) {
                        logger.warn("Outbox 投递完成但确认租约已丢失: outboxId={}, jobId={}", outbox.id, outbox.jobId)
                    }
                }
            } catch (exception: Exception) {
                val message = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                // 不按 jobId + triggerTime 批量收口：广播分片或租约接管时，同一触发时间可能对应多条
                // 独立日志，批量更新会误伤仍由其他节点持有的执行。异常兜底只在当前 Outbox
                // 租约仍有效时追加一条说明；已有日志由自身 finally/僵尸回收负责收口。
                if (!lease.lost) {
                    appendFailed(
                        job, null, outbox.triggerTime, "任务执行异常: $message",
                        outboxId = outbox.id, owner = ownerToken, claimToken = lease.token
                    )
                }
                if (!lease.lost) {
                    triggerOutboxRepository.releaseForRetry(
                        outbox.id, ownerToken, lease.token, "调度执行异常: $message", retryAt(outbox.attemptCount)
                    )
                }
            } finally {
                renewal.cancel(false)
            }
        }
        true
    } catch (exception: java.util.concurrent.RejectedExecutionException) {
        logger.error("调度工作线程拒绝任务: jobId={}, triggerTime={}", job.id, outbox.triggerTime, exception)
        false
    }

    /** 在本节点处理触发期间续租；节点在真正开始前崩溃时，原租约自然过期并可被其他节点恢复。 */
    private inner class OutboxLease(private val outbox: ScheduleTriggerOutbox) {
        val token: String = requireNotNull(outbox.claimToken) { "Outbox 缺少领取令牌: ${outbox.id}" }
        @Volatile var lost: Boolean = false

        /**
         * 在每个即将发起的远程调用前续租并确认本次 token 仍有效。
         * 这样任务被停用、删除或更新而撤销 Outbox 后，工作线程不会继续开始新的投递。
         */
        fun renewNow(): Boolean {
            if (lost) return false
            val now = System.currentTimeMillis()
            val renewed = triggerOutboxRepository.renewClaim(outbox.id, ownerToken, token, now + claimLeaseMillis, now)
            if (!renewed) {
                lost = true
                logger.warn("Outbox 投递租约已丢失: outboxId={}, jobId={}", outbox.id, outbox.jobId)
            }
            return renewed
        }
    }

    /** 续约失败即标记本工作线程失去所有权，禁止进入新的远程调用或重试。 */
    private fun renewOutboxClaim(lease: OutboxLease): ScheduledFuture<*> {
        val periodMillis = (claimLeaseMillis / 3).coerceAtLeast(1_000L)
        return outboxLeaseExecutor.scheduleAtFixedRate({
            lease.renewNow()
        }, periodMillis, periodMillis, TimeUnit.MILLISECONDS)
    }

    /**
     * 计算 Outbox 下一次允许领取的时刻。
     *
     * 初次失败等待 1 秒，随后指数退避，最大 60 秒；指数被限制在安全范围内，避免异常计数导致位移溢出。
     */
    private fun retryAt(attemptCount: Int): Long {
        val exponent = (attemptCount - 1).coerceIn(0, 6)
        val delayMillis = (1_000L shl exponent).coerceAtMost(60_000L)
        return System.currentTimeMillis() + delayMillis
    }

    /** 选择执行器并构造对应的执行上下文。 */
    private fun execute(
        job: ScheduleJob,
        outboxId: Long,
        claimToken: String,
        triggerTime: Long,
        manualTrigger: Boolean,
        isLeaseHeld: () -> Boolean
    ): DispatchOutcome {
        // Outbox 领取和工作线程实际开始之间，任务可能已停用、删除或被编辑。
        // 以数据库中的当前定义为准，避免撤销后仍产生业务副作用。
        val currentJob = jobRepository.findById(job.id)
        if (currentJob == null || (currentJob.status != JobStatus.ENABLED && !manualTrigger)) {
            logger.info("跳过已删除或已停用任务的待投递触发: jobId={}, triggerTime={}", job.id, triggerTime)
            return DispatchOutcome.COMPLETE
        }
        if (!isLeaseHeld()) return DispatchOutcome.COMPLETE
        val route = resolveExecutors(currentJob, isLeaseHeld)
        if (route.executors.isEmpty()) {
            // 路由查询/探活期间租约可能刚好失效；失效后禁止再写失败日志等新副作用。
            if (!isLeaseHeld()) return DispatchOutcome.COMPLETE
            if (route.retryable) return DispatchOutcome.RETRY
            val target = currentJob.executorId?.let { "执行器: $it" } ?: "分组: ${currentJob.executorGroup}"
            appendFailed(
                currentJob, null, triggerTime, route.failureReason ?: "没有可用执行器，$target",
                outboxId = outboxId, owner = ownerToken, claimToken = claimToken
            )
            return DispatchOutcome.COMPLETE
        }
        var shouldRetry = false
        route.executors.forEachIndexed { index, routed ->
            if (!isLeaseHeld()) {
                logger.warn("Outbox 租约已丢失，停止后续执行器投递: jobId={}, triggerTime={}", job.id, triggerTime)
                return DispatchOutcome.COMPLETE
            }
            val outcome = executeWithRetry(
                currentJob, outboxId, claimToken, routed, triggerTime, manualTrigger, shardIndex = index, shardTotal = route.executors.size,
                isLeaseHeld = isLeaseHeld
            )
            if (outcome == AttemptOutcome.Uncertain) shouldRetry = true
        }
        return if (shouldRetry) DispatchOutcome.RETRY else DispatchOutcome.COMPLETE
    }

    private data class ExecutorRouteResult(
        val executors: List<RoutedExecutor>,
        val failureReason: String? = null,
        /** 探活结果未知时必须保留 Outbox，不能把网络故障误判为确定失败。 */
        val retryable: Boolean = false
    )

    /**
     * 解析本次触发应调用的执行器地址节点列表。
     * FAILOVER / BUSYOVER 在有序候选上探活后只返回首个可用节点。
     */
    private fun resolveExecutors(job: ScheduleJob, isLeaseHeld: () -> Boolean = { true }): ExecutorRouteResult {
        if (!isLeaseHeld()) return ExecutorRouteResult(emptyList(), "Outbox 租约已失效")
        val executorId = job.executorId
        val target = executorId?.let { "执行器: $it" } ?: "分组: ${job.executorGroup}"
        val candidates = if (executorId != null) {
            executorRegistry.runnableNodes(executorId)
        } else {
            executorRegistry.activeRouted(job.executorGroup)
        }
        if (candidates.isEmpty()) {
            return ExecutorRouteResult(emptyList(), "没有可用执行器，$target")
        }
        val cursorKey = executorId?.let { "executor:$it" } ?: job.executorGroup
        val routed = executorRegistry.applyRoute(candidates, job.routeStrategy, job.id.toString(), cursorKey)
        if (routed.isEmpty()) {
            return ExecutorRouteResult(emptyList(), "没有可用执行器，$target")
        }
        return when (job.routeStrategy) {
            RouteStrategy.FAILOVER -> selectFailover(routed, target, isLeaseHeld)
            RouteStrategy.BUSYOVER -> selectBusyover(routed, job.id, target, isLeaseHeld)
            else -> ExecutorRouteResult(routed)
        }
    }

    private fun selectFailover(candidates: List<RoutedExecutor>, target: String, isLeaseHeld: () -> Boolean = { true }): ExecutorRouteResult {
        var unreachable = 0
        for (node in candidates) {
            if (!isLeaseHeld()) return ExecutorRouteResult(emptyList(), "Outbox 租约已失效")
            when (probeBeat(node)) {
                true -> return ExecutorRouteResult(listOf(node))
                false -> Unit
                null -> unreachable++
            }
        }
        val reason = if (unreachable == candidates.size) {
            "故障转移：全部 ${candidates.size} 个节点不可达，$target"
        } else {
            "故障转移未找到心跳成功的执行器，$target"
        }
        return ExecutorRouteResult(emptyList(), reason, retryable = unreachable > 0)
    }

    private fun selectBusyover(candidates: List<RoutedExecutor>, jobId: Long, target: String, isLeaseHeld: () -> Boolean = { true }): ExecutorRouteResult {
        var busy = false
        var unreachable = 0
        for (node in candidates) {
            if (!isLeaseHeld()) return ExecutorRouteResult(emptyList(), "Outbox 租约已失效")
            when (probeIdleBeat(node, jobId)) {
                true -> return ExecutorRouteResult(listOf(node))
                false -> busy = true
                null -> unreachable++
            }
        }
        val reason = when {
            unreachable == candidates.size ->
                "忙碌转移：全部 ${candidates.size} 个节点不可达，$target"
            busy ->
                "忙碌转移未找到空闲执行器，$target"
            else ->
                "忙碌转移未找到可用执行器，$target"
        }
        return ExecutorRouteResult(emptyList(), reason, retryable = unreachable > 0)
    }

    private fun probeBeat(routed: RoutedExecutor): Boolean? {
        val probeUrl = resolveExecutorProbeUrl(routed.address) ?: return true
        return cancelClient.beat(probeUrl)
    }

    private fun probeIdleBeat(routed: RoutedExecutor, jobId: Long): Boolean? {
        val probeUrl = resolveExecutorProbeUrl(routed.address)
        if (probeUrl == null) return if (taskTracker.isJobIdle(jobId)) true else false
        return cancelClient.idleBeat(probeUrl, jobId)
    }

    /** 在同一执行器上完成一次任务调用及其配置的重试次数；开始即记运行中，结束回写终态。 */
    private fun executeWithRetry(
        job: ScheduleJob,
        outboxId: Long,
        claimToken: String,
        routed: RoutedExecutor,
        triggerTime: Long,
        manualTrigger: Boolean,
        shardIndex: Int = 0,
        shardTotal: Int = 1,
        isLeaseHeld: () -> Boolean = { true }
    ): AttemptOutcome {
        val storageTarget = routed.address?.takeIf { it.isNotBlank() } ?: "本地"
        var lastMessage = ""
        var lastTarget: String? = storageTarget
        var lastDurationMs: Long? = null
        for (attempt in 0..job.maxRetryCount) {
            if (!isLeaseHeld()) {
                return AttemptOutcome.Cancelled
            }
            // 每个远程调用独占日志 ID。网络响应丢失并重试时，旧执行器实例仍可能完成，
            // 因而不能让新旧尝试共用 logId，否则 finish/cancel/running 探测会互相串台。
            val runningLog = newAttemptLog(job, outboxId, claimToken, routed.dbId, triggerTime, storageTarget, attempt)
                ?: return AttemptOutcome.Cancelled
            var future: Future<io.infra.structure.schedule.model.JobExecutionResult>? = null
            val startedAt = System.currentTimeMillis()
            try {
                future = attemptExecutor.submit<io.infra.structure.schedule.model.JobExecutionResult> {
                    routed.executor.execute(
                        JobExecutionContext(
                            jobId = job.id,
                            jobName = job.name,
                            handler = job.handler,
                            parameters = job.parameters,
                            triggerTime = triggerTime,
                            shardIndex = shardIndex,
                            shardTotal = shardTotal,
                            logId = runningLog.id,
                            blockStrategy = job.blockStrategy,
                            executionTimeoutMillis = effectiveExecutionTimeoutMillis(job)
                        )
                    )
                }
                attemptFutures[runningLog.id] = future
                val result = try {
                    val timeoutMillis = effectiveExecutionTimeoutMillis(job)
                    if (timeoutMillis > 0) {
                        future.get(timeoutMillis, TimeUnit.MILLISECONDS)
                    } else {
                        future.get()
                    }
                } finally {
                    attemptFutures.remove(runningLog.id, future)
                }
                val durationMs = System.currentTimeMillis() - startedAt
                if (result.success) {
                    finishLog(
                        runningLog,
                        status = ExecutionStatus.SUCCESS,
                        retryCount = attempt,
                        message = formatSuccessMessage(job.handler, result.message),
                        targetAddress = storageTarget,
                        durationMillis = durationMs
                    )
                    return AttemptOutcome.Success
                }
                if (result.discarded) {
                    if (job.resident) {
                        logRepository.delete(runningLog.id)
                        return AttemptOutcome.Cancelled
                    }
                    finishLog(
                        runningLog,
                        status = ExecutionStatus.SKIPPED,
                        retryCount = attempt,
                        message = result.message ?: if (job.resident) "常驻任务丢弃后续触发" else "丢弃后续调度",
                        targetAddress = storageTarget,
                        durationMillis = durationMs
                    )
                    return AttemptOutcome.Cancelled
                }
                if (result.cancelled || isAbortMessage(result.message)) {
                    finishLog(
                        runningLog,
                        status = ExecutionStatus.CANCELLED,
                        retryCount = attempt,
                        message = result.message ?: "任务执行被取消",
                        targetAddress = storageTarget,
                        durationMillis = durationMs
                    )
                    return AttemptOutcome.Cancelled
                }
                if (result.uncertain) {
                    // 传输层结果未知时，执行器可能已接收并继续运行。日志必须保持活跃，
                    // 使管理员取消、删除和僵尸探活都能按该次尝试的独立 logId 精确处理。
                    logger.warn(
                        "执行器调用结果未知，保留活跃日志并停止本 Outbox 的后续重试: jobId={}, logId={}, attempt={}",
                        job.id, runningLog.id, attempt
                    )
                    when (probeLogState(runningLog.id, runningLog.targetAddress)) {
                        true -> return AttemptOutcome.Success
                        false -> {
                            // 执行器明确不存在该 logId，当前调用可确定失败；落入下方统一
                            // 收口与重试逻辑，仍遵循任务配置的 maxRetryCount。
                            lastMessage = "执行器未找到该次尝试：${result.message}"
                            lastTarget = storageTarget
                            lastDurationMs = System.currentTimeMillis() - startedAt
                        }
                        null -> return AttemptOutcome.Uncertain
                    }
                }
                // 管理员终止等已进入取消确认状态时，禁止本次调用再发起重试。
                if (isLogNoLongerExecuting(runningLog.id)) {
                    return AttemptOutcome.Cancelled
                }
                if (!result.uncertain) {
                    lastMessage = result.message ?: "任务处理器返回失败"
                    lastTarget = storageTarget
                    lastDurationMs = durationMs
                }
            } catch (_: TimeoutException) {
                lastMessage = "任务执行超时（${effectiveExecutionTimeoutMillis(job)} 毫秒）"
                lastTarget = storageTarget
                lastDurationMs = System.currentTimeMillis() - startedAt
                val cancellationRequested = logRepository.requestCancellation(
                    runningLog.copy(message = "$lastMessage，等待执行器确认终止"), timeout = true
                )
                if (cancellationRequested) {
                    future?.cancel(true)
                    killExecutorTask(runningLog)
                }
                return AttemptOutcome.Cancelled
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                requestExecutionCancellation(runningLog, future, "任务执行被取消")
                return AttemptOutcome.Cancelled
            } catch (_: CancellationException) {
                requestExecutionCancellation(runningLog, future, "任务执行被取消")
                return AttemptOutcome.Cancelled
            } catch (exception: Exception) {
                if (Thread.currentThread().isInterrupted || isLogNoLongerExecuting(runningLog.id)) {
                    Thread.interrupted()
                    requestExecutionCancellation(runningLog, future, "任务执行被取消")
                    return AttemptOutcome.Cancelled
                }
                lastMessage = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                lastTarget = storageTarget
                lastDurationMs = System.currentTimeMillis() - startedAt
            }
            // 本次尝试失败但允许重试时，也必须先独立收口。旧节点即使稍后回调，条件更新
            // 也不会覆盖新尝试的日志；业务重复执行仍由处理器幂等键承担。
            finishLog(
                runningLog,
                status = ExecutionStatus.FAILED,
                retryCount = attempt,
                message = lastMessage.ifBlank { "任务处理器返回失败" },
                targetAddress = lastTarget,
                durationMillis = lastDurationMs
            )
            if (attempt < job.maxRetryCount) {
                if (!isLeaseHeld()) {
                    return AttemptOutcome.Cancelled
                }
                // 当前尝试已经被收口为 FAILED，不能再用它的活跃状态判断是否允许下一次尝试。
                // 只以当前任务定义、Outbox 租约和线程中断状态决定是否继续投递。
                if (jobRepository.findById(job.id)?.let { it.status == JobStatus.ENABLED || manualTrigger } != true) {
                    return AttemptOutcome.Cancelled
                }
                if (job.retryIntervalMillis > 0) Thread.sleep(job.retryIntervalMillis)
                if (!isLeaseHeld() ||
                    jobRepository.findById(job.id)?.let { it.status == JobStatus.ENABLED || manualTrigger } != true ||
                    Thread.currentThread().isInterrupted
                ) {
                    Thread.interrupted()
                    return AttemptOutcome.Cancelled
                }
            }
        }
        return AttemptOutcome.Failed(lastMessage)
    }

    /** 创建一次投递尝试的独立日志；该日志 ID 是执行器取消、探活与回调的唯一关联键。 */
    private fun newAttemptLog(
        job: ScheduleJob,
        outboxId: Long,
        claimToken: String,
        executorId: Long?,
        triggerTime: Long,
        targetAddress: String,
        attempt: Int
    ): JobExecutionLog? = logRepository.appendIfJobEnabled(
        JobExecutionLog(
            jobId = job.id,
            executorId = executorId,
            // 僵尸回收基于日志的 triggerTime；这里必须记录实际尝试创建时刻，避免长重试
            // 间隔后新日志因沿用原始计划时间而在 /run 之前被错误回收为 LOST。
            triggerTime = System.currentTimeMillis(),
            status = ExecutionStatus.QUEUED,
            retryCount = attempt,
            message = "${job.handler} 第 ${attempt + 1} 次尝试排队等待执行",
            targetAddress = targetAddress
        ),
        outboxId = outboxId,
        owner = ownerToken,
        claimToken = claimToken,
        now = System.currentTimeMillis()
    )

    /** 只要日志不再是可首次执行状态，当前尝试就不能继续重试。 */
    private fun isLogNoLongerExecuting(logId: Long): Boolean {
        val status = logRepository.findById(logId)?.status ?: return true
        return status != ExecutionStatus.QUEUED && status != ExecutionStatus.RUNNING
    }

    /** 先保留取消确认状态，再中断本地等待与远程执行器。 */
    private fun requestExecutionCancellation(
        runningLog: JobExecutionLog,
        future: Future<*>?,
        message: String
    ) {
        if (logRepository.requestCancellation(runningLog.copy(message = "$message，等待执行器确认终止"), timeout = false)) {
            future?.cancel(true)
            killExecutorTask(runningLog)
        }
    }

    /** 兼容未带 cancelled 标记的中止类失败文案。 */
    private fun isAbortMessage(message: String?): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isEmpty()) return false
        return text.contains("任务已被终止") ||
            text.contains("任务执行被取消") ||
            text.contains("block strategy effect：覆盖之前调度")
    }

    /** 将运行中日志回写为终态；若已被管理员终止则不再覆盖。 */
    private fun finishLog(
        runningLog: JobExecutionLog,
        status: ExecutionStatus,
        retryCount: Int,
        message: String?,
        targetAddress: String? = runningLog.targetAddress,
        durationMillis: Long? = null
    ) {
        logRepository.finishIfExecuting(
            runningLog.copy(
                finishTime = System.currentTimeMillis(),
                status = status,
                retryCount = retryCount,
                message = message,
                targetAddress = targetAddress,
                durationMillis = durationMillis
            )
        )
    }

    /** 成功日志：固定打印 handler 名；有返回值时追加。 */
    private fun formatSuccessMessage(handler: String, returnValue: String?): String {
        val base = "$handler 执行成功"
        val value = returnValue?.takeIf { it.isNotBlank() } ?: return base
        return "$base：$value"
    }

    private sealed class AttemptOutcome {
        data object Success : AttemptOutcome()
        data object Cancelled : AttemptOutcome()
        data class Failed(val message: String) : AttemptOutcome()
        /** 执行器调用及探活均未知；由 Outbox 释放后在下一轮至少一次重投。 */
        data object Uncertain : AttemptOutcome()
    }

    /** 本次 Outbox 是确认完成，还是需要因执行器状态未知而恢复待投递。 */
    private enum class DispatchOutcome { COMPLETE, RETRY }

    private companion object {
        /** 单轮同时在途的僵尸探活数量。 */
        const val STALE_PROBE_CONCURRENCY = 16
        /** 取消确认中已确认仍活跃时的最短重发间隔。 */
        const val CANCELLATION_RETRY_INTERVAL_MILLIS = 5_000L
    }

    /**
     * 仅由持有租约的节点推进下一次计划，防止租约过期的旧节点覆盖新节点状态。
     * 只更新调度进度字段，避免全量 save 把并发修改的 cron 等定义写回旧值。
     */
    /** 在推进任务下次触发时间的同一事务中写入 Outbox，消除进程崩溃导致的触发丢失窗口。 */
    private fun completeScheduleAndEnqueue(job: ScheduleJob, triggerTime: Long): Boolean {
        val current = jobRepository.findById(job.id) ?: return false
        if (current.status == JobStatus.DISABLED) {
            jobRepository.releaseClaim(job.id, ownerToken)
            return false
        }
        if (current.claimOwner != ownerToken) return false
        val now = System.currentTimeMillis()
        val base = current.nextTriggerAt ?: triggerTime
        val next = ScheduleCalculator.nextFutureTriggerAt(current, base, now)
        return jobRepository.completeScheduleAndEnqueue(
            id = job.id,
            owner = ownerToken,
            lastTriggerAt = triggerTime,
            nextTriggerAt = next,
            outbox = ScheduleTriggerOutbox(
                jobId = job.id,
                triggerTime = triggerTime,
                createTime = now,
                updateTime = now
            ),
            updateTime = now
        )
    }

    /** 显式任务超时优先；未设置时使用系统上限，避免 Future 无期限阻塞。 */
    private fun effectiveExecutionTimeoutMillis(job: ScheduleJob): Long = when {
        job.timeoutSeconds > 0 -> job.timeoutSeconds.coerceAtMost(Long.MAX_VALUE / 1_000) * 1_000
        maxExecutionMillis > 0 -> maxExecutionMillis
        else -> 0
    }

    private fun appendFailed(
        job: ScheduleJob,
        executorId: Long?,
        triggerTime: Long,
        message: String,
        retryCount: Int = 0,
        targetAddress: String? = null,
        durationMillis: Long? = null,
        outboxId: Long? = null,
        owner: String? = null,
        claimToken: String? = null
    ) {
        logRepository.appendIfJobEnabled(JobExecutionLog(
            jobId = job.id, executorId = executorId,
            triggerTime = triggerTime, finishTime = System.currentTimeMillis(),
            status = ExecutionStatus.FAILED, retryCount = retryCount, message = message,
            targetAddress = targetAddress, durationMillis = durationMillis
        ), outboxId = outboxId, owner = owner, claimToken = claimToken, now = System.currentTimeMillis())
    }

    private fun buildJob(id: Long, draft: ScheduleJobDraft, now: Long) = ScheduleJob(
        id = id, name = draft.name, executorGroup = draft.executorGroup, executorId = draft.executorId, handler = draft.handler,
        parameters = draft.parameters, scheduleType = draft.scheduleType, cron = draft.cron,
        fixedRateMillis = draft.fixedRateMillis, status = draft.status, routeStrategy = draft.routeStrategy,
        blockStrategy = draft.blockStrategy, resident = draft.resident, maxRetryCount = draft.maxRetryCount,
        retryIntervalMillis = draft.retryIntervalMillis, timeoutSeconds = draft.timeoutSeconds,
        createTime = now, updateTime = now
    )

    /** 为启用任务计算严格晚于当前时间的下次触发；禁用任务不维护触发时间。 */
    private fun initialTriggerAt(job: ScheduleJob, now: Long): Long? =
        if (job.status == JobStatus.ENABLED) ScheduleCalculator.nextFutureTriggerAt(job, now, now) else null

    private fun requireJob(id: Long): ScheduleJob = jobRepository.findById(id) ?: error("任务不存在: $id")
}
