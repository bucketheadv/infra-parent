package io.infra.structure.schedule.core

import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.client.RestClient
import java.time.Duration

/**
 * 将当前执行器存活状态定时上报给调度中心，并在进程关闭时主动上报离线。
 * 配置了 [InfraScheduleProperties.Executor.adminAddress] 时走 HTTP；否则仅操作本地注册表。
 */
class ExecutorHeartbeatReporter(
    private val properties: InfraScheduleProperties,
    private val executorRegistry: ExecutorRegistry
) : DisposableBean {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val adminBaseUrl = properties.executor.adminAddress?.takeIf { it.isNotBlank() }?.removeSuffix("/")
    private val client = adminBaseUrl?.let { baseUrl ->
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(properties.executor.connectTimeoutMillis))
                setReadTimeout(Duration.ofMillis(properties.executor.readTimeoutMillis))
            })
            .build()
    }

    /** 发送当前执行器分组、展示名称和服务地址；网络失败仅影响本次心跳。 */
    @Scheduled(fixedDelayString = $$"${infra.schedule.executor.heartbeat-interval-millis:10000}")
    fun report() {
        val remote = client ?: return
        val token = properties.executor.accessToken?.takeIf { it.isNotBlank() }
        runCatching {
            val request = remote.post()
                .uri(ScheduleWebPaths.EXECUTOR_HEARTBEAT)
                .body(
                    ExecutorPresenceReport(
                        executorGroup = properties.executor.group,
                        executorName = properties.executor.name ?: properties.executor.group,
                        address = properties.executor.address
                    )
                )
            if (properties.executor.authEnabled) request.header(SCHEDULE_ACCESS_TOKEN_HEADER, token ?: "")
            request.retrieve().toBodilessEntity()
        }
    }

    /** 进程关闭时向调度中心上报离线。 */
    override fun destroy() {
        if (!properties.executor.enabled) return
        val group = properties.executor.group
        val address = properties.executor.address
        val remote = client
        if (remote == null) {
            runCatching { executorRegistry.markOffline(group, address) }
            return
        }
        val token = properties.executor.accessToken?.takeIf { it.isNotBlank() }
        runCatching {
            val request = remote.post()
                .uri(ScheduleWebPaths.EXECUTOR_OFFLINE)
                .body(ExecutorOfflineReport(executorGroup = group, address = address))
            if (properties.executor.authEnabled) request.header(SCHEDULE_ACCESS_TOKEN_HEADER, token ?: "")
            request.retrieve().toBodilessEntity()
            logger.info("已向调度中心上报执行器离线: {} ({})", group, address ?: "无地址")
        }.onFailure { exception ->
            logger.warn("上报执行器离线失败（{}）: {}", group, exception.message)
        }
    }
}

/** 上报到管理端的最小执行器注册载荷。 */
private data class ExecutorPresenceReport(
    val executorGroup: String,
    val executorName: String,
    val address: String?
)

/** 执行器主动离线上报载荷。 */
private data class ExecutorOfflineReport(
    val executorGroup: String,
    val address: String? = null
)
