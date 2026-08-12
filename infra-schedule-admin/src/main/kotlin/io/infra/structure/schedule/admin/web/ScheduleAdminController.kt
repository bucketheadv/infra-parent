package io.infra.structure.schedule.admin.web

import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.HandleLogAppendRequest
import io.infra.structure.schedule.core.LogFinishRequest
import io.infra.structure.schedule.core.LogStartedRequest
import io.infra.structure.schedule.core.SCHEDULE_ACCESS_TOKEN_HEADER
import io.infra.structure.schedule.model.BlockStrategy
import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.ExecutorAddressMode
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleExecutorDraft
import io.infra.structure.schedule.model.ScheduleJobDraft
import io.infra.structure.schedule.model.ScheduleType
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.admin.service.ScheduleService
import io.infra.structure.schedule.web.ScheduleWebPaths
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

/**
 * 调度中心管理 REST 接口；由 [io.infra.structure.schedule.admin.autoconfigure.InfraScheduleAdminAutoConfiguration] 注册。
 */
@RestController
@RequestMapping(ScheduleWebPaths.API_ROOT)
class ScheduleAdminController(
    private val scheduleService: ScheduleService,
    private val executorRegistry: ExecutorRegistry,
    private val properties: InfraScheduleProperties
) {
    @GetMapping(ScheduleWebPaths.JOBS)
    fun jobs() = scheduleService.jobs()

    @GetMapping(ScheduleWebPaths.JOB_BY_ID)
    fun job(@PathVariable id: Long) = scheduleService.job(id)

    @PostMapping(ScheduleWebPaths.JOBS)
    fun create(@Valid @RequestBody request: ScheduleJobRequest) = scheduleService.create(request.toDraft())

    @PutMapping(ScheduleWebPaths.JOB_BY_ID)
    fun update(@PathVariable id: Long, @Valid @RequestBody request: ScheduleJobRequest) =
        scheduleService.update(id, request.toDraft())

    @DeleteMapping(ScheduleWebPaths.JOB_BY_ID)
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> =
        if (scheduleService.delete(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping(ScheduleWebPaths.JOB_TRIGGER)
    fun trigger(@PathVariable id: Long): TriggerResponse = TriggerResponse(scheduleService.triggerNow(id))

    @PostMapping(ScheduleWebPaths.JOB_STATUS)
    fun changeStatus(@PathVariable id: Long, @Valid @RequestBody request: JobStatusRequest) =
        scheduleService.setStatus(id, request.status)

    @GetMapping(ScheduleWebPaths.JOB_LOGS)
    fun logs(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) pageSize: Int
    ) = scheduleService.executionLogs(id, page, pageSize)

    @GetMapping(ScheduleWebPaths.LOGS)
    fun queryLogs(
        @RequestParam(required = false) jobId: Long?,
        @RequestParam(required = false) executorId: Long?,
        @RequestParam(required = false) status: ExecutionStatus?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) pageSize: Int
    ) = scheduleService.queryExecutionLogs(
        ExecutionLogQuery(
            jobId = jobId,
            executorId = executorId,
            status = status,
            triggerTimeFrom = from,
            triggerTimeTo = to
        ),
        page = page,
        pageSize = pageSize
    )

    @GetMapping(ScheduleWebPaths.LOG_BY_ID)
    fun log(@PathVariable id: Long) = try {
        scheduleService.executionLog(id)
    } catch (exception: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.NOT_FOUND, exception.message)
    }

    @PostMapping(ScheduleWebPaths.LOG_CANCEL)
    fun cancelLog(@PathVariable id: Long): CancelExecutionResponse {
        val cancelled = try {
            scheduleService.cancelRunningLog(id)
        } catch (exception: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, exception.message)
        }
        if (!cancelled) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "该日志当前不在排队或运行中")
        }
        return CancelExecutionResponse(cancelled = true)
    }

    @PostMapping(ScheduleWebPaths.LOG_STARTED)
    fun markLogStarted(
        @PathVariable id: Long,
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody(required = false) request: LogStartedRequest?
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        scheduleService.markExecutionStarted(id, request?.message ?: "执行中")
        return ResponseEntity.noContent().build()
    }

    @PostMapping(ScheduleWebPaths.LOG_FINISH)
    fun finishLogFromExecutor(
        @PathVariable id: Long,
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody request: LogFinishRequest
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        scheduleService.completeExecutionFromExecutor(
            logId = id,
            success = request.success,
            message = request.message,
            discarded = request.discarded,
            cancelled = request.cancelled,
            durationMillis = request.durationMillis
        )
        return ResponseEntity.noContent().build()
    }

    @PostMapping(ScheduleWebPaths.LOG_HANDLE_APPEND)
    fun appendHandleLog(
        @PathVariable id: Long,
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody request: HandleLogAppendRequest
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        if (request.lines.isEmpty()) return ResponseEntity.noContent().build()
        if (!scheduleService.appendHandleLog(id, request.lines)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "执行日志不存在: $id")
        }
        return ResponseEntity.noContent().build()
    }

    @GetMapping(ScheduleWebPaths.JOB_NEXT_TRIGGERS)
    fun nextTriggers(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "10") @Min(1) count: Int
    ): NextTriggersResponse = NextTriggersResponse(scheduleService.nextTriggerTimes(id, count))

    @PostMapping(ScheduleWebPaths.JOB_NEXT_TRIGGERS_PREVIEW)
    fun previewNextTriggers(
        @Valid @RequestBody request: SchedulePreviewRequest
    ): NextTriggersResponse = NextTriggersResponse(
        scheduleService.previewNextTriggerTimes(request.toDraft(), request.count)
    )

    @PostMapping(ScheduleWebPaths.HEARTBEAT)
    fun heartbeat(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @Valid @RequestBody request: ExecutorHeartbeatRequest
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        executorRegistry.heartbeat(request.executorGroup, request.executorName, request.address)
        return ResponseEntity.noContent().build()
    }

    @PostMapping(ScheduleWebPaths.OFFLINE)
    fun offline(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @Valid @RequestBody request: ExecutorOfflineRequest
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        executorRegistry.markOffline(request.executorGroup, request.address)
        return ResponseEntity.noContent().build()
    }

    @GetMapping(ScheduleWebPaths.EXECUTORS_BY_GROUP)
    fun executors(@PathVariable group: String) = executorRegistry.heartbeats(group)

    @GetMapping(ScheduleWebPaths.EXECUTORS)
    fun allExecutors(): List<ExecutorHeartbeat> = executorRegistry.registeredExecutors()

    @PostMapping(ScheduleWebPaths.EXECUTORS)
    fun createExecutor(@Valid @RequestBody request: ScheduleExecutorRequest): ExecutorHeartbeat =
        executorRegistry.createExecutor(request.toDraft())

    @PutMapping(ScheduleWebPaths.EXECUTOR_BY_ID)
    fun updateExecutor(@PathVariable id: Long, @Valid @RequestBody request: ScheduleExecutorRequest): ExecutorHeartbeat =
        executorRegistry.updateExecutor(id, request.toDraft())

    @DeleteMapping(ScheduleWebPaths.EXECUTOR_BY_ID)
    fun deleteExecutor(@PathVariable id: Long): ResponseEntity<Unit> =
        if (executorRegistry.deleteExecutor(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @GetMapping(ScheduleWebPaths.EXECUTOR_NODES)
    fun executorNodes(@PathVariable group: String) = executorRegistry.registeredExecutors(group)

    @PostMapping(ScheduleWebPaths.EXECUTOR_STATUS)
    fun changeExecutorStatus(@PathVariable id: Long, @Valid @RequestBody request: ExecutorStatusRequest): ResponseEntity<Unit> =
        if (executorRegistry.setStatus(id, request.status)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    private fun requireExecutorToken(accessToken: String?) {
        if (!properties.executor.authEnabled) return
        val expectedToken = properties.executor.accessToken?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "调度中心未配置执行器访问令牌")
        if (accessToken != expectedToken) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "执行器访问令牌无效")
        }
    }
}

