package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleHandler
import io.infra.structure.schedule.api.ScheduleJobHandler
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.util.ClassUtils
import java.util.concurrent.ConcurrentHashMap

/** 将应用中的 [ScheduleJobHandler] 映射为稳定的处理器名称。 */
class HandlerRegistry(handlers: List<ScheduleJobHandler>) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val handlerByName = ConcurrentHashMap<String, ScheduleJobHandler>()

    init {
        handlers.forEach(::register)
    }

    /** 注册处理器；重复名称会使应用在启动阶段失败。 */
    fun register(handler: ScheduleJobHandler) {
        val targetType = ClassUtils.getUserClass(handler)
        val name = AnnotatedElementUtils.findMergedAnnotation(targetType, ScheduleHandler::class.java)?.value
            ?: targetType.simpleName.replaceFirstChar { it.lowercase() }
        require(name.isNotBlank()) { "调度处理器名称不能为空" }
        check(handlerByName.putIfAbsent(name, handler) == null) { "重复的调度处理器: $name" }
    }

    /** 查找并同步执行指定处理器，异常会转换为失败结果。 */
    fun execute(context: JobExecutionContext): JobExecutionResult {
        val handler = handlerByName[context.handler]
            ?: return JobExecutionResult.failure("未找到任务处理器: ${context.handler}")
        return try {
            handler.execute(context)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            logCancelled(context, "InterruptedException")
            JobExecutionResult.failure("任务已被终止")
        } catch (exception: Exception) {
            if (exception.cause is InterruptedException || Thread.currentThread().isInterrupted) {
                Thread.currentThread().interrupt()
                logCancelled(context, exception.javaClass.simpleName)
                return JobExecutionResult.failure("任务已被终止")
            }
            JobExecutionResult.failure(exception.message ?: exception.javaClass.simpleName)
        }
    }

    private fun logCancelled(context: JobExecutionContext, reason: String) {
        logger.warn(
            "调度任务已中止: jobId={}, logId={}, handler={}, jobName={}, reason={}",
            context.jobId,
            context.logId,
            context.handler,
            context.jobName,
            reason
        )
    }

    /** 返回当前已注册的处理器名称，供管理端或自检使用。 */
    fun handlerNames(): Set<String> = handlerByName.keys
}
