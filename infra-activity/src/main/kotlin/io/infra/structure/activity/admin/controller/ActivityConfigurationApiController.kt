package io.infra.structure.activity.admin.controller

import io.infra.structure.activity.admin.dto.ActivityComponentResponse
import io.infra.structure.activity.admin.dto.ActivityFormResponse
import io.infra.structure.activity.admin.dto.ActivityResponse
import io.infra.structure.activity.admin.dto.ActivityTemplateResponse
import io.infra.structure.activity.admin.dto.CreateActivityRequest
import io.infra.structure.activity.admin.dto.CreateComponentRequest
import io.infra.structure.activity.admin.dto.CreateRewardComponentRequest
import io.infra.structure.activity.admin.dto.CreateRewardTemplateRequest
import io.infra.structure.activity.admin.dto.CreateTemplateRequest
import io.infra.structure.activity.admin.dto.CreatePrizeComponentRequest
import io.infra.structure.activity.admin.dto.PrizeLookupResponse
import io.infra.structure.activity.admin.dto.PrizeComponentResponse
import io.infra.structure.activity.admin.dto.RewardComponentResponse
import io.infra.structure.activity.admin.dto.RewardTemplateResponse
import io.infra.structure.activity.admin.dto.UpdateActivityDebugConfigurationRequest
import io.infra.structure.activity.admin.dto.UpdateActivityOnlineStatusRequest
import io.infra.structure.activity.admin.service.ActivityConfigurationService
import io.infra.structure.activity.admin.service.PrizeCatalogGateway
import io.infra.structure.activity.admin.service.PrizeComponentConfigurationService
import io.infra.structure.activity.admin.service.RewardConfigurationService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 活动配置管理接口。
 *
 * 页面通过此接口维护组件、模板和活动，并在创建活动前读取模板生成的动态字段。
 */
@RestController
@RequestMapping("/api/activity")
class ActivityConfigurationApiController(
    private val activityConfigurationService: ActivityConfigurationService,
    private val rewardConfigurationService: RewardConfigurationService,
    private val prizeComponentConfigurationService: PrizeComponentConfigurationService,
    private val prizeCatalogGateway: PrizeCatalogGateway
) {

    /** 返回固定奖品组件和全部扩展奖品组件。 */
    @GetMapping("/reward/prizes")
    fun prizeComponents(): List<PrizeComponentResponse> = prizeComponentConfigurationService.listComponents()

    /** 新建扩展奖品组件。 */
    @PostMapping("/reward/prizes")
    fun createPrizeComponent(@RequestBody request: CreatePrizeComponentRequest): PrizeComponentResponse =
        prizeComponentConfigurationService.createComponent(request)

    /** 更新指定扩展奖品组件；固定奖品组件 ID 1 不允许修改。 */
    @PutMapping("/reward/prizes/{componentId}")
    fun updatePrizeComponent(
        @PathVariable componentId: Long,
        @RequestBody request: CreatePrizeComponentRequest
    ): PrizeComponentResponse = prizeComponentConfigurationService.updateComponent(componentId, request)

    /** 删除未被奖励组件挂载的扩展奖品组件；固定奖品组件 ID 1 不允许删除。 */
    @DeleteMapping("/reward/prizes/{componentId}")
    fun deletePrizeComponent(@PathVariable componentId: Long): ResponseEntity<Void> {
        prizeComponentConfigurationService.deleteComponent(componentId)
        return ResponseEntity.noContent().build()
    }

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

    /** 返回全部可复用奖励组件。 */
    @GetMapping("/reward/components")
    fun rewardComponents(): List<RewardComponentResponse> = rewardConfigurationService.listComponents()

    /** 新建奖励组件。 */
    @PostMapping("/reward/components")
    fun createRewardComponent(@RequestBody request: CreateRewardComponentRequest): RewardComponentResponse =
        rewardConfigurationService.createComponent(request)

    /** 更新指定奖励组件。 */
    @PutMapping("/reward/components/{componentId}")
    fun updateRewardComponent(
        @PathVariable componentId: Long,
        @RequestBody request: CreateRewardComponentRequest
    ): RewardComponentResponse = rewardConfigurationService.updateComponent(componentId, request)

    /** 删除未被奖励模板引用的奖励组件。 */
    @DeleteMapping("/reward/components/{componentId}")
    fun deleteRewardComponent(@PathVariable componentId: Long): ResponseEntity<Void> {
        rewardConfigurationService.deleteComponent(componentId)
        return ResponseEntity.noContent().build()
    }

    /** 返回全部奖励模板及其奖励组件、固定奖品组件编排。 */
    @GetMapping("/reward/templates")
    fun rewardTemplates(): List<RewardTemplateResponse> = rewardConfigurationService.listTemplates()

    /** 新建奖励模板。 */
    @PostMapping("/reward/templates")
    fun createRewardTemplate(@RequestBody request: CreateRewardTemplateRequest): RewardTemplateResponse =
        rewardConfigurationService.createTemplate(request)

    /** 更新指定奖励模板。 */
    @PutMapping("/reward/templates/{templateId}")
    fun updateRewardTemplate(
        @PathVariable templateId: Long,
        @RequestBody request: CreateRewardTemplateRequest
    ): RewardTemplateResponse = rewardConfigurationService.updateTemplate(templateId, request)

    /** 删除未被活动模板引用的奖励模板。 */
    @DeleteMapping("/reward/templates/{templateId}")
    fun deleteRewardTemplate(@PathVariable templateId: Long): ResponseEntity<Void> {
        rewardConfigurationService.deleteTemplate(templateId)
        return ResponseEntity.noContent().build()
    }

    /** 查询装扮或礼物奖品的推荐属性；返回值可被运营人员继续修改。 */
    @GetMapping("/prizes/lookup")
    fun lookupPrize(
        @RequestParam prizeType: String,
        @RequestParam prizeId: String
    ): PrizeLookupResponse = prizeCatalogGateway.query(prizeType, prizeId)
        ?: throw IllegalArgumentException("未找到对应的奖品信息")

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
    @PatchMapping("/activities/{activityId}/online/status")
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
