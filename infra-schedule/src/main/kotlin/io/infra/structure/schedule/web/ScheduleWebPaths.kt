package io.infra.structure.schedule.web

/** 调度中心 HTTP 协议路径，供控制器、拦截器和 HTTP 客户端统一复用。 */
object ScheduleWebPaths {
    /** 调度管理 API 根路径。 */
    const val API_ROOT = "/infra/schedule"
    /** 调度管理 API 的全部子路径，用于 Spring MVC 拦截器匹配。 */
    const val API_ALL = "$API_ROOT/**"
    /** 任务集合的相对路径。 */
    const val JOBS = "/jobs"
    /** 以任务 ID 查询、更新或删除任务的相对路径模板。 */
    const val JOB_BY_ID = "/jobs/{id}"
    /** 立即触发任务的相对路径模板。 */
    const val JOB_TRIGGER = "/jobs/{id}/trigger"
    /** 修改任务状态的相对路径模板。 */
    const val JOB_STATUS = "/jobs/{id}/status"
    /** 查询任务执行日志的相对路径模板。 */
    const val JOB_LOGS = "/jobs/{id}/logs"
    /** 多条件查询执行日志的相对路径。 */
    const val LOGS = "/logs"
    /** 预览任务接下来若干次调度时间的相对路径模板。 */
    const val JOB_NEXT_TRIGGERS = "/jobs/{id}/next-triggers"
    /** 按当前表单中的调度配置预览接下来若干次调度时间。 */
    const val JOB_NEXT_TRIGGERS_PREVIEW = "/jobs/next-triggers/preview"
    /** 执行器心跳上报的相对路径。 */
    const val HEARTBEAT = "/executors/heartbeat"
    /** 执行器心跳上报的完整路径。 */
    const val EXECUTOR_HEARTBEAT = "$API_ROOT$HEARTBEAT"
    /** 执行器主动离线上报的相对路径。 */
    const val OFFLINE = "/executors/offline"
    /** 执行器主动离线上报的完整路径。 */
    const val EXECUTOR_OFFLINE = "$API_ROOT$OFFLINE"
    /** 按执行器分组查询健康节点的相对路径模板。 */
    const val EXECUTORS_BY_GROUP = "/executors/{group}"
    /** 执行器集合路径，用于节点新增及全量查询。 */
    const val EXECUTORS = "/executors"
    /** 按执行器 ID 编辑或删除节点的相对路径模板。 */
    const val EXECUTOR_BY_ID = "/executors/{id}"
    /** 按执行器分组查询全部已登记节点的相对路径模板。 */
    const val EXECUTOR_NODES = "/executors/{group}/nodes"
    /** 修改执行器可用状态的相对路径模板。 */
    const val EXECUTOR_STATUS = "/executors/{id}/status"
    /** 执行器 HTTP 协议根路径。 */
    const val EXECUTOR_ROOT = "$API_ROOT/executor"
    /** 执行器处理请求的相对路径。 */
    const val RUN = "/run"
    /** 调度中心调用执行器处理任务的完整路径。 */
    const val EXECUTOR_RUN = "$EXECUTOR_ROOT$RUN"
}
