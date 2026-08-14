package io.infra.structure.rocketmq.admin

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/**
 * 独立部署的 RocketMQ 消息管理后台。
 *
 * 管理页面与 REST 由 infra-rocketmq-admin 自动配置提供（/infra/rocketmq）。
 */
@SpringBootApplication
class InfraRocketMQAdminApplication

/** 启动 RocketMQ 管理后台。 */
fun main(args: Array<String>) {
    runApplication<InfraRocketMQAdminApplication>(*args)
}