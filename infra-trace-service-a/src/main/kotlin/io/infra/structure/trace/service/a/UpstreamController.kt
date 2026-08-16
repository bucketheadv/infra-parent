package io.infra.structure.trace.service.a

import io.infra.structure.trace.TraceContext
import io.infra.structure.trace.propagation.TraceKtorPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 示例服务 A 的上游接口，演示调用服务 B 并透传链路上下文。
 *
 * @author sven
 */
@RestController
@RequestMapping("/api/demo")
class UpstreamController(
    @param:Value("\${trace.demo.downstream-url:http://127.0.0.1:18092/api/demo/downstream}") private val downstreamUrl: String
) {

    private val logger = LoggerFactory.getLogger(UpstreamController::class.java)

    private val client: HttpClient = HttpClient(CIO) {
        // 1. 安装链路传播插件，出站请求自动携带 traceId/spanId
        install(TraceKtorPlugin)
    }

    @GetMapping("/upstream")
    fun upstream(): Map<String, Any> {
        val traceId = TraceContext.getTraceId()
        val spanId = TraceContext.getSpanId()
        logger.info("服务 A 收到上游请求，开始调用服务 B")
        // 2. 调用服务 B，Ktor 插件把当前 traceId/spanId 写入请求头
        val downstreamBody = try {
            runBlocking { client.get(downstreamUrl).bodyAsText() }
        } catch (exception: Exception) {
            logger.warn("调用服务 B 失败，exception={}", exception.message)
            "调用下游失败: ${exception.message}"
        }
        logger.info("服务 A 收到服务 B 响应，traceId={}", traceId)
        return mapOf(
            "service" to "service-a",
            "traceId" to (traceId ?: ""),
            "spanId" to (spanId ?: ""),
            "downstreamUrl" to downstreamUrl,
            "downstreamBody" to downstreamBody
        )
    }

    /** POST 演示接口：读取请求体后转发下游，用于演示入参采集。 */
    @PostMapping("/submit")
    fun submit(@RequestBody body: Map<String, Any>): Map<String, Any> {
        logger.info("服务 A 收到提交请求，body={}", body)
        val downstreamBody = try {
            runBlocking { client.get(downstreamUrl).bodyAsText() }
        } catch (exception: Exception) {
            "调用下游失败: ${exception.message}"
        }
        return mapOf(
            "service" to "service-a",
            "received" to body,
            "downstreamBody" to downstreamBody
        )
    }
}
