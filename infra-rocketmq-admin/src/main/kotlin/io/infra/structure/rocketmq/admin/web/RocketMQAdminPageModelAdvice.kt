package io.infra.structure.rocketmq.admin.web

import io.infra.structure.rocketmq.admin.properties.RocketMQAdminProperties
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** 为管理页面注入鉴权配置，供前端 API 请求携带 Admin Token。 */
@ControllerAdvice(basePackages = ["io.infra.structure.rocketmq.admin.web"])
class RocketMQAdminPageModelAdvice(
    private val properties: RocketMQAdminProperties
) {
    @ModelAttribute("adminAuthEnabled")
    fun adminAuthEnabled(): Boolean = properties.authEnabled

    @ModelAttribute("adminAccessToken")
    fun adminAccessToken(): String? =
        properties.accessToken?.takeIf { properties.authEnabled && it.isNotBlank() }

    @ModelAttribute("namesrvAddr")
    fun namesrvAddr(): String = properties.namesrvAddr
}