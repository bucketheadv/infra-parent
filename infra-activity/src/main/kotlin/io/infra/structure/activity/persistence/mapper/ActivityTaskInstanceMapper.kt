package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTaskInstanceEntity
import org.apache.ibatis.annotations.Mapper

/** 活动任务实例表的数据访问 Mapper。 */
@Mapper
interface ActivityTaskInstanceMapper : BaseMapper<ActivityTaskInstanceEntity>
