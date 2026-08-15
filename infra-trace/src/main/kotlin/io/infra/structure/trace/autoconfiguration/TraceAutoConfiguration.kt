package io.infra.structure.trace.autoconfiguration

import ch.qos.logback.classic.LoggerContext
import io.infra.structure.trace.TraceContext
import io.infra.structure.trace.filter.TraceFilter
import io.infra.structure.trace.logging.MemoryLogAppender
import io.infra.structure.trace.properties.TraceProperties
import io.infra.structure.trace.report.HttpLogReporter
import io.infra.structure.trace.report.HttpTraceReporter
import io.infra.structure.trace.report.LogReporter
import io.infra.structure.trace.report.TraceReporter
import io.infra.structure.trace.task.TraceTaskDecorator
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.core.env.Environment
import org.springframework.core.task.TaskDecorator

/**
 * 分布式日志追踪自动配置。
 *
 * 引用本模块后默认生效：Servlet 应用中自动注册 [TraceFilter]，为每个 HTTP 请求
 * 生成/透传 traceId/spanId 并写入 MDC；同时提供 MDC 异步传播的 [TaskDecorator]，
 * 以及可选的 span 上报能力（`infra.trace.report.*`，默认关闭）。
 *
 * 各出站传播组件（RestTemplate / WebClient / OkHttp）由于依赖引用方自行引入的客户端，
 * 拆分在独立的自动配置类中，仅在对应客户端类存在时生效，避免本类因缺失类而被
 * Spring 全量内省。
 *
 * @author sven
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "infra.trace", name = ["enabled"], havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TraceProperties::class)
class TraceAutoConfiguration {

    /** 注册 Servlet 过滤链，优先级最高，保证最先生成 traceId/spanId；同时接入 span 上报。 */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    fun traceFilterRegistration(
        properties: TraceProperties,
        environment: Environment,
        reporterProvider: ObjectProvider<TraceReporter>,
        logReporterProvider: ObjectProvider<LogReporter>
    ): FilterRegistrationBean<TraceFilter> {
        TraceContext.configure(properties.headerName, properties.mdcKey, properties.spanHeaderName, properties.spanMdcKey)
        val serviceName = environment.getProperty("spring.application.name", "")
        return FilterRegistrationBean<TraceFilter>().apply {
            setFilter(TraceFilter(properties, reporterProvider.ifAvailable, serviceName, logReporterProvider.ifAvailable))
            addUrlPatterns("/*")
            order = Ordered.HIGHEST_PRECEDENCE
        }
    }

    /** 开启上报（infra.trace.report.enabled=true 且配置采集地址）时创建 span 上报器。 */
    @Bean
    @ConditionalOnProperty(prefix = "infra.trace.report", name = ["enabled"], havingValue = "true")
    @ConditionalOnProperty(name = ["infra.trace.report.url"])
    @ConditionalOnMissingBean
    fun traceReporter(properties: TraceProperties): TraceReporter =
        HttpTraceReporter(properties.report.url, properties.report.timeoutMillis)

    /** 配置日志采集地址时创建日志上报器，随 span 一并上报链路日志。 */
    @Bean
    @ConditionalOnProperty(prefix = "infra.trace.report", name = ["enabled"], havingValue = "true")
    @ConditionalOnProperty(name = ["infra.trace.report.logs-url"])
    @ConditionalOnMissingBean
    fun logReporter(properties: TraceProperties): LogReporter =
        HttpLogReporter(properties.report.logsUrl, properties.report.timeoutMillis)

    /** 异步任务 MDC 传播，引用方未自定义 TaskDecorator 时生效。 */
    @Bean
    @ConditionalOnMissingBean
    fun traceTaskDecorator(): TaskDecorator = TraceTaskDecorator()

    /**
     * 内存日志收集器，按 traceId 索引日志供链路详情页展示。
     *
     * 返回值由 Spring 容器持有，防止 GC 回收 appender 实例；
     * 同时调用 [MemoryLogAppender.register] 挂载到 Logback ROOT logger。
     */
    @Bean(destroyMethod = "")
    fun memoryLogAppender(environment: Environment): MemoryLogAppender {
        val ctx = LoggerFactory.getILoggerFactory() as? LoggerContext
        MemoryLogAppender.register(ctx, environment.getProperty("spring.application.name", ""))
        return MemoryLogAppender()
    }
}
