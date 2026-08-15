package io.infra.structure.trace.propagation

import io.infra.structure.trace.TraceContext
import io.ktor.client.plugins.api.createClientPlugin

/**
 * Ktor HttpClient 的 traceId/spanId 传播插件。
 *
 * 当前链路存在 traceId/spanId 时，自动写入每个出站请求头，实现跨服务链路透传。
 * 引用方在构建 HttpClient 时安装即可：
 * ```
 * val client = HttpClient(CIO) { install(TraceKtorPlugin) }
 * ```
 *
 * @author sven
 */
val TraceKtorPlugin = createClientPlugin("TraceKtorPlugin") {
    onRequest { request, _ ->
        // 1. 透传 traceId，保证跨服务同一链路标识一致
        TraceContext.getTraceId()?.let { traceId ->
            request.headers.append(TraceContext.headerName, traceId)
        }
        // 2. 透传当前 spanId，下游将其作为 parentSpanId 还原调用树
        TraceContext.getSpanId()?.let { spanId ->
            request.headers.append(TraceContext.spanHeaderName, spanId)
        }
    }
}
