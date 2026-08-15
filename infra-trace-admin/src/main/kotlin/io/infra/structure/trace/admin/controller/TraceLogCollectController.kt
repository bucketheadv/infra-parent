package io.infra.structure.trace.admin.controller

import io.infra.structure.trace.logging.LogEntry
import io.infra.structure.trace.logging.MemoryLogAppender
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 链路日志采集接口，接收各服务上报的日志条目。
 *
 * 采集是遥测侧写路径：任何非法报文都不允许影响后台本身，解析或存储失败时仅记录
 * 日志并返回 200，避免上游上报方重试风暴。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/trace/logs")
class TraceLogCollectController {

    private val logger = LoggerFactory.getLogger(TraceLogCollectController::class.java)

    /** 接收一批日志，按 traceId 写入内存缓存供详情页查询。 */
    @PostMapping
    fun collect(@RequestBody logs: List<LogEntry>): ResponseEntity<Unit> {
        try {
            MemoryLogAppender.addEntries(logs)
        } catch (exception: Exception) {
            logger.warn("保存日志失败，条数={}", logs.size, exception)
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).build()
    }
}
