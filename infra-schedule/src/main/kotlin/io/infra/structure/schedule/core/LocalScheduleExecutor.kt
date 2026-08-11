package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleExecutor
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult

/** 当前应用内执行器；远程执行器可实现 ScheduleExecutor 后注册到 ExecutorRegistry。 */
class LocalScheduleExecutor(
    override val id: String,
    override val group: String,
    private val handlerRegistry: HandlerRegistry
) : ScheduleExecutor {
    override fun execute(context: JobExecutionContext): JobExecutionResult = handlerRegistry.execute(context)
}
