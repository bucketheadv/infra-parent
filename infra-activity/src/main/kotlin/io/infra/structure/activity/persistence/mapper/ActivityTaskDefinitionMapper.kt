package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTaskDefinitionEntity
import org.apache.ibatis.annotations.Mapper

/** 任务模板表的数据访问 Mapper。 */
@Mapper
interface ActivityTaskDefinitionMapper : BaseMapper<ActivityTaskDefinitionEntity>
