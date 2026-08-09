package io.infra.structure.activity.admin.task

import io.infra.structure.activity.admin.domain.model.ActivityTaskHandlerType

/** 任务执行器收到的上下文。 */
data class ActivityTaskExecutionContext(
    /** 活动主键。 */
    val activityId: Long,
    /** 活动任务实例主键。 */
    val activityTaskId: Long,
    /** 任务处理器类型。 */
    val handlerType: ActivityTaskHandlerType,
    /** 本次合并后的执行参数。 */
    val parameters: Map<String, Any?>,
    /** 本次计划触发时间戳，单位为毫秒。 */
    val triggerTime: Long
)

/** 可扩展的活动任务处理器。 */
interface ActivityTaskHandler {
    /** 当前处理器是否负责指定类型。 */
    fun supports(handlerType: ActivityTaskHandlerType): Boolean

    /** 执行任务并返回可审计的结果。 */
    fun execute(context: ActivityTaskExecutionContext): Map<String, Any?>
}
