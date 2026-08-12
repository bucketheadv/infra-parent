package io.infra.structure.schedule.autoconfigure

import io.infra.structure.schedule.api.ScheduleJobHandler
import io.infra.structure.schedule.api.ScheduleLogHelper
import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.ExecutorHeartbeatReporter
import io.infra.structure.schedule.core.ExecutorTaskTracker
import io.infra.structure.schedule.core.HandlerRegistry
import io.infra.structure.schedule.core.LocalScheduleExecutor
import io.infra.structure.schedule.core.ScheduleLogReporter
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.InMemoryExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.InMemoryRouteCursorRepository
import io.infra.structure.schedule.repository.InMemoryRouteNodeStatRepository
import io.infra.structure.schedule.repository.InMemoryScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.InMemoryScheduleJobRepository
import io.infra.structure.schedule.repository.RouteCursorRepository
import io.infra.structure.schedule.repository.RouteNodeStatRepository
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import io.infra.structure.schedule.web.ScheduleExecutorController
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/** 执行器 starter：Handler、HTTP 执行端点、心跳与日志上报。调度中心见 infra-schedule-admin。 */
@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(InfraScheduleProperties::class)
@ConditionalOnProperty(prefix = "infra.schedule", name = ["enabled"], havingValue = "true")
class InfraScheduleAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun handlerRegistry(handlers: List<ScheduleJobHandler>) = HandlerRegistry(handlers)

    @Bean
    @ConditionalOnMissingBean
    fun scheduleLogReporter(
        properties: InfraScheduleProperties,
        logRepository: ScheduleExecutionLogRepository
    ): ScheduleLogReporter {
        val reporter = ScheduleLogReporter(properties, logRepository)
        ScheduleLogHelper.install(reporter)
        return reporter
    }

    @Bean
    @ConditionalOnMissingBean
    fun executorTaskTracker(
        handlerRegistry: HandlerRegistry,
        scheduleLogReporter: ScheduleLogReporter
    ) = ExecutorTaskTracker(
        handlerRegistry = handlerRegistry,
        onExecutionStarted = { context ->
            val logId = context.logId ?: return@ExecutorTaskTracker
            scheduleLogReporter.markStarted(logId, "${context.handler} 执行中")
        },
        onExecutionFinished = { context, result, durationMillis ->
            val logId = context.logId ?: return@ExecutorTaskTracker
            scheduleLogReporter.markFinished(logId, result, durationMillis)
        }
    )

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "infra.schedule.management",
        name = ["enabled"],
        havingValue = "false",
        matchIfMissing = true
    )
    fun executorRegistry(
        heartbeatRepository: ExecutorHeartbeatRepository,
        properties: InfraScheduleProperties,
        routeStatRepository: RouteNodeStatRepository,
        routeCursorRepository: RouteCursorRepository
    ) = ExecutorRegistry(
        heartbeatRepository,
        properties.executor.heartbeatTimeoutMillis,
        clientFactory = null,
        routeStatRepository,
        routeCursorRepository
    )

    @Bean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun localScheduleExecutor(
        taskTracker: ExecutorTaskTracker,
        executorRegistry: ExecutorRegistry,
        properties: InfraScheduleProperties
    ): LocalScheduleExecutor {
        val executor = LocalScheduleExecutor(
            properties.executor.name ?: properties.executor.group,
            properties.executor.group,
            taskTracker
        )
        executorRegistry.register(executor)
        return executor
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true")
    fun scheduleExecutorController(
        taskTracker: ExecutorTaskTracker,
        properties: InfraScheduleProperties
    ) = ScheduleExecutorController(taskTracker, properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun executorHeartbeatReporter(
        properties: InfraScheduleProperties,
        executorRegistry: ExecutorRegistry
    ) = ExecutorHeartbeatReporter(properties, executorRegistry)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
        prefix = "infra.schedule.management",
        name = ["enabled"],
        havingValue = "false",
        matchIfMissing = true
    )
    class LocalPersistenceConfiguration {
        @Bean
        @ConditionalOnMissingBean(RouteNodeStatRepository::class)
        fun routeNodeStatRepository(): RouteNodeStatRepository = InMemoryRouteNodeStatRepository()

        @Bean
        @ConditionalOnMissingBean(RouteCursorRepository::class)
        fun routeCursorRepository(): RouteCursorRepository = InMemoryRouteCursorRepository()

        @Bean
        @ConditionalOnMissingBean(ScheduleJobRepository::class)
        fun scheduleJobRepository(): ScheduleJobRepository = InMemoryScheduleJobRepository()

        @Bean
        @ConditionalOnMissingBean(ScheduleExecutionLogRepository::class)
        fun scheduleExecutionLogRepository(): ScheduleExecutionLogRepository = InMemoryScheduleExecutionLogRepository()

        @Bean
        @ConditionalOnMissingBean(ExecutorHeartbeatRepository::class)
        fun executorHeartbeatRepository(properties: InfraScheduleProperties): ExecutorHeartbeatRepository =
            InMemoryExecutorHeartbeatRepository(properties.executor.heartbeatTimeoutMillis)
    }
}
