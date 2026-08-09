package io.infra.structure.activity.admin.dto

import io.infra.structure.activity.admin.domain.model.ActivityTaskExecutionStatus
import io.infra.structure.activity.admin.domain.model.ActivityTaskHandlerType
import io.infra.structure.activity.admin.domain.model.ActivityTaskStatus
import io.infra.structure.activity.admin.domain.model.ActivityTaskTriggerConfig
import io.infra.structure.activity.admin.domain.model.ActivityTaskTriggerSource
import io.infra.structure.activity.admin.domain.model.ActivityTaskTriggerType

/** 新建或更新可复用任务模板的请求。 */
data class CreateActivityTaskTemplateRequest(
    /** 任务模板名称。 */
    val name: String,
    /** 对应后端任务处理器的类型。 */
    val handlerType: ActivityTaskHandlerType,
    /** 任务用途说明。 */
    val description: String? = null,
    /** 任务默认执行参数。 */
    val defaultParameters: Map<String, Any?> = emptyMap(),
    /** 最大重试次数。 */
    val maxRetryCount: Int = 3,
    /** 两次重试之间的间隔，单位为毫秒。 */
    val retryIntervalMillis: Long = 60_000,
    /** 是否允许新活动模板继续关联。 */
    val enabled: Boolean = true
)

/** 活动模板关联任务模板的请求。 */
data class ActivityTemplateTaskBindingRequest(
    /** 被关联的任务定义主键；提交时必须选择一项。 */
    val taskTemplateId: Long? = null,
    /** 当前活动模板内唯一的任务编码。 */
    val code: String,
    /** 当前活动模板中展示的任务名称。 */
    val name: String,
    /** 当前任务的触发方式。 */
    val triggerType: ActivityTaskTriggerType,
    /** 当前任务的触发配置。 */
    val triggerConfig: ActivityTaskTriggerConfig = ActivityTaskTriggerConfig(),
    /** 覆盖任务模板默认参数的活动专属参数。 */
    val parameterOverrides: Map<String, Any?> = emptyMap(),
    /** 是否启用该任务绑定。 */
    val enabled: Boolean = true
)

/** 替换活动模板全部任务关联的请求。 */
data class ReplaceActivityTemplateTaskBindingsRequest(
    /** 按执行顺序排列的任务绑定。 */
    val tasks: List<ActivityTemplateTaskBindingRequest>
)

/** 手动触发活动任务的请求。 */
data class ManualTriggerActivityTaskRequest(
    /** 本次手动执行的说明。 */
    val reason: String? = null
)

/** 预览 Cron 后续触发时间的请求。 */
data class ActivityTaskCronPreviewRequest(
    /** Spring 六段式 Cron 表达式。 */
    val cron: String? = null,
    /** Cron 计算使用的 IANA 时区。 */
    val timezone: String? = null
)

/** Cron 后续触发时间的预览结果。 */
data class ActivityTaskCronPreviewResponse(
    /** 从当前时间开始计算的后续触发时间戳，单位为毫秒。 */
    val nextTimes: List<Long>
)

/** 任务模板接口视图。 */
data class ActivityTaskTemplateResponse(
    /** 任务模板主键。 */
    val id: Long,
    /** 任务模板名称。 */
    val name: String,
    /** 后端任务处理器类型。 */
    val handlerType: ActivityTaskHandlerType,
    /** 任务用途说明。 */
    val description: String?,
    /** 默认执行参数。 */
    val defaultParameters: Map<String, Any?>,
    /** 最大重试次数。 */
    val maxRetryCount: Int,
    /** 重试间隔，单位为毫秒。 */
    val retryIntervalMillis: Long,
    /** 是否允许继续关联。 */
    val enabled: Boolean
)

/** 活动模板任务绑定接口视图。 */
data class ActivityTemplateTaskBindingResponse(
    /** 任务绑定主键。 */
    val id: Long,
    /** 所属活动模板主键。 */
    val activityTemplateId: Long,
    /** 任务模板主键。 */
    val taskTemplateId: Long,
    /** 当前模板内唯一任务编码。 */
    val code: String,
    /** 任务展示名称。 */
    val name: String,
    /** 任务处理器类型快照。 */
    val handlerType: ActivityTaskHandlerType,
    /** 触发方式。 */
    val triggerType: ActivityTaskTriggerType,
    /** 触发配置。 */
    val triggerConfig: ActivityTaskTriggerConfig,
    /** 活动专属参数覆盖。 */
    val parameterOverrides: Map<String, Any?>,
    /** 是否启用。 */
    val enabled: Boolean,
    /** 执行排序。 */
    val sortNo: Int
)

/** 活动实例任务接口视图。 */
data class ActivityTaskResponse(
    /** 任务实例主键。 */
    val id: Long,
    /** 所属活动主键。 */
    val activityId: Long,
    /** 来源活动模板任务绑定主键。 */
    val activityTemplateTaskId: Long,
    /** 任务编码。 */
    val code: String,
    /** 任务名称。 */
    val name: String,
    /** 任务处理器类型。 */
    val handlerType: ActivityTaskHandlerType,
    /** 触发方式。 */
    val triggerType: ActivityTaskTriggerType,
    /** 下一次触发时间戳，单位为毫秒。 */
    val nextTriggerTime: Long?,
    /** 当前调度状态。 */
    val status: ActivityTaskStatus,
    /** 已重试次数。 */
    val retryCount: Int,
    /** 最近一次执行时间戳，单位为毫秒。 */
    val lastTriggerTime: Long?
)

/** 单次任务执行记录接口视图。 */
data class ActivityTaskExecutionResponse(
    /** 执行记录主键。 */
    val id: Long,
    /** 所属任务实例主键。 */
    val activityTaskId: Long,
    /** 幂等执行键。 */
    val executionKey: String,
    /** 触发来源。 */
    val triggerSource: ActivityTaskTriggerSource,
    /** 本次计划触发时间戳，单位为毫秒。 */
    val triggerTime: Long,
    /** 执行状态。 */
    val status: ActivityTaskExecutionStatus,
    /** 第几次执行尝试。 */
    val attemptNo: Int,
    /** 执行结果。 */
    val result: Map<String, Any?>?,
    /** 错误信息。 */
    val errorMessage: String?,
    /** 开始时间戳，单位为毫秒。 */
    val startTime: Long?,
    /** 结束时间戳，单位为毫秒。 */
    val endTime: Long?
)
