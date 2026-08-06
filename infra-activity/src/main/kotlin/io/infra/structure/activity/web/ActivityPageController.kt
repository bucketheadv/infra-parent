package io.infra.structure.activity.web

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/**
 * 活动业务模块的已登录首页控制器。
 *
 * 此页面展示 OIDC 用户信息和 Spring Security 当前授予的权限。
 */
@Controller
class ActivityPageController {

    /**
     * 渲染登录成功后的首页。
     *
     * 当认证主体是 OIDC 用户时优先读取标准声明；否则回退到 Spring Security 的认证名称，
     * 以兼容通过 Bearer Token 访问时的认证主体。
     */
    @GetMapping("/")
    fun index(authentication: Authentication, model: Model): String {
        val oidcUser = authentication.principal as? OidcUser
        model.addAttribute("username", oidcUser?.getClaimAsString("preferred_username") ?: authentication.name)
        model.addAttribute("subject", oidcUser?.subject ?: authentication.name)
        model.addAttribute("email", oidcUser?.email ?: "未提供")
        model.addAttribute("authorities", authentication.authorities.mapNotNull { it.authority }.sorted())
        return "index"
    }
}
