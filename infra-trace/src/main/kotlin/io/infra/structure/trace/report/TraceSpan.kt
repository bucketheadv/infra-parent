package io.infra.structure.trace.report

/**
 * 一条完整的 span 数据，用于向追踪后台上报。
 *
 * 字段与链路语义对齐：traceId 全链路一致，spanId 每次入站请求新建，
 * parentSpanId 为调用方 spanId（可空）。追踪后台依据这些字段还原调用树。
 *
 * 除链路标识与耗时外，还承载请求诊断信息：入参（请求体）、返回值（响应体）以及
 * 异常原因与堆栈，便于后台直接定位问题。请求/响应体按配置截断后上报，默认不采集。
 *
 * @author sven
 */
data class TraceSpan(
    /** 全链路唯一标识，32 位 hex，跨服务一致 */
    val traceId: String,
    /** 本次入站请求的 span 标识，16 位 hex */
    val spanId: String,
    /** 调用方的 spanId，根 span 为 null */
    val parentSpanId: String?,
    /** 上报该 span 的服务名（spring.application.name） */
    val serviceName: String,
    /** 请求 URI，如 /api/demo/upstream */
    val operation: String,
    /** HTTP 请求方法（GET / POST 等），非 HTTP 场景为 null */
    val httpMethod: String? = null,
    /** 请求开始时间（Unix 毫秒） */
    val startTimeMillis: Long,
    /** 请求处理耗时（毫秒） */
    val durationMillis: Long,
    /** 请求是否成功（无异常且 HTTP 状态码 < 400） */
    val success: Boolean,
    /** 异常信息（message），无异常时为 null */
    val errorMessage: String? = null,
    /** 异常堆栈，仅采集到异常时存在 */
    val errorStackTrace: String? = null,
    /** 入参（请求体），按配置采集并截断 */
    val requestBody: String? = null,
    /** 返回值（响应体），按配置采集并截断 */
    val responseBody: String? = null,
    /** 请求头（按配置采集并截断） */
    val requestHeaders: String? = null,
    /** 用户标识，从请求头或请求参数中自动提取 */
    val uid: String? = null
)
