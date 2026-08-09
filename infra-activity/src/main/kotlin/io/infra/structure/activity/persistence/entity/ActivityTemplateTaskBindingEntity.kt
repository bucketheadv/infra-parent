package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动模板关联任务模板的表映射。 */
@Table("activity_template_task_binding")
data class ActivityTemplateTaskBindingEntity(
    /** 关联主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属活动模板主键。 */
    var activityTemplateId: Long = 0,
    /** 被关联任务模板主键。 */
    var taskTemplateId: Long = 0,
    /** 当前模板内唯一任务编码。 */
    var code: String = "",
    /** 当前模板中的任务展示名称。 */
    var name: String = "",
    /** 任务处理器类型快照。 */
    var handlerType: String = "",
    /** 触发方式。 */
    var triggerType: String = "MANUAL",
    /** 触发配置 JSON。 */
    var triggerConfigJson: String = "{}",
    /** 活动专属参数覆盖 JSON。 */
    var parameterOverridesJson: String = "{}",
    /** 是否启用该任务绑定。 */
    var enabled: Boolean = true,
    /** 执行排序。 */
    var sortNo: Int = 0,
    /** 创建时间戳，单位为毫秒。 */
    var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    var updateTime: Long? = null
)
