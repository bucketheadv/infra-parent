package io.infra.structure.trace.propagation

import io.infra.structure.trace.TraceContext
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.ExchangeFunction
import reactor.core.publisher.Mono

/**
 * WebClient 出站请求的 traceId/spanId 传播过滤器。
 *
 * 当前链路存在 traceId/spanId 时，将其写入出站请求头，实现跨服务链路透传。引用方需将该
 * 过滤器加入 WebClient 的 filter 链。
 *
 * @author sven
 */
class WebClientTraceFilter : ExchangeFilterFunction {

    override fun filter(request: ClientRequest, next: ExchangeFunction): Mono<ClientResponse> {
        // 1. 读取当前链路上下文；两者都为空时无需改写，直接放行
        val traceId = TraceContext.getTraceId()
        val spanId = TraceContext.getSpanId()
        if (traceId == null && spanId == null) {
            return next.exchange(request)
        }
        // 2. 在原始请求基础上追加 traceId/spanId 请求头
        val tracedRequest = ClientRequest.from(request).apply {
            if (traceId != null) header(TraceContext.headerName, traceId)
            if (spanId != null) header(TraceContext.spanHeaderName, spanId)
        }.build()
        // 3. 使用带链路头的请求继续执行
        return next.exchange(tracedRequest)
    }
}
