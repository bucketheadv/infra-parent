package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleExecutor
import io.infra.structure.schedule.model.ExecutorHeartbeat
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/** 为已注册的远程执行器创建 HTTP 调用适配器。 */
fun interface ScheduleExecutorClientFactory {
    /** 根据心跳记录创建可调用执行器；没有有效地址时返回 null。 */
    fun create(heartbeat: ExecutorHeartbeat): ScheduleExecutor?
}

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

    /** 将执行上下文发送到执行器；通信异常会转换为可审计的失败结果。 */
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
    override fun create(heartbeat: ExecutorHeartbeat): ScheduleExecutor? {
        val address = heartbeat.address?.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return null
        val token = accessToken?.takeIf { it.isNotBlank() }
        if (authenticationEnabled && token == null) return null
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
