package io.infra.structure.sso.login.web

import io.infra.structure.sso.login.authentication.SsoLoginUserDetails
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** 登录中心默认首页，展示当前已认证账户的基础身份与角色信息。 */
@Controller
class LoginHomeController {

    /** 根路径受安全链保护，因此进入该方法时 Authentication 已完成认证。 */
    @GetMapping("/")
    fun home(authentication: Authentication, model: Model): String {
        val user = authentication.principal as? SsoLoginUserDetails
        model.addAttribute("subject", user?.userId?.toString() ?: authentication.name)
        model.addAttribute("username", user?.username ?: authentication.name)
        model.addAttribute("email", user?.email ?: "未提供")
        model.addAttribute("authorities", authentication.authorities.mapNotNull { it.authority }.sorted())
        return "index"
    }
}
