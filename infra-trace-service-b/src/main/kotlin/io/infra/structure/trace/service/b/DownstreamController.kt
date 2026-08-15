package io.infra.structure.trace.service.b

import io.infra.structure.trace.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 示例服务 B 的下游接口，回显链路透传的 traceId/spanId。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/demo")
class DownstreamController {

    private val logger = LoggerFactory.getLogger(DownstreamController::class.java)

    @GetMapping("/downstream")
    fun downstream(): Map<String, Any> {
        val traceId = TraceContext.getTraceId()
        val spanId = TraceContext.getSpanId()
        val parentSpanId = TraceContext.getParentSpanId()
        logger.info("服务 B 收到下游请求，traceId={}，spanId={}，parentSpanId={}", traceId, spanId, parentSpanId)
        return mapOf(
            "service" to "service-b",
            "traceId" to (traceId ?: ""),
            "spanId" to (spanId ?: ""),
            "parentSpanId" to (parentSpanId ?: "")
        )
    }
}
