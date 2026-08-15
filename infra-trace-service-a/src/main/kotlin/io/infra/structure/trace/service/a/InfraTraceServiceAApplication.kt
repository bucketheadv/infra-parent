package io.infra.structure.trace.service.a

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 链路追踪示例服务 A（上游）。
 *
 * 提供 /api/demo/upstream 接口，通过 Ktor HttpClient（安装 [TraceKtorPlugin]）调用
 * 示例服务 B 的下游接口，演示 traceId/spanId 在跨服务调用中的透传，并把两端 span
 * 上报到 infra-trace-admin（18090）聚合展示。
 */
@SpringBootApplication
class InfraTraceServiceAApplication

/** 启动示例服务 A。 */
fun main(args: Array<String>) {
    runApplication<InfraTraceServiceAApplication>(*args)
}
