package io.infra.structure.schedule.admin.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.schedule.admin.persistence.entity.ScheduleExecutionLogEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleExecutorEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleExecutorRegistryEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleRouteCursorEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleRouteStatEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleJobEntity
import io.infra.structure.schedule.admin.persistence.entity.ScheduleTriggerOutboxEntity
import io.infra.structure.schedule.repository.StaleRunningLogRef
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Result
import org.apache.ibatis.annotations.Results
import org.apache.ibatis.annotations.Select
import org.apache.ibatis.annotations.Update
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert

/** 调度任务定义的 MyBatis-Flex Mapper。 */
@Mapper
interface ScheduleJobMapper : BaseMapper<ScheduleJobEntity> {
    /** 统计仍绑定到指定执行器的任务，用于后台删除前的友好提示。 */
    @Select("SELECT COUNT(*) FROM infra_schedule_job WHERE executor_id = #{executorId}")
    fun countByExecutorId(@Param("executorId") executorId: Long): Long
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

    /** 查询待回收的僵尸排队/运行中日志（按触发时间升序）。 */
    @Select(
        """
        SELECT id, job_id, target_address
        FROM infra_schedule_execution_log
        WHERE status IN ('QUEUED', 'RUNNING')
          AND trigger_time <= #{staleBeforeTriggerTime}
        ORDER BY trigger_time ASC, id ASC
        LIMIT #{limit}
        """
    )
    @Results(
        value = [
            Result(property = "id", column = "id"),
            Result(property = "jobId", column = "job_id"),
            Result(property = "targetAddress", column = "target_address")
        ]
    )
    fun findStaleRunningCandidates(
        @Param("staleBeforeTriggerTime") staleBeforeTriggerTime: Long,
        @Param("limit") limit: Int
    ): List<StaleRunningLogRef>

    /** 将指定 ID 且仍为 QUEUED/RUNNING 的日志回收为 LOST。 */
    @Update(
        """
        UPDATE infra_schedule_execution_log
        SET status = 'LOST',
            finish_time = #{now},
            message = #{message},
            duration_millis = #{now} - trigger_time
        WHERE id = #{id}
          AND status IN ('QUEUED', 'RUNNING')
        """
    )
    fun markLostIfActive(
        @Param("id") id: Long,
        @Param("now") now: Long,
        @Param("message") message: String
    ): Int

    /** 将指定任务触发批次下仍 QUEUED/RUNNING 的日志标记为 FAILED。 */
    @Update(
        """
        UPDATE infra_schedule_execution_log
        SET status = 'FAILED',
            finish_time = #{finishTime},
            message = #{message},
            duration_millis = #{finishTime} - trigger_time
        WHERE job_id = #{jobId}
          AND trigger_time = #{triggerTime}
          AND status IN ('QUEUED', 'RUNNING')
        """
    )
    fun failRunningByJobAndTrigger(
        @Param("jobId") jobId: Long,
        @Param("triggerTime") triggerTime: Long,
        @Param("message") message: String,
        @Param("finishTime") finishTime: Long
    ): Int

    /** 按批删除已终态的历史日志，避免一次性删除造成长事务和锁等待。 */
    @Delete(
        """
        DELETE FROM infra_schedule_execution_log
        WHERE finish_time IS NOT NULL
          AND finish_time < #{finishTimeBefore}
          AND status NOT IN ('QUEUED', 'RUNNING')
        ORDER BY id ASC
        LIMIT #{limit}
        """
    )
    fun deleteFinishedBefore(
        @Param("finishTimeBefore") finishTimeBefore: Long,
        @Param("limit") limit: Int
    ): Int
}

/** 可靠触发 Outbox Mapper。 */
@Mapper
interface ScheduleTriggerOutboxMapper : BaseMapper<ScheduleTriggerOutboxEntity> {
    /** 锁定一页待投递或租约过期的触发记录，调用方必须处于事务中。 */
    @Select(
        """
        SELECT *
        FROM infra_schedule_trigger_outbox
        WHERE status = 'PENDING'
           OR (status = 'PROCESSING' AND claim_until IS NOT NULL AND claim_until <= #{now})
        ORDER BY id ASC
        LIMIT #{pageSize}
        FOR UPDATE SKIP LOCKED
        """
    )
    @Results(
        value = [
            Result(property = "jobId", column = "job_id"),
            Result(property = "triggerTime", column = "trigger_time"),
            Result(property = "claimOwner", column = "claim_owner"),
            Result(property = "claimUntil", column = "claim_until"),
            Result(property = "attemptCount", column = "attempt_count"),
            Result(property = "lastError", column = "last_error"),
            Result(property = "createTime", column = "create_time"),
            Result(property = "updateTime", column = "update_time")
        ]
    )
    fun lockPendingPage(@Param("now") now: Long, @Param("pageSize") pageSize: Int): List<ScheduleTriggerOutboxEntity>

