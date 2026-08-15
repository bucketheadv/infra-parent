package io.infra.structure.trace.admin.controller

import io.infra.structure.trace.admin.model.TopologyLink
import io.infra.structure.trace.admin.store.TraceSpanStore
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 服务拓扑接口，基于 span 中的 parentSpanId 还原服务间调用关系。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/trace/topology")
class TraceTopologyController(private val store: TraceSpanStore) {

    @GetMapping
    fun topology(
        @RequestParam(defaultValue = "500") limit: Int,
        @RequestParam(required = false) fromMillis: Long?,
        @RequestParam(required = false) toMillis: Long?
    ): List<TopologyLink> {
        val safeLimit = limit.coerceIn(1, 2000)
        val traceIds = store.recentTraceIds(safeLimit, fromMillis, toMillis)
        val linkMap = mutableMapOf<Pair<String, String>, LinkAccumulator>()

        for (traceId in traceIds) {
            val spans = store.findByTraceId(traceId)
            val spanById = spans.associateBy { it.spanId }
            for (child in spans) {
                val parent = child.parentSpanId?.let { spanById[it] } ?: continue
                if (parent.serviceName == child.serviceName) continue
                val key = parent.serviceName to child.serviceName
                linkMap.getOrPut(key) { LinkAccumulator() }.add(child.durationMillis, child.success)
            }
        }

        return linkMap.map { (key, acc) ->
            TopologyLink(
                source = key.first,
                target = key.second,
                callCount = acc.count,
                errorCount = acc.errorCount,
                avgDurationMillis = if (acc.count > 0) acc.totalDuration.toDouble() / acc.count else 0.0
            )
        }.sortedByDescending { it.callCount }
    }

    private class LinkAccumulator {
        var count = 0
        var errorCount = 0
        var totalDuration = 0L

        fun add(durationMillis: Long, success: Boolean) {
            count++
            totalDuration += durationMillis
            if (!success) errorCount++
        }
    }
}
