package io.infra.structure.activity.web.configuration

import io.infra.structure.activity.service.ActivityConfigurationService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 活动配置管理接口。
 *
 * 页面通过此接口维护组件、模板和活动，并在创建活动前读取模板生成的动态字段。
 */
@RestController
@RequestMapping("/api/activity")
class ActivityConfigurationApiController(
    private val activityConfigurationService: ActivityConfigurationService
) {

    /** 返回全部可复用活动组件。 */
    @GetMapping("/components")
    fun components(): List<ActivityComponentResponse> = activityConfigurationService.listComponents()

    /** 新建活动组件。 */
    @PostMapping("/components")
    fun createComponent(@RequestBody request: CreateComponentRequest): ActivityComponentResponse =
        activityConfigurationService.createComponent(request)

    /** 更新指定的活动组件。 */
    @PutMapping("/components/{componentId}")
    fun updateComponent(
        @PathVariable componentId: Long,
        @RequestBody request: CreateComponentRequest
    ): ActivityComponentResponse = activityConfigurationService.updateComponent(componentId, request)

    /** 删除未被模板或其他组件引用的活动组件。 */
    @DeleteMapping("/components/{componentId}")
    fun deleteComponent(@PathVariable componentId: Long): ResponseEntity<Void> {
        activityConfigurationService.deleteComponent(componentId)
        return ResponseEntity.noContent().build()
    }

    /** 返回全部活动模板及其组件编排。 */
    @GetMapping("/templates")
    fun templates(): List<ActivityTemplateResponse> = activityConfigurationService.listTemplates()

    /** 新建活动模板。 */
    @PostMapping("/templates")
    fun createTemplate(@RequestBody request: CreateTemplateRequest): ActivityTemplateResponse =
        activityConfigurationService.createTemplate(request)

    /** 更新指定的活动模板及其组件编排。 */
    @PutMapping("/templates/{templateId}")
    fun updateTemplate(
        @PathVariable templateId: Long,
        @RequestBody request: CreateTemplateRequest
    ): ActivityTemplateResponse = activityConfigurationService.updateTemplate(templateId, request)

    /** 删除未被活动使用的活动模板。 */
    @DeleteMapping("/templates/{templateId}")
    fun deleteTemplate(@PathVariable templateId: Long): ResponseEntity<Void> {
        activityConfigurationService.deleteTemplate(templateId)
        return ResponseEntity.noContent().build()
    }

    /** 根据模板返回可直接渲染的活动动态表单字段。 */
    @GetMapping("/templates/{templateId}/form")
    fun templateForm(@PathVariable templateId: Long): ActivityFormResponse =
        activityConfigurationService.getTemplateForm(templateId)

    /** 返回全部已保存活动。 */
    @GetMapping("/activities")
    fun activities(): List<ActivityResponse> = activityConfigurationService.listActivities()

    /** 依据指定模板创建活动并保存动态表单配置。 */
    @PostMapping("/activities")
    fun createActivity(@RequestBody request: CreateActivityRequest): ActivityResponse =
        activityConfigurationService.createActivity(request)

    /** 复制指定活动，并生成一份草稿、下线状态的活动副本。 */
    @PostMapping("/activities/{activityId}/copy")
    fun copyActivity(@PathVariable activityId: Long): ActivityResponse =
        activityConfigurationService.copyActivity(activityId)

    /** 更新指定活动的基础信息和动态表单配置。 */
    @PutMapping("/activities/{activityId}")
    fun updateActivity(
        @PathVariable activityId: Long,
        @RequestBody request: CreateActivityRequest
    ): ActivityResponse = activityConfigurationService.updateActivity(activityId, request)

    /** 删除指定活动及其保存的动态表单配置。 */
    @DeleteMapping("/activities/{activityId}")
    fun deleteActivity(@PathVariable activityId: Long): ResponseEntity<Void> {
        activityConfigurationService.deleteActivity(activityId)
        return ResponseEntity.noContent().build()
    }

    /** 仅更新指定活动的上下线状态，不改写活动表单配置。 */
    @PatchMapping("/activities/{activityId}/online-status")
    fun updateActivityOnlineStatus(
        @PathVariable activityId: Long,
        @RequestBody request: UpdateActivityOnlineStatusRequest
    ): ActivityResponse = activityConfigurationService.updateActivityOnlineStatus(activityId, request.onlineStatus)

    /** 仅更新指定活动的调试模式、用户白名单和强制指定时间。 */
    @PatchMapping("/activities/{activityId}/debug")
    fun updateActivityDebugConfiguration(
        @PathVariable activityId: Long,
        @RequestBody request: UpdateActivityDebugConfigurationRequest
    ): ActivityResponse = activityConfigurationService.updateActivityDebugConfiguration(activityId, request)

    /** 将可预期的配置校验失败返回为页面可识别的 400 响应。 */
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to (exception.message ?: "活动配置不合法")))
}
