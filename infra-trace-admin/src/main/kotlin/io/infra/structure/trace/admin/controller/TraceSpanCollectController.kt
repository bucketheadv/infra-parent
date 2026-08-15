package io.infra.structure.trace.admin.controller

import io.infra.structure.trace.admin.store.TraceSpanStore
import io.infra.structure.trace.report.TraceSpan
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * span 采集接口，接收各服务上报的 span 数据。
 *
 * 采集是遥测侧写路径：任何非法报文都不允许影响后台本身，解析或存储失败时仅记录
 * 日志并返回 200，避免上游上报方重试风暴。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/trace")
class TraceSpanCollectController(private val store: TraceSpanStore) {

    private val logger = LoggerFactory.getLogger(TraceSpanCollectController::class.java)

    @PostMapping("/spans")
    fun collect(@RequestBody span: TraceSpan): ResponseEntity<Unit> {
        try {
            store.add(span)
        } catch (exception: Exception) {
            logger.warn("保存 span 失败，traceId={}，spanId={}", span.traceId, span.spanId, exception)
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
