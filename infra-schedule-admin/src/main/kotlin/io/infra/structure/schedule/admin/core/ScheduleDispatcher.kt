package io.infra.structure.schedule.admin.core

import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.properties.InfraScheduleProperties
import io.infra.structure.schedule.admin.service.ScheduleService
import org.springframework.scheduling.annotation.Scheduled

/** 定时扫描数据库中的到期任务、回收僵尸日志并分批清理历史记录。 */
class ScheduleDispatcher(
    private val scheduleService: ScheduleService,
    private val executorRegistry: ExecutorRegistry,
    private val properties: InfraScheduleProperties
) {
    @Scheduled(fixedDelayString = $$"${infra.schedule.scan-interval-millis:1000}")
    /** 执行到期领取和 Outbox 投递；与慢速探活、清理任务分开调度，避免相互阻塞。 */
    fun dispatchDueAndOutbox() {
        if (properties.executor.enabled) {
            executorRegistry.heartbeat(
                properties.executor.group,
                properties.executor.name ?: properties.executor.group,
                properties.executor.address
            )
        }
        scheduleService.dispatchDueJobs(properties.dispatchBatchSize, properties.dispatchMaxPages)
        scheduleService.dispatchTriggerOutbox(properties.dispatchBatchSize, properties.dispatchMaxPages)
    }

    /** 探测并修复已确认不存在的僵尸执行日志。 */
    @Scheduled(fixedDelayString = $$"${infra.schedule.scan-interval-millis:1000}")
    fun reapStaleLogs() {
        scheduleService.reapStaleRunningLogs(
            properties.staleRunningLogMillis,
            properties.staleRunningLogBatchSize
        )
    }

    /** 分批删除历史终态日志和已完成 Outbox，避免清理长事务影响调度。 */
    @Scheduled(fixedDelayString = $$"${infra.schedule.scan-interval-millis:1000}")
    fun cleanupHistory() {
        scheduleService.cleanupFinishedLogs(
            properties.executionLogRetentionMillis,
            properties.executionLogCleanupBatchSize
        )
        scheduleService.cleanupCompletedOutbox(
            properties.triggerOutboxRetentionMillis,
            properties.triggerOutboxCleanupBatchSize
        )
    }
}
