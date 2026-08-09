package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTaskInstanceEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Update

/** 活动任务实例表的数据访问 Mapper。 */
@Mapper
interface ActivityTaskInstanceMapper : BaseMapper<ActivityTaskInstanceEntity> {

    /** 使用数据库条件更新获取分布式执行租约。 */
    @Update("""
        UPDATE activity_task_instance
        SET status = 'RUNNING', lease_owner = #{owner}, lease_expire_time = #{leaseExpireTime}, update_time = #{now}
        WHERE id = #{taskId}
          AND status = 'PENDING'
          AND next_trigger_time IS NOT NULL
          AND next_trigger_time <= #{now}
          AND (lease_expire_time IS NULL OR lease_expire_time < #{now})
    """)
    fun claimDueTask(
        @Param("taskId") taskId: Long,
        @Param("owner") owner: String,
        @Param("leaseExpireTime") leaseExpireTime: Long,
        @Param("now") now: Long
    ): Int
}
