package io.infra.structure.sso.login.web

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

/** 提供自定义表单登录视图；实际用户名密码校验由 Spring Security 过滤器完成。 */
@Controller
class LoginPageController {

    /** 返回登录模板，CSRF token 与错误状态由 Thymeleaf/Spring Security 自动渲染。 */
    @GetMapping("/login")
    fun login(): String = "login"
}
