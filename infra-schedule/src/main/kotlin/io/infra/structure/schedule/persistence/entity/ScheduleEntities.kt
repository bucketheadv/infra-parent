package io.infra.structure.schedule.persistence.entity

import com.mybatisflex.annotation.Id
import com.mybatisflex.annotation.KeyType
import com.mybatisflex.annotation.Table

@Table("infra_schedule_job")
open class ScheduleJobEntity(
    /** 数据库自增任务 ID。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 管理端展示名称。 */
    open var name: String = "",
    /** 指定执行器数据库 ID；为空时按分组路由。 */
    open var executorId: Long? = null,
    /** 已注册任务处理器名称。 */
    open var handler: String = "",
    /** 原样传递给处理器的参数。 */
    open var parameters: String = "",
    /** 触发方式枚举名称。 */
    open var scheduleType: String = "CRON",
    /** Cron 表达式。 */
    open var cron: String? = null,
    /** 固定执行间隔（毫秒）。 */
    open var fixedRateMillis: Long? = null,
    /** 任务启停状态枚举名称。 */
    open var status: String = "ENABLED",
    /** 节点路由策略枚举名称。 */
    open var routeStrategy: String = "FAILOVER",
    /** 重叠触发阻塞策略枚举名称。 */
    open var blockStrategy: String = "SERIAL",
    /** 最大额外重试次数。 */
    open var maxRetryCount: Int = 0,
    /** 两次重试之间的等待时间（毫秒）。 */
    open var retryIntervalMillis: Long = 1_000,
    /** 单次执行最长允许秒数，0 表示无限制。 */
    open var timeoutSeconds: Long = 0,
    /** 下一次应触发时间。 */
    open var nextTriggerAt: Long? = null,
    /** 最近一次定时触发时间。 */
    open var lastTriggerAt: Long? = null,
    /** 持有当前调度租约的节点 ID。 */
    open var claimOwner: String? = null,
    /** 租约失效时间。 */
    open var claimUntil: Long? = null,
    /** 创建时间。 */
    open var createTime: Long = 0,
    /** 最近一次定义或调度状态更新时间。 */
    open var updateTime: Long = 0
)

/** 任务执行审计表映射；开始为运行中，结束后回写终态。 */
@Table("infra_schedule_execution_log")
open class ScheduleExecutionLogEntity(
    /** 数据库自增日志 ID。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 所属任务数据库 ID。 */
    open var jobId: Long = 0,
    /** 实际处理本次任务的执行器数据库 ID。 */
    open var executorId: Long? = null,
    /** 调度器发起本次任务的时间。 */
    open var triggerTime: Long = 0,
    /** 处理结束时间。 */
    open var finishTime: Long? = null,
    /** 执行状态枚举名称。 */
    open var status: String = "RUNNING",
    /** 成功或失败前已执行的重试次数。 */
    open var retryCount: Int = 0,
    /** 结果、错误或跳过原因。 */
    open var message: String? = null,
    /** 本次调用的目标地址。 */
    open var targetAddress: String? = null,
    /** 本次实际执行耗时（毫秒）。 */
    open var durationMillis: Long? = null
)

/** 执行器分组表映射，语义对齐 xxl-job 的 JobGroup（appname + title）。 */
@Table("infra_schedule_executor")
open class ScheduleExecutorEntity(
    /** 数据库自增执行器 ID，供管理后台与任务定义关联。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 执行器分组标识，全局唯一，对应 xxl-job appname。 */
    open var executorGroup: String = "",
    /** 管理端展示名称，不要求唯一，对应 xxl-job title。 */
    open var executorName: String = "",
    /** 执行器服务地址列表（逗号分隔）；自动注册时由注册表同步。 */
    open var address: String? = null,
    /** 访问地址为人工维护还是由心跳自动回填。 */
    open var addressMode: String = "AUTO_REGISTER",
    /** 是否允许该执行器继续接收调度请求。 */
    open var status: String = "ENABLED",
    /** 最近一次收到心跳的时间。 */
    open var lastHeartbeatTime: Long = 0,
    /** 首次注册时间。 */
    open var createTime: Long = 0,
    /** 最近一次心跳更新时间。 */
    open var updateTime: Long = 0
)

/** 自动注册模式下的执行器实例地址登记。 */
@Table("infra_schedule_executor_registry")
open class ScheduleExecutorRegistryEntity(
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 所属执行器分组 ID。 */
    open var executorId: Long = 0,
    /** 实例访问地址。 */
    open var address: String = "",
    /** 该地址最近心跳时间。 */
    open var lastHeartbeatTime: Long = 0,
    open var createTime: Long = 0,
    open var updateTime: Long = 0
)
