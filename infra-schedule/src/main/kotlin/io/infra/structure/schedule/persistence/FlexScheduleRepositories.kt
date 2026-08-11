package io.infra.structure.schedule.persistence

import com.mybatisflex.kotlin.extensions.condition.and
import com.mybatisflex.kotlin.extensions.db.update
import com.mybatisflex.kotlin.extensions.kproperty.column
import com.mybatisflex.kotlin.extensions.kproperty.eq
import com.mybatisflex.kotlin.extensions.kproperty.ge
import com.mybatisflex.kotlin.extensions.kproperty.le
import com.mybatisflex.kotlin.extensions.mapper.query
import io.infra.structure.schedule.model.BlockStrategy
import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.JobExecutionLog
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleJob
import io.infra.structure.schedule.model.ScheduleType
import io.infra.structure.schedule.persistence.entity.ScheduleExecutionLogEntity
import io.infra.structure.schedule.persistence.entity.ScheduleJobEntity
import io.infra.structure.schedule.persistence.mapper.ScheduleExecutionLogMapper
import io.infra.structure.schedule.persistence.mapper.ScheduleJobMapper
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import org.springframework.transaction.annotation.Transactional

/** 基于 MyBatis-Flex 的任务仓储，领取操作通过条件更新提供跨节点互斥。 */
open class FlexScheduleJobRepository(
    private val jobMapper: ScheduleJobMapper
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

    override fun findById(id: Long): ScheduleJob? = jobMapper.selectOneById(id)?.toModel()

    override fun findAll(): List<ScheduleJob> = jobMapper.query {
        orderBy(ScheduleJobEntity::name.column, true)
    }.map(ScheduleJobEntity::toModel)

    override fun delete(id: Long): Boolean = jobMapper.deleteById(id) > 0

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

    override fun clearClaim(id: Long) {
        update<ScheduleJobEntity> {
            ScheduleJobEntity::claimOwner set null
            ScheduleJobEntity::claimUntil set null
            where(ScheduleJobEntity::id eq id)
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
        where((ScheduleJobEntity::id eq id) and (ScheduleJobEntity::claimOwner eq owner))
    } > 0
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

    override fun update(log: JobExecutionLog) {
        require(log.id > 0) { "更新执行日志需要有效主键" }
        logMapper.update(log.toEntity(), false)
    }

    override fun findByJobId(jobId: Long, limit: Int): List<JobExecutionLog> =
        query(ExecutionLogQuery(jobId = jobId, limit = limit))

    override fun query(query: ExecutionLogQuery): List<JobExecutionLog> {
        val conditions = buildList {
            query.jobId?.let { add(ScheduleExecutionLogEntity::jobId eq it) }
            query.executorId?.let { add(ScheduleExecutionLogEntity::executorId eq it) }
            query.status?.let { add(ScheduleExecutionLogEntity::status eq it.name) }
            query.triggerTimeFrom?.let { add(ScheduleExecutionLogEntity::triggerTime ge it) }
            query.triggerTimeTo?.let { add(ScheduleExecutionLogEntity::triggerTime le it) }
        }
        return logMapper.query {
            if (conditions.isNotEmpty()) {
                where(conditions.reduce { left, right -> left and right })
            }
            orderBy(ScheduleExecutionLogEntity::triggerTime.column, false)
            limit(query.limit.coerceIn(1, 1_000))
        }.map(ScheduleExecutionLogEntity::toModel)
    }
}

private fun ScheduleJob.toEntity() = ScheduleJobEntity(
    id = id.takeIf { it > 0 }, name = name, executorId = executorId, handler = handler, parameters = parameters,
    scheduleType = scheduleType.name, cron = cron, fixedRateMillis = fixedRateMillis, status = status.name,
    routeStrategy = routeStrategy.name, blockStrategy = blockStrategy.name, maxRetryCount = maxRetryCount,
    retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds, nextTriggerAt = nextTriggerAt,
    lastTriggerAt = lastTriggerAt, claimOwner = claimOwner, claimUntil = claimUntil,
    createTime = createTime, updateTime = updateTime
)

private fun ScheduleJobEntity.toModel() = ScheduleJob(
    id = id ?: 0, name = name, executorGroup = "default", executorId = executorId, handler = handler, parameters = parameters,
    scheduleType = ScheduleType.valueOf(scheduleType), cron = cron, fixedRateMillis = fixedRateMillis,
    status = JobStatus.valueOf(status), routeStrategy = RouteStrategy.valueOf(routeStrategy),
    blockStrategy = BlockStrategy.valueOf(blockStrategy), maxRetryCount = maxRetryCount,
    retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds, nextTriggerAt = nextTriggerAt,
    lastTriggerAt = lastTriggerAt, claimOwner = claimOwner, claimUntil = claimUntil,
    createTime = createTime, updateTime = updateTime
)

private fun JobExecutionLog.toEntity() = ScheduleExecutionLogEntity(
    // 新增日志必须让数据库自增主键生效；传入 0 会被当成显式主键导致后续插入冲突。
    id = id.takeIf { it > 0 }, jobId = jobId, executorId = executorId, triggerTime = triggerTime, finishTime = finishTime,
    status = status.name, retryCount = retryCount, message = message,
    targetAddress = targetAddress, durationMillis = durationMillis
)

private fun ScheduleExecutionLogEntity.toModel() = JobExecutionLog(
    id = id ?: 0, jobId = jobId, executorId = executorId, triggerTime = triggerTime, finishTime = finishTime,
    status = ExecutionStatus.valueOf(status), retryCount = retryCount, message = message,
    targetAddress = targetAddress, durationMillis = durationMillis
)
