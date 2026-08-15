package io.infra.structure.trace.autoconfiguration

import io.infra.structure.trace.propagation.RestTemplateTraceInterceptor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * RestTemplate 出站链路传播自动配置。
 *
 * 单独成类并仅在 spring-web（ClientHttpRequestInterceptor）存在时生效，避免缺失该
 * 依赖的引用方在 Spring 内省自动配置类时触发 NoClassDefFoundError。
 *
 * @author sven
 */
@AutoConfiguration
@ConditionalOnClass(name = ["org.springframework.http.client.ClientHttpRequestInterceptor"])
class TraceRestTemplateAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun restTemplateTraceInterceptor(): RestTemplateTraceInterceptor = RestTemplateTraceInterceptor()
}
