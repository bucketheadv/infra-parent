package io.infra.structure.trace.service.a

import io.infra.structure.trace.TraceContext
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 示例异常接口，用于演示异常原因与堆栈上报。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/demo")
class ErrorDemoController {

    private val logger = LoggerFactory.getLogger(ErrorDemoController::class.java)

    @GetMapping("/boom")
    fun boom(@RequestParam(defaultValue = "演示异常") reason: String): Map<String, Any> {
        logger.info("服务 A 即将抛出异常，reason={}", reason)
        // 模拟业务异常；由全局异常处理器调用 TraceContext.recordError 上报原因与堆栈
        throw IllegalStateException("演示异常: $reason")
    }
}
