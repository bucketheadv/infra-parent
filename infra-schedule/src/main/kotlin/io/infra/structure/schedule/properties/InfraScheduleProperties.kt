package io.infra.structure.schedule.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.util.UUID

@ConfigurationProperties("infra.schedule")
class InfraScheduleProperties {
    /** 调度中心开关，默认关闭，避免引入 starter 后产生非预期任务。 */
    var enabled: Boolean = false
    /** 两次数据库扫描之间的等待时间（毫秒）。 */
    var scanIntervalMillis: Long = 1_000
    /** 节点领取任务后持有租约的时长（毫秒）。 */
    var claimLeaseMillis: Long = 60_000
    /** 单次扫描最多领取的到期任务数量。 */
    var dispatchBatchSize: Int = 100
    /** 单轮扫描最多领取的页数，避免到期任务过多时长期占用调度线程。 */
    var dispatchMaxPages: Int = 10
    /** 是否启用当前应用的调度扫描；纯执行器实例应关闭此开关。 */
    var dispatcherEnabled: Boolean = true
    /** 用于编排任务执行的工作线程数量。 */
    var workerThreads: Int = 8
    /** 当前调度节点唯一 ID；集群部署时应配置为稳定且互不重复的值。 */
    var schedulerId: String = "schedule-${UUID.randomUUID()}"
    /** 本地执行器注册和健康检查配置。 */
    var executor: ExecutorProperties = ExecutorProperties()
    /** 管理 REST 接口暴露配置。 */
    var management: ManagementProperties = ManagementProperties()

    /** 本地执行器配置。 */
    class ExecutorProperties {
        /** 是否创建并注册当前应用内执行器。 */
        var enabled: Boolean = true
        /** 执行器展示名称；未配置时回退为 [group]。 */
        var name: String? = null
        /** 执行器分组标识，全局唯一，对应 xxl-job appname。 */
        var group: String = "default"
        /** 可选的执行器对外地址。 */
        var address: String? = null
        /** 调度中心管理地址；配置后本地执行器会定时向该地址上报心跳。 */
        var adminAddress: String? = null
        /** 调度中心与执行器通信使用的共享令牌，不可在配置文件中写入明文。 */
        var accessToken: String? = null
        /** 是否校验调度中心到执行器的共享令牌；本地页面联调可暂时关闭。 */
        var authEnabled: Boolean = false
        /** 调度中心调用执行器时的连接超时（毫秒）。 */
        var connectTimeoutMillis: Long = 3_000
        /** 调度中心调用执行器时的响应超时（毫秒）。 */
        var readTimeoutMillis: Long = 30_000
        /** 向调度中心上报心跳的时间间隔（毫秒）。 */
        var heartbeatIntervalMillis: Long = 10_000
        /** 超过该时间未上报的执行器会被视为离线。 */
        var heartbeatTimeoutMillis: Long = 30_000
    }

    /** 管理端接口安全开关。 */
    class ManagementProperties {
        /** 管理端点默认不暴露，接入方应在其安全网关或 Spring Security 后显式开启。 */
        var enabled: Boolean = false
        /** 管理任务配置接口的共享令牌，不可在配置文件中写入明文。 */
        var accessToken: String? = null
        /** 是否校验管理端访问令牌；接入 SSO 或网关前可暂时关闭。 */
        var authEnabled: Boolean = false
    }
}
