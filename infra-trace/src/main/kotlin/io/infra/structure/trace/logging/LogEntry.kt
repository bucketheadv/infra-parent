package io.infra.structure.trace.logging

/**
 * 内存中缓存的单条日志条目，用于链路详情页展示。
 *
 * @author sven
 */
data class LogEntry(
    /** 日志产生时间（Unix 毫秒） */
    val timestamp: Long,
    /** 日志级别（INFO / WARN / ERROR 等） */
    val level: String,
    /** 输出该日志的 Logger 简称（类名） */
    val logger: String,
    /** 日志消息正文 */
    val message: String,
    /** 所属链路 traceId */
    val traceId: String,
    /** 所属 span 的 spanId（可能为 null） */
    val spanId: String? = null,
    /** 父 span 的 spanId（可能为 null） */
    val parentSpanId: String? = null,
    /** 异常堆栈文本（仅异常日志存在） */
    val exception: String? = null
)
