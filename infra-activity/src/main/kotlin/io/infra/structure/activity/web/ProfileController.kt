package io.infra.structure.activity.web

import io.infra.structure.sso.core.SsoContext
import io.infra.structure.activity.web.model.ProfileResponse
import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 活动业务模块的用户信息接口。
 *
 * 用于同时演示浏览器 OIDC 会话和 Bearer JWT 两种认证方式下如何读取当前用户。
 */
@RestController
@RequestMapping("/api")
class ProfileController {

    /**
     * 返回当前已认证用户的基础资料和权限。
     *
     * 浏览器登录时认证主体是 OidcUser；资源服务器以 Bearer Token 认证时，
     * 则通过 SsoContext 统一解析 JWT 用户信息。
     */
    @GetMapping("/me")
    fun me(authentication: Authentication): ProfileResponse {
        val oidcUser = authentication.principal as? OidcUser
        if (oidcUser != null) {
            return ProfileResponse(
                subject = oidcUser.subject,
                username = oidcUser.getClaimAsString("preferred_username"),
                email = oidcUser.email,
                authorities = oidcUser.authorities.mapNotNullTo(linkedSetOf()) { it.authority }
            )
        }
        val user = SsoContext.requireCurrentUser()
        return ProfileResponse(user.subject, user.username, user.email, user.authorities)
    }

    /** 不需要认证的存活探测接口。 */
    @GetMapping("/public/ping")
    fun ping(): Map<String, String> = mapOf("status" to "ok")

}
