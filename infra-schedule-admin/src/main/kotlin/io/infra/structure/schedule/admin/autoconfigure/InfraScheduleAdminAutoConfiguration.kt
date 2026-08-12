package io.infra.structure.schedule.admin.autoconfigure

import io.infra.structure.schedule.admin.core.HttpScheduleCancelClient
import io.infra.structure.schedule.admin.core.HttpScheduleExecutorClientFactory
import io.infra.structure.schedule.admin.core.ScheduleDispatcher
import io.infra.structure.schedule.admin.service.ScheduleService
import io.infra.structure.schedule.admin.web.ScheduleAdminAccessInterceptor
import io.infra.structure.schedule.admin.web.ScheduleAdminController
import io.infra.structure.schedule.admin.web.ScheduleAdminWebConfigurer
import io.infra.structure.schedule.autoconfigure.InfraScheduleAutoConfiguration
import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.ExecutorTaskTracker
import io.infra.structure.schedule.core.ScheduleExecutorClientFactory
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.RouteCursorRepository
import io.infra.structure.schedule.repository.RouteNodeStatRepository
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * 调度中心自动配置：任务扫描、管理 REST、远程执行器客户端。
 */
@AutoConfiguration(
    after = [
        InfraScheduleAutoConfiguration::class,
        InfraScheduleAdminPersistenceAutoConfiguration::class
    ]
)
@EnableScheduling
@ConditionalOnProperty(prefix = "infra.schedule", name = ["enabled"], havingValue = "true")
@ConditionalOnProperty(prefix = "infra.schedule.management", name = ["enabled"], havingValue = "true")
class InfraScheduleAdminAutoConfiguration {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["infraScheduleWorkerExecutor"])
    fun infraScheduleWorkerExecutor(): ExecutorService =
        Executors.newVirtualThreadPerTaskExecutor()

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["infraScheduleAttemptExecutor"])
    fun infraScheduleAttemptExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    @Bean
    @ConditionalOnMissingBean
    fun httpScheduleCancelClient(properties: InfraScheduleProperties) = HttpScheduleCancelClient(
        properties.executor.accessToken,
        properties.executor.authEnabled,
        properties.executor.connectTimeoutMillis,
        minOf(properties.executor.readTimeoutMillis, 5_000L)
    )

    @Bean
    @ConditionalOnMissingBean
    fun scheduleExecutorClientFactory(properties: InfraScheduleProperties): ScheduleExecutorClientFactory =
        HttpScheduleExecutorClientFactory(
            properties.executor.accessToken,
            properties.executor.authEnabled,
            properties.executor.connectTimeoutMillis,
            properties.executor.readTimeoutMillis
        )

    @Bean
    @ConditionalOnMissingBean
    fun executorRegistry(
        heartbeatRepository: ExecutorHeartbeatRepository,
        clientFactory: ScheduleExecutorClientFactory,
        properties: InfraScheduleProperties,
        routeStatRepository: RouteNodeStatRepository,
        routeCursorRepository: RouteCursorRepository
    ) = ExecutorRegistry(
        heartbeatRepository,
        properties.executor.heartbeatTimeoutMillis,
        clientFactory,
        routeStatRepository,
        routeCursorRepository
    )

    @Bean
    @ConditionalOnMissingBean
    fun scheduleService(
        jobRepository: ScheduleJobRepository,
        logRepository: ScheduleExecutionLogRepository,
        executorRegistry: ExecutorRegistry,
        @Qualifier("infraScheduleWorkerExecutor") workerExecutor: ExecutorService,
        @Qualifier("infraScheduleAttemptExecutor") attemptExecutor: ExecutorService,
        taskTracker: ExecutorTaskTracker,
        cancelClient: HttpScheduleCancelClient,
        properties: InfraScheduleProperties
    ) = ScheduleService(
        jobRepository, logRepository, executorRegistry, workerExecutor, attemptExecutor,
        taskTracker, cancelClient, properties.claimLeaseMillis, properties.schedulerId
    )

    @Bean
    @ConditionalOnMissingBean
    fun scheduleDispatcher(
        scheduleService: ScheduleService,
        executorRegistry: ExecutorRegistry,
        properties: InfraScheduleProperties
    ) = ScheduleDispatcher(scheduleService, executorRegistry, properties)

    @Bean
    @ConditionalOnMissingBean
    fun scheduleAdminController(
        scheduleService: ScheduleService,
        executorRegistry: ExecutorRegistry,
        properties: InfraScheduleProperties
    ) = ScheduleAdminController(scheduleService, executorRegistry, properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.management", name = ["auth-enabled"], havingValue = "true")
    fun scheduleAdminAccessInterceptor(properties: InfraScheduleProperties) = ScheduleAdminAccessInterceptor(properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.management", name = ["auth-enabled"], havingValue = "true")
    fun scheduleAdminWebConfigurer(accessInterceptor: ScheduleAdminAccessInterceptor) =
        ScheduleAdminWebConfigurer(accessInterceptor)
}
