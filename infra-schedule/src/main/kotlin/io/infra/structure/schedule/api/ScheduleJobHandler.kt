package io.infra.structure.schedule.api

import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult

/**
 * 任务执行器实现此接口，并使用 [ScheduleHandler] 声明后台可配置的处理器名称。
 */
fun interface ScheduleJobHandler {
    /**
     * 执行一次任务。抛出的异常会被调度器转换为失败结果并按任务配置决定是否重试。
     */
    fun execute(context: JobExecutionContext): JobExecutionResult
}

/** 将 Spring Bean 绑定为可在管理端配置的任务处理器。 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ScheduleHandler(
    /** 任务定义中使用的处理器名称。 */
    val value: String
)

/** 可被调度中心注册、路由和调用的执行器抽象。 */
interface ScheduleExecutor {
    /** 执行器实例标识，可用于日志展示；业务唯一键为 [group]。 */
    val id: String
    /** 执行器分组标识，全局唯一，只接收同组任务。 */
    val group: String
    /** 执行一次任务上下文并返回处理结果。 */
    fun execute(context: JobExecutionContext): JobExecutionResult
}
