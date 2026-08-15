package io.infra.structure.trace.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 独立部署的分布式链路追踪后台。
 *
 * 提供 span 采集接口（POST /api/trace/spans）、链路查询接口（/api/trace/traces）
 * 以及 Thymeleaf 页面（/、/traces/{traceId}）。当前 span 数据存于进程内内存，
 * 后台自身的请求不会参与上报，避免自采自存的回环。
 */
@SpringBootApplication
class InfraTraceAdminApplication

/** 启动链路追踪后台。 */
fun main(args: Array<String>) {
    runApplication<InfraTraceAdminApplication>(*args)
}
