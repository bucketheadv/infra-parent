package io.infra.structure.schedule.autoconfigure

import io.infra.structure.schedule.api.ScheduleJobHandler
import io.infra.structure.schedule.api.ScheduleLogHelper
import io.infra.structure.schedule.core.ExecutorHeartbeatReporter
import io.infra.structure.schedule.core.ExecutorTaskTracker
import io.infra.structure.schedule.core.HandlerRegistry
import io.infra.structure.schedule.core.LocalScheduleExecutor
import io.infra.structure.schedule.core.ScheduleLogReporter
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.web.ScheduleExecutorController
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
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
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun scheduleLogReporter(properties: InfraScheduleProperties): ScheduleLogReporter {
        val reporter = ScheduleLogReporter(properties)
        ScheduleLogHelper.install(reporter)
        return reporter
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun executorTaskTracker(
        handlerRegistry: HandlerRegistry,
        scheduleLogReporter: ScheduleLogReporter,
        properties: InfraScheduleProperties
    ) = ExecutorTaskTracker(
        handlerRegistry = handlerRegistry,
        coverEarlyWaitMillis = properties.coverEarlyWaitMillis,
        onExecutionStarted = { context ->
            val logId = context.logId ?: return@ExecutorTaskTracker
            scheduleLogReporter.markStarted(logId, "${context.handler} 执行中")
        },
        onExecutionFinished = { context, result, durationMillis ->
            val logId = context.logId ?: return@ExecutorTaskTracker
            scheduleLogReporter.markFinished(logId, result, durationMillis)
        }
    )

    /**
     * 管理端关闭本地执行器时仍提供只读协调器，供取消/探活本机历史日志使用。
     * 该实例没有日志回调，不会要求管理端配置 executor.admin-address。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "false")
    fun disabledExecutorTaskTracker(handlerRegistry: HandlerRegistry, properties: InfraScheduleProperties) =
        ExecutorTaskTracker(handlerRegistry, properties.coverEarlyWaitMillis)

    @Bean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun localScheduleExecutor(
        taskTracker: ExecutorTaskTracker,
        properties: InfraScheduleProperties
    ): LocalScheduleExecutor {
        val executor = LocalScheduleExecutor(
            properties.executor.name ?: properties.executor.group,
            properties.executor.group,
            taskTracker
        )
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
        properties: InfraScheduleProperties
    ) = ExecutorHeartbeatReporter(properties)
}
