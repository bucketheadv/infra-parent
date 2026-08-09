package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.PrizeComponentEntity
import org.apache.ibatis.annotations.Mapper

/** 奖品组件定义表的基础数据访问 Mapper。 */
@Mapper
interface PrizeComponentMapper : BaseMapper<PrizeComponentEntity>
