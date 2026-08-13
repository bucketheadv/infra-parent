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
import org.springframework.dao.DataIntegrityViolationException
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
    fun trigger(@PathVariable id: Long): TriggerResponse {
        val job = try {
            scheduleService.job(id)
        } catch (exception: IllegalStateException) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, exception.message)
        }
        val executorId = job.executorId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "任务未绑定执行器，无法立即执行")
        if (executorRegistry.runnableNodes(executorId).isEmpty()) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "执行器不可用：请确认执行器已启用，并已配置手动地址或完成实例心跳注册"
            )
        }
        if (!scheduleService.triggerNow(id)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变更，请刷新页面后重试")
        }
        return TriggerResponse(accepted = true)
    }

    @PostMapping(ScheduleWebPaths.JOB_STATUS)
    fun changeStatus(@PathVariable id: Long, @Valid @RequestBody request: JobStatusRequest) = try {
        scheduleService.setStatus(id, request.status)
    } catch (exception: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, exception.message ?: "任务状态变更冲突")
    }

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
    fun createExecutor(@Valid @RequestBody request: ScheduleExecutorRequest): ExecutorHeartbeat = try {
        executorRegistry.createExecutor(request.toDraft())
    } catch (exception: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, exception.message)
    } catch (exception: DataIntegrityViolationException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, "执行器分组已存在: ${request.executorGroup}")
    }

    @PutMapping(ScheduleWebPaths.EXECUTOR_BY_ID)
    fun updateExecutor(@PathVariable id: Long, @Valid @RequestBody request: ScheduleExecutorRequest): ExecutorHeartbeat = try {
        executorRegistry.updateExecutor(id, request.toDraft())
    } catch (exception: IllegalArgumentException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, exception.message ?: "执行器配置冲突")
    }

    @DeleteMapping(ScheduleWebPaths.EXECUTOR_BY_ID)
    fun deleteExecutor(@PathVariable id: Long): ResponseEntity<Unit> = try {
        if (executorRegistry.deleteExecutor(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
    } catch (exception: IllegalStateException) {
        throw ResponseStatusException(HttpStatus.CONFLICT, exception.message)
    }

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

/** 管理端创建或编辑任务的请求载荷；运行期领取状态不允许由页面直接提交。 */
data class ScheduleJobRequest(
    /** 管理后台展示的任务名称。 */
    @field:NotBlank val name: String,
    /** 执行器应用内已注册的 Handler 名称。 */
    @field:NotBlank val handler: String,
    /** 触发方式，决定使用 Cron 还是固定间隔。 */
    @field:NotNull val scheduleType: ScheduleType,
    /** 目标执行器分组的数据库自增 ID。 */
    @field:NotNull val executorId: Long,
    /** 原样透传给 Handler 的参数文本。 */
    val parameters: String = "",
    /** Spring 六段 Cron 表达式，仅 CRON 类型生效。 */
    val cron: String? = null,
    /** 固定间隔毫秒数，仅 FIXED_RATE 类型生效。 */
    val fixedRateMillis: Long? = null,
    /** 初始调度状态；保存不会改变既有任务的运行状态。 */
    val status: JobStatus = JobStatus.DISABLED,
    /** 分组内执行器地址选择策略。 */
    val routeStrategy: RouteStrategy = RouteStrategy.FAILOVER,
    /** 同一执行器内该任务发生重叠时的处理策略。 */
    val blockStrategy: BlockStrategy = BlockStrategy.SERIAL,
    /** 是否为长期运行任务；影响丢弃触发时是否保留跳过日志。 */
    val resident: Boolean = false,
    /** Handler 明确失败时允许的额外调用次数。 */
    @field:Min(0) val maxRetryCount: Int = 0,
    /** 同一次触发的两次明确失败调用之间等待的毫秒数。 */
    @field:Min(0) val retryIntervalMillis: Long = 1_000,
    /** 单次 Handler 调用最长秒数；0 交由系统级默认上限控制。 */
    @field:Min(0) val timeoutSeconds: Long = 0
) {
    /** 转换为不含 ID、时间和租约字段的领域草稿。 */
    fun toDraft() = ScheduleJobDraft(
        name = name, executorGroup = "default", executorId = executorId, handler = handler, parameters = parameters,
        scheduleType = scheduleType, cron = cron, fixedRateMillis = fixedRateMillis, status = status,
        routeStrategy = routeStrategy, blockStrategy = blockStrategy, resident = resident,
        maxRetryCount = maxRetryCount, retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds
    )
}

/** 修改任务定时调度启停状态的请求。 */
data class JobStatusRequest(
    /** ENABLED 启动后续定时触发；DISABLED 停止后续定时触发，但不禁止手动执行。 */
    @field:NotNull val status: JobStatus
)

/** 自动注册地址模式下由执行器周期性上报的心跳载荷。 */
data class ExecutorHeartbeatRequest(
    /** 执行器全局唯一分组标识。 */
    @field:NotBlank val executorGroup: String = "default",
    /** 仅用于页面展示的执行器名称。 */
    @field:NotBlank val executorName: String,
    /** 当前实例对 Admin 可访问的地址；可为空以支持本地执行器。 */
    val address: String? = null
)

/** 执行器优雅下线时的通知载荷。 */
data class ExecutorOfflineRequest(
    /** 要下线的执行器分组标识。 */
    @field:NotBlank val executorGroup: String,
    /** 非空时只剔除该实例地址；为空时剔除分组全部自动注册地址。 */
    val address: String? = null
)

/** 修改执行器是否可被路由的请求。 */
data class ExecutorStatusRequest(
    /** DISABLED 时仍允许心跳，但新任务不再路由到该执行器。 */
    @field:NotNull val status: ExecutorStatus
)

/** 管理端创建或编辑执行器分组的请求载荷。 */
data class ScheduleExecutorRequest(
    /** 全局唯一分组标识，对应任务配置中的执行器选择项。 */
    @field:NotBlank val executorGroup: String,
    /** 仅用于管理页面展示的名称，可重复。 */
    @field:NotBlank val executorName: String,
    /** 手动地址模式的固定地址列表；自动模式由心跳维护。 */
    val address: String? = null,
    /** 地址由管理员配置，或由执行器心跳自动注册。 */
    @field:NotNull val addressMode: ExecutorAddressMode = ExecutorAddressMode.AUTO_REGISTER,
    /** 是否允许分组参与后续任务路由。 */
    @field:NotNull val status: ExecutorStatus = ExecutorStatus.ENABLED
) {
    /** 转换为领域层可持久化的执行器草稿。 */
    fun toDraft() = ScheduleExecutorDraft(
        executorGroup,
        executorName,
        address?.takeIf { it.isNotBlank() },
        addressMode,
        status
    )
}

/** 手动触发入队结果。 */
data class TriggerResponse(
    /** true 表示已写入可靠 Outbox；不代表 Handler 已开始或已成功。 */
    val accepted: Boolean
)

/** 单次执行终止请求结果。 */
data class CancelExecutionResponse(
    /** true 表示已进入取消确认链路或已完成，不代表远端 Handler 已立即退出。 */
    val cancelled: Boolean
)

/** 下次计划触发时间预览。 */
data class NextTriggersResponse(
    /** 按升序排列的 Unix 毫秒时间戳列表。 */
    val times: List<Long>
)

/** 根据尚未保存的页面配置预览未来触发时间的请求。 */
data class SchedulePreviewRequest(
    /** 需要预览的触发方式。 */
    @field:NotNull val scheduleType: ScheduleType,
    /** CRON 类型使用的 Spring 六段 Cron 表达式。 */
    val cron: String? = null,
    /** FIXED_RATE 类型使用的固定间隔毫秒数。 */
    val fixedRateMillis: Long? = null,
    /** 希望返回的未来时间数量，服务端会限制有效范围。 */
    @field:Min(1) val count: Int = 10
) {
    /** 以占位任务构造可供时间计算器校验的领域草稿。 */
    fun toDraft() = ScheduleJobDraft(
        name = "preview",
        handler = "preview",
        scheduleType = scheduleType,
        cron = cron,
        fixedRateMillis = fixedRateMillis
    )
}
