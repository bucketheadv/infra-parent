package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTaskExecutionLogEntity
import org.apache.ibatis.annotations.Mapper

/** 活动任务执行记录表的数据访问 Mapper。 */
@Mapper
interface ActivityTaskExecutionLogMapper : BaseMapper<ActivityTaskExecutionLogEntity>
