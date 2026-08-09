package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
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
    @Column("activity_template_id")
    var activityTemplateId: Long = 0,
    /** 被关联任务模板主键。 */
    @Column("task_template_id")
    var taskTemplateId: Long = 0,
    /** 当前模板内唯一任务编码。 */
    var code: String = "",
    /** 当前模板中的任务展示名称。 */
    var name: String = "",
    /** 任务处理器类型快照。 */
    @Column("handler_type")
    var handlerType: String = "",
    /** 触发方式。 */
    @Column("trigger_type")
    var triggerType: String = "MANUAL",
    /** 触发配置 JSON。 */
    @Column("trigger_config_json")
    var triggerConfigJson: String = "{}",
    /** 活动专属参数覆盖 JSON。 */
    @Column("parameter_overrides_json")
    var parameterOverridesJson: String = "{}",
    /** 是否启用该任务绑定。 */
    var enabled: Boolean = true,
    /** 执行排序。 */
    @Column("sort_no")
    var sortNo: Int = 0,
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
