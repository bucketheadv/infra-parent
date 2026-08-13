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
    private val connectTimeoutMillis: Long,
    private val readTimeoutMillis: Long
) : ScheduleExecutor {
    override fun execute(context: JobExecutionContext): JobExecutionResult = try {
        // /run 是同步协议；网络读超时不能早于调度器为本次任务设定的执行上限，
        // 否则任务仍在执行器运行时会被误判失败并按重试策略重复投递。
        val effectiveReadTimeout = maxOf(
            readTimeoutMillis,
            context.executionTimeoutMillis.coerceAtMost(Long.MAX_VALUE - HTTP_TIMEOUT_BUFFER_MILLIS) + HTTP_TIMEOUT_BUFFER_MILLIS
        )
        val client = RestClient.builder()
            .baseUrl(address.removeSuffix("/"))
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofMillis(connectTimeoutMillis))
                setReadTimeout(Duration.ofMillis(effectiveReadTimeout))
            })
            .build()
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
        // 只有传输中断与服务端 5xx 可能发生在 Handler 已开始之后；4xx 是执行器明确拒绝，
        // 必须按确定失败处理，不能让认证/参数错误伪装成“未知执行”。
        JobExecutionResult.failure(
            "调用执行器 $id 失败: ${describeHttpError(exception)}",
            uncertain = isDeliveryUncertain(exception)
        )
    }

    /** 4xx 已明确未受理；连接/读超时、无响应和 5xx 均可能在服务端已开始执行后发生。 */
    private fun isDeliveryUncertain(exception: Exception): Boolean {
        val response = generateSequence(exception as Throwable) { it.cause }
            .filterIsInstance<RestClientResponseException>()
            .firstOrNull()
        return response == null || response.statusCode.is5xxServerError
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

    private companion object {
        /** 为执行器完成最终日志回调预留的网络缓冲。 */
        const val HTTP_TIMEOUT_BUFFER_MILLIS = 5_000L
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
