package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityTemplateRewardTemplateEntity
import org.apache.ibatis.annotations.Mapper

/** 活动模板奖励模板关联表的基础数据访问 Mapper。 */
@Mapper
interface ActivityTemplateRewardTemplateMapper : BaseMapper<ActivityTemplateRewardTemplateEntity>
