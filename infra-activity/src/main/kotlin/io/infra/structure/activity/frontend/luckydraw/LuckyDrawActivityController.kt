package io.infra.structure.activity.frontend.luckydraw

import io.infra.structure.activity.frontend.controller.BaseActivityController
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 抽奖活动模板的前端访问接口。 */
@RestController
@RequestMapping(
    value = ["/api/activity/luckydraw"],
    produces = [MediaType.APPLICATION_JSON_VALUE]
)
class LuckyDrawActivityController(
    activityService: LuckyDrawActivityService
) : BaseActivityController<
    LuckyDrawActivityService,
    LuckyDrawActivityDto
>(activityService)
