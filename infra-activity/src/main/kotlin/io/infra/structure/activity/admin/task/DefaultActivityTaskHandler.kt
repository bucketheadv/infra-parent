package io.infra.structure.activity.admin.task

import io.infra.structure.activity.admin.domain.model.ActivityTaskHandlerType
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** 默认任务处理器，用于未接入具体业务系统前记录可追溯的演示执行结果。 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
class DefaultActivityTaskHandler : ActivityTaskHandler {

    /** 默认处理器接收任何已配置的任务类型，具体业务处理器可优先覆盖它。 */
    override fun supports(handlerType: ActivityTaskHandlerType): Boolean = true

    /** 返回执行上下文摘要；后续可替换为发奖、推送等实际业务调用。 */
    override fun execute(context: ActivityTaskExecutionContext): Map<String, Any?> = linkedMapOf(
        "handler_type" to context.handlerType.name,
        "activity_id" to context.activityId,
        "activity_task_id" to context.activityTaskId,
        "trigger_time" to context.triggerTime,
        "accepted" to true
    )
}
