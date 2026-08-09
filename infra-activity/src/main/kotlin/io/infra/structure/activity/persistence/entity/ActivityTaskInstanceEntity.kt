package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动上线后生成的实际可调度任务表映射。 */
@Table("activity_task_instance")
open class ActivityTaskInstanceEntity(
    /** 任务实例主键。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 所属活动主键。 */
    open var activityId: Long = 0,
    /** 来源活动模板任务关联主键。 */
    open var activityTemplateTaskId: Long = 0,
    /** 任务模板主键。 */
    open var taskTemplateId: Long = 0,
    /** 活动内唯一任务编码。 */
    open var code: String = "",
    /** 任务展示名称。 */
    open var name: String = "",
    /** 后端任务处理器类型。 */
    open var handlerType: String = "",
    /** 触发方式。 */
    open var triggerType: String = "MANUAL",
    /** 触发配置快照 JSON。 */
    open var triggerConfigJson: String = "{}",
    /** 合并后的任务参数快照 JSON。 */
    open var parametersJson: String = "{}",
    /** 最大重试次数快照。 */
    open var maxRetryCount: Int = 3,
    /** 重试间隔快照，单位为毫秒。 */
    open var retryIntervalMillis: Long = 60_000,
    /** 下一次触发时间戳，单位为毫秒。 */
    open var nextTriggerTime: Long? = null,
    /** 当前调度状态。 */
    open var status: String = "PENDING",
    /** 当前租约所属实例标识。 */
    open var leaseOwner: String? = null,
    /** 当前租约失效时间戳，单位为毫秒。 */
    open var leaseExpireTime: Long? = null,
    /** 已重试次数。 */
    open var retryCount: Int = 0,
    /** 最近一次实际触发时间戳，单位为毫秒。 */
    open var lastTriggerTime: Long? = null,
    /** 创建时间戳，单位为毫秒。 */
    open var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    open var updateTime: Long? = null
)
