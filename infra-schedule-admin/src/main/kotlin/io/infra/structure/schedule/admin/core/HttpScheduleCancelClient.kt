package io.infra.structure.schedule.admin.core

import io.infra.structure.schedule.core.ExecutorBeatResponse
import io.infra.structure.schedule.core.ExecutorCancelRequest
import io.infra.structure.schedule.core.ExecutorCancelResponse
import io.infra.structure.schedule.core.ExecutorIdleBeatRequest
import io.infra.structure.schedule.core.ExecutorIdleBeatResponse
import io.infra.structure.schedule.core.ExecutorRunningRequest
import io.infra.structure.schedule.core.ExecutorRunningResponse
import io.infra.structure.schedule.core.SCHEDULE_ACCESS_TOKEN_HEADER
import io.infra.structure.schedule.web.ScheduleWebPaths
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import java.time.Duration

/** 调度中心向远程执行器发起终止 / 探活 / 空闲检测的 HTTP 客户端。 */
class HttpScheduleCancelClient(
    private val accessToken: String?,
    private val authenticationEnabled: Boolean,
    connectTimeoutMillis: Long,
    readTimeoutMillis: Long
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val requestFactory = SimpleClientHttpRequestFactory().apply {
        setConnectTimeout(Duration.ofMillis(connectTimeoutMillis))
        setReadTimeout(Duration.ofMillis(readTimeoutMillis))
    }

    /**
     * 请求远程执行器终止 [logId] 对应任务。
     * @return true 表示执行器确认命中并取消；通信失败或未命中返回 false
     */
    fun cancel(baseUrl: String, logId: Long): Boolean = try {
        val response = post(baseUrl, ScheduleWebPaths.EXECUTOR_CANCEL, ExecutorCancelRequest(logId), ExecutorCancelResponse::class.java)
        response?.cancelled == true
    } catch (exception: Exception) {
        logProbeFailure(baseUrl, ScheduleWebPaths.EXECUTOR_CANCEL, exception)
        false
    }

    /**
     * 查询远程执行器上 [logId] 是否仍在运行。
     * @return true/false 表示明确结果；null 表示节点不可达或协议失败
     */
    fun isRunning(baseUrl: String, logId: Long): Boolean? = try {
        val response = post(baseUrl, ScheduleWebPaths.EXECUTOR_RUNNING, ExecutorRunningRequest(logId), ExecutorRunningResponse::class.java)
            ?: return null
        response.running
    } catch (exception: Exception) {
        logProbeFailure(baseUrl, ScheduleWebPaths.EXECUTOR_RUNNING, exception)
        null
    }

    /**
     * 执行器进程探活。
     * @return true 存活；false 可达但未通过探活；null 不可达或协议失败
     */
    fun beat(baseUrl: String): Boolean? = try {
        val response = post(baseUrl, ScheduleWebPaths.EXECUTOR_BEAT, emptyMap<String, Any>(), ExecutorBeatResponse::class.java)
            ?: return null
        response.alive
    } catch (exception: Exception) {
        logProbeFailure(baseUrl, ScheduleWebPaths.EXECUTOR_BEAT, exception)
        null
    }

    /**
     * 空闲检测：目标 job 在该执行器上是否空闲。
     * @return true 空闲；false 忙碌；null 不可达
     */
    fun idleBeat(baseUrl: String, jobId: Long): Boolean? = try {
        val response = post(
            baseUrl,
            ScheduleWebPaths.EXECUTOR_IDLE_BEAT,
            ExecutorIdleBeatRequest(jobId),
            ExecutorIdleBeatResponse::class.java
        ) ?: return null
        response.idle
    } catch (exception: Exception) {
        logProbeFailure(baseUrl, ScheduleWebPaths.EXECUTOR_IDLE_BEAT, exception)
        null
    }

    private fun <T : Any> post(baseUrl: String, path: String, body: Any, responseType: Class<T>): T? {
        if (authenticationEnabled && accessToken.isNullOrBlank()) {
            logger.warn(
                "已启用执行器令牌校验但未配置 infra.schedule.executor.access-token，探活/调用可能失败: url={}{}",
                baseUrl,
                path
            )
        }
        val client = RestClient.builder()
            .baseUrl(baseUrl.removeSuffix("/"))
            .requestFactory(requestFactory)
            .build()
        val request = client.post()
            .uri(path)
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON)
        if (authenticationEnabled && !accessToken.isNullOrBlank()) {
            request.header(SCHEDULE_ACCESS_TOKEN_HEADER, accessToken)
        }
        return request.body(body).retrieve().body(responseType)
    }

    private fun logProbeFailure(baseUrl: String, path: String, exception: Exception) {
        if (exception is RestClientResponseException && exception.statusCode.value() == 401) {
            logger.warn(
                "执行器返回 401，请检查调度中心与执行器的 access-token / auth-enabled 配置是否一致: url={}{}",
                baseUrl,
                path
            )
            return
        }
        logger.debug("执行器探活/调用失败: url={}{}, reason={}", baseUrl, path, exception.message)
    }
}
