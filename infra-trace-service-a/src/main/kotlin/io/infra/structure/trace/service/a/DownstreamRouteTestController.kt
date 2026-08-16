package io.infra.structure.trace.service.a

import io.infra.structure.trace.TraceContext
import io.infra.structure.trace.propagation.TraceKtorPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.ResponseException
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 下游异常路由测试接口。
 *
 * 服务 A 调用服务 B 的异常接口，Ktor 在收到非 2xx 响应时抛出 [ResponseException]，
 * 本接口不吞异常，让其向上传播由全局异常处理器记录，从而验证：
 * 1. traceId 跨服务一致，parentSpanId 还原调用树；
 * 2. 下游（服务 B）异常原因与堆栈随 span 上报；
 * 3. 上游（服务 A）同样捕获到下游异常，两端 span 均标记失败。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/demo")
class DownstreamRouteTestController(
    @param:Value($$"${trace.demo.downstream-error-url:http://127.0.0.1:18092/api/demo/downstream-error}") private val downstreamErrorUrl: String
) {

    private val logger = LoggerFactory.getLogger(DownstreamRouteTestController::class.java)

    private val client: HttpClient = HttpClient(CIO) {
        // 非 2xx 响应抛出 ResponseException，让下游异常向上传播，A 端 span 也标记失败
        expectSuccess = true
        install(TraceKtorPlugin)
    }

    @GetMapping("/route-error")
    fun routeError(): Map<String, Any> {
        logger.info("服务 A 开始路由调用服务 B 异常接口")
        // 下游返回 500 时 Ktor 抛 ResponseException，此处不捕获，让异常传播
        val downstreamBody = runBlocking { client.get(downstreamErrorUrl).bodyAsText() }
        logger.info("服务 A 收到服务 B 响应，traceId={}", TraceContext.getTraceId())
        return mapOf(
            "service" to "service-a",
            "traceId" to (TraceContext.getTraceId() ?: ""),
            "downstreamBody" to downstreamBody
        )
    }
}
