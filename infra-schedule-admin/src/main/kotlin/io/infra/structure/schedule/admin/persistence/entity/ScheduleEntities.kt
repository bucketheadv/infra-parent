package io.infra.structure.schedule.admin.persistence.entity

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
    /** 是否常驻任务。 */
    open var resident: Boolean = false,
    /** 最大额外重试次数。 */
    open var maxRetryCount: Int = 0,
    /** 两次重试之间的等待时间（毫秒）。 */
    open var retryIntervalMillis: Long = 1_000,
    /** 单次执行最长允许秒数，0 表示无限制。 */
    open var timeoutSeconds: Long = 0,
    /** 下一次定时计划应触发的 Unix 毫秒时间戳；停用任务时为空。 */
    open var nextTriggerAt: Long? = null,
    /** 最近一次已推进到 Outbox 的定时计划触发时间（Unix 毫秒）。 */
    open var lastTriggerAt: Long? = null,
    /** 持有当前调度租约的节点 ID。 */
    open var claimOwner: String? = null,
    /** 当前任务领取租约的 Unix 毫秒失效时间；到期后其他 Admin 可重新领取。 */
    open var claimUntil: Long? = null,
    /** 任务定义创建时的 Unix 毫秒时间戳。 */
    open var createTime: Long = 0,
    /** 最近一次任务定义、状态或租约变更的 Unix 毫秒时间戳。 */
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
    /** 本次远程投递尝试创建日志的 Unix 毫秒时间戳。 */
    open var triggerTime: Long = 0,
    /** 执行器确认结束、取消或回收为 LOST 的 Unix 毫秒时间戳；活跃状态时为空。 */
    open var finishTime: Long? = null,
    /** 执行状态枚举名称。 */
    open var status: String = "RUNNING",
    /** 成功或失败前已执行的重试次数。 */
    open var retryCount: Int = 0,
    /** 结果、错误或跳过原因。 */
    open var message: String? = null,
    /** 业务执行过程日志。 */
    open var handleLog: String? = null,
    /** 本次调用的目标地址。 */
    open var targetAddress: String? = null,
    /** 本次实际执行耗时（毫秒）。 */
    open var durationMillis: Long? = null
)

/** 可靠触发 Outbox：任务推进计划后，由独立投递循环可靠发送给执行器。 */
@Table("infra_schedule_trigger_outbox")
open class ScheduleTriggerOutboxEntity(
    /** 数据库自增主键。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 对应任务主键。 */
    open var jobId: Long = 0,
    /** 本次计划触发时间。 */
    open var triggerTime: Long = 0,
    /** 是否由管理员立即执行创建；暂停任务可继续投递此类记录。 */
    open var manualTrigger: Boolean = false,
    /** 投递状态：PENDING / PROCESSING / DISPATCHED / CANCELLED。 */
    open var status: String = "PENDING",
    /** 当前投递租约持有节点。 */
    open var claimOwner: String? = null,
    /** 本次领取的唯一租约令牌。 */
    open var claimToken: String? = null,
    /** 当前投递租约失效时间。 */
    open var claimUntil: Long? = null,
    /** 已尝试投递次数。 */
    open var attemptCount: Int = 0,
    /** 最近一次投递错误。 */
    open var lastError: String? = null,
    /** Outbox 创建时的 Unix 毫秒时间戳。 */
    open var createTime: Long = 0,
    /** Outbox 状态、租约或退避时间最近一次变更的 Unix 毫秒时间戳。 */
    open var updateTime: Long = 0
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
    /** 最近一次收到该分组任一实例心跳的 Unix 毫秒时间戳。 */
    open var lastHeartbeatTime: Long = 0,
    /** 执行器分组首次创建的 Unix 毫秒时间戳。 */
    open var createTime: Long = 0,
    /** 配置或心跳最近一次更新的 Unix 毫秒时间戳。 */
    open var updateTime: Long = 0
)

/** 自动注册模式下的执行器实例地址登记。 */
@Table("infra_schedule_executor_registry")
open class ScheduleExecutorRegistryEntity(
    /** 自动注册实例记录的数据库自增主键。 */
    @Id(keyType = KeyType.Auto)
    open var id: Long? = null,
    /** 所属执行器分组 ID。 */
    open var executorId: Long = 0,
    /** 实例访问地址。 */
    open var address: String = "",
    /** 该地址最近心跳时间。 */
    open var lastHeartbeatTime: Long = 0,
    /** 该实例地址首次登记的 Unix 毫秒时间戳。 */
    open var createTime: Long = 0,
    /** 地址记录最近一次心跳或状态变更的 Unix 毫秒时间戳。 */
    open var updateTime: Long = 0
)

/** 路由 LFU/LRU 统计（多调度节点共享）。 */
@Table("infra_schedule_route_stat")
open class ScheduleRouteStatEntity(
    /** 节点键：`address` 或 `local:{executorId}`。 */
    @Id
    open var nodeKey: String = "",
    /** 累计被路由选中次数。 */
    open var useCount: Int = 0,
    /** 最近一次被路由选中时间（毫秒）。 */
    open var lastRouteTime: Long = 0,
    /** LFU/LRU 统计最近一次更新的 Unix 毫秒时间戳。 */
    open var updateTime: Long = 0
)

/** 路由 ROUND 轮询游标（多调度节点共享）。 */
@Table("infra_schedule_route_cursor")
open class ScheduleRouteCursorEntity(
    /** 轮询键：`executor:{id}` 或执行器分组名。 */
    @Id
    open var cursorKey: String = "",
    /** 累计轮询次数。 */
    open var cursorValue: Long = 0,
    /** 轮询游标最近一次递增的 Unix 毫秒时间戳。 */
    open var updateTime: Long = 0
)
