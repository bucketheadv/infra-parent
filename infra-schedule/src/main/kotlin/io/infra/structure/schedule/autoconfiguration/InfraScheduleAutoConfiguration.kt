package io.infra.structure.schedule.autoconfigure

import io.infra.structure.schedule.api.ScheduleJobHandler
import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.core.ExecutorHeartbeatReporter
import io.infra.structure.schedule.core.HandlerRegistry
import io.infra.structure.schedule.core.HttpScheduleExecutorClientFactory
import io.infra.structure.schedule.core.LocalScheduleExecutor
import io.infra.structure.schedule.core.ScheduleExecutorClientFactory
import io.infra.structure.schedule.core.ScheduleDispatcher
import io.infra.structure.schedule.persistence.FlexExecutorHeartbeatRepository
import io.infra.structure.schedule.persistence.FlexScheduleExecutionLogRepository
import io.infra.structure.schedule.persistence.FlexScheduleJobRepository
import io.infra.structure.schedule.persistence.mapper.ScheduleExecutionLogMapper
import io.infra.structure.schedule.persistence.mapper.ScheduleExecutorMapper
import io.infra.structure.schedule.persistence.mapper.ScheduleExecutorRegistryMapper
import io.infra.structure.schedule.persistence.mapper.ScheduleJobMapper
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.InMemoryExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.InMemoryScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.InMemoryScheduleJobRepository
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import io.infra.structure.schedule.service.ScheduleService
import io.infra.structure.schedule.web.ScheduleAdminController
import io.infra.structure.schedule.web.ScheduleAdminAccessInterceptor
import io.infra.structure.schedule.web.ScheduleAdminWebConfigurer
import io.infra.structure.schedule.web.ScheduleExecutorController
import org.apache.ibatis.annotations.Mapper
import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.EnableScheduling
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.sql.DataSource

@AutoConfiguration
@EnableScheduling
@EnableConfigurationProperties(InfraScheduleProperties::class)
@ConditionalOnProperty(prefix = "infra.schedule", name = ["enabled"], havingValue = "true")
class InfraScheduleAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun handlerRegistry(handlers: List<ScheduleJobHandler>) = HandlerRegistry(handlers)

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["infraScheduleWorkerExecutor"])
    fun infraScheduleWorkerExecutor(properties: InfraScheduleProperties): ExecutorService =
        Executors.newFixedThreadPool(properties.workerThreads.coerceAtLeast(1))

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(name = ["infraScheduleAttemptExecutor"])
    fun infraScheduleAttemptExecutor(): ExecutorService = Executors.newVirtualThreadPerTaskExecutor()

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
        properties: InfraScheduleProperties
    ) = ExecutorRegistry(heartbeatRepository, properties.executor.heartbeatTimeoutMillis, clientFactory)

    @Bean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun localScheduleExecutor(
        handlerRegistry: HandlerRegistry,
        executorRegistry: ExecutorRegistry,
        properties: InfraScheduleProperties
    ): LocalScheduleExecutor {
        val executor = LocalScheduleExecutor(
            properties.executor.name ?: properties.executor.group,
            properties.executor.group,
            handlerRegistry
        )
        executorRegistry.register(executor)
        return executor
    }

    @Bean
    @ConditionalOnMissingBean
    fun scheduleService(
        jobRepository: ScheduleJobRepository,
        logRepository: ScheduleExecutionLogRepository,
        executorRegistry: ExecutorRegistry,
        @Qualifier("infraScheduleWorkerExecutor") workerExecutor: ExecutorService,
        @Qualifier("infraScheduleAttemptExecutor") attemptExecutor: ExecutorService,
        properties: InfraScheduleProperties
    ) = ScheduleService(
        jobRepository, logRepository, executorRegistry, workerExecutor, attemptExecutor,
        properties.claimLeaseMillis, properties.schedulerId
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
    @ConditionalOnProperty(prefix = "infra.schedule.management", name = ["enabled"], havingValue = "true")
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

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true")
    fun scheduleExecutorController(
        handlerRegistry: HandlerRegistry,
        properties: InfraScheduleProperties
    ) = ScheduleExecutorController(handlerRegistry, properties)

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "infra.schedule.executor", name = ["enabled"], havingValue = "true", matchIfMissing = true)
    fun executorHeartbeatReporter(
        properties: InfraScheduleProperties,
        executorRegistry: ExecutorRegistry
    ) = ExecutorHeartbeatReporter(properties, executorRegistry)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource::class)
    @MapperScan(basePackages = ["io.infra.structure.schedule.persistence.mapper"], annotationClass = Mapper::class)
    class FlexPersistenceConfiguration {
        @Bean
        @ConditionalOnMissingBean(ScheduleJobRepository::class)
        fun scheduleJobRepository(mapper: ScheduleJobMapper): ScheduleJobRepository = FlexScheduleJobRepository(mapper)

        @Bean
        @ConditionalOnMissingBean(ScheduleExecutionLogRepository::class)
        fun scheduleExecutionLogRepository(mapper: ScheduleExecutionLogMapper): ScheduleExecutionLogRepository =
            FlexScheduleExecutionLogRepository(mapper)

        @Bean
        @ConditionalOnMissingBean(ExecutorHeartbeatRepository::class)
        fun executorHeartbeatRepository(
            mapper: ScheduleExecutorMapper,
            registryMapper: ScheduleExecutorRegistryMapper,
            properties: InfraScheduleProperties
        ): ExecutorHeartbeatRepository = FlexExecutorHeartbeatRepository(
            mapper,
            registryMapper,
            properties.executor.heartbeatTimeoutMillis
        )
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(DataSource::class)
    class LocalPersistenceConfiguration {
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
