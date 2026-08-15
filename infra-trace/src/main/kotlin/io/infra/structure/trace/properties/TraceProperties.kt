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
    var includeResponseHeader: Boolean = true,
    /** span 上报配置，用于把链路数据发送到追踪后台 */
    var report: TraceReportProperties = TraceReportProperties()
)

/**
 * span 上报配置项，前缀 `infra.trace.report`。
 *
 * @author sven
 */
data class TraceReportProperties(
    /** 是否开启 span 上报，默认关闭，不影响现有使用方 */
    var enabled: Boolean = false,
    /** 追踪后台采集接口地址，如 http://127.0.0.1:18090/api/trace/spans */
    var url: String = "",
    /** 上报的服务名；为空时回退到 spring.application.name */
    var serviceName: String = "",
    /** 上报超时（毫秒） */
    var timeoutMillis: Long = 3_000L,
    /** 是否采集并上报入参（请求体），默认关闭避免额外开销 */
    var captureRequestBody: Boolean = false,
    /** 是否采集并上报返回值（响应体），默认关闭避免额外开销 */
    var captureResponseBody: Boolean = false,
    /** 是否采集并上报请求头，默认关闭避免额外开销 */
    var captureRequestHeaders: Boolean = false,
    /** 入参/返回值采集的最大长度（字符），超出部分截断 */
    var maxBodyLength: Int = 2_000
)
