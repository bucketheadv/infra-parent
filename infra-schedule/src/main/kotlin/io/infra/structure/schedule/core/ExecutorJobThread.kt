package io.infra.structure.schedule.core

import io.infra.structure.schedule.api.ScheduleLogHelper
import io.infra.structure.schedule.model.JobExecutionContext
import io.infra.structure.schedule.model.JobExecutionResult
import org.slf4j.LoggerFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 单次触发在 JobThread 队列中的票据，供同步 /run 等待执行结果。 */
internal class TriggerTicket(
    val context: JobExecutionContext
) {
    private val future = CompletableFuture<JobExecutionResult>()

    fun complete(result: JobExecutionResult) {
        future.complete(result)
    }

    fun await(): JobExecutionResult = try {
        future.get()
    } catch (exception: Exception) {
        JobExecutionResult.failure(exception.cause?.message ?: exception.message ?: "等待执行结果失败")
    }
}

/**
 * 对齐 xxl-job `JobThread`：每个 jobId 一条执行线程 + 触发队列。
 * 阻塞策略在入队前判定；COVER_EARLY 会 stop 本线程并由管理器重建。
 *
 * 串行时在 [ticket.complete] 前先 [onExecutionFinished] 回写终态，
 * 避免调度侧 finishLog 落后于下一票 markStarted 而出现多条 RUNNING。
 */
internal class ExecutorJobThread(
    private val jobId: Long,
    private val handlerRegistry: HandlerRegistry,
    private val onExecutionStarted: (JobExecutionContext) -> Unit,
    private val onExecutionFinished: (JobExecutionContext, JobExecutionResult, Long) -> Unit,
    private val onExit: (ExecutorJobThread) -> Unit
) : Thread("infra-schedule-job-$jobId") {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val queue = LinkedBlockingQueue<TriggerTicket>(20_000)
    private val toStop = AtomicBoolean(false)
    @Volatile private var runningHandler = false
    @Volatile private var currentLogId: Long? = null

    fun isRunningOrHasQueue(): Boolean = runningHandler || queue.isNotEmpty()

    fun offer(ticket: TriggerTicket): Boolean = !toStop.get() && queue.offer(ticket)

    /** 停止线程：清空队列并中断当前 handler。 */
    /** 请求中断并等待线程退出；未确认退出时禁止新线程启动。 */
    fun stopForCover(reason: String, waitMillis: Long): Boolean {
        toStop.set(true)
        drainQueue(JobExecutionResult.cancelled(reason))
        interrupt()
        return try {
            join(waitMillis.coerceAtLeast(1))
            !isAlive
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }

    /**
     * 按 logId 取消单次触发：
     * - 在队列中则只移除该票并 complete，**不** drain 整队（避免空窗导致乱序/丢序）；
     * - 若正在执行则中断当前 handler，队列其余项保持原顺序继续。
     */
    fun cancel(logId: Long): Boolean {
        var hit = false
        val iterator = queue.iterator()
        while (iterator.hasNext()) {
            val ticket = iterator.next()
            if (ticket.context.logId == logId) {
                iterator.remove()
                ticket.complete(JobExecutionResult.cancelled("任务已被终止"))
                hit = true
                break
            }
        }
        if (currentLogId == logId && runningHandler) {
            interrupt()
            hit = true
        }
        return hit
    }

    fun isRunning(logId: Long): Boolean =
        (currentLogId == logId && runningHandler) || queue.any { it.context.logId == logId }

    override fun run() {
        try {
            while (!toStop.get()) {
                val ticket = try {
                    queue.poll(1, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    if (toStop.get()) break
                    continue
                } ?: continue
                if (toStop.get()) {
                    ticket.complete(JobExecutionResult.cancelled("任务线程已停止"))
                    break
                }
                runningHandler = true
                currentLogId = ticket.context.logId
                val startedAt = System.currentTimeMillis()
                try {
                    val result = executeBound(ticket.context)
                    val durationMs = System.currentTimeMillis() - startedAt
                    // 先落终态再 complete，保证下一票 markStarted 时上一条已非 RUNNING。
                    onExecutionFinished(ticket.context, result, durationMs)
                    ticket.complete(result)
                } catch (_: InterruptedException) {
                    val cancelled = JobExecutionResult.cancelled("任务已被终止")
                    onExecutionFinished(ticket.context, cancelled, System.currentTimeMillis() - startedAt)
                    ticket.complete(cancelled)
                    if (toStop.get()) break
                } catch (exception: Exception) {
                    val message = exception.cause?.message ?: exception.message ?: exception.javaClass.simpleName
                    val failed = JobExecutionResult.failure(message)
                    onExecutionFinished(ticket.context, failed, System.currentTimeMillis() - startedAt)
                    ticket.complete(failed)
                } finally {
                    runningHandler = false
                    currentLogId = null
                    interrupted()
                }
            }
        } finally {
            drainQueue(JobExecutionResult.cancelled("任务线程已退出"))
            onExit(this)
            logger.debug("JobThread 退出: jobId={}", jobId)
        }
    }

    private fun executeBound(context: JobExecutionContext): JobExecutionResult {
        onExecutionStarted(context)
        ScheduleLogHelper.bind(context)
        return try {
            handlerRegistry.execute(context)
        } finally {
            ScheduleLogHelper.flush()
            ScheduleLogHelper.unbind()
        }
    }

    private fun drainQueue(result: JobExecutionResult) {
        while (true) {
            val ticket = queue.poll() ?: break
            ticket.complete(result)
        }
    }
}
