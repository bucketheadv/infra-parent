package io.infra.structure.schedule.web

import io.infra.structure.schedule.core.ExecutorBeatResponse
import io.infra.structure.schedule.core.ExecutorCancelRequest
import io.infra.structure.schedule.core.ExecutorCancelResponse
import io.infra.structure.schedule.core.ExecutorIdleBeatRequest
import io.infra.structure.schedule.core.ExecutorIdleBeatResponse
import io.infra.structure.schedule.core.ExecutorRunningRequest
import io.infra.structure.schedule.core.ExecutorRunningResponse
import io.infra.structure.schedule.core.ExecutorTaskTracker
import io.infra.structure.schedule.core.SCHEDULE_ACCESS_TOKEN_HEADER
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import io.infra.structure.schedule.properties.InfraScheduleProperties
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/** 接收调度中心请求的执行器协议端点。 */
@RestController
@RequestMapping(ScheduleWebPaths.EXECUTOR_ROOT)
class ScheduleExecutorController(
    private val taskTracker: ExecutorTaskTracker,
    private val properties: InfraScheduleProperties
) {
    /** 校验共享令牌后按阻塞策略入队/执行已注册处理器。 */
    @PostMapping(
        ScheduleWebPaths.RUN,
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun run(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody context: JobExecutionContext
    ): JobExecutionResult {
        requireAuthorized(accessToken)
        return taskTracker.run(context)
    }

    /** 按执行日志 ID 中断本进程内对应的 handler 线程或队列项。 */
    @PostMapping(
        ScheduleWebPaths.CANCEL,
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun cancel(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody request: ExecutorCancelRequest
    ): ExecutorCancelResponse {
        requireAuthorized(accessToken)
        return ExecutorCancelResponse(cancelled = taskTracker.cancel(request.logId))
    }

    /** 查询本进程内指定日志 ID 的 handler 是否仍在执行或排队。 */
    @PostMapping(
        ScheduleWebPaths.RUNNING,
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun running(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody request: ExecutorRunningRequest
    ): ExecutorRunningResponse {
        requireAuthorized(accessToken)
        return ExecutorRunningResponse(running = taskTracker.isRunning(request.logId))
    }

    /** 执行器进程探活（对齐 xxl-job beat）。 */
    @PostMapping(ScheduleWebPaths.BEAT, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun beat(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?
    ): ExecutorBeatResponse {
        requireAuthorized(accessToken)
        return ExecutorBeatResponse(alive = true)
    }

    /** 指定 job 是否空闲（对齐 xxl-job idleBeat）。 */
    @PostMapping(
        ScheduleWebPaths.IDLE_BEAT,
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun idleBeat(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody request: ExecutorIdleBeatRequest
    ): ExecutorIdleBeatResponse {
        requireAuthorized(accessToken)
        return ExecutorIdleBeatResponse(idle = taskTracker.isJobIdle(request.jobId))
    }

    private fun requireAuthorized(accessToken: String?) {
        if (!properties.executor.authEnabled) return
        val expectedToken = properties.executor.accessToken?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "执行器未配置访问令牌")
        if (accessToken != expectedToken) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "执行器访问令牌无效")
        }
    }
}
