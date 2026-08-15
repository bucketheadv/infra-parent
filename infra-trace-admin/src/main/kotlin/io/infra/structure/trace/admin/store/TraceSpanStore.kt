package io.infra.structure.trace.admin.store

import io.infra.structure.trace.report.TraceSpan

/**
 * 追踪后台的 span 存取抽象。
 *
 * 当前实现为进程内内存存储（[InMemoryTraceSpanStore]），预留接口以便后续替换为
 * MySQL/ES 等持久化实现。
 *
 * @author sven
 */
interface TraceSpanStore {

    /** 保存一条上报的 span，保存失败时由调用方降级处理。 */
    fun add(span: TraceSpan)

    /** 按 traceId 返回全部 span（按开始时间升序）。 */
    fun findByTraceId(traceId: String): List<TraceSpan>

    /**
     * 返回最近 [limit] 条 traceId，最新在前；[fromMillis]/[toMillis] 按 span 开始时间
     * 过滤，只保留落在时间窗内的 trace。
     */
    fun recentTraceIds(limit: Int, fromMillis: Long? = null, toMillis: Long? = null): List<String>

    /**
     * 按关键字搜索 traceId，匹配 traceId、spanId、operation、serviceName、uid。
     * 关键字不区分大小写，支持逗号分隔多个值。
     */
    fun searchTraceIds(keyword: String, limit: Int = 200, fromMillis: Long? = null, toMillis: Long? = null): List<String>

    /** 统计已缓存的 trace 数量。 */
    fun traceCount(): Int
}
