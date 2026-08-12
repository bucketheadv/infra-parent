package io.infra.structure.schedule.model

/** 任务触发方式。Cron 表达式采用 Spring 六段格式，首段为秒。 */
enum class ScheduleType {
    /** 由 Cron 表达式决定执行时间。 */
    CRON,
    /** 按固定毫秒间隔重复执行。 */
    FIXED_RATE
}

/** 任务在调度中心中的启停状态。 */
enum class JobStatus {
    /** 允许被调度器领取和执行。 */
    ENABLED,
    /** 不再生成新的定时触发，已执行任务不会被强制终止。 */
    DISABLED
}

/** 与 XXL-JOB 的阻塞处理语义保持一致。 */
enum class BlockStrategy {
    /** 当前任务运行时跳过后续触发，保证同一任务串行。 */
    SERIAL,
    /** 当前任务运行时直接丢弃本次触发。 */
    DISCARD_LATER,
    /** 中断当前任务并立即执行新触发。处理器应正确响应线程中断。 */
    COVER_EARLY
}

/** 在同一执行器分组中选择目标执行器的策略。 */
enum class RouteStrategy {
    /** 始终选择按执行器 ID 排序后的第一个节点。 */
    FIRST,
    /** 按节点顺序尝试，某个节点不可用或执行失败时转移到下一个，对应 xxl-job 故障转移。 */
    FAILOVER,
    /** 在健康节点间轮询。 */
    ROUND_ROBIN,
    /** 在健康节点间随机选择。 */
    RANDOM,
    /** 根据任务 ID 稳定映射到一个健康节点。 */
    CONSISTENT_HASH,
    /** 向分组内所有健康节点广播，并携带分片参数。 */
    BROADCAST
}

/** 单次执行记录的最终或中间状态。 */
enum class ExecutionStatus {
    /** 已创建但尚未完成。 */
    RUNNING,
    /** 处理器成功返回。 */
    SUCCESS,
    /** 处理器异常或重试耗尽后失败。 */
    FAILED,
    /** 因阻塞或取消策略未实际执行。 */
    SKIPPED,
    /** 超过任务配置的执行时限。 */
    TIMEOUT,
    /** 管理员主动终止执行。 */
    CANCELLED,
    /** 长时间停留在运行中被调度中心回收（节点崩溃或执行丢失）。 */
    LOST
}

/** 执行器在调度中心中的可用状态。 */
enum class ExecutorStatus {
    /** 执行器可参与任务路由和执行。 */
    ENABLED,
    /** 执行器仍可上报心跳，但不会再被调度中心选择。 */
    DISABLED
}

/** 执行器访问地址的来源。 */
enum class ExecutorAddressMode {
    /** 由管理员维护固定 IP 或域名地址。 */
    MANUAL,
    /** 由执行器启动后的心跳请求自动回填地址。 */
    AUTO_REGISTER
}

