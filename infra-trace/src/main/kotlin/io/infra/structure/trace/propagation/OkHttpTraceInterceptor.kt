package io.infra.structure.trace.propagation

import io.infra.structure.trace.TraceContext
import okhttp3.Interceptor
import okhttp3.Response

/**
 * OkHttp 出站请求的 traceId/spanId 传播拦截器。
 *
 * 当前链路存在 traceId/spanId 时，将其写入出站请求头，实现跨服务链路透传。
 * 引用方构建 OkHttpClient 时注册即可：
 * ```
 * val client = OkHttpClient.Builder().addInterceptor(okHttpTraceInterceptor).build()
 * ```
 *
 * @author sven
 */
class OkHttpTraceInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        // 1. 读取当前链路上下文；两者都为空时直接放行，避免不必要的请求重建
        val request = chain.request()
        val traceId = TraceContext.getTraceId()
        val spanId = TraceContext.getSpanId()
        if (traceId == null && spanId == null) {
            return chain.proceed(request)
        }
        // 2. 基于原始请求重建并追加 traceId/spanId 请求头
        val tracedRequest = request.newBuilder().apply {
            if (traceId != null) header(TraceContext.headerName, traceId)
            if (spanId != null) header(TraceContext.spanHeaderName, spanId)
        }.build()
        // 3. 使用带链路头的请求继续执行
        return chain.proceed(tracedRequest)
    }
}
