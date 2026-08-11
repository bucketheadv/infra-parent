package io.infra.structure.schedule.web

import io.infra.structure.schedule.core.HandlerRegistry
import io.infra.structure.schedule.core.SCHEDULE_ACCESS_TOKEN_HEADER
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import io.infra.structure.schedule.properties.InfraScheduleProperties
import org.springframework.http.HttpStatus
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
    private val handlerRegistry: HandlerRegistry,
    private val properties: InfraScheduleProperties
) {
    /** 校验共享令牌后在本进程执行已注册处理器。 */
    @PostMapping(ScheduleWebPaths.RUN)
    fun run(
        @RequestHeader(value = SCHEDULE_ACCESS_TOKEN_HEADER, required = false) accessToken: String?,
        @RequestBody context: JobExecutionContext
    ): JobExecutionResult {
        requireAuthorized(accessToken)
        return handlerRegistry.execute(context)
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
