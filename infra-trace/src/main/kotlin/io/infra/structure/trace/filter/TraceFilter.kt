package io.infra.structure.trace.filter

import io.infra.structure.trace.TraceContext
import io.infra.structure.trace.properties.TraceProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.util.HexFormat
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom

/**
 * 入站 HTTP 请求的 traceId/spanId 过滤器。
 *
 * 优先复用请求头中携带的 traceId（跨服务链路透传），缺失时按配置自动生成；
 * 每次入站请求都会新建一个 spanId，入站携带的 spanId 记录为 parentSpanId。
 * 随后写入 MDC 供本服务日志与下游调用使用，并在响应头回写。请求结束后清理 MDC，
 * 避免线程复用导致链路上下文串扰。
 *
 * @author sven
 */
class TraceFilter(private val properties: TraceProperties) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // 1. 从请求头提取调用方透传的 traceId；缺失时按配置自动生成
        val headerName = properties.headerName
        val incomingTraceId = request.getHeader(headerName)?.takeIf { it.isNotBlank() }
        val traceId = incomingTraceId ?: if (properties.generateIfAbsent) generateTraceId() else null

        if (traceId != null) {
            // 2. 每次入站请求新建一个 spanId，调用方携带的 spanId 作为 parentSpanId
            val spanId = generateSpanId()
            val incomingParentSpanId = request.getHeader(properties.spanHeaderName)?.takeIf { it.isNotBlank() }

            // 3. 写入 MDC，供本服务日志与出站调用读取
            TraceContext.setTraceId(traceId)
            TraceContext.setSpanId(spanId)
            if (incomingParentSpanId != null) {
                TraceContext.setParentSpanId(incomingParentSpanId)
            }
            // 4. 响应头回写，便于调用方拿到本次链路的 traceId/spanId
            if (properties.includeResponseHeader) {
                response.setHeader(headerName, traceId)
                response.setHeader(properties.spanHeaderName, spanId)
            }
        }
        try {
            // 5. 继续过滤链；异步/错误重分发不再重复生成，保证链路上下文一致
            filterChain.doFilter(request, response)
        } finally {
            // 6. 请求结束必须清理 MDC，避免线程池复用导致上下文串扰
            TraceContext.clear()
        }
    }

    override fun shouldNotFilterAsyncDispatch(): Boolean = true

    override fun shouldNotFilterErrorDispatch(): Boolean = true

    /** traceId 使用 32 位 hex（UUID 去横线），全链路保持一致 */
    private fun generateTraceId(): String = UUID.randomUUID().toString().replace("-", "")

    /** spanId 使用 16 位 hex（8 字节随机数），每次入站请求唯一 */
    private fun generateSpanId(): String {
        val bytes = ByteArray(8)
        ThreadLocalRandom.current().nextBytes(bytes)
        return HexFormat.of().formatHex(bytes)
    }
}
