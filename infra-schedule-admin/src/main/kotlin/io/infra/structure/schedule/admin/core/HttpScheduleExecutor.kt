package io.infra.structure.schedule.admin.core

import io.infra.structure.schedule.api.ScheduleExecutor
import io.infra.structure.schedule.core.ExecutorAddresses
import io.infra.structure.schedule.core.ScheduleExecutorClientFactory
import io.infra.structure.schedule.core.SCHEDULE_ACCESS_TOKEN_HEADER
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/** 通过执行器 HTTP 协议执行任务的客户端实现。 */
class HttpScheduleExecutor(
    override val id: String,
    override val group: String,
    private val address: String,
    private val accessToken: String?,
    connectTimeoutMillis: Long,
    readTimeoutMillis: Long
) : ScheduleExecutor {
    private val client = RestClient.builder()
        .baseUrl(address.removeSuffix("/"))
        .requestFactory(SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofMillis(connectTimeoutMillis))
            setReadTimeout(Duration.ofMillis(readTimeoutMillis))
        })
        .build()

    override fun execute(context: JobExecutionContext): JobExecutionResult = try {
        val request = client.post()
            .uri(ScheduleWebPaths.EXECUTOR_RUN)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
        if (!accessToken.isNullOrBlank()) request.header(SCHEDULE_ACCESS_TOKEN_HEADER, accessToken)
        request
            .body(context)
            .retrieve()
            .body(JobExecutionResult::class.java)
            ?: JobExecutionResult.failure("执行器未返回执行结果: $address")
    } catch (exception: Exception) {
        if (exception is InterruptedException ||
            exception.cause is InterruptedException ||
            Thread.currentThread().isInterrupted
        ) {
            Thread.currentThread().interrupt()
            return JobExecutionResult.cancelled("任务已被终止")
        }
        JobExecutionResult.failure("调用执行器 $id 失败: ${describeHttpError(exception)}")
    }

    private fun describeHttpError(exception: Exception): String {
        val responseException = generateSequence(exception as Throwable) { it.cause }
            .filterIsInstance<RestClientResponseException>()
            .firstOrNull()
        if (responseException != null) {
            val body = responseException.responseBodyAsString.take(500)
            return "HTTP ${responseException.statusCode.value()} ${responseException.statusText}" +
                if (body.isNotBlank()) " body=$body" else ""
        }
        if (exception is RestClientException) {
            return exception.message ?: exception.javaClass.simpleName
        }
        return exception.message ?: exception.javaClass.simpleName
    }
}

/** 创建使用统一共享令牌的 HTTP 执行器。 */
class HttpScheduleExecutorClientFactory(
    private val accessToken: String?,
    private val authenticationEnabled: Boolean,
    private val connectTimeoutMillis: Long,
    private val readTimeoutMillis: Long
) : ScheduleExecutorClientFactory {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun create(heartbeat: ExecutorHeartbeat): ScheduleExecutor? {
        val address = ExecutorAddresses.normalizeHttpBaseUrl(heartbeat.address)
        if (address == null) {
            logger.warn(
                "跳过无效执行器地址: executorId={}, group={}, raw={}",
                heartbeat.id,
                heartbeat.executorGroup,
                heartbeat.address
            )
            return null
        }
        val token = accessToken?.takeIf { it.isNotBlank() }
        if (authenticationEnabled && token == null) {
            logger.warn(
                "已启用执行器令牌校验但未配置 infra.schedule.executor.access-token，无法路由远程节点: executorId={}, address={}",
                heartbeat.id,
                address
            )
            return null
        }
        return HttpScheduleExecutor(
            heartbeat.executorGroup,
            heartbeat.executorGroup,
            address,
            token,
            connectTimeoutMillis,
            readTimeoutMillis
        )
    }
}
