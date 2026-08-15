package io.infra.structure.trace.admin.controller

import io.infra.structure.trace.admin.model.TraceSummary
import io.infra.structure.trace.admin.store.TraceSpanStore
import io.infra.structure.trace.report.TraceSpan
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 链路查询接口，供后台页面展示最近链路与单条链路详情。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/trace")
class TraceQueryController(private val store: TraceSpanStore) {

    /** 最近 trace 摘要列表，默认取最近 100 条；支持按关键字和时间范围过滤。 */
    @GetMapping("/traces")
    fun recentTraces(
        @RequestParam(defaultValue = "100") limit: Int,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) fromMillis: Long?,
        @RequestParam(required = false) toMillis: Long?
    ): List<TraceSummary> {
        val safeLimit = limit.coerceIn(1, 200)
        val traceIds = if (keyword.isNullOrBlank()) {
            store.recentTraceIds(safeLimit, fromMillis, toMillis)
        } else {
            store.searchTraceIds(keyword, safeLimit, fromMillis, toMillis)
        }
        return traceIds.map { traceId -> summarize(traceId) }
    }

    /** 单条 trace 的全部 span（按开始时间升序），用于渲染瀑布图。 */
    @GetMapping("/traces/{traceId}")
    fun traceDetail(@PathVariable traceId: String): List<TraceSpan> =
        store.findByTraceId(traceId)

    /** 汇总单条 trace 的概览信息。 */
    private fun summarize(traceId: String): TraceSummary {
        val spans = store.findByTraceId(traceId)
        val root = spans.firstOrNull()
        return TraceSummary(
            traceId = traceId,
            startTimeMillis = root?.startTimeMillis ?: 0L,
            durationMillis = if (spans.isEmpty()) 0L else spans.maxOf { it.startTimeMillis + it.durationMillis } - spans.minOf { it.startTimeMillis },
            spanCount = spans.size,
            serviceNames = spans.map { it.serviceName }.distinct(),
            success = spans.all { it.success },
            uids = spans.mapNotNull { it.uid }.distinct()
        )
    }
}