    /** 分批删除已确认投递或已取消的历史记录，活跃租约不在清理范围内。 */
    @Delete(
        """
        DELETE FROM infra_schedule_trigger_outbox
        WHERE status IN ('DISPATCHED', 'CANCELLED')
          AND update_time < #{updateTimeBefore}
        ORDER BY id ASC
        LIMIT #{limit}
        """
    )
    fun deleteCompletedBefore(
        @Param("updateTimeBefore") updateTimeBefore: Long,
        @Param("limit") limit: Int
    ): Int
}

/** 执行器心跳的 MyBatis-Flex Mapper。 */
@Mapper
interface ScheduleExecutorMapper : BaseMapper<ScheduleExecutorEntity> {
    /**
     * 心跳创建分组或刷新已有分组的存活时间。
     * 使用 MySQL upsert 消除多 Admin 节点同时收到首个心跳时的唯一键竞争。
     */
    @Insert(
        """
        INSERT INTO infra_schedule_executor (
            executor_group, executor_name, address, address_mode, status,
            last_heartbeat_time, create_time, update_time
        ) VALUES (
            #{executorGroup}, #{executorName}, NULL, 'AUTO_REGISTER', 'ENABLED',
            #{now}, #{now}, #{now}
        )
        ON DUPLICATE KEY UPDATE
            executor_name = IF(executor_name = '', VALUES(executor_name), executor_name),
            last_heartbeat_time = VALUES(last_heartbeat_time),
            update_time = VALUES(update_time)
        """
    )
    fun upsertHeartbeat(
        @Param("executorGroup") executorGroup: String,
        @Param("executorName") executorName: String,
        @Param("now") now: Long
    ): Int

    /** 执行器删除需要同时确认不存在引用它的任务，避免检查后新建任务的并发窗口。 */
    @Delete(
        """
        DELETE executor
        FROM infra_schedule_executor executor
        WHERE executor.id = #{id}
          AND NOT EXISTS (
              SELECT 1 FROM infra_schedule_job job WHERE job.executor_id = executor.id
          )
        """
    )
    fun deleteIfUnreferenced(@Param("id") id: Long): Int
}

/** 执行器实例地址注册表 Mapper。 */
@Mapper
interface ScheduleExecutorRegistryMapper : BaseMapper<ScheduleExecutorRegistryEntity> {
    /** 心跳地址登记使用原子 Upsert，避免多个 Admin 同时接收首心跳时撞唯一键。 */
    @Insert(
        """
        INSERT INTO infra_schedule_executor_registry (
            executor_id, address, last_heartbeat_time, create_time, update_time
        ) VALUES (#{executorId}, #{address}, #{now}, #{now}, #{now})
        ON DUPLICATE KEY UPDATE
            last_heartbeat_time = VALUES(last_heartbeat_time),
            update_time = VALUES(update_time)
        """
    )
    fun upsertRegistry(
        @Param("executorId") executorId: Long,
        @Param("address") address: String,
        @Param("now") now: Long
    ): Int
}

/** 路由 LFU/LRU 统计 Mapper。 */
@Mapper
interface ScheduleRouteStatMapper : BaseMapper<ScheduleRouteStatEntity> {
    @Update(
        """
        INSERT INTO infra_schedule_route_stat (node_key, use_count, last_route_time, update_time)
        VALUES (#{nodeKey}, 1, #{now}, #{now})
        ON DUPLICATE KEY UPDATE
            use_count = use_count + 1,
            last_route_time = VALUES(last_route_time),
            update_time = VALUES(update_time)
        """
    )
    fun upsertRouteUse(@Param("nodeKey") nodeKey: String, @Param("now") now: Long): Int
}

/** 路由 ROUND 游标 Mapper。 */
@Mapper
interface ScheduleRouteCursorMapper : BaseMapper<ScheduleRouteCursorEntity> {
    @Update(
        """
        INSERT INTO infra_schedule_route_cursor (cursor_key, cursor_value, update_time)
        VALUES (#{cursorKey}, 1, #{now})
        ON DUPLICATE KEY UPDATE
            cursor_value = cursor_value + 1,
            update_time = VALUES(update_time)
        """
    )
    fun incrementCursor(@Param("cursorKey") cursorKey: String, @Param("now") now: Long): Int

    @Select("SELECT cursor_value FROM infra_schedule_route_cursor WHERE cursor_key = #{cursorKey}")
    fun selectCursorValue(@Param("cursorKey") cursorKey: String): Long?
}
