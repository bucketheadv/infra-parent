package io.infra.structure.trace.admin.controller

import io.infra.structure.trace.logging.LogEntry
import io.infra.structure.trace.logging.MemoryLogAppender
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 链路日志查询接口，供详情页展示请求关联的日志。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/trace/logs")
class TraceLogController {

    /** 按 traceId 查询该链路产生的全部日志（最新在后）。 */
    @GetMapping("/{traceId}")
    fun logsByTraceId(
        @PathVariable traceId: String,
        @RequestParam(defaultValue = "200") limit: Int
    ): List<LogEntry> = MemoryLogAppender.findByTraceId(traceId, limit)

    /** 按关键字搜索日志内容，返回命中的 traceId 列表。 */
    @GetMapping("/search")
    fun searchLogs(
        @RequestParam keyword: String,
        @RequestParam(defaultValue = "50") limit: Int
    ): List<String> = MemoryLogAppender.searchTraceIds(keyword, limit)
}
