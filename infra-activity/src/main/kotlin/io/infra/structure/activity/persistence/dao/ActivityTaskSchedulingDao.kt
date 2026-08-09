package io.infra.structure.activity.persistence.dao

import com.mybatisflex.kotlin.extensions.condition.and
import com.mybatisflex.kotlin.extensions.db.update
import com.mybatisflex.kotlin.extensions.kproperty.column
import com.mybatisflex.kotlin.extensions.kproperty.eq
import com.mybatisflex.kotlin.extensions.kproperty.ge
import com.mybatisflex.kotlin.extensions.kproperty.isNotNull
import com.mybatisflex.kotlin.extensions.kproperty.le
import com.mybatisflex.kotlin.extensions.mapper.query
import com.mybatisflex.kotlin.scope.queryScope
import io.infra.structure.activity.admin.domain.model.ActivityTaskExecutionStatus
import io.infra.structure.activity.admin.domain.model.ActivityTaskStatus
import io.infra.structure.activity.persistence.entity.ActivityTaskInstanceEntity
import io.infra.structure.activity.persistence.entity.ActivityTaskExecutionLogEntity
import io.infra.structure.activity.persistence.entity.ActivityTaskSchedulerInstanceEntity
import io.infra.structure.activity.persistence.mapper.ActivityTaskExecutionLogMapper
import io.infra.structure.activity.persistence.mapper.ActivityTaskInstanceMapper
import io.infra.structure.activity.persistence.mapper.ActivityTaskSchedulerInstanceMapper
import org.springframework.stereotype.Repository

