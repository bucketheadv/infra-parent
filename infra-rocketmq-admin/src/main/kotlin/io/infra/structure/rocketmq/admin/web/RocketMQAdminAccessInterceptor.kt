package io.infra.structure.rocketmq.admin.web

import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/** 保护 RocketMQ 管理 REST 接口。 */
class RocketMQAdminAccessInterceptor(
    private val properties: RocketMQAdminProperties
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val expectedToken = properties.accessToken?.takeIf { it.isNotBlank() }
        if (expectedToken == null) {
            response.sendError(HttpStatus.SERVICE_UNAVAILABLE.value(), "管理端未配置访问令牌")
            return false
        }
        if (request.getHeader(ROCKETMQ_ADMIN_TOKEN_HEADER) != expectedToken) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "管理端访问令牌无效")
            return false
        }
        return true
    }
}

/** 注册管理令牌校验，仅保护管理 API，不拦截 Thymeleaf 页面与静态资源。 */
class RocketMQAdminWebConfigurer(
    private val accessInterceptor: RocketMQAdminAccessInterceptor
) : WebMvcConfigurer {
    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(accessInterceptor)
            .addPathPatterns("${RocketMQAdminWebPaths.API_ROOT}/**")
    }
}

/** 浏览器或管理 API 调用方传递管理员令牌的 HTTP Header。 */
const val ROCKETMQ_ADMIN_TOKEN_HEADER = "X-Infra-RocketMQ-Admin-Token"