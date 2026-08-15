package io.infra.structure.trace.report

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 基于 JDK HttpClient 的 span 上报器，异步、非阻塞、失败静默降级。
 *
 * 使用 JDK 自带的 HttpClient，避免为上报逻辑引入额外第三方依赖；上报失败仅记录
 * debug 日志，不影响业务请求。
 *
 * @author sven
 */
class HttpTraceReporter(
    private val url: String,
    private val timeoutMillis: Long = 3_000L
) : TraceReporter {

    private val logger = LoggerFactory.getLogger(HttpTraceReporter::class.java)

    private val objectMapper = ObjectMapper()

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofMillis(timeoutMillis))
        .build()

    override fun report(span: TraceSpan) {
        try {
            val body = objectMapper.writeValueAsBytes(span)
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMillis))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build()
            httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete { _, throwable ->
                    if (throwable != null) {
                        logger.debug("上报 span 失败，traceId={}", span.traceId, throwable)
                    }
                }
        } catch (exception: Exception) {
            logger.debug("上报 span 失败，traceId={}", span.traceId, exception)
        }
    }
}
