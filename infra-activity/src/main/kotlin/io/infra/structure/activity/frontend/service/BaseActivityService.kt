package io.infra.structure.activity.frontend.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.infra.structure.activity.admin.dto.FrontendActivityResponse
import io.infra.structure.activity.admin.service.ActivityConfigurationService
import io.infra.structure.activity.frontend.dto.BaseActivityDto
import io.infra.structure.activity.frontend.type.ActivityType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

/**
 * 面向前端构建活动的泛型服务基类。
 *
 * 活动类型负责声明表单 JSON 对应的数据类，基类统一解析表单 JSON；子类只负责组装自身 DTO。模板编码、
 * 活动状态、有效期和调试白名单校验均由后台活动服务统一完成。
 */
abstract class BaseActivityService<DTO : BaseActivityDto<*>> {

    /** 活动配置服务，由 Spring 在父类中注入。 */
    @Autowired
    private lateinit var activityConfigurationService: ActivityConfigurationService

    /** JSON 序列化工具，由 Spring 在父类中注入。 */
    @Autowired
    private lateinit var objectMapper: ObjectMapper

    /** 当前前端活动实现对应的类型。 */
    protected abstract val activityType: ActivityType

    /** 将活动配置转换为当前活动类型的前端 DTO。 */
    protected abstract fun toDto(activity: FrontendActivityResponse): DTO

    /** 构建指定活动类型的前端数据。 */
    @Transactional(readOnly = true)
    open fun build(activityId: Long, userId: Long): DTO {
        val activity = activityConfigurationService.getActivityForFrontend(activityId, activityType.templateCode, userId)
        return toDto(activity)
    }

    /** 将活动表中的表单 JSON 解析为当前活动类型绑定的具体数据类型。 */
    @Suppress("UNCHECKED_CAST")
    protected fun <FORM_DATA : Any> parseFormData(formDataJson: String): FORM_DATA = try {
        objectMapper.readValue(formDataJson, activityType.formDataClass) as FORM_DATA
    } catch (exception: Exception) {
        throw IllegalArgumentException("活动表单数据格式不符合${activityType.templateCode}活动类型", exception)
    }
}
