package io.infra.structure.trace.report

import com.fasterxml.jackson.databind.ObjectMapper
import io.infra.structure.trace.logging.LogEntry
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 基于 JDK HttpClient 的链路日志上报器，异步、非阻塞、失败静默降级。
 *
 * 与 [HttpTraceReporter] 保持一致：使用 JDK 自带 HttpClient，避免额外第三方依赖；
 * 上报失败仅记录 debug 日志，不影响业务请求。
 *
 * @author sven
 */
class HttpLogReporter(
    private val url: String,
    private val timeoutMillis: Long = 3_000L
) : LogReporter {

    private val logger = LoggerFactory.getLogger(HttpLogReporter::class.java)

    private val objectMapper = ObjectMapper()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMillis))
        .build()

    override fun reportLogs(logs: List<LogEntry>) {
        if (logs.isEmpty()) return
        try {
            val body = objectMapper.writeValueAsBytes(logs)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        logger.debug("上报日志失败，traceId={}", logs.first().traceId, throwable)
                    }
                }
        } catch (exception: Exception) {
            logger.debug("上报日志失败，traceId={}", logs.firstOrNull()?.traceId, exception)
        }
    }
}
