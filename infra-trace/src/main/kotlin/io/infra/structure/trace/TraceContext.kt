package io.infra.structure.trace

import org.slf4j.MDC

/**
 * 分布式日志追踪的上下文门面。
 *
 * 以 MDC（Mapped Diagnostic Context）为存储载体，确保同一线程内日志、下游调用与
 * 异步任务都能读取到一致的 traceId 与 spanId。key 与 header 名称可在启动时通过
 * 配置项 `infra.trace.*` 覆盖。
 *
 * span 语义与常见链路追踪一致：traceId 在整条链路保持一致；每次入站请求新建一个
 * spanId，入站请求携带的 spanId 作为 parentSpanId，从而在日志中还原调用树。
 *
 * @author sven
 */
object TraceContext {

    /** 默认请求头名称，用于跨服务传递 traceId */
    const val DEFAULT_HEADER_NAME = "X-Request-Id"

    /** 默认请求头名称，用于跨服务传递 spanId */
    const val DEFAULT_SPAN_HEADER_NAME = "X-Span-Id"

    /** 默认 MDC key，日志模式中使用 %X{traceId} 即可输出 */
    const val DEFAULT_MDC_KEY = "traceId"

    /** 默认 MDC key，日志模式中使用 %X{spanId} 即可输出 */
    const val DEFAULT_SPAN_MDC_KEY = "spanId"

    /** 父 span 的 MDC key，日志模式中使用 %X{parentSpanId} 即可输出 */
    const val PARENT_SPAN_MDC_KEY = "parentSpanId"

    @Volatile
    var headerName: String = DEFAULT_HEADER_NAME
        private set

    @Volatile
    var mdcKey: String = DEFAULT_MDC_KEY
        private set

    @Volatile
    var spanHeaderName: String = DEFAULT_SPAN_HEADER_NAME
        private set

    @Volatile
    var spanMdcKey: String = DEFAULT_SPAN_MDC_KEY
        private set

    /**
     * 以配置值覆盖默认的 header 与 MDC key，仅允许自动配置在启动阶段调用一次。
     */
    fun configure(headerName: String, mdcKey: String, spanHeaderName: String, spanMdcKey: String) {
        this.headerName = headerName
        this.mdcKey = mdcKey
        this.spanHeaderName = spanHeaderName
        this.spanMdcKey = spanMdcKey
    }

    /**
     * 获取当前线程的 traceId；当前链路没有 traceId 时返回 null。
     */
    fun getTraceId(): String? = MDC.get(mdcKey)

    /**
     * 写入当前线程的 traceId。
     */
    fun setTraceId(traceId: String) {
        MDC.put(mdcKey, traceId)
    }

    /**
     * 获取当前线程的 spanId；当前链路没有 spanId 时返回 null。
     */
    fun getSpanId(): String? = MDC.get(spanMdcKey)

    /**
     * 写入当前线程的 spanId。
     */
    fun setSpanId(spanId: String) {
        MDC.put(spanMdcKey, spanId)
    }

    /**
     * 写入当前线程的父 spanId（来自入站请求头），仅在调用方携带时设置。
     */
    fun setParentSpanId(parentSpanId: String) {
        MDC.put(PARENT_SPAN_MDC_KEY, parentSpanId)
    }

    /**
     * 清除当前线程的链路上下文，请求结束或任务完成时必须调用。
     */
    fun clear() {
        MDC.remove(mdcKey)
        MDC.remove(spanMdcKey)
        MDC.remove(PARENT_SPAN_MDC_KEY)
    }
}
