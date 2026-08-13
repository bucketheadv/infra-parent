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
import org.apache.ibatis.annotations.Options

/** 调度任务定义的 MyBatis-Flex Mapper。 */
@Mapper
interface ScheduleJobMapper : BaseMapper<ScheduleJobEntity> {
    /** 统计仍绑定到指定执行器的任务，用于后台删除前的友好提示。 */
    @Select("SELECT COUNT(*) FROM infra_schedule_job WHERE executor_id = #{executorId}")
    fun countByExecutorId(@Param("executorId") executorId: Long): Long

    /** 锁定单个任务，供任务变更与调度扫描互斥。 */
    @Select("SELECT * FROM infra_schedule_job WHERE id = #{id} FOR UPDATE")
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
    fun lockById(@Param("id") id: Long): ScheduleJobEntity?
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
     * 仅在任务仍启用时插入执行日志。
     *
     * INSERT ... SELECT 会和任务删除持有的行锁互斥，防止删除后的旧工作线程留下孤儿日志。
     */
    @Insert(
        """
        INSERT INTO infra_schedule_execution_log (
            job_id, executor_id, trigger_time, finish_time, status, retry_count,
            message, handle_log, target_address, duration_millis
        )
        SELECT #{entity.jobId}, #{entity.executorId}, #{entity.triggerTime}, #{entity.finishTime},
               #{entity.status}, #{entity.retryCount}, #{entity.message}, #{entity.handleLog},
               #{entity.targetAddress}, #{entity.durationMillis}
        FROM infra_schedule_job job
        LEFT JOIN infra_schedule_trigger_outbox outbox
          ON outbox.id = #{outboxId}
         AND outbox.status = 'PROCESSING'
         AND outbox.claim_owner = #{owner}
         AND outbox.claim_token = #{claimToken}
        WHERE job.id = #{entity.jobId}
          AND (job.status = 'ENABLED' OR outbox.manual_trigger = 1)
          AND (#{outboxId} IS NULL OR (outbox.id IS NOT NULL AND outbox.claim_until > #{now}))
        """
    )
    @Options(useGeneratedKeys = true, keyProperty = "entity.id")
    fun insertIfJobEnabled(
        @Param("entity") entity: ScheduleExecutionLogEntity,
        @Param("outboxId") outboxId: Long?,
        @Param("owner") owner: String?,
        @Param("claimToken") claimToken: String?,
        @Param("now") now: Long?
    ): Int

    /**
     * 原子处理执行器终态回调。
     *
     * 超时请求和 finish 回调可能交错到达，因此在同一条 UPDATE 中读取旧状态：
     * TIMING_OUT 始终收口为 TIMEOUT，不能被迟到的 success 覆盖；CANCELLING 仍保留执行器
     * 的实际结果，避免自然结束的任务被伪装为取消成功。
     */
    @Update(
        """
        UPDATE infra_schedule_execution_log
        SET finish_time = #{entity.finishTime},
            status = CASE WHEN status = 'TIMING_OUT' THEN 'TIMEOUT' ELSE #{entity.status} END,
            message = CASE WHEN status = 'TIMING_OUT' THEN #{timeoutMessage} ELSE #{entity.message} END,
            duration_millis = #{entity.durationMillis}
        WHERE id = #{entity.id}
          AND status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'TIMING_OUT')
        """
    )
    fun finishFromExecutor(
        @Param("entity") entity: ScheduleExecutionLogEntity,
        @Param("timeoutMessage") timeoutMessage: String
    ): Int

    /**
     * 原子追加业务执行日志；超过约 1MB 时截断，避免撑爆行。
     * MySQL 5.x 兼容 CONCAT / IFNULL / LEFT。
     */
    @Update(
        """
        UPDATE infra_schedule_execution_log
        SET handle_log = LEFT(CONCAT(IFNULL(handle_log, ''), #{chunk}), 1000000)
        WHERE id = #{id}
          AND status IN ('QUEUED', 'RUNNING', 'CANCELLING', 'TIMING_OUT')
        """
    )
    fun appendHandleLog(@Param("id") id: Long, @Param("chunk") chunk: String): Int

    /** 取消或超时确认中的记录需立即参与探活，而非等待普通僵尸阈值到期。 */
    @Select(
        """
        SELECT id, job_id, target_address, status, trigger_time
        FROM infra_schedule_execution_log
        WHERE status IN ('CANCELLING', 'TIMING_OUT')
          AND id > #{afterId}
        ORDER BY id ASC
        LIMIT #{limit}
        """
    )
    @Results(
        value = [
            Result(property = "id", column = "id"),
            Result(property = "jobId", column = "job_id"),
            Result(property = "targetAddress", column = "target_address"),
            Result(property = "status", column = "status"),
            Result(property = "triggerTime", column = "trigger_time")
        ]
    )
    fun findPendingCancellationCandidates(
        @Param("afterId") afterId: Long,
        @Param("limit") limit: Int
    ): List<StaleRunningLogRef>

    /** 查询待回收的排队、运行或取消确认中日志（按触发时间升序）。 */
    @Select(
        """
        SELECT id, job_id, target_address, status, trigger_time
        FROM infra_schedule_execution_log
        WHERE status IN ('QUEUED', 'RUNNING')
          AND trigger_time <= #{staleBeforeTriggerTime}
          AND id > #{afterId}
        ORDER BY id ASC
        LIMIT #{limit}
        """
    )
    @Results(
        value = [
            Result(property = "id", column = "id"),
            Result(property = "jobId", column = "job_id"),
            Result(property = "targetAddress", column = "target_address"),
            Result(property = "status", column = "status"),
            Result(property = "triggerTime", column = "trigger_time")
        ]
    )
    fun findStaleRunningCandidates(
        @Param("staleBeforeTriggerTime") staleBeforeTriggerTime: Long,
        @Param("afterId") afterId: Long,
        @Param("limit") limit: Int
    ): List<StaleRunningLogRef>

    /**
     * 将指定 ID 且仍为普通执行态的日志回收为 LOST。
     *
     * CANCELLING/TIMING_OUT 可能在探活请求期间刚写入，必须交由取消确认流程收口，
     * 不能被持有旧 RUNNING 快照的僵尸扫描覆盖为 LOST。
     */
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

    /** 按批删除已终态的历史日志，避免一次性删除造成长事务和锁等待。 */
    @Delete(
        """
        DELETE FROM infra_schedule_execution_log
        WHERE finish_time IS NOT NULL
          AND finish_time < #{finishTimeBefore}
          AND status NOT IN ('QUEUED', 'RUNNING', 'CANCELLING', 'TIMING_OUT')
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
        WHERE (status = 'PENDING' AND (claim_until IS NULL OR claim_until <= #{now}))
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
            Result(property = "manualTrigger", column = "manual_trigger"),
            Result(property = "claimOwner", column = "claim_owner"),
            Result(property = "claimToken", column = "claim_token"),
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

    /** 在同一事务内锁定当前游标，避免并发 Admin 读取同一个轮询值。 */
    @Select("SELECT cursor_value FROM infra_schedule_route_cursor WHERE cursor_key = #{cursorKey} FOR UPDATE")
    fun selectCursorValue(@Param("cursorKey") cursorKey: String): Long?
}
