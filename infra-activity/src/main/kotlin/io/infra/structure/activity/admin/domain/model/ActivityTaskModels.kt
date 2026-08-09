package io.infra.structure.activity.admin.domain.model

/** 可配置任务对应的业务处理器类型。 */
enum class ActivityTaskHandlerType(
    /** 管理后台展示名称。 */
    val displayName: String
) {
    /** 活动开始前的预热通知。 */
    WARMUP_NOTICE("预热通知"),
    /** 活动结束后的奖励发放。 */
    REWARD_GRANT("奖励发放"),
    /** 活动有效期内的定期巡检。 */
    ACTIVITY_PROGRESS_CHECK("活动巡检")
}

/** 任务的触发方式。 */
enum class ActivityTaskTriggerType {
    /** 仅允许运营人员手动触发。 */
    MANUAL,
    /** 在指定的一个或多个时间点触发。 */
    FIXED_TIMES,
    /** 根据 Cron 表达式触发。 */
    CRON,
    /** 相对活动开始时间按偏移量触发。 */
    ACTIVITY_START_OFFSET,
    /** 相对活动结束时间按偏移量触发。 */
    ACTIVITY_END_OFFSET,
    /** 在活动有效期窗口内按固定间隔触发。 */
    INTERVAL_WINDOW
}

/** 活动任务的调度状态。 */
enum class ActivityTaskStatus {
    /** 等待下一次触发。 */
    PENDING,
    /** 已被某个实例租约抢占并正在执行。 */
    RUNNING,
    /** 不再存在后续自动触发时间。 */
    COMPLETED,
    /** 已取消，不再执行。 */
    CANCELLED,
    /** 最终执行失败。 */
    FAILED
}

/** 单次任务执行的结果状态。 */
enum class ActivityTaskExecutionStatus {
    /** 已创建执行记录，尚未开始。 */
    PENDING,
    /** 正在执行。 */
    RUNNING,
    /** 执行成功。 */
    SUCCESS,
    /** 执行失败。 */
    FAILED
}

/** 触发一次任务的来源。 */
enum class ActivityTaskTriggerSource {
    /** 调度器自动触发。 */
    SCHEDULED,
    /** 管理后台手动触发。 */
    MANUAL,
    /** 上次失败后的重试触发。 */
    RETRY
}

/** 任务模板或活动模板绑定时使用的触发配置。 */
data class ActivityTaskTriggerConfig(
    /** Cron 表达式，仅 CRON 触发方式使用。 */
    val cron: String? = null,
    /** 指定的触发时间戳列表，单位为毫秒，仅 FIXED_TIMES 使用。 */
    val fixedTimes: List<Long>? = null,
    /** 相对活动开始或结束时间的偏移量，单位为毫秒。 */
    val offsetMillis: Long? = null,
    /** 间隔执行的周期，单位为毫秒，仅 INTERVAL_WINDOW 使用。 */
    val intervalMillis: Long? = null,
    /** 间隔窗口相对活动开始时间的偏移量，单位为毫秒。 */
    val windowStartOffsetMillis: Long? = null,
    /** 间隔窗口相对活动结束时间的偏移量，单位为毫秒。 */
    val windowEndOffsetMillis: Long? = null,
    /** Cron 表达式使用的时区。 */
    val timezone: String? = null
)
