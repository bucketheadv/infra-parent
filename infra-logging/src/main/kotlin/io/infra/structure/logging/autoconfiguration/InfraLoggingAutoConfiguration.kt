package io.infra.structure.logging.autoconfiguration

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/**
 * Logging 模块自动配置
 * 确保 PolyLogAspect 被正确扫描和加载，并启用 AOP
 *
 * @author sven
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = ["io.infra.structure.logging"])
class InfraLoggingAutoConfiguration
