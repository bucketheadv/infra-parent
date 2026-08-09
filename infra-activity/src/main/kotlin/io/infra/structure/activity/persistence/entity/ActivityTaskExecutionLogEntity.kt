package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Column
import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

/** 活动任务每次执行的幂等与审计记录表映射。 */
@Table("activity_task_execution_log")
data class ActivityTaskExecutionLogEntity(
    /** 执行记录主键。 */
    @Id(keyType = KeyType.Auto)
    var id: Long? = null,
    /** 所属活动任务实例主键。 */
    @Column("activity_task_id")
    var activityTaskId: Long = 0,
    /** 全局唯一幂等执行键。 */
    @Column("execution_key")
    var executionKey: String = "",
    /** 触发来源。 */
    @Column("trigger_source")
    var triggerSource: String = "SCHEDULED",
    /** 本次计划触发时间戳，单位为毫秒。 */
    @Column("trigger_time")
    var triggerTime: Long = 0,
    /** 当前执行状态。 */
    var status: String = "PENDING",
    /** 当前为第几次尝试。 */
    @Column("attempt_no")
    var attemptNo: Int = 1,
    /** 手动触发说明或执行上下文 JSON。 */
    @Column("request_json")
    var requestJson: String = "{}",
    /** 执行结果 JSON。 */
    @Column("result_json")
    var resultJson: String? = null,
    /** 失败原因。 */
    @Column("error_message")
    var errorMessage: String? = null,
    /** 开始时间戳，单位为毫秒。 */
    @Column("start_time")
    var startTime: Long? = null,
    /** 结束时间戳，单位为毫秒。 */
    @Column("end_time")
    var endTime: Long? = null,
    /** 创建时间戳，单位为毫秒。 */
    @Column("create_time")
    var createTime: Long? = null,
    /** 更新时间戳，单位为毫秒。 */
    @Column("update_time")
    var updateTime: Long? = null
)
