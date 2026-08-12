package io.infra.structure.schedule.admin.controller

import io.infra.structure.schedule.properties.InfraScheduleProperties
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

/** 为管理页面注入鉴权配置，供前端 API 请求携带 Admin Token。 */
@ControllerAdvice(basePackages = ["io.infra.structure.schedule.admin.controller"])
class ScheduleAdminPageModelAdvice(
    private val properties: InfraScheduleProperties
) {
    @ModelAttribute("adminAuthEnabled")
    fun adminAuthEnabled(): Boolean = properties.management.authEnabled

    @ModelAttribute("adminAccessToken")
    fun adminAccessToken(): String? =
        properties.management.accessToken?.takeIf { properties.management.authEnabled && it.isNotBlank() }
}
