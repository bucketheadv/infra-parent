package io.infra.structure.trace.service.b

import io.infra.structure.trace.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * 服务 B 全局异常处理器，把异常写入链路上下文随 span 上报。
 *
 * @author sven
 */
@RestControllerAdvice
class DownstreamExceptionHandler {

    private val logger = LoggerFactory.getLogger(DownstreamExceptionHandler::class.java)

    @ExceptionHandler(Throwable::class)
    fun handle(exception: Throwable): ResponseEntity<Map<String, String>> {
        logger.error("服务 B 接口异常，traceId={}，error={}", TraceContext.getTraceId(), exception.message, exception)
        TraceContext.recordError(exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("code" to "500", "message" to (exception.message ?: "下游内部错误")))
    }
}
