package io.infra.structure.trace.autoconfiguration

import io.infra.structure.trace.propagation.OkHttpTraceInterceptor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean

/**
 * OkHttp 出站链路传播自动配置。
 *
 * 单独成类并仅在 okhttp3.Interceptor 存在时生效，避免缺失该依赖的引用方在 Spring
 * 内省自动配置类时触发 NoClassDefFoundError。
 *
 * @author sven
 */
@AutoConfiguration
@ConditionalOnClass(name = ["okhttp3.Interceptor"])
class TraceOkHttpAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    fun okHttpTraceInterceptor(): OkHttpTraceInterceptor = OkHttpTraceInterceptor()
}
