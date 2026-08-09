package io.infra.structure.activity.admin.task

import io.infra.structure.activity.admin.service.ActivityTaskService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** 定时扫描待执行活动任务的分布式调度入口。 */
@Component
class ActivityTaskScheduler(
    private val activityTaskService: ActivityTaskService
) {

    /** 独立刷新当前实例心跳，避免长耗时任务阻塞后被其他实例误判为已失联。 */
    @Scheduled(fixedRate = 5_000)
    fun reportInstanceHeartbeat() {
        activityTaskService.heartbeatSchedulerInstance()
    }

    /**
     * 多实例均可扫描，到期任务会通过数据库租约仅被一个实例抢占。
     * 默认每秒扫描一次；可通过 infra.activity.task.scan_interval_ms 调整。
     */
    @Scheduled(fixedRateString = $$"${infra.activity.task.scan_interval_ms:1000}")
    fun dispatchDueTasks() {
        activityTaskService.heartbeatSchedulerInstance()
        activityTaskService.recoverOrphanedRunningTasks()
        activityTaskService.executeDueTasks()
    }
}
