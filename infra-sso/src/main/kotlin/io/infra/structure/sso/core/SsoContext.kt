package io.infra.structure.sso.core

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt

/** 从当前 SecurityContext 中读取已通过 SSO 认证的 JWT 用户。 */
object SsoContext {

    /**
     * 获取当前线程中已通过 Bearer JWT 认证的用户。
     *
     * 当请求来自 OAuth2 浏览器登录时认证主体为 OidcUser，此方法会返回 null；
     * 此时调用方应直接从 Authentication 或 OidcUser 中读取资料。
     */
    @JvmStatic
    fun currentUser(): SsoUser? {
        val authentication = SecurityContextHolder.getContext().authentication ?: return null
        val jwt = authentication.principal as? Jwt ?: return null
        if (!authentication.isAuthenticated) {
            return null
        }
        val authorities = authentication.authorities.mapNotNullTo(linkedSetOf()) { it.authority }
        return SsoUser.from(jwt, authorities)
    }

    /**
     * 获取当前 Bearer JWT 用户。
     *
     * 未认证、认证主体不是 JWT 或当前认证不可信时抛出 AccessDeniedException，
     * 便于由 Spring Security 返回统一的拒绝访问响应。
     */
    @JvmStatic
    fun requireCurrentUser(): SsoUser = currentUser()
        ?: throw AccessDeniedException("No authenticated SSO user")
}
