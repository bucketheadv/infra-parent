package io.infra.structure.activity.frontend.dto

/** 带库存和业务唯一标识的通用奖品数据。 */
class StockPrizeData(
    /** 当前奖品的可用库存。 */
    val stock: String,
    /** 奖励项在当前活动内的业务唯一标识。 */
    val uniqueId: String,
    /** 奖品唯一标识；装扮和礼物类型必须填写。 */
    prizeId: String,
    /** 奖品图标地址。 */
    prizeIcon: String,
    /** 奖品名称。 */
    prizeName: String,
    /** 奖品类型。 */
    prizeType: String,
    /** 奖品价值。 */
    prizeValue: String,
    /** 奖品数量。 */
    prizeQuantity: String,
    /** 奖品展示价值；未配置时可为空。 */
    prizeDisplayValue: String?
) : FixedPrizeData(
    prizeId = prizeId,
    prizeIcon = prizeIcon,
    prizeName = prizeName,
    prizeType = prizeType,
    prizeValue = prizeValue,
    prizeQuantity = prizeQuantity,
    prizeDisplayValue = prizeDisplayValue
)
