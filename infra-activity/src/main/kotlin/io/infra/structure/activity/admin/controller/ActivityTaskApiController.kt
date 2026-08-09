package io.infra.structure.activity.admin.controller

import io.infra.structure.activity.admin.dto.ActivityTaskExecutionResponse
import io.infra.structure.activity.admin.dto.ActivityTaskCronPreviewRequest
import io.infra.structure.activity.admin.dto.ActivityTaskCronPreviewResponse
import io.infra.structure.activity.admin.dto.ActivityTaskResponse
import io.infra.structure.activity.admin.dto.ActivityTaskTemplateResponse
import io.infra.structure.activity.admin.dto.ActivityTemplateTaskBindingResponse
import io.infra.structure.activity.admin.dto.CreateActivityTaskTemplateRequest
import io.infra.structure.activity.admin.dto.ManualTriggerActivityTaskRequest
import io.infra.structure.activity.admin.dto.ReplaceActivityTemplateTaskBindingsRequest
import io.infra.structure.activity.admin.service.ActivityTaskService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** 活动任务模板、绑定关系、实例任务和执行记录的管理接口。 */
@RestController
@RequestMapping(
    value = ["/api/activity/tasks"],
    produces = [MediaType.APPLICATION_JSON_VALUE]
)
class ActivityTaskApiController(
    private val activityTaskService: ActivityTaskService
) {

    /** 返回全部可复用任务模板。 */
    @GetMapping("/templates")
    fun taskTemplates(): List<ActivityTaskTemplateResponse> = activityTaskService.listTaskTemplates()

    /** 新建一个可复用任务模板。 */
    @PostMapping("/templates")
    fun createTaskTemplate(@RequestBody request: CreateActivityTaskTemplateRequest): ActivityTaskTemplateResponse =
        activityTaskService.createTaskTemplate(request)

    /** 更新一个任务模板，任务编码创建后保持稳定。 */
    @PutMapping("/templates/{templateId}")
    fun updateTaskTemplate(
        @PathVariable templateId: Long,
        @RequestBody request: CreateActivityTaskTemplateRequest
    ): ActivityTaskTemplateResponse = activityTaskService.updateTaskTemplate(templateId, request)

    /** 删除没有被活动模板使用的任务模板。 */
    @DeleteMapping("/templates/{templateId}")
    fun deleteTaskTemplate(@PathVariable templateId: Long): ResponseEntity<Void> {
        activityTaskService.deleteTaskTemplate(templateId)
        return ResponseEntity.noContent().build()
    }

    /** 返回某个活动模板关联的全部任务。 */
    @GetMapping("/bindings/{activityTemplateId}")
    fun templateTasks(@PathVariable activityTemplateId: Long): List<ActivityTemplateTaskBindingResponse> =
        activityTaskService.listTemplateTasks(activityTemplateId)

    /** 以提交列表整体替换一个活动模板的任务关联。 */
    @PutMapping("/bindings/{activityTemplateId}")
    fun replaceTemplateTasks(
        @PathVariable activityTemplateId: Long,
        @RequestBody request: ReplaceActivityTemplateTaskBindingsRequest
    ): List<ActivityTemplateTaskBindingResponse> = activityTaskService.replaceTemplateTasks(activityTemplateId, request.tasks)

    /** 返回活动上线后生成的实际任务实例。 */
    @GetMapping("/activities/{activityId}")
    fun activityTasks(@PathVariable activityId: Long): List<ActivityTaskResponse> =
        activityTaskService.listActivityTasks(activityId)

    /** 返回任务实例的执行审计记录。 */
    @GetMapping("/{taskId}/executions")
    fun taskExecutions(@PathVariable taskId: Long): List<ActivityTaskExecutionResponse> =
        activityTaskService.listTaskExecutions(taskId)

    /** 手动立即触发一个活动任务，并保留原有自动调度时间。 */
    @PostMapping("/{taskId}/trigger")
    fun triggerTask(
        @PathVariable taskId: Long,
        @RequestBody request: ManualTriggerActivityTaskRequest
    ): ActivityTaskExecutionResponse = activityTaskService.triggerManually(taskId, request.reason)

    /** 按当前时区预览 Cron 表达式未来五次的触发时间。 */
    @PostMapping("/cron/preview")
    fun previewCron(@RequestBody request: ActivityTaskCronPreviewRequest): ActivityTaskCronPreviewResponse =
        activityTaskService.previewCronNextTimes(request.cron, request.timezone)

    /** 将配置校验错误统一返回为 JSON，便于管理页面直接展示。 */
    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("message" to (exception.message ?: "任务配置不合法")))
}
