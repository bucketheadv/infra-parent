package io.infra.structure.trace.admin.store

import io.infra.structure.trace.report.TraceSpan
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 进程内内存实现的 span 存储。
 *
 * 以 traceId 为键保存 span 列表，保留最近 [maxTraces] 条 trace（默认 2000），每条
 * trace 最多保留 [maxSpansPerTrace] 个 span（默认 100），超出部分按时间淘汰，避免
 * 后台长期运行导致内存无限增长。所有并发修改通过锁串行化保证一致性。
 *
 * @author sven
 */
@Component
class InMemoryTraceSpanStore(
    private val maxTraces: Int = 2000,
    private val maxSpansPerTrace: Int = 100
) : TraceSpanStore {

    private val lock = ReentrantLock()
    private val spansByTrace = LinkedHashMap<String, MutableList<TraceSpan>>()

    override fun add(span: TraceSpan) {
        lock.withLock {
            var spans = spansByTrace[span.traceId]
            if (spans == null) {
                spans = ArrayList(maxSpansPerTrace)
                spansByTrace[span.traceId] = spans
            }
            spans.add(span)
            if (spans.size > maxSpansPerTrace) {
                // 按开始时间升序，保留最新的 maxSpansPerTrace 条
                spans.sortBy { it.startTimeMillis }
                while (spans.size > maxSpansPerTrace) {
                    spans.removeAt(0)
                }
            }
            trimOldest()
        }
    }

    override fun findByTraceId(traceId: String): List<TraceSpan> =
        lock.withLock { spansByTrace[traceId]?.toList().orEmpty().sortedBy { it.startTimeMillis } }

    override fun recentTraceIds(limit: Int, fromMillis: Long?, toMillis: Long?): List<String> {
        lock.withLock {
            return spansByTrace.entries.asSequence()
                .filter { (_, spans) ->
                    val start = spans.minOfOrNull { it.startTimeMillis }
                    start != null &&
                        (fromMillis == null || start >= fromMillis) &&
                        (toMillis == null || start <= toMillis)
                }
                .sortedByDescending { (_, spans) -> spans.minOf { it.startTimeMillis } }
                .take(limit)
                .map { it.key }
                .toList()
        }
    }

    override fun searchTraceIds(keyword: String, limit: Int, fromMillis: Long?, toMillis: Long?): List<String> {
        lock.withLock {
            val keys = keyword.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
            return spansByTrace.entries.asSequence()
                .filter { (_, spans) ->
                    val start = spans.minOfOrNull { it.startTimeMillis }
                    if (start != null && (fromMillis != null && start < fromMillis)) return@filter false
                    if (start != null && (toMillis != null && start > toMillis)) return@filter false
                    if (keys.isEmpty()) return@filter true
                    spans.any { span ->
                        val uid = span.uid
                        val reqBody = span.requestBody
                        val respBody = span.responseBody
                        val reqHeaders = span.requestHeaders
                        keys.any { k ->
                            span.traceId.lowercase().contains(k) ||
                                span.spanId.lowercase().contains(k) ||
                                span.operation.lowercase().contains(k) ||
                                span.serviceName.lowercase().contains(k) ||
                                (uid != null && uid.lowercase().contains(k)) ||
                                (reqBody != null && reqBody.lowercase().contains(k)) ||
                                (respBody != null && respBody.lowercase().contains(k)) ||
                                (reqHeaders != null && reqHeaders.lowercase().contains(k))
                        }
                    }
                }
                .sortedByDescending { (_, spans) -> spans.minOf { it.startTimeMillis } }
                .take(limit)
                .map { it.key }
                .toList()
        }
    }

    override fun traceCount(): Int = lock.withLock { spansByTrace.size }

    /** 淘汰最旧的 trace，保证总容量不超过 [maxTraces]。 */
    private fun trimOldest() {
        while (spansByTrace.size > maxTraces) {
            spansByTrace.entries.firstOrNull()?.let { spansByTrace.remove(it.key) }
        }
    }
}
