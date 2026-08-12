package io.infra.structure.schedule.web

import io.infra.structure.schedule.properties.InfraScheduleProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 保护任务 CRUD、手动触发和日志查询等管理端接口。 */
class ScheduleAdminAccessInterceptor(
    private val properties: InfraScheduleProperties
) : HandlerInterceptor {
    /** 未配置管理员令牌时拒绝管理请求，防止误将后台暴露到公网。 */
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val expectedToken = properties.management.accessToken?.takeIf { it.isNotBlank() }
        if (expectedToken == null) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "管理端未配置访问令牌")
            return false
        }
        if (request.getHeader(SCHEDULE_ADMIN_TOKEN_HEADER) != expectedToken) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "管理端访问令牌无效")
            return false
        }
        return true
    }
}

/** 注册后台令牌校验，并排除由执行器令牌保护的心跳 / 离线接口。 */
class ScheduleAdminWebConfigurer(
    private val accessInterceptor: ScheduleAdminAccessInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(accessInterceptor)
            .addPathPatterns(ScheduleWebPaths.API_ALL)
            .excludePathPatterns(
                ScheduleWebPaths.EXECUTOR_HEARTBEAT,
                ScheduleWebPaths.EXECUTOR_OFFLINE,
                ScheduleWebPaths.EXECUTOR_LOG_HANDLE_APPEND_PATTERN,
                ScheduleWebPaths.EXECUTOR_LOG_STARTED_PATTERN,
                ScheduleWebPaths.EXECUTOR_LOG_FINISH_PATTERN
            )
    }
}

/** 浏览器或管理 API 调用方传递管理员令牌的 HTTP Header。 */
const val SCHEDULE_ADMIN_TOKEN_HEADER = "X-Infra-Schedule-Admin-Token"
