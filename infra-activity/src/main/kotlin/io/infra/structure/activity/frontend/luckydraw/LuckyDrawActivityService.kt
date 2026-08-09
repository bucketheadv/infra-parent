package io.infra.structure.activity.frontend.luckydraw

import io.infra.structure.activity.admin.dto.FrontendActivityResponse
import io.infra.structure.activity.frontend.service.BaseActivityService
import io.infra.structure.activity.frontend.type.ActivityType
import org.springframework.stereotype.Service

/** 抽奖活动模板的前端构建服务。 */
@Service
class LuckyDrawActivityService : BaseActivityService<LuckyDrawActivityDto>() {

    /** 抽奖活动模板编码对应的类型。 */
    override val activityType: ActivityType = ActivityType.LUCKY_DRAW

    /** 将活动配置转换为前端 DTO。 */
    override fun toDto(activity: FrontendActivityResponse): LuckyDrawActivityDto = LuckyDrawActivityDto(
        id = activity.id,
        name = activity.name,
        templateId = activity.templateId,
        status = activity.status,
        onlineStatus = activity.onlineStatus,
        validForever = activity.validForever,
        validStartTime = activity.validStartTime,
        validEndTime = activity.validEndTime,
        debugMode = activity.debugMode,
        debugUserIds = activity.debugUserIds,
        debugForceTime = activity.debugForceTime,
        createTime = activity.createTime,
        updateTime = activity.updateTime,
        data = parseFormData(activity.formDataJson)
    )
}
