package io.infra.structure.activity.frontend.luckydraw

import io.infra.structure.activity.frontend.dto.FixedPrizeData
import io.infra.structure.activity.frontend.dto.StockPrizeData

/** 抽奖活动模板中定义的完整配置数据。 */
data class LuckyDrawData(
    /** 抽奖模板配置。 */
    val template: LuckyDrawTemplateData,
    /** 抽奖时间范围。 */
    val timeRange: LuckyDrawTimeRangeData,
    /** 每日奖励配置。 */
    val dailyRewards: LuckyDrawDailyRewardsData,
    /** 多个抽奖时间范围。 */
    val timeRangeList: List<LuckyDrawTimeRangeData>,
    /** 抽奖奖励配置，按奖励分组组织库存奖品。 */
    val luckyDrawRewards: LuckyDrawRewardsData = LuckyDrawRewardsData()
)

/** 抽奖模板的业务配置。 */
data class LuckyDrawTemplateData(
    /** 抽奖奖池类型。 */
    val type: String,
    /** 可参与抽奖的用户分组。 */
    val group: List<String>
)

/** 抽奖的开始和结束时间。 */
data class LuckyDrawTimeRangeData(
    /** 开始时间，格式为 yyyy-MM-ddTHH:mm:ss。 */
    val startTime: String,
    /** 结束时间，格式为 yyyy-MM-ddTHH:mm:ss。 */
    val endTime: String
)

/** 每日奖励组件的配置。 */
data class LuckyDrawDailyRewardsData(
    /** 每日奖励的排名区间列表。 */
    val dailyRewards: List<LuckyDrawRankRewardData>
)

/** 单个排名区间的奖励配置。 */
data class LuckyDrawRankRewardData(
    /** 该排名区间可获得的奖品列表。 */
    val rewards: List<FixedPrizeData>,
    /** 排名区间结束名次。 */
    val endRank: String,
    /** 排名区间开始名次。 */
    val startRank: String
)

/** 抽奖奖励组件的配置。 */
data class LuckyDrawRewardsData(
    /** 当前抽奖活动可使用的库存奖品列表。 */
    val luckyDrawRewards: List<StockPrizeData> = emptyList()
)