/** 活动任务调度的心跳、租约与状态转换数据访问层。 */
@Repository
class ActivityTaskSchedulingDao(
    private val taskInstanceMapper: ActivityTaskInstanceMapper,
    private val executionLogMapper: ActivityTaskExecutionLogMapper,
    private val schedulerInstanceMapper: ActivityTaskSchedulerInstanceMapper
) {

    /** 注册或刷新当前调度应用实例的心跳。 */
    @Synchronized
    fun heartbeat(instanceId: String, nodeIp: String, now: Long) {
        val existing = schedulerInstanceMapper.selectOneById(instanceId)
        if (existing == null) {
            schedulerInstanceMapper.insert(
                ActivityTaskSchedulerInstanceEntity(
                    instanceId = instanceId,
                    nodeIp = nodeIp,
                    lastHeartbeatTime = now,
                    createTime = now,
                    updateTime = now
                )
            )
            return
        }
        update<ActivityTaskSchedulerInstanceEntity> {
            ActivityTaskSchedulerInstanceEntity::nodeIp set nodeIp
            ActivityTaskSchedulerInstanceEntity::lastHeartbeatTime set now
            ActivityTaskSchedulerInstanceEntity::updateTime set now
            where(ActivityTaskSchedulerInstanceEntity::instanceId eq instanceId)
        }
    }

    /** 判断调度应用实例是否仍在有效心跳窗口内。 */
    fun isInstanceAlive(instanceId: String, minimumHeartbeatTime: Long): Boolean =
        schedulerInstanceMapper.selectCountByQuery(queryScope {
            where(
                (ActivityTaskSchedulerInstanceEntity::instanceId eq instanceId) and
                    (ActivityTaskSchedulerInstanceEntity::lastHeartbeatTime ge minimumHeartbeatTime)
            )
        }) > 0

    /** 查询当前已到期、等待被领取的任务。 */
    fun findDueTasks(now: Long): List<ActivityTaskInstanceEntity> = taskInstanceMapper.query {
        where(
            (ActivityTaskInstanceEntity::status eq ActivityTaskStatus.PENDING.name) and
                (ActivityTaskInstanceEntity::nextTriggerTime le now)
        )
        orderBy(ActivityTaskInstanceEntity::nextTriggerTime.column, true)
    }

    /** 通过条件更新原子领取到期任务，确保多实例只能有一个领取成功。 */
    fun claimDueTask(taskId: Long, owner: String, leaseExpireTime: Long, now: Long): Boolean =
        update<ActivityTaskInstanceEntity> {
            ActivityTaskInstanceEntity::status set ActivityTaskStatus.RUNNING.name
            ActivityTaskInstanceEntity::leaseOwner set owner
            ActivityTaskInstanceEntity::leaseExpireTime set leaseExpireTime
            ActivityTaskInstanceEntity::updateTime set now
            where(
                (ActivityTaskInstanceEntity::id eq taskId) and
                    (ActivityTaskInstanceEntity::status eq ActivityTaskStatus.PENDING.name) and
                    ActivityTaskInstanceEntity::nextTriggerTime.isNotNull and
                    (ActivityTaskInstanceEntity::nextTriggerTime le now)
            )
        } > 0

    /** 查询租约已过期但仍处于执行状态的任务。 */
    fun findExpiredRunningTasks(now: Long): List<ActivityTaskInstanceEntity> = taskInstanceMapper.query {
        where(
            (ActivityTaskInstanceEntity::status eq ActivityTaskStatus.RUNNING.name) and
                ActivityTaskInstanceEntity::leaseExpireTime.isNotNull and
                (ActivityTaskInstanceEntity::leaseExpireTime le now)
        )
    }

    /** 当前租约实例成功后推进任务的下一次调度。 */
    fun completeClaimedTask(
        taskId: Long,
        owner: String,
        status: ActivityTaskStatus,
        nextTriggerTime: Long?,
        triggerTime: Long,
        now: Long
    ): Boolean = update<ActivityTaskInstanceEntity> {
        ActivityTaskInstanceEntity::status set status.name
        ActivityTaskInstanceEntity::nextTriggerTime set nextTriggerTime
        ActivityTaskInstanceEntity::lastTriggerTime set triggerTime
        ActivityTaskInstanceEntity::retryCount set 0
        ActivityTaskInstanceEntity::leaseOwner set null
        ActivityTaskInstanceEntity::leaseExpireTime set null
        ActivityTaskInstanceEntity::updateTime set now
        where(
            (ActivityTaskInstanceEntity::id eq taskId) and
                (ActivityTaskInstanceEntity::status eq ActivityTaskStatus.RUNNING.name) and
                (ActivityTaskInstanceEntity::leaseOwner eq owner)
        )
    } > 0

    /** 当前租约实例失败后安排重试或将任务标记为最终失败。 */
    fun failClaimedTask(
        taskId: Long,
        owner: String,
        status: ActivityTaskStatus,
        nextTriggerTime: Long?,
        triggerTime: Long,
        retryCount: Int,
        now: Long
    ): Boolean = update<ActivityTaskInstanceEntity> {
        ActivityTaskInstanceEntity::status set status.name
        ActivityTaskInstanceEntity::nextTriggerTime set nextTriggerTime
        ActivityTaskInstanceEntity::lastTriggerTime set triggerTime
        ActivityTaskInstanceEntity::retryCount set retryCount
        ActivityTaskInstanceEntity::leaseOwner set null
        ActivityTaskInstanceEntity::leaseExpireTime set null
        ActivityTaskInstanceEntity::updateTime set now
        where(
            (ActivityTaskInstanceEntity::id eq taskId) and
                (ActivityTaskInstanceEntity::status eq ActivityTaskStatus.RUNNING.name) and
                (ActivityTaskInstanceEntity::leaseOwner eq owner)
        )
    } > 0

    /** 心跳失效后原子回收过期租约，并让后续扫描重新领取任务。 */
    fun recoverExpiredClaim(
        task: ActivityTaskInstanceEntity,
        owner: String,
        status: ActivityTaskStatus,
        nextTriggerTime: Long?,
        retryCount: Int,
        now: Long
    ): Boolean = update<ActivityTaskInstanceEntity> {
        ActivityTaskInstanceEntity::status set status.name
        ActivityTaskInstanceEntity::nextTriggerTime set nextTriggerTime
        ActivityTaskInstanceEntity::lastTriggerTime set task.nextTriggerTime
        ActivityTaskInstanceEntity::retryCount set retryCount
        ActivityTaskInstanceEntity::leaseOwner set null
        ActivityTaskInstanceEntity::leaseExpireTime set null
        ActivityTaskInstanceEntity::updateTime set now
        where(
            (ActivityTaskInstanceEntity::id eq requireNotNull(task.id)) and
                (ActivityTaskInstanceEntity::status eq ActivityTaskStatus.RUNNING.name) and
                (ActivityTaskInstanceEntity::leaseOwner eq owner) and
                (ActivityTaskInstanceEntity::leaseExpireTime le now)
        )
    } > 0

    /** 仅将仍在执行中的记录标记为成功，防止失联实例恢复后覆盖超时结果。 */
    fun completeRunningExecution(executionId: Long, resultJson: String, endTime: Long): Boolean =
        update<ActivityTaskExecutionLogEntity> {
            ActivityTaskExecutionLogEntity::status set ActivityTaskExecutionStatus.SUCCESS.name
            ActivityTaskExecutionLogEntity::resultJson set resultJson
            ActivityTaskExecutionLogEntity::endTime set endTime
            ActivityTaskExecutionLogEntity::updateTime set endTime
            where(
                (ActivityTaskExecutionLogEntity::id eq executionId) and
                    (ActivityTaskExecutionLogEntity::status eq ActivityTaskExecutionStatus.RUNNING.name)
            )
        } > 0

    /** 仅将仍在执行中的记录标记为失败。 */
    fun failRunningExecution(executionId: Long, errorMessage: String, endTime: Long): Boolean =
        update<ActivityTaskExecutionLogEntity> {
            ActivityTaskExecutionLogEntity::status set ActivityTaskExecutionStatus.FAILED.name
            ActivityTaskExecutionLogEntity::errorMessage set errorMessage
            ActivityTaskExecutionLogEntity::endTime set endTime
            ActivityTaskExecutionLogEntity::updateTime set endTime
            where(
                (ActivityTaskExecutionLogEntity::id eq executionId) and
                    (ActivityTaskExecutionLogEntity::status eq ActivityTaskExecutionStatus.RUNNING.name)
            )
        } > 0

    /** 将心跳失效实例遗留的执行记录标记为失败，保留完整审计。 */
    fun failRunningExecutionsByTask(taskId: Long, errorMessage: String, endTime: Long): Boolean =
        update<ActivityTaskExecutionLogEntity> {
            ActivityTaskExecutionLogEntity::status set ActivityTaskExecutionStatus.FAILED.name
            ActivityTaskExecutionLogEntity::errorMessage set errorMessage
            ActivityTaskExecutionLogEntity::endTime set endTime
            ActivityTaskExecutionLogEntity::updateTime set endTime
            where(
                (ActivityTaskExecutionLogEntity::activityTaskId eq taskId) and
                    (ActivityTaskExecutionLogEntity::status eq ActivityTaskExecutionStatus.RUNNING.name)
            )
        } > 0
}
