package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 可复用活动任务模板的表映射。 */
@Table("activity_task_definition")
data class ActivityTaskDefinitionEntity(
    /** 任务模板主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 任务模板唯一编码。 */
    var code: String = "",
    /** 任务模板展示名称。 */
    var name: String = "",
    /** 后端任务处理器类型。 */
    @Column("handler_type")
    var handlerType: String = "",
    /** 任务用途说明。 */
    var description: String? = null,
    /** 默认执行参数 JSON。 */
    @Column("default_parameters_json")
    var defaultParametersJson: String = "{}",
    /** 最大重试次数。 */
    @Column("max_retry_count")
    var maxRetryCount: Int = 3,
    /** 重试间隔，单位为毫秒。 */
    @Column("retry_interval_millis")
    var retryIntervalMillis: Long = 60_000,
    /** 是否允许继续关联。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
