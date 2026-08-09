package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTemplateTaskBindingEntity
import org.apache.ibatis.annotations.Mapper

/** 活动模板任务关联表的数据访问 Mapper。 */
@Mapper
interface ActivityTemplateTaskBindingMapper : BaseMapper<ActivityTemplateTaskBindingEntity>
