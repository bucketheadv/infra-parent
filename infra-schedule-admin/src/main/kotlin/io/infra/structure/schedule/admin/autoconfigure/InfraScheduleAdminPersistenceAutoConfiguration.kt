package io.infra.structure.schedule.admin.autoconfigure

import com.mybatisflex.spring.boot.MybatisFlexAutoConfiguration
import io.infra.structure.schedule.admin.persistence.FlexExecutorHeartbeatRepository
import io.infra.structure.schedule.admin.persistence.FlexRouteCursorRepository
import io.infra.structure.schedule.admin.persistence.FlexRouteNodeStatRepository
import io.infra.structure.schedule.admin.persistence.FlexScheduleExecutionLogRepository
import io.infra.structure.schedule.admin.persistence.FlexScheduleJobRepository
import io.infra.structure.schedule.admin.persistence.FlexScheduleTriggerOutboxRepository
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleExecutionLogMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleExecutorMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleExecutorRegistryMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleJobMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleTriggerOutboxMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleRouteCursorMapper
import io.infra.structure.schedule.admin.persistence.mapper.ScheduleRouteStatMapper
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.repository.ExecutorHeartbeatRepository
import io.infra.structure.schedule.repository.RouteCursorRepository
import io.infra.structure.schedule.repository.RouteNodeStatRepository
import io.infra.structure.schedule.repository.ScheduleExecutionLogRepository
import io.infra.structure.schedule.repository.ScheduleJobRepository
import io.infra.structure.schedule.repository.ScheduleTriggerOutboxRepository
import org.apache.ibatis.annotations.Mapper
import org.mybatis.spring.annotation.MapperScan
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureAfter
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.context.annotation.Bean

/**
 * 调度中心 MySQL 持久化；DataSource 缺失时应由 Spring JDBC 明确启动失败。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration::class, MybatisFlexAutoConfiguration::class)
@ConditionalOnProperty(prefix = "infra.schedule", name = ["enabled"], havingValue = "true")
@ConditionalOnProperty(prefix = "infra.schedule.management", name = ["enabled"], havingValue = "true")
@MapperScan(
    basePackages = ["io.infra.structure.schedule.admin.persistence.mapper"],
    annotationClass = Mapper::class
)
class InfraScheduleAdminPersistenceAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ScheduleJobRepository::class)
    fun scheduleJobRepository(
        mapper: ScheduleJobMapper,
        outboxMapper: ScheduleTriggerOutboxMapper
    ): ScheduleJobRepository = FlexScheduleJobRepository(mapper, outboxMapper)

    @Bean
    @ConditionalOnMissingBean(ScheduleExecutionLogRepository::class)
    fun scheduleExecutionLogRepository(mapper: ScheduleExecutionLogMapper): ScheduleExecutionLogRepository =
        FlexScheduleExecutionLogRepository(mapper)

    @Bean
    @ConditionalOnMissingBean(ScheduleTriggerOutboxRepository::class)
    fun scheduleTriggerOutboxRepository(mapper: ScheduleTriggerOutboxMapper): ScheduleTriggerOutboxRepository =
        FlexScheduleTriggerOutboxRepository(mapper)

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

    @Bean
    @ConditionalOnMissingBean(RouteNodeStatRepository::class)
    fun routeNodeStatRepository(mapper: ScheduleRouteStatMapper): RouteNodeStatRepository =
        FlexRouteNodeStatRepository(mapper)

    @Bean
    @ConditionalOnMissingBean(RouteCursorRepository::class)
    fun routeCursorRepository(mapper: ScheduleRouteCursorMapper): RouteCursorRepository =
        FlexRouteCursorRepository(mapper)
}
