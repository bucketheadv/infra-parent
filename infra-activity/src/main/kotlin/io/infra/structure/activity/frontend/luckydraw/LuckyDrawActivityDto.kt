package io.infra.structure.activity.frontend.luckydraw

import io.infra.structure.activity.frontend.dto.BaseActivityDto
import io.infra.structure.activity.frontend.type.ActivityType

/** 抽奖活动模板面向前端的响应模型。 */
data class LuckyDrawActivityDto(
    /** 活动主键。 */
    override val id: Long,
    /** 当前活动类型。 */
    override val activityType: ActivityType = ActivityType.LUCKY_DRAW,
    /** 活动展示名称。 */
    override val name: String,
    /** 活动采用的模板主键。 */
    override val templateId: Long,
    /** 活动状态。 */
    override val status: String,
    /** 活动上下线状态。 */
    override val onlineStatus: String,
    /** 是否永久有效。 */
    override val validForever: Boolean,
    /** 非永久活动的开始时间戳，单位为毫秒。 */
    override val validStartTime: Long?,
    /** 非永久活动的结束时间戳，单位为毫秒。 */
    override val validEndTime: Long?,
    /** 是否启用仅面向白名单用户的调试模式。 */
    override val debugMode: Boolean,
    /** 调试模式白名单中的用户主键。 */
    override val debugUserIds: List<Long>,
    /** 调试模式强制使用的时间戳，单位为毫秒。 */
    override val debugForceTime: Long?,
    /** 活动创建时间戳，单位为毫秒。 */
    override val createTime: Long,
    /** 活动最后更新时间戳，单位为毫秒。 */
    override val updateTime: Long,
    /** 按活动模板定义保存的层级配置数据。 */
    override val data: LuckyDrawData
) : BaseActivityDto<LuckyDrawData>(
    id = id,
    activityType = activityType,
    name = name,
    templateId = templateId,
    status = status,
    onlineStatus = onlineStatus,
    validForever = validForever,
    validStartTime = validStartTime,
    validEndTime = validEndTime,
    debugMode = debugMode,
    debugUserIds = debugUserIds,
    debugForceTime = debugForceTime,
    createTime = createTime,
    updateTime = updateTime,
    data = data
)
