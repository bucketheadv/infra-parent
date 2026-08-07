package io.infra.structure.activity.persistence.mapper

import com.mybatisflex.core.BaseMapper
import io.infra.structure.activity.persistence.entity.RewardComponentPrizeEntity
import org.apache.ibatis.annotations.Mapper

/** 奖励组件奖品关联表的基础数据访问 Mapper。 */
@Mapper
interface RewardComponentPrizeMapper : BaseMapper<RewardComponentPrizeEntity>
