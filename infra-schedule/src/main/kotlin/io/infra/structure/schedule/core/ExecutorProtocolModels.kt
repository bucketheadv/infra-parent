package io.infra.structure.schedule.core

/** 执行器终止请求体。 */
data class ExecutorCancelRequest(val logId: Long)

/** 执行器终止响应体。 */
data class ExecutorCancelResponse(val cancelled: Boolean)

/** 执行器任务存活查询请求体。 */
data class ExecutorRunningRequest(val logId: Long)

/** 执行器任务存活查询响应体。 */
data class ExecutorRunningResponse(val running: Boolean)

/** 执行器存活探活响应。 */
data class ExecutorBeatResponse(val alive: Boolean = true)

/** 执行器空闲检测请求。 */
data class ExecutorIdleBeatRequest(val jobId: Long)

/** 执行器空闲检测响应。 */
data class ExecutorIdleBeatResponse(val idle: Boolean)
