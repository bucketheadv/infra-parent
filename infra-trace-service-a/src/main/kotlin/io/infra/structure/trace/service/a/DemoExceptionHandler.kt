package io.infra.structure.trace.service.a

import io.infra.structure.trace.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 全局异常处理器。
 *
 * 把捕获到的异常写入链路上下文（[TraceContext.recordError]），让 infra-trace 过滤器
 * 在请求结束后随 span 一并上报异常原因与堆栈，便于追踪后台直接定位问题。
 *
 * @author sven
 */
@RestControllerAdvice
class DemoExceptionHandler {

    private val logger = LoggerFactory.getLogger(DemoExceptionHandler::class.java)

    @ExceptionHandler(Throwable::class)
    fun handle(exception: Throwable): ResponseEntity<Map<String, String>> {
        logger.error("接口异常，traceId={}，error={}", TraceContext.getTraceId(), exception.message)
        TraceContext.recordError(exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("code" to "500", "message" to (exception.message ?: "内部错误")))
    }
}
