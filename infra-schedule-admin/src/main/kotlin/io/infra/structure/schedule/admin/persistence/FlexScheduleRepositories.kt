package io.infra.structure.schedule.admin.persistence

import com.mybatisflex.kotlin.extensions.condition.and
import com.mybatisflex.kotlin.extensions.db.update
import com.mybatisflex.kotlin.extensions.kproperty.column
import com.mybatisflex.kotlin.extensions.kproperty.eq
import com.mybatisflex.kotlin.extensions.kproperty.ge
import com.mybatisflex.kotlin.extensions.kproperty.le
import com.mybatisflex.kotlin.extensions.kproperty.gt
import com.mybatisflex.kotlin.extensions.kproperty.isNull
import com.mybatisflex.kotlin.extensions.mapper.query
import com.mybatisflex.kotlin.scope.queryScope
import io.infra.structure.schedule.model.BlockStrategy
import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.JobExecutionLog
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleJob
import io.infra.structure.schedule.model.ScheduleTriggerOutbox
import io.infra.structure.schedule.model.ScheduleType
import io.infra.structure.schedule.model.TriggerOutboxStatus
import io.infra.structure.schedule.admin.persistence.entity.ScheduleExecutionLogEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleJobEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleTriggerOutboxEntity
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleExecutionLogMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleJobMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleTriggerOutboxMapper
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import io.infra.structure.schedule.repository.ScheduleTriggerOutboxRepository
import io.infra.structure.schedule.repository.StaleRunningLogRef
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/** 基于 MyBatis-Flex 的任务仓储，领取操作通过条件更新提供跨节点互斥。 */
open class FlexScheduleJobRepository(
    private val jobMapper: ScheduleJobMapper,
    private val outboxMapper: ScheduleTriggerOutboxMapper
) : ScheduleJobRepository {
    override fun save(job: ScheduleJob): ScheduleJob {
        val entity = job.toEntity()
        if (job.id == 0L) {
            jobMapper.insert(entity)
            return entity.toModel()
        }
        // 必须写入 null，否则改 cron/停用后旧的 next_trigger_at、租约会残留，调度不会立刻按新配置生效。
        jobMapper.update(entity, false)
        return job
    }

    @Transactional
    override fun updateAndCancelPendingOutbox(
        id: Long,
        now: Long,
        updater: (ScheduleJob) -> ScheduleJob
    ): ScheduleJob {
        val current = jobMapper.lockById(id)?.toModel() ?: error("任务不存在: $id")
        val updated = updater(current)
        require(updated.id == id) { "任务更新主键不匹配: $id" }
        jobMapper.update(updated.toEntity(), false)
        cancelPendingOutbox(id, now)
        return updated
    }

    @Transactional
    override fun updateLocked(id: Long, updater: (ScheduleJob) -> ScheduleJob): ScheduleJob {
        val current = jobMapper.lockById(id)?.toModel() ?: error("任务不存在: $id")
        val updated = updater(current)
        require(updated.id == id) { "任务更新主键不匹配: $id" }
        jobMapper.update(updated.toEntity(), false)
        return updated
    }

    override fun findById(id: Long): ScheduleJob? = jobMapper.selectOneById(id)?.toModel()

    override fun findAll(): List<ScheduleJob> = jobMapper.query {
        orderBy(ScheduleJobEntity::name.column, true)
    }.map(ScheduleJobEntity::toModel)

    override fun countByExecutorId(executorId: Long): Long = jobMapper.countByExecutorId(executorId)

    override fun delete(id: Long): Boolean = jobMapper.deleteById(id) > 0

    /** 先阻断新日志/新投递，随后由服务层在事务外取消已经排队或运行的执行。 */
    @Transactional
    override fun disableAndCancelPendingOutbox(id: Long, now: Long): Boolean {
        if (jobMapper.lockById(id) == null) return false
        val disabled = update<ScheduleJobEntity> {
            ScheduleJobEntity::status set JobStatus.DISABLED.name
            ScheduleJobEntity::nextTriggerAt set null
            ScheduleJobEntity::claimOwner set null
            ScheduleJobEntity::claimUntil set null
            ScheduleJobEntity::updateTime set now
            where(ScheduleJobEntity::id eq id)
        } > 0
        if (disabled) cancelPendingOutbox(id, now)
        return disabled
    }

    /** 删除前持有任务行锁，阻止并发扫描在撤销与删除之间再产生新的 Outbox。 */
    @Transactional
    override fun deleteAndCancelPendingOutbox(id: Long, now: Long): Boolean {
        if (jobMapper.lockById(id) == null) return false
        cancelPendingOutbox(id, now, includeManual = true)
        return jobMapper.deleteById(id) > 0
    }

    /** 暂停或编辑只撤销定时触发；删除任务才撤销已提交的手动触发。 */
    private fun cancelPendingOutbox(jobId: Long, now: Long, includeManual: Boolean = false) {
        update<ScheduleTriggerOutboxEntity> {
            ScheduleTriggerOutboxEntity::status set TriggerOutboxStatus.CANCELLED.name
            ScheduleTriggerOutboxEntity::claimOwner set null
            ScheduleTriggerOutboxEntity::claimToken set null
            ScheduleTriggerOutboxEntity::claimUntil set null
            ScheduleTriggerOutboxEntity::updateTime set now
            where(
                (ScheduleTriggerOutboxEntity::jobId eq jobId) and
                    ((ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PENDING.name)
                        .or(ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PROCESSING.name)) and
                    if (includeManual) {
                        ScheduleTriggerOutboxEntity::id gt 0L
                    } else {
                        ScheduleTriggerOutboxEntity::manualTrigger eq false
                    }
            )
        }
    }

    /**
     * 在同一事务中使用 MySQL 行锁锁定一页候选记录，并立即写入租约。
     * SKIP LOCKED 使并行调度器跳过已锁行；提交后处理器异步执行，不会长期占用数据库锁。
     */
    @Transactional
    override fun claimDueJobs(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleJob> {
        val candidates = jobMapper.lockDuePage(now, limit.coerceAtLeast(1))
        return candidates.map { candidate ->
            check(update<ScheduleJobEntity> {
                ScheduleJobEntity::claimOwner set owner
                ScheduleJobEntity::claimUntil set now + leaseMillis
                ScheduleJobEntity::updateTime set now
                where(ScheduleJobEntity::id eq candidate.id)
            } == 1) { "任务租约更新失败: ${candidate.id}" }
            candidate.toModel().copy(claimOwner = owner, claimUntil = now + leaseMillis, updateTime = now)
        }
    }

    /** 通过 claim_owner 条件限制，只释放调用节点自身持有的租约。 */
    override fun releaseClaim(id: Long, owner: String) {
        update<ScheduleJobEntity> {
            ScheduleJobEntity::claimOwner set null
            ScheduleJobEntity::claimUntil set null
            where((ScheduleJobEntity::id eq id) and (ScheduleJobEntity::claimOwner eq owner))
        }
    }

    /** 只更新调度进度，避免覆盖并发修改中的 cron 等任务定义字段。 */
    override fun completeSchedule(
        id: Long,
        owner: String,
        lastTriggerAt: Long,
        nextTriggerAt: Long,
        updateTime: Long
    ): Boolean = update<ScheduleJobEntity> {
        ScheduleJobEntity::lastTriggerAt set lastTriggerAt
        ScheduleJobEntity::nextTriggerAt set nextTriggerAt
        ScheduleJobEntity::claimOwner set null
        ScheduleJobEntity::claimUntil set null
        ScheduleJobEntity::updateTime set updateTime
        where((ScheduleJobEntity::id eq id) and (ScheduleJobEntity::claimOwner eq owner)
            and (ScheduleJobEntity::claimUntil gt updateTime))
    } > 0

    @Transactional
    override fun completeScheduleAndEnqueue(
        id: Long,
        owner: String,
        lastTriggerAt: Long,
        nextTriggerAt: Long,
        outbox: ScheduleTriggerOutbox,
        updateTime: Long
    ): Boolean {
        // 先以 owner 和未过期租约推进任务；只有成功时才插入 Outbox。二者同处事务，
        // 因此不会出现“计划已跳到下一次但进程在写 Outbox 前崩溃”的触发丢失窗口。
        val advanced = completeSchedule(id, owner, lastTriggerAt, nextTriggerAt, updateTime)
        if (!advanced) return false
        outboxMapper.insert(outbox.toEntity())
        return true
    }

    @Transactional
    override fun enqueueManual(outbox: ScheduleTriggerOutbox): Boolean {
        val job = jobMapper.lockById(outbox.jobId) ?: return false
        outboxMapper.insert(outbox.toEntity())
        return true
    }
}

/** 基于 MyBatis-Flex 的执行日志仓储；支持运行中记录的终态回写。 */
class FlexScheduleExecutionLogRepository(
    private val logMapper: ScheduleExecutionLogMapper
) : ScheduleExecutionLogRepository {
    override fun append(log: JobExecutionLog): JobExecutionLog {
        val entity = log.toEntity()
        logMapper.insert(entity)
        return entity.toModel()
    }

    override fun appendIfJobEnabled(
        log: JobExecutionLog,
        outboxId: Long?,
        owner: String?,
        claimToken: String?,
        now: Long?
    ): JobExecutionLog? {
        val entity = log.toEntity()
        return if (logMapper.insertIfJobEnabled(entity, outboxId, owner, claimToken, now) > 0) entity.toModel() else null
    }

    override fun findById(id: Long): JobExecutionLog? =
        logMapper.selectOneById(id)?.toModel()

    override fun update(log: JobExecutionLog) {
        require(log.id > 0) { "更新执行日志需要有效主键" }
        logMapper.update(log.toEntity(), false)
    }

    override fun delete(id: Long): Boolean = logMapper.deleteById(id) > 0

    override fun finishIfExecuting(log: JobExecutionLog): Boolean {
        require(log.id > 0) { "更新执行日志需要有效主键" }
        return update<ScheduleExecutionLogEntity> {
            ScheduleExecutionLogEntity::executorId set log.executorId
            ScheduleExecutionLogEntity::finishTime set log.finishTime
            ScheduleExecutionLogEntity::status set log.status.name
            ScheduleExecutionLogEntity::retryCount set log.retryCount
            ScheduleExecutionLogEntity::message set log.message
            ScheduleExecutionLogEntity::targetAddress set log.targetAddress
            ScheduleExecutionLogEntity::durationMillis set log.durationMillis
            where(
                (ScheduleExecutionLogEntity::id eq log.id) and
                    (
                        (ScheduleExecutionLogEntity::status eq ExecutionStatus.RUNNING.name)
                            .or(ScheduleExecutionLogEntity::status eq ExecutionStatus.QUEUED.name)
                        )
            )
        } > 0
    }

    override fun requestCancellation(log: JobExecutionLog, timeout: Boolean): Boolean {
        require(log.id > 0) { "中止执行日志需要有效主键" }
        return update<ScheduleExecutionLogEntity> {
            ScheduleExecutionLogEntity::status set if (timeout) {
                ExecutionStatus.TIMING_OUT.name
            } else {
                ExecutionStatus.CANCELLING.name
            }
            ScheduleExecutionLogEntity::message set log.message
            where(
                (ScheduleExecutionLogEntity::id eq log.id) and
                    (
                        (ScheduleExecutionLogEntity::status eq ExecutionStatus.RUNNING.name)
                            .or(ScheduleExecutionLogEntity::status eq ExecutionStatus.QUEUED.name)
                    )
            )
        } > 0
    }

    override fun finishPendingCancellation(
        id: Long,
        pendingStatus: ExecutionStatus,
        finalStatus: ExecutionStatus,
        finishTime: Long,
        durationMillis: Long,
        message: String
    ): Boolean = update<ScheduleExecutionLogEntity> {
        ScheduleExecutionLogEntity::status set finalStatus.name
        ScheduleExecutionLogEntity::finishTime set finishTime
        ScheduleExecutionLogEntity::durationMillis set durationMillis
        ScheduleExecutionLogEntity::message set message
        where(
            (ScheduleExecutionLogEntity::id eq id) and
                (ScheduleExecutionLogEntity::status eq pendingStatus.name)
        )
    } > 0

    override fun finishFromExecutor(log: JobExecutionLog, timeoutMessage: String): Boolean {
        require(log.id > 0) { "更新执行日志需要有效主键" }
        return logMapper.finishFromExecutor(log.toEntity(), timeoutMessage) > 0
    }

    override fun markRunningIfQueued(logId: Long, message: String): Boolean =
        update<ScheduleExecutionLogEntity> {
            ScheduleExecutionLogEntity::status set ExecutionStatus.RUNNING.name
            ScheduleExecutionLogEntity::message set message
            where(
                (ScheduleExecutionLogEntity::id eq logId) and
                    (ScheduleExecutionLogEntity::status eq ExecutionStatus.QUEUED.name)
            )
        } > 0

    /** 追加执行器上报的业务日志。 */
    override fun appendHandleLog(logId: Long, chunk: String): Boolean = logMapper.appendHandleLog(logId, chunk) > 0

    override fun findPendingCancellationCandidates(afterId: Long, limit: Int): List<StaleRunningLogRef> =
        logMapper.findPendingCancellationCandidates(afterId.coerceAtLeast(0), limit.coerceIn(1, 1_000))

    override fun findStaleRunningCandidates(staleBeforeTriggerTime: Long, afterId: Long, limit: Int): List<StaleRunningLogRef> =
        logMapper.findStaleRunningCandidates(staleBeforeTriggerTime, afterId.coerceAtLeast(0), limit.coerceAtLeast(1))

    override fun markLostIfActive(id: Long, now: Long, message: String): Boolean =
        logMapper.markLostIfActive(id, now, message) > 0

    override fun findActiveByJobId(jobId: Long, limit: Int): List<JobExecutionLog> =
        logMapper.query {
            where(
                (ScheduleExecutionLogEntity::jobId eq jobId) and
                    (
                        (ScheduleExecutionLogEntity::status eq ExecutionStatus.QUEUED.name)
                            .or(ScheduleExecutionLogEntity::status eq ExecutionStatus.RUNNING.name)
                    )
            )
            orderBy(ScheduleExecutionLogEntity::triggerTime.column, false)
            limit(limit.coerceIn(1, 1_000))
        }.map(ScheduleExecutionLogEntity::toModel)

    override fun findByJobId(jobId: Long, limit: Int): List<JobExecutionLog> =
        query(ExecutionLogQuery(jobId = jobId, limit = limit))

    override fun query(query: ExecutionLogQuery): List<JobExecutionLog> {
        val conditions = buildLogQueryConditions(query)
        val offset = query.offset.coerceAtLeast(0)
        val limit = query.limit.coerceIn(1, 1_000)
        return logMapper.query {
            if (conditions.isNotEmpty()) {
                where(conditions.reduce { left, right -> left and right })
            }
            orderBy(ScheduleExecutionLogEntity::triggerTime.column, false)
            if (offset > 0) {
                offset(offset)
            }
            limit(limit)
        }.map(ScheduleExecutionLogEntity::toModel)
    }

    override fun count(query: ExecutionLogQuery): Long {
        val conditions = buildLogQueryConditions(query)
        return logMapper.selectCountByQuery(queryScope {
            if (conditions.isNotEmpty()) {
                where(conditions.reduce { left, right -> left and right })
            }
        })
    }

    override fun deleteFinishedBefore(finishTimeBefore: Long, limit: Int): Int =
        logMapper.deleteFinishedBefore(finishTimeBefore, limit.coerceIn(1, 10_000))

    private fun buildLogQueryConditions(query: ExecutionLogQuery) = buildList {
        query.jobId?.let { add(ScheduleExecutionLogEntity::jobId eq it) }
        query.executorId?.let { add(ScheduleExecutionLogEntity::executorId eq it) }
        query.status?.let { add(ScheduleExecutionLogEntity::status eq it.name) }
        query.triggerTimeFrom?.let { add(ScheduleExecutionLogEntity::triggerTime ge it) }
        query.triggerTimeTo?.let { add(ScheduleExecutionLogEntity::triggerTime le it) }
    }
}

/**
 * MySQL Outbox 仓储。
 *
 * 类必须为 open：`claimPending` 使用 Spring 事务切面，需要由 CGLIB 创建代理。
 */
open class FlexScheduleTriggerOutboxRepository(
    private val outboxMapper: ScheduleTriggerOutboxMapper
) : ScheduleTriggerOutboxRepository {
    override fun enqueue(outbox: ScheduleTriggerOutbox): ScheduleTriggerOutbox {
        val entity = outbox.toEntity()
        outboxMapper.insert(entity)
        return entity.toModel()
    }

    @Transactional
    override fun claimPending(now: Long, limit: Int, leaseMillis: Long, owner: String): List<ScheduleTriggerOutbox> {
        val candidates = outboxMapper.lockPendingPage(now, limit.coerceIn(1, 1_000))
        return candidates.mapNotNull { candidate ->
            // 每次接管都生成新 token。旧工作线程即使在租约到期后恢复，后续 renew/complete
            // 条件更新也无法匹配新 token，从而不能覆盖新的领取方。
            val claimToken = UUID.randomUUID().toString()
            val claimed = update<ScheduleTriggerOutboxEntity> {
                ScheduleTriggerOutboxEntity::status set TriggerOutboxStatus.PROCESSING.name
                ScheduleTriggerOutboxEntity::claimOwner set owner
                ScheduleTriggerOutboxEntity::claimToken set claimToken
                ScheduleTriggerOutboxEntity::claimUntil set now + leaseMillis
                ScheduleTriggerOutboxEntity::attemptCount set candidate.attemptCount + 1
                ScheduleTriggerOutboxEntity::updateTime set now
                where(
                    (ScheduleTriggerOutboxEntity::id eq candidate.id) and
                        // PENDING 的 claimUntil 是退避截止时间；PROCESSING 的 claimUntil 才是租约截止。
                        // 两类记录都允许在到期后领取，以同时处理失败重试和节点宕机恢复。
                        ((ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PENDING.name)
                            .and(
                                ScheduleTriggerOutboxEntity::claimUntil.isNull
                                    .or(ScheduleTriggerOutboxEntity::claimUntil le now)
                            )
                            .or(
                                (ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PROCESSING.name) and
                                    (ScheduleTriggerOutboxEntity::claimUntil le now)
                            ))
                )
            } > 0
            if (claimed) candidate.toModel().copy(
                status = TriggerOutboxStatus.PROCESSING,
                claimOwner = owner,
                claimToken = claimToken,
                claimUntil = now + leaseMillis,
                attemptCount = candidate.attemptCount + 1,
                updateTime = now
            ) else null
        }
    }

    /** 仅在工作线程完成本次处理后确认投递，避免接收任务后进程崩溃造成静默丢失。 */
    override fun markDispatched(id: Long, owner: String, claimToken: String, now: Long): Boolean = update<ScheduleTriggerOutboxEntity> {
        ScheduleTriggerOutboxEntity::status set TriggerOutboxStatus.DISPATCHED.name
        ScheduleTriggerOutboxEntity::claimOwner set null
        ScheduleTriggerOutboxEntity::claimToken set null
        ScheduleTriggerOutboxEntity::claimUntil set null
        ScheduleTriggerOutboxEntity::lastError set null
        ScheduleTriggerOutboxEntity::updateTime set now
        where(
            // 必须同时持有 owner、token 与未过期租约；避免失去租约的旧节点把接管方的记录
            // 提前标记完成，造成后续触发被静默丢弃。
            (ScheduleTriggerOutboxEntity::id eq id) and
                (ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PROCESSING.name) and
                (ScheduleTriggerOutboxEntity::claimOwner eq owner) and
                (ScheduleTriggerOutboxEntity::claimToken eq claimToken) and
                (ScheduleTriggerOutboxEntity::claimUntil gt now)
        )
    } > 0

    override fun renewClaim(id: Long, owner: String, claimToken: String, claimUntil: Long, now: Long): Boolean =
        update<ScheduleTriggerOutboxEntity> {
            ScheduleTriggerOutboxEntity::claimUntil set claimUntil
            ScheduleTriggerOutboxEntity::updateTime set now
            where(
                // 续租不得“复活”已经到期或已被取消/接管的记录；更新计数为 0 时调用方必须停止投递。
                (ScheduleTriggerOutboxEntity::id eq id) and
                    (ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PROCESSING.name) and
                    (ScheduleTriggerOutboxEntity::claimOwner eq owner) and
                    (ScheduleTriggerOutboxEntity::claimToken eq claimToken) and
                    (ScheduleTriggerOutboxEntity::claimUntil gt now)
            )
        } > 0

    override fun releaseForRetry(id: Long, owner: String, claimToken: String, error: String, retryAt: Long): Boolean {
        val now = System.currentTimeMillis()
        return update<ScheduleTriggerOutboxEntity> {
            ScheduleTriggerOutboxEntity::status set TriggerOutboxStatus.PENDING.name
            ScheduleTriggerOutboxEntity::claimOwner set null
            ScheduleTriggerOutboxEntity::claimToken set null
            // PENDING 状态下 claim_until 表示下次可领取时间；PROCESSING 时才表示租约截止。
            ScheduleTriggerOutboxEntity::claimUntil set retryAt
            ScheduleTriggerOutboxEntity::lastError set error.take(1_000)
            ScheduleTriggerOutboxEntity::updateTime set now
            where(
                // 仅当前 token 可把记录释放回 PENDING，防止旧节点覆盖接管方正在处理的状态。
                (ScheduleTriggerOutboxEntity::id eq id) and
                    (ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PROCESSING.name) and
                    (ScheduleTriggerOutboxEntity::claimOwner eq owner) and
                    (ScheduleTriggerOutboxEntity::claimToken eq claimToken) and
                    (ScheduleTriggerOutboxEntity::claimUntil gt now)
            )
        } > 0
    }

    override fun cancelPendingByJobId(jobId: Long, now: Long, includeManual: Boolean): Int = update<ScheduleTriggerOutboxEntity> {
        ScheduleTriggerOutboxEntity::status set TriggerOutboxStatus.CANCELLED.name
        ScheduleTriggerOutboxEntity::claimOwner set null
        ScheduleTriggerOutboxEntity::claimToken set null
        ScheduleTriggerOutboxEntity::claimUntil set null
        ScheduleTriggerOutboxEntity::updateTime set now
        where(
            (ScheduleTriggerOutboxEntity::jobId eq jobId) and
                ((ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PENDING.name)
                    .or(ScheduleTriggerOutboxEntity::status eq TriggerOutboxStatus.PROCESSING.name)) and
                if (includeManual) {
                    ScheduleTriggerOutboxEntity::id gt 0L
                } else {
                    ScheduleTriggerOutboxEntity::manualTrigger eq false
                }
        )
    }

    override fun deleteCompletedBefore(updateTimeBefore: Long, limit: Int): Int =
        outboxMapper.deleteCompletedBefore(updateTimeBefore, limit.coerceIn(1, 10_000))
}

private fun ScheduleJob.toEntity() = ScheduleJobEntity(
    id = id.takeIf { it > 0 }, name = name, executorId = executorId, handler = handler, parameters = parameters,
    scheduleType = scheduleType.name, cron = cron, fixedRateMillis = fixedRateMillis, status = status.name,
    routeStrategy = routeStrategy.name, blockStrategy = blockStrategy.name, resident = resident,
    maxRetryCount = maxRetryCount, retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds,
    nextTriggerAt = nextTriggerAt, lastTriggerAt = lastTriggerAt, claimOwner = claimOwner, claimUntil = claimUntil,
    createTime = createTime, updateTime = updateTime
)

private fun ScheduleJobEntity.toModel() = ScheduleJob(
    id = id ?: 0, name = name, executorGroup = "default", executorId = executorId, handler = handler, parameters = parameters,
    scheduleType = ScheduleType.valueOf(scheduleType), cron = cron, fixedRateMillis = fixedRateMillis,
    status = JobStatus.valueOf(status), routeStrategy = RouteStrategy.parse(routeStrategy),
    blockStrategy = BlockStrategy.valueOf(blockStrategy), resident = resident, maxRetryCount = maxRetryCount,
    retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds, nextTriggerAt = nextTriggerAt,
    lastTriggerAt = lastTriggerAt, claimOwner = claimOwner, claimUntil = claimUntil,
    createTime = createTime, updateTime = updateTime
)

private fun JobExecutionLog.toEntity() = ScheduleExecutionLogEntity(
    // 新增日志必须让数据库自增主键生效；传入 0 会被当成显式主键导致后续插入冲突。
    id = id.takeIf { it > 0 }, jobId = jobId, executorId = executorId, triggerTime = triggerTime, finishTime = finishTime,
    status = status.name, retryCount = retryCount, message = message, handleLog = handleLog,
    targetAddress = targetAddress, durationMillis = durationMillis
)

private fun ScheduleExecutionLogEntity.toModel() = JobExecutionLog(
    id = id ?: 0, jobId = jobId, executorId = executorId, triggerTime = triggerTime, finishTime = finishTime,
    status = ExecutionStatus.valueOf(status), retryCount = retryCount, message = message, handleLog = handleLog,
    targetAddress = targetAddress, durationMillis = durationMillis
)

private fun ScheduleTriggerOutbox.toEntity() = ScheduleTriggerOutboxEntity(
    id = id.takeIf { it > 0 }, jobId = jobId, triggerTime = triggerTime, manualTrigger = manualTrigger, status = status.name,
    claimOwner = claimOwner, claimToken = claimToken, claimUntil = claimUntil, attemptCount = attemptCount, lastError = lastError,
    createTime = createTime, updateTime = updateTime
)

private fun ScheduleTriggerOutboxEntity.toModel() = ScheduleTriggerOutbox(
    id = id ?: 0, jobId = jobId, triggerTime = triggerTime, manualTrigger = manualTrigger, status = TriggerOutboxStatus.valueOf(status),
    claimOwner = claimOwner, claimToken = claimToken, claimUntil = claimUntil, attemptCount = attemptCount, lastError = lastError,
    createTime = createTime, updateTime = updateTime
)
