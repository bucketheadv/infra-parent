package io.infra.structure.trace.service.b

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 服务 B 的异常演示接口，用于下游异常路由测试。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/demo")
class DownstreamErrorController {

    @GetMapping("/downstream-error")
    fun downstreamError(@RequestParam(defaultValue = "下游库存不足") reason: String): Map<String, Any> {
        // 模拟下游业务异常，由全局异常处理器记录后随 span 上报
        throw IllegalStateException("下游服务异常: $reason")
    }
}
