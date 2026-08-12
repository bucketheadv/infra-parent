package io.infra.structure.schedule.service

import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.ExecutorTaskTracker
import io.infra.structure.schedule.core.HttpScheduleCancelClient
import io.infra.structure.schedule.core.RoutedExecutor
import io.infra.structure.schedule.core.ScheduleCalculator
import io.infra.structure.schedule.model.BlockStrategy
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
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.FutureTask
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
    private val running = mutableMapOf<Long, FutureTask<Unit>>()
    private val runningLock = Any()
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
        // 按新 cron/间隔立刻重算下次触发，并清租约，避免仍按旧计划扫描或被进行中的 complete 覆盖定义。
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

    /** 取消当前进程中正在执行的任务，并删除持久化任务定义。 */
    fun delete(id: Long): Boolean {
        synchronized(runningLock) { running.remove(id)?.cancel(true) }
        return jobRepository.delete(id)
    }

    /** 返回管理端可见的全部任务。 */
    fun jobs(): List<ScheduleJob> = jobRepository.findAll()

    /** 获取一个任务；不存在时抛出受控业务错误。 */
    fun job(id: Long): ScheduleJob = requireJob(id)

    /** 查询指定任务近期的执行审计记录。 */
    fun executionLogs(jobId: Long, limit: Int): List<JobExecutionLog> = logRepository.findByJobId(jobId, limit)

    /** 按日志主键查询单条执行记录。 */
    fun executionLog(logId: Long): JobExecutionLog =
        logRepository.findById(logId) ?: error("执行日志不存在: $logId")

    /** 按任务、执行器、状态与触发时间范围查询执行日志。 */
    fun queryExecutionLogs(query: ExecutionLogQuery): List<JobExecutionLog> {
        require(query.triggerTimeFrom == null || query.triggerTimeTo == null || query.triggerTimeFrom <= query.triggerTimeTo) {
            "触发时间范围无效：开始时间不能晚于结束时间"
        }
        return logRepository.query(query.copy(limit = query.limit.coerceIn(1, 1_000)))
    }

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

    /** 忽略任务的定时计划，立即异步提交一次手动执行。 */
    fun triggerNow(id: Long): Boolean {
        val job = requireJob(id)
        return submit(job, System.currentTimeMillis(), scheduled = false)
    }

    /**
     * 真正终止运行中任务：中断调度侧等待线程，并通知执行器按 logId kill handler 线程，最后回写日志。
     * @return false 表示日志不存在于运行中状态
     */
    fun cancelRunningLog(logId: Long): Boolean {
        val log = logRepository.findById(logId) ?: error("执行日志不存在: $logId")
        if (log.status != ExecutionStatus.RUNNING) return false
        logger.warn(
            "管理员中止任务: jobId={}, logId={}, target={}",
            log.jobId,
            log.id,
            log.targetAddress
        )
        // 先落库 CANCELLED，避免中断后执行回写抢先改成 SUCCESS/FAILED 导致此处更新 0 行并误报 409。
        val targets = logRepository.query(
            ExecutionLogQuery(jobId = log.jobId, status = ExecutionStatus.RUNNING, limit = 100)
        )
        val updated = logRepository.cancelRunningByJobId(
            jobId = log.jobId,
            message = "管理员终止执行",
            finishTime = System.currentTimeMillis()
        )
        synchronized(runningLock) {
            running[log.jobId]?.cancel(true)
        }
        targets.forEach { active ->
            attemptFutures.remove(active.id)?.cancel(true)
            killExecutorTask(active)
        }
        if (updated > 0) return true
        val current = logRepository.findById(logId)
        return current != null && current.status != ExecutionStatus.RUNNING
    }

    /** 远程地址走 HTTP cancel；本地执行走同进程 [ExecutorTaskTracker]。 */
    private fun killExecutorTask(log: JobExecutionLog) {
        val target = log.targetAddress?.takeIf { it.isNotBlank() && it != "本地" }
        if (target != null && (target.startsWith("http://") || target.startsWith("https://"))) {
            val ok = cancelClient.cancel(target, log.id)
            logger.warn(
                "已请求远程执行器中止: logId={}, target={}, accepted={}",
                log.id,
                target,
                ok
            )
        } else {
            taskTracker.cancel(log.id)
        }
    }

    /**
     * 由调度线程周期性调用，按页领取并异步提交到期任务。
     *
     * 每页在独立短事务内完成行锁领取，执行过程不持有数据库锁；达到 [maxPages] 后留待下一轮，
     * 让新到期任务也有机会被及时扫描。
     */
    fun dispatchDueJobs(pageSize: Int, maxPages: Int) {
        val now = System.currentTimeMillis()
        repeat(maxPages.coerceAtLeast(1)) {
            val claimed = jobRepository.claimDueJobs(now, pageSize.coerceAtLeast(1), claimLeaseMillis, schedulerId)
            claimed.forEach { job -> submit(job, now, scheduled = true) }
            if (claimed.size < pageSize) return
        }
    }

    /**
     * 依据阻塞策略提交执行任务。
     * 对定时任务，未执行的触发也必须推进下一次时间，避免反复领取相同到期记录。
     */
    private fun submit(job: ScheduleJob, triggerTime: Long, scheduled: Boolean): Boolean {
        synchronized(runningLock) {
            val previous = running[job.id]
            if (previous != null && !previous.isDone) {
                if (job.blockStrategy == BlockStrategy.COVER_EARLY) {
                    previous.cancel(true)
                } else {
                    // 常驻任务在串行跳过 / 丢弃后续时不写跳过日志，避免长驻运行刷屏。
                    if (!job.resident) {
                        appendSkipped(job, triggerTime, "任务正在执行，按 ${job.blockStrategy} 策略跳过本次触发")
                    }
                    if (scheduled) completeSchedule(job, triggerTime)
                    return false
                }
            }
            lateinit var task: FutureTask<Unit>
            task = FutureTask {
                try {
                    execute(job, triggerTime)
                } catch (exception: Exception) {
                    val message = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                    appendFailed(job, null, triggerTime, "任务执行异常: $message")
                } finally {
                    if (scheduled) completeSchedule(job, triggerTime)
                    synchronized(runningLock) {
                        if (running[job.id] === task) running.remove(job.id)
                    }
                }
            }
            running[job.id] = task
            workerExecutor.execute(task)
            return true
        }
    }

    /** 选择执行器并为广播 / 故障转移路由构造对应的执行上下文。 */
    private fun execute(job: ScheduleJob, triggerTime: Long) {
        val executors = resolveExecutors(job)
        if (executors.isEmpty()) {
            val target = job.executorId?.let { "执行器: $it" } ?: "分组: ${job.executorGroup}"
            appendFailed(job, null, triggerTime, "没有可用执行器，$target")
            return
        }
        if (job.routeStrategy == RouteStrategy.FAILOVER) {
            executeWithFailover(job, executors, triggerTime)
            return
        }
        executors.forEachIndexed { index, routed ->
            executeWithRetry(job, routed, triggerTime, shardIndex = index, shardTotal = executors.size)
        }
    }

    /**
     * 解析本次触发应调用的执行器地址节点列表。
     * 任务指定执行器时在其多地址上按路由策略选择；未指定时按分组选择。
     */
    private fun resolveExecutors(job: ScheduleJob): List<RoutedExecutor> {
        val candidates = if (job.executorId != null) {
            executorRegistry.runnableNodes(job.executorId)
        } else {
            executorRegistry.activeRouted(job.executorGroup)
        }
        val cursorKey = job.executorId?.let { "executor:$it" } ?: job.executorGroup
        return executorRegistry.applyRoute(candidates, job.routeStrategy, job.id.toString(), cursorKey)
    }

    /** 按候选节点顺序转移，任一节点成功即结束；全部失败才记最终失败。 */
    private fun executeWithFailover(
        job: ScheduleJob,
        executors: List<RoutedExecutor>,
        triggerTime: Long
    ) {
        var lastMessage = "没有可用执行器"
        var lastExecutorId: Long? = null
        for ((index, routed) in executors.withIndex()) {
            when (
                val outcome = executeWithRetry(
                    job, routed, triggerTime, shardIndex = index, shardTotal = executors.size
                )
            ) {
                AttemptOutcome.Success, AttemptOutcome.Cancelled -> return
                is AttemptOutcome.Failed -> {
                    lastExecutorId = routed.dbId
                    lastMessage = outcome.message
                }
            }
        }
        appendFailed(job, lastExecutorId, triggerTime, "故障转移耗尽全部候选节点：$lastMessage", job.maxRetryCount)
    }

    /** 在同一执行器上完成一次任务调用及其配置的重试次数；开始即记运行中，结束回写终态。 */
    private fun executeWithRetry(
        job: ScheduleJob,
        routed: RoutedExecutor,
        triggerTime: Long,
        shardIndex: Int = 0,
        shardTotal: Int = 1
    ): AttemptOutcome {
        // 保留完整地址供远程终止；本地显示为「本地」。
        val storageTarget = routed.address?.takeIf { it.isNotBlank() } ?: "本地"
        val runningLog = logRepository.append(
            JobExecutionLog(
                jobId = job.id,
                executorId = routed.dbId,
                triggerTime = triggerTime,
                status = ExecutionStatus.RUNNING,
                message = "${job.handler} 执行中",
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
                            logId = runningLog.id
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
                    status = ExecutionStatus.SKIPPED,
                    retryCount = attempt,
                    message = "任务执行被取消",
                    targetAddress = lastTarget
                )
                return AttemptOutcome.Cancelled
            } catch (exception: Exception) {
                lastMessage = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                lastTarget = storageTarget
                lastDurationMs = System.currentTimeMillis() - startedAt
            }
            if (attempt < job.maxRetryCount && job.retryIntervalMillis > 0) Thread.sleep(job.retryIntervalMillis)
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
    private fun completeSchedule(job: ScheduleJob, triggerTime: Long) {
        val current = jobRepository.findById(job.id) ?: return
        if (current.status == JobStatus.DISABLED) {
            jobRepository.releaseClaim(job.id, schedulerId)
            return
        }
        if (current.claimOwner != schedulerId) return
        val now = System.currentTimeMillis()
        val base = current.nextTriggerAt ?: triggerTime
        val next = ScheduleCalculator.nextFutureTriggerAt(current, base, now)
        jobRepository.completeSchedule(job.id, schedulerId, triggerTime, next, now)
    }

    private fun appendSkipped(job: ScheduleJob, triggerTime: Long, message: String) {
        logRepository.append(JobExecutionLog(
            jobId = job.id, executorId = null,
            triggerTime = triggerTime, finishTime = System.currentTimeMillis(),
            status = ExecutionStatus.SKIPPED, message = message
        ))
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
