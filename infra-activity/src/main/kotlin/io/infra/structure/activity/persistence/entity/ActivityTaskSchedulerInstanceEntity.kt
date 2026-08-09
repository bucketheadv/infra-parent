package io.infra.structure.activity.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.Table

/** 活动任务调度应用实例心跳表映射。 */
@Table("activity_task_scheduler_instance")
open class ActivityTaskSchedulerInstanceEntity(
    /** 调度应用实例的唯一标识。 */
    @Id
    open var instanceId: String = "",
    /** 调度节点的 IP 地址。 */
    open var nodeIp: String = "",
    /** 最近一次心跳时间戳，单位为毫秒。 */
    open var lastHeartbeatTime: Long = 0,
    /** 创建时间戳，单位为毫秒。 */
    open var createTime: Long = 0,
    /** 更新时间戳，单位为毫秒。 */
    open var updateTime: Long = 0
)
