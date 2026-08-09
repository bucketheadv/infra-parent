package io.infra.structure.activity.frontend.dto

import io.infra.structure.activity.frontend.type.ActivityType

/**
 * 面向前端构建活动时使用的泛型响应基类。
 *
 * [FORM_DATA] 由每种活动定义自身的表单数据类型，业务活动 DTO 只需要继承本类并声明具体类型。
 */
abstract class BaseActivityDto<out FORM_DATA : Any>(
    /** 活动主键。 */
    open val id: Long,
    /** 活动展示名称。 */
    open val name: String,
    /** 活动采用的模板主键。 */
    open val templateId: Long,
    /** 活动状态。 */
    open val status: String,
    /** 活动上下线状态。 */
    open val onlineStatus: String,
    /** 是否永久有效。 */
    open val validForever: Boolean,
    /** 非永久活动的开始时间戳，单位为毫秒。 */
    open val validStartTime: Long?,
    /** 非永久活动的结束时间戳，单位为毫秒。 */
    open val validEndTime: Long?,
    /** 是否启用仅面向白名单用户的调试模式。 */
    open val debugMode: Boolean,
    /** 调试模式白名单中的用户主键。 */
    open val debugUserIds: List<Long>,
    /** 调试模式强制使用的时间戳，单位为毫秒。 */
    open val debugForceTime: Long?,
    /** 活动创建时间戳，单位为毫秒。 */
    open val createTime: Long,
    /** 活动最后更新时间戳，单位为毫秒。 */
    open val updateTime: Long,
    /** 当前活动类型。 */
    open val activityType: ActivityType,
    /** 当前活动类型对应的结构化配置数据。 */
    open val data: FORM_DATA
)
