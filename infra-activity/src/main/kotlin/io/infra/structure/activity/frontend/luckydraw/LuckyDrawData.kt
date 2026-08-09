package io.infra.structure.activity.frontend.luckydraw

import com.fasterxml.jackson.annotation.JsonProperty

/** 抽奖活动模板中定义的完整配置数据。 */
data class LuckyDrawData(
    /** 抽奖模板配置。 */
    val template: LuckyDrawTemplateData,
    /** 抽奖时间范围。 */
    @param:JsonProperty("time_range")
    @get:JsonProperty("time_range")
    val timeRange: LuckyDrawTimeRangeData,
    /** 每日奖励配置。 */
    @param:JsonProperty("daily_rewards")
    @get:JsonProperty("daily_rewards")
    val dailyRewards: LuckyDrawDailyRewardsData,
    /** 多个抽奖时间范围。 */
    @param:JsonProperty("time_range_list")
    @get:JsonProperty("time_range_list")
    val timeRangeList: List<LuckyDrawTimeRangeData>,
    /** 抽奖奖励配置，按奖励分组组织库存奖品。 */
    @param:JsonProperty("lucky_draw_rewards")
    @get:JsonProperty("lucky_draw_rewards")
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
    @param:JsonProperty("start_time")
    @get:JsonProperty("start_time")
    val startTime: String,
    /** 结束时间，格式为 yyyy-MM-ddTHH:mm:ss。 */
    @param:JsonProperty("end_time")
    @get:JsonProperty("end_time")
    val endTime: String
)

/** 每日奖励组件的配置。 */
data class LuckyDrawDailyRewardsData(
    /** 每日奖励的排名区间列表。 */
    @param:JsonProperty("daily_rewards")
    @get:JsonProperty("daily_rewards")
    val dailyRewards: List<LuckyDrawRankRewardData>
)

/** 单个排名区间的奖励配置。 */
data class LuckyDrawRankRewardData(
    /** 该排名区间可获得的奖品列表。 */
    val rewards: List<LuckyDrawPrizeData>,
    /** 排名区间结束名次。 */
    @param:JsonProperty("end_rank")
    @get:JsonProperty("end_rank")
    val endRank: String,
    /** 排名区间开始名次。 */
    @param:JsonProperty("start_rank")
    @get:JsonProperty("start_rank")
    val startRank: String
)

/** 单个抽奖奖品的配置。 */
data class LuckyDrawPrizeData(
    /** 奖品唯一标识；装扮和礼物类型必须填写。 */
    val prizeId: String,
    /** 奖品图标地址。 */
    val prizeIcon: String,
    /** 奖品名称。 */
    val prizeName: String,
    /** 奖品类型。 */
    val prizeType: String,
    /** 奖品价值。 */
    val prizeValue: String,
    /** 奖品数量。 */
    val prizeQuantity: String,
    /** 奖品展示价值；未配置时可为空。 */
    val prizeDisplayValue: String?
)

/** 抽奖奖励组件的配置。 */
data class LuckyDrawRewardsData(
    /** 按业务规则排列的抽奖奖励分组。 */
    @param:JsonProperty("lucky_draw_rewards")
    @get:JsonProperty("lucky_draw_rewards")
    val luckyDrawRewards: List<LuckyDrawRewardGroupData> = emptyList()
)

/** 单个抽奖奖励分组的配置。 */
data class LuckyDrawRewardGroupData(
    /** 当前分组下可被抽取的库存奖品列表。 */
    @param:JsonProperty("lucky_draw_rewards")
    @get:JsonProperty("lucky_draw_rewards")
    val luckyDrawRewards: List<LuckyDrawStockPrizeData> = emptyList()
)

/** 带库存和业务唯一标识的抽奖奖品配置。 */
data class LuckyDrawStockPrizeData(
    /** 当前奖品的可用库存。 */
    val stock: String,
    /** 奖品唯一标识；装扮和礼物类型必须填写。 */
    val prizeId: String,
    /** 奖品图标地址。 */
    val prizeIcon: String,
    /** 奖品名称。 */
    val prizeName: String,
    /** 奖品类型。 */
    val prizeType: String,
    /** 奖励项在当前抽奖活动内的业务唯一标识。 */
    @param:JsonProperty("unique_id")
    @get:JsonProperty("unique_id")
    val uniqueId: String,
    /** 奖品价值。 */
    val prizeValue: String,
    /** 奖品数量。 */
    val prizeQuantity: String,
    /** 奖品展示价值；未配置时可为空。 */
    val prizeDisplayValue: String?
)