data class ScheduleJobRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val handler: String,
    @field:NotNull val scheduleType: ScheduleType,
    @field:NotNull val executorId: Long,
    val parameters: String = "",
    val cron: String? = null,
    val fixedRateMillis: Long? = null,
    val status: JobStatus = JobStatus.DISABLED,
    val routeStrategy: RouteStrategy = RouteStrategy.FAILOVER,
    val blockStrategy: BlockStrategy = BlockStrategy.SERIAL,
    val resident: Boolean = false,
    @field:Min(0) val maxRetryCount: Int = 0,
    @field:Min(0) val retryIntervalMillis: Long = 1_000,
    @field:Min(0) val timeoutSeconds: Long = 0
) {
    fun toDraft() = ScheduleJobDraft(
        name = name, executorGroup = "default", executorId = executorId, handler = handler, parameters = parameters,
        scheduleType = scheduleType, cron = cron, fixedRateMillis = fixedRateMillis, status = status,
        routeStrategy = routeStrategy, blockStrategy = blockStrategy, resident = resident,
        maxRetryCount = maxRetryCount, retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds
    )
}

data class JobStatusRequest(@field:NotNull val status: JobStatus)

data class ExecutorHeartbeatRequest(
    @field:NotBlank val executorGroup: String = "default",
    @field:NotBlank val executorName: String,
    val address: String? = null
)

data class ExecutorOfflineRequest(
    @field:NotBlank val executorGroup: String,
    val address: String? = null
)

data class ExecutorStatusRequest(@field:NotNull val status: ExecutorStatus)

data class ScheduleExecutorRequest(
    @field:NotBlank val executorGroup: String,
    @field:NotBlank val executorName: String,
    val address: String? = null,
    @field:NotNull val addressMode: ExecutorAddressMode = ExecutorAddressMode.AUTO_REGISTER,
    @field:NotNull val status: ExecutorStatus = ExecutorStatus.ENABLED
) {
    fun toDraft() = ScheduleExecutorDraft(
        executorGroup,
        executorName,
        address?.takeIf { it.isNotBlank() },
        addressMode,
        status
    )
}

data class TriggerResponse(val accepted: Boolean)

data class CancelExecutionResponse(val cancelled: Boolean)

data class NextTriggersResponse(val times: List<Long>)

data class SchedulePreviewRequest(
    @field:NotNull val scheduleType: ScheduleType,
    val cron: String? = null,
    val fixedRateMillis: Long? = null,
    @field:Min(1) val count: Int = 10
) {
    fun toDraft() = ScheduleJobDraft(
        name = "preview",
        handler = "preview",
        scheduleType = scheduleType,
        cron = cron,
        fixedRateMillis = fixedRateMillis
    )
}
