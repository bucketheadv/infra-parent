package io.infra.structure.trace.propagation

import io.infra.structure.trace.TraceContext
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * RestTemplate 出站请求的 traceId/spanId 传播拦截器。
 *
 * 当前链路存在 traceId/spanId 时，将其写入出站请求头，实现跨服务链路透传。引用方需将
 * 该拦截器注册到自己的 RestTemplate：`restTemplate.interceptors.add(interceptor)`。
 *
 * @author sven
 */
class RestTemplateTraceInterceptor : ClientHttpRequestInterceptor {

    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        // 1. 透传 traceId，保证跨服务同一链路标识一致
        TraceContext.getTraceId()?.let { traceId ->
            request.headers.set(TraceContext.headerName, traceId)
        }
        // 2. 透传当前 spanId，下游将其作为 parentSpanId 还原调用树
        TraceContext.getSpanId()?.let { spanId ->
            request.headers.set(TraceContext.spanHeaderName, spanId)
        }
        // 3. 继续执行后续拦截器与实际请求
        return execution.execute(request, body)
    }
}
