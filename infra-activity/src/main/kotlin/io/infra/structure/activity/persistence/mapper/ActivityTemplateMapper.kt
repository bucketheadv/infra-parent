package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTemplateEntity
import org.apache.ibatis.annotations.Mapper

/** 活动模板表的基础数据访问 Mapper。 */
@Mapper
interface ActivityTemplateMapper : BaseMapper<ActivityTemplateEntity>
