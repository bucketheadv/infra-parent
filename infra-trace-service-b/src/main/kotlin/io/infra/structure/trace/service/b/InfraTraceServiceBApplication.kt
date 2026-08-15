package io.infra.structure.trace.service.b

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 链路追踪示例服务 B（下游）。
 *
 * 提供 /api/demo/downstream 接口，被示例服务 A 调用；通过 [TraceContext] 回显本次
 * 链路透传的 traceId/spanId，并把本服务 span 上报到 infra-trace-admin（18090）。
 */
@SpringBootApplication
class InfraTraceServiceBApplication

/** 启动示例服务 B。 */
fun main(args: Array<String>) {
    runApplication<InfraTraceServiceBApplication>(*args)
}
