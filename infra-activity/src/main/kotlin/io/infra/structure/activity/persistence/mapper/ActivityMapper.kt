package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.ActivityEntity
import org.apache.ibatis.annotations.Mapper

/** 活动配置表的基础数据访问 Mapper。 */
@Mapper
interface ActivityMapper : BaseMapper<ActivityEntity>
