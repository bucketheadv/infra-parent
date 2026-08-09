package io.infra.structure.activity.frontend.controller

import io.infra.structure.activity.frontend.dto.BaseActivityDto
import io.infra.structure.activity.frontend.service.BaseActivityService
import io.infra.structure.sso.core.SsoContext
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable

/**
 * 面向前端构建活动的泛型控制器基类。
 *
 * 具体活动 Controller 只需声明自己的路由并继承本类；用户标识始终从认证上下文读取，
 * 不接受客户端传入的用户 ID。
 */
@Validated
abstract class BaseActivityController<
    SERVICE : BaseActivityService<DATA>,
    DATA : BaseActivityDto<*>
>(
    private val activityService: SERVICE
) {

    /** 构建当前类型活动的前端响应。 */
    @GetMapping("/{activityId}")
    fun build(@PathVariable @Positive activityId: Long, authentication: Authentication): DATA =
        activityService.build(activityId, currentUserId(authentication))

    /** 从 OIDC 浏览器会话或 Bearer Token 中解析数据库用户主键。 */
    private fun currentUserId(authentication: Authentication): Long {
        val oidcUser = authentication.principal as? OidcUser
        val subject = oidcUser?.subject ?: SsoContext.currentUser()?.subject
        return subject?.toLongOrNull()
            ?: throw AccessDeniedException("当前认证用户缺少有效的数字唯一标识")
    }

    /** 将活动类型、状态和有效期等可预期校验失败转换为受控响应。 */
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidActivity(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to (exception.message ?: "活动不可访问")))
}
