package io.infra.structure.schedule.web

import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.SCHEDULE_ACCESS_TOKEN_HEADER
import io.infra.structure.schedule.model.BlockStrategy
import io.infra.structure.schedule.model.ExecutionLogQuery
import io.infra.structure.schedule.model.ExecutionStatus
import io.infra.structure.schedule.model.ExecutorAddressMode
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.RouteStrategy
import io.infra.structure.schedule.model.ScheduleExecutorDraft
import io.infra.structure.schedule.model.ScheduleJobDraft
import io.infra.structure.schedule.model.ScheduleType
import io.infra.structure.schedule.service.ScheduleService
import io.infra.structure.schedule.properties.InfraScheduleProperties
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
import org.springframework.http.HttpStatus

/**
 * 管理接口默认关闭；开启前应由接入应用使用 Spring Security 或网关限制管理员访问。
 */
@RestController
@RequestMapping(ScheduleWebPaths.API_ROOT)
@ConditionalOnProperty(prefix = "infra.schedule.management", name = ["enabled"], havingValue = "true")
class ScheduleAdminController(
    private val scheduleService: ScheduleService,
    private val executorRegistry: ExecutorRegistry,
    private val properties: InfraScheduleProperties
) {
    @GetMapping(ScheduleWebPaths.JOBS)
    /** 查询全部任务定义。 */
    fun jobs() = scheduleService.jobs()

    @GetMapping(ScheduleWebPaths.JOB_BY_ID)
    /** 查询单个任务及其当前调度状态。 */
    fun job(@PathVariable id: Long) = scheduleService.job(id)

    @PostMapping(ScheduleWebPaths.JOBS)
    /** 创建任务，并由服务层校验 Cron 或固定间隔配置。 */
    fun create(@Valid @RequestBody request: ScheduleJobRequest) = scheduleService.create(request.toDraft())

    @PutMapping(ScheduleWebPaths.JOB_BY_ID)
    /** 覆盖更新任务定义。 */
    fun update(@PathVariable id: Long, @Valid @RequestBody request: ScheduleJobRequest) =
        scheduleService.update(id, request.toDraft())

    @DeleteMapping(ScheduleWebPaths.JOB_BY_ID)
    /** 删除任务；不存在时返回 404。 */
    fun delete(@PathVariable id: Long): ResponseEntity<Unit> =
        if (scheduleService.delete(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @PostMapping(ScheduleWebPaths.JOB_TRIGGER)
    /** 立即异步触发一次任务，不修改原有定时计划。 */
    fun trigger(@PathVariable id: Long): TriggerResponse = TriggerResponse(scheduleService.triggerNow(id))

    @PostMapping(ScheduleWebPaths.JOB_STATUS)
    /** 切换任务启停状态。 */
    fun changeStatus(@PathVariable id: Long, @Valid @RequestBody request: JobStatusRequest) =
        scheduleService.setStatus(id, request.status)

    @GetMapping(ScheduleWebPaths.JOB_LOGS)
    /** 获取指定任务最近的执行审计记录。 */
    fun logs(@PathVariable id: Long, @RequestParam(defaultValue = "100") @Min(1) limit: Int) =
        scheduleService.executionLogs(id, limit)

    @GetMapping(ScheduleWebPaths.LOGS)
    /** 按任务、执行器、状态与触发时间范围查询执行日志。 */
    fun queryLogs(
        @RequestParam(required = false) jobId: Long?,
        @RequestParam(required = false) executorId: Long?,
        @RequestParam(required = false) status: ExecutionStatus?,
        @RequestParam(required = false) from: Long?,
        @RequestParam(required = false) to: Long?,
        @RequestParam(defaultValue = "100") @Min(1) limit: Int
    ) = scheduleService.queryExecutionLogs(
        ExecutionLogQuery(
            jobId = jobId,
            executorId = executorId,
            status = status,
            triggerTimeFrom = from,
            triggerTimeTo = to,
            limit = limit
        )
    )

    @GetMapping(ScheduleWebPaths.JOB_NEXT_TRIGGERS)
    /** 预览任务接下来若干次调度时间。 */
    fun nextTriggers(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "10") @Min(1) count: Int
    ): NextTriggersResponse = NextTriggersResponse(scheduleService.nextTriggerTimes(id, count))

    @PostMapping(ScheduleWebPaths.JOB_NEXT_TRIGGERS_PREVIEW)
    /** 按当前编辑中的 Cron / 固定间隔配置预览接下来若干次调度时间。 */
    fun previewNextTriggers(
        @Valid @RequestBody request: SchedulePreviewRequest
    ): NextTriggersResponse = NextTriggersResponse(
        scheduleService.previewNextTriggerTimes(request.toDraft(), request.count)
    )

    @PostMapping(ScheduleWebPaths.HEARTBEAT)
    /** 接收执行器心跳；管理接口必须由接入应用限制访问来源。 */
    fun heartbeat(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @Valid @RequestBody request: ExecutorHeartbeatRequest
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        executorRegistry.heartbeat(request.executorGroup, request.executorName, request.address)
        return ResponseEntity.noContent().build()
    }

    @PostMapping(ScheduleWebPaths.OFFLINE)
    /** 接收执行器主动离线；进程优雅退出时调用，立即从在线集合移除。 */
    fun offline(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @Valid @RequestBody request: ExecutorOfflineRequest
    ): ResponseEntity<Unit> {
        requireExecutorToken(accessToken)
        executorRegistry.markOffline(request.executorGroup, request.address)
        return ResponseEntity.noContent().build()
    }

    @GetMapping(ScheduleWebPaths.EXECUTORS_BY_GROUP)
    /** 查询分组内仍处于健康窗口的执行器。 */
    fun executors(@PathVariable group: String) = executorRegistry.heartbeats(group)

    @GetMapping(ScheduleWebPaths.EXECUTORS)
    /** 查询所有已登记执行器，供执行器管理和任务编辑器选择目标节点。 */
    fun allExecutors(): List<ExecutorHeartbeat> = executorRegistry.registeredExecutors()

    @PostMapping(ScheduleWebPaths.EXECUTORS)
    /** 新增一个可由调度中心路由的执行器节点。 */
    fun createExecutor(@Valid @RequestBody request: ScheduleExecutorRequest): ExecutorHeartbeat =
        executorRegistry.createExecutor(request.toDraft())

    @PutMapping(ScheduleWebPaths.EXECUTOR_BY_ID)
    /** 覆盖编辑执行器的展示名称、地址和启停状态；分组不可修改。 */
    fun updateExecutor(@PathVariable id: Long, @Valid @RequestBody request: ScheduleExecutorRequest): ExecutorHeartbeat =
        executorRegistry.updateExecutor(id, request.toDraft())

    @DeleteMapping(ScheduleWebPaths.EXECUTOR_BY_ID)
    /** 删除执行器登记信息；同 ID 的运行实例下次心跳会自动重新注册。 */
    fun deleteExecutor(@PathVariable id: Long): ResponseEntity<Unit> =
        if (executorRegistry.deleteExecutor(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()

    @GetMapping(ScheduleWebPaths.EXECUTOR_NODES)
    /** 查询分组内所有登记的执行器，包含禁用或暂时离线节点。 */
    fun executorNodes(@PathVariable group: String) = executorRegistry.registeredExecutors(group)

    @PostMapping(ScheduleWebPaths.EXECUTOR_STATUS)
    /** 启用或禁用执行器；禁用节点不会再被任务路由选择。 */
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

/** 创建或更新任务的 REST 请求体。 */
data class ScheduleJobRequest(
    /** 管理端展示名称。 */
    @field:NotBlank val name: String,
    /** 已注册任务处理器名称。 */
    @field:NotBlank val handler: String,
    /** 触发方式。 */
    @field:NotNull val scheduleType: ScheduleType,
    /** 指定执行本任务的执行器 ID。 */
    @field:NotNull val executorId: Long,
    /** 传递给处理器的原始参数。 */
    val parameters: String = "",
    /** Cron 触发表达式。 */
    val cron: String? = null,
    /** 固定触发间隔（毫秒）。 */
    val fixedRateMillis: Long? = null,
    /** 启停状态；新建默认暂停，需通过启用接口启动。 */
    val status: JobStatus = JobStatus.DISABLED,
    /** 执行器节点路由策略。 */
    val routeStrategy: RouteStrategy = RouteStrategy.FAILOVER,
    /** 重叠触发阻塞策略。 */
    val blockStrategy: BlockStrategy = BlockStrategy.SERIAL,
    /** 最大额外重试次数。 */
    @field:Min(0) val maxRetryCount: Int = 0,
    /** 重试间隔（毫秒）。 */
    @field:Min(0) val retryIntervalMillis: Long = 1_000,
    /** 单次执行超时秒数，0 表示不限制。 */
    @field:Min(0) val timeoutSeconds: Long = 0
) {
    /** 转换为不含 HTTP/校验注解的应用层任务草稿。 */
    fun toDraft() = ScheduleJobDraft(
        name = name, executorGroup = "default", executorId = executorId, handler = handler, parameters = parameters,
        scheduleType = scheduleType, cron = cron, fixedRateMillis = fixedRateMillis, status = status,
        routeStrategy = routeStrategy, blockStrategy = blockStrategy, maxRetryCount = maxRetryCount,
        retryIntervalMillis = retryIntervalMillis, timeoutSeconds = timeoutSeconds
    )
}

/** 修改任务启停状态的请求体。 */
data class JobStatusRequest(
    /** 目标启停状态。 */
    @field:NotNull val status: JobStatus
)

/** 执行器上报存活状态的请求体。 */
data class ExecutorHeartbeatRequest(
    /** 执行器分组标识，全局唯一。 */
    @field:NotBlank val executorGroup: String = "default",
    /** 管理端展示名称；首次注册时写入，后续心跳不覆盖已有名称。 */
    @field:NotBlank val executorName: String,
    /** 可选的执行器访问地址。 */
    val address: String? = null
)

/** 执行器主动离线上报的请求体。 */
data class ExecutorOfflineRequest(
    /** 即将离线的执行器分组标识。 */
    @field:NotBlank val executorGroup: String,
    /** 即将离线的实例地址；自动注册模式下用于从地址列表剔除该节点。 */
    val address: String? = null
)

/** 修改执行器启停状态的请求体。 */
data class ExecutorStatusRequest(
    /** 目标执行器状态。 */
    @field:NotNull val status: ExecutorStatus
)

/** 创建或编辑执行器节点的 REST 请求体。 */
data class ScheduleExecutorRequest(
    /** 执行器分组标识，全局唯一。 */
    @field:NotBlank val executorGroup: String,
    /** 管理端展示名称，不要求唯一。 */
    @field:NotBlank val executorName: String,
    /** 执行器访问地址，支持多个（逗号 / 换行分隔）；手动模式写入配置，自动注册模式由心跳维护。 */
    val address: String? = null,
    /** 访问地址为手动输入还是由心跳自动注册。 */
    @field:NotNull val addressMode: ExecutorAddressMode = ExecutorAddressMode.AUTO_REGISTER,
    /** 节点是否允许接收新的调度请求。 */
    @field:NotNull val status: ExecutorStatus = ExecutorStatus.ENABLED
) {
    /** 转换为不带 Web 注解的执行器编辑草稿。 */
    fun toDraft() = ScheduleExecutorDraft(
        executorGroup,
        executorName,
        address?.takeIf { it.isNotBlank() },
        addressMode,
        status
    )
}

/** 手动触发请求的提交结果。 */
data class TriggerResponse(
    /** 是否已成功提交到本地工作线程池。 */
    val accepted: Boolean
)

/** 任务接下来若干次调度时间的预览结果。 */
data class NextTriggersResponse(
    /** 按时间升序的触发时间戳（毫秒）。 */
    val times: List<Long>
)

/** 按编辑中的调度配置预览下次触发时间的请求体。 */
data class SchedulePreviewRequest(
    /** 触发方式。 */
    @field:NotNull val scheduleType: ScheduleType,
    /** Cron 触发表达式。 */
    val cron: String? = null,
    /** 固定触发间隔（毫秒）。 */
    val fixedRateMillis: Long? = null,
    /** 预览次数，默认 10。 */
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
