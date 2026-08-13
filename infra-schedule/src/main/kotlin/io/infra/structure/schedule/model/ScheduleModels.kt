package io.infra.structure.schedule.model

import com.fasterxml.jackson.annotation.JsonCreator

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

/**
 * 阻塞处理策略（对齐 xxl-job `ExecutorBlockStrategyEnum`）。
 * 生效点在**执行器**按 jobId 的 JobThread，而非调度中心。
 */
enum class BlockStrategy {
    /** 单机串行：新触发进入该 job 的执行队列，按序执行。 */
    SERIAL,
    /** 丢弃后续调度：若该 job 正在执行或队列非空，则丢弃本次触发。 */
    DISCARD_LATER,
    /** 覆盖之前调度：若该 job 忙碌，则终止旧线程/清空队列，再执行本次触发。 */
    COVER_EARLY
}

/**
 * 路由策略（对齐 xxl-job `ExecutorRouteStrategyEnum`）。
 * 旧库值 `ROUND_ROBIN` / `BROADCAST` 读取时兼容映射为 [ROUND] / [SHARDING_BROADCAST]。
 */
enum class RouteStrategy {
    /** 第一个 */
    FIRST,
    /** 最后一个 */
    LAST,
    /** 轮询 */
    ROUND,
    /** 随机 */
    RANDOM,
    /** 一致性 HASH */
    CONSISTENT_HASH,
    /** 最不经常使用 */
    LEAST_FREQUENTLY_USED,
    /** 最近最久未使用 */
    LEAST_RECENTLY_USED,
    /** 故障转移：按序探活，选第一个心跳成功的节点 */
    FAILOVER,
    /** 忙碌转移：按序空闲检测，选第一个空闲节点 */
    BUSYOVER,
    /** 分片广播 */
    SHARDING_BROADCAST;

    companion object {
        /** 解析路由策略，兼容历史枚举名。 */
        @JvmStatic
        @JsonCreator
        fun parse(raw: String): RouteStrategy = when (raw) {
            "ROUND_ROBIN" -> ROUND
            "BROADCAST" -> SHARDING_BROADCAST
            else -> valueOf(raw)
        }
    }
}

/** 单次执行记录的最终或中间状态。 */
enum class ExecutionStatus {
    /** 已调度，在执行器 JobThread 队列中等待（串行常见）。 */
    QUEUED,
    /** 处理器正在执行。 */
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
    LOST;

    /** 排队或执行中，尚未到终态。 */
    fun isActive(): Boolean = this == QUEUED || this == RUNNING
}

/** 调度触发 Outbox 的投递状态。 */
enum class TriggerOutboxStatus {
    /** 已在领取任务的事务中写入，等待调度节点投递。 */
    PENDING,
    /** 某调度节点正在投递，租约超时后可由其他节点接管。 */
    PROCESSING,
    /** 已提交给本节点的执行工作线程；执行审计日志另行记录最终结果。 */
    DISPATCHED,
    /** 对应任务已删除或被禁用，不再投递。 */
    CANCELLED
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
     * 常驻任务在丢弃后续策略下被执行器丢弃时，不保留跳过日志。
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
    /** 是否常驻任务；常驻时丢弃后续不保留跳过日志。 */
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
    val logId: Long? = null,
    /** 阻塞策略；由执行器 JobThread 解释，默认单机串行。 */
    val blockStrategy: BlockStrategy = BlockStrategy.SERIAL,
    /**
     * 调度中心允许本次调用持续的最长时间（毫秒）。
     * 执行器 HTTP 客户端据此设置不短于该值的响应超时，避免网络层先于任务超时中断调用。
     */
    val executionTimeoutMillis: Long = 0
)

/** 任务处理器返回给调度器的执行结果。 */
data class JobExecutionResult(
    /** 是否执行成功。 */
    val success: Boolean,
    /** 可记录到执行日志的简短结果信息。 */
    val message: String? = null,
    /** 被阻塞策略丢弃（DISCARD_LATER）时为 true。 */
    val discarded: Boolean = false,
    /** 被覆盖或主动终止时为 true。 */
    val cancelled: Boolean = false
) {
    companion object {
        /** 构造成功结果；[message] 为可选返回值，由调度侧拼进日志。 */
        fun success(message: String? = null) = JobExecutionResult(true, message)
        /** 构造失败结果。 */
        fun failure(message: String) = JobExecutionResult(false, message)
        /** 构造被阻塞策略丢弃的结果。 */
        fun discarded(message: String) = JobExecutionResult(false, message, discarded = true)
        /** 构造被取消 / 覆盖的结果。 */
        fun cancelled(message: String) = JobExecutionResult(false, message, cancelled = true)
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
    /** 分页偏移量，从 0 开始。 */
    val offset: Int = 0,
    /** 每页条数，服务端会限制在 1..1000。 */
    val limit: Int = 20
)

/** 执行日志分页结果。 */
data class ExecutionLogPage(
    /** 当前页记录。 */
    val items: List<JobExecutionLog>,
    /** 符合条件的总条数。 */
    val total: Long,
    /** 当前页码，从 1 开始。 */
    val page: Int,
    /** 每页条数。 */
    val pageSize: Int
) {
    /** 总页数。 */
    val totalPages: Int
        get() = if (pageSize <= 0 || total <= 0) 0 else ((total + pageSize - 1) / pageSize).toInt()
}

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

/**
 * 可靠触发 Outbox 记录。
 *
 * 它与任务下次触发时间在同一数据库事务中写入；调度节点重启后，过期租约会让其他节点重新领取。
 */
data class ScheduleTriggerOutbox(
    /** 数据库自增主键。 */
    val id: Long = 0,
    /** 对应任务主键。 */
    val jobId: Long,
    /** 本次计划触发时间。 */
    val triggerTime: Long,
    /** 当前投递状态。 */
    val status: TriggerOutboxStatus = TriggerOutboxStatus.PENDING,
    /** 当前投递租约持有调度节点。 */
    val claimOwner: String? = null,
    /** 当前投递租约失效时间。 */
    val claimUntil: Long? = null,
    /** 已尝试投递次数。 */
    val attemptCount: Int = 0,
    /** 最近一次投递失败原因。 */
    val lastError: String? = null,
    /** 创建时间。 */
    val createTime: Long,
    /** 最近一次状态更新时间。 */
    val updateTime: Long
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
