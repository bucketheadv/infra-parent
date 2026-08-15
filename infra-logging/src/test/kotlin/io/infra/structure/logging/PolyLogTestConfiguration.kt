package io.infra.structure.logging

import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

/**
 * PolyLog AOP 集成测试配置：启用 AspectJ 自动代理并扫描切面与测试组件。
 */
@Configuration
@EnableAspectJAutoProxy
@ComponentScan(basePackages = ["io.infra.structure.logging"])
class PolyLogTestConfiguration
