package io.infra.structure.activity.frontend.dto

/** 固定奖品的通用属性，所有活动类型的奖品数据均可复用。 */
open class FixedPrizeData(
    /** 奖品唯一标识；装扮和礼物类型必须填写。 */
    open val prizeId: String,
    /** 奖品图标地址。 */
    open val prizeIcon: String,
    /** 奖品名称。 */
    open val prizeName: String,
    /** 奖品类型。 */
    open val prizeType: String,
    /** 奖品价值。 */
    open val prizeValue: String,
    /** 奖品数量。 */
    open val prizeQuantity: String,
    /** 奖品展示价值；未配置时可为空。 */
    open val prizeDisplayValue: String?
)