/** 调度中心持久化的完整任务定义。所有时间均为 Unix 毫秒时间戳。 */
data class ScheduleJob(
    /** 数据库自增任务 ID。 */
    val id: Long = 0,
    /** 管理端展示的任务名称。 */
    val name: String,
    /** 目标执行器分组。 */
    val executorGroup: String = "default",
    /** 指定的执行器数据库 ID；为空时兼容旧任务的分组路由。 */
    val executorId: Long? = null,
    /** [io.infra.structure.schedule.api.ScheduleHandler] 声明的处理器名称。 */
    val handler: String,
    /** 原样传递给处理器的任务参数。 */
    val parameters: String = "",
    /** 任务的触发方式。 */
    val scheduleType: ScheduleType,
    /** Cron 表达式，仅当 [scheduleType] 为 [ScheduleType.CRON] 时必填。 */
    val cron: String? = null,
    /** 固定执行间隔（毫秒），仅当 [scheduleType] 为 [ScheduleType.FIXED_RATE] 时必填。 */
    val fixedRateMillis: Long? = null,
    /** 是否允许调度器领取任务。 */
    val status: JobStatus = JobStatus.DISABLED,
    /** 执行器分组内的节点路由策略。 */
    val routeStrategy: RouteStrategy = RouteStrategy.FAILOVER,
    /** 同一任务发生重叠触发时的处理策略。 */
    val blockStrategy: BlockStrategy = BlockStrategy.SERIAL,
    /**
     * 是否常驻任务。
     * 常驻任务在串行跳过 / 丢弃后续策略下因重叠未实际执行时，不写入调度日志。
     */
    val resident: Boolean = false,
    /** 一次触发失败后的最大额外重试次数。 */
    val maxRetryCount: Int = 0,
    /** 两次重试之间的等待时间（毫秒）。 */
    val retryIntervalMillis: Long = 1_000,
    /** 单次处理器调用最长执行时间（秒）；0 表示不限制。 */
    val timeoutSeconds: Long = 0,
    /** 下一次应触发的时间；禁用任务时为 null。 */
    val nextTriggerAt: Long? = null,
    /** 最近一次定时触发的计划时间。 */
    val lastTriggerAt: Long? = null,
    /** 当前租约所属调度节点 ID。 */
    val claimOwner: String? = null,
    /** 当前租约失效时间；失效后其他节点可重新领取。 */
    val claimUntil: Long? = null,
    /** 任务创建时间。 */
    val createTime: Long,
    /** 最近一次任务定义或调度状态变更时间。 */
    val updateTime: Long
)

/** 创建或更新任务时使用的可编辑字段，不包含调度运行时状态。 */
data class ScheduleJobDraft(
    /** 管理端展示的任务名称。 */
    val name: String,
    /** 目标执行器分组。 */
    val executorGroup: String = "default",
    /** 指定的执行器数据库 ID；为空时兼容旧任务的分组路由。 */
    val executorId: Long? = null,
    /** 已注册任务处理器的名称。 */
    val handler: String,
    /** 原样传递给处理器的任务参数。 */
    val parameters: String = "",
    /** 任务触发方式。 */
    val scheduleType: ScheduleType,
    /** Cron 表达式。 */
    val cron: String? = null,
    /** 固定执行间隔（毫秒）。 */
    val fixedRateMillis: Long? = null,
    /** 初始启停状态；新建默认暂停，需显式启用后才会被调度。 */
    val status: JobStatus = JobStatus.DISABLED,
    /** 执行器节点路由策略。 */
    val routeStrategy: RouteStrategy = RouteStrategy.FAILOVER,
    /** 重叠触发处理策略。 */
    val blockStrategy: BlockStrategy = BlockStrategy.SERIAL,
    /** 是否常驻任务；常驻时串行跳过 / 丢弃后续不写调度日志。 */
    val resident: Boolean = false,
    /** 一次触发的最大额外重试次数。 */
    val maxRetryCount: Int = 0,
    /** 重试间隔（毫秒）。 */
    val retryIntervalMillis: Long = 1_000,
    /** 单次执行超时秒数，0 表示不限制。 */
    val timeoutSeconds: Long = 0
)

/** 调度器传递给任务处理器的本次执行上下文。 */
data class JobExecutionContext(
    /** 当前任务数据库 ID。 */
    val jobId: Long,
    /** 当前任务名称。 */
    val jobName: String,
    /** 当前处理器名称。 */
    val handler: String,
    /** 当前任务参数。 */
    val parameters: String,
    /** 本次触发时间。 */
    val triggerTime: Long,
    /** 广播执行时的当前分片下标，从 0 开始。 */
    val shardIndex: Int = 0,
    /** 广播执行时的总分片数。 */
    val shardTotal: Int = 1,
    /** 对应执行日志主键；执行器按此 ID 跟踪并支持远程终止。 */
    val logId: Long? = null
)

