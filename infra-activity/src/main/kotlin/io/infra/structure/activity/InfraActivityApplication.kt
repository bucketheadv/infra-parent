package io.infra.structure.activity

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * 活动业务模块的启动类。
 *
 * 模块依靠 infra-sso 自动配置启用 OAuth2 Client，因此不需要自行声明安全过滤器链。
 */
@SpringBootApplication
@EnableScheduling
class InfraActivityApplication

/** 启动活动业务模块。 */
fun main(args: Array<String>) {
    runApplication<InfraActivityApplication>(*args)
}
