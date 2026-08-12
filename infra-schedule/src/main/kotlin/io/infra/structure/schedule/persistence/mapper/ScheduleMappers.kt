package io.infra.structure.schedule.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.schedule.persistence.entity.ScheduleExecutionLogEntity
import io.infra.structure.schedule.persistence.entity.ScheduleExecutorEntity
import io.infra.structure.schedule.persistence.entity.ScheduleExecutorRegistryEntity
import io.infra.structure.schedule.persistence.entity.ScheduleJobEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Result
import org.apache.ibatis.annotations.Results
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update

/** 调度任务定义的 MyBatis-Flex Mapper。 */
@Mapper
interface ScheduleJobMapper : BaseMapper<ScheduleJobEntity> {
    /**
     * 在当前事务中锁定一页到期任务。
     *
     * MySQL 8 的 SKIP LOCKED 会跳过其他调度节点已锁定的记录，避免等待锁而降低调度吞吐。
     * 调用方必须在同一事务内立即写入 claim_owner 与 claim_until 后提交事务。
     */
    @Select(
        """
        SELECT *
        FROM infra_schedule_job
        WHERE status = 'ENABLED'
          AND next_trigger_at IS NOT NULL
          AND next_trigger_at <= #{now}
          AND (claim_until IS NULL OR claim_until <= #{now})
        ORDER BY next_trigger_at ASC, id ASC
        LIMIT #{pageSize}
        FOR UPDATE SKIP LOCKED
        """
    )
    @Results(
        value = [
            Result(property = "executorId", column = "executor_id"),
            Result(property = "scheduleType", column = "schedule_type"),
            Result(property = "fixedRateMillis", column = "fixed_rate_millis"),
            Result(property = "routeStrategy", column = "route_strategy"),
            Result(property = "blockStrategy", column = "block_strategy"),
            Result(property = "resident", column = "resident"),
            Result(property = "maxRetryCount", column = "max_retry_count"),
            Result(property = "retryIntervalMillis", column = "retry_interval_millis"),
            Result(property = "timeoutSeconds", column = "timeout_seconds"),
            Result(property = "nextTriggerAt", column = "next_trigger_at"),
            Result(property = "lastTriggerAt", column = "last_trigger_at"),
            Result(property = "claimOwner", column = "claim_owner"),
            Result(property = "claimUntil", column = "claim_until"),
            Result(property = "createTime", column = "create_time"),
            Result(property = "updateTime", column = "update_time")
        ]
    )
    fun lockDuePage(
        @Param("now") now: Long,
        @Param("pageSize") pageSize: Int
    ): List<ScheduleJobEntity>
}

/** 调度执行日志的 MyBatis-Flex Mapper。 */
@Mapper
interface ScheduleExecutionLogMapper : BaseMapper<ScheduleExecutionLogEntity> {
    /**
     * 原子追加业务执行日志；超过约 1MB 时截断，避免撑爆行。
     * MySQL 5.x 兼容 CONCAT / IFNULL / LEFT。
     */
    @Update(
        """
        UPDATE infra_schedule_execution_log
        SET handle_log = LEFT(CONCAT(IFNULL(handle_log, ''), #{chunk}), 1000000)
        WHERE id = #{id}
        """
    )
    fun appendHandleLog(@Param("id") id: Long, @Param("chunk") chunk: String): Int
}

/** 执行器心跳的 MyBatis-Flex Mapper。 */
@Mapper
interface ScheduleExecutorMapper : BaseMapper<ScheduleExecutorEntity>

/** 执行器实例地址注册表 Mapper。 */
@Mapper
interface ScheduleExecutorRegistryMapper : BaseMapper<ScheduleExecutorRegistryEntity>
