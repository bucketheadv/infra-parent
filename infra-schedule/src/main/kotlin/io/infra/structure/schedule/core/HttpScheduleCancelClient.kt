package io.infra.structure.schedule.core

import io.infra.structure.schedule.web.ScheduleWebPaths
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

/** 调度中心向远程执行器发起按日志 ID 终止执行的 HTTP 客户端。 */
class HttpScheduleCancelClient(
    private val accessToken: String?,
    private val authenticationEnabled: Boolean,
    connectTimeoutMillis: Long,
    readTimeoutMillis: Long
) {
    private val requestFactory = SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(Duration.ofMillis(connectTimeoutMillis))
        setReadTimeout(Duration.ofMillis(readTimeoutMillis))
    }

    /**
     * 请求远程执行器终止 [logId] 对应任务。
     * @return true 表示执行器确认命中并取消；通信失败或未命中返回 false
     */
    fun cancel(baseUrl: String, logId: Long): Boolean = try {
        val client = RestClient.builder()
            .baseUrl(baseUrl.removeSuffix("/"))
            .requestFactory(requestFactory)
            .build()
        val request = client.post().uri(ScheduleWebPaths.EXECUTOR_CANCEL)
        if (authenticationEnabled && !accessToken.isNullOrBlank()) {
            request.header(SCHEDULE_ACCESS_TOKEN_HEADER, accessToken)
        }
        val response = request
            .body(ExecutorCancelRequest(logId))
            .retrieve()
            .body(ExecutorCancelResponse::class.java)
        response?.cancelled == true
    } catch (_: Exception) {
        false
    }
}

/** 执行器终止请求体。 */
data class ExecutorCancelRequest(
    val logId: Long
)

/** 执行器终止响应体。 */
data class ExecutorCancelResponse(
    val cancelled: Boolean
)
