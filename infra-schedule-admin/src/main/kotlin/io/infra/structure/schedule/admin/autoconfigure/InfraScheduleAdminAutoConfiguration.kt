package io.infra.structure.schedule.admin.autoconfigure

import io.infra.structure.schedule.admin.core.HttpScheduleCancelClient
import io.infra.structure.schedule.admin.core.HttpScheduleExecutorClientFactory
import io.infra.structure.schedule.admin.core.ScheduleDispatcher
import io.infra.structure.schedule.admin.service.ScheduleService
import io.infra.structure.schedule.admin.web.ScheduleAdminAccessInterceptor
import io.infra.structure.schedule.admin.web.ScheduleAdminApiExceptionHandler
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
import io.infra.structure.schedule.repository.ScheduleTriggerOutboxRepository
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

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
    fun infraScheduleWorkerExecutor(properties: InfraScheduleProperties): ExecutorService {
        val threads = properties.workerThreads.coerceIn(1, 256)
        return ThreadPoolExecutor(
            threads,
            threads,
            0L,
            TimeUnit.MILLISECONDS,
            LinkedBlockingQueue(properties.workerQueueCapacity.coerceIn(1, 100_000)),
            ThreadPoolExecutor.AbortPolicy()
        )
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["infraScheduleAttemptExecutor"])
    fun infraScheduleAttemptExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

    /**
     * Outbox 工作线程执行期间的短周期租约续约器。
     * 多线程隔离慢 SQL / 短暂锁等待，单个续约阻塞不得拖延所有在途 Outbox 的租约。
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["infraScheduleOutboxLeaseExecutor"])
    fun infraScheduleOutboxLeaseExecutor(properties: InfraScheduleProperties): ScheduledExecutorService =
        Executors.newScheduledThreadPool(properties.workerThreads.coerceIn(2, 32))

    /** 为到期扫描、慢速执行器探活和历史清理提供隔离的有界定时线程池。 */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["taskScheduler"])
    fun taskScheduler(properties: InfraScheduleProperties): TaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = properties.schedulerThreads.coerceIn(3, 16)
            setThreadNamePrefix("infra-schedule-timer-")
        }

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
        routeCursorRepository: RouteCursorRepository,
        jobRepository: ScheduleJobRepository
    ) = ExecutorRegistry(
        heartbeatRepository,
        properties.executor.heartbeatTimeoutMillis,
        clientFactory,
        routeStatRepository,
        routeCursorRepository,
        jobRepository::countByExecutorId
    )

    @Bean
    @ConditionalOnMissingBean
    fun scheduleService(
        jobRepository: ScheduleJobRepository,
        logRepository: ScheduleExecutionLogRepository,
        triggerOutboxRepository: ScheduleTriggerOutboxRepository,
        executorRegistry: ExecutorRegistry,
        @Qualifier("infraScheduleWorkerExecutor") workerExecutor: ExecutorService,
        @Qualifier("infraScheduleAttemptExecutor") attemptExecutor: ExecutorService,
        @Qualifier("infraScheduleOutboxLeaseExecutor") outboxLeaseExecutor: ScheduledExecutorService,
        taskTracker: ExecutorTaskTracker,
        cancelClient: HttpScheduleCancelClient,
        properties: InfraScheduleProperties
    ) = ScheduleService(
        jobRepository, logRepository, triggerOutboxRepository, executorRegistry, workerExecutor, attemptExecutor,
        taskTracker, cancelClient, properties.claimLeaseMillis, properties.schedulerId,
        properties.maxExecutionMillis, outboxLeaseExecutor
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

    /** 注册管理 API 的冲突错误转换，保证 starter 接入方也能获得可读 409 原因。 */
    @Bean
    @ConditionalOnMissingBean
    fun scheduleAdminApiExceptionHandler() = ScheduleAdminApiExceptionHandler()

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
