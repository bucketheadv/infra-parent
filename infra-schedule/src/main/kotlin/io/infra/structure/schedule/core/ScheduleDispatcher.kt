package io.infra.structure.schedule.core

import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.service.ScheduleService
import org.springframework.scheduling.annotation.Scheduled

/** 定时刷新本地执行器心跳并扫描数据库中的到期任务。 */
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
        if (properties.dispatcherEnabled) {
            scheduleService.reapStaleRunningLogs(
                properties.staleRunningLogMillis,
                properties.staleRunningLogBatchSize
            )
            scheduleService.dispatchDueJobs(properties.dispatchBatchSize, properties.dispatchMaxPages)
        }
    }
}
