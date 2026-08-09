package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动任务每次执行的幂等与审计记录表映射。 */
@Table("activity_task_execution_log")
open class ActivityTaskExecutionLogEntity(
    /** 执行记录主键。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 所属活动任务实例主键。 */
    open var activityTaskId: Long = 0,
    /** 全局唯一幂等执行键。 */
    open var executionKey: String = "",
    /** 触发来源。 */
    open var triggerSource: String = "SCHEDULED",
    /** 本次计划触发时间戳，单位为毫秒。 */
    open var triggerTime: Long = 0,
    /** 当前执行状态。 */
    open var status: String = "PENDING",
    /** 当前为第几次尝试。 */
    open var attemptNo: Int = 1,
    /** 手动触发说明或执行上下文 JSON。 */
    open var requestJson: String = "{}",
    /** 执行结果 JSON。 */
    open var resultJson: String? = null,
    /** 失败原因。 */
    open var errorMessage: String? = null,
    /** 开始时间戳，单位为毫秒。 */
    open var startTime: Long? = null,
    /** 结束时间戳，单位为毫秒。 */
    open var endTime: Long? = null,
    /** 创建时间戳，单位为毫秒。 */
    open var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    open var updateTime: Long? = null
)
