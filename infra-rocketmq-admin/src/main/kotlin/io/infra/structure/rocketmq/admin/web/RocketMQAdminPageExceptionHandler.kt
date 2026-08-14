package io.infra.structure.rocketmq.admin.web

import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.servlet.ModelAndView

/** Thymeleaf 页面异常统一转友好错误页，避免向浏览器输出堆栈。 */
@ControllerAdvice(assignableTypes = [RocketMQAdminPageController::class])
class RocketMQAdminPageExceptionHandler {

    private val log = LoggerFactory.getLogger(RocketMQAdminPageExceptionHandler::class.java)

    @ExceptionHandler(RocketMQAdminException::class)
    fun adminException(exception: RocketMQAdminException): ModelAndView {
        log.warn("RocketMQ 管理页面异常: {}", exception.message)
        return errorView(exception.message ?: "管理操作失败")
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(exception: Exception): ModelAndView {
        log.error("RocketMQ 管理页面异常", exception)
        return errorView("页面加载异常：${exception.message ?: "未知错误"}")
    }

    private fun errorView(message: String): ModelAndView =
        ModelAndView("rocketmq-error").apply {
            addObject("errorMessage", message)
        }
}