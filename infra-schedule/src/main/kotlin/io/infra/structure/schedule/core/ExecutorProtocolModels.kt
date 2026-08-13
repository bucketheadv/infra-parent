package io.infra.structure.schedule.core

/** 执行器终止请求体。 */
data class ExecutorCancelRequest(
    /** 要精确终止的单次执行日志 ID，不按 jobId 批量中断。 */
    val logId: Long
)

/** 执行器终止响应体。 */
data class ExecutorCancelResponse(
    /** 是否找到排队或运行中的对应 logId 并已发出取消信号。 */
    val cancelled: Boolean
)

/** 执行器任务存活查询请求体。 */
data class ExecutorRunningRequest(
    /** 要查询的单次执行日志 ID。 */
    val logId: Long
)

/** 执行器任务存活查询响应体。 */
data class ExecutorRunningResponse(
    /** true 表示该 logId 仍在队列中或 Handler 正在运行。 */
    val running: Boolean
)

/** 执行器存活探活响应。 */
data class ExecutorBeatResponse(
    /** true 表示 HTTP 服务可用，不等同于某个任务正在运行。 */
    val alive: Boolean = true
)

/** 执行器空闲检测请求。 */
data class ExecutorIdleBeatRequest(
    /** 需要检查队列和 Handler 是否空闲的任务 ID。 */
    val jobId: Long
)

/** 执行器空闲检测响应。 */
data class ExecutorIdleBeatResponse(
    /** true 表示该 jobId 没有运行中的 Handler 且没有排队触发。 */
    val idle: Boolean
)
