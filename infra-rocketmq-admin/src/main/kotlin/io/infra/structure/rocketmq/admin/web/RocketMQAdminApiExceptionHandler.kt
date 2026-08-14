package io.infra.structure.rocketmq.admin.web

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/** 管理 REST 接口的异常转换，返回 JSON 而非堆栈。 */
@RestControllerAdvice(annotations = [RestController::class])
class RocketMQAdminApiExceptionHandler {

    private val log = LoggerFactory.getLogger(RocketMQAdminApiExceptionHandler::class.java)

    /** 业务异常：返回可读原因。 */
    @ExceptionHandler(RocketMQAdminException::class)
    fun adminException(exception: RocketMQAdminException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("message" to (exception.message ?: "管理操作失败")))

    /** 参数校验失败。 */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun validation(exception: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: exception.bindingResult.globalErrors.firstOrNull()?.defaultMessage
            ?: "请求参数不合法"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to message))
    }

    /** 请求 JSON 无法解析时返回受控提示。 */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableRequest(): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("message" to "请求参数格式不正确"))

    /** 参数缺失/类型不合法。 */
    @ExceptionHandler(IllegalArgumentException::class)
    fun illegalArgument(exception: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("message" to (exception.message ?: "请求参数不合法")))

    /** 兜底异常：隐藏内部堆栈。 */
    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ResponseEntity<Map<String, String>> {
        log.error("RocketMQ 管理接口异常", exception)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("message" to "管理操作异常：${exception.message ?: "未知错误"}"))
    }
}
