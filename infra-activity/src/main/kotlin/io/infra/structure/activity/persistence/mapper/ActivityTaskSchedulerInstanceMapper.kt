package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTaskSchedulerInstanceEntity
import org.apache.ibatis.annotations.Mapper

/** 活动任务调度实例心跳表的数据访问 Mapper。 */
@Mapper
interface ActivityTaskSchedulerInstanceMapper : BaseMapper<ActivityTaskSchedulerInstanceEntity>
