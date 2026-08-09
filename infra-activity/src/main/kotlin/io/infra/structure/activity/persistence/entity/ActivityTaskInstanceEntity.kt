package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动上线后生成的实际可调度任务表映射。 */
@Table("activity_task_instance")
data class ActivityTaskInstanceEntity(
    /** 任务实例主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属活动主键。 */
    @Column("activity_id")
    var activityId: Long = 0,
    /** 来源活动模板任务关联主键。 */
    @Column("activity_template_task_id")
    var activityTemplateTaskId: Long = 0,
    /** 任务模板主键。 */
    @Column("task_template_id")
    var taskTemplateId: Long = 0,
    /** 活动内唯一任务编码。 */
    var code: String = "",
    /** 任务展示名称。 */
    var name: String = "",
    /** 后端任务处理器类型。 */
    @Column("handler_type")
    var handlerType: String = "",
    /** 触发方式。 */
    @Column("trigger_type")
    var triggerType: String = "MANUAL",
    /** 触发配置快照 JSON。 */
    @Column("trigger_config_json")
    var triggerConfigJson: String = "{}",
    /** 合并后的任务参数快照 JSON。 */
    @Column("parameters_json")
    var parametersJson: String = "{}",
    /** 最大重试次数快照。 */
    @Column("max_retry_count")
    var maxRetryCount: Int = 3,
    /** 重试间隔快照，单位为毫秒。 */
    @Column("retry_interval_millis")
    var retryIntervalMillis: Long = 60_000,
    /** 下一次触发时间戳，单位为毫秒。 */
    @Column("next_trigger_time")
    var nextTriggerTime: Long? = null,
    /** 当前调度状态。 */
    var status: String = "PENDING",
    /** 当前租约所属实例标识。 */
    @Column("lease_owner")
    var leaseOwner: String? = null,
    /** 当前租约失效时间戳，单位为毫秒。 */
    @Column("lease_expire_time")
    var leaseExpireTime: Long? = null,
    /** 已重试次数。 */
    @Column("retry_count")
    var retryCount: Int = 0,
    /** 最近一次实际触发时间戳，单位为毫秒。 */
    @Column("last_trigger_time")
    var lastTriggerTime: Long? = null,
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
