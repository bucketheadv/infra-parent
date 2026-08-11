package io.infra.structure.schedule.admin.controller

import io.infra.structure.schedule.core.ExecutorRegistry
import io.infra.structure.schedule.model.ExecutorStatus
import io.infra.structure.schedule.model.JobStatus
import io.infra.structure.schedule.service.ScheduleService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping

/** 管理后台页面路径，避免控制器内散落 URL 字面量。 */
object ScheduleAdminPagePaths {
    /** 任务管理首页路径。 */
    const val ROOT = "/"
    /** 任务管理仪表盘别名路径。 */
    const val DASHBOARD = "/dashboard"
    /** 执行器管理独立路由。 */
    const val EXECUTORS = "/executors"
    /** 执行日志查询独立路由。 */
    const val LOGS = "/logs"
}

/** 渲染调度管理后台的 Thymeleaf 页面。 */
@Controller
class ScheduleAdminPageController(
    private val scheduleService: ScheduleService,
    private val executorRegistry: ExecutorRegistry
) {
    /** 任务管理页：概览指标与任务列表。 */
    @GetMapping(value = [ScheduleAdminPagePaths.ROOT, ScheduleAdminPagePaths.DASHBOARD])
    fun dashboard(model: Model): String {
        val jobs = scheduleService.jobs()
        val now = System.currentTimeMillis()
        model.addAttribute("page", "jobs")
        model.addAttribute("jobCount", jobs.size)
        model.addAttribute("enabledJobCount", jobs.count { it.status == JobStatus.ENABLED })
        model.addAttribute("disabledJobCount", jobs.count { it.status == JobStatus.DISABLED })
        model.addAttribute("executorCount", executorRegistry.registeredExecutors().count {
            it.status == ExecutorStatus.ENABLED && now - it.lastHeartbeatTime <= 30_000
        })
        return "schedule-dashboard"
    }

    /** 执行器管理独立页面。 */
    @GetMapping(ScheduleAdminPagePaths.EXECUTORS)
    fun executors(model: Model): String {
        val executors = executorRegistry.registeredExecutors()
        val now = System.currentTimeMillis()
        model.addAttribute("page", "executors")
        model.addAttribute("executorCount", executors.size)
        model.addAttribute("enabledExecutorCount", executors.count { it.status == ExecutorStatus.ENABLED })
        model.addAttribute("onlineExecutorCount", executors.count {
            it.status == ExecutorStatus.ENABLED && now - it.lastHeartbeatTime <= 30_000
        })
        return "schedule-executors"
    }

    /** 执行日志查询页。 */
    @GetMapping(ScheduleAdminPagePaths.LOGS)
    fun logs(model: Model): String {
        model.addAttribute("page", "logs")
        return "schedule-logs"
    }
}
