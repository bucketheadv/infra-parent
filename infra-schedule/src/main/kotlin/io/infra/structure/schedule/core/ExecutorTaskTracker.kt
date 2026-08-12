package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleLogHelper
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.slf4j.LoggerFactory
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/**
 * 跟踪本进程内正在执行的任务，支持按执行日志 ID 中断 handler 线程。
 * HTTP `/run` 与本地执行器共用同一追踪器。
 */
class ExecutorTaskTracker(
    private val handlerRegistry: HandlerRegistry,
    private val executor: ExecutorService
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = ConcurrentHashMap<Long, Future<JobExecutionResult>>()

    /** 提交并等待处理器执行；[context.logId] 用于取消时定位线程。 */
    fun run(context: JobExecutionContext): JobExecutionResult {
        val logId = context.logId
        if (logId == null || logId <= 0) {
            return executeBound(context)
        }
        val future = executor.submit<JobExecutionResult> { executeBound(context) }
        running[logId] = future
        return try {
            future.get()
        } catch (_: CancellationException) {
            logger.warn(
                "调度任务 Future 已取消: jobId={}, logId={}, handler={}",
                context.jobId,
                logId,
                context.handler
            )
            JobExecutionResult.failure("任务已被终止")
        } catch (_: InterruptedException) {
            future.cancel(true)
            Thread.currentThread().interrupt()
            logger.warn(
                "调度任务等待被中断: jobId={}, logId={}, handler={}",
                context.jobId,
                logId,
                context.handler
            )
            JobExecutionResult.failure("任务已被终止")
        } finally {
            running.remove(logId, future)
        }
    }

    private fun executeBound(context: JobExecutionContext): JobExecutionResult {
        ScheduleLogHelper.bind(context)
        return try {
            handlerRegistry.execute(context)
        } finally {
            ScheduleLogHelper.flush()
            ScheduleLogHelper.unbind()
        }
    }

    /** 中断指定日志对应的 handler 线程；返回是否命中运行中任务。 */
    fun cancel(logId: Long): Boolean {
        val future = running.remove(logId) ?: return false
        val cancelled = future.cancel(true)
        if (cancelled) {
            logger.warn("已向执行线程发送中止信号: logId={}", logId)
        }
        return cancelled
    }

    /** 指定日志 ID 对应的 handler 是否仍在本进程执行。 */
    fun isRunning(logId: Long): Boolean {
        val future = running[logId] ?: return false
        if (future.isDone) {
            running.remove(logId, future)
            return false
        }
        return true
    }
}
