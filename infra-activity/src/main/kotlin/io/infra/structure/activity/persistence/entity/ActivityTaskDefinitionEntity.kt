package io.infra.structure.activity.persistence.entity

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
    var handlerType: String = "",
    /** 任务用途说明。 */
    var description: String? = null,
    /** 默认执行参数 JSON。 */
    var defaultParametersJson: String = "{}",
    /** 最大重试次数。 */
    var maxRetryCount: Int = 3,
    /** 重试间隔，单位为毫秒。 */
    var retryIntervalMillis: Long = 60_000,
    /** 是否允许继续关联。 */
    var enabled: Boolean = true,
    /** 创建时间戳，单位为毫秒。 */
    var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    var updateTime: Long? = null
)
