package io.infra.structure.sso.login

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

/** 登录中心应用入口，组件扫描范围覆盖认证、协议配置、持久化和页面控制器。 */
@SpringBootApplication
class SsoLoginApplication

/** 启动 Spring Boot 登录中心。 */
fun main(args: Array<String>) {
    runApplication<SsoLoginApplication>(*args)
}
