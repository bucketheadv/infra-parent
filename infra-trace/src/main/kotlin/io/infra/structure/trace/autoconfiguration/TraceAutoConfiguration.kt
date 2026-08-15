package io.infra.structure.trace.autoconfiguration

import io.infra.structure.trace.TraceContext
import io.infra.structure.trace.filter.TraceFilter
import io.infra.structure.trace.propagation.OkHttpTraceInterceptor
import io.infra.structure.trace.propagation.RestTemplateTraceInterceptor
import io.infra.structure.trace.propagation.WebClientTraceFilter
import io.infra.structure.trace.properties.TraceProperties
import io.infra.structure.trace.task.TraceTaskDecorator
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.task.TaskDecorator

/**
 * 分布式日志追踪自动配置。
 *
 * 引用本模块后默认生效：Servlet 应用中自动注册 [TraceFilter]，为每个 HTTP 请求
 * 生成/透传 traceId 并写入 MDC；同时提供 MDC 异步传播的 [TaskDecorator]、以及
 * RestTemplate / WebClient 的出站传播组件。
 *
 * @author sven
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "infra.trace", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TraceProperties::class)
class TraceAutoConfiguration {

    /** 注册 Servlet 过滤链，优先级最高，保证最先生成 traceId/spanId。 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    fun traceFilterRegistration(properties: TraceProperties): FilterRegistrationBean<TraceFilter> {
        TraceContext.configure(properties.headerName, properties.mdcKey, properties.spanHeaderName, properties.spanMdcKey)
        return FilterRegistrationBean<TraceFilter>().apply {
            setFilter(TraceFilter(properties))
            addUrlPatterns("/*")
            order = Ordered.HIGHEST_PRECEDENCE
        }
    }

    /** 异步任务 MDC 传播，引用方未自定义 TaskDecorator 时生效。 */
    @Bean
    @ConditionalOnMissingBean
    fun traceTaskDecorator(): TaskDecorator = TraceTaskDecorator()

    /** RestTemplate 出站传播组件，供引用方注册到自己的 RestTemplate。 */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.http.client.ClientHttpRequestInterceptor"])
    @ConditionalOnMissingBean
    fun restTemplateTraceInterceptor(): RestTemplateTraceInterceptor = RestTemplateTraceInterceptor()

    /** WebClient 出站传播组件，供引用方注册到自己的 WebClient。 */
    @Bean
    @ConditionalOnClass(name = ["org.springframework.web.reactive.function.client.ExchangeFilterFunction"])
    @ConditionalOnMissingBean
    fun webClientTraceFilter(): WebClientTraceFilter = WebClientTraceFilter()

    /** OkHttp 出站传播组件，供引用方注册到自己的 OkHttpClient。 */
    @Bean
    @ConditionalOnClass(name = ["okhttp3.Interceptor"])
    @ConditionalOnMissingBean
    fun okHttpTraceInterceptor(): OkHttpTraceInterceptor = OkHttpTraceInterceptor()
}
