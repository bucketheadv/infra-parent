package io.infra.structure.activity.frontend.type

import io.infra.structure.activity.frontend.luckydraw.LuckyDrawData

/**
 * 面向前端的活动类型。
 *
 * 每个枚举值的 [templateCode] 必须与后台活动模板编码完全一致，[formDataClass] 用于解析活动表的
 * formDataJson。新增活动类型时，必须同步提供对应的 DTO、表单数据类、Service 和 Controller。
 */
enum class ActivityType(
    /** 后台活动模板的不可变编码。 */
    val templateCode: String,
    /** 活动表单 JSON 对应的具体数据类型。 */
    val formDataClass: Class<out Any>
) {
    /** 抽奖活动模板，对应本地演示数据中的 lucky_draw。 */
    LUCKY_DRAW("lucky_draw", LuckyDrawData::class.java)
}
