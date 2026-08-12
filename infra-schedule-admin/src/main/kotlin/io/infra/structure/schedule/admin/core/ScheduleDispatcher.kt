package io.infra.structure.schedule.admin.core

import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.admin.service.ScheduleService
import org.springframework.scheduling.annotation.Scheduled

/** 定时扫描数据库中的到期任务并回收僵尸运行中日志。 */
class ScheduleDispatcher(
    private val scheduleService: ScheduleService,
    private val executorRegistry: ExecutorRegistry,
    private val properties: InfraScheduleProperties
) {
    @Scheduled(fixedDelayString = $$"${infra.schedule.scan-interval-millis:1000}")
    /** 执行一次调度扫描；领取语义由 [ScheduleService] 和仓储实现共同保证。 */
    fun dispatch() {
        if (properties.executor.enabled) {
            executorRegistry.heartbeat(
                properties.executor.group,
                properties.executor.name ?: properties.executor.group,
                properties.executor.address
            )
        }
        scheduleService.reapStaleRunningLogs(
            properties.staleRunningLogMillis,
            properties.staleRunningLogBatchSize
        )
        scheduleService.dispatchDueJobs(properties.dispatchBatchSize, properties.dispatchMaxPages)
    }
}
