package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.RewardTemplateComponentEntity
import org.apache.ibatis.annotations.Mapper

/** 奖励模板组件关联表的基础数据访问 Mapper。 */
@Mapper
interface RewardTemplateComponentMapper : BaseMapper<RewardTemplateComponentEntity>