/** 任务处理器返回给调度器的执行结果。 */
data class JobExecutionResult(
    /** 是否执行成功。 */
    val success: Boolean,
    /** 可记录到执行日志的简短结果信息。 */
    val message: String? = null
) {
    companion object {
        /** 构造成功结果；[message] 为可选返回值，由调度侧拼进日志。 */
        fun success(message: String? = null) = JobExecutionResult(true, message)
        /** 构造失败结果。 */
        fun failure(message: String) = JobExecutionResult(false, message)
    }
}

/** 执行日志多条件查询参数。 */
data class ExecutionLogQuery(
    /** 按任务 ID 过滤；为空表示不限任务。 */
    val jobId: Long? = null,
    /** 按执行器数据库 ID 过滤。 */
    val executorId: Long? = null,
    /** 按执行状态过滤。 */
    val status: ExecutionStatus? = null,
    /** 触发时间下限（含），毫秒时间戳。 */
    val triggerTimeFrom: Long? = null,
    /** 触发时间上限（含），毫秒时间戳。 */
    val triggerTimeTo: Long? = null,
    /** 返回条数上限，服务端会限制在 1..1000。 */
    val limit: Int = 100
)

/** 一次任务触发在指定执行器上的执行审计记录。 */
data class JobExecutionLog(
    /** 数据库自增日志 ID。 */
    val id: Long = 0,
    /** 所属任务数据库 ID。 */
    val jobId: Long,
    /** 实际执行的执行器数据库 ID；未分发时为 null。 */
    val executorId: Long?,
    /** 任务被触发的时间。 */
    val triggerTime: Long,
    /** 执行结束时间；运行中时为 null。 */
    val finishTime: Long? = null,
    /** 本次执行状态。 */
    val status: ExecutionStatus = ExecutionStatus.RUNNING,
    /** 本次成功或失败前已执行的重试次数。 */
    val retryCount: Int = 0,
    /** 执行结果、失败原因或跳过原因。 */
    val message: String? = null,
    /** 业务执行过程日志（[io.infra.structure.schedule.api.ScheduleLogHelper] 异步上报）。 */
    val handleLog: String? = null,
    /** 本次调用的目标地址（host:port 或完整 URL）；本地执行可为空。 */
    val targetAddress: String? = null,
    /** 本次实际执行耗时（毫秒）。 */
    val durationMillis: Long? = null
)

/** 执行器上报的存活信息。 */
data class ExecutorHeartbeat(
    /** 数据库自增执行器 ID。 */
    val id: Long = 0,
    /** 执行器分组标识，全局唯一。 */
    val executorGroup: String = "default",
    /** 管理端展示名称，不要求唯一。 */
    val executorName: String,
    /**
     * 执行器访问地址列表（逗号分隔）。
     * 手动模式为配置值；自动注册模式为当前存活实例地址快照。
     */
    val address: String? = null,
    /** 执行器地址是后台手动维护还是由心跳自动上报。 */
    val addressMode: ExecutorAddressMode = ExecutorAddressMode.AUTO_REGISTER,
    /** 执行器是否允许接收新的调度请求。 */
    val status: ExecutorStatus = ExecutorStatus.ENABLED,
    /** 最近心跳时间。 */
    val lastHeartbeatTime: Long,
)

/** 创建或编辑执行器节点时可修改的字段。 */
data class ScheduleExecutorDraft(
    /** 执行器分组标识，全局唯一。 */
    val executorGroup: String,
    /** 管理端展示名称，不要求唯一。 */
    val executorName: String,
    /** 调度中心调用执行器使用的 HTTP 地址。 */
    val address: String? = null,
    /** 执行器地址的来源。 */
    val addressMode: ExecutorAddressMode = ExecutorAddressMode.AUTO_REGISTER,
    /** 节点是否允许接收新的调度请求。 */
    val status: ExecutorStatus = ExecutorStatus.ENABLED
)
