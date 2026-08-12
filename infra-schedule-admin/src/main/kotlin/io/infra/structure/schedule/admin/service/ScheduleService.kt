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
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import io.infra.structure.schedule.repository.StaleRunningLogRef
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** 编排任务定义、分布式领取、执行器分发、重试与下一次触发时间推进。 */
class ScheduleService(
    private val jobRepository: ScheduleJobRepository,
    private val logRepository: ScheduleExecutionLogRepository,
    private val executorRegistry: ExecutorRegistry,
    private val workerExecutor: ExecutorService,
    private val attemptExecutor: ExecutorService,
    private val taskTracker: ExecutorTaskTracker,
    private val cancelClient: HttpScheduleCancelClient,
    private val claimLeaseMillis: Long,
    private val schedulerId: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    /** 调度侧等待执行器返回的 Future，按日志 ID 可中断。 */
    private val attemptFutures = ConcurrentHashMap<Long, Future<*>>()

    /** 校验并创建任务，同时计算首次定时触发时间。 */
    fun create(draft: ScheduleJobDraft): ScheduleJob {
        val now = System.currentTimeMillis()
        val job = buildJob(0, draft, now)
        ScheduleCalculator.validate(job)
        return jobRepository.save(job.copy(nextTriggerAt = initialTriggerAt(job, now)))
    }

    /** 覆盖更新任务可编辑字段，并撤销旧配置可能遗留的租约；不改变当前启停状态。 */
    fun update(id: Long, draft: ScheduleJobDraft): ScheduleJob {
        val existing = requireJob(id)
        val now = System.currentTimeMillis()
        val updated = buildJob(id, draft, now).copy(
            createTime = existing.createTime,
            status = existing.status,
            lastTriggerAt = existing.lastTriggerAt
        )
        ScheduleCalculator.validate(updated)
        val saved = jobRepository.save(
            updated.copy(
                nextTriggerAt = initialTriggerAt(updated, now),
                claimOwner = null,
                claimUntil = null
            )
        )
        jobRepository.clearClaim(id)
        return saved
    }

    /** 启用或停用任务；停用后不再触发新的定时执行。 */
    fun setStatus(id: Long, status: JobStatus): ScheduleJob {
        val current = requireJob(id)
        val now = System.currentTimeMillis()
        val updated = current.copy(
            status = status,
            nextTriggerAt = if (status == JobStatus.ENABLED) {
                ScheduleCalculator.nextFutureTriggerAt(current, now, now)
            } else {
                null
            },
            claimOwner = null,
            claimUntil = null,
            updateTime = now
        )
        val saved = jobRepository.save(updated)
        jobRepository.clearClaim(id)
        return saved
    }

    /** 删除持久化任务定义。 */
    fun delete(id: Long): Boolean = jobRepository.delete(id)

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

    /** 忽略任务的定时计划，立即异步提交一次手动执行（不推进 cron）。 */
    fun triggerNow(id: Long): Boolean {
        val job = requireJob(id)
        reconcileActiveLogs(job.id)
        submit(job, System.currentTimeMillis())
        return true
    }

    /**
     * 终止指定日志对应的一次触发（排队或运行中），不影响同任务其他日志。
     * 先落库再中断调度侧 Future / 通知执行器，避免回写竞态。
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
        val finishTime = System.currentTimeMillis()
        val updated = logRepository.updateIfRunning(
            log.copy(
                status = ExecutionStatus.CANCELLED,
                message = "管理员终止执行",
                finishTime = finishTime
            )
        )
        attemptFutures.remove(logId)?.cancel(true)
        killExecutorTask(log)
        if (updated) return true
        val current = logRepository.findById(logId)
        return current != null && !current.status.isActive()
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
        val current = logRepository.findById(logId) ?: return false
        if (!current.status.isActive()) return false
        val request = LogFinishRequest(
            success = success,
            message = message,
            discarded = discarded,
            cancelled = cancelled,
            durationMillis = durationMillis
        )
        return logRepository.updateIfRunning(
            current.copy(
                finishTime = System.currentTimeMillis(),
                status = ScheduleLogReporter.finishStatus(request),
                message = ScheduleLogReporter.finishMessage(request),
                durationMillis = durationMillis
            )
        )
    }

    /** 远程地址走 HTTP cancel；无 HTTP 地址时走本进程 [ExecutorTaskTracker]。 */
    private fun killExecutorTask(log: JobExecutionLog) {
        val probeUrl = resolveExecutorProbeUrl(log.targetAddress)
        if (probeUrl == null) {
            taskTracker.cancel(log.id)
            logger.warn("已请求本地执行器中止: logId={}", log.id)
            return
        }
        val ok = cancelClient.cancel(probeUrl, log.id)
        logger.warn(
            "已请求远程执行器中止: logId={}, target={}, accepted={}",
            log.id,
            probeUrl,
            ok
        )
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
            val claimed = jobRepository.claimDueJobs(now, pageSize.coerceAtLeast(1), claimLeaseMillis, schedulerId)
            claimed.forEach { job ->
                val triggerTime = job.nextTriggerAt ?: now
                if (!completeSchedule(job, triggerTime)) {
                    logger.warn("推进调度进度失败（租约可能已丢失）: jobId={}", job.id)
                    return@forEach
                }
                reconcileActiveLogs(job.id)
                submit(job, triggerTime)
            }
            if (claimed.size < pageSize) return
        }
    }

    /**
     * 回收长时间停留在 QUEUED/RUNNING 的僵尸执行日志。
     *
     * 无论是否常驻，都会先向目标节点按 logId 探活：
     * - 仍在跑或排队：跳过
     * - 明确不在跑 / 节点不可达：标记 LOST
     *
     * 回收只改日志状态，不会 kill 执行进程。
     */
    fun reapStaleRunningLogs(staleAfterMillis: Long, batchSize: Int): Int {
        val now = System.currentTimeMillis()
        val threshold = staleAfterMillis.coerceAtLeast(claimLeaseMillis).coerceAtLeast(60_000L)
        val staleBefore = now - threshold
        val candidates = logRepository.findStaleRunningCandidates(
            staleBeforeTriggerTime = staleBefore,
            limit = batchSize.coerceIn(1, 1_000)
        )
        if (candidates.isEmpty()) return 0
        val message = "执行日志超时回收（目标进程已不存在或节点不可达，阈值 ${threshold}ms）"
        var reaped = 0
        for (candidate in candidates) {
            if (isLogStillAlive(candidate.id, candidate.targetAddress)) {
                continue
            }
            if (logRepository.markLostIfActive(candidate.id, now, message)) {
                reaped++
            }
        }
        if (reaped > 0) {
            logger.warn("已回收僵尸活跃日志 {} 条（trigger_time <= {}）", reaped, staleBefore)
        }
        return reaped
    }

    /**
     * 每次调度前对该任务全部 QUEUED/RUNNING 日志探活，回收不可观测的僵尸记录。
     */
    private fun reconcileActiveLogs(jobId: Long): Int {
        val actives = logRepository.findActiveByJobId(jobId, 100)
        if (actives.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val message = "调度前探活回收（目标进程已不存在或节点不可达）"
        var reaped = 0
        for (log in actives) {
            if (isLogStillAlive(log.id, log.targetAddress)) {
                continue
            }
            attemptFutures.remove(log.id)?.cancel(true)
            if (logRepository.markLostIfActive(log.id, now, message)) {
                reaped++
                logger.warn(
                    "调度前回收僵尸日志: jobId={}, logId={}, status={}, target={}",
                    jobId,
                    log.id,
                    log.status,
                    log.targetAddress
                )
            }
        }
        return reaped
    }

    /**
     * 查询目标节点上该 logId 是否仍在执行或排队。
     * 远程走执行器 `/running`；本地查 [ExecutorTaskTracker] 与调度侧 attempt Future。
     */
    private fun isLogStillAlive(logId: Long, targetAddress: String?): Boolean {
        if (attemptFutures[logId]?.isDone == false) return true
        val probeUrl = resolveExecutorProbeUrl(targetAddress)
        if (probeUrl == null) {
            return taskTracker.isRunning(logId)
        }
        return when (cancelClient.isRunning(probeUrl, logId)) {
            true -> true
            false -> false
            null -> {
                logger.warn("探活执行器不可达，视为日志不可观测: logId={}, target={}", logId, probeUrl)
                false
            }
        }
    }

    /** 将日志中的目标地址解析为可探活的 baseUrl；本地执行返回 null。 */
    private fun resolveExecutorProbeUrl(targetAddress: String?): String? {
        val target = targetAddress?.trim()?.takeIf { it.isNotBlank() && it != "本地" } ?: return null
        return ExecutorAddresses.normalizeHttpBaseUrl(target)
    }

    /** 异步提交一次触发；阻塞策略由执行器 JobThread 解释。 */
    private fun submit(job: ScheduleJob, triggerTime: Long) {
        workerExecutor.execute {
            try {
                execute(job, triggerTime)
            } catch (exception: Exception) {
                val message = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                val closed = logRepository.failRunningByJobAndTrigger(
                    jobId = job.id,
                    triggerTime = triggerTime,
                    message = "任务执行异常: $message",
                    finishTime = System.currentTimeMillis()
                )
                if (closed == 0) {
                    appendFailed(job, null, triggerTime, "任务执行异常: $message")
                }
            }
        }
    }

    /** 选择执行器并构造对应的执行上下文。 */
    private fun execute(job: ScheduleJob, triggerTime: Long) {
        val route = resolveExecutors(job)
        if (route.executors.isEmpty()) {
            val target = job.executorId?.let { "执行器: $it" } ?: "分组: ${job.executorGroup}"
            appendFailed(job, null, triggerTime, route.failureReason ?: "没有可用执行器，$target")
            return
        }
        route.executors.forEachIndexed { index, routed ->
            executeWithRetry(job, routed, triggerTime, shardIndex = index, shardTotal = route.executors.size)
        }
    }

    private data class ExecutorRouteResult(
        val executors: List<RoutedExecutor>,
        val failureReason: String? = null
    )

    /**
     * 解析本次触发应调用的执行器地址节点列表。
     * FAILOVER / BUSYOVER 在有序候选上探活后只返回首个可用节点。
     */
    private fun resolveExecutors(job: ScheduleJob): ExecutorRouteResult {
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
            RouteStrategy.FAILOVER -> selectFailover(routed, target)
            RouteStrategy.BUSYOVER -> selectBusyover(routed, job.id, target)
            else -> ExecutorRouteResult(routed)
        }
    }

    private fun selectFailover(candidates: List<RoutedExecutor>, target: String): ExecutorRouteResult {
        var unreachable = 0
        for (node in candidates) {
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
        return ExecutorRouteResult(emptyList(), reason)
    }

    private fun selectBusyover(candidates: List<RoutedExecutor>, jobId: Long, target: String): ExecutorRouteResult {
        var busy = false
        var unreachable = 0
        for (node in candidates) {
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
        return ExecutorRouteResult(emptyList(), reason)
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
        routed: RoutedExecutor,
        triggerTime: Long,
        shardIndex: Int = 0,
        shardTotal: Int = 1
    ): AttemptOutcome {
        val storageTarget = routed.address?.takeIf { it.isNotBlank() } ?: "本地"
        val runningLog = logRepository.append(
            JobExecutionLog(
                jobId = job.id,
                executorId = routed.dbId,
                triggerTime = triggerTime,
                status = ExecutionStatus.QUEUED,
                message = "${job.handler} 排队等待执行",
                targetAddress = storageTarget
            )
        )
        var lastMessage = ""
        var lastTarget: String? = storageTarget
        var lastDurationMs: Long? = null
        for (attempt in 0..job.maxRetryCount) {
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
                            blockStrategy = job.blockStrategy
                        )
                    )
                }
                attemptFutures[runningLog.id] = future
                val result = try {
                    if (job.timeoutSeconds > 0) {
                        future.get(job.timeoutSeconds, TimeUnit.SECONDS)
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
                // 管理员终止等已把日志收口为非 RUNNING 时，禁止再重试。
                if (isLogNoLongerRunning(runningLog.id)) {
                    return AttemptOutcome.Cancelled
                }
                lastMessage = result.message ?: "任务处理器返回失败"
                lastTarget = storageTarget
                lastDurationMs = durationMs
            } catch (_: TimeoutException) {
                future?.cancel(true)
                killExecutorTask(runningLog)
                lastMessage = "任务执行超时（${job.timeoutSeconds} 秒）"
                lastTarget = storageTarget
                lastDurationMs = System.currentTimeMillis() - startedAt
                finishLog(
                    runningLog,
                    status = ExecutionStatus.TIMEOUT,
                    retryCount = attempt,
                    message = lastMessage,
                    targetAddress = lastTarget,
                    durationMillis = lastDurationMs
                )
                return AttemptOutcome.Failed(lastMessage)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                future?.cancel(true)
                killExecutorTask(runningLog)
                finishLog(
                    runningLog,
                    status = ExecutionStatus.CANCELLED,
                    retryCount = attempt,
                    message = "任务执行被取消",
                    targetAddress = lastTarget
                )
                return AttemptOutcome.Cancelled
            } catch (_: CancellationException) {
                killExecutorTask(runningLog)
                finishLog(
                    runningLog,
                    status = ExecutionStatus.CANCELLED,
                    retryCount = attempt,
                    message = "任务执行被取消",
                    targetAddress = lastTarget
                )
                return AttemptOutcome.Cancelled
            } catch (exception: Exception) {
                if (Thread.currentThread().isInterrupted || isLogNoLongerRunning(runningLog.id)) {
                    Thread.interrupted()
                    finishLog(
                        runningLog,
                        status = ExecutionStatus.CANCELLED,
                        retryCount = attempt,
                        message = "任务执行被取消",
                        targetAddress = lastTarget
                    )
                    return AttemptOutcome.Cancelled
                }
                lastMessage = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                lastTarget = storageTarget
                lastDurationMs = System.currentTimeMillis() - startedAt
            }
            if (attempt < job.maxRetryCount) {
                if (isLogNoLongerRunning(runningLog.id)) {
                    return AttemptOutcome.Cancelled
                }
                if (job.retryIntervalMillis > 0) Thread.sleep(job.retryIntervalMillis)
                if (isLogNoLongerRunning(runningLog.id) || Thread.currentThread().isInterrupted) {
                    Thread.interrupted()
                    return AttemptOutcome.Cancelled
                }
            }
        }
        finishLog(
            runningLog,
            status = ExecutionStatus.FAILED,
            retryCount = job.maxRetryCount,
            message = lastMessage,
            targetAddress = lastTarget,
            durationMillis = lastDurationMs
        )
        return AttemptOutcome.Failed(lastMessage)
    }

    /** 日志已被终止/收口为非排队且非运行中，后续不应再重试。 */
    private fun isLogNoLongerRunning(logId: Long): Boolean {
        val status = logRepository.findById(logId)?.status ?: return true
        return !status.isActive()
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
        logRepository.updateIfRunning(
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
    }

    /**
     * 仅由持有租约的节点推进下一次计划，防止租约过期的旧节点覆盖新节点状态。
     * 只更新调度进度字段，避免全量 save 把并发修改的 cron 等定义写回旧值。
     */
    private fun completeSchedule(job: ScheduleJob, triggerTime: Long): Boolean {
        val current = jobRepository.findById(job.id) ?: return false
        if (current.status == JobStatus.DISABLED) {
            jobRepository.releaseClaim(job.id, schedulerId)
            return false
        }
        if (current.claimOwner != schedulerId) return false
        val now = System.currentTimeMillis()
        val base = current.nextTriggerAt ?: triggerTime
        val next = ScheduleCalculator.nextFutureTriggerAt(current, base, now)
        return jobRepository.completeSchedule(job.id, schedulerId, triggerTime, next, now)
    }

    private fun appendFailed(
        job: ScheduleJob,
        executorId: Long?,
        triggerTime: Long,
        message: String,
        retryCount: Int = 0,
        targetAddress: String? = null,
        durationMillis: Long? = null
    ) {
        logRepository.append(JobExecutionLog(
            jobId = job.id, executorId = executorId,
            triggerTime = triggerTime, finishTime = System.currentTimeMillis(),
            status = ExecutionStatus.FAILED, retryCount = retryCount, message = message,
            targetAddress = targetAddress, durationMillis = durationMillis
        ))
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
