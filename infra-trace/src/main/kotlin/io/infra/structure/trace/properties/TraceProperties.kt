package io.infra.structure.trace.properties

import io.infra.structure.trace.TraceContext
import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 分布式日志追踪配置项，前缀 `infra.trace`。
 *
 * @author sven
 */
@ConfigurationProperties(prefix = "infra.trace")
data class TraceProperties(
    /** 总开关，置为 false 时整个追踪模块不生效 */
    var enabled: Boolean = true,
    /** 传递 traceId 的 HTTP header 名称 */
    var headerName: String = TraceContext.DEFAULT_HEADER_NAME,
    /** 传递 spanId 的 HTTP header 名称 */
    var spanHeaderName: String = TraceContext.DEFAULT_SPAN_HEADER_NAME,
    /** 写入 MDC 的 traceId key，日志模式中按此 key 取值 */
    var mdcKey: String = TraceContext.DEFAULT_MDC_KEY,
    /** 写入 MDC 的 spanId key，日志模式中按此 key 取值 */
    var spanMdcKey: String = TraceContext.DEFAULT_SPAN_MDC_KEY,
    /** 入站请求未携带 traceId 时是否自动生成 */
    var generateIfAbsent: Boolean = true,
    /** 是否在响应头中回写 traceId 与 spanId，便于调用方排查链路 */
    var includeResponseHeader: Boolean = true
)
